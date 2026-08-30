---
status: Active
slug: 278-fix-key-loss-on-lyrics-search
related:
  - ../domain/processing.md
  - ../domain/catalog.md
  - ../architecture/L3-components.md
  - ../../specs/278-fix-key-loss-on-lyrics-search/spec.md
---

# 278 — Race condition: тональность/стемы теряются при синхронном поиске текста (LiveDoc)

> Drill-down — [specs/278-fix-key-loss-on-lyrics-search/spec.md](../../specs/278-fix-key-loss-on-lyrics-search/spec.md).

## Что делает

При добавлении файлов из папки через UI «Добавить файлы из папки» (`/api/utils/createfromfolder`)
параллельный процесс `KEY_BPM_FROM_FILE` (и `DEMUCS2`), поставленный в очередь
`KaraokeProcessWorker` из `Song.createFromPath()`, успевал найти тональность и
записать `song_tone`/`song_bpm` в БД. Но синхронный `findYandexSongLyrics`
(Playwright, 10-60 сек) после возврата вызывал `newSong.saveToDb()` на **stale
in-memory объекте** (key="", bpm=0) — `Song.saveToDb()` через `getDiff(this,
savedSong)` включал `song_tone` в UPDATE и перезатирал найденную тональность
пустым значением. Оператору приходилось вручную повторно запускать определение
тональности для каждой импортированной песни.

**Фикс — reload-from-db-before-save**: перед каждой из трёх точек `saveToDb()` в
`doCreateFromFolder` (после `findYandexSongLyrics`, в `applyDuplicateOriginal`,
в `applyAudioParentMarkers`) объект `Song` перезагружается через
`Song.loadFromDbById(id, WORKING_DATABASE, ...)` с fallback на старый объект
при null. Перезагруженный объект содержит актуальное состояние БД (включая
параллельно записанные `key`/`bpm`/url'ы стемов), `getDiff()` видит только
реальные изменения, и UPDATE ничего лишнего не перезатирает.

## User Stories (краткий список)

- **US1** (P1): Тональность (`song_tone`/`song_bpm`), найденная
  `KEY_BPM_FROM_FILE` раньше, чем завершился синхронный поиск текста, НЕ
  теряется при `saveToDb()` после `findYandexSongLyrics`.
- **US2** (P2): URL'ы стемов, найденные `DEMUCS2`, не теряются аналогичным
  образом (тот же паттерн защиты).

## Functional Requirements (указатель)

- **FR-001**: Reload `Song` из БД перед `saveToDb()` после `findYandexSongLyrics`
  в `ApiController.doCreateFromFolder`.
- **FR-002**: Аналогичный reload в `applyDuplicateOriginal` и
  `applyAudioParentMarkers` (`Utils.kt`).
- **FR-003**: Reload через `Song.loadFromDbById(id, database, storageService,
  storageApiClient)` — тот же паттерн, что уже используется в самом
  `Song.saveToDb()`.
- **FR-004**: Сам `Song.saveToDb()` НЕ модифицируется — используется в 46+
  других местах, проверенных на проде.

## Acceptance Criteria

- [x] **AC1**: При импорте 3+ файлов из папки, для которых `KEY_BPM_FROM_FILE`
  успевает отработать за время `findYandexSongLyrics`, в `tbl_songs`
  `song_tone`/`song_bpm` остаются заполненными.
- [x] **AC2**: Никаких новых записей `KEY_BPM_FROM_FILE` в `tbl_processes` для
  песен, где процесс уже отработал и текст найден.
- [x] **AC3**: Покрыты все 3 точки `saveToDb()` в `doCreateFromFolder`
  (`applyDuplicateOriginal`, `applyAudioParentMarkers`, блок после
  `findYandexSongLyrics`).
- [x] **AC4**: Регрессий в других 46 точках вызова `Song.saveToDb()` нет.

## Связанные LiveDocs

- Domain: [processing.md](../domain/processing.md) (Async process queue),
  [catalog.md](../domain/catalog.md) (Song entity)
- Architecture: [L3-components.md](../architecture/L3-components.md) (Queue + Async)

## Код

- Backend: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt:5461`
  — фикс в `doCreateFromFolder` (блок после `findYandexSongLyrics`).
- Backend: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt:4528` —
  фикс в `applyDuplicateOriginal` и `applyAudioParentMarkers`.
- Backend: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt:5169`
  — сам `saveToDb()` НЕ модифицируется (используется в 46+ местах).

## История

- Создан: 2026-08-30
- Последнее обновление: 2026-08-30
