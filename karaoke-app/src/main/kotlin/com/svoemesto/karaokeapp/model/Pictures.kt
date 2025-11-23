package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.WORKING_DATABASE
import com.svoemesto.karaokeapp.model.KaraokeDbTable.Companion.getListHashes
import com.svoemesto.karaokeapp.resizeBufferedImage
import com.svoemesto.karaokeapp.runCommand
import com.svoemesto.karaokeapp.services.KSS_APP
import com.svoemesto.karaokeapp.services.KaraokeStorage
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.Serializable
import java.util.*
import javax.imageio.ImageIO

class Pictures(
    override val database: KaraokeConnection = WORKING_DATABASE,
    override val storageService: KaraokeStorageService = KSS_APP
) : Serializable, Comparable<Pictures>, KaraokeDbTable, KaraokeStorage {



    @KaraokeDbTableField(name = "id", isId = true)
    override var id: Long = 0

    @KaraokeDbTableField(name = "picture_name")
    var name: String = "Picture name"

    @KaraokeDbTableField(name = "picture_full", useInList = false)
    var full: String = ""
        set(value) {
            field = value
            try {
                val pictureBites = Base64.getDecoder().decode(value)
                val bi = ImageIO.read(ByteArrayInputStream(pictureBites))
                val previewBi = if (bi.width > 400) resizeBufferedImage(bi, newW = 125, newH = 50) else resizeBufferedImage(bi, newW = 50, newH = 50)
                val iosPreview = ByteArrayOutputStream()
                ImageIO.write(previewBi, "png", iosPreview)
                Base64.getEncoder().encodeToString(iosPreview.toByteArray())
//                if (this.preview != preview) this.preview = preview
            } catch (_: Exception) {

            }
        }

//    @KaraokeDbTableField(name = "picture_preview")
//    var preview: String = ""

    val preview: String get() {
        return if (storageFilePreviewExists()) {
            println("Получаем preview для файла '$name' из хранилища.")
            storageDownloadFilePreview()
        } else {
            if (storageFileExists()) {
                println("Отсутствует preview для файла '$name', создаём.")
                val fullFileByteArray = storageDownloadFile().use { it.readBytes() }
                val bi = ImageIO.read(ByteArrayInputStream(fullFileByteArray))
                val previewBi = if (bi.width > 400) resizeBufferedImage(bi, newW = 125, newH = 50) else resizeBufferedImage(bi, newW = 50, newH = 50)
                val iosPreview = ByteArrayOutputStream()
                ImageIO.write(previewBi, "png", iosPreview)
                val previewInputStream = ByteArrayInputStream(iosPreview.toByteArray())
                storageUploadFilePreview(previewInputStream, previewInputStream.available().toLong())
                previewInputStream
            } else {
                null
            }
        }?.let { fileInputStream ->
            Base64.getEncoder().encodeToString(fileInputStream.use { it.readBytes() })
        } ?: ""
    }

    val author: String get() {
        val arr = name.split(" - ")
        return if (arr.size >= 3) arr[0] else name
    }

    val year: String get() {
        val arr = name.split(" - ")
        return if (arr.size >= 3) arr[1] else ""
    }

    val album: String get() {
        val arr = name.split(" - ")
        return if (arr.size >= 3) arr.filterIndexed { index, _ -> index >= 2 }.joinToString(" - ") else ""
    }

    val isAuthorPicture: Boolean get() = author.isNotBlank() && year.isBlank() && album.isBlank()
    val isAlbumPicture: Boolean get() = author.isNotBlank() && year.isNotBlank() && album.isNotBlank()

    override val storageBucketName: String get() = "karaoke"

    override val storageFileName: String get() = when {
        isAuthorPicture -> "$author/$name.author.png"
        isAlbumPicture -> "$author/$year - $album/$name.album.png"
        else -> "$name.png"
    }
    override val storageFileNamePreview: String get() = when {
        isAuthorPicture -> "$author/$name.preview.author.png"
        isAlbumPicture -> "$author/$year - $album/$name.preview.album.png"
        else -> "$name.preview.png"
    }
    override var storageBucketIsPublic: Boolean
        get() = storageService.isBucketPublic(storageBucketName)
        set(value) {
            if (value) {
                storageService.setBucketPublic(storageBucketName)
            } else {
                storageService.setBucketPrivate(storageBucketName)
            }
        }

    val pathToFolder: String get() {
        return if (isAlbumPicture) {
            // Ищем первую песню автора, года и альбома
            val args = mapOf("author" to author, "song_year" to year, "album" to album, "limit" to "1")
            Settings.loadListFromDb(args = args, database = WORKING_DATABASE, storageService = storageService, withoutMarkersAndText = true).firstOrNull()?.rootFolder ?: ""
        } else if (isAuthorPicture) {
            val args = mapOf("author" to author, "limit" to "1")
            Settings.loadListFromDb(args = args, database = WORKING_DATABASE, storageService = storageService, withoutMarkersAndText = true).firstOrNull()?.let { sett ->
                File(sett.rootFolder).parent
            }?: ""
        } else ""
    }

    val fileName: String get() = if (isAuthorPicture) "LogoAuthor.png" else if (isAlbumPicture) "LogoAlbum.png" else ""

    override fun compareTo(other: Pictures): Int {
        return id.compareTo(other.id)
    }

    override fun getTableName(): String = TABLE_NAME

    override fun toDTO(): PicturesDTO {
        return PicturesDTO(
                id = id,
                name = name,
                preview = preview,
                full = full,
                author = author,
                year = year,
                album = album,
                isAuthorPicture = isAuthorPicture,
                isAlbumPicture = isAlbumPicture,
                pathToFolder = pathToFolder,
                fileName = fileName
        )
    }

    fun saveToDisk() {
        try {
            val pictureBites = Base64.getDecoder().decode(full)
            val bi = ImageIO.read(ByteArrayInputStream(pictureBites))
            val fName = "/sm-karaoke/system/pictures/$name.png"
            val file = File(fName)
            ImageIO.write(bi, "png", file)
            runCommand(listOf("chmod", "666", fName))
        } catch (e: Exception) {
            println(e.message)
        }
    }

    init {
        storageBucketIsPublic = true
    }

    companion object {

        const val TABLE_NAME = "tbl_pictures"

        fun listHashes(database: KaraokeConnection, whereText: String = ""): List<RecordHash>? = getListHashes(tableName = TABLE_NAME, database = database, whereText = whereText)

        private fun getWhereList(whereArgs: Map<String, String>): List<String> {
            val where: MutableList<String> = mutableListOf()
            if (whereArgs.containsKey("id")) where += "id=${whereArgs["id"]}"
            if (whereArgs.containsKey("picture_name")) where += "LOWER(picture_name) LIKE '%${whereArgs["picture_name"]?.lowercase()}%'"
            if (whereArgs.containsKey("name")) where += "LOWER(picture_name) = '${whereArgs["name"]?.lowercase()}'"
            return where
        }

        fun loadList(whereArgs: Map<String, String>,
                     limit: Int = 0,
                     offset: Int = 0,
                     database: KaraokeConnection,
                     storageService: KaraokeStorageService,
                     ignoreUseInList: Boolean
        ): List<Pictures> {
            return KaraokeDbTable.loadList(
                clazz = Pictures::class,
                tableName = TABLE_NAME,
                whereList = getWhereList(whereArgs),
                limit = limit,
                offset = offset,
                database = database,
                storageService = storageService,
                ignoreUseInList = ignoreUseInList
            ).map { it as Pictures }
        }

        fun delete(id: Long, database: KaraokeConnection): Boolean {
            return KaraokeDbTable.delete(
                tableName = TABLE_NAME,
                id = id,
                database = database
            )
        }

        fun createNewPicture(newPicture: Pictures, database: KaraokeConnection): Pictures? {
            val newPictureInDb = KaraokeDbTable.createDbInstance(
                entity = newPicture,
                database = database
            ) as? Pictures?
            newPictureInDb?.let {
                return it
            }
            return null
        }

        fun getPictureById(id: Long, database: KaraokeConnection, storageService: KaraokeStorageService): Pictures? {
            return KaraokeDbTable.loadById(
                clazz = Pictures::class,
                tableName = TABLE_NAME,
                id = id,
                database = database,
                storageService = storageService,
            ) as? Pictures?
        }

        fun getPictureByName(name: String, database: KaraokeConnection, storageService: KaraokeStorageService): Pictures? {

            return loadList(
                whereArgs = mapOf(Pair("name", name)),
                database = database,
                storageService = storageService,
                ignoreUseInList = true
            ).firstOrNull()

        }

    }
}