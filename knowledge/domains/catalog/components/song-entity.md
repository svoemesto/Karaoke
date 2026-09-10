# Component: Song (главная entity)

> **Домен**: [catalog](../domain.md)
> **Компонента**: детальное описание `Song` — главной entity проекта.

## Файл

`karaoke-app/.../model/Song.kt` (~1500 строк, включая companion).

## Назначение

**Главная сущность Karaoke** — песня (одна запись в `tbl_songs`).
Всё крутится вокруг Song: рендер видео, MLT-генерация, async-очередь,
two-DB sync, поиск lyrics, авто-публикация.

## Хранит (полный набор полей)

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

**Внешние ссылки**:

- Telegram: `id_telegram_*`.
- VK: `id_vk_*`.
- SponsorBoard: `*`.

**Player readiness** (Pass 341 P0): `stemAccompanimentReady`,
`stemVocalReady`, `pictureAlbumReady`, `pictureAuthorReady` — 4 флага
от `reconcilePlayerReadinessFlags` (см.
[health-report.md](../../health/components/health-report.md#reconcileplayerreadinessflags)).

## Реализует

- **`KaraokeDbTable`** — reflection-loader (`saveToDb` через diff,
  `loadListFromDb` / `loadFromDbById` через SELECT).
- **`KaraokeStorage`** — `storageBucketName = "karaoke"`,
  `storageFileName = "$author/$year - $album/$fileName"`.
- **`Comparable<Song>`** — сортировка по `SongField`.

## JSON-сериализация (Jackson)

- При сериализации `database`, `storageService`, `pictureAuthor`,
  `pictureAlbum` исключаются (transient/runtime).
- **`Kotlin val isX: Boolean` сериализуется как `"x"`** (без `is`) —
  см. DEVELOPMENT.md и CLAUDE.md.

## Хот-патчи

- **`/api/songs/list`** — на каждое изменение фильтра/страницы.
- **`/api/songs/getById`** — на каждое открытие карточки.
- **`/api/songs/save`** — при редактировании метаданных.
- **HealthReport** — 4 `*_ready` флага обновляются
  `reconcilePlayerReadinessFlags`.

## Методы

- `createProcessDemux2/5(threadId, prior)` — создать задание Demucs
  (см. [async-process-queue.md](../../processing/components/async-process-queue.md)).
- `createProcessMelt*` — создать MLT-рендер.
- `createProcessUploadToRemoteStore` — загрузить в remote MinIO.
- И ещё ~10 типов (см. async-process-queue.md).

### `saveToDb()` vs `saveToDbLocked()` (Pass 357)

- **`saveToDb()`** — обычный путь сохранения через reflection-diff. Подходит для
  коротких эндпоинтов (объект `song` живёт < 100 мс).
- **`saveToDbLocked()`** — атомарная защита от race condition через
  `SELECT ... FOR NO KEY UPDATE` + UPDATE в одной транзакции. Используется для
  долгих процессов (KEY_BPM_FROM_FILE, DEMUCS2, Sheetsage, поиск текстов,
  импорт файлов из папки), где параллельная ручная правка через `SongEdit.vue`
  может перезатереть данные. Добавлен в [Pass 299](../../../../specs/299-song-fields-overwrite-race-condition/spec.md),
  расширен в [Pass 357](../../../../specs/357-folder-import-overwrite/spec.md) — 30+ мест
  переведены. Внутри пишет WARN `song.locked_save_diff_overlap` в `infra.prod.ping`
  лог при обнаружении расхождения между in-memory и БД (операционная диагностика).

## Связь с другими компонентами

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

- **Pass 384** (2026-09-09): Initial. Автор: agent (Karaoke).