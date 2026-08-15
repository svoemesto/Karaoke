---
status: Active
slug: db-migration-playbook
type: topic
related:
  - ../architecture/database.md
  - ../architecture/data-sync.md
  - ../architecture/invariants.md
  - ../runbooks/how-to-migrate-db.md
  - ../runbooks/how-to-migrate-prod-server.md
  - ../architecture/decisions/0001-raw-jdbc.md
---

# Database migration playbook — production

> Полный playbook для production-миграций. Drill-down для
> `runbooks/how-to-migrate-db.md` (шаги) и `runbooks/how-to-migrate-prod-server.md`
> (новый сервер).

## Назначение

В проекте Karaoke **миграция БД — отдельный процесс**, не автоматизированный:

- **Нет Flyway/Liquibase** — миграции применяются вручную.
- **Нет автоприменения** — каждая миграция отдельный `.sql` файл.
- **Нет автотеста** миграции (проверяется вручную на LOCAL).

Это by design (см. ADR-0001 — raw JDBC, без ORM), но требует
**дисциплины** от разработчика.

## Когда нужна миграция

Миграция БД нужна, если:

1. **Добавлена новая таблица** (`CREATE TABLE tbl_foo`)
2. **Добавлена колонка** (`ALTER TABLE tbl_bar ADD COLUMN baz VARCHAR` — nullable)
3. **Изменён тип колонки** (`ALTER TABLE tbl_bar ALTER COLUMN baz TYPE BIGINT`)
4. **Добавлен индекс** (`CREATE INDEX idx_tbl_bar_baz ON tbl_bar(baz)`)
5. **Изменена функция триггера** (recordhash — `CREATE OR REPLACE FUNCTION`)
6. **Удалена колонка/таблица** (редко, см. «Удаление» ниже)

**НЕ нужна миграция**, если:

- Изменён KDoc в Kotlin (не SQL)
- Добавлен новый эндпоинт (DML, не DDL)
- Изменён Vue-компонент (frontend-only)

## Структура миграции

### Имя файла

```
deploy/karaoke-db/<NNN>_<table_or_concept>.sql
```

`<NNN>` — 3 цифры, монотонно возрастает. Текущая последняя:

```bash
ls deploy/karaoke-db/*.sql | tail -1
# Например: deploy/karaoke-db/29_albums.sql
```

### Шаблон

```sql
-- Migration: <что меняется> (<список колонок / таблиц>)
-- Дата: <YYYY-MM-DD>
-- Автор: <разработчик / opencode-agent>
-- Spec: specs/<NNN>-<slug>/spec.md (или PASS номер)
-- Risk: <low|medium|high> — поясните почему

-- Изменяемые таблицы: <tbl_foo, tbl_bar>
-- Зависимости: <миграция NNN, если есть>

-- DOWN-секция (как откатить)
-- DROP TABLE tbl_foo;

-- Изменения ↓
ALTER TABLE tbl_bar ADD COLUMN baz VARCHAR(255) DEFAULT NULL;
```

### Заголовок — обязателен

Без заголовка CI не пропустит (rules-of-write проект).

## Workflow

```
1. LOCAL: создать deploy/karaoke-db/<NNN>_<name>.sql
2. LOCAL: применить через docker exec
3. LOCAL: убедиться что код (Kotlin) использует новые колонки
4. LOCAL: коммит + push → PR
5. CI: проверяет что миграция ОК (через check-livedocs-structure.sh)
6. MERGE в master
7. ADMIN-MACHINE: применить миграцию (db-sync подхватит recordhash)
8. PROD: применить миграцию (ВАЖНО: см. ниже про порядок)
```

## Команды

### LOCAL (admin-машина)

```bash
# Подключение к БД
docker exec -it karaoke-db psql -U postgres -d karaoke

# Применить миграцию
docker exec -i -u postgres karaoke-db psql -d karaoke < deploy/karaoke-db/<NNN>_<name>.sql

# Проверить что миграция применилась
docker exec -i -u postgres karaoke-db psql -d karaoke -c "\dt"  # список таблиц
docker exec -i -u postgres karaoke-db psql -d karaoke -c "\d tbl_foo"  # структура
```

### PROD (production)

```bash
# На прод-сервере
docker exec -i -u <prod_role> karaoke-db psql -d karaoke < deploy/karaoke-db/<NNN>_<name>.sql

# prod_role — НЕ postgres! Узнать:
docker exec karaoke-db env | grep '^POSTGRES_USER='
```

**ВАЖНО**: миграция должна быть **обратно совместимой** (nullable column, default value),
чтобы старый код мог работать с новой схемой.

## recordhash-триггеры (критично!)

Любое изменение таблицы с recordhash-триггером **обязано**:

1. Обновить функцию триггера (`CREATE OR REPLACE FUNCTION`)
2. Разовый `UPDATE tbl_xxx SET recordhash = md5(...)` для существующих строк

Без этого `SyncTarget.listHashes()` не увидит diff → **LOCAL↔SERVER sync молча игнорирует** изменение.

