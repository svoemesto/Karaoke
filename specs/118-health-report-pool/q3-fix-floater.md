## Question

Реализовать фикс «всплытия» заданий в пуле HealthReport при возврате на
ранее посещённую страницу компонента «Песни» в админке.

## Контекст (после Q1 research)

**Пул двухуровневый**:

1. **Backend-cascade** (`startRepairAll / onRepairProcessFinished` в
   `HealthReport.kt:2467-2529`) — **работает корректно**, задания живут
   в `tbl_processes` lane `THREAD_LANE_HEALTH_REPORT = 1` под защитой
   per-song single-flight (`attemptEnterRepair/exitRepair`). **НЕ ТРОГАТЬ**.

2. **Frontend-очередь** `hrQueue` в `SongsTable.vue:654-656`
   (массив, FIFO, `HR_MAX_CONCURRENT = 3`) — **здесь точка слома**:
   - `SongsTable.vue:1069` (`watch.currentPage.handler`) — `this.hrQueue = []`
     при каждой смене страницы.
   - `SongsTable.vue:1901` (`editSong`) — `this.hrQueue = []` при открытии
     редактора.
   - `SongsView.vue` — без `<keep-alive>`, поэтому при возврате
     SongsTable перемонтируется, `hrQueue` стартует с `[]` (default).

## Что фиксить

Только **фронт**. Backend не трогать.

Перед реализацией — **выбрать вариант** через `grilling` (HITL).
Три варианта (q1-answer § 8):

- **A**. Не сбрасывать `hrQueue` при смене страницы / открытии
  редактора (но тогда при возврате могут прилетать устаревшие
  результаты для предыдущей страницы).
- **B**. `<keep-alive>` для `SongsTable` в `SongsView.vue`
  (сохраняет state, но требует аудита — переживают ли `data()` /
  `computed` обратную навигацию; возможны side-effects).
- **C**. `Set`/`Map<songId, pageId>` для `hrQueue` — фильтрация
  по текущей странице, догрузка при возврате (чище, но больше
  правок).

## Зависимости

- Карта #119.
- Q1 research (закрыт).

Тип: [wayfinder:task]
Блокирует: отчёт report.md в OpenProject #118
Заблокирован: — (Q1 закрыт, grilling не блокирует старт)