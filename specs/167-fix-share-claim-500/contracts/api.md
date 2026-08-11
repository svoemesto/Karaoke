# HTTP API Contracts: Починить 500 на `POST /api/public/share/claim`

**Дата**: 2026-08-11
**Spec**: [./spec.md](./spec.md) | **Plan**: [./plan.md](./plan.md) | **Data Model**: [./data-model.md](./data-model.md)

## Что меняется

Hotfix затрагивает **только маппинг ошибок** в 3 эндпоинтах:

| Эндпоинт | Изменение | Где |
|---|---|---|
| `POST /api/public/share/claim` | catch-all → `InternalError` → 500 `share.internal` | `PublicShareController.kt:174-175` |
| `POST /api/public/share/{songId}/create` | catch-all → `InternalError` → 500 `share.internal` | `PublicShareController.kt:87-89` |
| `POST /api/public/share/heartbeat` | catch-all → `InternalError` → 500 `share.internal` | `PublicShareController.kt:189-191` |

**Никаких изменений** в:
- Path / Method / Headers / Request body shape.
- Success-ответах (200, 201, 204) — тела ответов не меняются.
- Документированных доменных ошибках (`share.notFound` 404, `share.concurrentLimit` 409, `share.rateLimited` 429, `share.tokenMissing` 400, `share.leaseExpired` 410, `share.notOwner` 403, `share.linkAlreadyActive` 429, `share.songUnavailable` 409) — **поведение и коды сохраняются**.
- Эндпоинтах `/release`, `/debug`, `/mine/{songId}`, `/mine/{songId}/revoke` — там нет catch-all.

## Контракт ошибок

### До фикса (текущее, неправильное поведение)

```http
POST /api/public/share/claim HTTP/1.1
Content-Type: application/json
Cookie: (none — анонимный гость)

{"secret": "abc123...", "browserHash": "deadbeef..."}

# → HTTP/1.1 500 Internal Server Error
# {"errorCode":"share.notFound"}
```

Причина: БД-таблиц нет на проде → `PSQLException` → catch-all → 500 `share.notFound` (вводит в заблуждение).

### После фикса (ожидаемое правильное поведение)

**Случай 1: БД-таблиц нет** (миграция не применена или применена частично)

```http
# → HTTP/1.1 500 Internal Server Error
# {"errorCode":"share.internal"}
```

В логах `karaoke-web`: `ERROR ... ShareLink tryClaim UNEXPECTED class=org.postgresql.util.PSQLException msg=ERROR: relation "tbl_song_share_links" does not exist` + полный стек-трейс.

**Случай 2: Ссылка валидная, миграция применена, claim работает**

```http
# → HTTP/1.1 200 OK
# {"linkId":42,"songId":12345,"sessionTokenHash":"<64 hex>","expiresAt":1723372800000,
#  "redirectTo":"/player/12345?share=1&session=<64 hex>",
#  "songName":"...","author":"...","album":"...","year":2024,
#  "albumImageUrl":"/api/public/picture?file=...","artistImageUrl":"/api/public/picture?file=..."}
```

**Случай 3: Ссылка отозвана / истёк TTL / неверный секрет** (ожидаемые доменные ошибки — без изменений)

```http
# → HTTP/1.1 404 Not Found
# {"errorCode":"share.notFound"}
```

**Случай 4: 2+ активных lease с разных устройств** (без изменений)

```http
# → HTTP/1.1 409 Conflict
# {"errorCode":"share.concurrentLimit"}
```

**Случай 5: пустой secret/browserHash** (без изменений)

```http
# → HTTP/1.1 400 Bad Request
# {"errorCode":"share.tokenMissing"}
```

**Случай 6: rate-limit превышен** (без изменений)

```http
# → HTTP/1.1 429 Too Many Requests
# {"errorCode":"share.rateLimited"}
```

## Эндпоинт: `POST /api/public/share/claim`

### Request

```http
POST /api/public/share/claim HTTP/1.1
Host: sm-karaoke.ru
Content-Type: application/json
Cookie: (none — анонимный гость)

{
  "secret": "abc123XYZ-_base64url_32bytes",
  "browserHash": "deadbeef1234567890abcdef1234567890abcdef1234567890abcdef12345678"
}
```

