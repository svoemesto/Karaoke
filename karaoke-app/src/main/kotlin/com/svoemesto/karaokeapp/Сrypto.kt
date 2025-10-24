package com.svoemesto.karaokeapp

import java.nio.charset.StandardCharsets
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class Crypto {
    companion object {
        private const val KEY = "aesEncryptionKey"
        private const val INIT_VECTOR = "nbwZ08J5101kUxCQ"
        const val WORDS_TO_CHECK = "Строка для проверки крипты"
        fun decrypt(encrypted: String?): String? {
            try {
                val iv = IvParameterSpec(INIT_VECTOR.toByteArray(StandardCharsets.UTF_8))
                val sKeySpec = SecretKeySpec(KEY.toByteArray(StandardCharsets.UTF_8), "AES")
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
                val iv = IvParameterSpec(INIT_VECTOR.toByteArray(StandardCharsets.UTF_8))
                val sKeySpec = SecretKeySpec(KEY.toByteArray(StandardCharsets.UTF_8), "AES")
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