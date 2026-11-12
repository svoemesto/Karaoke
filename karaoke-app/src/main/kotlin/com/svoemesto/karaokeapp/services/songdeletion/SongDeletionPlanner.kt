package com.svoemesto.karaokeapp.services.songdeletion

/**
 * План каскадного удаления связанных записей песни (KARAOKE-429, спека 739, FR-005…FR-008).
 *
 * **Белый список таблиц — главный рубеж защиты (FR-046).** План строится из
 * фиксированного списка: никаких «похожих» таблиц, никакого отражения по имени
 * колонки. Таблица входит в план только потому, что она перечислена здесь.
 *
 * **Что НЕ удаляется:**
 * - `tbl_news` — новость остаётся в ленте, теряет только привязку (FR-009; делает
 *   схема: FK `ON DELETE SET NULL`, миграция 58);
 * - `audit_log`, `songs_history.txt`, старые записи кеша хранилища (FR-038);
 * - `tbl_songs` самой удаляемой песни — это отдельный шаг плана, он же удаляет строку;
 * - `tbl_albums`/`tbl_pictures` — только если песня была последней в альбоме (FR-018);
 *   это делает исполнитель уборки, а не план БД.
 *
 * @see specs/739-song-delete-cascade/spec.md FR-005, FR-006, FR-007, FR-008, FR-046
 */
object SongDeletionPlanner {
    /** Назначения редакторов песни. */
    const val TABLE_SONG_ASSIGNMENTS = "tbl_song_assignments"

    /** Черновики редакторов — удаляются ДО назначений (связь по `assignment_id`). */
    const val TABLE_SONG_ASSIGNMENT_DRAFTS = "tbl_song_assignment_drafts"

    /** Позиции песен в плейлистах сайта. */
    const val TABLE_SITE_PLAYLIST_ITEMS = "tbl_site_playlist_items"

    /** Ссылки на песню, выданные посетителям. */
    const val TABLE_SONG_SHARE_LINKS = "tbl_song_share_links"

    /** История прослушиваний. */
    const val TABLE_LISTENING_HISTORY = "tbl_listening_history"

    /** Снимки результатов поиска. */
    const val TABLE_SEARCH_RESULTS = "tbl_search_results"

    /** Асинхронные снимки поиска. */
    const val TABLE_SEARCH_ASYNC = "tbl_search_async"

    /** События песни (миллионы строк — чистим только `song_id <> 0`). */
    const val TABLE_EVENTS = "tbl_events"

    /** Каталог песен — обнуление связи у потомков (`audio_parent_id`). */
    const val TABLE_SONGS = "tbl_songs"

    /**
     * Белый список таблиц каскада (FR-005, FR-006).
     *
     * Порядок важен: черновики идут перед назначениями (черновик ссылается на
     * назначение по `assignment_id`), события — последними (самая большая таблица).
     */
    val CASCADE_TABLES: List<String> =
        listOf(
            TABLE_SONG_ASSIGNMENT_DRAFTS,
            TABLE_SONG_ASSIGNMENTS,
            TABLE_SITE_PLAYLIST_ITEMS,
            TABLE_SONG_SHARE_LINKS,
            TABLE_LISTENING_HISTORY,
            TABLE_SEARCH_RESULTS,
            TABLE_SEARCH_ASYNC,
            TABLE_EVENTS,
        )

    /**
     * Один шаг плана: таблица + SQL + параметры.
     *
     * @property table имя таблицы (для журнала и проверки белого списка).
     * @property sql SQL с параметрами-«?».
     * @property params значения параметров.
     */
    data class CascadeStep(
        val table: String,
        val sql: String,
        val params: List<Any?>,
    )

