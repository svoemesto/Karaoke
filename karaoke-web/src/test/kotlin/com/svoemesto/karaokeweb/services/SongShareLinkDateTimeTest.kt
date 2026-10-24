package com.svoemesto.karaokeweb.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone

/**
 * Золотые числа для тестов — `quickstart.md` §3.
 *
 * Naive timestamp в МСК (`tbl_song_share_links.expires_at = '2026-08-11 09:57:36'`)
 * соответствует epoch ms `1786431456000` — это и есть «реальный момент». Эти числа
 * НЕ зависят от TZ JVM и НЕ зависят от TZ Postgres: `LocalDateTime.ofInstant(..., Europe/Moscow)`
 * алгоритмически переводит ms в календарное МСК-время.
 */
class SongShareLinkDateTimeTest {
    companion object {
        /** TZ, под которой функция обязана считать МСК-временем, а не местным временем JVM. */
        private const val NON_MOSCOW_ZONE = "America/New_York"
    }

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
        // — поэтому должна работать корректно даже если JVM TZ = America/New_York (FR-014).
        //
        // BR-513: прежняя версия этого теста НЕ меняла системную TZ — она декларировала
        // инвариант, но не проверяла его: при фиксированной TZ процесса (Europe/Moscow в CI)
        // assert проходил бы и после регресса `ZoneId.systemDefault()` внутри функции.
        // Теперь TZ процесса принудительно меняется (и восстанавливается в finally), поэтому
        // подмена явной зоны на системную ломает тест.
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(NON_MOSCOW_ZONE))
            // Самопроверка: тест обязан действительно менять TZ JVM, иначе он ничего не проверяет.
            assertEquals(NON_MOSCOW_ZONE, ZoneId.systemDefault().id, "the JVM default TZ must actually be changed by this test")

            val ldt = toMskLocalDateTime(1786431456000L)
            assertEquals(
                LocalDateTime.of(2026, 8, 11, 9, 57, 36),
                ldt,
                "epoch ms must be converted to the Moscow wall clock regardless of the JVM default TZ",
            )

            // Контроль: в America/New_York тот же момент — 02:57:36, т.е. `ZoneId.systemDefault()`
            // дал бы другой час. Если это перестанет выполняться, тест теряет смысл.
            val systemDefaultHour =
                LocalDateTime.ofInstant(Instant.ofEpochMilli(1786431456000L), ZoneId.systemDefault()).hour
            assertEquals(2, systemDefaultHour, "control check: the same epoch ms is 02:57 in $NON_MOSCOW_ZONE")
        } finally {
            TimeZone.setDefault(original)
        }
    }
}
