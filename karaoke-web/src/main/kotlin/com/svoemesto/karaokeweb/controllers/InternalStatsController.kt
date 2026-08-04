package com.svoemesto.karaokeweb.controllers

import com.svoemesto.karaokeweb.StatBySong
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Server-to-server эндпоинт для karaoke-app (specs/143-song-free-access-window) — НЕ проходит
 * через SiteAuthInterceptor, защищён тем же shared-secret заголовком X-Internal-Secret, что и
 * [InternalStemJobController] (значение — `Karaoke.stemJobsInternalSecret` на стороне karaoke-app,
 * `stemjobs.internal-secret` здесь). Секрет переиспользован намеренно — это единый внутренний
 * канал admin↔web, а не отдельный секрет на каждый internal-эндпоинт.
 *
 * karaoke-app вызывает [markDirty] при сохранении песни с изменённым `free`-флагом и при
 * one-click-синхронизации `songs`, доставившей такое изменение (LOCAL→SERVER) — см.
 * `notifyStatsDirty()` в `ApiController.kt` (karaoke-app). Сам пересчёт счётчиков не выполняется
 * здесь синхронно (см. [StatBySong.markDirty]) — только взводится флаг, который подхватывает
 * `StatsCacheScheduler.refreshIfDirty()` в течение минуты. Это устойчиво к задержке
 * LOCAL→SERVER-синхронизации: если данные ещё не доехали до SERVER-БД к моменту вызова, ежеминутный
 * тик просто увидит старое значение один раз и не более — специально не пытаемся дожидаться
 * подтверждения синхронизации, это избыточная сложность ради экономии секунд.
 */

/**
 * Контроллер (HTTP endpoints) для internal stats .
 *
 * @see AGENTS.md
 * @see docs/features/song-free-access.md
 */
@RestController
@RequestMapping("/api/internal/stats")
class InternalStatsController(
    @Value("\${stemjobs.internal-secret:}") private val internalSecret: String,
) {
    private fun authorized(request: HttpServletRequest): Boolean {
        if (internalSecret.isBlank()) return false // не сконфигурировано — по умолчанию закрыто, не открыто
        return request.getHeader("X-Internal-Secret") == internalSecret
    }

    @PostMapping("/mark-dirty")
    fun markDirty(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        if (!authorized(request)) {
            response.status = HttpServletResponse.SC_FORBIDDEN
            return
        }
        StatBySong.markDirty()
        response.status = HttpServletResponse.SC_OK
    }
}
