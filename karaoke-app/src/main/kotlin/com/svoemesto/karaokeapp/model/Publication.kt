package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.WORKING_DATABASE
import com.svoemesto.karaokeapp.censored
import java.io.Serializable
import java.text.SimpleDateFormat
import java.util.*

data class PublicationDTO(
    val id: Int?,
    val publishDate: String?,
    val publish10: SettingsDTO?,
    val publish11: SettingsDTO?,
    val publish12: SettingsDTO?,
    val publish13: SettingsDTO?,
    val publish14: SettingsDTO?,
    val publish15: SettingsDTO?,
    val publish16: SettingsDTO?,
    val publish17: SettingsDTO?,
    val publish18: SettingsDTO?,
    val publish19: SettingsDTO?,
    val publish20: SettingsDTO?,
    val publish21: SettingsDTO?,
    val publish22: SettingsDTO?,
    val publish23: SettingsDTO?,
    val publish10text: String,
    val publish11text: String,
    val publish12text: String,
    val publish13text: String,
    val publish14text: String,
    val publish15text: String,
    val publish16text: String,
    val publish17text: String,
    val publish18text: String,
    val publish19text: String,
    val publish20text: String,
    val publish21text: String,
    val publish22text: String,
    val publish23text: String
)

class Publication(val database: KaraokeConnection = WORKING_DATABASE) : Serializable, Comparable<Publication> {
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

    val publish10text: String get() = if (publish10 != null) "${if (publish10!!.firstSongInAlbum) "[${publish10!!.author}] ★ " else ""} ${publish10!!.songName.censored()}" else ""
    val publish11text: String get() = if (publish11 != null) "${if (publish11!!.firstSongInAlbum) "[${publish11!!.author}] ★ " else ""} ${publish11!!.songName.censored()}" else ""
    val publish12text: String get() = if (publish12 != null) "${if (publish12!!.firstSongInAlbum) "[${publish12!!.author}] ★ " else ""} ${publish12!!.songName.censored()}" else ""
    val publish13text: String get() = if (publish13 != null) "${if (publish13!!.firstSongInAlbum) "[${publish13!!.author}] ★ " else ""} ${publish13!!.songName.censored()}" else ""
    val publish14text: String get() = if (publish14 != null) "${if (publish14!!.firstSongInAlbum) "[${publish14!!.author}] ★ " else ""} ${publish14!!.songName.censored()}" else ""
    val publish15text: String get() = if (publish15 != null) "${if (publish15!!.firstSongInAlbum) "[${publish15!!.author}] ★ " else ""} ${publish15!!.songName.censored()}" else ""
    val publish16text: String get() = if (publish16 != null) "${if (publish16!!.firstSongInAlbum) "[${publish16!!.author}] ★ " else ""} ${publish16!!.songName.censored()}" else ""
    val publish17text: String get() = if (publish17 != null) "${if (publish17!!.firstSongInAlbum) "[${publish17!!.author}] ★ " else ""} ${publish17!!.songName.censored()}" else ""
    val publish18text: String get() = if (publish18 != null) "${if (publish18!!.firstSongInAlbum) "[${publish18!!.author}] ★ " else ""} ${publish18!!.songName.censored()}" else ""
    val publish19text: String get() = if (publish19 != null) "${if (publish19!!.firstSongInAlbum) "[${publish19!!.author}] ★ " else ""} ${publish19!!.songName.censored()}" else ""
    val publish20text: String get() = if (publish20 != null) "${if (publish20!!.firstSongInAlbum) "[${publish20!!.author}] ★ " else ""} ${publish20!!.songName.censored()}" else ""
    val publish21text: String get() = if (publish21 != null) "${if (publish21!!.firstSongInAlbum) "[${publish21!!.author}] ★ " else ""} ${publish21!!.songName.censored()}" else ""
    val publish22text: String get() = if (publish22 != null) "${if (publish22!!.firstSongInAlbum) "[${publish22!!.author}] ★ " else ""} ${publish22!!.songName.censored()}" else ""
    val publish23text: String get() = if (publish23 != null) "${if (publish23!!.firstSongInAlbum) "[${publish23!!.author}] ★ " else ""} ${publish23!!.songName.censored()}" else ""

//    val publish23text: String get() = if (publish23 != null) "${publish23!!.flags}[${publish23!!.author}] ${if (publish23!!.firstSongInAlbum) "★" else "-"} ${publish23!!.songName}" else ""

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

