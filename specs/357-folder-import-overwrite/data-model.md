# Data Model: 357 — Folder Import Overwrite (Audit #73)

**Дата**: 2026-09-10
**Спека**: [spec.md](spec.md)

> В спеке 357 **нет изменений в data model**. Существующая entity `Song` и таблица `tbl_songs` остаются без изменений. Этот документ фиксирует контракт для ясности.

## Entity: Song (без изменений)

**Файл**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt`

**Таблица**: `tbl_songs` (PostgreSQL)

**Поля**: ~150 полей. См. [`knowledge/domains/catalog/components/song-entity.md`](../../knowledge/domains/catalog/components/song-entity.md).

**Защищаемые поля** (FR-100..FR-150): ВСЕ поля, доступные через `SongEdit.vue`:
- `song_name`, `author`, `album`, `year`, `songType`, `genre`, `language`
- `id_status` (state machine 0..7)
- `song_tone`, `song_bpm`
- URL'ы стемов (`audioSong`, `audioVocals`, `audioAccompaniment`, `audioMix`)
- `root_id`, `audio_parent_id`, `audio_similarity_percent`, `audio_delta_ms`
- `source_text`, `source_markers`, `formatted_text_*`
- и т.д. (полный список — `Song.kt`, поля через `fields[SongField.X]`)

## Метод: Song.saveToDbLocked() (наследуется из спеки 299)

**Файл**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt:5540`

**Контракт** (без изменений, см. спеку 299):

```kotlin
fun saveToDbLocked(): Boolean {
    // 1. Check readonly / connection — fallback на saveToDb() с WARN
    // 2. Connection.setAutoCommit(false)
    // 3. SET LOCAL lock_timeout = '5s'
    // 4. SELECT * FROM tbl_songs WHERE id = ? FOR NO KEY UPDATE
    // 5. Если savedSong == null → rollback + fallback на saveToDb()
    // 6. getDiff(this, savedSong) → SQL UPDATE
    // 7. commit() / rollback() + WARN при ошибке
}
```

## Расширение: WARN-логирование diff overlap (FR-160)

**Новая секция** в `saveToDbLocked()` (после reload из БД, до `getDiff()`):

```kotlin
// FR-160: WARN при попытке записать diff с устаревшим значением поля
// (это значит параллельная транзакция успела обновить поле между load и reload)
val fieldsWithOverlap = mutableListOf<String>()
fields.forEach { (field, value) ->
    val savedValue = savedSong.fields[field]
    if (savedValue != null && value != savedValue && /* value in original in-memory snapshot */) {
        // value изменился с момента начальной загрузки, и savedValue другой
        fieldsWithOverlap.add("$field=$value(snapshot)/$savedValue(db)")
    }
}
if (fieldsWithOverlap.isNotEmpty()) {
    println("[${Timestamp.from(Instant.now())}] WARN infra.prod.ping song.locked_save_diff_overlap: songId=$id fields=${fieldsWithOverlap.joinToString(",")}")
}
```

**Контракт лога**: см. [contracts/log-format.md](contracts/log-format.md).

## Аудит (FR-100, FR-110, FR-120)

Результат аудита 49 мест `saveToDb()` — см. [research.md § Таблица аудита](research.md#таблица-аудита).

- **9 мест категории A** (короткий endpoint): оставить `saveToDb()`.
- **30 мест категории B1** (долгий процесс): перевести на `saveToDbLocked()`.
- **1 место категории C** (первичная вставка): оставить `saveToDb()`.
- **9 строк** — KDoc/комментарии, не реальные вызовы.

## Нет новых entities / таблиц

Спека 357 — это **аудит + рефакторинг существующего кода**, без новых таблиц, колонок или entities.
