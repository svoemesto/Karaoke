package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.mlt.MltObjectType
import com.svoemesto.karaokeapp.mlt.MltShape
import com.svoemesto.karaokeapp.mlt.MltText
import com.svoemesto.karaokeapp.mlt.setting
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.awt.Color
import java.awt.Font
import java.io.ByteArrayInputStream
import java.io.File
import java.util.*

const val PATH_TO_KARAOKE_PROPERTIES_FILE = "/sm-karaoke/system/Karaoke.properties"
class KaraokeProperties {
    companion object {

        fun pathToFile(): String = PATH_TO_KARAOKE_PROPERTIES_FILE
        @OptIn(ExperimentalSerializationApi::class)
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
            runCommand(listOf("chmod", "666", pathToFile()))
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

        fun types() : List<String> {
            return listOf(
                "Long",
                "Int",
                "Double",
                "Boolean",
                "String"
            )
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

        fun loadList(args: Map<String, String> = emptyMap()): List<KaraokePropertyDTO> {
            var lst = getDTOs()
            if (args.containsKey("key")) lst = lst.filter { it.key.contains(args["key"]!!) }
            if (args.containsKey("value")) lst = lst.filter { it.value.contains(args["value"]!!) }
            if (args.containsKey("default_value")) lst = lst.filter { it.defaultValue.contains(args["default_value"]!!) }
            if (args.containsKey("description")) lst = lst.filter { it.description.contains(args["description"]!!) }
            if (args.containsKey("type")) lst = lst.filter { it.type.contains(args["type"]!!) }
            return lst
        }

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
    KaraokeProperty(key = "checkSearchAsync", defaultValue = false, description = "Мониторинг SearchAsync"),
    KaraokeProperty(key = "requestAsyncUrl", defaultValue = "https://searchapi.api.cloud.yandex.net/v2/web/searchAsync", description = "URL асинхронного запроса поиска"),
    KaraokeProperty(key = "requestAsyncOperationsUrlPrefix", defaultValue = "https://operation.api.cloud.yandex.net/operations/", description = "Префикс URL-а запроса проверки готовности асинхронного запроса"),
    KaraokeProperty(key = "requestSyncUrl", defaultValue = "https://searchapi.api.cloud.yandex.net/v2/web/search", description = "URL синхронного запроса поиска"),
    KaraokeProperty(key = "yandexCloudFolderId", defaultValue = "", description = "Yandex Cloud Folder ID"),
    KaraokeProperty(key = "iamTokenFilePath", defaultValue = "/sm-karaoke/system/yandex/iam_token.txt", description = "Путь к файлу IAM Token"),
    KaraokeProperty(key = "requestIamToken", defaultValue = "", description = "IAM Token запроса поиска"),
    KaraokeProperty(key = "requestIamTokenLastTimeMs", defaultValue = 0L, description = "Время последнего получения IAM Token-а"),
    KaraokeProperty(key = "requestIamTokenTimeoutMs", defaultValue = 3_600_000L, description = "Таймаут для получения IAM Token-а"),
    KaraokeProperty(key = "requestResultTimeoutMs", defaultValue = 300_000L, description = "Таймаут для проверки готовности асинхронного запроса"),

    KaraokeProperty(key = "requestNewSongLastTimeMs", defaultValue = 0L, description = "Время последнего запроса поиска новой песни (миллисекунды)"),
    KaraokeProperty(key = "requestNewSongLastTimeCode", defaultValue = "", description = "Время последнего запроса поиска новой песни"),
    KaraokeProperty(key = "requestNewSongLastSuccessTimeMs", defaultValue = 0L, description = "Время последнего удачного запроса поиска новой песни (миллисекунды)"),
    KaraokeProperty(key = "requestNewSongLastSuccessTimeCode", defaultValue = "", description = "Время последнего удачного запроса поиска новой песни"),
    KaraokeProperty(key = "requestNewSongLastSuccessAuthor", defaultValue = "", description = "Автор последнего удачного запроса поиска новой песни"),
    KaraokeProperty(key = "requestNewSongLastAuthor", defaultValue = "", description = "Автор последнего запроса поиска новой песни"),
    KaraokeProperty(key = "requestNewSongTimeoutIncreaseMs", defaultValue = 600_000L, description = "На сколько увеличивать задержку между запросами в случае неудачи"),
    KaraokeProperty(key = "requestNewSongTimeoutMs", defaultValue = 600_000L, description = "Задержка между запросами поиска новых песен автора (миллисекунды)"),
    KaraokeProperty(key = "requestNewSongTimeoutMin", defaultValue = 10L, description = "Задержка между запросами поиска новых песен автора (минуты)"),
    KaraokeProperty(key = "autoSave", defaultValue = true, description = "Автосохранение"),
    KaraokeProperty(key = "showChordsIfEmpty", defaultValue = false, description = "Показывать кнопки версии аккордов, даже если версии нет"),
    KaraokeProperty(key = "showMelodyIfEmpty", defaultValue = false, description = "Показывать кнопки версии мелодии, даже если версии нет"),
    KaraokeProperty(key = "autoUpdateRemoteSettings", defaultValue = false, description = "Автообновление записи в удаленной БД при сохранении"),
    KaraokeProperty(key = "monitoringRemoteSettingsSync", defaultValue = false, description = "Мониторинг sync-записей в удаленной БД"),
    KaraokeProperty(key = "checkLastAlbum", defaultValue = false, description = "Искать альбомы авторов в Яндекс.Музыке"),
    KaraokeProperty(key = "vpnHomeCountry", defaultValue = "RU", description = "Код страны сервера без ВПН (ISO 3166-1 alpha-2, например RU или DE). Используется для определения активности ВПН."),
    KaraokeProperty(key = "autoSaveDelayMs", defaultValue = 1000L, description = "Время (в миллисекундах) задержки перед автосохранением"),
    KaraokeProperty(key = "allowUpdateRemote", defaultValue = false, description = "Разрешить обновлять REMOTE"),
    KaraokeProperty(key = "allowUpdateLocal", defaultValue = false, description = "Разрешить обновлять LOCAL"),
    KaraokeProperty(key = "allowAddSync", defaultValue = false, description = "Разрешить добавлять SYNC"),
    // Разрешения универсальной синхронизации (webvue3 "Синхронизация", /api/sync/*) — по одному
    // флагу на (сущность SyncRegistry × направление push/pull × операция insert/update/delete/move).
    // Раздельно от allowUpdateRemote/allowUpdateLocal выше (те управляют только старыми 6 кнопками на
    // Home и автопушем Settings при сохранении). insert/update/delete — операции над ЦЕЛЬЮ; move —
    // режим «перемещение»: после переноса удалить перенесённые строки из ИСТОЧНИКА (см. Utils.kt).
    // Дефолт всех — false (синхронизация выключена), КРОМЕ events/pull (перелив статистики с сервера
    // на LOCAL с очисткой сервера): insert/update/move = true, delete = false.
    KaraokeProperty(key = "sync_settings_push_insert_allowed", defaultValue = false, description = "Синхронизация: Настройки песен — push, Добавление"),
    KaraokeProperty(key = "sync_settings_push_update_allowed", defaultValue = false, description = "Синхронизация: Настройки песен — push, Изменение"),
    KaraokeProperty(key = "sync_settings_push_delete_allowed", defaultValue = false, description = "Синхронизация: Настройки песен — push, Удаление"),
    KaraokeProperty(key = "sync_settings_push_move_allowed", defaultValue = false, description = "Синхронизация: Настройки песен — push, Перемещение"),
    KaraokeProperty(key = "sync_settings_pull_insert_allowed", defaultValue = false, description = "Синхронизация: Настройки песен — pull, Добавление"),
    KaraokeProperty(key = "sync_settings_pull_update_allowed", defaultValue = false, description = "Синхронизация: Настройки песен — pull, Изменение"),
    KaraokeProperty(key = "sync_settings_pull_delete_allowed", defaultValue = false, description = "Синхронизация: Настройки песен — pull, Удаление"),
    KaraokeProperty(key = "sync_settings_pull_move_allowed", defaultValue = false, description = "Синхронизация: Настройки песен — pull, Перемещение"),
    KaraokeProperty(key = "sync_pictures_push_insert_allowed", defaultValue = false, description = "Синхронизация: Картинки — push, Добавление"),
    KaraokeProperty(key = "sync_pictures_push_update_allowed", defaultValue = false, description = "Синхронизация: Картинки — push, Изменение"),
    KaraokeProperty(key = "sync_pictures_push_delete_allowed", defaultValue = false, description = "Синхронизация: Картинки — push, Удаление"),
    KaraokeProperty(key = "sync_pictures_push_move_allowed", defaultValue = false, description = "Синхронизация: Картинки — push, Перемещение"),
    KaraokeProperty(key = "sync_pictures_pull_insert_allowed", defaultValue = false, description = "Синхронизация: Картинки — pull, Добавление"),
    KaraokeProperty(key = "sync_pictures_pull_update_allowed", defaultValue = false, description = "Синхронизация: Картинки — pull, Изменение"),
    KaraokeProperty(key = "sync_pictures_pull_delete_allowed", defaultValue = false, description = "Синхронизация: Картинки — pull, Удаление"),
    KaraokeProperty(key = "sync_pictures_pull_move_allowed", defaultValue = false, description = "Синхронизация: Картинки — pull, Перемещение"),
    KaraokeProperty(key = "sync_authors_push_insert_allowed", defaultValue = false, description = "Синхронизация: Авторы — push, Добавление"),
    KaraokeProperty(key = "sync_authors_push_update_allowed", defaultValue = false, description = "Синхронизация: Авторы — push, Изменение"),
    KaraokeProperty(key = "sync_authors_push_delete_allowed", defaultValue = false, description = "Синхронизация: Авторы — push, Удаление"),
    KaraokeProperty(key = "sync_authors_push_move_allowed", defaultValue = false, description = "Синхронизация: Авторы — push, Перемещение"),
    KaraokeProperty(key = "sync_authors_pull_insert_allowed", defaultValue = false, description = "Синхронизация: Авторы — pull, Добавление"),
    KaraokeProperty(key = "sync_authors_pull_update_allowed", defaultValue = false, description = "Синхронизация: Авторы — pull, Изменение"),
    KaraokeProperty(key = "sync_authors_pull_delete_allowed", defaultValue = false, description = "Синхронизация: Авторы — pull, Удаление"),
    KaraokeProperty(key = "sync_authors_pull_move_allowed", defaultValue = false, description = "Синхронизация: Авторы — pull, Перемещение"),
    KaraokeProperty(key = "sync_siteusers_push_insert_allowed", defaultValue = false, description = "Синхронизация: Пользователи сайта — push, Добавление"),
    KaraokeProperty(key = "sync_siteusers_push_update_allowed", defaultValue = false, description = "Синхронизация: Пользователи сайта — push, Изменение"),
    KaraokeProperty(key = "sync_siteusers_push_delete_allowed", defaultValue = false, description = "Синхронизация: Пользователи сайта — push, Удаление"),
    KaraokeProperty(key = "sync_siteusers_push_move_allowed", defaultValue = false, description = "Синхронизация: Пользователи сайта — push, Перемещение"),
    KaraokeProperty(key = "sync_siteusers_pull_insert_allowed", defaultValue = false, description = "Синхронизация: Пользователи сайта — pull, Добавление"),
    KaraokeProperty(key = "sync_siteusers_pull_update_allowed", defaultValue = false, description = "Синхронизация: Пользователи сайта — pull, Изменение"),
    KaraokeProperty(key = "sync_siteusers_pull_delete_allowed", defaultValue = false, description = "Синхронизация: Пользователи сайта — pull, Удаление"),
    KaraokeProperty(key = "sync_siteusers_pull_move_allowed", defaultValue = false, description = "Синхронизация: Пользователи сайта — pull, Перемещение"),
    KaraokeProperty(key = "sync_siteplaylists_push_insert_allowed", defaultValue = false, description = "Синхронизация: Плейлисты сайта — push, Добавление"),
    KaraokeProperty(key = "sync_siteplaylists_push_update_allowed", defaultValue = false, description = "Синхронизация: Плейлисты сайта — push, Изменение"),
    KaraokeProperty(key = "sync_siteplaylists_push_delete_allowed", defaultValue = false, description = "Синхронизация: Плейлисты сайта — push, Удаление"),
    KaraokeProperty(key = "sync_siteplaylists_push_move_allowed", defaultValue = false, description = "Синхронизация: Плейлисты сайта — push, Перемещение"),
    KaraokeProperty(key = "sync_siteplaylists_pull_insert_allowed", defaultValue = false, description = "Синхронизация: Плейлисты сайта — pull, Добавление"),
    KaraokeProperty(key = "sync_siteplaylists_pull_update_allowed", defaultValue = false, description = "Синхронизация: Плейлисты сайта — pull, Изменение"),
    KaraokeProperty(key = "sync_siteplaylists_pull_delete_allowed", defaultValue = false, description = "Синхронизация: Плейлисты сайта — pull, Удаление"),
    KaraokeProperty(key = "sync_siteplaylists_pull_move_allowed", defaultValue = false, description = "Синхронизация: Плейлисты сайта — pull, Перемещение"),
    KaraokeProperty(key = "sync_siteplaylistitems_push_insert_allowed", defaultValue = false, description = "Синхронизация: Элементы плейлистов — push, Добавление"),
    KaraokeProperty(key = "sync_siteplaylistitems_push_update_allowed", defaultValue = false, description = "Синхронизация: Элементы плейлистов — push, Изменение"),
    KaraokeProperty(key = "sync_siteplaylistitems_push_delete_allowed", defaultValue = false, description = "Синхронизация: Элементы плейлистов — push, Удаление"),
    KaraokeProperty(key = "sync_siteplaylistitems_push_move_allowed", defaultValue = false, description = "Синхронизация: Элементы плейлистов — push, Перемещение"),
    KaraokeProperty(key = "sync_siteplaylistitems_pull_insert_allowed", defaultValue = false, description = "Синхронизация: Элементы плейлистов — pull, Добавление"),
    KaraokeProperty(key = "sync_siteplaylistitems_pull_update_allowed", defaultValue = false, description = "Синхронизация: Элементы плейлистов — pull, Изменение"),
    KaraokeProperty(key = "sync_siteplaylistitems_pull_delete_allowed", defaultValue = false, description = "Синхронизация: Элементы плейлистов — pull, Удаление"),
    KaraokeProperty(key = "sync_siteplaylistitems_pull_move_allowed", defaultValue = false, description = "Синхронизация: Элементы плейлистов — pull, Перемещение"),
    KaraokeProperty(key = "sync_songassignments_push_insert_allowed", defaultValue = false, description = "Синхронизация: Задания редактора — push, Добавление"),
    KaraokeProperty(key = "sync_songassignments_push_update_allowed", defaultValue = false, description = "Синхронизация: Задания редактора — push, Изменение"),
    KaraokeProperty(key = "sync_songassignments_push_delete_allowed", defaultValue = false, description = "Синхронизация: Задания редактора — push, Удаление"),
    KaraokeProperty(key = "sync_songassignments_push_move_allowed", defaultValue = false, description = "Синхронизация: Задания редактора — push, Перемещение"),
    KaraokeProperty(key = "sync_songassignments_pull_insert_allowed", defaultValue = false, description = "Синхронизация: Задания редактора — pull, Добавление"),
    KaraokeProperty(key = "sync_songassignments_pull_update_allowed", defaultValue = false, description = "Синхронизация: Задания редактора — pull, Изменение"),
    KaraokeProperty(key = "sync_songassignments_pull_delete_allowed", defaultValue = false, description = "Синхронизация: Задания редактора — pull, Удаление"),
    KaraokeProperty(key = "sync_songassignments_pull_move_allowed", defaultValue = false, description = "Синхронизация: Задания редактора — pull, Перемещение"),
    KaraokeProperty(key = "sync_songassignmentdrafts_push_insert_allowed", defaultValue = false, description = "Синхронизация: Черновики редактора — push, Добавление"),
    KaraokeProperty(key = "sync_songassignmentdrafts_push_update_allowed", defaultValue = false, description = "Синхронизация: Черновики редактора — push, Изменение"),
    KaraokeProperty(key = "sync_songassignmentdrafts_push_delete_allowed", defaultValue = false, description = "Синхронизация: Черновики редактора — push, Удаление"),
    KaraokeProperty(key = "sync_songassignmentdrafts_push_move_allowed", defaultValue = false, description = "Синхронизация: Черновики редактора — push, Перемещение"),
    KaraokeProperty(key = "sync_songassignmentdrafts_pull_insert_allowed", defaultValue = false, description = "Синхронизация: Черновики редактора — pull, Добавление"),
    KaraokeProperty(key = "sync_songassignmentdrafts_pull_update_allowed", defaultValue = false, description = "Синхронизация: Черновики редактора — pull, Изменение"),
    KaraokeProperty(key = "sync_songassignmentdrafts_pull_delete_allowed", defaultValue = false, description = "Синхронизация: Черновики редактора — pull, Удаление"),
    KaraokeProperty(key = "sync_songassignmentdrafts_pull_move_allowed", defaultValue = false, description = "Синхронизация: Черновики редактора — pull, Перемещение"),
    KaraokeProperty(key = "sync_events_push_insert_allowed", defaultValue = false, description = "Синхронизация: Статистика — push, Добавление"),
    KaraokeProperty(key = "sync_events_push_update_allowed", defaultValue = false, description = "Синхронизация: Статистика — push, Изменение"),
    KaraokeProperty(key = "sync_events_push_delete_allowed", defaultValue = false, description = "Синхронизация: Статистика — push, Удаление"),
    KaraokeProperty(key = "sync_events_push_move_allowed", defaultValue = false, description = "Синхронизация: Статистика — push, Перемещение"),
    KaraokeProperty(key = "sync_events_pull_insert_allowed", defaultValue = true, description = "Синхронизация: Статистика — pull, Добавление"),
    KaraokeProperty(key = "sync_events_pull_update_allowed", defaultValue = true, description = "Синхронизация: Статистика — pull, Изменение"),
    KaraokeProperty(key = "sync_events_pull_delete_allowed", defaultValue = false, description = "Синхронизация: Статистика — pull, Удаление"),
    KaraokeProperty(key = "sync_events_pull_move_allowed", defaultValue = true, description = "Синхронизация: Статистика — pull, Перемещение"),
    KaraokeProperty(key = "backgroundFolderPath", defaultValue = "/sm-karaoke/system/SpaceBox4096", description = "Путь к папке с фонами"),
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
    KaraokeProperty(key = "separLineColor", defaultValue = Color(255,255,255,60).setting(), description = "Цвет линии разделителя слогов"),
    KaraokeProperty(key = "tabsLineColor", defaultValue = Color(85,200,200,255).setting(), description = "Цвет линий табулатуры нот"),
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
            font = Font(CHORDS_FONT_NAME, 0, 80),
            shapeColor = Color(255,127,127,255),
            shapeOutlineColor = Color(0,0,0,255),
            shapeOutline = 0,
            fontUnderline = 0
        ).setting(),
        description = "Фонт аккордов"
    ),
    KaraokeProperty(key = "chordsHeightCoefficient", defaultValue = 0.72, description = "Коэффициэнт размера шрифта аккорда относительно размера шрифта текста песни"),
    KaraokeProperty(
        key = "melodyNoteFont",
        defaultValue = MltText(
            font = Font(MELODY_NOTE_FONT_NAME, 0, 80),
            shapeColor = Color(255,127,127,255),
            shapeOutlineColor = Color(0,0,0,255),
            shapeOutline = 0,
            fontUnderline = 0
        ).setting(),
        description = "Фонт ноты"
    ),
    KaraokeProperty(key = "chordsHeightOffsetCoefficient", defaultValue = 0.72, description = "Коэффициэнт оффсета размера шрифта аккорда относительно размера шрифта текста песни"),
    KaraokeProperty(key = "melodyNoteHeightCoefficient", defaultValue = 0.72, description = "Коэффициэнт размера шрифта ноты относительно размера шрифта текста песни"),
    KaraokeProperty(key = "melodyNoteHeightOffsetCoefficient", defaultValue = 0.72, description = "Коэффициэнт оффсета размера шрифта ноты относительно размера шрифта текста песни"),
    KaraokeProperty(
        key = "melodyOctaveFont",
        defaultValue = MltText(
            font = Font(MELODY_OCTAVE_FONT_NAME, 0, 80),
            shapeColor = Color(127,127,127,255),
            shapeOutlineColor = Color(0,0,0,255),
            shapeOutline = 0,
            fontUnderline = 0
        ).setting(),
        description = "Фонт октавы"
    ),
    KaraokeProperty(key = "melodyOctaveHeightCoefficient", defaultValue = 0.52, description = "Коэффициэнт размера шрифта октавы относительно размера шрифта текста песни"),

