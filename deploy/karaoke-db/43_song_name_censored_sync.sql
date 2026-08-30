-- specs/277-song-name-censored — отдельная миграция для sync-таблицы `tbl_songs_sync`.
-- Основная миграция 42_song_name_censored.sql трогает только `tbl_songs` и её триггер
-- `update_tbl_songs_recordhash()`. Таблица `tbl_songs_sync` используется вручную через
-- Utils.setSongToSyncRemoteTable (формирует INSERT INTO tbl_songs_sync с sync=true и
-- шлёт зашифрованный SQL на /changerecords), а её собственный триггер
-- `update_tbl_songs_sync_recordhash()` тоже должен учитывать колонку — иначе
-- 1) INSERT INTO tbl_songs_sync (...) упадёт "column song_name_censored does not exist",
-- 2) даже если колонка появится, запись её recordhash не будет включать значение.
--
-- Аналогично 42_song_name_censored.sql: ALTER TABLE + пересборка триггера + бэкфилл.
-- На prod и LOCAL применяется вручную (см. AGENTS.md).

-- ==========================================================================================
-- tbl_songs_sync — добавить колонку
-- ==========================================================================================
ALTER TABLE public.tbl_songs_sync ADD COLUMN IF NOT EXISTS song_name_censored VARCHAR(255) DEFAULT '' NOT NULL;

-- ==========================================================================================
-- Пересобрать update_tbl_songs_sync_recordhash() — добавить song_name_censored в md5
-- (по образцу 42_song_name_censored.sql:76-190 для основной таблицы).
-- ==========================================================================================
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
        COALESCE(NEW.song_name_censored, '')
    );
    RETURN NEW;
END;
$$;

-- ==========================================================================================
-- Backfill колонки и recordhash для существующих строк tbl_songs_sync (если есть).
-- Берём song_name_censored из tbl_songs (там бэкфилл уже сделан миграцией 42), чтобы
-- строки sync-таблицы сразу попадали под тот же md5, что и в основной таблице.
-- ==========================================================================================
UPDATE public.tbl_songs_sync AS s
SET song_name_censored = t.song_name_censored
FROM public.tbl_songs AS t
WHERE s.id = t.id AND s.id > 0;

UPDATE public.tbl_songs_sync SET recordhash = md5(
    COALESCE(id::TEXT, '') ||
    COALESCE(song_name, '') ||
    COALESCE(song_author, '') ||
    COALESCE(song_album, '') ||
    COALESCE(publish_date, '') ||
    COALESCE(publish_time, '') ||
    COALESCE(song_year::TEXT, '') ||
    COALESCE(song_track::TEXT, '') ||
    COALESCE(song_tone, '') ||
    COALESCE(song_bpm::TEXT, '') ||
    COALESCE(song_ms::TEXT, '') ||
    COALESCE(file_name, '') ||
    COALESCE(root_folder, '') ||
    COALESCE(id_boosty, '') ||
    COALESCE(id_dzen_lyrics, '') ||
    COALESCE(id_dzen_karaoke, '') ||
    COALESCE(id_dzen_chords, '') ||
    COALESCE(id_status::TEXT, '') ||
    COALESCE(source_text, '') ||
    COALESCE(source_markers, '') ||
    COALESCE(id_vk_lyrics, '') ||
    COALESCE(id_vk_karaoke, '') ||
    COALESCE(id_vk_chords, '') ||
    COALESCE(status_process_lyrics, '') ||
    COALESCE(status_process_karaoke, '') ||
    COALESCE(status_process_chords, '') ||
    COALESCE(id_vk, '') ||
    COALESCE(id_telegram_lyrics, '') ||
    COALESCE(id_telegram_karaoke, '') ||
    COALESCE(id_telegram_chords, '') ||
    COALESCE(tags, '') ||
    COALESCE(result_text, '') ||
    COALESCE(id_boosty_files, '') ||
    COALESCE(result_version::TEXT, '') ||
    COALESCE(id_pl_lyrics, '') ||
    COALESCE(id_pl_karaoke, '') ||
    COALESCE(id_pl_chords, '') ||
    COALESCE(diff_beats::TEXT, '') ||
    COALESCE(id_sponsr, '') ||
    COALESCE(id_dzen_melody, '') ||
    COALESCE(id_vk_melody, '') ||
    COALESCE(status_process_melody, '') ||
    COALESCE(id_telegram_melody, '') ||
    COALESCE(id_pl_melody, '') ||
    COALESCE(index_tabs_variant::TEXT, '') ||
    COALESCE(version_dzen_lyrics::TEXT, '') ||
    COALESCE(version_dzen_karaoke::TEXT, '') ||
    COALESCE(version_dzen_chords::TEXT, '') ||
    COALESCE(version_dzen_melody::TEXT, '') ||
    COALESCE(version_vk_lyrics::TEXT, '') ||
    COALESCE(version_vk_karaoke::TEXT, '') ||
    COALESCE(version_vk_chords::TEXT, '') ||
    COALESCE(version_vk_melody::TEXT, '') ||
    COALESCE(version_telegram_lyrics::TEXT, '') ||
    COALESCE(version_telegram_karaoke::TEXT, '') ||
    COALESCE(version_telegram_chords::TEXT, '') ||
    COALESCE(version_telegram_melody::TEXT, '') ||
    COALESCE(version_pl_lyrics::TEXT, '') ||
    COALESCE(version_pl_karaoke::TEXT, '') ||
    COALESCE(version_pl_chords::TEXT, '') ||
    COALESCE(version_pl_melody::TEXT, '') ||
    COALESCE(version_boosty::TEXT, '') ||
    COALESCE(version_sponsr::TEXT, '') ||
    COALESCE(version_boosty_files::TEXT, '') ||
    COALESCE(rate::TEXT, '') ||
    COALESCE(root_id::TEXT, '') ||
    COALESCE(free::TEXT, '') ||
    COALESCE(exclusive::TEXT, '') ||
    COALESCE(formatted_text_song, '') ||
    COALESCE(formatted_text_tabs, '') ||
    COALESCE(formatted_text_chords, '') ||
    COALESCE(id_max_lyrics, '') ||
    COALESCE(id_max_karaoke, '') ||
    COALESCE(id_max_chords, '') ||
    COALESCE(id_max_melody, '') ||
    COALESCE(version_max_lyrics::TEXT, '') ||
    COALESCE(version_max_karaoke::TEXT, '') ||
    COALESCE(version_max_chords::TEXT, '') ||
    COALESCE(version_max_melody::TEXT, '') ||
    COALESCE(id_dzen_demo, '') ||
    COALESCE(version_dzen_demo::TEXT, '') ||
    COALESCE(id_vk_demo, '') ||
    COALESCE(version_vk_demo::TEXT, '') ||
    COALESCE(id_telegram_demo, '') ||
    COALESCE(version_telegram_demo::TEXT, '') ||
    COALESCE(id_max_demo, '') ||
    COALESCE(version_max_demo::TEXT, '') ||
    COALESCE(song_type, '') ||
    COALESCE(audio_parent_id::TEXT, '') ||
    COALESCE(audio_similarity_percent::TEXT, '') ||
    COALESCE(audio_delta_ms::TEXT, '') ||
    COALESCE(audio_compare_history, '') ||
    COALESCE(player_readiness_flags, '') ||
    COALESCE(song_name_censored, '')
) WHERE id > 0;