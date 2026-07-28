---

description: "Task list for feature implementation"
---

# Tasks: Исправление автоподстановки найденного текста песни

**Input**: Design documents from `/specs/020-fix-search-lyrics-autofill/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, quickstart.md

**Tests**: Автоматических тестов не запрошено (spec.md/CLAUDE.md: CI не полагается на существующие интеграционные тесты — большинство `@Disabled`). Проверка — ручная, по `quickstart.md` (задача T006).

**Organization**: В spec.md одна user story (P1) — вся фича является MVP.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1)

## Path Conventions

Существующий multi-module проект. Все пути — от корня репозитория, модуль
`karaoke-app` (см. `plan.md` → Project Structure).

---

## Phase 1: Setup

**Purpose**: Базовая проверка перед изменениями (существующий проект, новой инфраструктуры не требуется)

- [X] T001 Убедиться, что ветка `020-fix-search-lyrics-autofill` активна и `karaoke-app` собирается без ошибок (`./gradlew karaoke-app:compileKotlin`) — baseline перед правками

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Единая функция автоподстановки, которую должны использовать все три точки завершения поиска (research.md, Вопрос 1 и 2)

**⚠️ CRITICAL**: Task T003-T005 не могут быть корректно реализованы до завершения T002

- [X] T002 Добавить общую функцию автоподстановки найденного текста в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/UtilsAI.kt`: принимает `Song` + список кандидатов текста, находит первый непустой, и если `!settings.haveSourceText && settings.idStatus == 0L` — устанавливает `settings.sourceText`, `settings.fields[SongField.ID_STATUS] = "1"`, вызывает `settings.saveToDb()`. Снабдить KDoc с `@see docs/features/llm-lyrics-search.md` (FR-006 конституции). Функция переиспользует `Song.haveSourceText` вместо повторной проверки `sourceText == "" || sourceText == "[\"\"]"` (единственный источник истины, см. `data-model.md`)

**Checkpoint**: Общая функция готова — можно переключать все точки завершения поиска на неё

---

## Phase 3: User Story 1 - Автоподстановка независимо от способа хранения "пустого" текста (Priority: P1) 🎯 MVP

**Goal**: Найденный при поиске непустой текст песни автоматически подставляется в `source_text`, если у песни ещё нет текста — причём "ещё нет текста" одинаково определяется и для `''`, и для `'[""]'`, одинаково для всех 4 движков поиска (YANDEX_SYNC, YANDEX_ASYNC, SEARXNG, FOURGET)

**Independent Test**: Взять песню с текстом `'[""]'`, запустить поиск текста любым движком так, чтобы поиск нашёл непустой текст-кандидат; убедиться, что текст песни заполнился найденным текстом, а статус песни продвинулся так же, как если бы текущий текст был пустой строкой `''` (см. `quickstart.md`, сценарии 1-3)

### Implementation for User Story 1

- [X] T003 [US1] В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/UtilsAI.kt`, функция `getLyricsSearchViaSearchTool` (движки SEARXNG/FOURGET, текущая инлайн-логика вокруг `searchedRightResultsNotEmpty`/`settings.sourceText.isBlank()`): заменить инлайн-проверку на вызов общей функции из T002, передав список найденных непустых текстов-кандидатов

- [X] T004 [US1] В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/UtilsAI.kt`, функция `getYandexSearch` (синхронная ветка `async=false`, обработка `apiResponse.rawData`): после сохранения `savedResult` с непустым `rawData` добавить недостающий шаг — разобрать результат через `SearchResult.getSearchResultsForSearchAsync(searchAsync = savedResult)` и вызвать общую функцию из T002 с полученными непустыми кандидатами (сейчас в этой ветке автоподстановки нет вообще, research.md Вопрос 2)

