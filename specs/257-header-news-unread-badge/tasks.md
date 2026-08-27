---
description: "Task list for 257-header-news-unread-badge"
---

# Tasks: Бейдж непрочитанных новостей в шапке

**Input**: Design documents from `/specs/257-header-news-unread-badge/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/useNewsUnreadCount-composable.md, contracts/AuthStatusWidget-integration.md, quickstart.md

**Tests**: NOT requested. Project convention — manual smoke-test через `quickstart.md` (10 сценариев). Constitution § «Тесты»: существующие unit-тесты `@Disabled`, автотестов в CI нет.

**Organization**: Tasks grouped by user story (US1, US2 — оба P1; US3 — P2). Каждая story independently testable.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- Web app (frontend-only изменения): `karaoke-public/src/`
- LiveDoc: `livedocs/features/257-header-news-unread-badge.md`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Verify dev environment готов к имплементации (проект уже инициализирован — никаких bootstrap-операций не требуется).

- [x] T001 Проверить, что локально работает `npm run dev` в `karaoke-public/` (Vite dev server поднимается без ошибок, существующие компоненты `AuthStatusWidget` и `App.vue` компилируются). Если нет — починить зависимости (`npm install`) до старта US1.
- [x] T002 Проверить, что ветка `257-header-news-unread-badge` активна (`git branch --show-current`) и `feature.json` указывает на `specs/257-header-news-unread-badge/`. Если нет — `git checkout 257-header-news-unread-badge`.

**Checkpoint**: dev env готов, можно стартовать Phase 2.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Composable `useNewsUnreadCount` — ядро фичи, от него зависят все 3 user stories (US1 потребляет computed-поля, US2 убирает старый `NewsBell.vue`, US3 верифицирует polling). Этот phase MUST быть завершён до Phase 3.

**⚠️ CRITICAL**: US1, US2, US3 не могут стартовать без готового composable.

- [x] T003 Создать `karaoke-public/src/composables/useNewsUnreadCount.js` со module-level singleton state: `count = ref(0)`, `pollingPaused = ref(false)`, internal `pollTimer = null`, internal `pollOnce = async () => { ... }` (try/catch без сброса count, fetch `fetchNewsSince(lastSeenId)`). JSDoc-комментарий с `@see` ссылками на `specs/257-header-news-unread-badge/spec.md` и `livedocs/features/250-unify-site-header.md` (Constitution § VI FR-006).
- [x] T004 Добавить silent reset логику (FR-013, clarified 2026-08-27) в `karaoke-public/src/composables/useNewsUnreadCount.js`: при первом вызове `useNewsUnreadCount()` если `localStorage.getItem('km_news_last_seen_id')` === `null`, выполнить `fetchNews(0, 1)`, взять `body.items[0]?.id`, записать в `localStorage['km_news_last_seen_id']`. На network error — fallback (не писать, продолжить как `NewsBell`).
- [x] T005 [P] Добавить computed values в `karaoke-public/src/composables/useNewsUnreadCount.js`: `badgeText` (ComputedRef: `''` если count=0, `String(count)` если 0<count<50, `'50+'` если count≥50), `ariaLabel` (ComputedRef: пустая или `'{count} непрочитанных {pluralForm}'` через локальную функцию `pluralize(n, ['новость', 'новости', 'новостей'])`), `showBadge` (ComputedRef: `count > 0`).
- [x] T006 [P] Добавить route change detection в `karaoke-public/src/composables/useNewsUnreadCount.js`: `useRoute()` + `watch(() => route.name, ...)` — pause polling на `/news`, `/player`, `/share`, при `route.query.share === '1'`; resume при уходе. Для анонимов (нет `localStorage.km_auth_token`) — polling не запускается вообще (Pass 52 protection).
- [x] T007 [P] Реализовать `reset()` функцию в `karaoke-public/src/composables/useNewsUnreadCount.js`: `localStorage.removeItem('km_news_last_seen_id')`, `count.value = 0`, `await pollOnce()` — используется для тестов и debug, в production UI не вызывается. Закрыть экспорт через `export function useNewsUnreadCount() { return { count, badgeText, ariaLabel, showBadge, pollingPaused, reset } }`.

**Checkpoint**: composable готов. US1 может стартовать (потребляет `badgeText`, `ariaLabel`, `showBadge`).

---

## Phase 3: User Story 1 — Inline бейдж в шапке (Priority: P1) 🎯 MVP

**Goal**: Залогиненный пользователь видит inline бейдж рядом с ссылкой «Новости» в правом слоте `<AppHeader>` — с числом 1..49 или «50+». Для нового пользователя бейдж скрыт до первой реально новой новости.

**Independent Test**: открыть `/` под залогиненным с `count = 3` → `<span class="km-news-badge">3</span>` рядом с «Новости», `aria-label="3 непрочитанных новости"`. Открыть `localStorage` без `km_news_last_seen_id` → бейдж скрыт, ключ создан.

### Implementation for User Story 1

- [x] T008 [US1] Модифицировать `karaoke-public/src/components/AuthStatusWidget.vue`: добавить import `useNewsUnreadCount` из `../composables/useNewsUnreadCount`, в `setup()` добавить `const { badgeText, ariaLabel, showBadge } = useNewsUnreadCount()` и вернуть их. Существующая логика `useAuth`/`useCart` не трогается.
- [x] T009 [US1] В template `karaoke-public/src/components/AuthStatusWidget.vue` внутри `<RouterLink to="/news" class="km-auth-link km-auth-link-news">` добавить inline `<span v-if="showBadge" class="km-news-badge" :aria-label="ariaLabel">{{ badgeText }}</span>` после текста «Новости». Стиль `.km-news-badge` (CSS, согласован с `.km-cart-count`) добавить в `<style scoped>`: фон `#e05555`, цвет `#fff`, `font-size: 0.68rem`, `border-radius: 10px`, `padding: 0 0.4em`, `margin-left: 0.3em`, `vertical-align: top`. Бейдж MUST быть внутри `<RouterLink>`, чтобы наследовать `display: none` на ≤ 700px (FR-011).
- [ ] T010 [US1] Валидация US1 в браузере: `npm run dev` → открыть `/` под залогиненным с 3 непрочитанными → бейдж «3» виден. Сценарии quickstart.md #2, #3, #4, #5, #10 — PASS. Manual smoke-test без автотестов.

