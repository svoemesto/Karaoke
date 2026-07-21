package com.svoemesto.karaokeapp.model

/**
 * Перечисление возможных значений для markertype.
 *
 * @see docs/features/mlt-generator.md
 */
enum class Markertype(
    val value: String,
) {
    SYLLABLES("syllables"),
    ENDOFSYLLABLES("endofsyllable"),
    SETTING("setting"),
    ENDOFLINE("endofline"),
    NEWLINE("newline"),
    UNMUTE("unmute"),

    @Suppress("unused")
    BEAT("beat"),
    CHORD("chord"),
    EOL_CHORD("eolch"), // end of line chord
    ENDOF_CHORD("eoch"), // end of chord
    NEWLINE_CHORD("nlch"), // new line chord
    NOTE("note"),
    EOL_NOTE("eoln"), // end of line note
    ENDOF_NOTE("eon"), // end of note
    NEWLINE_NOTE("nln"),

    // new line note
    @Suppress("unused")
    OTHER("other"),
}
