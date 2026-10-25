-- BR-045 (аудит #336 / P-70-07, OpenProject #393): пересоздать GIN-индекс полнотекстового
-- поиска по result_text — он был построен по константе и лежал не на той таблице.
--
-- ЧТО БЫЛО. `01_initdb.sql` создавал
--     CREATE INDEX idx_gin_result_text ON public.tbl_settings
--         USING gin (to_tsvector('russian'::regconfig, 'result_text'));
-- Второй аргумент — СТРОКОВЫЙ ЛИТЕРАЛ `'result_text'` в кавычках, а не колонка: выражение
-- одинаково для всех строк, индекс бесполезен. Поиск же выполняется по tbl_songs
-- (`Song.loadList`: `to_tsvector('russian', result_text) @@ plainto_tsquery('russian', …)`) —
-- то есть индекса у него не было вообще, а имя idx_gin_result_text было занято (имена индексов
-- уникальны в схеме), так что корректный индекс нельзя было создать без DROP.
--
-- На БД, собранных из репозитория (LOCAL), 01/02 уже исправлены — на существующую БД это не
-- влияет (индекс уже создан). Поэтому нужен этот шаг: DROP старого индекса + CREATE заново по
-- колонке. Прод-дамп (`deploy/new_comp/.../karaoke_clear_dump.sql:2146`) уже содержит корректное
-- определение на tbl_songs — на проде миграция ничего не меняет, кроме пересоздания индекса.
--
-- Идемпотентно: DROP IF EXISTS + CREATE IF NOT EXISTS. Таблица — tbl_songs (старое имя
-- tbl_settings переименовано миграцией 28; состояние «28 не применена» не поддерживается).
-- Применять вручную на КАЖДОЙ БД отдельно (LOCAL + PROD), см.
-- knowledge/system/infra/sql-migrations.md, Решение 4.
--
-- Проверка:
--   \d tbl_songs | grep idx_gin_result_text
--   SELECT indexdef FROM pg_indexes
--    WHERE indexname = 'idx_gin_result_text';  -- выражение содержит result_text, не 'result_text'
-- План запроса: EXPLAIN (COSTS OFF) SELECT id FROM tbl_songs
--   WHERE to_tsvector('russian', result_text) @@ plainto_tsquery('russian', 'слово');
--   -- ожидается Bitmap Index Scan по idx_gin_result_text, а не Seq Scan.

DO $$
DECLARE
    target regclass;
BEGIN
    -- В initdb-цепочке файл идёт после 28_rename_settings_to_songs.sql, поэтому таблица
    -- называется tbl_songs. Состояние «миграция 28 ещё не применена» не поддерживается.
    target := to_regclass('public.tbl_songs');

    IF target IS NULL THEN
        RAISE NOTICE 'tbl_songs не найдена — миграция не применима';
        RETURN;
    END IF;

    -- Старое (бесполезное) определение могло быть создано под этим же именем.
    EXECUTE 'DROP INDEX IF EXISTS public.idx_gin_result_text';

    -- Выражение обязано ссылаться на колонку (без кавычек вокруг имени) и совпадать
    -- с выражением запроса: to_tsvector('russian'::regconfig, result_text).
    EXECUTE format(
        'CREATE INDEX IF NOT EXISTS idx_gin_result_text ON %s '
        'USING gin (to_tsvector(''russian''::regconfig, result_text))',
        target
    );

    RAISE NOTICE 'idx_gin_result_text пересоздан на %', target;
END;
$$;
