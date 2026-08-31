# Feature Specification: 281 — searchsongtextall перезатирает key/bpm (race condition)

**Feature Branch**: `281-find-lyrics-overwrites-key-bpm`

**Created**: 2026-08-31

**Status**: Draft

**Input**: User description: "Админка, функционал «Найти тексты для всех песен» похоже, перезатирает key/bpm так же, как это делалось при импорте из папки. Проверь."

## User Scenarios & Testing *(mandatory)*

### User Story 1 — key/bpm не теряются при «Найти тексты для всех песен» (Priority: P1)

Администратор в админке выбирает в таблице песен набор песен, у которых **уже определены** тональность (key) и BPM фоновым процессом `KEY_BPM_FROM_FILE` (т.е. в БД `song_tone`/`song_bpm` непустые). Нажимает кнопку «Найти тексты для всех песен» (`SongsTable.vue:419`), выбирает движок (YANDEX_SYNC/YANDEX_ASYNC/SEARXNG/FOURGET) и подтверждает. Поиск занимает от единиц до десятков секунд на песню (Playwright для Яндекс.Sync, HTTP-парсинг для SearXNG/Fourget). После завершения поиска найденный текст применяется к песне, **а ранее найденные key/bpm остаются на месте**. Админ не должен повторно запускать определение тональности для этих песен.

**Why this priority**: это точная копия бага, который уже был исправлен в Pass 278 для импорта из папки (`doCreateFromFolder` + `findYandexSongLyrics`), но в Параллельном эндпоинте `POST /songs/searchsongtextall` он остался. Каждое нажатие «Найти тексты для всех песен» для песен, у которых key/bpm уже найдены, обнуляет эти значения → требуется ручной перезапуск `KEY_BPM_FROM_FILE` → дублирующиеся процессы в очереди.

**Independent Test**: выбрать 3+ песни, для которых в БД заполнены `song_tone`/`song_bpm` (можно проверить через SELECT или через карточку песни до операции). Нажать «Найти тексты для всех песен», выбрать любой движок, дождаться завершения. После завершения открыть карточку любой обработанной песни: `song_tone` и `song_bpm` остались на прежних значениях, текст (`source_text`) обновился найденным (если движок что-то нашёл).

**Acceptance Scenarios**:

1. **Given** 3 песни с уже заполненными `song_tone="Am"`, `song_bpm=120`, **When** админ нажимает «Найти тексты для всех песен» (FOURGET), поиск занимает ~10 сек на песню, **Then** после завершения у всех 3 песен `song_tone="Am"`, `song_bpm=120`, `source_text` — найденный FOURGET-текст. Никаких новых записей `KEY_BPM_FROM_FILE` в `tbl_processes` не появилось.

2. **Given** песня с пустым `song_tone`, `song_bpm=0`, **When** админ запускает «Найти тексты для всех песен», **Then** после завершения `song_tone=""`, `song_bpm=0` (поведение не изменилось — ключ не был найден ранее, поэтому нечего терять).

3. **Given** в середине поиска (например, для второй из 3 песен) фоновый процесс `KEY_BPM_FROM_FILE` той же песни завершается и пишет `song_tone="Em"`, `song_bpm=80`, **When** поиск текста завершается и применяет `source_text`, **Then** `song_tone="Em"` и `song_bpm=80` остаются в БД (не перезатираются).

---

### User Story 2 — Гонка не возникает на любом из 4 движков (Priority: P1)

Тот же сценарий, что US1, но для каждого из поддерживаемых движков: `YANDEX_SYNC`, `YANDEX_ASYNC`, `SEARXNG`, `FOURGET`. Все четыре проходят через одну общую точку `applyFoundLyricsIfMissing` (`UtilsAI.kt:128`), которая и подлежит исправлению — фикс должен покрывать все четыре пути одной правкой.

**Why this priority**: архитектурно это одна и та же race condition в одной функции; закрытие только одного движка (например, FOURGET) не решает проблему для остальных трёх.

**Independent Test**: повторить US1 для каждого из 4 движков; результат один и тот же — key/bpm сохраняются.

