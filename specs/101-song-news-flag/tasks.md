---

description: "Task list for feature implementation"
---

# Tasks: Флаг «песня доступна» для авто-новостей + очистка ленты и таблицы учёта

**Input**: Design documents from `/specs/101-song-news-flag/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/news-lifecycle.md, quickstart.md

**Tests**: Не запрошены явно ни в спецификации, ни пользователем — отдельные test-таски не создаются; вместо них в каждой фазе есть задача ручной проверки по `quickstart.md` (в проекте нет CI-тестов для этого слоя, см. Constitution → «Рабочий процесс» → «Тесты»).

**Organization**: Задачи сгруппированы по user story (US1/US2/US3 из spec.md — все три Priority: P1, но независимо тестируемы). Схема `tbl_songs` не меняется (флаг живёт внутри уже существующего JSON-поля `player_readiness_flags`, см. data-model.md) — единственная схемная миграция (US3) удаляет старую таблицу и очищает ленту.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: можно выполнять параллельно (разные файлы, нет зависимости от незавершённых задач)
- **[Story]**: к какой user story относится задача (US1/US2/US3)
- Указаны точные пути файлов

---

## Phase 1: Setup

**Purpose**: Свериться с текущим состоянием переиспользуемой инфраструктуры (specs/089/092/094/095/096/098) перед заменой механизма «в эфире» и добавлением механизма «доступна»

- [X] T001 Свериться с текущим состоянием `SongReleaseAnnouncementService` (`checkAndAnnounce`, `forEachNewlyReadyCandidate`, `backfillExistingReadySongs`), `SongNewsAnnounced`, `News.createAutoAnnouncement`, `MainController.doChangeRecords`, `SongEditorController.approve()` и `SongReleaseAnnouncementScheduler` — подтвердить, что сигнатуры и поведение не изменились с момента `research.md` (`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/SongReleaseAnnouncementService.kt`, `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SongNewsAnnounced.kt`, `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/News.kt`, `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/MainController.kt`, `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/SongEditorController.kt`, `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/SongReleaseAnnouncementScheduler.kt`)

**Checkpoint**: базовая инфраструктура подтверждена неизменной — можно приступать к Foundational-фазе

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Общая инфраструктура, без которой ни US1 (флаг+детекция), ни US2 (категория `"air"` через параметризованный `createAutoAnnouncement`), ни US3 (backfill флага) не могут быть реализованы

**⚠️ CRITICAL**: Обе задачи ниже блокируют все три user story

- [X] T002 [P] Добавить свойство `newsAvailableAnnounced: Boolean` в `Song` через уже существующие приватные хелперы `readinessFlag()`/`setReadinessFlag()` (тот же паттерн, что `stemAccompanimentReady`/`stemVocalReady`/`pictureAlbumReady`/`pictureAuthorReady`, ~строки 896-910) — ключ `"newsAvailableAnnounced"` внутри JSON-поля `player_readiness_flags`, без новой колонки и без правки recordhash-триггера (см. research.md п.1, data-model.md) — `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt`
- [X] T003 [P] Добавить параметр `category: String = "air"` в `News.createAutoAnnouncement` (сохраняя обратную совместимость со старым единственным вызовом до его правки в US2) — `entity.category = category` вместо жёстко закодированного `"air"` (см. research.md п.6) — `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/News.kt` (~строки 330-349)

**Checkpoint**: Foundation готова — US1/US2/US3 можно реализовывать в любом порядке или параллельно

---

## Phase 3: User Story 1 - Новость «песня появилась в коллекции» создаётся автоматически (Priority: P1) 🎯 MVP

**Goal**: Как только песня локально достигает полной готовности контента (статус 6 + все флаги готовности плеера), это автоматически фиксируется, а при следующей синхронизации на сервере создаётся ровно одна новость «песня появилась в коллекции» — независимо от даты эфира.

**Independent Test**: Шаг 3 quickstart.md — довести тестовую песню до полной готовности локально, синхронизировать, убедиться, что в публичной ленте появилась ровно одна новость с бейджем «Премиум», повторная синхронизация не создаёт вторую.

### Implementation for User Story 1

- [X] T004 [US1] В `Song.saveToDb()` — до вычисления `diff` (~строка 5005, обе ветки: путь обновления существующей записи и путь `id == 0L` первого создания) — добавить проверку: если `idStatus == 6L && stemAccompanimentReady && stemVocalReady && pictureAlbumReady && pictureAuthorReady && sourceMarkersList.isNotEmpty() && !newsAvailableAnnounced`, установить `newsAvailableAnnounced = true` на сохраняемом объекте (FR-002/FR-003 spec.md, data-model.md «Условие установки в true») — `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt` (зависит от T002)
- [X] T005 [US1] Добавить в `SongReleaseAnnouncementService` функцию (например `detectAndAnnounceAvailability`), принимающую `database`/`storageService`/`storageApiClient` + идентификатор песни + «старое» значение флага (переданное вызывающим кодом до применения обновления): читает текущее (уже применённое) значение `newsAvailableAnnounced` этой строки, и если старое было `false`/отсутствовало, а новое — `true`, вызывает `News.createAutoAnnouncement(songId, title, body, link = "/song?id=$id", category = "premium", ...)` ровно один раз (см. contracts/news-lifecycle.md п.1-2, research.md п.2) — `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/SongReleaseAnnouncementService.kt` (зависит от T002, T003)
- [X] T006 [US1] В `MainController.doChangeRecords` — для каждой записи `dataUpdate` с `tableName == "tbl_songs"`: прочитать текущее (до применения) значение `newsAvailableAnnounced` строки одним точечным `SELECT` по `idRecord` **до** выполнения её `UPDATE`; после применения — вызвать `detectAndAnnounceAvailability` (T005) с этим старым значением. Для записей `dataCreate`, вставляющих строку в `tbl_songs` — «старое» значение считать `false` по определению (см. contracts/news-lifecycle.md п.2) — `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/MainController.kt` (~строки 266-328, зависит от T005; редактируется в том же методе, что и T011 из US2 — не выполнять параллельно с ней)
- [X] T007 [US1] Ручная проверка по `quickstart.md`, Шаг 3 — убедиться, что новость «доступна» (категория «Премиум») создаётся ровно один раз при первой синхронизации после достижения готовности, и не дублируется при повторной синхронизации (зависит от T004, T006)
  Живая проверка на LOCAL sandbox-стенде (dev-pc, изолированная БД `karaoke-db`, НЕ прод):
  вызов `/api/song/update?id=10709` (без изменения других полей) корректно перевёл
  `newsAvailableAnnounced` false→true через обычный `saveToDb()`, новость при этом НЕ создана
  (T004 — детекция строго на уровне flags, без сети). Затем прямой вызов `POST /changerecords`
  с `dataUpdate` по песне 10773 (флаг false→true) создал ровно одну новость: «Новая песня: 7Б —
  Субмарина (альбом «Я умираю, но не сдаюсь!», 2020)» / «...появилась в коллекции.»,
  `category=premium`, `source=auto`, `song_id=10773`. Повторный идентичный вызов `/changerecords`
  — новость не задвоилась (сервер уже хранил `true`, переход не обнаружен повторно). Тестовые
  строки/флаги удалены после проверки (см. T020).

**Checkpoint**: User Story 1 полностью функциональна и проверяема независимо

---

## Phase 4: User Story 2 - Новость «песня вышла в эфир» продолжает работать, но только по таймеру или вручную (Priority: P1)

**Goal**: Плановая проверка на проде (~раз в 5 минут) создаёт новость «песня вышла в эфир» для песен, чья дата/время эфира попали в последнее скользящее окно (~10 минут) — без отдельной таблицы учёта. Синхронизация и апрув задания редактора больше не создают эту новость напрямую.

**Independent Test**: Шаги 4-7 quickstart.md — подготовить готовую песню с близкой датой эфира, убедиться, что новость появляется только после срабатывания планового тика (не от синхронизации), не дублируется на следующем тике, блокируется предварительно созданной вручную новостью, и не создаётся задним числом при пропущенном окне.

### Implementation for User Story 2

- [X] T008 [P] [US2] Добавить в `SongReleaseAnnouncementService` дешёвую первую фазу скана: `SELECT id, publish_date, publish_time, id_status FROM tbl_songs WHERE id_status >= 6` (без текста/маркеров/base64 — тот же принцип, что `Song.listHashes`), с парсингом `dateTimePublish` тем же способом, что и `Song.onAir` (`dd.MM.yy` + `HH:mm`) — возвращает id кандидатов, чей `dateTimePublish` попадает в окно `(now - 10 минут, now]` (см. research.md п.4) — `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/SongReleaseAnnouncementService.kt`
- [X] T009 [US2] Заменить `checkAndAnnounce`/`forEachNewlyReadyCandidate`/`backfillExistingReadySongs` (весь старый механизм, завязанный на `SongNewsAnnounced`) на новую функцию (например `checkOnAirWindow`): использует кандидатов из T008, загружает полные объекты чанками по 25 (как раньше), фильтрует `isPubliclyWatchable`, и для каждого — `SELECT 1 FROM tbl_news WHERE song_id = ? AND category = 'air'`; если строка уже есть — пропустить, иначе `News.createAutoAnnouncement(..., category = "air")` (см. contracts/news-lifecycle.md п.3) — `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/SongReleaseAnnouncementService.kt` (зависит от T003, T008)
- [X] T010 [P] [US2] Обновить `SongReleaseAnnouncementScheduler.checkOnAir()` — вызывать новую `checkOnAirWindow` вместо `checkAndAnnounce` — `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/SongReleaseAnnouncementScheduler.kt` (зависит от T009)
- [X] T011 [US2] Убрать из `MainController.doChangeRecords` старый вызов `SongReleaseAnnouncementService.checkAndAnnounce(...)` (создание новости «в эфире» при синхронизации, FR-007 spec.md) — `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/MainController.kt` (~строки 315-323, редактируется в том же методе, что и T006 из US1 — не выполнять параллельно с ней)
- [X] T012 [P] [US2] Убрать из `SongEditorController.approve()` фоновый вызов `checkAndAnnounce` вместе с инфраструктурой `checkAndAnnounceRunning` (`AtomicBoolean`, добавлена specs/098) и связанными импортами — мёртвый код по FR-007 spec.md (создание новости «в эфире» при апруве больше не предусмотрено) — `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/SongEditorController.kt` (~строки 402-467)
- [X] T013 [US2] Удалить файл `SongNewsAnnounced.kt` целиком — не используется ни одним вызывающим кодом после T009/T011/T012 — `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SongNewsAnnounced.kt` (зависит от T009, T011, T012)
- [X] T014 [US2] Ручная проверка по `quickstart.md`, Шаги 4-7 — убедиться, что новость «в эфире» создаётся только по таймеру или вручную (не синхронизацией/апрувом), не дублируется, блокируется предварительно созданной вручную новостью, и не создаётся задним числом при пропущенном окне (зависит от T010, T011, T012, T013)
  Живая проверка на LOCAL sandbox-стенде: (а) уже вышедшие в эфир песни (11130 шт. по
  `StatBySong.refreshCache`) — за два прошедших тика `checkOnAir` (~5 мин каждый) не создано НИ
  ОДНОЙ новости (`tbl_news` оставалась пустой) — окно `(now-10мин, now]` физически не захватывает
  старые события, задним числом лавины нет. (б) Песне 10709 выставлена `publish_date/time` = «сейчас»
  → следующий тик планировщика создал ровно одну новость «...вышла в эфир.», `category=air`,
  `source=auto`. (в) **Найден и исправлен реальный баг**: `News.existsAutoAnnouncement` матчил только
  по `song_id`, а ручная новость (`NewsController.create`) не имеет поля для `song_id` (только
  `link`) — из-за этого при первой попытке проверить блокировку ручной новостью планировщик всё
  же создал дублирующую авто-новость (see история `tbl_news.id=4` в проверке). Исправлено —
  `News.existsAnnouncement(songId, link, category)` матчит по `song_id` ИЛИ `link`. После
  пересборки/передеплоя обоих контейнеров с фиксом — контрольный тик планировщика (≥5 минут
  наблюдения) НЕ создал новых строк: ручная новость (`link=/song?id=10773`) и уже существующая
  авто-новость обе корректно распознаются как «уже покрыто», дублей больше не возникает. Тестовые
  данные удалены после проверки.

**Checkpoint**: User Stories 1 и 2 обе работают независимо — оба механизма используют новые, более простые правила идемпотентности

---

## Phase 5: User Story 3 - Очистка ленты новостей без лавины новостей задним числом (Priority: P1)

**Goal**: Один раз почистить переполненную ленту новостей и удалить служебную таблицу учёта — без единой новости задним числом ни по одному из двух видов события.

**Independent Test**: Шаги 0-2 quickstart.md — выполнить backfill флага «доступна» на LOCAL и на PROD, затем применить миграцию очистки, убедиться, что ни одна новость не появилась как побочный эффект этих двух шагов.

### Implementation for User Story 3

- [X] T015 [P] [US3] Добавить в `SongReleaseAnnouncementService` функцию `backfillNewsAvailableFlag(database, storageService, storageApiClient): Int` — дешёвый двухфазный скан (как в T008/T009, но фильтр `id_status >= 6`), полные объекты чанками, для тех, что удовлетворяют `Song.isContentReady` и у которых `newsAvailableAnnounced == false` — устанавливает `newsAvailableAnnounced = true` и вызывает обычный `saveToDb()` (НЕ raw SQL — см. research.md п.3, почему важно переиспользовать `saveToDb()`); возвращает количество затронутых песен — `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/SongReleaseAnnouncementService.kt` (зависит от T002, T004)
- [X] T016 [US3] Добавить админский эндпоинт (по образцу `POST /utils/recalcplayerreadiness`, ~строка 5498) — например `POST /utils/backfillnewsavailable`, с параметром `target` (`local`/`remote`), запускающий `backfillNewsAvailableFlag` в фоновом потоке с логированием начала/конца и SSE-тостом по завершении (тот же паттерн try/catch-устойчивости, что уже есть у `doRecalcPlayerReadiness`) — `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt` (зависит от T015)
- [X] T017 [P] [US3] Создать миграцию `deploy/karaoke-db/34_cleanup_song_news_announced.sql`: `DROP TABLE IF EXISTS public.tbl_song_news_announced;` и `TRUNCATE TABLE public.tbl_news RESTART IDENTITY;`, с комментарием-заголовком (по стилю существующих миграций каталога), явно указывающим порядок операций — применять **после** выполнения backfill'а (T016) на каждой БД, не до — `deploy/karaoke-db/34_cleanup_song_news_announced.sql`
- [X] T018 [US3] Ручная проверка по `quickstart.md`, Шаги 0-2 — выполнить backfill (T016) на LOCAL и на PROD, убедиться, что ни одна строка не появилась в `tbl_news`; применить миграцию (T017) на обеих БД; убедиться, что `tbl_song_news_announced` больше не существует и `tbl_news` пуста (зависит от T016, T017)
  Выполнено на LOCAL sandbox-стенде (dev-pc, `karaoke-db`, НЕ прод — PROD не трогался, требует
  отдельного согласия пользователя на каждое действие). `POST /api/utils/backfillnewsavailable`
  (target=local): затронуто 14732 из 15196 песен со статусом 6 (464 песни статуса 6 не прошли
  `isContentReady` — нет персистентных флагов готовности, ожидаемо); `tbl_news` осталась пустой (0
  строк) до и после — новостей не создано. После этого применена миграция
  `34_cleanup_song_news_announced.sql`: `tbl_song_news_announced` (была 11123 строки) удалена,
  `tbl_news` (была 4 строки) очищена. Backfill на PROD НЕ выполнялся в этой сессии (нужно отдельное
  согласие пользователя на каждое действие с прод-БД) — эндпоинт готов, вызывается тем же способом
  с `target=remote`.

**Checkpoint**: Все три user story независимо функциональны — очистка выполнена без побочных новостей

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Проверки, затрагивающие все три user story одновременно, и обязательные по Constitution документационные обновления

- [X] T019 [P] Обновить `docs/features/dual-db-sync.md` (FR-009 Конституции — per-feature документ обновляется в том же PR, что и код фичи): заменить описание старого единого механизма «в эфире» (кумулятивная таблица `tbl_song_news_announced`) на описание двух независимых механизмов — флаг «доступна» на песне + узкое скользящее окно для «в эфире», со ссылкой на `specs/101-song-news-flag`
- [X] T020 Регрессия: прогнать Шаги 3-7 `quickstart.md` подряд на одном и том же наборе тестовых песен, включая случай, когда обе новости («доступна» и «в эфире») формируются для одной песни примерно одновременно — убедиться, что создаются ровно две отдельные новости (FR-013 spec.md), а не одна и не дубли (зависит от T007, T014, T018)
  Живая проверка на LOCAL sandbox-стенде, песня 13122 («7Б — Я люблю её»): триггер «доступна»
  (переход флага через `/changerecords`) и триггер «в эфире» (тик планировщика в окне ~10 мин)
  применены к ОДНОЙ и той же песне — результат: ровно 2 отдельные строки `tbl_news`
  (`category=premium`/«появилась в коллекции» и `category=air`/«вышла в эфир»), никакого
  взаимного блокирования или задвоения. Тестовые строки и синтетические флаги удалены после
  проверки — `tbl_news` возвращена в исходное (пустое, после миграции) состояние. Полный набор
  живых проверок в этой сессии: T007 (доступна, включая идемпотентность), T014 (в эфире, включая
  найденный и исправленный баг с ручными новостями), T018 (backfill LOCAL + миграция, без лавины
  на 15196 песнях/11130 уже-в-эфире), T020 (независимость двух видов новости) — все на LOCAL
  sandbox-стенде (dev-pc, изолированная БД, WORKING_DATABASE в этом стенде физически не
  указывает на прод). PROD не затрагивался.
- [X] T021 Прогнать `./gradlew ktlintCheck` и `bash tools/check-kdoc-coverage.sh` для всех изменённых/новых/удалённых файлов (`Song.kt`, `News.kt`, `SongReleaseAnnouncementService.kt`, `MainController.kt`, `SongEditorController.kt`, `SongReleaseAnnouncementScheduler.kt`, `ApiController.kt`; `SongNewsAnnounced.kt` удалён) перед коммитом — по чек-листу CONTRIBUTING.md/CLAUDE.md проекта (зависит от T002-T017)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: без зависимостей — можно начинать сразу
- **Foundational (Phase 2)**: зависит от Setup — блокирует все три user story
- **User Stories (Phase 3-5)**: каждая зависит только от Foundational; между собой почти не зависят, за одним исключением — T006 (US1) и T011 (US2) редактируют один и тот же метод `doChangeRecords` и не должны выполняться параллельно (хотя логически независимы и могут идти в любом порядке)
- **Polish (Phase 6)**: зависит от завершения всех трёх user story

### User Story Dependencies

- **User Story 1 (P1)**: после Foundational — не зависит от US2/US3 (кроме файлового пересечения T006/T011, см. выше)
- **User Story 2 (P1)**: после Foundational — не зависит от US1/US3 (кроме того же файлового пересечения)
- **User Story 3 (P1)**: после Foundational — не зависит от US1/US2 напрямую, но T018 (проверка backfill) логически должна выполняться **до** прогона T017 (миграция) на реальной БД — порядок операций, не порядок разработки кода

### Within Each User Story

- Реализация → ручная проверка по quickstart.md (тесты в CI не запрошены и не предусмотрены проектом для этого слоя)

### Parallel Opportunities

- T002, T003 (Foundational, разные файлы) — параллельно
- T004 (US1, `Song.kt`), T008 (US2, новая функция в `SongReleaseAnnouncementService.kt`), T015 (US3, тоже в `SongReleaseAnnouncementService.kt`, но независимая функция) — T004 параллельно с T008/T015; T008 и T015 в одном файле, но разные функции — по факту безопасны для параллельной работы разных разработчиков с последующим слиянием, помечены [P] с этой оговоркой
- T010, T012, T017 — разные файлы, параллельно с чем угодно после своих прямых зависимостей
- T019 (документация) может идти параллельно с любой user story

---

## Parallel Example: Foundational + начало всех трёх user story

```bash
# Foundational — параллельно:
Task: "Добавить newsAvailableAnnounced в Song.kt"
Task: "Добавить параметр category в News.createAutoAnnouncement"

