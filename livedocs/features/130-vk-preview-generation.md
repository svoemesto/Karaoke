---
status: Active
slug: 130-vk-preview-generation
related:
  - ../domain/publishing.md
  - ../features/138-vk-photo-preview-attachment.md
  - ../architecture/L3-components.md
  - ../../specs/130-vk-preview-generation/spec.md
---

# 130 — Предварительная подготовка PNG-кэша перед публикацией ВК (LiveDoc)

> Drill-down — [specs/130-vk-preview-generation/spec.md](../../specs/130-vk-preview-generation/spec.md).

## Что делает

При публикации песни во ВКонтакте запись создавалась с текстом и ссылкой, но
**без графического превью**. Гипотеза: бот ВК при первом обращении ещё не
получает PNG (генерация на лету занимает время), превышает таймаут, постит без
превью.

**Решение**: `VkPreviewWarmupClient` — синхронно прогревает PNG-кэш
`/api/public/song-vk-image/{id}` ДО `wall.post`. После прогрева файл уже на
диске (MinIO/локальный кэш) → бот ВК получает его моментально.

**Примечание**: эта фича НЕ решила проблему (см. `138-vk-photo-preview-attachment`),
но остаётся полезной как прогрев для OG-парсинга и быстрого ответа на первые
запросы.

## User Stories (краткий список)

- **US1** (P1): Публикация ВК → прогретый PNG-кэш виден сразу после `wall.post`.

## Functional Requirements (указатель)

- **FR-001**: `VkPreviewWarmupClient.warmup(songId)` — синхронный запрос к `/song-vk-image/{id}` с retry.
- **FR-002**: Прогрев блокирует `wall.post` (не отдавать в ВК, пока файл не готов).
- **FR-003**: Таймаут прогрева ≤ 5 сек (иначе fallback — постинг без превью).

## Acceptance Criteria

- [ ] **AC1**: `/song-vk-image/{id}` сразу после прогрева возвращает 200 + PNG.
- [ ] **AC2**: `wall.post` вызывается только после `warmup().isSuccess`.

## Связанные LiveDocs

- Domain: [publishing.md](../publishing.md)
- Feature: [138-vk-photo-preview-attachment.md](../features/138-vk-photo-preview-attachment.md) (связанная)
- Architecture: [L3-components.md](../architecture/L3-components.md)

## Код

- Backend: `karaoke-app/.../service/VkPreviewWarmupClient.kt` (новый)
- Backend: `karaoke-app/.../service/VkPublishService.kt` — вызов warmup перед wall.post

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14