    val publish10colorMeltLyrics: String get() = if (publish10 != null) publish10!!.processColorMeltLyrics else "#A9A9A9"
    val publish11colorMeltLyrics: String get() = if (publish11 != null) publish11!!.processColorMeltLyrics else "#A9A9A9"
    val publish12colorMeltLyrics: String get() = if (publish12 != null) publish12!!.processColorMeltLyrics else "#A9A9A9"
    val publish13colorMeltLyrics: String get() = if (publish13 != null) publish13!!.processColorMeltLyrics else "#A9A9A9"
    val publish14colorMeltLyrics: String get() = if (publish14 != null) publish14!!.processColorMeltLyrics else "#A9A9A9"
    val publish15colorMeltLyrics: String get() = if (publish15 != null) publish15!!.processColorMeltLyrics else "#A9A9A9"
    val publish16colorMeltLyrics: String get() = if (publish16 != null) publish16!!.processColorMeltLyrics else "#A9A9A9"
    val publish17colorMeltLyrics: String get() = if (publish17 != null) publish17!!.processColorMeltLyrics else "#A9A9A9"
    val publish18colorMeltLyrics: String get() = if (publish18 != null) publish18!!.processColorMeltLyrics else "#A9A9A9"
    val publish19colorMeltLyrics: String get() = if (publish19 != null) publish19!!.processColorMeltLyrics else "#A9A9A9"
    val publish20colorMeltLyrics: String get() = if (publish20 != null) publish20!!.processColorMeltLyrics else "#A9A9A9"
    val publish21colorMeltLyrics: String get() = if (publish21 != null) publish21!!.processColorMeltLyrics else "#A9A9A9"
    val publish22colorMeltLyrics: String get() = if (publish22 != null) publish22!!.processColorMeltLyrics else "#A9A9A9"
    val publish23colorMeltLyrics: String get() = if (publish23 != null) publish23!!.processColorMeltLyrics else "#A9A9A9"

    val publish10colorMeltKaraoke: String get() = if (publish10 != null) publish10!!.processColorMeltKaraoke else "#A9A9A9"
    val publish11colorMeltKaraoke: String get() = if (publish11 != null) publish11!!.processColorMeltKaraoke else "#A9A9A9"
    val publish12colorMeltKaraoke: String get() = if (publish12 != null) publish12!!.processColorMeltKaraoke else "#A9A9A9"
    val publish13colorMeltKaraoke: String get() = if (publish13 != null) publish13!!.processColorMeltKaraoke else "#A9A9A9"
    val publish14colorMeltKaraoke: String get() = if (publish14 != null) publish14!!.processColorMeltKaraoke else "#A9A9A9"
    val publish15colorMeltKaraoke: String get() = if (publish15 != null) publish15!!.processColorMeltKaraoke else "#A9A9A9"
    val publish16colorMeltKaraoke: String get() = if (publish16 != null) publish16!!.processColorMeltKaraoke else "#A9A9A9"
    val publish17colorMeltKaraoke: String get() = if (publish17 != null) publish17!!.processColorMeltKaraoke else "#A9A9A9"
    val publish18colorMeltKaraoke: String get() = if (publish18 != null) publish18!!.processColorMeltKaraoke else "#A9A9A9"
    val publish19colorMeltKaraoke: String get() = if (publish19 != null) publish19!!.processColorMeltKaraoke else "#A9A9A9"
    val publish20colorMeltKaraoke: String get() = if (publish20 != null) publish20!!.processColorMeltKaraoke else "#A9A9A9"
    val publish21colorMeltKaraoke: String get() = if (publish21 != null) publish21!!.processColorMeltKaraoke else "#A9A9A9"
    val publish22colorMeltKaraoke: String get() = if (publish22 != null) publish22!!.processColorMeltKaraoke else "#A9A9A9"
    val publish23colorMeltKaraoke: String get() = if (publish23 != null) publish23!!.processColorMeltKaraoke else "#A9A9A9"


