---
status: Active
slug: editorial
type: bounded-context
related:
  - ../features/182-editor-self-assign-tasks.md
  - ../features/184-approve-status-choice.md
  - ../domain/identity.md
  - ../architecture/L3-components.md
---

# Bounded Context: editorial (Редакторы)

> Задания редакторам, self-assign, авто-конвейер при апруве.

## Назначение

Editorial — контекст для управления **заданиями редакторов** на обработку песен.
Введён в Pass 51 (см. фичу 182, спека `specs/182-editor-self-assign-tasks/`).
Содержит бизнес-логику self-assign + авто-конвейера при апруве (фича 184).

## Aggregate Roots

- **EditorAssignment (Назначение)**: AR контекста. Identity = `id`. Содержит
  `songId`, `assigneeId`, `createdAt`, `status` (active/done/revoked).
  Инварианты: UNIQUE по `(song_id, assignee_id)`.

- **ReviewTask (Задача на ревью)**: задача, созданная при назначении. Identity = `id`.
  Содержит `assignmentId`, `targetIdStatus` (5 или 6), `reviewedBy`, `reviewedAt`.

## Entities

- **Draft (Черновик)**: промежуточное состояние задания (маркеры редактора).
- **Review (Ревью)**: ревью задания админом, выбор целевого `idStatus`.

## Value Objects

- **ApprovalStatus (PENDING | APPROVED | REJECTED)**: статус ревью.
- **TargetIdStatus (5 | 6)**: целевой статус после апрува (Render или Demo).

## Domain Events

- **TaskAssigned**: редактор назначил себя на задание (см. фичу 182).
- **TaskApproved**: админ одобрил задание, выбрав idStatus 5 или 6.
- **TaskRejected**: админ отклонил задание.
- **TaskRevoked**: редактор снял с себя задание.
- **PipelineTriggered**: запущен авто-конвейер (по выбору idStatus).

## Ubiquitous Language (глоссарий)

| Термин | Определение | Пример в коде |
|--------|-------------|----------------|
| **Self-assign** | Редактор сам назначает себя на задание | `POST /api/public/songeditor/assign-self` |
| **Idempotent** | Повторный клик = 200 OK без нового INSERT | `(song_id, assignee_id)` UNIQUE |
| **Race protection** | `SELECT FOR UPDATE` для защиты от гонок | `PublicSongEditorController.assignSelf` |
| **Song_already_taken** | 409 при попытке взять чужое задание | errorCode `song_already_taken` |
| **EditorAssignment** | Назначение редактора на песню | `tbl_song_assignments` |
| **ReviewTask** | Задача на ревью при апруве | см. фичу 184 |
| **Render (idStatus=5)** | Апрув → запуск LYRICS/KARAOKE рендера | `RenderVersion.LYRICS` |
| **Demo (idStatus=6)** | Апрув → запуск DEMO рендера + новости | `RenderVersion.DEMO` |
| **Cross-cutting** | Фича затрагивает несколько контекстов | фичи 182, 184 — cross-cutting |
| **canSelfAssign** | Флаг на редакторе: можно брать задания | `SiteUser.canSelfAssign` |
| **Idempotent: true** | Поле в ответе при повторном self-assign | `{ok: true, idempotent: true}` |

## Связанные фичи

- [182-editor-self-assign-tasks.md](../features/182-editor-self-assign-tasks.md) — primary фича
- [184-approve-status-choice.md](../features/184-approve-status-choice.md) — выбор idStatus при апруве

## Связанные LiveDocs

- Architecture: [L3-components.md](../architecture/L3-components.md)
- Domain: [identity.md](identity.md) (редактор = SiteUser с ролью editor)

## Код

- Модели: `karaoke-app/src/main/kotlin/.../model/SongAssignment.kt`, `ReviewTask.kt`
- Контроллеры: `karaoke-web/src/main/kotlin/.../controllers/PublicSongEditorController.kt`
- DTO: `SongAssignmentBriefDTO.kt`
- SQL: `deploy/karaoke-db/<NNN>_tbl_song_assignments.sql`
- Frontend: `karaoke-public/src/views/SongView.vue` (кнопка «Взять в работу»)
- Frontend: `webvue3/src/components/Songs/ReviewModal.vue` (radio + watch)

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14