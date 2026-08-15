---
status: Active
slug: 124-filename-sanitization-rename
related:
  - ../domain/catalog.md
  - ../domain/processing.md
  - ../domain/editorial.md
  - ../../specs/124-filename-sanitization-rename/spec.md
---

# 124 — Санитайзинг имён файлов при импорте + переименование (LiveDoc)

> Drill-down — [specs/124-filename-sanitization-rename/spec.md](../../specs/124-filename-sanitization-rename/spec.md).

## What it does

При импорте песен из папки имена файлов с «проблемными» символами (например,
`2012 (01) [Ария] - Дай жару!.mp3` с `!`) ломали Demucs — песня «падала»
при стем-сепарации.

**Фикс**:
1. **Санитайзинг при импорте** — имя файла → `{artist} - {song}.flac` (без
   спецсимволов для `flac`/файловой системы), но `songName` в БД сохраняет
   оригинальный `Дай жару!`.
2. **Переименование в SongEdit** — если редактор меняет имя файла, каскадно
   переименовываются: аудио на диске, стемы на диске, объекты в MinIO
   (local + remote) — всё, что зависит от шаблона имени.
3. **Частичный отказ** — best-effort, ошибка → статус «требует ручной
   проверки», повтор разрешён.
4. **Блокировка при активной обработке** — если идёт стем-сепарация,
   переименование отклоняется с понятным сообщением.
5. **Уникальность в пределах папки** — если два файла дают одинаковое имя,
   второй получает числовой суффикс.

## User Stories (краткий список)

- **US1** (P1): Импорт с `!` в имени → Demucs больше не падает.
- **US2** (P1): Переименование в SongEdit → каскад по всем зависимым файлам.

## Functional Requirements (указатель)

- **FR-001**: `FilenameSanitizer.sanitizeForFs(originalName)` — стабильное правило.
- **FR-002**: `SongFileRenameService.renameSongFiles(song, newName)` — каскад.
- **FR-003**: Lock-file на песню во время обработки (Demucs → блокирует rename).
- **FR-004**: Коллизия → числовой суффикс.

## Acceptance Criteria

- [ ] **AC1**: `Дай жару!.mp3` → импорт успешен, Demucs работает.
- [ ] **AC2**: Edit `Name=` → каскадно переименованы файл + стемы + MinIO.
- [ ] **AC3**: Переименование при активной Demucs-обработке → отклонено с message.
- [ ] **AC4**: Коллизия → авто-суффикс ` (2).flac`.

## Related LiveDocs

- Domain: [catalog.md](../domain/catalog.md) (Song.songName), [processing.md](../domain/processing.md) (Demucs), [editorial.md](../domain/editorial.md) (editor)
- Architecture: [L3-components.md](../architecture/L3-components.md) (MinIO layer)

## Code

- Backend: `karaoke-app/.../service/FilenameSanitizer.kt` (новый)
- Backend: `karaoke-app/.../service/SongFileRenameService.kt` (новый)
- Frontend: `webvue3/src/components/Songs/SongEdit.vue` — UI переименования + status ошибки

## History

- Created: 2026-08-14
- Last updated: 2026-08-14