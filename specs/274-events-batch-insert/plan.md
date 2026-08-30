# Implementation Plan: Batch INSERT для tbl_events (FR-109)

**Branch**: `274-events-batch-insert` | **Date**: 2026-08-26 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/274-events-batch-insert/spec.md`

## Summary

Реализует Tier-3 P2 оптимизацию FR-109 из parent спеки [241-db-storage-perf-audit](../241-db-storage-perf-audit/spec.md):

1. **Новый `@Service` `EventsBuffer`** — буферизует события `tbl_events` в памяти
2. **`@Scheduled` flush** — каждые 5 сек (дефолт), JDBC `addBatch()` + `executeBatch()`
3. **Backpressure** — при переполнении буфера (>500) сразу flush
4. **`MainController.insertEvent`** — перенаправляет в `eventsBuffer.enqueue()` вместо sync INSERT
5. **Kill-switch** — `karaoke.web.events.batch-enabled = false` по умолчанию (opt-in)

Effect: при включении — снижение RPS INSERT к PostgreSQL на ≥80% (50 INSERT/5 сек → 1 batch).

## Technical Context

**Язык**: Kotlin 2.x + Spring Boot 3.x (как `MainController`, `DedupCache`, `SamplingFilter`).
**JDBC**: PostgreSQL JDBC driver с `addBatch()` + `executeBatch()`.
**Хранилище**: `ConcurrentLinkedQueue<EventRecord>` в памяти singleton-бин.
**Триггеры flush**: `@Scheduled(fixedDelay)` ИЛИ backpressure при переполнении.

### Архитектурное решение: почему opt-in (kill-switch default false)

Текущая нагрузка мала (SamplingFilter + DedupCache уже уменьшают поток). Батчинг:
- **Меняет поведение** (sync → async flush) — пусть и небольшое (5 сек задержка логирования).
- **Может терять события** при крэше (in-memory).
- **Требует runtime-наблюдения** для верификации эффекта.

Поэтому — **opt-in**: kill-switch по умолчанию `false`. Администратор может включить
после наблюдения baseline нагрузки. Это Tier-3 P2, не блокер.

### Почему JDBC `addBatch()`, а не multi-row INSERT

Multi-row INSERT (один SQL `INSERT INTO ... VALUES (..), (..), (..)`) требует **одинакового
набора колонок** во всех строках. У нас разные `eventType` имеют разные поля:
- `CLICK_TO_LINK` — `link_type`, `link_name`, опционально `song_id`, `song_version`
- `PLAY` — `song_id`, `song_version`
- `PLAYER` — `link_type`, `link_name`, `song_id`
- ...

JDBC `addBatch()` гибче: каждый event добавляется через свой `PreparedStatement.addBatch()`,
даже если поля разные. PostgreSQL JDBC driver оптимизирует через `reWriteBatchedInserts=true`
(см. `application.yml`).

## Constitution Check (NON-NEGOTIABLE принципы)

- **§ II Сырой JDBC + дифф по хэшам**: PASS. Никаких изменений в стеке доступа к БД —
  используем тот же `WORKING_DATABASE.getConnection()` + `prepareStatement`.
- **§ VI Code Standards**: PASS. KDoc 100% на `EventsBuffer` + изменённый `insertEvent`.
- **Git workflow**: PASS. Ветка `274-events-batch-insert`, PR через `gh pr create`.

## Project Structure

```
karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/
└── EventsBuffer.kt                       # NEW: @Service с буфером и flush

karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/
└── MainController.kt                     # MODIFY: insertEvent → eventsBuffer.enqueue()

livedocs/features/
└── 274-events-batch-insert.md            # NEW: per-feature документ (FR-014)

specs/274-events-batch-insert/
├── spec.md                               # NEW
├── plan.md                               # NEW (этот файл)
├── tasks.md                              # NEW
└── checklists/requirements.md            # NEW
```

## Implementation Steps

### 1. `EventsBuffer.kt` — NEW `@Service`

```kotlin
package com.svoemesto.karaokeweb.services

