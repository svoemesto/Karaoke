---
description: "Task list для 250 — унификация шапки сайта karaoke-public"
---

# Tasks: Унификация шапки сайта

**Input**: Design documents from `/specs/250-unify-site-header/`
- plan.md (required)
- spec.md (required for user stories)
- research.md (5 research-решений: API hybrid, sticky opt-out, порядок элементов, CSS dedup, back-query)
- data-model.md (1 новый Vue-component: `<AppHeader>`)
- contracts/AppHeader-component.md (API-контракт: 9 props + 3 slots)
- quickstart.md (10 manual smoke-test сценариев)

**Tests**: Конституция § Тесты — автоматических тестов нет. Тестирование — ручное на dev/staging (10 сценариев из quickstart.md). Tests-фазы НЕ включены.

**Organization**: Tasks сгруппированы по user story (US1 — структурная миграция 19 view, US2 — CSS-дедупликация, US3 — специализированная шапка EditorWorkView).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: можно делать параллельно (разные файлы, без зависимостей)
- **[Story]**: к какой user story относится (US1, US2, US3)
- В описаниях — точные `file:line` или `file` paths

## Path Conventions

- **Web frontend only**: `karaoke-public/src/components/AppHeader.vue` (new) + `karaoke-public/src/views/*.vue` (миграция 20 файлов)
- Тесты отсутствуют (см. Constitution)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Подготовка ветки и preconditions проверка.

- [x] T001 [P] Переключиться на ветку `250-unify-site-header`, убедиться что `git status` чистый (или содержит только спеку/план от прошлой сессии).
- [x] T002 [P] Прочитать `karaoke-public/src/components/AuthStatusWidget.vue` — определить его API (props, emits, slots), чтобы понять, как `<AppHeader>` будет его рендерить.
- [x] T003 [P] Прочитать `karaoke-public/src/composables/useDesign.js` — определить API (`theme`, `applyTheme`), чтобы корректно встроить theme toggle в `<AppHeader>`.
- [x] T004 [P] Подтвердить, что `KARAOKE_LOGO.png` существует в `karaoke-public/public/` (или корне публичной папки). Если нет, создать заглушку (square 200×200 PNG).
- [x] T005 [P] Прочитать `karaoke-public/src/views/HomeView.vue` (строки 1-35) и `karaoke-public/src/views/SearchView.vue` (строки 1-100) — понять текущую структуру шапки (theme toggle setup, AuthStatusWidget setup, km-header CSS).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Создать `<AppHeader>`-компонент — единая точка истины для шапки. БЕЗ НЕГО user stories не могут начаться.

**⚠️ CRITICAL**: User stories не могут начаться, пока Phase 2 не завершена.

- [x] T006 [US0] Создать `karaoke-public/src/components/AppHeader.vue` со следующей структурой:
  - **`<script setup>`**: импорты `import { computed } from 'vue'`, `import { useDesign } from '@/composables/useDesign'`, `import { RouterLink } from 'vue-router'`, `import AuthStatusWidget from '@/components/AuthStatusWidget.vue'`. `defineProps({ back: { type: Object, default: null }, profileLink: { type: Object, default: null }, showAuthWidget: { type: Boolean, default: true }, showThemeToggle: { type: Boolean, default: true }, sticky: { type: Boolean, default: true }, logoSrc: { type: String, default: '/KARAOKE_LOGO.png' }, logoAlt: { type: String, default: 'Своё Место' }, maxWidth: { type: String, default: '900px' } })`. Computed `backRouteTo` (если `back`) — возвращает `{ path: back.to, query: back.query }` или просто `back.to`. Computed `profileLinkTo` — аналогично. Setup `useDesign()`, функция `setTheme(val)`.
  - **`<template>`**: `<header :class="['km-header', { 'km-header-sticky': sticky }]">` → `<div class="km-header-inner" :style="{ maxWidth }">` → три div `.km-header-left` / `.km-header-center` / `.km-header-right`. В `.km-header-left`: `<slot name="left">` с дефолтом `<RouterLink v-if="back" :to="backRouteTo" class="km-back">{{ back.label }}</RouterLink>`. В `.km-header-center`: `<slot name="center" />`. В `.km-header-right`: `<slot name="right">` с дефолтом `<RouterLink v-if="profileLink" :to="profileLinkTo" class="km-back">{{ profileLink.label }}</RouterLink>` + `<AuthStatusWidget v-if="showAuthWidget" />` + theme toggle (`<div v-if="showThemeToggle" class="km-theme-toggle">` с 3 кнопками ☀/⬡/🌙, логика из `SearchView.vue:14-32`) + `<RouterLink to="/" class="km-logo-link"><img :src="logoSrc" :alt="logoAlt" :class="['km-logo', { 'km-logo-large': !back && !profileLink }]" /></RouterLink>`.
  - **`<style scoped>`**: все стили из plan.md Phase 1 — `.km-header`, `.km-header-sticky`, `.km-header-inner`, `.km-header-left/center/right`, `.km-back`, `.km-logo-link`, `.km-logo`, `.km-logo-large`, `.km-theme-toggle`, `.km-tb`.
