# Data Model: Временный полный доступ к песне

**Spec**: [./spec.md](./spec.md)
**Branch**: `164-complete-guest-share-link`

## Сущности

### 1. ShareLink (`tbl_song_share_links`)

Долгоживущий грант — owner создаёт, гость использует.

| Field | Type | Constraint | Description |
|---|---|---|---|
| `id` | integer IDENTITY | PK | Уникальный id |
| `owner_site_user_id` | integer | FK `tbl_site_users(id)` ON DELETE CASCADE | Владелец ссылки (премиум-пользователь) |
| `song_id` | bigint | NOT NULL, **без FK** (не связываем с sync песен) | Id песни |
| `token_hash` | varchar(64) | NOT NULL, UNIQUE | SHA-256 от исходного секрета (hex64) |
| `active` | boolean | NOT NULL DEFAULT true | true ровно одна на (owner, song) — UNIQUE partial index `idx_..._active` |
| `expires_at` | timestamp | NOT NULL | TTL ссылки |
| `created_at` | timestamp | NOT NULL DEFAULT now() | Момент создания |
| `revoked_at` | timestamp | NULL | Момент отзыва (NULL = активна) |
| `revoke_reason` | varchar(64) | NOT NULL DEFAULT '' | `''`, `manual`, `replaced`, `premium_lost`, `song_unavailable`, `expired`, `admin:<text>` |
| `first_used_at` | timestamp | NULL | Момент первого успешного claim (для отображения в UI владельца) |
| `last_used_at` | timestamp | NULL | Момент последнего claim/heartbeat |
| `active_session_token_hash` | varchar(64) | NULL | SHA-256 от sessionSecret активной lease-сессии |
| `active_session_browser_hash` | varchar(64) | NULL | SHA-256 от browserId устройства, держащего lease |
| `active_session_lease_until` | timestamp | NULL | Конец heartbeat-окна |
| `sessions_total` | integer | NOT NULL DEFAULT 0 | Счётчик созданных сессий (для UI владельца) |
| `rejected_concurrent` | integer | NOT NULL DEFAULT 0 | Счётчик отказов по лимиту устройств (для UI владельца) |
| `last_update` | timestamp | NOT NULL DEFAULT now() | Обновляется триггером |
| `recordhash` | varchar(32) | NULL | md5 от всех полей (для контракта KaraokeDbTable) |

**Индексы:**
- `idx_tbl_song_share_links_active` UNIQUE ON `(owner_site_user_id, song_id) WHERE active` — гарантирует одну активную ссылку на пару.
- `idx_tbl_song_share_links_owner` ON `(owner_site_user_id, created_at DESC)` — листинг ссылок пользователя.
- `idx_tbl_song_share_links_token_hash` UNIQUE ON `token_hash` — поиск по секрету в `/claim`.
- `idx_tbl_song_share_links_owner_active` ON `owner_site_user_id WHERE active` — подсчёт активных.
- `idx_tbl_song_share_links_created_at` ON `created_at` — лимит `maxGenerationsPerDay`.
- `idx_tbl_song_share_links_lease` ON `active_session_lease_until WHERE active_session_lease_until IS NOT NULL` — для sweeper.
- `idx_tbl_song_share_links_recordhash` ON `recordhash` — sync-совместимость.

**Состояния (state transitions):**

```
[new] → active=true
active=true → revoked (manual): revoke_reason='manual'
active=true → revoked (replaced): revoke_reason='replaced' (новая ссылка на эту же песню)
active=true → revoked (premium_lost): revoke_reason='premium_lost' (sweeper)
active=true → revoked (song_unavailable): revoke_reason='song_unavailable' (sweeper, SKIP/dateTimePublish)
active=true → revoked (admin): revoke_reason='admin:<text>'
active=true → revoked (expired): revoke_reason='expired' (sweeper, expires_at < now())
```

Состояние `active_session_*` — отдельный sub-state (lease):
```
[no lease] → lease_active (tryClaim)
lease_active → lease_active (heartbeat)
lease_active → lease_expired (sweeper, lease_until < now())
lease_active → lease_released (release, finished_at ставится)
```

### 2. ShareSession (`tbl_song_share_sessions`)

