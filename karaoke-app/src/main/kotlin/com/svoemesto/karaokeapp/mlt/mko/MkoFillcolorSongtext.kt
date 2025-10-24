package com.svoemesto.karaokeapp.mlt.mko

import com.svoemesto.karaokeapp.Karaoke
import com.svoemesto.karaokeapp.hexRGB
import com.svoemesto.karaokeapp.mlt.MltGenerator
import com.svoemesto.karaokeapp.mlt.MltProp
import com.svoemesto.karaokeapp.model.MltNode
import com.svoemesto.karaokeapp.model.MltNodeBuilder
import com.svoemesto.karaokeapp.model.ProducerType

data class MkoFillcolorSongtext(val mltProp: MltProp, val type: ProducerType, val voiceId: Int = 0, val childId: Int = 0, val elementId: Int = 0): MltKaraokeObject {
    val mltGenerator = MltGenerator(mltProp, type, voiceId, childId)

    private val songLengthFr = mltProp.getSongLengthFr()
    private val songEndTimecode = mltProp.getSongEndTimecode()
    private val mkoFillcolorSongtextsFolderId = mltProp.getId(listOf(ProducerType.FILLCOLORSONGTEXTS, voiceId))
    private val mkoFillcolorSongtextFilePlaylistRect = mltProp.getRect(listOf(type, voiceId, childId))
    override fun producer(): MltNode = mltGenerator
        .producer(
            props = MltNodeBuilder()
                .propertyName("length", songLengthFr)
                .propertyName("eof", "pause")
                .propertyName("resource", Karaoke.voices[0].fill.evenColor.hexRGB())
                .propertyName("aspect_ratio", 1)
                .propertyName("mlt_service", "color")
                .propertyName("kdenlive:duration", songEndTimecode)
                .propertyName("mlt_image_format", "rgb")
                .propertyName("kdenlive:clipname", mltGenerator.name)
                .propertyName("kdenlive:folderid", mkoFillcolorSongtextsFolderId)
                .propertyName("kdenlive:clip_type", if (type.isAudio) 1 else 2)
                .propertyName("kdenlive:id", mltGenerator.id)
                .build()
        )

    override fun filePlaylist(): MltNode {
        val result = mltGenerator.filePlaylist()
        result.body?.let {
            @Suppress("UNCHECKED_CAST")
            val body = it as MutableList<MltNode>
            body.add(
                mltGenerator.entry(
                    nodes = MltNodeBuilder()
                        .propertyName("kdenlive:id", "filePlaylist${mltGenerator.id}")
                        .filterQtblend(mltGenerator.nameFilterQtblend, mainFilePlaylistTransformProperties(), distort = 1)
                        .build()
                )
            )
        }
        return result
    }

    override fun mainFilePlaylistTransformProperties(): String {
        return mkoFillcolorSongtextFilePlaylistRect
    }

    override fun trackPlaylist(): MltNode = mltGenerator.trackPlaylist()


    override fun tractor(): MltNode = mltGenerator.tractor()

}
