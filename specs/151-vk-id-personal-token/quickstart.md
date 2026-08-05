# Quickstart: Получение персонального токена через VK ID

**Спека**: [spec.md](./spec.md) | **План**: [plan.md](./plan.md)

> Этот документ — пошаговая инструкция для **администратора** (владельца
> группы ВКонтакте). Не требует знания кода. Время выполнения — 10 минут.

## Что мы делаем

Получаем **персональный токен** пользователя через сервис авторизации
**VK ID** (`id.vk.ru`). Этот токен используется для:

- ✅ Загрузки превью-фото в постах ВКонтакте (через методы `photos.*`).
- ✅ Загрузки видео демо-MP4 (через `video.save`).
- ✅ Публикации постов от имени сообщества (через `wall.post`).

**Почему нельзя получить токен через старый способ?**
Приложение VK с `client_id=54704234` (зарегистрированное 02.08.2026)
заблокировано VK — все запросы к `/oauth.vk.ru/authorize` возвращают
`Security Error`. Новое приложение через VK ID — это рекомендуемый
современный способ авторизации.

## Шаг 1: Регистрация приложения в VK ID (5 минут)

### 1.1. Открыть кабинет разработчика VK ID

Перейти на https://id.vk.com/about/business/go/ и залогиниться как
администратор группы `svoemestokaraoke`.

### 1.2. Создать Web-приложение

1. Нажать **«Создать приложение»** (или аналогичную кнопку).
2. Выбрать тип **«Сайт»** (Web).
3. Заполнить форму:
   - **Название**: `Karaoke SM` (или любое понятное).
   - **Описание**: `Бот автопубликации караоке-песен`.
   - **Домен**: `sm-karaoke.ru`.
   - **Redirect URI**:
     ```
     https://sm-karaoke.ru/api/public/utils/vkIdOAuthCallback
     ```
     ⚠️ **Важно**: URL должен быть **точно** таким (с https, с доменом,
     с `/api/public/utils/vkIdOAuthCallback`). Любое отличие — VK вернёт
     ошибку при попытке авторизации.

> ℹ️ **Почему redirect_uri на проде, а не на admin-машине?**
> VK ID требует **публичный HTTPS** для `redirect_uri`. Admin-машина
> `nsa-i9` за домашним роутером/NAT и не имеет публичного HTTPS.
> Поэтому OAuth callback принимает прод (`karaoke-web`), а потом
> отправляет токены POST-ом на admin-машину для сохранения в
> `Karaoke.properties`. Сам бот работает на admin-машине, не на проде.
> Подробнее — секция «Архитектурный контекст» в `spec.md`.

### 1.3. Получить `client_id` и `client_secret`

1. После создания приложения — найти **`client_id`** (числовой идентификатор).
   Например: `54704235`. **Записать.**
2. Найти **`client_secret`** (защищённый ключ). Обычно показывается 1 раз
   при создании. **Скопировать в безопасное место** (1Password / pass).
   ⚠️ Если потеряете — нужно пересоздать приложение.

### 1.4. Включить нужные права (scopes)

В настройках приложения включить следующие scopes:

- ✅ `vkid.personal_info` — базовая информация о пользователе.
- ✅ `photos` — управление фотографиями.
- ✅ `wall` — стена.
- ✅ `video` — видео.
- ✅ `offline` — бессрочный токен (если поддерживается).

### 1.5. Подтвердить приложение

VK ID может потребовать подтверждения по email или телефону. Следовать
инструкциям на экране. Обычно это занимает 1-2 минуты.

## Шаг 2: Настройка `Karaoke.properties` на admin-машине (2 минуты)

Подключиться к admin-машине `nsa-i9` и отредактировать `Karaoke.properties`:

```bash
ssh nsa@nsa-i9
nano /path/to/Karaoke.properties
```

Добавить 3 строки (значения из Шага 1):

```properties
vkIdClientId=54704235
vkIdClientSecret=<полученный_client_secret>
vkIdRedirectUri=https://sm-karaoke.ru/api/public/utils/vkIdOAuthCallback
```

> ⚠️ **Не путать** с существующими `vkAppId`, `vkClientSecret` (это старые
> ключи для `oauth.vk.ru`). Новые ключи имеют префикс `vkId*`.

Сохранить файл. **Перезапускать** `karaoke-app` пока не нужно — настройки
читаются через `KaraokeProperties.getString(...)` динамически (как сейчас
для старого `vkUserAccessToken`).

## Шаг 3: Настройка `application.yml` для `karaoke-web` (1 минута)

На сервере `188.119.64.111` (или в `docker-compose.yml` локально) добавить
переменные окружения:

```yaml
environment:
  VK_ID_CLIENT_ID: 54704235
  VK_ID_CLIENT_SECRET: <полученный_client_secret>
  VK_ID_REDIRECT_URI: https://sm-karaoke.ru/api/public/utils/vkIdOAuthCallback
  VK_ADMIN_API_URL: http://nsa-i9:8898
```

⚠️ **Секреты** — через переменные окружения, не коммитить в git.

После изменения — перезапустить `karaoke-web` (это делается через
`deploy_web.sh` после деплоя нового кода).

## Шаг 4: Деплой нового кода (5 минут)

Этот шаг делается **после** того, как код спеки #151 будет смержен в
`master` (см. [tasks.md](./tasks.md), Фаза 6):

```bash
cd /home/nsa/Karaoke/deploy
bash do.sh build              # gradle + docker images
bash do.sh push               # push to Docker Hub
ssh root@188.119.64.111 "cd Karaoke/deploy && bash do.sh pull"
ssh root@188.119.64.111 "cd Karaoke/deploy && bash do.sh restart_web && bash do.sh restart_app"
```

