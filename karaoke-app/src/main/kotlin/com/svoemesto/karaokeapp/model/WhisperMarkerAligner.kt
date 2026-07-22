package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.getSyllables
import com.svoemesto.karaokeapp.haveVowel
import com.svoemesto.karaokeapp.services.WhisperWordDto
import kotlin.math.max
import kotlin.math.min

private const val COLOR_FIRST_SYLLABLE = "#008000"
private const val COLOR_SYLLABLE = "#D2691E"
private const val COLOR_ENDOFLINE = "#FF0000"

// Ниже этой уверенности распознанное слово не используется как "якорь" времени — Whisper мог
// просто угадать мимо, и доверять его времени в этом случае опаснее, чем интерполировать.
private const val MIN_WORD_CONFIDENCE = 0.3

// Пороги для распознавания ВСТАВОК (Whisper услышал то, чего нет в официальном тексте, см.
// WhisperMarkerAligner.reconcile*) — сознательно строже, чем MIN_WORD_CONFIDENCE для обычных
// якорей: ложная вставка портит текст/датасет, а не просто одну метку времени. Одиночное
// низкоуверенное непойманное слово чаще ASR-шум/галлюцинация, чем реальная вставка — требуем
// подряд идущих слов и более высокую уверенность на каждом. Оба порога — кандидаты на калибровку
// по факту первого реального прогона (как и весь остальной alignment-ml пайплайн).
private const val MIN_INSERTION_CONFIDENCE = 0.6
private const val MIN_INSERTION_RUN_LENGTH = 2

/**
 * Сопоставляет word-level результат Whisper (см. WhisperAsrService) с уже введённым текстом песни
 * и строит слоговые маркеры. Whisper не даёт точности выше слова (см. решение в плане фичи), поэтому
 * время внутри слова распределяется по слогам пропорционально их длине в символах.
 */
object WhisperMarkerAligner {
    private data class TargetWord(
        val lineIndex: Int,
        val syllables: List<String>,
        val normalized: String,
    )

    private data class RecognizedWord(
        val raw: String,
        val start: Double,
        val end: Double,
        val confidence: Double,
        val normalized: String,
    )

    private data class Anchor(
        val targetIndex: Int,
        val start: Double,
        val end: Double,
    )

    // Подряд идущие recognized-слова, не сопоставленные ни с одним target-словом ("else -> j--" в
    // traceback alignWords) - кандидаты на вставку (Whisper услышал то, чего нет в тексте). Все
    // слова одного run-а всегда идут подряд и относятся к одной и той же позиции - i в traceback
    // между ними не менялся (см. alignWords). afterTargetIndex = -1 значит "перед первым словом".
    private data class RecognizedRun(
        val afterTargetIndex: Int,
        val words: List<RecognizedWord>,
    )

    data class ReconciledSyllable(
        val label: String,
        val timeMs: Double,
        val hasGroundTruth: Boolean,
    )

    data class ReconciledResult(
        val text: String,
        val syllables: List<ReconciledSyllable>,
    )

    fun alignToMarkers(
        sourceText: String,
        whisperWords: List<WhisperWordDto>,
    ): List<SourceMarker> {
        val targetWords = buildTargetWords(sourceText.replace("\r\n", "\n").split("\n"))
        if (targetWords.isEmpty()) return emptyList()

        val recognizedWords = buildRecognizedWords(whisperWords)
        if (recognizedWords.isEmpty()) return emptyList()

        val (anchors, _) = alignWords(targetWords, recognizedWords)
        if (anchors.isEmpty()) return emptyList()

        val wordTimes = interpolateWordTimes(targetWords, anchors)
        return buildMarkers(targetWords, wordTimes)
    }

