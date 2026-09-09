# Component: monitor-checks (детальный каталог)

> **Домен**: [monitoring](../domain.md)
> **Компонента**: детальный каталог 7 MonitorCheck'ов. Дополняет
> [monitor-checks.md](monitor-checks.md).

## Назначение

7 MonitorCheck'ов в `karaoke-app/.../monitor/checks/`. Тикают раз в
минуту через `MonitoringService`. Каждый возвращает `List<MonitorAlert>`.

## Каталог (7 чеков)

| # | Check | Строк | Severity | Что |
|---|---|---|---|---|
| 1 | `ProdContainerCheck` | 278 | **CRITICAL** | HTTP-пинг прод + JDBC-пинг прод-БД |
| 2 | `RenderQueueStalledCheck` | 39 | WARNING | Очередь рендера остановлена + есть WAITING |
| 3 | `LaneStalledCheck` | 109 | WARNING | **Конкретный лейн** завис (защитная сетка от #29) |
| 4 | `TelegramPollingDisabledCheck` | 33 | **CRITICAL** | `telegramPollingEnabled=false` |
| 5 | `UnreadChatMessagesCheck` | 47 | WARNING | Непрочитанных > 1 час (remote DB) |
| 6 | `SubmittedAssignmentsCheck` | 47 | WARNING | Заданий в `submitted` > 24ч (remote DB) |
| 7 | `StemJobsStuckCheck` | 49 | WARNING | StemJob застряли > 30 мин (WAITING/WORKING) |

## Детальные контракты

### 1. `ProdContainerCheck` (278 строк, **CRITICAL**)

**Файл**: `karaoke-app/.../monitor/checks/ProdContainerCheck.kt`.

**Проверяет**:
- **HTTP-пинг** `https://sm-karaoke.ru/` (nginx + karaoke-web за ним).
- **JDBC-пинг** прод-БД (хост из env `DB_REMOTE_HOST`).

**Severity нарастает**:
- **WARNING** — сразу после первого сбоя.
- **CRITICAL** — если недоступность длится ≥
  `monitorProdDownCriticalMinutes` минут.

**`firstFailureAt`** — хранится **только в памяти** (не
персистится) — при перезапуске `karaoke-app` отсчёт начинается
заново.

**Одноклик-fix**: не предусмотрен (требует ручного вмешательства).

### 2. `RenderQueueStalledCheck` (39 строк, WARNING)

**Проверяет**: `KaraokeProcessWorker.isWork == false` И
`getCountWaiting > 0`.

**Одноклик-fix**: запустить воркер (тот же вызов, что и кнопка
старт/стоп в webvue3, см. `ApiController./api/processes/workerstartstop`).

### 3. `LaneStalledCheck` (109 строк, WARNING)

**Отличие от `RenderQueueStalledCheck`**: видит зависание
**конкретного лейна** при работающей очереди (защитная сетка от
регрессий race-condition в `KaraokeProcessWorker`, см.
specs/029-fix-queue-lane-stall).

**Проверяет**: у лейна есть `WAITING`-задания, но ни одно не
выполняется дольше `STALL_THRESHOLD_MS` (при этом
`KaraokeProcessWorker.isWork == true` в целом).

**Одноклик-fix**: `KaraokeProcess.setWorkingToWaitingForThread` —
вернуть осиротевшие `WORKING`-записи этого лейна в `WAITING`, не
трогая другие лейны.

### 4. `TelegramPollingDisabledCheck` (33 строки, **CRITICAL**)

**Проверяет**: `KaraokeProperties.getBoolean("telegramPollingEnabled") == false`.

**Зачем**: без `getUpdates` ссылки на отложенные посты в Telegram
не проставляются автоматически (см. `TelegramUpdatesConsumer`).

**Одноклик-fix**: включить свойство и стартовать демон
(`TelegramUpdatesConsumer.start()` идемпотентен при `isWork=true`).

### 5. `UnreadChatMessagesCheck` (47 строк, WARNING)

**Проверяет**: `SiteChatMessage.countUnreadFromUsers(remoteDb) > 0` дольше 1 часа.

**БД**: `tbl_site_chat_messages` живёт **целиком на PROD** (см.
[SiteChatMessage](../../catalog/components/entities-catalog.md#sitechatmessage)).
`MonitorContext` даёт только `localDb`, поэтому проверка **сама
открывает** `Connection.remote()` и **обязательно закрывает**
(паттерн `ProdContainerCheck.pingRemoteDb()`).

**Body без числа**: количество в `detail` (через
`MonitorAlert.contentHash()`), иначе алерт «мигал» бы
read/unread на каждом тике.

### 6. `SubmittedAssignmentsCheck` (47 строк, WARNING)

**Проверяет**: `SongAssignment.countSubmitted(remoteDb, ...) > 0` дольше 24ч.

**БД**: PROD (аналогично `UnreadChatMessagesCheck`).
**Bodies тоже без числа**.

Реальный рабочий цикл заданий (назначить → работа → апрув) чаще
всего идёт целиком на PROD (см. `SongEditorController`,
`SongEditor/store.js defaultTarget='remote`).

### 7. `StemJobsStuckCheck` (49 строк, WARNING)

**Проверяет**: `StemJob.countStuck(remoteDb, STALE_MINUTES=30) > 0`.

**БД**: PROD (задания создаются пользователями на прод-сайте).
Body без числа.

## Архитектурные решения

### Решение 1: `body` без числа

Все проверки **намеренно** не включают число в `body` — оно в
`detail` (через `MonitorAlert.contentHash()`), иначе алерт «мигал» бы
read/unread на каждом тике.

### Решение 2: PROD-чеки сами открывают `Connection.remote()`

`MonitorContext` даёт только `localDb`, но PROD-чеки
(`UnreadChatMessagesCheck`, `SubmittedAssignmentsCheck`,
`StemJobsStuckCheck`, `ProdContainerCheck`) сами открывают
`Connection.remote()` и закрывают в `finally`.

### Решение 3: `firstFailureAt` в памяти

`ProdContainerCheck` хранит `firstFailureAt` **только в памяти** —
при перезапуске `karaoke-app` отсчёт начинается заново. Это
by-design (нет персистенции monitor-состояния).

## Связь с другими компонентами

- [monitor-checks.md](monitor-checks.md) — общий каталог (7 чеков).
- [log-categories.md](log-categories.md) — `infra.prod.ping`,
  `infra.prod.db`, `infra.cache.statbysong`.
- [rendering domain](../../rendering/domain.md) — `RenderQueueStalledCheck`.
- [async-process-queue.md](../../processing/components/async-process-queue.md) —
  `KaraokeProcessWorker`.

## Известные TODO

- [ ] **`MonitorContext`** — полный API (Pass 343+).
- [ ] **`MonitoringService`** — tick-алгоритм.
- [ ] **WebSocket/SSE push** — monitor-алерты (Pass 343+).

## Changelog

- **Pass 404-410** (2026-09-09): Initial detailed. Автор: agent (Karaoke).