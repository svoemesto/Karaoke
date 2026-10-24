package com.svoemesto.karaokeweb.controllers

import com.svoemesto.karaokeapp.model.Song
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient
import com.svoemesto.karaokeweb.dto.ZakromaStreamMetricDto
import com.svoemesto.karaokeweb.services.SiteUserResolver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest

/**
 * Регрессионные тесты «до/после» для аудита BR-013 «unpublished-song-leak:public» и
 * BR-015 «unbounded-page-size:public» (OpenProject #378).
 *
 * **BR-013 (SEC).** ДО правки `GET /api/public/song/{id}`, `/song-picture/{id}` и
 * `/song-vk-image/{id}` отдавали ЛЮБУЮ песню по id — без `id_status >= 6` и без проверки тега
 * `SKIP`, тогда как все публичные выборки фильтруют по этим правилам. Анонимный посетитель
 * получал полный текст неопубликованной (черновиковой) песни. ПОСЛЕ правки правило то же, что в
 * `/api/public/songs`: не-редактору неопубликованная/SKIP-песня — 404 (как для несуществующей).
 *
 * **BR-015 (PERF, часть про metrics).** ДО правки `POST /api/public/zakroma/stream/metrics`
 * принимал массив любого размера: один запрос раздувал heap десериализацией и `tbl_events` INSERT'ами.
 * ПОСЛЕ правки батч больше [PublicApiController.MAX_STREAM_METRICS_BATCH] отклоняется с 413 и в
 * БД не пишется.
 */
class PublicApiVisibilityTest {
    private fun controller(): PublicApiController =
        PublicApiController(
            mainController = Mockito.mock(MainController::class.java),
            storageService = Mockito.mock(KaraokeStorageService::class.java),
            storageApiClient = Mockito.mock(StorageApiClient::class.java),
            siteUserResolver = Mockito.mock(SiteUserResolver::class.java),
            minioProxyUrl = "",
        )

    private fun song(
        idStatus: Long,
        tags: String = "",
    ): Song =
        Mockito.mock(Song::class.java).also {
            Mockito.`when`(it.idStatus).thenReturn(idStatus)
            Mockito.`when`(it.tags).thenReturn(tags)
        }

    // ---- BR-013: видимость песни ----

    @Test
    fun `неопубликованная песня не видна публично`() {
        assertFalse(controller().isPubliclyVisible(song(idStatus = 5)))
        assertFalse(controller().isPubliclyVisible(song(idStatus = 0)))
    }

    @Test
    fun `опубликованная песня видна публично`() {
        assertTrue(controller().isPubliclyVisible(song(idStatus = 6)))
        assertTrue(controller().isPubliclyVisible(song(idStatus = 7)))
    }

    @Test
    fun `SKIP-песня не видна публично даже при опубликованном статусе`() {
        assertFalse(controller().isPubliclyVisible(song(idStatus = 6, tags = "SKIP")))
        assertFalse(controller().isPubliclyVisible(song(idStatus = 6, tags = "POPULAR SKIP")))
        assertTrue(controller().isPubliclyVisible(song(idStatus = 6, tags = "POPULAR")))
    }

    // ---- BR-015: ограничение размера батча метрик ----

    @Test
    fun `слишком большой батч метрик отклоняется с 413`() {
        val metrics =
            List(PublicApiController.MAX_STREAM_METRICS_BATCH + 1) {
                Mockito.mock(ZakromaStreamMetricDto::class.java)
            }

        val response =
            controller().zakromaStreamMetrics(
                metrics = metrics,
                anonId = null,
                referrer = null,
                request = MockHttpServletRequest(),
            )

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, response.statusCode)
    }
}
