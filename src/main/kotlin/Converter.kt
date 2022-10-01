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
        fun getVoicesFromString(settingString: String): MutableList<Karaoke.KaraokeVoice> {

            val karaokeVoices: MutableList<Karaoke.KaraokeVoice> = mutableListOf()
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
                val fill = Karaoke.KaraokeVoiceFill(
                    evenColor = evenColor ?: getColorFromString(""),
                    evenOpacity = evenOpacity ?: 0.6,
                    oddColor = oddColor ?: getColorFromString(""),
                    oddOpacity = oddOpacity?: 0.6
                )

                val karaokeGroups: MutableList<Karaoke.KaraokeVoiceGroup> = mutableListOf()
                val partsGroups = partVoiceFieldGroups.split(delimiterGroups)
                partsGroups.forEach { partGroup ->
                    val partsFields = partGroup.split(delimiterFields)
                    var songtextTextFont: Font? = null
                    var songtextTextFontUnderline: Long? = null
                    var songtextTextColor: Color? = null
                    var songtextBeatFont: Font? = null
                    var songtextBeatFontUnderline: Long? = null
                    var songtextBeatColor: Color? = null
                    partsFields.forEach { partField ->
                        val parts = partField.split(delimiterNames)
                        val partName = parts[0]
                        val partValue = parts[1]
                        try {
                            when(partName) {
                                "songtextTextFont" -> songtextTextFont = getFontFromString(partValue)
                                "songtextTextFontUnderline" -> songtextTextFontUnderline = partValue.toLong()
                                "songtextTextColor" -> songtextTextColor = getColorFromString(partValue)
                                "songtextBeatFont" -> songtextBeatFont = getFontFromString(partValue)
                                "songtextBeatFontUnderline" -> songtextBeatFontUnderline = partValue.toLong()
                                "songtextBeatColor" -> songtextBeatColor = getColorFromString(partValue)
                            }
                        } catch (e: Exception) {
                            println("ВНИМАНИЕ: Исключение ${e.message} при десериализации шрифта: $settingString")
                        }
                    }
                    karaokeGroups.add(
                        Karaoke.KaraokeVoiceGroup(
                            songtextTextFont = songtextTextFont ?: getFontFromString(""),
                            songtextTextFontUnderline = songtextTextFontUnderline ?: 0,
                            songtextTextColor = songtextTextColor ?: getColorFromString(""),
                            songtextBeatFont = songtextBeatFont ?: getFontFromString(""),
                            songtextBeatFontUnderline = songtextBeatFontUnderline ?: 0,
                            songtextBeatColor = songtextBeatColor ?: getColorFromString("")
                        )
                    )
                }
                karaokeVoices.add(Karaoke.KaraokeVoice(groups = karaokeGroups,fill = fill))
            }
            return karaokeVoices
        }

        fun getStringFromVoices(voices: MutableList<Karaoke.KaraokeVoice>): String {
            return voices.joinToString(delimiterVoices) { voice ->
                val groupsText = voice.groups.joinToString(delimiterGroups) { group ->
                    val fieldsText = "songtextTextFont" +
                            delimiterNames +
                            group.songtextTextFont.setting() +
                            delimiterFields +
                            "songtextTextColor" +
                            delimiterNames +
                            group.songtextTextColor.setting() +
                            delimiterFields +
                            "songtextBeatFont" +
                            delimiterNames +
                            group.songtextBeatFont.setting() +
                            delimiterFields +
                            "songtextBeatColor" +
                            delimiterNames +
                            group.songtextBeatColor.setting()
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