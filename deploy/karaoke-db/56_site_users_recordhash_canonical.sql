-- BR-044 (аудит #336 / P-70-06, OpenProject #393): доставить каноническую формулу
-- recordhash tbl_site_users на уже существующие БД (LOCAL и PROD).
--
-- Правки в 12/13/14/17/21/40/45 защищают будущие прогоны, но на существующей БД они не
-- выполняются (миграции применены давно). На такой БД последней по дате была 45: она
-- переопределяла функцию БЕЗ personal_discount_percent (его вводит 17) — персональная скидка
-- не входила в md5, изменение скидки не считалось расхождением и не уезжало на PROD.
--
-- Этот файл выполняет ровно то, что лежит в каноническом deploy/recordhash_site_users.sql:
-- самодостаточные ADD COLUMN IF NOT EXISTS → функция → пересчёт recordhash существующих строк
-- → пересоздание триггера. Копия синхронизирована с каноном; тождественность проверяет
-- tools/check-recordhash-consistency.py (check 5).
--
-- Применять вручную на КАЖДОЙ БД отдельно (knowledge/system/infra/sql-migrations.md, Решение 4).
-- Идемпотентно: повторный запуск ничего не ломает.
--
-- NB: md5 меняется у ВСЕХ строк — это ожидаемо (формула стала полной). Применять в одном окне
-- на LOCAL и PROD, иначе первый прогон sync увидит все строки изменёнными.

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

-- Триггер пересоздаётся явно: CREATE OR REPLACE TRIGGER есть только с PostgreSQL 14, а DROP
-- IF EXISTS + CREATE работает на любой поддерживаемой версии (BR-538 — тот же дефект в 44).
DROP TRIGGER IF EXISTS update_recordhash_site_users_trigger ON public.tbl_site_users;
CREATE TRIGGER update_recordhash_site_users_trigger
    BEFORE INSERT OR UPDATE ON public.tbl_site_users
    FOR EACH ROW
    EXECUTE FUNCTION public.update_tbl_site_users_recordhash();
