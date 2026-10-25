package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.model.Song
import org.slf4j.LoggerFactory
import java.io.File
import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sqrt

/**
 * Акустическая сверка двух песен ("текущая" ↔ "кандидат в оригинал").
 *
 * Идея: настоящее переиздание/ремастер — это та же самая запись вокала, возможно со сдвигом
 * (в начало добавлена/убрана тишина) и другим уровнем/EQ. Сравниваем амплитудную огибающую
 * (RMS) вокальных стемов через нормированную кросс-корреляцию (Пирсон) — она инвариантна к
 * масштабу/смещению уровня, поэтому ремастер той же записи даёт ~100%, а разные записи — низкий %.
 *
 * Результат: процент схожести (пик корреляции) и дельта сдвига в мс (положение пика). Дельта
 * такова, что для переноса маркеров кандидата в таймлайн текущей: time_current = time_candidate + deltaMs/1000.
 */

// Результат сверки. Возвращается эндпоинтом /api/song/comparewaveform как есть (Jackson).

/**
 * DTO для waveform compare result: сериализуемое представление для API/UI.
 *
 * @see archive/docs/features/dual-db-sync.md
 */
data class WaveformCompareResultDto(
    val idAnother: Long,
    val similarityPercent: Int,
    val deltaMs: Long,
    val stemUsed: String, // "vocals" | "mix"
    val ok: Boolean,
    val error: String?,
)

/**
 * Singleton-объект Waveform Compare.
 *
 * @see archive/docs/features/dual-db-sync.md
 */
object WaveformCompare {
    private const val SAMPLE_RATE = 8000 // Гц, моно — декодируем сюда через ffmpeg
    private const val FRAME_RATE_HZ = 100 // Гц огибающей ⇒ 10 мс на кадр
    private const val SAMPLES_PER_FRAME = SAMPLE_RATE / FRAME_RATE_HZ // 80
    private const val FRAME_MS = 1000.0 / FRAME_RATE_HZ // 10 мс
    private const val MIN_OVERLAP_FRAMES = FRAME_RATE_HZ * 5 // минимум 5 с перекрытия
    private const val MAX_LAG_BASE_SEC = 45 // базовое окно поиска сдвига
    private const val MAX_LAG_MARGIN_SEC = 20 // запас поверх разницы длительностей

    /**
     * BR-122 (аудит #336, пакет `PKG-S3-23`): таймаут декодирования через ffmpeg, секунды.
     *
     * Раньше `process.waitFor()` ждал бесконечно, а `readBytes()` на stdout блокировался
     * при зависшем вводе-выводе — поток Tomcat не возвращался, и осиротевший ffmpeg
     * оставался в системе. 120 с — с запасом для длинной песни на медленном томе.
     */
    internal const val FFMPEG_TIMEOUT_SECONDS = 120L

    /** BR-122: категория логов сверки огибающих. */
    private val waveformLog = LoggerFactory.getLogger("infra.waveform.compare")

    /**
     * Верхняя граница кэша огибающих (BR-110, аудит #336).
     *
     * Раньше кэш был `ConcurrentHashMap` без вытеснения: каждая версия стема
     * (ключ содержит `lastModified`) оставалась в памяти навсегда. 64 записи —
     * это ~6 МБ при огибающей 100 Гц на 4-минутную песню (~96 КБ), что с
     * запасом покрывает «текущая + кандидаты» одного прогона сверки.
     */
    internal const val ENVELOPE_CACHE_MAX_ENTRIES = 64

