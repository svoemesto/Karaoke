# Component: log-categories

> **Домен**: [monitoring](../domain.md)
> **Компонент**: реестр SLF4J-категорий проекта Karaoke для grep'аемого
> мониторинга и диагностики.

## Ответственность | Responsibility

В Karaoke есть **соглашение о SLF4J-категориях**: компоненты логируют
через `infra.*` категории для удобного grep'а в логах. Это компонента —
единая точка регистрации всех категорий.

**[WARN]** Логирование через стандартный `LoggerFactory.getLogger(Class)`
(без `infra.*` категории) **запрещено** для компонент мониторинга.
Это ломает grep в `log-correlation.md` runbook.

## Интерфейсы и Контракты | Interfaces and Contracts

### Категории

| Категория | Где | Что логируется | Уровень по умолчанию |
| --- | --- | --- | --- |
| `infra.prod.ping` | `ProdContainerCheck` | HTTP-пинг `sm-karaoke.ru/` | WARN при ошибке, INFO при recovery |
| `infra.prod.db` | `ProdContainerCheck` | JDBC-пинг прод-БД | WARN при ошибке, INFO при recovery |
| `infra.cache.statbysong` | `karaoke-web/StatBySong.kt` (cacheLog) | Cold-start, refresh, ошибки | WARN при cold-start, INFO при success, WARN при failure |
| `infra.queue.render` | **не реализована** | Render queue stalled/lane stalled | (Pass 343+) |
| `infra.telegram.polling` | **не реализована** | Telegram polling state | (Pass 343+) |

### Конвенция имён

- `infra.<subsystem>.<feature>` — где:
  - `infra` — префикс для всех мониторинговых логов.
  - `<subsystem>` — подсистема (`prod`, `cache`, `queue`, `telegram`).
  - `<feature>` — конкретная проверка/событие (`ping`, `db`,
    `statbysong`, `render`, `polling`).

**[WARN]** Не путать с категориями `com.svoemesto.*` — это бизнес-логика,
не мониторинг. Мониторинг — только `infra.*`.

## Логика и Алгоритмы | Logic and Algorithms

### Пример: `infra.prod.ping` (ProdContainerCheck)

```kotlin
private val log = LoggerFactory.getLogger("infra.prod.ping")

fun checkPing(): Boolean {
    return try {
        val conn = URL("https://sm-karaoke.ru/").openConnection()
        conn.connectTimeout = 5_000
        conn.readTimeout = 5_000
        val ok = (conn.responseCode in 200..399)
        if (!ok) log.warn("ping failed: statusCode={}", conn.responseCode)
        ok
    } catch (e: Exception) {
        log.warn("ping error='{}'", e.message)
        false
    }
}
```

**Что ищем в логах**:

- `grep "infra.prod.ping" /var/log/karaoke-app.log` — все события пинга.
- `grep "infra.prod.ping" /var/log/karaoke-app.log | grep WARN` — только ошибки.

### Пример: `infra.cache.statbysong` (StatBySong, спека 289)

```kotlin
private val log = LoggerFactory.getLogger("infra.cache.statbysong")

fun refreshCache() {
    if (refreshing.compareAndSet(false, true)) {
        try {
            log.warn("cache:coldStart triggering background refresh")
            // ... load from DB ...
            log.info("cache:refreshed total={} durationMs={}", total, durationMs)
        } catch (e: Exception) {
            log.warn("cache:refreshFailed error='{}'", e.message)
        } finally {
            refreshing.set(false)
        }
    }
}
```

**Что ищем в логах**:

- `grep "infra.cache.statbysong" /var/log/karaoke-web.log | grep coldStart` —
  были ли холодные старты.
- `grep "infra.cache.statbysong" /var/log/karaoke-web.log | grep refreshFailed` —
  были ли ошибки refresh.

## Зависимости | Dependencies

- → [domain](../domain.md) — `MonitorCheck`, `MonitorAlert`.
- → [monitor-checks](monitor-checks.md) — где категории используются.

## Runbook

[`docs/ops/log-correlation.md`](../../../../docs/ops/log-correlation.md) —
полная карта логов с примерами grep-маркеров.

## Ловушки и предупреждения

**[WARN] Логирование через `LoggerFactory.getLogger(Class)`** в
компонентах мониторинга запрещено. Используйте **строковую категорию**
`infra.*` вместо имени класса.

**[WARN] Регистрация новой категории** должна быть зафиксирована в
таблице выше. Несогласованная категория = невидимый grep.

**[WARN] Уровень WARN для восстановления (recovery)** — не INFO.
Восстановления важны для анализа инцидентов, и должны быть видимы
в `tail` лога.

## Связанные ADR | Related ADRs

- ADR по logging conventions (TODO).
