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

CREATE FUNCTION public.update_last_updated() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    NEW.last_update = NOW();
RETURN NEW;
END;
$$;


ALTER FUNCTION public.update_last_updated() OWNER TO postgres;

SET default_tablespace = '';
SET default_table_access_method = heap;

CREATE TABLE public.tbl_pictures (
                                     id integer NOT NULL,
                                     picture_name character varying(255),
                                     picture_full text,
                                     picture_preview text
);

ALTER TABLE public.tbl_pictures OWNER TO postgres;

ALTER TABLE public.tbl_pictures ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.tbl_pictures_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

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
                                      process_prioritet integer DEFAULT 0
);
ALTER TABLE public.tbl_processes OWNER TO postgres;
CREATE SEQUENCE public.tbl_processes_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
ALTER TABLE public.tbl_processes_id_seq OWNER TO postgres;
ALTER SEQUENCE public.tbl_processes_id_seq OWNED BY public.tbl_processes.id;
CREATE TABLE public.tbl_settings (
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
                                     id_youtube_lyrics character varying(40),
                                     id_youtube_lyrics_bt character varying(40),
                                     id_youtube_karaoke character varying(40),
                                     id_youtube_karaoke_bt character varying(40),
                                     id_youtube_chords character varying(40),
                                     id_youtube_chords_bt character varying(40),
                                     id_status integer DEFAULT 0,
                                     source_text text,
                                     source_markers text,
                                     result_text text,
                                     id_vk_lyrics character varying(20),
                                     id_vk_lyrics_bt character varying(20),
                                     id_vk_karaoke character varying(20),
                                     id_vk_karaoke_bt character varying(20),
                                     id_vk_chords character varying(20),
                                     id_vk_chords_bt character varying(20),
                                     status_process_lyrics character varying(20),
                                     status_process_lyrics_bt character varying(20),
                                     status_process_karaoke character varying(20),
                                     status_process_karaoke_bt character varying(20),
                                     status_process_chords character varying(20),
                                     status_process_chords_bt character varying(20),
                                     id_vk character varying(20),
                                     id_telegram_lyrics character varying(7),
                                     id_telegram_lyrics_bt character varying(7),
                                     id_telegram_karaoke character varying(7),
                                     id_telegram_karaoke_bt character varying(7),
                                     id_telegram_chords character varying(7),
                                     id_telegram_chords_bt character varying(7),
                                     tags text,
                                     last_update timestamp without time zone
);
ALTER TABLE public.tbl_settings OWNER TO postgres;
CREATE SEQUENCE public.tbl_settings_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
ALTER TABLE public.tbl_settings_id_seq OWNER TO postgres;
ALTER SEQUENCE public.tbl_settings_id_seq OWNED BY public.tbl_settings.id;
CREATE TABLE public.tbl_status (
                                   id integer NOT NULL,
                                   status character varying(255),
                                   color1 character varying(255),
                                   color2 character varying(255),
                                   color3 character varying(255)
);
ALTER TABLE public.tbl_status OWNER TO postgres;
ALTER TABLE ONLY public.tbl_processes ALTER COLUMN id SET DEFAULT nextval('public.tbl_processes_id_seq'::regclass);
ALTER TABLE ONLY public.tbl_settings ALTER COLUMN id SET DEFAULT nextval('public.tbl_settings_id_seq'::regclass);
ALTER TABLE ONLY public.tbl_processes
    ADD CONSTRAINT tbl_processes_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.tbl_settings
    ADD CONSTRAINT tbl_settings_id_key UNIQUE (id);
ALTER TABLE ONLY public.tbl_settings
    ADD CONSTRAINT tbl_settings_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.tbl_status
    ADD CONSTRAINT tbl_status_pkey PRIMARY KEY (id);