| Поле | Тип | Required | Описание |
|---|---|---|---|
| `secret` | string | ✅ | 32 байта SecureRandom в base64url (~43 символа). Хранится в БД как SHA-256 hex (64 символа). |
| `browserHash` | string | ✅ | SHA-256 hex от `browserId` (localStorage UUID). 64 hex-символа. |

### Response: 200 OK (US1#1, после фикса)

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "linkId": 42,
  "songId": 12345,
  "sessionTokenHash": "<64 hex>",
  "expiresAt": 1723372800000,
  "redirectTo": "/player/12345?share=1&session=<64 hex>",
  "songName": "Название песни",
  "author": "Автор",
  "album": "Альбом",
  "year": 2024,
  "albumImageUrl": "/api/public/picture?file=...",
  "artistImageUrl": "/api/public/picture?file=..."
}
```

| Поле | Тип | Описание |
|---|---|---|
| `linkId` | integer | PK в `tbl_song_share_links` |
| `songId` | long | FK из `tbl_settings.id` |
| `sessionTokenHash` | string | SHA-256 hex от `sessionSecret` (32 байта SecureRandom). Клиент хранит в `sessionStorage['kp_share_session_<songId>']` (см. спек 164 FR-004). |
| `expiresAt` | long (epoch ms) | Реальный момент времени конца lease. Фронт использует для отображения «Доступно до ДД.ММ.ГГГГ ЧЧ:ММ» в TZ устройства (FR-011 спеки 166). |
| `redirectTo` | string | URL для редиректа ShareView.vue после успешного claim. |
| `songName`, `author`, `album`, `year` | string, string, string, integer | Карточка песни для лендинга (см. `loadSongInfo`, `SongShareLinkService.kt:954-990`). |
| `albumImageUrl`, `artistImageUrl` | string \| null | URL превью альбома/автора для лендинга. `null` если картинка не готова (`pictureAlbumReady`/`pictureAuthorReady` = false в `player_readiness_flags`). |

### Error responses (полный список после фикса)

| HTTP | `errorCode` | Когда | Изменение |
|---|---|---|---|
| 200 | (нет — успех) | claim успешен, выдан `sessionTokenHash` | без изменений |
| 400 | `share.tokenMissing` | пустой/отсутствующий `secret` или `browserHash` | без изменений |
| 404 | `share.notFound` | ссылка не найдена, отозвана, истёк TTL, неверный секрет | **без изменений** (только `NotFound` исключение; не маскируется) |
| 409 | `share.concurrentLimit` | 2+ активных lease с разных `browserHash` | без изменений |
| 409 | `share.songUnavailable` | песня SKIP или не опубликована | без изменений |
| 429 | `share.rateLimited` | >10 claim/мин с одного IP | без изменений |
| 500 | `share.internal` ← **НОВОЕ** | неожиданная системная ошибка: SQLException (нет таблицы, нет коннекта), NPE в `loadSongInfo`, конфликт recordhash-триггера | **НОВОЕ** — раньше маскировалось под `share.notFound` 500 |

### Side effects на БД (после фикса, успешный claim)

```sql
-- 1. INSERT в tbl_song_share_sessions
INSERT INTO tbl_song_share_sessions
  (share_link_id, song_id, browser_hash, owner_site_user_id, anon_id,
   client_ip_hash, user_agent_hash)
VALUES (?, ?, ?, ?, '', ?, ?)
RETURNING id;

-- 2. UPDATE tbl_song_share_links (active_session_* + счётчики)
UPDATE tbl_song_share_links SET
  active_session_token_hash = ?,
  active_session_browser_hash = ?,
  active_session_lease_until = now() + (? || ' milliseconds')::interval,
  first_used_at = COALESCE(first_used_at, now()),
  last_used_at = now(),
  sessions_total = sessions_total + 1
WHERE id = ?;
```

## Эндпоинт: `POST /api/public/share/{songId}/create`

### Request

```http
POST /api/public/share/12345/create?ttlSeconds=3600 HTTP/1.1
Host: sm-karaoke.ru
Cookie: (site_user session — требуется премиум)

