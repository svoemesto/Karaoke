---

description: "Task list for feature 143-song-free-access-window"
---

# Tasks: Временное окно бесплатного доступа к песням

**Input**: Design documents from `/specs/143-song-free-access-window/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/public-api.md, quickstart.md

**Tests**: Проект не использует автотесты в CI (constitution.md «Тесты» — только ручная проверка). Тестовых тасков нет; вместо них — задачи "Manual validation" со ссылкой на конкретный раздел quickstart.md в конце каждой фазы.

**Organization**: Задачи сгруппированы по user story из spec.md (US1-US6, в порядке приоритета P1→P2→P3).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Можно делать параллельно (разные файлы, нет зависимости от незавершённых задач)
- **[Story]**: US1-US6 — соответствие user story из spec.md
- Точные пути файлов — в каждой задаче

---

## Phase 1: Setup

- [X] T001 Убедиться, что локальный dev-стек поднят (db/karaoke-app/karaoke-web/webvue3/karaoke-public контейнеры) — прерогативы для ручной проверки по quickstart.md; при необходимости поднять через scoped-команды `deploy/do.sh` (`build_X`/`start_X`, НЕ безусловные `build_start_app`/`start`)

---

## Phase 2: Foundational (Blocking Prerequisites)

**⚠️ CRITICAL**: Блокирует все user story ниже.

- [X] T002 Добавить вычисляемые свойства `freeAccessWindowEnd: Date?`, `isFreelyAvailableNow: Boolean`, `freeAccessWindowEndText: String?` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt` рядом с `onAir` (`:613-634`), по формулам из data-model.md («Новые вычисляемые поля»). **НЕ трогать** `onAir`/`isPubliclyWatchable` (research.md Decision 1 — используются триггером авто-новости specs/089)

**Checkpoint**: Foundation готов — можно параллельно начинать любую user story ниже.

---

## Phase 3: User Story 1 - Бесплатный доступ ограничен окном после эфира (Priority: P1) 🎯 MVP

**Goal**: Плеер и страница песни решают доступность по правилу «всегда-бесплатно ИЛИ (в эфире И внутри месячного окна)», а не по старому «в эфире = бесплатно навсегда».

**Independent Test**: `/api/public/player/{id}/access` отдаёт `canWatch=false` для готовой песни, эфир которой был больше месяца назад (без флага «всегда бесплатно»), и `canWatch=true` для такой же песни, но с эфиром внутри месяца — под анонимным/обычным пользователем без подписки.

### Implementation for User Story 1

- [X] T003 [US1] В `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicPlayerController.kt` метод `access()` (`:134`): заменить `song.onAir` на `song.isFreelyAvailableNow` в формуле `canWatch`
- [X] T004 [US1] В том же файле метод `readiness()` (`:218`): та же замена `song!!.onAir` → `song!!.isFreelyAvailableNow` в формуле `watchable`
- [X] T005 [US1] В `karaoke-public/src/views/SongView.vue` добавить computed `playerReady()` → `this.playerAccess.ready.value` (рядом с `playerCanWatch`/`playerAccessLoaded`, `:517-528`)
- [X] T006 [US1] В `SongView.vue` сузить условие VK-видео-фоллбека (`:336`) с `currentSong.onAir && !playerCanWatch && playerAccessLoaded` до `currentSong.onAir && !playerReady && playerAccessLoaded` (research.md Decision 6 — фоллбек только когда контент физически не готов)
- [X] T007 [US1] В `SongView.vue` расширить условие карточки ожидания (`:387`) с `!currentSong.onAir && !playerCanWatch && !playerIsDemo && playerAccessLoaded` до `(!currentSong.onAir || playerReady) && !playerCanWatch && !playerIsDemo && playerAccessLoaded`
- [X] T008 [US1] В `SongView.vue` computed `waitingTitle`/`waitingBody` (`:534-548`): заменить дискриминатор `s.exclusive` на `s.onAir`; текст заголовка остаётся «Эта песня доступна только по подписке» (FR-015, дословно старый текст); текст `waitingBody` для этой ветки — нейтральный «Оформите подписку, чтобы посмотреть эту песню.» (не подразумевает, что песня никогда не была бесплатной)
- [X] T009 [US1] Manual validation: quickstart.md разделы 1, 3, 6 (доступ к плееру для песен A-E под 3 ролями; регресс на странице песни для «не готово» vs «окно истекло»; регресс авто-новости «в эфире»)

