---
status: Active
slug: share-link
type: topic
related:
  - ../features/164-complete-guest-share-link.md
  - ../features/166-fix-share-link-timezone.md
  - ../features/167-fix-share-claim-500.md
  - ../features/169-share-link-in-premium-compare.md
  - ../features/171-admin-subscriptions-history.md
  - ../features/172-db-sync-temporary-links.md
  - ../domain/publishing.md
  - ../domain/identity.md
---

# share-link — паттерн гостевого доступа к премиум-песням (topic)

> Drill-down для архитектурного паттерна временных ссылок. Документы по
> конкретным фичам — в [`../features/`](../features/README.md).

## Назначение

**share-link** — паттерн выдачи **временного доступа** к премиум-песне
пользователю, у которого нет premium-подписки. Владелец песни (premium
или free с free-песней, см. спеку 005) генерирует ссылку с TTL 1ч/24ч/7д
и передаёт её гостю. Гость переходит по ссылке → попадает на страницу
claim → получает `sessionStorage['kp_share_session_<songId>']` →
открывает плеер как «гость по share-ссылке».

## Компоненты паттерна

### Backend

- **`PublicApiController.kt`** — публичный API (статистика, share-claim, songeditor).
- **`SiteUserShareService.kt`** — бизнес-логика: создание ссылки, claim,
  release, sweep. Хранит записи в `tbl_share_links` (таблица временных
  ссылок).
- **`ShareLinkSweeper`** — фоновый sweep (см. `architecture/observability.md`):
  авто-отзыв при:
  - потере премиума владельцем
  - тег `SKIP` на песне
  - истечение `active_session_lease_until`
  - ручной `ban` админом
- **webvue3 endpoints** `/api/siteusers/share/{links,sessions,links/revoke}`
  — админские операции: список, активные сессии, revoke.

### Frontend

- **`ShareLinkModal.vue`** — модалка генерации ссылки в карточке песни
  (выбор TTL, кнопка «Скопировать ссылку», автообновление статуса).
- **`ShareClaimView.vue`** — публичная страница `claim` (гость переходит
  по ссылке, видит информацию о песне, нажимает «Открыть»).
- **`PlayerView.vue`** — общий плеер; пускает гостя через
  `validateShareSession()` (пробрасывает `sessionStorage['kp_share_session_<songId>']`).
  Без этого гость физически не мог открыть плеер (PlayerView ждал
  `sessionStorage['kp_token_${id}']`, которого у гостя нет).
- **`KaraokePlayer.js`** — шлёт `heartbeat()` каждые ~30 сек
  (lease = 60 сек), `release()` на `_onEnded`/`beforeunload`/
  `pagehide`/`visibilitychange`.

### Database

- **`tbl_share_links`** — id, songId, ownerUserId, expiresAt, linkToken,
  revoked (bool), banReason.
- **`tbl_share_sessions`** — id, shareLinkId, deviceId, claimAt,
  lastHeartbeatAt, leaseUntil, releasedAt, releaseReason.
- Лимит **2 устройств** на ссылку (контроль через счётчик активных
  sessions на момент claim).

## Контракт

### Создание ссылки (владелец)

```http
POST /api/siteusers/share/links
Content-Type: application/json
{ "songId": 12345, "ttl": "PT24H" }   # PT1H / PT24H / P7D
→ 200 OK { "id": ..., "linkToken": "...", "expiresAt": "..." }
```

Ссылка для гостя: `https://karaoke.example/share/{linkToken}`.

### Claim (гость)

```http
POST /api/public/share/claim
Content-Type: application/json
{ "linkToken": "...", "deviceId": "..." }
→ 200 OK { "songId": 12345, "shareSessionToken": "...", "leaseUntil": "..." }
```

`shareSessionToken` кладётся в `sessionStorage['kp_share_session_<songId>']`
и используется PlayerView при загрузке.

### Heartbeat (гость, каждые ~30 сек)

```http
POST /api/public/share/heartbeat
{ "shareSessionToken": "...", "deviceId": "..." }
→ 200 OK { "leaseUntil": "<+60s>" }
```

Если `leaseExpired` — 410 Gone → overlay «Время сеанса истекло» +
кнопка «Закрыть» (без авто-recovery).

### Release (гость, при закрытии)

```http
POST /api/public/share/release
{ "shareSessionToken": "...", "reason": "ended|beforeunload|pagehide|visibilitychange" }
→ 200 OK
```

`release()` идёт через `navigator.sendBeacon` (best-effort idempotent,
даже если страница закрывается).

### Revoke (владелец или админ)

```http
POST /api/siteusers/share/links/revoke
{ "shareLinkId": ... }
→ 200 OK
```

После revoke → 404 `revoked` → overlay + кнопка «Закрыть».

## Timezone (спека 166)

- **В БД** — UTC (или МСК, зависит от миграции). DRIFT: на момент спеки
  166 был обнаружен рассинхрон МСК vs UTC.
- **На UI** — deviceTZ (timezone браузера пользователя).
- Все конвертации — на стороне сервера (`TimeZoneConverter`),
  UI получает уже локализованные строки.

## Премиум и free (спека 005 + 169)

- **Free-песня** — владелец может сгенерировать ссылку даже без
  премиум-подписки (free = доступна всем).
- **Premium-песня** — ссылка работает пока у владельца есть премиум.
  Если премиум истёк — `ShareLinkSweeper` отзывает все активные ссылки
  этой песни (`releaseReason="owner_lost_premium"`).
- Сравнение FREE vs PREMIUM в таблице `/premium` упоминает share-link
  как «Временная ссылка для гостя» (спека 169).

## Безопасность и лимиты

- **Лимит 2 устройств на ссылку** — контроль через счётчик активных
  sessions. При попытке 3-го claim — 409 Conflict.
- **`linkToken`** — криптостойкий random, 32 байта base64url.
  Никогда не логируется (PII concern).
- **`shareSessionToken`** — привязан к `deviceId` (fingerprint из
  `navigator.userAgent + screen + tz`); не может быть передан другому
  устройству без re-claim.
- **Rate limit** на `/api/public/share/claim` — 5 req/min/IP (anti-abuse).
- **Idempotency** — claim/heartbeat/release идемпотентны
  (см. [`idempotency.md`](idempotency.md)). Повторный claim с тем же
  `deviceId` возвращает существующий session, не создаёт новый.

## Мониторинг и sweep

- **Sweep каждые 60 сек** в `ShareLinkSweeper` (см.
  [`observability.md`](observability.md) про sweep-паттерн).
- **SSE-события** `share-link-revoked`, `share-session-expired` —
  для realtime UI обновления у владельца.
- **Метрики** (`tbl_events`): `share_link_created`, `share_claimed`,
  `share_heartbeat`, `share_released`, `share_revoked`,
  `share_session_expired` — для аналитики в stats.

## Связанные компоненты

- [`cache-invalidation.md`](cache-invalidation.md) — `setWebvueProp`
  + Vuex + SSE: при revoke ссылки у владельца — кэш инвалидируется
  через SSE.
- [`idempotency.md`](idempotency.md) — claim/heartbeat/release
  идемпотентны по `deviceId` + `shareSessionToken`.
- [`observability.md`](observability.md) — sweep-паттерн + heartbeat
  паттерн + release-on-unload.
- [`../domain/publishing.md`](../domain/publishing.md) — share-link —
  часть публикации (alternative channel доставки).
- [`../domain/identity.md`](../domain/identity.md) — owner/ban/JWT
  identity.

## История

- Создан: 2026-08-14 (Pass 43 follow-up спеки 189-live-documentation)
- Последнее обновление: 2026-08-14