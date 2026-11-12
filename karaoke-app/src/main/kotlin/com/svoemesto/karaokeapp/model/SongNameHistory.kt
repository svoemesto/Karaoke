package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.WORKING_DATABASE
import org.slf4j.LoggerFactory
import java.sql.Timestamp

/**
 * История прежних имён песни (KARAOKE-429, спека 739, FR-013).
 *
 * **Зачем.** В БД хранится только текущее `file_name` песни. FR-013 требует удалять
 * объекты песни в хранилищах «как по текущему имени, так и по ранее использовавшимся
 * вариантам имени (оставшиеся после переименований)». Без истории имён это
 * неисполнимо безопасно: сканировать весь префикс альбома нельзя — под ним лежат
 * артефакты соседних песен (см. R-004 в `specs/739-song-delete-cascade/research.md`).
 *
 * **Кто пишет.** [Song.renameAllArtifacts] и [Song.renameCascadeExtraArtifacts] —
 * прежнее имя фиксируется **ДО** переименования (в момент, когда старое имя ещё
 * актуально).
 *
 * **Таблица.** `tbl_song_name_history (id, song_id, file_name, changed_at)`,
 * уникальность пары `(song_id, file_name)` — миграция
 * `deploy/karaoke-db/59_song_name_history.sql`. FK на `tbl_songs` намеренно НЕ стоит:
 * строка песни удаляется раньше журнала, и история должна её пережить.
 *
 * @see specs/739-song-delete-cascade/spec.md FR-013
 */
object SongNameHistory {
    /** Категория логов истории имён. */
    private val log = LoggerFactory.getLogger("infra.catalog.namehistory")

    /** Имя таблицы. */
    const val TABLE_NAME = "tbl_song_name_history"

    /**
     * Зафиксировать прежнее имя песни.
     *
     * Идемпотентно: повторная запись того же имени НЕ создаёт дубль и НЕ приводит к
     * нарушению `UNIQUE` (используется `ON CONFLICT DO NOTHING`).
     *
     * @param songId идентификатор песни.
     * @param oldFileName прежнее имя файла песни (без расширения).
     * @param database подключение к БД (по умолчанию — рабочая LOCAL-БД).
     * @return `true`, если запись выполнена (или уже была); `false`, если БД недоступна.
     */
    fun record(
        songId: Long,
        oldFileName: String,
        database: KaraokeConnection = WORKING_DATABASE,
    ): Boolean {
        if (songId <= 0L) return false
        if (oldFileName.isBlank()) return false
        val connection =
            database.getConnection()
                ?: run {
                    log.warn("song.namehistory.not_recorded: songId={} нет соединения с БД {}", songId, database.name)
                    return false
                }
        return try {
            val sql =
                "INSERT INTO $TABLE_NAME (song_id, file_name, changed_at) VALUES (?, ?, ?) " +
                    "ON CONFLICT (song_id, file_name) DO NOTHING"
            connection.prepareStatement(sql).use { ps ->
                ps.setLong(1, songId)
                ps.setString(2, oldFileName)
                ps.setTimestamp(3, Timestamp.valueOf(java.time.LocalDateTime.now()))
                ps.executeUpdate()
            }
            true
        } catch (e: Exception) {
            // Отсутствие таблицы (миграция 59 ещё не применена) не должно ломать
            // переименование песни: предупреждение в лог, работа продолжается.
            log.warn("song.namehistory.not_recorded: songId={} '{}' — {}", songId, oldFileName, e.toString())
            false
        }
    }

    /**
     * Прежние имена песни.
     *
     * @param songId идентификатор песни.
     * @param database подключение к БД (по умолчанию — рабочая LOCAL-БД).
     * @return список прежних имён (пустой, если истории нет или БД недоступна).
     */
    fun listForSong(
        songId: Long,
        database: KaraokeConnection = WORKING_DATABASE,
    ): List<String> {
        if (songId <= 0L) return emptyList()
        val connection = database.getConnection() ?: return emptyList()
        return try {
            connection
                .prepareStatement("SELECT file_name FROM $TABLE_NAME WHERE song_id = ? ORDER BY id")
                .use { ps ->
                    ps.setLong(1, songId)
                    ps.executeQuery().use { rs ->
                        val result = mutableListOf<String>()
                        while (rs.next()) {
                            rs.getString("file_name")?.let { result.add(it) }
                        }
                        result
                    }
                }
        } catch (e: Exception) {
            log.warn("song.namehistory.not_read: songId={} — {}", songId, e.toString())
            emptyList()
        }
    }
}
