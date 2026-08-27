# Feature Specification: Закрома — header-back-link из SongView возвращается на страницу автора + рефакторинг URL-routing

**Feature Branch**: `258-zakroma-routing-refactor`

**Created**: 2026-08-27

**Status**: Draft (выбран вариант A — path-segment `/zakroma/:authorId`; см. Clarifications ниже)

**Input**: User bug-report: «Если из закромов песен автора открыть какую-нибудь песню и потом в ней в шапке нажать "Назад", то открывается страница тайтлов авторов, а не списка песен автора, с которой был переход — это надо исправить. Вопрос — может быть имеет смысл рассмотреть вариант, когда `/zakroma` возвращают только тайтлы авторов, а для того чтобы перейти к песням автора надо перейти не по адресу `/zakroma?author=имя_автора`, а например по адресу `/zakroma/имя_автора` или `/zakroma/id_автора`. Или наоборот — `/zakroma?author=имя_автора` работают как и сейчас, а `/zakroma` сделать отдельно, например `/authors`. Что позитивного может дать такой подход?».

## Зачем эта спека

Багфикс «header-back ведёт в неправильное место» + архитектурный рефакторинг URL в одном документе, потому что:

1. Минимальный фикс (без рефакторинга) лечит симптом — передавать referrer из `ZakromaView` в `SongView` через query.
2. Рефакторинг URL лечит причину — устраняет неоднозначность «`/zakroma` — это тайлы или песни автора?» навсегда (выбран вариант A, см. Clarifications).

## Clarifications

### Session 2026-08-27

- Q: Какой архитектурный вариант фикса выбираем — `/zakroma/:authorId`, отдельный `/authors`, или минимальный фикс без рефакторинга URL? → A: **A** — `/zakroma` = тайлы, `/zakroma/:authorId` = песни автора. Устраняет корень проблемы спеки 255 (watcher на `query.author` больше не нужен); соответствует паттерну `/account/playlists/:id`; REST-style.
- Q: Как идентифицировать автора в path-segment — числовым `:authorId` или строковым `:authorName`? → A: **A** — `:authorId` (числовой, `Long`, соответствует `tbl_authors.id`). Чистый URL без URL-encode; резолвинг имени ↔ ID делается через уже загруженные `authorTiles` в Vuex store.
- Q: Спец-корзину (`?specialBucket=true`) оставить внутри `/zakroma?specialBucket=true` или вынести в отдельный route? → A: **B** — вынести в `/zakroma/special-bucket` (или отдельный `SpecialBucketView.vue`). `/zakroma` становится полностью чистым «тайтлы»; спец-корзина — отдельная сущность со своим route.

## Текущее поведение (as-is)

```
1. Пользователь на /zakroma?author=Машина%20Времени
2. Кликает на песню → RouterLink :to="{ path: '/song', query: { id: sett.id } }"
3. Браузер → /song?id=...   (без какой-либо информации об источнике)
4. SongView.vue:4 жёстко зашит:
     <AppHeader :back="{ to: '/zakroma', label: '← Назад' }" />
5. Клик «← Назад» → /zakroma   (сетка тайтлов, БЕЗ ?author=)
   ❌ Ожидание: /zakroma?author=Машина%20Времени  (продолжить просмотр автора)
```

**Корень проблемы**: `/song?id=Y` теряет контекст источника. URL `/song` не знает, откуда пришёл пользователь.

**Дополнительная неоднозначность `/zakroma`** (источник спеки 254, 255):

| URL                                  | Что отрисовывается                                  |
|--------------------------------------|------------------------------------------------------|
| `/zakroma`                           | Сетка тайтлов авторов (`authorChosen=false`)         |
| `/zakroma?author=Машина Времени`     | Список песен автора (`authorChosen=true`)            |
| `/zakroma?specialBucket=true`        | Спец-корзина «Отдельные песни разных авторов»        |

