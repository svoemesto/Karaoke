package com.svoemesto.karaokeapp.services

import com.svoemesto.karaokeapp.WORKING_DATABASE
import com.svoemesto.karaokeapp.model.Song
import com.svoemesto.karaokeapp.tuple.AudioProbe
import org.springframework.stereotype.Service
import java.io.File

/**
 * Разовая починка длительности песен (`tbl_songs.song_ms = 0`).
 *
 * **Зачем.** Инцидент 2026-11-13: импорт из папки сохранял устаревший объект песни и затирал
 * длительность, записанную параллельным кортежом, — у песен оставался `song_ms = 0`
 * (см. `knowledge/domains/catalog/components/dictionaries.md`, раздел «Длительность песни
 * (`song_ms`) и правило "не сохранять устаревший объект"»). Причина устранена, но уже
 * испорченные песни нужно починить.
 *
 * **Как.** Длительность измеряется по файлу-источнику (`ffprobe`, тот же путь, что у кортежа) и
 * записывается тем же способом, что и в кортеже ([Song.refreshDurationMsFromAudioFile]).
 * Пересчитываются только песни с нулевой длительностью — у остальных значение не трогается.
 *
 * @see specs/674-kortege-execution/contracts/admin-api.md §4–5
 */
@Service
class DurationBackfillService {
    /** Результат починки одной песни. */
    data class Result(
        val songId: Long,
        val status: String,
        val durationMs: Long,
        val path: String,
    )

    /**
     * Починить длительность песен.
     *
     * @param songIds id песен (1..100 за вызов).
     * @param dryRun `true` — только показать, что будет записано (по умолчанию).
     * @return результат по каждой песне.
     */
    fun run(
        songIds: List<Long>,
        dryRun: Boolean = true,
    ): List<Result> =
        songIds.map { songId ->
            val song =
                Song.loadFromDbById(
                    id = songId,
                    database = WORKING_DATABASE,
                    sync = false,
                    storageService = KSS_APP,
                    storageApiClient = SAC_APP,
                )
            when {
                song == null -> Result(songId, "SKIPPED_NO_SONG", 0L, "")
                song.ms > 0L -> Result(songId, "SKIPPED_HAS_DURATION", song.ms, song.fileAbsolutePath)
                !File(song.fileAbsolutePath).exists() ->
                    Result(songId, "SKIPPED_NO_SOURCE", 0L, song.fileAbsolutePath)
                else -> {
                    val duration = AudioProbe.durationMs(song.fileAbsolutePath) ?: 0L
                    if (duration <= 0L) {
                        Result(songId, "SKIPPED_UNREADABLE", 0L, song.fileAbsolutePath)
                    } else {
                        if (!dryRun) {
                            // Запись в БД — тем же способом, что и в кортеже.
                            song.refreshDurationMsFromAudioFile(duration)
                        }
                        Result(
                            songId,
                            if (dryRun) "WOULD_WRITE" else "WRITTEN",
                            duration,
                            song.fileAbsolutePath,
                        )
                    }
                }
            }
        }
}
