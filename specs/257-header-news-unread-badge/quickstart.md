# Quickstart: 257-header-news-unread-badge

> Manual smoke-test сценарии для валидации фичи end-to-end. **Не** unit/integration тесты (их нет в проекте, см. Constitution § «Тесты»).

## Prerequisites

1. Локально поднят `karaoke-public` (Vite dev server, обычно `http://localhost:3000`).
2. Бэкенд `karaoke-web` поднят на `http://localhost:8080` (или соответствующий URL в `.env`).
3. В БД `tbl_news` есть хотя бы 1 запись (иначе тесты `count > 0` не сработают).
4. Браузер с DevTools (для проверки DOM и Network).

## Setup

```bash
# Backend
cd /home/nsa/Karaoke
./gradlew :karaoke-web:bootJar --parallel
deploy/do.sh start_web  # или вручную через deploy/

# Frontend
cd karaoke-public
npm install
npm run dev  # → http://localhost:3000

# Проверить, что:
# 1. http://localhost:3000/ открывается, шапка видна.
# 2. http://localhost:8080/api/public/news/since?id=0 возвращает JSON (для залогиненного).
```

## Validation scenarios

### Scenario 1: Baseline (анонимный пользователь)

**Prerequisite**: выйти из аккаунта (`localStorage` без `km_auth_token`).

**Steps**:
1. Открыть `http://localhost:3000/`.
2. Смотреть на правый слот шапки → видна ссылка «Новости» **без** бейджа.
3. В DevTools Console: `document.querySelector('.km-news-badge')` → `null`.
4. В DevTools Network: подождать 45 сек → **нет** запросов `/api/public/news/since` (polling отключён для анонимов).
5. В DevTools Console: `localStorage.getItem('km_news_last_seen_id')` → либо `null` (cleared), либо число.

**Expected**:
- ✅ Ссылка «Новости» видна, бейдж НЕ виден.
- ✅ Polling не происходит.
- ✅ Никаких ошибок в Console.

### Scenario 2: Залогиненный, 3 непрочитанных

**Prerequisite**:
- Залогиниться (`localStorage.km_auth_token` существует).
- В БД есть 3 новости с `id > localStorage.km_news_last_seen_id`.
- `localStorage.km_news_last_seen_id` установить на `lastExistingId - 3`.

**Steps**:
1. Открыть `http://localhost:3000/`.
2. Смотреть на ссылку «Новости» → видна с **бейджем «3»**.
3. В DevTools Console: `document.querySelector('.km-news-badge').textContent.trim()` → `'3'`.
4. В DevTools Console: `document.querySelector('.km-news-badge').getAttribute('aria-label')` → `'3 непрочитанных новости'`.
5. В DevTools Network: 1 запрос `/api/public/news/since?id=<lastSeenId>` в течение 45 сек.

**Expected**:
- ✅ Бейдж «3» виден, скруглённый, красный фон.
- ✅ `aria-label` = «3 непрочитанных новости».
- ✅ Polling происходит каждые 45 сек.

### Scenario 3: Залогиненный, 50 непрочитанных → «50+»

**Prerequisite**:
- Залогиниться.
- `localStorage.km_news_last_seen_id` = 0 (или удалить ключ).
- В БД ≥ 50 опубликованных новостей.

**Steps**:
1. Открыть `http://localhost:3000/`.
2. Смотреть на ссылку «Новости» → видна с **бейджем «50+»**.
3. В DevTools Console: `document.querySelector('.km-news-badge').textContent.trim()` → `'50+'`.
4. В DevTools Console: `document.querySelector('.km-news-badge').getAttribute('aria-label')` → `'50+ непрочитанных новостей'`.

**Expected**:
- ✅ Бейдж «50+» (не «50», не «51+», не «99+»).
- ✅ `aria-label` корректно склоняет.

### Scenario 4: Новый пользователь (silent reset)

**Prerequisite**:
- Залогиниться.
- `localStorage.removeItem('km_news_last_seen_id')` (имитация первого визита).
- В БД ≥ 50 опубликованных новостей.

**Steps**:
1. Открыть `http://localhost:3000/`.
2. **НЕ дожидаясь** polling-а, проверить:
   - `document.querySelector('.km-news-badge')` → `null` (бейдж скрыт).
   - `localStorage.getItem('km_news_last_seen_id')` → число (не null).
