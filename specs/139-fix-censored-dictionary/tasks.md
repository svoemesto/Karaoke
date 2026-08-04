---
description: "Task list for feature implementation"
---

# Tasks: Исправление цензурирования {songNameCensored} на продакшене

**Input**: Design documents from `/specs/139-fix-censored-dictionary/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/dictionary-test-endpoint.md, quickstart.md

**Tests**: Не запрошены явно (спека не требует TDD/автотестов; в проекте нет надёжного CI-прогона —
см. Constitution). Проверка — вручную по `quickstart.md`, задачи на это включены в каждую фазу.

**Organization**: Задачи сгруппированы по user story (spec.md) для независимой реализации/проверки
каждой истории.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Может выполняться параллельно (разные файлы, нет зависимости от незавершённых задач)
- **[Story]**: US1 (P1, spec.md) / US2 (P2) / US3 (P3)
- Указаны точные пути к файлам

---

## Phase 1: Setup

**Purpose**: Убедиться, что рабочее дерево чистое и готово к правкам (проект существующий,
дополнительной инициализации не требуется).

- [X] T001 Выполнить `git status` и `git diff --stat` на ветке `139-fix-censored-dictionary`,
      убедиться, что рабочее дерево чистое перед началом правок (Constitution, «Git»).
      **Примечание**: сессия стартовала на `master` (веткой никто не переключался — hook создания
      веток не настроен), branch `139-fix-censored-dictionary` создан отсюда перед началом правок.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Общий сигнатурный фикс, от которого зависят и US1 (реальная публикация), и US2
(admin-инструмент проверки) — параметризация чтения словаря явным `database` вместо модуля-глобала
(research.md R2, data-model.md).

**⚠️ CRITICAL**: Ни US1, ни US2 не могут быть корректно реализованы/провалидированы до завершения
этой фазы.

- [X] T002 В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/textfiledictionary/TextFileDictionary.kt`
      добавить в интерфейс `TextFileDictionary` overridable-свойство
      `val database: KaraokeConnection get() = WORKING_DATABASE` и прокинуть его в `dict`-геттер
      (`Dictionary.loadValues(dictName(), database)` вместо жёсткого `WORKING_DATABASE`). В блоке
      `catch (e: Throwable)` добавить лог уровня ошибки, явно отличимый от «словарь пуст» (например,
      `println("[TextFileDictionary.dict] ошибка чтения словаря '${dictName()}' (database=${database.name}): ${e.message}")`)
      — закрывает FR-002/R3.
- [X] T003 В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/textfiledictionary/CensoredWordsDictionary.kt`
      добавить конструкторный параметр `database: KaraokeConnection = WORKING_DATABASE`,
      реализующий (`override val database`) новое свойство интерфейса из T002 (зависит от T002).
- [X] T004 В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Extentions.kt` добавить параметр
      `database: KaraokeConnection = WORKING_DATABASE` в `fun String.censored()`, передать его в
      `CensoredWordsDictionary(database = database)` (зависит от T003).

**Checkpoint**: `censored()` реально учитывает переданный `database`, а не только глобал — можно
приступать к US1 и US2.

---

## Phase 3: User Story 1 - Цензурированное название реально публикуется без мата (Priority: P1) 🎯 MVP

**Goal**: Реально опубликованные Telegram/ВК-посты и новости на сайте, где название песни содержит
слово из словаря «Censored», получают маскированную форму слова — независимо от того, каким модулем
(`karaoke-app` или `karaoke-web`) сформирован текст.

**Independent Test**: quickstart.md, Сценарии 3 (воспроизведение и фикс пути «новость на сайте») и 5
(регресс: Telegram/ВК с admin-машины продолжают работать).

### Implementation for User Story 1

- [X] T005 [US1] В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/NewsTemplateService.kt`
      добавить параметр `database: KaraokeConnection = WORKING_DATABASE` в `fun render(...)` и в
      `private fun buildReplacements(...)`, прокинуть его в `"songNameCensored" to song.songName.censored(database)`.
- [X] T006 [US1] В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/SongReleaseAnnouncementService.kt`
      передать уже имеющийся в сигнатуре `database` в оба вызова `NewsTemplateService.render(...)`
      внутри `detectAndAnnounceAvailability` (title/body, ~строки 89-97) и `checkOnAirWindow`
      (title/body, ~строки 211-219). Зависит от T005.