CREATE INDEX tbl_settings_file_name_index ON public.tbl_settings USING btree (file_name);
CREATE INDEX tbl_settings_id_boosty_index ON public.tbl_settings USING btree (id_boosty);
CREATE INDEX tbl_settings_id_status_index ON public.tbl_settings USING btree (id_status);
CREATE INDEX tbl_settings_id_telegram_chords_bt_index ON public.tbl_settings USING btree (id_telegram_chords_bt);
CREATE INDEX tbl_settings_id_telegram_chords_index ON public.tbl_settings USING btree (id_telegram_chords);
CREATE INDEX tbl_settings_id_telegram_karaoke_bt_index ON public.tbl_settings USING btree (id_telegram_karaoke_bt);
CREATE INDEX tbl_settings_id_telegram_karaoke_index ON public.tbl_settings USING btree (id_telegram_karaoke);
CREATE INDEX tbl_settings_id_telegram_lyrics_bt_index ON public.tbl_settings USING btree (id_telegram_lyrics_bt);
CREATE INDEX tbl_settings_id_telegram_lyrics_index ON public.tbl_settings USING btree (id_telegram_lyrics);
CREATE INDEX tbl_settings_id_vk_chords_bt_index ON public.tbl_settings USING btree (id_vk_chords_bt);
CREATE INDEX tbl_settings_id_vk_chords_index ON public.tbl_settings USING btree (id_vk_chords);
CREATE INDEX tbl_settings_id_vk_index ON public.tbl_settings USING btree (id_vk);
CREATE INDEX tbl_settings_id_vk_karaoke_bt_index ON public.tbl_settings USING btree (id_vk_karaoke_bt);
CREATE INDEX tbl_settings_id_vk_karaoke_index ON public.tbl_settings USING btree (id_vk_karaoke);
CREATE INDEX tbl_settings_id_vk_lyrics_bt_index ON public.tbl_settings USING btree (id_vk_lyrics_bt);
CREATE INDEX tbl_settings_id_vk_lyrics_index ON public.tbl_settings USING btree (id_vk_lyrics);
CREATE INDEX tbl_settings_id_youtube_chords_bt_index ON public.tbl_settings USING btree (id_youtube_chords_bt);
CREATE INDEX tbl_settings_id_youtube_chords_index ON public.tbl_settings USING btree (id_youtube_chords);
CREATE INDEX tbl_settings_id_youtube_karaoke_bt_index ON public.tbl_settings USING btree (id_youtube_karaoke_bt);
CREATE INDEX tbl_settings_id_youtube_karaoke_index ON public.tbl_settings USING btree (id_youtube_karaoke);
CREATE INDEX tbl_settings_id_youtube_lyrics_bt_index ON public.tbl_settings USING btree (id_youtube_lyrics_bt);
CREATE INDEX tbl_settings_id_youtube_lyrics_index ON public.tbl_settings USING btree (id_youtube_lyrics);
CREATE INDEX tbl_settings_last_update_index ON public.tbl_settings USING btree (last_update);
CREATE INDEX tbl_settings_publish_date_index ON public.tbl_settings USING btree (publish_date);
CREATE INDEX tbl_settings_publish_time_index ON public.tbl_settings USING btree (publish_time);
CREATE INDEX tbl_settings_root_folder_index ON public.tbl_settings USING btree (root_folder);
CREATE INDEX tbl_settings_song_album_index ON public.tbl_settings USING btree (song_album);
CREATE INDEX tbl_settings_song_author_index ON public.tbl_settings USING btree (song_author);
CREATE INDEX tbl_settings_song_name_index ON public.tbl_settings USING btree (song_name);
CREATE INDEX tbl_settings_song_year_index ON public.tbl_settings USING btree (song_year);
CREATE INDEX tbl_settings_status_process_chords_bt_index ON public.tbl_settings USING btree (status_process_chords_bt);
CREATE INDEX tbl_settings_status_process_chords_index ON public.tbl_settings USING btree (status_process_chords);
CREATE INDEX tbl_settings_status_process_karaoke_bt_index ON public.tbl_settings USING btree (status_process_karaoke_bt);
CREATE INDEX tbl_settings_status_process_karaoke_index ON public.tbl_settings USING btree (status_process_karaoke);
CREATE INDEX tbl_settings_status_process_lyrics_bt_index ON public.tbl_settings USING btree (status_process_lyrics_bt);
CREATE INDEX tbl_settings_status_process_lyrics_index ON public.tbl_settings USING btree (status_process_lyrics);
CREATE INDEX idx_gin_result_text ON public.tbl_settings USING gin (to_tsvector('russian'::regconfig, 'result_text'));
CREATE TRIGGER update_last_updated_process_trigger BEFORE UPDATE ON public.tbl_processes FOR EACH ROW EXECUTE FUNCTION public.update_last_updated();
CREATE TRIGGER update_last_updated_trigger BEFORE UPDATE ON public.tbl_settings FOR EACH ROW EXECUTE FUNCTION public.update_last_updated();
