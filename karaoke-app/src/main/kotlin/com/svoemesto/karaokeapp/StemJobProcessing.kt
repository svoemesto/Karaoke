package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.model.StemJob
import com.svoemesto.karaokeapp.model.StemJobMode
import com.svoemesto.karaokeapp.model.StemJobStatus
import com.svoemesto.karaokeapp.services.KSS_APP
import com.svoemesto.karaokeapp.services.SAC_APP
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.sql.Timestamp

/**
 * Билдер OS-подпроцесс-пайплайна для demucs премиум-задания (StemJob, tbl_stem_jobs) — по образцу
 * Settings.argsDemucs2/5 (model/Settings.kt), но для произвольного файла пользователя, а не для
 * Settings/песни (settingsId=0, свой thread-лейн THREAD_LANE_STEM_JOBS). Своя temp-папка
 * (PATH_TO_TEMP_STEMJOB_FOLDER/{jobId}) — общая с песенным пайплайном привела бы к коллизии файлов,
 * т.к. оба лейна могут работать параллельно. Сырой файл уже скачан туда поллером
 * (StemJobPollScheduler) ДО постановки в очередь, под именем upload.<ext>.
 *
 * Первый шаг — ffmpeg-транскод произвольного входного формата в flac: demucs-докер ожидает именно
 * flac, а вход здесь (в отличие от песенного пайплайна, где fileAbsolutePath уже flac) может быть
 * любым поддерживаемым ffmpeg форматом — ffmpeg определяет кодек по содержимому файла, не по
 * расширению, так что переименование не подойдёт.
 *
 * ВАЖНО (см. KaraokeProcessThread.run() / KaraokeProcess.getProcessesToStart): движок очереди
 * НЕ прерывает цепочку шагов при ошибке одного из них — SQL-выборка следующего шага фильтрует только
 * по process_status='WAITING'; упавший (ERROR) шаг просто выпадает из пула, и следующий шаг всё
 * равно стартует (и почти наверняка тоже упадёт, если ему нужен результат предыдущего). Поэтому
 * финальный шаг (executeFinalizeStemJob) обязан сам проверять, что все ожидаемые выходные файлы
 * реально существуют, а не считать обработку успешной по факту своего запуска.
 */
fun argsStemJobDemucs(
    job: StemJob,
    device: String = "cuda",
): Pair<List<List<String>>, Map<String, String>> {
    val tempFolder = "$PATH_TO_TEMP_STEMJOB_FOLDER/${job.id}"
    val uploadPath = "$tempFolder/upload.${job.originalExt}".rightFileName()
    val flacPath = "$tempFolder/file.flac".rightFileName()
    val gpuFlags = if (device == "cuda") listOf("--gpus", "all") else emptyList()
    val processType = if (job.mode == StemJobMode.DEMUCS5) KaraokeProcessTypes.STEM_JOB_DEMUCS5 else KaraokeProcessTypes.STEM_JOB_DEMUCS2
    val cpuFlags = dockerCpusFlag(cpuLimitPercentForType(processType))
    val demucsScript = if (job.mode == StemJobMode.DEMUCS5) "./demucs5" else "./demucs2"

    val steps =
        mutableListOf(
            listOf("mkdir", "-p", tempFolder),
            listOf("chmod", "777", tempFolder),
            listOf("ffmpeg", "-i", uploadPath, flacPath, "-y"),
            listOf("docker", "run", "--rm", "-i", "--name=stemjob-${job.id}") + gpuFlags + cpuFlags +
                listOf(
                    "-v",
                    "$tempFolder:/data/input",
                    "-v",
                    "$tempFolder:/data/output",
                    "svoemestodev/demucs:latest",
                    "''$demucsScript -file $flacPath -recode flac -device $device''",
                ),
        )
    // Перекодирование каждого flac-стема в mp3 (320k) — по образцу существующих FF_MP3_* шагов
    // (model/Settings.kt) — экономит место в MinIO относительно flac (см. решение по режиму
    // хранения стемов в плане фичи).
    StemJobMode.stemNames(job.mode).forEach { stem ->
        steps.add(
            listOf(
                "ffmpeg",
                "-i",
                "$tempFolder/file-$stem.flac".rightFileName(),
                "-ab",
                "320k",
                "-map_metadata",
                "0",
                "-id3v2_version",
                "3",
                "$tempFolder/$stem.mp3".rightFileName(),
                "-y",
            ),
        )
    }
    steps.add(listOf("runFunctionWithArgs", "finalizeStemJob", "jobId=${job.id}"))

    return Pair(steps, mapOf("DOCKER_API_VERSION" to "1.53"))
}

