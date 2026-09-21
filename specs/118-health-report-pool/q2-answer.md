# Q2 — Исследование бейджа KaraokeProcess и UI админки «Песни»

> **Тикет**: [wayfinder:research] #121 «Исследовать бейдж KaraokeProcess и UI админки Песни»
> **Дата**: 2026-09-15
> **Статус**: research complete (read-only)
> **Блокирует**: Q4 (бейдж пула HealthReport)

## TL;DR

- **Фреймворк админки**: **Vue 3.5.21 + Vuex 4.1.0 + Bootstrap-vue-next 0.40.5 + EventSourcePolyfill (SSE)**.
  Pinia в проекте **не используется** — везде Vuex 4.
- **Компонент «Песни»** (`/songs`): это **НЕ** место с бейджем.
  - View: `webvue3/src/views/SongsView.vue` — обёртка 41 строка.
  - Контент: `webvue3/src/components/Songs/SongsTable.vue` (2358 строк) — фильтры,
    bulk-action кнопки, без процесса-бейджа.
  - В SongsTable **нет** бейджа счётчика процессов и **нет** кнопки Старт/Стоп воркера.
- **Бейдж KaraokeProcess** живёт в общей шапке приложения в
  **`webvue3/src/components/Common/ProcessWorker.vue`** (Common, не Songs).
  - Текстовый бейдж `text-count-waiting` — правый нижний угол круглой кнопки
    Старт/Стоп воркера.
  - Получает число через Vuex-getter `getCountWaiting` из модуля `processes`
    (`webvue3/src/components/Processes/store.js`).
  - Обновляется через **SSE-событие `PROCESS_COUNT_WAITING`** (см. `App.vue:362-365`)
    с fallback-poll `/api/processes/countwaiting` в `mounted()`.
- **Кнопка Старт/Стоп воркера**: **одна** в `App.vue:106` (`<ProcessWorker :included-thread-id="[0]" />`)
  для lane `threadId=0` (HEAVY_RENDER). Используется в `header-right` шапки
  на всех страницах админки, включая `/songs`. Вторая копия с
  `:excluded-thread-id="[0]"` — только прогресс-бар, без кнопки.
- **Конвенция синего цвета**: в проекте используются три варианта.
  - **`#007bff`** — текущий process-progress-bar в `ProcessWorker.vue:124`
    (старый Bootstrap 4 primary blue).
  - **`var(--bs-primary, #0d6efd)`** — Bootstrap 5 primary blue (через CSS-переменную),
    примеры: `ProcessBulkReportModal.vue:187,189`, `AuthorsTable.vue:971`.
  - **`#2c6bd5`** — синий бейдж `chat-notify-badge` в `ChatNotifyButton.vue:69`.
    Это ближайший **визуальный образец** для второго бейджа пула:
    маленький, насыщенный синий, белый текст, скруглённый, абсолютно-позиционированный
    поверх `.btn-round-double`.
- **Подписка на обновления счётчика**: использовать **тот же SSE-канал**
  `PROCESS_COUNT_WAITING` + Vuex-action `setCountWaiting` для счётчика пула
  HealthReport — это **паттерн, который надо переиспользовать**.

## 1. Компонент «Песни» — где живёт

### 1.1. View-слой

**`webvue3/src/views/SongsView.vue`** (41 строка) — обёртка для `SongsTable`.

```vue
<template>
  <div class="songstable">
    <SongsTable />
  </div>
</template>

<script>
import SongsTable from '../components/Songs/SongsTable.vue'

export default {
  name: 'SongView',
  components: { SongsTable },
}
</script>
```

Роут: `webvue3/src/router/index.js:36-39` — `path: '/songs' → SongsView`.

### 1.2. Таблица песен

**`webvue3/src/components/Songs/SongsTable.vue`** (2358 строк).

Содержит:
- `b-table` со списком песен + кастомные колонки (player, demo, assignment-badge,
  tg-publish-badge).
