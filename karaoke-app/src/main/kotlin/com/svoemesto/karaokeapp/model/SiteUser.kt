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
import java.sql.Timestamp

// Пользователь публичного сайта (karaoke-public).
@JsonIgnoreProperties(value = ["database", "sqlToInsert"])
class SiteUser(
    override val database: KaraokeConnection = WORKING_DATABASE,
    override val storageService: KaraokeStorageService = KSS_APP,
    override val storageApiClient: StorageApiClient = SAC_APP,
) : Serializable, Comparable<SiteUser>, KaraokeDbTable {

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
            return isPremium || isPermanentPremium
                    || (sponsrPremiumUntil?.time?.let { it > now } == true)
                    || (sitePremiumUntil?.time?.let { it > now } == true)
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

    fun checkPassword(rawPassword: String, passwordEncoder: PasswordEncoder): Boolean =
        passwordEncoder.matches(rawPassword, passwordHash)

    fun setPassword(rawPassword: String, passwordEncoder: PasswordEncoder) {
        passwordHash = passwordEncoder.encode(rawPassword)
    }

    override fun toDTO(): SiteUserDto = SiteUserDto(
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
    )

    companion object {

        const val TABLE_NAME = "tbl_site_users"

        private fun getWhereList(whereArgs: Map<String, String>): List<String> {
            val where: MutableList<String> = mutableListOf()
            if (whereArgs.containsKey("id")) where += "id=${whereArgs["id"]}"
            if (whereArgs.containsKey("email")) where += "LOWER(email) LIKE '%${whereArgs["email"]?.lowercase()?.replace("'", "''")}%'"
            if (whereArgs.containsKey("displayName")) where += "LOWER(display_name) LIKE '%${whereArgs["displayName"]?.lowercase()?.replace("'", "''")}%'"
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
        ): List<SiteUser> {
            return KaraokeDbTable.loadList(
                clazz = SiteUser::class,
                tableName = TABLE_NAME,
                whereList = getWhereList(whereArgs),
                limit = limit,
                offset = offset,
                database = database,
                storageService = storageService,
                storageApiClient = storageApiClient,
            ).map { it as SiteUser }
        }

        // Пользователи, у которых подписка на сайт истекает до указанного момента (включая уже
        // истёкшую — грейс-период для повторных попыток автосписания после сбоя). Используется
        // SubscriptionRenewalScheduler (karaoke-web). site_premium_until IS NOT NULL исключает тех,
        // у кого срочной подписки на сайт вообще нет (только вечный/ручной премиум или её отсутствие).
        fun loadSitePremiumExpiringBefore(
            before: Timestamp,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): List<SiteUser> = KaraokeDbTable.loadList(
            clazz = SiteUser::class,
            tableName = TABLE_NAME,
            whereList = listOf("site_premium_until IS NOT NULL", "site_premium_until < '$before'::timestamp"),
            database = database,
            storageService = storageService,
            storageApiClient = storageApiClient,
        ).map { it as SiteUser }

        fun getSiteUserById(id: Long, database: KaraokeConnection, storageService: KaraokeStorageService, storageApiClient: StorageApiClient): SiteUser? {
            return KaraokeDbTable.loadById(
                clazz = SiteUser::class,
                tableName = TABLE_NAME,
                id = id,
                database = database,
                storageService = storageService,
                storageApiClient = storageApiClient
            ) as? SiteUser?
        }

        fun getSiteUserByEmail(email: String, database: KaraokeConnection, storageService: KaraokeStorageService, storageApiClient: StorageApiClient): SiteUser? {
            return KaraokeDbTable.loadList(
                clazz = SiteUser::class,
                tableName = TABLE_NAME,
                whereList = listOf("LOWER(email) = LOWER('${email.replace("'", "''")}')"),
                database = database,
                storageService = storageService,
                storageApiClient = storageApiClient
            ).firstOrNull() as? SiteUser?
        }

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

        fun deleteSiteUser(id: Long, database: KaraokeConnection): Boolean {
            return KaraokeDbTable.delete(tableName = TABLE_NAME, id = id, database = database)
        }
    }
}
