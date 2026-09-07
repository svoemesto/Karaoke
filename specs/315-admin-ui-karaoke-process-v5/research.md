# Phase 0 Research: Admin UI для KaraokeProcess (iteration #5 — «эталон»)

**Branch**: `315-admin-ui-karaoke-process-v5`
**Date**: 2026-09-07
**Spec**: [spec.md](./spec.md)

## Цель

Зафиксировать технические решения для iter #5 — «эталон» поверх 4 прогонов. Основа — research iter #4 (R-001..R-018) с уточнениями по ревью Кирилла (Р-3, Р-5).

## Решения

### R-001: Backend placement
- **Decision**: `karaoke-app/` (НЕ `karaoke-web/`).
- **Rationale**: Karaoke constitution Principle II + lesson #3 iter #1 + WORKING_DATABASE gotcha.
- **Alternatives considered**: `karaoke-web/` — отклонён (karaokeapp ≠ karaokeweb gotcha).

### R-002: DI protection
- **Decision**: `@DependsOn("karaokeAppService")` на admin service И controller.
- **Rationale**: Lesson #4 iter #1 + Кирилл iter #3 grep-контроль (0 вхождений `@DependsOn` в кодовой базе до iter #4). В iter #4 впервые введён.
- **Alternatives considered**: `ApplicationContext.getBean()` lazy — отклонён (громоздко).

### R-003: Race protection в `KaraokeProcess.save()`
- **Decision**: `save()` НЕ пишет `process_deleted_at` / `process_chain_id`. Targeted UPDATE через raw JDBC: `db.getConnection()?.use { conn → conn.prepareStatement(...).use { ps → ps.setLong(...); ps.executeUpdate() }}` (KaraokeProcess.kt:361 reference).
- **Rationale**: Lesson #5 iter #1 + урок RC-2 iter #3 (нет метода `connection.executeUpdate(...)` — `KaraokeConnection` экспонирует только `getConnection()`).
- **Alternatives considered**: Soft-delete через save() — отклонён (race-prone).

### R-004: Status transitions
- **Decision**: CREATING→WAITING/WORKING/ERROR, WAITING→WORKING, WORKING→DONE/ERROR, DONE→(terminal), ERROR→WAITING (только через retry FR-014). **Отмена** — через delete (US3), не через статус.
- **Rationale**: Кирилл iter #3 REQUEST CHANGES Б-1 (`KaraokeProcessStatuses` enum: 5 значений CREATING/WAITING/WORKING/DONE/ERROR).
- **Alternatives considered**: CANCELED в enum — отклонён (расширение scope).

### R-005: Parent-child viz — legacy без backfill
- **Decision**: Legacy-процессы с `process_chain_id=null` отображаются как **самостоятельные head-процессы без дочерних**.
- **Rationale**: Урок #10 iter #2 + Кирилл iter #3 grep: `KaraokeProcess.kt` не содержит `chainId`, `separate()` копирует `threadId` head'а в tail'ов (threadId не уникален для цепочки). Backfill эвристический (миграция 48 в iter #4 — 13 169 tails привязаны, владелец решает применять или нет).
- **Alternatives considered**: Backfill через SQL — отклонён (нет данных). Orphan-orphan endpoint (T052 [OPT]) — backlog для iter #6+.

### R-006: Tree expand — top-level + lazy load
- **Decision**: Загружаются только head-процессы (`process_chain_id IS NULL`); при клике expand — отдельный запрос за детьми.
- **Rationale**: 18k+ процессов (constitution Principle II). Load-all не масштабируется.
- **Alternatives considered**: Load-all одним запросом — отклонён (UX). Pagination+virtual scroll без tree — отклонён.

### R-007: Name substring matcher
- **Decision**: `name ILIKE '%' || ? || '%'`.
- **Rationale**: Clarification Q2 iter #3 + регистр-независимость (PostgreSQL `ILIKE`).
- **Alternatives considered**: Prefix `x%` — отклонён. Regex — отклонён.

