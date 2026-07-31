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
// «Опубликовано» — вычисляемое условие publishAt <= now() (как Song.onAir), а не отдельный статус:
// новость с будущим publishAt, уже уехавшая на прод, сама «всплывает» в назначенный момент.

/**
 * Сущность «Новость» (пост в Telegram / Boosty / VK).
 *
 * Хранит:
 * - `id`, `idAuthor` — привязка к автору.
 * - `datePublicate` — дата публикации (используется для сортировки).
 * - `idTelegram`, `idBoosty`, `idVk` — ссылки на опубликованные копии.
 * - `text` — текст новости.
 * - `idPicture` — обложка (`Picture.kt`).
 *
 * Синхронизируется LOCAL↔SERVER.
 *
 * @see docs/features/dual-db-sync.md
 * @see docs/features/telegram-auto-publish.md
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

    // Ссылка на песню — заполняется только для авто-созданных новостей (specs/089-auto-news-song-release).
    // useInDiff=false: не должно участвовать в generic sync-diff (см. также source ниже и listHashes()).
    @KaraokeDbTableField(name = "song_id", useInDiff = false)
    var songId: Long? = null

    // "manual" (создана администратором через NewsController) | "auto" (создана
    // SongReleaseAnnouncementService). useInDiff=false, а строки с source="auto" полностью исключены
    // из listHashes() ниже — авто-новости физически существуют только на PROD и НЕ должны участвовать
    // в LOCAL↔SERVER hash-diff sync-движке (см. specs/089-auto-news-song-release/research.md, п.2):
    // иначе следующий admin-триггерный «1 клик» может стереть их как «отсутствующие в источнике»
    // (mirror-delete), если когда-либо включат sync_news_push_delete_allowed.
    @KaraokeDbTableField(name = "source", useInDiff = false)
    var source: String = "manual"

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
            source = source,
        )

    companion object {
        const val TABLE_NAME = "tbl_news"

        private fun isPublished(publishAt: Timestamp?): Boolean = publishAt != null && publishAt <= Timestamp(System.currentTimeMillis())

        // Используется ТОЛЬКО NewsSyncTarget (generic LOCAL↔SERVER sync-движок, sync/SyncTarget.kt).
        // Принудительно исключает source='auto' — авто-новости не должны попадать в hash-diff (см.
        // KDoc поля News.source выше и specs/089-auto-news-song-release/research.md, п.2).
        fun listHashes(
            database: KaraokeConnection,
            whereText: String = "",
        ): List<RecordHash>? {
            val autoExclusion = "source = 'manual'"
            val combinedWhere = if (whereText.isBlank()) "WHERE $autoExclusion" else "$whereText AND $autoExclusion"
            return KaraokeDbTable.getListHashes(tableName = TABLE_NAME, database = database, whereText = combinedWhere)
        }

        // Постранично (в т.ч. черновики/будущие) для админки — свежие сверху (specs/090-news-pagination).
        // Прямой SQL вместо generic KaraokeDbTable.loadList — LIMIT/OFFSET должен применяться в БД,
        // не после полной загрузки в память (та же проблема класса OOM, что и в
        // SongReleaseAnnouncementService, см. specs/089-auto-news-song-release/research.md п.5).
        fun loadAll(
            database: KaraokeConnection,
            limit: Int,
            offset: Int,
            storageService: KaraokeStorageService = KSS_APP,
            storageApiClient: StorageApiClient = SAC_APP,
        ): List<News> {
            val result: MutableList<News> = mutableListOf()
            val connection = database.getConnection() ?: return result
            val sql =
                """
                SELECT id FROM $TABLE_NAME
                ORDER BY id DESC
                LIMIT ? OFFSET ?
                """.trimIndent()
            val ids: MutableList<Long> = mutableListOf()
            try {
                connection.prepareStatement(sql).use { ps ->
                    ps.setInt(1, limit)
                    ps.setInt(2, offset)
                    ps.executeQuery().use { rs ->
                        while (rs.next()) ids.add(rs.getLong("id"))
                    }
                }
            } catch (e: SQLException) {
                println("News.loadAll SQLException: ${e.message}")
                return result
            }
            val byId =
                if (ids.isEmpty()) {
                    emptyMap()
                } else {
                    KaraokeDbTable
                        .loadList(
                            clazz = News::class,
                            tableName = TABLE_NAME,
                            whereList = listOf("id IN (${ids.joinToString(",")})"),
                            database = database,
                            storageService = storageService,
                            storageApiClient = storageApiClient,
                        ).map { it as News }
                        .associateBy { it.id }
                }
            ids.forEach { id -> byId[id]?.let { result.add(it) } }
            return result
        }

        // Общее число новостей — для пагинации админского списка (specs/090-news-pagination).
        fun countAll(database: KaraokeConnection): Long {
            val connection = database.getConnection() ?: return 0L
            val sql = "SELECT COUNT(*) FROM $TABLE_NAME"
            try {
                connection.prepareStatement(sql).use { ps ->
                    ps.executeQuery().use { rs ->
                        if (rs.next()) return rs.getLong(1)
                    }
                }
            } catch (e: SQLException) {
                println("News.countAll SQLException: ${e.message}")
            }
            return 0L
        }

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
        // Постранично (specs/090-news-pagination) — при 19000+ строках в tbl_news (см. FR-005
        // specs/089-auto-news-song-release) полная выгрузка одним запросом деградирует публичную
        // ленту; второй ключ сортировки id DESC — детерминированность на границе страниц, т.к.
        // publish_at не уникален (несколько авто-новостей одного прогона получают близкие значения).
        fun loadPublished(
            database: KaraokeConnection,
            limit: Int,
            offset: Int,
        ): List<NewsDto> {
            val result: MutableList<NewsDto> = mutableListOf()
            val connection = database.getConnection() ?: return result
            val sql =
                """
                SELECT id, title, body, category, link, publish_at, created_at, source
                FROM $TABLE_NAME
                WHERE publish_at IS NOT NULL AND publish_at <= now()
                ORDER BY publish_at DESC, id DESC
                LIMIT ? OFFSET ?
                """.trimIndent()
            try {
                connection.prepareStatement(sql).use { ps ->
                    ps.setInt(1, limit)
                    ps.setInt(2, offset)
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
                                    source = rs.getString("source") ?: "manual",
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

        // Общее число опубликованных новостей — для пагинации публичной ленты (specs/090-news-pagination).
        fun countPublished(database: KaraokeConnection): Long {
            val connection = database.getConnection() ?: return 0L
            val sql = "SELECT COUNT(*) FROM $TABLE_NAME WHERE publish_at IS NOT NULL AND publish_at <= now()"
            try {
                connection.prepareStatement(sql).use { ps ->
                    ps.executeQuery().use { rs ->
                        if (rs.next()) return rs.getLong(1)
                    }
                }
            } catch (e: SQLException) {
                println("News.countPublished SQLException: ${e.message}")
            }
            return 0L
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

        // Создаёт новость автоматически, без участия администратора — вызывается ТОЛЬКО
        // SongReleaseAnnouncementService при обнаружении песни, ставшей публично доступной
        // (specs/089-auto-news-song-release). publishAt = сейчас — новость публикуется немедленно
        // (иначе она осталась бы невидимым черновиком, см. research.md, п.6). source="auto" исключает
        // строку из LOCAL↔SERVER sync (см. listHashes() выше).
        fun createAutoAnnouncement(
            songId: Long,
            title: String,
            body: String,
            link: String,
            category: String = "air",
            database: KaraokeConnection,
            storageService: KaraokeStorageService = KSS_APP,
            storageApiClient: StorageApiClient = SAC_APP,
        ): News? {
            val entity = News(database = database, storageService = storageService, storageApiClient = storageApiClient)
            entity.title = title
            entity.body = body
            entity.category = category
            entity.link = link
            entity.publishAt = Timestamp(System.currentTimeMillis())
            entity.createdAt = Timestamp(System.currentTimeMillis())
            entity.songId = songId
            entity.source = "auto"
            return KaraokeDbTable.createDbInstance(entity = entity, database = database) as? News?
        }

        fun delete(
            id: Long,
            database: KaraokeConnection,
        ): Boolean = KaraokeDbTable.delete(tableName = TABLE_NAME, id = id, database = database)

        // Существование новости данного вида по песне (specs/101-song-news-flag) — единственная
        // идемпотентность механизма «в эфире» внутри узкого скользящего окна проверки (research.md
        // п.4): не более одной auto-новости категории "air" на песню, а ручная новость той же
        // категории также блокирует повторное авто-создание (source не фильтруется намеренно).
        // Матчинг по song_id ИЛИ по link: у ручных новостей (NewsController.create, webvue3
        // NewsTable.vue) сегодня нет поля для указания song_id — единственный способ администратору
        // связать вручную созданную новость с конкретной песней — вписать в поле "Ссылка" тот же
        // формат, что использует авто-создание (`/song?id={id}`). Без этой альтернативной проверки
        // ручная новость никогда не блокировала бы повторное авто-создание (FR-008 spec.md).
        fun existsAnnouncement(
            songId: Long,
            link: String,
            category: String,
            database: KaraokeConnection,
        ): Boolean {
            val connection = database.getConnection() ?: return false
            return try {
                connection
                    .prepareStatement(
                        "SELECT 1 FROM $TABLE_NAME WHERE category = ? AND (song_id = ? OR link = ?)",
                    ).use { ps ->
                        ps.setString(1, category)
                        ps.setLong(2, songId)
                        ps.setString(3, link)
                        ps.executeQuery().use { rs -> rs.next() }
                    }
            } catch (e: SQLException) {
                println("News.existsAnnouncement SQLException: ${e.message}")
                false
            }
        }
    }
}
