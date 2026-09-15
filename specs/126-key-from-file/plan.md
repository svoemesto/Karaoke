# Implementation Plan: 126 — Поиск тональности из существующего `[key].json`

**Branch**: `126-key-from-file` | **Date**: 2026-09-15 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/126-key-from-file/spec.md`

## Summary

Реализовать проверку наличия валидного файла `<songFile> [key].json` **перед**
созданием `KaraokeProcess` типа `KEY_BPM_FROM_FILE` (и внутри worker'а
перед запуском docker). Если файл есть и валиден — применить `key`/`bpm`
к песне без docker-прогона. Экономит ~5-10 секунд и CPU-ресурсы.

**Технический подход**:
1. Вынести логику проверки/применения из `Utils.executeGetKeyBpmFromFile`
   в публичный helper `Song.applyKeyBpmFromFileIfExists(database): Boolean`
   (возвращает `true` если файл был применён, `false` если нужно запускать docker).
2. **Точка 1** (`HealthReport.kt`): в `solutionActions` для `CONSISTENCY_VIOLATION`
   «У песни отсутствует тональность» (около строки 1418) — **перед**
   `KaraokeProcess.createProcess(...)` вызвать `song.applyKeyBpmFromFileIfExists(...)`.
   Если вернуло `true` — НЕ создавать процесс, сразу вернуть результат.
3. **Точка 2** (`KaraokeProcess.kt`): в `prepareContext` для типа
   `KEY_BPM_FROM_FILE` (около строки 1861) — **перед**
   `song.argsKeyBpmFinder()` вызвать ту же функцию. Если вернуло `true` —
   пометить процесс как `DONE` (пустой `args` + статус DONE).
4. Логировать пропуск docker в `infra.cache.keybpm` через SLF4J.

## Technical Context

**Language/Version**: Kotlin 1.9+ (JVM 21), Spring Boot.
**Primary Dependencies**: Spring DI, SLF4J (`org.slf4j`), `kotlinx.serialization`,
Jackson (через `Json.decodeFromString(AudioAnalysisResult.serializer(), text)`).
**Storage**: PostgreSQL (KaraokeConnection) — для `saveToDbLocked()`. Filesystem — для `[key].json`.
**Testing**: JUnit 5, Mockito (`@Mock` для KaraokeStorageService/StorageApiClient).
**Target Platform**: Linux server (karaoke-app Docker-контейнер).
**Project Type**: Spring Boot monolith (backend-only, никакого фронта).
**Performance Goals**: < 1 сек от нажатия «Исправить всё» до появления `song.key` (вместо ~5-10 сек с docker).
**Constraints**:
- **Race-safety**: используем `song.saveToDbLocked()` (Pass 299/357) для защиты от SongEdit race.
- **Single-flight**: `KaraokeProcess.loadList(args=processArgs).isNotEmpty()` уже проверяется в HealthReport (строка 1389).
- **docker-volume safety**: файл `[key].json` пишется в `pathToFileKeyBpmFinder.rightFileName()` (рядом с `fileAbsolutePath` песни).
**Scale/Scope**: Karaoke-проект, ~20000 песен, ~50000 keybpm-прогонов за всё время.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- ✅ **Principle II (Storage & DB)** — JPA запрещён, мы используем `KaraokeConnection` (jOOQ/raw JDBC), не JPA. PASS.
- ✅ **Principle IV (Knowledge-First)** — spec.md § Knowledge References содержит pre-flight grep и явный «Что НЕ нашлось». PASS.
- ✅ **Principle IX (Knowledge SSoT)** — FR-003 требует создания `key-bpm-from-file.md`. PASS.
- ✅ **AGENTS.md Tier-1 Hard Gates**:
  - Branch creation через `tools/specify-bootstrap.sh` уже выполнен (Pass 401).
  - Ветка `126-key-from-file` создана от master, НЕ прямой commit в master.
  - `tools/check-no-jpa-imports.sh` PASS (мы не добавляем JPA).
  - `tools/check-no-mp4-mentions.sh` — мы не трогаем MP4.
- ✅ **Architecture-conventions R-04/R-05** — не меняем Docker-образы.
- ✅ **Architecture-conventions R-08 (Sanitizer idempotency)** — не применяется.
- ✅ **Architecture-conventions R-11 (no MP4 mentions)** — не трогаем.
- ✅ **AGENTS.md Subagent workspace isolation (IX.3)** — один агент, один worktree, PASS.

## Project Structure

### Documentation (this feature)

```text
specs/126-key-from-file/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output (не нужен — backend-only без API)
└── tasks.md             # Phase 2 output
```

### Source Code (repository root)

```text
karaoke-app/
├── src/main/kotlin/com/svoemesto/karaokeapp/
│   ├── model/
│   │   └── Song.kt                                   # [MODIFY] + applyKeyBpmFromFileIfExists helper
│   ├── HealthReport.kt                                # [MODIFY] ~line 1418 + ~line 1861
│   ├── KaraokeProcess.kt                              # [MODIFY] ~line 1861 (точка 2)
│   └── Utils.kt                                       # [NO-OP: refactor-опционально]
└── src/test/kotlin/com/svoemesto/karaokeapp/
    └── KeyBpmFromFileCacheTest.kt                    # [NEW] unit-тесты

