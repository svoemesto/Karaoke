# Tasks: Миграция на VK ID

**Feature Branch**: `151-vk-id-personal-token`
**Spec**: [spec.md](./spec.md)
**Plan**: [plan.md](./plan.md)

> **Конвенция**: задачи сгруппированы по фазам. Каждая задача имеет
> чекбокс `- [ ]`, который становится `[x]` при выполнении. Подзадачи —
> вложенные списки.

## Фаза 0: Подготовка (пользователь делает вручную)

> **Блокирует** все последующие фазы. Без `client_id` и `client_secret`
> невозможно реализовать и протестировать VK ID flow.

- [ ] **T-0.1**: Зарегистрировать приложение на https://id.vk.com/about/business/go/
  - [ ] Зайти как администратор группы `svoemestokaraoke`.
  - [ ] Создать Web-приложение (тип «Сайт»).
  - [ ] Запомнить `client_id` (числовой).
  - [ ] Скопировать `client_secret` (показывается 1 раз).
  - [ ] Указать `redirect_uri = https://sm-karaoke.ru/api/public/utils/vkIdOAuthCallback`.
  - [ ] Включить scopes: `vkid.personal_info`, `photos`, `wall`, `video`, `offline`.
  - [ ] Подтвердить приложение (email + телефон, если требуется).

- [ ] **T-0.2**: Настроить `Karaoke.properties` на admin-машине `nsa-i9`:
  ```properties
  vkIdClientId=<новый_client_id>
  vkIdClientSecret=<полученный_client_secret>
  vkIdRedirectUri=https://sm-karaoke.ru/api/public/utils/vkIdOAuthCallback
  ```

- [ ] **T-0.3**: Настроить `application.yml` для `karaoke-web` (прод):
  ```yaml
  vk:
    id:
      client-id: ${VK_ID_CLIENT_ID:0}
      client-secret: ${VK_ID_CLIENT_SECRET:}
      redirect-uri: ${VK_ID_REDIRECT_URI:https://sm-karaoke.ru/api/public/utils/vkIdOAuthCallback}
      admin-api-url: ${VK_ADMIN_API_URL:http://nsa-i9:8898}
  ```
  Добавить переменные окружения в docker-compose.yml:
  ```yaml
  VK_ID_CLIENT_ID=<client_id>
  VK_ID_CLIENT_SECRET=<client_secret>
  ```

- [ ] **T-0.4**: Записать `client_id` и `client_secret` в безопасное место
  (1Password / pass). **Не коммитить** в git.

## Фаза 1: Реализация endpoints в `karaoke-web`

- [ ] **T-1.1**: Создать `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/utils/VkIdPkceUtils.kt`
  - [ ] Реализовать `generateCodeVerifier(): String` — 64 символа из `[A-Z][a-z][0-9]-._~` через `SecureRandom`.
  - [ ] Реализовать `generateCodeChallenge(verifier: String): String` — `Base64.getUrlEncoder().withoutPadding().encodeToString(SHA-256(verifier))`.
  - [ ] Реализовать `generateState(): String` — 32 символа через `SecureRandom`.
  - [ ] Добавить KDoc с описанием и ссылкой на RFC 7636.

- [ ] **T-1.2**: Создать `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicVkIdAuthController.kt`
  - [ ] `@RestController class PublicVkIdAuthController` с инжектом настроек через `@Value`.
  - [ ] Метод `GET /api/public/utils/vkIdOAuthUrl`:
    - [ ] Проверка настроек (`client-id`, `client-secret`, `redirect-uri`).
    - [ ] Генерация `code_verifier`, `code_challenge`, `state`.
    - [ ] Сохранение `code_verifier` + `state` в transient KaraokeProperties
      (`vkIdPendingCodeVerifier`, `vkIdPendingState`, `vkIdPendingAt`).
    - [ ] Возврат JSON `{success, url, instructions}` с URL авторизации.
  - [ ] Метод `GET /api/public/utils/vkIdOAuthCallback`:
    - [ ] Приём `code`, `state`, `error?` через `@RequestParam`.
    - [ ] Проверка `state` (CSRF) — должно совпадать с `vkIdPendingState`.
    - [ ] Получение `code_verifier` из `vkIdPendingCodeVerifier`.
    - [ ] POST `https://id.vk.ru/oauth2/token` с `grant_type=authorization_code`,
      `client_id`, `client_secret`, `redirect_uri`, `code`, `code_verifier`.
    - [ ] Парсинг response: `access_token`, `refresh_token`, `expires_in`, `id_token?`.
    - [ ] Вычисление `expiresAt = now + expires_in`.
    - [ ] HTTP POST на admin-машину `http://nsa-i9:8898/api/utils/vkIdSaveTokens`
      с параметрами `accessToken`, `refreshToken`, `expiresIn`, `idToken`.
    - [ ] Возврат HTML с подтверждением (по образцу `PublicVkAuthController.vkOAuthCallback`).
    - [ ] Очистка transient-полей `vkIdPending*`.

