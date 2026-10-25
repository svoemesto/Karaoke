package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.mlt.MltObjectType
import com.svoemesto.karaokeapp.mlt.MltShape
import com.svoemesto.karaokeapp.mlt.MltText
import com.svoemesto.karaokeapp.mlt.setting
import org.slf4j.LoggerFactory
import java.awt.Color
import java.awt.Font

/**
 * BR-292 (аудит #336, пакет PKG-S3-26): разбор настроек голосов/цветов не должен
 * ронять рендер из-за усечённой или испорченной строки настройки (файл свойств).
 */
private val converterLog = LoggerFactory.getLogger("infra.converter")

/**
 * Класс Converter.
 *
 * @see archive/docs/features/dual-db-sync.md
 */
class Converter {
    companion object {
        fun getColorsFromString(settingString: String): MutableList<Color> {
            val listColors: MutableList<Color> = mutableListOf()
            val colorsParts = settingString.split("|")
            colorsParts.forEach { colorPart ->
                listColors.add(getColorFromString(colorPart))
            }
            return listColors
        }

        fun getVoicesFromString(settingString: String): MutableList<KaraokeVoice> {
            val karaokeVoices: MutableList<KaraokeVoice> = mutableListOf()
            val partsVoices = settingString.split(DELIMITER_VOICES)
            partsVoices.forEach { partVoice ->
                val partsVoiceFields = partVoice.split(DELIMITER_VOICE_FIELDS)
                // BR-292 (#398): индексы брались без проверки — усечённая настройка
                // (пустая строка настройки) давала IndexOutOfBoundsException из
                // парсера (500 на admin-эндпоинте, ERROR в очереди рендера).
                val partVoiceFieldGroups = partsVoiceFields.getOrNull(0) ?: ""
                val partVoiceFieldFill = partsVoiceFields.getOrNull(1)
                if (partVoiceFieldFill == null) {
                    converterLog.warn(
                        "converter: в настройке голосов нет блока заливки — блок пропущен (fields={})",
                        partsVoiceFields.size,
                    )
                    return@forEach
                }

                val partsFills = partVoiceFieldFill.split(DELIMITER_FIELDS)
                var evenColor: Color? = null
                var evenOpacity: Double? = null
                var oddColor: Color? = null
                var oddOpacity: Double? = null
                partsFills.forEach { partFill ->
                    val parts = partFill.split(DELIMITER_NAMES)
                    // BR-292 (#398): проверка размера после split (см. выше).
                    val partName = parts.getOrNull(0)
                    val partValue = parts.getOrNull(1)
                    if (partName == null || partValue == null) {
                        converterLog.warn("converter: поле заливки без значения — пропущено")
                        return@forEach
                    }
                    try {
                        when (partName) {
                            "evenColor" -> evenColor = getColorFromString(partValue)
                            "evenOpacity" -> evenOpacity = partValue.toDouble()
                            "oddColor" -> oddColor = getColorFromString(partValue)
                            "oddOpacity" -> oddOpacity = partValue.toDouble()
                        }
                    } catch (e: Exception) {
                        println("ВНИМАНИЕ: Исключение ${e.message} при десериализации заливки: $settingString")
                    }
                }
                val fill =
                    KaraokeVoiceFill(
                        evenColor = evenColor ?: getColorFromString(""),
                        evenOpacity = evenOpacity ?: 0.6,
                        oddColor = oddColor ?: getColorFromString(""),
                        oddOpacity = oddOpacity ?: 0.6,
                    )

                val karaokeGroups: MutableList<KaraokeVoiceGroup> = mutableListOf()
                val partsGroups = partVoiceFieldGroups.split(DELIMITER_GROUPS)
                partsGroups.forEach { partGroup ->
                    val partsFields = partGroup.split(DELIMITER_FIELDS)
                    var songtextTextMltText: MltText? = null
//                    val songtextBeatMltText: MltText? = null
                    partsFields.forEach { partField ->
                        val parts = partField.split(DELIMITER_NAMES)
                        // BR-292 (#398): проверка размера после split (см. выше).
                        val partName = parts.getOrNull(0)
                        val partValue = parts.getOrNull(1)
                        if (partName == null || partValue == null) {
                            converterLog.warn("converter: поле группы голосов без значения — пропущено")
                            return@forEach
                        }
                        try {
                            when (partName) {
                                "songtextTextMltFont" -> songtextTextMltText = getMltFontFromString(partValue)
                            }
                        } catch (e: Exception) {
                            println("ВНИМАНИЕ: Исключение ${e.message} при десериализации шрифта: $settingString")
                        }
                    }
                    karaokeGroups.add(
                        KaraokeVoiceGroup(
                            mltText = songtextTextMltText ?: getMltFontFromString(""),
                        ),
                    )
                }
                karaokeVoices.add(KaraokeVoice(groups = karaokeGroups, fill = fill))
            }
            return karaokeVoices
        }

        fun getStringFromVoices(voices: MutableList<KaraokeVoice>): String =
            voices.joinToString(DELIMITER_VOICES) { voice ->
                val groupsText =
                    voice.groups.joinToString(DELIMITER_GROUPS) { group ->
                        val fieldsText =
                            "songtextTextMltFont" +
                                DELIMITER_NAMES +
                                group.mltText.setting()
                        fieldsText
                    }
                val fillText =
                    "evenColor" +
                        DELIMITER_NAMES +
                        voice.fill.evenColor.setting() +
                        DELIMITER_FIELDS +
                        "evenOpacity" +
                        DELIMITER_NAMES +
                        voice.fill.evenOpacity +
                        DELIMITER_FIELDS +
                        "oddColor" +
                        DELIMITER_NAMES +
                        voice.fill.oddColor.setting() +
                        DELIMITER_FIELDS +
                        "oddOpacity" +
                        DELIMITER_NAMES +
                        voice.fill.oddOpacity
                "${groupsText}${DELIMITER_VOICE_FIELDS}$fillText"
            }

        fun getMltFontFromString(settingString: String): MltText {
            val parts = settingString.split("|")

            var fname = "Tahoma"
            var fstyle = 0
            var fsize = 0
            var fcr = 255
            var fcg = 255
            var fcb = 255
            var fca = 255
            var ocr = 0
            var ocg = 0
            var ocb = 0
            var oca = 255
            var underline = 0
            var outline = 0

//            val fontSize = 100
            if (parts.size == 13) {
                parts.forEach { part ->
                    val nameAndValue = part.split("=")
                    if (nameAndValue.size == 2) {
                        val partName = nameAndValue[0]
                        val partValue = nameAndValue[1]
                        try {
                            when (partName) {
                                "fname" -> fname = partValue
                                "fstyle" -> fstyle = partValue.toInt()
                                "fsize" -> fsize = partValue.toInt()
                                "fcr" -> fcr = partValue.toInt()
                                "fcg" -> fcg = partValue.toInt()
                                "fcb" -> fcb = partValue.toInt()
                                "fca" -> fca = partValue.toInt()
                                "ocr" -> ocr = partValue.toInt()
                                "ocg" -> ocg = partValue.toInt()
                                "ocb" -> ocb = partValue.toInt()
                                "oca" -> oca = partValue.toInt()
                                "underline" -> underline = partValue.toInt()
                                "outline" -> outline = partValue.toInt()
                            }
                        } catch (e: Exception) {
                            println("ВНИМАНИЕ: Исключение ${e.message} при десериализации шрифта: $settingString")
                        }
                    } else {
                        println("ВНИМАНИЕ: Неверное количество аргументов при десериализации шрифта: $settingString")
                    }
                }
            } else {
                println("ВНИМАНИЕ: Неверное количество параметров при десериализации шрифта: $settingString")
            }

            // BR-292 (#398): Font/Color строятся ВНУТРИ try — значение из БД вида
            // «r=300» давало IllegalArgumentException мимо catch-а и роняло рендер.
            val font =
                try {
                    Font(fname, fstyle, fsize)
                } catch (e: Exception) {
                    converterLog.warn("converter: некорректный шрифт '{}' — взят дефолт", fname, e)
                    Font("Tahoma", fstyle, fsize)
                }
            return MltText(
                font = font,
                shapeColor = safeColor(fcr, fcg, fcb, fca),
                shapeOutlineColor = safeColor(ocr, ocg, ocb, oca),
                fontUnderline = underline,
                shapeOutline = outline,
            )
        }

        fun getMltShapeFromString(settingString: String): MltShape {
            val parts = settingString.split("|")

            var type = MltObjectType.RECTANGLE
            var fcr = 255
            var fcg = 255
            var fcb = 255
            var fca = 255
            var ocr = 0
            var ocg = 0
            var ocb = 0
            var oca = 255
            var outline = 0

            if (parts.size == 10) {
                parts.forEach { part ->
                    val nameAndValue = part.split("=")
                    if (nameAndValue.size == 2) {
                        val partName = nameAndValue[0]
                        val partValue = nameAndValue[1]
                        try {
                            when (partName) {
                                "type" -> type = MltObjectType.valueOf(partValue)
                                "fcr" -> fcr = partValue.toInt()
                                "fcg" -> fcg = partValue.toInt()
                                "fcb" -> fcb = partValue.toInt()
                                "fca" -> fca = partValue.toInt()
                                "ocr" -> ocr = partValue.toInt()
                                "ocg" -> ocg = partValue.toInt()
                                "ocb" -> ocb = partValue.toInt()
                                "oca" -> oca = partValue.toInt()
                                "outline" -> outline = partValue.toInt()
                            }
                        } catch (e: Exception) {
                            println("ВНИМАНИЕ: Исключение ${e.message} при десериализации шейпа: $settingString")
                        }
                    } else {
                        println("ВНИМАНИЕ: Неверное количество аргументов при десериализации шейпа: $settingString")
                    }
                }
            } else {
                println("ВНИМАНИЕ: Неверное количество параметров при десериализации шейпа: $settingString")
            }

            return MltShape(
                type = type,
                shapeColor = safeColor(fcr, fcg, fcb, fca),
                shapeOutlineColor = safeColor(ocr, ocg, ocb, oca),
                shapeOutline = outline,
            )
        }

        @Suppress("unused")
        fun getFontFromString(settingString: String): Font {
            val parts = settingString.split(";")
            var fontName = "Tahoma"
            var fontStyle = 0
            var fontSize = 100
            if (parts.size == 3) {
                parts.forEach { part ->
                    val nameAndValue = part.split("=")
                    if (nameAndValue.size == 2) {
                        val partName = nameAndValue[0]
                        val partValue = nameAndValue[1]
                        try {
                            when (partName) {
                                "name" -> fontName = partValue
                                "style" -> fontStyle = partValue.toInt()
                                "size" -> fontSize = partValue.toInt()
                            }
                        } catch (e: Exception) {
                            println("ВНИМАНИЕ: Исключение ${e.message} при десериализации шрифта: $settingString")
                        }
                    } else {
                        println("ВНИМАНИЕ: Неверное количество аргументов при десериализации шрифта: $settingString")
                    }
                }
            } else {
                println("ВНИМАНИЕ: Неверное количество параметров при десериализации шрифта: $settingString")
            }

            return Font(fontName, fontStyle, fontSize)
        }

        fun getColorFromString(settingString: String): Color {
            val parts = settingString.split(";")
            var colorR = 255
            var colorG = 255
            var colorB = 255
            var colorA = 255
            if (parts.size == 4) {
                parts.forEach { part ->
                    val nameAndValue = part.split("=")
                    if (nameAndValue.size == 2) {
                        val partName = nameAndValue[0]
                        val partValue = nameAndValue[1]
                        try {
                            when (partName) {
                                "r" -> colorR = partValue.toInt()
                                "g" -> colorG = partValue.toInt()
                                "b" -> colorB = partValue.toInt()
                                "a" -> colorA = partValue.toInt()
                            }
                        } catch (e: Exception) {
                            println("ВНИМАНИЕ: Исключение ${e.message} при десериализации цвета: $settingString")
                        }
                    } else {
                        println("ВНИМАНИЕ: Неверное количество аргументов при десериализации цвета: $settingString")
                    }
                }
            } else {
                println("ВНИМАНИЕ: Неверное количество параметров при десериализации цвета: $settingString")
            }
            // BR-292 (#398): Color(...) — вне try, значение «r=300» из БД роняло парсер.
            return safeColor(colorR, colorG, colorB, colorA)
        }

        /**
         * BR-292 (#398): безопасное построение цвета — значение вне 0..255 даёт
         * `IllegalArgumentException`, поэтому подменяем дефолтом и пишем в лог.
         */
        private fun safeColor(
            r: Int,
            g: Int,
            b: Int,
            a: Int,
        ): Color =
            try {
                Color(r, g, b, a)
            } catch (e: Exception) {
                converterLog.warn(
                    "converter: некорректный цвет ({}, {}, {}, {}) — взят дефолт",
                    r,
                    g,
                    b,
                    a,
                    e,
                )
                Color(255, 255, 255, 255)
            }
    }
}
