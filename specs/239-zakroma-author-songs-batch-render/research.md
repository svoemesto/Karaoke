# Research: Закрома автора — отрисовка списка песен без N×3 фоновых запросов

**Phase**: 0 | **Branch**: `239-zakroma-author-songs-batch-render` | **Date**: 2026-08-25

## R1. Существующая логика `stemsReady` / `isContentReady` / `isFreelyAvailableNow`

**Decision**: использовать уже существующие в `Song.kt` геттеры и НЕ дублировать логику в новых endpoint'ах.

**Rationale**: 
- `Song.isContentReady` (Song.kt:1132) — агрегирует персистентные `player_readiness_flags` из `tbl_settings` (Pass 100). Это **уже** «готовость плеера для всех голосов», без живого MinIO.
- `Song.isFreelyAvailableNow` (Song.kt:652) — `is_in_air || flag_free`. Поля уже в `tbl_songs`.
- `PublicPlayerController.stemsReady(song)` (line 139) = `song.isContentReady`. Уже вызывается без MinIO-проверок.
- `PublicPlayerController.readiness` endpoint (line 228-250) — уже содержит полную логику зелёный/золотой:
  ```kotlin
  val watchable = contentReady && (song.isFreelyAvailableNow || premium || id in subscribedIds)
  ```
  Где `subscribedIds = Subscription.subscribedSongIds(userId, songIds, ...)`.

**Альтернативы**:
- ❌ Дублировать логику `isContentReady` в новом endpoint'е → дублирование, рассинхрон с будущими изменениями флагов.
- ❌ Live HEAD в MinIO для каждой песни → то, от чего избавились в Pass 100.
- ✅ Использовать существующие геттеры в `Song` projection для NDJSON-сообщения спеки 181.

**Что нужно в NDJSON**: добавить в каждое сообщение поля `contentReady: Boolean` (вычисляется через `song.isContentReady`), `isFreelyAvailableNow: Boolean` (= `song.isFreelyAvailableNow`), `idStatus: Int`. Флаги `is_in_air` и `flag_free` уже присутствуют в `Song` и могут быть добавлены отдельно для прозрачности (необязательно).

## R2. Формат существующего `/playlists/membership` и план изменений

**Decision**: оставить существующий `/api/public/account/playlists/membership` без изменений (он уже bulk-friendly — принимает CSV ids), добавить новый endpoint `/favorites/ids` для плоского списка.

**Rationale**:
- Текущий `membership` уже принимает `ids` (CSV) и возвращает `Map<songId, {favorited, playlistIds}>`.
- Проблема не в endpoint'е, а в том, что фронт вызывал его много раз (через readiness chunks).
- Решение: фронт вызывает его **один раз** с полным списком id при входе на страницу крупного автора, кладёт результат в module-level Map.

**Альтернативы**:
- ❌ Расширять `membership` параметром `scope=favorites-only|all` → усложняет контракт, ломает существующих клиентов.
- ✅ Добавить новый тонкий endpoint `/api/public/account/favorites/ids` → просто, контракт ясен (только `favorited` ids, ничего больше), не ломает существующий код.

**Что нужно**:
- `GET /api/public/account/favorites/ids` → `[123, 456, 789]` (Long[], отсортирован или нет — без разницы).
- `GET /api/public/account/song-subscriptions/ids` → `[42, 43, 44]` (Long[]).
- Существующий `/playlists/membership?ids=...` остаётся для случая «есть не-избранные плейлисты».

## R3. Существующий NDJSON-стрим `/zakroma/stream` — какие поля уже есть

**Decision**: добавить 3 поля в каждое NDJSON-сообщение стрима: `idStatus`, `isFreelyAvailableNow`, `contentReady`.

**Rationale**:
- Спека 181 уже описывает NDJSON с полями песни. Текущая реализация `PublicApiController.zakromaStream` (line 254) использует `ZakromaStreamMessageDto` (см. `dto/ZakromaStreamMessageDto.kt`).
- Из них `idStatus` (int) и `contentReady` (boolean) — нужны для иконки плеера.
- `isFreelyAvailableNow` (boolean) — нужно для логики «зелёный vs золотой» на клиенте.

**Альтернативы**:
- ❌ Включить `is_in_air` и `flag_free` отдельно вместо `isFreelyAvailableNow` → клиенту пришлось бы самому OR'ить, плюс наружу утекают внутренние флаги.
- ✅ Агрегированное `isFreelyAvailableNow` — клиент получает один boolean, логика согласована с бэком (`Song.isFreelyAvailableNow`).

## R4. Индексы на `tbl_subscriptions` для быстрого lookup'а

**Decision**: проверить существование индекса `idx_subscriptions_user_scope_status (site_user_id, scope, status)`. Если нет — добавить миграцию.

**Rationale**:
- Запрос `SELECT id_song FROM tbl_subscriptions WHERE site_user_id=? AND scope='SONG' AND status='PAID'` без индекса = full table scan.
- На проде `tbl_subscriptions` — тысячи строк, без индекса сканирование медленное (но не критично — десятки мс).
- Best practice: составной индекс `(site_user_id, scope, status) WHERE id_song IS NOT NULL`.

**Альтернативы**:
- ❌ Partial index `WHERE scope='SONG' AND status='PAID'` — уже покрывается составным.
- ❌ Не добавлять индекс — допустимо, если объём данных мал. **Решение**: проверить, добавить если объём > 1k строк.

**Что нужно**: миграция `99_idx_subscriptions_user_scope_status.sql` (если индекса нет) + проверить через `\d tbl_subscriptions` в psql.

