# Data Model: Устранить утечку JDBC-соединений при «Синхронизации БД в 1 клик»

**Feature**: 234-db-sync-connection-leak
**Phase**: 1 (design)
**Date**: 2026-08-16

## TL;DR

Фикс инфраструктурный — **не добавляет и не меняет доменные сущности** (таблицы `tbl_*`, модели `Song`/`Album`/etc.). Затрагивает только **3 служебных поля** в существующих классах.

---

## Изменения

### 1. `karaoke-app/.../Connection.kt` — добавляются 3 singleton-инстанса

**До** (текущее состояние):
```kotlin
class Connection(...) : KaraokeConnection(...) {
    companion object {
        fun local(): KaraokeConnection = Connection(name = "LOCAL", url = connectionLocalUrl(), ...) // ← new каждый раз
        fun remote(): KaraokeConnection = Connection(name = "SERVER", url = connectionRemoteUrl(), ...) // ← new каждый раз
        @Suppress("unused")
        fun virtual(): KaraokeConnection = Connection(name = "VIRTUAL", url = connectionVirtualUrl(), ...) // ← new каждый раз
    }
}
```

**После**:
```kotlin
class Connection(...) : KaraokeConnection(...) {
    companion object {
        // FR-001: singleton-фабрики
        private val LOCAL_INSTANCE: Connection by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            Connection(name = "LOCAL", url = connectionLocalUrl(), username = USERNAME, password = PASSWORD)
        }
        private val REMOTE_INSTANCE: Connection by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            Connection(name = "SERVER", url = connectionRemoteUrl(), username = DB_SERVER_POSTGRES_USER, password = DB_SERVER_POSTGRES_PASSWORD)
        }
        @Suppress("unused")
        private val VIRTUAL_INSTANCE: Connection by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            Connection(name = "VIRTUAL", url = connectionVirtualUrl(), username = USERNAME, password = PASSWORD)
        }

        fun local(): KaraokeConnection = LOCAL_INSTANCE
        fun remote(): KaraokeConnection = REMOTE_INSTANCE
        @Suppress("unused")
        fun virtual(): KaraokeConnection = VIRTUAL_INSTANCE

        // connectionLocalUrl(), connectionRemoteUrl(), connectionVirtualUrl() — без изменений
    }
}
```

**Поля**:
| Имя | Тип | Инициализация | Назначение |
|-----|-----|---------------|------------|
| `LOCAL_INSTANCE` | `Connection` | lazy (SYNCHRONIZED) | Singleton для LOCAL-БД (один инстанс на весь процесс `karaoke-app`) |
| `REMOTE_INSTANCE` | `Connection` | lazy (SYNCHRONIZED) | Singleton для REMOTE-БД (один инстанс на весь процесс `karaoke-app`) |
| `VIRTUAL_INSTANCE` | `Connection` | lazy (SYNCHRONIZED) | Singleton для VIRTUAL-БД (тесты; в проде не используется) |

**Состояние**: нет state transitions — это immutable константы, инициализируются один раз.

**Валидация**: KDoc `Connection.kt` обновляется (FR-010 spec.md) — явно указывает, что фабрики возвращают singleton (а не «новый инстанс на каждый вызов»).

---

### 2. `karaoke-app/.../KaraokeConnection.kt` — добавляется SLF4J logger

**До**:
```kotlin
abstract class KaraokeConnection(...) {
    private val threadLocalConnection = ThreadLocal<java.sql.Connection?>()

    fun getConnection(): java.sql.Connection? {
        val conn = threadLocalConnection.get()
        if (conn == null || conn.isClosed || !conn.isValid(3)) {
            Class.forName("org.postgresql.Driver")
            try {
                threadLocalConnection.set(DriverManager.getConnection(url, username, password))
            } catch (e: Exception) {
                println("KaraokeConnection getConnection Exception: ${e.message}") // ← только println
            }
        }
        return threadLocalConnection.get()
    }

    fun closeThreadConnection() { ... }
}
```

**После**:
```kotlin
abstract class KaraokeConnection(...) {
    // FR-005: SLF4J logger
    private val log = LoggerFactory.getLogger(KaraokeConnection::class.java)

    private val threadLocalConnection = ThreadLocal<java.sql.Connection?>()

    fun getConnection(): java.sql.Connection? {
        val conn = threadLocalConnection.get()
        if (conn == null || conn.isClosed || !conn.isValid(3)) {
            Class.forName("org.postgresql.Driver")
            try {
                threadLocalConnection.set(DriverManager.getConnection(url, username, password))
            } catch (e: Exception) {
                // Сохраняем println для обратной совместимости (docker logs)
                println("KaraokeConnection getConnection Exception: ${e.message}")
                // FR-004: добавляем структурированный SLF4J warn
                log.warn(
                    "KaraokeConnection connect failure target={} thread={} cause={}",
                    name,
                    Thread.currentThread().name,
                    e.message ?: "unknown",
                )
            }
        }
        return threadLocalConnection.get()
    }

    fun closeThreadConnection() {
        val conn = threadLocalConnection.get() ?: return
        try {
            conn.close()
        } catch (e: Exception) {
            println("KaraokeConnection closeThreadConnection Exception: ${e.message}") // ← оставляем для совместимости
            // FR-004: аналогично добавляем log.warn для симметрии
            log.warn(
                "KaraokeConnection closeThreadConnection failure target={} thread={} cause={}",
                name,
                Thread.currentThread().name,
                e.message ?: "unknown",
            )
        } finally {
            threadLocalConnection.remove()
        }
    }
}
```