    private fun buildRecognizedWords(whisperWords: List<WhisperWordDto>): List<RecognizedWord> =
        whisperWords
            .filter { it.word.isNotBlank() }
            .map {
                RecognizedWord(
                    raw = it.word.trim(),
                    start = it.start,
                    end = it.end,
                    confidence = it.confidence,
                    normalized = normalize(it.word),
                )
            }.filter { it.normalized.isNotEmpty() }

    // Согласование официального текста с Whisper для НОВОЙ/ещё не размеченной песни - нет
    // существующих маркеров, поэтому нет и понятия hasGroundTruth: просто возвращает текст,
    // дополненный подтверждёнными вставками (см. RecognizedRun/acceptInsertionRuns), готовый как
    // вход для forced-alignment (align.py/serve.py) взамен нынешней Whisper-кнопки в SubsEdit.
    fun reconcileText(
        sourceText: String,
        whisperWords: List<WhisperWordDto>,
    ): String {
        val lines = sourceText.replace("\r\n", "\n").split("\n")
        val targetWords = buildTargetWords(lines)
        if (targetWords.isEmpty()) return sourceText

        val recognizedWords = buildRecognizedWords(whisperWords)
        if (recognizedWords.isEmpty()) return sourceText

        val (_, runs) = alignWords(targetWords, recognizedWords)
        val insertions = acceptInsertionRuns(runs)
        if (insertions.isEmpty()) return sourceText

        return rebuildText(targetWords, insertions)
    }

    // Согласование для УЖЕ РАЗМЕЧЕННОЙ песни (датасет, см. ExportAlignmentDataset.kt) - existingMarkers
    // это реальные, вручную выставленные маркеры (markertype=syllables, отсортированы по времени).
    // Возвращает null, если их количество не совпадает с числом слогов в sourceText (тот же случай
    // "расхождение слоговой разбивки", что уже встречается ~10-14% случаев в evaluate.py/chunking.py) -
    // безопаснее пропустить согласование для такой песни целиком, чем гадать про сопоставление позиций.
    fun reconcileWithGroundTruth(
        sourceText: String,
        existingMarkers: List<SourceMarker>,
        whisperWords: List<WhisperWordDto>,
    ): ReconciledResult? {
        val lines = sourceText.replace("\r\n", "\n").split("\n")
        val targetWords = buildTargetWords(lines)
        if (targetWords.isEmpty()) return null

        val realSyllables = existingMarkers.filter { it.markertype == Markertype.SYLLABLES.value }.sortedBy { it.time }
        if (realSyllables.size != targetWords.sumOf { it.syllables.size }) return null

        val recognizedWords = buildRecognizedWords(whisperWords)
        val insertions =
            if (recognizedWords.isEmpty()) {
                emptyList()
            } else {
                val (_, runs) = alignWords(targetWords, recognizedWords)
                acceptInsertionRuns(runs)
            }

        val syllables = mutableListOf<ReconciledSyllable>()
        var realSyllableIndex = 0
        val insertionsByAnchor = insertions.groupBy { it.afterTargetIndex }

        insertionsByAnchor[-1]?.forEach { run -> syllables.addAll(insertionSyllables(run)) }
        targetWords.forEachIndexed { wordIndex, word ->
            word.syllables.forEach { syllable ->
                val real = realSyllables[realSyllableIndex]
                syllables.add(ReconciledSyllable(label = syllable, timeMs = real.time * 1000, hasGroundTruth = true))
                realSyllableIndex++
            }
            insertionsByAnchor[wordIndex]?.forEach { run -> syllables.addAll(insertionSyllables(run)) }
        }

        return ReconciledResult(text = rebuildText(targetWords, insertions), syllables = syllables)
    }

