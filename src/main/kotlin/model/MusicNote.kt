package model

enum class MusicNote(val text: String, val names: List<String>, val frequencies: List<Double>) {
    C (text = "до", names = listOf("C", "B#", "B♯"), frequencies = listOf(16.35, 32.7, 65.41, 130.8, 261.6, 523.3, 1047.0, 2093.0, 4186.0)),
    C_SHARP (text = "до диез", names = listOf("C#", "C♯", "D♭", "Db"), frequencies = listOf(17.32, 34.65, 69.3, 138.6, 277.2, 554.4, 1109.0, 2217.0, 4435.0)),
    D (text = "ре", names = listOf("D"), frequencies = listOf(18.35, 36.71, 73.42, 146.8, 293.7, 587.3, 1175.0, 2349.0, 4699.0)),
    D_SHARP (text = "ре диез", names = listOf("D#", "E♭", "D♯", "Eb"), frequencies = listOf(19.45, 38.89, 77.78, 155.6, 311.1, 622.3, 1245.0, 2489.0, 4978.0)),
    E (text = "ми", names = listOf("E", "F♭", "Fb"), frequencies = listOf(20.6, 41.2, 82.41, 164.8, 329.6, 659.3, 1319.0, 2637.0, 5274.0)),
    F (text = "фа", names = listOf("F", "E#", "E♯"), frequencies = listOf(21.83, 43.65, 87.31, 174.6, 349.2, 698.5, 1397.0, 2794.0, 5588.0)),
    F_SHARP (text = "фа диез", names = listOf("F#", "G♭", "F♯", "Gb"), frequencies = listOf(23.12, 46.25, 92.5, 185.0, 370.0, 740.0, 1480.0, 2960.0, 5920.0)),
    G (text = "соль", names = listOf("G"), frequencies = listOf(24.5, 49.0, 98.0, 196.0, 392.0, 874.0, 1568.0, 3136.0, 6272.0)),
    G_SHARP (text = "соль диез", names = listOf("G#", "A♭", "G♯", "Ab"), frequencies = listOf(25.96, 51.91, 103.8, 207.7, 415.3, 830.6, 1661.0, 3322.0, 6645.0)),
    A (text = "ля", names = listOf("A",), frequencies = listOf(27.5, 55.0, 110.0, 220.0, 440.0, 880.0, 1760.0, 3520.0, 7040.0)),
    A_SHARP (text = "ля диез", names = listOf("A#", "B♭", "A♯", "Bb"), frequencies = listOf(29.14, 58.27, 116.5, 233.1, 466.2, 932.3, 1865.0, 3729.0, 7459.0)),
    B (text = "си", names = listOf("B", "C♭", "Cb"), frequencies = listOf(30.87, 61.74, 123.5, 246.9, 493.9, 987.8, 1976.0, 3951.0, 7902.0));

    companion object {
        fun getNote(noteName: String): MusicNote? {
            return MusicNote.values().firstOrNull { it.names.contains(noteName) }
        }
        fun getInterval(firstNoteOctave: Pair<MusicNote,Int>, secondNoteOctave: Pair<MusicNote,Int>): MusicInterval? {
            val intervalsNotesOctaves =  MusicInterval.getIntervals(firstNoteOctave.first, firstNoteOctave.second)
            return intervalsNotesOctaves.firstOrNull { it.second == secondNoteOctave.first && it.third == secondNoteOctave.second }?.first
        }
    }
}

data class NoteOctaveFret(val musicNote: MusicNote, val octave: Int, val fret: Int)

fun MusicNote.getStringsFrets(): List<Pair<GuitarString, List<Int>>> {
    return GuitarString.values().map { gs -> Pair(gs, gs.getFrets(this).map { it.fret }) }
}
