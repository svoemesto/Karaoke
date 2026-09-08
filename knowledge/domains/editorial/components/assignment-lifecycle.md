# Component: assignment-lifecycle

> **Домен**: [editorial](../domain.md)
> **Компонент**: жизненный цикл `EditorAssignment` + `ReviewTask`,
> race protection с `SELECT FOR UPDATE`.

## Ответственность | Responsibility

Эта компонента описывает полный pipeline задания редактора: от
self-assign до approve/reject с запуском авто-конвейера. Ключевой
момент — **race protection**: две конкурентных попытки взять одну
песню должны дать ровно одно задание, второму — 409.

## Интерфейсы и Контракты | Interfaces and Contracts

### `EditorAssignment.status`

| Значение | Описание |
| --- | --- |
| `active` | Задание активно, редактор работает. |
| `done` | Approve выдан, задание завершено. |
| `revoked` | Редактор сам снял задание (отмена). |

### `ReviewTask.status`

См. [dictionaries](dictionaries.md) — `ApprovalStatus` enum
(PENDING/APPROVED/REJECTED).

## Логика и Алгоритмы | Logic and Algorithms

### Полный pipeline одного задания

```
Editor clicks "Take to work" on SongView.vue
  ↓
POST /api/public/songeditor/assign-self { songId }
  ↓
[1] PublicSongEditorController.assignSelf()
  ↓
[2] PublicSongEditorService.assignSelf(songId, currentUser)
  ↓
[3] BEGIN TRANSACTION
  ↓
[4] SELECT * FROM tbl_song_assignments WHERE song_id = ? FOR UPDATE
    — row-level lock
  ↓
[5] IF EXISTS AND status = 'active':
        → 409 song_already_taken
  ↓
[6] IF EXISTS AND status IN ('done', 'revoked'):
        → SELECT current status, return {ok: true, idempotent: true}
  ↓
[7] ELSE:
        INSERT INTO tbl_song_assignments (...)
        → return {ok: true, idempotent: false}
  ↓
[8] COMMIT
  ↓
[9] Event TaskAssigned → Song.idStatus → остаётся прежним (редактор работает)
```

### Race protection через `SELECT FOR UPDATE`

**Проблема**: два редактора одновременно кликают «Взять в работу» на одной
песне. Без блокировки оба получат 200 OK и две строки в БД.

**Решение**: `SELECT ... FOR UPDATE` берёт row-level lock на
`tbl_song_assignments`. Второй транзакционный запрос ждёт commit первого,
затем видит новую строку и возвращает 409.

**[WARN]** `FOR UPDATE` обязателен. Без него — race condition, приводящий
к двойным заданиям.

**[WARN]** Транзакция должна быть короткой. Если внутри транзакции
делать HTTP-вызовы или долгие операции — lock удерживается слишком
долго, деградация UI.

### Approve flow

```
Admin opens ReviewModal.vue, picks idStatus 5 or 6
  ↓
POST /api/admin/review-task/approve { taskId, targetIdStatus }
  ↓
[1] AdminReviewTaskController.approve()
  ↓
[2] ReviewTaskService.approve(taskId, targetIdStatus, admin)
  ↓
[3] UPDATE tbl_review_tasks SET status='APPROVED', reviewed_by=?, reviewed_at=now()
  ↓
[4] PipelineTriggered.dispatch(targetIdStatus):
      if targetIdStatus = 5:
        KaraokeProcess.submit(RENDER_MP4_LYRICS)
        KaraokeProcess.submit(RENDER_MP4_KARAOKE)
      if targetIdStatus = 6:
        KaraokeProcess.submit(RENDER_MP4_DEMO)
        → после завершения: TelegramService.notify(...)
  ↓
[5] UPDATE tbl_song_assignments SET status='done'
  ↓
[6] Event TaskApproved
```

### Reject flow

```
Admin clicks "Reject"
  ↓
POST /api/admin/review-task/reject { taskId, reason }
  ↓
[1] ReviewTaskService.reject(taskId, reason)
  ↓
[2] UPDATE tbl_review_tasks SET status='REJECTED', reason=?
  ↓
[3] UPDATE tbl_song_assignments SET status='active'
      (или 'revoked' если админ решил закрыть)
  ↓
[4] Event TaskRejected
```

**[WARN]** После reject задание возвращается в `active` — редактор может
продолжить работу. Если админ хочет закрыть задание окончательно —
отдельный flow.

### Revoke flow (редактор сам снимает)

```
Editor clicks "Revoke"
  ↓
POST /api/public/songeditor/revoke { songId }
  ↓
[1] PublicSongEditorService.revoke(songId, currentUser)
  ↓
[2] UPDATE tbl_song_assignments
      SET status='revoked'
      WHERE song_id=? AND assignee_id=currentUser AND status='active'
  ↓
[3] IF 0 rows affected → 404 assignment_not_found или 403 assignment_not_mine
  ↓
[4] Event TaskRevoked
```

**[WARN]** Нельзя revoke чужое задание — это проверяется по `assignee_id`.

## Зависимости | Dependencies

- → [domain](../domain.md) — AR `EditorAssignment`, `ReviewTask`.
- → [dictionaries](dictionaries.md) — `ApprovalStatus`, error codes.
- → [identity](../identity/domain.md) — `SiteUser.canSelfAssign`.
- → [rendering dictionaries](../../rendering/components/dictionaries.md) —
  `RenderVersion`.
- → [monitoring](../monitoring/domain.md) — `SubmittedAssignmentsCheck`
  отслеживает задания в `submitted` без review > 24ч.

## Ловушки и предупреждения

**[WARN] `SELECT FOR UPDATE` без короткой транзакции** — деградация UI.

**[WARN] Race condition при self-assign** без `FOR UPDATE` — двойные
задания.

**[WARN] `idStatus` после approve** — НЕ устанавливается напрямую.
Pipeline сам выставит 5/6 после успешного рендера. См.
[song-lifecycle](../../catalog/components/song-lifecycle.md).

**[WARN] Reassign после revoke** — после `status='revoked'` можно
снова взять ту же песню (UNIQUE по `(song_id, assignee_id)` не
препятствует). Проверка `status='active'` сделает новый INSERT,
если старая строка была `revoked`.

## Связанные фичи

- `182-editor-self-assign-tasks` — primary фича (self-assign + race).
- `184-approve-status-choice` — выбор `targetIdStatus` при approve.

## Связанные ADR | Related ADRs

- [0001-raw-jdbc](../../adr/0001-raw-jdbc.md) — БД для хранения.