    // Внутри вставки время делится пропорционально длине символов слога - тот же приём, что и в
    // buildMarkers для обычных слов. Границы спана - от начала первого до конца последнего
    // recognized-слова run-а (её собственный Whisper-тайминг, единственный доступный для вставки).
    private fun insertionSyllables(run: RecognizedRun): List<ReconciledSyllable> {
        val result = mutableListOf<ReconciledSyllable>()
        run.words.forEach { word ->
            val syllables = getSyllables(word.raw).map { it.removeSuffix("_") }
            val totalChars = syllables.sumOf { it.length }.coerceAtLeast(1)
            val duration = (word.end - word.start).coerceAtLeast(0.0)
            var cursor = word.start
            syllables.forEach { syllable ->
                val share = duration * syllable.length / totalChars
                result.add(ReconciledSyllable(label = syllable, timeMs = cursor * 1000, hasGroundTruth = false))
                cursor += share
            }
        }
        return result
    }

    // Отбирает только надёжные run-ы (см. MIN_INSERTION_CONFIDENCE/MIN_INSERTION_RUN_LENGTH) -
    // одиночное низкоуверенное слово чаще ASR-шум, чем реальная вставка.
    private fun acceptInsertionRuns(runs: List<RecognizedRun>): List<RecognizedRun> =
        runs.filter { run ->
            run.words.size >= MIN_INSERTION_RUN_LENGTH && run.words.all { it.confidence >= MIN_INSERTION_CONFIDENCE }
        }

    // Собирает текст заново: официальные слова + принятые вставки сразу после того target-слова, к
    // которому они привязаны (afterTargetIndex) - перенос строки ставится там же, где менялся
    // lineIndex в оригинале, вставка остаётся в строке своего "якоря", а не разрывает её пополам.
    private fun rebuildText(
        targetWords: List<TargetWord>,
        insertions: List<RecognizedRun>,
    ): String {
        val insertionsByAnchor = insertions.groupBy { it.afterTargetIndex }
        val lines = mutableListOf<StringBuilder>()
        var currentLine = StringBuilder()
        lines.add(currentLine)

        fun appendWord(text: String) {
            if (currentLine.isNotEmpty()) currentLine.append(' ')
            currentLine.append(text)
        }

        insertionsByAnchor[-1]?.forEach { run -> run.words.forEach { appendWord(it.raw) } }
        targetWords.forEachIndexed { wordIndex, word ->
            appendWord(word.syllables.joinToString(""))
            insertionsByAnchor[wordIndex]?.forEach { run -> run.words.forEach { appendWord(it.raw) } }

            val isLastWord = wordIndex == targetWords.size - 1
            if (!isLastWord && targetWords[wordIndex + 1].lineIndex != word.lineIndex) {
                currentLine = StringBuilder()
                lines.add(currentLine)
            }
        }
        return lines.joinToString("\n") { it.toString() }
    }

    private val wordRegex = Regex("""\S+""")

    private data class RawSyllable(
        var text: String,
        val wordIndex: Int,
    )

