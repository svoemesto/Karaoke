-- ==========================================================================================
-- tbl_songs_sync — колонка album_id: без неё push песни в SYNC падал.
--
-- Аудит #336, BR-533/BR-540 (продолжение), OpenProject #363 и #373 (BR-108).
--
-- ЧТО БЫЛО. `Song.getSqlToInsert(sync = true)` (karaoke-app/.../model/Song.kt) пишет в
-- tbl_songs_sync колонку album_id — она есть в tbl_songs и входит в его recordhash
-- (29_albums.sql:175). В sync-таблице такой колонки не было ни в одной миграции, поэтому
-- ЛЮБОЙ push песни падал на первой же записи батча:
--     ERROR: column "album_id" of relation "tbl_songs_sync" does not exist
-- и ни одна песня не уезжала в sync. До правки BR-108 колонка просто не попадала в INSERT,
-- поэтому падения не было — было хуже: изменение альбома песни не доезжало до SERVER
-- (sync сравнивает строки только по recordhash, а album_id в хэш sync-таблицы не входил).
--
-- ЧТО ДЕЛАЕТ МИГРАЦИЯ
--   1. добавляет колонку album_id (определение — как в tbl_songs);
--   2. пересобирает update_tbl_songs_sync_recordhash(), добавляя album_id в md5 последним
--      слагаемым — порядок исторических слагаемых НЕ меняется, иначе разойдутся хэши на
--      LOCAL и SERVER и diff покажет все строки изменёнными;
--   3. проверяет, что в теле функции нет «голых» имён колонок без префикса NEW.
--      (регресс BR-553: в 50_tbl_songs_free_after_on_air.sql так потерялся префикс в 31 колонке,
--      CREATE FUNCTION проходил, а триггер падал на первой записи);
--   4. переносит значения из tbl_songs и пересчитывает recordhash существующих строк.
--
-- ПОРЯДОК ПРИМЕНЕНИЯ (обязательно): 50 → 52 → 53. После 53 НЕ переприменять 50 (вернёт формулу
-- из 95 колонок) и не переприменять 52 (вернёт функцию без album_id).
--
-- Проверка: \d tbl_songs_sync | grep album_id
--           SELECT count(*) FROM pg_proc WHERE proname='update_tbl_songs_sync_recordhash'
--             AND prosrc LIKE '%album_id%';
-- ==========================================================================================

BEGIN;

-- 1. Колонка album_id (как в tbl_songs)
ALTER TABLE public.tbl_songs_sync
    ADD COLUMN IF NOT EXISTS album_id BIGINT;