- `songs-bv-table-footer` — bulk-action кнопки `btn-round-long-double`/`btn-round-double`
  (Smart Copy, фильтр, найти тексты, создать караоке/DEMUCS2/DEMUCS5/ForcedAlign/Symlinks/Sheetsage,
  и пр.). Строки 432-541.
- Кнопок Старт/Стоп воркера процессов в этом файле **нет**.

### 1.3. Что важно: бейдж KaraokeProcess — НЕ в SongsTable

`grep -n "processCount\|countWaiting\|ProcessWorker\|process-worker"` по всему
`Songs/` — **нет вхождений**.

Бейдж живёт **в шапке приложения** (`App.vue:104-110`), а не в компоненте «Песни».
То есть постановка задачи в тикете «правый нижний угол кнопки Старт/Стоп в админке,
компонент «Песни»» интерпретируется так: бейдж рядом с кнопкой Старт/Стоп,
которая видна **на странице** `/songs` как часть общего хедера админки.

## 2. Бейдж KaraokeProcess — где находится

**`webvue3/src/components/Common/ProcessWorker.vue`** — общий компонент в шапке.
Используется на странице `ProcessesView` (см. `archive/docs/features/async-process-queue.md`)
**и** в `HealthReport` (см. KDoc).

### 2.1. Template (строки 1-31)

```vue
<template>
  <div class="process_worker">
    <div class="wrapper">
      <div class="process-text" v-text="processName" />
      <div class="wrapper-bar">
        <div
          class="process-progress-bar"
          role="progressbar"
          :style="styleProgressBar"
          v-text="processPercentage"
        />
      </div>
    </div>
    <div v-show="!hideButton" class="button-with-text-count-waiting" @dblclick="forceStopClick">
      <button
        class="btn-round-double"
        :disabled="disabled"
        @click.left="clickStartStopWorkerButton"
      >
        <img v-if="!isWork" alt="start" class="icon-40" src="../../assets/svg/icon_play.svg" />
        <img v-else alt="stop" alt="stop" class="icon-40" src="../../assets/svg/icon_stop.svg" />
      </button>
      <div class="text-count-waiting" v-text="countWaiting" />     <!-- ЭТО БЕЙДЖ -->
    </div>
    <custom-confirm v-if="isConfirmVisible" :params="confirmParams" @close="isConfirmVisible = false" />
  </div>
</template>
```

### 2.2. Скрипт — `countWaiting` геттер (строки 50-99)

```js
export default {
  name: 'ProcessWorker',
  components: { CustomConfirm },
  props: {
    hideButton: { type: Boolean, required: false, default: false },
    includedThreadId: { type: Array, required: false, default: () => [] },
    excludedThreadId: { type: Array, required: false, default: () => [] },
  },
  data() { return { isConfirmVisible: false, confirmParams: undefined } },
  computed: {
    process() { return this.$store.getters.getWorkingProcessForThreads(...) },
    isWork() { return this.$store.getters.getProcessIsWorking },
    stopAfterThreadIsDone() { return this.$store.getters.getProcessWillStopAfterThreadIsDone },
    countWaiting() { return this.$store.getters.getCountWaiting },    // ← ЭТО
    disabled() { return this.isWork && this.stopAfterThreadIsDone },
    processName() { ... },
    processPercentage() { return this.process ? `${this.process.percentage}%` : '' },
    styleProgressBar() {
      return {
        fontSize: 'small',
        width: this.processPercentage,
        backgroundImage: 'linear-gradient(45deg, hsla(0,0%,100%,.15) 25%, transparent 0, transparent 50%, hsla(0,0%,100%,.15) 0, hsla(0,0%,100%,.15) 75%, transparent 0, transparent)',
        backgroundSize: '1rem 1rem',
        display: 'flex', flexDirection: 'column', overflow: 'hidden',
        color: '#fff', justifyContent: 'center', textAlign: 'center',
        whiteSpace: 'nowrap',
        backgroundColor: '#007bff',                                  // ← синий progress-bar
        transition: 'width .6s ease',
        animation: '1s linear infinite progress-bar-stripes',
      }
    },
  },
  mounted() {
    this.checkUpdateProcessesWorker()
    this.checkCountWaiting()                                         // ← initial poll
  },
  methods: {
    clickStartStopWorkerButton() { this.$store.dispatch('startStopProcessWorker') },
    forceStopClick() { ... },
    doForceStop() { this.$store.dispatch('forceStopProcessWorker') },
    checkUpdateProcessesWorker() {
      this.$store.dispatch('getProcessesWorkerStatusPromise').then((data) => {
        let status = JSON.parse(data)
        let isWork = status.isWork
        let stopAfterThreadIsDone = status.stopAfterThreadIsDone
        this.$store.dispatch('setProcessIsWorking', isWork)
        this.$store.dispatch('setProcessWillStopAfterThreadIsDone', stopAfterThreadIsDone)
      })
    },
    checkCountWaiting() {
      this.$store.dispatch('getProcessesCountWaitingPromise').then((data) => {
        this.$store.dispatch('setCountWaiting', { countWaiting: data })
      })
    },
    truncateString(name, maxSymbols) { ... },
  },
}
```

