package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.mlt.MltObject
import com.svoemesto.karaokeapp.mlt.MltObjectType
import com.svoemesto.karaokeapp.mlt.MltText
import com.svoemesto.karaokeapp.model.*
import java.awt.*
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.*
import javax.imageio.ImageIO
import kotlin.io.path.Path

fun createDzenPicture(pathToAuthor: String) {
    val pathToLogoAuthor = "$pathToAuthor/LogoAuthor.png"

    val frameW = 575
    val frameH = 575
    val opaque: Float = 1f
    var fontSongname = Font(MAIN_FONT_NAME, 0, 10)
    val colorCaption = Color(85, 255, 255, 255)
    var textToOverlay = "Karaoke"
    val imageType = BufferedImage.TYPE_INT_ARGB
    var resultImage = BufferedImage(frameW, frameH, imageType)
    var graphics2D = resultImage.graphics as Graphics2D
    var alphaChannel = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opaque)

    var biLogoAuthor = ImageIO.read(File(pathToLogoAuthor))

    graphics2D.composite = alphaChannel
    graphics2D.background = Color.BLACK
    graphics2D.color = Color.BLACK
    graphics2D.fillRect(0,0,frameW, frameH)
    graphics2D.color = colorCaption
    graphics2D.font = fontSongname

    var rectW = 0
    var rectH = 0
    do {
        fontSongname = Font(fontSongname.name, fontSongname.style, fontSongname.size + 1)
        graphics2D.font = fontSongname
        val fontMetrics = graphics2D.fontMetrics
        val rect = fontMetrics.getStringBounds(textToOverlay, graphics2D)
        rectW = rect.width.toInt()
        rectH = rect.height.toInt()
    } while (!(rectH > 130 || rectW > (frameW * 0.95)))

    var centerX = (frameW - rectW) / 2
    var centerY = (frameH - rectH) / 2 + rectH + 140
    graphics2D.drawString(textToOverlay, centerX, centerY)

    graphics2D.drawImage(resizeBufferedImage(biLogoAuthor, 575, 230), 0, 120, null)

    graphics2D.dispose()

    var file = File("$pathToAuthor/DZEN_$textToOverlay.png")

    ImageIO.write(resultImage, "png", file)





    textToOverlay = "Lyrics"
    resultImage = BufferedImage(frameW, frameH, imageType)
    graphics2D = resultImage.graphics as Graphics2D
    alphaChannel = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opaque)

    biLogoAuthor = ImageIO.read(File(pathToLogoAuthor))

    graphics2D.composite = alphaChannel
    graphics2D.background = Color.BLACK
    graphics2D.color = Color.BLACK
    graphics2D.fillRect(0,0,frameW, frameH)
    graphics2D.color = colorCaption
    graphics2D.font = fontSongname

    rectW = 0
    rectH = 0
    do {
        fontSongname = Font(fontSongname.name, fontSongname.style, fontSongname.size + 1)
        graphics2D.font = fontSongname
        val fontMetrics = graphics2D.fontMetrics
        val rect = fontMetrics.getStringBounds(textToOverlay, graphics2D)
        rectW = rect.width.toInt()
        rectH = rect.height.toInt()
    } while (!(rectH > 130 || rectW > (frameW * 0.95)))

    centerX = (frameW - rectW) / 2
    centerY = (frameH - rectH) / 2 + rectH + 140
    graphics2D.drawString(textToOverlay, centerX, centerY)

    graphics2D.drawImage(resizeBufferedImage(biLogoAuthor, 575, 230), 0, 120, null)

    graphics2D.dispose()

    file = File("$pathToAuthor/DZEN_$textToOverlay.png")

    ImageIO.write(resultImage, "png", file)


}

