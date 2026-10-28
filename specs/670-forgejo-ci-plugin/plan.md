# Implementation Plan: плагин DSH «Forgejo CI»

**Branch**: `670-forgejo-ci-plugin` | **Date**: 2026-10-28 | **Spec**: [spec.md](spec.md)

**Input**: [spec.md](spec.md) (контракт, не меняется) + карта решений
`.scratch/karaoke/wayfinding/forgejo-ci-plugin/map.md` (факты API Forgejo
15.0.10 + 9 решений владельца) + тикеты `10`–`13` (критерии приёмки исполнения).

## Summary

Две половины одного пакета `dsh-forgejo-ci`, связанные одним JSON-контрактом:

- **host** (`index.js` + `lib/`) — раз в 15 с опрашивает Forgejo read-only
  токеном из хранилища секретов, держит снимок (прогоны + раннер + живая
  очередь задач + признаки свежести), отдаёт его по `GET /forgejo-ci/state`
  и регистрирует два tool'а: `forgejo_ci_status` (кеш, мгновенно) и
  `forgejo_ci_wait` (ждёт завершения с таймаутом);
- **client** (`client.js` — один файл в формате `__ModuleLoader__`, без сборки) —
  иконка с бейджем в `sidebar.panellist`, страница в `main`, toast'ы в
  `shell.overlay`; данные читает `fetch('/forgejo-ci/state')` раз в 15 с.

Код — в отдельном репозитории `svoemesto/dsh-forgejo-ci` (клон
`.scratch/karaoke/forgejo-ci-plugin/repo/`), спека/план/запись в `knowledge/` —
в Karaoke. Чистый JS/ESM, **ноль зависимостей**, автотесты `node --test` на
чистую логику.

## Technical Context

**Language/Version**: JavaScript ESM, Node ≥ 22 (на машине 25.7.0); браузер —
Chromium/Firefox из состава DSH Web GUI.

**Primary Dependencies**: **нет**. `@deepseek-ai/*`, `zod`, `react`,
`@deepseek-ai/schemastery` из каталога плагина **не резолвятся** (проверено
`require.resolve` → NOT RESOLVABLE). В браузерной половине React берётся из
таблицы модулей: `require('react')`; всё остальное — стандартные API браузера
(`fetch`, `setInterval`, `AbortController`).

**Storage**: в памяти процесса (снимок + кольцевой буфер прогонов), файлов и БД
нет. Значение токена — только в хранилище секретов DSH
(`~/.dsh/.credentials.yaml`, `refs.FORGEJO_CI_TOKEN`), плагин его не кеширует и
не пишет.

**Testing**: `node --test` в репозитории плагина (`test/*.test.mjs`), только
чистая логика (`lib/`), без сети и без DSH. Приёмочная проверка хоста и
интерфейса — вручную по [quickstart.md](quickstart.md) в живом GUI.

**Target Platform**: DSH, профиль `web` (`http://127.0.0.1:3080`), Linux
(`nsa-i9`). Установка — `plugin_manager install_bundle` с путём клона.

**Project Type**: плагин харнесса (пакет с двумя половинами: host-сервис +
браузерный модуль), а не приложение.

**Performance Goals**: опрос Forgejo — 3 запроса раз в 15 с (≈0.1 с каждый);
ответ `GET /forgejo-ci/state` — из кеша, < 100 мс; `forgejo_ci_status` — < 2 с
(фактически миллисекунды); доставка уведомления о завершении — ≤ 15 с после
факта.

**Constraints**: ноль зависимостей (иначе пакет не соберётся в профиле);
Forgejo может быть недоступен — деградация в `stale`, а не исключение; история
прогонов в Forgejo эфемерна (пурж) — нужен кольцевой буфер; джоб-уровень
привязан только к последнему прогону; токен не покидает хранилище секретов;
значения длительностей приходят в наносекундах; ссылка строится из
`index_in_repo`, не из `id`.

