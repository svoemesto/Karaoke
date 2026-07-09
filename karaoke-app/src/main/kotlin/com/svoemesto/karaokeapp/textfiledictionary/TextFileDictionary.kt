package com.svoemesto.karaokeapp.textfiledictionary

import com.svoemesto.karaokeapp.TEXT_FILE_DICTS
import com.svoemesto.karaokeapp.WORKING_DATABASE
import com.svoemesto.karaokeapp.model.Dictionary

/**
 * Раньше словарь хранился в текстовом файле на диске (pathToFile()/save()/loadList() читали
 * и писали .txt). Теперь все значения живут в единой таблице БД tbl_dictionaries
 * (см. model/Dictionary.kt) — интерфейс сохранён как тонкий фасад над ней, чтобы не трогать
 * вызывающий код (Extentions.censored(), Utils.replaceSymbolsInSong, Settings.getWhereList,
 * ApiController.doTextFileDictionary/getDict/addSyncForAll).
 */
interface TextFileDictionary {

    companion object {

        fun doAction(dictName: String, dictAction: String, dictValues: List<String>): Boolean {
            val tfd = TEXT_FILE_DICTS[dictName] ?: return false
            val tfdInstance = tfd.getDeclaredConstructor().newInstance()
            val func = tfdInstance.javaClass.declaredMethods.firstOrNull { it.name == dictAction } ?: return false
            func.invoke(tfdInstance, dictValues)
            return true
        }

        @Suppress("UNCHECKED_CAST")
        fun loadList(dictName: String): List<String> {
            val tfd = TEXT_FILE_DICTS[dictName] ?: return emptyList()
            val tfdInstance = tfd.getDeclaredConstructor().newInstance()
            val func = tfdInstance.javaClass.declaredMethods.firstOrNull { it.name == "loadList" } ?: return emptyList()
            val result = func.invoke(tfdInstance)
            return if (result is List<*>) result as List<String> else emptyList()
        }

    }

    /** Имя словаря (колонка dict_name в tbl_dictionaries) — раньше был путь к текстовому файлу. */
    fun dictName(): String

    // karaoke-web не имеет полноценной инициализации ConstantsKt/Connection (см. «karaoke-web Settings
    // trap» в CLAUDE.md) — обращение к WORKING_DATABASE там может бросить NoClassDefFoundError (Error,
    // не Exception), роняя весь запрос (Zakroma, страница песни и т.п.). Деградируем до пустого словаря,
    // а не валим вызывающий эндпоинт.
    val dict: List<String> get() = try {
        Dictionary.loadValues(dictName(), WORKING_DATABASE)
    } catch (e: Throwable) {
        emptyList()
    }

    fun clear() {
        Dictionary.clear(dictName(), WORKING_DATABASE)
    }

    fun save() {
        // Раньше здесь файл перезаписывался целиком; теперь запись построчная (add/remove
        // пишут сразу в БД) — метод оставлен как no-op ради обратной совместимости вызывающего кода.
    }

    fun add(elements: List<String>) {
        Dictionary.addValues(dictName(), elements, WORKING_DATABASE)
    }

    fun addOne(element: String) {
        Dictionary.addValues(dictName(), listOf(element), WORKING_DATABASE)
    }

    fun remove(elements: List<String>) {
        Dictionary.removeValues(dictName(), elements, WORKING_DATABASE)
    }

    fun removeOne(element: String) {
        Dictionary.removeValues(dictName(), listOf(element), WORKING_DATABASE)
    }

    @Suppress("unused")
    fun editOne(oldElement: String, newElement: String) {
        if (oldElement == "" || newElement == "") return
        Dictionary.removeValues(dictName(), listOf(oldElement), WORKING_DATABASE)
        Dictionary.addValues(dictName(), listOf(newElement), WORKING_DATABASE)
    }

    @Suppress("unused")
    fun have(element: String) = Dictionary.have(dictName(), element, WORKING_DATABASE)

    fun loadList(): List<String> = Dictionary.loadValues(dictName(), WORKING_DATABASE)

}
