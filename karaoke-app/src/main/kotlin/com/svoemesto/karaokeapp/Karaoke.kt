package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.Converter.Companion.getColorFromString
import com.svoemesto.karaokeapp.Converter.Companion.getColorsFromString
import com.svoemesto.karaokeapp.Converter.Companion.getMltFontFromString
import com.svoemesto.karaokeapp.Converter.Companion.getMltShapeFromString
import com.svoemesto.karaokeapp.Converter.Companion.getStringFromVoices
import com.svoemesto.karaokeapp.Converter.Companion.getVoicesFromString
import com.svoemesto.karaokeapp.mlt.MltShape
import com.svoemesto.karaokeapp.mlt.MltText
import com.svoemesto.karaokeapp.mlt.setting
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.awt.Color
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Serializable
import java.util.*

@kotlinx.serialization.Serializable
data class KaraokePropertySerializable(
    val key: String,
    val serializableValue: String
) {
    companion object {
        @OptIn(ExperimentalSerializationApi::class)
        fun create(key: String, value: Any): KaraokePropertySerializable {
            val kpInList = listKaraokeProperties.firstOrNull { it.key == key }
            val serializableValue = if (kpInList == null) {
                value.toString()
            } else {
                when(value) {
                    is String -> {
                        val bout = ByteArrayOutputStream()
                        Json.encodeToStream(String.serializer(), value, bout)
                        Base64.getEncoder().encodeToString(bout.toByteArray())
                    }
                    is Long -> {
                        val bout = ByteArrayOutputStream()
                        Json.encodeToStream(Long.serializer(), value, bout)
                        Base64.getEncoder().encodeToString(bout.toByteArray())
                    }
                    is Int -> {
                        val bout = ByteArrayOutputStream()
                        Json.encodeToStream(Int.serializer(), value, bout)
                        Base64.getEncoder().encodeToString(bout.toByteArray())
                    }
                    is Double -> {
                        val bout = ByteArrayOutputStream()
                        Json.encodeToStream(Double.serializer(), value, bout)
                        Base64.getEncoder().encodeToString(bout.toByteArray())
                    }
                    is Boolean -> {
                        val bout = ByteArrayOutputStream()
                        Json.encodeToStream(Boolean.serializer(), value, bout)
                        Base64.getEncoder().encodeToString(bout.toByteArray())
                    }
                    else -> ""
                }
            }
            return KaraokePropertySerializable(
                key = key,
                serializableValue = serializableValue
            )
        }
    }
    @OptIn(ExperimentalSerializationApi::class)
    fun value(): Any {

        val kpInList = listKaraokeProperties.firstOrNull { it.key == key } ?: return ""
        val ba = Base64.getDecoder().decode(serializableValue)

        return when(kpInList.defaultValue) {
            is String -> {
                val tmp: String = Json.decodeFromStream(ByteArrayInputStream(ba))
                tmp
            }
            is Long -> {
                val tmp: Long = Json.decodeFromStream(ByteArrayInputStream(ba))
                tmp
            }
            is Int -> {
                val tmp: Int = Json.decodeFromStream(ByteArrayInputStream(ba))
                tmp
            }
            is Double -> {
                val tmp: Double = Json.decodeFromStream(ByteArrayInputStream(ba))
                tmp
            }
            is Boolean -> {
                val tmp: Boolean = Json.decodeFromStream(ByteArrayInputStream(ba))
                tmp
            }
            is Color -> {
                val tmp: Color = Json.decodeFromStream(ByteArrayInputStream(ba))
                tmp
            }
            is List<*> -> {
                val tmp: List<String> = Json.decodeFromStream(ByteArrayInputStream(ba))
                tmp
            }
            is MltText -> {
                val tmp: MltText = Json.decodeFromStream(ByteArrayInputStream(ba))
                tmp
            }
            is MltShape -> {
                val tmp: MltShape = Json.decodeFromStream(ByteArrayInputStream(ba))
                tmp
            }
            else -> ""
        }
    }
}
data class KaraokeProperty(
    val key: String,
    val defaultValue: Any,
    val description: String
)

