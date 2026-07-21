// @file:Suppress("unused")
package com.svoemesto.karaokeapp.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.svoemesto.karaokeapp.model.SseNotification
import com.svoemesto.karaokeapp.model.SseNotificationType
import jakarta.annotation.PreDestroy
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.Serializable
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Класс Notification.
 *
 * @see docs/features/async-process-queue.md
 */
class Notification<out T>(
    @Suppress("unused") val userId: Long,
    @Suppress("unused") val payload: T? = null,
    val timestamp: Long = System.currentTimeMillis(),
) : Serializable

/**
 * Класс User Key.
 *
 * @see docs/features/async-process-queue.md
 */
data class UserKey(
    val userId: Long,
    val browserTabId: String,
)

// Контекст id вкладки браузера, инициировавшей текущий HTTP-запрос (см. TabIdFilter). Заполняется
// фильтром на входе запроса и обязательно очищается в finally - иначе на переиспользуемом потоке
// пула значение "протечёт" в следующий, не связанный с этой вкладкой запрос.

/**
 * Singleton-объект Tab Id Context.
 *
 * @see docs/features/async-process-queue.md
 */
object TabIdContext {
    private val tl = ThreadLocal<String?>()

    fun get(): String? = tl.get()

    fun set(tabId: String?) {
        tl.set(tabId)
    }

    fun clear() {
        tl.remove()
    }
}

/**
 * Spring-сервис для рассылки SSE-событий подписанным вкладкам.
 *
 * Архитектура:
 * - **Ключ подписки** — `UserKey(userId, browserTabId)`. Каждая вкладка
 *   имеет свой `tabId` (генерируется фронтом в `webvue3/src/lib/utils.js` —
 *   `getTabId()`, хранится в `sessionStorage`, передаётся в query-параметре
 *   `/api/subscribe?tabId=<id>` и в HTTP-заголовке `X-Tab-Id`).
 * - **Broadcast** — события типов `recordChange`, `log`, `processWorkerState`,
 *   `processCountWaiting`, `MONITOR_ALERTS` рассылаются всем вкладкам `userId`.
 * - **Адресная доставка** — события типов `MESSAGE`, `ERROR` доставляются
 *   ТОЛЬКО вкладке-инициатору (если `tabId` известен из [TabIdContext]).
 *   Это ответы на конкретные действия пользователя (например, «запустил
 *   рендер — получил ответ «успешно» или «ошибка»).
 * - **Heartbeat** — независимый Spring-планировщик [heartbeat] каждые 15
 *   секунд шлёт SSE-comment `:ping` всем подписчикам. Без heartbeat
 *   прокси/клиент рвёт соединение по таймауту простоя (особенно когда
 *   [com.svoemesto.karaokeapp.KaraokeProcessWorker] остановлен).
 *
 * **Ловушки** (см. [docs/features/sse-notifications.md]):
 * - `TabIdContext` живёт в `ThreadLocal` — должен очищаться после
 *   HTTP-запроса через `TabIdFilter` (OncePerRequestFilter).
 * - Длинные payload'ы (recordChange с большим `SettingsDTO`) могут
 *   рвать соединение — дробите на части.
 *
 * @see docs/features/sse-notifications.md
 * @see TabIdContext ThreadLocal с tabId текущего запроса
 * @see SseController REST-эндпоинт `/api/subscribe`
 */

/**
 * Сервис для sse notification .
 *
 * @see docs/features/sse-notifications.md
 */
