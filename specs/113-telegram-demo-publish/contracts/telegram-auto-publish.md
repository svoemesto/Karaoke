# Contracts: Автопубликация демо-версий песен в Telegram-канал по расписанию

> Phase 1 — контракты трёх точек взаимодействия:
> (1) `webvue3` → `karaoke-app` через REST endpoint (кнопка «Опубликовать сейчас»),
> (2) `TelegramAutoPublishScheduler` → `TelegramAutoPublishService` (внутри `karaoke-app`),
> (3) `TelegramAutoPublishService` → Telegram Bot API (`sendVideo`).
> Дополнительно — (4) конфигурация через `KaraokeProperties`.

---

## 1. REST: `POST /api/song/publishToTelegramNow` (FR-015/FR-016)

**Стороны**: `webvue3` (admin SPA) → `karaoke-app` (через
`ApiController.kt`).

**Назначение**: ручной запуск того же пути автопубликации,
который срабатывает по наступлению даты/времени. Используется
администратором для исправления «опоздавших» публикаций
(Q1 spec.md: «прошлая дата — пропускаем»), для немедленной
публикации готовых песен и для отладки.

### Request

```
POST /api/song/publishToTelegramNow
Content-Type: application/x-www-form-urlencoded

songId=12345
&adminKey=<значение adminKey из настроек, как у других admin-эндпоинтов>
```

| Параметр | Тип | Обязательный | Описание |
|---|---|---|---|
| `songId` | Long | да | ID песни (`tbl_songs.id`) |
| `adminKey` | String | да | Защита от CSRF, как у других admin-эндпоинтов (`MainController.kt`, `/api/private/**`) |

### Response

**HTTP 200 OK** (синхронный ответ после завершения публикации
или постановки в очередь рендера):

```json
{
  "success": true,
  "state": "publishing",
  "messageId": null,
  "error": null
}
```

или при ошибке:

```json
{
  "success": false,
  "state": "send_failed",
  "messageId": null,
  "error": "sendVideo failed after 3 attempts: 429 Too Many Requests"
}
```

или при нарушении FR-016 (кнопка нажата для уже опубликованной):

```
HTTP 400 Bad Request
{
  "success": false,
  "state": "published",
  "messageId": "12345",
  "error": "Song 12345 is already published (idTelegramDemo=67890); clear idTelegramDemo first to re-publish"
}
```

или при отсутствии прав (FR-011 — песня не готова):

```
HTTP 409 Conflict
{
  "success": false,
  "state": "scheduled",
  "messageId": null,
  "error": "Song 12345 is not content-ready (idStatus=5, missing: pictureAlbumReady)"
}
```

или при отсутствии настроек:

```
HTTP 503 Service Unavailable
{
  "success": false,
  "state": "scheduled",
  "messageId": null,
  "error": "telegramAutoPublishEnabled=false or telegramAutoPublishChannelId is empty"
}
```

### Поля ответа

| Поле | Тип | Всегда присутствует | Описание |
|---|---|---|---|
| `success` | Boolean | да | `true` если бот успешно начал публикацию (state перешёл в `"rendering"` или `"publishing"`); `false` если произошла ошибка (state при этом отражает текущее реальное состояние) |
| `state` | String (enum) | да | Одно из: `"scheduled"` / `"rendering"` / `"publishing"` / `"published"` / `"send_failed"` / `"cancelled"` (соответствует `TelegramAutoPublishState` в data-model.md) |
| `messageId` | String \| null | да | `null` до завершения `sendVideo`; заполнен когда `state == "published"` |
| `error` | String \| null | да | `null` если `success=true` или ошибка «штатная» (например, FR-016); заполнен текстом ошибки для нештатных случаев |

### Семантика (FR-015/FR-016)

1. Endpoint — **синхронный**: возвращает управление только
   после того, как `TelegramAutoPublishService` либо завершил
   `sendVideo` (с ретраями по FR-010), либо поставил задачу
   рендера в очередь и перевёл state в `"rendering"`. То есть
   `success=true` означает «бот начал работу», а не
   «пост уже в канале».
2. После возврата `success=true` клиент **обязан** полагаться
   на SSE-обновление `Settings` (через уже существующий канал
   `SseNotification.recordChange`) для отслеживания финального
   `state="published"`. Никакого polling в `webvue3` не
   добавляется.
3. Кнопка «Опубликовать сейчас» в `SongEdit.vue` скрыта (или
   `disabled`) если `state == "published"` (FR-016) — клиентская
   защита; сервер всё равно проверит через `idTelegramDemo`.
4. Повторный клик по кнопке (когда state уже `"rendering"` или
   `"publishing"`) — идемпотентен: бот проверяет `idTelegramDemo == ''`
   и текущий state; если уже идёт публикация — возвращает
   `success=true, state="publishing"` без постановки второй
   задачи.

