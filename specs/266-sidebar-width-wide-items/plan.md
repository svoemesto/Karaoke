# Implementation Plan: Расширение левого меню админки webvue3

**Branch**: `266-sidebar-width-wide-items` | **Date**: 2026-08-30 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/266-sidebar-width-wide-items/spec.md`

## Summary

Увеличить фиксированную ширину левой колонки навигации (`.app-sidebar`) в админке `webvue3` с **190px до 240px**, чтобы все 26 пунктов меню (включая самые длинные: «Sponsr-синхронизация», «История прослушиваний», «Шаблоны публикаций») и три бейджа («Авторы», «Чат», «Задания редактора») полностью помещались внутри серой области сайдбара и не «висели в воздухе» над основным контейнером.

**Технический подход**: одна строка CSS-правки в `webvue3/src/App.vue:728` (`.app-sidebar { width: 190px → 240px; }`) + обновление комментария. Никаких изменений в template/script, никаких изменений в backend, никаких миграций, никаких новых зависимостей.

## Technical Context

**Language/Version**: Vue 3 + Vite (admin: `webvue3/`)
**Primary Dependencies**: BootstrapVueNext (для `BApp`, `BToast` и др.), Vue Router 4. CSS — обычные global-стили (не CSS modules / не Tailwind / не SCSS — чистый CSS внутри `<style>` блока `App.vue`).
**Storage**: N/A (косметика CSS, данные не затрагиваются)
**Testing**: визуальная проверка скриншотом (Pass 244 — тестов в проекте нет, проверка пользователем)
**Target Platform**: десктопный браузер, ширина окна ≥1024px (мобильный режим не в скоупе)
**Project Type**: frontend-only (admin)
**Performance Goals**: N/A (правка статической ширины не влияет на производительность)
**Constraints**: основной контейнер `.app-main-content` должен оставаться ≥1000px на 1280px-экранах (т.е. ширина сайдбара ≤280px)
**Scale/Scope**: один CSS-блок, один файл (`App.vue`), ~2 строки diff

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Принцип (`.specify/memory/constitution.md`) | Соблюдён? | Комментарий |
|---|---|---|
| **NON-NEGOTIABLE: язык общения — русский** | ✅ | Все комментарии и спецификация на русском |
| **Backend compile после правок** | N/A | Backend не затрагивается |
| **ktlint** | N/A | Kotlin не затрагивается |
| **Frontend lint (ESLint + Prettier)** | ✅ | Будут запущены в Phase 2 |
| **KDoc / JSDoc coverage ≥50%** | ✅ | Правка — одна строка CSS, KDoc/JSDoc не требуются |
| **Pre-commit хуки (7 проверок)** | ✅ | Будут запущены в Phase 2 (но фактически только lint/format для App.vue) |
| **CI 7/7 PASS** | ✅ | Только frontend-проверки (webvue3 lint/format/build + Docker build_webvue3) |
| **LiveDoc обновление при смене BC/C4** | ✅ | BC не меняется, LiveDoc можно не создавать (см. FR-008 спеки) |
| **Сырой JDBC + recordhash** | N/A | БД не затрагивается |
| **Без `nginx:alpine` / `node:latest`** | ✅ | Docker-образы не меняются |
| **Git workflow: ветка + PR + CI** | ✅ | Будет создана ветка `266-sidebar-width-wide-items`, PR через `gh` |
| **Docker multi-stage (Pass 245)** | ✅ | После Vite-build ОБЯЗАТЕЛЬНО `bash do.sh build_webvue3` — изменения в `App.vue` попадают в `COPY ./webvue3/` контекст |

**Gates PASSED**. Все обязательные проверки выполнимы в рамках существующего workflow.

## Project Structure

### Documentation (this feature)

```text
specs/266-sidebar-width-wide-items/
├── plan.md              # Этот файл (/speckit.plan command output)
├── spec.md              # Уже создан на этапе /speckit.specify
├── research.md          # Phase 0 — N/A (фича тривиальная)
├── data-model.md        # Phase 1 — N/A (нет data-модели)
├── quickstart.md        # Phase 1 — N/A (нет runnable-примера)
├── contracts/           # Phase 1 — N/A (нет API-контрактов)
└── tasks.md             # Phase 2 (/speckit.tasks command output)
```

**Обоснование N/A**: фича — одна строка CSS, не требует research (нет новых технологий), не имеет data-модели (нет данных), не имеет контрактов (нет API), не требует quickstart (нет нового dev-flow). Все решения зафиксированы в `spec.md` (FR-001..008, SC-001..007).

### Source Code (repository root)

Файлы, которые будут изменены:

```text
webvue3/
└── src/
    └── App.vue              # Правка одной строки CSS (line 728) + комментарий
