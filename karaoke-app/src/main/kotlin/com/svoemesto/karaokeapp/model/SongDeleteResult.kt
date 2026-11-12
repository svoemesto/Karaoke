package com.svoemesto.karaokeapp.model

import java.io.Serializable

/**
 * Итог удаления строки песни из БД (KARAOKE-429, спека 739, FR-002).
 *
 * **Зачем отдельный тип.** Раньше `Song.deleteFromDb` возвращал `Unit` и глотал
 * исключения БД: при отказе базы вызывающий ничего не знал и отвечал `200` с
 * пустым телом — это и есть «ложный успех» (SC-003): карточка песни закрывалась,
 * песня оставалась в базе. Теперь результат — честный признак фактического
 * удаления, и ошибка видна вызывающему.
 *
 * @property status что произошло.
 * @property deletedRows число удалённых строк (`tbl_songs`).
 * @property error причина отказа БД (для `DB_ERROR`).
 */
data class SongDeleteResult(
    val status: SongDeleteStatus,
    val deletedRows: Int = 0,
    val error: String? = null,
) : Serializable {
    /** Удаление состоялось. */
    val isDeleted: Boolean get() = status == SongDeleteStatus.DELETED

    /** Строки песни уже не было — операция идемпотентна, это не ошибка. */
    val isNotFound: Boolean get() = status == SongDeleteStatus.NOT_FOUND

    /** Отказ базы данных. */
    val isDbError: Boolean get() = status == SongDeleteStatus.DB_ERROR
}

/**
 * Возможные исходы удаления строки песни (FR-002).
 *
 * - `DELETED` — строка удалена;
 * - `NOT_FOUND` — такой песни уже нет (повторное удаление — успех, не ошибка);
 * - `DB_ERROR` — отказ базы данных, ничего не удалено.
 */
enum class SongDeleteStatus {
    DELETED,
    NOT_FOUND,
    DB_ERROR,
}
