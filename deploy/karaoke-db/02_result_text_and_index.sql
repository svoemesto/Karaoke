-- Идемпотентный патч (BR-039, аудит #336 / P-70-01, OpenProject #371).
--
-- Было:
--   alter table tbl_settings add result_text TEXT;
--   CREATE INDEX idx_gin_result_text ON public.tbl_settings USING gin (to_tsvector('russian'::regconfig, 'result_text'));
--
-- Что не так было: содержимое этого файла уже есть в 01_initdb.sql
-- (result_text — 01:95, idx_gin_result_text — 01:182), поэтому на любой БД,
-- созданной из 01, обе команды падали:
--   ERROR: column "result_text" of relation "tbl_settings" already exists
-- Entry point образа postgres:16 выполняет init-файлы через
-- `psql -v ON_ERROR_STOP=1` — первая же ошибка роняет инициализацию,
-- контейнер с restart: always уходит в цикл рестартов (БД с нуля не
-- поднимается ни на dev-машине, ни при восстановлении на новой).
--
-- Стало: обе команды идемпотентны — файл одинаково применим к БД из 01
-- и к БД, где патч уже накатан.
--
-- NB: сам GIN-индекс построен по константе 'result_text', а не по колонке
-- (P-70-07, S3, отдельная запись аудита) — здесь не меняется.

ALTER TABLE public.tbl_settings ADD COLUMN IF NOT EXISTS result_text TEXT;
CREATE INDEX IF NOT EXISTS idx_gin_result_text ON public.tbl_settings USING gin (to_tsvector('russian'::regconfig, 'result_text'));
