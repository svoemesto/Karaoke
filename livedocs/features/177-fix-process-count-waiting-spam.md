---
status: Active
slug: 177-fix-process-count-waiting-spam
related:
  - ../domain/processing.md
  - ../features/187-site-traffic-anomaly-investigation.md
  - ../architecture/L3-components.md
  - ../../specs/177-fix-process-count-waiting-spam/spec.md
---

# 177 — Устранение спама PROCESS_COUNT_WAITING в SSE-канале (LiveDoc)

> Drill-down — [specs/177-fix-process-count-waiting-spam/spec.md](../../specs/177-fix-process-count-waiting-spam/spec.md).

## Что делает

На странице админки `webvue3` при `/api/subscribe?tabId=...` шёл **непрерывный
поток одинаковых SSE-сообщений** вида `{"type":"PROCESS_COUNT_WAITING","data":{"countWaiting":0}}`.

Зачем — непонятно (никакой информации, никакого прогресса, просто спам).
Штуки этого шума:
- забивал сетевой канал и CPU на сериализацию SSE-фреймов,
- маскировал реальные события,
- скрывал «дрейф» счётчика (когда значение менялось, оператор не понимал, дошло ли обновление).

**После фикса**: в SSE-канал идёт **ровно одно** сообщение `PROCESS_COUNT_WAITING`
на каждое **уникальное** значение `countWaiting`. При простое очереди —
**ни одного** сообщения.

Для сигнала «жив ли воркер» используется уже существующее событие
`PROCESS_WORKER_STATE` (флаг `isWork`) — heartbeat не нужен.

## User Stories (краткий список)

- **US1** (P1): В SSE-канал больше нет дублирующих одинаковых сообщений `PROCESS_COUNT_WAITING`.
- **US2** (P2): Heartbeat «очередь жива» — отдельно, через `PROCESS_WORKER_STATE` (без `countWaiting`).

## Functional Requirements (указатель)

- **FR-001**: Подавление дублей на стороне бэкенда (не клиента): один фактический вызов `sendCountWaitingMessage` вместо многих.
- **FR-002**: Track последнего отправленного `countWaiting` на стороне продьюсера.
- **FR-003**: При фактическом изменении числа — отправить новое сообщение (без дублей).
- **FR-004**: Удалить/заменить периодический вызов `sendCountWaitingMessage` в `doStart`.
- **FR-005**: `PROCESS_WORKER_STATE` (с `isWork`) остаётся как heartbeat-сигнал.

## Acceptance Criteria

- [ ] **AC1**: 60 секунд простоя + пустая очередь → **0** сообщений `PROCESS_COUNT_WAITING` с `countWaiting == 0`.
- [ ] **AC2**: Появилось WAITING-задание → **ровно одно** сообщение с актуальным `countWaiting > 0`.
- [ ] **AC3**: Подряд одинаковые `countWaiting` → подавляются (не более одного сообщения).
- [ ] **AC4**: Несколько вкладок админки → каждая получит корректное (одно) сообщение на изменение, дублирования нет.

## Связанные LiveDocs

- Domain: [processing.md](../domain/processing.md) (queue, HEAVY_RENDER/LIGHT_BACKGROUND lanes)
- Architecture: [L3-components.md](../architecture/L3-components.md) (SSE Hub компонент)
- Feature: [187-site-traffic-anomaly-investigation.md](../features/187-site-traffic-anomaly-investigation.md) (похожий паттерн — подавление шума + батч-отправка)

## Код

- Backend: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/sse/SseNotification.kt` — `sendCountWaitingMessage()` + dedup logic
- Backend: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/sse/SNS.kt` — продьюсер события
- Frontend: `webvue3/src/store/streamingchat.js` (обработчик PROCESS_COUNT_WAITING — без изменений, просто реже срабатывает)

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14