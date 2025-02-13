package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.mlt.MltObjectType
import com.svoemesto.karaokeapp.mlt.MltShape
import com.svoemesto.karaokeapp.mlt.MltText
import com.svoemesto.karaokeapp.mlt.setting
import com.svoemesto.karaokeapp.textfilehistory.HistoryMap
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.awt.Color
import java.awt.Font
import java.io.ByteArrayInputStream
import java.io.File
import java.util.*

const val PATH_TO_KARAOKE_PROPERTIES_FILE = "/home/nsa/Documents/Караоке/Karaoke.properties"
class KaraokeProperties {
    companion object {

        fun pathToFile(): String = PATH_TO_KARAOKE_PROPERTIES_FILE
        fun loadPropertiesMap() {
            val file = File(pathToFile())
            if (file.exists()) {
                try {
                    val list =
                        File(pathToFile())
                            .readText()
                            .split("\n")
                            .filter { it != "" }
                            .map {
                                Json.decodeFromStream(
                                    KaraokePropertySerializable.serializer(),
                                    ByteArrayInputStream(Base64.getDecoder().decode(it))
                                )
                            }
                    list.forEach { kps ->
                        karaokePropertiesMap[kps.key] = kps.value()
                    }
                } catch (_: Exception) {

                }
            } else {
                val list = listKaraokeProperties.map {
                    KaraokePropertySerializable.create(
                        key = it.key,
                        value = it.defaultValue
                    )
                }
                list.forEach { kps ->
                    karaokePropertiesMap[kps.key] = kps.value()
                }
                savePropertiesMap()
            }

        }

        fun savePropertiesMap() {

            File(pathToFile()).writeText(
                karaokePropertiesMap.map { (key, value) ->
                    Base64.getEncoder().encodeToString(
                        Json.encodeToString(
                            KaraokePropertySerializable.serializer(),
                            KaraokePropertySerializable.create(
                                key = key,
                                value = value
                            )
                        ).toByteArray()
                    )
            }.joinToString("\n"))

        }

        fun getString(key: String): String = get(key)?.let { it as String } ?: ""
        fun getLong(key: String): Long = get(key)?.let { it as Long } ?: 0L
        fun getInt(key: String): Int = get(key)?.let { it as Int } ?: 0
        fun getDouble(key: String): Double = get(key)?.let { it as Double } ?: 0.0
        fun getBoolean(key: String): Boolean = get(key)?.let { it as Boolean } ?: false

        fun get(key: String): Any? {
            if (karaokePropertiesMap.isEmpty()) loadPropertiesMap()
            return if (karaokePropertiesMap.containsKey(key = key)) {
                karaokePropertiesMap[key]
            } else {
                listKaraokeProperties.firstOrNull { it.key == key }?.defaultValue
            }
        }

        fun getDTO(key: String): KaraokePropertyDTO {
            val value = get(key) ?: return KaraokePropertyDTO()
            val defaultValue = listKaraokeProperties.firstOrNull { it.key == key }?.defaultValue ?: return KaraokePropertyDTO()
            val type: String = when(defaultValue) {
                is Long -> "Long"
                is Int -> "Int"
                is Double -> "Double"
                is Boolean -> "Boolean"
                else -> "String"
            }
            return KaraokePropertyDTO(
                key = key,
                value = value.toString(),
                defaultValue = defaultValue.toString(),
                description = listKaraokeProperties.firstOrNull { it.key == key }?.description ?: "",
                type = type
            )
        }

        fun getDTOs(): List<KaraokePropertyDTO> = listKaraokeProperties.map { getDTO(it.key) }

        fun set(key: String, value: Any) {
            val valueInMap = get(key)
            if (valueInMap !== null && valueInMap !== value) {
                karaokePropertiesMap[key] = value
                savePropertiesMap()
            }
        }

        fun setFromString(key: String, stringValue: String) {
            val defaultValue = listKaraokeProperties.firstOrNull { it.key == key }?.defaultValue
            if  (defaultValue !== null) {
                val value: Any = when(defaultValue) {
                    is Long -> stringValue.toLong()
                    is Int -> stringValue.toInt()
                    is Double -> stringValue.toDouble()
                    is Boolean -> stringValue.toBoolean()
                    else -> stringValue
                }
                set(key, value)
            }
        }

        fun setDefault(key: String) {
            val defaultValue = listKaraokeProperties.firstOrNull { it.key == key }?.defaultValue
            if (defaultValue !== null) set(key, defaultValue)
        }

    }
}


