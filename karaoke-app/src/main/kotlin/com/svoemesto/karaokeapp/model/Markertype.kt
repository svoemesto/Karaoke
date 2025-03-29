package com.svoemesto.karaokeapp.model

enum class Markertype(val value: String) {
    SYLLABLES("syllables"),
    ENDOFSYLLABLES("endofsyllable"),
    SETTING("setting"),
    ENDOFLINE("endofline"),
    NEWLINE("newline"),
    UNMUTE("unmute"),
    BEAT("beat"),
    CHORD("chord"),
    NOTE("note"),
    OTHER("other"),
}
