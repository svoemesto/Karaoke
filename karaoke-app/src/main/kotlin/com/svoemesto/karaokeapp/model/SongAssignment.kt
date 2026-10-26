package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.WORKING_DATABASE
import com.svoemesto.karaokeapp.services.KSS_APP
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.SAC_APP
import com.svoemesto.karaokeapp.services.SNS
import com.svoemesto.karaokeapp.services.StorageApiClient
import org.slf4j.LoggerFactory
import java.io.Serializable
import java.sql.Timestamp

// Назначение песни пользователю публичного сайта на разметку в онлайн-редакторе + вердикт админа.
// Направление sync SERVER_TO_LOCAL (SyncRegistry: songassignments) — как pull пользователей/статистики:
// реальный рабочий цикл (назначение→работа→апрув) часто идёт целиком на PROD. Рабочая копия пользователя —
// в отдельной таблице SongAssignmentDraft, см. 10_song_assignments.sql про причину разделения.

/**
 * Задание на редактирование песни, выданное пользователю (см. AdminAssign).
 *
 * Хранит:
 * - `id` — песня.
 * - `idSiteUser` — исполнитель.
 * - `idReviewer` — кто проверил (`null` = не проверено).
 * - `status` — `ASSIGNED` / `IN_PROGRESS` / `REVIEW` / `APPROVED` / `REJECTED`.
 * - `dueDate` — крайний срок.
 * - `result` — комментарий ревьюера.
 *
 * При `APPROVED` → создаётся `KaraokeProcess` для финальной обработки.
 *
 * @see archive/docs/features/async-process-queue.md
 */
