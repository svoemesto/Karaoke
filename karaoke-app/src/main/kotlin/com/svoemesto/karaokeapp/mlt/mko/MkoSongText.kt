package com.svoemesto.karaokeapp.mlt.mko

import com.svoemesto.karaokeapp.Karaoke
import com.svoemesto.karaokeapp.mlt.MltGenerator
import com.svoemesto.karaokeapp.mlt.MltProp
import com.svoemesto.karaokeapp.xmldata
import com.svoemesto.karaokeapp.mlt.MltText
import com.svoemesto.karaokeapp.mlt.mltNode
import com.svoemesto.karaokeapp.model.*

data class MkoSongText(val mltProp: MltProp, val type: ProducerType, val voiceId: Int = 0, val childId: Int = 0): MltKaraokeObject {
    val mltGenerator = MltGenerator(mltProp, type, voiceId)

    override fun producer(): MltNode = mltGenerator
        .producer(
            props = MltNodeBuilder(mltGenerator.defaultProducerPropertiesForMltService("kdenlivetitle"))
                .propertyName("kdenlive:folderid", mltProp.getId(listOf(ProducerType.VOICE, voiceId)))
                .propertyName("length", mltProp.getSongLengthFr())
                .propertyName("kdenlive:duration", mltProp.getSongEndTimecode())
                .propertyName("xmldata", mltProp.getXmlData(listOf(type, voiceId)).toString().xmldata())
                .propertyName("meta.media.width", Karaoke.frameWidthPx)
                .propertyName("meta.media.height", mltProp.getWorkAreaHeightPx(listOf(ProducerType.SONGTEXT, voiceId)))
                .build()
        )

    override fun filePlaylist(): MltNode {

        val voiceLines = mltProp.getVoicelines(listOf(ProducerType.SONGTEXT,voiceId))
        val propRect = voiceLines
            .filter {it.startTp != null && it.endTp != null && (it.type == SongVoiceLineType.TEXT || it == voiceLines.first() || it == voiceLines.last())}
            .map { listOf(it.startTp.toString(), it.endTp.toString()).joinToString(";") }.joinToString(";")

        val result = mltGenerator.filePlaylist()
        result.body?.let {
            val body = it as MutableList<MltNode>
//            body.addAll(MltNodeBuilder().blank(mltProp.getInOffsetVideo()).build())
            body.add(
                mltGenerator.entry(
                    nodes = MltNodeBuilder()
                        .propertyName("kdenlive:id", "filePlaylist${mltGenerator.id}")
                        .filterQtblend(mltGenerator.nameFilterQtblend, propRect)
                        .build()
                )
            )
        }
        return result
    }

    override fun trackPlaylist(): MltNode = mltGenerator.trackPlaylist()

    override fun tractor(): MltNode = mltGenerator.tractor()


    override fun template(): MltNode {
        val voiceLines = mltProp.getVoicelines(listOf(ProducerType.SONGTEXT,voiceId))
        val templateSongTextSymbolsGroup = mutableListOf<MltNode>()
        val workAreaSongtextHeightPx = mltProp.getWorkAreaHeightPx(listOf(ProducerType.SONGTEXT, voiceId))
        val capo = mltProp.getSongCapo()

        voiceLines.forEachIndexed { indexLine, it ->
            val voiceLineSongtext = it as SongVoiceLine
            voiceLineSongtext.symbols.forEachIndexed { indexSymbol, lineSymbol ->

                val mltText: MltText = lineSymbol.mltText
                val text = if (voiceLineSongtext.type == SongVoiceLineType.CHORDS) {
                    if (capo == 0 || mltProp.getIgnoreCapo()) {
                        mltText.text.split("|")[0]
                    } else {
                        val chordNameAndFret = mltText.text.split("|")
                        val nameChord = chordNameAndFret[0]
                        val fretChord = if (chordNameAndFret.size > 1) chordNameAndFret[1].toInt() else 0
                        val (chord, note) = MusicChord.getChordNote(nameChord)

                        var newIndexNote = MusicNote.values().indexOf(note!!) - capo
                        if (newIndexNote < 0) newIndexNote = MusicNote.values().size + newIndexNote
                        val newNote = MusicNote.values()[newIndexNote]

                        newNote.names.first() + chord!!.names.first()
                    }
                } else {
                    mltText.text.replace("&","&amp;amp;")
                }

                val x = lineSymbol.xStartPx + Karaoke.songtextStartPositionXpx // (startX + voiceLineSongtext.getSymbolXpx(indexSymbol)).toLong()
                val y = voiceLineSongtext.yPx //(startY + indexLine*symbolSongtextHeightPx + (symbolSongtextHeightPx - mltText.h)).toLong()

                templateSongTextSymbolsGroup.add(
                    MltNode(
                        name = "item",
                        fields = mutableMapOf(Pair("type","QGraphicsTextItem"), Pair("z-index","1")),
                        body = mutableListOf(
                            MltNode(
                                name = "position",
                                fields = mutableMapOf(Pair("x","$x"), Pair("y","$y")),
                                body = mutableListOf(MltNode(name = "transform", fields = mutableMapOf(), body = "1,0,0,0,1,0,0,0,1"))
                            ),
                            mltText.mltNode(text)
                        )
                    )
                )
            }

        }
        templateSongTextSymbolsGroup.add(MltNode(name = "startviewport", fields = mutableMapOf(Pair("rect","0,0,${Karaoke.frameWidthPx},$workAreaSongtextHeightPx"))))
        templateSongTextSymbolsGroup.add(MltNode(name = "endviewport", fields = mutableMapOf(Pair("rect","0,0,${Karaoke.frameWidthPx},$workAreaSongtextHeightPx"))))
        templateSongTextSymbolsGroup.add(MltNode(name = "background", fields = mutableMapOf(Pair("color","0,0,0,0"))))

        val templateSongText = MltNode(
            name = "kdenlivetitle",
            fields = mutableMapOf(
                Pair("duration","0"),
                Pair("LC_NUMERIC","C"),
                Pair("width","${Karaoke.frameWidthPx}"),
                Pair("height","$workAreaSongtextHeightPx"),
                Pair("out","0"),
            ),
            body = templateSongTextSymbolsGroup
        )

        return templateSongText
    }
}

