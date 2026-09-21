## Question

Реализовать UI-бейдж синего цвета, отображающий количество оставшихся
WAITING-заданий **только для lane `THREAD_LANE_HEALTH_REPORT = 1`**.

## Контекст (после Q2 research)

- **Бейдж KaraokeProcess** живёт в `webvue3/src/components/Common/ProcessWorker.vue`,
  внутри `.button-with-text-count-waiting` (template:23, style:233-247).
  Текущий бейдж — **серый**, позиция — правый нижний угол круглой кнопки
  Старт/Стоп.
- **Новый бейдж** — внутри того же `.button-with-text-count-waiting`,
  позиция `top: 0; right: 0;` (правый верхний).
- Фреймворк: Vue 3.5 + Vuex 4.1 (Pinia НЕТ). State через Vuex-модуль
  `processes` (`webvue3/src/components/Processes/store.js:45,93,196,317`).
- Подписка через SSE `PROCESS_COUNT_WAITING` (`App.vue:362-365` →
  Vuex action `setCountWaiting`).
- Цвет бейджа: `var(--bs-primary, #0d6efd)` (Bootstrap 5 primary) или
  `#2c6bd5` (конвенция `ChatNotifyButton`); НЕ `#007bff` (занят
  progress-bar'ом в `ProcessWorker.vue:124`).

## Что сделать

1. В `ProcessWorker.vue:233-247` добавить второй `<div class="text-pool-size" v-text="hrPoolSize" />`
   внутри `.button-with-text-count-waiting`, позиция `top: 0; right: 0;`,
   цвет `var(--bs-primary, #0d6efd)` или `#2c6bd5`.
2. Расширить Vuex-модуль `processes` (или завести новый `healthReportPool`)
   state: `hrPoolSize: 0`, getter `getHrPoolSize`, mutation `setHrPoolSize`,
   action `setHrPoolSize`.
3. На `App.vue:362-365` — добавить case для нового SSE-события (имя
   определит #125), диспетчер `this.setHrPoolSize(...)`.
4. Initial poll в `mounted()` `ProcessWorker.vue` — добавить вызов
   `getProcessesCountWaitingPromise({ thread_id: 1 })` (или отдельный
   action), если SSE-канал ещё не подключился.
5. Скрывать бейдж при `hrPoolSize === 0` (или показывать «0» — решит
   владелец на ревью).

## Зависимости

- Карта #119.
- Q2 research (закрыт).
- **#125** (backend `countwaiting?thread_id=1`).

Тип: [wayfinder:task]
Блокирует: отчёт report.md в OpenProject #118
Заблокирован: #125