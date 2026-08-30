-- Tier-2 P1 оптимизация FR-110 из parent спеки 241-db-storage-perf-audit.
--
-- Контекст: все три индекса УЖЕ созданы в 01_initdb.sql (изначально как
-- tbl_settings_*_index, переименованы в 28_rename_settings_to_songs.sql в tbl_songs_*_index;
-- tbl_events_song_id_index был в исходном 01_initdb.sql). На текущем проде эта миграция —
-- no-op (CREATE INDEX IF NOT EXISTS пропускает уже существующие).
--
-- Зачем миграция всё равно нужна:
--   1. Документирование в git-истории — явный артефакт в deploy/karaoke-db/.
--   2. Защита от восстановления БД из старого дампа, в котором индексов нет.
--   3. Baseline для будущих фич, которым понадобятся эти индексы.
--
-- Effect: ускорение hot queries из parent спеки 241:
--   - Song.loadListAuthors: SELECT DISTINCT song_author FROM tbl_songs ORDER BY song_author
--   - Song.loadAuthorSongCounts: SELECT song_author, count(*) FROM tbl_songs
--     [WHERE id_status >= 6] GROUP BY song_author
--   - StatBySong.getStatBySong: SELECT song_id, count(*) FILTER (...) FROM tbl_events
--     GROUP BY song_id
--
-- Convention имён: tbl_<table>_<column>_index (НЕ idx_* — convention проекта с 01_initdb.sql).
--
-- Идемпотентна: CREATE INDEX IF NOT EXISTS — повторное применение no-op.
-- Намеренно НЕ используется CREATE INDEX CONCURRENTLY: несовместимо с IF NOT EXISTS в
-- PostgreSQL <14 (даже в 14+ даёт неожиданное поведение при отсутствии индекса), а миграция
-- гарантированно либо no-op (на текущем проде), либо применяется на маленькой dev-БД,
-- где блокировка миллисекунды.
--
-- Apply:
--   локально: docker exec -i karaoke-db psql -U postgres -d karaoke \
--             < deploy/karaoke-db/41_db_indexes_verification.sql
--   прод:     ssh root@${PROD_HOST:-188.119.64.111} \
--             'docker exec -i karaoke-db psql -U postgres -d karaoke \
--              < /root/Karaoke/deploy/karaoke-db/41_db_indexes_verification.sql'
--
-- Refs: specs/241-db-storage-perf-audit/spec.md (FR-110, H-5, H-6, H-110),
--       specs/270-db-indexes-verification/spec.md (parent feature),
--       deploy/karaoke-db/01_initdb.sql:148,173 (исходное создание),
--       deploy/karaoke-db/28_rename_settings_to_songs.sql:48,66 (переименование).

-- 1. song_author — для Song.loadListAuthors и Song.loadAuthorSongCounts
--    (DISTINCT/GROUP BY song_author — главная страница «Закромов»).
CREATE INDEX IF NOT EXISTS tbl_songs_song_author_index
    ON public.tbl_songs USING btree (song_author);

-- 2. id_status — для Song.loadAuthorSongCounts с onlyPublished=true
--    (WHERE id_status >= 6 — «в коллекции», тот же фильтр, что в Zakroma.getZakroma).
CREATE INDEX IF NOT EXISTS tbl_songs_id_status_index
    ON public.tbl_songs USING btree (id_status);

-- 3. song_id — для StatBySong.getStatBySong и других GROUP BY song_id по tbl_events
--    (17 условных count(*) FILTER (...) + GROUP BY song_id, song_author, song_album, song_name).
CREATE INDEX IF NOT EXISTS tbl_events_song_id_index
    ON public.tbl_events USING btree (song_id);