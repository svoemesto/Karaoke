# Research: 126 — Поиск тональности из существующего `[key].json`

> Сгенерировано Stage 3 (`/speckit-plan`) — Phase 0.

## Decisions

### D-001. Где проверять файл (clarification #126-1)

**Decision**: Проверка в **обоих** местах — `HealthReport.solutionActions` (точка 1) и
`KaraokeProcess.prepareContext` для типа `KEY_BPM_FROM_FILE` (точка 2).

**Rationale**: Защита от всех call-sites. HealthReport — основной путь
(«Исправить всё» в UI). KaraokeProcess — slow path (ручные запуски через
`/api/process/...`, миграции, batch-скрипты).

**Alternatives**:
- ❌ Только в HealthReport: не защищает ручные вызовы.
- ❌ Только в KaraokeProcess: HealthReport всё равно показывает ERROR
  до того, как процесс дойдёт до prepareContext.

### D-002. Helper `applyKeyBpmFromFileIfExists` — instance fun или companion?

**Decision**: **Instance fun** на `Song`.

**Rationale**: Нужен доступ к `pathToFileKeyBpmFinder` (instance), `fields`
(instance), `saveToDbLocked()` (instance), `KSS_APP` (top-level). Это всё
state песни, не статический helper. Companion object даст только parse — а
parse логика уже в `getKeyBpmFromFile`.

**Alternatives**:
- ❌ Companion `KeyBpmFromFileCache.applyIfExists(...)` — много параметров
  передавать (song, database, log), instance fun чище.

### D-003. SLF4J-категория

**Decision**: `infra.cache.keybpm`.

**Rationale**: Семантически похоже на `infra.cache.hrpool` (Pass 128).
Помогает фильтровать логи при диагностике.

### D-004. Возвращать `Boolean` или `KeyBpmFromFileResult`?

**Decision**: `Boolean` (`true` = применён, `false` = нужно запускать docker).

**Rationale**: Минимальный API. Если в будущем понадобится `applied` / `file_missing` /
`file_invalid` — легко расширить до enum без breaking change в call-sites
(только в helper).

### D-005. Пометить процесс как DONE или просто не создавать?

**Decision**: В точке 1 (HealthReport) — **не создавать** процесс вообще.
В точке 2 (KaraokeProcess.prepareContext) — создать процесс с `args=emptyList()`,
и в worker'е — выполнится пустой args → сразу DONE.

**Rationale**: В точке 1 процесс ещё не создан → можно просто не создавать.
В точке 2 процесс уже создаётся (вызов пришёл через `createProcess`) →
нужно его корректно завершить, чтобы UI не висел с WAITING.

### D-006. Какие тесты добавить?

**Decision**: 4 unit-теста:
1. Файл есть, валиден → возвращает `true`, `song.key`/`song.bpm` обновляются.
2. Файла нет → возвращает `false`, ничего не меняется.
3. Файл есть, невалиден (`null` поля) → возвращает `false`.
4. Файл есть, битый JSON → возвращает `false`.

**Rationale**: Покрывают все 4 ветки (success, missing, invalid-fields, parse-error).
Race-condition test — отдельная задача (требует двух потоков с реальным БД).

## Knowledge References

- [`knowledge/domains/catalog/components/song-entity.md`](../../knowledge/domains/catalog/components/song-entity.md)
  — `Song` entity, `fields`, `saveToDbLocked()`.
- [`knowledge/domains/integration/components/song-public-dto.md`](../../knowledge/domains/integration/components/song-public-dto.md)
  — публичное поле `key` в `SongPublicDto`.
- [`knowledge/domains/monitoring/components/log-categories.md`](../../knowledge/domains/monitoring/components/log-categories.md)
  — соглашение об именовании SLF4J-категорий (новое `infra.cache.keybpm`).
- **`knowledge/domains/processing/components/key-bpm-from-file.md`** (новый, FR-003)
  — будет создан в рамках этой задачи.

## Files Touched

**Modify**:
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt` — добавить `applyKeyBpmFromFileIfExists`.
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReport.kt` — точка 1 (около строки 1418).
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcess.kt` — точка 2 (около строки 1861).

**Create**:
- `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/KeyBpmFromFileCacheTest.kt` — unit-тесты.
- `knowledge/domains/processing/components/key-bpm-from-file.md` — Living Docs.

## Risks

1. **Race**: `applyKeyBpmFromFileIfExists` + `KaraokeProcess.createProcess` — между ними может
   вклиниться SongEdit. **Mitigation**: `saveToDbLocked()` атомарно берёт row-level lock.
2. **Параллельный worker**: если worker уже исполняет `KEY_BPM_FROM_FILE` для этой песни
   (в HealthReport thread), а другой поток дёрнул `applyKeyBpmFromFileIfExists` — оба попробуют
   `saveToDbLocked`. **Mitigation**: `saveToDbLocked` сериализует через SELECT FOR NO KEY UPDATE.
3. **Битый файл**: docker сам иногда пишет невалидный JSON (если прервался посередине).
   **Mitigation**: `parseFromString` ловит исключение, возвращает `false` → fallback на docker.
