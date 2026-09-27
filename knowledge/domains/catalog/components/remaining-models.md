# Component: remaining-models

> **Домен**: [catalog](../domain.md)
> **Компонента**: оставшиеся модели karaoke-app, не покрытые
> [entities-catalog.md](entities-catalog.md).

## Ответственность

Детальное описание моделей, которые НЕ являются DB-entity в полном
смысле (нет `KaraokeDbTable`), но являются критическими DTO
с реальной логикой.

---

## Интерфейсы и Контракты | Interfaces and Contracts

### `StemJob`

**Файл**: `karaoke-app/.../model/StemJob.kt`. **Таблица**: `tbl_stem_jobs`.
Живёт **только на PROD-БД** (в `SyncRegistry.all` не зарегистрирован —
по образцу `SiteChatMessage`).

**Контракт класса**: `Serializable, Comparable<StemJob>, KaraokeDbTable`;
`toDTO() → StemJobDto`.

**Поля** (по `@KaraokeDbTableField`):

- `id BIGINT PK`
- `site_user_id BIGINT NOT NULL` — FK на `SiteUser`
- `mode VARCHAR NOT NULL` — `StemJobMode.DEMUCS2` / `DEMUCS5` (дефолт `DEMUCS2`)
- `status VARCHAR NOT NULL` — `StemJobStatus.WAITING` / `WORKING` / `DONE` / `ERROR` (дефолт `WAITING`)
- `original_file_name VARCHAR` — имя файла
- `original_ext VARCHAR` — расширение
- `file_size_bytes BIGINT` — размер
- `error_message` — текст ошибки
- `created_at TIMESTAMP` (`useInDiff=false`, ставится в коде в `createNew`)
- `started_at`, `finished_at TIMESTAMP`
- `expires_at TIMESTAMP` — срок жизни (выставляется только при `status=DONE` как `now()+24h`)
- `delete_requested BOOLEAN DEFAULT false` — пользователь нажал «удалить»
- `last_update TIMESTAMP` (`useInDiff=false`)

**Константы** (`companion object`):

- `TABLE_NAME = "tbl_stem_jobs"`
- `MAX_ACTIVE_JOBS_PER_USER = 5` — лимит одновременно активных (`WAITING`/`WORKING`) заданий
- `MAX_FILE_SIZE_BYTES = 50 * 1024 * 1024`
- `MAX_DURATION_SECONDS = 60 * 60`
- `RETENTION_HOURS = 24` — срок жизни готовых стемов в хранилище после `DONE`
- `ALLOWED_EXTENSIONS = {mp3, wav, flac, ogg, m4a, aac, wma, opus, aiff}`

**Синглтоны-константы** (не enum, а `object` со строковыми значениями):

- `StemJobStatus.{WAITING, WORKING, DONE, ERROR}`
- `StemJobMode.{DEMUCS2, DEMUCS5}`
- `StemJobMode.stemNames(mode): List<String>` — `DEMUCS5` → `[accompaniment, vocals, drums, bass, other]`, иначе → `[accompaniment, vocals]`. Используется и для путей в MinIO, и для валидации `?stem=` при скачивании.

**Статические методы**:

- `createNew(siteUserId, mode, originalFileName, originalExt, fileSizeBytes, database, storageService, storageApiClient): StemJob?` — заполняет поля, ставит `status=WAITING`, `createdAt=now()` и делает INSERT через `KaraokeDbTable.createDbInstance`.
- `getById(id, ...): StemJob?` — `KaraokeDbTable.loadById`.
- `loadByUser(siteUserId, ...): List<StemJob>` — задания пользователя, свежие сверху (`sortedByDescending { it.id }`).
- Выборка всех заданий для админ-панели (см. `StemJobsAdminController.kt`).

### `Publication`

**Файл**: `karaoke-app/.../model/Publication.kt`.

`class Publication(val database: KaraokeConnection) : Serializable, Comparable<Publication>`.
**НЕ `KaraokeDbTable`**: ни одного `@KaraokeDbTableField`; имя
`tbl_publications` встречается только в KDoc, таблицы в SQL-миграциях нет.

