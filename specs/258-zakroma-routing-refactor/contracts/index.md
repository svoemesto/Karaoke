# Contracts: Закрома — header-back-link из SongView + рефакторинг URL-routing

**Feature**: 258 — `specs/258-zakroma-routing-refactor`
**Date**: 2026-08-27

Phase 1 — контракты изменённых и новых интерфейсов.

## C-1. Backend API: `GET /api/public/authors-tiles`

**Расположение**: `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt:253-301`

### C-1.1. Запрос

```
GET /api/public/authors-tiles?scope=main
```

**Query params**:
- `scope` (опциональный, default `main`) — `main` / `special` / `all`.

**Auth**: не требуется (публичный endpoint).

### C-1.2. Ответ (было)

```json
[
  {
    "author": "Машина Времени",
    "authorPictureUrl": "/minio/karaoke/...",
    "songCount": 234,
    "isSpecialOrder": false
  },
  ...
]
```

### C-1.3. Ответ (стало) — добавляется `id`

```json
[
  {
    "id": 42,
    "author": "Машина Времени",
    "authorPictureUrl": "/minio/karaoke/...",
    "songCount": 234,
    "isSpecialOrder": false
  },
  ...
]
```

**Изменение**: добавляется поле `id: Long` (BigInt) в начало каждого объекта. Jackson сериализует поля в порядке объявления в data class.

**Семантика `id`**:
- Соответствует `tbl_authors.id` в БД.
- Уникален в пределах всех авторов (PK).
- `0` недопустимо (см. data-model.md §3 — backend пропускает авторов без найденного ID).

**Обратная совместимость**: фронт, который не использует `id`, продолжает работать. Поле просто игнорируется.

**Cache-инвалидация**: in-memory cache для `/authors-tiles` (спека 248) уже инвалидируется при изменении `tbl_authors`. Никаких дополнительных действий не требуется.

---

## C-2. Backend API: `GET /api/public/zakroma` — контракт не меняется

**Расположение**: `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt:298-340`

### C-2.1. Запрос (без изменений)

```
GET /api/public/zakroma?author=Машина%20Времени
GET /api/public/zakroma?author=Машина%20Времени&specialBucket=true  (deprecated после спеки 258)
```

**Query params** (без изменений):
- `author` (опциональный) — имя автора.
- `specialBucket` (опциональный, default `false`) — флаг «спец-корзина».
- `anonId`, `referrer` — трекинг.

### C-2.2. Ответ — без изменений

`List<ZakromaPublicDto>` — DTO без изменений.

**Implication**: фронт продолжает передавать `author` как **имя**, а не ID. Рефакторинг URL-routing — это чисто frontend-изменение; backend API остаётся стабильным.

---

## C-3. Backend API: `GET /api/public/zakroma/stream` — контракт не меняется

**Расположение**: `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt:371-...`

### C-3.1. Запрос (без изменений)

```
GET /api/public/zakroma/stream?author=Машина%20Времени&expectedCount=234
```

**Query params** (без изменений):
- `author` — имя автора (НЕ ID).
- `expectedCount` — для синхронизации прогресс-счётчика.
- `anonId`, `referrer` — трекинг.

### C-3.2. Ответ — без изменений

NDJSON стрим сообщений: `meta` → `album` → `song` → `done` (без изменений).

**Implication**: после рефакторинга URL фронт резолвит `id → name` (через `authorTiles`) и передаёт в `?author=...` уже имя.

---

## C-4. Frontend: vue-router routes (`karaoke-public/src/router/index.js`)

### C-4.1. Новые маршруты

```js
const routes = [
  // ... существующие ...

  // Тайтлы авторов (только — без режима «песни автора»)
  { path: '/zakroma', name: 'zakroma', component: ZakromaView },

  // Песни конкретного автора (ID — только цифры)
  {
    path: '/zakroma/:authorId(\\d+)',
    name: 'zakroma-author',
    component: ZakromaView,
  },

  // Спец-корзина «Отдельные песни разных авторов»
  {
    path: '/zakroma/special-bucket',
    name: 'zakroma-special-bucket',
    component: ZakromaView,
  },

  // ... существующие ...
]
```

### C-4.2. Global `beforeEach` guard для legacy URL

