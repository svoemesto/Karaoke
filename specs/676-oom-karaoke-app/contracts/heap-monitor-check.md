# Contract: проверка загрузки heap (`HeapUsageCheck`)

> **Ветка**: `676-oom-karaoke-app` | **Дата**: 2026-10-30
> **Спека**: FR-014…FR-019, SC-005 | **План**: [plan.md](../plan.md)

Контракт новой 8-й проверки мониторинга в домене `monitoring`.

---

## 1. Точка регистрации

| Что | Где | Как |
|---|---|---|
| Реализация | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/monitor/checks/HeapUsageCheck.kt` | `object HeapUsageCheck : MonitorCheck` |
| Регистрация | `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/monitor/MonitorRegistry.kt` | одна строка в `MonitorRegistry.checks` |

Тик — общий для всех проверок: `MonitoringService.tick()`,
`@Scheduled(fixedDelay = 60_000L, initialDelay = 20_000L)`
(`MonitoringService.kt:61`) → **раз в минуту** (FR-014).

---

## 2. Интерфейс

```kotlin
fun interface MonitorCheck {
    fun run(ctx: MonitorContext): List<MonitorAlert>
}
```

`HeapUsageCheck` не использует `ctx` (ни БД, ни сети, ни хранилища не касается) —
это и есть требование «без побочных эффектов» (FR-016).

---

## 3. Конфигурация порогов

| Переменная | Дефолт | Диапазон | Источник значения по умолчанию |
|---|---|---|---|
| `MONITOR_HEAP_WARN_PERCENT` | `75` | 1..99 | `System.getenv` (ADR `local-0001`) |
| `MONITOR_HEAP_CRITICAL_PERCENT` | `90` | 2..100 | `System.getenv` (ADR `local-0001`) |

Правила разбора:

- `System.getenv(name)?.trim()?.toIntOrNull()` → при `null`/не-числе/вне диапазона —
  дефолт;
- `warn < critical` — иначе дефолты (75/90);
- значения **явно прописаны** в `deploy/docker-compose-app.yml` (блок
  `environment:`) → видны в репозитории рядом с `mem_limit`/`-Xmx6g`;
- **новых персистентных ключей нет** (FR-019): ни в `KaraokeProperties`, ни в БД.

---

## 4. Формат логов

Категория: **`infra.monitor.heap`** (строковая — по конвенции
`knowledge/domains/monitoring/components/log-categories.md`; имя класса
запрещено для мониторинга).

Формат — `event:key=value` (ADR `local-0005`).

| Событие | Уровень | Сообщение |
|---|---|---|
| Тик | INFO | `heap:usage usedBytes=… maxBytes=… percent=…` |
| Превышение порога | WARN (WARNING) / ERROR (CRITICAL) | `heap:thresholdExceeded percent=… threshold=… severity=…` |
| Возврат в норму | INFO | `heap:recovered percent=…` |
| Не удалось прочитать метрику | WARN | `heap:readFailed error="…"` |

Требования:

- строка INFO о текущей загрузке пишется **каждый тик** (SC-005: «не реже раза
  в минуту»), а не только при превышении;
- recovery-сообщение пишется **только на переходе** из «алерт» в «норма»;
- секретов и credentials в записи нет (Constitution VIII.5) — только числа.

---

## 5. Контракт алерта

```kotlin
MonitorAlert(
    key = "monitor.heap.usage",     // стабильный между прогонами (monitor-core.md:85)
    severity = WARNING | CRITICAL,
    title = "Высокая загрузка heap JVM",
    body = "…",
    category = "Мониторинг",
    detail = "used=… max=… percent=…",   // НЕ входит в contentHash()
    recommendations = "…",
)
```

| Условие | Severity |
|---|---|
| `percent >= criticalPercent` | `CRITICAL` |
| `warnPercent <= percent < criticalPercent` | `WARNING` |
| `percent < warnPercent` | алерта нет (`emptyList()`) |

`resolveAction` — `null`: автоматического «лечения» у загрузки heap нет, и
кнопка «Решить проблему» не должна показываться (см. `MonitorAlert.canResolve`).

---

## 6. Канал доставки

Существующий, без новой инфраструктуры (FR-018):

1. журнал (SLF4J, категория `infra.monitor.heap`);
2. SSE `MONITOR_ALERTS` — `MonitoringService.broadcast()`
   (`MonitoringService.kt:106-112`);
3. `GET /api/monitor/alerts` — `MonitoringController`.

Внешняя доставка (Telegram/email) — **вне объёма**.

---

## 7. Состояние

| Что | Где | Сброс |
|---|---|---|
| «алерт был» (`alerting`) | поле `@Volatile` в объекте проверки | при рестарте контейнера (ожидаемо, FR-019 + § Edge Cases спеки) |
| «прочитано/отписано» | существующий `MonitoringService.dismissed` (не меняем) | — |

Новых персистентных ключей **не появляется**.

---

## 8. Инварианты (проверяемые тестом)

1. `percent < warnPercent` → пустой список алертов.
2. `warnPercent <= percent < criticalPercent` → ровно один алерт `WARNING`.
3. `percent >= criticalPercent` → ровно один алерт `CRITICAL`.
4. Два вызова подряд при превышении → **один и тот же** `key` (иначе снапшот
   мониторинга «мигает»).
5. При переходе из превышения в норму — recovery-сообщение в логе; при
   следующем превышении — снова алерт.
6. Выполнение укладывается в 1 с (с запасом относительно бюджета тика 5 с).
7. Проверка не трогает БД/сеть/файлы (никаких побочных эффектов).
