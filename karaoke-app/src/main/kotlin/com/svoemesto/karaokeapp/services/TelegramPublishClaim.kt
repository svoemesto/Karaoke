package com.svoemesto.karaokeapp.services

import com.svoemesto.karaokeapp.KaraokeProperties
import com.svoemesto.karaokeapp.WORKING_DATABASE
import com.svoemesto.karaokeapp.model.Song
import java.sql.SQLException
import java.time.OffsetDateTime

/**
 * Атомарный захват публикации демо-версии в Telegram (BR-062, BR-138; аудит #336, пакет
 * `PKG-RACE-PUBLISH`).
 *
 * **Проблема, которую решает компонент.** До этой правки идемпотентность публикации была
 * check-then-act по уже загруженному объекту [Song]: `publishToTelegram` читал
 * `idTelegramDemo`/`telegramAutoPublishState` из памяти, а `saveToDbLocked()` записывал
 * `publishing` уже после проверки. Два потока (HTTP-кнопка «Опубликовать сейчас» и
 * `TelegramAutoPublishScheduler`) читали одну и ту же строку до записи, оба видели пустое
 * состояние и оба вызывали `sendVideo` → два одинаковых поста в публичном канале.
 *
 * **Решение.** Захват вынесен в транзакцию БД: `SELECT … FOR NO KEY UPDATE` на строку
 * `tbl_songs` (та же блокировка, что в `Song.saveToDbLocked`, ADR-0001 — ручные
 * commit/rollback, пула соединений нет), свежее чтение `id_telegram_demo` и
 * `player_readiness_flags`, затем точечный `UPDATE tbl_songs SET player_readiness_flags = ?`
 * и `commit`. Только поток, зафиксировавший `publishing`, идёт в `sendVideo`; остальные
 * получают [TelegramPublishClaim.Busy] и не отправляют ничего.
 *
 * **Почему не `UPDATE … WHERE telegram_auto_publish_state = …`.** Состояние публикации
 * хранится не отдельной колонкой, а строковым ключом внутри JSON-блоба
 * `player_readiness_flags` (`text`, см. `deploy/karaoke-db/26_player_readiness_flags.sql` и
 * `knowledge/domains/publishing/components/publishing-services.md`). Условный UPDATE по
 * JSON-ключу потребовал бы `jsonb`-приведения и разбора ISO-таймстампа в SQL; блокировка
 * строки даёт ту же атомарность и переиспользует уже проверенный в specs/299 примитив.
 *
 * **Почему TTL.** `publishing` пишется до сетевого вызова и снимается записью результата
 * (`published`/`send_failed`). Если процесс упал посреди `sendVideo`, состояние осталось бы
 * `publishing` навсегда и заблокировало публикацию песни до ручной правки БД. Поэтому
 * захват считается живым только [TelegramPublishClaimPolicy.CLAIM_TTL_MS]; более старый
 * `publishing` перехватывается повторно.
 *
 * @see specs/366-pkg-race-publish/report.md
 */
internal sealed interface TelegramPublishClaim {
    /** Строка заблокирована, `publishing` записан; вызывающий владеет публикацией. */
    data object Claimed : TelegramPublishClaim

    /** Другой поток уже публикует эту песню (свежий `publishing`); отправлять нельзя. */
    data class Busy(
        val lastAttemptAt: String,
    ) : TelegramPublishClaim

    /** Публикация уже состоялась (свежий `id_telegram_demo`); повтор не нужен. */
    data class AlreadyPublished(
        val messageId: String,
    ) : TelegramPublishClaim

    /** Строки нет (песня удалена) — публиковать нечего. */
    data object NotFound : TelegramPublishClaim

    /** Захват не удался (нет соединения / SQL-ошибка / lock timeout) — fail-closed. */
    data class Failed(
        val error: String,
    ) : TelegramPublishClaim
}

/**
 * Чистая политика захвата — без JDBC, поэтому проверяется юнит-тестами
 * ([TelegramPublishClaimRaceTest]).
 */
internal object TelegramPublishClaimPolicy {
    /**
     * Сколько живёт захват `publishing` без результата. Больше максимального времени
     * `sendVideo` с ретраями FR-010 (3 попытки × 60 с таймаута + паузы 30 с / 2 мин / 5 мин).
     */
    const val CLAIM_TTL_MS: Long = 15 * 60 * 1000L

    /**
     * Решает, можно ли захватить публикацию.
     *
     * @param idTelegramDemo свежее (прочитанное под блокировкой) значение `id_telegram_demo`
     * @param state свежее значение `telegramAutoPublishState` из `player_readiness_flags`
     * @param lastAttemptAt свежий `telegramAutoPublishLastAttemptAt` (ISO-8601)
     * @param nowMs текущее время, мс с epoch
     * @param ttlMs время жизни захвата
     */
    fun decide(
        idTelegramDemo: String,
        state: String,
        lastAttemptAt: String,
        nowMs: Long,
        ttlMs: Long = CLAIM_TTL_MS,
    ): TelegramPublishClaim {
        if (idTelegramDemo.isNotEmpty()) {
            return TelegramPublishClaim.AlreadyPublished(idTelegramDemo)
        }
        if (state == TelegramAutoPublishState.PUBLISHING.code && !isStale(lastAttemptAt, nowMs, ttlMs)) {
            return TelegramPublishClaim.Busy(lastAttemptAt)
        }
        return TelegramPublishClaim.Claimed
    }

