# Research: 257-header-news-unread-badge

> Phase 0 — исследование неизвестных и лучших практик перед проектированием.

## R-001: Composable pattern в `karaoke-public` — singleton vs per-component

**Decision**: singleton на module-level (как `useAuth`, `useCart`, `useDesign`).

**Rationale**: в проекте устоявшийся паттерн — module-level `ref` + функция-обёртка `useX()`. Преимущества:
- Один `setInterval` на всё приложение (не на каждый `<AppHeader>` / `<AuthStatusWidget>` монтирование).
- Один polling-timer переживает переход между страницами (нет race condition при переподписке).
- При unmount компонента состояние и таймер НЕ уничтожаются (важно: если бы был per-component, polling останавливался бы при переходе с одной страницы на другую и перезапускался — лишний HTTP-запрос + потеря 45 сек).

**Alternatives considered**:
- Per-component instance (каждый вызов `useNewsUnreadCount()` создаёт свой `ref`) — отклонён: лишние таймеры, race conditions при HMR.
- Provide/inject (Vue DI) — отклонён: избыточно для одного источника истины.

**Источник в коде**: `karaoke-public/src/composables/useAuth.js:5-8` — `const token = ref(...)` на module-level; `useCart.js:7-9` — то же самое; `useDesign.js` — singleton.

## R-002: Routing/page change detection внутри composable

**Decision**: использовать `useRoute()` из `vue-router` внутри composable для watch `$route.name` (паттерн из `NewsBell.vue:87-99`).

**Rationale**: нужен для приостановки polling-а на `/news`, `/player/*`, `/share/*` (FR-005). `useRoute()` — стандартный способ получить реактивный route в Composition API.

**Alternatives considered**:
- Передача `routeName` пропсом из компонента — отклонён: компонент не знает имя текущего маршрута (это ответственность роутера), coupling.

## R-003: Обработка ошибок / отсутствия сети

**Decision**: `try/catch` вокруг каждого polling-вызова; **не** сбрасывать `count` в 0 при ошибке — оставлять последнее известное значение (Edge case в spec).

**Rationale**: пользователь не должен видеть «0 непрочитанных» во время сетевого сбоя (это вводит в заблуждение — может подумать, что новостей нет). Текущее поведение `NewsBell.vue:127-129` — то же самое.

## R-004: Склонение «новость / новости / новостей» для `aria-label`

**Decision**: использовать встроенную функцию склонения (нет внешних i18n-зависимостей).

**Rationale**: русские правила: 1 (искл.: 11) → «новость», 2-4 (искл.: 12-14) → «новостИ», 5-20 + 0 → «новостЕЙ». Универсальная функция `pluralize(n, ['новость', 'новости', 'новостей'])` (10 строк кода) — без зависимостей.

**Примеры**:
- 1 → «1 непрочитанная новость»
- 3 → «3 непрочитанных новости»
- 12 → «12 непрочитанных новостей»
- 50 → «50+ непрочитанных новостей»

**Alternatives considered**:
- Всегда «непрочитанных новостей» (без склонения) — отклонён: выглядит неестественно для 1 и 2.
- Intl.PluralRules API — overkill для одной фразы на русском.

## R-005: Удаление `NewsBell.vue` vs рефакторинг в composable

**Decision**: рефакторинг — логика из `NewsBell.vue` (polling, last-seen) переезжает в `composables/useNewsUnreadCount.js`; файл `NewsBell.vue` **удаляется**.

**Rationale**:
- Тост-логика (FR-008) удаляется как deprecated-функционал.
- Иконка-кнопка (FR-007) удаляется — заменена inline-бейджем.
- Сохранять `NewsBell.vue` как deprecated-обёртку нет смысла: 100% кода `NewsBell.vue` переезжает в composable.
- Меньше файлов = меньше шума в grep / code review.

**Alternatives considered**:
- Оставить `NewsBell.vue` и изменить на «no-op компонент» — отклонён: мёртвый код.
- Переименовать `NewsBell.vue` → `useNewsUnreadCount.js` — отклонён: новый путь в `composables/` лучше отражает назначение; не нарушает convention.

