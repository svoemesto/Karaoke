package com.svoemesto.karaokeweb.controllers

import com.svoemesto.karaokeapp.KaraokeProperties
import com.svoemesto.karaokeweb.WORKING_DATABASE
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Контроллер для управления свойствами в `tbl_public_settings` (спека 564, OpenProject #359,
 * аудит BR-019).
 *
 * Зачем существует в `karaoke-web`, а не в `karaoke-app`: однотипный endpoint
 * `PublicSettingsController` уже есть в `karaoke-app` (`/api/publicsettings/update`), но
 * `karaoke-app` **на проде не разворачивается** — поэтому Spring сканирует только
 * `com.svoemesto.karaokeweb.*` и тот endpoint возвращает 404. Этот контроллер
 * предоставляет `POST /api/properties/setproperty` как тонкий дубль: тот же UPDATE в
 * `tbl_public_settings`, короткий путь для операционного сценария быстрого включения
 * kill-switch'а (`specs/125-news-flags-backfix/quickstart.md`).
 *
 * **Периметр доступа (спека 564, решения владельца от 2026-10-24, п. 1–2):**
 *
 * - **Запись закрыта полностью.** `POST /setproperty` требует общий секрет в заголовке
 *   `X-Internal-Secret` (сравнение — только при непустом настроенном секрете, fail-safe) и
 *   дополнительно ограничен сетевым фильтром nginx по адресу админской машины
 *   (`deploy/prod-single-host/80to8897`, `location /api/properties/setproperty`). При отказе —
 *   `403`, значение в базе **не меняется**, попытка пишется в журнал категории
 *   `infra.settings.security` (время, адрес источника, причина — без тела запроса и без
 *   значения настройки).
 * - **Чтение остаётся по белому списку публичных ключей.** Публичные ключи читаются
 *   анонимно; операционные (kill-switch, шаблоны новостей) — только по защищённому каналу;
 *   секретные (серверный ключ капчи) **не выдаются никогда** — ни анонимному клиенту, ни по
 *   защищённому каналу (серверный ключ капчи живёт в env, см. [CaptchaConfigService]).
 *
 * Административный сценарий `webvue3` («Настройки сайта») сюда НЕ ходит: его маршрут
 * `webvue3/nginx_webvue3.conf` ведёт в `karaoke-app` (`/api/publicsettings/update` и
 * `/api/publicsettings/digest`), другой сервис
 * и другой путь — правка его не ломает (FR-005).
 *
 * @see archive/docs/features/news-publish-backfill.md
 * @see docs/features/public-settings-perimeter.md
 */
