--
-- PostgreSQL database dump
--

\restrict wbEFCRzTze873p33nyLrfKX0KGKceU6WEPjM4ij4UdDdjsszWs2mqYGlMerjts4

-- Dumped from database version 16.10 (Debian 16.10-1.pgdg13+1)
-- Dumped by pg_dump version 16.10 (Debian 16.10-1.pgdg13+1)

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


\unrestrict wbEFCRzTze873p33nyLrfKX0KGKceU6WEPjM4ij4UdDdjsszWs2mqYGlMerjts4
\connect karaoke
\restrict wbEFCRzTze873p33nyLrfKX0KGKceU6WEPjM4ij4UdDdjsszWs2mqYGlMerjts4

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
        COALESCE(NEW.last_album_ym, '') ||
        COALESCE(NEW.last_album_processed, '') ||
        COALESCE(NEW.watched::TEXT, '') ||
        COALESCE(NEW.skip::TEXT, '')
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
        COALESCE(NEW.referer, '')
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
-- Name: update_tbl_settings_recordhash(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.update_tbl_settings_recordhash() RETURNS trigger
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
    COALESCE(NEW.rate::TEXT, '')
        );
    RETURN NEW;
END;
$$;


--
-- Name: update_tbl_settings_sync_recordhash(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.update_tbl_settings_sync_recordhash() RETURNS trigger
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
        COALESCE(NEW.rate::TEXT, '')
    );
    RETURN NEW;
END;
$$;


SET default_tablespace = '';

SET default_table_access_method = heap;

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
    recordhash character varying(32)
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
    recordhash character varying(32)
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
    without_control boolean DEFAULT false
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
-- Name: tbl_settings; Type: TABLE; Schema: public; Owner: -
--

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
    recordhash character varying(32)
);


--
-- Name: tbl_settings_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.tbl_settings_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: tbl_settings_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.tbl_settings_id_seq OWNED BY public.tbl_settings.id;


--
-- Name: tbl_settings_sync; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tbl_settings_sync (
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
    recordhash character varying(32)
);


--
-- Name: tbl_settings_sync_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.tbl_settings_sync_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: tbl_settings_sync_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.tbl_settings_sync_id_seq OWNED BY public.tbl_settings_sync.id;


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
-- Name: tbl_settings id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tbl_settings ALTER COLUMN id SET DEFAULT nextval('public.tbl_settings_id_seq'::regclass);


--
-- Name: tbl_settings_sync id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tbl_settings_sync ALTER COLUMN id SET DEFAULT nextval('public.tbl_settings_sync_id_seq'::regclass);


--
-- Name: tbl_authors tbl_authors_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tbl_authors
    ADD CONSTRAINT tbl_authors_pk PRIMARY KEY (id);


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
-- Name: tbl_processes tbl_processes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tbl_processes
    ADD CONSTRAINT tbl_processes_pkey PRIMARY KEY (id);


--
-- Name: tbl_settings tbl_settings_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tbl_settings
    ADD CONSTRAINT tbl_settings_id_key UNIQUE (id);


--
-- Name: tbl_settings tbl_settings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tbl_settings
    ADD CONSTRAINT tbl_settings_pkey PRIMARY KEY (id);


--
-- Name: tbl_settings_sync tbl_settings_sync_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tbl_settings_sync
    ADD CONSTRAINT tbl_settings_sync_id_key UNIQUE (id);


--
-- Name: tbl_settings_sync tbl_settings_sync_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tbl_settings_sync
    ADD CONSTRAINT tbl_settings_sync_pkey PRIMARY KEY (id);


--
-- Name: tbl_uuids tbl_uuids_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tbl_uuids
    ADD CONSTRAINT tbl_uuids_pk PRIMARY KEY (id);


--
-- Name: idx_gin_result_text; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_gin_result_text ON public.tbl_settings USING gin (to_tsvector('russian'::regconfig, result_text));


--
-- Name: idx_tbl_authors_recordhash; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tbl_authors_recordhash ON public.tbl_authors USING btree (recordhash);


--
-- Name: idx_tbl_events_recordhash; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tbl_events_recordhash ON public.tbl_events USING btree (recordhash);


