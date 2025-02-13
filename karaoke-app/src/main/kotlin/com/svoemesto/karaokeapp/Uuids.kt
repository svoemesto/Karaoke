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
    val uuid = Uuids.load(id, WORKING_DATABASE)?.uuid ?: ""
    return if (uuid == "") {
        Uuids.createDbInstance(Uuids(id,UUID.randomUUID().toString()), WORKING_DATABASE)?.uuid ?: UUID.randomUUID().toString()
    } else {
        uuid
    }
}

