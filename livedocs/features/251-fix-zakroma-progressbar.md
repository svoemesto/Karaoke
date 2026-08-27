---
status: Active
slug: 251-fix-zakroma-progressbar
related:
  - ../architecture/L3-components.md
  - ../../specs/251-fix-zakroma-progressbar/spec.md
  - ../../specs/251-fix-zakroma-progressbar/plan.md
  - 181-zakroma-author-load-progress
  - 239-zakroma-author-songs-batch-render
---

# 251 — Закрома: корректное визуальное заполнение прогресс-бара (LiveDoc)

> Drill-down — [specs/251-fix-zakroma-progressbar/spec.md](../../specs/251-fix-zakroma-progressbar/spec.md),
> [plan.md](../../specs/251-fix-zakroma-progressbar/plan.md).

## Что делает

Исправляет баг визуального заполнения прогресс-бара при стриме песен автора в `ZakromaView`:
числа говорили «Загружаем 2350 из 2485 песен» (~95%), а полоска визуально была заполнена
на ~30-85% от ширины контейнера. Два корня:

1. **CSS-layout.** `.km-stream-progress` использовал `display: flex; flex-wrap: wrap`.
   Длинный неразрывный текст «Загружаем X из Y песен автора Z…» (`max-content` ~620 px)
   не сжимался, и `.km-stream-bar` (`flex: 2 1 240px`) получал только остаток ширины.
   В результате `95% fill` от ширины бара выглядели как ~30% от всего контейнера.
   **Фикс**: 2-row grid — text + «Отмена» на row 1, **bar на ВСЮ ширину** на row 2.
2. **CSS `transition: width 0.2s ease` на `.km-stream-bar-fill`** отставал от чисел.
   Batch-стрим по 50 песен (`PublicApiController.flushEveryNSongs = 50`) + смены
   `expectedCount` (тайл автора → meta → drift correction) — target width прыгал
   скачком, transition не успевал, цифры в тексте шли вперёд, а fill показывал
   промежуточное состояние. **Фикс**: transition убран, fill обновляется мгновенно.

**Plus**: drift correction — если `done.actualCount` расходится с `expectedCount`
> 5% (например, в БД добавились/удалились песни между тайлом и стримом), фронт
обновляет `expectedCount` на фактическое число, чтобы fill доехал до 100%.

## User Stories (краткий список)

- **US1** (P1): Честное визуальное заполнение полоски = `received / expected × 100%`
  от ширины контейнера, синхронно с числами.
- **US2** (P2): Drift correction на `done.actualCount` (FR-005).
- **US3** (P3, отложено): явный процент / цветовая индикация при завершении.

## Functional Requirements (указатель)

- **FR-001..FR-004** — CSS-layout прогресс-бара (2-row grid + убрать transition).
- **FR-005..FR-007** — drift detection на `done.actualCount`.
- **FR-008..FR-009** — text + кнопка «Отмена» sizing (`min-width: 0`).

Полный список — [spec.md](../../specs/251-fix-zakroma-progressbar/spec.md#requirements).

## Acceptance Criteria

- [ ] **AC1**: При `receivedCount / expectedCount = 0.85` визуальная ширина fill равна
      `0.85 × width(.km-stream-bar)` ± 2% (SC-001).
- [ ] **AC2**: При 50% — полоска визуально заполнена ровно наполовину (SC-002).
- [ ] **AC3**: На viewport 1280×800 для «Машины Времени» при 1240/2480 ширина
      `.km-stream-bar` ≥ 60% от ширины контейнера (SC-003).
- [ ] **AC4**: При drift > 5% fill показывает 100% в течение ≤ 500 мс после `done` (SC-004).
- [ ] **AC5**: На мобильном viewport 375×667 fill = 100% после `flex-wrap` (SC-005).
- [ ] **AC6**: Drift correction видна: при расхождении текст показывает актуальное
      число песен (SC-006).
- [ ] **AC7**: `npm run lint` (`karaoke-public`) — 0 warnings.
- [ ] **AC8**: `npm run build` — PASS.
- [ ] **AC9**: `tools/check-eslint-baseline.sh karaoke-public` — 0 новых нарушений.
- [ ] **AC10**: Бэкенд не менялся: `./gradlew :karaoke-web:bootJar` — UP-TO-DATE.

## Связанные LiveDocs

- [181-zakroma-author-load-progress](181-zakroma-author-load-progress.md) — основной
  NDJSON chunked-stream механизм; контракт `meta`/`song`/`done` сообщений.
  **Без изменений** — этот LiveDoc только правит CSS-визуализацию и добавляет
  drift correction поверх существующего `done.actualCount`.
- [239-zakroma-author-songs-batch-render](239-zakroma-author-songs-batch-render.md) —
  batch-рендер песен автора (без per-row readiness/membership запросов). **Без изменений** —
  этот LiveDoc не затрагивает логику рендера списка, только прогресс-метр.
- Architecture: [L3-components.md](../architecture/L3-components.md) — структура
  Vue-компонентов `karaoke-public`, в т.ч. `ZakromaView.vue` и
  `useZakromaStreamProgress.js` composable.

## Код

- `karaoke-public/src/views/ZakromaView.vue` — **CSS**:
  - `.km-stream-progress` → `display: grid; grid-template-columns: 1fr auto;
    grid-template-rows: auto auto`.
  - `.km-stream-bar` → `grid-column: 1 / -1; grid-row: 2` (вся ширина, вторая строка).
  - `.km-stream-text` → `grid-column: 1; grid-row: 1` + `min-width: 0`.
  - `.km-stream-cancel` → `grid-column: 2; grid-row: 1`.
  - `.km-stream-bar-fill` → **убран** `transition: width 0.2s ease`.
- `karaoke-public/src/composables/useZakromaStreamProgress.js` — **drift detection**:
  в обработчике `done`-сообщения при `|actualCount − expectedCount| / expectedCount > 0.05`
  `expectedCount.value` обновляется на `actualCount`. Бэкенд-контракт не меняется
  (`done.actualCount` уже отдаётся в `PublicApiController.kt:611`).

## История

- Создан: 2026-08-27
- Последнее обновление: 2026-08-27
