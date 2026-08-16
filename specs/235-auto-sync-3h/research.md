# Research: Spring `@Scheduled` + SyncRegistry для автозапуска «Синхронизации в 1 клик»

> Phase 0 output для фичи 235. Источник: внутренний research-агент, Baeldung, Spring Reference/Javadoc, JLS §17.4.

## TL;DR — принятые решения

| # | Проблема | Решение |
|---|----------|---------|
| 1 | Dynamic interval из `KaraokeProperties` | **Фиксированный тик 60 с + ручная проверка** `now - lastRun >= delayMs` (Kotlin `@Volatile var lastRunMs: Long`) |
| 2 | Per-target try/catch + FR-016 (scheduler не должен сломаться) | `for { try { runOne(target) } catch(Throwable) { log.error + record } }` + общий `try/catch(Throwable)` вокруг всего `@Scheduled`-метода |
| 3 | In-process lock «ручной + авто не одновременно» | `AtomicBoolean.compareAndSet(false, true)` + `finally { set(false) }`. Без `synchronized` / `ReentrantLock` |
| 4 | In-memory history ≤ 10 записей | `ConcurrentLinkedDeque<AutoOneClickSyncRun>` + `pollFirst()` при `size > 10` |
| 5 | Spring 3.5 / Kotlin surprises | `@Volatile` + `catch(Throwable)` (не Exception) + member `@Component` (не top-level fun) |

---

## 1. Dynamic interval из KaraokeProperties

### Decision
Вариант **(c)**: фиксированный `@Scheduled(fixedDelay = 60_000L, initialDelay = 5_000L)` тик раз в минуту; внутри метода — проверка `if (System.currentTimeMillis() - lastRunMs >= intervalMs) { doWork(); lastRunMs = now }`. `intervalMs` берётся из `KaraokeProperties.getLong("autoOneClickSyncIntervalMs")` каждый тик.