- [X] T007 [P] [US1] В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/VkTemplateService.kt`
      добавить параметр `database: KaraokeConnection = WORKING_DATABASE` в render-функцию
      (`renderWithFlags`/аналог) и прокинуть его в вызов `.censored(database)` внутри построения
      `songNameCensored` — для консистентности FR-001 («все текущие потребители»). Зависит от T004.
- [X] T008 [P] [US1] Аналогично T007 — в
      `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/TelegramTemplateService.kt`.
      Зависит от T004.
- [X] T009 [US1] Обновить вызывающие места `VkTemplateService.renderWithFlags(...)` в
      `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/VkAutoPublishService.kt`
      (~строки 84, 178) и аналогичные вызовы render в `TelegramAutoPublishService.kt`, чтобы они
      передавали уже доступный им `database` дальше (по образцу T006). Зависит от T007, T008.
      **Проверено**: оба места уже используют дефолт `database = WORKING_DATABASE` (свой, ранее
      захардкоженный) — новый параметр совпадает с прежним поведением, правок не потребовалось.
- [X] T010 [US1] Вручную выполнить quickstart.md Сценарий 3 (воспроизведение бага на пути «новость
      на сайте» через `checkOnAirWindow`/`tbl_news` — по возможности в тестовом контексте,
      имитирующем `karaoke-web`) и Сценарий 5 (регресс для Telegram/ВК с admin-машины). Зависит от
      T006, T009.
      **Частично**: оба модуля (`karaoke-app`/`karaoke-web`) компилируются без ошибок; временным
      JUnit-тестом (удалён после проверки) подтверждено, что новый параметр `database` реально
      используется и что новое логирование ошибки чтения словаря (T002/FR-002) срабатывает при
      реальном обращении к БД вне полного Spring-контекста (`KSS_APP`/`SAC_APP` недоступны в
      изолированном тесте — тот же класс ограничения, что описан в Constitution: «тестов в CI нет,
      проверка — пользователем в production-like окружении»). Полный live-цикл через
      `SongReleaseAnnouncementScheduler`/`tbl_news` требует пересборки+рестарта `karaoke-web` и
      тестовой песни в окне «в эфире» — оставлено пользователю (Сценарий 5 — регресс для VK/Telegram
      — подтверждён на уровне кода: T009 показал, что вызывающие места не меняют переданный
      `database`, дефолт идентичен прежнему поведению).

**Checkpoint**: US1 полностью реализована и проверяема независимо — основная жалоба пользователя
закрыта.

---

## Phase 4: User Story 2 - Администратор может проверить, что слово реально сработает (Priority: P2)

**Goal**: Администратор проверяет результат цензурирования произвольной строки той же логикой, что
использует реальная автопубликация — без ожидания реальной публикации песни.

**Independent Test**: quickstart.md, Сценарий 2 (вызов нового эндпоинта напрямую).

### Implementation for User Story 2

- [X] T011 [US2] Добавить `POST /api/dictionaries/test` в
      `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/DictionariesController.kt`
      по контракту `contracts/dictionary-test-endpoint.md` (`dictName` + `text` →
      `success`/`dictName`/`input`/`result`/`changed`/`error?`); для `dictName="Censored"`
      использовать `text.censored(database = <local db контроллера>)` (T004). Зависит от T004.
- [X] T012 [US2] Добавить действие `testDictionaryValuePromise` в
      `webvue3/src/components/Dictionaries/store.js` (по образцу существующих
      `createDictionaryItemPromise`/`saveDictionaryItemPromise`, `POST /api/dictionaries/test`).
      Зависит от T011.
- [X] T013 [US2] Добавить в `webvue3/src/components/Dictionaries/DictionariesTable.vue` поле
      «Проверить строку» (ввод текста + кнопка), вызывающее действие из T012 и показывающее `result`
      инлайн. Зависит от T012.
- [X] T014 [US2] Вручную выполнить quickstart.md Сценарий 2. Зависит от T011.
      **Частично**: логика эндпоинта (валидация `dictName` по `TEXT_FILE_DICTS`, ветка `censored()`,
      passthrough для словарей без функции замены) проверена компиляцией + прямым чтением кода;
      живой HTTP-вызов (`curl .../api/dictionaries/test`) требует запущенного `karaoke-app` с полным
      Spring-контекстом (`KSS_APP`/`SAC_APP`) — тот же класс ограничения, что и в T010, оставлен
      пользователю.

**Checkpoint**: US1 и US2 работают независимо; администратор может проверить словарь без реальной
публикации.

---

## Phase 5: User Story 3 - Формат словарных значений понятен при добавлении (Priority: P3)

**Goal**: При добавлении значения в словарь «Censored» без `[...]`-разметки администратор видит
подсказку/предупреждение, что слово не будет визуально маскировано.

**Independent Test**: quickstart.md, Сценарий 6.

### Implementation for User Story 3

- [X] T015 [P] [US3] В `webvue3/src/components/Dictionaries/DictionariesTable.vue` для выбранного
      словаря `dictName === 'Censored'` заменить/дополнить `placeholder` поля ввода значения на
      пример формата (`сл[о]во — [x] маскирует часть слова`) и добавить нестрогое (не блокирующее)
      предупреждение при сохранении значения без `[`/`]` (research.md R5).
- [X] T016 [US3] Вручную выполнить quickstart.md Сценарий 6. Зависит от T015.
      **Подтверждено живьём**: `npm run dev` + headless Chromium (Playwright) на странице
      `/dictionaries` — placeholder поля значения меняется с «Значение» на «сл[о]во — [x] маскирует
      часть слова» при выборе словаря «Censored» (скриншот); новый блок «Проверить строку»
      рендерится корректно (select + input + кнопка, кнопка активируется при заполнении); клик по
      «Проверить» с недоступным бэкендом не роняет страницу (промис поймал ошибку, консоль без
      новых необработанных исключений).

**Checkpoint**: Все три user story работают независимо.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Требования Constitution, применимые ко всем изменённым файлам.

- [X] T017 [P] Добавить/проверить KDoc на всех новых/изменённых публичных символах
      (`String.censored`, `TextFileDictionary.database`, `CensoredWordsDictionary`,
      `NewsTemplateService.render`, `VkTemplateService`/`TelegramTemplateService` render-функции,
      `DictionariesController.test`) — Principle VI / FR-006 конституции.
      **Проверено**: `bash tools/check-kdoc-coverage.sh` → karaoke-app 96.4%, karaoke-web 100%
      (порог 50%); `bash tools/check-jsdoc-coverage.sh webvue3` → 100%.
- [X] T018 Прогнать `./gradlew ktlintCheck` и исправить нарушения в изменённых Kotlin-файлах.
      **Результат**: `BUILD SUCCESSFUL`, нарушений нет.
- [X] T019 [P] Прогнать `cd webvue3 && npm run lint:check && npx prettier --check "src/**/*.{vue,js}"`
      и исправить нарушения в `DictionariesTable.vue`/`store.js`. **Результат**: ESLint — чисто;
      Prettier нашёл 1 нарушение форматирования в `DictionariesTable.vue` — исправлено
      `npx prettier --write`, повторная проверка чистая.
- [X] T020 Обновить `docs/features/news-templates.md` записью об изменении сигнатур
      `NewsTemplateService.render`/`SongReleaseAnnouncementService` (проброс `database` вместо
      глобала) — FR-009 конституции (per-feature документ подсистемы, которую правим).
- [X] T021 Полный прогон всех сценариев `quickstart.md` (1-6) сквозным образом после всех правок.
      Итог: Сценарии 1/4 (механизм `database`-параметра + логирование сбоя) и 6 (UI-подсказка
      формата) подтверждены живьём; Сценарий 2 (эндпоинт) и 5 (регресс VK/Telegram) подтверждены
      компиляцией + прослеживанием кода; Сценарий 3 (полный цикл планировщика → `tbl_news` на
      живом `karaoke-web`) оставлен пользователю — требует пересборки/рестарта контейнера и тестовой
      песни в окне «в эфире» (см. пометки при T010/T014). Оба модуля (`karaoke-app`/`karaoke-web`)
      и их test-source-sets компилируются чисто; `git status` — только запланированные файлы,
      посторонних изменений нет.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: без зависимостей.
- **Foundational (Phase 2)**: зависит от Phase 1. Блокирует US1 (T005+) и US2 (T011+).
- **User Story 1 (Phase 3)**: зависит от Phase 2. Не зависит от US2/US3.
- **User Story 2 (Phase 4)**: зависит от Phase 2 (T004 для `censored(database)`). Не зависит от US1
  по коду, но логически осмысленна только после того, как T004 существует — т.е. может идти
  параллельно с Phase 3, а не строго после неё.
- **User Story 3 (Phase 5)**: зависит только от Setup — чисто UI-подсказка, не зависит от Phase 2/3/4
  по коду (можно делать в любой момент, помещена последней по приоритету P3 из spec.md).
- **Polish (Phase 6)**: зависит от завершения желаемых user story.

### User Story Dependencies

- **US1 (P1)**: после Foundational — без зависимостей от US2/US3.
- **US2 (P2)**: после Foundational (нужен T004) — независима от US1 функционально.
- **US3 (P3)**: независима от всех остальных — можно делать в любой момент.

### Within Foundational

T002 → T003 → T004 (строго последовательно — каждая следующая опирается на API, добавленный
предыдущей).

### Within User Story 1

T005 → T006. T007 и T008 — параллельно друг другу и параллельно с T005/T006 (разные файлы), но все
после T004. T009 — после T007 и T008. T010 (ручная проверка) — после T006 и T009.

### Within User Story 2

T011 → T012 → T013. T014 (ручная проверка) — после T011 (не требует UI).

### Within User Story 3

T015 → T016.

### Parallel Opportunities

- T007 и T008 — параллельно (разные файлы: `VkTemplateService.kt` / `TelegramTemplateService.kt`).
- Phase 4 (US2) и Phase 5 (US3) можно вести параллельно с Phase 3 (US1) после завершения Foundational
  — разные файлы, независимая логика.
- T017 и T019 (Polish) — параллельно (Kotlin vs Vue/JS).

---

## Parallel Example: Foundational → User Story 1

```bash
# После T002-T004 (Foundational, последовательно):
Task: "Добавить database в VkTemplateService render в karaoke-app/.../services/VkTemplateService.kt"
Task: "Добавить database в TelegramTemplateService render в karaoke-app/.../services/TelegramTemplateService.kt"
# — T007 и T008 параллельно, оба зависят только от T004.
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1: Setup
2. Phase 2: Foundational (T002-T004) — критично, блокирует US1 и US2
3. Phase 3: User Story 1 (T005-T010)
4. **STOP и ПРОВЕРИТЬ**: quickstart.md Сценарии 3 и 5 независимо
5. Это и есть закрытие исходной жалобы пользователя — можно остановиться здесь, если US2/US3
   отложены.