## R-006: ESLint baseline impact

**Decision**: новый composable пишется в стиле существующих (`useCart.js`, `useAuth.js`) — без `var`, без console.log, без неиспользуемых переменных. Ожидаемо 0 новых ESLint-нарушений.

**Rationale**: проверка `tools/check-eslint-baseline.sh karaoke-public` после имплементации (FR/SC-007).

## R-007: Per-feature LiveDoc обязательство

**Decision**: создать `livedocs/features/257-header-news-unread-badge.md` (Constitution § VI FR-009).

**Rationale**: меняем одну из 9 ключевых подсистем (auth status widget / header), нужен per-feature документ.

## R-008: Smart reset — immediate на `/news` + auto-read через 10 сек на `/` (добавлено после фидбэка 2026-08-27)

**Decision**:
- При переходе на `/news` (route name `news`): immediate reset — `count = 0`, `localStorage['km_news_last_seen_id'] = String(max(items.id))`. Происходит в `useRoute()` watcher **до** HTTP-запроса `NewsView`.
- При нахождении на `/` (`route.path === '/'`, где `<LatestNewsSection>` показывает последние новости): `setTimeout(performAutoRead, 10_000)` запускается когда `count` переходит 0 → >0. По срабатыванию — тот же reset.

**Rationale**:
- Пользователь явно попросил: «бейдж должен сразу исчезать, а не после рефреша» (US4 AC1).
- «На главной странице (на которой показаны последние новости) то бейдж новых новостей должен пропадать после 10 секунд после появления — считаем, что пользователь прочёл новости на главной странице» (US4 AC2).
- 10 секунд — достаточно чтобы прочитать заголовок и краткое описание новости, но не раздражает пользователя задержкой.

**Механика immediate reset**:
- Используем те же `items`, что пришли из последнего успешного polling-а (`data.items`).
- Если `items` пуст (например, на первом визите до первого polling-а) — fallback: только `count.value = 0`, `localStorage` не трогаем. `NewsView.markAllSeen()` подхватит после загрузки данных.
- Watcher в composable уже слушает `route.name` — там же добавляем ветку для `route.name === 'news'`.

**Механика auto-read timer**:
- Храним `items` как `ref([])` на module-level — composable уже знает последние `items` из polling-а.
- Watch на `count` (с `oldCount !== undefined` guard, чтобы не сработать на initial): если `oldCount === 0 && newCount > 0 && currentRoute?.path === '/'` → старт таймера 10 сек.
- Watch на `count === 0` → отмена таймера.
- Watch на смену route → отмена таймера (если уходим с `/`).
- Перезапуск таймера при `count > 0 → newCount > oldCount` (новые новости поверх существующих).

**Когда таймер НЕ запускается**:
- На любом маршруте кроме `/`.
- Если `count === 0` (бейдж скрыт).
- Если `items` пуст (на самом первом визите до первого polling-а).

**Alternatives considered**:
- Делать timer не в composable, а в `HomeView.vue` (например, в `mounted`) — отклонён: размазывание логики по компонентам, composable перестаёт быть single source of truth.
- Использовать `setInterval` вместо `setTimeout` — отклонён: избыточно, нужен именно одноразовый таймер.
- 10 секунд как `import.meta.env.VITE_NEWS_AUTO_READ_MS` — отклонён: overkill для одного числа, можно вынести в настройки позже.
- Делать reset на `/news` через router navigation guard (`router.beforeEach`) — отклонён: composable уже слушает route change, дублировать в guard — лишнее coupling.

**Источник**: фидбэк пользователя 2026-08-27, добавлен в US4 (P1) в `spec.md`.

## R-009: Расширение набора маршрутов с auto-read (добавлено после фидбэка 2026-08-27)

