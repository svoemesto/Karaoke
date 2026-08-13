# API Contracts: Self-Assign Tasks

**Phase 1 output** — `/speckit.plan`  
**Created**: 2026-08-13

Все endpoints — POST unless specified. Все responses — JSON. Auth — session cookie (после login через `/api/public/auth/login`).

## C1. POST `/api/siteusers/update` (расширение)

**Назначение**: включить/выключить флаг `canSelfAssignTasks` в `SiteUserEdit` (webvue3).  
**Auth**: admin (нет проверки ролей в текущей реализации — переиспользуем существующий паттерн).  
**Existing**: уже принимает `isEditor`, `displayName`, `sponsrUid`, `isPremium`, `isPermanentPremium`, `maxFavorites`, `maxPlaylists`, `maxPlaylistItems`, `personalDiscountPercent`, `sponsrPremiumUntil`, `sitePremiumUntil`, `welcomeMessageSent`, `createdAt`, `lastLoginAt`.

**New request param**:
| Field | Type | Required | Notes |
|---|---|---|---|
| `canSelfAssignTasks` | `Boolean` | optional | Если не передан — поле НЕ меняется. `true`/`false` — установить. |

**Response** (как сейчас):
```json
{
  "ok": true,
  "user": { ... full SiteUserDto with canSelfAssignTasks ... }
}
```

**Errors**:
- `400` `bad_request` — некорректный `id` или невалидное значение параметра.
- `404` `user_not_found` — пользователь не найден.

## C2. POST `/api/public/songeditor/assign-self` (NEW)

**Назначение**: редактор с флагом `canSelfAssignTasks=true` берёт себе свободную песню.  
**Auth**: залогиненный редактор (cookie session, `SiteAuthInterceptor`).  
**Path**: в `PublicSongEditorController` (karaoke-web).

**Request**:
| Field | Type | Required | Notes |
|---|---|---|---|
| `songId` | `Long` | yes | ID песни |

**Response 200 OK** (created):
```json
{
  "ok": true,
  "id": 12345,
  "songId": 67890,
  "assigneeId": 42,
  "adminStatus": "open",
  "assignedAt": "2026-08-13T10:00:00Z"
}
```

**Response 200 OK** (idempotent — повторный клик):
```json
{
  "ok": true,
  "id": 12345,  // existing assignment.id
  "idempotent": true,
  "songId": 67890,
  "assigneeId": 42,
  "adminStatus": "open",
  "assignedAt": "2026-08-13T10:00:00Z"
}
```

**Errors**:
| HTTP | error code | Когда |
|---|---|---|
| 400 | `bad_request` | `songId` невалиден |
| 403 | `forbidden_not_editor` | `!user.isEditor` |
| 403 | `forbidden_not_self_assign_editor` | `isEditor && !canSelfAssignTasks` |
| 404 | `song_not_found` | `songId` не существует |
| 409 | `song_already_taken` | по песне уже есть задание от ДРУГОГО `assigneeId` |

**Implementation notes**:
- В одной `KaraokeConnection.getConnection()` транзакции:
  1. `connection.setAutoCommit(false)`
  2. `SELECT id FROM tbl_song_assignments WHERE song_id = ? FOR UPDATE` (row lock)
  3. Если строка есть → читаем `assignee_id`:
     - Если `assignee_id == user.id` → отдаём `{ok:true, idempotent:true, id: <existing.id>}` и `commit`.
     - Иначе → `409 song_already_taken`, `rollback`.
  4. Если строки нет → INSERT в `tbl_song_assignments`, `commit`.
- Черновик (`tbl_song_assignment_drafts`) НЕ создаётся (см. FR-009).
- `assignedBy = user.id` (не от админа).

## C3. GET `/api/public/zakroma/stream` (расширение)

**Назначение**: NDJSON-стрим песен по автору. Расширен полем `assignment` в payload для self-assign-редакторов.  
**Auth**: публичный (не требует залогиненности), но `assignment` показывается только залогиненным редакторам с флагом.

**Request** (без изменений):
| Field | Type | Required | Notes |
|---|---|---|---|
| `author` | `String` | yes | имя автора |
| `expectedCount` | `Long` | optional | кеш-подсказка с тайла |
| `anonId` | `String` | optional | метрика |
| `referrer` | `String` | optional | метрика |

