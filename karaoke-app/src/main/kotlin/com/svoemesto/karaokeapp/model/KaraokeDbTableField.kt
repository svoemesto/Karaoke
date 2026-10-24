package com.svoemesto.karaokeapp.model

@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY) // Указывает, что аннотация может применяться к полям и свойствам
@Retention(AnnotationRetention.RUNTIME) // Указывает, что аннотация должна быть доступна во время выполнения (runtime)

annotation class KaraokeDbTableField(
    val name: String,
    val isId: Boolean = false,
    val useInDiff: Boolean = true,
    // Контракт «поле входит в recordhash» (md5-формулу триггера в БД). Runtime-код это значение
    // не читает — формулу задаёт SQL-триггер; флаг объявляет намерение и проверяется статически:
    // tools/check-recordhash-consistency.py (check 2) требует, чтобы каждая колонка с
    // useInHash = true присутствовала в md5 своего канонического deploy/recordhash_<table>.sql.
    // Пример исключения — WebEvent.lastUpdate (поле авто-обновляет триггер в каждой БД).
    @Suppress("unused")
    val useInHash: Boolean = true,
    val useInList: Boolean = true, // Если false, то не будет включено в loadList. Нужно для больших полей типа full в Pictures, чтобы не было OutOfMemory Java heap space
)
