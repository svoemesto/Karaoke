# Contract: Загрузка обложки фото в VK через `photos.*` / `docs.*`

**Branch**: `132-vk-photo-preview-attachment` | **Date**: 2026-08-04
**Spec**: [spec.md](../spec.md) | **Plan**: [plan.md](../plan.md) | **Data Model**: [data-model.md](../data-model.md)

## Назначение

Контракт описывает поток вызовов VK API для загрузки PNG-обложки песни
в группу ВКонтакте и прикрепления её к посту через `wall.post`. Используется
**новый компонент** `VkPhotoUploadClient` в `karaoke-app` и расширенный
endpoint `/api/public/song-vk-image/{id}` в `karaoke-web` (размер 1200×630).

## Точки вызова

### Внутренний контракт (Kotlin)

```kotlin
// В VkAutoPublishService — после VkPreviewWarmupClient.warmup(songId):
val photoUploadResult = VkPhotoUploadClient().uploadCover(
    songId = song.id,
    groupId = KaraokeProperties.getString("vkGroupId"),
    pngBytes = pngBytes, // ByteArray из VK_IMAGE endpoint
)

// photoUploadResult.state == PhotoUploadState.SUCCESS → attachment готов
// photoUploadResult.attachment = "photo<owner>_<id>" или "doc<owner>_<id>"
// photoUploadResult.error = null

// Если FAILED → логируем, продолжаем без фото (деградация)
```

### Внешний контракт (HTTP к VK API)

#### 1. `photos.getWallUploadServer` — получить URL загрузки

**Метод**: `POST https://api.vk.ru/method/photos.getWallUploadServer`

**Параметры (application/x-www-form-urlencoded)**:

| Параметр | Значение | Обязательность |
|----------|----------|----------------|
| `group_id` | ID группы без минуса (например, `123456`) | Обязателен |
| `access_token` | user-token из `vkUserAccessToken` (scope `photos`) | Обязателен |
| `v` | `5.199` (или текущая версия из `vkApiVersion`) | Обязателен |

**Ответ (успех, HTTP 200)**:

```json
{
  "response": {
    "upload_url": "https://pu.vk.ru/...upload.php?act=publish_audio_to_group&...&mid=...&hash=...",
    "album_id": -123456,
    "user_id": 987654
  }
}
```

**Ответ (ошибка)**:

```json
{
  "error": {
    "error_code": 27,
    "error_msg": "Group authorization failed: method is unavailable with group auth"
  }
}
```

**Non-retryable коды** (при которых пробуем fallback `docs.*`):
- `5` (User authorization failed)
- `15` (Access denied)
- `27` (Group authorization failed)

**Retry**: при `5xx` HTTP, тайм-аутах — до 3 попыток с backoff.

#### 2. POST на `upload_url` — загрузить файл

**Метод**: `POST <upload_url>` (URL из шага 1)

**Тело**: `multipart/form-data`

| Поле | Content-Type | Описание |
|------|--------------|----------|
| `photo` | `image/png` | PNG-файл 1200×630, размер ~100-300 КБ |

**Заголовки**:

| Заголовок | Значение |
|-----------|----------|
| `Content-Type` | `multipart/form-data; boundary=<boundary>` |

**Ответ (успех, HTTP 200)**:

```json
{
  "server": 987654,
  "photo": "[]",  // JSON-строка, может быть "[]" если фото без отметок
  "hash": "abcdef0123456789..."
}
```

Поле `photo` — JSON-строка (вложенный JSON внутри JSON). Для одного фото
без отметок это обычно `"[]"`.

**Ответ (ошибка)**: HTTP не 200 или пустое тело → retry до 3 раз.

#### 3. `photos.saveWallPhoto` — сохранить загруженное фото

**Метод**: `POST https://api.vk.ru/method/photos.saveWallPhoto`

**Параметры (application/x-www-form-urlencoded)**:

| Параметр | Значение | Обязательность |
|----------|----------|----------------|
| `server` | `int` из ответа шага 2 | Обязателен |
| `photo` | JSON-строка из ответа шага 2 (URL-encoded) | Обязателен |
| `hash` | строка из ответа шага 2 | Обязателен |
| `group_id` | ID группы без минуса | Обязателен |
| `access_token` | user-token | Обязателен |
| `v` | `5.199` | Обязателен |

**Ответ (успех, HTTP 200)**:

```json
{
  "response": [
    {
      "id": 456789012,
      "owner_id": -123456,
      "sizes": [...],
      "text": "",
      "date": 1691097600
    }
  ]
}
```

**Ответ (ошибка)**: те же non-retryable коды, что в шаге 1.

**Извлекаем**: `photo<owner_id>_<id>` = `photo-123456_456789012`.

#### 4. (Fallback) `docs.getWallUploadServer` — получить URL для документа

**Метод**: `POST https://api.vk.ru/method/docs.getWallUploadServer`

**Параметры**:

| Параметр | Значение |
|----------|----------|
| `access_token` | community-token из `vkAccessToken` (право `docs`) |
| `v` | `5.199` |

**Ответ**:

```json
{
  "response": {
    "upload_url": "https://pu.vk.ru/...upload.php?act=doc_add_to_wall..."
  }
}
```

#### 5. (Fallback) POST на `upload_url` для документа

**Метод**: `POST <upload_url>` (URL из шага 4)

**Тело**: `multipart/form-data`

| Поле | Content-Type | Описание |
|------|--------------|----------|
| `file` | `image/png` | PNG-файл 1200×630 |

