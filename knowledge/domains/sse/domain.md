---
id: domain-sse
title: "Domain: SSE / Real-time notifications"
status: Active
slug: sse
related:
  - ../monitoring/domain.md
  - ../processing/domain.md
  - ../storage/domain.md
---

# Domain: SSE / Real-time notifications

> Bounded context для Server-Sent Events: real-time push уведомления
> от karaoke-app к webvue3. Прецедент создания — P2 Knowledge-аудита
> (Pass 341).

## Обзор контекста (Bounded Context)

**SSE (Server-Sent Events)** — основной механизм real-time
уведомлений в проекте. Каждый webvue3-tab подключается к `/api/subscribe`
и получает события:

- `recordChange`, `recordAdd`, `recordDelete` — записи изменились в БД.
- `processWorkerState` — статус задания в async-очереди изменился.
- `processCountWaiting` — счётчик заданий в очереди.
- `message`, `error` — пользовательские сообщения и ошибки.
- `log` — для отладки.
- `crud` — результаты bulk-операций.
- `sync` — статус two-DB sync.
- `healthReports` — обновление `HealthReportList` для UI.
- `monitorAlerts` — алерты мониторинга.
- `massSearchSummary` — сводка массового поиска текста (spec 316).

**Архитектурное решение**: SSE, а не WebSocket, потому что:

- Server → Client only (не нужен bidirectional).
- Проще в реализации (стандартный Spring `SseEmitter`).
- Авто-reconnect встроен в браузер.

**Граница**: контекст НЕ отвечает за:

- Хранение состояния задач — это async-process- queue.
- Логику мониторинга — это monitoring domain.
- UI-рендеринг событий — это webvue3.

## Ubiquitous Language | Единый язык

| Термин | Определение | Где в коде |
| --- | --- | --- |
| **`SseNotification`** | Data class: `(type: SseNotificationType, data: Any)` | `model/SseNotification.kt` |
| **`SseNotificationType`** | Enum типов событий (см. таблицу ниже) | `model/SseNotificationType.kt` |
| **`SseEmitter`** | Spring-обёртка над SSE-соединением | `services/SseNotificationService.kt` |
| **`UserKey`** | `(userId, tabId)` для маршрутизации | `services/SseNotificationService.kt:45` |
| **`TabIdContext`** | ThreadLocal с tabId текущего запроса | `services/SseNotificationService.kt:45` |
| **`addressedTypes`** | Типы, доставляемые адресно (только инициатору): `MESSAGE`, `ERROR` | `services/SseNotificationService.kt:99` |
| **`maxEmittersPerTab`** | Ограничение: 1 emitter на tab (default) | `services/SseNotificationService.kt:92` |
| **`ping`** | Heartbeat-комментарий каждые 15с (`fixedRate = 15_000`) | `services/SseNotificationService.kt:165` |

### Типы событий

