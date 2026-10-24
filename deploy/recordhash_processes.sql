-- ==========================================================================================
-- tbl_processes — КАНОНИЧЕСКИЙ recordhash-триггер (единственный источник истины).
--
-- Аудит #336, порция P-73, BR-540, OpenProject #363. До этой правки канонический файл
-- содержал СТАРОЕ (переименованное) имя колонки: `COALESCE(NEW.settings_id::TEXT, '')`.
-- Миграция deploy/karaoke-db/35_rename_processes_settings_id_to_song_id.sql переименовала
-- tbl_processes.settings_id → song_id, а 36_fix_processes_recordhash_trigger_song_id.sql
-- починила саму функцию в БД — но этот файл (тот, что в knowledge назван каноническим местом
-- для функции) остался со старым телом.
--
-- Чем это опасно: тело plpgsql при создании функции проверяется только синтаксически, поэтому
-- `CREATE OR REPLACE FUNCTION` проходит успешно, а триггер падает на ПЕРВОЙ же записи:
--   ERROR: record "new" has no field "settings_id"
-- То есть любое создание задания (INSERT INTO tbl_processes) и любая смена его статуса
-- (UPDATE ... WORKING → DONE) ломаются — очередь заданий встаёт. Ошибка видна только в момент
-- записи, поэтому «файл применился без ошибок» ничего не гарантирует.
--
-- Заодно закрыт второй дефект того же корня (дрейф набора колонок, см. BR-534/BR-541):
-- в md5 не входили process_envs, process_chain_id и process_deleted_at — поля, которые
-- объявлены в модели `KaraokeProcess` через @KaraokeDbTableField. Для recordhash на
-- tbl_processes это не «тихая потеря sync» (таблица не входит в SyncRegistry), но именно
-- этот файл оператор применяет вручную, и именно он должен оставаться определением функции.
--
-- Правило после правки: ЭТОТ ФАЙЛ — единственное определение функции
-- update_tbl_processes_recordhash(). Миграции 36 и 47 остаются историей; новые колонки
-- добавляются сюда (и в миграцию — ради бэкфилла значений).
--
-- Инвариант проверяется статически, без БД:
--   python3 tools/check-recordhash-consistency.py
--   check 2: множество колонок md5 == все @KaraokeDbTableField модели KaraokeProcess
--            (кроме useInHash = false) и совпадает с колонками бэкфилла ниже;
--   check 1: в теле функции нет «голых» имён колонок — только NEW.<колонка>.
--
-- Что НЕ входит в md5 (осознанно): progress-колонки process_start_str / process_end_str /
-- process_percentage / process_percentage_str / process_time_passed_ms / process_time_passed_str /
-- process_time_left_ms / process_time_left_str / last_update. Они меняются на каждом шаге
-- задания; включение их в хэш дало бы постоянный churn. В модели они не объявлены как
-- @KaraokeDbTableField, поэтому guard их и не требует.
--
-- Порядок колонок: новые колонки добавляются ТОЛЬКО в конец цепочки, а бэкфилл в конце файла
-- обязан повторять формулу один-в-один — иначе строки останутся со старым хэшем до первого UPDATE.
--
-- Применение: на ЛЮБОЙ БД, где есть схема tbl_processes (LOCAL и PROD). Один раз; идемпотентно:
-- ADD COLUMN IF NOT EXISTS + CREATE OR REPLACE + UPDATE. См. runbook в конце файла.
-- ==========================================================================================

-- ==========================================================================================
-- 0. Предусловия схемы. Проверяются ДО создания функции: молчаливая подстановка пустого
--    song_id потеряла бы связь задания с песней, поэтому при отсутствии song_id — явная ошибка
--    с указанием нужной миграции.
-- ==========================================================================================
DO $$
BEGIN
    IF to_regclass('public.tbl_processes') IS NULL THEN
        RAISE EXCEPTION
            'Таблица public.tbl_processes не найдена: канонический recordhash-файл применяется только к существующей схеме';
    END IF;

    IF NOT EXISTS (
        SELECT 1
          FROM information_schema.columns
         WHERE table_schema = 'public'
           AND table_name = 'tbl_processes'
           AND column_name = 'song_id'
    ) THEN
        RAISE EXCEPTION
            'В tbl_processes нет колонки song_id — сначала примените deploy/karaoke-db/35_rename_processes_settings_id_to_song_id.sql (settings_id → song_id)';
    END IF;
END;
$$;

