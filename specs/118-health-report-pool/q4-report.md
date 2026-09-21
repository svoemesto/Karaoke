# Report: OpenProject #123 — «UI-бейдж синего цвета в ProcessWorker.vue»

> **Статус**: ✅ done, merged в master через PR #493
> **PR**: <https://github.com/svoemesto/Karaoke/pull/493>
> **Ветка**: `396-hr-pool-badge` (заархивирована после merge)
> **Карта**: child of wayfinder-map #119 (specs/118)

## TL;DR

Реализован UI-бейдж синего цвета в правом верхнем углу кнопки Старт/Стоп
в `ProcessWorker.vue`, отображающий количество WAITING-заданий в lane
`THREAD_LANE_HEALTH_REPORT = 1`. Бейдж обновляется **через live SSE**
(новое событие `PROCESS_LANE_COUNT_WAITING`), с initial poll как fallback
на случай задержки SSE-reconnect.

## Что сделано

### Backend (live SSE, не initial poll)

1. **`SseNotificationType.kt`**: добавлен enum-член `PROCESS_LANE_COUNT_WAITING`.

2. **`Messages.kt`**: добавлен data class `ProcessLaneCountWaitingMessage(threadId, countWaiting)`.

3. **`SseNotification.kt`**: добавлен factory-метод `processLaneCountWaiting(...)`.

4. **`KaraokeProcessWorker.kt`**:
   - Поле `lastSentLaneCountWaiting: Map<Int, Long>` — дедупликация per-lane
     (паттерн повторяет существующий `lastSentCountWaiting`).
   - Метод `sendLaneCountWaitingMessage(threadId, countWaiting)` —
     отправляет `PROCESS_LANE_COUNT_WAITING` с подавлением дублей.
   - Приватный метод `sendLaneCountWaitingMessagesForAllLanes()` —
     перебирает 5 lane'ов (HEAVY_RENDER, LIGHT_BACKGROUND, REMOTE_STORE_UPLOAD,
     HEALTH_REPORT, STEM_JOBS) и вызывает уже-расширенный в #125
     `KaraokeProcess.getCountWaiting(threadId = ...)`.
   - Интегрирован в существующий `sendCountWaitingMessage` —
     дополнительный cost только когда общий счётчик реально изменился.
   - Сброс `lastSentLaneCountWaiting` в `start()` для гарантии начального
     сообщения при старте воркера.

### Frontend

5. **`webvue3/src/components/Processes/store.js`**:
   - State `countWaitingByThreadId: { [threadId]: count }`.
   - Getter `getCountWaitingByThreadId(threadId)`.
   - Mutation `setCountWaitingByThreadId({ threadId, countWaiting })` —
     с реактивностью через spread.
   - Action `getProcessesCountWaitingByThreadIdPromise({ threadId })` —
     POST `/api/processes/countwaiting?threadId={threadId}`.
   - Action `setCountWaitingByThreadId(payload)` — commit-обёртка.

6. **`webvue3/src/components/Common/ProcessWorker.vue`**:
   - Prop `poolThreadId: Number, default undefined`.
   - Computed `poolCountWaiting` — через getter.
   - Initial poll `checkCountWaitingByThreadId(threadId)` в `mounted()` —
     fallback при SSE-reconnect.
   - Template: `<div class="text-pool-size" v-if="poolThreadId !== undefined && poolCountWaiting >0" />`.
   - Style `.text-pool-size`: position absolute, top:0 right:0,
     background-color `var(--bs-primary, #0d6efd)`.

7. **`webvue3/src/App.vue`**:
   - SSE case `PROCESS_LANE_COUNT_WAITING` → `setCountWaitingByThreadId`.
   - Метод `setCountWaitingByThreadId(userEventData)` (симметричный
     существующему `setCountWaiting`).
   - Передача `:pool-thread-id="1"` для кнопки Старт/Стоп в шапке админки.

## Семантика (жизненный цикл)

1. Воркер стартует → `lastSent* = null` → `sendCountWaitingMessage` шлёт
   `PROCESS_COUNT_WAITING` + `sendLaneCountWaitingMessagesForAllLanes`
   шлёт 5×`PROCESS_LANE_COUNT_WAITING` (по одному на lane).