**Поля**:
| Имя | Тип | Назначение |
|-----|-----|------------|
| `log` | `org.slf4j.Logger` | SLF4J-логгер для структурированного вывода сбоев подключения/закрытия |

**Импорты (добавляются)**:
```kotlin
import org.slf4j.LoggerFactory
```

**Состояние**: `log` — immutable val, состояние не меняется.

**Валидация**: имя логгера = `com.svoemesto.karaokeapp.KaraokeConnection` (по `KaraokeConnection::class.java`). Поля сообщения: `target` (LOCAL/SERVER/VIRTUAL), `thread` (имя потока), `cause` (текст исключения или "unknown").

---

### 3. `karaoke-web/.../Connection.kt` — симметричный фикс

**Изменения идентичны** пункту 1 (`karaoke-app/.../Connection.kt`), но в неймспейсе `com.svoemesto.karaokeweb`. Поля:
- `LOCAL_INSTANCE`
- `REMOTE_INSTANCE`
- `VIRTUAL_INSTANCE` (помечен `@Suppress("unused")` как и раньше)

**Отличие от `karaoke-app`-варианта**: использует `WEB_WORK_ON_SERVER`/`WEB_WORK_IN_CONTAINER` (см. `karaoke-web/.../Connection.kt:9-11`) вместо `APP_WORK_ON_SERVER`/`APP_WORK_IN_CONTAINER`. Это **уже есть** в текущем коде — мы только переносим в `LOCAL_INSTANCE`/`REMOTE_INSTANCE`.

---

## Что НЕ меняется

| Компонент | Статус | Обоснование |
|-----------|--------|-------------|
| `KaraokeConnection.getConnection()` контракт | Без изменений | Возвращает `java.sql.Connection?`; 174+ вызывающих мест не трогаем (FR-006 spec.md) |
| `KaraokeConnection.closeThreadConnection()` контракт | Без изменений | Для одноразовых потоков (спека `091`), KDoc запрещает для долгоживущих |
| `KaraokeConnection.threadLocalConnection` (ThreadLocal) | Без изменений | Кеширует соединение на поток — контракт спеки `087` |
| `WORKING_DATABASE` (глобальный singleton) | Без изменений | После фикса указывает на singleton `LOCAL_INSTANCE` автоматически |
| `SyncRegistry.all` | Без изменений | 18 сущностей — не трогаем |
| `SyncTarget.loadByIds/listHashes/...` | Без изменений | Принимают `KaraokeConnection` — тип и контракт те же |
| `POST /api/sync/oneclick` | Без изменений | Использует singleton через `Connection.local()/remote()` — контракт endpoint'а сохранён |
| `SyncOneClickResultDto` | Без изменений | DTO ответа не меняется |
| Модели `Song`/`Album`/`Author`/etc. | Без изменений | Фикс инфраструктурный, доменные сущности не затрагивает |
| SQL-миграции (`karaoke-db/*`) | Без изменений | Никаких DDL |
| `deploy/.env`/`deploy/do.env` | Без изменений | Секреты не трогаем (Principle VIII) |

---

## ER-диаграмма

Неприменимо — фикс не вводит новых сущностей и не меняет существующих.

---

## Миграция

Не требуется — фикс кода без миграции данных и без миграции схемы БД.

---

## Риски

| Риск | Митигация |
|------|-----------|
| Race condition при первом обращении к singleton (2 потока одновременно вызывают `Connection.local()`) | `by lazy(SYNCHRONIZED)` — гарантирует single-init под `synchronized` блоком |
| `WORKING_DATABASE` теперь указывает на singleton — если кто-то мутирует `threadLocalConnection` | `ThreadLocal` — единственный mutable state; контракт `getConnection()` не меняется, мутация контролируема |
| Lombok/Kotlin null-safety при `by lazy` | `Connection` — non-null, инициализация гарантирована `lazy`; null-safety OK |
| SLF4J не настроен в dev-pc | Spring Boot по умолчанию включает Logback; если нет — fallback на `println` остаётся |
| `karaoke-web/Connection` без фикса = утечка остаётся | FR-008 spec.md — симметричный фикс обязателен |
