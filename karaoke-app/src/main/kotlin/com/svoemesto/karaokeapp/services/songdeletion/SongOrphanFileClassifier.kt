package com.svoemesto.karaokeapp.services.songdeletion

import java.util.ArrayDeque

/**
 * Классификатор файлов на диске для разовой уборки (KARAOKE-429, спека 739,
 * FR-041, FR-042).
 *
 * **Правило — из утверждённой инвентаризации** (карта решений KARAOKE-409,
 * тикет KARAOKE-424, артефакт `T11-disk-orphans.md`; замер 2026-11-12,
 * утверждён владельцем). Классификация каждого файла в папке альбома, где есть
 * живые песни:
 *
 * | Группа | Что это | Вердикт |
 * |---|---|---|
 * | (А) | Имя начинается с имени живой песни, хвост — по правилу продолжения | не удалять |
 * | (А2) | Имя живой песни, но хвост нестандартный (`<мусор> [метка].ext`, укороченное имя) | не удалять |
 * | (В) | Не опознан: обложки, `key_bpm.csv`, служебные файлы, ручные правки | не удалять |
 * | (Б1) | Имя совпадает с живой песней после нормализации | не удалять |
 * | (Б2f) | Нечёткое совпадение с живой песней (коэффициент ≥ 0,93) | не удалять |
 * | (Б2) | Песни с таким именем нет в БД **ни в каком виде** | кандидат на удаление |
 *
 * **Почему это отдельный класс, а не «файл не принадлежит ни одной живой песне».**
 * Наивное правило («не принадлежит ни одной живой песне») захватывает и то,
 * что удалять нельзя: обложки альбомов и авторов на диске (`LogoAlbum.png` —
 * их читает поток создания публикационных картинок, `Song.pathToFileLogoAlbum`),
 * `key_bpm.csv` (его читает `getBpmAndKeyFromCsv`), ручные файлы пользователя
 * (`.jpg`, `.xcf`, `.zip`), проекты рендера и прочее служебное. Удаление этих
 * файлов сломало бы живые песни — а владелец в инвентаризации прямо назвал их
 * «не удалять».
 *
 * @see specs/739-song-delete-cascade/spec.md FR-041, FR-042
 */