@Service
class SseNotificationService(
    private val mapper: ObjectMapper,
) {
    private val maxEmittersPerTab = 1
    private var emitters: ConcurrentHashMap<UserKey, MutableList<SseEmitter>> = ConcurrentHashMap()

    // MESSAGE/ERROR, сгенерированные на потоке HTTP-запроса (ответ на конкретное действие пользователя),
    // доставляются адресно - только вкладке-инициатору (см. TabIdContext). Остальные типы (recordChange,
    // log, processWorkerState и т.п.), а также MESSAGE/ERROR без известного tabId (фоновый воркер) -
    // широковещательно, всем вкладкам, как и раньше.
    private val addressedTypes = setOf(SseNotificationType.MESSAGE, SseNotificationType.ERROR)

    /**
     * Отправить событие всем вкладкам пользователя `userId=1` (default).
     * Используется для событий, не привязанных к конкретному пользователю
     * (например, `processWorkerState`, `MONITOR_ALERTS`).
     *
     * @param data событие для рассылки (см. [SseNotification] и [SseNotificationType]).
     * @see send(userId, data) overload с явным userId
     */
    fun send(data: SseNotification) {
        send(1L, data)
    }

    private fun send(
        userId: Long,
        data: SseNotification,
    ) {
        val addressedTabId = TabIdContext.get()
        val targeted = data.type in addressedTypes && !addressedTabId.isNullOrBlank()

        val userKeys: Collection<UserKey> =
            if (targeted) {
                val key = UserKey(userId, addressedTabId!!)
                if (emitters.containsKey(key)) listOf(key) else emptyList()
            } else {
                emitters.keys.filter { it.userId == userId }
            }

        if (userKeys.isEmpty()) {
            return
        }

        val notification =
            Notification(
                userId = userId,
                payload = data,
            )

        val sseEvent =
            SseEmitter
                .event()
                .id(UUID.randomUUID().toString())
                .data(mapper.writeValueAsString(notification))
                .name("user")

        sendEventToKeys(userKeys, sseEvent)
    }

    // Heartbeat, не зависящий от KaraokeProcessWorker - раньше единственным "пингом" было сообщение
    // DUMMY, отправляемое только из цикла воркера, пока очередь запущена; при остановленном воркере
    // соединение переставало получать трафик и рвалось прокси/клиентом по таймауту простоя. Здесь -
    // независимый Spring-планировщик (@EnableScheduling в KaraokeAppApplication), шлющий SSE-comment
    // (не долетает до фронтового addEventListener('user', ...), лишних веток обработки не создаёт).

    /**
     * Heartbeat — каждые 15 секунд шлёт SSE-comment `:ping` всем подписчикам.
     * Не зависит от состояния [com.svoemesto.karaokeapp.KaraokeProcessWorker] —
     * раньше heartbeat был через DUMMY-сообщения из воркера, что рвало
     * соединения при остановленной очереди.
     *
     * Аннотация `@Scheduled(fixedRate = 15_000)` требует `@EnableScheduling`
     * в `KaraokeAppApplication` (уже включено).
     *
     * Безопасен: если подписчиков нет (`allKeys.isEmpty()`) — выход без работы.
     */
    @Scheduled(fixedRate = 15_000)
    fun heartbeat() {
        val allKeys = emitters.keys.toList()
        if (allKeys.isEmpty()) {
            return
        }
        sendEventToKeys(allKeys, SseEmitter.event().comment("ping"))
    }

    private fun sendEventToKeys(
        userKeys: Collection<UserKey>,
        sseEvent: SseEmitter.SseEventBuilder,
    ) {
        userKeys.forEach { userKey ->
            val userEmitters = emitters[userKey] ?: return@forEach
            synchronized(userEmitters) {
                val staleEmitters = ArrayList<SseEmitter>()
                userEmitters.forEach { emitter ->
                    try {
                        emitter.send(sseEvent)
                    } catch (e: Exception) {
                        try {
                            emitter.completeWithError(e)
                        } catch (e2: Exception) {
                            // AsyncContext эмиттера уже был завершён/помечен ошибочным ранее (например, вкладка
                            // браузера закрылась) - completeWithError сам может кинуть исключение, которое иначе
                            // вылетело бы из send() и уронило бы совершенно не связанный HTTP-запрос, вызвавший
                            // рассылку уведомления.
                        }
                        staleEmitters.add(emitter)
                    }
                }
                if (staleEmitters.isNotEmpty()) {
                    staleEmitters.forEach { stale ->
                        userEmitters.removeIf { it === stale }
                    }
                    // если больше не осталось эмиттеров по данному ключу, то удалим ключ из мапы
                    if (userEmitters.isEmpty()) {
                        emitters.remove(userKey)
                    }
                }
            }
        }
    }

    fun subscribe(
        userId: Long,
        browserTabId: String,
    ): SseEmitter {
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

    private fun createEmitter(): SseEmitter = SseEmitter(-1)

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
