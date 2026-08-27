# Implementation Plan: Унификация шапки сайта

**Branch**: `250-unify-site-header` | **Date**: 2026-08-27 | **Spec**: [spec.md](spec.md)
**Research**: [research.md](research.md) | **Data Model**: [data-model.md](data-model.md) | **Component Contract**: [contracts/AppHeader-component.md](contracts/AppHeader-component.md) | **Quickstart**: [quickstart.md](quickstart.md)

**Input**: Feature specification from `/specs/250-unify-site-header/spec.md`

## Summary

Создать Vue-компонент `<AppHeader>` в `karaoke-public/src/components/AppHeader.vue`, который унифицирует шапку публичного сайта. Логотип `KARAOKE_LOGO.png` всегда справа и кликабельный → `/`; слева — back-ссылка по контексту (`← Главная`, `← Закрома`, `← Мои плейлисты`, `← Мои задания`, `← Личный кабинет`); в правом слоте — `AuthStatusWidget` + theme toggle (везде, кроме editor-страниц). Миграция 16 view-файлов: заменить `<header class="km-header">` блоки на `<AppHeader>` с пропами/slot-ами, удалить дублирующиеся CSS-стили (`.km-header*`, `.km-back`, `.km-logo`).

**Out of scope** (A-002, A-003): `PlayerView`, `ShareView`, `SubscriptionReturnView` остаются без шапки (full-screen / минималистичные). `EditorWorkView` — специализированная шапка (slot-based).

## Technical Context

**Language/Version**: Vue 3 (`<script setup>` + `<template>` + `<style scoped>`), Node 22 LTS, Vite 5
**Primary Dependencies**: `vue-router` (`RouterLink`), `useAuth()` composable (`useAuth.js`), `useDesign()` composable (`useDesign.js`), `AuthStatusWidget` (existing component)
**Storage**: N/A (UI-only refactor)
**Target Platform**: `karaoke-public` SPA (Vue 3 + Bootstrap 5)
**Project Type**: Web frontend (`karaoke-public/`)
**Performance Goals**: SC-001 ≥14 view используют `<AppHeader>`; SC-002 0 CSS-дублей; SC-008 ≥50 строк net-deleted
**Constraints**:
- Не менять существующие composables (`useAuth`, `useDesign`) — `<AppHeader>` их потребляет через `AuthStatusWidget` и theme toggle.
- Не менять Vue Router — `back` prop передаёт `{ to, query }`, `RouterLink` сам разруливает query.
- Live-логика premium (`usePremiumLiveSync`) не затрагивается (FR-016).
- Mobile/responsive: текущее поведение наследуется (FR/A-010).
- KDoc обязателен для `<AppHeader>` (Constitution § VI Code Standards, FR-006).
**Scale/Scope**: 1 новый component + миграция 16 view-файлов + удаление ~360 строк CSS-дублей + ~140 строк `<template>`-дублей.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-verified after Phase 1 design.*

- ✅ **Principle I (Self-contained автопайплайн)**: не затрагивается — это UI-рефакторинг, не вводит внешних SaaS-зависимостей.
- ✅ **Principle II (Сырой JDBC)**: не затрагивается.
- ✅ **Principle III (SyncRegistry)**: не затрагивается.
- ✅ **Principle IV (Async-очередь)**: не затрагивается.
- ✅ **Principle V (Двух-фронтенд)**: затрагивается **только `karaoke-public`** — это «наш» публичный фронтенд по Constitution. Admin `webvue3` не трогаем. Live-логика premium (`AuthStatusWidget`) остаётся в `karaoke-public`.
- ✅ **Principle VI (Code Standards)**: KDoc обязателен для `<AppHeader>` (Vue-компонент = `export default`). Per-feature документ `livedocs/features/250-unify-site-header.md` будет создан. ESLint baseline (`karaoke-public/.eslint-baseline.json`) — изменения в `<style scoped>`-блоках view-файлов (удаление) не должны нарушать baseline. Template-код переносится из 16 view в 1 component — суммарно ESLint-нарушений меньше.
- ✅ **Principle VII (Cross-Machine)**: не затрагивается — нет cross-machine изменений (только `karaoke-public/`).
- ✅ **Principle VIII (Секреты)**: не затрагивается — нет секрет-файлов.

**Constitution Check: PASS** до и после Phase 1.

## Project Structure

### Documentation (this feature)

