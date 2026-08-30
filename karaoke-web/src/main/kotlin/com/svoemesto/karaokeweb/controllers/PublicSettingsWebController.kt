package com.svoemesto.karaokeweb.controllers

import com.svoemesto.karaokeapp.KaraokeProperties
import com.svoemesto.karaokeweb.WORKING_DATABASE
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

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
 * @see archive/docs/features/news-publish-backfill.md
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
     * При успешном UPDATE/INSERT вызывает [markDirty] — следующий вызов [getProperty]
     * подхватит изменение через [consumeDirty] и вернёт свежее значение без ожидания TTL
     * (FR-004, FR-009 спеки 249-public-settings-cache). При ошибке JDBC `markDirty` НЕ
     * вызывается — cache остаётся валидным (старое значение совпадает с реальным).
     *
     * @param key имя свойства (например, `newsAutoPublishKillSwitch`).
     * @param stringValue строковое значение (`"true"`/`"false"` для boolean-флагов).
     * @return `true` при успехе, `false` при ошибке JDBC.
     *
     * @see specs/249-public-settings-cache FR-009
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
                markDirty()
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
            if (inserted > 0) markDirty()
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

    /**
     * Прочитать значение одного свойства из `tbl_public_settings`.
     *
     * Возвращает `String` (raw value) — пустую строку, если ключ не найден или БД недоступна
     * (fail-open: kill-switch не активен по дефолту, ровно как обсуждали в specs/125). Для
     * boolean-флагов сравнение делается через `== "true"`. Чтобы посмотреть описание поля —
     * используйте [digest].
     *
     * Результат кешируется в `companion object` через [getCachedProperty] (TTL=60 сек +
     * dirty-инвалидация через [setProperty]) — см. `specs/249-public-settings-cache` FR-001.
     * На админ-машине под webvue3 это снижает RPS к `tbl_public_settings` (страница SongsTable
     * делает серию `getPropertyValuePromise` вызовов).
     *
     * @param key имя свойства.
     * @return строковое значение поля `value` (или пустая строка).
     *
     * @see specs/249-public-settings-cache
     */
    @GetMapping("/getproperty")
    @ResponseBody
    fun getProperty(
        @RequestParam key: String,
    ): String =
        getCachedProperty(key) {
            val connection = WORKING_DATABASE.getConnection() ?: return@getCachedProperty ""
            connection
                .prepareStatement("SELECT value FROM tbl_public_settings WHERE key = ?")
                .use { ps ->
                    ps.setString(1, key)
                    ps.executeQuery().use { rs ->
                        if (rs.next()) rs.getString("value") ?: "" else ""
                    }
                }
        }

    /**
     * Дайджест всех свойств в `tbl_public_settings` — key/value/description, для аудита и
     * отладки kill-switch сценариев. Удобно перед sync-окном проверить, какие настройки вообще
     * сейчас установлены на проде. `last_update` намеренно не возвращается (внутренняя
     * аудит-информация, для `specs/125` не нужна).
     */
    @GetMapping("/digest")
    @ResponseBody
    fun digest(): Map<String, Any> {
        val list = mutableListOf<Map<String, String>>()
        return try {
            val connection = WORKING_DATABASE.getConnection() ?: return mapOf("properties" to list)
            connection
                .prepareStatement(
                    "SELECT key, value, description FROM tbl_public_settings ORDER BY key",
                ).use { ps ->
                    ps.executeQuery().use { rs ->
                        while (rs.next()) {
                            list.add(
                                mapOf(
                                    "key" to (rs.getString("key") ?: ""),
                                    "value" to (rs.getString("value") ?: ""),
                                    "description" to (rs.getString("description") ?: ""),
                                ),
                            )
                        }
                    }
                }
            mapOf("properties" to list)
        } catch (e: Exception) {
            println("PublicSettingsWebController.digest error: ${e.message}")
            mapOf("properties" to list)
        }
    }

    companion object {
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
                    println("PublicSettingsWebController.getProperty error: ${e.message}")
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
