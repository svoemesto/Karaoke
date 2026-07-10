-- Приветственное сообщение в чат с автором при первом получении премиум-доступа (любым способом —
-- подписка на сайт, Sponsr-синхронизация; см. SiteUser.sendWelcomePremiumMessageIfNeeded()).
-- Флаг гарантирует однократную отправку (в т.ч. при повторных вебхуках ЮKassa / скользящих окнах
-- Sponsr-синка, которые иначе повторно продлевали бы premium и триггерили бы сообщение заново).
--
-- Колонка входит в recordhash tbl_site_users, чтобы факт отправки проехал по LOCAL<->SERVER sync
-- (иначе LOCAL после "1 клика" считал бы, что сообщение ещё не отправлено, и продублировал бы его
-- при следующем локальном триггере того же пользователя).
--
-- ВАЖНО: применять вручную на КАЖДОЙ БД отдельно (LOCAL + PROD 79.174.95.69:8832).

ALTER TABLE public.tbl_site_users ADD COLUMN IF NOT EXISTS welcome_message_sent boolean DEFAULT false NOT NULL;

-- Задним числом гасим флаг тем, кто уже премиум на момент миграции — сообщение только для НОВЫХ
-- активаций премиума в будущем, не для уже действующих подписчиков на их ближайшем продлении/синке.
UPDATE public.tbl_site_users SET welcome_message_sent = true
WHERE is_premium = true
   OR is_permanent_premium = true
   OR (sponsr_premium_until IS NOT NULL AND sponsr_premium_until > now())
   OR (site_premium_until IS NOT NULL AND site_premium_until > now());

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
                                COALESCE(NEW.welcome_message_sent::TEXT, '')
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
    COALESCE(welcome_message_sent::TEXT, '')
) WHERE id > 0;
