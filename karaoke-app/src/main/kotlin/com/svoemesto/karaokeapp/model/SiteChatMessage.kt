package com.svoemesto.karaokeapp.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
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
 * Класс Site Chat Message.
 *
 * @see docs/features/dual-db-sync.md
 */
@JsonIgnoreProperties(value = ["database", "sqlToInsert"])
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

        // Тред одного пользователя целиком, по возрастанию id (= по времени).
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
