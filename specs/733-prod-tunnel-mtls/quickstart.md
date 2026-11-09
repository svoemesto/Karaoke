# Quickstart: проверка постоянного туннеля админ-машина→прод

> Phase 1 (`/speckit-plan`). Руководство по **проверке**, без кода реализации.
> Ссылки: контракт конфигурации — [`contracts/tunnel-config.md`](./contracts/tunnel-config.md),
> коды событий — [`contracts/event-codes.md`](./contracts/event-codes.md),
> CLI — [`contracts/cli.md`](./contracts/cli.md).

## 0. Предусловия

| Что | Как проверить |
|---|---|
| Инструменты WireGuard | `wg --version` (в контейнере — `docker exec karaoke-app wg --version`) |
| TUN-устройство | `ls -l /dev/net/tun` |
| Модуль ядра | `modinfo wireguard` |
| Свободна подсеть туннеля | `ip route show` — нет `10.77.0.0/24` |
| Не сломаны `tun0`/`xray` | `ip addr show tun0`, `systemctl is-active xray` |

## 1. Локальная проверка механики (без прода)

```bash
bash deploy/prod-tunnel/selftest.sh
```

Ожидание: все сценарии `OK`, код возврата `0`. Сценарии проверяют: рукопожатие,
неизменность маршрута по умолчанию, доступ «хоста» (третий namespace) к тому же
адресу `10.77.0.2` с тем же адресом источника `10.77.0.1`, переопределение
резолвинга имени хоста, детектор устаревшего рукопожатия, отказ при отсутствии ключа.

## 2. Проверка кода приложения

```bash
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$(dirname "$(git rev-parse --path-format=absolute --git-common-dir)")/.gradle}"
./gradlew :karaoke-app:test --tests '*ProdTunnel*' --tests '*CertFailure*' --tests '*ChannelFailure*' --tests '*RemoteJdbcUrl*'
```

Ожидание: `BUILD SUCCESSFUL`, все тесты зелёные.

## 3. Проверка инвариантов конфигурации

```bash
bash tools/check-prod-tunnel-guardrails.sh
bash deploy/admin-ip-allowlist.sh --self-test     # включая сценарий «режим туннеля»
```

Ожидание: код возврата `0`; в выводе self-test'а — сценарий 9 `OK`.

## 4. Проверка живой машины (админская, без прода)

| Шаг | Команда | Ожидание |
|---|---|---|
| 4.1 Подготовка | `bash deploy/prod-tunnel/install-admin-tunnel.sh --apply` | создан `/etc/karaoke/tunnel` (700), `wg0.conf` (600), напечатан публичный ключ |
| 4.2 Публичный ключ на прод | передать вывод шага 4.1 владельцу (он подставит в конфиг пира prod) | — |
| 4.3 Маршрут хоста | `bash deploy/prod-tunnel/host-route.sh --apply` | маршрут `10.77.0.0/24 via 172.18.0.10` |
| 4.4 Туннель в контейнере | `docker exec karaoke-app wg show` | handshake, `latest-handshake` свежий |
| 4.5 Канал БД из приложения | `docker exec karaoke-app pg_isready -h 10.77.0.2 -p 5433` (или лог `db.connect.failed`) | `accepting connections` |
| 4.6 Канал с хоста (DBeaver) | `psql "host=10.77.0.2 port=5433 user=… dbname=karaoke"` (или DBeaver) | подключение установлено |
| 4.7 HTTP-канал | `docker exec karaoke-app curl -sS -o /dev/null -w '%{http_code}' https://sm-karaoke.ru/health` | `200` **и** в выводе `wg show` — исходящий трафик через туннель |
| 4.8 Наблюдаемость | `docker logs karaoke-app 2>&1 \| grep -E 'TUNNEL-\|DB-CERT-'` | `TUNNEL-001` при подъёме |
| 4.9 Прямой канал не сломан | `ssh root@188.127.240.124 true` | успех (туннель не перехватывает маршрут по умолчанию) |

## 5. Проверка отказов ( FR-016, FR-028, SC-008 )

| Отказ | Как воспроизвести | Ожидаемый код |
|---|---|---|
| Туннель не поднят | `docker exec karaoke-app wg-quick down wg0` (или снять `NET_ADMIN`) | `TUNNEL-002` + `TUNNEL-007`, в мониторинге — `prod.tunnel` |
| Устаревшее рукопожатие | остановить WAN (шлюз `10.0.1.1`) дольше `PROD_TUNNEL_HANDSHAKE_TIMEOUT` | `TUNNEL-003`, затем `TUNNEL-004` после восстановления |
| Отказ по сертификату | подключиться к прод-БД с просроченным клиентским сертификатом | `DB-CERT-001` с `certReason=expired` |
| Обрыв WAN ≤ 60 с | выключить WAN на 60 с во время задания | задание возвращается в очередь (`WAITING`) и завершается; в логе нет ложного «прод недоступен» |

## 6. Откат

```bash
bash deploy/prod-tunnel/host-route.sh --remove          # снять маршрут хоста
docker exec karaoke-app wg-quick down wg0               # снять туннель
# вернуть DB_REMOTE_HOST / STORAGE_REMOTE_ENDPOINT на публичные значения,
# перезапустить контейнер по Pass 380 (очередь → стоп → дренаж → старт → старт очереди)
```

## 7. Что НЕ проверяется без согласия владельца

- Установка WireGuard на прод-хосте и выпуск ключей/сертификатов.
- Переключение allow-list прод-хоста на `10.77.0.1` и закрытие публичных портов
  5433/9000 (Фазы 2–4, FR-017/FR-018).
- Включение TLS и `clientcert` в `pg_hba.conf` прод-хоста (FR-011).

Эти шаги оформлены отдельными инструкциями с ожидаемым результатом — в
`knowledge/guidelines/runbooks/prod-tunnel.md` и в финальном отчёте
`specs/733-prod-tunnel-mtls/report.md`.
