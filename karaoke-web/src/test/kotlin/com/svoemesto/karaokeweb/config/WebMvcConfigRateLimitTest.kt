package com.svoemesto.karaokeweb.config

import com.svoemesto.karaokeweb.services.KaraokeProperties
import com.svoemesto.karaokeweb.services.RateLimitInterceptor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.web.servlet.config.annotation.InterceptorRegistry

/**
 * Регрессионный тест «до/после» для аудита BR-004 «shared-singleton-interceptor:rate-limit»
 * (OpenProject #378).
 *
 * **ДО правки** `WebMvcConfig` инжектил ОДИН singleton-бин `RateLimitInterceptor` и дважды
 * мутировал его поля `endpointName`/`limitPerMinute` перед регистрацией. Второй `apply`
 * перетирал настройки первого, поэтому:
 *  - оба публичных endpoint'а (префиксы `/api/public/song-picture/` и `/api/public/song-vk-image/`)
 *    работали с `endpointName = "song-vk-image"` и лимитом vk-image;
 *  - bucket'ы двух endpoint'ов были ОБЩИМИ (`ip|song-vk-image`) — исчерпание лимита на картинках
 *    песен давало 429 и на vk-image, и наоборот.
 *
 * **ПОСЛЕ правки** регистрируются два независимых экземпляра: имя endpoint'а и лимит —
 * конструкторные (immutable), состояние у каждого своё.
 */
class WebMvcConfigRateLimitTest {
    private fun properties(songPictureLimit: Int, songVkImageLimit: Int): KaraokeProperties =
        KaraokeProperties().also {
            ReflectionTestUtils.setField(it, "rateLimitSongPicturePerMinuteInternal", songPictureLimit)
            ReflectionTestUtils.setField(it, "rateLimitSongVkImagePerMinuteInternal", songVkImageLimit)
        }

    private fun config(props: KaraokeProperties): WebMvcConfig =
        WebMvcConfig(
            siteAuthInterceptor = Mockito.mock(SiteAuthInterceptor::class.java),
            properties = props,
        )

    /**
     * Достаёт зарегистрированные interceptor'ы из [InterceptorRegistry].
     *
     * `InterceptorRegistry.getInterceptors()` — protected-метод Spring, поэтому здесь reflection:
     * тесту важно увидеть РЕАЛЬНО зарегистрированные объекты, а не то, что конфиг «должен был»
     * зарегистрировать.
     */
    private fun registeredInterceptors(registry: InterceptorRegistry): List<Any> {
        val method = InterceptorRegistry::class.java.getDeclaredMethod("getInterceptors")
        method.isAccessible = true
        val raw = (method.invoke(registry) as List<*>).filterNotNull()
        // В зависимости от версии Spring `getInterceptors()` отдаёт либо сами interceptor'ы, либо
        // `InterceptorRegistration`; во втором случае достаём вложенный объект.
        return raw.map { item ->
            if (item is RateLimitInterceptor) {
                item
            } else {
                val getter = item.javaClass.getDeclaredMethod("getInterceptor")
                getter.isAccessible = true
                getter.invoke(item)!!
            }
        }
    }

    private fun request(): MockHttpServletRequest =
        MockHttpServletRequest().apply {
            remoteAddr = "203.0.113.7"
            addHeader("X-Real-IP", "203.0.113.7")
        }

    @Test
    fun `каждый endpoint получает собственный экземпляр интерцептора`() {
        val registry = InterceptorRegistry()
        config(properties(songPictureLimit = 60, songVkImageLimit = 60)).addInterceptors(registry)

        val rateLimiters =
            registeredInterceptors(registry).filterIsInstance<RateLimitInterceptor>()

        assertEquals(2, rateLimiters.size, "должны быть зарегистрированы ДВА интерцептора rate-limit")
        assertNotSame(
            rateLimiters[0],
            rateLimiters[1],
            "это должен быть РАЗНЫЕ объекты: раньше оба паттерна URL ссылались на один бин",
        )
        assertEquals(
            setOf("song-picture", "song-vk-image"),
            rateLimiters.map { it.endpointName }.toSet(),
            "у каждого интерцептора своё имя endpoint'а (ключ bucket'а)",
        )
    }

    @Test
    fun `лимиты разных endpoint'ов не влияют друг на друга`() {
        val registry = InterceptorRegistry()
        config(properties(songPictureLimit = 1, songVkImageLimit = 100)).addInterceptors(registry)

        val rateLimiters =
            registeredInterceptors(registry).filterIsInstance<RateLimitInterceptor>()
        val picture = rateLimiters.first { it.endpointName == "song-picture" }
        val vkImage = rateLimiters.first { it.endpointName == "song-vk-image" }

        // Первый запрос к картинке песни — в лимите (1/мин).
        assertTrue(
            picture.preHandle(request(), MockHttpServletResponse(), Any()),
            "первый запрос к song-picture должен проходить",
        )

        // Второй запрос к картинке песни — лимит исчерпан (429).
        val pictureResponse = MockHttpServletResponse()
        assertFalse(
            picture.preHandle(request(), pictureResponse, Any()),
            "второй запрос к song-picture превышает лимит 1/мин",
        )
        assertEquals(429, pictureResponse.status)

        // Тот же IP к ДРУГОМУ endpoint'у — свой bucket, чужой лимит не применяется.
        val vkResponse = MockHttpServletResponse()
        assertTrue(
            vkImage.preHandle(request(), vkResponse, Any()),
            "исчерпание лимита song-picture не должно блокировать song-vk-image",
        )
        assertEquals(200, vkResponse.status)
    }
}
