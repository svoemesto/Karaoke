package mlt

import getFontSizeByHeight
import getTextWidthHeightPx
import mlt
import model.MltNode
import java.awt.Color
import java.awt.Font

data class MltObject(
    val _shape: MltShape,
    val layoutW: Int = 100,
    val layoutH: Int = 100,
    val _x: Int = 0,
    val _y: Int = 0,
    val _w: Int = 0,
    val _h: Int = 0,
    val alignmentX: MltObjectAlignmentX = MltObjectAlignmentX.CENTER,
    val alignmentY: MltObjectAlignmentY = MltObjectAlignmentY.CENTER
) {

    val shape: MltShape
        get() {
            return when(_shape.type) {
                MltObjectType.TEXT -> {
                    MltText(
                        text = (_shape as MltText).text,
                        font = Font(_shape.font.name, _shape.font.style, getFontSizeByHeight(h,_shape.font)),
                        fontUnderline = (_shape as MltText).fontUnderline,
                        shapeColor = _shape.shapeColor,
                        shapeOutline = _shape.shapeOutline,
                        shapeOutlineColor = _shape.shapeOutlineColor
                    )
                }
                else -> {
                    MltShape(
                        type = _shape.type,
                        shapeColor = _shape.shapeColor,
                        shapeOutline = _shape.shapeOutline,
                        shapeOutlineColor = _shape.shapeOutlineColor
                    )
                }
            }
        }
    val x: Int
        get() {
            return when (alignmentX) {
                MltObjectAlignmentX.LEFT -> _x
                MltObjectAlignmentX.CENTER -> _x - w/2
                MltObjectAlignmentX.RIGHT -> _x - w
            }
        }
    val y: Int
        get() {
            return when (alignmentY) {
                MltObjectAlignmentY.TOP -> _y
                MltObjectAlignmentY.CENTER -> _y - h/2
                MltObjectAlignmentY.BOTTOM -> _y - h
            }
        }


    val w: Int
        get() {
            return when (shape.type) {
                MltObjectType.TEXT -> getTextWidthHeightPx((shape as MltText).text, (shape as MltText).font).first.toInt()
                else -> _w
            }
        }

    val h: Int
        get() {
            return _h
        }

}
enum class MltObjectType {
    TEXT, RECTANGLE, CIRCLE, ROUNDEDRECTANGLE
}
enum class MltObjectAlignmentY {
    TOP, CENTER, BOTTOM
}
enum class MltObjectAlignmentX {
    LEFT, CENTER, RIGHT
}
data class MltText(
    var text: String = "",
    var font: Font,
    var fontUnderline: Int,
    override var shapeColor: Color,
    override var shapeOutline: Int,
    override var shapeOutlineColor: Color
): MltShape(MltObjectType.TEXT,shapeColor,shapeOutline,shapeOutlineColor) {
    val fontWeight: Int get() = if (font.isBold) 75 else 50
    val alignment: Int = 1
    val lineSpacing: Int = 0
    val letterSpacing: Int = 0
    val typewriter: String = "0;2;1;0;0"
    val shadow: String = "0;#64000000;3;3;3"
    val fontItalic: Int get() = if (font.isItalic) 1 else 0

    override fun copy(): MltText {
        return MltText(text = text, font = Font(font.name,font.style, font.size) , fontUnderline = fontUnderline, shapeColor = Color(shapeColor.red, shapeColor.green, shapeColor.blue, shapeColor.alpha), shapeOutline = shapeOutline, shapeOutlineColor = Color(shapeOutlineColor.red, shapeOutlineColor.green, shapeOutlineColor.blue, shapeOutlineColor.alpha))
    }
}

open class MltShape(
    open var type: MltObjectType,
    open var shapeColor: Color,
    open var shapeOutline: Int,
    open var shapeOutlineColor: Color
) {
    open fun copy(): MltShape {
        return MltShape(type = type, shapeColor = Color(shapeColor.red, shapeColor.green, shapeColor.blue, shapeColor.alpha), shapeOutline = shapeOutline, shapeOutlineColor = Color(shapeOutlineColor.red, shapeOutlineColor.green, shapeOutlineColor.blue, shapeOutlineColor.alpha))
    }
}

fun MltText.mltNode(value: String): MltNode {
    return MltNode(
        name = "content",
        fields = mutableMapOf(
            Pair("font", font.name),
            Pair("font-pixel-size", font.size.toString()),
            Pair("font-weight", fontWeight.toString()),
            Pair("font-underline", fontUnderline.toString()),
            Pair("font-italic", fontItalic.toString()),
            Pair("font-color", shapeColor.mlt()),
            Pair("font-outline", shapeOutline.toString()),
            Pair("font-outline-color", shapeOutlineColor.mlt()),
            Pair("line-spacing", lineSpacing.toString()),
            Pair("letter-spacing", letterSpacing.toString()),
            Pair("shadow", shadow),
            Pair("typewriter", typewriter),
            Pair("alignment", alignment.toString())
        ),
        body = value
    )
}

fun MltText.setting(): String {
    return "fname=${font.name}|fstyle=${font.style}|fsize=${font.size}" +
            "|fcr=${shapeColor.red}|fcg=${shapeColor.green}|fcb=${shapeColor.blue}|fca=${shapeColor.alpha}" +
            "|ocr=${shapeOutlineColor.red}|ocg=${shapeOutlineColor.green}|ocb=${shapeOutlineColor.blue}|oca=${shapeOutlineColor.alpha}" +
            "|underline=${fontUnderline}" +
            "|outline=${shapeOutline}"
}

fun MltShape.setting(): String {
    return  "type=${type.name}|fcr=${shapeColor.red}|fcg=${shapeColor.green}|fcb=${shapeColor.blue}|fca=${shapeColor.alpha}" +
            "|ocr=${shapeOutlineColor.red}|ocg=${shapeOutlineColor.green}|ocb=${shapeOutlineColor.blue}|oca=${shapeOutlineColor.alpha}" +
            "|outline=${shapeOutline}"
}