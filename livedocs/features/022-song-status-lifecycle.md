---
status: Active
slug: 022-song-status-lifecycle
related:
  - ../domain/catalog.md
  - ../domain/editorial.md
  - ../features/125-player-status-gate.md
  - ../features/155-song-state-colors.md
  - ../features/184-approve-status-choice.md
  - ../architecture/L3-components.md
  - ../../specs/022-song-status-lifecycle/spec.md
---

# 022 — Расширенный жизненный цикл статусов готовности песни (LiveDoc)

> Drill-down — [specs/022-song-status-lifecycle/spec.md](../../specs/022-song-status-lifecycle/spec.md).

## Что делает

Изменение жизненного цикла `Song.idStatus`. До фикса: 0..3 (готова = ≥3).
После — расширенный цикл с появлением авто-расставления маркеров:

- **0** — новая песня
- **1** — текст найден
- **2** — текст проверен (орфография/пунктуация)
- **3** — текст проверен (слова в тексте соответствуют словам в песне)
- **4** — маркеры расставлены
- **5** — маркеры проверены
- **6** — песня готова

**Миграция**: существующие песни со статусом ≥3 → статус 6.

**Семантика**: «готова» = `idStatus >= 6`.

## User Stories (краткий список)

- **US1** (P1): Новый жизненный цикл с 7 уровнями статуса.
- **US2** (P1): Миграция старых песен (≥3 → 6).
- **US3** (P2): «Готова» = idStatus ≥ 6 (вместо ≥3).

## Functional Requirements (указатель)

- **FR-001**: Документирован новый lifecycle в `tbl_settings.idStatus`.
- **FR-002**: SQL-миграция: `UPDATE tbl_settings SET idStatus = 6 WHERE idStatus >= 3`.
- **FR-003**: В коде `Song.isReady` → `idStatus >= 6` (вместо >= 3).

## Acceptance Criteria

- [ ] **AC1**: Все старые песни мигрированы на idStatus=6.
- [ ] **AC2**: `Song.isReady == true` для новых песен со статусом ≥ 6.
- [ ] **AC3**: UI (админка, public) — обновлены диапазоны.

## Связанные LiveDocs

- Domain: [catalog.md](../domain/catalog.md) (Song.status), [editorial.md](../domain/editorial.md) (editor flow)
- Feature: [184-approve-status-choice.md](../features/184-approve-status-choice.md) (выбор 5/6),
  [125-player-status-gate.md](../features/125-player-status-gate.md) (доступность плеера при ≥4),
  [155-song-state-colors.md](../features/155-song-state-colors.md) (цвета статусов в UI)

## Код

- SQL: `deploy/karaoke-db/<NNN>_migrate_idStatus_to_6.sql`
- Backend: `karaoke-app/.../model/Song.kt` — `val status: String get() = when (idStatus) { ... }`,
  `idStatus: Long get() = fields[SongField.ID_STATUS]?.toLongOrNull() ?: 0L`.

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14