### 2.3. Стили — собственно бейдж (строки 233-247)

```css
.button-with-text-count-waiting {
  position: relative;
}
.text-count-waiting {
  font-size: x-small;
  color: white;
  position: absolute;
  pointer-events: none;
  top: 100%;          /* якорь — правый НИЖНИЙ угол кнопки (см. тикет) */
  left: 100%;
  transform: translate(-50%, -50%);
  padding: 0 4px;
  border-radius: 5px;
  background-color: gray;       /* ← серый (НЕ синий). Это «нейтральный» цвет. */
}
```

**Важно**: бейдж `text-count-waiting` — **серый**, не синий. Цвет фона — `gray`,
без какого-либо blue-token. Это **конвенция** для KaraokeProcess badge: нейтральный
счётчик, чтобы не путать с process-progress-bar (синий `#007bff`).

### 2.4. Стили кнопки (строки 200-218)

```css
.btn-round-double {
  border: solid 1px black;
  border-radius: 6px;
  width: 50px;
  height: 50px;
  margin-left: 2px;
  background-color: antiquewhite;
}
.btn-round-double:hover  { background-color: lightpink; }
.btn-round-double:focus  { background-color: darksalmon; }
.btn-round-double[disabled] {
  background-color: lightgray;
  pointer-events: none;
}
.icon-40 {
  width: 40px; height: 40px; margin-left: -1px;
}
```

## 3. Store — откуда берётся `processCount` (на самом деле `countWaiting`)

**`webvue3/src/components/Processes/store.js`** — модуль `processes` Vuex-стора.

### 3.1. Регистрация в корневом сторе

**`webvue3/src/store/index.js`** (99 строк) — Vuex 4, registration:

```js
import { createStore } from 'vuex'
import processes from '../components/Processes/store'
// ...
export default createStore({
  modules: { processes, /* + ещё ~30 модулей */ },
})
```

Подтверждение фреймворка (`webvue3/package.json`):

```json
"vue": "^3.5.21",
"vuex": "^4.1.0",
"bootstrap-vue-next": "^0.40.5",
"event-source-polyfill": "^1.0.31"
```

### 3.2. State/getter/mutation/action (store.js)

**State (строка 45):**
```js
countWaiting: '...',    // ← «processCount» в терминах тикета. Изначально строка '...'.
```

**Getter (строки 93-95):**
```js
getCountWaiting(state) { return state.countWaiting },
```

**Mutation (строки 196-198):**
```js
setCountWaiting(state, userEventData) {
  state.countWaiting = userEventData.countWaiting
},
```

