package com.svoemesto.karaokeapp.model

import java.sql.Timestamp

// Композитный статус задания онлайн-редактора для UI. Хранимых полей два (admin_status в
// tbl_song_assignments — пишет админ; user_status в tbl_song_assignment_drafts — пишет пользователь),
// а UI показывает единый статус. resolve() детерминированно выводит его из пары + временных меток —
// без общей на запись колонки, что критично для sync (см. 10_song_assignments.sql).
enum class SongAssignmentStatus(
    val dbValue: String,
) {
    ASSIGNED("assigned"), // назначено, пользователь ещё не начал
    IN_PROGRESS("in_progress"), // пользователь редактирует
    SUBMITTED("submitted"), // отправлено на проверку
    APPROVED("approved"), // одобрено, разметка применена
    REJECTED("rejected"), // отклонено с комментарием, вернулось на доработку
    ;

    companion object {
        const val ADMIN_OPEN = "open"
        const val ADMIN_APPROVED = "approved"
        const val ADMIN_REJECTED = "rejected"

        const val USER_IN_PROGRESS = "in_progress"
        const val USER_SUBMITTED = "submitted"

        // Композитный статус из (admin_status, draft?.user_status, reviewedAt, submittedAt).
        // Временные метки нужны, чтобы отличить «отправлено, ждёт ревью» от «отклонено» и «повторно
        // отправлено после доработки»: у admin_status=rejected + user_status=submitted это состояние
        // неоднозначно без сравнения reviewedAt (когда админ вынес вердикт) и submittedAt (когда
        // пользователь отправил). Если админ рассмотрел именно эту отправку (reviewedAt >= submittedAt)
        // — REJECTED; если пользователь отправил заново уже после вердикта — SUBMITTED.
        fun resolve(
            adminStatus: String,
            draftUserStatus: String?,
            reviewedAt: Timestamp? = null,
            submittedAt: Timestamp? = null,
        ): SongAssignmentStatus {
            if (adminStatus == ADMIN_APPROVED) return APPROVED
            if (draftUserStatus == null) return ASSIGNED
            if (draftUserStatus == USER_SUBMITTED) {
                val reviewedThisSubmission =
                    adminStatus == ADMIN_REJECTED &&
                        reviewedAt != null &&
                        submittedAt != null &&
                        !reviewedAt.before(submittedAt)
                return if (reviewedThisSubmission) REJECTED else SUBMITTED
            }
            // in_progress: после reject показываем REJECTED (на доработке, с комментарием), иначе IN_PROGRESS
            return if (adminStatus == ADMIN_REJECTED) REJECTED else IN_PROGRESS
        }
    }
}