- [X] T005 [P] [US1] В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcessWorker.kt`, обработка завершённого асинхронного Yandex-запроса (ветка `apiResponse.done == true`, `searchedRightResults`): заменить инлайн-проверку `settings.sourceText.isBlank() && settings.idStatus == 0L` на вызов общей функции из T002

- [X] T006 [US1] Прогнать сценарии 1-5 из `specs/020-fix-search-lyrics-autofill/quickstart.md` на локальном стенде (LOCAL БД/`karaoke-app`): подтвердить Acceptance Scenarios 1-4 из `spec.md` — подстановка при `''`, подстановка при `'[""]'`, отсутствие перезаписи при реальном тексте, одинаковое поведение при пакетном запуске «Найти тексты для всех песен». Выполнено на реальном движке FOURGET с реальным веб-поиском (пользователь явно разрешил пересборку/перезапуск `karaoke-app` в этой песочнице): пересобран образ (`do.sh build_app`), перезапущен (`do.sh start_app`), созданы одноразовые тестовые песни (id 22878-22882, удалены после проверки) — все 4 сценария подтверждены: `''`→подстановка (id 22879), `'[""]'`→подстановка (id 22878, id 22882 в батче), реальный текст не перезаписан несмотря на 69 найденных результатов (id 22880), батч на 2 песнях (id 22881/22882) обработал обе одинаково

**Checkpoint**: User Story 1 полностью функциональна и проверена вручную независимо от остальных фаз

---

## Phase 4: Polish & Cross-Cutting Concerns

**Purpose**: Обязательные по конституции проверки и обновление документации

- [X] T007 Обновить `docs/features/llm-lyrics-search.md` (FR-009 конституции — `llm-lyrics-search` входит в 12 ключевых подсистем): зафиксировать единое определение "текста ещё нет" через `Song.haveSourceText` и то, что автоподстановка теперь одинакова для всех 4 движков поиска (включая ранее не покрытый YANDEX_SYNC)

- [X] T008 [P] Прогнать `./gradlew ktlintCheck` и `bash tools/check-kdoc-coverage.sh` для изменённых файлов `karaoke-app` (FR-006/FR-007 конституции) — исправить нарушения при наличии (ktlint — чисто, KDoc-покрытие karaoke-app 96.4%, порог ≥50%)

- [X] T009 Финальная сверка реализации со Success Criteria `SC-001`-`SC-003` из `spec.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: без зависимостей
- **Foundational (Phase 2)**: зависит от Setup — БЛОКИРУЕТ Phase 3 (T003-T005 требуют функцию из T002)
- **User Story 1 (Phase 3)**: зависит от Foundational
- **Polish (Phase 4)**: зависит от завершения Phase 3 (T007 описывает финальное поведение, T009 сверяет готовую реализацию)

### Within Phase 3

- T003 и T004 — оба в `UtilsAI.kt`, выполнять последовательно (один файл — риск конфликта при параллельной правке)
- T005 — другой файл (`KaraokeProcessWorker.kt`), зависит только от T002 → может выполняться параллельно с T003/T004
- T006 (ручная проверка) — после T003, T004 и T005

### Parallel Opportunities

- T005 может выполняться параллельно с T003/T004 (разные файлы, оба зависят только от T002)
- T008 может выполняться параллельно с T007 (независимые проверки)

---

## Parallel Example: Phase 3

```bash
# После завершения T002 (Foundational) — параллельно:
Task: "T004 — правка getYandexSearch (sync) в UtilsAI.kt"
Task: "T005 — правка обработки async Yandex в KaraokeProcessWorker.kt"
# T003 выполняется последовательно с T004 (тот же файл UtilsAI.kt)
```

---

## Implementation Strategy

### MVP = вся фича (единственная user story)

1. Phase 1: Setup — baseline-сборка
2. Phase 2: Foundational — общая функция автоподстановки (T002)
3. Phase 3: User Story 1 — три точки завершения поиска переключены на общую функцию, включая ранее отсутствовавшую ветку YANDEX_SYNC (T003-T005), ручная проверка по `quickstart.md` (T006)
4. Phase 4: Polish — обязательное обновление per-feature документа (FR-009) + линт/KDoc-проверки + финальная сверка Success Criteria

### Инкрементальная проверка

- После T002 — код ещё не скомпилируется с новой функцией, пока не переключены вызывающие места (T003-T005) — это ожидаемо, компилировать/проверять только после T005.
- После T006 — фича полностью проверена вручную, можно переходить к Polish (T007-T009).
