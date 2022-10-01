package mlt

import mlt
import model.MltNode
import java.awt.Color
import java.awt.Font

data class MltFont(
    var font: Font,
    var fontUnderline: Int,
    var fontColor: Color,
    var fontOutline: Int,
    var fontOutlineColor: Color
) {
    val fontWeight: Int get() = if (font.isBold) 75 else 50
    val alignment: Int = 1
    val lineSpacing: Int = 0
    val letterSpacing: Int = 0
    val typewriter: String = "0;2;1;0;0"
    val shadow: String = "0;#64000000;3;3;3"
    val fontItalic: Int get() = if (font.isItalic) 1 else 0

}

fun MltFont.mltNode(value: String): MltNode {
    return MltNode(
        name = "content",
        fields = mutableMapOf(
            Pair("font", font.name),
            Pair("font-pixel-size", font.size.toString()),
            Pair("font-weight", fontWeight.toString()),
            Pair("font-underline", fontUnderline.toString()),
            Pair("font-italic", fontItalic.toString()),
            Pair("font-color", fontColor.mlt()),
            Pair("font-outline", fontOutline.toString()),
            Pair("font-outline-color", fontOutlineColor.mlt()),
            Pair("line-spacing", lineSpacing.toString()),
            Pair("letter-spacing", letterSpacing.toString()),
            Pair("shadow", shadow),
            Pair("typewriter", typewriter),
            Pair("alignment", alignment.toString())
        ),
        body = value
    )
}

fun MltFont.setting(): String {
    return "fname=${font.name}|fstyle=${font.style}|fsize=${font.size}" +
            "|fcr=${fontColor.red}|fcg=${fontColor.green}|fcb=${fontColor.blue}|fca=${fontColor.alpha}" +
            "|ocr=${fontColor.red}|ocg=${fontColor.green}|ocb=${fontColor.blue}|oca=${fontColor.alpha}" +
            "|underline=${fontUnderline}" +
            "|outline=${fontOutline}"
}