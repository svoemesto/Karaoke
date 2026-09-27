# Component: monitor-checks (детальный каталог)

> **Домен**: [monitoring](../domain.md)
> **Компонента**: детальный каталог 7 MonitorCheck'ов. Дополняет
> [monitor-checks.md](monitor-checks.md).


## Ответственность | Responsibility


детальный каталог 7 MonitorCheck'ов. Дополняет [monitor-checks.md](monitor-checks.md).

## Назначение

7 MonitorCheck'ов в `karaoke-app/.../monitor/checks/`. Тикают раз в
минуту через `MonitoringService`. Каждый возвращает `List<MonitorAlert>`.

## Каталог (7 чеков)

| # | Check | Строк | Severity | Что |
|---|---|---|---|---|
| 1 | `ProdContainerCheck` | 278 | **WARNING → CRITICAL** | HTTP-пинг прод + JDBC-пинг прод-БД |
| 2 | `RenderQueueStalledCheck` | 39 | WARNING | Очередь рендера остановлена + есть WAITING |
| 3 | `LaneStalledCheck` | 109 | WARNING | **Конкретный лейн** завис (защитная сетка от #29) |
| 4 | `TelegramPollingDisabledCheck` | 33 | WARNING | `telegramPollingEnabled=false` |
| 5 | `UnreadChatMessagesCheck` | 47 | INFO | Непрочитанные сообщения от пользователей (remote DB) |
| 6 | `SubmittedAssignmentsCheck` | 47 | INFO | Задания в `submitted` (remote DB) |
| 7 | `StemJobsStuckCheck` | 49 | WARNING | StemJob застряли > 30 мин (WAITING/WORKING) |

Severity-колонка — по фактическому `MonitorSeverity` в коде каждого
чека (проверено grep'ом `severity =`); `ProdContainerCheck` повышает
WARNING до CRITICAL по времени недоступности (см. ниже).

## Интерфейсы и Контракты | Interfaces and Contracts

### 1. `ProdContainerCheck` (278 строк, WARNING → CRITICAL)

**Файл**: `karaoke-app/.../monitor/checks/ProdContainerCheck.kt`.

**Проверяет**:
- **HTTP-пинг** `https://sm-karaoke.ru/` (nginx + karaoke-web за ним).
- **JDBC-пинг** прод-БД (хост из env `DB_REMOTE_HOST`).

**Severity нарастает**:
- **WARNING** — сразу после первого сбоя.
- **CRITICAL** — если недоступность длится ≥
  `monitorProdDownCriticalMinutes` минут (дефолт `5L`, если свойство
  ≤ 0 или не задано).

**`firstFailureAt`** — хранится **только в памяти** (`@Volatile`,
не персистится) — при перезапуске `karaoke-app` отсчёт начинается
заново.

**Ключ алерта**: `infra.prod.down`. При восстановлении пишется
`ping:recovered url=... downForMin=...`; в обычном режиме (пинги OK)
лог не пишется.

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

**Проверяет**: у лейна есть `WAITING`-задания (`KaraokeProcess.getProcessesToStart`),
а поток лейна не жив (`KaraokeProcessWorker.threadsMap[threadId]`, при
`isWork == true` в целом) дольше `STALL_THRESHOLD_MS = 2 * 60 * 1000L`
(2 минуты). Отметка простоя (`stalledSince`) живёт в памяти между
тиками и чистится, когда очередь лейна опустела.

**Ключ алерта**: `queue.lane.stalled.<threadId>`.

**Одноклик-fix**: `KaraokeProcess.setWorkingToWaitingForThread` —
вернуть осиротевшие `WORKING`-записи этого лейна в `WAITING`, не
трогая другие лейны.

### 4. `TelegramPollingDisabledCheck` (33 строки, WARNING)

**Проверяет**: `KaraokeProperties.getBoolean("telegramPollingEnabled") == false`.

**Зачем**: без `getUpdates` ссылки на отложенные посты в Telegram
не проставляются автоматически (см. `TelegramUpdatesConsumer`).

**Ключ алерта**: `telegram.polling.off`.

**Одноклик-fix**: `KaraokeProperties.setFromString("telegramPollingEnabled", "true")`
+ `TelegramUpdatesConsumer.start()` (идемпотентен при `isWork=true`).

### 5. `UnreadChatMessagesCheck` (47 строк, INFO)

**Проверяет**: `SiteChatMessage.countUnreadFromUsers(remoteDb) > 0`
(`SELECT COUNT(*) ... WHERE is_from_author = false AND is_read = false`).
Временного порога в чеке нет — алерт INFO загорается на любом
непрочитанном сообщении.

**Ключ алерта**: `chat.unread`.

**БД**: `tbl_site_chat_messages` живёт **целиком на PROD** (см.
[SiteChatMessage](../../catalog/components/entities-catalog.md#sitechatmessage)).
`MonitorContext` даёт только `localDb`, поэтому проверка **сама
открывает** `Connection.remote()` и **обязательно закрывает**
(паттерн `ProdContainerCheck.pingRemoteDb()`).

**Body без числа**: количество в `detail` (через
`MonitorAlert.contentHash()`), иначе алерт «мигал» бы
read/unread на каждом тике.

### 6. `SubmittedAssignmentsCheck` (47 строк, INFO)

**Проверяет**: `SongAssignment.countSubmitted(remoteDb, storageService, storageApiClient) > 0`.
Временного порога в чеке нет.

**Ключ алерта**: `songeditor.submitted`.

**БД**: PROD (аналогично `UnreadChatMessagesCheck`).
**Bodies тоже без числа**.

Реальный рабочий цикл заданий (назначить → работа → апрув) чаще
всего идёт целиком на PROD (см. `SongEditorController`,
`SongEditor/store.js defaultTarget='remote`).

### 7. `StemJobsStuckCheck` (49 строк, WARNING)

**Проверяет**: `StemJob.countStuck(remoteDb, STALE_MINUTES=30) > 0`
(WAITING — поллер не забрал, или WORKING — пайплайн демукса не дошёл
до финализации).

**Ключ алерта**: `stemjobs.stuck`.

**БД**: PROD (задания создаются пользователями на прод-сайте).
Body без числа.

## Логика и Алгоритмы | Logic and Algorithms

### Тик и сборка снапшота

`MonitoringService.tick()` — `@Scheduled(fixedRate = 60_000L,
initialDelay = 20_000L)`:

1. `MonitorRegistry.checks.flatMap { check.run(ctx()) }` — все 7
   проверок за один проход; `ctx()` = `MonitorContext(localDb =
   WORKING_DATABASE, storageService = KSS_APP, storageApiClient =
   SAC_APP)`.
2. Исключение из `run()` не роняет тик: проверка превращается в
   отдельный `MonitorAlert(key = "check.<Name>.failure", severity =
   WARNING)`.
3. `snapshot = alerts.associateBy { it.key }` — по одному актуальному
   алерту на ключ; затем `pruneDismissed()` (удалить из `dismissed`
   ключи, которых нет в снапшоте) и `broadcast()` по SSE
   (`SseNotification.monitorAlerts`).
4. `currentDtos()` отдаёт алерты, отсортированные по
   `severity.rank` (по убыванию).

### Правила и пороги

- **Пороговые значения держатся в константах чека**:
  `LaneStalledCheck.STALL_THRESHOLD_MS = 2 мин`,
  `StemJobsStuckCheck.STALE_MINUTES = 30`,
  `ProdContainerCheck` — `monitorProdDownCriticalMinutes` (дефолт
  5 мин). У `UnreadChatMessagesCheck` и `SubmittedAssignmentsCheck`
  порога по времени нет.
- **`body` без числа, число в `detail`** — `MonitorAlert.contentHash()`
  считается по `severity.name|title|body`; `detail` в хэш не входит,
  поэтому меняющееся число не «мигает» read/unread на каждом тике.
- **Ключ стабилен между прогонами** — по нему связаны «прочитано» и
  ре-деривация `resolveAction`.
- **PROD-чеки сами открывают `Connection.remote()`** и закрывают его
  в `finally` (`MonitorContext` даёт только `localDb`).
- **`firstFailureAt` и `stalledSince` живут только в памяти** — при
  рестарте `karaoke-app` отсчёт начинается заново (by-design).

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

## Зависимости | Dependencies

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

- **Pass 486** (2026-09-27, spec `486-knowledge-domains-others`): секции приведены к шаблону компонента. Автор: agent (Karaoke).
- **Pass 404-410** (2026-09-09): Initial detailed. Автор: agent (Karaoke).