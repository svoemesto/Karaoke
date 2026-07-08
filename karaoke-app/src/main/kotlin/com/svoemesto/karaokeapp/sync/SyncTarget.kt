package com.svoemesto.karaokeapp.sync

import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.KaraokeProperties
import com.svoemesto.karaokeapp.model.Author
import com.svoemesto.karaokeapp.model.Dictionary
import com.svoemesto.karaokeapp.model.KaraokeDbTable
import com.svoemesto.karaokeapp.model.Pictures
import com.svoemesto.karaokeapp.model.RecordDiff
import com.svoemesto.karaokeapp.model.RecordHash
import com.svoemesto.karaokeapp.model.Settings
import com.svoemesto.karaokeapp.model.SitePlaylist
import com.svoemesto.karaokeapp.model.SitePlaylistItem
import com.svoemesto.karaokeapp.model.SiteUser
import com.svoemesto.karaokeapp.model.SongAssignment
import com.svoemesto.karaokeapp.model.SongAssignmentDraft
import com.svoemesto.karaokeapp.model.WebEvent
import com.svoemesto.karaokeapp.services.KSS_APP
import com.svoemesto.karaokeapp.services.SAC_APP
import kotlin.reflect.KClass

enum class SyncDirection { LOCAL_TO_SERVER, SERVER_TO_LOCAL }

/**
 * Операции, разрешённые для сущности при синхронизации (per-table × per-direction флаги).
 * INSERT/UPDATE/DELETE относятся к ЦЕЛИ, MOVE — режим «перемещение»: после подтверждённого переноса
 * удалить перенесённые строки из ИСТОЧНИКА (см. collectSyncOps/updateDatabases в Utils.kt).
 * DELETE (зеркальное удаление в цели) и MOVE (удаление в источнике) взаимоисключающи по смыслу.
 * [dbValue] — суффикс ключа KaraokeProperty (не .name/ordinal), [ruName] — подпись столбца в webvue3.
 */
enum class SyncOperation(val dbValue: String, val ruName: String) {
    INSERT("insert", "Добавление"),
    UPDATE("update", "Изменение"),
    DELETE("delete", "Удаление"),
    MOVE("move", "Перемещение"),
}

/**
 * Одна синхронизируемая сущность (таблица) для универсального движка в Utils.kt (updateDatabases).
 * [oneClickDirection] — куда эта сущность едет при нажатии «Синхронизация в 1 клик» в webvue3.
 */
abstract class SyncTarget<T : Any>(
    val key: String,
    val tableName: String,
    val displayName: String,
    val oneClickDirection: SyncDirection,
    // Размер пачки для ПОЛНОСТРОЧНЫХ операций — READ (loadByIds, "WHERE id IN (...)"), INSERT (dataCreate)
    // и UPDATE (dataUpdate): их payload пропорционален весу строки. Подбирается под «вес» таблицы, чтобы
    // один запрос/HTTP-пакет на удалённую БД (Connection.remote(), socketTimeout=30) не давал
    // многомегабайтный payload по медленному каналу и не упирался в "Read timed out". Тяжёлые таблицы
    // (текст/маркеры/base64) — маленькие пачки; лёгкие — большие (меньше round-trip'ов). Для DELETE это
    // не используется: payload удаления ("DELETE ... WHERE id=X") крошечный у любой таблицы — см.
    // SyncRegistry.DELETE_CHUNK_SIZE (общий большой размер).
    val rowChunkSize: Int = 200,
) {
    abstract fun listHashes(db: KaraokeConnection, whereText: String): List<RecordHash>?
    abstract fun loadByIds(ids: List<Long>, db: KaraokeConnection): Map<Long, T>
    abstract fun getDiff(from: T, to: T): List<RecordDiff>
    abstract fun getSqlToInsert(item: T): String
    abstract fun deleteLocal(id: Long, db: KaraokeConnection): Boolean
    abstract fun label(item: T): String
    open fun shouldPush(diff: List<RecordDiff>): Boolean = diff.isNotEmpty()
}

