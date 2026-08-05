# Feature Specification: Миграция на VK ID для персонального токена пользователя

**Feature Branch**: `151-vk-id-personal-token`
**Created**: 2026-08-05
**Status**: Draft
**Input**: User description: "Мы это уже проходили: шаг 1 возвращает `{"error":"invalid_request","error_description":"Security Error"}`. Давай перейдём на VK ID. Пиши спеку."

## Контекст и текущее поведение

В спекации [specs/138-vk-photo-preview-attachment](../138-vk-photo-preview-attachment/spec.md)
запланирована загрузка PNG-обложки песни в ВКонтакте через методы
`photos.getWallUploadServer` / `photos.saveWallPhoto`. Эти методы требуют
**user-token** с scope `photos` (community-token возвращает `error_code=27`
«Group authorization failed»).

### Что было сделано раньше (хронология)

| Дата | Мера | Результат |
|------|------|-----------|
| 02.08.2026 | Создано Standalone-приложение VK, client_id=54704234. Получен user-token через Implicit Flow со scopes `video,photos,wall,offline`. | Токен сохранён в `KaraokeProperties.vkUserAccessToken`, использовался для `video.save` |
| 02.08.2026 | Обнаружено: Implicit Flow на Web возвращает `Security Error` (см. комментарий в `ApiController.kt:7196`). Реализован Authorization Code Flow через `karaoke-web` (`PublicVkAuthController`). | Endpoint `/api/public/utils/vkOAuthCodeUrl` готов |
| 04.08.2026 | Спека #138: реализация photos.* + fallback на docs.* | Код написан, в проде не развёрнут |
| 05.08.2026 | Попытка получить новый токен (старый потерян) — все варианты `/oauth.vk.ru/authorize` (с `response_type=code` и `response_type=token`, с разными `redirect_uri`, включая `https://oauth.vk.ru/blank.html`) возвращают `{"error":"invalid_request","error_description":"Security Error"}` (HTTP 401) | **Заблокировано**: получить новый токен через текущее приложение невозможно |

### Главная проблема

Приложение VK с `client_id=54704234` (Standalone, зарегистрировано 02.08.2026)
**полностью заблокировано** на `/oauth.vk.ru/authorize`. VK возвращает
`Security Error` для всех запросов — и для серверных (curl), и для клиентских
(браузер). Это означает:

