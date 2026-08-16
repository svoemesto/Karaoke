package com.svoemesto.karaokeapp.services

import com.svoemesto.karaokeapp.KaraokeProperties
import com.svoemesto.karaokeapp.controllers.SyncOneClickResultDto
import com.svoemesto.karaokeapp.controllers.dto.AutoOneClickSyncDtos
import com.svoemesto.karaokeapp.controllers.dto.AutoOneClickSyncStatusDto
import com.svoemesto.karaokeapp.runEntitySync
import com.svoemesto.karaokeapp.sync.SyncRegistry
import com.svoemesto.karaokeapp.sync.isAllowed
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Периодический автозапуск «Синхронизации в 1 клик» (spec 235).
 *
 * Запускает существующую бизнес-логику [runEntitySync] для каждой сущности из
 * [SyncRegistry.all] каждые `autoOneClickSyncIntervalMs` миллисекунд
 * (по умолчанию 3 часа). Работает, только пока запущен `karaoke-app` —
 * desktop-приложение, не always-on сервис (см. AGENTS.md, A-001). Поведение
 * «как у ручного клика»: тот же набор `SyncRegistry.all`, те же `oneClickDirection`,
 * те же per-target флаги (см. spec 235, A-002).
 *
 * **Архитектура** (см. `research.md`):
 *
 * 1. **Внешний `@Scheduled`-тик** — `fixedDelay = 60_000L` (1 минута). Внутри —
 *    ручная проверка `now - lastRunMs >= intervalMs`. Это **единственный** способ
 *    динамически менять интервал из `KaraokeProperties` (SpEL/`${}` в `@Scheduled`
 *    не работает: `KaraokeProperties` не публикуется в Spring `Environment`,
 *    см. `research.md §1`).
 * 2. **In-process lock** — общий singleton bean с `AtomicBoolean running`,
 *    разделяемый с [com.svoemesto.karaokeapp.controllers.ApiController.postSyncOneClick].
 *    Ручной клик во время автозапуска получает HTTP `409 Conflict`
 *    (spec 235, FR-015, US1 AC2). `compareAndSet(false, true)` — non-blocking,
 *    lock-free (spec 235, `research.md §3`).
 * 3. **Двухуровневая защита от исключений** (FR-016, SC-009):
 *    - Внутри per-target `for { try { runOne } catch(Throwable) { log+record } }` —
 *      одна упавшая сущность не ломает остальные (FR-012, SC-007).
 *    - Снаружи всего тика `try { ... } catch(Throwable) { FAILED + reason }` —
 *      даже если `SyncRegistry.all` бросит в `iterator()`, scheduler-бин не
 *      останавливается. Следующий тик через `fixedDelay` пытается снова.
 * 4. **In-memory history** — `ConcurrentLinkedDeque<AutoOneClickSyncRun>` длиной
 *    ≤10. После каждого `addLast` — `pollFirst()` при `size > 10` (FR-009).
 * 5. **Логирование** — все значимые события логируются через `org.slf4j.Logger`:
 *    - `[AutoOneClickSyncScheduler] disabled by config (autoOneClickSyncEnabled=false)` —
 *      при `enabled=false` (FR-013).
 *    - `[AutoOneClickSyncScheduler] tick=<ISO> RUNNING/SUCCESS/FAILED totals=…` — на тиках.
 *    - `[AutoOneClickSyncScheduler] target=<key> failed: <message>` — на per-target падениях.
 *
 * **Гарантии**:
 *
 * - `intervalMs.coerceAtLeast(60_000L)` — минимум 1 минута, иначе риск DDoS БД.
 * - `enabled` по умолчанию `true` (см. A-005, спецификация «должна запускаться»).
 * - `try/catch(Throwable)` (НЕ `Exception`) — `Error` (например, `OutOfMemoryError`)
 *   не должен убивать scheduler.
 *
 * **Не делает** (out of scope, см. spec 235 Notes):
 * - Persist'енция истории в БД (A-007).
 * - Cluster lock (karaoke-app — desktop, однопроцессный).
 * - SSE-push обновлений (Q2 в Clarifications — REST-only).
 *
 * @see AutoOneClickSyncRun
 * @see AutoOneClickSyncStatusDto
 * @see com.svoemesto.karaokeapp.controllers.AutoOneClickSyncStatusController
 * @see livedocs/features/235-auto-sync-3h.md
 */
@Component
class AutoOneClickSyncScheduler {
    private val log = LoggerFactory.getLogger(AutoOneClickSyncScheduler::class.java)

    /**
     * Момент последнего **выполненного** тика (`System.currentTimeMillis()`).
     * Используется для проверки `now - lastRunMs >= intervalMs` (см. `research.md §1`).
     * `@Volatile` — visibility между scheduler-thread и request-thread (Tomcat).
     * `0L` — initial-значение, гарантирует, что первый тик произойдёт
     * после `appStartTime + initialDelayMs` (если `initialDelayMs > 0`).
     * Записывается только изнутри `tick()` (в `finally`), читается из любого
     * потока; setter приватный по логике, но Kotlin запрещает `private set` на
     * `var` без явного `final`-модификатора (ktlint-правило
     * `private-setters-for-open-properties`), поэтому просто `var` без
     * `private set` — внешний код не должен писать в это поле по контракту.
     */
    @Volatile
    var lastRunMs: Long = 0L