--
-- Name: idx_tbl_pictures_recordhash; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tbl_pictures_recordhash ON public.tbl_pictures USING btree (recordhash);


--
-- Name: idx_tbl_pictures_sync_recordhash; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tbl_pictures_sync_recordhash ON public.tbl_pictures_sync USING btree (recordhash);


--
-- Name: idx_tbl_settings_recordhash; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tbl_settings_recordhash ON public.tbl_settings USING btree (recordhash);


--
-- Name: idx_tbl_settings_sync_recordhash; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tbl_settings_sync_recordhash ON public.tbl_settings_sync USING btree (recordhash);


--
-- Name: tbl_events_last_update_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_events_last_update_index ON public.tbl_events USING btree (last_update);


--
-- Name: tbl_events_song_id_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_events_song_id_index ON public.tbl_events USING btree (song_id);


--
-- Name: tbl_settings_file_name_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_settings_file_name_index ON public.tbl_settings USING btree (file_name);


--
-- Name: tbl_settings_id_boosty_files_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_settings_id_boosty_files_index ON public.tbl_settings USING btree (id_boosty_files);


--
-- Name: tbl_settings_id_boosty_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_settings_id_boosty_index ON public.tbl_settings USING btree (id_boosty);


--
-- Name: tbl_settings_id_dzen_chords_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_settings_id_dzen_chords_index ON public.tbl_settings USING btree (id_dzen_chords);


--
-- Name: tbl_settings_id_dzen_karaoke_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_settings_id_dzen_karaoke_index ON public.tbl_settings USING btree (id_dzen_karaoke);


--
-- Name: tbl_settings_id_dzen_lyrics_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_settings_id_dzen_lyrics_index ON public.tbl_settings USING btree (id_dzen_lyrics);


--
-- Name: tbl_settings_id_dzen_melody_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_settings_id_dzen_melody_index ON public.tbl_settings USING btree (id_dzen_melody);


--
-- Name: tbl_settings_id_pl_chords_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_settings_id_pl_chords_index ON public.tbl_settings USING btree (id_pl_chords);


--
-- Name: tbl_settings_id_pl_karaoke_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_settings_id_pl_karaoke_index ON public.tbl_settings USING btree (id_pl_karaoke);


--
-- Name: tbl_settings_id_pl_lyrics_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_settings_id_pl_lyrics_index ON public.tbl_settings USING btree (id_pl_lyrics);


--
-- Name: tbl_settings_id_pl_melody_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_settings_id_pl_melody_index ON public.tbl_settings USING btree (id_pl_melody);


--
-- Name: tbl_settings_id_status_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_settings_id_status_index ON public.tbl_settings USING btree (id_status);


--
-- Name: tbl_settings_id_telegram_chords_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_settings_id_telegram_chords_index ON public.tbl_settings USING btree (id_telegram_chords);


--
-- Name: tbl_settings_id_telegram_karaoke_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_settings_id_telegram_karaoke_index ON public.tbl_settings USING btree (id_telegram_karaoke);


--
-- Name: tbl_settings_id_telegram_lyrics_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_settings_id_telegram_lyrics_index ON public.tbl_settings USING btree (id_telegram_lyrics);


--
-- Name: tbl_settings_id_telegram_melody_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_settings_id_telegram_melody_index ON public.tbl_settings USING btree (id_telegram_melody);


--
-- Name: tbl_settings_id_vk_chords_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_settings_id_vk_chords_index ON public.tbl_settings USING btree (id_vk_chords);


--
-- Name: tbl_settings_id_vk_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_settings_id_vk_index ON public.tbl_settings USING btree (id_vk);


--
-- Name: tbl_settings_id_vk_karaoke_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_settings_id_vk_karaoke_index ON public.tbl_settings USING btree (id_vk_karaoke);


--
-- Name: tbl_settings_id_vk_lyrics_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_settings_id_vk_lyrics_index ON public.tbl_settings USING btree (id_vk_lyrics);


--
-- Name: tbl_settings_id_vk_melody_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_settings_id_vk_melody_index ON public.tbl_settings USING btree (id_vk_melody);