-- ==========================================================================================
-- 1. Колонки, которые участвуют в md5. Определения совпадают с 01_initdb.sql / 47 и с реальной
--    схемой (dump): файл обязан быть применимым к любой БД, иначе CREATE OR REPLACE ... пройдёт,
--    а триггер упадёт на первой записи (ровно этот класс ошибки и был у BR-541).
--    ADD COLUMN IF NOT EXISTS — на LOCAL/PROD, где колонки уже есть, это no-op.
-- ==========================================================================================
ALTER TABLE public.tbl_processes ADD COLUMN IF NOT EXISTS recordhash VARCHAR(32);
ALTER TABLE public.tbl_processes ADD COLUMN IF NOT EXISTS without_control BOOLEAN DEFAULT false;
ALTER TABLE public.tbl_processes ADD COLUMN IF NOT EXISTS thread_id INTEGER DEFAULT 0 NOT NULL;
ALTER TABLE public.tbl_processes ADD COLUMN IF NOT EXISTS process_envs TEXT DEFAULT '' NOT NULL;
ALTER TABLE public.tbl_processes ADD COLUMN IF NOT EXISTS process_chain_id BIGINT NULL;
ALTER TABLE public.tbl_processes ADD COLUMN IF NOT EXISTS process_deleted_at TIMESTAMP NULL;

CREATE INDEX IF NOT EXISTS idx_tbl_processes_recordhash ON public.tbl_processes(recordhash);

-- ==========================================================================================
-- 2. Функция-триггер (порядок колонок = порядок 47 + process_envs в конце).
-- ==========================================================================================
CREATE OR REPLACE FUNCTION public.update_tbl_processes_recordhash()
RETURNS TRIGGER AS
$$
BEGIN
    NEW.recordhash = md5(
        COALESCE(NEW.id::TEXT, '') ||
        COALESCE(NEW.process_name, '') ||
        COALESCE(NEW.process_status, '') ||
        COALESCE(NEW.process_order::TEXT, '') ||
        COALESCE(NEW.process_priority::TEXT, '') ||
        COALESCE(NEW.process_command, '') ||
        COALESCE(NEW.process_args, '') ||
        COALESCE(NEW.process_description, '') ||
        COALESCE(NEW.song_id::TEXT, '') ||
        COALESCE(NEW.process_type, '') ||
        COALESCE(NEW.process_start::TEXT, '') ||
        COALESCE(NEW.process_end::TEXT, '') ||
        COALESCE(NEW.process_prioritet::TEXT, '') ||
        COALESCE(NEW.without_control::TEXT, '') ||
        COALESCE(NEW.thread_id::TEXT, '') ||
        COALESCE(NEW.process_chain_id::TEXT, '') ||
        COALESCE(NEW.process_deleted_at::TEXT, '') ||
        COALESCE(NEW.process_envs, '')
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE TRIGGER update_recordhash_processes_trigger
    BEFORE UPDATE OR INSERT
    ON public.tbl_processes
    FOR EACH ROW
EXECUTE FUNCTION public.update_tbl_processes_recordhash();

-- ==========================================================================================
-- 3. Бэкфилл recordhash для существующих строк (триггер срабатывает только на новые
--    INSERT/UPDATE). Формула ДОЛЖНА совпадать с функцией выше — это проверяет guard.
-- ==========================================================================================
UPDATE public.tbl_processes
SET recordhash = md5(
    COALESCE(id::TEXT, '') ||
    COALESCE(process_name, '') ||
    COALESCE(process_status, '') ||
    COALESCE(process_order::TEXT, '') ||
    COALESCE(process_priority::TEXT, '') ||
    COALESCE(process_command, '') ||
    COALESCE(process_args, '') ||
    COALESCE(process_description, '') ||
    COALESCE(song_id::TEXT, '') ||
    COALESCE(process_type, '') ||
    COALESCE(process_start::TEXT, '') ||
    COALESCE(process_end::TEXT, '') ||
    COALESCE(process_prioritet::TEXT, '') ||
    COALESCE(without_control::TEXT, '') ||
    COALESCE(thread_id::TEXT, '') ||
    COALESCE(process_chain_id::TEXT, '') ||
    COALESCE(process_deleted_at::TEXT, '') ||
    COALESCE(process_envs, '')
) WHERE id > 0;

-- ==========================================================================================
-- Runbook применения (миграции — manual, knowledge/system/infra/sql-migrations.md, Решение 4)
-- ==========================================================================================
-- 1. LOCAL:  psql -f deploy/recordhash_processes.sql
-- 2. PROD:   docker exec -i karaoke-db psql -U postgres -d karaoke \
--              < /root/Karaoke/deploy/recordhash_processes.sql
-- 3. Проверить, что функция ссылается на song_id и НЕ ссылается на settings_id:
--      SELECT prosrc FROM pg_proc WHERE proname = 'update_tbl_processes_recordhash';
-- 4. Проверить, что триггер срабатывает (это и есть регресс «до/после» на живой БД):
--      UPDATE tbl_processes SET process_status = process_status WHERE id = (
--          SELECT id FROM tbl_processes ORDER BY id DESC LIMIT 1);
--    ошибки `record "new" has no field "settings_id"` быть не должно.
-- 5. Убедиться, что админка webvue3 «Очередь заданий» создаёт задание и переводит его
--    WORKING → DONE.
-- ==========================================================================================
