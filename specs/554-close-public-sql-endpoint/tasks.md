---

description: "Задачи реализации фичи 554 — закрытие публичного доступа к POST /changerecords (S1, BR-011, OpenProject #357)"
---

# Tasks: 554 — Закрыть публичный доступ к `POST /changerecords`

**Input**: Design documents from `/specs/554-close-public-sql-endpoint/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/internal-secret-contract.md, quickstart.md

**Tests**: включены — спека FR-010 требует регрессионный тест «до/после».

**Organization**: задачи сгруппированы по пользовательским сценариям; каждый сценарий —
самостоятельный проверяемый инкремент.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: можно выполнять параллельно (разные файлы, нет зависимостей)
- **[Story]**: сценарий из spec.md (US1 — P1, US2 — P2, US3 — P3)

## Path Conventions

Монорепозиторий: `karaoke-web/`, `karaoke-app/`, `deploy/`, `docs/features/`, `knowledge/`.

---

## Phase 1: Setup

**Purpose**: подготовить конфигурацию и проверку без изменения поведения.

- [x] T001 Добавить параметр `sync.internal-secret: ${SYNC_INTERNAL_SECRET:}` в `karaoke-web/src/main/resources/application.yml` (рядом с блоком `stemjobs:`), с комментарием про назначение и fail-safe
- [x] T002 [P] Зарегистрировать свойство `syncInternalSecret` (defaultValue = "", описание) в реестре `listKaraokeProperties` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProperties.kt` (рядом с `stemJobsInternalSecret`)
- [x] T003 [P] Добавить доступ `Karaoke.syncInternalSecret` (get/set) в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Karaoke.kt` (рядом с `stemJobsInternalSecret`)
- [x] T004 [P] Прокинуть `SYNC_INTERNAL_SECRET` в `deploy/docker-compose-web.yml`, `deploy/prod-single-host/docker-compose-web.yml`, `deploy/web-server-deploy/deploy/docker-compose-web.yml` (по образцу `STEMJOBS_INTERNAL_SECRET`)
- [x] T005 Зарегистрировать новую категорию журнала `infra.sync.security` в `knowledge/domains/monitoring/components/log-categories.md` (таблица категорий)

**Checkpoint**: параметры объявлены с обеих сторон, секрет в репозитории отсутствует, поведение не изменилось.

---

## Phase 2: Foundational

**Purpose**: общий фундамент — контракт и проверка доступа, от которой зависят все сценарии.

- [x] T006 Создать контракт `specs/554-close-public-sql-endpoint/contracts/internal-secret-contract.md` — уже создан в Phase 0; сверить с `research.md` D3/D5 и привести в соответствие
- [x] T007 [P] Добавить в `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/MainController.kt` проверку доступа: `@Value("\${sync.internal-secret:}")`, сравнение `secret.isNotEmpty() && request.getHeader("X-Internal-Secret") == secret` (fail-safe — см. research D2), метод `authorized(request)`
- [x] T008 [P] Добавить в `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/MainController.kt` отказ при непрошедшей проверке: HTTP 403, короткий ответ без текста ошибки БД, запись `sync:security:denied` в категорию `infra.sync.security` (время, адрес источника, причина)
- [x] T009 [P] Убрать наружу текст ошибки БД из `catch` обработчика `POST /changerecords` в `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/MainController.kt` (сейчас `return e.message!!`) — вместо него структурированная запись в журнал и нейтральный ответ

**Checkpoint**: запрос без корректного секрета отклоняется до обращения к БД; текст ошибки БД наружу не уходит.

---

## Phase 3: User Story 1 — Публичный интернет не может исполнить SQL (P1)

**Story goal**: анонимный внешний запрос не приводит к выполнению команд; попытка видна в журнале.

**Independent test**: запрос без ключа с внешнего адреса → отказ, тело ответа без текста ошибки БД, в журнале запись об отказе (quickstart, проверка 1).

- [x] T010 [US1] Написать тест «запрос без заголовка `X-Internal-Secret` → 403 и ни одной SQL-команды не выполнено» в `karaoke-web/src/test/kotlin/com/svoemesto/karaokeweb/controllers/MainControllerChangeRecordsTest.kt`
- [x] T011 [US1] Написать тест «запрос с корректным заголовком → действия применяются, ответ прежний» в `karaoke-web/src/test/kotlin/com/svoemesto/karaokeweb/controllers/MainControllerChangeRecordsTest.kt`
- [x] T012 [US1] Написать тест «секрет не настроен (пусто) и заголовок присутствует, но пустой → 403» в `karaoke-web/src/test/kotlin/com/svoemesto/karaokeweb/controllers/MainControllerChangeRecordsTest.kt` (ловушка образца, research D2)
- [x] T013 [US1] Написать тест «в ответе при отказе нет текста ошибки БД» в `karaoke-web/src/test/kotlin/com/svoemesto/karaokeweb/controllers/MainControllerChangeRecordsTest.kt`
- [x] T014 [US1] Добавить сетевой фильтр в `deploy/prod-single-host/80to8897` в `location /changerecords`: `allow 185.26.28.109; allow 83.137.50.231; allow 185.26.28.199; deny all;` с комментарием про плавающий адрес (образец — `deploy/prod-single-host/nginx.conf:22–32, 41–50`)
- [x] T015 [US1] Проверить синтаксис изменённого nginx-конфига (`nginx -t` в контейнере) и зафиксировать результат в `specs/554-close-public-sql-endpoint/report.md`

**Checkpoint**: внешний запрос не исполняет SQL; попытка видна в журнале; конфиг nginx валиден.

---

## Phase 4: User Story 2 — Внутренняя синхронизация продолжает работать (P2)

**Story goal**: легитимный клиент с ключом работает как раньше; сетевые ошибки — с прежней повторной попыткой; отказ по правам не повторяется.

**Independent test**: в тестах клиента — заголовок уходит в запрос; ответ 403 не приводит к повтору; существующие тесты ретрая зелёные (quickstart, проверка 2).

- [x] T016 [US2] Добавить заголовок `X-Internal-Secret` со значением `Karaoke.syncInternalSecret` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/SyncRemoteClient.kt` (в построении `HttpRequest`)
- [x] T017 [US2] Написать тест «в запрос уходит заголовок `X-Internal-Secret`» в `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/SyncRemoteClientTest.kt` (через существующий test-seam `postChangeRecords(body, retryDelayMs, send)`)
- [x] T018 [US2] Написать тест «ответ 403 → повторной попытки нет, возвращается false» в `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/SyncRemoteClientTest.kt`
- [x] T019 [US2] Проверить, что существующие тесты повторов на сетевых ошибках не изменились (зелёные) — `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/SyncRemoteClientTest.kt`

