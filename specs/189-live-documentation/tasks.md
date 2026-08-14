# Tasks: Live Documentation (LiveDocs)

**Input**: Design documents from `/specs/189-live-documentation/`
**Prerequisites**: plan.md ✓, spec.md ✓, research.md ✓, data-model.md ✓, contracts/ ✓, quickstart.md ✓
**Branch**: `189-live-documentation` | **Date**: 2026-08-14

**Tests**: явный запрос на tests отсутствует в спеке. Валидация — через
`tools/check-livedocs-structure.sh` (CI) + ручная проверка по `quickstart.md`
(8 сценариев). Тестовых unit/integration-задач не генерируем.

**Organization**: задачи сгруппированы по user stories (US1-US7 из `spec.md`),
в порядке приоритета (P1 → P2 → P3). Каждая фаза — independently testable
increment. Финальная фаза — Polish & Cross-Cutting Concerns.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: можно делать параллельно (разные файлы, нет зависимостей).
- **[Story]**: к какому US относится задача.
- Описание включает точный путь к файлу.

## Path Conventions

Эта фича — документационная (не кодовая). Структура:

```
Karaoke/
├── livedocs/                # NEW: каталог LiveDocs
│   ├── README.md             # Манифест
│   ├── INDEX.md             # Карта слоёв
│   ├── features/            # SDD слой
│   ├── domain/              # DDD слой
│   ├── architecture/        # C4 слой
│   └── templates/           # Шаблоны
├── tools/
│   └── check-livedocs-structure.sh   # NEW: CI валидация
├── docs/
│   └── livedocs-conventions.md      # NEW: мета-документ
├── .github/workflows/
│   └── lint.yml                     # UPDATE: + check-livedocs
└── AGENTS.md                        # UPDATE: сократить ≤ 100 строк
```

---

## Phase 1: Setup (Shared Infrastructure)

**Цель**: инициализация базовой инфраструктуры для LiveDocs (директории, скрипт
валидации, мета-документ).

- [x] T001 Создать директорию `livedocs/` с поддиректориями `features/`, `domain/`, `architecture/`, `templates/` (`mkdir -p livedocs/{features,domain,architecture,templates}`)
- [x] T002 [P] Создать пустой `livedocs/README.md` (будет заполнен в Phase 2, T004)
- [x] T003 [P] Создать `docs/livedocs-conventions.md` со ссылкой на `specs/189-live-documentation/spec.md` и кратким описанием назначения LiveDocs (FR-012)
- [x] T004 [P] Создать пустой `tools/check-livedocs-structure.sh` с shebang `#!/usr/bin/env bash` и `chmod +x` (заполнение логикой — в Phase 2)

**Checkpoint**: структура `livedocs/` существует, скрипт валидации — пустой файл (заполнится в Phase 2).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Цель**: создать манифесты, шаблоны, README-индексы для каждого слоя. **Блокирует
все user stories** — без них LiveDocs не могут существовать как система.

- [x] T005 Заполнить `livedocs/README.md` — корневой манифест (назначение, навигация по слоям, ссылка на INDEX). Содержимое по контракту `data-model.md` § 4
- [x] T006 Заполнить `livedocs/INDEX.md` — карта всех 3 слоёв + decision tree («задача про фичу → features/, про модуль → domain/, про систему → architecture/»). Содержимое по `data-model.md` § 4
- [x] T007 [P] Создать `livedocs/templates/feature-summary.md` — шаблон для SDD-сводки фичи (по контракту `contracts/feature-summary-template.md`)
- [x] T008 [P] Создать `livedocs/templates/bounded-context.md` — шаблон для DDD bounded context (по контракту `contracts/bounded-context-template.md`)
- [x] T009 [P] Создать `livedocs/templates/c4-level-L1.md` — шаблон для C4 уровня 1 (по контракту `contracts/c4-level-template.md` § L1)
- [x] T010 [P] Создать `livedocs/templates/c4-level-L2.md` — шаблон для C4 уровня 2 (по контракту `contracts/c4-level-template.md` § L2)
- [x] T011 [P] Создать `livedocs/templates/c4-level-L3.md` — шаблон для C4 уровня 3 (по контракту `contracts/c4-level-template.md` § L3)
- [x] T012 Создать `livedocs/templates/README.md` — индекс шаблонов (5 шаблонов с однострочным описанием каждого)
- [x] T013 [P] Создать `livedocs/features/README.md` — индекс фич (пустой, будет заполняться при миграции; см. Phase 4)
- [x] T014 [P] Создать `livedocs/domain/README.md` — индекс bounded contexts (пустой, будет заполняться; см. Phase 5)
- [x] T015 [P] Создать `livedocs/architecture/README.md` — индекс архитектурных документов (пустой, будет заполняться; см. Phase 6)
- [x] T016 Заполнить `tools/check-livedocs-structure.sh` — 7 проверок из `quickstart.md` § «Run-all скрипт» (структура, ≥5 features, ≥5 domains, L1+L2+L3, frontmatter, AGENTS.md ≤100, CI integration)

