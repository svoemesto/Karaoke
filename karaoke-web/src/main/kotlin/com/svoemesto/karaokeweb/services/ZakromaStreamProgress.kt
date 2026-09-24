package com.svoemesto.karaokeweb.services

/**
 * Чистая логика выбора знаменателя (`meta.expectedCount`) для NDJSON-стрима
 * `/api/public/zakroma/stream` (specs/444-fix-album-progress, issue #179).
 *
 * Вынесена из [com.svoemesto.karaokeweb.controllers.PublicApiController] ради
 * offline-тестируемости без Spring-контекста и БД (паттерн `SongStateResolver`).
 *
 * Контракт прогресса (спека 181): `expectedCount` в первом NDJSON-сообщении
 * `meta` — знаменатель «Загружаем X из N…». Он MUST совпадать с числом,
 * которое видит посетитель на плашке:
 * - без `albumId` — общее число песен автора (`Song.loadAuthorSongCounts`);
 * - с `albumId` — число песен конкретного альбома (`total_song_count` для
 *   редактора / `ready_song_count` для гостя), а не число песен всего автора.
 *
 * @see specs/444-fix-album-progress/spec.md FR-001..FR-005
 * @see archive/docs/features/zakroma-stream-progress.md
 */
object ZakromaStreamProgress {
    /**
     * Счётчики песен одного альбома (денормализованные колонки `tbl_albums`).
     *
     * @property readySongCount число песен с `id_status >= 6` (для гостя).
     * @property totalSongCount общее число песен альбома (для редактора).
     */
    data class AlbumCounters(
        val readySongCount: Long,
        val totalSongCount: Long,
    )

    /**
     * Выбирает `expectedCount` для `meta`-сообщения.
     *
     * Приоритет:
     * 1. `albumId != null` — album-scoped значение авторитетно на сервере:
     *    `ready_song_count` для гостя (`onlyPublished=true`) или
     *    `total_song_count` для редактора. `album == null` → `0` (альбом не найден,
     *    FR-005 — а не число песен автора).
     * 2. `providedExpectedCount > 0` — доверяем значению фронта (счётчик с тайла
     *    автора, спека 181; экономит DB-запрос).
     * 3. Иначе — [authorCountFallback] (lazy: `Song.loadAuthorSongCounts` по автору).
     *
     * @param albumId id альбома из query (`?albumId=`), `null` — фильтр не задан.
     * @param album счётчики альбома или `null`, если альбом не найден.
     * @param onlyPublished `true` для гостя/премиума (только `id_status >= 6`).
     * @param providedExpectedCount значение, присланное фронтом (`expectedCount`), или `null`.
     * @param authorCountFallback lazy-fallback по автору; НЕ вызывается, когда задан `albumId`.
     * @return знаменатель для `meta.expectedCount`.
     */
    fun resolveExpectedCount(
        albumId: Long?,
        album: AlbumCounters?,
        onlyPublished: Boolean,
        providedExpectedCount: Long?,
        authorCountFallback: () -> Long,
    ): Long =
        when {
            albumId != null ->
                album?.let { if (onlyPublished) it.readySongCount else it.totalSongCount } ?: 0L
            providedExpectedCount != null && providedExpectedCount > 0 -> providedExpectedCount
            else -> authorCountFallback()
        }
}