class SongOrphanFileClassifier(
    /** Живые песни по папке альбома: путь папки → имена файлов песен. */
    private val liveByFolder: Map<String, List<String>>,
    /** Все имена живых песен каталога. */
    allLiveNames: Collection<String>,
) {
    companion object {
        /** Порог нечёткого совпадения с живой песней (Б2f). */
        const val FUZZY_CUTOFF = 0.93

        /** Метки артефактов (`KaraokeFileType`): `[метка].расширение`. */
        private val LABELS =
            listOf(
                "karaoke", "lyrics", "chords", "tabs", "sponsr", "VKlink", "VK", "boosty",
                "files", "vkgroup", "lyricsVk", "karaokeVk", "chordsVk", "tabsVk",
                "demo", "render", "key", "music", "picture", "preview", "publication",
                "beattimes", "sheetsage",
            )

        /** Расширения артефактов. */
        private val EXTS =
            listOf(
                "flac", "mp3", "mp4", "png", "json", "txt", "mlt", "run", "kdenlive",
                "srt", "settings", "song", "wav", "pdf", "midi", "ly", "csv", "xcf",
                "jpg", "jpeg", "gif", "webp", "meta", "zip", "docx", "xml", "html",
                "css", "php", "svg",
            )

        /** Слова стемов (минусовка/вокал/бас/барабаны/прочее). */
        private val STEM_WORDS = listOf("accompaniment", "vocals", "bass", "drums", "other")

        /** Шаблоны имён артефактов: от длинных к коротким (хвост снимается с конца). */
        private val TAILS: List<String> =
            buildList {
                add(" [key].json.meta")
                add(".srt.backup")
                LABELS.forEach { label -> EXTS.forEach { ext -> add(" [$label].$ext") } }
                STEM_WORDS.forEach { word ->
                    listOf("flac", "mp3", "wav").forEach { ext ->
                        add("-$word.$ext")
                        add(".$word.$ext")
                    }
                }
                EXTS.forEach { ext -> add(".$ext") }
            }.sortedByDescending { it.length }

        /** Служебные файлы — читаются приложением, удалению не подлежат. */
        private val SERVICE_NAMES = setOf("key_bpm.csv", "result_key_bpm_finder.json")

        /** Имя песни в каталоге имеет вид `год (NN) [Автор] - Название`. */
        private val SONG_NAME_REGEX = Regex("""\(\d{1,3}\)\s*\[.+?]""")

        /** Не-ASCII буквы и цифры — как `\w` в Python (Unicode). */
        private val NON_WORD_REGEX = Regex("""[^\p{L}\p{N}_\s()\[\]]""")

        private val SPACES_REGEX = Regex("""\s+""")

        /**
         * Продолжение имени артефакта ([SongArtifactRenamer.isArtifactContinuation]):
         * пусто / `.расширение` / `-стема` / ` [метка]`.
         */
        fun isArtifactContinuation(rest: String): Boolean =
            rest.isEmpty() || rest.startsWith(".") || rest.startsWith("-") || rest.startsWith(" [")

        /**
         * Нормализация имени для сравнения (как в инвентаризации): `ё`→`е`,
         * убрать `ъ`, кавычки, знаки препинания → пробел, схлопнуть пробелы,
         * нижний регистр.
         */
        fun normalize(name: String): String {
            var s =
                name
                    .replace('ё', 'е')
                    .replace('Ё', 'Е')
                    .replace("ъ", "")
                    .replace("Ъ", "")
                    .replace("«", "")
                    .replace("»", "")
                    .replace("\"", "")
                    .replace("'", "")
            s = NON_WORD_REGEX.replace(s, " ")
            s = SPACES_REGEX.replace(s, " ")
            return s.trim().lowercase()
        }

        /**
         * Снять шаблон артефакта с конца имени.
         *
         * @return пара (имя без хвоста, хвост) либо `null`, если имя не по шаблону.
         */
        fun stripTail(name: String): Pair<String, String>? {
            TAILS.forEach { tail ->
                if (name.endsWith(tail) && name.length > tail.length) {
                    return name.substring(0, name.length - tail.length) to tail
                }
            }
            return null
        }

        /**
         * Коэффициент подобия двух строк — как `difflib.SequenceMatcher.ratio`:
         * `2 * совпавшие символы / (длина a + длина b)`, где совпавшие символы
         * считаются разбором по самому длинному общему блоку.
         */
        fun similarity(
            a: String,
            b: String,
        ): Double {
            val total = a.length + b.length
            if (total == 0) return 1.0
            var matches = 0
            val queue = ArrayDeque<IntArray>()
            queue.add(intArrayOf(0, a.length, 0, b.length))
            while (queue.isNotEmpty()) {
                val range = queue.removeFirst()
                val block = longestMatch(a, range[0], range[1], b, range[2], range[3])
                val i = block[0]
                val j = block[1]
                val size = block[2]
                if (size > 0) {
                    matches += size
                    if (i > range[0] && j > range[2]) {
                        queue.add(intArrayOf(range[0], i, range[2], j))
                    }
                    if (i + size < range[1] && j + size < range[3]) {
                        queue.add(intArrayOf(i + size, range[1], j + size, range[3]))
                    }
                }
            }
            return 2.0 * matches / total
        }

        /** Самый длинный общий непрерывный блок в пределах диапазонов. */
        private fun longestMatch(
            a: String,
            aStart: Int,
            aEnd: Int,
            b: String,
            bStart: Int,
            bEnd: Int,
        ): IntArray {
            var bestI = aStart
            var bestJ = bStart
            var bestSize = 0
            for (i in aStart until aEnd) {
                for (j in bStart until bEnd) {
                    if (a[i] != b[j]) continue
                    var size = 0
                    while (i + size < aEnd && j + size < bEnd && a[i + size] == b[j + size]) size++
                    if (size > bestSize) {
                        bestSize = size
                        bestI = i
                        bestJ = j
                    }
                }
            }
            return intArrayOf(bestI, bestJ, bestSize)
        }
    }

    /** Вердикт по файлу. */
    enum class Verdict {
        /** Принадлежит живой песне (файл артефакта). */
        LIVE,

        /** Имя живой песни, но хвост нестандартный — удалению не подлежит. */
        LIVE_ODD_TAIL,

        /** Не опознан (обложки, служебное, ручные файлы) — удалению не подлежит. */
        UNRECOGNIZED,

        /** Настоящий сирота: песни с таким именем нет в БД ни в каком виде. */
        ORPHAN,
    }

    /** Результат классификации. */
    data class Result(
        val verdict: Verdict,
        val owner: String?,
        val candidateName: String?,
        val reason: String,
    )

    private val allNames: List<String> = allLiveNames.filter { it.isNotBlank() }.toList()

    /** Нормализованные имена живых песен — для точного (Б1) и нечёткого (Б2f) поиска. */
    private val normalizedNames: List<String> = allNames.map { normalize(it) }.distinct()

    /**
     * Алфавит имён песен — для быстрого отсева при нечётком поиске.
     *
     * Считать мультимножество символов для каждой пары «кандидат ↔ живая песня»
     * на полном списке (десятки тысяч имён) — слишком дорого: остаётся один
     * отсев по длине, а он отсеивает слабо. Мультимножество символов — верхняя
     * оценка коэффициента подобия (`difflib.quick_ratio`): если даже она ниже
     * порога, полный расчёт не нужен.
     */
    private val alphabet: Map<Char, Int> =
        allNames
            .flatMap { normalize(it).toList() }
            .distinct()
            .sorted()
            .withIndex()
            .associate { (index, ch) -> ch to index }

    /** Имя живой песни с посчитанным мультимножеством символов. */
    private class IndexedName(
        val name: String,
        val counts: IntArray,
    )

    /** Нормализованные имена, сгруппированные по длине — для отсева по длине. */
    private val normalizedByLength: Map<Int, List<IndexedName>> =
        normalizedNames
            .distinct()
            .map { name -> IndexedName(name, charCounts(name)) }
            .groupBy { it.name.length }

    /**
     * Классифицировать файл в папке альбома.
     *
     * @param fileName имя файла (с расширением).
     * @param albumFolder путь папки альбома.
     * @return вердикт с пояснением.
     */
    fun classify(
        fileName: String,
        albumFolder: String,
    ): Result {
        val albumLiveNames = liveByFolder[albumFolder].orEmpty()

        // (А)/(А2): имя начинается с имени живой песни этого альбома.
        var strictOwner: String? = null
        var looseOwner: String? = null
        for (fn in albumLiveNames) {
            if (!fileName.startsWith(fn) || fileName.length == fn.length) continue
            val rest = fileName.substring(fn.length)
            if (isArtifactContinuation(rest)) {
                strictOwner = fn
                break
            }
            if (looseOwner == null) looseOwner = fn
        }
        if (strictOwner != null) {
            return Result(Verdict.LIVE, strictOwner, null, "принадлежит живой песне")
        }
        if (looseOwner != null) {
            return Result(
                Verdict.LIVE_ODD_TAIL,
                looseOwner,
                null,
                "имя живой песни, но хвост не по правилу продолжения",
            )
        }

        // (В): служебные файлы.
        if (fileName in SERVICE_NAMES) {
            return Result(Verdict.UNRECOGNIZED, null, null, "служебный файл")
        }

        // (В)/(Б): снимаем шаблон артефакта с конца имени.
        val stripped = stripTail(fileName)
        if (stripped == null) {
            return Result(Verdict.UNRECOGNIZED, null, null, "не артефактный шаблон имени")
        }
        val candidate = stripped.first
        if (!SONG_NAME_REGEX.containsMatchIn(candidate)) {
            return Result(
                Verdict.UNRECOGNIZED,
                null,
                candidate,
                "хвост есть, но имя не похоже на имя песни",
            )
        }

        // (В): имя песни из ДРУГОГО альбома — не удаляем.
        if (allNames.contains(candidate)) {
            return Result(
                Verdict.UNRECOGNIZED,
                null,
                candidate,
                "имя песни из другого альбома",
            )
        }

        // (А2): укороченное имя живой песни.
        val shortened = allNames.firstOrNull { it != candidate && it.startsWith(candidate) && it.length - candidate.length <= 40 }
        if (shortened != null) {
            return Result(
                Verdict.LIVE_ODD_TAIL,
                shortened,
                candidate,
                "укороченное имя живой песни",
            )
        }

        // (Б1): совпадение с живой песней после нормализации.
        val normalized = normalize(candidate)
        if (normalizedNames.contains(normalized)) {
            return Result(
                Verdict.LIVE,
                null,
                candidate,
                "совпадение с живой песней после нормализации",
            )
        }

        // (Б2f): нечёткое совпадение (≥ 0,93) — почти наверняка та же песня.
        if (hasCloseMatch(normalized)) {
            return Result(
                Verdict.LIVE,
                null,
                candidate,
                "нечёткое совпадение с живой песней (≥ $FUZZY_CUTOFF)",
            )
        }

        // (Б2): настоящий сирота.
        return Result(Verdict.ORPHAN, null, candidate, "песни с таким именем нет в БД")
    }

    /**
     * Есть ли живая песня, нечётко совпадающая с именем (Б2f).
     *
     * Отбор: сначала грубый фильтр по длине (эквивалент `real_quick_ratio`
     * из `difflib`), затем коэффициент подобия.
     *
     * @param normalized нормализованное имя кандидата.
     * @return `true`, если найдено совпадение с коэффициентом ≥ [FUZZY_CUTOFF].
     */
    fun hasCloseMatch(normalized: String): Boolean {
        if (normalized.isBlank()) return false
        val len = normalized.length
        // 2*min(l1,l2)/(l1+l2) ≥ cutoff  ⇒  l2 ∈ [c/(2-c) * l1, (2-c)/c * l1]
        val minLen = Math.floor(FUZZY_CUTOFF * len / (2 - FUZZY_CUTOFF)).toInt()
        val maxLen = Math.ceil((2 - FUZZY_CUTOFF) * len / FUZZY_CUTOFF).toInt()
        val candidateCounts = charCounts(normalized)
        val sparse = ArrayList<Pair<Int, Int>>()
        for (index in candidateCounts.indices) {
            val count = candidateCounts[index]
            if (count > 0) sparse.add(index to count)
        }
        for (candidateLen in minLen..maxLen) {
            val bucket = normalizedByLength[candidateLen] ?: continue
            for (live in bucket) {
                // Быстрый отсев по мультимножеству символов (верхняя оценка
                // коэффициента подобия) — иначе полный расчёт на десятках тысяч
                // пар занимает минуты.
                var common = 0
                for ((index, count) in sparse) {
                    val liveCount = live.counts[index]
                    common += if (liveCount < count) liveCount else count
                }
                val total = len + live.name.length
                if (total == 0) continue
                if (2.0 * common / total < FUZZY_CUTOFF) continue
                if (similarity(normalized, live.name) >= FUZZY_CUTOFF) return true
            }
        }
        return false
    }

    /** Мультимножество символов имени по алфавиту [alphabet]. */
    private fun charCounts(name: String): IntArray {
        val counts = IntArray(alphabet.size)
        for (ch in name) {
            val index = alphabet[ch] ?: continue
            counts[index]++
        }
        return counts
    }
}
