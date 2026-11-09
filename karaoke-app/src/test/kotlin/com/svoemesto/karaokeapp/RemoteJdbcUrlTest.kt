package com.svoemesto.karaokeapp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * BR-545 / BR-557 (аудит #336, OpenProject #371): явный `sslmode` в JDBC-строке
 * удалённой БД.
 *
 * Контекст: канал до SERVER-БД идёт через L4-`stream`-прокси прод-хоста
 * (`deploy/prod-single-host/nginx.conf`, порт 5433) — по умолчанию БЕЗ TLS.
 * Драйвер PostgreSQL при умолчательном `sslmode=prefer` МОЛЧА деградирует в
 * открытый текст, то есть пароль БД и прод-данные идут через интернет
 * незашифрованными. Поэтому режим TLS должен быть задан ЯВНО и переключаться
 * одной переменной окружения (`DB_REMOTE_SSL_MODE`).
 */
class RemoteJdbcUrlTest {
    @Test
    fun `default mode is prefer and is written explicitly`() {
        val url = buildRemoteJdbcUrl("188.127.240.124", "5433", "prefer")
        assertTrue(url.contains("sslmode=prefer"), "ожидался явный sslmode=prefer в $url")
        assertTrue(url.startsWith("jdbc:postgresql://188.127.240.124:5433/karaoke?"))
    }

    @Test
    fun `require mode is written explicitly`() {
        val url = buildRemoteJdbcUrl("127.0.0.1", "15433", "require")
        assertTrue(url.contains("sslmode=require"), "ожидался sslmode=require в $url")
    }

    @Test
    fun `verify-full mode is written explicitly`() {
        val url = buildRemoteJdbcUrl("db.prod", "5432", "verify-full")
        assertTrue(url.contains("sslmode=verify-full"), "ожидался sslmode=verify-full в $url")
    }

    @Test
    fun `empty mode does not add sslmode parameter`() {
        val url = buildRemoteJdbcUrl("db.prod", "5432", "   ")
        assertFalse(url.contains("sslmode="), "пустой режим не должен добавлять параметр: $url")
        assertEquals(
            "jdbc:postgresql://db.prod:5432/karaoke?currentSchema=public&socketTimeout=30&loginTimeout=10",
            url,
        )
    }

    @Test
    fun `existing query parameters are preserved`() {
        val url = buildRemoteJdbcUrl("db.prod", "5432", "require")
        assertTrue(url.contains("currentSchema=public"))
        assertTrue(url.contains("socketTimeout=30"))
        assertTrue(url.contains("loginTimeout=10"))
    }

    // Спека 733 (FR-011, FR-012): клиентский сертификат для mTLS-доступа к
    // прод-БД. Сертификат сервера проверяется ПОЛНОСТЬЮ (verify-full) — цепочка
    // и имя сервера, а не только шифрование канала.

    @Test
    fun `параметры клиентского сертификата добавляются в URL`() {
        val url =
            buildRemoteJdbcUrl(
                "10.77.0.2",
                "5433",
                "verify-full",
                "/run/karaoke/tunnel/db-client.crt",
                "/run/karaoke/tunnel/db-client.key",
                "/run/karaoke/tunnel/ca.crt",
            )
        assertTrue(url.contains("sslmode=verify-full"), "ожидался verify-full: $url")
        assertTrue(url.contains("sslcert=/run/karaoke/tunnel/db-client.crt"), "ожидался sslcert: $url")
        assertTrue(url.contains("sslkey=/run/karaoke/tunnel/db-client.key"), "ожидался sslkey: $url")
        assertTrue(url.contains("sslrootcert=/run/karaoke/tunnel/ca.crt"), "ожидался sslrootcert: $url")
    }

    @Test
    fun `пустые параметры сертификата не добавляются`() {
        val url = buildRemoteJdbcUrl("db.prod", "5432", "require", "  ", "", "")
        assertFalse(url.contains("sslcert="), "пустой sslcert не должен добавляться: $url")
        assertFalse(url.contains("sslkey="), "пустой sslkey не должен добавляться: $url")
        assertFalse(url.contains("sslrootcert="), "пустой sslrootcert не должен добавляться: $url")
    }

    @Test
    fun `без режима TLS и сертификатов URL не меняется`() {
        val url = buildRemoteJdbcUrl("db.prod", "5432", "   ", "", "", "")
        assertEquals(
            "jdbc:postgresql://db.prod:5432/karaoke?currentSchema=public&socketTimeout=30&loginTimeout=10",
            url,
        )
    }
}
