# Data Model: 289-fix-statbysong-cache-on-cold-start

**Дата**: 2026-09-01
**Привязка**: [specs/289-fix-statbysong-cache-on-cold-start/spec.md](./spec.md)
**Phase**: 1 of `/speckit.plan`

> Сущности этой фичи — runtime-индекс БД, in-memory guards, логическая категория логгера. Не «данные в БД», а артефакты runtime/perf.

---

## Entity 1: `idx_songs_id_status_source_markers` (B-tree индекс)

**Описание**: Composite B-tree индекс на таблице `tbl_songs`, ускоряет запросы с фильтром `id_status >= 6 AND btrim(coalesce(source_markers, '')) != ''` (используется в `StatBySong.refreshCache()`).

### Атрибуты

| Поле | Тип | Описание | Пример |
|------|-----|----------|--------|
| `index_name` | String | Имя индекса | `idx_songs_id_status_source_markers` |
| `table_name` | String | Имя таблицы | `tbl_songs` |
| `columns` | String[] | Колонки индекса (порядок важен) | `[id_status, source_markers]` |
| `type` | String | Тип индекса | `btree` |
| `is_unique` | Boolean | Уникальный? | `false` |
| `storage_size_estimate_mb` | Number | Примерный размер (18k записей) | 1-3 MB |
| `creation_time_estimate_sec` | Number | Время создания (CONCURRENTLY) | < 5 сек |

### Жизненный цикл

1. **Создание** — через `CREATE INDEX CONCURRENTLY` в SQL-миграции `45_idx_songs_id_status_source_markers.sql`.
2. **Применение на новых контейнерах** — авто через `docker-entrypoint-initdb.d/` (volume mount в `docker-compose-database.yml`).
3. **Применение на существующем прод-контейнере** — пользователем через `docker exec` или прямой `psql`.
4. **Maintenance** — `VACUUM` / `REINDEX` при необходимости (отдельная задача, Out of Scope).
5. **Удаление** — отдельная миграция (Out of Scope, не нужно).

### Валидация
- `EXPLAIN ANALYZE select count(DISTINCT id) from tbl_songs where id_status >= 6 AND btrim(coalesce(source_markers, '')) != ''` → должен использовать `idx_songs_id_status_source_markers` (Index Scan).
- `pg_log` после применения — `duration:` для этого SQL < 500 мс (vs 4 сек до).

---

## Entity 2: Категория логгера `infra.cache.statbysong`

**Описание**: Строковый идентификатор для SLF4J `LoggerFactory.getLogger("infra.cache.statbysong")`. Используется как grep-маркер.

### Атрибуты

| Поле | Тип | Описание |
|------|-----|----------|
| `category_name` | String | `infra.cache.statbysong` |
| `log_level` | Enum | INFO / WARN (по умолчанию) |
| `used_in_class` | String | `StatBySong` |
| `purpose` | String | Логирование cold-start refresh и ошибок |
| `grep_marker` | String | `infra.cache.statbysong` |

### Сообщения (FR-006, FR-007, FR-008)

| Сообщение | Уровень | Когда |
|-----------|---------|-------|
| `cache:coldStart triggering background refresh` | WARN | При cold-start, когда `cachedTotal.get() < 0` и `refreshCache()` запущен в фоне |
| `cache:refreshed total=N collection=M freeNow=K subscriptionOnly=P inWork=Q durationMs=X` | INFO | После успешного `refreshCache()` (async или scheduled) |
| `cache:refreshFailed error="..." exceptionClass=...` | WARN | При exception в `refreshCache()` |

---

## Entity 3: `AtomicBoolean refreshing` (in-memory guard)

**Описание**: Volatile-write/read flag для предотвращения параллельных `refreshCache()` вызовов. Single-flight pattern.

### Атрибуты

| Поле | Тип | Описание |
|------|-----|----------|
| `state` | Boolean | `true` = refresh в процессе, `false` = можно запустить новый |
| `setter` | AtomicBoolean.compareAndSet | Thread-safe transition |
| `initial_value` | Boolean | `false` |

### State машина

```
  ┌──────┐  CAS(false → true)  ┌──────────┐  refreshCache() complete (finally)  ┌──────┐
  │ idle │ ─────────────────>  │ running  │ ───────────────────────────────────> │ idle │
  └──────┘                      └──────────┘                                       └──────┘
     ▲                              │
     │    if state == false: no-op   │
     └──────────────────────────────┘
```

### Валидация (SC-003)
- 5 параллельных HTTP-запросов на cold-start → **ровно 1** `refreshCache()` запущен, остальные 4 возвращают fallback.
- В `pg_log` — **ровно 1 набор из 3** SQL-запросов (`total`, `collection`, `freeNow`).

---

## Entity 4: `ScheduledExecutorService` (single-thread, daemon)

**Описание**: Single-thread executor для background cold-start refresh. Daemon-thread (не блокирует JVM shutdown).

### Атрибуты

| Поле | Тип | Описание |
|------|-----|----------|
| `thread_count` | Int | 1 (single-thread) |
| `thread_name` | String | `StatBySong-ColdStart` |
| `is_daemon` | Boolean | `true` |
| `executor_type` | Class | `java.util.concurrent.ScheduledThreadPoolExecutor` (или `Executors.newSingleThreadScheduledExecutor`) |

### Инициализация (в `companion object`)

```kotlin
companion object {
    private val bgExecutor: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "StatBySong-ColdStart").apply { isDaemon = true }
        }
}
```

### Lifecycle
- Создаётся при первом обращении к `StatBySong` (singleton object — lazy init через `companion object`).
- Daemon-thread → JVM shutdown не блокируется.
- Не требует явного `shutdown()` (daemon автоматически прерывается).

---

## Связи между сущностями

```
                    [B-tree индекс]
                    idx_songs_id_status_source_markers
                              │
                              │ ускоряет SQL в
                              ▼
[ScheduledExecutorService]──►[refreshCache()]──►[pg_log: duration < 500ms]
        │                          │
        │ background              │ логирует через
        │ cold-start              ▼
        │                  [infra.cache.statbysong]
        │
        └──[AtomicBoolean refreshing]──►(single-flight guard)
```

---

## Out of Data-Model Scope

- **`tbl_songs`** — существующая таблица, не меняется схема.
- **`tbl_settings` / `KaraokeProperties`** — НЕ используется для persist кеша (per D-3).
- **Файл `/tmp/statbysong.cache`** — НЕ создаётся (per D-3).
- **Spring `TaskScheduler`** — НЕ переиспользуется (per D-1).

---

## История

- Создан: 2026-09-01 (Phase 1)