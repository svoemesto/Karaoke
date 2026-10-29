package com.svoemesto.karaokeapp.services

import com.svoemesto.karaokeapp.KaraokeProcessTypes
import com.svoemesto.karaokeapp.WORKING_DATABASE
import com.svoemesto.karaokeapp.model.Song
import com.svoemesto.karaokeapp.tuple.ArtifactIntegrity
import com.svoemesto.karaokeapp.tuple.AudioProbe
import com.svoemesto.karaokeapp.tuple.KeyBpmSidecar
import org.springframework.stereotype.Service
import java.io.File

/** Результат one-off backfill отпечатков для легаси `[key].json`. */
data class SidecarBackfillResult(
    val songId: Long,
    val status: String,
    val keyJson: String,
)

/**
 * 674 (FR-012/FR-013): разовый backfill `<file> [key].json.meta` для легаси-песен.
 *
 * `dryRun=true` по умолчанию. Отпечаток пишется только если стемы песни прошли проверку
 * длительности — иначе запись не создаётся и статус `SKIPPED_STEMS`.
 *
 * @see specs/674-kortege-execution/contracts/artifact-verification.md §5
 */
@Service
class SidecarBackfillService {
    fun run(
        songIds: List<Long>,
        dryRun: Boolean = true,
    ): List<SidecarBackfillResult> =
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
                song == null -> SidecarBackfillResult(songId, "SKIPPED_NO_SONG", "")
                !File(song.pathToFileKeyBpmFinder).exists() ->
                    SidecarBackfillResult(songId, "SKIPPED_NO_KEY", song.pathToFileKeyBpmFinder)
                KeyBpmSidecar.read(song.pathToFileKeyBpmFinder) != null ->
                    SidecarBackfillResult(songId, "SKIPPED_HAS_SIDECAR", song.pathToFileKeyBpmFinder)
                !stemsVerified(song) -> SidecarBackfillResult(songId, "SKIPPED_STEMS", song.pathToFileKeyBpmFinder)
                else -> {
                    val duration = AudioProbe.durationMs(song.fileAbsolutePath) ?: song.refreshDurationMsFromAudioFile()
                    if (duration <= 0L) {
                        SidecarBackfillResult(songId, "SKIPPED_SOURCE", song.pathToFileKeyBpmFinder)
                    } else {
                        val sidecar = KeyBpmSidecar.forSource(song.fileAbsolutePath, duration, song.id)
                        if (!dryRun) KeyBpmSidecar.write(song.pathToFileKeyBpmFinder, sidecar)
                        SidecarBackfillResult(
                            songId,
                            if (dryRun) "WOULD_WRITE" else "WRITTEN",
                            song.pathToFileKeyBpmFinder,
                        )
                    }
                }
            }
        }

    private fun stemsVerified(song: Song): Boolean {
        val sourceDuration = AudioProbe.durationMs(song.fileAbsolutePath) ?: return false
        val types =
            if (File(song.drumsNameFlac).exists()) {
                listOf(KaraokeProcessTypes.DEMUCS2, KaraokeProcessTypes.DEMUCS5)
            } else {
                listOf(KaraokeProcessTypes.DEMUCS2)
            }
        return types.any { ArtifactIntegrity.verifyDemucsStems(song, it, sourceDuration).isEmpty() }
    }
}
