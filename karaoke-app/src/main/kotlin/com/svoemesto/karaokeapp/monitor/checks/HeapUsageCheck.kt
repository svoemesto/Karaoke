package com.svoemesto.karaokeapp.monitor.checks

import com.svoemesto.karaokeapp.monitor.MonitorAlert
import com.svoemesto.karaokeapp.monitor.MonitorCheck
import com.svoemesto.karaokeapp.monitor.MonitorContext
import com.svoemesto.karaokeapp.monitor.MonitorSeverity
import org.slf4j.LoggerFactory

/**
 * Снимок загрузки heap JVM.
 *
 * Источник — [Runtime]: `totalMemory() - freeMemory()` (занято) и `maxMemory()`
 * (потолок, ≈ `-Xmx` минус один survivor-space). Выбор не зависит от состава
 * JRE/JDK и от наличия модуля `java.management` — MXBean для процента от
 * максимума heap точнее не даёт.
 *
 * @param usedBytes занятая часть heap, байт
 * @param maxBytes потолок heap, байт
 */
data class HeapSample(
    val usedBytes: Long,
    val maxBytes: Long,
) {
    /** Загрузка в процентах от потолка; `0.0`, если потолок неизвестен. */
    val usedPercent: Double
        get() = if (maxBytes <= 0L) 0.0 else usedBytes.toDouble() * 100.0 / maxBytes.toDouble()

    companion object {
        /** Текущее состояние heap процесса. */
        fun current(): HeapSample {
            val runtime = Runtime.getRuntime()
            return HeapSample(
                usedBytes = runtime.totalMemory() - runtime.freeMemory(),
                maxBytes = runtime.maxMemory(),
            )
        }
    }
}

/**
 * Пороги алерта загрузки heap (ADR `local-0001`: конфигурация через env,
 * дефолты несекретные).
 *
 * @param warnPercent порог предупреждения, % от максимума heap
 * @param criticalPercent порог критического уровня, % от максимума heap
 */
data class HeapThresholds(
    val warnPercent: Int,
    val criticalPercent: Int,
) {
    companion object {
        /** Порог предупреждения по умолчанию (решение владельца 2026-10-30). */
        const val DEFAULT_WARN_PERCENT = 75

        /** Порог критического уровня по умолчанию (решение владельца 2026-10-30). */
        const val DEFAULT_CRITICAL_PERCENT = 90

        /** Имя переменной окружения с порогом предупреждения. */
        const val WARN_ENV = "MONITOR_HEAP_WARN_PERCENT"

        /** Имя переменной окружения с порогом критического уровня. */
        const val CRITICAL_ENV = "MONITOR_HEAP_CRITICAL_PERCENT"

        /**
         * Пороги из окружения.
         *
         * Значение берётся из env; нечитаемое, нечисловое или выпадающее из
         * диапазона значение заменяется дефолтом. Если после разбора
         * `warnPercent >= criticalPercent` — берутся оба дефолта (несогласованная
         * пара порогов не должна приводить к молчащему отсутствию алертов).
         */
        fun fromEnv(env: Map<String, String> = System.getenv()): HeapThresholds {
            val warn = env[WARN_ENV]?.trim()?.toIntOrNull()?.takeIf { it in 1..99 } ?: DEFAULT_WARN_PERCENT
            val critical = env[CRITICAL_ENV]?.trim()?.toIntOrNull()?.takeIf { it in 2..100 } ?: DEFAULT_CRITICAL_PERCENT
            return if (warn < critical) {
                HeapThresholds(warnPercent = warn, criticalPercent = critical)
            } else {
                HeapThresholds(
                    warnPercent = DEFAULT_WARN_PERCENT,
                    criticalPercent = DEFAULT_CRITICAL_PERCENT,
                )
            }
        }
    }
}

/**
 * Проверка загрузки heap JVM — 8-я проверка мониторинга `karaoke-app`.
 *
 * Назначение: оператор узнаёт о приближении к исчерпанию памяти **до** падения
 * процесса, а не post-mortem'ом (прецедент 2026-10-23: OOM в `karaoke-app` и
 * ложный CRITICAL «прод недоступен» 5+ часов при живом проде).
 *
 * Пороги (решение владельца 2026-10-30): 75 % — предупреждение, 90 % —
 * критический уровень. Значения задаются конфигурацией (env, ADR
 * `local-0001`), дефолты — консервативные.
 *
 * Канал доставки — существующий, без новой инфраструктуры: журнал (SLF4J,
 * категория `infra.monitor.heap`), SSE `MONITOR_ALERTS` и
 * `GET /api/monitor/alerts`. Внешней доставки нет.
 *
 * Инварианты:
 * - без побочных эффектов (только чтение [Runtime] и формирование алерта);
 * - состояние — в памяти, новых персистентных ключей не появляется;
 * - `body` алерта не содержит изменяющихся чисел — иначе `contentHash()` менялся
 *   бы на каждом тике и сообщение «мигало» бы read/unread (вариативные значения
 *   живут в `detail`).
 *
 * @see knowledge/product/features/heap-memory-guardrails.md
 * @see knowledge/domains/monitoring/components/monitor-checks.md
 */
