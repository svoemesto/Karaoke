# Component: caching-patterns

> **Домен**: [caching](../domain.md)
> **Компонент**: каталог паттернов кеширования, используемых в Karaoke.

## Ответственность | Responsibility

Эта компонента — единая точка регистрации всех caching patterns,
применяемых в Karaoke. Каждый паттерн имеет **имя**, **где** применяется,
**зачем**, и **код-пример**. Новые кеши должны следовать одному из
существующих паттернов.

**[WARN]** Кастомные ad-hoc кеши (без следования паттернам) —
источник багов (race condition, stale data, memory leak). Не добавлять
без ревью этой страницы.

## Интерфейсы и Контракты | Interfaces and Contracts

### Шесть паттернов

| # | Паттерн | Когда использовать |
| --- | --- | --- |
| 1 | In-memory `AtomicInteger` | Read-mostly счётчики, обновляются редко. |
| 2 | Cron-обновление | Свежесть данных не критична, можно обновлять раз в час. |
| 3 | Dirty-флаг | Счётчики зависят от других сущностей (Song, Album, Author). |
| 4 | Денормализация в БД | SUM по 100+ записям — медленно, денормализация ускоряет. |
| 5 | Async cold-start | HTTP-тред не должен блокироваться на refresh > 100 мс. |
| 6 | Single-flight guard | Предотвращение дублирующих refresh при concurrent запросах. |

## Логика и Алгоритмы | Logic and Algorithms

### Паттерн 1: In-memory `AtomicInteger`

```kotlin
class StatBySong {
    val totalSongs = AtomicInteger(0)
    val openAccessSongs = AtomicInteger(0)
    val premiumOnlySongs = AtomicInteger(0)

    fun snapshot(): Snapshot = Snapshot(
        total = totalSongs.get(),
        open = openAccessSongs.get(),
        premium = premiumOnlySongs.get()
    )
}
```

**Когда**: read-mostly, lock-free read, write только при refresh.
**Где**: `StatBySong.kt`.

### Паттерн 2: Cron-обновление

```kotlin
@Component
class StatsCacheScheduler {
    @Scheduled(cron = "0 0 * * * *")  // каждый час
    fun refresh() {
        // [1] SQL aggregate
        // [2] AtomicInteger.set()
    }
}
```

**Когда**: данные меняются редко (раз в день/час), допустима
stale до 1 часа.
**Где**: `StatsCacheScheduler.kt`.

### Паттерн 3: Dirty-флаг

```kotlin
class StatBySong {
    private val dirty = AtomicBoolean(false)

    fun markDirty() {
        dirty.set(true)
        // Scheduler раз в минуту проверяет dirty и запускает refresh
    }

    @Scheduled(fixedDelay = 60_000)
    fun checkDirtyAndRefresh() {
        if (dirty.compareAndSet(true, false)) {
            refresh()
        }
    }
}
```

**Когда**: нужна **немедленная инвалидация** при изменении сущности,
но refresh отложен (не в hot path).
**Где**: `StatBySong.dirty`, вызывается из `karaoke-app` при
save/update.

**[WARN]** `markDirty()` должен быть **дешёвым** (atomic boolean set).
Никаких блокировок.

### Паттерн 4: Денормализация в БД

```sql
-- spec 286: tbl_authors.total_songs_count / ready_songs_count
ALTER TABLE tbl_authors ADD COLUMN total_songs_count INT DEFAULT 0;
ALTER TABLE tbl_authors ADD COLUMN ready_songs_count INT DEFAULT 0;

CREATE INDEX idx_authors_total_songs ON tbl_authors(total_songs_count);
```

**Когда**: full-scan COUNT/SUM медленный (>50 мс на 100+ записях).
**Где**: `tbl_authors.{total,ready}_songs_count`.
**Обновление**: trigger или scheduler пересчитывает после изменений
в `tbl_settings`.

**Эффект**: SUM по 126 авторам ~2 мс вместо full-scan ~200 мс.

### Паттерн 5: Async cold-start

```kotlin
@Component
class StatBySong {
    private val bgExecutor = Executors.newSingleThreadScheduledExecutor()
    val frozenAtStartup = AtomicBoolean(true)

    init {
        // [1] При старте возвращаем fallback (0)
        // [2] Асинхронно загружаем реальные данные
        bgExecutor.submit { refreshCache() }
    }

    fun snapshot(): Snapshot {
        if (frozenAtStartup.get()) {
            log.warn("cache:coldStart triggering background refresh")
        }
        return snapshot
    }
}
```

**Когда**: HTTP-тред не должен блокироваться > 100 мс на startup.
**Где**: `StatBySong` после спеки 289.
**Контракт**: `snapshot()` возвращает **fallback** (0) пока
`frozenAtStartup=true`. Refresh в фоне, после — `frozenAtStartup.set(false)`.

**[WARN]** Снапшот до окончания refresh — **не точные** данные.
UI должен явно показывать «Загрузка…» или «Данные устарели».

### Паттерн 6: Single-flight guard

```kotlin
class StatBySong {
    private val refreshing = AtomicBoolean(false)

    fun refreshCache() {
        if (refreshing.compareAndSet(false, true)) {
            try {
                // ... тяжёлый refresh ...
            } finally {
                refreshing.set(false)
            }
        } else {
            // Другой поток уже обновляет, ничего не делаем
            log.info("cache:refresh already in progress, skipping")
        }
    }
}
```

**Когда**: refresh дорогой (>1 сек), concurrent запросов > 1.
**Где**: `StatBySong.refreshCache()`.

**[WARN]** Если `refreshCache()` может занимать > 60 сек, добавить
**timeout**: `if (!refreshing.compareAndSet(false, true)) skip; else
withTimeout(60_000) { ... }`.

## Зависимости | Dependencies

- → [domain](../domain.md) — все кеши.
- → [monitoring log-categories](../monitoring/components/log-categories.md —
  SLF4J-категории для логирования.
- → [stats domain](../../stats/domain.md) — `StatsCacheScheduler`
  обслуживает stats.

## Ловушки и предупреждения

**[WARN] Ad-hoc кеш без паттерна** — источник багов. Использовать
только эти 6 паттернов.

**[WARN] In-memory кеш не переживает restart** — данные пересчитываются
при cold-start (паттерн 5).

**[WARN] Cron-обновление не реагирует на изменения** мгновенно —
максимум 1 час stale. Если нужна немедленная инвалидация — паттерн 3
(dirty-флаг).

**[WARN] Single-flight guard не спасает от долгого refresh** —
нужен timeout.

**[WARN] Денормализация требует синхронизации** — если забыть
обновить денормализованное поле, будет рассинхрон с source of truth.

## Связанные ADR | Related ADRs

- ADR по caching strategy (TODO).
