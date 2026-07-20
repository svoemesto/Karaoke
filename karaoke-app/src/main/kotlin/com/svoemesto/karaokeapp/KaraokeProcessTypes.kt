package com.svoemesto.karaokeapp

enum class KaraokeProcessTypes {
    NONE,
    MELT_LYRICS,
    MELT_KARAOKE,
    MELT_CHORDS,
    MELT_TABS,
    DEMUCS2,
    DEMUCS5,
    SHEETSAGE,
    SHEETSAGE2,
    FF_720_KAR,
    FF_720_LYR,
    SYMLINK,
    SMARTCOPY,
    COPY_TO_STORE_LYRICS,
    COPY_TO_STORE_KARAOKE,

//    FF_MP3_KAR,
//    FF_MP3_LYR,
    FF_MP3_ACCOMPANIMENT,
    FF_MP3_VOCAL,
    FF_MP3_DRUMS,
    FF_MP3_BASS,
    FF_MP3_OTHER,
    KEY_BPM_FROM_FILE,
    UPLOAD_TO_LOCAL_STORE,
    UPLOAD_TO_REMOTE_STORE,
    RENDER_MP4_LYRICS,
    RENDER_MP4_KARAOKE,
    RENDER_MP4_CHORDS,
    RENDER_MP4_TABS,
    RENDER_MP4_DEMO,
//    RECODE_48000,

    // Премиум-фича «Создать минусовку из аудиофайла» (StemJob, tbl_stem_jobs) — тот же демукс, что и
    // DEMUCS2/DEMUCS5, но для произвольного файла, загруженного пользователем публичного сайта, а не
    // для Settings/песни (settingsId=0). См. StemJobProcessing.kt.
    STEM_JOB_DEMUCS2,
    STEM_JOB_DEMUCS5,
}
