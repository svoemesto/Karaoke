# Data Model: Админ-таблицы «Подписки», «История прослушиваний», «Временные ссылки»

**Feature**: 171-admin-subscriptions-history
**Date**: 2026-08-11
**Status**: Phase 1 — data model

> **Важно**: все три таблицы уже существуют в БД. Эта фича **НЕ вводит** новых сущностей, миграций, изменений схемы. Документ описывает, какие колонки и отношения **используются** для админ-таблиц, и какие правила применяются на чтении.

## Entity 1: Subscription (`tbl_subscriptions`)

**Источник**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Subscription.kt`

### Колонки

| Колонка | Тип | Nullable | Описание |
|---|---|---|---|
| `id` | BIGSERIAL | NO | PK |
| `site_user_id` | BIGINT | NO | FK → `tbl_site_users.id` |
| `scope` | VARCHAR | NO | `SONG` (покупка одной песни) / `SITE` (периодическая подписка на сайт) |
| `id_song` | BIGINT | YES | FK → `tbl_songs.id`. NULL для scope=SITE |
| `tariff_id` | BIGINT | YES | FK → `tbl_tariffs.id`. NULL для scope=SONG |
| `period_days` | INT | NO | Период действия (для SITE); для SONG всегда 0 |
| `base_price` | DOUBLE PRECISION | NO | Базовая цена до скидки |
| `discount` | DOUBLE PRECISION | NO | Сумма скидки |
| `final_price` | DOUBLE PRECISION | NO | Итоговая сумма (= base_price - discount) |
| `promo_applied` | VARCHAR | NO | Имя применённого промо (`""` если нет) |
| `status` | VARCHAR | NO | `CREATED` / `PENDING` / `PAID` / `FAILED` / `REFUNDED` / `CANCELED` |
| `yookassa_payment_id` | VARCHAR | NO | ID платежа в ЮKassa (`""` если ещё не создан) |
| `order_id` | VARCHAR | YES | ID заказа «Корзины» (несколько записей одного order_id — одна оплата) |
| `auto_renew` | BOOLEAN | NO | Только для scope=SITE. По умолчанию `true` |
| `yookassa_payment_method_id` | VARCHAR | NO | ID метода оплаты для автопродления |
| `created_at` | TIMESTAMP | NO | Момент создания записи |
| `paid_at` | TIMESTAMP | YES | Момент успешной оплаты |
| `last_update` | TIMESTAMP | YES | Авто (триггер `update_last_updated_*`), НЕ входит в recordhash |

### Отношения (для JOIN в админ-таблице)

| Связь | Направление | Условие | Колонки для отображения |
|---|---|---|---|
| `Subscription → SiteUser` | N:1 | `tbl_subscriptions.site_user_id = tbl_site_users.id` | `tbl_site_users.email`, `tbl_site_users.display_name` |
| `Subscription → Song` (если scope=SONG) | N:1 | `tbl_subscriptions.id_song = tbl_songs.id` | `tbl_songs.song_name` |
| `Subscription → Tariff` (если scope=SITE) | N:1 | `tbl_subscriptions.tariff_id = tbl_tariffs.id` | `tbl_tariffs.name` |

### Состояния (lifecycle)

```
CREATED ──create()──► PENDING ──webhook OK──► PAID ──renew──► PAID (новая запись)
   │                   │                       │
   │                   │                       ├─► REFUNDED
   │                   │                       └─► CANCELED
   └───────────────────┴──────────────────────► FAILED
```

- `CREATED` — запись создана, но платёж ещё не инициирован (например, для одиночной мгновенной покупки).
- `PENDING` — платёж инициирован, ожидается webhook.
- `PAID` — успешная оплата (для SONG = владение, для SITE = продление премиума).
- `FAILED` — webhook вернул ошибку (терминально).
- `REFUNDED` — возврат средств.
- `CANCELED` — пользователь отменил (только для SITE с `auto_renew=true`).

### Правила для админ-таблицы

- **Без редактирования** — все поля read-only.
- **JOIN к `tbl_site_users`** — обязателен (для отображения email/displayName).
- **JOIN к `tbl_songs` / `tbl_tariffs`** — опциональный, по `scope`.
- **Сортировка по умолчанию**: `created_at DESC`.
- **Пагинация**: 25 строк/стр.

---

## Entity 2: ListeningHistory (`tbl_listening_history`)

**Источник**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/ListeningHistory.kt`