Короткоживущая playback-сессия — запись открытия ссылки конкретным устройством.

| Field | Type | Constraint | Description |
|---|---|---|---|
| `id` | integer IDENTITY | PK | Уникальный id |
| `share_link_id` | integer | FK `tbl_song_share_links(id)` ON DELETE CASCADE | Id ссылки |
| `song_id` | bigint | NOT NULL | Денормализовано для быстрого листинга в админке |
| `browser_hash` | varchar(64) | NOT NULL | SHA-256 от `browserId` в localStorage гостя |
| `owner_site_user_id` | integer | NOT NULL | Владелец ссылки (для админских запросов без JOIN) |
| `anon_id` | varchar(64) | NOT NULL DEFAULT '' | anonId гостя (если был залогинен ранее в этой сессии) |
| `opened_at` | timestamp | NOT NULL DEFAULT now() | Момент первого claim |
| `started_at` | timestamp | NULL | Момент первого PLAY (НЕ claim) |
| `last_seen_at` | timestamp | NOT NULL DEFAULT now() | Обновляется heartbeat'ом |
| `finished_at` | timestamp | NULL | Момент завершения (ended/closed/timeout/revoked/replaced) |
| `result` | varchar(16) | NOT NULL DEFAULT '' | `''`, `ended`, `closed`, `timeout`, `revoked`, `replaced` |
| `client_ip_hash` | varchar(64) | NOT NULL DEFAULT '' | SHA-256 от IP + daily-rotating-salt (GDPR) |
| `user_agent_hash` | varchar(64) | NOT NULL DEFAULT '' | SHA-256 от User-Agent + salt (GDPR) |
| `last_update` | timestamp | NOT NULL DEFAULT now() | Обновляется триггером |
| `recordhash` | varchar(32) | NULL | md5 от всех полей |

**Индексы:**
- `idx_tbl_song_share_sessions_link` ON `(share_link_id, opened_at DESC)` — листинг сессий по ссылке (админка).
- `idx_tbl_song_share_sessions_unfinished` ON `(share_link_id) WHERE finished_at IS NULL` — для sweeper.
- `idx_tbl_song_share_sessions_last_seen` ON `(last_seen_at) WHERE finished_at IS NULL` — для sweeper timeout detection.
- `idx_tbl_song_share_sessions_recordhash` ON `recordhash`.

## Lifecycle (полный путь)

```
1. OWNER создаёт ссылку
   POST /api/public/share/{songId}/create?ttlSeconds=3600|86400|604800
   → tbl_song_share_links INSERT (active=true, expires_at=now()+ttl, token_hash=SHA256(secret))
   → Возврат: { linkId, secret, url, expiresAt, expiresAtMs, expiresAtLabel, ttlSeconds }

2. OWNER перевыпускает (если скомпрометирована)
   POST /api/public/share/{songId}/create (повторно)
   → Старая: UPDATE active=false, revoked_at=now(), revoke_reason='replaced'
   → Новая: INSERT (как выше)
   → Возврат: новая ссылка

3. OWNER отзывает
   POST /api/public/share/mine/{songId}/revoke
   → UPDATE active=false, revoked_at=now(), revoke_reason='manual'
   → UPDATE tbl_song_share_sessions SET finished_at=now(), result='revoked' WHERE share_link_id=X AND finished_at IS NULL
   → UPDATE tbl_song_share_links SET active_session_* = NULL WHERE id=X

4. GUEST переходит по ссылке
   GET /share/{id}/{secret}  → SPA рендерит ShareView
   POST /api/public/share/claim { secret, browserHash } → { linkId, songId, sessionTokenHash, redirectTo, songName, author, album, year, albumImageUrl, artistImageUrl }

5. GUEST открывает плеер
   → router.push(`/player/${songId}?share=1&session=${sessionTokenHash}`)
   → PlayerView.vue читает session, передаёт в KaraokePlayer + кладёт в sessionStorage['kp_share_session_${songId}']
   → KaraokePlayer шлёт запросы с `?session=${sessionTokenHash}`

6. GUEST смотрит (heartbeat каждые 25 сек, пока вкладка открыта)
   POST /api/public/share/heartbeat { sessionTokenHash }
   → UPDATE tbl_song_share_links SET active_session_lease_until = now() + 90s, last_used_at = now()
   → UPDATE tbl_song_share_sessions SET last_seen_at = now()

7. GUEST закрыл вкладку / плеер доиграл
   navigator.sendBeacon('/api/public/share/release', { sessionTokenHash, result: 'closed'|'ended' })
   → UPDATE tbl_song_share_sessions SET finished_at=now(), result=...
   → UPDATE tbl_song_share_links SET active_session_* = NULL

8. Sweeper (каждые 60 сек)
   - Если active_session_lease_until < now():
     → закрыть активную сессию (result='timeout')
     → обнулить active_session_*
   - Если expires_at < now() AND active=true:
     → active=false, revoke_reason='expired'
   - Если owner.isEffectivePremium = false:
     → active=false, revoke_reason='premium_lost'
   - Если песня имеет SKIP или dateTimePublish в будущем:
     → active=false, revoke_reason='song_unavailable'

9. ADMIN смотрит / отзывает
   POST /api/siteusers/share/links { siteUserId, activeOnly=false, limit=50, target='local|remote' }
   POST /api/siteusers/share/links/revoke { shareLinkId, reason='admin:...', target='local|remote' }
   POST /api/siteusers/share/sessions { shareLinkId, target='local|remote' }
```