**Checkpoint**: US1 работает независимо — платный гейт отражает новое правило и на API, и на странице песни.

---

## Phase 4: User Story 2 - Песня, помеченная «всегда бесплатно» (Priority: P1)

**Goal**: Админ может явно и понятно пометить песню как бессрочно бесплатную.

**Independent Test**: В форме редактирования песни включить «Всегда бесплатно» у песни без даты эфира — убедиться, что `isFreelyAvailableNow` (и, соответственно, `canWatch` из US1) даёт `true` анониму.

*Примечание: сама логика "free ⇒ всегда доступна" уже реализована в T002 (Foundational) и работает через T003/T004 (US1) — этой фазе остаётся только переименовать переключатель в админке, чтобы он не вводил в заблуждение формулировкой "на sponsr".*

### Implementation for User Story 2

- [X] T010 [P] [US2] В `webvue3/src/components/Songs/edit/SongEdit.vue` (`:2154`) переименовать лейбл «Бесплатно на sponsr:» → «Всегда бесплатно (вечный эфир):»; `setFree`/`freeButtonClass`/привязку к `song.free` не менять
- [X] T011 [US2] Manual validation: quickstart.md раздел 5 (последний пункт — сохранить `free=true` без даты эфира, проверить доступ анониму) + раздел 1, песня E

**Checkpoint**: US1 и US2 вместе работают независимо.

---

## Phase 5: User Story 3 - Удаление флага «эксклюзив» из админки и моделей данных (Priority: P2)

**Goal**: Флаг `exclusive` полностью убран из бизнес-логики, DTO и admin UI (столбец/фильтр/переключатель). DB-колонка не трогается (research.md Decision 2).

**Independent Test**: В таблице песен, фильтре и форме редактирования нет элементов управления «эксклюзив»; ответ API редактирования песни не содержит поля `exclusive`; проект компилируется.

### Implementation for User Story 3

