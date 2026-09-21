# Report #128 — Асинхронный healthReportList через батч + SSE

> **OpenProject**: #128 «Асинхронный healthReportList».
> **Spec**: [spec.md](spec.md).
> **Plan**: [plan.md](plan.md).
> **Tasks**: [tasks.md](tasks.md).
> **PR**: [#504](https://github.com/svoemesto/Karaoke/pull/504).
> **Branch**: `398-async-hrlist`.
> **Status**: In review (CI running).

## Что сделано

### Backend (`karaoke-app`)

- **`HealthReportBatchPool.kt`** (новый, 191 строка) — Spring `@Service`:
  - 10 worker-потоков (`Executors.newFixedThreadPool(10)`).
  - Приоритетная очередь `MutableList<Long>` под `ReentrantLock` с
    семантикой move-to-front (требование #128).
  - Single-flight через `ConcurrentHashMap<Long, AtomicBoolean>` (паттерн
    из `race-fixed-65.md`).
  - `companion object fun parseSongIds(raw: String?)` — парсер
    `;`-separated строки (конвенция проекта).
  - Internal hooks `executor`, `workersEnabled` для unit-тестов.
  - SLF4J-категория `infra.cache.hrpool`.
  - `@PostConstruct` start, `@PreDestroy` graceful shutdown (await 5 сек).

- **`ApiController.kt`** — endpoint `POST /api/song/healthReportListBatch`:
  - Параметр `songIds: String` — `;`-separated.
  - Парсит через `HealthReportBatchPool.parseSongIds(...)`.
  - Возвращает `ResponseEntity.accepted().build()` (202) мгновенно.

- **`HealthReportBatchPoolTest.kt`** (новый, 10 тестов):
  - `parseSongIds semicolon-separated string` PASS.
  - `parseSongIds with surrounding whitespace` PASS.
  - `parseSongIds empty or blank returns empty` PASS.
  - `parseSongIds invalid tokens are silently dropped` PASS.
  - `enqueue same songId twice does not duplicate` PASS.
  - `enqueue same songId twice moves it to front` PASS.
  - `enqueue batch is inserted at front preserving internal order` PASS.
  - `enqueue empty list is no-op` PASS.
  - `enqueue with zero or negative ids is filtered` PASS.
  - `concurrent enqueue from many threads keeps queue consistent` PASS.

**Backend hard gates**:
- `:karaoke-app:compileKotlin` — PASS.
- `:karaoke-app:ktlintCheck` — PASS.
- `:karaoke-app:test --tests HealthReportBatchPoolTest` — 10/10 PASS за 1.1s.

### Frontend (`webvue3`)

- **`Songs/store.js`** — action `loadHealthReportBatch(ctx, songIds)`:
  - POST `/api/song/healthReportListBatch` с `params: { songIds: songIds.join(';') }`.
  - **Fire-and-forget** (`.catch(console.log)`).
  - **Перед** отправкой — локальная pending-маркировка:
    `song.healthReportText = '?'`, `song.healthReportColor = '#CCCCCC'`
    (для каждого найденного в `songsDigest`).
  - Документация в KDoc-комментарии обновлена (убрано упоминание
    `HR_MAX_CONCURRENT=3`).

- **`SongsTable.vue`** — каскад заменён на батч:
  - **Удалены** `data.hrQueue`, `data.hrRunning`, `data.HR_MAX_CONCURRENT`,
    методы `_enqueueHrRequest`, `_processHrQueue`.
  - **Добавлены** методы `_collectMissingHrSongIds(page)` (собирает id
    песен страницы с `healthReportText === '-'`) и `_enqueueHrBatch(songIds)`.
  - `currentPage` watcher: `this.hrQueue = []` + `updateHealthReportForCurrentPage()`
    → `_enqueueHrBatch(_collectMissingHrSongIds(newPage))`.
  - `updateHealthReportForCurrentPage()` — теперь делегирует в `_enqueueHrBatch`.
  - `editSong(id)` — убран лишний `this.hrQueue = []`.

**Frontend hard gates**:
- `npm run lint` — PASS.
- `npm run build` — PASS (built in 7.45s).

### Knowledge SSoT

- **`knowledge/domains/health/components/health-report-batch-pool.md`** (новый):
  полный контракт пула, алгоритм, single-flight, graceful shutdown,
  связь с `HealthReport.recomputeAndBroadcast` и SSE.

- **`knowledge/domains/health/domain.md`** — добавлен `HealthReportBatchPool`
  в «Ключевые компоненты»; зафиксирован #128 как FIXED в Pass 128 в
  секции «Связь с OpenProject».

- **`knowledge/domains/monitoring/components/log-categories.md`** — добавлена
  SLF4J-категория `infra.cache.hrpool` с примером логирования.

- **`knowledge/domains/README.md`** — добавлен `health-report-batch-pool` в
  список компонентов домена `health`.

**Knowledge lint**: `tools/lint-knowledge.py` — новых violations нет.

## Acceptance criteria

| # | Критерий | Статус |
|---|---|---|
| 3.1.1 | Endpoint принимает `List<Long>`, возвращает 202 мгновенно | ✅ |
| 3.1.2 | Никакого ожидания на HTTP | ✅ |
| 3.1.3 | 10 worker-потоков фиксированный | ✅ |
| 3.1.4 | Move-to-front при повторном enqueue | ✅ (тест + код) |
| 3.1.5 | Single-flight через `AtomicBoolean` | ✅ |
| 3.1.6 | SSE `HEALTH_REPORTS` рассылается через `recomputeAndBroadcast` | ✅ (переиспользуем без изменений) |
| 3.2.1 | Фронт группирует id страницы в один батч | ✅ |
| 3.2.2 | Локальная pending-маркировка `'?'` / `#CCCCCC` | ✅ |
| 3.2.3 | SSE обновляет state через существующую mutation | ✅ (без изменений) |
| 3.2.4 | Legacy endpoint `/song/healthReportList` оставлен | ✅ |
| 3.3 | 10 unit-тестов PASS | ✅ |
| 3.4 | Все hard gates (compile/ktlint/lint/build) | ✅ |

## Что осталось владельцу

- [ ] **CI**: дождаться 7/7 PASS на PR #504 (`gh pr checks`).
- [ ] **Merge**: `gh pr merge --merge` (без `--delete-branch`).
- [ ] **Docker**: `deploy/do.sh build_karaoke-app && build_webvue3`.
- [ ] **Deploy** (по согласию AGENTS.md Tier-1): `deploy/do.sh restart_karaoke-app && restart_webvue3`.
- [ ] **Ручная проверка**:
  - 30 песен без HR открываются за < 2 сек (вместо 5-10 сек).
  - Переключение страниц — приоритетная загрузка (текущая страница первой).
  - DevTools Network: один POST на страницу, дальше только SSE-события.
- [ ] **Close**: `tracker.sh close-issue 128`.

## Известные ограничения / trade-offs

1. **`workersEnabled` exposed internal**: используется только в тестах,
   помечен комментарием. В production остаётся `true`.
2. **`executor` exposed internal**: аналогично, только для тестов. В production
   инициализируется `Executors.newFixedThreadPool(10)`.
3. **Тесты используют `DirectExecutorService`**: чтобы изолировать от
   реального `HealthReport.recomputeAndBroadcast` (companion object, не
   мокается). Тесты покрывают только логику очереди.
5. **Memory churn в `inFlight`**: ключи НЕ удаляются после `exit()` (паттерн
   из `race-fixed-65.md`). Если в проекте будут тысячи уникальных песен —
   может быть заметный расход памяти. TODO: добавить `cleanupRepair(songId)`
   в дальнейшем (уже упоминается в race-fixed-65.md как TODO).

## Файлы в коммите

```
M  karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt
A  karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/HealthReportBatchPool.kt
A  karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/HealthReportBatchPoolTest.kt
M  knowledge/domains/README.md
A  knowledge/domains/health/components/health-report-batch-pool.md
M  knowledge/domains/health/domain.md
M  knowledge/domains/monitoring/components/log-categories.md
A  specs/128-async-health-report-list/plan.md
A  specs/128-async-health-report-list/spec.md
A  specs/128-async-health-report-list/tasks.md
M  webvue3/src/components/Songs/SongsTable.vue
M  webvue3/src/components/Songs/store.js
```

12 файлов, +1429 / -29 строк.

— отчёт для #128