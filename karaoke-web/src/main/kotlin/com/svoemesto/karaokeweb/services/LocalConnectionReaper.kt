package com.svoemesto.karaokeweb.services

import com.svoemesto.karaokeapp.AppLog
import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeweb.Connection
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Периодическая уборка простаивающих соединений к базе (инцидент 2026-11-15).
 *
 * **Симптом.** `event=db.connect.failed code=DB-CONNECT-001 target=LOCAL
 * reason=FATAL: sorry, too many clients already` тысячи раз в сутки; прод-БД
 * отдаёт 503 на эндпоинты, которые ходят в БД. При этом лимит `max_connections`
 * не превышен ни одним «полезным» соединением — все 300 держал `karaoke-web`.
 *
 * **Причина.** Соединение кешируется в `ThreadLocal` (ADR-0001, specs/087):
 * у каждого потока своё физическое соединение. Для потоков пула Tomcat это
 * норма — поток живёт долго и переиспользует соединение. Но потоки Tomcat
 * простаивают и умирают (`keepAliveTimeout`), а потоки `Dispatchers.IO`
 * создаются и умирают постоянно; их соединение не закрывает никто:
 * `closeThreadConnection()` из чужого потока не вызвать, финализатор JDBC
 * недетерминирован. Мёртвый поток уносит своё соединение, счётчик растёт.
 *
 * **Почему в `karaoke-web` уборки не было.** Реппер (`karaoke-app`,
 * `LocalConnectionReaper`) появился 2026-11-12 для того же дефекта в
 * `karaoke-app`. Но `@SpringBootApplication` в `karaoke-web` сканирует только
 * пакет `com.svoemesto.karaokeweb`, поэтому компонент из `karaoke-app` в
 * web-контекст не попадает — и web со своим `Connection` утекал без присмотра.
 * Проверено 2026-11-15: утекал и старый образ (`45cfe12f`, 27.10), и новый —
 * A/B-прогон на стенде дал одинаковый темп, значит дефект не в свежих правках.
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
 * @param leakThresholdMs порог удержания handle'а, после которого оно считается
 *   утечкой (событие `DB-POOL-003`). По умолчанию — 6 часов: под ThreadLocal-
 *   контрактом handle живого потока держится столько, сколько жив поток, и
 *   порог в 15 минут давал только шум (замер на стенде: 1364 события за 4 часа,
 *   все от нормально живущих потоков).
 */
@Component
class LocalConnectionReaper(
    private val local: KaraokeConnection = Connection.local(),
    private val remote: KaraokeConnection = Connection.remote(),
    @Value("\${karaoke.db.reaper.interval-ms:300000}") private val intervalMs: Long = 300_000,
    @Value("\${karaoke.db.reaper.idle-ms:900000}") private val idleMs: Long = 900_000,
    @Value("\${karaoke.db.pool.leak-threshold-ms:21600000}") private val leakThresholdMs: Long = 21_600_000,
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
        // Спека 778: в пуловом режиме уборка возвращает аренды мёртвых владельцев
        // (FR-003), и мы показываем, где соединение удерживается дольше
        // положенного (FR-009, DB-POOL-003). Симметрично рейперу в karaoke-app:
        // иначе дефект повторит нынешнюю асимметрию (рейпер был только в app).
        reportLongHeld(target, database)
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

    /**
     * Сообщает о handle'ах, удерживаемых дольше порога (`DB-POOL-003`).
     *
     * Сбой здесь не должен ломать уборку: событие наблюдательное, поэтому
     * ошибка логируется и не прерывает цикл.
     */
    private fun reportLongHeld(
        target: String,
        database: KaraokeConnection,
    ) {
        try {
            database.reportLongHeldHandles(leakThresholdMs)
        } catch (e: Exception) {
            AppLog.warn(
                category = "infra.db.reaper",
                code = "DB-REAP-002",
                event = "db.reap.failed",
                fields = mapOf("target" to target, "operation" to "reportLongHeldHandles"),
                error = e,
            )
        }
    }
}
