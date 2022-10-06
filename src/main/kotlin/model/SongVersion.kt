package model

enum class SongVersion(val text: String, val suffix: String, val producers: List<ProducerType>) {
    LYRICS(text = "Lyrics", suffix = "[lyrics]",
        producers = listOf(
            ProducerType.AUDIOSONG,
            ProducerType.BACKGROUND,
            ProducerType.HORIZON,
            ProducerType.PROGRESS,
            ProducerType.FILLCOLORSONGTEXT,
            ProducerType.SONGTEXT,
            ProducerType.FADER,
            ProducerType.HEADER,
            ProducerType.COUNTER,
            ProducerType.WATERMARK
        )
    ),
    KARAOKE(text = "Karaoke", suffix = "[karaoke]",
        producers = listOf(
            ProducerType.AUDIOMUSIC,
            ProducerType.BACKGROUND,
            ProducerType.HORIZON,
            ProducerType.PROGRESS,
            ProducerType.FILLCOLORSONGTEXT,
            ProducerType.SONGTEXT,
            ProducerType.FADER,
            ProducerType.HEADER,
            ProducerType.COUNTER,
            ProducerType.WATERMARK
        )
    ),
    CHORDS(text = "Chords", suffix = "[chords]",
        producers = listOf(
            ProducerType.AUDIOBASS,
            ProducerType.AUDIODRUMS,
            ProducerType.BACKGROUND,
            ProducerType.HORIZON,
            ProducerType.PROGRESS,
            ProducerType.FILLCOLORCHORDS,
            ProducerType.CHORDS,
            ProducerType.FADER,
            ProducerType.HEADER,
            ProducerType.COUNTER,
            ProducerType.WATERMARK
        ))
}