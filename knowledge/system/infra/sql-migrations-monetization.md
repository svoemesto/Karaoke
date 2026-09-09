# SQL migrations: Monetization/News/Charts (NNN 15, 16, 17, 18, 19, 20, 22)

> **Домен**: system (infrastructure)
> **Компонента**: детальный каталог SQL миграций monetization/news.

## NNN 15: `monetization`

```sql
CREATE TABLE IF NOT EXISTS tbl_price_tariffs (
    id BIGINT PK,
    scope VARCHAR,            -- 'SONG' | 'SITE'
    name VARCHAR,
    price_rub NUMERIC,
    period_days INT,          -- 0 = бессрочно
    is_active BOOLEAN,
    is_default BOOLEAN,
    sort_order INT,
    recordhash VARCHAR(32)
);
```

(См. [monetization domain](../../domains/monetization/domain.md) для
полного описания тарифов.)

**Важно**: применять на КАЖДОЙ БД (LOCAL + PROD 79.174.95.69:8832).
`id_tariff` на `tbl_settings` — см. `deploy/recordhash_settings.sql`
(применять вместе).

## NNN 16: `cart_and_orders`

```sql
CREATE TABLE IF NOT EXISTS tbl_cart_items (
    id INT PK,
    site_user_id BIGINT,
    song_id BIGINT,
    id_price_tariff BIGINT,
    id_promo_rule BIGINT,
    created_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tbl_orders (
    id INT PK,
    site_user_id BIGINT,
    yookassa_payment_id VARCHAR,
    total_rub NUMERIC,
    created_at TIMESTAMP,
    paid_at TIMESTAMP
);

ALTER TABLE tbl_subscriptions ADD COLUMN order_id BIGINT;
```

- **Корзина**: копим песни для **пакетной оплаты** одним заказом.
- **Order_id** в `tbl_subscriptions` — общий для группы
  (YooKassa).
- Одиночная мгновенная покупка — `order_id` остаётся NULL.

## NNN 17: `site_user_personal_discount`

```sql
ALTER TABLE tbl_site_users ADD personal_discount_percent INT;
```

- **Постоянная персональная скидка** (админ в webvue3,
  `SiteUsersController.update`).
- Суммируется поверх **любой** акции (НЕ конкурирует с
  `tbl_promo_rules`).

## NNN 18: `author_aliases`

```sql
CREATE TABLE tbl_author_aliases (
    id INT PK,
    author_id BIGINT,
    alias VARCHAR
);
```

- **Псевдонимы автора** для LLM-поиска (см. [author-entity.md](../../domains/catalog/components/author-entity.md)).

## NNN 19: `site_chat_messages`

```sql
CREATE TABLE tbl_site_chat_messages (
    id INT PK,
    site_user_id BIGINT,
    is_from_author BOOLEAN,
    body TEXT,
    is_read BOOLEAN,
    created_at TIMESTAMP
);
```

(См. [sitechatmessage](../../domains/catalog/components/entities-catalog.md#sitechatmessage).)

## NNN 20: `news`

```sql
CREATE TABLE tbl_news (
    id INT PK,
    news_author_id BIGINT,
    date_publicate TIMESTAMP,
    id_telegram VARCHAR,
    id_boosty VARCHAR,
    id_vk VARCHAR,
    text TEXT,
    id_picture BIGINT,
    song_id BIGINT,
    category VARCHAR,        -- 'air' | 'premium' | 'feature'
    publish_at TIMESTAMP,
    is_manual BOOLEAN,
    recordhash VARCHAR(32)
);
```

(См. [news](../../domains/catalog/components/entities-catalog.md#news).)

## NNN 22: `stem_jobs`

```sql
CREATE TABLE tbl_stem_jobs (
    id INT PK,
    site_user_id BIGINT,
    mode VARCHAR,            -- 'DEMUCS2' | 'DEMUCS5'
    status VARCHAR,          -- 'CREATED' | 'PENDING' | 'WAITING' | 'WORKING' | 'DONE' | 'FAILED'
    original_file_name VARCHAR,
    original_ext VARCHAR,
    file_size_bytes BIGINT,
    expires_at TIMESTAMP,
    delete_requested BOOLEAN,
    error_message TEXT
);
```

(См. [stem-job](../../domains/catalog/components/remaining-models.md#stemjob).)

## Связь

- [monetization domain](../../domains/monetization/domain.md) — bounded context.
- [entities-catalog.md](../../domains/catalog/components/entities-catalog.md) — entities.
- [sql-migrations-detailed.md](sql-migrations-detailed.md) — обзор.

## Changelog

- **Pass 458-460** (2026-09-09): Initial. Автор: agent (Karaoke).