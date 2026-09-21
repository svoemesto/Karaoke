# #93 — Final Map Update

> **Статус**: Карта #93 (wayfinder:map) активна. Дочерние тикеты:
> #94 (research, AFK субагент работает), #95 (research, CLOSED),
> #96 (prototype, HITL), #97 (grilling, CLOSED).

## Что сделано

### 1. Knowledge-first MUST #0 выполнен

Прочитаны:
- `knowledge/README.md` и `knowledge/domains/README.md`.
- `webvue3/src/components/Songs/SongsTable.vue` (текущая логика hrQueue).
- `webvue3/src/components/Common/ProcessWorker.vue` (существующий бейдж).
- Grep по `knowledge/` (3 попытки).

### 2. Корневая причина найдена

В `SongsTable.vue:1310-1339`:
- `hrQueue.push(songId)` — push в КОНЕЦ.
- `hrQueue.shift()` — shift из НАЧАЛА (FIFO).
- `currentPage` watcher (строка 1069): `this.hrQueue = []` — полная очистка.
- Нет приоритезации. Нет «всплытия». Нет визуализации размера.

### 3. Decisions so far

- **[#95] Backend queue size endpoint** — endpoint `/api/health/cacheStats` уже существует.
  Нужно расширить полем `cacheFiller: CacheFillerMetrics` (30 строк additive).
  См. resolution: `specs/_wayfinder-92-hrqueue-priority/95-research-resolution.md`.

- **[#97] Семантика приоритезации hrQueue** — финальные правила:
  - Элемент очереди: `(songId, pageId, filterHash)`.
  - При `currentPage` watcher: удалить задания **других** страниц, поднять задания **текущей** наверх.
  - При смене фильтра: НЕ удалять, вытеснить в конец.
  - При успешном fetch: удалить.
  - Без TTL.
  - При unmount: `hrQueue = []`.
  - Бейдж: `hrQueue.length` (всего).
  - Обновление через SSE (не polling).
  - Backend: расширить `/api/health/cacheStats` (см. #95).
  См. resolution: `specs/_wayfinder-92-hrqueue-priority/97-grilling-resolution.md`.

### 4. Дочерние тикеты

| ID | Тип | Тема | Статус |
|---|---|---|---|
| 94 | research | Текущее поведение hrQueue | **In progress** (AFK субагент be3f81a8) |
| 95 | research | Backend queue size endpoint | **CLOSED** (endpoint существует) |
| 96 | prototype | Бейдж синего цвета | **In progress** (HITL, требует #94 + дизайн с владельцем) |
| 97 | grilling | Семантика приоритезации | **CLOSED** (правила зафиксированы) |

## Что осталось неизвестным (в Not yet specified)

1. **HEX-цвет бейджа** — тикет #96.
2. **Расположение бейджа** — тикет #96.
3. **При 0 — скрывать или показывать «0»** — тикет #96.
4. **Текущее поведение hrQueue** (точно ли race conditions есть) — тикет #94 (в работе).

## Что нужно для имплементации

После того, как #94 (research current behavior) и #96 (prototype badge) завершатся,
можно создать **task-тикет** для имплементации:

- Backend (Pass 372/373/374): расширить `/api/health/cacheStats` + добавить SSE.
- Frontend (Pass 375): новый компонент бейджа + переписать hrQueue.
- Тесты: 7/7 CI PASS.

## Out of scope

- Полная замена `cacheFillerExecutor` (уже ограничен Pass 372).
- Изменения `KaraokeProcess`-воркера.
- Перевод `hrQueue` на серверную очередь (отдельный большой effort).

## Связанные документы

- `webvue3/src/components/Songs/SongsTable.vue` — основной файл правки
- `webvue3/src/components/Common/ProcessWorker.vue` — расположение бейджа
- `karaoke-app/.../controllers/CacheStatsController.kt` — расширение endpoint
- `karaoke-app/.../services/StorageMetadataCache.kt` — `cacheFillerMetrics`
- OP #83, #85, #87, #89, #90 (закрыты, дают контекст по healthReport)

— map update для #93