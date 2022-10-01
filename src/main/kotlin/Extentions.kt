import java.awt.Color
import java.awt.Font

fun Font.weight(): Int {
    return if (isBold) 75 else 50
}
fun Font.mlt(): String {
    return "${name};${style};${size}"
}
fun Font.setting(): String {
    return "name=${name};style=${style};size=${size}"
}
fun Color.hexRGB(): String {
    return Integer.toHexString(rgb)
}

fun Color.mlt(): String {
    return "${red},${green},${blue},${alpha}"
}

fun Color.setting(): String {
    return "r=${red};g=${green};b=${blue};a=${alpha}"
}
fun List<Color>.setting(): String {
    return joinToString("|") { it.setting() }
}