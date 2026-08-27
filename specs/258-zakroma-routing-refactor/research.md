# Research: Закрома — header-back-link из SongView + рефакторинг URL-routing

**Feature**: 258 — `specs/258-zakroma-routing-refactor`
**Date**: 2026-08-27
**Branch**: `258-zakroma-routing-refactor`

Phase 0 — исследование открытых вопросов и проверка технических предположений из спеки.

## RT-1 — Backend exposes author by NAME, not ID

**Context**: спека (assumptions (b), (h)) предполагает, что frontend может резолвить `authorId` из `authorTiles` (Vuex store). Проверка выявила противоречие:

- **`AuthorTilePublicDto`** (`karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/AuthorTilePublicDto.kt:27-38`) содержит **только**:
  - `author: String` (имя)
  - `authorPictureUrl: String`
  - `songCount: Long`
  - `isSpecialOrder: Boolean`
  - **НЕТ поля `id`** — это критично для варианта А с `:authorId`.

- **`PublicApiController.zakroma`** (`PublicApiController.kt:298-340`) принимает `author: String?` как query-param.
- **`PublicApiController.zakromaStream`** (`PublicApiController.kt:371-...`) тоже принимает `author: String?`.
- **`Author.loadList`** + `getAuthorByName(name)` (`Author.kt:276-288`) — есть, name → ID резолвинг работает.

**Implication**: для `:authorId` в URL нужен **либо** ID в DTO, **либо** name → ID резолвинг через отдельный endpoint, **либо** откат к `:authorName` (revert Q2).

**Decision**: **RT-1.A1** — добавить поле `id: Long` в `AuthorTilePublicDto`. Минимальное backend-изменение (1 поле + 1 параметр в `fromAuthorName`). Все остальные endpoints продолжают работать с `author` (по имени) — фронт сам выбирает, что передавать.

**Rationale**:
- Соответствует принятому решению Clarifications Q2 (`:authorId` числовой).
- Backend не получает новых ответственностей — только экспонирует уже существующее поле `tbl_authors.id`.
- Прямой mapping на Vuex `state.authorTiles[i].id`, без отдельного lookup-вызова.

**Alternatives considered**:
- **RT-1.A2** — отдельный endpoint `GET /api/public/authors/by-name/{name}` для name → ID резолвинга: добавляет сетевой запрос при каждом redirect; плохая UX при медленной сети. Отклонено.
- **RT-1.A3** — откат на `:authorName` (Cyrillic в URL → URL-encode): противоречит Clarifications Q2 = A. Отклонено.

**Implementation impact**:
- Backend: `AuthorTilePublicDto.kt` — добавить `val id: Long`, обновить `fromAuthorName` (принимать `id` параметром).
- Backend: `PublicApiController.kt:297-300` — `AuthorTilePublicDto.fromAuthorName(author = it, id = ..., songCount = ...)`.
- Backend: т.к. уже загружается `Song.loadListAuthors(...)` (список имён), нужно либо подгрузить `Song.loadList(whereArgs = mapOf("author" to authorName))` для получения ID, либо добавить новый helper `Author.loadIdsByNames(names: List<String>): Map<String, Long>` (raw SQL `SELECT id, author FROM tbl_authors WHERE author IN (...)`).

**Verification**:
- `curl http://localhost/api/public/authors-tiles | jq '.[0]'` → JSON содержит `id: <Long>` (было без `id`).

---

## RT-2 — Vue Router 4: пересоздание компонента при смене path vs query

**Context**: спека FR-A4 утверждает, что watcher из спеки 255 можно удалить, т.к. vue-router пересоздаёт компонент при смене path. Проверка.

**Findings**:

Vue Router 4 поведение (каноническая документация, версия проекта — см. RT-3):

