# Data Model: Удаление песни с каскадной уборкой

**Фича**: `739-song-delete-cascade` | **Дата**: 2026-11-12 | **Спека**: [`spec.md`](spec.md)
**План**: [`plan.md`](plan.md) | **Research**: [`research.md`](research.md)

> Phase 1 (`/speckit.plan`). Сущности, поля, связи, правила валидации и переходы
> состояний. Схема БД меняется тремя миграциями; каждая — отдельный файл в
> `deploy/karaoke-db/`.

---

## 1. Сущности предметной области

### 1.1 Песня (Song) — существующая, изменяется только поведением удаления

Источник истины — `tbl_songs`. В рамках фичи песня — «якорь» уборки.

| Поле | Тип | Смысл |
|---|---|---|
| `id` | bigint | PK, идентичность песни |
| `file_name` | varchar | **текущее** имя файла песни; ключ в хранилищах и префикс файлов на диске |
| `song_author`, `song_year`, `song_album` | varchar/int | префикс хранилища `$автор/$год - $альбом/` |
| `root_folder` | varchar | папка альбома на диске; общая для всех песен альбома |
| `album_id` | bigint | FK на `tbl_albums(id)` `ON DELETE SET NULL` |
| `audio_parent_id` | bigint | ссылка на родителя (без FK); при удалении родителя обнуляется у потомков |

Ключевые производные величины (уже есть в коде):

- `storageBucketName = "karaoke"`;
- `storageFileName = "$author/$year - $album/$fileName"`;
- `rootFolder` — папка альбома `/sm-karaoke/{work,done1,done2}/<автор>/<год> - <альбом>/`.

### 1.2 Альбом (Album) — существующая, расширяется каскадным удалением

| Поле | Тип | Смысл |
|---|---|---|
| `id` | bigint | PK |
| `author_id` | int | FK на `tbl_authors(id)` `ON DELETE RESTRICT` |
| `year`, `name`, `album_type` | — | метаданные; `name` участвует в имени картинки альбома |

Условие каскада: альбом удаляется, **если после удаления песни в его папке не
осталось файлов живых песен** (FR-018, FR-020, FR-021).

### 1.3 Артефакт песни (Artifact) — новая понятийная сущность

Не таблица; результат вычисления «что принадлежит песне».

| Поле | Тип | Смысл |
|---|---|---|
| `kind` | enum | `FILE`, `STORAGE_OBJECT`, `ALBUM`, `PICTURE`, `ALBUM_FOLDER` |
| `location` | enum | `LOCAL_FILESYSTEM`, `LOCAL_STORAGE`, `REMOTE_STORAGE`, `DATABASE` |
| `pathOrKey` | string | абсолютный путь (диск) или ключ MinIO (хранилище) |
| `sizeBytes` | long | размер; для хранилища — из листинга |
| `ownership` | enum | `OWN` (принадлежит песне), `PROTECTED` (общий: обложка альбома/автора), `UNKNOWN` (не опознан) |
| `decision` | enum | `DELETE`, `KEEP`, `PROTECT` |

Правила вычисления `ownership` — см. R-003 в [`research.md`](research.md):
- `OWN`: имя = `file_name + продолжение` (пусто / `.ext` / `-суффикс` / ` [метка]`),
  сравнение **точное** (регистр, `ё`/`е`, кавычки, `1`/`01` значимы);
- `PROTECTED`: обложка альбома (`*.album.png`, `*.preview.album.png`) и обложка
  автора (`*.author.png`, `*.preview.author.png`) — общие, удаляются с альбомом,
  не с песнёй (FR-014);
- `UNKNOWN`: всё, что не опознано ни одним правилом — остаётся и попадает в
  журнал как «оставлено» (FR-016).

### 1.4 Прогон уборки (CleanupRun) — новая таблица `tbl_song_cleanup_runs`

| Колонка | Тип | Смысл |
|---|---|---|
| `id` | bigint PK | идентификатор прогона (совпадает с `run_id` в журнале) |
| `song_id` | bigint | песня (FK не ставим: строка песни удаляется раньше, журнал переживает удаление) |
| `song_name` | varchar | снимок имени на момент запуска — для читаемости журнала |
| `author`, `album`, `root_folder` | varchar | снимок контекста: без него журнал нечитаем после удаления песни |
| `run_kind` | varchar | `SONG_DELETE` \| `ONE_TIME_CLEANUP` (FR-034, FR-041) |
| `state` | varchar | `PLANNED` → `APPROVED` → `RUNNING` → `DONE` \| `PARTIAL` \| `FAILED` |
| `initiator` | varchar | кто инициировал (админ / владелец) |
| `started_at`, `finished_at` | timestamp | границы прогона |
| `files_total`, `files_deleted`, `files_kept`, `files_failed` | int | счётчики по файлам |
| `objects_total`, `objects_deleted`, `objects_kept`, `objects_failed` | int | счётчики по объектам хранилищ |
| `bytes_deleted` | bigint | освобождено байт |
| `error` | text | текст последней ошибки |

