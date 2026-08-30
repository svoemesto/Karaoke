---
status: Active
slug: 272-statbysong-pagination
related:
  - ../domain/catalog.md
  - ../architecture/L3-components.md
  - ../../specs/272-statbysong-pagination/spec.md
  - 241-db-storage-perf-audit
  - 270-db-indexes-verification
---

# 272 — Ограничение лимита для Thymeleaf /statbysong (LiveDoc)

> Drill-down — [specs/272-statbysong-pagination/spec.md](../../specs/272-statbysong-pagination/spec.md).
> Parent — [241-db-storage-perf-audit](241-db-storage-perf-audit.md) — Tier-3 / FR-007.

## Что делает

Ограничивает `limit` для Thymeleaf `/statbysong` с `100_000` до `1000` + добавляет safety-guard
`coerceIn(1, 1000)` в `StatsByEvents.getStatBySong` + ненавязчивый баннер с `totalCount` и ссылкой
на REST API `/api/stats/by-song` для полной выгрузки.

## Effect

- **Время загрузки `/statbysong`**: минуты → секунды (SQL с `LIMIT 1000` + Index Scan + HashAggregate)
- **DoS-защита**: `StatsController?pageSize=100000` автоматически clamp'ится до 1000 (через safety-guard)
- **UX**: баннер явно сообщает об ограничении + даёт ссылку на REST API с пагинацией

## Реализация

### 1. `MainController.doStatBySong` (`karaoke-web/.../controllers/MainController.kt:486`)

```kotlin
// Было:
.getStatBySong(database = WORKING_DATABASE, limit = 100_000)

// Стало (FR-001):
.getStatBySong(database = WORKING_DATABASE, limit = 1000)
// + передаём totalCount в модель для баннера (FR-004):
model.addAttribute("totalCount", StatsByEvents.getStatBySongCount(WORKING_DATABASE))
```

### 2. `StatsByEvents.getStatBySong` (`karaoke-app/.../model/StatBySong.kt:455`)

```kotlin
// Константы safety-guard (FR-002):
private const val MAX_STAT_BY_SONG_LIMIT = 1000
private const val MIN_STAT_BY_SONG_LIMIT = 1

// Внутри функции:
val safeLimit = limit.coerceIn(MIN_STAT_BY_SONG_LIMIT, MAX_STAT_BY_SONG_LIMIT)
// ... limit $safeLimit offset $offset ...
```

**Это единая точка clamp'а** — защищает оба endpoint'а:
- `/statbysong` (Thymeleaf, hardcoded `limit=1000`)
- `/api/stats/by-song?pageSize=...` (REST, любое значение через query param)

### 3. `statbysong.html` — баннер

```html
<!-- FR-003/FR-004: баннер об ограничении top-1000 -->
<div class="alert alert-info" role="alert">
    Показано топ-1000 из ~<span th:text="${totalCount}">0</span> доступных.
    Для полной выгрузки используйте
    <a href="/api/stats/by-song?page=1&pageSize=50" target="_blank">/api/stats/by-song</a>
    с пагинацией.
</div>
```

Стилизация `alert alert-info` Bootstrap 4 (уже используется в шаблоне).

## Архитектурное решение: почему safety-guard в `getStatBySong`

`StatsByEvents.getStatBySong` — единая точка вызова SQL для `/statbysong` И `/api/stats/by-song`.
Если поставить guard только в `MainController`, то `StatsController?pageSize=100000` всё равно
сделает огромный SELECT. Поэтому guard в самой функции:

1. **Single point of truth** — невозможно случайно обойти.
2. **Защищает оба endpoint'а** (Thymeleaf + REST).
3. **Защита от copy-paste ошибок** (раньше был hardcoded `limit=100_000` в `MainController`).
4. **Защита от DoS через REST query params**.

## Почему НЕ пагинация в Thymeleaf

`LIMIT 1000` × ~20 колонок = 20k ячеек. Помещается на 1 экран с `table-responsive` Bootstrap 4.
Пагинация не нужна — администратор видит top-1000 разом.

Если нужна полная выгрузка — `/api/stats/by-song?page=N&pageSize=50` (REST API с пагинацией,
возвращает `{items: [...50...], totalCount: N}`).

## Runtime-валидация (опционально)

Пользователь может проверить на проде (после deploy):

```sql
EXPLAIN ANALYZE SELECT e.song_id, ... FROM tbl_events e
    LEFT JOIN tbl_songs song ON e.song_id = song.id
    WHERE e.song_id IS NOT NULL AND e.song_id > 0
    GROUP BY e.song_id, song.song_author, song.song_album, song.song_name
    ORDER BY total DESC, e.song_id ASC
    LIMIT 1000 OFFSET 0;
-- Должен показать Index Scan на tbl_events_song_id_index (миграция 41, FR-110)
-- + HashAggregate на 1000 строк, миллисекунды.
```

## Sister

- [270-db-indexes-verification](270-db-indexes-verification.md) — миграция 41 добавила
  `tbl_events_song_id_index` (FR-110). Без этого индекса `getStatBySong` всё равно был бы
  медленным — миграция + этот safety-guard дают кумулятивный эффект.