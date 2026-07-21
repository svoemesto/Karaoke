package com.svoemesto.karaokeapp.textfiledictionary

// Мёртвый код: словарь больше не зарегистрирован в TEXT_FILE_DICTS (Constants.kt) и нигде не
// используется — «Тестовый словарь» решили не переносить в tbl_dictionaries. Класс оставлен
// компилируемым (dictName() вместо старого pathToFile()) на случай, если понадобится снова.

/**
 * Класс Test Dictionary.
 *
 * @see AGENTS.md
 */
class TestDictionary : TextFileDictionary {
    override fun dictName() = "Тестовый словарь"
}