### Rationale
- `KaraokeProperties` (см. `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProperties.kt:25-63`) — **НЕ** Spring `@ConfigurationProperties`. Свойства грузятся в `MutableMap<String, Any>` (`Karaoke.kt:184`) из Base64-файла, не публикуются в Spring `Environment`. SpEL/`${...}` в `@Scheduled.fixedDelayString` **не работает** (Spring бросит `PlaceholderResolutionException` на старте контекста).
- `KaraokeProperties.kt:99` — `get(key)` сам лениво дёргает `loadPropertiesMap()` при первом вызове. Безопасно вызывать в scheduler-тике (1 раз в минуту), **не** в `@PostConstruct` (порядок init'а fragile).
- Альтернатива (b) `SchedulingConfigurer + Trigger` ([Baeldung §10](https://www.baeldung.com/spring-scheduled-tasks#10-setting-delay-or-rate-dynamically-at-runtime)) — работает, но избыточна: `Trigger.nextExecutionTime` всё равно дёргает `KaraokeProperties`, а рассинхрон с `ConcurrentTaskScheduler` добавляет хрупкости.
- Альтернатива (a) SpEL — частично возможна только для `fixedRateString` (НЕ `fixedDelayString`), плюс SpEL + Kotlin `object`/`companion` имеет давний bug с резолвом ([Baeldung §11](https://www.baeldung.com/spring-scheduled-tasks)).
- Альтернатива (d) cron — не подходит для «каждые N миллисекунд из конфига».

### Альтернативы
- **(b) `SchedulingConfigurer + Trigger`** — канонический путь; нужен, если когда-нибудь потребуется много независимых dynamic-интервалов (например, по scheduler на каждый из 14 `SyncTarget`).
- **(d) cron `0 */${n} * * * ?`** — Spring не поддерживает `${}` внутри полей cron, только целиком.

### Sources
- Baeldung: [The @Scheduled Annotation in Spring](https://www.baeldung.com/spring-scheduled-tasks) (§10 Dynamic scheduling)
- Spring Javadoc: [`@Scheduled.fixedDelayString()`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/annotation/Scheduled.html#fixedDelayString())
- Spring Reference: [Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)

---

## 2. Per-target `try/catch` + FR-016 (scheduler не должен сломаться)

### Decision
Двухуровневая защита:
```kotlin
@Scheduled(fixedDelay = 60_000L, initialDelay = 5_000L)
fun tick() {
    if (!KaraokeProperties.getBoolean("autoOneClickSyncEnabled")) return
    val now = System.currentTimeMillis()
    val interval = KaraokeProperties.getLong("autoOneClickSyncIntervalMs").coerceAtLeast(60_000L)
    if (now - lastRunMs.get() < interval) return
    if (!running.compareAndSet(false, true)) return  // double-tick guard + lock
    val run = AutoOneClickSyncRun(startedAt = Instant.now(), status = "RUNNING")
    try {
        // [1] per-target
        val perTarget = SyncRegistry.all.map { target ->
            try {
                if (target.oneClickDirection == null) {
                    runCatching { /* skip */ }.let { skippedResult(target) }
                } else {
                    val (c, u, d, m) = runEntitySync(target.key, target.oneClickDirection)
                    SyncOneClickResultDto(target.key, target.displayName, target.oneClickDirection.name, false, c, u, d, m)
                }
            } catch (t: Throwable) {
                log.error("[AutoOneClickSyncScheduler] target=${target.key} failed", t)
                SyncOneClickResultDto(target.key, target.displayName, "?", true, emptyList(), emptyList(), emptyList(), emptyList())
            }
        }
        run.finishedAt = Instant.now()
        run.status = "SUCCESS"
        run.perTarget = perTarget
        run.totals = perTarget.aggregate()
        log.info("[AutoOneClickSyncScheduler] tick=... SUCCESS totals=${run.totals}")
    } catch (t: Throwable) {
        // [2] общий рубеж
        run.finishedAt = Instant.now()
        run.status = "FAILED"
        run.reason = "${t::class.simpleName}: ${t.message}"
        log.error("[AutoOneClickSyncScheduler] tick=... FAILED", t)
    } finally {
        history.addLast(run); if (history.size > 10) history.pollFirst()
        running.set(false)
        lastRunMs.set(now)
    }
}
```

### Rationale
- **Внутренний `try { runOne } catch(Throwable)`** — без него `SyncRegistry.all` (≈14 элементов) даст «упал на 3-м, 11 пропущены», что нарушает FR-012.
- **Внешний `try { ... } catch(Throwable)`** — FR-016: даже если `SyncRegistry.all` бросит в `iterator()`, scheduler-бин не должен сломаться. Spring дефолт (`TaskUtils.LOG_AND_SUPPRESS_ERROR_HANDLER`) уже глотает, но: (1) `Throwable` (не `Exception`) пробрасывается выше по умолчанию; (2) `VkAutoPublishScheduler.kt:60` ловит только `Exception` — утечка `Error`.
- **`Throwable` вместо `Exception`** — `OutOfMemoryError` / `StackOverflowError` от сторонней БД или reflection внутри `runOne` не должны убивать scheduler, но они не `Exception`. `runCatching` тоже не ловит `Throwable` (только `Exception`), поэтому НЕ используем.
- **Skip + warn для `oneClickDirection == null`** — закрывает OQ-4.

### Альтернативы
- **Один общий `try { for {...} } catch (Throwable)`** — НЕ достаточно: одна упавшая цель прерывает цикл.
- **AOP-аспект `@Retry`** — overkill: sync-операции не идемпотентны на 100%, retry per-target опасен.
- **Kotlin `runCatching`** — не ловит `Throwable`. Не подходит.

### Sources
- Spring Javadoc: [`TaskUtils.LOG_AND_SUPPRESS_ERROR_HANDLER`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/config/TaskUtils.html)
- Baeldung: [The @Scheduled Annotation in Spring](https://www.baeldung.com/spring-scheduled-tasks) (§3 fixedDelay «слипание»)

---

## 3. In-process lock: «ручной + авто не одновременно»

### Decision
**`AtomicBoolean running`** (общий singleton-bean, `private val running = AtomicBoolean(false)`). Используется в **двух местах**:
1. Внутри scheduler-тика — `if (!running.compareAndSet(false, true)) return` (защита от «слипания» + lock).
2. Внутри `ApiController.postSyncOneClick()` (ручной клик) — **тот же** `running.compareAndSet(false, true)`. Если `false` → `throw SyncInProgressException()` → `ResponseEntity.status(409).body({"error":"sync_in_progress",…})`. Если `true` → `return ResponseEntity.status(409).body(...)` без вызова `runEntitySync`.

### Rationale
- Karaoke-app — **однопроцессное desktop-приложение** (см. AGENTS.md, constitution §«Ограничения и доступы агента»). JMM гарантирует visibility для `volatile`/`AtomicBoolean` между потоками одного процесса.
- `synchronized` — избыточно: блокировка монитора заставит авто-тик **ждать** завершения ручного вызова (минуты!). Нам нужно «не одновременно», а не «по очереди».
- `ReentrantLock.tryLock(0, MILLISECONDS)` — аналогично `AtomicBoolean`, но объёмнее API; `tryLock` поддерживает `Thread.interrupt()` (для нашего кейса лишнее).
- `compareAndSet(false, true)`:
  - **false → true**: захватили, выполняем.
  - **true → true** (не наш): авто-тик пропускается (US1 AC4).
  - **finally**: `running.set(false)`.

### Альтернативы
- **`synchronized(lock)`** — проще синтаксически, но блокирует.
- **Кластерный lock (Redis `SETNX`, JDBC `SELECT … FOR UPDATE`)** — не нужен. Karaoke-app — desktop, не кластер.

### Caveat
Если ручной вызов сам делает `Thread.sleep` или ходит в БД долго — авто-тики будут пропускаться один за другим. Это **корректное поведение** (US1 AC4: «skipped — previous run still in progress»). Отмена ручного — `Future.cancel(true)` (другой уровень сложности, не для текущей задачи).

### Sources
- [`AtomicBoolean.compareAndSet` Javadoc](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/AtomicBoolean.html#compareAndSet(boolean,boolean))
- JLS §17.4 [Memory Model](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.4)

---

## 4. In-memory history ≤ 10

### Decision
`ConcurrentLinkedDeque<AutoOneClickSyncRun>` + ручная проверка `if (history.size > 10) history.pollFirst()` после каждого `addLast`. Singleton bean, отдаётся в API как `List<AutoOneClickSyncRunDto>` (immutable copy).

### Rationale
- Запись раз в 3 часа → ~8 записей/день → десятки за жизнь процесса. Никакого contention.
- `ConcurrentLinkedDeque` — non-blocking, lock-free, основан на Michael & Scott алгоритме. `addLast` (tail) и `pollFirst()` (head) обе O(1) и lock-free.
- `Collections.synchronizedList(new ArrayList<>())` — примитивнее, но требует `synchronized(list) { … }` вокруг **всех** операций, иначе `ConcurrentModificationException`.
- `Collections.synchronizedDeque(new ArrayDeque<>())` — обёртка над `ArrayDeque`, который **не потокобезопасен сам по себе**; внутри `removeFirst()` может бросить `NoSuchElementException` при гонке.
- `size()` у `ConcurrentLinkedDeque` — O(n), для n=10 неважно.

### Альтернативы
- **Кольцевой буфер (массив [10]) + `AtomicInteger` index** — быстрее, но усложняет «read all».
- **Guava `EvictingQueue`** — не thread-safe по умолчанию, лишняя зависимость.

### Sources
- [`ConcurrentLinkedDeque` Javadoc](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/concurrentlinkeddeque.html)
- JLS §17.4 [Memory Model](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.4)

---

## 5. Spring 3.5 / Kotlin surprises

### Decision
- Используем `fixedDelay` (НЕ `fixedRate`) — не «догоняем» при длинной sync.
- `catch(Throwable)` (НЕ `Exception`) — как в `VkAutoPublishScheduler` уже НЕ правильно (`Exception` не ловит `Error`).
- Scheduler-метод — **member `@Component`-класса** (НЕ top-level `fun` — Kotlin top-level функции не сканируются `ScheduledAnnotationBeanPostProcessor`).
- `suspend fun` НЕ используем — проверка `gradle/libs.versions.toml` показала, что `kotlinx-coroutines-reactor` НЕ нужен для нашего варианта (синхронный scheduler).
- `@Volatile var lastRunMs: Long` в singleton bean — visibility между scheduler-thread (из `ConcurrentTaskScheduler`) и request-thread (из Tomcat).

### Rationale
- **`fixedDelay` слипание** ([Baeldung §3](https://www.baeldung.com/spring-scheduled-tasks)): если задача длиннее интервала, новый тик ждёт завершения старого. `ReschedulingRunnable` сериализует вызовы одного метода, **даже** с 4-поточным `ConcurrentTaskScheduler` (который параллелит только **между** методами).
- **`ConcurrentTaskScheduler` пул = 4 потока** (см. `KaraokeAppApplication.kt:28-33`): параллелизм **между** `@Scheduled` методами, не внутри. Если `SponsrSyncScheduler` (12 ч) зависнет — пул съест 1 поток, остальные 3 — для других scheduler'ов. **Лечение на будущее**: отдельный `scheduler = "heavyPool"` bean для тяжёлых scheduler'ов (НЕ в scope этой фичи).
- **Исключение в `@Scheduled`** — Spring дефолт `TaskUtils.LOG_AND_SUPPRESS_ERROR_HANDLER` логирует и suppress'ит. Scheduler **не останавливается**. Но `Throwable` (не `Exception`) пробрасывается выше — поэтому `catch(Throwable)` обязателен.
- **Kotlin**: `@Scheduled` на `suspend fun` требует `kotlinx-coroutines-reactor` ([Javadoc `Scheduled` Kotlin suspending](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/annotation/Scheduled.html)).

### Caveat
- **SpEL/`${}` в `@Scheduled` НЕ работает** с `KaraokeProperties` (см. п. 1).
- **`spring.task.scheduling.pool.size` из `application.yml` НЕ применяется** при нашем кастомном `ConcurrentTaskScheduler` bean (см. [Baeldung §11.1](https://www.baeldung.com/spring-scheduled-tasks)).

### Sources
- Baeldung: [The @Scheduled Annotation in Spring](https://www.baeldung.com/spring-scheduled-tasks)
- Spring Reference: [Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- Spring Javadoc: [`@Scheduled`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/annotation/Scheduled.html), [`TaskUtils`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/config/TaskUtils.html)

---

## Подводные камни конкретно для Karaoke (Spring 3.5 + Kotlin 1.x + KaraokeProperties)

1. **`KaraokeProperties` не в Spring Environment** → `${}` / SpEL в `@Scheduled` не работает.
2. **`KaraokeProperties.get(key)` ленивый** → OK в scheduler-тике, плохо в `@PostConstruct`.
3. **Kotlin `object` / `companion`** в SpEL `T(...)` имеет баги резолва — не использовать.
4. **Top-level `fun` в Kotlin** не сканируется `ScheduledAnnotationBeanPostProcessor` — только `@Component` member.
5. **`suspend fun @Scheduled`** требует `kotlinx-coroutines-reactor` — не применимо.
6. **`ConcurrentTaskScheduler(4 потока)`** — пул общий для всех scheduler'ов; тяжёлые job'ы могут исчерпать пул.
7. **`spring.task.scheduling.pool.size`** НЕ применяется при кастомном bean.

---

## Принятые для фичи 235 решения (mapping)

| Спека | Решение из research |
|---|---|
| FR-001..FR-003 (каждые 3 ч, `fixedDelay`, `initialDelay`) | Пункт 1: внутренний опрос `intervalMs`/`initialDelayMs` из `KaraokeProperties`; внешний `@Scheduled(fixedDelay = 60_000L, initialDelay = 5_000L)` |
| FR-007 + US1 AC2 (lock + 409) | Пункт 3: `AtomicBoolean running`, общий singleton bean, используется в scheduler-тике И в `ApiController.postSyncOneClick()` |
| FR-009 (REST `GET /api/sync/auto-status`) | Пункт 4: `ConcurrentLinkedDeque`, immutable copy в DTO |
| FR-012 (per-target try/catch) | Пункт 2: внутренний `for { try { runOne } catch(Throwable) { log + record } }` |
| FR-016 (fail-fast на сбое БД) | Пункт 2: внешний `try { … } catch(Throwable) { log + FAILED }` |
| SC-009 (scheduler не падает при сбое БД) | Пункт 2 + Пункт 5: `catch(Throwable)` + `TaskUtils` дефолт |