- [ ] **T-1.3**: Тесты (опционально, в первой версии можно без них):
  - [ ] Unit-тест на `VkIdPkceUtils` (PKCE генерация).
  - [ ] Mock-тест на `PublicVkIdAuthController` (с MockBean для HTTP-клиента).

## Фаза 2: Реализация endpoints в `karaoke-app`

- [ ] **T-2.1**: Обновить `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProperties.kt`
  - [ ] Добавить 9 новых ключей с KDoc:
    - [ ] `vkIdClientId` (Long, default 0).
    - [ ] `vkIdClientSecret` (String, default "").
    - [ ] `vkIdRedirectUri` (String, default "").
    - [ ] `vkIdAccessToken` (String, default "").
    - [ ] `vkIdRefreshToken` (String, default "").
    - [ ] `vkIdAccessTokenExpiresAt` (String ISO datetime, default "").
    - [ ] `vkIdIdToken` (String, default "").
    - [ ] `vkIdRefreshNeeded` (Boolean, default false).
    - [ ] `vkIdRefreshLastError` (String, default "").
    - [ ] Также добавить transient-ключи: `vkIdPendingCodeVerifier`, `vkIdPendingState`, `vkIdPendingAt`.

- [ ] **T-2.2**: Обновить `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/VkApiClient.kt`
  - [ ] Изменить `userAccessToken()`:
    ```kotlin
    private fun userAccessToken(): String {
        val idToken = KaraokeProperties.getString("vkIdAccessToken")
        return idToken.ifBlank { KaraokeProperties.getString("vkUserAccessToken") }
    }
    ```
  - [ ] Добавить класс `VkIdTokenRefreshResult` (data class с `accessToken`,
    `refreshToken`, `expiresIn`, `idToken?`).
  - [ ] Добавить класс `VkIdRefreshFailedException` (Exception с `errorCode`, `errorMsg`).
  - [ ] Добавить метод `refreshVkIdAccessToken(): VkIdTokenRefreshResult`:
    - [ ] Чтение `vkIdClientId`, `vkIdClientSecret`, `vkIdRefreshToken`.
    - [ ] Проверка, что все 3 настройки заполнены.
    - [ ] POST `https://id.vk.ru/oauth2/token` с `grant_type=refresh_token`,
      `client_id`, `client_secret`, `refresh_token`.
    - [ ] Парсинг response (та же логика, что и в PublicVkIdAuthController).
    - [ ] При `error=invalid_grant` — выбросить `VkIdRefreshFailedException`.

- [ ] **T-2.3**: Добавить 3 endpoint'а в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt`
  - [ ] `POST /api/utils/vkIdSaveTokens`:
    - [ ] Приём `accessToken`, `refreshToken`, `expiresIn`, `idToken?` через `@RequestParam`.
    - [ ] Проверка `accessToken` через `users.get` (по образцу `vkSaveUserToken`).
    - [ ] Сохранение в KaraokeProperties: `vkIdAccessToken`, `vkIdRefreshToken`,
      `vkIdAccessTokenExpiresAt = now + expiresIn`, `vkIdIdToken`,
      `vkIdRefreshNeeded = false`, `vkIdRefreshLastError = ""`.
    - [ ] Возврат JSON `{success, userId, firstName, lastName, expiresAt, message}`.
  - [ ] `GET /api/utils/vkIdTokenStatus`:
    - [ ] Чтение 7 ключей из KaraokeProperties.
    - [ ] Возврат JSON `{hasClientId, hasClientSecret, hasAccessToken,
      hasRefreshToken, expiresAt, refreshNeeded, lastError}`.
  - [ ] `POST /api/utils/vkIdRefreshNow`:
    - [ ] Вызов `VkApiClient.refreshVkIdAccessToken()` в try-catch.
    - [ ] При успехе — сохранение новых токенов.
    - [ ] При ошибке — установка `vkIdRefreshNeeded=true`, `vkIdRefreshLastError`.
    - [ ] Возврат JSON `{success, expiresAt?, error?}`.

