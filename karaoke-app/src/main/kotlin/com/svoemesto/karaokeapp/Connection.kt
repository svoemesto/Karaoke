package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.services.APP_WORK_IN_CONTAINER
import com.svoemesto.karaokeapp.services.APP_WORK_ON_SERVER
import com.svoemesto.karaokeapp.services.DB_LOCAL_POSTGRES_PASSWORD
import com.svoemesto.karaokeapp.services.DB_LOCAL_POSTGRES_USER
import com.svoemesto.karaokeapp.services.DB_SERVER_POSTGRES_PASSWORD
import com.svoemesto.karaokeapp.services.DB_SERVER_POSTGRES_USER

/**
 * Реализация [KaraokeConnection] — обёртка над JDBC URL + credentials +
 * display-name для логов и SSE.
 *
 * Используется через статические фабрики [Companion.local], [Companion.remote],
 * [Companion.virtual]. Прямое создание `Connection(...)` — не рекомендуется
 * (нет валидации параметров).
 *
 * Потокобезопасность: каждый [Connection] — stateless wrapper, `getConnection()`
 * возвращает НОВОЕ JDBC-соединение из пула при каждом вызове. Закрывайте
 * соединения через `withDb { ... }` в контроллерах, иначе утечка → «too many
 * clients» (см. DEVELOPMENT.md «Dual database targets»).
 *
 * @see KaraokeConnection базовый интерфейс
 * @see WORKING_DATABASE глобальный singleton (обычно = `Connection.local()`)
 * @see docs/features/dual-db-sync.md
 */

/**
 * Класс Connection.
 *
 * @see docs/features/dual-db-sync.md
 */
class Connection(
    override val url: String,
    override val username: String,
    override val password: String,
    override val name: String,
) : KaraokeConnection(url, username, password, name) {
    //    private var connection: java.sql.Connection? = null
//    override fun getConnection(): java.sql.Connection {
//        if (connection == null) {
//            Class.forName("org.postgresql.Driver")
//            connection = DriverManager.getConnection(url, username, password)
//        }
//        return connection!!
//    }
    companion object {
        private val USERNAME = if (APP_WORK_ON_SERVER) DB_SERVER_POSTGRES_USER else DB_LOCAL_POSTGRES_USER
        private val PASSWORD = if (APP_WORK_ON_SERVER) DB_SERVER_POSTGRES_PASSWORD else DB_LOCAL_POSTGRES_PASSWORD

        /**
         * Подключение к LOCAL-БД (admin-машина, dev).
         *
         * URL зависит от [APP_WORK_IN_CONTAINER]:
         * - В Docker: `jdbc:postgresql://karaoke-db:5432/karaoke` (через docker network).
         * - Локально: `jdbc:postgresql://localhost:8832/karaoke`.
         *
         * Credentials выбираются автоматически:
         * - На admin-машине ([APP_WORK_ON_SERVER]=false) — `DB_LOCAL_*`.
         * - На прод-сервере ([APP_WORK_ON_SERVER]=true) — `DB_SERVER_*` (для тестирования).
         *
         * @return новое подключение с `name = "LOCAL"`.
         * @see remote
         * @see virtual
         */
        fun local(): KaraokeConnection = Connection(name = "LOCAL", url = connectionLocalUrl(), username = USERNAME, password = PASSWORD)

        /**
         * Подключение к REMOTE-БД (прод-сервер `79.174.95.69:8832`).
         *
         * Используется для [SyncTarget] — pull/push изменений LOCAL ↔ SERVER.
         * В [KaraokeProcessWorker] и других runtime-сервисах используется
         * редко (только для sync).
         *
         * @return новое подключение с `name = "SERVER"`.
         * @see local
         */
        fun remote(): KaraokeConnection =
            Connection(
                name = "SERVER",
                url = connectionRemoteUrl(),
                username = DB_SERVER_POSTGRES_USER,
                password = DB_SERVER_POSTGRES_PASSWORD,
            )

        /**
         * Виртуальное подключение (in-memory) для тестов.
         * В текущей кодовой базе НЕ используется в проде (помечено `@Suppress("unused")`).
         * Оставлено для будущих интеграционных тестов, которым нужна изолированная БД.
         *
         * @return новое подключение с `name = "VIRTUAL"`.
         */
        @Suppress("unused")
        fun virtual(): KaraokeConnection =
            Connection(
                name = "VIRTUAL",
                url = connectionVirtualUrl(),
                username = USERNAME,
                password = PASSWORD,
            )

        private fun connectionLocalUrl(): String =
            if (APP_WORK_IN_CONTAINER) {
                "jdbc:postgresql://karaoke-db:5432/karaoke?currentSchema=public&socketTimeout=30&loginTimeout=10"
            } else {
                "jdbc:postgresql://localhost:8832/karaoke?currentSchema=public&socketTimeout=30&loginTimeout=10"
            }

        private fun connectionRemoteUrl(): String =
            "jdbc:postgresql://79.174.95.69:8832/karaoke?currentSchema=public&socketTimeout=30&loginTimeout=10"

        private fun connectionVirtualUrl(): String =
            "jdbc:postgresql://localhost:2230/karaoke?currentSchema=public&socketTimeout=30&loginTimeout=10"
    }
}
