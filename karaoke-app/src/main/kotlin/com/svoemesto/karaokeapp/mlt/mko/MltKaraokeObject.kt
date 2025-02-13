package com.svoemesto.karaokeapp.mlt.mko

import com.svoemesto.karaokeapp.model.MltNode
import com.svoemesto.karaokeapp.model.ProducerType

/***
 * filePlaylist - body - blank                      - начальное положение трека на таймлайне
 * filePlaylist - body - entry - timecodeIn/Out     - длительность трека на таймлайне
 */
interface MltKaraokeObject {
    fun producer(): MltNode? = null
    fun producerBlackTrack(): MltNode? = null
    fun fileProducer(): MltNode? = null
    fun filePlaylist(): MltNode? = null
    fun trackPlaylist(): MltNode? = null
    fun tractor(): MltNode? = null
    fun tractorSequence(): MltNode? = null
    fun template(): MltNode? = null
    fun mainFilePlaylistTransformProperties(): String
}