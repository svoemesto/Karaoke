# Component: key-bpm-from-file

> **Домен**: [processing](../domain.md)
> **Компонента**: логика применения тональности/BPM из существующего файла
> `[key].json` без повторного запуска docker.

## Ответственность | Responsibility

Эта компонента решает задачу **#126 (OpenProject «Поиск тональности»)**:
если у песни отсутствует тональность, но для неё уже есть файл
`<songFile> [key].json` с результатом прошлого keybpmfinder-прогона — применить
тональность из файла, не запуская docker заново.

**Экономия**: ~5-10 секунд docker-прогона + CPU-ресурсы на каждый случай.

**Граница**: компонента НЕ отвечает за:

- Запуск docker (`svoemestodev/keybpmfinder:latest`) — это
  [`async-process-queue.md`](async-process-queue.md) через
  `KaraokeProcessTypes.KEY_BPM_FROM_FILE`.
- Обновление UI — это [`sse` domain](../../sse/domain.md).
- Создание самого файла — это docker-image `keybpmfinder`.

## Ubiquitous Language | Единый язык

| Термин | Определение | Где в коде |
| --- | --- | --- |
| **`[key].json`** | Файл с результатом keybpmfinder-прогона рядом с audio-файлом песни. Формат — `AudioAnalysisResult`. | `Song.pathToFileKeyBpmFinder` (Song.kt:500) |
| **`AudioAnalysisResult`** | `data class AudioAnalysisResult(val bpm: Int?, val key: String?, val error: String?)` | `model/AudioAnalysisResult.kt` |
| **`applyKeyBpmFromFileIfExists`** | Instance helper на `Song`. Применяет key/bpm из файла, если он есть и валиден. Использует `saveToDbLocked()`. Возвращает `true` если файл был применён, `false` если нужно запускать docker. | `model/Song.kt:1902+` |
| **`parseKeyBpmFileOrNull`** | Pure-parse helper в companion `Song`. Принимает `filePath: String`, возвращает `AudioAnalysisResult?` (null если файла нет / невалиден / поля null). Без side-effect — для unit-тестов. | `model/Song.kt:6261+` |
| **`infra.cache.keybpm`** | SLF4J-категория логов для cache-hit событий. Семантически похоже на `infra.cache.hrpool` (Pass 128). | `model/Song.kt:692` |
| **`cache hit`** | Ситуация, когда `[key].json` уже есть → docker не запускается. |

## Algorithm | Алгоритм

```text
При попытке исправить тональность песни:

1. Парсить `<songFile> [key].json` через `Song.parseKeyBpmFileOrNull(pathToFileKeyBpmFinder)`:
   - Файла нет → вернуть null → fallback на docker.
   - JSON невалиден → catch exception, вернуть null → fallback на docker.
   - `data.key == null` или `data.bpm == null` → вернуть null → fallback на docker.
   - Иначе → вернуть `AudioAnalysisResult(key, bpm)`.

2. Применить через `Song.applyKeyBpmFromFileIfExists()`:
   - `fields[SongField.KEY] = data.key!!`
   - `fields[SongField.BPM] = data.bpm!!.toString()`
   - `saveToDbLocked()` — race-safety (Pass 299/357).
   - Логировать INFO `keybpm: applied from file for songId=... key=... bpm=...`.

3. Если helper вернул `false` (файла нет или невалиден) → создать
   `KaraokeProcess` типа `KEY_BPM_FROM_FILE` через
   `KaraokeProcess.createProcess(...)` (старый путь с docker).
```

## Call-sites

| Call-site | Файл | Что делает |
|---|---|---|
| **Точка 1**: HealthReport.solutionActions | `HealthReport.kt:1407-1458` | Перед `KaraokeProcess.createProcess(...)` вызывает `applyKeyBpmFromFileIfExists()`. При cache-hit — `HealthReport(OK, "Тональность применена из файла")`. |
| **Точка 2**: KaraokeProcess.createProcess | `KaraokeProcess.kt:1040-1043` | Сразу после `if (wasWorking) return 0` — если `KEY_BPM_FROM_FILE` и файл есть → return 0 (процесс НЕ создаётся). |

