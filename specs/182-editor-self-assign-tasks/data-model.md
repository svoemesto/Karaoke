# Data Model: Self-Assign Tasks

**Phase 1 output** — `/speckit.plan`  
**Created**: 2026-08-13

## Entities

### 1. `SiteUser` (расширение)

**File**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SiteUser.kt`  
**Table**: `tbl_site_users`

| Field | Kotlin Type | DB Type | Default | Notes |
|---|---|---|---|---|
| ... existing fields ... | ... | ... | ... | ... |
| `canSelfAssignTasks` | `Boolean` | `BOOLEAN NOT NULL` | `FALSE` | **NEW** |

**Validation rules**:
- `false` по умолчанию (opt-in).
- Поле ортогонально `isEditor`: формально может быть `true` для не-редактора, но UI его прячет (см. `SiteUserEdit.vue`).
- `save()` (через `KaraokeDbTable`) пишет в LOCAL → sync → SERVER автоматически.

**Migration required**:
```sql
-- XX_add_can_self_assign_tasks.sql
ALTER TABLE tbl_site_users ADD COLUMN can_self_assign_tasks BOOLEAN NOT NULL DEFAULT FALSE;

-- Recreate recordhash trigger (по конституции III: новая колонка → пересоздать триггер)
-- Иначе LOCAL и SERVER дадут разные md5 для одной записи и sync сломается.
DROP TRIGGER IF EXISTS tbl_site_users_recordhash ON tbl_site_users;
CREATE OR REPLACE FUNCTION tbl_site_users_recordhash() RETURNS trigger AS $$
-- ... (скопировать тело из существующей миграции + добавить can_self_assign_tasks в hash)
$$ LANGUAGE plpgsql;
CREATE TRIGGER tbl_site_users_recordhash BEFORE INSERT OR UPDATE ON tbl_site_users
  FOR EACH ROW EXECUTE FUNCTION tbl_site_users_recordhash();
```

**Sync impact**: `tbl_site_users` уже в `SyncRegistry.all` через `GenericKaraokeDbTableSyncTarget<SiteUser>`. Новая колонка попадёт в diff через `SiteUser.getDiff()` (auto-reflection).

---

### 2. `SongAssignment` (без изменений схемы)

**File**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SongAssignment.kt`  
**Table**: `tbl_song_assignments`

Существующая сущность, переиспользуется. Self-assign использует тот же конструктор и `KaraokeDbTable.createDbInstance(...)`.

**Поведение**:
- `assigneeId = currentUser.id` (вместо `request assigneeId` в админском `assign`)
- `songId = request.songId`
- `assignedBy = currentUser.id` (а не 0/админ)
- `adminStatus = ADMIN_OPEN` (как в админском `assign`)
- `assignedAt = now()` (default в entity)

**Helper used**:
- `SongAssignment.findExisting(songId, assigneeId, db, ...)` — для идемпотентности (уже взял — 200 OK).
- `SongAssignment.loadBySong(songId, db, ...)` — для проверки «свободна ли песня» (пост-проверка после lock).

**Sync**: уже синхронизируется с `LOCAL_TO_SERVER` направлением (см. `SyncTarget.kt`).

---

### 3. `SongAssignmentBriefDto` (НОВЫЙ)

**File**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/dto/SongAssignmentBriefDto.kt`  
**Purpose**: лёгкое представление задания для стрима `/api/public/zakroma` (не нужен полный `SongAssignmentDto` + draft-инфо).

| Field | Kotlin Type | JSON Name | Notes |
|---|---|---|---|
| `id` | `Long` | `id` | PK |
| `assigneeId` | `Long` | `assigneeId` | для UI-логики «у меня / у другого» |
| `assignedAt` | `Timestamp?` | `assignedAt` | для UI (опционально) |
| `adminStatus` | `String` | `adminStatus` | `'open'` / `'in_progress'` / `'approved'` / `'rejected'` — нужно для фильтрации «свободна» vs «занята» |

**Implementation**:
```kotlin
package com.svoemesto.karaokeapp.dto

import java.sql.Timestamp

