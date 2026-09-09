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
| **`ping`** | Heartbeat-комментарий каждые ~25с | `services/SseNotificationService.kt:171` |

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
| `MONITOR_ALERTS` | Алерты мониторинга | broadcast |
| `MASS_SEARCH_SUMMARY` | Сводка поиска (spec 316) | broadcast |

## Архитектура

### Endpoint: `/api/subscribe`

`SseController` (не путать с `WebSocketConfig`). На каждый GET-запрос
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

Каждые ~25 секунд рассылается SSE-комментарий `ping` всем emitters
(строка 171). Это нужно, чтобы:

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

## Связь с другими компонентами

- **HealthReport** ([health domain](../health/domain.md)):
  `recomputeAndBroadcast` → `HEALTH_REPORTS` событие.
- **Async Process Queue** ([processing/async-process-queue](../processing/components/async-process-queue.md)):
  `processWorkerState` и `processCountWaiting` события.
- **Monitoring** ([monitoring domain](../monitoring/domain.md)):
  `MONITOR_ALERTS` события.
- **Two-DB sync** ([processing/two-db-sync](../processing/components/two-db-sync.md)):
  `SYNC` события.

## Известные TODO

- [ ] **`SseController`** — где определён `/api/subscribe`, какие
      query-параметры.
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
- `karaoke-app/.../model/SseNotificationDto.kt` (предположительно,
  см. gaps)
- `karaoke-app/.../model/...` (конкретные сообщения: `RecordChangeMessage`,
  `ProcessWorkerStateMessage`, `ProcessCountWaitingMessage`, `Message`)

## Changelog

- **Pass 341 P2** (2026-09-09): Initial. Автор: agent (Karaoke).