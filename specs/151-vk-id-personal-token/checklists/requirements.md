# Requirements Checklist: Миграция на VK ID

> Чек-лист соответствия спеки требованиям. Используется при code review
> и финальной проверке перед merge.

## Спецификация (spec.md)

- [ ] Все 5 разделов спеки заполнены (`Что делает`, `Зачем`, `Как работает`,
      `Инварианты / правила`, `Известные ловушки`, `Ссылки`).
- [ ] User Scenarios описаны с приоритетами (P1/P2).
- [ ] Acceptance Scenarios для каждого User Story.
- [ ] Edge Cases перечислены.
- [ ] Functional Requirements (FR-001..FR-015) пронумерованы и описаны.
- [ ] Non-Functional Requirements (NFR-001..NFR-006) пронумерованы и описаны.
- [ ] Key Entities перечислены.
- [ ] Success Criteria (SC-001..SC-008) измеримы.
- [ ] Assumptions явно перечислены.
- [ ] Границы первой версии (`Входит` / `Не входит`) определены.
- [ ] Clarifications section заполнена.
- [ ] Связанные документы и спеки указаны.

## Реализация кода

### FR-001: `GET /api/public/utils/vkIdOAuthUrl`

- [ ] Endpoint существует в `PublicVkIdAuthController.kt`.
- [ ] Возвращает JSON `{success, url, instructions}`.
- [ ] URL содержит `client_id`, `redirect_uri`, `scope`, `response_type=code`,
      `state`, `code_challenge`, `code_challenge_method=S256`.
- [ ] Если настройки (`client-id`, `client-secret`, `redirect-uri`) пусты —
      возвращает `{success: false, error}`.
- [ ] KDoc с описанием endpoint'а и ссылкой на spec.md.

### FR-002: `GET /api/public/utils/vkIdOAuthCallback`

- [ ] Endpoint существует в `PublicVkIdAuthController.kt`.
- [ ] Принимает `code`, `state`, `error?` через `@RequestParam`.
- [ ] Проверяет `state` (CSRF) — должно совпадать с `vkIdPendingState`.
- [ ] Получает `code_verifier` из `vkIdPendingCodeVerifier`.
- [ ] Отправляет POST `https://oauth.vk.ru/access_token` с
      `grant_type=authorization_code`, `client_id`, `client_secret`,
      `redirect_uri`, `code`, `code_verifier`.
- [ ] Парсит response: `access_token`, `refresh_token`, `expires_in`, `id_token?`.
- [ ] Вычисляет `expiresAt = now + expires_in`.
- [ ] Отправляет HTTP POST на admin-машину
      `http://nsa-i9:8898/api/utils/vkIdSaveTokens` с параметрами
      `accessToken`, `refreshToken`, `expiresIn`, `idToken`.
- [ ] Возвращает HTML-страницу с подтверждением (по образцу `PublicVkAuthController`).
- [ ] Очищает transient-поля `vkIdPending*` после успешного callback.
- [ ] KDoc с описанием endpoint'а и ссылкой на spec.md.

### FR-003: `POST /api/utils/vkIdSaveTokens`

- [ ] Endpoint существует в `ApiController.kt` (admin).
- [ ] Принимает `accessToken`, `refreshToken`, `expiresIn`, `idToken?`
      через `@RequestParam`.
- [ ] Проверяет `accessToken` через `users.get` (по образцу `vkSaveUserToken`).
- [ ] Сохраняет токены в `KaraokeProperties`:
      - [ ] `vkIdAccessToken = accessToken`.
      - [ ] `vkIdRefreshToken = refreshToken`.
      - [ ] `vkIdAccessTokenExpiresAt = now + expiresIn` (ISO datetime).
      - [ ] `vkIdIdToken = idToken` (если есть).
      - [ ] `vkIdRefreshNeeded = false`.
      - [ ] `vkIdRefreshLastError = ""`.
- [ ] Возвращает JSON `{success, userId, firstName, lastName, expiresAt, message}`.
- [ ] KDoc с описанием endpoint'а и ссылкой на spec.md.

### FR-004: `VkIdTokenRefreshScheduler`

- [ ] Класс `VkIdTokenRefreshScheduler` существует.
- [ ] Аннотация `@Scheduled(cron = "0 0 * * * *")` (каждый час).
- [ ] Метод `refreshIfNeeded()`:
      - [ ] Читает `vkIdAccessTokenExpiresAt`.
      - [ ] Если пустое — выходит (токен не получен).
      - [ ] Парсит `expiresAt` как ISO datetime.
      - [ ] Если до `expiresAt` осталось < 30 минут — вызывает
            `refreshVkIdAccessToken()`.
      - [ ] При успехе — сохраняет новые токены + логирует INFO.
      - [ ] При ошибке — устанавливает `vkIdRefreshNeeded=true`,
            `vkIdRefreshLastError`, логирует WARNING.
