package com.svoemesto.karaokeapp.model

import java.io.Serializable

// Тип альбома (студийный/концертный/сборник/бутлег/архив). Хранится в tbl_albums.album_type в
// lowercase-форме dbValue, по образцу SongType — enum только типобезопасная обёртка над уже
// сохранённой строкой (не .name/.ordinal), чтобы переименование констант в коде не требовало
// миграции данных. Значение по умолчанию — STUDIO (dbValue "studio").

/**
 * Перечисление возможных значений для album type.
 *
 * `groupLabel`/`filterLabel` — канонические русские подписи для группировки/быстрых фильтров
 * альбомов на Закромах (specs/012-entity-description-fields FR-018/024/025): `groupLabel` —
 * заголовок раздела ("Студийные альбомы"), `filterLabel` — подпись кнопки фильтра (короче,
 * без слова "альбомы"/"альбом", счётчик добавляется отдельно фронтом).
 *
 * @see docs/features/dual-db-sync.md
 * @see specs/012-entity-description-fields/spec.md
 */
@Suppress("unused")
enum class AlbumType(
    val dbValue: String,
    val description: String,
    val groupLabel: String,
    val filterLabel: String,
) : Serializable {
    STUDIO(dbValue = "studio", description = "Студийный альбом", groupLabel = "Студийные альбомы", filterLabel = "Студийные"),
    LIVE(dbValue = "live", description = "Концертный альбом", groupLabel = "Концертные альбомы", filterLabel = "Концертные"),
    COMPILATION(dbValue = "compilation", description = "Сборник", groupLabel = "Сборники", filterLabel = "Сборники"),
    BOOTLEG(dbValue = "bootleg", description = "Бутлег", groupLabel = "Бутлеги", filterLabel = "Бутлеги"),
    SINGLE(dbValue = "single", description = "Сингл", groupLabel = "Синглы", filterLabel = "Синглы"),
    ARCHIVE(dbValue = "archive", description = "Исторические/архивные записи", groupLabel = "Архивные записи", filterLabel = "Архивные"),
    ;

    companion object {
        fun fromDb(value: String?): AlbumType = entries.find { it.dbValue == value } ?: STUDIO

        /**
         * Порядок группировки/фильтров альбомов на Закромах (FR-024): студийные → синглы →
         * концертные → сборники → бутлеги → архивные — НЕ порядок объявления констант enum выше.
         */
        val ZAKROMA_GROUP_ORDER: List<AlbumType> = listOf(STUDIO, SINGLE, LIVE, COMPILATION, BOOTLEG, ARCHIVE)
    }
}
