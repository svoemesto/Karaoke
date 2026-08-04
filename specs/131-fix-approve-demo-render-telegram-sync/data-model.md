# Data Model: фича 131 — починка пайплайна после одобрения задания

> **Статус**: Phase 1 (design). Никаких изменений схемы БД: фича использует
> уже существующие сущности `tbl_songs`, `tbl_processes`, `tbl_news`,
> `tbl_pictures`, `tbl_authors`, `tbl_albums`. Это закреплено Assumption A-002 в
> `spec.md` и согласуется с Principle II (recordhash) конституции.

## Содержание

1. [Затронутые сущности (no schema changes)](#1-затронутые-сущности-no-schema-changes)
2. [Используемые поля](#2-используемые-поля)
3. [State transitions](#3-state-transitions)
4. [Validation rules](#4-validation-rules)
5. [Связи между сущностями](#5-связи-между-сущностями)
6. [Что НЕ входит](#6-что-не-входит)

---

## 1. Затронутые сущности (no schema changes)

| Entity | Файл модели | Зачем фиче |
|---|---|---|
| `Song` (`tbl_songs`) | `karaoke-app/.../model/Song.kt:73` | Источник данных. При approve меняются `id_status` (`5`/`6`→`6`), внутренний флаг `newsAvailableAnnounced` (через `markNewsAvailableIfReady`), затем строки пушатся на сервер. |
| `KaraokeProcess` (`tbl_processes`) | `karaoke-app/.../KaraokeProcess.kt:49` | Хранит `RENDER_MP4_DEMO`-задание. Создаём новую строку из approve. Дальше worker обрабатывает стандартно (status `WAITING`→`WORKING`→`DONE`/`ERROR`). |
| `News` (`tbl_news`) | `karaoke-app/.../model/News.kt:42` | Сервер при apply `tbl_songs`-sync'а создаёт строку с `category='premium'` через `SongReleaseAnnouncementService.detectAndAnnounceAvailability`. |
| `Picture` (`tbl_pictures`) | `karaoke-app/.../model/Pictures.kt:33` | Пушится на сервер в related-sync после approve. |
| `Author` (`tbl_authors`) | `karaoke-app/.../model/SongSyncTarget → Author` (sync) | Пушится на сервер в related-sync. |
| `Album` (`tbl_albums`) | `karaoke-app/.../model/Album` | Пушится на сервер в related-sync. |

Поскольку никаких миграций нет, документ по сути описывает **роль каждой
сущности** в новом потоке. Подробные определения колонок — в
соответствующих `model/*` файлах и в `deploy/karaoke-db/*.sql`.

---

## 2. Используемые поля

### 2.1 `tbl_songs`

| Поле | Тип | Использование |
|---|---|---|
| `id` | bigint PK | Идентификатор песни в approve-sync. |
| `id_status` | bigint | До approve может быть `<6`, approve ставит `6`. Уже используется (`saveToDb` -> `getDiff`). |
| `news_available_announced` — хранится в `player_readiness_flags` (JSON) как `newsAvailableAnnounced: boolean` | JSONB | Через `markNewsAvailableIfReady()` проставляется `true` при idStatus≥6 + готовых стемах. Серверная детекция `false→true` в `MainController.doChangeRecords` создаёт новость. |
| `id_telegram_demo` | text | После успешной публикации в Telegram заполняется `TelegramAutoPublishService`. Используется как idempotency-маркер. |
| `telegram_auto_publish_state` — JSON-ключ в `player_readiness_flags` | JSONB | `SCHEDULED`/`RENDERING`/`PUBLISHING`/`PUBLISHED`/`SEND_FAILED`/`CANCELLED`. |
| `date_time_publish` (`publish_date` + `publish_time`) | text, text | Используется в `publishToTelegram` для проверки «опоздавшая». |

### 2.2 `tbl_processes`

| Поле | Тип | Использование |
|---|---|---|
| `process_type` | text | Значение `'RENDER_MP4_DEMO'` для нашего нового процесса. |
| `process_status` | text | `WAITING`→`WORKING`→`DONE`/`ERROR`, выставляется воркером. |
| `song_id` | bigint | Ссылка на `tbl_songs.id`. Используется в гарде «уже есть активный процесс». |
| `priority`, `thread_id`, `args`, `envs`, … | разные | Заполняются по образцу существующих render-задач. |

### 2.3 `tbl_news`

| Поле | Тип | Использование |
|---|---|---|
| `category` | text | Значение `'premium'` для новости «появилась в коллекции». Серверная `News.createNew(...)`. |
| `title`, `body` | text, text | Формируются `SongReleaseAnnouncementService` через `NewsTemplateService`. |
| `id_song` | bigint | Ссылка на `tbl_songs.id`. |

### 2.4 `tbl_pictures`, `tbl_authors`, `tbl_albums`

Стандартные поля сущностей. Hash-sync через `SyncTarget` (изменения по
recordhash). Из фичи мы только инициируем их sync — содержимое не правим.

---

## 3. State transitions

### 3.1 Approve-flow состояние `tbl_songs`

```text
            ┌──────────────────┐
            │  id_status < 6   │
            │ newsAvailable=false│
            └──────────────────┘
                       │
                  approve click
                       │
                       ▼
            ┌──────────────────┐   ┌──────────────────────────┐
            │  id_status = 6   │   │  saveToDb()              │
            │  (внутри)        │──▶│ markNewsAvailableIfReady │
            └──────────────────┘   │ newsAvailable=false→true│
                                    └──────────────────────────┘
                                                │
                                                ▼
                                    ┌──────────────────────────┐
                                    │ updateRemoteSongFromLocal│
                                    │ Database(id)             │
                                    │  → tbl_songs INSERT      │
                                    │   (recordhash diff)      │
                                    └──────────────────────────┘
                                                │
                                                ▼
                            ┌────────────────────────────────────┐
                            │ MainController.doChangeRecords     │
                            │  → detectAndAnnounceAvailability   │
                            │  → create tbl_news category=premium│
                            └────────────────────────────────────┘
```

### 3.2 RENDER_MP4_DEMO состояние

```text
approve click
    │
    ▼
[гард] SELECT process_status IN ('WAITING','WORKING')
    │
    ├── есть ──▶ skip
    │
    └── нет ──▶ createProcess(RENDER_MP4_DEMO, prior=5, threadId=0, doWait=false)
                │
                ▼
            tbl_processes.process_status = 'WAITING'
                │
                ▼ (KaraokeProcessWorker забирает)
            process_status = 'WORKING' → subprocess renderFrames+jpegMux
                │
                ├── success ──▶ 'DONE' → post-hook publishToTelegram → idTelegramDemo
                │
                └── error ──▶ 'ERROR' → на следующем тике scheduler попробует снова
```

### 3.3 Telegram публикация состояние

```text
publishToTelegram(song)
    │
    ▼
[song.idTelegramDemo.isNotEmpty()]──▶ skip (PUBLISHED)
    │
    ▼
[song.isContentReady == false]──▶ skip (SCHEDULED)
    │
    ▼
[demoFile.exists() AND size<=limit]
    │
    ├── да ──▶ publishFile → sendVideo → idTelegramDemo = msg_id → state=PUBLISHED
    │
    └── нет ──▶ startRenderAndReturn → state=RENDERING, новый RENDER_MP4_DEMO-процесс
                                          │
                                          └── пост-хук потом publishFile заново
```

### 3.4 News состояние (серверная сторона, спецификация 101)

```text
detectAndAnnounceAvailability(songId, wasAvailableBefore)
    │
    ├── wasAvailableBefore == true ──▶ skip (переход уже был)
    │
    ├── wasAvailableBefore == false AND now == true AND no existing news ──▶ create tbl_news
    │
    └── wasAvailableBefore == false AND now == false ──▶ skip
```

---

## 4. Validation rules

(Действуют уже сегодня; фича их **не изменяет**. Для полноты картины —
зафиксированы здесь.)

| Правило | Где валидируется | Что делает фича |
|---|---|---|
| `id_status >= 6` необходимо для `newsAvailableAnnounced` | `markNewsAvailableIfReady` в `Song.kt:5103` | approve уже выставляет `id_status=6`. |
| Содержимое готово: стемы + маркеры | `Song.isContentReady` | approve сам ставит только при наличии разметки; в противном случае естественный гард — отдельно (см. spec/094). |
| DEMO-файл ≤50 МБ | `telegramAutoPublishMaxFileSizeMb` в `TelegramAutoPublishService.publishToTelegram` | Используется как есть. |
| Один `RENDER_MP4_DEMO` на песню одновременно | Гард SELECT (см. D-3 в research.md) | Добавляем наш гард в approve. |
| Нет дубля Telegram-поста | `idTelegramDemo` непуст → skip в `publishToTelegram` | Используется как есть. |
| Sync бинарно-идемпотентен (recordhash) | `tbl_songs`/`tbl_settings` recordhash-триггер | Используется как есть — синк всегда даёт тот же результат при повторе. |

---

## 5. Связи между сущностями

```text
Song (tbl_songs) ─── id ───┐
                           ├──< tbl_pictures.picture_id
                           │
                           ├──< tbl_authors.id (song_author_id)
                           │
                           ├──< tbl_albums.id (song_album_id)
                           │
                           └──< tbl_processes.song_id
                                   │
                                   └── process_type = 'RENDER_MP4_DEMO'
                                       → state = WAITING/WORKING/DONE/ERROR

MainController.doChangeRecords (server-side)
   │
   └── tbl_songs rows UPDATE
       └── SongReleaseAnnouncementService.detectAndAnnounceAvailability
           └── tbl_news row CREATE (category='premium', title='Песня в коллекции: ...')

TelegramAutoPublishService.publishToTelegram
   │
   └── HttpRequest POST {TELEGRAM_BOT}/sendVideo
       └── tbl_songs.id_telegram_demo = msg_id (UPDATE, recordhash-tracked)
```

---

## 6. Что НЕ входит

- Никаких **новых таблиц**.
- Никаких **новых колонок** в `tbl_songs`/`tbl_processes`/`tbl_news`.
- Никаких **изменений `recordhash`-триггеров** (Principle II конституции).
- Никаких изменений **DTO** (полей/имен JSON): фронт продолжает получать
  тот же набор полей `tbl_songs` через `SongDTO`/`SongPublicDto`/`SongsTable`.
- Никаких новых **SSE-сообщений** с новой семантикой; используются
  существующие `SseNotification.recordChange`/`message`/`crud`/`playerStatus`.

Все следствия фичи наблюдаются через **уже существующие потоки**:
новый `tbl_processes` row виден в `/processes` Vue-таблице, новый
`tbl_news` row виден на `/news` и через Vuex `stats.newsBadge`.
