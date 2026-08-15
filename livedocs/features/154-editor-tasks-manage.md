---
status: Active
slug: 154-editor-tasks-manage
related:
  - ../domain/editorial.md
  - ../domain/identity.md
  - ../features/182-editor-self-assign-tasks.md
  - ../architecture/L3-components.md
  - ../../specs/154-editor-tasks-manage/spec.md
  - ../../archive/docs/features/editor-tasks.md
---

# 154 — Управление заданиями редактора в личном кабинете и админке (LiveDoc)

> Drill-down — [specs/154-editor-tasks-manage/spec.md](../../specs/154-editor-tasks-manage/spec.md).

## Что делает

В личном кабинете редактора (`/account/editor`) и в админке (`webvue3`,
компонент «Задания редактора») улучшено управление:

- **Сортировка**: одобренные (`status = approved`) — всегда внизу, активные
  (assigned/in_progress/submitted/rejected) — наверху. Внутри группы —
  стабильный порядок (по `assigned_at` или `id`).
- **Кнопка «Удалить»** для одобренных заданий (для одного и для всех сразу).
- **Кнопка «Отказаться»** для активных (assigned/in_progress/submitted/rejected).
- **Кнопка «Удалить все одобренные»** в личном кабинете И в админке.

**Эффект**: после N+ успешных закрытий активные назначения не «съезжают»
под длинный шлейф одобренных — редактор видит свою работу в приоритете.

## User Stories (краткий список)

- **US1** (P1): Задания в личном кабинете отсортированы — готовые внизу.
- **US2** (P1): Кнопка «Удалить» для одобренных + «Отказаться» для активных.
- **US3** (P1): «Удалить все одобренные» — в обоих интерфейсах.

## Functional Requirements (указатель)

- **FR-001**: Сортировка в `/account/editor`: `active_status FIRST, approved_last`.
- **FR-002**: Backend `POST /api/songeditor/editor-tasks/{id}/delete` — для одобренных.
- **FR-003**: Backend `POST /api/songeditor/editor-tasks/{id}/revoke` — отказаться.
- **FR-004**: Backend `POST /api/songeditor/editor-tasks/delete-all-approved`.
- **FR-005**: Аналоги в админке: `/api/admin/songeditor/tasks/{id}/delete` + `delete-all-approved`.
- **FR-006**: Кнопки с подтверждением (modal-style confirm).

## Acceptance Criteria

- [ ] **AC1**: `/account/editor` → одобренные внизу, активные наверху.
- [ ] **AC2**: Кнопка «Удалить» работает только для одобренных.
- [ ] **AC3**: «Отказаться» — для всех остальных статусов.
- [ ] **AC4**: «Удалить все одобренные» — bulk action с подтверждением.
- [ ] **AC5**: В админке — те же кнопки доступны в `TasksTable` / `SongsEditorTable`.

## Связанные LiveDocs

- Domain: [editorial.md](../domain/editorial.md) (assignment = AR), [identity.md](../domain/identity.md) (SiteUser = editor)
- Feature: [182-editor-self-assign-tasks.md](../features/182-editor-self-assign-tasks.md) (предыдущая фича)
- Architecture: [L3-components.md](../architecture/L3-components.md)

## Код

- Backend: `karaoke-web/.../controllers/PublicSongEditorController.kt` — личный кабинет endpoints
- Backend: `karaoke-web/.../controllers/AdminSongEditorController.kt` — admin endpoints
- Frontend: `karaoke-public/src/views/EditorTasksView.vue` — сортировка + кнопки
- Frontend: `webvue3/src/components/Songs/TasksTable.vue` — bulk actions

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14