3. В DevTools Network: должен быть **1 дополнительный** запрос `/api/public/news?page=0&size=1` при первой загрузке.
4. Подождать 45 сек, дождаться polling `/since` → `count = 0`, бейдж не виден.

**Expected**:
- ✅ На свежей загрузке бейдж не показывает «50+».
- ✅ `localStorage.km_news_last_seen_id` записан до основного polling-а.
- ✅ После polling бейдж остаётся скрытым (новых новостей после первого визита нет).

### Scenario 5: Открытие `/news` сбрасывает бейдж

**Prerequisite**:
- Залогиниться.
- В БД есть ≥ 3 новости с `id > localStorage.km_news_last_seen_id`.

**Steps**:
1. Открыть `http://localhost:3000/` → бейдж «3».
2. Кликнуть на ссылку «Новости» → переход на `/news`.
3. Дождаться полной загрузки ленты (видны все карточки).
4. В DevTools Console: `localStorage.getItem('km_news_last_seen_id')` → `maxId` (самый большой id из видимых).
5. Вернуться на `http://localhost:3000/` (кнопка «← Главная» или навигация).
6. Подождать ≤ 45 сек (1 polling cycle).
7. Смотреть на ссылку «Новости» → бейдж **скрыт**.

**Expected**:
- ✅ После открытия `/news` `lastSeenId` поднят до maxId.
- ✅ На следующей странице бейдж скрыт (нет непрочитанных).

### Scenario 6: Polling приостановлен на `/player`

**Prerequisite**:
- Залогиниться.
- Есть существующая песня с `id` в БД → URL `/player/<id>`.

**Steps**:
1. Открыть `/player/<id>` (full-screen плеер).
2. В DevTools Network подождать 60 сек → **нет** запросов `/api/public/news/since`.
3. Вернуться на `/` → polling возобновляется (через ≤ 1 сек первый запрос).

**Expected**:
- ✅ На `/player` polling не дёргает бэкенд вхолостую.
- ✅ На `/` polling сразу же возобновляется.

### Scenario 7: Floating bell отсутствует

**Prerequisite**: любое состояние.

**Steps**:
1. Открыть любую страницу `/`, `/zakroma`, `/about`, `/news`.
2. В DevTools Console: `document.querySelector('.nwb-wrap')` → `null`.
3. В DevTools Console: `document.querySelector('.nwb-btn')` → `null`.
4. В DevTools Elements: искать `<div class="nwb-wrap">` или `<button class="nwb-btn">` → не находится.

**Expected**:
- ✅ Плавающая иконка `📰` отсутствует в DOM.
- ✅ Никаких toast-ов не появляется в правом верхнем углу.

### Scenario 8: Backend ошибка не сбрасывает бейдж

**Prerequisite**:
- Залогиниться.
- В DevTools Network включить «Offline» (или throttling).

**Steps**:
1. Открыть `/` → дождаться первого polling-а (бейдж показывает, например, «3»).
2. Включить Offline.
3. Подождать 2 цикла polling-а (90 сек).
4. Смотреть на бейдж → **всё ещё «3»** (не сбросился в 0).

**Expected**:
- ✅ Бейдж остаётся в последнем известном значении при сетевых ошибках.
- ✅ Никаких ошибок в Console (try/catch поглощает).

### Scenario 9: a11y — screen reader

**Prerequisite**: залогиниться, бэкенд возвращает `count = 3`.

**Steps**:
1. Включить VoiceOver (macOS: `⌘ + F5`) или NVDA (Windows).
2. Перевести фокус на ссылку «Новости» (`Tab` навигация).
3. VoiceOver произносит: «Новости, 3 непрочитанных новости, link» (или эквивалент).
4. Изменить `localStorage.km_news_last_seen_id` так, чтобы `/since` вернул `count = 5`.
5. Дождаться следующего polling-а (≤ 45 сек).
6. VoiceOver (если снова перевести фокус) произносит: «Новости, 5 непрочитанных новостей, link».

**Expected**:
- ✅ `aria-label` корректно озвучивается.
- ✅ Изменение числа объявляется при переводе фокуса (НЕ через `aria-live`, шум отсутствует).

### Scenario 10: Узкий экран

**Prerequisite**: залогиненный, бейдж виден.

