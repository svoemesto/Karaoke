# Data Model: 293 — Галочка «Работа со SKIP-авторами и песнями» в настройках пользователя

**Status**: Phase 1 design | **Date**: 2026-09-02

## Сущности

### 1. `tbl_site_users` (изменение)

**Тип изменения**: additive — добавление одной колонки.

| Колонка | Тип | Null | Default | Описание |
|---------|-----|------|---------|----------|
| `can_work_with_skipped` | BOOLEAN | NOT NULL | `false` | Флаг: пользователю разрешено видеть и работать с контентом, скрытым механизмами SKIP (`tbl_authors.skip = TRUE` или тег `SKIP` в `tbl_songs.tags`). Не роль (как `is_editor`); это право поверх роли, по аналогии с `can_self_assign_tasks`. |

**Существующие колонки** (для контекста, без изменений):
- `id` BIGSERIAL PRIMARY KEY
- `email` TEXT
- `password_hash` TEXT
- `display_name` TEXT
- `is_editor` BOOLEAN — роль редактора
- `can_self_assign_tasks` BOOLEAN — право брать свободные задания (паттерн-аналог)
- `is_admin` (нет — админа определяет `is_admin` поле, не используется; в коде проверка через отдельную таблицу/конфиг, см. заметку ниже)
- ... (см. `SiteUser.kt` и миграцию V40 для полного списка)

**Заметка про `is_admin`**: в `tbl_site_users` нет колонки `is_admin` —
админ-доступ проверяется по наличию email в специальном списке
(см. `SecurityConfig` и `AdminController`). Это значит, что
авто-выдача галочки админам (см. clarify Q3 — отвергнута) была бы
сложнее, чем `is_admin OR can_work_with_skipped`. Подтверждает выбор
Q3: явная выдача через webvue3, без OR-логики.

**Constraints / индексы**:
- Первичный ключ уже включает `id` — никаких новых индексов не нужно.
- `can_work_with_skipped` — неиндексированная колонка (для 18k+
  пользователей даже full scan мгновенен, но всё равно редко читаемая —
  только при авторизации).

**Triggers**:
- **ОБЯЗАТЕЛЬНО пересоздать** `update_tbl_site_users_recordhash`
  (Constitution §III). Без этого sync LOCAL↔SERVER сломается.
  Шаблон — миграция V40 (`40_site_user_can_self_assign_tasks.sql`).

**Lifecycle**:
- Создаётся миграцией V45 одновременно с миграцией DDL.
- Backfill не требуется (DEFAULT FALSE).
- Удаление (если потребуется откатить фичу) — `ALTER TABLE ... DROP
  COLUMN can_work_with_skipped` + пересоздание триггера recordhash.

---

### 2. `SiteUser` Kotlin-модель (изменение)

**Файл**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SiteUser.kt`

| Поле Kotlin | Тип | Default | Аннотация | KDoc |
|-------------|-----|---------|-----------|------|
| `canWorkWithSkipped` | `Boolean` | `false` | `@KaraokeDbTableField(name = "can_work_with_skipped")` | «См. спеку 293: разрешение на работу с SKIP-авторами/песнями. Не роль, а право поверх `isEditor`. См. `specs/293-skip-author-toggle/spec.md` (FR-002).» |

**Изменения в `toDTO()`**:
```kotlin
override fun toDTO(): SiteUserDto =
    SiteUserDto(
        // ... существующие поля ...
        canWorkWithSkipped = canWorkWithSkipped,
        // ... остальные поля ...
    )
```

---

### 3. `SiteUserDto` data class (изменение)

**Файл**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SiteUserDto.kt`

| Поле DTO | Тип | Default | Jackson | KDoc |
|----------|-----|---------|---------|------|
| `canWorkWithSkipped` | `Boolean` | `false` | `@get:JsonProperty("canWorkWithSkipped")` | «Self-skip access: пользователь с флагом видит SKIP-авторов и SKIP-песни в публичных списках. Явный `@JsonProperty` для единообразия с соседними boolean-полями (см. `canSelfAssignTasks`). См. спеку 293 (FR-003).» |

**Изменения в `fromDto()`** (для админских PUT/POST из webvue3):
```kotlin
override fun fromDto(database: KaraokeConnection): SiteUser {
    val entity = SiteUser(database = database)
    // ... существующие поля ...
    entity.canWorkWithSkipped = canWorkWithSkipped
    // ... остальные поля ...
    return entity
}
```

**Изменения в `validationErrors()`**: нет (новое поле не требует валидации).

---

### 4. Никаких новых сущностей

Фича не вводит новых таблиц, индексов, типов. Все изменения —
additive patches в существующих `tbl_site_users`, `SiteUser`,
`SiteUserDto`. Никаких JOIN на новые таблицы, никаких миграций данных.

---

## Связи с другими сущностями (без изменений схемы)

- `tbl_site_users.can_work_with_skipped` влияет на runtime-поведение
  (фильтрация в SQL/Kotlin), но не создаёт FK-связей.
- `tbl_authors.skip` (Boolean, существующая) — с ней сравнивается
  runtime через `Song.loadListAuthors(withSkiped = ...)`.
- `tbl_songs.tags` (TEXT, существующая) — runtime-проверка через
  `tags.contains("SKIP")` (split по пробелам, uppercase).

---

## State Transitions

`can_work_with_skipped` — бинарный флаг, нет state machine.

