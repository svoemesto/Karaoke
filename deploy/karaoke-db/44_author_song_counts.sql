-- Кэш счётчиков песен автора в tbl_authors (specs/286-author-song-counts-cache).
--
-- Добавляет две денормализованные колонки:
--   * ready_songs_count — песен автора с id_status >= 6
--   * total_songs_count — всех песен автора в tbl_songs
--
-- Колонки поддерживаются актуальными DB-триггером trg_tbl_songs_update_author_counts
-- (создаётся в этой же миграции, ТОЛЬКО на LOCAL-БД — tbl_songs живёт только там).
-- На SERVER-БД триггер создаётся тоже, но остаётся no-op (tbl_songs пуста); это
-- упрощает миграцию и не требует условной логики.
--
-- Миграция ОБНОВЛЯЕТ recordhash-функцию для tbl_authors — добавляет новые колонки
-- в канонизированную строку md5, иначе sync сломается (Constitution Principle III).
--
-- Backfill существующих авторов — одним UPDATE с подзапросом COUNT(*) FROM tbl_songs.
-- Авторы без песен получают (0, 0) благодаря LEFT JOIN + COALESCE.
--
-- Применение: ОДИН РАЗ на LOCAL-БД и на SERVER-БД отдельно (см. шапку
-- deploy/karaoke-db/27_author_special_order.sql и 32_song_status_lifecycle.sql).
-- Идемпотентно: ADD COLUMN IF NOT EXISTS, CREATE OR REPLACE FUNCTION,
-- CREATE OR REPLACE TRIGGER, UPDATE — повторный запуск не ломает.

-- ===== 1. Новые колонки в tbl_authors =====
ALTER TABLE public.tbl_authors
    ADD COLUMN IF NOT EXISTS ready_songs_count BIGINT NOT NULL DEFAULT 0;

ALTER TABLE public.tbl_authors
    ADD COLUMN IF NOT EXISTS total_songs_count BIGINT NOT NULL DEFAULT 0;

-- ===== 2. Пересоздание recordhash-функции для tbl_authors =====
-- ВАЖНО: новые колонки должны попасть в md5, иначе LOCAL после sync
-- будет считать, что поле ещё не синхронизировано (Constitution III).
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
        COALESCE(NEW.ready_songs_count::TEXT, '') ||
        COALESCE(NEW.total_songs_count::TEXT, '')
    );
    RETURN NEW;
END;
$$;

-- ===== 3. Backfill счётчиков для существующих авторов =====
-- Один UPDATE с подзапросом COUNT(*) GROUP BY song_author. Авторы без песен
-- остаются с (0, 0) — спасибо LEFT JOIN + COALESCE.
UPDATE public.tbl_authors a
SET
    ready_songs_count = COALESCE(s.ready_cnt, 0),
    total_songs_count = COALESCE(s.total_cnt, 0)
FROM (
    SELECT
        song_author AS author,
        COUNT(*) FILTER (WHERE id_status >= 6) AS ready_cnt,
        COUNT(*) AS total_cnt
    FROM public.tbl_songs
    GROUP BY song_author
) s
WHERE a.author = s.author;

-- Авторы, у которых НЕТ ни одной песни (висящие или skip-авторы), получают 0,0
-- через DEFAULT. Дополнительный UPDATE не требуется.

-- ===== 4. Backfill recordhash для существующих строк =====
-- Триггер сработает только на новые INSERT/UPDATE — существующие строки надо
-- обновить явно, чтобы md5 включил новые колонки.
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
    COALESCE(ready_songs_count::TEXT, '') ||
    COALESCE(total_songs_count::TEXT, '')
) WHERE id > 0;

