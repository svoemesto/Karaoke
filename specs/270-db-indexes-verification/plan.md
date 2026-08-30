# Implementation Plan: Верификация и идемпотентное создание индексов FR-110

**Branch**: `270-db-indexes-verification` | **Date**: 2026-08-26 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/270-db-indexes-verification/spec.md`

## Summary

Реализует Tier-2 P1 оптимизацию FR-110 из parent спеки [241-db-storage-perf-audit](../241-db-storage-perf-audit/spec.md):
создаёт **идемпотентную** SQL-миграцию `41_db_indexes_verification.sql`, которая через
`CREATE INDEX IF NOT EXISTS` гарантирует наличие трёх индексов:

| Индекс | Таблица | Колонка |
|--------|---------|---------|
| `tbl_songs_song_author_index` | tbl_songs | song_author |
| `tbl_songs_id_status_index` | tbl_songs | id_status |
| `tbl_events_song_id_index` | tbl_events | song_id |

**Контекстная находка** (см. Clarifications Session 2026-08-26 Q1): все три индекса **уже существуют**
с `01_initdb.sql:148,173` (исходно как `tbl_settings_*_index`, переименованы в
`28_rename_settings_to_songs.sql:48,66`). На текущем проде миграция — **no-op**.
Реальная ценность фичи — **документирование в git-истории** + защита от случая восстановления БД
из старого дампа без индексов.

## Technical Context

**Тип**: SQL-миграция (DML/DDL).
**Применяется**: вручную через `docker exec -i karaoke-db psql ...`, как и все предыдущие 40 миграций.
**Идемпотентность**: `CREATE INDEX IF NOT EXISTS` — повторное применение no-op.
**Совместимость**: PostgreSQL 9.5+ (проект на 14+).

## Constitution Check (NON-NEGOTIABLE принципы)

- **§ II Сырой JDBC + дифф по хэшам**: PASS. Миграция — DDL, не меняет стек доступа к БД.
- **§ VI Code Standards**: PASS. Header-комментарий в миграции со ссылкой на FR-110 (аналогично
  `40_site_user_can_self_assign_tasks.sql`).
- **Git workflow**: PASS. Ветка `270-db-indexes-verification`, PR через `gh pr create` →
  `gh pr checks` → merge.

## Project Structure

Изменения в 2 файлах:

```
deploy/karaoke-db/
└── 41_db_indexes_verification.sql   # NEW: идемпотентная миграция (3× CREATE INDEX IF NOT EXISTS)

livedocs/features/
└── 270-db-indexes-verification.md   # NEW: per-feature документ (FR-005)

specs/270-db-indexes-verification/
├── spec.md                          # NEW
├── plan.md                          # NEW (этот файл)
├── tasks.md                         # NEW
└── checklists/requirements.md       # NEW
```

**Никакого Kotlin-кода** — только миграция + LiveDoc + спека. Это Tier-2 «dba-fix», не фича кода.

## Implementation Steps

### 1. `deploy/karaoke-db/41_db_indexes_verification.sql` — NEW

**Структура** (по образцу `40_site_user_can_self_assign_tasks.sql`):

```sql
-- [Header-комментарий с описанием и FR-ссылкой]
-- [Apply-блок: локально + прод]
-- [DDL: 3× CREATE INDEX IF NOT EXISTS]

-- Tier-2 P1 оптимизация FR-110 из parent спеки 241-db-storage-perf-audit.
-- Все три индекса уже созданы в 01_initdb.sql (изначально как tbl_settings_*_index,
-- переименованы в 28_rename_settings_to_songs.sql). Миграция идемпотентна —
-- повторное применение no-op. Цель — задокументировать в git-истории и
-- защитить от случая восстановления БД из старого дампа без индексов.
--
-- Apply:
--   локально: docker exec -i karaoke-db psql -U postgres -d karaoke < deploy/karaoke-db/41_db_indexes_verification.sql
--   прод:     ssh root@${PROD_HOST:-188.119.64.111} \
--               'docker exec -i karaoke-db psql -U postgres -d karaoke \
--                < /root/Karaoke/deploy/karaoke-db/41_db_indexes_verification.sql'
--
-- Идемпотентна: CREATE INDEX IF NOT EXISTS (повторное применение — no-op).