| Состояние | Событие | Результат |
|-----------|---------|-----------|
| `false` | Админ в webvue3 ставит галочку → `PUT /api/webvue3/site-users/{id}` | `true` в БД + следующий запрос пользователя использует новое значение |
| `true` | Админ снимает галочку | `false` в БД + следующий запрос использует прежнее поведение |
| `false` (не-редактор) | Админ выставляет галочку не-редактору | `true` — пользователь видит SKIP (допустимо по A-002) |

---

## Миграция V45 (скелет SQL)

```sql
-- Флаг "Может работать со SKIP-авторами и песнями" (canWorkWithSkipped): см. спеку
-- specs/293-skip-author-toggle. Колонка tbl_site_users, входит в recordhash —
-- иначе изменение флага не проедет по LOCAL<->SERVER sync (Constitution §III).
--
-- Apply (см. шапку V40 для шаблона команд):
--   локально:  docker exec -i karaoke-db psql -U postgres -d karaoke < deploy/karaoke-db/45_site_user_can_work_with_skipped.sql
--   прод:      ssh root@${PROD_HOST} 'docker exec -i karaoke-db psql -U postgres -d karaoke < /root/Karaoke/deploy/karaoke-db/45_site_user_can_work_with_skipped.sql'
--
-- Идемпотентен: ADD COLUMN IF NOT EXISTS + CREATE OR REPLACE FUNCTION.

ALTER TABLE public.tbl_site_users
    ADD COLUMN IF NOT EXISTS can_work_with_skipped boolean DEFAULT false NOT NULL;

CREATE OR REPLACE FUNCTION public.update_tbl_site_users_recordhash() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    NEW.recordhash = md5(
        COALESCE(NEW.id::TEXT, '') ||
        COALESCE(NEW.email, '') ||
        COALESCE(NEW.password_hash, '') ||
        COALESCE(NEW.display_name, '') ||
        COALESCE(NEW.sponsr_uid, '') ||
        COALESCE(NEW.is_premium::TEXT, '') ||
        COALESCE(NEW.is_permanent_premium::TEXT, '') ||
        COALESCE(NEW.is_banned::TEXT, '') ||
        COALESCE(NEW.ban_reason, '') ||
        COALESCE(NEW.max_favorites::TEXT, '') ||
        COALESCE(NEW.max_playlists::TEXT, '') ||
        COALESCE(NEW.max_playlist_items::TEXT, '') ||
        COALESCE(NEW.is_editor::TEXT, '') ||
        COALESCE(NEW.sponsr_premium_until::TEXT, '') ||
        COALESCE(NEW.site_premium_until::TEXT, '') ||
        COALESCE(NEW.welcome_message_sent::TEXT, '') ||
        COALESCE(NEW.can_self_assign_tasks::TEXT, '') ||
        COALESCE(NEW.can_work_with_skipped::TEXT, '')  -- NEW
    );
RETURN NEW;
END;
$$;

-- Пересчитать md5 для существующих строк (новая колонка default = false, но md5-функция
-- поменялась — без UPDATE 'запись на диске' не совпадёт с тем, что триггер сгенерит
-- при следующем изменении, и diff'ы в sync пойдут неверные).
UPDATE public.tbl_site_users SET recordhash = md5(
    COALESCE(id::TEXT, '') ||
    COALESCE(email, '') ||
    COALESCE(password_hash, '') ||
    COALESCE(display_name, '') ||
    COALESCE(sponsr_uid, '') ||
    COALESCE(is_premium::TEXT, '') ||
    COALESCE(is_permanent_premium::TEXT, '') ||
    COALESCE(is_banned::TEXT, '') ||
    COALESCE(ban_reason, '') ||
    COALESCE(max_favorites::TEXT, '') ||
    COALESCE(max_playlists::TEXT, '') ||
    COALESCE(max_playlist_items::TEXT, '') ||
    COALESCE(is_editor::TEXT, '') ||
    COALESCE(sponsr_premium_until::TEXT, '') ||
    COALESCE(site_premium_until::TEXT, '') ||
    COALESCE(welcome_message_sent::TEXT, '') ||
    COALESCE(can_self_assign_tasks::TEXT, '') ||
    COALESCE(can_work_with_skipped::TEXT, '')  -- NEW
) WHERE id > 0;
```

**Применяется**:
1. На LOCAL БД через `docker exec`.
2. На SERVER БД через `ssh + docker exec`.
3. Проверка: `SELECT column_name FROM information_schema.columns WHERE table_name = 'tbl_site_users' AND column_name = 'can_work_with_skipped'` → 1 row.

---

## Валидация

- `SiteUserDto.validationErrors()` — НЕ валидирует `canWorkWithSkipped`
  (Boolean, не может быть невалидным).
- На UI — галочка `<input type="checkbox">` (Vue), по умолчанию
  unchecked = false.
- На API — `Boolean?` параметр в `SiteUsersController.update(...)`,
  null = не менять (как `canSelfAssignTasks?`).

---

## Тестовые данные

- Создать 1 редактора с `can_work_with_skipped = true` (через webvue3).
- Создать 1 SKIP-автора (`tbl_authors.skip = true`) с 2 песнями (1
  с тегом SKIP, 1 без).
- Создать 1 не-skip-автора с 1 SKIP-песней (без тега, чтобы проверить
  фильтр только по автору).
- Анонимный пользователь — для регресс-теста.