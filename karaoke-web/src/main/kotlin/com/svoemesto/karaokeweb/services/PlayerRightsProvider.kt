package com.svoemesto.karaokeweb.services

import com.svoemesto.karaokeweb.WORKING_DATABASE

import com.svoemesto.karaokeapp.model.SiteUser
import com.svoemesto.karaokeapp.model.SongAssignment
import com.svoemesto.karaokeapp.model.Subscription
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient
import org.springframework.stereotype.Service

/**
 * Живой источник прав доступа к платному контенту для [PlayerAccessService].
 *
 * Каждый метод перечитывает состояние из БД в момент вызова — никакого кеша и никакой
 * «сверки по факту владения ранее выданным токеном» (FR-002, FR-004). Именно поэтому премиум,
 * истёкший посреди сессии, и отозванная подписка перестают давать доступ сразу, не дожидаясь
 * 30-минутного TTL токена.
 *
 * Отдельный сервис (а не методы в контроллере) — чтобы правило доступа можно было проверить
 * тестами без БД: в тестах подменяется этот источник, а [PlayerAccessService] остаётся
 * настоящим.
 */
@Service
class PlayerRightsProvider(
    private val storageService: KaraokeStorageService,
    private val storageApiClient: StorageApiClient,
) {
    /**
     * Действующий ли премиум у пользователя.
     *
     * Источник истины — [SiteUser.isEffectivePremium]: вечный/ручной грант, спонсорский премиум
     * и оплаченная подписка на сайт. Срок проверяется «здесь и сейчас», поэтому истёкший
     * премиум перестаёт давать доступ немедленно.
     */
    fun isPremium(siteUserId: Long): Boolean {
        if (siteUserId <= 0) return false
        val user =
            SiteUser.getSiteUserById(
                siteUserId,
                WORKING_DATABASE,
                storageService,
                storageApiClient,
            ) ?: return false
        return user.isEffectivePremium
    }

    /**
     * Оформлена ли бессрочная подписка именно на эту песню (scope=SONG).
     *
     * Отдельная ветка доступа от [isPremium]: подписка на одну песню не даёт isEffectivePremium.
     */
    fun isSubscribedToSong(
        siteUserId: Long,
        songId: Long,
    ): Boolean {
        if (siteUserId <= 0 || songId <= 0) return false
        return Subscription.isSubscribedToSong(
            siteUserId,
            songId,
            WORKING_DATABASE,
            storageService,
            storageApiClient,
        )
    }

    /**
     * Владеет ли пользователь заданием редактора на эту песню.
     *
     * Основание доступа к предпросмотру неодобренной разметки — ВЛАДЕНИЕ ЗАДАНИЕМ, а не
     * подписка (FR-009). Проверяется в момент отдачи байт: задание могли отозвать или передать.
     */
    fun isAssignmentOwner(
        assignmentId: Long,
        siteUserId: Long,
        songId: Long,
    ): Boolean {
        if (assignmentId <= 0 || siteUserId <= 0 || songId <= 0) return false
        val assignment =
            SongAssignment.getById(
                assignmentId,
                WORKING_DATABASE,
                storageService,
                storageApiClient,
            ) ?: return false
        return assignment.songId == songId && assignment.assigneeId == siteUserId
    }
}
