# Implementation Plan: 289-fix-statbysong-cache-on-cold-start

**Branch**: `289-fix-statbysong-cache-on-cold-start` | **Date**: 2026-09-01 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/289-fix-statbysong-cache-on-cold-start/spec.md`

## Summary

Устранить блокирующее поведение `StatBySong.refreshCache()` при cold-start и ускорить full-scan SQL в этом методе через:
1. **Индекс** `idx_songs_id_status_source_markers` на `tbl_songs(id_status, source_markers)` — снижает duration с 4 сек до < 500 мс (Index Scan вместо Seq Scan).
2. **Async cold-start refresh** через `ScheduledExecutorService` + `AtomicBoolean refreshing` — HTTP-треды возвращают fallback (0) за < 100 мс вместо блокировки 12 сек.

Технические детали в [research.md](./research.md), сущности — в [data-model.md](./data-model.md), формат логов — в [contracts/log-format.md](./contracts/log-format.md).

## Technical Context

**Language/Version**: Kotlin 1.x, JDK 22 (JRE), Spring Boot (karaoke-web). SLF4J + Logback (default).

**Primary Dependencies**:
- **Backend**: Spring Boot (karaoke-web), Gradle multi-module.
- **Logging**: SLF4J + Logback — без новых зависимостей.
- **Concurrency**: `java.util.concurrent.ScheduledExecutorService`, `AtomicBoolean` (стандартная библиотека JDK).
- **PostgreSQL**: postgres:16, индексы через SQL-миграции в `deploy/karaoke-db/`.

**Storage**: PostgreSQL 16. Новая миграция `deploy/karaoke-db/45_*.sql`. Без изменений схемы данных.

**Testing**: ручное на admin-машине (per Constitution § Тесты). `quickstart.md` содержит end-to-end сценарии.

**Target Platform**: Linux (Ubuntu 22.04) + Docker Compose на проде, прямая Java на admin-машине (karaoke-web). Karaoke-web развёрнут на проде.

**Project Type**: Web-service (мультимодуль: karaoke-app, karaoke-web, webvue, webvue3, karaoke-public). Изменения только в `karaoke-web`.

**Performance Goals**:
- SC-001: cold-start response < 100 мс (vs 12 сек до фикса).
- SC-002: `duration:` для SQL `StatBySong.refreshCache()` < 500 мс (vs 4 сек).
- Минимум overhead от нового `ScheduledExecutorService` (1 daemon-thread).

**Constraints**:
- Constitution § «Категорически запрещено» п. 2: DDL на проде (`CREATE INDEX`) — **только по per-action согласию**.
- Constitution § VI FR-006: KDoc обязателен на `StatBySong.refreshCache()`.
- Constitution § VIII.5: никаких секретов в логах.
- AGENTS.md: pipeline проверка после ЛЮБОГО изменения (compile, lint, bootJar).

**Scale/Scope**:
- `tbl_songs` ~18k записей.
- Индекс < 5 MB, создание < 5 сек (`CONCURRENTLY`).
- 1 daemon-thread (`bgExecutor`).
- 5 файлов для правки: 1 SQL-миграция + 1 Kotlin-файл + 1 спек/policy + 0 frontend (не меняется).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Принцип | Статус | Комментарий |
|---------|--------|-------------|
| I. Self-contained автопайплайн | ✓ N/A | Не пайплайн рендера |
| **II. Сырой JDBC + дифф по хэшам** | ✓ PASS | `runCountQuery()` уже использует `Statement`/`ResultSet` (сырой JDBC). Фикс не добавляет ORM. |
| III. Двух-БД синхронизация | ✓ N/A | Index — DDL, не data. Не затрагивает `SyncRegistry`. |
| IV. Async-очередь с парсингом stdout | ✓ N/A | Не пайплайн рендера |
| V. Двух-фронтенд | ✓ N/A | Затрагивает только `karaoke-web` (server-side) |
| **VI. Code Standards** | ✓ PASS | KDoc на `refreshCache()` обновится (FR-010). FR-006 KDoc обязателен. |
| VII. Cross-Machine Setup | ✓ N/A | Локальные конфиги не затрагиваются |
| ** VIII. Секреты и git-гигиена** | ✓ PASS | Никаких секретов в логах. Миграция в `deploy/karaoke-db/` не содержит секретов. `deploy/do.env` не меняется. |
| ** «Категорически запрещено»** | ✓ PASS | FR-001 (SQL-миграция) — файл создаётся агентом, выполнение — пользователем. Правка `StatBySong.kt` — разрешена агенту (на nsa-i9 без явного согласия для bootJar). |

**Constitution Check итог**: PASS, no violations.

## Project Structure

### Documentation (this feature)

```text
specs/289-fix-statbysong-cache-on-cold-start/
├── plan.md              # Этот файл
├── spec.md              # User-facing specification (3 US, 13 FR, 5 SC)
├── research.md          # Phase 0 — D-1..D-4 решения + best practices
├── data-model.md        # Phase 1 — 4 entities (index, logger, guard, executor)
├── contracts/
│   └── log-format.md    # Phase 1 — формат WARN/INFO сообщений
├── quickstart.md        # Phase 1 — end-to-end validation
├── checklists/
│   └── requirements.md  # Spec quality checklist (15/15 ✓)
└── tasks.md             # Phase 2 — будет /speckit.tasks (НЕ создано /speckit.plan)
```

### Source Code (repository root)

```text
karaoke-web/
└── src/main/kotlin/com/svoemesto/karaokeweb/
    └── StatBySong.kt                        # правка (FR-004..FR-010)

