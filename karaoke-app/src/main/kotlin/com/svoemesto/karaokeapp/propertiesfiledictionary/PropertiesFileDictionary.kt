package com.svoemesto.karaokeapp.propertiesfiledictionary

import com.svoemesto.karaokeapp.runCommand
import com.svoemesto.karaokeapp.writePropertiesFileAtomic
import java.io.File
import java.util.Base64
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("PropertiesFileDictionary")

/**
 * Префикс закодированного значения (BR-133, аудит #336).
 *
 * Старые файлы писались «как есть» (`key<SEP>value`), поэтому чтение
 * поддерживает оба формата: строка без префикса — legacy-значение.
 */
private const val ENCODED_VALUE_PREFIX = "b64:"

/**
 * Один lock на все экземпляры словаря: `set()` из разных HTTP-потоков
 * (фронт шлёт `/api/setwebvueprop` на каждое изменение фильтров) не должен
 * интерливить truncate+write одного и того же файла.
 */
private val PROPERTIES_FILE_LOCK = Any()

/**
 * Интерфейс для properties file dictionary.
 *
 * @see AGENTS.md
 */
interface PropertiesFileDictionary {
    fun separator() = "{WEBVUE_PROPERTIES_SEPARATOR}"

    fun pathToFile(): String

    var props: MutableMap<String, String>

    fun get(
        key: String,
        default: String,
    ): String = props[key] ?: default

    fun get(key: String): String = get(key = key, default = "")

    fun set(
        key: String,
        value: String,
    ) {
        synchronized(PROPERTIES_FILE_LOCK) {
            props[key] = value
            save()
        }
    }

    /**
     * Читает файл свойств.
     *
     * BR-133: разбор устойчив к битой строке — пропускает её с WARN и
     * сохраняет остальные настройки. Раньше destructuring `val (key, value) =
     * line.split(separator())` бросал `IndexOutOfBoundsException` на строке без
     * разделителя (например, после записи значения с переводом строки без
     * экранирования), и приложение не поднималось на следующем старте.
     */
    fun loadMap(): MutableMap<String, String> {
        val result: MutableMap<String, String> = mutableMapOf()
        val separator = separator()
        val list =
            try {
                File(pathToFile())
                    .readText()
                    .split("\n")
                    .filter { it != "" }
            } catch (_: Exception) {
//            println("Ошибка считывания файла, возвращаем пустую мапу")
                return result
            }

        list.forEachIndexed { i, line ->
            val index = line.indexOf(separator)
            if (index < 0) {
                log.warn("loadMap: пропускаю битую строку {} (нет разделителя): '{}'", i + 1, line.take(120))
                return@forEachIndexed
            }
            val key = line.substring(0, index)
            val rawValue = line.substring(index + separator.length)
            result[key] =
                if (rawValue.startsWith(ENCODED_VALUE_PREFIX)) {
                    try {
                        String(
                            Base64.getDecoder().decode(rawValue.removePrefix(ENCODED_VALUE_PREFIX)),
                            Charsets.UTF_8,
                        )
                    } catch (e: IllegalArgumentException) {
                        log.warn("loadMap: пропускаю строку {} — битый Base64 значения ключа '{}': {}", i + 1, key, e.message)
                        return@forEachIndexed
                    }
                } else {
                    rawValue
                }
        }
//        println("result = $result")
        return result
    }

    /**
     * Атомарно переписывает файл свойств.
     *
     * BR-133: значение кодируется в Base64 (иначе перевод строки — легальный
     * POST-параметр `/api/setwebvueprop` — порождал вторую строку без
     * разделителя), запись идёт через `writePropertiesFileAtomic` (tmp +
     * `ATOMIC_MOVE`) под общим lock. Раньше `File.writeText` усекал файл до
     * записи: падение процесса оставляло частичный файл, а параллельные `set()`
     * затирали правки друг друга.
     */
    private fun save() {
        val separator = separator()
        val list =
            props.map { (key, value) ->
                val encoded = Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))
                "$key$separator$ENCODED_VALUE_PREFIX$encoded"
            }
//        println("list to save = $list")
        synchronized(PROPERTIES_FILE_LOCK) {
            writePropertiesFileAtomic(File(pathToFile()), list.joinToString("\n"))
            runCommand(listOf("chmod", "666", pathToFile()))
        }
    }
}
