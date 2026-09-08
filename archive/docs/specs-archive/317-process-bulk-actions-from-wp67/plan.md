# Implementation Plan: Массовые действия с процессами (iter #317)

**Branch**: `317-process-bulk-actions` | **Date**: 2026-09-08 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/home/nsa/Karaoke/specs/317-process-bulk-actions/spec.md` (sha256 `ca52d54f0f7f0c901fe1c98a11811f2b1141d575e746d1570b8a005fd1300d21`, 3 цикла Stage 2 ревью Кирилла → APPROVE, 9 правок Р-1..Р-8 + бонус Assumption).

## Summary

Добавить два новых массовых действия над выборкой процессов в `ProcessesTable.vue`:

1. **Bulk-update** поля (`priority` / `status` / `threadId`) — POST `/api/admin/processes/bulk-update`.
2. **Bulk-delete** (soft-delete) — POST `/api/admin/processes/bulk-delete`.

Endpoint'ы — в **существующем** `KaraokeProcessAdminController.kt` (прецедент 6 endpoints из spec #315, `@RequestMapping("/api/admin/processes")`, auth = `permitAll()`). Bulk-логика — в `KaraokeProcessAdminService.kt` (reuse INSERT :643 для audit, фильтр `WHERE ... process_deleted_at IS NULL` :155, guard `:249`).

**Расширяемый каркас**: новые bulk-операции (reparenting, retry, restore) добавляются позже без переделки.

## Technical Context

**Language/Version**: Kotlin 1.x / JDK 17 (backend, karaoke-app), Vue 3 + Vite + Vuex (frontend, webvue3).

**Primary Dependencies**: Spring Boot 2.x/3.x (controllers/services), Bootstrap-vue-next (UI), `<custom-confirm>` (existing component, `webvue3/src/components/Common/CustomConfirm.vue`), Vuex-store (existing `webvue3/src/components/Processes/store.js`), Postgres JDBC (`KaraokeConnection`).

**Storage**: PostgreSQL (через сырой JDBC, `KaraokeConnection.kt`).

**Testing**: JUnit 5 (backend, `karaoke-app/src/test`), runtime-верификация — владелец (governance).

**Target Platform**: Linux server (admin-machine nsa-i9, OS user nsa — `karaoke-app` НЕ перезапускается агентом), Docker + docker-compose.

**Project Type**: Web application (admin SPA + backend).

**Performance Goals**: SC-008 ≤ 2 сек для 1000 процессов (bulk-update/bulk-delete), `WHERE id IN (...)` паттерн (Constitution II — O(n), не цикл).

**Constraints**:
- Лимит 10000 (FR-009) — UI-операция, batch-splitting out of scope.
- Auth = `permitAll()` (single-user, прецедент spec #315).
- `permitAll` НЕ нарушает governance — спека 315 уже использует этот паттерн.
- Bulk-update status применяет те же transition-правила (FR-017 spec #315): недопустимый → skipped+reason.

**Scale/Scope**: 13 169+ процессов в БД (прецедент iter #4 spec #315, миграция 48). Маленькая фича — 3 поля в v1.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Принцип | Применим | Статус |
|---|---|---|
| I. Self-contained автопайплайн | N/A | Фича вне горячего пути медиа (admin-UI для очереди) — N/A |
| II. Сырой JDBC + `WHERE id IN (..)` | ✅ | `WHERE id IN (..)` для UPDATE, не цикл (SC-008 ≤ 2 сек) — **PASS** |
| III. Двух-БД синхронизация | N/A | `tbl_processes` НЕ в sync (нет 8 `sync_<key>_<push|pull>_<insert|update|delete|move>_allowed` флагов в `KaraokeProperties.kt`), `tbl_processes_audit` тоже — N/A |
| IV. Async-очередь задач | N/A | Не меняем worker logic (только admin-UI + admin-endpoints) — N/A |
| V. Двух-фронтенд | ✅ | UI — `webvue3` (admin), `karaoke-public` нетронут — **PASS** |
| VI. Code Standards | ✅ | KDoc с `@see specs/317-process-bulk-actions/spec.md`, ktlint/eslint, pre-commit hooks — **PASS** |
| VII. Cross-Machine Setup | N/A | Никаких cross-machine специфик — N/A |
| VIII. Секреты и git-гигиена | ✅ | Никаких секрет-файлов, `.gitignore` не трогаем — **PASS** |

**Все применимые принципы PASS**. Complexity Tracking не нужен (нет нарушений).

## Project Structure

### Documentation (this feature)

```text
specs/317-process-bulk-actions/
├── plan.md              # Этот файл
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   └── karaoke-process-bulk-actions-api.md  # 2 endpoint'а
├── checklists/
│   └── requirements.md  # 16/16 PASS (Stage 2)
├── reviews/             # Кирилл (Stage 2) + Марк (Stage 9)
└── tasks.md             # Phase 2 output (/speckit.tasks)
```

### Source Code (repository root)

```text
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
├── controllers/
│   └── KaraokeProcessAdminController.kt   # MODIFY: +2 endpoints (POST bulk-update, POST bulk-delete)
└── services/
    └── KaraokeProcessAdminService.kt       # MODIFY: +2 метода (bulkUpdate, bulkDelete) + audit reuse