### Incremental Delivery

1. Setup + Foundational → фундамент готов (censored() умеет принимать чужой database)
2. + US1 → основной баг исправлен → проверить → это уже ценность сама по себе (MVP)
3. + US2 → админ получает самостоятельный инструмент проверки → проверить
4. + US3 → снижен риск повторения похожего инцидента в будущем → проверить
5. + Polish → KDoc/линтеры/per-feature документ приведены в соответствие Constitution

### Parallel Team Strategy

После Foundational (T002-T004): один разработчик — US1 (T005-T010, самая важная и самая
последовательная цепочка), второй — US2 (T011-T014) и/или US3 (T015-T016) параллельно, т.к. они не
пересекаются по файлам с US1 (кроме `DictionariesTable.vue`, который трогают и US2, и US3 — при
параллельной работе координировать порядок правок в этом одном файле).

---

## Notes

- [P]-задачи = разные файлы, нет зависимости от незавершённых задач.
- [Story]-метка привязывает задачу к конкретной user story из spec.md для трассируемости.
- Тестовых автотестов не запрошено — проверка на каждом чекпоинте вручную по `quickstart.md`.
- Коммитить после каждой задачи или логической группы (Constitution: не коммитить без явного
  запроса пользователя — держать изменения готовыми к коммиту, коммит делать по запросу).
- Перестройка/перезапуск контейнера `karaoke-app` — только с согласия пользователя (Constitution,
  «Ограничения и доступы агента», п.1), кроме случая явного разрешения на машине `dev-pc`/`dev`.
