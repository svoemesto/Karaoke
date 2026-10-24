-- ==========================================================================================
-- tbl_songs_sync — догнать схему tbl_songs: description-поля, тариф и статус демо.
--
-- Аудит #336, порция P-71, BR-533, OpenProject #363. `Song.getSqlToInsert(sync = true)`
-- (karaoke-app/.../model/Song.kt) пишет 91 колонку в tbl_songs_sync, а пяти из них в этой
-- таблице не было: status_process_demo, id_tariff, description, short_description, warning.
-- Ни одна миграция их в sync-таблицу не добавляла — после 28_rename_settings_to_songs.sql
-- туда попадали только song_name_censored (43) и free_after_on_air (50).
--
-- Следствие: ЛЮБОЙ push песни в SYNC-таблицу падал на первой же песне батча
--   ERROR: column "status_process_demo" of relation "tbl_songs_sync" does not exist
-- и ни одна песня не уезжала в sync: webvue3 «Добавить в SYNC» →
-- POST /api/songs/addsyncforall → Utils.setSongToSyncRemoteTable → INSERT INTO tbl_songs_sync
-- на PROD через /changerecords.
--
-- Что делает миграция (по образцу 43_song_name_censored_sync.sql):
--   1. добавляет пять колонок; определения — как в tbl_songs
--      (15_monetization.sql:169 — id_tariff; dump karaoke_clear_dump.sql — status_process_demo;
--      31_entity_description_fields.sql:72–74 — description / short_description / warning);
--   2. пересобирает update_tbl_songs_sync_recordhash(), включая четыре новых поля в md5:
--      без этого изменение description/warning не считалось бы расхождением, ведь sync
--      сравнивает строки только по recordhash;
--   3. проверяет, что в теле функции нет «голых» имён колонок — ровно тот регресс, что был
--      в 50_tbl_songs_free_after_on_air.sql (BR-553, потерян префикс NEW. в 31 колонке);
--   4. переносит значения из tbl_songs (если эти колонки там есть) и пересчитывает recordhash
--      существующих строк.
--
-- Почему status_process_demo добавлен как КОЛОНКА, но НЕ входит в md5: так же устроена
-- update_tbl_songs_recordhash() (миграции 31/42/50) — это транзиентный статус обработки, а
-- SongSyncTarget.shouldPush() отдельно отбрасывает диффы с префиксом `status_process_` как шум.
-- Формулы двух таблиц должны оставаться максимально близкими, поэтому набор колонок хэша здесь
-- = набор колонок tbl_songs (id_tariff / description / short_description / warning) без
-- status_process_demo.
--
-- ИЗВЕСТНОЕ РАСХОЖДЕНИЕ ВНЕ ЭТОЙ МИГРАЦИИ: в tbl_songs_sync нет колонки album_id, которая есть
-- в tbl_songs и входит в его recordhash (29_albums.sql:175). INSERT её не пишет, поэтому
-- падения нет, но UPDATE ... SET album_id на sync-таблице упадёт. Отдельная находка — отчёт
-- specs/363-pkg-db-recordhash/report.md, раздел «Что осталось за владельцем».
--
-- ВАЖНО (порядок применения): после этой миграции НЕЛЬЗЯ переприменять
-- 50_tbl_songs_free_after_on_air.sql — она пересобирает ту же функцию по состоянию на момент 50
-- (95 колонок) и потеряет четыре новых. 50 и 52 правятся вместе; 52 — последняя по номеру.
--
-- Применяется один раз на КАЖДОЙ БД, где есть tbl_songs_sync (LOCAL и PROD).
-- Идемпотентно: ADD COLUMN IF NOT EXISTS / CREATE OR REPLACE / UPDATE.
-- ==========================================================================================

-- ==========================================================================================
-- 1. Пять колонок tbl_songs_sync (определения — как в tbl_songs).
-- ==========================================================================================
ALTER TABLE public.tbl_songs_sync
    ADD COLUMN IF NOT EXISTS status_process_demo VARCHAR(255) DEFAULT '' NOT NULL;
ALTER TABLE public.tbl_songs_sync
    ADD COLUMN IF NOT EXISTS id_tariff INTEGER DEFAULT 0 NOT NULL;
ALTER TABLE public.tbl_songs_sync
    ADD COLUMN IF NOT EXISTS description TEXT DEFAULT '' NOT NULL;
ALTER TABLE public.tbl_songs_sync
    ADD COLUMN IF NOT EXISTS short_description VARCHAR(255) DEFAULT '' NOT NULL;
ALTER TABLE public.tbl_songs_sync
    ADD COLUMN IF NOT EXISTS warning VARCHAR(255) DEFAULT '' NOT NULL;

-- ==========================================================================================
-- 2. Пересобрать update_tbl_songs_sync_recordhash(): четыре новых поля в md5
--    (порядок — как в update_tbl_songs_recordhash(); новые колонки добавлены в конец цепочки).
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
        COALESCE(NEW.player_readiness_flags, '') ||
        COALESCE(NEW.song_name_censored, '') ||
        COALESCE(NEW.free_after_on_air::TEXT, 'false') ||
        COALESCE(NEW.description, '') ||
        COALESCE(NEW.short_description, '') ||
        COALESCE(NEW.warning, '') ||
        COALESCE(NEW.id_tariff::TEXT, '')
    );
    RETURN NEW;
END;
$$;