**Steps**:
1. На широком экране (> 700px) → ссылка «Новости» и бейдж видны.
2. Сжать окно браузера до ≤ 700px (или DevTools «Toggle device toolbar» → iPhone SE).
3. Ссылка «Новости» скрыта (существующее поведение `AuthStatusWidget.vue:86-91`).
4. В DevTools Elements: `<span class="km-news-badge">` тоже скрыт (наследует `display: none`).

**Expected**:
- ✅ Бейдж автоматически скрывается на узких экранах (внутри родительского `<RouterLink>`).

### Scenario 11: Immediate reset при переходе на `/news` (US4 FR-015)

**Prerequisite**:
- Залогиниться.
- `localStorage.km_news_last_seen_id` = (low value).
- В БД ≥ 3 новости с `id > lastSeenId`.
- `npm run dev` запущен, открыть `http://localhost:3000/`.

**Steps**:
1. На `/` проверить, что бейдж «3» виден.
2. **Медленно** навести мышь на ссылку «Новости» — НЕ кликать.
3. В DevTools Performance / или просто наблюдая визуально — кликнуть по ссылке «Новости».
4. В момент клика наблюдать за `<span class="km-news-badge">`:
   - Бейдж должен исчезнуть **в ту же секунду** (между кликом и полной загрузкой `/news`).
   - НЕ должно быть «моргания» (бейдж не висит после клика).
5. После полной загрузки `/news`:
   - В DevTools Console: `localStorage.getItem('km_news_last_seen_id')` → число `≥ 3` (был записан maxId из items).
   - В DevTools Console: `document.querySelector('.km-news-badge')` → `null`.

**Expected**:
- ✅ Бейдж исчезает до HTTP-запроса `NewsView` (`/api/public/news`).
- ✅ `localStorage.km_news_last_seen_id` обновлён синхронно (не дожидаясь `NewsView.markAllSeen()`).

### Scenario 12: Auto-read через 10 секунд на главной (US4 FR-016)

**Prerequisite**:
- Залогиниться.
- `localStorage.km_news_last_seen_id` = max существующего id (т.е. `count = 0` сейчас).
- Открыть `http://localhost:3000/`.
- В БД нет непрочитанных новостей (`count = 0`).

**Steps**:
1. На `/` проверить, что бейдж НЕ виден (`count = 0`).
2. **Через админку или прямой SQL** создать новую запись в `tbl_news` с `publish_at = NOW()`.
3. Подождать 45 секунд (1 polling tick) — polling обновит `count = 1`.
4. Бейдж «1» появляется, **запускается 10-секундный таймер**.
5. **НЕ кликать, НЕ навигировать** — просто наблюдать.
6. Через 10 секунд после появления бейджа → бейдж исчезает.
7. В DevTools Console:
   - `localStorage.getItem('km_news_last_seen_id')` → id новой новости (>= прежнего + 1).
   - `document.querySelector('.km-news-badge')` → `null`.

**Expected**:
- ✅ Бейдж автоматически исчезает через 10 сек после появления на `/`.
- ✅ `localStorage` обновлён (next polling вернёт `count = 0`).

### Scenario 13: Auto-read отменяется при уходе с `/`

**Prerequisite**:
- Залогиниться.
- На `/`, `count = 3` (например, открыть другую вкладку где нет polling, настроить localStorage).

**Steps**:
1. На `/` проверить, что бейдж «3» виден.
2. **Без ожидания 10 сек** (сразу после появления бейджа) кликнуть на ссылку «Закрома» (или навигировать на любой маршрут кроме `/` и `/news`).
3. Бейдж остаётся видимым (таймер отменён, но `count` не сброшен).
4. Подождать 45 сек polling — `count` может обновиться, но НЕ обнулится автоматически (auto-read на других страницах не работает).

**Expected**:
- ✅ Auto-read timer отменён при уходе с `/`.
- ✅ На других страницах бейдж не исчезает сам по себе (только через polling обновление или переход на `/news`).

### Scenario 14: Auto-read перезапускается при новых новостях

**Prerequisite**:
- Залогиниться.
- На `/`, `count = 3` (старые новости), auto-read таймер запущен (5 сек из 10).

