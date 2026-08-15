---
status: Active
slug: 156-remove-songs-table-platform-flags
related:
  - ../domain/catalog.md
  - ../domain/publishing.md
  - ../architecture/L3-components.md
  - ../../specs/156-remove-songs-table-platform-flags/spec.md
  - ../../archive/docs/features/songs-table.md
---

# 156 — Удалить 18 столбцов-флагов публикации из таблицы «Песни» (LiveDoc)

> Drill-down — [specs/156-remove-songs-table-platform-flags/spec.md](../../specs/156-remove-songs-table-platform-flags/spec.md).

## Что делает

В админ-таблице «Песни» (`webvue3`) в шапке было 18 узких столбцов-флагов
по 20 px (всего ≈ 360 px ширины): `SP/VG/ZL/ZK/ZC/ZM/VL/VK/VC/VM/TL/TK/TC/TM/ML/MK/MC/MM`.

Эти флаги перегружали таблицу, особенно на экранах ≤ 1440px. Удалены
**только визуально** — данные в БД (`flagSponsr`, `flagVk`, `flagDzen*`,
`flagTelegram*`, `flagMax*`) сохранены.

**Что НЕ затрагивается**: раздел «Публикации» (`/publications`) — там
столбцы публикации остаются (они нужны в этой конкретной таблице).

**Фильтры**: если фильтры в `SongsFilterModal.vue` содержат эти поля — они
тоже удалены из UI-формы фильтрации.

## User Stories (краткий список)

- **US1** (P1): Таблица «Песни» стала компактной — нет 18 узких столбцов.
- **US2** (P2): Фильтры в `SongsFilterModal.vue` синхронизированы (поля удалены из UI).

## Functional Requirements (указатель)

- **FR-001**: Удалить 18 столбцов из шапки таблицы «Песни».
- **FR-002**: Поля БД оставить без изменений.
- **FR-003**: Если поля есть в `SongsFilterModal.vue` — удалить из UI.

## Acceptance Criteria

- [ ] **AC1**: «Песни» → в шапке **нет** столбцов SP/VG/ZL/ZK/ZC/ZM/VL/VK/VC/VM/TL/TK/TC/TM/ML/MK/MC/MM.
- [ ] **AC2**: Ширина таблицы уменьшилась на ~360 px.
- [ ] **AC3**: Остальные столбцы видны без изменений (ID, Композиция, Исполнитель, Год, ...).
- [ ] **AC4**: `SongsFilterModal.vue` → нет полей `flagSponsr`, `flagVk`, `flagDzen*`, и т.п.
- [ ] **AC5**: «Публикации» (`/publications`) → столбцы публикации по-прежнему видны.
- [ ] **AC6**: При клике на строку → песня редактируется, флаги в БД сохраняются.

## Связанные LiveDocs

- Domain: [catalog.md](../domain/catalog.md) (Song: `flag*` поля), [publishing.md](../domain/publishing.md) (флаги публикации по платформам)
- Architecture: [L3-components.md](../architecture/L3-components.md) (контроллер + DTO)

## Код

- Frontend: `webvue3/src/components/Songs/SongsTable.vue` — удалить 18 `<th>` ячеек
- Frontend: `webvue3/src/components/Songs/SongsTable.vue` — удалить 18 `<td>` ячеек в `<tr>`
- Frontend: `webvue3/src/components/Songs/SongsFilterModal.vue` — удалить поля фильтрации (если были)

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14