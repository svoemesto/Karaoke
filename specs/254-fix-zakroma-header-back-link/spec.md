# Feature Specification: Закрома — header-back-link «К списку авторов» вместо «Главная» + убрать дублирующую in-page кнопку

**Feature Branch**: `254-fix-zakroma-header-back-link`

**Created**: 2026-08-27

**Status**: Draft

**Input**: User description: «Находясь в закромах автора надо чтобы в шапке слева вместо "Главная" ссылка была на "К списку авторов", а со страницы эту ссылку убрать».

## User Scenarios & Testing *(mandatory)*

### User Story 1 — header-back-link ведёт на «К списку авторов», когда открыты Закрома конкретного автора (Priority: P1)

Посетитель открывает `/zakroma?author=Машина Времени` (конкретный автор). В шапке сайта (`.km-header`) слева, в слоте `left` (вместо текущей ссылки «← Главная», ведущей на `/`), отображается ссылка **«← К списку авторов»**, ведущая на `/zakroma` без query-параметра `author`. Клик на неё сбрасывает выбор автора и возвращает к сетке тайлов авторов.

**Why this priority**: текущее поведение вводит в заблуждение — ссылка «← Главная» ведёт на главную страницу сайта, тогда как логически пользователь ожидает вернуться **к списку авторов** (контекстно — на уровень выше в текущем flow). Также эта ссылка дублируется в теле страницы (`.km-back-btn` блок, ~строка 100-115 в `ZakromaView.vue`) — избыточно, нарушает принцип «один логический путь, одна точка входа/выхода».

**Independent Test**: открыть `/zakroma?author=Машина Времени` в браузере, визуально проверить:
1. Слева в шапке видна ссылка с текстом **«← К списку авторов»** (НЕ «← Главная»).
2. Клик на неё → URL меняется на `/zakroma` (без query), открывается сетка тайлов авторов, выбранный автор сбрасывается.
3. В теле страницы (под `<AppHeader>` и над `.km-author-header-sticky`) **нет** in-page кнопки «← К списку авторов».

**Acceptance Scenarios**:

1. **Given** URL `/zakroma?author=Машина Времени`, **When** страница загружена, **Then** в `.km-header` слева виден `<RouterLink class="km-back">` с текстом «← К списку авторов», ссылающийся на `/zakroma` (без query).
2. **Given** пользователь на `/zakroma?author=X` кликает на «← К списку авторов», **When** vue-router отрабатывает переход, **Then** URL становится `/zakroma`; `authorChosen` сбрасывается в `false`; сетка тайлов авторов отрисовывается; header-back-link скрывается (см. US2).
3. **Given** URL `/zakroma?author=Кино`, **When** страница в режиме скролла (прокручена на 800 px), **Then** header-back-link «← К списку авторов» виден (т.к. AppHeader sticky на `top: 0`, спека 250) вместе с обёрткой `.km-author-header-sticky` под ним (спека 252 + 253).
4. **Given** URL `/zakroma?author=X` (любой валидный автор), **When** пользователь нажимает «← К списку авторов», **Then** НЕТ редиректа на главную (`/`); URL `localhost/zakroma?author=X` → клик → `localhost/zakroma` (без warning о "missing route", без смены origin).

---

### User Story 2 — header-back-link скрыт на странице выбора автора (Priority: P1)

Когда посетитель находится на `/zakroma` **без** query-параметра `author` (т.е. на экране с сеткой тайлов авторов и спец-плашкой «Отдельные песни разных авторов»), header-back-link **не отображается**. Левый слот `.km-header` пустой (или содержит только логотип — логотип справа, так что ничего не мешает).

**Why this priority**: без автора пользователь находится **на верхнем уровне** раздела «Закрома» — нет никакого «более высокого» раздела в контексте Закромов, куда можно вернуться. Ссылка «← Главная» в этой ситуации выглядит лишней и перегружает интерфейс; UX-смысла нет (главная сайта уже доступна по клику на логотип).

**Independent Test**: открыть `/zakroma` (без query), визуально:
1. В `.km-header` слева пусто (нет `<RouterLink class="km-back">`).
2. Логотип справа работает как обычно (ведёт на `/`).
3. Сетка тайлов авторов + спец-плашка отображаются.

**Acceptance Scenarios**:

