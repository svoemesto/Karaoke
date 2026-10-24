package com.svoemesto.karaokeapp.controllers

/**
 * Проверка индекса голоса, пришедшего из URL (`/song/{id}/{voice}/…`).
 *
 * BR-235/P-10-05: `Song.setSourceTextAndMarkers` при `voice >= 0` дополняет два списка до
 * индекса (`while (size <= voice) add(...)`) и кодирует их в JSON. Запрос с `voice=100000000`
 * выделял ~10^8 элементов в JVM, где живёт и очередь `KaraokeProcess`, — OOM/долгая блокировка.
 * В API-зеркале проверка была (`voice < 0 || voice > song.countVoices`), в Thymeleaf-контроллере —
 * нет. Верхняя граница намеренно повторяет API-контракт (`0..countVoices`): паддинг на один
 * пустой голос безопасен, неограниченный — нет.
 */
internal fun isValidVoiceIndex(voice: Int, countVoices: Int): Boolean = voice in 0..countVoices
