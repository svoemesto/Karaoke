---
status: Active
slug: 277-song-name-censored
related:
  - ../../specs/277-song-name-censored/spec.md
  - ../../specs/277-song-name-censored/plan.md
  - ../../specs/277-song-name-censored/research.md
  - ../../specs/277-song-name-censored/data-model.md
  - ../../specs/277-song-name-censored/contracts/api.md
  - 277-song-name-censored-sync (миграция для tbl_songs_sync, та же фича)
---

# 277 — Предвычисленная колонка `tbl_songs.song_name_censored` (LiveDoc)

> Drill-down — [specs/277-song-name-censored/spec.md](../../specs/277-song-name-censored/spec.md),
> [plan.md](../../specs/277-song-name-censored/plan.md),
> [research.md](../../specs/277-song-name-censored/research.md).

## Что делает

Добавляет в `tbl_songs` колонку `song_name_censored VARCHAR(255) NOT NULL DEFAULT ''`,
которая хранит **предвычисленное цензурированное название песни** по словарю «Censored»
(`tbl_dictionaries`). Чтение цензурированного названия больше **не дёргает
`tbl_dictionaries`** на каждой загрузке песни — все шаблоны (VK/Telegram/News),
картинки публикаций, закро́мы и публичные DTO берут готовое значение из БД.

## User Stories (краткий список)

- **US1** (P1): Фоновый реckan всех `song_name_censored` через новый endpoint
  `/api/utils/rescanallcensorednames` (кнопка «Пересканировать цензурированные
  названия песен» в `HomeView.vue`) — пересчёт по актуальному словарю.
- **US2** (P1): Ручной ввод/правка цензурированного названия в `SongEdit.vue`
  (политика «доверие редактору», см. FR-008 и Clarifications Q1/A).
- **US3** (P1): 0 SQL-запросов к `tbl_dictionaries` на горячем пути чтения
  (`getVKGroupDescription`, `getTextBoostyHead/...`, `UtilsPictures`,
  публичные DTO, шаблоны VK/Telegram/News).
- **US4** (P1): Безопасная миграция `42_song_name_censored.sql` на LOCAL+PROD
  + отдельная миграция `43_song_name_censored_sync.sql` для `tbl_songs_sync`.

## Functional Requirements (указатель)

- **FR-001..FR-010** — спека [spec.md](../../specs/277-song-name-censored/spec.md#functional-requirements).
- Ключевые: FR-003 (baseline-автозаполнение `saveToDb()`), FR-006 (фоновый реckan),
  FR-008 (поле в SongEdit + tooltip), FR-009 (замена вызовов `song.songName.censored` →
  `song.songNameCensored`), **FR-005a** (явное сравнение в `getDiff` — bug-fix после
  применения на проде).

## Acceptance Criteria

- [ ] **AC1** (US1): Запуск `rescanAllCensoredNames()` заполняет `song_name_censored`
  по словарю для всех строк; повторный запуск во время работы — `«ALREADY_RUNNING»`.
- [ ] **AC2** (US2): В SongEdit доступно поле «Композиция (цензурированная)»
  с undo/copy/paste и tooltip; ручное значение переживает перезагрузку и
  переименование `song_name`.
- [ ] **AC3** (US3): `GET /api/public/songs?limit=100` — 0 запросов к
  `tbl_dictionaries` на этапе сборки DTO.
- [ ] **AC4** (US4): Миграция `42_song_name_censored.sql` применена на LOCAL
  и PROD; `tbl_songs.recordhash` учитывает новую колонку; sync LOCAL↔SERVER
  проходит без ошибок.

## Что лежит в БД

- **`tbl_songs.song_name_censored VARCHAR(255) NOT NULL DEFAULT ''`** —
  предвычисленное цензурированное название (по словарю или вручную).
- **`tbl_songs_sync.song_name_censored`** — то же для sync-таблицы
  (отдельная миграция `43_song_name_censored_sync.sql`).
- Обе колонки участвуют в `recordhash`-триггере (md5).

## Sync-поток

1. `rescanAllCensoredNames()` (admin) — фоновый SQL-UPDATE всех строк
   по словарю, защита от гонок через `@Volatile isCensoredRescanInProgress`,
   SSE-тост по завершении.
2. `Song.saveToDb()` — при пустом `songNameCensored` авто-заполняет
   `songName.censored(database)` (FR-003, baseline).
3. `Song.getDiff()` — **обязательно** сравнивает `songNameCensored`
   (FR-005a — bug-fix 2026-08-30 после первого деплоя), иначе sync
   не пропихивает колонку в UPDATE SET.
4. SongEdit (`webvue3`) — `v-model="song.songNameCensored"` через
   `setCurrentSongField` → `POST /api/song/update` → `saveToDb()`.

## Известные ограничения

- **Overwrite при реckanе** — кнопка «Пересканировать» перезаписывает
  ВСЕ цензурированные названия, включая ручные правки в SongEdit
  (см. Edge Cases спеки). «Мягкий» режим — отдельная задача.
- **Миграция ручная на каждой БД** — не автоматизирована
  (см. AGENTS.md «Категорически запрещено», п.2).
- **`tbl_songs_sync`** отстаёт от `tbl_songs` на 7 колонок
  (album_id, description, short_description, warning, id_tariff,
  status_process_demo, song_name_censored) — наш фикс закрывает
  только последнюю; остальные — накопленный долг specs/011/012/015/022.

## Связанные спеки

- specs/141-fix-censored-web-storage-globals — фикс Java/Kotlin regex
  `\b` для русских слов (Pass 239 hotfix основывался на этом).
- specs/262-search-pagination — пример аналогичной фичи
  с предвычисленной колонкой для index/производительности.
- archive/docs/features/censored-words.md — старая документация по
  словарю (если существует).