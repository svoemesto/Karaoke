package com.svoemesto.karaokeapp.services

/**
 * Состояние автопубликации в группе ВКонтакте (specs/121-vk-news-auto-publish).
 *
 * Для типичного случая (новость `air` связана с песней через `tbl_news.song_id`,
 * или ручная `premium`-публикация) состояние хранится в
 * [com.svoemesto.karaokeapp.model.Song.playerReadinessFlags] JSON-блобе как
 * ключ `vkAutoPublishState` (паттерн specs/101-song-news-flag — без новой
 * колонки, без правки recordhash-триггера). Производное значение для UI/логики
 * вычисляет [com.svoemesto.karaokeapp.model.Song.effectiveVkAutoPublishState]:
 * `PUBLISHED` определяется по заполненному `Song.idVk` (FR-008), а не по самому
 * полю state — чтобы любая попытка записи (бот или ручная) согласованно
 * отражалась в UI.
 *
 * Для редкого случая (ручная `air`-новость без `song_id`, FR-004a) — состояние
 * хранится в `News.playerReadinessFlags` (аналогичный JSON-блоб), т.к. нет
 * песни для записи `idVk`.
 *
 * @see docs/features/vk-news-auto-publish.md
 */
enum class VkAutoPublishState(
    val code: String,
) {
    /** Новость опубликована (`publish_at <= now()`), `idVk` пуст, бот ещё не начинал. */
    SCHEDULED("scheduled"),

    /** Бот рендерит демо-MP4 (FR-020 сц. 2 или 3 — файла нет или превышает лимит). */
    RENDERING("rendering"),

    /** Демо-MP4 готов, бот делает `video.save` + `wall.post` (с ретраями FR-009). */
    PUBLISHING("publishing"),

    /** `Song.idVk` заполнен успешно (FR-004). */
    PUBLISHED("published"),

    /** Все ретраи FR-009 исчерпаны, последняя попытка в `vkAutoPublishLastError`. */
    SEND_FAILED("send_failed"),

    /** (Опц.) администратор удалил новость — только для редкого случая FR-004a. */
    CANCELLED("cancelled"),

    ;

    companion object {
        /** Возвращает состояние по коду или `null`, если код не сопоставлен ни одному значению. */
        fun fromCode(code: String?): VkAutoPublishState? = entries.firstOrNull { it.code == code }
    }
}