**Checkpoint**: foundation ready — все манифесты и шаблоны существуют, скрипт валидации готов. **User story work может начинаться.**

---

## Phase 3: User Story 2 — Структура LiveDocs создана и индексирована (Priority: P1) 🎯 MVP

**Goal**: убедиться, что после Phase 1+2 валидация `check-livedocs-structure.sh` проходит (структура индексирована, все обязательные файлы существуют).

**Independent Test**: `bash tools/check-livedocs-structure.sh` возвращает exit code 0 (см. `quickstart.md` сценарий 1).

- [x] T017 [US2] Запустить `bash tools/check-livedocs-structure.sh` и убедиться в `[1/7]` (структура): 0 failures
- [x] T018 [US2] Запустить `bash tools/check-livedocs-structure.sh` и убедиться в `[2/7]` (features/ ≥ 5) — ожидаемо FAIL (ещё не мигрировали); зафиксировать baseline

**Checkpoint**: foundation валидна (T017), features/ ожидаемо пустой (T018 зафиксировал baseline для Phase 4).

---

## Phase 4: User Story 4 — Миграция существующих спек в `livedocs/features/` (Priority: P2)

**Goal**: мигрировать 5 существующих спек в `livedocs/features/` как SDD-сводки (≤ 2 страницы каждая). Покрывает SC-006 (≥ 5 фич).

**Independent Test**: `ls livedocs/features/*.md | grep -v README.md | wc -l` ≥ 5 (см. `quickstart.md` сценарий 2).

- [x] T019 [P] [US4] Создать `livedocs/features/182-editor-self-assign-tasks.md` — SDD-сводка фичи 182 (по `contracts/feature-summary-template.md`). Источник: `specs/182-editor-self-assign-tasks/spec.md`. Содержит: что делает, US краткий список, FR указатель, AC, ссылки на bounded contexts (catalog + identity + editorial)
- [x] T020 [P] [US4] Создать `livedocs/features/184-approve-status-choice.md` — SDD-сводка фичи 184. Источник: `specs/184-approve-status-choice/spec.md`. Cross-cutting: catalog + processing
- [x] T021 [P] [US4] Создать `livedocs/features/185-song-dto-audit-sponsr-remove.md` — SDD-сводка фичи 185. Источник: `specs/185-song-dto-audit-sponsr-remove/spec.md`. Cross-cutting: catalog + identity
- [x] T022 [P] [US4] Создать `livedocs/features/186-zakroma-songs-fast-load.md` — SDD-сводка фичи 186. Источник: `specs/186-zakroma-songs-fast-load/spec.md`. Cross-cutting: catalog
- [x] T023 [P] [US4] Создать `livedocs/features/187-site-traffic-anomaly-investigation.md` — SDD-сводка фичи 187. Источник: `specs/187-site-traffic-anomaly-investigation/spec.md`. Cross-cutting: publishing + analytics
- [x] T024 [US4] Обновить `livedocs/features/README.md` — добавить таблицу из 5 файлов-фич со ссылками и однострочным описанием каждой

**Checkpoint**: в `livedocs/features/` есть ≥ 5 SDD-сводок, `check-livedocs-structure.sh` шаг `[2/7]` PASS.

---

## Phase 5: User Story 6 — DDD ubiquitous language: 5 bounded contexts (Priority: P3)

**Goal**: описать 5 bounded contexts проекта в `livedocs/domain/` с ubiquitous language glossary. Покрывает SC-007 (≥ 5 contexts).

**Independent Test**: `ls livedocs/domain/*.md | grep -v README.md | wc -l` ≥ 5, каждый содержит секцию `## Aggregate Roots` (см. `quickstart.md` сценарий 3).

