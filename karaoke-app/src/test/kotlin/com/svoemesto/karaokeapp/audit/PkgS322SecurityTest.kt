package com.svoemesto.karaokeapp.audit

import com.svoemesto.karaokeapp.config.AdminApiTokenFilter
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.never

/**
 * Регрессия BR-114 (аудит #336, пакет PKG-S3-22): admin-API больше не «дыра по построению».
 *
 * **До правки:** в приложении не было ни одного механизма, различающего анонимный и
 * админский запрос (`SecurityConfig`: `authenticated()` только для несуществующего
 * `/api/private/…`, всё остальное `permitAll()`). Тесты ниже на старом коде не
 * компилируются — класса `AdminApiTokenFilter` не существовало.
 *
 * **После правки:** при заданном `karaoke.security.admin-token` admin-эндпоинты требуют
 * заголовок `X-Admin-Token`; без токена поведение прежнее, но в лог уходит WARN.
 *
 * @see com.svoemesto.karaokeapp.config.AdminApiTokenFilter
 * @see specs/394-pkg-s3-22/report.md
 */
class PkgS322SecurityTest {
    private val chain: FilterChain = mock(FilterChain::class.java)

    private fun request(
        uri: String,
        token: String? = null,
    ): HttpServletRequest {
        val request = mock(HttpServletRequest::class.java)
        Mockito.`when`(request.requestURI).thenReturn(uri)
        Mockito.`when`(request.method).thenReturn("POST")
        Mockito.`when`(request.getHeader(AdminApiTokenFilter.TOKEN_HEADER)).thenReturn(token)
        return request
    }

    @Test
    fun `BR-114 admin-эндпоинт без токена получает 401 и не доходит до контроллера`() {
        val filter = AdminApiTokenFilter(adminToken = "s3cr3t")
        val response = mock(HttpServletResponse::class.java)

        filter.doFilterInternal(request("/api/admin/processes/7/delete"), response, chain)

        Mockito.verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED, "Требуется заголовок X-Admin-Token")
        Mockito.verify(chain, never()).doFilter(Mockito.any(), Mockito.any())
    }

    @Test
    fun `BR-114 admin-эндпоинт с верным токеном проходит`() {
        val filter = AdminApiTokenFilter(adminToken = "s3cr3t")
        val response = mock(HttpServletResponse::class.java)

        filter.doFilterInternal(request("/api/cleanup/apply", token = "s3cr3t"), response, chain)

        Mockito.verify(chain).doFilter(Mockito.any(), Mockito.any())
        Mockito.verify(response, never()).sendError(Mockito.anyInt(), Mockito.anyString())
    }

    @Test
    fun `BR-114 неверный токен отклоняется`() {
        val filter = AdminApiTokenFilter(adminToken = "s3cr3t")
        val response = mock(HttpServletResponse::class.java)

        filter.doFilterInternal(request("/api/dictionaries/create", token = "not-the-token"), response, chain)

        Mockito.verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED, "Требуется заголовок X-Admin-Token")
        Mockito.verify(chain, never()).doFilter(Mockito.any(), Mockito.any())
    }

    @Test
    fun `BR-114 публичные эндпоинты не требуют токена`() {
        val filter = AdminApiTokenFilter(adminToken = "s3cr3t")
        val response = mock(HttpServletResponse::class.java)

        for (uri in listOf("/api/songsdigests", "/api/processesdigests", "/api/song/playlyrics")) {
            val single = mock(FilterChain::class.java)
            assertFalse(AdminApiTokenFilter.isProtectedPath(uri), "публичный путь не должен фильтроваться: $uri")
            // Через публичный doFilter: только он применяет shouldNotFilter (проверка пути).
            filter.doFilter(request(uri), response, single)
            Mockito.verify(single).doFilter(Mockito.any(), Mockito.any())
        }
        Mockito.verify(response, never()).sendError(Mockito.anyInt(), Mockito.anyString())
    }

    @Test
    fun `BR-114 без настроенного токена поведение прежнее, но периметр виден в логе`() {
        // Осознанно: включение аутентификации — решение владельца (нет механизма входа).
        val filter = AdminApiTokenFilter(adminToken = "")
        val response = mock(HttpServletResponse::class.java)

        filter.doFilterInternal(request("/api/admin/processes/7/delete"), response, chain)

        Mockito.verify(chain).doFilter(Mockito.any(), Mockito.any())
        Mockito.verify(response, never()).sendError(Mockito.anyInt(), Mockito.anyString())
    }

    @Test
    fun `BR-114 защищаемый периметр покрывает перечисленные в карточке admin-контроллеры`() {
        val protected =
            listOf(
                "/api/admin/processes/1/edit",
                "/api/health/cache/refresh",
                "/api/health/circuit-breaker/reset",
                "/api/monitor/resolve",
                "/api/cleanup/plan",
                "/api/dictionaries/update",
                "/api/news/create",
                "/api/promorules/create",
                "/api/publicsettings/update",
                "/api/sharelinks/create",
                "/api/stemjobs/create",
            )
        protected.forEach {
            assertTrue(AdminApiTokenFilter.isProtectedPath(it), "должен защищаться: $it")
        }
        assertFalse(AdminApiTokenFilter.isProtectedPath("/api/songsdigests"))
    }

    @Test
    fun `BR-114 сравнение токена не падает на null и не пропускает пустой заголовок`() {
        assertFalse(AdminApiTokenFilter.tokenMatches("s3cr3t", null))
        assertFalse(AdminApiTokenFilter.tokenMatches("s3cr3t", ""))
        assertFalse(AdminApiTokenFilter.tokenMatches("s3cr3t", "s3cr3t "))
        assertTrue(AdminApiTokenFilter.tokenMatches("s3cr3t", "s3cr3t"))
    }
}
