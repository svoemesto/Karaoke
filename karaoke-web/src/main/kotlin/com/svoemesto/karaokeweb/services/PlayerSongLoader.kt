package com.svoemesto.karaokeweb.services

import com.svoemesto.karaokeweb.WORKING_DATABASE

import com.svoemesto.karaokeapp.model.Song
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient
import org.springframework.stereotype.Service

/**
 * Загрузка песни для публичного онлайн-плеера.
 *
 * Отдельный сервис — чтобы контроллер плеера не ходил в БД напрямую через статический
 * [Song.loadFromDbById]: так путь «песня → право → байты» можно проверить тестом без БД
 * (спека 557, OpenProject #358 — регрессионный тест «до/после»).
 */
@Service
class PlayerSongLoader(
    private val storageService: KaraokeStorageService,
    private val storageApiClient: StorageApiClient,
) {
    /** Песня по id или null, если такой песни нет. */
    fun load(id: Long): Song? =
        Song.loadFromDbById(
            id,
            WORKING_DATABASE,
            storageService = storageService,
            storageApiClient = storageApiClient,
        )
}
