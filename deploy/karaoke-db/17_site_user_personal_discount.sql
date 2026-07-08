-- Постоянная персональная скидка пользователя сайта (устанавливается вручную админом в webvue3,
-- SiteUsersController.update). Суммируется поверх ЛЮБОЙ акции (не конкурирует с tbl_promo_rules —
-- применяется дополнительно к итоговой цене после акции) и действует на любой заказ (одиночная
-- подписка на песню, корзина, подписка на сайт). 0 = скидки нет (по умолчанию у всех).
--
-- Колонка входит в recordhash tbl_site_users, чтобы изменение проехало по LOCAL<->SERVER sync.
--
-- ВАЖНО: применять вручную на КАЖДОЙ БД отдельно (LOCAL + PROD 79.174.95.69:8832).

ALTER TABLE public.tbl_site_users ADD COLUMN IF NOT EXISTS personal_discount_percent numeric(5,2) DEFAULT 0 NOT NULL;

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
                                COALESCE(NEW.personal_discount_percent::TEXT, '')
        );
RETURN NEW;
END;
$$;

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
    COALESCE(personal_discount_percent::TEXT, '')
) WHERE id > 0;
