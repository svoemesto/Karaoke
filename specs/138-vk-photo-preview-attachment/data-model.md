# Data Model: Надёжное превью публикации ВК через прикрепление обложки фото

**Branch**: `132-vk-photo-preview-attachment` | **Date**: 2026-08-04
**Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

## Принцип

**Никаких структурных изменений в БД** — фича использует существующие
поля и сущности. Все новые данные — transient (время жизни = одна
публикация), хранятся в памяти JVM или в файловом кэше.

## Существующие сущности (без изменений)

### Песня (Song) — `tbl_songs`

Используемые поля (уже существуют):

| Поле | Колонка | Тип | Назначение |
|------|---------|-----|------------|
| `idVk` | `id_vk` | `VARCHAR(50)` (nullable) | Идентификатор поста ВКонтакте в формате `-<groupId>_<postId>`. Заполненное значение = пост уже создан = идемпотентность. |
| `vkAutoPublishState` | `player_readiness_flags->>'vkAutoPublishState'` (JSON) | `String` | Состояние операции: `RENDERING` / `PUBLISHING` / `PUBLISHED` / `SEND_FAILED`. |
| `vkAutoPublishLastError` | `player_readiness_flags->>'vkAutoPublishLastError'` (JSON) | `String` | Описание последней ошибки (если была). Префиксы: `photo upload failed:` / `photo attach failed:` / `doc upload failed:` / `wall.post failed:` / `video.save failed:` / `preview prewarm failed:`. |
| `vkAutoPublishLastAttemptAt` | `player_readiness_flags->>'vkAutoPublishLastAttemptAt'` (JSON) | `ISO 8601` | Время последней попытки. |

**Изменений нет** — фича использует эти поля ровно так же, как specs/121.

### Новость (News) — `tbl_news`

Используемые поля (уже существуют):

| Поле | Колонка | Назначение |
|------|---------|------------|
| `songId` | `song_id` | Связь с песней (для `air`-публикации). |
| `publishAt` | `publish_at` | Когда новость становится опубликованной. |
| `category` | `category` | Фильтр — только `air`. |
| `playerReadinessFlags` | `player_readiness_flags` (JSON) | Содержит `vkAutoPublishState` для редкого случая новости без `song_id` (FR-004a specs/121). |

**Изменений нет**.

### Конфигурация (KaraokeProperties)

Новые ключи (по образцу `vkPreviewWarmup*` из specs/130):

| Ключ | Тип | Default | Назначение |
|------|-----|---------|------------|
| `vkPhotoAttachEnabled` | `Boolean` | `true` | Включает загрузку фото через `photos.*`. Если `false` — fallback на docs.* или пост без фото. |
| `vkDocAttachEnabled` | `Boolean` | `true` | Включает fallback на `docs.*` методы. Если `false` — при сбое `photos.*` сразу переходим к деградации (пост без фото). |
| `vkPreviewImageWidth` | `Integer` | `1200` | Ширина PNG-превью. |
| `vkPreviewImageHeight` | `Integer` | `630` | Высота PNG-превью. |

**Изменений в БД нет** — это ключи `tbl_settings` (`KaraokeProperties.kt`),
которые уже хранятся в существующей таблице.

## Новые (transient) сущности

### Прикреплённое фото (Photo Attachment)

**Не персистируется в БД** — создаётся при каждой публикации заново
через VK API, время жизни = пока существует в альбоме группы ВКонтакте.

| Поле | Тип | Назначение |
|------|-----|------------|
| `id` | `Long` | Идентификатор фото в VK (из `photos.saveWallPhoto[0].id`). |
| `ownerId` | `Long` | Идентификатор владельца (отрицательный для группы: `-<groupId>`). |
| `attachment` | `String` | Форматированная строка `photo<ownerId>_<id>` для `wall.post attachments`. |
| `bytes` | `ByteArray` | Содержимое PNG-файла (1200×630). Не хранится в БД, только в памяти при загрузке. |
| `loadMethod` | `enum { PHOTOS, DOCS, NONE }` | Каким методом загружено (для логов и метрик). |

**State machine**: NONE → PHOTOS (успех) | DOCS (fallback) | NONE (деградация).

### Прикреплённый документ-картинка (Doc Attachment)

**Не персистируется в БД**.

| Поле | Тип | Назначение |
|------|-----|------------|
| `id` | `Long` | Идентификатор документа в VK (из `docs.save[0].id`). |
| `ownerId` | `Long` | Идентификатор владельца. |
| `attachment` | `String` | Форматированная строка `doc<ownerId>_<id>` для `wall.post attachments`. |
| `bytes` | `ByteArray` | Содержимое PNG-файла. |
| `loadMethod` | `enum { DOCS }` | Всегда DOCS (используется только как fallback). |

