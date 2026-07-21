package com.svoemesto.karaokeapp.model

/**
 * Перечисление возможных значений для setting voice line element types.
 *
 * @see docs/features/mlt-generator.md
 */
enum class SettingVoiceLineElementTypes {
    TEXT, // Текст
    ACCORD, // Аккорд
    NOTE, // Нота
    COMMENT, // Комментарий
    EMPTY, // Пустая строка (для генерации видео)
    NEWLINE, // Пустая строка (для генерации текста)
}