-- ==========================================================================================
-- 3. Проверка (BR-553): в теле функции-триггера ВСЕ ссылки на колонки обязаны быть с NEW.
--
-- В PL/pgSQL идентификатор, не найденный среди переменных функции, передаётся SQL-парсеру как
-- ссылка на колонку; у функции-триггера нет FROM, поэтому «голое» имя не разрешается:
--   ERROR: column "description" does not exist
-- CREATE FUNCTION такую ошибку НЕ ловит (тело plpgsql проверяется только синтаксически) —
-- функция создаётся, а падает триггер на первой же записи. Проверка срабатывает сразу при
-- применении миграции, а не в момент первой синхронизации.
-- ==========================================================================================
DO $$
DECLARE
    v_body      TEXT;
    v_all       INTEGER;
    v_qualified INTEGER;
BEGIN
    SELECT p.prosrc INTO v_body
      FROM pg_proc p
      JOIN pg_namespace n ON n.oid = p.pronamespace
     WHERE p.proname = 'update_tbl_songs_sync_recordhash'
       AND n.nspname = 'public';

    IF v_body IS NULL THEN
        RAISE EXCEPTION 'Функция public.update_tbl_songs_sync_recordhash() не найдена';
    END IF;

    v_all       := (length(v_body) - length(replace(v_body, 'COALESCE(', ''))) / length('COALESCE(');
    v_qualified := (length(v_body) - length(replace(v_body, 'COALESCE(NEW.', ''))) / length('COALESCE(NEW.');

    IF v_all <> v_qualified THEN
        RAISE EXCEPTION
            'В update_tbl_songs_sync_recordhash() % из % ссылок на колонки без префикса NEW. — триггер упадёт на первой записи',
            v_all - v_qualified, v_all;
    END IF;
END;
$$;

-- ==========================================================================================
-- 4. Перенести значения пяти колонок из tbl_songs (там они уже есть) в sync-таблицу, чтобы
--    recordhash строк совпал сразу и первый прогон sync не показывал ложных расхождений.
--
--    Перенос выполняется ТОЛЬКО если в tbl_songs есть все пять колонок: на БД, где миграции
--    31 ещё нет, статический UPDATE упал бы при первом выполнении этой ветки. plpgsql
--    разбирает оператор лениво — при невыполненном IF он не разбирается вовсе.
-- ==========================================================================================
DO $$
BEGIN
    IF to_regclass('public.tbl_songs') IS NOT NULL
       AND (
            SELECT count(*)
              FROM information_schema.columns
             WHERE table_schema = 'public'
               AND table_name = 'tbl_songs'
               AND column_name IN ('status_process_demo', 'id_tariff', 'description',
                                   'short_description', 'warning')
           ) = 5
    THEN
        UPDATE public.tbl_songs_sync AS s
           SET status_process_demo = t.status_process_demo,
               id_tariff           = t.id_tariff,
               description         = t.description,
               short_description   = t.short_description,
               warning             = t.warning
          FROM public.tbl_songs AS t
         WHERE s.id = t.id;
    END IF;
END;
$$;

-- ==========================================================================================
-- 5. Backfill recordhash для существующих строк tbl_songs_sync: формула md5 изменилась
--    (добавлены четыре колонки), без пересчёта строки остались бы со старым хэшем до первого
--    UPDATE и выглядели бы расхождением в diff LOCAL↔SERVER.
-- ==========================================================================================
UPDATE public.tbl_songs_sync
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
        COALESCE(player_readiness_flags, '') ||
        COALESCE(song_name_censored, '') ||
        COALESCE(free_after_on_air::TEXT, 'false') ||
        COALESCE(description, '') ||
        COALESCE(short_description, '') ||
        COALESCE(warning, '') ||
        COALESCE(id_tariff::TEXT, '')
)
WHERE id > 0;

-- ==========================================================================================
-- Runbook применения (миграции — manual, knowledge/system/infra/sql-migrations.md, Решение 4)
-- ==========================================================================================
-- 1. Убедиться, что в админке не идёт пакет «Добавить в SYNC» (БД не в середине синхронизации)
--    и что 50 уже применена: 52 пересобирает ту же функцию, порядок «50 → 52» важен.
-- 2. LOCAL:  psql -v ON_ERROR_STOP=1 -f deploy/karaoke-db/52_tbl_songs_sync_description_fields.sql
-- 3. PROD:   docker exec -i karaoke-db psql -v ON_ERROR_STOP=1 -U postgres -d karaoke \
--              < /root/Karaoke/deploy/karaoke-db/52_tbl_songs_sync_description_fields.sql
--    -v ON_ERROR_STOP=1 обязателен: без него psql продолжает после ошибки, и проверка
--    DO $$ (BR-553) не остановит применение.
-- 4. Проверить на ОБЕИХ БД состав колонок и тело функции:
--      \d tbl_songs_sync | grep -E 'status_process_demo|id_tariff|description|warning'
--      SELECT prosrc FROM pg_proc WHERE proname = 'update_tbl_songs_sync_recordhash';
--    в теле должно быть 99 COALESCE(...), все — с NEW.; description / short_description /
--    warning / id_tariff присутствуют, status_process_demo — нет.
-- 5. Регресс «до/после» на живой БД — INSERT в sync-таблицу больше не падает на отсутствующей
--    колонке:
--      INSERT INTO tbl_songs_sync (id, song_name, description, warning, id_tariff)
--      VALUES (900001, 'smoke', 'd', 'w', 0) OVERRIDING SYSTEM VALUE;
--      DELETE FROM tbl_songs_sync WHERE id = 900001;
-- 6. Только после (2)–(5) нажимать «Добавить в SYNC» в webvue3 и убедиться, что батч уходит
--    без ERROR (текст ошибки БД наружу больше не отдаётся: с 554 такой сбой приходит как 500).
-- ==========================================================================================
