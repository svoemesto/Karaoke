# Component: entities-catalog

> **Домен**: [catalog](../domain.md)
> **Компонента**: детальное описание всех DB-сущностей домена
> `catalog` (Song/Album/Author и связанные). `Song`, `Album`, `Author`
> имеют отдельные компоненты, остальные — здесь.

## Ответственность

Каталог сущностей, относящихся к **контенту проекта**: песни, альбомы,
авторы, обложки, новости, словари, share-ссылки, аудитория. Каждая
сущность — полноценная статья с полями, hot paths, ловушками.

**NB**: Сущности в sync (`NewsSyncTarget`, `PicturesSyncTarget`, ...)
имеют `recordhash`-триггер. См. [processing/two-db-sync](../../../domains/processing/components/two-db-sync.md).

**NB**: Сущности, НЕ участвующие в sync, доступны только на admin-машине
(см. каждую сущность отдельно).

## Сущности каталога

- [Song](#song) — отдельный компонент (см. также `dictionaries.md`, `song-lifecycle.md`)
- [Album](#album) — отдельный компонент (см. `dictionaries.md`)
- [Author](#author) — отдельный компонент (см. `dictionaries.md`)
- [`Pictures`](#pictures) — обложки альбомов/треков
- [`News`](#news) — новости (посты в соцсети)
- [`Dictionary`](#dictionary) — словари (жанры, языки, и т.д.)
- [`SongCoAuthor`](#songcoauthor) — со-автор песни
- [`CrossSong`](#crosssong-cross-tab-helper-reporting) — НЕ entity, reporting helper (cross-table для UI)
- [`Producer`](#producer) — продюсер песни
- [`SongShareLink`](#songsharelink) — share-ссылка на песню
- [`SiteChatMessage`](#sitechatmessage) — сообщение в чате поддержки
- [`WebEvent`](#webevent) — событие вебвизора

## Сущности из других доменов

- [`ListeningHistory`](listeninghistory) — только LOCAL (см. также)
- [`SitePlaylist`, `SitePlaylistItem`](#siteplaylist--siteplaylistitem) — плейлисты
- [`SongAssignment`, `SongAssignmentDraft`](#songassignment--songassignmentdraft) — задания редактора
- [`KaraokeProcess`](#karaokeprocess) — async-очередь (см. [processing/async-process-queue](../../../domains/processing/components/async-process-queue.md))
- [`StemJob`](#stemjob) — премиум фича (см. [rendering domain](../../../domains/rendering/domain.md))
- [`SearchAsync`, `SearchResult`](#searchasync--searchresult) — async-поиск
- [`Uuids`](#uuids) — генератор UUID (не entity)

---

## Song

**См. отдельный компонент**: [dictionaries.md](dictionaries.md) (поля
+ AR), [song-lifecycle.md](song-lifecycle.md) (lifecycle).

`Song` — главная сущность проекта (18k+ записей). Реализует
`KaraokeDbTable`, участвует в sync, имеет `recordhash`-триггер.

## Album

**См. отдельный компонент**: [dictionaries.md](dictionaries.md).

`Album` — группа треков. FK на `Author`. Имеет `AlbumType`
(`STANDARD`, `TRIBUTE`, `COVER`, `ARCHIVE`, см. gaps). Sync.

## Author

**См. отдельный компонент**: [dictionaries.md](dictionaries.md).

`Author` — исполнитель/автор. Имеет FK на `Pictures` (аватарка).
Sync.

---

## `Pictures`

**Файл**: `karaoke-app/.../model/Pictures.kt`.

Менеджер картинок альбома/трека. Хранит URL'ы MinIO (НЕ base64).

**Таблица**: `tbl_pictures`. Колонки:

- `id BIGINT PK`
- `picture_name VARCHAR` (уникальное имя, используется как filename в MinIO)
- `picture_full TEXT` — `useInList=false` (большой, lazy)
- `picture_album_full TEXT`
- `picture_album_preview TEXT`
- `picture_cover_full TEXT`
- `picture_cover_preview TEXT`
- `picture_boosty_full TEXT`
- `picture_dzen_karaoke TEXT`
- `recordhash VARCHAR(32)` — для sync

**Ловушки**:

- `picture_full` setter `field = ""` — base64 не сохраняется.
- `useInList=false` MUST быть у больших полей (OOM на loadList).
- `picture_name` уникально — collision = silent overwrite.

**Sync**: `PicturesSyncTarget` (push+pull, all operations).
**CRUD**: `PicturesController` (`/api/pictures/list|...`).
**Vuex**: `webvue3/src/components/Pictures/store.js`.

См. детальный документ: [pictures.md](pictures.md).

---

## `News`

**Файл**: `karaoke-app/.../model/News.kt`.

Новость проекта (пост в Telegram/Boosty/VK).

**Таблица**: `tbl_news`. Колонки:

- `id BIGINT PK`
- `news_author_id BIGINT` — FK на `Author`
- `news_date_publicate TIMESTAMP` — дата публикации
- `news_id_telegram VARCHAR` — ID опубликованной копии в Telegram
- `news_id_boosty VARCHAR` — ID в Boosty
- `news_id_vk VARCHAR` — ID в VK
- `news_text TEXT` — текст новости
- `news_id_picture BIGINT` — FK на `Pictures`
- `news_song_id BIGINT` — FK на `Song` (для новостей «новая песня в эфире»)
- `news_category VARCHAR` — `air` / `premium` / `feature` / etc.
- `news_publish_at TIMESTAMP` — планируемая дата (как `Song.publishDate`)
- `news_is_manual BOOLEAN` — manual или auto
- `recordhash VARCHAR(32)`

**Lifecycle** (вычисляемое):

- «Опубликовано» — `publishAt <= now()`, не отдельный статус.
- Новость с будущим `publishAt` уже уехала на прод и «всплывает» в
  назначенный момент.

**Sync**: `NewsSyncTarget`.

**Автопубликация**:

- **`category=air` + `publish_at <= now()`** → триггер автопубликации в VK
  (`VkAutoPublishScheduler`).
- **Manual `air` без `song_id`** — идемпотентность через in-memory Set в
  `VkAutoPublishScheduler` (News не имеет JSON-блоба состояния, добавлять
  — избыточная миграция).

**CRUD**: `NewsController` (`/api/news/...`).
**Шаблоны**: `NewsTemplateService.kt` + `NewsTemplateController.kt` —
готовые шаблоны для `category=air|premium|feature`.

**Vuex**: `webvue3/src/components/News/store.js` + `NewsTemplates/store.js`.

**Ловушки**:

- `news_id_telegram/boosty/vk` — ссылки на **опубликованные** копии.
  Manual re-post требует сброса.
- `news_publish_at` против `news_date_publicate`: первое — план,
  второе — факт. Не путать.

---

## `Dictionary`

**Файл**: `karaoke-app/.../model/Dictionary.kt`.

Единая таблица словарей, заменяющая файловые `TextFileDictionary`.
Одна запись = одно значение одного словаря `(dict_name, dict_value)`.

**Таблица**: `tbl_dictionaries`. Колонки:

- `id BIGINT PK`
- `dict_name VARCHAR` — имя словаря (`genre`, `language`, ...)
- `dict_value VARCHAR` — значение
- UNIQUE INDEX `(dict_name, dict_value)` (см. `17_dictionaries.sql`)
- `recordhash VARCHAR(32)`

**Sync**: `DictionariesSyncTarget` (LOCAL→SERVER only, не двусторонний).

**Hot paths**:

- **`Dictionary.loadList`** — на любой странице, использующей enum-словари.
  Возвращает все значения одного словаря по `dict_name`.

**CRUD**: `DictionariesController` (`/api/dictionaries/list|create|update|delete|test`).
**Vuex**: `webvue3/src/components/Dictionaries/store.js` +
`webvue3/src/components/Dictionaries/filter/store.js`.

**Используется ВЕЗДЕ**: словари — центральный справочник для
жанров, языков, типов контента, типов файлов и т.д. См.
`catalog/components/dictionaries.md` для списка словарей.

**Ловушка**: `DictionariesController.test()` — зачем endpoint с именем
`test`? (TODO Pass 343).

---

## `SongCoAuthor`

**Файл**: `karaoke-app/.../model/SongCoAuthor.kt`. **Таблица**: `tbl_song_authors`.

**Поля** (по `@KaraokeDbTableField`):

- `id BIGINT PK` (`isId=true`)
- `song_id BIGINT NOT NULL` — FK на `Song`
- `author_id BIGINT NOT NULL` — FK на `Author`
- `recordhash VARCHAR(32)` (md5)

**Sync**: `SongCoAuthorsSyncTarget` (`SyncRegistry.all:507`).
**CRUD**: через SongEditorController (предположительно, нужно
подтверждение — Pass 343+).
## `CrossSong` (НЕ entity! это reporting helper)

**Файл**: `karaoke-app/.../model/CrossSong.kt`.

**ВАЖНО**: CrossSong — это **НЕ DB-entity**. Это **reporting/cross-tab helper**,
возвращающий данные в виде «строка × колонка × песня» (cross-table)
для UI-отчётов.

```kotlin
class CrossSong {
    companion object {
        fun publications(listOfSongs: List<Song>, rowField: SongField, columnField: SongField): List<CrossSongRow>
        fun unpublications(listOfSongs: List<Song>, columnField: SongField): List<CrossSongRow>
        fun skiped(listOfSongs: List<Song>, columnField: SongField): List<CrossSongRow>
    }
}
```

**Используется в** `ApiController.kt:2231, 2249, 2279, 2282, 2285`
для admin UI (страницы отчётов).

Связанные DTO:
- `CrossSongRow(csrId, csrName, csrCells)` — строка cross-table.
  `compareTo` использует `sortString` (если имя содержит `.`,
  сортирует по reversed parts; иначе — по `%015d` из id).
- `CrossSongCell(cscIs, cscName, songDTO?)` — ячейка.
  `compareTo` по `cscIs`.

**Нет таблицы**, **нет sync**. Зачем нужна эта структура — см.
`archive/docs/features/dual-db-sync.md` (исторически).
## `Producer` (НЕ entity!)

**Файл**: `karaoke-app/.../model/Producer.kt`.

**ВАЖНО**: Producer — это **НЕ DB-entity**. Это простой DTO, используемый
MLT-генератором:

```kotlin
@Suppress("unused")
data class Producer(
    val producerType: ProducerType,
    val groupId: Int,
    val param: MutableMap<String, Any?>,
) : Serializable
```

Поле `@Suppress("unused")` указывает, что класс сейчас не используется
(возможно, был частью старой MLT-логики, см. архив). Нет таблицы,
нет sync.

### `ProducerType` (enum, иерархия MLT-слоёв)

**Файл**: `karaoke-app/.../model/ProducerType.kt`.

**НЕ** entity — это enum иерархии слоёв MLT-проекта (~50 значений) для
построения караоке-видео. Поля: parent, text, onlyOne, ids, isAudio,
isVideo, coeffStatic, coeffVoice, isSequence, level, isCalculatedCount.

Иерархия: `MAINBIN` (level 0) → audio слои (`AUDIOVOCAL`,
`AUDIOMUSIC`, `AUDIOSONG`, `AUDIOBASS`, `AUDIODRUMS`, level 1) +
video слои (`BACKGROUND`, `HORIZON`, `FLASH`, и т.д., level 1) +
`VOICES` → `VOICE` → `COUNTERS`/`SCROLLERS`/`LINES`, и
`MAINBIN` → `CHORDSBOARD` → ... → `CHORDPICTUREIMAGE`
(глубокая иерархия до level 9).

Функция `ProducerType.childs()` возвращает прямых детей.

**Используется** в MLT-генерации (`mlt/` директория, `MltNode.kt`).
Детали — Pass 343+ в rendering domain.

---

## `SongShareLink`

**Файл**: `karaoke-app/.../model/SongShareLink.kt`. **Таблица**:
`tbl_song_share_links` (`SongShareLink.kt:93`).

**Поля** (по `@KaraokeDbTableField`):

- `id BIGINT PK`
- `owner_site_user_id BIGINT NOT NULL` — FK на `SiteUser` (владелец)
- `song_id BIGINT NOT NULL` — FK на `Song`
- `token_hash VARCHAR` — хэш токена (НЕ сам токен, безопасность)
- `active BOOLEAN` — активна ли ссылка
- `expires_at TIMESTAMP` — срок действия
- `created_at TIMESTAMP` (`useInDiff=false`)
- `revoked_at TIMESTAMP` — дата отзыва
- `revoke_reason VARCHAR` — причина отзыва
- `first_used_at TIMESTAMP` — первое использование
- `last_used_at TIMESTAMP` — последнее использование
- `active_session_token_hash VARCHAR` — хэш активной сессии
- `active_session_lease_until TIMESTAMP` — лиз до
- `active_session_browser_hash VARCHAR` — хэш браузера
- `sessions_total INT` — всего сессий
- `rejected_concurrent INT` — отклонено параллельных попыток
- `last_update TIMESTAMP` (`useInDiff=false`)
- `recordhash VARCHAR(32)` (`useInDiff=false`)

**Sync**: `ShareLinksSyncTarget` (`SyncRegistry.all:518`).

**CRUD**:

- Создание/управление: `SiteShareLinksController` (`karaoke-web`).
- Cleanup: `ShareLinkSweeper` (karaoke-web) — периодическая очистка
  expired.

**Ловушки**:

- `token_hash` (НЕ `token`) — сам токен не хранится в БД.
- `ShareLinkSweeper` должен удалять expired + revoked.
## `SiteChatMessage`

**Файл**: `karaoke-app/.../model/SiteChatMessage.kt`. **Таблица**:
`tbl_site_chat_messages` (`SiteChatMessage.kt:74`).

**Поля** (по `@KaraokeDbTableField` + raw SQL):

- `id BIGINT PK`
- `site_user_id BIGINT NOT NULL` — FK на `SiteUser`
- `is_from_author BOOLEAN NOT NULL` — true = от пользователя, false = от автора
- `body TEXT NOT NULL` — текст сообщения
- `is_read BOOLEAN NOT NULL`
- `created_at TIMESTAMP` (`useInDiff=false`)

**Sync**:  (`SyncRegistry.all:522`).

**CRUD**: `PublicChatController` (karaoke-web) — для пользователя,
`/api/chat/...` — для админа.

**Hot paths**:

- **`MONITOR_ALERTS` SSE** (см. [sse domain](../../sse/domain.md)) —
  если есть unread > 1 час → `UnreadChatMessagesCheck` (см.
  [monitor-checks](../../../domains/monitoring/components/monitor-checks.md)).
- **`unreadcount`** polling endpoint с TTL=10s (см.
  [caching/web-caches](../../../domains/caching/components/web-caches.md)).
## `WebEvent`

**Файл**: `karaoke-app/.../model/WebEvent.kt`. **Таблица**:
`tbl_events` (`WebEvent.kt:110`).

**Поля** (по `@KaraokeDbTableField`):

- `id BIGINT PK`
- `event_type VARCHAR NOT NULL` — тип события
- `rest_name VARCHAR NOT NULL` — имя REST endpoint
- `rest_parameters TEXT NOT NULL` — параметры (canonical)
- `link_type VARCHAR` — `INTERNAL` / `EXTERNAL` / `DOWNLOAD` / etc.
- `link_name VARCHAR` — имя ссылки
- `song_id BIGINT` — FK на `Song` (опционально)
- `song_version VARCHAR` — версия песни (LYRICS/KARAOKE/DEMO)
- `last_update TIMESTAMP` (`useInDiff=false`)
- `referer VARCHAR` — HTTP Referer
- `client_ip VARCHAR` — IP клиента
- `anon_id VARCHAR NOT NULL` — анонимный ID
- `site_user_id BIGINT` — FK на `SiteUser` (для логин-юзеров)
- `user_agent TEXT` — HTTP User-Agent
- `recordhash VARCHAR(32)`

**Sampling/Dedup** (см. [caching/web-caches](../../../domains/caching/components/web-caches.md)):

- **`SamplingFilter`** — отбрасывает события по sampling rate
  (1/N для admin, 1/1 для anonymous).
- **`DedupCache`** — дедуплицирует `(restName, canonical(parameters), anonId/userId)`
  за последние N секунд.
- **`EventsBuffer`** — батчит INSERT в БД.
- **`EventsRetentionScheduler`** — периодически удаляет старые события.

**Sync**: `EventsSyncTarget` (`SyncRegistry.all:519`).

**Ловушки**:

- **Размер таблицы**: миллионы записей. `recordhash` md5 считается
  на каждое событие → нагрузка на CPU. См. оптимизации в
  `EventsBuffer`.
- **GDPR / PII**: события НЕ ДОЛЖНЫ содержать персональные данные.
  См. Constitution VIII.
## Сущности из соседних доменов

### ListeningHistory

**Файл**: `karaoke-app/.../model/ListeningHistory.kt`. **Таблица**:
`tbl_listening_history` (`ListeningHistory.kt:66`).

**Поля** (по `@KaraokeDbTableField`):

- `id BIGINT PK`
- `site_user_id BIGINT NOT NULL` — FK
- `song_id BIGINT NOT NULL` — FK
- `play_count BIGINT NOT NULL DEFAULT 0` — количество прослушиваний
- `last_played_at TIMESTAMP NOT NULL`
- `last_update TIMESTAMP` (`useInDiff=false`)

**Hot path**:
- INSERT/UPDATE на каждом прослушивании (`ListeningHistory.kt:87-90` —
  `INSERT INTO ... ON CONFLICT UPDATE play_count + 1, last_played_at = now()`).

**Sync**: НЕ участвует. Только LOCAL.
**CRUD**: `ListeningHistoryController`.
**Vuex**: `webvue3/src/components/ListeningHistory/store.js`.
### SitePlaylist, SitePlaylistItem

**Файлы**: `model/SitePlaylist.kt`, `model/SitePlaylistItem.kt`.

**SitePlaylist** (`tbl_site_playlists`, `SitePlaylist.kt:84`):

- `id BIGINT PK`
- `owner_id BIGINT NOT NULL` — FK на `SiteUser`
- `name VARCHAR NOT NULL`
- `is_favorites BOOLEAN NOT NULL DEFAULT false` — системный плейлист «Избранное»
- `sort_order INT` — порядок отображения
- `continuous BOOLEAN NOT NULL` — играть без пауз
- `repeat_mode VARCHAR NOT NULL` — `NONE` / `ALL` / `ONE`
- `shuffle BOOLEAN NOT NULL` — перемешивать
- `last_update TIMESTAMP` (`useInDiff=false`)
- `recordhash VARCHAR(32)`

**SitePlaylistItem** (`tbl_site_playlist_items`):

- `id BIGINT PK`
- `playlist_id BIGINT NOT NULL` — FK
- `song_id BIGINT NOT NULL` — FK
- `position INT NOT NULL` — позиция в плейлисте
- `muted BOOLEAN NOT NULL DEFAULT false`
- `last_update TIMESTAMP` (`useInDiff=false`)
- **Transient-поля**: не аннотированы `@KaraokeDbTableField`, не пишутся в БД (см. `SitePlaylistItem.kt:56`)

**Sync**: `SitePlaylistsSyncTarget`, `SitePlaylistItemsSyncTarget`.
**CRUD**: `SitePlaylistsController`.
**Vuex**: `webvue3/src/components/SitePlaylists/store.js`.
### SongAssignment, SongAssignmentDraft

**Файлы**: `model/SongAssignment.kt`, `model/SongAssignmentDraft.kt`.

**SongAssignment** (`tbl_song_assignments`):

- `id BIGINT PK`
- `assignee_id BIGINT NOT NULL` — FK на `SiteUser` (editor)
- `song_id BIGINT NOT NULL` — FK на `Song`
- `voice INT NOT NULL` — номер голоса (0..N)
- `admin_status VARCHAR NOT NULL` — статус (см. editorial domain)
- `review_comment TEXT`
- `assigned_by BIGINT` — кто назначил
- `assigned_at TIMESTAMP NOT NULL`
- `reviewed_at TIMESTAMP`
- `last_update TIMESTAMP` (`useInDiff=false`)

**SongAssignmentDraft** (`tbl_song_assignment_drafts`):

- `id BIGINT PK`
- `assignment_id BIGINT NOT NULL` — FK
- `assignee_id BIGINT NOT NULL`
- `edited_source_text TEXT` — отредактированный текст
- `edited_markers TEXT` — отредактированные маркеры
- `user_status VARCHAR` — статус draft

**Sync**: `SongAssignmentsSyncTarget`, `SongAssignmentDraftsSyncTarget`.
**CRUD**: см. [editorial domain](../../editorial/domain.md).
**Vuex**: `webvue3/src/views/SongEditorView.vue` (нет отдельного store).

**Ловушка**:

- **`SubmittedAssignmentsCheck`** monitor: задания в `submitted` > 24ч
  → WARNING. См. [monitor-checks](../../../domains/monitoring/components/monitor-checks.md).
### KaraokeProcess

См. [processing/async-process-queue](../../../domains/processing/components/async-process-queue.md).

### StemJob

Премиум-фича «Создать минусовку». См. `rendering` domain (gaps).

### SearchAsync, SearchResult

Async-поиск текста песни.

**Поля SearchAsync**:

- `id BIGINT PK`
- `searchText TEXT` — что искали
- `status VARCHAR` — `PENDING` / `RUNNING` / `DONE` / `FAILED`
- `createdAt TIMESTAMP`
- `finishedAt TIMESTAMP` (опционально)

**SearchResult** — результаты async-поиска.

**Sync**: НЕ участвует. Только LOCAL.

**Известные TODO**: детали алгоритма async-поиска (`searchSongText` vs `searchSongText2` — см. drift в [system/utilities](../../../system/utilities.md)).

### Uuids

**Файл**: `model/Uuids.kt`.

Генератор UUID. Используется для:

- `tabId` (SSE, см. [sse domain](../../../domains/sse/domain.md)).
- `SongShareLink.token`.
- Других уникальных ID.

Не DB-entity, утилита.

---

## Архитектурные долги (Pass 343+)

1. **CrossSong, Producer** — точные поля не изучены (Pass 343+).
3. **Drift `searchSongText` vs `searchSongText2`** (см. utilities).
4. **`DictionariesController.test()` endpoint** — зачем?

## Код (физическая реализация)

- `karaoke-app/.../model/<Entity>.kt` × 24 файлов
- `karaoke-app/.../model/<Entity>Dto.kt` × 24 файлов (DTO)
- `karaoke-app/.../controllers/<Entity>Controller.kt` × 24 (предположительно)
- `webvue3/src/components/<Entity>/store.js` × 22 (Vuex)
- `karaoke-web/.../controllers/Public<Entity>Controller.kt` × ? (прод-сторона)

## Changelog

- **Pass 341 P3a** (2026-09-09): Initial detailed (catalog entities).
  Автор: agent (Karaoke).