    // Кэш огибающих: при "Сверить все" текущая песня декодируется один раз, повторная "Сверить" — бесплатна.
    // Ключ учитывает mtime файла, чтобы после пересчёта стема кэш инвалидировался сам.
    // accessOrder=true + removeEldestEntry — LRU, а не безграничный рост (BR-110).
    private val envelopeCache: MutableMap<String, FloatArray> =
        Collections.synchronizedMap(
            object : LinkedHashMap<String, FloatArray>(16, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FloatArray>?): Boolean =
                    size > ENVELOPE_CACHE_MAX_ENTRIES
            },
        )

    fun compareWaveforms(
        current: Song,
        candidate: Song,
    ): WaveformCompareResultDto {
        val id = candidate.id
        val useVocals = File(current.vocalsNameFlac).exists() && File(candidate.vocalsNameFlac).exists()
        val curPath = if (useVocals) current.vocalsNameFlac else current.fileAbsolutePath
        val candPath = if (useVocals) candidate.vocalsNameFlac else candidate.fileAbsolutePath
        val stemUsed = if (useVocals) "vocals" else "mix"

        if (!File(curPath).exists()) return fail(id, "Нет аудиофайла текущей песни", stemUsed)
        if (!File(candPath).exists()) return fail(id, "Нет аудиофайла кандидата", stemUsed)

        val envCur = extractEnvelope(curPath) ?: return fail(id, "Не удалось декодировать текущую песню", stemUsed)
        val envCand = extractEnvelope(candPath) ?: return fail(id, "Не удалось декодировать кандидата", stemUsed)
        if (envCur.size < MIN_OVERLAP_FRAMES || envCand.size < MIN_OVERLAP_FRAMES) {
            return fail(id, "Слишком короткое аудио для сверки", stemUsed)
        }

        val diffFrames = abs(envCur.size - envCand.size)
        val maxLag =
            minOf(
                minOf(envCur.size, envCand.size) - 1,
                maxOf(FRAME_RATE_HZ * MAX_LAG_BASE_SEC, diffFrames + FRAME_RATE_HZ * MAX_LAG_MARGIN_SEC),
            )
        val (peak, lagFrames) = crossCorrelate(envCur, envCand, maxLag)

        val percent = (maxOf(0.0, peak) * 100).roundToInt().coerceIn(0, 100)
        val deltaMs = (lagFrames * FRAME_MS).roundToLong()
        return WaveformCompareResultDto(id, percent, deltaMs, stemUsed, true, null)
    }

    private fun fail(
        id: Long,
        error: String,
        stemUsed: String,
    ) = WaveformCompareResultDto(id, 0, 0, stemUsed, false, error)

    // --- Огибающая -------------------------------------------------------------------------------

    private fun extractEnvelope(
        audioPath: String,
        frameRateHz: Int = FRAME_RATE_HZ,
    ): FloatArray? {
        val file = File(audioPath)
        if (!file.exists()) return null
        val cacheKey = "$audioPath|${file.lastModified()}|$frameRateHz"
        envelopeCache[cacheKey]?.let { return it }
        val env = computeEnvelope(audioPath) ?: return null
        synchronized(envelopeCache) {
            // BR-110: после пересчёта стема старый ключ того же пути (другой mtime)
            // больше не нужен — удаляем его, чтобы LRU не вытеснял живые записи.
            val pathPrefix = "$audioPath|"
            envelopeCache.keys.removeAll { it.startsWith(pathPrefix) }
            envelopeCache[cacheKey] = env
        }
        return env
    }

    private fun computeEnvelope(audioPath: String): FloatArray? {
        val tmp = File.createTempFile("wfcmp_", ".raw")
        // BR-122: отдельный файл под stderr/stdout ffmpeg. Раньше вывод читался из
        // `process.inputStream.readBytes()` — на зависшем вводе-выводе этот read
        // блокировался НАВСЕГДА, и таймаута не существовало вовсе.
        val procLog = File.createTempFile("wfcmp_log_", ".txt")
        try {
            val cmd =
                listOf(
                    "ffmpeg",
                    "-v",
                    "error",
                    "-y",
                    "-i",
                    audioPath,
                    "-ac",
                    "1",
                    "-ar",
                    SAMPLE_RATE.toString(),
                    "-f",
                    "s16le",
                    tmp.absolutePath,
                )
            // Вывод уходит в файл: у процесса нет stdout-трубы, которую надо было бы
            // «осужать» — значит, он не может зависнуть на полном буфере, и таймаут
            // реально срабатывает.
            val process =
                ProcessBuilder(cmd)
                    .redirectErrorStream(true)
                    .redirectOutput(procLog)
                    .start()
            val exit =
                try {
                    if (!process.waitFor(FFMPEG_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                        // BR-122: ffmpeg не уложился в таймаут — убиваем и не оставляем
                        // осиротевший процесс и поток Tomcat в ожидании.
                        process.destroyForcibly()
                        process.waitFor(5, TimeUnit.SECONDS)
                        waveformLog.warn(
                            "computeEnvelope: ffmpeg не завершился за {} с (файл='{}'), процесс принудительно остановлен",
                            FFMPEG_TIMEOUT_SECONDS,
                            audioPath,
                        )
                        return null
                    }
                    process.exitValue()
                } finally {
                    if (process.isAlive) process.destroyForcibly()
                }
            if (exit != 0 || !tmp.exists() || tmp.length() == 0L) {
                waveformLog.warn(
                    "computeEnvelope: ffmpeg завершился с кодом {} для '{}': {}",
                    exit,
                    audioPath,
                    runCatching { procLog.readText().take(500) }.getOrDefault(""),
                )
                return null
            }

            val bytes = tmp.readBytes()
            val nFrames = (bytes.size / 2) / SAMPLES_PER_FRAME
            if (nFrames == 0) return null

            val env = FloatArray(nFrames)
            var bi = 0
            for (f in 0 until nFrames) {
                var sumSq = 0.0
                for (s in 0 until SAMPLES_PER_FRAME) {
                    val lo = bytes[bi].toInt() and 0xFF
                    val hi = bytes[bi + 1].toInt() // старший байт — знаковый (little-endian s16)
                    val sample = (hi shl 8) or lo // корректное знаковое 16-битное значение
                    bi += 2
                    val v = sample.toDouble()
                    sumSq += v * v
                }
                env[f] = sqrt(sumSq / SAMPLES_PER_FRAME).toFloat()
            }
            return env
        } catch (e: Exception) {
            waveformLog.warn("computeEnvelope: не удалось декодировать '{}': {}", audioPath, e.message)
            return null
        } finally {
            // BR-122: временные файлы удаляются при ЛЮБОМ исходе (успех, ошибка,
            // таймаут) — раньше осиротевший `.raw` оставался на диске.
            tmp.delete()
            procLog.delete()
        }
    }

    // --- Нормированная кросс-корреляция ----------------------------------------------------------

    /**
     * a = текущая, b = кандидат. Для каждого лага L считаем Пирсоновскую корреляцию по перекрытию,
     * где a[i] сопоставляется b[i-L]. Возвращает (пик корреляции, лаг в кадрах с суб-кадровой
     * параболической интерполяцией). Пик при лаге L означает: событие текущей в позиции i совпадает
     * с событием кандидата в i-L ⇒ кандидат надо сдвинуть на +L кадров, чтобы лечь на текущую.
     */
    private fun crossCorrelate(
        a: FloatArray,
        b: FloatArray,
        maxLag: Int,
    ): Pair<Double, Double> {
        val lagLo = -maxLag
        val lagHi = maxLag
        val vals = DoubleArray(lagHi - lagLo + 1) { Double.NEGATIVE_INFINITY }
        var bestVal = Double.NEGATIVE_INFINITY
        var bestLag = 0

        for (L in lagLo..lagHi) {
            val iStart = maxOf(0, L)
            val iEnd = minOf(a.size - 1, b.size - 1 + L)
            val n = iEnd - iStart + 1
            if (n < MIN_OVERLAP_FRAMES) continue

            var sa = 0.0
            var sb = 0.0
            for (i in iStart..iEnd) {
                sa += a[i]
                sb += b[i - L]
            }
            val ma = sa / n
            val mb = sb / n

            var num = 0.0
            var da = 0.0
            var db = 0.0
            for (i in iStart..iEnd) {
                val xa = a[i] - ma
                val xb = b[i - L] - mb
                num += xa * xb
                da += xa * xa
                db += xb * xb
            }
            val denom = sqrt(da * db)
            val corr = if (denom > 0.0) num / denom else 0.0
            vals[L - lagLo] = corr
            if (corr > bestVal) {
                bestVal = corr
                bestLag = L
            }
        }

        if (bestVal == Double.NEGATIVE_INFINITY) return 0.0 to 0.0

        // Параболическая интерполяция по трём точкам вокруг пика — суб-кадровая точность дельты.
        var refinedLag = bestLag.toDouble()
        val bi = bestLag - lagLo
        if (bi in 1 until vals.size - 1) {
            val ym1 = vals[bi - 1]
            val y0 = vals[bi]
            val yp1 = vals[bi + 1]
            if (ym1.isFinite() && yp1.isFinite()) {
                val d = ym1 - 2 * y0 + yp1
                if (d != 0.0) {
                    val shift = 0.5 * (ym1 - yp1) / d
                    if (shift > -1.0 && shift < 1.0) refinedLag = bestLag + shift
                }
            }
        }
        return bestVal to refinedLag
    }
}