fun getVKPictureBase64(settings: Settings): String {

    val frameW = 800
    val frameH = 194
    val opaque: Float = 1f
    val imageType = BufferedImage.TYPE_INT_ARGB
    var resultImage = BufferedImage(frameW, frameH, imageType)
    val graphics2D = resultImage.graphics as Graphics2D
    graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    val alphaChannel = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opaque)

    val biLogoAlbum = ImageIO.read(ByteArrayInputStream(Base64.getDecoder().decode(settings.pictureAlbum?.full ?: "")))
    val biLogoAuthor =
        ImageIO.read(ByteArrayInputStream(Base64.getDecoder().decode(settings.pictureAuthor?.full ?: "")))

    graphics2D.composite = alphaChannel
    graphics2D.background = Color.BLACK
    graphics2D.color = Color.BLACK
    graphics2D.fillRect(0,0,frameW, frameH)

    graphics2D.drawImage(resizeBufferedImage(biLogoAlbum, 154, 154), 20, 20, null)
    graphics2D.drawImage(resizeBufferedImage(biLogoAuthor, 385, 154), 294, 20, null)

    graphics2D.dispose()

    val ios = ByteArrayOutputStream()

    ImageIO.write(resultImage, "png", ios)

    return Base64.getEncoder().encodeToString(ios.toByteArray())

}

fun createVKLinkPictureWeb(settings: Settings, reCreateIfExist: Boolean = true): String {

    val fName = "/home/nsa/Karaoke/karaoke-web/src/main/resources/static/tmp/${settings.id}.png"

    if (settings.onAir && File(fName).exists()) {
        println("${settings.rightSettingFileName} - в эфире, удаляем файл картинки")
        File(fName).delete()
        return "delete"
    } else if (settings.onAir) {
        println("${settings.rightSettingFileName} - в эфире, пропускаем создание файла картинки")
        return "skip"
//    } else if (!settings.onAir && settings.idStatus < 3) {
//        println("${settings.rightSettingFileName} - не в эфире и не готов, пропускаем создание файла картинки")
//        return "skip"
    } else if (!settings.onAir && File(fName).exists() && !reCreateIfExist) {
        println("${settings.rightSettingFileName} - не в эфире, не нужно пересоздавать, пропускаем создание файл картинки")
        return "skip"
    }
    println("${settings.rightSettingFileName} - не в эфире, создаём файл картинки")

    val pathToLogoAlbum = settings.pathToFileLogoAlbum
    val pathToLogoAuthor = settings.pathToFileLogoAuthor


    val frameW = 537
    val frameH = 240
    val padding = 20

    val albumW = ((frameW - 3*padding) / 3.5).toInt()
    val albumH = albumW
    val authorW = (albumW * 2.5).toInt()
    val authorH = albumH
    val picAreaH = 2*padding + albumH
    val textAreaW = frameW - 2*padding
    val textAreaH = frameH - picAreaH

//    val biLogoAlbum = ImageIO.read(ByteArrayInputStream(Base64.getDecoder().decode(settings.pictureAlbum?.full ?:"")))
//    val biLogoAuthor = ImageIO.read(ByteArrayInputStream(Base64.getDecoder().decode(settings.pictureAuthor?.full ?:"")))

    val albumPic = Picture(params = ImageParams(w = albumW, h = albumH, pathToFile = pathToLogoAlbum))
    val authorPic = Picture(params = ImageParams(w = authorW, h = authorH, pathToFile = pathToLogoAuthor))

//    val albumPic = Picture(params = ImageParams(w = albumW, h = albumH, biImage = biLogoAlbum))
//    val authorPic = Picture(params = ImageParams(w = authorW, h = authorH, biImage = biLogoAuthor))

    val areaText = Picture(
        params = TextParams(
            w = textAreaW,
            h = textAreaH,
            color = Color(255, 255, 127, 255),
            text = settings.songName.censored(),
            fontName = MAIN_FONT_NAME,
            fontStyle = 0,
            fontSize = 4,
            isCalculatedSize = true,
            isLineBreak = true
        )
    )

    val area = Picture(
        params = AreaParams(w = frameW, h = frameH, color = Color.BLACK),
        childs = mutableListOf(
            PictureChild(x = padding, y = padding, child = albumPic),
            PictureChild(x = albumW + 2 * padding, y = padding, child = authorPic),
            PictureChild(x = padding, y = picAreaH, child = areaText)
        )
    )

    val file = File(fName)

    try {
        ImageIO.write(area.bi(), "png", file)
    } catch (e: Exception) {
        println(e.message)
    }

    return "create"
}

