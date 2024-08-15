package com.svoemesto.karaokeapp.model

data class RecordDiff(
    val recordDiffName: String,
    val recordDiffValueNew: Any?,
    val recordDiffValueOld: Any?,
    val recordDiffRealField: Boolean = true
)