2. Создание/завершение задания → те же 6 событий, но дедупликация
   отсекает неизменившиеся lane'ы.
3. UI получает `PROCESS_LANE_COUNT_WAITING` → App.vue → store →
   `ProcessWorker.vue` → синий бейдж обновляется реактивно.
4. Если SSE-reconnect задержался — initial poll в `mounted()` даёт
   первое значение (без 5-секундного ожидания).

## Совместимость

- Существующий серый бейдж KaraokeProcess (правый нижний) **продолжает
  работать как раньше** через `PROCESS_COUNT_WAITING` + `getCountWaiting()`.
- `poolThreadId` — опциональный пропс (default `undefined`). Где не
  передано — бейдж пула не показывается.
- Существующие call-sites `<ProcessWorker>` без `poolThreadId` не затронуты.

## Проверки (kara-post-edit Pass 239+245)

| Шаг | Команда | Результат |
|-----|---------|-----------|
| Compile | `:karaoke-app:compileKotlin` | ✅ BUILD SUCCESSFUL |
| Lint Kotlin | `:karaoke-app:ktlintCheck` | ✅ (после исправления spacing) |
| Package | `:karaoke-app:bootJar` | ✅ |
| Lint JS | `cd webvue3 && npm run lint` | ✅ no errors |
| Build JS | `cd webvue3 && npm run build` | ✅ built in 7.45s |
| Format JS | `cd webvue3 && npx prettier --check` | ✅ (после --write) |
| MP4 guard | `tools/check-no-mp4-mentions.sh` | ✅ 130 grandfathered |

## CI (PR #493)

12/12 checks PASS:
- ✅ Baseline stats
- ✅ Docs (structure + offline links)
- ✅ ESLint + Prettier (karaoke-public)
- ✅ ESLint + Prettier (webvue3) — после Prettier auto-format
- ✅ JSDoc coverage
- ✅ KDoc coverage
- ✅ Knowledge SSoT impact
- ✅ Knowledge SSoT structure
- ✅ docker-image-tags guard
- ✅ ktlint (Kotlin/Java) — после spacing fix
- ✅ no-jpa-imports guard
- ✅ no-mp4-mentions guard

## Файлы изменены

```
karaoke-app/src/main/kotlin/.../KaraokeProcessWorker.kt   | 78 +++++++
karaoke-app/src/main/kotlin/.../model/Messages.kt         | 14 +++
karaoke-app/src/main/kotlin/.../model/SseNotification.kt  |  4 +
karaoke-app/src/main/kotlin/.../model/SseNotificationType.kt |  2 +
webvue3/src/App.vue                                       | 18 +-
webvue3/src/components/Common/ProcessWorker.vue           | 49 +++
webvue3/src/components/Processes/store.js                 | 31 +++
8 files changed, 197 insertions(+), 1 deletion(-)
```

2 коммита:
1. `SSE PROCESS_LANE_COUNT_WAITING + синий бейдж пула HR (specs/118 #123)`
2. `ProcessWorker.vue: prettier auto-format (checkCountWaitingByThreadId chain)`

## Out of scope

- ❌ Не создавал новых `class KaraokeProcess` / `data class KaraokeProcess*`.
- ❌ Не менял SQL-запросы.
- ❌ Не создавал новых сущностей в БД.
- ❌ Не трогал существующую логику обработки процессов
  (`startRepairAll`, `onRepairProcessFinished`, `getProcessesToStart`,
  `createProcess`).
- ❌ Не трогал существующее API `countWaiting` — оно осталось без изменений
  (новый параметр `threadId` опционален, default = `null`).

## Связанные документы

- Карта #119: <http://localhost:8080/work_packages/119>
- Исходная задача #118: <http://localhost:8080/work_packages/118>
- Backend #125 report: <specs/118-health-report-pool/q5-report.md> (PR #491)
- Фикс всплытия #122 report: <specs/118-health-report-pool/q3-report.md> (PR #492)
- Q1 research: <specs/118-health-report-pool/q1-answer.md>
- Q2 research: <specs/118-health-report-pool/q2-answer.md>
- Спека: <knowledge/domains/processing/components/async-process-queue.md>

---

**Готов к review владельца**. После одобрения и merge (уже выполнено):
work_package #123 → close.