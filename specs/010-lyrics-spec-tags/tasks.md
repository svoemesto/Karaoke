# Tasks: Спецтеги в тексте песни для авто-разметки маркеров

**Input**: Design documents from `/specs/010-lyrics-spec-tags/` (plan.md, spec.md, research.md, data-model.md, contracts/tag-registry.md, quickstart.md)

**Tests**: Не запрошены явно как TDD, но включены как точечные unit-тесты для чистых функций — обязательная регрессионная страховка для инварианта обратной совместимости (см. `plan.md`/`research.md` §8 и Constitution про ненадёжность существующих интеграционных тестов).

**Organization**: Задачи сгруппированы по user story из `spec.md` (US1=P1 newline, US2=P2 group/comment/алиасы, US3=P3 ручной аддитивный пересчёт в редакторе).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: можно выполнять параллельно (разные файлы, без незавершённых зависимостей)
- **[Story]**: US1/US2/US3, только для фазы соответствующей истории

## Path Conventions

Монорепозиторий, существующие модули (см. `plan.md`/Project Structure):
- Backend: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/`
- Backend tests: `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/model/`
- Frontend: `webvue3/src/components/Songs/edit/SubsEdit.vue`
- Docs: `docs/features/`

---

## Phase 1: Setup

**Purpose**: Скелеты новых файлов, не меняющие поведение.

- [X] T001 Создать файл `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SpecTags.kt` (package + пустой `object SpecTags` / `object SpecTagRegistry`, без логики).
- [X] T002 [P] Создать файл `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/model/SpecTagsTest.kt` (скелет, без тестов).
- [X] T003 [P] Решение по документации (см. `plan.md`/Constitution Check VI): `docs/features/` — фиксированный список 12+1 подсистем, механизм спецтегов в него не добавляется как отдельный файл; `docs/architecture-notes.md` документирует только Phase 001/002 (проверено — записей по 003/004/005/008/009 там нет, см. T027), поэтому и туда запись не добавляется — `specs/010-lyrics-spec-tags/` сам служит durable-документом по этой фиче, как и для 003-009; KDoc/JSDoc ссылаются на `specs/010-lyrics-spec-tags/contracts/tag-registry.md`.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Обобщённый механизм "тег на отдельной строке → маркер", без конкретных тегов из реестра — общая инфраструктура для всех user stories.

**⚠️ CRITICAL**: Ни одна user story не должна начинаться до завершения этой фазы.

- [X] T004 В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SpecTags.kt` реализовать парсер строки по грамматике `~(\p{L}+)(?::([^~]*))?~` (см. `contracts/tag-registry.md`): `data class SpecTag(val name: String, val value: String?)`, функция `fun parseLine(line: String): Pair<String, List<SpecTag>>` (строка без найденных тег-токенов + список тегов), сопоставление имени регистронезависимое (`name.lowercase()`).
- [X] T005 В том же файле реализовать структуру базового реестра: `data class TagRegistryEntry(val tagName: String, val validate: (String?) -> Boolean, val markertype: Markertype, val buildLabel: (String?) -> String)` и `object SpecTagRegistry` с функцией `fun resolve(tag: SpecTag): Pair<Markertype, String>?`, возвращающей `null` для неизвестного имени или невалидного значения (FR-008) — реестр пока без записей (заполняется в фазах US1/US2).
- [X] T006 В том же файле реализовать таблицу алиасов: `data class TagAlias(val aliasName: String, val targetTagName: String, val targetValue: String)`, разрешение алиаса — через тот же `SpecTagRegistry.resolve` с подставленным `SpecTag(targetTagName, targetValue)`; алиас со значением (`~Куплет:2~`) не резолвится (см. `contracts/tag-registry.md` §«Алиасы v1»). Таблица пока пустая (заполняется в US2).
- [X] T007 Обобщить `buildTargetWords` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/WhisperMarkerAligner.kt:352-404`: для каждой строки сначала вызывать `SpecTags.parseLine(line)`; решение "пропустить как пустую" принимать по строке-после-снятия-тегов; накопить `Map<Int, List<SpecTag>>` (по `lineIndex`) и вернуть его вместе с `List<TargetWord>` (меняется сигнатура — обновить все 3 вызывающих места: `buildMarkersFromSyllableTimes`, `reconcileText`, `reconcileWithGroundTruth`).
- [X] T008 Обобщить гэп-логику в `buildMarkersFromSyllableTimes` (`WhisperMarkerAligner.kt:93-148`, ветка `nextWord.lineIndex - word.lineIndex > 1` на строке ~132): вместо жёсткой вставки одного `NEWLINE`, перебрать все пропущенные `lineIndex` в диапазоне `(word.lineIndex, nextWord.lineIndex)`; для каждого — если на нём есть распознанные (через `SpecTagRegistry.resolve`, с учётом алиасов из T006) теги, вставить маркер(ы) по каждому; если тегов нет — вставить `NEWLINE`, как сегодня. Время каждого маркера — через существующую `newLineMarkerTime` (строки 539-549), независимо от типа маркера.
- [X] T009 Проверить/адаптировать `reconcileText`/`reconcileWithGroundTruth` (те же построчные вызовы `buildTargetWords`) — убедиться, что строки с тегами корректно исключаются из подсчёта слогов для ASR-сопоставления и не приводят к рассинхрону нумерации (см. `research.md` §4).

**Checkpoint**: Инфраструктура готова — реестр пуст, поведение системы **не изменилось** по сравнению с сегодняшним (обычная пустая строка → `NEWLINE`, как раньше).

---

## Phase 3: User Story 1 - Явный маркер новой строки через тег (Priority: P1) 🎯 MVP

**Goal**: `~newline~` на отдельной строке даёт тот же результат, что и обычная пустая строка.

**Independent Test**: Текст с обычной пустой строкой и текст с `~newline~` вместо неё дают идентичный список маркеров после "Точные маркеры" (quickstart Сценарий B); текст без тегов вообще не меняет поведение (Сценарий A).

- [X] T010 [US1] В `SpecTagRegistry` (`SpecTags.kt`) добавить запись `newline`: без значения (значение при наличии → невалидно/нераспознано), `Markertype.NEWLINE`, `label = ""`.
- [X] T011 [P] [US1] Unit-тест в `SpecTagsTest.kt`: `parseLine("~newline~")` возвращает пустую строку + один `SpecTag("newline", null)`; `parseLine("~newline:x~")` — тег не распознаётся `SpecTagRegistry.resolve`.
- [X] T012 [US1] Unit-тест (в `SpecTagsTest.kt` или новом `WhisperMarkerAlignerTest.kt` в `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/model/`): `buildMarkersFromSyllableTimes` на фикстуре с `~newline~`-строкой даёт идентичный список маркеров тому же тексту с обычной пустой строкой на этом месте (quickstart Сценарий B).
- [X] T013 [US1] Unit-тест: фикстура текста без единого спецтега даёт список маркеров, побайтово совпадающий с зафиксированным до фичи baseline-результатом (регрессия, quickstart Сценарий A, FR-004/FR-010).

**Checkpoint**: US1 работает независимо — "Точные маркеры" распознаёт `~newline~`.

---

## Phase 4: User Story 2 - Границы групп и комментарии через тег (Priority: P2)

**Goal**: `~group:N~`, `~comment:текст~` и человекочитаемые алиасы (`Куплет`/`Припев`/`Бридж`/`Приговор`) дают соответствующие маркеры; некорректные значения безопасно игнорируются.

**Independent Test**: `~group:2~` → маркер группы №2; `~comment:текст~` → маркер комментария; `~group:abc~` → тег не распознан, остальная разметка не нарушена (quickstart Сценарий C); `~Куплет~` ≡ `~group:0~` (quickstart Сценарий C2).

- [X] T014 [US2] В `SpecTagRegistry` добавить запись `group`: значение — обязательное целое число в диапазоне существующих ручных групп (0-4, см. `SubsEdit.vue:2266-2298`), иначе невалидно; `Markertype.SETTING`, `label = "GROUP|$value"`.
- [X] T015 [US2] В `SpecTagRegistry` добавить запись `comment`: значение — обязательная непустая строка после `trim()`, иначе невалидно; `Markertype.SETTING`, `label = "COMMENT|$value"`.
- [X] T016 [US2] Заполнить таблицу алиасов (T006) записями: `Куплет`→(`group`,`"0"`), `Припев`→(`group`,`"1"`), `Бридж`→(`group`,`"2"`), `Приговор`→(`group`,`"3"`) — без алиаса для `group:4` (см. `contracts/tag-registry.md`).
- [X] T017 [US2] Unit-тесты в `SpecTagsTest.kt`: `~group:2~` → `(SETTING, "GROUP|2")`; `~comment:текст~` → `(SETTING, "COMMENT|текст")`; `~group:abc~` → `resolve` возвращает `null` (нераспознан).
- [X] T018 [US2] Unit-тесты: `~Куплет~`/`~куплет~`/`~КУПЛЕТ~` все резолвятся идентично `~group:0~` (та же пара `Markertype`+`label`); `~Куплет:2~` (алиас со значением) не распознаётся.
- [X] T019 [US2] Unit-тест на фикстуре `buildMarkersFromSyllableTimes`: текст с `~group:1~` в одном месте и `~Припев~` в другом даёт корректные независимые маркеры группы №1 в обеих позициях, без ошибок и без влияния на счётчик слогов.

**Checkpoint**: US1 и US2 работают вместе независимо — авто-разметка поддерживает весь v1-реестр тегов и алиасов.

---

## Phase 5: User Story 3 - Автодобавление маркера по тегу при ручном редактировании (Priority: P3)

**Goal**: При правке текста уже размеченной песни в `SubsEdit.vue` (без повторного запуска "Точные маркеры") отсутствующие маркеры по тегам добавляются автоматически, существующие маркеры не трогаются.

**Independent Test**: Добавление `~newline~` в текст уже размеченной песни и пересчёт в редакторе добавляет маркер без изменения остальных (quickstart Сценарий D); маркер, расставленный вручную без соответствующего тега, не удаляется при правке текста в другом месте (Сценарий E).

Реализуется по контракту `contracts/tag-registry.md` (та же грамматика/реестр/алиасы, что и backend из Phase 2-4) — независимо от того, реализован ли уже backend, т.к. это отдельная реализация одного и того же контракта (см. `plan.md`/Structure Decision).

- [X] T020 [US3] В `webvue3/src/components/Songs/edit/SubsEdit.vue` реализовать зеркальный парсер тегов + полный v1-реестр (newline/group/comment) и таблицу алиасов (Куплет/Припев/Бридж/Приговор) — тот же регекс и правила валидации, что в `contracts/tag-registry.md`, размещённые рядом с `getSyllables()` (~1859-1901).
- [X] T021 [US3] Реализовать вычисление тег-якорей в `SubsEdit.vue`: для каждого распознанного тега на отдельной строке — определить, после какого ординального индекса `sourceSyllables` он стоит (тот же принцип, что уже использует `updateMarkersBySyllables()`, ~3136-3194, для сопоставления `syllables`-маркеров по индексу).
- [X] T022 [US3] Реализовать `syncMarkersFromSpecTags()` в `SubsEdit.vue`: для каждого тег-якоря проверить, есть ли уже маркер с тем же `markertype`+`label` на этой ординальной границе; если нет — вычислить время интерполяцией между временами соседних `syllables`-маркеров (аналогично backend `newLineMarkerTime`) и вставить новый маркер в `sourceMarkers` (пересортировать по времени, создать регион как в `addMarker()`, ~3568-3715). Функция строго аддитивна — не удаляет и не изменяет существующие маркеры.
- [X] T023 [US3] Вызывать `syncMarkersFromSpecTags()` при загрузке песни в редактор и в watcher на изменение `sourceText` (~1984-1992), рядом с существующим вызовом `updateMarkersBySyllables()`.
- [ ] T024 [US3] Ручная проверка в браузере по `quickstart.md` Сценариям D и E.

**Checkpoint**: Все три user story работают независимо — ручное редактирование тоже honors тегов.

---

## Phase 5b: `karaoke-public` — третья реализация контракта (добавлено постфактум)

**Почему эта фаза появилась после Phase 5, а не была спланирована сразу**: изначальное исследование (Phase 0/1) сравнивало только backend + `webvue3`. Пользователь явно спросил "а в `karaoke-web`/`karaoke-public` ничего менять не надо?" уже после реализации US1-US3 — выяснилось, что `karaoke-public` содержит собственную, полностью независимую копию слогоделения (`splitSyllables` в `useKaraokeEditor.js`, документированную в самом файле как "точная копия `getSyllables` из `SubsEdit.vue`"), используемую краудсорсинг-редактором заданий (`EditorWorkView.vue`). Без фикса — тот же класс бага, что чинился в US1: тег-строка в `sourceText` (который туда попадает как прямая копия официального текста песни) ломает подсчёт слогов и сдвигает все маркеры. `karaoke-web` (`PublicSongEditorController.kt`) при этом проверен и не требует изменений — чистый JSON passthrough, сам текст не парсит. См. `research.md` §8 и предупреждение в `contracts/tag-registry.md`.

**Goal**: `karaoke-public`-редактор заданий распознаёт те же спецтеги (не ломает слогоделение) и honors их так же аддитивно, как `SubsEdit.vue`.

**Independent Test**: Задание с текстом, содержащим `~newline~`/`~group:N~`/алиас, открывается в `EditorWorkView.vue` без искажения слогов; недостающий маркер по тегу добавляется при загрузке задания и при правке текста, существующие маркеры не удаляются.

- [X] T032 В `karaoke-public/src/composables/useKaraokeEditor.js` реализовать третью копию парсера тегов + полный v1-реестр и таблицу алиасов (тот же регекс/правила, что в `contracts/tag-registry.md`).
- [X] T033 Обновить `splitSyllables()` в том же файле — снимать тег-только строки перед слогоделением (аналогично `buildTargetWords`/`getSyllables` в двух других реализациях), не ломая существующую логику для текста без тегов.
- [X] T034 Реализовать `specTagAnchors()`/`syncMarkersFromSpecTags()` в том же файле — аддитивная синхронизация по ординальному индексу слога (без region-специфики WaveSurfer, т.к. регионы здесь перерисовывает `EditorWorkView.vue` отдельно).
- [X] T035 Подключить `syncMarkersFromSpecTags()` в `EditorWorkView.vue`: после `relabelSyllables` в `loadTask()` (загрузка задания) и в `onTextInput()` (правка текста волонтёром).

**Checkpoint**: Все три независимые реализации контракта синхронизированы.

---

## Phase 5c: Кнопки быстрой вставки тегов (FR-011, добавлено постфактум)

**Почему появилось после Phase 5b**: пользователь явно спросил "а в редакторы добавлены кнопки быстрого добавления тегов?" — до этого механизм требовал печатать `~тег~` руками. Не было в исходном Phase 0/1 исследовании, т.к. изначальный scope — "распознать уже введённый тег", а не "дать способ его ввести".

**Goal**: В обоих текстовых редакторах (`webvue3`/`SubsEdit.vue`, `karaoke-public`/`EditorWorkView.vue`) есть кнопки, вставляющие готовый тег в позицию курсора, гарантированно на отдельной строке.

**Independent Test**: Клик по кнопке "Куплет" в пустом текстовом поле вставляет `~куплет~`; клик в середине существующей строки — вставляет тег на новой отдельной строке (не ломая текущую); повторный вызов остальной пайплайна (пересчёт слогов/маркеров) срабатывает как при обычном печатании тега руками.

- [X] T036 [P] В `webvue3/src/components/Songs/edit/SubsEdit.vue`: чистая функция `insertSpecTagAtCursorImpl` (гарантирует, что тег окажется единственным содержимым своей строки) + метод `insertSpecTagAtCursor(tagBody)`/`insertSpecTagCommentAtCursor()` + панель `se-spectag-toolbar` с 7 кнопками (newline, 4 алиаса группы, group:4, комментарий) рядом с textarea.
- [X] T037 [P] В `karaoke-public/src/composables/useKaraokeEditor.js`: экспортируемая чистая функция `insertSpecTagAtCursor(text, selectionStart, selectionEnd, tagText)` (зеркало T036).
- [X] T038 [US-подобная, но post-hoc] В `karaoke-public/src/views/EditorWorkView.vue`: методы `onInsertSpecTag(tagBody)`/`onInsertSpecTagComment()` (используют импортированную функцию из T037) + панель `ke-spectag-toolbar` с теми же 7 кнопками рядом с textarea задания.
- [X] T039 Линтеры/prettier/сборка (`npx eslint`, `npx prettier --check`, `npm run build`) для всех трёх изменённых файлов (T036-T038) — чисто; JSDoc coverage `webvue3`/`karaoke-public` не упал (100%/100%). `webvue3` пересобран и перезапущен в докере повторно (`do.sh build_webvue3` + `start_webvue3`) после этой фазы.

**Checkpoint**: Оба редактора дают пользователю способ вставить любой тег из реестра v1 без печатания синтаксиса руками.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T025 [P] Добавить KDoc для новых публичных Kotlin-символов (`SpecTags`, `SpecTag`, изменённые сигнатуры `buildTargetWords`/`buildMarkersFromSyllableTimes`) со ссылкой `@see` на `specs/010-lyrics-spec-tags/contracts/tag-registry.md` (FR-006/Constitution Principle VI; см. T003 — новый per-feature документ не создаётся).
- [X] T026 [P] Добавить JSDoc для новых/изменённых функций в `SubsEdit.vue` (`syncMarkersFromSpecTags` и тег-парсер) со ссылкой на тот же контракт.
- [X] T027 Проверено: `docs/architecture-notes.md` документирует только инициативу Phase 001/002 (стандарты кода/CI) — записей по обычным пронумерованным фичам (003/004/005/008/009) там нет ни одной (проверено grep'ом). Спецтеги — обычная пронумерованная фича; отдельной записи не требуется, `specs/010-lyrics-spec-tags/` сам является тем же по смыслу durable-документом, что и для 003-009.
- [X] T028 Прогнать `./gradlew ktlintCheck` и исправить нарушения в изменённых Kotlin-файлах.
- [X] T029 [P] Прогнать `cd webvue3 && npm run lint:check && npx prettier --check "src/**/*.{vue,js,ts,json}"` и исправить нарушения в `SubsEdit.vue`.
- [X] T030 Прогнать `bash tools/check-kdoc-coverage.sh` и `bash tools/check-jsdoc-coverage.sh webvue3` — убедиться, что покрытие не упало по изменённым файлам.
- [X] T030a Прогнать `cd karaoke-public && npm run lint:check && npx prettier --check "src/**/*.{vue,js,ts,json}"` и `bash tools/check-jsdoc-coverage.sh karaoke-public` для файлов из Phase 5b (`useKaraokeEditor.js`, `EditorWorkView.vue`).
- [ ] T031 `quickstart.md`: Сценарии A/B/C/C2 покрыты автоматическими unit-тестами (`WhisperMarkerAlignerSpecTagsTest.kt`, все проходят) — эквивалент ручного прогона для backend-части выполнен. Сценарии D/E (ручное редактирование в `SubsEdit.vue`) и F (краудсорсинг-редактор `karaoke-public`) требуют интерактивной браузерной сессии — не выполнено агентом в этой сессии (нет инструмента управления браузером); и `webvue3`, и `karaoke-public` собраны без ошибок (`npm run build`), `webvue3` дополнительно пересобран и перезапущен в докере (`do.sh build_webvue3` + `start_webvue3`) и готов для проверки пользователем; `karaoke-public` не перезапускался в докере (пользователь не просил, изменение не деплоилось).

---

## Dependencies & Execution Order

- **Setup (Phase 1)** → **Foundational (Phase 2)** — блокирует все user stories.
- **US1 (Phase 3)** и **US2 (Phase 4)** оба редактируют один и тот же новый файл `SpecTags.kt` (реестр) — выполнять последовательно (T010 перед T014-T016), не параллельно как отдельные агенты по одному файлу; но US1 как таковая (P1, MVP) полностью независимо тестируема сразу после Foundational, до начала US2.
- **US3 (Phase 5)** реализует тот же контракт (`contracts/tag-registry.md`) во фронтенде — не имеет файловой зависимости от Phase 3/4 (другой файл, `SubsEdit.vue`), может выполняться параллельно с US1/US2 при наличии второго исполнителя.
- **Polish (Phase 6)** — после всех желаемых user stories.

### Parallel Opportunities

- T002, T003 (Phase 1) — разные файлы, параллельно.
- T011 (US1 тест) — отдельный файл от T010, но логически зависит от него (реестр должен содержать запись); выполнять после T010.
- Phase 5 (US3, `SubsEdit.vue`) может идти параллельно всей цепочке Phase 2→4 (`WhisperMarkerAligner.kt`/`SpecTags.kt`) — разные файлы, общая зависимость только на уже зафиксированный `contracts/tag-registry.md`.
- T025/T026 (KDoc/JSDoc) и T028/T029 (линтеры) — разные наборы файлов (Kotlin vs Vue), параллельно.

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1 (Setup) → Phase 2 (Foundational) → Phase 3 (US1).
2. **Проверить**: quickstart Сценарии A и B проходят.
3. На этом этапе `~newline~` уже работает в "Точные маркеры", `group`/`comment`/алиасы и ручной редактор — ещё нет.

### Incremental Delivery

1. Setup + Foundational → инфраструктура готова, поведение не изменилось.
2. + US1 → `~newline~` работает в авто-разметке (MVP).
3. + US2 → `group`/`comment`/алиасы работают в авто-разметке.
4. + US3 → ручное редактирование в `SubsEdit.vue` тоже honors тегов.
5. + Polish → документация, линтеры, финальная quickstart-проверка.
