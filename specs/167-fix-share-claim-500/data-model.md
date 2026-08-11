# Data Model: Починить 500 на `POST /api/public/share/claim`

**Дата**: 2026-08-11
**Spec**: [./spec.md](./spec.md) | **Plan**: [./plan.md](./plan.md) | **Research**: [./research.md](./research.md)

## Что меняется

Этот hotfix **не создаёт новых таблиц**. Миграция `deploy/karaoke-db/38_song_share_links.sql` уже в гите и применяется на проде как часть hotfix-релиза. Здесь — справочное описание схемы (для разработчика, который будет делать код-фикс) + единственная новая структура данных: `ShareException.InternalError` + `ShareErrorCode.INTERNAL`.

## Существующие таблицы (справочно)

### `public.tbl_song_share_links`

Грант — долгоживущая запись, созданная премиум-владельцем через `POST /api/public/share/{songId}/create`. Хранит SHA-256 от секрета, активен до `expires_at` или явного отзыва.

**Источник DDL**: `deploy/karaoke-db/38_song_share_links.sql:32-54`

| Поле | Тип | NOT NULL | Default | Описание |
|---|---|---|---|---|
| `id` | integer | ✅ | IDENTITY | PK |
| `owner_site_user_id` | integer | ✅ | — | FK → `public.tbl_site_users(id)` ON DELETE CASCADE |
| `song_id` | bigint | ✅ | — | bigint, без FK (не связываем с sync песен) |
| `token_hash` | varchar(64) | ✅ | — | SHA-256 от исходного секрета в hex (32 байта → 64 hex) |
| `active` | boolean | ✅ | `true` | `true` ровно одна на `(owner, song)` (см. индекс ниже) |
| `expires_at` | timestamp | ✅ | — | naive timestamp в МСК (см. FR-011 спеки 166) |
| `created_at` | timestamp | ✅ | `now()` | naive в МСК |
| `revoked_at` | timestamp | ❌ | NULL | naive в МСК; NULL = не отозвана |
| `revoke_reason` | varchar(64) | ✅ | `''` | `''` \| `'manual'` \| `'replaced'` \| `'premium_lost'` \| `'song_unavailable'` \| `'admin:<text>'` |
| `first_used_at` | timestamp | ❌ | NULL | naive в МСК; первый claim |
| `last_used_at` | timestamp | ❌ | NULL | naive в МСК; последний claim/heartbeat |
| `active_session_token_hash` | varchar(64) | ❌ | NULL | SHA-256 от текущего `sessionToken` (hex) |
| `active_session_browser_hash` | varchar(64) | ❌ | NULL | SHA-256 от `browserId` текущего устройства |
| `active_session_lease_until` | timestamp | ❌ | NULL | naive в МСК; конец текущего heartbeat-окна |
| `sessions_total` | integer | ✅ | `0` | счётчик созданных сессий |
| `rejected_concurrent` | integer | ✅ | `0` | счётчик отказов `concurrentLimit` |
| `last_update` | timestamp | ✅ | `now()` | naive в МСК; обновляется триггером |
| `recordhash` | varchar(32) | ❌ | NULL | md5 от канонизированной строки таблицы; устанавливается триггером `update_tbl_song_share_links_recordhash` |

**Индексы** (DDL `38_song_share_links.sql:89-111`):

| Имя | Тип | Поля | Назначение |
|---|---|---|---|
| `idx_tbl_song_share_links_active` | UNIQUE | `(owner_site_user_id, song_id) WHERE active` | одна активная ссылка на (owner, song) |
| `idx_tbl_song_share_links_owner` | обычный | `(owner_site_user_id, created_at DESC)` | листинг ссылок пользователя в админке |
| `idx_tbl_song_share_links_token_hash` | UNIQUE | `(token_hash)` | поиск по хэшу секрета в `/claim` |
| `idx_tbl_song_share_links_owner_active` | частичный | `(owner_site_user_id) WHERE active` | подсчёт «живых» ссылок пользователя (лимит `maxActivePerUser=10`) |
| `idx_tbl_song_share_links_created_at` | обычный | `(created_at)` | подсчёт «генераций за сутки» (лимит `maxGenerationsPerDay=30`) |
| `idx_tbl_song_share_links_lease` | частичный | `(active_session_lease_until) WHERE active_session_lease_until IS NOT NULL` | поиск ссылок с активной lease (sweeper + rate-limit) |
| `idx_tbl_song_share_links_recordhash` | обычный | `(recordhash)` | для sync (не используется, таблица PROD-only) |
| `tbl_song_share_links_last_update_index` | обычный | `(last_update)` | для SSE-обновлений на фронте |

