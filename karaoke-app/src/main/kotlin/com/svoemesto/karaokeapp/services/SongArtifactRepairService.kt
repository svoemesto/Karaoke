package com.svoemesto.karaokeapp.services

import com.svoemesto.karaokeapp.KaraokeProcess
import com.svoemesto.karaokeapp.KaraokeProcessTypes
import com.svoemesto.karaokeapp.WORKING_DATABASE
import com.svoemesto.karaokeapp.model.Song
import com.svoemesto.karaokeapp.tuple.ArtifactIntegrity
import com.svoemesto.karaokeapp.tuple.IntegrityFinding
import com.svoemesto.karaokeapp.services.KSS_APP
import com.svoemesto.karaokeapp.services.SAC_APP
import org.springframework.stereotype.Service
import java.io.File

/** Результат постановки ремонта по одной песне. */
data class RepairResult(
    val songId: Long,
    val demucsChainId: Long,
    val keyBpmChainId: Long?,
)

/**
 * 674 (FR-013): явный ремонт артефактов песни.
 *
 * `dryRun` (по умолчанию) только показывает находки; постановка кортежей — лишь при явном
 * `dryRun=false`. Ничего не правит в БД, кроме постановки заданий.
 *
 * @see specs/674-kortege-execution/contracts/admin-api.md §5
 */
@Service
class SongArtifactRepairService {
    fun findings(songIds: List<Long>): List<IntegrityFinding> = songIds.flatMap { ArtifactIntegrity.verifySong(it) }

    fun enqueue(
        songIds: List<Long>,
        includeKeyBpm: Boolean,
    ): List<RepairResult> {
        val results = mutableListOf<RepairResult>()
        songIds.forEach { songId ->
            val song =
                Song.loadFromDbById(
                    id = songId,
                    database = WORKING_DATABASE,
                    sync = false,
                    storageService = KSS_APP,
                    storageApiClient = SAC_APP,
                ) ?: return@forEach
            val hasFiveStems = listOf(song.drumsNameFlac, song.bassNameFlac, song.otherNameFlac).all { File(it).exists() }
            val demucsType = if (hasFiveStems) KaraokeProcessTypes.DEMUCS5 else KaraokeProcessTypes.DEMUCS2
            val demucsChainId =
                KaraokeProcess.createProcess(
                    song = song,
                    action = demucsType,
                    doWait = true,
                    prior = 0,
                    threadId = KaraokeProcess.THREAD_LANE_HEALTH_REPORT,
                )
            val keyBpmChainId =
                if (includeKeyBpm) {
                    KaraokeProcess
                        .createProcess(
                            song = song,
                            action = KaraokeProcessTypes.KEY_BPM_FROM_FILE,
                            doWait = true,
                            prior = 0,
                            threadId = KaraokeProcess.THREAD_LANE_HEALTH_REPORT,
                        ).takeIf { it > 0 }
                } else {
                    null
                }
            if (demucsChainId > 0) {
                results += RepairResult(songId, demucsChainId, keyBpmChainId)
            }
        }
        return results
    }
}
