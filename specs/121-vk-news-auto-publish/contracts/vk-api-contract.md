# Contract: VK API для автопубликации новостей

**Feature**: `121-vk-news-auto-publish`
**Date**: 2026-08-01
**Spec**: [spec.md](../spec.md) | [research.md](../research.md)

> Контракт внешних интеграций фичи — VK API methods, используемые ботом.
> Аутентификация: Community access token (`vkAccessToken` в
> `KaraokeProperties`). Базовый URL: `https://api.vk.ru/method/`.
> Версия API: `5.199` (конфигурируется через `vkApiVersion`).

## 1. wall.post — создание поста в группе

**Endpoint**: `POST https://api.vk.ru/method/wall.post`

**Назначение**: Создание поста в группе ВКонтакте (FR-001, FR-003).
Используется после успешной загрузки видео (§2) или для поста без видео
(редкий случай FR-021).

### Параметры запроса (form-urlencoded / query string)

| Параметр | Тип | Обязательный | Описание |
|----------|-----|--------------|----------|
| `owner_id` | Long | да | ID группы **с минусом** (например, `-123456`). Берётся из `vkGroupId`, бот добавляет `-`. |
| `from_group` | Int | да | `1` — пост от имени сообщества. |
| `message` | String | да* | Текст поста (FR-003): `News.title` + `News.body` + ссылка + хештеги. Лимит 10 000 символов (FR-005). *Обязателен, если нет `attachments`. |
| `attachments` | String | нет | Список прикреплений через запятую. Для видео: `video<owner_id>_<video_id>` (из ответа `video.save`). Для поста без видео — не указывается; VK сам парсит URL из `message` и генерирует rich-preview. |
| `access_token` | String | да | Community access token (`vkAccessToken`). |
| `v` | String | да | Версия API (`vkApiVersion`, дефолт `5.199`). |

### Пример запроса

```http
POST https://api.vk.ru/method/wall.post
Content-Type: application/x-www-form-urlencoded

owner_id=-123456&from_group=1&message=Вышла+в+эфир+новая+песня...&attachments=video-123456_7890&access_token=<TOKEN>&v=5.199
```

### Ответ (успех)

```json
{
  "response": {
    "post_id": 12345
  }
}
```

`post_id` — id созданного поста в группе. Записывается в `Song.idVk`
как `"-<group_id>_<post_id>"` (полный формат VK-ссылки, см. `Song.linkVk`,
`URL_PREFIX_VK` в `Song.kt:1156`).

### Ответ (ошибка)

```json
{
  "error": {
    "error_code": 15,
    "error_msg": "Access denied: no permission to post on this wall",
    "request_params": [...]
  }
}
```

**Non-retryable коды** (см. research.md §5): `4`, `5`, `15`, `100`.
**Retryable** (по умолчанию): сеть/timeout/5xx, `6` (too many requests),
`9`, `1`, `2700`.

## 2. video.save — резервирование видео-записи

**Endpoint**: `POST https://api.vk.ru/method/video.save`

**Назначение**: Резервирование видео-записи перед загрузкой файла (FR-019,
первый шаг двухшагового flow).

### Параметры запроса

| Параметр | Тип | Обязательный | Описание |
|----------|-----|--------------|----------|
| `group_id` | Long | да | ID группы **без минуса** (например, `123456`). |
| `name` | String | да | Название видео: `"<author> — <songName> (демо)"` (по образцу `buildCaption` в Telegram-Фазе 2). |
| `description` | String | нет | Описание видео. Можно дублировать `name` или использовать `News.body` (с усечением). |
| `access_token` | String | да | Community access token. |
| `v` | String | да | Версия API. |

### Ответ (успех)

```json
{
  "response": {
    "owner_id": -123456,
    "video_id": 1234567890,
  "upload_url": "https://uploader.vk.ru/upload.php?act=video&token=..."
  }
}
```

- `owner_id` — id группы (с минусом).
- `video_id` — id зарезервированной видео-записи. Используется в
  `wall.post` как `attachments=video<owner_id>_<video_id>`.
- `upload_url` — URL для загрузки видеофайла (§3).

## 3. Upload video file — загрузка видеофайла

**Endpoint**: `POST <upload_url>` (из ответа `video.save`)

**Назначение**: Загрузка демо-MP4 файла в VK (FR-019, второй шаг).

### Параметры запроса (multipart/form-data)

| Поле | Тип | Обязательный | Описание |
|------|-----|--------------|----------|
| `video_file` | binary | да | Демо-MP4 файл (`Song.pathToFileRenderMp4ForVersion(DEMO)`). Content-Type: `video/mp4`. |

### Ответ (успех)

```json
{
  "size": 12345678,
  "video_id": "1234567890"
}
```

Подтверждение загрузки. После этого видео становится доступным, но VK
обрабатывает его асинхронно (от секунд до минут). Прикрепление к посту
через `wall.post` возможно сразу после `video.save` (по `video_id`),
видео «подтянется» после обработки.

### Лимит размера

- `vkAutoPublishMaxVideoSizeMb` (дефолт `50`), по образцу Telegram-Фазы 2.
- VK API допускает до 2 ГБ для пользовательских загрузок, но для демо-MP4
  (фрагмент ~30 сек, ~5-20 МБ) лимит не проблема. Если файл превышает
  `vkAutoPublishMaxVideoSizeMb` — бот ставит задачу перерендера с
  уменьшенными параметрами (FR-020 сценарий 3, по образцу
  `specs/113-telegram-demo-publish` FR-003).

## 4. Прокси-fallback (конвенция VkApiClient)

