# Data Model: Не сохраняется цензурированное имя песни в SongEdit (spec 302)

**Date**: 2026-09-03
**Spec**: [spec.md](spec.md)
**Research**: [research.md](research.md)

## Schema Changes (Database)

**Изменения в схеме БД: 0 (нет).**

Колонка `tbl_songs.song_name_censored TEXT NOT NULL DEFAULT ''` уже
существует (создана в `specs/277-song-name-censored`,
миграция `NNN-song-name-censored.sql`). `recordhash`-триггер для
`song_name_censored` уже включён в общий триггер `tbl_songs`.

`getDiff()` (`Song.kt:6864`) уже умеет включать `song_name_censored`
в UPDATE SET, если значение изменилось (см. [research.md Decision 1](#)).

**Verification**:
```sql
-- Проверить, что колонка существует
SELECT column_name, data_type, character_maximum_length, is_nullable, column_default
FROM information_schema.columns
WHERE table_name = 'tbl_songs' AND column_name = 'song_name_censored';
-- Ожидаем: TEXT, 512 chars (если есть constraint), NOT NULL, '' (default)

-- Проверить, что recordhash-триггер покрывает song_name_censored
SELECT trigger_name, event_manipulation, action_timing
FROM information_schema.triggers
WHERE event_object_table = 'tbl_songs';
-- Ожидаем: триггер с event_manipulation = 'UPDATE' (или 'INSERT'/'DELETE' тоже),
-- в теле функции упоминается song_name_censored.
```

## New Files

### Backend (Kotlin)

| Файл | Назначение | Размер (ожидаемый) |
|---|---|---|
| `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/SongUpdateMapper.kt` | Централизованный маппер параметров → Song (Decision 2) | ~200 строк |

**SongUpdateMapper.kt** структура:
- `data class SongUpdateApplyResult(...)` — результат применения (albumLinkValid, fileNameRenameError, freeChanged, idStatusChanged, baselineAutoFilled).
- `object SongUpdateMapper { fun apply(...): SongUpdateApplyResult }` — singleton.
- Приватные helpers: `applySpecialFields`, `applyStandardStringFields`, `applyNonStringFields`, `applyBaseline`, `parseParam<T>`.
- Приватная `lookupTable: Map<String, SongField>` — инициализируется lazy через reflection на `SongField.entries`.

**KDoc coverage**: 100% (Constitution § VI FR-006 — публичные API
обязаны иметь KDoc + `@see` ссылку на per-feature документ).

### Tools (bash + yaml)

| Файл | Назначение |
|---|---|
| `tools/check-songedit-field-coverage.sh` | FR-005: чек пары SongEdit ↔ /song/update |
| `tools/check-songedit-field-coverage.whitelist.yml` | FR-005: whitelist ~10 нестандартных setter'ов (Q4→B) |
| `tools/check-endpoint-field-coverage.sh` | FR-007: общий чек всех пар |
| `tools/check-endpoint-field-coverage.whitelist.yml` | FR-008: глобальный whitelist |
| `tools/endpoint-pairs.yml` | FR-007: список пар UI↔backend (MVP: 1 пара) |
| `tools/cleanup-test-songs.sql` | NFR-006: откат тестовых данных |

### Documentation (markdown)

| Файл | Назначение |
|---|---|
| `docs/features/song-edit-and-censored.md` | FR-009: новый per-feature документ |
| `specs/277-song-name-censored/spec.md` | FR-010: обновлённый Acceptance Scenario для US-2 |

### Pre-commit + CI

| Файл | Изменение |
|---|---|
| `.pre-commit-config.yaml` | +2 hooks (songedit-field-coverage, endpoint-field-coverage) |
| `.github/workflows/lint.yml` | +1 job (field-coverage) |

## Modified Files

### Backend (Kotlin)

| Файл | Изменение |
|---|---|
| `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt` | FR-011: рефактор `songs2Update` — 95 `@RequestParam` → 1 `@RequestParam Map<String, String> all` + вызов `SongUpdateMapper.apply(...)`. Тело метода сокращается с ~250 строк до ~30. |

**Сигнатура ДО** (текущая):
```kotlin
@PostMapping("/song/update")
@ResponseBody
fun songs2Update(
    @RequestParam(required = false) id: String,
    @RequestParam(required = false) rootFolder: String?,
    @RequestParam(required = false) fileName: String?,
    // ... ещё ~90 @RequestParam
    @RequestParam(required = false) warning: String?,
): SongUpdateResultDto {
    // ~250 строк логики
}
```

**Сигнатура ПОСЛЕ** (после FR-011):
```kotlin
@PostMapping("/song/update")
@ResponseBody
fun songs2Update(
    @RequestParam all: Map<String, String>,
): SongUpdateResultDto {
    val songId: Long = all["id"]?.toLongOrNull()
        ?: throw BadRequestException("Missing required param 'id'")
    val song = Song.loadFromDbById(songId, WORKING_DATABASE, storageService, storageApiClient)
        ?: throw NotFoundException("Song $songId not found")

    val applyResult = SongUpdateMapper.apply(
        song = song,
        params = all,
        database = WORKING_DATABASE,
        storageService = storageService,
        storageApiClient = storageApiClient,
    )

    song.saveToDb()
    song.saveToFile()
    if (applyResult.freeChanged || applyResult.idStatusChanged) notifyStatsDirty()

    return SongUpdateResultDto(
        albumLinkValid = applyResult.albumLinkValid,
        fileNameRenameError = applyResult.fileNameRenameError,
    )
}
```

**Поведение**: 1:1 с текущим (FR-014). Все edge cases, special-case
обработка (fileName, albumId, songType), baseline-логика — всё
инкапсулировано в `SongUpdateMapper.apply`.

### Documentation (markdown)

| Файл | Изменение |
|---|---|
| `specs/277-song-name-censored/spec.md` | FR-010: добавить AS-5 «backend ДОЛЖЕН принимать `songNameCensored` через `Map<String, String> all` (или `@RequestParam`)» + ссылка на spec 302 |

## Entities (Reference)

Сущности уже описаны в [spec.md → Key Entities](spec.md#key-entities-include-if-feature-involves-data).
Здесь — только подтверждение, что schema изменений нет:

| Entity | Status | Notes |
|---|---|---|
| `tbl_songs.song_name_censored` | **unchanged** | TEXT NOT NULL DEFAULT '', recordhash-триггер есть |
| `SongDTO.songNameCensored` | **unchanged** | Уже в JSON-контракте публичного API |
| `SongEdit.vue.song.songNameCensored` | **unchanged** | `v-model` уже на `<input>` |
| `Song.songNameCensored` (Kotlin) | **unchanged** | Через `fields[SongField.NAME_CENSORED]` |
| `SongUpdateMapper` | **new** | Singleton object, Kotlin |
| `SongUpdateApplyResult` | **new** | Data class, Kotlin |
| `tools/endpoint-pairs.yml` | **new** | YAML config |
| `tools/check-songedit-field-coverage.whitelist.yml` | **new** | YAML config |
| `tools/check-endpoint-field-coverage.whitelist.yml` | **new** | YAML config |

## Migration Plan (for deployment)

**Не требуется** — schema не меняется, маппер и чек-скрипты
добавляются без миграций БД.

**Deployment order** (suggested):
1. Merge PR с кодом (маппер + рефактор songs2Update + чек-скрипты + pre-commit/CI).
2. CI прогоняет 8 чеков (7 существующих + field-coverage).
3. Деплой на admin-машину (LOCAL-БД): пересобрать `karaoke-app:bootJar`,
   перезапустить контейнер.
4. Прогон SC-001 (10 ручных правок через SongEdit на LOCAL-БД).
5. Прогон NFR-006 (cleanup через `tools/cleanup-test-songs.sql`).
6. После одобрения пользователем → `tools/tracker.sh mark-review 52`.

**Risk**: рефактор B1 — критическое изменение `songs2Update`. Митигация:
FR-009/FR-014 (integration-тест «до/после», SC-009/010).
