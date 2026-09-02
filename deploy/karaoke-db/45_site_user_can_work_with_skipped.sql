-- Флаг "Может работать со SKIP-авторами и песнями" (canWorkWithSkipped):
-- залогиненный пользователь (админ/редактор) с этим флагом видит SKIP-контент,
-- скрытый от публичной поверхности механизмами SKIP (`tbl_authors.skip = TRUE`
-- или тег `SKIP` в `tbl_songs.tags`). См. specs/293-skip-author-toggle/spec.md.
--
-- Колонка tbl_site_users, входит в recordhash — иначе изменение флага не
-- проедет по LOCAL<->SERVER sync (Constitution §III, pass 28 история
-- 24_song_type — recordhash-триггер ОБЯЗАН быть пересоздан при добавлении
-- колонки).
--
-- Apply:
--   локально:  docker exec -i karaoke-db psql -U postgres -d karaoke < deploy/karaoke-db/45_site_user_can_work_with_skipped.sql
--   прод:      ssh root@${PROD_HOST:-188.119.64.111} \
--                'docker exec -i karaoke-db psql -U postgres -d karaoke \
--                 < /root/Karaoke/deploy/karaoke-db/45_site_user_can_work_with_skipped.sql'
--
-- Идемпотентен: ADD COLUMN IF NOT EXISTS + CREATE OR REPLACE FUNCTION.

ALTER TABLE public.tbl_site_users
    ADD COLUMN IF NOT EXISTS can_work_with_skipped boolean DEFAULT false NOT NULL;

CREATE OR REPLACE FUNCTION public.update_tbl_site_users_recordhash() RETURNS trigger
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
                                COALESCE(NEW.ban_reason, '') ||
                                COALESCE(NEW.max_favorites::TEXT, '') ||
                                COALESCE(NEW.max_playlists::TEXT, '') ||
                                COALESCE(NEW.max_playlist_items::TEXT, '') ||
                                COALESCE(NEW.is_editor::TEXT, '') ||
                                COALESCE(NEW.sponsr_premium_until::TEXT, '') ||
                                COALESCE(NEW.site_premium_until::TEXT, '') ||
                                COALESCE(NEW.welcome_message_sent::TEXT, '') ||
                                COALESCE(NEW.can_self_assign_tasks::TEXT, '') ||
                                COALESCE(NEW.can_work_with_skipped::TEXT, '')
        );
RETURN NEW;
END;
$$;

-- Пересчитать md5 для существующих строк (новая колонка default = false, но
-- md5-функция поменялась — БЕЗ этого UPDATE 'запись на диске' не совпадёт с
-- тем, что триггер сгенерит при следующем изменении, и diff'ы в sync пойдут
-- неверные).
UPDATE public.tbl_site_users SET recordhash = md5(
    COALESCE(id::TEXT, '') ||
    COALESCE(email, '') ||
    COALESCE(password_hash, '') ||
    COALESCE(display_name, '') ||
    COALESCE(sponsr_uid, '') ||
    COALESCE(is_premium::TEXT, '') ||
    COALESCE(is_permanent_premium::TEXT, '') ||
    COALESCE(is_banned::TEXT, '') ||
    COALESCE(ban_reason, '') ||
    COALESCE(max_favorites::TEXT, '') ||
    COALESCE(max_playlists::TEXT, '') ||
    COALESCE(max_playlist_items::TEXT, '') ||
    COALESCE(is_editor::TEXT, '') ||
    COALESCE(sponsr_premium_until::TEXT, '') ||
    COALESCE(site_premium_until::TEXT, '') ||
    COALESCE(welcome_message_sent::TEXT, '') ||
    COALESCE(can_self_assign_tasks::TEXT, '') ||
    COALESCE(can_work_with_skipped::TEXT, '')
) WHERE id > 0;