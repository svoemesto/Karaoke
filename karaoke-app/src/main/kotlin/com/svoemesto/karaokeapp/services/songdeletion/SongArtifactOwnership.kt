package com.svoemesto.karaokeapp.services.songdeletion

import com.svoemesto.karaokeapp.model.SongArtifactRenamer
import java.io.File

/**
 * ЕДИНЫЙ модуль правил владения артефактом песни (KARAOKE-429, спека 739).
 *
 * **Почему модуль один, а правил два.** Правило владения — самый опасный place фичи:
 * от него зависит, чужое ли мы удаляем. Обе операции фичи (удаление песни и разовая
 * уборка осиротевших артефактов) обязаны опираться на ОДИН И ТОТ ЖЕ код, иначе
 * предпросмотр и фактическая уборка разойдутся (SC-004 требует расхождения 0),
 * а разовая уборка получит вторую, несовместимую копию логики.
 *
 * При этом сами правила — противоположные:
 *
 * | Правило | Где применяется | Смысл |
 * |---|---|---|
 * | **строгое** ([isOwnArtifact]) | удаление песни | «файл СВОЙ» — совпадение по имени песни |
 * | **нормализованное** ([normalizeForMatch], [isNearMatchOfLiveSong]) | разовая уборка | «файл ЧУЖОЙ (живой)» — совпадение после нормализации написания |
 *
 * Строгое правило: файл принадлежит песне, если его имя начинается с имени файла
 * песни, а остаток — продолжение артефакта ([SongArtifactRenamer.isArtifactContinuation]:
 * пусто, `.расширение`, `-суффикс`, ` [метка]`). Совпадение **регистрозависимое**:
 * `песня.mp3` при песне `Песня` — чужой файл. Различия `ё`/`е`, написания кавычек
 * и `01`/`1` значимы: нормализация применяется ТОЛЬКО в разовой уборке и только
 * для того, чтобы объект живой песни НЕ попал в кандидаты (FR-042).
 *
 * @see specs/739-song-delete-cascade/spec.md FR-011, FR-042
 * @see specs/739-song-delete-cascade/plan.md § «Решения лида» (R-003)
 */
object SongArtifactOwnership {
    /**
     * Продолжения имени артефакта, разрешённые строгим правилом.
     *
     * Делегирует [SongArtifactRenamer.isArtifactContinuation] — правило переименования
     * артефактов и правило владения при удалении MUST совпадать: иначе песня,
     * чьи файлы удалены, получит при следующем переименовании «сиротские» файлы.
     */
    private val CONTINUATION: (String) -> Boolean = SongArtifactRenamer::isArtifactContinuation

    /**
     * Строгое правило владения: файл принадлежит песне?
     *
     * Строгое (FR-011): совпадение учитывает регистр, `ё`/`е`, кавычки, `1`/`01`.
     * Near-match по нормализации НИКОГДА не признаётся своим.
     *
     * @param songFileName имя файла песни (`Song.fileName`) — без расширения.
     * @param candidateName имя проверяемого файла.
     */
    fun isOwnArtifact(
        songFileName: String,
        candidateName: String,
    ): Boolean {
        if (songFileName.isBlank()) return false
        if (candidateName.isBlank()) return false
        if (!candidateName.startsWith(songFileName)) return false
        return CONTINUATION(candidateName.removePrefix(songFileName))
    }

    /**
     * Нормализация имени для СРАВНЕНИЯ (FR-042) — только для разовой уборки.
     *
     * Нормализует: регистр, `ё`/`е`, тип кавычек, ведущие нули в числах (`01` → `1`),
     * лишние пробелы. Результат используется ТОЛЬКО для того, чтобы объект живой
     * песни не попал в перечень кандидатов на удаление.
     */
    fun normalizeForMatch(name: String): String {
        var normalized = name.trim().lowercase()
        normalized = normalized.replace('ё', 'е')
        normalized = QUOTES.replace(normalized, "")
        normalized = SPACES.replace(normalized, " ")
        normalized = LEADING_ZEROS.replace(normalized, "$1")
        return normalized.trim()
    }

    /**
     * Является ли объект near-match'ем имени ЖИВОЙ песни?
     *
     * Используется разовой уборкой: такой объект — не сирота, он исключается из
     * перечня (FR-042, SC-008).
     *
     * Сравнение идёт по нормализованным именам (регистр, `ё`/`е`, кавычки, `01`/`1`)
     * с тем же правилом продолжения артефакта, что и строгое правило: имя объекта
     * должно начинаться с имени живой песни, а остаток — продолжение артефакта
     * (`.mp3`, `-vocals`, ` [karaoke]`). Поэтому «Песня.mp3» — near-match живой песни
     * «Песня», а «Песня, длинная версия.mp3» — нет (это другая песня).
     *
     * @param liveNormalizedNames нормализованные имена живых песен.
     * @param candidateName имя проверяемого объекта (путь/ключ или имя файла).
     */
    fun isNearMatchOfLiveSong(
        liveNormalizedNames: Set<String>,
        candidateName: String,
    ): Boolean {
        if (liveNormalizedNames.isEmpty()) return false
        val normalized = normalizeForMatch(candidateName.substringAfterLast('/'))
        if (normalized.isBlank()) return false
        if (liveNormalizedNames.contains(normalized)) return true
        return liveNormalizedNames.any { live ->
            live.isNotBlank() &&
                normalized.startsWith(live) &&
                isNormalizedContinuation(normalized.removePrefix(live))
        }
    }

