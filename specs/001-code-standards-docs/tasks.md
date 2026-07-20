# Tasks: 001-code-standards-docs

**Input**: Design documents from `/specs/001-code-standards-docs/`
**Prerequisites**: plan.md (✅), spec.md (✅), research.md (✅), data-model.md (✅), contracts/ (✅)

**Tests**: SC-002 покрывает baseline-метрики (через `tools/baseline-stats.sh`); SC-005 — link-check через `lychee`. Юнит-тесты для самих линтеров не пишутся — линтеры и есть проверка. Сценарии «end-to-end» — в `quickstart.md` (10 шагов).

**Organization**: Задачи сгруппированы по User Story. P1 stories — фундамент, MVP; P2 — per-feature документы; P3 — KDoc/JSDoc.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: можно запускать параллельно (разные файлы, нет зависимостей).
- **[Story]**: к какой User Story относится (US1/US2/US3/US4).
- Полные пути к файлам включены в описания.

## Path Conventions

- **Web app**: `backend/` → `karaoke-app/`, `karaoke-web/`; `frontend/` → `webvue3/`, `karaoke-public/`.
- Документация — на верхнем уровне: `docs/`, `CONTRIBUTING.md`.
- Тулинг — `config/`, `tools/`, `.pre-commit-config.yaml`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: инициализация проекта, добавление инструментов в Gradle/npm.

- [ ] T001 Создать структуру директорий `config/ktlint/`, `config/detekt/`, `tools/`, `docs/features/` в корне репозитория
- [ ] T002 Добавить Gradle-плагины `com.github.pinterest:ktlint-gradle` и `io.gitlab.arturbosch.detekt:detekt-gradle-plugin` в корневой `build.gradle.kts` (актуальные версии — в `research.md`, Decision 1)
- [ ] T003 [P] Добавить зависимости `eslint@8.x`, `@vue/eslint-config-typescript`, `@vue/eslint-config-prettier`, `prettier@3.x` в `webvue3/package.json` + npm-script `lint` (`eslint --max-warnings 0 .`)
- [ ] T004 [P] Добавить те же зависимости в `karaoke-public/package.json` + npm-script `lint`
- [ ] T005 Создать `.pre-commit-config.yaml` в корне с хуками: ktlint, detekt, eslint для webvue3 и karaoke-public, `tools/verify-doc-links.sh`, `tools/check-feature-doc.sh`

**Checkpoint**: инструменты настроены, но baseline ещё не сгенерирован. CI/pre-commit могут валиться — переходим к Phase 2.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: базовая инфраструктура (baseline-файлы, скрипты), блокирующая запуск линтеров без false-positive.

**⚠️ CRITICAL**: никакая задача из US1-US4 не может стартовать, пока baseline не зафиксирован.

- [ ] T006 Сгенерировать `config/ktlint/baseline.xml` через `./gradlew ktlintGenerateBaseline` (karaoke-app + karaoke-web)
- [ ] T007 Сгенерировать `config/detekt/baseline.xml` через `./gradlew detektBaseline`
- [ ] T008 [P] Сгенерировать `webvue3/.eslint-baseline.json` через `tools/generate-eslint-baseline.sh` (см. T010)
- [ ] T009 [P] Сгенерировать `karaoke-public/.eslint-baseline.json`
- [ ] T010 Создать `tools/generate-eslint-baseline.sh` (обёртка над `eslint --format json` + `jq` → JSON-массив, см. `contracts/baseline-format.md`)
- [ ] T011 Создать `tools/baseline-stats.sh` (считает count нарушений в каждом baseline, печатает таблицу; diff с прошлым релизом через git tag)
- [ ] T012 Установить `lychee` (бинарь в `/usr/local/bin/`) и создать `tools/verify-doc-links.sh` (обёртка: `lychee --offline <paths>`)
- [ ] T013 Создать `tools/check-feature-doc.sh <path>` (проверяет 6 секций + slug + status, см. `contracts/per-feature-doc.md`)
- [ ] T014 Создать `tools/check-enforcement.sh` (парсит `CONTRIBUTING.md`, проверяет, что MUST-правила с `enforcedBy ≠ code-review-only` покрыты baseline)
- [ ] T015 [P] Создать `tools/verify-kotlin-refs.sh` (P3-зависимость; парсит `Class#method` из per-feature документов, проверяет наличие в `ktlint`-индексе или grep по исходникам)
- [ ] T016 Подключить baseline-файлы в Gradle: `ktlint { baseline = file("config/ktlint/baseline.xml") }`, `detekt { baseline = file("config/detekt/baseline.xml") }`
- [ ] T017 [P] Подключить baseline в ESLint: `webvue3/.eslintrc.cjs` + скрипт `tools/check-eslint-baseline.sh`
- [ ] T018 [P] Подключить baseline в ESLint: `karaoke-public/.eslintrc.cjs` + аналогичный скрипт

