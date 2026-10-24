-- ==========================================================================================
-- tbl_authors — КАНОНИЧЕСКИЙ recordhash-триггер (единственный источник истины).
--
-- Аудит #336, порция P-73, BR-541, OpenProject #363. До этой правки файл отставал от схемы
-- модели `Author`:
--   * в md5 не входили description / short_description / warning (миграция 31),
--     is_special_order (27), sort_order (46), ready_songs_count / total_songs_count (44);
--   * сам файл ТРЕБОВАЛ колонку aliases, которой нет в базовой схеме — она появляется
--     только после deploy/karaoke-db/18_author_aliases.sql.
--
-- Последствия были ровно два, в зависимости от состояния БД:
--   (а) на БД без aliases триггер падал на КАЖДОЙ записи tbl_authors
--       (`record "new" has no field "aliases"`) — редактирование автора в админке не работало;
--   (б) на БД с aliases правки description/warning/«по спецзаказу»/порядка/счётчиков не попадали
--       в diff LOCAL↔SERVER: sync-движок сравнивает строки ТОЛЬКО по recordhash
--       (`to.recordhash != from.recordhash`, KaraokeDbTable.getListHashes), поэтому изменение
--       непокрытого поля не считается расхождением и на PROD не уезжает. Публичные «Закрома»
--       показывали устаревшие описание автора, предупреждение, бейдж «по спецзаказу» и порядок
--       плиток, а автор мог не появиться в публичной сетке (отбор по ready_songs_count).
--
-- Правило после правки: ЭТОТ ФАЙЛ — единственное определение функции
-- update_tbl_authors_recordhash(). Любая миграция, добавляющая колонку в tbl_authors, обязана
-- добавить её сюда (и в свою миграцию — ради бэкфилла значений, как это сделано в 44/46).
--
-- Инвариант проверяется статически, без БД:
--   python3 tools/check-recordhash-consistency.py
--   check 2: множество колонок md5 == все @KaraokeDbTableField модели Author
--            (кроме помеченных useInHash = false) + денормализованные счётчики, которые
--            поддерживает БД-триггер trg_tbl_songs_update_author_counts (миграция 44);
--            и совпадает с колонками бэкфилла ниже.
--
-- Порядок колонок: новые колонки добавляются ТОЛЬКО в конец цепочки (тогда md5 существующих
-- строк меняется ровно на одну добавку), а бэкфилл в конце файла обязан повторять формулу
-- один-в-один — иначе строки останутся со старым хэшем до первого UPDATE.
--
-- Применение: на КАЖДОЙ БД (LOCAL + PROD), лучше в одном окне обслуживания: формула md5
-- должна совпадать на обеих сторонах, иначе первый же прогон sync увидит все строки
-- изменёнными. Идемпотентно: ADD COLUMN IF NOT EXISTS + CREATE OR REPLACE + UPDATE.
-- ==========================================================================================

-- ==========================================================================================
-- 1. Колонки, которые участвуют в md5. Все определения совпадают с исходными миграциями
--    (18 / 27 / 31 / 44 / 46) — файл обязан быть применимым и к БД, где эти миграции ещё не
--    применены, иначе CREATE OR REPLACE ... пройдёт, а триггер упадёт на первой записи.
-- ==========================================================================================
ALTER TABLE public.tbl_authors ADD COLUMN IF NOT EXISTS recordhash VARCHAR(32);
ALTER TABLE public.tbl_authors ADD COLUMN IF NOT EXISTS aliases text DEFAULT '' NOT NULL;
ALTER TABLE public.tbl_authors ADD COLUMN IF NOT EXISTS description TEXT DEFAULT '' NOT NULL;
ALTER TABLE public.tbl_authors ADD COLUMN IF NOT EXISTS short_description VARCHAR(255) DEFAULT '' NOT NULL;
ALTER TABLE public.tbl_authors ADD COLUMN IF NOT EXISTS warning VARCHAR(255) DEFAULT '' NOT NULL;
ALTER TABLE public.tbl_authors ADD COLUMN IF NOT EXISTS is_special_order BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE public.tbl_authors ADD COLUMN IF NOT EXISTS sort_order INTEGER NOT NULL DEFAULT 0;
ALTER TABLE public.tbl_authors ADD COLUMN IF NOT EXISTS ready_songs_count BIGINT NOT NULL DEFAULT 0;
ALTER TABLE public.tbl_authors ADD COLUMN IF NOT EXISTS total_songs_count BIGINT NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_tbl_authors_recordhash ON tbl_authors(recordhash);

