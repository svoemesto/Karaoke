package com.svoemesto.karaokeapp.services

/**
 * Состояние автопубликации демо-версии песни в Telegram-канал (Фаза 2, specs/113-telegram-demo-publish).
 *
 * Хранится как строковый ключ `telegramAutoPublishState` внутри JSON-блоба
 * [com.svoemesto.karaokeapp.model.Song.playerReadinessFlags] (паттерн
 * specs/101-song-news-flag — без новой колонки, без правки recordhash-триггера).
 * Производное значение для UI/логики вычисляет
 * [com.svoemesto.karaokeapp.model.Song.effectiveTelegramAutoPublishState]:
 * `PUBLISHED` определяется по заполненному `idTelegramDemo` (FR-008), а не по
 * самому полю state — чтобы любая попытка записи (Фаза 2 или ручная Фаза 1
 * через TelegramUpdatesConsumer) согласованно отражалась в UI.
 *
 * @see docs/features/telegram-auto-publish.md
 */
enum class TelegramAutoPublishState(
    val code: String,
) {
    /** date/time заполнены, в будущем, бот ещё не начинал. */
    SCHEDULED("scheduled"),

    /** Бот рендерит демо-MP4 (FR-003 сц. 2 или 3 — файла нет или превышает лимит 50 МБ). */
    RENDERING("rendering"),

    /** Демо-MP4 готов, бот делает sendVideo (с ретраями FR-010). */
    PUBLISHING("publishing"),

    /** `idTelegramDemo` заполнен успешно (FR-006). */
    PUBLISHED("published"),

    /** Все ретраи FR-010 исчерпаны, последняя попытка в `telegramAutoPublishLastError`. */
    SEND_FAILED("send_failed"),

    /** Админ очистил date/time (бот не публикует, пока дата снова не в будущем). */
    CANCELLED("cancelled"),

    ;

    companion object {
        /** Возвращает состояние по коду или `null`, если код не сопоставлен ни одному значению. */
        fun fromCode(code: String?): TelegramAutoPublishState? = entries.firstOrNull { it.code == code }
    }
}
