# Research: Устранить утечку JDBC-соединений при «Синхронизации БД в 1 клик»

**Feature**: 234-db-sync-connection-leak
**Phase**: 0 (research)
**Date**: 2026-08-16

## TL;DR

Минимальный фикс: `Connection.Companion.local()/remote()` сделать singleton-фабриками через Kotlin `by lazy(LazyThreadSafetyMode.SYNCHRONIZED)`. Добавить SLF4J `log.warn` в `KaraokeConnection.getConnection()` для структурированного логирования сбоев. Симметричный фикс в `karaoke-web`. Без новых зависимостей. HikariCP отложен в отдельную задачу.

---

## R-001: Паттерн thread-safe singleton в Kotlin для Connection-фабрики

**Decision**: `by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { Connection(...) }`

**Rationale**:
- Kotlin `lazy { ... }` с режимом `SYNCHRONIZED` гарантирует, что инициализация происходит ровно один раз, даже при конкурентных первых вызовах с разных потоков — `synchronized` блок на `Lazy` обёртке.
- Стандартный идиом для thread-safe singleton в Kotlin (документирован в [kotlinlang docs](https://kotlinlang.org/docs/delegated-properties.html#lazy-properties)).
- Инициализация ленивая — singleton создаётся при первом обращении к `Connection.local()`, а не при старте приложения (можно держать контейнер `karaoke-app` запущенным, но `local()` не вызывать — никаких JDBC-соединений не откроется).
- Не требует `@PostConstruct` или отдельного init-блока в Spring-конфигурации.

**Alternatives considered**:

| Альтернатива | Почему отклонена |
|--------------|-------------------|
| `object ConnectionLocal : KaraokeConnection(...)` | `Connection` параметризован (url, username, password, name) — нужно **два разных** singleton'а (LOCAL, REMOTE, VIRTUAL), а `object` — это только один инстанс. Можно сделать `object LocalConnection : ...` и `object RemoteConnection : ...`, но это лишает полиморфизма фабрики `local()`/`remote()` и усложняет KDoc. |
| `@Volatile private var localInstance: Connection? = null; init { ... }` | Работает, но требует ручного `synchronized` блока в `local()`. Больше boilerplate-кода, чем `by lazy`. |
| Spring `@Component` + `@PostConstruct` | Избыточно: фабрика `local()` статическая (`Companion object`), нет смысла делать её Spring-бин. Дополнительная сложность с lifecycle. |
| Eager init `private val LOCAL_INSTANCE = Connection(...)` | Инициализирует singleton при первом обращении к **классу** `Connection` (когда JVM загружает класс) — может быть слишком рано (например, до того как `APP_WORK_IN_CONTAINER` env var будет прочитан, если когда-нибудь понадобится). `lazy` — безопаснее. |

**Final code sketch**:

```kotlin
companion object {
    private val USERNAME = if (APP_WORK_ON_SERVER) DB_SERVER_POSTGRES_USER else DB_LOCAL_POSTGRES_USER
    private val PASSWORD = if (APP_WORK_ON_SERVER) DB_SERVER_POSTGRES_PASSWORD else DB_LOCAL_POSTGRES_PASSWORD

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

    /** См. FR-001 spec.md — теперь singleton. */
    fun local(): KaraokeConnection = LOCAL_INSTANCE

    /** См. FR-001 spec.md — теперь singleton. */
    fun remote(): KaraokeConnection = REMOTE_INSTANCE

    /** См. FR-002 spec.md — singleton, но помечен @Suppress("unused") как и раньше. */
    @Suppress("unused")
    fun virtual(): KaraokeConnection = VIRTUAL_INSTANCE
}
```

---

## R-002: SLF4J логирование в `KaraokeConnection.getConnection()`

**Decision**: Добавить `private val log = LoggerFactory.getLogger(KaraokeConnection::class.java)`. В `getConnection()` при исключении логировать `log.warn(...)` с полями `target`, `thread`, `cause`. Сохранить существующий `println(...)` для обратной совместимости.

**Rationale**:
- SLF4J уже в classpath через Spring Boot starter (используется во всём проекте: см. `Utils.kt`, `controllers/*`).
- `LoggerFactory.getLogger(KaraokeConnection::class.java)` — стандартный паттерн, даёт категорию логов = полное имя класса.
- Spring Boot по умолчанию использует **Logback** (включён в `spring-boot-starter-logging`), без новых зависимостей.
- Поля `target`/`thread`/`cause` — структурированные, легко парсятся стандартными инструментами (Kibana, Loki, etc.) или `grep`/`awk`.
- Сохранение `println` — для логов в dev-pc/локальных запусках, где SLF4J ещё не настроен (или вывод идёт в stdout контейнера).

**Alternatives considered**:

| Альтернатива | Почему отклонена |
|--------------|------------------|
| `kotlin.io.println` (заменить println на log.warn) | Ломает существующий вывод в stdout контейнера. В Karaoke проект пишет в `println` намеренно (для просмотра `docker logs`). Менять — лишний риск. |
| `MDC` (Mapped Diagnostic Context) для корреляции | Избыточно для текущей задачи. MDC нужен для сквозных trace-id между потоками (SSE/HTTP), а здесь — однократное сообщение об ошибке. |
| `log.error(...)` (а не `warn`) | `too many clients` — не фатальная ошибка приложения (приложение продолжает работать, fallback на `null`). `warn` — корректный уровень. |
| Бросать специализированное исключение (по FR-Q3 отклонённому) | Меняет контракт `getConnection(): Connection?` — 174+ вызывающих мест. Слишком инвазивно. |

**Final code sketch**:

```kotlin
abstract class KaraokeConnection(...) {
    private val log = LoggerFactory.getLogger(KaraokeConnection::class.java)
    private val threadLocalConnection = ThreadLocal<java.sql.Connection?>()

    fun getConnection(): java.sql.Connection? {
        val conn = threadLocalConnection.get()
        if (conn == null || conn.isClosed || !conn.isValid(3)) {
            Class.forName("org.postgresql.Driver")
            try {
                threadLocalConnection.set(DriverManager.getConnection(url, username, password))
            } catch (e: Exception) {
                // Сохраняем существующий println для обратной совместимости с docker logs
                println("KaraokeConnection getConnection Exception: ${e.message}")
                // Добавляем структурированный SLF4J warn (FR-004)
                log.warn(
                    "KaraokeConnection too many clients or connect failure",
                    mapOf(
                        "target" to name,
                        "thread" to Thread.currentThread().name,
                        "cause" to (e.message ?: "unknown"),
                    ),
                )
            }
        }
        return threadLocalConnection.get()
    }
    // closeThreadConnection() без изменений
}
```

**Примечание**: SLF4J `log.warn(String, Map)` — это **не стандартный** API. Стандартный — `log.warn(String, Object...)` с placeholder'ами или `log.warn(String, Throwable)`. Корректный код:

```kotlin
log.warn("KaraokeConnection connect failure target={} thread={} cause={}", name, Thread.currentThread().name, e.message)
```

или через `StructuredArguments` (Logback extension) — но это новая зависимость. Выбираем **стандартный SLF4J API** с placeholder'ами.

---

## R-003: Симметричный фикс в `karaoke-web`

**Decision**: В `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/Connection.kt` применить **идентичный** фикс — singleton для `local()`/`remote()`/`virtual()`.

**Rationale**:
- В `karaoke-web` есть свой `Connection` (см. `karaoke-web/.../Connection.kt:18-63`) с тем же багом: `fun local(): KaraokeConnection = Connection(name="LOCAL", ...)` — каждый раз `new`.
- Используется через `withDb { ... }` паттерн в `NewsController` (строки 30-43) и аналогичных контроллерах в `karaoke-web`.
- Если не сделать симметричный фикс — останется утечка в `webvue3`-эндпоинтах (новости, шаблоны, словари), которая тоже приводит к «too many clients» при массовом использовании.
- Усилий минимум: 1 файл, ~10 строк кода, идентично `karaoke-app`.

**Verification**:
- После фикса `karaoke-web/Connection.local()` возвращает тот же объект при повторных вызовах.
- `withDb { ... }` в `NewsController` после фикса становится **избыточным** (закрывать нечего — соединение переиспользуется), но безвредным (закрытие закрытого соединения = no-op).
- Удаление `withDb { ... }` НЕ входит в эту спеку (FR-009 spec.md, optional cleanup — отдельная задача).

---

## R-004: Совместимость с существующими контрактами

**Решаемые вопросы**:

### Q: Не сломает ли singleton контракт `087-fix-shared-db-connection`?

**A**: Нет. Спека `087` говорит «один физический канал на поток» (через `ThreadLocal`). Singleton `Connection` = **один инстанс** `Connection` на процесс, но внутри — `ThreadLocal` кеширует по одному каналу на поток. То есть:
- 200 Tomcat-потоков × 1 singleton `Connection.local()` = **200 физических каналов максимум** (а не 1, как было бы при общем `connection` без `ThreadLocal`).
- Это **идентично** текущему поведению с `ThreadLocal`, только без лишних 36 новых инстансов `Connection` на каждый клик.

### Q: Не сломает ли `closeThreadConnection()` из спеки `091-fix-connection-leak`?

**A**: Нет. `closeThreadConnection()` — метод **инстанса** `Connection`, он закрывает `ThreadLocal` **текущего потока** для **этого** инстанса. После singleton фикса все потоки делят один инстанс — `closeThreadConnection()` по-прежнему корректно закрывает свой `ThreadLocal`-слот:
- Для одноразовых потоков (`KaraokeProcessThread` — спека `091`) — закрывает свой слот, никто больше не пострадает.
- Для долгоживущих потоков (Tomcat) — KDoc явно запрещает вызов, поведение не меняется (не вызывали и не будем).

### Q: Что с `WORKING_DATABASE` (глобальный singleton = `Connection.local()`)?

**A**: `WORKING_DATABASE` — это `private val WORKING_DATABASE: KaraokeConnection = Connection.local()` (см. `karaoke-app/.../services/*Service.kt`). После фикса `Connection.local()` возвращает тот же singleton — `WORKING_DATABASE` теперь указывает на тот же объект, что и все остальные вызовы `Connection.local()`. Никаких регрессий.

---

## R-005: HikariCP — почему отложен

**Decision**: Не подключаем.

**Rationale** (см. спеку `174-fix-stats-connection-leak`/FR-007):
- HikariCP — connection pool с поддержкой eviction, health-check, метрик.
- Текущая задача — устранить конкретную утечку (новый Connection → новый ThreadLocal → новое JDBC). Singleton на `DriverManager` + `ThreadLocal` решает её полностью.
- HikariCP даст дополнительные преимущества (автоматическое закрытие idle-соединений, метрики), но **новая зависимость** + **переписывание** 174+ вызовов `getConnection()`.
- Если после singleton-фикса окажется, что 200 Tomcat-потоков × 1 соединение на поток = 200 каналов всё ещё тесно для Postgres `max_connections=100` — отдельная задача `XXX-hikaricp-pool` с обсуждением (см. спеку `174`).

**Проверка достаточности**: после фикса
- Singleton `local()` → 1 инстанс `Connection` → до 200 каналов (по потокам).
- Singleton `remote()` → 1 инстанс `Connection` → до 200 каналов.
- Итого ≤ 400 каналов максимум при 200 потоках. Но: обычно активны не все потоки одновременно, и `pg_stat_activity` показывает только те, что сейчас в запросе.
- На admin-машине (localhost Postgres) реальная нагрузка — десятки соединений, не сотни. `max_connections=100` — комфортно.

---

## R-006: Альтернативы (рассмотрены и отклонены)

| Альтернатива | Почему отклонена |
|--------------|------------------|
| Сделать `Connection` `data object` с предустановленными url/credentials | Нарушает текущий KDoc «фабрики local/remote/virtual»; не даёт тестировать VIRTUAL отдельно; усложняет KDoc. |
| Закрывать соединение в `getConnection()` после каждого вызова (как в `withDb`) | Убивает смысл `ThreadLocal` кеша (174+ вызовов будут открывать новое соединение каждый раз → каскад «too many clients» уже на первом запросе). |
| Использовать `javax.sql.DataSource` без пула | По сути то же, что текущее — обёртка над `DriverManager`. Не решает singleton-проблему. |
| Мигрировать на Exposed/JPA (см. Constitution § II) | ЗАПРЕЩЕНО Constitution § II «NON-NEGOTIABLE»: только сырой JDBC. |
| Глобальный `mutex` вокруг `getConnection()` | Убивает параллелизм — все HTTP-потоки выстраиваются в очередь за одним каналом. `max_connections=100` не спасает от thread contention. |

---

## Сводка решений

| # | Решение | Обоснование |
|---|---------|-------------|
| R-001 | `by lazy(SYNCHRONIZED)` для singleton `Connection` | Стандартный Kotlin-идиом, thread-safe, ленивая инициализация |
| R-002 | SLF4J `log.warn` с placeholder'ами в `getConnection()` | Уже в classpath, структурированный вывод без новых зависимостей |
| R-003 | Симметричный фикс в `karaoke-web` | Тот же баг, нужен параллельный фикс |
| R-004 | Совместимость с `087`/`091` сохранена | Singleton ≠ общий канал; `ThreadLocal` остаётся на поток |
| R-005 | HikariCP отложен | Минимальный фикс достаточен, HikariCP — отдельная задача при необходимости |
| R-006 | Альтернативы рассмотрены и отклонены | См. таблицу выше |

---

## Open Questions

Нет — все NEEDS CLARIFICATION из spec.md закрыты (см. секцию «Clarifications» в [spec.md](spec.md)).
