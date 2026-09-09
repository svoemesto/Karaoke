# Component: monitor-core (базовые классы)

> **Домен**: [monitoring](../domain.md)
> **Компонента**: базовые классы `MonitorContext`, `MonitorCheck`,
> `MonitorAlert`, `MonitorRegistry`.

## Файлы

- `karaoke-app/.../monitor/MonitorContext.kt`
- `karaoke-app/.../monitor/MonitorCheck.kt`
- `karaoke-app/.../monitor/MonitorAlert.kt`
- `karaoke-app/.../monitor/MonitorRegistry.kt`
- `karaoke-app/.../monitor/MonitoringService.kt` (тик каждую минуту)
- `karaoke-app/.../monitor/MonitorSeverity.kt` (enum WARNING/CRITICAL)

## `MonitorContext`

**Общий доступ проверок мониторинга к БД/сервисам** — чтобы не тянуть
глобалы (`WORKING_DATABASE`/`KSS_APP`/`SAC_APP`) напрямую из каждой
проверки.

```kotlin
data class MonitorContext(
    val localDb: KaraokeConnection,
    val storageService: KaraokeStorageService,
    val storageApiClient: StorageApiClient,
)
```

## `MonitorCheck` (fun interface)

```kotlin
fun interface MonitorCheck {
    fun run(ctx: MonitorContext): List<MonitorAlert>
}
```

**Возвращает 0..N алертов**; пустой список = проблемы нет.

**Добавление новой проверки**: один `object : MonitorCheck` в
`monitor/checks/` + одна строка в `MonitorRegistry.checks`.

**Исключения из `run()`**: `MonitoringService.tick()` ловит и
превращает упавшую проверку в отдельный WARNING-алерт. Но
**ожидаемые ошибки** (сеть, БД) проверка должна обрабатывать сама
(см. `ProdContainerCheck`).

## `MonitorAlert`

**Одно системное сообщение** — аналог `HealthReport`
(`canResolve`/`problemText`/`solutionText`/`solutionActions`), но НЕ
привязано к конкретной `Song`. Это **проверки состояния проекта в
целом** (очередь рендера, доступность прод-сервера, горизонт
публикаций).

```kotlin
data class MonitorAlert(
    val key: String,                    // стабильный между прогонами
    val severity: MonitorSeverity,      // WARNING | CRITICAL
    val title: String,
    val body: String,
    val category: String,
    val detail: String? = null,
    val recommendations: String? = null,
    val resolveAction: (() -> Unit)? = null,
)
```

**`key` MUST быть стабильным** — по нему связывается состояние
"прочитано" (`MonitoringService.dismissed`) и ре-деривация
`resolveAction` при "Решить проблему".

**`detail`** — изменчивая часть текста (например, "недоступен уже N мин").
**Сознательно НЕ входит** в `contentHash()`, иначе сообщение
"мигало" бы read/unread на каждом тике планировщика.

## `MonitorRegistry`

```kotlin
object MonitorRegistry {
    val checks: List<MonitorCheck> = listOf(
        ProdContainerCheck,
        RenderQueueStalledCheck,
        LaneStalledCheck,
        // ...
    )
}
```

**Добавление новой проверки** — один `object : MonitorCheck` в
`monitor/checks/` + одна строка здесь.

## `MonitoringService`

`@Scheduled(fixedDelay = 60_000L)` (1 минута). Каждый тик:

```
1. for each check in MonitorRegistry.all():
    - previousAlert = lastAlert[check.name]
    - currentAlert = check.run(ctx)
    - если currentAlert != previousAlert → broadcast (SSE MONITOR_ALERTS)
2. обновить lastAlert
```

(по KDoc [monitor-checks.md](monitor-checks.md))

## `MonitorSeverity`

```kotlin
enum class MonitorSeverity {
    WARNING,    // деградация, on-call может посмотреть в течение часа
    CRITICAL,   // прод не работает, немедленная реакция
}
```

## Связь

- [monitor-checks.md](monitor-checks.md) — 7 проверок.
- [monitor-checks-detailed.md](monitor-checks-detailed.md) — детали.
- [sse domain](../../sse/domain.md) — broadcast `MONITOR_ALERTS`.
- [log-categories.md](log-categories.md) — `infra.prod.*` логи.

## Changelog

- **Pass 421** (2026-09-09): Initial. Автор: agent (Karaoke).