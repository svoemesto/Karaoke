# Implementation Plan: Корректная пагинация таблиц после применения фильтра

**Branch**: `300-author-pagination-filter-bug` | **Date**: 2026-09-03 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/300-author-pagination-filter-bug/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command; its definition describes the execution workflow.

## Summary

Баг #50: после применения фильтра в админке (`webvue3`) на странице N>1, сужающего выборку до ≤1 страницы, таблица показывает пустую страницу вместо результатов фильтра.

**Корневая причина** (подтверждена в [research.md](research.md)):

- `webvue3/src/components/Authors/AuthorsTable.vue` использует computed `countRows = authorsDigests.length` (длина ТЕКУЩЕЙ страницы) как `:total-rows` для `<b-pagination>`.
- При применении фильтра backend возвращает массив длиной ≤ `perPage`. `countRows` уменьшается, но `currentPage` остаётся прежним (например, 3).
- Watcher на `currentPage` (для сохранения в стор) есть, а watcher на `countRows` с защитой — **нет** (есть только в `Songs/SongsTable.vue:998-1009`).
- `<b-pagination>` пересчитывает pages из `countRows`, но `currentPage=3` для `total-rows=5` → b-table рендерит пустоту.

**Технический подход**: добавить watcher на `countRows` в AuthorsTable, AlbumsTable, PicturesTable, SiteUsersTable по образцу SongsTable. Watcher пересчитывает `totalPages = ceil(countRows / perPage)` и сбрасывает `currentPage = 1`, если текущая страница вышла за пределы. Это покрывает и сброс фильтра, и сужение, и расширение — в полном соответствии с FR-006 (всегда страница 1 после сброса) и FR-001 (сброс на последнюю доступную).

**Объём фикса**:

- 4 файла в `webvue3/src/components/<Entity>/<Entity>Table.vue` (по ~5-7 строк в каждом).
- 0 изменений в backend (`karaoke-app`, `karaoke-web`).
- 0 изменений в `*/store.js`.
- 1 новый документ: `docs/features/pagination-filter-admin-tables.md` (per-feature, FR-011).
- 1 новый документ: `specs/300-author-pagination-filter-bug/audit.md` (результат аудита таблиц, FR-008).

## Technical Context

**Language/Version**: JavaScript (Vue 3 + Composition/Options API), Vuex 3.x, Bootstrap-vue-next (admin SPA).

**Primary Dependencies**:
- Vue 3 + Vite
- Vuex 3 (state management, per-entity modules)
- Bootstrap-vue-next (`<b-pagination>`, `<b-table>`)
- `promisedXMLHttpRequest` (utility из `webvue3/src/lib/utils`)

**Storage**: Vuex in-memory state (per-entity store в `webvue3/src/components/<Entity>/store.js`); localStorage-персистенция не задействована для пагинационных state.

**Testing**: ручная проверка (в проекте нет автотестов для `webvue3`, см. AGENTS.md и Constitution § «Рабочий процесс» → «Тесты: в CI нет»). Финальная проверка — пользователем вручную по сценариям в [quickstart.md](quickstart.md).

**Target Platform**: admin SPA `webvue3` (Vue 3 + Vite build), деплой через Docker-образ `karaoke-webvue3`.

**Project Type**: bug-fix на существующем web-приложении (frontend-only).

**Performance Goals**: без регрессии. Watcher срабатывает на каждое изменение `countRows` (макс. ~10 раз в секунду при печати в фильтре). Операция O(1) — не влияет на перф.

**Constraints**:
- **НЕ менять backend-контракты** (AGENTS.md § «Фикс не должен менять API/контракты бэкенда»). Если потребуется — отдельный тикет.
- Следовать существующему стилю `webvue3` (см. AGENTS.md, CONTRIBUTING.md): Vue 3 Options API (этот код — Options, не Composition), Bootstrap-vue-next, KDoc/JSDoc 100% (Constitution FR-006).
- Каждый файл с watcher должен сопровождаться JSDoc-комментарием с `@see` (Constitution FR-006) — ссылаться на `docs/features/pagination-filter-admin-tables.md`.

**Scale/Scope**: 4 файла изменений в `webvue3`; ~25 строк кода суммарно + JSDoc; 1 per-feature документ; 1 audit-документ.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Принцип | Соответствие | Обоснование |
|---------|-------------|-------------|
| **I. Self-contained автопайплайн** | ✅ N/A | Фикс в admin SPA, не затрагивает пайплайн медиа |
| **II. Сырой JDBC + дифф по хэшам** | ✅ N/A | Фикс на клиенте, без работы с БД |
| **III. Двух-БД синхронизация через SyncRegistry** | ✅ N/A | Не добавляем/изменяем сущности |
| **IV. Async-очередь задач с парсингом stdout** | ✅ N/A | Не затрагивает async-очередь |
| **V. Двух-фронтенд: админка и публичный сайт** | ✅ Соответствует | Фикс только в `webvue3` (admin), не трогаем `karaoke-public` |
| **VI. Code Standards (FR-006, FR-007, FR-009)** | ✅ Соответствует | KDoc/JSDoc добавляется, ktlint/ESLint пройдут (минимальные изменения), per-feature документ создаётся (FR-009) |
| **VII. Cross-Machine Setup** | ✅ N/A | Не меняет конфиги |
| **VIII. Секреты и git-гигиена** | ✅ N/A | Не затрагивает секреты |
| **Рабочий процесс (сборка, git)** | ✅ Соответствует | Только `webvue3` → `npm run build` + `bash do.sh build_webvue3` (не `karaoke-app`) |

**GATE PASSED**. Все принципы соблюдены. Нарушений нет.

