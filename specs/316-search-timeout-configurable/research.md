# Phase 0 Research: Настраиваемый таймаут между поисковыми запросами (iter #316)

**Branch**: `316-search-timeout-configurable`
**Date**: 2026-09-08 (rev 3: UI fix + авто-подбор)
**Spec**: [spec.md](./spec.md)

## Цель

Зафиксировать технические решения. Основа — реальные точки входа из `ApiController.kt`, `KaraokeProperties.kt`, `SseNotificationService.kt`, `webvue3/src/components/Common/CustomConfirm.vue` (rev 3 — интеграция в существующий custom-confirm вместо отдельной модалки).

## Решения

### R-001: Storage — `KaraokeProperties` (backend, single-user)
- **Decision**: глобальный backend-настройка через существующий `KaraokeProperties` (`karaoke-app/.../KaraokeProperties.kt:25`, файл `/sm-karaoke/system/Karaoke.properties`, base64-encoded). Новый ключ `lyricsSearchTimeoutSeconds` (Int, default 10).
- **Rationale**: (1) Владелец указал «к KaraokeProperties» — это backend-настройка, не web-storage. (2) `KaraokeProperties` — единый источник правды для всех backend-настроек, редактируется через Properties UI/API без перекомпиляции. (3) Single-user обосновано: Karaoke имеет одного admin-пользователя. (4) Не требует новых таблиц/миграций.
- **Alternatives considered**: `setWebvueProp`/`getWebvueProp` (web-storage) — **отклонён** (rev 2: не покрывает backend-путь createfromfolder, scope расширен владельцем). БД-таблица — отклонён (overengineering для single-value).

