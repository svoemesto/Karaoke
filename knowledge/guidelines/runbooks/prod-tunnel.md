# Runbook: постоянный туннель к проду (WireGuard) — эксплуатация и диагностика

> Спека 733 (KARAOKE-407). Туннель между админской машиной `nsa-i9` и прод-хостом
> `188.127.240.124`. Туннелем владеет приложение `karaoke-app`.
> Per-feature: `knowledge/product/features/prod-tunnel.md`; ADR:
> `knowledge/adr/local-0018-prod-wireguard-tunnel.md`.

## 1. Что где

| Что | Где |
|---|---|
| Интерфейс `wg0` (админская сторона) | сетевое пространство контейнера `karaoke-app` |
| Ключ админского пира | `/etc/karaoke/tunnel/tunnel.key` на хосте (600) → `/run/karaoke/tunnel/` в контейнере (ro) |
| Пир «prod» | `/etc/wireguard/wg0.conf` на прод-хосте (600), `wg-quick up wg0` |
| Постоянный внутренний адрес прода | `10.77.0.2` (БД `5433`, MinIO `9000`, HTTPS `443`) |
| Адрес, который видит прод | `10.77.0.1` (единственная строка `allow` в конфигурации прода) |
| Маршрут хоста в подсеть туннеля | `deploy/prod-tunnel/karaoke-tunnel-host-route.service` |
| Скрипты и шаблоны | `deploy/prod-tunnel/` |
| Коды событий | `knowledge/guidelines/runbooks/log-correlation.md` § 1a (`TUNNEL-001…007`, `DB-CERT-001`) |

## 2. Диагностика по шагам

### 2.1 Туннель поднят?

```bash
docker exec karaoke-app wg show
docker exec karaoke-app wg show wg0 latest-handshakes
```

* `latest-handshakes` — непустая эпоха (секунды с 1970) → рукопожатие было;
* значение старше `PROD_TUNNEL_HANDSHAKE_TIMEOUT` (по умолчанию 180 с) →
  туннель «устарел», приложение пересоздаст его (событие `TUNNEL-003`);
* `0` или пусто → рукопожатия нет (событие `TUNNEL-002`).

### 2.2 Что говорит приложение

```bash
docker logs karaoke-app --since 30m 2>&1 | grep -E "TUNNEL-|DB-CERT-"
```

| Код | Смысл | Что делать |
|---|---|---|
| `TUNNEL-001` | туннель поднят | — |
| `TUNNEL-002` | туннель не поднят (`reason=`) | см. таблицу причин ниже |
| `TUNNEL-003` | устаревшее рукопожатие, идёт перезапуск | проверить WAN (п. 2.3) |
| `TUNNEL-004` | туннель восстановлен | — |
| `TUNNEL-005` | туннель выключен конфигурацией | канал идёт по публичному пути |
| `TUNNEL-006` | конфигурация не прошла валидацию (`rule=`) | исправить конфигурацию |
| `TUNNEL-007` | туннель недоступен, канал ушёл в fallback | `fallback=public` — доступен прежний путь; `disabled` — канал закрыт намеренно |
| `DB-CERT-001` | сервер БД отклонил клиентский сертификат (`certReason=`) | см. п. 4 |

Причины `TUNNEL-002`:

| `reason` | Значение | Что делать |
|---|---|---|
| `no_permission` | нет `NET_ADMIN`/`/dev/net/tun` | проверить `deploy/docker-compose-app.yml` и пересоздать контейнер (Pass 380) |
| `no_key` | не смонтирован каталог с ключом | `ls -l /etc/karaoke/tunnel` на хосте; монтирование `:ro` в compose |
| `config_invalid` | конфигурация не прошла валидацию | `TUNNEL-006` рядом в логе — там указано правило |
| `udp_blocked` | нет маршрута/таймаут до `188.127.240.124:51820` | проверить, не режет ли провайдер UDP |
| `command_failed` | прочие ошибки `ip`/`wg` | посмотреть сообщение команды в логе |

### 2.3 Туннель есть, а канала нет (или наоборот)

```bash
ping -c1 -W2 10.0.1.1        # LAN-шлюз
ping -c1 -W2 1.1.1.1         # интернет
ping -c1 -W2 188.127.240.124 # прод-хост
```

| Наблюдение | Вывод |
|---|---|
| LAN ОК, интернет и прод НЕ ОК | **пропал WAN на машине** (обрыв шлюза `10.0.1.1`, runbook § 1d). Туннель это НЕ лечит (FR-034) |
| Всё ОК, рукопожатия нет | UDP-путь закрыт провайдером или на проде не поднят `wg0` |
| Рукопожатие есть, `ping 10.77.0.2` не отвечает | на проде не поднят `wg0` либо allow-list прода не содержит `10.77.0.1` |

