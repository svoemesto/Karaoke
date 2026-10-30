# Component: monitor-checks

> **Домен**: [monitoring](../domain.md)
> **Компонент**: каталог всех MonitorCheck'ов и связанных с ними алертов.

## Ответственность | Responsibility

Эта компонента — единая точка регистрации всех проверок мониторинга.
Каждая проверка реализует `MonitorCheck` интерфейс и регистрируется
в `MonitorRegistry`. Тикают раз в минуту через `MonitoringService`.

## Интерфейсы и Контракты | Interfaces and Contracts

### `MonitorCheck` интерфейс

```kotlin
fun interface MonitorCheck {
    fun run(ctx: MonitorContext): List<MonitorAlert>  // пусто = OK
}
```

**Возвращает 0..N алертов**; пустой список = проблемы нет. Исключения из
`run()` ловит `MonitoringService.tick()` и превращает в отдельный
WARNING-алерт `check.<ИмяКласса>.failure`; ожидаемые ошибки (сеть, БД)
проверка обрабатывает сама.

**Историческая справка**: до Pass 472 интерфейс описывался как
`val name` + `check(): MonitorAlert?` — такого интерфейса в коде нет
(см. `monitor/MonitorCheck.kt:24-26`).

### `MonitorSeverity` enum

| Значение | Описание |
| --- | --- |
| `INFO` | Информационное сообщение (в SSE/REST попадает, но не тревожит). |
| `WARNING` | Деградация, не критично, on-call может посмотреть в течение часа. |
| `ERROR` | Ошибка проверки (сама проверка не смогла ответить). |
| `CRITICAL` | Прод не работает, немедленная реакция. |

### Маппинг severity → уровень журнала (BR-630, ADR `local-0014`)

| Severity | Уровень SLF4J |
| --- | --- |
| `INFO` | INFO |
| `WARNING` | WARN |
| `ERROR` | ERROR |
| `CRITICAL` | ERROR |

Уровня CRITICAL в SLF4J нет, а внешних оповещений у мониторинга нет —
уровень лога остаётся **единственным машинно-читаемым признаком
эскалации** для оператора. Проверки с единственным уровнем (WARNING)
логируют свои алерты на WARN — это согласовано с тем же правилом.

## Текущие проверки (8 штук)

| Check | Что делает | Файл | Severity |
| --- | --- | --- | --- |
| `ProdContainerCheck` | HTTP-пинг `https://sm-karaoke.ru/` + JDBC-пинг прод-БД | `monitor/checks/ProdContainerCheck.kt` | WARNING → CRITICAL |
| `RenderQueueStalledCheck` | Очередь рендера не stalled > 30 мин | `monitor/checks/RenderQueueStalledCheck.kt` | WARNING |
| `LaneStalledCheck` | Лейн очереди не stalled > 30 мин | `monitor/checks/LaneStalledCheck.kt` | WARNING |
| `TelegramPollingDisabledCheck` | Telegram polling работает | `monitor/checks/TelegramPollingDisabledCheck.kt` | WARNING |
| `UnreadChatMessagesCheck` | Нет непрочитанных сообщений > 1 час | `monitor/checks/UnreadChatMessagesCheck.kt` | INFO |
| `SubmittedAssignmentsCheck` | Задания редактора не зависли в `submitted` > 24ч | `monitor/checks/SubmittedAssignmentsCheck.kt` | INFO |
| `StemJobsStuckCheck` | Stem jobs не зависли > 30 минут (`STALE_MINUTES`) | `monitor/checks/StemJobsStuckCheck.kt` | WARNING |
| `HeapUsageCheck` | Загрузка heap JVM (пороги 75 % / 90 %) | `monitor/checks/HeapUsageCheck.kt` | WARNING → CRITICAL |

## Логика и Алгоритмы | Logic and Algorithms

### Алгоритм тика

```
MonitoringService.tick() @Scheduled(fixedDelay = 60_000ms, initialDelay = 20_000ms)
  ↓
[1] for each check in MonitorRegistry.checks:
      try { check.run(ctx()) }
      catch (e: Exception) { listOf(checkFailureAlert(check, e)) }   # key = check.<Имя>.failure, WARNING
  ↓
[2] snapshot = alerts.associateBy { it.key }     # один актуальный алерт на ключ
  ↓
[3] pruneDismissed()                             # ключи dismissed, которых больше нет в снапшоте
  ↓
[4] broadcast()                                  # SSE MONITOR_ALERTS всем вкладкам webvue3
```

