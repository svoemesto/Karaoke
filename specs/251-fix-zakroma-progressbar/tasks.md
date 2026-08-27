---

description: "Task list for fix-zakroma-progressbar feature"

---

# Tasks: Закрома — корректное визуальное заполнение прогресс-бара

**Input**: Design documents from `/specs/251-fix-zakroma-progressbar/`
**Prerequisites**: plan.md (required), spec.md (required for user stories)

**Tests**: в этом проекте тесты не запрашивались (см. AGENTS.md — тесты `@Disabled` в `karaoke-app/src/test`). Проверка делается пользователем вручную через браузер.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: можно запустить параллельно (разные файлы, нет зависимостей).
- **[Story]**: к какому US относится задача.
- В описании — точные пути к файлам.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: ничего не требуется — фича локализована в одном CSS-блоке + одном composable, без новых модулей/зависимостей.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: прочитать существующий код, чтобы правки были точными.

- [x] T001 Прочитать `karaoke-public/src/views/ZakromaView.vue` строки 879–922 (CSS `.km-stream-*`) — уже сделано при составлении спеки.
- [x] T002 Прочитать `karaoke-public/src/composables/useZakromaStreamProgress.js` строки 200–280 (обработчик `done`) — уже сделано.

**Checkpoint**: foundation готова.

---

## Phase 3: User Story 1 — корректный визуальный прогресс (Priority: P1) 🎯 MVP

**Goal**: `.km-stream-bar` занимает всю свободную ширину → визуальное заполнение = `received/expected`.

**Independent Test**: открыть `/zakroma?author=Машина Времени` в DevTools, дождаться `receivedCount ≈ 1240, expectedCount = 2480`. Измерить `width(.km-stream-bar-fill) / width(.km-stream-bar)` — должно быть ≈ 0.5 ± 0.02.

### Implementation for User Story 1

- [x] T010 [US1] В `karaoke-public/src/views/ZakromaView.vue` (CSS-блок `.km-stream-text`, строки 891–896) добавить `min-width: 0;` для разрешения сжатия текста (FR-008). **Готово без правки** — `min-width: 0` уже присутствует в `.km-stream-text` (строка 895), видимо добавлен в одном из предыдущих проходов.
- [x] T011 [US1] В `karaoke-public/src/views/ZakromaView.vue` (CSS-блок `.km-stream-bar`, строки 897–904) заменить `flex: 2 1 240px` на `flex: 1 1 auto;` + добавить `min-width: 200px;` (FR-001).
- [x] T012 [US1] **ПЕРЕСМОТР**: первый фикс с `flex: 1 1 auto` + `flex: 0 1 auto` не решил проблему — flex-row с длинным `text` (`max-content` ≈ 620 px) не даёт bar'у достаточно места даже при `flex-grow: 1`, потому что text не сжимается без overflow (нет flex `flex-shrink` при свободном месте). Заменено на **2-row grid**: text + «Отмена» на row 1, **bar на ВСЮ ширину на row 2** (`grid-column: 1 / -1`). Подробности в комментарии в `ZakromaView.vue`. Удалён старый mobile-override (больше не нужен, bar уже 100% по умолчанию).

**Checkpoint**: визуальное заполнение теперь = `received/expected` от ширины самого бара; bar занимает всю свободную ширину.

---

## Phase 4: User Story 2 — drift detection (Priority: P2)

**Goal**: при `done.actualCount ≠ expectedCount` (drift > 5%) обновить `expectedCount` на `actualCount`, чтобы полоска доехала до 100%.

**Independent Test**: воспроизвести drift (например, добавить 5 песен в БД между загрузкой тайла и стримом) — полоска показывает 100% в течение ≤ 500 мс после `done`.

### Implementation for User Story 2

- [x] T020 [US2] В `karaoke-public/src/composables/useZakromaStreamProgress.js` (в обработчике `done`-сообщения, ~строка 244–263) добавить проверку drift: `const drift = Math.abs(actualCount - expectedCount.value) / Math.max(expectedCount.value, 1);` и при `drift > 0.05` обновить `expectedCount.value = actualCount` (+ синхронизировать `currentExpectedCount` для метрик) (FR-005).

**Checkpoint**: drift correction работает, полоска не «застревает» на 30–80%.

---

## Phase 5: Verify (обязательная проверка после изменения кода)

**Purpose**: убедиться, что правки не сломали линтеры/сборку.

- [x] T030 Линтер: `cd karaoke-public && npm run lint` — PASS (0 warnings).
- [x] T031 ESLint baseline: `tools/check-eslint-baseline.sh karaoke-public` — PASS (0 текущих, 0 в baseline, новых нарушений нет).
- [x] T032 Production-билд фронта: `cd karaoke-public && npm run build` — PASS (7.92s, ✓ built).
- [x] T033 Backend compile (sanity-check, бэкенд не менялся, но constitution требует): `./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel` — PASS (UP-TO-DATE).
- [x] T034 Backend bootJar (sanity-check): `./gradlew :karaoke-web:bootJar --parallel` — PASS (UP-TO-DATE).
- [ ] T035 Визуальная проверка (пользователем): после применения grid-layout bar должен занимать **100% ширины контейнера**, fill — `received / expected × 100%` от ширины бара = визуально `received / expected × 100%` от всего контейнера. На 98% (2438/2485) полоска заполнена почти целиком, на 50% (1240/2480) — ровно наполовину. ВНИМАНИЕ: dev-сервер нужно перезапустить или сделать hard-reload (Ctrl+Shift+R), чтобы Vite HMR подхватил изменения CSS.

**Checkpoint**: всё PASS → готово к коммиту и PR.

---

## Dependencies & Execution Order

- Phase 1 — нет.
- Phase 2 — нет (только чтение кода).
- Phase 3 (US1) — T010 → T011 → T012 (все в одном файле, последовательно, т.к. CSS-блоки ссылаются друг на друга визуально).
- Phase 4 (US2) — T020 (отдельный файл, может идти параллельно с Phase 3).
- Phase 5 (Verify) — последовательно после T010–T020.

### Parallel Opportunities

- T010, T011, T012 — в одном файле, последовательно (можно сделать одним edit'ом).
- T020 — параллельно с T010–T012 (другой файл).

### Implementation Strategy

**MVP**: Phase 3 (US1, CSS-layout) — решает наблюдаемый баг. Достаточно для релиза.

**Defensive enhancement**: Phase 4 (US2, drift detection) — на случай edge-case с расхождением счётчика. Делается заодно, т.к. изменение в одном composable.

---

## Notes

- Бэкенд НЕ меняется (`done.actualCount` уже отдаётся в `PublicApiController.kt:611`).
- Изменения только в `karaoke-public`. Админка (`webvue3`) не затрагивается.
- US3 (P3, процент/цветовая индикация) — НЕ делается в этой итерации; пользователь явно просил фикс CSS-layout, а не редизайн.
