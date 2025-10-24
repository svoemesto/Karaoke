package com.svoemesto.karaokeapp

import be.tarsos.dsp.AudioDispatcher
import be.tarsos.dsp.io.jvm.JVMAudioInputStream
import be.tarsos.dsp.onsets.OnsetHandler
import be.tarsos.dsp.onsets.PercussionOnsetDetector
import java.io.File
import javax.sound.sampled.AudioSystem

class MyOnsetHandler : OnsetHandler {
    private var lastOnsetTime = 0.0
    private var bpm = 0.0

    override fun handleOnset(time: Double, salience: Double) {
        if (lastOnsetTime > 0) {
            val interval = time - lastOnsetTime
            // Рассчитываем BPM на основе временного интервала между ударами
            bpm = 60.0 / interval
        }
        lastOnsetTime = time
        println("Re-re")
    }

    fun getBPM(): Double {
        return bpm
    }
}
@Suppress("unused")
fun mainAudioAnalize2() {
    val audioFile = File("/sm-karaoke/system/Infornal Fuckъ - Конунг Олаф Моржовый Хер/Конунг Олаф Моржовый Хер.wav")
    val audioInputStream = AudioSystem.getAudioInputStream(audioFile)
    val jVMAudioInputStream = JVMAudioInputStream(audioInputStream)
    val bufferSize = 1024 // Размер буфера
    val bufferOverlap = 512 // Перекрытие буферов
    val sampleRate = 44100.0f
    val audioDispatcher = AudioDispatcher(jVMAudioInputStream, bufferSize, bufferOverlap)
    val onsetHandler = MyOnsetHandler()

    val onsetDetector = PercussionOnsetDetector(sampleRate, bufferSize, onsetHandler, 1.0, 1.0)

    audioDispatcher.addAudioProcessor(onsetDetector)
    audioDispatcher.run()

    val bpm = onsetHandler.getBPM()

    println("Темп аудиофайла: $bpm bpm")
}
