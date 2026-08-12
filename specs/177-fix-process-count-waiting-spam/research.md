# Research: подавление дублей PROCESS_COUNT_WAITING

**Дата**: 2026-08-12
**Фича**: 177-fix-process-count-waiting-spam
**Связанная спека**: [spec.md](./spec.md)

## Контекст

Админка при обновлении страницы открывает SSE-подписку
`/api/subscribe?tabId=...`. В канал идёт непрерывный поток
одинаковых сообщений:

```json
{"userId":1,"payload":{"type":"PROCESS_COUNT_WAITING","data":{"countWaiting":0}},"timestamp":...}
```

Спам с периодичностью ~10–15 мс. `countWaiting == 0` — потому что в
текущий момент нет WAITING-заданий. Цель — устранить шум.

## 1. Источник шума

**Функция-продьюсер**: `KaraokeProcessWorker.sendCountWaitingMessage(countWaiting: Long)`
([KaraokeProcessWorker.kt:710](karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcessWorker.kt:710)).

```kotlin
fun sendCountWaitingMessage(countWaiting: Long) {
    val messageProcessCountWaiting =
        SseNotification.processCountWaiting(
            ProcessCountWaitingMessage(countWaiting = countWaiting),
        )
    try {
        SNS.send(messageProcessCountWaiting)
    } catch (e: Exception) {
        println(e.message)
    }
}
```

Никакой дедупликации здесь нет — каждый вызов сразу шлёт в `SNS`.

**5 call-sites** (подтверждено codegraph):

1. [`KaraokeProcessWorker.start()` — строка 612](karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcessWorker.kt:612)
   — один раз при старте воркера после `deleteDone`/`setWorkingToWaiting`.
2. [`KaraokeProcess.createDbInstance()` — строка 762](karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcess.kt:762)
   — при создании нового WAITING-процесса (не `tail`).
3. [`KaraokeProcess.run()` / `KaraokeProcessThread.run()` — строка 211](karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcessWorker.kt:211)
   — при старте subprocess для НЕ-`tail`-процесса.
4. `KaraokeProcess.forceStop()` — при принудительной остановке.
5. `KaraokeProcessWorker.doStart()` — внутри главного цикла `while(isWork)`
   (точное место внутри `doStart` ниже строки ~939 — НЕ подтверждено
   статическим чтением, требует runtime-проверки через breakpoint/log).

**Главный подозреваемый** на спам — цикл `doStart` с
`Thread.sleep(timeout=10L)` (см. [KaraokeProcessWorker.kt:734](karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcessWorker.kt:734))
и периодическим вызовом `sendCountWaitingMessage` в одной из ветвей
(либо явно, либо через `getKaraokeProcessesToStart` → создание
«хвостовых» процессов → `createDbInstance`). С учётом того, что
спам ~10–15 мс — это совпадает с `timeout=10L` (см. предположение
в § «Open Questions»).

**Решение по call-sites**: даже если call-site №5 безобидный,
дедупликация на стороне продьюсера (FR-001) всё равно закрывает
проблему полностью — никакой call-site не сможет слать дубли.

## 2. Стратегия дедупликации

### Decision

Добавить в `KaraokeProcessWorker` (companion object, рядом с
`@Volatile var isWork`) поле:

```kotlin
@Volatile private var lastSentCountWaiting: Long? = null
```

и обернуть `sendCountWaitingMessage`:

```kotlin
fun sendCountWaitingMessage(countWaiting: Long) {
    val previous = lastSentCountWaiting
    if (previous != null && previous == countWaiting) return  // подавление дубля
    lastSentCountWaiting = countWaiting
    val message = SseNotification.processCountWaiting(
        ProcessCountWaitingMessage(countWaiting = countWaiting),
    )
    try {
        SNS.send(message)
    } catch (e: Exception) {
        println(e.message)
    }
}
```

В `start()` (строка 612) — сбросить `lastSentCountWaiting = null` ПЕРЕД
первой отправкой, чтобы выполнить FR-007 (одно начальное сообщение
при старте воркера, даже если значение совпало с последним известным).

### Rationale

1. **`@Volatile`** — соответствует стилю уже существующих полей
   (`isWork`, `stopAfterThreadIsDone`, `withoutControl` — все
   `@Volatile`, см. [KaraokeProcessWorker.kt:514](karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcessWorker.kt:514)).
2. **`Long?` вместо `Long = -1`** — `Long.MIN_VALUE` мог бы
   совпасть с реальным значением; nullable явно отделяет «ещё не
   отправляли» от «отправляли 0».
3. **In-memory, не персистится** — после рестарта бэкенда первое
   вычисленное значение будет отправлено (это и нужно — клиент
   после реконнекта должен увидеть актуальное состояние).
4. **Без блокировки** — `compareAndSet`-семантика не нужна: если
   два потока одновременно прочитают одно значение и оба решат
   отправить — это ОК, всё равно лучше, чем спам (см. §
   «Альтернативы»).
