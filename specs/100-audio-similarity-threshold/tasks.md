# Tasks: Повышение порога аудио-похожести и демотация статуса при импорте из папки

**Input**: Design documents from `/specs/100-audio-similarity-threshold/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/behavior-contract.md, quickstart.md

**Tests**: Тесты не запрашивались. CI-тестов в проекте нет (конституция: «Тесты: в CI нет … проверка делается пользователем вручную»). Тестовые задачи НЕ генерируются; проверка — через `quickstart.md` (ручные сценарии) + ktlint/KDoc-coverage (CI-эквивалент).

**Organization**: Задачи сгруппированы по user stories из spec.md. Обе истории P1, но spec.md явно указывает (US2): «Жёсткий порог — фундамент для User Story 1». Поэтому фазы идут в порядке **US2 (порог) → US1 (демотация)** — это перестановка для корректной семантики зависимостей, не изменение приоритетов.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2)
- Include exact file paths in descriptions

## Path Conventions

Single-module change в `karaoke-app` (Kotlin). Все пути — от корня репозитория.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Подтверждение готовности окружения; проект уже существует, создавать структуру не нужно.

- [x] T001 Проверить, что находимся на ветке `100-audio-similarity-threshold` и `spec.md`/`plan.md` актуальны (`git branch --show-current`, `git status`)
- [x] T002 [P] Прочитать `CONTRIBUTING.md` (Kotlin-стиль: KDoc с `@see`, `redirectErrorStream(true)`, JSON-ключи) и `AGENTS.md` (CI-gate для master, ktlint/KDoc-coverage) — обновить mental model перед правками

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Нет blocking prerequisites вне самих правок US — сущности/БД/инфраструктура не меняются (data-model.md: «Миграции нет», «Схема не меняется»). Фаза пуста по существу; единственное «блокирующее» — убедиться, что текущий код соответствует ожиданиям plan.md перед правками.

- [x] T003 [P] Верифицировать текущее состояние кода: `AUDIO_PARENT_THRESHOLD = 85` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt:4612`, `applyAudioParentMarkers` ставит `"6"` в `Utils.kt:4406`, дефолты `threshold = 85` в `Utils.kt:4536` и `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt:4878` — grep/Read подтвердить, что это единственные места с literals `85`/`"6"` в аудио-пути (см. research.md R4 для полного списка KDoc-упоминаний)

**Checkpoint**: Код в ожидаемом состоянии, можно править US.

---

## Phase 3: User Story 2 - Порог аудио-похожести поднят до 95% (Priority: P1) 🎯 MVP

**Goal**: Повысить порог `AUDIO_PARENT_THRESHOLD` с 85 до 95 (включительно, `>=`) и поднять дефолт параметра `threshold` функции `autoAssignOriginalByWaveform` / эндпоинта `/songs/autoassignoriginalall` с 85 до 95 (параметр остаётся параметризованным). Это фундамент для US1 (демотация): без жёсткого порога демотация не имеет смысла.

**Independent Test**: Импортировать песню с кандидатом 90% → аудио-родитель НЕ назначается (было бы назначено при 85). Вызвать `/song/findaudioparent` → `reason` содержит «95%». Вызвать `/songs/autoassignoriginalall?threshold=80` → параметр honoured (см. quickstart.md сценарии 2, 3, 4).

### Implementation for User Story 2

- [x] T004 [US2] Изменить константу `AUDIO_PARENT_THRESHOLD` с `85` на `95` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt:4612`
- [x] T005 [US2] Изменить дефолт параметра `threshold: Int = 85` → `95` в сигнатуре `fun autoAssignOriginalByWaveform(...)` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt:4536`
- [x] T006 [P] [US2] Изменить дефолт `@RequestParam(required = false) threshold: Int = 85` → `95` в `autoAssignOriginalAll` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt:4878`
- [x] T007 [US2] Обновить KDoc `AUDIO_PARENT_THRESHOLD` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt:4611` — убрать упоминание «85%», согласовать с новым значением 95 (research.md R4, contracts/behavior-contract.md Контракт 5)
- [x] T008 [US2] Обновить KDoc `findAudioParentByWaveform` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt:4635` — заменить «(85%)» на «(95%)» (research.md R4)
- [x] T009 [US2] Обновить KDoc `autoAssignOriginalByWaveform` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt:4520-4530` — заменить «Порог по умолчанию — 85 %» на «95 %» (research.md R4)
- [x] T010 [P] [US2] Обновить комментарий `findAudioParent` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt:752-754` — заменить «порог 85%» на «95%» (research.md R4)
- [x] T011 [P] [US2] Обновить KDoc `autoAssignOriginalAll` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt:4868-4873` — уточнить «по умолчанию 95%» (параметр остаётся параметризованным, FR-001a)

