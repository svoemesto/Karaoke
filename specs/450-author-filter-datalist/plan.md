# Implementation Plan: Список авторов в фильтрах

**Branch**: `450-author-filter-datalist` | **Date**: 2026-09-24 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/450-author-filter-datalist/spec.md`

## Summary

В admin SPA `webvue3` поля «Автор:» в модалках фильтров компонентов
«Авторы» (`AuthorsFilterModal.vue`) и «Альбомы» (`AlbumsFilterModal.vue`)
должны показывать нативный список подсказок (`<datalist>`) с именами
авторов — точно так же, как это уже сделано в `SongsFilterModal.vue`
через геттер `songAuthorsPromise` (endpoint `POST /api/songs/authors`).

Технический подход — минимальный: добавить локальный `dictAuthors` в
`data()` обеих модалок, загружать его асинхронно в `mounted()` из
существующего Vuex-геттера `songAuthorsPromise`, отрендерить
модалко-локальный `<datalist>` и привязать поле через `list="..."`.
Серверных изменений, новых зависимостей и правок `karaoke-public` нет.

## Technical Context

**Language/Version**: JavaScript (ES2022) + Vue 3 Options API + Vuex 4; Vite 7

**Primary Dependencies**: `bootstrap-vue-next` (уже используется —
формы/datalist нативные), `promisedXMLHttpRequest` (`webvue3/src/lib/utils`),
Vuex-геттер `songAuthorsPromise` (модуль `Songs/store.js`)

**Storage**: N/A (новых данных не хранится). Значения фильтров продолжают
персистироваться через server-side key/value (`setWebvueProp`/`getWebvueProp`)
— поведение не меняется

**Testing**: Ручной E2E в `webvue3` (в проекте нет vitest; используется
`node --test` только для чистых util-хелперов). Обязательные проверки:
`cd webvue3 && npm run lint`, `npm run build`, `npm run format:check`

**Target Platform**: Браузер, admin SPA `webvue3`

**Project Type**: Web frontend (admin SPA, single project — модуль `webvue3`)

**Performance Goals**: Список авторов (~сотни строк) загружается
асинхронно и не блокирует открытие модалки; нативный datalist фильтрует
подстроку силами браузера

**Constraints**: Без новых зависимостей; без изменений backend и
`karaoke-public`; сохранить текущую семантику фильтрации (точное
совпадение имён) и персистентность фильтров

**Scale/Scope**: 2 модалки фильтров, 2 поля «Автор:», 1 Vuex-геттер

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Принцип | Применимость | Статус |
|---|---|---|
| **I. Self-contained автопайплайн** | Нет нового рантайма/SaaS | PASS |
| **II. Сырой JDBC** | Нет обращений к БД, только существующий HTTP-endpoint | PASS |
| **III. Двух-БД sync через SyncRegistry** | Не затрагивается | PASS |
| **IV. Async-очередь** | Не затрагивается | PASS |
| **V. Двух-фронтенд (admin ≠ public)** | Меняется только `webvue3`; `karaoke-public` не трогаем | PASS |
| **VI. Code Standards (FR-006/FR-007/FR-009)** | MUST: JSDoc + `@see docs/features/author-filter-datalist.md`; ESLint baseline; per-feature документ в том же PR | PASS (учтено в задачах) |
| **VII. Cross-Machine Setup** | Не затрагивается | PASS |
| **VIII. Секреты и git-гигиена** | Секреты не участвуют | PASS |
| **IX. Knowledge-first** | Pre-flight выполнен (см. spec.md § Knowledge References); синхронно обновить `knowledge/system/frontend/filter-stores.md` | PASS |

**Итог**: нарушений нет, секция Complexity Tracking не требуется.

## Project Structure

### Documentation (this feature)

```text
specs/450-author-filter-datalist/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   └── ui-author-datalist.md
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 output (/speckit.tasks — NOT created here)
```

### Source Code (repository root)

```text
webvue3/
├── src/
│   ├── components/
│   │   ├── Songs/filter/SongsFilterModal.vue      # ЭТАЛОН (datalist через songAuthorsPromise)
│   │   ├── Authors/filter/AuthorsFilterModal.vue  # ПРАВКА: dictAuthors + datalist
│   │   └── Albums/filter/AlbumsFilterModal.vue    # ПРАВКА: dictAuthors + datalist (заменить мёртвый list="list_authors")
│   ├── components/Songs/store.js                  # источник getters.songAuthorsPromise (не менять)
│   └── lib/utils.js                               # promisedXMLHttpRequest (не менять)
└── package.json                                   # lint / build / format:check

docs/features/
└── author-filter-datalist.md                      # FR-009 per-feature документ (НОВЫЙ)

knowledge/system/frontend/
└── filter-stores.md                               # SSoT: отметить datalist-источник для полей «Автор:»
```

**Structure Decision**: Single project — изменения только в модуле
`webvue3` (admin SPA). Backend (`karaoke-app`, `karaoke-web`) не
затрагивается, т.к. нужный endpoint `/api/songs/authors` уже существует и
используется фильтром «Песни».

## Complexity Tracking

> Нарушений Constitution Check нет — секция не заполняется.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| — | — | — |