/**
 * Универсальная реализация для любого класса, реализующего KaraokeDbTable и аннотирующего свои
 * колонки через @KaraokeDbTableField — вся логика построена на уже существующих generic-хелперах
 * KaraokeDbTable.Companion (getListHashes/loadByIds/getDiff/delete), без нового кода на сущность.
 */
class GenericKaraokeDbTableSyncTarget<T : KaraokeDbTable>(
    key: String,
    tableName: String,
    displayName: String,
    oneClickDirection: SyncDirection,
    private val clazz: KClass<T>,
    private val labelFn: (T) -> String,
    rowChunkSize: Int = 200,
) : SyncTarget<T>(key, tableName, displayName, oneClickDirection, rowChunkSize) {

    override fun listHashes(db: KaraokeConnection, whereText: String): List<RecordHash>? =
        KaraokeDbTable.getListHashes(tableName = tableName, database = db, whereText = whereText)

    // Грузим пачками по rowChunkSize, чтобы один запрос на удалённую БД не упирался в socketTimeout.
    @Suppress("UNCHECKED_CAST")
    override fun loadByIds(ids: List<Long>, db: KaraokeConnection): Map<Long, T> =
        ids.chunked(rowChunkSize).flatMap { chunk ->
            KaraokeDbTable.loadByIds(
                clazz = clazz,
                tableName = tableName,
                ids = chunk,
                database = db,
                storageService = KSS_APP,
                storageApiClient = SAC_APP,
            )
        }.associate { it.id to (it as T) }

    override fun getDiff(from: T, to: T): List<RecordDiff> = KaraokeDbTable.getDiff(from, to)

    override fun getSqlToInsert(item: T): String = item.getSqlToInsert()

    override fun deleteLocal(id: Long, db: KaraokeConnection): Boolean =
        KaraokeDbTable.delete(tableName = tableName, id = id, database = db)

    override fun label(item: T): String = labelFn(item)
}

/**
 * Settings реализует KaraokeDbTable только ради типизации (см. CLAUDE.md) — её поля НЕ аннотированы
 * @KaraokeDbTableField (виртуальные diff-поля status/color/processColorXxx, тяжёлые side-effect
 * геттеры ms/rootFolder и вся tbl_settings_sync-инфраструктура несовместимы с generic reflection).
 * Поэтому здесь — bespoke SyncTarget поверх уже существующих статических методов Settings, а не
 * GenericKaraokeDbTableSyncTarget<Settings>: тот молча дал бы пустые диффы/пустой INSERT.
 */
object SettingsSyncTarget : SyncTarget<Settings>(
    key = "settings",
    tableName = Settings.TABLE_NAME,
    displayName = "Настройки песен",
    oneClickDirection = SyncDirection.LOCAL_TO_SERVER,
    // Самые тяжёлые строки в базе: source_text/result_text/source_markers/formatted_text_tabs. Несколько
    // сотен таких за один запрос дают многомегабайтный payload и "Read timed out" на remote — грузим по 25.
    rowChunkSize = 25,
) {
    override fun listHashes(db: KaraokeConnection, whereText: String): List<RecordHash>? =
        Settings.listHashes(database = db, whereText = whereText)

    // Грузим пачками по rowChunkSize (25), чтобы не упереться в socketTimeout=30 на remote.
    override fun loadByIds(ids: List<Long>, db: KaraokeConnection): Map<Long, Settings> =
        ids.chunked(rowChunkSize).fold(LinkedHashMap<Long, Settings>()) { acc, chunk ->
            acc.putAll(Settings.loadListFromDbByIds(ids = chunk, database = db, storageService = KSS_APP, storageApiClient = SAC_APP))
            acc
        }

    override fun getDiff(from: Settings, to: Settings): List<RecordDiff> = Settings.getDiff(from, to)

    override fun getSqlToInsert(item: Settings): String = item.getSqlToInsert(sync = false)

    override fun deleteLocal(id: Long, db: KaraokeConnection): Boolean {
        Settings.deleteFromDb(id = id, database = db)
        return true
    }

    override fun label(item: Settings): String = item.fileName

    // Точь-в-точь текущий фильтр из Utils.kt (Settings.saveToDb() автопуш) — не пушим, если diff
    // состоит только из виртуальных полей (status/color/processColorXxx) или шума status_process_*.
    override fun shouldPush(diff: List<RecordDiff>): Boolean =
        diff.isNotEmpty() && !diff.all { !it.recordDiffRealField || it.recordDiffName.startsWith("status_process_") }
}