### Колонки

| Колонка | Тип | Nullable | Описание |
|---|---|---|---|
| `id` | BIGSERIAL | NO | PK |
| `site_user_id` | BIGINT | NO | FK → `tbl_site_users.id` |
| `song_id` | BIGINT | NO | FK → `tbl_songs.id` |
| `play_count` | BIGINT | NO | Сколько раз пользователь слушал эту песню |
| `last_played_at` | TIMESTAMP | NO | Последнее время прослушивания |
| `last_update` | TIMESTAMP | YES | Авто, НЕ входит в recordhash |

### Уникальность

- `UNIQUE (site_user_id, song_id)` — одна строка на пару (пользователь, песня).
- `upsert()` через `ON CONFLICT (site_user_id, song_id) DO UPDATE SET play_count = ... + 1`.

### Отношения (для JOIN в админ-таблице)

| Связь | Направление | Условие | Колонки для отображения |
|---|---|---|---|
| `ListeningHistory → SiteUser` | N:1 | `tbl_listening_history.site_user_id = tbl_site_users.id` | `tbl_site_users.email`, `tbl_site_users.display_name` |
| `ListeningHistory → Song` | N:1 | `tbl_listening_history.song_id = tbl_songs.id` | `tbl_songs.song_name`, `tbl_songs.song_author`, `tbl_songs.song_album` |

### Правила для админ-таблицы

- **SKIP-фильтр на чтении** (наследуется из публичного `getForUser`):
  ```sql
  AND (s.tags IS NULL OR NOT ('SKIP' = ANY(string_to_array(upper(coalesce(s.tags,'')), ' '))))
  ```
  — запись в `tbl_listening_history` при этом НЕ удаляется, фильтруется только выборка.
- **JOIN к `tbl_songs`** — обязателен для отображения `song_name`/`song_author`/`song_album`.
- **Сортировка по умолчанию**: `last_played_at DESC`.
- **Пагинация**: 500 строк/стр.
- **Footer «показано X из Y»** если Y > X.

### Lifecycle

- Строка создаётся при первом прослушивании.
- При повторном прослушивании той же песни — `upsert()` инкрементирует `play_count` и обновляет `last_played_at`.
- Никаких переходов состояний — это append-only лог.

---

## Entity 3: SongShareLink (`tbl_song_share_links`)

**Источник**: `deploy/karaoke-db/38_song_share_links.sql`, `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/SongShareLinkService.kt`

### Колонки

