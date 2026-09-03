# Data Model: 299 — Перезатирание полей песни при фоновой обработке

> **Phase 1 output для `/speckit-plan`.** Описывает изменения в `Song`, `KaraokeProperties`, log-маркерах.

## Изменения в схеме БД

**Нет миграций.** Подход полностью совместим с существующей схемой `tbl_songs`:
- `FOR NO KEY UPDATE` не требует изменений таблиц (PostgreSQL 9.3+ feature, поддерживается на проде 15+).
- `SET LOCAL lock_timeout` — runtime-настройка сессии, не ALTER DATABASE/ROLE.
- `KaraokeProperties.songSaveLockedTimeoutMs` — настройка в существующем файле `/sm-karaoke/system/Karaoke.properties` (или UI).

## Новые методы в `Song` (karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt)

### `Song.saveToDbLocked(): Boolean`

```kotlin
/**
 * Атомарное сохранение песни с блокировкой строки через `SELECT ... FOR NO KEY UPDATE` (specs/299).
 * Защищает от race condition, при которой параллельная транзакция (например, ручная правка
 * через SongEdit) успевает обновить поля песни между `loadFromDbById()` и `ps.executeUpdate()`
 * фонового процесса (импорт папки, поиск текстов, демус и т.д.).
 *
 * **Алгоритм** (см. также Clarifications Session 2026-09-03):
 * 1. Получить `connection = database.getConnection()` (ThreadLocal, см. specs/087).
 * 2. `connection.autoCommit = false` — открыть транзакцию.
 * 3. `SET LOCAL lock_timeout = '{songSaveLockedTimeoutMs}ms'` (FR-060).
 * 4. `loadFromDbByIdForUpdate(...)` — загрузить `savedSong` под блокировкой строки.
 * 5. Если `savedSong == null` (песня удалена): WARN + fallback на `saveToDb()` (Pass 281 паттерн).
 * 6. `getDiff(this, savedSong)` → `ps.executeUpdate()` в той же транзакции.
 * 7. `connection.commit()` — освободить блокировку.
 * 8. `finally { connection.autoCommit = true }` — восстановить автокоммит.
 *
 * **Lock semantics**: `FOR NO KEY UPDATE` блокирует `FOR UPDATE`/`DELETE` других транзакций
 * на эту строку, но НЕ блокирует `FOR SHARE` / `FOR KEY SHARE` / простые `SELECT` чтения
 * (см. research.md R1, PostgreSQL docs §13.3).
 *
 * **lock_timeout**: при таймауте (deadlock или долгая блокировка > 5s) — `PSQLException`
 * с SQL state `55P03` или `40P01`. `saveToDbLocked` ловит, пишет WARN, делает rollback,
 * возвращает `false`.
 *
 * **Поведение**: НЕ изменяет существующий `Song.saveToDb()` (FR-003). Обратно совместим —
 * 70+ мест вызова продолжают работать как раньше.
 *
 * **Когда вызывать**: только в hot paths FR-010..FR-016 (Pass 281) и FR-020. Для остальных
 * мест (короткие эндпоинты, объект живёт < 100мс) — `saveToDb()` достаточно, race condition
 * не воспроизводится.
 *
 * @see specs/299-song-fields-overwrite-race-condition/spec.md (FR-001..FR-003, FR-060)
 * @see specs/299-song-fields-overwrite-race-condition/research.md (R1-R3)
 * @see specs/281-find-lyrics-overwrites-key-bpm/spec.md (предыдущая итерация, reload-only)
 * @return `true` если UPDATE успешно выполнен; `false` если lock timeout / deadlock / ошибка.
 */
fun saveToDbLocked(): Boolean
```

### `Song.loadFromDbByIdForUpdate(...)`

```kotlin
/**
 * Загружает песню из БД с блокировкой строки через `SELECT ... FOR NO KEY UPDATE` (specs/299).
 * Используется ТОЛЬКО внутри [saveToDbLocked] — НЕ открывает свою транзакцию, требует уже
 * открытую (параметр `connection`).
 *
 * **Важно**: после `loadFromDbByIdForUpdate` блокировка держится до `connection.commit()` /
 * `connection.rollback()`. Вызывающий код ОБЯЗАН коммитить/роллбэчить транзакцию.
 *
 * @param id ID песни для загрузки
 * @param database KaraokeConnection (для логирования и error context)
 * @param storageService / storageApiClient — нужны для создания Song-объекта
 * @param connection уже открытая JDBC-транзакция (`autoCommit = false`)
 * @return `Song?` — загруженная песня или `null`, если не найдена (песня удалена)
 * @see saveToDbLocked
 */
fun loadFromDbByIdForUpdate(
    id: Long,
    database: KaraokeConnection,
    storageService: KaraokeStorageService,
    storageApiClient: StorageApiClient,
    connection: java.sql.Connection,
): Song?
```

### Сигнатура (полная)

```kotlin
// В Song.kt (model/)
fun saveToDbLocked(): Boolean
companion object {
    fun loadFromDbByIdForUpdate(
        id: Long,
        database: KaraokeConnection,
        storageService: KaraokeStorageService,
        storageApiClient: StorageApiClient,
        connection: java.sql.Connection,
    ): Song?
}
```

## Изменения в `KaraokeProperties` (karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProperties.kt)

### Новое поле: `songSaveLockedTimeoutMs`

