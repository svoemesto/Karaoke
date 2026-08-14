# How to: миграция БД (новая колонка / таблица)

## Prerequisites

- Доступ к admin-машине (`hostname=dev-pc && whoami=dev`) — здесь запускается
  `karaoke-app` + `karaoke-db`.
- Доступ к серверу БД через SSH.
- Имя миграции: `<NNN>_<table_or_concept>.sql` (напр. `42_add_tbl_foo.sql`).
- Знание текущей **последней миграции** (см. `ls deploy/karaoke-db/*.sql | tail -1`).

## Steps

### 1. Создать файл миграции

```bash
cd /path/to/Karaoke
NEW_NNN=$((LAST_NNN + 1))
touch "deploy/karaoke-db/${NEW_NNN}_add_tbl_foo.sql"
```

### 2. Написать SQL

```sql
-- Заголовок обязателен
-- Migration: add tbl_foo (columns bar, baz)
-- Дата: 2026-08-14
-- Автор: <opencode-agent / разработчик>
-- Spec: specs/189-live-documentation (или конкретная фича)

-- Изменяемые таблицы: tbl_foo (создаётся)

CREATE TABLE tbl_foo (
    id BIGSERIAL PRIMARY KEY,
    bar TEXT NOT NULL,
    baz INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    recordhash TEXT NOT NULL DEFAULT md5(''::text)  -- см. ADR-0001, data-sync
);

CREATE INDEX idx_tbl_foo_bar ON tbl_foo(bar);

-- Trigger recordhash
CREATE OR REPLACE FUNCTION update_recordhash_tbl_foo() RETURNS trigger AS $$
BEGIN
    NEW.recordhash = md5(NEW.id || NEW.bar || NEW.baz || NEW.created_at::text);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tbl_foo_recordhash
    BEFORE UPDATE ON tbl_foo
    FOR EACH ROW
    EXECUTE FUNCTION update_recordhash_tbl_foo();

-- Если таблица участвует в sync — добавить SyncRegistry (см. architecture/data-sync.md)
-- Сначала проверить: есть ли tbl_foo в karaoke-app/.../sync/SyncRegistry.kt
-- Если нет — добавить.
```

### 3. Локально применить

```bash
docker exec -i karaoke-db psql -U postgres -d karaoke < deploy/karaoke-db/${NEW_NNN}_add_tbl_foo.sql
```

### 4. Закоммитить

```bash
git add deploy/karaoke-db/${NEW_NNN}_add_tbl_foo.sql
git commit -m "Migration: add tbl_foo"
```

### 5. Накатить на прод (только пользователь, не агент!)

```bash
ssh root@${PROD_HOST}

# Backup перед миграцией
docker exec karaoke-db pg_dump -U postgres karaoke > /backup/karaoke_pre_${NEW_NNN}.sql

# Применить
docker exec -i karaoke-db psql -U postgres -d karaoke < /path/to/${NEW_NNN}_add_tbl_foo.sql

# Проверить
docker exec karaoke-db psql -U postgres -d karaoke -c "\\d tbl_foo"
```

## Verification

- На admin: `karaoke-app` запускается без ошибок.
- На прод: `karaoke-web` (без `karaoke-app`) — ему достаточно, чтобы таблица
  существовала, даже если `karaoke-app`-код там не запускается.

## Rollback

```sql
DROP TRIGGER IF EXISTS tbl_foo_recordhash ON tbl_foo;
DROP FUNCTION IF EXISTS update_recordhash_tbl_foo;
DROP INDEX IF EXISTS idx_tbl_foo_bar;
DROP TABLE IF EXISTS tbl_foo;
```

**Перед rollback на проде**: проконсультироваться с пользователем — данные
могут быть потеряны.

## Related

- LiveDocs: [architecture/dual-db-access.md](../architecture/dual-db-access.md),
  [architecture/data-sync.md](../architecture/data-sync.md),
  [ADR-0001](../architecture/decisions/0001-raw-jdbc.md).
- Constitution: § VIII «Секреты и git-гигиена» (не коммитить SQL в обход
  миграций).