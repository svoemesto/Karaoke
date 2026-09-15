# Spec #128 — Асинхронный healthReportList через батч + SSE

> **OpenProject**: #128 «Асинхронный healthReportList».
> **Тип**: Task (конкретная техническая задача с ясным контрактом).
> **Knowledge-first MUST #0**: выполнен — см. «Knowledge References» ниже.
> **Важно**: это **изолированная задача**, не наследует решений из каких-либо
> отменённых/архивных тикетов. Работаем с текущим кодом `SongsTable.vue` и
> `HealthReport.kt` как есть.

## 1. Проблема

При открытии страницы админки «Песни» в браузере запускается **каскад
синхронных HTTP-запросов** `POST /api/song/healthReportList?id=<songId>` —
по одному на каждую песню текущей страницы, у которой ещё не подгружен
HealthReport. Текущая реализация (`SongsTable.vue:1310-1339`):

```js
_enqueueHrRequest(songId) {
  this.hrQueue.push(songId)        // push в конец
  this._processHrQueue()
},
_processHrQueue() {
  while (this.hrRunning < this.HR_MAX_CONCURRENT && this.hrQueue.length > 0) {
    const id = this.hrQueue.shift()   // shift из начала (FIFO)
    this.hrRunning++
    this.$store.dispatch('setCurrentSongHealthReports', id).finally(() => {
      this.hrRunning--
      this._processHrQueue()
    })
  }
}
```

И при смене страницы — полная очистка `this.hrQueue = []`.

**Что не так**:

- ❌ **Синхронный HTTP**: фронт ждёт ответа бэка для каждой песни (5+ проверок
  × MinIO lookups). При 30 песнях на странице и `HR_MAX_CONCURRENT=3` это
  **10 раундов** HTTP round-trip ≈ **5–10 секунд блокировки UI**.
- ❌ **Полный wipe при переключении страницы**: задания предыдущей страницы
  теряются безвозвратно (даже если ещё не выполнялись).
- ❌ **Нет приоритизации**: FIFO на фронте — при переходе на новую страницу
  новые песни встают в конец очереди, обслуживаются вперемешку со старыми.

## 2. Решение (одно предложение)

Бэк принимает батч id-ов, **ставит их в приоритетный пул**, worker-пул из 10
потоков исполняет `getHealthReportList` чанками (по 1 songId за раз,
параллельно), а результаты рассылает на веб через **уже существующий SSE-канал
`HEALTH_REPORTS`**. Фронт перестаёт ждать ответа — обновление строки приходит
push-ом.

## 3. Acceptance criteria

### 3.1. Backend

1. Новый endpoint `POST /api/song/healthReportListBatch` принимает
   `List<Long> songIds`, возвращает `202 Accepted` мгновенно (< 50ms).
2. **Никакого ожидания** — `getHealthReportList` для каждой песни исполняется
   в фоновом пуле.
