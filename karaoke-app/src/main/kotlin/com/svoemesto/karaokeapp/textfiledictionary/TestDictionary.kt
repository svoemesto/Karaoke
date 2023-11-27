package com.svoemesto.karaokeapp.textfiledictionary

import com.svoemesto.karaokeapp.TESTDICT_FILE_PATH

class TestDictionary(): TextFileDictionary {

    override var dict = loadList()
    override fun pathToFile() = TESTDICT_FILE_PATH


}