```text
specs/250-unify-site-header/
├── plan.md                          # Этот файл
├── spec.md                          # Feature specification
├── research.md                      # Phase 0 output
├── data-model.md                    # Phase 1 output
├── quickstart.md                    # Phase 1 output (manual smoke-test)
├── contracts/
│   └── AppHeader-component.md       # Phase 1 output (Vue-component API)
├── checklists/
│   └── requirements.md              # 16/16 ✅
└── tasks.md                         # Phase 2 (через /speckit.tasks)
```

### Source Code (changes)

```text
karaoke-public/src/
├── components/
│   └── AppHeader.vue                # NEW: Vue 3 SFC, единая шапка
└── views/
    ├── HomeView.vue                 # ИЗМЕНЕНИЕ: <header> → <AppHeader :back="null" :profile-link="null" />
    ├── SearchView.vue               # ИЗМЕНЕНИЕ: <AppHeader :back="{ to: '/', label: '← Главная' }" />
    ├── ZakromaView.vue              # ИЗМЕНЕНИЕ: аналогично
    ├── SongView.vue                 # ИЗМЕНЕНИЕ: back="{ to: '/zakroma', label: '← Назад' }"
    ├── AboutView.vue                # ИЗМЕНЕНИЕ
    ├── NewsView.vue                 # ИЗМЕНЕНИЕ
    ├── PremiumView.vue              # ИЗМЕНЕНИЕ
    ├── LoginView.vue                # ИЗМЕНЕНИЕ
    ├── RegisterView.vue             # ИЗМЕНЕНИЕ
    ├── OfertaView.vue               # ИЗМЕНЕНИЕ
    ├── AccountView.vue              # ИЗМЕНЕНИЕ
    ├── EditorTasksView.vue          # ИЗМЕНЕНИЕ: back="← Личный кабинет", :show-auth-widget="false"
    ├── EditorWorkView.vue           # ИЗМЕНЕНИЕ: slot-based (left/center/right)
    ├── PlaylistEditView.vue         # ИЗМЕНЕНИЕ
    ├── AuthorPlaylistView.vue       # ИЗМЕНЕНИЕ: back с query
    ├── PlaylistsView.vue            # ИЗМЕНЕНИЕ: + profileLink
    ├── ChatView.vue                 # ИЗМЕНЕНИЕ: + profileLink
    ├── CartView.vue                 # ИЗМЕНЕНИЕ
    ├── StemJobsView.vue             # ИЗМЕНЕНИЕ
    ├── SubscriptionsView.vue        # ИЗМЕНЕНИЕ
    │
    ├── PlayerView.vue               # БЕЗ ИЗМЕНЕНИЙ (нет шапки, A-002)
    ├── ShareView.vue                # БЕЗ ИЗМЕНЕНИЙ (нет шапки, A-002)
    └── SubscriptionReturnView.vue   # БЕЗ ИЗМЕНЕНИЙ (нет шапки, A-002)
```

**Structure Decision**: Single project (Option 2 — Web application, frontend-only). Изменения сконцентрированы в `karaoke-public/src/components/AppHeader.vue` (new) + 16 view-файлов (migration).

## Implementation Approach

### Phase 1: Создание `<AppHeader>` компонента

Создать `karaoke-public/src/components/AppHeader.vue`:

```vue
<template>
  <header :class="['km-header', { 'km-header-sticky': sticky }]">
    <div class="km-header-inner" :style="{ maxWidth }">
      <div class="km-header-left">
        <!-- slot=left OR back prop -->
        <slot name="left">
          <RouterLink v-if="back" :to="backRouteTo" class="km-back">{{ back.label }}</RouterLink>
        </slot>
      </div>
      <div class="km-header-center">
        <slot name="center" />
      </div>
      <div class="km-header-right">
        <!-- slot=right OR profileLink + widgets + logo -->
        <slot name="right">
          <RouterLink v-if="profileLink" :to="profileLinkTo" class="km-back">{{ profileLink.label }}</RouterLink>
          <AuthStatusWidget v-if="showAuthWidget" />
          <div v-if="showThemeToggle" class="km-theme-toggle">
            <button :class="['km-tb', theme === 'light' ? 'active' : '']" @click="setTheme('light')" title="Светлая">☀</button>
            <button :class="['km-tb', theme === 'system' ? 'active' : '']" @click="setTheme('system')" title="Авто">⬡</button>
            <button :class="['km-tb', theme === 'dark' ? 'active' : '']" @click="setTheme('dark')" title="Тёмная">🌙</button>
          </div>
          <RouterLink to="/" class="km-logo-link">
            <img :src="logoSrc" :alt="logoAlt" :class="['km-logo', { 'km-logo-large': !back && !profileLink }]" />
          </RouterLink>
        </slot>
      </div>
    </div>
  </header>
</template>

<script setup>
import { computed } from 'vue'
import { useDesign } from '@/composables/useDesign'
import AuthStatusWidget from '@/components/AuthStatusWidget.vue'

/**
 * Единая шапка публичного сайта karaoke-public (spec 250).
 *
 * Заменяет 16 дублированных <header class="km-header">-блоков в views/.
 * Логотип ВСЕГДА справа, ВСЕГДА кликабельный → '/'. Слева — back-ссылка
 * по контексту. В правом слоте — profileLink + AuthStatusWidget + theme toggle.
 *
 * Editor-страницы (/account/editor, /account/editor/<id>) — без AuthStatusWidget
 * и theme toggle (FR-014, FR-015). EditorWorkView использует slot-based режим
 * для центрального контента (заголовок песни + статус-бейдж).
 *
 * Live-логика premium (AuthStatusWidget) наследуется автоматически — компонент
 * реактивен на auth.isPremium через usePremiumLiveSync.
 *
 * @see specs/250-unify-site-header FR-001..FR-016
 * @see livedocs/features/162-fix-header-stale-premium-status (live premium)
 * @see livedocs/features/250-unify-site-header (per-feature doc)
 */
defineProps({
  back: { type: Object, default: null },
  profileLink: { type: Object, default: null },
  showAuthWidget: { type: Boolean, default: true },
  showThemeToggle: { type: Boolean, default: true },
  sticky: { type: Boolean, default: true },
  logoSrc: { type: String, default: '/KARAOKE_LOGO.png' },
  logoAlt: { type: String, default: 'Своё Место' },
  maxWidth: { type: String, default: '900px' },
})

const { theme, applyTheme } = useDesign()
function setTheme(val) {
  theme.value = val
  applyTheme(val)
}

const backRouteTo = computed(() => {
  const props = defineProps /* dummy для type inference */
})
```

(Полная реализация будет в tasks.md / в PR — здесь показан API-контракт и общая структура.)

```vue
<style scoped>
.km-header {
  background: var(--km-header);
  border-bottom: 1px solid var(--km-border);
  padding: 0.5rem 1rem;
}
.km-header-sticky {
  position: sticky;
  top: 0;
  z-index: 100;
}
.km-header-inner {
  margin: 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}
.km-header-left, .km-header-center, .km-header-right {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}
.km-header-center {
  flex: 1;
  justify-content: center;
}
.km-back {
  color: var(--km-accent);
  text-decoration: none;
  font-size: 0.85rem;
  white-space: nowrap;
}
.km-logo-link {
  display: flex;
  align-items: center;
}
.km-logo {
  max-width: 100px;
  height: auto;
}
.km-logo-large {
  max-width: 200px;
}
.km-theme-toggle {
  display: flex;
  gap: 0.25rem;
}
.km-tb {
  background: transparent;
  border: 1px solid var(--km-border);
  color: var(--km-text);
  padding: 0.2rem 0.5rem;
  border-radius: 4px;
  cursor: pointer;
}
.km-tb.active {
  background: var(--km-accent);
  color: white;
}
</style>
```

### Phase 2: Миграция 14 view-файлов (тривиальный случай)

Каждый файл — 3 изменения:

1. **`<template>`**: заменить `<header class="km-header">...</header>` на `<AppHeader :back="{ to: '/x', label: '← X' }" />`.
2. **`<script>`**: добавить `import AppHeader from '@/components/AppHeader.vue'` и зарегистрировать в `components: { ... }`.
3. **`<style scoped>`**: удалить блоки `.km-header`, `.km-header-inner`, `.km-header-left`, `.km-header-right`, `.km-back`, `.km-logo`, `.km-brand-logo`.

**Маппинг** (16 view):