fun createVKLinkPicture(settings: Settings, fileName: String = "") {

    val fName = if (fileName == "") {
        settings.getOutputFilename(SongOutputFile.PICTUREVK, SongVersion.LYRICS).replace(" [lyrics] VK"," [VKlink]")
    } else {
        fileName
    }

    val pathToLogoAlbum = settings.pathToFileLogoAlbum
    val pathToLogoAuthor = settings.pathToFileLogoAuthor


    val frameW = 537
    val frameH = 240
    val padding = 20

    val albumW = ((frameW - 3*padding) / 3.5).toInt()
    val albumH = albumW
    val authorW = (albumW * 2.5).toInt()
    val authorH = albumH
    val picAreaH = 2*padding + albumH
    val textAreaW = frameW - 2*padding
    val textAreaH = frameH - picAreaH

//    val biLogoAlbum = ImageIO.read(ByteArrayInputStream(Base64.getDecoder().decode(settings.pictureAlbum?.full ?:"")))
//    val biLogoAuthor = ImageIO.read(ByteArrayInputStream(Base64.getDecoder().decode(settings.pictureAuthor?.full ?:"")))

    val albumPic = Picture(params = ImageParams(w = albumW, h = albumH, pathToFile = pathToLogoAlbum))
    val authorPic = Picture(params = ImageParams(w = authorW, h = authorH, pathToFile = pathToLogoAuthor))

//    val albumPic = Picture(params = ImageParams(w = albumW, h = albumH, biImage = biLogoAlbum))
//    val authorPic = Picture(params = ImageParams(w = authorW, h = authorH, biImage = biLogoAuthor))

    val areaText = Picture(
        params = TextParams(
            w = textAreaW,
            h = textAreaH,
            color = Color(255, 255, 127, 255),
            text = settings.songName.censored(),
            fontName = MAIN_FONT_NAME,
            fontStyle = 0,
            fontSize = 4,
            isCalculatedSize = true,
            isLineBreak = true
        )
    )

    val area = Picture(
        params = AreaParams(w = frameW, h = frameH, color = Color.BLACK),
        childs = mutableListOf(
            PictureChild(x = padding, y = padding, child = albumPic),
            PictureChild(x = albumW + 2 * padding, y = padding, child = authorPic),
            PictureChild(x = padding, y = picAreaH, child = areaText)
        )
    )

    val file = File(fName)

    try {
        ImageIO.write(area.bi(), "png", file)
    } catch (e: Exception) {
        println(e.message)
    }

}

fun createVKPicture(settings: Settings, fileName: String = "") {

    val fName = if (fileName == "") {
        settings.getOutputFilename(SongOutputFile.PICTUREVK, SongVersion.LYRICS).replace(" [lyrics] VK"," [VK]")
    } else {
        fileName
    }

    val pathToLogoAlbum = settings.pathToFileLogoAlbum
    val pathToLogoAuthor = settings.pathToFileLogoAuthor

    val frameW = 575
    val frameH = 300
    val padding = 20

    val albumW = ((frameW - 3*padding) / 3.5).toInt()
    val albumH = albumW
    val authorW = (albumW * 2.5).toInt()
    val authorH = albumH
    val picAreaH = 2*padding + albumH

    val textAreaW = frameW - 2*padding
    val textAreaH = frameH - picAreaH

    val albumPic = Picture(params = ImageParams(w = albumW, h = albumH, pathToFile = pathToLogoAlbum))
    val authorPic = Picture(params = ImageParams(w = authorW, h = authorH, pathToFile = pathToLogoAuthor))

    val areaText = Picture(
        params = TextParams(
            w = textAreaW,
            h = textAreaH,
            color = Color(255, 255, 127, 255),
            text = settings.songName.censored(),
            fontName = MAIN_FONT_NAME,
            fontStyle = 0,
            fontSize = 4,
            isCalculatedSize = true,
            isLineBreak = true
        )
    )

    val area = Picture(
        params = AreaParams(w = frameW, h = frameH, color = Color.BLACK),
        childs = mutableListOf(
            PictureChild(x = padding, y = padding, child = albumPic),
            PictureChild(x = albumW + 2 * padding, y = padding, child = authorPic),
            PictureChild(x = padding, y = picAreaH, child = areaText)
        )
    )

    val file = File(fName)

    try {
        ImageIO.write(area.bi(), "png", file)
    } catch (e: Exception) {
        println(e.message)
    }

}