**Checkpoint**: запуск `./gradlew ktlintCheck detekt` и `npm run lint` в обоих SPA завершается с кодом 0 (на baseline). Готов к работе над US1.

---

## Phase 3: User Story 1 — Разработчик понимает стандарты кода (Priority: P1) 🎯 MVP

**Goal**: явный документ с правилами, примерами, ссылками на инварианты.
**Independent Test**: открыть `CONTRIBUTING.md`, найти правила для Kotlin и Vue с примерами; запустить линтер на «нарушающем» примере → падает.

### Реализация

- [ ] T019 [P] [US1] Создать `CONTRIBUTING.md` с шапкой и TL;DR (см. `contracts/code-style-doc.md`)
- [ ] T020 [US1] Написать раздел «Kotlin / Spring Boot» в `CONTRIBUTING.md`: naming, package structure, imports, nullable-поля в `KaraokeDbTable`, Jackson-инвариант `is*`-полей, `redirectErrorStream(true)` для `ProcessBuilder`, обработка ошибок, KDoc (Severity MUST/SHOULD)
- [ ] T021 [P] [US1] Написать раздел «Vue 3 / TypeScript» в `CONTRIBUTING.md`: `<select class="form-select">`, table-layout с явной `width: Npx`, `display: flex` на `<td>` запрещён, white-space ellipsis, filter-modal CSS-блок, vuex-паттерны, JSDoc
- [ ] T022 [P] [US1] Написать раздел «SQL» в `CONTRIBUTING.md`: nullable-колонки, `recordhash`-триггеры, sync-флаги в `SyncRegistry`, миграции под karaoke-db
- [ ] T023 [P] [US1] Написать раздел «Markdown / Documentation» в `CONTRIBUTING.md`: per-feature шаблон (6 секций), `@see`-ссылки, битые ссылки запрещены
- [ ] T024 [P] [US1] Написать раздел «Shell / Bash» в `CONTRIBUTING.md`: `set -e`, `flock` для gradle-build, цитирование переменных
- [ ] T025 [US1] Написать раздел «Pre-commit и CI» в `CONTRIBUTING.md`: какие хуки, как добавить новый, как обойти (`--no-verify`), baseline-чек
- [ ] T026 [US1] Написать раздел «Обновление этого документа» (governance, semver, ссылка на constitution.md)
- [ ] T027 [US1] Прогнать `tools/check-enforcement.sh` → исправить найденные нарушения
- [ ] T028 [US1] Коммит: `CONTRIBUTING.md` + правки в `build.gradle.kts`/`package.json`/`.pre-commit-config.yaml`/baseline-файлов

**Checkpoint**: `CONTRIBUTING.md` опубликован, линтеры валидируют правила автоматически. Готов к US2.

---

## Phase 4: User Story 2 — Существующий код проходит автоматическую проверку (Priority: P1) 🎯 MVP

**Goal**: pre-commit-хук и (опц.) CI запускают линтеры; baseline сокращается.
**Independent Test**: создать файл с нарушением, попытаться сделать `git commit` — pre-commit блокирует.

### Реализация

