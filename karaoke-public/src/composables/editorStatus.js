// Русские подписи и цвета композитных статусов задания редактора (совпадают с SongAssignmentStatus
// на бэкенде). Держим в одном месте — используют EditorTasksView и EditorWorkView.
export const STATUS_LABELS = {
  assigned: 'Назначено',
  in_progress: 'В работе',
  submitted: 'На проверке',
  approved: 'Одобрено',
  rejected: 'Отклонено',
  revoked: 'Отозвано',
}
