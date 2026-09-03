# Research: Не сохраняется цензурированное имя песни в SongEdit (spec 302)

**Date**: 2026-09-03
**Spec**: [spec.md](spec.md)
**Branch**: `302-fix-censored-name-loss`
**Phase**: 0 — Outline & Research (resolves all NEEDS CLARIFICATION)

## Summary

Спека 302 имеет один открытый NEEDS CLARIFICATION (см. FR-011): **выбор
между B1 (`Map<String, String>`) и B2 (`@ModelAttribute DTO`)** для
рефактора `songs2Update`. Этот research фиксирует решение + 4 других
архитектурных решения, которые нужны для Phase 1 (data-model.md,
contracts/, quickstart.md).

## Decision 1 — FR-011: B1 (`Map<String, String>`) выбран

**Decision**: Использовать вариант **B1** — `@RequestParam Map<String, String> all`
в сигнатуре `songs2Update` + централизованный `SongUpdateMapper`.

**Rationale**:
1. **Минимизация ручной работы**: 95 `@RequestParam` (многословные,
   с `required = false`) → один `Map<String, String>` параметр.
   Сигнатура метода сокращается с ~250 строк до 1 строки.
2. **Корневая причина бага устраняется в принципе**: баг «фронт шлёт
   X, бэкенд не принимает» возникает только если есть два независимых
   списка (UI-поля + `@RequestParam`-параметры) и они рассинхронизированы.
   B1 устраняет второй список — теперь все параметры попадают в Map,
   а маппер сам решает, что с каждым делать.
3. **Type-safety компенсируется runtime-валидацией** (FR-012): маппер
   парсит `Int?`, `Long?`, `Boolean?`, enum'ы из строки с проверкой
   ошибок и возвратом HTTP 400 при невалидном значении. Это эквивалентно
   текущему поведению `@RequestParam Long?` (тоже бросает 400).
4. **Расширяемость**: добавление нового поля в Song требует одной
   строки в маппере (case в `when (key)`) + одна строка в whitelist
   если поле non-standard. Не нужно переписывать сигнатуру эндпоинта.
5. **Переиспользуемость**: `SongUpdateMapper` — singleton object,
   может быть переиспользован для будущих эндпоинтов (`/api/song/copy`,
   `/api/song/import`, и т.п.) или для других сущностей с похожей
   структурой (Author, Album — после spike).

**Alternatives considered**:

- **B2 (`@ModelAttribute SongUpdateRequestDto`)** — отклонён.
  - **За**: type-safety Spring binding, явный контракт для OpenAPI.
  - **Против**:
    - 95 полей в DTO = многословный boilerplate.
    - Добавление нового поля требует менять DTO + сигнатуру метода —
      то же, что без рефактора (bug может вернуться).
    - `@ModelAttribute` чувствителен к регистру — поведение аналогично
      `@RequestParam`, без выигрыша.
  - **Вердикт**: B2 не решает корневую причину бага архитектурно.

- **Pure spike с `@RequestBody Map<String, Object>`** — рассмотрен, отклонён.
  - **За**: типы сохраняются на JSON-уровне.
  - **Против**: требует смены протокола (JSON body вместо query-параметров),
    ломает FR-013 (обратная совместимость с внешними скриптами).
  - **Вердикт**: нарушает FR-013.

- **Полный рефактор на CQRS/Command-bus** — out of scope для bugfix-спеки,
  упоминается в Notes как возможное будущее развитие.

## Decision 2 — Архитектура `SongUpdateMapper`

**Decision**: Создать отдельный Kotlin-файл
`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/SongUpdateMapper.kt`
с singleton-object `SongUpdateMapper` и data-классом `SongUpdateApplyResult`.

**Структура файла**:

```kotlin
// Каркас (не финальный код — реализация в Phase 2/tasks.md)

data class SongUpdateApplyResult(
    val albumLinkValid: Boolean = true,
    val fileNameRenameError: String? = null,
    val freeChanged: Boolean = false,
    val idStatusChanged: Boolean = false,
    val baselineAutoFilled: Boolean = false,
)

object SongUpdateMapper {
    /**
     * Применяет параметры из запроса к загруженной Song.
     * Семантика 1:1 с текущим телом songs2Update (FR-014).
     */
    fun apply(
        song: Song,
        params: Map<String, String>,
        database: Database,
        storageService: StorageService,
        storageApiClient: StorageApiClient,
    ): SongUpdateApplyResult
}
```

**Фазы внутри `apply`**:

1. **Phase A — Special-case поля с бизнес-логикой**:
   - `fileName` (sanitize + collision + active-process check).
   - `albumId` (cross-author check через `Album.getAlbumById` + `Author.getAuthorById`).
   - `songType` (enum mapping через `SongType.entries.firstOrNull { ... }`).
   - Эти поля читаются из `params` напрямую (special-case) и применяются
     первыми, чтобы результат их проверок (например, `fileNameRenameError`)
     был доступен для финального return.

