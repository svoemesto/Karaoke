# Data Model: 126 — Поиск тональности из существующего `[key].json`

> Сгенерировано Stage 3 (`/speckit-plan`) — Phase 1.

**Без изменений в schema БД.** Эта фича — **runtime-оптимизация**, не требует миграций.

## Затронутые сущности

### 1. `Song` (модель Karaoke)

**Файл**: `karaoke-app/.../model/Song.kt` (~8820 строк)

**Затронутые поля**:
- `key: String` (getter, строка 687) — тональность из `fields[SongField.KEY]`.
- `bpm: Int` — BPM из `fields[SongField.BPM]`.
- `pathToFileKeyBpmFinder: String` (getter, строка 500) — путь к файлу `<songFile> [key].json`.

**Новый метод**:
- `fun applyKeyBpmFromFileIfExists(database: KaraokeConnection): Boolean`
  - Читает `pathToFileKeyBpmFinder`.
  - Парсит через `Json.decodeFromString(AudioAnalysisResult.serializer(), text)`.
  - Если файл валиден И `data.key != null` И `data.bpm != null` → применяет
    `data.key` и `data.bpm.toString()` к `fields`, сохраняет через `saveToDbLocked()`.
  - Возвращает `true` (применён) или `false` (нужен docker).

**Validation rules** (для unit-тестов):
- Файл не существует → `false`.
- Файл есть, но не читается (`IOException`) → `false`.
- Файл есть, но невалидный JSON (`SerializationException`) → `false`.
- Файл есть, JSON валиден, но `data.key == null` ИЛИ `data.bpm == null` → `false`.
- Файл есть, JSON валиден, поля заполнены → `true` + apply + save.

### 2. `AudioAnalysisResult` (data class)

**Файл**: `karaoke-app/.../model/AudioAnalysisResult.kt` (или подобный)

**Используется в**: `Json.decodeFromString(AudioAnalysisResult.serializer(), text)`.

**Структура** (предположительно, см. `Song.getKeyBpmFromFile`):
```kotlin
@Serializable
data class AudioAnalysisResult(
    val key: String?,
    val bpm: Int?,
    // ... возможно ещё поля
)
```

**Не меняем** — этот data class уже определён и используется.

### 3. `KaraokeProcess` (модель очереди)

**Файл**: `karaoke-app/.../KaraokeProcess.kt`

**Затронутые места**:
- `prepareContext` (строка 1861): для типа `KEY_BPM_FROM_FILE` — добавляем проверку файла.

### 4. `HealthReport` (модель отчёта)

**Файл**: `karaoke-app/.../HealthReport.kt`

**Затронутые места**:
- `getHealthReportList(song)` (строка 1294): в `solutionActions` для
  `CONSISTENCY_VIOLATION` «У песни отсутствует тональность» (около 1418) —
  добавляем проверку файла.

## State Machine

**Нет state transitions**. Фича не меняет состояния ни одной сущности —
только условия, при которых происходит side-effect (запись `key`/`bpm` в БД).

## Relationships

```text
Song.key ←── apply ──→ AudioAnalysisResult.key
Song.bpm ←── apply ──→ AudioAnalysisResult.bpm
Song.key  ──also-writes─→  KaraokeProcess.KEY_BPM_FROM_FILE (если файла нет)
Song ──triggers──→ HealthReport.CONSISTENCY_VIOLATION (если key пустой)
```

## Indexes / Constraints

**Нет новых индексов**. Поле `key` уже индексировано в `tbl_songs` (стандартная схема).

## Migration

**Не требуется**.

## Backward Compatibility

✅ **Полная**. Никакие поля не переименованы, никакие типы не изменены.
Существующие `KaraokeProcess` с типом `KEY_BPM_FROM_FILE` (созданные до merge)
продолжат работать: worker всё равно выполнит их через docker (если файл
не существует) или прочитает файл (если он уже есть — текущее поведение
через `getKeyBpmFromFile(false)`).
