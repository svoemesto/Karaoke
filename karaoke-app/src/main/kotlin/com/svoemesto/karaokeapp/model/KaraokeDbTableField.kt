package com.svoemesto.karaokeapp.model

@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY) // Указывает, что аннотация может применяться к полям и свойствам
@Retention(AnnotationRetention.RUNTIME) // Указывает, что аннотация должна быть доступна во время выполнения (runtime)

annotation class KaraokeDbTableField(
    val name: String,
    val isId: Boolean = false,
    val useInDiff: Boolean = true,
    @Suppress("unused")
    val useInHash: Boolean = true,
    val useInList: Boolean = true   // Если false, то не будет включено в loadList. Нужно для больших полей типа full в Pictures, чтобы не было OutOfMemory Java heap space
)
