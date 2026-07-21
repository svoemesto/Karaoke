package com.svoemesto.karaokeapp.mlt

/**
 * Builder для mlt prop .
 *
 * @see docs/features/mlt-generator.md
 */
@Suppress("unused")
data class MltPropBuilder(
    val props: MutableMap<String, Any> = mutableMapOf(),
) {
    fun build(): MutableMap<String, Any> = props
}