- [ ] **T-2.4**: Создать `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/VkIdTokenRefreshScheduler.kt`
  - [ ] Класс `VkIdTokenRefreshScheduler` с `@Service` или `@Component`.
  - [ ] `@Scheduled(cron = "0 0 * * * *")` метод `refreshIfNeeded()`:
    - [ ] Чтение `vkIdAccessTokenExpiresAt`.
    - [ ] Если пустое — выход (токен не получен).
    - [ ] Парсинг `expiresAt` как ISO datetime.
    - [ ] Если до `expiresAt` осталось < 30 минут — вызов `refreshVkIdAccessToken()`.
    - [ ] При успехе — сохранение новых токенов + логирование INFO.
    - [ ] При ошибке — установка `vkIdRefreshNeeded=true`,
      `vkIdRefreshLastError`, логирование WARNING.

## Фаза 3: Deprecation старых endpoints

- [ ] **T-3.1**: Обновить `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicVkAuthController.kt`
  - [ ] `getVkOAuthCodeUrl()` → возвращает `ResponseEntity.status(410).body(Map.of("deprecated", true, "use", "/api/public/utils/vkIdOAuthUrl"))`.
  - [ ] `vkOAuthCallback()` → аналогично.

- [ ] **T-3.2**: Обновить `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt`
  - [ ] `getVkOAuthUrl()` (admin, ~line 7091) → возвращает HTTP 410.
  - [ ] `vkOAuthCallback()` (admin, ~line 7206) → возвращает HTTP 410.
  - [ ] `getVkOAuthCodeUrl()` (admin, ~line 7310) → возвращает HTTP 410.
  - [ ] **Не удалять** код (для истории и обратной совместимости).

## Фаза 4: Документация

- [ ] **T-4.1**: Создать `docs/features/vk-id-auth.md` (per-feature документация)
  - [ ] `## Что делает` — краткое описание VK ID flow.
  - [ ] `## Зачем` — почему мигрируем с oauth.vk.ru.
  - [ ] `## Как работает` — диаграмма + пошаговое описание.
  - [ ] `## Инварианты / правила` — список FR-001..FR-015.
  - [ ] `## Известные ловушки` — типичные ошибки (см. plan.md «Риски»).
  - [ ] `## Quickstart` — ссылка на `quickstart.md`.
  - [ ] `## Ссылки` — на spec.md, на VK ID документацию, RFC 7636.

- [ ] **T-4.2**: Обновить `docs/features/vk-news-auto-publish.md`
  - [ ] Добавить секцию «Получение токена через VK ID» (со ссылкой на `vk-id-auth.md`).
  - [ ] Перенести секцию «User-token через Implicit Flow» в «Deprecated».

- [ ] **T-4.3**: Обновить `docs/features/README.md`
  - [ ] Добавить строку `vk-id-auth.md` в таблицу ключевых подсистем.

