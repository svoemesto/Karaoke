---
status: Active
slug: 310-ochistka-papki-logov
related:
  - ../../specs/310-ochistka-papki-logov/spec.md
---

# 310 — Автоочистка логов KaraokeProcess (LiveDoc)

> Drill-down — [specs/310-ochistka-papki-logov/spec.md](../../specs/310-ochistka-papki-logov/spec.md).

## Что делает

`KaraokeProcessWorker` при каждой успешной записи лога в `/sm-karaoke/system/logs/`
сразу после неё удаляет регулярные файлы старше 30 дней. Без cron, без отдельного
потока — очистка привязана к моменту записи.

## Поведение

- **Триггер**: после каждого `File(...).writeText(...)` + `chmod 666` в `KaraokeProcessThread.run()` (FR-3).
- **Порог**: `LOG_RETENTION_DAYS = 30` (константа в `Constants.kt`, `const val` — фиксированная, без runtime конфигурируемости).
- **Условие удаления**: `mtime <= now - 30 дней` И `mtime <= now` (защита от clock skew, FR-3).
- **Граница** (D5, SC-2): strict `>` на KEEP, файл с `mtime == threshold` удаляется.
- **Fail-open** (FR-4): исключения внутри cleanup логируются в stdout и не пробрасываются. Per-file IOException → пропуск файла, продолжение.
- **Только регулярные файлы** (FR-2): `Files.isRegularFile` пропускает подкаталоги / symlinks / sockets.
- **Отсутствие папки** (FR-5): если `/sm-karaoke/system/logs/` не существует, `Files.list` бросит `NoSuchFileException`, пойманное outer try/catch — функция возвращается без ошибки.

## Acceptance (резюме)

- **SC-1**: при типичной нагрузке (100 процессов/день) папка не превышает 3000 файлов / 100 МБ.
- **SC-2**: файлы ≤ 30 дней сохраняются, > 30 дней удаляются.
- **SC-3**: запись нового лога не блокируется ошибками cleanup (fail-open).
- **SC-4**: cleanup ≤ 1 сек на 1000 файлов.
- **SC-5**: livedoc + INDEX в этом файле.

## Tests (FR-6)

`KaraokeProcessCleanupTest` — 4 теста:
- `cleanupRemovesOldFiles` (SC-2)
- `cleanupIgnoresFutureFiles` (FR-3, clock skew)
- `cleanupFailsOpenOnLockedFile` (FR-4, per-file isolation)
- `cleanupKeepsFreshFilesForDebug` (SC-2 boundary)

## История

- Создан: 2026-09-06 (Pass 310).
- Заменил откаченную спеку #309 (v1 → v3 revert из-за нерелевантного env-var).