3. Пул из **10 worker-потоков**, фиксированный (`Executors.newFixedThreadPool(10)`).
4. Очередь заданий — **приоритетная**:
   - Дедуп по `songId` (через `LinkedHashSet` или `ArrayDeque` + индекс).
   - При поступлении нового батча все его `songId` вставляются **в начало**.
   - Если `songId` уже в очереди и не исполнен — он **перемещается в начало**
     (явное требование из #128).
   - Worker берёт `songId` из начала.
5. **Single-flight guard** на исполнении (паттерн из `race-fixed-65.md`):
   если для этого `songId` уже идёт вычисление в другом потоке — пропускаем.
6. На каждый готовый отчёт рассылается **существующее** SSE-событие
   `HEALTH_REPORTS` с `songId + healthReportDtoList` (без изменений payload —
   фронт уже умеет).

### 3.2. Frontend

1. `SongsTable.vue._enqueueHrRequest` теперь **группирует все id текущей страницы
   без HR** и шлёт **один** `POST /api/song/healthReportListBatch`.
2. Локально сразу помечает песни как **pending** (новое поле `healthReportText = '?'`
   с серым цветом `#CCCCCC`) — чтобы не слать повторно.
3. Получив SSE `HEALTH_REPORTS` для конкретного `songId` — обновляет
   `song.healthReportList`/`healthReportText`/`healthReportColor`
   (используем существующую mutation `healthReportMessageByUserEvent` — без изменений).
4. **Legacy**: одиночный `POST /api/song/healthReportList?id=<songId>` **остаётся**
   для `HealthReportTable.vue` (модалка одной песни) и для
   `setCurrentSongHealthReports` в редакторе. Без изменений.

### 3.3. Тесты

- Unit: дедуп + move-to-front при повторном добавлении.
- Unit: 10 worker'ов исполняют 100 заданий параллельно, порядок по приоритету.
- Unit: ошибка в одном worker'е не валит весь пул.

### 3.4. Hard gates (AGENTS.md § Обязательная проверка после изменения)

- `gradle compile + ktlint` PASS.
- `webvue3 lint + Vite build` PASS.
- Нет новых MP4-упоминаний, нет JPA, нет ломки Sanitizer (R-07/R-08/R-11).

## 4. Knowledge References (MUST #0 — verification)

**Прочитано** в этой сессии перед написанием спеки:

- `knowledge/README.md` (SSoT-каркас).
- `knowledge/domains/health/domain.md` (домен HealthReport, инварианты,
  связь с задачами #65/#69).
- `knowledge/domains/health/components/health-report.md` (детальный контракт
  `HealthReport`, `recomputeAndBroadcast`, `getHealthReportList`).
- `knowledge/domains/health/components/race-fixed-65.md` (паттерн
  `AtomicBoolean`/`ConcurrentHashMap` single-flight guard — **переиспользуем**
  в приоритетной очереди для дедупа).
- `knowledge/domains/sse/domain.md` (SSE-канал `HEALTH_REPORTS`, broadcast,
  `addressedTypes`).
- `knowledge/domains/processing/components/async-process-queue.md` (паттерн
  executor'а; `KaraokeProcess` не подходит — нам не нужны цепочки и
  персистентность across restart, делаем свой сервис).
- `knowledge/system/frontend/store-health-report-monitor.md` (Vuex
  `HealthReport/store.js` и `Songs/store.js`).
- `knowledge/adr/0008-tracker-openproject-migration.md` (трекер).
- `knowledge/domains/monitoring/components/log-categories.md`
  (для регистрации новой SLF4J-категории).

**Прямой код прочитан**:

- `webvue3/src/components/Common/HealthReport/store.js` (74 строки).
- `webvue3/src/components/Songs/SongsTable.vue:1310-1358` (каскад `hrQueue`).
- `webvue3/src/components/Songs/store.js:1846-1860` (`setCurrentSongHealthReports`).
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt:7673-7686`
  (старый endpoint `/song/healthReportList`).
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReport.kt:2350-2371`
  (`recomputeAndBroadcast` — переиспользуем как есть).
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/SseNotificationService.kt:109-146`
  (`send` — broadcast, без `tabId`).
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SseNotification.kt:43-53`
  (payload `healthReports(songId, healthReportDtoList)`).
- `webvue3/src/components/Songs/store.js:1670-1728` (`healthReportMessageByUserEvent` mutation).

**Searched → no relevant docs**: паттерн «приоритетный пул с дедупом и
move-to-front» в `knowledge/` отсутствует — это новая абстракция, описываем её
в `health/domain.md` как новый компонент `health-report-batch-pool.md`.

## 5. Архитектура (кратко)

```
┌────────────────┐ batch POST        ┌──────────────────────────────┐
│  webvue3       │ ────────────────► │  POST /song/healthReportList │
│  SongsTable.vue│                   │   Batch (List<Long> ids)     │
│                │                   │   202 Accepted, мгновенно    │
└────────────────┘                   └──────────────┬───────────────┘
        ▲                                           │ enqueue (приоритетно)
        │ SSE HEALTH_REPORTS                        ▼
        │ (broadcast)             ┌─────────────────────────────────┐
        │                         │  HealthReportBatchPool          │
        │                         │  - priorityQueue: LinkedHashSet │
        │                         │  - executorService: 10 потоков  │
        │                         │  - singleFlight: AtomicBoolean  │
        │                         └──────────────┬──────────────────┘
        │                                        │ каждый worker берёт 1 songId
        │                                        │ вызывает recomputeAndBroadcast
        │                                        ▼
        │                         ┌─────────────────────────────────┐
        │                         │  SseNotificationService.send    │
        │                         │   healthReports(songId, dtos)   │
        │                         └─────────────────────────────────┘
        │                                        │
        └────────────────────────────────────────┘
```

**Ключевые решения**:

1. **Не переиспользуем `KaraokeProcess`** — у нас не задачи в БД, не нужны
   цепочки, не нужна персистентность across restart. Простой `ExecutorService`
   с фиксированным пулом достаточно.
2. **Single-flight через `ConcurrentHashMap<Long, AtomicBoolean>`** — паттерн
   из `race-fixed-65.md`, переиспользуем без изменений. Не дважды считаем одну
   и ту же песню (если уже посчитали — событие SSE уже ушло, фронт обновил UI;
   повторный запрос был бы лишним).
3. **Приоритет = move-to-front** — `LinkedHashSet<Long>` с поиском через
   `remove(songId)` + `addFirst(songId)`. Worker читает из начала через
   `pollFirst()`. NB: `LinkedHashSet` в Java не имеет `addFirst` — оборачиваем
   в свой мини-класс или используем `LinkedHashSet` поверх `ArrayDeque`
   (см. plan.md для точной реализации).
4. **Не трогаем `recomputeAndBroadcast`** — он уже инкапсулирует логику
   `getHealthReportList` + `reconcilePlayerReadinessFlags` + `SNS.send`. Worker
   просто вызывает его.
5. **Поведение при ошибке**: если `recomputeAndBroadcast` бросил исключение,
   worker НЕ падает (try/catch внутри `executor.submit`-таска), логирует через
   **новую SLF4J-категорию** `infra.cache.hrpool`, `exitSingleFlight(songId)`
   всё равно вызывается.
6. **Graceful shutdown**: `@PreDestroy` — `executor.shutdown()` + await 5 сек
   + `shutdownNow()` для оставшихся.

## 6. Что НЕ делаем (Out of scope)

- Не трогаем `getHealthReportList` в `HealthReport.kt` (он уже оптимизирован
  кешами #69/#75 — это отдельные задачи).
- Не делаем persistence пула (при рестарте admin'а пул пуст — UI просто
  перезапросит HR при следующей загрузке страницы).
- Не делаем бейдж с размером пула (UI-улучшение, **отдельная задача**).
- Не меняем поведение `HealthReportTable.vue` (модалка одной песни — это
  legacy, остаётся).
- Не делаем клиент-сайд параллелизм параллельно с серверным — фронт шлёт
  батч сразу, заменяя старый `hrQueue` (точнее: `hrQueue` остаётся как
  вспомогательный буфер для группировки в батч, но не для HTTP round-trip).
- Не вводим `hrInFlight`/`pageId`/`filterHash` — это семантика других (отменённых)
  задач; наш подход проще: фронт шлёт батч и забывает, обновление придёт SSE.

## 7. Файлы к изменению/созданию

**Создать**:
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/HealthReportBatchPool.kt`
  — сервис с пулом + приоритетной очередью + single-flight.
- `karaoke-app/src/test/kotlin/.../services/HealthReportBatchPoolTest.kt`
  — unit-тесты (дедуп, move-to-front, параллелизм, устойчивость к ошибкам).

**Изменить**:
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt`
  — добавить endpoint `POST /song/healthReportListBatch` (после строки 7686).
- `webvue3/src/components/Songs/SongsTable.vue` — `_enqueueHrRequest` →
  `_enqueueHrBatch` (группирует в батч); `loadHealthReportBatch` action.
- `webvue3/src/components/Songs/store.js` — action `loadHealthReportBatch`.

**Обновить (Knowledge SSoT)**:
- `knowledge/domains/health/domain.md` — добавить ссылку на новый компонент.
- `knowledge/domains/health/components/health-report-batch-pool.md` (создать) —
  описание паттерна: LinkedHashSet приоритет, 10 worker'ов, single-flight.
- `knowledge/domains/monitoring/components/log-categories.md` — добавить
  `infra.cache.hrpool` (новая SLF4J-категория).

## 8. Тестирование

### 8.1. Unit (backend) — `HealthReportBatchPoolTest.kt`

- `enqueueThenDequeue_sameSongId_doesNotDuplicate`
- `enqueueSameSongIdTwice_secondCallMovesToFront`
- `enqueueBatch_appendsAllAtFront`
- `executorWith10Threads_runs10SongsConcurrently` (10 потоков берут 10 песен
  за < 100ms, реальный recompute мокается)
- `singleFlight_secondConcurrentCallIsSkipped`
- `exceptionInWorker_doesNotCrashPool_otherJobsContinue`
- `shutdown_doesNotLeaveOrphanedTasks`

### 8.2. Manual (frontend) — после deploy

1. Открыть страницу 1 (30 песен, без HR) → должен уйти **один** batch-запрос.
2. UI должен обновиться в течение 1-2 сек (вместо 5-10 сек раньше).
3. Переключиться на страницу 2 — новый батч приоритетный: песни страницы 2
   должны загрузиться раньше, чем недогруженные страницы 1.
4. DevTools Network: для batch — один POST, дальше только SSE-события.

### 8.3. Приёмочные (после deploy)

- 30 песен без HR открываются за < 2 сек (раньше 5-10 сек).
- Нет 500-ответов при недоступности MinIO (graceful degradation).

## 9. История изменений

- **2026-09-15**: Initial spec, версия 1.0. Чистовик, без наследования отменённых задач.