**Action (строки 317-319):**
```js
setCountWaiting(ctx, userEventData) { ctx.commit('setCountWaiting', userEventData) },
```

**Initial-poll action (строки 310-313):**
```js
getProcessesCountWaitingPromise: () => {
  let request = { method: 'POST', url: '/api/processes/countwaiting' }
  return promisedXMLHttpRequest(request)
},
```

### 3.3. Прочие процессные геттеры/мутации (для полноты)

`getProcessIsWorking`, `getProcessWillStopAfterThreadIsDone`, `getWorkingProcessForThreads(included, excluded)`,
мутации `setProcessIsWorking`, `setProcessWillStopAfterThreadIsDone`, `updateProcessWorkerStateByUserEvent`
(строки 67-211) — всё живёт в этом же модуле.

## 4. Подписка на обновления — паттерн, который надо переиспользовать

**`webvue3/src/App.vue`** — корневой компонент админ-SPA, через который идут SSE-события.

### 4.1. SSE-подключение (строки 278-307)

```js
connectSse(create) {
  const msgServer = new EventSourcePolyfill(
    `/api/subscribe?tabId=${encodeURIComponent(getTabId())}`,
    { heartbeatTimeout: 30000 },
  )
  msgServer.addEventListener('user', (event) => {
    this.userEvent(JSON.parse(event.data).payload, create)
  }, false)
  msgServer.onerror = () => {
    if (msgServer.readyState === msgServer.CLOSED) {
      this.scheduleSseReconnect(create)
    }
  }
  this.msgServer = msgServer
},
scheduleSseReconnect(create) {
  if (this.sseReconnectTimer) return
  this.sseReconnectTimer = setTimeout(() => {
    this.sseReconnectTimer = null
    this.connectSse(create)
  }, SSE_RECONNECT_DELAY_MS)        // = 4000 ms
},
```

Reconnect-логика: задержка 4000 мс, экспоненциальная **не используется**, retry фиксированный.

### 4.2. Диспетчер SSE-событий (строки 358-365 — релевантный фрагмент)

```js
case 'PROCESS_WORKER_STATE': {
  this.updateProcessWorkerStateByUserEvent(userEvent.data)
  break
}
case 'PROCESS_COUNT_WAITING': {
  this.setCountWaiting(userEvent.data)
  break
}
```

### 4.3. Метод-обёртка (строки 437-439)

```js
setCountWaiting(userEventData) {
  this.$store.dispatch('setCountWaiting', userEventData)
},
```

### 4.4. Паттерн для бейджа пула (что повторить)

| Шаг | Текущий `processCount` | Шаблон для `healthReportPool` |
|---|---|---|
| 1. Источник | Backend POST `/api/processes/countwaiting` | Backend POST `/api/healthreportpool/size` (новый, в Q4) |
| 2. State | `state.countWaiting` в `processes` module | Новый `state.poolSize` в `healthReport` module (или новый module `healthReportPool`) |
| 3. Getter | `getCountWaiting(state)` | `getHealthReportPoolSize(state)` |
| 4. Mutation | `setCountWaiting(state, payload)` | `setHealthReportPoolSize(state, payload)` |
| 5. Action dispatch | `dispatch('setCountWaiting', payload)` | `dispatch('setHealthReportPoolSize', payload)` |
| 6. SSE-канал | `PROCESS_COUNT_WAITING` (см. `App.vue:362`) | Новое SSE-событие (см. backend-домен `sse`, тип будет объявлен в Q4) |
| 7. Initial poll | `mounted() → checkCountWaiting() → getProcessesCountWaitingPromise` | Аналогично |
| 8. UI | `<ProcessWorker />` → `<div class="text-count-waiting">{{ countWaiting }}</div>` (серый, правый нижний угол кнопки) | Новый бейдж пула: синий, **правый верхний** угол той же кнопки. См. образец `ChatNotifyButton.chat-notify-badge` (`#2c6bd5`). |

