# Research: 414 Request-URI Too Large — выбор решения

> **Date**: 2026-09-10
> **Spec**: [spec.md](./spec.md)

## Проблема

Endpoint `GET /api/public/account/playlists/membership?ids=<csv>` на
крупных авторах (≈2500+ песен) превышает **8 КБ** (HTTP request-line limit
nginx `large_client_header_buffers 4 8k`). Результат: nginx возвращает
браузеру `414 Request-URI Too Large`, и membership-карта не доходит до
фронта → иконки избранного/плейлистов остаются в нейтральном
состоянии. Прецедент: OpenProject #77 (владелец открыл «Машину Времени»
с ≈2500 песнями).

## Решение (Decision 1)

**Добавить новый `POST /api/public/account/playlists/membership` с
JSON-телом `{"ids": number[]}`.** Старый GET сохраняем для backward-compat.

### Обоснование (Rationale)

1. **REST-конвенция**: GET URL ≤ 8 КБ (передача параметров через query-string).
   POST без лимита (передача через body, лимит — `client_max_body_size`,
   по умолчанию 1m). Для больших payloads POST — канонический паттерн
   (YooKassa, VK API, Telegram Bot API — все используют POST для
   `ids[]`/batch-операций).
2. **Сохранение семантики GET**: GET остаётся идемпотентным read-only,
   существующие клиенты (старые билды в кеше браузера) не ломаются.
3. **Нулевые изменения в SQL**: переиспользуется существующий
   `SitePlaylistItem.songIdsInPlaylists(...)` (один SQL с `WHERE song_id IN (...)`).
   См. `karaoke-web/.../PublicPlaylistController.kt:413`.
4. **Нулевые изменения в nginx**: `client_max_body_size = 1m` (по
   умолчанию) с запасом покрывает 2500 id (~20 КБ JSON).
5. **JWT-аутентификация в `Authorization: Bearer`** (не cookie) — CSRF
   невозможен by design (см. `SiteAuthInterceptor.kt:25` +
   Clarifications § Session 2026-09-10 в spec.md). Не нужен
   CSRF-middleware.

### Альтернативы рассмотрены и отклонены

| Альтернатива | Почему отклонена |
|---|---|
| **Увеличить nginx `large_client_header_buffers` до 64 КБ** | Не помогает для HTTP/1.1 RFC: многие прокси/CDN имеют свой лимит (Cloudflare = 8 КБ, Akamai = 8 КБ default). Сайт может оказаться за другим прокси в будущем. Решает симптом, не причину. |
| **Chunked GET (несколько запросов по 500 id)** | Уже пробовали в спеке #239 до bulk-fetch — фрагментировали запросы и сайт «лежал» от N×3 фоновых вызовов. Bulk-fetch через POST делает это **одним** запросом без фрагментации. |
| **GraphQL** | Никогда не использовался в проекте. Архитектурный overkill для одной операции. |
| **`@RequestParam` form-encoded (как у существующих POST)** | Возможный компромисс (минимальные изменения в authApi.js), но: (a) менее идиоматично для больших payloads, (b) Spring `@RequestParam` имеет лимит на количество параметров, (c) URL-encoded формат хуже для тестирования/документирования. JSON — стандарт для HTTP API. |
| **`multipart/form-data` с повторяющимся `ids`** | Самый гибкий, но: (a) сложнее для бэка (MultipartFile парсинг), (b) фронт уже использует `authUpload` для файлов — naming collision. Overkill. |
| **POST с `ids=N,N,N` в одном `@RequestParam`** | Работает, но: (a) требует парсинга CSV на бэке (как сейчас делает GET), (b) менее строгий контракт (нет типизации), (c) URL-encoding `%2C` ломает читаемость. JSON лучше. |

### Как именно будет выглядеть POST-вызов

**Фронт** (`karaoke-public/src/services/playlistApi.js`):

```javascript
// Было:
export function fetchMembership(ids) {
  return authGet(`${BASE}/playlists/membership${qs({ ids: ids.join(',') })}`, token())
}

// Стало:
export function fetchMembership(ids) {
  return authPostJson(`${BASE}/playlists/membership`, { ids }, token())
}
```

