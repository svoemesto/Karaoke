package com.svoemesto.karaokeweb.services

import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory (не персистентный) сервис выдачи и проверки токенов доступа к онлайн-плееру
 * публичного сайта.
 *
 * ИСТОРИЧЕСКОЕ ИМЯ: класс называется `PlayerGestureUnlockService` по историческому признаку —
 * раньше он распознавал «скрытый жест» (три клика с зажатой клавишей по полю метаданных песни)
 * и выдавал по нему токен доступа. Спека 557 (OpenProject #358, аудит BR-018) закрыла этот путь:
 * жест больше НЕ является основанием доступа, публичный эндпоинт событий не возвращает
 * значение доступа (см. PublicApiController.events), а право на полный доступ проверяется
 * в момент отдачи байт (см. PlayerAccessService). Переименование класса — отдельная правка
 * (research.md D8).
 *
 * ТОКЕН НЕ ЯВЛЯЕТСЯ ПРАВОМ на платный контент: это короткоживущее (не более 30 минут)
 * подтверждение, что доступ к конкретной песне однажды был разрешён. Право проверяется заново
 * при каждом запросе байтов/разметки (FR-002, FR-004) — см. PlayerAccessService.
 *
 * Состояние не пишется в БД намеренно: это чисто сессионный артефакт, не участвующий в
 * синхронизации LOCAL/SERVER и не оседающий в бэкапах/дампах.
 *
 * @see PlayerAccessService
 * @see com.svoemesto.karaokeweb.controllers.PublicPlayerController
 */

/**
 * Сервис выдачи токенов доступа к плееру.
 *
 * @see archive/docs/features/async-process-queue.md
 */
@Service
class PlayerGestureUnlockService {
    /**
     * Вид выданного токена — определяет, что именно он разрешает.
     */
    enum class TokenKind {
        /** Обычный токен доступа к плееру (без ограничения диапазона). */
        FULL,

        /** Демо-токен: байты и маркеры ограничены диапазоном демо-фрагмента. */
        DEMO,

        /** Токен онлайн-редактора, привязанный к конкретному заданию. */
        ASSIGNMENT,
    }

    /**
     * Разобранный токен: вид + основание (id пользователя / id задания) + диапазон демо-фрагмента.
     */
    data class TokenFacts(
        val kind: TokenKind,
        val userId: Long?,
        val assignmentId: Long?,
        val demoRange: DemoRange?,
    )

    // userId (опционально) — id пользователя, чьё право было проверено в момент выдачи токена.
    // Нужен, чтобы перепроверить право в момент отдачи байт: аудио-эндпоинты плеера НЕ получают
    // заголовок Authorization (плеер грузит стемы через fetch(url) без заголовков, см.
    // karaoke-public/src/player/KaraokePlayer.js), поэтому сервер берёт пользователя из токена
    // и перечитывает его премиум/подписку из БД заново.
    //
    // assignmentId (опционально) — токен выдан онлайн-редактором для конкретного задания: наряду с
    // доступом к стемам он же авторизует playerdata подставить НЕОДОБРЕННЫЙ черновик этого задания
    // вместо опубликованных маркеров (см. PublicPlayerController.playerData + assignmentId).
    //
    // demoStart/demoEndSeconds (опционально, оба сразу или оба null) — токен демо-режима
    // (не-премиум/не-подписан/не-в-эфире): ограничивает и байты стема (см.
    // PublicPlayerController.stemResponse + Mp3Trimmer.trimToRange), и маркеры playerdata диапазоном
    // демо-фрагмента "куплет минус отступ под фейд-ин" (см. Song.demoFragmentStartSeconds/
    // demoFragmentEndSeconds). null = обычный токен, доступ без ограничения.
    private data class TokenInfo(
        val songId: Long,
        val expiresAt: Long,
        val userId: Long? = null,
        val assignmentId: Long? = null,
        val demoStartSeconds: Double? = null,
        val demoEndSeconds: Double? = null,
    )

    // Диапазон демо-фрагмента (в системе координат исходного файла) для конкретного токена.
    data class DemoRange(
        val startSeconds: Double,
        val endSeconds: Double,
    )

    private val tokens = ConcurrentHashMap<String, TokenInfo>()
    private val random = SecureRandom()

    /**
     * Разбирает токен применительно к конкретной песне.
     *
     * null — токен отсутствует, пуст, просрочен или выдан для другой песни (FR-006: токен одной
     * песни не открывает доступ к другой).
     */
    fun describeToken(
        token: String?,
        songId: Long,
    ): TokenFacts? {
        pruneExpiredTokens()
        val info = token?.takeIf { it.isNotBlank() }?.let { tokens[it] } ?: return null
        if (info.songId != songId || info.expiresAt <= System.currentTimeMillis()) return null
        val kind =
            when {
                info.assignmentId != null -> TokenKind.ASSIGNMENT
                info.demoStartSeconds != null -> TokenKind.DEMO
                else -> TokenKind.FULL
            }
        val demoRange =
            if (info.demoStartSeconds != null && info.demoEndSeconds != null) {
                DemoRange(info.demoStartSeconds, info.demoEndSeconds)
            } else {
                null
            }
        return TokenFacts(kind, info.userId, info.assignmentId, demoRange)
    }

    /**
     * Выдаёт токен доступа к плееру напрямую — используется, когда страница песни сама решает
     * (по статусу "в эфире"/премиум/подписке), что показ плеера разрешён.
     * [userId] — id пользователя, чьё право проверено: он нужен для живой перепроверки права
     * в момент отдачи байт (см. PlayerAccessService).
     */
    fun issueDirectAccessToken(
        songId: Long,
        userId: Long? = null,
    ): String = issueToken(songId, userId = userId)

    // Тот же токен, что и обычный прямой доступ, но помеченный конкретным заданием редактора —
    // владение заданием уже проверено вызывающей стороной (PublicSongEditorController) до выдачи
    // и проверяется повторно в момент отдачи байт (PlayerAccessService).
    fun issueDirectAccessTokenForAssignment(
        songId: Long,
        assignmentId: Long,
        userId: Long? = null,
    ): String = issueToken(songId, userId = userId, assignmentId = assignmentId)

    // Демо-режим (PublicPlayerController.access, ветка !canWatch): токен, ограниченный диапазоном —
    // stemResponse обрежет байты стема через Mp3Trimmer.trimToRange, playerdata обрежет и перебазирует
    // маркеры — оба по этому же диапазону, взятому из Song.demoFragmentStartSeconds/
    // demoFragmentEndSeconds на момент выдачи.
    fun issueDemoAccessToken(
        songId: Long,
        startSeconds: Double,
        endSeconds: Double,
        userId: Long? = null,
    ): String = issueToken(songId, userId = userId, demoStartSeconds = startSeconds, demoEndSeconds = endSeconds)

    private fun issueToken(
        songId: Long,
        userId: Long? = null,
        assignmentId: Long? = null,
        demoStartSeconds: Double? = null,
        demoEndSeconds: Double? = null,
    ): String {
        pruneExpiredTokens()
        val bytes = ByteArray(24).also { random.nextBytes(it) }
        val token = bytes.joinToString("") { "%02x".format(it) }
        tokens[token] = TokenInfo(songId, System.currentTimeMillis() + TOKEN_TTL_MS, userId, assignmentId, demoStartSeconds, demoEndSeconds)
        return token
    }

    private fun pruneExpiredTokens() {
        val now = System.currentTimeMillis()
        tokens.entries.removeIf { it.value.expiresAt < now }
    }

    private companion object {
        // Срок жизни токена — 30 минут (FR-013; решение владельца от 2026-10-24: не увеличивать).
        // При живой проверке права (PlayerAccessService) срок жизни — не единственный барьер.
        const val TOKEN_TTL_MS = 30 * 60 * 1000L
    }
}