- [ ] KDoc с описанием scheduler'а и ссылкой на spec.md.

### FR-005: `VkApiClient.refreshVkIdAccessToken()`

- [ ] Метод существует в `VkApiClient.kt`.
- [ ] Читает `vkIdClientId`, `vkIdClientSecret`, `vkIdRefreshToken`.
- [ ] Проверяет, что все 3 настройки заполнены.
- [ ] Отправляет POST `https://oauth.vk.ru/access_token` с
      `grant_type=refresh_token`, `client_id`, `client_secret`, `refresh_token`.
- [ ] Парсит response: `access_token`, `refresh_token`, `expires_in`, `id_token?`.
- [ ] При `error=invalid_grant` — выбрасывает `VkIdRefreshFailedException`.
- [ ] Возвращает `VkIdTokenRefreshResult`.
- [ ] KDoc с описанием метода и ссылкой на RFC 6749 (refresh flow).

### FR-006: `KaraokeProperties` новые ключи

- [ ] `vkIdClientId` (Long, default 0).
- [ ] `vkIdClientSecret` (String, default "").
- [ ] `vkIdRedirectUri` (String, default "").
- [ ] `vkIdAccessToken` (String, default "").
- [ ] `vkIdRefreshToken` (String, default "").
- [ ] `vkIdAccessTokenExpiresAt` (String, default "").
- [ ] `vkIdIdToken` (String, default "").
- [ ] `vkIdRefreshNeeded` (Boolean, default false).
- [ ] `vkIdRefreshLastError` (String, default "").
- [ ] Transient: `vkIdPendingCodeVerifier`, `vkIdPendingState`, `vkIdPendingAt`.

### FR-007: `VkApiClient.userAccessToken()` обновлён

- [ ] Метод возвращает `vkIdAccessToken.ifBlank { vkUserAccessToken }`.
- [ ] KDoc обновлён с описанием fallback.

### FR-008: `GET /api/utils/vkIdTokenStatus`

- [ ] Endpoint существует в `ApiController.kt` (admin).
- [ ] Читает 7 ключей из KaraokeProperties.
- [ ] Возвращает JSON `{hasClientId, hasClientSecret, hasAccessToken,
      hasRefreshToken, expiresAt, refreshNeeded, lastError}`.
- [ ] KDoc с описанием endpoint'а.

### FR-009: `POST /api/utils/vkIdRefreshNow`

- [ ] Endpoint существует в `ApiController.kt` (admin).
- [ ] Вызывает `VkApiClient.refreshVkIdAccessToken()` в try-catch.
- [ ] При успехе — сохраняет новые токены.
- [ ] При ошибке — устанавливает `vkIdRefreshNeeded=true`,
      `vkIdRefreshLastError`.
- [ ] Возвращает JSON `{success, expiresAt?, error?}`.
- [ ] KDoc с описанием endpoint'а.

### FR-010: Deprecation старых endpoints

- [ ] `PublicVkAuthController.getVkOAuthCodeUrl()` → возвращает HTTP 410.
- [ ] `PublicVkAuthController.vkOAuthCallback()` → возвращает HTTP 410.
- [ ] `ApiController.getVkOAuthUrl()` → возвращает HTTP 410.
- [ ] `ApiController.vkOAuthCallback()` (admin) → возвращает HTTP 410.
- [ ] `ApiController.getVkOAuthCodeUrl()` → возвращает HTTP 410.
- [ ] Старый код НЕ удалён (для истории).

### FR-011: Обновление `docs/features/vk-news-auto-publish.md`

- [ ] Добавлена секция «Получение токена через VK ID» (со ссылкой на
      `vk-id-auth.md`).
- [ ] Секция «User-token через Implicit Flow» перенесена в «Deprecated».

### FR-012: Создание `docs/features/vk-id-auth.md`

- [ ] Файл создан в `docs/features/`.
- [ ] Содержит секции: `Что делает`, `Зачем`, `Как работает`,
      `Инварианты / правила`, `Известные ловушки`, `Quickstart`, `Ссылки`.
- [ ] Quickstart ссылается на `quickstart.md` спеки.

### FR-013: KDoc в `PublicVkIdAuthController`

- [ ] `@see docs/features/vk-id-auth.md` в KDoc класса.

### FR-014: Quickstart в `docs/features/vk-id-auth.md`

- [ ] Описывает шаги для админа: регистрация приложения, получение
      client_id/secret, настройка redirect_uri, настройка Karaoke.properties,
      открытие `/api/public/utils/vkIdOAuthUrl`.

### FR-015: Тесты (если добавлены)

- [ ] Unit-тест на `VkIdPkceUtils` (PKCE генерация).
- [ ] Тест на refresh flow (mock VK ID endpoint).

