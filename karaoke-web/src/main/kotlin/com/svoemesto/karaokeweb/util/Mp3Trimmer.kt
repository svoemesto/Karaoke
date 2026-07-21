package com.svoemesto.karaokeweb.util

import java.io.ByteArrayOutputStream

// Чистый JVM-обрезчик mp3 по границам фреймов, без перекодирования и без ffmpeg (в образе
// karaoke-web его нет — см. deploy/karaoke-web/Dockerfile, eclipse-temurin:22-jre-jammy).
// Нужен для демо-режима онлайн-плеера: стемы кодируются VBR (`ffmpeg -codec:a libmp3lame
// -qscale:a 2`, см. ApiController.pushMp3ToStorage), поэтому приблизительная обрезка по
// байтовому смещению (CBR-формула secs*bitrate/8) даёт плавающую и непредсказуемую ошибку в
// секундах. Вместо этого разбираем реальные MPEG-фреймы (11-битный sync + таблицы битрейта/
// частоты дискретизации) и режем строго по их границе — работает и с CBR, и с VBR.
// Используется из PublicPlayerController.stemResponse только для demo-токенов (см.
// Settings.demoFragmentStartSeconds/demoFragmentEndSeconds, PlayerGestureUnlockService.demoRangeForToken)
// — обычный (не-демо) токен получает исходные байты без изменений.
// ВАЖНО: фрагмент теперь может начинаться НЕ с нуля (демо = "куплет минус 5 секунд отступа"), а
// значит режем и по НАЧАЛУ, отбрасывая всё до него. У LAME есть bit reservoir (кадр может занимать
// биты у соседних кадров) — начало вырезанного диапазона в редких случаях может дать минимальный
// артефакт на первые ~1-2 кадра (~30-50мс); полноценно этого избежать можно только перекодированием
// (ffmpeg), которого в этом образе нет — сочтено приемлемым компромиссом для демо-фрагмента.

/**
 * Singleton-объект Mp3Trimmer.
 *
 * @see AGENTS.md
 */
