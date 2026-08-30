# Implementation Plan: Краткое отображение тональности в онлайн-плеере

**Branch**: `265-key-short-display` | **Date**: 2026-08-30 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/265-key-short-display/spec.md`

## Summary

Чисто клиентская фича отображения тональности (`data.key`) в коротком формате на экране онлайн-плеера — в публичном (`karaoke-public`) и админском (`webvue3`) фронтендах. Существующий парсер `_parseKey` + хроматическая шкала `CHROMATIC` уже инкапсулируют логику; фича добавляет тонкую обёртку `_shortKey(key)` и переключает 5 точек отрисовки (сплэш публичного плеера, header публичного плеера, сплэш админ-плеера в online и MP4 render режимах, header админ-плеера) с `this.data.key` напрямую на `_shortKey(this.data.key)`. Backend/DB не меняются. Существующее отображение в меню «Тональность» и бейдже transpose (через `_transposeLabel`) остаётся как есть.

## Technical Context

**Language/Version**: JavaScript ES2022+ (vanilla, не TypeScript). Файлы — `KaraokePlayer.js` (3865 / 4007 строк), используется внутри Vue 3 SPA; пишется без stage-3+ синтаксиса. JSDoc-комментарии на русском (инвариант проекта).

**Primary Dependencies**: Vue 3 (`vue@^3`) — для `<script setup>` в `PlayerView.vue`; HTML5 Canvas 2D API — для отрисовки сплэша и header на canvas. Никаких внешних зависимостей для самой фичи — `_parseKey` (существующий) и `CHROMATIC` (существующий) переиспользуются, добавляется только статический helper `_shortKey` в `KaraokePlayer.js`.

**Storage**: N/A. Фича не трогает БД и backend-эндпоинты (playerdata отдаёт `data.key` как и раньше).

**Testing**: ручное, по сценариям в [quickstart.md](./quickstart.md). В проекте нет CI-тестов для UI-фич (см. AGENTS.md, раздел «Тесты»). Линтер: ESLint через `npm run lint` в `webvue3` и `karaoke-public` + `tools/check-eslint-baseline.sh <pkg>` (FR-013, NON-NEGOTIABLE).

**Target Platform**: Современные браузеры с поддержкой ES2022 и HTML5 Canvas: Chrome 90+, Firefox 90+, Safari 14+, Edge 90+; desktop и mobile. Canvas-рендер на retina (devicePixelRatio) уже учтён в `_renderSplash` / `_renderHeader` существующем коде.

**Project Type**: Web — фронтенд-фича с двумя приложениями (Vite/Vue 3 SPA): `karaoke-public` (публичный сайт) и `webvue3` (админка). Backend (`karaoke-app`, `karaoke-web` Kotlin/Spring) НЕ затрагивается.

**Performance Goals**: Микрооптимизация — рендер одной строки на canvas занимает микросекунды. Helper `_shortKey` — однострочная обёртка вокруг `_parseKey` + индекс в массиве из 12 элементов. Никаких дополнительных аллокаций в горячем пути (canvas-рендер не пересоздаёт строки каждый кадр; сплэш и header — статические слои).

**Constraints**:
- ESLint baseline НЕ должен расти (FR-013).
- Backend API и формат `data.key` в БД НЕ меняются (FR-011).
- Меню «Тональность» и бейдж transpose НЕ затрагиваются (FR-009).
- Две копии `KaraokePlayer.js` (`karaoke-public` + `webvue3`) синхронизируются вручную (инвариант «две копии плеера», FR-010).
- Per-feature документ `archive/docs/features/player-transpose.md` обновляется в том же PR (FR-012, Constitution §VI FR-009).
- Helper `_shortKey` НЕ зависит от инстанса (`static`), парсер `_parseKey` — тоже static; согласовано.

**Scale/Scope**: 5 точек отрисовки в 2 файлах + 1 статический helper (по 4 строки в каждой копии плеера, ~8 строк итого) + 1 секция в per-feature документе. Общий объём правок ~15 строк кода + ~30 строк документации. Никаких новых полей, миграций, эндпоинтов.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Принцип | Релевантность | Compliance |
|---------|---------------|------------|
| **I. Self-contained автопайплайн** | N/A — UI-фича, не затрагивает MLT/ffmpeg/Demucs/Sheetsage | N/A |
| **II. Сырой JDBC + дифф по хэшам** | N/A — клиентская фича, нет доступа к БД | N/A |
| **III. Двух-БД синхронизация через SyncRegistry** | N/A — не затрагивает сущности БД | N/A |
| **IV. Async-очередь задач с парсингом stdout** | N/A — нет ProcessBuilder/subprocess | N/A |
| **V. Двух-фронтенд: админка и публичный сайт — разные приложения** | Релевантно — фича затрагивает обе копии `KaraokePlayer.js` в `karaoke-public` (публичный) и `webvue3` (админка). Копии синхронизируются вручную (FR-010). Изменения НЕ смешивают ответственности — helper `_shortKey` идентичен в обеих копиях | ✓ PASS |
| **VI. Code Standards (NON-NEGOTIABLE)** | Релевантно — обязательная проверка ESLint + baseline (FR-013, §VI FR-007); per-feature документ `archive/docs/features/player-transpose.md` MUST быть обновлён в том же PR (FR-012, §VI FR-009). Новый static-helper получит JSDoc-комментарий с `@see`-ссылкой на per-feature документ (§VI FR-006). Baseline `webvue3/.eslint-baseline.json` и `karaoke-public/.eslint-baseline.json` НЕ должны расти | ✓ PASS |
| **VII. Cross-Machine Setup (NON-NEGOTIABLE)** | Частично — фича пишет JS-файлы; `.gitattributes` нормализует LF, `.git-blame-ignore-revs` фильтрует рефакторинги (фича не такая). Новый код НЕ содержит захардкоженных секретов (§VII.1, §VIII.5) | ✓ PASS |
| **VIII. Секреты и git-гигиена (NON-NEGOTIABLE)** | N/A — фича не трогает `.env`, `.key`, `.pem` (§VIII.2). Pre-commit проверка `git ls-files | grep -iE '\.env$|do\.env$|\.key$|\.pem$'` MUST быть пусто (уже истина для текущей ветки) | ✓ PASS |

**Результат**: Все применимые принципы PASS. Нарушений нет. Complexity Tracking пустая.

### Post-Design Re-evaluation (после Phase 1)

*GATE: Re-check после Phase 1 design.*

Дизайн (`research.md` + `data-model.md` + `contracts/key-display-contract.md`) подтверждает pre-research оценку:

- **§V Двух-фронтенд**: helper `_shortKey` идентично в обеих копиях (`research.md` D1, D2, D3); per-feature документ обновляется для обеих одновременно.
- **§VI Code Standards**: helper получит JSDoc с `@see`-ссылкой на `archive/docs/features/player-transpose.md` (FR-006); per-feature документ обновляется в том же PR (FR-012, §VI FR-009); baseline ESLint не растёт (FR-013, §VI FR-007).
- **§VII/VIII Cross-Machine и Secrets**: фича не вводит новых зависимостей, секретов, line-ending проблем (JS-файлы, LF уже нормализован `.gitattributes`).

**Все 8 принципов остаются PASS.** Нарушений нет. Complexity Tracking пустая.

## Project Structure

### Documentation (this feature)

```text
specs/265-key-short-display/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/
│   └── key-display-contract.md  # Phase 1 output — UI contract для `_shortKey`
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

Фича затрагивает следующие файлы:

```text
karaoke-public/src/player/KaraokePlayer.js   # Публичный плеер: +_shortKey helper, правки 2 точек (сплэш + header)
webvue3/src/player/KaraokePlayer.js           # Админский плеер: +_shortKey helper, правки 3 точек (сплэш online, сплэш MP4, header)
archive/docs/features/player-transpose.md     # Per-feature документ: +раздел "Короткое отображение тональности" (FR-012)
```

**Structure Decision**: Option 2 (Web application). Проект уже структурирован как multi-module Gradle (`karaoke-app`, `karaoke-web`, `karaoke-db`) + два независимых Vite-приложения (`webvue3`, `karaoke-public`). Фича правит только фронтенд; backend-модули (`karaoke-app/`, `karaoke-web/`, `karaoke-db/`) НЕ затрагиваются. Никаких новых директорий или подмодулей не создаётся.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| (нет) | — | — |

Constitution Check прошёл без нарушений. Никаких сложных альтернатив не рассматривалось — фича узкая, повторное использование существующих компонентов (`_parseKey`, `CHROMATIC`), минимум нового кода.