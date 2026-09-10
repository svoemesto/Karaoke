# Implementation Plan: Сброс фильтра категории альбома при открытии песен конкретного альбома

**Branch**: `363-album-type-filter-reset-on-album-open` | **Date**: 2026-09-10 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/363-album-type-filter-reset-on-album-open/spec.md`

## Summary

Чисто-фронтовая фича для публичного модуля `karaoke-public`. Устраняет баг OpenProject #78: при открытии конкретного альбома автора через `/zakroma/{id}?albumId=Y`, если тип этого альбома скрыт в фильтре категорий (`hiddenAlbumTypes`), страница показывает пустоту. Решение: авто-сброс **только для одного типа** на время просмотра конкретного альбома, через временное состояние в компоненте `ZakromaView.vue`. `localStorage` не трогается. Фильтр-бар скрывается через `v-if="!selectedAlbumId"`.

## Technical Context

**Language/Version**: Vue 2 / JavaScript ES2020+ (как остальной `karaoke-public`)
**Primary Dependencies**: Vuex (для стора `zakroma`), Vue Router (для `selectedAlbumId` из query), `localStorage` (для persistence)
**Storage**: localStorage (`km-zakroma-hidden-album-types`) — read-only в рамках этой фичи
**Testing**: ручные DevTools-сценарии (проект не покрывает unit-тестами публичный фронт)
**Target Platform**: любой современный браузер (Vue 2 поддерживает все evergreen)
**Project Type**: web (frontend-only, второй фронт проекта)
**Performance Goals**: нет — синхронный JS-фильтр, наносекунды
**Constraints**: НЕ менять backend; НЕ менять формат `localStorage`-ключа; НЕ менять публичное API
**Scale/Scope**: 1 файл (`karaoke-public/src/views/ZakromaView.vue`), <30 строк правок

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Комментарий |
|---|---|---|
| I. Self-contained автопайплайн | N/A | Не затрагивается (рендер/пайплайн). |
| II. Сырой JDBC + дифф по хэшам | N/A | Не затрагивается (нет БД-правок). |
| III. Двух-БД синхронизация | N/A | Не затрагивается. |
| IV. Async-очередь задач | N/A | Не затрагивается. |
| V. Двух-фронтенд | Compliant | Правка только в `karaoke-public` (публичный фронт), не трогает `webvue3` (админку). |
| VI. Code Standards | Compliant | KDoc на JSDoc — добавим JSDoc-комментарий к новому computed/method. ESLint + Prettier прогоняются в CI. |
| VII. Cross-Machine Setup | N/A | Не затрагивается. |
| VIII. Секреты и git-гигиена | Compliant | Нет секретов, нет новых env-переменных. |
| IX. Knowledge-first | Compliant | Выполнен pre-flight (см. `spec.md` → Knowledge References): 3 grep-запроса, 6 прочитанных документов. |
| FR-009 (per-feature документ) | Compliant | Scope — single-file fix, нет новой C4 L3-компоненты, per-feature документ НЕ требуется (закреплено в `spec.md` → Assumptions). |

**Result**: pass — все релевантные принципы соблюдены.

## Project Structure

### Documentation (this feature)

```text
specs/363-album-type-filter-reset-on-album-open/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output (пусто — нет API-контрактов)
│   └── .gitkeep
└── tasks.md             # Phase 2 output (created by /speckit.tasks)
```

### Source Code (repository root)

```text
karaoke-public/src/views/ZakromaView.vue   # ← единственный файл правки
```

**Structure Decision**: Web frontend only — правка локализована в одном файле. `webvue3` (админка), `karaoke-web` (backend), `karaoke-app` (engine) не затрагиваются.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

Нет нарушений — секция пустая.

## Phase 0: Research

См. [`research.md`](./research.md).

## Phase 1: Design

См. [`data-model.md`](./data-model.md) + [`quickstart.md`](./quickstart.md).

Contracts: неприменимо (нет внешних API-контрактов — фига чисто фронтовая, данные уже приходят в `zakroma` Vuex-сторе).