# После Foundational — старт трёх user story параллельно (с оговоркой про T006/T011 выше):
Task: "Триггер флага «доступна» в Song.saveToDb()"                       # US1
Task: "Дешёвая первая фаза скана окна «в эфире» в SongReleaseAnnouncementService.kt"  # US2
Task: "Backfill-функция флага «доступна» в SongReleaseAnnouncementService.kt"        # US3
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Завершить Phase 1: Setup
2. Завершить Phase 2: Foundational (блокирует всё)
3. Завершить Phase 3: User Story 1
4. **STOP и ПРОВЕРИТЬ**: прогнать Шаг 3 quickstart.md независимо
5. Задеплоить/показать пользователю, если готово — но помнить, что старый механизм «в эфире» (с `tbl_song_news_announced`) при этом ещё продолжает работать по-старому (US2 не реализована) — это безопасное промежуточное состояние, просто без нового вида новости

### Incremental Delivery

1. Setup + Foundational → база готова
2. Добавить US1 → проверить независимо → новый вид новости «доступна» уже приносит пользу
3. Добавить US2 → проверить независимо → «в эфире» стало проще и не зависит от синхронизации/апрува
4. Добавить US3 → проверить независимо → лента очищена без побочных новостей
5. Phase 6 — регрессия + обязательное обновление per-feature документа перед PR

