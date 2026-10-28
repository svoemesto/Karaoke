# Phase 0 — Research: плагин DSH «Forgejo CI»

Все решения ниже опираются на проверенные факты: код DSH в чекауте
`/home/nsa/deepseek-harness`, ответы API Forgejo 15.0.10 и живой профиль `web`.
«Проверено» = команда/вызов, а не память.

## R1. Как состояние хоста попадает в браузер

**Решение**: собственный точный маршрут `GET /forgejo-ci/state` через
`ctx.webServer.register({kind:'exact', path, handler})`; браузер читает его
`fetch`'ем раз в 15 с.

**Обоснование (проверено)**:

- `webServer` есть в каталоге host-сервисов: `register(route: WebRoute)` →
  `{kind:'exact'|'prefix', path, handler(req,res)}`, дубликат `(kind,path)`
  бросает.
- Живой прецедент в этом профиле: `skills-plugins-viewer` регистрирует точный
  путь и вызывается из `client.js` через `fetch(REFRESH_PATH, {method:'POST'})`.
- Клиентские сервисы: `layout`, `locale`, `sessions`, `slots`, `theme`,
  `timer`, `uiWorkspace`, `workspaces` — транспорта к хосту нет.
- Пути заняты: `/api` — connection-RPC (`kind:'prefix'`), `/plugins` — выдача
  клиентских бандлов. Наш путь свободен.

**Отброшено**:

- *Session projection* (как `git-branch-chip`): значение привязано к сессии, а
  панель глобальна; в `sidebar.panellist` стандартные props вообще не содержат
  `useProjection` (`ownerProps = {size, active}` + `useRemoteAccess`,
  `useResource`, `useSessions`, …).
- *`@Remote`-метод сервиса*: требует typert/RPC-регистрации, а пакеты харнесса
  из каталога плагина не резолвятся.
- *SSE/WebSocket*: та же свежесть, что у 15-секундного опроса; сложность без
  выгоды.

## R2. Секрет: как плагин получает токен

**Решение**: `ctx.credentials.resolve('FORGEJO_CI_TOKEN')` перед каждым опросом;
значение нигде не кешируется, в конфиге — только имя ссылки.

**Обоснование (проверено)**:

- `credentials` — сервис харнесса: `resolve(ref)`, `describe(ref)`, `set(ref, …)`.
- Реализация `@deepseek-ai/dsh-credentials-local`: `resolve` — обычный `Map`
  lookup по строке, затем env процесса, затем `./.env`, затем `~/.dsh/.env`;
  **branded-тип существует только в TS**, в рантайме это строка — значит из
  чистого JS вызов работает без импорта пакета.
- Паттерн имени ссылки: `/^[A-Za-z_][A-Za-z0-9_]*$/` — `FORGEJO_CI_TOKEN`
  подходит; конфиг валидируем этим же паттерном, иначе имя вне грамматики
  молча читается как «не задано».
- Магазин: `~/.dsh/.credentials.yaml` (600), секция `refs:`. Токен создан
  владельцем и лежит там (проверено: 4 нужных эндпоинта → 200, `GET /user` →
  403, `POST /labels` → 403).

**Отброшено**: значение в конфиге плагина (секрет в патче профиля и в диффах);
чтение `.adm` (привязка к путям Karaoke и к токену владельца с правами записи);
`credentials.set` из плагина (плагин не должен уметь писать секреты).

## R3. Tool'ы без зависимостей

**Решение**: `ctx.tools.register({ name, description, parameters, output:
{schema, render}, execute, timeoutMs })` — обычный объект, без
`defineTool`/`z`-хелперов.

**Обоснование (проверено)**:

- Контракт `ToolDefinition` (инспектор `Tool`/`Service(tools)`): `ToolSchema`
  = `{name, description, parameters: Record<string, unknown>}` +
  `output: {schema: JsonSchemaNode, render(args, value): ContentBlock[]}` +
  `execute(args, exec)`; необязательные `timeoutMs`, `isConcurrencySafe`.
- `tools.register` требует только объект; дубликаты в одном слое падают.
- Guard `timeout-policy` читает **`ctx.tools.get(name).timeoutMs`** и
  прерывает вызов по `deadline(exec.signal, timeoutMs)` → собственный таймаут
  `forgejo_ci_wait` (≤ 600 с) уважается, если объявить `timeoutMs: 620000`.

**Отброшено**: `defineTool`/`zod` (не резолвятся), `systemPrompt.tools`
(подсказка модели, а не tool), `ctx.tools.presentAs('ptc')` (не нужен).

## R4. Браузерная половина без сборки

