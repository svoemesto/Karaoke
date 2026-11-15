package com.svoemesto.karaokeapp.db

import com.svoemesto.karaokeapp.AppLog
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection
import java.sql.SQLTransientConnectionException

/**
 * Ограниченный пул физических JDBC-соединений к одной базе данных
 * (спека 778, KARAOKE-430, ADR-0009).
 *
 * **Владелец физических каналов.** Каналом владеет пул, а не поток: handle —
 * это аренда (см. [PooledHandle]). Поэтому:
 *
 * - число физических соединений ограничено [PoolConfig.maxPoolSize] (FR-001) —
 *   независимо от числа потоков и числа обращений к БД;
 * - `close()` handle'а **возвращает** канал в пул (FR-002), а не закрывает его;
 * - соединение, оставшееся от мёртвого потока, возвращается рейпером через
 *   [PoolOwnerRegistry] (FR-003).
 *
 * **URL передаётся как есть.** `Connection.remote()` строит JDBC-URL с
 * `sslmode`/`sslcert`/`sslkey`/`sslrootcert` (спека 733, ADR local-0018) — любой
 * разбор и пересборка URL рисковала бы потерять параметры mTLS и отрезать
 * доступ к SERVER-БД. Здесь URL кладётся в `HikariConfig.jdbcUrl` без изменений.
 *
 * **Ленивое создание.** [HikariDataSource] создаётся при первом [borrow], а не
 * в конструкторе: 10 тестовых подмен `KaraokeConnection` переопределяют
 * `getConnection()`, и пул у них не должен создаваться вовсе (никаких сетевых
 * подключений в конструкторе).
 *
 * **Наблюдаемость.** Исчерпание пула — `DB-POOL-002` (с `target`, `waiting`,
 * `busy`), что отличает отказ приложения от отказа БД (`DB-CONNECT-001`).
 *
 * @param target роль БД (`LOCAL` / `SERVER`) — для различения в журнале
 * @param jdbcUrl JDBC-URL **как есть** (с параметрами mTLS для SERVER)
 * @param username пользователь БД
 * @param password пароль БД
 * @param config параметры пула
 * @param providerFactory фабрика источника каналов (в тестах — подставная)
 * @see PoolProvider
 * @see PooledHandle
 * @see PoolOwnerRegistry
 */
