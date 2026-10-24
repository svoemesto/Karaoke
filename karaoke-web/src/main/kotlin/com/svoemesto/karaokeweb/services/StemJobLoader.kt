package com.svoemesto.karaokeweb.services

import com.svoemesto.karaokeweb.WORKING_DATABASE

import com.svoemesto.karaokeapp.model.StemJob
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient
import org.springframework.stereotype.Component

/**
 * Загрузка задания «Создать минусовку» по идентификатору — узкое место, через которое проходит
 * проверка доступа к приватным объектам (PublicStemJobController.download).
 *
 * Вынесено в отдельный компонент, чтобы путь проверки права (владелец задания → статус → TTL →
 * наличие файла) был проверяем тестом без БД: в тесте подставляется подделка, в проде — БД.
 *
 * @see specs/561-impl-stem-bucket/tasks.md
 */
fun interface StemJobLoader {
    /** Задание по идентификатору либо `null` (нет такого задания). */
    fun load(id: Long): StemJob?
}

/**
 * Реализация загрузки задания из БД (прод-БД, `WORKING_DATABASE`).
 */
@Component
class DbStemJobLoader(
    private val storageService: KaraokeStorageService,
    private val storageApiClient: StorageApiClient,
) : StemJobLoader {
    override fun load(id: Long): StemJob? = StemJob.getById(id, WORKING_DATABASE, storageService, storageApiClient)
}
