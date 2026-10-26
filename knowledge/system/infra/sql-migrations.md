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
| 57 | authors_unique_author | UNIQUE-индекс на `tbl_authors.author` (BR-628) |
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

Дополнительно (аудит #336, волна C, пакет PKG-DB-RECORDHASH, OpenProject #363):

- **Канонический файл = единственный источник.** Для таблиц sync-движка определение функции
  живёт в `deploy/recordhash_<table>.sql` (`authors`, `events`, `pictures`,
  `pictures_sync`, `processes`, `site_users`). Миграция, добавляющая колонку в такую таблицу,
  обязана добавить её и в канонический файл; оператор применяет канонический файл на
  **каждой** БД (LOCAL и PROD) — иначе формулы md5 на сторонах разойдутся и diff покажет все
  строки изменёнными.
- **Повторное определение функции обязано быть полным.** Если миграция сама создаёт
  `update_<table>_recordhash()` (иначе нельзя: файлы применяются по одному и на разных БД),
  она MUST объявлять ровно тот же набор и порядок колонок, что канонический файл, и MUST сама
  добавлять `ADD COLUMN IF NOT EXISTS` для всех упомянутых колонок (иначе её же бэкфилл упадёт
  на чистой БД). Прецедент BR-044: семь миграций `tbl_site_users` (12/13/14/17/21/40/45) знали
  каждая только «свои» колонки, и повторный прогон старой миграции (шапки это прямо
  предписывают) молча сужал md5 — персональная скидка и сроки премиума переставали уезжать
  на PROD.
- **Все ссылки в теле триггера — через `NEW.`** Прецедент BR-553: в
  `50_tbl_songs_free_after_on_air.sql` 31 колонка скопирована из `UPDATE`-выражения без
  префикса; `CREATE FUNCTION` проходит, а триггер падает на первой записи
  (`ERROR: column "rate" does not exist`). Бэкфилл ниже функции — единственное место, где
  имена колонок пишутся без `NEW.` (это обычный `UPDATE`).
- **Проверка без БД.** `python3 tools/check-recordhash-consistency.py` (CI + pre-commit)
  проверяет: (1) `NEW.`-префикс во всех триггерных функциях `deploy/**/*.sql`;
  (2) канонические файлы покрывают все `@KaraokeDbTableField` своих моделей и совпадают с
  собственным бэкфиллом; (3) `tbl_songs_sync` содержит все колонки, которые пишет
  `Song.getSqlToInsert(sync = true)`; (5) все миграции `tbl_site_users` объявляют ту же
  формулу и тот же бэкфилл, что `deploy/recordhash_site_users.sql` (BR-044).
- **Порядок применения.** `50`, `52` и `53` правят одну и ту же функцию `tbl_songs_sync`:
  применять `50 → 52 → 53`; после `53` не переприменять `50` (вернёт формулу из 95 колонок)
  и не переприменять `52` (вернёт формулу без `album_id`).


### Решение 4: Миграции — manual (не Liquibase/Flyway)

Нет `migration tool`. Каждая миграция выполняется вручную
(`psql -f file.sql`). TODO Pass 343+: автоматизировать.

### Состояние применённых миграций (снимок от 2026-10-26)

Журнала миграций в проекте нет (Решение 4: всё вручную), поэтому «что уже
накатано» знать нечем. Ниже — состояние, зафиксированное после ручного
применения 2026-10-25 на **обеих** базах (LOCAL — контейнер `karaoke-db` на
админской машине, PROD — `188.127.240.124:5433`).

| Файл | LOCAL | PROD | Что делает |
|---|---|---|---|
| `50_tbl_songs_free_after_on_air.sql` | было | было | флаг `free_after_on_air`, функция `tbl_songs_sync` → 95 слагаемых |
| `51_public_settings_drop_captcha_server_key.sql` | применён | применён | удаляет серверный ключ капчи (теперь в env) |
| `51_subscriptions_on_delete_restrict.sql` | применён | применён | `tbl_subscriptions.site_user_id`: CASCADE → RESTRICT |
| `52_tbl_songs_sync_description_fields.sql` | применён | применён | 5 колонок, функция → 99 |
| `53_tbl_songs_sync_album_id.sql` | применён | применён | `album_id`, функция → 100 |
| `54_fix_result_text_gin_index.sql` | **пропущен** | **пропущен** | см. ниже |
| `55_stem_jobs_on_delete_restrict.sql` | применён | применён | `tbl_stem_jobs.site_user_id`: CASCADE → RESTRICT |
| `56_site_users_recordhash_canonical.sql` | применён | применён | канонический `recordhash` `tbl_site_users` → 19 слагаемых |
| `deploy/recordhash_authors.sql` | применён | применён | канонический `recordhash` авторов → 17 слагаемых |
| `deploy/recordhash_processes.sql` | применён | применён | канонический `recordhash` процессов → 18 слагаемых |
| `deploy/karaoke-db/57_authors_unique_author.sql` | применён | применён | UNIQUE-индекс на `tbl_authors.author` (применён 2026-10-26; перед применением проверено: 132 автора, 0 групп-дубликатов на обеих БД) |

**Почему 54 пропущен.** На обеих базах индекс уже существует и его определение
ровно такое, какое создаёт миграция:
`idx_gin_result_text ON public.tbl_songs USING gin (to_tsvector('russian'::regconfig, result_text))`.
Миграция делает `DROP INDEX` + `CREATE INDEX` (без `CONCURRENTLY`) — то есть
пересоздала бы индекс на 382 МБ, заблокировав запись в `tbl_songs` на минуты
ради нулевого результата. Если понадобится применить её «для честности» —
делать в окно без нагрузки и через `CREATE INDEX CONCURRENTLY`.

**Согласованность формул.** После применения `md5(prosrc)` четырёх функций
(`tbl_songs_sync`, `tbl_site_users`, `tbl_authors`, `tbl_processes`) на LOCAL и
PROD совпали побайтово. Это обязательное условие: sync сравнивает строки только
по `recordhash`, и расхождение формул означало бы «изменён весь каталог».

**Бэкапы перед применением** (откат возможен):
- LOCAL: `.scratch/db/local-20261025-1414.dump` (862 МБ);
- PROD: `/root/db-backups/karaoke-20261025-1414.dump` (140 МБ).

**Порядок.** Миграции применяются по числовому префиксу. Обратить внимание:
в каталоге **две** миграции `51_*` (`public_settings_drop_captcha_server_key` и
`subscriptions_on_delete_restrict`) — номер занят дважды, порядок между ними
не критичен (они трогают разные таблицы), но при добавлении новых файлов
нумерование надо продолжать с `57`.

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

### Решение 7: каскад БД не удаляет объекты вне БД

`ON DELETE CASCADE` уносит строки, но не объекты S3/MinIO. Если уборщик ищет работу по строкам
таблицы (`StemJobPollScheduler.cleanup` → `StemJobCleanup.cleanupJob`), каскадное удаление
строк оставляет объекты в бакете навсегда, а пользователь теряет доступ к результату
(премиум-«минусовка», BR-537). Правило: FK на владельца у таблиц, чьи строки соответствуют
объектам хранилища, — `ON DELETE RESTRICT`; удаление выполняет приложение: сначала объекты,
потом строка. `22_stem_jobs.sql` создаёт `RESTRICT` для новых БД, `55` пересоздаёт FK на
существующих.

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
