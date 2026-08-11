package com.svoemesto.karaokeweb.services

import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeweb.WORKING_DATABASE
import com.svoemesto.karaokeweb.util.ShareErrorCode
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.security.SecureRandom
import java.sql.Types
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

// Note: импорты `java.time.ZoneOffset` и `java.time.format.DateTimeFormatter` удалены
// вместе со старым серверным форматтером — теперь фронт форматирует epoch ms на
// клиенте через dateFormat.formatDate (FR-011). См. spec.md §«Трактовка дат».

// Источник правды — naive timestamp в МСК (`tbl_song_share_links.expires_at` и т.п.,
// `timestamp without time zone`). Чтение и запись идут через явный Europe/Moscow,
// а не через ZoneId.systemDefault() — это алгоритмический, не конфигурационный выбор
// (FR-002, FR-014): тесты на машинах в любом TZ должны давать одинаковую строку.
private val MOSCOW_ZONE: ZoneId = ZoneId.of("Europe/Moscow")

// `internal` (а не `private`) — чтобы тест `SongShareLinkDateTimeTest` мог напрямую
// проверить инвариант «epoch ms → момент в МСК» (FR-014). `private` на уровне файла
// в Kotlin ограничивает видимость одним файлом, а тест в другом файле; `internal`
// ограничивает модулем karaoke-web — это нормально и для тестов, и для прод-кода.
internal fun toMskLocalDateTime(epochMs: Long): LocalDateTime =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), MOSCOW_ZONE)

/**
 * Персистентный (PostgreSQL) сервис «Временный полный доступ к песне»
 * (add-song-share-link). Двухуровневая модель:
 *
 *  - Долгоживущий **грант** в [tbl_song_share_links] — создаётся премиум-владельцем песни,
 *    хранит SHA-256 от секрета, активен до [expiresAt] или явного отзыва.
 *  - Короткоживущая **playback-сессия** в [tbl_song_share_sessions] — выдаётся анонимному
 *    гостю через атомарный claim (`tryClaim`), продлевается heartbeat'ом
 *    ([heartbeatTtlSeconds]), завершается вручную ([release]) или фоновым sweeper'ом.
 *
 * Состояние НЕ in-memory (в отличие от [PlayerGestureUnlockService]) — ссылки
 * переживают рестарт karaoke-web, что критично для пересылаемой URL.
 *
 * **Безопасность:** в БД хранится только SHA-256(секрет). Исходный секрет (32 байта
 * SecureRandom, base64url) отдаётся ровно один раз при создании/перевыпуске. После
 * этого восстановить его нельзя — владелец должен явно перевыпустить.
 *
 * **Одновременность:** ≤ [WebShareProperties.maxConcurrentSessions] playback-сессий
 * на ссылку. Устройство идентифицируется SHA-256 от `browserId` в localStorage
 * (см. design D4). Тот же browserHash, открытый в новой вкладке, считается одним
 * устройством и НЕ инкрементирует счётчик.
 *
 * **Авто-отзыв:** фоновый [ShareLinkSweeper] раз в [WebShareProperties.sweepIntervalSeconds]
 * секунд завершает просроченные lease, отзывает ссылки при потере владельцем
 * `isEffectivePremium` и при появлении у песни тега SKIP / будущем `dateTimePublish`.
 *
 * **Даты (FR-011, FR-013).** Все даты в DTO/JSON — реальный момент в epoch ms
 * через `EXTRACT(EPOCH FROM ts AT TIME ZONE 'Europe/Moscow')*1000`. DDL не
 * меняется: `tbl_song_share_links.*_at` и `tbl_song_share_sessions.*_at`
 * остаются `timestamp without time zone` (naive, источник правды — МСК).
 * Раньше рядом дублировались `*Ms`/`*Label` поля — это и был источник бага
 * «−3 часа» (Pass 47). Теперь единственное числовое поле = реальный момент,
 * одинаково пригодное и для отображения в TZ устройства (`formatDate`),
 * и для сравнения с `Date.now()` (`isExpired`).
 *
 * @see docs/features/guest-share-link.md
 */