1. ❌ Невозможно получить новый user-token через текущий OAuth flow.
2. ❌ Невозможно загрузить превью-фото в посты ВКонтакте (см. спеку #138).
3. ❌ Бот публикует посты «голо» — без графического превью.
4. ⚠️ Резервный путь через `docs.*` (community-token) работает, но даёт
   некачественное превью в виде документа с иконкой файла, а не как сниппет.

### Решение — переход на VK ID

Сервис авторизации **VK ID** (`id.vk.ru`) — это рекомендуемый современный
способ авторизации пользователей ВКонтакте. В отличие от устаревшего
`oauth.vk.ru`, VK ID:

- ✅ Выдаёт токены в формате, совместимом с VK API (включая `photos.*`).
- ✅ Поддерживает **refresh_token** для долгосрочного доступа без повторного
  подтверждения пользователем.
- ✅ Имеет чёткую процедуру модерации и поддержки приложений.
- ✅ Защищён **PKCE** (Proof Key for Code Exchange) — обязательно для public clients.
- ✅ Является развитием OAuth 2.0 / OIDC — стандартный протокол.

### Цель фичи

Получить валидный персональный токен пользователя через сервис авторизации
VK ID, чтобы разблокировать загрузку превью-фото в постах ВКонтакте (спека #138).
Обеспечить долгосрочную работу токена через автоматический refresh
по `refresh_token`.

## Изученные варианты решения

### Вариант A — Пересоздать Standalone-приложение на `oauth.vk.ru` (❌ ОТКЛОНЁН)

**Метод**: зарегистрировать новое Standalone-приложение на `vk.com/apps?act=manage`,
получить новый `client_id`, использовать Implicit Flow (`response_type=token`).

**Преимущества**:
- ✅ Не нужно переключаться на новый сервис авторизации.

**Недостатки**:
- ❌ Непонятно, почему текущее приложение заблокировано. Если причина —
  глобальная политика VK по отношению к Standalone-приложениям,
  новое приложение тоже будет заблокировано.
- ❌ Implicit Flow deprecated, VK рекомендует переходить на VK ID.
- ❌ Токен без `refresh_token` — если истечёт, нужен ручной повтор.

### Вариант B — Пересоздать Web-приложение на `oauth.vk.ru` (⚠️ ЧАСТИЧНО РЕАЛИЗОВАН)

**Метод**: уже реализован в `PublicVkAuthController` (karaoke-web).
Использует Authorization Code Flow с `client_secret`.

**Преимущества**:
- ✅ Уже работает с текущим `client_id=54704234` — код готов.
- ✅ Server-side обмен `code → token`, безопаснее Implicit Flow.

**Недостатки**:
- ❌ **Заблокировано**: VK возвращает `Security Error` на `/oauth.vk.ru/authorize`.
- ❌ Без `refresh_token` — токен истечёт через ~24 часа (без `offline` scope).
- ❌ Без `offline` scope токен не бессрочный.
- ❌ Даже если получится зарегистрировать новое Web-приложение, не факт,
  что его не постигнет та же участь.

### Вариант C — VK ID (`id.vk.ru`) (✅ ОСНОВНОЙ)

**Метод**: зарегистрировать новое приложение через сервис VK ID
(https://id.vk.com/about/business/go/), реализовать OAuth 2.0 / OIDC flow
с PKCE, использовать `refresh_token` для автообновления `access_token`.

**Преимущества**:
- ✅ Рекомендуемый современный способ авторизации VK.
- ✅ Выдаёт `refresh_token` — долгосрочный доступ без участия пользователя.
- ✅ PKCE защищает от перехвата кода.
- ✅ Токены совместимы с VK API (включая `photos.*`).
- ✅ Чёткая процедура модерации — приложение сразу понятно зарегистрировано.

**Недостатки**:
- ⚠️ Требует регистрации нового приложения — пользователь делает вручную.
- ⚠️ `access_token` живёт ~1 час — нужен scheduled job для refresh.
- ⚠️ `refresh_token` живёт ~1 год — после истечения нужен повторный OAuth.

### Выбранное решение

**Вариант C — VK ID.** Регистрируем новое приложение через сервис VK ID,
реализуем полноценный OAuth 2.0 + PKCE flow с автоматическим refresh через
`refresh_token`. Старые endpoints (`oauth.vk.ru`) помечаем как deprecated,
но оставляем на случай обратной совместимости.

## Архитектурный контекст: где работает OAuth, а где — бот

Это ключевой момент, который влияет на дизайн endpoints и `redirect_uri`.

### Распределение ролей между admin-машиной и продом

| Компонент | Где развёрнут | Что делает в контексте ВК-публикации |
|-----------|---------------|--------------------------------------|
| **`karaoke-app`** (Spring Boot, Kotlin) | **admin-машина `nsa-i9:8898`** | **Бот публикует посты** через `VkAutoPublishService`. Имеет доступ к MinIO (картинки альбомов), MLT, очередям задач. Здесь же лежит `Karaoke.properties` с токенами. |
| **`karaoke-web`** (Spring Boot, Kotlin) | **прод `sm-karaoke.ru:443`** | **Публичный сайт** + **OAuth callback endpoint** (только ради получения токена). Сам НЕ публикует, НЕ имеет доступа к MinIO/MLT. |
| **`webvue3`** (Vue 3 SPA) | **прод `sm-karaoke.ru:7906`** | **Админка** — UI для ручного управления публикациями. Дёргает API `karaoke-app` через прокси. |
| **`karaoke-public`** (Vue 3 SPA) | **прод `sm-karaoke.ru:80`** | **Публичный каталог песен** для посетителей. |

**Бот работает на admin-машине** — это уже зафиксировано в спеках #121 и #138.
Никаких изменений архитектуры не требуется.

### Почему `redirect_uri` указывает на прод, а не на admin-машину

OAuth 2.0 устроен так: после подтверждения прав ВКонтакте перенаправляет
**браузер** пользователя на `redirect_uri`. Этот URL должен быть:

1. **Публичным** — доступным из браузера пользователя (где угодно в мире).
2. **HTTPS** — VK ID не принимает HTTP для production-приложений.

**Admin-машина `nsa-i9`** обычно **не удовлетворяет** этим требованиям:

- ❌ За домашним роутером / NAT (не имеет публичного IP).
- ❌ Динамический IP (меняется при переподключении).
- ❌ Нет валидного TLS-сертификата для внешнего домена.
- ❌ Firewall блокирует входящие соединения.

Поэтому `redirect_uri` указывает на **публичный endpoint на проде**:

```
https://sm-karaoke.ru/api/public/utils/vkIdOAuthCallback
```

**Прод-сервер `karaoke-web`** выступает как «прокси» для OAuth handshake:

1. Принимает `code` от VK ID.
2. Обменивает `code → токены` через `https://id.vk.ru/oauth2/token`
   (использует `client_secret` — секрет не утекает в браузер).
3. Отправляет токены POST-ом на admin-машину:
   `POST http://nsa-i9:8898/api/utils/vkIdSaveTokens`.
4. Возвращает HTML-страницу с подтверждением.

**Admin-машина `karaoke-app`** сохраняет токены в `Karaoke.properties` и
**использует их для публикации** через `VkAutoPublishService`.

### Диаграмма потока (ASCII)

```
┌──────────────┐                              ┌──────────────┐
│  Браузер     │                              │   Admin-     │
│  админа      │                              │   машина     │
│              │                              │  (nsa-i9)    │
└──────┬───────┘                              └──────┬───────┘
       │                                             │
       │  1. GET /api/public/utils/vkIdOAuthUrl     │
       │────────────────────────────────────────►   │
       │     (прод: sm-karaoke.ru)                  │
       │                                             │
       │  2. Возвращает URL для авторизации          │
       │◄────────────────────────────────────────   │
       │                                             │
       │  3. Открывает URL в браузере                │
       │     https://id.vk.ru/authorize?...          │
       │────────────────────────────────────────►   │
       │                                             │
       │  4. VK ID: форма подтверждения прав         │
       │◄────────────────────────────────────────   │
       │                                             │
       │  5. Админ подтверждает права                │
       │────────────────────────────────────────►   │
       │                                             │
       │  6. VK ID редиректит на                     │
       │     https://sm-karaoke.ru/                  │
       │      api/public/utils/vkIdOAuthCallback     │
       │      ?code=XXX&state=YYY                    │
       │     (это прод)                              │
       │                                             │
       │  7. Браузер делает GET на прод              │
       │────────────────────────────────────────►   │
       │                                             │
       │                  ┌──────────────────────────┐│
       │                  │ Прод (karaoke-web)       ││
       │                  │ PublicVkIdAuthController ││
       │                  └────────┬─────────────────┘│
       │                           │                  │
       │                           │ 8. POST          │
       │                           │ https://id.vk.ru/│
       │                           │ oauth2/token     │
       │                           │ (code→tokens)    │
       │                           ▼                  │
       │                  ┌──────────────────────────┐│
       │                  │    VK ID (id.vk.ru)      ││
       │                  └────────┬─────────────────┘│
       │                           │                  │
       │                           │ 9. access_token, │
       │                           │    refresh_token │
       │                           │◄─────────────────│
       │                           │                  │
       │                           │ 10. POST         │
       │                           │ http://nsa-i9:   │
       │                           │  8898/api/utils/ │
       │                           │  vkIdSaveTokens  │
       │                           ▼                  │
       │                  ┌──────────────────────────┐│
       │                  │ Admin (karaoke-app)     ││
       │                  │ ApiController.          ││
       │                  │ vkIdSaveTokens          ││
       │                  └────────┬─────────────────┘│
       │                           │                  │
       │                           │ 11. Сохраняет    │
       │                           │ в Karaoke.       │
       │                           │ properties.      │
       │                           │ vkIdAccessToken  │
       │                           ▼                  │
       │                                             │
       │  12. HTML "✅ авто-сохранено"               │
       │◄────────────────────────────────────────   │
       │                                             │
       │                                             │
       │                          ┌───────────────┐ │
       │                          │  Позже:       │ │
       │                          │  бот публикует│ │
       │                          │  пост в ВК    │ │
       │                          │               │ │
       │                          │ VkAutoPublish │ │
       │                          │ Service       │ │
       │                          │ (admin)       │ │
       │                          │               │ │
       │                          │ VK API:       │ │
       │                          │ photos.*      │ │
       │                          │ wall.post     │ │
       │                          │ video.save    │ │
       │                          └───────────────┘ │
```

### Аналогия с уже работающим кодом

Ровно такая же схема уже работает для **старого** `oauth.vk.ru` flow:

```kotlin
// karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicVkAuthController.kt:39-52
@RestController
class PublicVkAuthController {
    @Value("\${vk.admin-api-url:http://nsa-i9:8898}")
    private var adminApiUrl: String = ""
    // ...
    private fun saveTokenOnAdminMachine(accessToken: String): Pair<Boolean, String> {
        // POST $adminApiUrl/api/utils/vkSaveUserToken
    }
}
```

Спека #151 — это **ровно то же самое**, только:

| Параметр | oauth.vk.ru (старое) | id.vk.ru / VK ID (новое) |
|----------|---------------------|--------------------------|
| Authorization endpoint | `https://oauth.vk.ru/authorize` | `https://id.vk.ru/authorize` |
| Token endpoint | `https://oauth.vk.ru/access_token` | `https://id.vk.ru/oauth2/token` |
| `response_type` | `code` | `code` |
| PKCE | Нет | **Да** (обязательно) |
| `refresh_token` | Нет (или с `offline`) | **Да** (выдаётся всегда) |
| Scheduled refresh | Не нужен | **Нужен** (access_token ~1 час) |
| Token validation | Через `users.get` | Через `users.get` + (опц.) JWT |

### Что если admin-машина недоступна с прода

Прод-сервер пытается отправить токены на admin-машину через
`http://nsa-i9:8898/api/utils/vkIdSaveTokens`. Если admin-машина
недоступна (firewall, сеть упала, и т.п.):

1. `PublicVkIdAuthController.saveTokenOnAdminMachine()` ловит исключение.
2. Возвращает HTML-страницу с **готовой curl-командой** для ручного сохранения.
3. Админ копирует curl и выполняет на admin-машине:
   ```bash
   TOKEN='<access_token_from_html>'
   curl -X POST "http://nsa-i9:8898/api/utils/vkIdSaveTokens" \
        --data-urlencode "accessToken=$TOKEN" \
        --data-urlencode "refreshToken=<refresh_from_html>" \
        --data-urlencode "expiresIn=3600"
   ```

Это безопасный fallback — пользователь не теряет токен.

### Альтернативы (почему НЕ выбрали)

| Альтернатива | Почему нет |
|--------------|-----------|
| **`redirect_uri` = `http://nsa-i9:8898/...`** | VK ID требует HTTPS + публичный хост |
| **Cloudflare Tunnel / ngrok на admin-машине** | Дополнительная инфраструктура, единая точка отказа, надо поддерживать туннель |
| **VK ID Native App** | Другая модель регистрации, нужно отдельное приложение |
| **Callback через `oauth.vk.ru/blank.html`** | Работает только для Standalone Implicit Flow (deprecated) |

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Получение персонального токена через VK ID (Priority: P1)

Администратор запрашивает новый персональный токен через VK ID. После
подтверждения прав в ВКонтакте токен автоматически сохраняется в
`KaraokeProperties.vkUserAccessToken` (через новый ключ `vkIdAccessToken`).
Бот ВКонтакте использует этот токен для загрузки превью-фото в постах.

**Why this priority**: Без валидного токена спека #138 не работает —
превью-фото не загружается, посты выглядят «голо».

**Independent Test**:
1. Выполнить `curl http://sm-karaoke.ru/api/public/utils/vkIdOAuthUrl`.
2. Убедиться, что вернулся `success=true` и валидный `url`.
3. Открыть URL в браузере, подтвердить права.
4. После редиректа убедиться, что HTML-страница показывает
   «✅ авто-сохранено в Karaoke.properties admin-машины».
5. Выполнить `curl http://localhost:8898/api/utils/vkIdTokenStatus`
   (admin-машина).
6. Убедиться, что `hasAccessToken=true`, `hasRefreshToken=true`,
   `expiresAt` в будущем.
7. Запустить AIR-публикацию песни (`POST /api/song/publishToVkNow?id=<id>`).
8. Открыть пост в группе ВКонтакте — убедиться, что есть графическое превью.

**Acceptance Scenarios**:

1. **Given** новый VK ID client_id/secret настроен в `Karaoke.properties`,
   **When** админ открывает `/api/public/utils/vkIdOAuthUrl`,
   **Then** возвращается URL вида
   `https://id.vk.ru/authorize?client_id=<id>&...&code_challenge=<PKCE>`.
2. **Given** URL открыт в браузере, **When** пользователь подтверждает права,
   **Then** VK ID редиректит на наш
   `https://sm-karaoke.ru/api/public/utils/vkIdOAuthCallback?code=XXX`.
3. **Given** callback получен, **When** сервер обменивает code на токены,
   **Then** VK ID возвращает `access_token`, `refresh_token`, `expires_in`,
   и наш сервер сохраняет их в `KaraokeProperties.vkIdAccessToken` /
   `vkIdRefreshToken` / `vkIdAccessTokenExpiresAt`.
4. **Given** токены сохранены, **When** бот публикует пост в ВК,
   **Then** `photos.*` методы успешно работают (нет `error_code=27`).

### User Story 2 - Автоматический refresh access_token (Priority: P1)

После истечения `access_token` (через ~1 час) фоновый job автоматически
обновляет его через `refresh_token`. Администратору не нужно повторно
открывать URL и подтверждать права в ВК. Если `refresh_token` тоже истёк —
job логирует ошибку и помечает `vkIdRefreshNeeded=true` для UI-индикатора.

**Why this priority**: Без автоматического refresh бот перестанет
работать через час после первого получения токена — придётся снова
проходить OAuth flow, что неудобно и опасно (пропущенные публикации).

**Independent Test**:
1. Получить токен через `/api/public/utils/vkIdOAuthUrl` (как в US-1).
2. Проверить `expiresAt` — должен быть через ~1 час.
3. Подождать (или эмулировать) истечение access_token.
4. Убедиться, что `VkIdTokenRefreshScheduler` обновил токен.
5. Выполнить `GET /api/utils/vkIdTokenStatus` — `expiresAt` обновлён.

**Acceptance Scenarios**:

1. **Given** access_token истечёт через < 1 часа,
   **When** `VkIdTokenRefreshScheduler` срабатывает (раз в час),
   **Then** он вызывает `POST https://id.vk.ru/oauth2/token` с
   `grant_type=refresh_token` и сохраняет новые токены.
2. **Given** refresh_token валиден, **When** refresh успешен,
   **Then** `vkIdAccessToken` и `vkIdAccessTokenExpiresAt` обновлены,
   `vkIdRefreshToken` тоже обновлён (VK ID может ротировать refresh_token).
3. **Given** refresh_token истёк, **When** refresh не успешен,
   **Then** `vkIdRefreshNeeded=true` записан в Karaoke.properties,
   `vkIdRefreshLastError` содержит причину, scheduler логирует
   предупреждение.
4. **Given** `vkIdRefreshNeeded=true`, **When** админ открывает UI,
   **Then** отображается баннер «Требуется повторная авторизация VK ID»
   с ссылкой на `/api/public/utils/vkIdOAuthUrl`.

### User Story 3 - Сохранение существующего поведения (Priority: P1)

Все ранее реализованные компоненты автопубликации ВКонтакте продолжают
работать без изменений: `VkAutoPublishService`, `VkApiClient`,
`VkPhotoUploadClient`, `VkPreviewWarmupClient`. Поле `vkUserAccessToken`
продолжает использоваться как источник токена для `photos.*` методов —
теперь оно автоматически читается из `vkIdAccessToken` (через alias).

**Why this priority**: Регрессия в существующем коде = сломанные публикации.
VK ID — это только новый способ получения токена, не замена существующего API.

**Independent Test**:
1. Запустить AIR-публикацию песни (`POST /api/song/publishToVkNow?id=<id>`).
2. Убедиться, что `VkPhotoUploadClient.uploadCover` использует токен
   из `KaraokeProperties.vkUserAccessToken`.
3. Убедиться, что `VkApiClient.userAccessToken()` возвращает значение
   `vkIdAccessToken` (если он заполнен), иначе fallback на старый
   `vkUserAccessToken`.
4. Проверить rate-limit (3 поста/час), retry-логику, прогрев PNG —
   всё должно работать как раньше.

**Acceptance Scenarios**:

1. **Given** `vkIdAccessToken` заполнен валидным токеном,
   **When** `VkApiClient.userAccessToken()` вызывается,
   **Then** возвращается значение `vkIdAccessToken`.
2. **Given** `vkIdAccessToken` пуст, но старый `vkUserAccessToken` заполнен
   (например, в переходный период),
   **When** `VkApiClient.userAccessToken()` вызывается,
   **Then** возвращается значение `vkUserAccessToken` (fallback).
3. **Given** спека #138 уже реализована, **When** новый VK ID flow
   добавляется, **Then** `VkPhotoUploadClient` продолжает работать
   без изменений (читает токен через `VkApiClient`).

### User Story 4 - Backward compatibility для старого OAuth (Priority: P2)

Старые endpoints (`/api/utils/vkOAuthUrl`, `/api/utils/vkOAuthCodeUrl`,
`/api/public/utils/vkOAuthCodeUrl`, `/api/public/utils/vkOAuthCallback`)
продолжают работать в режиме «только чтение» — отвечают 410 Gone с понятным
сообщением «используйте новый VK ID flow». Если кто-то сохранил старый URL
в закладках или скриптах — не получает непонятных ошибок.

**Why this priority**: Не критично, но улучшает UX и предотвращает
путаницу. Старые endpoints сейчас заблокированы VK (Security Error),
так что их удаление безопасно.

**Independent Test**:
1. Выполнить `curl http://sm-karaoke.ru/api/public/utils/vkOAuthCodeUrl`.
2. Убедиться, что возвращается `410 Gone` с сообщением
   «Этот endpoint устарел. Используйте `/api/public/utils/vkIdOAuthUrl`».
3. Выполнить `curl http://localhost:8898/api/utils/vkOAuthUrl`.
4. Убедиться, что возвращается `410 Gone` с тем же сообщением.

**Acceptance Scenarios**:

1. **Given** старый endpoint вызван, **When** сервер его обрабатывает,
   **Then** возвращается HTTP 410 с JSON `{deprecated: true, use: "/api/public/utils/vkIdOAuthUrl"}`.
2. **Given** старый endpoint больше не нужен, **When** admin удаляет его код,
   **Then** старый endpoint перестаёт существовать (HTTP 404).

### Edge Cases

- **VK ID вернул error в callback** (`error=access_denied` или подобный) —
  возвращаем HTML с понятной ошибкой, токен не сохраняется.
- **Обмен code → token не удался** (VK ID вернул `error=invalid_grant` или
  5xx) — возвращаем HTML с retry-инструкцией, токен не сохраняется.
- **`code_verifier` не совпал с `code_challenge`** — VK ID вернёт ошибку,
  наш сервер возвращает HTML с описанием (это не должно происходить при
  правильной реализации PKCE).
- **PKCE state mismatch** (защита от CSRF) — если `state` из callback
  не совпадает с сохранённым в сессии — отклоняем запрос.
- **refresh_token тоже истёк** (`error=invalid_grant`) — `vkIdRefreshNeeded=true`,
  админ должен повторить OAuth flow.
- **VK ID недоступен** (5xx, тайм-аут) при refresh — повторяем через
  backoff, до 3 раз, потом помечаем `vkIdRefreshNeeded=true`.
- **Несколько VK ID приложений** в `Karaoke.properties` (например, для
  dev и prod) — не поддерживается в первой версии, используем одно.
- **Конкурентные refresh** (два scheduler'а пытаются refresh одновременно) —
  защищаемся через `synchronized` или `@SchedulerLock` (на будущее).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Реализовать endpoint `GET /api/public/utils/vkIdOAuthUrl`
  в `karaoke-web`, который генерирует URL авторизации VK ID с параметрами:
  `client_id`, `redirect_uri`, `scope=vkid.personal_info+photos+wall+video+offline`,
  `response_type=code`, `state`, `code_challenge`, `code_challenge_method=S256`.
- **FR-002**: Реализовать endpoint `GET /api/public/utils/vkIdOAuthCallback`
  в `karaoke-web`, который обрабатывает редирект от VK ID: обменивает `code`
  на `access_token` + `refresh_token` через `POST https://id.vk.ru/oauth2/token`
  с `code_verifier` (PKCE), `client_id`, `client_secret`, `redirect_uri`,
  `grant_type=authorization_code`. Сохраняет токены через HTTP POST
  на admin-машину `http://nsa-i9:8898/api/utils/vkIdSaveTokens`.
- **FR-003**: Реализовать endpoint `POST /api/utils/vkIdSaveTokens` в
  `karaoke-app` (admin), который принимает `access_token`, `refresh_token`,
  `expires_in`, `id_token?` и сохраняет их в `KaraokeProperties`:
  `vkIdAccessToken`, `vkIdRefreshToken`, `vkIdAccessTokenExpiresAt`,
  `vkIdIdToken`. Перед сохранением проверяет валидность `access_token`
  через `users.get` (как в существующем `vkSaveUserToken`).
- **FR-004**: Реализовать scheduled job `VkIdTokenRefreshScheduler` в
  `karaoke-app`, который раз в час проверяет `vkIdAccessTokenExpiresAt`
  и обновляет токен через `refresh_token` если до истечения осталось
  < 30 минут. Использует `@Scheduled(cron = "0 0 * * * *")` (каждый час).
- **FR-005**: Реализовать метод `refreshVkIdAccessToken()` в `VkApiClient.kt`
  который вызывает `POST https://id.vk.ru/oauth2/token` с
  `grant_type=refresh_token`, `refresh_token`, `client_id`, `client_secret`,
  и возвращает новые `access_token`, `refresh_token`, `expires_in`.
  При ошибке `invalid_grant` — выбрасывает исключение `VkIdRefreshFailedException`.
- **FR-006**: Обновить `KaraokeProperties.kt` — добавить 6 новых ключей:
  `vkIdClientId` (Long), `vkIdClientSecret` (String), `vkIdRedirectUri`
  (String), `vkIdAccessToken` (String), `vkIdRefreshToken` (String),
  `vkIdAccessTokenExpiresAt` (String, ISO datetime),
  `vkIdIdToken` (String), `vkIdRefreshNeeded` (Boolean),
  `vkIdRefreshLastError` (String).
- **FR-007**: Обновить `VkApiClient.userAccessToken()` — если `vkIdAccessToken`
  заполнен, возвращать его. Иначе fallback на старый `vkUserAccessToken`.
  Логика: `val idToken = KaraokeProperties.getString("vkIdAccessToken"); return idToken.ifBlank { KaraokeProperties.getString("vkUserAccessToken") }`.
- **FR-008**: Endpoint `GET /api/utils/vkIdTokenStatus` (admin) возвращает
  JSON: `{hasClientId, hasClientSecret, hasAccessToken, hasRefreshToken,
  expiresAt, refreshNeeded, lastError}`. Используется админом для проверки
  состояния токена.
- **FR-009**: Endpoint `POST /api/utils/vkIdRefreshNow` (admin) принудительно
  вызывает `refreshVkIdAccessToken()` и сохраняет результат. Возвращает
  JSON `{success, expiresAt, error?}`.
- **FR-010**: Старые endpoints (`/api/utils/vkOAuthUrl`,
  `/api/utils/vkOAuthCodeUrl`, `/api/public/utils/vkOAuthCodeUrl`,
  `/api/public/utils/vkOAuthCallback`) пометить как deprecated: они
  возвращают HTTP 410 Gone с JSON `{deprecated: true, use: "/api/public/utils/vkIdOAuthUrl"}`.
  Не удалять (для обратной совместимости и истории).
- **FR-011**: Документация `docs/features/vk-news-auto-publish.md`
  должна быть обновлена: добавлена секция «Получение токена через VK ID»,
  удалена (или перенесена в раздел «Deprecated») секция про OAuth 2.0.
- **FR-012**: Per-feature документ `docs/features/vk-id-auth.md` (новый)
  описывает VK ID flow, refresh-логику, troubleshooting. Добавляется в
  таблицу `docs/features/README.md` (11 → 12 фич).
- **FR-013**: В KDoc корневого класса `VkIdAuthController` (или
  `PublicVkAuthController`) добавить `@see docs/features/vk-id-auth.md`.
- **FR-014**: Сценарий quickstart (`docs/features/vk-id-auth.md`) описывает
  шаги для админа: (1) зарегистрировать приложение на https://id.vk.com/about/business/go/,
  (2) получить client_id/secret, (3) настроить redirect_uri, (4) задать
  ключи в Karaoke.properties, (5) открыть `/api/public/utils/vkIdOAuthUrl`.
- **FR-015**: Тесты (если будут добавлены) — unit-тест на PKCE
  (`generateCodeVerifier`, `generateCodeChallenge`) и на refresh flow
  (mock VK ID endpoint).

### Non-Functional Requirements

- **NFR-001**: Refresh token job не должен блокировать другие задачи —
  выполняется в отдельном потоке, таймаут 30 секунд.
- **NFR-002**: PKCE `code_verifier` генерируется как 43-128 символов
  из `[A-Z][a-z][0-9]-._~` (RFC 7636). `code_challenge` = base64url(SHA256(code_verifier)).
- **NFR-003**: PKCE `state` сохраняется в HTTP-сессии (Spring Session)
  и проверяется в callback. Если state не совпадает — отклоняем запрос.
- **NFR-004**: Все секретные параметры (`client_secret`, `access_token`,
  `refresh_token`) НЕ логируются — только факт операции и маскированный
  токен (первые 8 + `...` + последние 4 символа).
- **NFR-005**: `redirect_uri` в настройках VK ID должен **точно** совпадать
  с тем, что передаётся в `/authorize` и `/token` (включая https/http,
  домен, путь, query-параметры). Несоответствие → VK вернёт ошибку.
- **NFR-006**: Endpoint `/api/public/utils/vkIdOAuthCallback` доступен
  только на проде (`sm-karaoke.ru`) по HTTPS — VK ID не принимает HTTP
  redirect_uri для прода.

### Key Entities *(include if feature involves data)*

- **VK ID Application** — приложение, зарегистрированное в VK ID.
  Идентификатор `client_id` (число), секрет `client_secret` (строка).
  Хранится в `KaraokeProperties.vkIdClientId` / `vkIdClientSecret`.
- **VK ID Access Token** — короткоживущий токен (~1 час) для вызова
  VK API. Хранится в `vkIdAccessToken`. Используется в `access_token=...`
  параметре VK API запросов.
- **VK ID Refresh Token** — долгоживущий токен (~1 год) для обновления
  access_token. Хранится в `vkIdRefreshToken`. Используется в
  `grant_type=refresh_token` запросах.
- **VK ID ID Token** — JWT-токен с информацией о пользователе
  (OIDC standard claim). Хранится в `vkIdIdToken`. Используется
  для верификации пользователя (опционально).
- **PKCE Code Verifier** — случайная строка 43-128 символов, генерируется
  сервером перед `/authorize`. Хранится в HTTP-сессии (Spring Session)
  или в KaraokeProperties как transient-значение.
- **PKCE Code Challenge** — `base64url(SHA256(code_verifier))`. Передаётся
  в `/authorize`, проверяется при обмене code → token.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: В 100% сценариев получения токена через VK ID — токен
  успешно сохраняется в `KaraokeProperties.vkIdAccessToken`.
- **SC-002**: В 100% сценариев AIR-публикации после получения токена —
  пост в ВКонтакте содержит графическое превью (через `photos.*` методы).
- **SC-003**: Автоматический refresh работает: через 1 час после получения
  access_token — токен обновляется, `expiresAt` смещается на ~1 час вперёд.
- **SC-004**: Если refresh_token истёк — `vkIdRefreshNeeded=true`,
  `vkIdRefreshLastError` содержит описание, в логах — WARNING с полным контекстом.
- **SC-005**: Старые endpoints (`oauth.vk.ru` flow) возвращают HTTP 410
  с понятным сообщением — нет непонятных 404/500.
- **SC-006**: Время получения токена (от открытия URL до сохранения в
  Karaoke.properties) — < 10 секунд.
- **SC-007**: Per-feature документ `docs/features/vk-id-auth.md` создан,
  quickstart позволяет админу получить токен за 5 минут без чтения кода.
- **SC-008**: Spec #138 (загрузка превью-фото) разблокирована — спека
  может быть смержена и выпущена в прод после реализации #151.

## Assumptions

- Администратор имеет аккаунт ВКонтакте с правами администратора группы
  `svoemestokaraoke` (для подтверждения прав в VK ID).
- Администратор готов зарегистрировать новое приложение на
  https://id.vk.com/about/business/go/ и предоставить `client_id` /
  `client_secret` / `redirect_uri`.
- VK ID выдаёт токены в формате, совместимом с VK API (включая `photos.*`).
  Это документировано поведение VK ID для приложений с scopes `photos`,
  `wall`, `video`.
- `redirect_uri` будет `https://sm-karaoke.ru/api/public/utils/vkIdOAuthCallback`
  — этот домен уже есть и обслуживается nginx на проде.
- Admin-машина `nsa-i9` доступна с прода по HTTP (для автосохранения токена
  после callback) — это уже работает для старого OAuth flow.
- VK ID `access_token` живёт ~3600 секунд (1 час) — это документировано
  поведение VK ID.
- VK ID `refresh_token` живёт ~31536000 секунд (1 год) — это документировано.
  После истечения требуется повторный OAuth flow.

## Границы первой версии

**Входит**:
- Регистрация приложения в VK ID (пользователь делает вручную).
- Реализация VK ID OAuth flow с PKCE в `karaoke-web` (`PublicVkAuthController`).
- Сохранение токенов в `KaraokeProperties` через `karaoke-app` endpoint.
- Автоматический refresh по расписанию (`VkIdTokenRefreshScheduler`).
- Backward compatibility: старые endpoints возвращают 410 Gone.
- Per-feature документ `docs/features/vk-id-auth.md` с quickstart.

**Не входит** (backlog):
- Поддержка нескольких VK ID приложений (например, для dev и prod отдельно).
- Автоматическое определение, когда нужен refresh (например, по ответу
  VK API `error_code=5`). Сейчас — по расписанию.
- Вращение `client_secret` (если VK потребует — отдельная задача).
- UI-индикатор «требуется повторная авторизация» в `webvue3`. Сейчас —
  через `vkIdRefreshNeeded=true` в Karaoke.properties (читается через API).
- Отзыв старых токенов через VK API (если VK потребует — отдельная задача).

## Clarifications

### Session 2026-08-05 (initial, from user message)

- **Q1 (как получить токен, если oauth.vk.ru возвращает Security Error)**
  — **A. Переход на VK ID**: новое приложение через `id.vk.ru` —
  рекомендуемый современный способ авторизации VK, выдаёт refresh_token
  для долгосрочного доступа.
- **Q2 (что делать со старыми endpoints oauth.vk.ru)** — **A. HTTP 410**:
  старые endpoints возвращают 410 Gone с понятным сообщением «используйте
  новый VK ID flow». Код не удаляется (для истории и обратной совместимости).
- **Q3 (где хранить токены)** — **A. В Karaoke.properties**: как и текущий
  `vkUserAccessToken`. Новые ключи: `vkIdAccessToken`, `vkIdRefreshToken`,
  `vkIdAccessTokenExpiresAt` и др.
- **Q4 (как обновлять токен)** — **A. По расписанию**: `VkIdTokenRefreshScheduler`
  раз в час проверяет `expiresAt` и обновляет через `refresh_token`.

## Связанные документы

- [specs/138-vk-photo-preview-attachment/spec.md](../138-vk-photo-preview-attachment/spec.md) —
  спека, которую разблокирует #151 (загрузка превью-фото через `photos.*`).
- [specs/121-vk-news-auto-publish/spec.md](../121-vk-news-auto-publish/spec.md) —
  основная спецификация автопубликации ВКонтакте.
- [docs/features/vk-news-auto-publish.md](../../docs/features/vk-news-auto-publish.md) —
  общая документация по VK-публикации (обновить в том же PR: добавить
  секцию «Получение токена через VK ID», переместить старый OAuth в «Deprecated»).
- [docs/features/README.md](../../docs/features/README.md) —
  таблица ключевых подсистем (обновить, добавить `vk-id-auth.md`).
- [VK ID документация](https://id.vk.com/about/business/go/docs/ru/vkid/latest/vk-id/connection/main.html) —
  официальная документация VK ID (требует JS, изучена через альтернативные источники).
- [OAuth 2.0 RFC 6749](https://datatracker.ietf.org/doc/html/rfc6749) —
  стандарт OAuth 2.0 (используется VK ID).
- [PKCE RFC 7636](https://datatracker.ietf.org/doc/html/rfc7636) —
  стандарт PKCE (используется для защиты Authorization Code Flow).