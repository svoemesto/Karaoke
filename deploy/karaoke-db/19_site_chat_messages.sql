-- «Чат с автором проекта». Один непрерывный тред на пользователя сайта (site_user_id), append-only
-- (сообщения не редактируются, is_read переключается прочтением). Таблица живёт ЦЕЛИКОМ на PROD-БД —
-- пользователи пишут через karaoke-web (WORKING_DATABASE = серверная БД на проде), автор читает/пишет
-- из webvue3 через karaoke-app с target=remote (Connection.remote()), напрямую в ту же БД. НЕ участвует
-- в LOCAL<->SERVER синхронизации (SyncRegistry) — recordhash-триггер оставлен только ради консистентности
-- контракта KaraokeDbTable (id + recordhash обязательны), sync-движок эту таблицу не видит.
--
-- ВАЖНО: применять вручную на КАЖДОЙ БД отдельно (LOCAL — для локальной отладки — и PROD
-- 79.174.95.69:8832) — миграция сама на сервер не попадает (см. CLAUDE.md).

CREATE TABLE public.tbl_site_chat_messages (
    id integer NOT NULL,
    site_user_id integer NOT NULL,
    is_from_author boolean NOT NULL DEFAULT false,
    body text NOT NULL,
    is_read boolean NOT NULL DEFAULT false,
    created_at timestamp without time zone,
    recordhash character varying(32)
);

ALTER TABLE public.tbl_site_chat_messages ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.tbl_site_chat_messages_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
    );

ALTER TABLE ONLY public.tbl_site_chat_messages
    ADD CONSTRAINT tbl_site_chat_messages_pkey PRIMARY KEY (id);

-- Основной паттерн доступа: тред одного пользователя, сортировка по id (по возрастанию времени).
CREATE INDEX idx_tbl_site_chat_messages_site_user_id ON public.tbl_site_chat_messages (site_user_id, id);

-- Быстрый подсчёт непрочитанных (мониторинг, бейдж меню автора, бейдж пользователя).
CREATE INDEX idx_tbl_site_chat_messages_unread ON public.tbl_site_chat_messages (is_from_author, is_read);

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

CREATE INDEX idx_tbl_site_chat_messages_recordhash ON public.tbl_site_chat_messages USING btree (recordhash);

CREATE TRIGGER update_recordhash_site_chat_messages_trigger BEFORE INSERT OR UPDATE ON public.tbl_site_chat_messages FOR EACH ROW EXECUTE FUNCTION public.update_tbl_site_chat_messages_recordhash();
