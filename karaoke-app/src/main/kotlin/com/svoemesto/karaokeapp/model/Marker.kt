
package com.svoemesto.karaokeapp.model

import java.io.Serializable

/**
 * Класс Marker.
 *
 * @see docs/features/mlt-generator.md
 */
data class Marker(
    val comment: String = "",
    val pos: Long = 0,
    val type: Long = 0,
) : Serializable