class DbPool(
    val target: String,
    private val jdbcUrl: String,
    private val username: String,
    private val password: String,
    val config: PoolConfig,
    private val providerFactory: (DbPool) -> PoolProvider = { p -> HikariPoolProvider(p) },
) : PoolProvider {
    private companion object {
        /** Категория журнала пула (как у рейпера — `infra.db.reaper`). */
        const val CATEGORY = "infra.db.pool"
    }

    /**
     * Реестр handle'ов и их владельцев. Живёт внутри пула: пул выдаёт handle'и
     * и знает, кто ими владеет — это и нужно рейперу для возврата аренд
     * мёртвых владельцев (FR-003).
     */
    val ownerRegistry = PoolOwnerRegistry()

    /**
     * Источник каналов. Создаётся лениво: до первого обращения к БД пул не
     * существует — ни одного соединения, ни одного потока HikariCP.
     */
    @Volatile
    private var source: PoolProvider? = null

    /**
     * Возвращает handle (аренду) на канал из пула.
     *
     * @param owner поток-владелец аренды (по умолчанию — текущий)
     */
    fun borrowHandle(owner: Thread = Thread.currentThread()): PooledHandle {
        val provider = ensureSource()
        val channel =
            try {
                provider.borrow()
            } catch (e: PoolTimeoutException) {
                throw e
            } catch (e: SQLTransientConnectionException) {
                // HikariCP не успел выдать канал за connectionTimeout — это
                // исчерпание пула, а не отказ БД.
                val timeout =
                    PoolTimeoutException(
                        target = target,
                        waiting = provider.waitingThreads(),
                        busy = provider.activeConnections(),
                        maxPoolSize = config.maxPoolSize,
                        timeoutMs = config.acquireTimeoutMs,
                    )
                AppLog.warn(
                    category = CATEGORY,
                    code = "DB-POOL-002",
                    event = "db.pool.timeout",
                    fields =
                        mapOf(
                            "target" to target,
                            "waiting" to timeout.waiting,
                            "busy" to timeout.busy,
                            "maxPoolSize" to config.maxPoolSize,
                            "timeoutMs" to config.acquireTimeoutMs,
                        ),
                    error = e,
                )
                throw timeout
            }
        val handle =
            PooledHandle(
                provider = provider,
                channel = channel,
                owner = owner,
                onRelease = { released -> ownerRegistry.unregister(released) },
            )
        ownerRegistry.register(handle)
        return handle
    }

    /**
     * Возвращает в пул handle'ы, чьи владельцы-потоки **мертвы**.
     *
     * Вызывается рейпером. Условие возврата — только «владелец мёртв»
     * (`!thread.isAlive`), независимо от времени простоя: мёртвый поток handle
     * не использует, ждать нечего. Handle'ы живых владельцев **не трогаются
     * никогда** — в том числе простаивающие: лейн очереди `KaraokeProcess`
     * держит открытую транзакцию минутами, и отдать его handle в пул означало бы
     * откатить транзакцию и сломать задание (FR-013).
     *
     * @return сколько handle'ов возвращено в пул
     */
    fun reapDeadOwnerHandles(): Int {
        val dead = ownerRegistry.findDeadOwners()
        var returned = 0
        for (handle in dead) {
            if (handle.release()) returned++
        }
        if (returned > 0) {
            // DB-POOL-004 — подтверждение, что механизм FR-003 работает. Это
            // ключевая метрика фичи: без неё пул исчерпался бы так же, как
            // сегодня исчерпывается сама БД, только внутри пула.
            AppLog.warn(
                category = CATEGORY,
                code = "DB-POOL-004",
                event = "db.pool.reaped",
                fields =
                    mapOf(
                        "target" to target,
                        "returned" to returned,
                        "ownerAlive" to ownerRegistry.liveOwnerCount(),
                    ),
            )
        }
        return returned
    }

    /**
     * Сообщает о handle'ах, которые живой владелец удерживает дольше порога
     * (FR-009, событие `DB-POOL-003`).
     *
     * **Действие не предпринимается** — это наблюдательное событие: handle
     * живого потока не отбирается (лейн очереди `KaraokeProcess` держит
     * открытую транзакцию минутами, и отбор аренды сломал бы задание). Смысл
     * события — показать **конкретное место в коде**, где соединение удерживается
     * дольше положенного: без этого пул лишь меняет симптом утечки, а не
     * показывает её источник.
     *
     * @param thresholdMs порог удержания
     * @return сколько handle'ов сообщено
     */
    fun reportLongHeldHandles(thresholdMs: Long): Int {
        val longHeld = ownerRegistry.findLongHeld(thresholdMs)
        val now = System.currentTimeMillis()
        for (handle in longHeld) {
            AppLog.warn(
                category = CATEGORY,
                code = "DB-POOL-003",
                event = "db.pool.leak",
                fields =
                    mapOf(
                        "target" to target,
                        "heldMs" to (now - handle.borrowedAtMs),
                        "thresholdMs" to thresholdMs,
                        "thread" to handle.owner.name,
                        "stack" to handle.borrowStack,
                    ),
            )
        }
        return longHeld.size
    }

    /** Ленивое создание источника каналов. */
    private fun ensureSource(): PoolProvider {
        source?.let { return it }
        synchronized(this) {
            source?.let { return it }
            val created = providerFactory(this)
            source = created
            return created
        }
    }

    override fun borrow(): Connection = borrowHandle().channel

    override fun release(channel: Connection) {
        // Канал возвращается в пул силами реализации PoolProvider; здесь —
        // ничего: закрывать канал нельзя, он общий.
        source?.release(channel)
    }

    override fun totalConnections(): Int = source?.totalConnections() ?: 0

    override fun activeConnections(): Int = source?.activeConnections() ?: 0

    override fun idleConnections(): Int = source?.idleConnections() ?: 0

    override fun waitingThreads(): Int = source?.waitingThreads() ?: 0

    /** Закрывает пул (и все каналы). Вызывается при остановке приложения. */
    fun close() {
        val current = source ?: return
        source = null
        (current as? HikariPoolProvider)?.close()
    }

    /**
     * Реализация [PoolProvider] поверх HikariCP.
     *
     * Здесь и только здесь живёт знание о конкретной библиотеке: остальной код
     * работает через интерфейс — это позволяет проверять логику пула в CI без
     * живой БД.
     */
    class HikariPoolProvider(
        private val pool: DbPool,
    ) : PoolProvider {
        private val dataSource: HikariDataSource by lazy { createDataSource() }

        private fun createDataSource(): HikariDataSource {
            val cfg = HikariConfig()
            // URL — как есть (mTLS-параметры не теряются).
            cfg.jdbcUrl = pool.jdbcUrl
            cfg.username = pool.username
            cfg.password = pool.password
            cfg.maximumPoolSize = pool.config.maxPoolSize
            cfg.minimumIdle = pool.config.minIdle
            cfg.connectionTimeout = pool.config.acquireTimeoutMs
            cfg.validationTimeout = pool.config.validationTimeoutMs
            cfg.maxLifetime = pool.config.maxLifetimeMs
            // keepalive пингует простаивающие каналы: туннель до SERVER-БД
            // (ADR local-0018) рвёт их молча, и без пинга «мёртвый» канал
            // выдаётся как рабочий.
            if (pool.config.keepaliveMs > 0) {
                cfg.keepaliveTime = pool.config.keepaliveMs
            }
            cfg.poolName = "karaoke-${pool.target}"
            // Не мешать HikariCP печатать в stdout: у нас свой журнал.
            cfg.isRegisterMbeans = false
            return HikariDataSource(cfg)
        }

        override fun borrow(): Connection = dataSource.connection

        override fun release(channel: Connection) {
            // close() у прокси HikariCP возвращает канал в пул (не закрывает TCP).
            try {
                channel.close()
            } catch (_: Exception) {
                // Канал уже мёртв — HikariCP сам выбросит его.
            }
        }

        override fun totalConnections(): Int = dataSource.hikariPoolMXBean?.totalConnections ?: 0

        override fun activeConnections(): Int = dataSource.hikariPoolMXBean?.activeConnections ?: 0

        override fun idleConnections(): Int = dataSource.hikariPoolMXBean?.idleConnections ?: 0

        override fun waitingThreads(): Int = dataSource.hikariPoolMXBean?.threadsAwaitingConnection ?: 0

        fun close() {
            try {
                dataSource.close()
            } catch (_: Exception) {
                // Закрытие при остановке — ошибка здесь ничего не меняет.
            }
        }
    }
}