| View | back prop | profileLink | showAuthWidget | showThemeToggle |
|------|-----------|-------------|----------------|-----------------|
| `HomeView.vue` | `null` | `null` | `true` | `true` |
| `SearchView.vue` | `{ to: '/', label: '← Главная' }` | `null` | `true` | `true` |
| `ZakromaView.vue` | `{ to: '/', label: '← Главная' }` | `null` | `true` | `true` |
| `SongView.vue` | `{ to: '/zakroma', label: '← Назад' }` | `null` | `true` | `true` |
| `AboutView.vue` | `{ to: '/', label: '← Главная' }` | `null` | `true` | `true` |
| `NewsView.vue` | `{ to: '/', label: '← Главная' }` | `null` | `true` | `true` |
| `PremiumView.vue` | `{ to: '/', label: '← Главная' }` | `null` | `true` | `true` |
| `LoginView.vue` | `{ to: '/', label: '← Главная' }` | `null` | `true` | `true` |
| `RegisterView.vue` | `{ to: '/', label: '← Главная' }` | `null` | `true` | `true` |
| `OfertaView.vue` | `{ to: '/', label: '← Главная' }` | `null` | `true` | `true` |
| `AccountView.vue` | `{ to: '/', label: '← Главная' }` | `null` | `true` | `true` |
| `EditorTasksView.vue` | `{ to: '/account', label: '← Личный кабинет' }` | `null` | `false` | `false` |
| `PlaylistEditView.vue` | `{ to: '/account/playlists', label: '← Мои плейлисты' }` | `null` | `true` | `true` |
| `AuthorPlaylistView.vue` | `{ to: '/zakroma', label: '← Закрома', query: { author: $route.params.slug } }` | `null` | `true` | `true` |
| `PlaylistsView.vue` | `{ to: '/', label: '← Главная' }` | `{ to: '/account', label: 'Профиль →' }` | `true` | `true` |
| `ChatView.vue` | `{ to: '/', label: '← Главная' }` | `{ to: '/account', label: 'Профиль →' }` | `true` | `true` |
| `CartView.vue` | `{ to: '/', label: '← Главная' }` | `null` | `true` | `true` |
| `StemJobsView.vue` | `{ to: '/account', label: '← Личный кабинет' }` | `null` | `true` | `true` |
| `SubscriptionsView.vue` | `{ to: '/account', label: '← Личный кабинет' }` | `null` | `true` | `true` |

### Phase 3: Рефакторинг `EditorWorkView` (slot-based)

Текущий `EditorWorkView.vue` имеет уникальный header с центральным контентом (заголовок песни + автор) и статус-бейджем справа. Миграция через slots:

```vue
<template>
  <AppHeader :sticky="true">
    <template #left>
      <RouterLink to="/account/editor" class="km-back">← Мои задания</RouterLink>
    </template>
    <template #center>
      <template v-if="task">
        <span class="ke-h-song">{{ task.songName }}</span>
        <span class="ke-h-author">{{ task.author }}</span>
      </template>
    </template>
    <template #right>
      <span v-if="task" class="ke-badge" :class="`ke-badge-${status}`">{{ statusLabel }}</span>
    </template>
  </AppHeader>
</template>
```

(`ke-*` стили остаются в `<style scoped>` `EditorWorkView.vue` — специфичны для задания.)

### Phase 4: Создание per-feature документа

Создать `livedocs/features/250-unify-site-header.md` (согласно FR-014 + Constitution § VI «При правке кода одной из 9 ключевых подсистем...»). LiveDoc формат — аналогично `livedocs/features/162-fix-header-stale-premium-status.md`.

### Phase 5: Verify (AGENTS.md «Обязательная проверка после правок»)

```bash
# 1. Backend compile (на всякий случай, не должно ничего сломать)
./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel
# Ожидаемо: PASS (UI-only изменения)

# 2. Линтеры
./gradlew :karaoke-web:ktlintCheck                                  # backend — нет изменений, PASS
tools/check-eslint-baseline.sh karaoke-public                        # frontend — не должно быть НОВЫХ нарушений
# Ожидаемо: PASS (baseline не вырос)

# 3. Backend bootJar (не требуется, но для проформы)
./gradlew :karaoke-web:bootJar --parallel
# Ожидаемо: PASS

# 4. Frontend
cd karaoke-public && npm run build && npm run lint
# Ожидаемо: build OK, lint OK
```

## Risks & Mitigations

| Риск | Вероятность | Митигация |
|------|-------------|-----------|
| `useDesign()` импортируется в `<AppHeader>` — циклическая зависимость | Низкая | `useDesign` — composable, не компонент. Цикла нет. Проверить на dev-server. |
| Удаление CSS `.km-back` в views сломает другие элементы (не из шапки) | Низкая | Перед удалением — `grep -n "\.km-back" <view>.vue` — должно быть только в `.km-header`. |
| `?author=<slug>` query не работает в `<RouterLink>` | Низкая | Vue Router 4 поддерживает `:to="{ path, query }"` — стандартный паттерн. |
| Sticky-шапка конфликтует с другим sticky-элементом (например, player toolbar) | Низкая | PlayerView (`/player/<id>`) не использует `<AppHeader>` (A-002). Для других страниц sticky z-index = 100 — выше дефолтного. |
| ESLint baseline растёт при миграции | Низкая | Удаление `<style scoped>`-блоков уменьшает количество потенциальных нарушений. Net effect — baseline меньше или неизменен. |
| EditorWorkView теряет sticky при slot-based миграции | Средняя | Передать `:sticky="true"` явно + оставить `class="km-header ke-sticky-top"` (двойная sticky = noop, z-index не меняется). |
| LiveDoc не создан → нарушение Constitution § VI | Средняя | Phase 4 — обязательный. Проверяется в `tasks.md`. |
| Профиль не передан в `<AppHeader>` для некоторих страниц → ProfileLink не рендерится | Низкая | `profileLink: null` default = не рендерится. Smoke-test Scenario 3 покрывает. |

