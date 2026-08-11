# Data Model — Единая трактовка дат share-ссылок

**Feature**: `166-fix-share-link-timezone`
**Source**: `specs/166-fix-share-link-timezone/research.md`

DDL не меняется. Изменения области — только контракт полей, возвращаемых
бэкендом фронтендам, и KDoc-комментарии в коде.

## 1. Таблицы БД (без изменений)

### `tbl_song_share_links`

Колонки, попадающие в область задачи (см. `deploy/karaoke-db/38_song_share_links.sql`):

| Колонка | Тип | Хранение | Назначение |
|---------|-----|----------|------------|
| `expires_at` | `timestamp without time zone NOT NULL` | naive МСК | момент истечения |
| `created_at` | `timestamp without time zone DEFAULT now() NOT NULL` | naive МСК | момент создания |
| `revoked_at` | `timestamp without time zone` NULL | naive МСК | момент отзыва |
| `first_used_at` | `timestamp without time zone` NULL | naive МСК | момент первого использования |
| `last_used_at` | `timestamp without time zone` NULL | naive МСК | момент последнего использования |
| `active_session_lease_until` | `timestamp without time zone` NULL | naive МСК | конец текущего heartbeat-окна |

**Источник правды**: значение в МСК. DDL не меняется. Триггер `recordhash`
(миграция 39) продолжает включать `expires_at::TEXT` и пр. — хэш стабилен
при условии, что записываемые значения не меняются (см. Assumption #3).

### `tbl_song_share_sessions`

| Колонка | Тип | Хранение | Назначение |
|---------|-----|----------|------------|
| `opened_at` | `timestamp without time zone DEFAULT now() NOT NULL` | naive МСК | момент открытия |
| `started_at` | `timestamp without time zone` NULL | naive МСК | момент первого PLAY |
| `last_seen_at` | `timestamp without time zone DEFAULT now() NOT NULL` | naive МСК | момент heartbeat |
| `finished_at` | `timestamp without time zone` NULL | naive МСК | момент завершения |

## 2. Kotlin DTO (back-end)

### `SongShareLinkService.CreateResult`

```kotlin
data class CreateResult(
    val linkId: Long,
    val secret: String,
    val expiresAt: Long,  // ← РЕАЛЬНЫЙ МОМЕНТ (epoch ms), а не "naive_as_UTC"
    val url: String,
)
// Удалены: expiresAtMs, expiresAtLabel
```

### `SongShareLinkService.OwnerLinkView`

```kotlin
data class OwnerLinkView(
    val linkId: Long,
    val songId: Long,
    val active: Boolean,
    val expiresAt: Long,    // ← реальный момент
    val createdAt: Long,    // ← реальный момент
    val revokedAt: Long?,   // ← реальный момент
    val revokeReason: String,
    val firstUsedAt: Long?, // ← реальный момент
    val lastUsedAt: Long?,  // ← реальный момент
    val sessionsTotal: Int,
    val rejectedConcurrent: Int,
)
// Удалены: createdAtMs, createdAtLabel, expiresAtMs, expiresAtLabel,
//          revokedAtMs, revokedAtLabel, firstUsedAtMs, firstUsedAtLabel,
//          lastUsedAtMs, lastUsedAtLabel
```

### `SongShareLinkService.SessionView`

```kotlin
data class SessionView(
    val sessionId: Long,
    val shareLinkId: Long,
    val songId: Long,
    val browserHash: String,
    val ownerSiteUserId: Long,
    val anonId: String,
    val openedAt: Long,    // ← реальный момент (был сдвинутый)
    val startedAt: Long?,  // ← реальный момент (был сдвинутый)
    val lastSeenAt: Long,  // ← реальный момент (был сдвинутый)
    val finishedAt: Long?, // ← реальный момент (был сдвинутый)
    val result: String,
)
// Поля openedAt/startedAt/lastSeenAt/finishedAt:
//   сейчас: extract(epoch from ...) * 1000  (naive → UTC, сдвинуты на +3ч)
//   после:  extract(epoch from ... AT TIME ZONE 'Europe/Moscow') * 1000
//           (реальный момент)
```

### `SongShareLinkService.TryClaimResult`

Без изменений (даты не возвращает).

## 3. Преобразования (правила)

### 3.1 Чтение (server → DTO)

```kotlin
// Было:
"extract(epoch from expires_at) * 1000"
val expiresAt = rs.getLong("expires_ms")  // 1786442256000 (сдвинутый на +3ч)

// Стало:
"extract(epoch from expires_at AT TIME ZONE 'Europe/Moscow') * 1000"
val expiresAt = rs.getLong("expires_at")  // 1786431456000 (реальный момент)
```

То же для `created_at`, `revoked_at`, `first_used_at`, `last_used_at`,
`opened_at`, `started_at`, `last_seen_at`, `finished_at`.

### 3.2 Запись (DTO → DB)

```kotlin
// Было (зависит от JVM TZ):
ps.setTimestamp(col, Timestamp(epochMs))

// Стало (TZ-устойчиво):
val mskt = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), MOSCOW_ZONE)
ps.setObject(col, mskt, Types.TIMESTAMP)
```

Применяется в `createLink` (один `setTimestamp`). На проде JVM TZ уже
`Europe/Moscow` (Dockerfile `ENV TZ="Europe/Moscow"`), но это не должно быть
неявной зависимостью.

### 3.3 Удаление `formatMskLabel`

После удаления `*Label` полей эта функция становится не нужна. Удаляется
целиком вместе с комментарием — больше нечего форматировать на сервере.

### 3.4 Помощник `toMskLocalDateTime`

Нужен на сервере только в одном месте — запись. Утилитарная функция
(`SongShareLinkService.kt` или отдельный `DateTimeUtil.kt`):

```kotlin
private val MOSCOW_ZONE: ZoneId = ZoneId.of("Europe/Moscow")

fun toMskLocalDateTime(epochMs: Long): LocalDateTime =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), MOSCOW_ZONE)
```

## 4. Frontend API (consumers)

### `karaoke-public`

- `composables/useShareLink.js` — транспорт, без изменений (парсит body).
- `views/ShareView.vue` — `expiresAt.value = body.expiresAt` (реальный момент);
  `isExpired` = `expiresAt <= Date.now()`; форматирование `expiresAt` через
  `new Date(expiresAt).toLocaleString('ru-RU', { day, month, year, hour, minute })`
  (пояс устройства).
- `components/ShareLinkModal.vue` — `expiresLabel` через
  `new Date(link.expiresAt).toLocaleString('ru-RU', { ... })`; `isExpired`
  сравнивает `link.expiresAt` (реальный момент) с `Date.now()`.

### `webvue3`

- `components/SiteUsers/UserShareLinksModal.vue` — `formatDate` через
  `new Date(ts).toLocaleString('ru-RU')` (пояс устройства); прочерк для
  пустой даты; `createdAt`/`expiresAt`/`revokedAt`/`firstUsedAt`/`lastUsedAt`/
  `openedAt`/`finishedAt` берутся напрямую (все — реальные моменты).

## 5. Инварианты

| Инвариант | Где проверять |
|-----------|---------------|
| `expiresAt` (Long) в DTO = реальный момент времени | API / `OwnerLinkView` / `CreateResult` |
| `expiresAt` интерпретируется JS-кодом как `Date(epochMs)` (UTC), `toLocaleString` показывает в поясе устройства | `ShareLinkModal.vue`, `ShareView.vue`, `UserShareLinksModal.vue` |
| Никаких `+ 3*3600*1000` / `- 3*3600*1000` в коде фичи | grep `-3.*3600`, `+3.*3600`, `formatMskLabel` по всему репо после правки |
| `formatMskLabel` функции нет в коде | grep `formatMskLabel` |
| Чтение и запись идут через `Europe/Moscow` (явно), не через `ZoneId.systemDefault()` | grep `systemDefault\|SystemDefault` в `SongShareLinkService.kt` |
| `recordhash` стабилен | diff миграции 39 — отсутствует; `recordhash` функция не редактируется |

## 6. State transitions

`SongShareLinkService.CreateResult` — состояние не имеет transitions; это
DTO, создаётся в `createLink` и отдаётся в HTTP-ответ.

`OwnerLinkView`, `SessionView` — аналогично, immutable DTO, обновляются
только при повторном чтении из БД.

Изменение DTO **не влияет** на состояние сущностей в БД.
