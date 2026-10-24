package com.svoemesto.karaokeapp.controllers

import com.svoemesto.karaokeapp.Connection
import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.model.PriceTariff
import com.svoemesto.karaokeapp.services.KSS_APP
import com.svoemesto.karaokeapp.services.SAC_APP
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.server.ResponseStatusException

// Админка тарифов подписки (tbl_price_tariffs). Target-aware, как SiteUsersController: реальные
// тарифы правятся на прод-БД (target=remote), т.к. платёжный конвейер karaoke-web работает на
// проде и читает тарифы оттуда; локальная БД используется для теста/разработки.

/**
 * Контроллер (HTTP/WebSocket endpoints) для tariffs .
 *
 * @see AGENTS.md
 */
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

    private fun rejectInvalidTariff(
        scope: String,
        priceRub: Double,
        periodDays: Int,
    ) {
        val errorCode = validateTariffValues(scope = scope, priceRub = priceRub, periodDays = periodDays)
        if (errorCode != null) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, errorCode)
        }
    }

    /**
     * Сохраняет тариф и, если он назначен default, снимает прежний default в ТОЙ ЖЕ транзакции
     * (BR-219). `save()` пишет через `database.getConnection()` — то есть то же ThreadLocal-соединение,
     * поэтому `autoCommit=false` охватывает обе операции.
     */
    private fun saveWithSingleDefault(
        db: KaraokeConnection,
        tariff: PriceTariff,
        makeDefault: Boolean,
    ) {
        val connection =
            db.getConnection()
                ?: throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "db_unavailable")
        val previousAutoCommit = connection.autoCommit
        try {
            connection.autoCommit = false
            if (makeDefault) {
                connection
                    .prepareStatement(
                        "UPDATE ${PriceTariff.TABLE_NAME} SET is_default = false WHERE scope = ? AND id <> ?",
                    ).use { ps ->
                        ps.setString(1, tariff.scope)
                        ps.setLong(2, tariff.id)
                        ps.executeUpdate()
                    }
                tariff.isDefault = true
            }
            tariff.save()
            connection.commit()
        } catch (e: Exception) {
            try {
                connection.rollback()
            } catch (_: Exception) {
            }
            throw e
        } finally {
            try {
                connection.autoCommit = previousAutoCommit
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
            val days = periodDays ?: 0
            rejectInvalidTariff(scope = scope, priceRub = priceRub, periodDays = days)
            PriceTariff.createNew(scope, name, priceRub, days, db, KSS_APP, SAC_APP)?.id ?: 0L
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
                sortOrder?.let { tariff.sortOrder = it }
                // BR-219: проверяем ИТОГОВЫЕ значения (а не только переданные), иначе прежний
                // periodDays=0 у SITE можно сохранить, не передавая параметр.
                rejectInvalidTariff(scope = tariff.scope, priceRub = tariff.priceRub, periodDays = tariff.periodDays)
                if (isDefault == true) {
                    saveWithSingleDefault(db = db, tariff = tariff, makeDefault = true)
                } else {
                    isDefault?.let { tariff.isDefault = it }
                    tariff.save()
                }
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
