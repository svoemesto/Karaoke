package com.svoemesto.karaokeapp.model

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Юнит-тесты семантики фильтров админского раздела «Публикации» (OpenProject #326).
 *
 * Проверяются чистые предикаты [PublicationFilters] — без Spring-контекста и БД:
 * `Song` в тестах не инстанцируется (конструктор требует окружения).
 *
 * @see knowledge/product/features/publications-filters.md
 */
class PublicationFiltersTest {
    @Test
    fun `UNPUBLISH — готово, без даты и времени, не free, не skip`() {
        assertTrue(
            PublicationFilters.isUnpublished(
                idStatus = 6L,
                publishDate = "",
                publishTime = "",
                free = false,
                authorSkipped = false,
                tags = "",
            ),
        )
    }

    @Test
    fun `UNPUBLISH — free исключается`() {
        assertFalse(
            PublicationFilters.isUnpublished(
                idStatus = 6L,
                publishDate = "",
                publishTime = "",
                free = true,
                authorSkipped = false,
                tags = "",
            ),
        )
    }

    @Test
    fun `UNPUBLISH — не готово (статус ниже 6) не попадает`() {
        for (status in 0L..5L) {
            assertFalse(
                PublicationFilters.isUnpublished(
                    idStatus = status,
                    publishDate = "",
                    publishTime = "",
                    free = false,
                    authorSkipped = false,
                    tags = "",
                ),
                "статус $status не должен попадать в UNPUBLISH",
            )
        }
    }

    @Test
    fun `UNPUBLISH — назначенная дата или время исключает песню`() {
        assertFalse(
            PublicationFilters.isUnpublished(
                idStatus = 6L,
                publishDate = "22.10.26",
                publishTime = "",
                free = false,
                authorSkipped = false,
                tags = "",
            ),
        )
        assertFalse(
            PublicationFilters.isUnpublished(
                idStatus = 6L,
                publishDate = "",
                publishTime = "12:00",
                free = false,
                authorSkipped = false,
                tags = "",
            ),
        )
        assertFalse(
            PublicationFilters.isUnpublished(
                idStatus = 6L,
                publishDate = "22.10.26",
                publishTime = "12:00",
                free = false,
                authorSkipped = false,
                tags = "",
            ),
        )
    }

    @Test
    fun `UNPUBLISH — skip-автор и тег SKIP исключаются`() {
        assertFalse(
            PublicationFilters.isUnpublished(
                idStatus = 6L,
                publishDate = "",
                publishTime = "",
                free = false,
                authorSkipped = true,
                tags = "",
            ),
        )
        assertFalse(
            PublicationFilters.isUnpublished(
                idStatus = 6L,
                publishDate = "",
                publishTime = "",
                free = false,
                authorSkipped = false,
                tags = "SKIP",
            ),
        )
    }

    @Test
    fun `UNDONE — все статусы ниже 6, включая 5`() {
        for (status in 0L..5L) {
            assertTrue(
                PublicationFilters.isUndone(
                    idStatus = status,
                    authorSkipped = false,
                    tags = "",
                ),
                "статус $status должен попадать в UNDONE",
            )
        }
    }

    @Test
    fun `UNDONE — статус 6 и выше не попадает`() {
        for (status in 6L..7L) {
            assertFalse(
                PublicationFilters.isUndone(
                    idStatus = status,
                    authorSkipped = false,
                    tags = "",
                ),
                "статус $status не должен попадать в UNDONE",
            )
        }
    }

    @Test
    fun `UNDONE — skip-автор и тег SKIP исключаются`() {
        assertFalse(
            PublicationFilters.isUndone(
                idStatus = 1L,
                authorSkipped = true,
                tags = "",
            ),
        )
        assertFalse(
            PublicationFilters.isUndone(
                idStatus = 5L,
                authorSkipped = false,
                tags = "rock SKIP pop",
            ),
        )
    }

    @Test
    fun `тег SKIP ищется по словам и без учёта регистра`() {
        assertTrue(PublicationFilters.hasSkipTag("SKIP"))
        assertTrue(PublicationFilters.hasSkipTag("skip"))
        assertTrue(PublicationFilters.hasSkipTag("rock SKIP pop"))
        assertTrue(PublicationFilters.hasSkipTag("  SKIP  "))
        assertFalse(PublicationFilters.hasSkipTag(""))
        assertFalse(PublicationFilters.hasSkipTag("SKIPPED"))
        assertFalse(PublicationFilters.hasSkipTag("NO-SKIP"))
    }
}