fun createBoostyFilesPicture(settings: Settings, fileName: String = "") {

    val fName = if (fileName == "") {
        settings.getOutputFilename(SongOutputFile.PICTUREBOOSTYFILES)
    } else {
        fileName
    }

    val caption = "Файлы"
    val pathToLogoAlbum = settings.pathToFileLogoAlbum
    val pathToLogoAuthor = settings.pathToFileLogoAuthor

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

    val albumPic = Picture(params = ImageParams(w = albumW, h = albumH, pathToFile = pathToLogoAlbum))
    val authorPic = Picture(params = ImageParams(w = authorW, h = authorH, pathToFile = pathToLogoAuthor))

    val areaText = Picture(
        params = TextParams(
            w = textAreaW,
            h = textAreaH,
            color = Color(255, 255, 127, 255),
            text = settings.songName.censored(),
            fontName = MAIN_FONT_NAME,
            fontStyle = 0,
            fontSize = 50,
            isCalculatedSize = true,
            isLineBreak = true
        )
    )

    val areaTextCaption = Picture(
        params = TextParams(
            w = textAreaW,
            h = 250,
            color = Color(85, 255, 255, 255),
            text = caption,
            fontName = MAIN_FONT_NAME,
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
            PictureChild(x = albumW + 2 * padding, y = padding, child = authorPic),
            PictureChild(x = padding, y = picAreaH, child = areaText),
            PictureChild(x = padding, y = 475, child = areaTextCaption)

        )
    )

    val file = File(fName)

    try {
        ImageIO.write(area.bi(), "png", file)
    } catch (e: Exception) {
        println(e.message)
    }

}

fun createBoostyTeaserPicture(settings: Settings, fileName: String = "") {

    val fName = if (fileName == "") {
        settings.getOutputFilename(SongOutputFile.PICTUREBOOSTYTEASER)
    } else {
        fileName
    }

    val pathToLogoAlbum = settings.pathToFileLogoAlbum
    val pathToLogoAuthor = settings.pathToFileLogoAuthor

    val frameW = 575
    val frameH = 625
    val padding = 20

    val albumW = ((frameW - 3*padding) / 3.5).toInt()
    val albumH = albumW
    val authorW = (albumW * 2.5).toInt()
    val authorH = albumH
    val picAreaH = 2*padding + albumH

    val textAreaW = frameW - 2*padding
    val textAreaH = frameH - picAreaH

    val albumPic = Picture(params = ImageParams(w = albumW, h = albumH, pathToFile = pathToLogoAlbum))
    val authorPic = Picture(params = ImageParams(w = authorW, h = authorH, pathToFile = pathToLogoAuthor))
    val areaText = Picture(
        params = TextParams(
            w = textAreaW,
            h = textAreaH,
            color = Color(255, 255, 127, 255),
            text = settings.songName.censored(),
            fontName = MAIN_FONT_NAME,
            fontStyle = 0,
            fontSize = 4,
            isCalculatedSize = true,
            isLineBreak = true
        )
    )


    val area = Picture(
        params = AreaParams(w = frameW, h = frameH, color = Color.BLACK),
        childs = mutableListOf(
            PictureChild(x = padding, y = padding, child = albumPic),
            PictureChild(x = albumW + 2 * padding, y = padding, child = authorPic),
            PictureChild(x = padding, y = picAreaH, child = areaText)
        )
    )

    val file = File(fName)

    try {
        ImageIO.write(area.bi(), "png", file)
    } catch (e: Exception) {
        println(e.message)
    }

}

fun resizeBufferedImage(img: BufferedImage, newW: Int, newH: Int): BufferedImage? {
    val tmp = img.getScaledInstance(newW, newH, Image.SCALE_SMOOTH)
    val dimg = BufferedImage(newW, newH, BufferedImage.TYPE_INT_ARGB)
    val g2d = dimg.createGraphics()
    g2d.drawImage(tmp, 0, 0, null)
    g2d.dispose()
    return dimg
}

