package com.svoemesto.karaokeapp.services

import com.svoemesto.karaokeapp.model.Settings
import com.svoemesto.karaokeapp.runCommand
import java.io.File
import java.sql.Timestamp
import java.time.Instant

/**
 * Часть Этапа 1 (MVP) рендера видео mp4 из онлайн-плеера (см. DEVELOPMENT.md "Рендер видео MP4 из
 * онлайн-плеера" и [PlayerMp4RenderService]). Собирает JPEG-секвенцию кадров + микс аудио-стемов в
 * итоговый mp4 через ffmpeg (уже используется этим же способом — ProcessBuilder-обёртка
 * [runCommand] — во множестве других заданий очереди, например FF_720_KAR/FF_MP3_ACCOMPANIMENT в
 * KaraokeProcess.kt).
 *
 * Параметры кодека — дефолты как у MLT-консьюмера (mlt/Consumer.kt): libx265/CRF15/ultrafast,
 * AAC 160k/2 канала — переопределяемы через параметры функции.
 */

/**
 * Singleton-объект Player Mp4Mux Service.
 *
 * @see docs/features/async-process-queue.md
 */
object PlayerMp4MuxService {
    // Путь к flac-стему + линейный множитель громкости (0 = тишина, 1 = как есть в исходнике).
    //
    // ВАЖНО (сознательное упрощение MVP): это НЕ формат громкости MLT (Settings.propAudioVolumeOn/
    // Off/Custom, "timecode=dB", с посекундными "unmute"-окнами вокала для версии Karaoke,
    // Settings.sourceUnmute) — воспроизводить эту огибающую под ffmpeg не требуется для цели Этапа 1
    // ("экспорт плеера как есть"): сам онлайн-плеер по умолчанию держит постоянный баланс
    // accVol=100/vocVol=0 (см. конструктор KaraokePlayer.js), без вокальных "окон" — тот же баланс
    // воспроизводится и здесь. Полная параметризация под per-версийные MLT-огибающие громкости —
    // задача следующего этапа (встройка в очередь/несколько версий Chords/Tabs).
    data class AudioTrack(
        val flacPath: String,
        val gain: Double,
    )

    /** Состав/громкости по умолчанию — как у MLT-версии Karaoke: аккомпанемент слышен, вокал приглушён. */
    fun defaultTracks(settings: Settings): List<AudioTrack> =
        listOf(
            AudioTrack(settings.accompanimentNameFlac, 1.0),
            AudioTrack(settings.vocalsNameFlac, 0.0),
        )

    /** Микс аудио-стемов для конкретной версии рендера. */
    fun tracksForVersion(
        settings: Settings,
        version: RenderVersion,
    ): List<AudioTrack> =
        when (version) {
            RenderVersion.LYRICS ->
                listOf(
                    AudioTrack(settings.accompanimentNameFlac, 1.0),
                    AudioTrack(settings.vocalsNameFlac, 1.0),
                )
            RenderVersion.KARAOKE,
            RenderVersion.CHORDS,
            RenderVersion.TABS,
            RenderVersion.DEMO,
            ->
                listOf(
                    AudioTrack(settings.accompanimentNameFlac, 1.0),
                    AudioTrack(settings.vocalsNameFlac, 0.0),
                )
        }

