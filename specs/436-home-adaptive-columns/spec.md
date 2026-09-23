# Feature Specification: Адаптивный дизайн главной страницы админки

**Feature Branch**: `436-home-adaptive-columns`
**Created**: 2026-09-23
**Status**: Draft
**Input**: Задача владельца (OpenProject #160): админка, главная страница.
Нужно чтобы дизайн страницы был адаптивный, и если высоты экрана не хватает —
размещать кнопки на странице в 2, 3 и т.п. колонки.

## OpenProject Tracking *(MANDATORY — see AGENTS.md § Issue-tracker OpenProject)*

- **Issue ID**: `#160` («Адаптивный дизайн главной страницы»).
- **Title**: «Адаптивный дизайн главной страницы».
- **Created in OpenProject**: 2026-09-23.
- **Workflow**:
  1. **Claim**: `bash tools/tracker.sh claim-issue 160` — выполнено 2026-09-23.
  2. **Add comment**: `bash tools/tracker.sh add-comment 160 --file specs/436-home-adaptive-columns/report.md`.
  3. **Mark review**: `bash tools/tracker.sh mark-review 160`.
  4. **Close** (owner, после merge): `bash tools/tracker.sh close-issue 160`.

## Knowledge References *(MANDATORY — see Constitution Principle IX)*

### Pre-flight log

- **Дата pre-flight**: 2026-09-23
- **Grep-запросы**:
  1. `grep -rn -i "адаптив|responsive|@media|breakpoint|viewport" knowledge/`
     → прямых совпадений **нет** (в knowledge/ нет раздела про responsive-frontend).
  2. `grep -rn -i "HomeView|главная страница|dashboard" knowledge/` →
     `system/frontend/webvue3-views.md`, `system/frontend/webvue3-views-detailed.md`,
     `storage/components/storage-api-client.md`, `catalog/components/audio-descendant-sync.md`.
  3. `grep -rn -i "webvue3|frontend|css|scoped" knowledge/guidelines/ knowledge/system/frontend/`
     → `guidelines/code-style.md` (Vue 3 + Bootstrap-vue-next, Prettier/ESLint),
     `guidelines/architecture-conventions.md`.
  4. `grep -rn "@media" webvue3/src` → прецеденты responsive-CSS:
     `SongEditor/ReviewModal.vue` (768/1024), `SongEditor/SongKaraokeEditorView.vue`,
     `Stats/*.vue` (900) — все локальные `@media` в scoped-стилях компонентов.
  5. `grep -rn "85px|100vh" webvue3/src` → `App.vue` (`#app { height: 100vh }`),
     `HomeView.vue` (`min-height: calc(100vh - 85px)`).

### Knowledge files consulted

- [`knowledge/system/frontend/webvue3-views-detailed.md`](../../knowledge/system/frontend/webvue3-views-detailed.md)
  — `HomeView.vue` — view-контейнер главной без table-компонента, «Главная
  (dashboard, мониторинг)»; исключение из правила «view без логики».
- [`knowledge/system/frontend/webvue3-views.md`](../../knowledge/system/frontend/webvue3-views.md)
  — HomeView как dashboard, объединяющий несколько Vuex-модулей.
- [`knowledge/guidelines/code-style.md`](../../knowledge/guidelines/code-style.md)
  — Vue 3 + Bootstrap-vue-next; линтеры `npm run lint:check` + Prettier.
- [`knowledge/guidelines/architecture-conventions.md`](../../knowledge/guidelines/architecture-conventions.md)
  — двух-фронтенд (webvue3 admin vs karaoke-public), ловушка №5.
- [`knowledge/domains/storage/components/storage-api-client.md`](../../knowledge/domains/storage/components/storage-api-client.md)
  — `HomeView.vue` как consumer admin-действий (context, не влияет на дизайн).

### Если ничего не нашлось (явный no-op)

Раздела про responsive/adaptive layout в `knowledge/` нет — это первый
зафиксированный паттерн адаптивной раскладки для админки. ADR о введении
CSS-переменной/breakpoints не требуется (правило локально для одного view,
не меняет архитектуру).

## User Scenarios & Testing *(mandatory)*

### User Story 1 — кнопки раскладываются в несколько колонок при нехватке высоты (Priority: P1)

Администратор открывает главную страницу (`/`) админ-SPA `webvue3` на экране,
высота которого меньше высоты контента (≈1450 px). Вместо одной длинной
вертикальной колонки с прокруткой кнопки автоматически раскладываются в 2
колонки (на низких экранах — в 3), так что весь блок действий помещается
по высоте.

**Why this priority**: это и есть суть задачи #160 — убрать «простыню» из ~21
кнопки, которая на типичном ноутбуке 1366×768 требует длинной прокрутки.

**Independent Test**: открыть главную в Chrome DevTools, переключать viewport:
1920×1080 → 2 колонки; 1366×768 → 2 колонки; 2560×1440 → 1 колонка;
3440×2160 → 1 колонка. Число колонок замерить через
`getComputedStyle(document.querySelector('.home-controls')).columnCount`.

**Acceptance Scenarios**:

1. **Given** viewport 1920×1080, **When** открыта главная, **Then**
   `.home-controls` имеет `column-count: 2`, содержимое помещается по высоте
   (высота `.home-wrapper` ≤ доступной высоты, либо прокрутка минимальна).
2. **Given** viewport 1366×768, **When** открыта главная, **Then**
   `column-count: 2` (3 колонки требуют ширины ≥1520 px), ни одна группа
   кнопок не разрезана между колонками.
3. **Given** viewport 1920×768 (широкий и низкий), **When** открыта главная,
   **Then** `column-count: 3`.
4. **Given** viewport 2560×1800 (широкий и очень высокий), **When** открыта
   главная, **Then** 1 колонка (высоты хватает) — раскладка не ломается.
4. **Given** viewport 390×844 (телефон, узкий), **When** открыта главная,
   **Then** 1 колонка (ширина меньше порога многоколоночности), вертикальная
   прокрутка через `.app-main-content`.
5. **Given** resize окна с 3440×1440 до 1366×768, **When** viewport пересекает
   breakpoint, **Then** число колонок переключается без перезагрузки (чистый CSS).

### User Story 2 — группы кнопок не разрезаются между колонками (Priority: P1)

Блок «Автор» (input + 6 кнопок) и другие группы `.field-and-buttons-wrapper`
переносятся в колонку целиком, а не разрываются посередине.

**Why this priority**: без запрета разрыва многоколоночная раскладка выглядела
бы хуже исходной (input в одной колонке, его кнопки — в другой).

**Independent Test**: на viewport 1920×1080 проверить, что каждый
`.field-and-buttons-wrapper` целиком лежит в одной колонке (границы по X
не пересекают границу колонок).

**Acceptance Scenarios**:

1. **Given** многоколоночная раскладка, **When** браузер балансирует колонки,
   **Then** каждый прямoй ребёнок `.home-controls` имеет `break-inside: avoid`
   и не фрагментируется.

### Edge Cases

- **Очень низкий экран** (vh < ~560): автор-группа (~404 px) может не влезать
  в 3-колоночную высоту — допускается вертикальная прокрутка; разрывать группу
  не разрешено.
- **Ultra-wide и низкий** (3440×800): 3 колонки, ширина каждой ~1100 px —
  кнопки растягиваются на всю колонку (текущее поведение `width: 100%`).
- **Печать / `prefers-reduced-motion`** — не затрагивается (нет анимаций).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: `.home` MUST NOT ограничивать ширину значением `500px`, когда
  активна многоколоночная раскладка; базовая (одноколоночная) ширина остаётся
  ~500 px для читаемости.
- **FR-002**: `.home-controls` MUST использовать CSS multi-column layout
  (`column-count`), значение по умолчанию — `1`.
- **FR-003**: При недостаточной высоте viewport и достаточной ширине
  `.home-controls` MUST переключаться на `column-count: 2`, затем `3`
  через `@media (max-height)` + `@media (min-width)`.
- **FR-004**: Каждый прямой ребёнок `.home-controls` MUST иметь
  `break-inside: avoid`, чтобы группы кнопок не разрезались.
- **FR-005**: Раскладка MUST быть чисто CSS (без JS-resize-обработчиков),
  чтобы работать при resize/повороте без перезагрузки.
- **FR-006**: Изменения MUST быть локальными для `webvue3/src/views/HomeView.vue`
  (scoped-стили) и не затрагивать общие таблицы/прочие view.

### Key Entities *(include if feature involves data)*

N/A — фича чисто презентационная, данных/API не затрагивает.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: На viewport 1920×1080 и 1366×768 `getComputedStyle('.home-controls').columnCount`
  > 1 (2 или 3), при этом в DOM нет фрагментированных групп
  (каждый `.field-and-buttons-wrapper` целиком в одной колонке).
- **SC-002**: На viewport 3440×1440 `columnCount === 1` (регрессия «высокий
  экран остаётся одноколоночным»).
- **SC-003**: `cd webvue3 && npm run lint:check` — 0 ошибок;
  `npm run format:check` — OK; `npm run build` — OK.
- **SC-004**: Никаких изменений в `karaoke-public` (другой фронтенд) и backend.

## Assumptions

- Высота контента главной ≈ 1450 px при ширине колонки ~500 px (оценка по 21
  кнопке × 60 px + 4 input + padding групп); breakpoints выбраны из этой оценки.
- Целевые браузеры поддерживают CSS multi-column и `break-inside` (Chromium/
  Firefox/Safari актуальных версий).
- Владелец принимает приёмку визуально в браузере (автотестов UI в проекте нет).
