# Implementation Plan: Миграция на VK ID

**Feature Branch**: `151-vk-id-personal-token`
**Spec**: [spec.md](./spec.md)

## Архитектурный обзор

Реализация разбита на 3 слоя:

1. **`karaoke-web`** (прод, `sm-karaoke.ru`) — публичные endpoints
   для OAuth flow (`/api/public/utils/vkIdOAuthUrl`,
   `/api/public/utils/vkIdOAuthCallback`). Принимает редирект от VK ID,
   обменивает `code → tokens`, отправляет токены на admin-машину.

2. **`karaoke-app`** (admin, `nsa-i9:8898`) — приватные endpoints
   для сохранения токенов (`/api/utils/vkIdSaveTokens`), проверки статуса
   (`/api/utils/vkIdTokenStatus`), принудительного refresh
   (`/api/utils/vkIdRefreshNow`). Scheduled job для автообновления
   (`VkIdTokenRefreshScheduler`).

3. **`VkApiClient`** (общий модуль) — обновлённый метод `userAccessToken()`,
  новый метод `refreshVkIdAccessToken()`. Используется существующим
  `VkPhotoUploadClient` без изменений.

## Технологический стек

- **VK ID OAuth 2.0 / OIDC** — стандартный протокол, поддерживается VK.
- **PKCE (RFC 7636)** — обязательно для public clients (генерация
  `code_verifier` + `code_challenge = base64url(SHA256(code_verifier))`).
- **Java 17 `SecureRandom`** — для генерации криптографически стойких
  `code_verifier`, `state`, `nonce`.
- **Spring `@Scheduled`** — для `VkIdTokenRefreshScheduler` (cron `0 0 * * * *`).
- **Spring Session (опционально)** — для хранения `state` и `code_verifier`
  между запросами `/authorize` → `/callback`. Альтернатива — KaraokeProperties
  с TTL (transient-значение).

## Структура файлов

### Новые файлы

```
karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/
└── controllers/
    └── PublicVkIdAuthController.kt        # GET /api/public/utils/vkIdOAuthUrl, /vkIdOAuthCallback

karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/utils/
└── VkIdPkceUtils.kt                       # generateCodeVerifier, generateCodeChallenge

karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
├── services/
│   └── VkIdTokenRefreshScheduler.kt       # @Scheduled refresh
└── controllers/ApiController.kt           # Добавить 3 endpoint'a: vkIdSaveTokens, vkIdTokenStatus, vkIdRefreshNow

docs/features/
└── vk-id-auth.md                          # Per-feature документация
```

### Изменённые файлы

```
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
├── services/
│   └── VkApiClient.kt                     # userAccessToken() + refreshVkIdAccessToken()
├── KaraokeProperties.kt                   # 9 новых ключей
└── controllers/ApiController.kt           # Deprecated старые endpoints (410)

karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/
├── controllers/
│   └── PublicVkAuthController.kt          # Deprecated старые endpoints (410)
└── application.yml                        # Добавить vk.id.* настройки

docs/
├── features/
│   ├── vk-news-auto-publish.md            # Обновить: добавить VK ID секцию
│   └── README.md                          # Добавить vk-id-auth.md в таблицу
└── architecture-notes.md                  # Pass 35 (добавить запись о PR)
```

## Пошаговый план реализации

### Фаза 0: Подготовка (пользователь)

