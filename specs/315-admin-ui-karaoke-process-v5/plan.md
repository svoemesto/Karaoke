# Implementation Plan: Admin UI для KaraokeProcess (iteration #5 — «эталон»)

**Branch**: `315-admin-ui-karaoke-process-v5` | **Date**: 2026-09-07 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/315-admin-ui-karaoke-process-v5/spec.md`

## Summary

Реализация полноценного admin UI для `KaraokeProcess` через 10-фазный speckit-цикл как **«эталон»** поверх 4 прогонов. Бэкенд — `karaoke-app/` (admin controller по pattern `StemJobsAdminController`), фронтенд — `webvue3` (admin bundle). Миграции 47 + 48 остаются в master. Редактирование, удаление (по статусу), retry, audit log с retention 30 дней. Все 17 уроков iter #1-#4 применены.

## Technical Context

**Language/Version**: Kotlin 1.9 + JDK 17 (backend), Vue 3 + Vite + JavaScript (frontend)

**Primary Dependencies**: Spring Boot (backend), Vue 3 + Vuex + Bootstrap-vue-next + Vue Router (frontend), PostgreSQL via сырой JDBC (`KaraokeConnection`, `KaraokeDbTable`)

**Storage**: PostgreSQL (миграция 47 в master commit `7ad6313c`; миграция 48 в репо, НЕ применена)

**Testing**: `karaoke-app/src/test` — `@Disabled` (CI не запускает). Verify через quickstart.md manual scenarios владельцем.

**Target Platform**: Linux server (admin bundle через webvue3), single admin user (owner)

**Project Type**: Web-application (admin SPA + admin REST controller)

**Performance Goals**:
- Top-level список 18k+ процессов: ≤2 сек (SC-001).
- Expand конкретного head (lazy load): ≤1 сек (SC-001).
- Edit операция: ≤30 сек end-to-end (SC-002).
- Audit-запись после операции: ≤1 сек (SC-003).

**Constraints**:
- Сырой JDBC, БЕЗ JPA (constitution Principle II, NON-NEGOTIABLE).
- Admin controller: `permitAll()` (как весь webvue3, по Karaoke AGENTS.md).
- 5-step verification канон `brief.md:59-63` после ЛЮБОГО изменения кода (NON-NEGOTIABLE, FR-024, R-013).
- Git: commit + push + merge только по прямому указанию владельца (FR-028).
- **Governance**: агенты НЕ перезапускают контейнер karaoke-app (NON-NEGOTIABLE per Karaoke/AGENTS.md, R-017).

**Scale/Scope**: 18k+ процессов на проде (constitution Principle II), single admin user, 20 отображаемых колонок из 28 реальных (FR-001).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Principle I (Self-contained автопайплайн)
✅ N/A — фича НЕ добавляет новых внешних SaaS зависимостей.

### Principle II (Сырой JDBC + дифф по хэшам)
✅ COMPLIANT — backend использует `KaraokeConnection`, `KaraokeDbTable`, сырой JDBC без JPA. `recordhash` через триггер миграции 47.

### Principle III (Двух-БД синхронизация через SyncRegistry)
✅ **N/A** — `tbl_processes` НЕ участвует в sync (отсутствует в `SyncRegistry.all`, sync/SyncTarget.kt:503-524, 18 таблиц). `tbl_processes_audit` тем более вне sync. Verify: `git grep -n "tbl_processes" karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/sync/SyncTarget.kt` — пусто.

### Principle IV (Async-очередь задач с парсингом stdout)
✅ COMPLIANT — admin UI НЕ меняет существующую async-логику. Cancel WAITING = soft-delete + pickup-фильтр `process_deleted_at IS NULL` (R-019, новое для iter #5).

### Principle V (Двух-фронтенд)
✅ COMPLIANT — admin UI только в `webvue3`, `karaoke-public` НЕ трогается (FR-024 + R-013 no-op).

### Principle VI (Code Standards)
✅ COMPLIANT — KDoc обязателен, ktlint + ESLint в 5-step verification.

### Principle VII (Cross-Machine Setup)
✅ COMPLIANT.

### Principle VIII (Секреты и git-гигиена)
✅ COMPLIANT.

**Constitution Check verdict**: ✅ PASS (Principle III N/A).

## Project Structure

### Documentation (this feature)

```text
specs/315-admin-ui-karaoke-process-v5/
├── spec.md              # Спецификация (265 строк, 28 FR, 5 US, 9 SC)
├── plan.md              # Этот файл
├── research.md          # Phase 0: 20 решений R-001..R-020 (включая R-016 MVP, R-017 governance, R-018 boss self-verify, R-019 pickup-фильтр, R-020 T052 orphan)
├── data-model.md        # Phase 1: 3 entities + race protection + R-019 pickup-фильтр
├── contracts/           # Phase 1: admin-process-rest-api.md (6 endpoints)
│   └── admin-process-rest-api.md
├── quickstart.md        # Phase 1: 10 validation scenarios + 5-step + governance + MVP-checkpoint
├── checklists/
│   └── requirements.md  # Quality checklist + Lessons 17/17
├── notes/
│   └── real-schema-tbl_processes.txt  # Boss self-verify (зафиксировано ДО старта, Lesson #16)
└── tasks.md             # Phase 2 (будет создан /speckit.tasks)
```

### Source Code (repository root)

**Изменяемые файлы** (на фазе implement, на основе iter #4 + R-019):

```
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
├── controllers/
│   └── KaraokeProcessAdminController.kt   # NEW (pattern: StemJobsAdminController)
├── services/
│   └── KaraokeProcessAdminService.kt       # NEW (@DependsOn("karaokeAppService") — впервые введено iter #4)
├── dto/admin/
│   └── KaraokeProcessAdminDTO.kt            # NEW (KaraokeProcessAdminDTO + ProcessAuditDTO + ProcessListResult)
├── KaraokeProcess.kt                        # MODIFY (в корне пакета, НЕ beans/): добавить @KaraokeDbTableField-поля processChainId/processDeletedAt; save() их НЕ пишет (FR-019)
└── KaraokeProcessWorker.kt                  # MODIFY (в корне пакета, НЕ workers/): pickup-логика добавить WHERE process_deleted_at IS NULL (R-019, новое iter #5)

karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/config/
└── SecurityConfig.kt                       # НЕ изменяется — anyRequest permitAll покрывает /api/admin/processes/** (проверено: SecurityConfig.kt:43-45)

webvue3/src/components/Processes/
├── ProcessesTable.vue                       # MODIFY: parent-child viz + single entry point edit + soft-deleted indicator
├── ProcessesFilterModal.vue                 # MODIFY: header «Фильтр процессов», новые поля фильтра
├── store.js                                 # MODIFY: actions edit/delete/retry/audit + children load
├── filter/store.js                          # MODIFY: новые поля фильтра
├── edit/                                    # NEW dir
│   ├── ProcessEditModal.vue                 # NEW (convention match)
│   └── ProcessEdit.vue                      # NEW (форма + datetime Local)
├── delete/                                  # NEW dir
│   └── ProcessDeleteModal.vue               # NEW
└── audit/                                   # NEW dir
    └── ProcessAuditModal.vue                # NEW

livedocs/
├── INDEX.md                                 # MODIFY: добавить ссылку
└── features/315-admin-ui-karaoke-process-v5.md # NEW (формат NNN-name)
```

### Tests (опционально)

Новые тесты НЕ обязательны (CI не запускает). Verify через quickstart.md manual scenarios владельцем.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

Нет нарушений. Principle III N/A.

## Re-evaluation after Phase 1 design

**Constitution Check verdict**: ✅ PASS.

Phase 1 не добавляет новых нарушений. R-019 (pickup-фильтр `process_deleted_at IS NULL`) добавлен в data-model секция 3 — это **усиление** Principle IV (async-очередь не должна подхватывать soft-deleted).

## Phase 0 + Phase 1 deliverables

- ✅ research.md (20 решений R-001..R-020, включая R-016 MVP-checkpoint, R-017 governance, R-018 boss self-verify, R-019 pickup-фильтр новое iter #5, R-020 T052 orphan backlog).
- ✅ data-model.md (3 entities + race protection + R-019 pickup-фильтр warning).
- ✅ contracts/admin-process-rest-api.md (6 endpoints).
- ✅ quickstart.md (10 scenarios + governance + MVP-checkpoint).
- ✅ plan.md (этот файл).

## Что нового в iter #5 vs iter #4

| Изменение | Источник |
|---|---|
| **R-019** (pickup-фильтр `process_deleted_at IS NULL` в Worker) | Кирилл iter #5 Р-3 |
| **R-020** (T052 orphans endpoint, опционально) | Кирилл iter #5 Р-5 |
| **MVP-checkpoint восстановлен** (Lesson #15 после исключения iter #4) | Урок #15 |
| **Миграция 48 не применена** (0 chains в БД) | Реальное состояние после отката |

## Следующий шаг

**Фаза 10** (Кирилл ревью плана) → после ревью → фаза 11 (`speckit.tasks`) → Алина implement с MVP-checkpoint после US1.

— Илья (boss)