knowledge/domains/processing/components/
└── key-bpm-from-file.md                              # [NEW] Living Docs
```

## Phase 0: Research

### Исследованные вопросы

1. **Q: Где живёт `Song.fields[SongField.KEY]` / `Song.fields[SongField.BPM]`?**
   - A: В `fields: MutableMap<SongField, String>` (Song.kt:687), через getter
     `val key: String get() = fields[SongField.KEY] ?: ""`. Запись идёт
     напрямую в map (см. Utils.kt:4140-4141). Сохранение через
     `saveToDbLocked()` (Pass 299/357).

2. **Q: Какие call-sites могут запустить KEY_BPM_FROM_FILE?**
   - A:
     - `HealthReport.kt:1423` — `KaraokeProcess.createProcess(...)` в `solutionActions`.
     - `KaraokeProcess.kt:1861` — `argsKeyBpmFinder()` в `prepareContext`.
     - **Никаких других call-sites** (проверено через grep `KEY_BPM_FROM_FILE`).

3. **Q: Какой формат у файла `[key].json`?**
   - A: `AudioAnalysisResult` (data class с `key: String?`, `bpm: Int?`).
     Парсится в `Song.getKeyBpmFromFile` через `Json.decodeFromString(AudioAnalysisResult.serializer(), text)`.
     Если `data.key == null` или `data.bpm == null` — функция возвращает `Pair("", 0)`.

4. **Q: Нужна ли миграция БД?**
   - A: **Нет**. Не трогаем schema, не добавляем колонок. Меняем только
     логику создания процессов.

5. **Q: Какой SLF4J-категории придерживаться?**
   - A: Новая категория `infra.cache.keybpm` (см. convention в
     `knowledge/domains/monitoring/components/log-categories.md`). Это
     семантически похоже на `infra.cache.hrpool` (Pass 128).

### Альтернативы, отвергнутые

1. **Альтернатива A**: Проверка только в `HealthReport.solutionActions` —
   отвергнута владельцем (clarification #126-1). Не защищает от ручных
   запусков через `/api/process/...`.

2. **Альтернатива B**: Проверка только в `KaraokeProcess.prepareContext` —
   отвергнута владельцем (clarification #126-1). HealthReport всё равно
   покажет ERROR до того, как процесс пройдёт через prepareContext.

3. **Альтернатива C**: Использовать Spring `@Cacheable` на `getKeyBpmFromFile` —
   отвергнута. У нас уже есть **runtime-кэш** через наличие файла, нет
   смысла дублировать в Spring Cache (race-safety).

## Phase 1: Design & Contracts

### Data Model

**Без изменений schema БД**. Изменения только в коде.

Новый helper в `Song.kt`:

```kotlin
/**
 * specs/126-key-from-file (#126): применяет key/bpm из существующего
 * `<songFile> [key].json` файла, если файл есть и валиден.
 *
 * @return true если файл был найден и применён; false если файла нет
 *         или он невалиден (в этом случае нужно запускать docker).
 *
 * Race-safety: использует saveToDbLocked() для защиты от SongEdit.
 */
