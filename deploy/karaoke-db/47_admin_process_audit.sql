-- Спека 311 — Admin UI для KaraokeProcess (edit/retry/delete cascade + audit log).
--
-- Добавляет в tbl_processes две nullable колонки (process_chain_id, process_deleted_at),
-- создаёт отдельную таблицу tbl_processes_audit для admin-аудита операций EDIT/RETRY/DELETE.
-- Зависимости: нет. Обратная совместимость: да (nullable колонки + новая таблица).
--
-- Применяется один раз на LOCAL и PROD отдельно.
-- Колонки process_chain_id и process_deleted_at входят в recordhash tbl_processes.
--
-- ВАЖНО: этот же файл идемпотентно приводит recordhash-функцию update_tbl_processes_recordhash()
-- в актуальное состояние, включающее song_id (вместо legacy settings_id) и две новые колонки.
-- До этой миграции на части LOCAL/PROP-баз функция может иметь ссылку на NEW.settings_id
-- (после PR #160 переименовано в song_id), что ломает INSERT/UPDATE — поэтому фикс включён
-- прямо в эту миграцию. См. PR #161 (36_fix_processes_recordhash_trigger_song_id.sql) для
-- оригинального follow-up.

-- 1. tbl_processes: две новые nullable колонки для цепочек процессов и soft-delete.
ALTER TABLE tbl_processes ADD COLUMN IF NOT EXISTS process_chain_id BIGINT NULL;
ALTER TABLE tbl_processes ADD COLUMN IF NOT EXISTS process_deleted_at TIMESTAMP NULL;

-- 2. Индексы: cascade-search по process_chain_id + фильтр active (process_deleted_at IS NULL).
CREATE INDEX IF NOT EXISTS idx_tbl_processes_chain_id
    ON tbl_processes(process_chain_id) WHERE process_chain_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_tbl_processes_deleted_at
    ON tbl_processes(process_deleted_at) WHERE process_deleted_at IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_tbl_processes_active
    ON tbl_processes(id) WHERE process_deleted_at IS NULL;

-- 3. Новая таблица tbl_processes_audit: per-row audit-trail admin-операций
--    (EDIT / RETRY / DELETE). FK на tbl_processes(id) с ON DELETE CASCADE — если
--    процесс hard-deleted вне scope MVP, audit идёт с ним; soft-delete (process_deleted_at)
--    оставляет строку в tbl_processes и audit сохраняется для трассировки.
CREATE TABLE IF NOT EXISTS tbl_processes_audit (
    id BIGSERIAL PRIMARY KEY,
    process_id BIGINT NOT NULL REFERENCES tbl_processes(id) ON DELETE CASCADE,
    actor VARCHAR(64) NOT NULL,
    action VARCHAR(32) NOT NULL CHECK (action IN ('EDIT', 'RETRY', 'DELETE')),
    old_value JSONB NULL,
    new_value JSONB NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 4. Индексы на audit-таблицу: запросы по process_id (для GET /audit) и сортировка created_at DESC.
CREATE INDEX IF NOT EXISTS idx_tbl_processes_audit_process_id
    ON tbl_processes_audit(process_id);
CREATE INDEX IF NOT EXISTS idx_tbl_processes_audit_created_at
    ON tbl_processes_audit(created_at);

-- 5. Обновить recordhash-триггер: song_id (не legacy settings_id) + две новые колонки.
--    CREATE OR REPLACE идемпотентен — безопасно перезапускать.
CREATE OR REPLACE FUNCTION update_tbl_processes_recordhash()
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
        COALESCE(NEW.process_deleted_at::TEXT, '')
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 6. Backfill recordhash для существующих строк (триггер сработает только на новые UPDATE/INSERT).
UPDATE tbl_processes
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
    COALESCE(process_deleted_at::TEXT, '')
) WHERE id > 0;

-- DOWN-секция (для отката, выполнять вручную при необходимости):
--   DROP TABLE IF EXISTS tbl_processes_audit;
--   ALTER TABLE tbl_processes DROP COLUMN IF EXISTS process_deleted_at;
--   ALTER TABLE tbl_processes DROP COLUMN IF EXISTS process_chain_id;
--   -- восстановить предыдущую версию update_tbl_processes_recordhash() без двух новых колонок;
--   -- затем пересчитать recordhash: см. recordhash_processes.sql / 36_fix_processes_recordhash_trigger_song_id.sql.