**Checkpoint**: US1 полностью функционален. Можно отдельно релизить MVP (бейдж работает, плавающая иконка ещё есть, polling работает).

---

## Phase 4: User Story 2 — Удаление плавающей иконки (Priority: P1)

**Goal**: На любой странице публичного сайта плавающая кнопка `📰` в правом верхнем углу отсутствует. Информация о новых новостях передаётся только через inline-бейдж из US1.

**Independent Test**: открыть `/` (или любую страницу) в DevTools → `document.querySelector('.nwb-wrap')` возвращает `null`. `grep -rn "nwb-wrap\|nwb-btn" karaoke-public/src/` — 0 вхождений в production-коде (SC-001).

### Implementation for User Story 2

- [x] T011 [US2] Модифицировать `karaoke-public/src/App.vue`: удалить `<NewsBell />` из `<template>` (строка 16), удалить `import NewsBell from './components/NewsBell.vue'` из `<script>` (строка 22), удалить `NewsBell` из `components: { PremiumUpsellModal, ChatUnreadBadge, NewsBell }` (строка 32). Оставить `<ChatUnreadBadge />` и `<PremiumUpsellModal />` — они не связаны с новостями.
- [x] T012 [US2] Удалить файл `karaoke-public/src/components/NewsBell.vue` (269 строк) — вся логика (polling, lastSeenId, флаг isAuthenticated) переехала в `useNewsUnreadCount.js` (Phase 2). Toast-функционал `showToast()` уже удалён в A-003/FR-008. Проверить `git status` — файл ушёл.
- [ ] T013 [US2] Валидация US2 в браузере: `npm run dev` → открыть `/` → плавающая кнопка `📰` отсутствует. Сценарий quickstart.md #7 — PASS. Grep `nwb-wrap\|nwb-btn` — пусто. Grep `NewsBell` в `karaoke-public/src/` (исключая удалённый файл) — пусто.

**Checkpoint**: US2 готов. Бейдж работает (US1) + иконки нет (US2) = основная фича завершена.

---

## Phase 5: User Story 3 — Polling бэкенда (Priority: P2)

