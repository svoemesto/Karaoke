# Spec #129 — Бейдж количества заданий в пуле HealthReportBatchPool

> **OpenProject**: #129 «Бейдж количества заданий в пуле healthReport».
> **Тип**: Task (конкретная техническая фича).
> **Связь**: Pass 128 (#128, PR #504) — пул `HealthReportBatchPool` уже есть,
> сейчас добавляем только визуализацию.
> **Knowledge-first MUST #0**: выполнен — см. «Knowledge References» ниже.

## 1. Проблема

После Pass 128 (PR #504) на бэке есть `HealthReportBatchPool` — приоритетная
очередь, в которую фронт ставит батчи песен для асинхронного пересчёта
HealthReport. У админа нет способа увидеть, сколько заданий **сейчас** в
очереди, без захода в логи бэка или БД.

## 2. Решение (одно предложение)

Бэк при каждом изменении размера очереди (`queueSize()`) рассылает через
**существующий SSE-канал** новое событие `HEALTH_REPORT_POOL_COUNT` (с
подавлением дублей). Фронт подписывается на это событие в `App.vue`,
обновляет Vuex-state, и в `ProcessWorker.vue` рядом с серым бейджем
`countWaiting` (KaraokeProcess) появляется **зелёный бейдж** с числом.

Расположение — **симметрично серому бейджу**:

```
┌──────────────────────┐
│     Прогресс-бар      │
├──────────────────────┤
│   ╔═══╗  ╔═══╗       │   ← кнопка Старт/Стоп (40×40px)
│   ║ ▶ ║  ║ ⏹ ║       │
│   ╚═══╝  ╚═══╝       │
│  ╱╲       ╱╲         │
│ 5  ←─── green ──→   12  ←─ grey (countWaiting)
└──────────────────────┘
```

- **Слева** (зелёный, `#28a745`): `healthReportPoolCount` — размер пула HR.
- **Справа** (серый, `gray`): `countWaiting` — размер KaraokeProcess.

## 3. Acceptance criteria

### 3.1. Backend

1. Новый тип `SseNotificationType.HEALTH_REPORT_POOL_COUNT`.
2. Новый payload `HealthReportPoolCountMessage(count: Long)`.
3. Helper `HealthReportBatchPool.sendPoolCountMessage(count: Long)` —
   рассылает через `SNS.send(...)` с **подавлением дублей** через
   `@Volatile lastSentQueueSize: Long?` (паттерн скопирован из
   `KaraokeProcessWorker.sendCountWaitingMessage`).
4. Вызов `sendPoolCountMessage(queueSize().toLong())`:
   - В конце `enqueue(...)` (после мутации очереди).
   - В `workerLoop` сразу после успешного `takeNext(...)` (когда id != null).

### 3.2. Frontend

1. `App.vue`: case `HEALTH_REPORT_POOL_COUNT` → `this.setHealthReportPoolCount(userEvent.data)`.
2. `Processes/store.js`:
   - state: `healthReportPoolCount: '...'`
   - getter: `getHealthReportPoolCount(state)`
   - mutation: `setHealthReportPoolCount(state, { count })`
   - action: `setHealthReportPoolCount(ctx, payload)` → commit.
3. `ProcessWorker.vue`:
   - computed `healthReportPoolCount()`.
   - Новый `<div class="text-count-waiting-green" v-text="healthReportPoolCount" />`
     в шаблоне (рядом с серым бейджем).
   - CSS `.text-count-waiting-green { background-color: #28a745; ...; left: 0; }`.

### 3.3. Тесты

- 3 новых unit-теста на `lastSentQueueSize` (подавление дублей, трекинг
  после enqueue, трекинг при повторе).
- Все 13/13 тестов PASS.
- ktlint PASS.
- webvue3 lint + build PASS.

### 3.4. Hard gates

- gradle compile + ktlint PASS.
- webvue3 lint + Vite build PASS.
- Нет новых JPA/MP4/Sanitizer violations.

## 4. Knowledge References (MUST #0)

**Прочитано** в этой сессии:

- `knowledge/domains/sse/domain.md` — SSE-канал `HEALTH_REPORTS`, broadcast,
  `addressedTypes` (Pass 341 P2).
- `knowledge/domains/health/components/health-report-batch-pool.md` —
  контракт пула (Pass 128).
- `knowledge/domains/health/components/race-fixed-65.md` — паттерн
  single-flight.
- `knowledge/domains/monitoring/components/log-categories.md` —
  категория `infra.cache.hrpool`.

**Прямой код прочитан**:

- `webvue3/src/components/Common/ProcessWorker.vue` — полностью (257 строк).
- `webvue3/src/components/Processes/store.js:42-50, 92-105, 190-205, 314-325`.
- `webvue3/src/App.vue:355-410` (case'ы SSE).
- `karaoke-app/.../model/SseNotification.kt:43-53` — payload `healthReports`.
- `karaoke-app/.../model/SseNotificationType.kt` — список типов.
- `karaoke-app/.../model/Messages.kt:47-49` — `ProcessCountWaitingMessage`.
- `karaoke-app/.../services/SseNotificationService.kt:109-146` — `send`.
- `karaoke-app/.../KaraokeProcessWorker.kt:560-820` — образец
  `sendCountWaitingMessage` + `lastSentCountWaiting`.
- `karaoke-app/.../services/KaraokeAppService.kt:10, 53` — `lateinit var SNS`.

**Searched → no relevant docs**: подробного описания `ProcessWorker.vue` и
`Processes/store.js` в `knowledge/` нет (существующий пробел — не блокирует).

## 5. Архитектура (кратко)

```
┌──────────────────────────────────────────────────────────────────────┐
│   HealthReportBatchPool                                              │
│   ── enqueue ───────────► sendPoolCountMessage(N) ──► SNS.send(…)    │
│   ── workerLoop ────────► sendPoolCountMessage(N) ──► SNS.send(…)    │
└──────────────────────────────────────────────────────────────────────┘
                                │ SSE broadcast
                                ▼
┌──────────────────────────────────────────────────────────────────────┐
│   webvue3 App.vue: case 'HEALTH_REPORT_POOL_COUNT'                    │
│   ──► setHealthReportPoolCount(data)                                  │
│        ──► $store.dispatch('setHealthReportPoolCount', payload)       │
│             ──► Processes/store.js action                            │
│                  ──► commit('setHealthReportPoolCount')              │
│                       ──► state.healthReportPoolCount                 │
└──────────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌──────────────────────────────────────────────────────────────────────┐
│   ProcessWorker.vue                                                  │
│   healthReportPoolCount computed → v-text="healthReportPoolCount"    │
│   ──► <div class="text-count-waiting-green">N</div>                   │
└──────────────────────────────────────────────────────────────────────┘
```

**Подавление дублей** — **только на бэке** (`@Volatile lastSentQueueSize`).
На фронте просто `state.count = payload.count` (атомарная запись Vuex).

## 6. Что НЕ делаем (Out of scope)

- Не делаем бейдж с **временем ожидания** в очереди (отдельная фича).
- Не показываем `healthReportPoolCount` где-либо ещё, кроме `ProcessWorker.vue`.
- Не делаем отдельный тип SSE для `inFlight.size` (одновременно исполняемых)
  — это уже видно через `PROCESS_COUNT_WAITING` (там WORKING + WAITING).
- Не делаем WebSocket fallback — SSE работает надёжно.

## 7. Файлы к изменению/созданию

**Создать**:
- `specs/129-hrpool-badge/spec.md` — спека.
- `specs/129-hrpool-badge/report.md` — отчёт для OpenProject.

**Изменить**:
- `karaoke-app/.../model/SseNotificationType.kt` — новый enum.
- `karaoke-app/.../model/SseNotification.kt` — helper `healthReportPoolCount`.
- `karaoke-app/.../model/Messages.kt` — новый data class.
- `karaoke-app/.../services/HealthReportBatchPool.kt` — поле, helper, вызовы.
- `karaoke-app/.../test/.../HealthReportBatchPoolTest.kt` — новые тесты.
- `webvue3/src/App.vue` — case + метод-прокси.
- `webvue3/src/components/Processes/store.js` — state/getter/mutation/action.
- `webvue3/src/components/Common/ProcessWorker.vue` — computed + template + CSS.

**Обновить Knowledge SSoT**:
- `knowledge/domains/sse/domain.md` — добавлен тип `HEALTH_REPORT_POOL_COUNT`.

## 8. История изменений

- **Pass 129** (2026-09-15): Initial. Автор: agent (Karaoke).