### R-002: Точка внедрения межзапросного интервала — searchsongtextall (серийный)
- **Decision**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt:4771-4809` (`getSearchSongTextAll`, цикл `ids.forEach { ... }`). Использовать `ids.forEachIndexed { index, id -> ... }` — паузу **после каждого вызова `getLyricsSearch(song)`, кроме последнего** (т.е. если `index < ids.size - 1 && effectiveTimeout > 0`). Хвостовой sleep для `ids.size == 1` НЕ нужен.
- **Rationale**: Искать тексты нужно для списка песен, сервер итерирует их последовательно. `Thread.sleep(timeout * 1000L)` — блокирует Tomcat-поток, приемлемо для single-admin. Прецедент `MainController.kt:1626` (`Thread.sleep(2000)` уже есть).
- **Alternatives considered**: Пауза на клиенте — отклонён (frontend не контролирует backend-итерацию; createfromfolder вообще без UI). Пауза внутри `LyricsFinderService.findLyrics` — отклонён (изменит API, нарушит других callers).

### R-002a: Точка внедрения rate-limit — createfromfolder (параллельный)
- **Decision**: `karaoke-app/.../controllers/ApiController.kt:5302` (`doCreateFromFolder` → `lyricsSearchExecutor.submit { getLyricsSearch(...) }`). Перед каждым `submit` (внутри `if (!textResolved)`, кроме последнего — условие `songIndex < createdList.size - 1 && searchTimeout > 0`) — `Thread.sleep(searchTimeout * 1000L)`. Пул `Executors.newFixedThreadPool(4)` (ApiController.kt:209) **НЕ меняем**.
- **Rationale**: Без rate-limit submit'ы идут в 4 потока одновременно → DDoS-защита срабатывает. С `Thread.sleep` перед submit — суммарный поток замедляется, защита работает. Pause в main-thread допустима (single-admin, не latency-critical).
- **Alternatives considered**: Throttle-пул (Semaphore/rate-limiter) — отклонён (сложнее). Sequential submit (1 поток) — отклонён (медленнее для больших папок).

### R-003: Префикс ключа — `lyricsSearchTimeoutSeconds` (KaraokeProperties)
- **Decision**: Ключ `lyricsSearchTimeoutSeconds`, хранится как `Int` (целое число секунд).
- **Rationale**: Соответствует конвенции `KaraokeProperties` (camelCase). Существующие примеры: `checkSearchAsync` (Boolean), `requestAsyncUrl` (String), `requestIamTokenTimeoutMs` (Long). Сериализация не нужна (Int).

### R-004: Контракт API — query-param + endpoint для UI
- **Decision**: (a) `getSearchSongTextAll` и `doCreateFromFolder` принимают `timeout` query-param (default = `KaraokeProperties.getInt("lyricsSearchTimeoutSeconds").takeIf { it >= 1 } ?: 10` — **1-арг** `getInt` в `KaraokeProperties.kt:92`, двух-аргументный не существует). (b) UI использует новый endpoint `GET/POST /api/lyrics-search-timeout` (R-008).
- **Rationale**: (a) Query-param сохраняет обратную совместимость (если UI не шлёт параметр — fallback на сохранённое значение). (b) Отдельный endpoint — единый путь read/write из UI.

### R-005 (rev 3): Frontend — поле таймаута в существующих `<custom-confirm>` (НЕ отдельный диалог)
- **Decision**: **НЕ создаём** отдельный `SearchTimeoutDialog.vue` (rev 1-2 отклонён владельцем). Вместо этого добавляем **поле** `{ fldName: 'timeout', fldLabel: 'Таймаут (сек)', fldValue: <default> }` в `customConfirmParams.fields` существующих модалок:
  - **SongsTable.vue** «Подтвердите поиск текста» (стр. 1289-1314) — рядом с полем `engine`.
  - **HomeView.vue** «Добавление файлов из папки» (стр. 255-268) — рядом с информацией о формате.
  - `<custom-confirm>` (`webvue3/src/components/Common/CustomConfirm.vue`) уже поддерживает массив полей с разными типами (boolean/select/textarea/text — input default). Новое text-поле = `timeout` (default type `<input>`).
- **Rationale**: Принцип «сделай так же, как рядом» (по решению владельца). Существующая модалка уже открывается в обеих точках входа — добавляем поле в неё. Никаких дополнительных модалок, тем более не в дизайне текущего.
- **Callback**: `(ret) => this.doSearchTextForAll(ret.engine, ret.timeout)` в SongsTable.vue; `(ret) => this.doAddFilesFromFolder(ret.timeout)` в HomeView.vue.

### R-006 (rev 3): UI компонент — `<custom-confirm>` (existing, без нового)
- **Decision**: **НЕ создаём** `SearchTimeoutDialog.vue`. Используем существующий `<custom-confirm>` (`webvue3/src/components/Common/CustomConfirm.vue`). Поле таймаута — input (default `<input>` после `v-else`).
- **Rationale**: См. R-005.

### R-007: Validation — целое число ≥ 1
- **Decision**: В UI (custom-confirm с callback) — `ret.timeout` парсится в число перед передачей в backend: `Number.isInteger(Number(value)) && Number(value) >= 1`. Невалидный → ошибка. В backend — `effectiveTimeout = timeout?.coerceAtLeast(1) ?: ...` (defensive). Если значение невалидно — fallback на `KaraokeProperties` default 10.
- **Rationale**: Соответствует FR-006. Двойная защита (UI + backend) — пользователь видит ошибку, backend не падает на невалидном input.

### R-008: API endpoint — read/write таймаута
- **Decision**: `GET/POST /api/lyrics-search-timeout` в `ApiController.kt`:
  ```kotlin
  @GetMapping("/lyrics-search-timeout")
  fun getLyricsSearchTimeout(): Map<String, Int> =
      mapOf("value" to KaraokeProperties.getInt("lyricsSearchTimeoutSeconds").takeIf { it >= 1 } ?: 10)

  @PostMapping("/lyrics-search-timeout")
  fun setLyricsSearchTimeout(@RequestParam("value") value: Int): Map<String, Any> {
      val validated = if (value >= 1) value else 10
      KaraokeProperties.set("lyricsSearchTimeoutSeconds", validated)
      return mapOf("status" to "ok", "value" to validated)
  }
  ```
  Существующий Properties API (`getProperties()` ApiController.kt:5874) валиден — R-008 это **узкий** отдельный endpoint поверх, обосновано.
- **Rationale**: UI диалог вызывает endpoint (НЕ прямой файл Karaoke.properties — это base64 + защита прав).

### R-009 (rev 3): Авто-подбор — замер `minIntervalMs`
- **Decision**: В обоих путях (A и B) backend ведёт локальные переменные:
  ```kotlin
  var lastSuccessTime: Long? = null
  var minIntervalMs: Long? = null
  var successfulCount = 0
  val cycleStart = System.currentTimeMillis()
  ```
  Перед каждым `getLyricsSearch` — `val requestStart = System.currentTimeMillis()`. После успешного вызова (return non-null + нет exception):
  ```kotlin
  if (lastSuccessTime != null) {
      val delta = requestStart - lastSuccessTime!!
      if (minIntervalMs == null || delta < minIntervalMs!!) minIntervalMs = delta
  }
  lastSuccessTime = requestStart
  successfulCount++
  ```
  Упавшие запросы (exception, null, 5xx) — НЕ учитываются (нет пары).
- **Rationale**: Это **диагностическая метрика** для пользователя («нащупать» нужный таймаут), а не автоматическое обновление `KaraokeProperties`. Решение об изменении таймаута остаётся за пользователем.
- **Alternatives considered**: Автоматическое обновление таймаута на основе `minIntervalMs` — отклонён (out of scope, риск агрессивной адаптации).

### R-010 (rev 3): Логирование + SSE-нотификация
- **Decision**: После завершения цикла массового поиска (Path A или Path B) backend MUST:
  - **(a)** `println("[lyrics-search-summary] path=$path count=$count successful=$successfulCount minIntervalMs=${minIntervalMs ?: "null"} totalDurationMs=$totalDurationMs")` — в KaraokeApp-лог.
  - **(b)** `sseNotificationService.send(SseNotification(SseNotificationType.MASS_SEARCH_SUMMARY, mapOf("path" to path, "count" to count, "successfulCount" to successfulCount, "minIntervalMs" to minIntervalMs, "totalDurationMs" to totalDurationMs)))`.
  - Тип `MASS_SEARCH_SUMMARY` — **enum** в `model/SseNotificationType.kt` (УЖЕ существует, broadcast по умолчанию — НЕ в `addressedTypes`).
- **Rationale**: Логирование — для runtime-verify владельца и grep по `docker logs karaoke-app`. SSE — для UI-нотификации без перезагрузки. Используем существующий `SseNotificationService` (не нужно создавать новый механизм).
- **Frontend wiring**: `webvue3/src/App.vue:281` `EventSourcePolyfill` уже подписан на broadcast. Добавляем `case SseNotificationType.MASS_SEARCH_SUMMARY` в switch (опционально — можно просто логировать).

## NEEDS CLARIFICATION — все resolved

0 [NEEDS CLARIFICATION] остаётся. Все 5 спорных моментов решены в spec Assumptions (rev 3 + FR-009/FR-010).

— Илья (boss)
