# Implementation Plan: Ограничение лимита для Thymeleaf /statbysong (FR-107)

**Branch**: `272-statbysong-pagination` | **Date**: 2026-08-26 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/272-statbysong-pagination/spec.md`

## Summary

Реализует Tier-3 P2 оптимизацию FR-007 из parent спеки [241-db-storage-perf-audit](../241-db-storage-perf-audit/spec.md):

1. **`MainController.doStatBySong`**: `limit = 100_000` → `limit = 1000` (FR-001).
2. **`StatsByEvents.getStatBySong`**: добавить safety-guard `MAX_LIMIT = 1000`, `MIN_LIMIT = 1` через `coerceIn` (FR-002).
3. **`statbysong.html`**: добавить ненавязчивый баннер «Показано топ-1000 из ~N доступных» с числом через `getStatBySongCount` (FR-003, FR-004).

Effect:
- `/statbysong` загружается за <2 сек (vs десятки секунд/минуты baseline).
- `StatsController?pageSize=100000` тоже защищён через safety-guard.

## Technical Context

**Язык**: Kotlin 2.x + Spring Boot 3.x + Thymeleaf (как `MainController` и `StatBySong`).
**БД**: прямой JDBC к `WORKING_DATABASE` (на проде — прод-БД, на admin — локальная).
**UI**: Thymeleaf 3 + Bootstrap 4 (уже в `statbysong.html`).

### Архитектурное решение: почему safety-guard в `getStatBySong`

`StatsByEvents.getStatBySong` — единая точка вызова SQL для `/statbysong` И `/api/stats/by-song`.
Если поставить guard только в `MainController`, то `StatsController?pageSize=100000` всё равно
сделает огромный SELECT. Поэтому guard должен быть в самой функции — **single point of truth** для
лимита. Это:

1. Защищает оба endpoint'а (Thymeleaf + REST).
2. Любой будущий код автоматически защищён.
3. Невозможно случайно вызвать с большим лимитом (защита от copy-paste ошибок).

## Constitution Check (NON-NEGOTIALE принципы)

- **§ II Сырой JDBC + дифф по хэшам**: PASS. Никаких изменений в стеке доступа к БД — `getStatBySong`
  остаётся прямым JDBC.
- **§ VI Code Standards**: PASS. KDoc 100% на изменённые методы (FR-005).
- **Git workflow**: PASS. Ветка `272-statbysong-pagination`, PR через `gh pr create`.

## Project Structure

```
karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/
└── MainController.kt                      # MODIFY: limit 100_000 → 1000 + KDoc

karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/
└── StatBySong.kt                          # MODIFY: getStatBySong safety-guard + KDoc

karaoke-web/src/main/resources/templates/
└── statbysong.html                        # MODIFY: баннер с totalCount

livedocs/features/
└── 272-statbysong-pagination.md           # NEW: per-feature документ (FR-014)

specs/272-statbysong-pagination/
├── spec.md                                # NEW
├── plan.md                                # NEW (этот файл)
├── tasks.md                               # NEW
└── checklists/requirements.md             # NEW
```

## Implementation Steps

### 1. `MainController.kt:486-496` — `limit = 100_000` → `limit = 1000`

**Diff (концептуально)**:

```kotlin
// before:
model.addAttribute(
    "stats",
    com.svoemesto.karaokeapp.model.StatsByEvents
        .getStatBySong(database = WORKING_DATABASE, limit = 100_000),
)

// after:
model.addAttribute(
    "stats",
    com.svoemesto.karaokeapp.model.StatsByEvents
        .getStatBySong(database = WORKING_DATABASE, limit = 1000),
    // FR-107: см. specs/272-statbysong-pagination — limit ограничен 1000 для UI.
    // Полная выгрузка — через /api/stats/by-song?page=N&pageSize=50 (REST API с пагинацией).
    // getStatBySong сам clamp'ит limit через MAX_LIMIT safety-guard.
)
```

Также добавляю KDoc на метод с ссылкой на FR-107 (FR-005).

### 2. `StatBySong.kt:449-541` — safety-guard `MAX_LIMIT = 1000`, `MIN_LIMIT = 1`

**Diff (концептуально)**:

```kotlin
// В companion object (или прямо в object StatsByEvents):
const val MAX_STAT_BY_SONG_LIMIT = 1000
const val MIN_STAT_BY_SONG_LIMIT = 1

