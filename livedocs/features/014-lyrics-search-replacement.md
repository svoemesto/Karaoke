---
status: Active
slug: 014-lyrics-search-replacement
related:
  - ../domain/editorial.md
  - ../features/015-search-engine-selection.md
  - ../../specs/014-lyrics-search-replacement/spec.md
  - ../domain/catalog.md
  - ../architecture/queue-lanes.md
---

# 014 (lyrics) — Замена поискового движка для текстов песен (LiveDoc)

> Drill-down — [specs/014-lyrics-search-replacement/spec.md](../../specs/014-lyrics-search-replacement/spec.md).

## Что делает

SearXNG не справлялся с поиском текстов песен — мало результатов.
**Замена**: улучшенный движок (4get, Яндекс, и т.д. — см. `015-search-engine-selection`).

**Эффект**: при импорте песни без готового текста пайплайн находит
**больше** или **точнее** результатов, чем раньше.

## User Stories (краткий список)

- **US1** (P1): Пайплайн находит тексты там, где раньше SearXNG не справлялся.

## Functional Requirements (указатель)

- **FR-001**: Внедрить движок (4get или Яндекс) в `LyricsSearchService`.
- **FR-002**: A/B-тест на 50 песнях без текста.

## Acceptance Criteria

- [ ] **AC1**: 50 песен без текста → 80%+ найдены результаты.
- [ ] **AC2**: Точность (правильная песня в top-5).

## Связанные LiveDocs

- Domain: [editorial.md](../domain/editorial.md) (тексты)
- Feature: [015-search-engine-selection.md](../features/015-search-engine-selection.md)

## Код

- Backend: `karaoke-app/.../service/LyricsSearchService.kt` — выбор движка
- Backend: добавление 4get client

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14