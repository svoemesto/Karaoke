# Per-Feature: Site Traffic Resilience (sampling/dedup/caches/rate-limit/retention/debug)

> **Feature Key**: `site-traffic-resilience`
> **Status**: active
> **Slug**: `site-traffic-resilience`
> **Спека**: [specs/187-site-traffic-anomaly-investigation/spec.md](../../specs/187-site-traffic-anomaly-investigation/spec.md)
> **План**: [specs/187-site-traffic-anomaly-investigation/plan.md](../../specs/187-site-traffic-anomaly-investigation/plan.md)
> **Tasks**: [specs/187-site-traffic-anomaly-investigation/tasks.md](../../specs/187-site-traffic-anomaly-investigation/tasks.md)

## Что делает

Комплексная защита от аномальной нагрузки на сайт `sm-karaoke.ru` (периодические 7-10 мин
недоступности 1-2 раза в неделю). Состоит из 5 независимых слоёв:

1. **Sampling + Dedup REST-событий** (`SamplingFilter`, `DedupCache`): только 1 из N
   `tbl_events` INSERT'ов фактически доходит до БД — для анонимов 1/20, для залогиненных 1/5,
   для админов 1/1 (всё). Дедуп по `(restName, parameters, anonId|userId)` с TTL 30 сек —
   одинаковые запросы в течение 30 сек не пишутся вообще.
2. **Server-side polling caches** (`PollingCache`): для `/api/public/news/since` (TTL=60s),
   `/api/public/account/chat/unreadcount` (TTL=10s), `/api/public/share/heartbeat` (TTL=15s).
   Одинаковые запросы в течение TTL отдают кешированный ответ БЕЗ обращения к БД.
3. **MVP — Zakroma без Spring-redirect**: `AuthorTilePublicDto.authorPictureUrl` теперь
   указывает на прямой URL `/minio/karaoke/...` (nginx-`/minio/` location), а не на
   `/api/public/picture?file=...` (Spring → 302 → nginx). 200+ редиректов в секунду
   исчезают (Pass 50, FR-002). Legacy endpoint сохранён (FR-001).
4. **Rate-limit `/song-picture/**` и `/song-vk-image/**`**: 60 req/мин на IP через
   `RateLimitInterceptor` (Spring `HandlerInterceptor`). Защита от bot-storm на втором
   уровне после nginx User-Agent-фильтра (Pass 60).
5. **Retention `tbl_events`**: ежедневный cleanup в 03:00 (cron `0 0 3 * * *`) через
   `EventsRetentionScheduler` — старше 7 дней удаляются (`KARAOKE_WEB_EVENTS_RETENTION_DAYS`).

Плюс **Debug endpoint `/api/public/debug/db`** для мониторинга ресурсов в режиме
приближения к исчерпанию (FR-013, US6). Доступ через IP allowlist + master-flag
(по умолчанию отключён).

## Зачем

**Проблема** (Pass 50-52, 2026-08): периодически сайт падает на 7-10 мин. Первый раунд
фиксов (`Pass 52`, спека 174, ветка `174-fix-news-since-anon`) устранил самый очевидный
источник — `/api/public/news/since` для анонимов возвращал 3.5 MB JSON × 45 сек × N
пользователей × N вкладок → exhaustion `pg max_connections = 100`. Но инциденты
продолжались.

**Что осталось** (research.md Таблица A + B):
- `/api/public/zakroma/stream/metrics` — до 5000 INSERT/мин при пике (FR-P1).
- `/api/public/events` — обёртка над `doRegisterEvent` (FR-P1).
- `/api/public/authors-tiles` — формирование 200+ URL через Spring-redirect (FR-P1).
- `/api/public/song-picture/{id}`, `/api/public/song-vk-image/{id}` — bot-storm (FR-P1).
- 3 polling-endpoint'а без server-side кеша (FR-P1).
- Нет мониторинга ресурсов до инцидента (FR-P3, US6).

**Цель**: устранить источники аномальной нагрузки и дать админу инструмент видеть
приближение к исчерпанию ресурсов ДО того, как сайт упадёт.

## Как работает

### 1. Sampling + Dedup (FR-006/007, US3)

Поток события:
1. Клиент делает REST-запрос → nginx → Spring `karaoke-web`.
2. Контроллер вызывает `MainController.doRegisterEvent(data, request, siteUserId)`.
3. Для `EventType.CALL_REST` ДО `insertEvent`:
   - `SamplingFilter.shouldSkip(restName, parameters, siteUserId, anonId)` решает.
   - Если `true` — `return true` без INSERT (но endpoint отвечает 200 OK).
   - Если `false` — INSERT выполняется.
