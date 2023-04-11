package model

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
