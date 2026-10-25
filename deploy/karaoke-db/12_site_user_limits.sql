-- Персональные лимиты пользователя сайта (перекрывают дефолты в PublicPlaylistController).
-- 0 = использовать дефолт (100 избранное / 50 плейлистов / 500 песен в плейлисте).
-- Колонки входят в recordhash tbl_site_users, чтобы изменение лимита проехало по LOCAL<->SERVER sync.
--
-- ВАЖНО: применять вручную на КАЖДОЙ БД отдельно (LOCAL + PROD 79.174.95.69:8832).

-- BR-044 (аудит #336 / P-70-06, OpenProject #393): ВСЕ миграции набора, которые добавляют
-- колонку в tbl_site_users (12/13/14/17/21/40/45), обязаны объявлять ОДНУ И ТУ ЖЕ формулу
-- update_tbl_site_users_recordhash() — полный набор колонок (см. deploy/recordhash_site_users.sql).
-- Раньше каждая версия знала только свои колонки, и повторное применение старой миграции
-- (шапки прямо предписывают ручной прогон на каждой БД) молча откатывало формулу: изменения
-- выпавших колонок переставали попадать в recordhash, а sync сравнивает строки ТОЛЬКО по нему —
-- правка не уезжала на PROD. Тождественность всех файлов проверяет
-- tools/check-recordhash-consistency.py.
--
-- Каждая миграция самодостаточна: сначала ADD COLUMN IF NOT EXISTS для всех колонок, которые
-- упоминает формула (иначе backfill в этом же файле упал бы на отсутствующей колонке на чистой
-- БД), затем функция и пересчёт recordhash существующих строк.

ALTER TABLE public.tbl_site_users ADD COLUMN IF NOT EXISTS max_favorites integer DEFAULT 0 NOT NULL;
ALTER TABLE public.tbl_site_users ADD COLUMN IF NOT EXISTS max_playlists integer DEFAULT 0 NOT NULL;
ALTER TABLE public.tbl_site_users ADD COLUMN IF NOT EXISTS max_playlist_items integer DEFAULT 0 NOT NULL;
ALTER TABLE public.tbl_site_users ADD COLUMN IF NOT EXISTS is_editor boolean DEFAULT false NOT NULL;
ALTER TABLE public.tbl_site_users ADD COLUMN IF NOT EXISTS sponsr_premium_until timestamp without time zone;
ALTER TABLE public.tbl_site_users ADD COLUMN IF NOT EXISTS site_premium_until timestamp without time zone;
ALTER TABLE public.tbl_site_users ADD COLUMN IF NOT EXISTS personal_discount_percent numeric(5,2) DEFAULT 0 NOT NULL;
ALTER TABLE public.tbl_site_users ADD COLUMN IF NOT EXISTS welcome_message_sent boolean DEFAULT false NOT NULL;
ALTER TABLE public.tbl_site_users ADD COLUMN IF NOT EXISTS can_self_assign_tasks boolean DEFAULT false NOT NULL;
ALTER TABLE public.tbl_site_users ADD COLUMN IF NOT EXISTS can_work_with_skipped boolean DEFAULT false NOT NULL;

CREATE OR REPLACE FUNCTION public.update_tbl_site_users_recordhash() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    NEW.recordhash = md5(
                                COALESCE(NEW.id::TEXT, '') ||
                                COALESCE(NEW.email::TEXT, '') ||
                                COALESCE(NEW.password_hash::TEXT, '') ||
                                COALESCE(NEW.display_name::TEXT, '') ||
                                COALESCE(NEW.sponsr_uid::TEXT, '') ||
                                COALESCE(NEW.is_premium::TEXT, '') ||
                                COALESCE(NEW.is_permanent_premium::TEXT, '') ||
                                COALESCE(NEW.is_banned::TEXT, '') ||
                                COALESCE(NEW.ban_reason::TEXT, '') ||
                                COALESCE(NEW.max_favorites::TEXT, '') ||
                                COALESCE(NEW.max_playlists::TEXT, '') ||
                                COALESCE(NEW.max_playlist_items::TEXT, '') ||
                                COALESCE(NEW.is_editor::TEXT, '') ||
                                COALESCE(NEW.sponsr_premium_until::TEXT, '') ||
                                COALESCE(NEW.site_premium_until::TEXT, '') ||
                                COALESCE(NEW.personal_discount_percent::TEXT, '') ||
                                COALESCE(NEW.welcome_message_sent::TEXT, '') ||
                                COALESCE(NEW.can_self_assign_tasks::TEXT, '') ||
                                COALESCE(NEW.can_work_with_skipped::TEXT, '')
        );
RETURN NEW;
END;
$$;

UPDATE public.tbl_site_users SET recordhash = md5(
    COALESCE(id::TEXT, '') ||
    COALESCE(email::TEXT, '') ||
    COALESCE(password_hash::TEXT, '') ||
    COALESCE(display_name::TEXT, '') ||
    COALESCE(sponsr_uid::TEXT, '') ||
    COALESCE(is_premium::TEXT, '') ||
    COALESCE(is_permanent_premium::TEXT, '') ||
    COALESCE(is_banned::TEXT, '') ||
    COALESCE(ban_reason::TEXT, '') ||
    COALESCE(max_favorites::TEXT, '') ||
    COALESCE(max_playlists::TEXT, '') ||
    COALESCE(max_playlist_items::TEXT, '') ||
    COALESCE(is_editor::TEXT, '') ||
    COALESCE(sponsr_premium_until::TEXT, '') ||
    COALESCE(site_premium_until::TEXT, '') ||
    COALESCE(personal_discount_percent::TEXT, '') ||
    COALESCE(welcome_message_sent::TEXT, '') ||
    COALESCE(can_self_assign_tasks::TEXT, '') ||
    COALESCE(can_work_with_skipped::TEXT, '')
) WHERE id > 0;
