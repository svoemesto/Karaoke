-- Готовность песни к онлайн-плееру больше не вычисляется на лету через MinIO при каждом
-- запросе (это било по MinIO на каждый показ списка песен) — вместо этого 4 булевых флага
-- персистентно хранятся вместе с песней и обновляются точечно в момент успешной заливки
-- файла в хранилище (см. ApiController.pushMp3ToStorage / Settings.pictureAlbum/pictureAuthor),
-- плюс сверяются механизмом HealthReport (see HealthReport.recomputeAndBroadcast) на случай
-- рассинхрона (например, ручного удаления файла в MinIO).
--
-- Применяется вручную на КАЖДОЙ БД (LOCAL и PROD) — миграция сама на сервер не попадает
-- (см. karaoke-db/22_stem_jobs.sql, тот же комментарий).
--
-- ОБЯЗАТЕЛЬНО после миграции:
-- 1. В recordhash-триггерах добавлены 4 новых поля в md5 — иначе LOCAL и SERVER
--    будут иметь разные хэши для одной и той же песни, и sync (Settings) решит, что запись
--    расходится, начнёт перезаливать tbl_settings_sync ↔ tbl_settings после КАЖДОГО сохранения
--    (даже без реальных изменений). Оба триггера пересоздаются тут (см. karaoke-db/25_audio_parent.sql).
-- 2. Существующие песни получат все 4 флага = false (DEFAULT) — нужен разовый backfill-прогон
--    (см. план миграции, Фаза 5), иначе ранее готовые песни временно "исчезнут" из плеера,
--    пока их кто-то не откроет в редакторе/health-репорте.

ALTER TABLE public.tbl_settings
    ADD COLUMN IF NOT EXISTS stem_accompaniment_ready boolean NOT NULL DEFAULT false;
ALTER TABLE public.tbl_settings
    ADD COLUMN IF NOT EXISTS stem_vocal_ready boolean NOT NULL DEFAULT false;
ALTER TABLE public.tbl_settings
    ADD COLUMN IF NOT EXISTS picture_album_ready boolean NOT NULL DEFAULT false;
ALTER TABLE public.tbl_settings
    ADD COLUMN IF NOT EXISTS picture_author_ready boolean NOT NULL DEFAULT false;

ALTER TABLE public.tbl_settings_sync
    ADD COLUMN IF NOT EXISTS stem_accompaniment_ready boolean NOT NULL DEFAULT false;
ALTER TABLE public.tbl_settings_sync
    ADD COLUMN IF NOT EXISTS stem_vocal_ready boolean NOT NULL DEFAULT false;
ALTER TABLE public.tbl_settings_sync
    ADD COLUMN IF NOT EXISTS picture_album_ready boolean NOT NULL DEFAULT false;
ALTER TABLE public.tbl_settings_sync
    ADD COLUMN IF NOT EXISTS picture_author_ready boolean NOT NULL DEFAULT false;

-- Пересоздаём триггер хэша для tbl_settings с новыми колонками.
-- Идемпотентно: CREATE OR REPLACE FUNCTION, новый триггер пересоздаётся через DROP+CREATE.
CREATE OR REPLACE FUNCTION public.update_tbl_settings_recordhash()
    RETURNS trigger
    LANGUAGE plpgsql
    AS $$
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
                                COALESCE(NEW.rate::TEXT, '') ||
                                COALESCE(NEW.root_id::TEXT, '') ||
                                COALESCE(NEW.free::TEXT, '') ||
                                COALESCE(NEW.exclusive::TEXT, '') ||
                                COALESCE(NEW.formatted_text_song, '') ||
                                COALESCE(NEW.formatted_text_tabs, '') ||
                                COALESCE(NEW.formatted_text_chords, '') ||
                                COALESCE(NEW.id_max_lyrics, '') ||
                                COALESCE(NEW.id_max_karaoke, '') ||
                                COALESCE(NEW.id_max_chords, '') ||
                                COALESCE(NEW.id_max_melody, '') ||
                                COALESCE(NEW.version_max_lyrics::TEXT, '') ||
                                COALESCE(NEW.version_max_karaoke::TEXT, '') ||
                                COALESCE(NEW.version_max_chords::TEXT, '') ||
                                COALESCE(NEW.version_max_melody::TEXT, '') ||
                                COALESCE(NEW.id_tariff::TEXT, '') ||
                                COALESCE(NEW.id_dzen_demo, '') ||
                                COALESCE(NEW.version_dzen_demo::TEXT, '') ||
                                COALESCE(NEW.id_vk_demo, '') ||
                                COALESCE(NEW.version_vk_demo::TEXT, '') ||
                                COALESCE(NEW.id_telegram_demo, '') ||
                                COALESCE(NEW.version_telegram_demo::TEXT, '') ||
                                COALESCE(NEW.id_max_demo, '') ||
                                COALESCE(NEW.version_max_demo::TEXT, '') ||
                                COALESCE(NEW.song_type, '') ||
                                COALESCE(NEW.audio_parent_id::TEXT, '') ||
                                COALESCE(NEW.audio_similarity_percent::TEXT, '') ||
                                COALESCE(NEW.audio_delta_ms::TEXT, '') ||
                                COALESCE(NEW.audio_compare_history, '') ||
                                COALESCE(NEW.stem_accompaniment_ready::TEXT, '') ||
                                COALESCE(NEW.stem_vocal_ready::TEXT, '') ||
                                COALESCE(NEW.picture_album_ready::TEXT, '') ||
                                COALESCE(NEW.picture_author_ready::TEXT, '')
        );
RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS update_recordhash_trigger ON public.tbl_settings;
CREATE TRIGGER update_recordhash_trigger BEFORE INSERT OR UPDATE ON public.tbl_settings FOR EACH ROW EXECUTE FUNCTION public.update_tbl_settings_recordhash();

-- Аналогично для tbl_settings_sync — та же md5-формула с добавленными 4 полями.
CREATE OR REPLACE FUNCTION public.update_tbl_settings_sync_recordhash()
    RETURNS trigger
    LANGUAGE plpgsql
    AS $$
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
        COALESCE(NEW.rate::TEXT, '') ||
        COALESCE(NEW.root_id::TEXT, '') ||
        COALESCE(NEW.free::TEXT, '') ||
        COALESCE(NEW.exclusive::TEXT, '') ||
        COALESCE(NEW.formatted_text_song, '') ||
        COALESCE(NEW.formatted_text_tabs, '') ||
        COALESCE(NEW.formatted_text_chords, '') ||
        COALESCE(NEW.id_max_lyrics, '') ||
        COALESCE(NEW.id_max_karaoke, '') ||
        COALESCE(NEW.id_max_chords, '') ||
        COALESCE(NEW.id_max_melody, '') ||
        COALESCE(NEW.version_max_lyrics::TEXT, '') ||
        COALESCE(NEW.version_max_karaoke::TEXT, '') ||
        COALESCE(NEW.version_max_chords::TEXT, '') ||
        COALESCE(NEW.version_max_melody::TEXT, '') ||
        COALESCE(NEW.id_dzen_demo, '') ||
        COALESCE(NEW.version_dzen_demo::TEXT, '') ||
        COALESCE(NEW.id_vk_demo, '') ||
        COALESCE(NEW.version_vk_demo::TEXT, '') ||
        COALESCE(NEW.id_telegram_demo, '') ||
        COALESCE(NEW.version_telegram_demo::TEXT, '') ||
        COALESCE(NEW.id_max_demo, '') ||
        COALESCE(NEW.version_max_demo::TEXT, '') ||
        COALESCE(NEW.song_type, '') ||
        COALESCE(NEW.audio_parent_id::TEXT, '') ||
        COALESCE(NEW.audio_similarity_percent::TEXT, '') ||
        COALESCE(NEW.audio_delta_ms::TEXT, '') ||
        COALESCE(NEW.audio_compare_history, '') ||
        COALESCE(NEW.stem_accompaniment_ready::TEXT, '') ||
        COALESCE(NEW.stem_vocal_ready::TEXT, '') ||
        COALESCE(NEW.picture_album_ready::TEXT, '') ||
        COALESCE(NEW.picture_author_ready::TEXT, '')
    );
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS update_recordhash_settings_synctrigger ON public.tbl_settings_sync;
CREATE TRIGGER update_recordhash_settings_synctrigger BEFORE UPDATE OR INSERT ON public.tbl_settings_sync FOR EACH ROW EXECUTE FUNCTION public.update_tbl_settings_sync_recordhash();