**Checkpoint**: US2 завершён. Порог 95 подхвачен во всех четырёх путях (импорт, customFunction, findaudioparent, autoassignoriginalall-дефолт). `reason`-строки автоматически интерполируют «95%» из константы/параметра (хардкода «85» в строках нет — research.md R4). Демотация статуса ещё НЕ сделана — импорт ≥95% всё ещё даёт статус 6 (это исправляется в US1).

---

## Phase 4: User Story 1 - Импорт песни с похожим аудио (бывший статус 6) (Priority: P1)

**Goal**: В `applyAudioParentMarkers` заменить `"6"` на `"5"` — импортированная из папки песня с найденным аудио-родителем (≥6 статуса, сходство ≥95%) получает статус 5 (предфинальная вычитка) вместо 6 (READY). Копирование текста/маркеров и сдвиг `shiftMarkersAndFixEnd` сохраняются. Иные пути статуса 6 не затрагиваются (FR-008).

**Independent Test**: Импортировать папку с песней, у которой есть готовый (статус 6) аудио-родитель с ≥95% сходства → импортированная песня получает текст/маркеры родителя и `id_status = 5` (не 6). См. quickstart.md сценарий 1.

**Depends on**: US2 (порог 95) — без него демотация не имеет смысла (см. spec.md US2 «Why this priority»).

### Implementation for User Story 1

- [x] T012 [US1] Изменить literal статуса `"6"` → `"5"` в `settings.fields[SongField.ID_STATUS] = "6"` внутри `applyAudioParentMarkers` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt:4406`
- [x] T013 [US1] Обновить KDoc `applyAudioParentMarkers` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt:4384-4394` — заменить «статус выставляется в 6 (READY)» на «в 5 (маркеры проверены)»; переформулировать обоснование: копирование проверенного контента от аудио-подтверждённого родителя теперь НЕ обход ручных проверок, а осознанная остановка на предфинальной вычитке куратором (FR-003, research.md R3, contracts/behavior-contract.md Контракт 5)
- [x] T014 [P] [US1] Обновить комментарий в `doCreateFromFolder` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt:5266-5269` — заменить «переводим песню в статус 6» на «в статус 5 (предфинальная вычитка)» (research.md R4)

**Checkpoint**: US1 завершён. Обе user stories реализованы. Импорт ≥95% → статус 5.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Проверка консистентности, CI-гейт, ручная валидация.

- [x] T015 [P] Проверить, что в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt` и `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt` не осталось устаревших упоминаний «85%»/«статус 6» в связи с аудио-родителем (grep по контексту «аудио-родитель|порог|AUDIO_PARENT» — не по подстроке «85», см. quickstart.md сценарий 5 про допустимые «85» в `Color`/`Constants`)
- [x] T016 Запустить `./gradlew ktlintCheck` — MUST быть 0 нарушений (FR-007 конституции)
- [x] T017 [P] Запустить `bash tools/check-kdoc-coverage.sh` — MUST быть 100% (FR-006 конституции); если падает — KDoc в T007/T008/T009/T013 обновлён некорректно
- [x] T018 [P] Запустить `./gradlew :karaoke-app:compileKotlin` — компиляция MUST пройти (синтаксис/типы после правок literals/дефолтов)
- [ ] T019 Выполнить ручные сценарии 1–6 из `specs/100-audio-similarity-threshold/quickstart.md` на admin-машине (на `dev-pc`/`dev` — агент сам; иначе — пользователь): импорт ≥95% → статус 5; импорт 85–94% → не назначается; `findaudioparent` reason «95%»; `autoassignoriginalall` дефолт 95 + `?threshold=80` honoured; grep логов на «95%»; ktlint+KDoc
- [ ] T020 [P] Обновить `docs/architecture-notes.md` — добавить запись о PR (Pass 33+, дата 2026-07-31, краткое описание: «Порог аудио-похожести 85→95 + демотация статуса 6→5 при импорте из папки; дефолт autoassignoriginalall 85→95»). Это documentation-only commit — допускается прямо в master после merge PR с кодом (AGENTS.md «CI-gate для master», исключение для `docs/architecture-notes.md`)

**Checkpoint**: Фича готова к PR. Создать feature-ветку уже создана (`100-audio-similarity-threshold`), PR в master, дождаться CI 7/7 SUCCESS, merge.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: нет зависимостей — можно начать сразу
- **Foundational (Phase 2)**: зависит от Phase 1 (верификация текущего состояния кода)
- **US2 (Phase 3, MVP)**: зависит от Phase 2 — повышение порога
- **US1 (Phase 4)**: зависит от **US2** (semantical dependency из spec.md: «Жёсткий порог — фундамент для User Story 1»). Технически правки в одном файле (`Utils.kt`), но семантически US1 имеет смысл только после US2
- **Polish (Phase 5)**: зависит от US1 + US2 — валидация всей фичи