@Controller
@RequestMapping("/api/properties")
class PublicSettingsWebController(
    /**
     * Общий server-to-server секрет для защищённого канала изменения настроек.
     *
     * Значение — env `SYNC_INTERNAL_SECRET` (свойство `publicsettings.internal-secret`); новый
     * секрет не заводится — решение владельца от 2026-10-24, п. 2. Пустое значение = секрет
     * не настроен → доступ закрыт **всегда** (fail-safe).
     */
    @Value("\${publicsettings.internal-secret:}") private val internalSecret: String,
) {
    /**
     * Установить/снять произвольный флаг в `tbl_public_settings` (защищённый канал).
     *
     * Если ключа ещё нет — INSERT (для fresh БД после миграции
     * `37_news_auto_publish_kill_switch.sql` ключ уже есть). Если есть — UPDATE.
     *
     * При успешном UPDATE/INSERT вызывает [markDirty] — следующий вызов [getProperty]
     * подхватит изменение через [consumeDirty] и вернёт свежее значение без ожидания TTL
     * (FR-004, FR-009 спеки 249-public-settings-cache). При ошибке JDBC `markDirty` НЕ
     * вызывается — cache остаётся валидным (старое значение совпадает с реальным).
     *
     * **Требуется общий секрет в заголовке** [SECRET_HEADER] (FR-001 спеки 564). При отказе —
     * `403` и тело `false` (прежний формат ответа), ни одной выполненной SQL-команды.
     * Запись ключа из [SECRET_KEYS] отклоняется всегда: серверный ключ капчи живёт в env,
     * а не в таблице (FR-011).
     *
     * @param key имя свойства (например, `newsAutoPublishKillSwitch`).
     * @param stringValue строковое значение (`"true"`/`"false"` для boolean-флагов).
     * @param request запрос — из него берётся заголовок с секретом и адрес источника.
     * @return `200` + `true` при успехе, `200` + `false` при ошибке JDBC, `403` + `false`
     *   при отказе по правам.
     *
     * @see specs/564-impl-properties-captcha/spec.md FR-001, FR-003, FR-004
     * @see specs/249-public-settings-cache FR-009
     */
    @PostMapping("/setproperty")
    @ResponseBody
    fun setProperty(
        @RequestParam key: String,
        @RequestParam stringValue: String,
        request: HttpServletRequest,
    ): ResponseEntity<Boolean> = handleSetProperty(key, stringValue, request) { WORKING_DATABASE.getConnection() }

    /**
     * Тело обработчика [setProperty] с тест-швом [connectionProvider].
     *
     * [connectionProvider] — seam для unit-тестов: подставляется поддельное JDBC-соединение,
     * чтобы проверить, что при отказе по правам НИ ОДНА SQL-команда не выполнена. В рантайме
     * это обычное соединение с рабочей БД.
     */
    internal fun handleSetProperty(
        key: String,
        stringValue: String,
        request: HttpServletRequest,
        connectionProvider: () -> Connection?,
    ): ResponseEntity<Boolean> {
        // 1. Общий секрет — ПЕРВЫЙ рубеж (второй — сетевой фильтр nginx по адресу
        //    админской машины). При отказе тело запроса не разбирается и ни одна команда
        //    не выполняется; наружу уходит короткий 403 без деталей (FR-003).
        val denial = denialReason(request)
        if (denial != null) {
            logDenial("settings:security:denied", denial, request)
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(false)
        }
        // 2. Секретный ключ в таблицу не пишем: его источник истины — env (FR-011).
        if (key in SECRET_KEYS) {
            logDenial("settings:security:denied", "secret_key_write", request)
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(false)
        }

        var connection: Connection? = null
        return try {
            connection = connectionProvider()
            if (connection == null) {
                log.warn("settings:write:no-connection — значение настройки не изменено")
                return ResponseEntity.ok(false)
            }
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
                markDirty()
                return ResponseEntity.ok(true)
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
            if (inserted > 0) markDirty()
            ResponseEntity.ok(inserted > 0)
        } catch (e: Exception) {
            // Текст ошибки БД — ТОЛЬКО в журнал: наружу уходит прежний `false` (FR-019).
            log.error("settings:write:error key={} error={}", key, e.message)
            ResponseEntity.ok(false)
        } finally {
            // Соединение закрываем сами — и то, которое реально открыли (раньше в catch
            // закрывалось НОВОЕ соединение из WORKING_DATABASE, которого никто не открывал).
            try {
                connection?.close()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Прочитать значение одного свойства из `tbl_public_settings`.
     *
     * Границы выдачи (FR-002 спеки 564):
     *
     * - ключ из [SECRET_KEYS] — **никогда** не выдаётся (даже по защищённому каналу): серверный
     *   ключ капчи живёт в env, значение в таблице больше не является источником истины;
     * - ключ из [PUBLIC_KEYS] — читается анонимно;
     * - остальные (операционные: kill-switch, шаблоны новостей) — читаются только по
     *   защищённому каналу, анонимно возвращается пустая строка.
     *
     * Возвращает `String` (raw value) — пустую строку, если ключ не найден, не публичен или БД
     * недоступна (fail-open: kill-switch не активен по дефолту, ровно как обсуждали в
     * specs/125). Для boolean-флагов сравнение делается через `== "true"`. Чтобы посмотреть
     * описание поля — используйте [digest].
     *
     * Результат кешируется в `companion object` через [getCachedProperty] (TTL=60 сек +
     * dirty-инвалидация через [setProperty]) — см. `specs/249-public-settings-cache` FR-001.
     *
     * @param key имя свойства.
     * @param request запрос — из него берётся заголовок с секретом и адрес источника.
     * @return строковое значение поля `value` (или пустая строка).
     *
     * @see specs/249-public-settings-cache
     */
    @GetMapping("/getproperty")
    @ResponseBody
    fun getProperty(
        @RequestParam key: String,
        request: HttpServletRequest,
    ): String = handleGetProperty(key, request) { WORKING_DATABASE.getConnection() }

    /**
     * Тело [getProperty] с тест-швом [connectionProvider].
     *
     * [connectionProvider] — seam для unit-тестов: подставляется поддельное JDBC-соединение,
     * чтобы проверить границы выдачи (секретный ключ не выдаётся никогда, непубличный —
     * только по защищённому каналу) без обращения к боевой БД.
     */
    internal fun handleGetProperty(
        key: String,
        request: HttpServletRequest,
        connectionProvider: () -> Connection?,
    ): String {
        if (key in SECRET_KEYS) {
            securityLog.warn(
                "settings:security:secret-read-blocked reason=secret_key key={} source={} time={}",
                key,
                sourceAddress(request),
                Timestamp.from(Instant.now()),
            )
            return ""
        }
        if (!isAuthorized(request) && key !in PUBLIC_KEYS) {
            securityLog.warn(
                "settings:security:read-denied reason=key_not_public key={} source={} time={}",
                key,
                sourceAddress(request),
                Timestamp.from(Instant.now()),
            )
            return ""
        }
        return getCachedProperty(key) { loadValue(key, connectionProvider) }
    }

    /**
     * Дайджест публичных настроек в `tbl_public_settings` — key/value/description, для аудита и
     * отладки kill-switch сценариев.
     *
     * Границы выдачи (FR-002 спеки 564): анонимно — только ключи из [PUBLIC_KEYS]; по
     * защищённому каналу — все ключи, **кроме** [SECRET_KEYS]. `last_update` намеренно не
     * возвращается (внутренняя аудит-информация, для `specs/125` не нужна).
     */
    @GetMapping("/digest")
    @ResponseBody
    fun digest(request: HttpServletRequest): Map<String, Any> = handleDigest(request) { WORKING_DATABASE.getConnection() }

    /**
     * Тело [digest] с тест-швом [connectionProvider] (см. [handleGetProperty]).
     */
    internal fun handleDigest(
        request: HttpServletRequest,
        connectionProvider: () -> Connection?,
    ): Map<String, Any> {
        val list = mutableListOf<Map<String, String>>()
        val authorized = isAuthorized(request)
        return try {
            val connection = connectionProvider() ?: return mapOf("properties" to list)
            connection
                .prepareStatement(
                    "SELECT key, value, description FROM tbl_public_settings ORDER BY key",
                ).use { ps ->
                    ps.executeQuery().use { rs ->
                        while (rs.next()) {
                            val key = rs.getString("key") ?: ""
                            if (key in SECRET_KEYS) continue
                            if (!authorized && key !in PUBLIC_KEYS) continue
                            list.add(
                                mapOf(
                                    "key" to key,
                                    "value" to (rs.getString("value") ?: ""),
                                    "description" to (rs.getString("description") ?: ""),
                                ),
                            )
                        }
                    }
                }
            mapOf("properties" to list)
        } catch (e: Exception) {
            log.error("settings:digest:error error={}", e.message)
            mapOf("properties" to list)
        }
    }

    /**
     * Чтение значения ключа из `tbl_public_settings` одним SELECT'ом (без кеша).
     *
     * Используется как `loadFn` для [getCachedProperty].
     */
    private fun loadValue(
        key: String,
        connectionProvider: () -> Connection?,
    ): String {
        val connection = connectionProvider() ?: return ""
        return connection
            .prepareStatement("SELECT value FROM tbl_public_settings WHERE key = ?")
            .use { ps ->
                ps.setString(1, key)
                ps.executeQuery().use { rs ->
                    if (rs.next()) rs.getString("value") ?: "" else ""
                }
            }
    }

    /**
     * Причина отказа по правам (fail-safe) — `null`, если доступ разрешён.
     *
     * **Важно (research D2):** сравнение выполняется ТОЛЬКО при непустом настроенном
     * секрете. Образец `InternalStemJobController.authorized()` сравнивает заголовок «как есть» —
     * при незаданном секрете (`""`) и запросе с ПРИСУТСТВУЮЩИМ, но пустым заголовком
     * сравнение прошло бы и доступ открылся бы. Здесь — как в #357 (спека 554): пустой секрет
     * означает «не настроено» → доступ закрыт.
     *
     * @return `null` — доступ разрешён; иначе причина (`missing_secret` / `invalid_secret` /
     *   `secret_not_configured`).
     */
    private fun denialReason(request: HttpServletRequest): String? {
        if (internalSecret.isBlank()) return "secret_not_configured"
        val provided = request.getHeader(SECRET_HEADER) ?: return "missing_secret"
        if (provided != internalSecret) return "invalid_secret"
        return null
    }

    /** Разрешён ли запрос по защищённому каналу (см. [denialReason]). */
    private fun isAuthorized(request: HttpServletRequest): Boolean = denialReason(request) == null

    /**
     * Запись об отказе в журнал безопасности — время, адрес источника, причина.
     *
     * Тело запроса и **значение настройки** в журнал НЕ пишутся (FR-004).
     */
    private fun logDenial(
        event: String,
        reason: String,
        request: HttpServletRequest,
    ) {
        securityLog.warn(
            "{} reason={} source={} time={}",
            event,
            reason,
            sourceAddress(request),
            Timestamp.from(Instant.now()),
        )
    }

    /**
     * Адрес источника запроса для журнала безопасности.
     *
     * Берём `X-Real-IP` — его выставляет НАШ nginx (`proxy_set_header X-Real-IP $remote_addr;`,
     * `deploy/prod-single-host/80to8897`) — и НЕ `X-Forwarded-For`: префикс этого заголовка
     * клиент подделывает (аудит BR-003 «xff-first-element»), поэтому адрес из XFF в журнале
     * безопасности не является доказательством. Правило не менялось при правке BR-003:
     * журнал безопасности берёт только тот заголовок, который клиент подделать не может.
     *
     * Фолбэк — `request.remoteHost`: адрес docker-шлюза, когда запрос пришёл без nginx.
     */
    private fun sourceAddress(request: HttpServletRequest): String {
        val realIp = request.getHeader("X-Real-IP")?.trim()
        if (!realIp.isNullOrEmpty()) return realIp
        return request.remoteHost
    }

    companion object {
        /** Логгер бизнес-логики контроллера (ошибки чтения/записи — с текстом ошибки БД). */
        private val log = LoggerFactory.getLogger(PublicSettingsWebController::class.java)

        /**
         * Категория журнала безопасности публичных настроек (спека 564, research D10).
         *
         * Отдельная от `infra.sync.security` — то другое событие (доступ к настройкам, а не к
         * синхронизации песен).
         */
        private val securityLog = LoggerFactory.getLogger("infra.settings.security")

        /**
         * Заголовок с общим server-to-server секретом — тот же контракт, что у
         * `POST /changerecords` (спека 554) и `internal/stemjobs`.
         */
        const val SECRET_HEADER = "X-Internal-Secret"

        /**
         * Ключи `tbl_public_settings`, значение которых **никогда** не выдаётся этим
         * контроллером (FR-002, FR-011).
         *
         * Серверный ключ капчи после спеки 564 живёт в env (`captcha.server-key`), а строка в
         * таблице — артефакт старой схемы, удаляемый миграцией
         * `51_public_settings_drop_captcha_server_key.sql`. Пока строка есть, API не выдаёт её
         * значение — ни анонимному клиенту, ни по защищённому каналу.
         */
        val SECRET_KEYS: Set<String> = setOf("yandexSmartCaptchaServerKey")

        /**
         * Ключи `tbl_public_settings`, читаемые анонимно (белый список публичных ключей).
         *
         * Сейчас это клиентский ключ капчи — он публичен по устройству сайта (виджет на форме
         * регистрации). Операционные ключи (kill-switch авто-публикации, шаблоны новостей)
         * анонимному клиенту не нужны и читаются только по защищённому каналу.
         */
        val PUBLIC_KEYS: Set<String> = setOf("yandexSmartCaptchaClientKey")

        /**
         * In-memory TTL-кеш для [getProperty] (FR-001, FR-006 parent спеки 241-db-storage-perf-audit).
         *
         * Endpoint `/api/properties/getproperty` используется в webvue3-админке через
         * `getPropertyValuePromise` (`webvue3/src/components/Properties/store.js`) — на странице
         * SongsTable идёт серия вызовов для лимитов, default-значений, kill-switch'ей и т.п.
         * Без кеша — каждый вызов = `SELECT value FROM tbl_public_settings WHERE key = ?`.
         * С этим кешем — 1 cold start + cache hits в течение TTL=60 сек (FR-005).
         *
         * Инвалидация — через [markDirty] (вызывается из [setProperty] при успехе). Следующий
         * вызов [getCachedProperty] подхватывает изменение через [consumeDirty] и сбрасывает
         * cache → `loadFn()` читает свежее значение (FR-004, FR-009).
         *
         * Семантика кеширования отсутствующего ключа: если `loadFn()` вернул пустую строку
         * (key не найден в БД) — кладём [NOT_FOUND_SENTINEL] (FR-007), чтобы не делать
         * повторный SELECT для несуществующих ключей. Если key появится в БД — [setProperty]
         * взведёт `markDirty()` и cache обновится.
         *
         * Thread-safe через [ConcurrentHashMap] — два одновременных запроса в момент cache miss
         * могут сделать двойной SELECT, это допустимо (UI не блокируется, последний writer
         * выигрывает). Для админского UI при 1–5 RPS это не критично.
         *
         * @see specs/249-public-settings-cache FR-001..FR-009
         * @see specs/241-db-storage-perf-audit FR-006
         * @see specs/248-authors-tiles-cache sister spec (проверенный паттерн TTL-кеша)
         */
        /** TTL кеша — 60 секунд (FR-005 spec.md). */
        private const val CACHE_TTL_MS = 60 * 1000L

        /** Ключ свойства в [KaraokeProperties] (FR-003). */
        private const val KARAOKE_PROPERTY_CACHE_ENABLED = "karaoke.public.public-settings-cache.enabled"

        /**
         * Маркер для «key не найден в БД» (FR-007). Отдельный `Any()`-объект, чтобы
         * отличать от валидного `value = ""` (сравнение через `===`, referential equality).
         */
        private val NOT_FOUND_SENTINEL = Any()

        /**
         * Запись кеша — пара `(value, expiresAtMs)`. Immutable, чтобы не было гонок при
         * чтении в одном потоке и записи в другом.
         *
         * `value` — либо [String] (валидное значение из БД, может быть `""`), либо
         * [NOT_FOUND_SENTINEL] (key отсутствует).
         */
        private data class CachedProperty(
            val value: Any,
            val expiresAtMs: Long,
        )

        /** Thread-safe хранилище кеша (FR-002). */
        private val cache = ConcurrentHashMap<String, CachedProperty>()

        /**
         * Флаг инвалидации кеша (FR-004). Взводится из [setProperty] при успешном
         * UPDATE/INSERT. Считывается и сбрасывается через [consumeDirty] в
         * [getCachedProperty].
         *
         * Архитектурное решение: НЕ переиспользуем `StatBySong.dirty` (это про free-флаги
         * песен на главной странице — другой домен). Разделение ответственности и
         * предсказуемая инвалидация (см. Clarifications Session 2026-08-26 в spec.md).
         */
        private val dirty = AtomicBoolean(false)

        /**
         * Взводит флаг инвалидации кеша [getProperty]. Вызывается из [setProperty] после
         * успешного UPDATE/INSERT, перед `return true` (FR-009). Если [setProperty] упал —
         * [markDirty] НЕ вызывается, cache остаётся валидным (старое значение совпадает с
         * реальным — ничего не менялось).
         */
        fun markDirty() {
            dirty.set(true)
        }

        /**
         * Атомарно читает и сбрасывает [dirty] (как `getAndSet(false)` в `AtomicBoolean`).
         * Возвращает `true`, если между предыдущим и текущим вызовом [markDirty] был взведён.
         *
         * Вызывается в начале [getCachedProperty] перед проверкой TTL — dirty-проверка имеет
         * приоритет над TTL.
         */
        fun consumeDirty(): Boolean = dirty.getAndSet(false)

        /**
         * Возвращает кешированное значение `tbl_public_settings.value` для ключа `key`
         * или выполняет `loadFn` и кладёт результат в кеш.
         *
         * Алгоритм (FR-001):
         * 1. Если кеш отключён через [KARAOKE_PROPERTY_CACHE_ENABLED] → `loadFn()`.
         * 2. Если [consumeDirty] вернул `true` → cache очищается (dirty-инвалидация имеет
         *    приоритет над TTL).
         * 3. Cache hit (ключ есть + `expiresAtMs > now`) → возврат из кеша.
         * 4. Cache miss → `loadFn()`. Если результат пустой — кладём [NOT_FOUND_SENTINEL]
         *    (FR-007). Иначе — кладём значение.
         * 5. Если `loadFn()` бросил — cache не меняется, возвращается `""` (fail-open, FR-008).
         *
         * @param key имя свойства (например, `"newsAutoPublishKillSwitch"`).
         * @param loadFn функция загрузки (выполняет 1 SQL: `SELECT value FROM tbl_public_settings`).
         * @return строковое значение (или `""` если key отсутствует / БД недоступна).
         *
         * @see specs/249-public-settings-cache FR-001..FR-009
         */
        private fun getCachedProperty(
            key: String,
            loadFn: () -> String,
        ): String {
            if (!isCacheEnabled()) {
                return loadFn()
            }
            try {
                if (consumeDirty()) {
                    cache.clear()
                }
            } catch (_: Throwable) {
                // ignore — consumeDirty shouldn't throw, but defensive
            }

            val now = System.currentTimeMillis()
            val cached = cache[key]
            if (cached != null && cached.expiresAtMs > now) {
                return if (cached.value === NOT_FOUND_SENTINEL) "" else cached.value as String
            }

            val fresh =
                try {
                    loadFn()
                } catch (e: Exception) {
                    // FR-008: fail-open — возвращаем пустую строку, cache НЕ обновляется,
                    // следующий вызов повторит попытку.
                    log.error("settings:read:error key={} error={}", key, e.message)
                    return ""
                }

            val valueForCache: Any = if (fresh.isEmpty()) NOT_FOUND_SENTINEL else fresh
            cache[key] = CachedProperty(valueForCache, now + CACHE_TTL_MS)
            return fresh
        }

        /**
         * Проверяет, разрешён ли cache свойством `karaoke.public.public-settings-cache.enabled`
         * в [KaraokeProperties] (дефолт `true`, см. `KaraokeProperties.kt` в `karaoke-app`).
         *
         * Если `KaraokeProperties` по какой-то причине недоступен (ранняя инициализация,
         * проблемы с файлом) — функция возвращает `true` через `try/catch`. Безопасный
         * дефолт = кеш работает (минимизируем SQL round-trip'ы в типовом сценарии).
         *
         * @return `true` если кеш разрешён; `false` если явно отключён в свойствах.
         *
         * @see specs/249-public-settings-cache FR-003
         * @see KaraokeProperties.getBoolean
         */
        private fun isCacheEnabled(): Boolean =
            try {
                KaraokeProperties.getBoolean(KARAOKE_PROPERTY_CACHE_ENABLED)
            } catch (_: Throwable) {
                true
            }
    }
}
