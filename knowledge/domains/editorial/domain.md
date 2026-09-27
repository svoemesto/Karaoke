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

> **[WARN] Поправка Pass 474: агрегата `ReviewTask` НЕ существует.**
> Файла `model/ReviewTask.kt`, таблицы `tbl_review_tasks` и строки
> `review-task` в коде нет (grep по `*.kt`/`*.sql`/`*.vue` — 0).
> Энумов `ApprovalStatus` и `TargetIdStatus` тоже нет. Реальный
> флоу ревью — в `controllers/SongEditorController.kt`
> (`/api/songeditor`, approve `:336`, reject `:573`, revoke `:612`),
> а статусы назначения — в `model/SongAssignmentStatus.kt`
> (ASSIGNED/IN_PROGRESS/SUBMITTED/APPROVED/REJECTED). Прежнее
> описание сохранено как описание ЗАДУМАННОЙ модели:

- ~~**ReviewTask (Задача на ревью)**~~ — задача, созданная при
  назначении; Identity = `id`; содержит `assignmentId`,
  `targetIdStatus` (5 или 6), `reviewedBy`, `reviewedAt`.

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

- `POST /api/songeditor/approve` — approve (реально: `SongEditorController.kt:336`).
- `POST /api/songeditor/reject` — reject (`:573`).
- `POST /api/songeditor/revoke` — revoke (`:612`).

  (Прежняя версия называла `/api/admin/review-task/approve|reject` —
  таких эндпоинтов нет; Pass 474.)

## Структура компонентов (C4 L3)

- [assignment-lifecycle](components/assignment-lifecycle.md) — Эта компонента описывает полный pipeline задания редактора: от self-assign до approve/reject с запуском авто-конвейера.…
- [dictionaries](components/dictionaries.md) — В editorial-домене часто возникают ситуации, когда:

## Связанные фичи

- `182-editor-self-assign-tasks` — primary фича.
- `184-approve-status-choice` — выбор `idStatus` при апруве.

## Связанные ADR

- [0001-raw-jdbc](../../adr/0001-raw-jdbc.md) — сырой JDBC, без JPA/Hibernate.

## Код (физическая реализация)

- Модели: `karaoke-app/src/main/kotlin/.../model/SongAssignment.kt`, `ReviewTask.kt`
- Контроллер: `karaoke-web/.../controllers/PublicSongeditorController.kt` (`/api/public/songeditor`, `/assign-self`) — регистр в имени файла важен: рядом есть `PublicSongEditorController.kt` с другим назначением (`/api/public/account/editor`)
- DTO: `SongAssignmentBriefDTO.kt`
- SQL: `deploy/karaoke-db/10_song_assignments.sql`
- Frontend: `karaoke-public/src/views/SongView.vue` (кнопка «Взять в работу»)
- Frontend: `webvue3/src/components/SongEditor/ReviewModal.vue` (radio + watch)
