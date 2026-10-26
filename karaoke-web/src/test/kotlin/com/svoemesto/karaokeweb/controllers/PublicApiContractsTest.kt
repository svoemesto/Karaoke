package com.svoemesto.karaokeweb.controllers

import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.model.Zakroma
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeweb.services.PaymentService
import com.svoemesto.karaokeweb.services.PriceService
import com.svoemesto.karaokeapp.services.StorageApiClient
import com.svoemesto.karaokeweb.dto.ZakromaPublicDto
import com.svoemesto.karaokeweb.util.TestRepoRoot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.http.HttpStatus

/**
 * Контракты публичного API (BR-629) — «до/после» по трём расхождениям, зафиксированным
 * владельцем 2026-10-26.
 *
 * **1. Флаг SKIP автора (`authorSkip`) — REST против NDJSON.** Поток отдавал `authorSkip`
 * только тем, у кого есть право `canWorkWithSkipped` (иначе `null`), а REST-версия
 * отдавала флаг всем — два представления одних и тех же данных расходились, и обычный
 * посетитель получал сведения о скрытом авторе. ПОСЛЕ правки оба пути ведут себя
 * одинаково: `null` тем, кому флаг не положен.
 *
 * **2. Публичный список тарифов и `scope`.** `GET /api/public/account/subscription/tariffs`
 * принимал любую строку в `scope` и на неизвестный scope отдавал пустой список — фронт
 * показывал «Тарифы пока не настроены», то есть ошибка запроса выглядела как отсутствие
 * тарифов. ПОСЛЕ правки `scope` — закрытый список (`SONG`, `SITE`), неизвестное значение —
 * явная ошибка 400 со списком допустимых.
 *
 * **3. `SongPublicDto.skipped`.** Отдельного поля не вводим: состояние SKIP песни уже
 * публикуется полем `contentRemoved` (тег SKIP в тегах песни), и фронт на этом поле
 * строит и «снято»-состояние, и бейдж. Второе поле с другим правилом видимости
 * (null для обычного посетителя) развело бы один факт на два.
 */
class PublicApiContractsTest {
    private fun zakromaWithSkip(skip: Boolean): Zakroma =
        Zakroma(database = Mockito.mock(KaraokeConnection::class.java)).also {
            it.authorSkip = skip
        }

    @Test
    fun `флаг SKIP автора не раскрывается тем, кому не положен`() {
        val hidden = ZakromaPublicDto.fromZakroma(listOf(zakromaWithSkip(true)), canSeeSkipped = false).single()
        assertNull(hidden.authorSkip, "обычному посетителю флаг SKIP автора не показывается")

        val visible = ZakromaPublicDto.fromZakroma(listOf(zakromaWithSkip(true)), canSeeSkipped = true).single()
        assertEquals(true, visible.authorSkip, "с правом canWorkWithSkipped флаг виден")
    }

    @Test
    fun `REST и NDJSON отдают флаг SKIP автора одинаково`() {
        // NDJSON-стрим отдаёт `authorSkip = if (canSeeSkipped) authorMeta.skip else null`
        // (PublicApiController, zakroma-стрим). REST-путь обязан вести себя так же.
        val text =
            TestRepoRoot
                .file("karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt")
                .readText()
        assertTrue(
            text.contains("authorSkip = if (canSeeSkipped) authorMeta.skip else null"),
            "NDJSON-стрим должен прятать флаг SKIP от тех, кому он не положен",
        )
        assertTrue(
            text.contains("ZakromaPublicDto.fromZakroma(zakroma, canSeeSkipped)"),
            "REST-путь должен применять то же правило, что и поток",
        )
    }

    @Test
    fun `неизвестный scope тарифов — явная ошибка со списком допустимых значений`() {
        val controller =
            PublicSubscriptionController(
                storageService = Mockito.mock(KaraokeStorageService::class.java),
                storageApiClient = Mockito.mock(StorageApiClient::class.java),
                priceService = Mockito.mock(PriceService::class.java),
                paymentService = Mockito.mock(PaymentService::class.java),
                publicSiteUrl = "",
            )

        val response = controller.tariffs("НЕТ-ТАКОГО")
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        val body = response.body as Map<*, *>
        assertEquals("unknown_scope", body["error"])
        val allowed = body["allowedScopes"] as List<*>
        assertTrue(allowed.contains("SONG"), "в списке допустимых должен быть SONG: $allowed")
        assertTrue(allowed.contains("SITE"), "в списке допустимых должен быть SITE: $allowed")
    }

    @Test
    fun `допустимые scope тарифов — из модели, а не отдельным списком`() {
        assertEquals(
            listOf(
                com.svoemesto.karaokeapp.model.PriceTariff.SCOPE_SONG,
                com.svoemesto.karaokeapp.model.PriceTariff.SCOPE_SITE,
            ),
            PublicSubscriptionController.TARIFF_SCOPES,
            "список scope должен совпадать с моделью, иначе контракт разойдётся с данными",
        )
    }
}
