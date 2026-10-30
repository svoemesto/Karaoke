# Отчёт 676-oom-karaoke-app

**Issue**: KARAOKE-245 (Plane) · **Ветка**: `676-oom-karaoke-app` · **Дата**: 2026-10-30
**Спека**: `specs/676-oom-karaoke-app/spec.md` (контракт утверждён владельцем; правок FR/SC не было)
**Инцидент**: 2026-10-23, OOM в `karaoke-app` на admin-машине `nsa-i9` + ложный CRITICAL «прод недоступен» на 5+ часов

## Что сделано

Пять блоков спеки: A (конечность раскладки публикаций), B (лимиты памяти), C (дампы heap),
D (мониторинг загрузки heap), E (база знаний).

### A. Конечность раскладки публикаций (FR-001…FR-005)

Починка бесконечного цикла **уже была в master** (`4d643554`, BR-188, 2026-10-24) — спека 676
её не переписывает, а закрывает недостающее: **тест уровня вызывающих**.

`karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/model/PublicationGridCallerTest.kt`
(4 теста): 500 неопубликованных песен (10 авторов по 50) → сетка **50 строк × 10 столбцов**,
все 500 песен в раскладке ровно по разу, первый ряд — по самой ранней песне каждого автора;
отдельные сценарии для skip-авторов и пустого набора.

**Product-код не изменён** (FR-003): подменена граница JDBC (Mockito из
`spring-boot-starter-test`, готовый мок `MockJdbc` переиспользован). JPA не появился (ADR-0001).
`popPublicationStacks` осталась в `Publication.kt` с буквальной строкой условия цикла —
контрактный source-тест не тронут.

**Доказательство «падает на прежней реализации»** (временная подмена тела раскладки в обоих
вызывающих на прежнее — условие выхода по захваченному максимуму; откат сразу):

| Код | На прежней реализации | На текущей |
|---|---|---|
| `PublicationGridCallerTest` (новый) | **FAILED**: `OutOfMemoryError at Publication.kt:1739` — та же сигнатура, что в инциденте | зелёный |
| `audit/PkgS212BehaviorTest` (16 тестов) | **зелёный** | зелёный |
| `audit/PkgS212SourceContractsTest` (13 тестов) | **зелёный** | зелёный |

То есть прежние тесты регрессию в вызывающем коде действительно не ловили — ровно то,
что фиксирует FR-004.

### B. Предел памяти контейнера (FR-006…FR-008)

`deploy/docker-compose-app.yml`, сервис `karaoke-app`:

- `mem_limit: 8g` — предел памяти cgroup;
- `command: ${APP_JAVA_OPTS} -Xmx6g -jar /app.jar` — **`-Xmx6g` строго ПОСЛЕ
  `${APP_JAVA_OPTS}`**: последний `-Xmx` побеждает (проверено эмпирически:
  `java -Xmx8g -Xmx6g -XX:+PrintFlagsFinal -version` → `MaxHeapSize = 6442450944` = 6 GiB).
  Так устаревшее `-Xmx8g` из неотслеживаемого `deploy/.env` перекрывается, остальные флаги
  окружения сохраняются, а **фактический лимит задаёт репозиторий** — значение в `.env` на
  него не влияет;
- связь «heap 6 ГБ < предел 8 ГБ» видна в compose рядом и **проверяется автоматическим
  гейтом**.

### C. Дампы состояния heap и политика хранения (FR-009…FR-013)

- `-XX:+HeapDumpOnOutOfMemoryError` + `-XX:HeapDumpPath=/dumps` — дамп пишется на том,
  который переживает перезапуск контейнера;
- том `./karaoke-db/dumps:/dumps` — тот же каталог, что у `karaoke-db` (BR-046); дамп
  читается с хоста без входа в контейнер;
- `deploy/heap-dump-retention.sh` — политика хранения: последние **2** дампа, суммарный лимит
  тома **20 ГБ**, вытеснение **по возрасту**, защита **60 минут** (недописанный дамп не
  удаляется). Работает **без установки** (`bash deploy/heap-dump-retention.sh`), есть
  `--self-test`;
- `deploy/heap-dump-retention.{service,timer}` — systemd-юниты по образцу
  `deploy/karaoke-db-backup.*`. **Не устанавливались и не включались** — операции с systemd
  на машине только по слову владельца;
- политика трогает **только `*.hprof`** — SQL-дампы БД в общем каталоге не вытесняются
  никогда;
- содержимое `deploy/karaoke-db/dumps/*` — в `.gitignore` (дампы в git не попадают).

### D. Мониторинг загрузки heap (FR-014…FR-019)

