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

// Пользователь публичного сайта (karaoke-public). НЕ путать с Users (админские логины webvue3, tbl_users).
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
        isBanned = isBanned,
        banReason = banReason,
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
