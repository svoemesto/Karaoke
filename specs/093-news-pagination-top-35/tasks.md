# Tasks: Новости — пагинация над таблицей, не больше 35 строк

**Input**: Design documents from `/home/nsa/Karaoke/specs/093-news-pagination-top-35/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/, quickstart.md

**Tests**: не запрошены в спеке и в AGENTS.md для стека `webvue3` их нет — секции Tests опущены. Проверка выполняется вручную по `quickstart.md` (Q1–Q5).

**Organization**: один user story (P1) + Polish. Phase 1 (Setup) и Phase 2 (Foundational) опущены — фича не требует инициализации проекта или блокирующих пререквизитов (бэкенд и публичный фронт уже готовы, см. `specs/090-news-pagination`; см. `plan.md` → Technical Context).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: можно выполнять параллельно (разные файлы, нет зависимостей).
- **[Story]**: метка user story (только `US1` — единственная история в спеке).
- В описании — точные пути файлов.

## Path Conventions

- Web app, правка только во frontend: `webvue3/src/components/News/`.
- Бэкенд (`karaoke-app`, `karaoke-web`) и публичный фронт (`karaoke-public`) — вне области изменений (см. `plan.md` → Project Structure и `data-model.md` → «Что НЕ меняется»).

---

## Phase 3: User Story 1 — Пагинация над таблицей и perPage=35 (Priority: P1) 🎯 MVP

**Goal**: в админском разделе «Новости» (`webvue3/src/components/News/NewsTable.vue`) элемент `<b-pagination>` виден над таблицей `<b-table>`, а не под ней; размер страницы — 35 строк (а не 50).

**Independent Test** (см. `quickstart.md` Q1–Q4):
1. Открыть раздел «Новости» с >= 36 строками в `tbl_news` — пагинация видна **над** таблицей без прокрутки вниз.
2. При 100 строках в `tbl_news` — 3 страницы, по 35/35/30 строк, число страниц = `ceil(100/35) = 3`.
3. При 0 строк — пагинация скрыта, нет 35 пустых `<tr>`.
4. CRUD (создание/редактирование/удаление) и переключение LOCAL↔REMOTE работают без регрессий, размер страницы остаётся 35.

### Implementation for User Story 1

- [x] T001 [P] [US1] Изменить `NEWS_PER_PAGE` с 50 на 35 в `webvue3/src/components/News/store.js` (строка 11, единственное вхождение `NEWS_PER_PAGE = 50` → `NEWS_PER_PAGE = 35`; проверить `grep -n NEWS_PER_PAGE webvue3/src/components/News/store.js` после правки — должно быть одно совпадение со значением 35).
- [x] T002 [P] [US1] Перенести блок `<b-pagination v-model="currentPageModel" :total-rows="totalCount" :per-page="perPage" align="center" size="sm" />` из позиции «после `</b-table>`» в позицию «перед `<b-table>`» внутри `<div class="news-table-body">` в `webvue3/src/components/News/NewsTable.vue` (исходно строки 124–130, после правки — выше `<b-table>`; сохранить все пропсы и JSDoc компонента как есть).

**Checkpoint**: после T001+T002 User Story 1 полностью функциональна и проверяется вручную по `quickstart.md` Q1–Q4.

> **Параллельность**: T001 и T002 правят **разные файлы** (`store.js` vs `NewsTable.vue`) и не зависят друг от друга → могут выполняться одновременно одним агентом за один проход или разными разработчиками.

---

## Phase 4: Polish & Cross-Cutting Concerns

**Purpose**: проверки линтера, ручные сценарии, CI-gate, PR.

- [x] T003 [P] Прогнать `cd /home/nsa/Karaoke/webvue3 && npm run lint:check` и убедиться, что `webvue3/.eslint-baseline.json` **не вырос** (0 новых нарушений). Если baseline вырос — починить или явно зафиксировать в baseline-файле с обоснованием в commit-сообщении (см. `AGENTS.md` → «CI-gate для master» и `constitution.md` → Principle VI). — выполнено: `npm run lint:check` exit 0, `.eslint-baseline.json` идентичен (`diff` пустой).
- [ ] T004 Пройти `quickstart.md` Q1–Q5 вручную на локальном docker-стенде (`deploy/do.sh start`, `cd webvue3 && npm run dev` или `deploy/do.sh build_start_web`); зафиксировать результат в комментарии к PR.
- [ ] T005 Создать коммит в ветке `093-news-pagination-top-35` (ровно 2 файла: `webvue3/src/components/News/NewsTable.vue`, `webvue3/src/components/News/store.js`; `git status --short` перед `git add`); запушить `git push -u origin 093-news-pagination-top-35`; открыть PR в `master` через `gh pr create --base master`; **дождаться CI 7/7 SUCCESS** (`gh pr checks` / `gh run watch`); затем `gh pr merge --merge --delete-branch` (см. `AGENTS.md` → «CI-gate для master» — NON-NEGOTIABLE).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: не требуется — опущен.
- **Foundational (Phase 2)**: не требуется — опущен.
- **User Story 1 (Phase 3)**: может стартовать сразу. Нет блокирующих пререквизитов (проект инициализирован, `webvue3` собирается, серверная пагинация из `specs/090-news-pagination` уже работает).
- **Polish (Phase 4)**: зависит от завершения Phase 3.

### User Story Dependencies

- **User Story 1 (P1)**: единственная история, нет зависимостей от других.

### Within Each User Story

- T001 (правка `store.js`) и T002 (правка `NewsTable.vue`) независимы — оба `[P]`.
- Story complete (T001+T002 done) → можно переходить к Phase 4 (T003–T005).

### Parallel Opportunities

- T001 ∥ T002 — параллельно (разные файлы, нет зависимостей).
- T003 — параллельно с T004 в части «запустить lint-чек в фоне», но по UX лучше делать последовательно: T003 (lint) → T004 (ручная проверка) → T005 (PR).
- Phase 3 (всего 2 задачи) в целом достаточно мала, чтобы один агент выполнил её за один проход.

---

## Parallel Example: User Story 1

```bash
# Запустить обе правки параллельно (разные файлы — нет конфликтов):
Task: "Изменить NEWS_PER_PAGE с 50 на 35 в webvue3/src/components/News/store.js"
Task: "Перенести <b-pagination> над <b-table> в webvue3/src/components/News/NewsTable.vue"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. **Phase 3**: T001 + T002 (параллельно) — две правки в `webvue3/src/components/News/`.
2. **STOP and VALIDATE**: `npm run dev` → раздел «Новости» → пройти `quickstart.md` Q1–Q4.
3. **Phase 4**: T003 (lint) → T004 (полная ручная проверка Q1–Q5) → T005 (PR + CI 7/7 + merge).

