---

description: "Task list for feature implementation"
---

# Tasks: Убрать мониторинг запланированных публикаций

**Input**: Design documents from `/specs/154-remove-scheduled-publications-monitoring/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/monitor-alerts-contract.md, quickstart.md

**Tests**: Не запрошены явно и не требуются спецификацией — в проекте нет CI-тестов для `MonitorRegistry`/проверок мониторинга (см. Конституцию, «Рабочий процесс» → «Тесты»); проверка полностью ручная, через `quickstart.md`.

**Organization**: Задачи сгруппированы по user story из `spec.md` для независимой реализации и проверки каждой.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Может выполняться параллельно (разные файлы, нет зависимости от незавершённых задач)
- **[Story]**: К какой user story относится задача (US1, US2)
- Пути к файлам указаны точно

## Path Conventions

Существующая monorepo-структура (см. `plan.md` → Project Structure): backend
`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/...`, документация
`docs/features/...`. Frontend (`webvue3`) не редактируется — оба
UI-поверхности читают generic-компоненты из тех же backend-ответов.

---

## Phase 1: Setup

**Purpose**: Подготовка рабочей ветки перед правками кода (по правилам проекта — не коммитить в master)

- [X] T001 Создать и переключиться на feature-ветку `154-remove-scheduled-publications-monitoring` от актуального `master` (`git checkout -b 154-remove-scheduled-publications-monitoring`)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Общие блокирующие предпосылки перед началом любой user story

Не требуется: обе user story ниже правят непересекающиеся файлы
(`monitor/`-пакет и `docs/features/monitoring.md` для US1;
`KaraokeProperties.kt` для US2) и не зависят друг от друга — каждая может
начинаться сразу после Phase 1.

**Checkpoint**: Ветка готова — можно начинать любую user story (параллельно или последовательно).

---

## Phase 3: User Story 1 - Мониторинг больше не жалуется на горизонт запланированных публикаций (Priority: P1) 🎯 MVP

**Goal**: Проверка `TelegramHorizonCheck` полностью убрана из системы мониторинга — алерт «Мало запланированных постов в Telegram» больше никогда не формируется и не отображается, независимо от состояния очереди публикаций.

**Independent Test**: Пересобрать и перезапустить `karaoke-app`, открыть панель мониторинга в webvue3 при любом реальном состоянии Telegram-публикаций — алерт категории «Telegram» про горизонт запланированных постов отсутствует; остальные проверки продолжают работать.

### Implementation for User Story 1

- [X] T002 [P] [US1] Удалить файл `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/monitor/checks/TelegramHorizonCheck.kt` целиком
- [X] T003 [P] [US1] В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/monitor/MonitorRegistry.kt` убрать `import com.svoemesto.karaokeapp.monitor.checks.TelegramHorizonCheck` и элемент `TelegramHorizonCheck,` из списка `checks` (список должен остаться из 7 элементов: `ProdContainerCheck`, `RenderQueueStalledCheck`, `LaneStalledCheck`, `TelegramPollingDisabledCheck`, `UnreadChatMessagesCheck`, `SubmittedAssignmentsCheck`, `StemJobsStuckCheck`)
- [X] T004 [P] [US1] Обновить `docs/features/monitoring.md`: в разделе «Что делает» заменить «Восемь проверок» на «Семь проверок»; в разделе «Как работает (кратко)» убрать пункт «Горизонт запланированных постов в Telegram (< N дней)» из списка проверок `MonitorRegistry.checks` (FR-009)
- [X] T005 [US1] Прогнать `./gradlew ktlintCheck`, `bash tools/check-kdoc-coverage.sh` и `./gradlew karaoke-app:bootJar` из корня репозитория — убедиться, что удаление файла и правка реестра не сломали сборку, линт и KDoc-покрытие (зависит от T002, T003)
- [X] T006 [US1] Пересобрать/перезапустить `karaoke-app` локально (`cd deploy && bash do.sh start_app`, разрешено на `dev-pc`/`dev`) и вручную проверить панель мониторинга в webvue3 по шагам 2-3 `quickstart.md`: алерт «Мало запланированных постов в Telegram» не появляется ни при каком состоянии очереди, остальные актуальные алерты отображаются как раньше (зависит от T005). Проверено через `GET /api/monitor/alerts` (то же самое API, что читает webvue3): ключ `telegram.horizon` отсутствует; вернулись 3 алерта от оставшихся проверок (`infra.prod.down`, `queue.stalled`, `telegram.polling.off`) без ошибок — регрессий нет. Браузер недоступен в этой среде, UI визуально не открывался.

**Checkpoint**: User Story 1 полностью функциональна и проверяема независимо — основной запрос пользователя выполнен.

---

## Phase 4: User Story 2 - Настройка порога мониторинга больше не отображается администратору (Priority: P2)

**Goal**: Настройка `monitorTelegramHorizonDays` убрана из `KaraokeProperties.kt` и, как следствие, из списка настроек в webvue3 (single source of truth — `listKaraokeProperties`).

**Independent Test**: Пересобрать и перезапустить `karaoke-app`, открыть список настроек мониторинга в webvue3 — записи `monitorTelegramHorizonDays` нет, соседние `monitorDismissed`/`monitorProdDownCriticalMinutes` на месте.

### Implementation for User Story 2

