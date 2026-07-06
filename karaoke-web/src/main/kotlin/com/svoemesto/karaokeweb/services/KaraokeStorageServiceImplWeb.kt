package com.svoemesto.karaokeweb.services

import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageFileInfo
import io.minio.StatObjectResponse
import org.springframework.stereotype.Service
import java.io.File
import java.io.InputStream

/**
 * Заглушка бина [KaraokeStorageService] для karaoke-web.
 *
 * karaoke-web НИКОГДА не обращается к MinIO напрямую через MinioClient (см. корневой CLAUDE.md,
 * разделы «MinIO на отдельном сервере», «minio nginx proxy»): на проде MinIO вынесен на отдельный
 * хост (89.125.103.63), контейнера `karaoke-storage` в docker-сети нет, а подписанный S3 из
 * контейнера всё равно невозможен (MTU black-hole рвёт прямое соединение, а nginx path-proxy ломает
 * SigV4-подпись). Реальный доступ к MinIO идёт исключительно через хостовый nginx-прокси `minio-proxy`
 * неподписанными GET/HEAD — см. `fetchFromMinIO`/`existsInMinIO` в `PublicApiController`/
 * `PublicPlayerController`.
 *
 * Сам бин обязателен только для DI: на него держат конструкторную зависимость несколько web-классов,
 * но по реально достижимым публичным путям его методы НЕ вызываются — он прокидывается лишь как
 * «плумбинг»-параметр в companion-методы моделей karaoke-app (`Settings.loadFromDbById`,
 * `SiteUser.getSiteUserById`, `Zakroma.getZakroma`), которые по этим путям в MinIO не ходят. Поэтому
 * все методы бросают [UnsupportedOperationException]: в нормальной работе это никогда не срабатывает,
 * а случайный будущий вызов упадёт громко и понятно вместо тихого обращения к недоступному MinIO.
 */
@Service
class KaraokeStorageServiceImpl : KaraokeStorageService {

    private fun nope(): Nothing = throw UnsupportedOperationException(
        "karaoke-web не обращается к MinIO напрямую — только через minio-proxy " +
            "(см. fetchFromMinIO/existsInMinIO в PublicApiController/PublicPlayerController)"
    )

    override fun uploadFile(bucketName: String, fileName: String, file: InputStream, size: Long?) = nope()
    override fun uploadFile(bucketName: String, fileName: String, pathToFileOnDisk: String) = nope()
    override fun getFileUrl(bucketName: String, fileName: String): String = nope()
    override fun downloadFile(bucketName: String, fileName: String): InputStream = nope()
    override fun downloadFile(bucketName: String, fileName: String, pathToFileOnDisk: String): File = nope()
    override fun deleteFile(bucketName: String, fileName: String) = nope()
    override fun getPresignedUrl(bucketName: String, fileName: String, expiry: Int): String = nope()
    override fun bucketExists(bucketName: String): Boolean = nope()
    override fun fileExists(bucketName: String, fileName: String): Boolean = nope()
    override fun listFiles(bucketName: String): List<String> = nope()
    override fun setBucketPublic(bucketName: String) = nope()
    override fun setBucketPrivate(bucketName: String) = nope()
    override fun isBucketPublic(bucketName: String): Boolean = nope()
    override fun createBucketIfNotExists(bucketName: String) = nope()
    override fun deleteAllEmptyBuckets() = nope()
    override fun getFileStat(bucketName: String, fileName: String): StatObjectResponse? = nope()
    override fun getFileInfo(bucketName: String, fileName: String): StorageFileInfo = nope()
    override fun fileIsActual(bucketName: String, fileName: String, pathToFileOnDisk: String): Boolean = nope()
    override fun fileIsActual(bucketName: String, fileName: String, storageFileInfo: StorageFileInfo): Boolean = nope()
    override fun listFilesInfo(bucketName: String): List<StorageFileInfo> = nope()
}