1. **Given** URL `/zakroma` без query, **When** страница загружена, **Then** в `<RouterLink v-if="back">` (AppHeader:6) условие `back` ложно — никакого `<a>` слева в шапке нет.
2. **Given** пользователь на `/zakroma` (без автора) хочет попасть на главную, **When** он кликает на логотип (`.km-logo-link`), **Then** URL становится `/`, открывается главная.
3. **Given** пользователь на `/zakroma` (без автора) использует клавиатуру (`Tab`), **When** фокус проходит по шапке, **Then** слева нет tab-stop для скрытой back-ссылки; фокус сразу прыгает на правый слот (profile-link / auth-widget / theme-toggle / logo).

---

### User Story 3 — in-page `.km-back-btn` удалён (Priority: P2)

Дублирующая кнопка «← К списку авторов» в теле страницы (`ZakromaView.vue:100-115`, два `<button class="km-back-btn">` для обычного автора и спец-корзины) **удаляется** — её функцию выполняет header-back-link (US1). При клике на header-back-link тот же `backToAuthors()` метод сбрасывает выбранного автора; дополнительной логики не нужно.

**Why this priority**: устранение дублирования улучшает UX (один путь вместо двух) и убирает лишний ~30 строк JSX/template. Mobile-first — на телефоне header-sticky всегда виден, а `.km-back-btn` занимал место в начале контента.

**Independent Test**: открыть `/zakroma?author=X`, в DevTools-Console:
```js
const btns = document.querySelectorAll('.km-back-btn')
console.log(btns.length, btns.map(b => b.textContent.trim()))
```
**Ожидание**: `btns.length === 0` (пустой массив). Если есть — спека не выполнена.

**Acceptance Scenarios**:

1. **Given** URL `/zakroma?author=X` (`authorChosen=true`), **When** страница загружена, **Then** в DOM нет ни одного `<button class="km-back-btn">` с текстом «К списку авторов».
2. **Given** URL `/zakroma?specialBucket=true` (`specialBucketShown=true`), **When** страница загружена, **Then** нет ни одного `.km-back-btn` в DOM (раньше был второй button для спец-режима).
3. **Given** ни одного `.km-back-btn` в DOM, **When** пользователь хочет сбросить выбор, **Then** использует header-back-link «← К списку авторов» → клик вызывает `backToAuthors()` (метод сохранён в `methods`) → URL `/zakroma`, `authorChosen=false`, `specialBucketShown=false`, `songFilter=''`.

---

### User Story 4 — переходы между состояниями сохраняют корректный UI (Priority: P3)

Пользователь ходит туда-обратно между states: `/` → `/zakroma` → `/zakroma?author=X` → `/zakroma` (через header-back) → `/zakroma?author=Y` (выбор другого автора) → `/` (по логотипу). На каждом переходе header-back-link правильно видим/скрыт; in-page кнопок нет; спец-корзина (`?specialBucket=true`) также корректно скрывает header-back-link (т.к. это её «внутренний» state — обратно к авторам можно через «← К списку авторов»).

**Why this priority**: P3 — регрессия-проверка. Без неё фикс мог бы сломать edge-case спец-режима.

**Independent Test**: повторить flow в браузере, на каждом шаге проверить state (через DOM-инспектор или Visual).

**Acceptance Scenarios**:

1. **Given** пользователь проходит full flow: `/` → `/zakroma` → клик автора → `/zakroma?author=X` → клик «← К списку авторов» → `/zakroma` → клик спец-плашки → `/zakroma?specialBucket=true` → клик «← К списку авторов» (в шапке) → `/zakroma`, **When** каждый переход завершён, **Then** header-back-link правильно видим на `/zakroma?author=X` и `/zakroma?specialBucket=true`, скрыт на `/zakroma` (без query) и `/`.
2. **Given** пользователь на `/zakroma?author=X`, **When** он вручную правит URL на `/zakroma?author=` (пустое значение), **Then** `authorChosen` остаётся `false` (т.к. `!!this.$route.query.author` → false для пустой строки — НО! Vue-router сохраняет `author=''` как query-параметр, и `!!'' === false`, так что должно сработать корректно). Если сработает неправильно — добавить `.trim()` к `this.$route.query.author || ''`.
3. **Given** пользователь на `/zakroma?author=X`, **When** браузер нажимает «назад» (history.back), **Then** возвращается на предыдущую страницу (например, `/zakroma` до выбора автора или другую страницу), НЕ на header-back ссылки — это history браузера, не имеет отношения к фиче.