### R-008: Grace period для WORKING delete
- **Decision**: 5 секунд. `GRACE_PERIOD_MS = 5000L` в KaraokeProcessAdminService.
- **Rationale**: Clarification Q1 iter #3 + Demucs (стем-сепарация) — самый тяжёлый.
- **Alternatives considered**: 10 сек — отклонён. 30 сек — отклонён.

### R-009: Audit retention
- **Decision**: 30 дней через cron `@Scheduled(fixedDelay = 24*60*60*1000, initialDelay = 60*1000) cleanupOldAudit()`: `DELETE FROM tbl_processes_audit WHERE created_at < NOW() - INTERVAL '30 days'` (хардкод `'30 days'`).
- **Rationale**: Owner clarification iter #1. Cron проще чем filter в каждом SELECT.
- **Alternatives considered**: Query filter — отклонён.

### R-010: Optimistic locking
- **Decision**: НЕ реализуется (last-write-wins).
- **Rationale**: Один администратор (owner).
- **Alternatives considered**: Optimistic locking по `updated_at` — отклонён.

### R-011: Convention match для Edit/EditModal
- **Decision**: `ProcessEditModal.vue` (тонкая обёртка) + `ProcessEdit.vue` (форма с `label-and-input`/`custom-confirm`/`notChanged`/`save`).
- **Rationale**: Lesson #1 iter #1 + Кирилл iter #5 grep (`SongEdit.vue` 58 вхождений `label-and-input`, 2026-09-08).
- **Alternatives considered**: Свой стиль — отклонён.

### R-012: Single entry point для edit-modal
- **Decision**: Edit-modal ТОЛЬКО через кнопку «Редактировать» в Actions-колонке.
- **Rationale**: Lesson #9 iter #2 + Кирилл iter #3 grep `@click.left="editProcess(data.item.id)"` в `ProcessesTable.vue:65-72`. **Удалить** при iter #5 implement.
- **Alternatives considered**: Двойной вход — отклонён.

### R-013: 5-step verification (канон brief.md:59-63)
- **Decision**: 5 шагов дословно: compileKotlin app+web; ktlint + npm lint webvue3 (+ karaoke-public если менялся); bootJar; Vite build + format:check webvue3 (+ karaoke-public если менялся); Docker build_webvue3 (+ build_public если менялся).
- **Rationale**: Lesson #7+#8 iter #1 + Кирилл iter #4 REQUEST CHANGES Б-2 + Р-2 (1:1 канон) + Р-2а (format:check karaoke-public).
- **Alternatives considered**: Пропустить Vite/Docker — отклонён.

### R-014: Livedocs обновление
- **Decision**: Обновить `livedocs/INDEX.md` + создать `livedocs/features/315-admin-ui-karaoke-process-v5.md`.
- **Rationale**: FR-023 + Karaoke constitution.
- **Alternatives considered**: Пропустить — отклонён.

### R-015: Git workflow
- **Decision**: Вся работа в working tree до самого финала. Финальный commit + push + merge — ТОЛЬКО по прямому указанию владельца.
- **Rationale**: Правило 2026-09-05 + FR-028.
- **Alternatives considered**: Коммиты после каждой фазы — отклонён.

