-- KARAOKE-429 (спека 739 «удаление песни с каскадной уборкой»), FR-043.
--
-- Зачем: разовая уборка (FR-041) чистит не только файлы и объекты, но и строки-сироты
--   в БД — черновики заданий без задания и исторические ссылки на несуществующие песни
--   (решение владельца KARAOKE-426). Для таких действий в журнале нужен свой вид
--   записи: `DB_ROWS`.
--
-- ФАКТ (2026-11-13): ограничение из миграции 60 разрешало только
--   ('FILE','STORAGE_OBJECT','ALBUM','PICTURE','ALBUM_FOLDER'), поэтому пять записей
--   журнала прогона №212 (удаление строк-сироты в БД) не записались: приложение
--   залогировало `song.cleanup.entry.write_failed ... violates check constraint
--   "tbl_song_cleanup_entries_kind_chk"`. Сами удаления выполнены и проверены, но
--   журнал оказался неполным (FR-043 требует записи каждого действия).
--
-- Миграция идемпотентна: повторное применение ничего не ломает.
--
-- Проверка после применения:
--   SELECT pg_get_constraintdef(oid) FROM pg_constraint
--    WHERE conname = 'tbl_song_cleanup_entries_kind_chk';
--   -- ожидается список с 'DB_ROWS'
--
-- Применять вручную на КАЖДОЙ БД отдельно (Решение 4 в
-- knowledge/system/infra/sql-migrations.md). На SERVER-БД миграция не обязательна:
-- вид записи `DB_ROWS` использует только разовая уборка, которая выполняется в
-- LOCAL-БД; при этом применение на SERVER-БД не мешает и готовит базу к обслуживанию.

ALTER TABLE public.tbl_song_cleanup_entries
    DROP CONSTRAINT IF EXISTS tbl_song_cleanup_entries_kind_chk;
ALTER TABLE public.tbl_song_cleanup_entries
    ADD CONSTRAINT tbl_song_cleanup_entries_kind_chk
    CHECK (kind IN ('FILE', 'STORAGE_OBJECT', 'ALBUM', 'PICTURE', 'ALBUM_FOLDER', 'DB_ROWS'));