/**
 * Финальный функциональный шаг пайплайна (см. argsStemJobDemucs) — по образцу
 * executeGetKeyBpmFromFile (Utils.kt): загружает оригинал + готовые mp3-стемы в MinIO (тем же
 * механизмом, каким админка сегодня грузит в удалённое хранилище — SAC_APP), помечает задание
 * DONE + expiresAt=now+24h в tbl_stem_jobs (SERVER БД), сообщает karaoke-web, что временный файл
 * можно удалить, и чистит локальный temp. Возвращает false (⇒ ERROR у KaraokeProcess-шага), если
 * ожидаемые файлы не найдены — см. предупреждение в argsStemJobDemucs про негарантированную
 * последовательность шагов.
 */
fun executeFinalizeStemJob(params: Map<String, String>): Boolean {
    val jobId = params["jobId"]?.toLongOrNull() ?: return false
    // Финализация всегда идёт против прод-БД (SERVER) — реальные пользователи там; для локальной
    // отладки временно указать Connection.local() (тот же инвариант, что и SponsrSyncScheduler).
    val database = Connection.remote()
    try {
        val job =
            StemJob.getById(jobId, database = database, storageService = KSS_APP, storageApiClient = SAC_APP)
                ?: return false

        val tempFolder = File("$PATH_TO_TEMP_STEMJOB_FOLDER/${job.id}")
        val stemNames = StemJobMode.stemNames(job.mode)
        val uploadFile = File(tempFolder, "upload.${job.originalExt}")
        val stemFiles = stemNames.associateWith { File(tempFolder, "$it.mp3") }

        if (!uploadFile.exists() || stemFiles.values.any { !it.exists() || it.length() == 0L }) {
            job.status = StemJobStatus.ERROR
            job.errorMessage = "Обработка не создала все ожидаемые файлы — вероятно, ошибка на одном из шагов demucs/ffmpeg"
            job.finishedAt = Timestamp(System.currentTimeMillis())
            job.save()
            tempFolder.deleteRecursively()
            return false
        }

        val bucket = "karaoke"
        try {
            SAC_APP.uploadFile(bucket, "stemjobs/${job.id}/original.${job.originalExt}", uploadFile.absolutePath)
            stemFiles.forEach { (stem, file) -> SAC_APP.uploadFile(bucket, "stemjobs/${job.id}/$stem.mp3", file.absolutePath) }
        } catch (e: Exception) {
            job.status = StemJobStatus.ERROR
            job.errorMessage = "Ошибка загрузки в хранилище: ${e.message}"
            job.finishedAt = Timestamp(System.currentTimeMillis())
            job.save()
            tempFolder.deleteRecursively()
            return false
        }

        job.status = StemJobStatus.DONE
        job.finishedAt = Timestamp(System.currentTimeMillis())
        job.expiresAt = Timestamp(System.currentTimeMillis() + StemJob.RETENTION_HOURS * 3600_000L)
        job.save()

        tempFolder.deleteRecursively()
        ackStemJobRawFileConsumed(job.id)
        return true
    } finally {
        try {
            database.getConnection()?.close()
        } catch (_: Exception) {
        }
    }
}

// Сообщает karaoke-web, что сырой временный файл задания забран и обработан — можно удалить со
// своего диска (см. InternalStemJobController в karaoke-web). Best-effort: если запрос не прошёл,
// временный файл всё равно будет зачищен на стороне karaoke-web по safety-net TTL-очистке.
private fun ackStemJobRawFileConsumed(jobId: Long) {
    val baseUrl = Karaoke.stemJobsWebInternalUrl.trim().trimEnd('/')
    if (baseUrl.isBlank()) return
    try {
        val connection = URL("$baseUrl/api/internal/stemjobs/$jobId/ack").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("X-Internal-Secret", Karaoke.stemJobsInternalSecret)
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        connection.doOutput = false
        connection.responseCode // инициирует запрос
        connection.disconnect()
    } catch (e: Exception) {
        println("[ackStemJobRawFileConsumed] jobId=$jobId: ${e.message}")
    }
}
