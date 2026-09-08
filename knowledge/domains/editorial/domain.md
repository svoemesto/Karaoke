---
id: domain-editorial
title: "Domain: Editorial (Редакторы)"
status: Active
slug: editorial
related:
  - ../identity/domain.md
  - ../catalog/domain.md
  - ../rendering/domain.md
  - ../../adr/0001-raw-jdbc.md
---

# Domain: Editorial (Редакторы)

> Задания редакторам, self-assign, авто-конвейер при апруве.
>
> Drill-down (legacy): [livedocs/domain/editorial.md](../../../livedocs/domain/editorial.md).

## Обзор контекста (Bounded Context)

Editorial — контекст для управления **заданиями редакторов** на обработку
песен. Введён в Pass 51 (см. фичу 182, спека `specs/182-editor-self-assign-tasks/`).
Содержит бизнес-логику self-assign + авто-конвейера при апруве (фича 184).

**Граница**: контекст НЕ отвечает за:

- песенный каталог (→ [catalog](../catalog/domain.md));
- рендеринг MP4 (→ [rendering](../rendering/domain.md));
- аутентификацию пользователей (→ [identity](../identity/domain.md));
- мониторинг застрявших заданий (→ [monitoring](../monitoring/domain.md)).

## Ubiquitous Language | Единый язык

| Термин | Определение | Пример в коде |
| --- | --- | --- |
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

Полный словарь магических кодов (`ApprovalStatus`, `TargetIdStatus`,
error codes) — см. [dictionaries](components/dictionaries.md).
Жизненный цикл `EditorAssignment` + `ReviewTask` — см.
[assignment-lifecycle](components/assignment-lifecycle.md).

## Aggregate Roots

- **EditorAssignment (Назначение)**: AR контекста. Identity = `id`. Содержит
  `songId`, `assigneeId`, `createdAt`, `status` (active/done/revoked).
  Инварианты:
  - UNIQUE по `(song_id, assignee_id)`;
  - не более одного активного назначения на песню.

- **ReviewTask (Задача на ревью)**: задача, созданная при назначении.
  Identity = `id`. Содержит `assignmentId`, `targetIdStatus` (5 или 6),
  `reviewedBy`, `reviewedAt`.
  Инварианты:
  - `targetIdStatus ∈ {5, 6}`;
  - `reviewer` — admin.

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

## Domain Invariants | Инварианты и правила бизнеса

1. **UNIQUE по `(song_id, assignee_id)`**: повторный self-assign
   идемпотентен (`{ok: true, idempotent: true}`), без новой строки.
2. **`SELECT FOR UPDATE`** обязателен в `assignSelf` для защиты от гонок
   (две конкурентных попытки взять одну песню).
3. **409 при попытке взять чужое задание**: errorCode `song_already_taken`,
   **не** 500.
4. **`canSelfAssign=true`** обязателен: редактор без этого флага не
   может self-assign (см. [identity](../identity/domain.md)).
5. **Авто-pipeline** при апруве с `targetIdStatus=5` запускает
   LYRICS/KARAOKE рендер, при `targetIdStatus=6` — DEMO + Telegram-новость.

## Публичные контракты (API)

### Public API (для редакторов)

- `POST /api/public/songeditor/assign-self` — self-assign.
- `POST /api/public/songeditor/revoke` — снять задание.
- `GET /api/public/songeditor/mine` — мои задания.

### Internal API (для админа)

- `POST /api/admin/review-task/approve` — approve + выбор `targetIdStatus`.
- `POST /api/admin/review-task/reject` — reject.

## Структура компонентов (C4 L3)

- [dictionaries](components/dictionaries.md) — `ApprovalStatus` enum,
  `TargetIdStatus` (5/6), error codes (`song_already_taken`,
  `song_not_found`, etc.).
- [assignment-lifecycle](components/assignment-lifecycle.md) —
  жизненный цикл `EditorAssignment` (active → done/revoked),
  `ReviewTask` review flow, race protection с `SELECT FOR UPDATE`.

## Связанные фичи

- `182-editor-self-assign-tasks` — primary фича.
- `184-approve-status-choice` — выбор `idStatus` при апруве.

## Связанные ADR

- [0001-raw-jdbc](../../adr/0001-raw-jdbc.md) — сырой JDBC, без JPA/Hibernate.

## Код (физическая реализация)

- Модели: `karaoke-app/src/main/kotlin/.../model/SongAssignment.kt`, `ReviewTask.kt`
- Контроллеры: `karaoke-web/src/main/kotlin/.../controllers/PublicSongEditorController.kt`
- DTO: `SongAssignmentBriefDTO.kt`
- SQL: `deploy/karaoke-db/<NNN>_tbl_song_assignments.sql`
- Frontend: `karaoke-public/src/views/SongView.vue` (кнопка «Взять в работу»)
- Frontend: `webvue3/src/components/Songs/ReviewModal.vue` (radio + watch)
