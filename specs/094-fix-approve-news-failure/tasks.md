# Tasks: Апрув задания редактора завершается ошибкой запроса, новость не появляется

**Input**: Design documents from `/specs/094-fix-approve-news-failure/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/approve-endpoint.md, quickstart.md

**Tests**: В проекте нет CI-автотестов (см. Конституцию, «Рабочий процесс»); проверка — вручную по сценариям `quickstart.md`. Отдельных задач "написать unit/integration-тест" ниже нет — вместо них задачи "выполнить сценарий N из quickstart.md".

**Organization**: Задачи сгруппированы по user story (US1, US2 — обе P1, spec.md) для независимой реализации и проверки каждой.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Можно выполнять параллельно (разные файлы, нет зависимости от незавершённых задач)
- **[Story]**: К какой user story относится задача (US1/US2)
- Указаны точные пути к файлам

---

## Phase 1: Setup

**Purpose**: Зафиксировать чистый baseline перед правками (новых зависимостей фича не добавляет — см. plan.md, Technical Context)

- [X] T001 Собрать `karaoke-app` на ветке `094-fix-approve-news-failure` до внесения правок (`./gradlew karaoke-app:compileKotlin`) и зафиксировать, что baseline компилируется чисто. Агенту на машине `dev-pc` разрешено собирать локально без отдельного согласия пользователя (см. `.specify/memory/constitution.md`, «Ограничения и доступы агента»).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Сделать `approve()` устойчивым к необработанным исключениям и привести форму ответа к единому контракту (`contracts/approve-endpoint.md`) — на этой основе строятся и US1 (сообщения администратору), и US2 (надёжность появления новости), обе истории меняют одну и ту же функцию

**⚠️ CRITICAL**: Обе user story правят `SongEditorController.approve()` — эта фаза должна быть завершена первой, чтобы не создавать конфликтующих правок в одной и той же функции

- [X] T002 В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/SongEditorController.kt`, `approve()` (строки ~393-398) обернуть запись статуса задания (`aRead.adminStatus = SongAssignmentStatus.ADMIN_APPROVED`, `aRead.reviewedAt = ...`, `aRead.reviewComment = ""`, `aRead.save()`) в try/catch — аналогично уже существующему паттерну push/анонса чуть выше (строки ~371-390). При исключении — НЕ давать ему уйти необработанным из контроллера; в этом случае `admin_status` НЕ должен быть помечен approved (research.md §2, §4.1; FR-003/FR-005 spec.md; data-model.md, инвариант `SongAssignment`).
- [X] T003 В том же файле и той же функции `approve()` (`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/SongEditorController.kt:312-408`) привести ВСЕ ветки возврата к контракту `contracts/approve-endpoint.md`: добавить поле `"status"` (`"success"` для успешного завершения; `"error"` для существующих ранних отказов `assignment_not_found`/`draft_not_found`/`song_not_found`/`bad_markers` и для нового `save_failed` из T002), сохранив существующее поле `"ok"` для обратной совместимости.

**Checkpoint**: `approve()` больше не может завершиться необработанным исключением; ответ имеет единую типизированную форму — можно начинать любую из user story.

---

## Phase 3: User Story 1 - Апрув, который фактически удался, не показывается администратору как ошибка (Priority: P1) 🎯 MVP

**Goal**: Администратор при успешном апруве видит явное сообщение об успехе (не «Ошибка запроса»); при повторном клике по уже одобренному заданию видит явное «уже одобрено», а не тишину/ошибку.

**Independent Test**: Апрувить тестовое задание с валидной разметкой → интерфейс показывает успех. Повторно кликнуть «Одобрить и премиить» по тому же заданию → интерфейс показывает «уже одобрено», без повторного применения разметки/push (см. `quickstart.md`, Сценарии 1-2).

### Implementation for User Story 1

