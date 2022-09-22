package model

import FONT_SYMBOL_WIDTH_ASPECT
import FRAME_HEIGHT_PX
import KLT_ITEM_CONTENT_FONT_SIZE_PT
import PT_TO_PX

class Lyric {
    var items: List<LyricLine> = emptyList()
    var fontSize: Int = KLT_ITEM_CONTENT_FONT_SIZE_PT
    var horizontPosition = FRAME_HEIGHT_PX / 2
    var symbolWidth: Double = fontSize * PT_TO_PX
    var symbolHeight: Int = (fontSize * FONT_SYMBOL_WIDTH_ASPECT).toInt()
}

