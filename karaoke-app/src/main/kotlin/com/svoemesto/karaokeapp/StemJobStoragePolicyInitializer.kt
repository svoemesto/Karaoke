package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.model.StemJob
import com.svoemesto.karaokeapp.services.StorageApiClient
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Инвариант закрытости приватных объектов премиум-задания (спека 561, OpenProject #361,
 * аудит BR-258) — обеспечивается **программно** (решение владельца №2, FR-006): приложение само
 * приводит свои бакеты к безопасному состоянию, поэтому закрытость не зависит от ручных действий
 * администратора в каждом развёртывании.
 *
 * Что делается с REMOTE-хранилищем (тем же клиентом, которым объекты задания туда попадают —
 * `StorageApiClient` → MinIO на прод-хосте):
 *
 * - бакет `stemjobs` — создать (если нет) и выставить **private** (пустая bucket policy):
 *   анонимный GET `/minio/stemjobs/<key>` получает 403 от самого MinIO;
 * - бакет `karaoke` — выставить **public** (`s3:GetObject` на весь бакет): публичная часть
 *   сайта (картинки/превью/mp3 песен) по контракту раздаётся анонимно (FR-002).
 *
 * ## KARAOKE-385: политика — не одноразовое действие, а поддерживаемое состояние
 *
 * До этой правки политики применялись **ровно один раз** на `ApplicationReadyEvent`, а ошибка
 * только логировалась. Инцидент 2026-11-02: в момент старта прод-канал до MinIO был закрыт
 * (смена адреса админ-машины), обе политики не применились, и приложение больше не пыталось их
 * выставить — «дожать» можно было только перезапуском.
 *
 * Теперь:
 * 1. **Проверка вместо слепого применения** — сначала читаем фактическое состояние
 *    (`isBucketPublic`), применяем только при расхождении (идемпотентно, без лишних запросов).
 * 2. **Повтор с backoff при старте** — несколько попыток; неудача не роняет старт (fail-safe).
 * 3. **Самопроверка по расписанию** — раз в 5 минут, пока состояние не подтверждено, и раз в час,
 *    когда подтверждено: после восстановления связи политики дожимаются **без перезапуска**.
 * 4. **Одна человекочитаемая строка на событие**, без стектрейсов, со стабильными кодами
 *    (`STORAGE-POLICY-OK` / `STORAGE-POLICY-REPAIRED` / `STORAGE-POLICY-UNCONFIRMED`) —
 *    см. `knowledge/domains/monitoring/components/log-categories.md`.
 *
 * @see specs/561-impl-stem-bucket/research.md
 */
