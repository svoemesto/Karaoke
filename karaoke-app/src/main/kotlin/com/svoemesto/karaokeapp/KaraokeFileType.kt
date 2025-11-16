package com.svoemesto.karaokeapp

enum class KaraokeFileType(
    val description: String,
    val karaokeFileTypeFor: KaraokeFileTypeFor,
    val karaokeFileTypeKind: KaraokeFileTypeKind,
    val locations: List<KaraokeFileTypeLocations>,
    val symlinks: List<KaraokeFileSymlink> = emptyList(),
    val extention: String,
    val suffix: String
) {

    // MAIN

    AUDIO_SONG (
        description = "Исходный аудио файл",
        karaokeFileTypeFor = KaraokeFileTypeFor.SONG,
        karaokeFileTypeKind = KaraokeFileTypeKind.MAIN,
        locations = listOf(KaraokeFileTypeLocations.LOCAL_FILESYSTEM),
        extention = "flac",
        suffix = ".song",
    ),
    MP3_SONG (
        description = "Исходный аудио файл в формате mp3",
        karaokeFileTypeFor = KaraokeFileTypeFor.SONG,
        karaokeFileTypeKind = KaraokeFileTypeKind.MAIN,
        locations = listOf(KaraokeFileTypeLocations.LOCAL_FILESYSTEM),
        extention = "flac",
        suffix = ".song",
    ),
    AUDIO_ACCOMPANIMENT (
        description = "Минусовка",
        karaokeFileTypeFor = KaraokeFileTypeFor.SONG,
        karaokeFileTypeKind = KaraokeFileTypeKind.MAIN,
        locations = listOf(KaraokeFileTypeLocations.LOCAL_FILESYSTEM),
        symlinks = listOf(KaraokeFileSymlink(folder = "symlink_sponsr")),
        extention = "flac",
        suffix = ".accompaniment",
    ),
    MP3_ACCOMPANIMENT (
        description = "Минусовка в формате mp3",
        karaokeFileTypeFor = KaraokeFileTypeFor.SONG,
        karaokeFileTypeKind = KaraokeFileTypeKind.MAIN,
        locations = listOf(KaraokeFileTypeLocations.LOCAL_FILESYSTEM),
        extention = "flac",
        suffix = ".accompaniment",
    ),
    AUDIO_VOICE (
        description = "Чистый голос",
        karaokeFileTypeFor = KaraokeFileTypeFor.SONG,
        karaokeFileTypeKind = KaraokeFileTypeKind.MAIN,
        locations = listOf(KaraokeFileTypeLocations.LOCAL_FILESYSTEM),
        extention = "flac",
        suffix = ".vocals",
    ),
    AUDIO_BASS (
        description = "Бас",
        karaokeFileTypeFor = KaraokeFileTypeFor.SONG,
        karaokeFileTypeKind = KaraokeFileTypeKind.MAIN,
        locations = listOf(KaraokeFileTypeLocations.LOCAL_FILESYSTEM),
        extention = "flac",
        suffix = "bass",
    ),
    AUDIO_DRUMS (
        description = "Ударные",
        karaokeFileTypeFor = KaraokeFileTypeFor.SONG,
        karaokeFileTypeKind = KaraokeFileTypeKind.MAIN,
        locations = listOf(KaraokeFileTypeLocations.LOCAL_FILESYSTEM),
        extention = "flac",
        suffix = ".drums",
    ),
    AUDIO_OTHER (
        description = "Мелодия без баса и ударных",
        karaokeFileTypeFor = KaraokeFileTypeFor.SONG,
        karaokeFileTypeKind = KaraokeFileTypeKind.MAIN,
        locations = listOf(KaraokeFileTypeLocations.LOCAL_FILESYSTEM),
        extention = "flac",
        suffix = ".other",
    ),
    PICTURE_ALBUM (
        description = "Картинка альбома",
        karaokeFileTypeFor = KaraokeFileTypeFor.SONG,
        karaokeFileTypeKind = KaraokeFileTypeKind.MAIN,
        locations = listOf(
            KaraokeFileTypeLocations.LOCAL_FILESYSTEM,
            KaraokeFileTypeLocations.LOCAL_STORAGE
        ),
        extention = "png",
        suffix = ".album",
    ),
    PICTURE_ALBUM_PREVIEW (
        description = "Картинка альбома (preview)",
        karaokeFileTypeFor = KaraokeFileTypeFor.SONG,
        karaokeFileTypeKind = KaraokeFileTypeKind.MAIN,
        locations = listOf(
            KaraokeFileTypeLocations.LOCAL_FILESYSTEM,
            KaraokeFileTypeLocations.LOCAL_STORAGE
        ),
        extention = "png",
        suffix = ".preview.album",
    ),
    PICTURE_AUTHOR (
        description = "Картинка автора",
        karaokeFileTypeFor = KaraokeFileTypeFor.SONG,
        karaokeFileTypeKind = KaraokeFileTypeKind.MAIN,
        locations = listOf(
            KaraokeFileTypeLocations.LOCAL_FILESYSTEM,
            KaraokeFileTypeLocations.LOCAL_STORAGE,
            KaraokeFileTypeLocations.REMOTE_STORAGE
        ),
        extention = "png",
        suffix = ".author",
    ),
    PICTURE_AUTHOR_PREVIEW (
        description = "Картинка автора (preview)",
        karaokeFileTypeFor = KaraokeFileTypeFor.SONG,
        karaokeFileTypeKind = KaraokeFileTypeKind.MAIN,
        locations = listOf(
            KaraokeFileTypeLocations.LOCAL_FILESYSTEM,
            KaraokeFileTypeLocations.LOCAL_STORAGE,
            KaraokeFileTypeLocations.REMOTE_STORAGE
        ),
        extention = "png",
        suffix = ".preview.author",
    ),
    PICTURE_PUBLICATION (
        description = "Картинка публикации",
        karaokeFileTypeFor = KaraokeFileTypeFor.PLATFORM,
        karaokeFileTypeKind = KaraokeFileTypeKind.MAIN,
        locations = listOf(
            KaraokeFileTypeLocations.LOCAL_FILESYSTEM,
            KaraokeFileTypeLocations.LOCAL_STORAGE
        ),
        symlinks = listOf(KaraokeFileSymlink(folder = "symlink_sponsr", platforms = listOf(KaraokePlatform.SPONSR))),
        extention = "png",
        suffix = "",
    ),
    PICTURE_SONGVERSION (
        description = "Картинка для видео конкретной версии песни",
        karaokeFileTypeFor = KaraokeFileTypeFor.SONGVERSION,
        karaokeFileTypeKind = KaraokeFileTypeKind.MAIN,
        locations = listOf(
            KaraokeFileTypeLocations.LOCAL_FILESYSTEM,
            KaraokeFileTypeLocations.LOCAL_STORAGE
        ),
        symlinks = listOf(KaraokeFileSymlink(folder = "symlink_png")),
        extention = "png",
        suffix = "",
    ),
    VIDEO_SONGVERSION_1080P (
        description = "Видео конкретной версии песни в разрешении 1080p/60fps",
        karaokeFileTypeFor = KaraokeFileTypeFor.SONGVERSION,
        karaokeFileTypeKind = KaraokeFileTypeKind.MAIN,
        locations = listOf(KaraokeFileTypeLocations.LOCAL_FILESYSTEM),
        symlinks = listOf(KaraokeFileSymlink(folder = "symlink_mp4")),
        extention = "mp4",
        suffix = "",
    ),
    VIDEO_SONGVERSION_720P (
        description = "Видео конкретной версии песни в разрешении 720p/30fps",
        karaokeFileTypeFor = KaraokeFileTypeFor.SONGVERSION,
        karaokeFileTypeKind = KaraokeFileTypeKind.MAIN,
        locations = listOf(KaraokeFileTypeLocations.LOCAL_FILESYSTEM),
        extention = "mp4",
        suffix = "",
    ),

    // PROJECT

    PROJECT_ALL_RUN (
        description = "Скрипт для рендера всех версий",
        karaokeFileTypeFor = KaraokeFileTypeFor.SONG,
        karaokeFileTypeKind = KaraokeFileTypeKind.PROJECT,
        locations = listOf(KaraokeFileTypeLocations.LOCAL_FILESYSTEM),
        extention = "run",
        suffix = " [ALL]",
    ),
    PROJECT_ALL_WO_LYRICS_RUN (
        description = "Скрипт для рендера всех версий, кроме LYRICS",
        karaokeFileTypeFor = KaraokeFileTypeFor.SONG,
        karaokeFileTypeKind = KaraokeFileTypeKind.PROJECT,
        locations = listOf(KaraokeFileTypeLocations.LOCAL_FILESYSTEM),
        extention = "run",
        suffix = " [ALLwoLYRICS]",
    ),
    PROJECT_SONGVERSION_RUN (
        description = "Скрипт для рендера конкретной версии",
        karaokeFileTypeFor = KaraokeFileTypeFor.SONGVERSION,
        karaokeFileTypeKind = KaraokeFileTypeKind.PROJECT,
        locations = listOf(KaraokeFileTypeLocations.LOCAL_FILESYSTEM),
        extention = "run",
        suffix = "",
    ),
    PROJECT_SONGVERSION_MLT (
        description = "MLT-файл для конкретной версии",
        karaokeFileTypeFor = KaraokeFileTypeFor.SONGVERSION,
        karaokeFileTypeKind = KaraokeFileTypeKind.PROJECT,
        locations = listOf(KaraokeFileTypeLocations.LOCAL_FILESYSTEM),
        extention = "mlt",
        suffix = "",
    ),
    PROJECT_SONGVERSION_KDENLIVE (
        description = "KDENLIVE-файл для конкретной версии",
        karaokeFileTypeFor = KaraokeFileTypeFor.SONGVERSION,
        karaokeFileTypeKind = KaraokeFileTypeKind.PROJECT,
        locations = listOf(KaraokeFileTypeLocations.LOCAL_FILESYSTEM),
        extention = "kdenlive",
        suffix = "",
    ),
    PROJECT_SONGVERSION_TXT (
        description = "TXT-файл для конкретной версии",
        karaokeFileTypeFor = KaraokeFileTypeFor.SONGVERSION,
        karaokeFileTypeKind = KaraokeFileTypeKind.PROJECT,
        locations = listOf(KaraokeFileTypeLocations.LOCAL_FILESYSTEM),
        extention = "txt",
        suffix = "",
    ),


    ;

    val willBeInFileSystem: Boolean get() = locations.contains(KaraokeFileTypeLocations.LOCAL_FILESYSTEM)
    val willBeInLocalStorage: Boolean get() = locations.contains(KaraokeFileTypeLocations.LOCAL_STORAGE)
    val willBeInRemoteStorage: Boolean get() = locations.contains(KaraokeFileTypeLocations.REMOTE_STORAGE)
}