### 2.4 Прод-канал недоступен — что именно сломано

| Что | Код |
|---|---|
| туннель | `TUNNEL-002`/`TUNNEL-003`/`TUNNEL-006`/`TUNNEL-007` |
| сертификат | `DB-CERT-001` |
| БД (не туннель и не сертификат) | `DB-CONNECT-001` / `DB-UNAVAILABLE-002` |
| сайт | `MON-CHECK-001` с `check=ProdContainerCheck` |

В мониторинге состояние туннеля — отдельный признак (`prod.tunnel`), а проверка
прод-канала при упавшем туннеле пишет «Канал к проду недоступен (туннель не
поднят)», а не «Прод-сервер недоступен» — так ложный CRITICAL при живом проде не
возникает.

## 3. Операционные сценарии

### 3.1 Оператор подключается к прод-БД с админской машины (DBeaver)

```text
host: 10.77.0.2      port: 5433      dbname: karaoke
user: SvoeMestoKaraokeUser905
SSL: require (или verify-full, если настроен mTLS)
client certificate: db-client.crt / db-client.key (из /etc/karaoke/tunnel)
```

Маршрут на хосте держит юнит `karaoke-tunnel-host-route.service`; если маршрута
нет:

```bash
sudo bash deploy/prod-tunnel/host-route.sh --apply
```

### 3.2 Включение kill-switch публикации через туннель

```bash
# С админской машины; трафик идёт внутри туннеля (имя хоста прежнее).
curl -sS -X POST https://sm-karaoke.ru/api/properties/setproperty \
  -H "X-Internal-Secret: $INTERNAL_SECRET" \
  -H 'Content-Type: application/json' \
  -d '{"key":"<ключ настройки>","value":"<значение>"}'
```

Если туннель не поднят, вызов не пройдёт — причина будет видна в логе приложения
(`TUNNEL-002`/`TUNNEL-007`), а не как «403 от сервера».

### 3.3 Перевыпуск клиентского сертификата (FR-014)

Срок действия сертификата — 90 дней. Процедура без остановки конвейера:

1. Выпустить новый сертификат (CN = пользователь БД) тем же CA; срок — с запасом.
2. Положить `db-client.crt`/`db-client.key` в `/etc/karaoke/tunnel` (600) на
   админской машине **с другими именами** (`db-client-2026-12.crt` и т. п.).
3. Переключить `DB_REMOTE_SSL_CERT`/`DB_REMOTE_SSL_KEY` на новые файлы.
4. Перезапустить `karaoke-app` по Pass 380 (очередь → стоп → дренаж → старт →
   старт очереди) — окно в минуты, задания не теряются.
5. Старый сертификат отозвать в CA и удалить с машины.

Если сертификат просрочен и перевыпуск невозможен — приложение пишет
`DB-CERT-001` с `certReason=expired`; задания при этом возвращаются в очередь
(канальный отказ), а не падают.

### 3.4 Переключение каналов на туннель и откат

Порядок (менять местами нельзя — потеряется доступ):

1. Поднять пир на проде, проверить рукопожатие.
2. Заменить в allow-list прод-хоста публичный адрес на `10.77.0.1`
   (во всех трёх местах), с бэкапом и `nginx -t`.
3. Переключить конфигурацию приложения: `DB_REMOTE_HOST=10.77.0.2`,
   `STORAGE_REMOTE_ENDPOINT=http://10.77.0.2:9000`, `PROD_TUNNEL_ENABLED=true`.
4. Перезапустить `karaoke-app` по Pass 380.
5. Проверить: БД, MinIO, синхронизация, kill-switch, DBeaver с хоста.
6. Только после этого — закрыть публичные порты `5433`/`9000` (FR-018).

Откат: вернуть публичные значения в конфигурации приложения, перезапустить
контейнер по Pass 380, открыть порты, вернуть allow-list из бэкапа. Ориентир —
не дольше 15 минут (SC-006).

### 3.5 Установка mTLS к БД на прод-хосте (однократно)

Вариант C спеки 733: доступ к прод-БД подтверждается клиентским сертификатом,
а не только адресом. Порядок (менять местами нельзя — потеряется доступ к БД):

1. Выпустить собственный CA и два сертификата (на прод-хосте, ключи наружу не
   уходят): сертификат сервера БД (SAN: `DNS:sm-karaoke.ru`, `IP:10.77.0.2`) и
   клиентский сертификат админ-машины (CN = `nsa-i9`, срок 90 дней).
2. Положить на прод-хосте в `deploy/certs/postgres/` (каталог прокинут в контейнер
   read-only): `ca.crt`, `server.crt`, `server.key` (ключ `600`).
