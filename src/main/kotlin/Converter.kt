import java.awt.Color
import java.awt.Font

class Converter {
    companion object {

        fun colorToMltValue(color: Color): String {
            return "${color.red},${color.green},${color.blue},${color.alpha}"
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
        fun getStringFromFont(font: Font): String {
            return "name=${font.name};style=${font.style};size=${font.size}"
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
        fun getStringFromColor(color: Color): String {
            return "r=${color.red};g=${color.green};b=${color.blue};a=${color.alpha}"
        }

    }
}