**Scale/Scope**: один репозиторий (`svoemesto/Karaoke`), один workflow
(`lint.yml`, 30 джобов), 5 прогонов в списке, одна панель, два tool'а, ~8 файлов
кода (из них 3 — чистые функции под тестами).

## Constitution Check

*GATE: до Phase 0 и повторно после Phase 1.*

Конституция описывает продукт Karaoke (Kotlin/JDBC/два фронтенда). Плагин — не
компонент продукта, поэтому применимость проверена поштучно:

| Принцип | Применимость | Статус |
|---|---|---|
| I. Self-contained автопайплайн | не применим — плагин не участвует в пайплайне обработки | PASS (n/a) |
| II. Сырой JDBC + хэши | не применим — БД нет | PASS (n/a) |
| III. Двух-БД синхронизация | не применим | PASS (n/a) |
| IV. Async-очередь с stdout | не применим — фоновых процессов нет | PASS (n/a) |
| V. Два фронтенда | не применим — интерфейс живёт в Web GUI DSH | PASS (n/a) |
| VI. Code Standards (FR-006/007/009) | **применим частично**: ktlint/ESLint к JS-плагину не относятся | PASS при выполнении: JSDoc в `lib/`; запись в `knowledge/system/infra/ci-tools.md` (SSoT) в PR Karaoke. **Per-feature документ не заводится** — код вне репозитория Karaoke, guard `check-feature-doc.sh` его не проверит, а документ сгниёт |
| VII. Cross-Machine Setup | не применим — локальных AI-конфигов не добавляем | PASS (n/a) |
| VIII. Секреты и git-гигиена | **применим полностью** | PASS: токен только из хранилища секретов, в обоих репозиториях `git ls-files \| grep -iE '\.env$\|\.key$\|\.pem$'` → пусто, значение не логируется и не отдаётся |
| IX. Knowledge-first | **применим** | PASS: pre-flight выполнен при specify (7 ссылок, маркер `.specify/.preflight-forgejo-ci-plugin.ok`); правка `knowledge/system/infra/ci-tools.md` — в PR Karaoke |
| Ограничения агента (контейнеры, деплой, секреты в выводе) | применим п.6 (не печатать секреты) | PASS |

**Нарушений нет** → Complexity Tracking пуст.

## Project Structure

### Documentation (репозиторий Karaoke)

```text
specs/670-forgejo-ci-plugin/
├── plan.md              # этот файл
├── research.md          # Phase 0: решения с доказательствами из кода DSH
├── data-model.md        # Phase 1: сущности, поля, состояния, инварианты
├── contracts/
│   ├── state.schema.json # контракт снимка (host → client и host → tool)
│   └── tools.md          # схемы двух tool'ов: параметры, ответ, ошибки
├── quickstart.md        # как проверять: тесты, маршрут, живой GUI, деградация
├── checklists/requirements.md
└── tasks.md             # Phase 2 (/speckit.tasks), создаётся НЕ здесь
```

### Код (репозиторий `svoemesto/dsh-forgejo-ci`, клон в `.scratch/.../repo/`)

```text
package.json            # name dsh-forgejo-ci, type module, exports ., ./client, ./locale/*.json, ./icon
                        # dsh.bundle.patch → ./cordis.patch.yml; dsh.client → platform: web
cordis.patch.yml        # одна строка плагина с config (без секретов)
index.js                # host: apply(ctx, config) — опрос, кеш, маршрут, два tool'а
client.js               # браузерный модуль (__ModuleLoader__.load), один файл, без сборки
lib/
├── snapshot.js         # сборка снимка: слияние API + буфер, статус раннера, stale
├── runs.js             # нормализация прогона: duration (нс→с), ссылка из index_in_repo
├── buffer.js           # кольцевой буфер прогонов при пурже
├── forgejo.js          # HTTP-клиент: fetch, таймауты, маппинг 403/404/5xx
└── config.js           # дефолты + валидация конфига (без schemastery)
test/
├── runs.test.mjs
├── buffer.test.mjs
├── snapshot.test.mjs
├── config.test.mjs     # дефолты, валидация tokenRef, отказ на мусор
└── secret.test.mjs     # значение секрета не попадает в сериализуемый снимок
locale/{ru,en}.json     # meta.title/description + строки панели
icon.svg
README.md
PROVENANCE.md           # откуда взят каркас (git-branch-chip / skills-plugins-viewer)
```

