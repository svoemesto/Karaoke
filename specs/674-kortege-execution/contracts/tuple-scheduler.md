# Contract: планирование и исполнение кортежа

**Feature**: `674-kortege-execution` · **Тип**: внутренний контракт очереди (`tbl_processes`, сырой JDBC)
**Владелец**: `KaraokeProcess` + `KaraokeProcessWorker` + пакет `tuple/`
**Связанные FR**: FR-001, FR-002, FR-003, FR-006, FR-007, FR-010, FR-011, FR-016, FR-018

Контракт описывает **что** очередь обязана гарантировать. Конкретные сигнатуры классов — в
[data-model.md](../data-model.md) §4; реализация — `tasks.md` (следующий этап).

---

## 1. Идентификация

| Понятие | Правило |
|---|---|
| Кортеж | множество строк `tbl_processes` с одним `process_chain_id` |
| Голова | строка, у которой `process_chain_id = id` (у одиночного задания — сама строка) |
| Шаг | строка с `process_chain_id ≠ id` |
| Индекс шага | `process_order`, `0..N-1` |
| Лейн | `thread_id`; один живой поток на лейн |
| Живой кортеж | есть хотя бы одна строка `WAITING`/`WORKING`/`CREATING` с `process_deleted_at IS NULL` |
| Завершённый кортеж | все строки `DONE` |
| Проваленный кортеж | все строки терминальны (`DONE`/`ERROR`) и есть `ERROR` |

## 2. Выбор на лейн (заменяет `KaraokeProcess.getProcessesToStart`)

**Вход**: `database`, `throwOnError`.
**Выход**: `Map<threadId, KaraokeProcess>` — не более одной **головы** на лейн.

SQL (псевдокод контракта; финальная форма — `tuple/TupleSchedulerSql.SELECT_TUPLE_HEADS`):

```sql
WITH heads AS (
    SELECT h.id, h.thread_id, h.process_priority
    FROM tbl_processes h
    WHERE h.process_chain_id = h.id
      AND h.process_deleted_at IS NULL
      AND EXISTS (
          SELECT 1 FROM tbl_processes s
          WHERE s.process_chain_id = h.id
            AND s.process_status = 'WAITING'
            AND s.process_deleted_at IS NULL
      )
), ranked AS (
    SELECT id, ROW_NUMBER() OVER (
               PARTITION BY thread_id
               ORDER BY process_priority, id
           ) AS rn
    FROM heads
)
SELECT p.* FROM tbl_processes p JOIN ranked r ON r.id = p.id WHERE r.rn = 1;
```

Обязательные свойства (проверяются unit-тестом формы SQL):

1. Выбираются только строки с `process_chain_id = id` — шаг кортежа не выбирается никогда.
2. Кортеж попадает в выборку только при наличии `WAITING`-шага (голова может быть `DONE` —
   это resume, см. §4).
3. Сортировка — `(process_priority, id)`; `process_order` **не** участвует в межкортежном сравнении.
4. Фильтр `process_deleted_at IS NULL` применяется и к голове, и к шагу.
5. Один кортеж на лейн за итерацию (`rn = 1`).

## 3. Захват шага

```
claim(step):
    UPDATE tbl_processes SET process_status='WORKING', process_start=now
    WHERE id = ? AND process_status = 'WAITING'
```

- `claim` возвращает `false`, если обновлено не 1 строку; поток не исполняет шаг.
- `claim` не сбрасывает `process_end`/`process_percentage*` — их сбрасывает нормализация/recovery
  (§5), а `process_end` пишется терминальным переходом.
- После `claim` шаг исполняется **только** циклом своего кортежа.

## 4. Исполнение кортежа

```
execute(chainId):
    step = firstRunnable(chainId)           # MIN(process_order) WHERE status='WAITING' AND not deleted
    if step == null: return DONE            # нечего исполнять
    loop:
        if forceStopped: markWaiting(step); return STOPPED
        if not claim(step): return STOPPED  # строку забрал кто-то другой/удалили
        result = runStep(step)              # argv со {scope}, redirectErrorStream(true), exit code
        if forceStopped: markWaiting(step); return STOPPED
        if not result.ok:
            markError(step, result.reason)
            failTuple(chainId, result.reason)   # все не-DONE шаги → ERROR, priority=-1
            return FAILED(result.reason)
        markDone(step)
        next = nextWaiting(chainId)          # MIN(process_order) WHERE status='WAITING' AND not deleted
        if next == null: completeTuple(chainId); return DONE   # priority=999
        step = next
```

Гарантии:

