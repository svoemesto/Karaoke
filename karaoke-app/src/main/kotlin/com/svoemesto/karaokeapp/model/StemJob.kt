package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.WORKING_DATABASE
import com.svoemesto.karaokeapp.services.KSS_APP
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.SAC_APP
import com.svoemesto.karaokeapp.services.StorageApiClient
import java.io.Serializable
import java.sql.SQLException
import java.sql.Timestamp

/**
 * Singleton-объект Stem Job Status.
 *
 * @see docs/features/dual-db-sync.md
 */
object StemJobStatus {
    const val WAITING = "WAITING"
    const val WORKING = "WORKING"
    const val DONE = "DONE"
    const val ERROR = "ERROR"
}

// DEMUCS2 = музыка/голос (2 стема); DEMUCS5 = музыка/голос/бас/ударные/остальное (5 стемов) — те же
// режимы, что уже используются в обычном пайплайне выпуска песен (KaraokeProcessTypes.DEMUCS2/5).

/**
 * Singleton-объект Stem Job Mode.
 *
 * @see docs/features/dual-db-sync.md
 */
object StemJobMode {
    const val DEMUCS2 = "DEMUCS2"
    const val DEMUCS5 = "DEMUCS5"

    // Имена стемов на выходе demucs для каждого режима (см. Settings.argsDemucs2/5) — используются и
    // для построения путей файлов в MinIO (stemjobs/{id}/<имя>.mp3), и для валидации query-параметра
    // ?stem= при скачивании.
    fun stemNames(mode: String): List<String> =
        when (mode) {
            DEMUCS5 -> listOf("accompaniment", "vocals", "drums", "bass", "other")
            else -> listOf("accompaniment", "vocals")
        }
}

// Премиум-фича «Создать минусовку из аудиофайла» — задание пользователя публичного сайта на
// разделение произвольного аудиофайла на стемы (demucs). Таблица живёт ЦЕЛИКОМ на PROD-БД, НЕ
// участвует в LOCAL<->SERVER синхронизации (нет записи в SyncRegistry) — по образцу SiteChatMessage
// (см. 22_stem_jobs.sql): пользователь создаёт задание через karaoke-web (WORKING_DATABASE на
// проде = серверная БД), karaoke-app забирает его в работу через Connection.remote() (см.
// StemJobPollScheduler) напрямую в ту же БД. Список доступных стемов не хранится — выводится
// детерминированно из mode (см. StemJobMode.stemNames).

/**
 * Задание на стем-разделение (Demucs, Spleeter, UVR).
 *
 * Хранит:
 * - `id`, `idSettings` — песня.
 * - `model` — модель Demucs (`htdemucs`, `mdx_q`, `htdemucs_ft`, и т.п.).
 * - `status` — текущее состояние (WAITING/WORKING/DONE/ERROR).
 * - `result` — JSON с путями к выходным стемам.
 * - `created`, `finished` — таймстампы.
 *
 * Запускается через `StemJobController` (admin) или
 * `PublicStemJobController` (karaoke-public) — запись создаётся в
 * `tbl_stem_jobs`, далее `KaraokeProcess*` подхватывает.
 *
 * @see docs/features/premium-stems.md
 */
