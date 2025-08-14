alter table tbl_settings add id_boosty_files character varying(40);
CREATE INDEX tbl_settings_id_boosty_files_index ON public.tbl_settings USING btree (id_boosty_files);