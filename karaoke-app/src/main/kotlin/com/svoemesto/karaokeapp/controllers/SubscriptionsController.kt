package com.svoemesto.karaokeapp.controllers

import com.svoemesto.karaokeapp.Connection
import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.model.KaraokeDbTable
import com.svoemesto.karaokeapp.model.PriceTariff
import com.svoemesto.karaokeapp.model.SiteUser
import com.svoemesto.karaokeapp.model.Song
import com.svoemesto.karaokeapp.model.Subscription
import com.svoemesto.karaokeapp.services.KSS_APP
import com.svoemesto.karaokeapp.services.SAC_APP
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody

// Админский read-only просмотр всех подписок (`tbl_subscriptions`). Тот же паттерн, что и
// SitePlaylistsController: target=local|remote выбирается явно клиентом (webvue3); реальные
// платежи проходят на боевой БД, поэтому админ может смотреть обе. Фильтры — scope/status/userId/
// songId/createdFrom/createdTo. Пагинация — page/pageSize (clamp 1..100, default 25). JOIN к
// tbl_site_users / tbl_songs / tbl_price_tariffs — батчем через associateBy, чтобы не дёргать
// БД по одной записи на каждый row (паттерн производительности — см. AGENTS.md «Синхронизация
// LOCAL↔SERVER»). Сортировка по created_at DESC.

/**
 * Контроллер (HTTP/WebSocket endpoints) для глобального списка подписок из `tbl_subscriptions`.
 *
 * Поддерживает фильтры (scope/status/userId/songId/диапазон дат), пагинацию (offset+limit),
 * target-aware выбор БД (local|remote). Read-only — никаких мутаций (в отличие от share-ссылок).
 *
 * Эндпоинты:
 * - `POST /api/subscriptions/digest` — список подписок (FR-001…FR-007).
 *
 * @see specs/171-admin-subscriptions-history/contracts/subscriptions-digest.md
 * @see AGENTS.md
 */
