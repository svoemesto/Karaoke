package com.svoemesto.karaokeapp.controllers

import com.svoemesto.karaokeapp.Connection
import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.WORKING_DATABASE
import com.svoemesto.karaokeapp.model.Song
import com.svoemesto.karaokeapp.services.NewsTemplateService
import com.svoemesto.karaokeapp.services.StorageApiClient
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody

/**
 * REST-контроллер шаблонов автоматических новостей сайта
 * (`tbl_news.title`/`body`, FR-001..FR-016, specs/128-news-publish-templates).
 *
 * Зеркалирует паттерн `/api/vk/templates/...` и `/api/telegram/templates/...`
 * (`ApiController`), но с тремя отличиями (см. [archive/docs/features/news-templates.md]
 * и `research.md` R1-R3):
 * - Хранилище — `tbl_public_settings` (Postgres), не `KaraokeProperties` — потому что
 *   `NewsTemplateService.template` вызывается на проде в `karaoke-web`, где файла
 *   `KaraokeProperties` нет (FR-016).
 * - Запись — `INSERT ... ON CONFLICT (key) DO UPDATE` (UPSERT) — без seed-миграции
 *   (R2, research.md).
 * - Параметр `target=local|remote` — выбор БД для записи/чтения (по образцу
 *   [PublicSettingsController.resolveDb], R1).
 *
 * Все 4 endpoints возвращают JSON с `success: true|false` + бизнес-полями.
 * Ошибки валидации — `200 {success: false, error: "..."}` (UI шлёт
 * `promisedXMLHttpRequest`, не различает 200/400).
 *
 * **Где живёт**: `karaoke-app` (не `karaoke-web`), потому что admin-UI
 * `webvue3` работает против admin-машины, где запущен `karaoke-app`.
 * Prod-рендеринг использует прямой JDBC к `tbl_public_settings` из
 * `NewsTemplateService.template` — НЕ через HTTP-endpoint.
 *
 * @see archive/docs/features/news-templates.md
 */
