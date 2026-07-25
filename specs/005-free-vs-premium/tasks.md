# Tasks: Таблица «FREE vs PREMIUM» на /premium (QW-1)

**Input**: Design documents from `/specs/005-free-vs-premium/`
**Prerequisites**: plan.md (✅), spec.md (✅), research.md (✅), data-model.md (✅), quickstart.md (✅). Нет `contracts/` — новых REST-эндпоинтов нет.

**Tests**: в `karaoke-public` автотестов нет (constitution.md «Рабочий процесс: Тесты»). Проверка — ручной сценарий в `quickstart.md` (8 пунктов).

**Organization**: задачи сгруппированы по User Story. US1 (P1) — таблица сравнения, MVP. US3 (P2) — трекинг клика. US2 (P3) — упрощённый вид для премиум.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: можно запускать параллельно (разные файлы, нет зависимостей).
- **[Story]**: US1/US2/US3.
- Полные пути к файлам включены в описания.

## Path Conventions

- Фронтенд: `karaoke-public/src/views/PremiumView.vue` (единственный файл на правку).

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: новых зависимостей/директорий не требуется.

- [X] T001 Убедиться, что `/premium` собирается и открывается локально (`cd karaoke-public && npm run dev`, или `do.sh build_start_public`) *(пересобрано и запущено, `/premium` → 200)*

**Checkpoint**: dev-сервер поднимается, `/premium` открывается.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: блокирующих предпосылок нет — существующий блок выбора тарифа не трогается, таблица независима.

*(нет задач)*

**Checkpoint**: сразу переходим к User Story.

---

## Phase 3: User Story 1 — Free-пользователь видит сравнение FREE vs PREMIUM (Priority: P1) 🎯 MVP

**Goal**: таблица из 9 строк над блоком выбора тарифа, с CTA внизу.
**Independent Test**: free-пользователь/аноним заходит на `/premium`, видит таблицу с ≥9 строками, понимает разницу FREE/PREMIUM.

### Реализация

- [X] T002 [US1] Добавить в `karaoke-public/src/views/PremiumView.vue` (`data()`) массив `COMPARISON_ROWS` из 9 строк (см. `data-model.md`): `{ feature, free, premium }`, значения — как согласовано в `spec.md` FR-003 (включая точные числа 100/500/50, без упоминаний MP4/VK/истории/уведомлений/метки-шаринга)
- [X] T003 [US1] Вставить в template `PremiumView.vue` секцию `<table class="km-compare-table">` между `<p class="km-delivery-note">` и первой `<div class="km-card km-card-primary">` (см. `plan.md`/`research.md` Decision 3): `<thead>` (Фича/FREE/PREMIUM), `<tbody>` — `v-for` по `COMPARISON_ROWS`, ✅/❌ для boolean-значений, текст как есть для строковых
- [X] T004 [US1] Визуально выделить колонку PREMIUM (CSS: фон/рамка/иконка) — стиль на `--km-*` переменных (FR-004, FR-009), не только цветом (WCAG AA, NFR-003 — например, дополнительно `aria-label` или текстовая подпись у ✅/❌) *(корона 👑 в заголовке колонки + `aria-label` на каждой ✅/❌)*
- [X] T005 [US1] Добавить CTA-кнопку «Оформить премиум-подписку» под таблицей — скроллит/ведёт к существующему блоку `.km-card-primary` (первая карточка тарифа), не новый роут (FR-005, Acceptance Scenario US1.3) *(`scrollIntoView` на `ref="tariffCard"`)*
- [X] T006 [US1] Адаптивная вёрстка таблицы: десктоп — обычная 3-колоночная `<table>`; мобильный (≤500px, тот же брейкпоинт, что и остальные `.km-*`-блоки страницы) — раскладка «стопкой» через CSS (FR-007, SC-005)
- [ ] T007 [US1] Прогнать `quickstart.md` пп.1-3 в браузере (desktop) — 9 строк, выделение PREMIUM, CTA работает. **Не закрыто**: headless-браузер недоступен в этой песочнице (та же проблема, что в `003`/`004` — `page.goto` виснет). Подтверждено статически: `docker exec karaoke-public grep` находит новый текст в собранном бандле («Что вы получите за подписку», 1 совпадение), `curl /premium` → 200, `npm run lint:check`/`prettier`/JSDoc coverage — чисто. Визуальный рендер нужна ручная проверка

**Checkpoint**: US1 (MVP) закрыт — таблица видна, понятна, с рабочим CTA.

---

## Phase 4: User Story 3 — Метрика: трекинг конверсии (Priority: P2)

**Goal**: клик по CTA пишется в `tbl_events`.
**Independent Test**: клик по CTA → проверить в Network/БД, что событие ушло.

### Реализация

- [X] T008 [US3] Импортировать `trackUi` из `../services/tracking` в `PremiumView.vue`, вызвать `trackUi('navigate', 'free_vs_premium_cta')` в обработчике клика CTA из T005 (FR-008)
- [ ] T009 [US3] Прогнать `quickstart.md` п.5 — подтвердить в DevTools Network, что `POST /api/public/events` уходит с правильными `linkType`/`linkName`. **Не закрыто** — та же причина, что T007 (нужен живой клик в браузере); механизм (`trackUi`→`apiPost`) идентичен уже работающему в `ShareButton.vue`/`SocialLinks.vue`, код-ревью даёт высокую уверенность, но живого клика не было

