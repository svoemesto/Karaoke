---
status: Active
slug: 121-vk-news-auto-publish
related:
  - ../domain/publishing.md
  - ../features/130-vk-preview-generation.md
  - ../features/138-vk-photo-preview-attachment.md
  - ../features/151-vk-id-personal-token.md
  - ../../specs/121-vk-news-auto-publish/spec.md
  - ../../archive/docs/features/vk-news-auto-publish.md
  - ../architecture/monetization.md
  - ../architecture/censoring.md
---

# 121 — Автопубликация новостей в группу ВКонтакте (LiveDoc)

> Drill-down — [specs/121-vk-news-auto-publish/spec.md](../../specs/121-vk-news-auto-publish/spec.md).

## Что делает

Настройка автоматической публикации новостей (из `tbl_news`) в группу
ВКонтакте через VK API (`wall.post`).

**Типы публикаций**:
1. **Премиум-выпуск** (песня добавлена в коллекцию) — текст + ссылка +
   DEMO-видео + обложка (см. `138-vk-photo-preview-attachment`).
2. **В эфире** (`category="air"`, `specs/089-auto-news-song-release`) —
   текст + ссылка + обложка.

**Требования**:
- User-token (см. `151-vk-id-personal-token`).
- `group_id` из `KaraokeProperties.vkGroupId`.
- Шаблоны публикаций из `tbl_templates` (см. `128-news-publish-templates`).
- Retry с exponential backoff (на случай rate-limit).

## User Stories (краткий список)

- **US1** (P1): Новая новость «в эфире» → пост ВКонтакте в группе.
- **US2** (P1): Премиум-выпуск → пост ВКонтакте с DEMO-видео.

## Functional Requirements (указатель)

- **FR-001**: Scheduled polling `tbl_news` + publish to VK group.
- **FR-002**: `wall.post` с `attachments` (photo + video).
- **FR-003**: Retry с backoff (1m, 5m, 30m).
- **FR-004**: Audit-log в `tbl_news_publish_log`.

## Acceptance Criteria

- [ ] **AC1**: Создать новость → ≤ 5 мин пост ВКонтакте.
- [ ] **AC2**: Премиум-выпуск → ВК пост с DEMO-видео.
- [ ] **AC3**: VK rate-limit → retry, не теряем новость.

## Связанные LiveDocs

- Domain: [publishing.md](../domain/publishing.md)
- Feature: [130-vk-preview-generation.md](../features/130-vk-preview-generation.md) (прогрев), [138-vk-photo-preview-attachment.md](../features/138-vk-photo-preview-attachment.md) (превью), [151-vk-id-personal-token.md](../features/151-vk-id-personal-token.md) (token)

## Код

- Backend: `karaoke-app/.../service/VkPublishService.kt` — polling + wall.post
- Backend: `karaoke-app/.../schedulers/VkNewsScheduler.kt` (новый)
- Backend: `karaoke-app/.../KaraokeProperties.kt` — `vkGroupId`, `vkAccessToken`

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14