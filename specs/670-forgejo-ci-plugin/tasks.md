---
description: "Task list — плагин DSH «Forgejo CI» (670)"
---

# Tasks: плагин DSH «Forgejo CI»

**Input**: `specs/670-forgejo-ci-plugin/` — [spec.md](spec.md) (контракт),
[plan.md](plan.md), [research.md](research.md), [data-model.md](data-model.md),
[contracts/](contracts/), [quickstart.md](quickstart.md)

**Prerequisites**: план принят лидом 2026-10-28 с четырьмя правками (A: честная
гранулярность `wait`; B: полный снимок + бюджет ≤ 8 КБ; C: буфер в памяти и
фраза в README; D: перечислимый `enum error`). Задачи приняты вторым ревью
2026-10-28: метка `fromCache` в UI (T022), копия контракта с тестом (T044),
проверка снятия плагина (T045).

**Path conventions**:

- код плагина — репозиторий `svoemesto/dsh-forgejo-ci`, рабочая копия
  `/home/nsa/Karaoke/.scratch/karaoke/forgejo-ci-plugin/repo/` → в задачах
  записан как `repo/<файл>`;
- доки спеки — `specs/670-forgejo-ci-plugin/`;
- правки Karaoke — `knowledge/system/infra/ci-tools.md` (ветка
  `670-forgejo-ci-plugin`, PR, без прямых коммитов в `master`).

**Tests**: включены намеренно — требование лида (чистая логика под `node --test`).
Сеть, DSH и браузер в тестах не участвуют.

## Phase 1: Setup (каркас пакета)

**Purpose**: пакет, который вообще может быть установлен; никакой логики.

- [x] T001 Создать `repo/package.json`: `name: dsh-forgejo-ci`, `type: module`, `exports` (`.`, `./client`, `./locale/*.json`, `./icon`, `./package.json`), `dsh.bundle.patch: ./cordis.patch.yml`, `dsh.client.platform: web`, `files`, `engines.node >= 22`
- [x] T002 Заполнить `repo/cordis.patch.yml`: одна строка `id: forgejo-ci`, `name: dsh-forgejo-ci`, `config` с дефолтами (`baseUrl`, `repo`, `tokenRef`, `pollMs: 15000`, `runsLimit: 5`, `staleAfterMs: 45000`, `queueStuckAfterMs: 600000`, `routePath: /forgejo-ci/state`) — **без секретов**
- [x] T003 [P] Написать `repo/README.md`: назначение, таблица конфига, раздел «Известные ограничения» с фразой «после перезапуска DSH история прогонов пуста; Forgejo хранит историю недолго», раздел «Как установить»
- [x] T004 [P] Написать `repo/PROVENANCE.md`: откуда взята форма пакета (`git-branch-chip`, `skills-plugins-viewer`), что изменено, что скопировано без изменений
- [x] T005 [P] Создать `repo/locale/ru.json` и `repo/locale/en.json`: `meta.title`, `meta.description` + строки панели (заголовки, статусы, «сейчас бегут», «данные устарели», «токен не настроен», «нет доступа к репозиторию», toast-тексты)
- [x] T006 [P] Нарисовать `repo/icon.svg`: оригинальная иконка «терминал/CI» 24×24, `currentColor`, без внешних ссылок
- [x] T007 [P] Добавить `repo/.gitignore`: `node_modules/`, `.env`, `*.log`, `coverage/`

---

## Phase 2: Foundational (чистая логика — блокирует все истории)

**Purpose**: всё рискованное, что можно проверить без DSH и сети. Пока этот
блок не зелёный, host- и client-половины не начинаются.

