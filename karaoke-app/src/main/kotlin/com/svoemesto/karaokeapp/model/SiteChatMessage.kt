package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.WORKING_DATABASE
import com.svoemesto.karaokeapp.services.KSS_APP
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.SAC_APP
import com.svoemesto.karaokeapp.services.StorageApiClient
import java.io.Serializable
import java.sql.SQLException
import java.sql.Timestamp

// «Чат с автором проекта». Одно сообщение в непрерывном треде site_user_id <-> автор (append-only,
// сообщения не редактируются). Таблица живёт ЦЕЛИКОМ на PROD-БД (не участвует в LOCAL<->SERVER
// синхронизации — нет записи в SyncRegistry, нет sync_* флагов) — пользователи пишут через
// karaoke-web (WORKING_DATABASE на проде = серверная БД), автор читает/пишет из webvue3 через
// karaoke-app с target=remote (Connection.remote()) напрямую в ту же БД (19_site_chat_messages.sql).

/**
 * Сообщение в чате «с автором» (admin ↔ site-user).
 *
 * Содержит:
 * - `id`, `idSiteUser` — пользователь.
 * - `direction` — IN (от user) / OUT (от admin).
 * - `text` — текст.
 * - `created`, `isRead` — таймстамп + статус прочтения.
 *
 * Рассылается по SSE `MESSAGE` для live-обновления в `webvue3` (чат-виджет).
 * Непрочитанные счётчики — в Vuex-модуле `chat` (см. `App.vue`).
 *
 * @see docs/features/sse-notifications.md
 */
