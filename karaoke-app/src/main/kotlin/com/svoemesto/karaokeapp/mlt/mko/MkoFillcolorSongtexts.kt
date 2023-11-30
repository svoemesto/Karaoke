package com.svoemesto.karaokeapp.mlt.mko

import com.svoemesto.karaokeapp.Karaoke
import com.svoemesto.karaokeapp.mlt.MltGenerator
import com.svoemesto.karaokeapp.mlt.MltProp
import com.svoemesto.karaokeapp.xmldata
import com.svoemesto.karaokeapp.mlt.MltText
import com.svoemesto.karaokeapp.mlt.mltNode
import com.svoemesto.karaokeapp.model.SongVoiceLine
import com.svoemesto.karaokeapp.model.MltNode
import com.svoemesto.karaokeapp.model.MusicChord
import com.svoemesto.karaokeapp.model.MusicNote
import com.svoemesto.karaokeapp.model.ProducerType
import com.svoemesto.karaokeapp.model.SongVoiceLineType

data class MkoFillcolorSongtexts(val mltProp: MltProp,
                                 val voiceId: Int = 0) : MltKaraokeObject {
    val type: ProducerType = ProducerType.FILLCOLORSONGTEXTS
    val mltGenerator = MltGenerator(mltProp, type)
    override fun producer(): MltNode = MltNode()

    override fun fileProducer(): MltNode = MltNode()
    override fun filePlaylist(): MltNode = MltNode()

    override fun trackPlaylist(): MltNode = MltNode()

    override fun tractor(): MltNode = MltNode()

    override fun template(): MltNode = MltNode()


}
