---
description: "Task list for feature: Корректная пагинация таблиц после применения фильтра"
---

# Tasks: Корректная пагинация таблиц после применения фильтра

**Input**: Design documents from `/specs/300-author-pagination-filter-bug/`
- [plan.md](plan.md) (required) — технический подход
- [spec.md](spec.md) (required) — User Stories с приоритетами
- [research.md](research.md) — decisions и файл-список
- [data-model.md](data-model.md) — client state + backend response shapes
- [contracts/README.md](contracts/README.md) — reference backend-эндпоинтов (бэкенд не меняется)
- [quickstart.md](quickstart.md) — 9 ручных validation scenarios

**Source**: OpenProject #50 — «Неверное поведение на страницах автора после фильтра»
**Branch**: `300-author-pagination-filter-bug`
**Tests**: Опциональны (в проекте нет автотестов для `webvue3`); финальная проверка — пользователем вручную по `quickstart.md` (Constitution § «Рабочий процесс» → «Тесты»).

**Organization**: Tasks сгруппированы по user story (P1 → P1 → P2) с phase блоком для аудита/per-feature документа (Foundational) и финальным Polish.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: можно выполнять параллельно (разные файлы, нет зависимостей на незавершённые задачи)
- **[Story]**: к какой user story относится задача (US1, US2, US3)
- В описании — точные пути файлов

---

## Phase 1: Setup (Shared Infrastructure)

**Цель**: проверить, что dev-окружение в порядке и фикс-ветка готова к правкам кода.

- [x] T001 Подтвердить ветку `300-author-pagination-filter-bug` и чистоту `git status` (нет незакоммиченных изменений, нет неотслеживаемых файлов из других задач)
- [x] T002 [P] Убедиться, что `node_modules` в `webvue3/` установлен (`ls webvue3/node_modules >/dev/null 2>&1 || npm ci`); проверить, что `npm run build` отрабатывает baseline-успешно до фикса
- [x] T003 [P] Убедиться, что линтеры `npm run lint:check` и `npx prettier --check "src/**/*.{vue,js,ts,json}"` проходят baseline-успешно в `webvue3/` (фикс — минимальные правки, не должен ломать baseline)

**Checkpoint**: окружение готово, можно приступать к правкам кода.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Цель**: провести аудит всех таблиц admin SPA на воспроизведение бага (FR-008) и подготовить per-feature документ (FR-011 / Constitution FR-009). Без этих артефактов нельзя переходить к фиксам — нужно знать, ГДЕ воспроизводится баг, и зафиксировать документацию.

**⚠️ CRITICAL**: Без завершения Phase 2 нельзя переходить к Phase 3 (фикс Authors) — иначе фикс будет неполным/необоснованным.

- [x] T004 Создать `specs/300-author-pagination-filter-bug/audit.md` со списком всех таблиц из `webvue3/src/views/` (минимум: Authors, Songs, Albums, News, Pictures, SiteUsers, Dictionaries, ListeningHistory, SitePlaylists, ShareLinks, Subscriptions, Tariffs, SponsrSync, Stats, Sync, Publish, PublishTemplates, Processes, Properties, PublicSettings, Promotions, Chat, StemJobs, Player, Home) и колонками: **View / Store / Filter Modal / Pagination / Воспроизводится ли баг / Примечание**. Заполнить результаты grep-проверки: каждая таблица должна быть проверена на наличие (а) `set<Entity>TableCurrentPage` в store, (б) `countRows` computed в Table.vue, (в) watcher на `countRows` в Table.vue. Баг = (а) есть + (б) есть + (в) **нет**. Закрепить решение: какие таблицы требуют фикса (по результатам research.md — Authors, Albums, Pictures, SiteUsers точно; остальные TBD по audit). Сослаться на OpenProject #50 в шапке документа.
- [x] T005 Создать per-feature документ `docs/features/pagination-filter-admin-tables.md` (Constitution FR-009). Структура: (1) **Bug description** — ссылка на OpenProject #50, скриншот/описание симптома, корневая причина (countRows = digests.length, нет watcher); (2) **Pattern** — эталон из `Songs/SongsTable.vue:998-1009` с пояснением; (3) **Affected tables** — список из `audit.md`; (4) **Fix template** — фрагмент кода watcher с комментариями и `@see` на этот документ (Constitution FR-006); (5) **Why News/Songs work** — ссылка на разные правильные паттерны (News через `setNewsTarget`+totalCount, Songs через watcher); (6) **Future work** — добавить `total` в backend-ответы Authors/Albums/Pictures/SiteUsers (отдельная задача); (7) **See also** — ссылки на `specs/300-author-pagination-filter-bug/{spec,plan,research,data-model,quickstart,audit}.md` и OpenProject #50.

