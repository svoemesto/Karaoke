package com.svoemesto.karaokeapp.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.WORKING_DATABASE
import com.svoemesto.karaokeapp.services.KSS_APP
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.SAC_APP
import com.svoemesto.karaokeapp.services.StorageApiClient
import org.springframework.security.crypto.password.PasswordEncoder
import java.io.Serializable
import java.sql.SQLException
import java.sql.Timestamp

// Пользователь публичного сайта (karaoke-public).

/**
 * Класс Site User.
 *
 * @see docs/features/dual-db-sync.md
 */
@JsonIgnoreProperties(value = ["database", "sqlToInsert"])
class SiteUser(
    override val database: KaraokeConnection = WORKING_DATABASE,
    override val storageService: KaraokeStorageService = KSS_APP,
    override val storageApiClient: StorageApiClient = SAC_APP,
) : Serializable,
    Comparable<SiteUser>,
    KaraokeDbTable {
    override fun getTableName() = TABLE_NAME

    @KaraokeDbTableField(name = "id", isId = true)
    override var id: Long = 0

    @KaraokeDbTableField(name = "email")
    var email: String = ""

    @KaraokeDbTableField(name = "password_hash")
    var passwordHash: String = ""

    @KaraokeDbTableField(name = "display_name")
    var displayName: String = ""

    @KaraokeDbTableField(name = "sponsr_uid")
    var sponsrUid: String = ""

    @KaraokeDbTableField(name = "is_premium")
    var isPremium: Boolean = false

    @KaraokeDbTableField(name = "is_permanent_premium")
    var isPermanentPremium: Boolean = false

    // Источники срочного премиума (временная ось). Nullable — колонки допускают SQL NULL,
    // поэтому поле объявлено Timestamp? (инвариант reflection-loader: NPE даёт non-null Kotlin-поле
    // на nullable колонке, не наоборот). Гашение по истечении срока — без отдельного планировщика,
    // просто сравнение с now() в isEffectivePremium ниже.
    @KaraokeDbTableField(name = "sponsr_premium_until")
    var sponsrPremiumUntil: Timestamp? = null

    @KaraokeDbTableField(name = "site_premium_until")
    var sitePremiumUntil: Timestamp? = null

    // Флаг однократной отправки приветственного сообщения в чат с автором при первом получении
    // премиума (см. sendWelcomePremiumMessageIfNeeded ниже) — без него повторные вебхуки/скользящее
    // окно Sponsr-синка слали бы сообщение заново при каждом продлении.
    @KaraokeDbTableField(name = "welcome_message_sent")
    var welcomeMessageSent: Boolean = false

    // Постоянная персональная скидка (%), выставляется вручную админом (SiteUsersController).
    // Суммируется поверх ЛЮБОЙ акции (см. PriceService) и применяется к любому заказу — не
    // конкурирует с tbl_promo_rules, а уменьшает итоговую цену ДОПОЛНИТЕЛЬНО. 0 = скидки нет.
    @KaraokeDbTableField(name = "personal_discount_percent")
    var personalDiscountPercent: Double = 0.0

    // Не БД-поле — единая точка правды для всех проверок премиум-доступа (плеер и т.п.):
    // is_permanent_premium/is_premium — вечный/ручной грант админа; sponsr_premium_until —
    // проставляется Sponsr-синхронизацией; site_premium_until — продлевается оплатой подписки на
    // сайт (в т.ч. автопродлением). Живая проверка срока, не кэшируется.
    val isEffectivePremium: Boolean
        get() {
            val now = System.currentTimeMillis()
            return isPremium ||
                isPermanentPremium ||
                (sponsrPremiumUntil?.time?.let { it > now } == true) ||
                (sitePremiumUntil?.time?.let { it > now } == true)
        }

    // Вызывается сразу после того, как код перевёл пользователя в isEffectivePremium=true (оплата
    // подписки на сайт, акционная бесплатная подписка, Sponsr-синхронизация) — единая точка правды,
    // чтобы не дублировать проверку "первый ли это премиум" в каждом из вызывающих мест. No-op, если
    // сообщение уже отправлялось раньше или пользователь всё ещё не премиум (например SCOPE_SONG,
    // который не даёт isEffectivePremium). Использует database/storageService/storageApiClient,
    // с которыми уже загружен сам объект — вызывающему коду не нужно передавать их повторно.
    fun sendWelcomePremiumMessageIfNeeded() {
        if (welcomeMessageSent) return
        if (!isEffectivePremium) return
        SiteChatMessage.createNew(
            siteUserId = id,
            isFromAuthor = true,
            body = WELCOME_PREMIUM_MESSAGE,
            database = database,
            storageService = storageService,
            storageApiClient = storageApiClient,
        )
        welcomeMessageSent = true
        save()
    }

    // Персональные лимиты (0 = использовать дефолт в PublicPlaylistController). Int безопасен для
    // reflection-loader на SQL NULL (getInt→0), но колонки и так NOT NULL DEFAULT 0.
    @KaraokeDbTableField(name = "max_favorites")
    var maxFavorites: Int = 0

    @KaraokeDbTableField(name = "max_playlists")
    var maxPlaylists: Int = 0

    @KaraokeDbTableField(name = "max_playlist_items")
    var maxPlaylistItems: Int = 0

    @KaraokeDbTableField(name = "is_editor")
    var isEditor: Boolean = false

    @KaraokeDbTableField(name = "is_banned")
    var isBanned: Boolean = false

    @KaraokeDbTableField(name = "ban_reason")
    var banReason: String = ""

    @KaraokeDbTableField(name = "last_login_at")
    var lastLoginAt: Timestamp = Timestamp(0)

    @KaraokeDbTableField(name = "created_at")
    var createdAt: Timestamp = Timestamp(0)

    override fun compareTo(other: SiteUser): Int = email.compareTo(other.email)

    fun checkPassword(
        rawPassword: String,
        passwordEncoder: PasswordEncoder,
    ): Boolean = passwordEncoder.matches(rawPassword, passwordHash)

    fun setPassword(
        rawPassword: String,
        passwordEncoder: PasswordEncoder,
    ) {
        passwordHash = passwordEncoder.encode(rawPassword)
    }

    override fun toDTO(): SiteUserDto =
        SiteUserDto(
            id = id,
            email = email,
            displayName = displayName,
            sponsrUid = sponsrUid,
            isPremium = isPremium,
            isPermanentPremium = isPermanentPremium,
            isEffectivePremium = isEffectivePremium,
            sponsrPremiumUntil = sponsrPremiumUntil?.toString(),
            sitePremiumUntil = sitePremiumUntil?.toString(),
            personalDiscountPercent = personalDiscountPercent,
            isEditor = isEditor,
            isBanned = isBanned,
            banReason = banReason,
            maxFavorites = maxFavorites,
            maxPlaylists = maxPlaylists,
            maxPlaylistItems = maxPlaylistItems,
            createdAt = createdAt.toString(),
            lastLoginAt = lastLoginAt.toString(),
            welcomeMessageSent = welcomeMessageSent,
        )

    companion object {
        const val TABLE_NAME = "tbl_site_users"

        const val WELCOME_PREMIUM_MESSAGE =
            "Приветствую!\n" +
                "Спасибо за оформление премиум-подписки — это действительно очень важно для меня.\n" +
                "Надеюсь, что пользование этим сервисом доставит радость и удовольствие!\n" +
                "Если будут какие-то вопросы или предложения — я всегда открыт к диалогу.\n" +
                "С уважением,\n" +
                "автор проекта «Караоке на \"Своём Месте\"»\n" +
                "Новиков Сергей"

        private fun getWhereList(whereArgs: Map<String, String>): List<String> {
            val where: MutableList<String> = mutableListOf()
            if (whereArgs.containsKey("id")) where += "id=${whereArgs["id"]}"
            if (whereArgs.containsKey("email")) where += "LOWER(email) LIKE '%${whereArgs["email"]?.lowercase()?.replace("'", "''")}%'"
            if (whereArgs.containsKey("displayName")) {
                where +=
                    "LOWER(display_name) LIKE '%${whereArgs["displayName"]?.lowercase()?.replace("'", "''")}%'"
            }
            if (whereArgs.containsKey("sponsrUid")) {
                where +=
                    "LOWER(sponsr_uid) LIKE '%${whereArgs["sponsrUid"]?.lowercase()?.replace("'", "''")}%'"
            }
            if (whereArgs.containsKey("isPremium")) {
                if (whereArgs["isPremium"] == "+" || whereArgs["isPremium"] == "true") {
                    where += "is_premium = true"
                } else if (whereArgs["isPremium"] == "-" || whereArgs["isPremium"] == "false") {
                    where += "is_premium = false"
                }
            }
            if (whereArgs.containsKey("isPermanentPremium")) {
                if (whereArgs["isPermanentPremium"] == "+" || whereArgs["isPermanentPremium"] == "true") {
                    where += "is_permanent_premium = true"
                } else if (whereArgs["isPermanentPremium"] == "-" || whereArgs["isPermanentPremium"] == "false") {
                    where += "is_permanent_premium = false"
                }
            }
            if (whereArgs.containsKey("isEffectivePremium")) {
                // Зеркалит логику геттера isEffectivePremium (см. выше) в SQL — эта величина не
                // хранится отдельной колонкой, а считается на лету из 4 полей + текущего времени.
                val effectivePremiumExpr =
                    "(is_premium = true OR is_permanent_premium = true " +
                        "OR (sponsr_premium_until IS NOT NULL AND sponsr_premium_until > now()) " +
                        "OR (site_premium_until IS NOT NULL AND site_premium_until > now()))"
                if (whereArgs["isEffectivePremium"] == "+" || whereArgs["isEffectivePremium"] == "true") {
                    where += effectivePremiumExpr
                } else if (whereArgs["isEffectivePremium"] == "-" || whereArgs["isEffectivePremium"] == "false") {
                    where += "NOT $effectivePremiumExpr"
                }
            }
            if (whereArgs.containsKey("isBanned")) {
                if (whereArgs["isBanned"] == "+" || whereArgs["isBanned"] == "true") {
                    where += "is_banned = true"
                } else if (whereArgs["isBanned"] == "-" || whereArgs["isBanned"] == "false") {
                    where += "is_banned = false"
                }
            }
            if (whereArgs.containsKey("isEditor")) {
                if (whereArgs["isEditor"] == "+" || whereArgs["isEditor"] == "true") {
                    where += "is_editor = true"
                } else if (whereArgs["isEditor"] == "-" || whereArgs["isEditor"] == "false") {
                    where += "is_editor = false"
                }
            }
            return where
        }

        fun loadList(
            whereArgs: Map<String, String>,
            limit: Int = 0,
            offset: Int = 0,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): List<SiteUser> =
            KaraokeDbTable
                .loadList(
                    clazz = SiteUser::class,
                    tableName = TABLE_NAME,
                    whereList = getWhereList(whereArgs),
                    limit = limit,
                    offset = offset,
                    database = database,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                ).map { it as SiteUser }

        // Пользователи, у которых подписка на сайт истекает до указанного момента (включая уже
        // истёкшую — грейс-период для повторных попыток автосписания после сбоя). Используется
        // SubscriptionRenewalScheduler (karaoke-web). site_premium_until IS NOT NULL исключает тех,
        // у кого срочной подписки на сайт вообще нет (только вечный/ручной премиум или её отсутствие).
        fun loadSitePremiumExpiringBefore(
            before: Timestamp,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): List<SiteUser> =
            KaraokeDbTable
                .loadList(
                    clazz = SiteUser::class,
                    tableName = TABLE_NAME,
                    whereList = listOf("site_premium_until IS NOT NULL", "site_premium_until < '$before'::timestamp"),
                    database = database,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                ).map { it as SiteUser }

        fun getSiteUserById(
            id: Long,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): SiteUser? =
            KaraokeDbTable.loadById(
                clazz = SiteUser::class,
                tableName = TABLE_NAME,
                id = id,
                database = database,
                storageService = storageService,
                storageApiClient = storageApiClient,
            ) as? SiteUser?

        fun getSiteUserByEmail(
            email: String,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): SiteUser? =
            KaraokeDbTable
                .loadList(
                    clazz = SiteUser::class,
                    tableName = TABLE_NAME,
                    whereList = listOf("LOWER(email) = LOWER('${email.replace("'", "''")}')"),
                    database = database,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                ).firstOrNull() as? SiteUser?

        fun createNewSiteUser(
            email: String,
            rawPassword: String,
            displayName: String,
            database: KaraokeConnection,
            passwordEncoder: PasswordEncoder,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): SiteUser? {
            if (getSiteUserByEmail(email, database, storageService, storageApiClient) != null) return null
            val newUser = SiteUser(database = database, storageService = storageService, storageApiClient = storageApiClient)
            newUser.email = email
            newUser.setPassword(rawPassword, passwordEncoder)
            newUser.displayName = displayName
            val now = Timestamp(System.currentTimeMillis())
            newUser.createdAt = now
            newUser.lastLoginAt = now
            return KaraokeDbTable.createDbInstance(entity = newUser, database = database) as? SiteUser?
        }

        fun deleteSiteUser(
            id: Long,
            database: KaraokeConnection,
        ): Boolean = KaraokeDbTable.delete(tableName = TABLE_NAME, id = id, database = database)

        // Поиск по email ИЛИ имени одной подстрокой (OR, не выразить через getWhereList — там поля
        // ANDятся) — для «Начать чат» в webvue3 (ChatController.searchUsers). Raw-SELECT id-шников
        // (паттерн — Author.resolveByTerm()), затем batch-догрузка полных сущностей через loadByIds.
        fun searchByTerm(
            term: String,
            limit: Int,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): List<SiteUser> {
            val t = term.trim().lowercase()
            if (t.isEmpty()) return emptyList()
            val ids: MutableList<Long> = mutableListOf()
            val connection = database.getConnection() ?: return emptyList()
            val sql = "SELECT id FROM $TABLE_NAME WHERE LOWER(email) LIKE ? OR LOWER(display_name) LIKE ? ORDER BY id LIMIT ?"
            try {
                connection.prepareStatement(sql).use { ps ->
                    ps.setString(1, "%$t%")
                    ps.setString(2, "%$t%")
                    ps.setInt(3, limit)
                    ps.executeQuery().use { rs -> while (rs.next()) ids.add(rs.getLong("id")) }
                }
            } catch (e: SQLException) {
                println("SiteUser.searchByTerm SQLException: ${e.message}")
            }
            if (ids.isEmpty()) return emptyList()
            return KaraokeDbTable
                .loadByIds(
                    clazz = SiteUser::class,
                    tableName = TABLE_NAME,
                    ids = ids,
                    database = database,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                ).map { it as SiteUser }
        }
    }
}
