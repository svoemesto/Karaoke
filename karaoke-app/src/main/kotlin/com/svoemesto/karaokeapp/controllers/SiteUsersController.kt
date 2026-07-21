package com.svoemesto.karaokeapp.controllers

import com.svoemesto.karaokeapp.Connection
import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.model.PriceTariff
import com.svoemesto.karaokeapp.model.Settings
import com.svoemesto.karaokeapp.model.SiteUser
import com.svoemesto.karaokeapp.model.Subscription
import com.svoemesto.karaokeapp.services.KSS_APP
import com.svoemesto.karaokeapp.services.SAC_APP
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import java.sql.Timestamp

// Админка пользователей ПУБЛИЧНОГО САЙТА (tbl_site_users). Ключевое отличие от всех остальных ручных
// CRUD-контроллеров в проекте:
// параметр target выбирается явно клиентом (webvue3), а не берётся из compile-time WORKING_DATABASE —
// реальные посетители сайта регистрируются на боевой БД сервера, а не в локальной dev-БД, поэтому
// админке нужно уметь смотреть и туда, и туда.

/**
 * Контроллер (HTTP/WebSocket endpoints) для site users .
 *
 * @see AGENTS.md
 */
@Controller
@RequestMapping("/api/siteusers")
class SiteUsersController {
    private fun resolveDb(target: String?): KaraokeConnection = if (target == "remote") Connection.remote() else Connection.local()

    // resolveDb() создаёт НОВЫЙ объект Connection.local()/remote() на каждый вызов, открывающий
    // собственное физическое JDBC-соединение и кэширующий его в себе; без явного close() оно висит
    // до обрыва и постепенно исчерпывает пул Postgres ("too many clients already"). withDb закрывает
    // соединение сразу после использования. То же самое сделано в StatsController/PublicSettingsController.
    private fun <T> withDb(
        target: String?,
        block: (KaraokeConnection) -> T,
    ): T {
        val db = resolveDb(target)
        return try {
            block(db)
        } finally {
            try {
                db.getConnection()?.close()
            } catch (_: Exception) {
            }
        }
    }

    @PostMapping("/digest")
    @ResponseBody
    fun digest(
        @RequestParam(required = false) target: String?,
        // Валидируется до попадания в args["id"] — SiteUser.getWhereList() интерполирует его в SQL
        // без экранирования (числовое сравнение), toLongOrNull() не даёт произвольной строке попасть в запрос.
        @RequestParam(required = false) filterId: String?,
        @RequestParam(required = false) filterEmail: String?,
        @RequestParam(required = false) filterDisplayName: String?,
        @RequestParam(required = false) filterSponsrUid: String?,
        @RequestParam(required = false) filterIsPremium: String?,
        @RequestParam(required = false) filterIsPermanentPremium: String?,
        @RequestParam(required = false) filterIsEffectivePremium: String?,
        @RequestParam(required = false) filterIsBanned: String?,
        @RequestParam(required = false) filterIsEditor: String?,
    ): Map<String, Any> =
        withDb(target) { db ->
            val args: MutableMap<String, String> = mutableMapOf()
            filterId?.toLongOrNull()?.let { args["id"] = it.toString() }
            filterEmail?.let { if (it != "") args["email"] = it }
            filterDisplayName?.let { if (it != "") args["displayName"] = it }
            filterSponsrUid?.let { if (it != "") args["sponsrUid"] = it }
            filterIsPremium?.let { if (it != "") args["isPremium"] = it }
            filterIsPermanentPremium?.let { if (it != "") args["isPermanentPremium"] = it }
            filterIsEffectivePremium?.let { if (it != "") args["isEffectivePremium"] = it }
            filterIsBanned?.let { if (it != "") args["isBanned"] = it }
            filterIsEditor?.let { if (it != "") args["isEditor"] = it }

            val list =
                SiteUser
                    .loadList(
                        whereArgs = args,
                        database = db,
                        storageService = KSS_APP,
                        storageApiClient = SAC_APP,
                    ).map { it.toDTO() }
            mapOf("siteUsersDigest" to list)
        }

    @PostMapping("/byId")
    @ResponseBody
    fun byId(
        @RequestParam id: Long,
        @RequestParam(required = false) target: String?,
    ): Any? = withDb(target) { db -> SiteUser.getSiteUserById(id, db, KSS_APP, SAC_APP)?.toDTO() }

