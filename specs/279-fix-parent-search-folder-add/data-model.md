# Data Model: 279 — Восстановить поиск родителя при добавлении файлов из папки

**Дата**: 2026-08-31
**Спека**: [spec.md](spec.md)
**План**: [plan.md](plan.md)
**Research**: [research.md](research.md)

Эта фича не вводит новых сущностей и не изменяет схему БД. Data Model фиксирует **текущие** сущности и поля, участвующие в поиске «родителя» при импорте из папки, чтобы фикс был минимально-инвазивным.

## Сущность: `Song` (песня)

Хранится в таблице `tbl_songs` (PostgreSQL). Поля, непосредственно участвующие в этой спеке:

| Поле SQL | Тип | Kotlin (Song) | Назначение | Затрагивается в спеке? |
|----------|-----|---------------|------------|------------------------|
| `id` | integer | `id: Long` | Первичный ключ. | Используется в `findDuplicateOriginal` для `WHERE id <> ?` (исключаем саму новую песню). |
| `song_name` | varchar | `songName: String` через `fields[SongField.NAME]` | Название песни. | Используется в `normalizeSongNameForSearch(song_name)` для матчинга по нормализованному имени (без скобок, регистронезависимо, «е»/«ё» эквивалентны). |
| `song_author` | varchar | `author: String` через `fields[SongField.AUTHOR]` | Имя автора. | Используется в `findDuplicateOriginal` для `WHERE song_author ILIKE ?` (тот же автор, регистронезависимо). **Ключевое поле фикса — замена `LOWER(...) = LOWER(...)` на `ILIKE`**. |
| `source_text` | text | `sourceText: String` | Текст песни (исходный). | Используется в `WHERE TRIM(source_text) <> ''` (только песни с заполненным текстом являются кандидатами в родители) и копируется в `applyDuplicateOriginal`. |
| `source_markers` | text | `sourceMarkers: String` | Маркеры песни. | Копируется в `applyDuplicateOriginal`. |
| `result_text` | text | `resultText: String` | Результирующий текст (после разметки). | Копируется в `applyDuplicateOriginal`. |
| `formatted_text_song` | text | `formattedTextSong: String` | Форматированный текст песни (для караоке). | Копируется в `applyDuplicateOriginal`. |
| `formatted_text_tabs` | text | `formattedTextTabs: String` | Форматированные табы (аккорды). | Копируется в `applyDuplicateOriginal`. |
| `formatted_text_chords` | text | `formattedTextChords: String` | Форматированные аккорды. | Копируется в `applyDuplicateOriginal`. |
| `root_id` | integer | `rootId: Long` (var) | ID «родителя» в «семье» песен. `0` = песня сама является корнем своей семьи. | **Ключевое поле фикса**: устанавливается через `songToSave.rootId = original.id` в `applyDuplicateOriginal`. Проверяется через `getDiff` и записывается через `Song.saveToDb()`. |
| `id_status` | integer | `idStatus: Long` (var) | Статус пайплайна песни (`0`=NONE, `1`=TEXT_CREATE, `5`=MARKERS_CHECK, `6`=READY). | Устанавливается в `1` (TEXT_CREATE) в `applyDuplicateOriginal`. |
| `song_name_censored` | varchar | `songNameCensored: String` (var) | Цензурированное название (из спеки 277). | Не затрагивается напрямую. Baseline-автозаполнение в `saveToDb` (если поле пустое) срабатывает само — никаких изменений не требуется. |

## Поля, не затрагиваемые, но важающие для понимания

- `song_tone`, `song_bpm` — тональность и BPM. Заполняются фоновым процессом `KEY_BPM_FROM_FILE`. Защита от перезаписи параллельным `saveToDb()` в `applyDuplicateOriginal` — через reload-from-db-before-save (спека 278).
- `audio_song`, `audio_music`, `audio_vocals`, `audio_drums`, `audio_bass` — URL'ы стемов от `DEMUCS2`. Та же защита, что и для `song_tone`/`song_bpm`.
- `audio_parent_id`, `audio_similarity_percent`, `audio_delta_ms` — связь с аудио-родителем (по схожести звучания). Заполняются в `findAudioParentByWaveform` (независимая ветка, FR-009 спеки).
- `file_name`, `root_folder` — уникальный идентификатор файла. Используется в `Song.createFromPath` для дедупликации (`loadListFromDb({file_name, root_folder})` → пропуск уже импортированных). Не затрагивается фиксом.