data class KaraokePropertyDTO(
    val key: String = "",
    val value: String = "",
    val defaultValue: String = "",
    val description: String = "",
    val type: String = ""
) : Serializable

class Karaoke : Serializable {
    companion object {

        // Время последнего запроса поиска новой песни
        var requestNewSongLastTimeMs: Long
            get() = KaraokeProperties.getLong("requestNewSongLastTimeMs")
            set(value) { KaraokeProperties.set("requestNewSongLastTimeMs", value) }
        var requestNewSongLastTimeCode: String
            get() = KaraokeProperties.getString("requestNewSongLastTimeCode")
            set(value) { KaraokeProperties.set("requestNewSongLastTimeCode", value) }

        // Время последнего удачного запроса поиска новой песни
        var requestNewSongLastSuccessTimeMs: Long
            get() = KaraokeProperties.getLong("requestNewSongLastSuccessTimeMs")
            set(value) { KaraokeProperties.set("requestNewSongLastSuccessTimeMs", value) }
        var requestNewSongLastSuccessTimeCode: String
            get() = KaraokeProperties.getString("requestNewSongLastSuccessTimeCode")
            set(value) { KaraokeProperties.set("requestNewSongLastSuccessTimeCode", value) }

        // Автор последнего удачного запроса поиска новой песни
        var requestNewSongLastSuccessAuthor: String
            get() = KaraokeProperties.getString("requestNewSongLastSuccessAuthor")
            set(value) { KaraokeProperties.set("requestNewSongLastSuccessAuthor", value) }
        var requestNewSongLastAuthor: String
            get() = KaraokeProperties.getString("requestNewSongLastAuthor")
            set(value) { KaraokeProperties.set("requestNewSongLastAuthor", value) }

        // На сколько увеличивать задержку между запросами в случае неудачи
        var requestNewSongTimeoutIncreaseMs: Long
            get() = KaraokeProperties.getLong("requestNewSongTimeoutIncreaseMs")
            set(value) { KaraokeProperties.set("requestNewSongTimeoutIncreaseMs", value) }

        // Задержка между запросами поиска новых песен автора
        var requestNewSongTimeoutMs: Long
            get() = KaraokeProperties.getLong("requestNewSongTimeoutMs")
            set(value) { KaraokeProperties.set("requestNewSongTimeoutMs", value) }
        var requestNewSongTimeoutMin: Long
            get() = KaraokeProperties.getLong("requestNewSongTimeoutMin")
            set(value) { KaraokeProperties.set("requestNewSongTimeoutMin", value) }

        //Автосохранение
        @Suppress("unused")
        var autoSave: Boolean
            get() = KaraokeProperties.getBoolean("autoSave")
            set(value) { KaraokeProperties.set("autoSave", value) }

        //Автообновление записи в удаленной БД при сохранении
        var autoUpdateRemoteSettings: Boolean
            get() = KaraokeProperties.getBoolean("autoUpdateRemoteSettings")
            set(value) { KaraokeProperties.set("autoUpdateRemoteSettings", value) }

        //Мониторинг sync-записей в удаленной БД
        var monitoringRemoteSettingsSync: Boolean
            get() = KaraokeProperties.getBoolean("monitoringRemoteSettingsSync")
            set(value) { KaraokeProperties.set("monitoringRemoteSettingsSync", value) }

        //Мониторинг sync-записей в удаленной БД
        var checkLastAlbum: Boolean
            get() = KaraokeProperties.getBoolean("checkLastAlbum")
            set(value) { KaraokeProperties.set("checkLastAlbum", value) }

        //Мониторинг sync-записей в удаленной БД
        var allowUpdateRemote: Boolean
            get() = KaraokeProperties.getBoolean("allowUpdateRemote")
            set(value) { KaraokeProperties.set("allowUpdateRemote", value) }

        //Мониторинг sync-записей в удаленной БД
        @Suppress("unused")
        var allowUpdateLocal: Boolean
            get() = KaraokeProperties.getBoolean("allowUpdateLocal")
            set(value) { KaraokeProperties.set("allowUpdateLocal", value) }

        //Мониторинг sync-записей в удаленной БД
        @Suppress("unused")
        var allowAddSync: Boolean
            get() = KaraokeProperties.getBoolean("allowAddSync")
            set(value) { KaraokeProperties.set("allowAddSync", value) }

        // Время (в миллисекундах) задержки перед автосохранением
        @Suppress("unused")
        var autoSaveDelayMs: Long
            get() = KaraokeProperties.getLong("autoSaveDelayMs")
            set(value) { KaraokeProperties.set("autoSaveDelayMs", value) }

        // Путь к папке с фонами
        var backgroundFolderPath: String
            get() = KaraokeProperties.getString("backgroundFolderPath")
            set(value) { KaraokeProperties.set("backgroundFolderPath", value) }

        // Создавать логотип
        var createLogotype: Boolean
            get() = KaraokeProperties.getBoolean("createLogotype")
            set(value) { KaraokeProperties.set("createLogotype", value) }

        // Создавать микрофон
        @Suppress("unused")
        var createMicrophone: Boolean
            get() = KaraokeProperties.getBoolean("createMicrophone")
            set(value) { KaraokeProperties.set("createMicrophone", value) }

        // Создавать заголовок
        var createHeader: Boolean
            get() = KaraokeProperties.getBoolean("createHeader")
            set(value) { KaraokeProperties.set("createHeader", value) }

        // Создавать аудио ударных
        var createAudioDrums: Boolean
            get() = KaraokeProperties.getBoolean("createAudioDrums")
            set(value) { KaraokeProperties.set("createAudioDrums", value) }

        // Создавать аудио баса
        var createAudioBass: Boolean
            get() = KaraokeProperties.getBoolean("createAudioBass")
            set(value) { KaraokeProperties.set("createAudioBass", value) }

        // Создавать аудио вокала
        var createAudioVocal: Boolean
            get() = KaraokeProperties.getBoolean("createAudioVocal")
            set(value) { KaraokeProperties.set("createAudioVocal", value) }

        // Создавать аудио музыки
        var createAudioMusic: Boolean
            get() = KaraokeProperties.getBoolean("createAudioMusic")
            set(value) { KaraokeProperties.set("createAudioMusic", value) }

        // Создавать аудио песни
        var createAudioSong: Boolean
            get() = KaraokeProperties.getBoolean("createAudioSong")
            set(value) { KaraokeProperties.set("createAudioSong", value) }

        // Создавать фэйдер
        var createFader: Boolean
            get() = KaraokeProperties.getBoolean("createFader")
            set(value) { KaraokeProperties.set("createFader", value) }

        // Создавать заливки
        var createFillsSongtext: Boolean
            get() = KaraokeProperties.getBoolean("createFillsSongtext")
            set(value) { KaraokeProperties.set("createFillsSongtext", value) }

        @Suppress("unused")
        var createFillsChords: Boolean
            get() = KaraokeProperties.getBoolean("createFillsChords")
            set(value) { KaraokeProperties.set("createFillsChords", value) }

        // Раскрашивать горизонт
        var paintHorizon: Boolean
            get() = KaraokeProperties.getBoolean("paintHorizon")
            set(value) { KaraokeProperties.set("paintHorizon", value) }

        // Создавать горизонт
        var createHorizon: Boolean
            get() = KaraokeProperties.getBoolean("createHorizon")
            set(value) { KaraokeProperties.set("createHorizon", value) }

        // Создавать прогрессометр
        var createProgress: Boolean
            get() = KaraokeProperties.getBoolean("createProgress")
            set(value) { KaraokeProperties.set("createProgress", value) }

        // Создавать фон
        var createBackground: Boolean
            get() = KaraokeProperties.getBoolean("createBackground")
            set(value) { KaraokeProperties.set("createBackground", value) }

        // Создавать водяной знак
        var createWatermark: Boolean
            get() = KaraokeProperties.getBoolean("createWatermark")
            set(value) { KaraokeProperties.set("createWatermark", value) }

        // Создавать счётчики
        var createCounters: Boolean
            get() = KaraokeProperties.getBoolean("createCounters")
            set(value) { KaraokeProperties.set("createCounters", value) }

        // Создавать такты
        @Suppress("unused")
        var createBeats: Boolean
            get() = KaraokeProperties.getBoolean("createBeats")
            set(value) { KaraokeProperties.set("createBeats", value) }

        // Создавать текст песни
        var createSongtext: Boolean
            get() = KaraokeProperties.getBoolean("createSongtext")
            set(value) { KaraokeProperties.set("createSongtext", value) }

        // Время (в миллисекундах) задержки звука от начала анимации
        @Suppress("unused")
        var timeOffsetStartFillingLineMs: Long
            get() = KaraokeProperties.getLong("timeOffsetStartFillingLineMs")
            set(value) { KaraokeProperties.set("timeOffsetStartFillingLineMs", value) }

        @Suppress("unused")
        var timeOffsetBluetoothSpeakerMs: Long
            get() = KaraokeProperties.getLong("timeOffsetBluetoothSpeakerMs")
            set(value) { KaraokeProperties.set("timeOffsetBluetoothSpeakerMs", value) }

        // Время показа в миллисекундах начальной заставки
        var timeSplashScreenLengthMs: Long
            get() = KaraokeProperties.getLong("timeSplashScreenLengthMs")
            set(value) { KaraokeProperties.set("timeSplashScreenLengthMs", value) }

        // Время показа в миллисекундах boosty
        var timeBoostyLengthMs: Long
            get() = KaraokeProperties.getLong("timeBoostyLengthMs")
            set(value) { KaraokeProperties.set("timeBoostyLengthMs", value) }

        // Минимальное время (в миллисекундах) между линиями, меньше которого заливка последнего титра будет во время смещения линии
        @Suppress("unused")
        var transferMinimumMsBetweenLinesToScroll: Long
            get() = KaraokeProperties.getLong("transferMinimumMsBetweenLinesToScroll")
            set(value) { KaraokeProperties.set("transferMinimumMsBetweenLinesToScroll", value) }

        // Отступ начала заливки от первого символа в строке (в пикселах)
        @Suppress("unused")
        var songtextStartOffsetXpx: Long
            get() = KaraokeProperties.getLong("songtextStartOffsetXpx")
            set(value) { KaraokeProperties.set("songtextStartOffsetXpx", value) }

        // Начальная позиция по Х текста песни на экране (в % от ширины экрана)
        var songtextStartPositionXpercent: Double
            get() = KaraokeProperties.getDouble("songtextStartPositionXpercent")
            set(value) { KaraokeProperties.set("songtextStartPositionXpercent", value) }

        // Начальная позиция по Х текста песни на экране (в пикселах)
        val songtextStartPositionXpx: Int
            get () {
                return (songtextStartPositionXpercent * frameWidthPx / 100).toInt()
            }

        // Смещение горизонта (в пикселах)
        var horizonOffsetPx: Long
            get() = KaraokeProperties.getLong("horizonOffsetPx")
            set(value) { KaraokeProperties.set("horizonOffsetPx", value) }

        // Цвета каунтеров
        var countersColors: MutableList<Color>
            get() = getColorsFromString(KaraokeProperties.getString("countersColors"))
            set(value) { KaraokeProperties.set("countersColors", value.setting()) }

        // Цвета горизонта для групп
        var horizonColors: MutableList<Color>
            get() = getColorsFromString(KaraokeProperties.getString("horizonColors"))
            set(value) { KaraokeProperties.set("horizonColors", value.setting()) }

        // Цвет линии разделителя слогов
        var separLineColor: Color
            get() = getColorFromString(KaraokeProperties.getString("separLineColor"))
            set(value) { KaraokeProperties.set("separLineColor", value.setting()) }

        // Цвет линий табулатуры нот
        var tabsLineColor: Color
            get() = getColorFromString(KaraokeProperties.getString("tabsLineColor"))
            set(value) { KaraokeProperties.set("tabsLineColor", value.setting()) }

        // Цвет горизонта
        var horizonColor: Color
            get() = getColorFromString(KaraokeProperties.getString("horizonColor"))
            set(value) { KaraokeProperties.set("horizonColor", value.setting()) }

        // Цвет флеша горизонта
        var flashColor: Color
            get() = getColorFromString(KaraokeProperties.getString("flashColor"))
            set(value) { KaraokeProperties.set("flashColor", value.setting()) }

        // Настройки текста для голосов - групп
        var voices: MutableList<KaraokeVoice>
            get() = getVoicesFromString(KaraokeProperties.getString("voices"))
            set(value) { KaraokeProperties.set("flashColor", getStringFromVoices(value)) }

        // Ширина экрана в пикселах
        var frameWidthPx: Int
            get() = KaraokeProperties.getInt("frameWidthPx")
            set(value) { KaraokeProperties.set("frameWidthPx", value) }

        // Высота экрана в пикселах
        var frameHeightPx: Int
            get() = KaraokeProperties.getInt("frameHeightPx")
            set(value) { KaraokeProperties.set("frameHeightPx", value) }

        // Frames per seconds
        var frameFps: Double
            get() = KaraokeProperties.getDouble("frameFps")
            set(value) { KaraokeProperties.set("frameFps", value) }

        // HEADER

        // Фонт аккордов
        var chordsFont: MltText
            get() = getMltFontFromString(KaraokeProperties.getString("chordsFont"))
            set(value) { KaraokeProperties.set("chordsFont", value.setting()) }

        // Коэффициэнт размера шрифта аккорда относительно размера шрифта текста песни
        var chordsHeightCoefficient: Double
            get() = KaraokeProperties.getDouble("chordsHeightCoefficient")
            set(value) { KaraokeProperties.set("chordsHeightCoefficient", value) }

        // Коэффициэнт оффсета размера шрифта ноты относительно размера шрифта текста песни
        var chordsHeightOffsetCoefficient: Double
            get() = KaraokeProperties.getDouble("chordsHeightOffsetCoefficient")
            set(value) { KaraokeProperties.set("chordsHeightOffsetCoefficient", value) }

        // Фонт ноты
        var melodyNoteFont: MltText
            get() = getMltFontFromString(KaraokeProperties.getString("melodyNoteFont"))
            set(value) { KaraokeProperties.set("melodyNoteFont", value.setting()) }

        // Коэффициэнт размера шрифта ноты относительно размера шрифта текста песни
        var melodyNoteHeightCoefficient: Double
            get() = KaraokeProperties.getDouble("melodyNoteHeightCoefficient")
            set(value) { KaraokeProperties.set("melodyNoteHeightCoefficient", value) }

        // Коэффициэнт оффсета размера шрифта ноты относительно размера шрифта текста песни
        var melodyNoteHeightOffsetCoefficient: Double
            get() = KaraokeProperties.getDouble("melodyNoteHeightOffsetCoefficient")
            set(value) { KaraokeProperties.set("melodyNoteHeightOffsetCoefficient", value) }

        // Фонт номера лада в табулатуре
        var melodyTabsFont: MltText
            get() = getMltFontFromString(KaraokeProperties.getString("melodyTabsFont"))
            set(value) { KaraokeProperties.set("melodyTabsFont", value.setting()) }

        // Коэффициэнт размера шрифта номера лада в табулатуре относительно размера шрифта текста песни
        var melodyTabsHeightCoefficient: Double
            get() = KaraokeProperties.getDouble("melodyTabsHeightCoefficient")
            set(value) { KaraokeProperties.set("melodyTabsHeightCoefficient", value) }

        // Коэффициэнт оффсета размера шрифта номера лада в табулатуре относительно размера шрифта текста песни
        var melodyTabsHeightOffsetCoefficient: Double
            get() = KaraokeProperties.getDouble("melodyTabsHeightOffsetCoefficient")
            set(value) { KaraokeProperties.set("melodyTabsHeightOffsetCoefficient", value) }

        // Фонт открытой струны в табулатуре
        var melodyOpenStringFont: MltText
            get() = getMltFontFromString(KaraokeProperties.getString("melodyOpenStringFont"))
            set(value) { KaraokeProperties.set("melodyOpenStringFont", value.setting()) }

        // Коэффициэнт размера шрифта открытой струны в табулатуре относительно размера шрифта текста песни
        var melodyOpenStringHeightCoefficient: Double
            get() = KaraokeProperties.getDouble("melodyOpenStringHeightCoefficient")
            set(value) { KaraokeProperties.set("melodyOpenStringHeightCoefficient", value) }

        // Фонт октавы
        var melodyOctaveFont: MltText
            get() = getMltFontFromString(KaraokeProperties.getString("melodyOctaveFont"))
            set(value) { KaraokeProperties.set("melodyOctaveFont", value.setting()) }

        // Коэффициэнт размера шрифта октавы относительно размера шрифта текста песни
        var melodyOctaveHeightCoefficient: Double
            get() = KaraokeProperties.getDouble("melodyOctaveHeightCoefficient")
            set(value) { KaraokeProperties.set("melodyOctaveHeightCoefficient", value) }


        // Фонт каподастра
        var chordsCapoFont: MltText
            get() = getMltFontFromString(KaraokeProperties.getString("chordsCapoFont"))
            set(value) { KaraokeProperties.set("chordsCapoFont", value.setting()) }

        // Заголовок - Название песни - шрифт
        var headerSongnameFont: MltText
            get() = getMltFontFromString(KaraokeProperties.getString("headerSongnameFont"))
            set(value) { KaraokeProperties.set("headerSongnameFont", value.setting()) }

        // Максимальная позиция по X до которой должна быть надпись названия песни, чтобы не перекрывать логотип
        var headerSongnameMaxX: Long
            get() = KaraokeProperties.getLong("headerSongnameMaxX")
            set(value) { KaraokeProperties.set("headerSongnameMaxX", value) }

        // Заголовок - Автор - шрифт
        var headerAuthorFont: MltText
            get() = getMltFontFromString(KaraokeProperties.getString("headerAuthorFont"))
            set(value) { KaraokeProperties.set("headerAuthorFont", value.setting()) }

        // Заголовок - Автор (название) - шрифт
        var headerAuthorNameFont: MltText
            get() = getMltFontFromString(KaraokeProperties.getString("headerAuthorNameFont"))
            set(value) { KaraokeProperties.set("headerAuthorNameFont", value.setting()) }

        // Заголовок - Автор (название) - текст
        var headerAuthorName: String
            get() = KaraokeProperties.getString("headerAuthorName")
            set(value) { KaraokeProperties.set("headerAuthorName", value) }

        // Заголовок - Название альбома - шрифт
        var headerAlbumFont: MltText
            get() = getMltFontFromString(KaraokeProperties.getString("headerAlbumFont"))
            set(value) { KaraokeProperties.set("headerAlbumFont", value.setting()) }

        // Заголовок - Название альбома (название) - шрифт
        var headerAlbumNameFont: MltText
            get() = getMltFontFromString(KaraokeProperties.getString("headerAlbumNameFont"))
            set(value) { KaraokeProperties.set("headerAlbumNameFont", value.setting()) }

        // Заголовок - Название альбома (название) - текст
        var headerAlbumName: String
            get() = KaraokeProperties.getString("headerAlbumName")
            set(value) { KaraokeProperties.set("headerAlbumName", value) }

        // Заголовок - Тональность - шрифт
        var headerToneFont: MltText
            get() = getMltFontFromString(KaraokeProperties.getString("headerToneFont"))
            set(value) { KaraokeProperties.set("headerToneFont", value.setting()) }

        // Заголовок - Тональность (название) - шрифт
        var headerToneNameFont: MltText
            get() = getMltFontFromString(KaraokeProperties.getString("headerToneNameFont"))
            set(value) { KaraokeProperties.set("headerToneNameFont", value.setting()) }

        // Заголовок - Тональность (название) - текст
        var headerToneName: String
            get() = KaraokeProperties.getString("headerToneName")
            set(value) { KaraokeProperties.set("headerToneName", value) }

        // Заголовок - Темп - шрифт
        var headerBpmFont: MltText
            get() = getMltFontFromString(KaraokeProperties.getString("headerBpmFont"))
            set(value) { KaraokeProperties.set("headerBpmFont", value.setting()) }

        // Заголовок - Темп (название) - шрифт
        var headerBpmNameFont: MltText
            get() = getMltFontFromString(KaraokeProperties.getString("headerBpmNameFont"))
            set(value) { KaraokeProperties.set("headerBpmNameFont", value.setting()) }

        // Заголовок - Темп (название) - текст
        var headerBpmName: String
            get() = KaraokeProperties.getString("headerBpmName")
            set(value) { KaraokeProperties.set("headerBpmName", value) }

        // Прогрессометр - шрифт
        @Suppress("unused")
        var progressFont: MltText
            get() = getMltFontFromString(KaraokeProperties.getString("progressFont"))
            set(value) { KaraokeProperties.set("progressFont", value.setting()) }

        // Прогрессометр - указатель
        @Suppress("unused")
        var progressSymbol: String
            get() = KaraokeProperties.getString("progressSymbol")
            set(value) { KaraokeProperties.set("progressSymbol", value) }

        // Бусти - шрифт
        @Suppress("unused")
        var boostyFont: MltText
            get() = getMltFontFromString(KaraokeProperties.getString("boostyFont"))
            set(value) { KaraokeProperties.set("boostyFont", value.setting()) }

        // Текст Бусти
        @Suppress("unused")
        var boostyText: String
            get() = KaraokeProperties.getString("boostyText")
            set(value) { KaraokeProperties.set("boostyText", value) }

        // Водяной знак - шрифт
        var watermarkFont: MltText
            get() = getMltFontFromString(KaraokeProperties.getString("watermarkFont"))
            set(value) { KaraokeProperties.set("watermarkFont", value.setting()) }

        // Текст водяного знака
        var watermarkText: String
            get() = KaraokeProperties.getString("watermarkText")
            set(value) { KaraokeProperties.set("watermarkText", value) }

        // Заставка - Название песни - шрифт
        var splashstartSongNameFont: MltText
            get() = getMltFontFromString(KaraokeProperties.getString("splashstartSongNameFont"))
            set(value) { KaraokeProperties.set("splashstartSongNameFont", value.setting()) }

        // Заставка - Версия песни - шрифт
        var splashstartSongVersionFont: MltText
            get() = getMltFontFromString(KaraokeProperties.getString("splashstartSongVersionFont"))
            set(value) { KaraokeProperties.set("splashstartSongVersionFont", value.setting()) }

        // Заставка - Комментарий - шрифт
        var splashstartCommentFont: MltText
            get() = getMltFontFromString(KaraokeProperties.getString("splashstartCommentFont"))
            set(value) { KaraokeProperties.set("splashstartCommentFont", value.setting()) }

        // Заставка - Подпись аккордов - шрифт
        var splashstartChordDescriptionFont: MltText
            get() = getMltFontFromString(KaraokeProperties.getString("splashstartChordDescriptionFont"))
            set(value) { KaraokeProperties.set("splashstartChordDescriptionFont", value.setting()) }

        // Время в миллисекундах. Если субтитр длится дольше этого времени - закраска увеличивается
        var shortSubtitleMs: Long
            get() = KaraokeProperties.getLong("shortSubtitleMs")
            set(value) { KaraokeProperties.set("shortSubtitleMs", value) }

        @Suppress("unused")
        var chordLayoutW: Int
            get() = KaraokeProperties.getInt("chordLayoutW")
            set(value) { KaraokeProperties.set("chordLayoutW", value) }

        @Suppress("unused")
        var chordLayoutH: Int
            get() = KaraokeProperties.getInt("chordLayoutH")
            set(value) { KaraokeProperties.set("chordLayoutH", value) }

        @Suppress("unused")
        var shortLineMs: Long
            get() = KaraokeProperties.getLong("shortLineMs")
            set(value) { KaraokeProperties.set("shortLineMs", value) }

        @Suppress("unused")
        var maxCountChordsInFingerboard: Int
            get() = KaraokeProperties.getInt("maxCountChordsInFingerboard")
            set(value) { KaraokeProperties.set("maxCountChordsInFingerboard", value) }

        @Suppress("unused")
        var shortLineFontScaleCoeff: Double
            get() = KaraokeProperties.getDouble("shortLineFontScaleCoeff")
            set(value) { KaraokeProperties.set("shortLineFontScaleCoeff", value) }


        // Табулатура аккорда - Название аккорда - фонт
        var chordLayoutChordNameMltText: MltText
            get() = getMltFontFromString(KaraokeProperties.getString("chordLayoutChordNameMltText"))
            set(value) { KaraokeProperties.set("chordLayoutChordNameMltText", value.setting()) }

        // Табулатура аккорда - Номер струны - фонт
        var chordLayoutFretsNumbersMltText: MltText
            get() = getMltFontFromString(KaraokeProperties.getString("chordLayoutFretsNumbersMltText"))
            set(value) { KaraokeProperties.set("chordLayoutFretsNumbersMltText", value.setting()) }

        // Табулатура аккорда - Frets - Shape
        var chordLayoutFretsRectangleMltShape: MltShape
            get() = getMltShapeFromString(KaraokeProperties.getString("chordLayoutFretsRectangleMltShape"))
            set(value) { KaraokeProperties.set("chordLayoutFretsRectangleMltShape", value.setting()) }

        // Табулатура аккорда - Nuts - Shape
        var chordLayoutNutsRectangleMltShape: MltShape
            get() = getMltShapeFromString(KaraokeProperties.getString("chordLayoutNutsRectangleMltShape"))
            set(value) { KaraokeProperties.set("chordLayoutNutsRectangleMltShape", value.setting()) }

        // Табулатура аккорда - Capo - Shape
        var chordLayoutCapoRectangleMltShape: MltShape
            get() = getMltShapeFromString(KaraokeProperties.getString("chordLayoutCapoRectangleMltShape"))
            set(value) { KaraokeProperties.set("chordLayoutCapoRectangleMltShape", value.setting()) }

        // Табулатура аккорда - Background - Shape
        var chordLayoutBackgroundRectangleMltShape: MltShape
            get() = getMltShapeFromString(KaraokeProperties.getString("chordLayoutBackgroundRectangleMltShape"))
            set(value) { KaraokeProperties.set("chordLayoutBackgroundRectangleMltShape", value.setting()) }

        // Табулатура аккорда - Muted - Shape
        var chordLayoutMutedRectangleMltShape: MltShape
            get() = getMltShapeFromString(KaraokeProperties.getString("chordLayoutMutedRectangleMltShape"))
            set(value) { KaraokeProperties.set("chordLayoutMutedRectangleMltShape", value.setting()) }

        // Табулатура аккорда - Finger - Shape
        var chordLayoutFingerCircleMltShape: MltShape
            get() = getMltShapeFromString(KaraokeProperties.getString("chordLayoutFingerCircleMltShape"))
            set(value) { KaraokeProperties.set("chordLayoutFingerCircleMltShape", value.setting()) }

    }
}