# Component: SiteUser (детальный)

> **Домен**: [identity](../domain.md)
> **Компонента**: детальное описание `SiteUser` entity (пользователь
> ПУБЛИЧНОГО сайта, **НЕ** admin).


## Ответственность | Responsibility


детальное описание `SiteUser` entity (пользователь ПУБЛИЧНОГО сайта, **НЕ** admin).

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
| `email` | VARCHAR(255) NOT NULL | Email (логин); UNIQUE по `LOWER(email)` |
| `password_hash` | VARCHAR(255) NOT NULL | bcrypt-хеш пароля (**НЕ передаётся** в API/DTO) |
| `display_name` | VARCHAR(255) | Отображаемое имя |
| `sponsr_uid` | VARCHAR(64) | ID на Sponsr (для OAuth) |
| `is_premium` | BOOLEAN | Активен ли premium |
| `is_permanent_premium` | BOOLEAN | Бессрочный premium (без auto-renew) |
| `sponsr_premium_until` | TIMESTAMP (nullable) | Premium через Sponsr до даты |
| `site_premium_until` | TIMESTAMP (nullable) | Premium через site (см. `Subscription`) до даты |
| `welcome_message_sent` | BOOLEAN | Отправлено ли приветственное сообщение |
| `personal_discount_percent` | NUMERIC(5,2) | Персональная скидка (см. migration 17); в Kotlin — `Double` |
| `max_favorites` | INT | Лимит «избранного» (например, 1000) |
| `max_playlists` | INT | Лимит плейлистов |
| `max_playlist_items` | INT | Лимит позиций в плейлисте |
| `is_editor` | BOOLEAN | Может ли работать как редактор разметки |
| `can_self_assign_tasks` | BOOLEAN | Может ли сам себе назначать задания |
| `can_work_with_skipped` | BOOLEAN | Может ли работать с SKIP-песнями |
| `is_banned` | BOOLEAN | Забанен ли |
| `ban_reason` | VARCHAR(1024) | Причина бана |
| `last_login_at` | TIMESTAMP | Последний логин |
| `created_at` | TIMESTAMP | Создан |

**NB**: OAuth-полей `id_vk` / `id_telegram` в `SiteUser.kt` нет —
внешний ID хранится только в `sponsr_uid` (KDoc класса упоминает
`idVk`/`idTelegram`, но `@KaraokeDbTableField` для них отсутствует).

## Интерфейсы и Контракты | Interfaces and Contracts

Конструктор: `SiteUser(database = WORKING_DATABASE, storageService = KSS_APP, storageApiClient = SAC_APP)`.

### Instance-методы

| Метод | Контракт |
|---|---|
| `isEffectivePremium: Boolean` | Геттер (не кэшируется): `isPremium OR isPermanentPremium OR sponsrPremiumUntil > now() OR sitePremiumUntil > now()`. |
| `sendWelcomePremiumMessageIfNeeded()` | No-op, если `welcomeMessageSent` или пользователь не премиум; иначе создаёт `SiteChatMessage` (`isFromAuthor = true`, `WELCOME_PREMIUM_MESSAGE`), ставит `welcomeMessageSent = true` и `save()`. |
| `checkPassword(rawPassword, passwordEncoder): Boolean` | `passwordEncoder.matches(rawPassword, passwordHash)`. |
| `setPassword(rawPassword, passwordEncoder)` | `passwordHash = passwordEncoder.encode(rawPassword)`. |
| `toDTO(): SiteUserDto` | Безопасное представление без `password_hash`. |
| `compareTo(other: SiteUser): Int` | По `email`. |

### Companion-API

| Метод | Контракт |
|---|---|
| `TABLE_NAME` = `"tbl_site_users"`; `WELCOME_PREMIUM_MESSAGE` | Константы. |
| `loadList(whereArgs, limit, offset, database, storageService, storageApiClient)` | Фильтры `id`, `email`, `displayName`, `sponsrUid`, `isPremium`, `isPermanentPremium`, `isEffectivePremium`, `isBanned`, `isEditor`. |
| `getSiteUserById(id, database, ...)` / `getSiteUserByEmail(email, database, ...)` | Точечная загрузка; email — `LOWER(email) = LOWER(...)`. |
| `createNewSiteUser(email, rawPassword, displayName, database, passwordEncoder, ...): SiteUser?` | `null`, если email уже занят; иначе `createdAt`/`lastLoginAt` = now и `createDbInstance`. |
| `deleteSiteUser(id, database): Boolean` | DELETE по id. |
| `searchByTerm(term, limit, database, ...): List<SiteUser>` | Поиск по email ИЛИ display_name (raw-SELECT id → batch `loadByIds`). |
| `loadSitePremiumExpiringBefore(before, database, ...)` | `site_premium_until IS NOT NULL AND site_premium_until < before` — для `SubscriptionRenewalScheduler` (`karaoke-web`). |

