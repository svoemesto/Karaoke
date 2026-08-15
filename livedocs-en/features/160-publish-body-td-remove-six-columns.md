---
status: Active
slug: 160-publish-body-td-remove-six-columns
related:
  - ../domain/publishing.md
  - ../domain/catalog.md
  - ../architecture/L3-components.md
  - ../../specs/160-publish-body-td-remove-six-columns/spec.md
---

# 160 — Чистка PublishTableBodyTd + удаление processColor* из DTO (LiveDoc)

> Drill-down — [specs/160-publish-body-td-remove-six-columns/spec.md](../../specs/160-publish-body-td-remove-six-columns/spec.md).

## What it does

В админ-таблице «Публикации» (`webvue3`) каждая ячейка `PublishTableBodyTd`
показывала название песни (150 px) + шесть узких цветовых индикаторов
(Melt / Sponsr / Dzen / VK / Pl / Telegram) по 10 px = итого 210 px.

**Цветовые «полосочки» перестали быть нужны** редактору — он хочет видеть
только название на всю ширину ячейки. Удалены:
- 6 ячеек `class="publish-column"` в `PublishTableBodyTd.vue`.
- Соответствующие `processColorMeltLyrics/Karaoke/Chords/Melody` (и
  аналогичные для других платформ) — из DTO.
- Раскраска кнопок PLAY в `SongEdit.vue` по `processColor*` — убрана.

**Проверка**: поля реально нигде больше не используются (grep по всему коду).

## User Stories (краткий список)

- **US1** (P1): В таблице «Публикации» ячейки показывают только название песни (210 px ширины).
- **US2** (P1): DTO очищены от неиспользуемых `processColor*` полей.

## Functional Requirements (указатель)

- **FR-001**: Удалить 6 ячеек `publish-column` + class `publish-name` на 210 px.
- **FR-002**: Удалить `processColor*` поля из DTO (после grep-проверки).
- **FR-003**: Убрать раскраску PLAY в `SongEdit.vue` (более не нужен `processColor*`).

## Acceptance Criteria

- [ ] **AC1**: «Публикации» → ячейка содержит только название (210 px).
- [ ] **AC2**: DTO `SongPublicDto`, `SongDto` НЕ содержат `processColor*`.
- [ ] **AC3**: grep `processColor` → 0 совпадений (кроме удалённого кода в git-истории).
- [ ] **AC4**: Кнопки PLAY в `SongEdit.vue` НЕ раскрашиваются (визуальное подтверждение).
- [ ] **AC5**: Тесты UI проходят — таблица отображается без сломов.

## Related LiveDocs

- Domain: [publishing.md](../domain/publishing.md) (publish-процесс), [catalog.md](../domain/catalog.md) (Song)
- Architecture: [L3-components.md](../architecture/L3-components.md) (контроллеры + DTO)

## Code

- Frontend: `webvue3/src/components/Publish/PublishTableBodyTd.vue` — оставить только `.publish-name`
- Backend: `karaoke-app/.../dto/SongDto.kt`, `SongPublicDto.kt` — убрать `processColor*` поля
- Frontend: `webvue3/src/components/Songs/SongEdit.vue` — убрать `:style` с `processColor*`

## History

- Created: 2026-08-14
- Last updated: 2026-08-14