package com.svoemesto.karaokeapp.textfiledictionary

import com.svoemesto.karaokeapp.CENSORED_FILE_PATH

class CensoredWordsDictionary(): TextFileDictionary {

    override var dict = loadList()
    override fun pathToFile() = CENSORED_FILE_PATH


}