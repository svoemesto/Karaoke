# #92 Wayfinder Charter — HR Queue Priority

> **Сессия**: 2026-09-14T17:30Z, wayfinder charting pass.
> **Карта**: OpenProject #93.
> **Дочерние тикеты**: #94, #95, #96, #97 (все In progress, claimed).
> **Статус карты**: In progress (claimed by ai-agent).

## Что зафиксировано в этой сессии

### 1. Knowledge-first MUST #0 выполнен

- Прочитан `knowledge/README.md` и `knowledge/domains/README.md`.
- Прочитаны ключевые компоненты:
  - `webvue3/src/components/Songs/SongsTable.vue` (текущая логика `hrQueue`, FIFO, `hrQueue = []` reset)
  - `webvue3/src/components/Common/ProcessWorker.vue` (существующий бейдж в правом НИЖНЕМ углу)
- Grep по `knowledge/` (3 попытки):
  - `cacheFillerExecutor|cachedFileExistsAsync.*pool|пул.*cache` — пусто.
  - `KaraokeProcess.*badge|badge.*KaraokeProcess|cacheStats|infra\.cache` — 18 hits, найдены `infra.cache.statbysong`, `infra.cache.storage`.
  - `счётчик.*задач|размер.*пул|priority.*queue|приоритет.*очередь` — пусто.
  - `vuex-patterns|hrQueue|хранилищу.*пул` — 10 hits, найдена `store-songs.md`, `vuex-patterns.md`.

### 2. Найдена корневая причина в коде

**Текущая логика** (SongsTable.vue:1310-1339):
```js
_enqueueHrRequest(songId) {
  this.hrQueue.push(songId)        // push в КОНЕЦ
  ...
}
_processHrQueue() {
  while (this.hrRunning < this.HR_MAX_CONCURRENT && this.hrQueue.length > 0) {
    const id = this.hrQueue.shift()   // shift из НАЧАЛА (FIFO)
    ...
  }
}
```

**Watcher** (SongsTable.vue:1069):
```js
currentPage: {
  handler(newPage) {
    this.$store.commit('setSongsTableCurrentPage', newPage)
    this.hrQueue = []        // ← ПОЛНАЯ ОЧИСТКА при переключении!
    this.updateHealthReportForCurrentPage()
    ...
  },
},
```

Это значит:
- ❌ Нет приоритезации (новые задания не вытесняют старые в начало).
- ❌ Нет «всплытия» при возврате.
- ❌ Полный wipe при переключении — задания предыдущей страницы **потеряны**, даже если ещё не выполнялись.

### 3. Найден существующий бейдж (для сравнения)

**ProcessWorker.vue:14, 233-247**:
- Бейдж `text-count-waiting` — в правом НИЖНЕМ углу кнопки.
- Содержит `countWaiting` — количество задач в KaraokeProcess-воркере.
- Это **НЕ** то, что нам нужно: нам нужно количество задач в `cacheFillerExecutor`
  (на backend, в `StorageMetadataCache.kt`).

### 4. Карта #93 + 4 тикета созданы и claimed

| ID | Тип | Тема | Claimed |
|---|---|---|---|
| 93 | wayfinder:map | Карта effort'а | ✅ |
| 94 | wayfinder:research | Текущее поведение hrQueue | ✅ (AFK) |
| 95 | wayfinder:research | Backend queue size endpoint | ✅ (AFK) |
| 96 | wayfinder:prototype | Бейдж синего цвета | ✅ (HITL) |
| 97 | wayfinder:grilling | Семантика приоритезации | ✅ (HITL) |

### 5. Запущены research-субагенты в background (Pass 374 — можно параллельно)

Сейчас запущу субагентов для **#94** и **#95** (оба research, AFK — можно параллельно).
**#96** (prototype) и **#97** (grilling) — HITL, требуют владельца.

## Frontier (открыто, unblocked, claimable)

- **#94, #95** — AFK research, субагенты будут запущены в background.
- **#96, #97** — HITL, требуют интерактива с владельцем.

## Что осталось неизвестным (в карте #93 → Not yet specified)

1. Приоритет по странице: всплывают ли задания только текущей страницы?
2. Edge cases при фильтрах (песня удалена из фильтра).
3. Backend queue size: метрика считается на frontend или на backend?
4. Цвет бейджа: синий (HEX согласован)?
5. Расположение: точно над кнопкой или в header страницы Songs?
6. Синхронизация: polling или SSE?

## Что нужно от владельца

1. Согласовать цвет бейджа (HEX) для прототипа #96.
2. Согласовать семантику приоритезации (через grilling #97).
3. После research-результатов #94 и #95 — финальное решение, что считать и где показывать.

## Файлы сессии

- `specs/_wayfinder-92-hrqueue-priority/_charter.md` — этот файл.
- `specs/_wayfinder-92-hrqueue-priority/93-map-update-1.md` — обновление карты (после research).
- `research/92-hrqueue-current-behavior/` — рабочая директория research субагента.
- `research/92-backend-queue-size/` — рабочая директория research субагента.

— charter для #92