class StemJob(
    override val database: KaraokeConnection = WORKING_DATABASE,
    override val storageService: KaraokeStorageService = KSS_APP,
    override val storageApiClient: StorageApiClient = SAC_APP,
) : Serializable,
    Comparable<StemJob>,
    KaraokeDbTable {
    override fun getTableName() = TABLE_NAME

    @KaraokeDbTableField(name = "id", isId = true)
    override var id: Long = 0

    @KaraokeDbTableField(name = "site_user_id")
    var siteUserId: Long = 0

    @KaraokeDbTableField(name = "mode")
    var mode: String = StemJobMode.DEMUCS2

    @KaraokeDbTableField(name = "status")
    var status: String = StemJobStatus.WAITING

    @KaraokeDbTableField(name = "original_file_name")
    var originalFileName: String = ""

    @KaraokeDbTableField(name = "original_ext")
    var originalExt: String = ""

    @KaraokeDbTableField(name = "file_size_bytes")
    var fileSizeBytes: Long = 0

    @KaraokeDbTableField(name = "error_message")
    var errorMessage: String = ""

    // Ставится в коде при создании (createNew), не БД-дефолтом — тот же инвариант, что и
    // SiteChatMessage.createdAt (reflection-insert перечисляет все аннотированные поля явно).
    @KaraokeDbTableField(name = "created_at", useInDiff = false)
    var createdAt: Timestamp? = null

    @KaraokeDbTableField(name = "started_at")
    var startedAt: Timestamp? = null

    @KaraokeDbTableField(name = "finished_at")
    var finishedAt: Timestamp? = null

    // Выставляется только при status=DONE (now()+24h) — см. finalizeStemJob.
    @KaraokeDbTableField(name = "expires_at")
    var expiresAt: Timestamp? = null

    // Пользователь запросил удаление (в любом статусе) — фактическое удаление файлов из MinIO и
    // строки делает уборка на karaoke-app (см. StemJobPollScheduler), т.к. только она умеет писать в
    // MinIO. Единый механизм закрывает и явное удаление, и протухание по expiresAt.
    @KaraokeDbTableField(name = "delete_requested")
    var deleteRequested: Boolean = false

    @KaraokeDbTableField(name = "last_update", useInDiff = false)
    var lastUpdate: Timestamp? = null

    override fun compareTo(other: StemJob): Int = compareValuesBy(this, other, { it.id })

    override fun toDTO(): StemJobDto =
        StemJobDto(
            id = id,
            siteUserId = siteUserId,
            mode = mode,
            status = status,
            originalFileName = originalFileName,
            originalExt = originalExt,
            fileSizeBytes = fileSizeBytes,
            errorMessage = errorMessage,
            createdAt = createdAt,
            startedAt = startedAt,
            finishedAt = finishedAt,
            expiresAt = expiresAt,
            deleteRequested = deleteRequested,
        )

    companion object {
        const val TABLE_NAME = "tbl_stem_jobs"

        // Лимит очереди пользователя (ЛК karaoke-public) — не более стольки одновременно активных
        // (WAITING/WORKING) заданий.
        const val MAX_ACTIVE_JOBS_PER_USER = 5

        // Ограничения на загружаемый файл (для начала) — см. PublicStemJobController.
        const val MAX_FILE_SIZE_BYTES = 50L * 1024 * 1024
        const val MAX_DURATION_SECONDS = 60L * 60

        // Срок жизни готовых стемов в хранилище после DONE.
        const val RETENTION_HOURS = 24L

        // Допустимые расширения загружаемого файла (PublicStemJobController) — то, что реально
        // умеет декодировать ffmpeg на входе (StemJobProcessing.argsStemJobDemucs транскодирует любой
        // из них в flac перед демуксом).
        val ALLOWED_EXTENSIONS = setOf("mp3", "wav", "flac", "ogg", "m4a", "aac", "wma", "opus", "aiff")

        fun createNew(
            siteUserId: Long,
            mode: String,
            originalFileName: String,
            originalExt: String,
            fileSizeBytes: Long,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): StemJob? {
            val entity = StemJob(database = database, storageService = storageService, storageApiClient = storageApiClient)
            entity.siteUserId = siteUserId
            entity.mode = mode
            entity.originalFileName = originalFileName
            entity.originalExt = originalExt
            entity.fileSizeBytes = fileSizeBytes
            entity.status = StemJobStatus.WAITING
            entity.createdAt = Timestamp(System.currentTimeMillis())
            return KaraokeDbTable.createDbInstance(entity = entity, database = database) as? StemJob?
        }

        fun getById(
            id: Long,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): StemJob? =
            KaraokeDbTable.loadById(
                clazz = StemJob::class,
                tableName = TABLE_NAME,
                id = id,
                database = database,
                storageService = storageService,
                storageApiClient = storageApiClient,
            ) as? StemJob?

        // Задания пользователя для ЛК karaoke-public, свежие сверху.
        fun loadByUser(
            siteUserId: Long,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): List<StemJob> =
            KaraokeDbTable
                .loadList(
                    clazz = StemJob::class,
                    tableName = TABLE_NAME,
                    whereList = listOf("site_user_id=$siteUserId"),
                    database = database,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                ).map { it as StemJob }
                .sortedByDescending { it.id }

        // Все задания (админ-панель webvue3 «Минусовки», см. StemJobsAdminController) — с email/именем
        // пользователя одним JOIN-запросом (не через generic loadList — нужен JOIN, не входит в
        // reflection-контракт KaraokeDbTable; тот же паттерн, что SiteChatMessage.loadThreads).
        fun loadAllWithUserInfo(database: KaraokeConnection): List<StemJobAdminDto> {
            val connection = database.getConnection() ?: return emptyList()
            val sql =
                """
                SELECT j.*, u.email, u.display_name
                FROM $TABLE_NAME j
                JOIN ${SiteUser.TABLE_NAME} u ON u.id = j.site_user_id
                ORDER BY j.id DESC
                """.trimIndent()
            val result = mutableListOf<StemJobAdminDto>()
            try {
                connection.prepareStatement(sql).use { ps ->
                    ps.executeQuery().use { rs ->
                        while (rs.next()) {
                            result.add(
                                StemJobAdminDto(
                                    id = rs.getLong("id"),
                                    siteUserId = rs.getLong("site_user_id"),
                                    siteUserEmail = rs.getString("email") ?: "",
                                    siteUserDisplayName = rs.getString("display_name") ?: "",
                                    mode = rs.getString("mode") ?: "",
                                    status = rs.getString("status") ?: "",
                                    originalFileName = rs.getString("original_file_name") ?: "",
                                    originalExt = rs.getString("original_ext") ?: "",
                                    fileSizeBytes = rs.getLong("file_size_bytes"),
                                    errorMessage = rs.getString("error_message") ?: "",
                                    createdAt = rs.getTimestamp("created_at")?.toString() ?: "",
                                    startedAt = rs.getTimestamp("started_at")?.toString() ?: "",
                                    finishedAt = rs.getTimestamp("finished_at")?.toString() ?: "",
                                    expiresAt = rs.getTimestamp("expires_at")?.toString() ?: "",
                                    deleteRequested = rs.getBoolean("delete_requested"),
                                ),
                            )
                        }
                    }
                }
            } catch (e: SQLException) {
                println("StemJob.loadAllWithUserInfo SQLException: ${e.message}")
            }
            return result
        }

        // Число активных (WAITING/WORKING) заданий пользователя — гейт лимита очереди при создании.
        fun countActiveByUser(
            siteUserId: Long,
            database: KaraokeConnection,
        ): Int {
            val connection = database.getConnection() ?: return 0
            val sql = "SELECT COUNT(*) AS cnt FROM $TABLE_NAME WHERE site_user_id = ? AND status IN ('${StemJobStatus.WAITING}', '${StemJobStatus.WORKING}')"
            return try {
                connection.prepareStatement(sql).use { ps ->
                    ps.setLong(1, siteUserId)
                    ps.executeQuery().use { rs -> if (rs.next()) rs.getInt("cnt") else 0 }
                }
            } catch (e: SQLException) {
                println("StemJob.countActiveByUser SQLException: ${e.message}")
                0
            }
        }

        // Задания, ожидающие взятия в работу karaoke-app'ом (StemJobPollScheduler.pollWaiting).
        fun loadWaiting(
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): List<StemJob> =
            KaraokeDbTable
                .loadList(
                    clazz = StemJob::class,
                    tableName = TABLE_NAME,
                    whereList = listOf("status='${StemJobStatus.WAITING}'"),
                    database = database,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                ).map { it as StemJob }
                .sortedBy { it.id }

        // Задания к зачистке (уборка на karaoke-app, см. StemJobPollScheduler.cleanup): готовые и
        // просроченные, ЛИБО с явным запросом на удаление в любом статусе (включая ещё не забранные
        // WAITING — тогда просто отменяем, файла в MinIO ещё нет).
        fun loadPendingCleanup(
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): List<StemJob> {
            val connection = database.getConnection() ?: return emptyList()
            val sql =
                """
                SELECT id FROM $TABLE_NAME
                WHERE delete_requested = true
                   OR (status = '${StemJobStatus.DONE}' AND expires_at IS NOT NULL AND expires_at < now())
                """.trimIndent()
            val ids = mutableListOf<Long>()
            try {
                connection.prepareStatement(sql).use { ps ->
                    ps.executeQuery().use { rs -> while (rs.next()) ids.add(rs.getLong("id")) }
                }
            } catch (e: SQLException) {
                println("StemJob.loadPendingCleanup SQLException: ${e.message}")
                return emptyList()
            }
            return ids.mapNotNull { getById(it, database, storageService, storageApiClient) }
        }

        fun delete(
            id: Long,
            database: KaraokeConnection,
        ): Boolean = KaraokeDbTable.delete(tableName = TABLE_NAME, id = id, database = database)

        // Задания, застрявшие дольше staleMinutes — WAITING, которое поллер почему-то не забрал, или
        // WORKING, которое почему-то не дошло до финализации. Для монитора (StemJobsStuckCheck).
        fun countStuck(
            database: KaraokeConnection,
            staleMinutes: Long,
        ): Int {
            val connection = database.getConnection() ?: return 0
            val sql =
                """
                SELECT COUNT(*) AS cnt FROM $TABLE_NAME
                WHERE (status = '${StemJobStatus.WAITING}' AND created_at < now() - make_interval(mins => ?))
                   OR (status = '${StemJobStatus.WORKING}' AND started_at < now() - make_interval(mins => ?))
                """.trimIndent()
            return try {
                connection.prepareStatement(sql).use { ps ->
                    ps.setInt(1, staleMinutes.toInt())
                    ps.setInt(2, staleMinutes.toInt())
                    ps.executeQuery().use { rs -> if (rs.next()) rs.getInt("cnt") else 0 }
                }
            } catch (e: SQLException) {
                println("StemJob.countStuck SQLException: ${e.message}")
                0
            }
        }
    }
}

// Не БД-сущность — агрегат для админ-панели webvue3 «Минусовки» (StemJob.loadAllWithUserInfo,
// StemJobsAdminController), по образцу ChatThreadDto (SiteChatMessage.kt). Timestamp-поля — уже
// отформатированные строки (toString()), а не Timestamp?, т.к. это одноразовая read-only проекция
// для таблицы, не KaraokeDbTable-сущность с diff/save.

/**
 * DTO для stem job admin: сериализуемое представление для API/UI.
 *
 * @see docs/features/dual-db-sync.md
 */
data class StemJobAdminDto(
    val id: Long,
    val siteUserId: Long,
    val siteUserEmail: String,
    val siteUserDisplayName: String,
    val mode: String,
    val status: String,
    val originalFileName: String,
    val originalExt: String,
    val fileSizeBytes: Long,
    val errorMessage: String,
    val createdAt: String,
    val startedAt: String,
    val finishedAt: String,
    val expiresAt: String,
    val deleteRequested: Boolean,
)
