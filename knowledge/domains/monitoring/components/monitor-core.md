# Component: monitor-core (базовые классы)

> **Домен**: [monitoring](../domain.md)
> **Компонента**: базовые классы `MonitorContext`, `MonitorCheck`,
> `MonitorAlert`, `MonitorRegistry`.


## Ответственность | Responsibility


базовые классы `MonitorContext`, `MonitorCheck`, `MonitorAlert`, `MonitorRegistry`.

## Файлы

- `karaoke-app/.../monitor/MonitorCheck.kt` — `MonitorContext`
  (data class) + `MonitorCheck` (fun interface).
- `karaoke-app/.../monitor/MonitorAlert.kt`
- `karaoke-app/.../monitor/MonitorAlertDto.kt` — сериализуемое
  представление для фронта (SSE + REST).
- `karaoke-app/.../monitor/MonitorRegistry.kt`
- `karaoke-app/.../monitor/MonitoringService.kt` (тик раз в минуту)
- `karaoke-app/.../monitor/MonitorSeverity.kt` (enum
  `INFO`/`WARNING`/`ERROR`/`CRITICAL`)

## Интерфейсы и Контракты | Interfaces and Contracts

### `MonitorContext`

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

### `MonitorCheck` (fun interface)

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

### `MonitorAlert`

**Одно системное сообщение** — аналог `HealthReport`
(`canResolve`/`problemText`/`solutionText`/`solutionActions`), но НЕ
привязано к конкретной `Song`. Это **проверки состояния проекта в
целом** (очередь рендера, доступность прод-сервера, горизонт
публикаций).

```kotlin
data class MonitorAlert(
    val key: String,                    // стабильный между прогонами
    val severity: MonitorSeverity,      // INFO | WARNING | ERROR | CRITICAL
    val title: String,
    val body: String,
    val category: String,
    val detail: String? = null,
    val recommendations: String? = null,
    val resolveAction: (() -> Unit)? = null,
) {
    val canResolve: Boolean get() = resolveAction != null
    fun contentHash(): String            // hash(severity.name|title|body)
    fun executeResolve()                 // resolveAction?.invoke()
    fun toDto(read: Boolean): MonitorAlertDto
}
```

**`key` MUST быть стабильным** — по нему связывается состояние
"прочитано" (`MonitoringService.dismissed`) и ре-деривация
`resolveAction` при "Решить проблему".

**`detail`** — изменчивая часть текста (например, "недоступен уже N мин").
**Сознательно НЕ входит** в `contentHash()` (хэш считается только по
`severity.name|title|body`), иначе сообщение "мигало" бы read/unread
на каждом тике планировщика.

### `MonitorAlertDto`

Сериализуемое представление `MonitorAlert` для фронта (SSE + REST) —
**без лямбды** `resolveAction`; несёт `canResolve: Boolean`,
`contentHash: String`, `read: Boolean` и `severityName`/`color`.
По образцу `HealthReportDTO`.

### `MonitorRegistry`

```kotlin
object MonitorRegistry {
    val checks: List<MonitorCheck> = listOf(
        ProdContainerCheck,
        RenderQueueStalledCheck,
        LaneStalledCheck,
        TelegramPollingDisabledCheck,
        UnreadChatMessagesCheck,
        SubmittedAssignmentsCheck,
        StemJobsStuckCheck,
    )
}
```

**Добавление новой проверки** — один `object : MonitorCheck` в
`monitor/checks/` + одна строка здесь.

### `MonitorSeverity`

```kotlin
enum class MonitorSeverity(val rank: Int, val color: String) {
    INFO(0, "#4CAF50"),
    WARNING(1, "#FFC107"),
    ERROR(2, "#F44336"),
    CRITICAL(3, "#D50000"),
}
```

`rank` — для вычисления максимальной серьёзности среди активных
сообщений (цвет «светофора» в хедере webvue3); `color` — HEX для
маркировки строки в модалке. Проверки сейчас используют `INFO`,
`WARNING` и `CRITICAL` (см.
[monitor-checks-detailed.md](monitor-checks-detailed.md)).

## Логика и Алгоритмы | Logic and Algorithms

### `MonitoringService` — тик раз в минуту

`@Component`, `@Scheduled(fixedRate = 60_000L, initialDelay = 20_000L)`
(fixedRate, не fixedDelay; старт через 20 с после запуска app).

```kotlin
fun tick() {
    val alerts = MonitorRegistry.checks.flatMap { check ->
        try { check.run(ctx()) }
        catch (e: Exception) { listOf(checkFailureAlert(check, e)) }
    }
    snapshot = alerts.associateBy { it.key }
    pruneDismissed()
    broadcast()
}
```

1. Все 7 проверок прогоняются за один проход; `ctx()` собирает
   `MonitorContext(WORKING_DATABASE, KSS_APP, SAC_APP)`.
2. Упавшая проверка не роняет тик — превращается в WARNING-алерт
   `key = "check.<Name>.failure"`.
3. `snapshot: Map<String, MonitorAlert>` — по одному актуальному
   алерту на ключ (перезаписывается целиком).
4. `pruneDismissed()` удаляет из `dismissed` ключи, которых больше нет
   в снапшоте; `dismissed` персистится в `KaraokeProperties`
   `"monitorDismissed"` (JSON), чтобы разобранные предупреждения не
   всплывали после рестарта.
5. `broadcast()` шлёт `SseNotification.monitorAlerts(currentDtos())`
   всем вкладкам webvue3; `currentDtos()` сортирует по
   `severity.rank` по убыванию и выставляет `read` сравнением
   `dismissed[key] == alert.contentHash()`.
6. `markRead(key)` / `markUnread(key)` / `reset()` меняют `dismissed`
   и персистят; `resolve(key)` берёт лямбду из свежего снапшота
   (`takeIf { it.canResolve }?.executeResolve()`) и сразу вызывает
   `tick()`.

Сервис работает только пока запущен `karaoke-app` — это не 24/7
аптайм-монитор.

## Зависимости | Dependencies

- [monitor-checks.md](monitor-checks.md) — 7 проверок.
- [monitor-checks-detailed.md](monitor-checks-detailed.md) — детали.
- [sse domain](../../sse/domain.md) — broadcast `MONITOR_ALERTS`.
- [log-categories.md](log-categories.md) — `infra.prod.*` логи.

## Changelog

- **Pass 486** (2026-09-27, spec `486-knowledge-domains-others`): секции приведены к шаблону компонента. Автор: agent (Karaoke).
- **Pass 421** (2026-09-09): Initial. Автор: agent (Karaoke).