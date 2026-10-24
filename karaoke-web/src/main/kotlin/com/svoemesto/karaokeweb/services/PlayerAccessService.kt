package com.svoemesto.karaokeweb.services

import com.svoemesto.karaokeapp.model.Song
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Правило доступа к байтам и разметке онлайн-плеера — ЕДИНОЕ место, где оно вычисляется.
 *
 * Зачем отдельный сервис (спека 557, OpenProject #358, аудит BR-018): раньше основанием для
 * отдачи полных mp3-стемов и полной разметки был ФАКТ ВЛАДЕНИЯ токеном
 * (`gestureUnlockService.validateToken(token, id)`), а токен одинаково выдавался и легитимному
 * пути, и «скрытому жесту» — анонимному посетителю через публичный `POST /api/public/events`.
 * Итог — обход оплаты на публичном периметре.
 *
 * Теперь право вычисляется В МОМЕНТ ЗАПРОСА (FR-002, FR-004) по основаниям, перечисленным в
 * `docs/features/song-air-access.md`:
 *  1. песня публично доступна (всегда бесплатно / эфир в окне бесплатного доступа);
 *  2. у посетителя действует премиум;
 *  3. на эту песню оформлена бессрочная подписка;
 *  4. у посетителя есть действующая гостевая сессия по публичной share-ссылке;
 *  5. посетитель — редактор, владеющий заданием на эту песню (предпросмотр неодобренной
 *     разметки, FR-009; основание — владение заданием, а не подписка).
 *
 * Если ни одного основания нет — полный доступ НЕ предоставляется: посетитель получает
 * демо-фрагмент (обрезанный на сервере) либо отказ, если не предъявил ни токена, ни сессии.
 * Полный файл и полная разметка не покидают сервер ни при каких условиях (FR-005).
 *
 * Токен при этом — не право, а указатель на то, что проверять: из него берётся id пользователя
 * (чьи премиум/подписка перечитываются заново) и id задания редактора (владение перечитывается
 * заново). Поэтому решение одинаково для всех точек отдачи: стемы, playerdata, выгрузка пакета.
 */
@Service
class PlayerAccessService(
    private val tokenService: PlayerGestureUnlockService,
    private val shareLinkService: SongShareLinkService,
    private val rightsProvider: PlayerRightsProvider,
) {
    companion object {
        /** Категория журнала отказов (см. knowledge/domains/monitoring/components/log-categories.md). */
        const val LOG_CATEGORY = "infra.player.access"

        /** Причины решения — значения поля `reason` в журнале. */
        const val REASON_SONG_PUBLIC = "song_public"
        const val REASON_PREMIUM = "premium"
        const val REASON_SUBSCRIPTION = "subscription"
        const val REASON_SHARE_GUEST = "share_guest"
        const val REASON_ASSIGNMENT = "assignment"
        const val REASON_ASSIGNMENT_REVOKED = "assignment_revoked"
        const val REASON_DEMO_TOKEN = "demo_token"
        const val REASON_NO_RIGHTS = "no_rights"
        const val REASON_NO_CREDENTIALS = "no_credentials"
    }

    private val log = LoggerFactory.getLogger(LOG_CATEGORY)

    /**
     * Решение о доступе к конкретной песне.
     *
     * @param full true — полные байты и полная разметка.
     * @param demoRange ограничение диапазона демо-фрагмента (null — без ограничения).
     * @param shareGuest доступ получен по гостевой share-сессии (собственных прав нет).
     * @param reason причина решения (для журнала).
     * @param assignmentId id задания редактора, если токен выдан онлайн-редактором.
     */
    data class AccessDecision(
        val full: Boolean,
        val demoRange: PlayerGestureUnlockService.DemoRange?,
        val shareGuest: Boolean,
        val reason: String,
        val assignmentId: Long? = null,
    ) {
        /** Есть ли доступ хоть в каком-то виде (полный или демо-фрагмент). */
        val allowed: Boolean get() = full || demoRange != null
    }

    /**
     * Вычисляет право доступа к песне В МОМЕНТ ЗАПРОСА.
     *
     * @param song песня, к которой идёт запрос.
     * @param token токен доступа к плееру (query-параметр `token`).
     * @param session хеш гостевой share-сессии (query-параметр `session`).
     */
    fun evaluate(
        song: Song,
        token: String?,
        session: String?,
    ): AccessDecision {
        val facts = tokenService.describeToken(token, song.id)
        val userId = facts?.userId
        val shareSessionValid =
            session != null && shareLinkService.validateShareSession(session, song.id) != null

        // Собственное право посетителя: публичность песни / живой премиум / живая подписка.
        val freelyAvailable = song.isFreelyAvailableNow
        val premium = userId != null && rightsProvider.isPremium(userId)
        val subscribed = userId != null && !premium && rightsProvider.isSubscribedToSong(userId, song.id)
        val ownRights = freelyAvailable || premium || subscribed

        if (ownRights) {
            val reason =
                when {
                    freelyAvailable -> REASON_SONG_PUBLIC
                    premium -> REASON_PREMIUM
                    else -> REASON_SUBSCRIPTION
                }
            return AccessDecision(full = true, demoRange = null, shareGuest = false, reason = reason)
        }

        // Гость по публичной share-ссылке: смотрит плеер, но не экспортирует (FR-008).
        if (shareSessionValid) {
            return AccessDecision(full = true, demoRange = null, shareGuest = true, reason = REASON_SHARE_GUEST)
        }

        // Предпросмотр неодобренной разметки по СВОЕМУ заданию редактора (FR-009). Основание —
        // владение заданием, проверяется заново: задание могли отозвать или передать.
        val assignmentId = facts?.assignmentId
        if (assignmentId != null) {
            val owned = userId != null && rightsProvider.isAssignmentOwner(assignmentId, userId, song.id)
            if (!owned) {
                log.warn("player:access:denied songId={} reason={}", song.id, REASON_ASSIGNMENT_REVOKED)
                return AccessDecision(
                    full = false,
                    demoRange = null,
                    shareGuest = false,
                    reason = REASON_ASSIGNMENT_REVOKED,
                    assignmentId = assignmentId,
                )
            }
            return AccessDecision(
                full = true,
                demoRange = null,
                shareGuest = false,
                reason = REASON_ASSIGNMENT,
                assignmentId = assignmentId,
            )
        }

        // Демо-токен — штатный демо-режим: фрагмент по диапазону, зафиксированному при выдаче.
        val demoRange = facts?.demoRange
        if (demoRange != null) {
            log.info("player:access:demo songId={} reason={}", song.id, REASON_DEMO_TOKEN)
            return AccessDecision(
                full = false,
                demoRange = demoRange,
                shareGuest = false,
                reason = REASON_DEMO_TOKEN,
            )
        }

        // Токен есть, но действующего права нет (премиум истёк, подписка отозвана, песня ушла из
        // эфира) — полный доступ больше не предоставляется, но посетитель не получает ошибку:
        // его переводит в демо-режим фрагментом этой песни (Edge Case спеки 557).
        if (facts != null) {
            log.warn("player:access:denied songId={} reason={}", song.id, REASON_NO_RIGHTS)
            return AccessDecision(
                full = false,
                demoRange =
                    PlayerGestureUnlockService.DemoRange(
                        song.demoFragmentStartSeconds,
                        song.demoFragmentEndSeconds,
                    ),
                shareGuest = false,
                reason = REASON_NO_RIGHTS,
            )
        }

        // Ни токена, ни сессии — доступа нет вовсе.
        log.warn("player:access:denied songId={} reason={}", song.id, REASON_NO_CREDENTIALS)
        return AccessDecision(full = false, demoRange = null, shareGuest = false, reason = REASON_NO_CREDENTIALS)
    }

    /**
     * Действующий ли премиум у посетителя, определённого по токену.
     *
     * Нужно для выгрузки пакета файлов: запрос выгрузки приходит БЕЗ заголовка Authorization
     * (плеер грузит URL через fetch без заголовков), поэтому премиум определяется по id
     * пользователя из токена и перечитывается из БД здесь же (FR-008, research.md D7).
     */
    fun isPremiumByToken(
        song: Song,
        token: String?,
    ): Boolean {
        val userId = tokenService.describeToken(token, song.id)?.userId ?: return false
        return rightsProvider.isPremium(userId)
    }
}