    fun mixAndMux(
        framesDir: File,
        fps: Int,
        preroll: Double,
        audioTracks: List<AudioTrack>,
        outputPath: String,
        videoCodec: String = "libx265",
        crf: Int = 15,
        audioBitrateKbps: Int = 160,
        audioChannels: Int = 2,
        totalDurationSeconds: Double = 0.0,
        demoFragmentStart: Double? = null,
        demoFragmentEnd: Double? = null,
        demoFadeInSeconds: Double? = null,
        onProgress: ((Int) -> Unit)? = null,
    ) {
        require(audioTracks.isNotEmpty()) { "audioTracks не может быть пустым" }
        for (t in audioTracks) require(File(t.flacPath).exists()) { "Аудио-стем не найден: ${t.flacPath}" }

        // Задержка в мс на preroll (сплэш) — плеер тоже не проигрывает звук раньше _preroll, поэтому
        // звук в mp4 должен начинаться на том же смещении, что и в браузере.
        val delayMs = Math.round(preroll * 1000)

        val filterParts = mutableListOf<String>()
        val mixLabels = mutableListOf<String>()
        audioTracks.forEachIndexed { i, t ->
            // Входной индекс 0 занят JPEG-секвенцией (см. -framerate/-i ниже) — аудио-входы идут с 1.
            val srcIdx = i + 1
            val label = "a$i"
            // Для DEMO: atrim + setpts, чтобы обрезать аудио до фрагмента.
            val trimFilter =
                if (demoFragmentStart != null && demoFragmentEnd != null) {
                    "atrim=start=$demoFragmentStart:end=$demoFragmentEnd,asetpts=PTS-STARTPTS,"
                } else {
                    ""
                }
            // Для DEMO: fade-in/out аудио (зеркалит клиентский fade-in из KaraokePlayer.js).
            // afade применяется ПОСЛЕ atrim/asetpts (тайминг относительно обрезанного фрагмента)
            // и ПЕРЕД adelay (fade в домене аудио-времени, не выходного).
            val fadeFilter =
                if (demoFragmentStart != null && demoFragmentEnd != null && (demoFadeInSeconds ?: 0.0) > 0.0) {
                    val trimmedDur = demoFragmentEnd - demoFragmentStart
                    val fadeInDur = demoFadeInSeconds!!
                    val fadeOutStart = trimmedDur - 1.0 // fade-out в последнюю 1 секунду
                    "afade=t=in:st=0:d=$fadeInDur,afade=t=out:st=$fadeOutStart:d=1,"
                } else {
                    ""
                }
            // all=1 — задержка применяется ко всем каналам источника независимо от их числа (моно/стерео).
            filterParts.add("[$srcIdx:a]${trimFilter}${fadeFilter}adelay=delays=$delayMs:all=1,volume=${t.gain}[$label]")
            mixLabels.add("[$label]")
        }
        filterParts.add("${mixLabels.joinToString("")}amix=inputs=${audioTracks.size}:duration=longest:dropout_transition=0[aout]")
        val filterComplex = filterParts.joinToString(";")

        val args =
            mutableListOf(
                "ffmpeg",
                "-y",
                "-framerate",
                fps.toString(),
                "-i",
                "${framesDir.absolutePath}/frame_%06d.jpg",
            )
        audioTracks.forEach {
            args.add("-i")
            args.add(it.flacPath)
        }
        args.addAll(
            listOf(
                "-filter_complex",
                filterComplex,
                "-map",
                "0:v",
                "-map",
                "[aout]",
                "-c:v",
                videoCodec,
                "-crf",
                crf.toString(),
                "-preset",
                "ultrafast",
                "-pix_fmt",
                "yuv420p",
                "-c:a",
                "aac",
                "-b:a",
                "${audioBitrateKbps}k",
                "-ac",
                audioChannels.toString(),
                // DEMO: не使用 -shortest, чтобы 10-секундный end screen не обрезался
                *(if (demoFragmentStart == null) arrayOf("-shortest") else emptyArray()),
                "-progress",
                "pipe:1",
                outputPath,
            ),
        )

        log("Запускаем ffmpeg mux -> $outputPath")

        val pb = ProcessBuilder(args)
        pb.redirectErrorStream(true)
        val process = pb.start()

        // Парсим stdout ffmpeg с флагом -progress pipe:1:
        // ffmpeg выводит блоки вида "out_time_ms=12345678\nprogress=continue".
        // out_time_ms — текущая позиция кодирования в микросекундах; делим на totalDuration*1e6 → процент.
        val totalDurationMs = totalDurationSeconds * 1_000_000
        val readerThread =
            Thread {
                val reader = process.inputStream.bufferedReader()
                var currentOutTimeMs = 0L
                reader.forEachLine { line ->
                    if (line.startsWith("out_time_ms=")) {
                        currentOutTimeMs = line.substringAfter("=").trim().toLongOrNull() ?: 0L
                        if (totalDurationMs > 0) {
                            val pct = ((currentOutTimeMs * 100) / totalDurationMs).toInt().coerceIn(0, 99)
                            onProgress?.invoke(pct)
                        }
                    }
                }
                onProgress?.invoke(100)
            }
        readerThread.isDaemon = true
        readerThread.name = "ffmpeg-progress-reader"
        readerThread.start()

        process.waitFor()
        readerThread.join(3000)

        log("Готово: $outputPath")
    }

    private fun log(message: String) {
        println("[${Timestamp.from(Instant.now())}] PlayerMp4MuxService: $message")
    }
}