## Отношения

```
Song (id) ──┬── root_id ──> Song (id)           // self-reference, parent in "family"
            └── audio_parent_id ──> Song (id)   // self-reference, audio parent (by waveform)
```

«Семья» песен (`findFamilySongIds`, `Utils.kt:4683`) — все песни, у которых `id` или `root_id` совпадает с `id` или `root_id` текущей песни. Используется в UI модалки «Похожие версии песни» (`FamilySongsModal.vue`).

## Поведение полей при фиксе

### До фикса (текущее сломанное поведение)

1. `Song.createFromPath` создаёт новую песню, `song.id` присваивается БД.
2. `findDuplicateOriginal(newSong, ...)` выполняет SQL:
   ```sql
   SELECT id, song_name FROM tbl_songs
   WHERE id <> ? AND LOWER(song_author) = LOWER(?) AND TRIM(source_text) <> ''
   ORDER BY id ASC
   ```
3. Из-за локали `C`/`POSIX` в PostgreSQL `LOWER('Король и Шут')` НЕ понижает регистр кириллицы → сравнение ложно → `findId` возвращает `null` → `findDuplicateOriginal` возвращает `null` → `applyDuplicateOriginal` НЕ вызывается → `root_id = 0`, текст не копируется.

### После фикса (ожидаемое поведение)

1. Те же шаги 1-2.
2. SQL заменён на:
   ```sql
   SELECT id, song_name FROM tbl_songs
   WHERE id <> ? AND song_author ILIKE ? AND TRIM(source_text) <> ''
   ORDER BY id ASC
   ```
   Параметр передаётся как `newSong.author` (без изменений регистра). `ILIKE` корректно сравнивает «Король и Шут» с «Король и Шут» / «Король и шут» / «КОРОЛЬ И ШУТ» — все варианты находят базовую песню.
3. `findId` возвращает `id` родителя → `findDuplicateOriginal` возвращает `Song`-объект родителя → `applyDuplicateOriginal` вызывается → `root_id`, `source_text`, `source_markers`, `result_text`, `formatted_text_*` копируются, `id_status = 1`, `saveToDb()` записывает UPDATE.

## Валидация (FR из спеки → SQL/код-инвариант)

| FR | Инвариант |
|----|-----------|
| FR-001 | После импорта `root_id` указывает на `id` базовой песни того же автора. |
| FR-002 | При нескольких кандидатах выбирается минимальный `id` (через `ORDER BY id ASC`). |
| FR-003 | Поля `source_text`, `source_markers`, `result_text`, `formatted_text_*` скопированы от родителя; `id_status = 1`. |
| FR-004 | Поиск только среди песен того же автора (`song_author ILIKE ?`). |
| FR-005 | Сравнение имени через `normalizeSongNameForSearch` (удаление скобок, lowercase, «ё»→«е», удаление пунктуации, схлопывание пробелов). |
| FR-006 | Сравнение автора регистронезависимо через `ILIKE`. |
| FR-007 | Race condition защита (reload из БД перед `saveToDb`) сохранена. |
| FR-008 | Поведение «родитель не найден» не изменяется (`root_id = 0`, дальше интернет-поиск текста). |
| FR-009 | Аудио-родитель (`findAudioParentByWaveform`) работает независимо. |
| FR-010 | `customFunction` использует ту же `applyDuplicateOriginal` (через `findParentCandidateId`) — фикс автоматически покрывает. |
| FR-011 | UI/HTTP контракт не меняется. |

## Миграция схемы БД

**Не требуется.** Фикс локализован в SQL внутри `Utils.findDuplicateOriginal`. Никаких новых колонок, индексов, триггеров, recordhash-перерасчётов.

## Связь с другими спеками

- [specs/238-import-folder-author-album-cover/](../238-import-folder-author-album-cover/) — определил «только тот же автор», который фикс сохраняет через `ILIKE ?`.
- [specs/278-fix-key-loss-on-lyrics-search/](../278-fix-key-loss-on-lyrics-search/) — определил reload-from-db-before-save, который фикс сохраняет в `applyDuplicateOriginal`.
- [specs/277-song-name-censored/](../277-song-name-censored/) — добавил `song_name_censored`. Не затрагивается напрямую (baseline-автозаполнение в `saveToDb` работает само).