3. Включить TLS в PostgreSQL: `DB_SSL_ON=on` в `.env` прод-хоста. В контейнере
   Postgres дополнительно нужен `ssl_ca_file` (`DB_SSL_CA_FILE`, по умолчанию
   `/certs/postgres/ca.crt`) — без него Postgres не может проверить клиентский
   сертификат по правилу `clientcert=verify-ca`.
4. Добавить правило в `pg_hba.conf` (файл в `PGDATA`, каталог данных БД):
   `hostssl karaoke SvoeMestoKaraokeUser905 172.18.0.1/32 scram-sha-256 clientcert=verify-ca`.
   Правило с паролем остаётся запасным путём (FR-015).

   **Адрес в правиле — `172.18.0.1`, а не `10.77.0.1` и не `127.0.0.1`.**
   Причина: канал приходит из туннеля в nginx (`:5433`), nginx проксирует на
   `127.0.0.1:8832` (docker-proxy), и только потом в контейнер БД; внутри
   контейнера источником виден **docker-шлюз `172.18.0.1`**. Правило на
   `10.77.0.1` не сработает никогда, правило на `127.0.0.1` — тоже (оно
   относится только к прямым локальным соединениям). Проверять фактический
   источник: `select client_addr from pg_stat_activity`.
5. Перезапустить `karaoke-db` (кратковременная недоступность БД — сайт переживает:
   правила для локальных подключений `trust`/`scram-sha-256` не меняются).
   Перечитывания конфигурации (`pg_reload_conf()`) достаточно для `pg_hba.conf`,
   но параметры `ssl*` применяются только перезапуском.
6. Доставить клиентский сертификат и ключ на админскую машину в
   `/etc/karaoke/tunnel` (`db-client.crt` `644`, `db-client.key` `600`, `ca.crt` `644`).

   **Ключ обязан быть в формате PKCS-8 DER (двоичный), не PEM.** Драйвер
   `pgjdbc` (в отличие от `libpq`/`psql`) читает `sslkey` только как PKCS-8 DER;
   на PEM-ключ он отвечает `Could not read SSL key file`. Конвертация:

   ```bash
   openssl pkcs8 -topk8 -inform PEM -outform DER -in db-client.key.pem -nocrypt -out db-client.key
   ```
7. Включить проверку на клиенте: `DB_REMOTE_SSL_MODE=verify-full`,
   `DB_REMOTE_SSL_CERT=/run/karaoke/tunnel/db-client.crt`,
   `DB_REMOTE_SSL_KEY=/run/karaoke/tunnel/db-client.key`,
   `DB_REMOTE_SSL_ROOT_CERT=/run/karaoke/tunnel/ca.crt`; перезапустить
   `karaoke-app` по Pass 380.
8. Проверить: приложение работает (`DB-CONNECT-001` = 0), а соединение **без**
   клиентского сертификата отклоняется — сервер отвечает
   `FATAL: connection requires a valid client certificate` (проверка из
   контейнера через туннель, см. п. 5 «Проверки»).

Откат: `DB_SSL_ON=off` + перезапуск `karaoke-db`, удаление правила `hostssl` из
`pg_hba.conf`, `DB_REMOTE_SSL_MODE=prefer` + перезапуск `karaoke-app`.

## 4. Секреты

* Приватные ключи и сертификаты — только в `/etc/karaoke/tunnel` (каталог `700`,
  файлы `600`) на хосте; в контейнер монтируются read-only.
* В репозитории — только шаблоны с плейсхолдерами (`deploy/prod-tunnel/*.template`).
* Приватный ключ приложением **не читается**: его получает `wg` по пути файла
  (`wg set wg0 private-key <путь>`), поэтому ключ не попадает ни в лог, ни в `argv`.
* CA и корневой сертификат — вне репозитория (FR-013).

## 5. Проверки

```bash
bash deploy/prod-tunnel/selftest.sh              # механика туннеля на namespace'ах
bash deploy/prod-tunnel/host-route.sh --check    # маршрут хоста
bash tools/check-prod-tunnel-guardrails.sh        # инварианты конфигурации в репозитории
bash deploy/admin-ip-allowlist.sh --self-test     # включая сценарий «режим туннеля»
```

## 6. Ссылки

- Per-feature: `knowledge/product/features/prod-tunnel.md`.
- ADR: `knowledge/adr/local-0018-prod-wireguard-tunnel.md`.
- Реестр кодов: `knowledge/guidelines/runbooks/log-correlation.md` § 1a, § 1d.
- Прод-хост и allow-list: `knowledge/system/infra/deploy-overview.md`.
- Автоматика allow-list: `knowledge/product/features/admin-ip-allowlist.md`.
- Спека: `specs/733-prod-tunnel-mtls/`.