### 4.5. Прочие паттерны polling в `App.vue` (для справки)

- `CHAT_UNREAD_POLL_INTERVAL_MS = 20000` — отдельный `setInterval`-опрос для chat (нет SSE-канала).
- `SONGEDITOR_SUBMITTED_POLL_INTERVAL_MS = 20000` — аналогично для editor.
- `AUTHORS_NEW_ALBUMS_POLL_INTERVAL_MS = 20000` — для «новые альбомы» в Authors.

Эти 3 используют **отдельный опрос**, потому что данные идут не из karaoke-app,
а из karaoke-web (который не шлёт SSE). Для `healthReportPool` уместен SSE
(источник — karaoke-app), так что polling-fallback не нужен — достаточно
**следовать `PROCESS_COUNT_WAITING`-паттерну**.

## 5. Цветовая конвенция в админке

Проект **НЕ использует** Tailwind. Используется Bootstrap (через `bootstrap-vue-next`).
Theme tokens Bootstrap'а доступны через CSS-переменные `--bs-*`.

### 5.1. Цвета, найденные в админке (`webvue3/src/**`)

| Цвет | Что | Где |
|---|---|---|
| `#007bff` | **Bootstrap 4 primary** — текущий process-progress-bar | `ProcessWorker.vue:124` |
| `#0d6efd` (= `var(--bs-primary)`) | **Bootstrap 5 primary** — рекомендуемый синий | `ProcessBulkReportModal.vue:187,189`; `AuthorsTable.vue:971`; `SongEdit.vue:5829` |
| `#2c6bd5` | **Насыщенный синий** — `chat-notify-badge` | `ChatNotifyButton.vue:69,84,87,92` |
| `#28a745` | success | `SongEdit.vue:6411` |
| `#fd7e14` | warning | `SongEdit.vue:6417` |
| `#d02c3a` | danger | `App.vue:783,800,817`; `ChatPanel.vue:441` |
| `#4aae9b` | teal | `App.vue:830`; `ChatPanel.vue:518,566` |
| `#6c757d` | secondary | `SongEdit.vue:6414` |
| `gray` | **нейтральный** (processCount badge) | `ProcessWorker.vue:246` |
| `antiquewhite` / `lightpink` / `darksalmon` / `lightgray` | btn-round-double states | `ProcessWorker.vue:206-215` и др. |

### 5.2. Что выбрать для бейджа пула HealthReport

- **Текущий `processCount`-бейдж** = `gray` (`ProcessWorker.vue:246`) — нейтральный,
  «не-брендовый» счётчик. Это **де-факто конвенция** для цифрового счётчика
  на шапке.
- **Синий** в админке: три валидных shade-а. Для нового бейджа пула:
  - **`var(--bs-primary, #0d6efd)`** — Bootstrap-primary, **рекомендуемый**
    по конвенции BootstrapVueNext. Совпадает с уже используемым
    `var(--bs-primary, #0d6efd)` в `ProcessBulkReportModal.vue`.
  - **`#2c6bd5`** — насыщеннее, использован в `ChatNotifyButton` для аналогичного
    use-case (маленький синий бейдж поверх круглой кнопки). Если хочется визуально
    отличать pool от process badge — этот shade лучше.
  - `#007bff` — НЕ рекомендуется: используется только для process-progress-bar
    и конфликтует визуально.

### 5.3. Образец layout (взять за основу из `ChatNotifyButton.vue`)

```css
.chat-notify-badge {
  position: absolute;
  top: -4px;          /* правый ВЕРХНИЙ угол */
  right: -4px;
  background-color: #2c6bd5;
  color: #fff;
  border-radius: 10px;
  min-width: 18px;
  height: 18px;
  line-height: 18px;
  text-align: center;
  font-size: 11px;
  padding: 0 4px;
  border: 1px solid #fff;
}
```