val PicturesSyncTarget = GenericKaraokeDbTableSyncTarget(
    key = "pictures",
    tableName = Pictures.TABLE_NAME,
    displayName = "Картинки",
    oneClickDirection = SyncDirection.LOCAL_TO_SERVER,
    clazz = Pictures::class,
    labelFn = { it.name },
    // picture_full сейчас всегда "", но loadByIds грузит все колонки (ignoreUseInList=true), а часть
    // старых строк может ещё нести base64; write-flush тоже жмёт pictures по 1. Осторожно — по 50.
    rowChunkSize = 50,
)

val AuthorsSyncTarget = GenericKaraokeDbTableSyncTarget(
    key = "authors",
    tableName = Author.TABLE_NAME,
    displayName = "Авторы",
    oneClickDirection = SyncDirection.LOCAL_TO_SERVER,
    clazz = Author::class,
    labelFn = { it.author },
    // Лёгкие строки, вся таблица ~125 записей — по 500 это фактически один запрос.
    rowChunkSize = 500,
)

val DictionariesSyncTarget = GenericKaraokeDbTableSyncTarget(
    key = "dictionaries",
    tableName = Dictionary.TABLE_NAME,
    displayName = "Словари",
    oneClickDirection = SyncDirection.LOCAL_TO_SERVER,
    clazz = Dictionary::class,
    labelFn = { "${it.dictName}: ${it.dictValue}" },
    // Лёгкие строки (короткие значения словарей) — по 500 фактически один запрос.
    rowChunkSize = 500,
)

val SiteUsersSyncTarget = GenericKaraokeDbTableSyncTarget(
    key = "siteusers",
    tableName = SiteUser.TABLE_NAME,
    displayName = "Пользователи сайта",
    oneClickDirection = SyncDirection.SERVER_TO_LOCAL,
    clazz = SiteUser::class,
    labelFn = { it.email },
    // Лёгкие строки, таблица крошечная — по 500.
    rowChunkSize = 500,
)

val SitePlaylistsSyncTarget = GenericKaraokeDbTableSyncTarget(
    key = "siteplaylists",
    tableName = SitePlaylist.TABLE_NAME,
    displayName = "Плейлисты сайта",
    oneClickDirection = SyncDirection.SERVER_TO_LOCAL,
    clazz = SitePlaylist::class,
    labelFn = { "id=${it.id} ${it.name}" },
    // Лёгкие строки (нет текста/base64) — по 500 фактически один запрос.
    rowChunkSize = 500,
)

val SitePlaylistItemsSyncTarget = GenericKaraokeDbTableSyncTarget(
    key = "siteplaylistitems",
    tableName = SitePlaylistItem.TABLE_NAME,
    displayName = "Элементы плейлистов сайта",
    oneClickDirection = SyncDirection.SERVER_TO_LOCAL,
    clazz = SitePlaylistItem::class,
    labelFn = { "id=${it.id} pl=${it.playlistId} song=${it.songId}" },
    // Крошечные строки (playlist_id/song_id/position/muted) — по 500.
    rowChunkSize = 500,
)

val EventsSyncTarget = GenericKaraokeDbTableSyncTarget(
    key = "events",
    tableName = WebEvent.TABLE_NAME,
    displayName = "Статистика",
    oneClickDirection = SyncDirection.SERVER_TO_LOCAL,
    clazz = WebEvent::class,
    labelFn = { "id=${it.id} ${it.eventType ?: ""}" },
    // Строки лёгкие, но `SELECT * WHERE id IN (...)` по remote-соединению с socketTimeout=30
    // упирался в таймаут (Read timed out) при 500 id за раз — часть строк несёт заметный текст
    // (rest_parameters/user_agent/referer), а на пути к серверной БД крупные ответы чувствительны
    // к размеру пакета. По 100 каждая пачка — отдельный небольшой запрос со своим окном таймаута.
    rowChunkSize = 100,
)

