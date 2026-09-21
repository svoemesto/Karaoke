# #94 Resolution — Текущее поведение hrQueue

> **Resolution**: 2026-09-14, после research субагента.
> **Источник**: `research/92-hrqueue-current-behavior/REPORT.md` (794 строки, 40973 байт; SHA256 `18e22667308895d072d457c35bc0bf365ea676ce4f70575940b97be5dcfe4338`).

## Verdict (в 1 строку)

**Узкое место подтверждено**: `hrQueue` — чистый FIFO с принудительной очисткой при переключении страницы; это объясняет оба симптома #92 («не вытесняет приоритетно» + «не всплывает при возврате»).

## Ключевые факты

### Текущая логика (без изменений)

| Место | Поведение |
|---|---|
| `SongsTable.vue:1326-1339` | FIFO: `push` в конец, `shift` из начала. Лимит `HR_MAX_CONCURRENT=3`. |
| `SongsTable.vue:1069` | `currentPage` watcher → `this.hrQueue = []` — ПОЛНАЯ ОЧИСТКА. |
| `SongsTable.vue:1046-1057` | `countRows` watcher → `updateHealthReportForCurrentPage()` — может re-enqueue. |
| `ApiController.kt:7673-7686` | `POST /api/song/healthReportList` — синхронный, per-song, **нет batch**. |
| `HealthReport.kt:2353` | `recomputeAndBroadcast` дёргает MinIO per-id. |
| `HealthReport` single-flight | НЕТ (в отличие от Pass 343 race-fixed-65 в repair-loop). |

### 6 design issues (B1-B6)

- **B1**: Нет single-flight guard в `hrQueue` → дубли при `countRows` × `updateHealthReportForCurrentPage`.
- **B2**: Race `countRows` × `currentPage` — `hrQueue = []` может происходить в момент enqueue.
- **B3**: Потеря заданий при переключении (`hrQueue = []` wipe).
- **B4**: Нет cooperative cancellation — fetch'и в полёте после `hrQueue = []` продолжают работать.
- **B5**: HTTP-vs-SSE очерёдность — непонятно, обновляет ли SSE уже начатый fetch.
- **B6**: Нет batch endpoint — 3 параллельных fetch'а вместо одного запроса.

### POC фикса (в REPORT.md как комментарий, не код)

Идея для имплементации (НЕ реализовывать в этом research):
- Priority queue: каждый элемент имеет `priority` (выше = раньше).
- `hrInFlight: Set<songId>` — single-flight guard (mirror Pass 343).
- При `currentPage` watcher — НЕ чистить `hrQueue`, а rebalance:
  - Удалить задания **других** страниц.
  - Поднять задания **текущей** наверх.
- Drain по приоритету.

## Что решено (по итогам #94 + #97)

| Симптом #92 | Решение |
|---|---|
| Не вытесняют приоритетно при переключении вперёд | Перебалансировать (поднять задания новой страницы наверх). |
| Не всплывают при возврате | После перебалансировки — задания текущей страницы наверху. |
| Дубли при countRows | `hrInFlight: Set<songId>` — single-flight guard. |
| Fetch'и после wipe | Cooperative cancellation (AbortController) + фильтр на старте. |
| Per-song HTTP | **Опционально**: batch endpoint (но не обязательно для #92). |

## Что осталось неизвестным

1. **Race `countRows` × `currentPage`** — точные тайминги, нужно тестировать.
2. **Сравнение overhead** priority queue vs FIFO — измеримо при имплементации.
3. **Batch endpoint** — нужен ли, или 3 параллельных fetch'а достаточно.

## Связанные

- `webvue3/src/components/Songs/SongsTable.vue` — основной файл правки.
- `karaoke-app/src/main/kotlin/.../HealthReport.kt` — single-flight pattern (Pass 343) для зеркалирования.
- OP #97 (CLOSED) — финальные правила приоритезации.
- OP #95 (CLOSED) — backend endpoint расширение.

— resolution для #94