2. **Phase B — Standard string-поля**:
   - Все остальные `String?` параметры, маппящиеся в `fields[SongField.X]`
     через конвенцию camelCase → SONG_FIELD_NAME (например, `songName`
     → `SongField.NAME`, `songNameCensored` → `SongField.NAME_CENSORED`).
   - Реализация: lookup-table `Map<String, SongField>` строится один раз
     при инициализации object'а через reflection на `SongField.entries`.

3. **Phase C — Non-string типы**:
   - `Int?`, `Long?`, `Boolean?` — парсятся из строки в маппере.
   - Валидация: при `NumberFormatException` / `IllegalArgumentException`
     → бросает `BadRequestException("Invalid value for param X: '...' is not a number")`.
   - Парсинг делегирован helper'у `parseParam<T>(key, raw)` (generic).

4. **Phase D — Baseline**:
   - Если `songNameCensored` пустое и `songName` непустое → автозаполнение
     через `songName.censored(database)` (логика из `Song.kt:5364`,
     инкапсулирована здесь).

5. **Phase E — Возврат `SongUpdateApplyResult`**:
   - `freeChanged = song.free != freeBefore`
   - `idStatusChanged = song.idStatus != idStatusBefore`
   - `albumLinkValid = !albumIdConflict`
   - `fileNameRenameError = ...`

**Rationale**:
- Разделение ответственности: controller = HTTP/routing, mapper = маппинг.
- Переиспользуемость для будущих эндпоинтов.
- Легко тестировать в unit-тестах (без поднятия Spring context).
- KDoc coverage по Constitution § VI FR-006 — документируем mapper.

## Decision 3 — Размещение чек-скриптов

**Decision**: Чек-скрипты размещаются в `tools/` (как остальные
проектные скрипты), на чистом bash + awk/grep/sed (без Python/perl —
минимизация зависимостей).

**Файлы**:

| Файл | Назначение |
|---|---|
| `tools/check-songedit-field-coverage.sh` | FR-005: чек пары SongEdit ↔ /song/update |
| `tools/check-songedit-field-coverage.whitelist.yml` | FR-005: whitelist (~10 полей) |
| `tools/check-endpoint-field-coverage.sh` | FR-007: общий чек всех пар |
| `tools/check-endpoint-field-coverage.whitelist.yml` | FR-008: глобальный whitelist |
| `tools/endpoint-pairs.yml` | FR-007: список пар UI↔backend |
| `tools/cleanup-test-songs.sql` | NFR-006: откат тестовых данных |

**Алгоритм `check-songedit-field-coverage.sh`**:

1. Извлечь все `v-model="song.<key>"` из `SongEdit.vue` через `grep -oE`.
2. Извлечь все `@RequestParam` из `ApiController.songs2Update` через `grep -oE` + контекстный поиск setter'а.
3. Извлечь whitelist из yml (простой парсер без yq — через grep по `  "<key>":`).
4. Для каждого ключа из (1) проверить, что он есть в (2) ИЛИ в whitelist.
5. Если все ключи покрыты → exit 0 + `OK: N/N полей покрыты`.
6. Если есть uncovered → exit 1 + `MISSING: <key>` для каждого.

**Алгоритм `check-endpoint-field-coverage.sh`**:

1. Прочитать `tools/endpoint-pairs.yml` (простой yml-парсер).
2. Для каждой пары вызвать `check-songedit-field-coverage.sh` логику
   на нужном компоненте/эндпоинте (функция extractFields(компонент)
   + extractRequestParams(эндпоинт) + diff).
3. Глобальный whitelist `tools/check-endpoint-field-coverage.whitelist.yml`
   применяется последним (формат: `ComponentName/endpointName/fieldName: reason`).
4. Суммарный exit 0 если все пары зелёные, exit 1 если хоть одна нет.

**Rationale**:
- bash + awk — соответствует существующим скриптам в `tools/` (см.
  `baseline-stats.sh`, `check-eslint-baseline.sh`, и т.д.).
- Минимум зависимостей (только bash ≥4.0 + grep/awk/sed + yq если есть,
  fallback без yq через grep).
- Скрипты короткие (≈100-200 строк каждый), легко читаются и модифицируются.

## Decision 4 — Endpoint-pairs для FR-007

**Decision**: Начальный список пар в `tools/endpoint-pairs.yml`:

```yaml
pairs:
  - component: webvue3/src/components/Songs/edit/SongEdit.vue
    endpoint: /api/song/update
    method: POST
    controller: karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt
    controller_method: songs2Update

  # === MVP scope (эта спека) ===
  # Остальные пары добавляются в следующих раундах (post-MVP):
  # - AlbumEdit (если есть) ↔ /albums/updatealbum
  # - AuthorEdit (если есть) ↔ /authors/updateauthor
  # - SiteUserEdit.vue ↔ /api/siteuser/update (если есть)
  # - DictionariesTable.vue ↔ /api/dictionaries/update (если есть)
```

**Rationale**:
- MVP-чек покрывает основную пару (SongEdit ↔ /song/update), что
  достаточно для FR-005/006 и FR-007/008 (общий чек работает на
  списке пар, даже если там одна).
