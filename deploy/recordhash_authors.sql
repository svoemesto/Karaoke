ALTER TABLE tbl_authors
ADD COLUMN IF NOT EXISTS recordhash VARCHAR(32);

CREATE INDEX IF NOT EXISTS idx_tbl_authors_recordhash ON tbl_authors(recordhash);

CREATE OR REPLACE FUNCTION update_tbl_authors_recordhash()
RETURNS TRIGGER AS
$$
BEGIN
    NEW.recordhash = md5(
        COALESCE(NEW.id::TEXT, '') ||
        COALESCE(NEW.author, '') ||
        COALESCE(NEW.ym_id, '') ||
        COALESCE(NEW.last_album_ym, '') ||
        COALESCE(NEW.last_album_processed, '') ||
        COALESCE(NEW.watched::TEXT, '') ||
        COALESCE(NEW.skip::TEXT, '')
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE TRIGGER update_recordhash_authors_trigger
    BEFORE UPDATE OR INSERT
    ON tbl_authors
    FOR EACH ROW
EXECUTE FUNCTION update_tbl_authors_recordhash();

UPDATE tbl_authors
SET recordhash = md5(
    COALESCE(id::TEXT, '') ||
    COALESCE(author, '') ||
    COALESCE(ym_id, '') ||
    COALESCE(last_album_ym, '') ||
    COALESCE(last_album_processed, '') ||
    COALESCE(watched::TEXT, '') ||
    COALESCE(skip::TEXT, '')
) WHERE id > 0;
