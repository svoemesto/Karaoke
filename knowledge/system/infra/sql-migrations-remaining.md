# SQL migrations: остальные (NNN 08, 24-30+)

> **Домен**: system (infrastructure)
> **Компонента**: детальный каталог остальных SQL миграций.

## NNN 08: `ip_country`

```sql
CREATE TABLE tbl_ip_country (
    ip VARCHAR PK,
    country VARCHAR
);
```

- **Служебный кэш** «IP → страна» для админ-дашборда.
- Заполняется на лету через `api.country.is` (см.
  [external-api-clients.md#geoipservice](../../domains/integration/components/external-api-clients.md)).
- **НЕ** доменные данные: вне `recordhash`, **НЕ** sync.
- **Применять ТОЛЬКО на LOCAL** (на проде karaoke-app не разворачивается,
  страна на проде не нужна).

## NNN 09: `playlists`

```sql
CREATE TABLE tbl_site_playlists (...);
CREATE TABLE tbl_site_playlist_items (...);
```

(См. [siteplaylist--siteplaylistitem](../../domains/catalog/components/entities-catalog.md#siteplaylist--siteplaylistitem).)

## NNN 10: `song_assignments`

```sql
CREATE TABLE tbl_song_assignments (
    id INT PK,
    assignee_id BIGINT,
    song_id BIGINT,
    voice INT,
    admin_status VARCHAR,
    review_comment TEXT,
    assigned_by BIGINT,
    assigned_at TIMESTAMP,
    reviewed_at TIMESTAMP
);
CREATE TABLE tbl_song_assignment_drafts (...);
```

(См. [songassignment--songassignmentdraft](../../domains/catalog/components/entities-catalog.md#songassignment--songassignmentdraft).)

## NNN 17: `dictionaries`

```sql
CREATE TABLE tbl_dictionaries (
    id INT PK,
    dict_name VARCHAR,
    dict_value VARCHAR,
    UNIQUE (dict_name, dict_value)
);
```

(См. [dictionaries.md](../../domains/catalog/components/dictionaries.md).)

## NNN 24: `song_type`

```sql
ALTER TABLE tbl_songs ADD song_type VARCHAR DEFAULT 'song';
```

- **Тип песни** (отличает по составу):
  - `song` — вокал + музыка.
  - `instrumental` — только музыка без вокала.
  - `poetry` — только вокал без музыки.
- Default `song` для всех существующих.
- Применять вручную на КАЖДОЙ БД.

## NNN 25: `audio_parent`

```sql
ALTER TABLE tbl_songs ADD audio_parent_id BIGINT;
ALTER TABLE tbl_songs ADD audio_parent_similarity NUMERIC;
ALTER TABLE tbl_songs ADD audio_parent_delta_ms INT;
ALTER TABLE tbl_songs ADD audio_compare_history TEXT;
```

- **"Аудио-родитель"** песни: id наиболее похожей по `WaveformCompare`,
  % схожести, дельта сдвига маркеров (мс).
- `audio_compare_history` — JSON-история (id кандидата, %, ok, время)
  чтобы не сравнивать повторно.
- Применять вручную на КАЖДОЙ БД.

## NNN 26: `player_readiness_flags`

```sql
ALTER TABLE tbl_songs ADD player_readiness_flags TEXT;
```

- **Готовность к онлайн-плееру** персистентно (НЕ вычисляется на лету).
- Хранится ОДНИМ JSON-полем: `{"stemAccompanimentReady":true, ...}`.
- Обновляется в момент успешной заливки файла.
- Сверяется `HealthReport.recomputeAndBroadcast` на случай рассинхрона.

(См. [health-report.md#reconcileplayerreadinessflags](../../domains/health/components/health-report.md) — 4 флага.)

## NNN 27: `author_special_order`

```sql
ALTER TABLE tbl_authors ADD is_special_order BOOLEAN DEFAULT false;
```

- Отдельный флаг «спецзаказных» авторов (<3 песен — сделаны по
  индивидуальному заказу).
- Используется в karaoke-public для виртуальной плашки "Отдельные
  песни разных авторов" в конце Закромов.
- Один раз на LOCAL и PROD.
- Колонка входит в `recordhash` `tbl_authors`.

## NNN 28-30+

**NNN 28**: (если есть — `28_publishing.sql`).
**NNN 29-30**: (если есть — `29_stem_jobs_cleanup.sql`, `30_subscription.sql`).

## Связь

- [sql-migrations-detailed.md](sql-migrations-detailed.md) — обзор.
- [entities-catalog.md](../../domains/catalog/components/entities-catalog.md) — entities.

## Changelog

- **Pass 461-463** (2026-09-09): Initial. Автор: agent (Karaoke).