**Правила валидации.**
- `state` ∈ перечисленном наборе; переходы только вперёд по цепочке
  `PLANNED → APPROVED → RUNNING → {DONE, PARTIAL, FAILED}` (FR-034).
- `DONE` допустим только при `files_failed = 0 AND objects_failed = 0` — иначе
  `PARTIAL` (SC-003: «ложного успеха» не бывает).
- `PARTIAL`/`FAILED` обязаны иметь хотя бы одну запись в
  `tbl_song_cleanup_entries` с `result ∈ {KEPT, FAILED, SKIPPED}` и заполненным
  `reason` (US3-сценарий 2, FR-033).
- `initiator` обязателен (FR-035).

### 1.5 Запись журнала (CleanupEntry) — новая таблица `tbl_song_cleanup_entries`

| Колонка | Тип | Смысл |
|---|---|---|
| `id` | bigint PK | |
| `run_id` | bigint | FK на `tbl_song_cleanup_runs(id)` |
| `kind` | varchar | `FILE` \| `STORAGE_OBJECT` \| `ALBUM` \| `PICTURE` \| `ALBUM_FOLDER` |
| `location` | varchar | `LOCAL_FILESYSTEM` \| `LOCAL_STORAGE` \| `REMOTE_STORAGE` \| `DATABASE` |
| `path_or_key` | text | путь или ключ (FR-035) |
| `size_bytes` | bigint | размер (FR-035) |
| `result` | varchar | `DELETED` \| `KEPT` \| `FAILED` \| `SKIPPED` (FR-035) |
| `reason` | text | причина: `not_owned`, `album_has_live_songs`, `storage_unavailable`, `file_busy`, `not_recognized` |
| `attempt` | int | номер попытки (FR-032: до 5) |
| `created_at` | timestamp | дата (FR-035) |
| `initiator` | varchar | инициатор (FR-035) |

**Правила валидации.**
- `result = DELETED` требует непустого `path_or_key` и `size_bytes >= 0`.
- `result = KEPT` требует непустого `reason` — «оставлено» без причины не
  допускается (FR-016).
- `result = FAILED` требует непустого `reason` и `attempt`.
- Одна запись на (путь/ключ, попытка): повторная попытка — новая строка, а не
  перезапись (журнал показывает все попытки, US3-сценарий 4).

### 1.6 История имён песни (SongNameHistory) — новая таблица `tbl_song_name_history`

| Колонка | Тип | Смысл |
|---|---|---|
| `id` | bigint PK | |
| `song_id` | bigint | песня, чьё имя менялось |
| `file_name` | varchar | **прежнее** имя файла песни |
| `changed_at` | timestamp | момент переименования |

**Назначение.** FR-013 требует удалять объекты «по ранее использовавшимся вариантам
имени». Без этой таблицы история имён не сохраняется, и устаревшие копии
(745 ключей / 4,38 ГБ) — только следствие уже случившегося дефекта
`renameAllArtifacts` (KARAOKE-428).

**Правила валидации.**
- уникальность пары `(song_id, file_name)` — повторная запись того же имени не
  создаётся;
- запись делается **до** переименования (в момент, когда старое имя ещё актуально),
  в той же транзакции, что и запись нового имени в `tbl_songs.file_name`.

### 1.7 Кандидат разовой уборки (CleanupCandidate) — понятийная сущность

Не таблица; результат формирования перечня (FR-039).

| Поле | Тип | Смысл |
|---|---|---|
| `pathOrKey` | string | путь или ключ |
| `kind` | enum | как в `CleanupEntry` |
| `sizeBytes` | long | размер — в перечне обязателен (FR-039) |
| `reason` | enum | `ORPHAN` (сирота), `STALE_COPY` (устаревшая копия), `UNKNOWN` (не опознан) |
| `setFingerprint` | string | отпечаток набора (sha256) — FR-040 |

**Правила валидации.**
- кандидат не включается, если его имя после нормализации совпадает с именем
  живой песни (FR-042);
- `setFingerprint` пересчитывается по составу набора; при изменении состава
  `apply` отказывается выполнять уборку (FR-040);
- каждый выполненный проход пишется в журнал (FR-043).

---

## 2. Связи