### `SiteUserDto`

`karaoke-app/.../model/SiteUserDto.kt` — **без `password_hash`**.
Содержит `isEffectivePremium`, `sponsrPremiumUntil`/`sitePremiumUntil`
(`String?`), `personalDiscountPercent: Double`, флаги ролей/бана,
лимиты `max*`, `createdAt`/`lastLoginAt`, `welcomeMessageSent`.
Boolean-поля `canSelfAssignTasks` / `canWorkWithSkipped` помечены
`@get:JsonProperty` (Jackson отбрасывает `is`-префикс).
Реализует `KaraokeDbTableDto`: `fromDto(database)` (только профильные
поля, `passwordHash` сюда никогда не попадает),
`validationErrors()` (email содержит `@` и длина ≥ 5), `isValid()`,
`compareTo` по email.

## Логика и Алгоритмы | Logic and Algorithms

### `isEffectivePremium` — вычисляемый на лету

Премиум нигде не хранится одной колонкой: геттер проверяет 4 условия
(два вечных флага + две даты против `now()`), поэтому истечение срока
не требует отдельного планировщика. Эта же логика зеркалится в SQL
внутри `getWhereList` (фильтр `isEffectivePremium`), чтобы список и
entity не расходились:

```sql
is_premium = true OR is_permanent_premium = true
  OR (sponsr_premium_until IS NOT NULL AND sponsr_premium_until > now())
  OR (site_premium_until   IS NOT NULL AND site_premium_until   > now())
```

### Приветственное сообщение — один раз на первый премиум

`sendWelcomePremiumMessageIfNeeded()` — единая точка правды вместо
дублирования проверки «первый ли это премиум» в каждом вызывающем
месте (Sponsr-синхронизация, оплата на сайте, акционная подписка).
Порядок: (1) `welcomeMessageSent` → выход; (2) `!isEffectivePremium` →
выход (например, SCOPE_SONG не даёт `isEffectivePremium`);
(3) создание сообщения в `tbl_site_chat_messages` → флаг → `save()`.
Без флага повторные вебхуки/скользящее окно Sponsr слали бы сообщение
заново при каждом продлении.

### `searchByTerm` — два шага

1. Raw-`SELECT id FROM tbl_site_users WHERE LOWER(email) LIKE ?
   OR LOWER(display_name) LIKE ? ORDER BY id LIMIT ?` (терм
   `trim().lowercase()`; пустой терм → `emptyList()`).
2. Batch-догрузка сущностей `KaraokeDbTable.loadByIds` по найденным id
   (паттерн `Author.resolveByTerm`) — используется для «Начать чат» в
   webvue3 (`ChatController.searchUsers`).

### Загрузка и sync

`KaraokeDbTable.loadList` строит `whereList` из `Map<String, String>`
(`getWhereList`) — там же экранирование `'` и OR-выражение для
`isEffectivePremium`. Триггер `update_tbl_site_users_recordhash`
(migration 06 + 14 + 17) поддерживает `recordhash` по всем
синхронизируемым колонкам; сущность синхронизируется через
`SyncTarget<SiteUser>`.

## Роли и capabilities

- `is_admin` (отдельный `users` table, **не** SiteUser).
- `is_editor` — может быть редактором разметки.
- `can_self_assign_tasks` — может сам себе назначать.
- `can_work_with_skipped` — может работать с SKIP-песнями.

## Hot paths

- **`/api/siteusers/list`** — admin список.
- **`/api/siteusers/getById`** — детали.
- **`/api/siteusers/update`** — редактирование.
- **`/api/siteusers/{id}/subscriptions`** — подписки.
- **`/api/public/auth/me`** — текущий юзер (см. [composable-use-auth.md](../../../system/frontend/composable-use-auth.md)).

## Зависимости | Dependencies

- [identity domain](../domain.md) — bounded context.
- [composable-use-auth.md](../../../system/frontend/composable-use-auth.md) — auth.
- [store-site-users.md](../../../system/frontend/store-site-users.md) — UI.
- [monetization domain](../../monetization/domain.md) — `Subscription`, `PriceTariff`.

## Известные TODO

- [ ] **Лимиты** — точные значения `max_*` (Pass 343+).
- [ ] **`is_admin`** — отдельная table `users` (Pass 343+).

## Changelog

- **Pass 486** (2026-09-27, spec `486-knowledge-domains-others`): секции приведены к шаблону компонента. Автор: agent (Karaoke).
- **Pass 428** (2026-09-09): Initial. Автор: agent (Karaoke).