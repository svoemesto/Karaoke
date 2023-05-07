package com.svoemesto.karaokeapp.model

import java.io.Serializable

fun main() {

    for (i in 0 .. 12) {
        val result = MusicChord.Xm.getFingerboard(MusicNote.G,i)
        if (result.isNotEmpty()) {
            println(result.joinToString("\n"))
            println()
        }
    }


//    GuitarString.values().forEach { gs -> println("${gs.text} - первая нота предыдущей струны на ладу № ${gs.fretNextString}") }

//    MusicNote.values().forEach { note ->
//        GuitarString.values().forEach { gs -> println( "Нота ${note.names.first()} ${gs.getFrets(note).joinToString("\nНота ${note.names.first()} ") { "${gs.text} лад ${it.fret} октава ${it.octave}" }}")}
//        println()
//    }

//    MusicNote.values().forEach { note -> println("${note.names.first()} : ${note.getStringsFrets().joinToString(" | ")}")}

//    MusicInterval.values().forEach { mi ->
//        val mn = MusicNote.E
//        println("От ноты ${mn.names.first()} ${mi.text} = ${mi.getMusicNote(mn).first.names.first()} (+${mi.getMusicNote(mn).second})")
//    }

//    println(MusicChord.Xm.getNotes(MusicNote.E))

//    GuitarString.values().forEach { gs ->
//        println(gs.getPrintedString(MusicChord.Xm.getNotes(MusicNote.A).map { it.first }))
//    }

//    println(MusicChord.getChordNote("F#sus4"))

}

enum class GuitarString(val text: String, val number: Int, val musicNote: MusicNote, val octave: Int, val countFrets: Int = 25) : Serializable {
    GS1(text = "1-я струна", number = 1, musicNote = MusicNote.E, octave = 4),
    GS2(text = "2-я струна", number = 2, musicNote = MusicNote.B, octave = 3),
    GS3(text = "3-я струна", number = 3, musicNote = MusicNote.G, octave = 3),
    GS4(text = "4-я струна", number = 4, musicNote = MusicNote.D, octave = 3),
    GS5(text = "5-я струна", number = 5, musicNote = MusicNote.A, octave = 2),
    GS6(text = "6-я струна", number = 6, musicNote = MusicNote.E, octave = 2);

    val fretNextString: Int get() {
        var currString = this
        val strings = GuitarString.values()
        val currStringIndex = Integer.max(strings.indexOf(currString), 1)
        currString = strings[currStringIndex]
        val prevString = strings[currStringIndex - 1]
        val interval = MusicNote.getInterval(
            Pair(currString.musicNote, currString.octave),
            Pair(prevString.musicNote, prevString.octave)
        )
        return interval?.halfTones ?: -1
    }
}


fun GuitarString.getNotes(): List<NoteOctaveFret> {
    return (0 until countFrets).map { fret -> getNote(fret) }.toList()
}

fun GuitarString.getNote(fret: Int = 0): NoteOctaveFret {
    return NoteOctaveFret(
        musicNote = MusicNote.values()[(MusicNote.values().indexOf(musicNote) + fret) % 12],
        octave = octave + ((fret+ MusicNote.values().indexOf(musicNote)) / 12),
        fret =  fret
    )
}

fun GuitarString.getFrets(note: MusicNote): List<NoteOctaveFret> {
    return getNotes().filter { it.musicNote == note }.toList()
}

fun GuitarString.getPrintedString(notes: List<MusicNote>): String {
    var strString = "|-----".repeat(countFrets)
    notes
        .flatMap { note -> getFrets(note).map { nof -> Pair(nof.fret,notes.indexOf(note))}
        }
        .forEach { fretAndIndex -> strString = strString.replaceRange(fretAndIndex.first*6+3,fretAndIndex.first*6+4, fretAndIndex.second.toString())}
    return strString
}
