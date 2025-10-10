package com.svoemesto.karaokeapp.textfiledictionary

import com.svoemesto.karaokeapp.SYNCIDS_FILE_PATH

class SyncIdsDictionary(): TextFileDictionary {

    override var dict = loadList()
    override fun pathToFile() = SYNCIDS_FILE_PATH


}