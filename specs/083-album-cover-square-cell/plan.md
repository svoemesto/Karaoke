# Implementation Plan: Альбомы — квадратная ячейка обложки альбома

**Branch**: `083-album-cover-square-cell` | **Date**: 2026-07-29 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/083-album-cover-square-cell/spec.md`

## Summary

В `webvue3/src/components/Albums/AlbumsTable.vue` сузить колонку `(альбом)` с
125px до 54px (равно текущей высоте строки), чтобы ячейка с обложкой альбома
стала квадратной. Параллельно скорректировать CSS внутри ячейки, чтобы
изображение вписывалось в квадрат 50×50 без обрезки через `object-fit:
contain`. Никаких изменений в бэкенде, публичной части, других таблицах или
компонентах. Клик-логика (`AlbumCoverModal`) и плейсхолдер «Нет изображения»
сохраняются.

## Technical Context

- **Language/Version**:
  - Frontend: Vue 3 + Vite + Node 22 + Vuex + Bootstrap-vue-next.
  - Backend: N/A (фича чисто фронтовая).
- **Primary Dependencies**:
  - `BTable` из `bootstrap-vue-next` (применяет `field.style` к `<col>` через
    слот `table-colgroup`).
  - Никаких новых зависимостей.
- **Storage**: N/A. Фича не затрагивает БД, MinIO, кеш, Vuex.
- **Testing**: тестов в CI нет (см. constitution §Рабочий процесс,
  `docs/features/ci-lint-enforcement.md`). Проверка делается вручную
  на admin-машине по сценариям `quickstart.md`.
- **Target Platform**: браузер (admin SPA `webvue3`); Linux/Windows/macOS
  одинаково.
- **Project Type**: web SPA (frontend-only change в существующем модуле
  `webvue3`).
- **Performance Goals**: N/A. Чисто визуальная правка, не влияет на
  производительность (CSS-парсинг при рендере — миллисекунды).
- **Constraints**:
  - Backward-compat: визуальное изменение одной колонки. Никаких
    изменений DTO, эндпоинтов, сторов, фильтров, URL, роутинга.
  - Никаких изменений `recordhash`-триггеров (фича не затрагивает БД).
  - Никаких изменений в `karaoke-public`, `karaoke-app`, `karaoke-web`.
- **Scale/Scope**:
  - 1 файл: `webvue3/src/components/Albums/AlbumsTable.vue`.
  - ~4 строки правок (см. `research.md` «Сводка изменений»).
  - Затрагивает только админку: раздел «Альбомы» (`/Albums`).

## Constitution Check

*GATE: must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Принцип | Применимость | Статус |
|---|---------|--------------|--------|
| I | Self-contained автопайплайн | Не затрагивает: фича — CSS-правка в одном Vue-компоненте, не в горячем пути обработки медиа | N/A |
| II | Сырой JDBC + дифф по хэшам | Не затрагивает: фича не трогает БД и sync | N/A |
| III | Двух-БД синхронизация | Не затрагивает: фича не трогает `SyncRegistry`, `recordhash`-триггеры, БД-миграции | N/A |
| IV | Async-очередь задач | Не затрагивает: фича не в `KaraokeProcess*` и не в MLT-генераторе | N/A |
| V | Двух-фронтенд | Затрагивает только `webvue3` (admin). Публичный `karaoke-public` НЕ меняется (FR-004). Согласовано с Principle V | PASS |
| VI | Code Standards (FR-006/007/009) | `AlbumsTable.vue` — существующий компонент, `export default` уже с JSDoc-блоком (см. `AlbumsTable.vue:191-203`). Новых публичных API не добавляется → KDoc/JSDoc обновлять не нужно. ESLint пройдёт без новых violations (правки — только в `style` полей и значениях CSS-свойств). `docs/features/` — Albums не входит в 12 ключевых подсистем (`docs/features/README.md`), per-feature документ не требуется (FR-009 не применяется) | PASS |
| VII | Cross-Machine Setup | Не затрагивает | N/A |

**Итог**: все применимые принципы — PASS. Нарушений нет.
`Complexity Tracking` — пустая (нет нарушений, которые нужно обосновывать).

## Project Structure

### Documentation (this feature)

```text
specs/083-album-cover-square-cell/
├── plan.md              # Этот файл (/speckit.plan output)
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output (минимальный — фича не затрагивает data model)
├── quickstart.md        # Phase 1 output: ручные сценарии валидации
├── contracts/           # Phase 1 output: пустая (нет публичных API-контрактов)
├── checklists/
│   └── requirements.md  # Создан /speckit.specify
└── spec.md              # Создан /speckit.specify
```

### Source Code (repository root)

```text
webvue3/src/components/Albums/
└── AlbumsTable.vue                              # ~4 строки правок
```

Файлы вне `AlbumsTable.vue` **не затрагиваются**:

- `webvue3/src/components/Authors/AuthorsTable.vue` — аналогичный паттерн,
  но колонка `(автор)` НЕ квадратится (Q3 в research.md).
- `webvue3/src/components/Albums/filter/AlbumsFilterModal.vue` — фильтр,
  не таблица.
- `webvue3/src/components/Songs/edit/AlbumCoverModal.vue` — модалка,
  открываемая по клику на preview; клик-логика сохраняется (A-003).
- `karaoke-public/**` — публичный сайт, не затрагивается (Q4).
- `karaoke-app/**`, `karaoke-web/**` — бэкенд, не затрагивается.

**Structure Decision**: правки строго точечные, в 1 файле, без новых
модулей/папок/сервисов. CSS-правка одной колонки — это полный объём фичи.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| (нет)     | —          | —                                    |

**Re-evaluation after Phase 1 design**: см. секцию «Re-check после
Phase 1 design» в конце этого файла.

## Сводка артефактов Phase 0 / Phase 1

- **`research.md`** — 4 вопроса: (Q1) высота строки 54px, (Q2) подход
  через `style` поля + CSS внутри ячейки, (Q3) колонка `(автор)` не
  трогается, (Q4) `karaoke-public` не затрагивается. Все resolved.
- **`data-model.md`** — фигуративный, фича не затрагивает data model;
  фиксирует, что `Album.albumPicturePreviewUrl`, `Album.albumType` и
  прочие поля не меняются, а `AlbumDigest` (Vuex) — без изменений.
- **`contracts/`** — пустая. У фичи нет публичных API-контрактов
  (никаких REST/GraphQL/CLI изменений), нет JSON-схем для бэкенда.
  Внутренний контракт «ширина колонки ↔ высота строки» описан в
  `research.md` Q1/Q2.
- **`quickstart.md`** — ручные сценарии валидации end-to-end:
  визуальная проверка квадрата, проверка с обложкой и без, проверка
  регрессии (клик открывает модалку, фильтр/пагинация работают,
  высота остальных строк не изменилась).

## Re-check после Phase 1 design

| # | Принцип | Re-check |
|---|---------|----------|
| V | Двух-фронтенд | PASS — `webvue3` обновлён, `karaoke-public` не затрагивается; `grep -r "AlbumsTable" karaoke-public/` даёт 0 совпадений (проверено в research.md Q4). |
| VI | Code Standards | PASS — JSDoc-блок `AlbumsTable.vue:191-203` остаётся без изменений; новых `export default` нет; CSS-правки внутри `<style scoped>` не требуют JSDoc; ESLint пройдёт (правки — только в `style` полей и значениях CSS-свойств, не структурные). |

**Итог**: фича готова к `/speckit.tasks` (фаза 2 — генерация задач).