- [ ] **T-4.4**: Обновить `docs/architecture-notes.md`
  - [ ] Добавить Pass 35 (запись о PR #151).

- [ ] **T-4.5**: Обновить KDoc в `PublicVkIdAuthController`:
  - [ ] Добавить `@see docs/features/vk-id-auth.md`.

## Фаза 5: Локальное тестирование

- [ ] **T-5.1**: Собрать модули:
  ```bash
  cd /home/nsa/Karaoke
  ./gradlew karaoke-web:bootJar
  ./gradlew karaoke-app:bootJar
  ```
  - [ ] Убедиться, что сборка проходит без ошибок.

- [ ] **T-5.2**: Перезапустить локальные контейнеры:
  ```bash
  cd /home/nsa/Karaoke/deploy
  bash do.sh restart_web
  bash do.sh restart_app
  ```
  - [ ] Дождаться, пока контейнеры стартанут (проверить логи).

- [ ] **T-5.3**: Smoke-test VK ID flow:
  - [ ] `curl http://localhost:8897/api/public/utils/vkIdOAuthUrl` → возвращает URL.
  - [ ] Открыть URL в браузере, подтвердить права.
  - [ ] После редиректа — убедиться, что HTML показывает «✅ авто-сохранено».
  - [ ] `curl http://localhost:8898/api/utils/vkIdTokenStatus` → токен сохранён.

- [ ] **T-5.4**: Smoke-test публикации:
  - [ ] `curl -X POST 'http://localhost:8898/api/song/publishToVkNow?id=<test_song_id>'` → успех.
  - [ ] Открыть пост в группе ВК — превью есть.

- [ ] **T-5.5**: Smoke-test refresh:
  - [ ] Эмулировать истечение токена: `KaraokeProperties.set("vkIdAccessTokenExpiresAt", "2026-01-01T00:00:00Z")`.
  - [ ] Вызвать `curl -X POST http://localhost:8898/api/utils/vkIdRefreshNow` → успех.
  - [ ] Проверить `vkIdTokenStatus` — `expiresAt` обновлён.

- [ ] **T-5.6**: Smoke-test deprecation:
  - [ ] `curl http://localhost:8897/api/public/utils/vkOAuthCodeUrl` → HTTP 410.
  - [ ] `curl http://localhost:8898/api/utils/vkOAuthUrl` → HTTP 410.

## Фаза 6: Деплой в прод

- [ ] **T-6.1**: Запустить `pre-commit run --all-files` (lint + KDoc/JSDoc coverage).
  - [ ] Исправить все ошибки (если есть).

- [ ] **T-6.2**: Закоммитить изменения:
  ```bash
  git add specs/151-vk-id-personal-token/ \
          karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicVkIdAuthController.kt \
          karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicVkIdAuthController.kt \
          karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/utils/VkIdPkceUtils.kt \
          karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/VkApiClient.kt \
          karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/VkIdTokenRefreshScheduler.kt \
          karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProperties.kt \
          karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt \
          docs/features/vk-id-auth.md \
          docs/features/vk-news-auto-publish.md \
          docs/features/README.md \
          docs/architecture-notes.md
  git commit -m "feat(vk-id): миграция с oauth.vk.ru на id.vk.ru для персонального токена (specs/151)"
  ```

- [ ] **T-6.3**: Создать PR:
  ```bash
  git push -u origin 151-vk-id-personal-token
  gh pr create --base master \
    --title "feat(vk-id): миграция на VK ID для персонального токена (specs/151)" \
    --body "..."
  ```

- [ ] **T-6.4**: Дождаться CI 7/7 SUCCESS (`gh pr checks`).

- [ ] **T-6.5**: Merge PR: `gh pr merge <N> --merge` (без `--delete-branch`).

- [ ] **T-6.6**: Деплой:
  ```bash
  cd /home/nsa/Karaoke/deploy
  bash do.sh build
  bash do.sh push
  ssh root@188.119.64.111 "cd Karaoke/deploy && bash do.sh pull_web"
  ssh root@188.119.64.111 "cd Karaoke/deploy && bash do.sh restart_web"
  ```

- [ ] **T-6.7**: Проверка в проде:
  - [ ] `curl https://sm-karaoke.ru/api/public/utils/vkIdOAuthUrl` → возвращает URL.
  - [ ] Открыть URL в браузере, получить токен.
  - [ ] Проверить `https://sm-karaoke.ru/api/utils/vkIdTokenStatus` (через admin API).
  - [ ] Запустить публикацию — превью работает.

## Фаза 7: Разблокировка спеки #138

- [ ] **T-7.1**: Убедиться, что спека #138 (загрузка превью-фото) готова
  к merge (проверить текущий статус).

- [ ] **T-7.2**: Деплой спеки #138 в прод (после #151).

- [ ] **T-7.3**: Проверить, что в новых AIR-публикациях есть превью-фото
  (SC-001 спеки #138).

## Backlog (не входит в первую версию)

- [ ] **B-1**: UI-индикатор «требуется повторная авторизация VK ID» в `webvue3`
  (на базе `vkIdRefreshNeeded=true`).
- [ ] **B-2**: Автоматический refresh по ответу VK API `error_code=5`
  (вместо scheduled job).
- [ ] **B-3**: Поддержка нескольких VK ID приложений (для dev и prod).
- [ ] **B-4**: Валидация подписи `id_token` через JWK VK ID.
- [ ] **B-5**: Вращение `client_secret` (если VK потребует).
- [ ] **B-6**: Удаление старых `oauth.vk.ru` endpoint'ов (когда 100% пользователей
  перейдут на VK ID).

## Зависимости между задачами

```
Фаза 0 → Фазы 1, 2, 3, 4 → Фаза 5 → Фаза 6 → Фаза 7
                ↓                ↓        ↓
              (параллельно)   (блокирует деплой)
```

Внутри фаз:
- T-1.1 не зависит ни от чего (можно делать первым).
- T-1.2 зависит от T-1.1.
- T-2.1 не зависит ни от чего.
- T-2.2 зависит от T-2.1.
- T-2.3 зависит от T-2.2.
- T-2.4 зависит от T-2.2.
- T-3.1, T-3.2 — независимы (после Фаз 1-2).
- T-4.1 — независима.
- T-4.2, T-4.3, T-4.4 — независимы.
- T-5.1-T-5.6 — последовательно (тестирование).
- T-6.1-T-6.7 — последовательно (деплой).