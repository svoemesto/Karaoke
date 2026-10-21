# Contract: field-scoped save (внутренний примитив)

**Feature**: `546-lyrics-search-preserve-song-fields`
**Тип контракта**: internal API (Kotlin, модуль `karaoke-app`), не HTTP.
**Реализация**: `model/SongFieldUpdate.kt` (исполнение) + `model/SongWritePlan.kt` (планирование).

## 1. Сигнатуры

```kotlin
/** Колонка tbl_songs, которую разрешено писать операции. */
data class ColumnWrite(val column: String, val value: Any?)

/** Результат применения набора колонок. */
sealed interface FieldUpdateOutcome {
    data class Applied(val changedColumns: List<String>, val changedContentFields: Set<String>) : FieldUpdateOutcome
    data class Skipped(val reason: SkipReason) : FieldUpdateOutcome   // EXISTING_TEXT | EMPTY_INPUT | STATUS_AHEAD
    data object NotFound : FieldUpdateOutcome
    data class Failed(val sqlState: String?, val message: String?) : FieldUpdateOutcome
}

enum class SkipReason { EXISTING_TEXT, EMPTY_INPUT, STATUS_AHEAD }

/**
 * Записать в tbl_songs только перечисленные колонки.
 * @param songId идентификатор песни (объект Song вызывающего НЕ используется как источник значений)
 * @param plan   чистая функция, решающая по актуальному состоянию, что писать
 */
fun applyFieldUpdate(
    songId: Long,
    plan: (previous: Song) -> FieldWriteDecision,
    reason: String,
    database: KaraokeConnection,
    storageService: KaraokeStorageService,
    storageApiClient: StorageApiClient,
): FieldUpdateOutcome

/** Решение планировщика: писать / пропустить. */
sealed interface FieldWriteDecision {
    data class Write(val columns: List<ColumnWrite>) : FieldWriteDecision
    data class Skip(val reason: SkipReason) : FieldWriteDecision
}
```

Планировщики (чистые функции, тестируются без БД):

```kotlin
fun planLyricsWrite(previous: Song, foundText: String): FieldWriteDecision
fun planDuplicateOriginalWrite(previous: Song, original: Song): FieldWriteDecision
fun planAudioParentMarkersWrite(previous: Song, audioParent: Song, deltaMs: Long): FieldWriteDecision
fun planFamilySelectionWrite(previous: Song, candidate: Song, deltaMs: Long?, audio: AudioPair?): FieldWriteDecision
```

## 2. Транзакция (порядок обязателен)

1. `connection = database.getConnection()`; сохранить `previousAutoCommit`.
2. `connection.autoCommit = false`.
3. `SET LOCAL lock_timeout = '<KaraokeProperties.songSaveLockedTimeoutMs>ms'`.
4. `Song.loadFromDbByIdForUpdate(id, …, connection)` — блокировка строки + актуальное состояние.
   `null` → rollback, `NotFound` (WARN, без вставки строки).
5. `plan(previous)` → `Skip` → rollback/commit без записи, вернуть `Skipped` (INFO-лог).
6. `UPDATE tbl_songs SET <колонки из decision> WHERE id = ?` — **только** эти колонки.
7. Повторный `SELECT` строки → `current`; вычислить фактический diff `previous → current`.
8. `connection.commit()`.
9. Пост-эффекты (см. §4) — уже вне транзакции.

В `finally`: восстановить `autoCommit`; при исключении — `rollback()`.

## 3. Гарантии

- **G-1**: `UPDATE` не содержит колонок, которых нет в `decision.columns`; список колонок
  строится только из белого списка операции ([lyrics-write-policy.md](lyrics-write-policy.md)).
- **G-2**: значения в `UPDATE` берутся из `previous`/источника копирования, а не из полей
  вызывающего объекта `Song`.
- **G-3**: guard-решения принимаются по `previous`, прочитанному под блокировкой, — окна между
  решением и записью нет.
- **G-4**: `id_status` попадает в `UPDATE` только если новое значение строго больше
  `previous.idStatus` (монотонность).
- **G-5**: при `NotFound` строка не создаётся (никакого `INSERT`).
- **G-6**: ошибка записи не пробрасывается вызывающему: `Failed` + WARN, поиск/импорт
  продолжаются (совместимо с ADR local-0002 — ошибка не «глотается» молча, а логируется).
- **G-7**: идемпотентность: повторный вызов при уже записанном тексте → `Skipped(EXISTING_TEXT)`,
  никаких изменений.

## 4. Пост-эффекты (после commit)

| Эффект | Условие | Контракт |
|---|---|---|
| SSE `recordChange` | diff непустой | `SseNotification.recordChange(tableName="tbl_songs", recordId=id, diffs=factDiff, databaseName, record=current.toDTO())` через `SNS.send` |
| `SyncAudioDescendants.onSongSaved(current, previous, changedContentFields)` | всегда | набор content-полей = пересечение фактического diff с `SyncAudioDescendants.CONTENT_FIELDS`; ошибка хука логируется, запись не откатывается |
| `HealthReport.recomputeAndBroadcast` + удаление `SearchResult`/`SearchAsync` | `previous.idStatus < 6 && current.idStatus >= 6` | как в `saveToDb()` |
| `updateRemoteSongFromLocalDatabase(id)` | `Karaoke.autoUpdateRemoteSettings && Karaoke.allowUpdateRemote` и diff непустой | как в `saveToDb()` |

**Не выполняются** (относятся к полной записи): `renameFilesIfDiff`, version-bump блок,
`markNewsAvailableIfReady`.

## 5. Конфигурация и диагностика

- `songSaveLockedTimeoutMs` (default 5000) — тот же ключ, что у `saveToDbLocked()`.
- SQL state `55P03` (lock timeout) / `40P01` (deadlock) → `Failed`, WARN `song.field_update_failed`,
  rollback, вызывающий продолжает.
- Логи — по [log-format.md](log-format.md).

## 6. Кто использует

| Вызывающий | Было | Стало |
|---|---|---|
| `applyFoundLyricsIfMissing` (`UtilsAI.kt:144`) | `saveToDbLocked()` на объекте-снимке | `applyFieldUpdate(id, ::planLyricsWrite)` |
| `applyDuplicateOriginal` (`Utils.kt:4994`) | то же | `applyFieldUpdate` |
| `applyAudioParentMarkers` (`Utils.kt:5026`) | то же | `applyFieldUpdate` |
| `applyFamilySongSelection` (`Utils.kt:5047`) | то же | `applyFieldUpdate` |
| `ApiController.doCreateFromFolder`, ветка Yandex.Sync (`ApiController.kt:5417`) | `reload + saveToDbLocked()` | `applyFieldUpdate` (reload не нужен) |
| `KaraokeProcessWorker` дозавершение async (`KaraokeProcessWorker.kt:956`) | `applyFoundLyricsIfMissing` | без изменений на стороне воркера (внутри — примитив) |

## 7. Что НЕ входит

- `Song.saveToDb()` — контракт не меняется (31 вызывающее место).
- `Song.saveToDbLocked()` — остаётся для 50 вызовов в сервисах публикации (см. research R8).
- Фронтенды, схема БД, миграции.