### R-016: MVP-checkpoint workflow (Lesson #15)
- **Decision**: implement US1 → STOP → владелец rebuild + перезапуск + smoke-test 6 endpoints + visual verify Scenario 1, 9 → только после ОК продолжение US2..US5 + Polish.
- **Rationale**: Урок #15 iter #3 — batch mode хрупкий (6 regressions подряд в iter #2+#3). Iter #4 был исключением владельца (не норма).
- **Alternatives considered**: Batch mode — отклонён (Lesson #15).

### R-017: Governance — agent vs owner разделение (Lesson #11-#14)
- **Decision**: Чёткое разделение runtime-проверок:
  - **Агент**: статическая (grep, schema lookup через `psql -c information_schema.columns`), compile/lint/build, 5-step verification.
  - **Владелец**: перезапуск контейнера, HTTP smoke-test через curl, browser visual verify, deploy.
- **Rationale**: Уроки #11-#14 iter #3.
- **Alternatives considered**: Разрешить runtime-проверки агенту — отклонён (governance Karaoke/AGENTS.md).

### R-018: Boss self-verify через реальную схему БД (Lesson #16, Process Lesson)
- **Decision**: Перед отправкой требований Алине — boss выполняет `docker exec karaoke-db psql -U postgres -d karaoke -c "SELECT column_name, data_type, is_nullable FROM information_schema.columns WHERE table_name='tbl_processes'"` и фиксирует результат в `notes/real-schema-tbl_processes.txt`.
- **Rationale**: Урок #11 iter #3 (SQL bug `created_at`/`updated_at` прошёл все гейты). Только psql к реальной схеме ловит.
- **Status**: **Уже выполнено для iter #5** — `notes/real-schema-tbl_processes.txt` зафиксирована 2026-09-07 (28 колонок, нет created_at/updated_at/started_at/ended_at, есть last_update/process_start/process_end).

### R-019: pickup-логика Worker фильтрует `process_deleted_at IS NULL` (новое для iter #5, Кирилл Р-3)
- **Decision**: В `KaraokeProcessWorker.pickup*` методах добавить `WHERE process_deleted_at IS NULL` (сейчас 0 вхождений в коде — Кирилл iter #5 grep-контроль).
- **Rationale**: Без этого pickup-логика может подхватить soft-deleted процессы и попытаться их запустить. Cancel WAITING (FR-011) должен гарантировать что pickup-логика не подхватит удалённый процесс.
- **Status**: **Задача в tasks.md** — добавить явный WHERE `process_deleted_at IS NULL` в Worker.pickup*.

### R-020: T052 [OPT] orphans endpoint (новое для iter #5, Кирилл Р-5)
- **Decision** *(опционально, решает владелец)*: `GET /api/admin/processes?orphans=true` возвращает процессы с `process_chain_id NOT NULL AND NOT IN (SELECT id FROM tbl_processes WHERE process_deleted_at IS NULL)`. UI toggle «Orphans» в фильтр-модалке. Решает Lesson #10 dead code в полном объёме (FR-006 badge reachable).
- **Rationale**: Кирилл iter #5 Р-5 — orphan-определение (hard-missing parent vs soft-deleted parent).
- **Status**: **Backlog** для iter #5 если владелец решит включить (T052 [OPT] в tasks.md).

## NEEDS CLARIFICATION — все resolved

| # | Topic | Decision | Source |
|---|---|---|---|
| R-005 | Backfill chain_id | Legacy = самостоятельные head; миграция 48 опциональна | Lesson #10 + Кирилл iter #3 Б-5 |
| R-006 | Tree expand perf | Top-level + lazy load | Clarification Q3 iter #3 |
| R-007 | Name matcher | `ILIKE %x%` | Clarification Q2 iter #3 |
| R-008 | Grace period | 5 секунд | Clarification Q1 iter #3 |
| R-009 | Audit retention | 30 дней через cron + `INTERVAL '30 days'` хардкод | Owner iter #1 + Lesson RC-2 iter #3 |
| R-010 | Optimistic locking | last-write-wins | Кирилл iter #3 Б-4 |
| R-011 | Edit/EditModal pattern | ProcessEditModal + ProcessEdit | Lesson #1 iter #1 |
| R-012 | Single entry point | Только кнопка «Редактировать» | Lesson #9 iter #2 |
| R-016 | Workflow | MVP-checkpoint после US1 | Lesson #15 iter #3 |
| R-017 | Runtime проверки | Только владелец | Lessons #11-#14 iter #3 |
| R-018 | Boss self-verify | psql information_schema | Lesson #11 iter #3 + Lesson #16 iter #4 |
| R-019 | Pickup-фильтр | WHERE process_deleted_at IS NULL (новое iter #5) | Кирилл iter #5 Р-3 |
| R-020 | Orphan endpoint | T052 [OPT], backlog | Кирилл iter #5 Р-5 |

0 [NEEDS CLARIFICATION] остаётся. Готов к Phase 1 (design + contracts + quickstart).

— Илья (boss)
