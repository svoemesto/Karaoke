---
status: Active
slug: 155-editor-typograph-button
related:
  - ../domain/catalog.md
  - ../domain/editorial.md
  - ../architecture/L3-components.md
  - ../../specs/155-editor-typograph-button/spec.md
---

# 155 — Кнопка «Типограф» в онлайн-редакторе (LiveDoc)

> Drill-down — [specs/155-editor-typograph-button/spec.md](../../specs/155-editor-typograph-button/spec.md).

## Что делает

В онлайн-редакторе разметки (и в `karaoke-public`, и в `webvue3`) рядом с
кнопкой «Очистить маркеры» теперь есть кнопка «Типограф» — та же, что в
SubsEdit (классический редактор) делает «Произвести замену текста согласно
правилам»:
- прямые кавычки → «ёлочки»,
- дефис → тире,
- лишние пробелы → нормализация,
- спецсимволы — стандартизация.

**Эффект**: редактор избавлен от необходимости переносить текст в SubsEdit
и обратно — типографика правится одним кликом прямо в онлайн-редакторе.

## User Stories (краткий список)

- **US1** (P1): Типографская правка текста одним кликом (кавычки, тире, пробелы).

## Functional Requirements (указатель)

- **FR-001**: Кнопка «Типограф» рядом с «Очистить маркеры» в онлайн-редакторе (`karaoke-public` и `webvue3`).
- **FR-002**: Backend endpoint `POST /api/songeditor/typograph` с телом `{text}` → возвращает типографированный текст.
- **FR-003**: Сервис-функция типографики (вынесена из `SubsEdit` для переиспользования).
- **FR-004**: UX — preview до применения (показать diff / undo).

## Acceptance Criteria

- [ ] **AC1**: Кнопка «Типограф» видна в онлайн-редакторе (оба интерфейса).
- [ ] **AC2**: Прямые кавычки → «ёлочки» после клика.
- [ ] **AC3**: Дефис → тире, лишние пробелы → нормализация.
- [ ] **AC4**: Можно отменить (undo / re-do).
- [ ] **AC5**: Frontend `webvue3` и `karaoke-public` используют ту же сервис-функцию.

## Связанные LiveDocs

- Domain: [catalog.md](../domain/catalog.md) (lyrics как Value Object), [editorial.md](../domain/editorial.md)
- Architecture: [L3-components.md](../architecture/L3-components.md)
- Specs: `specs/010-lyrics-spec-tags` (базовые спецтеги)

## Код

- Frontend: `karaoke-public/src/components/SongEditor.vue` — добавить кнопку «Типограф»
- Frontend: `webvue3/src/components/Songs/SubsEditOnline.vue` — кнопка «Типограф»
- Backend: `karaoke-app/.../service/TypographerService.kt` (вынести из SubsEdit)
- Backend: `karaoke-web/.../controllers/PublicSongEditorController.kt` — endpoint

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14