    /**
     * Момент старта `karaoke-app` (для расчёта `nextRunEstimate` в `getStatus()`,
     * если `lastRun` ещё нет). `0L` означает «ещё не инициализирован»
     * (хотя `init`-блок Spring всегда инициализирует перед первым обращением).
     */
    @Volatile
    var appStartTime: Long = 0L

    /**
     * Общий lock «не одновременно» (FR-007, FR-015). Доступен напрямую
     * из [com.svoemesto.karaokeapp.controllers.ApiController.postSyncOneClick]
     * для реализации `409 Conflict` при попытке ручного клика во время
     * автозапуска. `AtomicBoolean.compareAndSet(false, true)` — non-blocking
     * (см. `research.md §3`).
     */
    val running: AtomicBoolean = AtomicBoolean(false)

    /**
     * История последних тиков (FR-009, лимит 10).
     * `ConcurrentLinkedDeque` — non-blocking, lock-free (см. `research.md §4`).
     * `addLast` — новый тик; `pollFirst` — при `size > 10` после `addLast`.
     */
    private val history: ConcurrentLinkedDeque<AutoOneClickSyncRun> = ConcurrentLinkedDeque()

    /**
     * Диагностический лог при старте bean'а: показывает, включён ли автозапуск
     * (FR-013, US2 AC1). Логируется один раз на старте контекста.
     */
    init {
        appStartTime = System.currentTimeMillis()
        val enabled = KaraokeProperties.getBoolean("autoOneClickSyncEnabled")
        if (!enabled) {
            log.info("[AutoOneClickSyncScheduler] disabled by config (autoOneClickSyncEnabled=false)")
        } else {
            val interval = KaraokeProperties.getLong("autoOneClickSyncIntervalMs").coerceAtLeast(60_000L)
            val initial = KaraokeProperties.getLong("autoOneClickSyncInitialDelayMs")
            log.info(
                "[AutoOneClickSyncScheduler] enabled: intervalMs={} ({} min), initialDelayMs={} ({} min)",
                interval,
                interval / 60_000L,
                initial,
                initial / 60_000L,
            )
        }
    }

    /**
     * Главный `@Scheduled`-тик. Запускается каждые 60 секунд (внешний `fixedDelay`),
     * внутри — проверка `intervalMs` и `enabled` из `KaraokeProperties`
     * (см. `research.md §1` — dynamic interval через внутреннюю проверку,
     * а не через `${...}` в `@Scheduled`).
     *
     * **Алгоритм** (соответствует `research.md §2`):
     * 1. Если `autoOneClickSyncEnabled=false` → выход, ничего не логируем
     *    (FR-013, US2).
     * 2. Если `now - lastRunMs < intervalMs` → выход (ещё не время).
     * 3. Если `running.compareAndSet(false, true) == false` → выход, автозапуск
     *    или ручной клик уже идёт (US1 AC4 — «skipped»).
     * 4. Создаём `AutoOneClickSyncRun(status="RUNNING", startedAt=now)`.
     * 5. Per-target `for { try { runOne } catch(Throwable) { log+record } }`
     *    (FR-012, SC-007). `target.oneClickDirection` не nullable в
     *    `SyncTarget<T>`, но `isAllowed(direction)` может вернуть `false` —
     *    тогда `skipped=true`, пустые списки.
     * 6. Снаружи `try { ... } catch(Throwable) { FAILED + reason }` (FR-016, SC-009).
     * 7. В `finally`: `history.addLast(run); if (history.size > 10) history.pollFirst();
     *    running.set(false); lastRunMs = now`.
     */
    @Scheduled(fixedDelay = 60_000L, initialDelay = 5_000L)
    fun tick() {
        if (!KaraokeProperties.getBoolean("autoOneClickSyncEnabled")) return

        val now = System.currentTimeMillis()
        val intervalMs = KaraokeProperties.getLong("autoOneClickSyncIntervalMs").coerceAtLeast(60_000L)
        if (now - lastRunMs < intervalMs) return

        if (!running.compareAndSet(false, true)) {
            log.info("[AutoOneClickSyncScheduler] skipped — previous run still in progress")
            return
        }

        val run = AutoOneClickSyncRun(startedAt = Instant.now())
        log.info("[AutoOneClickSyncScheduler] tick={} RUNNING", run.startedAt)

        try {
            val perTarget =
                SyncRegistry.all.map { target ->
                    try {
                        val direction = target.oneClickDirection
                        if (!target.isAllowed(direction)) {
                            // Per-target флаги `sync_<key>_<push|pull>_*_allowed` выключены
                            // (см. KaraokeProperties, ~строка 333+) — сущность пропускается
                            // тем же путём, что и в существующем ApiController.postSyncOneClick.
                            SyncOneClickResultDto(
                                key = target.key,
                                displayName = target.displayName,
                                direction = direction.name,
                                skipped = true,
                                created = emptyList(),
                                updated = emptyList(),
                                deleted = emptyList(),
                                moved = emptyList(),
                            )
                        } else {
                            val (created, updated, deleted, moved) =
                                runEntitySync(key = target.key, direction = direction)
                            SyncOneClickResultDto(
                                key = target.key,
                                displayName = target.displayName,
                                direction = direction.name,
                                skipped = false,
                                created = created,
                                updated = updated,
                                deleted = deleted,
                                moved = moved,
                            )
                        }
                    } catch (t: Throwable) {
                        // FR-012: одна упавшая сущность не ломает остальные.
                        // Логируем стек и подставляем "skipped"-результат,
                        // чтобы в `perTarget[]` сохранилась позиция для UI.
                        log.error("[AutoOneClickSyncScheduler] target={} failed", target.key, t)
                        SyncOneClickResultDto(
                            key = target.key,
                            displayName = target.displayName,
                            direction = "?",
                            skipped = true,
                            created = emptyList(),
                            updated = emptyList(),
                            deleted = emptyList(),
                            moved = emptyList(),
                        )
                    }
                }

            // Агрегируем totals. Per-target — List<String> (ID записей);
            // totals — Int (счётчики).
            val totals =
                Totals(
                    created = perTarget.sumOf { it.created.size },
                    updated = perTarget.sumOf { it.updated.size },
                    deleted = perTarget.sumOf { it.deleted.size },
                    moved = perTarget.sumOf { it.moved.size },
                )

            val finished = run.copy(finishedAt = Instant.now(), status = "SUCCESS", totals = totals, perTarget = perTarget)
            log.info(
                "[AutoOneClickSyncScheduler] tick={} SUCCESS totals=created:{}, updated:{}, deleted:{}, moved:{}",
                finished.startedAt,
                totals.created,
                totals.updated,
                totals.deleted,
                totals.moved,
            )
            appendHistory(finished)
        } catch (t: Throwable) {
            // FR-016, SC-009: внешний рубеж. scheduler-бин НЕ должен сломаться.
            // `Throwable` (не Exception) — ловим и Error (OutOfMemoryError, StackOverflowError).
            val failed =
                run.copy(
                    finishedAt = Instant.now(),
                    status = "FAILED",
                    reason = "${t::class.simpleName}: ${t.message ?: "(no message)"}",
                )
            log.error("[AutoOneClickSyncScheduler] tick={} FAILED", run.startedAt, t)
            appendHistory(failed)
        } finally {
            running.set(false)
            lastRunMs = now
        }
    }

