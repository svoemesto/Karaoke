# Quickstart: Закрома — Альбомы авторов

> Пошаговая валидация фичи end-to-end. Каждый шаг — конкретное действие + ожидаемый результат. После прохождения всех шагов фича готова к merge.

## Предусловия

| Шаг | Команда | Ожидание |
|---|---|---|
| 1 | `git checkout 356-zakroma-albums-by-author && git pull` | Ветка актуальна |
| 2 | `source .env.local-tracker && bash tools/tracker.sh get-issue 70` | Статус: `In progress`, assignee: `ai-agent` |
| 3 | `ls deploy/karaoke-db/49_albums_song_counts.sql` | Файл миграции существует |
| 4 | `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:compileKotlin --parallel` | `BUILD SUCCESSFUL` |

## Шаг 1: Применение миграции на LOCAL

```bash
# Подключение к LOCAL Postgres
psql -h localhost -U postgres -d karaoke

# Применение миграции
\i /path/to/deploy/karaoke-db/49_albums_song_counts.sql

# Проверка колонок
\d tbl_albums
-- Должно быть: total_song_count BIGINT NOT NULL DEFAULT 0,
--               ready_song_count BIGINT NOT NULL DEFAULT 0

# Проверка триггера
\df+ trg_tbl_songs_update_album_counts
-- Должна быть функция

\dy+ trg_tbl_songs_update_album_counts
-- Должен быть триггер на tbl_songs
```

**Ожидание**: миграция применяется без ошибок, новые колонки и триггер созданы.

## Шаг 2: Backfill существующих данных

```sql
-- Проверка: сколько альбомов получили ненулевые счётчики
SELECT COUNT(*) AS total_albums,
       COUNT(*) FILTER (WHERE total_song_count > 0) AS albums_with_songs,
       COUNT(*) FILTER (WHERE ready_song_count > 0) AS albums_with_ready
FROM tbl_albums;

-- Сравнение с реальным COUNT(*) FROM tbl_songs для верификации
SELECT
    (SELECT COUNT(*) FROM tbl_songs) AS songs_total,
    (SELECT SUM(total_song_count) FROM tbl_albums) AS albums_total_sum,
    (SELECT SUM(ready_song_count) FROM tbl_albums) AS albums_ready_sum;
```

**Ожидание**: `songs_total` ≈ `albums_total_sum` (могут отличаться на кол-во песен с `album_id = NULL`).

## Шаг 3: Тест триггера (атомарность)

```sql
-- Тест 1: INSERT новой песни с id_status=6
INSERT INTO tbl_songs (id_status, song_author, song_album, album_id, ...)
VALUES (6, 'Test Author', 'Test Album', <existing_album_id>, ...);
-- Проверить: total_song_count += 1, ready_song_count += 1 для этого альбома

-- Тест 2: UPDATE id_status с 6 на 5
UPDATE tbl_songs SET id_status = 5 WHERE id = <new_song_id>;
-- Проверить: ready_song_count -= 1, total_song_count без изменений

-- Тест 3: DELETE песни
DELETE FROM tbl_songs WHERE id = <new_song_id>;
-- Проверить: оба счётчика -= 1

-- Тест 4: UPDATE album_id (перенос между альбомами)
UPDATE tbl_songs SET album_id = <another_album_id> WHERE id = <some_song_id>;
-- Проверить: OLD-альбом декремент, NEW-альбом инкремент

-- Тест 5: skip=true альбом НЕ появляется в публичном API (FR-016)
INSERT INTO tbl_albums (author_id, year, name, album_type, sort_order, skip)
VALUES (<author_id>, 2099, 'SkipTestAlbum', 'studio', 0, true);
-- Проверить: SELECT в API не возвращает этот альбом (curl /api/public/authors/{id}/albums)
-- Откатить: DELETE FROM tbl_albums WHERE name = 'SkipTestAlbum'
```

**Ожидание**: каждое UPDATE/INSERT/DELETE корректно меняет счётчики в `tbl_albums`. Skip-альбом (`skip=true`) НЕ появляется в публичном API (FR-016). После тестов — откатить.

## Шаг 4: Sync LOCAL → SERVER

```bash
# На admin-машине — запуск синхронизации
bash deploy/do.sh sync   # или конкретная команда из do.sh

# Проверка на SERVER
psql -h <server-ip> -U postgres -d karaoke
SELECT id, name, total_song_count, ready_song_count, recordhash
FROM tbl_albums ORDER BY id LIMIT 10;

# Сравнение md5 с LOCAL
SELECT md5(string_agg(recordhash, '' ORDER BY id))
FROM tbl_albums;
-- Должно совпадать между LOCAL и SERVER
```

**Ожидание**: значения `total_song_count`/`ready_song_count` идентичны на LOCAL и SERVER, `recordhash` совпадает.

## Шаг 5: Сборка и запуск `karaoke-web`

```bash
# Бэкенд
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-web:bootJar --parallel

# Запуск контейнера (или рестарт через deploy/do.sh)
cd deploy && bash do.sh build_webvue3  # / start
```

**Ожидание**: контейнер `karaoke-web` запускается без ошибок, `/api/public/authors/42/albums` отвечает.