- [x] T007 [US0] Добавить KDoc-блок на `<AppHeader>` (перед `defineProps` в `<script setup>`): описание, FR-001..FR-016 ссылки, related LiveDoc `162-fix-header-stale-premium-status` (live premium через `AuthStatusWidget`). Соответствует Constitution § VI FR-006 (KDoc обязателен для Vue-компонентов = `export default`).
- [x] T008 [US0] Запустить `cd karaoke-public && npm run build` — должен пройти без ошибок (компонент компилируется, шаблон валиден).
- [x] T009 [P] [US0] Запустить `cd karaoke-public && npm run lint` — никаких НОВЫХ нарушений в ESLint baseline (`karaoke-public/.eslint-baseline.json`).

**Checkpoint**: `<AppHeader>` готов, можно начинать миграцию view-файлов.

---

## Phase 3: User Story 1 — Единая шапка на всех страницах сайта (Priority: P1) 🎯 MVP

**Goal**: 19 view-файлов используют `<AppHeader>` с типизированными props. Логотип всегда справа + кликабельный → `/`. Back-ссылки по контексту.

**Independent Test**: `grep -c "<AppHeader" karaoke-public/src/views/*.vue | awk -F: '$2>0' | wc -l` ≥ 19. Открыть любые 2 страницы (`/` и `/zakroma`) — DOM-структура идентична (порядок слотов, обёртка `<RouterLink>` вокруг лого).

### Implementation for User Story 1

> Все 19 view-миграций — **parallelizable** (разные файлы, нет зависимостей между ними). После завершения миграции всех — общий build + lint + grep-check.

#### Группа A — Простые view (back=Главная, default widgets)

- [x] T010 [P] [US1] Мигрировать `karaoke-public/src/views/HomeView.vue`: (1) удалить `<header class="km-header">...</header>` блок (строки 4-34) — теперь это `<AppHeader />` без props (back=null, profileLink=null, showAuthWidget=true, showThemeToggle=true); (3) импорт `import AppHeader from '@/components/AppHeader.vue'` и регистрация в `components: { AppHeader }`; (4) удалить из `<style scoped>` блоки `.km-header`, `.km-header-inner`, `.km-controls`, `.km-toggle-group`, `.km-theme-toggle`, `.km-toggle-btn`, `.km-brand-logo`. HomeView получает `.km-logo-large` через логику в AppHeader (когда back=null).
- [x] T011 [P] [US1] Мигрировать `karaoke-public/src/views/SearchView.vue`: заменить `<header class="km-header">` (строки 4-44) на `<AppHeader :back="{ to: '/', label: '← Главная' }" />`. Импорт + регистрация компонента. Удалить из `<style scoped>` блоки `.km-header`, `.km-header-inner`, `.km-header-left`, `.km-header-right`, `.km-back`, `.km-logo`, `.km-theme-toggle`, `.km-tb` (но `.km-results-list`, `.km-song-row`, `.km-filters-bar` оставить — это НЕ шапка).
- [x] T012 [P] [US1] Мигрировать `karaoke-public/src/views/ZakromaView.vue`: заменить `<header class="km-header">` на `<AppHeader :back="{ to: '/', label: '← Главная' }" />`. Импорт + регистрация. Удалить дубли `.km-header*`, `.km-back`, `.km-logo` из `<style scoped>`.
- [x] T013 [P] [US1] Мигрировать `karaoke-public/src/views/AboutView.vue`: заменить header на `<AppHeader :back="{ to: '/', label: '← Главная' }" />`. Импорт + регистрация. Удалить дубли CSS.
- [x] T014 [P] [US1] Мигрировать `karaoke-public/src/views/NewsView.vue`: аналогично T013 (`← Главная`). Удалить дубли CSS.
- [x] T015 [P] [US1] Мигрировать `karaoke-public/src/views/PremiumView.vue`: аналогично T013 (`← Главная`). Удалить дубли CSS.
- [x] T016 [P] [US1] Мигрировать `karaoke-public/src/views/LoginView.vue`: аналогично T013 (`← Главная`). Удалить дубли CSS.
- [x] T017 [P] [US1] Мигрировать `karaoke-public/src/views/RegisterView.vue`: аналогично T013 (`← Главная`). Удалить дубли CSS.
- [x] T018 [P] [US1] Мигрировать `karaoke-public/src/views/OfertaView.vue`: аналогично T013 (`← Главная`). Удалить дубли CSS.
- [x] T019 [P] [US1] Мигрировать `karaoke-public/src/views/AccountView.vue`: аналогично T013 (`← Главная`). Удалить дубли CSS.

