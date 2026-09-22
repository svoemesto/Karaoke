package com.svoemesto.karaokeapp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Офлайн-тесты логики home-стран для определения ВПН (Pass 427, #151).
 *
 * Проверяется чистая функция [parseHomeCountries] и правило «страна не в списке
 * home-стран ⇒ ВПН», без сети/БД (сетевой резолв страны — в isVpnActive, не здесь).
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
}
