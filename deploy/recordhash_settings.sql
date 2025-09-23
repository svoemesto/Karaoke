ALTER TABLE tbl_settings
ADD COLUMN IF NOT EXISTS recordhash VARCHAR(32);

CREATE INDEX IF NOT EXISTS idx_tbl_settings_recordhash ON tbl_settings(recordhash);

CREATE OR REPLACE FUNCTION update_tbl_settings_recordhash()
RETURNS TRIGGER AS
$$
BEGIN
    NEW.recordhash = md5(
        COALESCE(NEW.id::TEXT, '') ||
        COALESCE(NEW.song_name, '') ||
        COALESCE(NEW.song_author, '') ||
        COALESCE(NEW.song_album, '') ||
        COALESCE(NEW.publish_date, '') ||
        COALESCE(NEW.publish_time, '') ||
        COALESCE(NEW.song_year::TEXT, '') ||
        COALESCE(NEW.song_track::TEXT, '') ||
        COALESCE(NEW.song_tone, '') ||
        COALESCE(NEW.song_bpm::TEXT, '') ||
        COALESCE(NEW.song_ms::TEXT, '') ||
        COALESCE(NEW.file_name, '') ||
        COALESCE(NEW.root_folder, '') ||
        COALESCE(NEW.id_boosty, '') ||
        COALESCE(NEW.id_dzen_lyrics, '') ||
        COALESCE(NEW.id_dzen_karaoke, '') ||
        COALESCE(NEW.id_dzen_chords, '') ||
        COALESCE(NEW.id_status::TEXT, '') ||
        COALESCE(NEW.source_text, '') ||
        COALESCE(NEW.source_markers, '') ||
        COALESCE(NEW.id_vk_lyrics, '') ||
        COALESCE(NEW.id_vk_karaoke, '') ||
        COALESCE(NEW.id_vk_chords, '') ||
        COALESCE(NEW.status_process_lyrics, '') ||
        COALESCE(NEW.status_process_karaoke, '') ||
        COALESCE(NEW.status_process_chords, '') ||
        COALESCE(NEW.id_vk, '') ||
        COALESCE(NEW.id_telegram_lyrics, '') ||
        COALESCE(NEW.id_telegram_karaoke, '') ||
        COALESCE(NEW.id_telegram_chords, '') ||
        COALESCE(NEW.tags, '') ||
        COALESCE(NEW.result_text, '') ||
        COALESCE(NEW.id_boosty_files, '') ||
        COALESCE(NEW.result_version::TEXT, '') ||
        COALESCE(NEW.id_pl_lyrics, '') ||
        COALESCE(NEW.id_pl_karaoke, '') ||
        COALESCE(NEW.id_pl_chords, '') ||
        COALESCE(NEW.diff_beats::TEXT, '') ||
        COALESCE(NEW.id_sponsr, '') ||
        COALESCE(NEW.id_dzen_melody, '') ||
        COALESCE(NEW.id_vk_melody, '') ||
        COALESCE(NEW.status_process_melody, '') ||
        COALESCE(NEW.id_telegram_melody, '') ||
        COALESCE(NEW.id_pl_melody, '') ||
        COALESCE(NEW.index_tabs_variant::TEXT, '') ||
        COALESCE(NEW.version_dzen_lyrics::TEXT, '') ||
        COALESCE(NEW.version_dzen_karaoke::TEXT, '') ||
        COALESCE(NEW.version_dzen_chords::TEXT, '') ||
        COALESCE(NEW.version_dzen_melody::TEXT, '') ||
        COALESCE(NEW.version_vk_lyrics::TEXT, '') ||
        COALESCE(NEW.version_vk_karaoke::TEXT, '') ||
        COALESCE(NEW.version_vk_chords::TEXT, '') ||
        COALESCE(NEW.version_vk_melody::TEXT, '') ||
        COALESCE(NEW.version_telegram_lyrics::TEXT, '') ||
        COALESCE(NEW.version_telegram_karaoke::TEXT, '') ||
        COALESCE(NEW.version_telegram_chords::TEXT, '') ||
        COALESCE(NEW.version_telegram_melody::TEXT, '') ||
        COALESCE(NEW.version_pl_lyrics::TEXT, '') ||
        COALESCE(NEW.version_pl_karaoke::TEXT, '') ||
        COALESCE(NEW.version_pl_chords::TEXT, '') ||
        COALESCE(NEW.version_pl_melody::TEXT, '') ||
        COALESCE(NEW.version_boosty::TEXT, '') ||
        COALESCE(NEW.version_sponsr::TEXT, '') ||
        COALESCE(NEW.version_boosty_files::TEXT, '') ||
        COALESCE(NEW.rate::TEXT, '')
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION update_last_updated()
RETURNS TRIGGER AS
$$
BEGIN
    NEW.last_update = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE TRIGGER update_recordhash_trigger
    BEFORE UPDATE OR INSERT
    ON tbl_settings
    FOR EACH ROW
EXECUTE FUNCTION update_tbl_settings_recordhash();

UPDATE tbl_settings
SET recordhash = md5(
        COALESCE(id::TEXT, '') ||
        COALESCE(song_name, '') ||
        COALESCE(song_author, '') ||
        COALESCE(song_album, '') ||
        COALESCE(publish_date, '') ||
        COALESCE(publish_time, '') ||
        COALESCE(song_year::TEXT, '') ||
        COALESCE(song_track::TEXT, '') ||
        COALESCE(song_tone, '') ||
        COALESCE(song_bpm::TEXT, '') ||
        COALESCE(song_ms::TEXT, '') ||
        COALESCE(file_name, '') ||
        COALESCE(root_folder, '') ||
        COALESCE(id_boosty, '') ||
        COALESCE(id_dzen_lyrics, '') ||
        COALESCE(id_dzen_karaoke, '') ||
        COALESCE(id_dzen_chords, '') ||
        COALESCE(id_status::TEXT, '') ||
        COALESCE(source_text, '') ||
        COALESCE(source_markers, '') ||
        COALESCE(id_vk_lyrics, '') ||
        COALESCE(id_vk_karaoke, '') ||
        COALESCE(id_vk_chords, '') ||
        COALESCE(status_process_lyrics, '') ||
        COALESCE(status_process_karaoke, '') ||
        COALESCE(status_process_chords, '') ||
        COALESCE(id_vk, '') ||
        COALESCE(id_telegram_lyrics, '') ||
        COALESCE(id_telegram_karaoke, '') ||
        COALESCE(id_telegram_chords, '') ||
        COALESCE(tags, '') ||
        COALESCE(result_text, '') ||
        COALESCE(id_boosty_files, '') ||
        COALESCE(result_version::TEXT, '') ||
        COALESCE(id_pl_lyrics, '') ||
        COALESCE(id_pl_karaoke, '') ||
        COALESCE(id_pl_chords, '') ||
        COALESCE(diff_beats::TEXT, '') ||
        COALESCE(id_sponsr, '') ||
        COALESCE(id_dzen_melody, '') ||
        COALESCE(id_vk_melody, '') ||
        COALESCE(status_process_melody, '') ||
        COALESCE(id_telegram_melody, '') ||
        COALESCE(id_pl_melody, '') ||
        COALESCE(index_tabs_variant::TEXT, '') ||
        COALESCE(version_dzen_lyrics::TEXT, '') ||
        COALESCE(version_dzen_karaoke::TEXT, '') ||
        COALESCE(version_dzen_chords::TEXT, '') ||
        COALESCE(version_dzen_melody::TEXT, '') ||
        COALESCE(version_vk_lyrics::TEXT, '') ||
        COALESCE(version_vk_karaoke::TEXT, '') ||
        COALESCE(version_vk_chords::TEXT, '') ||
        COALESCE(version_vk_melody::TEXT, '') ||
        COALESCE(version_telegram_lyrics::TEXT, '') ||
        COALESCE(version_telegram_karaoke::TEXT, '') ||
        COALESCE(version_telegram_chords::TEXT, '') ||
        COALESCE(version_telegram_melody::TEXT, '') ||
        COALESCE(version_pl_lyrics::TEXT, '') ||
        COALESCE(version_pl_karaoke::TEXT, '') ||
        COALESCE(version_pl_chords::TEXT, '') ||
        COALESCE(version_pl_melody::TEXT, '') ||
        COALESCE(version_boosty::TEXT, '') ||
        COALESCE(version_sponsr::TEXT, '') ||
        COALESCE(version_boosty_files::TEXT, '') ||
        COALESCE(rate::TEXT, '')
) WHERE id > 0;
