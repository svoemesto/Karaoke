# Plan: Schema-cache в KaraokeDbTable.loadList (FR-102)

**Spec**: [`spec.md`](./spec.md)
**Tasks**: [`tasks.md`](./tasks.md)
**Parent**: `specs/241-db-storage-perf-audit/spec.md` FR-102

## Tech Stack

- **Язык**: Kotlin (JDK 17, Gradle Kotlin DSL).
- **Concurrency**: `java.util.concurrent.ConcurrentHashMap` (стандартная библиотека, без новых зависимостей).
- **Конфигурация**: `KaraokeProperties.getBoolean(key)` (уже есть, signature: `Boolean getBoolean(String key)`).
- **Изменяемые файлы**: только `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/KaraokeDbTable.kt`.

## Project Structure

Изменения локализованы в companion object `KaraokeDbTable`:

```
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/
├── KaraokeDbTable.kt          ← ИЗМЕНЯЕТСЯ (companion object)
└── ... (остальные без правок)
```

## Дизайн

### Структуры данных (companion object)

```kotlin
// Иммутабельная запись кеша. List<String> не мутируется после создания (val).
private data class SchemaCacheEntry(
    val columnNames: List<String>,
    val expiresAtMs: Long,
)

// Потокобезопасный in-memory кеш. Ключ — Pair(tableName, databaseName).
private val schemaCache = ConcurrentHashMap<Pair<String, String>, SchemaCacheEntry>()

// TTL = 1 час. Константа — единственное место для тюнинга.
private const val SCHEMA_CACHE_TTL_MS = 3_600_000L
```

### Алгоритм `columns()`

```kotlin
private fun columns(tableName: String, database: KaraokeConnection): List<String> {
    // 1. Опциональное отключение через KaraokeProperties (FR-007).
    if (!isSchemaCacheEnabled()) {
        return columnsFromDb(tableName, database)
    }

    val key = tableName to database.name
    val now = System.currentTimeMillis()

    // 2. Проверка кеша (FR-001).
    schemaCache[key]?.let { entry ->
        if (entry.expiresAtMs > now) return entry.columnNames
        // TTL истёк — удаляем устаревшую запись, идём в БД.
        schemaCache.remove(key, entry)
    }

    // 3. Cache miss — идём в БД.
    val fresh = columnsFromDb(tableName, database)

    // 4. Кешируем только непустые результаты (FR-003).
    if (fresh.isNotEmpty()) {
        schemaCache[key] = SchemaCacheEntry(
            columnNames = fresh,
            expiresAtMs = now + SCHEMA_CACHE_TTL_MS,
        )
    }
    return fresh
}
```

### Алгоритм `columnsFromDb()` — вынесенная существующая логика (FR-006)

Без изменений относительно текущего `columns()`. Просто переименован и помечен `private`
(без `private` уже был — добавляем явно).

### Алгоритм `isSchemaCacheEnabled()` (FR-007)

```kotlin
private fun isSchemaCacheEnabled(): Boolean = try {
    KaraokeProperties.getBoolean("karaoke.db.schema_cache.enabled")
} catch (_: Throwable) {
    // На случай ранней инициализации / проблем с файлом свойств —
    // безопасный дефолт = ВКЛ (минимизируем SQL round-trip'ы по умолчанию).
    true
}
```

**NB**: текущая сигнатура `KaraokeProperties.getBoolean(key: String): Boolean` принимает
только `key`. Используем её как есть. Default (если ключ не зарегистрирован) = `false` —
поэтому сначала регистрируем ключ с `defaultValue = true` в `listKaraokeProperties`
(см. изменения ниже). Если регистрация по какой-то причине не сработает — `try/catch`
страхует от падения и возвращает `true` (дефолт самой функции = безопасное поведение).

### Алгоритм `invalidateSchemaCache()` (FR-005)

