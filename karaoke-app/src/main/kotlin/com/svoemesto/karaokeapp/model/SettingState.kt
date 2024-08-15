package com.svoemesto.karaokeapp.model

enum class SettingState(val color: String) {
    ALL_DONE("#7FFFD4"),        // Полностью готово
    ALL_DONE_WO_PL("#00CC00"),  // Полностью готово, но без PL
    OVERDUE("#BDB76B"),         // Публикация прошла, но не все ссылки заполнены
    TODAY("#FFFF00"),           // Сегодня
    ALL_UPLOADED("#DCDCDC"),    // Готово к публикации (всё загружено)
    WO_TG("#87CEFA"),           // Нет TG
    WO_PL("#66B2FF"),           // Нет PL
    WO_VK("#FFDAB9"),           // Нет VK
    WO_DZEN("#FF8000"),         // Нет DZEN
    WO_DZEN_WITH_VK("#FF3399"), // Нет DZEN, есть VK
    WO_VKG("#FFC880"),          // Нет VKG
    IN_WORK(""),                // В работе
}