Таблицы с recordhash:
- `tbl_settings`
- `tbl_dictionaries`
- `tbl_events`
- `tbl_site_users`
- `tbl_songs`
- `tbl_pictures`
- ... (см. `karaoke-app/.../model/KaraokeDbTable.kt`)

## Типичные миграции

### 1. Nullable column

```sql
-- 101_add_published_at.sql
ALTER TABLE tbl_songs ADD COLUMN published_at TIMESTAMP DEFAULT NULL;
CREATE INDEX idx_tbl_songs_published_at ON tbl_songs(published_at);
```

**Безопасно**: старый код не знает о колонке, но работает.

### 2. NOT NULL с default

```sql
-- 102_add_seo_slug_not_null.sql
ALTER TABLE tbl_songs ADD COLUMN seo_slug VARCHAR(255) NOT NULL DEFAULT '';
UPDATE tbl_songs SET seo_slug = id::text WHERE seo_slug = '';
ALTER TABLE tbl_songs ALTER COLUMN seo_slug DROP DEFAULT;
```

**В 3 шага**: add nullable → backfill → drop default. Каждый в отдельной
миграции (или всё в одной, но рискованно).

### 3. Новая таблица

```sql
-- 103_create_tbl_subscriptions.sql
CREATE TABLE tbl_subscriptions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES tbl_site_users(id),
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_tbl_subscriptions_user_id ON tbl_subscriptions(user_id);
```

**Безопасно**: новая таблица, старый код не задевает.

### 4. Изменение типа

```sql
-- 104_change_status_to_bigint.sql
ALTER TABLE tbl_songs ALTER COLUMN id_status TYPE BIGINT;
```

**Опасно**: может блокировать таблицу на больших данных. Лучше в 3 шага:
1. Создать новую колонку `id_status_new BIGINT`
2. Backfill + триггер для синхронизации
3. Drop old + rename new

### 5. Удаление

```sql
-- 105_drop_obsolete_column.sql
ALTER TABLE tbl_songs DROP COLUMN obsolete_field;
```

**Осторожно**: код может ещё читать это поле. Проверьте grep:
```bash
grep -r "obsolete_field" karaoke-app/src/
```

## Производственные миграции — чеклист

Перед миграцией на PROD:

- [ ] Миграция проверена на LOCAL (полная регрессия)
- [ ] Колонка nullable или имеет default
- [ ] recordhash-функция обновлена (если таблица с триггером)
- [ ] Все индексы созданы
- [ ] Изменение совместимо со старым кодом (если rolling deploy)
- [ ] Тест на больших данных (10k+ строк)
- [ ] **kill-switch** для auto-news (если меняются news-флаги)
- [ ] **Бэкап БД** перед миграцией (`pg_dump`)
- [ ] DOWN-секция (как откатить)

## Миграция + sync

`tbl_settings` и др. с recordhash-триггером — после миграции на LOCAL,
sync на PROD подхватит изменение автоматически.

**Но** для DDL (CREATE TABLE, ALTER TABLE) — sync **не применит** на PROD.
Это ограничение raw JDBC (sync работает только с DML).

Поэтому **DDL миграции** — **всегда вручную** на PROD.

## Типичные ошибки

| Симптом | Причина | Фикс |
|---------|---------|------|
| `relation "tbl_foo" does not exist` | Миграция не применена | `docker exec -i -u postgres karaoke-db psql -d karaoke < migration.sql` |
| `column "baz" does not exist` | Код использует колонку до миграции | Применить миграцию раньше |
| Sync пропускает изменение | Забыли обновить recordhash-функцию | `CREATE OR REPLACE FUNCTION` + `UPDATE recordhash = md5(...)` |
| Миграция зависает на LOCK | Долгий ALTER TABLE на больших данных | Использовать `ALTER TABLE ... ADD COLUMN` (быстрый) или `CONCURRENTLY` для индексов |
| `constraint violation` | FK не проходит | Backfill перед ALTER TABLE ADD CONSTRAINT |

## Связанные артефакты

- `deploy/karaoke-db/*.sql` — все миграции
- `karaoke-app/.../model/KaraokeDbTable.kt` — Kotlin-модель с recordhash
- `karaoke-app/.../sync/SyncTarget.kt` — sync через recordhash-diff
- `tools/check-livedocs-structure.sh` — проверяет наличие миграций? (нет)
- `runbooks/how-to-migrate-db.md` — step-by-step инструкция
- `runbooks/how-to-migrate-prod-server.md` — миграция на новый сервер

## См. также

- `architecture/database.md` — tbl_public_settings + recordhash-триггеры
- `architecture/data-sync.md` — LOCAL↔SERVER sync (recordhash-diff)
- `architecture/invariants.md` — ловушки karaoke-web (нет postgres)
- `architecture/decisions/0001-raw-jdbc.md` — почему raw JDBC
- `runbooks/how-to-migrate-db.md` — процесс
- `runbooks/how-to-migrate-prod-server.md` — playbook для нового сервера

## История

- Создан: 2026-08-14 (Pass 51+ follow-up спеки 189)
- Автор: opencode (MiniMax-M3)
- Последнее обновление: 2026-08-14