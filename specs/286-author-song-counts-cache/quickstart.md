# Quickstart: Валидация фичи `author-song-counts-cache`

**Feature**: 286-author-song-counts-cache
**Date**: 2026-08-31
**Status**: Phase 1 — руководство для ручного прогона

Этот документ описывает **как проверить, что фича работает end-to-end** на LOCAL-БД и после sync — на SERVER-БД. Все шаги — ручные (CI тестов нет, проект полагается на ручную проверку пользователем).

---

## Prerequisites

- Karaoke dev-среда поднята (`karaoke-app` и `karaoke-web` запущены локально или в Docker).
- Доступ к LOCAL-БД через psql: `PGPASSWORD=... psql -h localhost -U ... -d karaoke_local`.
- Доступ к SERVER-БД через psql (или через SSH-туннель): `PGPASSWORD=... psql -h <prod-host> -U ... -d karaoke`.
- Миграция `deploy/karaoke-db/44_author_song_counts.sql` применена на ОБЕИХ БД (см. шаг 1).
- Сборка `karaoke-app:bootJar` и `karaoke-web:bootJar` выполнена после правки `Author.kt`, `PublicApiController.kt`, `ApiController.kt` (см. AGENTS.md чек-лист).

---

## Шаг 1 — Применение миграции

### LOCAL

```bash
PGPASSWORD="..." psql -h localhost -U ... -d karaoke_local \
    -f /home/nsa/Karaoke/deploy/karaoke-db/44_author_song_counts.sql
```

### SERVER (после одобрения пользователем)

```bash
PGPASSWORD="..." psql -h <prod-host> -U ... -d karaoke \
    -f /home/nsa/Karaoke/deploy/karaoke-db/44_author_song_counts.sql
```

### Проверка: миграция применилась без ошибок

```sql
-- На обеих БД:
\d tbl_authors
-- Должны быть колонки ready_songs_count, total_songs_count типа BIGINT NOT NULL DEFAULT 0.

SELECT column_name, data_type, column_default, is_nullable
FROM information_schema.columns
WHERE table_schema = 'public' AND table_name = 'tbl_authors'
  AND column_name IN ('ready_songs_count', 'total_songs_count', 'recordhash')
ORDER BY column_name;
```

**Ожидаемый результат**: 3 строки, обе новые колонки — `bigint`, `0`, `NO`.

### Проверка: backfill сработал

```sql
-- На обеих БД:
SELECT
    COUNT(*) AS total_authors,
    COUNT(*) FILTER (WHERE ready_songs_count IS NULL) AS null_ready,
    COUNT(*) FILTER (WHERE total_songs_count IS NULL) AS null_total
FROM tbl_authors;
```

**Ожидаемый результат**: `total_authors > 0`, `null_ready = 0`, `null_total = 0`.

```sql
-- На обеих БД:
SELECT
    SUM(ready_songs_count) AS sum_ready,
    (SELECT COUNT(*) FROM tbl_songs WHERE id_status >= 6) AS actual_ready
FROM tbl_authors;
```

**Ожидаемый результат**: `sum_ready = actual_ready` (допуск ±1 на race-condition в момент миграции).

### Проверка: триггер создан на LOCAL

```sql
-- Только на LOCAL:
SELECT tgname, tgrelid::regclass, tgenabled
FROM pg_trigger
WHERE tgname = 'trg_tbl_songs_update_author_counts';
```

**Ожидаемый результат**: 1 строка, `tgrelid = tbl_songs`, `tgenabled = 'O'` (enabled).

---

## Шаг 2 — Проверка триггера на INSERT/UPDATE/DELETE

### Тест 2.1 — INSERT готовой песни

```sql
-- Выбираем автора для теста
SELECT id, author, ready_songs_count, total_songs_count
FROM tbl_authors
WHERE skip = false AND ready_songs_count > 0
ORDER BY author LIMIT 1;
-- Допустим, это author='TestAuthor', ready=5, total=7

-- INSERT готовой песни
INSERT INTO tbl_songs (song_author, song_name, id_status, /*...other fields...*/)
VALUES ('TestAuthor', 'Test song ready', 6 /*, ...*/);

-- Проверка
SELECT ready_songs_count, total_songs_count FROM tbl_authors WHERE author = 'TestAuthor';
-- Ожидаемо: ready=6, total=8
```

### Тест 2.2 — INSERT неготовой песни

