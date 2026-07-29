# Contract: `LaneStalledCheck` (новая проверка мониторинга)

**Feature**: [spec.md](../spec.md) | **Data model**: [data-model.md](../data-model.md)

Проект — внутреннее приложение (не библиотека/публичный API), поэтому
«контракт» этой фичи — интерфейс расширения уже существующей подсистемы
мониторинга (`MonitorCheck`), а не HTTP-схема: новых HTTP-эндпоинтов фича не
вводит, переиспользует существующие `/api/monitor/resolve` и SSE-канал
`MONITOR_ALERTS` (см. `MonitoringService.kt`, `MonitorAlert.kt`).

## Интерфейс

```kotlin
object LaneStalledCheck : MonitorCheck {
    override fun run(ctx: MonitorContext): List<MonitorAlert>
}
```

Регистрируется одной строкой в `MonitorRegistry.checks` — как и все
остальные проверки (`ProdContainerCheck`, `RenderQueueStalledCheck` и т.д.).
Вызывается `MonitoringService.tick()` раз в минуту (`@Scheduled(fixedRate = 60_000L)`),
исключения ловятся вызывающей стороной и превращаются в отдельный
WARNING-алерт (`checkFailureAlert`) — `LaneStalledCheck.run()` может
пробрасывать неожиданные исключения без явного `try/catch` внутри себя,
симметрично остальным проверкам.

## Предусловие / когда возвращает алерты

Для каждого `threadId`, у которого в `tbl_processes` есть хотя бы одна
запись со статусом `WAITING`:

- **Не** возвращать алерт, если для этого `threadId` есть активный (`isAlive`)
  поток-обработчик в `KaraokeProcessWorker.threadsMap`, ИЛИ время с момента,
  когда лейн последний раз имел активный процесс, меньше порога простоя
  (см. Assumptions в `spec.md` — по умолчанию ~2 минуты).
- Вернуть один `MonitorAlert` **на каждый** зависший `threadId` — не один
  общий алерт на всю очередь (в отличие от `RenderQueueStalledCheck`, где
  проверяется полная остановка воркера целиком, здесь диагностика — per-lane).
- Если `KaraokeProcessWorker.isWork == false` — эта ситуация уже покрыта
  существующим `RenderQueueStalledCheck`; `LaneStalledCheck` не обязан
  дублировать этот алерт (можно либо не запускаться при `!isWork`, либо
  вернуть согласованный по смыслу, но не дублирующий по `key` результат —
  решается на этапе реализации, не меняет внешний контракт).

## Постусловие / `resolveAction`

`resolveAction` для алерта конкретного `threadId`:

1. Точечно возвращает в `WAITING` записи этого `threadId`, которые числятся
   `WORKING`, но не имеют живого потока-обработчика в `threadsMap` (см.
   Кандидат C, `research.md`) — НЕ трогает другие лейны.
2. Не создаёт дублирующий запуск, если к моменту вызова `resolveAction`
   лейн уже сам восстановился (например, `tick()` пересчитал состояние
   между рендером алерта на фронте и кликом — тот же паттерн, что уже
   описан в комментарии `MonitoringService.resolve()`: «тихий no-op»).
3. После выполнения — тот же алерт не должен немедленно появиться снова на
   следующем `tick()`, если восстановление удалось (иначе оператор увидит
   «висящий» алерт после успешного клика).

## DTO наружу (без изменений формата)

Формат, отдаваемый на фронт, не меняется — переиспользуется существующий
`MonitorAlertDto` (`key`, `severityName`, `color`, `title`, `body`,
`category`, `detail`, `recommendations`, `canResolve`, `contentHash`, `read`).
Никаких новых полей DTO эта фича не добавляет — значит, `webvue3`
Monitor-компонент не требует изменений (см. Constitution Check в `plan.md`,
Principle V — N/A).

## Совместимость с существующими проверками

- `key` НЕ ДОЛЖЕН пересекаться с `key` других проверок реестра
  (`MonitorRegistry.checks`) — по конструкции `"queue.lane.stalled.<threadId>"`
  уникален и не пересекается с `"queue.stalled"` (`RenderQueueStalledCheck`)
  или `"stemjobs.stuck"` (`StemJobsStuckCheck`, обрабатывает другой домен —
  `tbl_stem_jobs`, не пересекается по данным).
