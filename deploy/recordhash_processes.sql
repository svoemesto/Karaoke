ALTER TABLE tbl_processes
ADD COLUMN IF NOT EXISTS recordhash VARCHAR(32);

CREATE INDEX IF NOT EXISTS idx_tbl_processes_recordhash ON tbl_processes(recordhash);

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
        COALESCE(NEW.settings_id::TEXT, '') ||
        COALESCE(NEW.process_type, '') ||
        COALESCE(NEW.process_start::TEXT, '') ||
        COALESCE(NEW.process_end::TEXT, '') ||
        COALESCE(NEW.process_prioritet::TEXT, '') ||
        COALESCE(NEW.without_control::TEXT, '') ||
        COALESCE(NEW.thread_id::TEXT, '')
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE TRIGGER update_recordhash_processes_trigger
    BEFORE UPDATE OR INSERT
    ON tbl_processes
    FOR EACH ROW
EXECUTE FUNCTION update_tbl_processes_recordhash();

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
    COALESCE(settings_id::TEXT, '') ||
    COALESCE(process_type, '') ||
    COALESCE(process_start::TEXT, '') ||
    COALESCE(process_end::TEXT, '') ||
    COALESCE(process_prioritet::TEXT, '') ||
    COALESCE(without_control::TEXT, '') ||
    COALESCE(thread_id::TEXT, '')
) WHERE id > 0;
