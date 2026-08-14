---
status: Active
slug: 010-lyrics-spec-tags
related:
  - ../domain/catalog.md
  - ../domain/editorial.md
  - ../features/018-fix-spec-tag-markers-at-zero.md
  - ../../specs/010-lyrics-spec-tags/spec.md
---

# 010 — Спецтеги в тексте песни для авто-разметки маркеров (LiveDoc)

> Drill-down — [specs/010-lyrics-spec-tags/spec.md](../../specs/010-lyrics-spec-tags/spec.md).

## Что делает

Механизм **спецтегов** в тексте песни для авто-разметки маркеров.

**Синтаксис**:
- `~имя~` — без значения.
- `~имя:значение~` — со значением.
- Место в тексте — только отдельной строкой.

**Набор тегов v1**:
- `~newline~` — перенос строки (newline marker).
- `~group:N~` — группа маркеров.
- `~comment:текст~` — комментарий.

**Обратная совместимость**: теги только **добавляют** отсутствующие маркеры,
никогда не удаляют и не трогают существующие.

## User Stories (краткий список)

- **US1** (P1): Спецтеги в тексте песни → авто-разметка маркеров.

## Functional Requirements (указатель)

- **FR-001**: Парсер спецтегов `~name~` / `~name:value~` (отдельная строка).
- **FR-002**: `syncMarkersFromSpecTags(text)` — только добавление, не удаление.
- **FR-003**: SubsEdit рендерит спецтеги визуально (отдельный цвет).

## Acceptance Criteria

- [ ] **AC1**: `~newline~` в тексте → newline-маркер на нужной позиции.
- [ ] **AC2**: Существующие маркеры не затрагиваются.
- [ ] **AC3**: SubsEdit подсвечивает спецтеги.

## Связанные LiveDocs

- Domain: [catalog.md](../catalog.md) (lyrics), [editorial.md](../editorial.md) (SubsEdit)
- Feature: [018-fix-spec-tag-markers-at-zero.md](../features/018-fix-spec-tag-markers-at-zero.md) (связанная)

## Код

- Frontend: `webvue3/src/components/Songs/SubsEdit.vue` — `parseSpecTags()`
- Frontend: `webvue3/src/services/lyricsParser.js` — парсер

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14