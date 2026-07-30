package com.svoemesto.karaokeapp.services

import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.model.News
import com.svoemesto.karaokeapp.model.Song
import com.svoemesto.karaokeapp.model.SongNewsAnnounced

/**
 * Автоматическое создание новостей о выходе песни в эфир (specs/089-auto-news-song-release).
 * Единственный вызывающий — `karaoke-web` `MainController.doChangeRecords` (PROD-only, единственная
 * точка кода, реально исполняемая на PROD в момент существующего механизма синхронизации таблиц —
 * см. research.md фичи 083, п.1).
 *
 * @see docs/features/dual-db-sync.md
 */
object SongReleaseAnnouncementService {
    // Тот же порядок величины, что SongSyncTarget.rowChunkSize (25) — тяжёлые текстовые поля/маркеры
    // одной песни делают полную загрузку тысяч строк разом OOM-опасной (см.
    // specs/082-fix-import-folder-oom, тот же класс бага).
    private const val CHUNK_SIZE = 25

    /**
     * Дешёвый первый проход: только `id` + `recordhash` по всем песням со статусом >= 6 (без
     * текста/маркеров/base64 — `Song.listHashes`, тот же путь, что использует sync-движок), минус уже
     * отмеченные в `tbl_song_news_announced`. Полные объекты (нужны для проверки
     * [Song.isPubliclyWatchable] — стемы/обложки/маркеры) грузятся пачками по [CHUNK_SIZE] через
     * `WHERE id IN (...)` и обрабатываются [action] СРАЗУ ЖЕ, чанк за чанком — результат НЕ
     * накапливается в единый список между чанками (флаг `chunked(...).flatMap {...}` тихо собрал бы
     * все полные объекты разом и воспроизвёл бы тот же `OutOfMemoryError`, что чанкование запроса
     * само по себе не предотвращает).
     */
    private fun forEachNewlyReadyCandidate(
        database: KaraokeConnection,
        storageService: KaraokeStorageService,
        storageApiClient: StorageApiClient,
        action: (Song) -> Unit,
    ) {
        val alreadyAnnounced = SongNewsAnnounced.loadAnnouncedSongIds(database)
        val candidateIds =
            (Song.listHashes(database = database, whereText = "WHERE id_status >= 6") ?: emptyList())
                .map { it.id }
                .filter { it !in alreadyAnnounced }
        candidateIds.chunked(CHUNK_SIZE).forEach { chunk ->
            Song
                .loadListFromDb(
                    args = mapOf("ids" to chunk.joinToString(",")),
                    database = database,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                ).filter { it.isPubliclyWatchable }
                .forEach(action)
        }
    }

    /**
     * Альбом и год песни в виде суффикса `" (альбом «X», Y)"` для заголовка новости
     * (specs/092-fix-auto-news-triggers) — пустая строка, если ни альбом, ни год не заполнены;
     * без плейсхолдеров для отдельно отсутствующего альбома/года (FR-008 spec.md).
     */
    private fun albumYearSuffix(song: Song): String {
        val parts = mutableListOf<String>()
        if (song.album.isNotBlank()) parts.add("альбом «${song.album}»")
        if (song.year > 0) parts.add(song.year.toString())
        return if (parts.isEmpty()) "" else " (" + parts.joinToString(", ") + ")"
    }

    /** Автор + альбом/год одной строкой для тела новости (specs/092-fix-auto-news-triggers). */
    private fun bodyDetails(song: Song): String {
        val parts = mutableListOf(song.author)
        if (song.album.isNotBlank()) parts.add("альбом «${song.album}»")
        if (song.year > 0) parts.add(song.year.toString())
        return parts.joinToString(", ")
    }

    /**
     * Находит песни, ставшие публично доступными ([Song.isPubliclyWatchable]), но ещё не
     * анонсированные, и создаёт по каждой отдельную новость (FR-006 spec.md). Идемпотентно —
     * безопасно вызывать многократно, в т.ч. параллельно (см. `PRIMARY KEY(song_id)` в
     * `tbl_song_news_announced`, `SongNewsAnnounced.markAnnounced`) и из трёх независимых вызывающих
     * точек (синхронизация, периодическая проверка эфира, апрув/сохранение — см.
     * specs/092-fix-auto-news-triggers/contracts/news-triggers.md). Ошибки логируются и не
     * пробрасываются — сбой детекции анонсов не должен ронять вызывающий код
     * (см. contracts/news-api.md).
     *
     * `storageService`/`storageApiClient` ДОЛЖНЫ передаваться явно вызывающим кодом (без дефолта на
     * `KSS_APP`/`SAC_APP`) — эта функция вызывается и из `karaoke-app` (одна JVM, где эти lateinit
     * глобальные переменные проинициализированы), и из `karaoke-web` (другая JVM, другая реализация
     * `KaraokeStorageService`/`StorageApiClient`, инжектируемая через Spring DI в `MainController`
     * — `KSS_APP`/`SAC_APP` там никогда не инициализируются и обращение к ним падает с
     * `lateinit property ... has not been initialized`, что и произошло при первой ручной проверке
     * — см. tasks.md T012).
     *
     * @return id песен, для которых в этом вызове была создана новость.
     */
    fun checkAndAnnounce(
        database: KaraokeConnection,
        storageService: KaraokeStorageService,
        storageApiClient: StorageApiClient,
    ): List<Long> {
        val created = mutableListOf<Long>()
        try {
            forEachNewlyReadyCandidate(database, storageService, storageApiClient) { song ->
                val news =
                    News.createAutoAnnouncement(
                        songId = song.id,
                        title = "Новая песня: ${song.author} — ${song.songName}${albumYearSuffix(song)}",
                        body = "Стала доступна песня «${song.songName}» (${bodyDetails(song)}).",
                        link = "/song?id=${song.id}",
                        database = database,
                        storageService = storageService,
                        storageApiClient = storageApiClient,
                    )
                if (SongNewsAnnounced.markAnnounced(songId = song.id, newsId = news?.id, database = database)) {
                    created.add(song.id)
                }
            }
        } catch (e: Exception) {
            println("SongReleaseAnnouncementService.checkAndAnnounce error: ${e.message}")
        }
        return created
    }

    /**
     * Одноразовый backfill при включении фичи на PROD (FR-005 spec.md, User Story 3) — помечает уже
     * публично доступные песни как «анонсированные» БЕЗ создания видимой новости, чтобы первое
     * включение механизма не создало лавину исторических новостей (см. research.md, п.5). Вызывается
     * вручную и отдельно от [checkAndAnnounce], один раз, до начала штатной работы этого механизма.
     *
     * @return число песен, помеченных backfill'ом в этом вызове.
     */
    fun backfillExistingReadySongs(
        database: KaraokeConnection,
        storageService: KaraokeStorageService,
        storageApiClient: StorageApiClient,
    ): Int {
        var count = 0
        try {
            forEachNewlyReadyCandidate(database, storageService, storageApiClient) { song ->
                if (SongNewsAnnounced.markAnnounced(songId = song.id, newsId = null, database = database)) count++
            }
        } catch (e: Exception) {
            println("SongReleaseAnnouncementService.backfillExistingReadySongs error: ${e.message}")
        }
        return count
    }
}
