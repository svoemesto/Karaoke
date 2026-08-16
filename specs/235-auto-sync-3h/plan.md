# Implementation Plan: 235 — Автозапуск «Синхронизации в 1 клик» каждые 3 часа

**Branch**: `235-auto-sync-3h` | **Date**: 2026-08-16 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/235-auto-sync-3h/spec.md`

## Summary

Добавить периодический (каждые 3 ч) автозапуск существующей бизнес-логики «Синхронизация в 1 клик» (`POST /api/sync/oneclick`) в `karaoke-app`. Реализуется через новый `@Component AutoOneClickSyncScheduler` (Spring `@Scheduled` + `AtomicBoolean` lock + `ConcurrentLinkedDeque` history) + новый REST-эндпоинт `GET /api/sync/auto-status` + блок «Автозапуск» в webvue3 `SyncTable.vue`. 3 новых property в `KaraokeProperties`. Никаких миграций БД. Существующий ручной клик продолжает работать; при гонке (авто + ручной одновременно) ручной получает HTTP `409 Conflict`.

## Technical Context

**Language/Version**: Kotlin 1.x (модуль `karaoke-app`, JDK 17, Gradle multi-module)
**Primary Dependencies**: Spring Boot 3.5 (Spring Web, Spring Scheduling), Vue 3 + Vuex (модуль `webvue3`)
**Storage**: Не применимо (in-memory state, без БД)
**Testing**: Ручное тестирование через quickstart-сценарии; CI-тестов в проекте нет (см. constitution §«Тесты»)
**Target Platform**: Linux desktop (admin-машина), браузер (webvue3)
**Project Type**: Desktop web-service (Spring Boot monolith + SPA)
**Performance Goals**: 
  - Lock «не одновременно» — non-blocking (`AtomicBoolean.compareAndSet`), < 1 µs
  - History read — O(n) для n=10, < 100 µs
  - Scheduler-tick — без новой нагрузки (вызывает существующий `runEntitySync` ровно по той же логике, что и ручной клик; SC-008: ≤ +5% накладных)
**Constraints**:
  - 3-часовой интервал должен сохраняться даже если karaoke-app работает неделями (in-memory `@Volatile var lastRunMs` переживает тики, но не рестарт JVM — допустимо, см. A-001)
  - `intervalMs >= 60_000L` (минимум 1 минута, чтобы не DDoS-ить БД)
  - `try/catch(Throwable)` в scheduler-тике — обязательно, иначе scheduler-бин может остановиться (FR-016, SC-009)
**Scale/Scope**: 1 singleton bean + 1 controller + 3 DTO + 1 Vue-блок. Без новых сущностей в БД.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Pre-Phase-0 (до research)

| Principle | Статус | Комментарий |
|-----------|--------|-------------|
| I. Self-contained автопайплайн | ✅ Pass | Никаких внешних API в горячем пути; sync уже работает через локальный JDBC |
| II. Сырой JDBC + diff по хэшам | ✅ Pass | Автозапуск **переиспользует** существующую `runEntitySync` (Utils.kt:629), никаких новых JDBC-вызовов |
| III. Двух-БД sync через SyncRegistry | ✅ Pass | `SyncRegistry.all` — единственный источник истины для `autoOneClickSync`; ничего не дублируется |
| IV. Async-очередь с парсингом stdout | N/A | Не применимо — sync-движок синхронный, не запускает OS-процессов |
| V. Двух-фронтенд | ✅ Pass | Изменения только в admin SPA (`webvue3`), публичный фронт не трогаем |
| VI. Code Standards (FR-006 KDoc, FR-007 lint, FR-009 per-feature) | ⏳ Pending | Новый `AutoOneClickSyncScheduler` обязан иметь KDoc с `@see docs/features/235-auto-sync-3h.md`; новый LiveDoc обязателен до merge |
| VII. Cross-Machine Setup | ✅ Pass | Никаких локальных конфигов, `.gitattributes`/`blame-ignore-revs` не затрагиваются |
| VIII. Секреты и git-гигиена | ✅ Pass | Никаких секретов в коде, 3 новых property — несекретные default (`true` / `10800000` / `300000`) |

**Результат pre-Phase-0**: ✅ All clear, можно начинать research.

### Post-Phase-1 (после design)

| Principle | Статус | Комментарий |
|-----------|--------|-------------|
| I. Self-contained автопайплайн | ✅ Pass | Подтверждено: автозапуск только через локальный JDBC, никаких внешних вызовов |
| II. Сырой JDBC + diff по хэшам | ✅ Pass | `data-model.md` подтверждает: scheduler вызывает только `runEntitySync(key, direction)` — никаких прямых SQL |
| III. Двух-БД sync через SyncRegistry | ✅ Pass | `data-model.md §3`: scheduler-бин `reads: SyncRegistry.all`; ни одна таблица не hardcoded |
| IV. Async-очередь | N/A | — |
| V. Двух-фронтенд | ✅ Pass | `contracts/api-contracts.md §5`: все изменения в admin SPA; `karaoke-public` не затрагивается |
| VI. Code Standards | ⏳ Pending (action item для tasks.md) | Требуется: (1) LiveDoc `docs/features/235-auto-sync-3h.md`; (2) KDoc на `AutoOneClickSyncScheduler`, `AutoOneClickSyncRun`, `AutoOneClickSyncStatusController` + DTOs со ссылкой `@see`; (3) запись в `docs/architecture-notes.md` (Pass 63+). CI `tools/check-livedocs-structure.sh` проверит `≥5 фич` в `livedocs/features/`. |
| VII. Cross-Machine Setup | ✅ Pass | Без изменений |
| VIII. Секреты и git-гигиена | ✅ Pass | `data-model.md §«KaraokeProperties»`: 3 новых ключа — несекретные, default безопасны |

**Результат post-Phase-1**: ✅ All clear, фича готова к Phase 2 (tasks.md). Action item для Phase 2: **обязательное** обновление `livedocs/features/235-auto-sync-3h.md` (новый LiveDoc) и `livedocs/architecture-notes.md` в том же PR (см. AGENTS.md «Обновление LiveDocs (FR-014)»).

## Project Structure

### Documentation (this feature)

```text
specs/235-auto-sync-3h/
├── plan.md              # This file (/speckit.plan command output)
├── spec.md              # Feature spec (Phase 0 input)
├── research.md          # Phase 0 output — Spring @Scheduled patterns
├── data-model.md        # Phase 1 output — entities, DTOs, KaraokeProperties
├── contracts/
│   └── api-contracts.md # Phase 1 output — REST API contracts
├── quickstart.md        # Phase 1 output — validation scenarios
├── checklists/
│   └── requirements.md  # Spec quality checklist
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

