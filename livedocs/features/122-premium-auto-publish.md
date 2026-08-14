---
status: Active
slug: 122-premium-auto-publish
related:
  - ../domain/publishing.md
  - ../domain/editorial.md
  - ../features/131-fix-approve-demo-render-telegram-sync.md
  - ../../specs/122-premium-auto-publish/spec.md
---

# 122 — Премиум-автопубликация в Telegram + ВК при появлении в коллекции (LiveDoc)

> Drill-down — [specs/122-premium-auto-publish/spec.md](../../specs/122-premium-auto-publish/spec.md).

## Что делает

Когда администратор апрувит задание редактора (`SongEditorController.approve`):
- Песня получает `idStatus=6` + readiness-флаги.
- Триггерится новость «доступна» (`category="premium"`, `specs/101-song-news-flag`).

**Эта спека добавляет**: после DEMO-рендера (см. `131-fix-approve-demo-render-telegram-sync`)
— публикация в Telegram и ВК с превью DEMO-видео.

## User Stories (краткий список)

- **US1** (P1): После approve → посты в Telegram + ВК с DEMO-видео.
- **US2** (P1): Sync новости «доступна» из LOCAL на server.

## Functional Requirements (указатель)

- **FR-001**: `TelegramPublishService.publishDemo(songId)` (новый).
- **FR-002**: `VkPublishService.publishWithDemo(songId)` (новый).
- **FR-003**: Sync через `updateRemoteSongFromLocalDatabase` (см. `131`).

## Acceptance Criteria

- [ ] **AC1**: Approve → DEMO рендерится → Telegram пост с DEMO → ВК пост с DEMO.
- [ ] **AC2**: Новость «в коллекции» появляется на сервере.

## Связанные LiveDocs

- Domain: [publishing.md](../publishing.md), [editorial.md](../editorial.md)
- Feature: [131-fix-approve-demo-render-telegram-sync.md](../features/131-fix-approve-demo-render-telegram-sync.md)
- Specs: `121-vk-news-auto-publish`, `101-song-news-flag`

## Код

- Backend: `karaoke-app/.../service/TelegramPublishService.kt` — добавить `publishDemo()`
- Backend: `karaoke-app/.../service/VkPublishService.kt` — добавить `publishWithDemo()`

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14