```

Файлы, которые НЕ будут затронуты (для проверки):

```text
webvue3/src/
├── components/...           # 26 пунктов меню + 3 бейджа — без изменений
├── router/index.js          # Роуты — без изменений
├── store/                   # Vuex-стор (счётчики бейджей) — без изменений
├── views/...                # 26 view-компонентов — без изменений
└── style.css                # Глобальные стили — без изменений
```

Backend / Karaoke-public — без изменений.

**Structure Decision**: Single project (`webvue3` — admin frontend). Правка локализована в одном файле (`App.vue`).

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| (нет нарушений) | — | — |

**Complexity**: trivial. Фича не вводит новых абстракций, не требует новых зависимостей, не затрагивает несколько файлов. Правка может быть выполнена одним коммитом.

## Implementation Phases

### Phase 0: Research — N/A

Фича не требует research:
- Технология не новая (Vue 3 CSS — уже используется в проекте)
- Нет внешних зависимостей для изучения
- Нет новых паттернов для исследования
- Все стили и поведение уже задокументированы в существующем `App.vue` (см. спеку, секция «Контекст»)

### Phase 1: Design — N/A

Фича не требует design:
- Нет новых компонентов
- Нет новых API-эндпоинтов
- Нет новых data-моделей
- Нет изменений в архитектуре

Дизайн-решение зафиксировано в спеке:
- `width: 190px → 240px` (FR-001 спеки)
- Все остальные стили сохраняются (FR-002..006 спеки)

### Phase 2: Tasks

Список задач для `/speckit.tasks` — отдельный файл `tasks.md` (см. следующий этап).

## Risks & Mitigations

| Риск | Вероятность | Митигация |
|---|---|---|
| Новая ширина 240px сломает таблицы на узких экранах (<1024px) | Low | `.app-main-content` имеет `flex: 1` и `overflow: auto` (`App.vue:818-822`) — основной контейнер сжимается, таблицы получают горизонтальный скролл внутри себя (текущее поведение). SC-002/003 спеки валидируют ≥1000px на 1280px и ≥750px на 1024px. |
| Бейдж станет трёхзначным и не поместится | Very Low | Текущий лимит `submittedAssignmentsCount` обычно <100 (Pass 245 — UI показывает десятки). Если понадобится — увеличение ширины однострочное. Edge case зафиксирован в спеке. |
| Правка попадёт в Docker multi-stage сборку и упадёт | Very Low | Dockerfile копирует `COPY ./webvue3/` (`deploy/karaoke-webvue3/Dockerfile`), наш `App.vue` внутри. Vite-build ≠ Docker-образ (Pass 245), но в данном случае правка тривиальная — риск минимален. Всё равно запустим `bash do.sh build_webvue3` для гарантии (FR/SC из спеки). |
| CI упадёт на prettier/ESLint | Low | Правка только в существующем стилевом блоке, формат соответствует. Запустим `npm run lint` и `npx prettier --check` перед commit. |

## Pre-Implementation Checklist

Перед началом `/speckit.tasks` подтверждаю:

- [x] Спецификация `spec.md` создана и одобрена пользователем (явное «Делай сразу /speckit.plan + /speckit.tasks + /speckit.implement»).
- [x] Технический контекст изучен (Vue 3, BootstrapVueNext, raw CSS, single-file App.vue).
- [x] Constitution gates пройдены (все применимые — да, неприменимые — N/A).
- [x] Project structure определён (один файл `App.vue`).
- [x] Complexity tracking пуст (фича trivial).
- [x] Risks/mitigations задокументированы.
- [x] Phase 0/1 = N/A (зафиксировано).

**Готов к Phase 2 (`/speckit.tasks`)**.