# query params:
#   ttlSeconds: 3600 | 86400 | 604800 (default 3600)
```

### Response: 200 OK (без изменений)

```json
{
  "linkId": 42,
  "secret": "abc123XYZ-_base64url_32bytes",
  "url": "https://svoemesto.ru/share/12345/abc123XYZ-_base64url_32bytes",
  "expiresAt": 1723372800000,
  "ttlSeconds": 3600
}
```

### Error responses (полный список после фикса)

| HTTP | `errorCode` | Когда | Изменение |
|---|---|---|---|
| 200 | (успех) | ссылка создана/перевыпущена | без изменений |
| 400 | `share.tokenMissing` | невалидный `ttlSeconds` (не 3600/86400/604800) | без изменений |
| 401 | `share.tokenMissing` | неавторизован (нет site_user cookie) | без изменений |
| 403 | `share.notOwner` | `isEffectivePremium = false` | без изменений |
| 429 | `share.linkAlreadyActive` | превышен лимит активных/перевыпусков/генераций | без изменений |
| 500 | `share.internal` ← **НОВОЕ** | SQLException / NPE / иное | **НОВОЕ** — раньше маскировалось под `share.notFound` 500 |

## Эндпоинт: `POST /api/public/share/heartbeat`

### Request

```http
POST /api/public/share/heartbeat HTTP/1.1
Host: sm-karaoke.ru
Content-Type: application/json
Cookie: (none — сессия по sessionTokenHash)

{"sessionTokenHash": "<64 hex>"}
```

### Response: 200 OK (без изменений)

```json
{"ok": true}
```

### Error responses (полный список после фикса)

| HTTP | `errorCode` | Когда | Изменение |
|---|---|---|---|
| 200 | `{"ok":true}` | lease продлён | без изменений |
| 400 | `share.tokenMissing` | пустой/отсутствующий `sessionTokenHash` | без изменений |
| 410 | `share.leaseExpired` | sessionTokenHash не найден ИЛИ активный lease истёк ИЛИ `expires_at < now()` | без изменений |
| 500 | `share.internal` ← **НОВОЕ** | SQLException / иное | **НОВОЕ** — раньше маскировалось под `share.leaseExpired` 410 (тоже вводило в заблуждение) |

## Эндпоинт: `POST /api/public/share/debug` (без изменений, FR-020)

### Request

```http
POST /api/public/share/debug HTTP/1.1
Host: sm-karaoke.ru
Content-Type: application/json

{"secret": "abc123XYZ-_base64url_32bytes"}
```

### Response: 200 OK (без изменений)

```json
{
  "step1_resolve": "OK linkId=42",
  "linkId": 42,
  "step2_ownerId": "OK ownerId=123",
  "ownerId": 123,
  "step3_songId": "OK songId=12345",
  "songId": 12345,
  "step4_songInfo": "OK name='Песня' author='Автор' album='Альбом' year=2024",
  "step5_checkExisting": "OK existingTokenHash=null leaseUntil=null"
}
```

### Response при системной ошибке (после фикса)

```json
{
  "step1_resolve": "FAILED: PSQLException: ERROR: relation \"tbl_song_share_links\" does not exist",
  "error_step1": "class=org.postgresql.util.PSQLException msg=ERROR: relation \"tbl_song_share_links\" does not exist"
}
```

**Без изменений** в коде `debugTryClaim` — он уже использует `catch (e: Throwable)`, который сохраняет реальный класс исключения в JSON. После фикса `tryClaim` (catch-all → InternalError) не влияет на debug, т.к. debug вызывает `debugTryClaim`, не `tryClaim`.

### Security note

`/debug` остаётся **публичным** (без auth). Это намеренно для hotfix — оператор/разработчик должен мочь вызвать его без логина. Защита за `X-Share-Debug-Key` — **backlog spec 164** (см. Clarification Q3 спеки 167).

## Эндпоинт: `POST /api/public/share/release` (без изменений)

### Request

```http
POST /api/public/share/release HTTP/1.1
Content-Type: application/json
Cookie: (none)