    val publish10id: Long get() = if (publish10 != null) publish10!!.id else 0
    val publish11id: Long get() = if (publish11 != null) publish11!!.id else 0
    val publish12id: Long get() = if (publish12 != null) publish12!!.id else 0
    val publish13id: Long get() = if (publish13 != null) publish13!!.id else 0
    val publish14id: Long get() = if (publish14 != null) publish14!!.id else 0
    val publish15id: Long get() = if (publish15 != null) publish15!!.id else 0
    val publish16id: Long get() = if (publish16 != null) publish16!!.id else 0
    val publish17id: Long get() = if (publish17 != null) publish17!!.id else 0
    val publish18id: Long get() = if (publish18 != null) publish18!!.id else 0
    val publish19id: Long get() = if (publish19 != null) publish19!!.id else 0
    val publish20id: Long get() = if (publish20 != null) publish20!!.id else 0
    val publish21id: Long get() = if (publish21 != null) publish21!!.id else 0
    val publish22id: Long get() = if (publish22 != null) publish22!!.id else 0
    val publish23id: Long get() = if (publish23 != null) publish23!!.id else 0



    val publish10colorVkLyrics: String get() = if (publish10 != null) publish10!!.processColorVkLyrics else "#A9A9A9"
    val publish11colorVkLyrics: String get() = if (publish11 != null) publish11!!.processColorVkLyrics else "#A9A9A9"
    val publish12colorVkLyrics: String get() = if (publish12 != null) publish12!!.processColorVkLyrics else "#A9A9A9"
    val publish13colorVkLyrics: String get() = if (publish13 != null) publish13!!.processColorVkLyrics else "#A9A9A9"
    val publish14colorVkLyrics: String get() = if (publish14 != null) publish14!!.processColorVkLyrics else "#A9A9A9"
    val publish15colorVkLyrics: String get() = if (publish15 != null) publish15!!.processColorVkLyrics else "#A9A9A9"
    val publish16colorVkLyrics: String get() = if (publish16 != null) publish16!!.processColorVkLyrics else "#A9A9A9"
    val publish17colorVkLyrics: String get() = if (publish17 != null) publish17!!.processColorVkLyrics else "#A9A9A9"
    val publish18colorVkLyrics: String get() = if (publish18 != null) publish18!!.processColorVkLyrics else "#A9A9A9"
    val publish19colorVkLyrics: String get() = if (publish19 != null) publish19!!.processColorVkLyrics else "#A9A9A9"
    val publish20colorVkLyrics: String get() = if (publish20 != null) publish20!!.processColorVkLyrics else "#A9A9A9"
    val publish21colorVkLyrics: String get() = if (publish21 != null) publish21!!.processColorVkLyrics else "#A9A9A9"
    val publish22colorVkLyrics: String get() = if (publish22 != null) publish22!!.processColorVkLyrics else "#A9A9A9"
    val publish23colorVkLyrics: String get() = if (publish23 != null) publish23!!.processColorVkLyrics else "#A9A9A9"


    val publish10colorVkKaraoke: String get() = if (publish10 != null) publish10!!.processColorVkKaraoke else "#A9A9A9"
    val publish11colorVkKaraoke: String get() = if (publish11 != null) publish11!!.processColorVkKaraoke else "#A9A9A9"
    val publish12colorVkKaraoke: String get() = if (publish12 != null) publish12!!.processColorVkKaraoke else "#A9A9A9"
    val publish13colorVkKaraoke: String get() = if (publish13 != null) publish13!!.processColorVkKaraoke else "#A9A9A9"
    val publish14colorVkKaraoke: String get() = if (publish14 != null) publish14!!.processColorVkKaraoke else "#A9A9A9"
    val publish15colorVkKaraoke: String get() = if (publish15 != null) publish15!!.processColorVkKaraoke else "#A9A9A9"
    val publish16colorVkKaraoke: String get() = if (publish16 != null) publish16!!.processColorVkKaraoke else "#A9A9A9"
    val publish17colorVkKaraoke: String get() = if (publish17 != null) publish17!!.processColorVkKaraoke else "#A9A9A9"
    val publish18colorVkKaraoke: String get() = if (publish18 != null) publish18!!.processColorVkKaraoke else "#A9A9A9"
    val publish19colorVkKaraoke: String get() = if (publish19 != null) publish19!!.processColorVkKaraoke else "#A9A9A9"
    val publish20colorVkKaraoke: String get() = if (publish20 != null) publish20!!.processColorVkKaraoke else "#A9A9A9"
    val publish21colorVkKaraoke: String get() = if (publish21 != null) publish21!!.processColorVkKaraoke else "#A9A9A9"
    val publish22colorVkKaraoke: String get() = if (publish22 != null) publish22!!.processColorVkKaraoke else "#A9A9A9"
    val publish23colorVkKaraoke: String get() = if (publish23 != null) publish23!!.processColorVkKaraoke else "#A9A9A9"