// Назначение песни на разметку в онлайн-редакторе. Реальный рабочий цикл (назначить → пользователь
// делает → админ апрувит) чаще всего идёт ЦЕЛИКОМ на PROD (assign/approve поддерживают target=remote,
// см. SongEditorController) — как pull пользователей/статистики, а не push с LOCAL.
val SongAssignmentsSyncTarget = GenericKaraokeDbTableSyncTarget(
    key = "songassignments",
    tableName = SongAssignment.TABLE_NAME,
    displayName = "Задания редактора",
    oneClickDirection = SyncDirection.SERVER_TO_LOCAL,
    clazz = SongAssignment::class,
    labelFn = { "id=${it.id} song=${it.songId} user=${it.assigneeId} ${it.adminStatus}" },
    // Строки лёгкие (текста нет, review_comment короткий).
    rowChunkSize = 200,
)

// Черновик пользователя (правки текста/маркеров). Пишет пользователь (PROD) → едет админу на LOCAL.
val SongAssignmentDraftsSyncTarget = GenericKaraokeDbTableSyncTarget(
    key = "songassignmentdrafts",
    tableName = SongAssignmentDraft.TABLE_NAME,
    displayName = "Черновики редактора",
    oneClickDirection = SyncDirection.SERVER_TO_LOCAL,
    clazz = SongAssignmentDraft::class,
    labelFn = { "id=${it.id} assignment=${it.assignmentId} ${it.userStatus}" },
    // edited_markers/edited_source_text тяжёлые — мелкими пачками, как SettingsSyncTarget.
    rowChunkSize = 25,
)

object SyncRegistry {
    // Размер пачки для операций УДАЛЕНИЯ на удалённом сервере (зеркальное удаление в цели + move-удаление
    // из источника, оба идут как зашифрованный "DELETE ... WHERE id=X" на /changerecords). Payload одной
    // строки крошечный (~40 байт) и не зависит от таблицы, поэтому — один общий большой размер, а не
    // per-table rowChunkSize. Прежние «чанки по 10» давали, например, ~16900 HTTP-запросов на очистку
    // 169k событий при move; с 200 — на порядок меньше round-trip'ов.
    const val DELETE_CHUNK_SIZE = 200

    val all: List<SyncTarget<*>> = listOf(
        SettingsSyncTarget,
        PicturesSyncTarget,
        AuthorsSyncTarget,
        DictionariesSyncTarget,
        SiteUsersSyncTarget,
        SitePlaylistsSyncTarget,
        SitePlaylistItemsSyncTarget,
        SongAssignmentsSyncTarget,
        SongAssignmentDraftsSyncTarget,
        EventsSyncTarget,
    )

    fun byKey(key: String): SyncTarget<*>? = all.find { it.key == key }
}

// Ключ KaraokeProperty, разрешающий конкретную операцию этой сущности в данном направлении
// (см. 40 flags "sync_<key>_<push|pull>_<insert|update|delete|move>_allowed" в KaraokeProperties.kt).
fun SyncTarget<*>.operationPropertyKey(direction: SyncDirection, op: SyncOperation): String =
    "sync_${key}_${if (direction == SyncDirection.LOCAL_TO_SERVER) "push" else "pull"}_${op.dbValue}_allowed"

fun SyncTarget<*>.isOperationAllowed(direction: SyncDirection, op: SyncOperation): Boolean =
    KaraokeProperties.getBoolean(operationPropertyKey(direction, op))

// Направление считается разрешённым (кнопка запуска/one-click активна), если разрешена хотя бы одна
// операция этого направления — заменяет прежний одиночный флаг sync_<key>_push/pull_allowed.
fun SyncTarget<*>.isAllowed(direction: SyncDirection): Boolean =
    SyncOperation.values().any { isOperationAllowed(direction, it) }