Защита **обоих** call-sites (Pass 401 clarification #126-1) покрывает:
- HealthReport-кнопку «Исправить всё».
- Ручные вызовы `createProcess` через `/api/process/...`.
- Миграции и batch-скрипты.

## Race-safety

1. **HealthReport**: перед вызовом helper'а уже стоит single-flight
   `KaraokeProcess.loadList(...).isNotEmpty()` (см. HealthReport.kt:1389) —
   не создаст дубликат процесса.
2. **createProcess**: `if (wasWorking) return 0` блокирует дубликаты.
3. **Внутри helper**: `saveToDbLocked()` атомарно берёт `SELECT FOR NO KEY UPDATE`
   + UPDATE, защищая от race с параллельной правкой через SongEdit (Pass 299/357).

## Интерфейсы и Контракты

### Instance API: `Song.applyKeyBpmFromFileIfExists(): Boolean`

**Pre-conditions**: `song` уже загружен (id != 0), `pathToFileKeyBpmFinder` указывает
на валидный путь (создаётся через `rightFileName()` в Song).

**Post-conditions**:
- Если файл есть, валиден, поля `key` и `bpm` НЕ null:
  - `fields[SongField.KEY] = key`
  - `fields[SongField.BPM] = bpm.toString()`
  - `saveToDbLocked()` (Pass 299/357 race-safety)
  - лог `INFO infra.cache.keybpm: applied from file for songId=... key=... bpm=...`
  - return `true`
- Иначе: return `false` (песня не меняется, файл не создаётся).

### Static API: `Song.Companion.parseKeyBpmFileOrNull(filePath: String): AudioAnalysisResult?`

**Pure function** — без side-effect, без DB. Используется в unit-тестах.

**Возвращает**:
- `AudioAnalysisResult(key, bpm)` — файл есть, валиден, поля != null.
- `null` — файл не существует, или JSON невалиден, или `key`/`bpm` == null.

### Concurrency

- `saveToDbLocked()` (Pass 299/357) даёт `SELECT FOR NO KEY UPDATE` + UPDATE атомарно.
- Защита от дубля процесса в `createProcess` — `if (wasWorking) return 0`.
- Защита от дубля процесса в HealthReport — `loadList(...).isNotEmpty()` (line 1389).

### Error model

- Невалидный JSON — `catch (e: Exception)`, log DEBUG, return null (no crash).
- IO error — IOException, log DEBUG, return null.
- Не прерывает processing chain (Pass 379 R-08 sanitizer idempotency).

## Логика и Алгоритмы

Подробное описание шагов см. в секции «Algorithm | Алгоритм» выше.

### Диаграмма последовательности (cache-hit)

```text
User -> HealthReport: "Исправить всё"
HealthReport -> Song.applyKeyBpmFromFileIfExists()
  Song -> FileSystem: read <song> [key].json
  alt файл есть, валиден
    Song -> Song: fields[KEY] = key; fields[BPM] = bpm
    Song -> Postgres: SELECT FOR NO KEY UPDATE
    Song -> Postgres: UPDATE songs SET ...
    Song -> Logger: INFO cache-hit applied
    Song --> HealthReport: true
  else файла нет / невалиден
    Song --> HealthReport: false
  end
alt cache hit
  HealthReport -> HealthReport: HealthReport(OK, "Тональность применена из файла")
else cache miss
  HealthReport -> KaraokeProcess.createProcess(KEY_BPM_FROM_FILE)
  KaraokeProcess -> Song.applyKeyBpmFromFileIfExists()
    alt cache hit (race-condition second check)
      KaraokeProcess --> return 0 (skip docker)
    else cache miss
      KaraokeProcess -> Docker: run svoemestodev/keybpmfinder
    end
end
```

## Тестирование

**Unit-тесты** (без БД, через pure-helper `parseKeyBpmFileOrNull`):
- `KeyBpmFromFileCacheTest.kt` — 4 теста:
  1. Файл есть, валиден - возвращает AudioAnalysisResult.
  2. Файла нет - возвращает null.
  3. Файл есть, поля null - возвращает null.
  4. Файл есть, невалидный JSON - возвращает null (no crash).

**Интеграционные тесты** (требуют БД) - отдельная задача (Pass 401 follow-up).

## Зависимости

### Внутренние

- `Song` entity (`karaoke-app/.../model/Song.kt`).
- `SongField.KEY`, `SongField.BPM` (`karaoke-app/.../model/SongFields.kt`).
- `AudioAnalysisResult` (`karaoke-app/.../model/AudioAnalysisResult.kt`).
- `saveToDbLocked()` (`Song.kt`, Pass 299/357).
- `infra.cache.keybpm` SLF4J category (новая категория по образцу `infra.cache.hrpool` Pass 128).
- `KaraokeProcess.createProcess(...)` (`KaraokeProcess.kt:1040+`).
- `KaraokeProcessTypes.KEY_BPM_FROM_FILE`.
- `HealthReport.solutionActions` (`HealthReport.kt:1407+`).

### Внешние

- **None** — компонента читает только локальную FS (MinIO mount через Docker volume).
- Docker `svoemestodev/keybpmfinder:latest` НЕ используется (cache-hit пропускает его).

### Test dependencies

- JUnit 5 (`org.junit.jupiter.api.Assertions`).
- JSR-305 `@Nullable` (для nullability marker на `parseKeyBpmFileOrNull`).

## Связанные компоненты

- [`async-process-queue.md`](async-process-queue.md) — KaraokeProcess и worker'ы.
- [`song-entity.md`](../../catalog/components/song-entity.md) — `Song` entity, `fields`, `saveToDbLocked`.
- `karaoke-app/.../model/AudioAnalysisResult.kt` — формат `[key].json` (`AudioAnalysisResult { bpm: Int?, key: String?, error: String? }`).
- TODO: вынести в `knowledge/domains/processing/components/audio-analysis-result.md` отдельным документом (Pass 401 follow-up).

## Связанные ADR

- ADR `knowledge/adr/local-008-process-types.md` (если существует) — добавление
  `KEY_BPM_FROM_FILE` как типа процесса. Иначе — нет.

## История

- **Pass 401** (2026-09-15): Initial. Создан по OpenProject #126. Автор: agent (Karaoke).
- **Pass 401 follow-up** (2026-09-16): обновлён под CI lint-knowledge (NO EMOJI,
  добавлены секции «Интерфейсы и Контракты» / «Логика и Алгоритмы» / «Зависимости»
  / «Связанные ADR», удалены символьные маркеры статуса).
