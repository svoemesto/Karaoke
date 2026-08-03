-- Kill-switch для авто-новостей (specs/125-news-flags-backfix). Лежит в tbl_public_settings
-- (Postgres), чтобы быть одинаково доступным и на admin-машине (karaoke-app), и на проде
-- (karaoke-web) — в отличие от KaraokeProperties (/sm-karaoke/system/Karaoke.properties, файл
-- только на admin). tbl_public_settings — тот же слой, что и Yandex SmartCaptcha:
-- см. CaptchaConfigService.kt:35 (karaoke-web) и PublicSettingsController.kt (karaoke-app).
--
-- Значение читается в News.createAutoAnnouncement (News.kt) прямым JDBC-запросом. Endpoint для
-- включения/снимания — POST /api/properties/setproperty (новый контроллер
-- PublicSettingsWebController.kt:karaoke-web/.../controllers, т.к. существующий
-- /api/publicsettings/update живёт в karaoke-app и не доступен на проде — Spring не сканирует
-- com.svoemesto.karaokeapp.* в karaoke-web).
--
-- Значение: "true" = kill-switch активен (auto-новости заблокированы); "" или любое другое !=
-- "true" = kill-switch снят (default). Использовать строковое представление (а не boolean) —
-- для совместимости с tbl_public_settings.value TEXT DEFAULT ''.

-- Идемпотентно: ON CONFLICT DO NOTHING.
INSERT INTO public.tbl_public_settings (key, value, description)
VALUES (
    'newsAutoPublishKillSwitch',
    '',
    'Kill-switch для авто-новостей (specs/125-news-flags-backfix): при value=true News.createAutoAnnouncement возвращает null без INSERT — блокирует обе точки auto-новостей (SongReleaseAnnouncementService.detectAndAnnounceAvailability из sync и checkOnAirWindow из scheduler) во время sync-окна после backfill флагов публикации. Ручные новости (News.createNew, source=manual) не блокируются. Включается/снимается через POST /api/properties/setproperty без рестарта контейнера.'
)
ON CONFLICT (key) DO NOTHING;
