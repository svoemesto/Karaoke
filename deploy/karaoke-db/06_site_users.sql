CREATE TABLE public.tbl_site_users (
    id integer NOT NULL,
    email character varying(255) NOT NULL,
    password_hash character varying(255) NOT NULL,
    display_name character varying(255) DEFAULT '' NOT NULL,
    sponsr_uid character varying(64) DEFAULT '' NOT NULL,
    is_premium boolean DEFAULT false NOT NULL,
    is_permanent_premium boolean DEFAULT false NOT NULL,
    is_banned boolean DEFAULT false NOT NULL,
    ban_reason character varying(1024) DEFAULT '' NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    last_login_at timestamp without time zone DEFAULT now() NOT NULL,
    last_update timestamp without time zone DEFAULT now(),
    recordhash character varying(32)
);

ALTER TABLE public.tbl_site_users ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.tbl_site_users_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
    );

ALTER TABLE ONLY public.tbl_site_users
    ADD CONSTRAINT tbl_site_users_pkey PRIMARY KEY (id);

CREATE UNIQUE INDEX idx_tbl_site_users_email ON public.tbl_site_users (LOWER(email));

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
                                COALESCE(NEW.ban_reason, '')
        );
RETURN NEW;
END;
$$;

CREATE INDEX idx_tbl_site_users_recordhash ON public.tbl_site_users USING btree (recordhash);

CREATE INDEX tbl_site_users_last_update_index ON public.tbl_site_users USING btree (last_update);

CREATE TRIGGER update_recordhash_site_users_trigger BEFORE INSERT OR UPDATE ON public.tbl_site_users FOR EACH ROW EXECUTE FUNCTION public.update_tbl_site_users_recordhash();

CREATE TRIGGER update_last_updated_site_users_trigger BEFORE UPDATE ON public.tbl_site_users FOR EACH ROW EXECUTE FUNCTION public.update_last_updated();

-- Токены сессии сайта. Сознательно НЕ участвуют в recordhash/KaraokeDbTable-механизме —
-- служебная горячая таблица, читается на каждый защищённый запрос личного кабинета.
CREATE TABLE public.tbl_site_user_tokens (
    id integer NOT NULL,
    site_user_id integer NOT NULL REFERENCES public.tbl_site_users(id) ON DELETE CASCADE,
    token character varying(64) NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    expires_at timestamp without time zone NOT NULL,
    last_used_at timestamp without time zone,
    revoked boolean DEFAULT false NOT NULL
);

ALTER TABLE public.tbl_site_user_tokens ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.tbl_site_user_tokens_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
    );

ALTER TABLE ONLY public.tbl_site_user_tokens
    ADD CONSTRAINT tbl_site_user_tokens_pkey PRIMARY KEY (id);

CREATE UNIQUE INDEX idx_tbl_site_user_tokens_token ON public.tbl_site_user_tokens (token);

CREATE INDEX idx_tbl_site_user_tokens_site_user_id ON public.tbl_site_user_tokens (site_user_id);

-- Миграция для уже развёрнутых БД (local + remote), где таблица создана до появления этой колонки.
-- Применять вручную на каждой БД отдельно — миграция сама на сервер не попадает (см. CLAUDE.md).
-- ALTER TABLE public.tbl_site_users ADD COLUMN is_permanent_premium boolean DEFAULT false NOT NULL;
-- CREATE OR REPLACE FUNCTION public.update_tbl_site_users_recordhash() RETURNS trigger
--     LANGUAGE plpgsql
-- AS $$
-- BEGIN
--     NEW.recordhash = md5(
--                                 COALESCE(NEW.id::TEXT, '') ||
--                                 COALESCE(NEW.email, '') ||
--                                 COALESCE(NEW.password_hash, '') ||
--                                 COALESCE(NEW.display_name, '') ||
--                                 COALESCE(NEW.sponsr_uid, '') ||
--                                 COALESCE(NEW.is_premium::TEXT, '') ||
--                                 COALESCE(NEW.is_permanent_premium::TEXT, '') ||
--                                 COALESCE(NEW.is_banned::TEXT, '') ||
--                                 COALESCE(NEW.ban_reason, '')
--         );
-- RETURN NEW;
-- END;
-- $$;
