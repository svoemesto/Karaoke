package com.svoemesto.karaokeapp.mlt.mko

import com.svoemesto.karaokeapp.Karaoke
import com.svoemesto.karaokeapp.xmldata
import com.svoemesto.karaokeapp.mlt.MltText
import com.svoemesto.karaokeapp.mlt.mltNode
import com.svoemesto.karaokeapp.model.SongVoiceLine
import com.svoemesto.karaokeapp.model.MltNode
import com.svoemesto.karaokeapp.model.MusicChord
import com.svoemesto.karaokeapp.model.MusicNote
import com.svoemesto.karaokeapp.model.ProducerType
import com.svoemesto.karaokeapp.model.SongVoiceLineType

data class MkoSongTextLine(val param: Map<String, Any?>,
                           val voiceId: Int = 0) : MltKaraokeObject {
    val type: ProducerType = ProducerType.SONGTEXTLINE
    override fun producer(): MltNode {
        TODO("Not yet implemented")
    }

    override fun fileProducer(): MltNode {
        TODO("Not yet implemented")
    }

    override fun filePlaylist(): MltNode {
        TODO("Not yet implemented")
    }

    override fun trackPlaylist(): MltNode {
        TODO("Not yet implemented")
    }

    override fun tractor(): MltNode {
        TODO("Not yet implemented")
    }

    override fun template(): MltNode {
        TODO("Not yet implemented")
    }
}