## R5. Module-level singleton pattern в `usePlaylistMembership.js`

**Decision**: использовать тот же паттерн module-level singleton для новых `useSongSubscriptions` composable и для `FavoriteSet` (вынести из `usePlaylistMembership.js` в отдельный composable `useFavorites` или оставить в `usePlaylistMembership.js` как есть).

**Rationale**:
- `usePlaylistMembership.js` уже использует module-level `membership`, `loading`, `playlists`, `playlistsLoaded` — все живут вне setup()-scope, переживают browser back.
- Паттерн проверен в спеке 181/246, известные подводные камни учтены.
- Cleanup при logout: `watch(token, ...)` уже есть в архитектуре (надо убедиться — добавить в tasks).

**Альтернативы**:
- ❌ Pinia / Vuex store — overkill для трёх module-level коллекций; не используется в `karaoke-public`.
- ❌ LocalStorage кэш — излишне, memory-only достаточно (singleton живёт пока открыта вкладка; при reload — перезагрузка, что нормально).
- ✅ Module-level singleton + cleanup по `token` watcher.

**Что нужно**:
- Создать `composables/useSongSubscriptions.js` с module-level `Set<number>`, методом `loadOnce()` (вызывается из `App.vue` или при login).
- Расширить `usePlaylistMembership.js`: добавить метод `loadFavoritesIds()` (отдельный bulk fetch, кладёт в `favoriteIds: Set<number>` рядом с существующим `membership: Map`).
- В `App.vue` (или новом `composables/useAuthBootstrap.js`): при `token` change — `await Promise.all([favorites.loadOnce(), playlists.loadAll(), subscriptions.loadOnce()])`.

## R6. Совместимость с `PlayerView.vue` (страница одиночной песни)

**Decision**: НЕ трогать `PlayerView.vue`. Он использует `usePlayerAccess` (per-song токен `kp_token_*`) — это отдельная логика, не per-row запрос из списка. Backward-compat сохраняется.

**Rationale**:
- `PlayerView.vue` (PlayerIcon на странице одиночной песни) использует `usePlayerAccess.checkAccess(songId, ...)` — это про генерацию **токена** для плеера, не про «зелёный vs золотой» в списке.
- `usePlayerReadiness.js` остаётся для случая «одиночная песня с server-driven readiness» — на странице `SongView` (если используется).
- `usePlaylistMembership.load(ids)` остаётся для случая «страница одиночной песни, нужен membership одной песни».

**Альтернативы**:
- ❌ Удалить `usePlayerReadiness` совсем → сломает страницу `SongView`/`PlayerView`.
- ✅ Оставить, но запретить вызовы в `ZakromaView`/`SearchView`/`AuthorPlaylistView`.

## R7. Очистка call-site'ов `readiness.load(ids)` из списочных view

**Decision**: удалить вызовы `readiness.load(songIds)` из `ZakromaView.vue`, `SearchView.vue`, `AuthorPlaylistView.vue`. Оставить `membership.load(songIds)` в этих view — он уже вызывается, надо просто убедиться, что вызывается **один раз** с полным списком, а не через chunked-load.

**Rationale**:
- `readiness.load(songIds)` — главный источник зависания (2 HEAD в MinIO × 2500 = 5000 MinIO-операций).
- `membership.load(songIds)` — менее критичный (один SQL-запрос в БД с `WHERE id IN (...)`), но всё равно лишний. Заменяется на bulk fetch + singleton.

**Что нужно**:
- Найти все вызовы `readiness.load` через grep `readiness.load(`. Заменить на вызов новых bulk-fetch composables.
- Найти все вызовы `membership.load(songIds)` в этих view. Заменить на single bulk-fetch.

## R8. BroadcastChannel для playlist-membership

**Decision**: расширить существующий `BroadcastChannel('km-favorites')` для playlist-membership И subscriptions (одно имя канала, разные типы сообщений).

**Rationale**:
- `BroadcastChannel('km-favorites')` уже создан в `usePlaylistMembership.js:106`. Сейчас рассылает только `{songId, favorited}`.
- Расширение — добавить `type: 'favorited' | 'playlist' | 'subscription'` discriminator. Один канал — проще поддержка.
- Подписки (subscriptions) обновляются реже, чем favorites; их broadcast — для будущего расширения (например, если будет страница редактирования подписки).

**Альтернативы**:
- ❌ Отдельные каналы `km-playlists`, `km-subscriptions` → 3 канала = 3 подписки, сложнее координация.
- ✅ Один канал с дискриминатором.

## Summary research findings

| R# | Решение | Что делаем |
|----|---------|-----------|
| R1 | Используем `song.isContentReady` + `isFreelyAvailableNow` | +3 поля в NDJSON |
| R2 | Не трогаем существующий `membership`, добавляем `/favorites/ids` | 2 новых endpoint'а |
| R3 | Расширяем NDJSON-сообщение `zakromaStream` | +3 поля |
| R4 | Проверяем/добавляем индекс на `tbl_subscriptions` | 1 миграция (если нет) |
| R5 | Module-level singleton для новых stores | 2 новых composable |
| R6 | `PlayerView.vue` не трогаем | backward-compat |
| R7 | Удаляем `readiness.load` из списочных view | 3 файла, удаление вызовов |
| R8 | Расширяем `BroadcastChannel('km-favorites')` | 1 файл, добавление дискриминатора |

Все unknowns закрыты, переход к Phase 1 (design + contracts).