-- Полный пересчёт recordhash по новой формуле для всех существующих строк в обеих таблицах.
-- Триггер сделает то же при UPDATE любых полей, но те строки, которые никто не трогает,
-- останутся со старыми хэшами (вычисленными БЕЗ новых полей) и будут восприниматься sync'ом
-- как отличающиеся от "правильных" с любой стороны. Поэтому делаем массовый UPDATE.
UPDATE public.tbl_settings
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
        COALESCE(rate::TEXT, '') ||
        COALESCE(root_id::TEXT, '') ||
        COALESCE(free::TEXT, '') ||
        COALESCE(exclusive::TEXT, '') ||
        COALESCE(formatted_text_song, '') ||
        COALESCE(formatted_text_tabs, '') ||
        COALESCE(formatted_text_chords, '') ||
        COALESCE(id_max_lyrics, '') ||
        COALESCE(id_max_karaoke, '') ||
        COALESCE(id_max_chords, '') ||
        COALESCE(id_max_melody, '') ||
        COALESCE(version_max_lyrics::TEXT, '') ||
        COALESCE(version_max_karaoke::TEXT, '') ||
        COALESCE(version_max_chords::TEXT, '') ||
        COALESCE(version_max_melody::TEXT, '') ||
        COALESCE(id_tariff::TEXT, '') ||
        COALESCE(id_dzen_demo, '') ||
        COALESCE(version_dzen_demo::TEXT, '') ||
        COALESCE(id_vk_demo, '') ||
        COALESCE(version_vk_demo::TEXT, '') ||
        COALESCE(id_telegram_demo, '') ||
        COALESCE(version_telegram_demo::TEXT, '') ||
        COALESCE(id_max_demo, '') ||
        COALESCE(version_max_demo::TEXT, '') ||
        COALESCE(song_type, '') ||
        COALESCE(audio_parent_id::TEXT, '') ||
        COALESCE(audio_similarity_percent::TEXT, '') ||
        COALESCE(audio_delta_ms::TEXT, '') ||
        COALESCE(audio_compare_history, '') ||
        COALESCE(stem_accompaniment_ready::TEXT, '') ||
        COALESCE(stem_vocal_ready::TEXT, '') ||
        COALESCE(picture_album_ready::TEXT, '') ||
        COALESCE(picture_author_ready::TEXT, '')
) WHERE id > 0;

UPDATE public.tbl_settings_sync
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
        COALESCE(rate::TEXT, '') ||
        COALESCE(root_id::TEXT, '') ||
        COALESCE(free::TEXT, '') ||
        COALESCE(exclusive::TEXT, '') ||
        COALESCE(formatted_text_song, '') ||
        COALESCE(formatted_text_tabs, '') ||
        COALESCE(formatted_text_chords, '') ||
        COALESCE(id_max_lyrics, '') ||
        COALESCE(id_max_karaoke, '') ||
        COALESCE(id_max_chords, '') ||
        COALESCE(id_max_melody, '') ||
        COALESCE(version_max_lyrics::TEXT, '') ||
        COALESCE(version_max_karaoke::TEXT, '') ||
        COALESCE(version_max_chords::TEXT, '') ||
        COALESCE(version_max_melody::TEXT, '') ||
        COALESCE(id_dzen_demo, '') ||
        COALESCE(version_dzen_demo::TEXT, '') ||
        COALESCE(id_vk_demo, '') ||
        COALESCE(version_vk_demo::TEXT, '') ||
        COALESCE(id_telegram_demo, '') ||
        COALESCE(version_telegram_demo::TEXT, '') ||
        COALESCE(id_max_demo, '') ||
        COALESCE(version_max_demo::TEXT, '') ||
        COALESCE(song_type, '') ||
        COALESCE(audio_parent_id::TEXT, '') ||
        COALESCE(audio_similarity_percent::TEXT, '') ||
        COALESCE(audio_delta_ms::TEXT, '') ||
        COALESCE(audio_compare_history, '') ||
        COALESCE(stem_accompaniment_ready::TEXT, '') ||
        COALESCE(stem_vocal_ready::TEXT, '') ||
        COALESCE(picture_album_ready::TEXT, '') ||
        COALESCE(picture_author_ready::TEXT, '')
) WHERE id > 0;
