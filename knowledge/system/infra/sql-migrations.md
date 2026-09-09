# SQL migrations (deploy/karaoke-db/)

> **Домен**: system (infrastructure)
> **Компонента**: каталог 54 SQL миграций проекта.

## Назначение

`deploy/karaoke-db/*.sql` — **54 миграции** в формате `NN_<name>.sql`.
Каждая добавляет/изменяет таблицу или индекс.

## Каталог миграций (по NNN)

| NNN | Slug | Что |
|---|---|---|
| 01 | initdb | Начальная схема (Song, Author, Album, ...) |
| 02 | result_text_and_index | Поле result_text + индексы |
| 03 | events | tbl_events (см. WebEvent) |
| 04 | id_boosty_files_and_index | Boosty files (legacy) |
| 04 | melody | Melody (ноты) |
| 05 | index_tabs_variant | Индексы для табов |
| 06 | site_users | tbl_site_users (SiteUser) |
| 07 | public_settings | tbl_public_settings (PublicSettings) |
| 08 | ip_country | tbl_ip_country (GeoIp кэш) |
| 09 | playlists | tbl_site_playlists (Playlists) |
| 10 | song_assignments | tbl_song_assignments (SongAssignment) |
| 11 | pictures_unique_name | UNIQUE constraint на picture_name |
| 12 | site_user_limits | tbl_site_user_limits |
| 13 | site_user_editor_role | Editor role для SiteUser |
| 14 | site_user_premium_sources | Источники премиум-доступа |
| 15 | monetization | Таблицы монетизации (PriceTariff, PromoRule) |
| 16 | cart_and_orders | tbl_cart_items, tbl_orders |
| 17 | dictionaries | tbl_dictionaries (см. Dictionary) |
| 17 | site_user_personal_discount | Персональные скидки SiteUser |
| 18 | author_aliases | tbl_author_aliases (псевдонимы) |
| 19 | site_chat_messages | tbl_site_chat_messages (SiteChatMessage) |
| 20 | news | tbl_news (News) |
| 21 | site_user_welcome_message | Welcome message для SiteUser |
| 22 | stem_jobs | tbl_stem_jobs (StemJob) |
| 23 | demo_publish_links | Demo-публикации |
| 24 | song_type | SongType enum |
| 25 | audio_parent | Audio parent (parent_id для audio) |
| 26 | player_readiness_flags | `*_ready` колонки в tbl_songs (см. health-report.md) |
| 27 | author_special_order | Author special order |
| 27 | listening_history | tbl_listening_history (ListeningHistory) |
| ... (ещё ~25, см. `ls deploy/karaoke-db/*.sql`) | | |

**NB**: номера не строго последовательные (есть 04 дважды, 17
дважды, 27 дважды) — это **намеренно** (параллельные фичи).

## Архитектурные решения

### Решение 1: Один NNN = один файл

`NNN_<slug>.sql` — одна миграция = один файл. Идемпотентность через
`IF NOT EXISTS` / `DROP IF EXISTS`.

### Решение 2: NNN — не строго последовательные

Параллельные фичи могут иметь одинаковый NNN (если не было
конфликта). Нет глобального lock'а.

### Решение 3: `recordhash`-триггер

Каждая таблица, участвующая в sync, **MUST** иметь `recordhash`
колонку + триггер. См. [two-db-sync.md](../../domains/processing/components/two-db-sync.md).

### Решение 4: Миграции — manual (не Liquibase/Flyway)

Нет `migration tool`. Каждая миграция выполняется вручную
(`psql -f file.sql`). TODO Pass 343+: автоматизировать.

## Связь с Knowledge

Каждая миграция соответствует **domain entity** в
[entities-catalog.md](../../domains/catalog/components/entities-catalog.md)
или [persistence domain](../../domains/persistence/domain.md).

## Известные TODO

- [ ] **Каждая миграция** — детальное описание в Knowledge (Pass 343+).
- [ ] **Автоматизация** — Liquibase/Flyway.
- [ ] **Какие миграции выполнены** на admin / на проде (need
      tracking).

## Changelog

- **Pass 381** (2026-09-09): Initial. Автор: agent (Karaoke).