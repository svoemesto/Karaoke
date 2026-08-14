---
status: Active
slug: 142-remove-watch-links-block
related:
  - ../domain/catalog.md
  - ../architecture/L3-components.md
  - ../../specs/142-remove-watch-links-block/spec.md
---

# 142 — Удалить блок «Ссылки на просмотр» со страницы песни (LiveDoc)

> Drill-down — [specs/142-remove-watch-links-block/spec.md](../../specs/142-remove-watch-links-block/spec.md).

## Что делает

На публичной странице песни (`/song?id=...`) под онлайн-плеером выводился блок
`.km-links-card` с заголовком «Ссылки на просмотр» — пять групп внешних ссылок
(Sponsr, Dzen, VK, Telegram, Max) × четыре версии (Все / Karaoke / Lyrics /
TABS / Chords).

**Убран целиком со страницы песни**. Поля `currentSong.linkSponsrPlay`,
`currentSong.linkDzenKaraoke`, `currentSong.linkVkLyrics`, … и сам компонент
`PlatformLink.vue` сохраняются — они используются в других публичных
представлениях (`SearchView.vue`, `ZakromaView.vue`).

## User Stories (краткий список)

- **US1** (P1): Блок «Ссылки на просмотр» отсутствует на странице песни.

## Functional Requirements (указатель)

- **FR-001**: Удалить `v-if="currentSong.onAir"` блок `.km-links-card` из `SongView.vue`.
- **FR-002**: Сам компонент `PlatformLink.vue` сохраняется.
- **FR-003**: Поля DTO — без изменений (используются в Search/Zakroma).

## Acceptance Criteria

- [ ] **AC1**: `/song?id=...` → под плеером НЕТ блока «Ссылки на просмотр».
- [ ] **AC2**: Search/Zakroma — ссылки по-прежнему работают.
- [ ] **AC3**: Код-поле `currentSong.linkSponsrPlay` и т.п. не удалено из DTO.

## Связанные LiveDocs

- Domain: [catalog.md](../catalog.md) (Song: `linkSponsrPlay` и т.п.)
- Architecture: [L3-components.md](../architecture/L3-components.md)

## Код

- Frontend: `karaoke-public/src/views/SongView.vue` — удалить блок `.km-links-card`
- Frontend: `karaoke-public/src/components/PlatformLink.vue` — оставить без изменений (используется в Search/Zakroma)

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14