### User Story Dependencies

- **User Story 2 (P1, порог)**: Can start after Foundational (Phase 2). **No dependencies on other stories.** Это MVP.
- **User Story 1 (P1, демотация)**: Depends on **US2** (порог должен быть 95, иначе демотация 6→5 не имеет смысла — см. spec.md US2 «Why this priority»)

### Within Each User Story

- KDoc-обновления (T007–T011 для US2, T013–T014 для US1) идут ПОСЛЕ изменения literals (T004–T006, T012) — чтобы KDoc описывал уже-новое значение
- KDoc-задачи одного файла могут идти параллельно с KDoc-задачами другого файла ([P])

### Parallel Opportunities

- T002 || T003 (Phase 1/2 — разные активности: чтение доков vs верификация кода)
- T006 || T010 || T011 (US2 — все в `ApiController.kt`, но разные строки; один файл → строго говоря не [P] по rules «different files», но разные функции — помечены [P] как независимые правки в одном файле. Если lint требует serial — выполнить последовательно)
- T014 (US1, `ApiController.kt`) — можно параллельно с US2-задачами в `Utils.kt`, но US1 зависит от US2 по семантике → ждать US2
- T015 || T017 || T018 (Polish — разные проверки: grep / KDoc-coverage / компиляция)
- T020 (Polish — `docs/architecture-notes.md`) — после merge PR, параллельно с T019 если T019 уже идёт

---

## Parallel Example: User Story 2

```bash
# После T004–T005 (правки literals в Utils.kt), KDoc-обновления можно запустить параллельно по разным файлам:
Task: "T007 KDoc AUDIO_PARENT_THRESHOLD в Utils.kt:4611"
Task: "T010 комментарий findAudioParent в ApiController.kt:752-754"
Task: "T011 KDoc autoAssignOriginalAll в ApiController.kt:4868-4873"
```

---

## Implementation Strategy

### MVP First (User Story 2 Only)

1. Complete Phase 1: Setup (T001–T002)
2. Complete Phase 2: Foundational (T003)
3. Complete Phase 3: User Story 2 (T004–T011) — порог 95 во всех путях
4. **STOP and VALIDATE**: quickstart.md сценарии 2, 3, 4 (порог отсекает 85–94%, reason «95%», `autoassignoriginalall` дефолт 95 + `?threshold=80` honoured)
5. На этом этапе импорт ≥95% всё ещё даёт статус 6 — это ожидаемо (US1 ещё не сделан)

### Incremental Delivery

1. Setup + Foundational → верификация кода
2. + US2 (порог 95) → Test (сценарии 2–4) → Demо (MVP — жёсткий порог отсекает ложные срабатывания)
3. + US1 (демотация 6→5) → Test (сценарий 1) → Deploy/Demo (полная фича)
4. Polish (T015–T020) → PR + CI 7/7 → merge → `docs/architecture-notes.md`

### Parallel Team Strategy

Фича мала (2 файла, ~14 задач) — параллельная работа нескольких разработчиков избыточна. Реалистично один разработчик последовательно T001 → T020. [P]-метки указывают, какие задачи теоретически независимы, но serial execution в одном PR предпочтительнее (избегает merge-конфликтов в одних и тех же KDoc-блоках).

---

## Notes

- Все задачи — в `karaoke-app` (Kotlin). `webvue3`, `karaoke-public`, `karaoke-web`, `deploy/` — НЕ трогаются (plan.md Project Structure, contracts/behavior-contract.md Контракт 6)
- Per-feature документ `docs/features/<slug>.md` НЕ создаётся — аудио-родитель не входит в 13 ключевых подсистем `docs/features/README.md` (FR-009 не применим, plan.md Constitution Check Principle VI)
- Миграции БД нет (data-model.md) — все правки в коде (константы, дефолты параметров, literal статуса, KDoc)
- CI-gate для master: PR обязателен, дождаться CI 7/7 SUCCESS перед merge (AGENTS.md). T020 (`docs/architecture-notes.md`) — единственное исключение, допустимое прямым коммитом в master после merge PR с кодом
- Не коммитить без явного запроса пользователя (конституция «Git — не коммитить без явного запроса»)
- `redirectErrorStream(true)` — в данной фиче новых `ProcessBuilder` нет (конституция Principle IV, проверка не требуется)