fun createSongPicture(settings: Settings, songVersion: SongVersion) {

    val fileName = settings.getOutputFilename(SongOutputFile.PICTURE, songVersion)
    Files.createDirectories(Path(File(fileName).parent))

    val caption = songVersion.text
    val comment = songVersion.textForDescription
    val pathToLogoAlbum = settings.pathToFileLogoAlbum
    val pathToLogoAuthor = settings.pathToFileLogoAuthor
    val frameW = 1920
    val frameH = 1080
    val padding = 50

    val albumW = 400
    val albumH = 400
    val authorW = 1000
    val authorH = 400
    val picAreaH = 2*padding + albumH

    val pictureOffset = (frameW - albumW - authorW - padding) / 2

    val textAreaW = frameW - 2*padding
    val textAreaH = 400

    val albumPic = Picture(params = ImageParams(w = albumW, h = albumH, pathToFile = pathToLogoAlbum))
    val authorPic = Picture(params = ImageParams(w = authorW, h = authorH, pathToFile = pathToLogoAuthor))

    val areaText = Picture(
        params = TextParams(
            w = textAreaW,
            h = textAreaH,
            color = Color(255, 255, 127, 255),
            text = settings.songName.censored(),
            fontName = MAIN_FONT_NAME,
            fontStyle = 0,
            fontSize = 50,
            isCalculatedSize = true,
            isLineBreak = true
        )
    )
    val areaTextCaption = Picture(
        params = TextParams(
            w = textAreaW,
            h = 250,
            color = Color(85, 255, 255, 255),
            text = caption,
            fontName = MAIN_FONT_NAME,
            fontStyle = 0,
            fontSize = 185,
            isCalculatedSize = false,
            isLineBreak = false
        )
    )
    val areaTextComment = Picture(
        params = TextParams(
            w = textAreaW,
            h = 100,
            color = Color(85, 255, 255, 255),
            text = comment,
            fontName = MAIN_FONT_NAME,
            fontStyle = 0,
            fontSize = 60,
            isCalculatedSize = false,
            isLineBreak = false
        )
    )
    val area = Picture(
        params = AreaParams(w = frameW, h = frameH, color = Color.BLACK),
        childs = mutableListOf(
            PictureChild(x = pictureOffset, y = padding, child = albumPic),
            PictureChild(x = pictureOffset + albumW + padding, y = padding, child = authorPic),
            PictureChild(x = padding, y = picAreaH - padding, child = areaText),
            PictureChild(x = padding, y = 850, child = areaTextCaption),
            PictureChild(x = padding, y = 1080 - 70, child = areaTextComment)
        )
    )

    val file = File(fileName)

    try {
        ImageIO.write(area.bi(), "png", file)
    } catch (e: Exception) {
        println(e.message)
    }
}

