package com.svoemesto.karaokeapp.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.WORKING_DATABASE
import com.svoemesto.karaokeapp.services.KSS_APP
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.SAC_APP
import com.svoemesto.karaokeapp.services.StorageApiClient
import java.io.Serializable
import java.sql.Timestamp

// Модель tbl_events только для LOCAL<->SERVER синхронизации (sync/SyncTarget.kt). Чтение статистики
// для вьюхи "Статистика" по-прежнему идёт через StatBySong.kt/StatsByEvents (ручной JDBC) — не трогать.
@JsonIgnoreProperties(value = ["database", "sqlToInsert"])
class WebEvent(
    override val database: KaraokeConnection = WORKING_DATABASE,
    override val storageService: KaraokeStorageService = KSS_APP,
    override val storageApiClient: StorageApiClient = SAC_APP,
) : Serializable, Comparable<WebEvent>, KaraokeDbTable {

    override fun getTableName() = TABLE_NAME

    @KaraokeDbTableField(name = "id", isId = true)
    override var id: Long = 0

    // Nullable: реальные данные tbl_events (250k+ строк, разные event_type) содержат SQL NULL в этих
    // колонках сплошь и рядом (например rest_name/rest_parameters заполнены только для REST-событий,
    // link_type/link_name — только для кликов по ссылкам). generic reflection-loader (KaraokeDbTable.
    // loadList) вызывает rs.getString()/rs.getTimestamp() напрямую в property.setter — non-null String
    // здесь даёт "Parameter specified as non-null is null" NPE на первой же NULL-строке. song_id — Long
    // (не Long?) намеренно: JDBC rs.getLong() на SQL NULL возвращает примитивный 0, а не null, поэтому
    // NPE ему не грозит независимо от nullability колонки в БД.
    @KaraokeDbTableField(name = "event_type")
    var eventType: String? = null

    @KaraokeDbTableField(name = "rest_name")
    var restName: String? = null

    @KaraokeDbTableField(name = "rest_parameters")
    var restParameters: String? = null

    @KaraokeDbTableField(name = "link_type")
    var linkType: String? = null

    @KaraokeDbTableField(name = "link_name")
    var linkName: String? = null

    @KaraokeDbTableField(name = "song_id")
    var songId: Long = 0

    @KaraokeDbTableField(name = "song_version")
    var songVersion: String? = null

    @KaraokeDbTableField(name = "last_update")
    var lastUpdate: Timestamp? = null

    @KaraokeDbTableField(name = "referer")
    var referer: String? = null

    override fun compareTo(other: WebEvent): Int =
        (lastUpdate ?: Timestamp(0)).compareTo(other.lastUpdate ?: Timestamp(0))

    override fun toDTO(): WebEventDTO = WebEventDTO(
        id = id,
        eventType = eventType ?: "",
        restName = restName ?: "",
        restParameters = restParameters ?: "",
        linkType = linkType ?: "",
        linkName = linkName ?: "",
        songId = songId,
        songVersion = songVersion ?: "",
        lastUpdate = lastUpdate?.toString() ?: "",
        referer = referer ?: "",
    )

    companion object {
        const val TABLE_NAME = "tbl_events"
    }
}