- [X] T004 [US1] В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/SongEditorController.kt`, `approve()`, сразу после загрузки `aRead` (после `SongAssignment.getById(...)`, ~строка 325) добавить проверку: если `aRead.adminStatus == SongAssignmentStatus.ADMIN_APPROVED`, немедленно вернуть `{"ok": true, "status": "already_approved"}`, не выполняя повторно применение разметки, push и `checkAndAnnounce()` (FR-002, FR-006 spec.md; data-model.md, state machine, ветка «повторный клик»). Зависит от T003 (форма ответа).
- [X] T005 [US1] В `webvue3/src/components/SongEditor/ReviewModal.vue`, `doApprove()` (строки ~281-298) обработать `res.status` из ответа: `"success"` → явное сообщение об успехе перед `$emit('reviewed')` (сегодня успех не показывает никакого сообщения — молчаливое закрытие); `"already_approved"` → сообщение «Задание уже одобрено»; иначе (`"error"` или исключение в `catch`) — существующее поведение (`isError = true`, сообщение об ошибке), не путать с двумя предыдущими исходами (FR-001, FR-002, FR-005 spec.md). Зависит от T003, T004.
- [X] T006 [US1] Выполнить вручную Сценарии 1, 2 и 4 из `specs/094-fix-approve-news-failure/quickstart.md` (успешный апрув → явный успех, не «Ошибка запроса»; повторный клик → «уже одобрено» без повторных `saveToDb`/push в логах `docker logs karaoke-app`; ревью diff на предмет того, что ошибка локального применения к `Song` не помечает задание одобренным) и отметить соответствующие пункты `specs/094-fix-approve-news-failure/checklists/requirements.md`, если ещё не отмечены.
  Выполнено на LOCAL (dev-pc): пересобран и перезапущен `karaoke-app` с правками, создано тестовое задание (id=123, песня id=1, редактор id=5), Сценарии 1 и 2 подтверждены через прямой `curl` к `/api/songeditor/approve` (тот же backend-путь, что и клик в webvue3; браузерного клика не делал — в сессии нет инструмента управления браузером): 1-й вызов → `{"ok":true,"status":"success","idStatus":6}` + `admin_status='approved'` в БД; 2-й вызов → `{"ok":true,"status":"already_approved"}`. Сценарий 4 (ревью diff): все ранние `return@withDb` (`assignment_not_found`/`draft_not_found`/`song_not_found`/`bad_markers`) происходят ДО блока `aRead.save()` — подтверждено. Найден смежный ПРЕДСУЩЕСТВУЮЩИЙ (не внесённый этой правкой) пробел: `settings.saveToDb()` и цикл применения маркеров (строки ~348-372) по-прежнему без try/catch — если они бросят исключение, оно всё ещё уйдёт необработанным. Это тот же класс риска, что и починенный `aRead.save()`, но вне исследованного в `research.md` инцидента (лог инцидента показывает, что именно эти шаги отработали успешно) и вне зафиксированного скоупа FR этой фичи — не чинил, сообщил пользователю в отчёте о завершении.
  Тестовые строки (`tbl_song_assignments`/`tbl_song_assignment_drafts` id=123) удалены после проверки.

**Checkpoint**: User Story 1 полностью функциональна и проверяема независимо.

---

## Phase 4: User Story 2 - Новость о новой песне появляется на сайте после успешного апрува (Priority: P1)

**Goal**: Если апрув фактически перевёл песню в статус «доступна на сайте» на боевой копии, новость о новой песне появляется в публичной ленте — независимо от того, что увидел администратор в своём интерфейсе.

**Independent Test**: Апрувить тестовое задание так, чтобы песня реально стала доступна на боевой копии, не запуская отдельно синхронизацию — убедиться, что новость появилась в публичной ленте (см. `quickstart.md`, Сценарий 3).

### Implementation for User Story 2

- [X] T007 [P] [US2] В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt` добавить необязательный параметр `toDatabase: KaraokeConnection = Connection.remote()` в сигнатуру `updateRemoteSongFromLocalDatabase(id: Long, ...)` (строки 566-575), прокинув его в вызов `updateDatabases(..., toDatabase = toDatabase, ...)` вместо жёстко закодированного `Connection.remote()`. Поведение для существующих вызывающих (`Song.kt:5121` в `saveToDb()`, `KaraokeProcessWorker.kt`, `ApiController.kt`) не меняется — они продолжают использовать значение по умолчанию (research.md §2, §4.2).
- [X] T008 [US2] В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/SongEditorController.kt`, `approve()`, внутри блока `if (Karaoke.allowUpdateRemote) {...}` (строки ~371-390) создать ОДНО подключение `val remoteConnection = Connection.remote()` и передать его: (а) в `updateRemoteSongFromLocalDatabase(settings.id, toDatabase = remoteConnection)` (после T007) и (б) в `SongReleaseAnnouncementService.checkAndAnnounce(remoteConnection, KSS_APP, SAC_APP)` — вместо двух независимых вызовов `Connection.remote()`, каждый из которых сегодня открывает отдельное физическое JDBC-соединение к прод-серверу (research.md §1-2). Зависит от T007.
- [X] T009 [P] [US2] В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/SongReleaseAnnouncementService.kt`, KDoc объекта `SongReleaseAnnouncementService` (строки 8-12) исправить устаревшее утверждение «Единственный вызывающий — karaoke-web MainController.doChangeRecords» — актуализировать на реальный список: `karaoke-web MainController.doChangeRecords` (синхронизация, `specs/089`), `karaoke-web SongReleaseAnnouncementScheduler` (периодическая проверка эфира, `specs/092`), `karaoke-app SongEditorController.approve` (апрув задания редактора, эта фича) (research.md §3; Конституция, принцип VI/FR-006).
- [ ] T010 [US2] Выполнить вручную Сценарий 3 из `specs/094-fix-approve-news-failure/quickstart.md` (апрув реально переводит песню в доступную на боевой копии → новость появляется в публичной ленте в пределах времени, установленного `specs/092`) — согласовать с пользователем перед выполнением, если требуется запись в прод-БД (`.specify/memory/constitution.md`, «Категорически запрещено», п.2) — и отметить соответствующие пункты `specs/094-fix-approve-news-failure/checklists/requirements.md`.
  НЕ выполнено агентом: требует реального апрува с записью на прод (`target=remote`) и проверки публичной ленты новостей на sm-karaoke.ru — по Конституции («Категорически запрещено», п.2) это требует прямого согласия пользователя на каждое такое действие отдельно. Логика (переиспользование одного `Connection.remote()` для push+`checkAndAnnounce`, T007/T008) проверена компиляцией и локальным контрактным тестом (T006/T012), но реальную устойчивость `checkAndAnnounce()` на прод-соединении и появление новости может подтвердить только пользователь или агент с явным согласием на прод-шаг.