Это **прямой образец** для второго бейджа пула HealthReport (правый верхний,
синий, белый текст, скруглённый, поверх круглой кнопки). Отличие — ставить
внутрь `.button-with-text-count-waiting` (как `text-count-waiting`),
а не наружу (как `chat-notify-badge`).

### 5.4. Theme tokens / CSS-variables

- `webvue3/src/style.css` — только базовые CSS, без theme-overrides Bootstrap.
- НЕТ файла `tailwind.config.js`.
- НЕТ кастомных CSS-переменных для цветов вне Bootstrap.
- Доступные blue-shades берутся **из самого Bootstrap 5** (`var(--bs-primary, #0d6efd)`,
  `--bs-info`, `--bs-link-color` и т.д.) — список см. в документации Bootstrap 5
  (но в коде `webvue3` реально используются только три: `#007bff`, `#0d6efd`, `#2c6bd5`).

## 6. Summary / План переиспользования для Q4

1. **Компонент-владелец нового бейджа** — модифицировать
   `webvue3/src/components/Common/ProcessWorker.vue`: внутри
   `.button-with-text-count-waiting` добавить новый `<div class="text-pool-size">`,
   позиционированный `top: 0; right: 0;` (правый верхний, как `chat-notify-badge`),
   фон — `var(--bs-primary, #0d6efd)` или `#2c6bd5`.

2. **Store**: добавить state/getter/mutation/action в существующий модуль
   `healthReport` (`webvue3/src/components/Common/HealthReport/store.js`)
   или новый модуль `healthReportPool`. Имена: `poolSize`, `getPoolSize`,
   `setPoolSize`.

3. **Backend-эндпоинт** (новый, в Q4): `POST /api/healthreportpool/size` —
   число параллельных реплик HealthReport-воркера. Endpoint уже, видимо,
   подразумевается через `KaraokeProcess.THREAD_LANE_HEALTH_REPORT`
   (см. `knowledge/domains/health/components/health-report.md:287`).

4. **SSE-событие**: новое событие в `SseNotificationService` (backend), например
   `HEALTH_REPORT_POOL_SIZE`. В `App.vue:362` (рядом с `PROCESS_COUNT_WAITING`)
   добавить case — диспетчер `this.setPoolSize(...)` → store action.

5. **Initial-poll** (если SSE-канал недоступен): `mounted()` в `ProcessWorker`
   уже вызывает `checkCountWaiting()` — рядом добавить `checkPoolSize()`.

