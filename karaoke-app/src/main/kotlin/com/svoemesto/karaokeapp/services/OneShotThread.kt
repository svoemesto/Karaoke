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
 * @param block тело одноразовой задачи.
 * @return запущенный поток (уже стартовал).
 *
 * @see KaraokeConnection.closeThreadConnection контракт закрытия соединения потока.
 */
fun startOneShotThread(
    database: KaraokeConnection,
    name: String? = null,
    block: () -> Unit,
): Thread =
    Thread(
        {
            try {
                block()
            } finally {
                // Соединение потока закрываем ВСЕГДА — и при успехе, и при исключении:
                // иначе утечка (аудит #336, BR-060, BR-038, BR-116).
                database.closeThreadConnection()
            }
        },
        name,
    ).apply {
        isDaemon = true
        start()
    }
