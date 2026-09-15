# Report #130 — Бейдж количества WAITING-задач HealthReport (голубой)

> **OpenProject**: #130.
> **Spec**: [spec.md](spec.md).
> **PR**: [#506](https://github.com/svoemesto/Karaoke/pull/506).
> **Branch**: `400-hrwaiting-badge`.
> **Status**: In review (CI running).

## Что сделано

### Backend (`karaoke-app`)

- **`SseNotificationType.kt`** — новый enum `HEALTH_REPORT_WAITING_COUNT("healthReportWaitingCount")`.
- **`Messages.kt`** — новый data class `HealthReportWaitingCountMessage(val count: Long)`.
- **`SseNotification.kt`** — helper `healthReportWaitingCount(message: HealthReportWaitingCountMessage): SseNotification`.
- **`HealthReport.kt`**:
  - Companion `waitingCountBySongId: ConcurrentHashMap<Long, AtomicLong>` —
    in-memory счётчик.
  - Companion `@Volatile private var lastSentWaitingCount: Long?` — подавление
    дублей (паттерн скопирован из `HealthReportBatchPool.lastSentQueueSize`).
  - Companion `sendWaitingCountMessage(count: Long)` — рассылает через
    `SNS.send(...)` с подавлением.
  - Companion `waitingCountTotal(): Long` — сумма значений по всем ключам.
  - Companion `resetLastSentWaitingCountForTest()` +
    `clearWaitingCountBySongIdForTest()` — test hooks.
  - `recomputeAndBroadcast(...)` после `val reports = song.healthReportList()`:
    ```kotlin
    val waitingCount = reports.count { it.healthReportStatus == WAITING }
    waitingCountBySongId.compute(songId) { _, _ ->
        if (waitingCount == 0) null else AtomicLong(waitingCount.toLong())
    }
    sendWaitingCountMessage(waitingCountTotal())
    ```
  - `try/catch (e: Exception)` вокруг `SNS.send` — одна ошибка рассылки не
    валит pool (логируется через `infra.cache.hrpool`).

- **`HealthReportWaitingCountTest.kt`** (новый, 6 тестов):
  - `waitingCountBySongId is empty by default` PASS.
  - `waitingCountTotal sums values across songs` PASS.
  - `compute callback removes entry when count becomes zero` PASS.
  - `lastSentWaitingCount starts null after reset` PASS.
  - `sendWaitingCountMessage suppresses duplicates` PASS.
  - `resetLastSentWaitingCountForTest clears state` PASS.

**Backend hard gates**:
- `:karaoke-app:compileKotlin` — PASS.
- `:karaoke-app:ktlintCheck` — PASS.
- `:karaoke-app:test --tests HealthReportWaitingCountTest` — **6/6 PASS за 0.15s**.

### Frontend (`webvue3`)

- **`App.vue`**:
  - В `subscribeToSse`-обработчике: новый case `HEALTH_REPORT_WAITING_COUNT` →
    `this.setHealthReportWaitingCount(userEvent.data)`.
  - Новый метод `setHealthReportWaitingCount(userEventData)` —
    `this.$store.dispatch('setHealthReportWaitingCount', userEventData)`.

- **`Processes/store.js`**:
  - state: `healthReportWaitingCount: '...'`.
  - getter: `getHealthReportWaitingCount(state)`.
  - mutation: `setHealthReportWaitingCount(state, userEventData)` —
    `state.healthReportWaitingCount = userEventData.count`.
  - action: `setHealthReportWaitingCount(ctx, userEventData)` →
    `ctx.commit('setHealthReportWaitingCount', userEventData)`.

- **`ProcessWorker.vue`**:
  - computed `healthReportWaitingCount()` —
    `this.$store.getters.getHealthReportWaitingCount`.
  - В template: `<div class="text-count-waiting-blue" v-text="healthReportWaitingCount" />`
    рядом с зелёным/серым бейджами.
  - CSS `.text-count-waiting-blue { background-color: #17a2b8; ...; top: 0; left: 100%; }` —
    голубой бейдж в правом верхнем углу кнопки.
  - KDoc-комментарий обновлён — упомянут SSE `HEALTH_REPORT_WAITING_COUNT`.

**Frontend hard gates**:
- `npm run lint` — PASS.
- `npm run build` — PASS (built in 7.34s).

### Knowledge SSoT

- **`knowledge/domains/sse/domain.md`** — добавлен тип `HEALTH_REPORT_WAITING_COUNT`
  в таблицу событий с пояснением про `HealthReport.waitingCountBySongId` и
  связь с Pass 128/130.

## Acceptance criteria

| # | Критерий | Статус |
|---|---|---|
| 4.1.1 | Новый `SseNotificationType.HEALTH_REPORT_WAITING_COUNT` | ✅ |
| 4.1.2 | Новый `HealthReportWaitingCountMessage(count: Long)` | ✅ |
| 4.1.3 | Helper `SseNotification.healthReportWaitingCount(...)` | ✅ |
| 4.1.4 | `HealthReport.companion` поля + helpers + test hooks | ✅ |
| 4.1.5 | Обновление в `recomputeAndBroadcast` | ✅ |
| 4.2.1 | `App.vue` case + метод-прокси | ✅ |
| 4.2.2 | `Processes/store.js` state/getter/mutation/action | ✅ |
| 4.2.3 | `ProcessWorker.vue` computed + template + CSS | ✅ |
| 4.3 | 6/6 unit-тестов | ✅ |
| 4.4 | ktlint + Vite build + JPA + MP4 | ✅ |

## Файлы в коммите

```
M  karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReport.kt
M  karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Messages.kt
M  karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SseNotification.kt
M  karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SseNotificationType.kt
A  karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/HealthReportWaitingCountTest.kt
M  knowledge/domains/sse/domain.md
M  webvue3/src/App.vue
M  webvue3/src/components/Common/ProcessWorker.vue
M  webvue3/src/components/Processes/store.js
A  specs/130-hrwaiting-badge/spec.md
```

10 файлов, +451 строка.

## Что осталось владельцу

1. CI: дождаться 7/7 PASS на PR #506.
2. Merge: `gh pr merge --merge` (без `--delete-branch`).
3. Docker: `deploy/do.sh build_karaoke-app && build_webvue3`.
4. Deploy (по согласию AGENTS.md Tier-1).
5. Ручная проверка: при cold-start `StorageMetadataCache` голубой бейдж растёт
   и убывает синхронно с прогрессом fill'а.
6. `tracker.sh close-issue 130`.

## Известные ограничения

- **`waitingCountBySongId` — in-memory**, сбрасывается при рестарте бэкенда.
  После рестарта счётчик наполняется заново по мере пересчёта HR для всех
  песен (минуты). Приемлемо для UI-визуализации.

— отчёт для #130