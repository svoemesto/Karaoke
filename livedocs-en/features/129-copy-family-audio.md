---
status: Active
slug: 129-copy-family-audio
related:
  - ../domain/catalog.md
  - ../domain/editorial.md
  - ../architecture/L3-components.md
  - ../../specs/129-copy-family-audio/spec.md
---

# 129 — Копирование аудиосвязи при выборе похожей версии (LiveDoc)

> Drill-down — [specs/129-copy-family-audio/spec.md](../../specs/129-copy-family-audio/spec.md).

## What it does

В `SongEdit.vue` при нажатии «Похожие версии песни» и выборе строки
копируются текст, маркеры, `.srt`, текстовые представления. Но **аудиосвязь
не сохранялась** — `audioParentId`, `audioSimilarityPercent`, `audioDeltaMs`.

**Фикс**: при выборе кандидата дополнительно копировать:
- `audioParentId` — ID выбранной песни.
- `audioSimilarityPercent` — процент из результата сверки (например, 93%).
- `audioDeltaMs` — сдвиг в миллисекундах **с сохранением знака** (+120 или -250).

## User Stories (краткий список)

- **US1** (P1): При выборе похожей версии — три аудиополя заполняются.
- **US2** (P1): Отрицательный сдвиг сохраняется с минусом (не abs).

## Functional Requirements (указатель)

- **FR-001**: В `SongSimilarityModal.vue` (выбор кандидата) — копировать аудиосвязь.
- **FR-002**: API `/api/songs/{id}/family-audio` — при выборе сохраняет три поля.
- **FR-003**: `audioSimilarityPercent` — Int (0-100).
- **FR-004**: `audioDeltaMs` — Int (может быть отрицательным).

## Acceptance Criteria

- [ ] **AC1**: Сверка 93% / +120ms → в текущей песне `audioParentId=X, similarity=93, deltaMs=+120`.
- [ ] **AC2**: Сверка −250ms → сохраняется `-250`, не `250`.
- [ ] **AC3**: Повторный выбор B (вместо A) — A's данные полностью заменяются.

## Related LiveDocs

- Domain: [catalog.md](../domain/catalog.md) (Song: audio fields), [editorial.md](../domain/editorial.md) (editor flow)
- Architecture: [L3-components.md](../architecture/L3-components.md)

## Code

- Frontend: `webvue3/src/components/Songs/SongSimilarityModal.vue` — `selectCandidate()` — копировать аудиосвязь
- Frontend: `webvue3/src/services/songApi.js` — `/family-audio` action
- Backend: `karaoke-web/.../controllers/SongsController.kt` — endpoint
- Backend: `karaoke-app/.../model/Song.kt` — `audioParentId`, `audioSimilarityPercent`, `audioDeltaMs`
- SQL: `deploy/karaoke-db/<NNN>_tbl_settings_audio.sql` (миграция, если колонок ещё нет)

## History

- Created: 2026-08-14
- Last updated: 2026-08-14