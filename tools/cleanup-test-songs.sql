-- tools/cleanup-test-songs.sql
--
-- Откат тестовых правок song_name_censored после SC-001 (10 ручных правок
-- на LOCAL-БД для верификации bug #52, см. specs/302-fix-censored-name-loss
-- quickstart.md).
--
-- Перед запуском замените <original_value_N> и <ID_N> на реальные значения
-- из .report-tracker-52-cleanup-originals.txt (NFR-006 — сохранён ДО
-- выполнения теста).
--
-- Запуск:
--   PGPASSWORD=... psql -h localhost -U karaoke -d karaoke -f tools/cleanup-test-songs.sql
--
-- Безопасно для повторного запуска (UPDATE idempotent).

BEGIN;

-- Шаблон (заполните перед запуском):
-- UPDATE tbl_songs SET song_name_censored = '<original_value_1>' WHERE id = <ID_1>;
-- UPDATE tbl_songs SET song_name_censored = '<original_value_2>' WHERE id = <ID_2>;
-- ... повторить для всех 10 песен

COMMIT;
