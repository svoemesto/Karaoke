# Research: Технические решения для спеки 289-fix-statbysong-cache-on-cold-start

**Дата**: 2026-09-01
**Привязка**: [specs/289-fix-statbysong-cache-on-cold-start/spec.md](./spec.md)

> Phase 0 output — резюме research по 4 архитектурным вопросам из спеки (D-1..D-4) и best practices.

---

## Решение 1 (D-1): Async refresh — Spring `TaskScheduler` vs новый `ScheduledExecutorService`

### Decision
Использовать **`ScheduledExecutorService`** (отдельный, single-thread, daemon), инстанцируемый в `companion object` `StatBySong`. Не использовать Spring `@Async` (требует `@EnableAsync` + overhead) и не переиспользовать существующий Spring `TaskScheduler` (нет public access к нему в `karaoke-web` без `@Autowired`).

### Rationale
- **`@Async`** + `@EnableAsync`: требует Spring Boot конфигурацию, AOP proxy — overhead для простой операции. Избыточно для single-thread cold-start refresh.
- **Spring `TaskScheduler`**: в Karaoke-проекте используется (`@Scheduled` в `StatsCacheScheduler.refreshHourly`), но он привязан к Spring lifecycle. Получить ссылку на него из `StatBySong` (singleton object, не Spring bean) — неудобно, требует `@Autowired` или service-locator.
- **`ScheduledExecutorService` в `companion object`**: минимальный overhead, прямой контроль lifecycle, daemon-thread (не блокирует JVM shutdown). Идиоматично для Kotlin singleton с фоновой задачей.

```kotlin
companion object {
    private val bgExecutor: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "StatBySong-ColdStart").apply { isDaemon = true }
        }
}
```

### Alternatives Considered
- **`@Async`** с `@EnableAsync` на karaoke-web — rejected: overhead + конфигурация.
- **Spring `TaskScheduler` autowired** — rejected: сложная интеграция в singleton object.
- **`CompletableFuture.runAsync()` без явного Executor** — rejected: использует ForkJoinPool.commonPool, может конкурировать с другими задачами.

---

## Решение 2 (D-2): Fallback значение при cold-start

### Decision
Возвращать **`0`** для всех 5 getter'ов (`getCountSongsTotal`, `getCountSongsCollection`, и т.д.) при cold-start (`cachedTotal.get() < 0`). Не использовать `-1` или сохранять предыдущие значения.

### Rationale
- **`0`** — безопасное fallback значение:
  - Главная страница показывает «0 песен» / «0 в закромах» — корректно (не 500-ошибка).
  - Через 12 сек (когда background refresh завершится) — значения обновятся автоматически.
  - UI не сломается (нет отрицательных чисел в счётчиках).
- **`-1`** (текущее поведение) — anti-pattern: UI может не отрендерить `{{-1}}`, что лучше `0`.
- **Persist последних значений** — отдельная фича (FR-010 убрал persist из scope). Не нужен для cold-start — 12 сек без счётчиков это OK.

### Alternatives Considered
- **`-1`** (текущее поведение) — rejected.
- **Persist предыдущих значений в файле `/tmp/statbysong.cache`** — rejected: KISS, отдельная задача.
- **Persist в `tbl_settings`** через `KaraokeProperties` — rejected: нет смысла, `tbl_settings` для настроек, не для runtime-cache.

---

## Решение 3 (D-3): Persist последних значений — НЕТ в этой фиче

### Decision
**НЕ** persist'ить последние значения. Background refresh полностью пересчитывает все 5 счётчиков. После успешного refresh — `println` + WARN/INFO лог.

### Rationale
- KISS — минимум сложности.
- Persist добавляет: (а) IO операции (медленнее); (б) state management (что если файл повреждён); (в) race conditions между refresh и persist.
- 12 сек без счётчиков — приемлемо (главная страница работает, показывает 0).

### Alternatives Considered
- **Persist в файл** — rejected (см. выше).
- **Persist в `tbl_settings`** — rejected.

---

## Решение 4 (D-4): Применение индекса на проде

### Decision
**Двойной механизм**:
1. Миграция `deploy/karaoke-db/45_idx_songs_id_status_source_markers.sql` — для **новых** контейнеров через `docker-entrypoint-initdb.d/` (volume mount в `docker-compose-database.yml`).
2. На существующем прод-контейнере `karaoke-db` — пользователь выполняет **`CREATE INDEX CONCURRENTLY idx_songs_id_status_source_markers ON tbl_songs(id_status, source_markers);`** вручную через `psql` (или через `docker exec`).

### Rationale
- **Новый контейнер** (если будет пересоздан через `--force-recreate`): `initdb.d/` скрипты выполнятся автоматически.
- **Существующий контейнер на проде** (Up 3 days, per предыдущей проверке): `initdb.d/` уже не сработает (PostgreSQL init only at first start). Нужен ручной `psql`.
- **`CONCURRENTLY`** — без блокировки таблицы (zero-downtime на проде). `CREATE INDEX` без `CONCURRENTLY` требует `AccessExclusiveLock` на 5-30 сек для таблицы ~18k записей.
- **Per Constitution § п. 2** — выполнение DDL на проде требует per-action согласия.

### Команды