data class SongAssignmentBriefDto(
    val id: Long,
    val assigneeId: Long,
    val assignedAt: Timestamp?,
    val adminStatus: String,
)
```

**Note**: `adminStatus` хранится в `tbl_song_assignments.admin_status` (строковое поле, values: `SongAssignmentStatus.ADMIN_OPEN` / `ADMIN_IN_PROGRESS` / `ADMIN_APPROVED` / `ADMIN_REJECTED`).

---

### 4. `ZakromaAlbumSongPublicDto` (расширение)

**File**: `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/ZakromaPublicDto.kt`  
**Purpose**: DTO стрима `/api/public/zakroma`.

**Изменение**: добавляется последнее поле `assignment`:

```kotlin
data class ZakromaAlbumSongPublicDto(
    // ... все 30+ существующих полей ...
    val assignment: SongAssignmentBriefDto? = null,  // NEW: null = свободная песня
)
```

**Default value `null`** — обратная совместимость: фронт, который не знает про новое поле, получит `null` и НЕ покажет кнопку (т.е. не сломается).

**Заполняется** ТОЛЬКО в стриме `/api/public/zakroma` для self-assign-редакторов (логика в `PublicApiController.zakromaStream`).

**Не-залогиненные / обычные редакторы / не-редакторы** получают `assignment = null` — JOIN к `tbl_song_assignments` НЕ выполняется (экономия запросов).

---

### 5. `SiteUserDto` (расширение для admin)

**File**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SiteUserDto.kt`  
**Purpose**: DTO для `webvue3` (admin SPA).

**Изменение**: добавляется поле `canSelfAssignTasks` рядом с `isEditor`:

```kotlin
data class SiteUserDto(
    // ... existing fields ...
    val isEditor: Boolean = false,
    @get:JsonProperty("canSelfAssignTasks")  // явное JSON-имя, без префикса is
    val canSelfAssignTasks: Boolean = false,
    // ...
)
```

**Note**: `isEditor` уже в `SiteUserDto` (admin-вариант, не путать с публичным `SiteUserDto`). Имена `canSelfAssignTasks` (без `is`) — см. AGENTS.md Q&A «Jackson отбрасывает is». Явная `@JsonProperty` для подстраховки.

---

## Relationships

```
┌──────────────────────┐
│  tbl_site_users      │
│  ─────────────────   │
│  id                  │◄─────────┐
│  ...                 │          │
│  is_editor           │          │
│  can_self_assign ✓ NEW│          │
└──────────────────────┘          │
                                  │
┌──────────────────────┐          │
│  tbl_song_assignments│          │
│  ─────────────────   │          │
│  id                  │          │
│  song_id ───────────►│──┐       │
│  assignee_id ───────►│  │       │
│  admin_status        │  │       │
│  assigned_by ───────►│  │       │
│  assigned_at         │  │       │
│  reviewed_at         │  │       │
│  review_comment      │  │       │
└──────────────────────┘  │       │
                          │       │
┌──────────────────────┐  │       │
│  tbl_songs           │  │       │
│  ─────────────────   │  │       │
│  id ◄───────────────│──┘       │
│  song_name           │          │
│  author              │          │
│  ...                 │          │
└──────────────────────┘          │
                                  │
                                  │ (sync LOCAL↔SERVER через recordhash)
                                  │
                        MANUAL NOTES:
                        - песня считается "свободной" если по ней нет ни одной
                          записи в tbl_song_assignments (независимо от assignee_id
                          и admin_status).
                        - approve не освобождает песню (запись остаётся в БД как
                          история). Recall/refuse — освобождает.
```

## State Transitions (SongAssignment)

Прежние, не меняются:
```
(open)         ──► submit     ──► (in_progress)
                 (user)          (user: editedSourceText сохранён)
                               
(open)         ──► recall     ──► (open)
                 (admin)        (отзыв без причины)

(in_progress)  ──► approve    ──► (approved, draft+text применены)
                 (admin)

(in_progress)  ──► reject     ──► (rejected, draft+text применены)
                 (admin)

(refuse)       ──► DELETE (user: tasks/{id}/refuse)
```

**Self-assign** входит в эту машину в состоянии `(open)` — никаких новых переходов.

## Validation Rules

- `canSelfAssignTasks` ∈ `Boolean` (true/false). NULL → трактуется как `false` (migration default).
- `SongAssignment.findExisting(songId, assigneeId, ...)` возвращает `null` если нет задания — это допустимое состояние.
- `tbl_song_assignments` не имеет UNIQUE constraint на `song_id` (только PK на `id`) — отсюда необходимость `SELECT FOR UPDATE` в self-assign.

## Constraints Summary

- Self-assign пишет в `WORKING_DATABASE` (через `KaraokeDbTable.createDbInstance(SongAssignment(...))`).
- Sync наследуется автоматически (нет новых sync-флагов).
- `recordhash` для `tbl_site_users` пересоздаётся в миграции (обязательно).
- `SiteUserDto.canSelfAssignTasks` доступен ТОЛЬКО в admin API `/api/siteusers/digest` и `/api/siteusers/byId` — НЕ в публичном `ProfileDto` (отдельная сущность, не путать).
