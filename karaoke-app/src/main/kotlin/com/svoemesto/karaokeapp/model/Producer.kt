package com.svoemesto.karaokeapp.model

import java.io.Serializable

@Suppress("unused")
data class Producer(
    val producerType: ProducerType,
    val groupId: Int,
    val param: MutableMap<String, Any?>
) : Serializable
