# SQL migrations: Users-related (NNN 06, 12, 13, 14, 21, 27_listening)

> **Домен**: system (infrastructure)
> **Компонента**: детальный каталог SQL миграций для `tbl_site_users`.

## NNN 06: `site_users`

```sql
CREATE TABLE tbl_site_users (
    id BIGINT PK,
    email VARCHAR NOT NULL,
    password_hash VARCHAR NOT NULL,
    display_name VARCHAR,
    sponsr_uid VARCHAR,
    is_premium BOOLEAN,
    is_permanent_premium BOOLEAN,
    sponsr_premium_until TIMESTAMP,
    site_premium_until TIMESTAMP,
    welcome_message_sent BOOLEAN,
    personal_discount_percent INT,
    is_banned BOOLEAN,
    ban_reason TEXT,
    last_login_at TIMESTAMP,
    created_at TIMESTAMP,
    recordhash VARCHAR(32)
);
```

(См. [site-user-entity.md](../../domains/identity/components/site-user-entity.md)
для полного списка полей.)

## NNN 12: `site_user_limits`

```sql
ALTER TABLE tbl_site_users
  ADD COLUMN IF NOT EXISTS max_favorites INT DEFAULT 0 NOT NULL,
  ADD COLUMN IF NOT EXISTS max_playlists INT DEFAULT 0 NOT NULL,
  ADD COLUMN IF NOT EXISTS max_playlist_items INT DEFAULT 0 NOT NULL;
```

- **Личные лимиты** (`max_favorites`, `max_playlists`,
  `max_playlist_items`).
- `0` = использовать дефолт (100/50/500, см. `PublicPlaylistController`).
- Колонки входят в `recordhash`.

## NNN 13: `site_user_editor_role`

```sql
ALTER TABLE tbl_site_users
  ADD COLUMN IF NOT EXISTS is_editor BOOLEAN DEFAULT false NOT NULL;
```

- **`is_editor`** — роль редактора караоке-разметки.
- Также обновляет `update_tbl_site_users_recordhash` trigger.

## NNN 14: `site_user_premium_sources`

```sql
ALTER TABLE tbl_site_users
  ADD COLUMN IF NOT EXISTS sponsr_premium_until TIMESTAMP,
  ADD COLUMN IF NOT EXISTS site_premium_until TIMESTAMP;
```

- **Временная ось премиума**:
  - `sponsr_premium_until` — через Sponsr.
  - `site_premium_until` — через подписку на сайт.
- `isEffectivePremium` (SiteUser.kt) проверяет ИЛИ вечный флаг, ИЛИ
  ручной `is_premium`, ИЛИ `sponsr_premium_until > now()`, ИЛИ
  `site_premium_until > now()`. **Гашение по истечении — без
  отдельного планировщика** (живая проверка `> now()`).

## NNN 21: `site_user_welcome_message`

```sql
-- (не изучено, Pass 343+)
```

- Поле `welcome_message_sent` (см. NNN 06) — отметка об отправке
  приветственного сообщения.

## NNN 27: `listening_history`

```sql
CREATE TABLE tbl_listening_history (
    id BIGINT PK,
    site_user_id BIGINT NOT NULL,
    song_id BIGINT NOT NULL,
    play_count BIGINT NOT NULL DEFAULT 0,
    last_played_at TIMESTAMP NOT NULL,
    last_update TIMESTAMP,
    recordhash VARCHAR(32)
);
```

См. [listeninghistory](../../domains/catalog/components/entities-catalog.md#listeninghistory).

## Архитектурное решение

**ВСЕ миграции users** имеют инструкцию «**применять вручную на
КАЖДОЙ БД отдельно (LOCAL + PROD 79.174.95.69:8832)**» — потому что
**`tbl_site_users` живёт на проде**, и миграция должна быть
применена на обеих БД согласованно (иначе recordhash mismatch →
sync failure).

## Связь

- [site-user-entity.md](../../domains/identity/components/site-user-entity.md) — full entity.
- [identity domain](../../domains/identity/domain.md) — bounded context.
- [monetization domain](../../domains/monetization/domain.md) — premium sources.

## Changelog

- **Pass 454** (2026-09-09): Initial. Автор: agent (Karaoke).