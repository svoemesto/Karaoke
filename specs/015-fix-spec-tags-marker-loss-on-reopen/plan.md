# Implementation Plan: Спецтеги — сохранение маркеров после «Точные маркеры → Apply → Save → reopen»

**Branch**: `015-fix-spec-tags-marker-loss-on-reopen` | **Date**: 2026-07-27 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/015-fix-spec-tags-marker-loss-on-reopen/spec.md`

## Summary

Bugfix для `webvue3/src/components/Songs/edit/SubsEdit.vue`: на ПЕРВОМ открытии голоса со спецтегами watcher на `sourceText` срабатывает ДО загрузки `loadedMarkers` в `sourceMarkers` (которая происходит в `ws.on('decode')` — отложенно на момент декодирования аудио). За это время `syncMarkersFromSpecTags()` с пустым `sourceMarkers` вставляет spec tag-маркеры в позицию 0, и условие `sourceMarkers.length === 0` в `ws.on('decode')` перестаёт выполняться — реальные маркеры из БД не загружаются в UI. На Save уезжает мусор, цикл «Apply → Save → reopen» усугубляет потерю. Тот же баг затрагивает переключение голоса (watcher `currentVoice`) — там watcher `sourceText` срабатывает с маркерами ПРЕДЫДУЩЕГО голоса.

Фикс: перенести загрузку `loadedMarkers` в `sourceMarkers` в `mounted()` и в `currentVoice` watcher'е **синхронно ДО** `this.sourceText = await ...`; убрать цикл загрузки из `ws.on('decode')` (или свести к «re-decode»-предохранителю). Опционально: защитные гарды в `syncMarkersFromSpecTags` и `updateMarkersBySyllables`.

## Technical Context

**Language/Version**: Vue 2.7 (Options API, не Composition API; `<script>` без `setup`), JavaScript ES2020, JSDoc (без TypeScript)
**Primary Dependencies**: Vue 2, Vuex 4, Vite, Bootstrap-vue-next, WaveSurfer.js v7 (с плагином Regions), Wavesurfer-Hover, TimelinePlugin, Minimap
**Storage**: N/A (фронтенд; данные о маркерах — в `webvue3/src/components/Songs/store.js` через Vuex-геттеры/экшены, хранение в БД — на стороне karaoke-app/Kotlin, не меняется этим фиксом)
**Testing**: ручная проверка в браузере (по образцу спеки 010 T024, `quickstart.md` этой спеки — сценарии A-G); Vue-юнит-тестов в проекте нет, `karaoke-app` Kotlin-юнит-тесты (`SpecTagsTest.kt`, `WhisperMarkerAlignerSpecTagsTest.kt`) должны проходить без изменений как регрессия
**Target Platform**: Linux server (admin-контейнер `karaoke-app` + `webvue3` через `karaoke-web`), Chrome/Firefox (зависимость от WaveSurfer.js)
**Project Type**: фронтенд-багфикс существующей Vue-компоненты (admin SPA внутри `webvue3`); никаких новых сервисов / API / БД-таблиц
**Performance Goals**: сохранить текущую отзывчивость UI — загрузка `loadedMarkers` (HTTP-запрос к `/api/song/voicesourcemarkers`) и так уже происходит в `mounted()`, перенос в более раннюю точку не добавляет latency. Синхронная запись в `sourceMarkers` после `await` — десятки миллисекунд для типичной песни (N ≤ 200 маркеров), приемлемо.
**Constraints**:
- Никаких изменений за пределами `webvue3/src/components/Songs/edit/SubsEdit.vue` (одна точка файла, ~30 строк фактических правок в худшем случае).
- Никаких изменений backend (`karaoke-app`), `karaoke-web`, `karaoke-public`, `alignment-ml`.
- Никаких изменений контракта `specs/010-lyrics-spec-tags/contracts/tag-registry.md`.
- Сохранить семантику «Точные маркеры + Apply = полная замена маркеров» (FR-008 спеки 015).
- Сохранить совместимость со spec 010 FR-005/FR-006/FR-007 (строго аддитивная `syncMarkersFromSpecTags`).
**Scale/Scope**: один файл, ~30-50 строк diff; один компонент; один баг; затронуты ~2 user story спеки 010 (US1 «first open», US3 «voice switch»)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Self-contained автопайплайн**: PASS. Никаких новых внешних зависимостей. Фронтенд-багфикс в `SubsEdit.vue`, не затрагивает горячий путь обработки аудио/видео.
- **II. Сырой JDBC + дифф по хэшам**: PASS. Не затрагивается (только фронтенд).
- **III. Двух-БД синхронизация**: PASS. Не затрагивается. `tbl_settings.source_markers` не меняется (ни схема, ни запись, ни чтение).
- **IV. Async-очередь**: PASS. Не затрагивается. `KaraokeProcess*` не вовлечены.
- **V. Двух-фронтенд (admin / public)**: PASS. Фикс ТОЛЬКО в `webvue3` (admin). `karaoke-public` (краудсорсинг) и `webvue3/.../useKaraokeEditor.js` (лёгкий admin) не затрагиваются. Семантика фикса — «на ПЕРВОМ открытии маркеры из БД должны попасть в UI до срабатывания watcher'а» — переносима на лёгкий admin (там тоже есть `syncMarkersFromSpecTags` + watcher'ы), но в скоуп этой спеки не входит.
- **VI. Code Standards**:
  - **FR-006 KDoc/JSDoc**: PASS. Все модифицированные функции (`syncMarkersFromSpecTags`, `updateMarkersBySyllables`, `mounted`, `currentVoice` watcher, новые/правленые комментарии) уже имеют или получат JSDoc с пояснениями. Существующие KDoc/JSDoc-блоки в `SubsEdit.vue` для `syncMarkersFromSpecTags` (строки 3407-3411), `updateMarkersBySyllables`, `getProcessedSourceText` (2042-2044), `specTagAnchors` (2055-2058) — должны быть актуализированы под новый порядок вызовов.
  - **FR-007 Linters (ESLint)**: PASS. Правка в `SubsEdit.vue` — обычный Vue/JS, не вводит новых паттернов. Должна проходить `npm run lint:check` без новых baseline-нарушений. Ktlint N/A (только Kotlin, не затрагивается).
  - **FR-009 per-feature документ**: см. `Complexity Tracking` ниже — формально фикс затрагивает подсистему «спецтеги», для которой **не создан** отдельный `docs/features/lyrics-spec-tags.md` (см. спеку 010 `tasks.md` T003 — «механизм спецтегов в [per-feature список] не добавляется как отдельный файл»). Поэтому обновляем **именно** `specs/010-lyrics-spec-tags/spec.md` + его `tasks.md` (T024) вместо per-feature документа. Это и есть «обязательство per-feature документа» в духе FR-009 — для данной подсистемы оно реализовано через `specs/010-lyrics-spec-tags/`, не через `docs/features/`.
- **VII. Cross-Machine Setup**: PASS. Не затрагивается.

**Compliance review (post-design)**: после Phase 1 (data-model + contracts + quickstart) пересмотр — PASS. Все 7 принципов сохраняются.

## Project Structure

### Documentation (this feature)

```text
specs/015-fix-spec-tags-marker-loss-on-reopen/
├── plan.md              # Этот файл
├── research.md          # Phase 0: первопричина + дизайн фикса
├── data-model.md        # Phase 1: sourceMarkers / loadedMarkers / SourceMarker
├── quickstart.md        # Phase 1: сценарии A-G ручной проверки
├── contracts/           # Phase 1: README.md (наследует tag-registry.md из 010)
│   └── README.md
├── checklists/
│   └── requirements.md  # Quality checklist (от /speckit.specify)
├── spec.md              # Спецификация (от /speckit.specify)
└── tasks.md             # Phase 2 (от /speckit.tasks — НЕ создаётся этим планом)
```

### Source Code (repository root)

```text
webvue3/
└── src/
    └── components/
        └── Songs/
            └── edit/
                └── SubsEdit.vue     # ← ЕДИНСТВЕННЫЙ модифицируемый файл (~30-50 строк diff)