---

## Requirements *(mandatory)*

### Functional Requirements

#### A. Динамический `:back` prop для `AppHeader` в `ZakromaView.vue`

- **FR-001**: В `karaoke-public/src/views/ZakromaView.vue` заменить статический
  ```vue
  <AppHeader :back="{ to: '/', label: '← Главная' }" />
  ```
  на динамически вычисляемое значение:
  ```vue
  <AppHeader :back="zakromaHeaderBack" />
  ```
  где `zakromaHeaderBack` — это `computed`-property, возвращающее:
  - `null` (или `undefined`) — когда `authorChosen === false` И `specialBucketShown === false` (т.е. пользователь на странице выбора автора — header-back-link **скрыт**, как требует US2);
  - `{ to: '/zakroma', label: '← К списку авторов' }` — когда `authorChosen === true` ИЛИ `specialBucketShown === true` (т.е. на конкретном авторе или в спец-корзине — header-back-link ведёт на `/zakroma` без query, как требует US1).

- **FR-002**: Значение `zakromaHeaderBack` MUST учитывать оба состояния — обычный автор (`authorChosen`) И спец-корзина (`specialBucketShown`). Логика:
  ```js
  zakromaHeaderBack() {
    if (this.authorChosen || this.specialBucketShown) {
      return { to: '/zakroma', label: '← К списку авторов' }
    }
    return null
  }
  ```

- **FR-003**: Back-ссылка MUST указывать именно на `/zakroma` БЕЗ query-параметров. Поле `query` в объекте back НЕ передаётся (Vue-router по умолчанию переходит на path с пустым query — эквивалентно `?` или без `?`).

#### B. Удаление in-page `.km-back-btn` кнопок

- **FR-004**: В `ZakromaView.vue:99-115` удалить ОБА `<button class="km-back-btn">` элемента:
  - первый (с `v-if="authorChosen && !isSpecialBucketSelected"`) — для обычного автора;
  - второй (с `v-if="isSpecialBucketSelected"`) — для спец-режима.

- **FR-005**: Метод `backToAuthors()` (определён в `methods:` секции, ~строка 717-723) НЕ удаляется — он вызывается теперь из header-back-link автоматически через Vue-router (через `RouterLink :to="/zakroma"`). Если в дальнейшем понадобится программная навигация (например, из других частей UI), метод останется доступен.

- **FR-006**: Стили `.km-back-btn` в scoped CSS секции (`ZakromaView.vue:959-980`) — удаляются за отсутствием использования. Проверить, что нигде в проекте (по `grep -r '\.km-back-btn' karaoke-public/src`) больше нет ссылок на этот класс, и удалить стили.

#### C. Сохранение поведения и регрессии

- **FR-007**: Header-back-link скрытие НЕ ДОЛЖНО ломать другие view (AuthorPlaylistView, SongView, SearchView, AccountView и т.д.). Эти view передают собственные `:back` props или используют `<slot name="left">` — наш фикс НЕ затрагивает AppHeader API, только данные конкретного view (`ZakromaView.vue`).

- **FR-008**: Скролл, sticky-поведение (спека 250, 252, 253) — никаких регрессий. AppHeader sticky на `top: 0`, обёртка `.km-author-header-sticky` sticky на `top: var(...)` / `top: 53px/49px/46px` — всё сохранено.

- **FR-009**: Никаких изменений в backend, БД, Vuex, composables, API. Только template + computed + scoped-CSS в одном файле.

### Key Entities

