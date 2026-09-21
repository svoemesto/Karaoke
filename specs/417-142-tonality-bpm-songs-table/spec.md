# Feature Specification: Тональность и темп в таблице песен (#142)

**Feature Branch**: `417-142-tonality-bpm-songs-table`
**Created**: 2026-09-21
**Status**: Implemented
**Input**: OpenProject #142 «Тональность и темп в таблице песен». Админка,
компонент «Песни». В таблицу песен добавить столбцы тональности (`key`) и
темпа (`bpm`) между `t/c` и `HR`. Тональность — сокращённо, как в плеере
(«C minor» → «Cm», «D major» → «D»).

## OpenProject Tracking *(MANDATORY — see AGENTS.md § Issue-tracker OpenProject)*

- **Issue ID**: `#142`.
- **Title**: «Тональность и темп в таблице песен».
- **Created in OpenProject**: 2026-09-21.
- **Workflow**:
  1. **Claim**: `bash tools/tracker.sh claim-issue 142` — выполнен 2026-09-21
     (`New` → `In progress`, assignee=ai-agent).
  2. **Add comment с отчётом** (после merge):
     `bash tools/tracker.sh add-comment 142 --file specs/417-142-tonality-bpm-songs-table/report.md`.
  3. **Mark review**: `bash tools/tracker.sh mark-review 142`.
  4. **Close** (owner, после merge): `bash tools/tracker.sh close-issue 142`.

## Knowledge References *(MANDATORY — see Constitution Principle IX)*

### Pre-flight log

- **Дата pre-flight**: 2026-09-21.
- **Grep-запросы** (минимум 3):
  1. `grep -ril 'tonality|_shortKey|bpm' knowledge/` → `knowledge/domains/integration/components/song-public-dto.md`,
     `knowledge/domains/processing/components/key-bpm-from-file.md`,
     `knowledge/domains/catalog/components/song-entity.md`,
     `knowledge/system/frontend/*` (stores/composables).
  2. `grep -ril 'SongsTable|songDigestFields|таблиц.*песен' knowledge/` →
     `knowledge/system/frontend/webvue3-views-detailed.md`, `store-songs.md`, `vuex-patterns.md`.
  3. `grep -ril 'KaraokePlayer|коротк.*тональн' knowledge/` → нет прямого описания
     `_shortKey`; логика найдена в коде `webvue3/src/player/KaraokePlayer.js`
     (spec 265, ветка `265-key-short-display`).
  4. `grep -rln 'SongDTOdigest|songDigest|songsdigests' knowledge/` →
     `knowledge/domains/integration/components/dtos.md`.

### Knowledge files consulted

- [`knowledge/domains/integration/components/dtos.md`](../../knowledge/domains/integration/components/dtos.md)
  — реестр DTO; `SongDTOdigest` (эндпоинт `/api/songsdigests`) — источник данных таблицы.
- [`knowledge/system/frontend/webvue3-views-detailed.md`](../../knowledge/system/frontend/webvue3-views-detailed.md)
  — `SongsView` → `SongsTable`; колонки рендерятся из `songDigestFields`.
- [`knowledge/system/frontend/store-songs.md`](../../knowledge/system/frontend/store-songs.md)
  — Vuex-модуль `Songs`, `loadSongsDigests`.
- [`knowledge/domains/processing/components/key-bpm-from-file.md`](../../knowledge/domains/processing/components/key-bpm-from-file.md)
  — откуда берутся `key`/`bpm` (`SongField.KEY`/`SongField.BPM`).
- [`knowledge/domains/catalog/components/song-entity.md`](../../knowledge/domains/catalog/components/song-entity.md)
  — `Song.key`/`Song.bpm`, `SongDTO`-поля.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Видеть тональность и темп в таблице песен (Priority: P1)

Владелец открывает админку → «Песни» и между колонками `t/c` и `HR` видит
`Ton` (тональность сокращённо) и `BPM` (темп). Тональность совпадает по
обозначению с плеером; при отсутствии значения — прочерк.

**Acceptance Scenarios**:

1. **Given** у песни `key = "C minor"`, **When** таблица отрисована,
   **Then** в колонке `Ton` отображается `Cm`.
2. **Given** у песни `key = "D major"`, **Then** в `Ton` — `D`.
3. **Given** у песни `key` пуст, **Then** в `Ton` — `-`.
4. **Given** у песни `bpm = 128`, **Then** в `BPM` — `128`.
5. **Given** у песни `bpm = 0`, **Then** в `BPM` — `-`.
6. **Given** у песни `bpm > 0`, **Then** колонка `BPM` сортируема.

### Edge Cases

- Нераспознанная тональность (не `A-G`) → `KaraokePlayer._shortKey` возвращает
  исходную строку; при пустой — `-`.
- Бемоли нормализуются в диезы (`Bb minor` → `A#m`), как в плеере.

## Requirements *(mandatory)*

- **FR-001**: Система MUST отображать в таблице песен (`SongsTable.vue`) колонку
  тональности (`Ton`) между `t/c` и `HR`.
- **FR-002**: Система MUST отображать тональность сокращённо, используя **ту же**
  логику, что плеер (`KaraokePlayer._shortKey`): «C minor» → «Cm», «D major» → «D»,
  `Bb`/`Eb` нормализуются в диезы.
- **FR-003**: Система MUST отображать колонку темпа (`BPM`) между `t/c` и `HR`.
- **FR-004**: Система MUST показывать `-` при пустой/нулевой тональности и `bpm = 0`.
- **FR-005**: Backend `SongDTOdigest` MUST передавать поля `key: String` и
  `bpm: Long` (заполняются в `SongDTO.toDtoDigest()`).
- **FR-006**: Колонка `BPM` MUST быть сортируемой; `Ton` — не сортируется.

## Success Criteria *(mandatory)*

- **SC-001**: Тональность в таблице визуально идентична краткому обозначению
  в плеере для всех форматов, которые плеер распознаёт.
- **SC-002**: `npm run lint` / `npm run build` (webvue3) и
  `:karaoke-app:compileKotlin` / `:karaoke-web:compileKotlin` проходят без ошибок.

## Assumptions

- Логика сокращения тональности уже реализована в `KaraokePlayer._shortKey`
  (spec 265) и переиспользуется без дублирования.
- Новых колонок в БД не требуется: `key`/`bpm` уже хранятся (`SongField.KEY`/`BPM`).
