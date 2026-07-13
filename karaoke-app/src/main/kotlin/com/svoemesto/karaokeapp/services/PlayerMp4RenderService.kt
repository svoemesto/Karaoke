package com.svoemesto.karaokeapp.services

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.svoemesto.karaokeapp.PATH_TO_TEMP_RENDERMP4_FOLDER
import java.io.File
import java.sql.Timestamp
import java.time.Instant
import java.util.Base64
import kotlin.math.ceil

enum class RenderVersion(val label: String, val comment: String, val fileSuffix: String) {
    LYRICS("Lyrics", "Song", "[lyrics]"),
    KARAOKE("Karaoke", "Accompaniment", "[karaoke]"),
    DEMO("Karaoke (Demo)", "Ознакомительный фрагмент", "[demo]")
}

data class RenderMp4Params(
    val songId: Long,
    val width: Int = 1920,
    val height: Int = 1080,
    val fps: Int = 60,
    val version: RenderVersion = RenderVersion.KARAOKE,
    val demoFragmentStart: Double? = null,
    val demoFragmentEnd: Double? = null,
)

data class RenderMp4FramesResult(
    val framesDir: File,
    val preroll: Double,
    val duration: Double,
    val frameCount: Int,
)

/**
 * Этап 1 (MVP) рендера видео mp4 напрямую из онлайн-плеера — в перспективе замена рендера через MLT
 * (см. DEVELOPMENT.md "Рендер видео MP4 из онлайн-плеера"). Плеер (webvue3/src/player/KaraokePlayer.js) в
 * offline-режиме (URL `?render=1`, см. PlayerView.vue) детерминированно рисует любой кадр по времени
 * через публичный `renderFrameAt(dt)`: единственный источник времени контента (`_getDisplayTime()`) и
 * панорама фона (`_renderBackground()`) подменяются полем `_forcedTime` вместо реальных часов —
 * поэтому кадр воспроизводим по номеру/времени, без "живого" проигрывания.
 *
 * Здесь headless Chromium (Playwright, тот же паттерн headless-запуска, что и в
 * SponsrSyncService.syncViaScraping) открывает страницу плеера в docker-сети, покадрово по сетке
 * `t = frame/fps` вызывает `renderFrameAt(t)` и снимает canvas в JPEG-секвенцию. Звук на этом этапе
 * НЕ вынимается из браузера (WebAudio-декод плееру нужен только чтобы узнать duration/preroll) —
 * аудио отдельно микшируется из готовых flac-стемов, см. PlayerMp4MuxService.
 *
 * MVP: результат — JPEG-секвенция во временной папке, БЕЗ встраивания в очередь KaraokeProcess (нет
 * прогресса по SSE, нет thread-лейна) — только для визуальной сверки с эталонным MLT-рендером той же
 * песни. Встройка в очередь — следующий этап (см. план "Рендер видео MP4 из онлайн-плеера").
 */
object PlayerMp4RenderService {

    // webvue3-контейнер в docker-сети (nginx слушает 7906, проксирует /api → karaoke-app:8899 — тот
    // же путь, каким обычный браузер пользователя ходит на /player/:id). Реальный DNS-алиас в сети
    // karaokenet — "karaoke-webvue3" (имя контейнера/сервиса compose), не просто "webvue3" — проверено
    // docker inspect на рантайм-инстансе (/sm-karaoke/system/deploy): "karaoke-webvue3" ->
    // net::ERR_NAME_NOT_RESOLVED при попытке "webvue3".
    private const val WEBVUE3_BASE_URL = "http://karaoke-webvue3:7906"

    // Пока данные (playerdata JSON + оба аудио-стема + шрифты) не загружены — see KaraokePlayer.init().
    // Аудио-стемы могут быть десятки МБ, отсюда щедрый таймаут.
    private const val READY_TIMEOUT_MS = 180_000.0

    // Хвост fade-out в конце (см. FADE_OUT в _renderFrame KaraokePlayer.js) — 1с сверх звука, чтобы
    // кадр не обрывался на затухающем последнем слое караоке-текста.
    private const val TAIL_SECONDS = 1.0

    // Для DEMO — 10с экран завершения (логотип + сообщение + стрелка).
    private const val DEMO_END_SCREEN_SECONDS = 10.0