**Бэк** (`PublicPlaylistController.kt`):

```kotlin
@PostMapping("/playlists/membership")
fun membershipPost(
    @RequestBody request: MembershipRequest,
    httpRequest: HttpServletRequest,
): ResponseEntity<Any> {
    val user = currentUser(httpRequest)
    val songIds = request.ids.distinct().filter { it > 0 }
    return ResponseEntity.ok(buildMembershipResponse(user, songIds))
}
```

**DTO** (`karaoke-web/.../dtos/MembershipRequest.kt`):

```kotlin
data class MembershipRequest(
    @JsonProperty("ids")
    val ids: List<Long>,
)
```

**Auth-API** (`karaoke-public/src/services/authApi.js`): добавить
`authPostJson(path, jsonBody, token)` — новый метод, аналог `authPost`,
но с `Content-Type: application/json` и `xhr.send(JSON.stringify(jsonBody))`.
Существующий `authPost` (form-encoded) не трогаем — он используется
в 10+ местах.

### Nginx лимиты (assumptions для plan)

| Параметр | Default | Значение | Влияние |
|---|---|---|---|
| `large_client_header_buffers` | `4 8k` | 8 КБ на request-line | GET ломается на > 8 КБ URL (текущий баг) |
| `client_max_body_size` | `1m` | 1 МБ на body | POST с JSON ~20 КБ — ОК |
| `client_body_buffer_size` | `8k` | 8 КБ буфер | Если body > 8 КБ → пишется во временный файл (ОК) |

**Проверить на проде при deploy**: реальные значения могут быть
переопределены. Если `client_max_body_size` < 32 КБ — добавить правку
`deploy/nginx.conf` в этот PR.

### Backward-compat стратегия

Старый GET остаётся работать для маленьких `ids` (≤ 500). Для
больших — клиент получит 414 (текущее поведение, не наша забота).
Постепенно:
1. **Шаг 1 (этот PR)**: ввести POST, фронт переключить на POST.
   Старый GET остаётся (для диагностики + legacy клиентов).
2. **Шаг 2 (следующий PR, через 1-2 месяца, опционально)**: пометить
   GET как `@Deprecated`, добавить WARN-лог при вызове.
3. **Шаг 3 (по необходимости)**: удалить GET, если метрика
   `membership_get_calls_total` за месяц < 1%.

### Метрики для observability (FR-017, FR-018 в spec.md)

```kotlin
private val membershipGetCalls = AtomicLong(0)
private val membershipPostCalls = AtomicLong(0)

@GetMapping("/playlists/membership")
fun membership(...): ResponseEntity<Any> {
    membershipGetCalls.incrementAndGet()
    log.debug("infra.prod.api.membership.get user={} ids={}", user.id, songIds.size)
    // ... existing logic
}

@PostMapping("/playlists/membership")
fun membershipPost(...): ResponseEntity<Any> {
    membershipPostCalls.incrementAndGet()
    log.debug("infra.prod.api.membership.post user={} ids={}", user.id, songIds.size)
    // ... same logic via buildMembershipResponse()
}
```

Метрики — через существующий паттерн
`infra.prod.*` (см. ADR `local-0006-logging-and-error-handling-karaoke-web.md`).
Сейчас в проекте нет Prometheus — используем WARN/INFO логи +
парсинг `grep 'infra.prod.api.membership'` в Loki (или вручную).

## Открытые вопросы (NONE для implement)

Нет открытых вопросов, которые блокируют implement. Все архитектурные
решения приняты:

- ✅ POST vs GET → **POST** (Decision 1)
- ✅ CSRF → **не нужен** (JWT в Authorization header)
- ✅ nginx limits → **не трогаем** (дефолты покрывают JSON body 20 КБ)
- ✅ backward-compat → **сохраняем GET** (US3 в spec.md)
- ✅ observability → **log-based** через `infra.prod.api.membership.*`
- ✅ SQL → **без изменений** (переиспользуем существующий запрос)

Спека готова к Stage 4 (`/speckit.tasks`).