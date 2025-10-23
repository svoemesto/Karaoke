package com.svoemesto.karaokeapp.model

@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY) // Указывает, что аннотация может применяться к полям и свойствам
@Retention(AnnotationRetention.RUNTIME) // Указывает, что аннотация должна быть доступна во время выполнения (runtime)

annotation class KaraokeDbTableField(
    val name: String,
    val isId: Boolean = false,
    val useInDiff: Boolean = true,
    val useInHash: Boolean = true
)
