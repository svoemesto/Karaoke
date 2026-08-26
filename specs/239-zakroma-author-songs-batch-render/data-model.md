# Data Model: Закрома автора — отрисовка списка песен без N×3 фоновых запросов

**Phase**: 1 | **Branch**: `239-zakroma-author-songs-batch-render` | **Date**: 2026-08-25

## Новые module-level stores (frontend)

### FavoriteSet

**Назначение**: module-level `Set<number>` — id песен в «Избранном» у текущего пользователя. Используется `FavoriteIcon` для отображения ★/☆.

**Источник**: `GET /api/public/account/favorites/ids` → `Long[]`.

**Расположение**: `karaoke-public/src/composables/usePlaylistMembership.js` (расширение существующего файла) или новый `useFavorites.js`.

**Структура**:
```js
const favoriteIds = reactive(new Set())  // module-level
async function loadFavoritesIds() {
  if (no token) { favoriteIds.clear(); return }
  if (already loaded in this PR) return
  const { status, body } = await fetchFavoritesIds()
  if (status === 200 && Array.isArray(body)) {
    favoriteIds.clear()
    body.forEach(id => favoriteIds.add(Number(id)))
  }
}
```

**Lifecycle**:
- Created: при первом вызове `loadFavoritesIds()` или при логине.
- Updated: при `toggleFavorite` (optimistic + confirm/revert).
- Cleared: при logout (token change → пустой Set).

**Reactivity**: Vue `reactive(new Set())` — Vue 3 поддерживает `Set`/`Map` через Proxy.

### PlaylistMembershipMap

**Назначение**: module-level `Map<number, number[]>` — для каждой песни id не-избранных плейлистов пользователя, в которых она состоит. Используется `PlaylistIcon`.

**Источник**: `GET /api/public/account/playlists/membership?ids={csv}` (существующий endpoint, изменение — вызывается 1 раз с полным списком).

**Расположение**: `karaoke-public/src/composables/usePlaylistMembership.js` (уже существует, расширяется).

**Структура**:
```js
const playlistMembership = reactive(new Map())  // module-level
async function loadMembershipFor(ids) {
  // Идемпотентный — вызывается один раз с полным списком при входе на страницу крупного автора.
  if (!ids.length) return
  const { status, body } = await fetchMembership(ids)
  if (status === 200 && body && body.items) {
    Object.entries(body.items).forEach(([songId, info]) => {
      playlistMembership.set(Number(songId), info.playlistIds || [])
    })
  }
}
```

### SubscriptionSet

**Назначение**: module-level `Set<number>` — id песен, на которые у пользователя активная персональная подписка. Используется `PlayerIcon` для логики «зелёный vs золотой».

**Источник**: `GET /api/public/account/song-subscriptions/ids` → `Long[]`.

**Расположение**: `karaoke-public/src/composables/useSongSubscriptions.js` (новый файл).

**Структура**:
```js
const subscriptionIds = reactive(new Set())  // module-level
let loaded = false
async function loadOnce(force = false) {
  if (loaded && !force) return
  if (no token) { subscriptionIds.clear(); loaded = false; return }
  const { status, body } = await fetchSongSubscriptionsIds()
  if (status === 200 && Array.isArray(body)) {
    subscriptionIds.clear()
    body.forEach(id => subscriptionIds.add(Number(id)))
    loaded = true
  }
}
```

**Lifecycle**:
- Created: при `loadOnce()` (вызывается из `App.vue` bootstrap или при логине).
- Updated: при `subscribeToSong` / `unsubscribeFromSong` (TODO: API не существует — actions через существующий `tbl_subscriptions` напрямую? Уточнить в tasks).
- Cleared: при logout.

## Расширение NDJSON-сообщения `zakromaStream`

**Файл**: `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/ZakromaStreamMessageDto.kt`

**Изменения**: добавить 3 поля в JSON:

```kotlin
data class ZakromaStreamSongDto(
    // существующие поля...
    val id: Long,
    val name: String,
    val author: String,
    val album: String,
    val year: Int,
    // ... (SongPublicDto fields)
    
    // NEW: готовность для иконки плеера
    val idStatus: Int,
    val isFreelyAvailableNow: Boolean,
    val contentReady: Boolean,
)
```

**Заполнение**: в `PublicApiController.zakromaStream` для каждой `song`:
```kotlin
val msg = ZakromaStreamSongDto(
    // ...
    idStatus = song.idStatus,
    isFreelyAvailableNow = song.isFreelyAvailableNow,
    contentReady = song.isContentReady,
)
```

## Новые endpoint'ы

### `GET /api/public/account/favorites/ids`

**Файл**: `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicPlaylistController.kt` (расширение).

**Request**: 
- Headers: `Authorization: Bearer <token>` (km_auth_token, опционально).
- Body: пусто.
- Query: нет.

**Response**:
```json
[123, 456, 789]
```
- Status: 200 OK + JSON body (Long[]).
- Status: 401 Unauthorized + пусто (если токен невалидный, фронт редиректит на login).

**SQL**:
```sql
SELECT id_song
FROM tbl_subscriptions
WHERE site_user_id = ? 
  AND scope = 'SONG' 
  AND status = 'PAID'
```

**Дополнительно**: для бесплатных пользователей с лимитом (FREE_FAVORITES_LIMIT=100) — возвращаем ВСЕ их избранные (лимит проверяется при добавлении, не при чтении). Endpoint приватный, отдаёт только свои.