    /**
     * Покадрово рендерит песню [params.songId] в PNG-секвенцию. Папка результата — детерминированная
     * (по songId, не по timestamp'у) и перезаписывается при повторном вызове — удобно для повторных
     * прогонов MVP-сверки без накопления мусора на диске.
     */
    fun renderFrames(params: RenderMp4Params, onProgress: ((Int) -> Unit)? = null): RenderMp4FramesResult {
        val framesDir = File(PATH_TO_TEMP_RENDERMP4_FOLDER, "${params.songId}_${params.version.name}")
        framesDir.deleteRecursively()
        framesDir.mkdirs()

        log("Открываем headless-плеер для песни ${params.songId} (${params.width}x${params.height}@${params.fps}) version=${params.version.name}")

        var preroll = 0.0
        var duration = 0.0
        var frameCount = 0
        val tailSeconds = if (params.version == RenderVersion.DEMO) DEMO_END_SCREEN_SECONDS else TAIL_SECONDS

        Playwright.create().use { playwright ->
            val browser = playwright.chromium().launch(BrowserType.LaunchOptions().setHeadless(true))
            try {
                val context = browser.newContext(
                    Browser.NewContextOptions()
                        .setViewportSize(params.width, params.height)
                        .setDeviceScaleFactor(1.0)
                )
                val page = context.newPage()
                val url = buildString {
                    append("$WEBVUE3_BASE_URL/player/${params.songId}?render=1&version=${params.version.name}")
                    if (params.version == RenderVersion.DEMO && params.demoFragmentStart != null && params.demoFragmentEnd != null) {
                        append("&demoStart=${params.demoFragmentStart}")
                        append("&demoEnd=${params.demoFragmentEnd}")
                    }
                }
                page.navigate(url)

                page.waitForFunction(
                    "() => window.__kp && window.__kp._ready === true",
                    null,
                    Page.WaitForFunctionOptions().setTimeout(READY_TIMEOUT_MS)
                )

                @Suppress("UNCHECKED_CAST")
                val lens = page.evaluate("() => [window.__kp._preroll, window.__kp.duration]") as List<Any>
                preroll = (lens[0] as Number).toDouble()
                duration = (lens[1] as Number).toDouble()
                frameCount = ceil((preroll + duration + tailSeconds) * params.fps).toInt()
                log("Готово: preroll=${preroll}s duration=${duration}s -> $frameCount кадров")

                // Прямая установка backing store canvas в целевое разрешение — надёжнее, чем полагаться
                // на то, что _resizeCanvas() (реагирует на CSS-размер обёртки) даст именно W×H: viewport
                // контекста уже выставлен в params.width×params.height выше, это просто гарантия.
                page.evaluate(
                    "([w, h]) => { const c = document.querySelector('#kp-canvas'); c.width = w; c.height = h; }",
                    listOf(params.width, params.height)
                )

                // Оптимизация (см. DEVELOPMENT.md "Рендер видео MP4 из онлайн-плеера"): один CDP round-trip
                // на кадр вместо двух. Раньше — отдельный evaluate() (отрисовать) + отдельная команда
                // Locator.screenshot() (тяжёлый page-level screenshot pipeline: layout/compositing +
                // сериализация через протокол). Теперь canvas сам отдаёт себя как base64 PNG прямо из
                // возврата того же evaluate(), который его отрисовал — canvas.toDataURL() на порядок
                // дешевле полноценного "сфотографировать элемент страницы".
                val startNanos = System.nanoTime()
                for (frame in 0 until frameCount) {
                    val dt = frame.toDouble() / params.fps
                    val dataUrl = page.evaluate(
                        "(dt) => { window.__kp.renderFrameAt(dt); return document.querySelector('#kp-canvas').toDataURL('image/jpeg', 0.95); }",
                        dt
                    ) as String
                    val bytes = Base64.getDecoder().decode(dataUrl.substring(dataUrl.indexOf(',') + 1))
                    File(framesDir, "frame_%06d.jpg".format(frame)).writeBytes(bytes)
                    onProgress?.invoke(((frame + 1) * 100) / frameCount)
                    if ((frame + 1) % 200 == 0 || frame == frameCount - 1) {
                        val elapsedSec = (System.nanoTime() - startNanos) / 1_000_000_000.0
                        val rate = (frame + 1) / elapsedSec
                        log("Кадр ${frame + 1}/$frameCount (%.2f кадр/сек)".format(rate))
                    }
                }
            } finally {
                browser.close()
            }
        }

        log("Кадры сохранены в ${framesDir.absolutePath} ($frameCount шт.)")
        return RenderMp4FramesResult(framesDir = framesDir, preroll = preroll, duration = duration, frameCount = frameCount)
    }

    private fun log(message: String) {
        println("[${Timestamp.from(Instant.now())}] PlayerMp4RenderService: $message")
    }
}
