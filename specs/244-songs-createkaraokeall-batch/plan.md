# Implementation Plan: Батч-загрузка песен в getSongsCreateKaraokeAll

**Branch**: `244-songs-createkaraokeall-batch` | **Date**: 2026-08-26 | **Spec**: [spec.md](spec.md)
**Parent**: [`specs/241-db-storage-perf-audit/spec.md`](../241-db-storage-perf-audit/spec.md) — Tier-1 / FR-103

**Input**: Feature specification from `/specs/244-songs-createkaraokeall-batch/spec.md`

## Summary

Заменить N+1 в `ApiController.getSongsCreateKaraokeAll` (строки 3664-3778): `ids.forEach { id -> Song.loadFromDbById(id) }` → пакетный `Song.loadListFromDbByIds(ids.chunked(25))`. Создать helper `loadSongsBatch` для инкапсуляции батч-логики. Сохранить per-song side-effect (`KaraokeProcess.createProcess`) и SSE-уведомление. На 100 ID: 100 SQL → 4 SQL.

## Technical Context

**Language/Version**: Kotlin 1.x (JDK 17), Spring Boot 2.x/3.x (karaoke-app, admin)
**Primary Dependencies**: `Song.loadListFromDbByIds` (уже есть, используется в `SongSyncTarget.loadByIds`); стандартный `chunked(25)` из Kotlin
**Storage**: PostgreSQL `tbl_songs` (через `WORKING_DATABASE` = `Connection.local()`)
**Testing**: ручное на admin-машине через webvue3-таблицу Songs
**Target Platform**: Linux server (admin-машина; на проде karaoke-web этот endpoint НЕ вызывается)
**Project Type**: library/multi-module Gradle
**Performance Goals**: ≤5 SQL при N=100 (SC-001); ≤41 SQL при N=1000 (SC-002); latency ≤3 сек (SC-003)
**Constraints**: backward-compat endpoint'а (FR-007); сохранить side-effect (FR-004); chunk_size=25 для Song (validated by `SongSyncTarget.rowChunkSize`)
**Scale/Scope**: admin-сценарий, типично 10-100 песен за раз; редко 500-1000 (массовый импорт)

## Constitution Check

- ✅ **Principle I**: не затрагивается.
- ✅ **Principle II (Сырой JDBC + дифф по хэшам)**: ИСПРАВЛЯЕТ нарушение. Текущий код — N+1 с per-id SELECT; после рефакторинга — `WHERE id IN (...)` через `Song.loadListFromDbByIds`. Точно соответствует пункту «пакетно WHERE id IN».
- ✅ **Principle III-VIII**: не затрагиваются.

**Constitution Check: PASS**.

## Project Structure

```text
specs/244-songs-createkaraokeall-batch/
├── plan.md              # Этот файл
├── spec.md              # Feature specification (FR-103 из parent)
├── checklists/
│   └── requirements.md  # 16/16 ✅
└── tasks.md             # Phase 2

karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/
└── ApiController.kt     # ИЗМЕНЕНИЕ: getSongsCreateKaraokeAll + helper loadSongsBatch
```

**Structure Decision**: Single project. Точечное изменение в `ApiController.kt`.

## Implementation Approach

### Phase 1: Helper `loadSongsBatch`

Добавить в `ApiController` (или как private-метод, или вынести в `Song.kt` companion):

**Вариант A** (private в ApiController — минимальные изменения):
```kotlin
private fun loadSongsBatch(
    ids: List<Long>,
    database: KaraokeConnection,
    storageService: KaraokeStorageService,
    storageApiClient: StorageApiClient,
): Map<Long, Song> {
    if (ids.isEmpty()) return emptyMap()
    return ids
        .chunked(SELECT_CHUNK_SIZE)
        .flatMap { chunk ->
            Song.loadListFromDbByIds(
                ids = chunk,
                database = database,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        }.associateBy { it.id }
}

private const val SELECT_CHUNK_SIZE = 25  // Same as SongSyncTarget.rowChunkSize
```

**Вариант B** (utility в `Song.kt` companion — переиспользуемо для других фич):
```kotlin
// В Song.kt:
fun loadListByIdsBatch(
    ids: List<Long>,
    database: KaraokeConnection,
    storageService: KaraokeStorageService,
    storageApiClient: StorageApiClient,
    chunkSize: Int = 25,
): Map<Long, Song> = ...
```

**Рекомендация**: Вариант A для этой фичи (минимальный scope). Вариант B — отдельная фича для Tier-2 (parent спека, H-14).

### Phase 2: Изменение `getSongsCreateKaraokeAll`

