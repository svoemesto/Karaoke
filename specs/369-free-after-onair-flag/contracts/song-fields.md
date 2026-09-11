# Contracts: Флаг «не снимать с эфира» (free_after_on_air)

> **Phase 1**: контракты API и JSON-схемы, которые меняются/добавляются.

## JSON-схема `SongDTO` (Song → JSON)

`SongDTO` сериализуется через Jackson. Все boolean-поля `Song`
отдаются на фронт без префикса `is` (см. `docs/features/song-public-dto.md`).
Поле `freeAfterOnAir` сериализуется как:

```json
{
  "id": 12345,
  "name": "...",
  "free": false,
  "freeAfterOnAir": false,
  "exclusive": false,
  ...
}
```

### Обратная совместимость

- Старые клиенты (webvue3 без этой фичи, сторонние интеграции),
  которые НЕ знают о `freeAfterOnAir`, продолжат работать: при десериализации
  они проигнорируют неизвестное поле.
- Новый код, читающий старые данные (где `freeAfterOnAir` отсутствует),
  получит `false` через `var freeAfterOnAir: Boolean` (default).
- Jackson не включает поле в `recordhash` — recordhash считается только в
  БД (см. `data-model.md` Decision 2).

## REST API

### `GET /api/songs/getById?id={id}`

**Ответ** (новое поле):

```json
{
  "id": 12345,
  "name": "...",
  "free": false,
  "freeAfterOnAir": true,
  "dateTimePublish": "2026-09-01T12:00:00Z",
  ...
}
```

### `GET /api/songs/list?...&filter_free_after_on_air=true|false`

**Запрос**: фильтр списка песен по `freeAfterOnAir`. Аналогично существующему
`filter_free=true|false` (см. строку 7729 `Song.kt`).

**Ответ**: массив `SongDTO`, где `freeAfterOnAir` соответствует фильтру.

> **Примечание**: фильтр НЕ обязателен для MVP (см. spec FR-007 и
> research Decision 7 — основной UX редактора — toggle в `SongEdit.vue`).
> Если фильтр нужен — добавляется в этом же PR по образцу `filter_free`.

### `POST /api/songs/save` (SongEdit.vue → бэкенд)

**Тело**: полный `SongDTO` с `freeAfterOnAir: boolean`.

**Поведение**:

- Если `freeAfterOnAir=true` — `Song.saveToDb()` запишет
  `fields[SongField.FREE_AFTER_ON_AIR] = "true"`, `tbl_songs.free_after_on_air = true`.
- Если `freeAfterOnAir=false` — запишется `false`.
- Поле сохраняется через тот же reflection-diff, что и `free`,
  `exclusive` и др.

### `GET /api/songs/{id}/access` (публичный API)

**Ответ** (новое поведение):

| `freeAfterOnAir` | `dateTimePublish` | `isExclusive` | Стандартное окно | `AccessMode` |
|---|---|---|---|---|
| `true` | в прошлом | `false` | истекло | **`open`** ← новое |
| `false` | в прошлом | `false` | истекло | `premium-only` (старое поведение) |
| `true` или `false` | в будущем | любое | любое | `premium-only` |
| любое | любое | `true` | любое | `premium-only` |

## Внутренние контракты (Kotlin)

### `Song.isFreelyAvailableNow` (catalog)

**Старое поведение**:
```kotlin
val isFreelyAvailableNow: Boolean get() = (
    free ||
        (onAir && now < freeAccessWindowEnd)
)
```

**Новое поведение**:
```kotlin
val isFreelyAvailableNow: Boolean get() = (
    free ||
        (freeAfterOnAir && onAir) ||         // <-- NEW
        (onAir && now < freeAccessWindowEnd)
)
```

### `SongStateResolver.resolve(...)` (catalog)

**Старая сигнатура**:
```kotlin
fun resolve(idStatus: Long, free: Boolean, dateTimePublish: Date?, now: Date, ...): SongState
```

**Новая сигнатура** (добавлен параметр `freeAfterOnAir`):
```kotlin
fun resolve(
    idStatus: Long,
    free: Boolean,
    freeAfterOnAir: Boolean,        // <-- NEW
    dateTimePublish: Date?,
    now: Date,
    freeAccessWindowMonths: Int = 1,
): SongState
```

**Логика** — см. `data-model.md` Decision 5.

> **Совместимость**: сигнатура изменилась → все callers обновляются.
> Текущие callers (см. `grep -rn "SongStateResolver.resolve" karaoke-app/`)
> — единицы мест, обновляются в этом PR.

### `StatsService.accessModeFor(song, user)` (publishing)

Сигнатура без изменений. Логика — см. `data-model.md` Decision 6.

## UI контракт (`SongEdit.vue`)

### Toggle «Не снимать с эфира»

**Расположение**: в блоке «Метки / публикация», **под** существующим
блоком «Всегда бесплатно (вечный эфир): ДА / НЕТ».

**Структура** (HTML/Vue 3 + Bootstrap-vue-next):

```vue
<div class="label-and-input">
  <div class="label-medium">Не снимать с эфира (после окна доступа):</div>
  <button
    class="group-button-round-wide"
    :class="freeAfterOnAirButtonClass(true)"
    type="button"
    value="true"
    @click="setFreeAfterOnAir(true)"
  >
    ДА
  </button>
  <button
    class="group-button-round-wide"
    :class="freeAfterOnAirButtonClass(false)"
    type="button"
    value="false"
    @click="setFreeAfterOnAir(false)"
  >
    НЕТ
  </button>
</div>
```

**Методы**:

```javascript
setFreeAfterOnAir(freeAfterOnAir) {
  this.song.freeAfterOnAir = freeAfterOnAir
}

freeAfterOnAirButtonClass(freeAfterOnAir) {
  return freeAfterOnAir === this.song.freeAfterOnAir
    ? 'group-button-round-wide-active' : ''
}
```

(Шаблон скопирован из существующих `setFree` / `freeButtonClass` —
см. строки 4005-4023 `SongEdit.vue`.)

## Семантика комбинаций флагов

| `free` | `freeAfterOnAir` | `isExclusive` | Эффект |
|---|---|---|---|
| `true` | `*` | `false` | Всегда бесплатно (`isFreelyAvailableNow` = true навсегда). |
| `false` | `true` | `false` | После эфира (`dateTimePublish ≤ now()`) — навсегда бесплатно, независимо от стандартного окна. |
| `false` | `false` | `false` | Стандартное поведение: бесплатно в течение `freeAccessWindowMonths` после эфира, потом premium-only. |
| `*` | `*` | `true` | `premium-only` (бизнес-решение, приоритет). |

## Migration

Новые колонки в БД добавляются через `ALTER TABLE ... ADD COLUMN IF NOT EXISTS ... DEFAULT false NOT NULL` —
существующие 18 097 песен получат `false` автоматически. Никаких
backfill-скриптов, никаких down-time.

`recordhash`-триггеры пересоздаются в той же миграции (или в двух — по
одному на каждую таблицу: `tbl_songs` и `tbl_songs_sync`).