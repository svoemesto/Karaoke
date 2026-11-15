package com.svoemesto.karaokeapp.db

import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.sql.Connection
import java.util.concurrent.atomic.AtomicBoolean

/**
 * **Аренда** физического канала из пула (спека 778, KARAOKE-430).
 *
 * Handle реализует `java.sql.Connection` и делегирует всё физическому каналу,
 * но **владеет не каналом, а правом его использовать**:
 *
 * - `close()` → `provider.release(channel)` — канал **возвращается в пул**, а не
 *   закрывается. Прежняя модель закрывала канал здесь же, и потому канал
 *   принадлежал потоку; с пулом канал общий, и закрытие убило бы чужую работу.
 * - `isClosed()` → `released || channel.isClosed`.
 *
 * **Зачем отдельный объект, а не сам канал из пула.** Три требования сходятся
 * здесь:
 *
 * 1. FR-002 — освобождение обязано **возвращать** канал в пул.
 * 2. FR-005 — `getConnection()` обязан вернуть `java.sql.Connection`; handle
 *    реализует этот интерфейс, поэтому прикладной код (284 места вызова)
 *    не меняется и не отличает handle от канала.
 * 3. FR-013 — рейпер обязан отличать «handle, хозяин которого мёртв» (надо
 *    вернуть в пул) от «handle, хозяин которого жив» (трогать нельзя). Физический
 *    канал этого различия не даёт: он общий для всех арендаторов.
 *
 * **Потокобезопасность.** Handle по контракту ThreadLocal принадлежит одному
 * потоку; `released` — `AtomicBoolean`, потому что рейпер может вернуть handle
 * мёртвого владельца из своего потока.
 *
 * @param provider пул, у которого handle взят
 * @param channel физический канал, выданный пулом
 * @param owner поток, взявший handle (владелец аренды)
 * @param onRelease вызывается при возврате handle'а в пул (снятие с реестра)
 * @see com.svoemesto.karaokeapp.db.PoolOwnerRegistry
 * @see com.svoemesto.karaokeapp.db.DbPool
 */
class PooledHandle(
    private val provider: PoolProvider,
    val channel: Connection,
    val owner: Thread,
    private val onRelease: (PooledHandle) -> Unit = {},
) : InvocationHandler {
    /** Handle уже возвращён в пул — защита от двойного возврата. */
    private val released = AtomicBoolean(false)

    /** Время взятия handle (для детектора долгого удержания). */
    val borrowedAtMs: Long = System.currentTimeMillis()

    /**
     * Стек вызовов места взятия handle — для события `DB-POOL-003` (FR-009).
     *
     * Снимается **один раз** при взятии аренды, а не на каждый запрос: взятие
     * handle'а происходит редко (раз на поток на роль БД), а вот запросов к БД
     * — десятки тысяч, и заполнять стек на каждом было бы лишней работой в
     * горячем пути.
     */
    val borrowStack: String = captureStack()

    /** Последнее использование handle — обновляется при каждом обращении. */
    @Volatile
    var lastUsedMs: Long = System.currentTimeMillis()

    /** Handle уже возвращён в пул? */
    val isReleased: Boolean
        get() = released.get()

    /**
     * Возвращает handle в пул. Идемпотентно: повторный вызов ничего не делает.
     *
     * @return `true`, если возврат состоялся этим вызовом
     */
    fun release(): Boolean {
        if (!released.compareAndSet(false, true)) return false
        try {
            provider.release(channel)
        } finally {
            onRelease(this)
        }
        return true
    }

    /**
     * Прокси `java.sql.Connection`, который отдаётся прикладному коду.
     *
     * Отдельный объект нужен, чтобы перехватить `close()`: `close()` у канала
     * из пула означал бы «вернуть аренду», а у нашего handle — «освободить
     * аренду и снять с реестра».
     *
     * Прокси создаётся **один раз**: ThreadLocal-кеш в `KaraokeConnection`
     * обязан отдавать тот же объект при повторных вызовах `getConnection()`
     * (кеш на поток, а не новый фасад на каждый вызов).
     */
    fun asConnection(): Connection {
        proxy?.let { return it }
        synchronized(this) {
            proxy?.let { return it }
            val created =
                Proxy.newProxyInstance(
                    Connection::class.java.classLoader,
                    arrayOf(Connection::class.java),
                    this,
                ) as Connection
            proxy = created
            return created
        }
    }

    /** Кэш прокси (создаётся один раз). */
    @Volatile
    private var proxy: Connection? = null

    /**
     * Снимает усечённый стек вызовов места взятия handle.
     *
     * Обрезается до [STACK_FRAMES] кадров и до [STACK_CHARS] символов: стек
     * попадает в журнал, а журнал читает человек.
     */
    private fun captureStack(): String {
        val frames = Thread.currentThread().stackTrace
        val text =
            frames
                .dropWhile { it.className == PooledHandle::class.java.name }
                .take(STACK_FRAMES)
                .joinToString(" <- ") { "${it.className}.${it.methodName}:${it.lineNumber}" }
        return text.take(STACK_CHARS)
    }

    private companion object {
        /** Сколько кадров стека попадает в событие `DB-POOL-003`. */
        const val STACK_FRAMES = 12

        /** Предел длины стека в символах — журнал читает человек. */
        const val STACK_CHARS = 1_500
    }

    /** Обработчик вызовов JDBC: всё делегируется каналу, кроме `close()`. */
    override fun invoke(
        proxy: Any,
        method: Method,
        args: Array<out Any?>?,
    ): Any? {
        when (method.name) {
            "close" -> {
                release()
                return null
            }
            "isClosed" -> return isReleased || channel.isClosed
            "hashCode" -> return System.identityHashCode(proxy)
            "equals" -> return proxy === args?.getOrNull(0)
        }
        lastUsedMs = System.currentTimeMillis()
        return try {
            method.invoke(channel, *(args ?: emptyArray()))
        } catch (e: InvocationTargetException) {
            throw e.targetException
        }
    }
}