### `GET /api/public/account/song-subscriptions/ids`

**Файл**: `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicPlaylistController.kt` (расширение).

**Request**: 
- Headers: `Authorization: Bearer <token>`.
- Body: пусто.
- Query: нет.

**Response**:
```json
[42, 43, 44]
```

**SQL**:
```sql
SELECT id_song
FROM tbl_subscriptions
WHERE site_user_id = ?
  AND status = 'PAID'  -- любой scope (для per-song подписок scope='SONG')
  AND id_song IS NOT NULL
```

**Важно**: фильтр по `scope='SONG'` ИЛИ `id_song IS NOT NULL` — подписки на сайт (`scope='SITE'`) не имеют `id_song`.

## Расширение BroadcastChannel

**Файл**: `karaoke-public/src/composables/usePlaylistMembership.js`.

**Текущее состояние**: `BroadcastChannel('km-favorites')` рассылает `{songId, favorited}`.

**Изменение**: добавить поле `type: 'favorited' | 'playlist' | 'subscription'` для дискриминации.

```js
favoritesChannel?.postMessage({ type: 'favorited', songId: String(id), favorited: val })
// при playlist-membership:
favoritesChannel?.postMessage({ type: 'playlist', songId: String(id), playlistIds: ids })
// при subscription:
favoritesChannel?.postMessage({ type: 'subscription', songId: String(id), subscribed: val })
```

**Принимающая сторона**:
```js
favoritesChannel.onmessage = (e) => {
  const { type, songId, ...rest } = e.data || {}
  switch (type) {
    case 'favorited': setFavorited(songId, !!rest.favorited); break
    case 'playlist': setPlaylistIds(songId, rest.playlistIds || []); break
    case 'subscription': setSubscribed(songId, !!rest.subscribed); break
  }
}
```

## Модификация `PlayerIcon` props

**Файл**: `karaoke-public/src/components/PlayerIcon.vue`.

**Текущие props**: `songId`, `watchState`, `contentReadyState`.

**Новые props** (старые остаются для backward-compat):
- `premium: Boolean` — пользователь премиум?
- `inAir: Boolean` — песня в эфире (вычислено на бэке).
- `flagFree: Boolean` — песня бесплатная (вычислено на бэке).
- `hasSubscription: Boolean` — есть подписка (из `SubscriptionSet`).

**Computed**:
```js
const contentReadyState = computed(() => props.contentReadyState === 'ready')  // boolean
const isActive = computed(() => contentReadyState.value && 
  (props.inAir || props.flagFree || props.premium || props.hasSubscription))
const isDisabled = computed(() => !contentReadyState.value)
```

**Removed**: prop `watchState` — больше не нужен (нет per-row readiness). Если call-site'ы шлют `'loading'` — трактуется как `'notready'` (defensive default, FR-017).

## Модификация `FavoriteIcon` / `PlaylistIcon`

**Файл**: `karaoke-public/src/components/FavoriteIcon.vue`, `PlaylistIcon.vue`.

**Изменения**:
- Убрать prop `loading` — состояние определяется из store (если `membership[id]` есть → loaded; если нет → ещё грузится; иначе → «off»).
- Для анонима (нет токена) — «гостевая» иконка (серая ★) с `title="Войдите, чтобы добавить в избранное"`, клик → `router.push('/login?redirect=...')`.
- Optimistic update: при клике ДО отправки запроса — `setFavorited(id, newValue)`; если ответ пришёл с `limitReached=true` — откатываем, открываем premium modal.

## Модификация `ZakromaView.vue` / `SearchView.vue` / `AuthorPlaylistView.vue`

**Изменения**:
- Удалить вызовы `readiness.load(songIds)`.
- Удалить вызовы `membership.load(songIds)` (chunked).
- Добавить вызов (один раз на mount) `favorites.loadFavoritesIds()` + `subscriptions.loadOnce()` + (если есть плейлисты) `membership.loadMembershipFor(allSongIds)`.
- На каждую песню — `PlayerIcon` с props из stream-сообщения.

## Миграция (если нужно)

**Файл**: `deploy/karaoke-db/99_idx_subscriptions_user_scope_status.sql`

**Условие**: добавляется только если индекса `(site_user_id, scope, status)` ещё нет на проде.

**SQL**:
```sql
CREATE INDEX IF NOT EXISTS idx_subscriptions_user_scope_status
    ON tbl_subscriptions (site_user_id, scope, status)
    WHERE id_song IS NOT NULL;
```

**Применение**: вручную на КАЖДОЙ БД (LOCAL и PROD), см. Constitution § Синхронизация.

## Constitution compliance summary

- ✅ Сырой JDBC (Принцип II): все новые SQL — `WHERE id IN (...)` / `WHERE site_user_id=? AND scope='SONG'`. Никакого JPA/Hibernate.
- ✅ Self-contained (Принцип I): фича использует только локальные Postgres/MinIO/Vue.
- ✅ Двух-фронтенд (Принцип V): изменения только в `karaoke-web` (public API) + `karaoke-public` (public SPA).
- ✅ Секреты (Принцип VIII): никаких новых секретов, используется существующий `km_auth_token`.
- ✅ Sync-обязательства (Принцип III): новые SQL не меняют схему (только индекс, не влияет на recordhash). Существующие таблицы не модифицируются.