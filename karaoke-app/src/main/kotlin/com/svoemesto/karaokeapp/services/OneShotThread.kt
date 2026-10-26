package com.svoemesto.karaokeapp.services

import com.svoemesto.karaokeapp.KaraokeConnection

/**
 * Запуск **одноразового** фонового потока с ГАРАНТИТИРОВАННЫМ закрытием
 * JDBC-соединения этого потока.
 *
 * Зачем: `KaraokeConnection` кэширует физическое соединение в `ThreadLocal` потока
 * (см. `KaraokeConnection`, specs/087-fix-shared-db-connection). Поток, который
 * создаётся заново и умирает (фоновая задача API-контроллера, поток очереди
 * `KaraokeProcessWorker`, воркер `SyncAudioDescendants`), больше никогда не позовёт
 * `getConnection()` — значит, он не сможет ни переиспользовать, ни обнаружить как
 * невалидное своё соединение. Без явного закрытия физический канал к PostgreSQL
 * остаётся открытым до недетерминированной финализации сборщиком мусора; при
 * `max_connections` это отказ БД для всего приложения
 * (прецедент — `Connection.kt`, KDoc: «100+ физических каналов → `FATAL: sorry, too
 * many clients already`»).
 *
 * Контракт (см. KDoc `KaraokeConnection.closeThreadConnection`):
 *
 * - **одноразовый поток** → `closeThreadConnection()` ОБЯЗАТЕЛЕН (делает этот helper);
 * - **переиспользуемый поток** (пул Tomcat, `@Scheduled`) → `closeThreadConnection()`
 *   ВЫЗЫВАТЬ НЕЛЬЗЯ: закрытие разрушит thread-local кеш и заставит открывать новое
 *   физическое соединение на каждое обращение к БД.
 *
 * @param database соединение, thread-local кеш которого нужно закрыть по завершении.
 * @param name имя потока (для диагностики в thread-dump'ах и логах).
 * @param onFinally действие, выполняемое ВСЕГДА при завершении потока — в том же
 *   `finally`, что и закрытие соединения, но ДО него. Нужно для снятия флагов
 *   «задача идёт»: если сброс флага стоит последним оператором тела, то любой
 *   `Throwable` (в частности из `SNS.send`, который сериализует payload вне
 *   `try`) оставит флаг поднятым навсегда, и повторный запуск станет невозможен
 *   до перезапуска приложения (аудит #336, BR-254).
 * @param alsoClose дополнительные экземпляры [KaraokeConnection], чьи thread-local
 *   соединения закрываются в том же `finally`. Нужно, когда тело потока ходит в
 *   **две** БД (LOCAL + SERVER): у каждого экземпляра `KaraokeConnection` свой
 *   `ThreadLocal`, поэтому закрытие только [database] оставило бы соединение к
 *   второй БД висеть — ровно та же утечка (BR-627, OpenProject #410).
 *   Закрытие дополнительных соединений выполняется ПОСЛЕ закрытия [database] и
 *   не зависит от его исхода: ошибка одного не мешает закрытию остальных.
 * @param block тело одноразовой задачи.
 * @return запущенный поток (уже стартовал).
 *
 * @see KaraokeConnection.closeThreadConnection контракт закрытия соединения потока.
 */
fun startOneShotThread(
    database: KaraokeConnection,
    name: String? = null,
    onFinally: () -> Unit = {},
    alsoClose: List<KaraokeConnection> = emptyList(),
    block: () -> Unit,
): Thread =
    Thread(
        {
            try {
                block()
            } finally {
                // BR-254: снятие флага «задача идёт» — ВСЕГДА, при любом исходе.
                // Порядок: сначала флаг (чтобы новый запуск мог стартовать сразу),
                // потом закрытие соединения.
                try {
                    onFinally()
                } catch (_: Exception) {
                    // Ошибка снятия флага не должна маскировать исходное исключение
                    // тела потока и не должна помешать закрытию соединения.
                }
                // Соединение потока закрываем ВСЕГДА — и при успехе, и при исключении:
                // иначе утечка (аудит #336, BR-060, BR-038, BR-116).
                database.closeThreadConnection()
                // Поток мог ходить в несколько БД (LOCAL + SERVER) — у каждого
                // экземпляра KaraokeConnection свой ThreadLocal-кеш, поэтому
                // закрываем и остальные (BR-627, OpenProject #410).
                alsoClose.forEach { closeQuietly(it) }
            }
        },
        name,
    ).apply {
        isDaemon = true
        start()
    }

/**
 * Закрывает thread-local соединение [database], не давая сбою закрытия
 * прервать цепочку закрытия остальных соединений потока.
 */
private fun closeQuietly(database: KaraokeConnection) {
    try {
        database.closeThreadConnection()
    } catch (_: Exception) {
        // Ошибка закрытия дополнительного соединения не должна помешать закрытию
        // остальных и не должна маскировать исходное исключение тела потока.
    }
}
