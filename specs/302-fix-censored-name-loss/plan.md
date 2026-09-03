# Implementation Plan: Не сохраняется цензурированное имя песни в SongEdit

**Branch**: `302-fix-censored-name-loss` | **Date**: 2026-09-03 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/302-fix-censored-name-loss/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command; its definition describes the execution workflow.

## Summary

Bugfix #52 из OpenProject: ручная правка поля «Censored» в `SongEdit.vue`
теряется на бэкенде, потому что в `ApiController.songs2Update` нет
соответствующего `@RequestParam songNameCensored` — Spring Web молча
отбрасывает неизвестные query-параметры.

**Технический подход** (из [research.md](research.md) Decision 1):

1. **FR-011 (рефактор endpoint)** — `songs2Update` принимает все
   параметры через `@RequestParam Map<String, String> all`, а
   централизованный `SongUpdateMapper.apply` распределяет их по
   `fields[SongField.X]` или специальным обработчикам (`fileName`,
   `albumId`, `songType`). Это устраняет корневую причину класса багов
   «UI шлёт X, бэкенд не принимает» — параметры не могут «потеряться»,
   потому что они автоматически попадают в Map.

2. **FR-005/006/007/008 (чеки покрытия)** — статические bash-скрипты,
   которые автоматически ловят будущие рассинхроны UI↔backend:
   - `tools/check-songedit-field-coverage.sh` для пары SongEdit ↔ /song/update.
   - `tools/check-endpoint-field-coverage.sh` для всех пар из
     `tools/endpoint-pairs.yml`.
   Интегрируются в pre-commit + CI для belt-and-suspenders.

3. **FR-009/010 (документация)** — `docs/features/song-edit-and-censored.md`
   + обновление `specs/277-song-name-censored/spec.md` US-2.

4. **NFR-004/005/006 (OpenProject DoD)** — claim-issue 52 на старте,
   add-comment + mark-review при завершении, cleanup тестовых данных.

## Technical Context

**Language/Version**:
- Backend: Kotlin 2.x, JDK 17, Spring Boot 3.x (см. `constitution.md` § «Технологический стек»).
- Frontend: Vue 3 + Vite, Node 22 LTS (см. `AGENTS.md` Pass 282).

**Primary Dependencies**:
- Backend: Spring Boot Web (MVC), JDBC через `KaraokeConnection`/`Connection.local()/remote()/virtual()` (raw, без JPA/Hibernate — Constitution § II).
- Frontend: Vue 3, Vuex, Bootstrap-vue-next (`webvue3`).
- Tools: bash ≥4.0, awk/grep/sed, PostgreSQL client (`psql`), OpenProject REST API v3 через `tools/tracker.sh`.

**Storage**:
- PostgreSQL 13+ (LOCAL + SERVER).
- Существующая колонка `tbl_songs.song_name_censored TEXT NOT NULL DEFAULT ''` — schema **БЕЗ изменений**.
- `recordhash`-триггер уже покрывает `song_name_censored` (см. specs/277).

**Testing**:
- Backend: ручная верификация (см. [quickstart.md](quickstart.md)) + golden-requests для рефактора (SC-009).
- Frontend: ручная верификация 10 правок через UI (SC-001).
- Pre-commit: 11 hooks (см. AGENTS.md Pass 282 + FR-006 — добавляем 2).
- CI: `.github/workflows/lint.yml` — добавляем 1 job (FR-006).

**Target Platform**:
- Admin-машина (Linux x86_64, Docker 20.10+) — локальная разработка + деплой.
- Прод-сервер (`<PROD_SERVER_IP>`) — деплой через `deploy/deploy_*.sh` (только с согласия пользователя, см. constitution § «Ограничения»).

**Project Type**: Web-приложение (Option 2 в plan-template.md) — backend Kotlin/Spring Boot + 2 frontend'а (`webvue3` admin + `karaoke-public`).

**Performance Goals**:
- Latency `/api/song/update` ≤2 сек (autosave debounce 1 сек + Spring POST roundtrip) — SC-002.
- Чек-скрипты: ≤1 сек для SongEdit-чека (SC-004), ≤5 сек для общего аудита (SC-006).
- Маппер: O(N) по числу параметров, N≈95 — пренебрежимо (NFR-001).

**Constraints**:
- Constitution § II (NON-NEGOTIABLE): сырой JDBC, без JPA/Hibernate. Не нарушается (маппер работает поверх уже загруженной `Song`).
- Constitution § VI FR-006 (NON-NEGOTIABLE): KDoc 100% на публичные API. `SongUpdateMapper` (public object) ДОЛЖЕН иметь полный KDoc + `@see` ссылку на `docs/features/song-edit-and-censored.md`.
- AGENTS.md Pass 282: на `nsa-i9` разрешено пересобирать `karaoke-app` без согласия. На других машинах — только с согласия.
- Constitution § VIII: 0 секретов в git. Не нарушается (новые файлы — не секрет-файлы).

