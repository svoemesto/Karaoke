package com.svoemesto.karaokeapp.controllers

import com.svoemesto.karaokeapp.Connection
import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.model.PromoRule
import com.svoemesto.karaokeapp.services.KSS_APP
import com.svoemesto.karaokeapp.services.SAC_APP
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.server.ResponseStatusException
import java.sql.Timestamp

// Админка акций (tbl_promo_rules). Target-aware, как TariffsController/SiteUsersController.

/**
 * BR-120 (аудит #336, пакет `PKG-S3-23`, OpenProject #395): безопасный разбор границ
 * акции.
 *
 * Раньше `Timestamp.valueOf(...)` вызывался без `try/catch`: любая строка не в формате
 * `yyyy-MM-dd HH:mm:ss` роняла эндпоинт с 500 (страница ошибки Spring) и без внятного
 * описания поля. Теперь негодное значение → 400 с указанием имени поля.
 *
 * Пустая строка — не ошибка: она означает «границу не трогать» (см. комментарий к
 * параметрам `validFrom`/`validTo` в `update`).
 */
internal fun parsePromoBoundary(
    value: String?,
    field: String,
): Timestamp? {
    val raw = value?.trim().orEmpty()
    if (raw.isEmpty()) return null
    val normalized = raw.replace('T', ' ')
    val withSeconds = if (normalized.length == 16) "$normalized:00" else normalized
    return try {
        Timestamp.valueOf(withSeconds)
    } catch (_: Exception) {
        throw ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Некорректное значение параметра '$field': '$raw' (ожидается 'yyyy-MM-dd' или 'yyyy-MM-dd HH:mm:ss')",
        )
    }
}

/**
 * Контроллер (HTTP/WebSocket endpoints) для promo .
 *
 * @see AGENTS.md
 */
@Controller
@RequestMapping("/api/promorules")
class PromoController {
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

    @PostMapping("/list")
    @ResponseBody
    fun list(
        @RequestParam(required = false) target: String?,
    ): Map<String, Any> =
        withDb(target) { db ->
            mapOf("promoRules" to PromoRule.loadAll(db, KSS_APP, SAC_APP).map { it.toDTO() })
        }

    @PostMapping("/create")
    @ResponseBody
    fun create(
        @RequestParam(required = false) target: String?,
        @RequestParam name: String,
        @RequestParam type: String,
        @RequestParam(required = false) paramsJson: String?,
        @RequestParam(required = false) appliesTo: String?,
    ): Long =
        withDb(target) { db ->
            PromoRule.createNew(name, type, paramsJson ?: "{}", appliesTo ?: PromoRule.APPLIES_BOTH, db, KSS_APP, SAC_APP)?.id ?: 0L
        }

    @PostMapping("/update")
    @ResponseBody
    fun update(
        @RequestParam id: Long,
        @RequestParam(required = false) target: String?,
        @RequestParam(required = false) name: String?,
        @RequestParam(required = false) type: String?,
        @RequestParam(required = false) paramsJson: String?,
        @RequestParam(required = false) appliesTo: String?,
        @RequestParam(required = false) isActive: Boolean?,
        @RequestParam(required = false) priority: Int?,
        // ISO-строки (yyyy-MM-dd'T'HH:mm:ss). Пустая строка ЗДЕСЬ намеренно игнорируется (не
        // очищает границу) — KaraokeDbTable.save() некорректно обрабатывает diff nullable-поля
        // Timestamp? -> null (else-ветка `ps.setString(index, null.toString())` роняет весь UPDATE,
        // см. KaraokeDbTable.kt:74-83). Снять границу можно только прямым SQL (не через этот эндпоинт).
        @RequestParam(required = false) validFrom: String?,
        @RequestParam(required = false) validTo: String?,
    ): Long =
        withDb(target) { db ->
            PromoRule.getById(id, db, KSS_APP, SAC_APP)?.let { rule ->
                name?.let { rule.name = it }
                type?.let { rule.type = it }
                paramsJson?.let { rule.paramsJson = it }
                appliesTo?.let { rule.appliesTo = it }
                isActive?.let { rule.isActive = it }
                priority?.let { rule.priority = it }
                // BR-120: безопасный разбор — негодная дата → 400 с указанием поля
                // (раньше `Timestamp.valueOf` бросал IllegalArgumentException → 500).
                parsePromoBoundary(validFrom, "validFrom")?.let { rule.validFrom = it }
                parsePromoBoundary(validTo, "validTo")?.let { rule.validTo = it }
                rule.save()
                rule.id
            } ?: 0L
        }

    @PostMapping("/delete")
    @ResponseBody
    fun delete(
        @RequestParam id: Long,
        @RequestParam(required = false) target: String?,
    ): Boolean = withDb(target) { db -> PromoRule.delete(id, db) }
}
