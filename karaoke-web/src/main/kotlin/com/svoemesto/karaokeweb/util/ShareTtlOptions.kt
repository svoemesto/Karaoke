package com.svoemesto.karaokeweb.util

/**
 * Допустимые сроки действия share-ссылки (KARAOKE-399).
 *
 * Единый источник правды для бэкенда: контроллер создания ссылки проверяет TTL по
 * [ALLOWED_SECONDS], а не по литералам в условии. Фронт (`karaoke-public`,
 * `SHARE_TTL_OPTIONS` в `useShareLink.js`) показывает те же три варианта — список
 * продублирован на границе двух приложений, и при изменении здесь его надо менять
 * и там.
 *
 * Значения (в секундах):
 * - [ONE_HOUR] — 1 час;
 * - [ONE_DAY] — 24 часа;
 * - [ONE_WEEK] — 7 дней.
 */
object ShareTtlOptions {
    /** 1 час. */
    const val ONE_HOUR: Long = 3600

    /** 24 часа. */
    const val ONE_DAY: Long = 86_400

    /** 7 дней. */
    const val ONE_WEEK: Long = 604_800

    /** Срок по умолчанию — 1 час (значение `ttlSeconds` по умолчанию в контроллере). */
    const val DEFAULT_SECONDS: Long = ONE_HOUR

    /** Все допустимые сроки — конечное множество. */
    val ALLOWED_SECONDS: Set<Long> = setOf(ONE_HOUR, ONE_DAY, ONE_WEEK)

    /**
     * Допустим ли такой срок.
     *
     * @param ttlSeconds запрошенный срок в секундах.
     * @return `true`, если срок входит в [ALLOWED_SECONDS].
     */
    fun isAllowed(ttlSeconds: Long): Boolean = ttlSeconds in ALLOWED_SECONDS
}
