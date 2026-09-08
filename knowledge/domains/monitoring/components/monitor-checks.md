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
interface MonitorCheck {
    val name: String       // уникальное имя для grep'а в логах
    fun check(): MonitorAlert?  // null = OK, не-null = алерт
}
```

### `MonitorSeverity` enum

| Значение | Описание |
| --- | --- |
| `WARNING` | Деградация, не критично, on-call может посмотреть в течение часа. |
| `CRITICAL` | Прод не работает, немедленная реакция. |

## Текущие проверки (7 штук)

| Check | Что делает | Файл | Severity |
| --- | --- | --- | --- |
| `ProdContainerCheck` | HTTP-пинг `https://sm-karaoke.ru/` + JDBC-пинг прод-БД | `monitor/checks/ProdContainerCheck.kt` | CRITICAL |
| `RenderQueueStalledCheck` | Очередь рендера не stalled > 30 мин | `monitor/checks/RenderQueueStalledCheck.kt` | WARNING |
| `LaneStalledCheck` | Лейн очереди не stalled > 30 мин | `monitor/checks/LaneStalledCheck.kt` | WARNING |
| `TelegramPollingDisabledCheck` | Telegram polling работает | `monitor/checks/TelegramPollingDisabledCheck.kt` | CRITICAL |
| `UnreadChatMessagesCheck` | Нет непрочитанных сообщений > 1 час | `monitor/checks/UnreadChatMessagesCheck.kt` | WARNING |
| `SubmittedAssignmentsCheck` | Задания редактора не зависли в `submitted` > 24ч | `monitor/checks/SubmittedAssignmentsCheck.kt` | WARNING |
| `StemJobsStuckCheck` | Stem jobs не зависли > 2 часов | `monitor/checks/StemJobsStuckCheck.kt` | WARNING |

## Логика и Алгоритмы | Logic and Algorithms

### Алгоритм тика

```
MonitoringService.tick() @Scheduled(fixedDelay = 60_000ms)
  ↓
[1] for each check in MonitorRegistry.all():
      previousAlert = lastAlert[check.name]
  ↓
[2] currentAlert = check.check()
  ↓
[3] if currentAlert == null AND previousAlert != null:
        # Recovery
        log.info("infra.prod.checkName: RECOVERED from {}", previousAlert.severity)
        lastAlert[check.name] = null
  ↓
[4] if currentAlert != null AND previousAlert == null:
        # New alert
        log.warn("infra.prod.checkName: NEW {} {}", currentAlert.severity, currentAlert.message)
        lastAlert[check.name] = currentAlert
  ↓
[5] if currentAlert != null AND previousAlert != null:
        # Persistent — если WARNING > 5 мин, эскалация в CRITICAL (TODO)
        log.warn("infra.prod.checkName: STILL {} {}", currentAlert.severity, currentAlert.message)
```

### Детальный `ProdContainerCheck`

```kotlin
class ProdContainerCheck : MonitorCheck {
    override val name = "ProdContainer"
    override fun check(): MonitorAlert? {
        // [1] HTTP-пинг сайта
        val httpOk = try {
            val conn = URL("https://sm-karaoke.ru/").openConnection()
            conn.connectTimeout = 5_000
            conn.readTimeout = 5_000
            (conn.responseCode in 200..399)
        } catch (e: Exception) {
            log.warn("infra.prod.ping error='{}'", e.message)
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
            log.warn("infra.prod.db error='{}'", e.message)
            false
        }

        // [3] Если оба OK — null, иначе — CRITICAL alert
        return if (httpOk && dbOk) null
        else MonitorAlert(
            severity = MonitorSeverity.CRITICAL,
            message = "Prod down: http=$httpOk db=$dbOk"
        )
    }
}
```

## Зависимости | Dependencies

- → [domain](../domain.md) — `MonitorCheck`, `MonitorAlert`.
- → [log-categories](log-categories.md) — SLF4J-категории.
- → [rendering domain](../../rendering/domain.md) — `RenderQueueStalledCheck`.
- → [editorial domain](../../editorial/domain.md) — `SubmittedAssignmentsCheck`.

## Ловушки и предупреждения

**[WARN] Side-эффекты в `check()`** запрещены — только чтение и алерт.
Любая побочная логика ломает идемпотентность тиков.

**[WARN] Долгие проверки** (>30 сек) блокируют `MonitoringService.tick()`.
Каждая проверка должна быть быстрой (<5 сек).

**[WARN] Timeout** для HTTP/JDBC пингов обязателен (`connectTimeout=5s`,
`readTimeout=5s`). Без таймаута — зависание check'а → весь scheduler.

**[WARN] Recovery не должен пропускаться** — если проверка recovered,
обязательно `log.info("RECOVERED")` для отладки.

## Связанные фичи

- `288-prod-diagnostics-logging` — добавил SLF4J-категории
  `infra.prod.ping`, `infra.prod.db`.
- `289-fix-statbysong-cache-on-cold-start` — добавил `infra.cache.statbysong`.

## Связанные ADR | Related ADRs

- ADR по alerting (TODO, см. ADR backlog).