fun getSongChordsPicture(settings: Settings, mltNode: MltNode): BufferedImage {

    val songTextSymbolsGroup = mltNode.body as MutableList<MltNode>
    val startViewport = songTextSymbolsGroup.first { it.name == "startviewport" }
    val startViewportFields = startViewport.fields["rect"]!!.split(",")
    val frameW = startViewportFields[2].toInt()
    val frameH = startViewportFields[3].toInt()
    // Находим количество страниц, на которые надо разделить текст, чтобы ширина была больше высоты
    var countPages = 0
    do {
        countPages++
    } while ((frameW.toDouble() * countPages) / (frameH.toDouble() / countPages) < 1.2 )

    // Находим минимальную высоту страницы
    var minPageH = frameH / (countPages)

    val opaque: Float = 1f
    val colorBack = Color(255, 255, 255, 255)
    val colorText = Color(0, 0, 0, 255)
    val colorChord = Color(255, 0, 0, 255)
    var fontText = Font(MAIN_FONT_NAME, 0, 10)
    val imageType = BufferedImage.TYPE_INT_ARGB
    val resultImage = BufferedImage(frameW, frameH, imageType)
    val graphics2D = resultImage.graphics as Graphics2D
    graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    val alphaChannel = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opaque)

    graphics2D.composite = alphaChannel
    graphics2D.background = colorBack
    graphics2D.color = colorBack
    graphics2D.fillRect(0,0,frameW, frameH)
    graphics2D.color = colorBack
    graphics2D.font = fontText

    var currPage = 1

    data class PicturePage(
        val number: Int,
        var h: Int,
        var lines: MutableList<MltNode>
    )
    val pages: MutableList<PicturePage> = mutableListOf()
    var currentH = 0
    var listLines: MutableList<MltNode> = mutableListOf()
    songTextSymbolsGroup.filter {it.name == "item"} .forEach { songTextSymbols ->
        val nodePosition = (songTextSymbols.body as MutableList<*>)[0] as MltNode
        val nodeText = (songTextSymbols.body as MutableList<*>)[1] as MltNode
        val x = nodePosition.fields["x"]!!.toInt()
        val y = nodePosition.fields["y"]!!.toInt()
        val textToOverlay = nodeText.body as String
        val isChord = (nodeText.fields["font"]!!.split(" ")[0] == Karaoke.chordsFont.font.fontName.split(" ")[0])
        val fontTextTmp = Font(nodeText.fields["font"], 0, nodeText.fields["font-pixel-size"]!!.toInt())
        graphics2D.font = fontTextTmp
        val fontMetrics = graphics2D.fontMetrics
        val rectH = fontMetrics.getStringBounds(textToOverlay, graphics2D).height.toInt()
        currentH = y + rectH
        val totalH = minPageH + pages.sumOf { it.h } // Полная высота - минимальная высота плюс высоты уже найденных страниц
        // Если текущая высота больше полной высоты и строчка не аккорд - переход на следующую страницу
        if (currentH > totalH && isChord) {
            val picturePage = PicturePage(number = currPage, h = currentH - pages.sumOf { it.h } - rectH, lines = listLines)
            pages.add(picturePage)
            currPage++
            listLines = mutableListOf()
            listLines.add(songTextSymbols)
//            println("${picturePage.number} - ${picturePage.h} - ${picturePage.lines.last().body}")
        } else {
            listLines.add(songTextSymbols)
        }
    }
    val picturePage = PicturePage(number = currPage, h = currentH - pages.sumOf { it.h }, lines = listLines)
    pages.add(picturePage)

    var totalH = 0
    val bis: MutableList<BufferedImage> = mutableListOf()
    pages.forEach { picturePage ->

        val resultImagePage = BufferedImage(frameW, picturePage.h + 20, imageType)
        val graphics2Dpage = resultImagePage.graphics as Graphics2D
        graphics2Dpage.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        val alphaChannelPage = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opaque)

        graphics2Dpage.composite = alphaChannelPage
        graphics2Dpage.background = colorBack
        graphics2Dpage.color = colorBack
        graphics2Dpage.fillRect(0,0,frameW, picturePage.h+20)
        graphics2Dpage.color = colorBack
        graphics2Dpage.font = fontText

        picturePage.lines.forEach { songTextSymbols ->
            val nodePosition = (songTextSymbols.body as MutableList<*>)[0] as MltNode
            val nodeText = (songTextSymbols.body as MutableList<*>)[1] as MltNode
            val x = nodePosition.fields["x"]!!.toInt()
            val y = nodePosition.fields["y"]!!.toInt() - totalH
            val textToOverlay = (nodeText.body as String).replace("&amp;amp;", "&")
            val isChord = (nodeText.fields["font"]!!.split(" ")[0] == Karaoke.chordsFont.font.fontName.split(" ")[0])
            graphics2Dpage.color = if (isChord) colorChord else colorText
            fontText = Font(
                nodeText.fields["font"],
                0,
                (nodeText.fields["font-pixel-size"]!!.toInt() / if (isChord) Karaoke.chordsHeightCoefficient else 1.0).toInt()
            )
            graphics2Dpage.font = fontText
            val fontMetrics = graphics2Dpage.fontMetrics
            val rectH = fontMetrics.getStringBounds(textToOverlay, graphics2Dpage).height.toInt()
            val newY = y + rectH
            graphics2Dpage.drawString(textToOverlay, x, newY)
        }
        graphics2Dpage.dispose()
        bis.add(resultImagePage)
        totalH += picturePage.h
    }

    val fullW = frameW * pages.size
    var fullH = (210 * fullW / 297.0).toInt()

    val resultImages = BufferedImage(fullW, pages.maxOf { it.h } + 20, imageType)
    val graphics2Dresult = resultImages.graphics as Graphics2D
    graphics2Dresult.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    graphics2Dresult.composite = alphaChannel
    graphics2Dresult.background = colorBack
    graphics2Dresult.color = colorBack
    graphics2Dresult.fillRect(0,0,fullW, pages.maxOf { it.h } + 20)
    bis.forEachIndexed{ index, bi ->
        graphics2Dresult.drawImage(bi, frameW * index, 0, null)
    }
    graphics2Dresult.dispose()

    val nameW = fullW
    val nameH = Integer.max(fullH - (pages.maxOf { it.h } + 20),200)

    val resultImageName = BufferedImage(nameW, nameH, imageType)
    val graphics2name = resultImageName.graphics as Graphics2D
    graphics2name.composite = alphaChannel
    graphics2name.background = colorBack
    graphics2name.color = colorBack
    graphics2name.fillRect(0,0,nameW, nameH)
    graphics2name.color = colorText
    val textToOverlay = "${settings.author} - ${settings.year} - «${settings.songName}» (${settings.key}, ${settings.bpm} bpm)"
    var rectW = 0
    var rectH = 0
    fontText = Font(MAIN_FONT_NAME, 0, 10)
    do {
        fontText = Font(fontText.name, fontText.style, fontText.size + 1)
        graphics2name.font = fontText
        val fontMetrics = graphics2name.fontMetrics
        val rect = fontMetrics.getStringBounds(textToOverlay, graphics2name)
        rectW = rect.width.toInt()
        rectH = rect.height.toInt()
    } while (!(rectH > (nameH * 0.7) || rectW > (nameW * 0.7)))

    var centerX = (nameW - rectW) / 2
    var centerY = (nameH - rectH) / 2 + rectH
    graphics2name.drawString(textToOverlay, centerX, centerY)
    graphics2name.dispose()
    fullH = resultImageName.height + resultImages.height
    val resultImagesAndName = BufferedImage(fullW, fullH, imageType)
    val graphics2DresultAndName = resultImagesAndName.graphics as Graphics2D
    graphics2DresultAndName.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    graphics2DresultAndName.composite = alphaChannel
    graphics2DresultAndName.background = colorBack
    graphics2DresultAndName.color = colorBack
    graphics2DresultAndName.fillRect(0,0,fullW, fullH)
    graphics2DresultAndName.drawImage(resultImageName, 0, 0, null)
    graphics2DresultAndName.drawImage(resultImages, 0, nameH, null)

    graphics2DresultAndName.dispose()

    return resultImagesAndName
}

