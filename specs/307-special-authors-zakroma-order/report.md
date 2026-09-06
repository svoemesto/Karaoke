# Спецификация готова (задача №56 — Спец-авторы)

## Статус

- **Задача**: OpenProject #56 «Спец-авторы»
- **Assignee**: ai-agent
- **Статус**: In progress → готова спецификация → ожидает планирования
- **Ветка**: `307-special-authors-zakroma-order` (NNN=307)
- **Спека**: `specs/307-special-authors-zakroma-order/spec.md`
- **Чек-лист**: `specs/307-special-authors-zakroma-order/checklists/requirements.md`

## Суть изменений

Спека фиксирует два связанных требования из задачи №56:

1. **Плашка «Отдельные песни разных авторов» становится ПЕРВОЙ** в сетке `/zakroma`
   (сейчас — последняя через слот `#trailing`).
2. **Новое поле `sort_order` в `tbl_authors`** (INTEGER, default 0) с правилом сортировки:
   - ненулевые `sort_order` идут перед нулевыми;
   - внутри обеих групп — `ORDER BY sort_order ASC, author ASC`.

## Контрактные точки

- `deploy/karaoke-db/46_author_sort_order.sql` — миграция (идемпотентная, `ADD COLUMN IF NOT EXISTS`,
  пересоздание `update_tbl_authors_recordhash`).
- `karaoke-app/.../model/Author.kt` — поле `sortOrder: Int` + аннотация `@KaraokeDbTableField(name="sort_order")`,
  проброс в `AuthorDTO`.
- `karaoke-app/.../model/Author.kt::loadAuthorTilesWithCounts` — `ORDER BY sort_order ASC, author ASC`.
- `karaoke-web/.../dto/AuthorTilePublicDto.kt` — поле `sortOrder: Int = 0`.
- `karaoke-public/src/views/ZakromaView.vue` — спец-плашка переезжает из `<template #trailing>`
  в начало сетки.
- `webvue3/src/components/Authors/AuthorsTable.vue` — редактируемая колонка `sort_order`.
- `docs/features/zakroma-tiles-sort-order.md` — per-feature документ (FR-012, SC-007).

## Что НЕ делаем

- Не меняем серверную пагинацию/сортировку админ-таблицы (FR-009).
- Не трогаем поведение `scope=special` (плашка с папкой остаётся единственным рендером).
- Не инвалидируем `authorsTilesCache` явно — истечёт по TTL (≤60 с) или через `consumeDirty()`.

## Готовность

- ✅ Все 3 ключевых вопроса решены (см. секцию Clarifications в спеке).
- ✅ Чек-лист `requirements.md` — 13/13 пунктов ✅.
- ✅ Миграция SQL описана как контракт-референс, не как HOWTO.
- ✅ API `/authors-tiles` обратно-совместимо (новое поле, не breaking change).
- ✅ После спеки — `/speckit.plan` (или напрямую `/speckit.tasks` для имплементации).

## Следующий шаг

Дождаться одобрения спеки пользователем, затем перейти к `/speckit.tasks` для генерации
плана имплементации (последовательность: миграция → модель → API → фронт публичный → админка →
per-feature документ → тесты).
