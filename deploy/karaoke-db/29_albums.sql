-- Сущность "Альбом" (tbl_albums) + связь песни с альбомом (tbl_songs.album_id).
-- См. specs/011-album-song-rename/data-model.md и research.md §1, §2, §4, §7.
-- Применять ПОСЛЕ 28_rename_settings_to_songs.sql (ссылается на уже переименованную tbl_songs).
--
-- Участвует в LOCAL<->SERVER синхронизации (KaraokeDbTable + recordhash-триггер,
-- SyncRegistry: key="albums", направление LOCAL_TO_SERVER, как у tbl_authors — каталог
-- ведёт админ на LOCAL). Все 8 sync-флагов по умолчанию выключены (KaraokeProperties.kt).
--
-- ВАЖНО: применять вручную на КАЖДОЙ БД отдельно (LOCAL + PROD) — миграция сама на сервер
-- не попадает. Порядок деплоя: миграция ДО/вместе с новым karaoke-app/karaoke-web, иначе
-- запись в контроллере падает "column/relation does not exist".

-- ==========================================================================================
-- tbl_albums
-- ==========================================================================================
CREATE TABLE public.tbl_albums (
    id integer NOT NULL,
    author_id integer NOT NULL REFERENCES public.tbl_authors(id) ON DELETE RESTRICT,
    year integer DEFAULT 0 NOT NULL,
    name character varying(255) NOT NULL,
    album_type character varying(20) DEFAULT 'studio'::character varying NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    recordhash character varying(32)
);

ALTER TABLE public.tbl_albums ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.tbl_albums_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
    );

ALTER TABLE ONLY public.tbl_albums
    ADD CONSTRAINT tbl_albums_pkey PRIMARY KEY (id);

-- Идемпотентность бэкфилла (research.md §6, "ON CONFLICT DO NOTHING") + защита от дублей
-- при ручном создании альбома администратором.
ALTER TABLE ONLY public.tbl_albums
    ADD CONSTRAINT tbl_albums_author_year_name_key UNIQUE (author_id, year, name);

-- Быстрая выборка "альбомы автора за год" (сортировка дискографии на публичном сайте).
CREATE INDEX idx_tbl_albums_author_year ON public.tbl_albums (author_id, year);

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

CREATE INDEX idx_tbl_albums_recordhash ON public.tbl_albums USING btree (recordhash);

CREATE TRIGGER update_recordhash_albums_trigger BEFORE INSERT OR UPDATE ON public.tbl_albums FOR EACH ROW EXECUTE FUNCTION public.update_tbl_albums_recordhash();

-- ==========================================================================================
-- tbl_songs.album_id (FK, опционально -- сингл без альбома остаётся NULL)
-- ==========================================================================================
ALTER TABLE public.tbl_songs ADD COLUMN IF NOT EXISTS album_id integer REFERENCES public.tbl_albums(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_tbl_songs_album_id ON public.tbl_songs (album_id);

-- Пересоздать recordhash-функцию tbl_songs, включив album_id в состав хэша (Principle III —
-- любое изменение колонок таблицы, участвующей в sync, требует пересборки триггера, иначе
-- SyncTarget.listHashes() не увидит новую колонку как diff).
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
                                COALESCE(NEW.album_id::TEXT, '')
        );
RETURN NEW;
END;
$$;

-- Разовый пересчёт recordhash для уже существующих строк -- иначе LOCAL/SERVER будут молча
-- расходиться до первого UPDATE каждой строки (docs/database.md, "recordhash-триггеры").
UPDATE public.tbl_songs SET id = id;
