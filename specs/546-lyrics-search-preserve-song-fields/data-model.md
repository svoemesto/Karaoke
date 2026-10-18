# Phase 1 — Data Model: наборы полей и состояния

**Feature**: `546-lyrics-search-preserve-song-fields` | **Date**: 2026-10-18
**Источник истины по смыслу**: [spec.md](spec.md) | **Решения**: [research.md](research.md)

Модель описывает **не новые сущности**, а контракт записи в существующую `tbl_songs`:
какие колонки имеет право менять каждая операция подстановки данных и при каких условиях.

## 1. Сущность Song (`tbl_songs`) — поля, участвующие в сценарии

Типы сверены с `deploy/karaoke-db/*.sql` и `deploy/new_comp/.../karaoke_clear_dump.sql`.

| Поле (Kotlin) | Колонка | Тип БД | Default / NOT NULL | Кто пишет |
|---|---|---|---|---|
| `sourceText` | `source_text` | `text` | `''`, NOT NULL | подстановка текста, копирование от родителя |
| `resultText` | `result_text` | `text` | `''`, NOT NULL | копирование от родителя / аудио-родителя |
| `sourceMarkers` | `source_markers` | `text` | NULL допустим | копирование от родителя / аудио-родителя |
| `formattedTextSong` | `formatted_text_song` | `text` | `''`, NOT NULL | копирование контента |
| `formattedTextTabs` | `formatted_text_tabs` | `text` | `''`, NOT NULL | копирование контента |
| `formattedTextChords` | `formatted_text_chords` | `text` | `''`, NOT NULL | копирование контента |
| `idStatus` | `id_status` | `integer` | `0`, NOT NULL | любая операция — **только монотонно вверх** |
| `rootId` | `root_id` | `integer` | `0`, NOT NULL | `applyDuplicateOriginal`, `applyFamilySongSelection` |
| `audioParentId` | `audio_parent_id` | `integer` | `0`, NOT NULL | `applyFamilySongSelection` (opt-in) |
| `audioSimilarityPercent` | `audio_similarity_percent` | `integer` | `0`, NOT NULL | `applyFamilySongSelection` (opt-in) |
| `audioDeltaMs` | `audio_delta_ms` | `bigint` | `0`, NOT NULL | `applyFamilySongSelection` (opt-in) |

Поля, которые **не могут** появиться в записи этих операций ни при каких условиях:
`song_name`, `song_author`, `song_album`, `song_year`, `song_track`, `song_tone`, `song_bpm`,
`audio_song` / `audio_music` / `audio_vocals` / `audio_drums` / `audio_bass`,
`publish_date` / `publish_time`, `tags`, `song_type`, все `id_*` / `version_*` площадок,
`recordhash` (его ведёт триггер).

## 2. Наборы полей по операциям

### 2.1 `applyFoundLyricsIfMissing` — подстановка найденного текста

| Колонка | Значение | Условие включения в `UPDATE` |
|---|---|---|
| `source_text` | найденный текст | guard: текста ещё нет |
| `id_status` | `1` | guard: `previous.id_status = 0` |

### 2.2 `applyDuplicateOriginal` — копирование от найденного родителя (импорт)

| Колонка | Значение | Условие |
|---|---|---|
| `root_id` | `original.id` | всегда |
| `source_text`, `result_text`, `source_markers` | из `original` | всегда |
| `formatted_text_song`, `formatted_text_tabs`, `formatted_text_chords` | из `original` | всегда |
| `id_status` | `1` | только если `previous.id_status = 0` (не понижаем) |

### 2.3 `applyAudioParentMarkers` — маркеры от аудио-родителя

| Колонка | Значение | Условие |
|---|---|---|
| `source_text`, `result_text` | из `audioParent` | всегда |
| `source_markers` | сдвинутые маркеры (`shiftMarkersAndFixEnd`) | всегда |
| `formatted_text_*` | из `audioParent` | всегда |
| `id_status` | `5` | только если `previous.id_status < 5` (монотонно вверх) |

### 2.4 `applyFamilySongSelection` — выбор похожей версии из модалки

| Колонка | Значение | Условие |
|---|---|---|
| `root_id` | корень семьи | всегда (осознанный выбор пользователя) |
| `source_text`, `result_text`, `source_markers`, `formatted_text_*` | из кандидата | всегда |
| `audio_parent_id`, `audio_similarity_percent`, `audio_delta_ms` | из аргументов | только если все три заданы (opt-in) |
| `id_status` | `1` | только если `previous.id_status = 0` |

## 3. Состояния `id_status`

```
0 (NONE) ──подстановка текста──▶ 1 (TEXT_CREATE)
0 (NONE) ──копирование от родителя──▶ 1
0..4     ──маркеры аудио-родителя──▶ 5 (MARKERS_CHECK)
любой статус ≥ 1 ──подстановка данных──▶ без изменений
```

Инварианты:

- **I-1 (монотонность)**: операция подстановки данных не может записать `id_status` меньше
  текущего. Проверка — по значению, прочитанному под блокировкой строки.
- **I-2 (только свои поля)**: `UPDATE` содержит исключительно колонки из таблицы §2 для
  соответствующей операции.
- **I-3 (текст не перезаписывается)**: подстановка текста не выполняется, если в БД на момент
  записи `source_text` непустой.
- **I-4 (атомарность)**: между чтением состояния и `UPDATE` нет окна, в котором параллельная
  запись теряется (блокировка строки на всё время транзакции).
- **I-5 (побочные эффекты)**: после `commit` отправляются SSE `recordChange`, хук
  `SyncAudioDescendants`, remote-push (если включён) — ровно по фактически изменённым колонкам.

## 4. Guard-условия (проверяются под блокировкой)

| Операция | Условие | Результат |
|---|---|---|
| Подстановка текста | строка не найдена | `NOT_FOUND` → WARN, ничего не пишем |
| Подстановка текста | `source_text` непустой (`""`/`[""]`/`NULL` — пусто) | `SKIPPED_EXISTING_TEXT` → INFO, ничего не пишем |
| Подстановка текста | найденный текст пустой | `SKIPPED_EMPTY_INPUT` → ничего не пишем |
| Подстановка текста | статус уже ≥ 1 | поле `id_status` не включается, текст пишется |
| Копирование контента | строка не найдена | `NOT_FOUND` → WARN |
| Копирование контента | статус уже выше целевого | поле `id_status` не включается, контент пишется |
| Любая | SQL-ошибка / `lock_timeout` | `FAILED` → rollback, WARN, вызывающий продолжает работу |

## 5. Связи и влияние

- **`KaraokeProcess` (`tbl_processes`)** — другой писатель тех же полей (тональность, BPM,
  стемы). Не изменяется; его записи просто не должны откатываться подстановкой текста.
- **`SearchAsync` / `SearchResult`** — источник текста; примитив их не трогает, кроме
  существующего правила удаления при переходе статуса через `6` (в этом сценарии недостижимо).
- **`recordhash`-триггер `tg_tbl_songs_recordhash`** — обновляется автоматически на любом
  `UPDATE`; ручное управление не требуется, two-DB sync продолжает видеть изменение.
