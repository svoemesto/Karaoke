
package model

import java.io.Serializable

data class Marker(
    val comment: String = "",
    val pos: Long = 0,
    val type: Long = 0) : Serializable
