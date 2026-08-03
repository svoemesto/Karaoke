-- Follow-up к 35_rename_processes_settings_id_to_song_id.sql: на реальной БД (LOCAL
-- администратора) обнаружился recordhash-триггер update_tbl_processes_recordhash() на
-- tbl_processes, который НЕ зафиксирован ни в одной миграции этого репозитория (расхождение
-- схемы — создан в обход git-миграций). Его тело всё ещё ссылалось на NEW.settings_id,
-- что после переименования колонки ломало каждую вставку/обновление tbl_processes:
--   ERROR: record "new" has no field "settings_id"
--
-- Точное тело функции восстановлено из текста самой ошибки Postgres (она печатает полный
-- SQL упавшего PL/pgSQL-присваивания) — единственное изменение: settings_id -> song_id.
-- Остальные колонки (including without_control, thread_id — тоже не описанные в
-- 01_initdb.sql) оставлены как есть, без попытки полностью реконструировать всю историю
-- расхождения схемы, это отдельная задача.
--
-- ВАЖНО: применять СРАЗУ после (или вместе с) 35_rename_processes_settings_id_to_song_id.sql
-- на каждой БД, где реально существует этот триггер, — включая LOCAL администратора и PROD.
-- CREATE OR REPLACE идемпотентен, безопасно перезапускать.

CREATE OR REPLACE FUNCTION public.update_tbl_processes_recordhash() RETURNS trigger
    LANGUAGE plpgsql
AS $$
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
                                COALESCE(NEW.thread_id::TEXT, '')
        );
RETURN NEW;
END;
$$;
