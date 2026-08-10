-- Триггеры recordhash + last_update для tbl_song_share_links и tbl_song_share_sessions
-- (add-song-share-link). Применять ВМЕСТЕ с 38_song_share_links.sql — отдельной миграцией
-- потому, что recordhash-функции зависят от финального набора колонок.
--
-- Восстановлено из dangling git blob e6c7d1733b88588e71936dffd52fbc0c5e56718a (Pass 47,
-- 2026-08-10) — DDL утерян при переключении веток. Идемпотентен (DROP IF EXISTS + CREATE
-- для функций; DO-блоки для триггеров/индексов).

-- ==========================================================================================
-- tbl_song_share_links
-- ==========================================================================================
CREATE OR REPLACE FUNCTION public.update_tbl_song_share_links_recordhash() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    NEW.recordhash = md5(
                                COALESCE(NEW.id::TEXT, '') ||
                                COALESCE(NEW.owner_site_user_id::TEXT, '') ||
                                COALESCE(NEW.song_id::TEXT, '') ||
                                COALESCE(NEW.token_hash, '') ||
                                COALESCE(NEW.active::TEXT, '') ||
                                COALESCE(NEW.expires_at::TEXT, '') ||
                                COALESCE(NEW.created_at::TEXT, '') ||
                                COALESCE(NEW.revoked_at::TEXT, '') ||
                                COALESCE(NEW.revoke_reason, '') ||
                                COALESCE(NEW.first_used_at::TEXT, '') ||
                                COALESCE(NEW.last_used_at::TEXT, '') ||
                                COALESCE(NEW.active_session_token_hash, '') ||
                                COALESCE(NEW.active_session_lease_until::TEXT, '') ||
                                COALESCE(NEW.active_session_browser_hash, '') ||
                                COALESCE(NEW.sessions_total::TEXT, '') ||
                                COALESCE(NEW.rejected_concurrent::TEXT, '')
        );
RETURN NEW;
END;
$$;

CREATE INDEX IF NOT EXISTS idx_tbl_song_share_links_recordhash
    ON public.tbl_song_share_links USING btree (recordhash);

CREATE INDEX IF NOT EXISTS tbl_song_share_links_last_update_index
    ON public.tbl_song_share_links USING btree (last_update);

-- Триггеры (Postgres не поддерживает CREATE TRIGGER IF NOT EXISTS, оборачиваем в DO).
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_trigger
        WHERE tgname = 'update_recordhash_song_share_links_trigger'
    ) THEN
        CREATE TRIGGER update_recordhash_song_share_links_trigger
            BEFORE INSERT OR UPDATE ON public.tbl_song_share_links
            FOR EACH ROW EXECUTE FUNCTION public.update_tbl_song_share_links_recordhash();
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_trigger
        WHERE tgname = 'update_last_updated_song_share_links_trigger'
    ) THEN
        CREATE TRIGGER update_last_updated_song_share_links_trigger
            BEFORE UPDATE ON public.tbl_song_share_links
            FOR EACH ROW EXECUTE FUNCTION public.update_last_updated();
    END IF;
END $$;

-- ==========================================================================================
-- tbl_song_share_sessions
-- ==========================================================================================
CREATE OR REPLACE FUNCTION public.update_tbl_song_share_sessions_recordhash() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    NEW.recordhash = md5(
                                COALESCE(NEW.id::TEXT, '') ||
                                COALESCE(NEW.share_link_id::TEXT, '') ||
                                COALESCE(NEW.song_id::TEXT, '') ||
                                COALESCE(NEW.browser_hash, '') ||
                                COALESCE(NEW.owner_site_user_id::TEXT, '') ||
                                COALESCE(NEW.anon_id, '') ||
                                COALESCE(NEW.opened_at::TEXT, '') ||
                                COALESCE(NEW.started_at::TEXT, '') ||
                                COALESCE(NEW.last_seen_at::TEXT, '') ||
                                COALESCE(NEW.finished_at::TEXT, '') ||
                                COALESCE(NEW.result, '') ||
                                COALESCE(NEW.client_ip_hash, '') ||
                                COALESCE(NEW.user_agent_hash, '')
        );
RETURN NEW;
END;
$$;

CREATE INDEX IF NOT EXISTS idx_tbl_song_share_sessions_recordhash
    ON public.tbl_song_share_sessions USING btree (recordhash);

CREATE INDEX IF NOT EXISTS tbl_song_share_sessions_last_update_index
    ON public.tbl_song_share_sessions USING btree (last_update);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_trigger
        WHERE tgname = 'update_recordhash_song_share_sessions_trigger'
    ) THEN
        CREATE TRIGGER update_recordhash_song_share_sessions_trigger
            BEFORE INSERT OR UPDATE ON public.tbl_song_share_sessions
            FOR EACH ROW EXECUTE FUNCTION public.update_tbl_song_share_sessions_recordhash();
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_trigger
        WHERE tgname = 'update_last_updated_song_share_sessions_trigger'
    ) THEN
        CREATE TRIGGER update_last_updated_song_share_sessions_trigger
            BEFORE UPDATE ON public.tbl_song_share_sessions
            FOR EACH ROW EXECUTE FUNCTION public.update_last_updated();
    END IF;
END $$;
