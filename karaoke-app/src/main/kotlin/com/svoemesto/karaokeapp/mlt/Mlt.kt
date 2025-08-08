package com.svoemesto.karaokeapp.mlt

import com.svoemesto.karaokeapp.getStoredUuid
import com.svoemesto.karaokeapp.model.MltNode
import com.svoemesto.karaokeapp.model.ProducerType
import com.svoemesto.karaokeapp.model.childs
import com.svoemesto.karaokeapp.producerTypeClass
import getMltConsumer
import getMltProfile

/*
Все классы продюссеров должны иметь конструкторы с одинаковым количеством, типом и последовательностью аргументов
Чтобы их можно было вызвать с помощью рефлексии.
mltProp: MltProp - набор параметров
type: ProducerType - тип продюсера
voiceId: Int - id войса
childId: Int - id чилда (или линии)
elementId: Int - id элемента

НЕ ЗАБЫВАТЬ ПРОПИСЫВАТЬ КЛАССЫ В producerTypeClass
 */

data class MltInitialStructure(
    var mltProp: MltProp,
    var type: ProducerType,
    var voiceId: Int = -1,
    var childId: Int = -1,
    var elementId: Int = -1
) {
}
fun getMisList(mltProp: MltProp): List<MltInitialStructure> {
    val result: MutableList<MltInitialStructure> = mutableListOf()

    val songVersion = mltProp.getSongVersion()
    val settings = mltProp.getSettings() ?: return result
    val songProducers = songVersion.producers
    val listOfVoices = settings.voicesForMlt
    listOfVoices.forEachIndexed { indexVoice, voice ->

        voice.linesForMlt().forEachIndexed { indexLine, line ->
            if (!line.isEmptyLine) {
                line.getElements(songVersion).forEachIndexed { indexElement, element ->

                    if (ProducerType.ELEMENT in songProducers) {

                        ProducerType.ELEMENT.childs().asReversed().forEach {
                            if (it in songProducers) {
                                val key = listOf(it, indexVoice, indexLine, indexElement)
                                if (mltProp.getUUID(key) == "") mltProp.setUUID(getStoredUuid(key), key)
                                result.add(MltInitialStructure(mltProp, it, indexVoice, indexLine, indexElement))
                                mltProp.setCountChilds(line.getElements(songVersion).size, listOf(it, indexVoice, indexLine))
                            }
                        }

                        val key = listOf(ProducerType.ELEMENT, indexVoice, indexLine, indexElement)
                        if (mltProp.getUUID(key) == "") mltProp.setUUID(getStoredUuid(key), key)
                        result.add(MltInitialStructure(mltProp, ProducerType.ELEMENT, indexVoice, indexLine, indexElement))
                        mltProp.setCountChilds(line.getElements(songVersion).size, listOf(ProducerType.ELEMENT, indexVoice, indexLine))

                    }
                }
                if (ProducerType.LINE in songProducers) {
                    val key = listOf(ProducerType.LINE, indexVoice, indexLine)
                    if (mltProp.getUUID(key) == "") mltProp.setUUID(getStoredUuid(key), key)

                    mltProp.setDurationOnScreen(line.endVisibleTime - line.startVisibleTime, listOf(ProducerType.LINE, indexVoice, indexLine))
                    result.add(MltInitialStructure(mltProp, ProducerType.LINE, indexVoice, indexLine))
                }
            }
        }

        val chords = mltProp.getChords()
        chords.forEachIndexed { indexChord, chord ->
            val indexLine = indexChord

            if (ProducerType.CHORDPICTUREELEMENT in songProducers) {

                ProducerType.CHORDPICTUREELEMENT.childs().asReversed().forEach {
                    if (it in songProducers) {
                        val key = listOf(it, indexVoice, indexLine)
                        if (mltProp.getUUID(key) == "") mltProp.setUUID(getStoredUuid(key), key)
                        result.add(MltInitialStructure(mltProp, it, indexVoice, indexLine))
                    }
                }

                val key = listOf(ProducerType.CHORDPICTUREELEMENT, indexVoice, indexLine)
                if (mltProp.getUUID(key) == "") mltProp.setUUID(getStoredUuid(key), key)
                result.add(MltInitialStructure(mltProp, ProducerType.CHORDPICTUREELEMENT, indexVoice, indexLine))
            }

            if (ProducerType.CHORDPICTURELINE in songProducers) {
                val key = listOf(ProducerType.CHORDPICTURELINE, indexVoice, indexLine)
                if (mltProp.getUUID(key) == "") mltProp.setUUID(getStoredUuid(key), key)
                mltProp.setDurationOnScreen(chord.endChordVisibleTime - chord.startChordVisibleTime, listOf(ProducerType.CHORDPICTURELINE, indexVoice, indexLine))
                result.add(MltInitialStructure(mltProp, ProducerType.CHORDPICTURELINE, indexVoice, indexLine))
            }
        }


        if (ProducerType.CHORDPICTURELINETRACK in songProducers) {
            val cnt = voice.countChordPictureTracks
            mltProp.setCountChilds(cnt, listOf(ProducerType.CHORDPICTURELINETRACK, indexVoice))
            for (indexTrack in 0 until cnt) {
                val key = listOf(ProducerType.CHORDPICTURELINETRACK, indexVoice, indexTrack)
                if (mltProp.getUUID(key) == "") mltProp.setUUID(getStoredUuid(key), key)
                result.add(MltInitialStructure(mltProp, ProducerType.CHORDPICTURELINETRACK, indexVoice, indexTrack))
            }
        }

        if (ProducerType.CHORDPICTURELINES in songProducers) {
            val key = listOf(ProducerType.CHORDPICTURELINES, indexVoice)
            if (mltProp.getUUID(key) == "") mltProp.setUUID(getStoredUuid(key), key)
            result.add(MltInitialStructure(mltProp, ProducerType.CHORDPICTURELINES, indexVoice))
        }

        if (ProducerType.LINETRACK in songProducers) {
            val cnt = voice.countLineTracks
            mltProp.setCountChilds(cnt, listOf(ProducerType.LINETRACK, indexVoice))
            for (indexChild in 0 until cnt) {
                val key = listOf(ProducerType.LINETRACK, indexVoice, indexChild)
                if (mltProp.getUUID(key) == "") mltProp.setUUID(getStoredUuid(key), key)
                result.add(MltInitialStructure(mltProp, ProducerType.LINETRACK, indexVoice, indexChild))
            }
        }

        if (ProducerType.CHORDPICTUREFADER in songProducers) {
            val key = listOf(ProducerType.CHORDPICTUREFADER, indexVoice)
            if (mltProp.getUUID(key) == "") mltProp.setUUID(getStoredUuid(key), key)
            result.add(MltInitialStructure(mltProp, ProducerType.CHORDPICTUREFADER, indexVoice))
        }

        if (ProducerType.CHORDSBOARD in songProducers) {
            val key = listOf(ProducerType.CHORDSBOARD, indexVoice)
            if (mltProp.getUUID(key) == "") mltProp.setUUID(getStoredUuid(key), key)

            result.add(MltInitialStructure(mltProp, ProducerType.CHORDSBOARD, indexVoice))
        }

        if (ProducerType.LINES in songProducers) {
            val key = listOf(ProducerType.LINES, indexVoice)
            if (mltProp.getUUID(key) == "") mltProp.setUUID(getStoredUuid(key), key)
            mltProp.setCountChilds(voice.linesForMlt().size, listOf(ProducerType.LINE, indexVoice))
            result.add(MltInitialStructure(mltProp, ProducerType.LINES, indexVoice))
        }

        if (ProducerType.COUNTER in songProducers) {
            ProducerType.COUNTER.ids.forEach { childId->
                val key = listOf(ProducerType.COUNTER, indexVoice, childId)
                if (mltProp.getUUID(key) == "") mltProp.setUUID(getStoredUuid(key), key)
                result.add(MltInitialStructure(mltProp, ProducerType.COUNTER, indexVoice, childId))
            }
        }
        if (ProducerType.COUNTERS in songProducers) {
            val key = listOf(ProducerType.COUNTERS, indexVoice)
            if (mltProp.getUUID(key) == "") mltProp.setUUID(getStoredUuid(key), key)

            mltProp.setCountChilds(ProducerType.COUNTER.ids.size, listOf(ProducerType.COUNTER, indexVoice))
            result.add(MltInitialStructure(mltProp, ProducerType.COUNTERS, indexVoice))
        }

        if (ProducerType.FILLCOLORSONGTEXT in songProducers) {
            ProducerType.FILLCOLORSONGTEXT.ids.forEach { childId->
                val key = listOf(ProducerType.FILLCOLORSONGTEXT, indexVoice, childId)
                if (mltProp.getUUID(key) == "") mltProp.setUUID(getStoredUuid(key), key)
                result.add(MltInitialStructure(mltProp, ProducerType.FILLCOLORSONGTEXT, indexVoice, childId))
            }
        }
        if (ProducerType.FILLCOLORSONGTEXTS in songProducers) {
            val key = listOf(ProducerType.FILLCOLORSONGTEXTS, indexVoice)
            if (mltProp.getUUID(key) == "") mltProp.setUUID(getStoredUuid(key), key)
            mltProp.setCountChilds(ProducerType.FILLCOLORSONGTEXT.ids.size, listOf(ProducerType.FILLCOLORSONGTEXT, indexVoice))
            result.add(MltInitialStructure(mltProp, ProducerType.FILLCOLORSONGTEXTS, indexVoice))
        }

        if (ProducerType.SONGTEXT in songProducers) {
            val key = listOf(ProducerType.SONGTEXT, indexVoice)
            if (mltProp.getUUID(key) == "") mltProp.setUUID(getStoredUuid(key), key)
            result.add(MltInitialStructure(mltProp, ProducerType.SONGTEXT, indexVoice))
        }

        if (ProducerType.SCROLLER in songProducers) {
            val cnt = mltProp.getCountChilds(listOf(ProducerType.SCROLLER, indexVoice))
            for (indexChild in 0 until cnt) {
                val key = listOf(ProducerType.SCROLLER, indexVoice, indexChild)
                if (mltProp.getUUID(key) == "") mltProp.setUUID(getStoredUuid(key), key)
                result.add(MltInitialStructure(mltProp, ProducerType.SCROLLER, indexVoice, indexChild))
            }
        }
        if (ProducerType.SCROLLERTRACK in songProducers) {
            val cnt = mltProp.getCountChilds(listOf(ProducerType.SCROLLERTRACK, indexVoice))
            for (indexChild in 0 until cnt) {
                val key = listOf(ProducerType.SCROLLERTRACK, indexVoice, indexChild)
                if (mltProp.getUUID(key) == "") mltProp.setUUID(getStoredUuid(key), key)
                result.add(MltInitialStructure(mltProp, ProducerType.SCROLLERTRACK, indexVoice, indexChild))
            }
        }
        if (ProducerType.SCROLLERS in songProducers) {
            val cnt = mltProp.getCountChilds(listOf(ProducerType.SCROLLERS, indexVoice))

            for (indexChild in 0 until cnt) {
                val key = listOf(ProducerType.SCROLLERS, indexVoice, indexChild)
                if (mltProp.getUUID(key) == "") mltProp.setUUID(getStoredUuid(key), key)
                result.add(MltInitialStructure(mltProp, ProducerType.SCROLLERS, indexVoice, indexChild))
            }

            val key = listOf(ProducerType.SCROLLERS, indexVoice)
            if (mltProp.getUUID(key) == "") mltProp.setUUID(getStoredUuid(key), key)

            result.add(MltInitialStructure(mltProp, ProducerType.SCROLLERS, indexVoice))
        }

        if (ProducerType.VOICE in songProducers) {
            val key = listOf(ProducerType.VOICE, indexVoice)
            if (mltProp.getUUID(key) == "") mltProp.setUUID(getStoredUuid(key), key)

            result.add(MltInitialStructure(mltProp, ProducerType.VOICE, indexVoice))
        }

    }




    if (ProducerType.VOICES in songProducers) {
        val key = listOf(ProducerType.VOICES)
        if (mltProp.getUUID(key) == "") mltProp.setUUID(getStoredUuid(key), key)

        result.add(MltInitialStructure(mltProp, ProducerType.VOICES))
    }

    if (ProducerType.BOOSTY in songProducers) {
        val key = listOf(ProducerType.BOOSTY)
        if (mltProp.getUUID(key) == "") mltProp.setUUID(getStoredUuid(key), key)
        result.add(MltInitialStructure(mltProp, ProducerType.BOOSTY))
    }

    if (ProducerType.SPLASHSTART in songProducers) {
        val key = listOf(ProducerType.SPLASHSTART)
        if (mltProp.getUUID(key) == "") mltProp.setUUID(getStoredUuid(key), key)
        result.add(MltInitialStructure(mltProp, ProducerType.SPLASHSTART))
    }

    if (ProducerType.WATERMARK in songProducers) {
        val key = listOf(ProducerType.WATERMARK)
        if (mltProp.getUUID(key) == "") mltProp.setUUID(getStoredUuid(key), key)
        result.add(MltInitialStructure(mltProp, ProducerType.WATERMARK))
    }

    if (ProducerType.HEADER in songProducers) {
        val key = listOf(ProducerType.HEADER)
        if (mltProp.getUUID(key) == "") mltProp.setUUID(getStoredUuid(key), key)
        result.add(MltInitialStructure(mltProp, ProducerType.HEADER))
    }

    if (ProducerType.FINGERBOARD in songProducers) {
        val key = listOf(ProducerType.FINGERBOARD)
        if (mltProp.getUUID(key) == "") mltProp.setUUID(getStoredUuid(key), key)
        result.add(MltInitialStructure(mltProp, ProducerType.FINGERBOARD))
    }

    if (ProducerType.BACKCHORDS in songProducers) {
        val key = listOf(ProducerType.BACKCHORDS)
        if (mltProp.getUUID(key) == "") mltProp.setUUID(getStoredUuid(key), key)
        result.add(MltInitialStructure(mltProp, ProducerType.BACKCHORDS))
    }

    if (ProducerType.FADERTEXT in songProducers) {
        val key = listOf(ProducerType.FADERTEXT)
        if (mltProp.getUUID(key) == "") mltProp.setUUID(getStoredUuid(key), key)
        result.add(MltInitialStructure(mltProp, ProducerType.FADERTEXT))
    }

    if (ProducerType.PROGRESS in songProducers) {
        val key = listOf(ProducerType.PROGRESS)
        if (mltProp.getUUID(key) == "") mltProp.setUUID(getStoredUuid(key), key)
        result.add(MltInitialStructure(mltProp, ProducerType.PROGRESS))
    }

    if (ProducerType.FLASH in songProducers) {
        val key = listOf(ProducerType.FLASH)
        if (mltProp.getUUID(key) == "") mltProp.setUUID(getStoredUuid(key), key)
        result.add(MltInitialStructure(mltProp, ProducerType.FLASH))
    }

    if (ProducerType.HORIZON in songProducers) {
        val key = listOf(ProducerType.HORIZON)
        if (mltProp.getUUID(key) == "") mltProp.setUUID(getStoredUuid(key), key)
        result.add(MltInitialStructure(mltProp, ProducerType.HORIZON))
    }

    if (ProducerType.BACKGROUND in songProducers) {
        val key = listOf(ProducerType.BACKGROUND)
        if (mltProp.getUUID(key) == "") mltProp.setUUID(getStoredUuid(key), key)
        result.add(MltInitialStructure(mltProp, ProducerType.BACKGROUND))
    }

    if (ProducerType.AUDIODRUMS in songProducers) {
        val key = listOf(ProducerType.AUDIODRUMS)
        if (mltProp.getUUID(key) == "") mltProp.setUUID(getStoredUuid(key), key)
        result.add(MltInitialStructure(mltProp, ProducerType.AUDIODRUMS))
    }

    if (ProducerType.AUDIOBASS in songProducers) {
        val key = listOf(ProducerType.AUDIOBASS)
        if (mltProp.getUUID(key) == "") mltProp.setUUID(getStoredUuid(key), key)
        result.add(MltInitialStructure(mltProp, ProducerType.AUDIOBASS))
    }

    if (ProducerType.AUDIOSONG in songProducers) {
        val key = listOf(ProducerType.AUDIOSONG)
        if (mltProp.getUUID(key) == "") mltProp.setUUID(getStoredUuid(key), key)
        result.add(MltInitialStructure(mltProp, ProducerType.AUDIOSONG))
    }

    if (ProducerType.AUDIOMUSIC in songProducers) {
        val key = listOf(ProducerType.AUDIOMUSIC)
        if (mltProp.getUUID(key) == "") mltProp.setUUID(getStoredUuid(key), key)
        result.add(MltInitialStructure(mltProp, ProducerType.AUDIOMUSIC))
    }

    if (ProducerType.AUDIOVOCAL in songProducers) {
        val key = listOf(ProducerType.AUDIOVOCAL)
        if (mltProp.getUUID(key) == "") mltProp.setUUID(getStoredUuid(key), key)
        result.add(MltInitialStructure(mltProp, ProducerType.AUDIOVOCAL))
    }

    if (ProducerType.MAINBIN in songProducers) {
        val key = listOf(ProducerType.MAINBIN)
        if (mltProp.getUUID(key) == "") mltProp.setUUID(getStoredUuid(key), key)
        result.add(MltInitialStructure(mltProp, ProducerType.MAINBIN))
    }

    return result
}

