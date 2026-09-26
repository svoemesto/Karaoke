package com.svoemesto.karaokeapp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Офлайн-тесты логики home-стран для определения ВПН (Pass 427, #151) и разбора
 * ответов сервисов определения страны (Pass 454).
 *
 * Проверяются чистые функции: [parseHomeCountries], правило «страна не в списке
 * home-стран ⇒ ВПН» и [extractCountryCode]. Сеть не трогается: сетевой резолв
 * живёт в isVpnActive/resolveCurrentCountryCode.
 *
 * TTL-логика кэша страны сюда не входит: она принадлежит [PollingCache] и
 * проверяется в `PollingCacheTest` (Pass 456, до этого кэш был ad-hoc и тесты
 * на него жили здесь).
 */
class VpnHomeCountryTest {
    private fun isVpn(country: String, homeRaw: String): Boolean {
        val home = parseHomeCountries(homeRaw)
        return home.isNotEmpty() && country.uppercase() !in home
    }

    @Test
    fun `parses comma-separated list`() {
        assertEquals(setOf("DE", "RU"), parseHomeCountries("DE,RU"))
    }

    @Test
    fun `parses whitespace-and-semicolon-separated and lowercase`() {
        assertEquals(setOf("DE", "RU"), parseHomeCountries("de ru"))
        assertEquals(setOf("DE", "RU"), parseHomeCountries("DE;RU"))
        assertEquals(setOf("DE", "RU"), parseHomeCountries("  de , ; ru  "))
    }

    @Test
    fun `empty and blank produce empty set`() {
        assertTrue(parseHomeCountries("").isEmpty())
        assertTrue(parseHomeCountries("  , ;  ").isEmpty())
    }

    @Test
    fun `single legacy value stays one country`() {
        assertEquals(setOf("RU"), parseHomeCountries("RU"))
    }

    @Test
    fun `DE and RU both count as home when listed`() {
        assertFalse(isVpn("DE", "DE,RU"), "DE must be home")
        assertFalse(isVpn("RU", "DE,RU"), "RU must be home")
        assertFalse(isVpn("ru", "DE,RU"), "case-insensitive")
    }

    @Test
    fun `third country counts as VPN`() {
        assertTrue(isVpn("NL", "DE,RU"))
    }

    @Test
    fun `legacy single-RU behavior unchanged`() {
        assertFalse(isVpn("RU", "RU"))
        assertTrue(isVpn("DE", "RU"))
    }

    @Test
    fun `empty home list fails open (no VPN)`() {
        assertFalse(isVpn("NL", ""), "empty list must not block work")
    }

    @Test
    fun `extractCountryCode reads api country is body`() {
        val (_, regex) = VPN_COUNTRY_SERVICES.first { it.first.startsWith("https://api.country.is") }
        assertEquals("RU", extractCountryCode("""{"ip":"185.26.28.109","country":"RU"}""", regex))
    }

    @Test
    fun `extractCountryCode reads ipapi plain two-letter body`() {
        val (_, regex) = VPN_COUNTRY_SERVICES.first { it.first.startsWith("https://ipapi.co") }
        assertEquals("RU", extractCountryCode("RU", regex))
    }

    @Test
    fun `extractCountryCode rejects real 429 rate-limit body`() {
        // Именно это тело ipapi.co отдал 2026-09-26 при HTTP 429. До проверки статуса
        // оно молча превращалось в «страну определить не удалось» и давало fail-open.
        val (_, regex) = VPN_COUNTRY_SERVICES.first { it.first.startsWith("https://ipapi.co") }
        assertNull(
            extractCountryCode(
                "{'error': True, 'reason': 'RateLimited', 'message': 'Visit https://ipapi.co/ratelimited/ for details'}",
                regex,
            ),
            "тело ошибки не должно распознаваться как код страны",
        )
    }

    @Test
    fun `extractCountryCode returns null on empty body`() {
        for ((_, regex) in VPN_COUNTRY_SERVICES) {
            assertNull(extractCountryCode("", regex))
        }
    }

    @Test
    fun `extractCountryCode does not match lowercase country`() {
        val (_, regex) = VPN_COUNTRY_SERVICES.first { it.first.startsWith("https://api.country.is") }
        assertNull(
            extractCountryCode("""{"country":"ru"}""", regex),
            "сервисы отдают верхний регистр; нижний не должен совпадать",
        )
    }
}
