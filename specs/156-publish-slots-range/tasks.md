---

description: "Task list template for feature implementation"
---

# Tasks: Расширение диапазона свободных слотов публикации

**Input**: Design documents from `/specs/156-publish-slots-range/`

**Prerequisites**: [plan.md](./plan.md) (required), [spec.md](./spec.md) (required for user stories), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/getfreetimeslots.md](./contracts/getfreetimeslots.md)

**Tests**: Тестовые задачи не создаются — в feature spec тесты явно не запрошены, и в CI для этого пути автотестов нет (см. `plan.md` → Technical Context → Testing). Проверка — ручная, по сценариям `quickstart.md`.

**Organization**: Обе User Story в `spec.md` имеют приоритет P1 и реализуются одним и тем же изменением одной функции (`getFreeTimeSlots()` в `Utils.kt`) — код нельзя разделить между ними технически (один SQL-запрос + один цикл вычисления даты обслуживают оба требования одновременно). Поэтому сама реализация вынесена в Foundational-фазу (блокирует обе истории одинаково), а фазы User Story 1 / User Story 2 состоят из независимых задач ручной проверки соответствующих acceptance-сценариев из `spec.md`.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2)
- Include exact file paths in descriptions

## Path Conventions

Single Kotlin backend-модуль `karaoke-app` (см. `plan.md` → Project Structure). Фронтенд (`webvue3`) не меняется.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Подготовка файла к изменению логики (импорты типов даты/времени)

- [X] T001 Добавить импорты `java.time.LocalDate` и `java.time.LocalDateTime` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt` (рядом с существующими `import java.time.Instant` / `import java.time.format.DateTimeFormatter`, см. `research.md` → Вопрос 3)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Единственное изменение кода, от которого зависят обе User Story — новая версия `getFreeTimeSlots()` с расширенным диапазоном часов и гарантией «только будущее»

**⚠️ CRITICAL**: Ни одна из User Story не может считаться проверенной, пока эта фаза не завершена — обе истории проверяют один и тот же результат работы этой функции

- [X] T002 В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt`, функция `getFreeTimeSlots()` (строки ~4769-4867): заменить список из 7 `UNION ALL`-подзапросов (часы `11:00`-`17:00`) одним SQL-запросом вида `SELECT publish_time, MAX(TO_DATE(publish_date, 'DD.MM.YY')) AS last_date FROM tbl_songs WHERE publish_time IN ('10:00','11:00',...,'22:00') GROUP BY publish_time`, покрывающим 13 часов `10:00`-`22:00` включительно; сохранить существующий паттерн работы с соединением (`WORKING_DATABASE`, `Statement`, `ResultSet`, `try/catch/finally`) — см. `research.md` → Вопрос 1, `contracts/getfreetimeslots.md`
- [X] T003 В той же функции `getFreeTimeSlots()` (`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt`): для каждого из 13 часов из результата T002 вычислить `candidate = (lastDate?.plusDays(1) ?: LocalDate.now()).atTime(hour, 0)`, затем в цикле `while (candidate <= LocalDateTime.now()) candidate = candidate.plusDays(1)`, отформатировать как `dd.MM.yy HH:mm` (текущий формат сохраняется) и добавить в результирующий список по возрастанию часа — см. `research.md` → Вопрос 2, `data-model.md` (инвариант `candidateDateTime > now()`); зависит от T002
- [X] T004 Обновить/добавить KDoc для `getFreeTimeSlots()` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt` согласно Principle VI (FR-006 конституции): описать новый диапазон часов (10:00-22:00), правило вычисления кандидат-даты и гарантию «строго в будущем»; per-feature документ не заводится (см. `plan.md` → Complexity Tracking); зависит от T003

**Checkpoint**: `getFreeTimeSlots()` возвращает 13 слотов 10:00-22:00, все даты строго в будущем — обе User Story готовы к независимой проверке

---

## Phase 3: User Story 1 - Расширенный диапазон часовых слотов (Priority: P1) 🎯 MVP

**Goal**: Список подсказок при пустом поле «Дата публикации» содержит по одному варианту на каждый час с 10:00 до 22:00 включительно, включая часы, которые раньше никогда не предлагались

**Independent Test**: Открыть карточку любой песни с пустым полем «Дата публикации», поставить в него фокус, убедиться в 13 вариантах 10:00-22:00 (см. `quickstart.md` → Сценарий 1)

### Проверка для User Story 1

- [X] T005 [US1] Выполнить `quickstart.md` → Сценарий 1: в `webvue3` (`SongEdit.vue`) открыть карточку песни, очистить «Дата публикации», поставить фокус, убедиться, что список подсказок содержит ровно 13 вариантов (10:00…22:00), включая часы без истории (например, 20:00); зависит от T004
- [X] T006 [P] [US1] Проверить контракт напрямую: `curl -s -X POST http://localhost:<PORT>/api/getfreetimeslots | jq` — убедиться, что ответ содержит ровно 13 строк формата `dd.MM.yy HH:mm` по возрастанию часа 10:00…22:00, согласно `contracts/getfreetimeslots.md`; зависит от T004

**Checkpoint**: User Story 1 подтверждена независимо — расширенный диапазон работает

---

## Phase 4: User Story 2 - Предлагаются только будущие даты (Priority: P1)

**Goal**: Все предложенные слоты строго в будущем относительно текущего момента на сервере, независимо от того, когда в последний раз использовался часовой слот