## Complexity Tracking

*Нет нарушений Constitution Check — таблица пуста.*

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| (нет) | — | — |

## Verification Plan

### До PR

1. Code-review: KDoc на `<AppHeader>` присутствует, props типизированы, slots задокументированы.
2. Grep-проверки:
   - `grep -c "AppHeader" karaoke-public/src/views/*.vue | awk -F: '$2>0' | wc -l` ≥ 14.
   - `grep -rn "\.km-header\|\.km-back\|\.km-logo" karaoke-public/src/views/ --include="*.vue"` → пусто.
3. Линтеры: ESLint baseline не вырос, ktlint pass.
4. Build: `npm run build` pass.

### После деплоя (manual smoke-test)

10 сценариев из [quickstart.md](quickstart.md):

- Scenario 1 (лого кликабельный)
- Scenario 2 (back-ссылки)
- Scenario 3 (profile-link)
- Scenario 4 (editor без виджетов)
- Scenario 5 (sticky)
- Scenario 6 (live premium не сломан)
- Scenario 7 (theme toggle)
- Scenario 8 (CSS duplicates check)
- Scenario 9 (author-playlist query)
- Scenario 10 (Player/Share/SubscriptionReturn без шапки)

**Все 10 сценариев должны пройти вручную** (CI тестов нет — Constitution § «Рабочий процесс»).

### Acceptance (mapping)

| SC | Verification |
|----|--------------|
| SC-001 (≥16 view) | grep + code review |
| SC-002 (0 CSS-дублей) | grep + Scenario 8 |
| SC-003 (лого кликабельный) | Scenario 1 |
| SC-004 (лого справа) | Scenario 1 |
| SC-005 (editor без виджетов) | Scenario 4 |
| SC-006 (visual regression) | Все 10 сценариев |
| SC-007 (live premium) | Scenario 6 |
| SC-008 (≥50 строк net-deleted) | code review: `(deleted_view_lines - added_view_lines) + (added_AppHeader_lines - deleted_AppHeader_lines)` ≥ -50 |

## Timeline Estimate

- Phase 1 (AppHeader.vue + стили + KDoc): ~30 мин.
- Phase 2 (миграция 18 view — 16 prop-based + 1 slot-based + 1 HomeView edge case): ~45 мин.
- Phase 3 (EditorWorkView refactor): ~15 мин.
- Phase 4 (LiveDoc): ~10 мин.
- Phase 5 (Verify: build, lint, grep): ~10 мин.
- **Итого: ~2 часа кодинга**.
- Manual smoke-test (10 сценариев): ~30 мин.
- PR review + merge: ~1-2 дня (стандартный workflow).

## Definition of Done

- [ ] `karaoke-public/src/components/AppHeader.vue` создан с KDoc.
- [ ] 16 view-файлов мигрированы на `<AppHeader>` (или явно исключены — 3 view без шапки + 1 slot-based EditorWorkView = 16 миграций).
- [ ] 0 CSS-дублей в `karaoke-public/src/views/`.
- [ ] Логотип на любой странице (где есть `<AppHeader>`) кликабельный → `/` и визуально справа.
- [ ] Editor-страницы (`/account/editor`, `/account/editor/<id>`) корректно отделены: без AuthStatusWidget и theme toggle.
- [ ] Live-логика premium не сломана (Scenario 6).
- [ ] Per-feature документ `livedocs/features/250-unify-site-header.md` создан (FR-014 + Constitution § VI).
- [ ] ESLint baseline не вырос, ktlint pass, `npm run build` pass.
- [ ] PR создан через `gh pr create --base master` (см. AGENTS.md, «CI-gate для master»).
- [ ] CI (lint.yml) — PASS.
- [ ] 1 PR → 1 merge в master → 1 деплой на прод (karaoke-public).
- [ ] Manual smoke-test (10 сценариев) проходит.

## Next Step

→ `/speckit.tasks specs/250-unify-site-header` для генерации декомпозированных задач.