**Checkpoint**: `audit.md` заполнен, `pagination-filter-admin-tables.md` создан — теперь точно известно, в каких файлах применять фикс, и есть куда сослаться из JSDoc.

---

## Phase 3: User Story 1 — Фикс таблицы «Авторы» (Priority: P1) 🎯 MVP

**Goal**: исправить баг #50 — при применении фильтра в таблице «Авторы» (например, «есть новые альбомы») с любой страницы N>1, сужающего выборку до ≤1 страницы, таблица показывает корректную последнюю доступную страницу с записями, а не пустоту.

**Independent Test**: на dev-машине с ≥2 страницами авторов открыть `Authors`, перейти на страницу 3, применить фильтр «Есть новые альбомы» (или любой, возвращающий ≤1 страницу) → видны записи результата фильтра, счётчик «Page 1 of N» корректен, currentPage = 1. Дополнительно — переход на стр. 3, фильтр возвращает 2 страницы → видны записи стр. 2, currentPage = 2. Сброс фильтра → возврат на стр. 1 (FR-006).

### Implementation for User Story 1

- [x] T006 [US1] Добавить `watch.countRows` в `webvue3/src/components/Authors/AuthorsTable.vue` (в секцию `watch:`, рядом с существующим watcher на `currentPage` на строках 402-407). Код:
```js
countRows: {
  handler(newCount) {
    // Сбрасываем на 1, если текущая страница вышла за пределы после загрузки/фильтрации.
    // Иначе (при первом монтировании) сохраняем страницу пользователя.
    // @see docs/features/pagination-filter-admin-tables.md (FR-006, эталон — Songs/SongsTable.vue:998-1009)
    const totalPages = Math.max(1, Math.ceil(newCount / this.perPage))
    if (this.currentPage > totalPages) {
      this.currentPage = 1
    }
  },
},
```
Закоммитить в JSDoc-формате (Options API — комментарий непосредственно перед `watch: {`). Убедиться, что `perPage` существует в data/computed (если нет — добавить data `perPage: 30`).

**Checkpoint**: фикс Authors применён. Можно вручную проверить User Story 1 по Scenario 1 из `quickstart.md`. После проверки — коммит `git commit -m "fix(authors): сброс currentPage при уменьшении выборки после фильтра (refs OP#50)"`.

---

## Phase 4: User Story 2 — Аналогичный фикс в других таблицах (Priority: P1)

**Goal**: применить тот же паттерн ко всем таблицам из `audit.md`, у которых воспроизводится баг. На основано research.md: **Albums, Pictures, SiteUsers** (Songs и News уже имеют правильные паттерны). Финальный список зависит от `audit.md`.

**Independent Test**: для каждой исправленной таблицы воспроизвести User Story 1 (перейти на стр. N>1 → применить сужающий фильтр → видны записи результата, currentPage корректен). Сценарии в `quickstart.md`: Scenario 2 (Albums), 3 (Pictures), 4 (SiteUsers).

### Implementation for User Story 2