    /**
     * Добавляет [run] в [history]; при `size > 10` — `pollFirst()`
     * (удаляем самый старый). Не атомарно с `addLast` (между ними
     * возможна гонка двух тиков), но т.к. `running` lock сериализует
     * тики (только один тик одновременно), на практике безопасно.
     */
    private fun appendHistory(run: AutoOneClickSyncRun) {
        history.addLast(run)
        while (history.size > 10) {
            history.pollFirst()
        }
    }

    /**
     * Возвращает текущий статус автозапуска для UI (FR-009) и для
     * [com.svoemesto.karaokeapp.controllers.AutoOneClickSyncStatusController].
     *
     * `nextRunEstimate`:
     * - `null` если `enabled=false` (по spec 235).
     * - `lastRun.finishedAt + intervalMs` если `lastRun != null`.
     * - `appStartTime + initialDelayMs` если `lastRun == null` (ещё ни один тик не прошёл).
     * - `null` если `lastRun == null` И `appStartTime == 0L` (теоретически не должно случаться,
     *   но защищаемся).
     */
    fun getStatus(): AutoOneClickSyncStatusDto {
        val enabled = KaraokeProperties.getBoolean("autoOneClickSyncEnabled")
        val intervalMs = KaraokeProperties.getLong("autoOneClickSyncIntervalMs").coerceAtLeast(60_000L)
        val initialDelayMs = KaraokeProperties.getLong("autoOneClickSyncInitialDelayMs")

        val snapshot = history.toList() // immutable copy; ConcurrentLinkedDeque.toList() — weak consistency, но достаточно для UI
        val lastRun = snapshot.lastOrNull()
        val last10NewestFirst = snapshot.asReversed() // newest first для UI

        val nextRunEstimate: String? =
            if (!enabled) {
                null
            } else if (lastRun != null && lastRun.finishedAt != null) {
                lastRun.finishedAt.plusMillis(intervalMs).toString()
            } else if (appStartTime > 0L) {
                Instant.ofEpochMilli(appStartTime + initialDelayMs).toString()
            } else {
                null
            }

        return AutoOneClickSyncStatusDto(
            enabled = enabled,
            intervalMs = intervalMs,
            initialDelayMs = initialDelayMs,
            lastRun = lastRun?.let { AutoOneClickSyncDtos.toDto(it) },
            last10 = AutoOneClickSyncDtos.toDtos(last10NewestFirst),
            nextRunEstimate = nextRunEstimate,
        )
    }

    /**
     * Размер истории (для unit-тестов и диагностики). Не используется в проде.
     */
    @Suppress("unused")
    fun historySize(): Int = history.size
}
