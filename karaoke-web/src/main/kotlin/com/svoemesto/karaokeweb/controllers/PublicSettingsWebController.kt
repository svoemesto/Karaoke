package com.svoemesto.karaokeweb.controllers

import com.svoemesto.karaokeweb.WORKING_DATABASE
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody

/**
 * Контроллер для оперативного управления свойствами в `tbl_public_settings` (specs/125-news-flags-backfix).
 *
 * Зачем существует в `karaoke-web`, а не в `karaoke-app`: однотипный endpoint
 * `PublicSettingsController` уже есть в `karaoke-app` (`/api/publicsettings/update`), но
 * `karaoke-app` **на проде не разворачивается** — поэтому Spring сканирует только
 * `com.svoemesto.karaokeweb.*` и тот endpoint возвращает 404. Этот контроллер
 * предоставляет `POST /api/properties/setproperty` как тонкий дубль: тот же UPDATE в
 * `tbl_public_settings`, более короткий путь под curl-сценарий быстрого включения
 * kill-switch'а (`specs/125-news-flags-backfix/quickstart.md` Шаг 0 и Шаг 4).
 *
 * Чтение/запись — прямой JDBC к `WORKING_DATABASE` (на проде это прод-БД, на admin-машине —
 * локальная). Никаких внешних зависимостей от Spring-бинов karaoke-app.
 *
 * @see docs/features/news-publish-backfill.md
 */
@Controller
@RequestMapping("/api/properties")
class PublicSettingsWebController {
    /**
     * Установить/снять произвольный флаг в `tbl_public_settings`.
     *
     * Если ключа ещё нет — INSERT (для fresh БД после миграции `37_news_auto_publish_kill_switch.sql`
     * ключ уже есть). Если есть — UPDATE.
     *
     * @param key имя свойства (например, `newsAutoPublishKillSwitch`).
     * @param stringValue строковое значение (`"true"`/`"false"` для boolean-флагов).
     * @return `true` при успехе, `false` при ошибке JDBC.
     */
    @PostMapping("/setproperty")
    @ResponseBody
    fun setProperty(
        @RequestParam key: String,
        @RequestParam stringValue: String,
    ): Boolean {
        return try {
            val connection = WORKING_DATABASE.getConnection() ?: return false
            // Сначала пытаемся UPDATE — частый случай (ключ уже есть после миграции).
            val updateSql =
                "UPDATE tbl_public_settings SET value = ?, last_update = now() WHERE key = ?"
            val updated =
                connection.prepareStatement(updateSql).use { ps ->
                    ps.setString(1, stringValue)
                    ps.setString(2, key)
                    ps.executeUpdate()
                }
            if (updated > 0) {
                connection.close()
                return true
            }
            // Ключ не найден — INSERT (на случай, если миграция ещё не применена на этой БД —
            // идемпотентный upsert).
            val insertSql =
                """
                INSERT INTO tbl_public_settings (key, value, description)
                VALUES (?, ?, '')
                ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, last_update = now()
                """.trimIndent()
            val inserted =
                connection.prepareStatement(insertSql).use { ps ->
                    ps.setString(1, key)
                    ps.setString(2, stringValue)
                    ps.executeUpdate()
                }
            connection.close()
            inserted > 0
        } catch (e: Exception) {
            println("PublicSettingsWebController.setProperty error: ${e.message}")
            try {
                WORKING_DATABASE.getConnection()?.close()
            } catch (_: Exception) {
            }
            false
        }
    }
}