**Поля**:

- `id: Int?`, `publishDate: String?`
- `publish10 … publish23: Song?` — **14 слотов** (10..23).

**Производные**: `publish10text … publish23text: String` — подпись слота
вида `"[author] <marker> songNameCensored"`, где `<marker>` — звёздочка,
добавляемая при `firstSongInAlbum`; `""` для пустого слота.

**DTO** (в том же файле): `PublicationDTO(id, publishDate,
publish10 … publish23: SongDTO?, publish10text … publish23text: String)`.

### `Zakroma`

**Файл**: `karaoke-app/.../model/Zakroma.kt`.

`class Zakroma(val database: KaraokeConnection) : Serializable, Comparable<Zakroma>`.
Поля агрегата: `author`, `picture`; альбомы — `ZakromaAlbum` с песнями
`ZakromaAlbumSong`.

**Статические методы**:

- `getZakroma(author, database, storageService, storageApiClient, onlyPublished = false, canSeeSkipped = false, albumId: Long? = null): List<Zakroma>` — все песни автора, сгруппированные по альбомам.
- `getZakromaBySpecialOrder(database, storageService, storageApiClient, onlyPublished = false, canSeeSkipped = false, albumId: Long? = null): List<Zakroma>` — спецзаказные авторы (`is_special_order=true`) одним SQL-запросом через `author_in`.

### `EventTypes`

**Файл**: `karaoke-app/.../model/EventTypes.kt`. Содержит **5 enum-классов**
с явным строковым `dbValue` (не `.name`/`.ordinal` — 250k+ уже сохранённых
строк остаются источником истины без миграции данных):

- `EventType`: `CALL_REST("callRest")`, `CLICK_TO_LINK("clickToLink")`, `PLAY("play")` (legacy — видео на странице песни, НЕ онлайн-плеер), `PLAYER("player")`, `ENGAGEMENT("engagement")`, `UI("ui")`.
- `UiAction` (подтипы `EventType.UI`): `NAVIGATE("navigate")`, `THEME("theme")`, `SCROLL("scroll")`.
- `RestName`: `MAIN("main")`, `ZAKROMA("zakroma")`, `FILTER("filter")`, `SONG("song")`.
- `LinkType`: `LINK_TO_SOCIAL_NETWORK("linkToSocialNetwork")`, `LINK_TO_SONG("linkToSong")`, `SONG_META("songMeta")` (жест разблокировки плеера — в `tbl_events` не пишется).
- `PlayerAction` (`link_type` при `EventType.PLAYER`): `SHOWN("shown")`, `PLAY("play")`, `PAUSE("pause")`, `SEEK("seek")`, `EXPORT("export")`, `PROGRESS("progress")`, `ENDED("ended")`, `OPENED("opened")`.

У каждого enum — `companion fun fromDb(value: String?): X?` (поиск по `dbValue`).

### `Messages`

**Файл**: `karaoke-app/.../model/Messages.kt`:

- `Message(type = "info", head, body)` + `companion getRecordChangeMessage(...)`
- `ProcessWorkerStateMessage(isWork, stopAfterThreadIsDone)`
- `ProcessCountWaitingMessage(countWaiting)`
- `CacheQueueSizeMessage(cacheQueueSize)`
- `HealthReportPoolCountMessage(count)`
- `HealthReportWaitingPoolSizeMessage(count)`
- `RecordDeleteMessage(recordId, tableName, databaseName)`
- `RecordAddMessage(recordId, tableName, databaseName, record: Any?)`
- `RecordChangeMessage(recordId, tableName, diffs: List<RecordDiff>, databaseName, record: Any?)` + `getSetString()` (сборка `SET`-выражения) и `toString()` (человекочитаемый дамп).

### `SongOutputFile`, `SongRenderContext`

