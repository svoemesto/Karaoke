package com.svoemesto.karaokeapp

enum class KaraokeFileType(
    val description: String,
    val karaokeFileTypeFor: KaraokeFileTypeFor,
    val karaokeFileTypeKind: KaraokeFileTypeKind,
    val locations: List<KaraokeFileTypeLocations>,
    val symlinks: List<KaraokeFileSymlink> = emptyList(),
    val extention: String,
    val suffix: String,
    val canResolve: Boolean
) {

    // MAIN

    AUDIO_SONG (
        description = "Исходный аудио файл",
        karaokeFileTypeFor = KaraokeFileTypeFor.SONG,
        karaokeFileTypeKind = KaraokeFileTypeKind.MAIN,
        locations = listOf(KaraokeFileTypeLocations.LOCAL_FILESYSTEM),
        extention = "flac",
        suffix = ".song",
        canResolve = false
    ),
    MP3_SONG (
        description = "Исходный аудио файл в формате mp3",
        karaokeFileTypeFor = KaraokeFileTypeFor.SONG,
        karaokeFileTypeKind = KaraokeFileTypeKind.MAIN,
        locations = listOf(KaraokeFileTypeLocations.LOCAL_FILESYSTEM),
        extention = "flac",
        suffix = ".song",
        canResolve = true
    ),
    AUDIO_ACCOMPANIMENT (
        description = "Минусовка",
        karaokeFileTypeFor = KaraokeFileTypeFor.SONG,
        karaokeFileTypeKind = KaraokeFileTypeKind.MAIN,
        locations = listOf(KaraokeFileTypeLocations.LOCAL_FILESYSTEM),
        symlinks = listOf(KaraokeFileSymlink(folder = "symlink_sponsr")),
        extention = "flac",
        suffix = ".accompaniment",
        canResolve = true
    ),
    MP3_ACCOMPANIMENT (
        description = "Минусовка в формате mp3",
        karaokeFileTypeFor = KaraokeFileTypeFor.SONG,
        karaokeFileTypeKind = KaraokeFileTypeKind.MAIN,
        locations = listOf(KaraokeFileTypeLocations.LOCAL_FILESYSTEM),
        extention = "flac",
        suffix = ".accompaniment",
        canResolve = true
    ),
    AUDIO_VOICE (
        description = "Чистый голос",
        karaokeFileTypeFor = KaraokeFileTypeFor.SONG,
        karaokeFileTypeKind = KaraokeFileTypeKind.MAIN,
        locations = listOf(KaraokeFileTypeLocations.LOCAL_FILESYSTEM),
        extention = "flac",
        suffix = ".vocals",
        canResolve = true
    ),
    AUDIO_BASS (
        description = "Бас",
        karaokeFileTypeFor = KaraokeFileTypeFor.SONG,
        karaokeFileTypeKind = KaraokeFileTypeKind.MAIN,
        locations = listOf(KaraokeFileTypeLocations.LOCAL_FILESYSTEM),
        extention = "flac",
        suffix = ".bass",
        canResolve = true
    ),
    AUDIO_DRUMS (
        description = "Ударные",
        karaokeFileTypeFor = KaraokeFileTypeFor.SONG,
        karaokeFileTypeKind = KaraokeFileTypeKind.MAIN,
        locations = listOf(KaraokeFileTypeLocations.LOCAL_FILESYSTEM),
        extention = "flac",
        suffix = ".drums",
        canResolve = true
    ),
    AUDIO_OTHER (
        description = "Мелодия без баса и ударных",
        karaokeFileTypeFor = KaraokeFileTypeFor.SONG,
        karaokeFileTypeKind = KaraokeFileTypeKind.MAIN,
        locations = listOf(KaraokeFileTypeLocations.LOCAL_FILESYSTEM),
        extention = "flac",
        suffix = ".other",
        canResolve = true
    ),
    PICTURE_ALBUM (
        description = "Картинка альбома",
        karaokeFileTypeFor = KaraokeFileTypeFor.SONG,
        karaokeFileTypeKind = KaraokeFileTypeKind.MAIN,
        locations = listOf(
            KaraokeFileTypeLocations.LOCAL_FILESYSTEM,
            KaraokeFileTypeLocations.LOCAL_STORAGE,
            KaraokeFileTypeLocations.REMOTE_STORAGE
        ),
        extention = "png",
        suffix = ".album",
        canResolve = false
    ),
    PICTURE_ALBUM_PREVIEW (
        description = "Картинка альбома (preview)",
        karaokeFileTypeFor = KaraokeFileTypeFor.SONG,
        karaokeFileTypeKind = KaraokeFileTypeKind.MAIN,
        locations = listOf(
            KaraokeFileTypeLocations.LOCAL_FILESYSTEM,
            KaraokeFileTypeLocations.LOCAL_STORAGE,
            KaraokeFileTypeLocations.REMOTE_STORAGE
        ),
        extention = "png",
        suffix = ".preview.album",
        canResolve = true
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
        canResolve = false
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
        canResolve = true
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
        canResolve = true
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
        canResolve = true
    ),
    VIDEO_SONGVERSION_1080P (
        description = "Видео конкретной версии песни в разрешении 1080p/60fps",
        karaokeFileTypeFor = KaraokeFileTypeFor.SONGVERSION,
        karaokeFileTypeKind = KaraokeFileTypeKind.MAIN,
        locations = listOf(KaraokeFileTypeLocations.LOCAL_FILESYSTEM),
        symlinks = listOf(KaraokeFileSymlink(folder = "symlink_mp4")),
        extention = "mp4",
        suffix = "",
        canResolve = true
    ),
    VIDEO_SONGVERSION_720P (
        description = "Видео конкретной версии песни в разрешении 720p/30fps",
        karaokeFileTypeFor = KaraokeFileTypeFor.SONGVERSION,
        karaokeFileTypeKind = KaraokeFileTypeKind.MAIN,
        locations = listOf(KaraokeFileTypeLocations.LOCAL_FILESYSTEM),
        extention = "mp4",
        suffix = "",
        canResolve = true
    ),

    // PROJECT

    PROJECT_SONGVERSION_RUN (
        description = "Скрипт для рендера конкретной версии",
        karaokeFileTypeFor = KaraokeFileTypeFor.SONGVERSION,
        karaokeFileTypeKind = KaraokeFileTypeKind.PROJECT,
        locations = listOf(KaraokeFileTypeLocations.LOCAL_FILESYSTEM),
        extention = "run",
        suffix = "",
        canResolve = false
    ),
    PROJECT_SONGVERSION_MLT (
        description = "MLT-файл для конкретной версии",
        karaokeFileTypeFor = KaraokeFileTypeFor.SONGVERSION,
        karaokeFileTypeKind = KaraokeFileTypeKind.PROJECT,
        locations = listOf(KaraokeFileTypeLocations.LOCAL_FILESYSTEM),
        extention = "mlt",
        suffix = "",
        canResolve = false
    ),
    PROJECT_SONGVERSION_KDENLIVE (
        description = "KDENLIVE-файл для конкретной версии",
        karaokeFileTypeFor = KaraokeFileTypeFor.SONGVERSION,
        karaokeFileTypeKind = KaraokeFileTypeKind.PROJECT,
        locations = listOf(KaraokeFileTypeLocations.LOCAL_FILESYSTEM),
        extention = "kdenlive",
        suffix = "",
        canResolve = false
    ),
    PROJECT_SONGVERSION_TXT (
        description = "TXT-файл для конкретной версии",
        karaokeFileTypeFor = KaraokeFileTypeFor.SONGVERSION,
        karaokeFileTypeKind = KaraokeFileTypeKind.PROJECT,
        locations = listOf(KaraokeFileTypeLocations.LOCAL_FILESYSTEM),
        extention = "txt",
        suffix = "",
        canResolve = false
    ),

    ;

    val willBeInFileSystem: Boolean get() = locations.contains(KaraokeFileTypeLocations.LOCAL_FILESYSTEM)
    val willBeInLocalStorage: Boolean get() = locations.contains(KaraokeFileTypeLocations.LOCAL_STORAGE)
    val willBeInRemoteStorage: Boolean get() = locations.contains(KaraokeFileTypeLocations.REMOTE_STORAGE)

}