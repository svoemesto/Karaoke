---
description: "Task list: постоянный туннель админ-машина→прод (WireGuard) + mTLS к прод-БД"
---

# Tasks: Постоянный туннель админ-машина→прод (WireGuard) + mTLS к прод-БД

**Input**: Design documents from `specs/733-prod-tunnel-mtls/`
(plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md)

**Задача Plane**: `KARAOKE-407` (в работе с 2026-11-09, assignee `ai-agent`)

**Tests**: тестовые задачи включены — спека требует проверяемых исходов (SC-001…SC-013),
а Hard Gate «обязательная проверка после изменения» требует зелёных тестов перед коммитом.

**Граница итерации**: задачи фаз 1–7 выполняются агентом **без согласия владельца**
(код, скрипты, конфиги, тесты, документация, локальные проверки). Фаза 8 (прод-операции)
выполняется **только по отдельному согласию владельца** — там же перезапуск
`karaoke-app` по Pass 380.

---

## Format: `[ID] [P?] [Story] Description`

- **[P]**: можно выполнять параллельно (разные файлы, нет зависимостей)
- **[Story]**: пользовательская история из spec.md
- Пути — от корня репозитория

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: подготовка рабочего дерева и каркаса артефактов.

- [x] T001 Создать пакет `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/tunnel/` и каталог `deploy/prod-tunnel/`
- [x] T002 [P] Создать шаблоны конфигурации пиров `deploy/prod-tunnel/wg0-admin.conf.template` и `deploy/prod-tunnel/wg0-prod.conf.template` (без приватных ключей — только плейсхолдеры)
- [x] T003 [P] Создать `deploy/prod-tunnel/README.md` — что это, порядок установки, чего скрипты НЕ делают (прод не трогают без согласия)
- [x] T004 Зафиксировать в `.scratch/tunnel-verify/` результаты локальной проверки механики WireGuard (namespace-стенд) — артефакт для отчёта

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: базовые сущности и контракты, без которых не начинается ни одна история.

**⚠️ CRITICAL**: работа историй начинается только после этой фазы.

- [x] T005 Реализовать `ProdTunnelConfig` (чтение env, валидация, дефолты) в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/tunnel/ProdTunnelConfig.kt`
- [x] T006 [P] Реализовать `TunnelCommandRunner` (абстракция запуска команд `wg`/`wg-quick`/`ip`) в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/tunnel/TunnelCommandRunner.kt`
- [x] T007 Реализовать интерфейс `ProdTunnel` и модель состояния `TunnelState` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/tunnel/ProdTunnel.kt`
- [x] T008 [P] Реализовать `CertFailureClassifier` (причины: expired/revoked/untrusted/unknown) в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/tunnel/CertFailureClassifier.kt`
- [x] T009 [P] Реализовать `ChannelFailureClassifier` (канальный/сертификатный/логический отказ) в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/tunnel/ChannelFailureClassifier.kt`
- [x] T010 Написать unit-тесты конфигурации в `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/tunnel/ProdTunnelConfigTest.kt` (дефолты, невалидный ключ, пересечение подсетей, обязательные поля при `ENABLED=true`)
- [x] T011 Написать unit-тесты классификаторов в `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/tunnel/FailureClassifiersTest.kt` (канальные/сертификатные/логические отказы, цепочка cause)

**Checkpoint**: контракты и классификаторы готовы, тесты зелёные.

---

## Phase 3: User Story 2 — Туннель поднимает и держит karaoke-app (Priority: P1) 🎯 MVP

**Goal**: приложение само поднимает туннель, держит его, восстанавливает после обрыва и
сообщает состояние (FR-027…FR-030, FR-002, FR-008).

**Independent Test**: остановить туннель → приложение фиксирует событие с причиной и
поднимает туннель обратно не более чем за 60 с; перезапуск приложения → туннель поднят
до начала работы с прод-каналами.

### Tests for User Story 2

- [x] T012 [P] [US2] Написать unit-тесты `WireGuardProdTunnel` (подменённый `TunnelCommandRunner`) в `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/tunnel/WireGuardProdTunnelTest.kt`: подъём, повторный вызов идемпотентен, устаревшее рукопожатие → перезапуск, отсутствие ключа → `no_key`
- [x] T013 [P] [US2] Написать unit-тесты `ProdTunnelSupervisor` в `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/tunnel/ProdTunnelSupervisorTest.kt`: цикл опроса, восстановление после stale, отсутствие повторного восстановления

### Implementation for User Story 2

- [x] T014 [US2] Реализовать `WireGuardProdTunnel` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/tunnel/WireGuardProdTunnel.kt`: `wg-quick up/down`, чтение `latest-handshakes`, вычисление возраста рукопожатия, перезапуск при устаревании
- [x] T015 [US2] Реализовать `ProdTunnelSupervisor` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/tunnel/ProdTunnelSupervisor.kt`: периодический опрос, восстановление, запись событий `TUNNEL-001…007` через `AppLog`
- [x] T016 [US2] Управлять переопределением резолвинга имени хоста: при поднятом туннеле — запись `<prodHost> <dnsOverrideHost>` в `PROD_TUNNEL_HOSTS_FILE`, при падении — удаление (в `WireGuardProdTunnel.kt`)
- [x] T017 [US2] Подключить туннель к старту приложения: инициализация до работы с прод-каналами в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/KaraokeAppService.kt`
- [x] T018 [US2] Добавить в образ `deploy/karaoke-app/Dockerfile` пакеты `wireguard-tools`, `iproute2`, `iptables`
- [x] T019 [US2] Объявить в `deploy/docker-compose-app.yml`: `cap_add: [NET_ADMIN]`, `devices: [/dev/net/tun]`, `sysctls: net.ipv4.ip_forward=1`, монтирование secret-каталога read-only, переменные `PROD_TUNNEL_*`
- [x] T020 [US2] Добавить в `deploy/do.env.example` и `deploy/prod-single-host/do.env.example` placeholders переменных `PROD_TUNNEL_*` (без секретов)