**Steps**:
1. Через 5 сек после старта таймера (всё ещё на `/`) создать ещё 2 новости в `tbl_news`.
2. Подождать до следующего polling tick (≤ 45 сек от создания).
3. Polling обновит `count = 5`. Таймер должен **перезапуститься** на 10 сек с момента получения нового числа.
4. Через 10 сек от получения `count = 5` бейдж исчезает.

**Expected**:
- ✅ Таймер перезапускается при `count > 0 → newCount > oldCount` (новые новости поверх существующих).
- ✅ Бейдж исчезает через 10 сек от последнего обновления, а не от первого.

### Scenario 15: Возврат с `/news` на `/` — таймер не запускается

**Prerequisite**:
- Залогиниться.
- На `/`, `count = 3`.

**Steps**:
1. Кликнуть «Новости» → переход на `/news`, бейдж сразу исчезает (FR-015), `localStorage` обновлён.
2. Кликнуть «← Главная» (или браузерную back) → возврат на `/`.
3. На `/` проверить `count` — должен быть `0`.
4. Бейдж НЕ виден, таймер НЕ запускается (`count = 0`, нечего «прочитывать»).
5. В DevTools Network подождать 45 сек — следующий polling вернёт `count = 0`.

**Expected**:
- ✅ После возврата на `/` бейдж не появляется заново.
- ✅ Таймер не запускается (count === 0 condition).

## Validation checklist

После прохождения всех сценариев отметить в PR:

- [ ] Scenario 1 PASS (анонимный)
- [ ] Scenario 2 PASS (3 непрочитанных)
- [ ] Scenario 3 PASS (50 → «50+»)
- [ ] Scenario 4 PASS (silent reset для нового пользователя)
- [ ] Scenario 5 PASS (открытие `/news` сбрасывает бейдж)
- [ ] Scenario 6 PASS (polling приостановлен на `/player`)
- [ ] Scenario 7 PASS (нет floating bell)
- [ ] Scenario 8 PASS (backend error не сбрасывает)
- [ ] Scenario 9 PASS (a11y)
- [ ] Scenario 10 PASS (узкий экран)
- [ ] **Scenario 11 PASS (immediate reset на `/news`, US4 FR-015)**
- [ ] **Scenario 12 PASS (auto-read 10 сек на `/`, US4 FR-016)**
- [ ] **Scenario 13 PASS (auto-read отменён при уходе с `/`)**
- [ ] **Scenario 14 PASS (auto-read перезапускается при новых новостях)**
- [ ] **Scenario 15 PASS (возврат с `/news` не запускает таймер)**

## Failure modes & debug

| Симптом | Возможная причина | Где смотреть |
|---------|-------------------|--------------|
| Бейдж не появляется при `count > 0` | Composable не вызван в `AuthStatusWidget.setup()` | `AuthStatusWidget.vue` — `setup()` return |
| `count = 50+` для нового пользователя | Silent reset не сработал | `localStorage.km_news_last_seen_id` после загрузки — должно быть числом |
| Бейдж сбрасывается в 0 при network error | Нет `try/catch` в polling | `useNewsUnreadCount.js` — `poll()` |
| Polling дёргает `/since` на `/player` | Route watcher не останавливает таймер | `watch($route.name, ...)` блок |
| `aria-label` не объявляется screen reader-ом | `aria-label` не привязан реактивно | `:aria-label="ariaLabel"` в template |
| ESLint ругается на новый composable | Несоответствие стилю проекта | `npm run lint` в `karaoke-public/` |
| Backend возвращает `count > 50` | Race condition с TTL cache | `data-model.md` — defensive `count = Math.min(count, 50)` |
| **Бейдж не исчезает сразу при клике «Новости»** | `markRead()` не вызывается в route watcher | `useNewsUnreadCount.js` — `applyRouteState()` ветка `route.name === 'news'` |
| **Бейдж не исчезает через 10 сек на `/`** | Auto-read timer не запускается | `useNewsUnreadCount.js` — `watch(count, ...)` и `startAutoRead()` |
| **Бейдж исчезает на `/zakroma` (где нет новостей)** | `NEWS_SHOWN_ROUTES` содержит лишние пути | `useNewsUnreadCount.js` — `NEWS_SHOWN_ROUTES` const |
| **`markRead()` не обновляет `localStorage`** | `items.value` пуст (не было успешного polling) | Console: `useNewsUnreadCount().items.value.length` — должен быть > 0 |
