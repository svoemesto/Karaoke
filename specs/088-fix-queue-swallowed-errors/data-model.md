# Data Model: Единообразная обработка сбоев БД в главном цикле очереди

**Feature**: [spec.md](./spec.md) | **Research**: [research.md](./research.md)

Схема БД не меняется — эта фича целиком про обработку ошибок внутри уже
существующих функций чтения `tbl_processes`, без новых таблиц/колонок.

## Операция чтения очереди (существующая сущность — функции `KaraokeProcess.getCountWaiting()`/`getProcessesToStart()`)

| Атрибут | До фикса | После фикса |
|---|---|---|
| Сигнатура | `(database: KaraokeConnection): Long` / `: Map<Int, KaraokeProcess>` | `(database: KaraokeConnection, throwOnError: Boolean = false): ...` — тот же тип возврата, обратно совместимо |
| Поведение при `connection == null` | `return 0L` / `return emptyMap()`, лог | при `throwOnError=false` — без изменений; при `true` — `throw SQLException(...)` |
| Поведение при `SQLException` внутри запроса | `catch { e.printStackTrace() }`, возврат накопленного (нулевого/пустого) результата | при `throwOnError=false` — без изменений; при `true` — `throw e` |
| Закрытие `Statement`/`ResultSet` (`finally`) | тихо ловит и логирует | не меняется независимо от `throwOnError` |

## Вызывающие места (концептуальная классификация — не отдельные сущности)

| Место вызова | `throwOnError` | Почему |
|---|---|---|
| `KaraokeProcessWorker.doStart()` (через `getKaraokeProcessesToStart()`, ~955; напрямую ~979, ~1028) | `true` | Единственное место, где нужна видимая, retry-триггерящая ошибка (US1) |
| `KaraokeProcess.createDbInstance()` (~744, HTTP-путь создания задания) | `false` (не меняется) | FR-004 — не вносить новый отказ в создание задания |
| `KaraokeProcessThread.run()` (~204, отдельный per-job поток) | `false` (не меняется) | Косметическое уведомление о счётчике не должно ошибочно перевести исполняющееся задание в ERROR |
| `KaraokeProcessWorker.forceStop()` (~1090, HTTP-путь) | `false` (не меняется) | Финальное уведомление после уже выполненной важной работы (убийство контейнеров, сброс процессов) не должно превращаться в HTTP 500 |
| `KaraokeProcessWorker.start()` (~536, HTTP-путь, до `Thread{...}`) | `false` (не меняется) | Синхронный вызов на HTTP-потоке `/api/processes/workerstartstop`, до начала retry-защищённого цикла — найдено при сверке T004 |
| `RenderQueueStalledCheck`/`LaneStalledCheck` (мониторинг) | `false` (не меняется) | FR-005 — вне scope; и так уже безопасно обёрнуты `MonitoringService.tick()`, трогать незачем |

## Валидационные правила (из Functional Requirements)

- FR-001/FR-002/FR-003: все 3 вызова внутри `doStart()` используют
  `throwOnError=true` — ошибка одинаково пробрасывается как
  `SQLException`, тот же тип, что уже пробрасывает `.save()`, и ловится тем
  же `catch (e: Exception)` в `KaraokeProcessWorker.start()` (specs/087) —
  без отдельных правок в самом retry-механизме.
- FR-004/FR-005: 3 существующих вызывающих места + оба мониторинг-чека не
  меняются вовсе (используют дефолт `throwOnError=false`) — гарантия
  отсутствия регрессии обеспечивается самим значением параметра по
  умолчанию, а не отдельной логикой.