- [x] T007 [P] [US2] Добавить `watch.countRows` в `webvue3/src/components/Albums/AlbumsTable.vue` (в секцию `watch:`, рядом с существующим watcher на `currentPage` на строках 330-...). Код — точно такой же, как T006, с заменой ссылки на JSDoc и проверкой наличия `perPage` в data/computed. Коммит: `fix(albums): сброс currentPage при уменьшении выборки после фильтра (refs OP#50)`.
- [x] T008 [P] [US2] Добавить `watch.countRows` в `webvue3/src/components/Pictures/PicturesTable.vue` (в секцию `watch:`, рядом с watcher на `currentPage` на строках 183-...). Тот же код, проверка `perPage`. Коммит: `fix(pictures): сброс currentPage при уменьшении выборки после фильтра (refs OP#50)`.
- [x] T009 [P] [US2] Добавить `watch.countRows` в `webvue3/src/components/SiteUsers/SiteUsersTable.vue` (в секцию `watch:`, рядом с watcher на `currentPage` на строках 456-461). Тот же код, проверка `perPage`. Учесть, что в SiteUsersTable уже есть хардкод `this.currentPage = 1` в `onTargetChange` (строка 491) — **не удалять** его (это отдельный случай смены target, watcher покрывает случай смены фильтра). Коммит: `fix(site-users): сброс currentPage при уменьшении выборки после фильтра (refs OP#50)`.
- [ ] T010 [P] [US2] (ОПЦИОНАЛЬНО, по результатам `audit.md`) Если `audit.md` выявит дополнительные таблицы с багом (например, Dictionaries, ListeningHistory, ShareLinks, Subscriptions, Tariffs, SponsrSync, Stats, Sync, Publish, PublishTemplates, Processes, Properties, PublicSettings, Promotions, Chat, StemJobs, Player, Home — TBD), добавить аналогичный watcher в их `<Entity>Table.vue`. Каждый файл — отдельная задача T010a/T010b/..., все параллельны. Если `audit.md` не выявил — задача считается выполненной пустой (записать в audit.md «проверено, баг не воспроизводится»).

**Checkpoint**: все таблицы из `audit.md` с багом исправлены. Можно проверить User Story 2 по Scenarios 2-4 из `quickstart.md`. Songs и News — НЕ трогаем (уже работают, регрессии быть не должно — Scenario 5,6).

---

## Phase 5: User Story 3 — Корректная работа UI-контролей пагинации (Priority: P2)

**Goal**: убедиться, что после применения фильтра корректно обновляются все UI-контролы пагинации: счётчик «Page X of Y», подсветка текущей страницы, состояние кнопок «Назад»/«Вперёд»/«Первая»/«Последняя».

**Independent Test**: на каждой исправленной таблице (Authors, Albums, Pictures, SiteUsers) выполнить Scenario 9 из `quickstart.md` — после фильтра проверить (а) счётчик, (б) подсветку, (в) состояние кнопок. Дополнительно — Scenario 8 (empty-state при `totalCount == 0`) и Scenario 7 (race condition при медленной сети).

### Validation for User Story 3

- [ ] T011 [P] [US3] Выполнить ручную проверку Scenario 8 (empty-state) на всех 4 исправленных таблицах. Зафиксировать результат в комментарии к коммиту/PR (если есть проблемы — завести отдельный тикет, не блокировать PR с фиксом).
- [ ] T012 [P] [US3] Выполнить ручную проверку Scenario 9 (UI-контроли) на всех 4 исправленных таблицах. Зафиксировать результат аналогично.
- [ ] T013 [US3] Выполнить ручную проверку Scenario 7 (race condition) на таблице Authors. Если обнаружится проблема (промежуточные ответы перебивают финальный) — завести отдельный тикет «Race condition в admin tables» и не блокировать текущий PR (см. research.md Decision 4 / Risk R2 — это known limitation, отдельная задача).

**Checkpoint**: User Story 3 проверена. Если всё ОК — фикс готов к Polish-фазе. Если обнаружены **новые** баги (не race condition, который known limitation) — вернуться в Phase 3/4 и дофиксить.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Цель**: привести код/документацию к CI-требованиям (7/7 PASS), подготовить PR.