class SiteChatMessage(
    override val database: KaraokeConnection = WORKING_DATABASE,
    override val storageService: KaraokeStorageService = KSS_APP,
    override val storageApiClient: StorageApiClient = SAC_APP,
) : Serializable,
    KaraokeDbTable {
    override fun getTableName() = TABLE_NAME

    @KaraokeDbTableField(name = "id", isId = true)
    override var id: Long = 0

    @KaraokeDbTableField(name = "site_user_id")
    var siteUserId: Long = 0

    @KaraokeDbTableField(name = "is_from_author")
    var isFromAuthor: Boolean = false

    @KaraokeDbTableField(name = "body")
    var body: String = ""

    @KaraokeDbTableField(name = "is_read")
    var isRead: Boolean = false

    // Ставится в коде при создании (createNew), не БД-дефолтом — reflection-insert (getSqlToInsert())
    // перечисляет все аннотированные поля явно и затёр бы DEFAULT значением по умолчанию Kotlin-поля.
    // Nullable по инварианту reflection-loader (NPE на SQL NULL при non-null Timestamp-поле), хотя
    // колонка практически всегда заполнена.
    @KaraokeDbTableField(name = "created_at", useInDiff = false)
    var createdAt: Timestamp? = null

    override fun toDTO(): SiteChatMessageDto =
        SiteChatMessageDto(
            id = id,
            siteUserId = siteUserId,
            fromAuthor = isFromAuthor,
            body = body,
            read = isRead,
            createdAt = createdAt?.toString() ?: "",
        )

    companion object {
        const val TABLE_NAME = "tbl_site_chat_messages"
        const val DEFAULT_PAGE_SIZE = 10
        const val MAX_PAGE_SIZE = 2000

        // Тред одного пользователя целиком, по возрастанию id (= по времени). Используется только там,
        // где реально нужна вся история разом (сейчас — нигде в контроллерах, см. loadPageByUser).
        fun loadByUser(
            siteUserId: Long,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): List<SiteChatMessage> =
            KaraokeDbTable
                .loadList(
                    clazz = SiteChatMessage::class,
                    tableName = TABLE_NAME,
                    whereList = listOf("site_user_id=$siteUserId"),
                    database = database,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                ).map { it as SiteChatMessage }
                .sortedBy { it.id }

        // Одна "страница" переписки для infinite-scroll UI — raw SQL (не generic KaraokeDbTable.loadList:
        // тот не добавляет ORDER BY в SQL вообще, полагаясь на сортировку в памяти ПОСЛЕ полной выборки —
        // с LIMIT это отдало бы произвольные строки, а не именно последние/следующие по id).
        // - beforeId=null, afterId=null → последние `limit` сообщений треда (открытие чата).
        // - beforeId=X → `limit` более старых сообщений (id < X), подгрузка истории вверх.
        // - afterId=X → сообщения новее X, без ограничения по времени, но с тем же `limit` как
        //   защитой от аномально большого залпа (поллинг новых сообщений).
        // Результат всегда возвращается по возрастанию id, независимо от направления курсора.
        fun loadPageByUser(
            siteUserId: Long,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
            limit: Int,
            beforeId: Long? = null,
            afterId: Long? = null,
        ): List<SiteChatMessage> {
            val connection = database.getConnection() ?: return emptyList()
            val result = mutableListOf<SiteChatMessage>()
            val where = StringBuilder("site_user_id = ?")
            if (beforeId != null) where.append(" AND id < ?")
            if (afterId != null) where.append(" AND id > ?")
            // DESC (+ разворот результата в конце) нужен и для beforeId (история вверх), и для
            // случая без курсоров вообще (открытие чата — нужны последние `limit`, а не первые).
            // ASC — только при afterId (поллинг: следующие по хронологии сразу после курсора).
            val orderDesc = afterId == null
            val sql =
                "SELECT id, site_user_id, is_from_author, body, is_read, created_at FROM $TABLE_NAME " +
                    "WHERE $where ORDER BY id ${if (orderDesc) "DESC" else "ASC"} LIMIT ?"
            try {
                connection.prepareStatement(sql).use { ps ->
                    var idx = 1
                    ps.setLong(idx++, siteUserId)
                    if (beforeId != null) ps.setLong(idx++, beforeId)
                    if (afterId != null) ps.setLong(idx++, afterId)
                    ps.setInt(idx, limit.coerceIn(1, MAX_PAGE_SIZE))
                    ps.executeQuery().use { rs ->
                        while (rs.next()) {
                            val entity =
                                SiteChatMessage(database = database, storageService = storageService, storageApiClient = storageApiClient)
                            entity.id = rs.getLong("id")
                            entity.siteUserId = rs.getLong("site_user_id")
                            entity.isFromAuthor = rs.getBoolean("is_from_author")
                            entity.body = rs.getString("body") ?: ""
                            entity.isRead = rs.getBoolean("is_read")
                            entity.createdAt = rs.getTimestamp("created_at")
                            result.add(entity)
                        }
                    }
                }
            } catch (e: SQLException) {
                println("SiteChatMessage.loadPageByUser SQLException: ${e.message}")
            }
            return if (orderDesc) result.asReversed() else result
        }

        fun countByUser(
            siteUserId: Long,
            database: KaraokeConnection,
        ): Int {
            val connection = database.getConnection() ?: return 0
            val sql = "SELECT COUNT(*) AS cnt FROM $TABLE_NAME WHERE site_user_id = ?"
            return try {
                connection.prepareStatement(sql).use { ps ->
                    ps.setLong(1, siteUserId)
                    ps.executeQuery().use { rs -> if (rs.next()) rs.getInt("cnt") else 0 }
                }
            } catch (e: SQLException) {
                println("SiteChatMessage.countByUser SQLException: ${e.message}")
                0
            }
        }

        // Непрочитанные автором сообщения В КОНКРЕТНОМ треде — бейдж на публичной стороне
        // (в отличие от countUnreadFromUsers, который считает глобально по всем тредам для webvue3).
        fun countUnreadForUser(
            siteUserId: Long,
            database: KaraokeConnection,
        ): Int {
            val connection = database.getConnection() ?: return 0
            val sql = "SELECT COUNT(*) AS cnt FROM $TABLE_NAME WHERE site_user_id = ? AND is_from_author = true AND is_read = false"
            return try {
                connection.prepareStatement(sql).use { ps ->
                    ps.setLong(1, siteUserId)
                    ps.executeQuery().use { rs -> if (rs.next()) rs.getInt("cnt") else 0 }
                }
            } catch (e: SQLException) {
                println("SiteChatMessage.countUnreadForUser SQLException: ${e.message}")
                0
            }
        }

        fun getById(
            id: Long,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): SiteChatMessage? =
            KaraokeDbTable.loadById(
                clazz = SiteChatMessage::class,
                tableName = TABLE_NAME,
                id = id,
                database = database,
                storageService = storageService,
                storageApiClient = storageApiClient,
            ) as? SiteChatMessage?

        fun createNew(
            siteUserId: Long,
            isFromAuthor: Boolean,
            body: String,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): SiteChatMessage? {
            val entity = SiteChatMessage(database = database, storageService = storageService, storageApiClient = storageApiClient)
            entity.siteUserId = siteUserId
            entity.isFromAuthor = isFromAuthor
            entity.body = body
            entity.createdAt = Timestamp(System.currentTimeMillis())
            return KaraokeDbTable.createDbInstance(entity = entity, database = database) as? SiteChatMessage?
        }

        // Список тредов для автора (webvue3): по одному на site_user_id, с превью последнего
        // сообщения и счётчиком непрочитанных от пользователя. Раздельный raw-SQL-агрегат
        // (не через generic loadList — нужны JOIN и GROUP BY), возвращает простой data class,
        // не KaraokeDbTable (паттерн — Author.resolveByTerm()).
        fun loadThreads(database: KaraokeConnection): List<ChatThreadDto> {
            val result: MutableList<ChatThreadDto> = mutableListOf()
            val connection = database.getConnection() ?: return result
            val sql =
                """
                SELECT u.id AS site_user_id, u.email, u.display_name,
                       last_msg.body AS last_body, last_msg.created_at AS last_at,
                       COALESCE(unread.cnt, 0) AS unread_from_user
                FROM (SELECT DISTINCT site_user_id FROM $TABLE_NAME) m
                JOIN ${SiteUser.TABLE_NAME} u ON u.id = m.site_user_id
                JOIN LATERAL (
                    SELECT body, created_at FROM $TABLE_NAME
                    WHERE site_user_id = m.site_user_id
                    ORDER BY id DESC LIMIT 1
                ) last_msg ON true
                LEFT JOIN LATERAL (
                    SELECT COUNT(*) AS cnt FROM $TABLE_NAME
                    WHERE site_user_id = m.site_user_id AND is_from_author = false AND is_read = false
                ) unread ON true
                ORDER BY last_msg.created_at DESC
                """.trimIndent()
            try {
                connection.prepareStatement(sql).use { ps ->
                    ps.executeQuery().use { rs ->
                        while (rs.next()) {
                            result.add(
                                ChatThreadDto(
                                    siteUserId = rs.getLong("site_user_id"),
                                    email = rs.getString("email") ?: "",
                                    displayName = rs.getString("display_name") ?: "",
                                    lastBody = rs.getString("last_body") ?: "",
                                    lastAt = rs.getTimestamp("last_at")?.toString() ?: "",
                                    unreadFromUser = rs.getInt("unread_from_user"),
                                ),
                            )
                        }
                    }
                }
            } catch (e: SQLException) {
                println("SiteChatMessage.loadThreads SQLException: ${e.message}")
            }
            return result
        }

        // Непрочитанные автором сообщения от пользователей (глобально, по всем тредам) — для
        // мониторинга и бейджа меню webvue3. Симметричный подсчёт «непрочитанные от автора» не нужен
        // глобально — публичная сторона (PublicChatController.unreadCount) считает только свой тред.
        fun countUnreadFromUsers(database: KaraokeConnection): Int {
            val connection = database.getConnection() ?: return 0
            val sql = "SELECT COUNT(*) AS cnt FROM $TABLE_NAME WHERE is_from_author = false AND is_read = false"
            return try {
                connection.prepareStatement(sql).use { ps ->
                    ps.executeQuery().use { rs -> if (rs.next()) rs.getInt("cnt") else 0 }
                }
            } catch (e: SQLException) {
                println("SiteChatMessage.countUnreadFromUsers SQLException: ${e.message}")
                0
            }
        }

        // Открытие треда автором — все сообщения ОТ пользователя помечаются прочитанными.
        fun markThreadReadByAuthor(
            siteUserId: Long,
            database: KaraokeConnection,
        ): Unit = markThreadRead(siteUserId, database, isFromAuthor = false)

        // Открытие треда пользователем — все сообщения ОТ автора помечаются прочитанными.
        fun markThreadReadByUser(
            siteUserId: Long,
            database: KaraokeConnection,
        ): Unit = markThreadRead(siteUserId, database, isFromAuthor = true)

        private fun markThreadRead(
            siteUserId: Long,
            database: KaraokeConnection,
            isFromAuthor: Boolean,
        ) {
            val connection = database.getConnection() ?: return
            val sql = "UPDATE $TABLE_NAME SET is_read = true WHERE site_user_id = ? AND is_from_author = ? AND is_read = false"
            try {
                connection.prepareStatement(sql).use { ps ->
                    ps.setLong(1, siteUserId)
                    ps.setBoolean(2, isFromAuthor)
                    ps.executeUpdate()
                }
            } catch (e: SQLException) {
                println("SiteChatMessage.markThreadRead SQLException: ${e.message}")
            }
        }
    }
}

// Не БД-сущность — агрегат для списка тредов (webvue3, ChatController.threads).

/**
 * DTO для chat thread: сериализуемое представление для API/UI.
 *
 * @see docs/features/dual-db-sync.md
 */
data class ChatThreadDto(
    val siteUserId: Long,
    val email: String,
    val displayName: String,
    val lastBody: String,
    val lastAt: String,
    val unreadFromUser: Int,
)
