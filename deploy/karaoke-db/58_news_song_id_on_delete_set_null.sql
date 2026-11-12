-- KARAOKE-429 (спека 739 «удаление песни с каскадной уборкой»), FR-009, FR-046.
--
-- Зачем: удаление песни блокируется внешними ключами на tbl_songs.
--   * tbl_news.song_id — NO ACTION: новость песни не даёт удалить строку песни.
--     По FR-009 новость должна ОСТАТЬСЯ в ленте, но потерять привязку → ON DELETE SET NULL.
--   * tbl_song_news_announced.song_id — таблица-метка «решение об анонсе принято»
--     (миграция 33). Колонка входит в PRIMARY KEY(song_id), поэтому SET NULL неприменим;
--     таблица данных песни не хранит → ON DELETE CASCADE (решение лида В-02).
--
-- ВАЖНО (факт 2026-11-12): таблица tbl_song_news_announced удалена миграцией
-- 34_cleanup_song_news_announced.sql на LOCAL, но может ещё существовать на PROD.
-- Поэтому ALTER для неё выполняется только если таблица есть — иначе миграция падает
-- с «relation does not exist». Миграция идемпотентна: повторное применение ничего
-- не ломает.
--
-- Проверка после применения:
--   SELECT confdeltype FROM pg_constraint
--    WHERE conrelid='public.tbl_news'::regclass AND contype='f';
--   -- ожидается 'n' (SET NULL)
--   SELECT confdeltype FROM pg_constraint
--    WHERE conrelid='public.tbl_song_news_announced'::regclass AND contype='f';
--   -- ожидается 'c' (CASCADE), если таблица есть на этой БД
--
-- Применять вручную на КАЖДОЙ БД отдельно (Решение 4 в
-- knowledge/system/infra/sql-migrations.md). Порядок: 58 → 59 → 60.

-- 1) tbl_news: новость остаётся, привязка обнуляется.
ALTER TABLE public.tbl_news DROP CONSTRAINT IF EXISTS tbl_news_song_id_fkey;
ALTER TABLE public.tbl_news
    ADD CONSTRAINT tbl_news_song_id_fkey
    FOREIGN KEY (song_id) REFERENCES public.tbl_songs(id) ON DELETE SET NULL;

-- 2) tbl_song_news_announced: метка «анонс принят» удаляется вместе с песней.
--    Блокиров на случай, когда таблицы нет (миграция 34 удалила её на LOCAL).
DO $$
BEGIN
    IF to_regclass('public.tbl_song_news_announced') IS NOT NULL THEN
        ALTER TABLE public.tbl_song_news_announced
            DROP CONSTRAINT IF EXISTS tbl_song_news_announced_song_id_fkey;
        ALTER TABLE public.tbl_song_news_announced
            ADD CONSTRAINT tbl_song_news_announced_song_id_fkey
            FOREIGN KEY (song_id) REFERENCES public.tbl_songs(id) ON DELETE CASCADE;
    END IF;
END $$;
