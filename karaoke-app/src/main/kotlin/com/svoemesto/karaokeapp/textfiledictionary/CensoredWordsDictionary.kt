package com.svoemesto.karaokeapp.textfiledictionary

import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.WORKING_DATABASE

/**
 * Класс Censored Words Dictionary.
 *
 * @param database Соединение, из которого читать словарь — вызывающий код с собственным
 *   [KaraokeConnection] (например, karaoke-web) ДОЛЖЕН передать его явно, иначе используется
 *   дефолтный `karaoke-app`-глобал (см. [TextFileDictionary.database]).
 * @see AGENTS.md
 */
class CensoredWordsDictionary(
    override val database: KaraokeConnection = WORKING_DATABASE,
) : TextFileDictionary {
    override fun dictName() = "Censored"
}
