---
status: Active
slug: 016-fix-spec-tags-marker-loss-on-reopen
related:
  - ../domain/editorial.md
  - ../features/010-lyrics-spec-tags.md
  - ../../specs/016-fix-spec-tags-marker-loss-on-reopen/spec.md
---

# 016 — Spec-tags: сохранение маркеров после «Apply → Save → reopen» (LiveDoc)

> Drill-down — [specs/016-fix-spec-tags-marker-loss-on-reopen/spec.md](../../specs/016-fix-spec-tags-marker-loss-on-reopen/spec.md).

## What it does

Баг-фикс в `webvue3/src/components/Songs/edit/SubsEdit.vue`: при цикле
`Точные маркеры → Apply → Save → close → reopen` на песне со спецтегами
(`~Припев~`, `~newline~`, `~group:N~`, `~comment:текст~`) syllable-маркеры
теряются. Воспроизводится **только** на песнях со спецтегами; песни без
спецтегов ведут себя корректно.

**Первопричина** (асимметрия вызовов `syncMarkersFromSpecTags`):
- При первом открытии (`mounted()`) watcher `sourceText` срабатывает раньше
  `ws.on('decode')`, и `sourceMarkers` ещё `[]` — функция аддитивна, но не
  находит места для вставки.
- При смене голоса — `syncMarkersFromSpecTags()` вызывается явно в конце
  watcher'а, после загрузки `loadedMarkers` в `sourceMarkers`.

**Фикс**: гарантировать вызов `syncMarkersFromSpecTags()` **после**
`ws.on('decode')` (после полной загрузки `loadedMarkers` в `sourceMarkers`)
+ проверить, что Save отправляет в БД **весь** `sourceMarkers` (а не дельту).

## User Stories (краткий список)

- **US1** (P1): на песне со спецтегами цикл `Apply → Save → reopen` сохраняет
  все маркеры `newline`/`setting`/`group` без потерь.
- **US2** (P2): при рассинхроне `sourceSyllables` ↔ syllables-маркеры
  (старый билд) — нет молчаливой очистки `label` маркера в `''`.
- **US3** (P1): регрессия — на песне БЕЗ спецтегов поведение байт-в-байт
  идентично до фикса.

## Functional Requirements (указатель)

- **FR-001..FR-005**: `SubsEdit.vue` — `syncMarkersFromSpecTags` вызывать
  после `ws.on('decode')` и проверить, что Save отправляет полный
  `sourceMarkers`.
- **FR-006**: `loadedMarkers` на reopen совпадает с `sourceMarkers` после
  Save (за вычетом `beat`-маркеров).
- **FR-007..FR-009**: не сломать регрессионный сценарий (песни без
  спецтегов), контракт спецтегов (спека 010), семантику «Точные маркеры
  = полная замена».
- **FR-010**: цикл `Apply → Save → reopen` сохраняет набор маркеров
  (с точностью до `updateMarkersBySyllables`).
- **FR-011**: при рассинхроне — нет молчаливой очистки; стратегия
  (skip vs alert) определяется на этапе plan.

## Acceptance Criteria

- [ ] **AC1** (SC-001): на тестовой песне со спецтегами после
      `Apply → Save → reopen` — `sourceMarkers.length` совпадает ±1
      (за счёт `beat`), а `markertype`+`label`+`time` для `newline`/`setting`
      совпадают с тем, что было после Apply.
- [ ] **AC2** (SC-002): число маркеров с одинаковой `(markertype, label)`
      после reopen ≤ чем после Apply (нет дубликатов).
- [ ] **AC3** (SC-003): на 3-5 песнях БЕЗ спецтегов — байт-в-байт
      идентичный результат.
- [ ] **AC4** (SC-004): на 3-5 песнях СО всеми 4 типами спецтегов — нет
      потерь.
- [ ] **AC5** (SC-005): на сценарии US2 нет молчаливой очистки `label`.

## Связанные LiveDocs

- Domain: [editorial.md](../domain/editorial.md) — редактор маркеров.
- Feature: [010-lyrics-spec-tags.md](../features/010-lyrics-spec-tags.md) —
  контракт спецтегов (грамматика, реестр v1, инварианты; **НЕ меняется**).

## Код

- **Frontend**: `webvue3/src/components/Songs/edit/SubsEdit.vue`
  - `mounted()` строки 2619-2657
  - `ws.on('decode')` строки 2634-2657
  - `syncMarkersFromSpecTags()` строки 3412-3466
  - `applyAutoMarkersToEditor()` строки 4525-4536 (НЕ трогать — by design)
  - `updateMarkersBySyllables()` строки 3371-3375 (FR-004: подозрение)
- **Тесты**: ручные сценарии в `quickstart.md` (по образцу спеки 010,
  сценарии D/E/F).

## Out of scope

- Семантика «Точные маркеры + Apply = полная замена» — by design.
- Лёгкий admin-редактор `SongKaraokeEditorView.vue` и краудсорсинг
  `EditorWorkView.vue` — там нет потока «Apply», баг не воспроизводится.
- Whisper-поток (`doAutoMarkers`) — там маркеры не расставляются.
- Расширение реестра спецтегов — это спека 010.

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14
