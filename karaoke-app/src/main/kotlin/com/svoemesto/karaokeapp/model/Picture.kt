package com.svoemesto.karaokeapp.model

import java.awt.*
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class Picture(
    val params: PictureParams,
    val childs: MutableList<PictureChild> = mutableListOf()
) {
    companion object {
        fun createPicture() {

            val frameW = 575
            val frameH = 625

            val padding = 20

            val albumW = ((frameW - 3*padding) / 3.5).toInt()
            val albumH = albumW
            val authorW = (albumW * 2.5).toInt()
            val authorH = albumH
            val picAreaH = 2*padding + albumH

            val textAreaW = frameW - 2*padding
            val textAreaH = 350

            val albumPic = Picture(
                params = ImageParams(w = albumW, h = albumH, pathToFile = "/home/nsa/Documents/Караоке/Александр Лаэртский/1987 - Доители изнурённых жаб/LogoAlbum.png" )
            )
            val authorPic = Picture(
                params = ImageParams(w = authorW, h = authorH, pathToFile = "/home/nsa/Documents/Караоке/Александр Лаэртский/LogoAuthor.png" )
            )

            val a = Picture(params = AreaParams(w = textAreaW, h = textAreaH, color = Color(127,127,127,255)))

            val areaText = Picture(
                params = TextParams(
                    w = textAreaW,
                    h = textAreaH,
                    color = Color(255,255,127,255),
//                    text = "Мама",
//                    text = "Мама, мы все сошли с ума",
//                    text = "Мама мы все сошли с ума очень давно, надолго и всерьёз.",
                    text = "Мама мы все сошли с ума очень давно, надолго и всерьёз. И это печально.",
                    fontName = "Roboto",
                    fontStyle = 0,
                    fontSize = 4,
                    isCalculatedSize = true,
                    isLineBreak = true,
                    shadowColor = Color(127,127,127,255),
                    shadowOffset = 0
                )
            )

            val areaTextCaption = Picture(
                params = TextParams(
                    w = textAreaW,
                    h = 250,
                    color = Color(85,255,255,255),
                    text = "Файлы",
                    fontName =  "Roboto",
                    fontStyle = 0,
                    fontSize = 100,
                    isCalculatedSize = false,
                    isLineBreak = false
                )
            )

            val area = Picture(
                params = AreaParams(w = frameW, h = frameH, color = Color.BLACK),
                childs = mutableListOf(
                    PictureChild(x = padding, y = padding, child = albumPic),
                    PictureChild(x = albumW + 2*padding, y = padding, child = authorPic),
                    PictureChild(x = padding, y = picAreaH - padding, child = a),
                    PictureChild(x = padding, y = picAreaH - padding, child = areaText),
                    PictureChild(x = padding, y = 475, child = areaTextCaption)
                )
            )

            val fileName = "testPicture.png"
            val file = File(fileName)
            ImageIO.write(area.bi(), "png", file)

        }
    }
    fun bi(): BufferedImage {

        if (params is AreaParams) {
            val imageType = BufferedImage.TYPE_INT_ARGB
            val resultImage = BufferedImage(params.w, params.h, imageType)
            val graphics2D = resultImage.graphics as Graphics2D
            val alphaChannel = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, params.opaque)
            graphics2D.composite = alphaChannel
            graphics2D.background = params.color
            graphics2D.color = params.color
            graphics2D.fillRect(0,0,params.w, params.h)
            childs.forEach { pictureChild ->
                graphics2D.drawImage(pictureChild.child.bi(), pictureChild.x, pictureChild.y, null)
            }
            graphics2D.dispose()
            return resultImage
        } else if (params is ImageParams) {
            val biImage = resizeBufferedImage(ImageIO.read(File(params.pathToFile)), params.w, params.h)

            val imageType = BufferedImage.TYPE_INT_ARGB
            val resultImage = BufferedImage(params.w, params.h, imageType)
            val graphics2D = resultImage.graphics as Graphics2D
            val alphaChannel = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f)
            graphics2D.composite = alphaChannel

            graphics2D.drawImage(biImage, 0, 0, null)
            graphics2D.dispose()
            return resultImage

        } else if (params is TextParams) {

            val imageType = BufferedImage.TYPE_INT_ARGB
            val resultImage = BufferedImage(params.w, params.h, imageType)
            val graphics2D = resultImage.graphics as Graphics2D
            graphics2D.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            val alphaChannel = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f)
            graphics2D.composite = alphaChannel

            var font = Font(params.fontName, params.fontStyle, params.fontSize)

            var rectW = 0
            var rectWprev = 0
            var rectH = 0
            var rectHprev = 0
            var rectY = 0
            var rectYprev = 0

            graphics2D.color = params.color
            graphics2D.font = font

            if (params.isCalculatedSize) {

                val fontSizeAndWHYsingleLine = calcFontSizeAndWHY(
                    text = params.text,
                    w = params.w,
                    h = params.h,
                    font = Font(params.fontName, params.fontStyle, params.fontSize)
                )
                val fontSizeSingleLine = fontSizeAndWHYsingleLine.fontSize

                val words = params.text.split(" ")

                if (params.isLineBreak && words.size > 1) {

                    val vol = getVariantsOfLines(params.text)

                    val a = vol.map { lines ->
                        val fssl = lines.map { line ->
                            calcFontSizeAndWHY(
                                text = line,
                                w = params.w,
                                h = params.h / lines.size,
                                font = Font(params.fontName, params.fontStyle, params.fontSize)
                            )
                        }
                        val mfs = fssl.minOf { it.fontSize }
                        val minFontSizeSingleLine = fssl.filter { it.fontSize == mfs }.maxBy { it.w }
                        Pair(lines, minFontSizeSingleLine)
                    }
                    val maxFontSizeLines = a.maxOf { it.second.fontSize }
                    val filteredA = a.filter { it.second.fontSize == maxFontSizeLines }
                    val (lines, fswhy) = filteredA.minBy { it.second.w }

                    if (fswhy.fontSize > fontSizeSingleLine) {

                        lines.forEachIndexed { index, line ->

                            val lineSizeAndWHY = calcFontSizeAndWHY(
                                text = line,
                                w = params.w,
                                h = params.h,
                                font = Font(params.fontName, params.fontStyle, fswhy.fontSize),
                                isFix = true
                            )

                            val deltaH = fswhy.h * index - fswhy.h/4 + params.h / lines.size

                            val centerX = (params.w - lineSizeAndWHY.w) / 2
                            val centerY = deltaH

                            graphics2D.font = Font(params.fontName, params.fontStyle, lineSizeAndWHY.fontSize)
                            graphics2D.color = params.shadowColor
                            graphics2D.drawString(line, centerX+params.shadowOffset, centerY+params.shadowOffset)
                            graphics2D.color = params.color
                            graphics2D.drawString(line, centerX, centerY)

                        }

                    } else {

                        val centerX = (params.w - fontSizeAndWHYsingleLine.w) / 2
                        val centerY = (params.h + fontSizeAndWHYsingleLine.h) / 2  + fontSizeAndWHYsingleLine.y/4
                        graphics2D.font = Font(params.fontName, params.fontStyle, fontSizeAndWHYsingleLine.fontSize)
                        graphics2D.color = params.shadowColor
                        graphics2D.drawString(params.text, centerX+params.shadowOffset, centerY+params.shadowOffset)
                        graphics2D.color = params.color
                        graphics2D.drawString(params.text, centerX, centerY)

                    }

                } else {

                    val centerX = (params.w - fontSizeAndWHYsingleLine.w) / 2
                    val centerY = (params.h + fontSizeAndWHYsingleLine.h) / 2  + fontSizeAndWHYsingleLine.y/4
                    graphics2D.font = Font(params.fontName, params.fontStyle, fontSizeAndWHYsingleLine.fontSize)
                    graphics2D.color = params.shadowColor
                    graphics2D.drawString(params.text, centerX+params.shadowOffset, centerY+params.shadowOffset)
                    graphics2D.color = params.color
                    graphics2D.drawString(params.text, centerX, centerY)
                }

            } else {

                val fontSizeAndWHY = calcFontSizeAndWHY(
                    text = params.text,
                    w = params.w,
                    h = params.h,
                    font = Font(params.fontName, params.fontStyle, params.fontSize),
                    isFix = true
                )

                val centerX = (params.w - fontSizeAndWHY.w) / 2
                val centerY = (params.h + fontSizeAndWHY.h/2) / 2 + fontSizeAndWHY.y/4
                graphics2D.font = Font(params.fontName, params.fontStyle, fontSizeAndWHY.fontSize)
                graphics2D.color = params.shadowColor
                graphics2D.drawString(params.text, centerX+params.shadowOffset, centerY+params.shadowOffset)
                graphics2D.color = params.color
                graphics2D.drawString(params.text, centerX, centerY)

            }

            graphics2D.dispose()
            return resultImage

        }

        return BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)

    }

    private fun resizeBufferedImage(img: BufferedImage, newW: Int, newH: Int): BufferedImage {
        val tmp = img.getScaledInstance(newW, newH, Image.SCALE_SMOOTH)
        val dimg = BufferedImage(newW, newH, BufferedImage.TYPE_INT_ARGB)
        val g2d = dimg.createGraphics()
        g2d.drawImage(tmp, 0, 0, null)
        g2d.dispose()
        return dimg
    }

    private fun getVariantsOfLines(text: String): List<List<String>> {
        val result: MutableList<List<String>> = mutableListOf(listOf(text))
        val words = text.split(" ")
        for (i in 0 until words.size - 1) {
            val str1 = words.filterIndexed { index, _ -> index <= i }.joinToString(" ")
            val str2 = words.filterIndexed { index, _ -> index > i }.joinToString(" ")
            val vol = getVariantsOfLines(str2)
            vol.forEach {
                val rec = mutableListOf(str1)
                rec.addAll(it)
                result.add(rec)
            }
        }
        return result
    }

    private fun calcFontSizeAndWHY(text: String, w: Int, h: Int, font: Font, isFix: Boolean = false): FontSizeAndWHY {
        val imageType = BufferedImage.TYPE_INT_ARGB
        val resultImage = BufferedImage(w, h, imageType)
        val graphics2D = resultImage.graphics as Graphics2D
        graphics2D.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        val initFontSize = if (isFix) font.size else 4
        var fnt = Font(font.fontName, font.style, initFontSize-1)
        var rectW = 0
        var rectWprev = 0
        var rectH = 0
        var rectHprev = 0
        var rectY = 0
        var rectYprev = 0
        graphics2D.font = fnt
        do {
            rectWprev = rectW
            rectHprev = rectH
            rectYprev = rectY
            fnt = Font(fnt.fontName, fnt.style, fnt.size+1)
            graphics2D.font = fnt
            val fontMetrics = graphics2D.fontMetrics
            val rect = fontMetrics.getStringBounds(text, graphics2D)
            rectW = rect.width.toInt()
            rectH = rect.height.toInt()
            rectY = rect.y.toInt()
            if (isFix) return FontSizeAndWHY(fontSize = fnt.size, w = rectW, h = rectH, y = rectY, text = text)
        } while (!(rectH > h || rectW > w))
        return FontSizeAndWHY(fontSize = fnt.size-1, w = rectWprev, h = rectHprev, y = rectYprev, text = text)
    }

    data class FontSizeAndWHY(
        val fontSize: Int,
        val w: Int,
        val h: Int,
        val y: Int,
        val text: String
    )

}

data class PictureChild(
    val x: Int,
    val y: Int,
    val child: Picture
)

data class AreaParams(
    val w: Int,
    val h: Int,
    val color: Color,
    val opaque: Float = 1f
): PictureParams

data class ImageParams(
    val w: Int,
    val h: Int,
    val pathToFile: String
): PictureParams

data class TextParams(
    val w: Int,
    val h: Int,
    val color: Color,
    val text: String,
    val fontName: String,
    val fontStyle: Int,
    val fontSize: Int,
    val isCalculatedSize: Boolean = true,
    val isLineBreak: Boolean = true,
    val shadowColor: Color = Color.BLACK,
    val shadowOffset: Int = 2
): PictureParams

interface PictureParams