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

// «Новости» проекта (новая песня в эфире/премиум-доступе, новый функционал сайта). Готовится заранее
// на LOCAL из webvue3, синхронизируется на PROD штатным движком (LOCAL_TO_SERVER, SyncTarget "news",
// см. sync/SyncTarget.kt) — как Dictionary (20_news.sql, deploy/karaoke-db/17_dictionaries.sql).
// «Опубликовано» — вычисляемое условие publishAt <= now() (как Settings.onAir), а не отдельный статус:
// новость с будущим publishAt, уже уехавшая на прод, сама «всплывает» в назначенный момент.

/**
 * Класс News.
 *
 * @see docs/features/dual-db-sync.md
 */
@JsonIgnoreProperties(value = ["database", "sqlToInsert"])
class News(
    override val database: KaraokeConnection = WORKING_DATABASE,
    override val storageService: KaraokeStorageService = KSS_APP,
    override val storageApiClient: StorageApiClient = SAC_APP,
) : Serializable,
    KaraokeDbTable {
    override fun getTableName() = TABLE_NAME

    @KaraokeDbTableField(name = "id", isId = true)
    override var id: Long = 0

    @KaraokeDbTableField(name = "title")
    var title: String = ""

    @KaraokeDbTableField(name = "body")
    var body: String = ""

    // "air" (эфир) | "premium" | "feature" (функционал) | "general" — определяет иконку/цвет на фронте.
    @KaraokeDbTableField(name = "category")
    var category: String = "general"

    // Опциональная ссылка (переход на песню/раздел/фичу по клику на новость). NULLable по инварианту
    // reflection-loader (NPE на SQL NULL при non-null String-поле).
    @KaraokeDbTableField(name = "link")
    var link: String? = null

    // Момент публикации/выхода в эфир. Nullable — черновик без даты публикации ещё не должен
    // отображаться в публичной ленте.
    @KaraokeDbTableField(name = "publish_at")
    var publishAt: Timestamp? = null

    // Ставится в коде при createNew (см. SiteChatMessage.createdAt) — reflection-insert
    // (getSqlToInsert()) перечисляет все аннотированные поля явно и затёр бы БД-DEFAULT.
    @KaraokeDbTableField(name = "created_at", useInDiff = false)
    var createdAt: Timestamp? = null

    override fun toDTO(): NewsDto =
        NewsDto(
            id = id,
            title = title,
            body = body,
            category = category,
            link = link ?: "",
            publishAt = publishAt?.toString() ?: "",
            createdAt = createdAt?.toString() ?: "",
            published = isPublished(publishAt),
        )

    companion object {
        const val TABLE_NAME = "tbl_news"

        private fun isPublished(publishAt: Timestamp?): Boolean = publishAt != null && publishAt <= Timestamp(System.currentTimeMillis())

        fun listHashes(
            database: KaraokeConnection,
            whereText: String = "",
        ): List<RecordHash>? = KaraokeDbTable.getListHashes(tableName = TABLE_NAME, database = database, whereText = whereText)

        // Полный список (в т.ч. черновики/будущие) для админки — свежие сверху.
        fun loadAll(
            database: KaraokeConnection,
            storageService: KaraokeStorageService = KSS_APP,
            storageApiClient: StorageApiClient = SAC_APP,
        ): List<News> =
            KaraokeDbTable
                .loadList(
                    clazz = News::class,
                    tableName = TABLE_NAME,
                    whereList = emptyList(),
                    database = database,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                ).map { it as News }
                .sortedByDescending { it.id }

        fun getById(
            id: Long,
            database: KaraokeConnection,
            storageService: KaraokeStorageService = KSS_APP,
            storageApiClient: StorageApiClient = SAC_APP,
        ): News? =
            KaraokeDbTable.loadById(
                clazz = News::class,
                tableName = TABLE_NAME,
                id = id,
                database = database,
                storageService = storageService,
                storageApiClient = storageApiClient,
            ) as? News?

        // Только опубликованные (publish_at уже наступил), свежие сверху — публичная лента/бейдж.
        // Raw-SQL (не через generic loadList) — нужен фильтр по времени, которого нет в getWhereList
        // словарного паттерна (паттерн агрегата — см. SiteChatMessage.loadThreads).
        fun loadPublished(database: KaraokeConnection): List<NewsDto> {
            val result: MutableList<NewsDto> = mutableListOf()
            val connection = database.getConnection() ?: return result
            val sql =
                """
                SELECT id, title, body, category, link, publish_at, created_at
                FROM $TABLE_NAME
                WHERE publish_at IS NOT NULL AND publish_at <= now()
                ORDER BY publish_at DESC
                """.trimIndent()
            try {
                connection.prepareStatement(sql).use { ps ->
                    ps.executeQuery().use { rs ->
                        while (rs.next()) {
                            result.add(
                                NewsDto(
                                    id = rs.getLong("id"),
                                    title = rs.getString("title") ?: "",
                                    body = rs.getString("body") ?: "",
                                    category = rs.getString("category") ?: "general",
                                    link = rs.getString("link") ?: "",
                                    publishAt = rs.getTimestamp("publish_at")?.toString() ?: "",
                                    createdAt = rs.getTimestamp("created_at")?.toString() ?: "",
                                    published = true,
                                ),
                            )
                        }
                    }
                }
            } catch (e: SQLException) {
                println("News.loadPublished SQLException: ${e.message}")
            }
            return result
        }

        // Только опубликованные с id больше lastSeenId — лёгкий запрос для бейджа/тоста
        // (обычно 0-3 строки за один опрос).
        fun loadPublishedSince(
            database: KaraokeConnection,
            lastSeenId: Long,
        ): List<NewsDto> {
            val result: MutableList<NewsDto> = mutableListOf()
            val connection = database.getConnection() ?: return result
            val sql =
                """
                SELECT id, title, body, category, link, publish_at, created_at
                FROM $TABLE_NAME
                WHERE publish_at IS NOT NULL AND publish_at <= now() AND id > ?
                ORDER BY publish_at DESC
                """.trimIndent()
            try {
                connection.prepareStatement(sql).use { ps ->
                    ps.setLong(1, lastSeenId)
                    ps.executeQuery().use { rs ->
                        while (rs.next()) {
                            result.add(
                                NewsDto(
                                    id = rs.getLong("id"),
                                    title = rs.getString("title") ?: "",
                                    body = rs.getString("body") ?: "",
                                    category = rs.getString("category") ?: "general",
                                    link = rs.getString("link") ?: "",
                                    publishAt = rs.getTimestamp("publish_at")?.toString() ?: "",
                                    createdAt = rs.getTimestamp("created_at")?.toString() ?: "",
                                    published = true,
                                ),
                            )
                        }
                    }
                }
            } catch (e: SQLException) {
                println("News.loadPublishedSince SQLException: ${e.message}")
            }
            return result
        }

        fun createNew(
            title: String,
            body: String,
            category: String,
            link: String?,
            publishAt: Timestamp?,
            database: KaraokeConnection,
            storageService: KaraokeStorageService = KSS_APP,
            storageApiClient: StorageApiClient = SAC_APP,
        ): News? {
            val entity = News(database = database, storageService = storageService, storageApiClient = storageApiClient)
            entity.title = title
            entity.body = body
            entity.category = category
            entity.link = link
            entity.publishAt = publishAt
            entity.createdAt = Timestamp(System.currentTimeMillis())
            return KaraokeDbTable.createDbInstance(entity = entity, database = database) as? News?
        }

        fun delete(
            id: Long,
            database: KaraokeConnection,
        ): Boolean = KaraokeDbTable.delete(tableName = TABLE_NAME, id = id, database = database)
    }
}
