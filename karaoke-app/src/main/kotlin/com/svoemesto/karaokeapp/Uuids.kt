package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.model.ProducerType
import com.svoemesto.karaokeapp.model.Uuids
import java.util.*

fun getStoredUuid(key: List<Any>): String {
    val typeName = (key[0] as ProducerType).name
    val voiceId = if (key.size > 1) key[1] as Int else -1
    val childId = if (key.size > 2) key[2] as Int else -1
    val elementId = if (key.size > 3) key[3] as Int else -1
    val id = listOf(typeName, voiceId, childId, elementId).hashCode()

    // BR-197 (аудит #336, пакет `PKG-S3-24`): ключи больше не читаются по одному.
    // Раньше на КАЖДЫЙ ключ MLT-модели делался отдельный `SELECT … WHERE id = ?`
    // (тысячи round-trip'ов на песню), для новых — ещё и отдельный INSERT.
    // Теперь все пары `(id, uuid)` читаются одним запросом и держатся в кеше.
    if (!Uuids.isCacheLoaded()) {
        Uuids.loadAll(WORKING_DATABASE)
    }
    var uuid = Uuids.cachedUuid(id)
    if (uuid.isNullOrBlank()) {
        // Ключа нет в кеше — читаем из БД (кеш мог быть загружен раньше появления ключа).
        uuid = Uuids.load(id, WORKING_DATABASE)?.uuid
    }
    if (uuid.isNullOrBlank()) {
        val generated = UUID.randomUUID().toString()
        Uuids.createDbInstance(Uuids(id, generated), WORKING_DATABASE)
        // После вставки перечитываем: если строка уже была создана другим процессом,
        // в базе лежит чужой uuid — берём его, а не свой (иначе uuid в MLT не совпадёт
        // с uuid в БД и будет меняться при каждом рендере).
        val stored = Uuids.load(id, WORKING_DATABASE)?.uuid?.takeIf { it.isNotBlank() } ?: generated
        Uuids.putCached(id, stored)
        return stored
    }
    return uuid
}