```kotlin
// В listKaraokeProperties (добавить новое свойство)
KaraokeProperty(
    key = "songSaveLockedTimeoutMs",
    defaultValue = 5000L,
    description = "Таймаут (мс) для SELECT FOR NO KEY UPDATE в Song.saveToDbLocked() (specs/299). " +
        "При превышении — PSQLException с SQL state 55P03 (lock timeout), saveToDbLocked возвращает false. " +
        "Default 5000 (5 сек) — оптимальный баланс между deadlock-устойчивостью и пользовательским опытом.",
),
```

### Где читается
В `Song.saveToDbLocked()`:
```kotlin
val timeoutMs = KaraokeProperties.getLong("songSaveLockedTimeoutMs").coerceAtLeast(1000L)
```

## Лог-маркеры (для `docs/ops/log-correlation.md`)

### Новые WARN-маркеры

| Маркер | Где | Условие | Что значит для оператора |
|---|---|---|---|
| `song.locked_save_fallback` | `Song.saveToDbLocked` | `loadFromDbByIdForUpdate` вернул `null` (песня удалена во время фоновой обработки) | OK fallback на `saveToDb()` без lock; текст сохранён, race condition в этом редком случае возможна |
| `song.locked_save_failed` | `Song.saveToDbLocked` | Любое другое исключение (SQL error, connection closed) | НЕ ОК; нужно проверить лог karaoke-app + состояние БД |
| `song.lock_timeout` | `Song.saveToDbLocked` | `PSQLException` SQL state `55P03` или `40P01` | OK если < 1 раза в час (SC-007); если чаще — пересмотреть подход (FR-030-bis) |

### Формат лога
```kotlin
println("[${Timestamp.from(Instant.now())}] WARN song.locked_save_fallback: songId=$id deleted between load and save; falling back to unlocked saveToDb()")
println("[${Timestamp.from(Instant.now())}] WARN song.locked_save_failed: songId=$id error=${e.message}")
println("[${Timestamp.from(Instant.now())}] WARN song.lock_timeout: songId=$id sqlState=${e.sqlState} cause=${e.message}")
```

### Обновление `docs/ops/log-correlation.md`
Добавить секцию «Перезатирание полей (specs/299)» с этими маркерами + grep-команды для мониторинга.

## Изменения в горячих путях (Pass 281 + FR-020)

### `applyFoundLyricsIfMissing` (UtilsAI.kt:144)
```kotlin
// БЫЛО (Pass 281):
val songToSave = Song.loadFromDbById(...) ?: song
songToSave.sourceText = firstNonEmpty
songToSave.fields[SongField.ID_STATUS] = "1"
songToSave.saveToDb()

// СТАЛО (spec 299):
song.sourceText = firstNonEmpty
song.fields[SongField.ID_STATUS] = "1"
song.saveToDbLocked()  // ← новая обёртка с FOR NO KEY UPDATE
```

### `applyDuplicateOriginal` (Utils.kt:4847)
Аналогично — `newSong.saveToDbLocked()` вместо `songToSave.saveToDb()`. Sync-блок (newSong.X = ...) сохраняется.

### `applyFamilySongSelection` / `autoAssignOriginalByWaveform` / `findAudioParentByWaveform`
Аналогично — `songToSave.saveToDbLocked()` + sync (как в Pass 281).

### `Song.setSourceMarkers` / `Song.setSourceText`
Внутри каждого метода — `reloaded.saveToDbLocked()` вместо `reloaded.saveToDb()`.

### FR-020 — 25+ мест
Каждое место: либо `song.saveToDbLocked()`, либо явное KDoc-обоснование «объект живёт < 100мс, race не воспроизводится» (FR-021). Полный список и verdict — в `tasks.md`.

## KDoc coverage

Все новые публичные методы (`saveToDbLocked`, `loadFromDbByIdForUpdate`) сопровождаются KDoc ≥ 50% coverage (CI gate, FR-006, FR-041).

## Метрики после деплоя

| Метрика | Где | Ожидаемое значение | Реакция при превышении |
|---|---|---|---|
| `song.locked_save_fallback` | `infra.prod.ping` лог | 0 / час | OK если 1-2 / день (удаление во время фоновой обработки — норма) |
| `song.locked_save_failed` | `infra.prod.ping` лог | 0 / час | Алерт, проверить БД |
| `song.lock_timeout` | `infra.prod.ping` лог | < 1 / час | Если > 1/час — рассматривать FR-030-bis (optimistic lock) |
| Pass 281 acceptance scenarios | manual (см. `contracts/manual-test-checklist.md`) | PASS | Откат через revert PR |

## Phase 2 input (для tasks.md)

Из data-model.md следующие конкретные изменения нужны:
1. **`Song.kt`**: добавить 2 новых метода (`saveToDbLocked`, `loadFromDbByIdForUpdate`) — ~80 строк кода + ~60 строк KDoc.
2. **`KaraokeProperties.kt`**: добавить 1 новое поле в `listKaraokeProperties` — ~6 строк.
3. **8 мест Pass 281**: заменить `loadFromDbById` + `saveToDb` на `saveToDbLocked` — ~20 строк.
4. **25+ мест FR-020**: для каждого — вердикт hot/not-hot + (если hot) `saveToDbLocked` + (если not-hot) KDoc-обоснование.
5. **`docs/ops/log-correlation.md`**: добавить секцию про specs/299 маркеры — ~30 строк.

## См. также

- [`spec.md`](./spec.md) — спецификация.
- [`plan.md`](./plan.md) — Implementation Plan.
- [`research.md`](./research.md) — Phase 0 research (R1-R7).
- [`contracts/manual-test-checklist.md`](./contracts/manual-test-checklist.md) — manual test checklist.
- [`../../../docs/ops/log-correlation.md`](../../../docs/ops/log-correlation.md) — log markers (обновляется в Phase 5).
