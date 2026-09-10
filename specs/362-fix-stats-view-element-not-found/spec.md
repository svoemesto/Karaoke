# Feature Specification: Исправить «Element not found» при открытии компонента «Статистика» в webvue3

**Feature Branch**: `362-fix-stats-view-element-not-found`

**Created**: 2026-09-10

**Status**: Draft

**Input**: User description: "Работа над задачей #79 в трекере OpenProject"

## OpenProject Tracking *(MANDATORY — see AGENTS.md § Issue-tracker OpenProject)*

### Идентификация

- **Issue ID**: `#79`
- **Title**: «Статистика, ошибка в консоли браузера»
- **Created in OpenProject**: 2026-09-10

### Workflow (NON-NEGOTIABLE при наличии Issue ID)

| Шаг | Команда | Когда | Кто |
|---|---|---|---|
| 1. **Claim** | `source .env.local-tracker && bash tools/tracker.sh claim-issue 79` | ПЕРЕД первой строкой кода спеки. | Agent |
| 2. **Pre-flight Knowledge** | `spec.md § Knowledge References` | Согласно Constitution Principle IX (см. ниже). | Agent |
| 3. **Work** | код, tests, knowledge updates | `/speckit.implement` | Agent |
| 4. **Add comment с отчётом** | `bash tools/tracker.sh add-comment 79 --file specs/362-fix-stats-view-element-not-found/report.md` | После merge. Файл `report.md` — REQUIRED. | Agent |
| 5. **Mark review** | `bash tools/tracker.sh mark-review 79` | После публикации комментария. | Agent |
| 6. **Close** | `bash tools/tracker.sh close-issue 79` | После ревью владельцем. | Agent или Owner |

### Проверки (validation)

- `tools/check-spec-issue-link.py` проверяет наличие секции и обязательных полей.

### Прецедент

Issue #79 открыт владельцем 2026-09-10 — в админ-компоненте «Статистика»
(`webvue3/src/views/StatsView.vue`) при переходе на вкладку в браузерной
консоли появляется ошибка `Uncaught (in promise) Error: Element not found`
из `index--dqHOftT.js:1442:2730 → at new Promise → at Proxy.render`.
Стек указывает на apexcharts (`vue3-apexcharts`), который отвечает за
графики KPI/TimeSeries/TypeChannelBreakdown/DetailBreakdown/GeoReferrers
на странице статистики.

Спека 174 (`specs/174-fix-stats-connection-leak/spec.md`) уже пыталась
исправить эту область (lazy load табов + 60s TTL), но **фикс не был
применён в коде**: `reloadAll()` до сих пор существует в `StatsView.vue`
(строки 543–583), `mounted()` до сих пор вызывает `reloadAll()`,
и кнопка «Обновить всё» в toolbar вызывает тот же `reloadAll()`.
В спеке 174 FR-001 обещал удалить `reloadAll()` и заменить на
`loadDataForActiveTab()`, но этого не произошло — это и есть
**корневая причина** бага #79.

## Knowledge References *(MANDATORY — see Constitution Principle IX)*

> **Прецедент**: 2026-09-09, spec #339 — агент пропустил Knowledge-first
> pre-flight и изобрёл форму кеша вместо паттернов из
> `knowledge/domains/caching/components/caching-patterns.md`.
> Без заполненной секции спека **НЕ ДОЛЖНА** переходить в
> `/speckit.plan`. См. `AGENTS.md` MUST #0, Constitution Principle IX.

### Pre-flight log

- **Дата pre-flight**: 2026-09-10
- **Grep-запросы** (5 попыток):
  1. `StatsView / статистика` →
     `knowledge/domains/stats/domain.md` (bounded context статистики),
     `knowledge/system/frontend/webvue3-views.md` (список views webvue3),
     `knowledge/system/frontend/webvue3-views-detailed.md` (детальная
     документация views, включая StatsView).
  2. `store stats / Vuex stats` → `knowledge/system/frontend/store-stats.md`
     (Vuex store статистики, state/getters/mutations/actions, hot paths).
  3. `EventType / dictionaries` → `knowledge/domains/stats/components/dictionaries.md`
     (EventType enum, магические коды аналитики).
  4. `lazy load / pagination` →
     `knowledge/adr/local-0004-lazy-eager-load-webvue3-pagination.md`
     (паттерн lazy load vs eager load в webvue3 таблицах).
  5. `apexcharts / Element not found` → **no relevant docs** (нет ADR
     или guideline про apexcharts / vue3-apexcharts. Это известная
     внешняя ошибка apexcharts `render() → Element not found`,
     документируется в их issue tracker).

