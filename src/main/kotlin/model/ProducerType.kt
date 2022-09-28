package model

enum class ProducerType(val text: String, val orderInTimeline: Int, val onlyOne: Boolean) {
    NONE(text = "none", orderInTimeline = 0, onlyOne = false),
    AUDIOVOCAL(text = "audiovocal", orderInTimeline = 1, onlyOne = true),
    AUDIOMUSIC(text ="audiomusic", orderInTimeline = 2, onlyOne = true),
    AUDIOSONG(text ="audiosong", orderInTimeline = 3, onlyOne = true),
    BACKGROUND(text ="background", orderInTimeline = 4, onlyOne = true),
    MICROPHONE(text ="microphone", orderInTimeline = 5, onlyOne = true),
    HORIZON(text ="horizon", orderInTimeline = 6, onlyOne = true),
    PROGRESS(text ="progress", orderInTimeline = 7, onlyOne = true),
    FILLCOLOR(text ="fillcolor", orderInTimeline = 8, onlyOne = false),
    SONGTEXT(text ="songtext", orderInTimeline = 9, onlyOne = false),
    HEADER(text ="header", orderInTimeline = 10, onlyOne = true),
    LOGOTYPE(text ="logotype", orderInTimeline = 11, onlyOne = true),
    BEAT(text ="beat", orderInTimeline = 12, onlyOne = false),
    COUNTER(text ="counter", orderInTimeline = 13, onlyOne = false),
    WATERMARK(text ="watermark", orderInTimeline = 14, onlyOne = true)
}