    val publish10colorDzenLyrics: String get() = if (publish10 != null) publish10!!.processColorDzenLyrics else "#A9A9A9"
    val publish11colorDzenLyrics: String get() = if (publish11 != null) publish11!!.processColorDzenLyrics else "#A9A9A9"
    val publish12colorDzenLyrics: String get() = if (publish12 != null) publish12!!.processColorDzenLyrics else "#A9A9A9"
    val publish13colorDzenLyrics: String get() = if (publish13 != null) publish13!!.processColorDzenLyrics else "#A9A9A9"
    val publish14colorDzenLyrics: String get() = if (publish14 != null) publish14!!.processColorDzenLyrics else "#A9A9A9"
    val publish15colorDzenLyrics: String get() = if (publish15 != null) publish15!!.processColorDzenLyrics else "#A9A9A9"
    val publish16colorDzenLyrics: String get() = if (publish16 != null) publish16!!.processColorDzenLyrics else "#A9A9A9"
    val publish17colorDzenLyrics: String get() = if (publish17 != null) publish17!!.processColorDzenLyrics else "#A9A9A9"
    val publish18colorDzenLyrics: String get() = if (publish18 != null) publish18!!.processColorDzenLyrics else "#A9A9A9"
    val publish19colorDzenLyrics: String get() = if (publish19 != null) publish19!!.processColorDzenLyrics else "#A9A9A9"
    val publish20colorDzenLyrics: String get() = if (publish20 != null) publish20!!.processColorDzenLyrics else "#A9A9A9"
    val publish21colorDzenLyrics: String get() = if (publish21 != null) publish21!!.processColorDzenLyrics else "#A9A9A9"
    val publish22colorDzenLyrics: String get() = if (publish22 != null) publish22!!.processColorDzenLyrics else "#A9A9A9"
    val publish23colorDzenLyrics: String get() = if (publish23 != null) publish23!!.processColorDzenLyrics else "#A9A9A9"


    val publish10colorDzenKaraoke: String get() = if (publish10 != null) publish10!!.processColorDzenKaraoke else "#A9A9A9"
    val publish11colorDzenKaraoke: String get() = if (publish11 != null) publish11!!.processColorDzenKaraoke else "#A9A9A9"
    val publish12colorDzenKaraoke: String get() = if (publish12 != null) publish12!!.processColorDzenKaraoke else "#A9A9A9"
    val publish13colorDzenKaraoke: String get() = if (publish13 != null) publish13!!.processColorDzenKaraoke else "#A9A9A9"
    val publish14colorDzenKaraoke: String get() = if (publish14 != null) publish14!!.processColorDzenKaraoke else "#A9A9A9"
    val publish15colorDzenKaraoke: String get() = if (publish15 != null) publish15!!.processColorDzenKaraoke else "#A9A9A9"
    val publish16colorDzenKaraoke: String get() = if (publish16 != null) publish16!!.processColorDzenKaraoke else "#A9A9A9"
    val publish17colorDzenKaraoke: String get() = if (publish17 != null) publish17!!.processColorDzenKaraoke else "#A9A9A9"
    val publish18colorDzenKaraoke: String get() = if (publish18 != null) publish18!!.processColorDzenKaraoke else "#A9A9A9"
    val publish19colorDzenKaraoke: String get() = if (publish19 != null) publish19!!.processColorDzenKaraoke else "#A9A9A9"
    val publish20colorDzenKaraoke: String get() = if (publish20 != null) publish20!!.processColorDzenKaraoke else "#A9A9A9"
    val publish21colorDzenKaraoke: String get() = if (publish21 != null) publish21!!.processColorDzenKaraoke else "#A9A9A9"
    val publish22colorDzenKaraoke: String get() = if (publish22 != null) publish22!!.processColorDzenKaraoke else "#A9A9A9"
    val publish23colorDzenKaraoke: String get() = if (publish23 != null) publish23!!.processColorDzenKaraoke else "#A9A9A9"