- [x] T025 [P] [US6] Создать `livedocs/domain/catalog.md` — bounded context «Каталог»: Aggregate Roots (Song, Album, Author, Genre), Entities, Value Objects (SongType, Tags), Domain Events (SongAdded, AlbumPublished), Ubiquitous Language (песня ≠ трек ≠ karaoke-видео)
- [x] T026 [P] [US6] Создать `livedocs/domain/processing.md` — bounded context «Обработка»: Aggregate Roots (KaraokeVideo, MLTProject), Entities (RenderMp4Params), Value Objects (RenderVersion), Domain Events (VideoRendered, StemsSeparated), glossary (MLT, Demucs, Sheetsage, stem, mix)
- [x] T027 [P] [US6] Создать `livedocs/domain/publishing.md` — bounded context «Публикация»: Aggregate Roots (PublishWindow, Subscription), Entities (SiteUser), Value Objects (AccessMode), Domain Events (SongPublished, SubscriptionExpired), glossary (эфир, on-air, exclusive, publish-date)
- [x] T028 [P] [US6] Создать `livedocs/domain/identity.md` — bounded context «Идентификация»: Aggregate Roots (SiteUser), Entities (Session), Value Objects (Email, Role), Domain Events (UserRegistered, UserLoggedIn), glossary (JWT, cookie, principal)
- [x] T029 [P] [US6] Создать `livedocs/domain/editorial.md` — bounded context «Редакторы»: Aggregate Roots (EditorAssignment, ReviewTask), Entities (Draft, Review), Value Objects (ApprovalStatus), Domain Events (TaskAssigned, TaskApproved), glossary (self-assign, idempotent, race)
- [x] T030 [US6] Обновить `livedocs/domain/README.md` — добавить таблицу из 5 bounded contexts со ссылками и кратким описанием

**Checkpoint**: в `livedocs/domain/` есть 5 bounded contexts, `check-livedocs-structure.sh` шаг `[3/7]` PASS.

---

## Phase 6: User Story 7 — C4 архитектурные диаграммы всех 3 уровней (Priority: P3)

**Goal**: создать C4 диаграммы L1/L2/L3 + 2 topic-документа в `livedocs/architecture/`. Покрывает SC-008 (все 3 уровня C4 обязательны).

**Independent Test**: `livedocs/architecture/{L1-system-context,L2-containers,L3-components}.md` существуют, каждый содержит Mermaid-блок (см. `quickstart.md` сценарий 4).

- [x] T031 [US7] Создать `livedocs/architecture/L1-system-context.md` — C4 L1: Karaoke (svoemesto) ↔ внешние системы (Browser, Postgres, MinIO, Ollama, Sheetsage, SearXNG, YOOKASSA, VK). Mermaid flowchart, не менее 6 связей
- [x] T032 [US7] Создать `livedocs/architecture/L2-containers.md` — C4 L2: контейнеры (karaoke-app, karaoke-web, karaoke-public SPA, webvue3 SPA, Postgres, MinIO). Mermaid с подграфами, не менее 8 связей
- [x] T033 [US7] Создать `livedocs/architecture/L3-components.md` — C4 L3: компоненты внутри karaoke-app (Model layer, MLT layer, Queue layer, LLM layer, SSE hub, MLT generator). Mermaid, не менее 6 связей
- [x] T034 [P] [US7] Создать `livedocs/architecture/data-sync.md` — тематический документ про LOCAL ↔ SERVER синхронизацию (SyncRegistry, recordhash, AssociateBy). Mermaid sequence-диаграмма
- [x] T035 [P] [US7] Создать `livedocs/architecture/queue-lanes.md` — тематический документ про async-очередь (KaraokeProcess*, threadId lanes, HEAVY_RENDER / LIGHT_BACKGROUND / REMOTE_STORE_UPLOAD / STEM_JOBS). Mermaid
- [x] T036 [US7] Обновить `livedocs/architecture/README.md` — добавить таблицу из 5 файлов (3 C4 уровня + 2 topic)

**Checkpoint**: в `livedocs/architecture/` есть 3 C4 уровня + 2 topic, каждый содержит Mermaid. `check-livedocs-structure.sh` шаг `[4/7]` PASS.

---

## Phase 7: User Story 5 — AGENTS.md сокращается до ≤ 100 строк (Priority: P2)

**Goal**: мигрировать детали из `AGENTS.md` (~230 строк → ≤ 100) в новые LiveDocs-документы. Сохраняем historical context (Q&A, паттерны), а не удаляем. Покрывает SC-002 (≤ 100 строк).