**Checkpoint**: туннель поднимается и восстанавливается приложением; события в логе.

---

## Phase 4: User Story 1 — Смена адреса админ-машины не останавливает конвейер (Priority: P1)

**Goal**: конвейер работает по постоянному внутреннему адресу; смена внешнего адреса и
кратковременный обрыв WAN не приводят к простою и потере заданий (FR-001, FR-003,
FR-004, FR-006, FR-033).

**Independent Test**: сменить/имитировать смену внешнего адреса → конфигурация прод-хоста
не меняется, каналы БД и MinIO доступны; оборвать WAN на ≤ 60 с во время задания →
задание возвращается в очередь и завершается.

### Tests for User Story 1

- [x] T021 [P] [US1] Написать unit-тесты классификации канальных отказов и поведения очереди в `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/tunnel/ChannelRetryPolicyTest.kt` (канальный отказ → возврат в очередь, лимит попыток, логический отказ → ERROR)

### Implementation for User Story 1

- [x] T022 [US1] Перевести задание в `WAITING` вместо `ERROR` при канальном отказе в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcessWorker.kt` (счётчик попыток, лимит `PROD_TUNNEL_MAX_CHANNEL_RETRIES`)
- [x] T023 [US1] Добавить событие `DB-CERT-001` при отказе по сертификату в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeConnection.kt` (через `CertFailureClassifier`)
- [x] T024 [US1] Переключить адреса прод-каналов на постоянный внутренний адрес в конфигурации: `DB_REMOTE_HOST`, `STORAGE_REMOTE_ENDPOINT` (документировано в runbook'е; фактическое переключение — фаза 8)
- [x] T025 [US1] Реализовать `deploy/prod-tunnel/install-admin-tunnel.sh`: `--check` (проверки без изменений), `--apply` (каталог 700, ключ 600, конфиг 600, печать публичного ключа), `--print-public-key`
- [x] T026 [US1] Реализовать `deploy/prod-tunnel/host-route.sh`: `--check`/`--apply`/`--remove` маршрута `10.77.0.0/24` через контейнер `karaoke-app`
- [x] T027 [US1] Добавить юнит `deploy/prod-tunnel/karaoke-tunnel-host-route.service` (установку выполняет владелец) — маршрут хоста к туннелю

**Checkpoint**: конвейер не зависит от внешнего адреса; задания переживают обрыв WAN.

---

## Phase 5: User Story 3 — К прод-БД допускается только владелец ключа (Priority: P1)

**Goal**: доступ к прод-БД по клиентскому сертификату; сертификат сервера проверяется
полностью (FR-011, FR-012, FR-013, FR-014, FR-015, FR-016).

**Independent Test**: подключение с валидным сертификатом — успех; без сертификата или
с недоверенным/просроченным — отказ сервера, в логе `DB-CERT-001` с причиной.

### Tests for User Story 3

- [x] T028 [P] [US3] Расширить `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/RemoteJdbcUrlTest.kt` — параметры `sslcert`/`sslkey`/`sslrootcert`, режим `verify-full`, отсутствие параметров при пустых значениях
- [x] T029 [P] [US3] Написать unit-тесты `CertFailureClassifier` (причины просрочки/отзыва/недоверенности) в `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/tunnel/CertFailureClassifierTest.kt`

### Implementation for User Story 3

- [x] T030 [US3] Расширить `buildRemoteJdbcUrl` параметрами клиентского сертификата в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Connection.kt` (`DB_REMOTE_SSL_CERT`, `DB_REMOTE_SSL_KEY`, `DB_REMOTE_SSL_ROOT_CERT`)
- [x] T031 [US3] Прокинуть переменные `DB_REMOTE_SSL_*` в окружение контейнера в `deploy/docker-compose-app.yml`
- [x] T032 [US3] Подготовить процедуру выпуска/перевыпуска сертификатов (CA вне репозитория, `600`; срок 90 дней) в `deploy/prod-tunnel/README.md` и `knowledge/guidelines/runbooks/prod-tunnel.md`
- [x] T033 [US3] Описать конфигурацию прод-хоста для mTLS (`ssl=on`, `ssl_ca_file`, `pg_hba.conf` c `hostssl … clientcert=verify-ca`, пароль сохранён как запасной путь) в `knowledge/guidelines/runbooks/prod-tunnel.md`

**Checkpoint**: клиентская часть mTLS готова; серверная — фаза 8 (согласие владельца).

---

## Phase 6: User Story 4 — Админские операции работают через туннель (Priority: P1)

**Goal**: `POST /changerecords` и `POST /api/properties/setproperty` идут внутри туннеля
при неизменном имени хоста и сертификате сервера (FR-021…FR-025, FR-029).

**Independent Test**: вызвать оба эндпоинта с админской машины так, чтобы трафик физически
шёл через туннель; убедиться, что allow-list прод-хоста содержит постоянный внутренний
адрес, а TLS-сертификат сервера прежний и проверяется.

### Tests for User Story 4

- [x] T034 [P] [US4] Написать тест разрешения имени хоста и адреса назначения в `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/tunnel/HostsOverrideTest.kt` (запись/удаление переопределения, сохранение прочих строк `/etc/hosts`)

### Implementation for User Story 4

- [x] T035 [US4] Реализовать управление записью в `PROD_TUNNEL_HOSTS_FILE` (идемпотентно, с маркером-комментарием, без порчи чужих строк) в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/tunnel/HostsOverride.kt`
- [x] T036 [US4] Объявить направление трафика контейнера к прод-сайту декларативно в `deploy/docker-compose-app.yml` (маршрут/резолвинг через туннель, параметры `PROD_TUNNEL_*`)
- [x] T037 [US4] Подготовить шаблон allow-list прод-хоста с постоянным внутренним адресом в `deploy/prod-single-host/` (отдельный файл-шаблон; боевую правку выполняет владелец — фаза 8)
- [x] T038 [US4] Описать рабочий пример вызова kill-switch через туннель в `knowledge/guidelines/runbooks/prod-tunnel.md`

**Checkpoint**: оба админских HTTP-канала идут через туннель; имя хоста и сертификат прежние.

---

## Phase 7: User Story 6 — Дежурный инженер понимает, где сломалось (Priority: P3)

**Goal**: отказ любого участка различим по коду события (FR-008, FR-028, FR-016,
SC-008, SC-010).

**Independent Test**: смоделировать недоступность туннеля/сертификата/БД/сайта — в логах и
мониторинге появляются различимые коды.

### Tests for User Story 6

- [x] T039 [P] [US6] Написать unit-тесты проверки мониторинга в `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/monitor/ProdTunnelCheckTest.kt` (up/down/stale/disabled → ожидаемые алерты и severity)

### Implementation for User Story 6

- [x] T040 [US6] Реализовать проверку `ProdTunnelCheck` (ключ `prod.tunnel`, категория «Прод-канал») в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/monitor/checks/ProdTunnelCheck.kt`
- [x] T041 [US6] Зарегистрировать проверку в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/monitor/MonitorRegistry.kt`
- [x] T042 [US6] Дополнить реестр кодов событий в `knowledge/guidelines/runbooks/log-correlation.md` (§ 1a: `TUNNEL-001…007`, `DB-CERT-001`) и § 1d (обрыв WAN vs туннель)
- [x] T043 [US6] Добавить в `knowledge/guidelines/runbooks/log-correlation.md` таблицу различения участников (туннель / сертификат / БД / сайт)

**Checkpoint**: каждый отказ прод-канала имеет код участника.

---

## Phase 8: User Story 5 — Прод-каналы закрыты из интернета (Priority: P2) — ТОЛЬКО ПО СОГЛАСИЮ ВЛАДЕЛЬЦА

**Goal**: публичные порты прод-каналов закрыты, доступ — только из туннеля (FR-005,
FR-017, FR-018, SC-005).

**Independent Test**: с внешней машины соединение к 5433/9000 не устанавливается; из
туннеля — работает.

- [x] T044 [US5] Подготовить инструкцию закрытия портов и проверки отката в `knowledge/guidelines/runbooks/prod-tunnel.md` (Фазы 2–4, ожидаемые результаты, время отката ≤ 15 мин)
- [x] T044a [US6] Устранить ложный CRITICAL «прод недоступен»: в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/monitor/checks/ProdContainerCheck.kt` при неудачном пиге сайта/БД сверяться с состоянием туннеля и указывать участника («туннель недоступен» вместо «прод недоступен») — см. analysis.md A2
- [x] T045 [US5] Реализовать режим туннеля в `deploy/admin-ip-allowlist.sh`: `ALLOWLIST_MODE=tunnel` не определяет и не пишет публичный адрес, только проверяет наличие `10.77.0.1` во всех блоках (FR-009)
- [x] T046 [US5] Добавить self-test-сценарий «режим туннеля» в `deploy/admin-ip-allowlist.sh` (публичный адрес не попадает в allow-list; расхождение — событие и ненулевой код возврата)
- [x] T047 [US5] Добавить guard `tools/check-prod-tunnel-guardrails.sh`: инварианты туннеля (подсеть, `NET_ADMIN`, `/dev/net/tun`, отсутствие секретов в репозитории, единый allow-list во всех копиях, срок сертификата в шаблонах)
- [x] T048 [US5] Подключить guard к pre-commit и к CI (`.forgejo/workflows/lint.yml`, `config/ci/checks.yml`, `.github/workflows/lint.yml`)
- [ ] T049 [US5] **По согласию владельца**: установить пир на прод-хосте, применить allow-list = `10.77.0.1`, выполнить контрольные проверки и зафиксировать факты в отчёте `specs/733-prod-tunnel-mtls/report.md` (включая **снимок** `/etc/nginx/nginx.conf` и `80to8897` до и после — SC-001)
- [ ] T050 [US5] **По согласию владельца**: закрыть публичные порты 5433/9000 и проверить недоступность извне; зафиксировать факты в отчёте
- [ ] T051 [US5] **По согласию владельца**: переключить каналы приложения на постоянный внутренний адрес по Pass 380 — 1) проверить, что нет заданий в `WORKING`/`CREATING`; 2) остановить очередь (`curl -s http://localhost:8898/process/stop`); 3) дождаться дренажа; 4) `bash deploy/do.sh start_app`; 5) запустить очередь (`/process/start`); зафиксировать факты в отчёте
- [ ] T052a [US3] **По согласию владельца**: выпустить CA и сертификат сервера прод-БД (SAN включает `10.77.0.2`), скопировать CA-сертификат в `ssl_ca_file`, включить `ssl=on` в `postgresql.conf`, заменить правило `pg_hba.conf` на `hostssl karaoke SvoeMestoKaraokeUser905 10.77.0.1/32 scram-sha-256 clientcert=verify-ca` (пароль остаётся запасным путём — FR-015), перезапустить `karaoke-db` и зафиксировать факты в отчёте (см. analysis.md A1)

**Checkpoint**: прод-каналы доступны только из туннеля; публичный путь — откат (≤ 15 мин).

---

## Phase 9: Polish & Cross-Cutting Concerns

- [x] T052 [P] Создать per-feature документ `knowledge/product/features/prod-tunnel.md` (6 секций FR-009: что делает, зачем, как работает, инварианты, известные ловушки, ссылки)
- [x] T053 [P] Создать ADR `knowledge/adr/local-0018-prod-wireguard-tunnel.md` (контекст, решения, отклонённые альтернативы, последствия)
- [x] T054 [P] Создать runbook `knowledge/guidelines/runbooks/prod-tunnel.md` (установка, эксплуатация, диагностика по кодам, откат, процедура перевыпуска сертификатов)
- [x] T055 Обновить `knowledge/system/infra/deploy-overview.md` (раздел «Туннель к проду», адреса, где живёт конфигурация)
- [x] T056 Обновить `knowledge/guidelines/runbooks/prod-config-delivery.md` (доставка конфигурации туннеля на прод, правила отката)
- [x] T057 Обновить `knowledge/product/features/admin-ip-allowlist.md` (режим туннеля: что автоматика не делает)
- [x] T058 [P] Обновить `knowledge/product/README.md` (индекс per-feature документов) — если требуется линтером
- [x] T059 Реализовать изолированный стенд проверки `deploy/prod-tunnel/selftest.sh` (namespace'ы: рукопожатие, неизменность маршрута по умолчанию, доступ «хоста» через SNAT с тем же адресом источника, переопределение резолвинга, детектор устаревшего рукопожатия, отказ при отсутствии ключа)
- [x] T060 Прогнать `bash deploy/prod-tunnel/selftest.sh` и зафиксировать вывод в `specs/733-prod-tunnel-mtls/report.md`
- [x] T061 Прогнать структурные проверки базы знаний: `bash tools/check-knowledge-structure.sh`, `python3 tools/lint-knowledge.py --baseline config/knowledge/baseline-knowledge-lint.txt`, `python3 tools/check-ssot-impact.py`
- [x] T062 Прогнать обязательные проверки из `AGENTS.md`: `./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel`, `./gradlew :karaoke-app:test`, `./gradlew :karaoke-web:ktlintCheck`
- [x] T063 Прогнать pre-commit-чеклист `karaoke-pre-commit` (скилл) по своим файлам
- [x] T064 Написать финальный отчёт `specs/733-prod-tunnel-mtls/report.md` (критерии → команда → результат; что сделано; что требует согласия владельца; откат)
- [x] T065 Добавить комментарий в Plane: `bash tools/tracker.sh add-comment 407 --file specs/733-prod-tunnel-mtls/report.md`
- [x] T066 Запушить ветку `733-prod-tunnel-mtls` и создать PR через API Forgejo; номер PR указать в отчёте

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: без зависимостей
- **Foundational (Phase 2)**: зависит от Setup — блокирует все истории
- **US2 (Phase 3)**: зависит от Foundational — база для US1/US3/US4/US6
- **US1 (Phase 4)**: зависит от US2 (туннель нужен для проверки устойчивости)
- **US3 (Phase 5)**: зависит от Foundational; серверная часть — от Phase 8 (согласие)
- **US4 (Phase 6)**: зависит от US2 (переопределение резолвинга — часть туннеля)
- **US6 (Phase 7)**: зависит от US2 и US3 (коды и классификаторы)
- **US5 (Phase 8)**: зависит от US2, US3, US4; прод-часть — только по согласию владельца
- **Polish (Phase 9)**: зависит от всех нужных историй

### User Story Dependencies

- **US2 (P1)**: стартует после Foundational, ни от кого не зависит
- **US1 (P1)**: после US2
- **US3 (P1)**: после Foundational (клиентская часть), серверная — по согласию
- **US4 (P1)**: после US2
- **US6 (P3)**: после US2 и US3
- **US5 (P2)**: после US2/US3/US4, прод-операции — по согласию владельца

### Parallel Opportunities

- T002, T003 (Setup) — параллельно
- T006, T008, T009 (Foundational) — параллельно
- T012, T013 (тесты US2) — параллельно
- T028, T029 (тесты US3) — параллельно
- T039 (тест US6) — параллельно с реализацией US3
- T052…T058 (документация) — параллельно между собой
- Фазы историй (кроме зависимостей выше) — параллельно

---

## Parallel Example: User Story 2

```bash
# Одновременно:
Task: "Написать unit-тесты WireGuardProdTunnel в karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/tunnel/WireGuardProdTunnelTest.kt"
Task: "Написать unit-тесты ProdTunnelSupervisor в karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/tunnel/ProdTunnelSupervisorTest.kt"

# Затем:
Task: "Реализовать WireGuardProdTunnel в karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/tunnel/WireGuardProdTunnel.kt"
Task: "Реализовать ProdTunnelSupervisor в karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/tunnel/ProdTunnelSupervisor.kt"
```

---

## Implementation Strategy

### MVP First (User Story 2)

1. Phase 1 Setup → 2. Phase 2 Foundational → 3. Phase 3 (US2: туннель поднимает и держит приложение)
4. **STOP и VALIDATE**: туннель поднят локально (стенд), состояние видно в логах и мониторинге
5. Далее — US1 (устойчивость), US3 (mTLS), US4 (HTTP-каналы), US6 (наблюдаемость), US5 (закрытие портов — по согласию)

### Incremental Delivery

1. Foundational → US2 → US1 → US3 → US4 → US6 → US5 (по согласию) → Polish
2. Каждая история добавляет ценность, не ломая предыдущие
3. Публичный каналprodа остаётся рабочим до Фазы 5 (US5) — это и есть откат

---

## Notes

- **[P]** — разные файлы, нет зависимостей
- **[Story]** — трассировка к пользовательским историям спеки
- Тесты пишутся до реализации и обязаны падать на текущем коде
- Прод-операции (T049…T051, T052a) — только по согласию владельца; до них работа ограничена подготовкой и локальными проверками
- Коммит после каждой логической группы задач

## Итог реализации (2026-11-09)

Закрыто **61** задача из 67. Не закрыты **6** — все они требуют согласия владельца
на изменение прод-хоста (установка пира, allow-list, выпуск сертификатов,
переключение каналов с перезапуском `karaoke-app` по Pass 380, закрытие публичных
портов) либо являются финальными шагами workflow (комментарий в Plane, PR).

| Задача | Почему не закрыта | Что нужно от владельца |
|---|---|---|
| T049 | установка пира «prod» и allow-list на прод-хосте | согласие на изменение прод-хоста |
| T050 | закрытие публичных портов `5433`/`9000` | согласие + успешная Фаза 3 |
| T051 | переключение каналов приложения (Pass 380) | согласие на перезапуск `karaoke-app` |
| T052a | выпуск CA и сертификата сервера БД, `ssl=on`, `clientcert=verify-ca` | согласие на изменение конфигурации БД прода |
| T065 | комментарий в Plane | выполняется после merge |
| T066 | PR | — |

Точные команды и ожидаемые результаты — в `specs/733-prod-tunnel-mtls/report.md` § 6.
Прод-хоста в этой итерации **не касались**: изменений на `188.127.240.124` — ноль.