```sql
INSERT INTO tbl_songs (song_author, song_name, id_status, /*...*/)
VALUES ('TestAuthor', 'Test song notready', 1 /*, ...*/);

SELECT ready_songs_count, total_songs_count FROM tbl_authors WHERE author = 'TestAuthor';
-- Ожидаемо: ready=6, total=9 (ready не изменился)
```

### Тест 2.3 — UPDATE id_status с 1 на 6

```sql
UPDATE tbl_songs SET id_status = 6 WHERE song_name = 'Test song notready';

SELECT ready_songs_count, total_songs_count FROM tbl_authors WHERE author = 'TestAuthor';
-- Ожидаемо: ready=7, total=9 (готовых прибавилось)
```

### Тест 2.4 — UPDATE id_status с 6 на 5

```sql
UPDATE tbl_songs SET id_status = 5 WHERE song_name = 'Test song ready';

SELECT ready_songs_count, total_songs_count FROM tbl_authors WHERE author = 'TestAuthor';
-- Ожидаемо: ready=6, total=9 (готовых убавилось)
```

### Тест 2.5 — UPDATE song_author (перенос)

```sql
UPDATE tbl_songs SET song_author = 'AnotherAuthor' WHERE song_name = 'Test song notready';

SELECT ready_songs_count, total_songs_count FROM tbl_authors WHERE author = 'TestAuthor';
-- Ожидаемо: ready=6, total=8 (одну песню перенесли)

SELECT ready_songs_count, total_songs_count FROM tbl_authors WHERE author = 'AnotherAuthor';
-- Ожидаемо: ready=0, total=1 (новая песня у нового автора)
```

### Тест 2.6 — DELETE

```sql
DELETE FROM tbl_songs WHERE song_name = 'Test song notready';

SELECT ready_songs_count, total_songs_count FROM tbl_authors WHERE author = 'AnotherAuthor';
-- Ожидаемо: ready=0, total=0 (песню удалили)
```

### Тест 2.7 — Skip-автор

```sql
-- Предположим, у нас уже есть skip-автор 'OldAuthor' с одной песней
INSERT INTO tbl_songs (song_author, song_name, id_status, /*...*/)
VALUES ('OldAuthor', 'Skip author test', 6 /*, ...*/);

SELECT ready_songs_count, total_songs_count FROM tbl_authors WHERE author = 'OldAuthor';
-- Ожидаемо: ready=1, total=1 (счётчики обновлены несмотря на skip=true)
-- UI скрывает OldAuthor, но БД остаётся консистентной
```

### Тест 2.8 — Висящая песня

```sql
-- 'Ghost' не существует в tbl_authors
INSERT INTO tbl_songs (song_author, song_name, id_status, /*...*/)
VALUES ('Ghost', 'Phantom song', 6 /*, ...*/);
-- Не должно быть RAISE EXCEPTION

-- Удалить висящую песню
DELETE FROM tbl_songs WHERE song_author = 'Ghost';
-- Не должно быть RAISE EXCEPTION

-- Проверить, что tbl_authors не получила мусорную запись
SELECT COUNT(*) FROM tbl_authors WHERE author = 'Ghost';
-- Ожидаемо: 0
```

---

## Шаг 3 — Проверка `/api/public/authors-tiles`

### Тест 3.1 — Анонимный запрос

```bash
curl -s 'http://localhost:8080/api/public/authors-tiles?scope=main&anonId=test' \
    | jq '.[0:3]'
```

**Ожидаемо**: JSON-массив с 3 объектами, поля `songCount` = `ready_songs_count` для этих авторов.

### Тест 3.2 — Сравнение с предыдущей реализацией

```bash
# Сохранить текущий ответ
curl -s 'http://localhost:8080/api/public/authors-tiles?scope=main&anonId=test' > /tmp/before.json

# Применить фичу (если ещё не применена), подождать пока кэш сбросится (30 мин или вызвать markDirty вручную)

# Запросить снова
curl -s 'http://localhost:8080/api/public/authors-tiles?scope=main&anonId=test' > /tmp/after.json

diff /tmp/before.json /tmp/after.json
```

**Ожидаемо**: пустой diff (или разница только в порядке выдачи, не в числах).

### Тест 3.3 — В логе нет GROUP BY

```bash
# Включить SQL-логирование в karaoke-web (если ещё не включено)
# application.yml: spring.jpa.show-sql=true (или специфичный для HikariCP)

# Сделать 100 запросов
for i in $(seq 1 100); do
    curl -s 'http://localhost:8080/api/public/authors-tiles?scope=main' > /dev/null
done

# Проверить лог
grep -c "group by song_author" /path/to/karaoke-web.log
# Ожидаемо: 0
```

