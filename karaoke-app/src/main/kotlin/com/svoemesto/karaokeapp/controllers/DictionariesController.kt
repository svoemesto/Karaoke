package com.svoemesto.karaokeapp.controllers

import com.svoemesto.karaokeapp.Connection
import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.model.Dictionary
import com.svoemesto.karaokeapp.services.KSS_APP
import com.svoemesto.karaokeapp.services.SAC_APP
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody

// Админка словарей (tbl_dictionaries: пары dict_name/dict_value — "Слова с Ё", "Censored",
// "Sync Ids"). Только LOCAL — правки уходят на сервер штатной синхронизацией (SyncTarget
// "dictionaries", см. sync/SyncTarget.kt), отдельного target=remote тут не нужно.
@Controller
@RequestMapping("/api/dictionaries")
class DictionariesController {

    // Per-request соединение обязательно закрывать (см. инвариант resolveDb connection leak в
    // CLAUDE.md) — иначе пул Postgres постепенно исчерпывается.
    private fun <T> withDb(block: (KaraokeConnection) -> T): T {
        val db = Connection.local()
        return try {
            block(db)
        } finally {
            try { db.getConnection()?.close() } catch (_: Exception) {}
        }
    }

    @PostMapping("/list")
    @ResponseBody
    fun list(
        @RequestParam(required = false) id: Long?,
        @RequestParam(required = false) dictName: String?,
        @RequestParam(required = false) dictValue: String?,
    ): Map<String, Any> = withDb { db ->
        var items = Dictionary.loadList(
            whereArgs = when {
                id != null -> mapOf("id" to id.toString())
                !dictName.isNullOrBlank() -> mapOf("dict_name" to dictName)
                else -> emptyMap()
            },
            database = db,
            storageService = KSS_APP,
            storageApiClient = SAC_APP
        )
        if (!dictValue.isNullOrBlank()) {
            items = items.filter { it.dictValue.contains(dictValue, ignoreCase = true) }
        }
        mapOf("dictionaries" to items.map { it.toDTO() }.sorted())
    }

    @PostMapping("/names")
    @ResponseBody
    fun names(): Map<String, Any> = withDb { db ->
        mapOf("names" to Dictionary.loadList(whereArgs = emptyMap(), database = db, storageService = KSS_APP, storageApiClient = SAC_APP)
            .map { it.dictName }.distinct().sorted())
    }

    @PostMapping("/create")
    @ResponseBody
    fun create(
        @RequestParam dictName: String,
        @RequestParam dictValue: String,
    ): Map<String, Any> = withDb { db ->
        // Пара (dictName, dictValue) уникальна (uq_tbl_dictionaries_name_value);
        // createNewDictionaryItem идемпотентен и на дубль тихо вернёт id существующей записи —
        // проверяем существование ДО вызова, чтобы честно сообщить фронту, была ли запись реально
        // создана (иначе UI не сможет отличить "добавлено" от "такая пара уже была").
        val existedBefore = Dictionary.getItem(dictName = dictName, dictValue = dictValue, database = db) != null
        val item = Dictionary.createNewDictionaryItem(
            newItem = Dictionary(database = db).apply {
                this.dictName = dictName
                this.dictValue = dictValue
            },
            database = db
        )
        mapOf("id" to (item?.id ?: 0L), "created" to (item != null && !existedBefore))
    }

    @PostMapping("/update")
    @ResponseBody
    fun update(
        @RequestParam id: Long,
        @RequestParam(required = false) dictName: String?,
        @RequestParam(required = false) dictValue: String?,
    ): Long = withDb { db ->
        val item = Dictionary.loadList(whereArgs = mapOf("id" to id.toString()), database = db, storageService = KSS_APP, storageApiClient = SAC_APP)
            .firstOrNull() ?: return@withDb 0L
        val newDictName = dictName ?: item.dictName
        val newDictValue = dictValue ?: item.dictValue
        // uq_tbl_dictionaries_name_value: ветка UPDATE в KaraokeDbTable.save() молча глотает SQL-
        // исключение (executeUpdate там обёрнут в try/catch без rethrow — save() не бросает даже
        // при нарушении уникального индекса), поэтому конфликт проверяем заранее сами, не полагаясь
        // на try/catch вокруг save().
        val conflict = Dictionary.getItem(dictName = newDictName, dictValue = newDictValue, database = db)
        if (conflict != null && conflict.id != id) return@withDb 0L
        item.dictName = newDictName
        item.dictValue = newDictValue
        item.save()
        item.id
    }

    @PostMapping("/delete")
    @ResponseBody
    fun delete(@RequestParam id: Long): Boolean = withDb { db -> Dictionary.delete(id, db) }
}