Vue-router считает это одним и тем же маршрутом → при навигации между ними компонент **не пересоздаётся** (баг спеки 255, починен watcher'ом, но хрупко).

## Целевое поведение (to-be — выбран вариант А)

**Финальная URL-схема**:

- `/zakroma` → **только** сетка тайтлов авторов.
- `/zakroma/:authorId` → песни конкретного автора (`:authorId` — числовой `Long`, см. Clarifications).
- `/zakroma/special-bucket` → спец-корзина «Отдельные песни разных авторов».
- `/song?id=Y&authorId=X` → страница песни с referrer-информацией об авторе (для корректного back-link).

**Back-link в SongView**:

- Если `route.query.authorId` есть → `{ name: 'zakroma-author', params: { authorId: X }, label: '← К песням автора' }`.
- Иначе fallback → `{ to: '/zakroma', label: '← В Закрома' }`.

**Поведение back-end** (URL → view):

| URL                              | View                          | Режим                       |
|----------------------------------|-------------------------------|------------------------------|
| `/zakroma`                       | `ZakromaView`                 | тайтлы (`authorChosen=false`) |
| `/zakroma/:authorId`             | `ZakromaView`                 | песни автора (`authorChosen=true`) |
| `/zakroma/special-bucket`        | `ZakromaView` или `SpecialBucketView` | спец-корзина (`specialBucketShown=true`) |
| `/zakroma?author=X`              | redirect → `/zakroma/:authorId` | обратная совместимость       |
| `/zakroma?specialBucket=true`    | redirect → `/zakroma/special-bucket` | обратная совместимость |

## User Scenarios & Testing *(mandatory)*

### User Story 1 — header-back-link из SongView возвращает на список песен автора (Priority: P1)

Посетитель на `/zakroma/:authorId` (например, `/zakroma/123`) кликает на любую песню → открывается `/song?id=Y&authorId=123`. В шапке `SongView` клик на «← Назад» возвращает на `/zakroma/123` (тот же URL, тот же автор, та же позиция скролла если возможно). Содержимое — список песен этого автора, не тайлы.

**Why this priority**: прямой баг-репорт пользователя, P1. UX-разрыв: после прослушивания песни приходится заново искать автора в сетке тайтлов и кликать его снова. Теряется позиция просмотра (какая песня была последней).

**Independent Test**: открыть `/zakroma/123` → кликнуть любую песню → на странице песни кликнуть «← Назад» → проверить URL = `/zakroma/123`, в DOM — список песен автора, не сетка тайтлов.

**Acceptance Scenarios**:

1. **Given** URL `/zakroma/123`, **When** пользователь кликает на песню, **Then** открывается `/song?id=Y&authorId=123` с referrer-информацией об авторе.
2. **Given** URL `/song?id=Y&authorId=123`, **When** пользователь кликает «← Назад» в шапке, **Then** URL становится `/zakroma/123`, в DOM — список песен автора 123.
3. **Given** URL `/song?id=Y` БЕЗ referrer (например, прямая ссылка из внешнего источника, из поиска, из истории), **When** пользователь кликает «← Назад», **Then** fallback на разумный target (`/zakroma` или главная).

---

### User Story 2 — referrer не теряется при share-link / deep-link (Priority: P2)

Пользователь копирует URL песни `/song?id=Y&authorId=123` и отправляет другу. Друг открывает ссылку. У него:
- песня отображается корректно (back-link остаётся рабочим и **ведёт на страницу песен автора 123**);
- back-link не ломается из-за отсутствия `$route.query.authorId` (валидация в SongView).

**Why this priority**: P2, потому что share-link — частый сценарий, но не блокер; если share ссылка не содержит `authorId`, fallback должен быть безопасным.

**Independent Test**: открыть `/song?id=Y&authorId=123` в новой вкладке (без истории переходов). Back-link корректно строится → `/zakroma/123`.

**Acceptance Scenarios**:

1. **Given** пользователь копирует URL `/song?id=Y&authorId=123`, **When** новый пользователь открывает ссылку, **Then** back-link строится по query `authorId` и ведёт на `/zakroma/123`.
2. **Given** URL `/song?id=Y` БЕЗ query, **When** страница загружается, **Then** back-link показывает разумный fallback (например, «← В Закрома» → `/zakroma`).

---

### User Story 3 — browser-back ведёт себя ожидаемо (Priority: P1)

Пользователь: `/zakroma/123` → клик песни → `/song?id=Y&authorId=123` → browser «←». Должен вернуться на `/zakroma/123` (страницу песен автора). Не на главную, не на тайлы.

**Why this priority**: P1 — browser-back в SPA обычно = «верни меня туда, откуда я пришёл». Это контракт браузера; его нарушение = регрессия базовой UX-гарантии.

**Independent Test**: открыть `/zakroma/123` → клик песни → `/song?id=Y&authorId=123` → browser back → URL = `/zakroma/123`, контент = песни автора, скролл-позиция восстановлена (vue-router scrollBehavior).

**Acceptance Scenarios**:

1. **Given** `/zakroma/123` → клик песни → `/song?id=Y&authorId=123`, **When** пользователь нажимает browser «←», **Then** URL = `/zakroma/123`.
2. **Given** прямая ссылка `/song?id=Y` (без referrer), **When** browser «←», **Then** URL = предыдущая страница браузерной истории (часто about:blank или новая вкладка → ничего не происходит), без ошибок.

---

### User Story 4 — рефакторинг URL не ломает существующие ссылки (Priority: P2)

После рефакторинга URL должны выполняться следующие условия:
- Старые ссылки `/zakroma?author=X` редиректятся на `/zakroma/:authorId` (FR-A2).
- Старые ссылки `/zakroma?specialBucket=true` редиректятся на `/zakroma/special-bucket` (FR-A7).
- Все RouterLink `to="/zakroma"` в проекте (без query) продолжают работать — рендерят тайтлы авторов.
- SEO/индексация: старые URL редиректятся с 301, новые индексируются.

**Why this priority**: P2 — после рефакторинга 1-2 недели пользователи могут кликать по старым ссылкам из чатов/закладок; резкий редирект на 404 будет регрессией.

**Independent Test**: `curl -I http://localhost/zakroma?author=Машина%20Времени` → 301/302 на `/zakroma/123`; `curl -I http://localhost/zakroma/123` → 200 + рендерится страница песен автора.

**Acceptance Scenarios**:

1. **Given** ссылка из чата `/zakroma?author=Машина Времени`, **When** пользователь открывает её, **Then** происходит redirect на `/zakroma/:authorId`, рендерится страница песен автора.
2. **Given** старая ссылка `/zakroma` (без query), **When** пользователь открывает её, **Then** рендерятся тайтлы авторов (без редиректа, без 404).
3. **Given** ссылка `/zakroma/special-bucket`, **When** пользователь открывает её, **Then** рендерится спец-корзина.
4. **Given** ссылка `/zakroma?specialBucket=true`, **When** пользователь открывает её, **Then** происходит redirect на `/zakroma/special-bucket`.

---

### User Story 5 — рефакторинг устраняет неоднозначность `/zakroma` (Priority: P3)

После рефакторинга URL `/zakroma` имеет ровно одно значение (тайтлы). Это:
- упрощает watcher'ы в `ZakromaView.vue` (FR-A4: watcher из спеки 255 удаляется);
- упрощает deep-link UX (URL = состояние);
- упрощает мониторинг/аналитику (нет «неявного» состояния через query).

**Why this priority**: P3 — это уже про качество кода и архитектуру, не про баг. Но для спеки 255 (state reset on back-nav) — корень проблемы именно в неоднозначности URL; рефакторинг её убирает.

**Independent Test**: `route('/zakroma')` всегда рендерит тайтлы; `route('/zakroma/123')` всегда песни автора 123; `route('/zakroma/special-bucket')` всегда спец-корзина. Не нужны watcher'ы на `query.author`.

**Acceptance Scenarios**:

1. **Given** URL `/zakroma`, **When** разработчик смотрит `ZakromaView.vue`, **Then** `data.authorChosen = false`, `data.specialBucketShown = false` (без watcher'ов).
2. **Given** vue-router переходит `/zakroma/123` → `/zakroma` (back), **Then** vue-router пересоздаёт компонент (другой path → другой matched route record), state сбрасывается естественно, watcher из спеки 255 не нужен.

---

## Edge Cases

- **Прямая ссылка на песню** (без referrer): back-link fallback на разумный target. Не 404, не сломанный href.
- **Песня, открытая из search** (`/filter`): referrer = search; back-link ведёт на `/filter?q=...`.
- **Песня, открытая из author-playlist** (`/author-playlist`): referrer = author-playlist; back-link ведёт туда.
- **Песня, открытая из subscription-return или history**: аналогично — referrer сохраняется.
- **Пользователь на `/song` нажимает F5** (без referrer в URL): back-link всё равно строится по `$route.query`, состояние страницы сохраняется в URL.
- **URL с broken referrer** (`?author=` пустое значение или `%00`): не крашить приложение; показать fallback «← В Закрома».
- **Cyrillic в URL** (`Машина Времени`): вариант А с path-segment требует URL-encode (`/zakroma/%D0%9C%D0%B0%D1%88%D0%B8%D0%BD%D0%B0%20%D0%92%D1%80%D0%B5%D0%BC%D0%B5%D0%BD%D0%B8`) — уродливо, но валидно; альтернатива — ID (`/zakroma/123`), более чистый URL.

## Requirements *(mandatory)*

### Функциональные требования (общие для всех вариантов)

- **FR-001**: При переходе с `/zakroma/:authorId` на любую песню, URL `/song` MUST содержать referrer-информацию об авторе (`&authorId=X` или эквивалент в query), позволяющую однозначно восстановить страницу-источник.
- **FR-002**: `SongView.vue` MUST строить back-link на основе referrer-информации в `$route.query` — если есть `authorId`, back ведёт на `/zakroma/:authorId`; иначе fallback (например, на `/zakroma` или главную).
- **FR-003**: Back-link MUST работать одинаково для клика в шапке, browser-back и programmatic `router.back()` (через vue-router history).
- **FR-004**: Никаких 404 / «Cannot read property» / undefined-ошибок при broken referrer (пустой `authorId`, несуществующий ID, спец-символы).
- **FR-005**: Никаких изменений в backend, БД, API. Изменения только в `karaoke-public/src/views/{ZakromaView,SongView}.vue` и `karaoke-public/src/router/index.js`.

### Функциональные требования (выбран вариант А)

- **FR-A1**: Route `/zakroma/:authorId` MUST рендерить `ZakromaView` в режиме «песни автора» (эквивалент текущего `/zakroma?author=X`). `:authorId` — числовой ID (`Long`, regex `\d+`), соответствующий `tbl_authors.id` в БД. Имя автора для отображения берётся из Vuex `authorTiles` по этому ID.
- **FR-A2**: Старые ссылки `/zakroma?author=X` MUST redirect'иться на `/zakroma/:authorId`, где `:authorId` — ID автора, резолвленный из имени X. Если имя не резолвится (автор удалён) — рендерить тайлы `/zakroma` с уведомлением.
- **FR-A3**: Route `/zakroma` (без path-segment) MUST рендерить **только** сетку тайтлов авторов. Никаких режимов «песни автора» / «спец-корзина» по этому URL.
- **FR-A4**: Watcher `'$route.query.author'` из спеки 255 MUST быть удалён (больше не нужен — URL однозначен; vue-router пересоздаёт компонент при смене path).
- **FR-A5**: `SongView.vue` (AppHeader back-link) MUST использовать `{ name: 'zakroma-author', params: { authorId: X } }` если referrer содержит `authorId`; иначе fallback `{ to: '/zakroma', label: '← В Закрома' }`.
- **FR-A6**: Спец-корзина (`?specialBucket=true`) MUST быть вынесена в отдельный route `/zakroma/special-bucket` (или собственный `SpecialBucketView.vue` со своим route). Внутри `/zakroma` спец-режима больше нет.
- **FR-A7**: Старая ссылка `/zakroma?specialBucket=true` MUST редиректиться на `/zakroma/special-bucket` (для обратной совместимости).

### Не-функциональные требования

- **NFR-001**: Никаких регрессий по ленте спек 252/253/250/254/255 (sticky-header, скролл, watcher'ы).
- **NFR-002**: `cd karaoke-public && npm run build` PASS.
- **NFR-003**: `npm run lint` (karaoke-public) — 0 новых warnings.
- **NFR-004**: `tools/check-eslint-baseline.sh karaoke-public` — 0 новых нарушений.
- **NFR-005**: Никаких изменений в backend (Конституция Principle II, V, VIII).

## Key Entities

- **Маршрут (route)**: связка `path + name + component + meta`. Затронуты `/zakroma`, `/song`, возможно `/authors`, `/zakroma/:authorId`.
- **Referrer-информация**: часть URL-query, передаваемая между view через `RouterLink :to="{ query: { from, author } }"`. Не персистится (только в URL).
- **AppHeader back-prop** (из спеки 250): объект `{ to, label, query? }`, передаваемый из view в компонент шапки.

## Success Criteria *(mandatory)*

### Измеримые исходы

- **SC-001**: При переходе `/zakroma/:authorId` (например, `/zakroma/123`) → клик песни → клик «← Назад» в шапке → URL возвращается на `/zakroma/:authorId` (`/zakroma/123`), в DOM — список песен автора.
- **SC-002**: Browser-back с `/song?id=Y&authorId=X` ведёт на `/zakroma/:authorId` (`/zakroma/X`).
- **SC-003**: Прямая ссылка `/song?id=Y&authorId=X` (без истории) — back-link строится по query, корректно ведёт на `/zakroma/:authorId`.
- **SC-004**: Прямая ссылка `/song?id=Y` БЕЗ query — back-link показывает fallback (например, «← В Закрома» → `/zakroma`), не 404.
- **SC-005**: Все существующие RouterLink `to="/zakroma"` в проекте (CartView, HistoryView, AboutView, HomeView, EditorWorkView, SearchView и др.) продолжают работать без ошибок (рендерят тайлы авторов, как раньше `/zakroma` без query).
- **SC-006**: Старые ссылки `/zakroma?author=X` → редирект 301/302 на `/zakroma/:authorId` (где ID резолвится из имени X); если не резолвится — fallback на `/zakroma` с уведомлением.
- **SC-007**: `/zakroma` без path-segment рендерит **только** тайтлы, без режима «песни автора» и без спец-корзины. Открытие напрямую `/zakroma?author=X` работает через redirect (см. SC-006); `/zakroma?specialBucket=true` → редирект на `/zakroma/special-bucket`.
- **SC-008**: `cd karaoke-public && npm run build` PASS; `npm run lint` 0 warnings.
- **SC-009**: `tools/check-eslint-baseline.sh karaoke-public` — 0/0.

## Что позитивного даёт выбранный вариант (А)

### Почему выбран вариант А

1. **Устраняет неоднозначность URL на корню**. `/zakroma` = тайлы (одно значение), `/zakroma/:authorId` = песни (другое значение). Нет «неявного состояния через query».
2. **Естественный REST-style URL**. `/zakroma/123` читается как «закрома автора с id=123», как `/account/playlists/456` уже делает.
3. **Vue-router пересоздаёт компонент при смене path**. Watcher из спеки 255 больше не нужен — `data.authorChosen = !!this.$route.params.authorId` корректно пересчитывается. Исчезает целый класс багов «state не сбрасывается при навигации».
4. **Cyrillic-friendly URL при использовании ID**. `/zakroma/123` чище, чем `/zakroma?author=Машина%20Времени` (path-segment с name требовал бы `%D0%9C%D0%B0%D1%88%D0%B8%D0%BD%D0%B0...`).
5. **Глубокая ссылка = состояние**. Можно шарить `/zakroma/123` — получатель сразу попадает на ту же страницу, без двусмысленности «а что покажет `/zakroma`?».
6. **Back-link в SongView становится тривиальным**. `to: { name: 'zakroma-author', params: { authorId: X } }` — однозначная навигация на конкретную страницу.
7. **SEO-friendly**: поисковики видят `/zakroma/123` как «страница автора 123» (отдельная сущность), а не как «фильтр на /zakroma».

### Минусы варианта А (принятые риски)

1. **Ломает обратную совместимость**. Все существующие `/zakroma?author=X` ссылки (закладки, чаты, email) требуют redirect. **Решение**: 301-redirect на `/zakroma/:authorId` с резолвингом имени в ID (см. FR-A2).
2. **Больше рефакторинга** — затрагивает router, view, watcher'ы (3-4 файла).
3. **Сложнее со спец-корзиной** — куда положить `?specialBucket=true`? **Решение**: оставить на этом этапе как есть внутри `/zakroma?specialBucket=true`; вынести в отдельный route — отдельная задача (см. Open Questions).

### Почему отклонены варианты Б и М

**Вариант Б** (отдельный `/authors`): не устраняет корень проблемы — watcher 255 остаётся нужен, неоднозначность `/zakroma` остаётся, плюс два «входа» в каталог (`/authors` и legacy `/zakroma`) создают путаницу.

**Вариант М** (минимальный фикс): не устраняет корень, накапливает техдолг. Используется как fallback, если вариант А окажется слишком инвазивным на этапе planning.

## Assumptions

- (a) Вариант А затрагивает только frontend (router, view, watcher'ы), не backend. Никаких изменений в `karaoke-web/controllers/ZakromaController.kt` или DTO.
- (b) Для `:authorId` используется существующий `tbl_authors.id` (Long, regex `\d+`) — не вводим отдельный slug или name в path-segment. Резолвинг ID ↔ имя — через Vuex `authorTiles` (см. assumption (h)). Если у сущности нет ID (например, для спец-корзины) — отдельный path-segment (`/zakroma/special-bucket`, см. Q3).
- (c) Старые ссылки `/zakroma?author=X` обрабатываются через router redirect — имя автора резолвится в ID на стороне frontend (через уже загруженные `authorTiles` в Vuex, см. assumption (h)).
- (d) Спец-корзина выносится в отдельный route `/zakroma/special-bucket` (см. FR-A6). Старая ссылка `/zakroma?specialBucket=true` редиректится (см. FR-A7). Загрузка данных спец-корзины остаётся через `loadSpecialBucket()` Vuex action — изменений в backend/store нет.
- (e) `SongView.vue` — единственная страница песни (`/song?id=...`), других страниц с контекстом «песня» нет (player — отдельный route, share — отдельный).
- (f) Back-link в SongView — это визуальный помощник, **не** единственный способ навигации. Browser-back и прямой переход через меню работают независимо.
- (g) Изменения не затрагивают Vuex store (`zakroma`, `songs`) и composables (`usePlaylistMembership`, `useCart`, `useAuth`). Только router/view.
- (h) `authorTiles` уже загружаются в `ZakromaView.mounted()` через `loadAuthorTiles('main')` → Vuex store содержит массив `{ id, name, ... }`. Резолвинг имени → ID для redirect можно делать через `getters`/`state` store или computed-property. Если тайлы ещё не загружены (например, при прямом переходе) — fallback на `GET /api/authors?name=X` (если есть) или просто показать тайлы с уведомлением «автор не найден».

## Что НЕ входит в эту спеку

- Изменение спек 254/255 (фикс уже применён, эта спека — следующий шаг).
- Рефакторинг `AuthorPlaylistView.vue` (отдельная страница `/author-playlist`, не `/zakroma`).
- Мультиязычность / i18n.
- SEO meta-теги для новых URL (если применимо).
- Тёмная/светлая тема (не затронуто).
- Аналитика кликов на back-link (отдельная задача).
- Изменение API бэкенда.

## Open Questions

### Резюме: все вопросы спецификации разрешены (см. секцию Clarifications)

Все три блокирующих вопроса (Q1: выбор архитектурного варианта, Q2: ID vs Name в path, Q3: судьба спец-корзины) разрешены в пользу **максимально чистой URL-схемы**:

- `/zakroma` → тайтлы авторов
- `/zakroma/:authorId` → песни автора (`Long` ID)
- `/zakroma/special-bucket` → спец-корзина

Это устраняет неоднозначность `/zakroma` полностью, делает URL = состояние и убирает необходимость в watcher'е из спеки 255.
- (c) **Удалить** (ломает все старые закладки — плохо).
