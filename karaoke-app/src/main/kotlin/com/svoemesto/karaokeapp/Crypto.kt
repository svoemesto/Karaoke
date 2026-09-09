package com.svoemesto.karaokeapp

import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES/CBC/PKCS5PADDING crypto для двух-БД sync (см. Utils.kt:888, 902).
 *
 * **SECURITY** (Pass 343+ fix, issue из spec 426):
 * Ключ и IV **MUST** браться из env-переменных (`CRYPTO_AES_KEY`,
 * `CRYPTO_AES_IV`). Hardcoded fallback оставлен **только** для
 * обратной совместимости (если ключи не заданы, логируется WARN
 * при первом использовании, но шифрование продолжает работать —
 * чтобы существующие зашифрованные SQL не сломались).
 *
 * **TODO (Pass 343+)**: добавить миграцию: при включении env-ключа
 * расшифровать старые записи (через fallback) и зашифровать
 * заново. После полной миграции — удалить fallback.
 *
 * @see archive/docs/features/dual-db-sync.md
 */
class Crypto {
    companion object {
        private val log = LoggerFactory.getLogger(Crypto::class.java)

        // ENV-имена (Pass 343+)
        private const val ENV_KEY = "CRYPTO_AES_KEY"
        private const val ENV_IV = "CRYPTO_AES_IV"

        // Legacy hardcoded значения (Pass 343+ — DEPRECATED, оставлены
        // для обратной совместимости; будут удалены после миграции).
        private const val LEGACY_KEY = "aesEncryptionKey"
        private const val LEGACY_INIT_VECTOR = "nbwZ08J5101kUxCQ"

        const val WORDS_TO_CHECK = "Строка для проверки крипты"

        // Кешируем факт использования legacy + однократный WARN-лог
        // (чтобы не спамить при каждом вызове).
        @Volatile private var legacyWarned = false

        private fun key(): String {
            val envKey = System.getenv(ENV_KEY)
            if (!envKey.isNullOrBlank()) {
                return envKey
            }
            if (!legacyWarned) {
                log.warn(
                    "CRYPTO_AES_KEY env not set — falling back to LEGACY hardcoded key. " +
                        "Migrate to env ASAP (see Crypto.kt KDoc)."
                )
                legacyWarned = true
            }
            return LEGACY_KEY
        }

        private fun initVector(): String {
            val envIv = System.getenv(ENV_IV)
            if (!envIv.isNullOrBlank()) {
                return envIv
            }
            if (!legacyWarned) {
                log.warn(
                    "CRYPTO_AES_IV env not set — falling back to LEGACY hardcoded IV. " +
                        "Migrate to env ASAP (see Crypto.kt KDoc)."
                )
                legacyWarned = true
            }
            return LEGACY_INIT_VECTOR
        }

        fun decrypt(encrypted: String?): String? {
            try {
                val iv = IvParameterSpec(initVector().toByteArray(StandardCharsets.UTF_8))
                val sKeySpec = SecretKeySpec(key().toByteArray(StandardCharsets.UTF_8), "AES")
                val cipher: Cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING")
                cipher.init(Cipher.DECRYPT_MODE, sKeySpec, iv)
                val original: ByteArray = cipher.doFinal(Base64.getDecoder().decode(encrypted))
                return original.toString(StandardCharsets.UTF_8)
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
            return null
        }

        fun encrypt(value: String): String? {
            try {
                val iv = IvParameterSpec(initVector().toByteArray(StandardCharsets.UTF_8))
                val sKeySpec = SecretKeySpec(key().toByteArray(StandardCharsets.UTF_8), "AES")
                val cipher: Cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING")
                cipher.init(Cipher.ENCRYPT_MODE, sKeySpec, iv)
                val encrypted: ByteArray = cipher.doFinal(value.toByteArray())
                return Base64.getEncoder().encodeToString(encrypted)
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
            return null
        }
    }
}
