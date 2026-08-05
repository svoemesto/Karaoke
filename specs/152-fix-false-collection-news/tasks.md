---

description: "Task list for feature implementation"
---

# Tasks: Ложное срабатывание новости «песня появилась в коллекции» после синхронизации

**Input**: Design documents from `/specs/152-fix-false-collection-news/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/collection-news-trigger.md, quickstart.md

**Tests**: Автотестов в CI для этой области нет (constitution.md — «Тесты: в CI нет»). Проверка
каждой user story — ручной replay сценариев `quickstart.md` на LOCAL-сэндбоксе, без отдельных
автотестовых файлов.

**Organization**: Задачи сгруппированы по user story из spec.md для независимой реализации и
проверки каждой.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Можно выполнять параллельно (разные файлы, нет зависимости от незавершённых задач)
- **[Story]**: К какой user story относится задача (US1, US2, US3)
- Указаны точные пути к файлам

## Path Conventions

Backend-only фикс в существующей gradle multi-module структуре (см. plan.md → Project
Structure): `karaoke-app/src/main/kotlin/...` (основная логика) и `docs/features/*.md`
(обязательные обновления per-feature документов, FR-009). Новых директорий не создаётся.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Общая тестовая фикстура для ручной проверки всех user story ниже

- [X] T001 Подготовить на LOCAL-сэндбоксе тестовую песню-фикстуру — вместо синтетической
      записи переиспользованы реальные данные сэндбокса: song_id=21388
      (`newsAvailableAnnounced=true`, уже есть `category='air'`, `category='premium'` ещё
      нет — точный класс инцидента для US1/US3) и song_id=22981 (`newsAvailableAnnounced=true`,
      ни `air`, ни `premium` ещё нет — легитимный первый кейс для US2)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Общая инфраструктура, обязательная перед любой user story

Блокирующих задач нет: `News.existsAnnouncement` (используется обеими новыми проверками)
уже существует и публичен в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/News.kt`,
`tbl_news` уже содержит нужные колонки (`category`, `song_id`, `link`) — см.
[data-model.md](./data-model.md). Новых миграций/сервисов/моделей не требуется — переходим
сразу к user story после Phase 1.

**Checkpoint**: Фундамент готов — можно приступать к Phase 3.

---

## Phase 3: User Story 1 - Синхронизация не создаёт ложных новостей о «появлении в коллекции» (Priority: P1) 🎯 MVP

**Goal**: Устранить конкретный класс инцидента — новость «в коллекции» никогда не появляется
для песни, у которой уже есть новость «в эфире» (on-air ⇒ давно в коллекции).

**Independent Test**: [quickstart.md](./quickstart.md) Сценарий 1 (baseline: воспроизвести
ложное срабатывание до фикса) → Сценарий 2 (после фикса — новой строки `category='premium'`
не появляется).

### Implementation for User Story 1

- [X] T002 [US1] В `detectAndAnnounceAvailability`
      (`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/SongReleaseAnnouncementService.kt`)
      добавить гейт «уже вышла в эфир ⇒ не публиковать» (Decision 2 / шаг 5 контракта): перед
      вызовом `News.createAutoAnnouncement(..., category = "premium", ...)` вернуть `false`,
      если `News.existsAnnouncement(songId = song.id, link = "/song?id=${song.id}", category = "air", database = database)`
      истинно
- [X] T003 [US1] Обновить KDoc метода `detectAndAnnounceAvailability` в том же файле
      (`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/SongReleaseAnnouncementService.kt`),
      описав новый инвариант «уже в эфире ⇒ заведомо не новое событие» и добавив `@see`-ссылку
      на `docs/features/approve-pipeline.md` (FR-006)
- [ ] T004 [US1] Прогнать [quickstart.md](./quickstart.md) Сценарий 1 (на текущем, ещё не
      исправленном коде — подтвердить baseline-воспроизведение бага) и затем Сценарий 2 (после
      применения T002 — подтвердить отсутствие новой строки `tbl_news` с `category='premium'`)
      на LOCAL-сэндбоксе, используя фикстуру из T001

**Checkpoint**: Репортованный инцидент (3 песни, ложная новость «в коллекции» после «в
эфире») больше не воспроизводится — MVP готов.

---

## Phase 4: User Story 2 - Настоящее первое появление песни в коллекции по-прежнему объявляется (Priority: P2)

**Goal**: Убедиться, что гейт из US1 не подавляет легитимные, ещё не вышедшие в эфир, первые
появления песни в коллекции — новых код-изменений эта история не требует, только проверка
регрессии поверх T002.

**Independent Test**: [quickstart.md](./quickstart.md) Сценарий 3 — совершенно новая песня
без новостей `air`/`premium` по-прежнему получает ровно одну новость `category='premium'`.

### Implementation for User Story 2

- [ ] T005 [US2] Прогнать [quickstart.md](./quickstart.md) Сценарий 3 на LOCAL-сэндбоксе
      (после T002): довести новую тестовую песню (без `air`-новости) до полной готовности
      через штатный `saveToDb()`/sync-цикл, подтвердить создание ровно одной строки
      `tbl_news` с `category='premium'`

**Checkpoint**: Легитимные первые появления по-прежнему корректно объявляются — гейт из US1
не даёт побочных эффектов.

---

## Phase 5: User Story 3 - Идемпотентность при повторных/ретраящихся синхронизациях (Priority: P3)

**Goal**: Гарантировать, что ни одна песня никогда не получит более одной новости «в
коллекции», независимо от числа повторов/ретраев синхронизации.

**Independent Test**: [quickstart.md](./quickstart.md) Сценарий 4 — повторная отправка того
же sync-батча не создаёт вторую строку `category='premium'`.

### Implementation for User Story 3

- [X] T006 [US3] В `detectAndAnnounceAvailability`
      (`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/SongReleaseAnnouncementService.kt`)
      добавить гейт «уже опубликовано ⇒ не дублировать» (Decision 1 / шаг 4 контракта): перед
      вызовом `News.createAutoAnnouncement(..., category = "premium", ...)` вернуть `false`,
      если `News.existsAnnouncement(songId = song.id, link = "/song?id=${song.id}", category = "premium", database = database)`
      уже истинно
- [ ] T007 [US3] Прогнать [quickstart.md](./quickstart.md) Сценарий 4 на LOCAL-сэндбоксе,
      используя песню из T004/T005, у которой уже создана новость `category='premium'`:
      повторно отправить тот же `dataUpdate`-батч в `POST /changerecords`, подтвердить, что
      количество строк `tbl_news` с `category='premium'` для этой песни осталось равным 1

**Checkpoint**: Все три user story независимо реализованы и проверены — можно переходить к
Polish.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Обязательные по FR-009/Constitution VI сопутствующие обновления и финальная
сквозная проверка

- [X] T008 [P] Обновить `docs/features/approve-pipeline.md` — заменить пункт «L3 —
      `SongReleaseAnnouncementService.detectAndAnnounceAvailability` early-return если
      `wasAvailableBefore=true`» описанием новых гейтов (Decision 1 — дедуп через
      `existsAnnouncement(category="premium")`, Decision 2 — гейт по `category="air"`), FR-009
- [X] T009 [P] Обновить `docs/features/dual-db-sync.md` — раздел про «Доступна»
      (`category="premium"`, `detectAndAnnounceAvailability`) переписать под новое правило
      детекции ([data-model.md](./data-model.md), «Логическое правило»), убрав устаревшее
      описание «переход `false → true` по сравнению с текущим значением на этой БД» как
      единственного условия, FR-009
- [X] T010 Запустить `./gradlew ktlintCheck` и `bash tools/check-kdoc-coverage.sh` для
      изменённых файлов в `karaoke-app` (CLAUDE.md, обязательный чек-лист перед коммитом)
- [ ] T011 Полный сквозной прогон всех 4 сценариев [quickstart.md](./quickstart.md) подряд в
      одной сессии на LOCAL-сэндбоксе — финальная проверка перед открытием PR

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: без зависимостей — можно начинать сразу
- **Foundational (Phase 2)**: пуста (см. выше) — не блокирует Phase 3
- **User Stories (Phase 3-5)**: зависят от завершения Phase 1 (нужна общая тестовая
  фикстура T001)
  - US1 (Phase 3) — независима от US2/US3, может быть реализована и проверена первой (MVP)
  - US2 (Phase 4) — чисто проверочная, логически идёт ПОСЛЕ T002 (US1), т.к. проверяет
    отсутствие регрессии от нового гейта; кода не добавляет
  - US3 (Phase 5) — код-независима от US1 (другое условие раннего выхода), но правится в
    том же файле/методе — в рамках этой задачи выполняется последовательно после US1 во
    избежание конфликтов правок внутри одной функции
- **Polish (Phase 6)**: после завершения всех трёх user story

### Within Each User Story

- US1: код (T002) → документация метода (T003) → ручная проверка (T004)
- US2: только ручная проверка (T005), зависит от T002
- US3: код (T006) → ручная проверка (T007), зависит от T004/T005 (переиспользует фикстуру
  с уже созданной новостью `category='premium'`)

### Parallel Opportunities

- T008 и T009 — разные файлы документации, можно выполнять параллельно
- T002 и T006 затрагивают один и тот же метод одного файла — НЕ параллелить, выполнять
  последовательно (T002 раньше T006 по порядку фаз)

---

## Parallel Example: Phase 6 (Polish)

```bash
# После завершения всех user story — документация обновляется параллельно:
Task: "Обновить docs/features/approve-pipeline.md — новые гейты detectAndAnnounceAvailability"
Task: "Обновить docs/features/dual-db-sync.md — новое правило детекции «Доступна»"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1: подготовить тестовую фикстуру (T001)
2. Phase 2: пусто, пропустить
3. Phase 3: реализовать и проверить US1 (T002-T004) — устраняет конкретно репортованный
   инцидент
4. **STOP and VALIDATE**: Сценарии 1-2 quickstart.md проходят
5. При необходимости — можно остановиться здесь и уже открыть PR с MVP-фиксом; US2/US3
   усиливают гарантии, но не обязательны для устранения самого репортованного бага

### Incremental Delivery

1. Setup → Phase 3 (US1) → проверить независимо → это уже закрывает инцидент из spec.md
2. + Phase 4 (US2) → подтвердить отсутствие регрессии
3. + Phase 5 (US3) → добавить defence-in-depth дедупликацию
4. Phase 6 → документация (FR-009) + финальный сквозной прогон → готово к PR

---

## Notes

- [P] задачи = разные файлы, нет зависимостей
- [Story] маркер связывает задачу с конкретной user story для трассируемости
- T002 и T006 — единственные задачи, меняющие код; оба гейта — независимые
  early-return-проверки внутри одного метода, порядок между ними (какая проверяется
  первой) не влияет на корректность
- Тестов в привычном смысле (файлы `tests/...`) в этом фиксе нет — см. раздел Tests выше;
  «Independent Test» каждой user story = конкретный сценарий quickstart.md
- Коммитить после каждой задачи или логической группы (T002+T003, затем T004, и т.д.)
