package com.svoemesto.karaokeapp.textfiledictionary

import com.svoemesto.karaokeapp.YO_FILE_PATH

class YoWordsDictionary(): TextFileDictionary {

    override var dict = loadList()
    override fun pathToFile() = YO_FILE_PATH


}