- FR-007 Acceptance Scenario #2: чек корректно ловит баги и в
  паре SongEdit ↔ /song/update (как `MISSING: songNameCensored`
  до фикса).
- Добавление новых пар — incremental, в следующих спеках
  (см. spec Out of Scope).

## Decision 5 — Интеграция в pre-commit и CI

**Decision**: Чек-скрипты добавляются в ОБА pre-commit и CI для
belt-and-suspenders (pre-commit для developer ergonomics, CI для
remote PR validation).

**Pre-commit hooks** (`.pre-commit-config.yaml` — добавляем 2 hook):

```yaml
  # === Field coverage: SongEdit ↔ /song/update ===
  - id: songedit-field-coverage
    name: SongEdit field coverage (SongEdit.vue ↔ /api/song/update)
    entry: bash tools/check-songedit-field-coverage.sh
    language: system
    pass_filenames: false
    files: '^webvue3/src/components/Songs/edit/SongEdit\.vue$|^karaoke-app/src/main/kotlin/.*ApiController\.kt$'
    stages: [pre-commit]

  # === Field coverage: все пары UI↔backend ===
  - id: endpoint-field-coverage
    name: Endpoint field coverage (все пары UI↔backend)
    entry: bash tools/check-endpoint-field-coverage.sh
    language: system
    pass_filenames: false
    files: '^(webvue3|karaoke-public)/.*\.(vue|js|ts)$|^karaoke-app/src/main/kotlin/.*\.kt$'
    stages: [pre-commit]
```

**CI workflow** (`.github/workflows/lint.yml` — добавляем job):

```yaml
  field-coverage:
    name: Field coverage (SongEdit + endpoint pairs)
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: SongEdit field coverage
        run: bash tools/check-songedit-field-coverage.sh
      - name: Endpoint field coverage
        run: bash tools/check-endpoint-field-coverage.sh
```

**Rationale**:
- Pre-commit = developer ergonomics (быстрый feedback до push).
- CI = authoritative gate (даже если разработчик использовал
  `--no-verify`, CI поймает).
- `files:` regex — hook срабатывает только когда меняется
  релевантный код (избегаем лишних запусков).

## Risks & Mitigations

| Risk | Severity | Mitigation |
|---|---|---|
| Рефактор B1 ломает специальную обработку `fileName` (sanitize/collision/active-process) | High | FR-014 explicitly сохраняет всю special-case логику в маппере. SC-009: integration-тест «до/после» поведения. |
| Парсинг нестроковых типов выдаёт HTTP 500 вместо HTTP 400 | Medium | FR-012 явно требует HTTP 400 с понятным сообщением. Тест: `idStatus=abc` → HTTP 400, не 500. |
| Чек-скрипт ломается на edge-case форматирования (например, `v-model="song.<key>"` с переносом строки) | Low | Алгоритм явно использует `grep -oE` multiline. Тест: разные варианты форматирования. |
| Маппер становится bottleneck на больших payload'ах | Low | O(N) по числу параметров, N≈95 — пренебрежимо. NFR-001 фиксирует. |
| Whitelist разрастается > 15 (сигнал «чек слишком шумный») | Low | Assumption в spec: переделать на AST-анализ. Out of scope для этой спеки. |
| После рефактора NFR-003 (observability: diff в логах) требует адаптации | Low | `RecordChangeMessage` уже работает через `getDiff(this, savedSong)` после `saveToDb()`. Не зависит от способа, которым поля были обновлены до `saveToDb()`. |

## Open Architectural Questions (deferred)

Эти вопросы не блокируют реализацию этой спеки, но могут стать
предметом отдельных spec'ов в будущем:

1. **Generic-критичный рефактор**: применить тот же подход (B1 + Mapper)
   к другим update-эндпоинтам (`/albums/updatealbum`, `/authors/updateauthor`,
   `/api/song/copyfieldsfromanother`). Это устранит целый КЛАСС багов
   (не только текущий). Out of scope для spec 302.
2. **AST-анализ вместо grep** для чек-скриптов — если whitelist
   превысит 15 полей. Можно использовать Kotlin compiler embeddable
   для AST-парсинга или `@RequestParam` extraction из compiled bytecode.
3. **JSON-body вместо query-параметров** для update — требует смены
   протокола, ломает обратную совместимость. Out of scope.
4. **CQRS/Command-bus** — глобальный архитектурный сдвиг. Out of scope.

## Conclusion

Все NEEDS CLARIFICATION разрешены:
- FR-011 (B1 vs B2) → **B1** выбран (см. Decision 1).
- Маппер размещается в `controllers/SongUpdateMapper.kt` (Decision 2).
- Чек-скрипты в `tools/`, чистый bash (Decision 3).
- Endpoint-pairs начинается с одной пары, расширяется инкрементально (Decision 4).
- Pre-commit + CI — belt-and-suspenders (Decision 5).

Phase 1 (data-model.md, contracts/, quickstart.md) может стартовать.
