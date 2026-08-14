---
status: Active
slug: 100-audio-similarity-threshold
related:
  - ../domain/catalog.md
  - ../domain/editorial.md
  - ../architecture/L3-components.md
  - ../../specs/100-audio-similarity-threshold/spec.md
---

# 100 — Повышение порога аудио-похожести + демотация статуса (LiveDoc)

> Drill-down — [specs/100-audio-similarity-threshold/spec.md](../../specs/100-audio-similarity-threshold/spec.md).

## Что делает

При импорте песен из папки порог «похожести» аудио поднимается с **85% до 95%**.
Если для песни найден аудио-родитель и раньше она переводилась в статус 6
(«готова»), теперь переводится в **статус 5** (требующая проверки редактором).

**Область применения**:
- `AUDIO_PARENT_THRESHOLD` → 95 (постоянная).
- `/songs/autoassignoriginalall` — параметр `threshold` теперь дефолт 95 (но
  куратор может передать `?threshold=` явно для иного значения).

## User Stories (краткий список)

- **US1** (P1): Импорт песни с похожим аудио (бывший статус 6) → теперь статус 5.

## Functional Requirements (указатель)

- **FR-001**: Константа `AUDIO_PARENT_THRESHOLD = 95` (вместо 85).
- **FR-002**: Default `?threshold=95` для `/songs/autoassignoriginalall`.
- **FR-003**: При `audioSimilarityPercent >= 95` песня получает статус 5 (не 6).

## Acceptance Criteria

- [ ] **AC1**: Импорт песни с похожестью 90% → статус **5**, не 6.
- [ ] **AC2**: Импорт с похожестью 97% → статус 5.
- [ ] **AC3**: Куратор может `?threshold=80` — работает с пониженным порогом.

## Связанные LiveDocs

- Domain: [catalog.md](../catalog.md) (Song status), [editorial.md](../editorial.md)
- Architecture: [L3-components.md](../architecture/L3-components.md) (audio processing)

## Код

- Backend: `karaoke-app/.../KaraokeProperties.kt` — `audioParentThreshold = 95`
- Backend: `karaoke-web/.../controllers/SongsController.kt` — `/songs/autoassignoriginalall`

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14