object Mp3Trimmer {
    private val BITRATES_V1_L1 = intArrayOf(0, 32, 64, 96, 128, 160, 192, 224, 256, 288, 320, 352, 384, 416, 448)
    private val BITRATES_V1_L2 = intArrayOf(0, 32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384)
    private val BITRATES_V1_L3 = intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320)
    private val BITRATES_V2_L1 = intArrayOf(0, 32, 48, 56, 64, 80, 96, 112, 128, 144, 160, 176, 192, 224, 256)
    private val BITRATES_V2_L23 = intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160)

    private val SAMPLERATES_V1 = intArrayOf(44100, 48000, 32000)
    private val SAMPLERATES_V2 = intArrayOf(22050, 24000, 16000)
    private val SAMPLERATES_V25 = intArrayOf(11025, 12000, 8000)

    private data class FrameInfo(
        val frameLength: Int,
        val durationSeconds: Double,
        val isXingHeader: Boolean,
    )

    /**
     * Обрезает mp3 до диапазона [[startSeconds], [endSeconds]] (в системе координат исходного
     * файла), строго по границам фреймов (перекодирование не требуется). Фреймы, чей интервал
     * полностью до [startSeconds] или после [endSeconds], отбрасываются; Xing/Info/VBRI служебный
     * фрейм (несёт таблицу для seek/общую длину — после обрезки уже неактуален) исключается всегда,
     * даже если он попадает в диапазон. Если распарсить поток не удалось — защитный фолбэк:
     * возвращает исходные байты целиком (лучше отдать длиннее задуманного, чем сломанный файл).
     * Если поток разобрался, но ни один фрейм не попал в диапазон (некорректный/слишком поздний
     * [startSeconds]) — возвращает пустой массив, а не полный файл (диапазона просто нет).
     */
    fun trimToRange(
        bytes: ByteArray,
        startSeconds: Double,
        endSeconds: Double,
    ): ByteArray {
        val start = startSeconds.coerceAtLeast(0.0)
        val end = endSeconds.coerceAtLeast(start)
        if (end <= start) return ByteArray(0)
        return try {
            val headerEnd = skipId3v2(bytes)
            val out = ByteArrayOutputStream(minOf(bytes.size, 2_000_000))
            if (start <= 0.0) out.write(bytes, 0, headerEnd) // ID3v2 бессмысленно сохранять, если фрагмент начинается не с нуля

            var pos = headerEnd
            var accumulated = 0.0
            var wroteAnyFrame = false
            while (pos + 4 <= bytes.size && accumulated < end) {
                val frame = parseFrameHeader(bytes, pos)
                if (frame == null) {
                    pos++
                    continue
                }
                if (pos + frame.frameLength > bytes.size) break
                if (!frame.isXingHeader) {
                    val frameEnd = accumulated + frame.durationSeconds
                    if (frameEnd > start) {
                        out.write(bytes, pos, frame.frameLength)
                        wroteAnyFrame = true
                    }
                    accumulated = frameEnd
                }
                pos += frame.frameLength
            }
            if (!wroteAnyFrame) ByteArray(0) else out.toByteArray()
        } catch (e: Exception) {
            bytes
        }
    }

    private fun skipId3v2(bytes: ByteArray): Int {
        if (bytes.size < 10) return 0
        if (bytes[0] != 'I'.code.toByte() || bytes[1] != 'D'.code.toByte() || bytes[2] != '3'.code.toByte()) return 0
        val flags = bytes[5].toInt() and 0xFF
        val hasFooter = (flags and 0x10) != 0
        val size =
            ((bytes[6].toInt() and 0x7F) shl 21) or
                ((bytes[7].toInt() and 0x7F) shl 14) or
                ((bytes[8].toInt() and 0x7F) shl 7) or
                (bytes[9].toInt() and 0x7F)
        val headerLen = 10 + size + (if (hasFooter) 10 else 0)
        return headerLen.coerceAtMost(bytes.size)
    }

    private fun parseFrameHeader(
        bytes: ByteArray,
        pos: Int,
    ): FrameInfo? {
        if (pos + 4 > bytes.size) return null
        val b0 = bytes[pos].toInt() and 0xFF
        val b1 = bytes[pos + 1].toInt() and 0xFF
        val b2 = bytes[pos + 2].toInt() and 0xFF
        val b3 = bytes[pos + 3].toInt() and 0xFF
        if (b0 != 0xFF || (b1 and 0xE0) != 0xE0) return null // 11-битный sync-word

        val versionBits = (b1 shr 3) and 0x3 // 00=MPEG2.5, 01=reserved, 10=MPEG2, 11=MPEG1
        val layerBits = (b1 shr 1) and 0x3 // 00=reserved, 01=Layer III, 10=Layer II, 11=Layer I
        if (versionBits == 1 || layerBits == 0) return null

        val bitrateIndex = (b2 shr 4) and 0xF
        val samplerateIndex = (b2 shr 2) and 0x3
        val padding = (b2 shr 1) and 0x1
        if (bitrateIndex == 0 || bitrateIndex == 15 || samplerateIndex == 3) return null // "free"/reserved

        val isMpeg1 = versionBits == 3
        val layer =
            when (layerBits) {
                3 -> 1
                2 -> 2
                else -> 3
            }

        val bitrateTable =
            when {
                isMpeg1 && layer == 1 -> BITRATES_V1_L1
                isMpeg1 && layer == 2 -> BITRATES_V1_L2
                isMpeg1 && layer == 3 -> BITRATES_V1_L3
                !isMpeg1 && layer == 1 -> BITRATES_V2_L1
                else -> BITRATES_V2_L23
            }
        val bitrateKbps = bitrateTable.getOrElse(bitrateIndex) { 0 }
        if (bitrateKbps == 0) return null

        val sampleRateTable =
            when (versionBits) {
                3 -> SAMPLERATES_V1
                2 -> SAMPLERATES_V2
                else -> SAMPLERATES_V25
            }
        val sampleRate = sampleRateTable[samplerateIndex]
        val bitrateBps = bitrateKbps * 1000

        val frameLength =
            if (layer == 1) {
                (12 * bitrateBps / sampleRate + padding) * 4
            } else {
                val coeff = if (isMpeg1) 144 else 72
                coeff * bitrateBps / sampleRate + padding
            }
        if (frameLength <= 0) return null

        val samplesPerFrame =
            when {
                layer == 1 -> 384
                layer == 2 -> 1152
                isMpeg1 -> 1152 // Layer III, MPEG1
                else -> 576 // Layer III, MPEG2/2.5
            }
        val durationSeconds = samplesPerFrame.toDouble() / sampleRate

        return FrameInfo(frameLength, durationSeconds, isXingFrame(bytes, pos, frameLength, isMpeg1, b3))
    }

    // Xing/Info (VBR-заголовок LAME) или VBRI (Fraunhofer) — служебный первый фрейм с таблицей
    // для seek и заявленной общей длиной; после обрезки он вводит плеер в заблуждение насчёт
    // фактической длительности, поэтому исключается из результата.
    private fun isXingFrame(
        bytes: ByteArray,
        pos: Int,
        frameLength: Int,
        isMpeg1: Boolean,
        b3: Int,
    ): Boolean {
        val channelMode = (b3 shr 6) and 0x3
        val isMono = channelMode == 3
        val sideInfoSize =
            if (isMpeg1) {
                if (isMono) 17 else 32
            } else {
                if (isMono) 9 else 17
            }
        val tagOffset = pos + 4 + sideInfoSize
        if (tagOffset + 4 > bytes.size || tagOffset + 4 > pos + frameLength) return false
        val tag = String(bytes, tagOffset, 4, Charsets.US_ASCII)
        return tag == "Xing" || tag == "Info" || tag == "VBRI"
    }
}