// В getStatBySong:
// before:
fun getStatBySong(
    database: KaraokeConnection = WORKING_DATABASE,
    limit: Int = 50,
    offset: Int = 0,
): List<StatBySongDto> {
    val result: MutableList<StatBySongDto> = mutableListOf()
    val sql = """...limit $limit offset $offset..."""

// after:
fun getStatBySong(
    database: KaraokeConnection = WORKING_DATABASE,
    limit: Int = 50,
    offset: Int = 0,
): List<StatBySongDto> {
    val result: MutableList<StatBySongDto> = mutableListOf()
    // FR-002: safety-guard для защиты от огромных limit (copy-paste ошибки, DoS через REST).
    // Любой limit > 1000 или <= 0 нормализуется до безопасного диапазона.
    val safeLimit = limit.coerceIn(MIN_STAT_BY_SONG_LIMIT, MAX_STAT_BY_SONG_LIMIT)
    val sql = """...limit $safeLimit offset $offset..."""
```

Также обновляю KDoc с упоминанием safety-guard (FR-005).

### 3. `statbysong.html` — баннер с totalCount

**Diff (концептуально, под `<h2>` или `<h3>` заголовком)**:

```html
<!-- before (нет баннера): -->
<h2>Статистика по песням</h2>
<table class="table">...

<!-- after: -->
<h2>Статистика по песням</h2>
<div class="alert alert-info" role="alert">
    Показано топ-1000 из ~<span th:text="${totalCount}">N</span> доступных.
    Для полной выгрузки используйте
    <a href="/api/stats/by-song?page=1&amp;pageSize=50" target="_blank">/api/stats/by-song</a>
    с пагинацией.
</div>
<table class="table">...
```

Также передаю `totalCount` из контроллера:
```kotlin
model.addAttribute("totalCount",
    com.svoemesto.karaokeapp.model.StatsByEvents.getStatBySongCount(WORKING_DATABASE))
```

### 4. `livedocs/features/272-statbysong-pagination.md` — NEW

Per-feature документ (FR-014). Содержит:
- Summary / Why (FR-107 parent спеки 241, Tier-3).
- **Effect**: <2 сек загрузка /statbysong.
- **Safety-guard**: `MAX_LIMIT = 1000` через `coerceIn`.
- **Баннер**: с `totalCount`.
- Cross-links: `../241-db-storage-perf-audit.md`, `MainController.kt`, `StatBySong.kt`, `statbysong.html`.

### 5. CI checks (последовательность по AGENTS.md § «Обязательная проверка после ЛЮБОГО изменения кода»)

```bash
./gradlew :karaoke-web:compileKotlin :karaoke-app:compileKotlin --parallel
./gradlew :karaoke-web:ktlintCheck :karaoke-app:ktlintCheck
bash tools/check-kdoc-coverage.sh
pre-commit run --all-files
```

## Risks & Mitigations

| Риск | Митигация |
|------|-----------|
| `limit = 1000` обрежет данные, которые администратор хочет видеть в UI | Баннер явно сообщает «показано топ-1000», ссылка на REST API с пагинацией. Для полных данных — `/api/stats/by-song`. |
| `coerceIn(1, 1000)` для `limit = 0` или отрицательных даст `1`, не `50` (default) | Текущая семантика: `limit = 50` (default). `coerceIn` для `0` даст `1`. Это не критично — 1 строка лучше, чем N запрос с `LIMIT 0` (вернёт 0 строк). Альтернатива — отдельная проверка `if (limit < 1) 50` (default). |
| Thymeleaf баннер ломает вёрстку на мобильных | Bootstrap 4 `alert` responsive по умолчанию. Проверим в browser devtools при smoke-тесте. |
| `StatsByEvents.getStatBySongCount` тоже становится «тяжёлым» при большом `tbl_events` | На текущем проде — Index Only Scan на `tbl_events_song_id_index` (см. миграцию 41, FR-110). На больших объёмах (>1M events) — миллисекунды, не секунды. |
| Breaking change для существующих клиентов Thymeleaf | Endpoint `/statbysong` — внутренний admin-tool, нет внешних клиентов. Изменение `limit` — улучшение UX, ничего не ломает. |
| `coerceIn` обрезает `limit` молча, без предупреждения | KDoc явно упоминает safety-guard. Опционально — `println` warning при clamp (но это избыточно для SQL-библиотечной функции). |

## Definition of Done

- [ ] `MainController.kt:486-496` — `limit = 1000` + KDoc с FR-ссылкой.
- [ ] `StatBySong.kt:449-541` — safety-guard `coerceIn(MIN, MAX)` + KDoc.
- [ ] `statbysong.html` — баннер `alert-info` с `totalCount`.
- [ ] `MainController.doStatBySong` передаёт `totalCount` в модель.
- [ ] LiveDoc создан в `livedocs/features/272-statbysong-pagination.md`.
- [ ] Все 5 спецификационных файлов созданы.
- [ ] Все 7 CI gates PASS (ktlint, KDoc coverage, LiveDocs, ...).
- [ ] PR создан и замержен в master.

## Next Steps

После мёрджа — обновить `specs/241-db-storage-perf-audit/tasks.md`:
- T012.3 → `[x] FR-107 реализован (PR #...)`.
- Обновить `livedocs/architecture-notes.md` §Pass 241 — отметить FR-107 как done.

Также — **runtime-валидация** (опционально, делается пользователем):
```sql
EXPLAIN ANALYZE SELECT e.song_id, ... GROUP BY e.song_id ... LIMIT 1000 OFFSET 0;
-- Должен показать Index Scan + HashAggregate на 1000 строках, миллисекунды.
```