`karaoke-app/.../monitor/checks/HeapUsageCheck.kt` — 8-я проверка мониторинга:

- источник данных — `Runtime` (`maxMemory`, `totalMemory - freeMemory`): не зависит от
  состава JRE/JDK и от наличия `java.management`; MXBean процент от максимума heap точнее
  не даёт;
- пороги **75 %** (WARNING) и **90 %** (CRITICAL) — из конфигурации (env
  `MONITOR_HEAP_WARN_PERCENT` / `MONITOR_HEAP_CRITICAL_PERCENT`, ADR `local-0001`),
  нечитаемое или несогласованное значение → дефолты;
- загрузка публикуется **на каждом тике** (раз в минуту) — строка INFO в категории
  `infra.monitor.heap`, формат `event:key=value`; при превышении — `heap:thresholdExceeded`,
  при возврате в норму — `heap:recovered`;
- состояние — **только в памяти** (`@Volatile`), новых персистентных ключей нет;
- `body` алерта не содержит изменяющихся чисел (процент — в `detail`), иначе
  `contentHash()` менялся бы на каждом тике и сообщение «мигало» бы read/unread;
- регистрация — одна строка в `MonitorRegistry.checks`; тик общий, раз в минуту.

### E. База знаний (FR-020…FR-021)

12 документов из FR-020 + `knowledge/system/infra/ci-tools.md` (13-й) приведены в
соответствие фактам; `knowledge/adr/local-0013-…` **не правился** (ADR append-only — см.
«Отклонения»); заведён ADR `local-0014` и per-feature документ
`knowledge/product/features/heap-memory-guardrails.md`; Linking Protocol L3→L2→L1 соблюдён
(проверка `HeapUsageCheck` перечислена в `knowledge/domains/monitoring/domain.md`, ADR — в
`knowledge/adr/README.md`, per-feature — в `knowledge/product/README.md`).

### Гейты развёртывания (BR-630)

- `tools/check-memory-guardrails.sh` — **новый быстрый guard** (без внешних зависимостей, с
  `--self-test`): предел памяти, максимум heap и отношение «heap < предел», порядок флагов,
  флаги дампа, том, пороги, параметры retention-политики. Подключён **в pre-commit и в обе
  копии CI-workflow** (40 гейтов в каждой);
- `tools/check-deploy-hardening.sh` — блок BR-630 **делегирует** новому guard'у (вывод +
  код возврата): реализация правил одна, расхождение между двумя инструментами невозможно.
  Сам `check-deploy-hardening.sh` по политике репозитория остаётся ручным.

## Проверки (фактический вывод)

### Код и модульные тесты

```bash
$ GRADLE_USER_HOME=… ./gradlew :karaoke-app:test
BUILD SUCCESSFUL — тестов=957, провалов=0, пропущено=0
```

Baseline до правок (T002): **941 тест, 0 провалов** → после фичи **957** (+16: 4 в
`PublicationGridCallerTest` + 12 в `HeapUsageCheckTest`).

### Линтеры и сборка (kara-post-edit)

| Шаг | Команда | Результат |
|---|---|---|
| Backend compile | `./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel` | BUILD SUCCESSFUL |
| Kotlin-линтер | `./gradlew ktlintCheck` | BUILD SUCCESSFUL |
| Backend bootJar | `./gradlew :karaoke-app:bootJar :karaoke-web:bootJar --parallel` | BUILD SUCCESSFUL |
| ESLint + Prettier | `cd webvue3 && npm run lint` + `format:check`; то же для `karaoke-public` | без ошибок |
| Vite-сборки | `cd webvue3 && npm run build`; то же для `karaoke-public` | `✓ built in 8.39s` / `✓ built in 4.11s` |
| Docker-образ | `cd deploy && bash do.sh build_webvue3` | `sm-karaoke.ru:5443/karaoke/karaoke-webvue3:1` собран (только сборка — без push и рестарта) |

### Гейты деплой-контура

| Команда | Результат |
|---|---|
| `bash tools/check-deploy-hardening.sh` | **PASS=97 FAIL=0** (BR-630 делегирован `check-memory-guardrails.sh`) |
| `bash tools/check-deploy-hardening.sh --self-test` | **все 29 правил ловят пустое (битое) состояние** |
| `bash tools/check-memory-guardrails.sh` | **PASS=12 FAIL=0** |
| `bash tools/check-memory-guardrails.sh --self-test` | **правила ловят битое состояние и пропускают корректное** |
| `bash deploy/heap-dump-retention.sh --self-test` | **OK** (последние 2 / ≤ 20 ГБ / вытеснение по возрасту) |
| `bash tools/check-ci-workflow-sync.sh` | **OK: 40 гейтов в каждой копии** |

