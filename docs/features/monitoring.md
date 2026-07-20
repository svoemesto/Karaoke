# Мониторинг ключевых моментов проекта

> **Status**: active
> **Feature Key**: monitoring
> **Last Updated**: 2026-07-20

## Что делает

Обобщение подсистемы `HealthReport`, но НЕ привязанное к конкретной
песне `Settings`. Системные проверки состояния проекта в целом.
Снимок показывается в хедере webvue3 как «светофор» (красный/жёлтый/
зелёный). Шесть проверок из коробки + легко добавить новую.

## Зачем

Без светофора разработчик узнаёт о проблеме (остановилась очередь
рендера, непрочитанные сообщения в чате, и т.п.) только когда что-то
сломалось у пользователя. Мониторинг даёт упреждающий сигнал.

## Как работает (кратко)

1. **`MonitorRegistry.checks`** — список `object : MonitorCheck`:
   - Горизонт запланированных постов в Telegram (< N дней).
   - Доступность прод-сервера (HTTP HEAD на `https://sm-karaoke.ru`).
   - Остановленная очередь рендера (нет процессов в `RUNNING` > 30 мин).
   - Выключенный Telegram-поллинг (`telegramPollingEnabled = false`).
   - Непрочитанные сообщения в чате от пользователей.
   - Задания онлайн-редактора «на проверке» (`SubmittedAssignmentsCheck`,
     смотрит remote-БД).
   - Добавление новой проверки — одна строка в реестре.
2. **`MonitoringService`** — `@Scheduled` раз в минуту, прогоняет
   все проверки, хранит снапшот в памяти, рассылает по SSE
   (`MONITOR_ALERTS`, broadcast всем вкладкам).
3. **Состояние «прочитано»** — по хэшу содержимого
   (severity+заголовок+текст, БЕЗ изменчивых чисел вроде «N минут» —
   те в отдельном поле `detail`, иначе алерт «мигал» бы read/unread на
   каждом тике).
4. **Персистентность** — `KaraokeProperty monitorDismissed`
   (для текущего пользователя/UI).
5. **«Решить проблему»** — ре-деривация resolve-действия по ключу из
   свежего снапшота (лямбда не сериализуется в DTO, тот же паттерн,
   что у `HealthReport`).
6. **REST-контракт**:
   - `GET /api/monitor/alerts` — снапшот.
   - `POST /api/monitor/resolve` — выполнить resolve-действие.
   - `POST /api/monitor/markRead` / `markUnread`.
   - `POST /api/monitor/reset` — сбросить dismissed.

## Инварианты / правила

- **MUST**: новая проверка — `object : MonitorCheck` в `MonitorRegistry.checks`.
  Прямой вызов логики проверки из контроллера — ЗАПРЕЩЁН.
- **MUST**: хеш «прочитано» НЕ включает изменчивые поля (время, count).
  Иначе алерт мигает.
- **MUST**: `MonitoringService.run()` идемпотентен — повторный вызов
  не создаёт дублей алертов.
- **SHOULD**: проверка выполняется за <5 секунд (иначе `@Scheduled`
  накладывается на следующий тик).

## Известные ловушки

- **`HealthReport` vs `Monitor`**: путаница. `HealthReport` — для
  конкретной песни (`Settings`), `Monitor` — для проекта в целом. Не
  смешивать.
- **SubmittedAssignmentsCheck смотрит remote**: нужен доступ к
  remote-БД из `karaoke-app`. Если VPN/сеть падают — проверка
  возвращает «unknown», не «fail».
- **SSE flood**: при 10+ одновременных проверках, каждая из которых
  публикует алерт, UI может захлебнуться. Группируйте алерты в
  `MonitorAlert.bucket` (1-2-3 severity).

## Ссылки на ключевые классы/файлы

- [`MonitorRegistry.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/monitor/MonitorRegistry.kt) — реестр проверок
- [`MonitorCheck.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/monitor/MonitorCheck.kt) — интерфейс проверки
- [`MonitoringService.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/monitor/MonitoringService.kt) — `@Scheduled` runner
- [`MonitorController.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/MonitorController.kt) — REST
- [`MonitorAlert.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/monitor/MonitorAlert.kt) — модель алерта
- [`webvue3/src/components/Header/MonitorBadge.vue`](../../webvue3/src/components/Header/MonitorBadge.vue) — UI светофор
