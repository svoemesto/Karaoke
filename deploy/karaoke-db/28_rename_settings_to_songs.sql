-- Переименование сущности "песня": Settings→Song (Kotlin) требует переименования и физической
-- таблицы, чтобы имя в коде/API/БД было согласовано (см. specs/011-album-song-rename/research.md
-- §5, §5.1 — явное решение пользователя после ознакомления с рисками; изначальная рекомендация
-- была "оставить физическое имя как есть", отклонена).
--
-- ВАЖНО (runbook, research.md §5.1): применять на LOCAL и на PROD СТРОГО в этом порядке:
--   1. Эта миграция (переименование) -- на LOCAL.
--   2. Обновить Kotlin-код (Settings.TABLE_NAME = "tbl_songs" + все raw-SQL литералы) и
--      пересобрать/перезапустить karaoke-app/karaoke-web (перезапуск karaoke-app -- только
--      пользователь, см. constitution.md).
--   3. Убедиться, что список/редактирование/синхронизация песен работают как раньше.
--   4. Только после этого -- та же миграция и код на PROD, по прямому согласию пользователя.
-- Если применить эту миграцию БЕЗ немедленного обновления кода -- приложение сломается сразу
-- ("relation tbl_settings does not exist"), т.к. Settings.TABLE_NAME на момент этой миграции
-- всё ещё "tbl_settings".
--
-- Переименовывается ТОЛЬКО физическое имя (таблица/sequence/констрейнты/индексы/функции с
-- "settings" в имени). Данные, колонки, генерируемые ими значения -- не меняются. Ключ
-- регистрации синхронизации SyncTarget.key = "settings" (используется в несохранённом в git
-- Karaoke.properties на машине администратора) НЕ переименовывается -- независимая деталь,
-- см. research.md §5.

-- ==========================================================================================
-- tbl_settings -> tbl_songs
-- ==========================================================================================
ALTER TABLE public.tbl_settings RENAME TO tbl_songs;

ALTER SEQUENCE public.tbl_settings_id_seq RENAME TO tbl_songs_id_seq;

ALTER TABLE public.tbl_songs RENAME CONSTRAINT tbl_settings_pkey TO tbl_songs_pkey;
ALTER TABLE public.tbl_songs RENAME CONSTRAINT tbl_settings_id_key TO tbl_songs_id_key;