**Structure Decision**: один пакет, две половины, общий контракт; общий код
между половинами **не выносится** — браузерная половина обязана быть одним
файлом без импортов (иначе её придётся собирать), поэтому форматирование в
`client.js` минимальное и не дублирует бизнес-логику: всё, что можно посчитать
на хосте (секунды, возраст, статусы, ссылки), хост и считает.

## Phase 0: Research

Все решения Phase 0 собраны в [research.md](research.md) — по одному блоку
«Решение / Обоснование / Отброшенные альтернативы», каждое с фактом из кода DSH
или из API Forgejo:

1. **Доставка состояния в браузер** — свой точный маршрут `webServer.register`
   + `fetch`, не проекции сессий и не `@Remote`.
2. **Секрет** — `credentials.resolve('FORGEJO_CI_TOKEN')`; ref — обычная строка
   (branded-тип только для TS), паттерн `/^[A-Za-z_][A-Za-z0-9_]*$/`.
3. **Tool'ы без зависимостей** — `ctx.tools.register` с обычным объектом
   `{name, description, parameters, output:{schema, render}, execute}`;
   `timeoutMs` уважается guard'ом `timeout-policy`.
4. **Клиент без сборки** — один файл `client.js` в формате `__ModuleLoader__`,
   React через `require('react')`; локальные импорты невозможны без сборки.
5. **Слоты** — `sidebar.panellist` (id → ключ `main`), `main` (keyed, **без
   Session-binding**), `shell.overlay` (list, слой click-through — toast'ы
   включают `pointer-events: auto` сами).
6. **Формы данных API** — длительность в нс, ссылка из `index_in_repo`, у
   ждущих `started = 1970-01-01`, `conclusion` отсутствует, `actions/tasks`
   только для последнего прогона и с устаревшими `running`.

NEEDS CLARIFICATION: **нет**.

## Phase 1: Design

- [data-model.md](data-model.md) — сущности `Run`, `RunnerState`, `Task`,
  `Snapshot`, `TokenRef`; инварианты (порядок, буфер, гейт строки задач) и
  правила вычисления `stale` / `queue-stuck`.
- [contracts/state.schema.json](contracts/state.schema.json) — JSON Schema
  снимка: единственный контракт между host, client и tool'ом статуса.
- [contracts/tools.md](contracts/tools.md) — схемы `forgejo_ci_status` и
  `forgejo_ci_wait`, включая коды ошибок и поведение на таймауте.
- [quickstart.md](quickstart.md) — прогон тестов, проверка маршрута curl'ом,
  проверка панели/бейджа/toast в живом GUI, тест деградации.

## Execution Phases (порядок работ)

Соответствие тикетам карты (там же критерии приёмки):

| Фаза | Тикет | Что появляется | Проверка |
|---|---|---|---|
| 1. Каркас репозитория | `08` | `package.json`, `cordis.patch.yml`, `locale/`, `icon.svg`, `PROVENANCE.md` | пакет валиден, `git ls-files` без секретов |
| 2. Чистая логика + тесты | `10` (часть) | `lib/*` + `test/*` | `node --test` зелёный |
| 3. Host-половина | `10` | `index.js`: опрос, кеш, маршрут, деградация | `curl /forgejo-ci/state` |
| 4. Tool'ы | `11` | `forgejo_ci_status`, `forgejo_ci_wait` | вызовы в живой сессии |
| 5. Client-половина | `12` | `client.js`: иконка+бейдж, панель, toast | живой GUI |
| 6. Установка и приёмка | `13` | установка в профиль, проверка, доки | чек-лист `13` + `knowledge/` PR |

