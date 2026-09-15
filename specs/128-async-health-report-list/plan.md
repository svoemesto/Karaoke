# Plan #128 — Асинхронный healthReportList через батч + SSE

> **OpenProject**: #128.
> **Spec**: [spec.md](spec.md).
> **Цель**: свести design-решения к минимуму — спека уже почти implementation-ready.

## 1. Архитектурный скелет

```
┌──────────────────────────────────────────────────────────────────────┐
│  webvue3 SongsTable.vue                                              │
│                                                                      │
│  mounted / currentPage watcher ──► _enqueueHrBatch(currentPageIds)  │
│                                          │                            │
│                                          ▼                            │
│  Songs/store.js: loadHealthReportBatch(ctx, songIds)                 │
│      │  POST /api/song/healthReportListBatch  (params: songIds[])    │
│      │  ожидаем 202 Accepted; ответ не парсим (он пустой)            │
│      └─► помечаем локально pending: song.healthReportText = '?'      │
│                          song.healthReportColor = '#CCCCCC'           │
└──────────────────────────────────────────────────────────────────────┘
                                │ HTTP POST (fire-and-forget)
                                ▼
┌──────────────────────────────────────────────────────────────────────┐
│  karaoke-app ApiController.kt                                         │
│                                                                      │
│  @PostMapping("/song/healthReportListBatch")                         │
│  fun healthReportListBatch(@RequestParam songIds: List<Long>) {      │
│      HealthReportBatchPool.enqueue(songIds)                          │
│      ResponseEntity.accepted().build()                               │
│  }                                                                   │
└──────────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌──────────────────────────────────────────────────────────────────────┐
│  HealthReportBatchPool (Spring @Service)                             │
│                                                                      │
│  - priorityQueue: MutableList<Long>  // LinkedHashSet-подобная      │
│  - priorityLock: ReentrantLock                                       │
│  - inFlight: ConcurrentHashMap<Long, AtomicBoolean>                  │
│  - executor: ExecutorService = newFixedThreadPool(10)                │
│                                                                      │
│  fun enqueue(songIds: List<Long>) {                                  │
│      priorityLock.withLock {                                         │
│          for (id in songIds.reversed()) {                            │
│              priorityQueue.remove(id)  // если есть — удаляем        │
│              priorityQueue.add(0, id)  // вставляем в начало          │
│          }                                                            │
│      }                                                                │
│      wakeupWorkers()  // если executor простаивает, дёргаем N тасков │
│  }                                                                    │
│                                                                      │
│  private fun takeNext(): Long? {                                     │
│      priorityLock.withLock {                                         │
│      return if (priorityQueue.isEmpty()) null                        │
│             else priorityQueue.removeAt(0)                           │
│  }                                                                    │
│                                                                      │
│  private fun tryEnter(songId: Long): Boolean =                      │
│      inFlight.computeIfAbsent(songId) { AtomicBoolean(false) }       │
│          .compareAndSet(false, true)                                 │
│                                                                      │
│  private fun exit(songId: Long) {                                    │
│      inFlight[songId]?.set(false)                                    │
│  }                                                                    │
│                                                                      │
│  private fun workerLoop() {                                          │
│      while (!Thread.currentThread().isInterrupted) {                 │
│          val id = takeNext() ?: run { Thread.sleep(50); return@workerLoop }│
│          if (!tryEnter(id)) continue                                  │
│          try {                                                        │
│              HealthReport.recomputeAndBroadcast(id, ...)              │
│          } catch (e: Exception) {                                    │
│              log.warn("infra.cache.hrpool", "recompute failed for $id", e)│
│          } finally {                                                  │
│              exit(id)                                                 │
│          }                                                            │
│      }                                                                │
│  }                                                                    │
│                                                                      │
│  @PostConstruct fun start() { repeat(10) { executor.submit(::workerLoop) } }│
│  @PreDestroy fun stop() { executor.shutdown(); executor.awaitTermination(5, SECONDS); executor.shutdownNow() }│
└──────────────────────────────────────────────────────────────────────┘
                                │
                                ▼  (через `recomputeAndBroadcast`)
┌──────────────────────────────────────────────────────────────────────┐
│  HealthReport.recomputeAndBroadcast                                  │
│  ─► SNS.send(SseNotification.healthReports(songId, dtos))           │
└──────────────────────────────────────────────────────────────────────┘
                                │
                                ▼  SSE broadcast
┌──────────────────────────────────────────────────────────────────────┐
│  webvue3 App.vue: case 'HEALTH_REPORTS' ─► Songs/store.js mutation  │
│  healthReportMessageByUserEvent ─► обновляет healthReportList/Text/Color│
└──────────────────────────────────────────────────────────────────────┘
```