После деплоя — проверить, что новый endpoint работает:

```bash
curl https://sm-karaoke.ru/api/public/utils/vkIdOAuthUrl
```

Должен вернуться JSON:

```json
{
  "success": true,
  "url": "https://id.vk.ru/authorize?client_id=54704235&...",
  "instructions": ["..."]
}
```

## Шаг 5: Получение токена через VK ID (2 минуты)

### 5.1. Открыть URL авторизации

Скопировать `url` из ответа выше и открыть в браузере **от имени
администратора группы `svoemestokaraoke`**.

### 5.2. Подтвердить права

VK ID покажет форму с запросом разрешений. Подтвердить **все**:
- ✅ Доступ к базовой информации.
- ✅ Доступ к фотографиям.
- ✅ Доступ к стене.
- ✅ Доступ к видео.
- ✅ Доступ к офлайн-режиму.

### 5.3. Дождаться редиректа

После подтверждения VK ID перенаправит на
`https://sm-karaoke.ru/api/public/utils/vkIdOAuthCallback?code=XXX`.

Наш сервер автоматически:
1. Обменивает `code` на `access_token` + `refresh_token`.
2. Отправляет токены POST-ом на admin-машину.
3. Сохраняет в `Karaoke.properties.vkIdAccessToken` и `vkIdRefreshToken`.
4. Возвращает HTML-страницу с подтверждением.

### 5.4. Проверить, что токен сохранён

На admin-машине:

```bash
curl http://localhost:8898/api/utils/vkIdTokenStatus
```

Должен вернуться JSON:

```json
{
  "hasClientId": true,
  "hasClientSecret": true,
  "hasAccessToken": true,
  "hasRefreshToken": true,
  "expiresAt": "2026-08-05T12:34:56Z",
  "refreshNeeded": false,
  "lastError": ""
}
```

Если `hasAccessToken: true` — всё работает.

## Шаг 6: Запустить публикацию и проверить превью

```bash
curl -X POST 'http://localhost:8898/api/song/publishToVkNow?id=<song_id>'
```

После завершения — открыть пост в группе ВКонтакте. Должно быть
**графическое превью** (картинка обложки альбома + название песни).

## Troubleshooting

### `vkIdOAuthUrl` возвращает `error: "vk.id.client-id is empty"`

→ Не настроен `application.yml` для `karaoke-web`. Проверить Шаг 3.

### `vkIdOAuthUrl` возвращает `error: "vk.id.client-secret is empty"`

→ Не задана переменная окружения `VK_ID_CLIENT_SECRET`. Проверить
docker-compose.yml.

### VK ID возвращает `Security Error` после подтверждения прав

→ `redirect_uri` не совпадает с тем, что в настройках приложения VK ID.
Проверить:
1. В кабинете VK ID — `https://sm-karaoke.ru/api/public/utils/vkIdOAuthCallback`
   (без слэша в конце, без query-параметров, с https).
2. В `Karaoke.properties` — `vkIdRedirectUri` — должен совпадать **точно**.
3. В `application.yml` — `vk.id.redirect-uri` — должен совпадать **точно**.

### `vkIdTokenStatus` возвращает `hasAccessToken: false` после callback

→ Admin-машина недоступна с прода (firewall). Проверить:
1. `http://nsa-i9:8898/api/utils/vkIdTokenStatus` доступен с сервера.
2. В `application.yml` указан правильный `vk.id.admin-api-url`.
3. HTML-страница покажет curl-команду для ручного сохранения — выполнить
   её вручную на admin-машине.

### `error_code=5 User authorization failed` в логах публикации

→ Access_token истёк или был отозван. Подождать срабатывания
`VkIdTokenRefreshScheduler` (каждый час) или вызвать принудительный refresh:

```bash
curl -X POST http://localhost:8898/api/utils/vkIdRefreshNow
```

Если не помогает — `refresh_token` тоже истёк. Повторить Шаг 5 (получить
новый токен через VK ID).

### `error_code=27 Group authorization failed`

→ Это ошибка из `VkPhotoUploadClient`. Означает, что `photos.*` методы
вызваны с токеном сообщества (community-token), а нужен user-token.
Проверить:
1. `vkIdAccessToken` заполнен (через `vkIdTokenStatus`).
2. `VkApiClient.userAccessToken()` возвращает значение из `vkIdAccessToken`,
   а не пустоту.

## Что дальше

После успешного получения токена:

1. **Спека #138 (загрузка превью-фото)** может быть смержена и развёрнута
   в проде. Без токена VK ID она не работала.
2. **Бот автопубликации** автоматически начнёт использовать новый токен
   (через `VkApiClient.userAccessToken()`).
3. **Автоматический refresh** будет работать в фоне (каждый час проверяет
   `expiresAt` и обновляет через `refresh_token`).
4. Если что-то сломается — HTML-страница `vkIdTokenStatus` покажет
   состояние, логи `karaoke-app` покажут детали.

## Безопасность

- ⚠️ `client_secret`, `access_token`, `refresh_token` — **секреты**.
  Не логировать, не коммитить в git.
- ⚠️ Использовать `https://` для `redirect_uri` (VK ID не принимает http).
- ⚠️ PKCE (`code_verifier`, `code_challenge`) защищает от перехвата кода
  авторизации. Не отключать.
- ⚠️ `state` (CSRF-токен) защищает от подделки редиректа. Проверяется
  в callback. Не отключать.
- ⚠️ Если токен скомпрометирован — отозвать через кабинет VK ID и
  повторить Шаг 5.