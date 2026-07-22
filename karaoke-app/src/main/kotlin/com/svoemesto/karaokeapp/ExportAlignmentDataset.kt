package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.model.Markertype
import com.svoemesto.karaokeapp.model.Message
import com.svoemesto.karaokeapp.model.Settings
import com.svoemesto.karaokeapp.model.SseNotification
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.SNS
import com.svoemesto.karaokeapp.services.StorageApiClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.concurrent.thread

@Serializable
data class AlignmentSyllableDto(
    val label: String,
    val timeMs: Long,
)

@Serializable
data class AlignmentDatasetRow(
    val songId: Long,
    val voice: Int,
    val audioFile: String,
    val text: String,
    val syllables: List<AlignmentSyllableDto>,
    val durationMs: Long,
)

// Экспорт манифеста для дообучения forced-alignment модели (см. alignment-ml/) - тот же паттерн
// "фоновый поток + SSE-тост по завершении", что и customFunction()/autoAssignOriginalAll (Utils.kt),
// но ОТДЕЛЬНАЯ функция: customFunction() - живая фича поиска родителей, её нельзя затирать.
//
// Аудио НЕ копируется: манифест хранит абсолютный путь к уже существующему на диске FLAC
// (Settings.vocalsNameFlac) - обучение идёт на той же машине, копирование только удвоило бы место
// на диске без пользы. idStatus >= 3 - тот же порог "маркеры уже финальны/проверены", что и у
// player-readiness флагов (проект уже собран из разметки на этом этапе пайплайна).
fun exportAlignmentDataset(
    storageService: KaraokeStorageService,
    storageApiClient: StorageApiClient,
): String {
    thread {
        val json = Json { ignoreUnknownKeys = true }
        val ids = mutableListOf<Long>()
        try {
            val connection = WORKING_DATABASE.getConnection()
            if (connection != null) {
                val ps = connection.prepareStatement("SELECT id FROM tbl_settings WHERE id_status >= 3 ORDER BY id")
                val rs = ps.executeQuery()
                while (rs.next()) ids.add(rs.getLong("id"))
                rs.close()
                ps.close()
            }
        } catch (e: Exception) {
            println("Экспорт датасета для forced-alignment: ошибка выборки песен - ${e.message}")
        }

        println("Экспорт датасета для forced-alignment: найдено песен: ${ids.size}")

        val datasetDir = File(PATH_TO_ALIGNMENT_DATASET_FOLDER)
        datasetDir.mkdirs()
        val manifestFile = File(datasetDir, "manifest.jsonl")

        var songsScanned = 0
        var tracksExported = 0
        var tracksSkippedNoMarkers = 0
        var tracksSkippedNoAudio = 0

        // Прогресс - раз в ~5% (не реже, чем раз в 200 песен), помимо построчного println ниже (для
        // логов) - чтобы админ видел живой прогресс в UI на такой массовой операции, не только по
        // завершении. Тот же SNS.send(message), что и итоговая сводка - отдельного SSE-типа для
        // прогресс-бара здесь не заводим, это не полноценный KaraokeProcess (нет retry/persistence,
        // линейный однопроходный скан).
        val progressStepSongs = maxOf(1, minOf(200, ids.size / 20))

        manifestFile.bufferedWriter().use { writer ->
            ids.forEachIndexed { index, id ->
                try {
                    val settings =
                        Settings.loadFromDbById(
                            id = id,
                            database = WORKING_DATABASE,
                            storageService = storageService,
                            storageApiClient = storageApiClient,
                        )
                    if (settings == null) {
                        println("  [${index + 1}/${ids.size}] id=$id - пропущено (не найдено)")
                        return@forEachIndexed
                    }
                    songsScanned++

                    for (voice in 0 until settings.countVoices) {
                        val markers =
                            settings.sourceMarkersList
                                .getOrNull(voice)
                                ?.filter { it.markertype == Markertype.SYLLABLES.value }
                                ?.sortedBy { it.time }
                                ?: emptyList()
                        if (markers.isEmpty()) {
                            tracksSkippedNoMarkers++
                            continue
                        }
                        val audioFile = File(settings.vocalsNameFlac)
                        if (!audioFile.exists()) {
                            tracksSkippedNoAudio++
                            continue
                        }

                        val row =
                            AlignmentDatasetRow(
                                songId = id,
                                voice = voice,
                                audioFile = audioFile.absolutePath,
                                text = settings.getSourceText(voice),
                                syllables =
                                    markers.map {
                                        AlignmentSyllableDto(label = it.label, timeMs = (it.time * 1000).toLong())
                                    },
                                durationMs = (markers.last().time * 1000).toLong(),
                            )
                        writer.write(json.encodeToString(AlignmentDatasetRow.serializer(), row))
                        writer.newLine()
                        tracksExported++
                    }
                    println("  [${index + 1}/${ids.size}] ${settings.songName} (id=$id) - обработано")
                } catch (e: Exception) {
                    println("  [${index + 1}/${ids.size}] id=$id - ошибка: ${e.message}")
                }

                if ((index + 1) % progressStepSongs == 0 || index + 1 == ids.size) {
                    val percent = ((index + 1) * 100 / maxOf(1, ids.size))
                    SNS.send(
                        SseNotification.message(
                            Message(
                                type = "info",
                                head = "Экспорт датасета для forced-alignment",
                                body = "Прогресс: ${index + 1}/${ids.size} песен ($percent%), треков экспортировано: $tracksExported",
                            ),
                        ),
                    )
                }
            }
        }

        val summary =
            "Песен просканировано: $songsScanned, треков экспортировано: $tracksExported " +
                "(пропущено без разметки: $tracksSkippedNoMarkers, без аудио: $tracksSkippedNoAudio). " +
                "Манифест: ${manifestFile.absolutePath}"
        println("Экспорт датасета для forced-alignment: завершено. $summary")

        SNS.send(
            SseNotification.message(
                Message(
                    type = "info",
                    head = "Экспорт датасета для forced-alignment",
                    body = summary,
                ),
            ),
        )
    }
    return "Экспорт датасета для forced-alignment запущен в фоне"
}
