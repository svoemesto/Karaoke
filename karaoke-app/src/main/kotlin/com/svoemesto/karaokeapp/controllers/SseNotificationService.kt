//@file:Suppress("unused")
package com.svoemesto.karaokeapp.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.svoemesto.karaokeapp.model.SseNotification
import jakarta.annotation.PreDestroy
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.Serializable
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class Notification<out T>(
    @Suppress("unused") val userId: Long,
    @Suppress("unused") val payload: T? = null,
    val timestamp: Long = System.currentTimeMillis()) : Serializable

data class UserKey(
    val userId: Long,
    val browserTabId: String)
@Service
class SseNotificationService(private val mapper: ObjectMapper) {

    private val maxEmittersPerTab = 1
    private var emitters: ConcurrentHashMap<UserKey, MutableList<SseEmitter>> = ConcurrentHashMap()

    fun send(data: SseNotification) {
        send(1L, data)
    }

    private fun send(userId: Long, data: SseNotification) {
        val userKeys = emitters
            .filter { it.key.userId == userId }
            .keys

        if (userKeys.isEmpty()) {
            return
        }

        val notification = Notification(
            userId = userId,
            payload = data)

        val sseEvent = SseEmitter.event()
            .id(UUID.randomUUID().toString())
            .data(mapper.writeValueAsString(notification))
            .name("user")

        userKeys.forEach { userKey ->
            val userEmitters = emitters[userKey]!!
            synchronized(userEmitters) {
                val staleEmitters = ArrayList<SseEmitter>()
                userEmitters.forEach { emitter ->
                    try {
                        emitter.send(sseEvent)
                    } catch (e: Exception) {
                        emitter.completeWithError(e)
                        staleEmitters.add(emitter)
                    }
                }
                if (staleEmitters.isNotEmpty()) {
//                    val before = userEmitters.size
                    staleEmitters.forEach { stale ->
                        userEmitters.removeIf { it === stale }
                        // если больше не осталось эмиттеров по данному ключу, то удалим ключ из мапы
                        if (userEmitters.isEmpty()) {
                            emitters.remove(userKey)
                        }
                    }
                }
            }
        }
    }

    fun subscribe(): SseEmitter {
        return subscribe(1L, "tabId")
    }
    private fun subscribe(userId: Long, browserTabId: String): SseEmitter {
        val emitter = createEmitter()
        val key = UserKey(userId, browserTabId)

        emitters.putIfAbsent(key, mutableListOf())

        with(emitters[key]!!) {
            synchronized(this) {
                if (size == maxEmittersPerTab) {
                    removeAt(0).safeComplete() // завершить предыдущий, если есть
                }
                add(emitter)
            }
        }
        return emitter
    }

    private fun createEmitter(): SseEmitter {
        return SseEmitter(-1)
    }

    @PreDestroy
    fun onShutdown() {
        synchronized(emitters) {
            emitters.forEach { (_, emitters) ->
                emitters.forEach {
                    it.safeComplete()
                }
            }
        }
    }

    private fun SseEmitter.safeComplete() {
        try {
            complete()
        } catch (e: Exception) {
            completeWithError(e)
        }
    }
}