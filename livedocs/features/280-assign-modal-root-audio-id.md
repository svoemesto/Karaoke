---
status: Active
slug: 280-assign-modal-root-audio-id
related:
  - ../features/263-editor-task-review-modal.md
  - ../features/017-editor-status-bypass.md
  - ../architecture/L3-components.md
  - ../../specs/280-assign-modal-root-audio-id/spec.md
---

# 280 — Фильтр rootId/audioRootId в AssignModal (LiveDoc)

> Drill-down — [specs/280-assign-modal-root-audio-id/spec.md](../../specs/280-assign-modal-root-audio-id/spec.md).

## Что делает

В админке `webvue3`, в модалке «Назначить песню на разметку» (`AssignModal.vue`)
строка фильтра песен расширена двумя числовыми полями:

1. **«root ID»** — точное совпадение по `song.root_id` (семейство по песне).
2. **«A-root ID»** — точное совпадение по `song.audio_parent_id` (семейство по аудио).

Поля добавлены справа от существующих «Автор / Альбом / Название песни»,
порядок полей повторяет `SongsFilterModal.vue` (где уже есть оба фильтра с теми же
метками «root ID:» / «A-root ID:» — единая UI-конвенция админки).

Каждое числовое поле имеет кнопку очистки «✕» (`:disabled="!rootIdQuery"`), которая
сбрасывает только это поле — остальные фильтры («Автор», «Альбом», «Название»)
сохраняются. Кнопка очистки по аналогии с `.sfm-button-clear-field` из
`SongsFilterModal.vue`.

Все фильтры комбинируются AND с чекбоксом «Только кандидаты на разметку» и
друг с другом; пустое/невалидное (нечисловое) значение трактуется как «фильтр
не применён» — поведение полностью совпадает с текстовыми полями.

## Главное решение (Assumption A-1 спеки)

В постановке задачи поле названо `audioRootId`, но в существующем API/UI проекта
(`ApiController.apisSongsDigests`, `SongsFilterModal.vue`, `SongDTOdigest`) это
поле называется **`audioParentId`**. Используется каноническое имя
`audioParentId` в бэкенд-параметре (`filterAudioParentId`), а в payload action
`searchCandidateSongs` и в `data()` компонента — `audioRootId`/`audioRootIdQuery`
(соответствует ТЗ). Метка в UI — «A-root ID» по конвенции `SongsFilterModal.vue`.

## User Stories (краткий список)

- **US1** (P1): поиск кандидатов по `root ID` / `A-root ID` (точное совпадение).
- **US2** (P2): локальная очистка числового поля через «✕».
- **US3** (P3): пустые числовые поля = фильтр не применяется (регрессия
  гарантирована через `normalizeNumericFilter` в `AssignModal.doSearch`).

## Functional Requirements (указатель)

- **FR-001**: в `.se-search-row` добавлены `<input class="se-search-root-id">` и
  `<input class="se-search-audio-root-id">` после `.se-search-name`, до кнопки
  «Найти».
- **FR-002**: каждое числовое поле обёрнуто в `.se-search-root-id-wrap` /
  `.se-search-audio-root-id-wrap` с локальной кнопкой «✕» (`:disabled` пока поле
  пустое).
- **FR-003**: `type="text"` с `inputmode="numeric"` + `pattern="[0-9]*"` —
  цифровая клавиатура на мобильных, hint валидации.
- **FR-005**: `doSearch` диспатчит action `searchCandidateSongs` с payload
  `{ ..., rootId, audioRootId }`; action маппит их в HTTP `filterRootId` /
  `filterAudioParentId` для `POST /api/songsdigests`.
- **FR-007**: action `searchCandidateSongs` в `store.js` принимает новые параметры;
  бэкенд уже принимает `filterRootId`/`filterAudioParentId` (без изменений).
- **FR-008**: `SongDTOdigest` уже содержит `rootId` / `audioParentId` — без изменений.
- **FR-010**: бэкенд не меняется (только фронт).

## Acceptance Criteria (manual, см. quickstart.md SC-1..SC-9)

- [ ] **AC1**: ввод «root ID» = `42` + «Найти» → в Network виден `filterRootId=42`,
  список результатов содержит только песни с `root_id == 42`.
- [ ] **AC2**: ввод «A-root ID» = `17` + «Найти» → в Network виден
  `filterAudioParentId=17`, список сужен до песен с `audio_parent_id == 17`.
- [ ] **AC3**: AND-комбинация с «Автор» — оба фильтра присутствуют в HTTP-запросе.
- [ ] **AC4**: нажатие «✕» рядом с «root ID» обнуляет только это поле; остальные
  фильтры сохраняются.
- [ ] **AC5**: пустые числовые поля — поведение модалки идентично поведению до
  фичи (HTTP-запрос без `filterRootId`/`filterAudioParentId`).
- [ ] **AC6**: невалидный ввод `abc` не приводит к HTTP 400; параметр не
  передаётся; UI не показывает ошибок.

## Связанные LiveDocs

- Feature: [263-editor-task-review-modal.md](../features/263-editor-task-review-modal.md) —
  ReviewModal (соседняя модалка задач редактора).
- Feature: [017-editor-status-bypass.md](../features/017-editor-status-bypass.md) —
  статусы заданий редактора.
- Architecture: [L3-components.md](../architecture/L3-components.md) — структура
  Vue-компонентов `webvue3`.

## Код

- Frontend: `webvue3/src/components/SongEditor/AssignModal.vue` — template (`.se-search-row`),
  `data()` (поля `rootIdQuery`/`audioRootIdQuery`), `methods.doSearch` (payload +
  `normalizeNumericFilter`), `<style scoped>` (`.se-search-root-id-wrap`,
  `.se-search-audio-root-id-wrap`, `.se-btn-clear`).
- Store: `webvue3/src/components/SongEditor/store.js` — action
  `searchCandidateSongs` (расширенная сигнатура + JSDoc).
- Backend: без изменений (бэкенд уже принимает `filterRootId`/`filterAudioParentId`).

## История

- Создан: 2026-08-31 (Pass 280 — фича AssignModal: фильтр по rootId и audioRootId).
