package com.svoemesto.karaokeapp.db

/**
 * Настройки ограниченного пула соединений для процесса (спека 778, KARAOKE-430).
 *
 * **Зачем объект, а не `@Value` прямо в `KaraokeConnection`.** Пул создаётся
 * лениво — при первом обращении к БД, то есть уже после инициализации
 * Spring-контекста. Значения приходят из `application.yml` модуля
 * (`karaoke.db.pool.*`, перекрываются переменными окружения) и заполняются
 * сервисом модуля в `init`-блоке — так же, как остальные параметры БД
 * (`DB_LOCAL_POSTGRES_USER` и прочие).
 *
 * **Дефолты различаются для app и web**, потому что различается верхняя граница
 * одновременно существующих handle'ов (см. `plan.md` § «Размеры пулов»):
 * app — 36 `Schedulers.parallel()` + 30 пулов HealthReport + 50 Tomcat max + 4
 * лейна очереди; web — 36 пул MessageBroker + 50 Tomcat max. Значения заданы в
 * `application.yml` каждого модуля.
 *
 * **Процессы разные.** Модуль `karaoke-app` — общий код, но `karaoke-app` и
 * `karaoke-web` работают в **разных процессах**: каждый заполняет этот объект
 * своими значениями, конфликта нет.
 *
 * @see DbPool
 * @see PoolConfig
 */
object DbPoolSettings {
    /** Включён ли пул. По умолчанию выключен — откат без пересборки (FR-011). */
    @Volatile
    var enabled: Boolean = false

    /** Потолок физических соединений к LOCAL. */
    @Volatile
    var maxLocal: Int = 128

    /** Потолок физических соединений к SERVER. */
    @Volatile
    var maxServer: Int = 20

    /** Нижняя граница прогретых каналов. */
    @Volatile
    var minIdle: Int = 2

    /** Сколько ждать свободный слот перед отказом (FR-008, решение Q3=A). */
    @Volatile
    var acquireTimeoutMs: Long = 5_000

    /** Абсолютный срок жизни канала (страховка от «залипших»). */
    @Volatile
    var maxLifetimeMs: Long = 1_800_000

    /** Пинг простаивающих каналов (туннель до SERVER-БД рвёт их молча). */
    @Volatile
    var keepaliveMs: Long = 60_000

    /** Проверка живости канала при выдаче. */
    @Volatile
    var validationTimeoutMs: Long = 3_000

    /**
     * Применены ли настройки (Spring-инициализация завершена).
     *
     * Пока флаг не выставлен, [configFor] возвращает `null` — пул не
     * создаётся. Это защита от гонки: пул создаётся лениво, при первом
     * обращении к БД, и если это обращение случится **до** инициализации
     * Spring-контекста, пул не должен «залипнуть» выключенным навсегда.
     */
    @Volatile
    var resolved: Boolean = false

    /**
     * Применяет настройки пула (вызывается сервисом модуля в `init`-блоке).
     *
     * @param enabled включён ли пул
     * @param maxLocal потолок для LOCAL
     * @param maxServer потолок для SERVER
     * @param minIdle нижняя граница прогретых каналов
     * @param acquireTimeoutMs ожидание свободного слота
     * @param maxLifetimeMs срок жизни канала
     * @param keepaliveMs пинг простаивающих каналов
     * @param validationTimeoutMs проверка живости канала
     * @throws IllegalArgumentException если сумма потолков превышает лимит БД (FR-010)
     */
    fun apply(
        enabled: Boolean,
        maxLocal: Int,
        maxServer: Int,
        minIdle: Int,
        acquireTimeoutMs: Long,
        maxLifetimeMs: Long,
        keepaliveMs: Long,
        validationTimeoutMs: Long,
    ) {
        this.enabled = enabled
        this.maxLocal = maxLocal
        this.maxServer = maxServer
        this.minIdle = minIdle
        this.acquireTimeoutMs = acquireTimeoutMs
        this.maxLifetimeMs = maxLifetimeMs
        this.keepaliveMs = keepaliveMs
        this.validationTimeoutMs = validationTimeoutMs
        // FR-010: сумма потолков пулов процесса — меньше лимита БД.
        validateAgainstDbLimit()
        this.resolved = true
    }

    /**
     * Параметры пула для роли БД.
     *
     * @param role роль БД (`LOCAL` / `SERVER`)
     * @return параметры пула или `null`, если пул выключен
     */
    fun configFor(role: String): PoolConfig? {
        if (!resolved) return null
        if (!enabled) return null
        // Роль VIRTUAL — тестовая (in-memory), в проде не используется и
        // помечена `@Suppress("unused")`. Пул для неё не создаётся: незачем
        // поднимать источник каналов к базе, которой нет.
        if (role == "VIRTUAL") return null
        val maxSize = if (role == "SERVER") maxServer else maxLocal
        return PoolConfig(
            maxPoolSize = maxSize,
            minIdle = minIdle,
            acquireTimeoutMs = acquireTimeoutMs,
            maxLifetimeMs = maxLifetimeMs,
            keepaliveMs = keepaliveMs,
            validationTimeoutMs = validationTimeoutMs,
        )
    }

    /**
     * Проверяет, что сумма потолков пулов процесса укладывается в лимит БД
     * с запасом на административные подключения (FR-010).
     *
     * Вызывается при заполнении настроек — то есть на старте: неверный
     * потолок должен обнаруживаться сразу, а не при первом обращении к БД.
     *
     * @throws IllegalArgumentException если сумма превышает лимит
     */
    fun validateAgainstDbLimit() {
        PoolConfig.requireWithinDbLimit(listOf(maxLocal, maxServer))
    }
}
