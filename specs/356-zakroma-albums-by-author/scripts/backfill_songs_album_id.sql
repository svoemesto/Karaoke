-- Бэкфилл tbl_songs.album_id из tbl_albums по паре (song_author, song_album)
-- Запускать ПОСЛЕ применения миграции 49_albums_song_counts.sql.
--
-- Что делает:
-- 1. Связывает каждую песню с её реальным альбомом через JOIN
--    tbl_songs → tbl_authors (по song_author) → tbl_albums (по author_id + name)
-- 2. Песни с album_id = NULL, которые можно привязать — получат album_id
-- 3. Песни, у которых НЕТ соответствующего альбома в tbl_albums — пропускаются
--    (no-op, без RAISE EXCEPTION)
-- 4. После UPDATE — триггер trg_tbl_songs_update_album_counts автоматически
--    пересчитает total_song_count и ready_song_count для затронутых альбомов.
--
-- Идемпотентность: WHERE s.album_id IS NULL — повторный запуск не меняет уже привязанные.
-- Безопасно: UPDATE … FROM работает в одной транзакции, на каждой строке отдельно.

BEGIN;

-- 1. Бэкфилл album_id (только для песен без album_id, где есть соответствующий альбом)
UPDATE tbl_songs s
SET album_id = a.id
FROM tbl_authors au, tbl_albums a
WHERE au.author = s.song_author
  AND a.author_id = au.id
  AND a.name = s.song_album
  AND s.album_id IS NULL
  AND s.song_album IS NOT NULL
  AND s.song_album <> ''
  AND a.skip = false;

-- 2. Альтернативный бэкфилл: песни с album_id, но без нормализации по годам
-- (если name совпадает, но год не проверяется — некоторые импорты могли расходиться в годах)
-- Раскомментируйте, если после шага 1 много песен осталось с album_id = NULL:
--
-- UPDATE tbl_songs s
-- SET album_id = a.id
-- FROM tbl_authors au, tbl_albums a
-- WHERE au.author = s.song_author
--   AND a.author_id = au.id
--   AND LOWER(REGEXP_REPLACE(a.name, '[^[:alnum:]]', '', 'g')) =
--       LOWER(REGEXP_REPLACE(s.song_album, '[^[:alnum:]]', '', 'g'))
--   AND s.album_id IS NULL
--   AND s.song_album IS NOT NULL
--   AND s.song_album <> ''
--   AND a.skip = false;

-- 3. Статистика после бэкфилла
SELECT
    'songs linked via backfill' AS metric,
    COUNT(*) AS cnt
FROM tbl_songs
WHERE album_id IS NOT NULL;

SELECT
    'songs still without album_id' AS metric,
    COUNT(*) AS cnt
FROM tbl_songs
WHERE album_id IS NULL AND song_album IS NOT NULL AND song_album <> '';

SELECT
    'albums with total_song_count > 0' AS metric,
    COUNT(*) AS cnt
FROM tbl_albums WHERE total_song_count > 0;

SELECT
    'albums with ready_song_count > 0' AS metric,
    COUNT(*) AS cnt
FROM tbl_albums WHERE ready_song_count > 0;

COMMIT;