## 2. Дизайн классов

### 2.1. `HealthReportBatchPool.kt`

```kotlin
@Service
class HealthReportBatchPool(
    private val database: KaraokeConnection,
    private val storageService: KaraokeStorageService,
    private val storageApiClient: StorageApiClient,
) {
    companion object {
        private const val POOL_SIZE = 10
        private const val WORKER_IDLE_SLEEP_MS = 50L
        private const val SHUTDOWN_TIMEOUT_SEC = 5L
        private const val LOG_CATEGORY = "infra.cache.hrpool"
    }

    private val log = LoggerFactory.getLogger(LOG_CATEGORY)
    private val priorityLock = ReentrantLock()
    private val priorityQueue: MutableList<Long> = mutableListOf()
    private val inFlight: ConcurrentHashMap<Long, AtomicBoolean> = ConcurrentHashMap()
    private val executor: ExecutorService = Executors.newFixedThreadPool(POOL_SIZE)

    /**
     * Добавить песни в приоритетную очередь. Каждый songId:
     *  - если уже в очереди — удаляется и вставляется в начало (move-to-front).
     *  - если уже считается в worker'е — игнорируется (single-flight).
     */
    fun enqueue(songIds: List<Long>) {
        priorityLock.withLock {
            for (id in songIds.asReversed()) {
                if (id <= 0) continue
                priorityQueue.remove(id)
                priorityQueue.add(0, id)
            }
        }
        // Гарантируем, что все worker'ы проснулись: если в очереди N песен,
        // а простаивает M worker'ов (M <= 10), запустится M новых тасков.
        wakeupWorkers()
    }

    fun queueSize(): Int = priorityLock.withLock { priorityQueue.size }

    @PostConstruct
    fun start() {
        repeat(POOL_SIZE) {
            executor.submit { workerLoop() }
        }
        log.info("started with $POOL_SIZE workers")
    }

    @PreDestroy
    fun stop() {
        executor.shutdown()
        if (!executor.awaitTermination(SHUTDOWN_TIMEOUT_SEC, TimeUnit.SECONDS)) {
            executor.shutdownNow()
        }
        log.info("stopped")
    }

    private fun wakeupWorkers() {
        // Избегаем огромного всплеска тасков: POOL_SIZE раз就够了.
        // Если очередь > POOL_SIZE, worker'ы будут брать последовательно.
        repeat(POOL_SIZE) { executor.submit { workerLoop() } }
    }

    private fun workerLoop() {
        while (!Thread.currentThread().isInterrupted) {
            val id = takeNext() ?: run {
                Thread.sleep(WORKER_IDLE_SLEEP_MS)
                return@workerLoop
            }
            if (!tryEnter(id)) continue  // уже считается в другом worker'е
            try {
                HealthReport.recomputeAndBroadcast(
                    songId = id,
                    database = database,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                )
            } catch (e: Exception) {
                log.warn("recompute failed for songId=$id: ${e.message}", e)
            } finally {
                exit(id)
            }
        }
    }

    private fun takeNext(): Long? = priorityLock.withLock {
        if (priorityQueue.isEmpty()) null else priorityQueue.removeAt(0)
    }

    private fun tryEnter(songId: Long): Boolean =
        inFlight.computeIfAbsent(songId) { AtomicBoolean(false) }
            .compareAndSet(false, true)

    private fun exit(songId: Long) {
        inFlight[songId]?.set(false)
    }
}
```

**NB**: `wakeupWorkers` запускает POOL_SIZE новых тасков при каждом `enqueue`.
Если worker уже работает — он просто возьмёт следующую песню. Если worker
простаивает — он выполнит одну итерацию цикла и снова уснёт. Дубликаты
безвредны (`takeNext()` атомарно разбирает очередь).

### 2.2. Endpoint в `ApiController.kt`

