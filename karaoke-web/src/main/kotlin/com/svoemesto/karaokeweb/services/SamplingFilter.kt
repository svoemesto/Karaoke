package com.svoemesto.karaokeweb.services

import org.springframework.stereotype.Component
import kotlin.random.Random

/**
 * Решает, нужно ли пропустить INSERT в `tbl_events` для конкретного REST-вызова (FR-006, FR-007).
 *
 * Используется в `MainController.doRegisterEvent` для ветки `EventType.CALL_REST`:
 *  - Перед [com.svoemesto.karaokeapp.model.EventType.CALL_REST]-INSERT вызывается [shouldSkip].
 *  - Если возвращает `true` — INSERT пропускается (но endpoint всё равно возвращает 200 OK,
 *    чтобы клиент не заметил sampling).
 *
 * **Sampling** (FR-006, clarified 2026-08-14):
 *  - Из N запросов берётся 1 (random).
 *  - N зависит от userType: anonymous=20 (5%), logged=5 (20%), admin=1 (100%).
 *  - Бросок монеты через [Random] — простой uniform distribution, thread-safe (см. KDoc).
 *
 * **Dedup** (FR-007, clarified 2026-08-14):
 *  - Ключ = `(restName, canonical(parameters), anonId-or-userId)`.
 *  - TTL = `KARAOKE_WEB_EVENTS_DEDUP_TTL_SECONDS` (default 30s).
 *  - Если за последние TTL тот же ключ уже был записан — INSERT пропускается.
 *
 * **Алгоритм**:
 *  1. Определить userType (anonymous/logged/admin) по [siteUserId].
 *  2. Сформировать dedup-ключ.
 *  3. Если `dedupCache.isDuplicate(key)` — return `true` (пропустить).
 *  4. Иначе: бросить `random.nextInt(samplingRate) == 0` — если нет, return `true` (пропустить).
 *  5. Иначе: return `false` (сохраняем).
 *
 * @see docs/features/site-traffic-resilience.md
 * @see DedupCache
 * @see SamplingConfig
 * @see KaraokeProperties
 */
@Component
class SamplingFilter(
    private val properties: KaraokeProperties,
) {
    private val dedupCache = DedupCache(ttlMs = { properties.eventsDedupTtlMs() })

    private val random = Random.Default

    /**
     * Тип пользователя для дифференцированного sampling.
     *
     *  - [ANONYMOUS]: siteUserId == 0, нет токена авторизации.
     *  - [LOGGED]: siteUserId > 0, залогиненный пользователь.
     *  - [ADMIN]: admin-пользователь (на текущий момент неотличим от LOGGED —
     *    в SiteUser нет флага isAdmin, см. KDoc SiteUser. Для будущего, когда
     *    появится детект admin'а — будет sampling rate 1/1 = ничего не сэмплируется).
     */
    enum class UserType {
        ANONYMOUS,
        LOGGED,
        ADMIN,
    }

    /**
     * Решает, нужно ли пропустить INSERT.
     *
     * @param restName имя REST-эндпоинта (RestName.dbValue, например ZAKROMA.dbValue).
     * @param parameters параметры запроса (Map, канонизируется для дедупа).
     * @param siteUserId ID залогиненного пользователя (0 = аноним).
     * @param anonId анонимный ID (из cookies/localStorage фронта).
     * @return `true` если INSERT пропускается, `false` если записываем.
     */
    fun shouldSkip(
        restName: String,
        parameters: Map<*, *>,
        siteUserId: Long,
        anonId: String?,
    ): Boolean {
        val userType = userTypeOf(siteUserId)
        val samplingRate = samplingRateFor(userType)
        val dedupKey = buildDedupKey(restName, parameters, siteUserId, anonId)

        // 1. Дедуп: если за последние TTL тот же ключ — пропускаем.
        if (dedupCache.isDuplicate(dedupKey)) {
            return true
        }

        // 2. Sampling: 1 из samplingRate запросов пропускается.
        //    nextInt(N) возвращает 0..N-1 равновероятно, так что условие ==0 срабатывает ровно в 1/N случаев.
        //    samplingRate=1 означает "всегда сохраняем" (nextInt(1) всегда 0 → НЕ пропускаем).
        if (samplingRate > 1 && random.nextInt(samplingRate) != 0) {
            return true
        }

        return false
    }

    /**
     * Определяет userType по siteUserId.
     *
     * На текущий момент admin неотличим от logged (нет isAdmin в SiteUser). Если в будущем
     * появится admin-detection — добавить третий if-блок с явным списком admin ID.
     */
    private fun userTypeOf(siteUserId: Long): UserType =
        if (siteUserId > 0) UserType.LOGGED else UserType.ANONYMOUS

    private fun samplingRateFor(userType: UserType): Int {
        val cfg = properties.samplingConfig
        return when (userType) {
            UserType.ANONYMOUS -> cfg.samplingAnonymous
            UserType.LOGGED -> cfg.samplingLogged
            UserType.ADMIN -> cfg.samplingAdmin
        }
    }

    /**
     * Канонический dedup-ключ: `(restName, canonical(parameters), anonId|userId)`.
     *
     * Канонизация parameters = сортировка ключей + конкатенация `key=value` через `&`.
     * Это гарантирует, что `{a:1,b:2}` и `{b:2,a:1}` дают одинаковый ключ.
     *
     * Scope (clarified Q2): per-(anonId|userId). То есть два анонима с одним anonId
     * дедупятся, разные анонимы — нет (для них работает sampling).
     */
    private fun buildDedupKey(
        restName: String,
        parameters: Map<*, *>,
        siteUserId: Long,
        anonId: String?,
    ): String {
        val canonicalParams =
            if (parameters.isEmpty()) {
                ""
            } else {
                parameters.entries
                    .filter { it.key != null }
                    .sortedBy { it.key.toString() }
                    .joinToString("&") { "${it.key}=${it.value}" }
            }
        val identity =
            when {
                siteUserId > 0 -> "user:$siteUserId"
                !anonId.isNullOrBlank() -> "anon:$anonId"
                else -> "anon:none"
            }
        return "$restName|$canonicalParams|$identity"
    }

    /** Размер dedup-кеша (для метрик/debug). */
    fun dedupCacheSize(): Int = dedupCache.size()
}