#### Группа B — Специфичные back-цели

- [x] T020 [P] [US1] Мигрировать `karaoke-public/src/views/SongView.vue`: заменить header на `<AppHeader :back="{ to: '/zakroma', label: '← Назад' }" />` (FR-008). Удалить дубли CSS (включая `.km-tb` theme toggle — перенесён в AppHeader).
- [x] T021 [P] [US1] Мигрировать `karaoke-public/src/views/EditorTasksView.vue`: заменить header на `<AppHeader :back="{ to: '/account', label: '← Личный кабинет' }" :show-auth-widget="false" :show-theme-toggle="false" />` (FR-015). Editor — без виджетов. Удалить дубли CSS.
- [x] T022 [P] [US1] Мигрировать `karaoke-public/src/views/PlaylistEditView.vue`: заменить header на `<AppHeader :back="{ to: '/account/playlists', label: '← Мои плейлисты' }" />` (FR-006). Удалить дубли CSS.
- [x] T023 [P] [US1] Мигрировать `karaoke-public/src/views/AuthorPlaylistView.vue`: заменить header на `<AppHeader :back="{ to: '/zakroma', label: '← Закрома', query: { author: $route.params.slug } }" />` (FR-009, A-009). Использовать `computed` для динамического `query` (или передавать напрямую, если `$route.params.slug` доступен в шаблоне). Удалить дубли CSS.
- [x] T024 [P] [US1] Мигрировать `karaoke-public/src/views/CartView.vue`: заменить header на `<AppHeader :back="{ to: '/', label: '← Главная' }" />` (FR-010). Удалить дубли CSS.
- [x] T025 [P] [US1] Мигрировать `karaoke-public/src/views/StemJobsView.vue`: заменить header на `<AppHeader :back="{ to: '/account', label: '← Личный кабинет' }" />` (FR-010). Удалить дубли CSS.
- [x] T026 [P] [US1] Мигрировать `karaoke-public/src/views/SubscriptionsView.vue`: заменить header на `<AppHeader :back="{ to: '/account', label: '← Личный кабинет' }" />` (FR-010). Удалить дубли CSS.

#### Группа C — С profileLink в правом слоте

- [x] T027 [P] [US1] Мигрировать `karaoke-public/src/views/PlaylistsView.vue`: заменить header на `<AppHeader :back="{ to: '/', label: '← Главная' }" :profile-link="{ to: '/account', label: 'Профиль →' }" />` (FR-005). Удалить дубли CSS.
- [x] T028 [P] [US1] Мигрировать `karaoke-public/src/views/ChatView.vue`: заменить header на `<AppHeader :back="{ to: '/', label: '← Главная' }" :profile-link="{ to: '/account', label: 'Профиль →' }" />` (FR-007). Удалить дубли CSS.

#### Verify US1

- [x] T029 [US1] Запустить `cd karaoke-public && npm run build && npm run lint` — должен пройти (все 19 views мигрированы, AppHeader корректно используется).
- [x] T030 [US1] Grep-проверка: `grep -c "AppHeader" karaoke-public/src/views/*.vue | awk -F: '{ if ($2 > 0) print $1 }' | wc -l` — должно вернуть **19** (все view, кроме PlayerView/ShareView/SubscriptionReturnView/EditorWorkView). Если мень — какие-то пропущены.

