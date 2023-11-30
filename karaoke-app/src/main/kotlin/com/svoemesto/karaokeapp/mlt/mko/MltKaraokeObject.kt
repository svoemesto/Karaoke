package com.svoemesto.karaokeapp.mlt.mko

import com.svoemesto.karaokeapp.model.MltNode
import com.svoemesto.karaokeapp.model.ProducerType

interface MltKaraokeObject {
    fun producer(): MltNode = MltNode()
    fun fileProducer(): MltNode = MltNode()
    fun filePlaylist(): MltNode = MltNode()
    fun trackPlaylist(): MltNode = MltNode()
    fun tractor(): MltNode = MltNode()
    fun tractorSequence(): MltNode = MltNode()
    fun template(): MltNode = MltNode()

}