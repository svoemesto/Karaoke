# SQL migrations: Settings-related (NNN 02, 04, 05, 07, 11, 23)

> **Домен**: system (infrastructure)
> **Компонента**: детальный каталог SQL миграций для таблиц `tbl_settings`
> и связанных.

## NNN 02: `result_text_and_index`

```sql
ALTER TABLE tbl_settings ADD result_text TEXT;
CREATE INDEX idx_gin_result_text ON tbl_settings
  USING gin (to_tsvector('russian', result_text));
```

- **`result_text`**: произвольный текст (для AI/LLM-поиска).
- **GIN-индекс** с `to_tsvector('russian')` — full-text search по
  русскому языку.

## NNN 04: `id_boosty_files_and_index`

```sql
ALTER TABLE tbl_settings ADD id_boosty_files character varying(40);
CREATE INDEX tbl_settings_id_boosty_files_index ON tbl_settings
  USING btree (id_boosty_files);
```

- **`id_boosty_files`**: ID альбома/поста на Boosty (для premium-публикаций).
- BTree-индекс для быстрого поиска.

## NNN 05: `index_tabs_variant`

```sql
ALTER TABLE tbl_settings ADD index_tabs_variant int DEFAULT 0;
```

- **Формат отображения табов** (LYRICS/CHORDS/TABS) — 0 = tabs,
  1 = alternate (см. `MkoChordBoard`).

## NNN 07: `public_settings`

```sql
CREATE TABLE tbl_public_settings (
    key VARCHAR(255) PRIMARY KEY,
    value TEXT NOT NULL DEFAULT ''
);
```

- **Key/value таблица** — для настроек, нужных сервисам на **проде**
  (karaoke-web).
- **НЕ** ~150 файловых настроек `Karaoke.properties` (только на
  admin-машине).
- Доступна через `Connection.local()/remote()`.

## NNN 11: `pictures_unique_name`

```sql
CREATE UNIQUE INDEX tbl_pictures_unique_name ON tbl_pictures (picture_name);
```

**Проблема**: при параллельной обработке нескольких песен одного
альбома (общий album-логотип) `Pictures.createNewPicture()` не
находил запись и делал INSERT — возникали дубли (на момент ввода — 49).

## NNN 23: `demo_publish_links`

```sql
-- (не изучено, Pass 343+)
```

## Связь

- [sql-migrations-detailed.md](sql-migrations-detailed.md) — обзор.
- [karaoke-properties.md](../../domains/processing/components/karaoke-properties.md) — 150 параметров.
- [Pictures](../../domains/catalog/components/pictures.md) — entity.
-

## Changelog

- **Pass 453** (2026-09-09): Initial. Автор: agent (Karaoke).