ALTER INDEX public.idx_tbl_settings_audio_parent_id RENAME TO idx_tbl_songs_audio_parent_id;
ALTER INDEX public.idx_tbl_settings_recordhash RENAME TO idx_tbl_songs_recordhash;
ALTER INDEX public.tbl_settings_file_name_index RENAME TO tbl_songs_file_name_index;
ALTER INDEX public.tbl_settings_id_boosty_files_index RENAME TO tbl_songs_id_boosty_files_index;
ALTER INDEX public.tbl_settings_id_boosty_index RENAME TO tbl_songs_id_boosty_index;
ALTER INDEX public.tbl_settings_id_dzen_chords_index RENAME TO tbl_songs_id_dzen_chords_index;
ALTER INDEX public.tbl_settings_id_dzen_demo_index RENAME TO tbl_songs_id_dzen_demo_index;
ALTER INDEX public.tbl_settings_id_dzen_karaoke_index RENAME TO tbl_songs_id_dzen_karaoke_index;
ALTER INDEX public.tbl_settings_id_dzen_lyrics_index RENAME TO tbl_songs_id_dzen_lyrics_index;
ALTER INDEX public.tbl_settings_id_dzen_melody_index RENAME TO tbl_songs_id_dzen_melody_index;
ALTER INDEX public.tbl_settings_id_max_demo_index RENAME TO tbl_songs_id_max_demo_index;
ALTER INDEX public.tbl_settings_id_pl_chords_index RENAME TO tbl_songs_id_pl_chords_index;
ALTER INDEX public.tbl_settings_id_pl_karaoke_index RENAME TO tbl_songs_id_pl_karaoke_index;
ALTER INDEX public.tbl_settings_id_pl_lyrics_index RENAME TO tbl_songs_id_pl_lyrics_index;
ALTER INDEX public.tbl_settings_id_pl_melody_index RENAME TO tbl_songs_id_pl_melody_index;
ALTER INDEX public.tbl_settings_id_status_index RENAME TO tbl_songs_id_status_index;
ALTER INDEX public.tbl_settings_id_telegram_chords_index RENAME TO tbl_songs_id_telegram_chords_index;
ALTER INDEX public.tbl_settings_id_telegram_demo_index RENAME TO tbl_songs_id_telegram_demo_index;
ALTER INDEX public.tbl_settings_id_telegram_karaoke_index RENAME TO tbl_songs_id_telegram_karaoke_index;
ALTER INDEX public.tbl_settings_id_telegram_lyrics_index RENAME TO tbl_songs_id_telegram_lyrics_index;
ALTER INDEX public.tbl_settings_id_telegram_melody_index RENAME TO tbl_songs_id_telegram_melody_index;
ALTER INDEX public.tbl_settings_id_vk_chords_index RENAME TO tbl_songs_id_vk_chords_index;
ALTER INDEX public.tbl_settings_id_vk_demo_index RENAME TO tbl_songs_id_vk_demo_index;
ALTER INDEX public.tbl_settings_id_vk_index RENAME TO tbl_songs_id_vk_index;
ALTER INDEX public.tbl_settings_id_vk_karaoke_index RENAME TO tbl_songs_id_vk_karaoke_index;
ALTER INDEX public.tbl_settings_id_vk_lyrics_index RENAME TO tbl_songs_id_vk_lyrics_index;
ALTER INDEX public.tbl_settings_id_vk_melody_index RENAME TO tbl_songs_id_vk_melody_index;
ALTER INDEX public.tbl_settings_last_update_index RENAME TO tbl_songs_last_update_index;
ALTER INDEX public.tbl_settings_publish_date_index RENAME TO tbl_songs_publish_date_index;
ALTER INDEX public.tbl_settings_publish_time_index RENAME TO tbl_songs_publish_time_index;
ALTER INDEX public.tbl_settings_root_folder_index RENAME TO tbl_songs_root_folder_index;
ALTER INDEX public.tbl_settings_root_id_index RENAME TO tbl_songs_root_id_index;
ALTER INDEX public.tbl_settings_song_album_index RENAME TO tbl_songs_song_album_index;
ALTER INDEX public.tbl_settings_song_author_index RENAME TO tbl_songs_song_author_index;
ALTER INDEX public.tbl_settings_song_name_index RENAME TO tbl_songs_song_name_index;
ALTER INDEX public.tbl_settings_song_year_index RENAME TO tbl_songs_song_year_index;
ALTER INDEX public.tbl_settings_status_process_chords_index RENAME TO tbl_songs_status_process_chords_index;
ALTER INDEX public.tbl_settings_status_process_karaoke_index RENAME TO tbl_songs_status_process_karaoke_index;
ALTER INDEX public.tbl_settings_status_process_lyrics_index RENAME TO tbl_songs_status_process_lyrics_index;
-- idx_gin_result_text -- имя не содержит "settings", переименование не требуется.

-- Функция вызывается update_recordhash_trigger по OID, переименование функции не рвёт триггер.
ALTER FUNCTION public.update_tbl_settings_recordhash() RENAME TO update_tbl_songs_recordhash;
-- update_last_updated_trigger / update_recordhash_trigger -- имена не содержат "settings",
-- переименование не требуется (они per-table, конфликтов имён нет).

-- ==========================================================================================
-- tbl_settings_sync -> tbl_songs_sync (служебная таблица очереди синхронизации)
-- ==========================================================================================
ALTER TABLE public.tbl_settings_sync RENAME TO tbl_songs_sync;

ALTER SEQUENCE public.tbl_settings_sync_id_seq RENAME TO tbl_songs_sync_id_seq;

ALTER TABLE public.tbl_songs_sync RENAME CONSTRAINT tbl_settings_sync_pkey TO tbl_songs_sync_pkey;
ALTER TABLE public.tbl_songs_sync RENAME CONSTRAINT tbl_settings_sync_id_key TO tbl_songs_sync_id_key;

ALTER INDEX public.idx_tbl_settings_sync_recordhash RENAME TO idx_tbl_songs_sync_recordhash;

ALTER FUNCTION public.update_tbl_settings_sync_recordhash() RENAME TO update_tbl_songs_sync_recordhash;

ALTER TRIGGER update_recordhash_settings_synctrigger ON public.tbl_songs_sync RENAME TO update_recordhash_songs_sync_trigger;
-- update_sync_last_updated_trigger -- имя не содержит "settings", переименование не требуется.
