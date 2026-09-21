# #93 — Final Map Update (Closed)

> **Status**: ✅ Карта #93 (wayfinder:map) ЗАКРЫТА. Маршрут до implementation готов.

## Decisions so far (финальный архив)

- **[#94] Текущее поведение hrQueue** — `FIFO + hrQueue = []` (SongsTable.vue:1310-1339, 1069).
  Подтверждены 6 design issues (B1-B6). POC priority-aware фикса в REPORT.md.
  Resolution: `94-research-resolution.md`.

- **[#95] Backend queue size endpoint** — `/api/health/cacheStats` уже существует.
  Расширить полем `cacheFiller: CacheFillerMetrics` (30 строк additive).
  Resolution: `95-research-resolution.md`.

- **[#96] Prototype бейджа** — компонент `CacheQueueBadge.vue` со stub-данными.
  Цвет `#007bff`, правый верхний угол, скрытие при 0.
  PR [svoemesto/Karaoke#477](https://github.com/svoemesto/Karaoke/pull/477) MERGED в `1fd3cc76`.
  Resolution: `96-prototype-resolution.md`.

- **[#97] Семантика приоритезации** — финальные 10 правил для implementation:
  - Элемент очереди: `(songId, pageId, filterHash)`.
  - При `currentPage` watcher: удалить задания **других** страниц, поднять задания **текущей** наверх.
  - При смене фильтра: НЕ удалять, вытеснить в конец.
  - При успешном fetch: удалить.
  - Без TTL.
  - При unmount: `hrQueue = []`.
  - Бейдж: `hrQueue.length` (всего).
  - Обновление через SSE (не polling).
  - Backend: расширить `/api/health/cacheStats` (см. #95).
  Resolution: `97-grilling-resolution.md`.

## Что за границей (graduate в task-тикет)

**[#98] Implementation** — task-тикет создан, claimed.
Содержит полный план реализации:
- Backend: расширение `CacheStatsController.kt` + `StorageMetadataCache.kt`.
- Frontend: рефакторинг `hrQueue` (FIFO → priority + single-flight).
- Frontend: подключение `CacheQueueBadge` к Vuex через SSE.
- Тесты: 7/7 CI PASS.
- После merge: закрыть #98 → #93 → #92.

## Финальное состояние OpenProject

| ID | Статус | Описание |
|---|---|---|
| 92 | New | Исходная задача (закроется после implementation) |
| 93 | **Closed** ✅ | wayfinder:map (маршрут готов) |
| 94 | **Closed** ✅ | research current behavior |
| 95 | **Closed** ✅ | research backend endpoint |
| 96 | **Closed** ✅ | prototype бейджа |
| 97 | **Closed** ✅ | grilling semantics |
| 98 | In progress | Implementation (claimed by ai-agent) |

## Артефакты сессии

- `specs/_wayfinder-92-hrqueue-priority/_charter.md`
- `specs/_wayfinder-92-hrqueue-priority/93-map-update-1.md`
- `specs/_wayfinder-92-hrqueue-priority/94-research-resolution.md`
- `specs/_wayfinder-92-hrqueue-priority/95-research-resolution.md`
- `specs/_wayfinder-92-hrqueue-priority/96-prototype-resolution.md`
- `specs/_wayfinder-92-hrqueue-priority/97-grilling-resolution.md`
- `research/92-hrqueue-current-behavior/REPORT.md`
- `research/92-backend-queue-size/REPORT.md`

## Связанные PR

- [svoemesto/Karaoke#477](https://github.com/svoemesto/Karaoke/pull/477) — prototype (merged)

— final map для #93