**Checkpoint**: US1 функциональна — единая шапка на 19 страницах. Логотип справа и кликабелен везде. Back-ссылки соответствуют контексту. MVP готов к deploy.

---

## Phase 4: User Story 2 — Единый CSS-стиль шапки (Priority: P2)

**Goal**: 0 CSS-дублей в `karaoke-public/src/views/`. Все стили шапки — в `<AppHeader>.vue`.

**Замечание**: Дедупликация уже происходит при миграции US1 (каждая задача T010-T028 включает «удалить дубли CSS»). US2 — финальная verification + grep-инвариант.

**Independent Test**: `grep -rn "\.km-header\|\.km-back\|\.km-logo" karaoke-public/src/views/ --include="*.vue"` возвращает пусто. `grep -rn "\.km-header\|\.km-back\|\.km-logo" karaoke-public/src/components/AppHeader.vue` возвращает стили (это правильно — single source of truth).

### Verification for User Story 2

- [x] T031 [US2] Grep-проверка 1 (CSS в views): `grep -rn "\.km-header\|\.km-back\|\.km-logo" karaoke-public/src/views/ --include="*.vue"` → ожидаемо **пусто**. Если не пусто — найти файлы с дублями и удалить соответствующие `<style scoped>` блоки (не трогать `<template>` ссылки на `<AppHeader>`).
- [x] T032 [P] [US2] Grep-проверка 2 (CSS в AppHeader): `grep -c "\.km-header\|\.km-back\|\.km-logo" karaoke-public/src/components/AppHeader.vue` → ожидаемо **>0** (стили есть, single source of truth).
- [x] T033 [P] [US2] Grep-проверка 3 (legacy km-brand-logo): `grep -rn "km-brand-logo" karaoke-public/src/views/ --include="*.vue"` → ожидаемо **пусто**. Если найдено — заменить на `.km-logo` + логику `.km-logo-large` в AppHeader (FR-013).
- [x] T034 [P] [US2] Финальный ESLint baseline check: `tools/check-eslint-baseline.sh karaoke-public` → baseline не вырос. Если вырос — починить или добавить в baseline с обоснованием (Constitution § VI FR-007).

**Checkpoint**: US2 удовлетворена. 0 CSS-дублей в views. SC-002 пройден.

---

## Phase 5: User Story 3 — Специализированная шапка редактора (Priority: P3)

**Goal**: `EditorWorkView` использует `<AppHeader>` через slots (left = back, center = заголовок песни + автор, right = статус-бейдж). Логотип не отображается (или отображается без ссылки) — это рабочий инструмент.

**Independent Test**: открыть `/account/editor/<id>` — заголовок песни и статус-бейдж в шапке. Открыть `/account/editor` (список заданий) — обычный `<AppHeader>` из US1 (мигрирован в T021).

### Implementation for User Story 3

- [x] T035 [US3] Мигрировать `karaoke-public/src/views/EditorWorkView.vue`: заменить `<header class="km-header ke-sticky-top">` (строки ~3-25) на `<AppHeader :sticky="true">` + три слота:
  - **slot `left`**: `<RouterLink to="/account/editor" class="km-back">← Мои задания</RouterLink>`
  - **slot `center`**: `<template v-if="task"><span class="ke-h-song">{{ task.songName }}</span><span class="ke-h-author">{{ task.author }}</span></template>` (с существующими `ke-h-*` стилями, перенесёнными в scoped)
  - **slot `right`**: `<span v-if="task" class="ke-badge" :class="\`ke-badge-${status}\`">{{ statusLabel }}</span>`
  
  Удалить `<header>`-блок из template. Удалить из `<style scoped>` блоки `.km-header`, `.ke-sticky-top`, `.ke-header-inner` (но оставить `.ke-h-song`, `.ke-h-author`, `.ke-badge`, `.ke-badge-*` — специфичны для задания). Импорт AppHeader + регистрация в components.
- [x] T036 [US3] Запустить `cd karaoke-public && npm run build && npm run lint` — должен пройти. `EditorWorkView` использует `<AppHeader>` + slots корректно.

**Checkpoint**: US3 функциональна. EditorWorkView имеет специализированный header (центр + бейдж), но через общий `<AppHeader>`-компонент.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Документация, LiveDoc, финальная проверка, PR.

