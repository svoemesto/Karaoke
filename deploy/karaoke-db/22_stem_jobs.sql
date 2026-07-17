-- Премиум-фича «Создать минусовку из аудиофайла»: пользователь публичного сайта загружает
-- произвольный аудиофайл и получает исходник + стемы (demucs2: музыка/голос; demucs5: музыка/
-- голос/бас/ударные/остальное). Таблица живёт ЦЕЛИКОМ на PROD-БД, по образцу tbl_site_chat_messages
-- (19_site_chat_messages.sql) — НЕ участвует в LOCAL<->SERVER синхронизации (нет записи в
-- SyncRegistry, нет sync_* флагов): пользователи создают задания через karaoke-web (WORKING_DATABASE
-- на проде = серверная БД), karaoke-app забирает их в работу через Connection.remote() (поллер +
-- финализация процесса), напрямую в ту же БД. recordhash-триггер оставлен только ради
-- консистентности контракта KaraokeDbTable (id + recordhash обязательны), sync-движок эту таблицу
-- не видит.
--
-- Обработка (demucs, GPU, docker) может идти только на машине администратора — karaoke-web не имеет
-- доступа на запись в MinIO и не гоняет docker (см. DEVELOPMENT.md, "Storage (MinIO)"). Список
-- доступных стемов не хранится отдельной колонкой — выводится детерминированно из mode.
--
-- ВАЖНО: применять вручную на КАЖДОЙ БД отдельно (LOCAL — для локальной отладки — и PROD
-- 79.174.95.69:8832) — миграция сама на сервер не попадает.

CREATE TABLE public.tbl_stem_jobs (
    id integer NOT NULL,
    site_user_id integer NOT NULL REFERENCES public.tbl_site_users(id) ON DELETE CASCADE,
    mode character varying(16) NOT NULL,                       -- DEMUCS2 / DEMUCS5
    status character varying(16) DEFAULT 'WAITING' NOT NULL,   -- WAITING / WORKING / DONE / ERROR
    original_file_name text DEFAULT '' NOT NULL,
    original_ext character varying(16) DEFAULT '' NOT NULL,
    file_size_bytes bigint DEFAULT 0 NOT NULL,
    error_message text DEFAULT '' NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    started_at timestamp without time zone,
    finished_at timestamp without time zone,
    expires_at timestamp without time zone,                    -- выставляется только при status=DONE (now()+24h)
    delete_requested boolean DEFAULT false NOT NULL,            -- пользователь запросил удаление (любой статус)
    last_update timestamp without time zone DEFAULT now(),
    recordhash character varying(32)
);

ALTER TABLE public.tbl_stem_jobs ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.tbl_stem_jobs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
    );

ALTER TABLE ONLY public.tbl_stem_jobs
    ADD CONSTRAINT tbl_stem_jobs_pkey PRIMARY KEY (id);

-- Список заданий пользователя в ЛК + подсчёт активных (WAITING/WORKING) для лимита очереди (<=5).
CREATE INDEX idx_tbl_stem_jobs_site_user_id ON public.tbl_stem_jobs (site_user_id, id);

-- Поллер karaoke-app (WAITING) и уборка (DONE + expires_at / delete_requested).
CREATE INDEX idx_tbl_stem_jobs_status ON public.tbl_stem_jobs (status);

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

CREATE INDEX idx_tbl_stem_jobs_recordhash ON public.tbl_stem_jobs USING btree (recordhash);

CREATE TRIGGER update_recordhash_stem_jobs_trigger BEFORE INSERT OR UPDATE ON public.tbl_stem_jobs FOR EACH ROW EXECUTE FUNCTION public.update_tbl_stem_jobs_recordhash();

CREATE TRIGGER update_last_updated_stem_jobs_trigger BEFORE UPDATE ON public.tbl_stem_jobs FOR EACH ROW EXECUTE FUNCTION public.update_last_updated();
