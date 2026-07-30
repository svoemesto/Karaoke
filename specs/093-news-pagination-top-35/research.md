# Research: Новости — пагинация над таблицей, не больше 35 строк

**Branch**: `093-news-pagination-top-35` | **Date**: 2026-07-30 | **Plan**: [plan.md](./plan.md)

**Замечание о размере**: задача локальна и хорошо покрыта уже выполненной
`specs/090-news-pagination` + существующим кодом в `webvue3/src/components/News/`.
Внешних NEEDS CLARIFICATION, новых зависимостей или незнакомых API нет —
этот документ фиксирует уже принятые решения и ссылки, а не полноценное
исследование. Если в будущем здесь появится нетривиальная ветка решений —
формат ниже соответствует шаблону (Decision / Rationale / Alternatives).

## Phase 0 — открытые вопросы

Из Technical Context плана (`plan.md`):

- `Language/Version` — ✅ Vue 3, JS, Node 22 (LTS). Уточнения не нужны.
- `Primary Dependencies` — ✅ `vue`, `vuex`, `bootstrap-vue-next`. Новых нет.
- `Storage` — N/A (UI-only).
- `Testing` — ✅ ручная по `quickstart.md` + `npm run lint:check` в `webvue3`.
- `Target Platform` — ✅ браузер (админка).
- `Project Type` — ✅ Web (frontend-only правка в существующем SPA).
- `Performance Goals` — унаследованы от `specs/090-news-pagination` (SC-001/SC-002).
- `Constraints` — перечислены в `plan.md`.
- `Scale/Scope` — ~19000+ строк в `tbl_news` на проде; правка в 2 файлах.

**NEEDS CLARIFICATION**: нет. Все технические детали известны из контекста проекта.

## Решения

### D1. Положение `<b-pagination>` в `NewsTable.vue`

- **Decision**: переместить блок `<b-pagination v-model="currentPageModel" ...>` (строки 124–130 текущего `NewsTable.vue`) из позиции «после `</b-table>`» в позицию «перед `<b-table>`» внутри того же `<div class="news-table-body">`. Сохранить те же пропсы (`v-model="currentPageModel"`, `:total-rows="totalCount"`, `:per-page="perPage"`, `align="center"`, `size="sm"`).
- **Rationale**:
  - Решает FR-001 спеки буквально (над таблицей, а не под ней) без изменения DOM-структуры блока и без новых обёрток.
  - Не ломает SC-001/SC-002 из `specs/090-news-pagination` — `<b-pagination>` остаётся внутри того же `.news-table-body`, ничего не «уезжает» в другую часть страницы.
  - Согласуется с уже принятым в `specs/090-news-pagination` принципом «`<b-pagination>` лишь триггерит перезапрос на бэкенд» (комментарий в `NewsTable.vue:182-183`).
- **Alternatives considered**:
  - **Дублировать пагинацию сверху и снизу** (как в некоторых публичных таблицах с длинным скроллом). Отклонено: не входит в объём спеки (FR-001 говорит «над», не «над и под»), плюс админский экран в основном короткий.
  - **Sticky-пагинация (прибитая к верху viewport)**. Отклонено: требует дополнительной CSS-обвязки и в `quickstart.md` явно не проверяется; FR-001 ограничен положением в DOM, а не поведением при скролле.

### D2. Размер страницы (perPage)

- **Decision**: установить `NEWS_PER_PAGE = 35` в `webvue3/src/components/News/store.js:11` (сейчас 50).
- **Rationale**:
  - Прямо выполняет FR-002 спеки («не больше 35 строк», дефолт = 35).
  - `35` уже передаётся как число строк в сутки на админский просмотр — типичный экран ноутбука вмещает шапку таблицы + 35 строк без агрессивного скролла.
  - Уменьшение с 50 до 35 улучшает SC-001/SC-002 (меньше DOM) и не требует изменений в бэкенде — `pageSize` уже принимается `NewsController` как параметр (см. `specs/090-news-pagination/plan.md`, Project Structure).
- **Alternatives considered**:
  - **Опциональный UI-селектор 10/20/35/50**. Отклонено: Assumptions спеки явно фиксируют «не делаем UI-селектор в рамках этой фичи, если администратор не запросит явно». Если позже понадобится — это отдельная фича.
  - **perPage = 20 или 25** (ещё меньше). Отклонено: пользователь явно сказал «не больше 35», не задавая минимум; 35 — наибольшее допустимое и одновременно разумный потолок для админского экрана.

### D3. Реактивность и watchers

- **Decision**: не вводить новые watcher'ы; оставить текущий поток `currentPageModel` ⇄ Vuex-действие `setNewsCurrentPage` ⇄ `loadNews()` (см. `NewsTable.vue:184-191`, `store.js:85-88`).
- **Rationale**:
  - Изменение `perPage` с 50 на 35 не требует новых watcher'ов — `perPage` читается из стора через геттер `getNewsPerPage` и передаётся в `:per-page="perPage"` (строка 127), который реагирует на изменение store-state реактивно.
  - Текущая логика сброса `setNewsCurrentPage(1)` при смене `newsTarget` (строки 89–92) уже даёт нужное поведение «после смены LOCAL↔REMOTE открываем первую страницу».
- **Alternatives considered**:
  - **watcher `perPage` → `setNewsCurrentPage(1)`**. Отклонено: `perPage` в этой фиче меняется только один раз (через правку константы в `store.js`), а не в рантайме из UI, поэтому watcher не нужен.

### D4. Линт и JSDoc

- **Decision**: после правки прогнать `cd webvue3 && npm run lint:check` и убедиться, что `webvue3/.eslint-baseline.json` не вырос. JSDoc существующих функций/компонента — оставить как есть (уже ссылается на `AGENTS.md` через `@see`), при необходимости добавить короткий комментарий о причине нового `NEWS_PER_PAGE`.
- **Rationale**:
  - Соответствует `Principle VI` (FR-006 — JSDoc обязателен, FR-007 — линтер не должен расти) и `AGENTS.md` (CI-gate `npm run lint:check`).
  - Документация для per-feature подсистемы не требуется (см. Complexity Tracking плана).
- **Alternatives considered**: пересмотр всего JSDoc-блока компонента — отклонён, текущий JSDoc остаётся корректным.

## Что НЕ меняется (явно вне объёма)

- `tbl_news` — структура, индексы, `recordhash`-триггер.
- `News.loadAll(limit, offset)` / `News.countAll()` в `karaoke-app`.
- `NewsController` (`/api/news/list`) — параметр `pageSize` уже принимается.
- `PublicNewsController` (`/api/public/news`) — публичная лента со своей пагинацией «Показать ещё» из `specs/090-news-pagination`; её `pageSize` отдельно (не 35/не 50 — см. `karaoke-public/src/services/newsApi.js`).
- `SongReleaseAnnouncementService` / `News.createAutoAnnouncement` — логика авто-создания новостей (за рамками `specs/089-auto-news-song-release`).
- `<b-spinner>` / состояние `newsListIsLoading` — не затрагивается.
- Существующая CSS-разметка `.news-table*`, `.fld-news-*` — не затрагивается.