- При навигации между маршрутами с **разными path** — компонент пересоздаётся (новый инстанс, `data()`, `setup()`, lifecycle hooks вызываются заново).
- При навигации между маршрутами с **одинаковым path, но разным query** — vue-router **переиспользует** тот же инстанс компонента. Вызываются только watchers на `$route` (или `route.query.*`).
- При навигации между маршрутами с **разными params** (даже при том же path-pattern) — компонент пересоздаётся. Это поведение контролируется через `:key="$route.fullPath"` на `<router-view>` или `router-view v-slot` для принудительного пересоздания.

**Implication для спеки 258**:
- `/zakroma` → `/zakroma/123` — **разные path**, компонент `ZakromaView` пересоздаётся → `data()` вызывается заново → `authorChosen = !!this.$route.params.authorId` инициализируется корректно.
- `/zakroma/123` → `/zakroma/456` — **разные path** (params в path), компонент пересоздаётся → state корректен.
- `/zakroma/123` → `/zakroma` — разные path, пересоздание.

**Decision**: **RT-2.A1** — watcher из спеки 255 (строки 542-554 в `ZakromaView.vue`) удаляется, как и требует FR-A4. Vue-router сам пересоздаёт компонент при смене path.

**Rationale**: документированное поведение Vue Router 4 + устраняет 13 строк хрупкого watcher-кода.

**Alternatives considered**:
- **RT-2.A2** — оставить watcher + добавить `:key="$route.fullPath"` на `<router-view>` — избыточно, watcher не нужен. Отклонено.
- **RT-2.A3** — оставить watcher «на всякий случай» — нарушает YAGNI, добавляет мёртвый код. Отклонено.

**Verification**:
- Открыть `/zakroma/123` → изменить URL на `/zakroma/456` (через `history.pushState` или router-link) → `data.authorChosen` должен стать true, `data.selectedAuthor` = '456' (новый ID → резолвится в имя через `authorTiles`).

---

## RT-3 — Vue Router 4: redirect с query-resolving

**Context**: спека FR-A2 требует redirect `/zakroma?author=X` → `/zakroma/:authorId`. Резолвинг имени X в ID требует доступа к Vuex store (который загружается асинхронно через `loadAuthorTiles`).

**Findings**:

Два подхода к redirect в Vue Router 4:

**A. Per-route `redirect` функция** (синхронная, вызывается при каждой навигации на этот route):

```js
{
  path: '/zakroma-author-legacy',
  redirect: () => {
    // Синхронно: возвращаем либо target path, либо другой route
    return '/zakroma'
  }
}
```

Проблема: на момент вызова redirect Vuex store может быть ещё не загружен (`loadAuthorTiles` async). Резолвинг имени → ID ненадёжен.

**B. Global `router.beforeEach` guard** (асинхронный, может ждать):

```js
router.beforeEach(async (to, from) => {
  if (to.path === '/zakroma' && to.query.author) {
    // Ждём загрузки tiles если нужно
    await store.dispatch('zakroma/loadAuthorTiles', 'main')
    const tile = store.state.zakroma.authorTiles.find(t => t.author === to.query.author)
    if (tile) return { path: `/zakroma/${tile.id}` }
    return { path: '/zakroma' } // fallback
  }
})
```

Преимущества: полный контроль, async-await, fallback при ошибке.
Недостатки: глобальный guard — больше кода, чем per-route; нужно убедиться, что не ломает другие guard'ы.

**C. Отдельный transient route + watcher в `ZakromaView`** (вариант для раздумий):

```js
{ path: '/zakroma-redirect/:author', component: { template: '<div></div>' }, beforeEnter: ... }
```

Усложнение. Не рекомендуется.

**Decision**: **RT-3.B** — global `router.beforeEach` guard для legacy URL. Размещается в `router/index.js` после `createRouter(...)`. Guard:
1. Если `to.path === '/zakroma'` и `to.query.author` есть → пытается резолвить через Vuex `state.zakroma.authorTiles`.
2. Если tiles пустые → `await store.dispatch('zakroma/loadAuthorTiles', 'main')` (дедуп 30 сек — избегаем двойного запроса).
3. Если резолвинг успешен → return `{ path: `/zakroma/${id}`, replace: true }` (replace, чтобы не плодить историю).
4. Если резолвинг провалился (автор не найден, удалён) → return `{ path: '/zakroma' }` (показываем тайлы с уведомлением через `notify()` из `App.vue`).
5. Если `to.path === '/zakroma'` и `to.query.specialBucket === 'true'` → return `{ path: '/zakroma/special-bucket', replace: true }`.

