-- Миграция 44: оптимизация `tbl_songs` для Pass 297.
-- Контекст: specs/297-fix-tbl-songs-perf/spec.md
--
-- Что делает:
-- 1. Создаёт partial index `tbl_authors_skip_idx` (для JOIN в StatBySong.refreshCache).
-- 2. Создаёт partial index `tbl_songs_tags_idx` (для фильтра SKIP-тегов).
-- 3. Создаёт partial index `tbl_songs_free_partial_idx` (для фильтра free=true).
-- 4. Создаёт MATERIALIZED VIEW `mv_songs_free_now` + REFRESH CONCURRENTLY function.
--
-- Идемпотентна — можно запускать повторно через psql.
--
-- ВАЖНО: применять на КАЖДОЙ БД отдельно (LOCAL + PROD), аналогично 42_song_name_censored.sql.

-- =====================================================================
-- 1. tbl_authors.skip — partial index для JOIN в StatBySong.refreshCache
-- =====================================================================
-- До: WHERE a.skip = false делает seq_scan по tbl_authors (126 строк).
-- После: btree на skip, partial WHERE skip = false (компактный, ~1 KB).
CREATE INDEX IF NOT EXISTS tbl_authors_skip_idx
  ON public.tbl_authors USING btree (skip)
  WHERE skip = false;

-- =====================================================================
-- 2. tbl_songs.tags — для фильтра SKIP-тегов (Pass 293)
-- =====================================================================
-- До: filter 'SKIP' = ANY(string_to_array(upper(coalesce(tags,'')), ' '))
--     использует seq_scan, потому что tags — text без индекса.
-- После: btree на upper(tags), partial WHERE tags IS NOT NULL AND tags <> ''.
CREATE INDEX IF NOT EXISTS tbl_songs_tags_idx
  ON public.tbl_songs USING btree (upper(tags))
  WHERE tags IS NOT NULL AND tags <> '';

-- =====================================================================
-- 3. tbl_songs.free — partial index для фильтра free = true
-- =====================================================================
-- До: WHERE free = true (или свободный фильтр) делает seq_scan.
-- После: btree partial (только true-строки — обычно ~30% от всех).
CREATE INDEX IF NOT EXISTS tbl_songs_free_partial_idx
  ON public.tbl_songs USING btree (free)
  WHERE free = true;

-- =====================================================================
-- 4. MATERIALIZED VIEW mv_songs_free_now
-- =====================================================================
-- До: SELECT count(*) FROM tbl_songs s JOIN tbl_authors a ON a.author = s.song_author
--     WHERE a.skip = false AND ... — 2-5 сек каждый час.
-- После: SELECT count(*) FROM mv_songs_free_now WHERE ... — <50 мс.
-- Обновление: REFRESH MATERIALIZED VIEW CONCURRENTLY (через cron каждые 5 минут).
CREATE MATERIALIZED VIEW IF NOT EXISTS public.mv_songs_free_now AS
SELECT
    s.id,
    s.song_author,
    s.tags,
    s.id_status,
    s.source_markers,
    s.free,
    s.publish_date,
    s.publish_time
FROM public.tbl_songs s
JOIN public.tbl_authors a ON a.author = s.song_author
WHERE a.skip = false
  AND s.id_status >= 6
  AND btrim(coalesce(s.source_markers, '')) != ''
WITH NO DATA;

-- Уникальный индекс для CONCURRENTLY refresh (без него нельзя)
CREATE UNIQUE INDEX IF NOT EXISTS mv_songs_free_now_id_idx
  ON public.mv_songs_free_now USING btree (id);

-- Дополнительные индексы для фильтров внутри MV
CREATE INDEX IF NOT EXISTS mv_songs_free_now_song_author_idx
  ON public.mv_songs_free_now USING btree (song_author);

CREATE INDEX IF NOT EXISTS mv_songs_free_now_free_idx
  ON public.mv_songs_free_now USING btree (free)
  WHERE free = true;

-- Заполняем MV начальными данными (если только что создана, без WITH NO DATA)
REFRESH MATERIALIZED VIEW public.mv_songs_free_now;

-- =====================================================================
-- 5. REFRESH function (вызывается из cron каждые 5 минут)
-- =====================================================================
CREATE OR REPLACE FUNCTION public.refresh_mv_songs_free_now()
RETURNS void AS $$
BEGIN
    REFRESH MATERIALIZED VIEW CONCURRENTLY public.mv_songs_free_now;
END;
$$ LANGUAGE plpgsql;

-- =====================================================================
-- Sanity check: количество строк в MV (для логов миграции)
-- =====================================================================
DO $$
DECLARE
    mv_count bigint;
    base_count bigint;
BEGIN
    SELECT count(*) INTO mv_count FROM public.mv_songs_free_now;
    RAISE NOTICE 'mv_songs_free_now: % строк', mv_count;
    SELECT count(*) INTO base_count FROM public.tbl_songs;
    RAISE NOTICE 'tbl_songs: % строк (всего)', base_count;
END $$;
