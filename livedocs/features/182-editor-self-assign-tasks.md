---
status: Active
slug: 182-editor-self-assign-tasks
related:
  - ../domain/catalog.md
  - ../domain/identity.md
  - ../domain/editorial.md
  - ../../specs/182-editor-self-assign-tasks/spec.md
---

# 182 — Self-Assign Tasks для редакторов (LiveDoc)

> Drill-down — [specs/182-editor-self-assign-tasks/spec.md](../../specs/182-editor-self-assign-tasks/spec.md).

## Что делает

Позволяет редакторам (site_users с ролью `editor`) **самостоятельно назначать
себе задания** на редактирование песен через публичный интерфейс Закромов.
Раньше задания распределялись только вручную админом через `webvue3`.

**Ключевая особенность**: атомарная транзакция `SELECT FOR UPDATE` + UNIQUE-индекс
защищает от гонок (два редактора кликают одновременно → один получает задание,
другой — 409). Идемпотентность по `(song_id, assignee_id)` (повторный клик того
же редактора возвращает 200 OK без новой строки).

**UX-эффект**: time-to-first-task для редактора сократилось с дней (пока админ
распределит) до секунд (кликнул → сразу работаешь).

## User Stories (краткий список)

- **US1**: Редактор открывает `/song/{id}` → видит кнопку «Взять в работу» (НЕ для готовых песен, idStatus < 6) → кликает → получает назначение, кнопка меняется на «В работе» (FR-005).
- **US2**: Админ снимает флаг «можно брать задания» у редактора → ранее взятые задания НЕ отзываются (FR-006 / clarification #2).
- **US3**: Два редактора кликают одновременно → один получает 200, другой — 409 `song_already_taken` (race protection).

## Functional Requirements (указатель)

- **FR-001**: Endpoint `POST /api/public/songeditor/assign-self` (idempotent).
- **FR-005**: Идемпотентность по `(song_id, assignee_id)`.
- **FR-006**: Снятие флага НЕ отзывает ранее взятые задания.
- **FR-008**: `SongPublicDto.assignment: SongAssignmentBriefDto?` — поле для UI (default null).

## Acceptance Criteria

- [ ] **AC1**: Редактор с флагом `canSelfAssign=true` кликает на песню с idStatus < 6 → 200 OK + `id` нового assignment.
- [ ] **AC2**: Тот же редактор повторно кликает → 200 OK + `idempotent: true` (без INSERT).
- [ ] **AC3**: Другой редактор кликает → 409 `song_already_taken`.
- [ ] **AC4**: Админ снимает флаг → старые assignments остаются, новые не создаются.
- [ ] **AC5**: Кнопка НЕ показывается для песен с idStatus >= 6 (финальные).

## Связанные LiveDocs

- Domain: [catalog.md](../domain/catalog.md) (песня как AR) | [identity.md](../domain/identity.md) (редактор как SiteUser) | [editorial.md](../domain/editorial.md) (assignment как AR)
- Architecture: [L3-components.md](../architecture/L3-components.md) (где живёт контроллер)

## Код

- Контроллер: `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicSongEditorController.kt` — `assignSelf()`
- Модель: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SongAssignment.kt`
- DTO: `karaoke-public/src/store/modules/song.js` (Vuex модуль)
- Frontend: `karaoke-public/src/views/SongView.vue` (кнопка «Взять в работу»)
- SQL: `deploy/karaoke-db/<NNN>_tbl_song_assignments.sql` (миграция с UNIQUE-индексом)

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14