1. **Регистрация VK ID приложения** (https://id.vk.com/about/business/go/)
   - Создать приложение (тип Web).
   - Получить `client_id` (числовой, например 54704235).
   - Получить `client_secret` (секрет, показать админу 1 раз).
   - Указать `redirect_uri = https://sm-karaoke.ru/api/public/utils/vkIdOAuthCallback`.
   - Включить scopes: `vkid.personal_info`, `photos`, `wall`, `video`, `offline`.

2. **Настройка Karaoke.properties** (admin-машина `nsa-i9`)
   ```properties
   vkIdClientId=54704235
   vkIdClientSecret=<полученный_секрет>
   vkIdRedirectUri=https://sm-karaoke.ru/api/public/utils/vkIdOAuthCallback
   ```

3. **Настройка application.yml** (прод `karaoke-web`)
   ```yaml
   vk:
     id:
       client-id: 54704235
       client-secret: ${VK_ID_CLIENT_SECRET}
       redirect-uri: https://sm-karaoke.ru/api/public/utils/vkIdOAuthCallback
       admin-api-url: http://nsa-i9:8898
   ```

### Фаза 1: Реализация endpoints в `karaoke-web` (новый код)

4. **Создать `VkIdPkceUtils.kt`** — утилиты для PKCE:
   - `generateCodeVerifier(): String` — 64 случайных символа из `[A-Z][a-z][0-9]-._~`.
   - `generateCodeChallenge(verifier: String): String` — `base64url(SHA256(verifier))`.
   - `generateState(): String` — 32 случайных символа для CSRF-защиты.

5. **Создать `PublicVkIdAuthController.kt`** — два endpoint'а:
   - `GET /api/public/utils/vkIdOAuthUrl`:
     - Проверяет, что `vk.id.client-id`, `vk.id.client-secret`, `vk.id.redirect-uri` настроены.
     - Генерирует `code_verifier` и `code_challenge`.
     - Генерирует `state`.
     - Сохраняет `code_verifier` + `state` в HTTP-сессию (или transient-хранилище).
     - Возвращает URL: `https://id.vk.ru/authorize?client_id=...&redirect_uri=...&scope=...&response_type=code&state=...&code_challenge=...&code_challenge_method=S256`.
   - `GET /api/public/utils/vkIdOAuthCallback`:
     - Принимает `code`, `state` (от VK ID).
     - Проверяет, что `state` совпадает с сохранённым (CSRF).
     - Получает `code_verifier` из сессии.
     - Обменивает `code → tokens` через `POST https://id.vk.ru/oauth2/token`:
       ```
       grant_type=authorization_code
       &client_id=...
       &client_secret=...
       &redirect_uri=...
       &code=...
       &code_verifier=...
       ```
     - Получает `access_token`, `refresh_token`, `expires_in`, `id_token?`.
     - Вычисляет `expiresAt = now + expires_in`.
     - Отправляет POST на admin-машину `http://nsa-i9:8898/api/utils/vkIdSaveTokens`:
       ```
       accessToken=...
       &refreshToken=...
       &expiresIn=...
       &idToken=...
       ```
     - Возвращает HTML-страницу с подтверждением (аналогично `PublicVkAuthController`).

### Фаза 2: Реализация endpoints в `karaoke-app` (новый код)

6. **Добавить 3 endpoint'а в `ApiController.kt`** (admin-машина):
   - `POST /api/utils/vkIdSaveTokens`:
     - Принимает `accessToken`, `refreshToken`, `expiresIn`, `idToken?`.
     - Проверяет валидность `accessToken` через `users.get` (как в существующем
       `vkSaveUserToken`).
     - Сохраняет токены в `KaraokeProperties`:
       - `vkIdAccessToken = accessToken`
       - `vkIdRefreshToken = refreshToken`
       - `vkIdAccessTokenExpiresAt = now + expiresIn` (ISO datetime)
       - `vkIdIdToken = idToken` (если есть)
       - `vkIdRefreshNeeded = false`
       - `vkIdRefreshLastError = ""`
     - Возвращает JSON `{success: true, expiresAt, userId, firstName, lastName}`.
   - `GET /api/utils/vkIdTokenStatus`:
     - Читает `vkIdClientId`, `vkIdClientSecret`, `vkIdAccessToken`,
       `vkIdRefreshToken`, `vkIdAccessTokenExpiresAt`, `vkIdRefreshNeeded`,
       `vkIdRefreshLastError`.
     - Возвращает JSON `{hasClientId, hasClientSecret, hasAccessToken,
       hasRefreshToken, expiresAt, refreshNeeded, lastError}`.
   - `POST /api/utils/vkIdRefreshNow`:
     - Вызывает `VkApiClient.refreshVkIdAccessToken()`.
     - Сохраняет результат в `KaraokeProperties`.
     - Возвращает JSON `{success, expiresAt, error?}`.

7. **Обновить `VkApiClient.kt`** (admin-машина):
   - Изменить `userAccessToken()`:
     ```kotlin
     private fun userAccessToken(): String {
         val idToken = KaraokeProperties.getString("vkIdAccessToken")
         return idToken.ifBlank { KaraokeProperties.getString("vkUserAccessToken") }
     }
     ```
   - Добавить `refreshVkIdAccessToken()`:
     ```kotlin
     fun refreshVkIdAccessToken(): VkIdTokenRefreshResult {
         val clientId = KaraokeProperties.getLong("vkIdClientId")
         val clientSecret = KaraokeProperties.getString("vkIdClientSecret")
         val refreshToken = KaraokeProperties.getString("vkIdRefreshToken")
         if (clientId <= 0 || clientSecret.isBlank() || refreshToken.isBlank()) {
             throw VkIdRefreshFailedException("vkIdClientId/vkIdClientSecret/vkIdRefreshToken not configured")
         }
         // POST https://id.vk.ru/oauth2/token
         // ... парсинг response
         // Если error=invalid_grant → throw VkIdRefreshFailedException
     }
     ```

8. **Создать `VkIdTokenRefreshScheduler.kt`** (admin-машина):
   - `@Scheduled(cron = "0 0 * * * *")` — каждый час.
   - В начале: проверяет, что `vkIdAccessTokenExpiresAt` заполнено.
   - Если до `expiresAt` осталось < 30 минут — вызывает `refreshVkIdAccessToken()`.
   - При успехе — сохраняет новые токены.
   - При ошибке — логирует WARNING, помечает `vkIdRefreshNeeded=true`.

### Фаза 3: Обновление `KaraokeProperties.kt`

9. **Добавить 9 новых ключей** в `KaraokeProperties.kt`:
   ```kotlin
   KaraokeProperty(key = "vkIdClientId", defaultValue = 0L, description = "..."),
   KaraokeProperty(key = "vkIdClientSecret", defaultValue = "", description = "..."),
   KaraokeProperty(key = "vkIdRedirectUri", defaultValue = "", description = "..."),
   KaraokeProperty(key = "vkIdAccessToken", defaultValue = "", description = "..."),
   KaraokeProperty(key = "vkIdRefreshToken", defaultValue = "", description = "..."),
   KaraokeProperty(key = "vkIdAccessTokenExpiresAt", defaultValue = "", description = "..."),
   KaraokeProperty(key = "vkIdIdToken", defaultValue = "", description = "..."),
   KaraokeProperty(key = "vkIdRefreshNeeded", defaultValue = false, description = "..."),
   KaraokeProperty(key = "vkIdRefreshLastError", defaultValue = "", description = "..."),
   ```

### Фаза 4: Deprecation старых endpoints

10. **Обновить `PublicVkAuthController.kt` (`karaoke-web`)**:
    - `getVkOAuthCodeUrl()` → возвращает HTTP 410 Gone + JSON.
    - `vkOAuthCallback()` → возвращает HTTP 410 Gone + JSON.

11. **Обновить `ApiController.kt` (`karaoke-app`)**:
    - `getVkOAuthUrl()` → возвращает HTTP 410 Gone + JSON.
    - `vkOAuthCallback()` (admin) → возвращает HTTP 410 Gone + JSON.
    - `getVkOAuthCodeUrl()` → возвращает HTTP 410 Gone + JSON.

### Фаза 5: Документация

12. **Создать `docs/features/vk-id-auth.md`** — per-feature документация:
    - `## Что делает` — описание VK ID flow + refresh.
    - `## Зачем` — почему мигрируем с oauth.vk.ru.
    - `## Как работает` — диаграмма + шаги.
    - `## Инварианты / правила` — список инвариантов (FR-001..FR-015).
    - `## Известные ловушки` — типичные ошибки PKCE, state mismatch, refresh_token expiry.
    - `## Ссылки` — на spec.md, на VK ID документацию.

13. **Обновить `docs/features/vk-news-auto-publish.md`**:
    - Добавить секцию «Получение токена через VK ID» с quickstart-ссылкой.
    - Перенести секцию «User-token через Implicit Flow» в раздел «Deprecated».

14. **Обновить `docs/features/README.md`**:
    - Добавить строку `vk-id-auth.md` в таблицу ключевых подсистем.

15. **Обновить `docs/architecture-notes.md`** — добавить Pass 35 (запись о PR #151).

### Фаза 6: Тестирование

16. **Локальное тестирование** (admin-машина `nsa-i9`):
    - Собрать `karaoke-web`: `./gradlew karaoke-web:bootJar`.
    - Собрать `karaoke-app`: `./gradlew karaoke-app:bootJar`.
    - Перезапустить оба контейнера: `bash do.sh restart_web && bash do.sh restart_app`.
    - Открыть `http://localhost:8897/api/public/utils/vkIdOAuthUrl` — проверить URL.
    - Открыть URL в браузере — подтвердить права.
    - Проверить `http://localhost:8898/api/utils/vkIdTokenStatus` — токен сохранён.
    - Запустить публикацию: `POST /api/song/publishToVkNow?id=<id>` — превью появилось.

17. **Тестирование refresh** (admin-машина):
    - Эмулировать истечение токена (записать `vkIdAccessTokenExpiresAt` в прошлое).
    - Подождать следующего срабатывания scheduler (или вызвать `POST /api/utils/vkIdRefreshNow`).
    - Проверить, что токен обновлён.

18. **Тестирование в проде** (sm-karaoke.ru):
    - Дождаться деплоя (`deploy_web.sh`).
    - Проверить `https://sm-karaoke.ru/api/public/utils/vkIdOAuthUrl` — возвращает URL.
    - Получить токен через прод-URL.
    - Запустить публикацию — убедиться, что превью работает в проде.

### Фаза 7: Деплой

19. **Сборка и деплой**:
    ```bash
    cd /home/nsa/Karaoke/deploy
    bash do.sh build              # gradle + docker images
    bash do.sh push               # push to Docker Hub
    ssh root@188.119.64.111 "cd Karaoke/deploy && bash do.sh pull"
    ssh root@188.119.64.111 "cd Karaoke/deploy && bash do.sh restart_web && bash do.sh restart_app"
    ```

20. **Проверка после деплоя** (sm-karaoke.ru):
    - `curl https://sm-karaoke.ru/api/public/utils/vkIdOAuthUrl` — возвращает URL.
    - Получить токен через UI.
    - Проверить `vkIdTokenStatus` через admin API.
    - Запустить публикацию — превью работает.

## Порядок выполнения (для разработчика)

1. Фаза 0 (пользователь, вручную) → **блокирует** все последующие фазы.
2. Фаза 1 → можно делать параллельно с Фазой 2.
3. Фаза 2 → можно делать параллельно с Фазой 1.
4. Фаза 3 → тривиально, делается в любой момент.
5. Фаза 4 → тривиально, делается в конце (после того, как новый flow заработает).
6. Фаза 5 → параллельно с разработкой.
7. Фаза 6 → после Фаз 1-5.
8. Фаза 7 → после успешного тестирования (Фаза 6).

## Риски и митигация

| Риск | Вероятность | Митигация |
|------|-------------|-----------|
| VK ID приложение не одобрят | Низкая (для Web-приложения обычно одобряют автоматически) | Использовать Standalone-приложение (но тогда нужен другой redirect_uri). Или переключиться на мобильное приложение. |
| `redirect_uri` не совпадает | Средняя | Тщательно проверить, что URL **точно** совпадает (https, домен, путь, query-параметры). Использовать copy-paste. |
| `refresh_token` тоже истечёт | Низкая (~1 год) | UI-индикатор `vkIdRefreshNeeded=true` + логирование. |
| VK ID недоступен при refresh | Средняя | Retry с backoff (3 попытки: 30с, 2мин, 5мин). Помечать `vkIdRefreshNeeded=true` после исчерпания. |
| PKCE state mismatch | Очень низкая | Логировать WARNING. Не сохранять токен. Возвращать HTML с ошибкой. |
| Конкурентные refresh | Низкая (scheduler раз в час) | Добавить `synchronized(lock)` для защиты (как в `VkAutoPublishService.songLocks`). |
| VK ID не принимает `code_verifier` | Очень низкая | Проверить формат (43-128 символов, `[A-Z][a-z][0-9]-._~`). Проверить, что `code_challenge_method=S256`. |

## Открытые вопросы

1. **Где хранить PKCE `code_verifier` между `/authorize` и `/callback`?**
   - Вариант A: HTTP-сессия (Spring Session) — требует distributed session.
   - Вариант B: KaraokeProperties с TTL — проще, но transient.
   - **Выбор**: Вариант B (transient KaraokeProperty `vkIdPendingCodeVerifier`,
     `vkIdPendingState` с TTL 10 минут, очищается в callback).

2. **Что делать, если VK ID `access_token` имеет scope `offline`?**
   - В OAuth 2.0 scope `offline` означает выдачу refresh_token.
   - В VK ID scope `offline` может быть не нужен (refresh выдаётся по умолчанию).
   - **Выбор**: включить `offline` для совместимости с VK API (долгоживущий access_token).
     Но ожидать, что основной механизм — `refresh_token`.

3. **Использовать ли `id_token` (OIDC) для верификации пользователя?**
   - VK ID выдаёт `id_token` (JWT) с информацией о пользователе.
   - Можно использовать для подтверждения, что токен принадлежит админу группы.
   - **Выбор**: сохранять `id_token` в `vkIdIdToken`, но не валидировать подпись
     (это дополнительная задача, не входит в первую версию).

## Связанные спеки и документы

- [spec.md](./spec.md) — основная спецификация.
- [tasks.md](./tasks.md) — задачи (todo-list).
- [research.md](./research.md) — исследования VK ID.
- [data-model.md](./data-model.md) — модель данных.
- [quickstart.md](./quickstart.md) — инструкция для админа.
- [specs/138-vk-photo-preview-attachment/spec.md](../138-vk-photo-preview-attachment/spec.md) — спека, которую разблокирует #151.