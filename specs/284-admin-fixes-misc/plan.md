# Implementation Plan: Админка — мелкие правки UI (SongEdit label, описание, пагинация истории)

**Branch**: `284-admin-fixes-misc` | **Date**: 2026-08-31 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/284-admin-fixes-misc/spec.md`

## Summary

Три небольших правки в админ-SPA `webvue3`:
1. **SongEdit.vue**: лейбл `Композиция (цензурированная):` → `Censored:` (чисто текстовая правка).
2. **SongEdit.vue**: `<textarea>` «Описание» `rows="4"` → `rows="2"` (вдвое ниже).
3. **ListeningHistoryTable.vue**: починить пагинацию — root-cause: watcher `currentPage` сохраняет
   значение в Vuex, но **не** диспатчит `loadListeningHistoryDigest({page})`. Бэкенд уже
   поддерживает параметры `page`/`pageSize` (см. `ListeningHistoryController.kt:66-67, 85-87, 146`),
   нужно только прокинуть их из фронта и триггерить reload при смене страницы.

## Technical Context

- **Language/Version**: JavaScript / Vue 3 / Vuex 4.
- **Primary Dependencies**:
  - `bootstrap-vue-next` (`BPagination`, `BTable`) — уже подключены в `ListeningHistoryTable.vue`.
  - `promisedXMLHttpRequest` (`webvue3/src/lib/utils.js:15`) — для HTTP POST с параметрами в form-urlencoded body.
  - Backend: `ListeningHistoryController.kt` (`/api/listeninghistory/digest`) — уже принимает `page` и `pageSize` с дефолтами `1`/`500`.
- **Storage**: N/A (только UI-стейт, никаких изменений БД).
- **Testing**: ручная валидация по `quickstart.md` (Pass 56/Constitution § «Рабочий процесс»). CI-тестов нет.
- **Target Platform**: `webvue3` (admin SPA), Linux, деплой через `deploy/do.sh build_webvue3`.
- **Project Type**: web SPA (Vue 3) поверх Kotlin-Spring backend.
- **Performance Goals**: 1 POST-запрос на 1 клик по странице (без дребезга, без лишних вызовов при `currentPage === 1` без изменения).
- **Constraints**:
  - Не ломать существующую пагинацию в `<b-table>` (`v-model:sort-by` + `:per-page`/`:current-page` остаются для клиентской сортировки 500 строк).
  - Не вводить новых зависимостей.
  - Линтеры: ktlintCheck не нужен (изменения только в JS-фронте), ESLint (Vue/JS) + prettier + Vite-build + Docker-образ — обязательные шаги после правок (AGENTS.md, Pass 245).
  - `nsa-i9`/`nsa`: права на сборку `karaoke-web`/`webvue3` есть (см. AGENTS.md, Pass 282). Пересборка `karaoke-app`/контейнера не требуется — меняется только фронт.
- **Scale/Scope**: 2 файла правки (`SongEdit.vue` 2 строки текста) + 1 пара файлов для пагинации (`ListeningHistoryTable.vue` + `ListeningHistory/store.js`). ~30 строк кода всего.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Принцип | Статус | Комментарий |
|---|---------|--------|-------------|
| I  | Self-contained автопайплайн | ✅ | Изменения только в UI админки; никаких внешних SaaS в горячем пути. |
| II | Сырой JDBC + дифф по хэшам | ✅ | Не затрагивается: фича только во фронте, никаких SQL-изменений, recordhash-триггеры не трогаются. |
| III | Двух-БД синхронизация через SyncRegistry | ✅ | Не затрагивается: нет изменений в сущностях, не нужен sync-флаг. |
| IV | Async-очередь задач с парсингом stdout | ✅ | Не затрагивается: пагинация — обычный синхронный HTTP POST, без `ProcessBuilder`, без stdout-парсинга. |
| V  | Двух-фронтенд: админка и публичный сайт | ✅ | Фича только в админке `webvue3`. Публичный `karaoke-public` не затрагивается. |
| VI | Code Standards | ✅ | Новых публичных API не вводим (просто добавляем параметр в существующий action `loadListeningHistoryDigest`). Если будет новый action — KDoc/JSDoc со ссылкой на `specs/284-admin-fixes-misc/spec.md`. Линтеры и Docker-сборка — обязательные шаги после правок. |
| VII | Cross-Machine Setup | ✅ | Не затрагивается: фича не меняет `.gitignore`, `.gitattributes`, `.git-blame-ignore-revs`. |
| VIII | Секреты и git-гигиена | ✅ | Не затрагивается: фича не вводит/не меняет секрет-файлы, не трогает `.env`. |

**GATE: PASS** — нарушений нет, дополнительных обоснований не требуется. Complexity Tracking — пусто.

## Project Structure

### Documentation (this feature)

```text
specs/284-admin-fixes-misc/
├── plan.md              # этот файл
├── spec.md              # Feature Spec
├── contracts/
│   └── listeninghistory-pagination.md  # Phase 1 (параметр `page` + поведение на бэке и фронте)
├── quickstart.md        # Phase 1 (ручные сценарии валидации)
├── checklists/
│   └── requirements.md  # Spec Quality Checklist (✅ 16/16)
└── tasks.md             # Phase 2 — следующий файл
```

`research.md` и `data-model.md` НЕ нужны: фича — UI-твик + уже-поддержанный параметр пагинации,
новых исследований/таблиц нет.

### Source Code (repository root)

**Структура не меняется** — фича встраивается в существующие файлы. Никаких новых директорий в исходниках.

Затрагиваемые файлы:

| Файл | Изменение |
|------|-----------|
| `webvue3/src/components/Songs/edit/SongEdit.vue` | (1) Лейбл `Композиция (цензурированная):` → `Censored:` (SongEdit.vue:136). (2) `rows="4"` → `rows="2"` у `<textarea>` описания (SongEdit.vue:326). |
| `webvue3/src/components/ListeningHistory/ListeningHistoryTable.vue` | Watcher `currentPage` дополнен: после мутации стейта диспатчит `loadListeningHistoryDigest({ page: newPage })`. `mounted()` / `reload()` остаются для первичной загрузки. |
| `webvue3/src/components/ListeningHistory/store.js` | Action `loadListeningHistoryDigest` принимает `params.page` (опционально, дефолт `1`) и пробрасывает его в бэк. Getter/mutation для текущей страницы — без изменений. |

**Structure Decision**: фича затрагивает 3 существующих файла в `webvue3`. Никаких новых модулей/директорий.
Тесты не добавляются (валидация — ручная по `quickstart.md`).

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| _(нет)_   | —          | —                                   |

Constitution Check — без нарушений. Complexity Tracking пуст по форме.

## Детальный план реализации (для tasks.md)

### A. `SongEdit.vue` (карточка песни)

- **A1 (US1, FR-001…FR-003)**: заменить текст лейбла (SongEdit.vue:136). `title`-тултип и `v-model`/кнопки
  не трогаем — это чисто текстовая правка.
- **A2 (US2, FR-004)**: изменить `rows="4"` → `rows="2"` (SongEdit.vue:326). `v-model` и undo-кнопка
  остаются.

### B. `ListeningHistoryTable.vue` + `store.js` (пагинация)

- **B1 (US3, FR-005…FR-008, FR-010)**: в `store.js`:
  - Action `loadListeningHistoryDigest(ctx, params)` принимает `params.page` (1-based; дефолт `1`,
    если не передан). Мержит с `target`, отправляет в `POST /api/listeninghistory/digest`.
  - Никаких изменений в getter/mutation/states.
- **B2**: в `ListeningHistoryTable.vue`:
  - `watch.currentPage(newPage, oldPage)`: после `commit('setListeningHistoryTableCurrentPage', newPage)`
    диспатчит `loadListeningHistoryDigest({ page: newPage })`, **но только если** `newPage !== oldPage`
    (защита от дребезга при первом mount, где Vuex уже содержит старое значение).
  - `mounted()` остаётся (первичная загрузка для восстановленной страницы).
  - `reload()` (кнопка «Обновить» в тулбаре) — `currentPage` НЕ сбрасывается (обновление **текущей**
    страницы), передаёт `currentPage` в action.
  - `onTargetChange()` остаётся (сброс на 1 + reload, FR-008).
  - Страница > pages: `bootstrap-vue-next` сам clamp'ит; бэкенд clamp'ит `safePage >= 1`
    (ListeningHistoryController.kt:85), так что данные не падают.

### C. Polish (AGENTS.md, порядок non-negotiable)

1. Backend compile: фронт-only → `:karaoke-web:compileKotlin` для контроля нетронутости
   backend-API (ListeningHistoryController не менялся). На `nsa-i9`/`nsa` дополнительно
   `:karaoke-app:compileKotlin` (Pass 282).
2. Линтеры: `cd webvue3 && npm run lint`, `cd karaoke-public && npm run lint` — никаких НОВЫХ
   нарушений (baseline OK).
3. Frontend Vite + prettier: `cd webvue3 && npm run build && npm run format:check`.
4. Docker-образ webvue3 (Pass 245 NON-NEGOTIABLE): `cd deploy && bash do.sh build_webvue3`.
5. Ручная валидация по `quickstart.md`.