**Важно**: несмотря на то, что каждая story тестируется независимо, **боевое включение** (миграция T017 + backfill T016) требует, чтобы US1 и US2 уже были задеплоены — иначе очистка `tbl_news`/удаление `tbl_song_news_announced` оставит старый код (если он ещё не заменён) без нужной ему таблицы. См. quickstart.md, Шаг 0 «порядок применения».

### Parallel Team Strategy

При нескольких разработчиках: после Foundational US1/US2/US3 можно раздать трём разработчикам
одновременно, договорившись отдельно о слиянии T006 (US1) и T011 (US2) в `MainController.doChangeRecords`
— единственное фактическое файловое пересечение.

---

## Notes

- [P] задачи = разные файлы (или независимые функции в одном файле с последующим слиянием), нет
  блокирующих зависимостей
- [Story] метка привязывает задачу к конкретной user story для трассируемости
- Каждая user story независимо завершаема и проверяема
- Единственная схемная миграция — удаление `tbl_song_news_announced` + очистка `tbl_news` (US3);
  `tbl_songs` не меняется
- Коммитить после каждой задачи или логической группы (per CLAUDE.md — не коммитить в master, только
  в feature-ветке `101-song-news-flag`)
- Перед коммитом — полный чек-лист CLAUDE.md (ktlint, KDoc coverage, prettier, pre-commit)
- Backfill (T016) и миграция (T017) выполняются пользователем вручную на LOCAL и на PROD, по прямому
  согласию на каждое действие с PROD (Constitution → «Ограничения и доступы агента») — агент не
  запускает их самостоятельно на серверной БД
