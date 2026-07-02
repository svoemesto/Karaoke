package com.svoemesto.karaokeweb.services

import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory (не персистентный) сервис распознавания скрытого жеста разблокировки онлайн-плеера
 * на публичном сайте. Никакие детали жеста (какое поле, сколько кликов, тайминг) не покидают
 * этот класс — фронтенд лишь пересылает сюда обычные на вид клик-события, а этот сервис сам
 * решает, совпал ли паттерн, и выдаёт короткоживущий токен доступа к плееру.
 *
 * Состояние не пишется в БД намеренно: это чисто сессионный артефакт, не участвующий в
 * синхронизации LOCAL/SERVER и не оседающий в бэкапах/дампах.
 */
@Service
class PlayerGestureUnlockService {

    private data class ClickBucket(val timestamps: MutableList<Long> = mutableListOf())
    private data class TokenInfo(val songId: Long, val expiresAt: Long)

    private val clickBuckets = ConcurrentHashMap<String, ClickBucket>()
    private val tokens = ConcurrentHashMap<String, TokenInfo>()
    private val random = SecureRandom()

    /**
     * Регистрирует один клик по полю метаданных песни. Возвращает токен доступа к плееру,
     * если этим кликом только что был завершён секретный паттерн, иначе null.
     */
    fun registerClick(clientId: String, songId: Long, field: String, shiftKey: Boolean): String? {
        val bucketKey = "$clientId:$songId:$field"

        if (!shiftKey || field != TARGET_FIELD) {
            clickBuckets.remove(bucketKey)
            return null
        }

        val now = System.currentTimeMillis()
        val bucket = clickBuckets.computeIfAbsent(bucketKey) { ClickBucket() }
        synchronized(bucket) {
            bucket.timestamps.removeIf { now - it > CLICK_WINDOW_MS }
            bucket.timestamps.add(now)
            if (bucket.timestamps.size >= REQUIRED_CLICKS) {
                bucket.timestamps.clear()
                return issueToken(songId)
            }
        }
        return null
    }

    fun validateToken(token: String, songId: Long): Boolean {
        pruneExpiredTokens()
        val info = tokens[token] ?: return false
        return info.songId == songId && info.expiresAt > System.currentTimeMillis()
    }

    private fun issueToken(songId: Long): String {
        pruneExpiredTokens()
        val bytes = ByteArray(24).also { random.nextBytes(it) }
        val token = bytes.joinToString("") { "%02x".format(it) }
        tokens[token] = TokenInfo(songId, System.currentTimeMillis() + TOKEN_TTL_MS)
        return token
    }

    private fun pruneExpiredTokens() {
        val now = System.currentTimeMillis()
        tokens.entries.removeIf { it.value.expiresAt < now }
        // Click buckets are self-cleaning per-call (removeIf above); an occasional sweep here
        // keeps memory bounded even for clients that start but never finish a gesture.
        clickBuckets.entries.removeIf { (_, bucket) -> synchronized(bucket) { bucket.timestamps.removeIf { now - it > CLICK_WINDOW_MS }; bucket.timestamps.isEmpty() } }
    }

    private companion object {
        const val TARGET_FIELD = "key"
        const val REQUIRED_CLICKS = 3
        const val CLICK_WINDOW_MS = 1500L
        const val TOKEN_TTL_MS = 30 * 60 * 1000L
    }
}