--
-- Name: tbl_settings_last_update_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_settings_last_update_index ON public.tbl_settings USING btree (last_update);


--
-- Name: tbl_settings_publish_date_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_settings_publish_date_index ON public.tbl_settings USING btree (publish_date);


--
-- Name: tbl_settings_publish_time_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_settings_publish_time_index ON public.tbl_settings USING btree (publish_time);


--
-- Name: tbl_settings_root_folder_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_settings_root_folder_index ON public.tbl_settings USING btree (root_folder);


--
-- Name: tbl_settings_song_album_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_settings_song_album_index ON public.tbl_settings USING btree (song_album);


--
-- Name: tbl_settings_song_author_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_settings_song_author_index ON public.tbl_settings USING btree (song_author);


--
-- Name: tbl_settings_song_name_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_settings_song_name_index ON public.tbl_settings USING btree (song_name);


--
-- Name: tbl_settings_song_year_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_settings_song_year_index ON public.tbl_settings USING btree (song_year);


--
-- Name: tbl_settings_status_process_chords_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_settings_status_process_chords_index ON public.tbl_settings USING btree (status_process_chords);


--
-- Name: tbl_settings_status_process_karaoke_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_settings_status_process_karaoke_index ON public.tbl_settings USING btree (status_process_karaoke);


--
-- Name: tbl_settings_status_process_lyrics_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tbl_settings_status_process_lyrics_index ON public.tbl_settings USING btree (status_process_lyrics);


--
-- Name: tbl_events update_last_updated_events_trigger; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_last_updated_events_trigger BEFORE UPDATE ON public.tbl_events FOR EACH ROW EXECUTE FUNCTION public.update_last_updated();


--
-- Name: tbl_processes update_last_updated_process_trigger; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_last_updated_process_trigger BEFORE UPDATE ON public.tbl_processes FOR EACH ROW EXECUTE FUNCTION public.update_last_updated();


--
-- Name: tbl_settings update_last_updated_trigger; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_last_updated_trigger BEFORE UPDATE ON public.tbl_settings FOR EACH ROW EXECUTE FUNCTION public.update_last_updated();


--
-- Name: tbl_authors update_recordhash_authors_trigger; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_recordhash_authors_trigger BEFORE INSERT OR UPDATE ON public.tbl_authors FOR EACH ROW EXECUTE FUNCTION public.update_tbl_authors_recordhash();


--
-- Name: tbl_events update_recordhash_events_trigger; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_recordhash_events_trigger BEFORE INSERT OR UPDATE ON public.tbl_events FOR EACH ROW EXECUTE FUNCTION public.update_tbl_events_recordhash();


--
-- Name: tbl_pictures_sync update_recordhash_pictures_sync_trigger; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_recordhash_pictures_sync_trigger BEFORE INSERT OR UPDATE ON public.tbl_pictures_sync FOR EACH ROW EXECUTE FUNCTION public.update_tbl_pictures_sync_recordhash();


--
-- Name: tbl_pictures update_recordhash_pictures_trigger; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_recordhash_pictures_trigger BEFORE INSERT OR UPDATE ON public.tbl_pictures FOR EACH ROW EXECUTE FUNCTION public.update_tbl_pictures_recordhash();


--
-- Name: tbl_settings_sync update_recordhash_settings_synctrigger; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_recordhash_settings_synctrigger BEFORE INSERT OR UPDATE ON public.tbl_settings_sync FOR EACH ROW EXECUTE FUNCTION public.update_tbl_settings_sync_recordhash();


--
-- Name: tbl_settings update_recordhash_trigger; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_recordhash_trigger BEFORE INSERT OR UPDATE ON public.tbl_settings FOR EACH ROW EXECUTE FUNCTION public.update_tbl_settings_recordhash();


--
-- Name: tbl_settings_sync update_sync_last_updated_trigger; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_sync_last_updated_trigger BEFORE UPDATE ON public.tbl_settings_sync FOR EACH ROW EXECUTE FUNCTION public.update_last_updated();


--
-- PostgreSQL database dump complete
--

\unrestrict wbEFCRzTze873p33nyLrfKX0KGKceU6WEPjM4ij4UdDdjsszWs2mqYGlMerjts4