**Acceptance Scenarios**:

1. **Given** 1 песня с заполненными key/bpm, **When** админ запускает «Найти тексты для всех песен» для движка YANDEX_SYNC, **Then** key/bpm сохраняются.
2. (аналогично для YANDEX_ASYNC, SEARXNG, FOURGET)

---

### User Stories из Pass 278 не должны сломаться (Priority: P2)

При импорте из папки (`doCreateFromFolder`) Pass 278 уже добавил локальный reload-from-db-before-save в трёх точках после `findYandexSongLyrics`/`applyDuplicateOriginal`/`applyAudioParentMarkers`. Глобальный фикс в `applyFoundLyricsIfMissing` НЕ должен сломать этот локальный фикс: повторный reload на той же песне безопасен (ничего не меняется, если за прошедшие миллисекунды БД не обновилась).

**Why this priority**: регрессионная гарантия — глобальный фикс должен быть совместим с локальным фиксом Pass 278.

**Independent Test**: прогнать Pass 278 acceptance scenarios — после импорта из папки key/bpm остаются на месте.

**Acceptance Scenarios**:

1. **Given** импорт из папки из 3 файлов, KEY_BPM_FROM_FILE успевает отработать до завершения поиска текста, **When** импорт завершается, **Then** `song_tone`/`song_bpm` заполнены (поведение Pass 278 сохранено).

---

### Edge Cases

- **Песня удалена из БД между `loadFromDbById` и `applyFoundLyricsIfMissing`**: `Song.loadFromDbById` в фиксе вернёт `null`; используем fallback на исходный `song` объект (паттерн Pass 278).
- **`applyFoundLyricsIfMissing` вызывается из `KaraokeProcessWorker` (фоновый процесс)** — там `song` уже только что загружен из БД (`KaraokeProcessWorker.kt:911` контекст); reload будет безвреден (тот же объект, та же БД).
- **`applyFoundLyricsIfMissing` вызывается из `getYandexSearch` (YANDEX_SYNC, UtilsAI.kt:398)** — там `song` тоже только что загружен; reload — безвреден.
- **`applyFoundLyricsIfMissing` вызывается из `getLyricsSearchViaSearchTool` (SEARXNG/FOURGET, UtilsAI.kt:249)** — там `song` мог быть загружен давно (внутри `searchsongtextall` контроллер передаёт старый объект через цепочку вызовов); reload здесь как раз и нужен.
- **Что если во время reload-а БД недоступна (transient connection error)?** — fallback на исходный объект (паттерн Pass 278) обеспечивает, что текст всё равно сохранится; цена — возможная потеря key/bpm в этом редком случае. Текущий код без фикса теряет их всегда в этом сценарии → фикс строго лучше.
- **Что если `firstNonEmpty == null`** (пустой список кандидатов) — функция возвращается на строке 132 до `saveToDb`, никакого эффекта, никаких проблем.

## Requirements *(mandatory)*

### Часть 1 — `applyFoundLyricsIfMissing` (ПЕРВОНАЧАЛЬНЫЙ БАГ)

