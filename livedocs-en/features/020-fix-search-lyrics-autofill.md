---
status: Active
slug: 020-fix-search-lyrics-autofill
related:
  - ../domain/catalog.md
  - ../domain/editorial.md
  - ../features/089-auto-news-song-release.md
  - ../../specs/020-fix-search-lyrics-autofill/spec.md
---

# 020 — Auto-fill found lyrics into song (LiveDoc)

> Drill-down — [specs/020-fix-search-lyrics-autofill/spec.md](../../specs/020-fix-search-lyrics-autofill/spec.md).

## What it does

В функции «Найти тексты для всех песен» результаты поиска сохраняются в
`tbl_search_async` и `tbl_search_results`. Подтверждённая проблема: **найденный
текст НЕ подставляется в текст песни**, когда текущий текст пустой (хранится
либо как `''`, либо как `[""]` — обе трактовки значат «текста ещё нет»).

**Фикс**: при обработке результата поиска проверять на «пусто» оба варианта
(`''` и `[""]`); если найден непустой кандидат — **подставлять** в `Song.songText`.

## User Stories (краткий список)

- **US1** (P1): Автоподстановка текста работает независимо от способа хранения «пустого».

## Functional Requirements (указатель)

- **FR-001**: Условие подстановки: `songText in set('', '[""]')` И найден непустой текст.
- **FR-002**: После подстановки — `Song.saveToDb()`.

## Acceptance Criteria

- [ ] **AC1**: Песня с `songText=''` → найден текст → `songText` заполнен.
- [ ] **AC2**: Песня с `songText='[""]'` → найден текст → `songText` заполнен.

## Related LiveDocs

- Domain: [catalog.md](../domain/catalog.md) (Song.songText), [editorial.md](../domain/editorial.md) (editor flow)
- Feature: [089-auto-news-song-release.md](../features/089-auto-news-song-release.md) (смежная — news service)

## Code

- Backend: `karaoke-app/.../service/SongTextSearchService.kt` — `applyFoundText()`
- Backend: `karaoke-app/.../model/Song.kt` — пометка «empty sentinel» value

## History

- Created: 2026-08-14
- Last updated: 2026-08-14