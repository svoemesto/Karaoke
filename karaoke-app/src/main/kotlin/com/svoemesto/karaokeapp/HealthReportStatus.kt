package com.svoemesto.karaokeapp

enum class HealthReportStatus(val color: String) {
    OK(color = "#99FF99"),             // Всё хорошо
    WARNING(color = "#99CCFF"),        // Всё хорошо, но есть нюансы
    IN_PROGRESS(color = "#FFFF99"),    // Всё будет хорошо
    ERROR(color = "#FF9999"),          // Всё плохо, но можно сделать хорошо
    FATAL_ERROR(color = "#FF0000")     // Всё совсем плохо
}