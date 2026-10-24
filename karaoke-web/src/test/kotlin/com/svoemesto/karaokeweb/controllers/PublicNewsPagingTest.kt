package com.svoemesto.karaokeweb.controllers

import com.svoemesto.karaokeweb.services.SiteUserResolver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito

/**
 * Регрессионный тест «до/после» для аудита BR-015 «unbounded-page-size:public» (OpenProject #378).
 *
 * **ДО правки** `GET /api/public/news?page=&size=` передавал клиентские значения прямо в
 * `LIMIT ?`/`OFFSET ?`: `?size=100000` вытягивал всю `tbl_news` (19k+ строк с телами новостей,
 * 3.5+ MB JSON — инцидент Pass 52), а отрицательный `page` давал отрицательный offset (ошибка SQL).
 *
 * **ПОСЛЕ правки** `size` ограничен диапазоном [1, MAX_PAGE_SIZE = 100], `page` — неотрицателен;
 * offset считается в `Long` (нет переполнения int на больших page*size).
 */
class PublicNewsPagingTest {
    private fun controller(): PublicNewsController = PublicNewsController(Mockito.mock(SiteUserResolver::class.java))

    @Test
    fun `слишком большой size обрезается до максимума`() {
        assertEquals(0 to PublicNewsController.MAX_PAGE_SIZE, controller().normalizePaging(page = 0, size = 100_000))
        assertEquals(5 to 20, controller().normalizePaging(page = 5, size = 20))
    }

    @Test
    fun `size не может быть нулевым или отрицательным`() {
        assertEquals(0 to 1, controller().normalizePaging(page = 0, size = 0))
        assertEquals(0 to 1, controller().normalizePaging(page = 0, size = -50))
    }

    @Test
    fun `отрицательная страница приводится к нулю`() {
        assertEquals(0 to 20, controller().normalizePaging(page = -3, size = 20))
    }

    @Test
    fun `страница и размер в допустимых границах не меняются`() {
        assertEquals(7 to 20, controller().normalizePaging(page = 7, size = 20))
        assertEquals(0 to 100, controller().normalizePaging(page = 0, size = 100))
    }
}
