package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.mlt.MltObjectType
import com.svoemesto.karaokeapp.mlt.MltShape
import com.svoemesto.karaokeapp.mlt.MltText
import com.svoemesto.karaokeapp.mlt.setting
import java.awt.Color
import java.awt.Font

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
            val partsVoices = settingString.split(delimiterVoices)
            partsVoices.forEach { partVoice ->
                val partsVoiceFields = partVoice.split(delimiterVoiceFields)
                val partVoiceFieldGroups = partsVoiceFields[0]
                val partVoiceFieldFill = partsVoiceFields[1]

                val partsFills = partVoiceFieldFill.split(delimiterFields)
                var evenColor: Color? = null
                var evenOpacity: Double? = null
                var oddColor: Color? = null
                var oddOpacity: Double? = null
                partsFills.forEach { partFill ->
                    val parts = partFill.split(delimiterNames)
                    val partName = parts[0]
                    val partValue = parts[1]
                    try {
                        when(partName) {
                            "evenColor" -> evenColor = getColorFromString(partValue)
                            "evenOpacity" -> evenOpacity = partValue.toDouble()
                            "oddColor" -> oddColor = getColorFromString(partValue)
                            "oddOpacity" -> oddOpacity = partValue.toDouble()
                        }
                    } catch (e: Exception) {
                        println("ВНИМАНИЕ: Исключение ${e.message} при десериализации заливки: $settingString")
                    }
                }
                val fill = KaraokeVoiceFill(
                    evenColor = evenColor ?: getColorFromString(""),
                    evenOpacity = evenOpacity ?: 0.6,
                    oddColor = oddColor ?: getColorFromString(""),
                    oddOpacity = oddOpacity ?: 0.6
                )

                val karaokeGroups: MutableList<KaraokeVoiceGroup> = mutableListOf()
                val partsGroups = partVoiceFieldGroups.split(delimiterGroups)
                partsGroups.forEach { partGroup ->
                    val partsFields = partGroup.split(delimiterFields)
                    var songtextTextMltText: MltText? = null
                    var songtextBeatMltText: MltText? = null
                    partsFields.forEach { partField ->
                        val parts = partField.split(delimiterNames)
                        val partName = parts[0]
                        val partValue = parts[1]
                        try {
                            when(partName) {
                                "songtextTextMltFont" -> songtextTextMltText = getMltFontFromString(partValue)
                            }
                        } catch (e: Exception) {
                            println("ВНИМАНИЕ: Исключение ${e.message} при десериализации шрифта: $settingString")
                        }
                    }
                    karaokeGroups.add(
                        KaraokeVoiceGroup(
                            mltText = songtextTextMltText ?: getMltFontFromString("")
                        )
                    )
                }
                karaokeVoices.add(KaraokeVoice(groups = karaokeGroups, fill = fill))
            }
            return karaokeVoices
        }

        fun getStringFromVoices(voices: MutableList<KaraokeVoice>): String {
            return voices.joinToString(delimiterVoices) { voice ->
                val groupsText = voice.groups.joinToString(delimiterGroups) { group ->
                    val fieldsText = "songtextTextMltFont" +
                            delimiterNames +
                            group.mltText.setting()
                    fieldsText
                }
                val fillText = "evenColor" +
                        delimiterNames +
                        voice.fill.evenColor.setting() +
                        delimiterFields +
                        "evenOpacity" +
                        delimiterNames +
                        voice.fill.evenOpacity +
                        delimiterFields +
                        "oddColor" +
                        delimiterNames +
                        voice.fill.oddColor.setting() +
                        delimiterFields +
                        "oddOpacity" +
                        delimiterNames +
                        voice.fill.oddOpacity
                "${groupsText}${delimiterVoiceFields}${fillText}"
            }
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

            var fontSize = 100
            if (parts.size == 13) {
                parts.forEach {part ->
                    val nameAndValue = part.split("=")
                    if (nameAndValue.size ==2) {
                        val partName = nameAndValue[0]
                        val partValue = nameAndValue[1]
                        try {
                            when(partName) {
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

            return MltText(font = Font(fname,fstyle,fsize), shapeColor = Color(fcr,fcg,fcb,fca), shapeOutlineColor = Color(ocr,ocg,ocb,oca), fontUnderline = underline, shapeOutline = outline)
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
                parts.forEach {part ->
                    val nameAndValue = part.split("=")
                    if (nameAndValue.size ==2) {
                        val partName = nameAndValue[0]
                        val partValue = nameAndValue[1]
                        try {
                            when(partName) {
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

            return MltShape(type = type, shapeColor = Color(fcr,fcg,fcb,fca), shapeOutlineColor = Color(ocr,ocg,ocb,oca), shapeOutline = outline)
        }

        fun getFontFromString(settingString: String): Font {
            val parts = settingString.split(";")
            var fontName = "Tahoma"
            var fontStyle = 0
            var fontSize = 100
            if (parts.size == 3) {
                parts.forEach {part ->
                    val nameAndValue = part.split("=")
                    if (nameAndValue.size ==2) {
                        val partName = nameAndValue[0]
                        val partValue = nameAndValue[1]
                        try {
                            when(partName) {
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
            var colorR: Int = 255
            var colorG: Int = 255
            var colorB: Int = 255
            var colorA: Int = 255
            if (parts.size == 4) {
                parts.forEach {part ->
                    val nameAndValue = part.split("=")
                    if (nameAndValue.size ==2) {
                        val partName = nameAndValue[0]
                        val partValue = nameAndValue[1]
                        try {
                            when(partName) {
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
            return Color(colorR, colorG, colorB, colorA)
        }

    }
}