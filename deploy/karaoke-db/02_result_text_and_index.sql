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
-- BR-045 (аудит #336 / P-70-07, OpenProject #393): выражение индекса было построено по
-- строковой КОНСТАНТЕ 'result_text' (в кавычках) — одинаково для всех строк, поиск его не
-- использует. Плюс на БД, где индекс уже создан (из 01), `CREATE INDEX IF NOT EXISTS` молча
-- оставлял старое определение. Теперь индекс пересоздаётся по колонке: DROP IF EXISTS +
-- CREATE IF NOT EXISTS. Имя индекса при переименовании таблицы (28) сохраняется.

ALTER TABLE public.tbl_settings ADD COLUMN IF NOT EXISTS result_text TEXT;
DROP INDEX IF EXISTS public.idx_gin_result_text;
CREATE INDEX IF NOT EXISTS idx_gin_result_text ON public.tbl_settings USING gin (to_tsvector('russian'::regconfig, result_text));
