---
status: Active
slug: 113-telegram-demo-publish
related:
  - ../domain/publishing.md
  - ../features/131-fix-approve-demo-render-telegram-sync.md
  - ../architecture/L3-components.md
  - ../../specs/113-telegram-demo-publish/spec.md
---

# 113 — Автопубликация DEMO-версий в Telegram по расписанию (LiveDoc)

> Drill-down — [specs/113-telegram-demo-publish/spec.md](../../specs/113-telegram-demo-publish/spec.md).

## Что делает

Telegram-бот, который **оформляет публикации DEMO-версий по графику**.

Раньше админ вручную создавал `Publication` записи с `publishDate` и слотами
`publish10…publish23` (до 14 песен на публикацию). Тексты слотов
собирались из автора/названия. Теперь — автоматизированный бот, который
по расписанию формирует и публикует DEMO-видео в Telegram-канале.

## User Stories (краткий список)

- **US1** (P1): Бот публикует DEMO по графику без ручного создания `Publication`.
- **US2** (P2): Каждый слот может варьироваться (например, 7 слотов — не 14).

## Functional Requirements (указатель)

- **FR-001**: Scheduled job запускает публикации по cron-выражению.
- **FR-002**: Telegram Bot API — `sendVideo` или `sendMediaGroup`.
- **FR-003**: Тексты из `tbl_templates` с плейсхолдерами (см. `128-news-publish-templates`).
- **FR-004**: Retry при ошибках.

## Acceptance Criteria

- [ ] **AC1**: Расписание публикации → автоматический пост в Telegram-канале.
- [ ] **AC2**: Retry при недоступности Telegram API.

## Связанные LiveDocs

- Domain: [publishing.md](../publishing.md)
- Feature: [131-fix-approve-demo-render-telegram-sync.md](../features/131-fix-approve-demo-render-telegram-sync.md) (смежная)
- Specs: `128-news-publish-templates` (шаблоны)

## Код

- Backend: `karaoke-app/.../service/TelegramBotService.kt` (новый)
- Backend: `karaoke-app/.../schedulers/TelegramDemoScheduler.kt`
- Backend: `karaoke-app/.../KaraokeProperties.kt` — `telegramBotToken`, `telegramChannelId`

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14