    /**
     * Построить план каскада для песни.
     *
     * @param songId идентификатор удаляемой песни.
     * @return список шагов; каждый шаг — из белого списка [CASCADE_TABLES].
     */
    fun plan(songId: Long): List<CascadeStep> {
        val steps = mutableListOf<CascadeStep>()
        // Черновики — по назначениям этой песни (у черновика нет своей song_id).
        steps.add(
            CascadeStep(
                table = TABLE_SONG_ASSIGNMENT_DRAFTS,
                sql =
                    "DELETE FROM $TABLE_SONG_ASSIGNMENT_DRAFTS " +
                        "WHERE assignment_id IN (SELECT id FROM $TABLE_SONG_ASSIGNMENTS WHERE song_id = ?)",
                params = listOf(songId),
            ),
        )
        steps.add(
            CascadeStep(
                table = TABLE_SONG_ASSIGNMENTS,
                sql = "DELETE FROM $TABLE_SONG_ASSIGNMENTS WHERE song_id = ?",
                params = listOf(songId),
            ),
        )
        steps.add(
            CascadeStep(
                table = TABLE_SITE_PLAYLIST_ITEMS,
                sql = "DELETE FROM $TABLE_SITE_PLAYLIST_ITEMS WHERE song_id = ?",
                params = listOf(songId),
            ),
        )
        steps.add(
            CascadeStep(
                table = TABLE_SONG_SHARE_LINKS,
                sql = "DELETE FROM $TABLE_SONG_SHARE_LINKS WHERE song_id = ?",
                params = listOf(songId),
            ),
        )
        steps.add(
            CascadeStep(
                table = TABLE_LISTENING_HISTORY,
                sql = "DELETE FROM $TABLE_LISTENING_HISTORY WHERE song_id = ?",
                params = listOf(songId),
            ),
        )
        steps.add(
            CascadeStep(
                table = TABLE_SEARCH_RESULTS,
                sql = "DELETE FROM $TABLE_SEARCH_RESULTS WHERE song_id = ?",
                params = listOf(songId),
            ),
        )
        steps.add(
            CascadeStep(
                table = TABLE_SEARCH_ASYNC,
                sql = "DELETE FROM $TABLE_SEARCH_ASYNC WHERE song_id = ?",
                params = listOf(songId),
            ),
        )
        // FR-007: события БЕЗ привязки к песне (song_id = 0) не трогаем.
        steps.add(
            CascadeStep(
                table = TABLE_EVENTS,
                sql = "DELETE FROM $TABLE_EVENTS WHERE song_id = ? AND song_id <> 0",
                params = listOf(songId),
            ),
        )
        return steps
    }

    /**
     * Шаг обнуления связи потомков (FR-008): у родственных песен
     * `audio_parent_id` становится «нет родителя», сами потомки остаются живыми.
     *
     * **Значение «нет родителя» — `0`, а не `NULL`** (факт схемы LOCAL/SERVER:
     * `tbl_songs.audio_parent_id integer NOT NULL DEFAULT 0`, см. также
     * `SongDTO.audioParentId` — «используется 0 как «нет значения»»). Запись `NULL`
     * нарушала бы NOT NULL и роняла всю транзакцию фазы A.
     *
     * @param songId идентификатор удаляемого родителя.
     */
    fun descendantsStep(songId: Long): CascadeStep =
        CascadeStep(
            table = TABLE_SONGS,
            sql = "UPDATE $TABLE_SONGS SET audio_parent_id = 0 WHERE audio_parent_id = ?",
            params = listOf(songId),
        )

    /**
     * Шаг удаления самой строки песни.
     *
     * @param songId идентификатор удаляемой песни.
     * @param syncTable true — удалять из `tbl_songs_sync` (SERVER, В-07).
     */
    fun songStep(
        songId: Long,
        syncTable: Boolean = false,
    ): CascadeStep =
        CascadeStep(
            table = if (syncTable) "${TABLE_SONGS}_sync" else TABLE_SONGS,
            sql = "DELETE FROM ${if (syncTable) "${TABLE_SONGS}_sync" else TABLE_SONGS} WHERE id = ?",
            params = listOf(songId),
        )
}