**Decision**: пока только `/` (где `<LatestNewsSection>` показывает последние новости). ZakromaView не содержит `<LatestNewsSection>` (проверено grep'ом 2026-08-27) — auto-read на нём не нужен.

**Rationale**: минимизация поверхности — пользователь явно назвал только главную страницу. Если в будущем новостной блок появится на `/zakroma` или других страницах — расширим `NEWS_SHOWN_ROUTES` set.

**Alternatives considered**:
- Сразу добавить `/zakroma`, `/filter` «на всякий случай» — отклонён: scope creep, ненужная сложность. Конфигурируемость через set добавит ~5 строк, если потребуется.
- Использовать `route.meta.showsNews` (мета-маршрут) — отклонён: текущий router не использует meta, потребуется миграция.

## R-010: `NewsView.markAllSeen()` остаётся как defense-in-depth

**Decision**: не удалять `NewsView.markAllSeen()` (existing код, `NewsView.vue:103-108`) после добавления immediate reset в composable.

**Rationale**:
- Composable делает reset при `route.name === 'news'` до того как `NewsView` начнёт HTTP-запрос. Если `items` пуст (на первом визите) — composable НЕ пишет в localStorage (fallback), `NewsView.markAllSeen()` подхватит после загрузки `data.items`.
- Наличие двух writers — безопасно: обе пишут одно и то же (`Math.max(...items.map(i => i.id))`), идемпотентно. Удаление `markAllSeen()` — лишний риск регрессии (функция может быть вызвана из других мест в будущем).

## R-011: `items` как `ref([])` на module-level в composable

**Decision**: добавить `items: Ref<News[]>` рядом с `count: Ref<number>` в `useNewsUnreadCount.js`. Обновляется при каждом успешном `pollOnce()` (копия `data.items`).

**Rationale**:
- Нужен для `markRead()` (max id) — не делать отдельный HTTP-запрос `/since` перед reset.
- Минимальный overhead: `items` уже приходят в ответе `/since`, мы их просто сохраняем.
- Размер в памяти: cap=50 элементов × ~200 байт = ~10 KB — пренебрежимо.

**Alternatives considered**:
- Делать отдельный запрос `/api/public/news?page=0&size=1` перед каждым reset — отклонён: лишний HTTP round-trip, polling уже принёс данные.
- Не хранить `items`, парсить `localStorage` (как было в `markAllSeen`) — отклонён: тот же результат, лишний HTTP-запрос (если `items` пуст, нужно сходить за `data.items[0].id`).

---

## Резюме исследований (обновлено)

| ID | Решение | Готово к Phase 1 |
|----|---------|------------------|
| R-001 | Singleton pattern (module-level ref) | ✅ |
| R-002 | `useRoute()` для route change detection | ✅ |
| R-003 | try/catch без сброса count | ✅ |
| R-004 | Склонение «новость/новости/новостей» для aria-label | ✅ |
| R-005 | Удаление `NewsBell.vue`, логика в composable | ✅ |
| R-006 | 0 новых ESLint-нарушений | ✅ (target) |
| R-007 | LiveDoc `livedocs/features/257-header-news-unread-badge.md` | ✅ (target) |
| **R-008** | **Immediate reset на `/news` + auto-read через 10 сек на `/`** | ✅ |
| **R-009** | **Только `/` в `NEWS_SHOWN_ROUTES`** | ✅ |
| **R-010** | **`NewsView.markAllSeen()` остаётся как defense-in-depth** | ✅ |
| **R-011** | **`items: Ref<News[]>` на module-level** | ✅ |

**Нет открытых NEEDS CLARIFICATION.** Все архитектурные решения приняты на основе существующих convention проекта + явных требований пользователя от 2026-08-27.

## Резюме исследований

| ID | Решение | Готово к Phase 1 |
|----|---------|------------------|
| R-001 | Singleton pattern (module-level ref) | ✅ |
| R-002 | `useRoute()` для route change detection | ✅ |
| R-003 | try/catch без сброса count | ✅ |
| R-004 | Склонение «новость/новости/новостей» для aria-label | ✅ |
| R-005 | Удаление `NewsBell.vue`, логика в composable | ✅ |
| R-006 | 0 новых ESLint-нарушений | ✅ (target) |
| R-007 | LiveDoc `livedocs/features/257-header-news-unread-badge.md` | ✅ (target) |

**Нет открытых NEEDS CLARIFICATION.** Все архитектурные решения приняты на основе существующих convention проекта.
