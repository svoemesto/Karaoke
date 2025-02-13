package com.svoemesto.karaokeapp.model

enum class SettingVoiceLineElementTypes {
    TEXT, // Текст
    ACCORD, // Аккорд
    NOTE, // Нота
    COMMENT, // Комментарий
    EMPTY, // Пустая строка (для генерации видео)
    NEWLINE // Пустая строка (для генерации текста)
}