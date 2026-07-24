-- Отдельный флаг «спецзаказных» авторов — у которых в коллекции <3 песен (т.е. песни
-- сделаны по индивидуальному заказу, а не вся дискография). Используется в karaoke-public
-- для виртуальной плашки "Отдельные песни разных авторов" в конце Закромов.
--
-- Один раз на LOCAL и PROD отдельно.
-- Колонка входит в recordhash tbl_authors (FR по конституции).

ALTER TABLE public.tbl_authors
    ADD COLUMN IF NOT EXISTS is_special_order BOOLEAN DEFAULT FALSE NOT NULL;

-- Обновляем recordhash-триггер: ВАЖНО включить новое поле в md5,
-- иначе LOCAL после "1 клика" считал бы, что поле ещё не синхронизировано.
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
                                COALESCE(NEW.is_special_order::TEXT, '')
        );
RETURN NEW;
END;
$$;

-- Backfill: помечаем как "одиночных" авторов, у которых <3 песен в коллекции
-- (id_status>=3 = готовая песня). Одноразовая операция.
UPDATE public.tbl_authors
SET is_special_order = true
WHERE skip = false
  AND author IN (
      SELECT song_author
      FROM public.tbl_settings
      WHERE id_status >= 3
        AND btrim(song_author) <> ''
      GROUP BY song_author
      HAVING count(*) < 3
  );

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
    COALESCE(is_special_order::TEXT, '')
) WHERE id > 0;
