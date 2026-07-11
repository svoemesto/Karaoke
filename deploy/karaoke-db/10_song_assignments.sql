-- Онлайн-редактор караоке-разметки для пользователей публичного сайта (karaoke-public) с
-- админской модерацией. Админ назначает site user'у песню (голос) → пользователь размечает
-- слоги → отправляет на проверку → админ одобряет (разметка применяется в tbl_settings через
-- karaoke-app) или отклоняет с комментарием.
--
-- ДВЕ ТАБЛИЦЫ, а не одна — потому что sync-движок (GenericKaraokeDbTableSyncTarget) пишет строку
-- ЦЕЛИКОМ по ОДНОМУ направлению (oneClickDirection), per-column merge нет. Статус — «пинг-понг»
-- между админом (LOCAL) и пользователем (SERVER), поэтому каждую строку должна писать ровно одна
-- сторона:
--   tbl_song_assignments        — конверт задания + вердикт админа. Пишет админ. LOCAL_TO_SERVER.
--   tbl_song_assignment_drafts  — рабочая копия пользователя. Пишет пользователь. SERVER_TO_LOCAL.
-- Композитный статус для UI выводится из пары (admin_status, draft.user_status) — без общих на
-- запись колонок (см. SongAssignmentStatus.kt).
--
-- ВАЖНО: применять вручную на КАЖДОЙ БД отдельно (LOCAL + PROD 79.174.95.69:8832) — миграция сама
-- на сервер не попадает. Порядок деплоя: миграция ДО/вместе с новым karaoke-web,
-- иначе INSERT в контроллере падает "column/relation does not exist".
-- Функция public.update_last_updated() уже определена в 01_initdb.sql — здесь только используется.

-- ==========================================================================================
-- tbl_song_assignments  (authority = админ, направление sync LOCAL_TO_SERVER)
-- ==========================================================================================
CREATE TABLE public.tbl_song_assignments (
    id integer NOT NULL,
    assignee_id integer NOT NULL REFERENCES public.tbl_site_users(id) ON DELETE CASCADE,
    song_id bigint NOT NULL,                     -- -> tbl_settings.id, БЕЗ FK (песни живут своей жизнью)
    voice integer DEFAULT 0 NOT NULL,            -- какой голос песни редактируется
    admin_status character varying(16) DEFAULT 'open' NOT NULL,   -- open / approved / rejected
    review_comment text DEFAULT '' NOT NULL,     -- комментарий админа при reject
    assigned_by bigint DEFAULT 0 NOT NULL,       -- id админа (tbl_users), информационно
    assigned_at timestamp without time zone DEFAULT now() NOT NULL,
    reviewed_at timestamp without time zone,
    last_update timestamp without time zone DEFAULT now(),
    recordhash character varying(32)
);

ALTER TABLE public.tbl_song_assignments ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.tbl_song_assignments_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
    );

ALTER TABLE ONLY public.tbl_song_assignments
    ADD CONSTRAINT tbl_song_assignments_pkey PRIMARY KEY (id);

-- Одна песня не назначается одному пользователю дважды.
CREATE UNIQUE INDEX idx_tbl_song_assignments_uniq ON public.tbl_song_assignments (song_id, assignee_id);

CREATE INDEX idx_tbl_song_assignments_assignee_id ON public.tbl_song_assignments (assignee_id);

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

CREATE INDEX idx_tbl_song_assignments_recordhash ON public.tbl_song_assignments USING btree (recordhash);

CREATE INDEX tbl_song_assignments_last_update_index ON public.tbl_song_assignments USING btree (last_update);

CREATE TRIGGER update_recordhash_song_assignments_trigger BEFORE INSERT OR UPDATE ON public.tbl_song_assignments FOR EACH ROW EXECUTE FUNCTION public.update_tbl_song_assignments_recordhash();

CREATE TRIGGER update_last_updated_song_assignments_trigger BEFORE UPDATE ON public.tbl_song_assignments FOR EACH ROW EXECUTE FUNCTION public.update_last_updated();

-- ==========================================================================================
-- tbl_song_assignment_drafts  (authority = пользователь, направление sync SERVER_TO_LOCAL)
-- ==========================================================================================
CREATE TABLE public.tbl_song_assignment_drafts (
    id integer NOT NULL,
    assignment_id bigint NOT NULL,               -- -> tbl_song_assignments.id, БЕЗ FK, UNIQUE
    assignee_id integer NOT NULL REFERENCES public.tbl_site_users(id) ON DELETE CASCADE,
    edited_source_text text DEFAULT '' NOT NULL,
    edited_markers text DEFAULT '[]' NOT NULL,   -- JSON List<SourceMarker> ОДНОГО голоса
    user_status character varying(16) DEFAULT 'in_progress' NOT NULL,  -- in_progress / submitted
    submitted_at timestamp without time zone,
    last_update timestamp without time zone DEFAULT now(),
    recordhash character varying(32)
);

ALTER TABLE public.tbl_song_assignment_drafts ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.tbl_song_assignment_drafts_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
    );

ALTER TABLE ONLY public.tbl_song_assignment_drafts
    ADD CONSTRAINT tbl_song_assignment_drafts_pkey PRIMARY KEY (id);

-- Один черновик на задание.
CREATE UNIQUE INDEX idx_tbl_song_assignment_drafts_uniq ON public.tbl_song_assignment_drafts (assignment_id);

CREATE INDEX idx_tbl_song_assignment_drafts_assignee_id ON public.tbl_song_assignment_drafts (assignee_id);

-- recordhash ОБЯЗАН включать edited_markers + edited_source_text — иначе правки не пойдут в sync-diff.
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

CREATE INDEX idx_tbl_song_assignment_drafts_recordhash ON public.tbl_song_assignment_drafts USING btree (recordhash);

CREATE INDEX tbl_song_assignment_drafts_last_update_index ON public.tbl_song_assignment_drafts USING btree (last_update);

CREATE TRIGGER update_recordhash_song_assignment_drafts_trigger BEFORE INSERT OR UPDATE ON public.tbl_song_assignment_drafts FOR EACH ROW EXECUTE FUNCTION public.update_tbl_song_assignment_drafts_recordhash();

CREATE TRIGGER update_last_updated_song_assignment_drafts_trigger BEFORE UPDATE ON public.tbl_song_assignment_drafts FOR EACH ROW EXECUTE FUNCTION public.update_last_updated();