Порядок жёсткий: 1 → 2 → 3 → 4 → 5 → 6. Фаза 6 включает PR в Karaoke —
**только** запись в `knowledge/system/infra/ci-tools.md`; README и
`PROVENANCE.md` плагина (с разделом «Известные ограничения») живут в репозитории
плагина, per-feature документ Karaoke не заводится (решение лида 2026-10-28).

Трекер: `KARAOKE-305` (Plane, `In progress`, assignee `ai-agent` — claim выполнен
2026-10-28). После merge фазы 6 — `report.md` → `tools/tracker.sh add-comment
KARAOKE-305 --file specs/670-forgejo-ci-plugin/report.md` → `mark-review`.

## Risks & Mitigations

| Риск | Проявление | Мера |
|---|---|---|
| Плагин нельзя установить без approval | `plugin_manager` требует подтверждения, в субагентской сессии оно отклоняется автоматически | установку выполняет лид/владелец; я готовлю пакет и точную команду (см. вопросы) |
| Браузерный модуль не подхватился | иконки нет после перезагрузки | проверить `dsh.client` в `package.json` и строку в `cordis.patch.yml`; при необходимости пересборка web-артефактов |
| Пурж прогонов обнуляет список | панель схлопывается в одну строку | кольцевой буфер + `fromCache` (тест `buffer.test.mjs`) |
| Устаревшие `running` в `actions/tasks` | «бегут 19 задач» у провалившегося прогона | гейт: строка задач только при активном последнем прогоне (тест `snapshot.test.mjs`) |
| `waiting` при живом раннере | ложное «ничего не происходит» | статус раннера по свежести активности, а не по `active` |
| Секрет в снимке | токен в ответе маршрута | снимок собирается из явного белого списка полей; тест `secret.test.mjs` |
| Сеть до Forgejo пропала | панель пуста / процесс падает | `stale` + `lastOkAt`, никаких `throw` из опроса |
| Долгий `forgejo_ci_wait` | зависание вызова | `timeoutMs: 620000` у определения, собственный таймаут ≤ 600 с, отмена по AbortSignal |
| Ссылка ведёт не туда | 404 в Forgejo | ссылка из `index_in_repo`, не из `id` (тест `runs.test.mjs`) |

## Resolved by lead (2026-10-28)

| Вопрос | Решение |
|---|---|
| Кто устанавливает плагин | **Лид**. Я готовлю пакет и одну точную команду, довожу до состояния «устанавливается одной командой» и останавливаюсь перед установкой; лид ставит, подтверждает `application: applied`, и только потом я продолжаю приёмку |
| Пересборка web-артефактов | **Лид**: `pnpm run dev:web` идёт из его сессии, а не из субагентской (долгая общая сборка). Если после перезагрузки панели нет — лид пересобирает и сообщает результат |
| `docs/features/forgejo-ci-plugin.md` | **Не заводим**. Вместо него: README + `PROVENANCE.md` в репозитории плагина (там же «Известные ограничения») + запись в `knowledge/system/infra/ci-tools.md` в PR Karaoke |

Внесено лидом также: гранулярность `forgejo_ci_wait` формулируется честно (≤ `pollMs` + 1 с);
полный снимок в `forgejo_ci_status` принят (бюджет ≤ 8 КБ при `runsLimit: 5`);
`error` в схеме — перечислимый enum с разделением `token-missing` / `forbidden`;
в тесты добавлен `config.test.mjs`; правило `schemaVersion` зафиксировано в
[contracts/tools.md](contracts/tools.md).

## Complexity Tracking

Нарушений Конституции нет; дополнительных проектов/слоёв не вводится.
