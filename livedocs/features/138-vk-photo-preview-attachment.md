---
status: Active
slug: 138-vk-photo-preview-attachment
related:
  - ../domain/publishing.md
  - ../features/130-vk-preview-generation.md
  - ../features/151-vk-id-personal-token.md
  - ../architecture/L1-system-context.md
  - ../../specs/138-vk-photo-preview-attachment/spec.md
---

# 138 — Превью публикации ВК через прикрепление фото (LiveDoc)

> Drill-down — [specs/138-vk-photo-preview-attachment/spec.md](../../specs/138-vk-photo-preview-attachment/spec.md).

## Что делает

При автоматической публикации новостей в группу ВКонтакте (`group_id` через
VK API `wall.post`) **посты, опубликованные ботом, не получали графическое
превью** (картинку-обложку), несмотря на OG-теги, прогрев PNG-кэша и
другие меры.

**Решение**: прикреплять картинку **напрямую** через **VK API
`photos.getWallUploadServer` → upload → `photos.saveWallPhoto` → attachment
в `wall.post`**. Требует user-token со scope `photos` (есть в
`KaraokeProperties.vkUserAccessToken`, см. `151-vk-id-personal-token`).

**Fallback**: если `photos.*` падает с ошибкой (например, лимит API) —
откатываемся на текущее поведение (без превью) с логированием.

## User Stories (краткий список)

- **US1** (P1): Пост ВК имеет графическое превью-обложку песни.
- **US2** (P1): Fallback на «без превью» если photos.* падает.

## Functional Requirements (указатель)

- **FR-001**: `wall.post` принимает `attachments` с `photo{N}_{N}_{hash}` (результат `photos.saveWallPhoto`).
- **FR-002**: Перед публикацией — `photos.getWallUploadServer` → загрузка PNG → `photos.saveWallPhoto` → сохранить `id`.
- **FR-003**: Retry/lock — защита от race (одна песня = одно превью, повтор = используем существующее).
- **FR-004**: Если `photos.*` падает → fallback на публикацию без превью + лог.

## Acceptance Criteria

- [ ] **AC1**: Новая песня → пост ВК имеет обложку.
- [ ] **AC2**: API-ошибка → пост без обложки, опубликован, без exception (graceful).
- [ ] **AC3**: Concurrent publish одной песни — только одно превью генерируется.

## Связанные LiveDocs

- Domain: [publishing.md](../domain/publishing.md)
- Feature: [130-vk-preview-generation.md](../features/130-vk-preview-generation.md) (предыдущая), [151-vk-id-personal-token.md](../features/151-vk-id-personal-token.md) (token VK ID)
- Architecture: [L1-system-context.md](../architecture/L1-system-context.md)

## Код

- Backend: `karaoke-app/.../service/VkPublishService.kt` — добавить `attachPhoto(songId): photoRef?`
- Backend: `karaoke-app/.../service/VkPhotoUploadService.kt` (новый) — getWallUploadServer + upload + saveWallPhoto
- Backend: `karaoke-app/.../KaraokeProperties.kt` — `vkUsePhotoAttachment = true` (включение)

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14