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

data class MkoVoice(val mltProp: MltProp,
                    val voiceId: Int = 0) : MltKaraokeObject {
    val type: ProducerType = ProducerType.VOICE
    val mltGenerator = MltGenerator(mltProp, type)



}