{"sessionTokenHash": "<64 hex>", "result": "closed"}
```

Или через form-urlencoded (для `navigator.sendBeacon`):
```
sessionTokenHash=<64 hex>&result=closed
```

### Response: 200 OK (без изменений)

```json
{"ok": true}
```

`/release` НЕ имеет `catch (_: Exception)` маскировки. Системная ошибка → Spring default 500 без структурированного JSON. **Приемлемо для hotfix** — фронт `KaraokePlayer` обрабатывает это как «lease освобождён best-effort».

## Эндпоинт: `GET /api/public/share/mine/{songId}` (без изменений)

### Request

```http
GET /api/public/share/mine/12345 HTTP/1.1
Cookie: (site_user session — требуется премиум)
```

### Response: 200 OK

```json
{
  "link": {
    "linkId": 42,
    "songId": 12345,
    "active": true,
    "expiresAt": 1723372800000,
    "createdAt": 1723369200000,
    "revokedAt": null,
    "revokeReason": "",
    "firstUsedAt": 1723370000000,
    "lastUsedAt": 1723370100000,
    "sessionsTotal": 1,
    "rejectedConcurrent": 0
  }
}
```

Или если ссылки нет: `{"link": null}`.

Без изменений. Без catch-all маскировки.

## Эндпоинт: `POST /api/public/share/mine/{songId}/revoke` (без изменений)

### Request

```http
POST /api/public/share/mine/12345/revoke?reason=manual HTTP/1.1
Cookie: (site_user session — требуется премиум-владелец)
```

### Response: 200 OK

```json
{"revoked": true}
```

Без изменений. Без catch-all маскировки.

## Поведение фронта после фикса

### `karaoke-public/src/components/ShareLinkModal.vue`

Создание ссылки:
- **200 OK** → модалка показывает URL + кнопку «Копировать» (без изменений).
- **400 / 401 / 403** → existing error-handling (без изменений).
- **429 `share.linkAlreadyActive`** → existing error-handling (без изменений).
- **500 `share.internal`** (НОВОЕ) → `else errorMessage.value = 'Не удалось создать ссылку'` (line 275, existing fallback). Можно уточнить текст в backlog, но generic уже корректнее, чем «Ссылка формируется» при полностью сломанной БД.

### `karaoke-public/src/components/ShareView.vue`

Claim ссылки:
- **200 OK** → редирект на `/player/{songId}?share=1&session=...` (без изменений).
- **404 `share.notFound`** → заглушка «Ссылка недоступна» (без изменений).
- **409 `share.concurrentLimit`** → заглушка «Уже 2 зрителя, попробуйте позже» (без изменений).
- **500 `share.internal`** (НОВОЕ) → generic «Не удалось открыть плеер» вместо «Ссылка недоступна». Это **семантически точнее**: если БД упала, ссылка может быть валидной, и пользователю нужно попробовать позже, а не думать, что ссылка отозвана.

### `karaoke-public/src/composables/useShareLink.js` / `services/songShareLink.js`

Heartbeat:
- **410 `share.leaseExpired`** → плеер ставит на паузу + overlay «Время сессии истекло» (без изменений).
- **500 `share.internal`** (НОВОЕ) → generic error-handler. Backlog: добавить специфический текст «Внутренняя ошибка сервера, lease продлён на следующем heartbeat» или аналог.

## Итоговая матрица изменений

| Эндпоинт | До фикса (системная ошибка → ?) | После фикса (системная ошибка → ?) | Поведение для ожидаемых ошибок |
|---|---|---|---|
| `POST /claim` | 500 `share.notFound` (мискейдж) | **500 `share.internal`** (точно) | без изменений |
| `POST /{songId}/create` | 500 `share.notFound` (мискейдж) | **500 `share.internal`** (точно) | без изменений |
| `POST /heartbeat` | 410 `share.leaseExpired` (мискейдж) | **500 `share.internal`** (точно) | без изменений |
| `POST /release` | 500 без JSON (Spring default) | без изменений | без изменений |
| `POST /debug` | 200 + `"FAILED: NotFound"` (мискейдж) | 200 + `"FAILED: PSQLException: ..."` (точно) | без изменений |
| `GET /mine/{songId}` | 500 без JSON (Spring default) | без изменений | без изменений |
| `POST /mine/{songId}/revoke` | 500 без JSON (Spring default) | без изменений | без изменений |

## Открытые вопросы для будущих раундов (НЕ в этом hotfix)

- Защита `/api/public/share/debug` за `X-Share-Debug-Key` (env `WEB_SHARE_DEBUG_KEY`) — backlog spec 164.
- `@ControllerAdvice` для централизованного маппинга `ShareException` → HTTP response (использует поле `httpStatus`) — backlog spec 164.
- Уточнение текстов на фронте для `share.internal` в `ShareView.vue`, `ShareLinkModal.vue`, `songShareLink.js` — backlog.
- Перевод `throw NotFound()` в `loadSongInfo:962` на `throw SongUnavailable()` — backlog.