**Rationale**:
- Async-await решает проблему «tiles ещё не загружены».
- Дедуп из `loadAuthorTiles` (30 сек) защищает от лишних HTTP-запросов.
- `replace: true` — пользователь не видит промежуточный URL в истории браузера.
- Fallback на `/zakroma` (тайлы) при broken referrer — нет 404.

**Alternatives considered**:
- **RT-3.A** (per-route redirect) — синхронно, не дождётся Vuex. Отклонено.
- **RT-3.D** — резолвинг на стороне `ZakromaView` через `beforeRouteEnter` — загромождает view. Отклонено.

---

## RT-4 — Имя vs ID: где делать финальный fallback для отображения

**Context**: после того как пользователь на `/zakroma/:authorId`, view должен показать имя автора (для `<title>`, header, breadcrumb). Где брать?

**Findings**:
- `state.authorTiles` уже содержит `id` (после RT-1) и `author` (имя).
- `state.zakroma[]` (для конкретного автора) тоже содержит `author: String` (поле модели).
- Резолвинг через `authorTiles.find(t => t.id === authorId)` — O(n), но `n ≤ ~50` (число авторов на публичной странице).

**Decision**: **RT-4.A** — в `ZakromaView` добавить computed `currentAuthorName(authorId)`, который ищет в `state.authorTiles`. Если не найдено (edge case: автор удалён после клика) → fallback на `state.zakroma[0]?.author || ''`.

**Rationale**: минимальные изменения, используем уже загруженные данные.

---

## RT-5 — `SpecialBucketView.vue` или переиспользовать `ZakromaView`?

**Context**: спека FR-A6 выносит спец-корзину в `/zakroma/special-bucket`. Два варианта реализации.

**Findings**:
- `ZakromaView` уже умеет рендерить спец-корзину через `data.specialBucketShown = true` (см. спеку 254).
- Логика спец-корзины тесно переплетена с общей view (тот же header, те же store actions).
- Создание отдельного `SpecialBucketView.vue` означает дублирование: header, AppHeader, фильтр по альбомам, observer'ы на `specialBucket`, и т.д.

**Decision**: **RT-5.A** — переиспользовать `ZakromaView` для `/zakroma/special-bucket`. Переход:
- В `data()` инициализировать `specialBucketShown: true` если `route.path === '/zakroma/special-bucket'`.
- Удалить логику `?specialBucket=true` из route `/zakroma` (она перенесена в `/zakroma/special-bucket`).
- Спец-корзина рендерится через существующий template (v-if на `specialBucketShown`).

