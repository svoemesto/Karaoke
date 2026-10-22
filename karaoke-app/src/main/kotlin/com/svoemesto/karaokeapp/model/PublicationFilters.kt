package com.svoemesto.karaokeapp.model

/**
 * Чистые предикаты фильтров админского раздела «Публикации» (OpenProject #326).
 *
 * Вынесены из [Publication], чтобы семантику можно было проверить
 * юнит-тестами без БД: сам [Song] в тестах не инстанцируется (конструктор
 * требует окружения).
 *
 * @see docs/features/publications-filters.md
 */
object PublicationFilters {
    /**
     * Тег `SKIP` в `tbl_songs.tags`: список слов через пробел, сравнение без
     * учёта регистра. Та же логика, что в `Zakroma.songHasSkipTag` и
     * `ListeningHistoryController.songHasSkipTag`.
     */
    fun hasSkipTag(tags: String): Boolean = tags.split(" ").any { it.trim().equals("SKIP", ignoreCase = true) }

    /**
     * UNPUBLISH: песня полностью готова (статус 6), публикация не назначена
     * (пусты и дата, и время), песня не «всегда в эфире» (`free = false`) и не
     * исключена как skip.
     *
     * Условие `exclusive = true AND id_sponsr = ''` намеренно не используется:
     * по решению владельца (2026-10-22) эксклюзивная песня без даты тоже
     * считается неопубликованной.
     *
     * @param authorSkipped автор песни помечен `tbl_authors.skip = true`.
     */
    fun isUnpublished(
        idStatus: Long,
        publishDate: String,
        publishTime: String,
        free: Boolean,
        authorSkipped: Boolean,
        tags: String,
    ): Boolean =
        idStatus == 6L &&
            publishDate.isEmpty() &&
            publishTime.isEmpty() &&
            !free &&
            !authorSkipped &&
            !hasSkipTag(tags)

    /**
     * UNDONE: песня не полностью готова (статус < 6, включая 5 — «отрендерено,
     * ждёт approve») и не исключена как skip.
     *
     * Дата/время и `free` намеренно не влияют: неготовая песня не публикуется,
     * даже если помечена `free`.
     */
    fun isUndone(
        idStatus: Long,
        authorSkipped: Boolean,
        tags: String,
    ): Boolean = idStatus < 6L && !authorSkipped && !hasSkipTag(tags)
}
