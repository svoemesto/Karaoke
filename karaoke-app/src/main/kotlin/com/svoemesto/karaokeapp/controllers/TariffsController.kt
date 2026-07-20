package com.svoemesto.karaokeapp.controllers

import com.svoemesto.karaokeapp.Connection
import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.model.PriceTariff
import com.svoemesto.karaokeapp.services.KSS_APP
import com.svoemesto.karaokeapp.services.SAC_APP
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody

// Админка тарифов подписки (tbl_price_tariffs). Target-aware, как SiteUsersController: реальные
// тарифы правятся на прод-БД (target=remote), т.к. платёжный конвейер karaoke-web работает на
// проде и читает тарифы оттуда; локальная БД используется для теста/разработки.
@Controller
@RequestMapping("/api/tariffs")
class TariffsController {
    private fun resolveDb(target: String?): KaraokeConnection = if (target == "remote") Connection.remote() else Connection.local()

    // См. комментарий к withDb в SiteUsersController — resolveDb() открывает новое физическое
    // соединение на каждый вызов; без явного close() пул Postgres постепенно исчерпывается.
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

    @PostMapping("/list")
    @ResponseBody
    fun list(
        @RequestParam(required = false) target: String?,
    ): Map<String, Any> =
        withDb(target) { db ->
            mapOf("tariffs" to PriceTariff.loadAll(db, KSS_APP, SAC_APP).map { it.toDTO() })
        }

    @PostMapping("/create")
    @ResponseBody
    fun create(
        @RequestParam(required = false) target: String?,
        @RequestParam scope: String,
        @RequestParam name: String,
        @RequestParam priceRub: Double,
        @RequestParam(required = false) periodDays: Int?,
    ): Long =
        withDb(target) { db ->
            PriceTariff.createNew(scope, name, priceRub, periodDays ?: 0, db, KSS_APP, SAC_APP)?.id ?: 0L
        }

    @PostMapping("/update")
    @ResponseBody
    fun update(
        @RequestParam id: Long,
        @RequestParam(required = false) target: String?,
        @RequestParam(required = false) name: String?,
        @RequestParam(required = false) priceRub: Double?,
        @RequestParam(required = false) periodDays: Int?,
        @RequestParam(required = false) isActive: Boolean?,
        @RequestParam(required = false) isDefault: Boolean?,
        @RequestParam(required = false) sortOrder: Int?,
    ): Long =
        withDb(target) { db ->
            PriceTariff.getById(id, db, KSS_APP, SAC_APP)?.let { tariff ->
                name?.let { tariff.name = it }
                priceRub?.let { tariff.priceRub = it }
                periodDays?.let { tariff.periodDays = it }
                isActive?.let { tariff.isActive = it }
                isDefault?.let { tariff.isDefault = it }
                sortOrder?.let { tariff.sortOrder = it }
                tariff.save()
                tariff.id
            } ?: 0L
        }

    @PostMapping("/delete")
    @ResponseBody
    fun delete(
        @RequestParam id: Long,
        @RequestParam(required = false) target: String?,
    ): Boolean = withDb(target) { db -> PriceTariff.delete(id, db) }
}
