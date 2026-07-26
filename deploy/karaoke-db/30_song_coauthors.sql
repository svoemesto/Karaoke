-- Соавторы песни (tbl_song_authors) -- многие-ко-многим Song<->Author, доп. к главному автору.
-- См. specs/011-album-song-rename/data-model.md и research.md §3, §7.
-- Применять ПОСЛЕ 28_rename_settings_to_songs.sql (ссылается на уже переименованную tbl_songs).
--
-- Участвует в LOCAL<->SERVER синхронизации (KaraokeDbTable + recordhash-триггер,
-- SyncRegistry: key="songcoauthors", направление LOCAL_TO_SERVER, как у tbl_albums — данные
-- вводит админ на LOCAL). Все 8 sync-флагов по умолчанию выключены (KaraokeProperties.kt).
--
-- ВАЖНО: применять вручную на КАЖДОЙ БД отдельно (LOCAL + PROD) — миграция сама на сервер
-- не попадает. Порядок деплоя: миграция ДО/вместе с новым karaoke-app, иначе запись в
-- контроллере падает "relation/column does not exist".

CREATE TABLE public.tbl_song_authors (
    id integer NOT NULL,
    song_id integer NOT NULL REFERENCES public.tbl_songs(id) ON DELETE CASCADE,
    author_id integer NOT NULL REFERENCES public.tbl_authors(id) ON DELETE CASCADE,
    recordhash character varying(32)
);

ALTER TABLE public.tbl_song_authors ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.tbl_song_authors_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
    );

ALTER TABLE ONLY public.tbl_song_authors
    ADD CONSTRAINT tbl_song_authors_pkey PRIMARY KEY (id);

-- Не допускает дублирования одного и того же соавтора у песни.
ALTER TABLE ONLY public.tbl_song_authors
    ADD CONSTRAINT tbl_song_authors_song_author_key UNIQUE (song_id, author_id);

CREATE INDEX idx_tbl_song_authors_song_id ON public.tbl_song_authors (song_id);
CREATE INDEX idx_tbl_song_authors_author_id ON public.tbl_song_authors (author_id);

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

CREATE INDEX idx_tbl_song_authors_recordhash ON public.tbl_song_authors USING btree (recordhash);

CREATE TRIGGER update_recordhash_song_authors_trigger BEFORE INSERT OR UPDATE ON public.tbl_song_authors FOR EACH ROW EXECUTE FUNCTION public.update_tbl_song_authors_recordhash();
