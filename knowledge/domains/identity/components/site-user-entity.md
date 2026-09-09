# Component: SiteUser (детальный)

> **Домен**: [identity](../domain.md)
> **Компонента**: детальное описание `SiteUser` entity (пользователь
> ПУБЛИЧНОГО сайта, **НЕ** admin).

## Файл

`karaoke-app/.../model/SiteUser.kt`

## Назначение

**Пользователь публичного сайта** (НЕ путать с admin-логинами
webvue3). `tbl_site_users` живёт **на проде** (`Connection.remote()`,
см. [store-site-users.md](../../../system/frontend/store-site-users.md)).

## Поля (по `@KaraokeDbTableField`)

| Колонка | Тип | Описание |
|---|---|---|
| `id` | BIGINT PK | Auto-generated |
| `email` | VARCHAR NOT NULL | Email (логин) |
| `password_hash` | VARCHAR NOT NULL | bcrypt-хеш пароля (**НЕ передаётся** в API/DTO) |
| `display_name` | VARCHAR | Отображаемое имя |
| `sponsr_uid` | VARCHAR | ID на Sponsr (для OAuth) |
| `id_vk`, `id_telegram` | VARCHAR | Внешние OAuth ID (для VK ID и TG) |
| `is_premium` | BOOLEAN | Активен ли premium |
| `is_permanent_premium` | BOOLEAN | Бессрочный premium (без auto-renew) |
| `sponsr_premium_until` | TIMESTAMP | Premium через Sponsr до даты |
| `site_premium_until` | TIMESTAMP | Premium через site (см. `Subscription`) до даты |
| `welcome_message_sent` | BOOLEAN | Отправлено ли приветственное сообщение |
| `personal_discount_percent` | INT | Персональная скидка (см. migration 17) |
| `max_favorites` | INT | Лимит «избранного» (например, 1000) |
| `max_playlists` | INT | Лимит плейлистов |
| `max_playlist_items` | INT | Лимит позиций в плейлисте |
| `is_editor` | BOOLEAN | Может ли работать как редактор разметки |
| `can_self_assign_tasks` | BOOLEAN | Может ли сам себе назначать задания |
| `can_work_with_skipped` | BOOLEAN | Может ли работать с SKIP-песнями |
| `is_banned` | BOOLEAN | Забанен ли |
| `ban_reason` | TEXT | Причина бана |
| `last_login_at` | TIMESTAMP | Последний логин |
| `created_at` | TIMESTAMP | Создан |

## Роли и capabilities

- `is_admin` (отдельный `users` table, **не** SiteUser).
- `is_editor` — может быть редактором разметки.
- `can_self_assign_tasks` — может сам себе назначать.
- `can_work_with_skipped` — может работать с SKIP-песнями.

## Sync

Триггер `update_tbl_site_users_recordhash` поддерживает `recordhash`
для two-DB sync. Синхронизируется через `SyncTarget<SiteUser>`.

## DTO

`SiteUserDto` — **без `password_hash`** (см. CLAUDE.md «DTO без is*-префикса»
+ безопасность). Передаёт только безопасные поля.

## Hot paths

- **`/api/siteusers/list`** — admin список.
- **`/api/siteusers/getById`** — детали.
- **`/api/siteusers/update`** — редактирование.
- **`/api/siteusers/{id}/subscriptions`** — подписки.
- **`/api/public/auth/me`** — текущий юзер (см. [composable-use-auth.md](../../../system/frontend/composable-use-auth.md)).

## Связь

- [identity domain](../domain.md) — bounded context.
- [composable-use-auth.md](../../../system/frontend/composable-use-auth.md) — auth.
- [store-site-users.md](../../../system/frontend/store-site-users.md) — UI.
- [monetization domain](../../monetization/domain.md) — `Subscription`, `PriceTariff`.

## Известные TODO

- [ ] **Лимиты** — точные значения `max_*` (Pass 343+).
- [ ] **`is_admin`** — отдельная table `users` (Pass 343+).

## Changelog

- **Pass 428** (2026-09-09): Initial. Автор: agent (Karaoke).