---

## 2. Internal: `TelegramAutoPublishScheduler` → `TelegramAutoPublishService`

**Стороны**: оба в `karaoke-app` (JVM-вызов, не сетевой).

**Назначение**: плановый тик каждые `telegramAutoPublishWindowMinutes`
минут. Находит кандидатов и делегирует каждому
`TelegramAutoPublishService.publishToTelegram(song)`.

### Сигнатура `TelegramAutoPublishService.publishToTelegram`

```kotlin
// karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/
//   TelegramAutoPublishService.kt

class TelegramAutoPublishService {

    /**
     * Выполняет полный цикл автопубликации одной песни:
     * 1. Проверка FR-008 (idTelegramDemo == '') и FR-001
     *    (dateTimePublish >= now()); если не прошло — return
     *    с текущим state без действий.
     * 2. Проверка FR-011 (песня публично готова); если нет —
     *    return с state="scheduled" и error="not content-ready".
     * 3. Алгоритм FR-003:
     *    - Если демо-MP4 существует и <= maxFileSize → используем
     *    - Иначе → ставим KaraokeProcess RENDER_MP4_DEMO,
     *      state="rendering", return (публикация продолжится
     *      через KaraokeProcessWorker при завершении рендера).
     * 4. После рендера (или если файл уже был) → state="publishing",
     *    вызываем TelegramApiClient.sendVideo (с retry FR-010).
     * 5. По успеху → state="published", saveToDb() пишет
     *    idTelegramDemo (FR-006).
     * 6. По исчерпанию ретраев → state="send_failed",
     *    lastError заполнен.
     */
    fun publishToTelegram(song: Song): TelegramAutoPublishResult

    /**
     * Callback из KaraokeProcessWorker при завершении задачи
     * RENDER_MP4_DEMO. Если рендер успешен — продолжает
     * публикацию (state="publishing" → sendVideo). Если рендер
     * упал — state="send_failed", lastError заполнен текстом
     * ошибки рендера.
     */
    fun onRenderCompleted(songId: Long, success: Boolean, error: String?)
}

data class TelegramAutoPublishResult(
    val state: TelegramAutoPublishState,
    val messageId: String? = null,
    val error: String? = null
)
```

### Контракт scheduler'а

```kotlin
// karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/
//   TelegramAutoPublishScheduler.kt

@Scheduled(fixedDelayString = "\${telegramAutoPublishWindowMinutes:5}PT{telegramAutoPublishWindowMinutes:5}M")
fun tick() {
    if (!KaraokeProperties.getBoolean("telegramAutoPublishEnabled")) return

    // Фаза 1: дешёвый SELECT кандидатов (без loadListFromDb)
    val candidates = loadCandidates()  // List<SongRowLite>

    for (row in candidates) {
        // Фильтр на стороне Kotlin (dateTimePublish в окне)
        if (!isInCurrentWindow(row)) continue

        // Загружаем полный Song только для тех, кто прошёл фильтр
        val song = Song.loadFromDb(row.id) ?: continue
        telegramAutoPublishService.publishToTelegram(song)
    }
}
```

**Инвариант**: `tick()` идемпотентен — повторный вызов в течение
одного окна не дублирует публикации, потому что `publishToTelegram`
проверяет `idTelegramDemo == ''` (FR-008) и текущий
`telegramAutoPublishState` (если уже `"rendering"` или `"publishing"`
— повторный вызов не ставит вторую задачу).

---

## 3. External: `TelegramAutoPublishService` → Telegram Bot API

**Стороны**: `karaoke-app` → `https://api.telegram.org/bot<token>`.

**Метод**: `sendVideo`.

### Request

```http
POST /bot<telegramBotToken>/sendVideo
Content-Type: multipart/form-data; boundary=...

--boundary
Content-Disposition: form-data; name="chat_id"

-1001234567890
--boundary
Content-Disposition: form-data; name="video"; filename="demo.mp4"
Content-Type: video/mp4

<binary data of demo.mp4>
--boundary
Content-Disposition: form-data; name="caption"

🎤 Земфира — Почему (демо)
https://sm-karaoke.ru/song?id=12345
#караоке #svoemesto
--boundary
Content-Disposition: form-data; name="parse_mode"

HTML
--boundary
Content-Disposition: form-data; name="disable_notification"

false
--boundary--
```

| Поле | Обязательное | Описание |
|---|---|---|
| `chat_id` | да | Значение `telegramAutoPublishChannelId` |
| `video` | да | MP4-файл (multipart). Альтернатива: `video` = `file_id` ранее загруженного файла (используется при локальном Bot API сервере) |
| `caption` | да | Подпись (≤1024 символа). FR-005 |
| `parse_mode` | нет | `"HTML"` или `"MarkdownV2"` — на усмотрение планирования |
| `disable_notification` | нет | `false` (по умолчанию) — уведомления в канале включены |

