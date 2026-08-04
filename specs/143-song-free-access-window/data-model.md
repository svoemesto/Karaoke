# Data Model: Временное окно бесплатного доступа к песням

## Song (`karaoke-app/model/Song.kt`)

### Существующие поля (без изменений)

| Поле | Тип | Источник | Назначение |
|------|-----|----------|------------|
| `date`/`time` | `String` | `publish_date`/`publish_time` (VARCHAR) | Сырые компоненты момента эфира |
| `dateTimePublish` | `Date?` | вычисляется из `date`+`time`, MSK | Единая точка отсчёта эфира |
| `onAir` | `Boolean` | `dateTimePublish <= now(MSK)` | «Эфир уже наступил» — НЕ меняется, используется `isPubliclyWatchable` (specs/089) |
| `isContentReady` | `Boolean` | статус≥6 + стемы + картинки + маркеры | Готовность контента — НЕ меняется |
| `isPubliclyWatchable` | `Boolean` | `isContentReady && onAir` | Триггер одноразовой авто-новости «в эфире» (specs/089) — НЕ меняется (см. research.md Decision 1) |
| `free` | `Boolean` | `free` (BOOLEAN) | **Новый смысл**: «всегда бесплатно» / вечный эфир (было: «бесплатно на Sponsr») |
| `idTariff` | `Int` | `id_tariff` | Разрешена ли разовая подписка на песню (`>= 0`) — без изменений |

### Новые вычисляемые поля

| Поле | Тип | Формула | Назначение |
|------|-----|---------|------------|
| `freeAccessWindowEnd` | `Date?` | `null`, если `!onAir`; иначе `dateTimePublish` + 1 календарный месяц (`Calendar.add(MONTH, 1)`, MSK) | Момент, когда окно бесплатного доступа истекает |
| `isFreelyAvailableNow` | `Boolean` | `free \|\| (onAir && now(MSK) < freeAccessWindowEnd)` | **Единственный источник истины** для «бесплатно доступна прямо сейчас» — заменяет `onAir` во ВСЕХ местах платного доступа (FR-001) |
| `freeAccessWindowEndText` | `String?` | `null`, если `free \|\| !onAir`; иначе `freeAccessWindowEnd` отформатирован тем же паттерном, что и `date`/`time` (`"dd.MM.yy HH:mm"`) | Готовый текст для «В эфире до …» (FR-009), не требует форматирования на фронте |

### Удаляемые поля/ветвления

- `var exclusive: Boolean` (геттер/сеттер, `SongField.EXCLUSIVE`) — удаляется целиком.
- `flagExclusive: String` (digest-геттер `"✓"/"-"`) — удаляется.
- `datePublish: String` getter — убирается ветвление `exclusive && free` / `exclusive` (первые 2 ветки), остаётся только «дата не определена» / `"$date $time"`.
- `SongState.EXCLUSIVE`/`EXCLUSIVE_FREE` и обе ветки в `Song.state` getter'е — удаляются (см. research.md Decision 4).
- `getVKGroupDescriptionSponsr()` — убирается ветвление по `exclusive` (текст всегда идёт по «не-эксклюзивной» ветке, т.к. `exclusive` больше не существует; это legacy Sponsr-генератор текста, вызывается только из `ApiController.kt:1254`).
- `RecordDiff("exclusive", ...)` в LOCAL↔SERVER diff-компараторе — удаляется.
- INSERT/UPDATE Pair-список и row-load (`rs.getBoolean("exclusive")`) — удаляются (колонка БД остаётся, просто не читается/не пишется, см. research.md Decision 2).

## ZakromaAlbumSong / ZakromaAlbumSongPublicDto

| Было | Стало |
|------|-------|
| `onAir: Boolean` | без изменений (внутреннее поле, больше не используется фронтом для гейта — см. ниже) |
| `exclusive: Boolean` | **удаляется** |
| `datePublish: String` | без изменений по типу, но текст больше не содержит «Эксклюзивно на SPONSR…» веток |
| `songSubscriptionAvailable: Boolean` | без изменений |
| — | **новое**: `alwaysFree: Boolean` (= `song.free`) |
| — | **новое**: `freelyAvailableNow: Boolean` (= `song.isFreelyAvailableNow`) |
| — | **новое**: `freeAccessWindowEndText: String?` (= `song.freeAccessWindowEndText`) |

## SongPublicDto

Аналогичный набор изменений: `exclusive` удаляется; добавляются
`alwaysFree`, `freelyAvailableNow`, `freeAccessWindowEndText`. Поля
`onAir`/`airTimestamp`/`datePublish` остаются без изменений (нужны для
«Будет в эфире с …» и для `SongView.vue`'s `daysUntilAir`).

## StatBySong — счётчики

| Категория (внутр. имя) | Было (SQL) | Стало (SQL) |
|---|---|---|
| `total` | `count(*) WHERE SKIP` | без изменений |
| `collection` (`onSponsr` в API) | `CONTENT_READY AND SKIP` | без изменений |
| `freeNow` (был `onAir`) | `CONTENT_READY AND SKIP AND publish_date/time истекли` | `CONTENT_READY AND SKIP AND (free=true OR (publish_date/time истекли AND publish_date/time + INTERVAL '1 month' > now()))` |
| `subscriptionOnly` (был `exclusive`) | `collection − onAir` | `collection − freeNow` (формула та же, входные данные другие) |
| `inWork` | `total − collection` | без изменений |

Инвариант (SC-003 spec.md): `freeNow + subscriptionOnly + inWork == total −
(total − collection) + inWork` → упрощается до `collection == freeNow +
subscriptionOnly` и `total == collection + inWork`, обе тождественно верны
по построению (вычитание), проверяется вручную в `quickstart.md`.

## Song Availability Category (производное UI-состояние, не поле БД)

Используется для решения, какой текст показать в Закромах/на странице
песни непремиум-пользователю без личной подписки на конкретную песню:

```text
alwaysFree == true              → "always_free"     (ничего не показывать)
subscribed(user, song) == true  → "purchased"        (ничего не показывать)
!onAir                          → "not_yet_aired"    ("Будет в эфире с {datePublish}", только если date/time заданы)
onAir && freelyAvailableNow     → "free_window"       ("В эфире до {freeAccessWindowEndText}")
onAir && !freelyAvailableNow    → "subscription_only" (ничего доп. не показывать — платный статус и так виден по иконке-монетке/отсутствию плеера)
```

Премиум-пользователь: во всех случаях — ничего (FR-010).

## SongView.vue — состояния плеера (уточнение существующего `usePlayerAccess`)

| `ready` | `canWatch` | `isDemo` | `onAir` | UI |
|---|---|---|---|---|
| false | false | false | false/true | Карточка ожидания с обратным отсчётом ИЛИ старый VK-видео-фоллбек (если демо тоже недоступно, контент не готов) |
| true | false | true | false/true | Демо-карточка (`playerIsDemo`) — без изменений |
| true | false | false | false | Карточка ожидания, обратный отсчёт до эфира (как раньше) |
| true | false | false | **true** | **Новое**: карточка ожидания, текст «Эта песня доступна только по подписке» (FR-015) — раньше недостижимо |
| true | true | false | true/false | Плеер, без карточки |