    val publish10colorTelegramLyrics: String get() = if (publish10 != null) publish10!!.processColorTelegramLyrics else "#A9A9A9"
    val publish11colorTelegramLyrics: String get() = if (publish11 != null) publish11!!.processColorTelegramLyrics else "#A9A9A9"
    val publish12colorTelegramLyrics: String get() = if (publish12 != null) publish12!!.processColorTelegramLyrics else "#A9A9A9"
    val publish13colorTelegramLyrics: String get() = if (publish13 != null) publish13!!.processColorTelegramLyrics else "#A9A9A9"
    val publish14colorTelegramLyrics: String get() = if (publish14 != null) publish14!!.processColorTelegramLyrics else "#A9A9A9"
    val publish15colorTelegramLyrics: String get() = if (publish15 != null) publish15!!.processColorTelegramLyrics else "#A9A9A9"
    val publish16colorTelegramLyrics: String get() = if (publish16 != null) publish16!!.processColorTelegramLyrics else "#A9A9A9"
    val publish17colorTelegramLyrics: String get() = if (publish17 != null) publish17!!.processColorTelegramLyrics else "#A9A9A9"
    val publish18colorTelegramLyrics: String get() = if (publish18 != null) publish18!!.processColorTelegramLyrics else "#A9A9A9"
    val publish19colorTelegramLyrics: String get() = if (publish19 != null) publish19!!.processColorTelegramLyrics else "#A9A9A9"
    val publish20colorTelegramLyrics: String get() = if (publish20 != null) publish20!!.processColorTelegramLyrics else "#A9A9A9"
    val publish21colorTelegramLyrics: String get() = if (publish21 != null) publish21!!.processColorTelegramLyrics else "#A9A9A9"
    val publish22colorTelegramLyrics: String get() = if (publish22 != null) publish22!!.processColorTelegramLyrics else "#A9A9A9"
    val publish23colorTelegramLyrics: String get() = if (publish23 != null) publish23!!.processColorTelegramLyrics else "#A9A9A9"


    val publish10colorTelegramKaraoke: String get() = if (publish10 != null) publish10!!.processColorTelegramKaraoke else "#A9A9A9"
    val publish11colorTelegramKaraoke: String get() = if (publish11 != null) publish11!!.processColorTelegramKaraoke else "#A9A9A9"
    val publish12colorTelegramKaraoke: String get() = if (publish12 != null) publish12!!.processColorTelegramKaraoke else "#A9A9A9"
    val publish13colorTelegramKaraoke: String get() = if (publish13 != null) publish13!!.processColorTelegramKaraoke else "#A9A9A9"
    val publish14colorTelegramKaraoke: String get() = if (publish14 != null) publish14!!.processColorTelegramKaraoke else "#A9A9A9"
    val publish15colorTelegramKaraoke: String get() = if (publish15 != null) publish15!!.processColorTelegramKaraoke else "#A9A9A9"
    val publish16colorTelegramKaraoke: String get() = if (publish16 != null) publish16!!.processColorTelegramKaraoke else "#A9A9A9"
    val publish17colorTelegramKaraoke: String get() = if (publish17 != null) publish17!!.processColorTelegramKaraoke else "#A9A9A9"
    val publish18colorTelegramKaraoke: String get() = if (publish18 != null) publish18!!.processColorTelegramKaraoke else "#A9A9A9"
    val publish19colorTelegramKaraoke: String get() = if (publish19 != null) publish19!!.processColorTelegramKaraoke else "#A9A9A9"
    val publish20colorTelegramKaraoke: String get() = if (publish20 != null) publish20!!.processColorTelegramKaraoke else "#A9A9A9"
    val publish21colorTelegramKaraoke: String get() = if (publish21 != null) publish21!!.processColorTelegramKaraoke else "#A9A9A9"
    val publish22colorTelegramKaraoke: String get() = if (publish22 != null) publish22!!.processColorTelegramKaraoke else "#A9A9A9"
    val publish23colorTelegramKaraoke: String get() = if (publish23 != null) publish23!!.processColorTelegramKaraoke else "#A9A9A9"



