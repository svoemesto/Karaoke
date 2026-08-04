# Contracts: изменённые эндпоинты

## `GET /api/public/player/{id}/access` (karaoke-web, `PublicPlayerController.access`)

Формат ответа не меняется. Меняется ТОЛЬКО формула `canWatch`:

```diff
- val canWatch = ready && (song.onAir || premium || subscribed)
+ val canWatch = ready && (song.isFreelyAvailableNow || premium || subscribed)
```

Всё остальное (`isDemo`, `canExport`, `token`, логирование событий) — без
изменений.

## `POST /api/public/player/readiness` (karaoke-web, `PublicPlayerController.readiness`)

```diff
- val watchable = contentReady && (song!!.onAir || premium || id in subscribedIds)
+ val watchable = contentReady && (song!!.isFreelyAvailableNow || premium || id in subscribedIds)
```

## `GET /api/public/stats` (karaoke-web, `PublicApiController`)

**До**:
```json
{"onSponsr": 12345, "onAir": 8000, "exclusive": 4345, "inWork": 1500, "total": 13845}
```

**После** (ключи `onAir`/`exclusive` переименованы, остальные — без
изменений, см. research.md Decision 5):
```json
{"onSponsr": 12345, "freeNow": 8100, "subscriptionOnly": 4245, "inWork": 1500, "total": 13845}
```

Потребители, требующие правки: `karaoke-public/src/store/modules/stats.js`
(state/getters/mutations), `HomeView.vue` (`mapGetters`), `AboutView.vue`
(`stats.onAir`/`stats.exclusive` → `stats.freeNow`/`stats.subscriptionOnly`).

## `GET /api/public/zakroma` (через `ZakromaPublicDto.fromZakroma`)

`ZakromaAlbumSongPublicDto` — поле `exclusive: Boolean` удаляется, добавляются:

```diff
  val onAir: Boolean,
- val exclusive: Boolean,
  val datePublish: String,
  val songSubscriptionAvailable: Boolean,
+ val alwaysFree: Boolean,
+ val freelyAvailableNow: Boolean,
+ val freeAccessWindowEndText: String?,
```

`ZakromaView.vue`/`SearchView.vue` читают новые поля вместо `sett.exclusive`:

```diff
  showCoin(sett) {
-   return !this.isPremium && (sett.exclusive || !sett.onAir)
+   return !this.isPremium && !sett.freelyAvailableNow
  },
  showDate(sett) {
-   return !sett.onAir && !sett.exclusive
+   return !this.isPremium && !sett.alwaysFree && !isPurchased(sett) &&
+     (!sett.onAir || sett.freelyAvailableNow)
  }
```

`isPurchased(sett)` — уже существующая проверка через `readiness`/`cart`
composable (см. `showCartIcon`), song куплена = не показываем ничего.
Текст, который выводится (`sett.datePublish` для «Будет в эфире с…» или
`sett.freeAccessWindowEndText` для «В эфире до…»), выбирается по
`!sett.onAir` (первое) / `sett.onAir && sett.freelyAvailableNow` (второе) —
см. data-model.md «Song Availability Category». Ключевое отличие от
старого поведения: `showDate` теперь также гейтится `!isPremium` (FR-010
spec.md — раньше дата показывалась и премиум-пользователям тоже, это
осознанное изменение поведения, зафиксированное в spec.md User Story 6,
сценарий 4).

## `GET /api/public/song` (через `SongPublicDto.fromSong`, karaoke-web)

Аналогично `ZakromaAlbumSongPublicDto`:

```diff
  val onAir: Boolean,
- val exclusive: Boolean,
  val datePublish: String,
  val airTimestamp: Long?,
  ...
  val songSubscriptionAvailable: Boolean,
+ val alwaysFree: Boolean,
+ val freelyAvailableNow: Boolean,
+ val freeAccessWindowEndText: String?,
```

`SongView.vue` — `waitingTitle`/`waitingBody` переключаются с `s.exclusive`
на `s.onAir` (см. research.md Decision 6). `s.alwaysFree`/
`s.freelyAvailableNow` пока не требуются в `SongView.vue` напрямую (доступ
уже решён через `/access`), но нужны для консистентности с
`ZakromaAlbumSongPublicDto` и на случай будущего переиспользования.

## Admin API (karaoke-app, `ApiController.kt`) — song edit / list endpoints

- Query-параметр `exclusive: String?` (`ApiController.kt:2969`) и его
  обработка (`:3098`, `sett.fields[SongField.EXCLUSIVE] = it`) —
  удаляются.
- Query-параметр `flagExclusive` в двух list/digest-эндпоинтах
  (`:2426`, `:2606`) — удаляется (соответствующий `args["flag_exclusive"]`
  UI-фильтр в webvue3 больше не существует; raw-SQL обработчик в `Song.kt`
  остаётся нетронутым как безвредный мёртвый код, см. research.md
  Decision 7 — специально НЕ удаляется из `Song.kt`, чтобы не трогать
  общий query-билдер, используемый и легаси `unpublish`-инструментом).
- `SongDTO`/`SongDTOdigest` — поля `exclusive`/`flagExclusive` удаляются
  из сериализации (запрос/ответ редактирования и списка песен).

## `webvue3` internal store (`filter/store.js`, admin only)

`songsFilterFlagExclusive` (state/getter/mutation/action, включая
персист через `setWebvueProp`) — удаляется целиком.
