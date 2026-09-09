-- Кэш счётчиков песен альбома в tbl_albums (specs/356-zakroma-albums-by-author).
--
-- Прецедент: specs/286-author-song-counts-cache (та же логика для tbl_authors).
--
-- Добавляет две денормализованные колонки в tbl_albums:
--   * total_song_count — общее кол-во песен альбома
--   * ready_song_count — песен с id_status >= 6 (APPROVED)
--
-- Колонки поддерживаются актуальными DB-триггером trg_tbl_songs_update_album_counts
-- (создаётся в этой же миграции). Триггер AFTER INSERT/UPDATE/DELETE на tbl_songs.
-- На SERVER-БД триггер создаётся тоже, но остаётся no-op (tbl_songs пуста); это
-- упрощает миграцию и не требует условной логики.
--
-- Миграция ОБНОВЛЯЕТ recordhash-функцию для tbl_albums — добавляет новые колонки
-- в канонизированную строку md5, иначе sync сломается (Constitution Principle III).
--
-- Backfill существующих альбомов — одним UPDATE с подзапросом COUNT(*).
-- Альбомы без песен получают (0, 0) благодаря LEFT JOIN + COALESCE.
--
-- Применение: ОДИН РАЗ на LOCAL-БД и на SERVER-БД отдельно.
-- Идемпотентно: ADD COLUMN IF NOT EXISTS, CREATE OR REPLACE FUNCTION,
-- CREATE OR REPLACE TRIGGER, UPDATE — повторный запуск не ломает.

-- ==========================================================================================
-- 1. Новые колонки в tbl_albums
-- ==========================================================================================
ALTER TABLE public.tbl_albums
    ADD COLUMN IF NOT EXISTS total_song_count BIGINT NOT NULL DEFAULT 0;

ALTER TABLE public.tbl_albums
    ADD COLUMN IF NOT EXISTS ready_song_count BIGINT NOT NULL DEFAULT 0;

-- ==========================================================================================
-- 2. Пересоздание recordhash-функции для tbl_albums
-- ВАЖНО: новые колонки должны попасть в md5, иначе LOCAL после sync будет считать,
-- что поле ещё не синхронизировано (Constitution III).
-- ==========================================================================================
CREATE OR REPLACE FUNCTION public.update_tbl_albums_recordhash() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    NEW.recordhash = md5(
        COALESCE(NEW.id::TEXT, '') ||
        COALESCE(NEW.author_id::TEXT, '') ||
        COALESCE(NEW.year::TEXT, '') ||
        COALESCE(NEW.name, '') ||
        COALESCE(NEW.album_type, '') ||
        COALESCE(NEW.sort_order::TEXT, '') ||
        COALESCE(NEW.description, '') ||
        COALESCE(NEW.short_description, '') ||
        COALESCE(NEW.warning, '') ||
        COALESCE(NEW.total_song_count::TEXT, '') ||
        COALESCE(NEW.ready_song_count::TEXT, '')
    );
    RETURN NEW;
END;
$$;

-- ==========================================================================================
-- 3. Backfill счётчиков для существующих альбомов
-- Один UPDATE с подзапросом COUNT(*) GROUP BY album_id. Альбомы без песен
-- остаются с (0, 0) — спасибо LEFT JOIN + COALESCE.
-- ==========================================================================================
UPDATE public.tbl_albums a
SET
    total_song_count = COALESCE(s.total_cnt, 0),
    ready_song_count = COALESCE(s.ready_cnt, 0)
FROM (
    SELECT
        album_id,
        COUNT(*) AS total_cnt,
        COUNT(*) FILTER (WHERE id_status >= 6) AS ready_cnt
    FROM public.tbl_songs
    WHERE album_id IS NOT NULL
    GROUP BY album_id
) s
WHERE a.id = s.album_id;

-- Альбомы, у которых НЕТ ни одной песни (висящие), получают 0,0 через DEFAULT.
-- Дополнительный UPDATE не требуется.

-- ==========================================================================================
-- 4. Backfill recordhash для существующих строк
-- Триггер сработает только на новые INSERT/UPDATE — существующие строки надо
-- обновить явно, чтобы md5 включил новые колонки.
-- ==========================================================================================
UPDATE public.tbl_albums
SET recordhash = md5(
    COALESCE(id::TEXT, '') ||
    COALESCE(author_id::TEXT, '') ||
    COALESCE(year::TEXT, '') ||
    COALESCE(name, '') ||
    COALESCE(album_type, '') ||
    COALESCE(sort_order::TEXT, '') ||
    COALESCE(description, '') ||
    COALESCE(short_description, '') ||
    COALESCE(warning, '') ||
    COALESCE(total_song_count::TEXT, '') ||
    COALESCE(ready_song_count::TEXT, '')
) WHERE id > 0;