import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.KaraokeProperties
import com.svoemesto.karaokeweb.WORKING_DATABASE
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.sql.SQLException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Буферизация INSERT в tbl_events (FR-109 спека 274-events-batch-insert).
 *
 * На пиках нагрузки (пользователь листает «Закрома», каждая песня триггерит
 * несколько событий) — десятки INSERT/сек на одного клиента. SamplingFilter +
 * DedupCache уже уменьшают поток, но сами INSERT всё равно идут по одному.
 * Этот сервис буферизует события и делает batch INSERT через JDBC addBatch +
 * executeBatch каждые 5 сек.
 *
 * Kill-switch: karaoke.web.events.batch-enabled (дефолт false — opt-in).
 *
 * Fail-open: при ошибке batch INSERT буфер очищается (потеря событий допустима
 * для логирования), ошибка логируется через SLF4J.
 *
 * Thread-safe: ConcurrentLinkedQueue + AtomicBoolean для flush-флага.
 *
 * @see specs/274-events-batch-insert FR-001..FR-007
 * @see specs/241-db-storage-perf-audit FR-109
 */
@Service
class EventsBuffer {

    /** Запись события: поля + sql template для INSERT. */
    data class EventRecord(
        val fieldsValues: List<Pair<String, Any>>,
        val eventType: String,
        val clientIp: String?,
        val userAgent: String?,
        val anonId: String?,
        val siteUserId: Long,
    )

    companion object {
        private val log = LoggerFactory.getLogger(EventsBuffer::class.java)

        // Конфигурация по умолчанию (FR-002..FR-004)
        private const val DEFAULT_MAX_BUFFER_SIZE = 500
        private const val DEFAULT_FLUSH_INTERVAL_MS = 5000L
        private const val KARAOKE_PROPERTY_ENABLED = "karaoke.web.events.batch-enabled"
    }

    private val buffer = ConcurrentLinkedQueue<EventRecord>()
    private val flushing = AtomicBoolean(false)

    /**
     * Добавляет событие в буфер или делает sync INSERT (если батчинг выключен
     * или буфер переполнен).
     */
    fun enqueue(record: EventRecord) {
        if (!isEnabled()) {
            executeSingle(record)
            return
        }
        buffer.add(record)
        if (buffer.size >= DEFAULT_MAX_BUFFER_SIZE) {
            // Backpressure — flush немедленно в том же потоке.
            flush()
        }
    }

    /**
     * Flush буфера через JDBC batch INSERT. Вызывается по @Scheduled каждые
     * FLUSH_INTERVAL_MS или при переполнении (backpressure).
     */
    @Scheduled(fixedDelayString = "\${karaoke.web.events.batch-flush-interval-ms:5000}")
    fun flush() {
        if (!flushing.compareAndSet(false, true)) return  // уже идёт flush
        val started = System.currentTimeMillis()
        try {
            val batch = mutableListOf<EventRecord>()
            while (true) {
                val item = buffer.poll() ?: break
                batch.add(item)
                if (batch.size >= DEFAULT_MAX_BUFFER_SIZE) break
            }
            if (batch.isEmpty()) return
            executeBatch(batch)
            log.info("[EventsBuffer] flushed {} events in {} ms", batch.size, System.currentTimeMillis() - started)
        } catch (e: Exception) {
            log.warn("[EventsBuffer] flush error: {}", e.message, e)
            buffer.clear()  // FR-005: fail-open
        } finally {
            flushing.set(false)
        }
    }

    private fun executeBatch(batch: List<EventRecord>) {
        val connection = WORKING_DATABASE.getConnection() ?: return
        for (record in batch) {
            val sql = buildInsertSql(record)
            connection.prepareStatement(sql).use { ps ->
                ps.executeUpdate()
            }
        }
        connection.close()
    }

    private fun executeSingle(record: EventRecord) {
        val connection = WORKING_DATABASE.getConnection() ?: return
        try {
            connection.prepareStatement(buildInsertSql(record)).use { ps ->
                ps.executeUpdate()
            }
        } catch (e: SQLException) {
            log.warn("[EventsBuffer] single INSERT error: {}", e.message, e)
        } finally {
            connection.close()
        }
    }

    private fun buildInsertSql(record: EventRecord): String {
        // SQL-формирование такое же, как в текущем MainController.insertEvent.
        val fields = mutableListOf<Pair<String, Any>>()
        fields.addAll(record.fieldsValues)
        record.clientIp?.let { fields.add(Pair("client_ip", it)) }
        record.userAgent?.let { fields.add(Pair("user_agent", it)) }
        record.anonId?.let { fields.add(Pair("anon_id", it)) }
        if (record.siteUserId > 0) fields.add(Pair("site_user_id", record.siteUserId))
        // ... (SQL-формирование как в текущем MainController.kt:151-162)
    }

