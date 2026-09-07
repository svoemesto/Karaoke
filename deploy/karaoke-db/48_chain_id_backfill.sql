-- ============================================================================
-- Migration 48: Backfill process_chain_id для legacy tail-процессов
-- ============================================================================
--
-- Контекст (iter #4 spec 314):
-- - Миграция 47 добавила process_chain_id BIGINT NULL в tbl_processes.
-- - Миграция 47 НЕ делает backfill существующих строк.
-- - KaraokeProcess.separate() (KaraokeProcess.kt:~1945) создаёт child-процесс
--   с тем же thread_id, что у head, и command='tail'.
-- - Старые процессы были созданы ДО миграции 47, и в них process_chain_id
--   остался NULL — цепочки "сломаны" (урок #10 iter #2 / R-005 spec 314).
--
-- Эвристика (владелец выбрал B в ask_user_question 2026-09-07):
-- - В реальной БД thread_id=1 имеет 9 984 tail + 6 897 head за 10 минут.
-- - thread_id=2 имеет 3 192 tail + 455 head за миллисекунду (batch upload).
-- - thread_id=0 имеет 0 tail + 2 head (одиночные).
-- - process_start = NULL у большинства tails (нет данных о времени).
--
-- Эвристика:
-- Для каждого tail (process_command='tail', process_chain_id IS NULL):
--   Назначить head = id процесса с тем же thread_id, command≠'tail',
--   process_deleted_at IS NULL, с НАИБОЛЬШИМ id < tail.id.
--   Т.е. ближайший предыдущий head в том же thread_id.
--
-- ⚠️ WARNING: Эвристика НЕ ТОЧНАЯ. Возможны ошибки:
-- - Если в одном thread_id несколько head разных типов (DEMUCS2, FF_MP3,
--   UPLOAD) идут подряд — tail получит ближайший предыдущий head, что
--   может быть не его реальный parent.
-- - process_start = NULL у большинства tails — сортировка только по id.
-- - Владелец должен валидировать результат на выборке перед применением на prod.
--
-- Применять:
-- 1. На local: docker exec -i karaoke-db psql -U postgres -d karaoke < deploy/karaoke-db/48_chain_id_backfill.sql
-- 2. SELECT COUNT(*) FROM tbl_processes WHERE process_chain_id IS NOT NULL;
--    Должно быть ~13 176 (9 984 + 3 192).
-- 3. Визуально проверить в /admin/processes: развернуть head с DEMUCS2 →
--    должны появиться tail'ы DEMUCS2.
-- 4. Если всё ОК — применить на prod.
-- ============================================================================

BEGIN;

-- Шаг 1: Простая эвристика — для каждого tail находим ближайший предыдущий
-- head (тот же thread_id, command≠'tail', process_deleted_at IS NULL).
WITH tail_heads AS (
    SELECT
        t.id AS tail_id,
        (
            SELECT h.id
            FROM tbl_processes h
            WHERE h.thread_id = t.thread_id
              AND h.process_command <> 'tail'
              AND h.process_deleted_at IS NULL
              AND h.id < t.id
            ORDER BY h.id DESC
            LIMIT 1
        ) AS head_id
    FROM tbl_processes t
    WHERE t.process_deleted_at IS NULL
      AND t.process_command = 'tail'
      AND t.process_chain_id IS NULL
)
UPDATE tbl_processes t
SET process_chain_id = th.head_id
FROM tail_heads th
WHERE t.id = th.tail_id
  AND th.head_id IS NOT NULL;

-- Шаг 2: Verification queries (для владельца — выполнить вручную после миграции):
-- SELECT COUNT(*) FROM tbl_processes WHERE process_chain_id IS NOT NULL;
--   Ожидаемо: ~13 176 (9 984 в thread_id=1 + 3 192 в thread_id=2).
-- SELECT thread_id, COUNT(*) AS total, COUNT(*) FILTER (WHERE process_chain_id IS NOT NULL) AS with_chain
--   FROM tbl_processes WHERE process_deleted_at IS NULL GROUP BY thread_id ORDER BY thread_id;

COMMIT;

-- DOWN (откат): устанавливает process_chain_id = NULL для всех строк, изменённых этой миграцией.
-- UPDATE tbl_processes SET process_chain_id = NULL WHERE process_chain_id IS NOT NULL;

