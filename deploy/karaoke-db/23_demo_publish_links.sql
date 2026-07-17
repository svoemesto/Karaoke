-- Публикация DEMO-версии (RenderVersion.DEMO — короткий ознакомительный фрагмент караоке,
-- см. PlayerMp4RenderService.kt) на площадках VK/Dzen/Telegram/Max, по образцу id_*_lyrics/
-- id_*_karaoke/id_*_chords/id_*_melody (04_melody.sql). Участвует в recordhash tbl_settings и
-- tbl_settings_sync — применять ВМЕСТЕ с правками deploy/recordhash_settings.sql и
-- deploy/recordhash_settings_sync.sql. Применять на LOCAL и на PROD-БД отдельно (сама на сервер
-- не попадает).

ALTER TABLE public.tbl_settings ADD COLUMN IF NOT EXISTS id_dzen_demo character varying(40) DEFAULT '';
ALTER TABLE public.tbl_settings ADD COLUMN IF NOT EXISTS version_dzen_demo integer DEFAULT 0;
ALTER TABLE public.tbl_settings ADD COLUMN IF NOT EXISTS id_vk_demo character varying(20) DEFAULT '';
ALTER TABLE public.tbl_settings ADD COLUMN IF NOT EXISTS version_vk_demo integer DEFAULT 0;
ALTER TABLE public.tbl_settings ADD COLUMN IF NOT EXISTS id_telegram_demo character varying(7) DEFAULT '';
ALTER TABLE public.tbl_settings ADD COLUMN IF NOT EXISTS version_telegram_demo integer DEFAULT 0;
ALTER TABLE public.tbl_settings ADD COLUMN IF NOT EXISTS id_max_demo character varying(20) DEFAULT '';
ALTER TABLE public.tbl_settings ADD COLUMN IF NOT EXISTS version_max_demo integer DEFAULT 0;

ALTER TABLE public.tbl_settings_sync ADD COLUMN IF NOT EXISTS id_dzen_demo character varying(40) DEFAULT '';
ALTER TABLE public.tbl_settings_sync ADD COLUMN IF NOT EXISTS version_dzen_demo integer DEFAULT 0;
ALTER TABLE public.tbl_settings_sync ADD COLUMN IF NOT EXISTS id_vk_demo character varying(20) DEFAULT '';
ALTER TABLE public.tbl_settings_sync ADD COLUMN IF NOT EXISTS version_vk_demo integer DEFAULT 0;
ALTER TABLE public.tbl_settings_sync ADD COLUMN IF NOT EXISTS id_telegram_demo character varying(7) DEFAULT '';
ALTER TABLE public.tbl_settings_sync ADD COLUMN IF NOT EXISTS version_telegram_demo integer DEFAULT 0;
ALTER TABLE public.tbl_settings_sync ADD COLUMN IF NOT EXISTS id_max_demo character varying(20) DEFAULT '';
ALTER TABLE public.tbl_settings_sync ADD COLUMN IF NOT EXISTS version_max_demo integer DEFAULT 0;

CREATE INDEX IF NOT EXISTS tbl_settings_id_dzen_demo_index ON public.tbl_settings USING btree (id_dzen_demo);
CREATE INDEX IF NOT EXISTS tbl_settings_id_vk_demo_index ON public.tbl_settings USING btree (id_vk_demo);
CREATE INDEX IF NOT EXISTS tbl_settings_id_telegram_demo_index ON public.tbl_settings USING btree (id_telegram_demo);
CREATE INDEX IF NOT EXISTS tbl_settings_id_max_demo_index ON public.tbl_settings USING btree (id_max_demo);