### Response (success)

```json
{
  "ok": true,
  "result": {
    "message_id": 67890,
    "date": 1753990800,
    "chat": { "id": -1001234567890, "title": "Svoemesto Караоке", "type": "channel" },
    "video": { "duration": 47, "width": 1280, "height": 720, "file_name": "demo.mp4", "mime_type": "video/mp4", "file_id": "BAAD..." }
  }
}
```

Используемые поля:
- `ok == true` → success
- `result.message_id` → записывается в `Settings.idTelegramDemo` (FR-006)

### Response (error / rate limit)

```json
{
  "ok": false,
  "error_code": 429,
  "description": "Too Many Requests: retry after 30",
  "parameters": { "retry_after": 30 }
}
```

Используемые поля:
- `ok == false` → failure
- `error_code == 429` → retryable (FR-010: respect `retry_after` или использовать 30с из backoff-schedule)
- `error_code == 400 / 403 / 404` → non-retryable (сразу `"send_failed"`)
- `error_code == 5xx` → retryable

### Retry-цикл (FR-010)

```
maxAttempts = 3
backoffSchedule = [30_000, 120_000, 300_000]  // мс

for attempt in 1..maxAttempts:
    response = trySendVideo(...)
    if response.ok:
        return Success(messageId = response.result.message_id)
    if response.error_code in [400, 403, 404]:
        return Failed(reason = "non-retryable: ${response.description}")
    if attempt < maxAttempts:
        delay(backoffSchedule[attempt - 1])  // 30с, 2м, 5м
return Failed(reason = "retries exhausted: ${lastResponse.description}")
```

Каждая попытка использует прокси-fallback из существующего
`TelegramApiClient` (тот же паттерн, что в Фазе 1 для
`getUpdates`): если `useProxy` ещё `false`, но запрос падает —
переключаемся на прокси и пробуем снова.

---

## 4. Конфигурация: `KaraokeProperties` (Karaoke.properties)

**Стороны**: админ → `KaraokeProperties.kt` (через Properties UI).

**Формат** — тот же base64-properties файл, что и для всех
остальных настроек (`/sm-karaoke/system/Karaoke.properties`).
Новые ключи (см. data-model.md):

```
telegramAutoPublishEnabled=false
telegramAutoPublishChannelId=
telegramAutoPublishWindowMinutes=5
telegramAutoPublishMaxFileSizeMb=50
```

Поведение при старте `karaoke-app`:
- `telegramAutoPublishEnabled=false` (по умолчанию) — scheduler
  не стартует, endpoint `/api/song/publishToTelegramNow` тоже
  отвечает `503 Service Unavailable` (контракт п.1).
- `telegramAutoPublishChannelId=""` — `sendVideo` не вызывается,
  scheduler логирует warning и пропускает тик.
- `telegramAutoPublishWindowMinutes=5` — тик каждые 5 минут
  (`fixedDelay`).
- `telegramAutoPublishMaxFileSizeMb=50` — стандартный лимит
  Telegram Bot API.

Все 4 ключа — `KaraokeProperty` (read-only после загрузки
файла; для изменения нужно пересохранить файл через Properties
UI и перезапустить `karaoke-app` — тот же паттерн, что для
`telegramPollingEnabled`).

---

## 5. Сводка контрактов

| #   | Сторона-источник | Сторона-приёмник | Формат | Описание |
|-----|------------------|------------------|--------|----------|
| 1   | `webvue3`        | `karaoke-app`    | HTTP `POST /api/song/publishToTelegramNow` | Ручной запуск публикации (FR-015/FR-016) |
| 2   | `TelegramAutoPublishScheduler` | `TelegramAutoPublishService` | JVM-вызов `publishToTelegram(Song)` | Плановый тик (FR-001) |
| 3a  | `TelegramAutoPublishService`   | Telegram Bot API | HTTPS `POST /bot{token}/sendVideo` (multipart) | Отправка демо-MP4 в канал |
| 3b  | `TelegramAutoPublishService`   | `KaraokeProcess*` | JVM-вызов `KaraokeProcess` с типом `RENDER_MP4_DEMO` | Постановка задачи рендера (FR-003 сц. 2/3) |
| 3c  | `KaraokeProcessWorker`         | `TelegramAutoPublishService` | Callback `onRenderCompleted(songId, success, error)` | Завершение рендера → продолжение публикации |
| 4   | админ            | `KaraokeProperties` | base64-properties файл | Конфигурация (FR-013) |
