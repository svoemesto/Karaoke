package com.svoemesto.karaokeapp.services.cleanup

/**
 * Каталоги папки песни, по которым раскладываются артефакты.
 *
 * Вынесены на верхний уровень файла намеренно: записи enum-класса не могут обращаться
 * к собственному `companion object` (Kotlin: «companion object of enum class is
 * uninitialized here»), а раскладка нужна уже в конструкторах записей реестра.
 */
const val CLEANUP_STEMS_FOLDER = "hdemucs_mmi"
const val CLEANUP_DONE_FILES_FOLDER = "done_files"
const val CLEANUP_DONE_PROJECTS_FOLDER = "done_projects"
const val CLEANUP_DONE_CHORDS_FOLDER = "done_chords"
const val CLEANUP_SHEETSAGE_FOLDER = "sheetsage"

/** Каталоги симлинков песни (`Song.kt:1777-1779`). */
val CLEANUP_SYMLINK_FOLDERS = listOf("symlink_mp4", "symlink_png", "symlink_sponsr")

/**
 * Вердикт по классу артефактов песни (см. `specs/544-song-artifact-cleanup/data-model.md` §1).
 *
 * @see specs/544-song-artifact-cleanup/data-model.md
 */
enum class ArtifactCleanupVerdict {
    /** Не удаляется ничем и никогда (исходники, flac-стемы). */
    PROTECTED,

    /** Остаётся как есть — у артефакта есть читатель. */
    KEEP,

    /** Код перестаёт создавать артефакт (производство мусора прекращается). */
    STOP_CREATING,

    /** Артефакт удаляется на шаге жизненного цикла (например, в конце публикации). */
    DELETE_AFTER_STAGE,

    /** Артефакт удаляется разовой чисткой истории. */
    DELETE_EXISTING,

    /** Есть читатель — руками не трогать, но и не защищён от будущих решений. */
    NOT_TOUCH,
}

/**
 * Где живут файлы класса. Обход выполняется [ArtifactCleanupPlanner] по этим правилам,
 * чтобы «что с каким артефактом делаем» было в одном месте (реестре), а не в планировщике.
 *
 * @param subFolder имя каталога-родителя относительно папки песни (`hdemucs_mmi`, `done_files`,
 *   `done_projects`, `done_chords`), либо пустая строка для корня папки песни.
 * @param extensions расширения файлов (с точкой), по которым матчится класс; пустой список —
 *   любое расширение.
 * @param nameContainsAny имя файла должно содержать любую из этих подстрок; пустой список —
 *   без ограничения.
 * @param nameExactly имя файла должно быть равно этой строке.
 * @param maxBytes верхняя граница размера (для «мёртвых» мелких файлов вроде 1-байтных `.txt`).
 * @param brokenSymlinkOnly матчить только символические ссылки без цели.
 */
data class CleanupFileMatcher(
    val subFolder: String,
    val extensions: List<String> = emptyList(),
    val nameContainsAny: List<String> = emptyList(),
    val nameExactly: String = "",
    val maxBytes: Long? = null,
    val brokenSymlinkOnly: Boolean = false,
)

/**
 * Реестр классов артефактов песни — единственное место правды о вердиктах и порядке чистки
 * (`specs/544-song-artifact-cleanup/data-model.md` §1, FR-010/FR-013/FR-015).
 *
 * Используется и автоматической уборкой в пайплайне, и разовой чисткой истории.
 * Класс с вердиктом [ArtifactCleanupVerdict.PROTECTED] или [ArtifactCleanupVerdict.NOT_TOUCH]
 * отклоняется API чистки **всегда**, независимо от подтверждений (FR-010).
 *
 * @param expectedFileCount ожидаемое число файлов класса по инвентаризации 2026-10-16;
 *   `0` означает «число не сверяется» (класс собирается из разнородного мусора).
 * @param expectedBytes ожидаемый объём класса в байтах по инвентаризации 2026-10-16;
 *   `0` означает «объём не сверяется».
 * @see specs/544-song-artifact-cleanup/data-model.md
 */
