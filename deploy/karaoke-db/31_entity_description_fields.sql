-- Доп. поля "Описание"/"Короткое описание"/"Предупреждение" для tbl_authors/tbl_albums/tbl_songs.
-- См. specs/012-entity-description-fields/data-model.md и research.md §3.
--
-- Участвует в LOCAL<->SERVER синхронизации (все три таблицы уже зарегистрированы в
-- SyncRegistry: authors/albums/settings) — новых sync-таргетов/флагов не требуется, только
-- пересборка recordhash-триггеров (Принцип II/III конституции).
--
-- ВАЖНО: применять вручную на КАЖДОЙ БД отдельно (LOCAL + PROD) — миграция сама на сервер
-- не попадает. Порядок деплоя: миграция ДО/вместе с новым karaoke-app/karaoke-web, иначе
-- запись в контроллере падает "column does not exist".

-- ==========================================================================================
-- tbl_authors
-- ==========================================================================================
ALTER TABLE public.tbl_authors ADD COLUMN IF NOT EXISTS description TEXT DEFAULT '' NOT NULL;
ALTER TABLE public.tbl_authors ADD COLUMN IF NOT EXISTS short_description VARCHAR(255) DEFAULT '' NOT NULL;
ALTER TABLE public.tbl_authors ADD COLUMN IF NOT EXISTS warning VARCHAR(255) DEFAULT '' NOT NULL;

CREATE OR REPLACE FUNCTION public.update_tbl_authors_recordhash() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    NEW.recordhash = md5(
                                COALESCE(NEW.id::TEXT, '') ||
                                COALESCE(NEW.author, '') ||
                                COALESCE(NEW.ym_id, '') ||
                                COALESCE(NEW.vk_id, '') ||
                                COALESCE(NEW.last_album_ym, '') ||
                                COALESCE(NEW.last_album_vk, '') ||
                                COALESCE(NEW.last_album_processed, '') ||
                                COALESCE(NEW.watched::TEXT, '') ||
                                COALESCE(NEW.skip::TEXT, '') ||
                                COALESCE(NEW.aliases, '') ||
                                COALESCE(NEW.is_special_order::TEXT, '') ||
                                COALESCE(NEW.description, '') ||
                                COALESCE(NEW.short_description, '') ||
                                COALESCE(NEW.warning, '')
        );
RETURN NEW;
END;
$$;

-- ==========================================================================================
-- tbl_albums
-- ==========================================================================================
ALTER TABLE public.tbl_albums ADD COLUMN IF NOT EXISTS description TEXT DEFAULT '' NOT NULL;
ALTER TABLE public.tbl_albums ADD COLUMN IF NOT EXISTS short_description VARCHAR(255) DEFAULT '' NOT NULL;
ALTER TABLE public.tbl_albums ADD COLUMN IF NOT EXISTS warning VARCHAR(255) DEFAULT '' NOT NULL;

CREATE OR REPLACE FUNCTION public.update_tbl_albums_recordhash() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    NEW.recordhash = md5(
                                COALESCE(NEW.id::TEXT, '') ||
                                COALESCE(NEW.author_id::TEXT, '') ||
                                COALESCE(NEW.year::TEXT, '') ||
                                COALESCE(NEW.name, '') ||
                                COALESCE(NEW.album_type, '') ||
                                COALESCE(NEW.sort_order::TEXT, '') ||
                                COALESCE(NEW.description, '') ||
                                COALESCE(NEW.short_description, '') ||
                                COALESCE(NEW.warning, '')
        );
RETURN NEW;
END;
$$;

-- ==========================================================================================
-- tbl_songs
-- ==========================================================================================
ALTER TABLE public.tbl_songs ADD COLUMN IF NOT EXISTS description TEXT DEFAULT '' NOT NULL;
ALTER TABLE public.tbl_songs ADD COLUMN IF NOT EXISTS short_description VARCHAR(255) DEFAULT '' NOT NULL;
ALTER TABLE public.tbl_songs ADD COLUMN IF NOT EXISTS warning VARCHAR(255) DEFAULT '' NOT NULL;

CREATE OR REPLACE FUNCTION public.update_tbl_songs_recordhash() RETURNS trigger
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
                                COALESCE(NEW.id_tariff::TEXT, '') ||
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
                                COALESCE(NEW.album_id::TEXT, '') ||
                                COALESCE(NEW.description, '') ||
                                COALESCE(NEW.short_description, '') ||
                                COALESCE(NEW.warning, '')
        );
RETURN NEW;
END;
$$;

-- ==========================================================================================
-- Backfill recordhash для существующих строк -- иначе LOCAL/SERVER будут молча расходиться
-- до первого UPDATE каждой строки. НЕ через "SET id = id" (id -- GENERATED ALWAYS AS IDENTITY
-- у tbl_authors/tbl_albums, такую колонку нельзя обновить даже её же значением -- Postgres
-- кидает "column can only be updated to DEFAULT"), а явным пересчётом recordhash -- по образцу
-- 27_author_special_order.sql.
-- ==========================================================================================
UPDATE public.tbl_authors SET recordhash = md5(
    COALESCE(id::TEXT, '') ||
    COALESCE(author, '') ||
    COALESCE(ym_id, '') ||
    COALESCE(vk_id, '') ||
    COALESCE(last_album_ym, '') ||
    COALESCE(last_album_vk, '') ||
    COALESCE(last_album_processed, '') ||
    COALESCE(watched::TEXT, '') ||
    COALESCE(skip::TEXT, '') ||
    COALESCE(aliases, '') ||
    COALESCE(is_special_order::TEXT, '') ||
    COALESCE(description, '') ||
    COALESCE(short_description, '') ||
    COALESCE(warning, '')
) WHERE id > 0;

UPDATE public.tbl_albums SET recordhash = md5(
    COALESCE(id::TEXT, '') ||
    COALESCE(author_id::TEXT, '') ||
    COALESCE(year::TEXT, '') ||
    COALESCE(name, '') ||
    COALESCE(album_type, '') ||
    COALESCE(sort_order::TEXT, '') ||
    COALESCE(description, '') ||
    COALESCE(short_description, '') ||
    COALESCE(warning, '')
) WHERE id > 0;

UPDATE public.tbl_songs SET recordhash = md5(
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
    COALESCE(id_tariff::TEXT, '') ||
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
    COALESCE(album_id::TEXT, '') ||
    COALESCE(description, '') ||
    COALESCE(short_description, '') ||
    COALESCE(warning, '')
) WHERE id > 0;