6. **Цвет бейджа пула**: НЕ `#007bff` (используется progress-bar'ом),
   а `var(--bs-primary, #0d6efd)` или `#2c6bd5` (конвенция ChatNotifyButton).

## 7. Файлы — реестр с line numbers

| Файл | Назначение | Ключевые строки |
|---|---|---|
| `webvue3/src/views/SongsView.vue` | View-обёртка для `/songs` | 1-41 |
| `webvue3/src/components/Songs/SongsTable.vue` | Список песен + bulk-actions | 1-2358 (нет процесса-бейджа) |
| `webvue3/src/components/Common/ProcessWorker.vue` | **Бейдж KaraokeProcess** (countWaiting) + кнопка Старт/Стоп | template 1-31; script 33-188; styles 190-256; **бейдж 233-247** |
| `webvue3/src/components/Processes/store.js` | Vuex-модуль `processes` | state.countWaiting: 45; getter 93-95; mutation 196-198; action 310-319 |
| `webvue3/src/store/index.js` | Корневой Vuex-store | import `processes` from line 10; modules 48-87 |
| `webvue3/src/main.js` | Bootstrap (Vue 3 createApp) | 1-18 |
| `webvue3/src/App.vue` | Корневой компонент + SSE | template:101-117; SSE connectSse 278-307; SSE dispatch 358-365; setCountWaiting 437-439 |
| `webvue3/src/router/index.js` | Vue-router 4 | `/songs` route 36-39 |
| `webvue3/package.json` | Версии | `vue ^3.5.21`, `vuex ^4.1.0`, `bootstrap-vue-next ^0.40.5`, `event-source-polyfill ^1.0.31` |
| `webvue3/src/components/Chat/ChatNotifyButton.vue` | **Образец** синего бейджа для Q4 | template 1-11; styles 65-93 (chat-notify-badge) |
| `webvue3/src/components/Common/Monitor/MonitorLight.vue` | Соседний бейдж в шапке | весь файл |
| `webvue3/src/components/Common/HealthReport/store.js` | Текущий Vuex-модуль `healthReport` (без pool) | весь (нет pool state) |

## 8. Knowledge References (SSoT)

- `knowledge/domains/processing/components/async-process-queue.md` — backend
  `KaraokeProcess` + worker (источник событий `PROCESS_WORKER_STATE`,
  `PROCESS_COUNT_WAITING`).
- `knowledge/domains/sse/domain.md` — 14 типов SSE-событий, упоминания
  `processCountWaiting` (строка 26) и `processWorkerState` (строка 159).
  Место для добавления нового типа события в Q4.
- `knowledge/domains/health/components/health-report.md` — упоминает
  `KaraokeProcess.THREAD_LANE_HEALTH_REPORT` (строки 277, 287) — концепция
  lane для пула HealthReport.
- `knowledge/system/frontend/webvue3-views.md:82` — описание
  `KaraokeProcessAdmin` в шапке.
- `knowledge/system/frontend/vuex-patterns.md` — паттерны Vuex-стора webvue3.
- `knowledge/system/frontend/store-songs.md`, `store-song-editor.md`,
  `store-sync.md`, `store-properties.md`, `store-site-users.md` —
  pattern store-импортов.

## 9. Поисковые запросы, использованные при исследовании

- `grep -r 'KaraokeProcessBadge\|processBadge\|ProcessBadge' webvue3/` — **0 hits**
  (бейдж не вынесен в отдельный компонент, живёт внутри `ProcessWorker.vue`).
- `grep -r 'processCount\|process_count\|process-count' webvue3/` — **0 hits**
  (имя — `countWaiting`).
- `grep -rn 'getCountWaiting\|countWaiting' webvue3/src/` — 8 hits, все в
  `ProcessWorker.vue` + `processes/store.js`.
- `grep -rn 'PROCESS_COUNT_WAITING\|PROCESS_WORKER_STATE' webvue3/src/` —
  4 hits: 2 в `App.vue` (dispatcher), 2 комментария в `processes/store.js`
  и самом `ProcessWorker.vue`.
- `grep -n '007bff\|007BFF\|primary.*blue\|--bs-primary' webvue3/` —
  4 hits: `ProcessWorker.vue:124` (progress-bar), `AuthorsTable.vue:971`,
  `ProcessBulkReportModal.vue:187,189`.
- `mcp__codegraph` — **не использовался** (research, read-only; достаточно
  grep/read для плоских файлов webvue3, плюс прямые знания из `knowledge/`).

## 10. Open questions / Что выяснится в Q4

- **Формат SSE-события** для пула — какое имя, какая структура payload —
  определяется на backend-стороне. Нужно синхронизировать с
  `KaraokeProcess.THREAD_LANE_HEALTH_REPORT` инициативой.
- **Эндпоинт** `POST /api/healthreportpool/size` — подтвердить имя и метод
  с backend.
- **Цвет бейджа пула** — выбор между `#0d6efd` и `#2c6bd5` отдан на
  решение в Q4. Оба варианта совместимы с конвенцией.
- **Расположение** — точно правый верхний угол `button-with-text-count-waiting`
  (т.е. внутри существующего контейнера, рядом с `text-count-waiting`).
  Это совпадает с постановкой задачи.
