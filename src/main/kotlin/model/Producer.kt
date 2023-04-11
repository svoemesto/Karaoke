package model

import java.io.Serializable

data class Producer(
    val producerType: ProducerType,
    val groupId: Int,
    val param: MutableMap<String, Any?>
) : Serializable