**Goal**: Polling `/api/public/news/since` каждые 45 сек сохраняется, активен для залогиненных на `/`, приостановлен на `/news`, `/player`, `/share`. Результат обновляет бейдж (US1).

**Independent Test**: под залогиненным на `/` ждать 45 сек → 1 GET `/api/public/news/since?id={lastSeenId}` в DevTools Network. Создать новую запись в `tbl_news` (через админку / прямым SQL на LOCAL) → следующий polling обновляет бейдж без F5.

### Implementation for User Story 3

- [ ] T014 [US3] Валидация US3 в браузере: сценарий quickstart.md #5 (открытие `/news` сбрасывает бейдж) + #6 (polling приостановлен на `/player`) + #8 (backend error не сбрасывает count). Manual smoke-test — три тика polling-а подряд, проверить счётчик запросов в Network tab. ⏳ **Code complete; требуется browser validation пользователем.**
- [ ] T015 [US3] Проверить, что `NewsView.markAllSeen()` (`karaoke-public/src/views/NewsView.vue:103-108`) корректно работает с новым `useNewsUnreadCount`: открыть `/news`, дождаться загрузки ленты → `localStorage.km_news_last_seen_id` поднят до `maxId` → вернуться на `/` → следующий polling возвращает `count = 0` → бейдж скрыт. Если не работает — поправить `markAllSeen()` логику. ⏳ **Code complete; требуется browser validation пользователем.**

**Checkpoint**: US3 готов. Polling синхронизирован с открытием `/news`. Live badge updates в реальном времени.

---

## Phase 5b: User Story 4 — Smart reset (Immediate + Auto-read, Priority: P1) 🔄 ИТЕРАЦИЯ 2

**Goal**: Расширить `useNewsUnreadCount` двумя поведениями: (a) **immediate reset** при переходе на `/news` (бейдж исчезает в ту же секунду, до HTTP-запроса `NewsView`); (b) **auto-read через 10 секунд** на главной странице `/` (где рендерится `<LatestNewsSection>` — пользователь «увидел» последние новости).

> **Итерация 2** (добавлено 2026-08-27 по фидбэку пользователя). Phase 1-5a уже реализованы и закоммичены в feature-ветке (Phase 2-5 в tasks.md помечены [X]).

**Independent Test**:
- Immediate: открыть `/`, дождаться `count = 3` (через polling), кликнуть по ссылке «Новости» → бейдж исчезает **до** того, как `/news` отрендерится (визуально мгновенно).
- Auto-read: открыть `/`, дождаться `count = 3` → НЕ кликать, НЕ навигировать → через 10 сек бейдж исчезает.

### Implementation for User Story 4

- [x] T023 [US4] Добавить в `karaoke-public/src/composables/useNewsUnreadCount.js` module-level `items: Ref<News[]> = ref([])`. Обновлять `items.value = data.items.slice(0, COUNT_CAP)` в `pollOnce()` после успешного fetch (рядом с `count.value = capped`). Используется для `markRead()` — не делать лишний HTTP-запрос (R-011).
- [x] T024 [US4] Реализовать `function markRead()` в `karaoke-public/src/composables/useNewsUnreadCount.js`: (1) `cancelAutoRead()`; (2) если `items.value.length === 0` → return (fallback на `NewsView.markAllSeen()`); (3) `const maxId = Math.max(...items.value.map(i => Number(i.id) || 0))`; (4) если `maxId <= 0` → return; (5) `localStorage.setItem(STORAGE_KEY, String(maxId))` в try/catch; (6) `count.value = 0`. Идемпотентна. Добавить в экспорт `useNewsUnreadCount()`.
- [x] T025 [US4] Обновить route watcher `applyRouteState(route)` в `karaoke-public/src/composables/useNewsUnreadCount.js`: добавить ветку `if (route?.name === 'news')` внутри `if (hidden)` блока → вызвать `markRead()` (FR-015). ВАЖНО: вызывать ДО `stopPolling()`, чтобы reset был синхронным с route change.
- [x] T026 [US4] Добавить `const NEWS_SHOWN_ROUTES = new Set(['/'])` и `const AUTO_READ_DELAY_MS = 10000` в `karaoke-public/src/composables/useNewsUnreadCount.js` (R-009 — пока только `/`). Реализовать `function startAutoRead()`: (1) `cancelAutoRead()`; (2) проверить `currentRoute?.path === '/'`; (3) проверить `count.value > 0`; (4) проверить `isAuthenticated()`; (5) `autoReadTimer = setTimeout(() => { markRead(); autoReadTimer = null; }, AUTO_READ_DELAY_MS)`. Реализовать `function cancelAutoRead()`: `if (autoReadTimer) clearTimeout(autoReadTimer); autoReadTimer = null`.
- [x] T027 [US4] Добавить `let currentRoute = null` (module-level) в `karaoke-public/src/composables/useNewsUnreadCount.js`. В `init(route)` установить `currentRoute = route`. Добавить `watch(count, (newCount, oldCount) => { if (oldCount === undefined) return; if (newCount === 0) cancelAutoRead(); else if (currentRoute && NEWS_SHOWN_ROUTES.has(currentRoute.path)) startAutoRead(); })`. Также в `applyRouteState()` добавить вызов `startAutoRead()` или `cancelAutoRead()` при смене route на/с `/` (FR-016, US4 AC3).
- [ ] T028 [US4] Валидация US4 в браузере: сценарии quickstart.md #11 (immediate на `/news`), #12 (auto-read 10 сек на `/`), #13 (timer cancel при уходе с `/`), #14 (timer restart при новых новостях), #15 (возврат с `/news` не запускает timer). Manual smoke-test, замерить таймер 10 сек секундомером. ⏳ **Pending user browser validation.**