- **`SongOutputFile`** (`karaoke-app/.../model/SongOutputFile.kt`) — **enum**, а не DTO. Значения несут `extension`: `PROJECT("kdenlive")`, `VIDEO("mp4")`, `PICTURE` / `PICTURECHORDS` / `PICTUREBOOSTY` / `PICTUREBOOSTYTEASER` / `PICTUREBOOSTYFILES` / `PICTURESPONSRTEASER` / `PICTUREVK("png")`, `SUBTITLE("kdenlive.srt")`, `DESCRIPTION` / `VK` / `TEXT("txt")`, `RUN` / `RUNALL("run")`, `MLT("mlt")`.
- **`SongRenderContext`** (`karaoke-app/.../model/SongRenderContext.kt`) — `data class SongRenderContext(song: Song, songVersion: SongVersion, woInit: Boolean = false)`. Производные члены: `propAudioVolumeOn` / `propAudioVolumeOff` / `propAudioVolumeCustom`, `hasChords`, `getOutputFilename2(songOutputFile)`, `endTimecode`, `capo`, `voices: MutableList<SongRenderVoice>`. Рядом объявлены `data class SongRenderVoice`, `enum class SongRenderVoiceLineType`, `data class SongRenderVoiceLine`, `SongRenderVoiceLineSymbol` и вложенный `SubtitleFileElement(startFrame, endFrame, text, isStartOfLine, isEndOfLine, isSetting)` — парсинг `.srt` и раскладка субтитров.

### `SongShortInfoDto`, `KaraokeDbTableDto`

- **`SongShortInfoDto`** (`karaoke-app/.../model/SongShortInfoDto.kt`): `data class SongShortInfoDto(id: Long, author: String, year: Long, album: String, songName: String) : Serializable` — минимальная инфа о песне для тултипов ячеек `root`/`A-root` в таблице песен webvue3.
- **`KaraokeDbTableDto`** (`karaoke-app/.../model/KaraokeDbTableDto.kt`) — **interface**, базовый для всех `*Dto.kt`: `isValid(): Boolean = true`, `validationErrors(): List<String> = emptyList()`, `fromDto(database: KaraokeConnection): KaraokeDbTable`. Тип возврата `KaraokeDbTable.toDTO()`.

### `SongField`, `SongVersion`, `SongState`, `SongType`

- **`SongField`** (`karaoke-app/.../model/SongField.kt`) — `enum class SongField : Serializable`. Поля строки/колонки cross-tab: `ID`, `NAME`, `NAME_CENSORED`, `AUTHOR`, `ALBUM`, `YEAR`, `TRACK`, `KEY`, `BPM`, `MS`, `FORMAT`, `AUDIOSONG`, `AUDIOMUSIC`, `AUDIOVOCALS`, `AUDIODRUMS`, `AUDIOBASS`, `DATE`, `TIME`, `BOOSTY_ONLY`, `ID_*`/`VERSION_*` (Boosty/VK/Dzen), `ID_STATUS`, `COLOR`, `SOURCE_TEXT`, `RESULT_TEXT`, `SOURCE_MARKERS`, `FORMATTED_TEXT_*` и др.
- **`SongVersion`** (`karaoke-app/.../model/SongVersion.kt`) — **4 значения**: `LYRICS`, `KARAOKE`, `CHORDS`, `TABS`. Каждое несёт `text`, `textForDescription`, `suffix`, `producers: List<ProducerType>`, `producersInMainBin`, `markertypes`.
- **`SongState`** (`karaoke-app/.../model/SongState.kt`) — **5 состояний** с цветом: `DONE("#CCFFCC")`, `TODAY("#FFFF00")`, `ON_AIR("#33FF33")`, `EXCLUSIVE("#99CCFF")`, `IN_WORK("")`.
- **`SongType`** (`karaoke-app/.../model/SongType.kt`) — `SONG("song")`, `INSTRUMENTAL("instrumental")`, `POETRY("poetry")` с `description`/`caption`.

## Логика и Алгоритмы | Logic and Algorithms

### `StemJob`: жизненный цикл (по `StemJobPollScheduler`)

1. User загружает файл через `PublicStemJobController.create` →
   `StemJob.createNew(...)` → `status = WAITING`, INSERT в
   `tbl_stem_jobs`. Файл сохраняется в temp-dir, НЕ в MinIO.