-- 2. Функция recordhash: то же тело, что в 52, плюс album_id последним слагаемым
CREATE OR REPLACE FUNCTION public.update_tbl_songs_sync_recordhash() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    NEW.recordhash = md5(
        COALESCE(NEW.id::TEXT, '') ||
        COALESCE(NEW.song_name, '') ||
        COALESCE(NEW.song_author, '') ||
        COALESCE(NEW.song_album, '') ||
        COALESCE(NEW.publish_date, '') ||
        COALESCE(NEW.publish_time, '') ||
        COALESCE(NEW.song_year::TEXT, '') ||
        COALESCE(NEW.song_track::TEXT, '') ||
        COALESCE(NEW.song_tone, '') ||
        COALESCE(NEW.song_bpm::TEXT, '') ||
        COALESCE(NEW.song_ms::TEXT, '') ||
        COALESCE(NEW.file_name, '') ||
        COALESCE(NEW.root_folder, '') ||
        COALESCE(NEW.id_boosty, '') ||
        COALESCE(NEW.id_dzen_lyrics, '') ||
        COALESCE(NEW.id_dzen_karaoke, '') ||
        COALESCE(NEW.id_dzen_chords, '') ||
        COALESCE(NEW.id_status::TEXT, '') ||
        COALESCE(NEW.source_text, '') ||
        COALESCE(NEW.source_markers, '') ||
        COALESCE(NEW.id_vk_lyrics, '') ||
        COALESCE(NEW.id_vk_karaoke, '') ||
        COALESCE(NEW.id_vk_chords, '') ||
        COALESCE(NEW.status_process_lyrics, '') ||
        COALESCE(NEW.status_process_karaoke, '') ||
        COALESCE(NEW.status_process_chords, '') ||
        COALESCE(NEW.id_vk, '') ||
        COALESCE(NEW.id_telegram_lyrics, '') ||
        COALESCE(NEW.id_telegram_karaoke, '') ||
        COALESCE(NEW.id_telegram_chords, '') ||
        COALESCE(NEW.tags, '') ||
        COALESCE(NEW.result_text, '') ||
        COALESCE(NEW.id_boosty_files, '') ||
        COALESCE(NEW.result_version::TEXT, '') ||
        COALESCE(NEW.id_pl_lyrics, '') ||
        COALESCE(NEW.id_pl_karaoke, '') ||
        COALESCE(NEW.id_pl_chords, '') ||
        COALESCE(NEW.diff_beats::TEXT, '') ||
        COALESCE(NEW.id_sponsr, '') ||
        COALESCE(NEW.id_dzen_melody, '') ||
        COALESCE(NEW.id_vk_melody, '') ||
        COALESCE(NEW.status_process_melody, '') ||
        COALESCE(NEW.id_telegram_melody, '') ||
        COALESCE(NEW.id_pl_melody, '') ||
        COALESCE(NEW.index_tabs_variant::TEXT, '') ||
        COALESCE(NEW.version_dzen_lyrics::TEXT, '') ||
        COALESCE(NEW.version_dzen_karaoke::TEXT, '') ||
        COALESCE(NEW.version_dzen_chords::TEXT, '') ||
        COALESCE(NEW.version_dzen_melody::TEXT, '') ||
        COALESCE(NEW.version_vk_lyrics::TEXT, '') ||
        COALESCE(NEW.version_vk_karaoke::TEXT, '') ||
        COALESCE(NEW.version_vk_chords::TEXT, '') ||
        COALESCE(NEW.version_vk_melody::TEXT, '') ||
        COALESCE(NEW.version_telegram_lyrics::TEXT, '') ||
        COALESCE(NEW.version_telegram_karaoke::TEXT, '') ||
        COALESCE(NEW.version_telegram_chords::TEXT, '') ||
        COALESCE(NEW.version_telegram_melody::TEXT, '') ||
        COALESCE(NEW.version_pl_lyrics::TEXT, '') ||
        COALESCE(NEW.version_pl_karaoke::TEXT, '') ||
        COALESCE(NEW.version_pl_chords::TEXT, '') ||
        COALESCE(NEW.version_pl_melody::TEXT, '') ||
        COALESCE(NEW.version_boosty::TEXT, '') ||
        COALESCE(NEW.version_sponsr::TEXT, '') ||
        COALESCE(NEW.version_boosty_files::TEXT, '') ||
        COALESCE(NEW.rate::TEXT, '') ||
        COALESCE(NEW.root_id::TEXT, '') ||
        COALESCE(NEW.free::TEXT, '') ||
        COALESCE(NEW.exclusive::TEXT, '') ||
        COALESCE(NEW.formatted_text_song, '') ||
        COALESCE(NEW.formatted_text_tabs, '') ||
        COALESCE(NEW.formatted_text_chords, '') ||
        COALESCE(NEW.id_max_lyrics, '') ||
        COALESCE(NEW.id_max_karaoke, '') ||
        COALESCE(NEW.id_max_chords, '') ||
        COALESCE(NEW.id_max_melody, '') ||
        COALESCE(NEW.version_max_lyrics::TEXT, '') ||
        COALESCE(NEW.version_max_karaoke::TEXT, '') ||
        COALESCE(NEW.version_max_chords::TEXT, '') ||
        COALESCE(NEW.version_max_melody::TEXT, '') ||
        COALESCE(NEW.id_dzen_demo, '') ||
        COALESCE(NEW.version_dzen_demo::TEXT, '') ||
        COALESCE(NEW.id_vk_demo, '') ||
        COALESCE(NEW.version_vk_demo::TEXT, '') ||
        COALESCE(NEW.id_telegram_demo, '') ||
        COALESCE(NEW.version_telegram_demo::TEXT, '') ||
        COALESCE(NEW.id_max_demo, '') ||
        COALESCE(NEW.version_max_demo::TEXT, '') ||
        COALESCE(NEW.song_type, '') ||
        COALESCE(NEW.audio_parent_id::TEXT, '') ||
        COALESCE(NEW.audio_similarity_percent::TEXT, '') ||
        COALESCE(NEW.audio_delta_ms::TEXT, '') ||
        COALESCE(NEW.audio_compare_history, '') ||
        COALESCE(NEW.player_readiness_flags, '') ||
        COALESCE(NEW.song_name_censored, '') ||
        COALESCE(NEW.free_after_on_air::TEXT, 'false') ||
        COALESCE(NEW.description, '') ||
        COALESCE(NEW.short_description, '') ||
        COALESCE(NEW.warning, '') ||
        COALESCE(NEW.id_tariff::TEXT, '') ||
        COALESCE(NEW.album_id::TEXT, '')
    );
    RETURN NEW;
END;
$$;

-- 3. Контроль: в теле функции не должно быть «голых» имён колонок (BR-553)
DO $$
DECLARE
    v_body      TEXT;
    v_all       INTEGER;
    v_qualified INTEGER;
BEGIN
    SELECT p.prosrc INTO v_body
      FROM pg_proc p
      JOIN pg_namespace n ON n.oid = p.pronamespace
     WHERE p.proname = 'update_tbl_songs_sync_recordhash'
       AND n.nspname = 'public';

    IF v_body IS NULL THEN
        RAISE EXCEPTION 'Функция public.update_tbl_songs_sync_recordhash() не найдена';
    END IF;

    v_all := (length(v_body) - length(replace(v_body, 'COALESCE(', ''))) / length('COALESCE(');
    v_qualified := (length(v_body) - length(replace(v_body, 'COALESCE(NEW.', ''))) / length('COALESCE(NEW.');

    IF v_all <> v_qualified THEN
        RAISE EXCEPTION
            'В update_tbl_songs_sync_recordhash() % из % ссылок на колонки без префикса NEW. — триггер упадёт на первой записи',
            v_all - v_qualified, v_all;
    END IF;
END $$;

-- 4. Перенос значений и пересчёт recordhash существующих строк
UPDATE public.tbl_songs_sync s
   SET album_id = src.album_id
  FROM public.tbl_songs src
 WHERE s.id = src.id
   AND s.album_id IS DISTINCT FROM src.album_id;

UPDATE public.tbl_songs_sync
   SET recordhash = NULL
 WHERE recordhash IS NOT NULL;   -- триггер пересчитает при следующем UPDATE

COMMIT;
