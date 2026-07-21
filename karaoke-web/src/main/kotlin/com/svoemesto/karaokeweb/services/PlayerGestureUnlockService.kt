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

/**
 * Сервис для player gesture unlock .
 *
 * @see docs/features/async-process-queue.md
 */
@Service
class PlayerGestureUnlockService {
    private data class ClickBucket(
        val timestamps: MutableList<Long> = mutableListOf(),
    )

    // assignmentId (опционально) — токен выдан онлайн-редактором для конкретного задания: наряду с
    // доступом к стемам он же авторизует playerdata подставить НЕОДОБРЕННЫЙ черновик этого задания
    // вместо опубликованных маркеров (см. PublicPlayerController.playerData + assignmentIdForToken).
    // demoStart/demoEndSeconds (опционально, оба сразу или оба null) — токен демо-режима
    // (не-премиум/не-подписан/не-в-эфире): ограничивает и байты стема (см.
    // PublicPlayerController.stemResponse + Mp3Trimmer.trimToRange), и маркеры playerdata диапазоном
    // демо-фрагмента "куплет минус отступ под фейд-ин" (см. Settings.demoFragmentStartSeconds/
    // demoFragmentEndSeconds). null = обычный токен, доступ без ограничения.
    private data class TokenInfo(
        val songId: Long,
        val expiresAt: Long,
        val assignmentId: Long? = null,
        val demoStartSeconds: Double? = null,
        val demoEndSeconds: Double? = null,
    )

    // Диапазон демо-фрагмента (в системе координат исходного файла) для конкретного токена.
    data class DemoRange(
        val startSeconds: Double,
        val endSeconds: Double,
    )

    private val clickBuckets = ConcurrentHashMap<String, ClickBucket>()
    private val tokens = ConcurrentHashMap<String, TokenInfo>()
    private val random = SecureRandom()

    /**
     * Регистрирует один клик по полю метаданных песни. Возвращает токен доступа к плееру,
     * если этим кликом только что был завершён секретный паттерн, иначе null.
     */
    fun registerClick(
        clientId: String,
        songId: Long,
        field: String,
        shiftKey: Boolean,
    ): String? {
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

    fun validateToken(
        token: String,
        songId: Long,
    ): Boolean {
        pruneExpiredTokens()
        val info = tokens[token] ?: return false
        return info.songId == songId && info.expiresAt > System.currentTimeMillis()
    }

    /**
     * Выдаёт токен доступа к плееру напрямую, в обход секретного жеста — используется, когда
     * страница песни сама решает (по статусу "в эфире"/премиум), что показ плеера разрешён.
     * Токен неотличим от токена, выданного через жест: тот же TTL, та же структура.
     */
    fun issueDirectAccessToken(songId: Long): String = issueToken(songId)

    // Тот же токен, что и обычный прямой доступ, но помеченный конкретным заданием редактора —
    // владение заданием уже проверено вызывающей стороной (PublicSongEditorController) до выдачи.
    fun issueDirectAccessTokenForAssignment(
        songId: Long,
        assignmentId: Long,
    ): String = issueToken(songId, assignmentId)

    // Демо-режим (PublicPlayerController.access, ветка !canWatch): токен, ограниченный диапазоном —
    // stemResponse обрежет байты стема через Mp3Trimmer.trimToRange, playerdata обрежет и перебазирует
    // маркеры — оба по этому же диапазону, взятому из Settings.demoFragmentStartSeconds/
    // demoFragmentEndSeconds на момент выдачи.
    fun issueDemoAccessToken(
        songId: Long,
        startSeconds: Double,
        endSeconds: Double,
    ): String = issueToken(songId, demoStartSeconds = startSeconds, demoEndSeconds = endSeconds)

    // Для playerdata: если токен был выдан онлайн-редактором для задания, возвращает его id
    // (иначе null — обычный токен плеера, без переопределения маркеров). null и при невалидном токене.
    fun assignmentIdForToken(
        token: String?,
        songId: Long,
    ): Long? {
        pruneExpiredTokens()
        val info = token?.let { tokens[it] } ?: return null
        if (info.songId != songId || info.expiresAt <= System.currentTimeMillis()) return null
        return info.assignmentId
    }

    // Диапазон демо-фрагмента для данного токена, если он был выдан как демо-токен; иначе null
    // (обычный токен — полный доступ). null и при невалидном/просроченном/чужом токене.
    fun demoRangeForToken(
        token: String?,
        songId: Long,
    ): DemoRange? {
        pruneExpiredTokens()
        val info = token?.let { tokens[it] } ?: return null
        if (info.songId != songId || info.expiresAt <= System.currentTimeMillis()) return null
        val start = info.demoStartSeconds ?: return null
        val end = info.demoEndSeconds ?: return null
        return DemoRange(start, end)
    }

    private fun issueToken(
        songId: Long,
        assignmentId: Long? = null,
        demoStartSeconds: Double? = null,
        demoEndSeconds: Double? = null,
    ): String {
        pruneExpiredTokens()
        val bytes = ByteArray(24).also { random.nextBytes(it) }
        val token = bytes.joinToString("") { "%02x".format(it) }
        tokens[token] = TokenInfo(songId, System.currentTimeMillis() + TOKEN_TTL_MS, assignmentId, demoStartSeconds, demoEndSeconds)
        return token
    }

    private fun pruneExpiredTokens() {
        val now = System.currentTimeMillis()
        tokens.entries.removeIf { it.value.expiresAt < now }
        // Click buckets are self-cleaning per-call (removeIf above); an occasional sweep here
        // keeps memory bounded even for clients that start but never finish a gesture.
        clickBuckets.entries.removeIf { (_, bucket) ->
            synchronized(bucket) {
                bucket.timestamps.removeIf { now - it > CLICK_WINDOW_MS }
                bucket.timestamps.isEmpty()
            }
        }
    }

    private companion object {
        const val TARGET_FIELD = "key"
        const val REQUIRED_CLICKS = 3
        const val CLICK_WINDOW_MS = 1500L
        const val TOKEN_TTL_MS = 30 * 60 * 1000L
    }
}