val karaokePropertiesMap: MutableMap<String, Any> = mutableMapOf()

val listKaraokeProperties = listOf(
    KaraokeProperty(key = "autoSave", defaultValue = true, description = "Автосохранение"),
    KaraokeProperty(key = "autoSaveDelayMs", defaultValue = 1000L, description = "Время (в миллисекундах) задержки перед автосохранением"),
    KaraokeProperty(key = "backgroundFolderPath", defaultValue = "/home/nsa/Documents/SpaceBox4096", description = "Путь к папке с фонами"),
    KaraokeProperty(key = "createLogotype", defaultValue = true, description = "Создавать логотип"),
    KaraokeProperty(key = "createMicrophone", defaultValue = false, description = "Создавать микрофон"),
    KaraokeProperty(key = "createHeader", defaultValue = true, description = "Создавать заголовок"),
    KaraokeProperty(key = "createAudioDrums", defaultValue = true, description = "Создавать аудио ударных"),
    KaraokeProperty(key = "createAudioBass", defaultValue = true, description = "Создавать аудио баса"),
    KaraokeProperty(key = "createAudioVocal", defaultValue = true, description = "Создавать аудио вокала"),
    KaraokeProperty(key = "createAudioMusic", defaultValue = true, description = "Создавать аудио музыки"),
    KaraokeProperty(key = "createAudioSong", defaultValue = true, description = "Создавать аудио песни"),
    KaraokeProperty(key = "createFader", defaultValue = true, description = "Создавать фэйдер"),
    KaraokeProperty(key = "createFillsSongtext", defaultValue = true, description = "Создавать заливки текста"),
    KaraokeProperty(key = "createFillsChords", defaultValue = true, description = "Создавать заливки аккорда"),
    KaraokeProperty(key = "paintHorizon", defaultValue = true, description = "Раскрашивать горизонт"),
    KaraokeProperty(key = "createHorizon", defaultValue = true, description = "Создавать горизонт"),
    KaraokeProperty(key = "createProgress", defaultValue = true, description = "Создавать прогрессометр"),
    KaraokeProperty(key = "createBackground", defaultValue = true, description = "Создавать фон"),
    KaraokeProperty(key = "createWatermark", defaultValue = true, description = "Создавать водяной знак"),
    KaraokeProperty(key = "createCounters", defaultValue = true, description = "Создавать счётчики"),
    KaraokeProperty(key = "createBeats", defaultValue = true, description = "Создавать такты"),
    KaraokeProperty(key = "createSongtext", defaultValue = true, description = "Создавать текст песни"),
    KaraokeProperty(key = "timeOffsetStartFillingLineMs", defaultValue = 170L, description = "Время (в миллисекундах) задержки звука от начала анимации"),
    KaraokeProperty(key = "timeOffsetBluetoothSpeakerMs", defaultValue = 300L, description = "Время (в миллисекундах) задержки звука от начала анимации для Bluetooth"),
    KaraokeProperty(key = "timeSplashScreenLengthMs", defaultValue = 5000L, description = "Время показа в миллисекундах начальной заставки"),
    KaraokeProperty(key = "timeBoostyLengthMs", defaultValue = 3000L, description = "Время показа в миллисекундах boosty"),
    KaraokeProperty(key = "transferMinimumMsBetweenLinesToScroll", defaultValue = 200L, description = "Минимальное время (в миллисекундах) между линиями, меньше которого заливка последнего титра будет во время смещения линии"),
    KaraokeProperty(key = "songtextStartOffsetXpx", defaultValue = 20L, description = "Отступ начала заливки от первого символа в строке (в пикселах)"),
    KaraokeProperty(key = "songtextStartPositionXpercent", defaultValue = 5.0, description = "Начальная позиция по Х текста песни на экране (в % от ширины экрана)"),
    KaraokeProperty(key = "horizonOffsetPx", defaultValue = -7L, description = "Смещение горизонта (в пикселах)"),
    KaraokeProperty(
        key = "countersColors",
        defaultValue = listOf(
            Color(0,255,0,255),
            Color(255,255,0,255),
            Color(255,255,0,255),
            Color(255,0,0,255),
            Color(255,0,0,255)
        ).setting(),
        description = "Цвета каунтеров"
    ),
    KaraokeProperty(
        key = "horizonColors",
        defaultValue = listOf(
            Color(255,255,255,255),
            Color(255,255,0,255),
            Color(85,255,255,255)
        ).setting(),
        description = "Цвета горизонта для групп"
    ),
    KaraokeProperty(key = "horizonColor", defaultValue = Color(0,255,0,255).setting(), description = "Цвет горизонта"),
    KaraokeProperty(key = "flashColor", defaultValue = Color(255,0,0,255).setting(), description = "Цвет флеша горизонта"),
    KaraokeProperty(
        key = "voices",
        defaultValue = "" +
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
                "",
        description = "Настройки текста для голосов - групп"
    ),
    KaraokeProperty(key = "frameWidthPx", defaultValue = 1920, description = "Ширина экрана в пикселах"),
    KaraokeProperty(key = "frameHeightPx", defaultValue = 1080, description = "Высота экрана в пикселах"),
    KaraokeProperty(key = "frameFps", defaultValue = 60.0, description = "Frames per seconds"),
    KaraokeProperty(
        key = "chordsFont",
        defaultValue = MltText(
            font = Font("Fira Sans Extra Condensed Medium", 0, 80),
            shapeColor = Color(255,127,127,255),
            shapeOutlineColor = Color(0,0,0,255),
            shapeOutline = 0,
            fontUnderline = 0
        ).setting(),
        description = "Фонт аккордов"
    ),
    KaraokeProperty(key = "chordsHeightCoefficient", defaultValue = 0.72, description = "Коэффициэнт размера шрифта аккорда относительно размера шрифта текста песни"),
    KaraokeProperty(
        key = "chordsCapoFont",
        defaultValue = MltText(
            font = Font("Fira Sans Extra Condensed Medium", 0, 35),
            shapeColor = Color(255,127,127,255),
            shapeOutlineColor = Color(255,127,127,255),
            shapeOutline = 0,
            fontUnderline = 0
        ).setting(),
        description = "Фонт каподастра"
    ),
    KaraokeProperty(
        key = "headerSongnameFont",
        defaultValue = MltText(
            font = Font(MAIN_FONT_NAME, 0, 80),
            shapeColor = Color(255,255,127,255),
            shapeOutlineColor = Color(0,0,0,255),
            shapeOutline = 0,
            fontUnderline = 0
        ).setting(),
        description = "Заголовок - Название песни - шрифт"
    ),
    KaraokeProperty(key = "headerSongnameMaxX", defaultValue = 1200L, description = "Максимальная позиция по X до которой должна быть надпись названия песни, чтобы не перекрывать логотип"),
    KaraokeProperty(
        key = "headerAuthorFont",
        defaultValue = MltText(
            font = Font(MAIN_FONT_NAME, 0, 30),
            shapeColor = Color(255,255,127,255),
            shapeOutlineColor = Color(0,0,0,255),
            shapeOutline = 0,
            fontUnderline = 0
        ).setting(),
        description = "Заголовок - Автор - шрифт"
    ),
    KaraokeProperty(
        key = "headerAuthorNameFont",
        defaultValue = MltText(
            font = Font(MAIN_FONT_NAME, 0, 30),
            shapeColor = Color(85,255,255,255),
            shapeOutlineColor = Color(0,0,0,255),
            shapeOutline = 0,
            fontUnderline = 0
        ).setting(),
        description = "Заголовок - Автор (название) - шрифт"
    ),
    KaraokeProperty(key = "headerAuthorName", defaultValue = "Исполнитель: ", description = "Заголовок - Автор (название) - текст"),
    KaraokeProperty(
        key = "headerAlbumFont",
        defaultValue = MltText(
            font = Font(MAIN_FONT_NAME, 0, 30),
            shapeColor = Color(255,255,127,255),
            shapeOutlineColor = Color(0,0,0,255),
            shapeOutline = 0,
            fontUnderline = 0
        ).setting(),
        description = "Заголовок - Название альбома - шрифт"
    ),
    KaraokeProperty(
        key = "headerAlbumNameFont",
        defaultValue = MltText(
            font = Font(MAIN_FONT_NAME, 0, 30),
            shapeColor = Color(85,255,255,255),
            shapeOutlineColor = Color(0,0,0,255),
            shapeOutline = 0,
            fontUnderline = 0
        ).setting(),
        description = "Заголовок - Название альбома (название) - шрифт"
    ),
    KaraokeProperty(key = "headerAlbumName", defaultValue = "Альбом: ", description = "Заголовок - Название альбома (название) - текст"),
    KaraokeProperty(
        key = "headerToneFont",
        defaultValue = MltText(
            font = Font(MAIN_FONT_NAME, 0, 30),
            shapeColor = Color(255,255,127,255),
            shapeOutlineColor = Color(0,0,0,255),
            shapeOutline = 0,
            fontUnderline = 0
        ).setting(),
        description = "Заголовок - Тональность - шрифт"
    ),
    KaraokeProperty(
        key = "headerToneNameFont",
        defaultValue = MltText(
            font = Font(MAIN_FONT_NAME, 0, 30),
            shapeColor = Color(85,255,255,255),
            shapeOutlineColor = Color(0,0,0,255),
            shapeOutline = 0,
            fontUnderline = 0
        ).setting(),
        description = "Заголовок - Тональность (название) - шрифт"
    ),
    KaraokeProperty(key = "headerToneName", defaultValue = "Тональность: ", description = "Заголовок - Тональность (название) - текст"),
    KaraokeProperty(
        key = "headerBpmFont",
        defaultValue = MltText(
            font = Font(MAIN_FONT_NAME, 0, 30),
            shapeColor = Color(255,255,127,255),
            shapeOutlineColor = Color(0,0,0,255),
            shapeOutline = 0,
            fontUnderline = 0
        ).setting(),
        description = "Заголовок - Темп - шрифт"
    ),
    KaraokeProperty(
        key = "headerBpmNameFont",
        defaultValue = MltText(
            font = Font(MAIN_FONT_NAME, 0, 30),
            shapeColor = Color(85,255,255,255),
            shapeOutlineColor = Color(0,0,0,255),
            shapeOutline = 0,
            fontUnderline = 0
        ).setting(),
        description = "Заголовок - Темп (название) - шрифт"
    ),
    KaraokeProperty(key = "headerBpmName", defaultValue = "Темп: ", description = "Заголовок - Темп (название) - текст"),
    KaraokeProperty(
        key = "progressFont",
        defaultValue = MltText(
            font = Font("Tahoma", 0, 20),
            shapeColor = Color(255,255,255,255),
            shapeOutlineColor = Color(0,0,0,255),
            shapeOutline = 0,
            fontUnderline = 0
        ).setting(),
        description = "Прогрессометр - шрифт"
    ),
    KaraokeProperty(key = "progressSymbol", defaultValue = "▲", description = "Прогрессометр - указатель"),
    KaraokeProperty(
        key = "boostyFont",
        defaultValue = MltText(
            font = Font(MAIN_FONT_NAME, 0, 100),
            shapeColor = Color(255,255,255,255),
            shapeOutlineColor = Color(0,0,0,255),
            shapeOutline = 0,
            fontUnderline = 0
        ).setting(),
        description = "Бусти - шрифт"
    ),
    KaraokeProperty(key = "boostyText", defaultValue = "Поддержи создание караоке\nна https://boosty.to/svoemesto\n\nГруппа ВКонтакте:\nhttps://vk.com/svoemestokaraoke\n\nВсе ссылки - в описании.", description = "Текст Бусти"),
    KaraokeProperty(
        key = "watermarkFont",
        defaultValue = MltText(
            font = Font(MAIN_FONT_NAME, 0, 10),
            shapeColor = Color(255,255,255,127),
            shapeOutlineColor = Color(0,0,0,255),
            shapeOutline = 0,
            fontUnderline = 0
        ).setting(),
        description = "Водяной знак - шрифт"
    ),
    KaraokeProperty(key = "watermarkText", defaultValue = "https://github.com/svoemesto/Karaoke", description = "Текст водяного знака"),
    KaraokeProperty(
        key = "splashstartSongNameFont",
        defaultValue = MltText(
            font = Font(MAIN_FONT_NAME, 0, 10),
            shapeColor = Color(255,255,127,255),
            shapeOutlineColor = Color(0,0,0,255),
            shapeOutline = 0,
            fontUnderline = 0
        ).setting(),
        description = "Заставка - Название песни - шрифт"
    ),
    KaraokeProperty(
        key = "splashstartSongVersionFont",
        defaultValue = MltText(
            font = Font(MAIN_FONT_NAME, 0, 150),
            shapeColor = Color(85,255,255,255),
            shapeOutlineColor = Color(0,0,0,255),
            shapeOutline = 0,
            fontUnderline = 0
        ).setting(),
        description = "Заставка - Версия песни - шрифт"
    ),
    KaraokeProperty(
        key = "splashstartCommentFont",
        defaultValue = MltText(
            font = Font(MAIN_FONT_NAME, 0, 60),
            shapeColor = Color(85,255,255,255),
            shapeOutlineColor = Color(0,0,0,255),
            shapeOutline = 0,
            fontUnderline = 0
        ).setting(),
        description = "Заставка - Комментарий - шрифт"
    ),
    KaraokeProperty(
        key = "splashstartChordDescriptionFont",
        defaultValue = MltText(
            font = Font("Fira Sans Extra Condensed Medium", 0, 40),
            shapeColor = Color(255,127,127,255),
            shapeOutlineColor = Color(0,0,0,255),
            shapeOutline = 0,
            fontUnderline = 0
        ).setting(),
        description = "Заставка - Подпись аккордов - шрифт"
    ),
    KaraokeProperty(key = "shortSubtitleMs", defaultValue = 750L, description = "Время в миллисекундах. Если субтитр длится дольше этого времени - закраска увеличивается"),
    KaraokeProperty(key = "chordLayoutW", defaultValue = 800, description = "Ширина табулатуры аккорда в пикселях"),
    KaraokeProperty(key = "chordLayoutH", defaultValue = 800, description = "Высота табулатуры аккорда  в пикселях"),
    KaraokeProperty(key = "shortLineMs", defaultValue = 200L, description = "Время в миллисекундах. Короткая линия."),
    KaraokeProperty(key = "maxCountChordsInFingerboard", defaultValue = 100, description = "Максимально кол-во аккордов на доске"),
    KaraokeProperty(key = "shortLineFontScaleCoeff", defaultValue = 0.75, description = "Коэффициент короткой линии"),
    KaraokeProperty(
        key = "chordLayoutChordNameMltText",
        defaultValue = MltText(
            font = Font(MAIN_FONT_NAME, 0, 10),
            shapeColor = Color(255,255,127,255),
            shapeOutlineColor = Color(0,0,0,255),
            shapeOutline = 0,
            fontUnderline = 0
        ).setting(),
        description = "Табулатура аккорда - Название аккорда - фонт"
    ),
    KaraokeProperty(
        key = "chordLayoutFretsNumbersMltText",
        defaultValue = MltText(
            font = Font(MAIN_FONT_NAME, 0, 10),
            shapeColor = Color(127,127,127,255),
            shapeOutlineColor = Color(0,0,0,255),
            shapeOutline = 0,
            fontUnderline = 0
        ).setting(),
        description = "Табулатура аккорда - Номер струны - фонт"
    ),
    KaraokeProperty(
        key = "chordLayoutFretsRectangleMltShape",
        defaultValue = MltShape(
            type = MltObjectType.RECTANGLE,
            shapeColor = Color(255,255,255,127),
            shapeOutlineColor = Color(255,255,255,255),
            shapeOutline = 2
        ).setting(),
        description = "Табулатура аккорда - Frets - Shape"
    ),
    KaraokeProperty(
        key = "chordLayoutNutsRectangleMltShape",
        defaultValue = MltShape(
            type = MltObjectType.RECTANGLE,
            shapeColor = Color(255,255,255,255),
            shapeOutlineColor = Color(255,255,255,255),
            shapeOutline = 2
        ).setting(),
        description = "Табулатура аккорда - Nuts - Shape"
    ),
    KaraokeProperty(
        key = "chordLayoutCapoRectangleMltShape",
        defaultValue = MltShape(
            type = MltObjectType.RECTANGLE,
            shapeColor = Color(255,0,0,255),
            shapeOutlineColor = Color(255,0,0,255),
            shapeOutline = 2
        ).setting(),
        description = "Табулатура аккорда - Capo - Shape"
    ),
    KaraokeProperty(
        key = "chordLayoutBackgroundRectangleMltShape",
        defaultValue = MltShape(
            type = MltObjectType.RECTANGLE,
            shapeColor = Color(0,0,0,255),
            shapeOutlineColor = Color(255,255,255,20),
            shapeOutline = 2
        ).setting(),
        description = "Табулатура аккорда - Background - Shape"
    ),
    KaraokeProperty(
        key = "chordLayoutMutedRectangleMltShape",
        defaultValue = MltShape(
            type = MltObjectType.RECTANGLE,
            shapeColor = Color(255,0,0,200),
            shapeOutlineColor = Color(255,0,0,0),
            shapeOutline = 0
        ).setting(),
        description = "Табулатура аккорда - Muted - Shape"
    ),
    KaraokeProperty(
        key = "chordLayoutFingerCircleMltShape",
        defaultValue = MltShape(
            type = MltObjectType.CIRCLE,
            shapeColor = Color(255,0,0,255),
            shapeOutlineColor = Color(255,255,255,255),
            shapeOutline = 2
        ).setting(),
        description = "Табулатура аккорда - Finger - Shape"
    ),
)