    KaraokeProperty(
        key = "melodyTabsFont",
        defaultValue = MltText(
            font = Font(MELODY_NOTE_FONT_NAME, 0, 80),
            shapeColor = Color(255,127,127,255),
            shapeOutlineColor = Color(0,0,0,255),
            shapeOutline = 0,
            fontUnderline = 0
        ).setting(),
        description = "Фонт номера лада в табулатуре"
    ),
    KaraokeProperty(key = "melodyTabsHeightCoefficient", defaultValue = 0.60, description = "Коэффициэнт размера шрифта номера лада в табулатуре относительно размера шрифта текста песни"),
    KaraokeProperty(key = "melodyTabsHeightOffsetCoefficient", defaultValue = 0.50, description = "Коэффициэнт оффсета размера шрифта номера лада в табулатуре относительно размера шрифта текста песни"),

    KaraokeProperty(
        key = "melodyOpenStringFont",
        defaultValue = MltText(
            font = Font(MELODY_NOTE_FONT_NAME, 0, 80),
            shapeColor = Color(255,188,188,255),
            shapeOutlineColor = Color(0,0,0,255),
            shapeOutline = 0,
            fontUnderline = 0
        ).setting(),
        description = "Фонт открытой струны в табулатуре"
    ),
    KaraokeProperty(key = "melodyOpenStringHeightCoefficient", defaultValue = 0.30, description = "Коэффициэнт размера шрифта открытой струны в табулатуре относительно размера шрифта текста песни"),




