-- Сортировка авторов в публичной сетке Закромов.
--
-- Позволяет редактору задавать порядок плашек авторов в Закромах: ненулевые
-- sort_order идут перед нулевыми (внутри обеих групп — по алфавиту для нулевых
-- и по возрастанию sort_order для ненулевых).
--
-- Один раз на LOCAL и PROD отдельно.
-- Колонка входит в recordhash tbl_authors (FR по конституции).
--
-- BR-534 (аудит #336 / P-71-02, OpenProject #371): эта миграция ПЕРЕОПРЕДЕЛЯЛА
-- update_tbl_authors_recordhash() и теряла пять колонок, введённых ранее:
--   description, short_description, warning   — миграция 31 (2026-07-27)
--   ready_songs_count, total_songs_count      — миграция 44 (2026-08-31)
-- Итоговая формула содержала 12 колонок вместо 17. Sync-diff сравнивает
-- строки ТОЛЬКО по recordhash (KaraokeDbTable.getListHashes), поэтому правка
-- только этих полей не давала расхождения LOCAL↔SERVER: предупреждение и
-- описание автора не уезжали на публичный периметр, а счётчики песен —
-- тоже (автор мог не попасть в публичную плитку).
--
-- Ниже:
--   1) миграция создаёт все колонки, на которые ссылается функция
--      (IF NOT EXISTS) — иначе триггер с NEW.<нет колонки> падал бы на
--      каждой записи tbl_authors;
--   2) функция собирает хэш из ПОЛНОГО набора колонок (17);
--   3) backfill пересчитывает хэш у существующих строк.
--
-- NB: набор и ПОРЯДОК колонок должен совпадать с каноническим файлом
-- deploy/recordhash_authors.sql (BR-541, пакет #363) и с миграцией 18 —
-- иначе применение одного из них откатывает функцию к меньшему набору,
-- а разные формулы дают разный md5 у одной и той же строки (LOCAL и SERVER
-- навсегда видят расхождение и гоняют полный re-sync авторов).
--
-- Порядок канонический: id, author, ym_id, vk_id, last_album_ym, last_album_vk,
-- last_album_processed, watched, skip, aliases, is_special_order, sort_order,
-- description, short_description, warning, ready_songs_count, total_songs_count.
-- Первые 12 — ровно тот префикс, который был развёрнут на LOCAL/PROD этой
-- миграцией до правки; пять недостающих полей добавлены В КОНЕЦ, поэтому
-- смысл уже существующих 12 полей в хэше не изменился. Порядок менять нельзя:
-- он зафиксирован здесь как единственный и проверяется guard'ом
-- tools/check-deploy-hardening.sh (BR-534: сравнение порядка 46 и 18).


ALTER TABLE public.tbl_authors
    ADD COLUMN IF NOT EXISTS sort_order INTEGER NOT NULL DEFAULT 0;

-- Колонки, на которые ссылается recordhash-функция. Создаём их здесь,
-- чтобы миграция была самодостаточной (её можно применить к БД, где
-- 31/44 ещё не применены, — иначе триггер падал бы на каждой записи).
ALTER TABLE public.tbl_authors
    ADD COLUMN IF NOT EXISTS aliases text DEFAULT '' NOT NULL;
ALTER TABLE public.tbl_authors
    ADD COLUMN IF NOT EXISTS description text DEFAULT '' NOT NULL;
ALTER TABLE public.tbl_authors
    ADD COLUMN IF NOT EXISTS short_description character varying(255) DEFAULT '' NOT NULL;
ALTER TABLE public.tbl_authors
    ADD COLUMN IF NOT EXISTS warning character varying(255) DEFAULT '' NOT NULL;
ALTER TABLE public.tbl_authors
    ADD COLUMN IF NOT EXISTS is_special_order boolean DEFAULT false NOT NULL;
ALTER TABLE public.tbl_authors
    ADD COLUMN IF NOT EXISTS ready_songs_count bigint DEFAULT 0 NOT NULL;
ALTER TABLE public.tbl_authors
    ADD COLUMN IF NOT EXISTS total_songs_count bigint DEFAULT 0 NOT NULL;

-- Обновляем recordhash-триггер: ВАЖНО включить ВСЕ поля в md5,
-- иначе изменения sort_order (или description/warning/счётчиков)
-- не будут считаться расхождениями LOCAL↔SERVER.
CREATE OR REPLACE FUNCTION public.update_tbl_authors_recordhash() RETURNS trigger
    LANGUAGE plpgsql
AS $$
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
$$;

-- Backfill recordhash для существующих строк (триггер сработает только на новые UPDATE/INSERT).
UPDATE public.tbl_authors SET recordhash = md5(
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
