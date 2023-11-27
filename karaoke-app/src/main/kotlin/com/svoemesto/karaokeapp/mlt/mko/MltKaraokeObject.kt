package com.svoemesto.karaokeapp.mlt.mko

import com.svoemesto.karaokeapp.model.MltNode
import com.svoemesto.karaokeapp.model.ProducerType

interface MltKaraokeObject {
    fun producer(): MltNode
    fun fileProducer(): MltNode
    fun filePlaylist(): MltNode
    fun trackPlaylist(): MltNode
    fun tractor(): MltNode
    fun template(): MltNode

}