**Checkpoint**: клики по CTA измеримы.

---

## Phase 5: User Story 2 — Премиум-пользователь видит «Спасибо» (Priority: P3)

**Goal**: премиум-пользователь видит упрощённый вид вместо таблицы-продажника.
**Independent Test**: премиум-пользователь на `/premium` видит пометку «Вы премиум» + «Что вы получили» + ссылку на управление подпиской.

### Реализация

- [X] T010 [US2] Добавить `computed: { isPremium() { return !!(this.user && this.user.effectivePremium) } }` в `PremiumView.vue` (тот же паттерн, что в `SearchView.vue`/`ZakromaView.vue`/`PlaylistsView.vue` — см. `data-model.md`)
- [X] T011 [US2] `v-if="!isPremium"` на полную таблицу сравнения (T002-T006), `v-else` — блок «Вы премиум-пользователь — спасибо!» + «Что вы получили» с акцентом на строки таблицы, которые реально применимы (полный плеер, безлимитное избранное и т.д.)
- [X] T012 [US2] Добавить в премиум-блок ссылку «Управление подпиской» → `/account/subscriptions` (уже существующий роут)
- [ ] T013 [US2] Прогнать `quickstart.md` п.4 — залогиниться премиум-пользователем (или подставить `user.effectivePremium=true` в localStorage `km_auth_user` для локальной проверки), убедиться в переключении вида. **Не закрыто** — та же причина, что T007; текст «Вы премиум-пользователь» подтверждён в собранном бандле (2 совпадения), переключение `v-if`/`v-else` не проверено вживую

**Checkpoint**: US2 закрыт — премиум-пользователи не видят продающую таблицу.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: финальная валидация.

- [X] T014 [P] Прогнать `cd karaoke-public && npm run lint:check && npx prettier --check "src/**/*.{vue,js,ts,json}"` — правки `PremiumView.vue` проходят CI-линт *(0 warnings/errors; автоформатирован prettier --write)*
- [X] T015 [P] Прогнать `bash tools/check-jsdoc-coverage.sh --strict` — правки не роняют JSDoc coverage ниже 50% *(exit 0)*
- [ ] T016 Полный прогон `quickstart.md` (все 8 пунктов чек-листа) — финальная валидация перед PR. **Блокируется T007/T009/T013** (нет живого рендера в браузере в этой песочнице) — 5/8 пунктов подтверждены статически/линтерами, визуальные пп.1-2 и живой клик по CTA (п.5) нужны от пользователя

**Checkpoint**: всё готово к PR.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: нет зависимостей.
- **Phase 2 (Foundational)**: пустая.
- **Phase 3 (US1, P1)**: независима, MVP.
- **Phase 4 (US3, P2)**: зависит от T005 (CTA-кнопка должна существовать, прежде чем вешать на неё трекинг) — то есть после Phase 3.
- **Phase 5 (US2, P3)**: зависит от Phase 3 (оборачивает уже готовую таблицу в `v-if`) — после Phase 3, независимо от Phase 4.
- **Phase 6 (Polish)**: после всех User Story.

### Within Each Story

- Phase 3: T002 (данные) → T003 (разметка использует данные) → T004/T005/T006 можно параллельно (разные аспекты той же секции: стили, CTA, адаптивность) → T007 в конце.
- Phase 5: T010 → T011 (использует `isPremium` из T010) → T012 → T013.

### Parallel Opportunities

- Phase 3: T004, T005, T006 — параллельно после T003 (разные CSS-правила/обработчики, один и тот же файл, но не пересекающиеся блоки — при последовательном коммите рисков нет).
- Phase 6: T014, T015 — параллельно (разные команды, не пересекаются).

---

## Implementation Strategy

### MVP First (P1 only)

1. Phase 1 (Setup, тривиальный).
2. Phase 3 (US1) — таблица сравнения с CTA.
3. **STOP и VALIDATE**: `quickstart.md` пп.1-3.
4. MVP готов — free-пользователи видят честное сравнение.

### Incremental Delivery

1. MVP (US1).
2. + Phase 4 (US3) — трекинг клика (маленький инкремент).
3. + Phase 5 (US2) — вид для премиум (можно отдельным PR, не блокирует MVP).
4. + Phase 6 (Polish).

---

## Notes

- Список из 9 строк — результат построчной верификации с кодом и явного
  согласования с пользователем (2026-07-25) — **не менять** без повторного
  согласования (см. `research.md` Decision 1 для полного обоснования каждой
  строки).
- Единственный файл на правку — `PremiumView.vue`; коммитить можно одним
  PR (в отличие от `003`/`004`, здесь нет отдельных новых компонентов).
- Avoid: не трогать существующий блок выбора тарифа (`useSiteSubscription()`,
  строки 21-138 в исходном `PremiumView.vue`) — таблица только добавляется
  над ним.