```bash
# Вариант A: через docker exec (рекомендуется)
docker exec karaoke-db psql -U postgres -d karaoke \
  -c "CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_songs_id_status_source_markers ON tbl_songs(id_status, source_markers);"

# Вариант B: через прямой psql (если есть доступ к прод-БД)
psql -h 188.119.64.111 -U SvoeMestoKaraokeUser905 -d karaoke \
  -c "CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_songs_id_status_source_markers ON tbl_songs(id_status, source_markers);"
```

`IF NOT EXISTS` — idempotent. Если индекс уже создан — skip.

### Alternatives Considered
- **Создание индекса через `KaraokeProperties`/API в `karaoke-web`** — rejected: прямое DDL через приложение — анти-паттерн.
- **Добавление через sync-механизм `SyncRegistry`** — rejected: `SyncRegistry` синхронизирует **данные**, не DDL. Indexes создаются через init-скрипты.

---

## Best Practice: Spring Boot cache warm-up

### Источники
- [Spring Boot Reference: Caching](https://docs.spring.io/spring-boot/docs/current/reference/html/io.html#io.caching)
- [Baeldung: Spring Cache Annotations](https://www.baeldung.com/spring-cache-annotations)

### Ключевые принципы (применимые к нашему случаю)
1. **Cache warm-up должен быть async** — первая операция НЕ должна блокировать.
2. **Fallback на null/0/default** — если кеш не прогрет, возвращать безопасное значение, не 500.
3. **Single-flight pattern** — если refresh уже запущен, второй вызов no-op (или ждёт).
4. **Logging при ошибках** — failed refresh не должен «молча» оставлять кеш пустым.

### Наша реализация
- (1) ✓ через `ScheduledExecutorService.submit { refreshCache() }`.
- (2) ✓ возвращаем 0.
- (3) ✓ через `AtomicBoolean refreshing`.
- (4) ✓ через `log.warn("cache:refreshFailed ...", exception)`.

---

## Best Practice: PostgreSQL `CREATE INDEX CONCURRENTLY`

### Источники
- [PostgreSQL Docs: CREATE INDEX](https://www.postgresql.org/docs/16/sql-createindex.html#SQL-CREATEINDEX-CONCURRENTLY)
- [Cybertec: CREATE INDEX CONCURRENTLY](https://www.cybertec-postgresql.com/en/postgresql-create-index-concurrently/)

### Ключевые факты
- **`CONCURRENTLY`** строит индекс без `AccessExclusiveLock` на таблицу → **SELECT/INSERT/UPDATE/DELETE** могут продолжаться во время создания.
- **Минусы**: (а) занимает больше времени (примерно в 2-3 раза дольше, чем без `CONCURRENTLY`); (б) **нельзя** внутри транзакции (для прод-deploy это не проблема — autocommit).
- **`IF NOT EXISTS`** — idempotent, не упадёт если индекс уже есть.
- **Размер**: на 18k записей × 2 столбца (id_status: smallint, source_markers: text) → ~1-3 MB. Создание < 5 сек.

### Наша команда
```sql
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_songs_id_status_source_markers ON tbl_songs(id_status, source_markers);
```

`tbl_songs.id_status` — это smallint (1-2 bytes на запись), `tbl_songs.source_markers` — text (variable, но `btrim(coalesce(..., '')) != ''` фильтрует пустые → индекс будет содержать NULLs для пустых). B-tree индекс работает.

### Когда НЕ использовать `CONCURRENTLY`
- На пустой таблице (< 100 записей) — лишний overhead.
- Внутри одной транзакции с другими DDL — нельзя.

На нашей таблице 18k записей + создание при cold-start (нет активных INSERT) — `CONCURRENTLY` не критичен, но **безопаснее** (нет риска lock contention если какие-то INSERT идут).

---

## Best Practice: `AtomicBoolean` для guard от дублирования

### Источники
- [Java Docs: AtomicBoolean](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/atomic/AtomicBoolean.html)
- [Baeldung: Guide to AtomicBoolean](https://www.baeldung.com/java-atomicboolean)

### Наш паттерн

```kotlin
companion object {
    private val refreshing = AtomicBoolean(false)
}

private fun ensureCacheInitialized() {
    if (cachedTotal.get() < 0 && refreshing.compareAndSet(false, true)) {
        // Только ОДИН поток выигрывает CAS, остальные возвращают fallback.
        bgExecutor.submit {
            try {
                refreshCache()
            } finally {
                refreshing.set(false)
            }
        }
    }
    // Все запросы (включая тот, который запустил refresh) возвращают fallback.
    // НЕ блокируемся на ожидании refresh — иначе теряем смысл async warm-up.
}
```

### `compareAndSet(false, true)` semantics
- Атомарно проверяет + устанавливает значение.
- Только **один** поток получает `true` (выигрывает CAS).
- Остальные получают `false` → сразу возвращают fallback.

### `finally { refreshing.set(false) }`
- Гарантирует сброс guard даже если `refreshCache()` бросит exception.
- Без `finally` — guard остался бы `true` навсегда, и последующие refresh'и не запускались бы → cache stuck.

---

## Сводка NEEDS CLARIFICATION — все резолвнуты

| ID | Решение |
|----|---------|
| D-1 | `ScheduledExecutorService` (single-thread, daemon) в `companion object` |
| D-2 | Fallback = 0 (безопасное значение для UI) |
| D-3 | НЕ persist'ить (KISS) |
| D-4 | Миграция `45_*.sql` для новых + ручной `psql` для существующего |

Дополнительные технические решения (best practices для async warm-up, `CREATE INDEX CONCURRENTLY`, `AtomicBoolean`) — резолвнуты в этом `research.md`.