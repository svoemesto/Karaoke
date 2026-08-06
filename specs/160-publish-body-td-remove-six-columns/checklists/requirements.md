# Specification Quality Checklist: Упрощение PublishTableBodyTd + полная чистка DTO от processColor*

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-08-06 (обновлён после уточнения про PLAY-кнопки)
**Feature**: [spec.md](./spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs) — спека ссылается на
      конкретные файлы и CSS-классы, потому что это refactoring с явным
      указанием файла пользователем; для такого класса задач это допустимо.
- [x] Focused on user value and business needs — основная user story описывает
      «редактор видит более читаемую ячейку и единый стиль PLAY-кнопок»,
      а не «меняем CSS».
- [x] Written for non-technical stakeholders — сценарии даны языком пользователя
      («админ открыл раздел Публикации»), не «Vue computed properties».
- [x] All mandatory sections completed — User Scenarios, Requirements, Key
      Entities, Success Criteria, Assumptions присутствуют.

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain — спека полностью self-contained.
- [x] Requirements are testable and unambiguous — каждый FR-001…FR-017 указывает
      файл, что именно меняется и как проверить.
- [x] Success criteria are measurable — SC-001…SC-008 имеют конкретные метрики
      (210 px, 1 поле JSON, отсутствие `background-color` в DOM, зелёный CI 7/7).
- [x] Success criteria are technology-agnostic (no implementation details) — см.
      примечание к Content Quality; ссылки на CSS-классы/CSSOM допустимы для
      UI-рефакторинга, остальные критерии измеримы на уровне поведения.
- [x] All acceptance scenarios are defined — 8 сценариев в 4 user stories
      (US1: 3, US2: 1, US3: 2, US4: 3).
- [x] Edge cases are identified — 5 граничных случаев (шапка, серверные
      шаблоны, sync, Publication.kt, Vuex-стейт).
- [x] Scope is clearly bounded — раздел Assumptions перечисляет, что вне скоупа
      (PublishTableHead.vue, Song.kt геттеры, diff-логика, закомментированный
      код в SongsTable.vue, мёртвый геттер `processColorBoostyFiles`).
- [x] Dependencies and assumptions identified — assumptions о sync, ширине,
      совместимости API, тестах задокументированы.

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria — каждый FR
      привязан к одному или нескольким SC (FR-001…FR-009 → SC-001…SC-003;
      FR-010…FR-015 → SC-004, SC-006, SC-008; FR-016…FR-017 → SC-005,
      SC-006, SC-008).
- [x] User scenarios cover primary flows — US1 (заполненная ячейка) +
      US2 (пустая ячейка) + US3 (PLAY-кнопки) + US4 (API payload).
- [x] Feature meets measurable outcomes defined in Success Criteria — все SC
      достижимы при выполнении FR.
- [x] No implementation details leak into specification — implementation
      details (имена файлов, CSS-классы, JSON-ключи) присутствуют только в
      качестве якоря для проверки конкретных требований; общая структура
      спеки остаётся в терминах «что меняется для пользователя/вызывающего».

## Notes

- **Решение по скоупу**: визуальный cleanup в `PublishTableBodyTd.vue` +
  снятие раскраски PLAY-кнопок в `SongEdit.vue` (FR-016/017) +
  чистка DTO от 27 неиспользуемых `processColor*` полей + сохранение
  геттеров и diff-логики в `Song.kt`. Граница явно зафиксирована в
  Assumptions.
- **Готово к `/speckit.plan`**: спека не требует дополнительных
  уточнений, маркеров `[NEEDS CLARIFICATION]` нет, критерии измеримы.
- **Изменение против первоначальной версии спеки** (от 2026-08-06,
  до уточнения про PLAY): из DTO дополнительно удаляются 4 поля
  `processColorMeltLyrics/Karaoke/Chords/Melody`, из `SongEdit.vue`
  снимается раскраска 4 PLAY-кнопок (FR-016/017). Итог: в DTO
  остаётся **ровно 1** поле `processColorPlayerDemo` (вместо 5).
- **Замечание для планирования**: на этапе `/speckit.plan` желательно
  сразу сформировать список точных строк для удаления в `Song.kt:toDTO()`
  (27 строк `processColorX = processColorX,`) и `SongDTO:toDtoDigest()`
  (аналогично), чтобы при реализации не ошибиться в порядке полей.
- **Замечание для tasks**: удаление полей из DTO меняет публичный JSON API.
  Хотя никаких внешних потребителей не найдено (`grep` подтвердил), это
  формально breaking change для тех, кто парсит ответ. Поскольку API
  документирован только внутри команды, риск низкий.