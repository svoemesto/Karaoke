# SQL migrations (deploy/karaoke-db/)

> **Домен**: system (infrastructure)
> **Компонента**: каталог SQL-миграций проекта (55 файлов на 2026-10-24).

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
| 18 | author_aliases | `tbl_authors.aliases` + **bootstrap** `tbl_authors` (BR-039) |
| 19 | site_chat_messages | tbl_site_chat_messages (SiteChatMessage) |
| 20 | news | tbl_news (News) |
| 21 | site_user_welcome_message | Welcome message для SiteUser |
| 22 | stem_jobs | tbl_stem_jobs (StemJob) |
| 23 | demo_publish_links | Demo-публикации |
| 24 | song_type | SongType enum |
| 25 | audio_parent | Audio parent (parent_id для audio) |
| 26 | player_readiness_flags | `*_ready` колонки в tbl_songs (см. health-report.md) |
| 27 | author_special_order | Author special order |
| 46 | author_sort_order | `tbl_authors.sort_order` + полный recordhash (BR-534) |
| 27 | listening_history | tbl_listening_history (ListeningHistory) |
| 51 | subscriptions_on_delete_restrict | `tbl_subscriptions.site_user_id` → `ON DELETE RESTRICT` (BR-041) |
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

### Решение 5: initdb-цепочка должна воспроизводить схему с нуля

`deploy/docker-compose-database.yml` монтирует `deploy/karaoke-db/` в
`/docker-entrypoint-initdb.d`, официальный entrypoint `postgres:16`
выполняет файлы по порядку через `psql -v ON_ERROR_STOP=1`: **любая**
ошибка роняет инициализацию, а контейнер с `restart: always` уходит в
цикл рестартов. Отсюда два правила (BR-039, BR-534, пакет #371):

- файл, который может выполняться на БД, где объект уже есть, обязан
  быть идемпотентным (`ADD COLUMN IF NOT EXISTS`, `CREATE INDEX IF NOT
  EXISTS`, `CREATE TABLE IF NOT EXISTS`);
- файл, который делает `ALTER TABLE`, обязан сам создавать таблицу, если
  её нет в наборе (пример — `18_author_aliases.sql` и `tbl_authors`:
  ни один файл каталога её не создавал, а `01_initdb.sql` — частичный
  дамп из четырёх таблиц).

Известное ограничение (за владельцем): каталог по-прежнему не является
полным дампом схемы — `tbl_songs`, `tbl_authors` и другие создаются
только миграциями/bootstrap'ом, поэтому «поднять БД с нуля» проверяется
только прогоном на пустом PGDATA, а не автоматическим тестом.

### Решение 6: recordhash-функция авторов — один канонический набор и порядок

`update_tbl_authors_recordhash()` определена в нескольких файлах
(`27`, `31`, `44`, `46`, `18`), и каждая `CREATE OR REPLACE`
**переопределяет** предыдущую. Если очередная версия теряет колонки,
sync-diff перестаёт видеть их изменения: `KaraokeDbTable.getListHashes`
сравнивает строки только по `recordhash` (BR-534, пакет #371).

Канонический порядок (17 колонок), зафиксирован в `46` и `18`:

```
id, author, ym_id, vk_id, last_album_ym, last_album_vk, last_album_processed,
watched, skip, aliases, is_special_order, sort_order,
description, short_description, warning, ready_songs_count, total_songs_count
```

Первые 12 — префикс, который был развёрнут на LOCAL/PROD до правки;
пять недостающих полей добавлены в конец. Порядок менять нельзя: разные
формулы дают разный `md5` у одной и той же строки, и LOCAL/SERVER
навсегда видят расхождение. После применения — backfill пересчитывает
хэш всех строк (однократный полный re-sync авторов).

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

- **Pass 382** (2026-10-24, PKG-S2-08 / OpenProject #371): Решение 5
  (initdb-цепочка с нуля: идемпотентность, bootstrap таблицы в 18) и
  Решение 6 (канонический набор/порядок колонок recordhash авторов);
  каталог дополнен 46 и 51.
- **Pass 381** (2026-09-09): Initial. Автор: agent (Karaoke).