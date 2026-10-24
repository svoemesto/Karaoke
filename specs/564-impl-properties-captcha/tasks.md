---

description: "Задачи реализации фичи 564 — закрытие публичной записи в tbl_public_settings и fail-closed проверка капчи (S1, BR-019 + BR-518, OpenProject #359 + #362)"
---

# Tasks: 564 — Закрыть публичную запись в `tbl_public_settings` и fail-open проверку капчи

**Input**: Design documents from `specs/564-impl-properties-captcha/` + спека
`specs/559-close-public-properties-captcha/spec.md`

**Prerequisites**: plan.md, research.md, contracts/public-settings-secret-contract.md, quickstart.md

**Tests**: включены — спека FR-007 требует регрессионные тесты «до/после».

**Organization**: задачи сгруппированы по пользовательским сценариям спеки; каждый сценарий —
самостоятельный проверяемый инкремент. **Критично**: сценарии US1 (#359) и US2 (#362)
поставляются вместе — починка половины цепочки считается незавершённой (FR-016).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: можно выполнять параллельно (разные файлы, нет зависимостей)
- **[Story]**: сценарий из spec.md (US1 — P1, US2 — P1, US3 — P2, US4 — P3)

## Path Conventions

Монорепозиторий: `karaoke-web/`, `karaoke-public/`, `deploy/`, `docs/features/`, `knowledge/`.

---

## Phase 1: Setup

**Purpose**: объявить параметры конфигурации и категорию журнала, не меняя поведения.

- [x] T001 Добавить в `karaoke-web/src/main/resources/application.yml` секцию `publicsettings:` с `internal-secret: ${SYNC_INTERNAL_SECRET:}` — тот же общий server-to-server секрет, что у `sync.internal-secret` (решение владельца, п. 2; research D3), с комментарием про fail-safe
- [x] T002 [P] Добавить в `karaoke-web/src/main/resources/application.yml` в секцию `captcha:` параметр `server-key: ${CAPTCHA_SERVER_KEY:}` — серверный ключ капчи переезжает в env (решение владельца, п. 3; research D8)
- [x] T003 [P] Прокинуть `CAPTCHA_SERVER_KEY` в `deploy/docker-compose-web.yml`, `deploy/prod-single-host/docker-compose-web.yml`, `deploy/web-server-deploy/deploy/docker-compose-web.yml` (по образцу `SYNC_INTERNAL_SECRET`)
- [x] T004 [P] Зарегистрировать новую категорию журнала `infra.settings.security` в `knowledge/domains/monitoring/components/log-categories.md` (таблица категорий) с перечнем событий (research D10)

**Checkpoint**: параметры объявлены, секреты в репозитории отсутствуют, поведение не изменилось.

---

## Phase 2: Foundational — периметр публичных настроек (#359)

**Purpose**: закрыть запись и ограничить выдачу — общий фундамент для US1 и US3.

- [x] T005 Создать контракт `specs/564-impl-properties-captcha/contracts/public-settings-secret-contract.md` — заголовок `X-Internal-Secret`, коды ответа, белый список ключей, fail-safe (research D2, D5)
- [x] T006 Переписать `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicSettingsWebController.kt`: добавить конструкторный параметр `@Value("\${publicsettings.internal-secret:}")`, метод `denialReason(request)` (fail-safe), константы `SECRET_KEYS` / `PUBLIC_KEYS`, метод `isAuthorized(request)`
- [x] T007 [P] В том же контроллере: `POST /setproperty` → при отказе `403` + тело `false`, **до** обращения к БД; при успехе — прежний `Boolean` и прежний порядок UPDATE→INSERT; тест-швы `connectionProvider` / `keyWriter`
- [x] T008 [P] В том же контроллере: `GET /getproperty` → секретные ключи не выдаются никогда; непубличные — только по защищённому каналу, анонимно пустая строка; публичные — как раньше (TTL-кеш сохранён)
- [x] T009 [P] В том же контроллере: `GET /digest` → анонимно только публичные ключи; по защищённому каналу — все ключи, кроме секретных
- [x] T010 [P] В том же контроллере: журнал `infra.settings.security` — `settings:security:denied` / `read-denied` / `secret-read-blocked` (время, адрес источника, причина; без тела запроса и без значения настройки)
- [x] T011 [P] Добавить сетевой фильтр в `deploy/prod-single-host/80to8897`: `location /api/properties/setproperty` с `allow`/`deny all` по трём адресам админской машины и проксированием на `127.0.0.1:8897` (образец — `location /changerecords`, research D4)

**Checkpoint**: анонимная запись отклоняется до обращения к БД; секретный ключ не выдаётся;
nginx-фильтр на месте.

---

## Phase 3: User Story 1 — Аноним не может изменить публичные настройки (P1, #359)

**Story goal**: запрос на изменение настройки без разрешения администратора отклоняется,
значение в базе не меняется, попытка видна в журнале.

**Independent test**: анонимный запрос на запись → `403`, ни одной SQL-команды, запись в
журнале `infra.settings.security` (quickstart, проверка 1).

- [x] T012 [US1] Написать тест «запрос без заголовка `X-Internal-Secret` → `403` и ни одной SQL-команды не выполнено» в `karaoke-web/src/test/kotlin/com/svoemesto/karaokeweb/controllers/PublicSettingsWebControllerTest.kt`
- [x] T013 [US1] Написать тест «запрос с неверным секретом → `403`, SQL не выполняется» в том же файле
- [x] T014 [US1] Написать тест «пустой настроенный секрет + пустой заголовок → `403`» (ловушка образца, research D2) в том же файле
- [x] T015 [US1] Написать тест «в ответе при отказе нет текста ошибки БД» в том же файле
- [x] T016 [US1] Написать тест «в журнале есть запись об отказе с адресом источника, без тела запроса и без значения настройки» в том же файле
- [x] T017 [US1] Проверить синтаксис изменённого nginx-конфига (`nginx -t` в контейнере `nginx:stable`) и зафиксировать результат в `report.md`

**Checkpoint**: внешний запрос не меняет настройки; попытка наблюдаема; конфиг nginx валиден.

---

## Phase 4: User Story 3 — Легитимный административный путь продолжает работать (P2, #359)

**Story goal**: запись по защищённому каналу работает; чтение назад видно; админка
`webvue3` («Настройки сайта») не сломана.

**Independent test**: запись с корректным секретом → значение записано и прочитано обратно;
дайджест без секрета не содержит ни секретных, ни операционных ключей (quickstart, проверка 2).

- [x] T018 [US3] Написать тест «запрос с корректным заголовком → запись выполняется, ответ прежнего формата (`true`)» в `PublicSettingsWebControllerTest.kt`
- [x] T019 [US3] Написать тест «после записи чтение возвращает новое значение (dirty-инвалидация)» в том же файле
- [x] T020 [US3] Написать тест «анонимное чтение непубличного ключа → пустая строка; по защищённому каналу → значение» в том же файле
- [x] T021 [US3] Написать тест «дайджест анонимно содержит только публичные ключи; по защищённому каналу — все, кроме секретных» в том же файле
- [x] T022 [US3] Написать тест «чтение секретного ключа не выдаёт значение даже по защищённому каналу» в том же файле
- [x] T023 [US3] Написать тест «запись секретного ключа отклоняется (`403`, `secret_key_write`)» в том же файле
- [x] T024 [US3] Проверить, что админский сценарий `webvue3` («Настройки сайта» → `/api/publicsettings/*` в `karaoke-app`) не затронут: маршрут `webvue3/nginx_webvue3.conf` ведёт в другой сервис — зафиксировать в `report.md`

**Checkpoint**: легитимный путь работает; непубличные и секретные ключи анонимно недоступны.

---

## Phase 5: User Story 2 — Регистрация не проходит без действующей проверки капчи (P1, #362)

**Story goal**: при незаконной конфигурации регистрация отклоняется с внятным кодом; при
корректной настройке и решённой капче — работает как раньше.

**Independent test**: (1) пустой серверный ключ → `NOT_CONFIGURED`, регистрация отклонена;
(2) настроенная капча → регистрация без токена отклонена, с токеном — проходит (quickstart,
проверка 3).

- [x] T025 [US2] Переписать `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/CaptchaConfigService.kt`: серверный ключ — из env (`captcha.server-key`) без кеша; клиентский — из таблицы с окном 60 секунд; метод `isServerKeyConfigured()`; диагностическая запись при старте (research D8, D9)
- [x] T026 [US2] Переписать `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/YandexCaptchaValidationService.kt`: результат — перечисление из четырёх исходов (`PASSED` / `FAILED` / `NOT_CONFIGURED` / `UNAVAILABLE`), fail-closed, без печати значения ключа (research D7)
- [x] T027 [US2] В `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicAuthController.kt`: маппинг исхода проверки в код отказа (`captcha` / `captcha_not_configured`), формат ответа прежний (FR-018)
- [x] T028 [US2] Написать тесты «пустой серверный ключ → `NOT_CONFIGURED`», «пустой токен → `FAILED`», «внешний сервис недоступен → `UNAVAILABLE`», «`status=ok` → `PASSED`», «`status!=ok` → `FAILED`» в `karaoke-web/src/test/kotlin/com/svoemesto/karaokeweb/services/YandexCaptchaValidationServiceTest.kt`
- [x] T029 [US2] Написать тесты «регистрация при `NOT_CONFIGURED` → `400` + `{"error":"captcha_not_configured"}`», «при `FAILED` → `400` + `{"error":"captcha"}`», «при `PASSED` — регистрация продолжается» в `karaoke-web/src/test/kotlin/com/svoemesto/karaokeweb/controllers/PublicAuthControllerCaptchaTest.kt`
- [x] T030 [US2] Обновить `karaoke-public/src/views/RegisterView.vue`: код `captcha_not_configured` → сообщение «Регистрация временно недоступна»; исправить устаревший комментарий про «регистрация всё равно пройдёт» (решение владельца, п. 6)

**Checkpoint**: fail-closed работает; обход «пустой ключ» невозможен; публичная форма при
корректной настройке не сломана.

---

## Phase 6: User Story 4 — Отказы видны и не выдают деталей (P3, связка)

**Story goal**: администратор видит попытки и отказы; наружу не утекают ни секрет, ни текст
ошибки БД, ни имена таблиц.

**Independent test**: один отклонённый запрос на запись + одна отклонённая регистрация →
обе попытки видны в журнале; в ответах нет ни секрета, ни текста ошибки БД.

- [x] T031 [US4] Проверить записи журнала `infra.settings.security`: время, адрес источника, причина; без тела запроса и без значения настройки (FR-004)
- [x] T032 [US4] Проверить запись `captcha:config:not_configured` при отказе регистрации: без значения серверного ключа (FR-014, local-0006)
- [x] T033 [US4] Проверить, что в ответах защищаемых путей нет текста ошибки БД, имён таблиц и значений настроек (FR-019)

**Checkpoint**: отказы наблюдаемы, деталей не выдаёт.

---

## Phase 7: Polish & Cross-Cutting

- [x] T034 Обновить per-feature документ `docs/features/public-settings-perimeter.md` (FR-009 конституции): периметр, контракт секрета, белый список ключей, fail-closed капча, порядок выдачи/ротации
- [x] T035 [P] Обновить `knowledge/domains/karaoke-web/components/public-controllers-6.md`: контракт `PublicSettingsWebController` (запись под секретом, чтение по белому списку, коды ответа)
- [x] T036 [P] Обновить `knowledge/domains/karaoke-web/components/config.md`: новые параметры `publicsettings.internal-secret` и `captcha.server-key`, правило резолва настроек
- [x] T037 [P] Обновить `knowledge/domains/karaoke-web/components/services-overview.md`: контракт `CaptchaConfigService` (env + клиентский из таблицы) и `YandexCaptchaValidationService` (четыре исхода)
- [x] T038 [P] Обновить `knowledge/domains/integration/components/external-api-clients.md`: раздел про `YandexCaptchaValidationService` — fail-closed и место хранения ключа
- [x] T039 Добавить миграцию `deploy/karaoke-db/51_public_settings_drop_captcha_server_key.sql` — удаление строки `yandexSmartCaptchaServerKey` (выполняется владельцем **после** инвентаризации, research D12)
- [x] T040 Обновить `specs/124-news-flags-backfill/quickstart.md` — операционный регламент переведён на защищённый канал (решение владельца, п. 5)
- [x] T041 Прогнать полный набор проверок проекта: `:karaoke-web:compileKotlin`, `:karaoke-app:compileKotlin`, `:karaoke-web:test`, `:karaoke-app:test`, `ktlintCheck`, `nginx -t`, guard-ы (quickstart, проверки 4–8)
- [x] T042 Проверить, что значения секретов не попали в diff (`git diff master...HEAD | grep -inE 'secret\s*=\s*["'"'"'][0-9a-f]{16,}'` → пусто) (quickstart, проверка 6)
- [x] T043 Написать `specs/564-impl-properties-captcha/report.md` для трекера: что было, что изменено, как проверялось, что осталось за владельцем (инвентаризация, чистка, выдача ключа, деплой)

---

## Dependencies

```text
Phase 1 (Setup) → Phase 2 (Foundational) → Phase 3 (US1) → Phase 4 (US3) → Phase 5 (US2) → Phase 6 (US4) → Phase 7 (Polish)
```

- US1 (P1) — базовый инкремент: без него дыра записи открыта.
- US3 (P2) — зависит от Foundational (нужен контракт секрета); реализуется вместе с US1.
- US2 (P1) — независим по коду от US1/US3, но **поставляется вместе** с ними: FR-016
  (починка половины цепочки — незавершённая починка).
- US4 (P3) — зависит от US1 и US2 (журнал пишется при отказе).
- Phase 7 — после всех сценариев.

## Parallel Execution Examples

```text
Phase 1:  T002, T003, T004 — параллельно (разные файлы)
Phase 2:  T006 — до остальных; T007–T011 — в одном файле по очереди, T011 независим
Phase 3:  T012–T016 — по очереди (один тестовый файл); T017 независим
Phase 4:  T018–T023 — по очереди (один тестовый файл)
Phase 5:  T028 — по очереди; T029 — по очереди; T025–T027, T030 — независимы между собой
Phase 6:  T031–T033 — по очереди
Phase 7:  T035–T038 — параллельно (разные файлы)
```

## Implementation Strategy

1. **MVP (минимум, дающий ценность)**: Phase 1 + Phase 2 + Phase 3 — запись закрыта,
   секретный ключ не выдаётся, попытки видны.
2. **Икремент 2**: Phase 4 — легитимный путь доказан тестами, границы выдачи зафиксированы.
3. **Икремент 3**: Phase 5 — вторая половина цепочки закрыта (fail-closed капча, ключ в env).
4. **Финал**: Phase 6 + Phase 7 — наблюдаемость, документация, knowledge, отчёт.

**Критично**: Phase 3 (US1) и Phase 5 (US2) выпускаются **вместе** — иначе цепочка
«аноним пишет → обнуляет ключ → регистрация без капчи» остаётся разорванной лишь наполовину
(FR-016). Порядок развёртывания (решает владелец, quickstart): сначала env
(`CAPTCHA_SERVER_KEY`, `SYNC_INTERNAL_SECRET`), потом миграция таблицы, потом деплой кода и
nginx — иначе регистрация временно отклоняется (ожидаемое поведение, не регрессия).
