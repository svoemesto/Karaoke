# Feature Specification: Сброс фильтра категории альбома при открытии песен конкретного альбома

**Feature Branch**: `363-album-type-filter-reset-on-album-open`

**Created**: 2026-09-10

**Status**: Draft

**Input**: User description: "Работа над задачей #78 в трекере OpenProject" (OpenProject issue #78 «Сброс фильтра категории альбома при открытии песен альбома автора»).

## Clarifications

### Session 2026-09-10

- **Q1 (UI поведения кнопок фильтра при auto-reset)**: Как должны выглядеть кнопки фильтра категорий альбомов в шапке, когда активен auto-reset (просмотр конкретного альбома)? → A: **Скрыть весь фильтр-бар** — блок `.km-album-type-filters` не рендерится через `v-if="!selectedAlbumId"`, чтобы посетитель не видел неактуальный фильтр и случайно его не правил. При уходе с `?albumId=` блок возвращается, фильтр в исходном состоянии.
- **Q2 (уточнение после внедрения, 2026-09-10)**: После первой реализации владелец уточнил — при открытии одного альбома скрывать нужно всю панель `.km-album-controls-bar` (включая переключатель «Сквозной/По типам альбомов»), а не только фильтр-бар: переключатель режима отображения теряет смысл, когда альбом один (нечего группировать/сквозить). → A: **Расширено до всей панели** — `v-if="zakromaAlbumTypeCounts.length > 0 && !selectedAlbumId"` поднят на `.km-album-controls-bar`, внутренний `v-if` на `.km-album-type-filters` убран.

## OpenProject Tracking *(MANDATORY — see AGENTS.md § Issue-tracker OpenProject)*

### Идентификация

- **Issue ID**: `#78`
- **Title**: Сброс фильтра категории альбома при открытии песен альбома автора
- **Created in OpenProject**: 2026-09-10

### Workflow (NON-NEGOTIABLE при наличии Issue ID)

| Шаг | Команда | Когда | Кто |
|---|---|---|---|
| 1. **Claim** | `source .env.local-tracker && bash tools/tracker.sh claim-issue 78` | ✅ ВЫПОЛНЕНО перед стартом спеки (2026-09-10, status переведён в `In progress`). | Agent |
| 2. **Pre-flight Knowledge** | `spec.md § Knowledge References` | См. ниже — MUST #0, Constitution Principle IX. | Agent |
| 3. **Work** | код, tests, knowledge updates | `/speckit.implement` | Agent |
| 4. **Add comment с отчётом** | `bash tools/tracker.sh add-comment 78 --file specs/363-album-type-filter-reset-on-album-open/report.md` | После merge. Файл `report.md` — REQUIRED. | Agent |
| 5. **Mark review** | `bash tools/tracker.sh mark-review 78` | После публикации комментария. | Agent |
| 6. **Close** | `bash tools/tracker.sh close-issue 78` | После ревью владельцем. | Agent или Owner |

### Проверки (validation)

- `tools/check-spec-issue-link.py` — секция `## OpenProject Tracking` присутствует, поля заполнены.
- Чек-лист `checklists/requirements.md` — MANDATORY (Pass 340 Knowledge Compliance).

## Knowledge References *(MANDATORY — see Constitution Principle IX)*

### Pre-flight log

- **Дата pre-flight**: 2026-09-10
- **Grep-запросы** (минимум 3, по релевантным ключевым словам задачи):
  1. `albumType` → `knowledge/domains/catalog/components/album-entity.md`, `knowledge/domains/catalog/components/dictionaries.md`, `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/AlbumType.kt`, `karaoke-public/src/views/ZakromaView.vue`
  2. `filter` → `knowledge/system/frontend/filter-stores.md`, `karaoke-public/src/views/ZakromaView.vue` (строки 451–733), `karaoke-public/src/views/ZakromaAlbumsView.vue`
  3. `album` → `knowledge/domains/catalog/components/album-entity.md`, `knowledge/domains/catalog/components/author-entity.md`, `karaoke-public/src/components/AlbumTiles.vue`

### Knowledge files consulted

- [`knowledge/domains/catalog/components/album-entity.md`](../../knowledge/domains/catalog/components/album-entity.md)
  — зачем прочитан: понять модель `Album` + `albumType` (dbValue `studio`/`single`/`live`/`compilation`/`bootleg`/`archive`/`tribute`) и тип хранения в БД.
- [`knowledge/domains/catalog/components/dictionaries.md`](../../knowledge/domains/catalog/components/dictionaries.md)
  — зачем прочитан: подтвердить контракт перечисления `AlbumType` (dbValue-строки).
- [`knowledge/system/frontend/filter-stores.md`](../../knowledge/system/frontend/filter-stores.md)
  — зачем прочитан: контекст паттерна фильтров; помог найти, что в публичном модуле фильтр категорий альбомов хранится в `localStorage` (а не в Vuex, как админские фильтры).
- [`karaoke-public/src/views/ZakromaView.vue`](../../karaoke-public/src/views/ZakromaView.vue)
  — зачем прочитан: основной файл, где живёт `hiddenAlbumTypes` Set + `visibleAlbums()` + `selectedAlbumId` (computed из query `?albumId=`).
- [`karaoke-public/src/views/ZakromaAlbumsView.vue`](../../karaoke-public/src/views/ZakromaAlbumsView.vue)
  — зачем прочитан: страница-источник перехода на конкретный альбом (Pass 359); `onAlbumSelect` пушит `{ name: 'zakroma-author', params: { authorId }, query: { albumId } }`.
- [`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/AlbumType.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/AlbumType.kt)
  — зачем прочитан: источник правды по `dbValue` (используем их при авто-сбросе фильтра).
- [`specs/356-zakroma-albums-by-author/spec.md`](356-zakroma-albums-by-author/spec.md)
  — зачем прочитан: донорский паттерн «альбомы автора → клик по альбому → фильтр в URL» — то, на чём строится текущая регрессия.

### Search coverage

- ✅ Searched: `albumType` / `album_type` / `category.*album` / `filter.*album` → найдены `AlbumType.kt`, `album-entity.md`, `ZakromaView.vue`, `ZakromaAlbumsView.vue`.
- ✅ Searched: `фильтр категорий` / `hiddenAlbumTypes` → найдены в `ZakromaView.vue:451–733`.
- ✅ Searched: `selectedAlbumId` / `albumId` URL-параметр → найдены в `ZakromaView.vue:575–578` + спека #356.
- ✅ ADR по этой области не существует — фича чисто фронт-ендовая, без новых архитектурных решений.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Открытие песен альбома со скрытой категорией (Priority: P1)

Зарегистрированный/анонимный посетитель находится на странице «Песни автора X» `/zakroma/{authorId}` с активным фильтром категорий альбомов (в localStorage скрыт, например, тип «singl»). Переходит в `/zakroma/{authorId}/albums`, кликает на плитку конкретного альбома типа «singl» (id=Y). Открывается страница `/zakroma/{authorId}?albumId=Y` и **показывает песни альбома Y** (а не пустоту), потому что фильтр категорий для типа «singl» авто-сбрасывается на время просмотра конкретного альбома.

**Why this priority**: Это исходный баг issue #78 — посетитель видит пустую страницу без объяснений. Без фикса фича «альбомы автора» (Pass 359, спека #356) остаётся сломанной для всех пользователей с не-дефолтным фильтром категорий.

**Independent Test**: Открыть `/zakroma/{id}` в режиме инкогнито, в DevTools выполнить `localStorage.setItem('km-zakroma-hidden-album-types', JSON.stringify(['single']))`, перейти на `/zakroma/{id}/albums`, кликнуть на плашку альбома-сингла → ожидаемо показываются песни альбома. После возврата «← К альбомам автора» фильтр «singl» остаётся выключенным (как было до клика), альбом с типом «singl» снова скрыт в основном списке песен автора.

**Acceptance Scenarios**:

1. **Given** фильтр категорий альбомов содержит «single» в `localStorage` И пользователь открывает `/zakroma/{id}?albumId=Y`, где `Y` — id альбома с `albumType=single`, **When** страница загружается, **Then** песни альбома Y отображаются И `hiddenAlbumTypes` больше не содержит «single» (фильтр автоматически сброшен на эту сессию просмотра конкретного альбома).
2. **Given** пользователь находился на `/zakroma/{id}?albumId=Y` с авто-сброшенным фильтром И кликнул back-link «← К альбомам автора», **When** он попадает на `/zakroma/{id}/albums`, **Then** плашка альбома Y по-прежнему отображается (фильтр «single» временно снят только на время просмотра конкретного альбома).
3. **Given** пользователь вернулся на `/zakroma/{id}` (без `?albumId=`) со страницы альбомов автора, **When** он смотрит основной список песен автора, **Then** фильтр «single» восстановлен в исходное состояние (если был скрыт до клика, остаётся скрытым; если был виден — остаётся видимым).

---

### User Story 2 — Сохранение пользовательского выбора фильтра (Priority: P2)

Пользователь явно отключает категорию «Сборники» в фильтре на странице `/zakroma/{id}` через UI-кнопку. Изменение должно сохраниться в `localStorage` и пережить обновление страницы.

**Why this priority**: Существующее поведение (Pass 012), нельзя ломать обратно. Авто-сброс фильтра при открытии конкретного альбома — **только временный** на сессию просмотра, не трогает `localStorage`.

**Independent Test**: Открыть `/zakroma/{id}`, кликнуть кнопку «Сборники (5)» — фильтр переключается, `localStorage.km-zakroma-hidden-album-types` обновляется. Перезагрузить страницу — фильтр по-прежнему активен.

**Acceptance Scenarios**:

1. **Given** пользователь находится на `/zakroma/{id}` без `?albumId=`, **When** он кликает по кнопке-фильтру категории альбомов (например, «Сборники»), **Then** состояние фильтра меняется И сохраняется в `localStorage.km-zakroma-hidden-album-types`.
2. **Given** пользователь находится на `/zakroma/{id}?albumId=Y` (открыт конкретный альбом), **When** он смотрит страницу, **Then** вся панель `.km-album-controls-bar` (и переключатель «Сквозной/По типам», и `.km-album-type-filters`) НЕ отображается (v-if), ни один из её элементов недоступен.

---

### User Story 3 — Открытие альбома без скрытого типа (Priority: P3)

Пользователь с фильтром, в котором «single» скрыт, кликает на плашку **студийного** альбома (тип «studio»). Поскольку «studio» не входит в скрытые типы — никакого авто-сброса не требуется, страница работает как раньше.

**Why this priority**: Контр-кейс, подтверждающий, что авто-сброс не срабатывает «превентивно» — только когда фильтр реально мешает.

**Independent Test**: Скрыть «single» в `localStorage`, открыть `/zakroma/{id}/albums`, кликнуть на студийный альбом → песни показываются, `hiddenAlbumTypes` остаётся `['single']`.

**Acceptance Scenarios**:

1. **Given** `hiddenAlbumTypes` содержит «single» И пользователь открывает `/zakroma/{id}?albumId=Z`, где `Z` — id студийного альбома, **When** страница загружается, **Then** песни альбома Z отображаются И `hiddenAlbumTypes` остаётся `['single']` (авто-сброс не трогает фильтр, потому что тип не был скрыт).

---

### Edge Cases

- **Альбом без `albumType` (NULL → STUDIO по дефолту)**: открывается корректно, авто-сброс не нужен.
- **У пользователя ВСЕ типы скрыты (включая тип открываемого альбома)**: авто-сброс снимает только тот тип, который соответствует открываемому альбому; остальные остаются скрытыми.
- **Прямой URL `/zakroma/{id}?albumId=Y` при первом заходе (нет предыдущей сессии)**: фильтр в `localStorage` уже может быть не-дефолтным — логика авто-сброса работает одинаково.
- **Альбом удалён/недоступен (404 от бэка)**: ошибка показывается как обычно, авто-сброс фильтра не выполняется (фильтр не знает `albumType`).
- **Watchers на `$route.query.albumId`**: смена `?albumId=Y` на тот же URL (например, refresh) не должна повторно сбрасывать фильтр, если он уже сброшен.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: When `?albumId=Y` is present in the URL on `/zakroma/{authorId}` AND the album Y's `albumType` is currently in `hiddenAlbumTypes`, the system MUST automatically remove that specific `albumType` from `hiddenAlbumTypes` for the duration of viewing that album (without writing to `localStorage` — this is a transient state).
- **FR-002**: When the user navigates away from `/zakroma/{authorId}?albumId=Y` (e.g., back to `/zakroma/{authorId}/albums` or `/zakroma`), the transient auto-reset MUST be reverted — the original `hiddenAlbumTypes` (from `localStorage`) MUST be restored on the songs-view, so user filters are preserved across navigation.
- **FR-003**: The original `hiddenAlbumTypes` value (as stored in `localStorage`) MUST NOT be modified by the auto-reset logic — only the in-memory transient state used for rendering.
- **FR-004**: When the auto-reset is active (user is on `/zakroma/{id}?albumId=Y`), the **entire album controls panel** (`.km-album-controls-bar`, содержит и переключатель «Сквозной/По типам альбомов», и блок `.km-album-type-filters` с фильтром категорий) MUST be hidden via `v-if="zakromaAlbumTypeCounts.length > 0 && !selectedAlbumId"` — при просмотре ОДНОГО конкретного альбома оба элемента теряют смысл: режим отображения (continuous/grouped) актуален только когда альбомов несколько, а фильтр по типу — transient auto-reset. При возврате на `/zakroma/{id}` (без `?albumId=`) панель возвращается с исходным состоянием. (Уточнение 2026-09-10: было «только фильтр-бар» — расширено до всей панели, т.к. переключатель режима отображения тоже неактуален при одном альбоме.)
- **FR-005**: The auto-reset MUST NOT trigger when the album's `albumType` is NOT in `hiddenAlbumTypes` (no-op case).
- **FR-006**: The auto-reset MUST operate on the **single** `albumType` matching the opened album — not on the whole `hiddenAlbumTypes` set.
- **FR-007**: The change MUST be localized to `karaoke-public/src/views/ZakromaView.vue` (no backend changes — all required data — `albumType` per album — is already present in the `zakroma` Vuex state).
- **FR-008**: Behavior when `?albumId=` is absent MUST be unchanged (no regression on existing albums-list view).

### Key Entities

- **`ZakromaAlbum` (frontend DTO)**: represents one album in the zakroma tree. Carries `albumType: string` (dbValue — one of `studio`/`live`/`compilation`/`bootleg`/`single`/`archive`/`tribute`) and `albumTypeLabel: string` (display label). Already present in the streaming payload from `/api/public/zakroma/stream` (Pass 357).
- **`hiddenAlbumTypes: Set<string>`**: in-component reactive set, initialized from `localStorage.km-zakroma-hidden-album-types`. Used by `visibleAlbums(zak)` to filter the displayed album list. Lives in `data()` of `ZakromaView.vue`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: After this fix, in a reproducer where the user has hidden `single` in their album-type filter, clicking on a single-type album in `/zakroma/{id}/albums` results in the album's songs being displayed (not an empty page) in 100% of cases — verified manually via DevTools reproducer + visual check.
- **SC-002**: User's `localStorage.km-zakroma-hidden-album-types` value MUST remain byte-identical before and after the navigation that triggered the auto-reset (verified via `localStorage.getItem` in DevTools before/after).
- **SC-003**: After returning from `/zakroma/{id}?albumId=Y` to `/zakroma/{id}` (songs view), the originally hidden types MUST still be hidden — i.e., the user's filter choice is respected in the regular view (verified by clicking the filter button — its pressed/unpressed state must match the pre-click state).
- **SC-004**: No regression in the existing "hide category" UX — toggling a filter via the UI still persists to `localStorage` and survives page reload (verified manually).
- **SC-005**: CI 7/7 passes after the change (ktlint + ESLint + Prettier + coverage + structure).

## Assumptions

- **Scope**: только фронт-енд (`karaoke-public`), без правок бэкенда. Все нужные данные (`albumType` каждого альбома) уже приходят в `zakroma` Vuex-сторе.
- **Persistence**: `hiddenAlbumTypes` хранится в `localStorage` (FR-025 спеки #012), ключ `km-zakroma-hidden-album-types`, формат — JSON-массив строк `dbValue`. Авто-сброс не пишет в localStorage.
- **URL flow**: `?albumId=` (preferred) и `?album=` (legacy alias) уже поддерживаются в `selectedAlbumId` (Pass 358). Авто-сброс работает для обоих.
- **No new ADR required**: фича чисто-фронтовая (state management), не затрагивает архитектурных решений; существующий ADR о паттерне фильтров во `filter-stores.md` достаточно.
- **No new docs/features/*.md**: scope слишком узкий (один computed + одно временное состояние в одном файле), per-feature документ не требуется (FR-009 — per-feature документ для фичи с C4 L3-компонентой или bounded-context изменениями; здесь этого нет).
- **Tests**: ручные через DevTools reproducer (как и большинство фронт-фич проекта, автотесты не покрывают).

## Out of Scope

- Изменение логики фильтра на других страницах (например, на `/search`).
- Перенос `hiddenAlbumTypes` из `localStorage` в user-scoped server-side storage.
- Визуальный редизайн кнопок фильтра.
- Переименование ключа `km-zakroma-hidden-album-types` (legacy concern).