**Re-check после Phase 1 design**: без изменений (дизайн полностью клиентский, не затрагивает других принципов).

## Project Structure

### Documentation (this feature)

```text
specs/300-author-pagination-filter-bug/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output — кодовая разведка, decisions
├── data-model.md        # Phase 1 output — client-side state + backend response shapes
├── quickstart.md        # Phase 1 output — runnable validation scenarios
├── contracts/           # Phase 1 output — бэкенд не меняется, README с reference
│   └── README.md
├── audit.md             # Phase 2 output (в tasks.md) — результат аудита таблиц (FR-008)
└── tasks.md             # Phase 2 output (/speckit.tasks — НЕ создаётся этим планом)
```

### Source Code (repository root)

Фикс затрагивает **только admin SPA**, без изменений backend.

```text
webvue3/src/components/
├── Authors/
│   ├── AuthorsTable.vue         # +watcher countRows
│   ├── store.js                 # без изменений
│   └── filter/
│       └── AuthorsFilterModal.vue  # без изменений
├── Albums/
│   ├── AlbumsTable.vue          # +watcher countRows
│   └── store.js                 # без изменений
├── Pictures/
│   ├── PicturesTable.vue        # +watcher countRows
│   └── store.js                 # без изменений
├── SiteUsers/
│   ├── SiteUsersTable.vue       # +watcher countRows
│   └── store.js                 # без изменений
├── Songs/
│   ├── SongsTable.vue           # БЕЗ изменений (эталон, уже имеет watcher)
│   └── store.js                 # без изменений
└── News/
    ├── NewsTable.vue            # БЕЗ изменений (другой эталон: totalCount + setNewsTarget)
    └── store.js                 # без изменений

docs/features/
└── pagination-filter-admin-tables.md  # NEW (per-feature, FR-011)

specs/300-author-pagination-filter-bug/
└── audit.md                     # NEW (FR-008 — результат ручного аудита)
```

**Structure Decision**: опция «Single project» НЕ применима; релевантна **Option 2: Web application** (admin frontend), но фикс затрагивает только `frontend/` часть. Backend не меняется. Karaoke-public — отдельный проект, не задействован.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| (нет) | — | — |

Constitution Check пройден без нарушений — секция пуста.

## Implementation outline (high-level, для следующей фазы `/speckit.tasks`)

1. **Аудит таблиц** (FR-008): создать `audit.md` с результатами проверки всех таблиц из `webvue3/src/views/` на воспроизведение бага.
2. **Шаблон watcher** (по образцу `Songs/SongsTable.vue:998-1009`):
   ```js
   watch: {
     countRows: {
       handler(newCount) {
         // Сбрасываем на 1, если текущая страница вышла за пределы после загрузки/фильтрации.
         // Иначе (при первом монтировании) сохраняем страницу пользователя.
         const totalPages = Math.max(1, Math.ceil(newCount / this.perPage))
         if (this.currentPage > totalPages) {
          this.currentPage = 1
         }
       },
     },
   },
   ```
3. **Применить шаблон в 4 файлах** (Authors, Albums, Pictures, SiteUsers), добавить JSDoc с `@see`.
4. **Создать per-feature документ** `docs/features/pagination-filter-admin-tables.md` с описанием бага, фикса, паттерна и reference на SongsTable.
5. **Валидация**: ручные сценарии из [quickstart.md](quickstart.md), линтеры (`ktlintCheck` + `npm run lint:check` + `prettier --check`), пересборка `webvue3`, деплой на dev-машину.
6. **PR**: `300-author-pagination-filter-bug` → `master`, прохождение CI 7/7, ручная проверка пользователем, мерж.

## Open questions

Нет открытых вопросов после `/speckit.clarify` (FR-006 закрыт).

## Risks

- **R1**: Watcher на `countRows` срабатывает на КАЖДОЕ изменение массива (включая `update*Digests` mutations). Если `update*Digests` изменяет `digests` в результате одной операции (например, обновление одной записи в таблице), `countRows` может не измениться (длина та же) — watcher не сработает (Vue не вызывает watcher если значение не поменялось). **Mitigation**: длина массива меняется только при `set*Digests` (полная перезагрузка) или `update*Digests` с разной длиной (что не должно происходить — это обновление одной записи). Если происходит — поведение остаётся корректным (currentPage не сбрасывается, если страница остаётся валидной).
- **R2**: Race condition в случае медленной сети — пользователь быстро меняет фильтр, ответы приходят вразнобой. **Mitigation**: watcher на `countRows` отрабатывает **последнее** изменение; промежуточные ответы могут оставить currentPage в неправильном состоянии (например, страница 3 при загрузке страницы 1 после сброса фильтра). **Известное ограничение** (см. research.md Decision 4) — решается отдельным таском «Race condition fix», не в этой задаче.
- **R3**: Если в будущем кто-то добавит `total` в backend-ответ и переключит `countRows` на `get<Entity>TotalCount`, watcher продолжит работать (он на `countRows` computed, не на state напрямую). **Mitigation**: при будущей миграции — обновить только computed `countRows`, watcher не трогать.

## Done When (для этого плана)

- [x] Plan workflow выполнен
- [x] `research.md` создан с подтверждёнными decisions
- [x] `data-model.md` создан
- [x] `contracts/README.md` создан (бэкенд не меняется)
- [x] `quickstart.md` создан с validation scenarios
- [x] Constitution Check пройден без нарушений
- [x] Сложность не увеличена (нет violations)
- [ ] Создать `tasks.md` через `/speckit.tasks` — следующий шаг (НЕ создаётся этим планом)