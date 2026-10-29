package com.svoemesto.karaokeweb.services

import com.svoemesto.karaokeweb.WORKING_DATABASE

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

// Ключи Yandex SmartCaptcha:
//
// - СЕРВЕРНЫЙ (секретный) ключ — живёт в env (свойство `captcha.server-key`, env
//   `CAPTCHA_SERVER_KEY`), читается ПРИ СТАРТЕ сервиса, кеша нет. До спеки 564 (OpenProject
//   #362, аудит BR-518) он лежал в tbl_public_settings — в той же таблице, которую мог
//   изменить анонимный клиент через POST /api/properties/setproperty. Отсюда fail-open:
//   при пустом ключе validate() возвращал «проверка пройдена», и цепочка «аноним пишет →
//   обнуляет ключ → регистрация без капчи» была замкнута. Теперь таблица секрет не хранит,
//   а незаконная конфигурация означает отказ регистрации (fail-closed).
//
// - КЛИЕНТСКИЙ (публичный) ключ — остаётся в tbl_public_settings: он публичен по устройству
//   сайта (виджет на публичной форме регистрации) и меняется владельцем без рестарта
//   сервиса. Читается прямой JDBC-запросом к той же Postgres, к которой karaoke-web и так
//   подключён через WORKING_DATABASE (на сервере это серверная БД, локально — локальная).

/**
 * Сервис конфигурации проверки капчи (Yandex SmartCaptcha).
 *
 * **Серверный ключ** — из env, без кеша (FR-011, FR-013 спеки 564): после перезапуска
 * сервиса новое значение действует сразу, «окно устаревания» отсутствует.
 *
 * **Клиентский ключ** — из `tbl_public_settings`, окно кеша 60 секунд (было 5 минут вместе с
 * серверным ключом — теперь серверный ключ в env, и общее 5-минутное окно исчезло).
 *
 * @see archive/docs/features/async-process-queue.md
 * @see knowledge/product/features/public-settings-perimeter.md
 */
@Service
class CaptchaConfigService(
    /**
     * Серверный (секретный) ключ проверки капчи — env `CAPTCHA_SERVER_KEY`.
     *
     * Пусто = «проверка не настроена»: регистрация отклоняется (fail-closed), а не
     * пропускается.
     */
    @Value("\${captcha.server-key:}") private val serverKey: String,
) {
    /**
     * Категория журнала конфигурации проверки капчи (спека 564, research D9).
     */
    private val log = LoggerFactory.getLogger("infra.captcha.config")

    /** Кеш клиентского ключа: значение + время прочтения. */
    private data class CachedClientKey(
        val value: String,
        val fetchedAt: Long,
    )

    /** Окно кеша клиентского ключа — 60 секунд (как TTL кеша публичных свойств). */
    private val clientKeyCacheTtlMs = 60 * 1000L

    @Volatile
    private var clientKeyCache: CachedClientKey? = null

    init {
        // Диагностика FR-014: администратор видит в журнале, настроена ли проверка, но само
        // значение ключа НЕ логируется (Constitution VIII.5, ADR local-0006).
        log.info(
            "captcha:config:server-key={}",
            if (isServerKeyConfigured()) "configured" else "not_configured",
        )
    }

    /**
     * Значение серверного ключа проверки капчи (секрет).
     *
     * Пустая строка = проверка не настроена. Значение не логируется и не попадает в ответы.
     */
    fun getServerKey(): String = serverKey

    /**
     * Настроена ли проверка капчи (диагностика FR-014).
     *
     * Возвращает только факт наличия ключа — без самого значения.
     */
    fun isServerKeyConfigured(): Boolean = serverKey.isNotBlank()

    /**
     * Значение клиентского (публичного) ключа проверки капчи.
     *
     * Читается из `tbl_public_settings` (`yandexSmartCaptchaClientKey`) с окном кеша 60
     * секунд. Клиентский ключ публичен по устройству сайта, поэтому его публичная выдача
     * (`GET /api/public/auth/config`) не нарушает FR-012.
     */
    fun getClientKey(): String {
        val current = clientKeyCache
        val now = System.currentTimeMillis()
        if (current != null && now - current.fetchedAt < clientKeyCacheTtlMs) return current.value
        val fresh = fetchValue(CLIENT_KEY)
        clientKeyCache = CachedClientKey(fresh, now)
        return fresh
    }

    /**
     * Чтение значения ключа из `tbl_public_settings` прямым JDBC.
     *
     * При ошибке БД возвращается пустая строка (fail-open для клиентского ключа: он не
     * влияет на безопасность, только на показ виджета).
     */
    private fun fetchValue(key: String): String {
        return try {
            val connection = WORKING_DATABASE.getConnection() ?: return ""
            connection
                .prepareStatement("SELECT value FROM tbl_public_settings WHERE key = ?")
                .use { ps ->
                    ps.setString(1, key)
                    ps.executeQuery().use { rs ->
                        if (rs.next()) rs.getString("value") ?: "" else ""
                    }
                }
        } catch (e: Exception) {
            log.warn("captcha:config:client-key-read-failed error={}", e.message)
            ""
        }
    }

    companion object {
        /** Ключ клиентского (публичного) ключа в `tbl_public_settings`. */
        const val CLIENT_KEY = "yandexSmartCaptchaClientKey"
    }
}