```js
router.beforeEach(async (to, from) => {
  // Legacy: /zakroma?author=X → /zakroma/:authorId
  if (to.path === '/zakroma' && to.query.author) {
    const authorName = to.query.author
    const store = useStore()  // или импорт из '@/store'

    // 1. Убедиться, что тайлы загружены (dedup 30s внутри loadAuthorTiles)
    await store.dispatch('zakroma/loadAuthorTiles', 'main')

    // 2. Резолвинг имя → ID
    const tile = store.state.zakroma.authorTiles.find(
      (t) => t.author === authorName,
    )

    if (tile && tile.id) {
      return { path: `/zakroma/${tile.id}`, replace: true }
    }

    // Fallback: автор не найден → тайтлы + notify
    store.dispatch('app/showNotify', {
      message: `Автор «${authorName}» не найден`,
      kind: 'warning',
    })
    return { path: '/zakroma', replace: true }
  }

  // Legacy: /zakroma?specialBucket=true → /zakroma/special-bucket
  if (to.path === '/zakroma' && to.query.specialBucket === 'true') {
    return { path: '/zakroma/special-bucket', replace: true }
  }
})
```

**Примечание по `useStore`**: если в проекте используется Vuex 3 с Options API (как здесь), то store доступен через `import store from '@/store'` или через `this.$store` в компонентах. В `beforeEach` (вне компонента) — через прямой импорт:

```js
import store from '@/store'
```

### C-4.3. Scroll behavior — без изменений

Существующий `scrollBehavior(to, from, savedPosition)` в `router/index.js` остаётся как есть:
```js
scrollBehavior(to, from, savedPosition) {
  if (savedPosition) return savedPosition
  return { top: 0 }
}
```

При переходе `/zakroma/123` → `/song?id=Y` → browser-back — scroll-позиция восстанавливается через `savedPosition`.

---

## C-5. Frontend: `AppHeader.vue` — без изменений в API

**Расположение**: `karaoke-public/src/components/AppHeader.vue`

**API без изменений**:
```js
props: {
  back: { type: Object, default: null },  // { to, label, query? } ИЛИ { name, params, label }
  // ...
}
```

**Использование нового back-объекта в `ZakromaView.vue`**:

```js
zakromaHeaderBack() {
  if (this.authorChosen) {
    return { to: '/zakroma', label: '← К списку авторов' }
  }
  if (this.specialBucketShown) {
    return { to: '/zakroma', label: '← К списку авторов' }
  }
  return null  // на тайтлах — back скрыт
}
```

**Использование нового back-объекта в `SongView.vue`**:

```js
songHeaderBack() {
  const authorId = this.$route.query.authorId
  if (authorId && /^\d+$/.test(authorId)) {
    return {
      name: 'zakroma-author',       // → /zakroma/:authorId
      params: { authorId },         // → передаётся в path
      label: '← К песням автора',
    }
  }
  return { to: '/zakroma', label: '← В Закрома' }
}
```

`<AppHeader :back="songHeaderBack" />` сам разрезолвит `name + params` через `vue-router.resolve()`.

---

## C-6. Frontend: `ZakromaView.vue` — изменения в props/data/computed/mounted

### C-6.1. Data (новая инициализация)

```js
data() {
  const isSpecialBucketRoute = this.$route.path === '/zakroma/special-bucket'
  const authorIdParam = this.$route.params.authorId
  const hasAuthor = !!authorIdParam && /^\d+$/.test(authorIdParam)

  return {
    // ID из path (или '' если тайты/спец)
    selectedAuthorId: hasAuthor ? authorIdParam : '',
    // Имя — резолвится в mounted() через authorTiles
    selectedAuthor: '',
    // Режим
    authorChosen: hasAuthor,
    specialBucketShown: isSpecialBucketRoute,
    // ... остальное без изменений ...
    songFilter: '',
    subscribingSongId: null,
    subscribingSongName: '',
    albumDisplayMode: ...,
    hiddenAlbumTypes: ...,
  }
}
```

### C-6.2. Computed `zakromaHeaderBack` (без изменений по логике)

```js
zakromaHeaderBack() {
  if (this.authorChosen || this.specialBucketShown) {
    return { to: '/zakroma', label: '← К списку авторов' }
  }
  return null
}
```

### C-6.3. Mounted — изменения

```js
mounted() {
  // Тайлы нужны для резолвинга ID→name (на /zakroma/:authorId) И для сетки (на /zakroma)
  this.loadAuthorTiles('main')
  // Спец-каталог нужен для тайла «Отдельные песни» и для /zakroma/special-bucket
  this.loadSpecialBucket()

  // Резолвим имя автора по ID и стартуем стрим
  if (this.authorChosen && this.selectedAuthorId) {
    const tile = this.authorTiles.find(
      (t) => String(t.id) === String(this.selectedAuthorId),
    )
    if (tile) {
      this.selectedAuthor = tile.author
      this.loadZakromaStream({
        author: tile.author,
        expectedCount: tile.songCount || undefined,
      })
    } else {
      // Автор с таким ID не найден (удалён?) → сброс на тайты
      this.authorChosen = false
      this.selectedAuthorId = ''
      this.notify(`Автор с ID=${this.selectedAuthorId} не найден`, 'warning')
    }
  }
  // Для спец-корзины — loadSpecialBucket() уже загрузил данные, ничего больше не нужно
}
```