-- ==========================================================================================
-- 5. Триггер для авто-обновления счётчиков при изменениях tbl_songs
-- AFTER INSERT/UPDATE/DELETE на tbl_songs — атомарно обновляет
-- total_song_count/ready_song_count в tbl_albums.
--
-- Покрывает:
--   * INSERT (новая песня): +1 в total_song_count, +1 в ready_song_count если
--     id_status >= 6.
--   * DELETE (удаление песни): -1 в total_song_count, -1 в ready_song_count
--     если OLD.id_status >= 6.
--   * UPDATE id_status (1 → 6 или 6 → 5): меняет только ready_song_count.
--   * UPDATE album_id (перенос песни между альбомами): декремент у OLD.album_id
--     и инкремент у NEW.album_id с учётом OLD/NEW.id_status.
--
-- Граничные случаи:
--   * album_id = NULL → триггер затрагивает 0 строк (no-op), без RAISE EXCEPTION.
--   * Skip-альбом (tbl_albums.skip = true) — счётчики обновляются нормально
--     (UI скрывает отдельно через WHERE skip = false в Album.loadAlbumTilesWithCounts).
--   * Висящая песня (album_id без соответствия в tbl_albums) — UPDATE
--     затрагивает 0 строк (no-op), без RAISE EXCEPTION.
-- ==========================================================================================
CREATE OR REPLACE FUNCTION public.trg_tbl_songs_update_album_counts()
RETURNS TRIGGER
    LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        -- Новая песня: +1 в total, +1 в ready если готова.
        UPDATE public.tbl_albums
        SET
            total_song_count = total_song_count + 1,
            ready_song_count = ready_song_count + CASE WHEN NEW.id_status >= 6 THEN 1 ELSE 0 END
        WHERE id = NEW.album_id;
        RETURN NEW;

    ELSIF TG_OP = 'DELETE' THEN
        -- Удаление песни: -1 в total, -1 в ready если была готова.
        UPDATE public.tbl_albums
        SET
            total_song_count = total_song_count - 1,
            ready_song_count = ready_song_count - CASE WHEN OLD.id_status >= 6 THEN 1 ELSE 0 END
        WHERE id = OLD.album_id;
        RETURN OLD;

    ELSIF TG_OP = 'UPDATE' THEN
        -- UPDATE album_id (перенос между альбомами)
        IF (OLD.album_id IS DISTINCT FROM NEW.album_id) THEN
            -- Декремент у OLD
            IF OLD.album_id IS NOT NULL THEN
                UPDATE public.tbl_albums
                SET
                    total_song_count = total_song_count - 1,
                    ready_song_count = ready_song_count - CASE WHEN OLD.id_status >= 6 THEN 1 ELSE 0 END
                WHERE id = OLD.album_id;
            END IF;
            -- Инкремент у NEW
            IF NEW.album_id IS NOT NULL THEN
                UPDATE public.tbl_albums
                SET
                    total_song_count = total_song_count + 1,
                    ready_song_count = ready_song_count + CASE WHEN NEW.id_status >= 6 THEN 1 ELSE 0 END
                WHERE id = NEW.album_id;
            END IF;

        -- UPDATE id_status без смены album_id
        ELSIF (OLD.id_status IS DISTINCT FROM NEW.id_status) THEN
            IF NEW.album_id IS NOT NULL THEN
                UPDATE public.tbl_albums
                SET
                    ready_song_count = ready_song_count
                        - CASE WHEN OLD.id_status >= 6 THEN 1 ELSE 0 END
                        + CASE WHEN NEW.id_status >= 6 THEN 1 ELSE 0 END
                WHERE id = NEW.album_id;
            END IF;
        END IF;
        RETURN NEW;
    END IF;

    RETURN NULL;
END;
$$;

CREATE OR REPLACE TRIGGER trg_tbl_songs_update_album_counts
AFTER INSERT OR UPDATE OR DELETE ON public.tbl_songs
FOR EACH ROW
EXECUTE FUNCTION public.trg_tbl_songs_update_album_counts();

-- ==========================================================================================
-- 6. Verification notes (заполняется после smoke-тестов в quickstart.md Step 3-4)
-- ==========================================================================================
-- TODO(spec 356): заполнить после успешного прохождения тестов в quickstart.md
-- - [ ] INSERT test passed
-- - [ ] UPDATE id_status test passed
-- - [ ] DELETE test passed
-- - [ ] UPDATE album_id (перенос) test passed
-- - [ ] skip=true test passed (FR-016)
-- - [ ] sync LOCAL→SERVER test passed
-- - [ ] sync флаги в KaraokeProperties.kt не изменились (FR-009)