## Поток данных при публикации

```
┌─────────────────────────────────────────────────────────────────────────┐
│ KaraokeProcess / VkAutoPublishService.publishToVk(song, type)          │
└─────────────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ 1. FR-008: проверка Song.idVk (идемпотентность)                         │
└─────────────────────────────────────────────────────────────────────────┘
                                  │ (пуст)
                                  ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ 2. FR-022: проверка song.isContentReady                                 │
└─────────────────────────────────────────────────────────────────────────┘
                                  │ (готов)
                                  ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ 3. FR-020: проверка/рендер demo MP4 (если нужен)                        │
└─────────────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ 4. VkTemplateService.render(template, song, news) — текст поста         │
└─────────────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ 5. specs/130: VkPreviewWarmupClient.warmup(songId) — синхронный GET    │
│    /api/public/song-vk-image/{id} → PNG 1200×630                       │
│    └─ если FAILED → SEND_FAILED, префикс "preview prewarm failed:"     │
└─────────────────────────────────────────────────────────────────────────┘
                                  │ (SUCCESS)
                                  ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ 6. [NEW] VkPhotoUploadClient.uploadCover(songId, pngBytes):            │
│    ├─ photos.getWallUploadServer(group_id) → upload_url                │
│    ├─ POST <upload_url> multipart/form-data {photo}                    │
│    │   → {server, photo, hash}                                          │
│    ├─ photos.saveWallPhoto(server, photo, hash, group_id)              │
│    │   → [{id, owner_id}]                                               │
│    └─ photoAttachment = "photo<owner>_<id>"                            │
│    При ошибке error_code=27/15/5 →                                     │
│    ├─ docs.getWallUploadServer(type=image) → upload_url                │
│    ├─ POST <upload_url> multipart/form-data {file}                     │
│    │   → {file}                                                         │
│    ├─ docs.save(file, title) → [{id, owner_id}]                        │
│    └─ docAttachment = "doc<owner>_<id>"                                │
│    Если оба не сработали → photoAttachment = null                       │
└─────────────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ 7. FR-019: video.save → wall.post                                      │
│    attachments = "photo<owner>_<id>,video<owner>_<video_id>"          │
│    или "photo<owner>_<id>" (если нет видео)                            │
│    или "video<owner>_<video_id>" (если фото не загрузилось)            │
└─────────────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ 8. FR-004: запись Song.idVk через Song.saveToDb()                      │
└─────────────────────────────────────────────────────────────────────────┘
```

## Состояния публикации (расширение VkAutoPublishState)

Существующие состояния сохраняются:

| Состояние | Когда |
|-----------|-------|
| `SCHEDULED` | Не content-ready (FR-022). |
| `RENDERING` | Идёт рендер demo MP4. |
| `PUBLISHING` | Идёт публикация (между прогревом и wall.post). |
| `PUBLISHED` | Пост создан, `Song.idVk` заполнен. |
| `SEND_FAILED` | Любой сбой (превью / загрузка фото / video.save / wall.post). |

**Новых состояний нет** — используем существующие. Ошибка загрузки фото
записывается через `vkAutoPublishLastError` с префиксом, администратор
видит причину в `webvue3`.

## Совместимость с sync LOCAL↔SERVER

**Изменений в sync нет**:
- Новые ключи `KaraokeProperties` (`vkPhotoAttachEnabled`, `vkDocAttachEnabled`,
  `vkPreviewImageWidth`, `vkPreviewImageHeight`) — это **настройки admin-машины**,
  а не данные песни. Они НЕ участвуют в sync LOCAL↔SERVER (так же, как
  `vkPreviewWarmupEnabled`, `vkProxyUrl` — это локальные настройки).
- Никаких изменений в `tbl_songs`, `tbl_news`, `tbl_settings` —
  recordhash-триггеры не затрагиваются.
- SyncRegistry не меняется.

## Миграции БД

**Нет** — никаких миграций не требуется. Все новые данные либо transient
(загруженные фото), либо хранятся в существующих JSON-полях
(`playerReadinessFlags.vkAutoPublishLastError`).

## Валидация

- ✅ Скомпилируется с текущей структурой `tbl_songs`.
- ✅ Существующие unit-тесты (`VkPreviewWarmupClientTest`) — без изменений.
- ✅ Существующие ручные сценарии (specs/121, specs/130) — продолжают работать.