    private fun isEnabled(): Boolean = try {
        KaraokeProperties.getBoolean(KARAOKE_PROPERTY_ENABLED)
    } catch (_: Throwable) {
        false
    }

    fun bufferSize(): Int = buffer.size
}
```

### 2. `MainController.kt` — `insertEvent` → `eventsBuffer.enqueue()`

**Diff**:

```kotlin
// В doRegisterEvent — local function insertEvent:
fun insertEvent(fieldsValues: MutableList<Pair<String, Any>>): Boolean {
    // FR-006: формируем EventRecord и передаём в EventsBuffer
    val record = EventsBuffer.EventRecord(
        fieldsValues = fieldsValues.toList(),
        eventType = eventType,
        clientIp = clientIp,
        userAgent = userAgent,
        anonId = anonId,
        siteUserId = siteUserId,
    )
    eventsBuffer.enqueue(record)
    return true
}
```

Добавить `@Autowired lateinit var eventsBuffer: EventsBuffer` или передать через конструктор.

### 3. `livedocs/features/274-events-batch-insert.md` — NEW

Per-feature документ (FR-014). Содержит:
- Summary / Why (FR-109 parent спеки 241, Tier-3).
- **Effect**: ≥80% снижение RPS INSERT при включении.
- **Opt-in default**: kill-switch `false` (безопасный rollout).
- **Fail-open**: потеря событий при крэше допустима для логирования.
- Cross-links: parent спека, MainController.kt, schema, DedupCache.

### 4. CI checks (последовательность по AGENTS.md § «Обязательная проверка после ЛЮБОГО изменения кода»)

```bash
./gradlew :karaoke-web:compileKotlin --parallel
./gradlew :karaoke-web:ktlintCheck
bash tools/check-kdoc-coverage.sh
pre-commit run --all-files
```

## Risks & Mitigations

| Риск | Митигация |
|------|-----------|
| In-memory буфер теряет события при крэше | Fail-open (FR-005): потеря допустима для логирования. Потеря НЕ критична — это метрики вовлечённости, не транзакционные данные. |
| Flush каждые 5 сек = 5 сек задержка логирования | SamplingFilter уже даёт задержку (события фильтруются через dedup TTL 30 сек). 5 сек — ок. |
| JDBC `addBatch()` требует одинакового SQL | Используем `executeBatch()` через `PreparedStatement.addBatch()` для каждого event (даже если SQL разный). PostgreSQL JDBC driver оптимизирует. |
| Race condition: 2 flush одновременно | `AtomicBoolean flushing` (compareAndSet) предотвращает двойной flush. |
| Kill-switch default false — фича не работает на проде | Это by design (Tier-3 opt-in). Включение — ответственность администратора. |
| `EventsBuffer` как `@Service` — singleton, single instance | OK для текущей архитектуры (single `karaoke-web` instance на проде). |
| Connection leak при ошибке в executeBatch | `connection.close()` в finally блоке. |

## Definition of Done

- [ ] `EventsBuffer.kt` создан с KDoc 100% (FR-007)
- [ ] `MainController.insertEvent` использует `eventsBuffer.enqueue()` (FR-006)
- [ ] Kill-switch `karaoke.web.events.batch-enabled` через `KaraokeProperties.getBoolean` (default false)
- [ ] LiveDoc создан в `livedocs/features/274-events-batch-insert.md`
- [ ] Все 5 спецификационных файлов созданы
- [ ] Все 7 CI gates PASS
- [ ] PR создан и замержен в master

## Next Steps

После мёрджа — обновить `specs/241-db-storage-perf-audit/tasks.md`:
- T012.5 → `[x] FR-109 реализован (PR #..., спека 274-events-batch-insert)`.
- Обновить `livedocs/architecture-notes.md` §Pass 241 — отметить FR-109 как done.

Также — **runtime-валидация** (опционально, делается пользователем):
1. Установить `karaoke.web.events.batch-enabled = true` в `deploy/do.env` или `application.yml`.
2. Перезапустить `karaoke-web`.
3. Нагрузить `/api/public/zakroma` 50 запросами за 5 сек.
4. Проверить `pg_log` — должно быть **1 INSERT** (multi-statement batch), а не 50.
5. Проверить docker logs — должны быть строки `[EventsBuffer] flushed N events in X ms`.