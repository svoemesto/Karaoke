# Адаптивная раскладка главной страницы админки

> **Status**: active
> **Feature Key**: admin-home-adaptive-columns
> **Last Updated**: 2026-09-23

## Что делает

Главная страница админ-SPA `webvue3` (`/`, `HomeView.vue`) содержит ~21 кнопку
фоновых операций и 2 input-поля. Если по высоте экрана контент не помещается,
раскладка автоматически переключается с одной колонки на 2, 3 или 4 — так, чтобы
блок действий занимал меньше вертикали и не требовал длинной прокрутки.

## Зачем

До #160 все элементы шли одной вертикальной «простыней» в колонке шириной 500 px.
На типичном ноутбуке 1366×768 это требовало прокрутки на несколько экранов.
Адаптивная многоколоночность использует свободную ширину широких экранов, чтобы
сократить высоту.

## Как работает

`.home-controls` — **block**-контейнер с CSS `column-count` (по умолчанию `1`,
`column-gap: 20px`). Каждый прямой ребёнок получает `break-inside: avoid`, поэтому
группа кнопок (`.field-and-buttons-wrapper`) не разрезается между колонками.

Media-запросы включают многоколоночность по **высоте** с гейтом по **ширине**:

| Условие (min-width / max-height) | `column-count` | `.home { max-width }` |
| --- | --- | --- |
| базовый режим | 1 | 500 px |
| ≥ 1024 px / ≤ 1500 px | 2 | 1120 px |
| ≥ 1400 px / ≤ 820 px | 3 | 1760 px |
| ≥ 1920 px / ≤ 560 px | 4 | 2200 px |

Изменения — только в scoped-стилях `webvue3/src/views/HomeView.vue`. JS-resize
не используется: раскладка пересчитывается браузером при resize/повороте.

**Оценка высоты контента**: ~1450 px (21 кнопка × 60 px + input + padding групп) —
отсюда порог `max-height: 1500px` для перехода на 2 колонки.

## Инварианты / правила

- **MUST**: многоколоночность не работает на flex-контейнере — `.home-controls`
  остаётся `display: block` (не `flex`).
- **MUST**: каждый прямой ребёнок `.home-controls` имеет `break-inside: avoid`.
- **MUST**: расширять `.home { max-width }` синхронно с `column-count`, иначе
  базовый `max-width: 500px` схлопнет колонки.
- **MUST NOT**: трогать `karaoke-public` (другой фронтенд, ловушка №5 из
  `knowledge/guidelines/architecture-conventions.md`) и общие стили
  `App.vue` / `style.css`.
- **SHOULD**: добавлять breakpoints только при изменении фактической высоты
  контента (оценка ~1450 px), иначе раскладка разъедется.

## Известные ловушки

- **`width: 500px` в базовом `.home-controls`** перекрывал бы многоколоночность —
  в media-запросах он заменяется на `width: 100%`.
- **Bootstrap-таблица размеров (`box-sizing`)** — при расчёте `max-width` под
  колонки закладывается запас ~20 px на `column-gap` (20 px) и padding контейнера.
- **`break-inside: avoid` — лишь hint**: на экстремально низком viewport группа
  «Автор» (~404 px) может не влезть даже в 3 колонки и вызовет прокрутку, но
  разрыва группы не произойдёт.
- **Автотестов UI в проекте нет** — приёмка визуальная (DevTools device toolbar,
  `getComputedStyle('.home-controls').columnCount`).

## Ссылки на ключевые классы/файлы

- [`webvue3/src/views/HomeView.vue`](../../webvue3/src/views/HomeView.vue) —
  `.home`, `.home-controls`, media-запросы.
- [`specs/436-home-adaptive-columns/spec.md`](../../specs/436-home-adaptive-columns/spec.md) —
  спека (OpenProject #160).
- [`specs/436-home-adaptive-columns/plan.md`](../../specs/436-home-adaptive-columns/plan.md) —
  план реализации.
- [`knowledge/system/frontend/webvue3-views-detailed.md`](../../knowledge/system/frontend/webvue3-views-detailed.md) —
  HomeView как dashboard-контейнер.

## История версий

- **v1.0** (2026-09-23, Pass 436, #160) — CSS multi-column раскладка 1/2/3/4.
