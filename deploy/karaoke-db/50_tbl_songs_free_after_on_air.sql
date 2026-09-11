-- Флаг «не снимать с эфира» (free_after_on_air) на `tbl_songs` и `tbl_songs_sync`
-- (см. specs/369-free-after-onair-flag/spec.md, OpenProject #81 «Флаг не снимать с эфира»).
--
-- Семантика: после наступления dateTimePublish песня остаётся публично доступной
-- (ON_AIR / AccessMode.open) даже после истечения стандартного окна бесплатного
-- доступа (1 календарный месяц). Не путать с `free=true` («всегда бесплатно») и
-- `exclusive=true` (premium-only по бизнес-решению).
--
-- Поле участвует в двух sync-таблицах (tbl_songs и tbl_songs_sync — см. Constitution III):
--   - Контракт KaraokeDbTable (id + recordhash) сохранён.
--   - Пересобираем update_tbl_songs_recordhash() и update_tbl_songs_sync_recordhash(),
--     добавив COALESCE(NEW.free_after_on_air::TEXT, 'false') в md5-цепочку.
--   - Пересчитываем recordhash для существующих строк ОБЕИХ таблиц (иначе LOCAL↔SERVER
--     молча разойдутся до первого UPDATE).
--
-- По образцу 42_song_name_censored.sql / 43_song_name_censored_sync.sql: одна миграция
-- покрывает ОБЕ таблицы (вместо двух отдельных), так как порядок применения очевиден
-- (один файл → один docker exec). См. runbook в конце файла.
--
-- ВАЖНО: имя таблицы — `tbl_songs` (НЕ `tbl_settings`). В миграции 28
-- (28_rename_settings_to_songs.sql) таблица была переименована. Использование
-- старого имени в новых миграциях ЗАПРЕЩЕНО — проверяется скриптом
-- tools/check-no-legacy-tbl-settings.sh (Pass 369).
--
-- Default = false для обратной совместимости: все существующие 18 097 песен
-- получают false без backfill-скрипта.

-- ==========================================================================================
-- tbl_songs — добавить колонку
-- ==========================================================================================
ALTER TABLE public.tbl_songs
    ADD COLUMN IF NOT EXISTS free_after_on_air BOOLEAN NOT NULL DEFAULT false;

-- ==========================================================================================
-- Пересобрать update_tbl_songs_recordhash(), включив free_after_on_air в md5.
-- Колонка добавлена В КОНЕЦ цепочки (после song_name_censored — поля добавленного
-- в Pass 277), чтобы не пересчитывать recordhash существующих строк без необходимости.
-- ==========================================================================================
CREATE OR REPLACE FUNCTION public.update_tbl_songs_recordhash() RETURNS trigger
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
        COALESCE(NEW.player_readiness_flags, '') ||
        COALESCE(NEW.album_id::TEXT, '') ||
        COALESCE(NEW.description, '') ||
        COALESCE(NEW.short_description, '') ||
        COALESCE(NEW.warning, '') ||
        COALESCE(NEW.song_name_censored, '') ||
        COALESCE(NEW.free_after_on_air::TEXT, 'false')
    );
RETURN NEW;
END;
$$;

-- ==========================================================================================
-- Backfill recordhash для существующих строк tbl_songs — иначе LOCAL↔SERVER будут
-- молча расходиться до первого UPDATE каждой строки. Колонка free_after_on_air имеет
-- DEFAULT false, так что содержимое не меняется, но md5 — да (т.к. триггер теперь
-- учитывает новое поле). Не через SET id = id (id — GENERATED ALWAYS AS IDENTITY),
-- а явным пересчётом — по образцу 42_song_name_censored.sql.
-- ==========================================================================================
UPDATE public.tbl_songs SET recordhash = md5(
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
    COALESCE(player_readiness_flags, '') ||
    COALESCE(album_id::TEXT, '') ||
    COALESCE(description, '') ||
    COALESCE(short_description, '') ||
    COALESCE(warning, '') ||
    COALESCE(song_name_censored, '') ||
    COALESCE(free_after_on_air::TEXT, 'false')
) WHERE id > 0;

-- ==========================================================================================
-- tbl_songs_sync — добавить колонку (синк-таблица используется в Utils.setSongToSyncRemoteTable
-- для отправки зашифрованного SQL на /changerecords; её собственный триггер тоже должен
-- учитывать колонку, иначе 1) INSERT INTO tbl_songs_sync упадёт column free_after_on_air
-- does not exist, 2) даже если колонка появится, запись её recordhash не будет включать
-- значение).
-- ==========================================================================================
ALTER TABLE public.tbl_songs_sync
    ADD COLUMN IF NOT EXISTS free_after_on_air BOOLEAN NOT NULL DEFAULT false;

-- ==========================================================================================
-- Пересобрать update_tbl_songs_sync_recordhash() — добавить free_after_on_air в md5
-- (по образцу 43_song_name_censored_sync.sql для основной таблицы).
-- ==========================================================================================
CREATE OR REPLACE FUNCTION public.update_tbl_songs_sync_recordhash() RETURNS trigger
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
        COALESCE(player_readiness_flags, '') ||
        COALESCE(song_name_censored, '') ||
        COALESCE(free_after_on_air::TEXT, 'false')
    );
    RETURN NEW;
END;
$$;

-- ==========================================================================================
-- Backfill колонки и recordhash для существующих строк tbl_songs_sync (если есть).
-- Колонка free_after_on_air уже создана выше с DEFAULT false (подставляется автоматически),
-- достаточно пересчитать recordhash для существующих строк (md5 теперь включает новое поле).
-- ==========================================================================================
UPDATE public.tbl_songs_sync SET recordhash = md5(
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
    COALESCE(player_readiness_flags, '') ||
    COALESCE(song_name_censored, '') ||
    COALESCE(free_after_on_air::TEXT, 'false')
) WHERE id > 0;

-- ==========================================================================================
-- Runbook для применения на проде (Pass 369)
-- ==========================================================================================
-- 1. Подключиться к прод-серверу: ssh root@${PROD_HOST:-<server>}
-- 2. Применить этот файл на прод-БД (идемпотентно):
--      docker exec -i karaoke-db psql -U postgres -d karaoke \
--        < /root/Karaoke/deploy/karaoke-db/50_tbl_songs_free_after_on_air.sql
-- 3. Проверить:
--      docker exec karaoke-db psql -U postgres -d karaoke \
--        -c "\d tbl_songs" | grep free_after_on_air
--      docker exec karaoke-db psql -U postgres -d karaoke \
--        -c "\d tbl_songs_sync" | grep free_after_on_air
--      docker exec karaoke-db psql -U postgres -d karaoke -tAc \
--        "SELECT prosrc FROM pg_proc WHERE proname = 'update_tbl_songs_recordhash'" \
--        | grep free_after_on_air
--      docker exec karaoke-db psql -U postgres -d karaoke -tAc \
--        "SELECT prosrc FROM pg_proc WHERE proname = 'update_tbl_songs_sync_recordhash'" \
--        | grep free_after_on_air
-- 4. После успешного (3) — деплоить karaoke-app и karaoke-web
--    (cd /root/Karaoke/deploy && bash do.sh build_start_web; build_start_app — пользователь).
-- ==========================================================================================