- [x] T037 [P] Создать `livedocs/features/250-unify-site-header.md` с frontmatter (`status: Active`, `slug: 250-unify-site-header`, `related: ../architecture/L3-components.md, ../../specs/250-unify-site-header/spec.md, ../../specs/250-unify-site-header/plan.md`). Содержимое: drill-down на spec.md/plan.md, что делает (унификация шапки, AppHeader), 19 view-миграций с back/profile props, EditorWorkView slot-based, Acceptance Criteria (SC-001..SC-008), related LiveDocs (`162-fix-header-stale-premium-status`), код (AppHeader.vue + KDoc). Соответствует Constitution § VI FR-009.
- [x] T038 [P] Обновить `livedocs/features/README.md` — добавить запись о `250-unify-site-header.md` (если README — index per-feature документов). Проверить существующий формат записей.
- [x] T039 [P] Обновить `livedocs/INDEX.md` — добавить ссылку на `features/250-unify-site-header.md` в соответствующий раздел (Features, Architecture).
- [ ] T040 Ручной smoke-test (10 сценариев из `quickstart.md`): SC-001 (≥19 view), SC-003 (лого кликабельный), SC-004 (лого справа), SC-005 (editor без виджетов), SC-007 (live premium не сломан). Открыть dev-server `cd karaoke-public && npm run dev`, пройти все 10 сценариев. **Если сценарий падает — откатить соответствующую миграцию (revert T010-T028) и починить.**
- [ ] T041 Code-review checklist (Constitution § VI FR-006, FR-009): KDoc на `<AppHeader>` есть (T007), per-feature документ создан (T037), ESLint baseline не вырос (T034), 0 CSS-дублей (T031).
- [x] T042 Финальная verify-сборка: `cd karaoke-public && npm run build && npm run lint` — обе PASS. Без warning'ов.
- [x] T043 Зафиксировать `git status` + `git diff --stat`. Только intended файлы: `karaoke-public/src/components/AppHeader.vue` (new) + 20 view-файлов + `livedocs/features/250-unify-site-header.md` + `livedocs/features/README.md` + `livedocs/INDEX.md`. Никаких `deploy/.env`, `*.key`, `*.pem` (Constitution § VIII).
- [ ] T044 Commit в стиле проекта: `area: краткое описание` (на русском). Пример: `public: единая <AppHeader> для 19 view + slot-based EditorWorkView (spec 250)`.
- [ ] T045 Push в origin: `git push -u origin 250-unify-site-header`.
- [ ] T046 Создать PR через `gh pr create --base master` (AGENTS.md «CI-gate для master»). Title: `public: унификация шапки сайта (spec 250)`. Description — со ссылкой на `specs/250-unify-site-header/spec.md` + checklist по Definition of Done из plan.md.
- [ ] T047 Дождаться CI (lint.yml) PASS через `gh pr checks`. Если FAIL — починить.
- [ ] T048 Merge через `gh pr merge --merge` **БЕЗ `--delete-branch`** (AGENTS.md «Lifecycle: ветка живёт после мёрджа»).
- [ ] T049 Deploy на прод через `cd deploy && bash do.sh build_start_public` (karaoke-public). Post-deploy: ручной smoke-test (Scenario 1, 3, 6 из quickstart.md) на проде.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: нет зависимостей — можно начать сразу.
- **Phase 2 (Foundational)**: зависит от Phase 1 — БЛОКИРУЕТ все user stories (без `<AppHeader>` нечего мигрировать).
- **Phase 3 (US1)**: зависит от Phase 2 — миграция 19 view.
- **Phase 4 (US2)**: зависит от Phase 3 — verification (вся дедупликация делается внутри T010-T028).
- **Phase 5 (US3)**: зависит от Phase 2 — EditorWorkView использует `<AppHeader>` через slots.
- **Phase 6 (Polish)**: зависит от всех user stories.

### User Story Dependencies

- **US1 (P1)**: требует `<AppHeader>` из Phase 2.
- **US2 (P2)**: требует завершения US1 (CSS-дедупликация происходит внутри US1).
- **US3 (P3)**: требует `<AppHeader>` из Phase 2. Не зависит от US1 (EditorWorkView — отдельный файл).

### Within Each User Story