@Controller
@RequestMapping("/api/news/templates")
class NewsTemplateController(
    private val storageService: KaraokeStorageService,
    private val storageApiClient: StorageApiClient,
) {
    /** Выбор БД по `target` — `remote` → prod-БД, иначе local (FR-016, R1). */
    private fun resolveDb(target: String?): KaraokeConnection = if (target == "remote") Connection.remote() else Connection.local()

    /**
     * `resolveDb()` создаёт НОВЫЙ объект Connection.local()/remote() на каждый вызов,
     * открывающий собственное физическое JDBC-соединение и кэширующий его в себе;
     * без явного `close()` оно висит до обрыва и постепенно исчерпывает пул Postgres
     * ("too many clients already"). `withDb` закрывает соединение сразу после
     * использования. По образцу `PublicSettingsController.withDb`.
     */
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

    /**
     * GET `/api/news/templates` — список всех 4 ключей с их текущими значениями из БД
     * (или `""` если ключ отсутствует), дефолт из кода, категория/поле для UI
     * группировки, и список плейсхолдеров (FR-009).
     *
     * @param target `local` (default) | `remote` — выбор БД.
     */
    @GetMapping("")
    @ResponseBody
    fun list(@RequestParam(required = false) target: String?): Map<String, Any> {
        val templates =
            NewsTemplateService.ALLOWED_KEYS.map { key ->
                val value = withDb(target) { db -> NewsTemplateService.template(key, db) }
                mapOf(
                    "key" to key,
                    "category" to NewsTemplateService.categoryFor(key),
                    "field" to NewsTemplateService.fieldFor(key),
                    "value" to value,
                    "default" to NewsTemplateService.defaultFor(key),
                    "description" to NewsTemplateService.descriptionFor(key),
                )
            }
        return mapOf(
            "templates" to templates,
            "placeholders" to NewsTemplateService.placeholders(),
        )
    }

    /**
     * POST `/api/news/templates` — UPSERT значение одного ключа в `tbl_public_settings`
     * (FR-008 — без перезапуска, R2 — UPSERT-стратегия).
     *
     * @param key Один из 4 разрешённых ключей (`NewsTemplateService.ALLOWED_KEYS`) — иначе
     *   `{success: false, error: "unknown key: ... (allowed: ...)"}`.
     * @param value Любая строка (включая пустую); при пустом `value` рендер использует
     *   дефолт из кода через `.ifBlank` (FR-010).
     * @param target `local` (default) | `remote` — выбор БД.
     */
    @PostMapping("")
    @ResponseBody
    fun save(
        @RequestParam key: String,
        @RequestParam value: String,
        @RequestParam(required = false) target: String?,
    ): Map<String, Any> {
        if (key !in NewsTemplateService.ALLOWED_KEYS) {
            return mapOf(
                "success" to false as Any,
                "error" to "unknown key: $key (allowed: ${NewsTemplateService.ALLOWED_KEYS.sorted().joinToString(", ")})" as Any,
            )
        }
        return withDb(target) { db ->
            val connection =
                db.getConnection()
                    ?: return@withDb mapOf(
                        "success" to false as Any,
                        "error" to "Не удалось получить JDBC-соединение (target=${target ?: "local"})" as Any,
                    )
            val sql =
                "INSERT INTO tbl_public_settings (key, value, description, last_update) " +
                    "VALUES (?, ?, ?, now()) " +
                    "ON CONFLICT (key) DO UPDATE " +
                    "SET value = EXCLUDED.value, description = EXCLUDED.description, last_update = now()"
            connection.prepareStatement(sql).use { ps ->
                ps.setString(1, key)
                ps.setString(2, value)
                ps.setString(3, NewsTemplateService.descriptionFor(key))
                ps.executeUpdate()
            }
            mapOf("success" to true as Any, "key" to key as Any)
        }
    }

    /**
     * POST `/api/news/templates/preview` — рендер НЕзасейвленных шаблонов `title`+`body`
     * на тестовой песне (FR-009, SC-003 SLA ≤3 сек).
     *
     * Возвращает пару `title`/`body` отдельно (не одно поле как у VK/Telegram — две
     * разные колонки `tbl_news`). `title` усекается до [NewsTemplateService.NEWS_TITLE_MAX_LENGTH]
     * с `…` (FR-010a), `body` — без усечения (TEXT). Возвращает флаги
     * `titleTruncated`/`bodyTruncated` + длины для UI-индикации.
     *
     * @param titleTemplate Шаблон заголовка (можно с плейсхолдерами).
     * @param bodyTemplate Шаблон тела (можно с плейсхолдерами).
     * @param id ID песни для рендера.
     * @param target `local` (default) | `remote` — выбор БД для загрузки `Song`.
     */
    @PostMapping("/preview")
    @ResponseBody
    fun preview(
        @RequestParam titleTemplate: String,
        @RequestParam bodyTemplate: String,
        @RequestParam id: Long,
        @RequestParam(required = false) target: String?,
    ): Map<String, Any> {
        // Для preview всегда читаем Song из WORKING_DATABASE (admin-local) — превью это
        // операция администратора на admin-машине, target применим только для будущей
        // пропагации шаблона, не для загрузки тестовой песни. Если нужно подтянуть песню
        // с прода — можно сменить на withDb(target).
        val song =
            Song.loadFromDbById(
                id = id,
                database = WORKING_DATABASE,
                storageService = storageService,
                storageApiClient = storageApiClient,
            ) ?: return mapOf(
                "success" to false as Any,
                "error" to "Песня не найдена: id=$id" as Any,
            )
        // Для title — с усечением (FR-010a); для body — без усечения (TEXT без лимита).
        val title = NewsTemplateService.render(titleTemplate, song, news = null, truncate = true)
        val body = NewsTemplateService.render(bodyTemplate, song, news = null, truncate = false)
        val titleMaxLength = NewsTemplateService.NEWS_TITLE_MAX_LENGTH
        return mapOf(
            "success" to true as Any,
            "title" to title as Any,
            "body" to body as Any,
            "titleLength" to title.length as Any,
            "titleTruncated" to (title.length >= titleMaxLength && title.endsWith("…")) as Any,
            "titleMaxLength" to titleMaxLength as Any,
            "bodyLength" to body.length as Any,
            "bodyTruncated" to false as Any,
        )
    }

    /**
     * GET `/api/news/templates/defaults` — 4 заводских значения из кода
     * (FR-013, для кнопки «Сбросить к дефолту»).
     *
     * Значения возвращаются как Map из [NewsTemplateService.ALLOWED_KEYS] в
     * [NewsTemplateService.DEFAULT_AIR_TITLE]/[DEFAULT_AIR_BODY]/
     * [DEFAULT_PREMIUM_TITLE]/[DEFAULT_PREMIUM_BODY] — НЕ из БД.
     */
    @GetMapping("/defaults")
    @ResponseBody
    fun defaults(): Map<String, Any> =
        mapOf(
            "defaults" to
                mapOf(
                    "newsTemplateAirTitle" to NewsTemplateService.DEFAULT_AIR_TITLE,
                    "newsTemplateAirBody" to NewsTemplateService.DEFAULT_AIR_BODY,
                    "newsTemplatePremiumTitle" to NewsTemplateService.DEFAULT_PREMIUM_TITLE,
                    "newsTemplatePremiumBody" to NewsTemplateService.DEFAULT_PREMIUM_BODY,
                ),
        )
}