- [x] T008 Написать `repo/lib/config.js`: дефолты, нормализация и валидация (`tokenRef` по `/^[A-Za-z_][A-Za-z0-9_]*$/`, `pollMs >= 5000`, `runsLimit` 1..20, `routePath` абсолютный без хвостового `/`, `baseUrl` http(s)); JSDoc на экспортах
- [x] T009 [P] Написать `repo/test/config.test.mjs`: дефолты при пустом конфиге; отказ на мусорный `tokenRef`, отрицательный `pollMs`, `runsLimit: 0` и `: 21`, относительный `routePath`
- [x] T010 Написать `repo/lib/runs.js`: `normalizeRun()` — `duration` нс→с (округление), `url` из `index_in_repo` (fallback — из `html_url`), нулевая метка `1970-01-01` → `null`, незнакомый статус → `unknown`, белый список полей (никакого копирования объекта API)
- [x] T011 [P] Написать `repo/test/runs.test.mjs`: 331000000000 → 331; ждущий прогон → `durationSec: null`; `id 398 / index 32` → ссылка `/actions/runs/32`; `started: 1970-01-01` → `null`; лишние поля из API не попадают в результат
- [x] T012 Написать `repo/lib/buffer.js`: кольцевой буфер ёмкостью `runsLimit`; `merge(apiRuns)` — объединение по `id`, обновление без дублей, вытеснение старого, пометка `fromCache: true` для прогонов, отсутствующих в ответе API
- [x] T013 [P] Написать `repo/test/buffer.test.mjs`: API вернул 1 прогон, буфер отдаёт 5; повторная вставка того же `id` не дублирует и обновляет `durationSec`; вытеснение по `id`; пустой ответ API не стирает буфер
- [x] T014 Написать `repo/lib/snapshot.js`: `buildSnapshot({runs, tasks, runner, now, ...})` — порядок и обрезка до `runsLimit`, `stale` по `staleAfterMs`, `lastOkAt`, `lastActivityAt = max(runs.updatedAt, tasks.runStartedAt)`, состояние раннера (`running`/`idle`/`queue-stuck`/`offline`/`stale`), гейт `jobs` (только при активном последнем прогоне), `error` из enum
- [x] T015 [P] Написать `repo/test/snapshot.test.mjs`: `stale` после 45 с; `queue-stuck` после 10 мин без активности; `running` при активных задачах; `offline` при пустом списке раннеров; `jobs: null` у завершённого последнего прогона; `jobs` заполнен при `running`; `lastOkAt` не теряется при ошибке; `active` из API не выходит наружу
- [x] T016 Написать `repo/lib/forgejo.js`: `fetch` с таймаутом 5 с и `AbortSignal`; маппинг ошибок в enum (`token-missing` — нет значения ссылки, `forbidden` — 403, `network` — таймаут/отказ, `http-4xx`, `http-5xx`); только GET-запросы, ни одной операции записи (FR-017); заголовок `Authorization` только в исходящем запросе, в тексты ошибок и логи не попадает
- [x] T017 [P] Написать `repo/test/secret.test.mjs`: `JSON.stringify(buildSnapshot(...))` с подставленным тестовым токеном не содержит его ни в одном поле; объект ошибки `forgejo.js` не содержит значения токена
- [x] T044 [P] Скопировать контракт в плагин: `repo/lib/state.schema.json` — байт-в-байт копия `specs/670-forgejo-ci-plugin/contracts/state.schema.json` (в шапке обоих файлов — строка «источник правды»); написать `repo/test/schema.test.mjs`: читает `repo/lib/state.schema.json`, проверяет, что `buildSnapshot()` содержит все `required`-поля, что значения `enum` поля `error` совпадают со словарём `lib/forgejo.js`, и что `schemaVersion` — целое ≥ 1. Без зависимостей: `assert` по прочитанному JSON, не полноценный валидатор. *(ID сохранён из ревью лида, чтобы не перенумеровывать 45 задач; выполняется в Phase 2, до гейта T018.)*
- [x] T018 Прогнать `cd repo && node --test` — все тесты зелёные (гейт для всех следующих фаз)

---

## Phase 3: User Story 1 — человек видит состояние CI (P1)

**Goal**: иконка с бейджем в сайдбаре + панель с прогонами, раннером и живой
очередью; данные из кеша хоста.

**Independent Test**: открыть панель в живом GUI — строки совпадают с Forgejo,
иконка показывает точку при `failure` и пульс при `running`.

- [x] T019 [US1] Написать `repo/index.js` (host): `inject = ['webServer','tools','credentials']`, разрешение токена перед каждым опросом, опрос трёх эндпоинтов параллельно с таймаутом, кеш + `lastOkAt`, сборка снимка через `lib/`, никаких `throw` из опроса
- [x] T020 [US1] Добавить в `repo/index.js` таймер опроса через `ctx.effect` (снятие при выгрузке) и маршрут `GET /forgejo-ci/state` (405 на прочие методы, `Cache-Control: no-store`, бюджет ≤ 8 КБ при `runsLimit: 5`)
- [x] T021 [US1] Написать `repo/client.js` — каркас модуля `__ModuleLoader__.load({id:'dsh-forgejo-ci'})`, React через `require('react')`, регистрация стилей на токенах `--dsw-alias-*`, тексты через `ctx.locale`
- [x] T022 [US1] Добавить в `repo/client.js` страницу в `main` (`key: forgejo-ci`): таблица прогонов (номер, ref, событие, статус, длительность), строка раннера, строка «сейчас бегут …» (только когда она не `null`), ссылки `html_url` в новой вкладке; прогон с `fromCache: true` получает видимую метку «из кеша» с `title` «Forgejo больше не отдаёт этот прогон» (FR-004)
- [x] T023 [US1] Добавить в `repo/client.js` иконку в `sidebar.panellist` (`id: forgejo-ci`, `order: 100`, `size×size`): красная точка при `failure` последнего завершённого прогона, пульс при `running`, `label` через локаль
- [x] T024 [US1] Добавить в `repo/client.js` опрос `fetch('/forgejo-ci/state')` раз в `pollMs` с `AbortController` и полной очисткой на размонтировании (FR-018: ни одного таймера после снятия)
- [x] T025 [US1] Проверить US1: `node --test`; после установки (T039, делает лид) — `curl /forgejo-ci/state`, occupant `forgejo-ci` в `sidebar.panellist`, панель в живом GUI