-- ==========================================================================================
-- 2. Функция-триггер. В PL/pgSQL идентификатор, не найденный среди переменных функции,
--    передаётся SQL-парсеру как ссылка на колонку; у функции-триггера нет FROM, поэтому любое
--    «голое» имя колонки — это ошибка времени выполнения (`column "x" does not exist`), которую
--    CREATE FUNCTION не ловит. Все ссылки — только через NEW.
-- ==========================================================================================
CREATE OR REPLACE FUNCTION public.update_tbl_authors_recordhash()
RETURNS TRIGGER AS
$$
BEGIN
    NEW.recordhash = md5(
        COALESCE(NEW.id::TEXT, '') ||
        COALESCE(NEW.author, '') ||
        COALESCE(NEW.ym_id, '') ||
        COALESCE(NEW.vk_id, '') ||
        COALESCE(NEW.last_album_ym, '') ||
        COALESCE(NEW.last_album_vk, '') ||
        COALESCE(NEW.last_album_processed, '') ||
        COALESCE(NEW.watched::TEXT, '') ||
        COALESCE(NEW.skip::TEXT, '') ||
        COALESCE(NEW.aliases, '') ||
        COALESCE(NEW.is_special_order::TEXT, '') ||
        COALESCE(NEW.sort_order::TEXT, '') ||
        COALESCE(NEW.description, '') ||
        COALESCE(NEW.short_description, '') ||
        COALESCE(NEW.warning, '') ||
        COALESCE(NEW.ready_songs_count::TEXT, '') ||
        COALESCE(NEW.total_songs_count::TEXT, '')
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE TRIGGER update_recordhash_authors_trigger
    BEFORE UPDATE OR INSERT
    ON public.tbl_authors
    FOR EACH ROW
EXECUTE FUNCTION public.update_tbl_authors_recordhash();

-- ==========================================================================================
-- 3. Бэкфилл recordhash для существующих строк (триггер срабатывает только на новые
--    INSERT/UPDATE). Формула ДОЛЖНА совпадать с функцией выше — это проверяет guard.
-- ==========================================================================================
UPDATE public.tbl_authors
SET recordhash = md5(
    COALESCE(id::TEXT, '') ||
    COALESCE(author, '') ||
    COALESCE(ym_id, '') ||
    COALESCE(vk_id, '') ||
    COALESCE(last_album_ym, '') ||
    COALESCE(last_album_vk, '') ||
    COALESCE(last_album_processed, '') ||
    COALESCE(watched::TEXT, '') ||
    COALESCE(skip::TEXT, '') ||
    COALESCE(aliases, '') ||
    COALESCE(is_special_order::TEXT, '') ||
    COALESCE(sort_order::TEXT, '') ||
    COALESCE(description, '') ||
    COALESCE(short_description, '') ||
    COALESCE(warning, '') ||
    COALESCE(ready_songs_count::TEXT, '') ||
    COALESCE(total_songs_count::TEXT, '')
) WHERE id > 0;

-- ==========================================================================================
-- Runbook применения (миграции — manual, knowledge/system/infra/sql-migrations.md, Решение 4)
-- ==========================================================================================
-- 1. LOCAL:  psql -f deploy/recordhash_authors.sql
-- 2. PROD:   docker exec -i karaoke-db psql -U postgres -d karaoke \
--              < /root/Karaoke/deploy/recordhash_authors.sql
-- 3. Проверить на ОБЕИХ БД, что тело функции содержит 17 колонок и все — с NEW.:
--      SELECT prosrc FROM pg_proc WHERE proname = 'update_tbl_authors_recordhash';
--    и что хэши пересчитаны (не NULL):
--      SELECT count(*) FILTER (WHERE recordhash IS NULL) FROM tbl_authors;
-- 4. Убедиться, что админка сохраняет автора (INSERT/UPDATE tbl_authors не падает) и что
--    правка «Описание»/«Предупреждение» даёт diff на вкладке синхронизации.
-- ==========================================================================================
