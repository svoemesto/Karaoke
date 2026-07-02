-- Небольшая key/value таблица настроек, нужных сервисам, которые реально работают на боевом
-- сервере (karaoke-web). В отличие от ~150 файловых настроек KaraokeProperties
-- (/sm-karaoke/system/Karaoke.properties, есть только на локальной машине администратора — karaoke-app
-- на сервере вообще не разворачивается), эта таблица живёт в Postgres и потому одинаково доступна
-- через Connection.local()/Connection.remote() — тот же паттерн, что и для tbl_site_users.
CREATE TABLE public.tbl_public_settings (
    key character varying(255) NOT NULL,
    value text DEFAULT '' NOT NULL,
    description character varying(1024) DEFAULT '' NOT NULL,
    last_update timestamp without time zone DEFAULT now()
);

ALTER TABLE ONLY public.tbl_public_settings
    ADD CONSTRAINT tbl_public_settings_pkey PRIMARY KEY (key);

INSERT INTO public.tbl_public_settings (key, value, description) VALUES
    ('yandexSmartCaptchaClientKey', '', 'Yandex SmartCaptcha — клиентский ключ (публичный, для формы регистрации на сайте)'),
    ('yandexSmartCaptchaServerKey', '', 'Yandex SmartCaptcha — серверный ключ (секретный, для валидации на бэкенде)');