deploy/
└── karaoke-db/
    └── 45_idx_songs_id_status_source_markers.sql  # НОВЫЙ (FR-001)
```

**Structure Decision**: используется существующая структура. Новая директория не нужна. Изменения — точечные.

## Technical Decisions (summary, см. research.md для деталей)

| ID | Решение | Обоснование |
|----|---------|-------------|
| **D-1** | `ScheduledExecutorService` (single-thread, daemon) в `companion object` | Минимальный overhead, прямой контроль lifecycle. Альтернативы (`@Async`, Spring `TaskScheduler`) — отвергнуты. |
| **D-2** | Fallback = 0 | Безопасное значение для UI (нет 500-ошибки, главная работает). |
| **D-3** | НЕ persist'ить | KISS. Persist — отдельная фича. |
| **D-4** | Двойной механизм: миграция для новых + ручной `psql` для существующего прод-контейнера | Per A-002/A-003. `initdb.d/` не сработает для существующего контейнера. |

## Risks & Mitigations

| Риск | Митигация |
|------|-----------|
| `CREATE INDEX CONCURRENTLY` падает с ошибкой (например, дубликат имени) | `IF NOT EXISTS` — idempotent. Если ошибка — пользователь видит сообщение, повторяет или rollback'ит. |
| Background refresh падает с SQL exception | `try/catch/finally` + WARN лог + `refreshing.set(false)`. UI продолжает возвращать fallback (0). |
| Два потока одновременно запускают refresh | `AtomicBoolean.compareAndSet(false, true)` — single-flight. |
| JVM shutdown во время background refresh | `isDaemon = true` — JVM не ждёт daemon-thread. Refresh прерывается. Кеш остаётся в `cachedTotal.get() == -1`. |
| `tbl_songs` слишком большая для быстрого индекса | На 18k записей — ~1-3 MB, < 5 сек. Если в будущем больше — `CONCURRENTLY` всё равно безопасен. |
| Memory overhead от `ScheduledExecutorService` | 1 daemon-thread, idle когда нет задач. Минимальный overhead. |

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

Не заполняется — Constitution Check PASS, no violations.

## Open Items (для `/speckit.tasks`)

1. **T-001**: создать SQL-миграцию `deploy/karaoke-db/45_idx_songs_id_status_source_markers.sql` (FR-001).
2. **T-002**: применить миграцию на admin-машине (без per-action согласия — локальная БД).
3. **T-003**: применить миграцию на проде — **per-action согласие** (Constitution п. 2).
4. **T-004..T-009**: правка `StatBySong.kt` (FR-004..FR-010) — async refresh, AtomicBoolean, SLF4J логирование, KDoc.
5. **T-010**: compile + ktlintCheck + bootJar (на nsa-i9 — без явного согласия).
6. **T-011**: deploy `karaoke-web` на прод — **per-action согласие**.
7. **T-012..T-014**: end-to-end validation (SC-001..SC-005) per `quickstart.md`.

Детальная разбивка — в `/speckit.tasks` (Phase 2).

## History

- Создан: 2026-09-01 (Phase 0 + Phase 1 завершены)
- Phase 0: research.md — D-1..D-4 решения + best practices
- Phase 1: data-model.md (4 entities), contracts/log-format.md, quickstart.md