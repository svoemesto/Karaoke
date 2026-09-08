# Component: stats-cache

> **Домен**: [publishing](../domain.md)
> **Компонент**: `StatBySong` AtomicInteger-кеш для счётчиков главной
> страницы. Обновляется `StatsCacheScheduler` каждый час.

## Ответственность | Responsibility

Главная страница `karaoke-public` показывает счётчики: «Всего песен»,
«В открытом доступе», «Доступно по подписке» и т.д. Эти счётчики
**read-hot path** — каждый visitor генерирует запрос.

Прямой SQL-запрос `SELECT COUNT(*) FROM tbl_settings WHERE ...` при
каждом visitor — слишком дорого (миллионы запросов в день на 18k+
песен). Решение: **AtomicInteger-кеш**, обновляемый `StatsCacheScheduler`
раз в час.

**[WARN] Cache invalidation** в hot path — типичный источник багов.
Не меняйте этот компонент без полного понимания расписания scheduler'а.

## Интерфейсы и Контракты | Interfaces and Contracts

### `StatBySong` — singleton с AtomicInteger-полями

- **Где**: `karaoke-app/src/main/kotlin/.../StatsService.kt`.
- **Поля**:
  - `totalSongs: AtomicInteger` — всего песен в каталоге.
  - `openAccessSongs: AtomicInteger` — `AccessMode.OPEN`.
  - `premiumOnlySongs: AtomicInteger` — `AccessMode.PREMIUM_ONLY`.
  - `visitorLast24h: AtomicInteger` — посетителей за 24ч.
  - `realUsersLast24h: AtomicInteger` — реальных пользователей за 24ч.
- **Контракт**: значения обновляются атомарно через `set()` /
  `incrementAndGet()`. Read-only из контроллеров через `get()`.

### `StatsCacheScheduler` — Spring `@Scheduled`

- **Расписание**: `@Scheduled(cron = "0 0 * * * *")` — каждый час.
- **Поведение**:
  1. Читает из БД актуальные счётчики (5 SQL-запросов).
  2. Обновляет `StatBySong` через `set()`.
  3. Логирует в audit_log.
- **Failure mode**: если SQL упал, **предыдущие** значения остаются.
  Лучше показать устаревшие данные, чем вернуть 500.

### `GET /api/stats` — публичный endpoint

- **Возвращает**: snapshot `StatBySong.get()`.
- **Cache-Control**: `max-age=300` (5 минут на стороне CDN/браузера).

## Логика и Алгоритмы | Logic and Algorithms

### Алгоритм обновления `StatBySong`

```
Hour 0:00 (scheduler tick)
  ↓
[1] SELECT COUNT(*) FROM tbl_settings WHERE id_status = 6
    → StatBySong.totalSongs.set(N)
  ↓
[2] SELECT COUNT(*) FROM tbl_settings
    WHERE id_status = 6 AND is_exclusive = false AND publish_date <= now()
    → StatBySong.openAccessSongs.set(M)
  ↓
[3] SELECT COUNT(*) FROM tbl_settings
    WHERE id_status = 6 AND (is_exclusive = true OR publish_date > now())
    → StatBySong.premiumOnlySongs.set(K)
  ↓
[4] SELECT COUNT(*) FROM tbl_events
    WHERE created_at > now() - interval '24 hours'
    → StatBySong.visitorLast24h.set(V)
  ↓
[5] SELECT COUNT(*) FROM tbl_events
    WHERE created_at > now() - interval '24 hours' AND bot_score < 0.7
    → StatBySong.realUsersLast24h.set(R)
  ↓
[6] log.info("StatBySong updated: total={}, open={}, premium={}, v24={}, r24={}",
            N, M, K, V, R)
```

Все 5 запросов выполняются в одной транзакции (read-only), чтобы
получить **консистентный** snapshot.

### Алгоритм чтения из `/api/stats`

```kotlin
@GetMapping("/api/stats")
fun stats(): StatsDto = StatsDto(
    totalSongs = StatBySong.totalSongs.get(),
    openAccessSongs = StatBySong.openAccessSongs.get(),
    premiumOnlySongs = StatBySong.premiumOnlySongs.get(),
    visitorLast24h = StatBySong.visitorLast24h.get(),
    realUsersLast24h = StatBySong.realUsersLast24h.get(),
)
```

**[WARN]** Никогда не делать SQL внутри обработчика `/api/stats` —
только чтение из AtomicInteger. Иначе деградация на 1000%.

## Зависимости | Dependencies

- → [domain](../domain.md) — AR `SiteStats`, `SiteEvent`.
- → [dictionaries](dictionaries.md) — `BotScore` thresholds для фильтрации.
- → [catalog](../catalog/domain.md) — `Song.isExclusive`, `Song.publishDate`.
- → [caching context](../caching/domain.md) (TODO) — другие кеши проекта.

## Ловушки и предупреждения

**[WARN] Stale data до 1 часа** — если новая песня добавлена в 14:30,
а scheduler сработал в 14:00, счётчик обновится только в 15:00.
Это **нормально** для публичных счётчиков; для точных данных есть
другие API.

**[WARN] Cold start** — при перезапуске `karaoke-app` AtomicInteger
инициализируется нулями. До первого тика scheduler'а `/api/stats`
возвращает нули. Решение: в `init { }` блоке сделать eager-load из БД.

**[WARN] Concurrent updates** — `set()` атомарен, но **не** транзакционен.
Между чтением N (totalSongs) и M (openAccess) новый scheduler tick
может обновить только N. Решается **одной транзакцией** на шагах [1]-[5].

**[WARN] `bot_score > 0.7` — это фильтр для `real_users`, не для visitor'ов**.
VisitorLast24h учитывает ВСЕ визиты (включая ботов); realUsersLast24h —
только реальных. Не путать при показе метрик.

## Связанные ADR | Related ADRs

- ADR по caching (TODO, [caching domain](../caching/domain.md)).