    // Разбивает текст на "целевые слова" с их слогами — должно 1:1 совпадать с тем, как frontend
    // (SubsEdit.vue getSyllables computed) реально нарезал слоги для уже размеченных песен, иначе
    // расхождение в счётчике слогов на строку молча сдвигает подписи ВСЕХ последующих маркеров при
    // загрузке в редактор (updateMarkersBySyllables сопоставляет маркеры с sourceSyllables по индексу,
    // без учёта содержимого).
    //
    // ВАЖНО: getSyllables(text) (Utils.kt) сама разбивает text на слова через regexWords.find(text)
    // (не findAll!) — при многословном входе она молча обрабатывает только ПЕРВОЕ слово. Поэтому слова
    // выделяем здесь сами (findAll), зовём getSyllables() ПОШТУЧНО на каждом слове (на одном слове её
    // "найти первое" работает корректно) — но проход слияния слогов без гласной (частицы вроде "в",
    // "с", "к" без гласной приклеиваются к следующему слову) делаем ОДИН РАЗ по всей строке, а не
    // отдельно на каждом слове — именно так это делает и JS-версия, и оригинальная (корректная для
    // одного слова) Kotlin getSyllables.
    private fun buildTargetWords(lines: List<String>): List<TargetWord> {
        val result = mutableListOf<TargetWord>()
        lines.forEachIndexed { lineIndex, line ->
            if (line.isBlank()) return@forEachIndexed

            val flat = mutableListOf<RawSyllable>()
            wordRegex.findAll(line).forEachIndexed { wordIndex, match ->
                getSyllables(match.value).forEach { syl ->
                    flat.add(RawSyllable(syl.removeSuffix("_"), wordIndex))
                }
            }

            // Условие смешивает ДВЕ ветки из frontend getSyllables (SubsEdit.vue): там последний
            // элемент строки мержится назад ВСЕГДА, если без гласной (не только буквальное "-_") —
            // через ИЛИ, а не через И. Раньше здесь стояло "i == last && text == '-'" (И) — это
            // сильнее фронтенда и оставляло, например, повисшую запятую в конце строки как
            // самостоятельный "слог" (в отличие от фронтенда, где она смержилась бы назад). i != 0
            // — защита от IndexOutOfBounds на однословной строке без гласной (в JS это undefined,
            // не падение — тут безопаснее просто не мержить).
            var i = 0
            while (i < flat.size) {
                val piece = flat[i]
                if (!piece.text.haveVowel()) {
                    if (i != 0 && (i == flat.size - 1 || piece.text == "-")) {
                        flat[i - 1].text += piece.text
                        flat.removeAt(i)
                        i--
                    } else if (i < flat.size - 2) {
                        flat[i + 1].text = piece.text + flat[i + 1].text
                        flat.removeAt(i)
                        i--
                    }
                }
                i++
            }

            flat
                .groupBy { it.wordIndex }
                .toSortedMap()
                .forEach { (_, syllables) ->
                    addTargetWord(result, lineIndex, syllables.map { it.text })
                }
        }
        return result
    }

    private fun addTargetWord(
        result: MutableList<TargetWord>,
        lineIndex: Int,
        syllables: List<String>,
    ) {
        if (syllables.isEmpty()) return
        val normalized = normalize(syllables.joinToString(""))
        if (normalized.isNotEmpty()) {
            result.add(TargetWord(lineIndex, syllables.toList(), normalized))
        }
    }

    private fun normalize(text: String): String =
        text
            .lowercase()
            .replace("ё", "е")
            .replace(Regex("[^a-zа-я0-9]"), "")

