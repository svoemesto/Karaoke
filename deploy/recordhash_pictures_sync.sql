ALTER TABLE tbl_pictures_sync
ADD COLUMN IF NOT EXISTS recordhash VARCHAR(32);

CREATE INDEX IF NOT EXISTS idx_tbl_pictures_sync_recordhash ON tbl_pictures_sync(recordhash);

CREATE OR REPLACE FUNCTION update_tbl_pictures_sync_recordhash()
RETURNS TRIGGER AS
$$
BEGIN
    NEW.recordhash = md5(
        COALESCE(NEW.id::TEXT, '') ||
        COALESCE(NEW.picture_name, '') ||
        COALESCE(NEW.picture_full, '') ||
        COALESCE(NEW.picture_preview, '')
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE TRIGGER update_recordhash_pictures_sync_trigger
    BEFORE UPDATE OR INSERT
    ON tbl_pictures_sync
    FOR EACH ROW
EXECUTE FUNCTION update_tbl_pictures_sync_recordhash();

UPDATE tbl_pictures_sync
SET recordhash = md5(
    COALESCE(id::TEXT, '') ||
    COALESCE(picture_name, '') ||
    COALESCE(picture_full, '') ||
    COALESCE(picture_preview, '')
) WHERE id > 0;