### C-6.4. Watcher (УДАЛЯЕТСЯ)

```js
// ❌ УДАЛЕНО (спека 258 FR-A4):
// '$route.query.author'(newAuthor) { ... }
// Причина: vue-router пересоздаёт компонент при смене path → data() вызывается заново →
// authorChosen/songFilter инициализируются корректно. Watcher был нужен только для смены query.
```

---

## C-7. Frontend: `SongView.vue` — изменения

### C-7.1. Computed `songHeaderBack`

```js
computed: {
  // ... существующие computed ...
  songHeaderBack() {
    const authorId = this.$route.query.authorId
    if (authorId && /^\d+$/.test(authorId)) {
      return {
        name: 'zakroma-author',
        params: { authorId },
        label: '← К песням автора',
      }
    }
    return { to: '/zakroma', label: '← В Закрома' }
  },
}
```

### C-7.2. Template — использование нового back

```vue
<!-- Было: -->
<AppHeader :back="{ to: '/zakroma', label: '← Назад' }" />

<!-- Стало: -->
<AppHeader :back="songHeaderBack" />
```

### C-7.3. RouterLink от ZakromaView — передача authorId в query

В `ZakromaView.vue:253-256` и `:301-304` (RouterLink на песни):

```vue
<!-- Было: -->
<RouterLink :to="{ path: '/song', query: { id: sett.id } }">
  {{ sett.songName }}
</RouterLink>

<!-- Стало: -->
<RouterLink
  :to="{
    path: '/song',
    query: { id: sett.id, authorId: selectedAuthorId },
  }"
>
  {{ sett.songName }}
</RouterLink>
```

`selectedAuthorId` уже есть в `data()` после рефакторинга.

---

## C-8. Не-изменяемые контракты

| Контракт | Расположение | Статус |
|----------|--------------|--------|
| `Author.loadList` | `Author.kt:266` | без изменений |
| `Author.getAuthorByName` | `Author.kt:276` | без изменений |
| `Zakroma.getZakroma(author: String)` | `Zakroma.kt` | без изменений |
| `Zakroma.getZakromaBySpecialOrder` | `Zakroma.kt` | без изменений |
| `Song.loadAuthorSongCounts` | `Song.kt` | без изменений |
| Vuex store `zakroma` (state, mutations, actions) | `zakroma.js` | без изменений, кроме имён полей в data |
| Vuex store `songs` | `songs.js` | без изменений |
| `usePlaylistMembership`, `useCart`, `useAuth` composables | `composables/` | без изменений |
| `App.vue`, `AppHeader.vue`, `AuthStatusWidget.vue` | `components/` | без изменений (кроме `App.vue` — добавляется `notify` если нужно) |
| Backend `PublicApiController.zakroma` | `PublicApiController.kt:298-340` | без изменений |
| Backend `PublicApiController.zakromaStream` | `PublicApiController.kt:371-...` | без изменений |
| Backend `PublicApiController.authorsTiles` (логика) | `PublicApiController.kt:253-301` | **изменяется** (см. C-1) |
| Backend `AuthorTilePublicDto` | `AuthorTilePublicDto.kt` | **изменяется** (добавляется `id`) |

---

## C-9. Error handling

| Сценарий | Поведение |
|----------|-----------|
| `/zakroma?author=X` где X не найден в `authorTiles` | redirect на `/zakroma` (тайты) + toast «Автор не найден» |
| `/zakroma/:authorId` где ID не найден в `authorTiles` | `authorChosen = false`, redirect не делаем, показываем пустой тайл «Автор не найден» |
| `/zakroma/:authorId` где ID = 0 (невалидный из БД) | то же что выше |
| `/zakroma/special-bucket` если `loadSpecialBucket` упал | пустая спец-корзина + error message |
| `?authorId` в SongView — невалидный (не цифры) | fallback `{ to: '/zakroma', label: '← В Закрома' }` |
| `?authorId` в SongView — цифры, но автор удалён | back-link кликабельный → vue-router переходит на `/zakroma/123` → mounted() ловит «не найден» → показывает тайты |

Все сценарии не приводят к 404 или «Cannot read property».