    KaraokeProperty(
        key = "chordsCapoFont",
        defaultValue = MltText(
            font = Font(CHORDS_CAPO_FONT_NAME, 0, 35),
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
    KaraokeProperty(key = "yandexSmartCaptchaClientKey", defaultValue = "", description = "Yandex SmartCaptcha — клиентский ключ (публичный, для формы регистрации на сайте)"),
    KaraokeProperty(key = "yandexSmartCaptchaServerKey", defaultValue = "", description = "Yandex SmartCaptcha — серверный ключ (секретный, для валидации на бэкенде)"),

    // Ограничение CPU тяжёлых заданий (KaraokeProcess) — глобальный переключатель + процент на тип задания.
    // 100 = без ограничения (поведение как раньше). Применяется только в момент старта задания, не влияет
    // на уже запущенные. См. Utils.kt: hostCpuCoreCount/dockerCpusFlag/cpulimitPrefix/cpuLimitPercentForType.
    KaraokeProperty(key = "resourceLimitsEnabled", defaultValue = false, description = "Ограничивать CPU тяжёлых заданий (иначе — безлимит)"),
    KaraokeProperty(key = "cpuLimitPercentMeltLyrics", defaultValue = 100L, description = "Лимит CPU (%) для MELT_LYRICS"),
    KaraokeProperty(key = "cpuLimitPercentMeltKaraoke", defaultValue = 100L, description = "Лимит CPU (%) для MELT_KARAOKE"),
    KaraokeProperty(key = "cpuLimitPercentMeltChords", defaultValue = 100L, description = "Лимит CPU (%) для MELT_CHORDS"),
    KaraokeProperty(key = "cpuLimitPercentMeltTabs", defaultValue = 100L, description = "Лимит CPU (%) для MELT_TABS"),
    KaraokeProperty(key = "cpuLimitPercentDemucs2", defaultValue = 100L, description = "Лимит CPU (%) для DEMUCS2"),
    KaraokeProperty(key = "cpuLimitPercentDemucs5", defaultValue = 100L, description = "Лимит CPU (%) для DEMUCS5"),
    KaraokeProperty(key = "cpuLimitPercentKeyBpmFinder", defaultValue = 100L, description = "Лимит CPU (%) для Key/BPM Finder (docker-шаг)"),
    KaraokeProperty(key = "cpuLimitPercentSheetsage", defaultValue = 100L, description = "Лимит CPU (%) для SHEETSAGE"),
    KaraokeProperty(key = "cpuLimitPercentSheetsage2", defaultValue = 100L, description = "Лимит CPU (%) для SHEETSAGE2"),
    KaraokeProperty(key = "cpuLimitPercentFf720Kar", defaultValue = 100L, description = "Лимит CPU (%) для FF_720_KAR"),
    KaraokeProperty(key = "cpuLimitPercentFf720Lyr", defaultValue = 100L, description = "Лимит CPU (%) для FF_720_LYR"),

    // Автоматизация публикации в Telegram-канал (см. TelegramApiClient/TelegramUpdatesConsumer).
    // Фаза 1 — автоотлов ссылки на вышедший (отложенный, созданный вручную) пост через long-polling
    // getUpdates. Работа из России: Telegram периодически недоступен без VPN — telegramProxyUrl задаёт
    // HTTP-прокси (VLESS/xray) для авто-fallback "напрямую → прокси" (см. CLAUDE.md/архив).
    KaraokeProperty(key = "telegramBotToken", defaultValue = "", description = "Telegram: токен бота-администратора канала"),
    KaraokeProperty(key = "telegramChannelUsername", defaultValue = "svoemestokaraoke", description = "Telegram: username канала (без @)"),
    KaraokeProperty(key = "telegramChannelChatId", defaultValue = "", description = "Telegram: числовой chat_id канала (-100...)"),
    KaraokeProperty(key = "telegramBotApiBaseUrl", defaultValue = "https://api.telegram.org", description = "Telegram: базовый URL Bot API (Фаза 2 — локальный Local Bot API server, напр. http://localhost:8081)"),
    KaraokeProperty(key = "telegramUpdatesOffset", defaultValue = 0L, description = "Telegram: курсор (offset) long-polling getUpdates — не редактировать вручную"),
    KaraokeProperty(key = "telegramPollingEnabled", defaultValue = false, description = "Telegram: включить фоновый отлов вышедших постов (getUpdates)"),
    KaraokeProperty(key = "telegramFallbackMatchWindowMin", defaultValue = 10L, description = "Telegram: окно (мин) для проверки времени поста относительно графика эфира"),
    KaraokeProperty(key = "telegramProxyUrl", defaultValue = "", description = "Telegram: HTTP-прокси (VLESS/xray) для доступа при недоступности напрямую, напр. http://telegram-xray:1082. Пусто = только напрямую"),
    KaraokeProperty(key = "telegramDirectTimeoutMs", defaultValue = 10_000L, description = "Telegram: таймаут прямого запроса перед переключением на прокси"),
    KaraokeProperty(key = "telegramProxyModeTtlMs", defaultValue = 60_000L, description = "Telegram: как часто перепроверять восстановление прямого доступа, пока используется прокси"),
)