### Гейты базы знаний

| Команда | Результат |
|---|---|
| `python3 tools/lint-knowledge.py` | **All documentation passes architectural style and structural checks** |
| `bash tools/check-knowledge-structure.sh` | **OK: 9/9 проверок** |
| `python3 tools/check-ssot-impact.py` | **OK: 0 нарушений** (24 изменённых файла, 5 значимых) |
| `bash tools/check-feature-doc.sh knowledge/product/features/*.md` | **Все документы валидны** |
| `python3 tools/check-audit-registry.py` | **OK**: 511 кодов в § 3, 552 зарегистрировано, 292 встречается в `knowledge/` |

### Pre-commit (перед КАЖДЫМ коммитом, без `--no-verify` и без SKIP)

```bash
$ pre-commit run --all-files
34 хука — 34 Passed
```

Состав: guard'и gradle-user-home / docker-config / container-restart / frontend-build /
docker-image-tags / no-jpa-imports / no-mp4-mentions / subagent-isolation / security-headers /
recordhash / confirm-escape / secret-in-argv / gradle-wrapper / build-config /
marker-escape / **memory-guardrails (BR-630)** / **heap-dump-retention self-test (BR-630)**;
ktlint; eslint ×2; prettier ×2; lychee (offline-ссылки); check per-feature document;
check documentation references; no docs/ directory; audit registry; CI workflow sync;
@Value placeholders; SongEdit/endpoint field coverage; block direct commits to master.

### Quickstart § 1–4 (T045)

```bash
$ ./gradlew :karaoke-app:test --tests '*PublicationGridCallerTest*' --tests '*HeapUsageCheckTest*' --tests '*PkgS212*'
BUILD SUCCESSFUL
```

§ 3 — конфигурация прочитана и сверена с контрактом: `mem_limit: 8g` (`:99`),
`-Xmx6g` после `${APP_JAVA_OPTS}` (`:112`), `-XX:+HeapDumpOnOutOfMemoryError` +
`-XX:HeapDumpPath=/dumps` (`:112`), том `./karaoke-db/dumps:/dumps` (`:86`), пороги
`MONITOR_HEAP_WARN_PERCENT=75` / `MONITOR_HEAP_CRITICAL_PERCENT=90` (`:62-63`).
§ 4 — гейты базы знаний (см. выше). § 5 (стенд) — **не выполнялся**: требует работы с
контейнером и согласия владельца (owner-action № 2).

## Что осталось красным и почему

**Красного нет.** Все перечисленные гейты зелёные: 957 тестов / 0 провалов, deploy-guard
97 PASS / 0 FAIL, новый guard BR-630 — 12 PASS / 0 FAIL, retention self-test — OK, CI-копии
синхронны (40 гейтов в каждой), база знаний — lint/structure/ssot/feature-doc/registry зелёные,
pre-commit — 34/34.

Не выполнялось (и почему):

| Что | Почему |
|---|---|
| Стендовая проверка SC-004 (искусственное исчерпание памяти в контейнере) | требует работы с контейнером `karaoke-app` и согласия владельца; зафиксировано как owner-action № 2 |
| Установка и включение systemd-таймера уборки дампов | операции с systemd на машине — только по слову владельца; owner-action № 1 |
| `docker compose up` / рестарт контейнеров / деплой | запрещено без согласия; push/PR/merge — только по слову лида |
| Сборка Docker-образа `karaoke-app` | образы приложения собираются `deploy/do.sh build_app` при деплое; в рамках задачи собран только образ webvue3 (фронтенд не менялся, но образ пересобран по чеклисту `kara-post-edit`) |

## Решения владельца, зафиксированные в артефактах

1. **Своп на admin-машине не поднимаем** (2026-10-30). Факт на дату: 62 ГБ RAM, 25 занято,
   36 доступно; `/swapfile` 64 ГБ, занято 21,7 ГБ, подкачки сейчас нет (`vmstat`: si≈0,
   so=0) → занятый своп — припаркованные холодные страницы, а не признак давления.
   Отклонённая альтернатива «поднять своп 64 → 128 ГБ» — с обоснованием в ADR `local-0014`:
   (а) проблема инцидента не «мало RAM», а «контейнер без cgroup-лимита»; (б) в cgroup v2
   лимит RAM и лимит свопа разные — при наличии свопа контейнер уходит в подкачку вместо
   OOM-kill, то есть тихое подвисание вместо быстрого отказа (хуже рестарта и повторяет класс
   ложной тревоги «прод недоступен»); (в) своп и том дампов на одном разделе NVMe (86 % занято).
   Выбранное решение — **ограничение контейнера** (`mem_limit: 8g` + `-Xmx6g`).