**Checkpoint**: User Story 1 И User Story 2 обе работают независимо.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Документация, контрактная проверка и обязательные перед коммитом проверки проекта

- [X] T011 [P] Обновить `docs/features/dual-db-sync.md` — зафиксировать исправление (typed-контракт `approve()` со статусами success/already_approved/error, короткое замыкание на повторном апруве, переиспользование одного `Connection.remote()` для push+`checkAndAnnounce`) — FR-009 spec.md, тот же файл, что редактировали `specs/089` и `specs/092`.
- [X] T012 Выполнить вручную Сценарий 5 из `specs/094-fix-approve-news-failure/quickstart.md` (контракт ответа `approve()` — валидный JSON с HTTP 200 во всех ветках, включая штатные `assignment_not_found` и т.п.) через devtools Network или `curl -i`.
  Выполнено через `curl -i` (LOCAL, порт 8898): все три ветки (`success`, `already_approved`, `error`/`assignment_not_found` на несуществующем id) вернули HTTP 200 и валидный JSON с полем `status` — контракт `contracts/approve-endpoint.md` подтверждён.
- [X] T013 Прогнать обязательные перед коммитом линтеры (`./gradlew ktlintCheck`; `cd webvue3 && npm run lint:check && npx prettier --check "src/**/*.{vue,js,ts,json}"`) — CLAUDE.md, «ОБЯЗАТЕЛЬНО перед каждым git commit».
- [X] T014 Прогнать проверку покрытия документацией изменённых публичных функций (`bash tools/check-kdoc-coverage.sh`, `bash tools/check-jsdoc-coverage.sh webvue3`) — Конституция, принцип VI/FR-006.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: без зависимостей — можно начинать сразу
- **Foundational (Phase 2)**: зависит от Setup; БЛОКИРУЕТ обе user story (обе меняют одну и ту же функцию `approve()`)
- **User Story 1 (Phase 3)**: зависит от Foundational; независима от User Story 2
- **User Story 2 (Phase 4)**: зависит от Foundational; независима от User Story 1 (может выполняться параллельно с Phase 3 другим исполнителем — оба меняют `SongEditorController.kt`, но разные, неперекрывающиеся участки функции `approve()`)
- **Polish (Phase 5)**: зависит от завершения обеих user story

