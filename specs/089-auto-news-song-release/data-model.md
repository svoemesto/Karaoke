# Data Model: Автоматические новости о выходе песни в эфир

## Изменённые/новые сущности

### `News` (существующая, `tbl_news`) — новые поля

| Поле (Kotlin) | Колонка | Тип | Nullable | Назначение |
|---|---|---|---|---|
| `songId` | `song_id` | `bigint` | да | Заполняется только для авто-созданных новостей — ссылка на `tbl_songs.id`. `NULL` для ручных новостей (без изменений в существующем поведении). |
| `source` | `source` | `varchar(20)` | нет, `DEFAULT 'manual'` | `'manual'` (по умолчанию — существующие и новые ручные новости) / `'auto'` (созданные этим механизмом). Используется UI-фильтром в webvue3 и WHERE-исключением в `News.listHashes` для sync-движка. |

**Важно (см. research.md, п.2)**: оба поля **НЕ включаются**:
- в `update_tbl_news_recordhash()` (SQL-триггер) — иначе изменение этих полей у существующих ручных
  строк повлияло бы на их recordhash и создало бы ложный «diff» для обычного sync;
- в список полей, которые `News.listHashes(...)` возвращает для сравнения sync-движком, когда
  `source = 'auto'` — такие строки должны быть полностью не видны `NewsSyncTarget` (см. ниже).

Изменение `News.listHashes` (используется исключительно `NewsSyncTarget`, generic sync engine):

```text
-- было (упрощённо):
SELECT id, recordhash FROM tbl_news <whereText>

-- становится:
SELECT id, recordhash FROM tbl_news WHERE source = 'manual' <AND whereText, если передан>
```

Админский листинг (`News.loadAll`, `/api/news/list`) — **без изменений в фильтрации**, продолжает
возвращать все строки независимо от `source` (это то, что даёт видимость по User Story 2).

`NewsDto` — добавить поле `source: String` (для отображения бейджа «авто»/«ручная» в webvue3).

### `SongNewsAnnounced` (новая, `tbl_song_news_announced`) — PROD-локальная таблица-метка

| Поле | Тип | Ограничения | Назначение |
|---|---|---|---|
| `song_id` | `bigint` | `PRIMARY KEY`, `REFERENCES tbl_songs(id)` | Песня, по которой уже принято решение «анонс создан / не нужен» (включая backfill-строки без реальной новости). |
| `news_id` | `bigint` | `NULL`, `REFERENCES tbl_news(id)` | Ссылка на созданную новость. `NULL` для backfill-строк (см. research.md п.5, шаг 3) — «эта песня была готова уже до включения фичи, анонс не создавался намеренно». |
| `created_at` | `timestamp` | `NOT NULL DEFAULT now()` | Когда принято решение. |

Эта таблица **не регистрируется** в `SyncRegistry` — она не участвует в LOCAL↔SERVER синхронизации
вообще (ни push, ни pull). Она должна физически существовать в схеме и на LOCAL, и на PROD (для
консистентности кода/дев-стенда), но реально накапливает данные только там, где выполняется
`doChangeRecords` — то есть на PROD (и на локальном dev-стенде при ручном тестировании, что не
является проблемой, поскольку данные PROD и LOCAL по этой таблице никогда не сравниваются).

## Связь с существующими сущностями

- **`Song`** (`tbl_songs`) — не меняется схемно. Получает новое **вычисляемое** (не хранимое) свойство
  `isPubliclyWatchable: Boolean`, консолидирующее уже существующую логику `id_status >= 6` +
  `onAir` + готовность стемов/обложек/маркеров (см. research.md п.4). Используется и
  `PublicPlayerController`, и новым `SongReleaseAnnouncementService`.
- **`SongReleaseAnnouncementService`** (новый, не сущность БД) — читает кандидатов из `Song`,
  сверяется с `SongNewsAnnounced`, создаёт `News` (`source = 'auto'`) и строку в
  `tbl_song_news_announced` в одной транзакции.

## State transitions (для одной песни)

```text
[не готова]
   │  id_status >= 6 достигнут, но дата эфира ещё не наступила
   ▼
[готова по статусу, ждёт эфира]
   │  наступила дата/время эфира (публично доступна) — обнаруживается на следующем /changerecords
   ▼
[публично доступна, анонс не создан] ──(SongReleaseAnnouncementService)──▶ [анонс создан]
                                                                              │
                                                     ручное понижение статуса ниже 6 (FR-008)
                                                                              ▼
                                                          [анонс остаётся; песня скрыта с сайта]
```

Переход «публично доступна → анонс создан» необратим и происходит не более одного раза на песню
(гарантия — `PRIMARY KEY(song_id)` в `tbl_song_news_announced`, FR-004). Понижение статуса
администратором НЕ откатывает эту метку (FR-008) — повторное достижение готовности той же песней
повторный анонс не создаёт (это соответствует Edge Cases spec.md; если потребуется «повторный
анонс», это отдельная будущая фича — ручное удаление строки из `tbl_song_news_announced` вручную не
предусмотрено этой фичей).

## Миграция схемы (для `deploy/karaoke-db/`)

Один новый SQL-файл (по аналогии с `20_news.sql`), применяемый вручную на LOCAL и на PROD отдельно
(см. Constitution — «применять вручную на КАЖДОЙ БД отдельно», и «Ограничения агента» — DDL/DML на
PROD только по согласию пользователя):

- `ALTER TABLE tbl_news ADD COLUMN song_id bigint NULL;`
- `ALTER TABLE tbl_news ADD COLUMN source varchar(20) NOT NULL DEFAULT 'manual';`
- `CREATE TABLE tbl_song_news_announced (...)` + `PRIMARY KEY (song_id)` + FK на `tbl_songs`/`tbl_news`
  (nullable FK).
- Индекс `idx_tbl_song_news_announced_news_id` — не обязателен (таблица маленькая, PK на `song_id`
  достаточен для обеих сторон запроса «уже анонсировано?»).
- **Не трогать** `update_tbl_news_recordhash()` — новые колонки не должны попасть в хэш (см. выше).
