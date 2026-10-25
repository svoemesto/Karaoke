-- ==========================================================================================
-- tbl_site_users — КАНОНИЧЕСКИЙ recordhash-триггер (единственный источник истины).
--
-- Аудит #336, порция P-70, BR-044, OpenProject #393. До этой правки функция
-- update_tbl_site_users_recordhash() была скопирована в семь миграций (12/13/14/17/21/40/45),
-- и каждая версия знала только колонки, существовавшие на момент её написания:
--   * 12 — без is_editor, premium-сроков, персональной скидки и флагов 21/40/45;
--   * 13 — без premium-сроков и скидки;
--   * 14 — без personal_discount_percent;
--   * 45 (последняя по дате) — без personal_discount_percent (её вводила 17);
--   * 21/40 — тоже без скидки.
-- Колонки добавляются через ADD COLUMN IF NOT EXISTS, поэтому повторное применение старой
-- миграции проходило «успешно» и молча откатывало триггер к короткой формуле. Sync-движок
-- сравнивает строки ТОЛЬКО по recordhash (`to.recordhash != from.recordhash`), поэтому правки
-- выпавших колонок переставали считаться расхождением и на PROD не уезжали: у пользователей
-- на LOCAL оставались старые сроки премиума и персональная скидка.
--
-- Правило после правки: ЭТОТ ФАЙЛ — единственное каноническое определение функции
-- update_tbl_site_users_recordhash(). Любая миграция, добавляющая колонку в tbl_site_users,
-- обязана добавить её И СЮДА, И в свою миграцию — полный набор колонок (иначе тождественность
-- ломается и проверка ниже падает).
--
-- Инварианты проверяются статически, без БД:
--   python3 tools/check-recordhash-consistency.py
--   check 2: набор колонок md5 == набор @KaraokeDbTableField модели SiteUser
--            (last_login_at и created_at помечены useInHash = false — авто-поля, которые
--            намеренно не участвуют в diff, иначе каждый вход на сайт давал бы расхождение);
--            и совпадает с колонками бэкфилла ниже;
--   check 5: все миграции 12/13/14/17/21/40/45 объявляют ровно эту формулу (BR-044).
--
-- Порядок колонок: новые колонки добавляются ТОЛЬКО в конец цепочки (тогда md5 существующих
-- строк меняется ровно на одну добавку), а бэкфилл в конце файла обязан повторять формулу
-- один-в-один — иначе строки останутся со старым хэшем до первого UPDATE.
--
-- Применение: на КАЖДОЙ БД (LOCAL + PROD), лучше в одном окне обслуживания: формула md5
-- должна совпадать на обеих сторонах, иначе первый же прогон sync увидит все строки
-- изменёнными. Идемпотентно: ADD COLUMN IF NOT EXISTS + CREATE OR REPLACE + UPDATE.
-- ==========================================================================================

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
