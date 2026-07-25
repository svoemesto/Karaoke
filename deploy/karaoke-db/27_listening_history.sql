-- «История прослушиваний» пользователей публичного сайта (karaoke-public), QW-13.
-- Одна строка на пару (site_user_id, song_id) — апсертится при каждом прослушивании
-- (play_count++, last_played_at=now()), НЕ растёт на каждое событие (в отличие от tbl_events,
-- которая используется для общей аналитики и регулярно опустошается на PROD через
-- sync_events_pull_move_allowed — поэтому непригодна как персистентный источник для этой
-- фичи, см. specs/009-listening-history/research.md Decision 1).
--
-- Участвует в LOCAL<->SERVER синхронизации (KaraokeDbTable + recordhash-триггер,
-- SyncRegistry: listeninghistory, направление SERVER_TO_LOCAL, как у tbl_site_playlists —
-- данные создаются реальными пользователями на PROD). Все 8 sync-флагов по умолчанию
-- выключены (см. KaraokeProperties.kt) — ничего не синхронизируется автоматически.
--
-- ВАЖНО: применять вручную на КАЖДОЙ БД отдельно (LOCAL + PROD) — миграция сама на сервер
-- не попадает. Порядок деплоя: миграция ДО/вместе с новым karaoke-web/karaoke-app, иначе
-- запись в контроллере падает "column/relation does not exist".

-- ==========================================================================================
-- tbl_listening_history
-- ==========================================================================================
CREATE TABLE public.tbl_listening_history (
    id integer NOT NULL,
    site_user_id integer NOT NULL REFERENCES public.tbl_site_users(id) ON DELETE CASCADE,
    song_id bigint NOT NULL,               -- -> tbl_settings.id, БЕЗ FK (не связываем с sync песен)
    play_count integer DEFAULT 1 NOT NULL,
    last_played_at timestamp without time zone DEFAULT now() NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    last_update timestamp without time zone DEFAULT now(),
    recordhash character varying(32)
);

ALTER TABLE public.tbl_listening_history ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.tbl_listening_history_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
    );

ALTER TABLE ONLY public.tbl_listening_history
    ADD CONSTRAINT tbl_listening_history_pkey PRIMARY KEY (id);

-- Одна строка на пару (пользователь, песня) — conflict target для апсерта.
CREATE UNIQUE INDEX idx_tbl_listening_history_uniq ON public.tbl_listening_history (site_user_id, song_id);

CREATE INDEX idx_tbl_listening_history_site_user_id ON public.tbl_listening_history (site_user_id);

CREATE INDEX idx_tbl_listening_history_last_played_at ON public.tbl_listening_history (last_played_at);

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

CREATE INDEX idx_tbl_listening_history_recordhash ON public.tbl_listening_history USING btree (recordhash);

CREATE INDEX tbl_listening_history_last_update_index ON public.tbl_listening_history USING btree (last_update);

CREATE TRIGGER update_recordhash_listening_history_trigger BEFORE INSERT OR UPDATE ON public.tbl_listening_history FOR EACH ROW EXECUTE FUNCTION public.update_tbl_listening_history_recordhash();

CREATE TRIGGER update_last_updated_listening_history_trigger BEFORE UPDATE ON public.tbl_listening_history FOR EACH ROW EXECUTE FUNCTION public.update_last_updated();