fun getMlt(mltProp: MltProp): MltNode {

    val songVersion = mltProp.getSongVersion()
    val countVoices = mltProp.getCountVoices()

    val body = mutableListOf<MltNode>()

    val bodyProducers = mutableListOf<MltNode>()
    val bodyOthers = mutableListOf<MltNode>()

    body.add(getMltProfile())
    body.add(getMltConsumer(mltProp))

    val misList = getMisList(mltProp)
    misList.forEach { mis ->
        val type = mis.type
        val voiceId = Integer.max(mis.voiceId, 0)
        val childId = Integer.max(mis.childId, 0)
        val elementId = Integer.max(mis.elementId, 0)

        val pct = producerTypeClass[type] ?: return@forEach
        val pctInstance = pct
            .getDeclaredConstructor(*arrayOf(MltProp::class.java, ProducerType::class.java, Int::class.java, Int::class.java, Int::class.java))
            .newInstance(*arrayOf(mltProp, type, voiceId, childId, elementId))
        val resultProducer = pctInstance.javaClass.declaredMethods.firstOrNull() { it.name == "producer" }?.let {it.invoke(pctInstance) as MltNode? }
        val resultProducerBlackTrack = pctInstance.javaClass.declaredMethods.firstOrNull() { it.name == "producerBlackTrack" }?.let {it.invoke(pctInstance) as MltNode? }
        val resultFileProducer = pctInstance.javaClass.declaredMethods.firstOrNull() { it.name == "fileProducer" }?.let {it.invoke(pctInstance) as MltNode? }
        val resultFilePlaylist = pctInstance.javaClass.declaredMethods.firstOrNull() { it.name == "filePlaylist" }?.let {it.invoke(pctInstance) as MltNode? }
        val resultTrackPlaylist = pctInstance.javaClass.declaredMethods.firstOrNull() { it.name == "trackPlaylist" }?.let {it.invoke(pctInstance) as MltNode? }
        val resultTractor = pctInstance.javaClass.declaredMethods.firstOrNull() { it.name == "tractor" }?.let {it.invoke(pctInstance) as MltNode? }
        val resultTractorSequence = pctInstance.javaClass.declaredMethods.firstOrNull() { it.name == "tractorSequence" }?.let {it.invoke(pctInstance) as MltNode? }

        resultProducerBlackTrack?.let { bodyProducers.add(it) }
        resultProducer?.let { bodyProducers.add(it) }
        resultFileProducer?.let { bodyProducers.add(it) }

        resultTractorSequence?.let { bodyOthers.add(it) }
        resultFilePlaylist?.let { bodyOthers.add(it) }
        resultTrackPlaylist?.let { bodyOthers.add(it) }
        resultTractor?.let { bodyOthers.add(it) }

    }

    body.addAll(bodyProducers)
    body.addAll(bodyOthers)

    val mlt = MltNode(
        name = "mlt",
        fields = mutableMapOf(
            Pair("LC_NUMERIC","C"),
            Pair("producer","main_bin"),
            Pair("version","7.21.0"),
            Pair("root",mltProp.getRootFolder("Song")),
        ),
        body = body
    )

    return mlt
}