4. Sampling решение:
   - `userType = if (siteUserId > 0) LOGGED else ANONYMOUS` (admin = LOGGED на текущий момент).
   - `samplingRate` из `KaraokeProperties` (anon=20, logged=5, admin=1).
   - **Дедуп-ключ** = `"$restName|$canonicalParams|$identity"` (anon/userId).
   - Если ключ за последние TTL — дубликат, return `true`.
   - Иначе `random.nextInt(samplingRate) == 0` — пропускаем в N-1 из N случаев.

**Per-US3 dedup scope**: clarified Q2 — per-`(anonId|userId)`. Два разных анонима
НЕ дедупятся (для них работает sampling); один аноним с тем же запросом в течение 30с
— дедупится.

### 2. Polling caches (FR-008, US2)

Простой TTL-based in-memory кеш в `ConcurrentHashMap<K, CacheEntry<V>>`. Каждый
endpoint имеет СВОЙ TTL (clarified Q1 — per-endpoint):

| Endpoint | TTL | Обоснование |
|----------|-----|-------------|
| `/api/public/news/since` | 60s | новости меняются нечасто (release-анонсы), polling 45s |
| `/api/public/account/chat/unreadcount` | 10s | UX бейджа, polling 20s |
| `/api/public/share/heartbeat` | 15s | heartbeat каждые 25s, кеш = каждый 2-й no-op |

Для `/share/heartbeat` **НЕ кешируем** `500 share.internal` — внутренние ошибки нельзя
закэшировать на 15 сек, иначе потеряем сигнал о падении БД.

### 3. MVP — Zakroma URL (FR-002, US1)

`AuthorTilePublicDto.fromAuthorName` теперь формирует:
```
authorPictureUrl = "/minio/karaoke/${URLEncoder.encode(previewFileName).replace("+", "%20")}"
```

Раньше было:
```
authorPictureUrl = "/api/public/picture?file=${URLEncoder.encode(previewFileName)}"
```

Что меняется:
- **Раньше**: 200+ запросов с `/zakroma` → каждый → Spring `PublicApiController.picture()` → 302 → nginx `/minio/`.
- **Теперь**: 200+ запросов → каждый → nginx `/minio/karaoke/...` напрямую (без Spring).

Legacy endpoint `PublicApiController.picture()` сохранён (FR-001) — старые deep-link'и,
тесты и другой код (PublicPlayerController, ZakromaPublicDto и т.д.) продолжают работать.

### 4. Rate-limit (FR-010, US Defense)

`RateLimitInterceptor` (Spring `HandlerInterceptor`) регистрируется в `WebMvcConfig`
на URL patterns:
- `/api/public/song-picture/**` → `endpointName="song-picture"`, `limitPerMinute=60`.
- `/api/public/song-vk-image/**` → `endpointName="song-vk-image"`, `limitPerMinute=60`.

Алгоритм **fixed window** (минута):
- `key = "ip|endpointName"`.
- `bucket = (windowStartMs, count)`.
- Если `now - windowStartMs >= 60_000` — окно сбрасывается.
- Инкремент `count`. Если `count > limit` — `sendError(429, "rate_limit_exceeded")` + `Retry-After: 60`.

**Второй уровень защиты**: Pass 60 уже сделал nginx-redirect по User-Agent для ботов;
это дополнение — если бот всё-таки пройдёт User-Agent-фильтр, он упрётся в 429.

### 5. Retention (FR-011)

`EventsRetentionScheduler` (Spring `@Component` с `@Scheduled(cron = "0 0 3 * * *")`):
- 03:00 каждый день.
- `DELETE FROM tbl_events WHERE last_update < now() - retentionDays`.
- Default retention = 7 дней (`KARAOKE_WEB_EVENTS_RETENTION_DAYS`).
- SQL-ошибки → `log.warn` (не бросает — иначе Spring выключит задачу).

