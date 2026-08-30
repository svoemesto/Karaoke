# Data Model: 276-fix-zakroma-authors-link

**Дата**: 2026-08-30
**Спека**: [spec.md](spec.md)

## Сущности

Эта фича — UI-only исправление в одном Vue-компоненте, не вводящее новых persisted-сущностей или API-контрактов. Ниже — описание локального state `ZakromaView`, который затрагивается фиксом.

### ZakromaView.data() — изменяемые поля (сбрасываются при возврате на `/zakroma`)

| Поле | Тип | Назначение | Сбрасывается в `backToAuthors()`? |
|------|-----|------------|-----------------------------------|
| `selectedAuthorId` | `String` (число в виде строки, пустая строка если нет) | ID выбранного автора из `$route.params.authorId` | Да → `''` |
| `selectedAuthor` | `String` | Имя выбранного автора (резолвится из `authorTiles` в `mounted()`) | Да → `''` |
| `authorChosen` | `Boolean` | Флаг: показывать таблицу песен автора или сетку тайлов | Да → `false` |
| `specialBucketShown` | `Boolean` | Флаг: режим «Отдельные песни разных авторов» | Да → `false` |
| `songFilter` | `String` | Быстрый фильтр по названию песни (клиентский, без запроса к бэку) | Да → `''` |

### ZakromaView.data() — НЕ изменяемые поля (личные предпочтения, persist в localStorage)

| Поле | Тип | Назначение | Почему НЕ сбрасывается |
|------|-----|------------|-------------------------|
| `albumDisplayMode` | `'continuous' \| 'grouped'` | Переключатель «Сквозной / По типам альбомов» | Личная настройка посетителя, persist в `localStorage['km-zakroma-album-mode']`. Применяется только когда `authorChosen === true`. На сетке тайлов (`authorChosen === false`) не используется — сброс не нужен. |
| `hiddenAlbumTypes` | `Set<string>` | Быстрый фильтр по типу альбома | Личная настройка, persist в `localStorage['km-zakroma-hidden-album-types']`. На сетке тайлов не используется. |

### Vuex store `zakroma` — затрагиваемые поля

| Поле | Тип | Назначение | Кто сбрасывает |
|------|-----|------------|----------------|
| `zakroma` | `Array<ZakromaPublicDto>` | Песни выбранного автора (стрим-результат) | `setZakroma([])` через `loadZakromaStream` (store-action:193-208) или явный abort |
| `specialBucket` | `Array<ZakromaPublicDto>` | Список спецзаказных авторов | Не сбрасывается — это общий для всего сайта кэш, нужен для тайла спец-корзины |
| `isStreaming` | `Boolean` | Идёт ли сейчас загрузка стрима | `setStreaming(false)` после завершения/отмены стрима |
| `streamProgress` | `Object` | `{ receivedCount, expectedCount }` | `setStreamProgress({ receivedCount: 0, expectedCount: 0 })` при старте нового стрима или при отмене |
| `streamError` | `string \| null` | Текст ошибки стрима | `setStreamError(null)` при отмене или при успешном старте |
| `lastLoadedTimestampByAuthor` | `Record<authorName, ts>` | Кэш «когда последний раз грузили этого автора» (30 сек dedup) | `setLastLoadedTimestamp({ author, ts: 0 })` при ошибке (см. `zakroma.js:259`). При `backToAuthors()` НЕ сбрасывается — это общий кэш, при повторном заходе на того же автора dedup сработает в его пользу (быстрее). |
| `authorTiles` | `Array<AuthorTileDto>` | Тайлы для сетки авторов | Не сбрасывается — это общий кэш для всех view, нужен при первом монтировании `ZakromaView` |

### Lifecycle / Transitions

```
        ┌─────────────────────────────────────────────────────────────┐
        │                       ZakromaView                            │
        │                  authorChosen / path                         │
        └─────────────────────────────────────────────────────────────┘
                ▲               ▲               ▲               ▲
                │               │               │               │
       path=/zakroma  path=/zakroma/N  path=/zakroma/special  watcher
       (no author)    (author chosen)   -bucket (special mode) на $route.path
                │               │               │               │
                │               │               └───────────────┘
                │               │         (сброс authorChosen, selectedAuthor, etc.)
                │               │
                │               └── loadZakromaStream({ author }) ──► Vuex zakroma store
                │                       (active stream + abort old)
                │
                └── show AuthorTiles.vue grid (сетка тайлов)
```

**Transitions**:

- **Path `/zakroma` → `/zakroma/:authorId`**: vue-router навигирует → `mounted()` срабатывает только при первом монтировании компонента. Для последующих переходов срабатывает watcher (если есть) или компонент переиспользуется. Сейчас watcher'а на `/zakroma → /zakroma/:authorId` нет (это работает корректно: пользователь только что выбрал автора из тайлов, `authorChosen = true` уже выставлен в обработчике клика по тайлу). **Не блокирует 276**.

- **Path `/zakroma/:authorId` → `/zakroma`**: vue-router навигирует → компонент переиспользуется, `data()` НЕ вызывается → баг. **Фикс 276**: watcher на `$route.path` ловит изменение → вызывает `backToAuthors()` → сбрасывает state → вызывает `$router.replace({ path: '/zakroma', query: {} })` (но path уже `/zakroma`, replace no-op, watcher не рекурсирует).

- **Path `/zakroma/special-bucket` → `/zakroma`**: то же, что выше. `backToAuthors()` сбрасывает и `specialBucketShown = false`. **Фикс покрывает US1 Scenario 2**.

- **Path `/zakroma` → `/zakroma/:authorId`** (обратное направление): vue-router навигирует → компонент переиспользуется → watcher на `$route.path` срабатывает → `backToAuthors()` сбрасывает state → компонент остаётся «голым». **ПРОБЛЕМА**: на этом переходе watcher тоже сработает и сбросит state, а потом `mounted()` НЕ вызовется (компонент уже смонтирован). Получим обратный баг: кликнули на тайл автора — URL `/zakroma/51`, но state пустой.

  **Решение**: watcher должен срабатывать только при ПЕРЕХОДЕ НА «таилы» (path === `/zakroma` без параметров), а не на ЛЮБОЕ изменение path. В watcher'е проверить: если новый path не содержит `authorId` и не является `/zakroma/special-bucket` → вызвать `backToAuthors()`. Иначе (новый path содержит `authorId`) → ничего не делать (или явно инициировать fetch, но это уже работает через `onAuthorSelect`).

## Связанные API endpoints (НЕ изменяются, для контекста)

| Endpoint | Описание | Когда вызывается |
|----------|----------|------------------|
| `GET /api/public/zakroma?author={name}` | Песни автора (старый query-based) | Только для legacy redirect в `router/index.js:149-163`. Активный код не использует. |
| `GET /api/public/authors?scope=main` | Список имён авторов для тайлов | `loadAuthorTiles('main')` в `mounted()` |
| `GET /api/public/authors-tiles` | Тайлы (id, name, picture, songCount) | `loadAuthorTiles` через store action (см. `karaoke-public/src/store/modules/zakroma.js`) |
| `GET /api/public/zakroma?specialBucket=true` | Спецзаказные авторы | `loadSpecialBucket` в `mounted()` |
| `GET /api/public/zakroma/stream?author={name}` (NDJSON) | Стрим песен автора | `loadZakromaStream` через `useZakromaStreamProgress` composable |

Все эти endpoints уже работают корректно. Никаких изменений бэкенда для 276 не требуется.
