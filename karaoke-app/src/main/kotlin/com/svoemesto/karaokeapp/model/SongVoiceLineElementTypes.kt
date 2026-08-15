package com.svoemesto.karaokeapp.model

/**
 * Перечисление возможных значений для setting voice line element types.
 *
 * @see archive/docs/features/mlt-generator.md
 */
enum class SongVoiceLineElementTypes {
    TEXT, // Текст
    ACCORD, // Аккорд
    NOTE, // Нота
    COMMENT, // Комментарий
    EMPTY, // Пустая строка (для генерации видео)
    NEWLINE, // Пустая строка (для генерации текста)
}
