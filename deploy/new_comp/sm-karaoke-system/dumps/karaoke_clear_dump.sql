--
-- PostgreSQL database dump
--

\restrict qYdCtYF06Zrldkb4Fo9Mg7VQ3tVdR1GFlK8FxBbEWxBBXDk09RYjiqSEuK9q9bf

-- Dumped from database version 16.14 (Debian 16.14-1.pgdg13+1)
-- Dumped by pg_dump version 16.14 (Debian 16.14-1.pgdg13+1)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: karaoke; Type: DATABASE; Schema: -; Owner: -
--

CREATE DATABASE karaoke WITH TEMPLATE = template0 ENCODING = 'UTF8' LOCALE_PROVIDER = libc LOCALE = 'en_US.utf8';


\unrestrict qYdCtYF06Zrldkb4Fo9Mg7VQ3tVdR1GFlK8FxBbEWxBBXDk09RYjiqSEuK9q9bf
\connect karaoke
\restrict qYdCtYF06Zrldkb4Fo9Mg7VQ3tVdR1GFlK8FxBbEWxBBXDk09RYjiqSEuK9q9bf

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: update_last_updated(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.update_last_updated() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    NEW.last_update = NOW();
    RETURN NEW;
END;
$$;


--
-- Name: update_tbl_albums_recordhash(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.update_tbl_albums_recordhash() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    NEW.recordhash = md5(
                                COALESCE(NEW.id::TEXT, '') ||
                                COALESCE(NEW.author_id::TEXT, '') ||
                                COALESCE(NEW.year::TEXT, '') ||
                                COALESCE(NEW.name, '') ||
                                COALESCE(NEW.album_type, '') ||
                                COALESCE(NEW.sort_order::TEXT, '')
        );
RETURN NEW;
END;
$$;


--
-- Name: update_tbl_authors_recordhash(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.update_tbl_authors_recordhash() RETURNS trigger
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
        COALESCE(NEW.aliases, '')
    );
    RETURN NEW;
END;
$$;


--
-- Name: update_tbl_dictionaries_recordhash(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.update_tbl_dictionaries_recordhash() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    NEW.recordhash = md5(
                                COALESCE(NEW.id::TEXT, '') ||
                                COALESCE(NEW.dict_name, '') ||
                                COALESCE(NEW.dict_value, '')
        );
RETURN NEW;
END;
$$;


--
-- Name: update_tbl_events_recordhash(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.update_tbl_events_recordhash() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    NEW.recordhash = md5(
        COALESCE(NEW.id::TEXT, '') ||
        COALESCE(NEW.event_type, '') ||
        COALESCE(NEW.rest_name, '') ||
        COALESCE(NEW.rest_parameters, '') ||
        COALESCE(NEW.link_type, '') ||
        COALESCE(NEW.link_name, '') ||
        COALESCE(NEW.song_id::TEXT, '') ||
        COALESCE(NEW.song_version, '') ||
        COALESCE(NEW.referer, '') ||
        COALESCE(NEW.client_ip, '') ||
        COALESCE(NEW.anon_id, '') ||
        NEW.site_user_id::TEXT ||
        COALESCE(NEW.user_agent, '')
    );
    RETURN NEW;
END;
$$;


--
-- Name: update_tbl_listening_history_recordhash(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.update_tbl_listening_history_recordhash() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    NEW.recordhash = md5(
                                COALESCE(NEW.id::TEXT, '') ||
                                COALESCE(NEW.site_user_id::TEXT, '') ||
                                COALESCE(NEW.song_id::TEXT, '') ||
                                COALESCE(NEW.play_count::TEXT, '') ||
                                COALESCE(NEW.last_played_at::TEXT, '')
        );
RETURN NEW;
END;
$$;


--
-- Name: update_tbl_news_recordhash(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.update_tbl_news_recordhash() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    NEW.recordhash = md5(
                                COALESCE(NEW.id::TEXT, '') ||
                                COALESCE(NEW.title, '') ||
                                COALESCE(NEW.body, '') ||
                                COALESCE(NEW.category, '') ||
                                COALESCE(NEW.link, '') ||
                                COALESCE(NEW.publish_at::TEXT, '')
        );
RETURN NEW;
END;
$$;


--
-- Name: update_tbl_pictures_recordhash(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.update_tbl_pictures_recordhash() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    NEW.recordhash = md5(
                        COALESCE(NEW.id::TEXT, '') ||
                        COALESCE(NEW.picture_name, '') ||
                        COALESCE(NEW.picture_full, '') ||
                        COALESCE(NEW.picture_preview, '')
        );
    RETURN NEW;
END;
$$;


--
-- Name: update_tbl_pictures_sync_recordhash(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.update_tbl_pictures_sync_recordhash() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    NEW.recordhash = md5(
        COALESCE(NEW.id::TEXT, '') ||
        COALESCE(NEW.picture_name, '') ||
        COALESCE(NEW.picture_full, '') ||
        COALESCE(NEW.picture_preview, '')
    );
    RETURN NEW;
END;
$$;


--
-- Name: update_tbl_price_tariffs_recordhash(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.update_tbl_price_tariffs_recordhash() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    NEW.recordhash = md5(
                                COALESCE(NEW.id::TEXT, '') ||
                                COALESCE(NEW.scope, '') ||
                                COALESCE(NEW.name, '') ||
                                COALESCE(NEW.price_rub::TEXT, '') ||
                                COALESCE(NEW.period_days::TEXT, '') ||
                                COALESCE(NEW.is_active::TEXT, '') ||
                                COALESCE(NEW.is_default::TEXT, '') ||
                                COALESCE(NEW.sort_order::TEXT, '')
        );
RETURN NEW;
END;
$$;


--
-- Name: update_tbl_processes_recordhash(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.update_tbl_processes_recordhash() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    NEW.recordhash = md5(
                                                                    COALESCE(NEW.id::TEXT, '') ||
                                                                    COALESCE(NEW.process_name, '') ||
                                                                    COALESCE(NEW.process_status, '') ||
                                                                    COALESCE(NEW.process_order::TEXT, '') ||
                                                                    COALESCE(NEW.process_priority::TEXT, '') ||
                                                                    COALESCE(NEW.process_command, '') ||
                                                                    COALESCE(NEW.process_args, '') ||
                                                                    COALESCE(NEW.process_description, '') ||
                                                                    COALESCE(NEW.settings_id::TEXT, '') ||
                                                                    COALESCE(NEW.process_type, '') ||
                                                                    COALESCE(NEW.process_start::TEXT, '') ||
                                                                    COALESCE(NEW.process_end::TEXT, '') ||
                                                                    COALESCE(NEW.process_prioritet::TEXT, '') ||
                                                                    COALESCE(NEW.without_control::TEXT, '') ||
                                                                    COALESCE(NEW.thread_id::TEXT, '')
        );
    RETURN NEW;
END;
$$;


--
-- Name: update_tbl_promo_rules_recordhash(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.update_tbl_promo_rules_recordhash() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    NEW.recordhash = md5(
                                COALESCE(NEW.id::TEXT, '') ||
                                COALESCE(NEW.name, '') ||
                                COALESCE(NEW.type, '') ||
                                COALESCE(NEW.params_json, '') ||
                                COALESCE(NEW.applies_to, '') ||
                                COALESCE(NEW.is_active::TEXT, '') ||
                                COALESCE(NEW.valid_from::TEXT, '') ||
                                COALESCE(NEW.valid_to::TEXT, '') ||
                                COALESCE(NEW.priority::TEXT, '')
        );
RETURN NEW;
END;
$$;


--
-- Name: update_tbl_search_async_recordhash(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.update_tbl_search_async_recordhash() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    NEW.recordhash = md5(
        COALESCE(NEW.id::TEXT, '') ||
        COALESCE(NEW.song_id::TEXT, '') ||
        COALESCE(NEW.url, '') ||
        COALESCE(NEW.iam_token, '') ||
        COALESCE(NEW.query, '') ||
        COALESCE(NEW.body, '') ||
        COALESCE(NEW.response_format, '') ||
        COALESCE(NEW.operation_id, '') ||
        COALESCE(NEW.done::TEXT, '') ||
        COALESCE(NEW.raw_data, '') ||
        COALESCE(NEW.last_requested_at::TEXT, '')
    );
    RETURN NEW;
END;
$$;


--
-- Name: update_tbl_search_results_recordhash(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.update_tbl_search_results_recordhash() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    NEW.recordhash = md5(
        COALESCE(NEW.id::TEXT, '') ||
        COALESCE(NEW.search_async_id::TEXT, '') ||
        COALESCE(NEW.song_id::TEXT, '') ||
        COALESCE(NEW.url, '') ||
        COALESCE(NEW.html, '') ||
        COALESCE(NEW.text, '') ||
        COALESCE(NEW.wrong_result::TEXT, '')
    );
    RETURN NEW;
END;
$$;


--
-- Name: update_tbl_site_chat_messages_recordhash(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.update_tbl_site_chat_messages_recordhash() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    NEW.recordhash = md5(
                                COALESCE(NEW.id::TEXT, '') ||
                                COALESCE(NEW.site_user_id::TEXT, '') ||
                                COALESCE(NEW.is_from_author::TEXT, '') ||
                                COALESCE(NEW.body, '') ||
                                COALESCE(NEW.is_read::TEXT, '')
        );
RETURN NEW;
END;
$$;


--
-- Name: update_tbl_site_playlist_items_recordhash(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.update_tbl_site_playlist_items_recordhash() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    NEW.recordhash = md5(
                                COALESCE(NEW.id::TEXT, '') ||
                                COALESCE(NEW.playlist_id::TEXT, '') ||
                                COALESCE(NEW.song_id::TEXT, '') ||
                                COALESCE(NEW.position::TEXT, '') ||
                                COALESCE(NEW.muted::TEXT, '')
        );
RETURN NEW;
END;
$$;


--
-- Name: update_tbl_site_playlists_recordhash(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.update_tbl_site_playlists_recordhash() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    NEW.recordhash = md5(
                                COALESCE(NEW.id::TEXT, '') ||
                                COALESCE(NEW.owner_id::TEXT, '') ||
                                COALESCE(NEW.name, '') ||
                                COALESCE(NEW.is_favorites::TEXT, '') ||
                                COALESCE(NEW.sort_order::TEXT, '') ||
                                COALESCE(NEW.continuous::TEXT, '') ||
                                COALESCE(NEW.repeat_mode, '') ||
                                COALESCE(NEW.shuffle::TEXT, '')
        );
RETURN NEW;
END;
$$;


--
-- Name: update_tbl_site_users_recordhash(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.update_tbl_site_users_recordhash() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    NEW.recordhash = md5(
                                COALESCE(NEW.id::TEXT, '') ||
                                COALESCE(NEW.email, '') ||
                                COALESCE(NEW.password_hash, '') ||
                                COALESCE(NEW.display_name, '') ||
                                COALESCE(NEW.sponsr_uid, '') ||
                                COALESCE(NEW.is_premium::TEXT, '') ||
                                COALESCE(NEW.is_permanent_premium::TEXT, '') ||
                                COALESCE(NEW.is_banned::TEXT, '') ||
                                COALESCE(NEW.ban_reason, '') ||
                                COALESCE(NEW.max_favorites::TEXT, '') ||
                                COALESCE(NEW.max_playlists::TEXT, '') ||
                                COALESCE(NEW.max_playlist_items::TEXT, '') ||
                                COALESCE(NEW.is_editor::TEXT, '') ||
                                COALESCE(NEW.sponsr_premium_until::TEXT, '') ||
                                COALESCE(NEW.site_premium_until::TEXT, '') ||
                                COALESCE(NEW.welcome_message_sent::TEXT, '')
        );
RETURN NEW;
END;
$$;


--
-- Name: update_tbl_song_assignment_drafts_recordhash(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.update_tbl_song_assignment_drafts_recordhash() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    NEW.recordhash = md5(
                                COALESCE(NEW.id::TEXT, '') ||
                                COALESCE(NEW.assignment_id::TEXT, '') ||
                                COALESCE(NEW.assignee_id::TEXT, '') ||
                                COALESCE(NEW.edited_source_text, '') ||
                                COALESCE(NEW.edited_markers, '') ||
                                COALESCE(NEW.user_status, '') ||
                                COALESCE(NEW.submitted_at::TEXT, '')
        );
RETURN NEW;
END;
$$;


--
-- Name: update_tbl_song_assignments_recordhash(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.update_tbl_song_assignments_recordhash() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    NEW.recordhash = md5(
                                COALESCE(NEW.id::TEXT, '') ||
                                COALESCE(NEW.assignee_id::TEXT, '') ||
                                COALESCE(NEW.song_id::TEXT, '') ||
                                COALESCE(NEW.voice::TEXT, '') ||
                                COALESCE(NEW.admin_status, '') ||
                                COALESCE(NEW.review_comment, '') ||
                                COALESCE(NEW.assigned_by::TEXT, '') ||
                                COALESCE(NEW.reviewed_at::TEXT, '')
        );
RETURN NEW;
END;
$$;


--
-- Name: update_tbl_song_authors_recordhash(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.update_tbl_song_authors_recordhash() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    NEW.recordhash = md5(
                                COALESCE(NEW.id::TEXT, '') ||
                                COALESCE(NEW.song_id::TEXT, '') ||
                                COALESCE(NEW.author_id::TEXT, '')
        );
RETURN NEW;
END;
$$;


--
-- Name: update_tbl_songs_recordhash(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.update_tbl_songs_recordhash() RETURNS trigger
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
                                COALESCE(NEW.album_id::TEXT, '')
        );
RETURN NEW;
END;
$$;


--
-- Name: update_tbl_songs_sync_recordhash(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.update_tbl_songs_sync_recordhash() RETURNS trigger
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
        COALESCE(NEW.player_readiness_flags, '')
    );
    RETURN NEW;
END;
$$;


--
-- Name: update_tbl_stem_jobs_recordhash(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.update_tbl_stem_jobs_recordhash() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    NEW.recordhash = md5(
                                COALESCE(NEW.id::TEXT, '') ||
                                COALESCE(NEW.site_user_id::TEXT, '') ||
                                COALESCE(NEW.mode, '') ||
                                COALESCE(NEW.status, '') ||
                                COALESCE(NEW.original_file_name, '') ||
                                COALESCE(NEW.original_ext, '') ||
                                COALESCE(NEW.file_size_bytes::TEXT, '') ||
                                COALESCE(NEW.error_message, '') ||
                                COALESCE(NEW.started_at::TEXT, '') ||
                                COALESCE(NEW.finished_at::TEXT, '') ||
                                COALESCE(NEW.expires_at::TEXT, '') ||
                                COALESCE(NEW.delete_requested::TEXT, '')
        );
RETURN NEW;
END;
$$;


--
-- Name: update_tbl_subscriptions_recordhash(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.update_tbl_subscriptions_recordhash() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    NEW.recordhash = md5(
                                COALESCE(NEW.id::TEXT, '') ||
                                COALESCE(NEW.site_user_id::TEXT, '') ||
                                COALESCE(NEW.scope, '') ||
                                COALESCE(NEW.id_song::TEXT, '') ||
                                COALESCE(NEW.tariff_id::TEXT, '') ||
                                COALESCE(NEW.period_days::TEXT, '') ||
                                COALESCE(NEW.base_price::TEXT, '') ||
                                COALESCE(NEW.discount::TEXT, '') ||
                                COALESCE(NEW.final_price::TEXT, '') ||
                                COALESCE(NEW.promo_applied, '') ||
                                COALESCE(NEW.status, '') ||
                                COALESCE(NEW.yookassa_payment_id, '') ||
                                COALESCE(NEW.auto_renew::TEXT, '') ||
                                COALESCE(NEW.yookassa_payment_method_id, '') ||
                                COALESCE(NEW.paid_at::TEXT, '') ||
                                COALESCE(NEW.order_id, '')
        );
RETURN NEW;
END;
$$;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: tbl_albums; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tbl_albums (
    id integer NOT NULL,
    author_id integer NOT NULL,
    year integer DEFAULT 0 NOT NULL,
    name character varying(255) NOT NULL,
    album_type character varying(20) DEFAULT 'studio'::character varying NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    recordhash character varying(32)
);


--
-- Name: tbl_albums_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.tbl_albums ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.tbl_albums_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: tbl_authors; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tbl_authors (
    id integer NOT NULL,
    author character varying(255),
    ym_id character varying(255),
    last_album_ym character varying(255),
    last_album_processed character varying(255),
    watched boolean DEFAULT true,
    skip boolean DEFAULT false,
    recordhash character varying(32),
    vk_id character varying(255) DEFAULT ''::character varying NOT NULL,
    last_album_vk character varying(255) DEFAULT ''::character varying NOT NULL,
    aliases text DEFAULT ''::text NOT NULL
);


--
-- Name: tbl_authors_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.tbl_authors ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.tbl_authors_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: tbl_cart_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tbl_cart_items (
    id integer NOT NULL,
    site_user_id integer NOT NULL,
    id_song bigint NOT NULL,
    added_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: tbl_cart_items_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.tbl_cart_items ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.tbl_cart_items_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: tbl_dictionaries; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tbl_dictionaries (
    id integer NOT NULL,
    dict_name character varying(255) NOT NULL,
    dict_value text NOT NULL,
    recordhash character varying(32)
);


--
-- Name: tbl_dictionaries_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.tbl_dictionaries ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.tbl_dictionaries_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: tbl_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tbl_events (
    id integer NOT NULL,
    event_type character varying(255),
    rest_name character varying(255),
    rest_parameters text,
    link_type character varying(255),
    link_name character varying(255),
    song_id integer,
    song_version character varying(255),
    last_update timestamp without time zone DEFAULT now(),
    referer text,
    recordhash character varying(32),
    client_ip character varying(64),
    anon_id character varying(64),
    site_user_id bigint DEFAULT 0 NOT NULL,
    user_agent text
);


--
-- Name: tbl_events_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.tbl_events ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.tbl_events_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: tbl_ip_country; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tbl_ip_country (
    ip character varying(64) NOT NULL,
    country character varying(8),
    resolved_at timestamp without time zone DEFAULT now()
);


--
-- Name: tbl_listening_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tbl_listening_history (
    id integer NOT NULL,
    site_user_id integer NOT NULL,
    song_id bigint NOT NULL,
    play_count integer DEFAULT 1 NOT NULL,
    last_played_at timestamp without time zone DEFAULT now() NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    last_update timestamp without time zone DEFAULT now(),
    recordhash character varying(32)
);


--
-- Name: tbl_listening_history_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.tbl_listening_history ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.tbl_listening_history_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: tbl_news; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tbl_news (
    id integer NOT NULL,
    title character varying(500) NOT NULL,
    body text NOT NULL,
    category character varying(50) DEFAULT 'general'::character varying NOT NULL,
    link character varying(1000),
    publish_at timestamp without time zone,
    created_at timestamp without time zone,
    recordhash character varying(32)
);


--
-- Name: tbl_news_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.tbl_news ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.tbl_news_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: tbl_pictures; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tbl_pictures (
    id integer NOT NULL,
    picture_name character varying(255),
    picture_full text,
    picture_preview text,
    recordhash character varying(32)
);


--
-- Name: tbl_pictures_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.tbl_pictures ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.tbl_pictures_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: tbl_pictures_sync; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tbl_pictures_sync (
    id integer NOT NULL,
    picture_name character varying(255),
    picture_full text,
    picture_preview text,
    recordhash character varying(32)
);


--
-- Name: tbl_pictures_sync_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.tbl_pictures_sync ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.tbl_pictures_sync_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: tbl_price_tariffs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tbl_price_tariffs (
    id integer NOT NULL,
    scope character varying(16) NOT NULL,
    name character varying(255) DEFAULT ''::character varying NOT NULL,
    price_rub numeric(10,2) DEFAULT 0 NOT NULL,
    period_days integer DEFAULT 0 NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    is_default boolean DEFAULT false NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    last_update timestamp without time zone DEFAULT now(),
    recordhash character varying(32)
);


--
-- Name: tbl_price_tariffs_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.tbl_price_tariffs ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.tbl_price_tariffs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: tbl_processes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tbl_processes (
    id integer NOT NULL,
    process_name character varying(255),
    process_status character varying(255),
    process_order integer,
    process_priority integer DEFAULT 1,
    process_command character varying(255),
    process_args text,
    process_description text,
    settings_id integer,
    process_type character varying(255),
    process_start timestamp without time zone,
    process_end timestamp without time zone,
    last_update timestamp without time zone,
    process_prioritet integer DEFAULT 0,
    process_start_str character varying,
    process_end_str character varying,
    process_percentage integer,
    process_percentage_str character varying,
    process_time_passed_ms integer,
    process_time_passed_str character varying,
    process_time_left_ms integer,
    process_time_left_str character varying,
    without_control boolean DEFAULT false,
    thread_id integer DEFAULT 0 NOT NULL,
    recordhash character varying(32),
    process_envs text DEFAULT ''::text NOT NULL
);


--
-- Name: tbl_processes_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.tbl_processes_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: tbl_processes_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.tbl_processes_id_seq OWNED BY public.tbl_processes.id;


--
-- Name: tbl_promo_rules; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tbl_promo_rules (
    id integer NOT NULL,
    name character varying(255) DEFAULT ''::character varying NOT NULL,
    type character varying(32) NOT NULL,
    params_json text DEFAULT '{}'::text NOT NULL,
    applies_to character varying(16) DEFAULT 'BOTH'::character varying NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    valid_from timestamp without time zone,
    valid_to timestamp without time zone,
    priority integer DEFAULT 0 NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    last_update timestamp without time zone DEFAULT now(),
    recordhash character varying(32)
);


--
-- Name: tbl_promo_rules_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.tbl_promo_rules ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.tbl_promo_rules_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: tbl_public_settings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tbl_public_settings (
    key character varying(255) NOT NULL,
    value text DEFAULT ''::text NOT NULL,
    description character varying(1024) DEFAULT ''::character varying NOT NULL,
    last_update timestamp without time zone DEFAULT now()
);


--
-- Name: tbl_search_async; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tbl_search_async (
    id integer NOT NULL,
    song_id integer,
    url character varying(255),
    iam_token text,
    query text,
    body text,
    response_format character varying(255) DEFAULT 'FORMAT_XML'::character varying,
    operation_id character varying(255),
    done boolean DEFAULT false,
    raw_data text,
    created_at timestamp without time zone DEFAULT now(),
    last_requested_at timestamp without time zone DEFAULT now(),
    last_update timestamp without time zone DEFAULT now(),
    recordhash character varying(32)
);


--
-- Name: tbl_search_async_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.tbl_search_async ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.tbl_search_async_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: tbl_search_results; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tbl_search_results (
    id integer NOT NULL,
    search_async_id integer,
    song_id integer,
    url text,
    html text,
    text text,
    wrong_result boolean DEFAULT false,
    last_update timestamp without time zone DEFAULT now(),
    recordhash character varying(32)
);


--
-- Name: tbl_search_results_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.tbl_search_results ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.tbl_search_results_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: tbl_site_chat_messages; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tbl_site_chat_messages (
    id integer NOT NULL,
    site_user_id integer NOT NULL,
    is_from_author boolean DEFAULT false NOT NULL,
    body text NOT NULL,
    is_read boolean DEFAULT false NOT NULL,
    created_at timestamp without time zone,
    recordhash character varying(32)
);


--
-- Name: tbl_site_chat_messages_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.tbl_site_chat_messages ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.tbl_site_chat_messages_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: tbl_site_playlist_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tbl_site_playlist_items (
    id integer NOT NULL,
    playlist_id integer NOT NULL,
    song_id bigint NOT NULL,
    "position" integer DEFAULT 0 NOT NULL,
    muted boolean DEFAULT false NOT NULL,
    added_at timestamp without time zone DEFAULT now() NOT NULL,
    last_update timestamp without time zone DEFAULT now(),
    recordhash character varying(32)
);


--
-- Name: tbl_site_playlist_items_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.tbl_site_playlist_items ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.tbl_site_playlist_items_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: tbl_site_playlists; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tbl_site_playlists (
    id integer NOT NULL,
    owner_id integer NOT NULL,
    name character varying(255) DEFAULT ''::character varying NOT NULL,
    is_favorites boolean DEFAULT false NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    continuous boolean DEFAULT true NOT NULL,
    repeat_mode character varying(8) DEFAULT 'none'::character varying NOT NULL,
    shuffle boolean DEFAULT false NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    last_update timestamp without time zone DEFAULT now(),
    recordhash character varying(32)
);


--
-- Name: tbl_site_playlists_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.tbl_site_playlists ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.tbl_site_playlists_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: tbl_site_user_tokens; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tbl_site_user_tokens (
    id integer NOT NULL,
    site_user_id integer NOT NULL,
    token character varying(64) NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    expires_at timestamp without time zone NOT NULL,
    last_used_at timestamp without time zone,
    revoked boolean DEFAULT false NOT NULL
);


--
-- Name: tbl_site_user_tokens_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.tbl_site_user_tokens ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.tbl_site_user_tokens_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: tbl_site_users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tbl_site_users (
    id integer NOT NULL,
    email character varying(255) NOT NULL,
    password_hash character varying(255) NOT NULL,
    display_name character varying(255) DEFAULT ''::character varying NOT NULL,
    sponsr_uid character varying(64) DEFAULT ''::character varying NOT NULL,
    is_premium boolean DEFAULT false NOT NULL,
    is_banned boolean DEFAULT false NOT NULL,
    ban_reason character varying(1024) DEFAULT ''::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    last_login_at timestamp without time zone DEFAULT now() NOT NULL,
    last_update timestamp without time zone DEFAULT now(),
    recordhash character varying(32),
    is_permanent_premium boolean DEFAULT false NOT NULL,
    max_favorites integer DEFAULT 0 NOT NULL,
    max_playlists integer DEFAULT 0 NOT NULL,
    max_playlist_items integer DEFAULT 0 NOT NULL,
    is_editor boolean DEFAULT false NOT NULL,
    sponsr_premium_until timestamp without time zone,
    site_premium_until timestamp without time zone,
    personal_discount_percent numeric(5,2) DEFAULT 0 NOT NULL,
    welcome_message_sent boolean DEFAULT false NOT NULL
);


--
-- Name: tbl_site_users_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.tbl_site_users ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.tbl_site_users_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: tbl_song_assignment_drafts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tbl_song_assignment_drafts (
    id integer NOT NULL,
    assignment_id bigint NOT NULL,
    assignee_id integer NOT NULL,
    edited_source_text text DEFAULT ''::text NOT NULL,
    edited_markers text DEFAULT '[]'::text NOT NULL,
    user_status character varying(16) DEFAULT 'in_progress'::character varying NOT NULL,
    submitted_at timestamp without time zone,
    last_update timestamp without time zone DEFAULT now(),
    recordhash character varying(32)
);


--
-- Name: tbl_song_assignment_drafts_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.tbl_song_assignment_drafts ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.tbl_song_assignment_drafts_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: tbl_song_assignments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tbl_song_assignments (
    id integer NOT NULL,
    assignee_id integer NOT NULL,
    song_id bigint NOT NULL,
    voice integer DEFAULT 0 NOT NULL,
    admin_status character varying(16) DEFAULT 'open'::character varying NOT NULL,
    review_comment text DEFAULT ''::text NOT NULL,
    assigned_by bigint DEFAULT 0 NOT NULL,
    assigned_at timestamp without time zone DEFAULT now() NOT NULL,
    reviewed_at timestamp without time zone,
    last_update timestamp without time zone DEFAULT now(),
    recordhash character varying(32)
);


--
-- Name: tbl_song_assignments_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.tbl_song_assignments ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.tbl_song_assignments_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: tbl_song_authors; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tbl_song_authors (
    id integer NOT NULL,
    song_id integer NOT NULL,
    author_id integer NOT NULL,
    recordhash character varying(32)
);


--
-- Name: tbl_song_authors_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.tbl_song_authors ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.tbl_song_authors_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: tbl_songs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tbl_songs (
    id integer NOT NULL,
    song_name character varying(255),
    song_author character varying(255),
    song_album character varying(255),
    publish_date character varying(8),
    publish_time character varying(5),
    song_year integer,
    song_track integer,
    song_tone character varying(10),
    song_bpm integer,
    song_ms integer,
    file_name character varying(255),
    root_folder character varying(255),
    id_boosty character varying(40),
    id_dzen_lyrics character varying(40),
    id_dzen_karaoke character varying(40),
    id_dzen_chords character varying(40),
    id_status integer DEFAULT 0,
    source_text text,
    source_markers text,
    id_vk_lyrics character varying(20),
    id_vk_karaoke character varying(20),
    id_vk_chords character varying(20),
    status_process_lyrics character varying(20),
    status_process_karaoke character varying(20),
    status_process_chords character varying(20),
    id_vk character varying(20),
    id_telegram_lyrics character varying(7),
    id_telegram_karaoke character varying(7),
    id_telegram_chords character varying(7),
    tags text,
    last_update timestamp without time zone,
    result_text text,
    id_boosty_files character varying(40),
    result_version integer DEFAULT 0,
    id_pl_lyrics character varying(20) DEFAULT ''::character varying,
    id_pl_karaoke character varying(20) DEFAULT ''::character varying,
    id_pl_chords character varying(20) DEFAULT ''::character varying,
    diff_beats integer DEFAULT 0,
    id_sponsr character varying(7) DEFAULT ''::character varying,
    id_dzen_melody character varying(40) DEFAULT ''::character varying,
    id_vk_melody character varying(20) DEFAULT ''::character varying,
    status_process_melody character varying(20) DEFAULT ''::character varying,
    id_telegram_melody character varying(7) DEFAULT ''::character varying,
    id_pl_melody character varying(20) DEFAULT ''::character varying,
    index_tabs_variant integer DEFAULT 0,
    version_dzen_lyrics integer DEFAULT 0,
    version_dzen_karaoke integer DEFAULT 0,
    version_dzen_chords integer DEFAULT 0,
    version_dzen_melody integer DEFAULT 0,
    version_vk_lyrics integer DEFAULT 0,
    version_vk_karaoke integer DEFAULT 0,
    version_vk_chords integer DEFAULT 0,
    version_vk_melody integer DEFAULT 0,
    version_telegram_lyrics integer DEFAULT 0,
    version_telegram_karaoke integer DEFAULT 0,
    version_telegram_chords integer DEFAULT 0,
    version_telegram_melody integer DEFAULT 0,
    version_pl_lyrics integer DEFAULT 0,
    version_pl_karaoke integer DEFAULT 0,
    version_pl_chords integer DEFAULT 0,
    version_pl_melody integer DEFAULT 0,
    version_boosty integer DEFAULT 0,
    version_sponsr integer DEFAULT 0,
    version_boosty_files integer DEFAULT 0,
    rate integer DEFAULT 0,
    recordhash character varying(32),
    formatted_text_song text DEFAULT ''::text NOT NULL,
    formatted_text_tabs text DEFAULT ''::text NOT NULL,
    formatted_text_chords text DEFAULT ''::text NOT NULL,
    root_id integer DEFAULT 0 NOT NULL,
    exclusive boolean DEFAULT false NOT NULL,
    free boolean DEFAULT false NOT NULL,
    id_max_lyrics character varying(20) DEFAULT ''::character varying,
    id_max_karaoke character varying(20) DEFAULT ''::character varying,
    id_max_chords character varying(20) DEFAULT ''::character varying,
    id_max_melody character varying(20) DEFAULT ''::character varying,
    version_max_lyrics integer DEFAULT 0,
    version_max_karaoke integer DEFAULT 0,
    version_max_chords integer DEFAULT 0,
    version_max_melody integer DEFAULT 0,
    id_tariff integer DEFAULT 0 NOT NULL,
    status_process_demo character varying(255) DEFAULT ''::character varying NOT NULL,
    id_dzen_demo character varying(40) DEFAULT ''::character varying,
    version_dzen_demo integer DEFAULT 0,
    id_vk_demo character varying(20) DEFAULT ''::character varying,
    version_vk_demo integer DEFAULT 0,
    id_telegram_demo character varying(7) DEFAULT ''::character varying,
    version_telegram_demo integer DEFAULT 0,
    id_max_demo character varying(20) DEFAULT ''::character varying,
    version_max_demo integer DEFAULT 0,
    song_type character varying(20) DEFAULT 'song'::character varying NOT NULL,
    audio_parent_id integer DEFAULT 0 NOT NULL,
    audio_similarity_percent integer DEFAULT 0 NOT NULL,
    audio_delta_ms bigint DEFAULT 0 NOT NULL,
    audio_compare_history text DEFAULT '[]'::text NOT NULL,
    player_readiness_flags text DEFAULT '{}'::text NOT NULL,
    album_id integer
);


--
-- Name: tbl_songs_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.tbl_songs_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: tbl_songs_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.tbl_songs_id_seq OWNED BY public.tbl_songs.id;


--
-- Name: tbl_songs_sync; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tbl_songs_sync (
    id integer NOT NULL,
    song_name character varying(255),
    song_author character varying(255),
    song_album character varying(255),
    publish_date character varying(8),
    publish_time character varying(5),
    song_year integer,
    song_track integer,
    song_tone character varying(10),
    song_bpm integer,
    song_ms integer,
    file_name character varying(255),
    root_folder character varying(255),
    id_boosty character varying(40),
    id_dzen_lyrics character varying(40),
    id_dzen_karaoke character varying(40),
    id_dzen_chords character varying(40),
    id_status integer DEFAULT 0,
    source_text text,
    source_markers text,
    id_vk_lyrics character varying(20),
    id_vk_karaoke character varying(20),
    id_vk_chords character varying(20),
    status_process_lyrics character varying(20),
    status_process_karaoke character varying(20),
    status_process_chords character varying(20),
    id_vk character varying(20),
    id_telegram_lyrics character varying(7),
    id_telegram_karaoke character varying(7),
    id_telegram_chords character varying(7),
    tags text,
    last_update timestamp without time zone,
    result_text text,
    id_boosty_files character varying(40),
    result_version integer DEFAULT 0,
    id_pl_lyrics character varying(20) DEFAULT ''::character varying,
    id_pl_karaoke character varying(20) DEFAULT ''::character varying,
    id_pl_chords character varying(20) DEFAULT ''::character varying,
    diff_beats integer DEFAULT 0,
    id_sponsr character varying(7) DEFAULT ''::character varying,
    id_dzen_melody character varying(40) DEFAULT ''::character varying,
    id_vk_melody character varying(20) DEFAULT ''::character varying,
    status_process_melody character varying(20) DEFAULT ''::character varying,
    id_telegram_melody character varying(7) DEFAULT ''::character varying,
    id_pl_melody character varying(20) DEFAULT ''::character varying,
    index_tabs_variant integer DEFAULT 0,
    version_dzen_lyrics integer DEFAULT 0,
    version_dzen_karaoke integer DEFAULT 0,
    version_dzen_chords integer DEFAULT 0,
    version_dzen_melody integer DEFAULT 0,
    version_vk_lyrics integer DEFAULT 0,
    version_vk_karaoke integer DEFAULT 0,
    version_vk_chords integer DEFAULT 0,
    version_vk_melody integer DEFAULT 0,
    version_telegram_lyrics integer DEFAULT 0,
    version_telegram_karaoke integer DEFAULT 0,
    version_telegram_chords integer DEFAULT 0,
    version_telegram_melody integer DEFAULT 0,
    version_pl_lyrics integer DEFAULT 0,
    version_pl_karaoke integer DEFAULT 0,
    version_pl_chords integer DEFAULT 0,
    version_pl_melody integer DEFAULT 0,
    version_boosty integer DEFAULT 0,
    version_sponsr integer DEFAULT 0,
    version_boosty_files integer DEFAULT 0,
    rate integer DEFAULT 0,
    recordhash character varying(32),
    formatted_text_song text DEFAULT ''::text NOT NULL,
    formatted_text_tabs text DEFAULT ''::text NOT NULL,
    formatted_text_chords text DEFAULT ''::text NOT NULL,
    root_id integer DEFAULT 0 NOT NULL,
    exclusive boolean DEFAULT false NOT NULL,
    free boolean DEFAULT false NOT NULL,
    id_max_lyrics character varying(20) DEFAULT ''::character varying,
    id_max_karaoke character varying(20) DEFAULT ''::character varying,
    id_max_chords character varying(20) DEFAULT ''::character varying,
    id_max_melody character varying(20) DEFAULT ''::character varying,
    version_max_lyrics integer DEFAULT 0,
    version_max_karaoke integer DEFAULT 0,
    version_max_chords integer DEFAULT 0,
    version_max_melody integer DEFAULT 0,
    id_dzen_demo character varying(40) DEFAULT ''::character varying,
    version_dzen_demo integer DEFAULT 0,
    id_vk_demo character varying(20) DEFAULT ''::character varying,
    version_vk_demo integer DEFAULT 0,
    id_telegram_demo character varying(7) DEFAULT ''::character varying,
    version_telegram_demo integer DEFAULT 0,
    id_max_demo character varying(20) DEFAULT ''::character varying,
    version_max_demo integer DEFAULT 0,
    song_type character varying(20) DEFAULT 'song'::character varying NOT NULL,
    audio_parent_id integer DEFAULT 0 NOT NULL,
    audio_similarity_percent integer DEFAULT 0 NOT NULL,
    audio_delta_ms bigint DEFAULT 0 NOT NULL,
    audio_compare_history text DEFAULT '[]'::text NOT NULL,
    player_readiness_flags text DEFAULT '{}'::text NOT NULL
);


--
-- Name: tbl_songs_sync_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.tbl_songs_sync_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: tbl_songs_sync_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.tbl_songs_sync_id_seq OWNED BY public.tbl_songs_sync.id;


--
-- Name: tbl_stem_jobs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tbl_stem_jobs (
    id integer NOT NULL,
    site_user_id integer NOT NULL,
    mode character varying(16) NOT NULL,
    status character varying(16) DEFAULT 'WAITING'::character varying NOT NULL,
    original_file_name text DEFAULT ''::text NOT NULL,
    original_ext character varying(16) DEFAULT ''::character varying NOT NULL,
    file_size_bytes bigint DEFAULT 0 NOT NULL,
    error_message text DEFAULT ''::text NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    started_at timestamp without time zone,
    finished_at timestamp without time zone,
    expires_at timestamp without time zone,
    delete_requested boolean DEFAULT false NOT NULL,
    last_update timestamp without time zone DEFAULT now(),
    recordhash character varying(32)
);


--
-- Name: tbl_stem_jobs_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.tbl_stem_jobs ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.tbl_stem_jobs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: tbl_subscriptions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tbl_subscriptions (
    id integer NOT NULL,
    site_user_id integer NOT NULL,
    scope character varying(16) NOT NULL,
    id_song bigint,
    tariff_id integer,
    period_days integer DEFAULT 0 NOT NULL,
    base_price numeric(10,2) DEFAULT 0 NOT NULL,
    discount numeric(10,2) DEFAULT 0 NOT NULL,
    final_price numeric(10,2) DEFAULT 0 NOT NULL,
    promo_applied character varying(64) DEFAULT ''::character varying NOT NULL,
    status character varying(16) DEFAULT 'CREATED'::character varying NOT NULL,
    yookassa_payment_id character varying(64) DEFAULT ''::character varying NOT NULL,
    auto_renew boolean DEFAULT true NOT NULL,
    yookassa_payment_method_id character varying(64) DEFAULT ''::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    paid_at timestamp without time zone,
    last_update timestamp without time zone DEFAULT now(),
    recordhash character varying(32),
    order_id character varying(36)
);


--
-- Name: tbl_subscriptions_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.tbl_subscriptions ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.tbl_subscriptions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: tbl_uuids; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tbl_uuids (
    id integer NOT NULL,
    uuid uuid
);


--
-- Name: tbl_processes id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tbl_processes ALTER COLUMN id SET DEFAULT nextval('public.tbl_processes_id_seq'::regclass);


--
-- Name: tbl_songs id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tbl_songs ALTER COLUMN id SET DEFAULT nextval('public.tbl_songs_id_seq'::regclass);


--
-- Name: tbl_songs_sync id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tbl_songs_sync ALTER COLUMN id SET DEFAULT nextval('public.tbl_songs_sync_id_seq'::regclass);


--
-- Name: tbl_albums tbl_albums_author_year_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tbl_albums
    ADD CONSTRAINT tbl_albums_author_year_name_key UNIQUE (author_id, year, name);


--
-- Name: tbl_albums tbl_albums_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tbl_albums
    ADD CONSTRAINT tbl_albums_pkey PRIMARY KEY (id);


--
-- Name: tbl_authors tbl_authors_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tbl_authors
    ADD CONSTRAINT tbl_authors_pk PRIMARY KEY (id);


--
-- Name: tbl_cart_items tbl_cart_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tbl_cart_items
    ADD CONSTRAINT tbl_cart_items_pkey PRIMARY KEY (id);


--
-- Name: tbl_dictionaries tbl_dictionaries_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tbl_dictionaries
    ADD CONSTRAINT tbl_dictionaries_pkey PRIMARY KEY (id);


--
-- Name: tbl_events tbl_events_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tbl_events
    ADD CONSTRAINT tbl_events_id_key UNIQUE (id);


--
-- Name: tbl_events tbl_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tbl_events
    ADD CONSTRAINT tbl_events_pkey PRIMARY KEY (id);


--
-- Name: tbl_ip_country tbl_ip_country_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tbl_ip_country
    ADD CONSTRAINT tbl_ip_country_pkey PRIMARY KEY (ip);


--
-- Name: tbl_listening_history tbl_listening_history_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tbl_listening_history
    ADD CONSTRAINT tbl_listening_history_pkey PRIMARY KEY (id);


--
-- Name: tbl_news tbl_news_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tbl_news
    ADD CONSTRAINT tbl_news_pkey PRIMARY KEY (id);


--
-- Name: tbl_price_tariffs tbl_price_tariffs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tbl_price_tariffs
    ADD CONSTRAINT tbl_price_tariffs_pkey PRIMARY KEY (id);


--
-- Name: tbl_processes tbl_processes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tbl_processes
    ADD CONSTRAINT tbl_processes_pkey PRIMARY KEY (id);


--
-- Name: tbl_promo_rules tbl_promo_rules_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tbl_promo_rules
    ADD CONSTRAINT tbl_promo_rules_pkey PRIMARY KEY (id);


--
-- Name: tbl_public_settings tbl_public_settings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tbl_public_settings
    ADD CONSTRAINT tbl_public_settings_pkey PRIMARY KEY (key);


--
-- Name: tbl_search_results tbl_search_results_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tbl_search_results
    ADD CONSTRAINT tbl_search_results_id_key UNIQUE (id);


--
-- Name: tbl_search_results tbl_search_results_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tbl_search_results
    ADD CONSTRAINT tbl_search_results_pkey PRIMARY KEY (id);


--
-- Name: tbl_site_chat_messages tbl_site_chat_messages_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tbl_site_chat_messages
    ADD CONSTRAINT tbl_site_chat_messages_pkey PRIMARY KEY (id);


--
-- Name: tbl_site_playlist_items tbl_site_playlist_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tbl_site_playlist_items
    ADD CONSTRAINT tbl_site_playlist_items_pkey PRIMARY KEY (id);


--
-- Name: tbl_site_playlists tbl_site_playlists_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tbl_site_playlists
    ADD CONSTRAINT tbl_site_playlists_pkey PRIMARY KEY (id);


--
-- Name: tbl_site_user_tokens tbl_site_user_tokens_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tbl_site_user_tokens
    ADD CONSTRAINT tbl_site_user_tokens_pkey PRIMARY KEY (id);


--
-- Name: tbl_site_users tbl_site_users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tbl_site_users
    ADD CONSTRAINT tbl_site_users_pkey PRIMARY KEY (id);


--
-- Name: tbl_song_assignment_drafts tbl_song_assignment_drafts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tbl_song_assignment_drafts
    ADD CONSTRAINT tbl_song_assignment_drafts_pkey PRIMARY KEY (id);


--
-- Name: tbl_song_assignments tbl_song_assignments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tbl_song_assignments
    ADD CONSTRAINT tbl_song_assignments_pkey PRIMARY KEY (id);


--
-- Name: tbl_song_authors tbl_song_authors_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tbl_song_authors
    ADD CONSTRAINT tbl_song_authors_pkey PRIMARY KEY (id);


--
-- Name: tbl_song_authors tbl_song_authors_song_author_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tbl_song_authors
    ADD CONSTRAINT tbl_song_authors_song_author_key UNIQUE (song_id, author_id);


--
-- Name: tbl_songs tbl_songs_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tbl_songs
    ADD CONSTRAINT tbl_songs_id_key UNIQUE (id);


--
-- Name: tbl_songs tbl_songs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tbl_songs
    ADD CONSTRAINT tbl_songs_pkey PRIMARY KEY (id);


--
-- Name: tbl_songs_sync tbl_songs_sync_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tbl_songs_sync
    ADD CONSTRAINT tbl_songs_sync_id_key UNIQUE (id);


--
-- Name: tbl_songs_sync tbl_songs_sync_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tbl_songs_sync
    ADD CONSTRAINT tbl_songs_sync_pkey PRIMARY KEY (id);


--
-- Name: tbl_stem_jobs tbl_stem_jobs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tbl_stem_jobs
    ADD CONSTRAINT tbl_stem_jobs_pkey PRIMARY KEY (id);


--
-- Name: tbl_subscriptions tbl_subscriptions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tbl_subscriptions
    ADD CONSTRAINT tbl_subscriptions_pkey PRIMARY KEY (id);


--
-- Name: tbl_uuids tbl_uuids_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tbl_uuids
    ADD CONSTRAINT tbl_uuids_pk PRIMARY KEY (id);


--
-- Name: tbl_cart_items uq_tbl_cart_items_user_song; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tbl_cart_items
    ADD CONSTRAINT uq_tbl_cart_items_user_song UNIQUE (site_user_id, id_song);


--
-- Name: idx_gin_result_text; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_gin_result_text ON public.tbl_songs USING gin (to_tsvector('russian'::regconfig, result_text));


--
-- Name: idx_tbl_albums_author_year; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tbl_albums_author_year ON public.tbl_albums USING btree (author_id, year);


--
-- Name: idx_tbl_albums_recordhash; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tbl_albums_recordhash ON public.tbl_albums USING btree (recordhash);


--
-- Name: idx_tbl_authors_recordhash; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tbl_authors_recordhash ON public.tbl_authors USING btree (recordhash);


--
-- Name: idx_tbl_cart_items_site_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tbl_cart_items_site_user_id ON public.tbl_cart_items USING btree (site_user_id);


--
-- Name: idx_tbl_dictionaries_dict_name; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tbl_dictionaries_dict_name ON public.tbl_dictionaries USING btree (dict_name);


--
-- Name: idx_tbl_dictionaries_recordhash; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tbl_dictionaries_recordhash ON public.tbl_dictionaries USING btree (recordhash);


--
-- Name: idx_tbl_events_recordhash; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tbl_events_recordhash ON public.tbl_events USING btree (recordhash);


--
-- Name: idx_tbl_listening_history_last_played_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tbl_listening_history_last_played_at ON public.tbl_listening_history USING btree (last_played_at);


--
-- Name: idx_tbl_listening_history_recordhash; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tbl_listening_history_recordhash ON public.tbl_listening_history USING btree (recordhash);


--
-- Name: idx_tbl_listening_history_site_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tbl_listening_history_site_user_id ON public.tbl_listening_history USING btree (site_user_id);


--
-- Name: idx_tbl_listening_history_uniq; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX idx_tbl_listening_history_uniq ON public.tbl_listening_history USING btree (site_user_id, song_id);


--
-- Name: idx_tbl_news_publish_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tbl_news_publish_at ON public.tbl_news USING btree (publish_at);


--
-- Name: idx_tbl_news_recordhash; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tbl_news_recordhash ON public.tbl_news USING btree (recordhash);


--
-- Name: idx_tbl_pictures_recordhash; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tbl_pictures_recordhash ON public.tbl_pictures USING btree (recordhash);


--
-- Name: idx_tbl_pictures_sync_recordhash; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tbl_pictures_sync_recordhash ON public.tbl_pictures_sync USING btree (recordhash);


--
-- Name: idx_tbl_price_tariffs_recordhash; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tbl_price_tariffs_recordhash ON public.tbl_price_tariffs USING btree (recordhash);


--
-- Name: idx_tbl_processes_recordhash; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tbl_processes_recordhash ON public.tbl_processes USING btree (recordhash);


--
-- Name: idx_tbl_promo_rules_recordhash; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tbl_promo_rules_recordhash ON public.tbl_promo_rules USING btree (recordhash);


--
-- Name: idx_tbl_search_async_recordhash; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tbl_search_async_recordhash ON public.tbl_search_async USING btree (recordhash);


--
-- Name: idx_tbl_search_results_recordhash; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tbl_search_results_recordhash ON public.tbl_search_results USING btree (recordhash);


--
-- Name: idx_tbl_site_chat_messages_recordhash; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tbl_site_chat_messages_recordhash ON public.tbl_site_chat_messages USING btree (recordhash);


--
-- Name: idx_tbl_site_chat_messages_site_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tbl_site_chat_messages_site_user_id ON public.tbl_site_chat_messages USING btree (site_user_id, id);


--
-- Name: idx_tbl_site_chat_messages_unread; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tbl_site_chat_messages_unread ON public.tbl_site_chat_messages USING btree (is_from_author, is_read);


--
-- Name: idx_tbl_site_playlist_items_playlist_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tbl_site_playlist_items_playlist_id ON public.tbl_site_playlist_items USING btree (playlist_id);


--
-- Name: idx_tbl_site_playlist_items_recordhash; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tbl_site_playlist_items_recordhash ON public.tbl_site_playlist_items USING btree (recordhash);


--
-- Name: idx_tbl_site_playlist_items_uniq; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX idx_tbl_site_playlist_items_uniq ON public.tbl_site_playlist_items USING btree (playlist_id, song_id);


--
-- Name: idx_tbl_site_playlists_favorites; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX idx_tbl_site_playlists_favorites ON public.tbl_site_playlists USING btree (owner_id) WHERE is_favorites;


--
-- Name: idx_tbl_site_playlists_owner_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tbl_site_playlists_owner_id ON public.tbl_site_playlists USING btree (owner_id);


--
-- Name: idx_tbl_site_playlists_recordhash; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tbl_site_playlists_recordhash ON public.tbl_site_playlists USING btree (recordhash);


--
-- Name: idx_tbl_site_user_tokens_site_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tbl_site_user_tokens_site_user_id ON public.tbl_site_user_tokens USING btree (site_user_id);


--
-- Name: idx_tbl_site_user_tokens_token; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX idx_tbl_site_user_tokens_token ON public.tbl_site_user_tokens USING btree (token);


--
-- Name: idx_tbl_site_users_email; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX idx_tbl_site_users_email ON public.tbl_site_users USING btree (lower((email)::text));


--
-- Name: idx_tbl_site_users_recordhash; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tbl_site_users_recordhash ON public.tbl_site_users USING btree (recordhash);


--
-- Name: idx_tbl_song_assignment_drafts_assignee_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tbl_song_assignment_drafts_assignee_id ON public.tbl_song_assignment_drafts USING btree (assignee_id);


--
-- Name: idx_tbl_song_assignment_drafts_recordhash; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tbl_song_assignment_drafts_recordhash ON public.tbl_song_assignment_drafts USING btree (recordhash);


--
-- Name: idx_tbl_song_assignment_drafts_uniq; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX idx_tbl_song_assignment_drafts_uniq ON public.tbl_song_assignment_drafts USING btree (assignment_id);


--
-- Name: idx_tbl_song_assignments_assignee_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tbl_song_assignments_assignee_id ON public.tbl_song_assignments USING btree (assignee_id);


--
-- Name: idx_tbl_song_assignments_recordhash; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tbl_song_assignments_recordhash ON public.tbl_song_assignments USING btree (recordhash);


--
-- Name: idx_tbl_song_assignments_uniq; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX idx_tbl_song_assignments_uniq ON public.tbl_song_assignments USING btree (song_id, assignee_id);


--
-- Name: idx_tbl_song_authors_author_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tbl_song_authors_author_id ON public.tbl_song_authors USING btree (author_id);


--
-- Name: idx_tbl_song_authors_recordhash; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tbl_song_authors_recordhash ON public.tbl_song_authors USING btree (recordhash);


--
-- Name: idx_tbl_song_authors_song_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tbl_song_authors_song_id ON public.tbl_song_authors USING btree (song_id);


--
-- Name: idx_tbl_songs_album_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tbl_songs_album_id ON public.tbl_songs USING btree (album_id);


--
-- Name: idx_tbl_songs_audio_parent_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tbl_songs_audio_parent_id ON public.tbl_songs USING btree (audio_parent_id);


--
-- Name: idx_tbl_songs_recordhash; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tbl_songs_recordhash ON public.tbl_songs USING btree (recordhash);


--
-- Name: idx_tbl_songs_sync_recordhash; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tbl_songs_sync_recordhash ON public.tbl_songs_sync USING btree (recordhash);


--
-- Name: idx_tbl_stem_jobs_recordhash; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tbl_stem_jobs_recordhash ON public.tbl_stem_jobs USING btree (recordhash);


--
-- Name: idx_tbl_stem_jobs_site_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tbl_stem_jobs_site_user_id ON public.tbl_stem_jobs USING btree (site_user_id, id);


--
-- Name: idx_tbl_stem_jobs_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tbl_stem_jobs_status ON public.tbl_stem_jobs USING btree (status);


--
-- Name: idx_tbl_subscriptions_order_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tbl_subscriptions_order_id ON public.tbl_subscriptions USING btree (order_id) WHERE (order_id IS NOT NULL);


--
-- Name: idx_tbl_subscriptions_recordhash; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tbl_subscriptions_recordhash ON public.tbl_subscriptions USING btree (recordhash);


--
-- Name: idx_tbl_subscriptions_site_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tbl_subscriptions_site_user_id ON public.tbl_subscriptions USING btree (site_user_id);


--
-- Name: idx_tbl_subscriptions_song_owned; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tbl_subscriptions_song_owned ON public.tbl_subscriptions USING btree (id_song, site_user_id) WHERE (((scope)::text = 'SONG'::text) AND ((status)::text = 'PAID'::text));


--
-- Name: idx_tbl_subscriptions_yookassa_payment_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tbl_subscriptions_yookassa_payment_id ON public.tbl_subscriptions USING btree (yookassa_payment_id) WHERE ((yookassa_payment_id)::text <> ''::text);


--
-- Name: tbl_events_last_update_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_events_last_update_index ON public.tbl_events USING btree (last_update);


--
-- Name: tbl_events_site_user_id_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_events_site_user_id_index ON public.tbl_events USING btree (site_user_id) WHERE (site_user_id > 0);


--
-- Name: tbl_events_song_id_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_events_song_id_index ON public.tbl_events USING btree (song_id);


--
-- Name: tbl_listening_history_last_update_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_listening_history_last_update_index ON public.tbl_listening_history USING btree (last_update);


--
-- Name: tbl_price_tariffs_last_update_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_price_tariffs_last_update_index ON public.tbl_price_tariffs USING btree (last_update);


--
-- Name: tbl_promo_rules_last_update_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_promo_rules_last_update_index ON public.tbl_promo_rules USING btree (last_update);


--
-- Name: tbl_search_async_last_update_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_search_async_last_update_index ON public.tbl_search_async USING btree (last_update);


--
-- Name: tbl_search_async_song_id_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_search_async_song_id_index ON public.tbl_search_async USING btree (song_id);


--
-- Name: tbl_search_results_last_update_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_search_results_last_update_index ON public.tbl_search_results USING btree (last_update);


--
-- Name: tbl_search_results_search_async_id_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_search_results_search_async_id_index ON public.tbl_search_results USING btree (search_async_id);


--
-- Name: tbl_search_results_song_id_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_search_results_song_id_index ON public.tbl_search_results USING btree (song_id);


--
-- Name: tbl_site_playlist_items_last_update_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_site_playlist_items_last_update_index ON public.tbl_site_playlist_items USING btree (last_update);


--
-- Name: tbl_site_playlists_last_update_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_site_playlists_last_update_index ON public.tbl_site_playlists USING btree (last_update);


--
-- Name: tbl_site_users_last_update_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_site_users_last_update_index ON public.tbl_site_users USING btree (last_update);


--
-- Name: tbl_song_assignment_drafts_last_update_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_song_assignment_drafts_last_update_index ON public.tbl_song_assignment_drafts USING btree (last_update);


--
-- Name: tbl_song_assignments_last_update_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_song_assignments_last_update_index ON public.tbl_song_assignments USING btree (last_update);


--
-- Name: tbl_songs_file_name_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_songs_file_name_index ON public.tbl_songs USING btree (file_name);


--
-- Name: tbl_songs_id_boosty_files_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_songs_id_boosty_files_index ON public.tbl_songs USING btree (id_boosty_files);


--
-- Name: tbl_songs_id_boosty_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_songs_id_boosty_index ON public.tbl_songs USING btree (id_boosty);


--
-- Name: tbl_songs_id_dzen_chords_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_songs_id_dzen_chords_index ON public.tbl_songs USING btree (id_dzen_chords);


--
-- Name: tbl_songs_id_dzen_demo_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_songs_id_dzen_demo_index ON public.tbl_songs USING btree (id_dzen_demo);


--
-- Name: tbl_songs_id_dzen_karaoke_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_songs_id_dzen_karaoke_index ON public.tbl_songs USING btree (id_dzen_karaoke);


--
-- Name: tbl_songs_id_dzen_lyrics_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_songs_id_dzen_lyrics_index ON public.tbl_songs USING btree (id_dzen_lyrics);


--
-- Name: tbl_songs_id_dzen_melody_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_songs_id_dzen_melody_index ON public.tbl_songs USING btree (id_dzen_melody);


--
-- Name: tbl_songs_id_max_demo_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_songs_id_max_demo_index ON public.tbl_songs USING btree (id_max_demo);


--
-- Name: tbl_songs_id_pl_chords_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_songs_id_pl_chords_index ON public.tbl_songs USING btree (id_pl_chords);


--
-- Name: tbl_songs_id_pl_karaoke_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_songs_id_pl_karaoke_index ON public.tbl_songs USING btree (id_pl_karaoke);


--
-- Name: tbl_songs_id_pl_lyrics_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_songs_id_pl_lyrics_index ON public.tbl_songs USING btree (id_pl_lyrics);


--
-- Name: tbl_songs_id_pl_melody_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_songs_id_pl_melody_index ON public.tbl_songs USING btree (id_pl_melody);


--
-- Name: tbl_songs_id_status_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_songs_id_status_index ON public.tbl_songs USING btree (id_status);


--
-- Name: tbl_songs_id_telegram_chords_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_songs_id_telegram_chords_index ON public.tbl_songs USING btree (id_telegram_chords);


--
-- Name: tbl_songs_id_telegram_demo_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_songs_id_telegram_demo_index ON public.tbl_songs USING btree (id_telegram_demo);


--
-- Name: tbl_songs_id_telegram_karaoke_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_songs_id_telegram_karaoke_index ON public.tbl_songs USING btree (id_telegram_karaoke);


--
-- Name: tbl_songs_id_telegram_lyrics_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_songs_id_telegram_lyrics_index ON public.tbl_songs USING btree (id_telegram_lyrics);


--
-- Name: tbl_songs_id_telegram_melody_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_songs_id_telegram_melody_index ON public.tbl_songs USING btree (id_telegram_melody);


--
-- Name: tbl_songs_id_vk_chords_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_songs_id_vk_chords_index ON public.tbl_songs USING btree (id_vk_chords);


--
-- Name: tbl_songs_id_vk_demo_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_songs_id_vk_demo_index ON public.tbl_songs USING btree (id_vk_demo);


--
-- Name: tbl_songs_id_vk_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_songs_id_vk_index ON public.tbl_songs USING btree (id_vk);


--
-- Name: tbl_songs_id_vk_karaoke_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_songs_id_vk_karaoke_index ON public.tbl_songs USING btree (id_vk_karaoke);


--
-- Name: tbl_songs_id_vk_lyrics_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_songs_id_vk_lyrics_index ON public.tbl_songs USING btree (id_vk_lyrics);


--
-- Name: tbl_songs_id_vk_melody_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_songs_id_vk_melody_index ON public.tbl_songs USING btree (id_vk_melody);


--
-- Name: tbl_songs_last_update_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_songs_last_update_index ON public.tbl_songs USING btree (last_update);


--
-- Name: tbl_songs_publish_date_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_songs_publish_date_index ON public.tbl_songs USING btree (publish_date);


--
-- Name: tbl_songs_publish_time_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_songs_publish_time_index ON public.tbl_songs USING btree (publish_time);


--
-- Name: tbl_songs_root_folder_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_songs_root_folder_index ON public.tbl_songs USING btree (root_folder);


--
-- Name: tbl_songs_root_id_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_songs_root_id_index ON public.tbl_songs USING btree (root_id);


--
-- Name: tbl_songs_song_album_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_songs_song_album_index ON public.tbl_songs USING btree (song_album);


--
-- Name: tbl_songs_song_author_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_songs_song_author_index ON public.tbl_songs USING btree (song_author);


--
-- Name: tbl_songs_song_name_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_songs_song_name_index ON public.tbl_songs USING btree (song_name);


--
-- Name: tbl_songs_song_year_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_songs_song_year_index ON public.tbl_songs USING btree (song_year);


--
-- Name: tbl_songs_status_process_chords_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_songs_status_process_chords_index ON public.tbl_songs USING btree (status_process_chords);


--
-- Name: tbl_songs_status_process_karaoke_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_songs_status_process_karaoke_index ON public.tbl_songs USING btree (status_process_karaoke);


--
-- Name: tbl_songs_status_process_lyrics_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_songs_status_process_lyrics_index ON public.tbl_songs USING btree (status_process_lyrics);


--
-- Name: tbl_subscriptions_last_update_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_subscriptions_last_update_index ON public.tbl_subscriptions USING btree (last_update);


--
-- Name: uq_tbl_dictionaries_name_value; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_tbl_dictionaries_name_value ON public.tbl_dictionaries USING btree (dict_name, dict_value);


--
-- Name: uq_tbl_pictures_picture_name; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_tbl_pictures_picture_name ON public.tbl_pictures USING btree (picture_name);


--
-- Name: tbl_events update_last_updated_events_trigger; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_last_updated_events_trigger BEFORE UPDATE ON public.tbl_events FOR EACH ROW EXECUTE FUNCTION public.update_last_updated();


--
-- Name: tbl_listening_history update_last_updated_listening_history_trigger; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_last_updated_listening_history_trigger BEFORE UPDATE ON public.tbl_listening_history FOR EACH ROW EXECUTE FUNCTION public.update_last_updated();


--
-- Name: tbl_price_tariffs update_last_updated_price_tariffs_trigger; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_last_updated_price_tariffs_trigger BEFORE UPDATE ON public.tbl_price_tariffs FOR EACH ROW EXECUTE FUNCTION public.update_last_updated();


--
-- Name: tbl_processes update_last_updated_process_trigger; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_last_updated_process_trigger BEFORE UPDATE ON public.tbl_processes FOR EACH ROW EXECUTE FUNCTION public.update_last_updated();


--
-- Name: tbl_promo_rules update_last_updated_promo_rules_trigger; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_last_updated_promo_rules_trigger BEFORE UPDATE ON public.tbl_promo_rules FOR EACH ROW EXECUTE FUNCTION public.update_last_updated();


--
-- Name: tbl_search_async update_last_updated_search_async_trigger; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_last_updated_search_async_trigger BEFORE UPDATE ON public.tbl_search_async FOR EACH ROW EXECUTE FUNCTION public.update_last_updated();


--
-- Name: tbl_search_results update_last_updated_search_results_trigger; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_last_updated_search_results_trigger BEFORE UPDATE ON public.tbl_search_results FOR EACH ROW EXECUTE FUNCTION public.update_last_updated();


--
-- Name: tbl_site_playlist_items update_last_updated_site_playlist_items_trigger; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_last_updated_site_playlist_items_trigger BEFORE UPDATE ON public.tbl_site_playlist_items FOR EACH ROW EXECUTE FUNCTION public.update_last_updated();


--
-- Name: tbl_site_playlists update_last_updated_site_playlists_trigger; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_last_updated_site_playlists_trigger BEFORE UPDATE ON public.tbl_site_playlists FOR EACH ROW EXECUTE FUNCTION public.update_last_updated();


--
-- Name: tbl_site_users update_last_updated_site_users_trigger; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_last_updated_site_users_trigger BEFORE UPDATE ON public.tbl_site_users FOR EACH ROW EXECUTE FUNCTION public.update_last_updated();


--
-- Name: tbl_song_assignment_drafts update_last_updated_song_assignment_drafts_trigger; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_last_updated_song_assignment_drafts_trigger BEFORE UPDATE ON public.tbl_song_assignment_drafts FOR EACH ROW EXECUTE FUNCTION public.update_last_updated();


--
-- Name: tbl_song_assignments update_last_updated_song_assignments_trigger; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_last_updated_song_assignments_trigger BEFORE UPDATE ON public.tbl_song_assignments FOR EACH ROW EXECUTE FUNCTION public.update_last_updated();


--
-- Name: tbl_stem_jobs update_last_updated_stem_jobs_trigger; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_last_updated_stem_jobs_trigger BEFORE UPDATE ON public.tbl_stem_jobs FOR EACH ROW EXECUTE FUNCTION public.update_last_updated();


--
-- Name: tbl_subscriptions update_last_updated_subscriptions_trigger; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_last_updated_subscriptions_trigger BEFORE UPDATE ON public.tbl_subscriptions FOR EACH ROW EXECUTE FUNCTION public.update_last_updated();


--
-- Name: tbl_songs update_last_updated_trigger; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_last_updated_trigger BEFORE UPDATE ON public.tbl_songs FOR EACH ROW EXECUTE FUNCTION public.update_last_updated();


--
-- Name: tbl_albums update_recordhash_albums_trigger; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_recordhash_albums_trigger BEFORE INSERT OR UPDATE ON public.tbl_albums FOR EACH ROW EXECUTE FUNCTION public.update_tbl_albums_recordhash();


--
-- Name: tbl_authors update_recordhash_authors_trigger; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_recordhash_authors_trigger BEFORE INSERT OR UPDATE ON public.tbl_authors FOR EACH ROW EXECUTE FUNCTION public.update_tbl_authors_recordhash();


--
-- Name: tbl_dictionaries update_recordhash_dictionaries_trigger; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_recordhash_dictionaries_trigger BEFORE INSERT OR UPDATE ON public.tbl_dictionaries FOR EACH ROW EXECUTE FUNCTION public.update_tbl_dictionaries_recordhash();


--
-- Name: tbl_events update_recordhash_events_trigger; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_recordhash_events_trigger BEFORE INSERT OR UPDATE ON public.tbl_events FOR EACH ROW EXECUTE FUNCTION public.update_tbl_events_recordhash();


--
-- Name: tbl_listening_history update_recordhash_listening_history_trigger; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_recordhash_listening_history_trigger BEFORE INSERT OR UPDATE ON public.tbl_listening_history FOR EACH ROW EXECUTE FUNCTION public.update_tbl_listening_history_recordhash();


--
-- Name: tbl_news update_recordhash_news_trigger; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_recordhash_news_trigger BEFORE INSERT OR UPDATE ON public.tbl_news FOR EACH ROW EXECUTE FUNCTION public.update_tbl_news_recordhash();


--
-- Name: tbl_pictures_sync update_recordhash_pictures_sync_trigger; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_recordhash_pictures_sync_trigger BEFORE INSERT OR UPDATE ON public.tbl_pictures_sync FOR EACH ROW EXECUTE FUNCTION public.update_tbl_pictures_sync_recordhash();


--
-- Name: tbl_pictures update_recordhash_pictures_trigger; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_recordhash_pictures_trigger BEFORE INSERT OR UPDATE ON public.tbl_pictures FOR EACH ROW EXECUTE FUNCTION public.update_tbl_pictures_recordhash();


--
-- Name: tbl_price_tariffs update_recordhash_price_tariffs_trigger; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_recordhash_price_tariffs_trigger BEFORE INSERT OR UPDATE ON public.tbl_price_tariffs FOR EACH ROW EXECUTE FUNCTION public.update_tbl_price_tariffs_recordhash();


--
-- Name: tbl_processes update_recordhash_processes_trigger; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_recordhash_processes_trigger BEFORE INSERT OR UPDATE ON public.tbl_processes FOR EACH ROW EXECUTE FUNCTION public.update_tbl_processes_recordhash();


--
-- Name: tbl_promo_rules update_recordhash_promo_rules_trigger; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_recordhash_promo_rules_trigger BEFORE INSERT OR UPDATE ON public.tbl_promo_rules FOR EACH ROW EXECUTE FUNCTION public.update_tbl_promo_rules_recordhash();


--
-- Name: tbl_search_async update_recordhash_search_async_trigger; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_recordhash_search_async_trigger BEFORE INSERT OR UPDATE ON public.tbl_search_async FOR EACH ROW EXECUTE FUNCTION public.update_tbl_search_async_recordhash();


--
-- Name: tbl_search_results update_recordhash_search_results_trigger; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_recordhash_search_results_trigger BEFORE INSERT OR UPDATE ON public.tbl_search_results FOR EACH ROW EXECUTE FUNCTION public.update_tbl_search_results_recordhash();


--
-- Name: tbl_site_chat_messages update_recordhash_site_chat_messages_trigger; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_recordhash_site_chat_messages_trigger BEFORE INSERT OR UPDATE ON public.tbl_site_chat_messages FOR EACH ROW EXECUTE FUNCTION public.update_tbl_site_chat_messages_recordhash();


--
-- Name: tbl_site_playlist_items update_recordhash_site_playlist_items_trigger; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_recordhash_site_playlist_items_trigger BEFORE INSERT OR UPDATE ON public.tbl_site_playlist_items FOR EACH ROW EXECUTE FUNCTION public.update_tbl_site_playlist_items_recordhash();


--
-- Name: tbl_site_playlists update_recordhash_site_playlists_trigger; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_recordhash_site_playlists_trigger BEFORE INSERT OR UPDATE ON public.tbl_site_playlists FOR EACH ROW EXECUTE FUNCTION public.update_tbl_site_playlists_recordhash();


--
-- Name: tbl_site_users update_recordhash_site_users_trigger; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_recordhash_site_users_trigger BEFORE INSERT OR UPDATE ON public.tbl_site_users FOR EACH ROW EXECUTE FUNCTION public.update_tbl_site_users_recordhash();


--
-- Name: tbl_song_assignment_drafts update_recordhash_song_assignment_drafts_trigger; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_recordhash_song_assignment_drafts_trigger BEFORE INSERT OR UPDATE ON public.tbl_song_assignment_drafts FOR EACH ROW EXECUTE FUNCTION public.update_tbl_song_assignment_drafts_recordhash();


--
-- Name: tbl_song_assignments update_recordhash_song_assignments_trigger; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_recordhash_song_assignments_trigger BEFORE INSERT OR UPDATE ON public.tbl_song_assignments FOR EACH ROW EXECUTE FUNCTION public.update_tbl_song_assignments_recordhash();


--
-- Name: tbl_song_authors update_recordhash_song_authors_trigger; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_recordhash_song_authors_trigger BEFORE INSERT OR UPDATE ON public.tbl_song_authors FOR EACH ROW EXECUTE FUNCTION public.update_tbl_song_authors_recordhash();


--
-- Name: tbl_songs_sync update_recordhash_songs_sync_trigger; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_recordhash_songs_sync_trigger BEFORE INSERT OR UPDATE ON public.tbl_songs_sync FOR EACH ROW EXECUTE FUNCTION public.update_tbl_songs_sync_recordhash();


--
-- Name: tbl_stem_jobs update_recordhash_stem_jobs_trigger; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_recordhash_stem_jobs_trigger BEFORE INSERT OR UPDATE ON public.tbl_stem_jobs FOR EACH ROW EXECUTE FUNCTION public.update_tbl_stem_jobs_recordhash();


--
-- Name: tbl_subscriptions update_recordhash_subscriptions_trigger; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_recordhash_subscriptions_trigger BEFORE INSERT OR UPDATE ON public.tbl_subscriptions FOR EACH ROW EXECUTE FUNCTION public.update_tbl_subscriptions_recordhash();


--
-- Name: tbl_songs update_recordhash_trigger; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_recordhash_trigger BEFORE INSERT OR UPDATE ON public.tbl_songs FOR EACH ROW EXECUTE FUNCTION public.update_tbl_songs_recordhash();


--
-- Name: tbl_songs_sync update_sync_last_updated_trigger; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_sync_last_updated_trigger BEFORE UPDATE ON public.tbl_songs_sync FOR EACH ROW EXECUTE FUNCTION public.update_last_updated();


--
-- Name: tbl_albums tbl_albums_author_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tbl_albums
    ADD CONSTRAINT tbl_albums_author_id_fkey FOREIGN KEY (author_id) REFERENCES public.tbl_authors(id) ON DELETE RESTRICT;


--
-- Name: tbl_cart_items tbl_cart_items_site_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tbl_cart_items
    ADD CONSTRAINT tbl_cart_items_site_user_id_fkey FOREIGN KEY (site_user_id) REFERENCES public.tbl_site_users(id) ON DELETE CASCADE;


--
-- Name: tbl_listening_history tbl_listening_history_site_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tbl_listening_history
    ADD CONSTRAINT tbl_listening_history_site_user_id_fkey FOREIGN KEY (site_user_id) REFERENCES public.tbl_site_users(id) ON DELETE CASCADE;


--
-- Name: tbl_site_playlist_items tbl_site_playlist_items_playlist_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tbl_site_playlist_items
    ADD CONSTRAINT tbl_site_playlist_items_playlist_id_fkey FOREIGN KEY (playlist_id) REFERENCES public.tbl_site_playlists(id) ON DELETE CASCADE;


--
-- Name: tbl_site_playlists tbl_site_playlists_owner_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tbl_site_playlists
    ADD CONSTRAINT tbl_site_playlists_owner_id_fkey FOREIGN KEY (owner_id) REFERENCES public.tbl_site_users(id) ON DELETE CASCADE;


--
-- Name: tbl_site_user_tokens tbl_site_user_tokens_site_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tbl_site_user_tokens
    ADD CONSTRAINT tbl_site_user_tokens_site_user_id_fkey FOREIGN KEY (site_user_id) REFERENCES public.tbl_site_users(id) ON DELETE CASCADE;


--
-- Name: tbl_song_assignment_drafts tbl_song_assignment_drafts_assignee_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tbl_song_assignment_drafts
    ADD CONSTRAINT tbl_song_assignment_drafts_assignee_id_fkey FOREIGN KEY (assignee_id) REFERENCES public.tbl_site_users(id) ON DELETE CASCADE;


--
-- Name: tbl_song_assignments tbl_song_assignments_assignee_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tbl_song_assignments
    ADD CONSTRAINT tbl_song_assignments_assignee_id_fkey FOREIGN KEY (assignee_id) REFERENCES public.tbl_site_users(id) ON DELETE CASCADE;


--
-- Name: tbl_song_authors tbl_song_authors_author_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tbl_song_authors
    ADD CONSTRAINT tbl_song_authors_author_id_fkey FOREIGN KEY (author_id) REFERENCES public.tbl_authors(id) ON DELETE CASCADE;


--
-- Name: tbl_song_authors tbl_song_authors_song_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tbl_song_authors
    ADD CONSTRAINT tbl_song_authors_song_id_fkey FOREIGN KEY (song_id) REFERENCES public.tbl_songs(id) ON DELETE CASCADE;


--
-- Name: tbl_songs tbl_songs_album_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tbl_songs
    ADD CONSTRAINT tbl_songs_album_id_fkey FOREIGN KEY (album_id) REFERENCES public.tbl_albums(id) ON DELETE SET NULL;


--
-- Name: tbl_stem_jobs tbl_stem_jobs_site_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tbl_stem_jobs
    ADD CONSTRAINT tbl_stem_jobs_site_user_id_fkey FOREIGN KEY (site_user_id) REFERENCES public.tbl_site_users(id) ON DELETE CASCADE;


--
-- Name: tbl_subscriptions tbl_subscriptions_site_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tbl_subscriptions
    ADD CONSTRAINT tbl_subscriptions_site_user_id_fkey FOREIGN KEY (site_user_id) REFERENCES public.tbl_site_users(id) ON DELETE CASCADE;


--
-- Name: tbl_subscriptions tbl_subscriptions_tariff_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tbl_subscriptions
    ADD CONSTRAINT tbl_subscriptions_tariff_id_fkey FOREIGN KEY (tariff_id) REFERENCES public.tbl_price_tariffs(id);


--
-- PostgreSQL database dump complete
--

\unrestrict qYdCtYF06Zrldkb4Fo9Mg7VQ3tVdR1GFlK8FxBbEWxBBXDk09RYjiqSEuK9q9bf