**Independent Test**: `wc -l AGENTS.md` ≤ 100 (см. `quickstart.md` сценарий 6).

- [x] T037 [P] [US5] Создать `livedocs/architecture/jackson-conventions.md` — мигрировать Q&A «Jackson отбрасывает `is` в boolean-полях Kotlin DTO» из `AGENTS.md`. Содержит: описание проблемы, симптом, решение с `@JsonProperty`, исторический контекст (PR #48-#49)
- [x] T038 [P] [US5] Создать `livedocs/architecture/docker-conventions.md` — мигрировать Q&A про `nginx:alpine` (нет bash), `node:latest` (недетерминирован), JRE vs JDK, Docker CE в karaoke-app
- [x] T039 [P] [US5] Создать `livedocs/architecture/documentation-conventions.md` — мигрировать Q&A про KDoc с backticks, JSDoc coverage, prettier/ktlint blame-ignore-revs
- [x] T040 [P] [US5] Создать `livedocs/architecture/webvue3-patterns.md` — мигрировать паттерн «персистентность страницы пагинации в webvue3» (Vuex store + watcher)
- [x] T041 [US5] Сократить `AGENTS.md`: удалить 4 мигрированных блока Q&A, заменить на одну строку-ссылку `> Детали и паттерны — в [livedocs/architecture/](../livedocs/architecture/README.md).` Проверить `wc -l AGENTS.md` ≤ 100
- [x] T042 [US5] Обновить Q&A секцию `AGENTS.md` — убрать мигрированные Q&A (Jackson, Dockerfile, KDoc, webvue3-пагинация); оставить governance (CI-gate, иерархия, lifecycle)

**Checkpoint**: `AGENTS.md` ≤ 100 строк, 4 детали мигрированы в `livedocs/architecture/*.md`. `check-livedocs-structure.sh` шаг `[6/7]` PASS.

---

## Phase 8: User Story 1 — AI-агент при старте читает LiveDocs первым (Priority: P1) 🎯 MVP

**Goal**: правило «AI-агент при старте сессии читает `livedocs/README.md` и `livedocs/INDEX.md` первым» зафиксировано в `AGENTS.md`. Покрывает SC-001 (≤ 5K токенов на онбординг), SC-009 (агент НЕ лезет в устаревшие спеки).

**Independent Test**: открыть свежую сессию AI-агента, дать задачу «опиши модуль X», убедиться что первые 2-3 обращения — в `livedocs/*` (см. `quickstart.md` сценарий 8).

- [x] T043 [US1] Добавить в начало `AGENTS.md` блок «С чего начать сессию» с инструкцией: «1) прочитай `livedocs/README.md` + `livedocs/INDEX.md`; 2) переходи в `livedocs/domain/<context>.md` или `livedocs/features/<NNN>.md` по задаче; 3) только если в LiveDocs нет — лезь в `specs/NNN-*/spec.md` или `AGENTS.md` Q&A»
- [x] T044 [US1] Запустить `bash tools/check-livedocs-structure.sh` и убедиться, что ВСЕ 7 шагов PASS (после Phase 1-7 — структура полная, AGENTS.md ≤ 100 строк, frontmatter валиден)

**Checkpoint**: правило зафиксировано в `AGENTS.md`, все 7 шагов валидации PASS.

---

## Phase 9: User Story 3 — LiveDocs обновляются по pull request (Priority: P2)

**Goal**: правило «при изменении кода обновлять LiveDocs в том же PR» зафиксировано в `AGENTS.md`, CI-gate проверен. Покрывает FR-014, FR-015, US3.

**Independent Test**: открыть PR с изменением кода → убедиться что `check-livedocs-structure.sh` запускается в GitHub Actions (см. `quickstart.md` сценарий 7).

- [x] T045 [US3] Добавить в `.github/workflows/lint.yml` шаг `check-livedocs` после существующих 7 проверок: `bash tools/check-livedocs-structure.sh`. Проверить синтаксис YAML
- [x] T046 [US3] Добавить в `AGENTS.md` правило «Обновление LiveDocs»: при изменении bounded context в `livedocs/domain/` или C4 уровня в `livedocs/architecture/` — в том же PR обновить соответствующий LiveDoc. Ссылка на `quickstart.md` для примеров
- [x] T047 [US3] Проверить, что скрипт `tools/check-livedocs-structure.sh` запускается в CI: открыть PR (или push в feature-ветку), убедиться что job `check-livedocs` PASS

