# Data Model: 232-admin-song-editor-local-db

**Feature**: 232-admin-song-editor-local-db
**Date**: 2026-08-15

## Важно

Эта фича **не меняет схему БД**. Не создаёт таблиц, не добавляет колонок,
не меняет индексы. `recordhash`-триггеры не затрагиваются. SQL-миграции
**не нужны**.

Фича меняет **runtime-логику выбора БД** в двух эндпоинтах
`SongEditorController` (mode='song'): Song теперь всегда читается и
пишется в `Connection.local()` (LOCAL-БД admin-машины), независимо от
параметра `target` в HTTP-запросе.

## Затронутые сущности

### Song (tbl_songs)

| Поле | Тип | Назначение | Изменения в фиче |
|------|-----|-----------|------------------|
| `id` | bigserial PK | Идентификатор песни | — |
| `song_name` | text | Название | — |
| `source_text` | text[] | Тексты по голосам | — |
| `source_markers` | jsonb | Маркеры по голосам | — |
| `id_status` | int | Статус песни (1=CREATE, …, 6=DEMO) | — |
| `recordhash` | text | md5 для sync LOCAL↔SERVER (триггер) | — (триггер пересчитывается автоматически при UPDATE через `Song.setSourceMarkers`/`setSourceText` → `saveToDb`) |
| ... (остальные поля Song) | ... | ... | — |

**Поведение при чтении** (`editById`, mode='song'):
- БЫЛО: `Song.loadFromDbById(songId, WORKING_DATABASE, …)` — глобал `WORKING_DATABASE = Connection.local()` на admin-машине; на проде может быть перенаправлен (если раскомментировать `Connection.REMOTE` в `Constants.kt:206`).
- СТАЛО: `Song.loadFromDbById(songId, Connection.local(), …)` — явное использование LOCAL-БД.

**Поведение при записи** (`editSave`, mode='song'):
- БЫЛО: `Song.loadFromDbById(id, db, …)` где `db = withDb(target)` (для `target='remote'` → `Connection.remote()` → серверная БД).
- СТАЛО: `Song.loadFromDbById(id, Connection.local(), …)` + `song.setSourceMarkers(...)` / `song.setSourceText(...)` — всегда в LOCAL-БД.

### SongAssignmentDraft (tbl_song_assignment_drafts)

| Поле | Тип | Назначение | Изменения в фиче |
|------|-----|-----------|------------------|
| `id` | bigserial PK | Идентификатор черновика | — |
| `assignment_id` | bigint FK → SongAssignment | Привязка к заданию | — |
| `edited_source_text` | text | JSON-encoded texts per voice | — |
| `edited_markers` | text | JSON-encoded markers per voice | — |
| `user_status` | int | Статус черновика | — |
| ... | ... | ... | — |

**Поведение при чтении/записи** (`editById`/`editSave`, **mode='assignment'**):
- НЕ меняется. Остаётся target-aware через `db = withDb(target)`, как сейчас.
- Это сделано намеренно — задание живёт в той БД, где идёт рабочий цикл (см. спеке US1.4, FR-003, LiveDoc `livedocs/domain/editorial.md`).

### SongAssignment (tbl_song_assignments)

Не затрагивается напрямую. Резолвится в `editById` для mode='assignment' (по `songId` из задания), но в `mode='song'` не используется.

## Связи, не изменившиеся

```
Song (tbl_songs) — [1:N] → SongAssignment (tbl_song_assignments) — [1:1] → SongAssignmentDraft (tbl_song_assignment_drafts)
```

Эти связи не меняются. Sync LOCAL ↔ SERVER остаётся стандартным
(`sync/SyncTarget.kt`, `KaraokeProperties.kt` флаги `sync_song_*`).

## Валидации

Не добавляются. Фича runtime-only.

## State Transitions

Не добавляются. Фича runtime-only.

## Что НЕ меняется

- Никаких миграций SQL (`deploy/karaoke-db/`).
- Никаких изменений в `KaraokeProperties.kt` (флаги sync не трогаются).
- Никаких изменений в `SyncRegistry` (`SyncTarget.kt`).
- Никаких изменений в `recordhash`-триггерах (триггеры остаются валидными, потому что схема таблицы не меняется; см. Constitution Principle III).

## Что меняется в коде

Только два метода в одном файле:
`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/SongEditorController.kt`:

1. `editById` (строки 732–830) — заменить `WORKING_DATABASE` на `Connection.local()` в вызове `Song.loadFromDbById` (строка 757).
2. `editSave` (строки 837–901) — для ветки `mode == "song"` (строки 859–878) заменить `withDb(target) { db → Song.loadFromDbById(id, db, …) }` на прямую работу с `Connection.local()` без `withDb(target)`. Параметр `target` остаётся в сигнатуре `@RequestParam` для обратной совместимости, но не используется для mode='song'.

Опционально (по FR-005 спеки): добавить отличимый код ошибки `song_not_found_in_local_db` в `editById` при отсутствии Song в LOCAL-БД (сейчас возвращается `found=false` без детализации). Решение — на этапе tasks.md.
