package com.svoemesto.karaokeapp.model

import java.io.Serializable

class Publication : Serializable, Comparable<Publication> {
    var id: Int? = null
    var publishDate: String? = null
    var publish10: Settings? = null
    var publish11: Settings? = null
    var publish12: Settings? = null
    var publish13: Settings? = null
    var publish14: Settings? = null
    var publish15: Settings? = null
    var publish16: Settings? = null
    var publish17: Settings? = null
    var publish18: Settings? = null
    var publish19: Settings? = null
    var publish20: Settings? = null
    var publish21: Settings? = null
    var publish22: Settings? = null
    var publish23: Settings? = null

    val publish10text: String get() = if (publish10 != null) "[${publish10!!.author}] - ${publish10!!.songName}" else ""
    val publish11text: String get() = if (publish11 != null) "[${publish11!!.author}] - ${publish11!!.songName}" else ""
    val publish12text: String get() = if (publish12 != null) "[${publish12!!.author}] - ${publish12!!.songName}" else ""
    val publish13text: String get() = if (publish13 != null) "[${publish13!!.author}] - ${publish13!!.songName}" else ""
    val publish14text: String get() = if (publish14 != null) "[${publish14!!.author}] - ${publish14!!.songName}" else ""
    val publish15text: String get() = if (publish15 != null) "[${publish15!!.author}] - ${publish15!!.songName}" else ""
    val publish16text: String get() = if (publish16 != null) "[${publish16!!.author}] - ${publish16!!.songName}" else ""
    val publish17text: String get() = if (publish17 != null) "[${publish17!!.author}] - ${publish17!!.songName}" else ""
    val publish18text: String get() = if (publish18 != null) "[${publish18!!.author}] - ${publish18!!.songName}" else ""
    val publish19text: String get() = if (publish19 != null) "[${publish19!!.author}] - ${publish19!!.songName}" else ""
    val publish20text: String get() = if (publish20 != null) "[${publish20!!.author}] - ${publish20!!.songName}" else ""
    val publish21text: String get() = if (publish21 != null) "[${publish21!!.author}] - ${publish21!!.songName}" else ""
    val publish22text: String get() = if (publish22 != null) "[${publish22!!.author}] - ${publish22!!.songName}" else ""
    val publish23text: String get() = if (publish23 != null) "[${publish23!!.author}] - ${publish23!!.songName}" else ""

    val publish10color: String get() = if (publish10 != null) publish10!!.color else "#A9A9A9"
    val publish11color: String get() = if (publish11 != null) publish11!!.color else "#A9A9A9"
    val publish12color: String get() = if (publish12 != null) publish12!!.color else "#A9A9A9"
    val publish13color: String get() = if (publish13 != null) publish13!!.color else "#A9A9A9"
    val publish14color: String get() = if (publish14 != null) publish14!!.color else "#A9A9A9"
    val publish15color: String get() = if (publish15 != null) publish15!!.color else "#A9A9A9"
    val publish16color: String get() = if (publish16 != null) publish16!!.color else "#A9A9A9"
    val publish17color: String get() = if (publish17 != null) publish17!!.color else "#A9A9A9"
    val publish18color: String get() = if (publish18 != null) publish18!!.color else "#A9A9A9"
    val publish19color: String get() = if (publish19 != null) publish19!!.color else "#A9A9A9"
    val publish20color: String get() = if (publish20 != null) publish20!!.color else "#A9A9A9"
    val publish21color: String get() = if (publish21 != null) publish21!!.color else "#A9A9A9"
    val publish22color: String get() = if (publish22 != null) publish22!!.color else "#A9A9A9"
    val publish23color: String get() = if (publish23 != null) publish23!!.color else "#A9A9A9"
    override fun compareTo(other: Publication): Int {
        return (id?:0).compareTo(other.id?:0)
    }

    override fun toString(): String {
        var text = "id=${id}, publishDate=${publishDate}\n"
        if (publish10 != null) text += "10:00 ${publish10!!.author} - ${publish10!!.songName}\n"
        if (publish11 != null) text += "11:00 ${publish11!!.author} - ${publish11!!.songName}\n"
        if (publish12 != null) text += "12:00 ${publish12!!.author} - ${publish12!!.songName}\n"
        if (publish13 != null) text += "13:00 ${publish13!!.author} - ${publish13!!.songName}\n"
        if (publish14 != null) text += "14:00 ${publish14!!.author} - ${publish14!!.songName}\n"
        if (publish15 != null) text += "15:00 ${publish15!!.author} - ${publish15!!.songName}\n"
        if (publish16 != null) text += "16:00 ${publish16!!.author} - ${publish16!!.songName}\n"
        if (publish17 != null) text += "17:00 ${publish17!!.author} - ${publish17!!.songName}\n"
        if (publish18 != null) text += "18:00 ${publish18!!.author} - ${publish18!!.songName}\n"
        if (publish19 != null) text += "19:00 ${publish19!!.author} - ${publish19!!.songName}\n"
        if (publish20 != null) text += "20:00 ${publish20!!.author} - ${publish20!!.songName}\n"
        if (publish21 != null) text += "21:00 ${publish21!!.author} - ${publish21!!.songName}\n"
        if (publish22 != null) text += "22:00 ${publish22!!.author} - ${publish22!!.songName}\n"
        if (publish23 != null) text += "23:00 ${publish23!!.author} - ${publish23!!.songName}\n"
        return text
    }

    companion object {
        fun getPublicationList(): List<Publication> {
            var result: MutableList<Publication> = mutableListOf()
            val listOfSettings = Settings.loadListFromDb().filter { it.date != null && it.date != "" && it.time != null && it.time != ""}
            listOfSettings.forEach { settings ->
                var publicationInList = result.filter { it.publishDate == settings.date }.firstOrNull()
                if (publicationInList == null) {
                    publicationInList = Publication()
                    publicationInList.publishDate = settings.date
                    publicationInList.id = (settings.date.split(".")[2]+settings.date.split(".")[1]+settings.date.split(".")[0]).toInt()
                    result.add(publicationInList)
                }
                when (settings.time) {
                    "10:00" -> publicationInList.publish10 = settings
                    "11:00" -> publicationInList.publish11 = settings
                    "12:00" -> publicationInList.publish12 = settings
                    "13:00" -> publicationInList.publish13 = settings
                    "14:00" -> publicationInList.publish14 = settings
                    "15:00" -> publicationInList.publish15 = settings
                    "16:00" -> publicationInList.publish16 = settings
                    "17:00" -> publicationInList.publish17 = settings
                    "18:00" -> publicationInList.publish18 = settings
                    "19:00" -> publicationInList.publish19 = settings
                    "20:00" -> publicationInList.publish20 = settings
                    "21:00" -> publicationInList.publish21 = settings
                    "22:00" -> publicationInList.publish22 = settings
                    "23:00" -> publicationInList.publish23 = settings
                }
            }
            result.sort()
            return result
        }
    }
}