- T001-T005 → параллельно (Setup preconditions).
- T006-T009 → последовательно (Foundational: создание компонента).
- T010-T028 → **параллельно** (миграция 19 разных view-файлов, нет cross-file зависимостей).
- T029-T030 → последовательно (Verify US1).
- T031-T034 → параллельно (Grep-проверки US2).
- T035-T036 → последовательно (US3 EditorWorkView).
- T037-T049 → параллельно где возможно (docs + smoke-test + PR).

### Parallel Opportunities

- Phase 1: T001-T005 все [P].
- Phase 2: T008, T009 — [P] (build + lint параллельно).
- Phase 3: T010-T028 — **все [P]** (19 разных view-файлов, никаких cross-file зависимостей).
- Phase 4: T032, T033, T034 — [P] (3 grep-проверки + lint).
- Phase 6: T037, T038, T039 — [P] (3 документа); T047-T048 — последовательно (CI → merge).

---

## Implementation Strategy

### MVP First (US1 Only)

1. ✅ Phase 1: Setup (T001-T005).
2. ✅ Phase 2: Foundational (T006-T009) — создать `<AppHeader>`.
3. ✅ Phase 3: US1 (T010-T030) — мигрировать 19 view, базовая функциональность.
4. **STOP and VALIDATE**: build + lint + grep на dev-server. Открыть `/` и `/zakroma` — шапка идентична.
5. Deploy на dev/staging (опционально) или сразу на прод.

### Incremental Delivery

1. ✅ Setup + Foundational → `<AppHeader>` готов.
2. ✅ US1 → 19 view мигрированы, MVP на проде.
3. ✅ US2 → CSS deduplication (внутри US1 уже сделано, US2 — verification).
4. ✅ US3 → EditorWorkView slot-based, финальная polish.
5. Каждая фаза deploy'ится независимо (но для одной фичи — один PR).

### Parallel Team Strategy

- Разработчик A: Phase 2 + начало US1 (T006-T012) — foundational.
- Разработчик B: US3 (T035) после Phase 2.
- Разработчик C: Phase 6 (T037-T039) — документация параллельно.

После завершения T009 (Phase 2) — все 19 view-миграций T010-T028 могут идлиться параллельно разными людьми (если команда > 1).

---

## Notes

- **Scope**: 19 prop-based view + 1 slot-based (`EditorWorkView`) = 20 миграций. `PlayerView`, `ShareView`, `SubscriptionReturnView` — НЕ мигрируются (нет шапки, A-002).
- **Live-логика premium** (LiveDoc 162): не затрагивается. `AuthStatusWidget` реактивен на `auth.isPremium` независимо от того, где рендерится. После миграции — поведение идентично.
- **Vue Router query**: `<RouterLink :to="{ path, query }">` поддерживается из коробки (Vue Router 4). Для `AuthorPlaylistView` (`back.query = { author: ... }`) — стандартный паттерн.
- **CSS-переменные** (`--km-header`, `--km-border`, `--km-accent`): определены в `karaoke-public/src/style.css`, `<AppHeader>` их использует — никаких изменений в общем CSS.
- **Mobile/responsive**: текущее поведение шапки (flex + wrap) наследуется `<AppHeader>`'ом. Бургер-меню — отдельная задача (A-010).
- **Регрессии после деплоя**: 10 manual smoke-test сценариев из `quickstart.md`. Особое внимание к Scenario 6 (live premium) и Scenario 1 (лого кликабельный).
- **После успешного merge в master**: feature-ветка `250-unify-site-header` НЕ удаляется (AGENTS.md «Lifecycle: ветка живёт после мёрджа»).
- **Per-feature документ** `livedocs/features/250-unify-site-header.md` создаётся в T037 (Phase 6) — обязателен по Constitution § VI FR-009.
- **Все 19 миграций T010-T028** — атомарные (touch один файл), могут делаться параллельно. Если какой-то commit конфликтует с master — rebase + resolve + push.
- **EditorWorkView refactor** (T035) — единственный нетривиальный кейс (slots), требует осторожности с `ke-*` стилями. Перед merge — обязательно вручную открыть `/account/editor/<id>` на dev-server.
- **LiveDoc 250** должен cross-reference `162-fix-header-stale-premium-status` (related LiveDoc) — это требование cross-link-density (LiveDoc CI).