CREATE INDEX IF NOT EXISTS tbl_songs_song_author_index ON public.tbl_songs USING btree (song_author);
CREATE INDEX IF NOT EXISTS tbl_songs_id_status_index ON public.tbl_songs USING btree (id_status);
CREATE INDEX IF NOT EXISTS tbl_events_song_id_index ON public.tbl_events USING btree (song_id);
```

### 2. `livedocs/features/270-db-indexes-verification.md` — NEW

Per-feature документ (FR-005/FR-014). Содержит:
- Summary / Why (FR-110 parent спеки 241, Tier-2).
- **Контекстная находка** — индексы уже существуют с `01_initdb.sql`.
- Effect: гарантия наличия + документирование в git.
- Cross-links: `../241-db-storage-perf-audit.md`, `../specs/270/...`, `01_initdb.sql`, `28_rename_settings_to_songs.sql`.
- Notes: convention имён `tbl_*_*_index` (не `idx_*`), `CREATE INDEX` без `CONCURRENTLY`.

### 3. CI checks (после коммита, через GitHub Actions)

Поскольку миграция — только SQL-файл, CI проверки:
- [x] `tools/check-livedocs-structure.sh` (LiveDoc валиден)
- [x] `tools/check-livedocs-cross-links.sh` (cross-links OK)
- [x] `tools/check-livedocs-external-links.sh` (нет битых ссылок)
- [x] Документ в `livedocs/features/` — проверка наличия

**Никаких** ktlint/ESLint/Prettier/JSDoc — Kotlin/JS код не менялся.

## Risks & Mitigations

| Риск | Митигация |
|------|-----------|
| На проде индекс с таким именем существует, но на другой колонке | `CREATE INDEX IF NOT EXISTS` выдаст WARNING, но НЕ упадёт. Convention проекта исключает такой случай. |
| `tbl_songs` или `tbl_events` не существует (fresh БД до предыдущих миграций) | По номеру 41 миграция естественно применяется после всех предыдущих (≤40), таблицы гарантированно созданы. |
| Долгая блокировка таблицы при создании индекса (нет `CONCURRENTLY`) | На текущем проде — no-op (индекс уже есть). На fresh БД — таблицы маленькие (dev), блокировка миллисекунды. На большой существующей БД — `CREATE INDEX` без CONCURRENTLY берёт `ShareLock` на запись, в продакшн-окно с низким трафиком это OK. |
| Оператор по привычке применит `CONCURRENTLY` | Явный комментарий в header: «Миграция намеренно использует `CREATE INDEX IF NOT EXISTS` без `CONCURRENTLY` (FR-006, см. spec.md Clarifications Q3)». |

## Definition of Done

- [ ] `deploy/karaoke-db/41_db_indexes_verification.sql` создан с правильным header и 3× `CREATE INDEX IF NOT EXISTS`.
- [ ] `livedocs/features/270-db-indexes-verification.md` создан (FR-005).
- [ ] `specs/270-db-indexes-verification/` — 4 файла (spec, plan, tasks, checklist).
- [ ] LiveDocs structure + cross-links + external-links PASS.
- [ ] PR создан, CI 7/7 PASS (только LiveDocs проверки + Doc), merge в master.

## Next Steps

После мёрджа — обновить `specs/241-db-storage-perf-audit/tasks.md`:
- T012.6 → `[x] FR-110 реализован (PR #..., идемпотентная миграция 41)`.
- Обновить `livedocs/architecture-notes.md` §Pass 241 — отметить FR-110 как done с пометкой
  «индексы уже существовали в `01_initdb.sql`, миграция для документирования».

Также — **runtime-валидация** на проде (опционально, делается пользователем):
```sql
EXPLAIN ANALYZE SELECT song_author, count(*) FROM tbl_songs WHERE id_status >= 6 GROUP BY song_author;
EXPLAIN ANALYZE SELECT song_id, count(*) FROM tbl_events WHERE song_id > 0 GROUP BY song_id;
```
Оба должны показывать Index Scan / Bitmap Index Scan, не Seq Scan.