@Controller
@RequestMapping("/api/subscriptions")
class SubscriptionsController {
    private fun resolveDb(target: String?): KaraokeConnection = if (target == "remote") Connection.remote() else Connection.local()

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
        @RequestParam(required = false, defaultValue = "1") page: Int,
        @RequestParam(required = false, defaultValue = "25") pageSize: Int,
        @RequestParam(required = false) filterScope: String?,
        @RequestParam(required = false) filterStatus: String?,
        @RequestParam(required = false) filterUserId: Long?,
        @RequestParam(required = false) filterSongId: Long?,
        @RequestParam(required = false) filterCreatedFrom: String?,
        @RequestParam(required = false) filterCreatedTo: String?,
        @RequestParam(required = false, defaultValue = "created_at") sortBy: String?,
        @RequestParam(required = false, defaultValue = "DESC") sortDir: String?,
    ): Map<String, Any> =
        withDb(target) { db ->
            // 1. Собираем whereList из непустых фильтров.
            val whereList = mutableListOf<String>()
            filterScope?.takeIf { it.isNotBlank() }?.let { whereList.add("scope='${it.replace("'", "''")}'") }
            filterStatus?.takeIf { it.isNotBlank() }?.let { whereList.add("status='${it.replace("'", "''")}'") }
            filterUserId?.let { whereList.add("site_user_id=$it") }
            filterSongId?.let { whereList.add("id_song=$it") }
            filterCreatedFrom?.takeIf { it.isNotBlank() }?.let { whereList.add("created_at >= '${it.replace("'", "''")}'") }
            filterCreatedTo?.takeIf { it.isNotBlank() }?.let { whereList.add("created_at <= '${it.replace("'", "''")}'") }

            // 2. Clamp пагинации.
            val safePage = if (page < 1) 1 else page
            val safePageSize = pageSize.coerceIn(1, 100)
            val offset = (safePage - 1) * safePageSize

            // 3. Подгружаем ВСЕ подписки по where (без limit/offset), фильтруем в памяти и
            //    сортируем — для ~10k записей на проде это допустимо. Альтернатива — SQL с
            //    LIMIT/OFFSET (через KaraokeDbTable.loadList) + реверс-сортировка, но тогда
            //    нужны два SQL (для totalCount и для page), что сложнее и всё равно грузит
            //    данные по тем же строкам. JOIN-обогащение делаем ниже одним батчем.
            val allLoaded =
                KaraokeDbTable
                    .loadList(
                        clazz = Subscription::class,
                        tableName = Subscription.TABLE_NAME,
                        whereList = whereList,
                        database = db,
                        storageService = KSS_APP,
                        storageApiClient = SAC_APP,
                    ).map { it as Subscription }

            // 4. Сортировка (whitelist колонок против SQL-инъекции; default — created_at DESC).
            val sortColumn =
                when (sortBy) {
                    "created_at", "paid_at", "final_price" -> sortBy
                    else -> "created_at"
                }
            val sortDirection = if (sortDir?.uppercase() == "ASC") "ASC" else "DESC"
            val sorted =
                when (sortColumn) {
                    "paid_at" ->
                        if (sortDirection == "ASC") {
                            allLoaded.sortedBy { it.paidAt?.time ?: 0L }
                        } else {
                            allLoaded.sortedByDescending { it.paidAt?.time ?: 0L }
                        }
                    "final_price" ->
                        if (sortDirection == "ASC") {
                            allLoaded.sortedBy { it.finalPrice }
                        } else {
                            allLoaded.sortedByDescending { it.finalPrice }
                        }
                    else ->
                        if (sortDirection == "ASC") {
                            allLoaded.sortedBy { it.createdAt.time }
                        } else {
                            allLoaded.sortedByDescending { it.createdAt.time }
                        }
                }
            val totalCount = sorted.size
            val pageItems = sorted.drop(offset).take(safePageSize)

            // 5. Батч-JOIN к tbl_site_users / tbl_songs / tbl_price_tariffs (паттерн — см.
            //    AGENTS.md «Синхронизация LOCAL↔SERVER — критичные паттерны производительности»).
            val userIds = pageItems.map { it.siteUserId }.distinct()
            val songIds = pageItems.mapNotNull { it.idSong }.distinct()
            val tariffIds = pageItems.mapNotNull { it.tariffId }.distinct()
            val usersById =
                if (userIds.isEmpty()) {
                    emptyMap()
                } else {
                    KaraokeDbTable
                        .loadByIds(SiteUser::class, SiteUser.TABLE_NAME, userIds, db, KSS_APP, SAC_APP)
                        .map { it as SiteUser }
                        .associateBy { it.id }
                }
            val songsById =
                if (songIds.isEmpty()) {
                    emptyMap()
                } else {
                    KaraokeDbTable
                        .loadByIds(Song::class, Song.TABLE_NAME, songIds, db, KSS_APP, SAC_APP)
                        .map { it as Song }
                        .associateBy { it.id }
                }
            val tariffsById =
                if (tariffIds.isEmpty()) {
                    emptyMap()
                } else {
                    KaraokeDbTable
                        .loadByIds(PriceTariff::class, PriceTariff.TABLE_NAME, tariffIds, db, KSS_APP, SAC_APP)
                        .map { it as PriceTariff }
                        .associateBy { it.id }
                }

            val list =
                pageItems.map { sub ->
                    val user = usersById[sub.siteUserId]
                    val song = sub.idSong?.let { songsById[it] }
                    val tariff = sub.tariffId?.let { tariffsById[it] }
                    mapOf(
                        "id" to sub.id,
                        "siteUserId" to sub.siteUserId,
                        "userEmail" to (user?.email ?: ""),
                        "userDisplayName" to (user?.displayName ?: ""),
                        "scope" to sub.scope,
                        "idSong" to sub.idSong,
                        "songName" to (song?.songName ?: ""),
                        "tariffId" to sub.tariffId,
                        "tariffName" to (tariff?.name ?: ""),
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
            mapOf(
                "subscriptionsDigest" to list,
                "totalCount" to totalCount,
                "page" to safePage,
                "pageSize" to safePageSize,
            )
        }
}