### Incremental Delivery

- В этой фиче **один инкремент** (US1 = MVP = весь объём фичи). Никаких P2/P3 — спек не задаёт дополнительных историй.
- После merge в `master` фича считается доставленной.

### Parallel Team Strategy

- Один разработчик/агент достаточен — Phase 3 = 2 параллельные правки в разных файлах, больше работы в этом PR нет.
- Если стенд `dev-pc`/`dev` (см. `constitution.md` → «Ограничения и доступы агента», п. 6) — пересборка `webvue3` через `deploy/do.sh build_start_web` разрешена без согласия пользователя; в противном случае — пользователь пересобирает контейнер сам.

---

## Notes

- [P] задачи = разные файлы, нет зависимостей. T001 и T002 здесь единственный случай.
- [Story] метка: только `US1` — единственная история.
- Каждая user story должна быть независимо завершаемой и проверяемой — здесь это US1, проверка по `quickstart.md` Q1–Q4.
- Тестов нет → правило «tests fail before implementation» не применяется.
- Коммит после Phase 3 (T001+T002) — один коммит на оба файла; коммит-сообщение в стиле `AGENTS.md`: на русском, коротко, по существу (например, `news: перенести пагинацию над таблицей и снизить perPage до 35`).
- Stop at checkpoint после Phase 3 — пройти `quickstart.md` Q1–Q4, прежде чем переходить к Phase 4.
- Избегать: размытых задач, конфликтов в одном файле, кросс-story зависимостей. В этой фиче ничего из этого нет.