fun applyKeyBpmFromFileIfExists(database: KaraokeConnection): Boolean {
    if (!File(pathToFileKeyBpmFinder).exists()) return false
    val text = try {
        File(pathToFileKeyBpmFinder).readText(Charsets.UTF_8)
    } catch (e: Exception) {
        return false
    }
    val data = try {
        Json.decodeFromString(AudioAnalysisResult.serializer(), text)
    } catch (e: Exception) {
        return false
    }
    if (data.key == null || data.bpm == null) return false
    fields[SongField.KEY] = data.key
    fields[SongField.BPM] = data.bpm.toString()
    saveToDbLocked()  // Pass 299/357
    log.info("keybpm: applied from file for songId=$id (key=${data.key}, bpm=${data.bpm})")
    return true
}
```

### Изменения в `HealthReport.kt` (точка 1)

```kotlin
// около строки 1418, в solutionActions для CONSISTENCY_VIOLATION
// «У песни отсутствует тональность»
// ПЕРЕД: KaraokeProcess.createProcess(...)
// ДОБАВИТЬ:

if (song.applyKeyBpmFromFileIfExists(database)) {
    // Файл уже есть — тональность применена, process не нужен.
    // Возвращаем HealthReport с canResolve=false, problemText обновлён.
    result.add(
        HealthReport(
            healthReportType = CONSISTENCY_VIOLATION,
            song = song,
            healthReportStatus = OK,
            canResolve = false,
            problemText = "Тональность была применена из существующего файла",
        ),
    )
    return  // не создавать KaraokeProcess
}
// Иначе — старое поведение (создать процесс)
```

### Изменения в `KaraokeProcess.kt` (точка 2)

```kotlin
// около строки 1861, в prepareContext для KEY_BPM_FROM_FILE
KaraokeProcessTypes.KEY_BPM_FROM_FILE -> {
    // specs/126: если файл уже есть — НЕ запускаем docker, сразу применяем.
    if (song.applyKeyBpmFromFileIfExists(database)) {
        description = "Key Bpm from file (cache hit, no docker)"
        args = emptyList()  // нет docker-команд
        envs = emptyMap()
    } else {
        description = "Key Bpm from file"
        val (actionArgs, actionEnvs) = song.argsKeyBpmFinder()
        args = actionArgs
        envs = actionEnvs
    }
}
```

### Contracts

**Нет новых API endpoints** (фича внутренняя — поведение `HealthReport` и
`KaraokeProcess`). UI не меняется.

**SSE-канал**: `HEALTH_REPORTS` уже рассылает результаты (через
`recomputeAndBroadcast` в worker). После применения из файла нужно вручную
вызвать `HealthReport.recomputeAndBroadcast(songId, ...)` чтобы UI обновился.

### Quickstart validation

См. [quickstart.md](quickstart.md).

## Re-evaluation Constitution Check

- ✅ Все gates из первоначальной проверки сохраняются.
- ✅ Phase 1 design не нарушает constitution.
- ✅ Race-safety учтена (`saveToDbLocked` + одиночная проверка в `KaraokeProcess.loadList`).
- ✅ Single-flight учтена (через `applyKeyBpmFromFileIfExists` + `loadList`).

## Changelog

- **Pass 401** (2026-09-15): Initial. Автор: agent (Karaoke).
