package com.svoemesto.karaokeapp.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

/**
 * Офлайн-проверка матрицы классификации `SongState`. Использует чистый
 * `SongStateResolver.resolve(...)` без поднятия Spring-контекста / БД / KSS_APP.
 * Время `now` подаётся явно, чтобы изолировать границы дня и бесплатного окна от
 * системных часов.
 *
 * Контракт цветов и приоритетов — `specs/155-song-state-colors/spec.md`,
 * `docs/features/song-state-colors.md`.
 */
class SongStateTest {
    private fun now(
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 12,
        minute: Int = 0,
    ): Date {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Europe/Moscow"))
        cal.set(year, month - 1, day, hour, minute, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.time
    }

    private fun dateTime(
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 12,
        minute: Int = 0,
    ): Date = now(year, month, day, hour, minute)

    @Test
    fun `IN_WORK при status ниже 6 независимо от free`() {
        // Кейс 1: idStatus < 6 + free=true + валидное расписание → IN_WORK (пустой цвет).
        val fixedNow = now(2026, 8, 6, 10, 0)
        val state =
            SongStateResolver.resolve(
                idStatus = 5L,
                free = true,
                dateTimePublish = dateTime(2026, 8, 6, 23, 59),
                now = fixedNow,
            )
        assertEquals(SongState.IN_WORK, state)
        assertEquals("", state.color)
    }

    @Test
    fun `ON_AIR при free без расписания`() {
        // Кейс 2: готов, free=true, date и time отсутствуют → ON_AIR.
        val state =
            SongStateResolver.resolve(
                idStatus = 7L,
                free = true,
                dateTimePublish = null,
                now = now(2026, 8, 6),
            )
        assertEquals(SongState.ON_AIR, state)
        assertEquals("#33FF33", state.color)
    }

    @Test
    fun `TODAY при сегодняшнем будущем эфире`() {
        // Кейс 3: готов, free=false, эфир сегодня в будущем → TODAY.
        val fixedNow = now(2026, 8, 6, 10, 0)
        val state =
            SongStateResolver.resolve(
                idStatus = 6L,
                free = false,
                dateTimePublish = dateTime(2026, 8, 6, 20, 0),
                now = fixedNow,
            )
        assertEquals(SongState.TODAY, state)
        assertEquals("#FFFF00", state.color)
    }

    @Test
    fun `ON_AIR внутри бесплатного окна после эфира`() {
        // Кейс 4: готов, free=false, эфир вчера, окно ещё не вышло → ON_AIR.
        val fixedNow = now(2026, 8, 6, 10, 0)
        val state =
            SongStateResolver.resolve(
                idStatus = 6L,
                free = false,
                dateTimePublish = dateTime(2026, 8, 5, 12, 0),
                now = fixedNow,
            )
        assertEquals(SongState.ON_AIR, state)
        assertEquals("#33FF33", state.color)
    }

    @Test
    fun `DONE после окончания бесплатного окна без free`() {
        // Кейс 5: готов, free=false, эфир сильно в прошлом (окно вышло).
        val fixedNow = now(2026, 8, 6, 10, 0)
        val state =
            SongStateResolver.resolve(
                idStatus = 6L,
                free = false,
                dateTimePublish = dateTime(2026, 1, 1, 12, 0),
                now = fixedNow,
            )
        assertEquals(SongState.DONE, state)
        assertEquals("#CCFFCC", state.color)
    }

    @Test
    fun `DONE при будущем не сегодня эфире`() {
        // Кейс 6: готов, free=false, эфир через неделю → DONE (не TODAY).
        val state =
            SongStateResolver.resolve(
                idStatus = 6L,
                free = false,
                dateTimePublish = dateTime(2026, 8, 13, 12, 0),
                now = now(2026, 8, 6),
            )
        assertEquals(SongState.DONE, state)
        assertEquals("#CCFFCC", state.color)
    }

    @Test
    fun `EXCLUSIVE при отсутствии расписания у готовой не бесплатной`() {
        // Кейс 7: готов, free=false, date/time отсутствуют → EXCLUSIVE.
        val state =
            SongStateResolver.resolve(
                idStatus = 6L,
                free = false,
                dateTimePublish = null,
                now = now(2026, 8, 6),
            )
        assertEquals(SongState.EXCLUSIVE, state)
        assertEquals("#99CCFF", state.color)
    }

    @Test
    fun `EXCLUSIVE пропускается к Song только если dateTimePublish невалиден на стороне геттера`() {
        // Кейс 8 (часть): резолвер работает с уже распарсенным Date?; сценарий, когда date/time
        // неполные или битые, обслуживается геттером `Song.dateTimePublish` (возвращает null
        // при некорректной паре). Здесь проверяем две вещи: null → EXCLUSIVE; эфир сегодня в
        // прошлом — попадает в бесплатное окно, поэтому ON_AIR (а не DONE).
        val atNow = now(2026, 8, 6, 10, 0)
        assertEquals(
            SongState.ON_AIR,
            SongStateResolver.resolve(6L, false, dateTime(2026, 8, 6, 8, 0), atNow),
        )
        assertEquals(SongState.EXCLUSIVE, SongStateResolver.resolve(6L, false, null, atNow))
    }

    @Test
    fun `граница момента эфира — ровно равно now это ON_AIR (окно уже активно)`() {
        // Кейс 9: момент эфира ровно равен фиксированному now → эфир уже наступил,
        // бесплатное окно активно → ON_AIR. За минуту до эфира — TODAY.
        val fixedNow = now(2026, 8, 6, 12, 0)
        val sameInstant = dateTime(2026, 8, 6, 12, 0)
        assertEquals(
            SongState.ON_AIR,
            SongStateResolver.resolve(6L, false, sameInstant, fixedNow),
        )
        val oneMinuteBefore = now(2026, 8, 6, 11, 59)
        assertEquals(
            SongState.TODAY,
            SongStateResolver.resolve(6L, false, sameInstant, oneMinuteBefore),
        )
    }

    @Test
    fun `канонический контракт цвета соответствует contracts-songs-state-color`() {
        val mapping =
            mapOf(
                SongState.DONE to "#CCFFCC",
                SongState.TODAY to "#FFFF00",
                SongState.ON_AIR to "#33FF33",
                SongState.EXCLUSIVE to "#99CCFF",
                SongState.IN_WORK to "",
            )
        mapping.forEach { (state, expected) ->
            assertEquals(expected, state.color, "цвет $state должен быть $expected")
        }
    }

    @Test
    fun `резолвер работает без Spring базы KSS_APP и lateinit глобалов`() {
        // Smoke: чистая функция не должна трогать lateinit (KSS_APP / SAC_APP) и БД —
        // иначе она ломалась бы в офлайн-тестах.
        val state =
            SongStateResolver.resolve(6L, false, null, now(2026, 8, 6))
        assertEquals(SongState.EXCLUSIVE, state)
    }
}