object HeapUsageCheck : MonitorCheck {
    /**
     * BR-630 / спека 676: категория логов проверки мониторинга.
     *
     * Строковая категория (а не имя класса) — по конвенции
     * `knowledge/domains/monitoring/components/log-categories.md`: мониторинг
     * пишет только в `infra.*`, иначе теряется grep в runbook'е.
     */
    private val heapLog = LoggerFactory.getLogger("infra.monitor.heap")

    /**
     * Состояние «алерт был» — только в памяти (FR-019). Нужно, чтобы
     * сообщение о восстановлении писалось на переходе, а не на каждом тике.
     */
    @Volatile
    private var alerting = false

    /** Ключ алерта — стабильный между прогонами (по нему снапшот мониторинга связывает состояние). */
    const val ALERT_KEY = "monitor.heap.usage"

    override fun run(ctx: MonitorContext): List<MonitorAlert> = run(ctx, HeapSample.current())

    /**
     * Прогон проверки на заданном снимке — отдельно от [run], чтобы тест мог
     * задать загрузку без управления реальной памятью JVM.
     */
    internal fun run(
        ctx: MonitorContext,
        sample: HeapSample,
    ): List<MonitorAlert> {
        val thresholds = HeapThresholds.fromEnv()
        val percent = sample.usedPercent

        // Строка о текущей загрузке — на каждом тике (SC-005: «не реже раза в
        // минуту»), а не только при превышении порога.
        heapLog.info(
            "heap:usage usedBytes={} maxBytes={} percent={}",
            sample.usedBytes,
            sample.maxBytes,
            formatPercent(percent),
        )

        val severity = severityFor(percent, thresholds)
        if (severity == null) {
            if (alerting) {
                heapLog.info("heap:recovered percent={}", formatPercent(percent))
                alerting = false
            }
            return emptyList()
        }

        alerting = true
        val threshold = if (severity == MonitorSeverity.CRITICAL) thresholds.criticalPercent else thresholds.warnPercent
        // Маппинг severity → уровень SLF4J (BR-630, ADR local-0014): WARNING→WARN,
        // CRITICAL→ERROR. Уровня CRITICAL в SLF4J нет, а внешних оповещений нет —
        // уровень лога остаётся единственным машинно-читаемым признаком
        // эскалации для оператора.
        if (severity == MonitorSeverity.CRITICAL) {
            heapLog.error(
                "heap:thresholdExceeded percent={} threshold={} severity=CRITICAL",
                formatPercent(percent),
                threshold,
            )
        } else {
            heapLog.warn(
                "heap:thresholdExceeded percent={} threshold={} severity=WARNING",
                formatPercent(percent),
                threshold,
            )
        }

        return listOf(
            MonitorAlert(
                key = ALERT_KEY,
                severity = severity,
                title = "Высокая загрузка heap JVM",
                // body — стабильный текст: в contentHash() входит только severity+title+body.
                body = "Загрузка heap JVM выше порога — процесс близок к исчерпанию памяти.",
                category = "Мониторинг",
                detail = "used=${sample.usedBytes} max=${sample.maxBytes} percent=${formatPercent(percent)} threshold=$threshold",
                recommendations =
                    "Проверьте дампы heap в ./karaoke-db/dumps (BR-630), очередь заданий и лимиты " +
                        "контейнера karaoke-app (mem_limit 8 ГБ, -Xmx6g).",
            ),
        )
    }

    /** Severity по порогам; `null` — загрузка ниже порога предупреждения. */
    private fun severityFor(
        percent: Double,
        thresholds: HeapThresholds,
    ): MonitorSeverity? =
        when {
            percent >= thresholds.criticalPercent -> MonitorSeverity.CRITICAL
            percent >= thresholds.warnPercent -> MonitorSeverity.WARNING
            else -> null
        }

    private fun formatPercent(percent: Double): String = "%.1f".format(percent)
}