**Триггеры** (DDL `39_song_share_recordhash.sql:45-64`):

| Имя | Когда | Что делает |
|---|---|---|
| `update_recordhash_song_share_links_trigger` | BEFORE INSERT OR UPDATE | вызывает `update_tbl_song_share_links_recordhash()` — пересчитывает `recordhash` |
| `update_last_updated_song_share_links_trigger` | BEFORE UPDATE | вызывает `update_last_updated()` — обновляет `last_update = now()` |

### `public.tbl_song_share_sessions`

Playback-сессия — короткоживущая запись, создаваемая анонимным гостем через `POST /api/public/share/claim`. Идентифицирует одно «устройство» (browserHash) на одну ссылку.

**Источник DDL**: `deploy/karaoke-db/38_song_share_links.sql:116-137`

| Поле | Тип | NOT NULL | Default | Описание |
|---|---|---|---|---|
| `id` | integer | ✅ | IDENTITY | PK |
| `share_link_id` | integer | ✅ | — | FK → `public.tbl_song_share_links(id)` ON DELETE CASCADE |
| `song_id` | bigint | ✅ | — | денормализовано для быстрого листинга |
| `browser_hash` | varchar(64) | ✅ | — | SHA-256 от `browserId` (localStorage UUID) |
| `owner_site_user_id` | integer | ✅ | — | денормализовано для админских запросов без JOIN |
| `anon_id` | varchar(64) | ✅ | `''` | если гость был авторизован — заполняется как в `tbl_events.anon_id` |
| `opened_at` | timestamp | ✅ | `now()` | naive в МСК; время claim |
| `started_at` | timestamp | ❌ | NULL | naive в МСК; первый PLAY (не claim!) |
| `last_seen_at` | timestamp | ✅ | `now()` | naive в МСК; обновляется heartbeat'ом |
| `finished_at` | timestamp | ❌ | NULL | naive в МСК; время завершения (release/sweeper/timeout) |
| `result` | varchar(16) | ✅ | `''` | `''` \| `'ended'` \| `'closed'` \| `'timeout'` \| `'revoked'` \| `'replaced'` |
| `client_ip_hash` | varchar(64) | ✅ | `''` | SHA-256 от `ip + daily-rotating-salt` (GDPR) |
| `user_agent_hash` | varchar(64) | ✅ | `''` | SHA-256 от `userAgent + salt` (GDPR) |
| `last_update` | timestamp | ✅ | `now()` | naive в МСК |
| `recordhash` | varchar(32) | ❌ | NULL | md5 от канонизированной строки таблицы |

**Индексы** (DDL `38_song_share_links.sql:170-178`):

| Имя | Тип | Поля | Назначение |
|---|---|---|---|
| `idx_tbl_song_share_sessions_link` | обычный | `(share_link_id, opened_at DESC)` | листинг сессий по ссылке (админка) |
| `idx_tbl_song_share_sessions_unfinished` | частичный | `(share_link_id) WHERE finished_at IS NULL` | поиск незавершённых (sweeper) |
| `idx_tbl_song_share_sessions_last_seen` | частичный | `(last_seen_at) WHERE finished_at IS NULL` | лимиты по lease (grace_pause) |
| `idx_tbl_song_share_sessions_recordhash` | обычный | `(recordhash)` | для sync (не используется) |
| `tbl_song_share_sessions_last_update_index` | обычный | `(last_update)` | SSE-обновления |

