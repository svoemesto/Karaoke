# Implementation Plan: Закрома — корректное визуальное заполнение прогресс-бара

**Branch**: `251-fix-zakroma-progressbar` | **Date**: 2026-08-27 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/251-fix-zakroma-progressbar/spec.md`

## Summary

Баг: в `ZakromaView.vue` при стриме песен автора полоска прогресс-бара визуально заполнена ~30%, хотя `receivedCount / expectedCount` ≈ 85%. Причина — CSS-layout: `.km-stream-bar` имеет `flex: 2 1 240px` без растягивания на всю свободную ширину, а соседний `.km-stream-text` без `min-width: 0` не сжимается → bar получает только остаток ширины контейнера, и `85%` от него выглядят как ~30% от всего блока.

Решение (P1, MVP):
- Заменить `flex: 2 1 240px` на `flex: 1 1 auto` + `min-width: 200px` для `.km-stream-bar`.
- Добавить `.km-stream-text { min-width: 0; }` (разрешает сжатие текста).
- На мобильных (≤ 480px) `.km-stream-bar` занимает 100% ширины после `flex-wrap`.

Plus (P2, drift detection):
- В `useZakromaStreamProgress.js` при получении `done.actualCount` сверять с `expectedCount`; при drift > 5% обновлять `expectedCount` на `actualCount`.

Бэкенд НЕ меняется (`done.actualCount` уже отдаётся, см. `PublicApiController.kt:611`).

## Technical Context

- **Frontend**: Vue 3 + Vite (SFC с `<script>` без setup-script), Bootstrap 5.
- **Backend**: Kotlin / Spring Boot 2.x/3.x — НЕ затрагивается этим планом (см. PublicApiController.kt уже содержит `actualCount` в `done` DTO).
- **Storage**: N/A.
- **Testing**: ручное (тесты в `karaoke-app/src/test` `@Disabled`, см. AGENTS.md). Проверка — пользователем через браузер.
- **Target Platform**: публичный фронтенд (`karaoke-public`) + dev-режим (`npm run dev`).
- **Project Type**: Web SPA (`karaoke-public` — Vue 3).
- **Performance Goals**: без регрессий, фикс < 1 мс на рендер прогресс-блока.
- **Constraints**: только CSS-правки + один watcher; никаких новых API/моделей; без изменения существующих endpoint'ов.

## Constitution Check

*Шлюз перед Phase 0 research. После Phase 1 — перепроверка.*

| Principle | Compliance |
|-----------|------------|
| I. Self-contained автопайплайн | ✅ фикс в UI, не затрагивает pipeline. |
| II. Сырой JDBC + дифф по хэшам | ✅ никаких изменений в БД. |
| III. Двух-БД синхронизация через SyncRegistry | ✅ не затрагивается. |
| IV. Async-очередь задач | ✅ не затрагивается. |
| V. Двух-фронтенд: admin / public | ✅ меняется ТОЛЬКО `karaoke-public`, админка (`webvue3`) не трогается. |
| VI. Code Standards | ✅ обновляется `docs/features/zakroma-stream-progress.md` если требуется. Линтеры — без новых baseline-нарушений (проверим `tools/check-eslint-baseline.sh karaoke-public`). |
| VII. Cross-Machine Setup | ✅ не затрагивается. |
| VIII. Секреты и git-гигиена | ✅ не затрагивается. |

**GATE PASSED** — к Phase 0.

## Project Structure

### Documentation (this feature)

```text
specs/251-fix-zakroma-progressbar/
├── plan.md              # Этот файл
├── spec.md              # Уже создан /speckit.specify
└── checklists/
    └── requirements.md  # Уже создан /speckit.specify
```

`research.md`, `data-model.md`, `contracts/`, `quickstart.md` НЕ создаются — фича слишком мала для отдельных артефактов (только CSS + мелкий watcher; нет ни новых API, ни новых моделей данных, ни новых паттернов).

### Source Code (repository root)

```text
karaoke-public/src/
├── views/
│   └── ZakromaView.vue                 # CSS + inline-style (FR-001…FR-004, FR-008, FR-009)
└── composables/
    └── useZakromaStreamProgress.js     # watcher на done.actualCount (FR-005)

# Бэкенд не меняется (actualCount уже отдаётся в done DTO)
```

**Structure Decision**: Web SPA. Изменения локализованы в двух файлах `karaoke-public`. Никаких изменений в `karaoke-app` или `karaoke-web`.

## Implementation Steps

### Step 1: CSS-layout (P1 MVP)

Файл: `karaoke-public/src/views/ZakromaView.vue`, секция `<style scoped>`.

- `.km-stream-text`: добавить `min-width: 0;` (FR-008).
- `.km-stream-bar`: изменить `flex: 2 1 240px` → `flex: 1 1 auto; min-width: 200px;` (FR-001).
- `.km-stream-bar`: добавить `width: 100%;` для случая `flex-wrap` (FR-003).
- `@media (max-width: 480px)`: убедиться, что `.km-stream-bar` занимает 100% ширины (FR-003). Альтернативно — в общем правиле.

### Step 2: Drift detection (P2)

Файл: `karaoke-public/src/composables/useZakromaStreamProgress.js`.

В обработчике `done`-сообщения (строки ~258-272 уже есть логика):
- Сохранить `actualCount` в `doneActualCount`.
- Вычислить `drift = |actualCount - expectedCount.value| / max(expectedCount.value, 1)`.
- Если `drift > 0.05` и `expectedCount.value > 0` → обновить `expectedCount.value = actualCount` (FR-005).

### Step 3: Гарантия 100% перед скрытием (FR-004)

Файл: `karaoke-public/src/views/ZakromaView.vue`, секция `<script>`.

В методе, который вызывается при `done` (через watcher в store или composable):
- Перед `isStreaming = false` — установить `streamProgress.receivedCount = streamProgress.expectedCount` (принудительно 100%), подождать 200 мс, потом скрыть.

Альтернативно — это уже автоматически работает, потому что `receivedCount` инкрементируется в `useZakromaStreamProgress.js:239` на каждую песню, и на последней песне `receivedCount === expectedCount`. Если после этого не вызывается немедленно `isStreaming = false`, пользователь видит 100%. Проверим на месте.

### Step 4: Проверки

- `cd karaoke-public && npm run lint` — без новых нарушений.
- `cd karaoke-public && npm run build` — успешный билд.
- Визуальная проверка через `npm run dev` на крупном авторе (Машина Времени).

## Complexity Tracking

Нет нарушений Constitution — таблица не требуется.

## Files to Modify

1. `karaoke-public/src/views/ZakromaView.vue` (CSS, ~5 строк)
2. `karaoke-public/src/composables/useZakromaStreamProgress.js` (drift detection, ~10 строк)

Бэкенд НЕ меняется. `docs/` НЕ меняются (фича слишком мала для отдельного feature-документа).
