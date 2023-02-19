import model.ProducerType

val idProducerSongText = ProducerType.SONGTEXT.ordinal * 10
val idProducerHorizon = ProducerType.HORIZON.ordinal * 10
val idProducerFillColorSongtextEven = ProducerType.FILLCOLORSONGTEXT.ordinal * 10
val idProducerFillColorSongtextOdd = ProducerType.FILLCOLORSONGTEXT.ordinal * 10 + 1
val idProducerHeader = ProducerType.HEADER.ordinal * 10
val idProducerBackground = ProducerType.BACKGROUND.ordinal * 10
val idProducerCounter4 = ProducerType.COUNTER.ordinal * 10 + 4
val idProducerCounter3 = ProducerType.COUNTER.ordinal * 10 + 3
val idProducerCounter2 = ProducerType.COUNTER.ordinal * 10 + 2
val idProducerCounter1 = ProducerType.COUNTER.ordinal * 10 + 1
val idProducerCounter0 = ProducerType.COUNTER.ordinal * 10
val idProducerAudioSong = ProducerType.AUDIOSONG.ordinal * 10
val idProducerAudioMusic = ProducerType.AUDIOMUSIC.ordinal * 10
val idProducerAudioVocal = ProducerType.AUDIOVOCAL.ordinal * 10
val idProducerAudioBass = ProducerType.AUDIOBASS.ordinal * 10
val idProducerAudioDrums = ProducerType.AUDIODRUMS.ordinal * 10
val idProducerProgress = ProducerType.PROGRESS.ordinal * 10 + 1
val idProducerWatermark = ProducerType.WATERMARK.ordinal * 10 + 1
val idProducerFaderText = ProducerType.FADERTEXT.ordinal * 10 + 1
val idProducerFaderChords = ProducerType.FADERCHORDS.ordinal * 10 + 1
val idProducerBackChords = ProducerType.BACKCHORDS.ordinal * 10 + 1
val idProducerFingerboard = ProducerType.FINGERBOARD.ordinal * 10 + 1
val idProducerSplashstart = ProducerType.SPLASHSTART.ordinal * 10 + 1
val idProducerBoosty = ProducerType.BOOSTY.ordinal * 10 + 1
val idProducerFlash = ProducerType.FLASH.ordinal * 10 + 1


val delimiterVoices = "|[VOICE]|"
val delimiterGroups = "|[GROUP]|"
val delimiterFields = "|[FIELD]|"
val delimiterVoiceFields = "|[VOICEFIELDS]|"
val delimiterNames = "|[NAME]|"

const val LINE_SPACING = 0L
const val SHADOW = "1;#64000000;3;3;3"
const val ALIGNMENT = 0L
const val TYPEWRITER = "0;2;1;0;0"
const val LETTERS_VOWEL = "EUIOAeuioaЁУЕЫАОЭЯИЮёуеыаоэяию"
const val NOTES_SYMBOLS = "♬♩♪△▲⬦⬥"

const val DEMUCS_MODEL_NAME = "hdemucs_mmi"