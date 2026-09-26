package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.services.PollingCache
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Офлайн-тесты [PollingCache] — санкционированного TTL-кеша
 * (`knowledge/domains/caching/components/caching-patterns.md`: новые кэши обязаны
 * следовать существующему паттерну, ad-hoc запрещены).
 *
 * Pass 456: класс перенесён в `karaoke-app` из `karaoke-web` (направление
 * зависимостей Gradle не позволяло ядру пользоваться копией в web — это было
 * зафиксировано как known gap в `web-caches.md`). Тогда же добавлен `shouldCache`
 * и исправлен расчёт `expiresAt`.
 */
internal class PollingCacheTest {
    @Test
    fun `в пределах TTL loader вызывается один раз`() {
        val cache = PollingCache<String>()
        var calls = 0
        val loader: () -> String = {
            calls++
            "v$calls"
        }
        assertEquals("v1", cache.getOrCompute("k", ttlSeconds = 300, loader = loader))
        assertEquals("v1", cache.getOrCompute("k", ttlSeconds = 300, loader = loader))
        assertEquals(1, calls, "второй вызов должен был попасть в кэш")
    }

    @Test
    fun `ttlSeconds = 0 означает отсутствие кэширования (запись сразу просрочена)`() {
        // На этой семантике держится настройка vpnCheckCacheTtlSeconds <= 0
        // («кэш выключен»), поэтому она зафиксирована тестом.
        val cache = PollingCache<String>()
        var calls = 0
        val loader: () -> String = {
            calls++
            "v$calls"
        }
        assertEquals("v1", cache.getOrCompute("k", ttlSeconds = 0, loader = loader))
        assertEquals("v2", cache.getOrCompute("k", ttlSeconds = 0, loader = loader))
        assertEquals(2, calls)
    }

    @Test
    fun `shouldCache = false не сохраняет результат — loader зовётся каждый раз`() {
        // Мотивирующий случай: неудача детекта ВПН не должна кэшироваться,
        // иначе fail-open «залипнет» на весь TTL.
        val cache = PollingCache<String>()
        var calls = 0
        val loader: () -> String = {
            calls++
            "v$calls"
        }
        assertEquals("v1", cache.getOrCompute("k", ttlSeconds = 300, shouldCache = { false }, loader = loader))
        assertEquals("v2", cache.getOrCompute("k", ttlSeconds = 300, shouldCache = { false }, loader = loader))
        assertEquals(2, calls, "при shouldCache=false значение не должно оставаться в кэше")
        assertEquals(0, cache.size(), "в кэш ничего не записано")
    }

    @Test
    fun `shouldCache решает по значению — успех кэшируется, неудача нет`() {
        // Ровно так устроен детект ВПН: пустая строка = «страну определить не удалось».
        val cache = PollingCache<String>()
        var calls = 0
        val loader: () -> String = {
            calls++
            if (calls == 1) "" else "RU"
        }
        val shouldCache: (String) -> Boolean = { it.isNotEmpty() }
        assertEquals("", cache.getOrCompute("k", ttlSeconds = 300, shouldCache = shouldCache, loader = loader))
        assertEquals(0, cache.size(), "неудача не сохранена")
        assertEquals("RU", cache.getOrCompute("k", ttlSeconds = 300, shouldCache = shouldCache, loader = loader))
        assertEquals(1, cache.size(), "успех сохранён")
        assertEquals("RU", cache.getOrCompute("k", ttlSeconds = 300, shouldCache = shouldCache, loader = loader))
        assertEquals(2, calls, "третий вызов — попадание в кэш")
    }

    @Test
    fun `ttl отсчитывается от завершения loader'а, а не от его старта`() {
        // Регресс-тест: до Pass 456 `now` снимался ДО вызова loader'а, поэтому
        // медленный loader съедал часть TTL (у детекта ВПН — до 5+5 с на сервис).
        val cache = PollingCache<String>()
        var calls = 0
        val loader: () -> String = {
            calls++
            Thread.sleep(1_100)
            "value"
        }
        assertEquals("value", cache.getOrCompute("k", ttlSeconds = 1, loader = loader))
        // Считать expiresAt от старта — запись была бы уже просрочена (1 с прошла
        // за время loader'а), и это был бы второй вызов.
        assertEquals("value", cache.getOrCompute("k", ttlSeconds = 1, loader = loader))
        assertEquals(1, calls, "TTL должен отсчитываться от завершения loader'а")
    }

    @Test
    fun `разные ключи кэшируются независимо`() {
        val cache = PollingCache<String>()
        assertEquals("a", cache.getOrCompute("k1", ttlSeconds = 300) { "a" })
        assertEquals("b", cache.getOrCompute("k2", ttlSeconds = 300) { "b" })
        assertEquals(2, cache.size())
        assertEquals("a", cache.getOrCompute("k1", ttlSeconds = 300) { "changed" })
    }

    @Test
    fun `clear очищает кэш`() {
        val cache = PollingCache<String>()
        cache.getOrCompute("k", ttlSeconds = 300) { "v" }
        assertEquals(1, cache.size())
        cache.clear()
        assertEquals(0, cache.size())
    }
}