**Триггеры** (DDL `39_song_share_recordhash.sql:98-115`): аналогично `tbl_song_share_links` (`update_recordhash_song_share_sessions_trigger`, `update_last_updated_song_share_sessions_trigger`).

### Контракт `KaraokeDbTable`

`recordhash`-колонка и триггер нужны **только для единообразия** с остальным кодом — share-таблицы **не участвуют** в SyncRegistry (явно закреплено в шапке `38_song_share_links.sql:11-15` и FR-060 спеки 164). SyncRegistry.all **не расширяется**.

## Новые структуры данных

### `ShareException.InternalError` (sealed-подтип)

**Файл**: `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/SongShareLinkService.kt`

**Расположение в иерархии** — после `TokenMissing` (line 193), последний подтип sealed-class.

```kotlin
/**
 * Неожиданное системное исключение (SQLException, NPE в loadSongInfo, конфликт
 * recordhash-триггера и т.п.). Пробрасывается через catch-all в [tryClaim] /
 * [createLink] / [heartbeat] / [release] и маппится контроллером в HTTP 500 +
 * [ShareErrorCode.INTERNAL]. НЕ маскируется под [NotFound] / [LeaseExpired] —
 * иначе диагностика PROD-инцидентов невозможна (Pass 50, инцидент 2026-08-11:
 * relation "tbl_song_share_links" does not exist возвращался клиенту как
 * share.notFound, что делало невозможным понять, почему).
 *
 * @param cause оригинальное исключение ([java.sql.SQLException] /
 *              [RuntimeException] / etc.); сохраняется через [addSuppressed]
 *              для стандартного стек-трейса в логах.
 * @see docs/features/guest-share-link.md
 */
class InternalError(
    cause: Throwable,
) : ShareException(ShareErrorCode.INTERNAL, 500) {
    init {
        addSuppressed(cause)
    }
}
```

**Отношения**:
- Наследует `ShareException` (sealed class, line 166).
- `code = ShareErrorCode.INTERNAL` (новый).
- `httpStatus = 500` — наследуется полем, потенциально используется future `@ExceptionHandler`.
- `cause` — оригинальное исключение; стандартный Kotlin-механизм `addSuppressed` гарантирует, что в логах (`log.error("...", e)`) виден полный стек-трейс оригинала.

**Валидация / инварианты**:
- `cause` НЕ ДОЛЖЕН быть null (контракт Kotlin по `addSuppressed`).
- `cause` НЕ ДОЛЖЕН быть сам `InternalError` (избегаем рекурсии; если вложенное исключение тоже InternalError — логируем как есть).

**State transitions**: неприменимо (immutable value).

### `ShareErrorCode.INTERNAL` (enum-член)

**Файл**: `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/util/ShareErrorCode.kt`

**Расположение**: после `TOKEN_MISSING` (последний существующий, line 40), в конец enum.

```kotlin
/**
 * Неожиданная системная ошибка (SQLException, NPE и т.п.) — раньше маскировалась
 * под share.notFound / share.leaseExpired, что делало невозможной диагностику
 * PROD-инцидентов. Теперь это отдельный код 500, чтобы в логах было видно
 * «у нас упало внутри», а не «пользователю показали что-то странное».
 *
 * HTTP status: 500. Не ожидается в нормальном flow. Маппится на generic
 * «Внутренняя ошибка сервера, попробуйте позже» в ShareView.vue (TODO backlog).
 *
 * См. specs/167-fix-share-claim-500/spec.md, FR-013. Pass 50.
 */
INTERNAL("share.internal"),
```

**Отношения**:
- Член enum `ShareErrorCode` (file-level, line 9).
- `dbValue = "share.internal"` — JSON-ключ в ответах API.
- Маппится на HTTP 500 в контроллере (`PublicShareController.claim` + `create` + `heartbeat`).

**Валидация / инварианты**:
- `dbValue` должен совпадать с JSON-ключом в ответе: `{"errorCode":"share.internal"}`.
- Строковое значение не должно меняться после релиза (фронт `songShareLink.js` матчит по нему).