Фича модифицирует **существующие** файлы и добавляет **4 новых**. Никаких новых модулей.

```text
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
├── services/
│   ├── AutoOneClickSyncScheduler.kt      # NEW — @Component, @Scheduled
│   └── AutoOneClickSyncRun.kt            # NEW — value-класс
├── controllers/
│   ├── AutoOneClickSyncStatusController.kt  # NEW — @RestController, GET /api/sync/auto-status
│   ├── ApiController.kt                  # MODIFIED — /api/sync/oneclick wraps with try { running.cas }
│   └── dto/
│       └── AutoOneClickSyncDtos.kt       # NEW — AutoOneClickSyncStatusDto, AutoOneClickSyncRunDto, TotalsDto
├── sync/
│   └── (no changes)
└── KaraokeProperties.kt                  # MODIFIED — 3 new KaraokeProperty entries (~строка 319)

webvue3/src/components/Sync/
├── store.js                              # MODIFIED — add loadSyncAutoStatusPromise
└── SyncTable.vue                         # MODIFIED — add «Автозапуск» block

livedocs/                                  # FR-014, обязательно в том же PR
├── features/
│   └── 235-auto-sync-3h.md               # NEW — LiveDoc
└── architecture-notes.md                 # MODIFIED — запись в changelog (Pass 63+)
```

**Structure Decision**: существующая multi-module структура (Kotlin backend + Vue admin SPA) — **без изменений**. Фича — аддитивная: 4 новых файла в уже существующих директориях + точечные правки `ApiController.kt`, `KaraokeProperties.kt`, `webvue3/.../SyncTable.vue`, `webvue3/.../store.js`.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| (нет нарушений) | — | — |

**Нет нарушений constitution** — Complexity Tracking пуст.

## Notes

- **Переиспользование**: 0 дублей. `runEntitySync`, `SyncRegistry`, `SyncOneClickResultDto`, `notifyStatsDirtyIfSongsPushed`, `SseNotification.crud` — все переиспользуются.
- **Минимальный surface area**: 1 новый scheduler-бин, 1 новый controller, 3 DTO, 1 Vue-блок. Без новых БД-таблиц, без новых зависимостей.
- **FR-014 reminder**: в том же PR — `livedocs/features/235-auto-sync-3h.md` + правка `livedocs/architecture-notes.md`. CI `tools/check-livedocs-structure.sh` это проверит.
- **Next phase**: `/speckit.tasks` для генерации `tasks.md` (НЕ создаётся этим планом).
