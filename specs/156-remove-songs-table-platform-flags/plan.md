# Implementation Plan: Удалить из таблицы «Песни» 18 столбцов-флагов публикации

**Branch**: `156-remove-songs-table-platform-flags` | **Date**: 2026-08-06 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/156-remove-songs-table-platform-flags/spec.md`

## Summary

Удалить из таблицы песен в админке `webvue3` 18 узких столбцов-флагов публикации по платформам (SP/VG/ZL/ZK/ZC/ZM/VL/VK/VC/VM/TL/TK/TC/TM/ML/MK/MC/MM). Изменение чисто UI-уровневое: удаляются 18 объектов из массива `fields[]` в `SongsTable.vue`, 18 ячеек-шаблонов `<template #cell(flagX)>`, 18 CSS-блоков `.fld-flag-*`, 4 метода воспроизведения (`playLyrics/Karaoke/Chords/Tabs`), и 10 определений из Vuex-state `fieldSongParams[]`. Бэкенд, БД, DTO и связанные компоненты (`Publish`, `SongEdit`) **не затрагиваются** — данные продолжают вычисляться и сохраняться как прежде. В том же PR обновляется per-feature документация `docs/features/songs-table.md` (FR-009 Конституции).

## Technical Context

**Language/Version**: Vue 3 + Vite (admin SPA `webvue3`). JavaScript (ES modules), Vue SFC `<script>` блоки. ESLint 8.x baseline.

**Primary Dependencies**:
- `bootstrap-vue-next` (используется `<b-table>` — массив `fields[]` декларирует колонки)
- `vuex` (state, getters, modules)
- `vue-3-icon`-семейство (иконки для бейджей)

**Storage**: Не затрагивается. Данные песен хранятся в PostgreSQL (`tbl_settings` / `tbl_settings_sync`) через сырой JDBC, без изменений.

**Testing**: Ручная проверка через браузер (см. [quickstart.md](./quickstart.md)). CI-проверки: `npm run build`, `npm run lint:check`, ktlint для Kotlin (не затрагивается), JSDoc coverage (не затрагивается, методы удаляются, не добавляются). Существующих unit/integration-тестов нет (per AGENTS.md / constitution § «Тесты»).

**Target Platform**: Браузер (Chrome/Firefox/Safari актуальной версии), desktop-разрешения 1280×800 и выше.

**Project Type**: Web (admin SPA), часть двух-фронтенд архитектуры проекта (admin = `webvue3`, public = `karaoke-public`).

**Performance Goals**: Уменьшение DOM-узлов в таблице (18 × ~10 строк × 18 ячеек = 3240 узлов). Bundle size ↓ ≥ 1KB. Никаких регрессий на время отрисовки.

**Constraints**:
- `table-layout: fixed` + явная `width` на каждой колонке (per CONTRIBUTING.md, AGENTS.md) — НЕ пересчитывать ширины оставшихся колонок.
- Горизонтальный скролл разрешён в админке (per UX-решение проекта).
- Не ломать обратной совместимости (per Constitution § I).

**Scale/Scope**:
- Изменение затрагивает ~250 строк удаления в `SongsTable.vue` + ~140 строк удаления в `store.js`.
- 1 Vue SFC + 1 Vuex store + 1 per-feature документ = 3 файла.
- Не требуется миграций БД или API.

## Constitution Check

