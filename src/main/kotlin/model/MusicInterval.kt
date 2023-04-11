package model

import java.io.Serializable

enum class MusicInterval (val halfTones: Int, val text: String) : Serializable {
    UNISON (halfTones = 0, text ="чистая прима (унисон)"),
    MINOR_SECOND (halfTones = 1, text ="малая секунда (полутон)"),
    MAJOR_SECOND (halfTones = 2, text ="большая секунда (целый тон)"),
    MINOR_THIRD (halfTones = 3, text ="малая терция (полудитон)"),
    MAJOR_THIRD (halfTones = 4, text ="большая терция (дитон)"),
    PERFECT_FOURTH (halfTones = 5, text ="чистая кварта"),
    AUGMENTED_FOURTH (halfTones = 6, text ="увеличенная кварта (тритон)"),
    DIMINISHED_FIFTH (halfTones = 6, text ="уменьшенная квинта (тритон)"),
    PERFECT_FIFTH (halfTones = 7, text ="чистая квинта"),
    MINOR_SIXTH (halfTones = 8, text ="малая секста"),
    MAJOR_SIXTH (halfTones = 9, text ="большая секста"),
    MINOR_SEVENTH (halfTones = 10, text ="малая септима"),
    MAJOR_SEVENTH (halfTones = 11, text ="большая септима"),
    PERFECT_OCTAVE (halfTones = 12, text ="чистая октава"),
    MINOR_NINTH (halfTones = 13, text ="малая нона"),
    MAJOR_NINTH (halfTones = 14, text ="большая нона"),
    MINOR_TENTH (halfTones = 15, text ="малая децима"),
    MAJOR_TENTH (halfTones = 16, text ="большая децима"),
    PERFECT_ELEVENTH (halfTones = 17, text ="чистая ундецима"),
    AUGMENTED_ELEVENTH (halfTones = 18, text ="увеличенная ундецима"),
    DIMINISHED_TWELFTH (halfTones = 18, text ="уменьшенная дуодецима"),
    PERFECT_TWELFTH (halfTones = 19, text ="чистая дуодецима"),
    MINOR_THIRTEENTH (halfTones = 20, text ="малая терцдецима"),
    MAJOR_THIRTEENTH (halfTones = 21, text ="большая терцдецима"),
    MINOR_FOURTEENTH (halfTones = 22, text ="малая квартдецима"),
    MAJOR_FOURTEENTH (halfTones = 23, text ="большая квартдецима"),
    PERFECT_FIFTEENTH (halfTones = 24, text ="чистая квинтдецима");

    companion object {
        // Список интервал-нота-октава
        fun getIntervals(rootMusicNote: MusicNote, rootOctave: Int = 0): List<Triple<MusicInterval, MusicNote, Int>> {
            val result: MutableList<Triple<MusicInterval, MusicNote, Int>> = mutableListOf()
            MusicInterval.values().forEach { mi ->
                val (note, octave) = mi.getMusicNote(rootMusicNote, rootOctave)
                result.add(Triple(mi, note, octave))
            }
            return result
        }
    }

}

fun MusicInterval.getMusicNote(rootMusicNote: MusicNote, rootOctave: Int = 0): Pair<MusicNote, Int> =
    Pair(
        MusicNote.values()[(MusicNote.values().indexOf(rootMusicNote) + halfTones) % 12],
        (MusicNote.values().indexOf(rootMusicNote) + halfTones) / 12 + rootOctave
    )