**Checkpoint**: US4 готов. Бейдж живёт ровно столько, сколько пользователю нужно для «прочтения»: мгновенно при открытии ленты, 10 сек при пассивном просмотре главной.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Финальные шаги — LiveDoc, ESLint baseline check, полная quickstart валидация, optional cleanup.

- [x] T016 [P] Создать `livedocs/features/257-header-news-unread-badge.md` по шаблону `livedocs/templates/feature-summary.md`. Содержание: описание фичи (замена иконки на бейдж), краткий список US (3 шт.), ссылки на FR-001..FR-014 (spec.md), acceptance criteria (AC-1: бейдж виден, AC-2: «50+» при cap, AC-3: a11y aria-label, AC-4: no floating bell), related LiveDocs (`250-unify-site-header.md`). Constitution § VI FR-009 — обязательство.
- [x] T017 [P] Обновить `livedocs/INDEX.md` (таблица `### SDD — livedocs/features/`) — добавить строку: `` [`257-header-news-unread-badge.md`](features/257-header-news-unread-badge.md) | Бейдж непрочитанных новостей в шапке (замена NewsBell.vue) ``. Это требует `tools/check-livedocs-structure.sh` на CI — новый LiveDoc должен попасть в индекс.
- [x] T018 Запустить `cd karaoke-public && npm run lint` — 0 НОВЫХ нарушений baseline (`tools/check-eslint-baseline.sh karaoke-public`). Если есть новые — починить стиль (предположительно в `useNewsUnreadCount.js` или `AuthStatusWidget.vue`).
- [x] T019 Запустить `cd karaoke-public && npm run build` — build PASS. Если ошибка — чаще всего проблема с импортом composable или шаблоном `<AppHeader>`.
- [x] T020 [P] Backend compile check (проект Karaoke требует): `./gradlew :karaoke-web:compileKotlin --parallel` — PASS (хотя мы не трогаем бэкенд, проверка ничего не сломала).
- [x] T021 [P] Запустить `bash tools/check-livedocs-structure.sh` — PASS (новый LiveDoc в индексе, ≥5 фич в `livedocs/features/` и т.д.).
- [ ] T022 Полная quickstart валидация: пройти все 10 сценариев из `specs/257-header-news-unread-badge/quickstart.md` руками (или через деплой на dev-pc), записать результат в PR description: «Quickstart сценарии 1-10: PASS». ⏳ **Code complete; требуется browser validation пользователем.**

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately (trivially short).
- **Foundational (Phase 2)**: Depends on Setup completion — **BLOCKS all user stories**.
- **User Story 1 (Phase 3)**: Depends on Phase 2 (composable готов) — может стартовать параллельно с US2/US3 после Phase 2.
- **User Story 2 (Phase 4)**: Depends on Phase 2 (composable готов, чтобы удалить старый `NewsBell.vue` безопасно) + US1 (рекомендуется, но не обязательно — `AuthStatusWidget` интегрирован = есть куда показать count).
- **User Story 3 (Phase 5)**: Depends on Phase 2 (polling внутри composable) + US1 (count должен отображаться, чтобы видеть обновления).
- **Polish (Phase 6)**: Depends on US1+US2+US3 — финальные шаги.

