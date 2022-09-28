package model

data class Producer(
    val producerType: ProducerType,
    val groupId: Int,
    val param: MutableMap<String, Any?>
)
