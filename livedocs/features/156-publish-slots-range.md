---
status: Active
slug: 156-publish-slots-range
related:
  - ../domain/publishing.md
  - ../architecture/L3-components.md
  - ../../specs/156-publish-slots-range/spec.md
  - ../../specs/268-song-edit-date-datalist/spec.md
---

# 156 — Расширение диапазона свободных слотов публикации (10 → 22) (LiveDoc)

> Drill-down — [specs/156-publish-slots-range/spec.md](../../specs/156-publish-slots-range/spec.md).
> Фронт-фикс datalist-маскировки (2026-08-30) — см. также
> [specs/268-song-edit-date-datalist/spec.md](../../specs/268-song-edit-date-datalist/spec.md).

## Что делает

В `SongEdit.vue` при пустом поле «Дата публикации» и фокусе отображается
список свободных часовых слотов. Диапазон расширен с **(11:00-17:00)** до
**(10:00-22:00)**.

**Уточнение**: даты в списке — **только в будущем**. Если последняя
публикация в 10:00 была месяц назад, а сейчас 12:00, свободный слот на 10:00 —
**завтрашняя дата**, а не сегодняшняя.

**Уточнение (фронт-фикс 268)**: чтобы datalist со слотами показывался при
фокусе, а не маскировался браузерным автозаполнением, поля «Дата» и «Время»
в `SongEdit.vue` имеют `name="song_date_field"` / `name="song_time_field"` и
`autocomplete="off"`. Другие datalist-поля (поиск авторов и т.п.) не задеты —
у них другой UX-контекст.

## User Stories (краткий список)

- **US1** (P1): Расширенный диапазон 10:00–22:00 для публикаций.
- **US2** (P1): datalist в поле «Дата» и «Время» показывается при фокусе,
  а не подменяется браузерным автокомплитом (фикс 268).

## Functional Requirements (указатель)

- **FR-001**: Константа `PUBLISH_SLOTS_HOURS = listOf(10..22)`.
- **FR-002**: При фокусе на пустом поле дата → автокомплишен только из будущих слотов.
- **FR-003**: Поля «Дата» и «Время» в `SongEdit.vue` имеют уникальный `name`
  и `autocomplete="off"`, чтобы datalist не маскировался браузером
  (фикс 268, см. [specs/268-song-edit-date-datalist/spec.md](../../specs/268-song-edit-date-datalist/spec.md)).

## Acceptance Criteria

- [ ] **AC1**: Фокус на пустом «Дата публикации» → 13 слотов (10:00..22:00).
- [ ] **AC2**: Все слоты — в будущем (не в прошлом).
- [ ] **AC3**: Если последний слот 10:00 был месяц назад, следующий свободный 10:00 — завтра.
- [ ] **AC4** (фикс 268): фокус на поле «Дата» показывает datalist со слотами,
      а не браузерный список автозаполнения; DevTools содержит
      `name="song_date_field"` и `autocomplete="off"` на `<input>`.

## Связанные LiveDocs

- Domain: [publishing.md](../domain/publishing.md) (publish date / slots)
- Architecture: [L3-components.md](../architecture/L3-components.md)

## Код

- Frontend: `webvue3/src/components/Songs/edit/SongEdit.vue` — `<datalist id="list_free_time_slots">` и `<datalist id="list_hours">` + атрибуты `name`/`autocomplete` на полях «Дата»/«Время» (фикс 268)
- Backend: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt` — `getFreeTimeSlots()` (диапазон 10..22, гарантия «только в будущем»)

## История

- Создан: 2026-08-14
- 2026-08-30 — Фронт-фикс datalist-маскировки (см. [specs/268-song-edit-date-datalist/spec.md](../../specs/268-song-edit-date-datalist/spec.md)). Добавлены `name` + `autocomplete="off"` на поля «Дата» и «Время» в `SongEdit.vue`; путь к файлу исправлен на `edit/SongEdit.vue`.
- Последнее обновление: 2026-08-30