**Response** (NDJSON, расширение в `type:"song"`):
```json
{
  "type": "song",
  "album": { ... ZakromaAlbumMetaPublicDto ... },
  "song": {
    "id": 67890,
    "track": 1,
    "songName": "...",
    "onAir": false,
    ... 30+ existing fields ...,
    "assignment": {  // NEW: только для self-assign-редакторов
      "id": 12345,
      "assigneeId": 42,
      "assignedAt": "2026-08-13T10:00:00Z",
      "adminStatus": "open"
    }
  }
}
```

**Для всех остальных пользователей** поле `assignment` отсутствует в JSON/`null` (опускается через `if (isSelfAssignEditor) ...`).

**Implementation notes**:
- В `zakromaStream` определяем `isSelfAssignEditor = siteUserResolver.resolve(request)?.let { it.isEditor && it.canSelfAssignTasks } ?: false`.
- Если `false` → стрим отправляется без JOIN'а к `tbl_song_assignments` (экономия запросов).
- Если `true` → перед началом стрима выполняем ОДИН батч-запрос:
  ```sql
  SELECT id, song_id, assignee_id, assigned_at, admin_status
  FROM tbl_song_assignments
  WHERE song_id IN (<все song_id из zakroma>);
  ```
  Используем существующий `SongAssignment.composeStatusesForSongIds()` (уже есть, может быть simplify до `loadBySongIds` + без draft).
- При сериализации каждого `song` если `isSelfAssignEditor` И есть assignment для `song.id` → добавляем поле.

## C4. (не меняется) GET `/api/songeditor/assign` — admin path

**Назначение**: админ назначает редактора на песню (existing).  
**Status**: без изменений. Self-assign использует другой endpoint.

## C5. (не меняется) POST `/api/public/songeditor/tasks/{id}/refuse`

**Назначение**: редактор отказывается от своего активного задания.  
**Status**: без изменений. После refuse песня снова становится «свободной» (нет ни одного назначения), кнопка «Взять в работу» снова появляется.

## C6. (не меняется) GET `/api/public/songeditor/tasks`

**Назначение**: список заданий текущего пользователя.  
**Status**: self-assign задания автоматически появятся здесь (как и админские).

## Constraints / Invariants

- Все response-body ошибки используют `{"error": "<code>"}` (конвенция проекта).
- `400` используется ТОЛЬКО для валидации (например, невалидный `songId`).
- `403` — для прав (нет роли редактора / нет флага).
- `404` — entity not found (песня) или role check fail (существующая конвенция `notFound()` в `PublicSongEditorController`).
- `409` — конфликт (песня уже занята).
- `500` — только для неожиданных ошибок (НЕ для бизнес-логики).
- Все endpoint'ы логируются через `println("[self-assign] ...")` (как в админском `/assign`).

## Error Code Catalog

| Code | HTTP | Asana |
|---|---|---|
| `song_not_found` | 404 | `Song.loadFromDbById == null` |
| `user_not_found` | 404 | (admin assign) |
| `forbidden_not_editor` | 403 | `!user.isEditor` |
| `forbidden_not_self_assign_editor` | 403 | `isEditor && !canSelfAssignTasks` |
| `song_already_taken` | 409 | чужая запись в `tbl_song_assignments` для этой песни |
| `forbidden` | 403 | (generic в `SongsTable`) |
| `markers_exist` | 200 | (admin assign) — нужна явная очистка маркеров |
| `create_failed` | 500 | `KaraokeDbTable.createDbInstance` вернул null |
| `bad_request` | 400 | валидация параметра |
| `not_found` | 404 | generic (default `notFound()`) |

## Backward Compatibility

- Все новые поля — `nullable`/`default null` → старый клиент работает как раньше.
- Старое поле `assignment` добавляется ТОЛЬКО в публичный стрим, не в любые существующие эндпоинты.
- Старый параметр `canSelfAssignTasks` опционален в `/api/siteusers/update` → старый клиент, не передающий параметр, не ломается.
- `SongAssignmentBriefDto` — новый DTO, не заменяет `SongAssignmentDto` (тот продолжает использоваться где раньше).