### Тест 3.4 — Кэш сбрасывается через markDirty

```bash
# Изменить статус песни (любой)
psql -c "UPDATE tbl_songs SET id_status = 6 WHERE id = <test-song-id>;"

# Запросить API
curl -s 'http://localhost:8080/api/public/authors-tiles?scope=main' | jq '.[] | select(.author == "TestAuthor").songCount'
# Ожидаемо: обновлённое значение (если кэш сбросился через markDirty)
# Проверить, что markDirty был вызван — лог "[notifyStatsDirty" в karaoke-app.log
```

### Тест 3.5 — Ручной markDirty (если cache не сбрасывается автоматически)

```bash
# Прямой вызов internal endpoint (требует X-Internal-Secret)
curl -X POST 'http://localhost:8080/api/internal/stats/mark-dirty' \
    -H 'X-Internal-Secret: <secret-from-do.env>'
# Ожидаемо: 200 OK

# Следующий запрос /api/public/authors-tiles покажет свежие данные
curl -s 'http://localhost:8080/api/public/authors-tiles?scope=main' | jq '.[] | select(.author == "TestAuthor").songCount'
```

---

## Шаг 4 — Sync LOCAL → SERVER

### Тест 4.1 — Sync счётчиков на SERVER

```bash
# Изменить что-то на LOCAL (например, добавить песню TestAuthor)
# Подождать, пока счётчик обновится в tbl_authors на LOCAL (мгновенно через триггер)

# Запустить sync LOCAL → SERVER
bash /home/nsa/Karaoke/deploy/do.sh sync  # или эквивалентная команда
# (требует одобрения пользователя, см. AGENTS.md)

# Проверить на SERVER
psql -h <prod-host> -U ... -d karaoke \
    -c "SELECT ready_songs_count, total_songs_count FROM tbl_authors WHERE author = 'TestAuthor';"
# Ожидаемо: значения совпадают с LOCAL
```

### Тест 4.2 — recordhash совпадает

```sql
-- На обеих БД:
SELECT recordhash FROM tbl_authors WHERE author = 'TestAuthor';
-- Ожидаемо: одинаковые хэши на LOCAL и SERVER
```

---

## Шаг 5 — Очистка тестовых данных

После прогона тестов желательно убрать тестовые данные:

```sql
-- На LOCAL:
DELETE FROM tbl_songs WHERE song_name IN ('Test song ready', 'Test song notready', 'Skip author test');
-- Висящие 'Ghost' записи уже удалены в тесте 2.8
-- (опционально) DELETE FROM tbl_authors WHERE author = 'OldAuthor' AND skip = true;
```

После удаления `tbl_songs` строк счётчики в `tbl_authors` обновятся автоматически через триггер. Затем sync LOCAL → SERVER прогонит изменения.

---

## Чек-лист «готово к merge»

- [ ] Шаг 1 пройден (миграция применена, триггер есть, backfill корректный)
- [ ] Шаг 2 пройден (все 8 подтестов триггера: insert/update/delete/transfer/skip/ghost)
- [ ] Шаг 3.1-3.3 пройдены (API отвечает, числа совпадают с предыдущей реализацией, GROUP BY в логе отсутствует)
- [ ] Шаг 3.4-3.5 пройдены (cache invalidation работает через `notifyStatsDirty`)
- [ ] Шаг 4.1-4.2 пройдены (sync LOCAL → SERVER прокатил счётчики и recordhash)
- [ ] Шаг 5 выполнен (тестовые данные убраны)
- [ ] LiveDocs обновлены (`docs/features/author-song-counts-cache.md` создан)
- [ ] AGENTS.md чек-лист сборки выполнен:
  - [ ] `./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel`
  - [ ] `./gradlew :karaoke-web:ktlintCheck` + `cd webvue3 && npm run lint` + `cd karaoke-public && npm run lint` (baseline OK, без новых нарушений)
  - [ ] `./gradlew :karaoke-web:bootJar --parallel` (на `nsa-i9`/`nsa` также `:karaoke-app:bootJar`)
  - [ ] `cd webvue3 && npm run build && npm run format:check`
  - [ ] `cd karaoke-public && npm run build && npm run format:check`
  - [ ] `cd deploy && bash do.sh build_webvue3` (если менялся `karaoke-public` или есть кросс-импорты — также `bash do.sh build_public`)

После всех чек-листов — готовность к merge через PR в `master`.