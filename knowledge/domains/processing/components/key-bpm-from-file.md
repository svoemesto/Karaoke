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

## Тестирование

**Unit-тесты** (без БД, через pure-helper `parseKeyBpmFileOrNull`):
- `KeyBpmFromFileCacheTest.kt` — 4 теста:
  1. Файл есть, валиден → возвращает AudioAnalysisResult.
  2. Файла нет → возвращает null.
  3. Файл есть, поля null → возвращает null.
  4. Файл есть, невалидный JSON → возвращает null (no crash).

**Интеграционные тесты** (требуют БД) — отдельная задача (Pass 401 follow-up).

## Rollout checklist

1. ✅ Helper `applyKeyBpmFromFileIfExists` (Song.kt).
2. ✅ HealthReport (точка 1) — `solutionActions`.
3. ✅ KaraokeProcess.createProcess (точка 2).
4. ✅ Pure-parse helper `parseKeyBpmFileOrNull` + 4 unit-теста.
5. ⏳ Интеграционное тестирование (Stage 6 ручная проверка на dev).
6. ⏳ Production deploy (Pass 401+ — после ревью владельца).

## Связанные компоненты

- [`async-process-queue.md`](async-process-queue.md) — KaraokeProcess и worker'ы.
- [`song-entity.md`](../../catalog/components/song-entity.md) — `Song` entity, `fields`, `saveToDbLocked`.
- [`audio-analysis-result.md`](../../processing/components/audio-analysis-result.md) — формат `[key].json` (TODO: создать отдельно).

## История

- **Pass 401** (2026-09-15): Initial. Создан по OpenProject #126. Автор: agent (Karaoke).