- [ ] T029 [P] [US2] Добавить `pre-commit install` в `README.md` (раздел «Quick Start»)
- [ ] T030 [P] [US2] Добавить `pre-commit install` в setup-скрипт (если есть; опц.)
- [ ] T031 [US2] Настроить `tools/baseline-stats.sh` как pre-commit хук (только информативно, без блокировки — для отслеживания прогресса)
- [ ] T032 [US2] Запустить полный quickstart сценарий шаг 6 (создание файла с нарушением, попытка коммита) — убедиться, что pre-commit блокирует
- [ ] T033 [US2] Задокументировать процесс «как добавить новое правило в baseline» (если не сделано в CONTRIBUTING.md)
- [ ] T034 [US2] Написать первый «baseline reduction» PR: исправить 10% нарушений из ktlint baseline (выбрать самые простые категории, например `standard:no-wildcard-imports`)
- [ ] T035 [US2] После мержа baseline-reduction PR: запустить `tools/baseline-stats.sh`, убедиться, что count уменьшился на ≥10%

**Checkpoint**: pre-commit работает, baseline-метрики видны. MVP готов. Можно переходить к P2 (документация).

---

## Phase 5: User Story 3 — Каждая ключевая фича имеет per-feature документ (Priority: P2)

**Goal**: 9 файлов в `docs/features/` с 6 обязательными разделами + валидные ссылки.
**Independent Test**: `ls docs/features/` показывает 9 файлов; `lychee --offline` → 0 errors; `tools/check-feature-doc.sh docs/features/*.md` → 0 errors.

### Реализация