```kotlin
@PostMapping("/songs/createkaraokeall")
@ResponseBody
fun getSongsCreateKaraokeAll(
    @RequestParam songsIds: String,
    @RequestParam(required = false) priorLyrics: String? = "10",
    @RequestParam(required = false) priorKaraoke: String? = "10",
    @RequestParam(required = false) priorChords: String? = "",
    @RequestParam(required = false) priorMelody: String? = "",
    @RequestParam(required = false) priorDemo: String? = "",
    @RequestParam(required = false) threadId: String? = "0",
) {
    var result = false
    songsIds.let {
        // FR-005 (опционально): distinct() для предотвращения дубликатов
        val ids = songsIds
            .split(";")
            .map { it }
            .filter { it != "" }
            .map { it.toLong() }
            .distinct()  // ← новое: убираем дубликаты

        // БАТЧ-ЗАГРУЗКА: 1..N/25 SQL вместо N
        val songsById = loadSongsBatch(
            ids = ids,
            database = WORKING_DATABASE,
            storageService = storageService,
            storageApiClient = storageApiClient,
        )

        val createLyrics = priorLyrics != "" && priorLyrics != null
        val createKaraoke = priorKaraoke != "" && priorKaraoke != null
        val createChords = priorChords != "" && priorChords != null
        val createMelody = priorMelody != "" && priorMelody != null
        val createDemo = priorDemo != "" && priorDemo != null

        ids.forEach { id ->
            val song = songsById[id]  // O(1) lookup
            song?.let {
                if (createLyrics) KaraokeProcess.createProcess(it, KaraokeProcessTypes.RENDER_MP4_LYRICS, true, priorLyrics!!.toInt(), threadId = threadId?.toInt() ?: 0, context = mapOf("version" to RenderVersion.LYRICS.name))
                if (createKaraoke) KaraokeProcess.createProcess(it, KaraokeProcessTypes.RENDER_MP4_KARAOKE, true, priorKaraoke!!.toInt(), threadId = threadId?.toInt() ?: 0, context = mapOf("version" to RenderVersion.KARAOKE.name))
                if (createChords) KaraokeProcess.createProcess(it, KaraokeProcessTypes.RENDER_MP4_CHORDS, true, priorChords!!.toInt(), threadId = threadId?.toInt() ?: 0, context = mapOf("version" to RenderVersion.CHORDS.name))
                if (createMelody) KaraokeProcess.createProcess(it, KaraokeProcessTypes.RENDER_MP4_TABS, true, priorMelody!!.toInt(), threadId = threadId?.toInt() ?: 0, context = mapOf("version" to RenderVersion.TABS.name))
                if (createDemo) KaraokeProcess.createProcess(it, KaraokeProcessTypes.RENDER_MP4_DEMO, true, priorDemo!!.toInt(), threadId = threadId?.toInt() ?: 0, context = mapOf("version" to RenderVersion.DEMO.name))
            }
        }
        result = ids.isNotEmpty()  // ← FR: было result = true (без проверки)
    }

    // SSE-уведомление — БЕЗ изменений
    if (result) { ... }
}
```

### Phase 3: Решение по FR-005 (distinct)

**Вопрос**: добавлять ли `.distinct()`?

**Pro**:
- Убирает дублирующие INSERT в `tbl_processes` (SC-007).
- Strict improvement (нет downside).

**Con**:
- Минимальное изменение поведения: если `songsIds = "1;1;2"` — раньше создавалось 2 процесса для песни 1, теперь только 1.
- Может маскировать баг в клиентском коде (если UI отправляет дубликаты).

**Рекомендация**: принять FR-005 с `.distinct()` по умолчанию (strict improvement). Если пользователь против — оставить как есть.

## Risks & Mitigations

| Риск | Вероятность | Митигация |
|------|-------------|-----------|
| `Song.loadListFromDbByIds` имеет другую сигнатуру | Низкая | Проверить в Phase 0 (preconditions). |
| Изменение `result = true` → `result = ids.isNotEmpty()` меняет поведение | Средняя | Это улучшение: раньше даже при пустом `songsIds` метод возвращал success. Сейчас — корректно. |
| `distinct()` пропускает дубликаты (SC-007) | Низкая | Это intentional. |
| `songsById[id]` = null для несуществующих ID | Средняя | Текущий код `song?.let { ... }` уже корректно обрабатывает. |

## Out-of-Scope

- Батчинг INSERT в `tbl_processes` (parent спека, Tier-3).
- Изменение других `forEach { loadFromDbById }` (MainController.kt, NewsTemplateController.kt, ExportAlignmentDataset.kt).
- Замена `loadFromDbById` в других admin-контроллерах (H-14 в parent).
- Изменение `KaraokeProcess.createProcess`.

## Complexity Tracking

*Нет нарушений Constitution Check.*

## Verification Plan

### До деплоя

1. Сделать снэпшот `pg_log` за 24 часа с фильтром `query LIKE '%tbl_songs WHERE id=%'` (per-id SELECT).
2. Подсчитать количество таких запросов (baseline для SC-006).

### После деплоя

1. Через webvue3-таблицу Songs выбрать 100 песен → нажать «Создать караоке для всех».
2. Замерить latency (SC-003): ожидание ≤ 3 сек.
3. Проверить в `pg_log`: число SQL к `tbl_songs` должно быть ~4 (а не 100).
4. Проверить, что все 100 `KaraokeProcess.createProcess` вызовов состоялись (через webvue3-таблицу Processes).
5. **Regression**: убедиться, что для песен с RENDER side-effect (если есть такие в выборке) процессы создаются (SC-005).

### Acceptance (mapping)

- **SC-001/SC-002**: docker logs / `pg_log`.
- **SC-003**: stopwatch на admin UI.
- **SC-004**: code-review (метрика цикломатики).
- **SC-005**: regression-тест через admin UI.
- **SC-006**: сравнение `pg_log` до/после.

## Timeline Estimate

- Phase 1 (helper): 15 мин.
- Phase 2 (изменение метода): 30 мин.
- Phase 3 (FR-005 distinct): 5 мин.
- **Итого: ~50 мин кодинга**.
- Тестирование: 20 мин.

## Definition of Done

- [ ] FR-001 … FR-007 реализованы.
- [ ] SC-001/SC-002/SC-003/SC-005/SC-006/SC-007 измеримы и подтверждены.
- [ ] ktlintCheck + compile проходят.
- [ ] PR создан через `gh pr create --base master`.
- [ ] CI 8/8 PASS.
- [ ] Deploy на admin-машину + ручное тестирование через webvue3.

## Next Step

→ `/speckit.tasks specs/244-songs-createkaraokeall-batch`.
