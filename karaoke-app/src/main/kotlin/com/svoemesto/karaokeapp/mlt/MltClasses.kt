package com.svoemesto.karaokeapp.mlt

import com.svoemesto.karaokeapp.getFontSizeByHeight
import com.svoemesto.karaokeapp.getTextWidthHeightPx
import com.svoemesto.karaokeapp.mlt
import com.svoemesto.karaokeapp.model.MltNode
import java.awt.Color
import java.awt.Font
import java.io.Serializable

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
) : Serializable {

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
enum class MltObjectType : Serializable {
    TEXT, RECTANGLE, CIRCLE, ROUNDEDRECTANGLE
}
enum class MltObjectAlignmentY : Serializable {
    TOP, CENTER, BOTTOM
}
enum class MltObjectAlignmentX : Serializable {
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
    val fontWeight: Int get() = if (font.isBold) 700 else 300
    val alignment: Int = 1
    val lineSpacing: Int = 0
    val letterSpacing: Int = 0
    val typewriter: String = "0;2;1;0;0"
    val shadow: String = "1;#ff000000;3;3;3"
    val fontItalic: Int get() = if (font.isItalic) 1 else 0

    fun w(): Int = getTextWidthHeightPx(text, font).first.toInt()
    fun h(): Int = getTextWidthHeightPx(text, font).second.toInt()
    var x: Int = 0

    fun copy(newText: String, fontScaleCoeff: Double = 1.0): MltText {
        return MltText(
            text = newText,
            font = Font(font.name,font.style, (font.size*fontScaleCoeff).toInt()) ,
            fontUnderline = fontUnderline,
            shapeColor = Color(shapeColor.red, shapeColor.green, shapeColor.blue, shapeColor.alpha),
            shapeOutline = shapeOutline,
            shapeOutlineColor = Color(shapeOutlineColor.red, shapeOutlineColor.green, shapeOutlineColor.blue, shapeOutlineColor.alpha)
        )
    }

    fun copy(newText: String, newFontSize: Int): MltText {
        return MltText(
            text = newText,
            font = Font(font.name,font.style, newFontSize) ,
            fontUnderline = fontUnderline,
            shapeColor = Color(shapeColor.red, shapeColor.green, shapeColor.blue, shapeColor.alpha),
            shapeOutline = shapeOutline,
            shapeOutlineColor = Color(shapeOutlineColor.red, shapeOutlineColor.green, shapeOutlineColor.blue, shapeOutlineColor.alpha)
        )
    }
}

open class MltShape(
    open var type: MltObjectType,
    open var shapeColor: Color,
    open var shapeOutline: Int,
    open var shapeOutlineColor: Color
) : Serializable {
    open fun copy(): MltShape {
        return MltShape(
            type = type,
            shapeColor = Color(shapeColor.red, shapeColor.green, shapeColor.blue, shapeColor.alpha),
            shapeOutline = shapeOutline,
            shapeOutlineColor = Color(shapeOutlineColor.red, shapeOutlineColor.green, shapeOutlineColor.blue, shapeOutlineColor.alpha)
        )
    }
}

fun MltText.mltNode(value: String): MltNode {
    val fields = mutableMapOf<String,String>()
    if (font.name != "") fields["font"] = font.name
    if (font.size > 0) fields["font-pixel-size"] = font.size.toString()
    if (fontWeight > 0) fields["font-weight"] = fontWeight.toString()
    if (fontUnderline > 0) fields["font-underline"] = fontUnderline.toString()
    if (fontItalic > 0) fields["font-italic"] = fontItalic.toString()
    if (fontItalic > 0) fields["font-italic"] = fontItalic.toString()
    fields["font-color"] = shapeColor.mlt()
    if (shapeOutline > 0) {
        fields["font-outline"] = shapeOutline.toString()
        fields["font-outline-color"] = shapeOutlineColor.mlt()
    }
    if (lineSpacing > 0) fields["line-spacing"] = lineSpacing.toString()
    if (letterSpacing > 0) fields["letter-spacing"] = letterSpacing.toString()
   fields["shadow"] = shadow
   fields["typewriter"] = typewriter
   fields["alignment"] = alignment.toString()

    return MltNode(
        name = "content",
        fields = fields,
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