Не применимо — фикс чисто UI-template, без data-моделей.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: На `/zakrama?author=Машина Времени` в DOM присутствует ровно **один** `<a class="km-back">` (или `<RouterLink>` отрендеренный как `<a>`) с текстом «К списку авторов», атрибутом `href="/zakroma"` (без `?author=...`).
- **SC-002**: На `/zakroma` (без query) в DOM **нет** `<a class="km-back">` слева в `.km-header-left` (`querySelectorAll('.km-header-left .km-back').length === 0`).
- **SC-003**: На `/zakroma?author=X` в DOM **нет** ни одного `<button class="km-back-btn">` (`document.querySelectorAll('.km-back-btn').length === 0`).
- **SC-004**: Клик на header-back-link «← К списку авторов» переводит URL на `/zakroma` (без query) — `document.location.pathname === '/zakroma'` после клика, без reload страницы (history.pushState).
- **SC-005**: На `/zakroma?specialBucket=true` header-back-link виден (т.к. `specialBucketShown === true`) и ведёт на `/zakroma` без query. После клика URL очищается и `specialBucketShown` сбрасывается.
- **SC-006**: `cd karaoke-public && npm run build` PASS.
- **SC-007**: `npm run lint` (`karaoke-public`) — 0 warnings.
- **SC-008**: `bash tools/check-eslint-baseline.sh karaoke-public` — 0 новых нарушений.
- **SC-009**: `./gradlew :karaoke-app:bootJar :karaoke-web:bootJar --parallel` — `:karaoke-web:bootJar UP-TO-DATE` (бэкенд не задет).

## Edge Cases *(include if feature involves data)*

- Что если пользователь на `/zakroma?author=` (пустое значение query)?
  → `author = ''`, `!!'' === false` в JavaScript → `authorChosen = false`. Header-back-link скрыт, in-page кнопок нет (тоже). Корректно.
- Что если пользователь на `/zakroma?specialBucket=true&author=X` (невалидная комбинация)?
  → `authorChosen = true` И `specialBucketShown = true`. Header-back-link виден с label «К списку авторов». После клика — оба state сбрасываются. Корректно.
- Что если AppHeader использует `<slot name="left">` вместо prop :back в каком-то view?
  → не наш кейс; спека не трогает slot-API AppHeader. Другие view не задеты.
- Что если backToAuthors() будет удалён кем-то в будущем?
  → Header-back-link всё равно работает (vue-router переходит на `/zakroma`, view-логика сама сбрасывает state через watcher'ы / mounted); in-page необходимость в методе отпадает.

## Assumptions

- (a) «К списку авторов» — корректный русский label для перехода от автора обратно к сетке тайлов. Альтернативы («← К выбору автора», «← Все авторы») рассматривались, но «К списку авторов» совпадает с текущим in-page текстом и интуитивно понятно.
- (b) Удаление in-page `.km-back-btn` — безопасное, т.к. функциональность полностью покрывается header-back-link + существующий `backToAuthors()`. Никаких side-effects (аналитика событий, focus-trap и т.п.) не используется на этих кнопках.
- (c) `AppHeader`'s API (`back`, `backRouteTo()`, computed `v-if="back"` rendering) достаточно для новой логики. Альтернативный подход (ввести `:hideBack` prop или всегда передавать `null` явно) переусложняет; текущий API поддерживает передачу `null`.
- (d) Header-back-link target `/zakroma` (НЕ `/`) — пользователь вернётся именно к сетке тайлов, а не на главную сайта. Это семантически правильнее: «уровень назад в разделе Закрома», а не «глобальная главная».
- (e) Спец-корзина (`?specialBucket=true`) — самостоятельный edge-case из спеки 008. После её активации header-back-link «← К списку авторов» корректно возвращает к `/zakroma` (НЕ к «Отдельные песни разных авторов»). Если потребуется иной путь для спец-корзины (например, «← Отдельные песни»), это ОТДЕЛЬНАЯ фича, не часть 254.
- (f) Скролл / sticky-поведение из спек 252 + 253 + 250 НЕ затронуты. Фикс только в `ZakromaView.vue` template + computed + scoped CSS.
- (g) Бэкенд не затрагивается (Конституция Principle II, V, VIII). DTO, БД, Vuex — без изменений.

## Что НЕ входит в эту спеку

- Изменение поведения в `AuthorPlaylistView.vue` или `SongView.vue` — у них другие `.back` props / slot'ы, не наш scope.
- Аналогичная правка `← К списку авторов` в спец-режиме при `?specialBucket=true` → разная формулировка («← Отдельные песни» или подобное). Out of scope, см. assumption (e).
- Изменение AppHeader API (новые props типа `hideBack`, `disableBackForRoute`) — overkill, текущий API поддерживает `null`.
- Аналитика/трекинг кликов на back-link — out of scope (бэкенд).
- i18n / мультиязычность (`← К списку авторов` сейчас захардкожен, как и весь интерфейс проекта) — out of scope.
