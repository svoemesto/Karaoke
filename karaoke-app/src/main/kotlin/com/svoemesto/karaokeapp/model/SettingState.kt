package com.svoemesto.karaokeapp.model

enum class SettingState(val color: String) {
    ALL_DONE("#7FFFD4"),        // Полностью готово
    ALL_DONE_WO_SPONSR("#00CC00"),  // Полностью готово, но без SPONSR
    OVERDUE("#BDB76B"),         // Публикация прошла, но не все ссылки заполнены
    TODAY("#FFFF00"),           // Сегодня
    ALL_UPLOADED("#DCDCDC"),    // Готово к публикации (всё загружено)
    WO_TG("#87CEFA"),           // Нет TG
    WO_TG_WITH_SPONSR("#60A2CE"),           // Нет TG
    WO_PL("#66B2FF"),           // Нет PL
    WO_VK("#FFDAB9"),           // Нет VK
    WO_VK_WO_PL("#FFFEBA"),           // Нет VK
    WO_DZEN("#FF8000"),         // Нет DZEN
    BOOSTY_SPONSR("#FFAA55"),   // Нет BOOSTY_SPONSR
    WO_DZEN_WITH_VK("#FF3399"), // Нет DZEN, есть VK
    WO_DZEN_WITH_VK_WITH_PL("#CC00CC"), // Нет DZEN, есть VK, есть PL
    WO_VKG("#FFC880"),          // Нет VKG
    IN_WORK(""),                // В работе
}