@Service
class SongShareLinkService(
    private val props: com.svoemesto.karaokeweb.config.WebShareProperties,
) {
    private val log = LoggerFactory.getLogger(SongShareLinkService::class.java)

    /**
     * Результат [createLink] — содержит исходный секрет для URL.
     * Секрет доступен только сразу после создания; хэш — в БД.
     *
     * `expiresAt` = реальный момент в epoch ms (System.currentTimeMillis() + ttlSeconds*1000),
     * а не «naive as UTC» — это инвариант всей фичи (FR-011, FR-013). Раньше рядом
     * дублировались `expiresAtMs` и `expiresAtLabel`, что и было источником бага
     * «−3 часа» (см. research.md §1).
     */
    data class CreateResult(
        val linkId: Long,
        val secret: String,
        val expiresAt: Long,
        val url: String,
    )

    /**
     * Метаданные активной ссылки для UI владельца (без секрета).
     *
     * Все даты — реальный момент в epoch ms (через `EXTRACT(EPOCH FROM ts AT TIME ZONE 'Europe/Moscow')`),
     * одинаково пригодный и для отображения на UI владельца (после `formatDate(...)`),
     * и для сравнения с `Date.now()` (проверка «истёк ли срок»). Раньше рядом
     * дублировались `*Ms`/`*Label` поля — это и был источник бага «−3 часа»
     * (см. research.md §1).
     */
    data class OwnerLinkView(
        val linkId: Long,
        val songId: Long,
        val active: Boolean,
        val expiresAt: Long,
        val createdAt: Long,
        val revokedAt: Long?,
        val revokeReason: String,
        val firstUsedAt: Long?,
        val lastUsedAt: Long?,
        val sessionsTotal: Int,
        val rejectedConcurrent: Int,
    )

    /**
     * Результат [tryClaim] — содержит id записи в `tbl_song_share_links` и `songId`
     * песни. Раньше возвращался `Pair<linkId, sessionTokenHash>`, фронт в ShareView
     * ошибочно использовал linkId как songId — и попадал на `/player/<linkId>`, где
     * `validateShareSession` искал эту ссылку для song_id=linkId и не находил (404).
     * Теперь явно отдаём songId + минимальную информацию о песне (название, автор,
     * альбом, год, URL превью обложки/автора) — нужно ShareView лендингу, чтобы до
     * открытия плеера нарисовать карточку песни с картинками. Подгружается тем же
     * SQL, что и в `PublicPlayerController.playerData` (статичная ключевая формула
     * storageKey, без обращения к song.pictureAlbum/pictureAuthor — защита от
     * rootFolder/APP_WORK_ON_SERVER).
     *
     * `expiresAt` — реальный момент окончания lease (epoch ms). Нужен ShareView,
     * чтобы показать гостю «Доступно до ДД.ММ.ГГГГ ЧЧ:ММ» в его TZ (FR-011, US4).
     * Источник — `System.currentTimeMillis() + leaseTtlSeconds*1000L` (для нового
     * lease) или `leaseUntil.time` (для existing lease). Погрешность ≤ 1 сек
     * при lease 25 сек — для отображения пользователю не критично.
     */
    data class TryClaimResult(
        val linkId: Long,
        val songId: Long,
        val sessionTokenHash: String,
        val expiresAt: Long,
        val songName: String,
        val author: String,
        val album: String,
        val year: Int,
        val albumImageUrl: String?,
        val artistImageUrl: String?,
    )

    /**
     * Информация о сессии по ссылке (для админа).
     */
    data class SessionView(
        val sessionId: Long,
        val shareLinkId: Long,
        val songId: Long,
        val browserHash: String,
        val ownerSiteUserId: Long,
        val anonId: String,
        val openedAt: Long,
        val startedAt: Long?,
        val lastSeenAt: Long,
        val finishedAt: Long?,
        val result: String,
    )

    sealed class ShareException(
        val code: ShareErrorCode,
        val httpStatus: Int
    ) : RuntimeException(code.dbValue)

    class NotFound : ShareException(ShareErrorCode.NOT_FOUND, 404)

    class Expired : ShareException(ShareErrorCode.EXPIRED, 404)

    class Revoked : ShareException(ShareErrorCode.REVOKED, 404)

    class SongUnavailable : ShareException(ShareErrorCode.SONG_UNAVAILABLE, 409)

    class ConcurrentLimit : ShareException(ShareErrorCode.CONCURRENT_LIMIT, 409)

    class LeaseExpired : ShareException(ShareErrorCode.LEASE_EXPIRED, 410)

    class RateLimited : ShareException(ShareErrorCode.RATE_LIMITED, 429)

    class NotOwner : ShareException(ShareErrorCode.NOT_OWNER, 403)

    class LinkAlreadyActive(
        val reason: String,
        val limit: Long,
        val actual: Long,
    ) : ShareException(ShareErrorCode.LINK_ALREADY_ACTIVE, 429)

    class TokenMissing : ShareException(ShareErrorCode.TOKEN_MISSING, 400)

    /**
     * Системная (не доменная) ошибка, пробрасывается из catch-all в [tryClaim],
     * [heartbeat] и др. Контроллер не должен маскировать её как 404;
     * FR-010, FR-014 (spec 167-fix-share-claim-500). Раньше любое неожиданное
     * исключение из БД маскировалось под [NotFound] (404 `share.notFound`) — это
     * ломало диагностику инцидентов уровня «БД недоступна» / «relation does not
     * exist» / NPE в SQL-обёртке.
     *
     * Конструктор принимает [cause] для сохранения stacktrace через [addSuppressed].
     * Родительский [ShareException] не принимает cause в primary constructor
     * (его контракт заточен под код+httpStatus); чтобы не ломать существующие
     * подклассы, причина пробрасывается через suppressed-механизм, который
     * попадает в логи и stacktrace при `e.printStackTrace()` / `Throwable.stackTraceToString()`.
     */
    class InternalError(
        cause: Throwable,
    ) : ShareException(ShareErrorCode.INTERNAL, 500) {
        init {
            addSuppressed(cause)
        }
    }

    private val random = SecureRandom()
    private val claimRateBuckets = ConcurrentHashMap<String, AtomicInteger>()

    // ---------- Owner API ----------

    /**
     * Создаёт (или перевыпускает) ссылку. Если у пользователя уже была
     * `active=true` ссылка на ту же песню, она переводится в `active=false`
     * с `revoke_reason='replaced'`. Старый секрет перестаёт работать.
     *
     * @throws SongUnavailable если контент песни не готов или она помечена SKIP
     * @throws LinkAlreadyActive при превышении лимитов (генераций/сутки, активных/пользователь, перевыпусков/час)
     */
    fun createLink(
        siteUserId: Long,
        songId: Long,
        ttlSeconds: Long,
        baseUrl: String,
        database: KaraokeConnection = WORKING_DATABASE,
    ): CreateResult {
        if (!songIsShareable(songId, database)) {
            throw SongUnavailable()
        }

        val maxActive = props.maxActivePerUser
        val activeCount = countActiveForUser(siteUserId, database)
        if (activeCount >= maxActive) {
            log.warn(
                "share limit: maxActivePerUser hit for user=$siteUserId " +
                    "($activeCount/$maxActive)",
            )
            throw LinkAlreadyActive("maxActivePerUser", maxActive, activeCount)
        }

        val maxGenDay = props.maxGenerationsPerDay
        val genDayCount = countGenerationsLast24h(siteUserId, database)
        if (genDayCount >= maxGenDay) {
            log.warn(
                "share limit: maxGenerationsPerDay hit for user=$siteUserId " +
                    "($genDayCount/$maxGenDay)",
            )
            throw LinkAlreadyActive("maxGenerationsPerDay", maxGenDay, genDayCount)
        }

        val maxReissueHour = props.maxReissuesPerSongPerHour
        val reissueHourCount = countReissuesLastHour(siteUserId, songId, database)
        if (reissueHourCount >= maxReissueHour) {
            log.warn(
                "share limit: maxReissuesPerSongPerHour hit for user=$siteUserId " +
                    "song=$songId ($reissueHourCount/$maxReissueHour)",
            )
            throw LinkAlreadyActive(
                "maxReissuesPerSongPerHour",
                maxReissueHour,
                reissueHourCount,
            )
        }

        val secret = generateSecret()
        val tokenHash = sha256Hex(secret)
        val now = System.currentTimeMillis()
        val expiresAt = now + ttlSeconds * 1000L

        val conn = database.getConnection() ?: throw SongUnavailable()
        try {
            conn
                .prepareStatement(
                    "UPDATE tbl_song_share_links SET active=false, revoked_at=now(), revoke_reason='replaced' " +
                        "WHERE owner_site_user_id=? AND song_id=? AND active",
                ).use { ps ->
                    ps.setLong(1, siteUserId)
                    ps.setLong(2, songId)
                    ps.executeUpdate()
                }

            val newId: Long
            conn
                .prepareStatement(
                    "INSERT INTO tbl_song_share_links " +
                        "(owner_site_user_id, song_id, token_hash, active, expires_at, created_at) " +
                        "VALUES (?, ?, ?, true, ?, now()) RETURNING id",
                ).use { ps ->
                    ps.setLong(1, siteUserId)
                    ps.setLong(2, songId)
                    ps.setString(3, tokenHash)
                    // Явное преобразование epoch ms → LocalDateTime в Europe/Moscow. Раньше
                    // использовался setTimestamp(Timestamp(epochMs)), который читает JVM TZ
                    // через Timestamp.toString() — на машинах с TZ != Europe/Moscow запись
                    // попадает в БД со сдвигом. setObject(..., Types.TIMESTAMP) передаёт
                    // значение как naive LocalDateTime, и Postgres хранит его 1:1 (FR-014,
                    // Assumption #1 спеки).
                    ps.setObject(4, toMskLocalDateTime(expiresAt), Types.TIMESTAMP)
                    val rs = ps.executeQuery()
                    rs.next()
                    newId = rs.getLong(1)
                }
            return CreateResult(
                linkId = newId,
                secret = secret,
                expiresAt = expiresAt,
                url = "$baseUrl/share/$newId/$secret",
            )
        } finally {
            // Не закрываем соединение — оно per-thread (см. KaraokeConnection.getConnection).
            // Вызывающий код (HTTP-поток Tomcat) переиспользует соединение для следующего запроса.
            // (см. принцип specs/087-fix-shared-db-connection).
        }
    }

    /**
     * Отзыв ссылки владельцем. Активные сессии завершаются на ближайшем heartbeat
     * (или фоновым sweeper'ом в течение ≤60 сек).
     */
    fun revokeLink(siteUserId: Long, songId: Long, reason: String = "manual", database: KaraokeConnection = WORKING_DATABASE) {
        val conn = database.getConnection() ?: return
        conn
            .prepareStatement(
                "UPDATE tbl_song_share_links SET active=false, revoked_at=now(), revoke_reason=? " +
                    "WHERE owner_site_user_id=? AND song_id=? AND active",
            ).use { ps ->
                ps.setString(1, reason)
                ps.setLong(2, siteUserId)
                ps.setLong(3, songId)
                ps.executeUpdate()
            }
    }

    /**
     * Admin-отзыв по linkId (не по паре owner+song). Используется [SiteShareLinksController]
     * — админ видит ссылку конкретного пользователя и отзывает её по id, без знания songId.
     * Завершает все активные lease-сессии (finished_at, result='revoked') и обнуляет active_session_*.
     * Все три операции — в одной транзакции (атомарность).
     */
    fun revokeLinkById(linkId: Long, reason: String = "admin", database: KaraokeConnection = WORKING_DATABASE) {
        val conn = database.getConnection() ?: return
        conn.autoCommit = false
        try {
            conn
                .prepareStatement(
                    "UPDATE tbl_song_share_links SET active=false, revoked_at=now(), revoke_reason=? " +
                        "WHERE id=? AND active",
                ).use { ps ->
                    ps.setString(1, reason)
                    ps.setLong(2, linkId)
                    ps.executeUpdate()
                }
            conn
                .prepareStatement(
                    "UPDATE tbl_song_share_sessions SET finished_at=now(), result='revoked' " +
                        "WHERE share_link_id=? AND finished_at IS NULL",
                ).use { ps ->
                    ps.setLong(1, linkId)
                    ps.executeUpdate()
                }
            conn
                .prepareStatement(
                    "UPDATE tbl_song_share_links SET active_session_token_hash=NULL, " +
                        "active_session_browser_hash=NULL, active_session_lease_until=NULL WHERE id=?",
                ).use { ps ->
                    ps.setLong(1, linkId)
                    ps.executeUpdate()
                }
            conn.commit()
        } catch (e: Exception) {
            conn.rollback()
            throw e
        } finally {
            conn.autoCommit = true
        }
    }

    /**
     * Возвращает текущую `active=true` ссылку пользователя на песню, либо `null`.
     * Секрет НЕ возвращается — он не сохраняется после создания.
     */
    fun getCurrentForOwner(
        siteUserId: Long,
        songId: Long,
        database: KaraokeConnection = WORKING_DATABASE,
    ): OwnerLinkView? {
        val conn = database.getConnection() ?: return null
        conn
            .prepareStatement(
                "SELECT id, song_id, active, " +
                    "extract(epoch from expires_at AT TIME ZONE 'Europe/Moscow')*1000 as expires_ms, " +
                    "extract(epoch from created_at AT TIME ZONE 'Europe/Moscow')*1000 as created_ms, " +
                    "extract(epoch from revoked_at AT TIME ZONE 'Europe/Moscow')*1000 as revoked_ms, " +
                    "revoke_reason, " +
                    "extract(epoch from first_used_at AT TIME ZONE 'Europe/Moscow')*1000 as first_used_ms, " +
                    "extract(epoch from last_used_at AT TIME ZONE 'Europe/Moscow')*1000 as last_used_ms, " +
                    "sessions_total, rejected_concurrent " +
                    "FROM tbl_song_share_links " +
                    "WHERE owner_site_user_id=? AND song_id=? AND active",
            ).use { ps ->
                ps.setLong(1, siteUserId)
                ps.setLong(2, songId)
                val rs = ps.executeQuery()
                if (!rs.next()) return null
                val expiresAt = rs.getLong("expires_ms")
                val createdAt = rs.getLong("created_ms")
                val revokedAt = rs.getLong("revoked_ms").takeIf { !rs.wasNull() }
                val firstUsedAt = rs.getLong("first_used_ms").takeIf { !rs.wasNull() }
                val lastUsedAt = rs.getLong("last_used_ms").takeIf { !rs.wasNull() }
                return OwnerLinkView(
                    linkId = rs.getLong("id"),
                    songId = rs.getLong("song_id"),
                    active = rs.getBoolean("active"),
                    expiresAt = expiresAt,
                    createdAt = createdAt,
                    revokedAt = revokedAt,
                    revokeReason = rs.getString("revoke_reason") ?: "",
                    firstUsedAt = firstUsedAt,
                    lastUsedAt = lastUsedAt,
                    sessionsTotal = rs.getInt("sessions_total"),
                    rejectedConcurrent = rs.getInt("rejected_concurrent"),
                )
            }
    }

    // ---------- Guest API ----------

    /**
     * Резолвит секрет в ссылку или бросает [NotFound] (единый ответ для всех
     * негативных кейсов: несуществующая / отозванная / просроченная / SKIP-песня).
     */
    private fun resolveForGuest(secret: String, database: KaraokeConnection = WORKING_DATABASE): Long {
        val conn = database.getConnection() ?: throw NotFound()
        // SHA-256 считается на стороне Kotlin (sha256Hex) и сравнивается с token_hash как
        // обычная hex-строка через setString. Самый надёжный вариант: проверял через
        // encode(sha256(?::bytea), 'hex') — один и тот же SQL через PREPARE/EXECUTE в psql
        // находит запись, а через JDBC PreparedStatement+setString — нет, через setBytes — тоже
        // не находит (видимо PostgreSQL JDBC-драйвер передаёт параметр не так, как psql). Поэтому
        // вычисляем хэш в JVM и сравниваем как строки.
        val tokenHash = sha256Hex(secret)
        conn
            .prepareStatement(
                "SELECT id, song_id FROM tbl_song_share_links " +
                    "WHERE token_hash=? AND active AND expires_at>now() AND revoked_at IS NULL",
            ).use { ps ->
                ps.setString(1, tokenHash)
                val rs = ps.executeQuery()
                if (!rs.next()) throw NotFound()
                val linkId = rs.getLong("id")
                val songId = rs.getLong("song_id")
                // Дополнительно проверяем, что песня всё ещё доступна — единый NotFound.
                if (!songIsShareable(songId, database)) throw NotFound()
                return linkId
            }
    }

    /**
     * Возвращает `linkId` для активного секрета или `null`, если ссылка отозвана/просрочена.
     * Используется [PublicPlayerController.access] для проверки гостевого доступа без
     * heartbeat-прохода — просто факт, что share-ссылка действительна на момент вызова /access.
     */
    fun findLinkIdBySecret(secret: String, database: KaraokeConnection = WORKING_DATABASE): Long? {
        val conn = database.getConnection() ?: return null
        // SHA-256 на стороне Kotlin (см. подробный комментарий в resolveForGuest).
        val tokenHash = sha256Hex(secret)
        conn
            .prepareStatement(
                "SELECT id FROM tbl_song_share_links " +
                    "WHERE token_hash=? AND active AND expires_at>now() AND revoked_at IS NULL",
            ).use { ps ->
                ps.setString(1, tokenHash)
                val rs = ps.executeQuery()
                return if (rs.next()) rs.getLong(1) else null
            }
    }

    /**
     * Атомарный claim playback-сессии. Создаёт `tbl_song_share_sessions` строку,
     * выдаёт `sessionTokenHash`. Учитывает лимит ≤2 устройств.
     *
     * @param secret исходный секрет из URL
     * @param browserHash SHA-256 от browserId в localStorage
     * @param request для rate-limit по IP
     * @return sessionTokenHash (клиент должен передавать его в `X-Share-Session` для
     *         `playerdata`/stem-запросов и в `/heartbeat`, `/release`)
     */
    fun tryClaim(
        secret: String,
        browserHash: String,
        request: HttpServletRequest,
        database: KaraokeConnection = WORKING_DATABASE,
    ): TryClaimResult {
        checkRateLimit(request)
        val linkId = resolveForGuest(secret, database)
        val ownerId = ownerIdOf(linkId, database)
        val songId = songIdOf(linkId, database)
        // Подгружаем минимальную карточку песни (название, автор, альбом, год, URL превью
        // обложки/автора) одним запросом — нужно ShareView лендингу, чтобы показать
        // картинки и подпись до нажатия «Открыть плеер». Сырой SQL: тянем `tbl_songs`
        // напрямую, чтобы не зависеть от karaoke-app-классов (Song/Storage) — в karaoke-web
        // они заглушки (см. WebKaraokeStorageServiceImpl).
        val songInfo = loadSongInfo(songId, database)
        // DEBUG: принудительно log.error вместо log.info — точно должно дойти до консоли.
        log.error("ShareLink tryClaim START linkId=$linkId songId=$songId songName='${songInfo.name}' browserHash='${browserHash.take(8)}…'")
        val now = System.currentTimeMillis()
        val sessionSecret = generateSecret()
        val sessionTokenHash = sha256Hex(sessionSecret)

        val maxConcurrent = props.maxConcurrentSessions.toInt()

        val conn = database.getConnection() ?: throw NotFound()
        try {
            // 1. Проверяем — может, тот же browserHash уже имеет активную сессию по этой ссылке.
            //    То же устройство повторно: возвращаем существующий sessionToken (он же лежит
            //    в tbl_song_share_links.active_session_*).
            conn
                .prepareStatement(
                    "SELECT active_session_token_hash, active_session_lease_until " +
                        "FROM tbl_song_share_links WHERE id=?",
                ).use { ps ->
                    ps.setLong(1, linkId)
                    val rs = ps.executeQuery()
                    if (!rs.next()) throw NotFound()
                    val existingTokenHash = rs.getString("active_session_token_hash")
                    val leaseUntil = rs.getTimestamp("active_session_lease_until")
                    if (existingTokenHash != null && leaseUntil != null && leaseUntil.time > now) {
                        // TODO check that existingTokenHash corresponds to the same browserHash. For now
                        // any existing active lease is returned (multiple browserHash tabs on the same
                        // device share the same lease slot — see design D4).
                        return TryClaimResult(
                            linkId,
                            songId,
                            existingTokenHash,
                            expiresAt = leaseUntil.time,
                            songName = songInfo.name,
                            author = songInfo.author,
                            album = songInfo.album,
                            year = songInfo.year,
                            albumImageUrl = songInfo.albumImageUrl,
                            artistImageUrl = songInfo.artistImageUrl,
                        )
                    }
                }

            // 2. Считаем активные сессии по ссылке (< 2).
            val activeCount = countActiveSessions(linkId, database)
            if (activeCount >= maxConcurrent) {
                incrementRejected(linkId, database)
                throw ConcurrentLimit()
            }

            // 3. Создаём сессию.
            val ipHash = clientIpHash(request)
            val uaHash = userAgentHash(request)
            conn
                .prepareStatement(
                    "INSERT INTO tbl_song_share_sessions " +
                        "(share_link_id, song_id, browser_hash, owner_site_user_id, anon_id, " +
                        "client_ip_hash, user_agent_hash) " +
                        "VALUES (?, ?, ?, ?, '', ?, ?) RETURNING id",
                ).use { ps ->
                    ps.setLong(1, linkId)
                    ps.setLong(2, songId)
                    ps.setString(3, browserHash)
                    ps.setLong(4, ownerId)
                    ps.setString(5, ipHash)
                    ps.setString(6, uaHash)
                    val rs = ps.executeQuery()
                    rs.next()
                    val newSessionId = rs.getLong(1)
                    if (newSessionId <= 0) throw NotFound()
                }

            // 4. Обновляем активный lease на ссылке и счётчики.
            val leaseTtlMs = props.leaseTtlSeconds * 1000L
            conn
                .prepareStatement(
                    "UPDATE tbl_song_share_links SET " +
                        "active_session_token_hash=?, active_session_browser_hash=?, " +
                        "active_session_lease_until=now() + (? || ' milliseconds')::interval, " +
                        "first_used_at = COALESCE(first_used_at, now()), last_used_at=now(), " +
                        "sessions_total = sessions_total + 1 " +
                        "WHERE id=?",
                ).use { ps ->
                    ps.setString(1, sessionTokenHash)
                    ps.setString(2, browserHash)
                    ps.setLong(3, leaseTtlMs)
                    ps.setLong(4, linkId)
                    ps.executeUpdate()
                }

            return TryClaimResult(
                linkId,
                songId,
                sessionTokenHash,
                expiresAt = now + props.leaseTtlSeconds * 1000L,
                songName = songInfo.name,
                author = songInfo.author,
                album = songInfo.album,
                year = songInfo.year,
                albumImageUrl = songInfo.albumImageUrl,
                artistImageUrl = songInfo.artistImageUrl,
            )
        } catch (e: ShareException) {
            println("[tryClaim] ShareException class=${e::class.simpleName} msg=${e.message}")
            e.printStackTrace()
            log.error("ShareLink tryClaim ShareException: class=${e::class.simpleName} msg=${e.message}")
            throw e
        } catch (e: Exception) {
            println("[tryClaim] UNEXPECTED class=${e::class.simpleName} msg=${e.message}")
            e.printStackTrace()
            log.error("ShareLink tryClaim UNEXPECTED class=${e::class.simpleName} msg=${e.message}", e)
            throw InternalError(e)
        }
    }

    /**
     * Продление lease. Вызывается клиентом раз в 25 сек.
     */
    fun heartbeat(sessionTokenHash: String, database: KaraokeConnection = WORKING_DATABASE) {
        val conn = database.getConnection() ?: throw LeaseExpired()
        val leaseTtlMs = props.leaseTtlSeconds * 1000L
        conn
            .prepareStatement(
                "UPDATE tbl_song_share_links SET " +
                    "active_session_lease_until = now() + (? || ' milliseconds')::interval, " +
                    "last_used_at = now() " +
                    "WHERE active_session_token_hash=? AND active AND expires_at>now()",
            ).use { ps ->
                ps.setLong(1, leaseTtlMs)
                ps.setString(2, sessionTokenHash)
                if (ps.executeUpdate() == 0) throw LeaseExpired()
            }
        conn
            .prepareStatement(
                "UPDATE tbl_song_share_sessions SET last_seen_at=now() " +
                    "FROM tbl_song_share_links l " +
                    "WHERE l.active_session_token_hash=? AND tbl_song_share_sessions.share_link_id=l.id " +
                    "AND tbl_song_share_sessions.finished_at IS NULL",
            ).use { ps ->
                ps.setString(1, sessionTokenHash)
                ps.executeUpdate()
            }
    }

    /**
     * Диагностический пошаговый проход по этапам tryClaim — для дебага когда логи не
     * помогают. Возвращает Map<String, Any?> с результатом каждого шага. Используется
     * только через /api/public/share/debug — в прод не зовётся.
     */
    fun debugTryClaim(
        secret: String,
        database: KaraokeConnection = WORKING_DATABASE,
    ): Map<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        out["step1_resolve"] =
            try {
                val linkId = resolveForGuest(secret, database)
                out["linkId"] = linkId
                "OK linkId=$linkId"
            } catch (e: Throwable) {
                out["error_step1"] = "class=${e::class.simpleName} msg=${e.message}"
                "FAILED: ${e::class.simpleName}: ${e.message}"
            }
        val linkId = (out["linkId"] as? Long) ?: return out
        out["step2_ownerId"] =
            try {
                val ownerId = ownerIdOf(linkId, database)
                out["ownerId"] = ownerId
                "OK ownerId=$ownerId"
            } catch (e: Throwable) {
                out["error_step2"] = "class=${e::class.simpleName} msg=${e.message}"
                "FAILED: ${e::class.simpleName}: ${e.message}"
            }
        val songId =
            try {
                songIdOf(linkId, database)
            } catch (e: Throwable) {
                out["error_step3"] = "class=${e::class.simpleName} msg=${e.message}"
                return out
            }
        out["songId"] = songId
        out["step3_songId"] = "OK songId=$songId"
        out["step4_songIsShareable"] =
            try {
                val shareable = songIsShareable(songId, database)
                out["shareable"] = shareable
                "OK shareable=$shareable"
            } catch (e: Throwable) {
                out["error_step4"] = "class=${e::class.simpleName} msg=${e.message}"
                "FAILED: ${e::class.simpleName}: ${e.message}"
            }
        out["step5_loadSongInfo"] =
            try {
                val info = loadSongInfo(songId, database)
                out["songName"] = info.name
                out["author"] = info.author
                "OK songName='${info.name}'"
            } catch (e: Throwable) {
                out["error_step5"] = "class=${e::class.simpleName} msg=${e.message}"
                "FAILED: ${e::class.simpleName}: ${e.message}"
            }
        return out
    }

    /**
     * Завершение сессии. Освобождает lease. Вызывается при `_onEnded`, `beforeunload`
     * (через sendBeacon) или при ручном отзыве.
     *
     * @param result одно из 'ended' | 'closed' | 'timeout' | 'revoked' | 'replaced'
     */
    fun release(sessionTokenHash: String, result: String, database: KaraokeConnection = WORKING_DATABASE) {
        val conn = database.getConnection() ?: return
        val normalised =
            result.lowercase().let {
                when (it) {
                    "ended", "closed", "timeout", "revoked", "replaced" -> it
                    else -> "closed"
                }
            }
        conn
            .prepareStatement(
                "UPDATE tbl_song_share_sessions SET finished_at=now(), result=? " +
                    "FROM tbl_song_share_links l " +
                    "WHERE l.active_session_token_hash=? " +
                    "AND tbl_song_share_sessions.share_link_id=l.id " +
                    "AND tbl_song_share_sessions.finished_at IS NULL",
            ).use { ps ->
                ps.setString(1, normalised)
                ps.setString(2, sessionTokenHash)
                ps.executeUpdate()
            }
        conn
            .prepareStatement(
                "UPDATE tbl_song_share_links SET " +
                    "active_session_token_hash=NULL, active_session_browser_hash=NULL, " +
                    "active_session_lease_until=NULL " +
                    "WHERE active_session_token_hash=?",
            ).use { ps ->
                ps.setString(1, sessionTokenHash)
                ps.executeUpdate()
            }
    }

    /**
     * По токену сессии возвращает `linkId`. Используется `PublicPlayerController`
     * для проверки живого lease при обращении к stem-эндпоинтам.
     */
    fun validateShareSession(
        sessionTokenHash: String?,
        songId: Long,
        database: KaraokeConnection = WORKING_DATABASE,
    ): Long? {
        if (sessionTokenHash.isNullOrBlank()) return null
        val conn = database.getConnection() ?: return null
        conn
            .prepareStatement(
                "SELECT l.id FROM tbl_song_share_links l " +
                    "WHERE l.active_session_token_hash=? AND l.active AND l.expires_at>now() " +
                    "AND l.song_id=?",
            ).use { ps ->
                ps.setString(1, sessionTokenHash)
                ps.setLong(2, songId)
                val rs = ps.executeQuery()
                return if (rs.next()) rs.getLong(1) else null
            }
    }

    // ---------- Admin API ----------

    fun listLinksForUser(
        siteUserId: Long,
        activeOnly: Boolean,
        limit: Int,
        database: KaraokeConnection = WORKING_DATABASE,
    ): List<OwnerLinkView> {
        val conn = database.getConnection() ?: return emptyList()
        val where = if (activeOnly) "WHERE owner_site_user_id=? AND active" else "WHERE owner_site_user_id=?"
        conn
            .prepareStatement(
                "SELECT id, song_id, active, " +
                    "extract(epoch from expires_at AT TIME ZONE 'Europe/Moscow')*1000 as expires_ms, " +
                    "extract(epoch from created_at AT TIME ZONE 'Europe/Moscow')*1000 as created_ms, " +
                    "extract(epoch from revoked_at AT TIME ZONE 'Europe/Moscow')*1000 as revoked_ms, " +
                    "revoke_reason, " +
                    "extract(epoch from first_used_at AT TIME ZONE 'Europe/Moscow')*1000 as first_used_ms, " +
                    "extract(epoch from last_used_at AT TIME ZONE 'Europe/Moscow')*1000 as last_used_ms, " +
                    "sessions_total, rejected_concurrent " +
                    "FROM tbl_song_share_links $where ORDER BY created_at DESC LIMIT ?",
            ).use { ps ->
                ps.setLong(1, siteUserId)
                ps.setInt(2, limit)
                val rs = ps.executeQuery()
                val out = mutableListOf<OwnerLinkView>()
                while (rs.next()) {
                    val expiresAt = rs.getLong("expires_ms")
                    val createdAt = rs.getLong("created_ms")
                    val revokedAt = rs.getLong("revoked_ms").takeIf { !rs.wasNull() }
                    val firstUsedAt = rs.getLong("first_used_ms").takeIf { !rs.wasNull() }
                    val lastUsedAt = rs.getLong("last_used_ms").takeIf { !rs.wasNull() }
                    out.add(
                        OwnerLinkView(
                            linkId = rs.getLong("id"),
                            songId = rs.getLong("song_id"),
                            active = rs.getBoolean("active"),
                            expiresAt = expiresAt,
                            createdAt = createdAt,
                            revokedAt = revokedAt,
                            revokeReason = rs.getString("revoke_reason") ?: "",
                            firstUsedAt = firstUsedAt,
                            lastUsedAt = lastUsedAt,
                            sessionsTotal = rs.getInt("sessions_total"),
                            rejectedConcurrent = rs.getInt("rejected_concurrent"),
                        ),
                    )
                }
                return out
            }
    }

    fun listSessionsForLink(linkId: Long, database: KaraokeConnection = WORKING_DATABASE): List<SessionView> {
        val conn = database.getConnection() ?: return emptyList()
        conn
            .prepareStatement(
                "SELECT id, share_link_id, song_id, browser_hash, owner_site_user_id, anon_id, " +
                    "extract(epoch from opened_at AT TIME ZONE 'Europe/Moscow')*1000 as opened_ms, " +
                    "extract(epoch from started_at AT TIME ZONE 'Europe/Moscow')*1000 as started_ms, " +
                    "extract(epoch from last_seen_at AT TIME ZONE 'Europe/Moscow')*1000 as last_seen_ms, " +
                    "extract(epoch from finished_at AT TIME ZONE 'Europe/Moscow')*1000 as finished_ms, " +
                    "result FROM tbl_song_share_sessions WHERE share_link_id=? ORDER BY opened_at DESC",
            ).use { ps ->
                ps.setLong(1, linkId)
                val rs = ps.executeQuery()
                val out = mutableListOf<SessionView>()
                while (rs.next()) {
                    out.add(
                        SessionView(
                            sessionId = rs.getLong("id"),
                            shareLinkId = rs.getLong("share_link_id"),
                            songId = rs.getLong("song_id"),
                            browserHash = rs.getString("browser_hash") ?: "",
                            ownerSiteUserId = rs.getLong("owner_site_user_id"),
                            anonId = rs.getString("anon_id") ?: "",
                            openedAt = rs.getLong("opened_ms"),
                            startedAt = rs.getLong("started_ms").takeIf { !rs.wasNull() },
                            lastSeenAt = rs.getLong("last_seen_ms"),
                            finishedAt = rs.getLong("finished_ms").takeIf { !rs.wasNull() },
                            result = rs.getString("result") ?: "",
                        ),
                    )
                }
                return out
            }
    }

    // ---------- Helpers ----------

    // Старый серверный форматировщик меток (МСК-строка) удалён: фронт форматирует
    // epoch ms в TZ устройства через dateFormat.formatDate (FR-011). Сервер больше
    // не отдаёт строковые метки — только единственное числовое поле `expiresAt`
    // (Long, реальный момент). См. spec.md §«Трактовка дат» (FR-013).

    private fun countActiveForUser(siteUserId: Long, database: KaraokeConnection): Long {
        val conn = database.getConnection() ?: return 0
        conn
            .prepareStatement(
                "SELECT count(*) FROM tbl_song_share_links WHERE owner_site_user_id=? AND active",
            ).use { ps ->
                ps.setLong(1, siteUserId)
                val rs = ps.executeQuery()
                rs.next()
                return rs.getLong(1)
            }
    }

    private fun countGenerationsLast24h(siteUserId: Long, database: KaraokeConnection): Long {
        val conn = database.getConnection() ?: return 0
        conn
            .prepareStatement(
                "SELECT count(*) FROM tbl_song_share_links WHERE owner_site_user_id=? AND created_at > now() - interval '24 hours'",
            ).use { ps ->
                ps.setLong(1, siteUserId)
                val rs = ps.executeQuery()
                rs.next()
                return rs.getLong(1)
            }
    }

    private fun countReissuesLastHour(siteUserId: Long, songId: Long, database: KaraokeConnection): Long {
        val conn = database.getConnection() ?: return 0
        conn
            .prepareStatement(
                "SELECT count(*) FROM tbl_song_share_links WHERE owner_site_user_id=? AND song_id=? " +
                    "AND created_at > now() - interval '1 hour'",
            ).use { ps ->
                ps.setLong(1, siteUserId)
                ps.setLong(2, songId)
                val rs = ps.executeQuery()
                rs.next()
                return rs.getLong(1)
            }
    }

    private fun countActiveSessions(linkId: Long, database: KaraokeConnection): Int {
        val conn = database.getConnection() ?: return 0
        conn
            .prepareStatement(
                "SELECT count(*) FROM tbl_song_share_sessions WHERE share_link_id=? AND finished_at IS NULL",
            ).use { ps ->
                ps.setLong(1, linkId)
                val rs = ps.executeQuery()
                rs.next()
                return rs.getInt(1)
            }
    }

    private fun incrementRejected(linkId: Long, database: KaraokeConnection) {
        val conn = database.getConnection() ?: return
        conn
            .prepareStatement(
                "UPDATE tbl_song_share_links SET rejected_concurrent = rejected_concurrent + 1 WHERE id=?",
            ).use { ps ->
                ps.setLong(1, linkId)
                ps.executeUpdate()
            }
    }

    private fun ownerIdOf(linkId: Long, database: KaraokeConnection): Long {
        val conn = database.getConnection() ?: throw NotFound()
        conn.prepareStatement("SELECT owner_site_user_id FROM tbl_song_share_links WHERE id=?").use { ps ->
            ps.setLong(1, linkId)
            val rs = ps.executeQuery()
            if (!rs.next()) throw NotFound()
            return rs.getLong(1)
        }
    }

    private fun songIdOf(linkId: Long, database: KaraokeConnection): Long {
        val conn = database.getConnection() ?: throw NotFound()
        conn.prepareStatement("SELECT song_id FROM tbl_song_share_links WHERE id=?").use { ps ->
            ps.setLong(1, linkId)
            val rs = ps.executeQuery()
            if (!rs.next()) throw NotFound()
            return rs.getLong(1)
        }
    }

    internal fun songHasSkipTag(tags: String?): Boolean =
        (tags ?: "").split(" ").any { it.trim().equals("SKIP", ignoreCase = true) }

    /**
     * Минимальная карточка песни для ShareView лендинга. Загружается сырым SQL — Song.loadFromDbById
     * в karaoke-web недоступен (storageService — заглушка, см. WebKaraokeStorageServiceImpl).
     * URL превью собирается по чистой формуле storageKey, без обращения к song.pictureAlbum/Author
     * (защита от rootFolder/APP_WORK_ON_SERVER).
     */
    private data class SongInfo(
        val name: String,
        val author: String,
        val album: String,
        val year: Int,
        val albumImageUrl: String?,
        val artistImageUrl: String?,
    )

    private fun loadSongInfo(songId: Long, database: KaraokeConnection): SongInfo {
        val conn = database.getConnection() ?: throw NotFound()
        conn
            .prepareStatement(
                "SELECT song_name, song_author, song_album, song_year, player_readiness_flags FROM tbl_songs WHERE id=?",
            ).use { ps ->
                ps.setLong(1, songId)
                val rs = ps.executeQuery()
                if (!rs.next()) throw NotFound()
                val name = rs.getString("song_name") ?: "Песня"
                val author = rs.getString("song_author") ?: ""
                val album = rs.getString("song_album") ?: ""
                val year = rs.getInt("song_year")
                val flags = rs.getString("player_readiness_flags") ?: "{}"
                // Тот же storage-key, что в PublicPlayerController.pictureAlbumStorageKey /
                // pictureAuthorStorageKey (KaraokeFileType.PICTURE_ALBUM/PICTURE_AUTHOR: extention=png,
                // suffix=.album/.author). Сырая формула — без обращения к song.pictureAlbum/Author,
                // которые в karaoke-web валятся на rootFolder/APP_WORK_ON_SERVER.
                val albumKey = "$author/$year - $album/$author - $year - $album.album.png"
                val artistKey = "$author/$author.author.png"
                val albumImageUrl =
                    if (flags.contains("\"pictureAlbumReady\":true")) {
                        "/api/public/picture?file=" +
                            java.net.URLEncoder.encode(albumKey, java.nio.charset.StandardCharsets.UTF_8)
                    } else {
                        null
                    }
                val artistImageUrl =
                    if (flags.contains("\"pictureAuthorReady\":true")) {
                        "/api/public/picture?file=" +
                            java.net.URLEncoder.encode(artistKey, java.nio.charset.StandardCharsets.UTF_8)
                    } else {
                        null
                    }
                return SongInfo(name, author, album, year, albumImageUrl, artistImageUrl)
            }
    }

    /**
     * Проверяет, что песня доступна для share-ссылки — полная проверка готовности контента
     * (см. Song.isContentReady): id_status>=6, готовы оба стема, обе картинки, есть непустые
     * маркеры; не помечена SKIP. Сделано на сыром SQL, чтобы не тянуть `Song.loadFromDbById`
     * с зависимостями от storageService, которые в karaoke-web — заглушки (см.
     * WebKaraokeStorageServiceImpl.kt). Раньше проверялись только стемы, что создавало link
     * для песен с готовыми стемами, но без картинок/маркеров/idStatus<6 — публичный плеер
     * не мог отдать audioAccompanimentUrl/audioVocalsUrl и KaraokePlayer показывал
     * «Данная песня не может быть проиграна». Теперь проверка полная.
     */
    internal fun songIsShareablePublic(songId: Long, database: KaraokeConnection): Boolean = songIsShareable(songId, database)

    private fun songIsShareable(songId: Long, database: KaraokeConnection): Boolean {
        val conn = database.getConnection() ?: return false
        conn
            .prepareStatement(
                "SELECT tags, id_status, source_markers, player_readiness_flags " +
                    "FROM tbl_songs WHERE id=?",
            ).use { ps ->
                ps.setLong(1, songId)
                val rs = ps.executeQuery()
                if (!rs.next()) return false
                val tags = rs.getString("tags") ?: ""
                if (songHasSkipTag(tags)) return false
                if (rs.getInt("id_status") < 6) return false
                if (rs.getString("source_markers").isNullOrBlank()) return false
                val flags = rs.getString("player_readiness_flags") ?: "{}"
                return flags.contains("\"stemAccompanimentReady\":true") &&
                    flags.contains("\"stemVocalReady\":true") &&
                    flags.contains("\"pictureAlbumReady\":true") &&
                    flags.contains("\"pictureAuthorReady\":true")
            }
    }

    private fun checkRateLimit(request: HttpServletRequest) {
        val ip =
            com.svoemesto.karaokeweb.util.ClientIpResolver
                .resolve(request)
        val key = "claim:$ip:${System.currentTimeMillis() / 60_000}"
        val maxPerMin = props.claimRateLimitPerIpPerMin.toInt()
        val counter = claimRateBuckets.computeIfAbsent(key) { AtomicInteger(0) }
        val n = counter.incrementAndGet()
        if (n > maxPerMin) throw RateLimited()
    }

    private fun clientIpHash(request: HttpServletRequest): String {
        val ip =
            com.svoemesto.karaokeweb.util.ClientIpResolver
                .resolve(request)
        return sha256Hex("ip:$ip:share-salt")
    }

    private fun userAgentHash(request: HttpServletRequest): String {
        val ua = request.getHeader("User-Agent") ?: ""
        return sha256Hex("ua:$ua:share-salt")
    }

    private fun generateSecret(): String {
        val bytes = ByteArray(32).also { random.nextBytes(it) }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    companion object {
        fun sha256Hex(input: String): String {
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(input.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
