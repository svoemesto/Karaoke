# Отчёт по спецификации 733: постоянный туннель админ-машина→прод (WireGuard) + mTLS к прод-БД

**Ветка**: `733-prod-tunnel-mtls` | **Задача Plane**: `KARAOKE-407` (In progress, assignee `ai-agent`)
**Дата**: 2026-11-09 | **Спека**: [spec.md](./spec.md) (утверждена владельцем 2026-11-09)

---

## 1. Итог одной строкой

Механика постоянного туннеля WireGuard между админской машиной и прод-хостом
**спроектирована, реализована и проверена фактом** на изолированном стенде
(10/10 проверок): приложение владеет туннелем, хост и приложение ходят в прод по
**одному** постоянному внутреннему адресу `10.77.0.2` и прод видит **один** адрес
источника `10.77.0.1`. Изменений на прод-хосте **не вносилось** — они вынесены в
отдельный шаг, требующий согласия владельца (раздел 6).

---

## 2. Что сделано

### 2.1 Артефакты speckit-цепочки

| Артефакт | Что внутри |
|---|---|
| [plan.md](./plan.md) | технический план, 14 решений (DD-1…DD-14), фазы переключения и отката, стратегия проверки |
| [research.md](./research.md) | 12 исследований с альтернативами (R-1…R-12) |
| [data-model.md](./data-model.md) | сущности «Туннель», «Пир», «CA», «Прод-канал», «Событие наблюдаемости», классификация отказов |
| [contracts/tunnel-config.md](./contracts/tunnel-config.md) | контракт конфигурации: переменные окружения, файлы секретов, правила валидации |
| [contracts/event-codes.md](./contracts/event-codes.md) | коды событий `TUNNEL-001…007`, `DB-CERT-001`, таблица различения участников |
| [contracts/cli.md](./contracts/cli.md) | контракт CLI скриптов (`selftest.sh`, `install-admin-tunnel.sh`, `install-prod-tunnel.sh`, `host-route.sh`) |
| [quickstart.md](./quickstart.md) | сценарии проверки от локального стенда до живого канала |
| [tasks.md](./tasks.md) | 66 задач, 9 фаз, [P]/[Story]-маркеры, зависимости |
| [analysis.md](./analysis.md) | 14 находок (CRITICAL — 0, HIGH — 2), покрытие требований 95.7 %, нарушений конституции — 0; находки закрыты в задачах |

### 2.2 Код приложения (`karaoke-app`)

| Файл | Назначение |
|---|---|
| `tunnel/ProdTunnelConfig.kt` | конфигурация из env + валидация (в т.ч. «подсеть туннеля не пересекается с известными сетями машины») |
| `tunnel/TunnelCommandRunner.kt` | абстракция запуска `ip`/`wg` (тестовая подмена) + исполнитель с таймаутом |
| `tunnel/ProdTunnel.kt` | интерфейс жизненного цикла + модель состояния |
| `tunnel/WireGuardProdTunnel.kt` | подъём/снятие интерфейса, чтение возраста рукопожатия, перезапуск при устаревании, классификация причин отказа |
| `tunnel/ProdTunnelSupervisor.kt` | цикл наблюдения (30 с) и восстановления; события `TUNNEL-001…007` |
| `tunnel/HostsOverride.kt` | переопределение резолвинга имени хоста (SNI/Host не меняются) |
| `tunnel/CertFailureClassifier.kt` | причины отказа по сертификату (просрочен/отозван/недоверенный) |
| `tunnel/ChannelFailureClassifier.kt` | канальный vs логический отказ (для возврата задания в очередь) |
| `services/ProdTunnelService.kt` | подключение туннеля к старту приложения; доступ из проверок мониторинга |
| `monitor/checks/ProdTunnelCheck.kt` | отдельный признак состояния туннеля в мониторинге (`prod.tunnel`) |
| `monitor/MonitorRegistry.kt` | регистрация проверки |
| `Connection.kt` | параметры клиентского сертификата в JDBC-URL (`sslcert`/`sslkey`/`sslrootcert`) |
| `KaraokeConnection.kt` | событие `DB-CERT-001` с причиной отказа по сертификату |
| `KaraokeProcessWorker.kt` | канальный отказ → задание возвращается в `WAITING` (FR-033), а не падает в `ERROR` |
| `monitor/checks/ProdContainerCheck.kt` | при упавшем туннеле — «Канал к продук недоступен (туннель не поднят)», а не «Прод-сервер недоступен» (устранение ложного CRITICAL) |

