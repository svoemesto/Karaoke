# Data Model: подавление дублей PROCESS_COUNT_WAITING

**Дата**: 2026-08-12
**Фича**: 177-fix-process-count-waiting-spam

## Что меняется в модели данных

**Ничего.** Фича не трогает БД, DTO, формат wire-протокола.

Единственное изменение — добавление одного in-memory volatile-поля в
`KaraokeProcessWorker` (Kotlin object, runtime-состояние). Это
runtime-кеш, не часть data-модели проекта.

## Entities

### LastSentCountWaiting (in-memory, runtime-only)

| Поле | Тип | Nullable | Описание |
|---|---|---|---|
| `lastSentCountWaiting` | `Long?` | да | Последнее значение `countWaiting`, фактически отправленное через `SNS.send` в SSE-канал. `null` = «ещё не отправляли» (после рестарта бэкенда или после `start()`). |

**Объявление** (Kotlin, в `KaraokeProcessWorker` companion object):

```kotlin
@Volatile private var lastSentCountWaiting: Long? = null
```

**Аннотация `@Volatile`** — поле читается/пишется как минимум из
потока `doStart()` (демон-поток воркера очереди) и из HTTP-потоков,
вызывающих `KaraokeProcess.createDbInstance(...)` / `run()` /
`forceStop()` (которые в итоге зовут `sendCountWaitingMessage`).
Без `@Volatile` запись могла бы быть не видна другому потоку вовремя
(JMM не гарантирует visibility для обычного `var`).

**Жизненный цикл**:

- **Инициализация**: `null` при загрузке класса (BSS/init в JVM).
- **Сброс в `null`**: в `KaraokeProcessWorker.start()` — перед
  первой отправкой после `deleteDone`/`setWorkingToWaiting`,
  чтобы выполнить FR-007 («одно начальное сообщение при старте
  воркера, даже если значение совпало с последним известным»).
- **Обновление**: внутри `sendCountWaitingMessage` ПОСЛЕ успешного
  решения «отправлять» (т.е. после проверки на дубль, но ПЕРЕД
  вызовом `SNS.send`).
- **Чтение**: внутри `sendCountWaitingMessage` для сравнения.

## Несуществующие / ненужные сущности

- ❌ `tbl_count_waiting` (БД-таблица) — не нужна.
- ❌ DTO `LastSentCountWaitingDto` — поле runtime-only, не передаётся
  через wire.
- ❌ Per-subscriber state — подавление работает на стороне продьюсера,
  не зависит от количества подписчиков (см. research.md § 2
  «Alternatives considered → A»).
- ❌ Heartbeat-событие — отдельная фича для US2 (Priority P2),
  текущая итерация закрывает только US1 (Priority P1).

## Связь с существующими сущностями

| Существующая сущность | Связь | Поведение после фикса |
|---|---|---|
| `KaraokeProcessWorker.sendCountWaitingMessage(Long)` | Метод-обёртка над `SNS.send` для события `PROCESS_COUNT_WAITING` | Дедупликация в начале метода; early return при совпадении с `lastSentCountWaiting` |
| `SseNotification.processCountWaiting(ProcessCountWaitingMessage)` | Конструктор SSE-сообщения | Без изменений |
| `SNS.send(SseNotification)` | Fan-out SSE-подписчикам | Без изменений |
| `ProcessCountWaitingMessage(countWaiting: Long)` | DTO события | Без изменений (payload на wire тот же) |
