---
status: Active
slug: 123-vk-og-preview-fix
related:
  - ../domain/publishing.md
  - ../features/138-vk-photo-preview-attachment.md
  - ../features/130-vk-preview-generation.md
  - ../../specs/123-vk-og-preview-fix/spec.md
  - ../features/180-og-seo-html.md
---

# 123 — Премиум-публикация ВК: превью через attachments=photo (LiveDoc)

> Drill-down — [specs/123-vk-og-preview-fix/spec.md](../../specs/123-vk-og-preview-fix/spec.md).

## What it does

При автоматической публикации через бота (`POST /method/wall.post`) ВК **не
формировал превью с обложкой**, несмотря на OG-теги и endpoint
`/api/public/og/song`.

Гипотезы:
- VK кэшировал URL и при бот-публикации не делал re-парсинг → сниппет «залипал».
- VK при `/method/wall.post` не парсит сам, а полагается на закэшированный snippet.

**Решение**: прикреплять фото через `attachments=photo` (см. подробнее в
`138-vk-photo-preview-attachment`).

## User Stories (краткий список)

- **US1** (P1): Премиум-пост ВК имеет превью-обложку (attachments=photo).

## Functional Requirements (указатель)

- **FR-001**: `wall.post` с `attachments` (без OG-зависимости).
- **FR-002**: Fallback на текущее поведение (без превью) при ошибке.

## Acceptance Criteria

- [ ] **AC1**: Премиум-пост ВК имеет превью.
- [ ] **AC2**: Без превью → graceful fallback.

## Related LiveDocs

- Domain: [publishing.md](../domain/publishing.md)
- Feature: [138-vk-photo-preview-attachment.md](../features/138-vk-photo-preview-attachment.md) (полное решение), [130-vk-preview-generation.md](../features/130-vk-preview-generation.md) (прогрев)

## Code

- Backend: `karaoke-app/.../service/VkPublishService.kt` — добавить `attachPhoto`

## History

- Created: 2026-08-14
- Last updated: 2026-08-14