### 2.3 Конфигурация и скрипты (`deploy/`)

| Файл | Назначение |
|---|---|
| `deploy/prod-tunnel/wg0-admin.conf.template`, `wg0-prod.conf.template` | шаблоны пиров (без приватных ключей) |
| `deploy/prod-tunnel/install-admin-tunnel.sh` | `--check` / `--apply` / `--print-public-key`: каталог `700`, ключ `600`, наружу — только публичный ключ |
| `deploy/prod-tunnel/install-prod-tunnel.sh` | печатает точные команды установки пира на проде (сам на прод **ничего не делает**) |
| `deploy/prod-tunnel/host-route.sh` | маршрут хоста `10.77.0.0/24` через контейнер (`--check`/`--apply`/`--remove`) |
| `deploy/prod-tunnel/karaoke-tunnel-host-route.service` | systemd-юнит маршрута (установку выполняет владелец) |
| `deploy/prod-tunnel/selftest.sh` | изолированная проверка механики туннеля на сетевых namespace'ах |
| `deploy/docker-compose-app.yml` | `NET_ADMIN`, `/dev/net/tun`, `net.ipv4.ip_forward=1`, монтирование каталога с ключами `:ro`, переменные `PROD_TUNNEL_*` и `DB_REMOTE_SSL_*` |
| `deploy/karaoke-app/Dockerfile` | `wireguard-tools`, `iproute2`, `iptables` в образе |
| `deploy/admin-ip-allowlist.sh` | **режим туннеля** `ALLOWLIST_MODE=tunnel`: публичный адрес не пишется никогда, только read-only проверка + уведомление владельцу (FR-009) |

### 2.4 База знаний

| Файл | Что добавлено |
|---|---|
| `knowledge/product/features/prod-tunnel.md` | per-feature документ (FR-009), 6 секций |
| `knowledge/adr/local-0018-prod-wireguard-tunnel.md` | ADR: решения владельца, 8 решений, 7 отклонённых альтернатив |
| `knowledge/guidelines/runbooks/prod-tunnel.md` | runbook: диагностика по шагам, операционные сценарии, перевыпуск сертификата, откат |
| `knowledge/system/infra/deploy-overview.md` | раздел «Постоянный туннель к проду» |
| `knowledge/guidelines/runbooks/prod-config-delivery.md` | § 9 «Доставка конфигурации туннеля» |
| `knowledge/guidelines/runbooks/log-correlation.md` | § 1a — коды `TUNNEL-001…007`, `DB-CERT-001`, `QUEUE-CHANNEL-001`; § 1d — различение «туннель / сертификат / БД / сайт» |
| `knowledge/product/features/admin-ip-allowlist.md` | раздел «Режим туннеля» |

### 2.5 Проверки (guard, CI, pre-commit)

* `tools/check-prod-tunnel-guardrails.sh` — 24 проверки инвариантов (compose, образ,
  отсутствие секретов в репозитории, свободная подсеть, шаблоны пиров, режим
  туннеля у автоматики, отсутствие вывода ключа в журнал).
* Подключён к **pre-commit** (новый хук `prod-tunnel-guardrails-guard`) и к **CI**:
  обе копии workflow (`.forgejo/`, `.github/`) + `config/ci/checks.yml` (зона
  интереса). Манифест согласован: 37 записей, 37 job-id, мёртвых зон нет.

---

## 3. Проверки: критерий → команда → результат

