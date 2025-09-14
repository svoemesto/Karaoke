import be.tarsos.dsp.AudioDispatcher
import be.tarsos.dsp.io.jvm.AudioDispatcherFactory
import be.tarsos.dsp.pitch.PitchProcessor
import java.io.File
import javax.sound.sampled.AudioSystem

fun mainAudioAnalize(args: Array<String>) {
    val file = File("/sm-karaoke/system/Infornal Fuckъ - Конунг Олаф Моржовый Хер/Конунг Олаф Моржовый Хер.wav")
    val audioInputStream = AudioSystem.getAudioInputStream(file)
    val dispatcher = AudioDispatcherFactory.fromFile(file, 2048, 0)
    val silenceDetector = PitchProcessor(PitchProcessor.PitchEstimationAlgorithm.YIN, 22050f, 2048, { pitchDetectionResult, _ ->
        val pitchInHz = pitchDetectionResult.pitch
        if (pitchInHz != -1f) {
            val note = getNoteFromPitch(pitchInHz)
            println("Detected pitch: $pitchInHz Hz ($note)")
        } else {
            println("No pitch detected")
        }
    })
    dispatcher.addAudioProcessor(silenceDetector)
    dispatcher.run()
}

fun getNoteFromPitch(pitchInHz: Float): String {
    val octave = (Math.log(pitchInHz / 440.0) / Math.log(2.0)).toInt() + 4
    val note = ((12 * Math.log(pitchInHz / 440.0) / Math.log(2.0)) % 12).toInt()
    return "${getNoteName(note)}$octave"
}

fun getNoteName(note: Int): String {
    return when (note) {
        0 -> "C"
        1 -> "C#"
        2 -> "D"
        3 -> "D#"
        4 -> "E"
        5 -> "F"
        6 -> "F#"
        7 -> "G"
        8 -> "G#"
        9 -> "A"
        10 -> "A#"
        11 -> "B"
        else -> "Unknown"
    }
}
