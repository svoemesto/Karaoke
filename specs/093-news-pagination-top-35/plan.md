# Implementation Plan: Новости — пагинация над таблицей, не больше 35 строк на страницу

**Branch**: `093-news-pagination-top-35` | **Date**: 2026-07-30 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/home/nsa/Karaoke/specs/093-news-pagination-top-35/spec.md`

## Summary

Админский раздел «Новости» (`webvue3/src/components/News/NewsTable.vue`) уже имеет серверную пагинацию, реализованную в `specs/090-news-pagination` (LIMIT/OFFSET + total count + Vuex-стор `newsPerPage`/`newsCurrentPage`/`newsTotalCount` + `<b-pagination>`). Эта фича делает две узкие правки поверх неё: (1) переносит `<b-pagination>` из позиции «после `</b-table>`» в позицию «перед `<b-table>`» внутри блока `.news-table-body`; (2) уменьшает значение `NEWS_PER_PAGE` в `webvue3/src/components/News/store.js` с 50 на 35. Бэкенд (Kotlin-контроллер `NewsController`, модель `News`, публичный `PublicNewsController`, `karaoke-public`) не меняется — серверная пагинация уже принимает нужный `pageSize` через Vuex-стор.

## Technical Context

**Language/Version**: Vue 3 (Composition-API + Options-API mix, как в существующих таблицах), JavaScript (без TypeScript в `webvue3`), Node 22 (LTS) — `webvue3/package.json`.

**Primary Dependencies**:
- `vue@3`, `vuex@4` (state), `vue-router@4` — стандартный стек `webvue3` (см. `webvue3/package.json`).
- `bootstrap-vue-next` — `<b-table>`, `<b-pagination>`, `<b-spinner>` уже подключены в `NewsTable.vue` (импорт `BPagination, BSpinner, BTable` на строке 136).
- Никаких новых зависимостей не требуется.

**Storage**: N/A — фича не трогает БД. Серверная пагинация уже реализована в `karaoke-app` (`News.loadAll(limit, offset)` + `News.countAll()`) и в `karaoke-web` (`PublicNewsController`); контракт `/api/news/list?target=…&page=…&pageSize=…` уже принимает `pageSize` (см. `specs/090-news-pagination`).

**Testing**: ручная проверка в браузере на локальном стенде по `quickstart.md`. Автотестов в CI для этого стека нет (см. constitution.md, «Рабочий процесс», п. «Тесты»). Перед PR — обязательный прогон CI 7/7 (см. `AGENTS.md`, «CI-gate для master»), в т.ч. `npm run lint:check` для `webvue3`.

**Target Platform**: браузер (десктоп/ноутбук, основной сценарий администратора). Мобильная вёрстка — в текущем объёме не меняется (см. `Assumptions` спеки).

**Project Type**: Web-приложение (frontend-only правка в существующем Vue 3 SPA `webvue3`).

**Performance Goals**: позиция пагинации и `perPage=35` не ухудшают SC-001/SC-002 спеки `specs/090-news-pagination` (открытие первой страницы < 2 сек на 19000+ строк). Меньший `perPage` дополнительно снижает размер DOM-дерева на одной странице (≤35 строк вместо 50) — положительный побочный эффект.

**Constraints**:
- Соблюдать `Principle V` (Конституция): правки только в админском `webvue3`; никаких изменений в `karaoke-public` или бэкенде.
- Соблюдать `Principle VI` (FR-006, FR-009): при изменении кода публичной функции Vue-компонента — JSDoc-комментарий с `@see`; линтер должен проходить без новых baseline-нарушений; если в существующем `webvue3/.eslint-baseline.json` уже есть нарушения — они не должны расти.
- Соблюдать `Principle VII.1`: никаких локальных AI-конфигов в коммите.

**Scale/Scope**: ~19000+ строк в `tbl_news` на проде (по данным `specs/090-news-pagination`); UI-правка затрагивает 2 файла в `webvue3/src/components/News/` (`NewsTable.vue` + `store.js`) и не добавляет новых модулей.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Проверка | Статус |
|---|---|---|
| I. Self-contained автопайплайн | Фича не трогает медиа-пайплайн (ffmpeg/melt/Demucs) | ✅ N/A |
| II. Сырой JDBC + дифф по хэшам | Фича не делает SQL-запросов; серверная пагинация уже реализована в `specs/090-news-pagination` (LIMIT/OFFSET + `News.countAll()`) и не затрагивает sync-diff между LOCAL/SERVER | ✅ Соответствует |
| III. Двух-БД синхронизация через SyncRegistry | Структура `tbl_news`, `NewsSyncTarget`, `listHashes()` и sync-флаги не меняются; фича правит только UI | ✅ Соответствует |
| IV. Async-очередь задач | UI-правка — синхронный HTTP-запрос/ответ на `/api/news/list`; `KaraokeProcess*` не участвует | ✅ N/A |
| V. Двух-фронтенд разделение | Правка только в `webvue3` (админское SPA). Публичная лента новостей (`karaoke-public`) и бэкенд не затрагиваются | ✅ Соответствует |
| VI. Code Standards | Изменяется публичный Vue-компонент `NewsTable.vue` (JSDoc уже есть, `@see AGENTS.md` — сохранить/уточнить при правке шаблона) и Vuex-стор `store.js` (JSDoc уже есть, `@see AGENTS.md`). Перед PR — `npm run lint:check` в `webvue3` без роста `eslint-baseline.json`. Раздел «Новости» не входит в 12 ключевых подсистем `docs/features/README.md`, per-feature документ не требуется (аналогичное обоснование — `specs/090-news-pagination/plan.md`, Complexity Tracking) | ✅ Соответствует (см. Complexity Tracking о non-need per-feature doc) |
| VII. Cross-Machine Setup | Правки не затрагивают `.gitattributes`/`.git-blame-ignore-revs`/локальные AI-конфиги; коммит строго в feature-ветке `093-news-pagination-top-35` через PR с CI 7/7 | ✅ N/A |

Нарушений, требующих обоснования в Complexity Tracking, нет — секция ниже
пуста по существу (Constitution Check полностью проходит без исключений).

## Project Structure

### Documentation (this feature)

```text
specs/093-news-pagination-top-35/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command) — пусто, см. ниже
├── checklists/
│   └── requirements.md  # /speckit.specify output
└── tasks.md             # Phase 2 output (/speckit.tasks command — НЕ создаётся /speckit.plan)
```

### Source Code (repository root)

```text
webvue3/src/components/News/
├── NewsTable.vue        # <b-pagination>: перенести НАД <b-table> (внутри .news-table-body)
└── store.js             # NEWS_PER_PAGE: 50 → 35

# Без изменений:
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
├── model/News.kt
└── controllers/NewsController.kt
karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/
└── PublicNewsController.kt
karaoke-public/src/
├── services/newsApi.js
└── views/NewsView.vue
```

**Structure Decision**: фича правит только 2 файла в существующем модуле `webvue3/src/components/News/`. Новых модулей/директорий/папок не создаётся. Бэкенд `karaoke-app`/`karaoke-web` и публичный фронт `karaoke-public` явно вне области изменений.

## Complexity Tracking

*Пусто — Constitution Check выше не выявил нарушений, требующих обоснования.*

Примечание (не нарушение, а явное решение по объёму работ): раздел «Новости» (`tbl_news`, `NewsTable.vue`, `store.js`) не входит в 12 ключевых подсистем `docs/features/README.md`, и фича не меняет ни схему БД, ни sync-логику, ни медиа-пайплайн. Обновление per-feature документа поэтому не требуется по FR-009 Конституции — фича правит только положение UI-элемента и одно число в Vuex-сторе (аналогичное обоснование приведено в `specs/090-news-pagination/plan.md`, Complexity Tracking).