**Ответ**:

```json
{
  "file": "{\"title\":\"...\",\"size\":...}"
}
```

Поле `file` — JSON-строка с метаданными загруженного документа.

#### 6. (Fallback) `docs.save` — сохранить документ

**Метод**: `POST https://api.vk.ru/method/docs.save`

**Параметры**:

| Параметр | Значение |
|----------|----------|
| `file` | JSON-строка из ответа шага 5 (URL-encoded) |
| `title` | Название (например, `"cover-${songId}.png"`) |
| `access_token` | community-token |
| `v` | `5.199` |

**Ответ**:

```json
{
  "response": {
    "id": 234567890,
    "owner_id": -123456,
    "title": "cover-12345.png",
    "size": 234567,
    "ext": "png",
    "url": "https://vk.com/doc-123456_234567890",
    "date": 1691097600,
    "type": 4
  }
}
```

**Извлекаем**: `doc<owner_id>_<id>` = `doc-123456_234567890`.

#### 7. `wall.post` — публикация с прикреплениями

**Метод**: `POST https://api.vk.ru/method/wall.post`

**Параметры**:

| Параметр | Значение |
|----------|----------|
| `owner_id` | `-<groupId>` |
| `from_group` | `1` |
| `message` | Текст поста (URL-encoded) |
| `attachments` | `photo<owner>_<id>,video<owner>_<video_id>` (если есть видео) или `photo<owner>_<id>` или `video<owner>_<video_id>` |
| `access_token` | community-token |
| `v` | `5.199` |

**Прикрепления** (порядок важен — VK берёт первое как превью):

```
photo<owner>_<id>      ← первое = превью-картинка
video<owner>_<video_id> ← второе = видео демо-MP4
```

Если нет видео — только фото. Если фото не загрузилось (оба метода не
сработали) — только видео (пост создаётся в деградированном виде).

## Модель ошибок

| Сценарий | Действие | `vkAutoPublishLastError` |
|----------|----------|--------------------------|
| `photos.getWallUploadServer` вернул HTTP 5xx | Retry до 3 раз, потом FAILED | `photo upload failed: photos.getWallUploadServer HTTP 503` |
| `photos.getWallUploadServer` вернул `error_code=27/15/5` | **Fallback на docs.*** (если `vkDocAttachEnabled=true`) | (не записывается — успех через docs.*) |
| `photos.getWallUploadServer` вернул `error_code=27/15/5`, docs.* тоже не сработали | Деградация (пост без фото) | `photo attach failed: photos.*=27 + docs.*=27` |
| POST на `upload_url` (photos) вернул HTTP не 200 | Retry до 3 раз, потом FAILED | `photo upload failed: upload HTTP 500` |
| `photos.saveWallPhoto` вернул `error_code=27/15/5` | **Fallback на docs.*** | (не записывается) |
| `photos.saveWallPhoto` вернул `error_code=100` (invalid params) | Retry не поможет → FAILED | `photo upload failed: photos.saveWallPhoto 100` |
| VK API timeout | Retry до 3 раз | `photo upload failed: timeout after 30000ms` |

**Префиксы `vkAutoPublishLastError`** (для UI администратора):
- `photo upload failed:` — сбой на шаге `photos.*` без fallback.
- `photo attach failed:` — оба способа (`photos.*` + `docs.*`) не сработали → деградация.
- `doc upload failed:` — сбой на шаге `docs.*` (fallback тоже не сработал).
- `wall.post failed:` — сбой на финальном `wall.post` (не относится к фото).
- `video.save failed:` — сбой на `video.save` (не относится к фото).
- `preview prewarm failed:` — сбой на прогреве PNG (specs/130).

## Контракт с `webvue3`

**Изменений нет**:
- Никаких новых UI-элементов в первой версии.
- Администратор видит состояние `vkAutoPublishState` и ошибку
  `vkAutoPublishLastError` в существующем UI карточки песни.
- Если хочется видеть «превью загружено» — отдельная задача (backlog).

## Контракт с `KaraokeProperties`

Новые ключи (опциональные, с default):

| Ключ | Тип | Default | Где читается |
|------|-----|---------|--------------|
| `vkPhotoAttachEnabled` | Boolean | `true` | `VkPhotoUploadClient` |
| `vkDocAttachEnabled` | Boolean | `true` | `VkPhotoUploadClient` |
| `vkPreviewImageWidth` | Integer | `1200` | `PublicApiController.songVkImage` |
| `vkPreviewImageHeight` | Integer | `630` | `PublicApiController.songVkImage` |

## Контракт с `docs/features/vk-news-auto-publish.md`

Обновляется в том же PR (FR-009 specs/121):
- Секция «Как работает»: добавить шаг 6 (загрузка фото) между прогревом и `wall.post`.
- Секция «Известные ловушки»: добавить про `photos.*` user-token scope и fallback.
- Связанные документы: добавить ссылку на `specs/132-vk-photo-preview-attachment/`.

## Совместимость

- ✅ Обратная совместимость: `vkPhotoAttachEnabled=false` → бот работает как раньше
  (только видео, без фото — старые посты без превью).
- ✅ Существующие unit-тесты (`VkPreviewWarmupClientTest`) — без изменений.
- ✅ Существующие сценарии `specs/121-vk-news-auto-publish/quickstart.md` —
  продолжают работать (с `vkPhotoAttachEnabled=false`).
- ✅ Существующие сценарии `specs/130-vk-preview-generation/quickstart.md` —
  продолжают работать (прогрев PNG сохраняется).