- [X] T012 [US3] В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt` убрать: свойство `exclusive` (геттер/сеттер, `:854-860`), геттер `flagExclusive` (`:1695`), ветки `exclusive`/`exclusive && free` в геттере `datePublish` (`:1799-1808`, оставить только «дата не определена»/`"$date $time"`), ветку `exclusive` в `getVKGroupDescriptionSponsr()` (`:4834`), 2 ветки `SongState.EXCLUSIVE`/`EXCLUSIVE_FREE` в геттере `state` (`:5957-5958`), строку `RecordDiff("exclusive", ...)` (`:6922`), запись `exclusive` в INSERT/UPDATE Pair-списке и `rs.getBoolean("exclusive")` в row-load (`~5849-5931`, `:7877`) — см. data-model.md «Удаляемые поля/ветвления»
- [X] T013 [P] [US3] В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SongState.kt` убрать `EXCLUSIVE`/`EXCLUSIVE_FREE` (`:27-28`)
- [X] T014 [P] [US3] В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SongField.kt` убрать `EXCLUSIVE` (`:81`)
- [X] T015 [P] [US3] В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SongDTO.kt` убрать поле `exclusive` (объявление `:175`, `fromDto` `:279`, конструктор `:420`)
- [X] T016 [P] [US3] В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SongDTOdigest.kt` убрать поля `exclusive`/`flagExclusive` (`:59,125`), оставить `free`/`flagFree`
- [X] T017 [US3] В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Zakroma.kt` убрать поле `exclusive` из `ZakromaAlbumSong` и его присвоение (`:199,285`)
- [X] T018 [P] [US3] В `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/SongPublicDto.kt` убрать поле `exclusive` (`:21,97`)
- [X] T019 [P] [US3] В `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/ZakromaPublicDto.kt` убрать поле `exclusive` из `ZakromaAlbumSongPublicDto` (`:18,148`)
- [X] T020 [US3] В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt` убрать query-параметр `exclusive: String?` и его обработку (`:2969,3098`) и параметр `flagExclusive` из двух list/digest-эндпоинтов (`:2426,2606`)
- [X] T021 [US3] В `webvue3/src/components/Songs/SongsTable.vue` убрать колонку `flagExclusive` (шаблон `:492-501`, определение поля `:1319-1329`)
- [X] T022 [US3] В `webvue3/src/components/Songs/filter/SongsFilterModal.vue` убрать поле фильтра Exclusive (`:400-408,709-714,851-852,915,961,1028`)
- [X] T023 [P] [US3] В `webvue3/src/components/Songs/filter/store.js` убрать `songsFilterFlagExclusive` (state/getter/mutation/action, `:36,125-126,250-252,356-357`)
- [X] T024 [US3] В `webvue3/src/components/Songs/edit/SongEdit.vue` убрать переключатель «Эксклюзивно на sponsr» + `setExclusive`/`exclusiveButtonClass` (`:2133-2152,3995-3997,4015-4017`); убрать `v-if="!song.exclusive"` на `links-tabs-widget` (`:671`, виджет теперь показывается всегда)
- [X] T025 [US3] Manual validation: `./gradlew karaoke-app:compileKotlin karaoke-web:compileKotlin` без ошибок; quickstart.md раздел 5 (нет элементов «эксклюзив» в таблице/фильтре/форме)

**Checkpoint**: US1/US2/US3 вместе работают независимо, флага `exclusive` в коде больше нет.

---

## Phase 6: User Story 4 - Правила доступа описаны на сайте (Priority: P2)

**Goal**: Раздел «О проекте» объясняет новое правило бесплатного доступа.

**Independent Test**: Открыть `/about` — виден абзац с правилом (эфир + месяц бесплатно, отдельные песни — всегда, остальное — по подписке), без упоминания MP4/скачивания.

### Implementation for User Story 4

- [X] T026 [P] [US4] В `karaoke-public/src/views/AboutView.vue` добавить абзац с описанием правила бесплатного доступа рядом с секцией статистики (после `:118`) — без упоминания MP4/скачивания (constitution.md «Только онлайн»)
- [X] T027 [US4] Manual validation: открыть `/about`, убедиться в наличии и читаемости текста (SC-004 spec.md)

**Checkpoint**: US1/US2/US3/US4 вместе работают независимо.

---

## Phase 7: User Story 5 - Корректные счётчики песен (Priority: P2)

**Goal**: Счётчики на главной/«О проекте»/`/api/public/stats` отражают новое правило без пересечений категорий.

**Independent Test**: `curl /api/public/stats` — `freeNow`/`subscriptionOnly`/`inWork` в сумме дают `collection`/`total` без расхождений (SC-003 spec.md); карточки на `/` и `/about` показывают те же числа.

### Implementation for User Story 5

- [X] T028 [US5] В `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/StatBySong.kt` переписать SQL и кеш-поля: `cachedOnAir`→`cachedFreeNow` (условие `free=true OR (публикация истекла AND публикация + INTERVAL '1 month' > now())`), `cachedExclusive`→`cachedSubscriptionOnly` (`= collection − freeNow`); переименовать `getCountSongsOnAir`/`getCountSongsExclusive` соответственно (research.md Decision 5)
- [X] T029 [US5] В `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt` переименовать ключи ответа `/api/public/stats`: `onAir`→`freeNow`, `exclusive`→`subscriptionOnly`
- [X] T030 [P] [US5] В `karaoke-public/src/store/modules/stats.js` переименовать state/getters/mutations: `onAir`→`freeNow`, `exclusive`→`subscriptionOnly`
- [X] T031 [P] [US5] В `karaoke-public/src/views/HomeView.vue` обновить `mapGetters('stats', [...])` (`:187`) и привязки в шаблоне (`:44-62`) под новые имена
- [X] T032 [US5] В `karaoke-public/src/views/AboutView.vue` обновить `stats.onAir`/`stats.exclusive` → `stats.freeNow`/`stats.subscriptionOnly` (`:106,110`) — тот же файл, что T026, делать после него
- [X] T033 [US5] Manual validation: quickstart.md разделы 4 и 7 (`curl /api/public/stats`, сверка с `/` и `/about`, проверка тождества SC-003)

**Checkpoint**: US1-US5 вместе работают независимо.

---

## Phase 8: User Story 6 - Статус доступности в таблице закромов (Priority: P3)

**Goal**: Непремиум-пользователь без подписки видит «Будет в эфире с …» / «В эфире до …» в Закромах и Поиске; премиум/купившие/всегда-бесплатные — ничего.

**Independent Test**: В Закромах под анонимным пользователем — песня без даты эфира по подписке (без текста), с будущей датой («Будет в эфире с»), внутри окна («В эфире до»), всегда-бесплатная/купленная (без текста); под премиум — без текста везде.

*Зависит от Foundational (T002). Не блокируется US3, но по порядку фаз (P2 раньше P3) выполняется уже после удаления `exclusive` из DTO — избегает временного дублирования полей.*

### Implementation for User Story 6

- [X] T034 [US6] В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Zakroma.kt` добавить поля `alwaysFree`/`freelyAvailableNow`/`freeAccessWindowEndText` в `ZakromaAlbumSong` и заполнить их из `song.free`/`song.isFreelyAvailableNow`/`song.freeAccessWindowEndText` (рядом с `:198-201`)
- [X] T035 [US6] В `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/ZakromaPublicDto.kt` добавить те же 3 поля в `ZakromaAlbumSongPublicDto` (объявление + маппинг во `fromZakroma`)
- [X] T036 [P] [US6] В `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/SongPublicDto.kt` добавить те же 3 поля в `SongPublicDto` (для консистентности, data-model.md)
- [X] T037 [US6] В `karaoke-public/src/views/ZakromaView.vue` переписать `showCoin`/`showDate` (`:579-615`) и привязку текста в шаблоне (`:254-255,318-319`) по contracts/public-api.md diff — `showDate` теперь также гейтится `!isPremium` (FR-010, было доступно и премиуму)
- [X] T038 [US6] В `karaoke-public/src/views/SearchView.vue` та же правка `showCoin`/`showDate` + шаблон (`:122-197,311-341`) — идентичная логика, копия ZakromaView.vue (feedback-память: проверять ВСЕ независимые копии алгоритма)
- [X] T039 [US6] Manual validation: quickstart.md раздел 2 (Закрома для анонима и премиума, песни A-E)