    val publish10colorVk: String get() = if (publish10 != null) publish10!!.processColorVk else "#A9A9A9"
    val publish11colorVk: String get() = if (publish11 != null) publish11!!.processColorVk else "#A9A9A9"
    val publish12colorVk: String get() = if (publish12 != null) publish12!!.processColorVk else "#A9A9A9"
    val publish13colorVk: String get() = if (publish13 != null) publish13!!.processColorVk else "#A9A9A9"
    val publish14colorVk: String get() = if (publish14 != null) publish14!!.processColorVk else "#A9A9A9"
    val publish15colorVk: String get() = if (publish15 != null) publish15!!.processColorVk else "#A9A9A9"
    val publish16colorVk: String get() = if (publish16 != null) publish16!!.processColorVk else "#A9A9A9"
    val publish17colorVk: String get() = if (publish17 != null) publish17!!.processColorVk else "#A9A9A9"
    val publish18colorVk: String get() = if (publish18 != null) publish18!!.processColorVk else "#A9A9A9"
    val publish19colorVk: String get() = if (publish19 != null) publish19!!.processColorVk else "#A9A9A9"
    val publish20colorVk: String get() = if (publish20 != null) publish20!!.processColorVk else "#A9A9A9"
    val publish21colorVk: String get() = if (publish21 != null) publish21!!.processColorVk else "#A9A9A9"
    val publish22colorVk: String get() = if (publish22 != null) publish22!!.processColorVk else "#A9A9A9"
    val publish23colorVk: String get() = if (publish23 != null) publish23!!.processColorVk else "#A9A9A9"



    val publish10colorBoosty: String get() = if (publish10 != null) publish10!!.processColorBoosty else "#A9A9A9"
    val publish11colorBoosty: String get() = if (publish11 != null) publish11!!.processColorBoosty else "#A9A9A9"
    val publish12colorBoosty: String get() = if (publish12 != null) publish12!!.processColorBoosty else "#A9A9A9"
    val publish13colorBoosty: String get() = if (publish13 != null) publish13!!.processColorBoosty else "#A9A9A9"
    val publish14colorBoosty: String get() = if (publish14 != null) publish14!!.processColorBoosty else "#A9A9A9"
    val publish15colorBoosty: String get() = if (publish15 != null) publish15!!.processColorBoosty else "#A9A9A9"
    val publish16colorBoosty: String get() = if (publish16 != null) publish16!!.processColorBoosty else "#A9A9A9"
    val publish17colorBoosty: String get() = if (publish17 != null) publish17!!.processColorBoosty else "#A9A9A9"
    val publish18colorBoosty: String get() = if (publish18 != null) publish18!!.processColorBoosty else "#A9A9A9"
    val publish19colorBoosty: String get() = if (publish19 != null) publish19!!.processColorBoosty else "#A9A9A9"
    val publish20colorBoosty: String get() = if (publish20 != null) publish20!!.processColorBoosty else "#A9A9A9"
    val publish21colorBoosty: String get() = if (publish21 != null) publish21!!.processColorBoosty else "#A9A9A9"
    val publish22colorBoosty: String get() = if (publish22 != null) publish22!!.processColorBoosty else "#A9A9A9"
    val publish23colorBoosty: String get() = if (publish23 != null) publish23!!.processColorBoosty else "#A9A9A9"


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

    fun toDTO(): PublicationDTO {
        return PublicationDTO(
            id = id,
            publishDate = publishDate,
            publish10 = publish10?.toDTO(),
            publish11 = publish11?.toDTO(),
            publish12 = publish12?.toDTO(),
            publish13 = publish13?.toDTO(),
            publish14 = publish14?.toDTO(),
            publish15 = publish15?.toDTO(),
            publish16 = publish16?.toDTO(),
            publish17 = publish17?.toDTO(),
            publish18 = publish18?.toDTO(),
            publish19 = publish19?.toDTO(),
            publish20 = publish20?.toDTO(),
            publish21 = publish21?.toDTO(),
            publish22 = publish22?.toDTO(),
            publish23 = publish23?.toDTO(),
            publish10text = publish10text,
            publish11text = publish11text,
            publish12text = publish12text,
            publish13text = publish13text,
            publish14text = publish14text,
            publish15text = publish15text,
            publish16text = publish16text,
            publish17text = publish17text,
            publish18text = publish18text,
            publish19text = publish19text,
            publish20text = publish20text,
            publish21text = publish21text,
            publish22text = publish22text,
            publish23text = publish23text
        )
    }

