# Data Model: Флаг «не снимать с эфира» (free_after_on_air)

> **Phase 1**: модель данных и контракт нового поля.

## Изменения в БД

### Таблица `tbl_songs`

**Файл миграции**: `deploy/karaoke-db/<NNN>_tbl_songs_free_after_on_air.sql`

```sql
-- Добавление колонки "не снимать с эфира" (см. specs/369-free-after-onair-flag).
-- Семантика: после наступления dateTimePublish песня остаётся публично доступной
-- (ON_AIR / AccessMode.open) даже после истечения стандартного окна бесплатного
-- доступа. Не путать с `free=true` ("всегда бесплатно") и `exclusive=true`
-- (premium-only по бизнес-решению). Default = false для обратной совместимости.
ALTER TABLE tbl_songs
    ADD COLUMN IF NOT EXISTS free_after_on_air BOOLEAN NOT NULL DEFAULT false;
```

### `recordhash`-триггеры (ОБЯЗАТЕЛЬНО обновить, см. Constitution III)

Два файла:

- `deploy/recordhash_songs.sql` — `update_tbl_songs_recordhash()`;
- `deploy/recordhash_songs_sync.sql` — `update_tbl_songs_sync_recordhash()`.

В обоих файлах добавить в цепочку `md5(COALESCE(...) || COALESCE(...) || ...)`:

```sql
COALESCE(NEW.free_after_on_air::TEXT, 'false') ||
```

**Где именно**: после `COALESCE(NEW.exclusive::TEXT, '') ||` (последнее поле
в текущем триггере) — это естественное место, т.к. `free_after_on_air` —
бизнес-флаг уровня публикации, как и `exclusive`.

## Изменения в Kotlin (catalog)

### `Song.kt` (companion + instance)

**Файл**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt`

1. Добавить в `SongField` enum (если `free_after_on_air` тоже enum-keyed —
   см. существующий `FREE`):

   ```kotlin
   FREE_AFTER_ON_AIR,
   ```

2. В блок с `var free: Boolean` (строки ~901) добавить аналогичный геттер-сеттер:

   ```kotlin
   /**
    * Не снимать с эфира после стандартного окна бесплатного доступа.
    * См. specs/369-free-after-onair-flag.
    * Семантика: после dateTimePublish (если он уже наступил) песня остаётся
    * публично доступной, не уходит в premium-only по таймеру.
    * Не путать с [free] ("всегда бесплатно") и [isExclusive] ("premium-only").
    */
   var freeAfterOnAir: Boolean
       get() {
           val txt = fields[SongField.FREE_AFTER_ON_AIR] ?: false.toString()
           return txt == true.toString()
       }
       set(value) {
           fields[SongField.FREE_AFTER_ON_AIR] = value.toString()
       }
   ```

3. В `Song.isFreelyAvailableNow` (строки ~664) добавить условие:

   ```kotlin
   val isFreelyAvailableNow: Boolean get() = (
       free ||
           freeAfterOnAir && onAir ||  // <-- NEW: не снимать с эфира после окна
           (
               onAir &&
                   Calendar.getInstance(TimeZone.getTimeZone("Europe/Moscow")).time < freeAccessWindowEnd
           )
   )
   ```

   > Оператор `&&` имеет более высокий приоритет, чем `||`, так что
   > `freeAfterOnAir && onAir` вычислится корректно. Для ясности —
   > добавить скобки вокруг всего `||`-выражения при код-ревью.

### `SongStateResolver.kt`

**Файл**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SongStateResolver.kt`

Добавить параметр `freeAfterOnAir: Boolean` в сигнатуру `resolve()` и
вставить новую ветку:

```kotlin
fun resolve(
    idStatus: Long,
    free: Boolean,
    freeAfterOnAir: Boolean,         // <-- NEW
    dateTimePublish: Date?,
    now: Date,
    freeAccessWindowMonths: Int = 1,
): SongState {
    if (idStatus < 6L) return SongState.IN_WORK
    if (free) return SongState.ON_AIR                              // приоритет
    if (freeAfterOnAir) {                                         // <-- NEW
        val dt = dateTimePublish ?: return SongState.EXCLUSIVE
        if (!dt.after(now)) return SongState.ON_AIR
    }
    val dt = dateTimePublish ?: return SongState.EXCLUSIVE
    if (!dt.after(now) && isFreelyAvailableNowAt(dt, now, freeAccessWindowMonths)) {
        return SongState.ON_AIR
    }
    // ... остальная логика (TODAY / DONE)
}
```

### `StatsService.kt` (publishing) — `accessModeFor`

**Файл**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/StatsService.kt`
(или эквивалентный файл, если метод уже перенесён в другой класс —
см. `knowledge/domains/publishing/components/publishing-services.md`).

Добавить новую ветку после проверки `isExclusive`:

```kotlin
fun accessModeFor(song: Song, user: SiteUser?): AccessMode {
    if (song.isExclusive) return AccessMode.PREMIUM_ONLY
    if (song.freeAfterOnAir &&                                       // <-- NEW
        song.dateTimePublish?.isAfter(Instant.now()) == false) {
        return AccessMode.OPEN
    }
    if (song.publishDate?.isAfter(Instant.now()) == true) {
        return AccessMode.PREMIUM_ONLY
    }
    return AccessMode.OPEN
}
```

## Изменения в БД — детальный контракт

| Поле | Тип | Default | NOT NULL | Описание |
|---|---|---|---|---|
| `free_after_on_air` | `BOOLEAN` | `false` | ✅ | Флаг «не снимать с эфира». См. semantics выше. |

Существующие записи (18 097 на проде) получат `false` через `DEFAULT` —
никаких backfill-скриптов.

## Изменения в Song.kt — read/write контракт

Все чтения/записи — через существующий `KaraokeDbTable.save()`:

- `loadListFromDb` / `loadFromDbById` — добавить `rs.getBoolean("free_after_on_air")` →
  `song.freeAfterOnAir = value` (рядом со строкой 8034, где читается `free`);
- `saveToDb` — reflection-diff автоматически подхватит новое поле через
  `fields[SongField.FREE_AFTER_ON_AIR]`;
- `compareByRecordDiff` (или эквивалент) — добавить проверку
  `if (settA.freeAfterOnAir != settB.freeAfterOnAir) result.add(RecordDiff("freeAfterOnAir", ...))`.

## Validations / Constraints

- **NOT NULL**: ✅ (default `false` гарантирует значение для существующих строк).
- **Range**: только `true` / `false` (BOOLEAN).
- **Cross-field**: не зависит от `free` / `exclusive` / `idStatus` /
  `dateTimePublish` (значения сохраняются независимо). Семантика
  комбинаций описана в `dictionaries.md` (обновление ниже).

## Relationships

- `Song` 1→1 `tbl_songs.free_after_on_air` (через PK `id`).
- Поле не имеет FK / ссылок на другие таблицы — это «бизнес-флаг
  уровня публикации», хранится вместе с другими флагами уровня
  публикации (`free`, `exclusive`).

## State transitions

Нет — поле не участвует в `IdStatus` (1..6). Состояние песни
определяется через `SongStateResolver`, который **уже** учитывает
`freeAfterOnAir` через Decision 5.