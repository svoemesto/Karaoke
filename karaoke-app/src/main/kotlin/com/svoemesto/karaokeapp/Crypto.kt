package com.svoemesto.karaokeapp

import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Ошибка конфигурации крипты (BR-291, аудит #336, пакет `PKG-S3-26`).
 *
 * Бросается, когда ключ/IV из окружения имеют недопустимую длину. Раньше такая
 * ошибка молча превращалась в `null` (шифрование «не сработало»), а вызывающий
 * отправлял в удалённую БД пустую строку вместо шифротекста.
 */
class CryptoConfigurationException(
    message: String,
) : IllegalStateException(message)

/**
 * AES/CBC/PKCS5PADDING crypto для двух-БД sync (см. Utils.kt:888, 902).
 *
 * **SECURITY** (Pass 343+ fix, issue из spec 426; ротация завершена 2026-10-26):
 * Ключ и IV **MUST** браться из env-переменных (`CRYPTO_AES_KEY`,
 * `CRYPTO_AES_IV`). Hardcoded fallback **удалён**: если переменные не
 * заданы — [CryptoConfigurationException], сервис не стартует.
 *
 * Секрет в коде больше не живёт: в репозитории ключа нет, он лежит только
 * в `deploy/do.env` на каждом хосте (вне git) и прокидывается в контейнер
 * через `CRYPTO_AES_KEY` / `CRYPTO_AES_IV` в compose-файлах.
 *
 * Ключ и IV должны быть **ОДИНАКОВЫМИ** у `karaoke-app` (шифрует
 * `sqlToInsert`/`sqlToDelete`) и `karaoke-web` (расшифровывает) — это разные
 * хосты, поэтому значения синхронизируются вручную.
 *
 * Порядок ротации и процедура: `docs/ops/crypto-key-rotation.md`.
 *
 * BR-291 (аудит #336, пакет `PKG-S3-26`):
 *  - длина ключа (16/24/32 байта) и IV (16 байт) проверяется явно; неверная
 *    конфигурация даёт [CryptoConfigurationException], а не тихий `null`;
 *  - ошибки шифрования/расшифрования логируются через SLF4J (тип операции +
 *    throwable), а не через `printStackTrace()` в stdout; значения и ключи в лог
 *    НЕ попадают.
 *
 * @see archive/docs/features/dual-db-sync.md
 */
class Crypto {
    companion object {
        private val log = LoggerFactory.getLogger(Crypto::class.java)

        // ENV-имена (Pass 343+)
        private const val ENV_KEY = "CRYPTO_AES_KEY"
        private const val ENV_IV = "CRYPTO_AES_IV"

        const val WORDS_TO_CHECK = "Строка для проверки крипты"

        /** BR-291: допустимые длины ключа AES (в байтах): 128/192/256 бит. */
        val VALID_KEY_LENGTHS: Set<Int> = setOf(16, 24, 32)

        /** BR-291: допустимая длина IV для AES-CBC (в байтах). */
        const val VALID_IV_LENGTH: Int = 16

        // Кешируем факт использования legacy + однократный WARN-лог
        // (чтобы не спамить при каждом вызове).

        /**
         * BR-291: явная проверка длины ключа и IV.
         *
         * @throws CryptoConfigurationException если длина ключа не 16/24/32 байта
         *   или длина IV не 16 байт.
         */
        fun validateKeyMaterial(
            key: String,
            iv: String,
        ) {
            val keyLength = key.toByteArray(StandardCharsets.UTF_8).size
            if (keyLength !in VALID_KEY_LENGTHS) {
                throw CryptoConfigurationException(
                    "$ENV_KEY must be 16, 24 or 32 bytes long (got $keyLength)",
                )
            }
            val ivLength = iv.toByteArray(StandardCharsets.UTF_8).size
            if (ivLength != VALID_IV_LENGTH) {
                throw CryptoConfigurationException(
                    "$ENV_IV must be $VALID_IV_LENGTH bytes long (got $ivLength)",
                )
            }
        }

        /**
         * BR-291: проверка **текущей** (env) конфигурации.
         *
         * Вызывается на старте приложения: конфигурация проверяется один раз, а не
         * «внутри try» на каждой операции. Сама операция при неверной конфигурации
         * бросает [CryptoConfigurationException] — «тихого» `null` больше нет.
         */
        fun validateCurrentKeyMaterial() {
            validateKeyMaterial(key(), initVector())
        }

        private fun key(): String = resolveOrThrow(ENV_KEY, System.getenv(ENV_KEY))

        private fun initVector(): String = resolveOrThrow(ENV_IV, System.getenv(ENV_IV))

        /**
         * Достаёт обязательную переменную окружения или падает.
         *
         * Fail-fast вместо тихого fallback'а на hardcoded-ключ: сервис без ключа
         * не должен работать «наполовину» — иначе шифрование уедет от расшифровки
         * (разные хосты) и потеряется молча.
         *
         * @param name имя переменной окружения
         * @param value значение из окружения (может быть `null` или пустым)
         * @throws CryptoConfigurationException если значение не задано
         */
        fun resolveOrThrow(
            name: String,
            value: String?,
        ): String {
            if (value.isNullOrBlank()) {
                throw CryptoConfigurationException(
                    "$name is not set — ключ шифрования обязателен. Задайте его в deploy/do.env " +
                        "на ВСЕХ хостах, где работают karaoke-app/karaoke-web (значения должны быть " +
                        "ОДИНАКОВЫМИ: app шифрует, web расшифровывает). " +
                        "Процедура: docs/ops/crypto-key-rotation.md.",
                )
            }
            return value
        }

        fun decrypt(encrypted: String?): String? {
            // BR-291: проверка конфигурации — ВНЕ try: неверная длина ключа/IV это
            // ошибка конфигурации, а не «не удалось расшифровать», и она обязана быть
            // видна вызывающему, а не тонуть в null.
            validateKeyMaterial(key(), initVector())
            return try {
                val iv = IvParameterSpec(initVector().toByteArray(StandardCharsets.UTF_8))
                val sKeySpec = SecretKeySpec(key().toByteArray(StandardCharsets.UTF_8), "AES")
                val cipher: Cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING")
                cipher.init(Cipher.DECRYPT_MODE, sKeySpec, iv)
                val original: ByteArray = cipher.doFinal(Base64.getDecoder().decode(encrypted))
                original.toString(StandardCharsets.UTF_8)
            } catch (ex: Exception) {
                // BR-291: SLF4J вместо printStackTrace в stdout; в лог — только тип
                // операции и класс/сообщение исключения (без значений и ключей).
                log.error("crypto: decrypt failed op=decrypt error={}", "${ex::class.simpleName}: ${ex.message}", ex)
                null
            }
        }

        fun encrypt(value: String): String? {
            // BR-291: см. decrypt — конфигурация проверяется вне try.
            validateKeyMaterial(key(), initVector())
            return try {
                val iv = IvParameterSpec(initVector().toByteArray(StandardCharsets.UTF_8))
                val sKeySpec = SecretKeySpec(key().toByteArray(StandardCharsets.UTF_8), "AES")
                val cipher: Cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING")
                cipher.init(Cipher.ENCRYPT_MODE, sKeySpec, iv)
                val encrypted: ByteArray = cipher.doFinal(value.toByteArray())
                Base64.getEncoder().encodeToString(encrypted)
            } catch (ex: Exception) {
                // BR-291: SLF4J вместо printStackTrace в stdout; значения в лог не пишем.
                log.error("crypto: encrypt failed op=encrypt error={}", "${ex::class.simpleName}: ${ex.message}", ex)
                null
            }
        }
    }
}
