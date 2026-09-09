package com.svoemesto.karaokeweb.dto

import com.svoemesto.karaokeapp.model.Album
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Публичный DTO плашки альбома для эндпоинта
 * `GET /api/public/authors/{authorId}/albums?scope=main`.
 *
 * Используется на странице `/zakroma/{authorId}/albums` публичного сайта
 * и в секции «Альбомы автора» на странице `/zakroma/{authorId}`.
 *
 * Backward compatible — добавление полей не ломает существующих клиентов.
 *
 * @property id Идентификатор альбома (`tbl_albums.id`).
 * @property name Название альбома. Может быть обрезано в UI многоточием (`...`); полное название — в tooltip.
 * @property year Год выпуска. `0` или `null` → не отображается в подписи.
 * @property pictureUrl URL обложки альбома 200×200 в MinIO; пустая строка если нет.
 * @property totalSongCount Общее кол-во песен альбома (для редакторов).
 * @property readySongCount Кол-во готовых песен (`id_status >= 6`, для гостей).
 * @property albumType Тип альбома (`"studio"` / `"live"` / `"compilation"` / `"bootleg"`, см. `AlbumType.dbValue`).
 *
 * @see docs/features/zakroma-albums-by-author.md
 * @see specs/356-zakroma-albums-by-author/spec.md FR-014
 * @see specs/356-zakroma-albums-by-author/contracts/albums-tiles-api.md
 */
data class AlbumTilePublicDto(
    val id: Long,
    val name: String,
    val year: Int,
    val pictureUrl: String,
    val totalSongCount: Long,
    val readySongCount: Long,
    val albumType: String,
) {
    companion object {
        /**
         * Bucket `karaoke` — основной публичный bucket для превью альбомов (см. SPEC.md FR-002, C1).
         * URL-encoding: `URLEncoder.encode` заменяет пробелы на `+`, а nginx ожидает `%20`.
         * Поэтому дополнительно заменяем `+` на `%20` (паттерн `AuthorTilePublicDto`).
         */
        private const val BUCKET = "karaoke"

        /**
         * Строит URL обложки альбома в MinIO.
         * Шаблон: `<author>/<year> - <name>/<author> - <year> - <name>.preview.album.png`.
         * Пустая строка если поля пустые.
         */
        fun albumPictureUrl(
            authorName: String,
            year: Int,
            albumName: String,
        ): String {
            if (authorName.isEmpty() || year <= 0 || albumName.isEmpty()) return ""
            val previewFileName = "$authorName/$year - $albumName/$authorName - $year - $albumName.preview.album.png"
            val encoded = URLEncoder.encode(previewFileName, StandardCharsets.UTF_8).replace("+", "%20")
            return "/minio/$BUCKET/$encoded"
        }

        /**
         * Маппинг [Album] (raw model из karaoke-app) → [AlbumTilePublicDto].
         * Требует имя автора (для URL обложки и шаблона пути в MinIO).
         *
         * @param album загруженная запись `tbl_albums` (после T008 `Album.loadAlbumTilesWithCounts`).
         * @param authorName имя автора (из `tbl_authors.author`); пустая строка если author_id не найден.
         * @return готовый DTO для JSON-сериализации в endpoint.
         */
        fun fromAlbum(
            album: Album,
            authorName: String,
        ): AlbumTilePublicDto {
            val pictureUrl =
                albumPictureUrl(
                    authorName = authorName,
                    year = album.year,
                    albumName = album.name,
                )
            return AlbumTilePublicDto(
                id = album.id,
                name = album.name,
                year = album.year,
                pictureUrl = pictureUrl,
                totalSongCount = album.totalSongCount,
                readySongCount = album.readySongCount,
                albumType = album.albumType,
            )
        }
    }
}
