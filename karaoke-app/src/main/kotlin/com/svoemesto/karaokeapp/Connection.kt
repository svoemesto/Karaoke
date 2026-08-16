package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.services.APP_WORK_IN_CONTAINER
import com.svoemesto.karaokeapp.services.APP_WORK_ON_SERVER
import com.svoemesto.karaokeapp.services.DB_LOCAL_POSTGRES_PASSWORD
import com.svoemesto.karaokeapp.services.DB_LOCAL_POSTGRES_USER
import com.svoemesto.karaokeapp.services.DB_REMOTE_HOST
import com.svoemesto.karaokeapp.services.DB_REMOTE_PORT
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
 * **Singleton-семантика фабрик (specs/234-db-sync-connection-leak):** начиная с
 * этой спеки, `local()`/`remote()`/`virtual()` возвращают **тот же самый
 * инстанс** `Connection` на повторные вызовы (Kotlin `by lazy(SYNCHRONIZED)`),
 * а не `new Connection(...)` каждый раз. До этого фабрики создавали новый
 * инстанс на каждый вызов — каждый со своим `ThreadLocal<java.sql.Connection?>`
 * в [KaraokeConnection], что приводило к утечке JDBC-соединений на каждом
 * клике «Синхронизация БД в 1 клик» (36 новых инстансов Connection × N
 * вызовов = 100+ физических каналов → `FATAL: sorry, too many clients
 * already` при `max_connections=100`).
 *
 * Потокобезопасность: `getConnection()` (см. [KaraokeConnection]) кеширует
 * по одному физическому JDBC-соединению **на поток выполнения**
 * (`ThreadLocal`, specs/087-fix-shared-db-connection) — не на весь инстанс
 * и тем более не «новое соединение на каждый вызов». Для долгоживущего
 * singleton-инстанса `Connection`, на который ссылаются многие потоки
 * (типично для [WORKING_DATABASE]), это означает одно устойчивое соединение
 * на поток, переиспользуемое между вызовами без явного `close()`.
 *
 * Контракт `closeThreadConnection()` (specs/091-fix-connection-leak)
 * сохраняется: для одноразовых потоков вызывать явно, для долгоживущих
 * (Tomcat worker pool, главный цикл `KaraokeProcessWorker.doStart()`) —
 * НЕ вызывать (см. KDoc [KaraokeConnection.closeThreadConnection]).
 *
 * Отдельный, дополнительный паттерн — короткоживущий `Connection`,
 * создаваемый заново на каждый HTTP-запрос через приватный `withDb { ... }`
 * в части контроллеров (`SponsrSyncController`, `SiteUsersController`):
 * там именно НОВЫЙ инстанс `Connection` на вызов, закрываемый явно в
 * `finally`, чтобы не плодить лишние физические соединения при работе с
 * `target`-параметром (LOCAL/SERVER на выбор). После singleton-фикса этот
 * паттерн становится избыточным (закрытие закрытого соединения = no-op), но
 * не ломает контракт; опциональная чистка — отдельная задача.
 *
 * @see KaraokeConnection базовый интерфейс
 * @see WORKING_DATABASE глобальный singleton (обычно = `Connection.local()`)
 * @see archive/docs/features/dual-db-sync.md
 * @see specs/234-db-sync-connection-leak фикс утечки JDBC-соединений
 */

/**
 * Класс Connection.
 *
 * @see archive/docs/features/dual-db-sync.md
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

        // Singleton-фабрики (specs/234-db-sync-connection-leak): один инстанс Connection на процесс,
        // ленивая инициализация при первом обращении, thread-safe через LazyThreadSafetyMode.SYNCHRONIZED.
        // Решает утечку: раньше каждый вызов local()/remote() создавал НОВЫЙ Connection, у которого свой
        // ThreadLocal<java.sql.Connection?> → новый физический JDBC-канал, который никогда не закрывался.
        // Теперь: один Connection.local() на весь процесс karaoke-app, его ThreadLocal кеширует соединение
        // по потоку (контракт спеки 087-fix-shared-db-connection сохранён).
        private val LOCAL_INSTANCE: Connection by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            Connection(name = "LOCAL", url = connectionLocalUrl(), username = USERNAME, password = PASSWORD)
        }

        private val REMOTE_INSTANCE: Connection by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            Connection(
                name = "SERVER",
                url = connectionRemoteUrl(),
                username = DB_SERVER_POSTGRES_USER,
                password = DB_SERVER_POSTGRES_PASSWORD,
            )
        }

        @Suppress("unused")
        private val VIRTUAL_INSTANCE: Connection by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            Connection(name = "VIRTUAL", url = connectionVirtualUrl(), username = USERNAME, password = PASSWORD)
        }

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
         * **Singleton** (specs/234-db-sync-connection-leak): возвращает **тот же инстанс** `Connection`
         * на повторные вызовы, не новый объект. См. KDoc класса.
         *
         * @return singleton-инстанс `Connection` с `name = "LOCAL"`.
         * @see remote
         * @see virtual
         */
        fun local(): KaraokeConnection = LOCAL_INSTANCE

        /**
         * Подключение к REMOTE-БД (прод-сервер, хост/порт из env `DB_REMOTE_HOST`/`DB_REMOTE_PORT`,
         * дефолт `188.119.64.111:8832`).
         *
         * Используется для [SyncTarget] — pull/push изменений LOCAL ↔ SERVER.
         * В [KaraokeProcessWorker] и других runtime-сервисах используется
         * редко (только для sync).
         *
         * **Singleton** (specs/234-db-sync-connection-leak): возвращает **тот же инстанс** `Connection`
         * на повторные вызовы. См. KDoc класса.
         *
         * @return singleton-инстанс `Connection` с `name = "SERVER"`.
         * @see local
         */
        fun remote(): KaraokeConnection = REMOTE_INSTANCE

        /**
         * Виртуальное подключение (in-memory) для тестов.
         * В текущей кодовой базе НЕ используется в проде (помечено `@Suppress("unused")`).
         * Оставлено для будущих интеграционных тестов, которым нужна изолированная БД.
         *
         * **Singleton** (specs/234-db-sync-connection-leak): возвращает **тот же инстанс** `Connection`
         * на повторные вызовы. См. KDoc класса.
         *
         * @return singleton-инстанс `Connection` с `name = "VIRTUAL"`.
         */
        @Suppress("unused")
        fun virtual(): KaraokeConnection = VIRTUAL_INSTANCE

        private fun connectionLocalUrl(): String =
            if (APP_WORK_IN_CONTAINER) {
                "jdbc:postgresql://karaoke-db:5432/karaoke?currentSchema=public&socketTimeout=30&loginTimeout=10"
            } else {
                "jdbc:postgresql://localhost:8832/karaoke?currentSchema=public&socketTimeout=30&loginTimeout=10"
            }

        private fun connectionRemoteUrl(): String =
            "jdbc:postgresql://$DB_REMOTE_HOST:$DB_REMOTE_PORT/karaoke?currentSchema=public&socketTimeout=30&loginTimeout=10"

        private fun connectionVirtualUrl(): String =
            "jdbc:postgresql://localhost:2230/karaoke?currentSchema=public&socketTimeout=30&loginTimeout=10"
    }
}