| № | Гарантия | Как обеспечена |
|---|---|---|
| G1 | шаги кортежа не перемежаются с чужими | цикл живёт внутри потока лейна, новые потоки лейна не стартуют, пока жив текущий (`KaraokeProcessWorker.kt:1154-1200`) |
| G2 | шаги кортежа не параллельны | один поток; второй поток на тот же лейн не стартует |
| G3 | шаг N+1 не стартует при не-`DONE` N | `nextWaiting` ищет `WAITING` только после `markDone`; планировщик шаги не выбирает |
| G4 | ненулевой код/исключение → весь кортеж `ERROR` | `markError` + `failTuple` |
| G5 | причина видна оператору | `process_description` (хвост ` | ERROR: …`, лимит 500 символов) |
| G6 | один шаг = цикл из одной итерации | голова-одиночка: `nextWaiting` → `null` |
| G7 | force-stop возвращает кортеж в `WAITING` | `markWaiting(step)`; остальные шаги уже `WAITING`; backstop `setWorkingToWaiting` |
| G8 | мягкая остановка доигрывает текущий кортеж | `stopAfterThreadIsDone` блокирует старт нового кортежа, цикл не прерывает текущий |

## 5. Нормализация и восстановление (`start()`)

Порядок при старте воркера:

1. `purgeFinishedTuples()` — удалить строки кортежей, все строки которых `DONE` (и одиночные `DONE`).
   **Никогда** не удалять `DONE`-строку кортежа с не-`DONE` шагами.
2. `normalizeTuples()`:
   - `process_chain_id IS NULL` → `process_chain_id = id, process_order = 0`;
   - безголовая цепочка (`NOT EXISTS id = process_chain_id`) → голова = строка с `MIN(process_order)`,
     её `process_chain_id = id`, `process_order = 0` пересчитывается;
   - `CREATING` → `WAITING`;
   - `WORKING` → `WAITING` с `process_end = NULL`, `process_percentage* = 0`;
   - строки soft-deleted кортежей без живых шагов — лог, лейн не блокируется.
3. `TupleJanitor.sweep()` — уборка осиротевших scope-каталогов (см. контракт `tuple-cli.md` §5).

Все операции идемпотентны и не зависят от порядка запуска.

## 6. Single-flight и чистота строк

`createProcess(song, action, doWait=true, prior, threadId, context)`:

1. Найти строки `(song_id, process_type, thread_id)`.
2. Если среди них есть живая (`WAITING/WORKING/CREATING`, не удалённая) — **вернуть `0`**, ничего
   не удаляя и не изменяя (включая `DONE`-голову живого кортежа).
3. Удалить только кортежи, все строки которых терминальны (`DONE`; `ERROR` сохраняются для retry).
4. `KEY_BPM_FROM_FILE`: если `applyKeyBpmFromFileIfExists()` вернул `true` (файл есть **и**
   sidecar-отпечаток совпал) — вернуть `0`.
5. Валидация `args` до вставки (сохраняется).
6. Создать строки: голова первой, затем `process_chain_id = head.id`, `order = index + 1`;
   для одиночного — `markAsHead()` (`chain_id = id`, `order = 0`).

Все прочие построители цепочек (StemJob, CPU-retry, Utils) используют тот же путь создания
(`createChain`), чтобы у каждой цепочки был `process_chain_id`.

## 7. Retry (админский)

| Вход | Поведение |
|---|---|
| `POST /api/admin/processes/{id}/retry`, строка кортежа | все `ERROR`-шаги кортежа → `WAITING`; `priority` кортежа = приоритет головы (если голова утрачена — `1`); одна audit-запись `RETRY` на кортеж; возвращается DTO головы |
| `POST .../retry`, одиночная строка (`chain_id = id`) | как сейчас: `ERROR → WAITING`, приоритет не меняется (у одиночного он и есть приоритет кортежа) |
| Строка не `ERROR` / soft-deleted / не найдена | `409` / `404` (как сейчас) |

## 8. Ошибки и наблюдаемость

| Событие | Лог (stdout) | БД |
|---|---|---|
| старт кортежа | `Tuple[<threadId>]: старт кортежа chain=<id> шагов=<N> песня=<songId>` | — |
| шаг | `Tuple[<threadId>]: шаг <k>/<N> <type> chain=<id>` | `WORKING` |
| шаг завершён | `Tuple[<threadId>]: шаг <k>/<N> DONE` | `DONE` |
| сбой шага | `Tuple[<threadId>]: шаг <k>/<N> ERROR: <reason>` | `ERROR` всем не-`DONE` |
| кортеж завершён | `Tuple[<threadId>]: кортеж chain=<id> DONE` | `priority=999` |
| кортеж провален | `Tuple[<threadId>]: кортеж chain=<id> FAILED: <reason>` | `priority=-1` |
| нормализация | `Tuple: нормализовано строк: <n> (chainless=<x>, headless=<y>, creating=<z>, working=<w>)` | — |

SSE: `recordChange` для `tbl_processes` по-прежнему шлётся из `save()`/`saveProgress()` для каждой
изменённой строки; фронт получает текущий шаг и прогресс (FR-015).
