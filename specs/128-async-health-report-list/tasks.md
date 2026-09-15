# Tasks #128 — Асинхронный healthReportList через батч + SSE

> **Spec**: [spec.md](spec.md).
> **Plan**: [plan.md](plan.md).
> **OpenProject**: #128 (claimed by ai-agent, status: In progress).
> **Hard-gates reference**: `AGENTS.md` v3.1.0 § «Hard Gate» и «Обязательная проверка после изменения».

## 0. Pre-flight (Knowledge-first MUST #0)

- [x] **0.1** Прочитать `knowledge/README.md` и `knowledge/domains/README.md`
- [x] **0.2** Прочитать `knowledge/domains/health/domain.md` и `components/health-report.md`
- [x] **0.3** Прочитать `knowledge/domains/health/components/race-fixed-65.md` (single-flight паттерн)
- [x] **0.4** Прочитать `knowledge/domains/sse/domain.md`
- [x] **0.5** Прочитать `knowledge/system/frontend/store-health-report-monitor.md`
- [x] **0.6** Прочитать `knowledge/domains/processing/components/async-process-queue.md` (паттерн executor'а)
- [x] **0.7** Прочитать `knowledge/adr/0008-tracker-openproject-migration.md` (трекер)
- [x] **0.8** Grep по `knowledge/` 3 попытки (`healthReportList`, `healthReportBatch`, `hrpool`, `priorityQueue`) → паттерна нет, описываем с нуля
- [x] **0.9** Зафиксировать в spec.md: «Searched: ... → no relevant docs для приоритетного пула»

## 1. OpenProject workflow

- [x] **1.1** `tracker.sh claim-issue 128` (выполнено)
- [ ] **1.2** Реализация → `tracker.sh add-comment 128 --file specs/128-async-health-report-list/report.md`
- [ ] **1.3** `tracker.sh mark-review 128`
- [ ] **1.4** Ждём close от владельца (после ревью)

## 2. Git workflow (CI-gate для master)

- [ ] **2.1** `N=$(./tools/reserve-branch-number.sh async-hrlist)` → получить номер
- [ ] **2.2** `git checkout -b "${N}-async-hrlist" master`
- [ ] **2.3** Все правки — на этой ветке, **НИКОГДА** в master
- [ ] **2.4** После завершения: `git push -u origin "${N}-async-hrlist" && gh pr create --base master`
- [ ] **2.5** `gh pr checks` → CI 7/7 PASS (ktlint + Vite build + ...)
- [ ] **2.6** `gh pr merge --merge` (БЕЗ `--delete-branch`)
- [ ] **2.7** Пересобрать Docker-образы (Pass 374) — после merge, отдельной задачей

## 3. Backend — `HealthReportBatchPool.kt`

- [ ] **3.1** Создать `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/HealthReportBatchPool.kt`
  - [ ] Класс `@Service`, конструктор с `KaraokeConnection`, `KaraokeStorageService`, `StorageApiClient`
  - [ ] Поле `priorityQueue: MutableList<Long>` под `ReentrantLock`
  - [ ] Поле `inFlight: ConcurrentHashMap<Long, AtomicBoolean>`
  - [ ] Поле `executor: ExecutorService = Executors.newFixedThreadPool(10)`
  - [ ] Метод `fun enqueue(songIds: List<Long>)`:
    - Под `priorityLock`: для каждого id в `songIds.asReversed()` — `remove` + `add(0, id)`
    - Затем `wakeupWorkers()` (10 новых submit'ов — дубликаты безвредны)
  - [ ] Метод `fun queueSize(): Int` (для тестов и диагностики)
  - [ ] `@PostConstruct fun start()`: `repeat(10) { executor.submit(::workerLoop) }`
  - [ ] `@PreDestroy fun stop()`: `executor.shutdown()` + `awaitTermination(5, SECONDS)` + `shutdownNow()`
  - [ ] `private fun workerLoop()`: takeNext → tryEnter → recomputeAndBroadcast → exit в finally
  - [ ] `private fun takeNext(): Long?` под `priorityLock`
  - [ ] `private fun tryEnter(songId: Long): Boolean` через `compareAndSet(false, true)`
  - [ ] `private fun exit(songId: Long)`: `inFlight[songId]?.set(false)`
  - [ ] `private fun wakeupWorkers()`: `repeat(POOL_SIZE) { executor.submit(::workerLoop) }`
  - [ ] SLF4J-логгер с категорией `infra.cache.hrpool` (см. log-categories.md update)

## 4. Backend — Endpoint в `ApiController.kt`

- [ ] **4.1** Добавить `@Autowired lateinit var healthReportBatchPool: HealthReportBatchPool`
- [ ] **4.2** Добавить endpoint **сразу после** строки 7686 (конец текущего `/song/healthReportList`):
  ```kotlin
  @PostMapping("/song/healthReportListBatch")
  @ResponseBody
  fun healthReportListBatch(@RequestParam songIds: List<Long>): ResponseEntity<Void> {
      healthReportBatchPool.enqueue(songIds)
      return ResponseEntity.accepted().build()
  }
  ```
- [ ] **4.3** KDoc-комментарий с пояснением (fire-and-forget, SSE-push)

## 5. Backend — Unit-тесты

- [ ] **5.1** Создать `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/HealthReportBatchPoolTest.kt`
- [ ] **5.2** Тест `enqueueThenDequeue_sameSongId_doesNotDuplicate`:
  - enqueue([1, 2, 3]); enqueue([1]); assert queueSize() == 3, first() == 1
- [ ] **5.3** Тест `enqueueSameSongIdTwice_secondCallMovesToFront`:
  - enqueue([1, 2, 3]); enqueue([1]); first() == 1 (move-to-front, не дубликат)
- [ ] **5.4** Тест `enqueueBatch_appendsAllAtFront`:
  - enqueue([1, 2, 3]); enqueue([4, 5, 6]); order: [6, 5, 4, 1, 2, 3]
- [ ] **5.5** Тест `executorWith10Threads_runs10SongsConcurrently`:
  - enqueue 10 songId, моки `recomputeAndBroadcast` через spy, проверить что
    worker'ы подхватывают параллельно (latch.await(2, SECONDS))
- [ ] **5.6** Тест `singleFlight_secondConcurrentCallIsSkipped`:
  - мок `recomputeAndBroadcast` с `Thread.sleep(200)`, два worker'а берут
    один songId, проверка что `tryEnter` второго вернул false
- [ ] **5.7** Тест `exceptionInWorker_doesNotCrashPool_otherJobsContinue`:
  - мок бросает исключение на songId=1, enqueue [1, 2, 3], проверить что
    2 и 3 посчитались, пул жив
- [ ] **5.8** Тест `shutdown_doesNotLeaveOrphanedTasks`:
  - enqueue 100 songId, вызвать `stop()`, проверить что executor завершился
    без orphan-задач

## 6. Frontend — `Songs/store.js`

- [ ] **6.1** Добавить action `loadHealthReportBatch(ctx, songIds)`:
  - POST `/api/song/healthReportListBatch` с `params: { songIds: [...] }`
  - Fire-and-forget (catch, не await)
  - **Перед отправкой**: пройти по songIds, найти в `state.songsDigest`,
    поставить `song.healthReportText = '?'` и `song.healthReportColor = '#CCCCCC'`
  - NB: это прямое мутирование (как уже делается в `healthReportMessageByUserEvent`)

## 7. Frontend — `SongsTable.vue`

- [ ] **7.1** Заменить `_enqueueHrRequest` → `_enqueueHrBatch(songIds)`
- [ ] **7.2** Удалить `_processHrQueue`, `hrQueue`, `hrRunning`, `HR_MAX_CONCURRENT`
  (больше не нужны — бэк сам разруливает)
- [ ] **7.3** Заменить watcher `currentPage`: вместо `this.hrQueue = []` +
  `updateHealthReportForCurrentPage` + `_processHrQueue` —
  вызвать `_enqueueHrBatch(this._collectMissingHrSongIds(newPage))`
- [ ] **7.4** Добавить метод `_collectMissingHrSongIds(page)`:
  - Фильтрует `songsDigests` по `songPageNumber === page && song.healthReportText === '-'`
  - Возвращает массив `songId`
- [ ] **7.5** `mounted()` / другие места с `updateHealthReportForCurrentPage` —
  заменить на `_enqueueHrBatch(_collectMissingHrSongIds(currentPage))`
- [ ] **7.6** Оставить `repairAllForCurrentPage` без изменений (legacy-ремонт)

## 8. Knowledge SSoT update (Pass 379 linking protocol)

- [ ] **8.1** `knowledge/domains/health/domain.md` — добавить ссылку на новый
  компонент `health-report-batch-pool.md` в секцию «Структура компонентов»
- [ ] **8.2** Создать `knowledge/domains/health/components/health-report-batch-pool.md`
  по шаблону из `knowledge/templates/component.md`:
  - Назначение (асинхронный батч + SSE)
  - Интерфейсы (enqueue, queueSize, single-flight семантика)
  - Алгоритм (LinkedHashSet-подобная приоритетная очередь + 10 worker'ов)
  - Связь с `HealthReport.recomputeAndBroadcast` и `SseNotificationService.send`
  - Код (путь к `HealthReportBatchPool.kt`)
- [ ] **8.3** `knowledge/domains/health/domain.md` — Known gaps: убрать «UI-сторона
  не описана» (SSE HEALTH_REPORTS уже описан), добавить batch-pool
- [ ] **8.4** `knowledge/domains/monitoring/components/log-categories.md` —
  добавить `infra.cache.hrpool` в таблицу с примером лога
- [ ] **8.5** `python3 tools/lint-knowledge.py` — должен пройти без ошибок

## 9. Build / Lint / Docker (Hard gates)

- [ ] **9.1** `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:compileKotlin --parallel`
- [ ] **9.2** `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:ktlintCheck`
- [ ] **9.3** `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:test --tests "*.HealthReportBatchPoolTest"`
- [ ] **9.4** `cd webvue3 && npm run lint && cd ..`
- [ ] **9.5** `cd webvue3 && npm run build && npm run format:check && cd ..`
- [ ] **9.6** `tools/check-no-jpa-imports.sh` (Pass 379, R-07)
- [ ] **9.7** `tools/check-no-mp4-mentions.sh` (Pass 379, R-11)
- [ ] **9.8** `tools/lint-knowledge.py` (Pass 379, Knowledge structure)

## 10. Финализация

- [ ] **10.1** `git add -A && git status` — проверить, что нет лишних файлов
- [ ] **10.2** `git diff --cached | grep -iE '\.env$|do\.env$|\.key$|\.pem$'` — нет секретов
- [ ] **10.3** `git commit -m "#128: асинхронный healthReportList через батч + SSE"`
- [ ] **10.4** `git push -u origin "${N}-async-hrlist"`
- [ ] **10.5** `gh pr create --base master --title "#128: асинхронный healthReportList" --body "..."`
- [ ] **10.6** `gh pr checks` — дождаться 7/7 PASS
- [ ] **10.7** `gh pr merge --merge` (без `--delete-branch`)
- [ ] **10.8** Написать `specs/128-async-health-report-list/report.md`:
  - Что сделано (файлы, тесты)
  - Acceptance criteria — отметить выполненные
  - Что осталось владельцу (docker rebuild, ручная проверка UI)
- [ ] **10.9** `tracker.sh add-comment 128 --file specs/128-async-health-report-list/report.md`
- [ ] **10.10** `tracker.sh mark-review 128`

## 11. Что осталось владельцу (после merge)

- [ ] **11.1** Пересобрать Docker-образы (`deploy/do.sh build_karaoke-app`, `build_webvue3`)
- [ ] **11.2** Задеплоить на прод через `deploy/do.sh restart_*` (по согласию, см. AGENTS.md Tier-1)
- [ ] **11.3** Проверить в браузере: страница 30 песен без HR открывается за < 2 сек
- [ ] **11.4** Проверить: переключение страниц — приоритетная загрузка
- [ ] **11.5** Если всё ОК: `tracker.sh close-issue 128`

## 12. История изменений

- **2026-09-15**: Initial tasks, версия 1.0. Чистовик.