2. `StemJobPollScheduler.pollWaiting` (`@Scheduled(fixedDelay = 45_000,
   initialDelay = 30_000)`) скачивает файл с karaoke-web через
   `InternalStemJobController`, проверяет длительность (ffprobe) и создаёт
   `KaraokeProcess` с типом `KaraokeProcessTypes.STEM_JOB_DEMUCS2` /
   `STEM_JOB_DEMUCS5` в отдельном лейне
   `KaraokeProcess.THREAD_LANE_STEM_JOBS = 2` (не блокирует обычный
   пайплайн выпуска песен).
3. `KaraokeProcessWorker` → `StemJobProcessing` (Demucs) → upload в MinIO:
   `stemjobs/{id}/original.<ext>` и `stemjobs/{id}/<stem>.mp3`.
4. `StemJob.status = DONE`; `expires_at = now() + RETENTION_HOURS`.
5. User скачивает через `PublicStemJobController.download`
   (`GET /{id}/download`) — стрим из MinIO.
6. `StemJobPollScheduler.cleanup` (`fixedDelay = 5 * 60_000,
   initialDelay = 60_000`) удаляет `DONE + expiresAt < now` или
   `delete_requested = true` — единый механизм закрывает и явное удаление,
   и протухание. Только karaoke-app умеет писать в MinIO, поэтому удаление
   делает уборка, а не контроллер.

**Связь**: см.
[schedulers.md](../../../domains/processing/components/schedulers.md) +
[storage-flow.md](../../../domains/storage/components/storage-flow.md).

### `Publication`: семантика

- `publish10 … publish23` — **ежедневные публикации** песни в разных
  форматах: слот = день месяца, `publishDate` — дата публикации.
- В sync **не участвует**: класс не реализует `KaraokeDbTable` и
  отсутствует в `SyncRegistry.all` — только LOCAL, для UI/отчётов.
- Использование: cross-tab отчёты — `CrossSong.publications(...)`
  (вызовы в `ApiController.kt:2240, 2294`).

### `Zakroma.getZakroma`: правила фильтрации

- `onlyPublished = true` → в SQL-фильтр добавляется `id_status = ">=6"`.
  **NB (drift)**: KDoc метода говорит «статус готовности >= 3» — код
  использует `>=6`; при правке синхронизировать KDoc и код.
- `canSeeSkipped = false` → Kotlin-фильтр песен с тегом `SKIP`
  (`filterNot(::songHasSkipTag)`); фильтр `tbl_authors.skip` применяется
  вызывающим контроллером (`withSkiped`).
- `albumId != null` → SQL-фильтр по FK `album_id`: с бэка прилетают только
  песни этого альбома (Pass 359, spec 356).
- `getZakromaBySpecialOrder` вместо N последовательных вызовов
  `getZakroma` (N+1) грузит имена `is_special_order=true` один раз, затем
  все их песни одним запросом через `author_in`.

### `Messages` в SSE

Каждый DTO используется в `SseNotification.companion object`
(`recordChange`, `recordAdd`, `recordDelete`, `processWorkerState`,
`processCountWaiting`, `cacheQueueSize`, `healthReportPoolCount`,
`healthReportWaitingPoolSize`) — см. [sse domain](../../../domains/sse/domain.md).
`RecordChangeMessage` — часть two-DB sync: `getSetString()` собирает
`SET`-выражение из `diffs.filter { it.recordDiffRealField }`.

## Известные TODO

- [ ] `Publication` — есть ли фактическая таблица/колонки (`tbl_publications`
      упомянута только в KDoc, в SQL-миграциях отсутствует).
- [ ] `Zakroma` — полный список полей `ZakromaAlbum` / `ZakromaAlbumSong`.
- [ ] `StemJobMode.stemNames` — соответствие имён стемов `Song.argsDemucs2/5`.

## Код (физическая реализация)

Все в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/`.

## Changelog

- **Pass 483** (2026-09-27, spec `483-knowledge-domain-catalog`): секции приведены к шаблону компонента. Автор: agent (Karaoke).
- **Pass 348** (2026-09-09): Initial. Автор: agent (Karaoke).