| Тип | Что несёт | Broadcast или Addressed |
|---|---|---|
| `RECORD_CHANGE` | Запись изменилась в БД | broadcast |
| `RECORD_ADD` | Новая запись | broadcast |
| `RECORD_DELETE` | Запись удалена | broadcast |
| `PROCESS_WORKER_STATE` | Задание в async-queue сменило статус | broadcast |
| `PROCESS_COUNT_WAITING` | Счётчик WAITING-заданий | broadcast |
| `MESSAGE` | Сообщение пользователю | **addressed** (только инициатору) |
| `ERROR` | Ошибка | **addressed** |
| `DUMMY` | Тест | broadcast |
| `LOG` | Лог | broadcast |
| `CRUD` | Результат bulk-операции | broadcast |
| `SYNC` | Статус two-DB sync | broadcast |
| `HEALTH_REPORTS` | Обновление HealthReportList | broadcast |
| `HEALTH_REPORT_POOL_COUNT` | Размер приоритетной очереди `HealthReportBatchPool` (Pass 128/129). Рассылается при изменении с подавлением дублей через `lastSentQueueSize`. Фронт — зелёный бейдж в `ProcessWorker.vue`. | broadcast |
| `HEALTH_REPORT_WAITING_POOL_SIZE` | Размер реального backend-пула WAITING-задач `HealthReportBatchPool.waitingQueue` (OpenProject #132, specs/132-hrwaiting-pool). Пул LIFO с 20 worker-потоками, одно задание = один файл `WaitingFileTask(songId, source, bucket, fileName)`, single-flight. Воркеры синхронно проверяют файл и заполняют `StorageMetadataCache`, затем ставят песню в song-пул на пересчёт. Наполняется из `HealthReport.recomputeAndBroadcast` для записей со статусом `WAITING`. Рассылается с подавлением дублей через `lastSentWaitingPoolSize`. Фронт — синий бейдж в `ProcessWorker.vue` (скрыт при `0`). | broadcast |
| `MONITOR_ALERTS` | Алерты мониторинга | broadcast |
| `MASS_SEARCH_SUMMARY` | Сводка поиска (spec 316) | broadcast |

## Архитектура

### Endpoint: `/api/subscribe`

Эндпоинт живёт в `ApiController` (`@RequestMapping("/api")`, `:200`) — класса `SseController` в коде нет. На каждый GET-запрос
создаёт `SseEmitter` (timeout=-1 = forever) и регистрирует в `emitters`
по `UserKey`.

### Routing: addressed vs broadcast

`addressedTypes = { MESSAGE, ERROR }`:

- **Addressed** — событие доставляется только вкладке-инициатору (через
  `TabIdContext` ThreadLocal). Полезно для «ответ на конкретное
  действие».
- **Broadcast** — всем вкладкам пользователя `userId=1` (default в
  текущей кодовой базе; в multi-user среде будет `request.userId`).

### Heartbeat

Каждые 15 секунд рассылается SSE-комментарий `ping` всем emitters
(`@Scheduled(fixedRate = 15_000)`, `SseNotificationService.kt:165`).
Прежняя версия говорила «~25с» — это противоречило и коду, и строке 160
этого же файла. Это нужно, чтобы:

- Прокси не закрывали соединение по idle timeout.
- Клиент обнаруживал разрыв быстрее (если ping не пришёл).

### Cleanup

Если `SseEmitter.send()` бросает IOException (клиент ушёл), emitter
удаляется из map (строка 181-195 `staleEmitters`).

### `recomputeAndBroadcast` в HealthReport

После repair-loop `HealthReport.recomputeAndBroadcast` рассылает SSE
событие `HEALTH_REPORTS` (см. [health domain](../health/domain.md)).

## Архитектурные решения

### Решение 1: SSE вместо WebSocket

- Server→Client only (нет нужды в bidirectional).
- Проще: `SseEmitter` из коробки Spring.
- Browser auto-reconnect.

### Решение 2: UserKey = `(userId, tabId)`

Мульти-вкладочность: каждый webvue3-tab имеет свой `tabId` (UUID).
ThreadLocal `TabIdContext` сохраняет `tabId` на время HTTP-запроса,
позволяя рассылать ответы адресно.

### Решение 3: `maxEmittersPerTab = 1`

Защита от дублей: если вкладка переподключилась (например, F5),
старый emitter удаляется. Один на tab.

## Публичные контракты (API)

### HTTP-контракт (SSE)

- `GET /api/subscribe?tabId=<uuid>` — `ApiController`
  (`@RequestMapping("/api")`, `ApiController.kt:6041`): создаёт
  `SseEmitter(timeout = -1)` и регистрирует его по `UserKey(userId = 1L, tabId)`.
  Заголовки ответа: `Content-Type: text/event-stream`, `Cache-Control: no-store`,
  `X-Accel-Buffering: no`. Потребитель — `webvue3` (`App.vue:282`,
  `EventSourcePolyfill`, `heartbeatTimeout = 30000`); других слушателей нет.
- Heartbeat: SSE-комментарий `ping` каждые 15 с
  (`@Scheduled(fixedRate = 15_000)`, `SseNotificationService.kt:165`).
- STOMP-конфигурация в `karaoke-web` (`WebSocketConfig.kt:15`: endpoint
  `/api/message` с SockJS, simple-broker `/api/messages`, app-prefix `/app`)
  присутствует, но **не используется**: `SimpMessagingTemplate` нигде не
  вызывается (`convertAndSend` в коде не встречается), прикладного
  STOMP-клиента во фронтах нет. Реальный контракт real-time — только SSE.

### Каналы (типы событий)

Публикуются через `SseNotificationService.send(SseNotification)`; тип —
`SseNotificationType` (имя на клиенте в скобках). Broadcast для всех типов, кроме
`MESSAGE`/`ERROR` — они addressed по `tabId` (`addressedTypes`).

| Публикатор | Типы |
| --- | --- |
| `KaraokeDbTable.save()` / `createDbInstance`, `KaraokeProcess` | `RECORD_CHANGE` (`recordChange`), `RECORD_ADD` (`recordAdd`), `RECORD_DELETE` (`recordDelete`) |
| `KaraokeProcessWorker` (`:775`, `:820`) | `PROCESS_WORKER_STATE` (`processWorkerState`), `PROCESS_COUNT_WAITING` (`processCountWaiting`) |
| `StorageMetadataCache` → `KaraokeProcessWorker.sendCacheQueueSizeMessage` | `CACHE_QUEUE_SIZE` (`cacheQueueSize`), specs/118 #397 |
| `HealthReport.recomputeAndBroadcast` (`HealthReport.kt:2473`) | `HEALTH_REPORTS` (`healthReports`) |
| `HealthReportBatchPool` (`:155`, `:194`) | `HEALTH_REPORT_POOL_COUNT` (`healthReportPoolCount`), `HEALTH_REPORT_WAITING_POOL_SIZE` (`healthReportWaitingPoolSize`) |
| `MonitoringService.kt:79`, `ApiController.kt:4868` / `:5490` | `MONITOR_ALERTS` (`monitorAlerts`), `MASS_SEARCH_SUMMARY` (`massSearchSummary`) |

Полный перечень значений — `model/SseNotificationType.kt`; фабрики событий —
`model/SseNotification.kt`.

### Internal API

- `SseNotificationService.subscribe(userId, tabId): SseEmitter`, `send(notification)`,
  `heartbeat()`, `onShutdown()`.
- `SseNotification` — фабрики событий; `UserKey(userId, tabId)`; `TabIdContext`
  (ThreadLocal с `tabId` текущего HTTP-запроса).

## Структура компонентов (C4 L3)

L3-компонентов у домена пока нет: контекст описан целиком в этом
`domain.md` (см. [домены](../README.md)). Создание компонентных документов —
отдельная задача; сам факт отсутствия L3 зафиксирован здесь, чтобы ссылка
«структура компонентов» не выглядела потерянной.

## Domain Invariants

1. **`SseEmitter.timeout = -1` (forever)**: SSE-соединение держится,
   пока клиент не отключится. Heartbeat (комментарии) защищает от
   прокси-timeout'ов.
2. **`send()` НЕ ДОЛЖЕН бросать exception** в caller-thread — ошибки
   клиента обрабатываются внутри `sendEventToKeys`.
3. **`MESSAGE`/`ERROR` ДОЛЖНЫ иметь `tabId`** для addressed-доставки.
   Без `tabId` → broadcast (что нежелательно, но не критично).

## Hot paths

- **`/api/subscribe`** — открывается при загрузке webvue3. Долгое
  соединение (часы).
- **Heartbeat каждые 25 сек** — низкий объём.
- **`processWorkerState`** — несколько раз в секуну на активном
  rendering (5 одновременных задач → ~5 событий/сек).
- **`healthReports`** — после repair-loop, может быть N=сотни за
  раз (bulk-repair).

## Зависимости | Dependencies

- **HealthReport** ([health domain](../health/domain.md)):
  `recomputeAndBroadcast` → `HEALTH_REPORTS` событие.
- **Async Process Queue** ([processing/async-process-queue](../processing/components/async-process-queue.md)):
  `processWorkerState` и `processCountWaiting` события.
- **Monitoring** ([monitoring domain](../monitoring/domain.md)):
  `MONITOR_ALERTS` события.
- **Two-DB sync** ([processing/two-db-sync](../processing/components/two-db-sync.md)):
  `SYNC` события.

## Известные TODO

> **[WARN] Ограничение адресации (Pass 476)**: эндпоинт `/api/subscribe`
> не знает пользователя — у него нет ни auth-контекста, ни параметра
> userId, и он вызывает `sseNotificationService.subscribe(1L, tabId)`
> с **жёстко заданным** `userId = 1` (`ApiController.kt:6056`).
> Поскольку `userId` входит в `UserKey(userId, tabId)`, адресная
> доставка (`MESSAGE`/`ERROR`) фактически работает по `tabId`, а не по
> пользователю: все подписки зарегистрированы как пользователь 1.
> Для однопользовательского admin-UI это не проявляется; корректная
> адресация требует прокинуть аутентифицированного пользователя в
> эндпоинт — отдельная задача.

- [x] **Где определён `/api/subscribe`** — `ApiController`
      (`controllers/ApiController.kt:200` `@RequestMapping("/api")`,
      `:6041` `@GetMapping("/subscribe")`). Класса `SseController` нет.
- [ ] **`TabIdContext`** — где устанавливается tabId, кто читает.
- [ ] **Multi-user**: сейчас `userId=1` hard-coded. Как перейти на
      реального пользователя?
- [ ] **Browser-side**: Vuex-модуль для SSE в webvue3 — где,
      какие типы обрабатывает.
- [ ] **Reconnect logic**: встроенный браузер или кастомный?
- [ ] **`message`/`error`** — где конкретно создаются (например,
      `crud-error`, `force-stop-ok`).
- [ ] **Тесты**: есть ли unit-тесты на SseNotificationService.

## Код (физическая реализация)

- `karaoke-app/.../services/SseNotificationService.kt` (~250 строк)
- `karaoke-app/.../model/SseNotification.kt`
- `karaoke-app/.../model/SseNotificationType.kt`
- `karaoke-app/.../model/RecordChangeMessage.kt`
- `karaoke-app/.../model/RecordAddMessage.kt`
- `karaoke-app/.../model/RecordDeleteMessage.kt`
- `karaoke-app/.../model/ProcessWorkerStateMessage.kt`
- `karaoke-app/.../model/ProcessCountWaitingMessage.kt`
- `karaoke-app/.../model/Message.kt`

## Changelog

- **Pass 341 P2** (2026-09-09): Initial. Автор: agent (Karaoke).
- **Pass 486** (2026-09-27, spec `486-knowledge-domains-others`): добавлена секция «Публичные контракты (API)». Автор: agent (Karaoke).