class SongAssignment(
    override val database: KaraokeConnection = WORKING_DATABASE,
    override val storageService: KaraokeStorageService = KSS_APP,
    override val storageApiClient: StorageApiClient = SAC_APP,
) : Serializable,
    Comparable<SongAssignment>,
    KaraokeDbTable {
    override fun getTableName() = TABLE_NAME

    @KaraokeDbTableField(name = "id", isId = true)
    override var id: Long = 0

    @KaraokeDbTableField(name = "assignee_id")
    var assigneeId: Long = 0

    @KaraokeDbTableField(name = "song_id")
    var songId: Long = 0

    @KaraokeDbTableField(name = "voice")
    var voice: Long = 0

    @KaraokeDbTableField(name = "admin_status")
    var adminStatus: String = SongAssignmentStatus.ADMIN_OPEN

    @KaraokeDbTableField(name = "review_comment")
    var reviewComment: String = ""

    @KaraokeDbTableField(name = "assigned_by")
    var assignedBy: Long = 0

    // Nullable-в-БД timestamp: поле обязано быть Timestamp? (иначе NPE в reflection-loader на SQL NULL —
    // см. SitePlaylist.lastUpdate / DEVELOPMENT.md про reflection loader nullable columns).
    @KaraokeDbTableField(name = "assigned_at")
    var assignedAt: Timestamp? = null

    @KaraokeDbTableField(name = "reviewed_at")
    var reviewedAt: Timestamp? = null

    // Вне recordhash ⇒ вне diff синхронизации — см. SitePlaylist.lastUpdate / WebEvent.kt.
    @KaraokeDbTableField(name = "last_update", useInDiff = false)
    var lastUpdate: Timestamp? = null

    override fun compareTo(other: SongAssignment): Int = compareValuesBy(this, other, { it.id })

    override fun toDTO(): SongAssignmentDto =
        SongAssignmentDto(
            id = id,
            assigneeId = assigneeId,
            songId = songId,
            voice = voice,
            adminStatus = adminStatus,
            reviewComment = reviewComment,
            assignedBy = assignedBy,
        )

    companion object {
        const val TABLE_NAME = "tbl_song_assignments"

        private val log = LoggerFactory.getLogger(SongAssignment::class.java)

        fun loadByAssignee(
            assigneeId: Long,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): List<SongAssignment> =
            KaraokeDbTable
                .loadList(
                    clazz = SongAssignment::class,
                    tableName = TABLE_NAME,
                    whereList = listOf("assignee_id=$assigneeId"),
                    database = database,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                ).map { it as SongAssignment }
                .sorted()

        fun loadBySong(
            songId: Long,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): List<SongAssignment> =
            KaraokeDbTable
                .loadList(
                    clazz = SongAssignment::class,
                    tableName = TABLE_NAME,
                    whereList = listOf("song_id=$songId"),
                    database = database,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                ).map { it as SongAssignment }
                .sorted()

        fun loadAll(
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): List<SongAssignment> =
            KaraokeDbTable
                .loadList(
                    clazz = SongAssignment::class,
                    tableName = TABLE_NAME,
                    whereList = emptyList(),
                    database = database,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                ).map { it as SongAssignment }
                .sorted()

        /**
         * BR-221 (аудит #336, OpenProject #409): задания для админского диджеста —
         * фильтры применяются В SQL, а не в памяти после полной загрузки.
         *
         * Раньше `SongEditorController.digest` загружал ВСЕ назначения и уже потом
         * отсеивал их по `assigneeId` и по автору песни — на каждый рендер таблицы
         * это полный скан `tbl_song_assignments` (а с автором — ещё и `tbl_songs`).
         *
         * @param assigneeId назначение конкретному пользователю (`assignee_id`).
         * @param authorLike подстрока автора песни (`song_author` через подзапрос).
         */
        fun loadList(
            assigneeId: Long?,
            authorLike: String?,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): List<SongAssignment> {
            val where = mutableListOf<String>()
            assigneeId?.let { where += "assignee_id=$it" }
            authorLike
                ?.takeIf { it.isNotBlank() }
                ?.let {
                    val safe = it.replace("'", "''")
                    where += "song_id IN (SELECT id FROM tbl_songs WHERE LOWER(song_author) LIKE '%${safe.lowercase()}%')"
                }
            return KaraokeDbTable
                .loadList(
                    clazz = SongAssignment::class,
                    tableName = TABLE_NAME,
                    whereList = where,
                    database = database,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                ).map { it as SongAssignment }
                .sorted()
        }

        fun getById(
            id: Long,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): SongAssignment? =
            KaraokeDbTable.loadById(
                clazz = SongAssignment::class,
                tableName = TABLE_NAME,
                id = id,
                database = database,
                storageService = storageService,
                storageApiClient = storageApiClient,
            ) as? SongAssignment?

        /**
         * Назначения для набора песен: `songId -> все его назначения` (BR-128, аудит #336).
         *
         * Раньше возвращался `Map<Long, SongAssignment>` через `associateBy { songId }`,
         * то есть при нескольких строках на песню (штатная ситуация: UNIQUE только по
         * `(song_id, assignee_id)`, строка после approve не удаляется) молча оставалась
         * произвольная строка. `loadList` не задаёт `ORDER BY`, поэтому выбор зависел от
         * физического порядка строк.
         *
         * Каждый список отсортирован [preferredAssignmentOrder]: сначала активное
         * назначение (`admin_status = 'open'`), затем по убыванию `id`. Потребители,
         * которым нужна одна строка, берут `first()` — это детерминированно.
         */
        fun loadBySongIds(
            songIds: List<Long>,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): Map<Long, List<SongAssignment>> {
            if (songIds.isEmpty()) return emptyMap()
            return KaraokeDbTable
                .loadList(
                    clazz = SongAssignment::class,
                    tableName = TABLE_NAME,
                    whereList = listOf("song_id IN (${songIds.joinToString(",")})"),
                    database = database,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                ).map { it as SongAssignment }
                .groupBy { it.songId }
                .mapValues { (_, list) -> list.sortedWith(preferredAssignmentOrder) }
        }

        /**
         * Порядок предпочтения назначений одной песни (BR-128): активное (`open`) — первым,
         * иначе самое новое (по `id`). Детерминированный выбор вместо случайного.
         */
        internal val preferredAssignmentOrder: Comparator<SongAssignment> =
            compareByDescending<SongAssignment> { it.adminStatus == SongAssignmentStatus.ADMIN_OPEN }
                .thenByDescending { it.id }

        /** Одно предпочтительное назначение из списка (BR-128). */
        internal fun preferredAssignment(list: List<SongAssignment>): SongAssignment? =
            list.minWithOrNull(preferredAssignmentOrder)

        /**
         * BR-223 (аудит #336, OpenProject #409): **атомарный** переход статуса
         * задания в `approved`.
         *
         * Раньше защита от повторного апрува была check-then-act: контроллер
         * читал `admin_status`, и если он ещё не `approved` — применял разметку,
         * пушил песню на SERVER и ставил процесс `RENDER_MP4_DEMO`. Два
         * параллельных запроса (двойной клик по «Одобрить» или ретрай после
         * таймаута — апрув делает синхронный push) оба проходили проверку и оба
         * запускали тяжёлый рендер.
         *
         * Теперь переход — одна SQL-команда с условием
         * `admin_status <> 'approved'`: ровно один вызов обновляет строку,
         * остальные получают `false` (задание уже одобрено).
         *
         * @param previousStatus прежнее значение `admin_status` — для SSE-diff.
         * @return `true` — переход выполнен этим вызовом; `false` — задание уже
         *   было одобрено (гонка выиграна другим запросом) либо нет соединения.
         */
        fun markApprovedIfNotApproved(
            id: Long,
            reviewedAt: Timestamp,
            previousStatus: String,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): Boolean {
            val connection = database.getConnection() ?: return false
            val sql =
                "UPDATE $TABLE_NAME SET admin_status = ?, reviewed_at = ?, review_comment = '' " +
                    "WHERE id = ? AND admin_status <> ?"
            val updated =
                connection.prepareStatement(sql).use { ps ->
                    ps.setString(1, SongAssignmentStatus.ADMIN_APPROVED)
                    ps.setTimestamp(2, reviewedAt)
                    ps.setLong(3, id)
                    ps.setString(4, SongAssignmentStatus.ADMIN_APPROVED)
                    ps.executeUpdate()
                }
            if (updated <= 0) return false
            // SSE-уведомление — как в KaraokeDbTable.save(), иначе подписчики
            // (recordChange) не увидят смену статуса.
            try {
                val saved =
                    KaraokeDbTable.loadById(
                        clazz = SongAssignment::class,
                        tableName = TABLE_NAME,
                        id = id,
                        database = database,
                        storageService = storageService,
                        storageApiClient = storageApiClient,
                    )
                SNS.send(
                    SseNotification.recordChange(
                        RecordChangeMessage(
                            tableName = TABLE_NAME,
                            recordId = id,
                            diffs =
                                listOf(
                                    RecordDiff(
                                        "admin_status",
                                        SongAssignmentStatus.ADMIN_APPROVED,
                                        previousStatus,
                                    ),
                                ),
                            databaseName = database.name,
                            record = saved?.toDTO(),
                        ),
                    ),
                )
            } catch (e: Exception) {
                // SNS в unit-тестах не инициализирован (lateinit) — это не повод
                // откатывать уже выполненный переход статуса.
                log.warn("Не удалось разослать SSE recordChange для {}: {}", TABLE_NAME, e.message, e)
            }
            return true
        }

        // Композитный статус для набора песен (батчем: assignment + его draft), переиспользуется и
        // статус-эндпоинтом для таблицы/карточки песни, и фильтром /api/songsdigests — единая точка
        // вычисления, без дублирования логики resolve() между контроллерами.
        fun composeStatusesForSongIds(
            songIds: List<Long>,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): Map<Long, Pair<SongAssignment, SongAssignmentStatus>> {
            val assignmentsBySong = loadBySongIds(songIds, database, storageService, storageApiClient)
            if (assignmentsBySong.isEmpty()) return emptyMap()
            // BR-128: по одной детерминированно выбранной строке на песню, черновики — батчем.
            val chosen = assignmentsBySong.values.mapNotNull { preferredAssignment(it) }
            if (chosen.isEmpty()) return emptyMap()
            val drafts = SongAssignmentDraft.loadByAssignments(chosen.map { it.id }, database, storageService, storageApiClient)
            return chosen.associate { a ->
                val draft = drafts[a.id]
                a.songId to (a to SongAssignmentStatus.resolve(a.adminStatus, draft?.userStatus, a.reviewedAt, draft?.submittedAt))
            }
        }

        // Существующее назначение этой песни этому пользователю (защита от дубля перед assign).
        fun findExisting(
            songId: Long,
            assigneeId: Long,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): SongAssignment? =
            KaraokeDbTable
                .loadList(
                    clazz = SongAssignment::class,
                    tableName = TABLE_NAME,
                    whereList = listOf("song_id=$songId", "assignee_id=$assigneeId"),
                    database = database,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                ).firstOrNull() as? SongAssignment?

        fun delete(
            id: Long,
            database: KaraokeConnection,
        ): Boolean = KaraokeDbTable.delete(tableName = TABLE_NAME, id = id, database = database)

        // Количество заданий со статусом "на проверке" (SUBMITTED) — бейдж пункта меню «Задания
        // редактора» в webvue3 и монитор-проверка SubmittedAssignmentsCheck. Композитный статус не
        // хранится отдельной колонкой (см. SongAssignmentStatus.resolve) — считаем так же, как digest().
        fun countSubmitted(
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): Int {
            val assignments = loadAll(database, storageService, storageApiClient)
            if (assignments.isEmpty()) return 0
            val drafts = SongAssignmentDraft.loadByAssignments(assignments.map { it.id }, database, storageService, storageApiClient)
            return assignments.count { a ->
                val draft = drafts[a.id]
                SongAssignmentStatus.resolve(a.adminStatus, draft?.userStatus, a.reviewedAt, draft?.submittedAt) ==
                    SongAssignmentStatus.SUBMITTED
            }
        }
    }
}
