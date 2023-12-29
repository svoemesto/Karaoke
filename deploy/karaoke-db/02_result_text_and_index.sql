alter table tbl_settings add result_text TEXT;
CREATE INDEX idx_gin_result_text ON public.tbl_settings USING gin (to_tsvector('russian'::regconfig, 'result_text'));