package com.svoemesto.karaokeapp.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

/**
 * Чистая функция резолвинга [SongState] по готовности, бесплатности, расписанию эфира и
 * фиксированному моменту времени. Вынесена из [Song] в top-level, чтобы офлайн-тесты могли
 * проверять приоритеты без поднятия Spring-контекста / БД / `KSS_APP`.
 *
 * Приоритеты (зафиксированы в `specs/155-song-state-colors/spec.md`, FR-002..FR-006 +
 * `specs/369-free-after-onair-flag/spec.md`, clarification 2026-09-11):
 *  1. `idStatus < 6` → [SongState.IN_WORK] (пустой цвет, старый fallback по idStatus отключён).
 *  2. `free=true` или активное бесплатное окно → [SongState.ON_AIR] (`#33FF33`).
 *  3. Нет действительного [dateTimePublish] (null/пустая пара/битая пара) →
 *     [SongState.EXCLUSIVE] (`#99CCFF`).
 *  4. Дата эфира — сегодня в Москве, момент ещё не наступил → [SongState.TODAY] (`#FFFF00`),
 *     приоритет над [SongState.DONE].
 *  5. Иначе при наличии действительного расписания → [SongState.DONE] (`#CCFFCC`).
 *
 * С `specs/369-free-after-onair-flag` (Pass 369): добавлен параметр [freeAfterOnAir]
 * — если флаг установлен И эфир уже наступил ([dateTimePublish] ≤ [now]) — песня остаётся
 * в эфире навсегда ([SongState.ON_AIR]), даже после окончания стандартного бесплатного
 * окна. Приоритет: `free=true` > `freeAfterOnAir=true` (т.е. оба флага независимы,
 * `free=true` имеет преимущество).
 */
object SongStateResolver {
    fun resolve(
        idStatus: Long,
        free: Boolean,
        dateTimePublish: Date?,
        now: Date,
        freeAccessWindowMonths: Int = 1,
        freeAfterOnAir: Boolean = false,
    ): SongState {
        if (idStatus < 6L) return SongState.IN_WORK
        if (free) return SongState.ON_AIR
        // specs/369-free-after-onair-flag: флаг «не снимать с эфира» — после наступления
        // эфира песня остаётся публично доступной, не уходит в DONE/TODAY/EXCLUSIVE по
        // таймеру стандартного окна. Действует только если dateTimePublish задан и эфир
        // уже наступил (см. clarification 2026-09-11 — Q2: «только после эфира»).
        if (freeAfterOnAir) {
            val dtFaA = dateTimePublish
            if (dtFaA != null && !dtFaA.after(now)) {
                return SongState.ON_AIR
            }
        }
        val dt = dateTimePublish ?: return SongState.EXCLUSIVE
        // Эфир наступил или прямо сейчас — мы внутри бесплатного окна → ON_AIR
        // (имеет приоритет над TODAY/DONE — даже в день эфира песня уже «доступна бесплатно»).
        if (!dt.after(now) && isFreelyAvailableNowAt(dt, now, freeAccessWindowMonths)) {
            return SongState.ON_AIR
        }
        val moscow = TimeZone.getTimeZone("Europe/Moscow")
        val dayFmt = SimpleDateFormat("yyyyMMdd").apply { timeZone = moscow }
        val today = dayFmt.format(now)
        val publishDay = dayFmt.format(dt)
        if (publishDay == today && dt.after(now)) return SongState.TODAY
        return SongState.DONE
    }

    private fun isFreelyAvailableNowAt(
        publish: Date,
        now: Date,
        freeAccessWindowMonths: Int,
    ): Boolean {
        if (publish.after(now)) return false
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Europe/Moscow"))
        cal.time = publish
        cal.add(Calendar.MONTH, freeAccessWindowMonths)
        return now.before(cal.time)
    }
}