-- ===== 5. Триггер для авто-обновления счётчиков при изменениях tbl_songs =====
-- AFTER INSERT/UPDATE/DELETE на tbl_songs — атомарно обновляет
-- ready_songs_count/total_songs_count в tbl_authors.
--
-- Покрывает (см. specs/286-author-song-counts-cache US2):
--   * INSERT (новая песня): +1 в total_songs_count, +1 в ready_songs_count если
--     id_status >= 6.
--   * DELETE (удаление песни): -1 в total_songs_count, -1 в ready_songs_count
--     если OLD.id_status >= 6.
--   * UPDATE id_status (1 → 6 или 6 → 5): меняет только ready_songs_count.
--   * UPDATE song_author (перенос песни между авторами): декремент у OLD.song_author
--     и инкремент у NEW.song_author с учётом OLD/NEW.id_status.
--
-- Граничные случаи:
--   * Skip-автор (tbl_authors.skip = true) — счётчики обновляются нормально
--     (UI скрывает автора отдельно через loadListAuthors).
--   * Висящая песня (song_author без соответствия в tbl_authors) — UPDATE
--     затрагивает 0 строк (no-op), без RAISE EXCEPTION.
CREATE OR REPLACE FUNCTION public.trg_tbl_songs_update_author_counts()
RETURNS TRIGGER
    LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        -- Новая песня: +1 в total, +1 в ready если готова.
        UPDATE public.tbl_authors
        SET ready_songs_count = ready_songs_count + 1
        WHERE author = NEW.song_author
          AND NEW.id_status >= 6;

        UPDATE public.tbl_authors
        SET total_songs_count = total_songs_count + 1
        WHERE author = NEW.song_author;

        RETURN NEW;

    ELSIF TG_OP = 'DELETE' THEN
        -- Удаление песни: -1 в total, -1 в ready если была готова.
        UPDATE public.tbl_authors
        SET ready_songs_count = ready_songs_count - 1
        WHERE author = OLD.song_author
          AND OLD.id_status >= 6;

        UPDATE public.tbl_authors
        SET total_songs_count = total_songs_count - 1
        WHERE author = OLD.song_author;

        RETURN OLD;

    ELSIF TG_OP = 'UPDATE' THEN
        IF OLD.song_author IS DISTINCT FROM NEW.song_author THEN
            -- Перенос песни между авторами: декремент у старого, инкремент у нового.
            -- Учитываем, что id_status мог тоже измениться — 4 комбинации.
            IF OLD.id_status >= 6 THEN
                UPDATE public.tbl_authors
                SET ready_songs_count = ready_songs_count - 1
                WHERE author = OLD.song_author;
            END IF;
            UPDATE public.tbl_authors
            SET total_songs_count = total_songs_count - 1
            WHERE author = OLD.song_author;

            IF NEW.id_status >= 6 THEN
                UPDATE public.tbl_authors
                SET ready_songs_count = ready_songs_count + 1
                WHERE author = NEW.song_author;
            END IF;
            UPDATE public.tbl_authors
            SET total_songs_count = total_songs_count + 1
            WHERE author = NEW.song_author;
        ELSE
            -- Тот же автор, меняется только id_status (1 → 6 или 6 → 5 и т.д.).
            IF OLD.id_status < 6 AND NEW.id_status >= 6 THEN
                UPDATE public.tbl_authors
                SET ready_songs_count = ready_songs_count + 1
                WHERE author = NEW.song_author;
            ELSIF OLD.id_status >= 6 AND NEW.id_status < 6 THEN
                UPDATE public.tbl_authors
                SET ready_songs_count = ready_songs_count - 1
                WHERE author = NEW.song_author;
            END IF;
            -- total_songs_count не меняется (та же песня).
        END IF;
        RETURN NEW;
    END IF;
    RETURN NULL;
END;
$$;

-- На LOCAL-БД: tbl_songs есть, INSERT/UPDATE/DELETE активны, триггер работает.
-- На SERVER-БД: tbl_songs пуста, триггер no-op (безопасно — не плодит мусор).
CREATE TRIGGER trg_tbl_songs_update_author_counts
    AFTER INSERT OR UPDATE OR DELETE
    ON public.tbl_songs
    FOR EACH ROW
    EXECUTE FUNCTION public.trg_tbl_songs_update_author_counts();