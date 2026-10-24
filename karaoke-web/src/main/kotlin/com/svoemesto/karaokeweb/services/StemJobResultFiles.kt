package com.svoemesto.karaokeweb.services

import com.svoemesto.karaokeapp.model.StemJob
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.File

/**
 * Раскладка приватных файлов премиум-задания «Создать минусовку» на стороне `karaoke-web`.
 *
 * Спека 561 (OpenProject #361, аудит BR-258): `karaoke-web` **не умеет** читать MinIO с
 * учётными данными (`WebKaraokeStorageServiceImpl` — заглушка, все методы бросают
 * `UnsupportedOperationException`), а presigned-URL через nginx path-proxy нереализуем
 * (SigV4 ломается переписыванием пути). Поэтому приватные объекты хранятся в отдельном
 * бакете `stemjobs` без публичной политики, а `karaoke-app` (единственный компонент с
 * учётными данными) передаёт их сюда по внутреннему каналу `X-Internal-Secret`
 * (`InternalStemJobController`), и владелец задания получает файл уже отсюда.
 *
 * Каталог результатов **не публикуется** nginx'ом — единственный путь к файлу —
 * авторизованный `PublicStemJobController.download` (проверка владельца, статуса и TTL).
 *
 * @see specs/561-impl-stem-bucket/data-model.md
 * @see specs/561-impl-stem-bucket/contracts/stemjob-private-files-contract.md
 */
@Component
class StemJobResultFiles(
    @Value("\${stemjobs.temp-dir:/tmp/stemjobs}") private val tempDir: String,
    @Value("\${stemjobs.result-dir:}") private val resultDir: String,
) {
    /** Корень каталога результатов. Пустое значение `result-dir` → `${temp-dir}/results`. */
    val root: File
        get() = File(if (resultDir.isBlank()) File(tempDir, "results").path else resultDir)

    /** Каталог файлов одного задания. */
    fun jobDir(jobId: Long): File = File(root, jobId.toString())

    /** Файл результата по имени стема (`vocals`, `original`, …). */
    fun fileFor(
        jobId: Long,
        stem: String,
        originalExt: String,
    ): File = File(jobDir(jobId), StemJob.resultFileName(stem, originalExt))

    /**
     * Файл результата по готовому имени (`vocals.mp3`) — используется приёмом объекта по
     * внутреннему каналу: имя уже проверено allow-list'ом (`StemJob.isValidResultFileName`).
     */
    fun fileByName(
        jobId: Long,
        fileName: String,
    ): File = File(jobDir(jobId), fileName)

    /** Удаляет все локальные копии задания (каталог результатов + сырой файл загрузки). */
    fun purge(jobId: Long): Int {
        var removed = 0
        val dir = jobDir(jobId)
        if (dir.exists() && dir.deleteRecursively()) removed++
        // Сырой файл загрузки лежит рядом с каталогом результатов: <temp-dir>/<jobId>.<ext>
        StemJob.ALLOWED_EXTENSIONS.forEach { ext ->
            if (File(tempDir, "$jobId.$ext").delete()) removed++
        }
        return removed
    }
}
