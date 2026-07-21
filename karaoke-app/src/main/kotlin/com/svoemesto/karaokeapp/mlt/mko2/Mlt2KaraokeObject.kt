package com.svoemesto.karaokeapp.mlt.mko2

import com.svoemesto.karaokeapp.model.MltNode

/**
 * Интерфейс для mlt2karaoke object.
 *
 * @see docs/features/mlt-generator.md
 */
@Suppress("unused")
interface Mlt2KaraokeObject {
    fun producer(): MltNode

    fun fileProducer(): MltNode

    @Suppress("unused")
    fun filePlaylist(): MltNode

    fun trackPlaylist(): MltNode

    fun tractor(): MltNode

    fun template(): MltNode
}
