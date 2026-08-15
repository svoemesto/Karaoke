---
status: Active
slug: 015-search-engine-selection
related:
  - ../domain/editorial.md
  - ../architecture/L1-system-context.md
  - ../../specs/015-search-engine-selection/spec.md
  - ../../archive/docs/features/llm-lyrics-search.md
---

# 015 — Search engine selection (text + covers) (LiveDoc)

> Drill-down — [specs/015-search-engine-selection/spec.md](../../specs/015-search-engine-selection/spec.md).

## What it does

Настройка **выбора поискового движка** отдельно для:
- **Поиск текстов песен** (4 варианта): Яндекс синхронный, Яндекс асинхронный,
  SearXNG, 4get.
- **Поиск обложек альбомов** (2 варианта): SearXNG, 4get.

В механизме поиска текстов — вопрос «Выполнить поиск заново?» с очисткой
старых результатов и выбором движка + кнопка «Удалить результаты поиска».

## User Stories (краткий список)

- **US1** (P1): Настройка движка по умолчанию для текстов + обложек.
- **US2** (P1): «Выполнить поиск заново» с очисткой.

## Functional Requirements (указаль)

- **FR-001**: `tbl_public_settings.search_engine_lyrics` и `search_engine_covers`.
- **FR-002**: UI в `webvue3` «Настройки поиска».
- **FR-003**: Кнопки «Поиск заново» + «Удалить результаты».

## Acceptance Criteria

- [ ] **AC1**: Настройка применима (тексты и обложки — отдельные).
- [ ] **AC2**: «Поиск заново» очищает старые + стартует новый.

## Related LiveDocs

- Domain: [editorial.md](../domain/editorial.md)
- Architecture: [L1-system-context.md](../architecture/L1-system-context.md) (SearXNG)

## Code

- Backend: `karaoke-app/.../service/LyricsSearchService.kt` — выбор движка
- Backend: `karaoke-app/.../service/AlbumCoverSearchService.kt` — выбор движка
- SQL: `tbl_public_settings` колонки `search_engine_lyrics`, `search_engine_covers`
- Frontend: `webvue3/src/views/Settings/SearchEngineView.vue`

## History

- Created: 2026-08-14
- Last updated: 2026-08-14