2. **`local-0013` не правим** — ADR append-only; уточнение области решения («канал доставки
   для `karaoke-app` — журнал/SLF4J по FR-018») вынесено в `local-0014` и в домен
   `monitoring`.
3. **`cpus` не добавляем** — FR-006/FR-007 требуют только предел памяти; CPU-лимит для
   контейнера с ffmpeg/melt/Playwright — отдельное эксплуатационное решение.
4. **`deploy/.env` не трогаем** и не печатаем — файл вне git и с секретами. Фактический лимит
   задаёт compose; значение в `.env` инертно.
5. **Systemd на машине не устанавливаем** — только файлы в репозитории; активация — за
   владельцем.
6. **Retention-политика проверяется self-test'ом в автоматических гейтах**; сквозная
   стендовая часть SC-004 (JVM действительно оставляет дамп после OOM) — owner-action.

## Owner-action'ы (вне репозитория)

| # | Действие | Как | Почему за владельцем |
|---|---|---|---|
| 1 | Установить и включить таймер уборки дампов | `systemctl enable --now heap-dump-retention.timer` (юниты — в `deploy/`) | операции с systemd на машине — только по слову владельца; до этого работает ручной `bash deploy/heap-dump-retention.sh` |
| 2 | Стендовая проверка SC-004 | искусственно исчерпать память в контейнере `karaoke-app`, убедиться, что в `deploy/karaoke-db/dumps/` появился `java_pid*.hprof` | требует работы с контейнером (согласие) и перезапуска по Pass 380 |
| 3 | Почистить `deploy/.env` от устаревшего `-Xmx8g` | ручная правка файла вне git | файл с секретами вне git; на лимит не влияет (compose перекрывает), но значение — ловушка |
| 4 | Решение по CPU-лимиту (`cpus`) | — | вне контракта FR-006/FR-007 |
| 5 | Кандидат на правку governance-файла: `constitution.md:209` | утверждает, что образ `eclipse-temurin:22-jre-jammy` — для «karaoke-web/app»; по факту temurin только у `karaoke-web` (`deploy/karaoke-web/Dockerfile:1`), `karaoke-app` — `ubuntu:22.04` + `openjdk-17-jre-headless` (`deploy/karaoke-app/Dockerfile:23`, `:48`) | конституция — governance-файл: правка только через semver-бамп по процедуре владельца |

## Отклонения от контракта (приняты лидом)

1. **`knowledge/adr/local-0013-infra-backups-and-monitoring.md` не правился.** FR-020 перечисляет
   его среди 12 документов к приведению в соответствие, но ADR в этом репозитории
   **append-only** (`knowledge/adr/README.md`): «нельзя редактировать или удалять ADR после
   принятия». Уточнение области решения вынесено в новый ADR `local-0014` («Следствия») и в
   документы домена `monitoring`. FR/SC спеки при этом не менялись; решение владельца
   запрашивается отдельно.
2. **BR-код нового guard'а — BR-630, а не BR-629** (как предполагалось в плане): BR-629 уже
   занят (`karaoke-web`, контракты публичного API). Уточнено в `research.md`, `analysis.md`
   и `tasks.md`.
3. **Правило BR-630 вынесено в отдельный быстрый guard**, а не осталось в ручном
   `check-deploy-hardening.sh`: тот по политике репозитория в CI не вызывается, а связь
   «heap < предел» — смысл задачи и должна проверяться автоматически. Реализация правил
   одна — ручной инструмент делегирует новому guard'у.

## Известные ограничения

- **Три легаси-точки входа раздела публикаций** (`GET /unpublications`,
  `POST /api/unpublications`, `POST /api/skipedpublications`) **без авторизации**; ограничение
  доступа — вне объёма (KARAOKE-335), выпилка мёртвого кода легаси-пути — KARAOKE-336.
- **Лимита на объём раскладки публикаций нет** — защита только от бесконечного цикла.
- **Ограничения доступа к трём точкам входа и авторизация легаси-страниц** — не в объёме.
- **Внешней доставки алертов нет** (Telegram/email) — канал мониторинга: журнал + SSE +
  `GET /api/monitor/alerts`.
- **Значения порогов в `deploy/.env` не действуют** — фактический лимит задаёт compose.

## Не выполнено (вне фазы)

- `tracker.sh add-comment 245 --file specs/676-oom-karaoke-app/report.md` → `mark-review 245`
  выполняются **после merge** (делает лид).
- Push / PR / merge — только по слову лида; в этой ветке ничего не пушилось.