**Checkpoint**: правило обновления LiveDocs по PR задокументировано + CI-gate активен.

---

## Final Phase: Polish & Cross-Cutting Concerns

**Цель**: финальные проверки, обновление changelog, валидация quickstart-сценариев.

- [x] T048 [P] Добавить запись в `docs/architecture-notes.md` (Pass 62+): фича 189-live-documentation завершена, LiveDocs внедрены (структура + 5 фич + 5 contexts + 3 C4 уровня, AGENTS.md сокращён)
- [x] T049 [P] Запустить все 8 сценариев из `quickstart.md` вручную (или в сухосбережении): зафиксировать результаты в комментарии к PR
- [x] T050 Финальная проверка: `bash tools/check-livedocs-structure.sh` → exit 0; `wc -l AGENTS.md` ≤ 100; `ls livedocs/features/*.md | wc -l` ≥ 5; `ls livedocs/domain/*.md | wc -l` ≥ 5; `ls livedocs/architecture/L*.md` == 3 файла
- [x] T051 [P] Обновить `AGENTS.md` Q&A секцию: добавить Q&A «Где искать актуальные знания о проекте?» со ссылкой на `livedocs/README.md` (новый Q&A, связанный с этой фичей)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: нет зависимостей — старт immediately.
- **Phase 2 (Foundational)**: зависит от Phase 1 — **блокирует** все user stories.
- **Phase 3 (US2, P1)**: зависит от Phase 2 — валидирует foundation.
- **Phase 4 (US4, P2)**: зависит от Phase 2 — мигрирует фичи.
- **Phase 5 (US6, P3)**: зависит от Phase 2 — создаёт bounded contexts.
- **Phase 6 (US7, P3)**: зависит от Phase 2 — создаёт C4 диаграммы.
- **Phase 7 (US5, P2)**: зависит от Phase 2 — мигрирует Q&A из AGENTS.md.
- **Phase 8 (US1, P1)**: зависит от Phase 3+4+5+6+7 — финальное правило в AGENTS.md.
- **Phase 9 (US3, P2)**: зависит от Phase 2 — CI-gate.
- **Final (Polish)**: зависит от Phase 1-9 — финальные проверки.

### User Story Dependencies

- **US2 (P1)**: после Phase 2 — нет зависимостей от других stories.
- **US4 (P2)**: после Phase 2 — независим от US2/US6/US7 (мигрирует фичи параллельно).
- **US6 (P3)**: после Phase 2 — независим (создаёт новые bounded contexts).
- **US7 (P3)**: после Phase 2 — независим (создаёт C4 диаграммы).
- **US5 (P2)**: после Phase 2 — независим (мигрирует Q&A из AGENTS.md).
- **US1 (P1)**: после Phase 4-7 (правило ссылается на существующие LiveDocs).
- **US3 (P2)**: после Phase 2 (CI-gate) + после Phase 7 (правило в AGENTS.md).

**Сценарий параллельного выполнения** (если есть несколько разработчиков):
- Phase 1+2 — последовательно (один человек).
- После Phase 2 — можно параллелить:
  - Разработчик A: Phase 3 (валидация) → Phase 4 (миграция 5 фич).
  - Разработчик B: Phase 5 (5 bounded contexts).
  - Разработчик C: Phase 6 (3 C4 уровня + 2 topic).
  - Разработчик D: Phase 7 (4 миграции Q&A из AGENTS.md).
- Phase 8+9+Final — последовательно (один человек, финальная интеграция).

### Within Each Phase

- Шаблоны и README-индексы (T007-T015) — параллельно после T005+T006.
- Миграция фич (T019-T023) — параллельно (5 разных файлов).
- Создание bounded contexts (T025-T029) — параллельно (5 разных файлов).
- Создание C4 уровней (T031-T033) — параллельно НЕЛЬЗЯ (связаны, лучше последовательно, начиная с L1).
- Миграция Q&A из AGENTS.md (T037-T040) — параллельно (4 разных файла).

---

## Parallel Opportunities

### Setup Phase (Phase 1) — все [P]

```bash
# Запустить параллельно:
Task: "Создать livedocs/README.md placeholder (T002)"
Task: "Создать docs/livedocs-conventions.md (T003)"
Task: "Создать tools/check-livedocs-structure.sh placeholder (T004)"
```