### Knowledge files consulted

- [`knowledge/README.md`](../../knowledge/README.md)
  — общий обзор SSoT (Knowledge-first pre-flight обязателен).
- [`knowledge/domains/README.md`](../../knowledge/domains/README.md)
  — реестр Bounded Contexts; определил `stats` как целевой домен,
  а `system/frontend` как источник паттернов views/stores.
- [`knowledge/domains/stats/domain.md`](../../knowledge/domains/stats/domain.md)
  — зачем прочитан: bounded context «статистика», какие endpoints и
  компоненты есть на frontend, hot path `/api/stats/summary`,
  `/api/stats/timeseries`, `/api/stats/by-type` и т.д.
- [`knowledge/domains/stats/components/dictionaries.md`](../../knowledge/domains/stats/components/dictionaries.md)
  — зачем прочитан: EventType enum, магические коды аналитики;
  в `TimeSeriesChart.vue` есть `mode: 'all' | 'type' | 'detail'` —
  привязка к типам событий.
- [`knowledge/system/frontend/store-stats.md`](../../knowledge/system/frontend/store-stats.md)
  — зачем прочитан: Vuex store stats (`webvue3/src/components/Stats/store.js`,
  498 строк, самый длинный), state/getters/mutations/actions, hot paths,
  важный нюанс — `promisedXMLHttpRequest` не сериализует params для GET
  (устоявшийся квирк проекта).
- [`knowledge/system/frontend/webvue3-views-detailed.md`](../../knowledge/system/frontend/webvue3-views-detailed.md)
  — зачем прочитан: детальная документация views webvue3;
  StatsView — основной dashboard статистики, импортирует 11 компонентов.
- [`knowledge/adr/local-0004-lazy-eager-load-webvue3-pagination.md`](../../knowledge/adr/local-0004-lazy-eager-load-webvue3-pagination.md)
  — зачем прочитан: принятое решение — **lazy load по умолчанию** в
  таблицах webvue3, **eager load только для маленьких справочников**.
  Это обосновывает FR-001 — отказ от `reloadAll()` в пользу
  `loadDataForActiveTab()`.
- [`archive/docs/features/stats.md`](../../archive/docs/features/stats.md)
  — зачем прочитан: per-feature документ статистики (Pass 51 + 174),
  содержит **исходное описание `loadDataForActiveTab()`** из спеки 174 —
  этот метод был обещан, но **не реализован** в коде.

### Если ничего не нашлось (явный no-op)

