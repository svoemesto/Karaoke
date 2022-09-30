import java.awt.Font

class Converter {
    companion object {
        fun getFontFromString(settingString: String): Font {
            val parts = settingString.split(";")
            var fontName = "Tahoma"
            var fontStyle = 0
            var fontSize = 100
            parts.forEach {part ->
                val nameAndValue = part.split("=")
                if (nameAndValue.size ==2) {
                    val partName = nameAndValue[0]
                    val partValue = nameAndValue[1]
                    try {
                        when(partName) {
                            "name" -> fontName = partValue
                            "style" -> fontStyle = partValue.toInt()
                            "fontSize" -> fontSize = partValue.toInt()
                        }
                    } catch (e: Exception) {
                        println("ВНИМАНИЕ: Исключение ${e.message} при десериализации шрифта: $settingString")
                    }
                } else {
                    println("ВНИМАНИЕ: Неверное количество аргументов при десериализации шрифта: $settingString")
                }

            }
            return Font(fontName, fontStyle, fontSize)
        }
        fun getStringFromFont(font: Font): String {
            return "name=${font.name};style=${font.style};size=${font.size}"
        }
    }
}