## Non-Functional Requirements

- [ ] **NFR-001**: Refresh token job не блокирует другие задачи —
      выполняется в отдельном потоке, таймаут 30 секунд.
- [ ] **NFR-002**: PKCE `code_verifier` — 43-128 символов из
      `[A-Z][a-z][0-9]-._~`. `code_challenge` = `base64url(SHA256(verifier))`.
- [ ] **NFR-003**: PKCE `state` сохраняется в KaraokeProperties transient
      (TTL 10 минут) и проверяется в callback.
- [ ] **NFR-004**: Секреты НЕ логируются — только факт операции и
      маскированный токен (первые 8 + `...` + последние 4 символа).
- [ ] **NFR-005**: `redirect_uri` в настройках VK ID точно совпадает
      с тем, что передаётся в `/authorize` и `/token`.
- [ ] **NFR-006**: Endpoint `/api/public/utils/vkIdOAuthCallback`
      доступен только на проде (`sm-karaoke.ru`) по HTTPS.

## Документация

- [ ] `specs/151-vk-id-personal-token/spec.md` создан.
- [ ] `specs/151-vk-id-personal-token/plan.md` создан.
- [ ] `specs/151-vk-id-personal-token/tasks.md` создан.
- [ ] `specs/151-vk-id-personal-token/quickstart.md` создан.
- [ ] `specs/151-vk-id-personal-token/research.md` создан.
- [ ] `specs/151-vk-id-personal-token/data-model.md` создан.
- [ ] `specs/151-vk-id-personal-token/contracts/vk-id-api.md` создан.
- [ ] `specs/151-vk-id-personal-token/checklists/requirements.md` создан (этот файл).
- [ ] `docs/features/vk-id-auth.md` создан.
- [ ] `docs/features/vk-news-auto-publish.md` обновлён.
- [ ] `docs/features/README.md` обновлён (добавлен `vk-id-auth.md`).
- [ ] `docs/architecture-notes.md` обновлён (Pass 35).

## CI / Lint

- [ ] `./gradlew ktlintCheck` — без ошибок.
- [ ] `./gradlew :karaoke-web:compileKotlin` — без ошибок.
- [ ] `./gradlew :karaoke-app:compileKotlin` — без ошибок.
- [ ] `bash tools/check-kdoc-coverage.sh` — 100%.
- [ ] `pre-commit run --all-files` — все 7 проверок зелёные.

## Тестирование

- [ ] Smoke-test: `curl /api/public/utils/vkIdOAuthUrl` возвращает URL.
- [ ] Smoke-test: открытие URL в браузере, подтверждение прав.
- [ ] Smoke-test: `curl /api/utils/vkIdTokenStatus` показывает токен.
- [ ] Smoke-test: `POST /api/song/publishToVkNow?id=<id>` → превью есть.
- [ ] Smoke-test: `POST /api/utils/vkIdRefreshNow` → токен обновлён.
- [ ] Smoke-test: старые endpoints возвращают HTTP 410.
- [ ] Smoke-test: VK ID ошибка `error=access_denied` → HTML с ошибкой.

## Деплой

- [ ] PR создан через `gh pr create --base master`.
- [ ] CI 7/7 SUCCESS (`gh pr checks`).
- [ ] PR смержен через `gh pr merge --merge` (без `--delete-branch`).
- [ ] Образы собраны и запушены в Docker Hub (`bash do.sh build && push`).
- [ ] Контейнеры обновлены на сервере (`bash do.sh pull && restart`).
- [ ] Проверка в проде: `curl https://sm-karaoke.ru/api/public/utils/vkIdOAuthUrl`.

## Success Criteria (SC-001..SC-008)

- [ ] **SC-001**: Токен успешно получается через VK ID flow (smoke-test).
- [ ] **SC-002**: AIR-публикация содержит графическое превью (smoke-test).
- [ ] **SC-003**: Автоматический refresh работает (smoke-test с эмуляцией
      истечения).
- [ ] **SC-004**: При истечении `refresh_token` — `vkIdRefreshNeeded=true`
      и `vkIdRefreshLastError` (smoke-test с подменой refresh_token).
- [ ] **SC-005**: Старые endpoints возвращают HTTP 410 (smoke-test).
- [ ] **SC-006**: Время получения токена < 10 секунд (smoke-test с таймером).
- [ ] **SC-007**: Per-feature документ `docs/features/vk-id-auth.md` создан
      (code review).
- [ ] **SC-008**: Spec #138 (загрузка превью-фото) разблокирована —
      PR может быть смержен после #151.

## Подпись

- [ ] Code review пройден (минимум 1 reviewer).
- [ ] Все комментарии reviewer'а разрешены.
- [ ] CI зелёный.
- [ ] Деплой в прод выполнен успешно.
- [ ] Smoke-test в проде пройден.