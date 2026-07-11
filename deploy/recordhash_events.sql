ALTER TABLE tbl_events
ADD COLUMN IF NOT EXISTS recordhash VARCHAR(32);

CREATE INDEX IF NOT EXISTS idx_tbl_events_recordhash ON tbl_events(recordhash);

-- Реальный IP клиента, анонимный localStorage-идентификатор, привязка к tbl_site_users
-- (0 = аноним, см. WebEvent.kt / EventTypes.kt) и User-Agent — "статистика".
ALTER TABLE tbl_events ADD COLUMN IF NOT EXISTS client_ip character varying(64);
ALTER TABLE tbl_events ADD COLUMN IF NOT EXISTS anon_id character varying(64);
ALTER TABLE tbl_events ADD COLUMN IF NOT EXISTS site_user_id bigint NOT NULL DEFAULT 0;
ALTER TABLE tbl_events ADD COLUMN IF NOT EXISTS user_agent text;

CREATE OR REPLACE FUNCTION update_tbl_events_recordhash()
RETURNS TRIGGER AS
$$
BEGIN
    NEW.recordhash = md5(
        COALESCE(NEW.id::TEXT, '') ||
        COALESCE(NEW.event_type, '') ||
        COALESCE(NEW.rest_name, '') ||
        COALESCE(NEW.rest_parameters, '') ||
        COALESCE(NEW.link_type, '') ||
        COALESCE(NEW.link_name, '') ||
        COALESCE(NEW.song_id::TEXT, '') ||
        COALESCE(NEW.song_version, '') ||
        COALESCE(NEW.referer, '') ||
        COALESCE(NEW.client_ip, '') ||
        COALESCE(NEW.anon_id, '') ||
        NEW.site_user_id::TEXT ||
        COALESCE(NEW.user_agent, '')
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE TRIGGER update_recordhash_events_trigger
    BEFORE UPDATE OR INSERT
    ON tbl_events
    FOR EACH ROW
EXECUTE FUNCTION update_tbl_events_recordhash();

UPDATE tbl_events
SET recordhash = md5(
    COALESCE(id::TEXT, '') ||
    COALESCE(event_type, '') ||
    COALESCE(rest_name, '') ||
    COALESCE(rest_parameters, '') ||
    COALESCE(link_type, '') ||
    COALESCE(link_name, '') ||
    COALESCE(song_id::TEXT, '') ||
    COALESCE(song_version, '') ||
    COALESCE(referer, '') ||
    COALESCE(client_ip, '') ||
    COALESCE(anon_id, '') ||
    site_user_id::TEXT ||
    COALESCE(user_agent, '')
) WHERE id > 0;
