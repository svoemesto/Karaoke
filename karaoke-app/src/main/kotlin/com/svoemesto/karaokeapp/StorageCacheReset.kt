package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.model.Song

/**
 * Чистые вычисления имён файлов песни в хранилище — единый источник формул.
 *
 * До этого формулы дублировались в `HealthReport.getHealthReportList` (по одной
 * на каждый `KaraokeFileType`). Спека `446-storage-cache-reset-button` (FR-002)
 * требует переиспользовать их в сбросе кеша, поэтому формулы вынесены сюда.
 *
 * Формулы ДОЛЖНЫ совпадать с теми, что использует `HealthReport` — иначе сброс
 * кеша не попадёт в реальные ключи. Это покрыто `StorageCacheResetTest`.
 *
 * Функции на примитивах (`author/year/album/fileName`) тестируемы без `Song`
 * (конструктор `Song` тянет статическую инициализацию окружения).
 *
 * @see specs/446-storage-cache-reset-button/spec.md
 */
object StorageCacheReset {
    /**
     * Имя файла в MinIO для конкретного типа (`song.storageFileName` + suffix/ext,
     * либо специальные формы для картинок альбома/автора).
     */
    fun storageFileNameFor(
        author: String,
        year: String,
        album: String,
        songFileName: String,
        karaokeFileType: KaraokeFileType,
    ): String =
        when (karaokeFileType) {
            KaraokeFileType.PICTURE_ALBUM,
            KaraokeFileType.PICTURE_ALBUM_PREVIEW,
            ->
                "$author/$year - $album/" +
                    "$author - $year - $album" +
                    "${karaokeFileType.suffix}.${karaokeFileType.extention}"
            KaraokeFileType.PICTURE_AUTHOR,
            KaraokeFileType.PICTURE_AUTHOR_PREVIEW,
            ->
                "$author/$author" +
                    "${karaokeFileType.suffix}.${karaokeFileType.extention}"
            else ->
                "$author/$year - $album/$songFileName" +
                    "${karaokeFileType.suffix}.${karaokeFileType.extention}"
        }

    /**
     * Все имена файлов песни, которые живут в MinIO (LOCAL_STORAGE и/или
     * REMOTE_STORAGE). Без дублей (картинка автора общая для песен автора).
     * Только `KaraokeFileTypeFor.SONG`: `PROJECT_*`/`VIDEO_*` — файловая система.
     */
    fun storageFileNamesForSong(
        author: String,
        year: String,
        album: String,
        songFileName: String,
    ): List<String> {
        val result = LinkedHashSet<String>()
        KaraokeFileType.entries.forEach { type ->
            if (type.karaokeFileTypeFor != KaraokeFileTypeFor.SONG) return@forEach
            if (!type.willBeInLocalStorage && !type.willBeInRemoteStorage) return@forEach
            result.add(storageFileNameFor(author, year, album, songFileName, type))
        }
        return result.toList()
    }

    /** Имя файла для [Song]-сущности (делегат на примитивы). */
    fun storageFileNameFor(
        song: Song,
        karaokeFileType: KaraokeFileType,
    ): String = storageFileNameFor(song.author, song.year.toString(), song.album, song.fileName, karaokeFileType)

    /** Все storage-имена [Song]-сущности (делегат на примитивы). */
    fun storageFileNamesForSong(song: Song): List<String> =
        storageFileNamesForSong(song.author, song.year.toString(), song.album, song.fileName)
}