```kotlin
// Асинхронный батч-запрос healthReportList: принимает список, ставит в пул,
// мгновенно возвращает 202. Результаты приходят на веб через SSE HEALTH_REPORTS.
// (Pass 128 — фронт больше не ждёт ответа для каждой песни.)
@PostMapping("/song/healthReportListBatch")
@ResponseBody
fun healthReportListBatch(
    @RequestParam songIds: List<Long>,
): ResponseEntity<Void> {
    healthReportBatchPool.enqueue(songIds)
    return ResponseEntity.accepted().build()
}
```

Поле `healthReportBatchPool` — `@Autowired` через конструктор (Kotlin по
умолчанию) или через `@Autowired lateinit var` (если нет primary constructor).

### 2.3. Frontend — `Songs/store.js` (новый action)

```javascript
loadHealthReportBatch(ctx, songIds) {
  if (!songIds || songIds.length === 0) return
  const params = { songIds: songIds }
  const request = { method: 'POST', url: '/api/song/healthReportListBatch', params: params }
  // Fire-and-forget: помечаем pending сразу, ответ не парсим.
  for (const songId of songIds) {
    const song = ctx.state.songsDigest.find((s) => s.id === songId)
    if (song) {
      song.healthReportText = '?'
      song.healthReportColor = '#CCCCCC'
    }
  }
  promisedXMLHttpRequest(request).catch((err) => console.log(err))
},
```

### 2.4. Frontend — `SongsTable.vue` (изменённый каскад)

Заменить:
```js
_enqueueHrRequest(songId) {
  this.hrQueue.push(songId)
  this._processHrQueue()
},
_processHrQueue() { ... }
```

На:
```js
_enqueueHrBatch(songIdsForCurrentPage) {
  // songIdsForCurrentPage — это песни текущей страницы БЕЗ healthReportList.
  // Шлём одним батчем — бэк сам разрулит приоритет и SSE.
  this.$store.dispatch('loadHealthReportBatch', songIdsForCurrentPage)
}
```

И в watcher'е `currentPage`:
```js
currentPage: {
  handler(newPage) {
    this.$store.commit('setSongsTableCurrentPage', newPage)
    this._enqueueHrBatch(this._collectMissingHrSongIds(newPage))
    ...
  },
}
```

`_collectMissingHrSongIds(page)` — фильтрует `songsDigests` по `pageNumber ===
newPage && healthReportText === '-'` и возвращает `songId[]`.

## 6. Альтернативы, которые НЕ выбрали

| Альтернатива | Почему отвергли |
|---|---|
| Использовать `LinkedHashSet` напрямую | Нет `addFirst`. Оборачивать — лишний класс. `ArrayList` + `removeAt(0)` достаточно для N ≤ 1000 на странице. |
| `PriorityBlockingQueue<PrioritizedSongId>` | Избыточно для нашего use-case (нет приоритетов разных типов — только порядок). |
| `KaraokeProcess` (существующая async-queue) | Не нужны цепочки, персистентность, UI-бейджи. Свой сервис проще. |
| Frontend per-song polling на SSE | Каждый songId отдельно — лишний round-trip. Один батч лучше. |
| `ConcurrentHashMap.newKeySet()` + manual order | Атомарность добавита сложна; `removeAt(0)` под `ReentrantLock` — простой и достаточный. |

## 7. Threading-модель

- **HTTP-поток** (вызов endpoint) — короткий: enqueue + return 202.
- **Worker-потоки** (10 штук) — постоянные, берут по 1 songId за раз.
- **SSE-поток** (`SseNotificationService.send`) — broadcast всем emitters,
  быстро (< 10ms).

Нет общих mutable state между worker'ами, кроме `priorityQueue` (под
`priorityLock`) и `inFlight` (lock-free `AtomicBoolean`). Гонок нет.

## 8. Совместимость

- **API**: новый endpoint, старый `/song/healthReportList` без изменений.
- **SSE**: payload `healthReports(songId, dtos)` без изменений — фронт уже
  умеет.
- **HealthReport.kt**: без изменений (мы только вызываем существующий
  `recomputeAndBroadcast`).
- **Тесты**: новый `HealthReportBatchPoolTest.kt`. Существующие тесты — без изменений.

## 9. История изменений

- **2026-09-15**: Initial plan, версия 1.0. Чистовик, без наследования отменённых задач.