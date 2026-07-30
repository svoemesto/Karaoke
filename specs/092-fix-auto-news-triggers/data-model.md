# Data Model: Триггеры авто-новостей независимо от синхронизации + альбом/год в тексте

## Схема БД — без изменений

Эта фича **не добавляет и не меняет** ни одной таблицы/колонки. Полностью переиспользуется схема,
введённая `specs/089-auto-news-song-release`:

- `tbl_news.song_id` / `tbl_news.source` — без изменений.
- `tbl_song_news_announced` (`song_id` PK, `news_id` nullable, `created_at`) — без изменений, теперь
  используется тремя вызывающими путями вместо одного, но сама таблица и её инварианты
  (`PRIMARY KEY(song_id)`, идемпотентность через `ON CONFLICT DO NOTHING`) не меняются.

Никакой новой SQL-миграции для `deploy/karaoke-db/` эта фича не требует.

## Переиспользуемые сущности (без изменений)

- **`Song`** (`tbl_songs`) — используются уже существующие read-only свойства:
  - `isPubliclyWatchable: Boolean` — критерий готовности (specs/089), не меняется.
  - `album: String` — `fields[SongField.ALBUM] ?: ""`, пустая строка если не заполнено.
  - `year: Long` — `fields[SongField.YEAR]?.toLongOrNull() ?: 0L`, `0L` если не заполнено.
- **`News`** / `News.createAutoAnnouncement(...)` — сигнатура не меняется; меняется только
  содержимое передаваемых `title`/`body` строк (см. ниже), формируемых вызывающим кодом
  (`SongReleaseAnnouncementService`), а не самим `createAutoAnnouncement`.
- **`SongNewsAnnounced`** (`tbl_song_news_announced`) — используется как есть, без изменений API.
- **`SyncResult`** (`data class`, `Utils.kt`) — уже существующий тип возврата
  `updateRemoteSongFromLocalDatabase(id): SyncResult` (`created`/`updated`/`deleted`/`moved`,
  списки имён затронутых записей). Используется новым кодом в `approve()` как сигнал «push реально
  применился» — непустой `created` ИЛИ `updated` означает, что запись песни действительно дошла до
  `SERVER`, и есть смысл вызывать `checkAndAnnounce` на `Connection.remote()`.

## Изменённое поведение (без изменения схемы/сигнатур)

### `SongReleaseAnnouncementService.checkAndAnnounce(database, storageService, storageApiClient)`

Сигнатура не меняется. Внутреннее изменение — построение `title`/`body`, передаваемых в
`News.createAutoAnnouncement(...)`:

```text
было:
  title = "Новая песня: ${song.author} — ${song.songName}"
  body  = "Стала доступна песня «${song.songName}» (${song.author})."

становится (пример, точная пунктуация — на этапе реализации):
  title = "Новая песня: ${song.author} — ${song.songName}" + [альбом/год, если заполнены]
  body  = "Стала доступна песня «${song.songName}» (${song.author})" +
          [", альбом «...»" если album заполнен] +
          [", ГГГГ" если year > 0] + "."
```

Правило пустых значений: см. research.md, п.6 — ни одна из четырёх комбинаций (альбом+год / только
альбом / только год / ничего) не должна давать пустых плейсхолдеров или висящей пунктуации.

### Новые вызывающие точки (не новые сущности — новые call sites уже существующей функции)

1. **`SongReleaseAnnouncementScheduler.checkOnAir()`** (новый класс, `karaoke-web`) —
   `@Scheduled(fixedDelay = 5 * 60_000L, ...)` → `SongReleaseAnnouncementService.checkAndAnnounce(WORKING_DATABASE, storageService, storageApiClient)`.
   Ничего не возвращает наружу (как и `StatsCacheScheduler.refreshHourly()`) — побочный эффект,
   ошибки логируются и не прерывают следующий тик (тот же паттерн `try/catch`, что уже используется
   в `MainController.doChangeRecords` для этого же вызова).

2. **`SongEditorController.approve()`** (`karaoke-app`) — после существующего блока
   `if (Karaoke.allowUpdateRemote) { ... updateRemoteSongFromLocalDatabase(settings.id) ... }`:
   если возвращённый `SyncResult` показывает реальное изменение (`created.isNotEmpty() || updated.isNotEmpty()`),
   дополнительно вызывается `SongReleaseAnnouncementService.checkAndAnnounce(Connection.remote(), KSS_APP, SAC_APP)`.
   Обёрнуто в `try/catch` (как и оригинальный вызов в specs/089) — сбой детекции анонса не должен
   откатывать уже совершённый апрув.

## State transitions

Не меняются относительно specs/089 (см. `specs/089-auto-news-song-release/data-model.md`, раздел
«State transitions») — эта фича не вводит новых состояний, только новые пути обнаружения перехода
«публично доступна, анонс не создан» → «анонс создан».