| Критерий | Команда | Результат |
|---|---|---|
| Механика туннеля работает (рукопожатие, трафик, маршрут по умолчанию, доступ с хоста, переопределение резолвинга, детектор рукопожатия, отсутствие ключа) | `bash deploy/prod-tunnel/selftest.sh` | **10/10 OK**, exit 0 (стенд на сетевых namespace'ах; прод не тронут) |
| Хост и приложение используют один адрес и один источник | сценарий 3 стенда | `peer=10.77.0.1` от обоих; адрес назначения `10.77.0.2` |
| Маршрут по умолчанию не изменён | сценарий 2 стенда | маршрут по умолчанию отсутствует; маршрут туннеля — только `dev wg0` |
| Имя хоста и SNI не меняются | сценарий 4 стенда | `sm-karaoke.ru` → `10.77.0.2`, чужие строки файла резолвинга сохранены |
| Туннель поднимается/восстанавливается приложением | `./gradlew :karaoke-app:test --tests '*ProdTunnel*'` | 40 тестов зелёные (подъём, идемпотентность, stale→пересоздание, `no_key`, `no_permission`, `no_handshake`, восстановление) |
| Отказ по сертификату различим | `--tests '*FailureClassifiers*'` | зелёные: expired / revoked / untrusted / unknown |
| Параметры mTLS в JDBC | `--tests '*RemoteJdbcUrl*'` | зелёные (в т.ч. пустые параметры не добавляются) |
| Состояние туннеля в мониторинге отдельным признаком | `--tests '*ProdTunnelCheckTest'` | зелёные (up/down/stale/выключен) |
| Канальный отказ → задание в очередь | `ChannelFailureClassifierTest` | зелёные (канальный/сертификатный/логический) |
| Автоматика allow-list в режиме туннеля не пишет публичный адрес | `bash deploy/admin-ip-allowlist.sh --self-test` | 22 сценария, **все пройдены** (новый сценарий 22: публичный адрес не попал, файл не изменён, код 3 + событие `reason=tunnel_address_mismatch`) |
| Инварианты конфигурации туннеля | `bash tools/check-prod-tunnel-guardrails.sh` | **PASS=24 FAIL=0** |
| Полный набор тестов `karaoke-app` | `./gradlew :karaoke-app:test` | **1038 тестов, 0 падений** |
| Kotlin-стиль | `./gradlew :karaoke-app:ktlintCheck` | BUILD SUCCESSFUL |
| Структура базы знаний | `bash tools/check-knowledge-structure.sh` | 9/9 OK |
| Линтер базы знаний | `python3 tools/lint-knowledge.py --baseline config/knowledge/baseline-knowledge-lint.txt` | «All documentation passes architectural style and structural checks» |
| Per-feature документ | `bash tools/check-feature-doc.sh knowledge/product/features/prod-tunnel.md` | OK |
| Влияние на SSoT | `python3 tools/check-ssot-impact.py` | новых нарушений нет |
| Манифест CI | `bash tools/check-ci-checks-manifest.sh` | «манифест и обе копии workflow согласованы (37 записей, 37 job-id, мёртвых зон нет)» |
| Pre-commit (полный набор хуков) | `pre-commit run --files <мои файлы>` | все хуки Passed (включая новый guard туннеля, lychee, doc-references, ktlint) |
| Секреты не в git | `git ls-files \| grep -iE '\.env$\|do\.env$\|\.key$\|\.pem$\|\.p12$\|\.pfx$'` | пусто (в `deploy/prod-tunnel/` — только шаблоны) |

---

## 4. Ключевые проектные решения (сжато)

1. **Интерфейс `wg0` — в контейнере `karaoke-app`** (`NET_ADMIN` + `/dev/net/tun`):
   туннелем владеет приложение (FR-027). Хост-демон сделал бы владельцем туннеля
   не приложение.
2. **Адресация**: `10.77.0.1` (админская сторона; единственный адрес в allow-list
   прода) и `10.77.0.2` (прод; постоянный внутренний адрес каналов).
3. **Доступ с хоста** — маршрут `10.77.0.0/24` через контейнер + SNAT в
   контейнере: поэтому прод видит один адрес источника, а оператор ходит по тому
   же `10.77.0.2` (FR-031/FR-032). Проверено фактом (сценарий 3 стенда).
4. **HTTP-каналы при неизменном имени хоста**: переопределение резолвинга
   `sm-karaoke.ru` → `10.77.0.2`; SNI/Host прежние, сертификат сервера не
   трогаем (FR-022). Туннель не поднят — переопределение снимается, канал
   возвращается на публичный путь (откат).
5. **mTLS**: `sslmode=verify-full` + `sslcert`/`sslkey`/`sslrootcert` на клиенте;
   на проде `hostssl … scram-sha-256 clientcert=verify-ca` — **пароль остаётся**
   запасным путём (FR-015). Переход на «только сертификат» — отдельное решение
   владельца.
6. **Наблюдаемость**: `TUNNEL-001…007`, `DB-CERT-001`, отдельная проверка
   `prod.tunnel`; «прод недоступен» больше не выдаётся при живом проде.
7. **Устойчивость к обрыву WAN**: канальный отказ → задание в `WAITING`
   (FR-033). FR-034 зафиксирован явно: туннель **не** снимает зависимость от
   WAN шлюза `10.0.1.1`.

---

## 5. Что НЕ сделано и почему (границы среды)

| Не сделано | Причина |
|---|---|
| Установка WireGuard и выпуск ключей на прод-хосте `188.127.240.124` | Hard Gate: любые изменения на проде — только по отдельному согласию владельца (раздел 6) |
| Переключение allow-list прод-хоста на `10.77.0.1` | то же |
| Включение TLS и `clientcert` в конфигурации БД прода, выпуск CA/сертификатов | то же |
| Переключение каналов приложения и перезапуск `karaoke-app` | то же + Pass 380 (очередь → стоп → дренаж → старт → старт очереди) |
| Закрытие публичных портов `5433`/`9000` | то же (Фаза 4 — после успешной проверки Фазы 3) |
| Реальный прогон стенда на прод-хосте | прод не трогаем; вместо этого — изолированный стенд на namespace'ах (10/10) |

**Изменения на прод-хосте в этой итерации — НОЛЬ.**

---

## 6. Шаги, требующие согласия владельца (точные команды и ожидание)

### Шаг 1. Подготовка админской стороны (root на `nsa-i9`, согласие не требуется — машина своя)

```bash
cd /home/nsa/Karaoke
sudo apt-get install -y wireguard-tools
bash deploy/prod-tunnel/install-admin-tunnel.sh --apply
# ожидание: создан /etc/karaoke/tunnel (700), tunnel.key (600),
#            напечатан ПУБЛИЧНЫЙ ключ админского пира
```

### Шаг 2. Пир «prod» на прод-хосте (требует согласия)

```bash
bash deploy/prod-tunnel/install-prod-tunnel.sh --print-commands
# выполнить напечатанные команды на root@188.127.240.124:
#   apt-get install -y wireguard-tools
#   install -d -m 700 /etc/wireguard && (umask 077 && wg genkey > /etc/wireguard/wg0.key)
#   # заполнить /etc/wireguard/wg0.conf по deploy/prod-tunnel/wg0-prod.conf.template
#   wg-quick up wg0 && wg show wg0
# ожидание: в wg show — пир, latest-handshake свежий
```

### Шаг 3. Allow-list прод-хоста (требует согласия)

Заменить публичный адрес админской машины на `10.77.0.1` **во всех трёх местах**
(stream `5433`/`9000` в `/etc/nginx/nginx.conf`, HTTP-локации `/changerecords` и
`/api/properties/setproperty` в `/etc/nginx/sites-enabled/80to8897`) — с бэкапом,
`nginx -t`, reload и контрольной проверкой. Порядок: **сначала allow-list, потом
переключение каналов**.

### Шаг 4. Переключение каналов (требует согласия; Pass 380)

```bash
# 0. контейнеру: PROD_TUNNEL_ENABLED=true, DB_REMOTE_HOST=10.77.0.2,
#    STORAGE_REMOTE_ENDPOINT=http://10.77.0.2:9000, DB_REMOTE_SSL_MODE=verify-full
# 1. убедиться, что заданий в работе нет:
curl -s -X POST http://localhost:7906/api/processesdigests -H 'Content-Type: application/json' -d '{}' \
  | python3 -c "import json,sys; ps=json.load(sys.stdin)['processesDigests']; print([(p['id'],p.get('name','')[:40]) for p in ps if p.get('status') in ('WORKING','CREATING')])"
# 2. остановить очередь и дождаться дренажа:
curl -s http://localhost:8898/process/stop
# 3. пересобрать/перезапустить контейнер через deploy/do.sh (НЕ docker restart)
bash deploy/do.sh start_app
# 4. запустить очередь:
curl -s http://localhost:8898/process/start
# ожидание: docker exec karaoke-app wg show — рукопожатие есть; DB/MinIO/синхронизация/kill-switch работают
```

### Шаг 5. Закрытие публичных портов (требует согласия; Фаза 4)

Закрыть `5433`/`9000` для интернета; проверить с внешней машины, что соединение
не устанавливается, а из туннеля — работает (SC-005).

### Откат (в любой фазе)

```bash
bash deploy/prod-tunnel/host-route.sh --remove
docker exec karaoke-app wg-quick down wg0
# вернуть DB_REMOTE_HOST/STORAGE_REMOTE_ENDPOINT на публичные значения,
# перезапустить контейнер по Pass 380, открыть порты, вернуть allow-list из бэкапа
```

---

## 7. Вопросы владельцу (зафиксированы, спека не менялась)

1. **Резервный путь доступа к БД.** Сейчас в плане: клиентский сертификат
   **плюс** пароль (`scram-sha-256 clientcert=verify-ca`) — пароль остаётся
   запасным путём (FR-015 требует сохранить его до отдельного решения).
   Переход на «только сертификат» — отдельное осознанное решение; нужно ли
   готовить его сейчас?
2. **Срок сертификата.** В плане — 90 дней с процедурой перевыпуска без
   остановки конвейера (runbook § 3.3). Подходит ли срок?
3. **Режим при неподнятом туннеле.** По умолчанию канал уходит на публичный путь
   (`fallback=public`), альтернатива — явное отключение канала
   (`fallback=disabled`). Сейчас стоит «public» — при смене IP провайдера старый
   путь перестанет работать, и это будет видно в мониторинге.
4. **Таймер allow-list.** В режиме туннеля он не выключается, а превращается в
   read-only проверку наличия `10.77.0.1` (с уведомлением владельцу при
   расхождении). Оставить так или выключить совсем?

---

## 8. Побочная правка (вне основной задачи, но нужна для зелёного CI)

Тест `PkgS335SourceContractsTest` («BR-510 per-feature документы проверяются в
обоих каталогах») был **красным уже на HEAD** ветки (после коммита
`14411b0d` «ci(docs): lychee без action»): тест искал блок `lychee-action@`,
которого в workflow уже нет, и сравнивал строки с висящим `\`. Правка теста
(3 строки: привязка к `lychee --offline` + снятие переноса строки) — иначе CI
красный и PR не слить. Отдельной фичей не является.

---

## 9. Артефакты

| Артефакт | Путь |
|---|---|
| Спека / план / задачи / анализ / quickstart / контракты | `specs/733-prod-tunnel-mtls/` |
| Код туннеля | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/tunnel/` |
| Скрипты и шаблоны | `deploy/prod-tunnel/` |
| Guard | `tools/check-prod-tunnel-guardrails.sh` |
| Per-feature / ADR / runbook | `knowledge/product/features/prod-tunnel.md`, `knowledge/adr/local-0018-prod-wireguard-tunnel.md`, `knowledge/guidelines/runbooks/prod-tunnel.md` |
| Лог локальной проверки механики | `.scratch/tunnel-verify/selftest.log` (в git не входит) |