```
tbl_songs ──1:N──► tbl_song_cleanup_runs (song_id, без FK — журнал переживает удаление)
tbl_song_cleanup_runs ──1:N──► tbl_song_cleanup_entries (run_id, FK)
tbl_songs ──1:N──► tbl_song_name_history (song_id, без FK — переживает удаление)

tbl_songs ──FK CASCADE──► tbl_song_authors
tbl_songs ──FK SET NULL (миграция 58)──► tbl_news.song_id
tbl_songs ──FK CASCADE (миграция 58)──► tbl_song_news_announced.song_id
tbl_songs ──(soft) SET NULL──► tbl_songs.audio_parent_id (потомки)
tbl_songs ──FK SET NULL──► tbl_albums (album_id) — при удалении альбома
tbl_albums ──(soft)──► tbl_pictures (по picture_name, не FK)
```

Диаграмма уборки (порядок по FR-031):

```
[подтверждение админа]
        │
        ▼
[фаза A: LOCAL-БД, транзакция]
   tbl_songs DELETE
   tbl_song_assignments / drafts / playlist_items / share_links DELETE
   tbl_listening_history / search_results / search_async DELETE
   tbl_events DELETE (только song_id <> 0)
   tbl_songs.audio_parent_id → NULL (потомки)
   tbl_news.song_id → NULL (схема)
   tbl_song_authors → CASCADE (схема)
        │
        ▼
[фаза B: SERVER-БД, транзакция]
   tbl_songs DELETE
   tbl_songs_sync DELETE
        │
        ▼
[фаза C: фоновая задача KaraokeProcess]
   1. диск: файлы песни (корень + hdemucs_mmi, done_files, done_chords, sheetsage, symlink_*)
            → битые симлинки → неопознанные остаются («оставлено»)
   2. хранилища: LOCAL (MinIO admin) → REMOTE (MinIO прод)
            по ключу $автор/$год - $альбом/<имя> (+ варианты старых имён)
   3. альбом: tbl_albums DELETE, tbl_pictures DELETE, обложка+превью в обоих хранилищах
   4. папка альбома: удалить ТОЛЬКО если в ней нет файлов живых песен
```

---

## 3. Переходы состояний прогона уборки

```
        (создаётся)         (утверждён)
            │                    │
            ▼                    ▼
        PLANNED ────────────► APPROVED
            │                    │
            │  (фоновая задача поставлена)
            ▼                    ▼
        RUNNING ◄─────────────────┘
         │  │  │
         │  │  └──► RUNNING (повтор шага, attempt+1; до 5 попыток, паузы 2s…60s)
         ▼  ▼
   DONE / PARTIAL / FAILED
```

| Из | В | Условие | Что означает для админа |
|---|---|---|---|
| — | `PLANNED` | прогон создан (предпросмотр) | «запланировано» |
| `PLANNED` | `APPROVED` | админ подтвердил удаление | «утверждено» |
| `APPROVED` | `RUNNING` | задание взято воркером | «выполняется» |
| `RUNNING` | `DONE` | все шаги выполнены, `*_failed = 0` | «завершено» |
| `RUNNING` | `PARTIAL` | часть шагов не выполнена, остаток записан | «не завершено», есть добор |
| `RUNNING` | `FAILED` | прогон прерван, остаток записан | «не выполнено», есть добор |
| `PARTIAL`/`FAILED` | `RUNNING` | ручной добор (US3-сценарий 4) | новый прогон либо продолжение того же |

Идемпотентность: повторный запуск добора продолжает **тот же** прогон (тот же
`run_id`), добавляя новые записи журнала; итоговый статус прогона становится
`DONE`, когда остаток пуст (US3-сценарий 4).

---

## 4. Миграции

| Файл | Что делает | Идемпотентность |
|---|---|---|
| `deploy/karaoke-db/58_news_song_id_on_delete_set_null.sql` | пересоздаёт FK `tbl_news.song_id` → `ON DELETE SET NULL`; пересоздаёт FK `tbl_song_news_announced.song_id` → `ON DELETE CASCADE` | да (`DROP CONSTRAINT IF EXISTS` + `ADD CONSTRAINT`) |
| `deploy/karaoke-db/59_song_name_history.sql` | создаёт `tbl_song_name_history` + индекс `(song_id)` | да (`CREATE TABLE IF NOT EXISTS`) |
| `deploy/karaoke-db/60_song_cleanup_journal.sql` | создаёт `tbl_song_cleanup_runs` + `tbl_song_cleanup_entries` + индексы (`run_id`, `(run_id, result)`) | да |

Порядок применения: 58 → 59 → 60. Применяются вручную на LOCAL, затем на PROD
(runbook `knowledge/system/infra/sql-migrations.md`, решение 4).

Проверка после применения:

```sql
-- 58
SELECT conname, confdeltype FROM pg_constraint
 WHERE conrelid = 'public.tbl_news'::regclass AND contype = 'f';
-- ожидается 'n' (SET NULL) — в PostgreSQL SET NULL = 'n', CASCADE = 'c', NO ACTION = 'a'

SELECT conname, confdeltype FROM pg_constraint
 WHERE conrelid = 'public.tbl_song_news_announced'::regclass AND contype = 'f';
-- ожидается 'c' (CASCADE)

-- 59/60
\d tbl_song_name_history
\d tbl_song_cleanup_runs
\d tbl_song_cleanup_entries
```

---

## 5. Нормализация имён (два разных правила)

### 5.1 Строгое правило владения (удаление песни)

```
принадлежит(песня, имяФайла) ==
    имяФайла.startsWith(песня.fileName) &&
    продолжениеАртефакта(имяФайла.removePrefix(песня.fileName))

продолжениеАртефакта(rest) ==
    rest.isEmpty() ||
    rest.startsWith(".") ||          // .mp3, .flac, .accompaniment
    rest.startsWith("-") ||          // -accompaniment, -vocals
    rest.startsWith(" [")            // " [karaoke]", " [key].json"
```

Сравнение — **точное** (без `equalsIgnoreCase`): регистр значим, `ё` ≠ `е`,
`«` ≠ `"`, `01` ≠ `1`. Реализация переиспользует
`SongArtifactRenamer.isArtifactContinuation` (`SongArtifactRenamer.kt:50`).

Проверяемые примеры (все — измеренные на реальных данных):

| Имя файла | Имя песни | Вердикт | Почему |
|---|---|---|---|
| `Песня.mp3` | `Песня` | свой | пустой остаток |
| `Песня.accompaniment.flac` | `Песня` | свой | остаток `.accompaniment.flac` |
| `Песня [karaoke].png` | `Песня` | свой | остаток ` [karaoke].png` |
| `Песня, длинная версия.mp3` | `Песня` | **чужой** | остаток `, длинная версия.mp3` — не продолжение |
| `песня.mp3` | `Песня` | **чужой** | регистр |
| `Песня.mp3` | `Песня` (с `ё` в имени) | **чужой** | `ё` ≠ `е` |
| `Песня 01.mp3` | `Песня 1` | **чужой** | `01` ≠ `1` |
| `Песня.mp3` | `Песня.mp3` (с кавычками) | **чужой** | тип кавычки |

### 5.2 Нормализованное правило (разовая уборка — исключение из кандидатов)

```
совпадаетПослеНормализации(имяФайла, имяЖивойПесни) ==
    нормализовать(имяФайла) == нормализовать(имяЖивойПесни)

нормализовать(s) =
    s.lowercase()
     .replace('ё', 'е')
     .replace('«', '"').replace('»', '"')
     .replace('–', '-').replace('—', '-')
     .replaceAll("\\s+", " ")
     .trim()
     .replace(Regex("(?<![\\p{L}\\p{N}])0+(?=\\d)"), "")   // 01 -> 1
```

Если совпадение есть — объект **не** попадает в кандидаты на удаление (FR-042).
Это ровно случай измеренных 3 520 near-match файлов: живые песни с отличающимся
написанием (регистр, `ё`/`е`, кавычки).

---

## 6. Ограничения целостности новых таблиц

| Таблица | Ограничение |
|---|---|
| `tbl_song_cleanup_runs` | `CHECK (state IN ('PLANNED','APPROVED','RUNNING','DONE','PARTIAL','FAILED'))`; `CHECK (run_kind IN ('SONG_DELETE','ONE_TIME_CLEANUP'))`; `CHECK (files_deleted >= 0 AND objects_deleted >= 0 AND bytes_deleted >= 0)`; `CHECK (state <> 'DONE' OR (files_failed = 0 AND objects_failed = 0))` |
| `tbl_song_cleanup_entries` | `CHECK (kind IN ('FILE','STORAGE_OBJECT','ALBUM','PICTURE','ALBUM_FOLDER'))`; `CHECK (result IN ('DELETED','KEPT','FAILED','SKIPPED'))`; `CHECK (result <> 'KEPT' OR length(trim(reason)) > 0)`; `CHECK (result <> 'FAILED' OR length(trim(reason)) > 0)`; `CHECK (attempt BETWEEN 1 AND 5)` |
| `tbl_song_name_history` | `UNIQUE (song_id, file_name)`; индекс по `song_id` |

Ограничения «CHECK» выражены и в коде (загрузчик/валидатор DTO), и в БД — БД как
последний рубеж, код как источник внятного сообщения.
