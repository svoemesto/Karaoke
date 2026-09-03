# Feature Specification: 299 — Перезатирание полей песни при фоновой обработке

**Feature Branch**: `299-song-fields-overwrite-race-condition`
**Created**: 2026-09-03
**Status**: Draft
**Input**: User description (OpenProject WP #49): «Кейс следующий. Админка, импорт файлов из папки. В базу добавляются новые песни, формируются кортежи заданий на демукс, создание файлов мп3, загрузку в хранилище и т.п. Запускается поиск текстов песен в интернете. Наблюдается такой баг. У песни через интерфейс SongEdit.vue меняются значения полей — например изменяется название песни — было "ПММЛ" стало "П.М.М.Л.". Но после того, как до этой песни доходит дело в запущенном ранее синхронном задании на поиск текста (когда задание запускалось, название песни было ещё старое) и если текст находится и сохраняется в песне — то сделанные изменения (новое название песни) перезатирается. Необходимо найти в коде ВСЕ места, где может происходить подобная гонка и исправить их.»

> **Контекст.** Pass 281 (закрыт в PR #395) уже защитил от этой гонки 5 горячих путей (`applyFoundLyricsIfMissing`, `applyDuplicateOriginal`, `applyAudioParentMarkers`, `applyFamilySongSelection`, `autoAssignOriginalByWaveform`, `findAudioParentByWaveform`, `setSourceMarkers`, `setSourceText`) паттерном **reload-from-db-before-save**: перед `saveToDb()` объект `song` перезагружается из БД, чтобы `Song.getDiff()` НЕ включил в `UPDATE` поля, которые параллельная транзакция успела записать между первоначальной загрузкой и сохранением. Однако задача #49 фиксирует, что баг **по-прежнему проявляется** в продакшене — значит, либо (a) защита покрыта не для всех путей, либо (b) сам паттерн «reload + save» фундаментально не атомарен и между `loadFromDbById(...)` и `ps.executeUpdate()` другая транзакция успевает закоммитить изменение, которое diff увидит как «stale в памяти против нового в БД» и перезатрёт обратно в БД.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Название песни из SongEdit не теряется при поиске текстов (Priority: P1)

Админ импортирует папку с N аудиофайлами (стандартный сценарий импорта). На старте импорта формируется очередь задач: демус, mp3, загрузка в MinIO, **поиск текстов** (синхронное задание через FOURGET/SEARXNG/Yandex.Sync, типично 10-60 сек на песню). Сразу после старта админ открывает карточку любой песни (`SongEdit.vue`), меняет название с «ПММЛ» на «П.М.М.Л.» (или любое другое поле — `songName`, `author`, `album`, `year`, `songType`, `genre` и т.д.) и нажимает «Сохранить». UI возвращает успех, обновляет таблицу песен. Через несколько минут до этой песни доходит фоновая задача поиска текста, находит текст и сохраняет его в `source_text`. После сохранения **название песни остаётся «П.М.М.Л.»** (а не возвращается к «ПММЛ»).

**Why this priority**: критический пользовательский сценарий (потеря ручных правок = потеря доверия к системе), описан буквально в задаче #49, проявляется в проде.

**Independent Test**: импортировать папку из 3 песен → сразу после старта импорта у одной из песен через SongEdit.vue поменять название и `author` → дождаться завершения очереди поиска текстов → убедиться, что название и `author` сохранены, а `source_text` обновлён найденным текстом.

**Acceptance Scenarios**:

1. **Given** песня в БД с `song_name='ПММЛ'`, `author='Aaa'`, `source_text=''`, `id_status=0`, **When** админ через SongEdit меняет `song_name='П.М.М.Л.'` и одновременно стартовавший поиск текстов находит и записывает `source_text='Какой-то текст'`, **Then** финальное состояние в БД: `song_name='П.М.М.Л.'`, `author='Aaa'`, `source_text='Какой-то текст'`, `id_status=1` (TEXT_CREATE).
2. **Given** песня с уже заполненным `source_text`, **When** админ меняет `album` и фоновая задача пересохраняет песню (любой `saveToDb` в фоне), **Then** `album` сохранён, `source_text` не перезатёрт.
3. **Given** админ меняет 3 разных поля (`song_name`, `author`, `album`) подряд за 5 секунд, **When** между изменениями приходит фоновое обновление `id_status`, **Then** все 3 поля админа сохранены, `id_status` отражает фоновое обновление.

---

### User Story 2 — Аналогично для всех фоновых процессов, не только поиска текстов (Priority: P1)

То же поведение, что в US1, но гонка возникает в любом из фоновых процессов: `KEY_BPM_FROM_FILE` (тональность + BPM), `DEMUCS2` (стемы), `Sheetsage` (аккорды), `findAudioParentByWaveform` (аудио-родитель), `applyFamilySongSelection` (похожие версии), `applyDuplicateOriginal` (импорт из папки). Все они вызывают `song.saveToDb()` после того, как объект `song` прожил в памяти секунды/десятки секунд (HTTP-парсинг, ffmpeg-декод, ML-вызовы). Все они **должны уважать** параллельные ручные правки через SongEdit.

**Why this priority**: задача #49 явно говорит «найти ВСЕ места, где может происходить подобная гонка». US1 закрывает только поиск текстов; US2 — обобщение на любые «долгие» пути сохранения.

**Independent Test**: для каждого из 5+ путей повторить US1 — выбрать другой фон (например, `KEY_BPM_FROM_FILE`), параллельно менять поле через SongEdit. Финальное состояние: ручные правки сохранены, фоновое обновление тоже применено.

**Acceptance Scenarios**:

1. **Given** `KEY_BPM_FROM_FILE` запущен на песне с `song_tone=''`, **When** админ через SongEdit меняет `genre='Rock'` и `KEY_BPM_FROM_FILE` завершается с `song_tone='Am'`, **Then** финал: `genre='Rock'`, `song_tone='Am'`, `id_status=2` (TEXT_CHECK или где был).
2. **Given** `DEMUCS2` запущен на песне, **When** админ меняет `songName`, **Then** `songName` сохранён, URL'ы стемов (`audioSong`, `audioVocals` и т.д.) обновлены.
3. **Given** `applyFamilySongSelection` отрабатывает фоновую автопривязку, **When** админ параллельно меняет `songType`, **Then** оба изменения применены.

---

### User Story 3 — Pass 281 фикс не сломан (Priority: P2)

Существующие 5+ мест, защищённые Pass 281 (reload-from-db-before-save), продолжают работать. После миграции на новое решение они либо остаются на reload-паттерне, либо автоматически переводятся на новый механизм — **без регрессий**.

**Why this priority**: регрессионная гарантия. Pass 281 закрыт в PR #395 и сейчас на проде; любая регрессия сломает ключевые сценарии (импорт из папки, поиск текстов для всех песен, апрув заданий редактора).

**Independent Test**: повторить Pass 281 acceptance scenarios для всех 6 закрытых FR-001..FR-014 (см. `specs/281-find-lyrics-overwrites-key-bpm/spec.md`, разделы «User Stories» и «Measurable Outcomes»).

**Acceptance Scenarios**:

1. **Given** импорт папки из 3 файлов + KEY_BPM_FROM_FILE отрабатывает параллельно с поиском текстов, **When** импорт завершается, **Then** `song_tone`/`song_bpm`/URL'ы стемов заполнены, `source_text` заполнен (если найден).
2. **Given** админ вручную кликает по строке в модалке «Похожие версии» (`applyFamilySongSelection` через `applyfamilysongselection`), **When** параллельно отрабатывает `KEY_BPM_FROM_FILE`, **Then** выбор пользователя применён, `song_tone` не перезатёрт.
3. **Given** админ одобряет задание редактора (`SongEditorController.approve` → цикл `setSourceMarkers`/`setSourceText` по голосам), **When** параллельно отрабатывает `KEY_BPM_FROM_FILE`, **Then** маркеры/текст сохранены, `song_tone`/`song_bpm` не перезатёрты.

---

### User Story 4 — Диагностика при попытке потерять правку (Priority: P3)

Если по какой-то причине фоновое сохранение **всё же попытается** перезатереть поле, обновлённое параллельной транзакцией, система **детектирует** это и **логирует предупреждение** вместо тихого перезатирания. Это страховка от регрессий и помощь в отладке.

**Why this priority**: не блокирует фикс, но даёт операционную видимость. В текущем коде Pass 281 «проглатывает» гонку — diff перезатирает молча.

**Independent Test**: запустить параллельное обновление (SQL UPDATE в фоне) + автоматический тест, который проверяет, что (a) ручная правка сохранена ИЛИ (b) в логе появилось предупреждение с обоими значениями и `song_id`.

**Acceptance Scenarios**:

1. **Given** фоновое сохранение пытается записать UPDATE с diff, содержащим устаревшее значение поля, **When** включён режим диагностики, **Then** в `infra.prod.ping`-логе появляется `WARN song.overwrite_recovered: songId=X field=song_name oldInMemory=… newInDb=…` — оператор видит, что защита сработала.

---

### Edge Cases

- **Песня удалена между `loadFromDbById` и `saveToDb`** — `loadFromDbById` возвращает `null`, fallback на исходный объект (паттерн Pass 281), текст сохраняется; потеря ручных правок при удалении невозможна (запись не существует).
- **БД недоступна в момент reload (transient connection error)** — fallback на старый паттерн «save-as-is»; гонка теоретически возможна в этом окне, но это строго лучше текущего поведения (гарантированная потеря правок).
- **Два потока с одинаковым `song_id` стартуют `saveToDb` почти одновременно** (race на уровне `Song.saveToDb` — не на уровне UI/фона) — нужно, чтобы SELECT FOR UPDATE (если выбран пессимистичный подход) сериализовал записи по строке, иначе оба UPDATE'а применятся и diff одного перезатрёт diff другого.
- **`source_text` уже заполнен** (`idStatus >= 1`) — `applyFoundLyricsIfMissing` ничего не делает (early-return на `song.haveSourceText`); гонки нет.
- **`song_to_save.id_status` уже продвинут дальше** — фоновое сохранение текста должно учитывать текущий статус, не откатывать назад.
- **Field length / charset** — перезатирание может проявиться только для полей определённой длины, если дифф ошибочно усекает значение; полнота значения сохраняется во всех сценариях.

## Requirements *(mandatory)*

### Functional Requirements

#### Часть 1 — Базовый механизм защиты от race в `Song.saveToDb()`

- **FR-001**: В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt` метод `saveToDb()` (строка 5205, UPDATE-ветка) ДОЛЖЕН загружать `savedSong` блокирующей операцией: новая функция `Song.loadFromDbByIdForUpdate(id, database, ...)` использует SQL `SELECT ... FOR UPDATE` (или `SELECT ... FOR SHARE`, если блокировка чтения допустима) в транзакции, в которой будет выполнен `UPDATE`. **Решает** root cause: между `loadFromDbById` (старым) и `ps.executeUpdate()` другая транзакция физически не сможет закоммитить UPDATE на эту же строку — она будет ждать снятия блокировки.

- **FR-002**: `loadFromDbByIdForUpdate` ДОЛЖЕН открывать явную транзакцию (`connection.setAutoCommit(false)` или эквивалент через `KaraokeConnection`), держать её открытой до явного `commit`/`rollback` после `UPDATE`. `Song.saveToDb()` сам управляет жизненным циклом транзакции — `loadFromDbByIdForUpdate` лишь открывает/возвращает транзакционный контекст, а `saveToDb()` коммитит его в самом конце. **Альтернатива** (если менеджер транзакций уже есть): использовать `TransactionTemplate.execute { ... }` / `@Transactional`-стиль через DI — но в текущей кодовой базе нет Spring-Tx, поэтому ручной `connection.setAutoCommit(false)` + явный `commit` + `finally { rollback if not committed }`.

- **FR-003**: `Song.saveToDb()` ДОЛЖЕН быть обратно совместим: семантика для существующих 80+ вызывающих мест НЕ меняется (если они не передают транзакционный контекст — работают в автокоммите, как сейчас). Поведение под блокировкой включается **только когда** вызывающий код явно открыл транзакцию или явно запросил «lockable save» (например, через новый флаг `useLock = true` или выделенный метод `saveToDbWithLock()`).

#### Часть 2 — Применение защиты ко всем горячим путям

> **Контекст**: спека 281 уже применила паттерн `reload-from-db-before-save` к 5+ местам, но этот паттерн **не атомарен** — между `loadFromDbById` и `ps.executeUpdate` гонка всё ещё возможна (см. анализ в разделе «Key Entities»). Требуется либо (a) заменить паттерн на `loadFromDbByIdForUpdate + UPDATE в той же транзакции`, либо (b) добавить оптимистичный lock через колонку `version`/`updated_at` + `WHERE version = oldVersion` в UPDATE.

- **FR-010**: `applyFoundLyricsIfMissing` (`UtilsAI.kt:144`) ДОЛЖЕН использовать `loadFromDbByIdForUpdate` вместо `loadFromDbById`. Существующий fallback `?: song` сохраняется.

- **FR-011**: `applyDuplicateOriginal` (`Utils.kt:4847`) — заменить `loadFromDbById` на `loadFromDbByIdForUpdate`.

- **FR-012**: `applyAudioParentMarkers` (`Utils.kt:4897`) — заменить `loadFromDbById` на `loadFromDbByIdForUpdate`.

- **FR-013**: `applyFamilySongSelection` (`Utils.kt:4939`) — заменить `loadFromDbById` на `loadFromDbByIdForUpdate`.

- **FR-014**: `autoAssignOriginalByWaveform` (`Utils.kt:5104`) — оба reload'а (`finalSongToSave` и тот, что внутри `applyFamilySongSelection`) использовать `loadFromDbByIdForUpdate`.

- **FR-015**: `findAudioParentByWaveform` (`Utils.kt:5248`) — все 4 reload'а (`songToSave` перед каждым `saveToDb`) использовать `loadFromDbByIdForUpdate`.

- **FR-016**: `Song.setSourceMarkers` (`Song.kt:3626`) и `Song.setSourceText` (`Song.kt:3690`) — оба `loadFromDbById` заменить на `loadFromDbByIdForUpdate`.

- **FR-020**: Должны быть **найдены и аналогично защищены** все **остальные** места, где объект `song` живёт в памяти долго (секунды и более) между `loadFromDbById` (или конструктором `Song.createFromPath`) и `saveToDb()`. Минимальный список для проверки (по результатам ресёрча задачи #49):
  - `Utils.kt:666` — `song.saveToDb()` (контекст требует уточнения).
  - `Utils.kt:1594, 1624, 1705, 1734, 1737` — внутри `doCreateFromFolder` или `findParentAndAudioParentForAll`.
  - `Utils.kt:4141, 4201` — внутри других функций поиска родителей.
  - `Utils.kt:4654` — `newSong.saveToDb()` в `doCreateFromFolder` сразу после `createFromPath()`.
  - `KaraokeProcess.kt:408, 415, 422, 429, 436` — сохранение внутри `createKaraoke()`.
  - `Song.kt:455, 738, 776, 5951, 6577, 8186, 8357, 8374, 8544` — внутренние `saveToDb()`.
  - `ApiController.kt:883, 904, 7014, 7043, 7099, 7125, 7907, 7912` — эндпоинты, где объект `song` мог жить долго.
  - `MainController.kt:1631, 1804, 1993` — другие эндпоинты.
  - `KaraokeProcess.kt:408, 415, 422, 429, 436`.
  - `TelegramAutoPublishService.kt:257, 293, 310, 327`, `VkAutoPublishService.kt:252, 358, 372, 468, 480, 535` — публикация на площадки.

- **FR-021**: Для каждого найденного места в FR-020 — явно зафиксировать в KDoc: (a) «между загрузкой объекта и сохранением проходит N секунд/минут», (b) «параллельно работает процесс X, который может обновить поле Y», (c) «без `loadFromDbByIdForUpdate` гонка воспроизводится в сценарии US1». Если место признано **не** горячим (объект живёт < 100 мс) — обосновать и оставить как есть.

#### Часть 3 — Альтернативный подход (опционально): optimistic locking

> **Контекст.** Если пессимистичный подход (FOR UPDATE) окажется слишком инвазивным (например, будет блокировать чтение других песен при глобальной синхронизации), альтернатива — optimistic locking через `updated_at` или `version` колонку в `tbl_songs` (миграция БД + новая колонка + изменение `saveToDb` на `WHERE id = X AND updated_at = oldSnapshot.updated_at`).

- **FR-030**: [NEEDS CLARIFICATION: выбор между pessimistic (FR-001..FR-021) и optimistic (эта часть) — решается на этапе `/speckit.plan` по результатам бенчмарка на проде] Реализовать ОДИН из двух подходов, не оба. По умолчанию — pessimistic (FR-001..FR-021), он покрывает задачу без миграции БД.

#### Часть 4 — Существующие паттерны Pass 281

- **FR-040**: Pass 281 фиксы (FR-001 спеки 281 в `applyFoundLyricsIfMissing`, FR-011 в `applyFamilySongSelection`, FR-012 в `autoAssignOriginalByWaveform`, FR-013 в `findAudioParentByWaveform`, FR-014 в `setSourceMarkers/setSourceText`) **остаются на месте**. Паттерн `reload-from-db-before-save` — это страховка, даже если будет добавлен `FOR UPDATE`. Совместимость: `loadFromDbByIdForUpdate` можно вызывать поверх существующего `loadFromDbById` без побочных эффектов — оба читают одни и те же данные; блокировка только добавляет гарантию атомарности записи.

- **FR-041**: KDoc-комментарии ко всем затронутым функциям ДОЛЖНЫ быть обновлены — `@see specs/299-song-fields-overwrite-race-condition/spec.md` + краткое объяснение «почему FOR UPDATE (или optimistic lock)».

#### Часть 5 — Диагностика (опционально)

- **FR-050**: В `Song.saveToDb()` при обнаружении diff-поля, которое в `savedSong` (после `loadFromDbByIdForUpdate`) отличается от `this` И в БД было обновлено **после** момента, когда объект `song` был загружен в вызывающем коде (например, по `recordhash`-метке времени или новой колонке `updated_at`) — записать WARN в `infra.prod.ping` лог с `songId`, именем поля, старым/новым значениями. Реализация — на усмотрение плана (через пост-дифф-чек или через отдельный фоновый репортер).

### Key Entities

- **`Song`** (без изменений в схеме для pessimistic-подхода): сущность `tbl_songs`, ~70 редактируемых полей (`song_name`, `song_bpm`, `song_tone`, `source_text`, `id_status`, URL'ы стемов, `audio_parent_id`, ...). Объект в памяти может устаревать относительно БД — это корень всех гонок, описанных в задаче #49.
- **`loadFromDbByIdForUpdate`** (новый метод `Song.kt`): открывает JDBC-транзакцию, выполняет `SELECT * FROM tbl_songs WHERE id = ? FOR UPDATE`, возвращает `(Song, Transaction)` — где `Transaction` инкапсулирует `Connection` и позволяет вызвать `commit()` или `rollback()`. Используется только в горячих путях с долгим жизненным циклом объекта.
- **`applyFoundLyricsIfMissing`** (`UtilsAI.kt:144`) — единая точка автоподстановки найденного текста, ключевой hot path для US1.
- **`applyDuplicateOriginal`** / **`applyAudioParentMarkers`** / **`applyFamilySongSelection`** / **`autoAssignOriginalByWaveform`** / **`findAudioParentByWaveform`** (`Utils.kt`) — горячие пути Pass 281, которые должны быть переведены на новый механизм.
- **`Song.setSourceMarkers`** / **`Song.setSourceText`** (`Song.kt:3626/3690`) — вызываются из `SongEditorController.approve` и других мест; цикл по голосам = N reload'ов на одну операцию, при параллельной фоновой обработке — потеря key/bpm на каждом `saveToDb`.
- **`doCreateFromFolder`** (`Utils.kt`) — функция импорта папки; вызывает цепочку операций над `newSong`, каждая из которых потенциально гоняется с параллельным `KEY_BPM_FROM_FILE`/`DEMUCS2`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Сценарий US1 воспроизводится вручную (импорт папки → мгновенная правка `songName` через SongEdit → поиск текстов) — после завершения поиска `songName` остаётся изменённым, `source_text` обновлён. Допустимо ручное воспроизведение один раз перед merge и автоматический чек на integration-тестах (если добавим).

- **SC-002**: Все 5+ мест Pass 281 (`applyFoundLyricsIfMissing`, `applyDuplicateOriginal`, `applyAudioParentMarkers`, `applyFamilySongSelection`, `autoAssignOriginalByWaveform`, `findAudioParentByWaveform`, `Song.setSourceMarkers`, `Song.setSourceText`) используют `loadFromDbByIdForUpdate` (или эквивалентный optimistic-lock подход). Code review: каждое место содержит `Song.loadFromDbByIdForUpdate(...)` (или новый механизм) ПЕРЕД `saveToDb()`.

- **SC-003**: Дополнительные горячие пути, найденные в FR-020, либо (a) защищены аналогично, либо (b) явно обоснованы в KDoc почему они НЕ горячие (объект живёт < 100 мс).

- **SC-004**: Регрессий в Pass 281 acceptance scenarios нет — Pass 281 закрыт и в проде; фикс должен быть совместим (FR-040).

- **SC-005**: `Song.saveToDb()` остаётся обратно совместимым для 70+ других мест вызова, где объект `song` живёт < 100 мс (типичный случай `SongEditorController` и коротких endpoint'ов) — там продолжает работать старый путь без транзакции.

- **SC-006**: Тесты проекта (если есть) не сломаны. В проекте нет автотестов на `Song.saveToDb` (см. `karaoke-app/src/test` — `@Disabled`), проверка — пользователем после деплоя.

- **SC-007**: Производительность не деградирует значимо. Блокировка `FOR UPDATE` действует только на конкретную строку `tbl_songs` (на время одной транзакции, ~миллисекунды) — глобальные операции (поиск текстов для 100 песен подряд) не блокируют другие песни.

- **SC-008**: KDoc coverage ≥ 50% (CI gate). Все новые/изменённые публичные функции имеют KDoc с `@see specs/299`.

## Assumptions

- **A-1**: `Song.saveToDb()` вызывается преимущественно в фоне или из коротких endpoint'ов — там, где объект `song` живёт < 100 мс, риск гонки минимален. Защищать имеет смысл только пути с долгим жизненным циклом (HTTP-парсинг, ML-вызовы, ffmpeg).

- **A-2**: PostgreSQL поддерживает `SELECT ... FOR UPDATE` (да, с 6.0+; в проде 15+). Блокировка снимается при `commit`/`rollback`.

- **A-3**: `KaraokeConnection` (см. Constitution §II «сырой JDBC») уже предоставляет `getConnection()`. Управление транзакциями через `Connection.setAutoCommit(false)` + `commit()` + `rollback()` — стандартный JDBC API.

- **A-4**: Существующий `loadFromDbById` остаётся без изменений (FR-001/FR-002 не трогают его). Новый метод `loadFromDbByIdForUpdate` — отдельный API для горячих путей.

- **A-5**: Альтернативный подход (optimistic locking) требует миграции БД (новая колонка `version BIGINT` или использование существующего `record_update_date`-подобного поля, если есть) + изменение схемы UPDATE на `WHERE id = ? AND version = ?`. Это больший объём работы и больше риска регрессий — дефолт pessimistic.

- **A-6**: Задача #49 — про гонку между **ручной правкой через SongEdit** и **фоновым сохранением**. Гонки между двумя фоновыми процессами (например, `KEY_BPM_FROM_FILE` и `DEMUCS2` одновременно на одной песне) — отдельная проблема, фикс FR-001 защищает и её (блокировка на уровне строки сериализует все записи).

- **A-7**: SPEC 281 фиксы (reload-from-db-before-save) уже в проде (PR #395 смержен 2026-08-31). Они не ломаются от добавления `FOR UPDATE` сверху — оба подхода совместимы (FR-040).

## Open Questions

- **Q1**: Pessimistic (`FOR UPDATE`) vs optimistic (`version` колонка)? Дефолт — pessimistic. Если в `/speckit.plan` выяснится, что `FOR UPDATE` деградирует конкурентность (например, частые lock-wait на популярных песнях) — переключиться на optimistic.
- **Q2**: Достаточно ли блокировать `FOR UPDATE` на уровне `Song.saveToDb`, или нужна блокировка также на чтение (`FOR SHARE` / `FOR NO KEY UPDATE`)? `FOR NO KEY UPDATE` — компромисс (разрешает другим FOR SHARE/FOR NO KEY UPDATE, но блокирует FOR UPDATE/DELETE) — подходит, если UPDATE не меняет ключи (что верно для `tbl_songs.id` — он никогда не меняется).
- **Q3**: Нужно ли в KDoc FR-021 для каждого места явно описывать race-сценарий, или достаточно `@see specs/299`? Дефолт — да, явно описывать, для образовательной ценности.