- [x] T014 [P] Запустить линтеры: `./gradlew :karaoke-web:ktlintCheck` + `cd webvue3 && npm run lint:check` + `cd webvue3 && npx prettier --check "src/**/*.{vue,js,ts,json}"`. Все три — PASS. Если FAIL — исправить (только в файлах, которые менялись в этой задаче: `webvue3/src/components/{Authors,Albums,Pictures,SiteUsers}/*Table.vue`).
- [ ] T015 [P] Обновить `docs/architecture-notes.md` (Pass 14+ для архитектурных изменений) — краткая запись о фиксе: «Pass N: исправлен баг #50 (OP) — добавлен watcher на countRows в admin tables (Authors, Albums, Pictures, SiteUsers) для сброса currentPage при уменьшении выборки после фильтра. Backend не менялся». Сослаться на `specs/300-author-pagination-filter-bug/`.
- [ ] T016 [P] Обновить `livedocs/features/000-admin-tables.md` (или создать, если нет) — краткое описание паттерна пагинации admin-таблиц с watcher на countRows и ссылкой на `docs/features/pagination-filter-admin-tables.md`. Это требование Constitution FR-014 (LiveDocs обновляются при изменении bounded context или C4 уровня).
- [ ] T017 Пересобрать `webvue3`: `cd webvue3 && npm run build && npm run format:check`. Убедиться, что `dist/` обновился и в нём присутствуют исправленные `*.vue` (через `grep -l "countRows" dist/assets/*.js | head -5` или аналогично).
- [ ] T018 Пересобрать Docker-образ: `cd deploy && bash do.sh build_webvue3`. Убедиться, что образ успешно собран и содержит исправленные `*.vue` (через `docker run --rm <image> grep -l "countRows" /app/assets/*.js` или аналогично). Согласно AGENTS.md § «Обязательная проверка после ЛЮБОГО изменения кода», шаги 4 (Vite) и 5 (Docker) обязательны.
- [ ] T019 Выполнить полный smoke-чек по `quickstart.md`: пройти все 9 сценариев на dev-машине. Зафиксировать результат в комментарии к PR (если есть проблемы — зафиксировать как «known limitations» или откатить отдельные коммиты).
- [ ] T020 Подготовить PR: `git push -u origin 300-author-pagination-filter-bug && gh pr create --base master`. В описании PR: (а) ссылка на OpenProject #50, (б) ссылка на `specs/300-author-pagination-filter-bug/spec.md`, (в) список изменённых файлов (4 `*.vue` + `pagination-filter-admin-tables.md` + `audit.md` + правки в `architecture-notes.md` и `livedocs/features/`), (г) чеклист «CI 7/7 PASS» (Constitution), (д) скриншот до/после (если есть), (е) «Manual validation: все 9 сценариев из `quickstart.md` пройдены».
- [ ] T021 Дождаться `gh pr checks` (CI 7/7) и одобрения пользователем. После approval — `gh pr merge --merge` (БЕЗ `--delete-branch`, AGENTS.md § «CI-gate для master» — ветка живёт после мёрджа). После мёрджа — закрыть OpenProject #50 (`./tools/tracker.sh mark-review 50` затем `./tools/tracker.sh close-issue 50`).

**Checkpoint**: PR создан, CI зелёный, manual validation пройдена, PR смёржен, OpenProject #50 закрыт.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: нет зависимостей — стартует немедленно.
- **Foundational (Phase 2)**: зависит от Setup (T1-T3). **Блокирует** все user stories (Phase 3+).
- **User Stories (Phase 3-5)**: все зависят от Foundational (Phase 2) → могут идти параллельно или последовательно в порядке приоритета (P1 → P1 → P2).
- **Polish (Phase 6)**: зависит от завершения **всех** user stories.

### User Story Dependencies

- **User Story 1 (P1)**: после Foundational → независим.
- **User Story 2 (P1)**: после Foundational → независим от US1 (разные файлы). Можно делать параллельно с US1, если аудит подтвердил, что фикс нужен в этих таблицах.
- **User Story 3 (P2)**: после завершения US1 и US2 (валидирует их результат).

