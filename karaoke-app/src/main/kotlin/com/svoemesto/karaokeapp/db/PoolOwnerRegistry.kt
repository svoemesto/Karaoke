package com.svoemesto.karaokeapp.db

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Реестр handle'ов и их владельцев (спека 778, KARAOKE-430, FR-003).
 *
 * **Зачем.** С пулом «забытое» соединение перестаёт быть проблемой БД, но
 * остаётся проблемой **пула**: handle, взятый потоком, который умер, занимает
 * слот до тех пор, пока его не вернут. Если этого не делать, пул исчерпается
 * ровно так же, как сегодня исчерпывается сама БД (только внутри пула).
 *
 * **Как определяется смерть владельца.** Через `Thread.isAlive()`:
 *
 * - `false` после `Thread.exit()` — **навсегда** (прямой факт, без GC);
 * - дешёво (поле в объекте `Thread`, без синхронизации).
 *
 * Отклонённые альтернативы:
 *
 * - `WeakReference<Thread>`: реестр удерживает поток → объект не соберётся, а
 *   ссылка на мёртвый `Thread` удерживает его `ThreadLocalMap` → удерживает
 *   handle → цикл. Нужен отдельный обход по GC.
 * - `PhantomReference`/`Cleaner`: handle удерживается `ThreadLocal` мёртвого
 *   потока, поэтому GC не гарантирован вовремя — тайминг возврата слота
 *   неконтролируем (нужен «за 5 минут»).
 *
 * **Гонки исключены.** Рейпер возвращает handle только мёртвого потока — такой
 * поток уже не может использовать handle. Если поток умер сразу после проверки
 * `isAlive() == true`, рейпер его не трогает, и следующий проход рейпера увидит
 * мёртвый поток.
 *
 * @see com.svoemesto.karaokeapp.db.PooledHandle
 */
class PoolOwnerRegistry {
    /** Запись реестра: владелец handle'а и время последнего использования. */
    private data class OwnerRecord(
        val owner: Thread,
        val borrowedAtMs: Long,
        val lastUsedMs: AtomicLong,
    )

    private val registry = ConcurrentHashMap<PooledHandle, OwnerRecord>()

    /** Регистрирует handle под его владельцем. */
    fun register(handle: PooledHandle) {
        registry[handle] =
            OwnerRecord(
                owner = handle.owner,
                borrowedAtMs = handle.borrowedAtMs,
                lastUsedMs = AtomicLong(handle.lastUsedMs),
            )
    }

    /** Снимает handle с реестра (после возврата в пул). */
    fun unregister(handle: PooledHandle) {
        registry.remove(handle)
    }

    /** Сколько handle'ов сейчас на учёте. */
    fun size(): Int = registry.size

    /** Handle'ы, чей владелец мёртв — их можно вернуть в пул. */
    fun findDeadOwners(): List<PooledHandle> =
        registry.entries
            .filter { !it.value.owner.isAlive }
            .map { it.key }

    /** Handle'ы, удерживаемые дольше [thresholdMs] живым владельцем — кандидаты на утечку. */
    fun findLongHeld(thresholdMs: Long): List<PooledHandle> {
        val now = System.currentTimeMillis()
        return registry.entries
            .filter { entry ->
                entry.value.owner.isAlive && (now - entry.value.lastUsedMs.get()) > thresholdMs
            }.map { it.key }
    }

    /** Сколько handle'ов удерживается живыми владельцами (наблюдаемость). */
    fun liveOwnerCount(): Int = registry.entries.count { it.value.owner.isAlive }
}