    /**
     * Продолжение артефакта в нормализованном виде: `.расширение`, `-суффикс`, ` [метка]`.
     */
    private fun isNormalizedContinuation(rest: String): Boolean =
        rest.startsWith(".") || rest.startsWith("-") || rest.startsWith(" [")

    /**
     * Обход папки альбома: вернуть файлы, принадлежащие песне.
     *
     * Обходятся корень папки альбома и подкаталоги артефактов:
     * [SongArtifactRenamer.ARTIFACT_SUB_FOLDERS] (`hdemucs_mmi`, `done_files`,
     * `done_chords`, `sheetsage`) и все каталоги `symlink_*`
     * ([SongArtifactRenamer.SYMLINK_FOLDER_PREFIX]).
     *
     * Возвращаются ТОЛЬКО файлы песни — файл соседней песни с префиксом в список
     * не попадает (FR-010, FR-011).
     *
     * @param rootFolder папка альбома (`Song.rootFolder`).
     * @param songFileName имя файла песни.
     */
    fun collectOwnArtifacts(
        rootFolder: String,
        songFileName: String,
    ): List<File> {
        if (rootFolder.isBlank() || songFileName.isBlank()) return emptyList()
        val root = File(rootFolder)
        if (!root.isDirectory) return emptyList()
        val result = mutableListOf<File>()
        artifactDirs(root).forEach { dir ->
            val files = dir.listFiles()?.sortedBy { it.name }.orEmpty()
            files.forEach { file ->
                if (file.isDirectory) return@forEach
                if (!isOwnArtifact(songFileName, file.name)) return@forEach
                result.add(file)
            }
        }
        return result
    }

    /**
     * Каталоги, в которых лежат артефакты песни: корень, подкаталоги артефактов и
     * каталоги символических ссылок.
     *
     * Порядок совпадает с [SongArtifactRenamer]: корневой каталог, затем
     * `hdemucs_mmi`/`done_files`/`done_chords`/`sheetsage`, затем `symlink_*`.
     */
    fun artifactDirs(root: File): List<File> {
        val dirs = mutableListOf(root)
        SongArtifactRenamer.ARTIFACT_SUB_FOLDERS.forEach { name ->
            val dir = File(root, name)
            if (dir.isDirectory) dirs.add(dir)
        }
        root
            .listFiles { f -> f.isDirectory && f.name.startsWith(SongArtifactRenamer.SYMLINK_FOLDER_PREFIX) }
            ?.sortedBy { it.name }
            ?.forEach { dirs.add(it) }
        return dirs
    }

    /**
     * Строгое правило владения для объекта хранилища (MinIO).
     *
     * Ключ песни: `$автор/$год - $альбом/$имя песни` + продолжение артефакта.
     * Обложка альбома и картинка автора начинаются с ДРУГОГО префикса, поэтому
     * структурно не попадают в выборку (FR-014).
     *
     * @param author автор песни; @param year год; @param album альбом.
     * @param songFileName имя файла песни; @param key ключ объекта в бакете.
     */
    fun isOwnStorageKey(
        author: String,
        year: String,
        album: String,
        songFileName: String,
        key: String,
    ): Boolean {
        if (songFileName.isBlank() || key.isBlank()) return false
        val prefix = "$author/$year - $album/"
        if (!key.startsWith(prefix)) return false
        val rest = key.removePrefix(prefix)
        if (!rest.startsWith(songFileName)) return false
        if (!CONTINUATION(rest.removePrefix(songFileName))) return false
        // Второй рубеж (эшелонированная защита FR-014): обложка альбома и картинка
        // автора — общие для всех песен альбома/автора и не удаляются с песнёй.
        if (isSharedCoverKey(author, year, album, key)) return false
        return true
    }

    /**
     * Ключ общей обложки (альбом/автор) — не удаляется при удалении песни (FR-014).
     */
    fun isSharedCoverKey(
        author: String,
        year: String,
        album: String,
        key: String,
    ): Boolean {
        val name = key.substringAfterLast('/')
        val albumCoverBase = "$author - $year - $album"
        val authorCoverBase = author
        return SHARED_COVER_SUFFIXES.any { suffix ->
            name == "$albumCoverBase$suffix" || name == "$authorCoverBase$suffix"
        }
    }

    /** Суффиксы общих обложек (альбом/автор + превью). */
    private val SHARED_COVER_SUFFIXES = listOf(".album.png", ".preview.album.png", ".author.png", ".preview.author.png")

    /** Кавычки всех видов — при нормализации убираются. */
    private val QUOTES = Regex("[«»„“”\"'`´‘’]")

    /** Несколько пробелов подряд — схлопываются в один. */
    private val SPACES = Regex("\\s+")

    /** Ведущие нули в числе: `01` → `1`, `007` → `7`. */
    private val LEADING_ZEROS = Regex("(?<![\\p{L}\\p{N}])0+(\\d)")
}