### Within Each Phase

- Phase 2: T004 и T005 — можно параллельно (разные файлы).
- Phase 3: T006 — одна задача.
- Phase 4: T007, T008, T009 — параллельно (разные файлы). T010 — параллельно с T007-T009 (разные файлы, отдельные сущности).
- Phase 5: T011, T012 — параллельно (разные сценарии). T013 — последовательно (зависит от T011/T012).
- Phase 6: T014, T015, T016 — параллельно. T017 — последовательно (зависит от T014). T018 — последовательно (зависит от T017). T019 — последовательно (зависит от T018). T020 — последовательно (зависит от T019). T021 — последовательно (зависит от T020).

### Parallel Opportunities

- **Phase 1**: T002, T003 параллельно.
- **Phase 2**: T004, T005 параллельно.
- **Phase 4**: T007, T008, T009, T010 (если есть) — все параллельно.
- **Phase 5**: T011, T012 параллельно.
- **Phase 6**: T014, T015, T016 параллельно.

---

## Parallel Example: User Story 2

```bash
# Параллельно применить фикс к трём таблицам:
Task: "Добавить watch.countRows в webvue3/src/components/Albums/AlbumsTable.vue"
Task: "Добавить watch.countRows в webvue3/src/components/Pictures/PicturesTable.vue"
Task: "Добавить watch.countRows в webvue3/src/components/SiteUsers/SiteUsersTable.vue"
Task: "Добавить watch.countRows в webvue3/src/components/Dictionaries/DictionariesTable.vue (если audit выявил)"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Завершить Phase 1: Setup.
2. Завершить Phase 2: Foundational.
3. Завершить Phase 3: User Story 1 (фикс Authors).
4. **STOP and VALIDATE**: Scenario 1 из `quickstart.md` вручную на dev-машине.
5. Если MVP нужен немедленно (минимум для OP#50) — можно сделать PR только с Phase 3 + минимальным Phase 6 (T014, T017, T018, T020, T021). Phase 4-5 — следующим PR.

### Incremental Delivery (рекомендуется)

1. Setup + Foundational → готов фундамент (audit + per-feature doc).
2. User Story 1 → тест → demo (MVP — закрывает OP#50).
3. User Story 2 → тест → demo (расширение фикса на остальные таблицы).
4. User Story 3 → тест → demo (валидация UI-контролей).
5. Каждая стадия добавляет ценность, не ломая предыдущие.

### Parallel Team Strategy

С несколькими разработчиками:

1. Команда вместе завершает Setup + Foundational.
2. После Foundational:
   - Developer A: User Story 1 (Authors).
   - Developer B: User Story 2 (Albums, Pictures, SiteUsers — параллельно).
   - Developer C: User Story 2 (T010 — дополнительные таблицы из audit).
3. User Story 3 (валидация) — последовательно после A+B.

---

## Notes

- [P] задачи = разные файлы, нет зависимостей.
- [Story] лейбл связывает задачу с user story для traceability.
- Каждая user story должна быть независимо завершаемой и тестируемой.
- Коммит после каждой задачи или логической группы (T006, потом T007+T008+T009 как отдельные коммиты).
- Останавливаться на любом checkpoint для валидации story независимо.
- Избегать: расплывчатых задач, конфликтов на одном файле, межstory зависимостей, ломающих независимость.
- Согласно AGENTS.md: **не коммитить без явного запроса пользователя** — финальный коммит/PR ожидает явной инструкции «коммить» или «создавай PR».
- Согласно AGENTS.md: перед каждым коммитом — линтеры (`./gradlew :karaoke-web:ktlintCheck` + `webvue3/npm run lint:check` + `webvue3/npx prettier --check`).
- Согласно AGENTS.md: после ЛЮБОГО изменения кода — обязательная 5-шаговая проверка (compile, lint, bootJar, vite, docker). Для этой задачи bootJar не нужен (бэкенд не менялся), но остальные 4 шага — обязательны (Phase 6 → T014, T017, T018).