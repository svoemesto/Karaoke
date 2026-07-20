# SSE-уведомления для live-UI

> **Status**: active
> **Feature Key**: sse-notifications
> **Last Updated**: 2026-07-20

## Что делает

Один SSE-эндпоинт `GET /api/subscribe` рассылает в реальном времени
события об изменениях записей (`recordChange`/`recordDelete`), логах
(`log`), состоянии процесса (`processWorkerState`), аляртах мониторинга
(`MONITOR_ALERTS`) и адресных сообщениях конкретной вкладке
(`MESSAGE`/`ERROR`). Клиент — `EventSourcePolyfill` (НЕ SockJS/STOMP).

## Зачем

Админка `webvue3` показывает таблицы песен, авторов, процессов, картинок.
Когда в фоне работает Demucs или редактируется песня в другом окне —
нужно мгновенно обновить UI без polling. SSE — нативный браузерный
протокол, не требует WebSocket-инфраструктуры, проходит через HTTP-прокси.

## Как работает (кратко)

1. **Подписка** — `EventSourcePolyfill` (или нативный `EventSource`)
   подключается к `/api/subscribe?tabId=<id>`. `tabId` генерирует фронт
   (`getTabId()` в `webvue3/src/lib/utils.js`, `sessionStorage`), передаётся
   в query-параметре.
2. **Ключ подписки** — `UserKey(userId, tabId)`. У каждой вкладки/компьютера
   своё соединение, не одно на всё приложение.
3. **Heartbeat** — независимый `@Scheduled(fixedRate = 15_000)`
   (`@EnableScheduling` в `KaraokeAppApplication`) шлёт SSE-comment `:ping`
   каждые 15 секунд. **Не** завязан на `KaraokeProcessWorker` (раньше был
   единственный «пинг» `DUMMY` из воркера — без запущенной очереди
   соединение рвалось по таймауту простоя).
4. **Broadcast vs адресная доставка**:
   - `recordChange`/`log`/`processWorkerState` — broadcast всем вкладкам.
   - `MESSAGE`/`ERROR` — только вкладке, инициировавшей запрос (через
     `TabIdContext` из HTTP-заголовка `X-Tab-Id`).
5. **SSE-controller** — `SseEmitter` (Spring), timeout 0 (без таймаута,
   управляется heartbeat).

## Инварианты / правила

- **MUST**: heartbeat `@Scheduled(fixedRate = 15_000)` ВСЕГДА активен,
  независимо от наличия задач в `KaraokeProcessWorker`.
- **MUST**: каждое SSE-сообщение отправляется через `SseNotificationService.send()`,
  не напрямую через `emitter.send()`. Иначе теряется фильтрация по `tabId`.
- **MUST**: `TabIdContext` очищается после HTTP-запроса
  (через `TabIdFilter`/`OncePerRequestFilter`).
- **SHOULD**: не использовать SockJS/STOMP — эти либы лежат в репо, но
  нигде не подключены (SSE достаточно).

## Известные ловушки

- **Heartbeat зависит от воркера**: до 2026-07-09 heartbeat был через
  `DUMMY`-сообщения из `KaraokeProcessWorker`. Без запущенной очереди
  соединение рвалось прокси/клиентом по таймауту простоя. **Больше так
  не делать** — heartbeat отдельный `@Scheduled`.
- **TabId теряется**: `tabId` живёт в `sessionStorage` — при очистке
  storage подписка перестаёт получать адресные сообщения. На UI это
  выглядит как «всё сломалось, но без ошибок».
- **Длинные payload'ы**: SSE не любит payload > 64KB. `recordChange`
  с большим `SettingsDTO` может рвать соединение. Дробите на части
  (например, `songPicture`-изменение — отдельным событием).
- **CORS preflight**: SSE через `EventSource` не делает preflight, но
  через `EventSourcePolyfill` с `withCredentials: true` — да. Настройте
  `CorsConfigurationSource` на проде.

## Ссылки на ключевые классы/файлы

- [`SseNotificationService.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/SseNotificationService.kt) — главный сервис
- [`SseController.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/SseController.kt) — `/api/subscribe`
- [`TabIdFilter.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/security/TabIdFilter.kt) — извлечение tabId из запроса
- [`TabIdContext.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/security/TabIdContext.kt) — ThreadLocal для tabId
- [`KaraokeAppApplication.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeAppApplication.kt) — `@EnableScheduling`
- [`webvue3/src/lib/utils.js`](../../webvue3/src/lib/utils.js) — `getTabId()` (sessionStorage)
