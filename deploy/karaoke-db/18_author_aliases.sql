-- Алиасы автора (солисты/участники), по которым публичный поиск тоже находит автора.
-- Значения хранятся через разделитель ';' (напр. 'Владимир Ткаченко;Владимир Кучеренко').
--
-- BR-039 (аудит #336 / P-70-01, OpenProject #371): до этой правки НИ ОДИН файл
-- каталога deploy/karaoke-db/ не создавал tbl_authors, а этот файл делал
-- ALTER TABLE tbl_authors → на БД, созданной с нуля (docker-entrypoint-initdb.d),
-- цепочка падала на «relation "tbl_authors" does not exist».
--
-- Ниже — самостоятельный bootstrap таблицы (идемпотентный) + канонический
-- recordhash-триггер. Набор колонок — полный, как в модели Author
-- (karaoke-app/.../model/Author.kt) и в миграциях 27/31/44/46:
--   id, author, ym_id, vk_id, last_album_ym, last_album_vk, last_album_processed,
--   watched, skip, aliases, description, short_description, warning,
--   is_special_order, sort_order, ready_songs_count, total_songs_count
--   (+ recordhash — служебная колонка sync-diff, + last_update).
--
-- Создание колонок и функции здесь — с IF NOT EXISTS / CREATE OR REPLACE,
-- поэтому файл безопасно применять повторно (в т.ч. к существующей БД).

-- --- Таблица ---
CREATE TABLE IF NOT EXISTS public.tbl_authors (
    id integer GENERATED ALWAYS AS IDENTITY (
        SEQUENCE NAME public.tbl_authors_id_seq
        START WITH 1
        INCREMENT BY 1
        NO MINVALUE
        NO MAXVALUE
        CACHE 1
    ),
    author character varying(255) DEFAULT 'Author name' NOT NULL,
    ym_id character varying(255) DEFAULT '' NOT NULL,
    vk_id character varying(255) DEFAULT '' NOT NULL,
    last_album_ym character varying(255) DEFAULT '' NOT NULL,
    last_album_vk character varying(255) DEFAULT '' NOT NULL,
    last_album_processed character varying(255) DEFAULT '' NOT NULL,
    watched boolean DEFAULT false NOT NULL,
    skip boolean DEFAULT false NOT NULL,
    aliases text DEFAULT '' NOT NULL,
    description text DEFAULT '' NOT NULL,
    short_description character varying(255) DEFAULT '' NOT NULL,
    warning character varying(255) DEFAULT '' NOT NULL,
    is_special_order boolean DEFAULT false NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    ready_songs_count bigint DEFAULT 0 NOT NULL,
    total_songs_count bigint DEFAULT 0 NOT NULL,
    recordhash character varying(32),
    last_update timestamp without time zone DEFAULT now() NOT NULL
);

-- Колонки, добавленные более поздними миграциями (27/31/44/46) — создаём
-- заранее, чтобы канонический триггер ниже не ссылался на несуществующие поля
-- (триггер с NEW.<нет колонки> падает на каждой записи tbl_authors).
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
    ADD COLUMN IF NOT EXISTS sort_order integer DEFAULT 0 NOT NULL;
ALTER TABLE public.tbl_authors
    ADD COLUMN IF NOT EXISTS ready_songs_count bigint DEFAULT 0 NOT NULL;
ALTER TABLE public.tbl_authors
    ADD COLUMN IF NOT EXISTS total_songs_count bigint DEFAULT 0 NOT NULL;
ALTER TABLE public.tbl_authors
    ADD COLUMN IF NOT EXISTS recordhash character varying(32);
ALTER TABLE public.tbl_authors
    ADD COLUMN IF NOT EXISTS last_update timestamp without time zone DEFAULT now() NOT NULL;

-- PK: на уже существующей БД он, скорее всего, есть — добавляем только если нет.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'tbl_authors_pkey' AND conrelid = 'public.tbl_authors'::regclass
    ) THEN
        ALTER TABLE public.tbl_authors ADD CONSTRAINT tbl_authors_pkey PRIMARY KEY (id);
    END IF;
END;
$$;

-- --- Канонический recordhash-триггер ---
-- Sync-diff (LOCAL↔SERVER) сравнивает строки ТОЛЬКО по recordhash
-- (KaraokeDbTable.getListHashes), поэтому в хэш обязаны входить ВСЕ
-- значимые колонки автора — иначе правка (например, предупреждение) не
-- даёт расхождения и не уезжает на публичный периметр.
--
-- NB: набор и ПОРЯДОК колонок должен совпадать с каноническим файлом
-- deploy/recordhash_authors.sql (BR-541, пакет #363) и с миграцией 46
-- (там же — обоснование порядка: 12 исторических полей + 5 новых в конце).
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

DROP TRIGGER IF EXISTS update_recordhash_authors_trigger ON public.tbl_authors;
CREATE TRIGGER update_recordhash_authors_trigger
    BEFORE UPDATE OR INSERT
    ON public.tbl_authors
    FOR EACH ROW
EXECUTE FUNCTION public.update_tbl_authors_recordhash();

-- Backfill: у уже существующих строк хэш пересчитывается (триггер срабатывает
-- только на новые INSERT/UPDATE).
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
