# Vuex store: Songs

> **Домен**: system (frontend)
> **Компонента**: `webvue3/src/components/Songs/store.js` — главный
> Vuex-модуль проекта (страница «Песни»).

## Файл

`webvue3/src/components/Songs/store.js`

## State

```javascript
state: {
    toSync: false,            // флаг синхронизации с сервером
    freeTimeSlots: [],         // свободные тайм-слоты (для рендера)
    lastSettingType: '',       // последний тип настройки
    lastSettingValue: '',      // последнее значение настройки
    lastPriorLyrics: '',       // приоритеты рендера
    lastPriorKaraoke: '',
    lastPriorChords: '',
    lastPriorMelody: '',
    lastPriorDemo: '',
    lastThreadId: '0',         // последний thread ID
    lastPriorCodeLyrics: '',   // приоритеты кода
    lastPriorCodeKaraoke: '',
    lastPriorDemucs: '',
    songs: [],                 // текущая страница (SongDTO[])
    countRows: 0,              // total count
    songsTableCurrentPage: 1,  // текущая страница (persistent)
    selectedRows: [],          // ID выбранных строк
}
```

## Архитектурные решения

### Решение 1: Vuex (НЕ Pinia)

Стандартный Vuex 4. Pinia НЕ принят (legacy).

### Решение 2: HR-очередь с лимитом 3

`HR_MAX_CONCURRENT=3` — лимит одновременных
`/api/songs/getHealthReport` запросов. Без лимита быстрая
пагинация перегружает сервер MinIO-запросами (см.
[health-report.md](../../domains/health/components/health-report.md) —
проблема OpenProject #69).

### Решение 3: SSE-driven updates

После мутации сервер шлёт SSE `recordChange` — UI реагирует на
него, **не** перезагружает данные вручную. Экономит XHR.

### Решение 4: persistent filters через server-side key/value

`Songs/filter/store.js` хранит фильтры через `setWebvueProp` /
`getWebvueProp` (server-side), не `localStorage`. Синхронизирует
между устройствами.

## Hot paths

- **`/api/songs/list`** — на каждое изменение фильтра/страницы.
- **`/api/songs/getHealthReport`** (через HR-очередь) — для каждой
  видимой строки (open #69 bottleneck).
- **SSE `recordChange`** — реактивно обновляет таблицу.

## Известные TODO

- [ ] **Все actions** — детальный contract (Pass 343+).
- [ ] **Все getters/mutations** — полный список.
- [ ] **`HR_MAX_CONCURRENT`** — где определена, как настраивается.

## Связь

- **HealthReport** ([health-report.md](../../domains/health/components/health-report.md)) —
  HR-очередь.
- **SSE** ([sse domain](../../domains/sse/domain.md)) — recordChange
  events.
- **Vuex patterns** ([vuex-patterns.md](vuex-patterns.md)) — общие
  паттерны.

## Changelog

- **Pass 371** (2026-09-09): Initial. Автор: agent (Karaoke).