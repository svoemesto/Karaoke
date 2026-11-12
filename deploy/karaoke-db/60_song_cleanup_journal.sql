-- KARAOKE-429 (спека 739 «удаление песни с каскадной уборкой»), FR-034, FR-035, SC-003.
--
-- Зачем: FR-034 требует, чтобы журнал уборки вёлся В БАЗЕ ДАННЫХ и сохранял
-- состояние прогона (запланирован → утверждён → выполняется → завершён/частично/
-- не выполнен). TSV-журнал (как в specs/544) не переживает переустановку
-- контейнера и не даёт выборку «что осталось» по прогону.
--
-- Таблицы НЕ участвуют в LOCAL↔SERVER sync: журнал — локальная хроника
-- администратора; его синхронизация переносила бы пути к файлам admin-машины
-- на прод (осознанное исключение из Principle III, зафиксировано в
-- specs/739-song-delete-cascade/plan.md § «Constitution Check»).
--
-- Применять вручную на КАЖДОЙ БД отдельно (Решение 4 в
-- knowledge/system/infra/sql-migrations.md). Порядок: 58 → 59 → 60.
-- Идемпотентно: повторное применение ничего не ломает.

-- 1) Прогон уборки.
CREATE TABLE IF NOT EXISTS public.tbl_song_cleanup_runs (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    song_id bigint NOT NULL DEFAULT 0,
    song_name varchar(255) NOT NULL DEFAULT '',
    author varchar(255) NOT NULL DEFAULT '',
    album varchar(255) NOT NULL DEFAULT '',
    root_folder varchar(500) NOT NULL DEFAULT '',
    run_kind varchar(32) NOT NULL DEFAULT 'SONG_DELETE',
    state varchar(16) NOT NULL DEFAULT 'PLANNED',
    initiator varchar(64) NOT NULL DEFAULT '',
    started_at timestamp without time zone,
    finished_at timestamp without time zone,
    files_total int NOT NULL DEFAULT 0,
    files_deleted int NOT NULL DEFAULT 0,
    files_kept int NOT NULL DEFAULT 0,
    files_failed int NOT NULL DEFAULT 0,
    objects_total int NOT NULL DEFAULT 0,
    objects_deleted int NOT NULL DEFAULT 0,
    objects_kept int NOT NULL DEFAULT 0,
    objects_failed int NOT NULL DEFAULT 0,
    bytes_deleted bigint NOT NULL DEFAULT 0,
    error text,
    CONSTRAINT tbl_song_cleanup_runs_state_chk
        CHECK (state IN ('PLANNED', 'APPROVED', 'RUNNING', 'DONE', 'PARTIAL', 'FAILED')),
    CONSTRAINT tbl_song_cleanup_runs_kind_chk
        CHECK (run_kind IN ('SONG_DELETE', 'ONE_TIME_CLEANUP')),
    CONSTRAINT tbl_song_cleanup_runs_counters_chk
        CHECK (files_deleted >= 0 AND objects_deleted >= 0 AND bytes_deleted >= 0),
    -- SC-003: «ложного успеха» не бывает — DONE только когда отказов нет.
    CONSTRAINT tbl_song_cleanup_runs_done_chk
        CHECK (state <> 'DONE' OR (files_failed = 0 AND objects_failed = 0)),
    CONSTRAINT tbl_song_cleanup_runs_initiator_chk
        CHECK (length(trim(initiator)) > 0)
);

CREATE INDEX IF NOT EXISTS idx_tbl_song_cleanup_runs_song_id
    ON public.tbl_song_cleanup_runs (song_id);

CREATE INDEX IF NOT EXISTS idx_tbl_song_cleanup_runs_state
    ON public.tbl_song_cleanup_runs (state);

-- 2) Запись журнала: одно действие (путь или ключ, размер, результат, причина,
--    номер попытки, дата, инициатор, идентификатор прогона).
CREATE TABLE IF NOT EXISTS public.tbl_song_cleanup_entries (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    run_id bigint NOT NULL,
    kind varchar(32) NOT NULL,
    location varchar(32) NOT NULL,
    path_or_key text NOT NULL,
    size_bytes bigint NOT NULL DEFAULT 0,
    result varchar(16) NOT NULL,
    reason text,
    attempt int NOT NULL DEFAULT 1,
    created_at timestamp without time zone NOT NULL DEFAULT now(),
    initiator varchar(64) NOT NULL DEFAULT '',
    CONSTRAINT tbl_song_cleanup_entries_kind_chk
        CHECK (kind IN ('FILE', 'STORAGE_OBJECT', 'ALBUM', 'PICTURE', 'ALBUM_FOLDER')),
    CONSTRAINT tbl_song_cleanup_entries_location_chk
        CHECK (location IN ('LOCAL_FILESYSTEM', 'LOCAL_STORAGE', 'REMOTE_STORAGE', 'DATABASE')),
    CONSTRAINT tbl_song_cleanup_entries_result_chk
        CHECK (result IN ('DELETED', 'KEPT', 'FAILED', 'SKIPPED')),
    -- «оставлено» без причины не допускается (FR-016), «не выполнено» — тоже.
    CONSTRAINT tbl_song_cleanup_entries_kept_reason_chk
        CHECK (result <> 'KEPT' OR length(trim(coalesce(reason, ''))) > 0),
    CONSTRAINT tbl_song_cleanup_entries_failed_reason_chk
        CHECK (result <> 'FAILED' OR length(trim(coalesce(reason, ''))) > 0),
    CONSTRAINT tbl_song_cleanup_entries_attempt_chk
        CHECK (attempt BETWEEN 1 AND 5),
    CONSTRAINT tbl_song_cleanup_entries_path_chk
        CHECK (length(trim(path_or_key)) > 0),
    CONSTRAINT tbl_song_cleanup_entries_run_fk
        FOREIGN KEY (run_id) REFERENCES public.tbl_song_cleanup_runs(id)
);

CREATE INDEX IF NOT EXISTS idx_tbl_song_cleanup_entries_run_id
    ON public.tbl_song_cleanup_entries (run_id);

CREATE INDEX IF NOT EXISTS idx_tbl_song_cleanup_entries_run_result
    ON public.tbl_song_cleanup_entries (run_id, result);

COMMENT ON TABLE public.tbl_song_cleanup_runs IS
    'Прогон уборки: состояние, счётчики, инициатор. Журнал уборки песни (FR-034).';
COMMENT ON TABLE public.tbl_song_cleanup_entries IS
    'Запись журнала уборки: путь/ключ, размер, результат, причина, попытка, дата, инициатор, прогон (FR-035).';