**Scale/Scope**:
- Backend: 1 файл изменён (`ApiController.kt`), 1 файл новый (`SongUpdateMapper.kt`, ~200 строк).
- Frontend: 0 изменений (багфикс на backend, фронт уже правильно шлёт поле).
- Tools: 6 новых файлов (3 sh, 3 yml/sql).
- Docs: 1 новый per-feature документ + обновление 1 спеки.
- CI: +1 job.
- Pre-commit: +2 hooks.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Gate | Result | Notes |
|---|---|---|
| § I Self-contained автопайплайн | ✅ PASS | Рефактор `songs2Update` не вводит внешних зависимостей; новые tools/bash скрипты не требуют сети в hot path |
| § II Сырой JDBC + дифф по хэшам | ✅ PASS | `SongUpdateMapper.apply` принимает уже загруженную `Song` через `WORKING_DATABASE`, не вводит JPA/Hibernate; `recordhash`-триггер для `song_name_censored` уже есть |
| § III Двух-БД синхронизация через SyncRegistry | ✅ PASS | Поле `song_name_censored` уже зарегистрировано (см. specs/277), `recordhash`-триггер работает |
| § IV Async-очередь задач с парсингом stdout | ✅ PASS | Не затрагивается (bugfix в синхронном эндпоинте) |
| § V Двух-фронтенд: админка и публичный сайт | ✅ PASS | Затрагивается только `webvue3` (SongEdit), `karaoke-public` не меняется; v-model паттерн сохранён |
| § VI Code Standards (KDoc, линтеры, per-feature doc) | ✅ PASS | `SongUpdateMapper` будет иметь полный KDoc + `@see` на per-feature документ; pre-commit 8/8 (после +2 hooks); CI 7/7+ (после +1 job); FR-009 создаёт per-feature документ |
| § VII Cross-Machine Setup | ✅ PASS | Не затрагивается |
| § VIII Секреты и git-гигиена | ✅ PASS | Новые файлы — bash-скрипты, yml-конфиги, kotlin-файлы. Никаких `.env`, `*.key`, `*.pem`. Pre-commit проверка (git ls-files grep) не должна ничего найти |

**Все Gates PASS**. Complexity Tracking НЕ требуется (нет нарушений).

## Project Structure

### Documentation (this feature)

```text
specs/302-fix-censored-name-loss/
├── plan.md              # Этот файл (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
│   ├── api-song-update.md
│   ├── endpoint-pairs-yml.md
│   └── checklist-whitelist-yml.md
└── checklists/
    └── requirements.md  # Spec Quality Checklist
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

**Структура не меняется** (Web application, Option 2 в plan-template.md):

```text
karaoke-app/                         # backend (Kotlin/Spring Boot)
├── src/main/kotlin/com/svoemesto/karaokeapp/
│   ├── controllers/
│   │   ├── ApiController.kt         # MODIFIED: рефактор songs2Update (FR-011)
│   │   └── SongUpdateMapper.kt      # NEW: централизованный маппер (FR-011)
│   └── model/
│       └── Song.kt                  # unchanged (recordhash-триггер уже есть)
└── build.gradle.kts                 # unchanged

karaoke-web/                         # backend proxy (Spring Boot)
└── unchanged (проксирует /api/song/* в karaoke-app)

webvue3/                             # admin SPA (Vue 3)
└── src/components/Songs/edit/SongEdit.vue  # unchanged (фронт уже правильно шлёт поле)

karaoke-public/                      # public SPA (Vue 3)
└── unchanged (не затрагивается)

tools/                               # утилиты
├── check-songedit-field-coverage.sh        # NEW (FR-005)
├── check-songedit-field-coverage.whitelist.yml  # NEW (FR-005, Q4→B)
├── check-endpoint-field-coverage.sh        # NEW (FR-007)
├── check-endpoint-field-coverage.whitelist.yml  # NEW (FR-008)
├── endpoint-pairs.yml                      # NEW (FR-007)
├── cleanup-test-songs.sql                  # NEW (NFR-006)
└── (другие существующие скрипты — unchanged)

docs/
└── features/
    └── song-edit-and-censored.md    # NEW (FR-009)

specs/277-song-name-censored/
└── spec.md                          # MODIFIED (FR-010)

.pre-commit-config.yaml              # MODIFIED (+2 hooks)
.github/workflows/lint.yml           # MODIFIED (+1 job)
```

**Structure Decision**: Существующая структура Karaoke (Web
application, backend + 2 frontend'а) сохраняется. Добавления —
1 Kotlin-файл + 6 tools-файлов + 1 docs-файл + 2 спека-апдейта +
2 CI-апдейта. Никаких новых модулей или переименований.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| (нет) | — | — |

Constitution Check PASS для всех 8 принципов. Complexity Tracking
не заполняется.
