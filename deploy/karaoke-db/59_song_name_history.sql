-- KARAOKE-429 (спека 739 «удаление песни с каскадной уборкой»), FR-013.
--
-- Зачем: в БД хранится только текущее file_name песни. FR-013 требует удалять
-- объекты песни в хранилищах «как по текущему имени, так и по ранее использовавшимся
-- вариантам имени (оставшиеся после переименований)». Без истории имён это
-- неисполнимо: сканировать весь префикс альбома небезопасно — под нём лежат
-- артефакты соседних песен (см. R-004 в specs/739-song-delete-cascade/research.md).
--
-- Наполнение: Song.renameAllArtifacts / renameCascadeExtraArtifacts пишут прежнее
-- имя ПЕРЕД переименованием (таблица переживает удаление песни — FK на tbl_songs
-- намеренно не ставим: строка песни удаляется раньше журнала).
--
-- Применять вручную на КАЖДОЙ БД отдельно (Решение 4 в
-- knowledge/system/infra/sql-migrations.md). Порядок: 58 → 59 → 60.
-- Идемпотентно: повторное применение ничего не ломает.

CREATE TABLE IF NOT EXISTS public.tbl_song_name_history (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    song_id bigint NOT NULL,
    file_name varchar(255) NOT NULL,
    changed_at timestamp without time zone NOT NULL DEFAULT now(),
    CONSTRAINT tbl_song_name_history_song_file_uniq UNIQUE (song_id, file_name)
);

CREATE INDEX IF NOT EXISTS idx_tbl_song_name_history_song_id
    ON public.tbl_song_name_history (song_id);

COMMENT ON TABLE public.tbl_song_name_history IS
    'История прежних имён песни: прежнее имя файла, записанное перед переименованием. Используется при удалении песни для очистки объектов хранилищ под старыми именами (FR-013).';
