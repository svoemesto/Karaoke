//package com.svoemesto.karaokeapp.controllers
//
//import org.springframework.beans.factory.annotation.Autowired
//import org.springframework.beans.factory.annotation.Qualifier
//import org.springframework.core.task.AsyncTaskExecutor
//import org.springframework.http.MediaType
//import org.springframework.http.codec.ServerSentEvent
//import org.springframework.scheduling.annotation.Async
//import org.springframework.scheduling.annotation.EnableAsync
//import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
//import org.springframework.web.bind.annotation.*
//import reactor.core.publisher.Flux
//import reactor.core.publisher.FluxSink
//import java.util.*
//import java.util.concurrent.ConcurrentHashMap
//
//
//@RestController
//
//class SseRestController() {
//
//    var subscriptions: MutableMap<UUID, SubscriptionData> = ConcurrentHashMap() // 1
//
//    @GetMapping(path = ["/open-sse-stream/{nickName}"], produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
//
//    fun openSseStream(@PathVariable nickName: String): Flux<ServerSentEvent<*>> {
//        return Flux.create { fluxSink ->
//            println("create subscription for $nickName")
//            val uuid: UUID = UUID.randomUUID()
//
//            fluxSink.onCancel {
//                println("subscription $nickName was closed")
//                subscriptions.remove(uuid)
//            }
//
//            val subscriptionData = SubscriptionData(nickName = nickName, fluxSink = fluxSink)
//            subscriptions[uuid] = subscriptionData
//
//            val helloEvent =
//                ServerSentEvent.builder("Hello $nickName").build()
//
//            val d = fluxSink.next(helloEvent)
//        }
//    }
//
//    @PutMapping(path = ["/send-message-for-all"])
//    fun sendMessageForAll(@RequestBody request: SendMessageRequest) {
//
//        val event: ServerSentEvent<String> = ServerSentEvent
//            .builder(request.message)
//            .build()
//
//        subscriptions.forEach { (uuid: Any?, subscriptionData: Any) ->
//            subscriptionData.fluxSink.next(event)
//        }
//    }
//
//    @PutMapping(path = ["/send-message-by-name/{nickName}"])
//    fun sendMessageByName(
//        @PathVariable nickName: String,
//        @RequestBody request: SendMessageRequest
//    ) {
//        val event = ServerSentEvent
//            .builder(request.message)
//            .build()
//        subscriptions.forEach { (uuid: Any?, subscriptionData: Any) ->
//            if (nickName == subscriptionData.nickName) {
//                subscriptionData.fluxSink.next(event)
//            }
//        }
//    }
//}
//
//data class SubscriptionData(
//    val nickName: String,
//    val fluxSink: FluxSink<ServerSentEvent<*>>
//)
//data class SendMessageRequest (
//    val message: String
//)