- [X] T007 [US2] В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProperties.kt` удалить запись `KaraokeProperty(key = "monitorTelegramHorizonDays", ...)` целиком (текущие строки ~2047-2051), не затрагивая соседние `monitorDismissed` и `monitorProdDownCriticalMinutes`
- [X] T008 [US2] Прогнать `./gradlew ktlintCheck` и `./gradlew karaoke-app:bootJar`, пересобрать/перезапустить `karaoke-app` локально (`cd deploy && bash do.sh start_app`) и вручную проверить список настроек в webvue3 по шагу 4 `quickstart.md`: `monitorTelegramHorizonDays` отсутствует, `monitorDismissed`/`monitorProdDownCriticalMinutes` не пострадали (зависит от T007). Проверено через `POST /api/properties/getproperties` (тот же источник данных, что и generic-список настроек в webvue3): `monitorTelegramHorizonDays` отсутствует в ответе, `monitorDismissed`/`monitorProdDownCriticalMinutes` присутствуют без изменений.

**Checkpoint**: User Story 1 и User Story 2 обе работают независимо — фича полностью реализована.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Обязательные перед каждым коммитом проверки проекта (см. `CLAUDE.md` → «ОБЯЗАТЕЛЬНО перед каждым git commit») и финальная regression-проверка

- [X] T009 [P] Прогнать `cd webvue3 && npm run lint:check && npx prettier --check "src/**/*.{vue,js,ts,json}"` (frontend-код не менялся, но проверка обязательна перед коммитом по правилам проекта) — PASS
- [X] T010 [P] Прогнать `cd karaoke-public && npm run lint:check && npx prettier --check "src/**/*.{vue,js,ts,json}"` — PASS
- [X] T011 [P] Прогнать `bash tools/check-jsdoc-coverage.sh webvue3` и `bash tools/check-jsdoc-coverage.sh karaoke-public` — 100%/100%
- [X] T012 Прогнать `pre-commit run --all-files` из корня репозитория — единая точка проверки (зависит от T005, T008, T009, T010, T011). Результат: все хуки, относящиеся к изменённым файлам этой фичи (ktlint, eslint×2, prettier×2, per-feature doc structure), — PASS. Хук `lychee` (проверка ссылок в документации) упал на предсуществующей битой ссылке в `docs/architecture-notes.md` (локальный путь `/home/dev/deploy/web-server-deploy/deploy/80to8897`, машинно-зависимый) — файл этой фичей не менялся (`git diff` по нему пуст), сбой воспроизводится и на `master` без правок этой ветки; вне рамок этой фичи, не исправлялось.
- [X] T013 Выполнить полный прогон `quickstart.md` (все 5 шагов, включая шаг 5 — сверку `docs/features/monitoring.md`) как финальную regression-проверку SC-001/SC-002/SC-003 (зависит от T006, T008). Шаги 1-2 и 4-5 выполнены буквально; шаг 3 (панель мониторинга в webvue3) проверен через прямой вызов `GET /api/monitor/alerts` внутри контейнера `karaoke-app` — визуально в браузере не открывался (headless-браузер недоступен в этой среде). Побочно найдена и исправлена опечатка в самом `quickstart.md` (шаг 2 рекомендовал небезопасную unscoped-команду `build_start_app` вместо `start_app`).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: без зависимостей — можно начинать сразу
- **Foundational (Phase 2)**: пустая — блокирующих задач нет
- **User Stories (Phase 3-4)**: обе зависят только от завершения Phase 1; между собой не зависят и могут выполняться параллельно (разные файлы)
- **Polish (Phase 5)**: зависит от завершения обеих user story

### User Story Dependencies

- **User Story 1 (P1)**: можно начинать сразу после Phase 1 — не зависит от US2
- **User Story 2 (P2)**: можно начинать сразу после Phase 1 — не зависит от US1

### Within Each User Story

- US1: T002/T003/T004 (правки разных файлов) → T005 (сборка/линт) → T006 (ручная проверка)
- US2: T007 (правка файла) → T008 (сборка + ручная проверка)

### Parallel Opportunities

- T002, T003, T004 — разные файлы, можно выполнять параллельно
- Вся Phase 3 (US1) и вся Phase 4 (US2) не пересекаются по файлам — можно выполнять параллельно двумя разработчиками/агентами
- T009, T010, T011 в Polish — разные модули, можно выполнять параллельно

---

## Parallel Example: User Story 1

```bash
# Параллельно (разные файлы):
Task: "Удалить файл karaoke-app/.../monitor/checks/TelegramHorizonCheck.kt"
Task: "Убрать TelegramHorizonCheck из MonitorRegistry.kt"
Task: "Обновить docs/features/monitoring.md"
```

## Parallel Example: User Story 1 + User Story 2 одновременно

```bash
# Разработчик/агент A:
Task: "T002-T006 (User Story 1: удаление TelegramHorizonCheck)"
# Разработчик/агент B:
Task: "T007-T008 (User Story 2: удаление monitorTelegramHorizonDays)"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1: Setup (T001)
2. Phase 2: Foundational — пусто, пропускается
3. Phase 3: User Story 1 (T002-T006)
4. **STOP and VALIDATE**: ложный алерт больше не появляется — основной запрос пользователя закрыт, можно остановиться здесь как MVP
5. При необходимости продолжить Phase 4 (зачистка настройки) и Phase 5 (полный pre-commit гейт) перед коммитом/PR

### Incremental Delivery

1. Setup → готова ветка
2. User Story 1 → проверено независимо → фича по сути работает (MVP)
3. User Story 2 → проверено независимо → зачищена «мёртвая» настройка из UI
4. Polish → все обязательные линтеры/coverage/pre-commit проходят → готово к коммиту/PR

---

## Notes

- [P] задачи = разные файлы, нет зависимостей
- [Story]-метка связывает задачу с конкретной user story
- Тесты не запрошены — раздел «Tests for User Story N» не создавался (см. `Tests` выше)
- Коммитить в master напрямую нельзя — только в ветке `154-remove-scheduled-publications-monitoring` (T001), и только по прямому запросу пользователя на коммит
- Перед PR — все проверки из `CLAUDE.md`/Конституции (Phase 5) обязаны пройти; CI блокирует merge при любом failing check
