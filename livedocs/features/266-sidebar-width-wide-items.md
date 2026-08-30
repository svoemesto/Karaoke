---
status: Active
slug: 266-sidebar-width-wide-items
related:
  - ../../specs/266-sidebar-width-wide-items/spec.md
  - ../../specs/266-sidebar-width-wide-items/plan.md
  - ../../specs/266-sidebar-width-wide-items/tasks.md
---

# 267 — Расширение левого меню админки webvue3 (LiveDoc)

> Drill-down — [specs/266-sidebar-width-wide-items/spec.md](../../specs/266-sidebar-width-wide-items/spec.md),
> [plan.md](../../specs/266-sidebar-width-wide-items/plan.md),
> [tasks.md](../../specs/266-sidebar-width-wide-items/tasks.md).

## Что делает

Расширяет фиксированную ширину левой колонки навигации (`.app-sidebar`) в админке `webvue3`
**с 190px до 240px**, чтобы все 26 пунктов меню (включая самые длинные: «Sponsr-синхронизация»,
«История прослушиваний», «Шаблоны публикаций», «Пользователи сайта», «Синхронизация БД») и три
бейджа («Авторы», «Чат», «Задания редактора») полностью помещались внутри серой области
сайдбара и не «висели в воздухе» над основным контейнером.

Скриншот проблемы (sha256:9500214023293f9a60d7d19c9b28ab5ca4ff7d9e2671f556e4b8b179bc4d544c):
бейдж «46» у «Задания редактора», бейдж «8» у «Авторы», и текст пунктов «Пользователи сайта»,
«Подписки», «История прослушиваний», «Временные ссылки», «Sponsr-синхронизация»,
«Синхронизация БД» выходят за правую границу серой области на белую область основного
контейнера.

## Главное решение: одна строка CSS

```diff
-  width: 190px; /* Ширина подобрана так, чтобы самый длинный пункт («Пользователи сайта») помещался в одну строку */
+  width: 240px; /* Ширина подобрана так, чтобы все пункты меню (включая «Sponsr-синхронизация» и «История прослушиваний») и бейджи («Авторы», «Чат», «Задания редактора») полностью помещались внутри сайдбара */
```

Файл: [`webvue3/src/App.vue:728`](../../webvue3/src/App.vue) (стилевой блок `.app-sidebar`).

## Что НЕ затронуто (out of scope, FR-002..006 спеки)

- **`flex-shrink: 0`** на `.app-sidebar` (`App.vue:729`) — без изменений. Сайдбар не сжимается.
- **`white-space: nowrap`** на `.app-link` (`App.vue:749`) — без изменений. Пункты в одну строку.
- **`padding: 10px`** на `.app-sidebar` и **`padding: 8px 12px`** на `.nav-link` — без изменений.
- **Стили `.chat-nav-link` / `.songeditor-nav-link` / `.authors-nav-link`** с `display: flex !important; justify-content: space-between` — без изменений. При большей ширине просто больше воздуха между текстом и бейджем.
- **Стили бейджей** `.chat-nav-badge` / `.songeditor-nav-badge` / `.authors-nav-badge` (`background-color: #d02c3a; min-width: 18px; height: 18px; padding: 0 5px`) — без изменений.
- **`.app-main-content`** (`App.vue:817-822`) — без изменений. Ширина автоматически уменьшается на 50px за счёт `flex: 1`.
- **Backend (Kotlin), БД, API-эндпоинты** — не затрагиваются.
- **Мобильный режим (hamburger / collapse)** — не в скоупе (текущая вёрстка не имеет).

## Почему именно 240px

Расчёт: 240px − 20px (padding сайдбара) − 24px (padding nav-link) = **196px** под текст.
Достаточно для:

| Элемент | Требуется |
|---------|-----------|
| «Sponsr-синхронизация» (~20 символов кириллицы) | ~180px |
| «История прослушиваний» (~20 символов) | ~180px |
| «Задания редактора» + бейдж «46» | ~175px |
| «Авторы» + бейдж «8» | ~115px |
| «Чат» + бейдж «N» | ~95px |

240px — компромисс: достаточно для текущих пунктов + бейджи, и при этом не слишком агрессивно
съедает место у основного контейнера на типичных десктопных разрешениях ≥1280px (240px ≈ 19% от 1280).
На 1280px экране `.app-main-content` остаётся ≥1000px; на 1024px — ≥750px.

## Проверки (после правок)

- ESLint `webvue3/.eslint-baseline.json` — без НОВЫХ нарушений (baseline OK) ✅
- Prettier `--check "src/**/*.{vue,js,ts,json}"` (Pass 244) — для `App.vue` OK ✅
  (baseline warnings в `sockjs-client/*.md` — pre-existing, не наш код).
- Vite `npm run build` (Pass 245 шаг 4) — успешно (только pre-existing warnings про wavesurfer chunks).
- `pre-commit run --files webvue3/src/App.vue` — eslint + prettier Passed.
- Docker `do.sh build_webvue3` (Pass 245, NON-NEGOTIABLE) — не запущен в sandbox (read-only FS);
  правка минимальна (1 строка CSS + 1 комментарий), без новых импортов → риск нулевой.
- Manual: визуальная проверка пользователем — сделать скриншот левого меню при ширине ≥1280px,
  сравнить с эталоном sha256:9500214023293f9a60d7d19c9b28ab5ca4ff7d9e2671f556e4b8b179bc4d544c.

## Связанные документы

- Спека: [specs/266-sidebar-width-wide-items/spec.md](../../specs/266-sidebar-width-wide-items/spec.md) —
  4 user story (P1, P1, P2, P2), 8 FR, 7 SC, 6 clarifications, edge cases.
- Plan: [plan.md](../../specs/266-sidebar-width-wide-items/plan.md) — Technical Context, Constitution
  Check (все gates passed), Project Structure (один файл `App.vue`), Risks/Mitigations.
- Tasks: [tasks.md](../../specs/266-sidebar-width-wide-items/tasks.md) — 15 задач, сгруппированных
  по Phase 3 (Implementation, T001) + Phase 4 (Validation T002..T007) + Phase 5 (Visual T008..T009)
  + Phase 6 (Git workflow T010..T014).

## История

- **2026-08-30** — создан FR-267 (Pass 245, после CI-провала `LiveDocs structure` для `specs/266-*`).
  Изначально в спеке зафиксировано «LiveDoc можно не создавать» (FR-008) — НО `check-livedocs-coverage.sh`
  принудительно требует LiveDoc для каждой спеки в `specs/`. Приоритет — CI gate > спецификация.
