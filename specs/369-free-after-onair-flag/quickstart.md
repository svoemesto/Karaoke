# Quickstart: Флаг «не снимать с эфира» (free_after_on_air)

> **Phase 1**: ручной сценарий проверки end-to-end.

## Цель

Подтвердить, что после применения миграции и пересборки `karaoke-web`
- новый флаг `freeAfterOnAir` доступен в `SongEdit.vue`;
- после включения флага песня с истёкшим стандартным окном эфира
  остаётся в `AccessMode.open`;
- существующее поведение (без флага) не изменилось.

## Предусловия

1. PostgreSQL `karaoke-db` доступен (`docker ps | grep karaoke-db` или
   аналогичный контейнер).
2. Контейнер `karaoke-web` доступен.
3. Контейнер `webvue3` доступен (admin UI).
4. В проекте применены миграции до `48_storage_metadata_cache.sql`
   (или последний номер в `deploy/karaoke-db/` до начала этого PR).
5. В Song.kt, SongStateResolver.kt, StatsService.kt — код, описанный в
   `data-model.md`, уже применён.
6. В `SongEdit.vue` добавлены кнопки ДА/НЕТ «Не снимать с эфира».

## Подготовка тестовой песни

```sql
-- Выбираем любую песню, у которой dateTimePublish в прошлом и idStatus >= 6
-- (например, чтобы она была в эфире, но окно доступа истекло).
-- Помещаем её в «прошлое» на 2+ месяца, чтобы гарантированно пройти
-- стандартное окно (1 месяц).
UPDATE tbl_songs
   SET publish_date = '2025-12-01',
       publish_time = '00:00:00',
       free = false,
       free_after_on_air = false,
       exclusive = false
 WHERE id = <TEST_SONG_ID>;

-- Проверить, что песня существует и находится в эфире:
SELECT id, song_name, song_author, publish_date, free, free_after_on_air, exclusive
  FROM tbl_songs
 WHERE id = <TEST_SONG_ID>;
```

Ожидаемо: одна строка, `free_after_on_air = false`, `exclusive = false`.

## Шаг 1. До включения флага — песня НЕ в эфире (premium-only)

### 1a. Проверить через публичный API (анонимный пользователь):

```bash
curl -s "http://localhost:8897/api/songs/<TEST_SONG_ID>/access" | jq .
```

**Ожидаемо**:

```json
{
  "accessMode": "premium-only",
  ...
}
```

(потому что `dateTimePublish` давно прошёл, стандартное окно истекло,
`free=false`, `exclusive=false`, `free_after_on_air=false`.)

### 1b. Проверить через `SongStateResolver`:

```kotlin
// В существующем тесте или в REPL через Spring:
val state = SongStateResolver.resolve(
    idStatus = 6L,
    free = false,
    freeAfterOnAir = false,
    dateTimePublish = <2025-12-01>,
    now = Date(),  // сейчас
)
assert(state == SongState.DONE)  // окно истекло, эфир давно
```

**Ожидаемо**: `SongState.DONE` (песня вышла из эфира по таймеру).

## Шаг 2. Включить флаг через `SongEdit.vue`

1. Открыть `http://localhost:8897/admin/songs/edit/<TEST_SONG_ID>` (или
   аналогичный URL `webvue3`).
2. В блоке «Метки / публикация» найти новый блок «Не снимать с эфира».
3. Нажать кнопку **ДА**.
4. Нажать **Сохранить** в форме.

**Ожидаемо**: страница перезагружается, кнопка **ДА** подсвечена
(`group-button-round-wide-active`), `lastUpdate` в SongEdit изменился.

### Проверить, что в БД записалось:

```sql
SELECT id, free_after_on_air FROM tbl_songs WHERE id = <TEST_SONG_ID>;
```

**Ожидаемо**: одна строка, `free_after_on_air = true`.

## Шаг 3. После включения флага — песня в эфире

### 3a. Публичный API:

```bash
curl -s "http://localhost:8897/api/songs/<TEST_SONG_ID>/access" | jq .
```