5. **FR-007 (одно начальное сообщение при старте)** — `start()`
   сбрасывает `lastSentCountWaiting = null` перед первой отправкой.

### Alternatives considered

- **A. Дедупликация на стороне фронта (`webvue3`)** — отвергнуто:
  - Пропускная способность SSE-канала и сериализация CPU на бэке
    не освободятся; клиент просто выбросит дубли (пассивная
    защита);
  - Несколько вкладок админки каждая получает свою копию и
    каждая вынуждена дублировать фильтр;
  - Нарушает SC-005 «UI не требует изменений».
- **B. `AtomicLong` + `compareAndSet`** — избыточно: гонка
  «два потока прочитали одно значение и оба отправили» не хуже
  текущего поведения (одно лишнее сообщение при экзотической
  гонке). Но `@Volatile var Long?` + проверка «== previous» —
  проще и читается как естественная Kotlin-идиома.
- **C. Поллинг по таймеру с явной задержкой (debounce 200 мс)** —
  отвергнуто: вносит задержку в доставку события до 200 мс
  (SC-002 требует 1–2 секунды — пока вписывается, но хуже, чем
  текущая реакция; нет смысла платить задержкой ради того, что
  дедупликация решает без задержки).
- **D. Убрать периодический вызов из `doStart` без дедупликации** —
  отвергнуто: остаётся 4 других call-sites, любой из которых
  может попасть в спам при других сценариях (например, если
  пользователь быстро кликает «Создать задачу» → «Отмена»).
  Подавление на стороне продьюсера — единая точка защиты.

## 3. Где именно править код

Минимальный diff (на уровне plan'а; конкретные строки могут
сместиться после имплементации):

| Файл | Изменение | Цель |
|---|---|---|
| `KaraokeProcessWorker.kt` | Добавить `@Volatile private var lastSentCountWaiting: Long? = null` рядом с `isWork` | Источник истины |
| `KaraokeProcessWorker.kt` | Переписать `sendCountWaitingMessage` с проверкой `previous == countWaiting` → early return | FR-001 |
| `KaraokeProcessWorker.kt` | В `start()` сбросить `lastSentCountWaiting = null` перед первым вызовом `sendCountWaitingMessage` | FR-007 |
| `KaraokeProcess.kt` | Не менять 3 call-sites напрямую — они уже вызывают `KaraokeProcessWorker.sendCountWaitingMessage(...)`, и фикс на стороне воркера их автоматически защищает | Минимизация diff |

## 4. KDoc / `@see` (FR-006 конституции)

Для нового поля и для переписанной `sendCountWaitingMessage` нужен
KDoc с `@see docs/features/async-process-queue.md` (per-feature
документ уже существует — см. `docs/features/README.md`).

Дополнительно — в KDoc упомянуть, что фикс закрывает спам из PR
`#177-fix-process-count-waiting-spam`.

## 5. Тестирование

### Проверка вручную (на admin-машине)

См. [quickstart.md](./quickstart.md) — детальные шаги.

Краткий сценарий:

1. Собрать `karaoke-app:bootJar`, перезапустить контейнер.
2. В браузере открыть любую страницу админки → DevTools →
   Network → EventStream → фильтр `subscribe`.
3. Подождать 5 минут, ничего не делая.
4. Ожидание: **0** сообщений `PROCESS_COUNT_WAITING` с
   `countWaiting == 0`.
5. Создать новое задание в очереди (например, рендер) →
   ожидание: ровно **1** сообщение с ненулевым `countWaiting` в
   течение 1–2 секунд (SC-002).
6. Завершить задание → ещё **1** сообщение с уменьшенным
   `countWaiting`.

### Что НЕ покрыто тестами

В проекте нет CI/unit-тестов на этот код (см.
[constitution.md § "Тесты"](../../.specify/memory/constitution.md)).
Проверка — только ручная на admin-машине. Это нормально для проекта,
фикс минимальный и локальный, регрессия маловероятна.

## Open Questions

- **Где именно в `doStart` вызывается `sendCountWaitingMessage`?**
  На статическом чтении (codegraph) не подтверждено — codegraph
  указывает на `doStart → sendCountWaitingMessage`, но в показанных
  строках 729–939 явного вызова не видно. Возможно:
  - В блоке после синк-проверки (~940+), который codegraph обрезал.
  - Через `getKaraokeProcessesToStart` → внутренний путь, который
    триггерит `createDbInstance` (но `createDbInstance` уже входит
    в покрытие дедупликации).
  - В одной из cleanup-ветвей (завершившиеся потоки обновили
    счётчик → вызов `sendCountWaitingMessage`).
- **Решение**: реализация фикса (дедупликация на стороне продьюсера)
  закрывает все 5 call-sites единообразно — нет необходимости точно
  знать, какой из них генерирует спам. Если после фикса шум
  останется — диагностика через `grep -n 'sendCountWaitingMessage'`
  в логах admin-машины (`grep "PROCESS_COUNT_WAITING"` в stdout
  `karaoke-app`) покажет оставшиеся источники.
