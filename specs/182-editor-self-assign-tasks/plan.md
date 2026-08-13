# Implementation Plan: Self-Assign Tasks для редакторов

**Branch**: `182-editor-self-assign-tasks` | **Date**: 2026-08-13 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/182-editor-self-assign-tasks/spec.md`

## Summary

Self-assign-возможность для публичных редакторов сайта: флаг `canSelfAssignTasks` в `tbl_site_users`, новый endpoint `POST /api/public/songeditor/assign-self` (атомарный self-assign с `SELECT FOR UPDATE`), встроенное поле `assignment` в стрим `/api/public/zakroma` (заполняется только для self-assign-редакторов), кнопка «Взять в работу» в `karaoke-public/ZakromaView` внутри карточки каждой песни, чекбокс «Может сам назначать себе задания» в `SiteUserEdit` (webvue3).

Фича расширяет существующие паттерны (`PublicSongEditorController`, `ZakromaStreamMessageDto`, `SiteUsersController.update`, `saveSiteUser` action) — никаких greenfield-решений. Подробный анализ — в [research.md](./research.md).

## Technical Context

**Language/Version**: Kotlin 1.x (JDK 17), Vue 3 + Vite (Node 22), PostgreSQL 14+, AGPL-3.0-or-later.

**Primary Dependencies**:
- Backend: Spring Boot 2.x/3.x, сырой JDBC (`KaraokeConnection`), Jackson, kotlinx.serialization.
- Frontend (admin): Vue 3 + Vuex + Bootstrap-vue-next (`webvue3`).
- Frontend (public): Vue 3 + Bootstrap 5 (`karaoke-public`).
- Storage: PostgreSQL через `WORKING_DATABASE` (karaoke-web singleton), MinIO для стемов.

**Storage**: PostgreSQL (`tbl_site_users`, `tbl_song_assignments` — обе уже существуют; `tbl_song_assignments` пишет чекрез `KaraokeDbTable.createDbInstance`).

**Testing**: ручные сценарии в [quickstart.md](./quickstart.md). CI не запускает тесты для admin/public (см. AGENTS.md «Тесты»). Регрессия проверяется вручную.

**Target Platform**: Linux (Docker compose), JDK 17 JRE в контейнерах `karaoke-app`/`karaoke-web`.

**Project Type**: web-service (karaoke-web/public + admin SPA webvue3). См. `Project Structure` ниже.

**Performance Goals**:
- `POST /api/public/songeditor/assign-self` ≤ 500 мс (p95) на проде.
- Стрим `/api/public/zakroma` для self-assign-редактора: +1 batch SQL-запрос к `tbl_song_assignments` (на IN-clause по song_ids), влияние на латентность первого байта ≤ 200 мс.
- Чекбокс в `SiteUserEdit` сохраняется через `/api/siteusers/update` (стандартный путь, latency не меняется).

**Constraints**:
- БД — PostgreSQL (нет миграций между БД в коде, всё на SQL).
- Self-assign пишет в `WORKING_DATABASE` (один singleton), sync LOCAL↔SERVER — отдельный layer (см. `SyncRegistry`).
- `recordhash` триггер в `tbl_site_users` MUST быть пересоздан в миграции (см. constitution III).
- Все новые boolean-поля DTO MUST иметь `@get:JsonProperty` (см. AGENTS.md Q&A «Jackson отбрасывает is»).

**Scale/Scope**:
- ~18k записей в `tbl_songs`, ~50k в `tbl_song_assignments` (на проде).
- Self-assign ограничен 1 песней за раз (1 SQL транзакция).
- Стрим zakroma: ~200-500 песен на автора → 1 batch SQL по `song_id IN (...)`.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Verdict | Notes |
|---|---|---|
| I. Self-contained автопайплайн | ✅ Pass | Никаких внешних API в hot-path. Self-assign — локальная транзакция в БД. |
| II. Сырой JDBC + diff по хэшам | ✅ Pass | Используем `KaraokeConnection.getConnection()` + `SELECT FOR UPDATE`. Сравнение по `song_id` через `findExisting` (PK lookup, O(1)). |
| III. Двух-БД синхронизация | ✅ Pass | `tbl_song_assignments` и `tbl_site_users` уже в `SyncRegistry.all`. Новая колонка попадёт в diff через `recordhash`-trigger (обязательно пересоздаётся в миграции). |
| IV. Async-очередь | ✅ Pass | Self-assign не использует `KaraokeProcess*` — это короткая синхронная транзакция. |
| V. Двух-фронтенд | ✅ Pass | Admin UI → `webvue3` (как и сейчас), public UI → `karaoke-public` (только `ZakromaView.vue`). Никакого смешивания. |
| VI. Code Standards | ✅ Pass | Новые boolean-поля — `@get:JsonProperty` явно. KDoc на публичных API. После реализации — обновить per-feature документ (см. ниже). |
| VII. Cross-Machine Setup | ✅ Pass | Локальные AI-конфиги не трогаем. Запуск тестов — на машине разработчика. |
| VIII. Секреты | ✅ Pass | Новых секретов нет. |

**Gates**: ✅ Все пройдены. Никаких нарушений, нечего заносить в `Complexity Tracking`.

## Project Structure

### Documentation (this feature)

```text
specs/182-editor-self-assign-tasks/
├── plan.md              # Этот файл (/speckit.plan output)
├── research.md          # Phase 0 output (/speckit.plan)
├── data-model.md        # Phase 1 output (/speckit.plan)
├── contracts/
│   └── README.md        # Phase 1 output (/speckit.plan)
├── quickstart.md        # Phase 1 output (/speckit.plan)
└── tasks.md             # Phase 2 output (/speckit.tasks — НЕ создано)
```

### Source Code (repository root)

Зафиксированные файлы для реализации (на основе [research.md](./research.md) секции «Implementation Order»):

```text
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
├── model/
│   ├── SiteUser.kt                              # + canSelfAssignTasks field
│   ├── SiteUserDto.kt                           # + canSelfAssignTasks (admin)
│   └── SongAssignment.kt                        # UNCHANGED (переиспользуем)
├── dto/
│   └── SongAssignmentBriefDto.kt                # NEW
├── controllers/
│   ├── SiteUsersController.kt                   # + canSelfAssignTasks param
│   └── SongEditorController.kt                  # UNCHANGED (только reference)
└── sync/SyncTarget.kt                           # UNCHANGED (уже участники)

karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/
├── controllers/
│   ├── PublicSongEditorController.kt            # + assignSelf endpoint
│   └── PublicApiController.kt                   # + assignment field in stream
└── dto/
    └── ZakromaPublicDto.kt                      # + assignment field

deploy/karaoke-db/
└── XX_add_can_self_assign_tasks.sql             # NEW (DDL + recordhash-rec)

webvue3/src/components/SiteUsers/
├── store.js                                     # + canSelfAssignTasks в saveSiteUser
└── edit/SiteUserEdit.vue                        # + checkbox в секции прав редактора

karaoke-public/src/views/
├── ZakromaView.vue                              # + кнопка «Взять в работу» в карточке песни
└── (existing) EditorTasksView.vue               # без изменений, self-assign задания = adminские
```

**Structure Decision**: расширение существующей 3-фронтенд структуры (admin SPA `webvue3` + public SPA `karaoke-public` + backend `karaoke-app` + backend `karaoke-web`). Никаких новых модулей/папок. Один DTO файл (`SongAssignmentBriefDto.kt`) — выделен отдельно, чтобы не размывать admin/public DTO.

## Per-feature документ (FR-009)

После реализации — создать/обновить `docs/features/editor-tasks.md` (или добавить секцию «Self-assign»), упомянуть в KDoc `PublicSongEditorController.assignSelf`:

```kotlin
/**
 * Self-assign (FR-005): редактор с флагом canSelfAssignTasks берёт себе свободную песню.
 * @see docs/features/editor-tasks.md#self-assign
 */
@PostMapping("/assign-self")
```

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| (нет) | — | — |

Все принципы конституции соблюдены. Race-condition защита через `SELECT FOR UPDATE` — стандартный SQL-приём, не требует обоснования.

## Open Questions (resolved in research.md)

| Q | Resolution |
|---|---|
| Sync для `tbl_song_assignments`? | Уже в `SyncRegistry.all`, новых флагов не нужно. |
| Защита от race? | `SELECT FOR UPDATE` в одной транзакции. |
| Структура `assignment` в стриме? | `SongAssignmentBriefDto` (id, assigneeId, assignedAt, adminStatus). |
| Авторизация endpoint? | `currentUser(request)` + `isEditor && canSelfAssignTasks` внутри метода. |
| Где живёт флаг? | `tbl_site_users.can_self_assign_tasks` BOOLEAN NOT NULL DEFAULT FALSE. |

## Следующие шаги

1. **`/speckit.tasks`** — создаст `tasks.md` с декомпозицией на 10 этапов (см. research.md «Implementation Order»).
2. Реализация (по `tasks.md`).
3. **`/speckit.implement`** (или ручной dev) — коммиты по `tasks.md`.
4. CI-gate (см. AGENTS.md «CI-gate для master»).
5. PR + merge.