> **Searched**: `apexcharts`, `vue3-apexcharts`, `Element not found`,
> `render error apexcharts`, `chart race condition`, `BTabs render` в
> `knowledge/` → **no relevant docs**. Это известная ошибка apexcharts
> `render() → "Element not found"` (issue #2489 в их GitHub) —
> внешняя зависимость, документировать в knowledge не нужно.
> Решение — клиентский фикс (lazy load + отказ от параллельных HTTP
> при mounted), не upstream-патч.


## User Scenarios & Testing *(mandatory)*

### User Story 1 — Администратор открывает «Статистику» без ошибок в консоли (Priority: P1)

Администратор сайта открывает пункт меню «Статистика» в админке
`webvue3`. Браузерная консоль **чистая** — нет ошибок
`Uncaught (in promise) Error: Element not found` ни на загрузке
страницы, ни при переключении между вкладками (KPI / Монетизация /
Динамика / Разбивки / География / Пользователи / Слушают / События).
В DevTools Network — **не более 2-3 параллельных HTTP-запросов** к
`/api/stats/*` при первом открытии (вместо 10-12 в текущем коде).

**Why this priority**: Это **исходный репорт бага** (Issue #79) —
ошибка в консоли блокирует комфортную работу с админкой. Без
исправления администратор не может анализировать статистику без
красных сообщений об ошибках, что подрывает доверие к системе.

**Independent Test**: Залогиниться в `webvue3`, перейти в меню
«Статистика». Открыть DevTools → Console (фильтр Errors) и DevTools →
Network (фильтр `/api/stats/`). На странице статистики:
- Console: 0 ошибок `Element not found`, 0 других unhandled promise
  rejection.
- Network: при первом `mounted()` видны только запросы активной
  вкладки (KPI — это 1-2 запроса: `summary` + `monetization summary`).
  При переключении на «Динамика» — подгружается `timeseries` (1
  запрос), на «Разбивки» — 3 параллельных (`by-type` + `channels` +
  `by-detail`) и т.д.
- Вкладки переключаются плавно (нет «прыжков» и повторных
  перезагрузок).

**Acceptance Scenarios**:

1. **Given** администратор открывает «Статистику» впервые за сессию,
   **When** компонент монтируется (`mounted()`),
   **Then** в DevTools Console — **0 ошибок**, в DevTools Network —
   **только 1-2 запроса** к `/api/stats/summary` и
   `/api/stats/monetization` (для активной вкладки KPI + Монетизация).
2. **Given** администратор переключается на вкладку «Динамика»,
   **When** срабатывает watcher на `activeTab`,
   **Then** в Network появляется **1 запрос** `/api/stats/timeseries`,
   в Console — **0 ошибок**.
3. **Given** администратор переключается на вкладку «Разбивки»,
   **When** watcher триггерит загрузку,
   **Then** в Network появляются **3 параллельных** запроса
   (`/api/stats/by-type`, `/api/stats/channels`, `/api/stats/by-detail`),
   в Console — **0 ошибок**.
4. **Given** администратор нажимает кнопку «Обновить» в toolbar,
   **When** срабатывает `onRefreshActiveTab()`,
   **Then** загружаются **только данные активной вкладки**
   (не все 10-12 параллельных запросов, как раньше), в Console —
   **0 ошибок**.

---

### User Story 2 — Backward-compat: пользовательские настройки target/days сохраняются (Priority: P2)

Администратор переключает БД (`local`/`remote`) или период
(7/30/90/365 дней). Состояние сохраняется в Vuex store (как сейчас),
но вместо полного `reloadAll()` подгружаются **только те endpoint'ы**,
которые зависят от `target`/`days` (а не все 10-12 параллельных).
Другие endpoint'ы (`statsBySong`, `topUsers`, `topListened`, `webevents`,
`monetization`) зависят только от `target` — и подгружаются тоже
только при первом открытии соответствующей вкладки.

**Why this priority**: Сейчас переключение «БД» вызывает
`reloadAll()` → 11 параллельных HTTP. Это и есть **триггер** бага #79
(11 параллельных fetch → store обновляется до завершения рендера
apexcharts → `Element not found`). Без фикса этого сценария
бага не избежать.

**Independent Test**: Открыть «Статистику», переключить БД на
«Сервер». В Network — только endpoint'ы активной вкладки +
endpoint'ы с фильтром `target`/`days`. Не должно быть 11 запросов.

**Acceptance Scenarios**:

1. **Given** администратор на вкладке «KPI» переключает БД,
   **When** срабатывает `onTargetChange()`,
   **Then** в Network — только 2 запроса (`summary` + `monetization
   summary`), оба с `target=remote`. Никаких других запросов
   (`by-type`, `timeseries`, `top-users`, `stats-by-song`, и т.д.)
   не происходит до момента открытия соответствующих вкладок.
2. **Given** администратор на вкладке «Динамика» переключает период
   на 90 дней, **When** срабатывает `onDaysChange()`,
   **Then** в Network — только 3 запроса с `days=90`:
   `summary`, `timeseries`, `by-detail` (или `by-type`).
   `by-song`, `top-users`, `webevents` не запрашиваются.
3. **Given** администратор на вкладке «Слушают» переключает БД,
   **When** срабатывает `onTargetChange()`,
   **Then** в Network — только `top-listened` (с `target=remote`).
   `summary`, `monetization`, и т.д. не запрашиваются.

---

### User Story 3 — Кэш не блокирует работу (Priority: P2)

В спеке 174 обещано добавить 60-секундный TTL-кеш на фронте, чтобы
повторное открытие таба (F5 / уход-возврат) не шло новым HTTP.
Это поведение должно сохраниться: в течение 60 сек после загрузки
таба переключение на него-же или возврат **не должно** генерировать
новых запросов, и уж тем более — не должно триггерить race
«Element not found».

**Why this priority**: TTL-кеш — это **защита от повторных race**:
если пользователь быстро кликает по табам или возвращается на
страницу, без кеша каждый клик = новый HTTP = новый шанс на race.

**Independent Test**: Открыть «Динамику» → подождать загрузки →
перейти на «KPI» → вернуться на «Динамику». В Network —
**0 новых запросов** (кеш жив, 60s TTL).

**Acceptance Scenarios**:

1. **Given** вкладка «Динамика» загружена (TTL активен),
   **When** администратор переключается на «KPI» и возвращается
   на «Динамику` в течение 60 сек,
   **Then** в Network — **0 новых запросов** `timeseries`,
   график отрисовывается сразу из кеша.
2. **Given** вкладка «Разбивки» загружена, **When** администратор
   переходит на другую страницу админки и возвращается на
   «Статистику` в течение 60 сек,
   **Then** в Network — **0 запросов** `by-type/channels/by-detail`
   (TTL не истёк), графики показываются из кеша.
3. **Given** TTL истёк (>60 сек), **When** администратор возвращается
   на вкладку «Динамика`,
   **Then** в Network — **1 запрос** `timeseries`,
   в Console — **0 ошибок** (lazy load + отсутствие race).

---

### Edge Cases

- **Что происходит при ошибке БД (503 / `too many clients`)**:
  текущая спека 174 уже ввела `<DbOverloadBanner>` и `503
  stats.unavailable` — **сохраняем**. Lazy load дополнительно снижает
  шанс 503 (вместо 11 параллельных запросов — 1-3).
- **Что происходит при медленной БД (slow query > 30 сек)**:
  пользователь увидит `<BSpinner>` в течение этого времени. Переключение
  на другой таб отменяет loading-флаг для текущего (через watcher +
  reset state), но **не отменяет** HTTP-запрос (xmlhttprequest уже
  в полёте — это by design, см. архитектуру в спеке 174). Backpressure
  защита — TTL на фронте.
- **Что происходит при F5 во время загрузки**:
  весь `StatsView` размонтируется и монтируется заново. `reloadAll()`
  не успевает отправить HTTP → apexcharts тоже размонтируется. **Race
  не возникает** при таком сценарии (он возникает именно при
  переключении табов во время загрузки — lazy load лечит).
- **Что если пользователь быстро кликает по табам (КПИ → Динамика →
  КПИ → Динамика за <1 сек)**:
  без TTL = 4 HTTP-запроса; с TTL = 1-2 HTTP (КПИ и Динамика
  кешируются после первой загрузки).
- **Что если открыть «Статистику» на 2 вкладках браузера одновременно**:
  каждый инстанс имеет свой Vuex store (store = singleton в пределах
  tab/iframe). Race на apexcharts не возникает — каждый инстанс
  рендерит свой DOM.

## Requirements *(mandatory)*

### Functional Requirements

#### A. Отказ от `reloadAll()` в пользу lazy load табов

- **FR-001**: `StatsView.vue:mounted()` MUST вызывать
  **`loadDataForActiveTab()`** вместо `reloadAll()`. При первом
  открытии загружаются **только данные активной вкладки**
  (по умолчанию `activeTab=0` → KPI: `summary` + `monetization summary`).
  **Обоснование**: см. спеку 174 (FR-001) и ADR
  `local-0004-lazy-eager-load-webvue3-pagination.md`.
- **FR-002**: `StatsView.vue` MUST иметь watcher на `activeTab` —
  при смене вкладки вызывать `loadDataForActiveTab()` для новой
  активной вкладки, **если** с момента последней загрузки этой
  вкладки прошло > `STATS_FRONT_TTL_MS` (60 сек, см. FR-006).
- **FR-003**: Метод `reloadAll()` MUST быть **удалён** из
  `StatsView.vue` (старый код 543-583). Кнопка «Обновить всё»
  в toolbar (`StatsView.vue:20`) MUST быть **заменена** на
  «Обновить», вызывающую `loadDataForActiveTab()` для текущей
  вкладки. Это удаляет footgun из 11 параллельных HTTP-запросов.
- **FR-004**: Метод `loadDataForActiveTab(activeTabIndex)` MUST
  загружать **только** endpoint'ы, относящиеся к указанной вкладке.
  Маппинг «вкладка → endpoint'ы»:

  | `activeTab` | Вкладка | Endpoint'ы |
  |---|---|---|
  | 0 | KPI | `/api/stats/summary` |
  | 1 | Монетизация | `/api/stats/monetization` |
  | 2 | Динамика | `/api/stats/timeseries` (с `mode=current`) |
  | 3 | Разбивки | `/api/stats/by-type`, `/api/stats/channels`, `/api/stats/by-detail` |
  | 4 | География | `/api/stats/countries`, `/api/stats/referrers` |
  | 5 | Пользователи | `/api/stats/top-users` (с `page` и `pageSize`) |
  | 6 | Слушают | `/api/stats/top-listened` (с `page` и `pageSize`) |
  | 7 | События | `/api/stats/by-song` (с `page` и `pageSize`), `/api/webevents` (с `page` и `pageSize`) |

#### B. Сериализация и отмена загрузок для предотвращения race

- **FR-005**: `loadDataForActiveTab(activeTabIndex)` MUST **сбрасывать
  loading-флаги** (`setStatsXxxIsLoading(false)`) для вкладки, с которой
  уходит пользователь, чтобы spinner не «висел» вечно при быстром
  переключении табов. Это решает race: «переключился на «Динамику»,
  потом на «КПИ» → «Динамика» загружается дольше всех → spinner
  «Динамики» блокирует UI при возврате».
- **FR-006**: Хранилище `lastLoadedAt[tabIndex]` (Map в data() или
  store) MUST использоваться для 60-секундного TTL-кеша на фронте.
  При попытке загрузки таба проверяется `Date.now() - lastLoadedAt[tab]`
  — если < `STATS_FRONT_TTL_MS` (= 60_000), HTTP НЕ отправляется,
  данные берутся из store. Это и есть защита от race при
  быстром переключении (см. спеку 174, US2).
- **FR-007**: При вызове `onTargetChange()` (переключение БД
  `local`/`remote`) MUST обновляться только **данные активной
  вкладки + данные с фильтром `days`** (на момент открытия
  «Динамики», «Разбивок», «KPI»). Endpoint'ы без фильтра `days`
  и `target` (`statsBySong`, `topUsers`, `topListened`, `webevents`,
  `monetization summary/top-songs`) — обновляются **только при
  открытии соответствующей вкладки** после смены `target`.
- **FR-008**: При вызове `onDaysChange()` (смена периода 7/30/90/365)
  MUST обновляться только endpoint'ы с фильтром `days`:
  `summary`, `timeseries`, `by-type`, `by-detail`. Остальные — не
  трогаем до переключения на соответствующую вкладку.

#### C. Гарантии от `Element not found`

- **FR-009**: ApexCharts `<apexchart>` MUST получать `series` и
  `options` **после того, как** его DOM-элемент закрепился в
  дереве. Это решается автоматически благодаря `v-if`/`v-else-if`
  гардам в `TimeSeriesChart.vue`, `DetailBreakdown.vue`,
  `TypeChannelBreakdown.vue`, `GeoReferrers.vue` — эти guard'ы уже
  есть, баг был в том, что **state store менялся слишком быстро**
  (11 параллельных HTTP), не давая apexcharts завершить рендер.
  Lazy load по FR-001..FR-004 **устраняет** этот race.
- **FR-010**: При `selectedDetailType = ''` (сброс фильтра в
  `DetailBreakdown`) MUST вызываться `loadDataForActiveTab(3)` —
  чтобы `by-detail` перезагрузился с актуальными данными. Сейчас
  фильтр работает in-memory, без рефреша — оставляем как есть,
  добавляем только в список триггеров lazy load.
- **FR-011**: `promisedXMLHttpRequest` (используемый в
  `webvue3/src/components/Stats/store.js`) — **не** отменяет
  in-flight HTTP-запрос при размонтировании компонента. Это
  by design проекта (см. ADR local-0004 — нет AbortController).
  FR-011 говорит: **не пытаться добавлять AbortController** в
  рамках этой спеки — это отдельная задача. Race в #79 лечится
  lazy load (FR-001..FR-008), а не отменой запросов.

#### D. Наблюдаемость и observability

- **FR-012**: При первом `mounted()` MUST логироваться
  `console.debug('[Stats] mounted — lazy loading active tab',
  { tab: this.activeTab, ts: Date.now() })` — для ручной
  диагностики в DevTools.
- **FR-013**: При смене `activeTab` MUST логироваться
  `console.debug('[Stats] tab switched — lazy load',
  { from, to, ttlRemaining: STATS_FRONT_TTL_MS - (Date.now() - lastLoadedAt[to]) })`
  — чтобы видеть в DevTools, работает ли TTL.
- **FR-014**: В `reproduce.md` (документ для владельца) MUST быть
  шаги для ручного воспроизведения бага #79 и подтверждения фикса:
  открыть «Статистику» → DevTools Console → 0 ошибок. Сравнить
  «до» (reloadAll → 11 запросов → ошибка) и «после»
  (loadDataForActiveTab → 2 запроса → 0 ошибок).

### Key Entities

- **`activeTab` (Number, 0-7)**: индекс текущей вкладки в `BTabs`.
  Хранится в `data()`. По умолчанию `0` (KPI).
- **`lastLoadedAt` (Map<Number, Number>)**: timestamp последней
  успешной загрузки каждой вкладки. Хранится в `data()` или
  в Vuex store (для persistence между mount/unmount).
- **`STATS_FRONT_TTL_MS` (Number, default 60_000)**: TTL фронтового
  кеша в миллисекундах. Если `Date.now() - lastLoadedAt[tab] <
  STATS_FRONT_TTL_MS` → повторная загрузка НЕ выполняется.
- **`tabEndpoints` (Object<Number, String[]>)**: маппинг
  «индекс вкладки → массив endpoint'ов». См. таблицу в FR-004.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001** (главный критерий бага #79): При открытии
  «Статистики» в `webvue3` **залогиненным** администратором —
  в DevTools Console **0 ошибок** `Element not found` (и любых
  других unhandled promise rejection) на момент `mounted()` и
  в течение 10 секунд после. До фикса — **≥1 ошибки**
  `Element not found` сразу после `mounted()`.
- **SC-002**: При первом `mounted()` в DevTools Network —
  **не более 2 HTTP-запросов** к `/api/stats/*` (для вкладки KPI:
  `summary` + `monetization`). До фикса — **10-12 параллельных**
  HTTP-запросов.
- **SC-003**: При переключении на любую вкладку (`activeTab`)
  в DevTools Network — **ровно столько запросов**, сколько
  endpoint'ов привязано к этой вкладке (см. таблицу FR-004),
  **не больше**. Например, «География» → 2 запроса
  (`countries` + `referrers`); «Разбивки» → 3 запроса.
- **SC-004**: При повторном открытии вкладки в течение 60 сек
  после её загрузки — **0 новых HTTP-запросов** в Network
  (TTL-кеш работает). До фикса — каждый клик по табу
  генерировал новый запрос (TTL не реализован).
- **SC-005**: Кнопка «Обновить всё» в toolbar MUST быть **удалена**
  или переименована в «Обновить» с lazy-семантикой (только активная
  вкладка). В исходном коде `StatsView.vue:20` сейчас есть кнопка
  «Обновить всё» → клик → 11 параллельных HTTP → race.
- **SC-006**: Backward-compat: ручное переключение БД `local`/`remote`
  и периода `7/30/90/365 дней` продолжает работать (с `STATS_FRONT_TTL`
  в виде поведения), но без 11 параллельных запросов при каждом
  переключении. Регрессионный тест: открыть «Динамику», переключить
  на 90 дней → 1 запрос `timeseries?days=90`, остальные табы — без
  перезагрузки.

## Clarifications

### Session 2026-09-10

- Q: Удалять ли `reloadAll()` сразу или сначала ввести
  `loadDataForActiveTab()` параллельно? → A: **Сразу удалять**
  `reloadAll()` (FR-003). Сохранение двух методов = footgun:
  разработчик может случайно вызвать старый. Спека 174 обещала
  удаление, но не сделала — мы выполняем это обещание.
- Q: Хранить `lastLoadedAt` в `data()` или в Vuex store? → A:
  **В `data()`** компонента `StatsView.vue` — `lastLoadedAt`
  это UI-state, привязанный к жизненному циклу view. Vuex
  store не нужен (нет persistence между сессиями).
- Q: Что делать с уже закэшированными данными в store при
  переключении БД? → A: **Очищать** данные активной вкладки
  (`setStatsXxx(null/[]/0)`) при `onTargetChange()` — чтобы
  не показывать «remote» данные, пока грузятся новые
  (это и есть «честный» UX).
- Q: Нужно ли чинить `MonetizationPanel` (он тоже в reloadAll)?
  → A: **Да**, в рамках FR-001 — при первом монтировании
  `activeTab=0` (KPI), `MonetizationPanel` НЕ отрисовывается
  (вкладка 1 не активна). Его данные подгружаются только при
  переключении на вкладку 1.

## Assumptions

- **Lazy load табов — устоявшийся паттерн webvue3** (см. ADR
  `local-0004-lazy-eager-load-webvue3-pagination.md`):
  таблицы загружаются по требованию, не все сразу. Аналогичный
  паттерн применим к графикам.
- **`promisedXMLHttpRequest` НЕ отменяется при размонтировании** —
  by design проекта (нет AbortController в lib/utils). Race в #79
  лечится **lazy load** (FR-001..FR-008), а не отменой запросов.
  Это допустимо: типичный HTTP-запрос к `/api/stats/*` укладывается
  в 100-500 ms, после получения store обновляется — если
  компонент уже размонтирован, store обновится «в пустоту»,
  но при следующем монтировании данные уже будут в store
  (благодаря singleton Vuex store).
- **ApexCharts `Element not found`** — известная проблема
  `vue3-apexcharts` при попытке render() в DOM-элемент,
  который ещё не закрепился в дереве (или был удалён
  при ререндере). Решение — клиентское (lazy load + не
  дёргать store 11 раз параллельно), не upstream-патч.
  Upstream issue: `apexcharts/apexcharts.js#2489`.
- **`STATS_FRONT_TTL_MS = 60_000` (60 сек)** — соответствует
  спецификации 174, FR-005. Не меняем в этой спеке, чтобы
  избежать расхождений с TTL backend'а (`StatsCache`).
- **Vuex store сохраняется между mount/unmount** (singleton
  в пределах `webvue3` app). Поэтому при возврате на
  «Статистику» данные из store доступны даже без HTTP —
  достаточно проверить `lastLoadedAt` для TTL.
- **Кнопка «Обновить всё» удаляется**, не переименовывается —
  UX-семантика «обновить всё» = «11 параллельных HTTP» = race.
  Новая кнопка «Обновить» = `loadDataForActiveTab(currentTab)` —
  честная, lazy.
- **LiveDocs**: фича влияет на архитектуру (новый lazy load
  паттерн в StatsView, изменение UX кнопки). В этом же PR обновить:
  - `knowledge/system/frontend/store-stats.md` (упоминание
    lazy load + TTL на фронте)
  - `knowledge/adr/local-0004-lazy-eager-load-webvue3-pagination.md`
    (добавить ссылку на эту спеку как пример применения
    паттерна к графикам, не только таблицам)
  - `archive/docs/features/stats.md` (FR-001 + FR-002 уже описаны
    как «после фикса 174» — обновить до «применено в 362»).
- **Constitution Check** (см. Constitution § Governance п.4):
  - **Principle II (Сырой JDBC + дифф по хэшам)**: фича чисто
    фронтовая, SQL не затрагивается.
  - **Principle I (Self-contained)**: фича не добавляет внешних
    зависимостей (apexcharts уже есть).
  - **Principle V (Двух-фронтенд)**: изменения касаются **только**
    `webvue3` (admin). `karaoke-public` (public) не затронут.
  - **Principle VIII (Секреты)**: никаких новых секретов.
  - **Principle IX (Knowledge-first)**: ✅ pre-flight выполнен в этой
    спеке (см. § Knowledge References).