    private fun levenshtein(
        a: String,
        b: String,
    ): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = min(min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost)
            }
        }
        return dp[a.length][b.length]
    }

    // >= 1 считается надёжным совпадением (используется и при выравнивании, и при traceback).
    private fun wordScore(
        a: String,
        b: String,
    ): Int {
        if (a.isEmpty() || b.isEmpty()) return -2
        if (a == b) return 2
        val dist = levenshtein(a, b)
        val threshold = max(1, (max(a.length, b.length) * 0.3).toInt())
        return if (dist <= threshold) 1 else -2
    }

    // Needleman-Wunsch: глобальное выравнивание targetWords <-> recognizedWords. Счёт: точное
    // совпадение 2, похожее (Левенштейн <= 30% длины) 1, иначе -2; гэп (пропуск с любой стороны) -1.
    // n/m - обычно несколько сотен слов на песню, O(n*m) с большим запасом укладывается в
    // интерактивный отклик кнопки.
    // Возвращает и якоря (для тайминга, как раньше), и RecognizedRun - подряд идущие recognized-слова,
    // не сопоставленные ни с одним target-словом ("else" ниже) - кандидаты на вставку (см.
    // reconcileText/reconcileWithGroundTruth). NB: DP иногда предпочтёт "плохую" диагональ (полное
    // несовпадение, matchScore=-2) отдельным gap-ходам с тем же суммарным счётом (-1-1=-2) - в этом
    // редком случае вставка окажется "молча" поглощена как несостоявшийся анchor и не будет замечена;
    // это ограничение текущей схемы весов, не баг конкретно детектора вставок.
    private fun alignWords(
        targetWords: List<TargetWord>,
        recognizedWords: List<RecognizedWord>,
    ): Pair<List<Anchor>, List<RecognizedRun>> {
        val n = targetWords.size
        val m = recognizedWords.size
        val gapPenalty = -1
        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in 0..n) dp[i][0] = i * gapPenalty
        for (j in 0..m) dp[0][j] = j * gapPenalty
        for (i in 1..n) {
            for (j in 1..m) {
                val matchScore = wordScore(targetWords[i - 1].normalized, recognizedWords[j - 1].normalized)
                val diag = dp[i - 1][j - 1] + matchScore
                val up = dp[i - 1][j] + gapPenalty
                val left = dp[i][j - 1] + gapPenalty
                dp[i][j] = max(diag, max(up, left))
            }
        }

        val anchors = mutableListOf<Anchor>()
        // (afterTargetIndex, recognizedWordIndex), в ОБРАТНОМ (traceback) порядке - переворачивается
        // вместе с anchors ниже.
        val skipped = mutableListOf<Pair<Int, Int>>()
        var i = n
        var j = m
        while (i > 0 && j > 0) {
            val matchScore = wordScore(targetWords[i - 1].normalized, recognizedWords[j - 1].normalized)
            val diag = dp[i - 1][j - 1] + matchScore
            when {
                dp[i][j] == diag -> {
                    if (matchScore >= 1 && recognizedWords[j - 1].confidence >= MIN_WORD_CONFIDENCE) {
                        anchors.add(Anchor(i - 1, recognizedWords[j - 1].start, recognizedWords[j - 1].end))
                    }
                    i--
                    j--
                }
                dp[i][j] == dp[i - 1][j] + gapPenalty -> i--
                else -> {
                    skipped.add((i - 1) to (j - 1))
                    j--
                }
            }
        }
        // Recognized-слова ДО самого первого сопоставления (i дошло до 0 раньше j) - тоже кандидаты,
        // привязаны к позиции "перед первым target-словом" (afterTargetIndex = -1).
        while (j > 0) {
            skipped.add(-1 to (j - 1))
            j--
        }

        anchors.reverse()
        skipped.reverse()

        val runs = mutableListOf<RecognizedRun>()
        var currentAnchor: Int? = null
        var currentWords = mutableListOf<RecognizedWord>()
        skipped.forEach { (afterTargetIndex, recognizedIndex) ->
            if (currentAnchor != null && currentAnchor != afterTargetIndex) {
                runs.add(RecognizedRun(currentAnchor!!, currentWords))
                currentWords = mutableListOf()
            }
            currentAnchor = afterTargetIndex
            currentWords.add(recognizedWords[recognizedIndex])
        }
        if (currentAnchor != null) runs.add(RecognizedRun(currentAnchor!!, currentWords))

        return anchors to runs
    }

    // Для каждого целевого слова вычисляет (start, end): у якорей - реальное время Whisper, у
    // остальных - линейная интерполяция по накопленной длине символов между соседними якорями
    // (и экстраполяция по локальной скорости символ/сек на краях, до первого и после последнего якоря).
    private fun interpolateWordTimes(
        targetWords: List<TargetWord>,
        anchors: List<Anchor>,
    ): List<Pair<Double, Double>> {
        val charLengths = targetWords.map { it.syllables.sumOf { s -> s.length }.coerceAtLeast(1) }
        val result = arrayOfNulls<Pair<Double, Double>>(targetWords.size)
        anchors.forEach { result[it.targetIndex] = Pair(it.start, it.end) }

        for (anchorPos in anchors.indices) {
            val anchor = anchors[anchorPos]
            val prevAnchor = anchors.getOrNull(anchorPos - 1)
            val rangeStart = (prevAnchor?.targetIndex ?: -1) + 1
            val rangeEnd = anchor.targetIndex - 1
            if (rangeEnd < rangeStart) continue

            val totalChars = (rangeStart..rangeEnd).sumOf { charLengths[it] }.coerceAtLeast(1)
            val spanEndTime = anchor.start
            val spanStartTime =
                prevAnchor?.end ?: run {
                    // Перед первым якорем нет опоры слева - экстраполируем той же плотностью,
                    // что и внутри самого диапазона (символы поровну делят время до якоря).
                    val rate = if (totalChars > 0) spanEndTime / totalChars else 0.0
                    spanEndTime - totalChars * rate
                }
            var cursor = spanStartTime
            val span = (spanEndTime - spanStartTime).coerceAtLeast(0.0)
            for (idx in rangeStart..rangeEnd) {
                val share = span * charLengths[idx] / totalChars
                result[idx] = Pair(cursor, cursor + share)
                cursor += share
            }
        }

        val lastAnchor = anchors.lastOrNull()
        if (lastAnchor != null && lastAnchor.targetIndex < targetWords.size - 1) {
            val rangeStart = lastAnchor.targetIndex + 1
            val rangeEnd = targetWords.size - 1
            val prevAnchor = anchors.getOrNull(anchors.size - 2)
            val rate =
                if (prevAnchor != null && lastAnchor.start > prevAnchor.end) {
                    val chars = (prevAnchor.targetIndex + 1..lastAnchor.targetIndex).sumOf { charLengths[it] }.coerceAtLeast(1)
                    (lastAnchor.start - prevAnchor.end) / chars
                } else {
                    0.35 // грубая эвристика (сек/символ), если экстраполировать не от чего
                }
            var cursor = lastAnchor.end
            for (idx in rangeStart..rangeEnd) {
                val share = charLengths[idx] * rate
                result[idx] = Pair(cursor, cursor + share)
                cursor += share
            }
        }

        // Финальная защита от отрицательных/немонотонных значений на краях грубой экстраполяции.
        var prevEnd = 0.0
        for (idx in result.indices) {
            val pair = result[idx] ?: Pair(prevEnd, prevEnd)
            val s = max(pair.first, prevEnd)
            val e = max(pair.second, s)
            result[idx] = Pair(s, e)
            prevEnd = e
        }

        return result.map { it!! }
    }

    private fun buildMarkers(
        targetWords: List<TargetWord>,
        wordTimes: List<Pair<Double, Double>>,
    ): List<SourceMarker> {
        val markers = mutableListOf<SourceMarker>()
        targetWords.forEachIndexed { wordIndex, word ->
            val (wordStart, wordEnd) = wordTimes[wordIndex]
            val totalChars = word.syllables.sumOf { it.length }.coerceAtLeast(1)
            val duration = (wordEnd - wordStart).coerceAtLeast(0.0)
            val isFirstOfLine = wordIndex == 0 || targetWords[wordIndex - 1].lineIndex != word.lineIndex
            var cursor = wordStart
            word.syllables.forEachIndexed { syllableIndex, syllable ->
                val share = duration * syllable.length / totalChars
                markers.add(
                    SourceMarker(
                        time = cursor,
                        label = syllable,
                        color = if (isFirstOfLine && syllableIndex == 0) COLOR_FIRST_SYLLABLE else COLOR_SYLLABLE,
                        position = "bottom",
                        markertype = Markertype.SYLLABLES.value,
                    ),
                )
                cursor += share
            }

            val isLastOfLine = wordIndex == targetWords.size - 1 || targetWords[wordIndex + 1].lineIndex != word.lineIndex
            if (isLastOfLine && wordIndex != targetWords.size - 1) {
                markers.add(
                    SourceMarker(
                        time = wordEnd,
                        label = "",
                        color = COLOR_ENDOFLINE,
                        position = "bottom",
                        markertype = Markertype.ENDOFLINE.value,
                    ),
                )
            }
        }
        return markers.sortedBy { it.time }
    }
}
