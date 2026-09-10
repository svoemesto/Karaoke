# Implementation Plan: Исправить «Element not found» в компоненте «Статистика» (Issue #79)

**Branch**: `362-fix-stats-view-element-not-found` | **Date**: 2026-09-10 | **Spec**: [`spec.md`](./spec.md)

**Input**: Feature specification from `/specs/362-fix-stats-view-element-not-found/spec.md`

## Summary

Устранить race condition между apexcharts (vue3-apexcharts) и 11 параллельными
HTTP-запросами в `webvue3/src/views/StatsView.vue:mounted() → reloadAll()`,
который приводит к ошибке `Uncaught (in promise) Error: Element not found`
в браузерной консоли (Issue #79). Решение — lazy load табов через
`loadDataForActiveTab(activeTabIndex)` вместо `reloadAll()`, плюс
60s TTL на фронте (Vuex store singleton) и удаление кнопки «Обновить всё».

**Технический подход** (после research.md):
- Заменить `reloadAll()` на `loadDataForActiveTab(activeTabIndex)`
- Добавить watcher на `activeTab` с TTL-проверкой
- Добавить `state.lastLoadedAt` (Object<Number, Number>) в Vuex store
- Удалить `reloadAll()` и кнопку «Обновить всё»
- Добавить `console.debug` для observability
- Backward-compat с БД/период через частичный reload (только endpoint'ы
  активной вкладки + endpoint'ы с фильтром days)

## Technical Context

**Language/Version**: JavaScript ES2020+ (Vue 3.x, Options API), Vuex 4.x
**Primary Dependencies**:
- `vue@3.x`
- `vuex@4.x`
- `bootstrap-vue-next@0.40.5` (BTabs/BTab локально)
- `vue3-apexcharts@1.11.1` (глобально через `app.use(VueApexCharts)`)
- `apexcharts@5.16.0` (зависимость vue3-apexcharts)
- `promisedXMLHttpRequest` (webvue3/src/lib/utils.js — собственный wrapper)

**Storage**: N/A (фича чисто фронтовая, никаких изменений в БД/LocalStorage)
**Testing**: ручное в DevTools (Console/Network) — см. quickstart.md
**Target Platform**: webvue3 admin SPA, браузер (Chrome 120+)
**Project Type**: web-application (frontend-only change)
**Performance Goals**:
- ≤ 2 HTTP-запросов при первом `mounted()` (KPI: summary + monetization summary)
- 0 новых HTTP при возврате на страницу в течение 60 сек (TTL)
- 0 ошибок `Element not found` в console за 10 сек после mount

**Constraints**:
- Без backend-изменений (только frontend)
- Backward-compat с существующими `data()` (страницы пагинации)
- Backward-compat с существующими Vuex mutations/getters (имена сохраняем)
- Без добавления AbortController (нет в lib/utils, добавлять вне scope)
- Без удаления vue3-apexcharts (он работает, проблема в нашем коде)

**Scale/Scope**:
- 1 компонент: `webvue3/src/views/StatsView.vue` (~742 строки → ~800 после)
- 1 store: `webvue3/src/components/Stats/store.js` (~498 строк → ~520 после)
- 8 вкладок (`activeTab` 0-7)
- 11 параллельных endpoint'ов → 1-3 параллельных на активную вкладку

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Principle I — Self-contained автопайплайн

✅ **PASS**: фича не добавляет внешних зависимостей, не меняет горячий путь
обработки медиа. Apexcharts уже в `package.json` (Pass 51).

### Principle II — Сырой JDBC + дифф по хэшам

✅ **PASS**: фича чисто фронтовая, SQL не затрагивается. `statsBySong`,
`topUsers` и т.д. — те же SQL-запросы на backend'е, что и сейчас.

### Principle III — Двух-БД синхронизация через SyncRegistry

✅ **PASS**: не применимо — фича на frontend'е. `tbl_events` и
`tbl_web_event` не затрагиваются.

### Principle IV — Async-очередь задач с парсингом stdout

✅ **PASS**: не применимо. Фича не использует ProcessBuilder.

### Principle V — Двух-фронтенд

✅ **PASS**: изменения **только** в `webvue3` (admin). `karaoke-public`
не затронут. Граница ответственности соблюдена.

### Principle VI — Code Standards (FR-006/FR-007/FR-009)

✅ **PASS**:
- **FR-006**: KDoc/JSDoc для новых методов (`loadDataForActiveTab`,
  `onTabChange` и т.д.) — обязательно. В `StatsView.vue` уже есть
  JSDoc-блок на строке 335-339.
- **FR-007**: линтеры запускаются через pre-commit (`pre-commit run --all-files`)
  + ktlintCheck в CI.
- **FR-009**: per-feature документ — `archive/docs/features/stats.md`
  уже описывает желаемое поведение (FR-001 спеки 174). В этом PR
  нужно обновить `archive/docs/features/stats.md` чтобы отразить,
  что «lazy load применён» (а не только «обещан»).

### Principle VII — Cross-Machine Setup

✅ **PASS**: фича не требует новых git-конфигов.

### Principle VIII — Секреты и git-гигиена

✅ **PASS**: фича не добавляет секретов. `git ls-files deploy/.env` и т.д.
останутся пустыми.

### Principle IX — Knowledge-first

✅ **PASS**: pre-flight выполнен (см. spec.md § Knowledge References).
5 grep-запросов, 8 knowledge файлов прочитаны ДО `codegraph_explore`.

**GATE: All principles pass.** ✅ Можно переходить к Phase 0/1.

## Project Structure

### Documentation (this feature)

```text
specs/362-fix-stats-view-element-not-found/
├── plan.md              # Этот файл (/speckit.plan output)
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output (нет — фича чисто фронтовая)
└── tasks.md             # Phase 2 output (/speckit.tasks)
```

### Source Code (repository root)

```text
# Изменения в этой спеке:
webvue3/src/
├── views/
│   └── StatsView.vue              # Главный файл: добавить loadDataForActiveTab, watcher, удалить reloadAll
└── components/
    └── Stats/
        └── store.js               # Добавить lastLoadedAt в state, добавить геттер
```

**Structure Decision**: Single project (webvue3 admin SPA). Изменения
касаются 2 файлов:
- `webvue3/src/views/StatsView.vue` — главный view, lazy load логика
- `webvue3/src/components/Stats/store.js` — Vuex store, добавить
  `lastLoadedAt` (Object<Number, Number>) + геттер

`contracts/` НЕ создаётся — фича чисто фронтовая, нет новых
API-контрактов между backend и frontend (используются существующие
11 endpoint'ов `/api/stats/*`).

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| None | — | Все Constitution principles PASS |

Нет нарушений. Complexity Tracking не требуется.