- [ ] T036 [P] [US3] Создать `docs/features/README.md` — оглавление с кратким описанием каждой из 9 фич и ссылками
- [ ] T037 [P] [US3] Создать `docs/features/mlt-generator.md` (выделить из `DEVELOPMENT.md` раздел «MLT video generation»; ссылки на `mlt/mko/*`, `KaraokeProperties.kt`, `MltGenerator`)
- [ ] T038 [P] [US3] Создать `docs/features/async-process-queue.md` (выделить раздел «Async job pipeline»; ссылки на `KaraokeProcess*`, `KaraokeProcessWorker`, `KaraokeProcessThread`, thread-лейны)
- [ ] T039 [P] [US3] Создать `docs/features/dual-db-sync.md` (выделить раздел «Универсальный движок sync LOCAL↔SERVER»; ссылки на `SyncTarget.kt`, `SyncRegistry.all`, 8 флагов)
- [ ] T040 [P] [US3] Создать `docs/features/mp4-render.md` (выделить раздел «Рендер MP4 из онлайн-плера»; ссылки на `PlayerMp4RenderService`, `PlayerMp4MuxService`, `Utils.executeRenderMp4`)
- [ ] T041 [P] [US3] Создать `docs/features/sse-notifications.md` (выделить раздел «SSE-уведомления»; ссылки на `SseNotificationService`, `TabIdFilter`, heartbeat)
- [ ] T042 [P] [US3] Создать `docs/features/premium-stems.md` (выделить раздел «Премиум-фича Создать минусовку»; ссылки на `StemJob`, `tbl_stem_jobs`, `StemJobsAdminController`)
- [ ] T043 [P] [US3] Создать `docs/features/llm-lyrics-search.md` (выделить раздел «LLM-assisted lyrics/chords search»; ссылки на `LyricsFinderService`, `Agents.kt`, `Tools.kt`, `UtilsPlaywright.kt`)
- [ ] T044 [P] [US3] Создать `docs/features/telegram-auto-publish.md` (выделить раздел «Автоматизация публикации в Telegram»; ссылки на `TelegramApiClient`, `TelegramUpdatesConsumer`, `Settings.parseTelegramPostSongId`)
- [ ] T045 [P] [US3] Создать `docs/features/monitoring.md` (выделить раздел «Мониторинг ключевых моментов»; ссылки на `MonitorRegistry`, `MonitoringService`, `MonitorCheck`)
- [ ] T046 [US3] Запустить `lychee --offline docs/features/`, исправить битые ссылки (на несуществующие файлы, неправильные anchor'ы)
- [ ] T047 [P] [US3] Добавить `tools/check-feature-doc.sh docs/features/*.md` как pre-commit хук
- [ ] T048 [US3] Добавить правило FR-009 в `CONTRIBUTING.md`: «PR с новой/изменённой фичей из FR-004 обязан обновлять соответствующий документ»
- [ ] T049 [US3] Запустить `tools/check-feature-doc.sh` для всех 9 файлов → 0 errors

**Checkpoint**: 9 per-feature документов созданы, проходят валидацию. Готов к P3.

---

## Phase 6: User Story 4 — KDoc/JSDoc на публичных API (Priority: P3)

**Goal**: ≥90% публичных API документированы, Dokka/typedoc без warning'ов.
**Independent Test**: `./gradlew :karaoke-app:dokkaHtml` → SUCCESS, 0 warnings; открыть HTML, найти любой публичный класс — у него есть KDoc + `@see docs/features/<slug>.md`.

### Реализация

- [ ] T050 [P] [US4] Добавить Gradle-плагин `org.jetbrains.dokka:dokka-gradle-plugin` в `karaoke-app/build.gradle.kts` и `karaoke-web/build.gradle.kts`
- [ ] T051 [US4] Сгенерировать первый Dokka-отчёт, проанализировать список «missing description» warning'ов, выбрать 10 ключевых классов для первоочередной документации
- [ ] T052 [US4] Написать KDoc для 10 ключевых классов `karaoke-app` (минимум): `KaraokeProcessWorker`, `KaraokeProcessThread`, `Settings`, `KaraokeDbTable`, `Connection`, `LyricsFinderService`, `SseNotificationService`, `SyncTarget`, `MltGenerator`, `KaraokeStorageService` — каждый с `@see docs/features/<slug>.md`
- [ ] T053 [US4] Написать KDoc для основных сервисов `karaoke-web`: `MainController`, `PublicApiController`, `PublicPlayerController`
- [ ] T054 [P] [US4] Подключить `typedoc` к `webvue3/package.json` (или использовать встроенный `vue-tsc --noEmit` для типов, если typedoc не подходит)
- [ ] T055 [P] [US4] Написать JSDoc для 10 ключевых Vue-компонентов `webvue3`: `SongsTable`, `AuthorsTable`, `PicturesTable`, `ProcessesTable`, `PropertiesTable`, `SiteUsersTable`, `SitePlaylistsTable`, `DictionariesTable`, `SyncTable`, `StatsView`
- [ ] T056 [P] [US4] Написать JSDoc для 5 ключевых компонентов `karaoke-public` (по образцу)
- [ ] T057 [US4] Включить Dokka-генерацию в pre-commit/CI: `./gradlew :karaoke-app:dokkaHtml :karaoke-web:dokkaHtml` → fail при warning'ах
- [ ] T058 [US4] Повторно сгенерировать Dokka-отчёт, убедиться, что warning'ов меньше исходного на ≥50%

**Checkpoint**: ≥90% публичных API документированы, Dokka-генерация не падает.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: финальная интеграция, обновление смежных документов, PR-шаблоны.

- [ ] T059 [P] Обновить `AGENTS.md` — добавить ссылку на `CONTRIBUTING.md` в раздел «Важные паттерны», пометить, что «правила оформления» перенесены в CONTRIBUTING.md
- [ ] T060 [P] Обновить `DEVELOPMENT.md` — добавить раздел «Code Standards» со ссылкой на CONTRIBUTING.md и `docs/features/README.md`
- [ ] T061 [P] Обновить `README.md` — добавить секцию «Стандарты оформления кода» со ссылкой на CONTRIBUTING.md
- [ ] T062 Обновить `.specify/memory/constitution.md` если добавились новые MUST-правила (semver MINOR)
- [ ] T063 [P] Создать `.github/PULL_REQUEST_TEMPLATE.md` (или эквивалент) с чек-листом: «новый код → обновил доку? FR-009»
- [ ] T064 Запустить полный `quickstart.md` сценарий (10 шагов), отметить все 11 пунктов ✅
- [ ] T065 Создать один сводный PR с фичей, попросить review у пользователя

**Checkpoint**: всё готово к merge.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: нет зависимостей, можно стартовать сразу.
- **Phase 2 (Foundational)**: зависит от Phase 1; **БЛОКИРУЕТ** все US.
- **Phase 3 (US1, P1)**: зависит от Phase 2.
- **Phase 4 (US2, P1)**: зависит от Phase 3 (CONTRIBUTING.md нужен для документирования baseline-процесса).
- **Phase 5 (US3, P2)**: зависит от Phase 2 (скрипты валидации); US1 желателен, но не блокирует.
- **Phase 6 (US4, P3)**: зависит от Phase 2 + Phase 5 (per-feature документы нужны как `@see`-цели).
- **Phase 7 (Polish)**: зависит от всех US.

### User Story Dependencies

- **US1 (P1)**: после Phase 2.
- **US2 (P1)**: после US1.
- **US3 (P2)**: после Phase 2; желательно после US1.
- **US4 (P3)**: после Phase 2 + US3 (для `@see`-ссылок на per-feature документы).

### Within Each Story

- В Phase 5 (US3) per-feature документы можно писать параллельно (T037-T045, все [P]).
- В Phase 2 baseline-файлы (T006, T007) можно генерировать параллельно.
- В Phase 6 Dokka + typedoc настройка (T050, T054) — параллельно.

### Parallel Opportunities

- Phase 1: T003, T004 — параллельно (разные `package.json`).
- Phase 2: T008, T009 — параллельно (разные baseline-файлы); T015, T010, T011 — параллельно.
- Phase 5: T037-T045 — все 9 per-feature документов параллельно.
- Phase 6: T050, T054 — параллельно (Gradle + npm).

---

## Implementation Strategy

### MVP First (P1 stories only)

1. Phase 1 + Phase 2 (Setup + Foundational).
2. Phase 3 (US1: CONTRIBUTING.md).
3. Phase 4 (US2: pre-commit + baseline).
4. **STOP и VALIDATE**: запустить quickstart шаги 1-3 + 6, отметить прогресс.
5. Merge P1 stories как MVP. Baseline начал сокращаться.

### Incremental Delivery

1. MVP (P1) — линтеры + baseline + CONTRIBUTING.md.
2. + Phase 5 (US3) — per-feature документы, инкрементально по 1-2 за итерацию.
3. + Phase 6 (US4) — KDoc/JSDoc, инкрементально.
4. + Phase 7 (Polish) — финальные обновления `AGENTS.md`/`DEVELOPMENT.md`/README/PR-шаблоны.

### Parallel Team Strategy (если несколько разработчиков)

- Разработчик A: Phase 1-2 + Phase 3-4 (P1 MVP).
- Разработчик B: Phase 5 (per-feature документы — можно делить 9 фич на 2-3 части).
- Разработчик C: Phase 6 (KDoc/JSDoc).

После Phase 2 (Foundational) — все три параллельно.

---

## Notes

- [P] задачи = разные файлы, нет зависимостей.
- [Story] метка привязывает задачу к User Story для трассируемости.
- Каждая User Story завершаема независимо после Phase 2.
- **Verify tests fail before implementing** — для линтеров: создать пример с нарушением, убедиться, что линтер его ловит (см. quickstart шаг 6).
- Commit после каждой задачи или логической группы (например, после T028 — коммит «US1: CONTRIBUTING.md + базовые правила»).
- Stop at any checkpoint to validate story independently.
- Avoid: расплывчатые задачи, конфликты в одном файле, cross-story зависимости, ломающие независимость.

---

## Связь с Constitution.md

- **Principle I (Self-contained)**: все инструменты локальные (T002-T005).
- **Principle II (Сырой JDBC)**: не затрагивается (нет работы со слоем данных).
- **Principle III (SyncRegistry)**: не затрагивается.
- **Principle IV (Async-очередь)**: pre-commit — синхронная обёртка, не подменяет `KaraokeProcess*`.
- **Principle V (Двух-фронтенд)**: T003, T004 — отдельные ESLint-конфиги для admin и public.

**Constitution Check**: 5/5 PASS для всей фичи.