# Затронутые секции SubsEdit.vue (см. номера строк из research.md §2):
#   - mounted()                  ~2619-2657  (ШАГ 1 фикса: загрузка маркеров ДО sourceText)
#   - ws.on('decode', handler)   ~2634-2657  (ШАГ 1 фикса: убрать цикл загрузки маркеров)
#   - currentVoice watcher       ~2105-2138  (ШАГ 2 фикса: тот же приём)
#   - syncMarkersFromSpecTags()   ~3412-3466  (ШАГ 3 фикса: опциональный гард)
#   - updateMarkersBySyllables() ~3348-3406  (ШАГ 4 фикса: не обнулять label лишних)
#   - JSDoc-комментарии          ~2042-2058, 3407-3411  (обновить под новый порядок)
```

**Structure Decision**: один файл в одном пакете. Структура `webvue3/` уже существует и хорошо описана в `AGENTS.md`. Никаких новых директорий, никаких новых модулей. Существующие тесты в `karaoke-app/src/test/kotlin/.../model/` (`SpecTagsTest.kt`, `WhisperMarkerAlignerSpecTagsTest.kt`) **не трогаем** — это регрессионная страховка для backend-стороны контракта, которая не меняется.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| _Нет нарушений_ | Все 7 принципов Constitution сохранены. Фикс — bugfix-уровень правка в одном файле, не требует обоснований сложности. | — |

**Дополнительное примечание по FR-009** (per-feature документ): см. Constitution Check выше — фикс в духе FR-009 обновляет `specs/010-lyrics-spec-tags/spec.md` (там же, где живёт «durable-документ» подсистемы спецтегов по решению спеки 010 T003). Отдельный `docs/features/lyrics-spec-tags.md` **не создаётся** — это было бы нарушением решения T003, а не обязательства FR-009 (FR-009 говорит «при правке кода одной из 9 ключевых подсистем», а спецтеги в список 9 НЕ входят; в спеке 010 T003 это явно зафиксировано).
