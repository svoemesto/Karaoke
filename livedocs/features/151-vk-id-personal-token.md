---
status: Active
slug: 151-vk-id-personal-token
related:
  - ../domain/publishing.md
  - ../domain/identity.md
  - ../architecture/L1-system-context.md
  - ../../specs/151-vk-id-personal-token/spec.md
---

# 151 — Миграция на VK ID для персонального токена (LiveDoc)

> Drill-down — [specs/151-vk-id-personal-token/spec.md](../../specs/151-vk-id-personal-token/spec.md).

## Что делает

Проект использует **VK API** для авто-публикации новостей о песнях (см.
`specs/121-vk-news-auto-publish`, `122-premium-auto-publish`) — методам
`video.save`, `photos.getWallUploadServer`, `photos.saveWallPhoto` нужен
**user-token** со scope `photos` + `video` + `wall` + `offline`.

**Проблема**: попытки получить новый токен через старое VK Standalone-приложение
(`oauth.vk.ru/authorize` с разными `response_type` и `redirect_uri`, включая
`https://oauth.vk.ru/blank.html`) возвращают
`{"error":"invalid_request","error_description":"Security Error"}` (HTTP 401).
Получить токен через текущее приложение невозможно.

**Решение** — миграция на **VK ID** (новый OAuth-провайдер, ID.VK.RU, см.
`https://id.vk.ru/`):
- Новый client_id (через регистрацию приложения в VK ID Console).
- `https://id.vk.ru/oauth2/auth` — authorize endpoint (см. [VK ID docs](https://id.vk.ru/about/business/go/docs)).
- `https://id.vk.ru/oauth2/token` — обмен `code → token` (Authorization Code Flow; см. [VK API](https://dev.vk.com/api/vk-id/oauth)).
- Token сохраняется в `KaraokeProperties.vkUserAccessToken` (то же поле —
  переиспользуется существующим кодом `vkUserAccessToken`).

## User Stories (краткий список)

- **US1** (P1): Получение нового user-token через VK ID без «Security Error».
- **US2** (P1): Code → token exchange работает (Authorization Code Flow).
- **US3** (P1): VK API (video.save, photos.*) снова работают в проде.

## Functional Requirements (указатель)

- **FR-001**: Зарегистрировать новое приложение в VK ID Console (вне репо — пользователь делает вручную).
- **FR-002**: `client_id` + `client_secret` сохранить в `KaraokeProperties.vkIdClientId/vkIdClientSecret`.
- **FR-003**: Endpoint `/api/public/utils/vkIdOAuthCodeUrl` (аналог текущего `vkOAuthCodeUrl`).
- **FR-004**: Endpoint `/api/public/utils/vkIdOAuthToken` (code → token).
- **FR-005**: Token → `vkUserAccessToken` — переиспользуется существующим кодом без изменений.
- **FR-006**: Старый `PublicVkAuthController` помечен `@Deprecated` (на случай отката).

## Acceptance Criteria

- [ ] **AC1**: Получить новый токен через VK ID — без «Security Error».
- [ ] **AC2**: Code → token exchange возвращает `access_token` и `expires_in`.
- [ ] **AC3**: VK API `video.save` + `photos.*` снова работают (новости публикуются).
- [ ] **AC4**: Тест в Karaoke-properties — `vkUserAccessToken` заполнен.

## Связанные LiveDocs

- Domain: [publishing.md](../domain/publishing.md) (VK публикация), [identity.md](../domain/identity.md) (OAuth)
- Architecture: [L1-system-context.md](../architecture/L1-system-context.md) (YOOKASSA / VK — внешние OAuth)
- Specs: `138-vk-photo-preview-attachment`, `121-vk-news-auto-publish`, `122-premium-auto-publish`

## Код

- Backend: `karaoke-web/.../controllers/PublicVkAuthController.kt` — добавить VK ID flow
- Backend: `karaoke-app/.../KaraokeProperties.kt` — `vkIdClientId`, `vkIdClientSecret`
- Backend: `karaoke-web/.../services/VkIdAuthService.kt` (новый)
- Скрипт: `tools/vk-id-setup.md` — инструкция по регистрации приложения в VK ID Console

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14