webvue3/src/components/Processes/
├── ProcessesTable.vue                     # MODIFY: +2 bulk-кнопки в шапке (после Фильтр), +computed countRows
├── store.js                                # MODIFY: +2 actions (processesBulkUpdate, processesBulkDelete)
└── filter/ProcessesFilterModal.vue         # (без изменений)
```

### Structure Decision

**Single project, multi-module** (как в spec #315). Backend — в `karaoke-app` (НЕ в `karaoke-web` — gotcha per `karaoke-web-architecture-boundaries.md`, `WORKING_DATABASE = com.svoemesto.karaokeapp.WORKING_DATABASE`). Frontend — в `webvue3` (admin-SPA). Логика — reuse существующего `KaraokeProcessAdminService` (не создаём новый сервис).

## Key Decisions (D-1..D-7)

| # | Решение | Обоснование |
|---|---|---|
| D-1 | Endpoint'ы — в **существующем** `KaraokeProcessAdminController.kt` | Прецедент 6 endpoints из spec #315, auth = `permitAll()` |
| D-2 | Bulk-логика — в **существующем** `KaraokeProcessAdminService.kt` | Reuse INSERT :643 (audit), фильтр :155 (process_deleted_at IS NULL), guard :249 |
| D-3 | `POST` вместо `DELETE` для bulk-delete | Прецедент webvue3: все admin-действия POST, нет DELETE-with-body паттернов |
| D-4 | UI — `computed countRows` в `ProcessesTable.vue` | Прецедент `SongsTable.vue:410/:421` — кнопка disabled при пустой выборке |
| D-5 | Прямые enum-значения статуса (`CREATING/WAITING/WORKING/DONE/ERROR`) в UI и API | Прецедент `ProcessEdit.vue:184-185` — симметрия «как рядом» |
| D-6 | Bulk-update status применяет те же transition-правила (FR-017 spec #315) | Reuse `AdminService InvalidStatusTransitionException`, недопустимый → skipped+reason |
| D-7 | Audit — `old_value::jsonb` / `new_value::jsonb` | Фактическая схема `tbl_processes_audit` (`process_id, actor, action, old_value::jsonb, new_value::jsonb, created_at`) |

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| (none) | — | — |

**Все применимые принципы Constitution v2.1.0 PASS**. Complexity tracking пуст.

## Implementation Plan (high-level)

### Phase 1 — Backend (8 tasks)

1. **T001** [P] Add `processesBulkUpdate(ids, field, value)` method в `KaraokeProcessAdminService.kt` (reuse INSERT :643, фильтр :155, transition-валидация через FR-017).
2. **T002** [P] Add `processesBulkDelete(ids)` method в `KaraokeProcessAdminService.kt` (reuse soft-delete + guard :249, проверка children для head-процессов).
3. **T003** Add `POST /api/admin/processes/bulk-update` endpoint в `KaraokeProcessAdminController.kt`.
4. **T004** Add `POST /api/admin/processes/bulk-delete` endpoint в `KaraokeProcessAdminController.kt`.
5. **T005** KDoc с `@see specs/317-process-bulk-actions/spec.md` для новых публичных API.
6. **T006** Backend tests (JUnit 5) — happy path + transition-skipped + idempotency + head-with-children-skipped.
7. **T007** Verify `WHERE id IN (..)` для 1000 процессов ≤ 2 сек (SC-008 benchmark).
8. **T008** 5-step subset: compileKotlin, ktlintCheck (app+web), bootJar (app+web).

### Phase 2 — Frontend (7 tasks)

9. **T009** [P] Add `computed countRows` в `ProcessesTable.vue` (прецедент `SongsTable.vue`).
10. **T010** [P] Add `bulkChangeField()` method в `ProcessesTable.vue` — открывает `<custom-confirm>` с тремя полями.
11. **T011** [P] Add `bulkDelete()` method в `ProcessesTable.vue` — открывает `<custom-confirm>` с подтверждением.
12. **T012** Add 2 кнопки в шапку `ProcessesTable.vue` (после Фильтр): «Массовое изменение поля» + «Массовое удаление», обе `:disabled="countRows === 0"`.
13. **T013** Add Vuex actions `processesBulkUpdate` / `processesBulkDelete` в `webvue3/src/components/Processes/store.js`.
14. **T014** JSDoc для новых actions (Constitution VI).
15. **T015** 5-step subset (frontend): eslint, vite build, format:check.

### Phase 3 — Polish (2 tasks)

16. **T016** Regression grep: `parseRetentionDays`=0, `SyncRegistry` для `tbl_processes`=0.
17. **T017** Livedoc `livedocs/features/317-process-bulk-actions.md` (NEW) + INDEX row.

**Итого**: 17 задач (T001..T017). 5-step subset шаги 1-3 (compileKotlin + ktlintCheck + bootJar) для backend, шаги 1-2 (eslint + vite build) для frontend.

## Governance

- Агент НЕ перезапускает `karaoke-app` (nsa-i9 / nsa — запрещено per Constitution V).
- Smoke-test, visual verify, deploy — **только владелец**.
- 5-step verification subset — агент делает (compile, lint, build). Полный 5-step (с Docker build_webvue3 + npm run lint + bootJar для обоих модулей) — перед mark-review.
- Один squash-коммит на фичу, push + merge через PR — по явному указанию владельца.
- Pre-commit check: `git ls-files | grep -iE '\.env$|do\.env$|\.key$|\.pem$'` MUST быть пусто.

## Verification (runtime — владелец)

После APPROVE Stage 9 (Марк, mark-review будет после успешного runtime-verify):

1. **Scenario 1 (US1)**: filter=ERROR → 5 процессов → bulk-update status→WAITING → backend отвечает `{"updated": 5, "skipped": 0}` → таблица обновляется.
2. **Scenario 2 (US1)**: filter=ERROR → 5 процессов → bulk-update status→CREATING (запрещённый переход ERROR→CREATING) → `{"updated": 0, "skipped": 5, "reasons": ["недопустимый переход ERROR→CREATING"]}`.
3. **Scenario 3 (US1)**: bulk-update priority=−1 на 1000 процессов → ≤ 2 сек (SC-008).
4. **Scenario 4 (US2)**: filter=DONE, age>30d → 10 процессов → bulk-delete → `{"deleted": 10, "skipped": 0}` → таблица обновляется.
5. **Scenario 5 (US2)**: filter=head с 2 детьми → bulk-delete → `{"deleted": 0, "skipped": 1, "reasons": ["есть дочерние"]}` → дети остались нетронутыми.
6. **Scenario 6 (audit)**: psql `SELECT COUNT(*) FROM tbl_processes_audit WHERE action='bulk-update' AND created_at >= ?` = `updated`.
7. **Scenario 7 (UI)**: пустая выборка → обе кнопки `:disabled="countRows === 0"`.

После всех scenarios → `tracker.sh mark-review 67` (по владельцу после успешной runtime-верификации) → `tracker.sh close-issue 67` (по владельцу).