**Checkpoint**: Все 6 user story работают независимо и вместе.

---

## Phase 9: Polish & Cross-Cutting Concerns

- [X] T040 [P] Создать `docs/features/song-free-access.md` (Что/Зачем/Как/Инварианты/Ловушки/Ссылки по формату `specs/001-code-standards-docs/contracts/per-feature-doc.md`) — явно зафиксировать инвариант «не путать `isFreelyAvailableNow` с `isPubliclyWatchable`/`onAir`» (research.md Decision 1)
- [X] T041 [P] Добавить строку в таблицу `docs/features/README.md` (20-я подсистема, slug `song-free-access`)
- [X] T042 Пройтись KDoc/JSDoc по всем новым/изменённым публичным символам (свойства `Song.kt`, поля DTO, computed во Vue) со ссылкой `@see docs/features/song-free-access.md` (Constitution Principle VI)
- [X] T043 Прогнать полный pre-commit чек-лист из `CLAUDE.md`: `./gradlew ktlintCheck`, `cd webvue3 && npm run lint:check && npx prettier --check "src/**/*.{vue,js,ts,json}"`, `cd karaoke-public && npm run lint:check && npx prettier --check "src/**/*.{vue,js,ts,json}"`, `bash tools/check-kdoc-coverage.sh`, `bash tools/check-jsdoc-coverage.sh webvue3`, `bash tools/check-jsdoc-coverage.sh karaoke-public`, `pre-commit run --all-files`
- [X] T044 Финальный полный прогон quickstart.md (все 7 разделов) после того, как все 6 user story смёржены — регресс-проверка целиком

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: без зависимостей
- **Foundational (Phase 2)**: зависит от Setup — БЛОКИРУЕТ все user story
- **US1/US2 (Phase 3-4, P1)**: зависят только от Foundational, полностью независимы друг от друга
- **US3 (Phase 5, P2)**: зависит только от Foundational (не от US1/US2 — компилируется независимо; T012 трогает `Song.kt`, который T003/T004 не редактируют)
- **US4/US5 (Phase 6-7, P2)**: независимы от US1-US3 (свои файлы), T032 (US5) идёт после T026 (US4) — один файл `AboutView.vue`
- **US6 (Phase 8, P3)**: зависит от Foundational (T002); по порядку выполнения — после US3 (Phase 5), чтобы не было временного дублирования поля `exclusive`/новых полей в одних DTO (не строгая блокировка, но такой порядок рекомендуется)
- **Polish (Phase 9)**: после всех желаемых user story

