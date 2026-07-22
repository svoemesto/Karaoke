package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.getSyllables
import com.svoemesto.karaokeapp.services.WhisperWordDto
import kotlin.math.max
import kotlin.math.min

private const val COLOR_FIRST_SYLLABLE = "#008000"
private const val COLOR_SYLLABLE = "#D2691E"
private const val COLOR_ENDOFLINE = "#FF0000"

// Ниже этой уверенности распознанное слово не используется как "якорь" времени — Whisper мог
// просто угадать мимо, и доверять его времени в этом случае опаснее, чем интерполировать.
private const val MIN_WORD_CONFIDENCE = 0.3

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

    fun alignToMarkers(
        sourceText: String,
        whisperWords: List<WhisperWordDto>,
    ): List<SourceMarker> {
        val lines = sourceText.replace("\r\n", "\n").split("\n")
        val targetWords = buildTargetWords(lines)
        if (targetWords.isEmpty()) return emptyList()

        val recognizedWords =
            whisperWords
                .filter { it.word.isNotBlank() }
                .map {
                    RecognizedWord(
                        start = it.start,
                        end = it.end,
                        confidence = it.confidence,
                        normalized = normalize(it.word),
                    )
                }.filter { it.normalized.isNotEmpty() }
        if (recognizedWords.isEmpty()) return emptyList()

        val anchors = alignWords(targetWords, recognizedWords)
        if (anchors.isEmpty()) return emptyList()

        val wordTimes = interpolateWordTimes(targetWords, anchors)
        return buildMarkers(targetWords, wordTimes)
    }

    // Разбивает текст на "целевые слова" с их слогами через getSyllables (Utils.kt) — ту же слоговую
    // разбивку, что использует весь остальной проект. Граница слова закодирована в самой разбивке:
    // последний слог слова оканчивается на "_".
    private fun buildTargetWords(lines: List<String>): List<TargetWord> {
        val result = mutableListOf<TargetWord>()
        lines.forEachIndexed { lineIndex, line ->
            if (line.isBlank()) return@forEachIndexed
            var current = mutableListOf<String>()
            getSyllables(line).forEach { syl ->
                val isWordEnd = syl.endsWith("_")
                current.add(syl.removeSuffix("_"))
                if (isWordEnd) {
                    addTargetWord(result, lineIndex, current)
                    current = mutableListOf()
                }
            }
            addTargetWord(result, lineIndex, current)
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
    private fun alignWords(
        targetWords: List<TargetWord>,
        recognizedWords: List<RecognizedWord>,
    ): List<Anchor> {
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
                else -> j--
            }
        }
        anchors.reverse()
        return anchors
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
