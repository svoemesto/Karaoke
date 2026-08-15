---
status: Active
slug: 125-player-status-gate
related:
  - ../domain/catalog.md
  - ../features/022-song-status-lifecycle.md
  - ../../specs/125-player-status-gate/spec.md
---

# 125 — Player availability в таблице «Песни» при статусе ≥4 (LiveDoc)

> Drill-down — [specs/125-player-status-gate/spec.md](../../specs/125-player-status-gate/spec.md).

## What it does

Маленькая правка в админке: в таблице «Песни» (`webvue3/.../SongsTable.vue`)
снизить порог доступности иконки плеера со статуса 6 (READY) до статуса
4 (MARKERS_CREATED). Это позволяет редактору визуально проверить
синхронизацию текста/аккордов **сразу после разметки маркеров**, не дожидаясь
MARKERS_VERIFIED и READY.

**Не затрагивает**:
- DEMO-плеер (`playerDemo`) — прежний порог ≥6 (бизнес-логика монетизации).
- Кнопка «Открыть плеер» в `SongEdit` — уже без статусной блокировки.

## User Stories (краткий список)

- **US1** (P1): редактор открывает плеер для песни со статусом 4
  (MARKERS_CREATED) — иконка активна, открывает `/player/{id}` в новой
  вкладке.

## Functional Requirements (указатель)

- **FR-001, FR-002**: статус ≥4 → иконка активна; статус <4 → неактивна.
- **FR-003**: клик по активной иконке → онлайн-плеер в новой вкладке.
- **FR-004**: tooltip у неактивной иконки — текст про «статус <4», не
  «статус <6».
- **FR-005**: только основной плеер; DEMO-плеер и кнопка в SongEdit — без
  изменений.

## Acceptance Criteria

- [ ] **AC1** (SC-001): 100% песен со статусом ≥4 — активная иконка.
- [ ] **AC2** (SC-002): 100% песен со статусом <4 — неактивная иконка
      (регрессий нет).
- [ ] **AC3** (SC-003): администратор может открыть плеер сразу после
      создания меток (без прохождения 5 и 6 статусов).
- [ ] **AC4**: tooltip у неактивной иконки отражает новый порог (<4).

## Связанные LiveDocs

- Domain: [catalog.md](../domain/catalog.md) — сущность Song, `idStatus`.
- Feature: [022-song-status-lifecycle.md](../features/022-song-status-lifecycle.md)
  — жизненный цикл статусов (0..6).

## Код

- **Frontend**: `webvue3/src/components/Songs/SongsTable.vue` — колонка
  `player`, условие `idStatus >= 4` (было `>= 6`).
- **Frontend**: `webvue3/src/components/Songs/SongsTable.vue` — tooltip
  у неактивной иконки.

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14
