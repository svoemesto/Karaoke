# Spec #130 — Бейдж количества WAITING-задач HealthReport (голубой)

> **OpenProject**: #130 «Бейдж количества WAITING-задач HealthReport на хранилище (голубой)».
> **Тип**: Task (UI-визуализация поверх #128).
> **Связь**: Pass 128 (#128, PR #504) + Pass 129 (#129, PR #505).
> **Knowledge-first MUST #0**: выполнен — см. «Knowledge References» ниже.

## 1. Проблема

В `HealthReport` есть временный статус `WAITING` (спека #368): он выставляется
в `FILE_VIOLATION`-проверке, когда `StorageMetadataCache` ещё не заполнен и
async fill идёт в фоне. После fill'а `recomputeAndBroadcast` дёргается
повторно и WAITING сменяется на OK/ERROR.

У админа нет способа увидеть, сколько WAITING-записей сейчас в сумме по
проекту — приходится открывать каждую песню и считать руками.

## 2. Решение (одно предложение)

В `HealthReport.companion object` — `ConcurrentHashMap<SongId, AtomicLong>`
`waitingCountBySongId`, обновляемый в `recomputeAndBroadcast` (единая точка).
Сумма по всем ключам рассылается через новый SSE-канал
`HEALTH_REPORT_WAITING_COUNT` с подавлением дублей. Фронт рисует **голубой
бейдж** в правом верхнем углу кнопки Старт/Стоп в `ProcessWorker.vue`.

Расположение — **симметрично серому бейджу `countWaiting`** в правом нижнем:

```
┌──────────────────────┐
│  5  ←── blue ───┐    │
│ ╔═══╗  ╔═══╗    │    │
│ ║ ▶ ║  ║ ⏹ ║    │    │
│ ╚═══╝  ╚═══╝    │    │
│  5  ←── green ───┘ 12  ←─ grey
└──────────────────────┘
```

## 3. Семантика (по решению владельца)

> «Для каждой песни есть количество WAITING-записей в HR. Сумма этого
> количества по всем песням для которых на данный момент получен HR —
> и есть нужное число.»

- Считаем только по песням, для которых **хотя бы раз был сгенерирован HR**
  через `recomputeAndBroadcast`.
- Песни, **ещё в очереди `HealthReportBatchPool`** (HR не получен) — НЕ
  учитываются (нет ключа в `waitingCountBySongId`).
- Песни, у которых HR был получен, но сейчас все записи OK/ERROR —
  удаляются из map (`compute { _, _ -> null }`).

## 4. Acceptance criteria

### 4.1. Backend

1. Новый тип `SseNotificationType.HEALTH_REPORT_WAITING_COUNT`.
2. Новый payload `HealthReportWaitingCountMessage(count: Long)`.
3. Helper `SseNotification.healthReportWaitingCount(message)`.
4. `HealthReport.companion`:
   - `waitingCountBySongId: ConcurrentHashMap<Long, AtomicLong>`.
   - `@Volatile lastSentWaitingCount: Long?` — подавление дублей.
   - `sendWaitingCountMessage(count: Long)` — рассылает через `SNS.send`.
   - `waitingCountTotal(): Long` — сумма значений.
   - `resetLastSentWaitingCountForTest()` / `clearWaitingCountBySongIdForTest()`
     — test hooks.
5. В `recomputeAndBroadcast` после `val reports = song.healthReportList()`:
   ```kotlin
   val waitingCount = reports.count { it.healthReportStatus == WAITING }
   waitingCountBySongId.compute(songId) { _, _ ->
       if (waitingCount == 0) null else AtomicLong(waitingCount.toLong())
   }
   sendWaitingCountMessage(waitingCountTotal())
   ```

### 4.2. Frontend

1. `App.vue`: case `HEALTH_REPORT_WAITING_COUNT` → `setHealthReportWaitingCount`.
2. `Processes/store.js`: state/getter/mutation/action для
   `healthReportWaitingCount`.
3. `ProcessWorker.vue`:
   - computed `healthReportWaitingCount()`.
   - `<div class="text-count-waiting-blue" v-text="healthReportWaitingCount" />`
     в template.
   - CSS `.text-count-waiting-blue { background-color: #17a2b8; top: 0; left: 100%; ... }`.

### 4.3. Тесты

- 6 новых unit-тестов `HealthReportWaitingCountTest` PASS.
- ktlint PASS.
- webvue3 lint + Vite build PASS.

### 4.4. Hard gates

- gradle compile + ktlint PASS.
- webvue3 lint + Vite build PASS.
- Нет новых JPA/MP4/Sanitizer violations.

## 5. Knowledge References (MUST #0)

**Прочитано** в этой сессии:

- `knowledge/domains/sse/domain.md` — таблица SSE-типов, broadcast,
  `addressedTypes` (Pass 341 P2).
- `knowledge/domains/health/components/health-report-batch-pool.md` —
  контракт пула (Pass 128).
- `knowledge/domains/health/components/race-fixed-65.md` — паттерн
  single-flight.
- `knowledge/domains/monitoring/components/log-categories.md` —
  категория `infra.cache.hrpool`.

**Прямой код прочитан**:

- `karaoke-app/.../HealthReport.kt:30-145, 990-1025, 2340-2371` —
  `data class HealthReport`, единственное место возврата WAITING,
  `recomputeAndBroadcast`.
- `karaoke-app/.../HealthReportStatus.kt` — enum со статусом WAITING.
- `karaoke-app/.../HealthReportDTO.kt` — DTO (для понимания, что HealthReport
  НЕ сущность в БД, а пересчитываемый на лету объект).
- `karaoke-app/.../model/SseNotification.kt` — payload существующих типов.
- `karaoke-app/.../model/SseNotificationType.kt` — список типов.
- `karaoke-app/.../model/Messages.kt:47-64` — `ProcessCountWaitingMessage`,
  `HealthReportPoolCountMessage` (образец payload).
- `karaoke-app/.../services/KaraokeAppService.kt:10, 53` — `lateinit var SNS`.
- `karaoke-app/.../services/StorageMetadataCache.kt` — что именно fill идёт.
- `webvue3/src/components/Common/ProcessWorker.vue` (полностью).
- `webvue3/src/components/Processes/store.js:42-50, 92-105, 190-205, 314-325`.
- `webvue3/src/App.vue:355-410` (case'ы SSE).

## 6. Архитектура

```
┌────────────────────────────────────────────────────────────────────────┐
│  HealthReport.recomputeAndBroadcast(songId, …)                          │
│  ── val reports = song.healthReportList()                              │
│  ── SNS.send(SseNotification.healthReports(songId, dtoErrors))         │
│  ── val waitingCount = reports.count { it.healthReportStatus == WAITING }│
│  ── waitingCountBySongId.compute(songId) { _, _ ->                     │
│         if (waitingCount == 0) null else AtomicLong(waitingCount.toLong())│
│     }                                                                   │
│  ── sendWaitingCountMessage(waitingCountTotal())                       │
└────────────────────────────────────────────────────────────────────────┘
                                │ SSE broadcast
                                ▼
┌────────────────────────────────────────────────────────────────────────┐
│  webvue3 App.vue: case 'HEALTH_REPORT_WAITING_COUNT'                    │
│  ──► setHealthReportWaitingCount(data)                                 │
│       ──► $store.dispatch('setHealthReportWaitingCount', payload)      │
│            ──► Processes/store.js action                               │
│                 ──► commit('setHealthReportWaitingCount')              │
│                      ──► state.healthReportWaitingCount                │
└────────────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌────────────────────────────────────────────────────────────────────────┐
│  ProcessWorker.vue                                                     │
│  healthReportWaitingCount computed → v-text                            │
│  ──► <div class="text-count-waiting-blue">N</div>                      │
└────────────────────────────────────────────────────────────────────────┘
```

## 7. Что НЕ делаем (Out of scope)

- НЕ храним `waitingCountBySongId` в БД — он in-memory и сбрасывается при
  рестарте бэкенда. После рестарта счётчик «наполняется заново» по мере
  пересчёта HR для всех песен (это занимает минуты — приемлемо для
  визуализации).
- НЕ делаем отдельный SSE для количества песен в `HealthReportBatchPool` —
  это уже есть в `HEALTH_REPORT_POOL_COUNT` (Pass 128/129, зелёный бейдж).
- НЕ делаем SSE-канал для каждой песни отдельно — UI просто показывает сумму.

## 8. Файлы к изменению/созданию

**Создать**:
- `specs/130-hrwaiting-badge/spec.md` — спека.
- `specs/130-hrwaiting-badge/report.md` — отчёт для OpenProject.
- `karaoke-app/.../HealthReportWaitingCountTest.kt` — unit-тесты.

**Изменить**:
- `karaoke-app/.../HealthReport.kt` — `companion`-поля, `recomputeAndBroadcast`.
- `karaoke-app/.../model/SseNotificationType.kt` — новый enum.
- `karaoke-app/.../model/SseNotification.kt` — helper.
- `karaoke-app/.../model/Messages.kt` — payload.
- `webvue3/src/App.vue` — case + метод-прокси.
- `webvue3/src/components/Processes/store.js` — state/getter/mutation/action.
- `webvue3/src/components/Common/ProcessWorker.vue` — computed + template + CSS.

**Обновить Knowledge SSoT**:
- `knowledge/domains/sse/domain.md` — новый тип.

## 9. История

- **Pass 130** (2026-09-15): Initial. Автор: agent (Karaoke).