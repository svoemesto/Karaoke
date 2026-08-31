---
status: Active
slug: 279-fix-parent-search-folder-add
related:
  - ./238-import-folder-author-album-cover.md
  - ./238-import-folder-author-album-cover.md
  - ../../specs/279-fix-parent-search-folder-add/spec.md
  - ../../specs/279-fix-parent-search-folder-add/plan.md
  - ../../specs/279-fix-parent-search-folder-add/research.md
  - ../../specs/278-fix-key-loss-on-lyrics-search/spec.md
---

# 279 — Восстановить поиск родителя при добавлении файлов из папки (LiveDoc)

> Drill-down — [specs/279-fix-parent-search-folder-add/spec.md](../../specs/279-fix-parent-search-folder-add/spec.md).
> Это **bugfix** к фиче 238 (поиск «родителя» только у того же автора). Содержательное описание фичи 238 — в её [LiveDoc](./238-import-folder-author-album-cover.md); здесь — только суть фикса.

## Что делает

Точечный фикс регрессии, внесённой спекой 278 (`acfb936d`, 2026-08-30): после `songToSave.saveToDb()` в `applyDuplicateOriginal` и `applyAudioParentMarkers` добавляется явная синхронизация `newSong`/`song` в памяти с записанным состоянием. Без этого следующий шаг `doCreateFromFolder` (`findAudioParentByWaveform` → `song.saveToDb()`) видел расхождение `this.rootId = 0` (в памяти) ≠ `savedSong.rootId = original.id` (из БД) → diff включал `root_id = 0` → UPDATE перезатирал только что записанный `root_id` обратно в 0. Симптом: при импорте «Камнем по голове (Epic Orchestral, Cover-2)» от «Король и Шут» `root_id` оставался `0` и `findYandexSongLyrics` не запускался.

## User Stories (краткий список)

- **US1** (P1): поиск родителя у того же автора при импорте из папки — `root_id` записывается и не перезатирается.
- **US2** (P2): поиск родителя не пересекает авторов (сохраняется ограничение спеки 238).
- **US3** (P3): поиск родителя устойчив к регистру автора в имени файла.

## Functional Requirements (указатель)

- **FR-001..FR-006, FR-008, FR-009, FR-011**: см. спек 279 (поиск родителя, ограничение тем же автором, регистр, поведение «не найден», UI/HTTP-контракт).
- **FR-007**: защита от race condition (спека 278) сохраняется — мы только ДОБАВЛЯЕМ синхронизацию после `songToSave.saveToDb()`, не убираем reload-from-db-before-save.
- **FR-007a**: новая защита от расхождения память↔БД после спеки 278 — синхронизация `newSong` в `applyDuplicateOriginal` и `song` в `applyAudioParentMarkers`.

## Acceptance Criteria

- [ ] **AC1** (SC-001): импорт 5+ файлов одной группы с суффиксами в скобках — для 100% импортированных файлов `root_id` указывает на `id` базовой песни, текстовые поля скопированы.
- [ ] **AC2** (SC-002): ни одна импортированная песня не привязывается к чужому автору (FR-004 сохранён).
- [ ] **AC3** (SC-005): race condition защита (спека 278) сохранена — `song_tone`/`song_bpm`/`audio_*` не перезатираются.

## Связанные LiveDocs

- [238-import-folder-author-album-cover.md](./238-import-folder-author-album-cover.md) — основная фича (поиск родителя у того же автора).
- [specs/278-fix-key-loss-on-lyrics-search](../../specs/278-fix-key-loss-on-lyrics-search/spec.md) — оригинальный источник регрессии.

## Код

- Backend: `karaoke-app/.../utils/Utils.kt`:
  - `applyDuplicateOriginal:4528` — добавлена синхронизация `newSong` с записанным состоянием после `songToSave.saveToDb()` (для исправления H1 CONFIRMED).
  - `applyAudioParentMarkers:4568` — то же для `song` (consistency, чтобы `audio_*` поля не перезатирались аналогичной регрессией).

## История

- Создан: 2026-08-31 (Pass 245+).
- Последнее обновление: 2026-08-31.