**Тик — `fixedDelay`, а не `fixedRate`**, и у него single-flight guard
(`tickRunning`): пересекающийся тик пропускается с записью в журнал
(BR-135). Тик не персистится — при перезапуске `karaoke-app` отсчёт
длительности сбоя начинается заново (by design: нет персистентности
monitor-состояния).

**Ключ алерта** — стабильный между прогонами: по нему снапшот
связывает состояние «прочитано» и ре-деривацию `resolveAction`.
**Изменяющиеся числа (проценты, счётчики, «уже N мин») живут в
`detail`**, а не в `body`/`title` — иначе `contentHash()` менялся бы на
каждом тике и сообщение «мигало» бы read/unread.

### Детальный `ProdContainerCheck`

```kotlin
object ProdContainerCheck : MonitorCheck {
    override fun run(ctx: MonitorContext): List<MonitorAlert> {
        // [1] HTTP-пинг сайта
        val httpOk = try {
            val conn = URL("https://sm-karaoke.ru/").openConnection()
            conn.connectTimeout = 5_000
            conn.readTimeout = 5_000
            (conn.responseCode in 200..399)
        } catch (e: Exception) {
            pingLog.warn("infra.prod.ping error='{}'", e.message)
            false
        }

        // [2] JDBC-пинг прод-БД
        val dbOk = try {
            dataSource.connection.use { conn ->
                conn.createStatement().use { stmt ->
                    stmt.queryTimeout = 5
                    stmt.executeQuery("SELECT 1").next()
                }
            }
        } catch (e: Exception) {
            dbLog.warn("infra.prod.db error='{}'", e.message)
            false
        }

        // [3] Если оба OK — пустой список, иначе — алерт
        return if (httpOk && dbOk) emptyList()
        else listOf(MonitorAlert(...))
    }
}
```

## Зависимости | Dependencies

- → [domain](../domain.md) — `MonitorCheck`, `MonitorAlert`.
- → [log-categories](log-categories.md) — SLF4J-категории.
- → [monitor-core](monitor-core.md) — `MonitorContext`, `MonitorRegistry`.
- → [monitor-checks-detailed](monitor-checks-detailed.md) — детали по каждому чеку.
- → [rendering domain](../../rendering/domain.md) — `RenderQueueStalledCheck`.
- → [editorial domain](../../editorial/domain.md) — `SubmittedAssignmentsCheck`.

## Ловушки и предупреждения

**[WARN] Side-эффекты в `run()`** запрещены — только чтение и алерт.
Любая побочная логика ломает идемпотентность тиков.

**[WARN] Долгие проверки** (>30 сек) блокируют `MonitoringService.tick()`.
Каждая проверка должна быть быстрой (<5 сек).

**[WARN] Timeout** для HTTP/JDBC пингов обязателен (`connectTimeout=5s`,
`readTimeout=5s`). Без таймаута — зависание check'а → весь scheduler.

**[WARN] Recovery не должен пропускаться** — если проверка recovered,
обязательно `log.info("…recovered…")` для отладки.

**[WARN] Ошибка проверки ≠ «проблем нет»**: исключение из `run()`
превращается в WARNING-алерт `check.<Имя>.failure`, а не в пустой список
(BR-134).

## Связанные фичи

- `288-prod-diagnostics-logging` — добавил SLF4J-категории
  `infra.prod.ping`, `infra.prod.db`.
- `289-fix-statbysong-cache-on-cold-start` — добавил `infra.cache.statbysong`.
- `676-oom-karaoke-app` (KARAOKE-245, BR-630) — добавил `HeapUsageCheck`
  (загрузка heap JVM) и правило маппинга severity → уровень лога.

## Связанные ADR | Related ADRs

- [local-0014 — превентивные меры против OOM в `karaoke-app`](../../../adr/local-0014-heap-memory-guardrails-karaoke-app.md) —
  источник данных о heap, место хранения дампов, маппинг severity → уровень лога.
- ADR по внешнему alerting (TODO, см. ADR backlog).

## Changelog

- **Спека 676** (2026-10-30, KARAOKE-245, BR-630): интерфейс приведён к
  фактическому (`fun interface MonitorCheck { fun run(ctx) }`), алгоритм
  тика — к фактическому (`fixedDelay` + single-flight, снапшот по
  `it.key`), добавлена 8-я проверка `HeapUsageCheck` и таблица маппинга
  severity → уровень лога. Автор: agent (Karaoke).