@Component
class StemJobStoragePolicyInitializer(
    private val storageApiClient: StorageApiClient,
) {
    private val log = LoggerFactory.getLogger("infra.stemjobs.storage")

    /** Целевое состояние одного бакета: `public = true` — анонимный GET разрешён. */
    internal data class PolicyTarget(
        val bucket: String,
        val public: Boolean,
    ) {
        val expected: String get() = if (public) "public" else "private"
    }

    private val targets =
        listOf(
            PolicyTarget(StemJob.STORAGE_BUCKET, public = false),
            PolicyTarget(StemJob.PUBLIC_STORAGE_BUCKET, public = true),
        )

    /** Попыток при старте, прежде чем политика будет признана неподтверждённой. */
    internal var attempts: Int = DEFAULT_ATTEMPTS

    /** Базовая пауза backoff (удваивается на каждой следующей попытке, но не выше [maxDelayMs]). */
    internal var baseDelayMs: Long = DEFAULT_BASE_DELAY_MS

    internal var maxDelayMs: Long = DEFAULT_MAX_DELAY_MS

    /** Пауза между попытками — подменяется в тестах, чтобы не спать по-настоящему. */
    internal var pause: (Long) -> Unit = { millis -> Thread.sleep(millis) }

    /** Часы — подменяются в тестах для проверки интервалов самопроверки. */
    internal var clock: () -> Long = { System.currentTimeMillis() }

    @Volatile
    private var confirmed = false

    @Volatile
    private var lastCheckAtMs = 0L

    /** true — фактическое состояние обоих бакетов совпадает с ожидаемым (для health/тестов). */
    fun isConfirmed(): Boolean = confirmed

    /** Проверка при старте: с повтором и backoff, ошибки не пробрасываются. */
    @EventListener(ApplicationReadyEvent::class)
    fun enforceBucketPolicies() {
        runCheck(withRetry = true)
    }

    /**
     * Самопроверка по расписанию (KARAOKE-385): тик раз в минуту, но фактическая проверка идёт не
     * чаще [currentIntervalMs] — 5 минут, пока политика не подтверждена, и час после подтверждения.
     * Так восстановление связи с MinIO дожимает политику без перезапуска приложения.
     *
     * `initialDelay`: первый тик — через интервал, а не сразу. Иначе при старте проверка идёт
     * дважды (событие `ApplicationReadyEvent` + первый тик планировщика) и в лог попадают две
     * пары одинаковых строк — шум, который противоречит цели KARAOKE-387.
     */
    @Scheduled(fixedDelay = SELF_CHECK_TICK_MS, initialDelay = SELF_CHECK_TICK_MS)
    fun scheduledSelfCheck() {
        val now = clock()
        if (now - lastCheckAtMs < currentIntervalMs()) return
        runCheck(withRetry = false)
    }

    /** Интервал фактических проверок: чаще, пока состояние не подтверждено. */
    internal fun currentIntervalMs(): Long = if (confirmed) CONFIRMED_INTERVAL_MS else UNCONFIRMED_INTERVAL_MS

    /**
     * Полный проход по бакетам. Возвращает true, если все политики подтверждены.
     * Никогда не пробрасывает исключения: недоступность хранилища не должна ронять приложение.
     */
    internal fun runCheck(withRetry: Boolean): Boolean {
        lastCheckAtMs = clock()
        val wasConfirmed = confirmed
        var allOk = true
        for (target in targets) {
            if (!verifyAndApply(target, withRetry, wasConfirmed)) allOk = false
        }
        confirmed = allOk
        if (allOk && !wasConfirmed) {
            log.info(
                "stemjobs:storage:policy confirmed buckets={} code=STORAGE-POLICY-OK",
                targets.joinToString(",") { "${it.bucket}:${it.expected}" },
            )
        }
        if (!allOk) {
            log.error(
                "stemjobs:storage:policy unconfirmed buckets={} code=STORAGE-POLICY-UNCONFIRMED retryInSec={}",
                targets.joinToString(",") { "${it.bucket}:${it.expected}" },
                currentIntervalMs() / 1000,
            )
        }
        return allOk
    }

    private fun verifyAndApply(
        target: PolicyTarget,
        withRetry: Boolean,
        wasConfirmed: Boolean,
    ): Boolean {
        val maxAttempts = if (withRetry) attempts.coerceAtLeast(1) else 1
        var attempt = 0
        var actualWas = "unknown"
        var lastReason = "none"

        while (attempt < maxAttempts) {
            attempt++
            try {
                val actual = readIsPublic(target.bucket)
                actualWas =
                    when (actual) {
                        null -> "unknown"
                        true -> "public"
                        false -> "private"
                    }
                if (actual == target.public) {
                    if (!wasConfirmed) {
                        log.info(
                            "stemjobs:storage:policy bucket={} expected={} actual={} action=none result=ok attempts={} " +
                                "code=STORAGE-POLICY-OK",
                            target.bucket,
                            target.expected,
                            actualWas,
                            attempt,
                        )
                    }
                    return true
                }

                applyPolicy(target)
                val after = readIsPublic(target.bucket)
                if (after == target.public) {
                    log.info(
                        "stemjobs:storage:policy bucket={} expected={} actual={} action=reapply result=ok attempts={} " +
                            "code=STORAGE-POLICY-REPAIRED",
                        target.bucket,
                        target.expected,
                        actualWas,
                        attempt,
                    )
                    return true
                }
                lastReason = "state_mismatch"
            } catch (e: Exception) {
                // Стектрейс намеренно не печатается (KARAOKE-387): разбираться нужно по коду,
                // хосту и операции, а не по фреймам Spring.
                lastReason = e.javaClass.simpleName
            }
            if (attempt < maxAttempts) {
                pause(backoffDelayMs(attempt))
            }
        }

        log.error(
            "stemjobs:storage:policy bucket={} expected={} actual={} action=apply result=failed attempts={} reason={} " +
                "code=STORAGE-POLICY-UNCONFIRMED",
            target.bucket,
            target.expected,
            actualWas,
            attempt,
            lastReason,
        )
        return false
    }

    /** Пауза перед следующей попыткой: base * 2^(attempt-1), но не выше [maxDelayMs]. */
    internal fun backoffDelayMs(attempt: Int): Long {
        val shift = (attempt - 1).coerceIn(0, 20)
        return minOf(baseDelayMs shl shift, maxDelayMs).coerceAtLeast(0)
    }

    private fun readIsPublic(bucket: String): Boolean? =
        storageApiClient
            .isBucketPublic(bucket)
            .block()
            ?.get("isPublic")

    private fun applyPolicy(target: PolicyTarget) {
        if (target.public) {
            storageApiClient.setBucketPublic(target.bucket).block()
        } else {
            storageApiClient.setBucketPrivate(target.bucket).block()
        }
    }

    companion object {
        /** Тик планировщика самопроверки. */
        internal const val SELF_CHECK_TICK_MS = 60_000L

        /** Интервал проверок, пока политика не подтверждена. */
        internal const val UNCONFIRMED_INTERVAL_MS = 5 * 60_000L

        /** Интервал проверок после подтверждения — дорого не нужно, но дрейф заметим. */
        internal const val CONFIRMED_INTERVAL_MS = 60 * 60_000L

        internal const val DEFAULT_ATTEMPTS = 5
        internal const val DEFAULT_BASE_DELAY_MS = 2_000L
        internal const val DEFAULT_MAX_DELAY_MS = 30_000L
    }
}