```kotlin
@Suppress("unused")
fun invalidateSchemaCache(
    tableName: String? = null,
    database: KaraokeConnection? = null,
) {
    when {
        tableName != null && database != null ->
            schemaCache.remove(tableName to database.name)
        tableName != null ->
            schemaCache.keys.removeAll { it.first == tableName }
        database != null ->
            schemaCache.keys.removeAll { it.second == database.name }
        else ->
            schemaCache.clear()
    }
}
```

**NB про `keys.removeAll`**: `ConcurrentHashMap.keys` — это view, **не снимок**;
`removeAll` работает по iterator'у и может бросить `ConcurrentModificationException`
при параллельной модификации. Для нашего use-case (редкая операция инвалидации, обычно
в пост-миграционных хуках или тестах) это приемлемо; задокументировано в KDoc. Альтернатива
(собрать ключи в отдельный `List` и потом `removeAll { it in keysToRemove }`) — оверкилл
для текущего объёма кеша.

### Изменения в `KaraokeProperties.kt`

Добавить запись в `listKaraokeProperties`:

```kotlin
KaraokeProperty(
    key = "karaoke.db.schema_cache.enabled",
    defaultValue = true,
    description = "Кешировать список колонок information_schema в KaraokeDbTable.loadList (TTL=1ч). false = отключить кеш, каждый loadList идёт в БД (для отладки schema-related багов).",
),
```

## Constitution Check

| § | Принцип | Статус |
|---|---------|--------|
| I | DRY | ✅ Кеш локализован в одном месте — companion object `KaraokeDbTable`. |
| II | Тесты | ⏸ В проекте `@Disabled` (см. AGENTS.md). Проверка — ручная по `quickstart.md`. |
| III | Документация | ✅ KDoc на `columns()`, `columnsFromDb()`, `invalidateSchemaCache()`, `SchemaCacheEntry`, ссылка на эту спеку. Обновить LiveDocs `architecture-notes.md` (отдельная задача T030 — out of scope для этого PR). |
| IV | Безопасность | ✅ `invalidateSchemaCache` помечен `@Suppress("unused")` (публичный API без активных caller'ов в коде). Нет внешних точек входа. |
| V | Логирование | ✅ Существующий `println` при ошибке подключения сохранён. Новых логов не добавляем (кеш — silent perf optimization). |
| VI | KDoc | ✅ KDoc на изменённых/новых символах. |
| VII | Идемпотентность | ✅ `invalidateSchemaCache` идемпотентен. `put` в `ConcurrentHashMap` идемпотентен. |

## Риски и митигация

| Риск | Вероятность | Митигация |
|------|-------------|-----------|
| Устаревший кеш после `ALTER TABLE ADD COLUMN` | Средняя | TTL=1ч + публичный `invalidateSchemaCache(...)` для hot-fixes. |
| `ConcurrentModificationException` в `invalidateSchemaCache` при параллельной модификации | Низкая | Задокументировано в KDoc; типичный вызов — в пост-миграционном хуке или тесте, без параллельных loadList. |
| `KaraokeProperties.getBoolean` недоступен при раннем обращении | Низкая | `try/catch` → fallback на `true` (безопасный дефолт = кеш работает). |
| Пустой результат `information_schema` (новая таблица без DDL или typo в имени) | Низкая | FR-003 — не кешируем, повторяем SQL на следующий вызов. |

## План реализации (резюме tasks.md)

- **Phase 1 (T001-T005)**: setup — прочитать код, найти callers, проверить `KaraokeProperties.getBoolean`.
- **Phase 2 (T006-T008)**: foundational — добавить структуры данных (entry, cache map, TTL const).
- **Phase 3 (T009-T015)**: US1 — кеширование в `columns()` + вынос в `columnsFromDb()` + `isSchemaCacheEnabled()` + регистрация свойства + KDoc.
- **Phase 4 (T016-T021)**: US2 — публичный `invalidateSchemaCache(...)` с 4 режимами + KDoc с примерами.
- **Phase 5 (T022-T030)**: polish — обязательная проверка (compile/ktlint/bootJar), отчёт.

Полный список задач — [`tasks.md`](./tasks.md).