    companion object {
        fun getPublicationList(args: Map<String, String> = emptyMap(), database: KaraokeConnection): List<Publication> {
            var result: MutableList<Publication> = mutableListOf()

            var filterDateFrom =  args["filter_date_from"] ?: ""
            var filterDateTo =  args["filter_date_to"] ?: ""
            val filterCond =  args["filter_cond"] ?: ""

            if (filterCond == "all") {
                filterDateFrom = ""
                filterDateTo = ""
            } else if (filterCond == "fromtoday") {
                filterDateFrom = SimpleDateFormat("dd.MM.yy").format(Date())
                filterDateTo = ""
            } else if (filterCond == "fromnotpublish") {
                val listOfSettingsTemp = Settings.loadListFromDb(database = database).filter {
                    it.date != "" &&
                    it.time != "" &&
                    it.flags != ""
                }
                if (listOfSettingsTemp.isNotEmpty()) {
                    var minDate = ""
                    listOfSettingsTemp.forEach {
                        if (minDate == "" || SimpleDateFormat("dd.MM.yy").parse(it.date) < SimpleDateFormat("dd.MM.yy").parse(minDate)) {
                            minDate = it.date
                        }
                    }
                    println(minDate)
                    filterDateFrom = minDate
                    filterDateTo = ""
                }
            } else if (filterCond == "fromnotdone") {
                val listOfSettingsTemp = Settings.loadListFromDb(database = database).filter {
                    it.date != "" &&
                    it.time != "" &&
                    it.idStatus < 3L
                }
                if (listOfSettingsTemp.isNotEmpty()) {
                    var minDate = ""
                    listOfSettingsTemp.forEach {
                        if (minDate == "" || SimpleDateFormat("dd.MM.yy").parse(it.date) < SimpleDateFormat("dd.MM.yy").parse(minDate)) {
                            minDate = it.date
                        }
                    }
                    println(minDate)
                    filterDateFrom = minDate
                    filterDateTo = ""
                }
            } else if (filterCond == "fromnotcheck") {
                val listOfSettingsTemp = Settings.loadListFromDb(database = database).filter {
                    it.date != "" &&
                    it.time != "" &&
                    it.idStatus < 4L
                }
                if (listOfSettingsTemp.isNotEmpty()) {
                    var minDate = ""
                    listOfSettingsTemp.forEach {
                        if (minDate == "" || SimpleDateFormat("dd.MM.yy").parse(it.date) < SimpleDateFormat("dd.MM.yy").parse(minDate)) {
                            minDate = it.date
                        }
                    }
                    println(minDate)
                    filterDateFrom = minDate
                    filterDateTo = ""
                }

            }


            val listOfSettings = Settings.loadListFromDb(database = database).filter {
                it.date != "" &&
                it.time != "" &&
                (if (filterDateFrom != "") SimpleDateFormat("dd.MM.yy").parse(it.date)  >= SimpleDateFormat("dd.MM.yy").parse(filterDateFrom) else true) &&
                (if (filterDateTo != "") SimpleDateFormat("dd.MM.yy").parse(it.date)  <= SimpleDateFormat("dd.MM.yy").parse(filterDateTo) else true)
            }

            listOfSettings.forEach { settings ->
                var publicationInList = result.filter { it.publishDate == settings.date }.firstOrNull()
                if (publicationInList == null) {
                    publicationInList = Publication(database)
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

        fun getUnPublicationList(database: KaraokeConnection): MutableList<MutableList<Publication>> {
            var result: MutableList<MutableList<Publication>> = mutableListOf()

            val listUnpublished =
                Settings.loadListFromDb(mapOf("publish_date" to "-", "publish_time" to "-"), database)
                    .groupBy { it.author }
                    .map { it.value }
                    .sortedBy { it.size }

            val listStack: MutableList<Stack<Settings?>> = mutableListOf()
            for (i in listUnpublished.indices) {
                listStack.add(Stack<Settings?>())
            }

            listUnpublished.forEachIndexed { index, settings ->
                for (i in (settings.size-1)downTo 0 ) {
                    listStack[index].push(settings[i])
                }
            }

            var i = 0
            while (listStack.map { it.size }.max() > 0) {
                i++
                val publicationInList: MutableList<Publication> = mutableListOf()
                for (i in listUnpublished.indices) {
                    val publication = Publication(database)
                    publication.publish10 =
                        if (listStack[i].size > 0) {
                            listStack[i].pop()
                        } else {
                            null
                        }
                    publicationInList.add(publication)
                }
                result.add(publicationInList)
            }

            return result
        }

        fun getSettingsListForPublications(args: Map<String, String> = emptyMap(), database: KaraokeConnection): List<Settings> {

            var filterDateFrom =  args["filter_date_from"] ?: ""
            var filterDateTo =  args["filter_date_to"] ?: ""
            val filterCond =  args["filter_cond"] ?: ""

            if (filterCond == "all") {
                filterDateFrom = ""
                filterDateTo = ""
            } else if (filterCond == "fromtoday") {
                filterDateFrom = SimpleDateFormat("dd.MM.yy").format(Date())
                filterDateTo = ""
            } else if (filterCond == "fromnotpublish") {
                val listOfSettingsTemp = Settings.loadListFromDb(database = database).filter {
                    it.date != "" &&
                            it.time != "" &&
                            it.flags != ""
                }
                if (listOfSettingsTemp.isNotEmpty()) {
                    var minDate = ""
                    listOfSettingsTemp.forEach {
                        if (minDate == "" || SimpleDateFormat("dd.MM.yy").parse(it.date) < SimpleDateFormat("dd.MM.yy").parse(minDate)) {
                            minDate = it.date
                        }
                    }
                    println(minDate)
                    filterDateFrom = minDate
                    filterDateTo = ""
                }
            } else if (filterCond == "fromnotdone") {
                val listOfSettingsTemp = Settings.loadListFromDb(database = database).filter {
                    it.date != "" &&
                            it.time != "" &&
                            it.idStatus < 3L
                }
                if (listOfSettingsTemp.isNotEmpty()) {
                    var minDate = ""
                    listOfSettingsTemp.forEach {
                        if (minDate == "" || SimpleDateFormat("dd.MM.yy").parse(it.date) < SimpleDateFormat("dd.MM.yy").parse(minDate)) {
                            minDate = it.date
                        }
                    }
                    println(minDate)
                    filterDateFrom = minDate
                    filterDateTo = ""
                }
            } else if (filterCond == "fromnotcheck") {
                val listOfSettingsTemp = Settings.loadListFromDb(database = database).filter {
                    it.date != "" &&
                            it.time != "" &&
                            it.idStatus < 4L
                }
                if (listOfSettingsTemp.isNotEmpty()) {
                    var minDate = ""
                    listOfSettingsTemp.forEach {
                        if (minDate == "" || SimpleDateFormat("dd.MM.yy").parse(it.date) < SimpleDateFormat("dd.MM.yy").parse(minDate)) {
                            minDate = it.date
                        }
                    }
                    println(minDate)
                    filterDateFrom = minDate
                    filterDateTo = ""
                }

            } else if (filterCond == "unpublish") {
                return Settings.loadListFromDb(mapOf("publish_date" to "-", "publish_time" to "-"), database)
            }

            return Settings.loadListFromDb(database = database).filter {
                it.date != "" &&
                        it.time != "" &&
                        (if (filterDateFrom != "") SimpleDateFormat("dd.MM.yy").parse(it.date)  >= SimpleDateFormat("dd.MM.yy").parse(filterDateFrom) else true) &&
                        (if (filterDateTo != "") SimpleDateFormat("dd.MM.yy").parse(it.date)  <= SimpleDateFormat("dd.MM.yy").parse(filterDateTo) else true)
            }

        }

        fun getSettingsListForUnpublications(database: KaraokeConnection): List<Settings> {

            return Settings.loadListFromDb(mapOf("publish_date" to "-", "publish_time" to "-"), database)

        }

    }
}