**Checkpoint**: легитимный обмен работает; отказ по правам не повторяется; сетевые ошибки ведут себя как раньше.

---

## Phase 5: User Story 3 — Отказ виден и не выдаёт деталей (P3)

**Story goal**: администратор видит попытки внешнего доступа; наружу не утекают ни тексты ошибок БД, ни детали схемы.

**Independent test**: один отклонённый запрос → запись в журнале категории `infra.sync.security`; в теле ответа нет текста ошибки БД.

- [x] T020 [US3] Проверить и дополнить запись об отказе в `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/MainController.kt`: время, адрес источника, причина (`missing_secret` / `invalid_secret` / `secret_not_configured`), без тела запроса
- [x] T021 [US3] Добавить/обновить тест «в журнале есть запись об отказе и в ней нет тела запроса» в `karaoke-web/src/test/kotlin/com/svoemesto/karaokeweb/controllers/MainControllerChangeRecordsTest.kt`

**Checkpoint**: отказ наблюдаем, деталей не выдаёт.

---

## Phase 6: Polish & Cross-Cutting

- [x] T022 Обновить per-feature документ `docs/features/internal-secret-sync.md` (FR-009 конституции): назначение, стороны, контракт, fail-safe, порядок выдачи и ротации секрета
- [x] T023 Обновить `knowledge/domains/karaoke-web/components/main-controller.md`: контракт `POST /changerecords` теперь требует внутренний секрет; отметить сетевой рубеж nginx
- [x] T024 Обновить `knowledge/domains/processing/components/two-db-sync.md`: клиент отправляет `X-Internal-Secret`
- [x] T025 Прогнать полный набор проверок проекта: `:karaoke-web:compileKotlin`, `:karaoke-app:compileKotlin`, `:karaoke-web:ktlintCheck`, линтеры фронтов — 7/7 PASS (quickstart, проверка 4)
- [x] T026 Проверить, что значение секрета не попало в diff (`git diff master...HEAD | grep -inE 'secret\s*=\s*["'][0-9a-f]{16,}'` → пусто) (quickstart, проверка 5)
- [x] T027 Написать `specs/554-close-public-sql-endpoint/report.md` для трекера: что было, что изменено, как проверялось, что осталось за владельцем (выдача секрета на боевом хосте, деплой)
- [x] T028 Обновить карточку аудита `specs/336-code-audit/findings/P-35.md` (запись P-35-01 / BR-011): статус → «исправлено», ссылка на PR; проставить номер задачи в `specs/336-code-audit/registry.csv` (колонка `work_package`)

---

## Dependencies

```text
Phase 1 (Setup)          → Phase 2 (Foundational) → Phase 3 (US1) → Phase 4 (US2) → Phase 5 (US3) → Phase 6 (Polish)
```

- US1 (P1) — базовый инкремент: без него дыра остаётся.
- US2 (P2) — зависит от Foundational (нужен контракт ключа); реализуется вместе с US1, иначе синхронизация сломается.
- US3 (P3) — зависит от US1 (журнал пишется при отказе) и может идти параллельно с US2.
- Phase 6 — после всех сценариев.

## Parallel Execution Examples

```text
Phase 1:  T002, T003, T004, T005 — параллельно (разные файлы)
Phase 2:  T007, T008, T009 — T008/T009 в одном файле, T007 независим
Phase 3:  T010–T013 — параллельно (один тестовый файл — по очереди); T014 независим от тестов
Phase 4:  T017–T019 — по очереди (один файл); T016 независим
Phase 5:  T021 — параллельно с Phase 4
Phase 6:  T022, T023, T024 — параллельно (разные файлы)
```

## Implementation Strategy

1. **MVP (минимум, дающий ценность)**: Phase 1 + Phase 2 + Phase 3 — дыра закрыта, синхронизация
   ещё работает (US2 дорабатывается сразу следом).
2. **Инкремент 2**: Phase 4 — клиент научен ключу (иначе откат: Phase 3 без Phase 4 ломает
   синхронизацию — поэтому Phase 3 и Phase 4 выпускаются вместе).
3. **Инкремент 3**: Phase 5 — наблюдаемость.
4. **Финал**: Phase 6 — документация, знание, проверки, отчёт.

**Критично**: Phase 3 (US1) и Phase 4 (US2) нельзя разворачивать по отдельности — без заголовка
на клиенте (T016) синхронизация перестанет проходить. Порядок деплоя: сначала серверная проверка
с поддержкой «пустой секрет = пропуск» на время перехода либо одновременный выпуск обеих сторон.
