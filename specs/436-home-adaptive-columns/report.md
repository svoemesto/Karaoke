# Отчёт по #160 — Адаптивный дизайн главной страницы

> **Спека**: [spec.md](spec.md) | **Ветка**: `436-home-adaptive-columns` |
> **Дата**: 2026-09-23 | **Pass**: 436.

## Задача

OpenProject #160: «Админка, главная страница. Нужно чтобы дизайн страницы был
адаптивный, и если высоты экрана не хватает то размещать кнопки на странице
в 2, 3 и т.п. колонки.»

## Контекст

`webvue3/src/views/HomeView.vue` — главная админ-SPA: ~21 кнопка фоновых операций
+ 2 input в одной вертикальной колонке `max-width: 500px`. Оценка высоты контента
≈1450 px: на экране 1366×768 (доступно ~683 px) требуется прокрутка на несколько
экранов.

## Что сделано

- **`webvue3/src/views/HomeView.vue`** (scoped-стили):
  - `.home-controls`: `display: flex` → `display: block` + `column-count: 1;
    column-gap: 20px` (многоколоночность не работает на flex-контейнере).
  - `.home-controls > * { break-inside: avoid }` — группы кнопок не разрезаются.
  - Media-запросы (по высоте с гейтом по ширине):
    - `min-width: 1024px` и `max-height: 1500px` → `column-count: 2`, `.home { max-width: 1120px }`.
    - `min-width: 1400px` и `max-height: 820px` → `column-count: 3`, `.home { max-width: 1760px }`.
    - `min-width: 1920px` и `max-height: 560px` → `column-count: 4`, `.home { max-width: 2200px }`.
  - Чистый CSS, без JS-resize.
- **`docs/features/admin-home-adaptive-columns.md`** (NEW) — per-feature документ (FR-009).

## Поведение (число колонок)

| Viewport | `columnCount` | Обоснование |
|---|---|---|
| 2560×1800 | 1 | высоты хватает на ~1450 px |
| 1920×1080 | 2 | 1450/2 ≈ 725 ≤ 995 |
| 1920×768 | 3 | 1450/3 ≈ 483 ≤ 683 |
| 1366×768 | 2 | ширина < 1400 (3 колонки не влезают) |
| 390×844 | 1 | ширина < 1024 |

## Проверки

| Проверка | Результат |
|---|---|
| `cd webvue3 && npm run lint:check` | OK (0 errors) |
| `cd webvue3 && npx prettier --check "src/views/HomeView.vue"` | OK |
| `cd webvue3 && npm run build` | OK (vite 7.58s) |
| `check-feature-doc.sh docs/features/admin-home-adaptive-columns.md` | OK |
| `check-spec-issue-link.py` | OK (33/33) |
| `check-jsdoc-coverage.sh` | 98.1% (≥ 50%) |

## Не затронуто

Backend, API, БД, Docker, CI, `karaoke-public` (другой фронтенд), общие стили
`App.vue` / `style.css`.

## Follow-up

- Визуальная приёмка владельцем: DevTools device toolbar, viewport
  2560×1800 / 1920×1080 / 1920×768 / 1366×768 →
  `getComputedStyle(document.querySelector('.home-controls')).columnCount` = 1/2/3/2.
- Деплой на прод — по общему порядку (владелец).