enum class ArtifactCleanupClass(
    val title: String,
    val verdicts: Set<ArtifactCleanupVerdict>,
    val cleanupOrder: Int,
    val expectedFileCount: Long,
    val expectedBytes: Long,
    val note: String,
    val matchers: List<CleanupFileMatcher>,
) {
    SOURCE_FLAC(
        title = "Исходный аудиофайл песни",
        verdicts = setOf(ArtifactCleanupVerdict.PROTECTED),
        cleanupOrder = 0,
        expectedFileCount = 26_769,
        expectedBytes = gigibytes(768.1),
        note = "Неприкосновенный класс: удаление запрещено (FR-010)",
        matchers = listOf(CleanupFileMatcher(subFolder = "", extensions = listOf(".flac"))),
    ),
    STEMS_FLAC(
        title = "FLAC-стемы (hdemucs_mmi)",
        verdicts = setOf(ArtifactCleanupVerdict.PROTECTED),
        cleanupOrder = 0,
        expectedFileCount = 53_944,
        expectedBytes = gigibytes(964.7),
        note = "Неприкосновенный класс: удаление запрещено (FR-010)",
        matchers = listOf(CleanupFileMatcher(subFolder = CLEANUP_STEMS_FOLDER, extensions = listOf(".flac"))),
    ),
    STEMS_MP3_LOCAL(
        title = "Локальные mp3-стемы",
        verdicts = setOf(ArtifactCleanupVerdict.DELETE_AFTER_STAGE, ArtifactCleanupVerdict.DELETE_EXISTING),
        cleanupOrder = 2,
        expectedFileCount = 53_618,
        expectedBytes = gigibytes(445.0),
        note = "Копии совпадают с MinIO побайтово; чистка допустима только после правки модели ожидаемых файлов (FR-004)",
        matchers = listOf(CleanupFileMatcher(subFolder = CLEANUP_STEMS_FOLDER, extensions = listOf(".mp3"))),
    ),
    DEMO_MP4(
        title = "Демо-ролик публикации",
        verdicts = setOf(ArtifactCleanupVerdict.DELETE_AFTER_STAGE, ArtifactCleanupVerdict.DELETE_EXISTING),
        cleanupOrder = 1,
        expectedFileCount = 17_885,
        expectedBytes = gigibytes(176.7),
        note = "Самый крупный низкорисковый класс; не хранится после публикации (FR-001)",
        matchers =
            listOf(
                CleanupFileMatcher(
                    subFolder = CLEANUP_DONE_FILES_FOLDER,
                    extensions = listOf(".mp4"),
                    nameContainsAny = listOf("[demo]"),
                ),
            ),
    ),
    PUBLICATION_PNG(
        title = "Публикационные картинки",
        verdicts = setOf(ArtifactCleanupVerdict.DELETE_AFTER_STAGE, ArtifactCleanupVerdict.DELETE_EXISTING),
        cleanupOrder = 3,
        expectedFileCount = 44_651,
        expectedBytes = gigibytes(20.6),
        note = "Воссоздаются из данных песни; набор суффиксов задан UtilsPictures.kt — расхождение видно как warning отчёта (FR-005)",
        matchers =
            listOf(
                CleanupFileMatcher(
                    subFolder = CLEANUP_DONE_FILES_FOLDER,
                    extensions = listOf(".png"),
                    nameContainsAny = listOf("[karaoke]", "[lyrics]", "[sponsr]", "[vklink]", "[vkgroup]", "song_banner"),
                ),
            ),
    ),
    CHORDS_PNG(
        title = "Картинки аккордов",
        verdicts = setOf(ArtifactCleanupVerdict.DELETE_EXISTING),
        cleanupOrder = 3,
        expectedFileCount = 0,
        expectedBytes = 0,
        note = "Учтено в объёме PUBLICATION_PNG; писатель createSongChordsPicture сегодня вызывается только из закомментированной строки — автоматического удаления нет",
        matchers =
            listOf(
                CleanupFileMatcher(subFolder = CLEANUP_DONE_CHORDS_FOLDER, extensions = listOf(".png")),
                CleanupFileMatcher(
                    subFolder = CLEANUP_DONE_FILES_FOLDER,
                    extensions = listOf(".png"),
                    nameContainsAny = listOf("[chords]"),
                ),
            ),
    ),
    SONG_TXT(
        title = "Текстовые описания публикации",
        verdicts = setOf(ArtifactCleanupVerdict.KEEP),
        cleanupOrder = 0,
        expectedFileCount = 29_792,
        expectedBytes = mebibytes(76.0),
        note = "Есть читатель — не трогать",
        matchers = listOf(CleanupFileMatcher(subFolder = CLEANUP_DONE_FILES_FOLDER, extensions = listOf(".txt"))),
    ),
    SHEETSAGE(
        title = "Результаты Sheetsage",
        verdicts = setOf(ArtifactCleanupVerdict.KEEP),
        cleanupOrder = 0,
        expectedFileCount = 30_673,
        expectedBytes = gigibytes(1.8),
        note = "Используются вручную — не трогать",
        matchers = listOf(CleanupFileMatcher(subFolder = CLEANUP_SHEETSAGE_FOLDER)),
    ),
    DONE_PROJECTS(
        title = "Проекты рендера (done_projects + .kdenlive в корне)",
        verdicts = setOf(ArtifactCleanupVerdict.STOP_CREATING, ArtifactCleanupVerdict.DELETE_EXISTING),
        cleanupOrder = 4,
        expectedFileCount = 8_650,
        expectedBytes = gigibytes(8.7),
        note = "Код перестаёт создавать (FR-009); замороженный melt-конвейер, читателей нет",
        matchers =
            listOf(
                CleanupFileMatcher(
                    subFolder = CLEANUP_DONE_PROJECTS_FOLDER,
                    extensions = listOf(".mlt", ".kdenlive", ".run", ".txt"),
                ),
                CleanupFileMatcher(subFolder = "", extensions = listOf(".kdenlive")),
            ),
    ),
    SRT(
        title = "Субтитры и их бэкапы",
        verdicts = setOf(ArtifactCleanupVerdict.STOP_CREATING, ArtifactCleanupVerdict.DELETE_EXISTING),
        cleanupOrder = 6,
        expectedFileCount = 23_748,
        expectedBytes = mebibytes(243.0),
        note = "Разметка живёт в БД; запись файлов снимается (FR-008)",
        matchers =
            listOf(
                CleanupFileMatcher(subFolder = "", extensions = listOf(".srt", ".srt.backup")),
            ),
    ),
    SONG_SIDECAR(
        title = "Сериализованное состояние песни",
        verdicts = setOf(ArtifactCleanupVerdict.STOP_CREATING, ArtifactCleanupVerdict.DELETE_EXISTING),
        cleanupOrder = 5,
        expectedFileCount = 32_863,
        expectedBytes = gigibytes(1.2),
        note = "Писатели удаляются (FR-006); содержимое производно от БД",
        matchers = listOf(CleanupFileMatcher(subFolder = "", extensions = listOf(".song", ".settings"))),
    ),
    KEY_JSON(
        title = "Результат key/BPM (keybpmfinder)",
        verdicts = setOf(ArtifactCleanupVerdict.KEEP),
        cleanupOrder = 0,
        expectedFileCount = 15_743,
        expectedBytes = kibibytes(553.0),
        note = "Класс, который решено не трогать",
        matchers =
            listOf(
                CleanupFileMatcher(subFolder = "", extensions = listOf(".json"), nameContainsAny = listOf("[key]")),
            ),
    ),
    HYGIENE_WAV(
        title = "Осиротевшие wav-стемы",
        verdicts = setOf(ArtifactCleanupVerdict.DELETE_EXISTING),
        cleanupOrder = 7,
        expectedFileCount = 20,
        expectedBytes = mebibytes(480.0),
        note = "Промежуточные файлы demucs; остались после сбоев финализации",
        matchers = listOf(CleanupFileMatcher(subFolder = CLEANUP_STEMS_FOLDER, extensions = listOf(".wav"))),
    ),
    HYGIENE_JUNK(
        title = "Гигиенический мусор",
        verdicts = setOf(ArtifactCleanupVerdict.DELETE_EXISTING),
        cleanupOrder = 7,
        expectedFileCount = 0,
        expectedBytes = mebibytes(900.0),
        note = "Битые симлинки, 1-байтные txt в hdemucs_mmi, result_key_bpm_finder.json, *.kdenlive_back*, мёртвое имя [render].mp4 (FR-015)",
        matchers =
            listOf(
                CleanupFileMatcher(subFolder = CLEANUP_STEMS_FOLDER, extensions = listOf(".txt"), maxBytes = 1L),
                CleanupFileMatcher(subFolder = "", nameExactly = "result_key_bpm_finder.json"),
                CleanupFileMatcher(subFolder = "", nameContainsAny = listOf(".kdenlive_back")),
                CleanupFileMatcher(
                    subFolder = CLEANUP_DONE_FILES_FOLDER,
                    extensions = listOf(".mp4"),
                    nameContainsAny = listOf("[render]"),
                ),
            ),
    ),
    KEY_BPM_CSV(
        title = "key_bpm.csv",
        verdicts = setOf(ArtifactCleanupVerdict.NOT_TOUCH),
        cleanupOrder = 0,
        expectedFileCount = 465,
        expectedBytes = mebibytes(0.6),
        note = "Читается getBpmAndKeyFromCsv (Utils.kt) — руками не трогать",
        matchers = listOf(CleanupFileMatcher(subFolder = "", nameExactly = "key_bpm.csv")),
    ),
    MANUAL_FILES(
        title = "Пользовательские файлы (xcf, jpg, zip, wav)",
        verdicts = setOf(ArtifactCleanupVerdict.NOT_TOUCH),
        cleanupOrder = 0,
        expectedFileCount = 0,
        expectedBytes = gigibytes(6.0),
        note = "Вне скоупа #305: писателя в коде нет",
        matchers = emptyList(),
    ),
    ;

    /** Класс защищён от удаления: API чистки отклоняет его всегда (FR-010). */
    val isProtected: Boolean
        get() = ArtifactCleanupVerdict.PROTECTED in verdicts || ArtifactCleanupVerdict.NOT_TOUCH in verdicts

    /** Класс участвует в разовой чистке истории. */
    val isDeletable: Boolean get() = ArtifactCleanupVerdict.DELETE_EXISTING in verdicts

    /** Класс удаляется на шаге жизненного цикла (не разовой чисткой). */
    val isDeletedAfterStage: Boolean get() = ArtifactCleanupVerdict.DELETE_AFTER_STAGE in verdicts

    /** Код перестаёт создавать этот артефакт. */
    val isStopCreating: Boolean get() = ArtifactCleanupVerdict.STOP_CREATING in verdicts

    companion object {
        /** Каталог flac/mp3/wav-стемов песни (`Constants.kt` DEMUCS_MODEL_NAME). */
        const val STEMS_FOLDER = CLEANUP_STEMS_FOLDER
        const val DONE_FILES_FOLDER = CLEANUP_DONE_FILES_FOLDER
        const val DONE_PROJECTS_FOLDER = CLEANUP_DONE_PROJECTS_FOLDER
        const val DONE_CHORDS_FOLDER = CLEANUP_DONE_CHORDS_FOLDER
        const val SHEETSAGE_FOLDER = CLEANUP_SHEETSAGE_FOLDER

        /** Каталоги симлинков песни (`Song.kt:1777-1779`). */
        val SYMLINK_FOLDERS = CLEANUP_SYMLINK_FOLDERS

        /**
         * Подкаталоги папки песни. Файл, чей родитель не входит в этот набор, лежит
         * в корне папки песни (там же, где `<song>.flac`).
         */
        val KNOWN_SUBFOLDERS =
            setOf(
                CLEANUP_STEMS_FOLDER,
                CLEANUP_DONE_FILES_FOLDER,
                CLEANUP_DONE_PROJECTS_FOLDER,
                CLEANUP_DONE_CHORDS_FOLDER,
                CLEANUP_SHEETSAGE_FOLDER,
            ) + CLEANUP_SYMLINK_FOLDERS

        /** Классы, доступные разовой чистке, в порядке из `data-model.md` §1. */
        fun deletable(): List<ArtifactCleanupClass> =
            entries.filter { it.isDeletable && !it.isProtected }.sortedBy { it.cleanupOrder }

        /** Найти класс по идентификатору (регистронезависимо). */
        fun byId(classId: String): ArtifactCleanupClass? =
            entries.firstOrNull { it.name.equals(classId.trim(), ignoreCase = true) }
    }
}

private fun kibibytes(value: Double): Long = (value * 1024.0).toLong()

private fun mebibytes(value: Double): Long = (value * 1024.0 * 1024.0).toLong()

private fun gigibytes(value: Double): Long = (value * 1024.0 * 1024.0 * 1024.0).toLong()