    /** `true`, если захват `publishing` протух (нет/битый timestamp трактуем как протухший). */
    fun isStale(
        lastAttemptAt: String,
        nowMs: Long,
        ttlMs: Long,
    ): Boolean {
        if (lastAttemptAt.isBlank()) return true
        val startedMs =
            try {
                OffsetDateTime.parse(lastAttemptAt).toInstant().toEpochMilli()
            } catch (_: Exception) {
                return true
            }
        return nowMs - startedMs >= ttlMs
    }
}

/**
 * Шов для тестов и точка расширения: реализация захвата. Продакшн-реализация —
 * [JdbcTelegramPublishClaimStore].
 */
internal fun interface TelegramPublishClaimStore {
    fun claim(
        songId: Long,
        nowMs: Long,
        nowIso: String,
    ): TelegramPublishClaim
}

/**
 * Продакшн-захват: транзакция + `SELECT … FOR NO KEY UPDATE` + точечный UPDATE JSON-блоба.
 *
 * Транзакция короткая (только строка `tbl_songs`, без сетевых вызовов): `sendVideo` идёт уже
 * после `commit`, поэтому блокировка строки не удерживается минутами и не мешает
 * `SongEdit`/`saveToDbLocked`.
 */
internal object JdbcTelegramPublishClaimStore : TelegramPublishClaimStore {
    override fun claim(
        songId: Long,
        nowMs: Long,
        nowIso: String,
    ): TelegramPublishClaim {
        val connection =
            WORKING_DATABASE.getConnection()
                ?: return TelegramPublishClaim.Failed("no DB connection available")
        val previousAutoCommit = connection.autoCommit
        try {
            connection.autoCommit = false
            // FR-060: не зависаем на строке, которую держит параллельное сохранение песни.
            val timeoutMs = KaraokeProperties.getLong("songSaveLockedTimeoutMs").coerceAtLeast(1000L)
            connection.createStatement().use { st ->
                st.execute("SET LOCAL lock_timeout = '${timeoutMs}ms'")
            }

            val fresh =
                Song.loadFromDbByIdForUpdate(
                    id = songId,
                    database = WORKING_DATABASE,
                    storageService = KSS_APP,
                    storageApiClient = SAC_APP,
                    connection = connection,
                )
            if (fresh == null) {
                connection.rollback()
                return TelegramPublishClaim.NotFound
            }

            val decision =
                TelegramPublishClaimPolicy.decide(
                    idTelegramDemo = fresh.idTelegramDemo,
                    state = fresh.telegramAutoPublishState,
                    lastAttemptAt = fresh.telegramAutoPublishLastAttemptAt,
                    nowMs = nowMs,
                )
            if (decision != TelegramPublishClaim.Claimed) {
                // НЕ busy → строку не меняем; rollback снимает блокировку.
                connection.rollback()
                return decision
            }

            fresh.telegramAutoPublishState = TelegramAutoPublishState.PUBLISHING.code
            fresh.telegramAutoPublishLastAttemptAt = nowIso
            fresh.telegramAutoPublishLastError = ""
            connection
                .prepareStatement("UPDATE tbl_songs SET player_readiness_flags = ? WHERE id = ?")
                .use { ps ->
                    ps.setString(1, fresh.playerReadinessFlags)
                    ps.setLong(2, songId)
                    if (ps.executeUpdate() != 1) {
                        throw SQLException("claim UPDATE affected != 1 row")
                    }
                }
            connection.commit()
            return TelegramPublishClaim.Claimed
        } catch (e: SQLException) {
            rollbackQuietly(connection)
            val sqlState = e.sqlState ?: ""
            if (sqlState == "55P03" || sqlState == "40P01") {
                println("[TelegramPublishClaim] WARN claim.lock_timeout: songId=$songId sqlState=$sqlState cause=${e.message}")
            } else {
                println("[TelegramPublishClaim] WARN claim.failed: songId=$songId sqlState=$sqlState cause=${e.message}")
            }
            return TelegramPublishClaim.Failed("claim SQL error (sqlState=$sqlState): ${e.message}")
        } catch (e: Exception) {
            rollbackQuietly(connection)
            println("[TelegramPublishClaim] WARN claim.failed: songId=$songId cause=${e.message}")
            return TelegramPublishClaim.Failed("claim error: ${e.message}")
        } finally {
            try {
                connection.autoCommit = previousAutoCommit
            } catch (_: SQLException) {
            }
        }
    }

    private fun rollbackQuietly(connection: java.sql.Connection) {
        try {
            connection.rollback()
        } catch (_: SQLException) {
        }
    }
}