    @PostMapping("/update")
    @ResponseBody
    fun update(
        @RequestParam id: Long,
        @RequestParam(required = false) target: String?,
        @RequestParam(required = false) displayName: String?,
        @RequestParam(required = false) sponsrUid: String?,
        // Ручная простановка премиум-статуса — временная замена автоматической Sponsr-сверки
        // (см. раздел 7 плана: пока не реализован импорт Excel-выгрузки, админ выставляет вручную).
        @RequestParam(required = false) isPremium: Boolean?,
        // Независимый флаг "вечного" премиума — делает пользователя премиумным, даже если isPremium
        // не выставлен (например, автоматическая Sponsr-сверка сбросит isPremium в будущем).
        @RequestParam(required = false) isPermanentPremium: Boolean?,
        // Роль "редактор" — доступ к онлайн-редактору разметки (/account/editor) на публичном сайте.
        @RequestParam(required = false) isEditor: Boolean?,
        // Персональные лимиты (0 = дефолт). Перекрывают дефолты в PublicPlaylistController.
        @RequestParam(required = false) maxFavorites: Int?,
        @RequestParam(required = false) maxPlaylists: Int?,
        @RequestParam(required = false) maxPlaylistItems: Int?,
        // Постоянная скидка (%) — вручную, суммируется поверх любой акции (PriceService).
        @RequestParam(required = false) personalDiscountPercent: Double?,
        // Даты окончания срочного премиума — в норме проставляются Sponsr-синхронизацией/оплатой
        // подписки, но админ может подправить/выдать/отозвать вручную. Пустая строка = очистить (null),
        // не спутать с отсутствием параметра (null Kotlin-параметр = поле не трогать).
        @RequestParam(required = false) sponsrPremiumUntil: String?,
        @RequestParam(required = false) sitePremiumUntil: String?,
        // Флаг однократной отправки приветственного сообщения — сброс в false заставит
        // sendWelcomePremiumMessageIfNeeded() отправить его заново при следующем получении премиума.
        @RequestParam(required = false) welcomeMessageSent: Boolean?,
        // NOT NULL-колонки — в отличие от премиум-дат, пустая строка здесь игнорируется (не может
        // означать null), просто ничего не меняет.
        @RequestParam(required = false) createdAt: String?,
        @RequestParam(required = false) lastLoginAt: String?,
    ): Long =
        withDb(target) { db ->
            SiteUser.getSiteUserById(id, db, KSS_APP, SAC_APP)?.let { user ->
                displayName?.let { user.displayName = it }
                sponsrUid?.let { user.sponsrUid = it }
                isPremium?.let { user.isPremium = it }
                isPermanentPremium?.let { user.isPermanentPremium = it }
                isEditor?.let { user.isEditor = it }
                maxFavorites?.let { user.maxFavorites = it }
                maxPlaylists?.let { user.maxPlaylists = it }
                maxPlaylistItems?.let { user.maxPlaylistItems = it }
                personalDiscountPercent?.let { user.personalDiscountPercent = it.coerceIn(0.0, 100.0) }
                sponsrPremiumUntil?.let { user.sponsrPremiumUntil = if (it == "") null else Timestamp.valueOf(it) }
                sitePremiumUntil?.let { user.sitePremiumUntil = if (it == "") null else Timestamp.valueOf(it) }
                welcomeMessageSent?.let { user.welcomeMessageSent = it }
                createdAt?.let { if (it != "") user.createdAt = Timestamp.valueOf(it) }
                lastLoginAt?.let { if (it != "") user.lastLoginAt = Timestamp.valueOf(it) }
                user.save()
                user.id
            } ?: 0L
        }

    @PostMapping("/ban")
    @ResponseBody
    fun ban(
        @RequestParam id: Long,
        @RequestParam(required = false) target: String?,
        @RequestParam reason: String,
    ): Boolean =
        withDb(target) { db ->
            SiteUser.getSiteUserById(id, db, KSS_APP, SAC_APP)?.let { user ->
                user.isBanned = true
                user.banReason = reason
                user.save()
                true
            } ?: false
        }

    @PostMapping("/unban")
    @ResponseBody
    fun unban(
        @RequestParam id: Long,
        @RequestParam(required = false) target: String?,
    ): Boolean =
        withDb(target) { db ->
            SiteUser.getSiteUserById(id, db, KSS_APP, SAC_APP)?.let { user ->
                user.isBanned = false
                user.banReason = ""
                user.save()
                true
            } ?: false
        }

    @PostMapping("/delete")
    @ResponseBody
    fun delete(
        @RequestParam id: Long,
        @RequestParam(required = false) target: String?,
    ): Boolean = withDb(target) { db -> SiteUser.deleteSiteUser(id, db) }

    // История подписок/покупок пользователя для карточки в админке — те же записи, что видит сам
    // пользователь в личном кабинете (PublicSubscriptionController.list, karaoke-web), но здесь читаем
    // напрямую из karaoke-app (без HTTP-вызова, которого для karaoke-app на проде и быть не может —
    // см. инвариант "karaoke-app не на проде"), плюс название тарифа для scope=SITE (в личном кабинете
    // не нужно — там подписка всегда на уже известный тариф этого же похода).
    @PostMapping("/subscriptions")
    @ResponseBody
    fun subscriptions(
        @RequestParam id: Long,
        @RequestParam(required = false) target: String?,
    ): List<Map<String, Any?>> =
        withDb(target) { db ->
            Subscription.loadByUser(id, db, KSS_APP, SAC_APP).map { sub ->
                val songName =
                    sub.idSong?.let {
                        Settings
                            .loadFromDbById(
                                it,
                                db,
                                storageService = KSS_APP,
                                storageApiClient = SAC_APP,
                            )?.songName
                    }
                val tariffName = sub.tariffId?.let { PriceTariff.getById(it, db, KSS_APP, SAC_APP)?.name }
                mapOf(
                    "id" to sub.id,
                    "scope" to sub.scope,
                    "idSong" to sub.idSong,
                    "songName" to songName,
                    "tariffId" to sub.tariffId,
                    "tariffName" to tariffName,
                    "periodDays" to sub.periodDays,
                    "basePrice" to sub.basePrice,
                    "discount" to sub.discount,
                    "finalPrice" to sub.finalPrice,
                    "promoApplied" to sub.promoApplied,
                    "status" to sub.status,
                    "autoRenew" to sub.autoRenew,
                    "createdAt" to sub.createdAt.toString(),
                    "paidAt" to sub.paidAt?.toString(),
                    "orderId" to sub.orderId,
                )
            }
        }
}