**Independent Test**: Для часового слота с историей месячной давности открыть список подсказок в момент, когда час уже прошёл сегодня, и убедиться, что предложена ближайшая будущая дата (см. `quickstart.md` → Сценарии 2-4)

### Проверка для User Story 2

- [X] T007 [US2] Подготовить тестовые данные (запись `tbl_songs` с `publish_time='10:00'`, `publish_date` = месяц назад, без более поздних записей на этот час) и выполнить `quickstart.md` → Сценарий 2: в момент времени после 10:00 сегодня убедиться, что предложенный слот для 10:00 — завтрашняя дата (акцептанс-сценарий 1 User Story 2 в `spec.md`); зависит от T004
- [X] T008 [US2] Подготовить тестовые данные (запись с `publish_time='15:00'`, `publish_date` = вчера) и выполнить `quickstart.md` → Сценарий 3: в момент времени до 15:00 сегодня убедиться, что предложенный слот для 15:00 — сегодняшняя дата (акцептанс-сценарий 2 User Story 2 в `spec.md`); зависит от T004
- [X] T009 [P] [US2] Выполнить `quickstart.md` → Сценарий 4: для всех 13 предложенных слотов вручную сверить, что каждая дата+время строго позже текущего момента на сервере (SC-002); зависит от T004

**Checkpoint**: User Story 2 подтверждена независимо — гарантия «только будущее» работает для всех веток (есть история / нет истории / история в прошлом)

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Финальные проверки качества перед коммитом/PR (см. `CLAUDE.md` → «ОБЯЗАТЕЛЬНО перед каждым git commit»)

- [X] T010 [P] Прогнать `./gradlew ktlintCheck` и `bash tools/check-kdoc-coverage.sh` для `karaoke-app`, исправить замечания, если появились
- [X] T011 Прогнать все 4 сценария `quickstart.md` целиком ещё раз после T010 (на случай, если правки линтера что-то задели) и зафиксировать, что приёмка пройдена

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: без зависимостей — можно начинать сразу
- **Foundational (Phase 2)**: зависит от Setup (T001); T002 → T003 → T004 строго последовательно (один и тот же файл/функция) — БЛОКИРУЕТ обе User Story
- **User Stories (Phase 3, Phase 4)**: обе зависят только от завершения Foundational (T004); не зависят друг от друга, могут проверяться в любом порядке или параллельно
- **Polish (Phase 5)**: зависит от завершения обеих User Story

### User Story Dependencies

- **User Story 1 (P1)**: может проверяться сразу после Foundational — не зависит от User Story 2
- **User Story 2 (P1)**: может проверяться сразу после Foundational — не зависит от User Story 1 (использует ту же реализацию, но проверяет другой аспект поведения)

### Parallel Opportunities

- T006 (проверка контракта curl) может выполняться параллельно с T005 (проверка через UI) — разные способы проверки одного результата
- T009 (сверка всех 13 слотов) может выполняться параллельно с T007/T008 после того, как тестовые данные из T007/T008 подготовлены
- T010 помечена [P] относительно T011, но T011 логически должна идти после T010 (см. описание) — оставлена как отдельный шаг проверки, не строго параллельный

---

## Parallel Example: User Story 1 + User Story 2 (после Foundational)

```bash
# После завершения T004 обе истории можно проверять одновременно:
Task: "T005 — UI-проверка 13 слотов (quickstart.md Сценарий 1)"
Task: "T007 — данные месячной давности + проверка сдвига на завтра (quickstart.md Сценарий 2)"
```

---

## Implementation Strategy

### MVP First (User Story 1)

1. Phase 1: Setup (T001)
2. Phase 2: Foundational (T002-T004) — это и есть вся реализация фичи
3. Phase 3: User Story 1 (T005-T006) — **STOP and VALIDATE**
4. Продемонстрировать пользователю расширенный диапазон

### Incremental Delivery

1. Setup + Foundational → функция `getFreeTimeSlots()` полностью переписана (T001-T004)
2. User Story 1 (T005-T006) → подтверждён расширенный диапазон → можно показать пользователю
3. User Story 2 (T007-T009) → подтверждена гарантия «только будущее», включая пример из задачи (месяц назад + сейчас 12:00 → завтра)
4. Polish (T010-T011) → линтеры/KDoc/финальный прогон `quickstart.md`

### Единственный разработчик (типичный случай для этой фичи)

Поскольку вся логика — одна функция в одном файле, параллельная работа нескольких разработчиков не даёт выигрыша; задачи T002-T004 выполняются строго последовательно одним человеком/агентом, T005-T009 — проверочные и могут перемежаться в любом порядке после T004.

---

## Notes

- [P] tasks = разные файлы или независимые проверки без пересечения
- [Story] label связывает задачу проверки с конкретной User Story из `spec.md`
- T002-T004 — единственные задачи, меняющие код; T005-T009 — задачи ручной проверки (в проекте нет CI-тестов для этого пути, см. `plan.md`)
- Перед коммитом — обязательный чек-лист из `CLAUDE.md` (ktlint, KDoc coverage, pre-commit)
- Избегать: смешивания правок T002/T003 в один неделимый коммит без возможности проверить промежуточный результат — если удобнее, можно закоммитить одним коммитом после T004, так как отдельно T002 (без T003) не проходит acceptance-критерии ни одной из историй