**Ожидаемо**:

```json
{
  "accessMode": "open",
  ...
}
```

### 3b. `SongStateResolver`:

```kotlin
val state = SongStateResolver.resolve(
    idStatus = 6L,
    free = false,
    freeAfterOnAir = true,                    // <-- ИЗМЕНЕНО
    dateTimePublish = <2025-12-01>,
    now = Date(),
)
assert(state == SongState.ON_AIR)             // эфир навсегда
```

**Ожидаемо**: `SongState.ON_AIR`.

### 3c. `Song.isFreelyAvailableNow`:

```kotlin
val song = ... // загрузить <TEST_SONG_ID>
song.freeAfterOnAir = true
song.onAir = true                              // dateTimePublish в прошлом
song.free = false
song.isExclusive = false
assert(song.isFreelyAvailableNow == true)      // даже если now > freeAccessWindowEnd
```

**Ожидаемо**: `true` (песня остаётся freely available после окна).

## Шаг 4. Выключить флаг — поведение возвращается

В `SongEdit.vue` нажать **НЕТ**, сохранить.

### Проверить:

```bash
curl -s "http://localhost:8897/api/songs/<TEST_SONG_ID>/access" | jq .
```

**Ожидаемо**: `accessMode: "premium-only"` (как в шаге 1a).

## Шаг 5. Не сломать другие песни

### 5a. Случайная выборка (10 песен) — все должны иметь `free_after_on_air = false`:

```sql
SELECT COUNT(*) FROM tbl_songs WHERE free_after_on_air IS NULL;
-- Ожидаемо: 0 (NOT NULL DEFAULT)

SELECT COUNT(*) FROM tbl_songs WHERE free_after_on_air = false;
-- Ожидаемо: ~18 097 (все существующие)
```

### 5b. Sync md5 не разошёлся:

```bash
# В Karaoke admin: «Синхронизация в 1 клик» для тестовой песни
# Должна показать recordhash = одинаковый LOCAL и SERVER
```

### 5c. CI 7/7 PASS:

```bash
# Линтеры + покрытие документации
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-web:ktlintCheck
cd webvue3 && npm run lint:check && npx prettier --check "src/**/*.{vue,js,ts,json}" && cd ..
cd karaoke-public && npm run lint:check && npx prettier --check "src/**/*.{vue,js,ts,json}" && cd ..
bash tools/check-kdoc-coverage.sh
bash tools/check-jsdoc-coverage.sh webvue3
bash tools/check-jsdoc-coverage.sh karaoke-public
```

**Ожидаемо**: все 7 проверок PASS (или с поправкой baseline в этом PR).

## Шаг 6. Regression — grandfathered + free=true

Песня с `free=true`:

```kotlin
val state = SongStateResolver.resolve(
    idStatus = 6L,
    free = true,
    freeAfterOnAir = true,                      // оба флага — должно быть ON_AIR
    dateTimePublish = null,                     // нет publishDate
    now = Date(),
)
assert(state == SongState.ON_AIR)               // free=true имеет приоритет
```

**Ожидаемо**: `ON_AIR` (приоритет `free=true`).

## Acceptance gates

| Gate | Ожидаемо |
|---|---|
| Шаг 1 (pre-flag): `access` = premium-only | ✅ |
| Шаг 2: `SongEdit.vue` сохраняет `free_after_on_air = true` | ✅ |
| Шаг 3a: `access` = open | ✅ |
| Шаг 3b: `SongState.ON_AIR` | ✅ |
| Шаг 3c: `isFreelyAvailableNow = true` | ✅ |
| Шаг 4: выключение → premium-only | ✅ |
| Шаг 5a: 0 NULL, ~18 097 = false | ✅ |
| Шаг 5b: sync md5 совпадает | ✅ |
| Шаг 5c: CI 7/7 PASS | ✅ |
| Шаг 6: `free=true` приоритет | ✅ |

Если все gates ✅ — фича готова к merge.