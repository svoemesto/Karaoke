package com.svoemesto.karaokeapp.services

import com.svoemesto.karaokeapp.AppLog
import com.svoemesto.karaokeapp.Connection
import com.svoemesto.karaokeapp.KaraokeConnection
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Периодическая уборка простаивающих соединений к базе (инцидент 2026-11-12).
 *
 * **Симптом.** `event=db.connect.failed code=DB-CONNECT-001 target=LOCAL
 * reason=FATAL: sorry, too many clients already` десятками в минуту, очередь
 * падает с `WORKER-001`. При этом прод-БД свободна: перегружена **локальная**.
 *
 * **Причина.** Соединение кешируется в `ThreadLocal` (ADR-0001, specs/087):
 * у каждого потока своё физическое соединение. Для потоков пула это норма —
 * поток живёт долго и переиспользует соединение. Но потоки `ForkJoinPool`
 * (`parallel-*`, в т.ч. `Dispatchers.IO`) и прочие недолговечные потоки
 * создаются и умирают; их соединение не закрывает никто: `closeThreadConnection()`
 * из чужого потока не вызвать, финализатор JDBC недетерминирован. Замер
 * 2026-11-12: 241 простаивающее соединение из 300, 221 из них старше 2 часов.
 *
 * **Что делает.** Раз в [intervalMs] просит `KaraokeConnection` закрыть
 * соединения, не использовавшиеся дольше [idleMs]. Это безопасно: если поток
 * ещё жив, следующий `getConnection()` увидит `isClosed` и откроет новое —
 * тот же путь self-healing, что и после обрыва сети (specs/236).
 *
 * Порог простоя намеренно заметно больше периода проверки, чтобы не закрывать
 * соединения активных потоков между запросами.
 *
 * @param local соединение LOCAL (в тестах подменяется)
 * @param remote соединение SERVER (в тестах подменяется)
 * @param intervalMs период уборки
 * @param idleMs порог простоя, после которого соединение закрывается
 */
@Component
class LocalConnectionReaper(
    private val local: KaraokeConnection = Connection.local(),
    private val remote: KaraokeConnection = Connection.remote(),
    @Value("\${karaoke.db.reaper.interval-ms:300000}") private val intervalMs: Long = 300_000,
    @Value("\${karaoke.db.reaper.idle-ms:900000}") private val idleMs: Long = 900_000,
) {
    /**
     * Убирает простаивающие соединения LOCAL и SERVER.
     *
     * `fixedDelay` (а не `fixedRate`): следующий запуск отсчитывается от конца
     * предыдущего, поэтому медленная уборка не накладывается сама на себя.
     */
    @Scheduled(fixedDelayString = "\${karaoke.db.reaper.interval-ms:300000}", initialDelayString = "60000")
    fun reap() {
        reapOne("LOCAL", local)
        reapOne("SERVER", remote)
    }

    private fun reapOne(
        target: String,
        database: KaraokeConnection,
    ) {
        val closed =
            try {
                database.reapIdleConnections(idleMs)
            } catch (e: Exception) {
                AppLog.warn(
                    category = "infra.db.reaper",
                    code = "DB-REAP-002",
                    event = "db.reap.failed",
                    fields = mapOf("target" to target),
                    error = e,
                )
                return
            }
        if (closed <= 0) return
        AppLog.warn(
            category = "infra.db.reaper",
            code = "DB-REAP-001",
            event = "db.reap.idle",
            fields =
                mapOf(
                    "target" to target,
                    "closed" to closed,
                    "open" to database.openConnectionCount(),
                    "idleMs" to idleMs,
                ),
        )
    }
}