`VkApiClient.send` повторяет логику `TelegramApiClient.send`
(`TelegramApiClient.kt:146`):
- Попытка напрямую через `directClient` (JDK `HttpClient` без прокси).
- При ошибке → переключение на `proxyClient()` (через `vkProxyUrl`) на
  TTL-окно `vkProxyModeTtlMs` (дефолт 60 сек).
- Возврат к прямому доступу после TTL, если прямой снова работает.
- `HttpRequest` иммутабелен — один и тот же запрос безопасно передаётся
  в оба клиента.

## 5. Retry / backoff (конвенция VkApiClient)

`VkApiClient.sendPost` (обёртка над `wall.post` + `video.save`):
- 3 попытки с backoff `30 сек → 2 мин → 5 мин` (как
  `TelegramApiClient.sendVideo`, `backoffScheduleMs`).
- Non-retryable коды → немедленный выход без backoff (FR-010).
- Retryable коды → backoff между попытками.
- Возврат `VkAutoPublishResult(state, postId, error)`.

## 6. Внутренний API бота (REST, для админки)

### `POST /api/song/publishToVkNow` (FR-016, FR-026)

**Endpoint в `ApiController.kt`** (новый, по образцу `/api/song/publishToTelegramNow`
из `specs/113-telegram-demo-publish`).

**Назначение**: Принудительный запуск публикации песни во ВКонтакте
(кнопки «Опубликовать во ВК (air)» / «Опубликовать во ВК (premium)» в
`webvue3`). Поддерживает два типа публикации (FR-027 — расширяемо).

**Параметры** (form-urlencoded):

| Параметр | Тип | Обязательный | Описание |
|----------|-----|--------------|----------|
| `id` | Long | да | ID песни для публикации. |
| `type` | String | нет (дефолт `air`) | Тип публикации: `air` или `premium` (FR-023, FR-026). Определяет шаблон (`vkTemplateAir` / `vkTemplatePremium`). Расширяемо (FR-027). |

**Логика**:
1. `Song.loadFromDbById(id)`.
2. FR-008: если `song.idVk` не пуст → вернуть `{ state: "published", postId: idVk }`
   (общая идемпотентность, FR-016 — кнопка должна быть disabled в UI,
   но и API защищено).
3. Если `idVk` пуст → `VkAutoPublishService.publishToVk(song,
   PublicationType.fromCode(type) ?: AIR)`.
4. Вернуть `VkAutoPublishResult` как JSON.

**Ответ** (JSON, по образцу `TelegramAutoPublishResult`):

```json
{
  "state": "published",
  "postId": "-123456_12345",
  "error": null
}
```

Или при ошибке:
```json
{
  "state": "send_failed",
  "postId": null,
  "error": "wall.post failed (15): Access denied"
}
```

**Доступ**: только админ-контекст (`karaoke-app`, `permitAll()` в
`SecurityConfig.kt` — как остальные `/api/song/*` endpoints). На проде
не暴露яется (бот работает только на admin-машине).

### `GET /api/vk/templates` (FR-025)

**Endpoint в `ApiController.kt`** (новый).

**Назначение**: Получить все шаблоны постов ВК (для редактора шаблонов
в `webvue3`).

**Параметры**: нет.

**Ответ** (JSON):
```json
{
  "templates": [
    {
      "type": "air",
      "key": "vkTemplateAir",
      "value": "{author} — {songName} (демо)\n{link}\n#караоке #svoemosto",
      "description": "Шаблон поста типа 'в эфире' (авто, по tbl_news)"
    },
    {
      "type": "premium",
      "key": "vkTemplatePremium",
      "value": "{author} — {songName} (премиум)\n{link}\n#караоке #svoemesto #премиум",
      "description": "Шаблон поста типа 'премиум-выпуск' (ручной, кнопка)"
    }
  ],
  "placeholders": [
    { "name": "author", "description": "Song.author" },
    { "name": "songName", "description": "Song.songName" },
    { "name": "link", "description": "https://sm-karaoke.ru/song?id={id}" },
    { "name": "id", "description": "Song.id" },
    { "name": "body", "description": "News.body (для air; пусто для premium)" }
  ]
}
```

### `POST /api/vk/templates` (FR-025)

**Endpoint в `ApiController.kt`** (новый).

**Назначение**: Сохранить шаблон поста ВК (из редактора шаблонов).

**Параметры** (form-urlencoded):

| Параметр | Тип | Обязательный | Описание |
|----------|-----|--------------|----------|
| `key` | String | да | Ключ KaraokeProperties (`vkTemplateAir` или `vkTemplatePremium`). |
| `value` | String | да | Новое значение шаблона (многострочная строка). |

**Логика**: `KaraokeProperties.set(key, value)` (без перезапуска
`karaoke-app`, FR-015).

**Ответ** (JSON): `{ "success": true, "key": "vkTemplateAir" }` или
`{ "success": false, "error": "unknown key: <key>" }` (если ключ не
зарегистрирован в `listKaraokeProperties`).

## 7. Конвенция формата id поста в `Song.idVk`

- VK возвращает `post_id` (число).
- Бот формирует полный идентификатор: `"-<group_id>_<post_id>"` (например,
  `-123456_12345`).
- Это согласовано с существующим `Song.linkVk` (`Song.kt:1156`):
  `URL_PREFIX_VK.replace("{REPLACE}", idVk)` → `https://vk.ru/wall-123456_12345`.
- То же поле `idVk` исторически использовалось для ручных публикаций
  (администратор вставлял тот же формат вручную) — переиспользование
  не ломает существующий UI (`haveVkGroup`, `flagVk`, `linkVk`).