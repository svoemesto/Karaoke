# Pass 401 — Report: OpenProject #126 «Поиск тональности»

> **Branch**: `401-key-from-file` → **PR**: #507 → **OpenProject**: #126
> **Date**: 2026-09-15
> **Author**: ai-agent (Karaoke)

## Summary

Если у песни отсутствует тональность, но для неё уже есть файл `<songFile> [key].json` —
**применяем** тональность из файла вместо запуска docker. Экономит ~5-10 секунд docker-прогона.

## Pipeline

| Stage | Status |
|---|---|
| 1. `/speckit-specify` | ✅ spec.md + 21/21 checklist PASS |
| 2. `/speckit-clarify` | ✅ 1 question (where_check = «В обоих»), FR-001.a |
| 3. `/speckit-plan` | ✅ plan + research + data-model + quickstart + contracts |
| 4. `/speckit-tasks` | ✅ 16 tasks, 6 phases |
| 5. `/speckit-analyze` | ✅ 0 CRITICAL, coverage 100% |
| 6. `/speckit-implement` | ✅ 16/16 tasks completed |

## Code changes

| File | Change |
|---|---|
| `karaoke-app/.../model/Song.kt` | +`applyKeyBpmFromFileIfExists()` (instance), +`parseKeyBpmFileOrNull()` (companion pure-parse), +`infra.cache.keybpm` logger |
| `karaoke-app/.../HealthReport.kt` | solutionActions: проверка файла перед `KaraokeProcess.createProcess` |
| `karaoke-app/.../KaraokeProcess.kt` | `createProcess`: проверка файла перед записью процесса для типа `KEY_BPM_FROM_FILE` |
| `karaoke-app/src/test/.../KeyBpmFromFileCacheTest.kt` | **NEW** — 4 unit-теста |
| `knowledge/domains/processing/components/key-bpm-from-file.md` | **NEW** — Living Docs |

## Tests

- **4/4 PASS**: `parseKeyBpmFileOrNull` — success / missing / null-fields / invalid-JSON.
- **Hard gates**: ktlintCheck ✅, JPA guard ✅, MP4 guard ✅ (без новых violations).

## Race-safety

- `saveToDbLocked()` (Pass 299/357) — атомарно через `SELECT FOR NO KEY UPDATE` + UPDATE.
- Single-flight через `existedProcesses` lookup в `createProcess`.

## Performance

| Сценарий | Раньше | Теперь |
|---|---|---|
| Песня без тональности, файл `[key].json` есть | ~5-10 сек (docker) | **< 1 сек** (file read + saveToDbLocked) |
| Песня без тональности, файла нет | ~5-10 сек (docker) | ~5-10 сек (docker) — без изменений |

## Out of scope (явно)

- **Файл устарел** (например, аудио перезаписано после keybpmfinder) — будет пересчитан через docker при следующем HealthReport-чеке (поведение по умолчанию, см. spec.md § Out of Scope).
- **UI-индикатор «key/bpm from cache»** в карточке песни — отдельная задача.
- **Распознавание мажор vs минор** — текущий формат сохраняем.

## OpenProject workflow

- [x] Claim (#126, status: In progress).
- [ ] Add comment (Pass 401 follow-up, после merge PR #507).
- [ ] Mark review (Pass 401 follow-up, после add-comment).
- [ ] Close (после ревью владельцем).

## Related

- AGENTS.md v3.1.0 — Hard Gates (Pass 372-375), MUST #0 Knowledge-first.
- Constitution v2.4.0 — Principle IX (Knowledge SSoT).
- Pass 128 — аналогичный паттерн `infra.cache.hrpool` для batch pool.
- Pass 299 / 357 — `saveToDbLocked()` для race-safety.
