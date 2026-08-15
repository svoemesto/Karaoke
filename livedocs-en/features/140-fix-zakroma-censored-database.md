---
status: Active
slug: 140-fix-zakroma-censored-database
related:
  - ../domain/catalog.md
  - ../features/139-fix-censored-dictionary.md
  - ../features/141-fix-censored-web-storage-globals.md
  - ../../specs/140-fix-zakroma-censored-database/spec.md
  - ../architecture/censoring.md
---

# 140 — Падение /api/public/zakroma (Property APP_WORK_ON_SERVER не инициализирована) (LiveDoc)

> Drill-down — [specs/140-fix-zakroma-censored-database/spec.md](../../specs/140-fix-zakroma-censored-database/spec.md).

## What it does

После PR #179 (commit `72ea8eba`) на проде (`karaoke-web`, контейнер без
модуля `karaoke-app`) перестали работать **все** endpoint'ы, где
`String.censored()` вызывается из публичного пути — на момент репорта
`/api/public/zakroma` и `/api/public/zakroma?specialBucket=true`, любое
другое обращение (Song.songName.censored / Song.songNameCensored /
Publication.publishNNtext / Song.getDescription*) упало бы тем же исключением.

**Корневая причина**:
`String.censored()` (karaoke-app/Extentions.kt:210) получил дефолтный параметр
`database: KaraokeConnection = WORKING_DATABASE`. Без явной передачи —
это karaoke-app-глобал `WORKING_DATABASE`, инициализируемый в `karaoke-app`.
На `karaoke-web` (без `KaraokeAppService` бина) → `IllegalStateException:
Property APP_WORK_ON_SERVER should be initialized before get.`

**Фикс**: явное пробрасывание `database` через DI при вызове `censored()`. Удалить
дефолты-глобалы из публичного API.

## User Stories (краткий список)

- **US1** (P1): `/api/public/zakroma` возвращает 200 + данные (а не 500).

## Functional Requirements (указатель)

- **FR-001**: Удалить дефолт `database = WORKING_DATABASE` из `String.censored()`.
- **FR-002**: Все вызовы `censored()` в публичных endpoint'ах явно передают `database`.
- **FR-003**: Smoke-test перед merge: `bash tools/check-censored-public.sh` (эмулирует prod-окружение).

## Acceptance Criteria

- [ ] **AC1**: `/api/public/zakroma` на проде → 200 OK.
- [ ] **AC2**: Smoke-test на dev `karaoke-web` без `karaoke-app` → endpoint 200.
- [ ] **AC3**: Логи НЕ содержат `Property APP_WORK_ON_SERVER should be initialized`.

## Related LiveDocs

- Domain: [catalog.md](../domain/catalog.md) (Song.songName → censored)
- Feature: [139-fix-censored-dictionary.md](../features/139-fix-censored-dictionary.md) (общий root cause), [141-fix-censored-web-storage-globals.md](../features/141-fix-censored-web-storage-globals.md) (связанная фича)

## Code

- Backend: `karaoke-app/.../Extentions.kt:210` — убрать дефолт `database = WORKING_DATABASE`
- Backend: `karaoke-web/.../controllers/PublicZakromaController.kt` — передавать `database` явно
- Скрипт: `tools/check-censored-public.sh` — smoke-test без `karaoke-app` бина
- Grep: убрать любые `= WORKING_DATABASE` дефолты в публичном пути

## History

- Created: 2026-08-14
- Last updated: 2026-08-14