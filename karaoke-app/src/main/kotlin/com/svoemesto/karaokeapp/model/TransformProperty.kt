package com.svoemesto.karaokeapp.model

import java.io.Serializable

data class TransformProperty(
    val time: String = "",
    val x: Int = 0,
    val y: Int = 0,
    val w: Int = 0,
    val h: Int = 0,
    val opacity: Double = 0.0
) : Serializable {
    override fun toString(): String {
        return "$time=$x $y $w $h $opacity"
    }
}

fun List<TransformProperty>.asRects(): List<TransformProperty> {
    if (this.size <= 1) return emptyList()
    val tmp: MutableList<TransformProperty> = mutableListOf()
    for (index in 1 until this.size ) {
        val prevTp = this[index - 1]
        val currTp = this[index]
        if (currTp.w != prevTp.w) {
            tmp.add(currTp)
        }
    }

    if (tmp.size <= 1) return emptyList()
    val result: MutableList<TransformProperty> = mutableListOf()
    for (index in 1 until tmp.size ) {
        val prevTp = tmp[index - 1]
        val currTp = tmp[index]
        if (index == 1) {
            result.add(
                TransformProperty(
                    time = prevTp.time,
                    x = 0,
                    y = currTp.y,
                    w = prevTp.w,
                    h = currTp.h,
                    opacity = currTp.opacity
                )
            )
        }
        result.add(
            TransformProperty(
                time = prevTp.time,
                x = prevTp.w,
                y = currTp.y,
                w = currTp.w - prevTp.w,
                h = currTp.h,
                opacity = currTp.opacity
            )
        )
    }
    return result
}