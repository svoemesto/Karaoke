<!-- описание карты [wayfinder:map] в OpenProject — Issue #119 -->

## Destination

Реализовать:
1. **Механизм LIFO/всплытия в cache-очереди**: при переходе на страницу песен
   задачи этой страницы (cache-fills в `StorageMetadataCache.cacheFillerExecutor`)
   вставляются в **начало** очереди (а не в конец). При возврате на ранее
   посещённую страницу задачи песен этой страницы, ещё ожидающие в очереди,
   **всплывают** в её начало.
2. **Бейдж размера cache-очереди** в правом верхнем углу кнопки Старт/Стоп,
   показывающий количество задач в общей очереди `cacheFillerExecutor`.

**Это НЕ про `tbl_processes` lane1 и НЕ про UI `hrQueue`**.

## Notes

- **Домен**: caching (`StorageMetadataCache.cacheFillerExecutor`).
- **Скиллы**: `kara-post-edit`, `knowledge-first MUST #0`.
- **Стоячие предпочтения**:
 - Без MP4/JPA (см. `AGENTS.md` Tier-1 guards R-07, R-11).
 - Без изменений в `tbl_processes` lane1 — это другая подсистема.
 - OpenProject Workflow: claim → add-comment (report.md) → mark-review → close.

## Frontier (открытые child-issues)

| ID  | Subject | Status | Заблокирован |
|-----|---------|--------|--------------|
| 491 | [wayfinder:revert] Revert PR #491 (countwaiting thread filter) | **To do** | — |
| 492 | [wayfinder:revert] Revert PR #492 (LIFO hrQueue SongsTable) | **To do** | — |
| 493 | [wayfinder:revert] Revert PR #493 (SSE PROCESS_LANE_COUNT) | **To do** | — |
| (new) | [wayfinder:task] LIFO/всплытие в StorageMetadataCache.cacheFillerExecutor | **To create** | reverts |
| (new) | [wayfinder:task] Бейдж размера cache-очереди | **To create** | new fix |

**Unblocked frontier**: reverts.
**Blocked**: новые фиксы ждут revert'ов.

## Decisions so far

(пусто — это первая редакция карты после переосмысления)

## Not yet specified

- Какой API возвращает размер `cacheFillerExecutor` — отдельный endpoint
  или расширение существующего `/api/health/cacheStats`.
- Будет ли live SSE для бейджа (как раньше для PROCESS_LANE_COUNT_WAITING)
  или только initial poll. Зависит от того, насколько часто меняется
  размер очереди.
- Как передавать `pageId` в backend из SongsTable.vue — новый query param
  для `/api/song/healthReportList` или отдельный вызов.

## Out of scope

- Изменения в `tbl_processes` lane1 (это другая подсистема, не кеш).
- Изменения в UI-очереди `SongsTable.vue:hrQueue` (это про UI-отчёты, не про кеш).
- Изменения в `HealthReport.kt` business-логике ремонта.