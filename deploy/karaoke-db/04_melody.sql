alter table public.tbl_settings
    add id_youtube_melody varchar(40) default '';

alter table public.tbl_settings
    add id_vk_melody varchar(20) default '';

alter table public.tbl_settings
    add status_process_melody varchar(20) default '';

alter table public.tbl_settings
    add id_telegram_melody varchar(7) default '';

alter table public.tbl_settings
    add id_pl_melody varchar(20) default '';

CREATE INDEX tbl_settings_id_youtube_melody_index ON public.tbl_settings USING btree (id_youtube_melody);
CREATE INDEX tbl_settings_id_vk_melody_index ON public.tbl_settings USING btree (id_vk_melody);
CREATE INDEX tbl_settings_id_telegram_melody_index ON public.tbl_settings USING btree (id_telegram_melody);
CREATE INDEX tbl_settings_id_pl_melody_index ON public.tbl_settings USING btree (id_pl_melody);
