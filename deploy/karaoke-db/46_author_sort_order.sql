-- Сортировка авторов в публичной сетке Закромов.
--
-- Позволяет редактору задавать порядок плашек авторов в Закромах: ненулевые
-- sort_order идут перед нулевыми (внутри обеих групп — по алфавиту для нулевых
-- и по возрастанию sort_order для ненулевых).
--
-- Один раз на LOCAL и PROD отдельно.
-- Колонка входит в recordhash tbl_authors (FR по конституции).

ALTER TABLE public.tbl_authors
    ADD COLUMN IF NOT EXISTS sort_order INTEGER NOT NULL DEFAULT 0;

-- Обновляем recordhash-триггер: ВАЖНО включить новое поле в md5,
-- иначе изменения sort_order не будут считаться расхождениями LOCAL↔SERVER.
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
                                COALESCE(NEW.sort_order::TEXT, '')
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
    COALESCE(sort_order::TEXT, '')
) WHERE id > 0;