---

## Phase 4: User Story 2 — агент узнаёт итог прогона (P1)

**Goal**: `forgejo_ci_status` (кеш) и `forgejo_ci_wait` (ожидание с таймаутом).

**Independent Test**: в живой сессии — статус < 2 с и совпадает с панелью;
`wait` на завершённом прогоне отвечает сразу, на идущем — по таймауту с
`timedOut: true`.

- [x] T026 [US2] Написать `repo/lib/tools.js`: `statusTool(getSnapshot)` — параметры `{runs?: 1..20}`, ответ = снимок с обрезанным `runs` (только массив), `output.schema` — из `repo/lib/state.schema.json` (копия контракта, источник правды — `specs/670-forgejo-ci-plugin/contracts/state.schema.json`), `render` — короткая строка для карточки
- [x] T027 [US2] Добавить в `repo/lib/tools.js` `waitTool(getSnapshot, clock)` — `{runId?, timeoutSec?}` (10..600, по умолчанию 300), ожидание по кешу с проверкой раз в 1 с, реакция ≤ `pollMs + 1 с` (в описании tool'а — честная формулировка, не «за секунду»), `waitedSec` в реальных секундах, отмена по `AbortSignal` без утечки таймера, ошибки `unknown-run` (со списком известных id), `no-runs`, `timeout-too-long`; в ответе — `stale` и `lastOkAt` (FR-008), как у `forgejo_ci_status`
- [x] T028 [P] [US2] Написать `repo/test/tools.test.mjs` на инъекции `clock`: выбор самого нового прогона при `runId` отсутствующем; прогон уже терминален → `waitedSec: 0`; истечение таймаута → `timedOut: true` и текущий статус; неизвестный `runId` → форма ошибки со списком id; `timeoutSec > 600` → `timeout-too-long`
- [x] T029 [US2] Зарегистрировать оба tool'а в `repo/index.js` внутри `ctx.effect`, `timeoutMs: 620000`, `isConcurrencySafe: () => true`, английские описания (status — дешёвый, wait — экономит цикл опросов)
- [x] T030 [US2] Проверить US2 по `specs/670-forgejo-ci-plugin/quickstart.md` §3: пять сценариев вызовов + отсутствие токена в ответах; создать `specs/670-forgejo-ci-plugin/report.md` (каркас с заголовками «Что проверено», «Чем проверено», «За границей», «Отклонения»), если его нет, и записать результат туда

---

## Phase 5: User Story 3 — уведомление о завершении прогона (P2)

**Goal**: toast в `shell.overlay`: падение заметно, успех тихо, без дублей и без
«здоровканья» старым прогоном.

**Independent Test**: дождаться завершения прогона при открытом GUI — ровно одно
уведомление нужного веса; перезагрузка страницы не даёт уведомления о старом
прогоне.

- [x] T031 [US3] Добавить в `repo/client.js` контейнер toast'ов в `shell.overlay` (`id: forgejo-ci-toasts`): `pointer-events: auto` на своих элементах, максимум 3 на экране, не чаще одного в 5 с, авто-скрытие 8 с (падение) / 3 с (успех), крестик
- [x] T032 [US3] Добавить в `repo/client.js` детекцию перехода `waiting|running → терминальный` между двумя опросами, дедуп по `run.id` в памяти вкладки, гейт FR-013 (первый опрос после загрузки не уведомляет), приглушённый стиль для успеха
- [x] T033 [US3] Проверить US3 по `repo/client.js` в живом GUI: прогон на падение и на успех; повторный рендер без дублей; перезагрузка страницы без уведомления о старом прогоне; результат — в `specs/670-forgejo-ci-plugin/report.md`

---

## Phase 6: User Story 4 — деградация вместо падения (P2)

**Goal**: `stale`-полоса, «токен не настроен» ≠ «нет доступа», неизвестная
версия схемы, отсутствие падений.

**Independent Test**: сломать `baseUrl` — панель помечена «устарело» с временем
последнего успеха; указать несуществующий `tokenRef` — «токен не настроен».

- [x] T034 [US4] Добавить в `repo/index.js` заполнение `error` по enum (`token-missing` / `forbidden` / `network` / `http-4xx` / `http-5xx`), сохранение `lastOkAt` и прошлого снимка при ошибке, отсутствие `throw` и необработанных промисов
- [x] T035 [US4] Добавить в `repo/client.js` состояния: полоса `stale` с временем последнего успеха, экраны «токен не настроен», «нет доступа к репозиторию», плашка «плагин и хост разной версии» при неизвестном `schemaVersion`, «прогонов пока нет»
- [x] T036 [US4] Проверить US4 по quickstart §5: недоступный Forgejo, битый `tokenRef`, восстановление без перезапуска DSH (правки конфига профиля — с участием лида)

---

## Phase 7: Polish, установка и приёмка

**Purpose**: довести до «устанавливается одной командой», отдать установку
лиду, закрыть приёмку.

- [x] T037 [P] Дописать `repo/README.md`: точная команда установки, таблица конфига, как проверить маршрут и tool'ы, «Известные ограничения» (буфер в памяти, джоб-разбивки нет, dispatch недоступен)
- [x] T038 Собрать git-состояние: `git status` чистый, `git ls-files | grep -iE '\.env$|\.key$|\.pem$'` пусто, коммит в ветку `main` репозитория плагина, пуш
- [x] T039 Подготовить и записать в отчёт точную команду установки (`plugin_manager install_bundle` с абсолютным путём клона) и **остановиться** — установку выполняет лид, подтверждает `application: applied`
- [x] T040 [P] Добавить запись о плагине в `knowledge/system/infra/ci-tools.md` (раздел операционных инструментов: что это, где живёт, как включить, какие ограничения) — ветка `670-forgejo-ci-plugin`, PR в Karaoke
- [x] T041 Приёмка после установки по `specs/670-forgejo-ci-plugin/quickstart.md` §4: перезагрузка страницы, панель/бейдж/**живой toast** (#36 `running → cancelled`, 163 с; владелец видел тихое уведомление за ~15 с), DevTools — токена нет; результат — в `specs/670-forgejo-ci-plugin/report.md`
- [x] T045 Проверить SC-007/FR-018 снятием: `plugin_manager` отключает плагин → иконка, панель и toast'ы исчезают без перезагрузки процесса и без ошибок в консоли; включение возвращает всё на место; результат — в `report.md`
- [x] T042 Заполнить `specs/670-forgejo-ci-plugin/report.md` (что проверено, чем, что осталось за границей, `schemaVersion` и rev клиентского артефакта) и опубликовать в трекер: `add-comment KARAOKE-305` → `mark-review`
- [x] T043 Убрать временные артефакты: `/tmp/forgejo-askpass.sh`, локальные временные файлы проверок; убедиться, что значение токена не осталось ни в одном файле рабочей копии

## Phase 8: Изменение формы входа (слово владельца 2026-10-28)

- [x] T046 Обновить контракт в `spec.md`: FR-010 (кнопка в верхней панели с индикатором, видна без сессии), FR-011 (popover с прогонами и раннером, Esc/клик вне, «открыть полностью» на страницу), сценарии US1; пометка «изменено словом владельца 2026-10-28»
- [x] T047 Кнопка в `conversation.header.leading` (single-слот, регистрация без `id/order/label`) с индикатором `failure`/`running`; регистрация в `sidebar.panellist` удалена целиком
- [x] T048 Popover в `shell.overlay` (`id: forgejo-ci-popover`, order 90): `position: fixed` по `getBoundingClientRect` якоря, прижатие к вьюпорту, пересчёт на `resize`/`scroll`, закрытие по Esc и клику вне, «открыть полностью» → `layout.selectPanel('forgejo-ci')`; состояние в модульном сторе, слушатели и таймеры снимаются `ctx.effect`
- [x] T049 Страница в `main` сохранена; `package.json` → 0.2.0; README, `knowledge/system/infra/ci-tools.md` и `report.md` обновлены; тесты `test/entry.test.mjs` (13: регистрации, позиционирование, машина состояний, бейдж, подписка)

- [x] T050 Спека: FR-010 — кнопка в правой части шапки сессии (`conversation.session.header.utilities`, order 90), ограничение session-scope и закрытие popover при размонтировании; сценарии US1 поправлены, SC не тронуты
- [x] T051 Клиент: регистрация перенесена в `conversation.session.header.utilities` (`id: forgejo-ci`, `order: 90`), `conversation.header.leading` освобождён; unmount-эффект кнопки закрывает popover и снимает слушатели; тест на размонтирование в `test/entry.test.mjs`
- [x] T052 Документы: `package.json` → 0.3.0; README (кнопка справа в шапке); `knowledge/system/infra/ci-tools.md`; `report.md` — второй пункт изменения формы

- [x] T053 Семантика индикатора: `lastOutcome()` — первый прогон со статусом `success`/`failure` (cancelled пропускается), `badgeState()` на нём; синяя точка (`--dsw-alias-brand-primary`) плюс пульс при активном прогоне, красная точка (`--dsw-alias-state-error-primary`) главнее; цвета — на токенах темы
- [x] T054 Тесты и документы индикатора: `[cancelled, cancelled, failure] → failure`, `[cancelled, success] → idle`, только `cancelled → idle`, `[running] → running`, `[running, failure] → failure`, разметка кнопки; `package.json` → 0.4.0, README, `knowledge/system/infra/ci-tools.md`, `report.md`, спека FR-010

- [x] T055 Развести память и витрину: `historyLimit` (20, 5..100, ≥ `runsLimit`) в конфиге; буфер и снимок по `historyLimit`; витрина `runsLimit` в панели, popover и `forgejo_ci_status` по умолчанию; `maxItems` в полной схеме до 100, `schemaVersion` остаётся 1
- [x] T056 Тесты: буфер держит 20; снимок отдаёт больше 5; индикатор видит исход за пределами первых пяти (12 записей, `failure` в конце → `failure`); tool по умолчанию отдаёт 5, по `runs: 20` — всю историю; панель показывает 5 из 20; конфиг `historyLimit` (границы и связь с `runsLimit`)
- [x] T057 Документы: `package.json` → 0.5.0; README (два параметра и зачем); `contracts/tools.md` (пределы); `data-model.md`; спека FR-004/FR-010; `report.md` — находка с числами

- [x] T058 Полная страница показывает всю историю снимка (до `historyLimit`), popover остаётся на `runsLimit`; список с ограничением высоты и прокруткой, шапка таблицы закреплена; тесты: 12 строк на странице при 12 в снимке, 5 в popover, прокрутка/липкая шапка по CSS
- [x] T059 Документация HMR исправлена по факту: правка клиента — перезагрузка страницы, правка host-кода — `set_bundle` off/on (HMR на пакете не срабатывает); README и `knowledge/system/infra/ci-tools.md`
- [x] T060 `package.json` → 0.5.1; спека FR-001/FR-011 (страница = вся история, popover = витрина); `report.md` — пятая правка с числами 1749 Б/5 → 3603 Б/12

---

## Dependencies (порядок исполнения)

```text
Phase 1 (T001–T007)
   └── Phase 2 (T008–T017, T044 → T018)   # копия контракта, затем гейт node --test
         ├── Phase 3 US1 (T019–T025)   # host + панель; US2 нужен его сервис
         │     └── Phase 4 US2 (T026–T030)
         │           └── Phase 5 US3 (T031–T033)   # toast поверх того же опроса
         │                 └── Phase 6 US4 (T034–T036)
         └── Phase 7 (T037–T043, T045)   # T039 — СТОП: установка делает лид
```

- **US1 → US2**: tool'ы читают снимок, который собирает сервис US1; логика
  tool'ов (`lib/tools.js`) пишется параллельно с US1, но регистрация и проверка —
  после него.
- **US3** — надстройка над опросом US1, отдельного host-кода не требует;
  **US4** трогает и host (маппинг `error` в `index.js`, T034), и client (T035).
- **T039 — жёсткая остановка**: дальше T041 нельзя, пока лид не подтвердил
  `application: applied`.
- **Порядок Phase 7 после установки**: T041 (приёмка) → T045 (снятие/возврат,
  SC-007) → T042 (отчёт и трекер) → T043 (уборка). Отчёт пишется последним,
  чтобы включить в него результат проверки снятия.

## Parallel opportunities

- T003–T007 (README, PROVENANCE, локали, иконка, `.gitignore`) — разные файлы.
- T009, T011, T013, T015, T017 — тесты разных модулей.
- T028 — тест tool'ов пишется параллельно с T026–T027.
- T037 и T040 — README плагина и запись в `knowledge/` (разные репозитории).

## MVP scope

**US1** (T001–T025): пакет + чистая логика + host + панель с бейджем. Это уже
снимает основную боль человека. US2 добавляет агентский путь, US3–US4 —
уведомления и устойчивость; они не блокируют MVP.