### User Story Dependencies

- **US1 (P1)**: Can start after Phase 2. Independent of US2/US3.
- **US2 (P1)**: Can start after Phase 2. Ideally after US1 (чтобы видеть куда ушёл count), но не строго.
- **US3 (P2)**: Can start after Phase 2. Independent of US2 (polling работает даже если bell ещё есть).

### Within Each User Story

- T008 (setup import) перед T009 (template binding) — порядок импорта важен для Vue.
- T009 (template) → T010 (валидация в браузере).
- T011 (App.vue) перед T012 (delete NewsBell.vue) — если удалить файл до App.vue cleanup, build сломается.
- T014, T015 — оба ручных, не имеют строгого порядка.

### Parallel Opportunities

- **Phase 2**: T005, T006, T007 — все [P] (разные computed/watcher/reset exports, не пересекаются).
- **Phase 3 (US1)**: T008, T009 — не [P] (тот же файл `AuthStatusWidget.vue`, последовательно).
- **Phase 4 (US2)**: T011, T012 — не [P] (T012 зависит от T011).
- **Phase 6 (Polish)**: T016, T017, T020, T021 — все [P] (разные файлы, разные проверки).

---

## Parallel Example: User Story 1

US1 всего 3 задачи, последовательные. Параллелизация только в Phase 2 (composable).

```bash
# Phase 2 — все computed/watcher/reset в параллель (после T003+T004):
Task: "T005 [P] Добавить computed values (badgeText, ariaLabel, showBadge)"
Task: "T006 [P] Добавить route change detection"
Task: "T007 [P] Реализовать reset() функцию"
```

---

## Implementation Strategy

### MVP First (User Story 1 + 2)

1. ✅ Phase 1: Setup — verify dev env
2. ✅ Phase 2: Foundational — composable `useNewsUnreadCount`
3. ✅ Phase 3: US1 (P1) — inline бейдж в `AuthStatusWidget`
4. ✅ Phase 4: US2 (P1) — удалить `NewsBell.vue`, почистить `App.vue`
5. 🛑 **STOP and VALIDATE**: ручной smoke-test quickstart сценарии #2, #3, #7
6. 🚀 Deploy / demo if ready (фича пользовательская — обе P1 готовы)

### Incremental Delivery

1. Setup + Foundational → composable готов, polling работает (но бейджа ещё нет)
2. + US1 → бейдж виден, но плавающая иконка ещё есть (странный промежуточный UX — НЕ релизить)
3. + US2 → бейдж + нет иконки = MVP готов к деплою
4. + US3 → polling синхронизирован с `/news` (это уже работало в Phase 2, но US3 — верификация)
5. + Polish → LiveDoc + ESLint + build PASS = PR готов к merge

### Parallel Team Strategy

С одним разработчиком (текущий сценарий Karaoke — solo) — последовательная стратегия MVP First.
С 2+ разработчиками:
- Dev A: Phase 2 (composable).
- Dev B (после Phase 2): Phase 3 (US1) — AuthStatusWidget интеграция.
- Dev A: Phase 4 (US2) — после US1.

---

## Notes

- [P] tasks = different files, no dependencies (см. формат задач).
- [Story] label maps task to specific user story (US1/US2/US3) — обязательно для Phase 3+.
- Каждая user story independently testable (см. Independent Test в Phase 3/4/5).
- Tests NOT included (Constitution § «Тесты»: автотесты в CI отсутствуют, существующие `@Disabled`).
- Commit after each task or logical group (`git commit -m "..."` style: `area: краткое описание`).
- Stop at Phase 4 checkpoint для MVP demo.
- Avoid: vague tasks (все имеют file path + конкретное действие), same-file conflicts (T008/T009 sequential), cross-story deps breaking independence.