| Колонка | Тип | Nullable | Описание |
|---|---|---|---|
| `id` | BIGSERIAL | NO | PK |
| `song_id` | BIGINT | NO | FK → `tbl_songs.id` |
| `owner_site_user_id` | BIGINT | NO | FK → `tbl_site_users.id` (владелец ссылки, премиум-пользователь) |
| `secret` | VARCHAR | NO | Секретный токен (UUID-ish, длинный) |
| `created_at` | TIMESTAMP | NO | Момент создания |
| `expires_at` | TIMESTAMP | NO | Момент истечения (задаётся при создании, неизменяемый) |
| `active` | BOOLEAN | NO | `true` = ссылка жива, `false` = отозвана |
| `revoked_at` | TIMESTAMP | YES | Момент отзыва (NULL если `active=true`) |
| `revoke_reason` | VARCHAR | YES | `expired` / `premium_lost` / `song_unavailable` / `admin` (NULL если `active=true`) |
| `active_session_token_hash` | VARCHAR | YES | Хеш токена активной сессии (NULL = никто сейчас не слушает) |
| `active_session_browser_hash` | VARCHAR | YES | Хеш браузера активной сессии |
| `active_session_lease_until` | TIMESTAMP | YES | Момент окончания lease (обновляется heartbeat'ом) |
| `last_update` | TIMESTAMP | YES | Авто, recordhash |

### Отношения

| Связь | Направление | Условие | Колонки для отображения |
|---|---|---|---|
| `SongShareLink → SiteUser` (owner) | N:1 | `tbl_song_share_links.owner_site_user_id = tbl_site_users.id` | `tbl_site_users.email`, `tbl_site_users.display_name` |
| `SongShareLink → Song` | N:1 | `tbl_song_share_links.song_id = tbl_songs.id` | `tbl_songs.song_name` |

### Состояния (lifecycle)

```
active=true, expires_at > now(), lease_until NULL/now()    → "Активна, нет сессии"
active=true, expires_at > now(), lease_until > now()       → "Активна, идёт сессия"
active=true, expires_at < now()                            → "Истекла (sweep отзовёт)"
active=false, revoke_reason='expired'                      → "Отозвана: истекла"
active=false, revoke_reason='premium_lost'                 → "Отозвана: владелец потерял премиум"
active=false, revoke_reason='song_unavailable'             → "Отозвана: песня стала SKIP/будущий publish"
active=false, revoke_reason='admin'                        → "Отозвана: админом вручную"
```

Переходы автоматические (через `ShareLinkSweeper` раз в 60 сек), кроме `admin` (через `POST /api/siteusers/share/links/revoke`).

### Правила для админ-таблицы

- **JOIN к `tbl_site_users`** (owner) — обязателен.
- **JOIN к `tbl_songs`** — обязателен.
- **Действие «Отозвать»**: переиспользует `POST /api/siteusers/share/links/revoke` с `reason='admin'`.
- **Сортировка по умолчанию**: `created_at DESC`.
- **Пагинация**: 25 строк/стр.

---

## Связи между сущностями (ER-summary)

```
┌─────────────────────┐         ┌──────────────────────┐
│ tbl_site_users      │         │ tbl_songs             │
│ (PK: id)            │         │ (PK: id)              │
└─────────────────────┘         └──────────────────────┘
        ▲                                ▲
        │ owner_id                       │ song_id
        │                                │
        ├────────────────┐    ┌───────────┤
        │                │    │           │
┌───────┴──────────┐  ┌──┴────┴────────┐ ┌┴────────────────────┐
│ tbl_subscriptions│  │ tbl_listening_ │ │ tbl_song_share_     │
│ FK: site_user_id │  │ history        │ │ links               │
│ FK: id_song (opt)│  │ FK: song_id    │ │ FK: owner_site_     │
│ FK: tariff_id    │  │ UNIQUE(user,   │ │     user_id         │
│ (opt)            │  │       song)    │ │ FK: song_id         │
└──────────────────┘  └────────────────┘ └─────────────────────┘
                                                       │
                                                       ▼
                                          ┌─────────────────────┐
                                          │ tbl_song_share_     │
                                          │ sessions            │
                                          │ (НЕ в админ-таблице, │
                                          │  отдельная фича)    │
                                          └─────────────────────┘
```

**Нет cross-references** между `tbl_subscriptions` и `tbl_listening_history` — это ортогональные домены (покупки vs поведение).

## Source of Truth (для будущих ревью)

- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Subscription.kt:130` — `TABLE_NAME = "tbl_subscriptions"`.
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/ListeningHistory.kt:66` — `TABLE_NAME = "tbl_listening_history"`.
- `deploy/karaoke-db/38_song_share_links.sql` — DDL `tbl_song_share_links` (полная схема).
- `deploy/karaoke-db/39_song_share_recordhash.sql` — recordhash триггер.
- `deploy/karaoke-db/27_listening_history.sql` — DDL `tbl_listening_history`.
- `deploy/karaoke-db/15_monetization.sql` — DDL `tbl_subscriptions`.
