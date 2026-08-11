package com.svoemesto.karaokeweb.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

/**
 * Золотые числа для тестов — `quickstart.md` §3.
 *
 * Naive timestamp в МСК (`tbl_song_share_links.expires_at = '2026-08-11 09:57:36'`)
 * соответствует epoch ms `1786431456000` — это и есть «реальный момент». Эти числа
 * НЕ зависят от TZ JVM и НЕ зависят от TZ Postgres: `LocalDateTime.ofInstant(..., Europe/Moscow)`
 * алгоритмически переводит ms в календарное МСК-время.
 */
class SongShareLinkDateTimeTest {
    @Test
    fun `toMskLocalDateTime returns 2026-08-11 09-57-36 for epoch 1786431456000`() {
        val ldt = toMskLocalDateTime(1786431456000L)
        assertEquals(2026, ldt.year, "year")
        assertEquals(8, ldt.monthValue, "month")
        assertEquals(11, ldt.dayOfMonth, "day")
        assertEquals(9, ldt.hour, "hour")
        assertEquals(57, ldt.minute, "minute")
        assertEquals(36, ldt.second, "second")
        assertEquals(LocalDateTime.of(2026, 8, 11, 9, 57, 36), ldt)
    }

    @Test
    fun `toMskLocalDateTime returns 2026-08-11 08-57-36 for epoch 1786427856000`() {
        val ldt = toMskLocalDateTime(1786427856000L)
        assertEquals(LocalDateTime.of(2026, 8, 11, 8, 57, 36), ldt)
    }

    @Test
    fun `difference between expires and created moments is 1 hour for ttl 1h`() {
        val expires = toMskLocalDateTime(1786431456000L)
        val created = toMskLocalDateTime(1786427856000L)
        // Проверяем, что дельта 1 час — это инвариант, на который опирается US2
        // (повторное открытие модалки).
        assertEquals(1, (expires.hour - created.hour + 24) % 24)
        assertEquals(0, (expires.minute - created.minute + 60) % 60)
        // Разница в минутах между моментами — 60 (1 час)
        val diffMin = (expires.toEpochSecond(java.time.ZoneOffset.UTC) - created.toEpochSecond(java.time.ZoneOffset.UTC)) / 60
        assertEquals(60L, diffMin, "diff must be 60 minutes")
    }

    @Test
    fun `toMskLocalDateTime is invariant under JVM default timeZone`() {
        // Логика использует явный ZoneId.of("Europe/Moscow"), не ZoneId.systemDefault()
        // — поэтому работает корректно даже если JVM TZ = America/New_York (FR-014).
        // Этот тест не меняет системную TZ (она контролируется окружением через
        // ENV TZ=Asia/Vladivostok ./gradlew :karaoke-web:test), но проверяет,
        // что функция НЕ читает её из ZoneId.systemDefault().
        val ldt = toMskLocalDateTime(1786431456000L)
        assertEquals(9, ldt.hour, "hour must be the Moscow hour, not the JVM TZ hour")
    }
}
