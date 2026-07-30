---

description: "Task list for feature implementation"
---

# Tasks: Автоматические новости о выходе песни в эфир

**Input**: Design documents from `/specs/083-auto-news-song-release/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/news-api.md, quickstart.md

**Tests**: Не запрошены явно ни в спецификации, ни пользователем — отдельные test-таски не создаются; вместо них в каждой фазе есть задача ручной проверки по `quickstart.md` (в проекте нет CI-тестов для этого слоя, см. Constitution → «Рабочий процесс» → «Тесты»).

**Organization**: Задачи сгруппированы по user story (US1/US2/US3 из spec.md), чтобы каждую историю можно было реализовать и проверить независимо.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: можно выполнять параллельно (разные файлы, нет зависимости от незавершённых задач)
- **[Story]**: к какой user story относится задача (US1/US2/US3)
- Указаны точные пути файлов

---

## Phase 1: Setup

**Purpose**: Подготовка схемы БД (черновик миграции, ещё не применённой на PROD)

- [X] T001 Создать SQL-миграцию `deploy/karaoke-db/33_song_news_announced.sql`: `ALTER TABLE tbl_news ADD COLUMN song_id bigint NULL`, `ALTER TABLE tbl_news ADD COLUMN source varchar(20) NOT NULL DEFAULT 'manual'`, `CREATE TABLE tbl_song_news_announced (song_id bigint PRIMARY KEY REFERENCES tbl_songs(id), news_id bigint NULL REFERENCES tbl_news(id), created_at timestamp NOT NULL DEFAULT now())` — по образцу `deploy/karaoke-db/20_news.sql`; явно закомментировать, что новые поля/таблица НЕ входят в `update_tbl_news_recordhash()` (см. data-model.md)
- [X] T002 Применить миграцию `33_song_news_announced.sql` на LOCAL dev-БД (docker-стенд) для разработки/тестирования этой фичи — **не на PROD** (PROD — только по отдельному согласию пользователя, см. Constitution)

**Checkpoint**: схема на LOCAL готова, можно приступать к Foundational-фазе

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Общая инфраструктура, без которой ни одна user story не может быть реализована

**⚠️ CRITICAL**: ни одна из следующих фаз не начинается до завершения этой

- [X] T003 Добавить поля `songId: Long?` и `source: String` в `News` (`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/News.kt`), с KDoc и `@see docs/features/dual-db-sync.md`; поля НЕ должны попасть в SQL, генерируемый `update_tbl_news_recordhash()` (триггер не трогаем) — используются только в Kotlin-коде
- [X] T004 Добавить поле `source: String` в `NewsDto` и прокинуть его в `News.toDTO()` (`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/News.kt`)
- [X] T005 **[КРИТИЧНО, защита от потери данных]** Изменить `News.listHashes(...)` (`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/News.kt`), используемый ТОЛЬКО `NewsSyncTarget`, добавив `WHERE source = 'manual'` (объединяя с уже переданным `whereText`, если он есть) — авто-созданные новости должны быть структурно невидимы для LOCAL↔SERVER sync-движка (см. research.md п.2, риск mirror-delete)
- [X] T006 [P] Вынести единое вычисляемое свойство `Song.isPubliclyWatchable: Boolean` (`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt`), консолидирующее текущую логику `id_status >= 6` + `onAir` + готовность стемов/обложек/маркеров (см. research.md п.4); в KDoc явно указать, что `StatBySong.CONTENT_READY_FILTER` (raw SQL) — независимая параллельная реализация того же условия, которую нужно проверить при будущих изменениях
- [X] T007 Заменить локальную логику `stemsReady`/`canWatch` в `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicPlayerController.kt` на вызов `Song.isPubliclyWatchable` из T006 (устранение дублирования, а не создание третьей копии)
- [X] T008 Создать модель/DAO для `tbl_song_news_announced` (например `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SongNewsAnnounced.kt`, сырой JDBC через `KaraokeConnection`, без JPA): методы `isAnnounced(songId, db): Boolean`, `markAnnounced(songId, newsId: Long?, db): Boolean` (INSERT, идемпотентно — игнорировать конфликт по PK `song_id`), `loadAnnouncedSongIds(db): Set<Long>` (для пакетной фильтрации кандидатов)
- [X] T009 Создать `SongReleaseAnnouncementService` (`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/SongReleaseAnnouncementService.kt`) с методом `checkAndAnnounce(database: KaraokeConnection): List<Long>` по контракту из `contracts/news-api.md`: загрузить кандидатов (`id_status >= 6`, дата эфира наступила — узкий SQL-фильтр через `Song.loadListFromDb`), отфильтровать по `Song.isPubliclyWatchable` (T006) и по отсутствию в `SongNewsAnnounced` (T008), для каждого — создать `News` (`source = "auto"`, `songId = id`, заголовок/текст/ссылка по шаблону из research.md п.6) и вызвать `markAnnounced`; обернуть в try/catch с логированием (не должен ронять вызывающий код)
- [X] T010 [P] Обновить `docs/features/dual-db-sync.md`: добавить раздел про авто-новости — почему они намеренно исключены из `NewsSyncTarget`-scope (FR-009 Конституции, ссылка на `specs/083-auto-news-song-release/research.md`)

**Checkpoint**: фундамент готов — сервис детекции существует и протестирован изолированно, но ещё не подключён ни к `/changerecords`, ни к UI, ни к backfill-процедуре

---

## Phase 3: User Story 1 - Автоматическое оповещение о новой доступной песне (Priority: P1) 🎯 MVP

**Goal**: Когда песня становится публично доступна и происходит очередная синхронизация на PROD, автоматически создаётся новость — без участия администратора, без дублей.

**Independent Test**: Сценарии 1 и 2 из `quickstart.md` — довести тестовую песню до полной готовности, прогнать синхронизацию, убедиться в появлении ровно одной новости; повторный прогон синхронизации не создаёт вторую.

### Implementation for User Story 1

- [X] T011 [US1] Подключить вызов `SongReleaseAnnouncementService.checkAndAnnounce(WORKING_DATABASE)` в конец `doChangeRecords(...)` (`karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/MainController.kt:265`), после применения `dataCreate`/`dataUpdate`/`dataDelete`, обёрнутый в `try/catch` — ошибка детекции анонсов не должна ронять уже применённую синхронизацию и не должна менять формат ответа (см. contracts/news-api.md)
- [X] T012 [US1] Ручная проверка: выполнить Сценарий 1 из `quickstart.md` на локальном dev-стенде (довести тестовую песню до готовности, прогнать `/api/sync/oneclick`, убедиться в появлении новости с `source = 'auto'` в `tbl_news` и строки в `tbl_song_news_announced`)
- [X] T013 [US1] Ручная проверка: выполнить Сценарий 2 из `quickstart.md` (повторная синхронизация той же песни не создаёт вторую новость)
- [X] T013a [US1] Ручная проверка: выполнить Сценарий 6 из `quickstart.md` (FR-006 — две песни становятся готовы за одну синхронизацию → создаются две отдельные новости, не одна сводная; добавлено по итогам `/speckit-analyze`, находка G1)

**Checkpoint**: User Story 1 полностью работает и проверена независимо — это MVP фичи

---

## Phase 4: User Story 2 - Автоматическая новость не отличается от обычной для администратора (Priority: P2)

**Goal**: Администратор видит, редактирует и удаляет авто-созданную новость через тот же интерфейс webvue3, что и обычную.

**Independent Test**: Сценарий из `quickstart.md`, шаг 6 — открыть раздел «Новости» в webvue3 с переключателем на PROD (`target=remote`), найти авто-новость, отредактировать и сохранить как обычную.

### Implementation for User Story 2

- [X] T014 [P] [US2] Добавить визуальный бейдж источника новости («авто»/«ручная», по полю `source` из T004) в `webvue3/src/components/News/NewsTable.vue`
- [X] T015 [US2] ~~Добавить переключатель LOCAL/PROD~~ — уже реализован существующим кодом (`newsTargetModel`/`setNewsTarget` в `NewsTable.vue`/`store.js`), изменений не потребовалось (использующий уже существующий параметр `target` в `NewsController`, см. research.md п.3) в `webvue3/src/views/NewsView.vue` и соответствующий store/API-вызов, если он не принимает `target` уже сейчас
- [X] T016 [US2] Ручная проверка: открыть авто-созданную новость (из Phase 3) через переключатель PROD в webvue3, отредактировать текст, сохранить, убедиться, что изменение применилось так же, как для ручной новости (FR-007)

**Checkpoint**: User Stories 1 и 2 работают вместе — авто-новости создаются и полностью управляемы администратором

---

## Phase 5: User Story 3 - Первое включение механизма не приводит к «спаму» историческими новостями (Priority: P2)

**Goal**: При первом включении механизма на PROD уже опубликованные ранее песни не превращаются в лавину новых новостей.

**Independent Test**: Сценарий 3 из `quickstart.md` — на БД с уже существующими «старыми» готовыми песнями выполнить backfill, прогнать синхронизацию, убедиться в нуле новых авто-новостей по историческим песням.

### Implementation for User Story 3

- [X] T017 [US3] Добавить в `SongReleaseAnnouncementService` (T009) отдельный метод `backfillExistingReadySongs(database: KaraokeConnection): Int` — находит ВСЕ песни, уже удовлетворяющие `Song.isPubliclyWatchable`, и вызывает `SongNewsAnnounced.markAnnounced(songId, newsId = null, db)` для каждой БЕЗ создания записи в `tbl_news` (см. research.md п.5, шаг 3)
- [X] T018 [US3] Документировать одноразовую процедуру запуска backfill (`docs/features/dual-db-sync.md` или отдельный раздел в `specs/083-auto-news-song-release/quickstart.md`): backfill выполняется один раз при включении фичи на PROD, ПЕРЕД тем как `doChangeRecords` начнёт вызывать `checkAndAnnounce` в реальном режиме; запуск на PROD БД — только по прямому согласию пользователя, на каждое выполнение отдельно (см. Constitution → «Ограничения агента»)
- [X] T019 [US3] Ручная проверка: выполнить Сценарии 3 и 5 из `quickstart.md` на локальном dev-стенде (backfill не создаёт видимых новостей по историческим песням; включение `sync_news_push_delete_allowed=true` и последующий «1 клик» не удаляют уже созданную авто-новость — проверка исправления из T005)

**Checkpoint**: все три user story работают независимо и вместе; фичу можно безопасно включать на PROD без лавины исторических новостей и без риска их удаления обычным sync-прогоном

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Финальные проверки перед PR, затрагивающие все user story

- [X] T020 [P] Проверить KDoc-покрытие новых/изменённых публичных символов (`bash tools/check-kdoc-coverage.sh`) для `News.kt`, `Song.kt`, `SongNewsAnnounced.kt`, `SongReleaseAnnouncementService.kt`, `MainController.kt`
- [X] T021 [P] Прогнать `./gradlew ktlintCheck` (Kotlin) и, если менялись `.vue`-файлы из Phase 4, `cd webvue3 && npm run lint:check && npx prettier --check "src/**/*.{vue,js,ts,json}"`
- [X] T022 Полный прогон `quickstart.md` целиком (все 6 сценариев подряд) на локальном dev-стенде, эмулирующем PROD
- [X] T023 Подготовить чек-лист ручного деплоя на PROD (миграция `33_song_news_announced.sql` на PROD БД, backfill T017/T018 на PROD, деплой обновлённого `karaoke-web`) — каждый пункт требует отдельного явного согласия пользователя, агент не выполняет их автономно (см. Constitution → «Категорически запрещено»)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: без зависимостей, начинается сразу
- **Foundational (Phase 2)**: зависит от Setup — БЛОКИРУЕТ все user story
- **User Story 1 (Phase 3)**: зависит только от Foundational
- **User Story 2 (Phase 4)**: зависит от Foundational; для реалистичной ручной проверки (T016) удобнее делать после Phase 3 (нужна хотя бы одна реальная авто-новость), но сам код (T014/T015) можно писать параллельно с Phase 3
- **User Story 3 (Phase 5)**: зависит только от Foundational (использует `SongReleaseAnnouncementService`/`SongNewsAnnounced` из Phase 2); не зависит от Phase 3/4 по коду, но T019 логически проще проверять после того, как Phase 3 подтвердила базовый сценарий
- **Polish (Phase 6)**: зависит от завершения всех желаемых user story

### Within Each User Story

- Модели/DAO (Foundational) → сервис (Foundational) → подключение к точке входа (US1) → UI (US2) → backfill (US3)
- Ручные проверки — после соответствующей реализации

### Parallel Opportunities

- T001 (миграция) можно готовить параллельно с чтением остальных design-документов
- В Foundational: T006 (Song.isPubliclyWatchable) и T008 (SongNewsAnnounced DAO) — разные файлы, без взаимной зависимости, можно параллельно; T010 (документация) — параллельно почти с чем угодно
- T014 (бейдж в NewsTable.vue) и код T009/T011 — разные модули (webvue3 vs backend), можно параллельно
- Phase 3 и Phase 5 (US1 и US3) можно реализовывать параллельно — оба зависят только от Foundational и трогают разные точки подключения (`doChangeRecords` vs отдельный backfill-метод)

---

## Parallel Example: Foundational Phase

```bash
# Можно запускать параллельно (разные файлы):
Task: "Вынести Song.isPubliclyWatchable в karaoke-app/.../model/Song.kt"          # T006
Task: "Создать SongNewsAnnounced DAO в karaoke-app/.../model/SongNewsAnnounced.kt" # T008
Task: "Обновить docs/features/dual-db-sync.md"                                    # T010
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1 (Setup) + Phase 2 (Foundational) — обязательны целиком, включая T005 (защита sync-движка) и T006/T007 (единая логика готовности)
2. Phase 3 (User Story 1) — MVP: авто-новости создаются на дев-стенде и видны на публичном сайте
3. **Остановиться и проверить** по Сценариям 1-2 `quickstart.md`
4. Не разворачивать на PROD без Phase 5 (backfill) — иначе первое включение создаст лавину исторических новостей (см. FR-005/SC-003)

### Incremental Delivery

1. Setup + Foundational → фундамент готов (включая критичную защиту T005)
2. + User Story 1 → MVP-детекция и создание новостей работает на дев-стенде
3. + User Story 3 (backfill) → теперь механизм безопасно включаем на PROD без лавины
4. + User Story 2 (админский UI) → администратор может полноценно управлять авто-новостями
5. Polish → PROD-деплой по чек-листу T023, только с явного согласия пользователя на каждый шаг

---

## Notes

- Тесты не запрошены — вместо них ручные сценарии по `quickstart.md` внутри каждой фазы
- T005 и T006/T007 — самые рискованные задачи Foundational-фазы (защита от потери данных и устранение дублирования условия готовности); не пропускать и не откладывать
- Перед PROD-деплоем и перед PROD-миграцией — обязательное отдельное согласие пользователя на каждое действие (T002 — только LOCAL; T023 — чек-лист, не автономное выполнение)
- Commit — после каждой задачи или логической группы задач, в соответствии с правилами проекта (не коммитить в master, ветка `083-auto-news-song-release`)
