---
status: Active
slug: 268-song-edit-date-datalist
related:
  - ../domain/publishing.md
  - ../architecture/L3-components.md
  - ../../specs/268-song-edit-date-datalist/spec.md
  - 156-publish-slots-range.md
---

# 268 — Возврат выпадающего списка свободных слотов публикации в поле «Дата» (`SongEdit.vue`) (LiveDoc)

> Drill-down — [specs/268-song-edit-date-datalist/spec.md](../../specs/268-song-edit-date-datalist/spec.md).
> Связано с фичей [156 — расширение диапазона свободных слотов публикации](156-publish-slots-range.md)
> (это её UI-потребитель): фронт-фикс 268 закрывает регресс отображения `<datalist>`,
> который возник независимо от серверного диапазона 10–22.

## Что делает

В `SongEdit.vue` (карточка редактирования песни) поля «Дата» и «Время»
привязаны к `<datalist>` через атрибут `list`. После внедрения других полей
формы Chrome/Edge/Firefox стали **подменять** datalist собственным
автокомплитом (история заполнения поля) при фокусе — список свободных
слотов публикации (`freeTimeSlots`) и список часов (`hours`) перестали
быть видны администратору.

**Фикс**: на оба `<input>` добавлены `name` (уникальный в пределах
страницы) и `autocomplete="off"`. Это рекомендованный MDN-способ
подавить браузерный автокомплит на конкретном поле, не ломая
его в остальных полях формы.

**Что не меняется**: API `POST /api/getfreetimeslots`, формат
`tbl_songs.publish_date`/`publish_time`, формат `song.date` в UI
(`dd.MM.yy HH:mm`), другие datalist-поля (поиск авторов и т.п.) —
у них другой UX-контекст, автокомплит там либо полезен, либо
не конфликтует с datalist.

## User Stories (краткий список)

- **US1** (P1): Администратор при фокусе на поле «Дата» видит datalist
  со слотами публикации (10:00–22:00), а не браузерный автокомплит.
- **US2** (P2): Поле «Время» показывает фиксированный список часов
  (`11:00`..`16:00`) при фокусе, без браузерной подмешки.

## Functional Requirements (указатель)

- **FR-001**: При фокусе на пустом поле «Дата» отображается выпадающий
  список из `<datalist id="list_free_time_slots">`, а не браузерный
  список автозаполнения.
- **FR-002**: Атрибут `name="song_date_field"` уникален и явно задан.
- **FR-003**: Атрибут `autocomplete="off"` подан на оба поля.
- **FR-004**: То же поведение для поля «Время» (`name="song_time_field"`).

## Acceptance Criteria

- [ ] **AC1**: DevTools на `<input>` поля «Дата» содержит
      `name="song_date_field"` и `autocomplete="off"`.
- [ ] **AC2**: DevTools на `<input>` поля «Время» содержит
      `name="song_time_field"` и `autocomplete="off"`.
- [ ] **AC3**: Фокус на пустом поле «Дата» → выпадающий список из ≤13
      значений формата `dd.MM.yy HH:mm`, без браузерного списка
      «Предлагать заполнение поля».
- [ ] **AC4**: Фокус на пустом поле «Время» → ровно 6 значений
      `11:00`..`16:00`, без браузерного автокомплита.
- [ ] **AC5**: При наборе символов в поле «Дата» datalist фильтруется,
      браузерная история не подмешивается.
- [ ] **AC6**: Проверено в Chrome 120+, Firefox 120+, Safari 17+ (если доступны).

## Связанные LiveDocs

- Domain: [publishing.md](../domain/publishing.md) (publish date / slots)
- Architecture: [L3-components.md](../architecture/L3-components.md)
- [156-publish-slots-range.md](156-publish-slots-range.md) — серверная
  часть (диапазон 10–22 + гарантия «только в будущем»)

## Код

- Frontend: `webvue3/src/components/Songs/edit/SongEdit.vue` — поля
  «Дата» (строка ~342) и «Время» (~369) с `name` + `autocomplete="off"`.
  Datalist-определения остались прежними (`<datalist id="list_free_time_slots">`,
  `<datalist id="list_hours">`).
- Backend: без изменений (см. [156-publish-slots-range](156-publish-slots-range.md)).

## История

- Создан: 2026-08-30
- Последнее обновление: 2026-08-30