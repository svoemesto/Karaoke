-- «Избранное» и «Плейлисты» пользователей публичного сайта (karaoke-public).
-- «Избранное» — это плейлист с is_favorites = true (ровно один на пользователя).
-- Обе таблицы участвуют в LOCAL<->SERVER синхронизации (KaraokeDbTable + recordhash-триггер,
-- SyncRegistry: siteplaylists / siteplaylistitems, направление SERVER_TO_LOCAL как у siteusers).
-- Данные создаются реальными пользователями на PROD.
--
-- ВАЖНО: применять вручную на КАЖДОЙ БД отдельно (LOCAL + PROD 79.174.95.69:8832) —
-- миграция сама на сервер не попадает (см. CLAUDE.md). Порядок деплоя: миграция ДО/вместе с
-- новым karaoke-web, иначе INSERT в контроллере падает "column/relation does not exist".

-- ==========================================================================================
-- tbl_site_playlists
-- ==========================================================================================
CREATE TABLE public.tbl_site_playlists (
    id integer NOT NULL,
    owner_id integer NOT NULL REFERENCES public.tbl_site_users(id) ON DELETE CASCADE,
    name character varying(255) DEFAULT '' NOT NULL,
    is_favorites boolean DEFAULT false NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    continuous boolean DEFAULT true NOT NULL,
    repeat_mode character varying(8) DEFAULT 'none' NOT NULL,
    shuffle boolean DEFAULT false NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    last_update timestamp without time zone DEFAULT now(),
    recordhash character varying(32)
);

ALTER TABLE public.tbl_site_playlists ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.tbl_site_playlists_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
    );

ALTER TABLE ONLY public.tbl_site_playlists
    ADD CONSTRAINT tbl_site_playlists_pkey PRIMARY KEY (id);

-- Ровно один «Избранное» на пользователя.
CREATE UNIQUE INDEX idx_tbl_site_playlists_favorites ON public.tbl_site_playlists (owner_id) WHERE is_favorites;

CREATE INDEX idx_tbl_site_playlists_owner_id ON public.tbl_site_playlists (owner_id);

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

CREATE INDEX idx_tbl_site_playlists_recordhash ON public.tbl_site_playlists USING btree (recordhash);

CREATE INDEX tbl_site_playlists_last_update_index ON public.tbl_site_playlists USING btree (last_update);

CREATE TRIGGER update_recordhash_site_playlists_trigger BEFORE INSERT OR UPDATE ON public.tbl_site_playlists FOR EACH ROW EXECUTE FUNCTION public.update_tbl_site_playlists_recordhash();

CREATE TRIGGER update_last_updated_site_playlists_trigger BEFORE UPDATE ON public.tbl_site_playlists FOR EACH ROW EXECUTE FUNCTION public.update_last_updated();

-- ==========================================================================================
-- tbl_site_playlist_items
-- ==========================================================================================
CREATE TABLE public.tbl_site_playlist_items (
    id integer NOT NULL,
    playlist_id integer NOT NULL REFERENCES public.tbl_site_playlists(id) ON DELETE CASCADE,
    song_id bigint NOT NULL,               -- -> tbl_settings.id, БЕЗ FK (не связываем с sync песен)
    position integer DEFAULT 0 NOT NULL,
    muted boolean DEFAULT false NOT NULL,
    added_at timestamp without time zone DEFAULT now() NOT NULL,
    last_update timestamp without time zone DEFAULT now(),
    recordhash character varying(32)
);

ALTER TABLE public.tbl_site_playlist_items ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.tbl_site_playlist_items_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
    );

ALTER TABLE ONLY public.tbl_site_playlist_items
    ADD CONSTRAINT tbl_site_playlist_items_pkey PRIMARY KEY (id);

-- Одна песня не дублируется в пределах плейлиста.
CREATE UNIQUE INDEX idx_tbl_site_playlist_items_uniq ON public.tbl_site_playlist_items (playlist_id, song_id);

CREATE INDEX idx_tbl_site_playlist_items_playlist_id ON public.tbl_site_playlist_items (playlist_id);

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

CREATE INDEX idx_tbl_site_playlist_items_recordhash ON public.tbl_site_playlist_items USING btree (recordhash);

CREATE INDEX tbl_site_playlist_items_last_update_index ON public.tbl_site_playlist_items USING btree (last_update);

CREATE TRIGGER update_recordhash_site_playlist_items_trigger BEFORE INSERT OR UPDATE ON public.tbl_site_playlist_items FOR EACH ROW EXECUTE FUNCTION public.update_tbl_site_playlist_items_recordhash();

CREATE TRIGGER update_last_updated_site_playlist_items_trigger BEFORE UPDATE ON public.tbl_site_playlist_items FOR EACH ROW EXECUTE FUNCTION public.update_last_updated();