*Gate: must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Принцип | Применимость | Статус |
|---|---------|--------------|--------|
| I | Self-contained автопайплайн | N/A — UI-only | ✅ Не затрагивается |
| II | Сырой JDBC + дифф по хэшам | N/A — UI-only | ✅ Не затрагивается |
| III | Двух-БД синхронизация через SyncRegistry | N/A — UI-only | ✅ Не затрагивается |
| IV | Async-очередь задач с парсингом stdout | N/A — UI-only | ✅ Не затрагивается |
| V | Двух-фронтенд: админка и публичный сайт | Применимо | ✅ Изменение только в admin SPA `webvue3`; public SPA `karaoke-public` не затрагивается (флагов публикации там не было, см. grep) |
| VI | Code Standards (FR-006/007/009) | Применимо | ✅ Удаление мёртвого CSS/методов = чистый код. Per-feature документ `docs/features/songs-table.md` обновляется в том же PR (FR-009). KDoc/JSDoc не затрагиваются (методы удаляются, а не добавляются). |
| VII | Cross-Machine Setup | Применимо | ✅ Все изменения в общих файлах (в гите), личных AI-конфигов нет. |
| VIII | Секреты и git-гигиена | N/A | ✅ Не затрагивается — нет секрет-файлов, нет новых хардкодов. |

**Итог**: все применимые принципы соблюдены, нарушений нет. Complexity Tracking не требуется.

## Project Structure

### Documentation (this feature)

```text
specs/156-remove-songs-table-platform-flags/
├── plan.md              # Этот файл (/speckit.plan output)
├── research.md          # Phase 0 output — решения по способу удаления, альтернативам
├── data-model.md        # Phase 1 output — таблица «что в БД / что в state / что в UI»
├── quickstart.md        # Phase 1 output — 8 шагов ручной проверки
├── contracts/           # Phase 1 output — нет изменяемых API-контрактов
│   └── README.md
├── checklists/
│   └── requirements.md  # Quality checklist (создан на фазе /speckit.specify)
└── spec.md              # Feature specification
```

### Source Code (репозиторий)

```text
webvue3/
├── src/
│   ├── components/
│   │   └── Songs/
│   │       ├── SongsTable.vue          ← правка: -18 объектов fields[], -18 <template #cell>, -18 CSS-блоков, -4 метода
│   │       └── store.js                ← правка: -10 объектов fieldSongParams[]
│   ├── store/
│   │   └── modules/                    ← НЕ затрагивается (play*-геттеры остаются)
│   └── components/
│       ├── Publish/                    ← НЕ затрагивается
│       └── Songs/edit/                 ← НЕ затрагивается
└── package.json                        ← НЕ затрагивается

docs/
└── features/
    └── songs-table.md                  ← правка: обновить описание удалённых столбцов (FR-009)

# НЕ затрагиваются:
karaoke-app/        (Kotlin/Spring Boot, бэкенд)
karaoke-web/        (тонкий слой над karaoke-app)
karaoke-public/     (публичный SPA, этих флагов там нет)
deploy/             (Docker-конфигурация, не нужна)
postgres/           (БД, миграций не нужно)
```

**Structure Decision**: Single frontend-project edit (`webvue3`), плюс один файл документации (`docs/features/songs-table.md`). Никаких новых директорий, модулей или архитектурных решений не требуется — это типичный «3-файловый фичефикс» в существующем admin SPA.

## Complexity Tracking

> **Не заполняется.** Constitution Check пройден без нарушений (см. таблицу выше). Никаких оправданий для complexity не требуется.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| — | — | — |

## Артефакты для следующей фазы

После прохождения `/speckit.plan` следующая фаза — `/speckit.tasks` (опционально для маленькой фичи) или сразу реализация через feature-ветку.

**Оценка объёма работы**:
- `SongsTable.vue`: ~250 строк удаления (18 объектов × ~12 строк + 18 шаблонов × ~12 строк + 18 CSS × ~9 строк + 4 метода × ~3 строки = ~466 строк, после минификации ~250)
- `store.js`: ~140 строк удаления (10 объектов × ~14 строк)
- `docs/features/songs-table.md`: ~30 строк правки (удаление упоминаний + запись в changelog)
- `docs/architecture-notes.md`: ~10 строк (запись о PR)

**Итого**: ~430 строк diff, из которых 99% — удаление. Можно выполнить одним коммитом (или 2-3 логически: код, документация, changelog).