### Foundational Phase (Phase 2) — шаблоны [P]

```bash
# После T005+T006 — запустить параллельно:
Task: "T007: livedocs/templates/feature-summary.md"
Task: "T008: livedocs/templates/bounded-context.md"
Task: "T009: livedocs/templates/c4-level-L1.md"
Task: "T010: livedocs/templates/c4-level-L2.md"
Task: "T011: livedocs/templates/c4-level-L3.md"

# Параллельно:
Task: "T013: livedocs/features/README.md"
Task: "T014: livedocs/domain/README.md"
Task: "T015: livedocs/architecture/README.md"
```

### User Story 4 (Phase 4) — все 5 миграций [P]

```bash
Task: "T019: livedocs/features/182-editor-self-assign-tasks.md"
Task: "T020: livedocs/features/184-approve-status-choice.md"
Task: "T021: livedocs/features/185-song-dto-audit-sponsr-remove.md"
Task: "T022: livedocs/features/186-zakroma-songs-fast-load.md"
Task: "T023: livedocs/features/187-site-traffic-anomaly-investigation.md"
```

### User Story 6 (Phase 5) — все 5 bounded contexts [P]

```bash
Task: "T025: livedocs/domain/catalog.md"
Task: "T026: livedocs/domain/processing.md"
Task: "T027: livedocs/domain/publishing.md"
Task: "T028: livedocs/domain/identity.md"
Task: "T029: livedocs/domain/editorial.md"
```

### User Story 5 (Phase 7) — все 4 миграции [P]

```bash
Task: "T037: livedocs/architecture/jackson-conventions.md"
Task: "T038: livedocs/architecture/docker-conventions.md"
Task: "T039: livedocs/architecture/documentation-conventions.md"
Task: "T040: livedocs/architecture/webvue3-patterns.md"
```

---

## Implementation Strategy

### MVP First (User Stories 1+2)

**MVP** — это **Phase 1+2+3** (Setup + Foundational + US2). Это даёт:
- Структуру `livedocs/` со всеми манифестами.
- Скрипт валидации (пусть с baseline-failures для features/domain/C4).
- 0 failures по `[1/7]` (структура).

**Почему не US1**: US1 требует, чтобы LiveDocs были **наполнены** (P1 про правило
«читать первым» имеет смысл только когда есть что читать). После Phase 3
LiveDocs пустые, правило бесполезно.

### Incremental Delivery

1. Phase 1+2 → foundation (структура, шаблоны).
2. Phase 3 → валидация foundation (MVP #1: «структура индексирована»).
3. Phase 4 → 5 SDD-сводок фич (MVP #2: «первые фичи мигрированы»).
4. Phase 5+6 → 5 bounded contexts + 3 C4 уровня (MVP #3: «архитектура описана»).
5. Phase 7 → AGENTS.md сокращён (MVP #4: «AGENTS.md эффективен»).
6. Phase 8 → правило в AGENTS.md (MVP #5: «AI-агент знает, куда идть»).
7. Phase 9 → CI-gate (operational readiness).
8. Final Phase → changelog + ручная валидация.

### Parallel Team Strategy

См. «Сценарий параллельного выполнения» в Dependencies. После Phase 2 можно
параллелить Phase 4/5/6/7 — это **4 независимых потока**.

---

## Format Validation Checklist

- [x] Каждая задача начинается с `- [ ]`.
- [x] У каждой задачи есть Task ID (T001-T051).
- [x] [P] только у параллельных задач (разные файлы, нет зависимостей).
- [x] [Story] у задач в фазах US1-US7 (нет [Story] в Setup/Foundational/Polish).
- [x] Описание включает точный путь к файлу.
- [x] Нет «мусорных» задач без пути или без действия.
- [x] Setup → Foundational → US stories → Polish порядок.
- [x] P1 stories (US1, US2) раньше P2 (US3, US4, US5), P3 (US6, US7) последними.

---

## Notes

- [P] задачи = разные файлы, нет зависимостей.
- [Story] метка обеспечивает traceability к spec.md.
- Каждая user story должна быть independently completable.
- Тесты не генерируются (явный запрос на tests отсутствует).
- Commit после каждой задачи или логической группы.
- Stop на checkpoint для валидации story independently.
- Avoid: vague tasks, same file conflicts, cross-story dependencies that break independence.