### Within Each User Story

- US3: T012 (Song.kt) логически предшествует T015/T016/T018/T019 (DTO ссылаются на `Song.exclusive`/`ZakromaAlbumSong.exclusive` в своих factory-методах) и T017 (Zakroma.kt) — держать этот порядок при выполнении, даже если помечено `[P]` относительно других файлов
- US6: T034 (Zakroma.kt) предшествует T035 (ZakromaPublicDto.kt, маппит из `ZakromaAlbumSong`)

### Parallel Opportunities

- Внутри US3: T013/T014/T015/T016/T018/T019/T023 — разные файлы, можно параллельно (после T012/T017)
- Внутри US5: T030/T031 — разные файлы, параллельно
- US1 и US2 — можно вести параллельно (разные файлы, оба зависят только от Foundational)
- US4 и US5 частично параллельны (пересекаются только в `AboutView.vue`, T026 перед T032)
- Polish: T040/T041 — параллельно

---

## Parallel Example: User Story 3

```bash
# После T012 (Song.kt) и T017 (Zakroma.kt) — параллельно:
Task: "Remove EXCLUSIVE/EXCLUSIVE_FREE from SongState.kt"
Task: "Remove EXCLUSIVE from SongField.kt"
Task: "Remove exclusive field from SongDTO.kt"
Task: "Remove exclusive/flagExclusive fields from SongDTOdigest.kt"
Task: "Remove exclusive field from SongPublicDto.kt"
Task: "Remove exclusive field from ZakromaAlbumSongPublicDto (ZakromaPublicDto.kt)"
Task: "Remove songsFilterFlagExclusive from filter/store.js"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1 (Setup) → Phase 2 (Foundational) → Phase 3 (US1)
2. **STOP and VALIDATE**: quickstart.md разделы 1/3/6 — окно бесплатного доступа работает через API и на странице песни
3. На этом этапе `exclusive` ещё присутствует в коде (не мешает, US1 его не трогает) — можно деплоить MVP без US3, если нужно быстро проверить саму бизнес-логику

### Incremental Delivery

1. Setup + Foundational → база готова
2. US1 → core-правило работает (MVP)
3. US2 → админ может явно пометить «всегда бесплатно»
4. US3 → флаг `exclusive` убран отовсюду
5. US4 → правило объяснено на сайте
6. US5 → счётчики корректны
7. US6 → закрома показывают статус построчно
8. Polish → документация, линтеры, финальный регресс

### Parallel Team Strategy

После Foundational: один разработчик — US1+US2 (P1, тесно связаны), второй — US3 (самая объёмная механическая правка), третий — US4+US5 (тексты + счётчики). US6 — после US3 (см. Dependencies).

---

## Notes

- Никаких новых зависимостей/миграций БД не требуется (research.md Decision 2)
- `[P]` расставлен по признаку «разные файлы», НЕ по признаку «безопасно компилируется в любом порядке» — см. явные примечания о порядке в разделе Dependencies
- Коммитить после каждой завершённой user story (checkpoint), не после каждой отдельной задачи