**Использование**:
- `ShareException.InternalError` — единственный источник.
- `PublicShareController.kt:claim` catch-блок для `InternalError`.
- `PublicShareController.kt:create` catch-блок для `InternalError`.
- `PublicShareController.kt:heartbeat` catch-блок для `InternalError`.

## Миграции (operations, не schema)

### `deploy/karaoke-db/38_song_share_links.sql`

**Применяется на проде вручную** (Constitution, «Ограничения агента», п. 2). Идемпотентна — повторное применение безопасно (см. research.md R4).

```bash
# Только пользователь, через ssh на прод-сервер:
docker exec -i karaoke-db psql -U postgres -d karaoke < deploy/karaoke-db/38_song_share_links.sql
```

Проверка после:
```bash
docker exec karaoke-db psql -U postgres -d karaoke -c "\dt tbl_song_share*"
# Должно вернуть 2 строки: tbl_song_share_links, tbl_song_share_sessions
```

### `deploy/karaoke-db/39_song_share_recordhash.sql`

```bash
docker exec -i karaoke-db psql -U postgres -d karaoke < deploy/karaoke-db/39_song_share_recordhash.sql
```

Проверка:
```bash
docker exec karaoke-db psql -U postgres -d karaoke -c "\df update_tbl_song_share*"
# Должно вернуть 2 функции: update_tbl_song_share_links_recordhash, update_tbl_song_share_sessions_recordhash
docker exec karaoke-db psql -U postgres -d karaoke -c "SELECT tgname FROM pg_trigger WHERE tgname LIKE '%song_share%'"
# Должно вернуть 4 триггера: update_recordhash_*_trigger (×2), update_last_updated_*_trigger (×2)
```

## Связи между сущностями

```
tbl_song_share_links.owner_site_user_id → tbl_site_users.id (FK CASCADE)
tbl_song_share_sessions.share_link_id → tbl_song_share_links.id (FK CASCADE)
tbl_song_share_links.song_id → tbl_settings.id (bigint, без FK — не связываем с sync)
tbl_song_share_sessions.song_id → tbl_settings.id (bigint, без FK)

ShareException (sealed)
├── NotFound (404)         — ссылка не найдена / отозвана / просрочена
├── Expired (404)          — expires_at < now()
├── Revoked (404)          — active=false
├── SongUnavailable (409)  — песня SKIP / не опубликована
├── ConcurrentLimit (409)  — >maxConcurrentSessions активных сессий
├── LeaseExpired (410)     — heartbeat не приходил >leaseTtlSeconds
├── RateLimited (429)      — >N claim/мин с одного IP
├── NotOwner (403)         — не премиум-владелец
├── LinkAlreadyActive (429, + reason/limit/actual)
├── TokenMissing (400)     — пустой/отсутствующий secret или browserHash
└── InternalError (500) ← НОВОЕ — неожиданная системная ошибка
```

## Что НЕ меняется в data model

- Никаких новых таблиц, колонок, индексов, триггеров.
- Никаких изменений в `SyncRegistry` (share-таблицы остаются PROD-only).
- Никаких изменений в `KaraokeDbTable` (нет контракта для share).
- Никаких изменений в `KaraokeProperties` / `WebShareProperties` (никаких новых параметров).
- Никаких изменений в env-переменных / секретах.

## Открытые вопросы для следующих раундов (НЕ в этом hotfix)

- **Backlog spec 164**: защита `/api/public/share/debug` за `X-Share-Debug-Key` (env `WEB_SHARE_DEBUG_KEY`).
- **Backlog spec 164**: heartbeat / release / sweeper (FR-010..FR-014, FR-040..FR-042 спеки 164).
- **Backlog**: явный текст на фронте для `share.internal` в `ShareView.vue` (сейчас generic «Не удалось…»).
- **Backlog**: перевод `throw NotFound()` в `loadSongInfo:962` на `throw SongUnavailable()` для семантически более точной ошибки при удалённой песне.
- **Backlog**: `@ControllerAdvice` для централизованного маппинга `ShareException` → HTTP (использует `httpStatus` поле, которое уже есть).
