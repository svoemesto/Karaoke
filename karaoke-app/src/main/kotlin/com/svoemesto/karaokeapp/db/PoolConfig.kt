package com.svoemesto.karaokeapp.db

/**
 * Параметры ограниченного пула соединений к БД (спека 778, KARAOKE-430).
 *
 * **Зачем пул.** `KaraokeConnection` кэширует физическое соединение в
 * `ThreadLocal` потока (ADR-0009). У потока, который завершился, соединение
 * закрыть некому — канал остаётся в БД до отзыва процесса. Итог: число
 * физических соединений растёт без предела, и при `max_connections` PostgreSQL
 * отказывает **всей** системе сразу (`FATAL: sorry, too many clients already`,
 * инцидент 2026-11-14: web держал 297 из 300, прод отдавал 503).
 *
 * Пул даёт **потолок**: физических каналов не больше `maxPoolSize`, сколько бы
 * потоков ни коснулось БД. Отказ приложения (пул исчерпан) становится
 * различимым с отказом БД — это отдельное событие со своим кодом.
 *
 * **Размеры — потолок одновременно существующих handle'ов, а не от нагрузки.**
 * Handle живёт, пока жив поток-хозяин, поэтому размер пула считается от
 * конфигурации процесса (число потоков, которые *могут* коснуться БД), а не от
 * наблюдаемого числа соединений: см. `plan.md` § «Размеры пулов и конфигурация».
 *
 * **Значения по умолчанию различаются для app и web** — они задаются в
 * `application.yml` каждого модуля; переменные окружения перекрывают.
 *
 * @param maxPoolSize потолок одновременных физических каналов
 * @param minIdle нижняя граница прогретых каналов
 * @param acquireTimeoutMs сколько ждать свободный слот перед отказом
 * @param maxLifetimeMs абсолютный срок жизни канала (страховка от «залипших»)
 * @param keepaliveMs пинг простаивающих каналов (туннель до SERVER-БД рвёт их молча)
 * @param validationTimeoutMs проверка живости канала при выдаче
 * @see com.svoemesto.karaokeapp.db.DbPool
 * @see knowledge/adr/local-0009-thread-local-connection-lifecycle.md
 */
data class PoolConfig(
    val maxPoolSize: Int,
    val minIdle: Int = 2,
    val acquireTimeoutMs: Long = 5_000,
    val maxLifetimeMs: Long = 1_800_000,
    val keepaliveMs: Long = 60_000,
    val validationTimeoutMs: Long = 3_000,
) {
    init {
        require(maxPoolSize >= 1) { "maxPoolSize должен быть ≥ 1, получено $maxPoolSize" }
        require(minIdle >= 0) { "minIdle должен быть ≥ 0, получено $minIdle" }
        require(minIdle <= maxPoolSize) {
            "minIdle ($minIdle) не может превышать maxPoolSize ($maxPoolSize)"
        }
        require(acquireTimeoutMs > 0) {
            "acquireTimeoutMs должен быть > 0, получено $acquireTimeoutMs"
        }
        require(maxLifetimeMs > 0) { "maxLifetimeMs должен быть > 0, получено $maxLifetimeMs" }
        require(keepaliveMs >= 0) { "keepaliveMs должен быть ≥ 0, получено $keepaliveMs" }
        require(validationTimeoutMs > 0) {
            "validationTimeoutMs должен быть > 0, получено $validationTimeoutMs"
        }
        // keepalive должен быть меньше maxLifetime: иначе пул будет «пинговать»
        // канал, который вот-вот выбросит по maxLifetime.
        require(keepaliveMs < maxLifetimeMs) {
            "keepaliveMs ($keepaliveMs) должен быть меньше maxLifetimeMs ($maxLifetimeMs)"
        }
    }

    companion object {
        /**
         * Лимит соединений PostgreSQL, против которого проверяется сумма пулов
         * процесса (FR-010). Держим в одном месте с compose-файлами БД
         * (`deploy/docker-compose-database.yml`, `deploy/prod-single-host/docker-compose-database.yml`;
         * переменная `DB_MAX_CONNECTIONS`, по умолчанию 300).
         */
        const val DB_MAX_CONNECTIONS = 300

        /**
         * Проверяет, что сумма потолков всех пулов процесса укладывается в лимит
         * БД с запасом на административные подключения (FR-010).
         *
         * @param maxPoolSizes потолки пулов процесса
         * @param limit лимит соединений БД
         * @throws IllegalArgumentException если сумма превышает лимит
         */
        fun requireWithinDbLimit(
            maxPoolSizes: List<Int>,
            limit: Int = DB_MAX_CONNECTIONS,
        ) {
            val total = maxPoolSizes.sum()
            require(total < limit) {
                "Сумма размеров пулов ($total) должна быть меньше лимита соединений БД ($limit) " +
                    "с запасом на административные подключения (FR-010)"
            }
        }
    }
}
