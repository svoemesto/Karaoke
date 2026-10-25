package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.runCommand
import java.awt.*
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Одна картинка из MinIO (cover/album/author).
 *
 * Хранит:
 * - `id`, `pictureFileName` — идентификаторы.
 * - `pictureAuthor`, `pictureAlbum` — внешние ссылки на `Pictures.kt`
 *   (для отображения в UI).
 * - `pictureFull` — base64-данные (НЕ загружаются в `loadList`).
 * - `picturePreviewUrl` / `pictureFullUrl` — публичные URLs
 *   (presigned MinIO, см. `KaraokeStorageService.upload`).
 *
 * @see archive/docs/features/dual-db-sync.md
 */
class Picture(
    val params: PictureParams,
    val childs: MutableList<PictureChild> = mutableListOf(),
) {
    companion object {
        @Suppress("unused")
        fun createPicture() {
            val frameW = 575
            val frameH = 625

            val padding = 20

            val albumW = ((frameW - 3 * padding) / 3.5).toInt()
            val albumH = albumW
            val authorW = (albumW * 2.5).toInt()
            val authorH = albumH
            val picAreaH = 2 * padding + albumH

            val textAreaW = frameW - 2 * padding
            val textAreaH = 350

            val albumPic =
                Picture(
                    params =
                        ImageParams(
                            w = albumW,
                            h = albumH,
                            pathToFile = "/sm-karaoke/system/1987 - Доители изнурённых жаб/LogoAlbum.png",
                        ),
                )
            val authorPic =
                Picture(
                    params = ImageParams(w = authorW, h = authorH, pathToFile = "/sm-karaoke/system/LogoAuthor.png"),
                )

            val a = Picture(params = AreaParams(w = textAreaW, h = textAreaH, color = Color(127, 127, 127, 255)))

            val areaText =
                Picture(
                    params =
                        TextParams(
                            w = textAreaW,
                            h = textAreaH,
                            color = Color(255, 255, 127, 255),
//                    text = "Мама",
//                    text = "Мама, мы все сошли с ума",
//                    text = "Мама мы все сошли с ума очень давно, надолго и всерьёз.",
                            text = "Мама мы все сошли с ума очень давно, надолго и всерьёз. И это печально.",
                            fontName = "Roboto",
                            fontStyle = 0,
                            fontSize = 4,
                            isCalculatedSize = true,
                            isLineBreak = true,
                            shadowColor = Color(127, 127, 127, 255),
                            shadowOffset = 0,
                        ),
                )

            val areaTextCaption =
                Picture(
                    params =
                        TextParams(
                            w = textAreaW,
                            h = 250,
                            color = Color(85, 255, 255, 255),
                            text = "Файлы",
                            fontName = "Roboto",
                            fontStyle = 0,
                            fontSize = 100,
                            isCalculatedSize = false,
                            isLineBreak = false,
                        ),
                )

            val area =
                Picture(
                    params = AreaParams(w = frameW, h = frameH, color = Color.BLACK),
                    childs =
                        mutableListOf(
                            PictureChild(x = padding, y = padding, child = albumPic),
                            PictureChild(x = albumW + 2 * padding, y = padding, child = authorPic),
                            PictureChild(x = padding, y = picAreaH - padding, child = a),
                            PictureChild(x = padding, y = picAreaH - padding, child = areaText),
                            PictureChild(x = padding, y = 475, child = areaTextCaption),
                        ),
                )

            val fileName = "testPicture.png"
            val file = File(fileName)
            ImageIO.write(area.bi(), "png", file)
            runCommand(listOf("chmod", "666", fileName))
        }

        /**
         * BR-180 (аудит #336): максимум строк, в которые разбивается текст.
         *
         * Разбиение выбирается динамикой по позициям разрыва; больше строк — ниже
         * потолок кегля (высота строки = `h / k`), поэтому оптимум находится среди
         * первых десяти.
         */
        const val MAX_SPLIT_LINES = 10

        /**
         * BR-180 (аудит #336): максимум слов, для которых разбиение вообще выполняется.
         *
         * Прежний перебор разбиений был экспоненциальным (`2^(n-1)`), и длинное
         * название (13+ слов встречается в каталоге) приводило к минутам CPU в потоке
         * рендера и риску OOM всего процесса. Сверх этого порога текст не разбивается
         * (однострочная раскладка) — но разбиения уже не перебираются.
         */
        const val MAX_SPLIT_WORDS = 24
    }

    data class MultiLine(
        val fontName: String,
        val fontStyle: Int,
        val fontSize: Int,
        val text: String,
        val centerX: Int,
        val centerY: Int,
        val topY: Int,
    )

    fun multiLines(): List<MultiLine> {
        val result: MutableList<MultiLine> = mutableListOf()

        if (params is TextParams) {
            val imageType = BufferedImage.TYPE_INT_ARGB
            val resultImage = BufferedImage(params.w, params.h, imageType)
            val graphics2D = resultImage.graphics as Graphics2D
            try {
                graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                val alphaChannel = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f)
                graphics2D.composite = alphaChannel

                val font = Font(params.fontName, params.fontStyle, params.fontSize)

                graphics2D.color = params.color
                graphics2D.font = font

                if (params.isCalculatedSize) {
                    val fontSizeAndWHYsingleLine =
                        calcFontSizeAndWHY(
                            text = params.text,
                            w = params.w,
                            h = params.h,
                            font = Font(params.fontName, params.fontStyle, params.fontSize),
                        )
                    val fontSizeSingleLine = fontSizeAndWHYsingleLine.fontSize

                    val words = params.text.split(" ")

                    if (params.isLineBreak && words.size > 1) {
                        // BR-180: динамика по позициям разрыва вместо перебора 2^(n-1) вариантов.
                        val split =
                            selectBestSplit(
                                text = params.text,
                                w = params.w,
                                h = params.h,
                                font = Font(params.fontName, params.fontStyle, params.fontSize),
                            )
                        val lines = split?.lines ?: listOf(params.text)
                        val fswhy = split?.metrics ?: fontSizeAndWHYsingleLine

                        if (fswhy.fontSize > fontSizeSingleLine) {
                            lines.forEachIndexed { index, line ->

                                val lineSizeAndWHY =
                                    calcFontSizeAndWHY(
                                        text = line,
                                        w = params.w,
                                        h = params.h,
                                        font = Font(params.fontName, params.fontStyle, fswhy.fontSize),
                                        isFix = true,
                                    )

                                val deltaH = fswhy.h * index - fswhy.h / 4 + params.h / lines.size

                                val centerX = (params.w - lineSizeAndWHY.w) / 2
                                val centerY = deltaH

                                val topY = fswhy.h * index + (params.h - fswhy.h * lines.size) / 2

                                graphics2D.font = Font(params.fontName, params.fontStyle, lineSizeAndWHY.fontSize)
                                graphics2D.color = params.shadowColor
                                graphics2D.drawString(line, centerX + params.shadowOffset, centerY + params.shadowOffset)
                                graphics2D.color = params.color
                                graphics2D.drawString(line, centerX, centerY)
                                result.add(
                                    MultiLine(
                                        fontName = params.fontName,
                                        fontStyle = params.fontStyle,
                                        fontSize = lineSizeAndWHY.fontSize,
                                        text = line,
                                        centerX = centerX,
                                        centerY = centerY,
                                        topY = topY,
                                    ),
                                )
                            }
                        } else {
                            val centerX = (params.w - fontSizeAndWHYsingleLine.w) / 2
                            val centerY = (params.h + fontSizeAndWHYsingleLine.h) / 2 + fontSizeAndWHYsingleLine.y / 4
                            val topY = (params.h - fontSizeAndWHYsingleLine.h) / 2

                            graphics2D.font = Font(params.fontName, params.fontStyle, fontSizeAndWHYsingleLine.fontSize)
                            graphics2D.color = params.shadowColor
                            graphics2D.drawString(params.text, centerX + params.shadowOffset, centerY + params.shadowOffset)
                            graphics2D.color = params.color
                            graphics2D.drawString(params.text, centerX, centerY)
                            result.add(
                                MultiLine(
                                    fontName = params.fontName,
                                    fontStyle = params.fontStyle,
                                    fontSize = fontSizeAndWHYsingleLine.fontSize,
                                    text = params.text,
                                    centerX = centerX,
                                    centerY = centerY,
                                    topY = topY,
                                ),
                            )
                        }
                    } else {
                        val centerX = (params.w - fontSizeAndWHYsingleLine.w) / 2
                        val centerY = (params.h + fontSizeAndWHYsingleLine.h) / 2 + fontSizeAndWHYsingleLine.y / 4
                        val topY = (params.h - fontSizeAndWHYsingleLine.h) / 2
                        graphics2D.font = Font(params.fontName, params.fontStyle, fontSizeAndWHYsingleLine.fontSize)
                        graphics2D.color = params.shadowColor
                        graphics2D.drawString(params.text, centerX + params.shadowOffset, centerY + params.shadowOffset)
                        graphics2D.color = params.color
                        graphics2D.drawString(params.text, centerX, centerY)
                        result.add(
                            MultiLine(
                                fontName = params.fontName,
                                fontStyle = params.fontStyle,
                                fontSize = fontSizeAndWHYsingleLine.fontSize,
                                text = params.text,
                                centerX = centerX,
                                centerY = centerY,
                                topY = topY,
                            ),
                        )
                    }
                } else {
                    val fontSizeAndWHY =
                        calcFontSizeAndWHY(
                            text = params.text,
                            w = params.w,
                            h = params.h,
                            font = Font(params.fontName, params.fontStyle, params.fontSize),
                            isFix = true,
                        )

                    val centerX = (params.w - fontSizeAndWHY.w) / 2
                    val centerY = (params.h + fontSizeAndWHY.h / 2) / 2 + fontSizeAndWHY.y / 4
                    val topY = (params.h - fontSizeAndWHY.h / 2) / 2

                    graphics2D.font = Font(params.fontName, params.fontStyle, fontSizeAndWHY.fontSize)
                    graphics2D.color = params.shadowColor
                    graphics2D.drawString(params.text, centerX + params.shadowOffset, centerY + params.shadowOffset)
                    graphics2D.color = params.color
                    graphics2D.drawString(params.text, centerX, centerY)
                    result.add(
                        MultiLine(
                            fontName = params.fontName,
                            fontStyle = params.fontStyle,
                            fontSize = fontSizeAndWHY.fontSize,
                            text = params.text,
                            centerX = centerX,
                            centerY = centerY,
                            topY = topY,
                        ),
                    )
                }
            } finally {
                // BR-180 (аудит #336): Graphics2D освобождается — раньше он оставался
                // невысвобожденным нативным ресурсом на каждый рендер.
                graphics2D.dispose()
            }
        }

        return result
    }

    fun bi(): BufferedImage {
        when (params) {
            is AreaParams -> {
                val imageType = BufferedImage.TYPE_INT_ARGB
                val resultImage = BufferedImage(params.w, params.h, imageType)
                val graphics2D = resultImage.graphics as Graphics2D
                graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                val alphaChannel = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, params.opaque)
                graphics2D.composite = alphaChannel
                graphics2D.background = params.color
                graphics2D.color = params.color
                graphics2D.fillRect(0, 0, params.w, params.h)
                childs.forEach { pictureChild ->
                    graphics2D.drawImage(pictureChild.child.bi(), pictureChild.x, pictureChild.y, null)
                }
                graphics2D.dispose()
                return resultImage
            }

            is ImageParams -> {
                // BR-179 (аудит #336, пакет `PKG-S3-24`): путь к картинке проверяется
                // ЯВНО. Раньше `ImageIO.read(File(""))` бросал `IIOException`, а для
                // битого/неподдерживаемого файла `ImageIO.read` возвращал `null`, и
                // `resizeBufferedImage(null, …)` падал NPE. Исключение уходило в
                // вызывающий `catch`, и картинка просто не создавалась — без диагностики.
                val biImage = params.biImage ?: readImageOrThrow(params.pathToFile)

                val imageType = BufferedImage.TYPE_INT_ARGB
                val resultImage = BufferedImage(params.w, params.h, imageType)
                val graphics2D = resultImage.graphics as Graphics2D
                graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                val alphaChannel = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f)
                graphics2D.composite = alphaChannel

                graphics2D.drawImage(biImage, 0, 0, null)
                graphics2D.dispose()
                return resultImage
            }

            is TextParams -> {
                val imageType = BufferedImage.TYPE_INT_ARGB
                val resultImage = BufferedImage(params.w, params.h, imageType)
                val graphics2D = resultImage.graphics as Graphics2D
                graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                val alphaChannel = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f)
                graphics2D.composite = alphaChannel

                val font = Font(params.fontName, params.fontStyle, params.fontSize)

                graphics2D.color = params.color
                graphics2D.font = font

                if (params.isCalculatedSize) {
                    val fontSizeAndWHYsingleLine =
                        calcFontSizeAndWHY(
                            text = params.text,
                            w = params.w,
                            h = params.h,
                            font = Font(params.fontName, params.fontStyle, params.fontSize),
                        )
                    val fontSizeSingleLine = fontSizeAndWHYsingleLine.fontSize

                    val words = params.text.split(" ")

                    if (params.isLineBreak && words.size > 1) {
                        // BR-180: динамика по позициям разрыва вместо перебора 2^(n-1) вариантов.
                        val split =
                            selectBestSplit(
                                text = params.text,
                                w = params.w,
                                h = params.h,
                                font = Font(params.fontName, params.fontStyle, params.fontSize),
                            )
                        val lines = split?.lines ?: listOf(params.text)
                        val fswhy = split?.metrics ?: fontSizeAndWHYsingleLine

                        if (fswhy.fontSize > fontSizeSingleLine) {
                            lines.forEachIndexed { index, line ->

                                val lineSizeAndWHY =
                                    calcFontSizeAndWHY(
                                        text = line,
                                        w = params.w,
                                        h = params.h,
                                        font = Font(params.fontName, params.fontStyle, fswhy.fontSize),
                                        isFix = true,
                                    )

                                val deltaH = fswhy.h * index - fswhy.h / 4 + params.h / lines.size

                                val centerX = (params.w - lineSizeAndWHY.w) / 2
                                val centerY = deltaH

                                graphics2D.font = Font(params.fontName, params.fontStyle, lineSizeAndWHY.fontSize)
                                graphics2D.color = params.shadowColor
                                graphics2D.drawString(line, centerX + params.shadowOffset, centerY + params.shadowOffset)
                                graphics2D.color = params.color
                                graphics2D.drawString(line, centerX, centerY)
                            }
                        } else {
                            val centerX = (params.w - fontSizeAndWHYsingleLine.w) / 2
                            val centerY = (params.h + fontSizeAndWHYsingleLine.h) / 2 + fontSizeAndWHYsingleLine.y / 4
                            graphics2D.font = Font(params.fontName, params.fontStyle, fontSizeAndWHYsingleLine.fontSize)
                            graphics2D.color = params.shadowColor
                            graphics2D.drawString(params.text, centerX + params.shadowOffset, centerY + params.shadowOffset)
                            graphics2D.color = params.color
                            graphics2D.drawString(params.text, centerX, centerY)
                        }
                    } else {
                        val centerX = (params.w - fontSizeAndWHYsingleLine.w) / 2
                        val centerY = (params.h + fontSizeAndWHYsingleLine.h) / 2 + fontSizeAndWHYsingleLine.y / 4
                        graphics2D.font = Font(params.fontName, params.fontStyle, fontSizeAndWHYsingleLine.fontSize)
                        graphics2D.color = params.shadowColor
                        graphics2D.drawString(params.text, centerX + params.shadowOffset, centerY + params.shadowOffset)
                        graphics2D.color = params.color
                        graphics2D.drawString(params.text, centerX, centerY)
                    }
                } else {
                    val fontSizeAndWHY =
                        calcFontSizeAndWHY(
                            text = params.text,
                            w = params.w,
                            h = params.h,
                            font = Font(params.fontName, params.fontStyle, params.fontSize),
                            isFix = true,
                        )

                    val centerX = (params.w - fontSizeAndWHY.w) / 2
                    val centerY = (params.h + fontSizeAndWHY.h / 2) / 2 + fontSizeAndWHY.y / 4
                    graphics2D.font = Font(params.fontName, params.fontStyle, fontSizeAndWHY.fontSize)
                    graphics2D.color = params.shadowColor
                    graphics2D.drawString(params.text, centerX + params.shadowOffset, centerY + params.shadowOffset)
                    graphics2D.color = params.color
                    graphics2D.drawString(params.text, centerX, centerY)
                }

                graphics2D.dispose()
                return resultImage
            }

            else -> return BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        }
    }

    private fun resizeBufferedImage(
        img: BufferedImage,
        newW: Int,
        newH: Int,
    ): BufferedImage {
        val tmp = img.getScaledInstance(newW, newH, Image.SCALE_SMOOTH)
        val dimg = BufferedImage(newW, newH, BufferedImage.TYPE_INT_ARGB)
        val g2d = dimg.createGraphics()
        g2d.drawImage(tmp, 0, 0, null)
        g2d.dispose()
        return dimg
    }

    /**
     * Чтение картинки с явной проверкой (BR-179, аудит #336, пакет `PKG-S3-24`).
     *
     * Раньше `ImageIO.read(File(params.pathToFile))` вызывался «как есть»:
     * - пустой путь (`pathToFile` по умолчанию `""`) → `IIOException("Can't read input file!")`;
     * - отсутствующий файл → `IIOException`;
     * - неподдерживаемый/битый файл → `ImageIO.read` возвращает `null`, и
     *   `resizeBufferedImage(null, …)` падал NPE.
     *
     * Теперь все три случая дают явную ошибку с путём в сообщении.
     *
     * @param path путь к файлу картинки (непустой, существующий, читаемый как изображение).
     * @throws IllegalStateException если путь пуст, файла нет или формат не поддерживается.
     */
    private fun readImageOrThrow(path: String): BufferedImage {
        if (path.isBlank()) {
            throw IllegalStateException("picture.image_path_blank: путь к файлу картинки не задан")
        }
        val file = File(path)
        if (!file.isFile) {
            throw IllegalStateException("picture.image_file_missing: файл картинки '$path' не найден")
        }
        val image =
            ImageIO.read(file)
                ?: throw IllegalStateException("picture.image_unreadable: '$path' не читается как изображение")
        return image
    }

    /**
     * Лучшее разбиение текста на строки (BR-180, аудит #336, пакет `PKG-S3-24`).
     *
     * Прежняя реализация [getVariantsOfLines] перебирала ВСЕ разбиения текста на
     * строки — `2^(n-1)` вариантов для `n` слов, и каждый вариант полностью
     * измерялся (для каждой строки шёл подбор кегля с шагом 1 px). Название из 15 слов
     * давало 16 384 варианта, из 20 слов — 524 288, и рендер занимал минуты CPU
     * (риск OOM всего процесса `karaoke-app`).
     *
     * Теперь используется динамика по позициям разрыва: для каждого числа строк `k`
     * считается лучшее разбиение (максимальный минимальный кегль, затем минимальная
     * ширина самой широкой строки) — `O(n²·maxLines)` замеров вместо экспоненты.
     * Правило выбора совпадает с прежним: кегль считается по высоте `h / k`
     * (целочисленное деление), ширина — по самой широкой строке с минимальным кеглем.
     *
     * @param text текст для разбиения.
     * @param w ширина области.
     * @param h высота области.
     * @param font базовый шрифт (кегль подбирается по месту).
     * @return лучшее разбиение или `null`, если текст пуст / слишком длинный.
     */
    private fun selectBestSplit(
        text: String,
        w: Int,
        h: Int,
        font: Font,
    ): LineSplit? {
        val words = text.split(" ")
        val n = words.size
        if (n == 0) return null
        // Слишком длинный текст не разбиваем: прежний перебор на таком объёме был
        // непрактичен (минуты CPU, риск OOM). Остаётся однострочная раскладка.
        if (n > MAX_SPLIT_WORDS) return null
        val maxLines = minOf(n, MAX_SPLIT_LINES)

        // Один Graphics2D на все замеры: `calcFontSizeAndWHY` раньше создавал
        // BufferedImage(w, h) на КАЖДЫЙ замер (десятки мегабайт мусора за рендер).
        val measureImage = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        val measureGraphics = measureImage.graphics as Graphics2D
        measureGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        var bestSplit: LineSplit? = null
        var bestValue: SplitValue? = null
        var prevValues = arrayOfNulls<SplitValue>(n + 1)
        var prevBack = IntArray(n + 1) { -1 }

        try {
            for (k in 1..maxLines) {
                val lineHeight = h / k
                val lineMetrics = Array(n + 1) { arrayOfNulls<FontSizeAndWHY>(n + 1) }

                fun metricOf(
                    i: Int,
                    j: Int,
                ): FontSizeAndWHY {
                    lineMetrics[i][j]?.let { return it }
                    val measured =
                        calcFontSizeAndWHY(
                            text = words.subList(i, j).joinToString(" "),
                            w = w,
                            h = lineHeight,
                            font = font,
                            isFix = false,
                            graphics2D = measureGraphics,
                        )
                    lineMetrics[i][j] = measured
                    return measured
                }

                val curValues = arrayOfNulls<SplitValue>(n + 1)
                val curBack = IntArray(n + 1) { -1 }
                for (j in 1..n) {
                    val minI = k - 1
                    for (i in minI..j - 1) {
                        if (i == 0) {
                            if (k != 1) continue
                        } else if (prevValues[i] == null) {
                            continue
                        }
                        val line = metricOf(i, j)
                        val value =
                            if (i == 0) {
                                SplitValue.of(line)
                            } else {
                                prevValues[i]!!.combine(line)
                            }
                        val current = curValues[j]
                        if (current == null || SplitValue.isBetter(value, current)) {
                            curValues[j] = value
                            curBack[j] = i
                        }
                    }
                }

                val valueForK = curValues[n]
                if (valueForK != null && (bestValue == null || SplitValue.isBetter(valueForK, bestValue))) {
                    bestValue = valueForK
                    val lines = mutableListOf<String>()
                    var pos = n
                    while (pos > 0) {
                        val from = curBack[pos]
                        if (from < 0) break
                        lines.add(0, words.subList(from, pos).joinToString(" "))
                        pos = from
                    }
                    bestSplit = LineSplit(lines = lines, metrics = valueForK.metrics)
                }

                prevValues = curValues
                prevBack = curBack
            }
        } finally {
            measureGraphics.dispose()
        }
        return bestSplit
    }

    /**
     * Значение разбиения: минимальный кегль строк и ширина самой широкой строки
     * с этим кеглем (BR-180).
     *
     * Правило выбора совпадает с прежним: сначала максимальный кегль, затем
     * минимальная ширина.
     */
    private data class SplitValue(
        val fontSize: Int,
        val width: Int,
        val metrics: FontSizeAndWHY,
    ) {
        companion object {
            fun of(line: FontSizeAndWHY): SplitValue = SplitValue(line.fontSize, line.w, line)

            /**
             * `true`, если [a] лучше [b]: больше кегль, а при равном — меньше ширина.
             */
            fun isBetter(
                a: SplitValue,
                b: SplitValue,
            ): Boolean = a.fontSize > b.fontSize || (a.fontSize == b.fontSize && a.width < b.width)
        }

        /**
         * Объединение с ещё одной строкой: минимальный кегль; ширина — максимум
         * среди строк с этим кеглем.
         */
        fun combine(line: FontSizeAndWHY): SplitValue {
            val newFontSize = minOf(fontSize, line.fontSize)
            val newWidth =
                when {
                    line.fontSize < fontSize -> line.w
                    line.fontSize == fontSize -> maxOf(width, line.w)
                    else -> width
                }
            val newMetrics =
                if (line.fontSize < fontSize) {
                    line
                } else if (line.fontSize == fontSize && line.w >= width) {
                    line
                } else {
                    metrics
                }
            return SplitValue(newFontSize, newWidth, newMetrics)
        }
    }

    /** Разбиение текста на строки + метрики опорной строки (BR-180). */
    private data class LineSplit(
        val lines: List<String>,
        val metrics: FontSizeAndWHY,
    )

    fun calcFontSizeAndWHY(
        text: String,
        w: Int,
        h: Int,
        font: Font,
        isFix: Boolean = false,
    ): FontSizeAndWHY {
        val imageType = BufferedImage.TYPE_INT_ARGB
        val resultImage = BufferedImage(maxOf(w, 1), maxOf(h, 1), imageType)
        val graphics2D = resultImage.graphics as Graphics2D
        graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        return try {
            calcFontSizeAndWHY(text, w, h, font, isFix, graphics2D)
        } finally {
            // BR-180: Graphics2D освобождается — на каждый замер больше не остаётся
            // невысвобожденного нативного ресурса.
            graphics2D.dispose()
        }
    }

    /**
     * Замер текста на переиспользуемом [graphics2D] (BR-180).
     *
     * Метрики шрифта не зависят от размера изображения, поэтому для серии замеров
     * достаточно одного `Graphics2D` (1×1) вместо `BufferedImage(w, h)` на каждый вызов.
     */
    private fun calcFontSizeAndWHY(
        text: String,
        w: Int,
        h: Int,
        font: Font,
        isFix: Boolean,
        graphics2D: Graphics2D,
    ): FontSizeAndWHY {
        val initFontSize = if (isFix) font.size else 4
        var fnt = Font(font.fontName, font.style, initFontSize - 1)
        var rectW = 0
        var rectWprev: Int
        var rectH = 0
        var rectHprev: Int
        var rectY = 0
        var rectYprev: Int
        graphics2D.font = fnt
        do {
            rectWprev = rectW
            rectHprev = rectH
            rectYprev = rectY
            fnt = Font(fnt.fontName, fnt.style, fnt.size + 1)
            graphics2D.font = fnt
            val fontMetrics = graphics2D.fontMetrics
            val rect = fontMetrics.getStringBounds(text, graphics2D)
            rectW = rect.width.toInt()
            rectH = rect.height.toInt()
            rectY = rect.y.toInt()
            if (isFix) return FontSizeAndWHY(fontSize = fnt.size, w = rectW, h = rectH, y = rectY, text = text)
        } while (!(rectH > h || rectW > w))
        return FontSizeAndWHY(fontSize = fnt.size - 1, w = rectWprev, h = rectHprev, y = rectYprev, text = text)
    }

    data class FontSizeAndWHY(
        val fontSize: Int,
        val w: Int,
        val h: Int,
        val y: Int,
        val text: String,
    )
}

/**
 * Класс Picture Child.
 *
 * @see archive/docs/features/dual-db-sync.md
 */
data class PictureChild(
    val x: Int,
    val y: Int,
    val child: Picture,
)

/**
 * Класс Area Params.
 *
 * @see archive/docs/features/dual-db-sync.md
 */
data class AreaParams(
    val w: Int,
    val h: Int,
    val color: Color,
    val opaque: Float = 1f,
) : PictureParams

/**
 * Класс Image Params.
 *
 * @see archive/docs/features/dual-db-sync.md
 */
data class ImageParams(
    val w: Int,
    val h: Int,
    val pathToFile: String = "",
    val biImage: BufferedImage? = null,
) : PictureParams

/**
 * Класс Text Params.
 *
 * @see archive/docs/features/dual-db-sync.md
 */
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
    val shadowOffset: Int = 2,
) : PictureParams

/**
 * Интерфейс для picture params.
 *
 * @see archive/docs/features/dual-db-sync.md
 */
interface PictureParams