### Within Each User Story

- US1: T004 (backend short-circuit) → T005 (frontend messaging, зависит от формы ответа T004/T003) → T006 (ручная проверка)
- US2: T007 (сигнатура) → T008 (переиспользование в approve(), зависит от T007); T009 (KDoc) параллельно T007/T008 (другой файл); T010 (ручная проверка) — после T008

### Parallel Opportunities

- T006 может выполняться параллельно с частью Phase 4 (разные истории)
- T007 и T009 — разные файлы, можно параллельно
- T010 — после T008, но параллельно с любыми оставшимися задачами US1
- T011, T012, T013, T014 в Polish — в основном независимы друг от друга (T012 требует уже собранного и запущенного кода после T002-T010)

---

## Parallel Example: Foundational → обе истории

```bash
# После завершения Phase 2 (T002, T003) — запустить обе истории параллельно:
Task: "US1: T004 already-approved короткое замыкание в SongEditorController.kt"
Task: "US2: T007 необязательный toDatabase в Utils.kt"
Task: "US2: T009 актуализация KDoc в SongReleaseAnnouncementService.kt (не зависит от T007/T008)"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1 (Setup) → Phase 2 (Foundational) — обязательны для любой истории.
2. Phase 3 (User Story 1) — устраняет самый заметный симптом (ложная «Ошибка запроса»).
3. **Остановиться и проверить**: Сценарии 1-2 (и 4) `quickstart.md` независимо от US2.
4. Это уже самостоятельно ценный результат — можно поставить как отдельный PR/деплой, если US2 требует более осторожной проверки на проде.

### Incremental Delivery

1. Setup + Foundational → безопасная база (`approve()` больше не 500-ит).
2. + User Story 1 → администратор больше не видит ложных ошибок → проверить → задеплоить.
3. + User Story 2 → новость надёжно появляется при готовности песни → проверить (с согласия пользователя на прод-шаги) → задеплоить.
4. Polish (документация, линтеры) — перед PR.

---

## Notes

- Тесты в CI отсутствуют (Конституция) — вместо задач «написать тест» используются задачи «выполнить сценарий N из quickstart.md».
- T002/T003 (Foundational) и T004/T005 (US1) все находятся в одной функции `approve()` — выполнять последовательно в указанном порядке, не параллельно, во избежание конфликтующих правок одного и того же участка кода.
- Не менять бизнес-правила `specs/092` (критерий «песня публично доступна», best-effort push, см. Clarifications spec.md) — эта фича только про надёжность ответа/соединений, не про новые правила.
- Коммитить после каждой завершённой задачи или логической группы (Foundational; US1; US2; Polish).
