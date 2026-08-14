---
status: Active
slug: 102-rename-song-settings-vars
related:
  - ../domain/catalog.md
  - ../architecture/L3-components.md
  - ../../specs/102-rename-song-settings-vars/spec.md
---

# 102 — Переименование settings → song в коде (полный охват) (LiveDoc)

> Drill-down — [specs/102-rename-song-settings-vars/spec.md](../../specs/102-rename-song-settings-vars/spec.md).

## Что делает

Полный рефакторинг: после переименования класса `Settings → Song` остались
артефакты с именами `settings*`:
- Параметры: `fun getVKPictureBase64(settings: Song): String` → `song: Song`.
- DTO: `HealthReportDTO.settingsId/settingsFileName` → `songId/songFileName`.
- DTO: `KaraokeProcessDTO.settingsId` → `songId`.
- HTTP wire-level: `MainController.POST /changesettingsstatus` — все
  `@RequestParam settings_*` → `song_*`.
- SSE: `settingsId` keys в `SseNotification.kt` + соответствующий listener в
  `webvue3/src/components/Songs/store.js`.
- Legacy Thymeleaf: `songs.html`, `songs2.html`, `area_center_column.html`
  (используют ~60 параметров `settings_*`).
- Frontend `webvue3`: `settings_context.js` и везде, где `settingsId`.

**БД-колонка `settings_id`** НЕ переименована (прецедент из
`28_rename_settings_to_songs.sql`, см. research §5.1) — миграция БД не входит.

## User Stories (краткий список)

- **US1** (P1): Полный охват refactoring: backend + DTO + HTTP + SSE + frontend.
- **US2** (P2): Все `settingsId` references на фронте обновлены.

## Functional Requirements (указатель)

- **FR-001**: Синхронный PR — все изменения в одном (нельзя делать partial deploy).
- **FR-002**: `HealthReportDTO.songId/songFileName` — JSON-ключи соответственно.
- **FR-003**: HTTP-параметр `song_xxx` для всех ранее `settings_*`.
- **FR-004**: SSE-ключи `songId`/`songFileName`.

## Acceptance Criteria

- [ ] **AC1**: grep `settingsId` → 0 совпадений в коде (кроме БД-колонки).
- [ ] **AC2**: `git grep "var.*settings\|: Song.*settings"` → 0 совпадений.
- [ ] **AC3**: Тесты проходят (если есть unit-тесты на DTO).

## Связанные LiveDocs

- Domain: [catalog.md](../domain/catalog.md) (Song class)
- Architecture: [L3-components.md](../architecture/L3-components.md)
- Specs: `28_rename_settings_to_songs.sql` (БД-прецедент)

## Код

- ~50+ файлов — `sed`-замены `settingsId → songId`, `: Song) -> : Song)`, и т.п.
- `MainController.kt`: ~60 параметров
- `SseNotification.kt`: SSE keys
- `HealthReportDTO.kt`, `KaraokeProcessDTO.kt`: DTO поля
- `webvue3/src/components/Songs/store.js`: SSE listener + settingsId
- `webvue3/src/settings_context.js` (legacy)
- Шаблоны: `songs.html`, `songs2.html`, `area_center_column.html`

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14