# #96 Resolution — Prototype бейджа счётчика cache queue

> **Resolution**: 2026-09-14T17:40Z.
> **PR**: [svoemesto/Karaoke#477](https://github.com/svoemesto/Karaoke/pull/477) (MERGED 2026-09-14T17:40:48Z).
> **Merge commit**: `1fd3cc76d93864f59846db62f30d4cd84536f958`.
> **Source commit**: `68a1f3d3` (на ветке `380-92-cache-queue-badge-prototype`).

## Что сделано

### Дизайн (согласован с владельцем через grilling)

| Аспект | Решение |
|---|---|
| Цвет | `#007bff` (стандартный Bootstrap primary) |
| Расположение | Правый ВЕРХНИЙ угол кнопки Старт/Стоп |
| При `count = 0` | Скрывается через `v-show` |
| Содержимое | Число (count) |
| Title (tooltip) | "В пуле проверки кеша: N задач" |

### Реализация

1. **Новый компонент** `webvue3/src/components/Common/CacheQueueBadge.vue`:
   - prop `count: Number` (required, default 0)
   - `v-show="count > 0"` — скрытие при 0
   - CSS: `position: absolute; top: 0; right: 0; transform: translate(-25%, -25%)`
   - Цвет фона: `#007bff`
   - `box-shadow` для контраста
   - JSDoc полный (Pass 376 governance)

2. **Интеграция** в `ProcessWorker.vue`:
   - Импорт `CacheQueueBadge` в `components: {}`
   - Шаблон: `<cache-queue-badge :count="cacheQueueCount" />`
   - data(): `cacheQueueCount: 5` (stub для прототипа)

### Acceptance criteria

- [x] `npm run lint` — exit 0
- [x] `npm run build` — exit 0
- [x] Pre-commit (Pass 372-375) — все guard hooks Passed
- [x] PR #477 — CI 9/9 PASS, MERGED

## Что осталось (для implementation-фазы)

Прототип готов. **Implementation-фаза** (новый task-тикет) должна:
1. Backend: расширить `/api/health/cacheStats` полем `cacheFiller: CacheFillerMetrics`
   (Pass 95).
2. Frontend: подключить `cacheQueueCount` к Vuex store через SSE или polling
   (Pass 97 — выбрано SSE).
3. Переписать `hrQueue` логику с FIFO на priority queue + single-flight guard
   (Pass 94 + Pass 97 правила).
4. Тесты: 7/7 CI PASS.

## Связанные

- OP #92 (исходная задача) — закроется после implementation-фазы
- OP #93 (wayfinder:map) — карта effort'а
- OP #95 (Backend queue size, CLOSED)
- OP #97 (Grilling semantics, CLOSED)
- Pass 95, 97 — финальные решения, на которые опирается прототип

— resolution для #96