**Почему НЕ через SyncRegistry**: `tbl_events` намеренно НЕ синхронизируется (см. комментарий
`НЕ tbl_events` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/sync/SyncTarget.kt`).
Retention scheduler безопасен для sync.

### 6. Debug endpoint (FR-013, US6)

`GET /api/public/debug/db` возвращает:
```json
{
  "pgActiveConnections": 12,
  "pgIdleConnections": 88,
  "pgMaxConnections": 100,
  "currentThreadCount": 87,
  "currentTomcatMaxThreads": 200,
  "sampledAt": "2026-08-14T..."
}
```

Доступ через `DebugDbAccessGuard.isAllowed(...)`:
1. `KARAOKE_WEB_DEBUG_DB_ENABLED=true` (default `false`).
2. `KARAOKE_WEB_DEBUG_DB_ALLOWED_IPS` содержит IP клиента.

Если оба условия выполнены — 200 OK + JSON. Иначе — 404 Not Found (endpoint невидим).

## Инварианты / правила

1. **Sampling НЕ блокирует endpoint**: даже если `shouldSkip=true`, endpoint возвращает
   `200 OK` — клиент НЕ замечает sampling.
2. **Dedup по `(restName, parameters, anonId|userId)`** (clarified Q2): НЕ глобальный дедуп,
   иначе два разных анонима поделили бы кеш.
3. **Per-endpoint TTL** (clarified Q1): разные TTL для разных polling-endpoint'ов.
4. **`/share/heartbeat` НЕ кеширует 500** — системные ошибки нельзя закэшировать.
5. **`tbl_events` НЕ в SyncRegistry**: retention scheduler безопасен, но НЕ должен запускаться
   на admin-машине с LOCAL-БД (таблица PROD-only).
6. **Rate-limit через URL pattern, не per-method аннотацию** (`@RateLimit` не существует
   в кодовой базе).
7. **Debug endpoint по умолчанию ВЫКЛЮЧЕН**: `KARAOKE_WEB_DEBUG_DB_ENABLED=false`.
8. **Backward compatibility** (FR-019): legacy `PublicApiController.picture()` сохранён,
   `/api/public/news/since` для анонимов возвращает `count=0` (Pass 52 уже сделал).

## Известные ловушки

- **Race condition в дедупе**: 2 параллельных запроса с одним ключом могут оба
  попасть в `dedupCache.compute()` — но `ConcurrentHashMap.compute` сериализует
  доступ к ключу атомарно, поэтому один из них увидит `lastSeen` только что записанный
  и вернёт `true`. OK.
- **Race condition в rate-limit**: 2 параллельных запроса могут оба пройти 60-секундный
  лимит на 1 больше. Это приемлемо — fixed-window допускает до 2× лимита на границе окна.
- **`@RateLimit` аннотация НЕ существует**: код ссылается на URL pattern registration в
  `WebMvcConfig`, не на per-method аннотацию. См. `specs/187.../plan.md` D-7 и U2.
- **`pg_max_connections = 100`**: при rate-limit 60 req/мин на IP и 100 одновременных IP
  получаем 6000 req/мин потенциально. Это НОРМАЛЬНО, потому что:
  1. sampling 1/20 для анонимов → 5% реальных INSERT'ов;
  2. polling cache снижает SELECT'ы к БД;
  3. rate-limit 429 отбивает bot-storm.
- **HikariCP `maximumPoolSize=10`**: меньше чем `pg max_connections=100`. Проблема
  исчерпания HikariCP была Pass 52 — решается polling cache + sampling.
- **Heartbeat НЕ пишет в `tbl_events`** (FR-009): уже выполнено в Pass 174, verify
  в T017 (нет вызова `doRegisterEvent` из `heartbeat`).

## Ссылки

- [specs/187-site-traffic-anomaly-investigation/](../../specs/187-site-traffic-anomaly-investigation/) — спека, plan, tasks, research.
- [docs/features/dual-db-sync.md](../dual-db-sync.md) — почему `tbl_events` НЕ в SyncRegistry.
- [docs/features/stats.md](../stats.md) — контекст in-memory кешей.
- [docs/features/monitoring.md](../monitoring.md) — debug endpoint как часть мониторинга.
- [Pass 50-60 в docs/architecture-notes.md](../../docs/architecture-notes.md) — история фиксов.
- Конкретные классы:
  - `DedupCache` — `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/DedupCache.kt`.
  - `PollingCache` — `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/PollingCache.kt`.
  - `SamplingConfig` — `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/SamplingConfig.kt`.
  - `KaraokeProperties` (env) — `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/KaraokeProperties.kt`.
  - `SamplingFilter` — `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/SamplingFilter.kt`.
  - `EventsRetentionScheduler` — `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/EventsRetentionScheduler.kt`.
  - `RateLimitInterceptor` — `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/RateLimitInterceptor.kt`.
  - `DebugDbAccessGuard` — `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/DebugDbAccessGuard.kt`.
  - `DebugDbController` — `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/DebugDbController.kt`.
