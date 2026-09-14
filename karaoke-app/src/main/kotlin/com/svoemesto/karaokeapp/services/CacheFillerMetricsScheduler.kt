package com.svoemesto.karaokeapp.services

import com.svoemesto.karaokeapp.model.SseNotification
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

/**
 * Pass 92/98 — периодическая рассылка метрик [StorageMetadataCache.CacheFillerMetrics]
 * через SSE-событие `CACHE_FILLER_METRICS` (см. [SseNotificationType.CACHE_FILLER_METRICS]).
 *
 * Используется в webvue3 `CacheQueueBadge.vue` для отображения бейджа счётчика
 * количества задач в пуле проверки кеша обращения к хранилищу.
 *
 * Решение: SSE (Pass 97 Q6) — не polling. Каждые 5 сек отправляется событие
 * с актуальным `pendingTotal`. Подавление дублей: если значение не изменилось —
 * не рассылаем (FR-001 в `KaraokeProcessWorker.sendCountWaitingMessage`).
 *
 * @see research/92-backend-queue-size/REPORT.md
 * @see specs/_wayfinder-92-hrqueue-priority/97-grilling-resolution.md
 */
@Service
class CacheFillerMetricsScheduler(
    private val storageMetadataCache: StorageMetadataCache,
    private val sseNotificationService: SseNotificationService,
) {
    private val log = LoggerFactory.getLogger("infra.cache.storage")

    /** Последнее отправленное значение — для подавления дублей (FR-001). */
    @Volatile
    private var lastSentPendingTotal: Int? = null

    /**
     * @Scheduled с `fixedRate = 5000` — каждые 5 сек. Согласно research #95,
     * polling 5-10 сек достаточно; здесь это интервал между рассылками SSE.
     */
    @Scheduled(fixedRate = 5_000L, initialDelay = 5_000L)
    fun sendCacheFillerMetrics() {
        try {
            val metrics = storageMetadataCache.cacheFillerMetrics()
            // Подавление дублей: если pendingTotal не изменился — не шлём.
            if (lastSentPendingTotal != null && lastSentPendingTotal == metrics.pendingTotal) return
            lastSentPendingTotal = metrics.pendingTotal
            sseNotificationService.send(
                SseNotification.cacheFillerMetrics(metrics),
            )
        } catch (e: Exception) {
            log.warn("Failed to send cacheFillerMetrics SSE: {}", e.message)
        }
    }
}