fun createSongChordsPicture(settings: Settings, fileName: String, songVersion: SongVersion, mltNode: MltNode) {
    if (songVersion == SongVersion.CHORDS) {
        val resultImage = getSongChordsPicture(settings, mltNode)
        val file = File(fileName)
        ImageIO.write(resultImage, "png", file)
    }
}

fun getChordLayoutPicture(mltObjects:List<MltObject>): BufferedImage {

    val imageType = BufferedImage.TYPE_INT_ARGB

    if (mltObjects.isEmpty()) {
        val resultImage =
            BufferedImage((Karaoke.frameHeightPx / 4).toInt(), (Karaoke.frameHeightPx / 4).toInt(), imageType)
        val graphics2D = resultImage.graphics as Graphics2D
        graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        val opaque = 1f
        val alphaChannel = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opaque)
        graphics2D.composite = alphaChannel
        graphics2D.color = Color.BLACK
        graphics2D.fillRect(0,0,(Karaoke.frameHeightPx /4).toInt(), (Karaoke.frameHeightPx /4).toInt())
        graphics2D.dispose()
        return resultImage
    }
    val resultImage = BufferedImage(mltObjects[0].layoutW, mltObjects[0].layoutH, imageType)
    val graphics2D = resultImage.graphics as Graphics2D
    graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

    mltObjects.forEach { obj ->

        when (obj.shape.type) {
            MltObjectType.TEXT -> {
                val opaque = obj.shape.shapeColor.alpha / 255f
                val alphaChannel = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opaque)
                graphics2D.composite = alphaChannel
                graphics2D.color = obj.shape.shapeColor
                graphics2D.stroke = BasicStroke(obj.shape.shapeOutline.toFloat())

                val textToOverlay = (obj.shape as MltText).text
                graphics2D.font = (obj.shape as MltText).font
                graphics2D.drawString(textToOverlay, obj.x, obj.y + obj.h - obj.h/4)

            }
            else -> {

                var opaque = obj.shape.shapeColor.alpha / 255f
                var alphaChannel = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opaque)
                graphics2D.composite = alphaChannel
                graphics2D.color = obj.shape.shapeColor

                when (obj.shape.type) {
                    MltObjectType.RECTANGLE -> graphics2D.fillRect(obj.x,obj.y,obj.w, obj.h)
                    MltObjectType.CIRCLE -> graphics2D.fillOval(obj.x,obj.y,obj.w, obj.h)
                    MltObjectType.ROUNDEDRECTANGLE -> graphics2D.fillRoundRect(obj.x,obj.y,obj.w, obj.h, Integer.min(obj.w, obj.h), Integer.min(obj.w, obj.h))
                    else -> {}
                }

                opaque = obj.shape.shapeOutlineColor.alpha / 255f
                alphaChannel = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opaque)
                graphics2D.composite = alphaChannel
                graphics2D.stroke = BasicStroke(obj.shape.shapeOutline.toFloat())

                graphics2D.color = obj.shape.shapeOutlineColor
                when (obj.shape.type) {
                    MltObjectType.RECTANGLE -> graphics2D.drawRect(obj.x,obj.y,obj.w, obj.h)
                    MltObjectType.CIRCLE -> graphics2D.drawOval(obj.x,obj.y,obj.w, obj.h)
                    MltObjectType.ROUNDEDRECTANGLE -> graphics2D.drawRoundRect(obj.x,obj.y,obj.w, obj.h, Integer.min(obj.w, obj.h), Integer.min(obj.w, obj.h))
                    else -> {}
                }

            }
        }
    }
    graphics2D.dispose()
    return resultImage
}

