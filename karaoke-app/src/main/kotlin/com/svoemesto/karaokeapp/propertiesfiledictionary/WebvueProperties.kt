package com.svoemesto.karaokeapp.propertiesfiledictionary

import com.svoemesto.karaokeapp.WEBVUE_PROPERTIES_FILE_PATH

class WebvueProperties(): PropertiesFileDictionary {
    override fun pathToFile() = WEBVUE_PROPERTIES_FILE_PATH
    override var props: MutableMap<String, String> = loadMap()

}