**Rationale**:
- Меньше дублирования.
- `ZakromaView` уже имеет полный набор логики (loadSpecialBucket, фильтры, observer'ы).
- `specialBucket` остаётся в Vuex store, отдельный view не нужен.

**Alternatives considered**:
- **RT-5.B** — отдельный `SpecialBucketView.vue`: чисто архитектурно, но ~200 строк дублирования. Отклонено.

---

## RT-6 — Vue Router 4: param с цифрами только vs с любой строкой

**Context**: спека FR-A1 определяет `:authorId` как `Long, regex \d+`. Проверим, как Vue Router 4 обрабатывает `path: '/zakroma/:authorId'`.

**Findings**:
- По умолчанию `:authorId` принимает любую строку, не соответствующую `/`. Vue-router НЕ валидирует тип.
- Для валидации можно использовать `path: '/zakroma/:authorId(\\d+)'` — синтаксис regex в path.
- Альтернатива: в компоненте `data() { return { authorId: Number(this.$route.params.authorId) || 0 } }` и проверка `if (!authorId) { redirect }`.

**Decision**: **RT-6.A** — использовать regex в path: `path: '/zakroma/:authorId(\\d+)'`. Vue-router сам отклонит невалидные URL (404). Дополнительной проверки в компоненте не нужно.

**Rationale**:
- Декларативная валидация на уровне маршрута — чище, чем в компоненте.
- 404 на `/zakroma/abc` — корректное поведение (нельзя показать «автора с невалидным ID»).

**Verification**:
- `/zakroma/123` → matched route `zakroma-author`, render `ZakromaView`.
- `/zakroma/abc` → 404 (Vue-router No match).

---

## RT-7 — Совместимость с `auth.is_special_order` для спец-корзины

**Context**: спец-корзина (`/zakroma/special-bucket`) — это «виртуальная плашка», которая показывает всех авторов с `is_special_order=true` одним запросом. Проверим, что вынос в отдельный route не ломает эту логику.

**Findings**:
- `Zakroma.getZakromaBySpecialOrder(database, ...)` (см. `Zakroma.kt`) — работает без параметра `author`.
- `store.action('loadSpecialBucket')` уже существует и вызывается в `ZakromaView.mounted()`.
- В новом маршруте `/zakroma/special-bucket` нужно убедиться, что `loadSpecialBucket()` вызывается, `data.specialBucketShown = true` и `data.authorChosen = false`.

**Decision**: **RT-7.A** — `ZakromaView` в `mounted()` проверяет:
- `if (route.path === '/zakroma/special-bucket') { data.specialBucketShown = true }`
- Стандартный `loadSpecialBucket()` уже вызывается безусловно.

**Rationale**: совместимо с текущей логикой, минимальные правки.

---

## RT-8 — Поведение watcher'а на `route.path` для очистки `songFilter`

**Context**: спека 255 показала, что при переходе от автора к тайлам нужно сбрасывать `songFilter`. После рефакторинга — vue-router сам пересоздаёт компонент при смене path → `data()` вызывается заново → `songFilter = ''` по умолчанию. Спека FR-A4 говорит «удалить watcher».

**Verification**:
- `data()` содержит `songFilter: ''` (ZakromaView.vue:413).
- При пересоздании компонента (смена path) `data()` вызывается заново → `songFilter` сбрасывается автоматически.
- Watcher был нужен ТОЛЬКО потому, что vue-router переиспользовал инстанс при смене query. После рефакторинга — query больше не управляет состоянием view → watcher не нужен.

**Decision**: **RT-8.A** — watcher из спеки 255 удаляется полностью. `data.songFilter = ''` по умолчанию.

**Rationale**: естественная инициализация через `data()`, без хрупких watcher'ов.

---

## Сводка решений

| ID       | Decision | Impact |
|----------|----------|--------|
| RT-1.A1  | Добавить `id: Long` в `AuthorTilePublicDto` | 1 файл бэка, 1 файл фронта |
| RT-2.A1  | Удалить watcher из спеки 255 | ZakromaView.vue, -13 строк |
| RT-3.B   | Global `router.beforeEach` guard для legacy URL | router/index.js, +~30 строк |
| RT-4.A   | Computed `currentAuthorName(authorId)` через `authorTiles` | ZakromaView.vue, +5 строк |
| RT-5.A   | Переиспользовать `ZakromaView` для `/zakroma/special-bucket` | ZakromaView.vue, ~5 строк правок |
| RT-6.A   | Regex `(\\d+)` в path для `:authorId` | router/index.js |
| RT-7.A   | В `mounted()` ветка для `/zakroma/special-bucket` | ZakromaView.vue, +3 строки |
| RT-8.A   | Полное удаление watcher'а из спеки 255 | ZakromaView.vue |

## Открытые вопросы после Phase 0

**Нет**. Все 8 вопросов (RT-1..RT-8) разрешены.