**Решение**: `client.js` — один файл в формате
`window.__ModuleLoader__.load({id: 'dsh-forgejo-ci', factory: (require) => …})`,
React — `require('react')`, локальных `import` нет.

**Обоснование (проверено)**:

- Профиль `web` содержит три работающих плагина с ровно такой формой
  (`git-branch-chip`, `skills-plugins-viewer`): `package.json` объявляет
  `exports['./client']` + `dsh.client.platform = "web"`, файл отдаётся как
  артефакт, никакой сборки не делается.
- Клиентский модуль получает `ctx` с сервисами (`slots`, `locale`, …),
  регистрации — внутри `apply` с `ctx.effect`/`ctx.on`.

**Следствие для раскладки**: общий код между половинами не выносится —
дублировать бизнес-логику в браузер нельзя, поэтому всё вычислимое (секунды,
возраст, статусы, ссылки, гейт строки задач) считает хост, а `client.js` только
рисует. Форматирование «5 мин 20 с» — единственное, что живёт на клиенте.

**Отброшено**: Vite/tsdown-сборка клиента (лишний тулинг ради одного файла);
второй файл с `import` (не загрузится без сборки).

## R5. Слоты и их поведение

**Решение**: `sidebar.panellist` (`id: forgejo-ci`, `order: 100`) + `main`
(`key: forgejo-ci`) + `shell.overlay` (`id: forgejo-ci-toasts`).

**Обоснование (проверено по исходнику `packages/client/ui-layout/src/client/index.ts`)**:

- `'main': { kind: 'keyed', scope: 'root' }` — в комментарии прямо сказано:
  «The reserved `conversation` key hosts the Conversation; **other keys receive
  no Session binding**» → наша страница не получит сессию и не должна её ждать.
- `sidebar.panellist` (каталог инспектора): «Each list id addresses the matching
  main panel; the sidebar owns the button and resolves its label from list
  metadata» → `id` панели и `key` страницы обязаны совпадать; `label` даёт
  подпись кнопки (можно thunk — локализация без перерегистрации).
- `'shell.overlay': { kind: 'list', scope: 'root' }` — «Frame-wide floating
  layer… The layer itself is **click-through** — entries opt back into pointer
  events» → контейнер toast'ов обязан сам включить `pointer-events: auto`.
- `workspace.toast` не годится: keyed с фиксированным набором ключей
  (`archived`, `pinFailed`, …) — новый ключ не добавить без ядра.
- `conversation.input.dock` живёт только внутри сессии (не виден в панели CI).

## R6. Формы данных Forgejo (что именно нормализуем)

**Решение**: нормализация на хосте один раз, наружу — стабильная схема.

**Обоснование (проверено вызовами 2026-10-28)**:

| Факт | Следствие в коде |
|---|---|
| `duration` в наносекундах, только у завершённых | `Math.round(ns / 1e9)` → секунды; у остальных `null` |
| `html_url` = `/actions/runs/{index_in_repo}` (398 → `/runs/32`) | ссылка строится из `index_in_repo`; `id` — только для сопоставления |
| `conclusion` в инстансе отсутствует | статус берём из `status`; словарь: `waiting`/`running`/`success`/`failure`/`cancelled` |
| `started`/`stopped` у ждущих = `1970-01-01T00:00:00Z` | нулевую метку трактуем как «нет значения» |
| `/runs/{id}/jobs`, `/logs`, `/runs/{id}/tasks` → 404 | джоб-детализации по прогону нет |
| `actions/tasks` отдаёт только последний прогон, `limit`/`status` не работают | живая очередь — отдельный блок снимка; строку показываем только при активном последнем прогоне |
| `tasks` у завершённого прогона держит `running` (19 из 25) | гейт: `jobs` заполняется только если последний прогон `running`/`waiting` |
| история пуржится (4 → 1 за 40 минут, `/runs/395…398` → 404) | кольцевой буфер последних `runsLimit` прогонов + `fromCache` |
| `runners.status = "active"` при простаивающих прогонах | состояние раннера выводим из свежести активности |
| `?limit=1` на `tasks` игнорируется, `page=2` → 0 | параметры пагинации в план не закладываем |

## R7. Порядок работ и тестируемость

**Решение**: сначала чистые функции (`lib/`), затем host-обвязка, затем клиент.
`node --test` в репозитории плагина; никаких тестов, требующих DSH или сети.

**Обоснование**: всё рискованное (длительности, буфер, гейт задач, `stale`,
`queue-stuck`, отсутствие секрета в снимке) выражается чистыми функциями, а
остальное проверяется в живом GUI по [quickstart.md](quickstart.md) — там, где
мок бесполезен.
