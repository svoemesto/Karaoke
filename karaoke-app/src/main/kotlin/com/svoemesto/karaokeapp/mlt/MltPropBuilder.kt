package com.svoemesto.karaokeapp.mlt

data class MltPropBuilder(
    val props: MutableMap<String, Any> = mutableMapOf()
) {

    fun build(): MutableMap<String, Any> {
        return props
    }
}