## Шаг 6: Проверка API

```bash
# Гость
curl -s "http://localhost:8080/api/public/authors/42/albums" | jq '.[0]'

# Ожидаемый JSON:
# {
#   "id": 17,
#   "name": "Alpha",
#   "year": 1995,
#   "pictureUrl": "...",
#   "totalSongCount": 12,
#   "readySongCount": 12,
#   "albumType": "studio"
# }
```

**Ожидание**: ответ содержит массив `AlbumTilePublicDto`. Гость видит только альбомы с `ready_song_count > 0`.

## Шаг 7: Сборка и запуск `karaoke-public`

```bash
# Frontend
cd karaoke-public
npm install --frozen-lockfile
npm run lint:check
npm run build

# Запуск / рестарт через deploy/do.sh (karaoke-public — Docker)
cd ../deploy && bash do.sh build_start_public
```

**Ожидание**: сборка без ошибок, контейнер `karaoke-public` обновлён.

## Шаг 8: UI smoke test (в браузере)

| Действие | Ожидание |
|---|---|
| Открыть `http://localhost:8080/zakroma` | Видны плашки авторов (как раньше). Слайдер размера виден сверху. |
| Кликнуть на плашку автора | Открывается `/zakroma/{author_id}` — список песен с группировкой по альбомам. |
| Прокрутить к новой секции «Альбомы автора» | Видны плашки альбомов с обложкой 200×200, годом, названием, «N готовых». |
| Альбом без готовых песен (для гостя) | Скрыт. |
| Кликнуть на плашку альбома | Открывается `/zakroma/{author_id}?album={album_id}` — песни только этого альбома. |
| Хлебная крошка в шапке | Ведёт на `/zakroma/{author_id}/albums` (а не на `/zakroma`). |
| Перейти на `/zakroma/{author_id}/albums` напрямую | Список альбомов + псевдо-плашка «Все песни автора с группировкой по альбомам» (ведёт на `/zakroma/{author_id}` без `?album`). |
| Сдвинуть слайдер размера на 300px | Плашки становятся крупнее. |
| Перезагрузить страницу (F5) | Размер сохранился. |
| Переключиться на «Таблица» | Плашки скрылись, появилась таблица. |
| Зайти в другой браузер (или инкогнито) | Слайдер и переключатель в дефолтных значениях. |

## Шаг 9: Editor smoke test

| Действие | Ожидание |
|---|---|
| Залогиниться как редактор | (если есть) |
| Открыть `/zakroma/{author_id}/albums` | Видны ВСЕ альбомы автора (включая без готовых песен). Подпись плашки: «N песен» (а не «N готовых»). |
| Skip-альбом (если есть в БД) | Скрыт для редактора (как у гостя — это политика безопасности FR-016). |

## Шаг 10: Tracker workflow

```bash
# После прохождения шагов 1-9 — публикация отчёта
source .env.local-tracker && bash tools/tracker.sh add-comment 70 \
  --file specs/356-zakroma-albums-by-author/report.md
bash tools/tracker.sh mark-review 70
```

**Ожидание**: статус задачи → `In review`. PR готов к merge.

## Post-merge: проверка на проде

| Действие | Ожидание |
|---|---|
| `deploy_web.sh` выполнен без ошибок | (если пользователь дал согласие на деплой) |
| На проде открыть `/zakroma/{author_id}/albums` | Плашки альбомов видны. |
| Подождать ≤60с (TTL кеша) | Новые песни отображаются в счётчиках. |
| Sync LOCAL → SERVER на проде | Счётчики согласованы. |

## Известные ограничения

- **legacy `song_album` (string)** — НЕ очищается в этой спеке. Используется в `Zakroma.kt` для legacy группировки. Потенциальная отдельная спека на миграцию.
- **`A-005` «плашка текущего альбома в шапке»** — может быть отложено, если сложно сделать в рамках этой спеки.
- **`tbl_albums.sort_order` (UI-поле)** — НЕ используется в публичной сетке (только year+name). Используется в админке для drag-drop.

## Чек-лист готовности

- [ ] Шаги 1-7 пройдены без ошибок
- [ ] UI smoke test (шаг 8) пройден полностью
- [ ] Editor smoke test (шаг 9) пройден
- [ ] Per-feature документ `docs/features/zakroma-albums-by-author.md` создан (FR-009)
- [ ] ADR `knowledge/adr/local-0007-album-tile-sort-order.md` создан (опционально)
- [ ] `report.md` создан в `specs/356-zakroma-albums-by-author/`
- [ ] Tracker workflow выполнен (add-comment + mark-review)
- [ ] PR открыт и CI 7/7 PASS

## Дополнительно

- Полная спека: [spec.md](spec.md)
- Технические решения: [research.md](research.md)
- Модель данных: [data-model.md](data-model.md)
- API контракт: [contracts/albums-tiles-api.md](contracts/albums-tiles-api.md)
- Прецедент (счётчики): [specs/286-author-song-counts-cache/](../286-author-song-counts-cache/)
- Прецедент (zakroma-tiles): [specs/307-special-authors-zakroma-order/](../307-special-authors-zakroma-order/)