# Component: Song (главная entity)

> **Домен**: [catalog](../domain.md)
> **Компонента**: детальное описание `Song` — главной entity проекта.


## Ответственность | Responsibility


детальное описание `Song` — главной entity проекта.

## Файл

`karaoke-app/.../model/Song.kt` (~1500 строк, включая companion).

## Назначение

**Главная сущность Karaoke** — песня (одна запись в `tbl_songs`).
Всё крутится вокруг Song: рендер видео, MLT-генерация, async-очередь,
two-DB sync, поиск lyrics, авто-публикация.

## Интерфейсы и Контракты | Interfaces and Contracts

### Реализует

- **`KaraokeDbTable`** — reflection-loader (`saveToDb` через diff,
  `loadListFromDb` / `loadFromDbById` через SELECT).
- **`KaraokeStorage`** — `storageBucketName = "karaoke"`,
  `storageFileName = "$author/$year - $album/$fileName"`,
  `storageFileNamePreview = ""`.
- **`Comparable<Song>`** — `compareTo` по `sortString`.
- **`Serializable`**.

### Хранит (контракт данных)

**Метаданные**:

- `id`, `name`, `author`, `year`, `album`.
- `songType` (song/instrumental/poetry).

**Аудио**:

- `sourceFileName`, `vocalsFileName`, `accompanimentFileName`, `mixFileName`.

**Контент**:

- `sourceText` (оригинальный текст).
- `translatedText` (перевод).
- `chordsText` (аккорды).

**Параметры рендера**: **~150 полей** из
[KaraokeProperties](../../processing/components/karaoke-properties.md)
(шрифты, цвета, отступы, тайминги, горизонт, watermark, и т.д.).

**Состояние пайплайна**:

- `idStatus` (0..7) — см. [dictionaries.md#idstatus](dictionaries.md).
- `lastUpdate` — последнее обновление.
- Флаги ремонта: `isAudioAnalizeNeed`, `isMelodyNeed`, и т.п.
- `free` — «всегда бесплатно», см. [dictionaries.md](dictionaries.md).
- `freeAfterOnAir` — «не снимать с эфира» после стандартного окна доступа
  (Pass 369, OpenProject #81). См.
  [dictionaries.md#флаг-freeafteronair--не-снимать-с-эфира-pass-369-openproject-81](dictionaries.md).
- `isExclusive` — «premium-only по бизнес-решению».

**Внешние ссылки**:

- Telegram: `id_telegram_*`.
- VK: `id_vk_*`.
- SponsorBoard: `*`.

**Player readiness** (Pass 341 P0): `stemAccompanimentReady`,
`stemVocalReady`, `pictureAlbumReady`, `pictureAuthorReady` — 4 флага
от `reconcilePlayerReadinessFlags` (см.
[health-report.md](../../health/components/health-report.md#reconcileplayerreadinessflags)).

**Прочее**: `tags: String`, `fields: MutableMap<SongField, String>`
(вычисляемые/производные поля по `SongField`), `firstSongInAlbum: Boolean`,
`sortString` = `"author - year - album - %3d(track)"`.

### JSON-сериализация (Jackson)

`@JsonIgnoreProperties(value = ["database", "storageService",
"pictureAuthor", "pictureAlbum"])` — при сериализации эти поля исключаются
(transient/runtime). **`Kotlin val isX: Boolean` сериализуется как `"x"`**
(без `is`) — см. DEVELOPMENT.md и CLAUDE.md.

### Методы

**Инстанс-методы (создание процессов)**:

- `createProcessDemux2(threadId, prior)` / `createProcessDemux5(...)` —
  `KaraokeProcess.createProcess(song = this, action =
  KaraokeProcessTypes.DEMUCS2 / DEMUCS5, doWait = true)`.
- `createProcessVideo720(songVersion, threadId, prior)` —
  `KaraokeProcessTypes.FF_720_KAR` / `FF_720_LYR`.
- `save()` — алиас `saveToDb()`.
- `saveToDb()`, `saveToDbLocked(): Boolean` — см. «Логика».
- `deleteFromDb(...)`.

Остальные типы процессов (`KEY_BPM_FROM_FILE`, `SYMLINK`, `SMARTCOPY`,
`UPLOAD_TO_LOCAL_STORE`, `UPLOAD_TO_REMOTE_STORE`,
`FF_MP3_ACCOMPANIMENT` / `FF_MP3_VOCAL`, ...) не обёрнуты отдельными
методами — они создаются внутри `Song.createFromPath()` и шагов пайплайна
(см. [async-process-queue.md](../../processing/components/async-process-queue.md)).

**Статические методы (`companion object`)**:

- `createFromPath(...)`, `loadListFromDb(...)`, `loadFromDbById(...)`,
  `loadFromDbByIdForUpdate(...)`, `loadListFromDbByIds(...)`,
  `loadListIds(...)`, `loadListAuthors(...)`, `loadListAlbums(...)`,
  `deleteFromDb(...)`, `listHashes(...)`, `getWhereList(...)`.

## Логика и Алгоритмы | Logic and Algorithms

### `saveToDb()` vs `saveToDbLocked()` (Pass 357)

- **`saveToDb()`** — обычный путь сохранения через reflection-diff. При
  `readonly` — выход; при `id == 0L` — INSERT-ветка (там же baseline
  `song_name_censored`, specs/277). Подходит для коротких эндпоинтов
  (объект `song` живёт < 100 мс).
- **`saveToDbLocked()`** — атомарная защита от race condition через
  `SELECT ... FOR NO KEY UPDATE` + UPDATE в одной транзакции. Используется
  для долгих процессов (KEY_BPM_FROM_FILE, DEMUCS2, Sheetsage, поиск
  текстов, импорт файлов из папки), где параллельная ручная правка через
  `SongEdit.vue` может перезатереть данные. Добавлен в
  [Pass 299](../../../../specs/299-song-fields-overwrite-race-condition/spec.md),
  расширен в [Pass 357](../../../../specs/357-folder-import-overwrite/spec.md) —
  30+ мест переведены. Внутри пишет WARN `song.locked_save_diff_overlap` в
  `infra.prod.ping` лог при обнаружении расхождения между in-memory и БД
  (операционная диагностика). При `readonly`, `id == 0L` или отсутствии
  соединения — fallback на `saveToDb()`.

### Хук аудио-потомков (#141, specs/413)

Обе ветки сохранения вызывают `SyncAudioDescendants.onSongSaved(...)` после
успешного UPDATE (`Song.kt:5521`, `5715`). `saveToDbLocked()` подавляет
pre-commit вызов через `suppressTriggers` и повторяет хук после `commit()`,
чтобы воркер синхронизации читал уже зафиксированный статус родителя. См.
[audio-descendant-sync](audio-descendant-sync.md).

### Автопостановка премиум-публикации в очередь

`saveToDb()` при `newsPremiumPublishPending == false` и
`premiumAutoPublishState` пустом или `"RUNNING"` ставит публикацию в очередь,
если одновременно: `idTelegramDemo.isEmpty()`, `idVk.isEmpty()`,
`idStatus == 6L`, `stemAccompanimentReady`, `stemVocalReady`,
`pictureAlbumReady`, `pictureAuthorReady`, `sourceMarkersList.isNotEmpty()` —
тогда `newsPremiumPublishPending = true`, `premiumAutoPublishState =
"RUNNING"`, `premiumAttemptCount = 0`.
Уже завершённая (`COMPLETE`) или окончательно проваленная (`FAILED`)
публикация повторно не переустанавливается.

## Hot paths

- **`POST /api/songs`** — на каждое изменение фильтра/страницы.
- **`POST /api/song`** (`id`) — на каждое открытие карточки.
- **`POST /api/song/update`** — при редактировании метаданных.
- **HealthReport** — 4 `*_ready` флага обновляются
  `reconcilePlayerReadinessFlags`.

## Зависимости | Dependencies

- **HealthReport** ([health-report.md](../../health/components/health-report.md)) —
  `Song.save()` для смены `idStatus`.
- **Async Process Queue** ([async-process-queue.md](../../processing/components/async-process-queue.md)) —
  `KaraokeProcess.createProcess(song=this, ...)`.
- **Two-DB sync** ([two-db-sync.md](../../processing/components/two-db-sync.md)) —
  Song — основная syncable-сущность.
- **MLT** ([mlt-generator.md](../../rendering/components/mlt-generator.md)) —
  входные данные для MLT.
- **KaraokeProperties** ([karaoke-properties.md](../../processing/components/karaoke-properties.md)) —
  150 параметров.

## Известные TODO

- [ ] **Каждое из ~150 полей** — детальное описание (Pass 343+).
- [ ] **Nullable-колонки** в БД vs Kotlin-поля — соответствие.
- [ ] **`fields: Map<SongField, String>`** — все поля где используются.

## Changelog

- **Pass 483** (2026-09-27, spec `483-knowledge-domain-catalog`): секции приведены к шаблону компонента. Автор: agent (Karaoke).
- **Pass 384** (2026-09-09): Initial. Автор: agent (Karaoke).
