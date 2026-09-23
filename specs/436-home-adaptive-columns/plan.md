# Implementation Plan: Адаптивный дизайн главной страницы админки

**Branch**: `436-home-adaptive-columns` | **Date**: 2026-09-23 | **Spec**: [spec.md](./spec.md)

## Summary

Главная админки (`webvue3/src/views/HomeView.vue`) содержит ~21 кнопку и 2 input
в одной вертикальной колонке шириной 500 px — на низких экранах это «простыня» с
прокруткой. Решение: перевести `.home-controls` на **CSS multi-column**
(`column-count`), переключая 1 → 2 → 3 → 4 колонки media-запросами по
`max-height` (с гейтом по `min-width`). Группы кнопок защищаются
`break-inside: avoid`. Раскладка чисто CSS, без JS-resize.

## Technical Context

**Language/Version**: CSS (Vue 3 SFC scoped-стили), `webvue3`
**Primary Dependencies**: нет новых (нативный CSS multi-column)
**Testing**: автотестов UI в проекте нет — приёмка владельцем визуально
**Constraints**: только `HomeView.vue`; не трогать backend, `karaoke-public`,
общие стили `App.vue`/`style.css`
**Scale/Scope**: 1 файл, ~30 строк CSS

## Constitution Check

- **Principle IX (Knowledge-first)** — pre-flight выполнен (см. spec.md).
- **Tier-1 Hard Gate — Knowledge SSoT** — `.ssot-map.yml` не покрывает
  `webvue3/views/*`; обновляется per-feature doc (FR-009).
- **Tier-1 Hard Gate — Git CI-gate** — ветка + PR + CI.
- **Tier-1 Hard Gate — Machine-Specific** — `nsa-i9`/`nsa`: rebuild ✅, restart ❌.
- **R-375 Frontend** — сборка только `cd webvue3 && npm run ...`.

Нарушений нет.

## Project Structure

```text
webvue3/src/views/HomeView.vue              # MODIFY: scoped-стили (multi-column)
docs/features/admin-home-adaptive-columns.md # NEW: per-feature doc (FR-009)
specs/436-home-adaptive-columns/{spec,plan,tasks,report}.md
```

Бэкенд / БД / Dockerfile / CI / `karaoke-public` — **не затрагиваются**.

## Phase 1 — Design

### Раскладка

| Viewport (пример) | Условие media | `column-count` | Обоснование |
|---|---|---|---|
| 2560×1600 | — | 1 | высоты (1600−85=1515) хватает на ~1450 px контента |
| 1920×1080 | `max-height: 1500` и `min-width: 700` | 2 | 1450/2=725 ≤ 995 |
| 1366×768 | `max-height: 820` и `min-width: 700` | 3 | 1450/3≈483 ≤ 683 |
| 3440×620 | `max-height: 620` и `min-width: 1600` | 4 | 1450/4≈363 ≤ 535 |

Порог 1500 px выбран из оценки высоты одноколоночного контента ≈1450 px
(21 кнопка × 60 px + 2 input + padding групп) плюс запас.

### Ключевые CSS-инварианты

- Контейнер `.home-controls` — **block** с `column-count`, а не flex:
  многоколоночность не работает на flex-контейнере.
- `break-inside: avoid` на всех прямых детях.
- Расширение `.home { max-width }` в каждом breakpoint пропорционально числу
  колонок (иначе `max-width: 500px` схлопнет колонки).
- Базовый (1-колоночный) режим визуально не меняется.

## Phase 2 — Implementation

- [T001] `webvue3/src/views/HomeView.vue`: `.home-controls` → `display: block`,
  `column-count: 1`, `column-gap: 20px`, `> * { break-inside: avoid }`.
- [T002] `webvue3/src/views/HomeView.vue`: media-запросы 2/3/4 колонки +
  `max-width` на `.home`.

## Phase 3 — Verification

- `cd webvue3 && npm run lint:check` / `format:check` / `build`.
- Визуальный замер `getComputedStyle('.home-controls').columnCount` (US1).

## Risks

- **Длинные подписи кнопок** в узких колонках (3–4) переносятся на 2 строки —
  допустимо, `width: 100%` сохраняется.
- **`break-inside: avoid` — hint**, не гарантия: при экстремально низком экране
  группа «Автор» (~404 px) может не влезть даже в 3 колонки и вызовет прокрутку;
  разрыв при этом не происходит.
- **Старые браузеры** без multi-column — деградация к 1 колонке (приемлемо).