fun createSponsrTeaserPicture(settings: Settings, fileName: String = "") {

    val fName = if (fileName == "") {
        settings.getOutputFilename(SongOutputFile.PICTURESPONSRTEASER)
    } else {
        fileName
    }

    val caption = settings.getTextForSponsrPictureDescription()
    val pathToLogoAlbum = settings.pathToFileLogoAlbum
    val pathToLogoAuthor = settings.pathToFileLogoAuthor
    val frameW = 1920
    val frameH = 1080
    val padding = 50

    val albumW = 400
    val albumH = 400
    val authorW = 1000
    val authorH = 400
    val picAreaH = 2*padding + albumH

    val pictureOffset = (frameW - albumW - authorW - padding) / 2

    val textAreaW = frameW - 2*padding
    val textAreaH = 400

    val albumPic = Picture(params = ImageParams(w = albumW, h = albumH, pathToFile = pathToLogoAlbum))
    val authorPic = Picture(params = ImageParams(w = authorW, h = authorH, pathToFile = pathToLogoAuthor))

    val areaText = Picture(
        params = TextParams(
            w = textAreaW,
            h = textAreaH,
            color = Color(255, 255, 127, 255),
            text = settings.songName.censored(),
            fontName = MAIN_FONT_NAME,
            fontStyle = 0,
            fontSize = 50,
            isCalculatedSize = true,
            isLineBreak = true
        )
    )
    val areaTextCaption = Picture(
        params = TextParams(
            w = textAreaW,
            h = 250,
            color = Color(85, 255, 255, 255),
            text = caption,
            fontName = MAIN_FONT_NAME,
            fontStyle = 0,
            fontSize = 100,
            isCalculatedSize = false,
            isLineBreak = false
        )
    )
    val area = Picture(
        params = AreaParams(w = frameW, h = frameH, color = Color.BLACK),
        childs = mutableListOf(
            PictureChild(x = pictureOffset, y = padding, child = albumPic),
            PictureChild(x = pictureOffset + albumW + padding, y = padding, child = authorPic),
            PictureChild(x = padding, y = picAreaH - padding, child = areaText),
            PictureChild(x = padding, y = 850, child = areaTextCaption)
        )
    )

    val file = File(fName)

    try {
        ImageIO.write(area.bi(), "png", file)
    } catch (e: Exception) {
        println(e.message)
    }
}