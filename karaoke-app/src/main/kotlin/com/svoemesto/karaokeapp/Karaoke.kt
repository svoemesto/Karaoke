package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.Converter.Companion.getColorFromString
import com.svoemesto.karaokeapp.Converter.Companion.getColorsFromString
import com.svoemesto.karaokeapp.Converter.Companion.getMltFontFromString
import com.svoemesto.karaokeapp.Converter.Companion.getMltShapeFromString
import com.svoemesto.karaokeapp.Converter.Companion.getStringFromVoices
import com.svoemesto.karaokeapp.Converter.Companion.getVoicesFromString
import com.svoemesto.karaokeapp.mlt.MltObjectType
import com.svoemesto.karaokeapp.mlt.MltShape
import com.svoemesto.karaokeapp.mlt.MltText
import com.svoemesto.karaokeapp.mlt.setting
import java.awt.Color
import java.awt.Font
import java.io.File
import java.io.Serializable
import java.util.*

class Karaoke : Serializable {
    companion object {
        private val fileNameXml = "src/main/resources/settings.xml"
        private val props = Properties()

        // Путь к папке с фонами
        var backgroundFolderPath: String
            get() {
                val defaultValue = "/home/nsa/Documents/SpaceBox4096"
                // props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("backgroundFolderPath",defaultValue)
            }
            set(value) {
                props.setProperty("backgroundFolderPath", value)
                // props.storeToXML(File(fileNameXml).outputStream(), null)
            }


        // Создавать логотип
        var createLogotype: Boolean
            get() {
                val defaultValue = "true"
                // props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("createLogotype",defaultValue).toBoolean()
            }
            set(value) {
                props.setProperty("createLogotype", value.toString())
                // props.storeToXML(File(fileNameXml).outputStream(), null)
            }


        // Создавать микрофон
        var createMicrophone: Boolean
            get() {
                val defaultValue = "true"
                // props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("createMicrophone",defaultValue).toBoolean()
            }
            set(value) {
                props.setProperty("createMicrophone", value.toString())
                // props.storeToXML(File(fileNameXml).outputStream(), null)
            }


        // Создавать заголовок
        var createHeader: Boolean
            get() {
                val defaultValue = "true"
                // props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("createHeader",defaultValue).toBoolean()
            }
            set(value) {
                props.setProperty("createHeader", value.toString())
                // props.storeToXML(File(fileNameXml).outputStream(), null)
            }


        // Создавать аудио ударных
        var createAudioDrums: Boolean
            get() {
                val defaultValue = "true"
                // props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("createAudioDrums",defaultValue).toBoolean()
            }
            set(value) {
                props.setProperty("createAudioDrums", value.toString())
                // props.storeToXML(File(fileNameXml).outputStream(), null)
            }


        // Создавать аудио баса
        var createAudioBass: Boolean
            get() {
                val defaultValue = "true"
                // props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("createAudioBass",defaultValue).toBoolean()
            }
            set(value) {
                props.setProperty("createAudioBass", value.toString())
                // props.storeToXML(File(fileNameXml).outputStream(), null)
            }

        // Создавать аудио вокала
        var createAudioVocal: Boolean
            get() {
                val defaultValue = "true"
                // props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("createAudioVocal",defaultValue).toBoolean()
            }
            set(value) {
                props.setProperty("createAudioVocal", value.toString())
                // props.storeToXML(File(fileNameXml).outputStream(), null)
            }


        // Создавать аудио музыки
        var createAudioMusic: Boolean
            get() {
                val defaultValue = "true"
                // props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("createAudioMusic",defaultValue).toBoolean()
            }
            set(value) {
                props.setProperty("createAudioMusic", value.toString())
                // props.storeToXML(File(fileNameXml).outputStream(), null)
            }

        // Создавать аудио песни
        var createAudioSong: Boolean
            get() {
                val defaultValue = "true"
                // props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("createAudioSong",defaultValue).toBoolean()
            }
            set(value) {
                props.setProperty("createAudioSong", value.toString())
                // props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Создавать фэйдер
        var createFader: Boolean
            get() {
                val defaultValue = "true"
                // props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("createFader",defaultValue).toBoolean()
            }
            set(value) {
                props.setProperty("createFader", value.toString())
                // props.storeToXML(File(fileNameXml).outputStream(),null)
            }


        // Создавать заливки
        var createFillsSongtext: Boolean
            get() {
                val defaultValue = "true"
                // props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("createFillsSongtext",defaultValue).toBoolean()
            }
            set(value) {
                props.setProperty("createFillsSongtext", value.toString())
                // props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        var createFillsChords: Boolean
            get() {
                val defaultValue = "true"
                // props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("createFillsChords",defaultValue).toBoolean()
            }
            set(value) {
                props.setProperty("createFillsChords", value.toString())
                // props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Раскрашивать горизонт
        var paintHorizon: Boolean
            get() {
                val defaultValue = "false"
                // props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("paintHorizon",defaultValue).toBoolean()
            }
            set(value) {
                props.setProperty("paintHorizon", value.toString())
                // props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Создавать горизонт
        var createHorizon: Boolean
            get() {
                val defaultValue = "true"
                // props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("createHorizon",defaultValue).toBoolean()
            }
            set(value) {
                props.setProperty("createHorizon", value.toString())
                // props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Создавать прогрессометр
        var createProgress: Boolean
            get() {
                val defaultValue = "true"
                // props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("createProgress",defaultValue).toBoolean()
            }
            set(value) {
                props.setProperty("createProgress", value.toString())
                // props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Создавать фон
        var createBackground: Boolean
            get() {
                val defaultValue = "true"
                // props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("createBackground",defaultValue).toBoolean()
            }
            set(value) {
                props.setProperty("createBackground", value.toString())
                // props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Создавать водяной знак
        var createWatermark: Boolean
            get() {
                val defaultValue = "true"
                // props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("createWatermark",defaultValue).toBoolean()
            }
            set(value) {
                props.setProperty("createWatermark", value.toString())
                // props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Создавать счётчики
        var createCounters: Boolean
            get() {
                val defaultValue = "true"
                // props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("createCounters",defaultValue).toBoolean()
            }
            set(value) {
                props.setProperty("createCounters", value.toString())
                // props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Создавать такты
        var createBeats: Boolean
            get() {
                val defaultValue = "false"
                // props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("createBeats",defaultValue).toBoolean()
            }
            set(value) {
                props.setProperty("createBeats", value.toString())
                // props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Создавать текст песни
        var createSongtext: Boolean
            get() {
                val defaultValue = "true"
                // props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("createSongtext",defaultValue).toBoolean()
            }
            set(value) {
                props.setProperty("createSongtext", value.toString())
                // props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Время (в миллисекундах) задержки звука от начала анимации
        var timeOffsetStartFillingLineMs: Long
            get() {
                val defaultValue = "170"
                // props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("timeOffsetStartFillingLineMs",defaultValue).toLong()
            }
            set(value) {
                props.setProperty("timeOffsetStartFillingLineMs", value.toString())
                // props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        var timeOffsetBluetoothSpeakerMs: Long
            get() {
                val defaultValue = "300"
                // props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("timeOffsetBluetoothSpeakerMs",defaultValue).toLong()
            }
            set(value) {
                props.setProperty("timeOffsetBluetoothSpeakerMs", value.toString())
                // props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Время показа в миллисекундах начальной заставки
        var timeSplashScreenLengthMs: Long
            get() {
                val defaultValue = "5000"
                // props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("timeSplashScreenLengthMs",defaultValue).toLong()
            }
            set(value) {
                props.setProperty("timeSplashScreenLengthMs", value.toString())
                // props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Время показа в миллисекундах boosty
        var timeBoostyLengthMs: Long
            get() {
                val defaultValue = "3000"
                // props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("timeBoostyLengthMs",defaultValue).toLong()
            }
            set(value) {
                props.setProperty("timeBoostyLengthMs", value.toString())
                // props.storeToXML(File(fileNameXml).outputStream(),null)
            }


        // Минимальное время (в миллисекундах) между линиями, меньше которого заливка последнего титра будет во время смещения линии
        var transferMinimumMsBetweenLinesToScroll: Long
            get() {
                val defaultValue = "200"
                // props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("transferMinimumMsBetweenLinesToScroll",defaultValue).toLong()
            }
            set(value) {
                props.setProperty("transferMinimumMsBetweenLinesToScroll", value.toString())
                // props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Отступ начала заливки от первого символа в строке (в пикселах)
        var songtextStartOffsetXpx: Long
            get() {
                val defaultValue = "20"
                // props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("songtextStartOffsetXpx",defaultValue).toLong()
            }
            set(value) {
                props.setProperty("songtextStartOffsetXpx", value.toString())
                // props.storeToXML(File(fileNameXml).outputStream(),null)
            }


        // Начальная позиция по Х текста песни на экране (в % от ширины экрана)
        var songtextStartPositionXpercent: Double
            get() {
                val defaultValue = "5.0"
                // props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("songtextStartPositionXpercent",defaultValue).toDouble()
            }
            set(value) {
                props.setProperty("songtextStartPositionXpercent", value.toString())
                // props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Начальная позиция по Х текста песни на экране (в пикселах)
        val songtextStartPositionXpx: Int
            get () {
                return (songtextStartPositionXpercent * frameWidthPx / 100).toInt()
            }

        // Смещение горизонта (в пикселах)
        var horizonOffsetPx: Long
            get() {
                val defaultValue = "-7"
                // props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("horizonOffsetPx",defaultValue).toLong()
            }
            set(value) {
                props.setProperty("horizonOffsetPx", value.toString())
                // props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Цвета горизонта для групп
        var countersColors: MutableList<Color>
            get() {
                val defaultValue = listOf(
                    Color(0,255,0,255),
                    Color(255,255,0,255),
                    Color(255,255,0,255),
                    Color(255,0,0,255),
                    Color(255,0,0,255)
                ).setting()
                // props.loadFromXML(File(fileNameXml).inputStream())
                return getColorsFromString(props.getProperty("countersColors", defaultValue))
            }
            set(value) {
                props.setProperty("countersColors", value.setting())
                // props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Цвета горизонта для групп
        var horizonColors: MutableList<Color>
            get() {
                val defaultValue = listOf(
                    Color(255,255,255,255),
                    Color(255,255,0,255),
                    Color(85,255,255,255)
                ).setting()
                // props.loadFromXML(File(fileNameXml).inputStream())
                return getColorsFromString(props.getProperty("horizonColors", defaultValue))
            }
            set(value) {
                props.setProperty("horizonColors", value.setting())
                // props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Цвет горизонта
        var horizonColor: Color
            get() {
                val defaultValue = Color(0,255,0,255).setting()
                // props.loadFromXML(File(fileNameXml).inputStream())
                return getColorFromString(props.getProperty("horizonColor", defaultValue))
            }
            set(value) {
                props.setProperty("horizonColor", value.setting())
                // props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        var flashColor: Color
            get() {
                val defaultValue = Color(255,0,0,255).setting()
                // props.loadFromXML(File(fileNameXml).inputStream())
                return getColorFromString(props.getProperty("flashColor", defaultValue))
            }
            set(value) {
                props.setProperty("flashColor", value.setting())
                // props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Настройки текста для голосов - групп
        var voices: MutableList<KaraokeVoice>
            get() {
                val defaultValue = "" +
                        "songtextTextMltFont|[NAME]|${MltText(font = Font(MAIN_FONT_NAME,0,80), shapeColor = Color(255,255,255,255), shapeOutlineColor = Color(0,0,0,255) , shapeOutline = 0, fontUnderline = 0).setting()}" +
                        "|[GROUP]|" +
                        "songtextTextMltFont|[NAME]|${MltText(font = Font(MAIN_FONT_NAME,2,80), shapeColor = Color(255,255,155,255), shapeOutlineColor = Color(0,0,0,255) , shapeOutline = 0, fontUnderline = 0).setting()}" +
                        "|[GROUP]|" +
                        "songtextTextMltFont|[NAME]|${MltText(font = Font(MAIN_FONT_NAME,0,80), shapeColor = Color(155,255,255,255), shapeOutlineColor = Color(0,0,0,255) , shapeOutline = 0, fontUnderline = 0).setting()}" +
                        "|[GROUP]|" +
                        "songtextTextMltFont|[NAME]|${MltText(font = Font(MAIN_FONT_NAME,2,80), shapeColor = Color(155,255,155,255), shapeOutlineColor = Color(0,0,0,255) , shapeOutline = 0, fontUnderline = 0).setting()}" +
                        "|[GROUP]|" +
                        "songtextTextMltFont|[NAME]|${MltText(font = Font(MAIN_FONT_NAME,2,80), shapeColor = Color(127,127,127,255), shapeOutlineColor = Color(0,0,0,255) , shapeOutline = 0, fontUnderline = 1).setting()}" +
                        "|[GROUP]|" +
                        "songtextTextMltFont|[NAME]|${MltText(font = Font(MAIN_FONT_NAME,2,80), shapeColor = Color(255,127,127,255), shapeOutlineColor = Color(0,0,0,255) , shapeOutline = 0, fontUnderline = 0).setting()}" +
                        "|[GROUP]|" +
                        "songtextTextMltFont|[NAME]|${MltText(font = Font(MAIN_FONT_NAME,2,80), shapeColor = Color(255,255,255,255), shapeOutlineColor = Color(0,0,0,255) , shapeOutline = 0, fontUnderline = 0).setting()}" +
                        "|[GROUP]|" +
                        "songtextTextMltFont|[NAME]|${MltText(font = Font(MAIN_FONT_NAME,0,80), shapeColor = Color(255,255,155,255), shapeOutlineColor = Color(0,0,0,255) , shapeOutline = 0, fontUnderline = 0).setting()}" +
                        "|[GROUP]|" +
                        "songtextTextMltFont|[NAME]|${MltText(font = Font(MAIN_FONT_NAME,2,80), shapeColor = Color(155,255,255,255), shapeOutlineColor = Color(0,0,0,255) , shapeOutline = 0, fontUnderline = 0).setting()}" +
                        "|[VOICEFIELDS]|" + "evenColor|[NAME]|${Color(255,128,0,255).setting()}" + "|[FIELD]|" + "evenOpacity|[NAME]|1.0" + "|[FIELD]|" + "oddColor|[NAME]|${Color(255,128,0,255).setting()}" + "|[FIELD]|" + "oddOpacity|[NAME]|1.0" +
                        ""
                // props.loadFromXML(File(fileNameXml).inputStream())
                return getVoicesFromString(props.getProperty("voices", defaultValue))
            }
            set(value) {
                props.setProperty("voices", getStringFromVoices(value))
                // props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Ширина экрана в пикселах
        var frameWidthPx: Int
            get() {
                val defaultValue = "1920"
                // props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("frameWidthPx",defaultValue).toInt()
            }
            set(value) {
                props.setProperty("frameWidthPx", value.toString())
                // props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Высота экрана в пикселах
        var frameHeightPx: Int
            get() {
                val defaultValue = "1080"
                // props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("frameHeightPx",defaultValue).toInt()
            }
            set(value) {
                props.setProperty("frameHeightPx", value.toString())
                // props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Frames per seconds
        var frameFps: Double
            get() {
                val defaultValue = "60"
                // props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("frameFps",defaultValue).toDouble()
            }
            set(value) {
                props.setProperty("frameFps", value.toString())
                // props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // HEADER


        var chordsFont: MltText
            get() {
                val defaultValue = MltText(
                    font = Font("Fira Sans Extra Condensed Medium", 0, 80),
                    shapeColor = Color(255,127,127,255),
                    shapeOutlineColor = Color(0,0,0,255),
                    shapeOutline = 0,
                    fontUnderline = 0
                ).setting()
                // props.loadFromXML(File(fileNameXml).inputStream())
                return getMltFontFromString(props.getProperty("chordsFont", defaultValue))
            }
            set(value) {
                props.setProperty("chordsFont", value.setting())
                // props.storeToXML(File(fileNameXml).outputStream(),null)
            }


        // Коэффициэнт размера шрифта аккорда относительно размера шрифта текста песни
        var chordsHeightCoefficient: Double
            get() {
                val defaultValue = "0.72"
                // props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("chordsHeightCoefficient",defaultValue).toDouble()
            }
            set(value) {
                props.setProperty("chordsHeightCoefficient", value.toString())
                // props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        var chordsCapoFont: MltText
            get() {
                val defaultValue = MltText(
                    font = Font("Fira Sans Extra Condensed Medium", 0, 35),
                    shapeColor = Color(255,127,127,255),
                    shapeOutlineColor = Color(255,127,127,255),
                    shapeOutline = 0,
                    fontUnderline = 0
                ).setting()
                // props.loadFromXML(File(fileNameXml).inputStream())
                return getMltFontFromString(props.getProperty("chordsCapoFont", defaultValue))
            }
            set(value) {
                props.setProperty("chordsCapoFont", value.setting())
                // props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Заголовок - Название песни - шрифт
        var headerSongnameFont: MltText
            get() {
                val defaultValue = MltText(
                    font = Font(MAIN_FONT_NAME, 0, 80),
                    shapeColor = Color(255,255,127,255),
                    shapeOutlineColor = Color(0,0,0,255),
                    shapeOutline = 0,
                    fontUnderline = 0
                ).setting()
                // props.loadFromXML(File(fileNameXml).inputStream())
                return getMltFontFromString(props.getProperty("headerSongnameFont", defaultValue))
            }
            set(value) {
                props.setProperty("headerSongnameFont", value.setting())
                // props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Максимальная позиция по X до которой должна быть надпись названия песни, чтобы не перекрывать логотип
        var headerSongnameMaxX: Long
            get() {
                val defaultValue = "1200"
                // props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("headerSongnameMaxX",defaultValue).toLong()
            }
            set(value) {
                props.setProperty("headerSongnameMaxX", value.toString())
                // props.storeToXML(File(fileNameXml).outputStream(), null)
            }

        // Заголовок - Автор - шрифт
        var headerAuthorFont: MltText
            get() {
                val defaultValue = MltText(
                    font = Font(MAIN_FONT_NAME, 0, 30),
                    shapeColor = Color(255,255,127,255),
                    shapeOutlineColor = Color(0,0,0,255),
                    shapeOutline = 0,
                    fontUnderline = 0
                ).setting()
                // props.loadFromXML(File(fileNameXml).inputStream())
                return getMltFontFromString(props.getProperty("headerAuthorFont", defaultValue))
            }
            set(value) {
                props.setProperty("headerAuthorFont", value.setting())
                // props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        var headerAuthorNameFont: MltText
            get() {
                val defaultValue = MltText(
                    font = Font(MAIN_FONT_NAME, 0, 30),
                    shapeColor = Color(85,255,255,255),
                    shapeOutlineColor = Color(0,0,0,255),
                    shapeOutline = 0,
                    fontUnderline = 0
                ).setting()
                // props.loadFromXML(File(fileNameXml).inputStream())
                return getMltFontFromString(props.getProperty("headerAuthorNameFont", defaultValue))
            }
            set(value) {
                props.setProperty("headerAuthorNameFont", value.setting())
                // props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        var headerAuthorName: String
            get() {
                val defaultValue = "Исполнитель: "
                // props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("headerAuthorName",defaultValue)
            }
            set(value) {
                props.setProperty("headerAuthorName", value)
                // props.storeToXML(File(fileNameXml).outputStream(), null)
            }

        // Заголовок - Название альбома - шрифт
        var headerAlbumFont: MltText
            get() {
                val defaultValue = MltText(
                    font = Font(MAIN_FONT_NAME, 0, 30),
                    shapeColor = Color(255,255,127,255),
                    shapeOutlineColor = Color(0,0,0,255),
                    shapeOutline = 0,
                    fontUnderline = 0
                ).setting()
                // props.loadFromXML(File(fileNameXml).inputStream())
                return getMltFontFromString(props.getProperty("headerAlbumFont", defaultValue))
            }
            set(value) {
                props.setProperty("headerAlbumFont", value.setting())
                // props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        var headerAlbumNameFont: MltText
            get() {
                val defaultValue = MltText(
                    font = Font(MAIN_FONT_NAME, 0, 30),
                    shapeColor = Color(85,255,255,255),
                    shapeOutlineColor = Color(0,0,0,255),
                    shapeOutline = 0,
                    fontUnderline = 0
                ).setting()
                // props.loadFromXML(File(fileNameXml).inputStream())
                return getMltFontFromString(props.getProperty("headerAlbumNameFont", defaultValue))
            }
            set(value) {
                props.setProperty("headerAlbumNameFont", value.setting())
                // props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        var headerAlbumName: String
            get() {
                val defaultValue = "Альбом: "
                // props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("headerAlbumName",defaultValue)
            }
            set(value) {
                props.setProperty("headerAlbumName", value)
                // props.storeToXML(File(fileNameXml).outputStream(), null)
            }

        // Заголовок - Тональность - шрифт
        var headerToneFont: MltText
            get() {
                val defaultValue = MltText(
                    font = Font(MAIN_FONT_NAME, 0, 30),
                    shapeColor = Color(255,255,127,255),
                    shapeOutlineColor = Color(0,0,0,255),
                    shapeOutline = 0,
                    fontUnderline = 0
                ).setting()
                // props.loadFromXML(File(fileNameXml).inputStream())
                return getMltFontFromString(props.getProperty("headerToneFont", defaultValue))
            }
            set(value) {
                props.setProperty("headerAlbumFont", value.setting())
                // props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        var headerToneNameFont: MltText
            get() {
                val defaultValue = MltText(
                    font = Font(MAIN_FONT_NAME, 0, 30),
                    shapeColor = Color(85,255,255,255),
                    shapeOutlineColor = Color(0,0,0,255),
                    shapeOutline = 0,
                    fontUnderline = 0
                ).setting()
                // props.loadFromXML(File(fileNameXml).inputStream())
                return getMltFontFromString(props.getProperty("headerToneNameFont", defaultValue))
            }
            set(value) {
                props.setProperty("headerToneNameFont", value.setting())
                // props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        var headerToneName: String
            get() {
                val defaultValue = "Тональность: "
                // props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("headerToneName",defaultValue)
            }
            set(value) {
                props.setProperty("headerToneName", value)
                // props.storeToXML(File(fileNameXml).outputStream(), null)
            }


        // Заголовок - Темп - шрифт
        var headerBpmFont: MltText
            get() {
                val defaultValue = MltText(
                    font = Font(MAIN_FONT_NAME, 0, 30),
                    shapeColor = Color(255,255,127,255),
                    shapeOutlineColor = Color(0,0,0,255),
                    shapeOutline = 0,
                    fontUnderline = 0
                ).setting()
                // props.loadFromXML(File(fileNameXml).inputStream())
                return getMltFontFromString(props.getProperty("headerBpmFont", defaultValue))
            }
            set(value) {
                props.setProperty("headerBpmFont", value.setting())
                // props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        var headerBpmNameFont: MltText
            get() {
                val defaultValue = MltText(
                    font = Font(MAIN_FONT_NAME, 0, 30),
                    shapeColor = Color(85,255,255,255),
                    shapeOutlineColor = Color(0,0,0,255),
                    shapeOutline = 0,
                    fontUnderline = 0
                ).setting()
                // props.loadFromXML(File(fileNameXml).inputStream())
                return getMltFontFromString(props.getProperty("headerBpmNameFont", defaultValue))
            }
            set(value) {
                props.setProperty("headerBpmNameFont", value.setting())
                // props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        var headerBpmName: String
            get() {
                val defaultValue = "Темп: "
                // props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("headerBpmName",defaultValue)
            }
            set(value) {
                props.setProperty("headerBpmName", value)
                // props.storeToXML(File(fileNameXml).outputStream(), null)
            }

        // Прогрессометр - шрифт
        var progressFont: MltText
            get() {
                val defaultValue = MltText(
                    font = Font("Tahoma", 0, 20),
                    shapeColor = Color(255,255,255,255),
                    shapeOutlineColor = Color(0,0,0,255),
                    shapeOutline = 0,
                    fontUnderline = 0
                ).setting()
                // props.loadFromXML(File(fileNameXml).inputStream())
                return getMltFontFromString(props.getProperty("progressFont", defaultValue))
            }
            set(value) {
                props.setProperty("progressFont", value.setting())
                // props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        var progressSymbol: String
            get() {
                val defaultValue = "▲"
                // props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("progressSymbol",defaultValue)
            }
            set(value) {
                props.setProperty("progressSymbol", value)
                // props.storeToXML(File(fileNameXml).outputStream(), null)
            }

        // Бусти - шрифт
        var boostyFont: MltText
            get() {
                val defaultValue = MltText(
                    font = Font(MAIN_FONT_NAME, 0, 100),
                    shapeColor = Color(255,255,255,255),
                    shapeOutlineColor = Color(0,0,0,255),
                    shapeOutline = 0,
                    fontUnderline = 0
                    ).setting()
                // props.loadFromXML(File(fileNameXml).inputStream())
                return getMltFontFromString(props.getProperty("boostyFont", defaultValue))
            }
            set(value) {
                props.setProperty("boostyFont", value.setting())
                // props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Текст Бусти
        var boostyText: String
            get() {
                val defaultValue = "Поддержи создание караоке\nна https://boosty.to/svoemesto\n\nГруппа ВКонтакте:\nhttps://vk.com/svoemestokaraoke\n\nВсе ссылки - в описании."
                // props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("boostyText",defaultValue)
            }
            set(value) {
                props.setProperty("boostyText", value)
                // props.storeToXML(File(fileNameXml).outputStream(), null)
            }

        // Водяной знак - шрифт
        var watermarkFont: MltText
            get() {
                val defaultValue = MltText(
                    font = Font(MAIN_FONT_NAME, 0, 10),
                    shapeColor = Color(255,255,255,127),
                    shapeOutlineColor = Color(0,0,0,255),
                    shapeOutline = 0,
                    fontUnderline = 0
                ).setting()
                // props.loadFromXML(File(fileNameXml).inputStream())
                return getMltFontFromString(props.getProperty("watermarkFont", defaultValue))
            }
            set(value) {
                props.setProperty("watermarkFont", value.setting())
                // props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Текст водяного знака
        var watermarkText: String
            get() {
                val defaultValue = "https://github.com/svoemesto/Karaoke"
                // props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("watermarkText",defaultValue)
            }
            set(value) {
                props.setProperty("watermarkText", value)
                // props.storeToXML(File(fileNameXml).outputStream(), null)
            }


        var splashstartSongNameFont: MltText
            get() {
                val defaultValue = MltText(
                    font = Font(MAIN_FONT_NAME, 0, 10),
                    shapeColor = Color(255,255,127,255),
                    shapeOutlineColor = Color(0,0,0,255),
                    shapeOutline = 0,
                    fontUnderline = 0
                ).setting()
                // props.loadFromXML(File(fileNameXml).inputStream())
                return getMltFontFromString(props.getProperty("splashstartSongNameFont", defaultValue))
            }
            set(value) {
                props.setProperty("splashstartSongNameFont", value.setting())
                // props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        var splashstartSongVersionFont: MltText
            get() {
                val defaultValue = MltText(
                    font = Font(MAIN_FONT_NAME, 0, 150),
                    shapeColor = Color(85,255,255,255),
                    shapeOutlineColor = Color(0,0,0,255),
                    shapeOutline = 0,
                    fontUnderline = 0
                ).setting()
                // props.loadFromXML(File(fileNameXml).inputStream())
                return getMltFontFromString(props.getProperty("splashstartSongVersionFont", defaultValue))
            }
            set(value) {
                props.setProperty("splashstartSongVersionFont", value.setting())
                // props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        var splashstartCommentFont: MltText
            get() {
                val defaultValue = MltText(
                    font = Font(MAIN_FONT_NAME, 0, 60),
                    shapeColor = Color(85,255,255,255),
                    shapeOutlineColor = Color(0,0,0,255),
                    shapeOutline = 0,
                    fontUnderline = 0
                ).setting()
                // props.loadFromXML(File(fileNameXml).inputStream())
                return getMltFontFromString(props.getProperty("splashstartCommentFont", defaultValue))
            }
            set(value) {
                props.setProperty("splashstartSongVersionFont", value.setting())
                // props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        var splashstartChordDescriptionFont: MltText
            get() {
                val defaultValue = MltText(
                    font = Font("Fira Sans Extra Condensed Medium", 0, 40),
                    shapeColor = Color(255,127,127,255),
                    shapeOutlineColor = Color(0,0,0,255),
                    shapeOutline = 0,
                    fontUnderline = 0
                ).setting()
                // props.loadFromXML(File(fileNameXml).inputStream())
                return getMltFontFromString(props.getProperty("splashstartChordDescriptionFont", defaultValue))
            }
            set(value) {
                props.setProperty("splashstartChordDescriptionFont", value.setting())
                // props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        // Время в миллисекундах. Если субтитр длится дольше этого времени - закраска увеличивается
        var shortSubtitleMs: Long
            get() {
                val defaultValue = "750"
                // props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("shortSubtitleMs",defaultValue).toLong()
            }
            set(value) {
                props.setProperty("shortSubtitleMs", value.toString())
                // props.storeToXML(File(fileNameXml).outputStream(), null)
            }

        var chordLayoutW: Int
            get() {
                val defaultValue = "800"
                // props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("chordLayoutW",defaultValue).toInt()
            }
            set(value) {
                props.setProperty("chordLayoutW", value.toString())
                // props.storeToXML(File(fileNameXml).outputStream(), null)
            }

        var chordLayoutH: Int
            get() {
                val defaultValue = "800"
                // props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("chordLayoutH",defaultValue).toInt()
            }
            set(value) {
                props.setProperty("chordLayoutH", value.toString())
                // props.storeToXML(File(fileNameXml).outputStream(), null)
            }

        var shortLineMs: Long
            get() {
                val defaultValue = "200"
                // props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("shortLineMs",defaultValue).toLong()
            }
            set(value) {
                props.setProperty("shortLineMs", value.toString())
                // props.storeToXML(File(fileNameXml).outputStream(), null)
            }

        var maxCountChordsInFingerboard: Int
            get() {
                val defaultValue = "100"
                // props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("maxCountChordsInFingerboard",defaultValue).toInt()
            }
            set(value) {
                props.setProperty("maxCountChordsInFingerboard", value.toString())
                // props.storeToXML(File(fileNameXml).outputStream(), null)
            }


        var shortLineFontScaleCoeff: Double
            get() {
                val defaultValue = "0.75"
                // props.loadFromXML(File(fileNameXml).inputStream())
                return props.getProperty("shortLineFontScaleCoeff",defaultValue).toDouble()
            }
            set(value) {
                props.setProperty("shortLineFontScaleCoeff", value.toString())
                // props.storeToXML(File(fileNameXml).outputStream(), null)
            }

        var chordLayoutChordNameMltText: MltText
            get() {
                val defaultValue = MltText(
                    font = Font(MAIN_FONT_NAME, 0, 10),
                    shapeColor = Color(255,255,127,255),
                    shapeOutlineColor = Color(0,0,0,255),
                    shapeOutline = 0,
                    fontUnderline = 0
                ).setting()
                // props.loadFromXML(File(fileNameXml).inputStream())
                return getMltFontFromString(props.getProperty("chordLayoutChordNameMltText", defaultValue))
            }
            set(value) {
                props.setProperty("chordLayoutChordNameMltText", value.setting())
                // props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        var chordLayoutFretsNumbersMltText: MltText
            get() {
                val defaultValue = MltText(
                    font = Font(MAIN_FONT_NAME, 0, 10),
                    shapeColor = Color(127,127,127,255),
                    shapeOutlineColor = Color(0,0,0,255),
                    shapeOutline = 0,
                    fontUnderline = 0
                ).setting()
                // props.loadFromXML(File(fileNameXml).inputStream())
                return getMltFontFromString(props.getProperty("chordLayoutFretsNumbersMltText", defaultValue))
            }
            set(value) {
                props.setProperty("chordLayoutFretsNumbersMltText", value.setting())
                // props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        var chordLayoutFretsRectangleMltShape: MltShape
            get() {
                val defaultValue = MltShape(
                    type = MltObjectType.RECTANGLE,
                    shapeColor = Color(255,255,255,127),
                    shapeOutlineColor = Color(255,255,255,255),
                    shapeOutline = 2
                ).setting()
                // props.loadFromXML(File(fileNameXml).inputStream())
                return getMltShapeFromString(props.getProperty("chordLayoutFretsRectangleMltShape", defaultValue))
            }
            set(value) {
                props.setProperty("chordLayoutFretsRectangleMltShape", value.setting())
                // props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        var chordLayoutNutsRectangleMltShape: MltShape
            get() {
                val defaultValue = MltShape(
                    type = MltObjectType.RECTANGLE,
                    shapeColor = Color(255,255,255,255),
                    shapeOutlineColor = Color(255,255,255,255),
                    shapeOutline = 2
                ).setting()
                // props.loadFromXML(File(fileNameXml).inputStream())
                return getMltShapeFromString(props.getProperty("chordLayoutNutsRectangleMltShape", defaultValue))
            }
            set(value) {
                props.setProperty("chordLayoutNutsRectangleMltShape", value.setting())
                // props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        var chordLayoutСapoRectangleMltShape: MltShape
            get() {
                val defaultValue = MltShape(
                    type = MltObjectType.RECTANGLE,
                    shapeColor = Color(255,0,0,255),
                    shapeOutlineColor = Color(255,0,0,255),
                    shapeOutline = 2
                ).setting()
                // props.loadFromXML(File(fileNameXml).inputStream())
                return getMltShapeFromString(props.getProperty("chordLayoutСapoRectangleMltShape", defaultValue))
            }
            set(value) {
                props.setProperty("chordLayoutСapoRectangleMltShape", value.setting())
                // props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        var chordLayoutBackgroundRectangleMltShape: MltShape
            get() {
                val defaultValue = MltShape(
                    type = MltObjectType.RECTANGLE,
                    shapeColor = Color(0,0,0,255),
                    shapeOutlineColor = Color(255,255,255,20),
                    shapeOutline = 2
                ).setting()
                // props.loadFromXML(File(fileNameXml).inputStream())
                return getMltShapeFromString(props.getProperty("chordLayoutBackgroundRectangleMltShape", defaultValue))
            }
            set(value) {
                props.setProperty("chordLayoutBackgroundRectangleMltShape", value.setting())
                // props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        var chordLayoutMutedRectangleMltShape: MltShape
            get() {
                val defaultValue = MltShape(
                    type = MltObjectType.RECTANGLE,
                    shapeColor = Color(255,0,0,200),
                    shapeOutlineColor = Color(255,0,0,0),
                    shapeOutline = 0
                ).setting()
                // props.loadFromXML(File(fileNameXml).inputStream())
                return getMltShapeFromString(props.getProperty("chordLayoutMutedRectangleMltShape", defaultValue))
            }
            set(value) {
                props.setProperty("chordLayoutMutedRectangleMltShape", value.setting())
                // props.storeToXML(File(fileNameXml).outputStream(),null)
            }

        var chordLayoutFingerCircleMltShape: MltShape
            get() {
                val defaultValue = MltShape(
                    type = MltObjectType.CIRCLE,
                    shapeColor = Color(255,0,0,255),
                    shapeOutlineColor = Color(255,255,255,255),
                    shapeOutline = 2
                ).setting()
                // props.loadFromXML(File(fileNameXml).inputStream())
                return getMltShapeFromString(props.getProperty("chordLayoutFingerCircleMltShape", defaultValue))
            }
            set(value) {
                props.setProperty("chordLayoutFingerCircleMltShape", value.setting())
                // props.storeToXML(File(fileNameXml).outputStream(),null)
            }
    }
}