- **FR-001**: В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/UtilsAI.kt:128` (`fun applyFoundLyricsIfMissing`) ПЕРЕД строкой `song.saveToDb()` объект `song` ДОЛЖЕН быть перезагружен из БД через `Song.loadFromDbById(id = song.id, database = song.database, storageService = song.storageService, storageApiClient = song.storageApiClient)` с fallback на исходный `song` при `null`. Паттерн — точно как в Pass 278 (`ApiController.kt:5461-5472`).

- **FR-002**: После перезагрузки `song` ДОЛЖНЫ быть применены `song.sourceText = firstNonEmpty` и `song.fields[SongField.ID_STATUS] = "1"` (при `idStatus == 0L`), затем вызывается `song.saveToDb()`. Логика применения идентична текущей (FR-001 фичи 020 — fix-search-lyrics-autofill).

- **FR-003**: Фикс ДОЛЖЕН покрывать все 4 места вызова `applyFoundLyricsIfMissing`:
  - `UtilsAI.kt:249` — `getLyricsSearchViaSearchTool` (SEARXNG, FOURGET)
  - `UtilsAI.kt:398` — `getYandexSearch` (YANDEX_SYNC, после парсинга результатов)
  - `KaraokeProcessWorker.kt:911` — фоновый процесс
  - Опционально (если найдётся ещё одно использование) — никаких изменений

- **FR-007**: KDoc-комментарий к `applyFoundLyricsIfMissing` ДОЛЖЕН быть обновлён — добавлено объяснение, почему перед `saveToDb` нужен reload, со ссылкой на Pass 278 (`specs/278-fix-key-loss-on-lyrics-search/spec.md`) и эту спеку (`specs/281-find-lyrics-overwrites-key-bpm/spec.md`).

### Часть 2 — Аналогичные баги в других местах (найдены при проверке «заодно все места»)

> **Контекст поиска**: после фикса `applyFoundLyricsIfMissing` пользователь попросил проверить ВСЕ места с потенциальной такой же гонкой. Поиск по `saveToDb()` в `karaoke-app/src/main/kotlin` (80 точек вызова) + анализ «жил ли объект `song` между `loadFromDbById` и `saveToDb`» выявил ещё **5 горячих точек**, где stale `Song` мог перезатереть `key`/`bpm`/URL'ы стемов. Все они — паттерн «reload-from-db-before-save» по образцу Pass 278.

- **FR-010**: `Utils.kt:162` — фоновый процесс `findParentAndAudioParentForAll` (фаза 1 «поиск родителей»). Если у найденного `original.sourceText.isBlank()`, текущий код делает `song.rootId = original.id; song.saveToDb()` БЕЗ reload. Между `loadFromDbById(song)` (строка 124) и `saveToDb` (строка 162) проходит несколько секунд (`findParentCandidateId` + `loadFromDbById(original)`). Параллельный `KEY_BPM_FROM_FILE` может успеть записать `key`/`bpm` — они будут перезатёрты пустыми. **Фикс**: добавить reload по образцу `applyDuplicateOriginal` (Utils.kt:4560-4566).

- **FR-011**: `Utils.kt:4647 applyFamilySongSelection` — функция применяет данные «похожей версии песни» (модалка «Похожие версии» + autoAssign). Текущая реализация делает `song.saveToDb()` напрямую БЕЗ reload. Используется из:
  - `ApiController.applyfamilysongselection` (ручной клик пользователя по строке модалки)
  - `Utils.kt:4826 autoAssignOriginalByWaveform` (фоновая задача с ffmpeg-сверкой — десятки секунд)
  
  В обоих случаях объект `song` мог жить в памяти долго. **Фикс**: перезагрузить объект из БД, применить все изменения к `songToSave`, сделать `songToSave.saveToDb()`, затем синхронизировать `song` в памяти с записанным состоянием (паттерн Pass 279: `applyDuplicateOriginal` Utils.kt:4582-4589 и `applyAudioParentMarkers` Utils.kt:4638-4644).

- **FR-012**: `Utils.kt:4850` — финальный `song.saveToDb()` в `autoAssignOriginalByWaveform` (после `applyFamilySongSelection` + `.srt` файлов + установки `idStatus = "2"`). Между `applyFamilySongSelection` (где теперь будет reload по FR-011) и этим `saveToDb` проходит несколько секунд (запись N файлов `.srt`). Параллельный процесс может обновить поля. **Фикс**: после FR-011 `song` уже синхронизирован, но `resultText`/`formattedText*` могли измениться между applyFamilySongSelection и этим saveToDb (пересчёт на строке 4831-4834 — на основе `song` в памяти). Безопаснее — ещё один reload перед этим saveToDb, ИЛИ синхронизировать `song.idStatus`/`song.resultText`/`song.formattedText*` после applyFamilySongSelection и оставить `song.saveToDb()` как есть. Выбран второй вариант (минимальная инвазивность): добавить sync-блок после строк 4831-4834, переустанавливающий `song.fields[SongField.ID_STATUS] = "2"` на тот же объект.

- **FR-013**: `Utils.kt:4893 findAudioParentByWaveform` — функция делает 4× `song.saveToDb()` (строки 4929, 4948, 4969, 4983). Между `loadFromDbById` в caller-е и этими saveToDb проходят десятки секунд (ffmpeg-декод через `WaveformCompare.compareWaveforms`). Используется из:
  - `ApiController.findaudioparent` (ручной запуск админом)
  - `Utils.findParentAndAudioParentForAll` (фоновая задача на ВСЕ песни с `root_id = 0`)

  **Фикс**: перед КАЖДЫМ из 4 `saveToDb` сделать `Song.loadFromDbById(...) ?: song` и присвоить в `songToSave`; `songToSave.audioCompareHistory = ...` / `songToSave.audioParentId = ...` / `songToSave.audioSimilarityPercent = ...` / `songToSave.audioDeltaMs = ...` применить к reloaded; `songToSave.saveToDb()`. Параллельный процесс не перезатрёт ни одно поле.

- **FR-014**: `Song.kt:3626 Song.setSourceMarkers` и `Song.kt:3662 Song.setSourceText` — эти методы делают `saveToDb()` ВНУТРИ (строки 3639, 3650, 3671, 3678). Вызываются из:
  - `SongEditorController.approve` (строки 381, 390) — цикл по голосам, апрув задания редактора, между голосами проходят секунды (запись `.srt` файлов)
  - `SongEditorController.savesourcetextmarkers` (строки 894, 896) — сохранение черновика разметки на сайте
  - `MainController:426, 673`, `ApiController:3505/3532/3562/3571`, `Song.updateMarkersFromSourceText:4947`
  
  Если у песни в фоне работает `KEY_BPM_FROM_FILE`/`DEMUCS2`/`Sheetsage` (песня недавно импортирована или размечается онлайн), то между первым `setSourceMarkers` и последним `setSourceText` (несколько секунд) параллельный процесс может успеть обновить `key`/`bpm`/URL'ы стемов — каждый следующий `saveToDb` будет их перезатирать.

  **Фикс**: внутри `setSourceMarkers` и `setSourceText` ПЕРЕД каждым `saveToDb()` сделать `Song.loadFromDbById(id = this.id, ...)` → применить изменения к reloaded → `reloaded.saveToDb()` → синхронизировать `this` с reloaded (паттерн applyDuplicateOriginal/applyAudioParentMarkers). Это замедлит цикл апрува на N×2 reload'ов (N голосов), но N типично 1-3 — приемлемо.

### Часть 3 — Уже исправлено ранее (НЕ ТРОГАЕМ)

- **FR-020**: В `doCreateFromFolder` (Pass 278, `ApiController.kt:5461-5472`) — reload перед `saveToDb` после `findYandexSongLyrics`. НЕ ДОЛЖЕН быть удалён/изменён.
- **FR-021**: `Utils.kt:4555 applyDuplicateOriginal` (Pass 278) — reload+sync. НЕ ДОЛЖЕН быть изменён.
- **FR-022**: `Utils.kt:4605 applyAudioParentMarkers` (Pass 278) — reload+sync. НЕ ДОЛЖЕН быть изменён.

### Часть 4 — Что НЕ меняется

- **FR-030**: `Song.saveToDb()` (`Song.kt:5169`) — поведение НЕ изменяется (Pass 278 FR-004). Все 46+ вызывающих мест продолжают работать как раньше.
- **FR-031**: KDoc-комментарии к затронутым функциям ДОЛЖНЫ быть обновлены — добавлены `@see` ссылки на эту спеку и краткое объяснение паттерна reload-from-db-before-save.

### Key Entities

- **`Song`** (без изменений в схеме): хранит `key`/`bpm`/`source_text`/`id_status`/URL'ы стемов/`audioParentId`/`audioCompareHistory` и т.д. Объект в памяти может устаревать относительно БД — это корень всех гонок.
- **`applyFoundLyricsIfMissing`** (`UtilsAI.kt:128`) — фикс FR-001.
- **`applyFamilySongSelection`** (`Utils.kt:4647`) — фикс FR-011.
- **`findAudioParentByWaveform`** (`Utils.kt:4893`) — фикс FR-013.
- **`autoAssignOriginalByWaveform`** (`Utils.kt:4782`) — фикс FR-012.
- **`findParentAndAudioParentForAll`** (`Utils.kt:79`) — фикс FR-010.
- **`Song.setSourceMarkers`/`setSourceText`** (`Song.kt:3626/3662`) — фикс FR-014.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Все 5 мест с потенциальной race condition (FR-001, FR-010, FR-011, FR-012, FR-013, FR-014) защищены паттерном reload-from-db-before-save. Проверяется через code review: каждое из этих мест содержит `Song.loadFromDbById(...) ?: song` (или `songToSave`) перед `saveToDb()`.

- **SC-002**: Регрессий в 46+ других местах вызова `Song.saveToDb()` нет — `saveToDb()` не модифицируется. Фиксы локальны для конкретных функций.

- **SC-003**: Регрессий в Pass 278 (фикс в `doCreateFromFolder`) и Pass 279 (фикс в `applyDuplicateOriginal`/`applyAudioParentMarkers`) нет — эти функции сохраняют свой reload+sync паттерн.

- **SC-004**: Все существующие пользовательские сценарии продолжают работать:
  - «Найти тексты для всех песен» (FR-001)
  - Импорт из папки (FR-020)
  - Апрув задания редактора (FR-014 — критичный, т.к. цикл по голосам)
  - Модалка «Похожие версии песни» — ручной выбор (FR-011)
  - «Найти аудио-родителя» — ручной и фоновый (FR-013)
  - Поиск родителей и аудио-родителей фоном (FR-010, FR-012, FR-013)

## Assumptions

- **A-1**: Глобальный фикс в `applyFoundLyricsIfMissing` не конфликтует с локальным фиксом Pass 278 в `doCreateFromFolder`: там `applyFoundLyricsIfMissing` НЕ вызывается (используется прямой `newSong.saveToDb()` после `findYandexSongLyrics`). Глобальный фикс покрывает путь `searchsongtextall` → `getLyricsSearch` → `applyFoundLyricsIfMissing`; локальный — путь `doCreateFromFolder` → `findYandexSongLyrics` → `newSong.saveToDb()`. Это два непересекающихся пути.

- **A-2**: Повторный reload в `KaraokeProcessWorker` (где `song` свежезагружен) — безвреден: между загрузкой объекта в `KaraokeProcessWorker` и вызовом `applyFoundLyricsIfMissing` проходят миллисекунды (не десятки секунд), БД практически гарантированно не изменилась, `loadFromDbById` вернёт идентичный объект, поведение не изменится. Стоимость — один лишний SELECT; приемлемо.

- **A-3**: Паттерн fallback `?: song` (Pass 278) — корректный: если `loadFromDbById` вернул `null` (песня удалена между вызовами), мы всё равно сохраним текст через исходный объект — лучше сохранить текст, чем потерять его. Цена fallback — возможная потеря key/bpm в этом редком случае, но это строго лучше, чем текущее поведение (потеря key/bpm всегда).

- **A-4**: `Song.loadFromDbById` уже корректно работает и не имеет собственных race condition (используется повсеместно, проверено на проде 18k+ песен).

- **A-5**: Минимальный риск: фикс — это 5 строк кода в одной функции, без изменения схемы БД, без миграций, без изменения контракта эндпоинта. Не требуется никаких изменений во фронте (`SongsTable.vue`), тестах (их нет в проекте для бэкенда), LiveDoc-принципах.

- **A-6**: Race condition с `DEMUCS2`/`Sheetsage`/другими фоновыми процессами, обновляющими `audio_song`/`audio_vocals`/..., тоже покрывается этим фиксом: если во время поиска текста они успевают записать URL'ы стемов — reload-from-db-before-save их сохранит. Pass 278 явно упоминает эту возможность как теоретическую — глобальный фикс делает её автоматической защитой для всех путей.