## Validation rules

### ShareLink
- `owner_site_user_id` должен существовать в `tbl_site_users` (FK).
- На момент `createLink`:
  - `owner.isEffectivePremium = true` (иначе 403 `share.notOwner`).
  - `songIsShareable(songId)` = true (id_status≥6, есть стемы/картинки, нет SKIP).
  - Количество активных у пользователя ≤ `maxActivePerUser` (5).
  - Количество генераций за сутки ≤ `maxGenerationsPerDay` (30).
  - Количество перевыпусков за час на одну песню ≤ `maxReissuesPerSongPerHour` (3).
- `ttlSeconds` ∈ {3600, 86400, 604800}.
- `token_hash` = `SHA-256(secret)` (hex64).
- `active=true` ровно одно на (owner, song) — partial unique index.

### ShareSession
- `browser_hash` = `SHA-256('browser:' + browserId)` (hex64).
- `result` ∈ {'', 'ended', 'closed', 'timeout', 'revoked', 'replaced'} (lowercase enforced).
- `client_ip_hash` = `SHA-256('ip:' + ip + ':share-salt')`.
- `user_agent_hash` = `SHA-256('ua:' + ua + ':share-salt')`.
- Одновременно ≤ `maxConcurrentSessions` (2) активных (не finished) сессий на ссылку.

## Capacity assumptions

- 1 премиум-пользователь: ≤ 5 активных ссылок, 30 генераций/сутки.
- 1 ссылка: ≤ 2 одновременных устройств, ≤ 1 heartbeat каждые 25 сек на устройство.
- 1 сессия: lease 90 сек.
- Sweeper: 1000 активных ссылок / тик, batch=100, sweep=60 сек → ~17 batch'ей / тик.

## Соответствие спецификации

| FR / Edge Case | Где отражено |
|---|---|
| FR-001 (session token в player endpoints) | Decision 1, Decision 2 |
| FR-002 (validateShareSession вызывается) | Decision 1 |
| FR-003 (router пускает с sessionStorage) | Lifecycle step 5 |
| FR-004 (PlayerView читает session) | Lifecycle step 5 |
| FR-008 (TTL whitelist) | Decision 6 |
| FR-010/011/012 (heartbeat/release) | Lifecycle steps 6-7 |
| FR-013 (browserHash match) | Validation rules → ShareSession |
| FR-014 (heartbeatIntervalSeconds) | Decision 3 |
| FR-040/041/042 (sweeper) | Decision 5 |
| FR-050 (usePlayerAccess принимает session) | Lifecycle step 5 |
| FR-060 (syncRegistry не расширяем) | Constitution Check III |
| Edge case: revoke пока гость смотрит | Lifecycle step 3, step 6 (следующий heartbeat → 410) |
| Edge case: lease timeout | Lifecycle step 8 |
| Edge case: перевыпуск | Lifecycle step 2 |
| Edge case: 2 устройства | Validation rules → ShareSession |
