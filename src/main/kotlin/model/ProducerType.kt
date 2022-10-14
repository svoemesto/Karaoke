package model

enum class ProducerType(val text: String, val onlyOne: Boolean, val suffixes: List<String>, val ids: List<Int>) {
    NONE(text = "none", onlyOne = false, suffixes = emptyList(), ids = emptyList()),
    AUDIOVOCAL(text = "audiovocal", onlyOne = true, suffixes = emptyList(), ids = emptyList()),
    AUDIOMUSIC(text ="audiomusic", onlyOne = true, suffixes = emptyList(), ids = emptyList()),
    AUDIOSONG(text ="audiosong", onlyOne = true, suffixes = emptyList(), ids = emptyList()),
    AUDIOBASS(text ="audiobass", onlyOne = true, suffixes = emptyList(), ids = emptyList()),
    AUDIODRUMS(text ="audiodrums", onlyOne = true, suffixes = emptyList(), ids = emptyList()),
    BACKGROUND(text ="background", onlyOne = true, suffixes = emptyList(), ids = emptyList()),
    HORIZON(text ="horizon", onlyOne = true, suffixes = emptyList(), ids = emptyList()),
    FLASH(text ="flash", onlyOne = true, suffixes = emptyList(), ids = emptyList()),
    PROGRESS(text ="progress", onlyOne = true, suffixes = emptyList(), ids = emptyList()),
    FILLCOLORSONGTEXT(text ="fillcolorsongtext", onlyOne = false, suffixes = listOf("even", "odd"), ids = emptyList()),
    SONGTEXT(text ="songtext", onlyOne = false, suffixes = emptyList(), ids = emptyList()),
    COUNTER(text ="counter", onlyOne = false, suffixes = emptyList(), ids = listOf(4,3,2,1,0)),
    FADERTEXT(text ="fadertext", onlyOne = true, suffixes = emptyList(), ids = emptyList()),
    FADERCHORDS(text ="faderchords", onlyOne = true, suffixes = emptyList(), ids = emptyList()),
    FINGERBOARD(text ="fingerboard", onlyOne = true, suffixes = emptyList(), ids = emptyList()),
    HEADER(text ="header", onlyOne = true, suffixes = emptyList(), ids = emptyList()),
    WATERMARK(text ="watermark", onlyOne = true, suffixes = emptyList(), ids = emptyList()),
    SPLASHSTART(text ="splashstart", onlyOne = true, suffixes = emptyList(), ids = emptyList())
}