# Tasks: Закрома — сброс state при навигации от автора к тайлам (через header-back-link или browser back)

**Input**: Design documents from `/specs/255-fix-zakroma-state-reset-on-back-nav/`

**Prerequisites**: plan.md ✅, spec.md ✅, checklist ✅.

**Tests**: автотесты отсутствуют (AGENTS.md); приёмка — пользователем в браузере по quickstart.md V-1..V-5 (inline ниже).

**Organization**: 1 watch-блок в одном файле → один MVP-таск + проверки + LiveDoc.

## Format: `[ID] [P?] [Story] Description`

## Phase 1: Setup

- [ ] T001 Подтвердить, что ветка `255-fix-zakroma-state-reset-on-back-nav` активна (`git branch --show-current`), и что предыдущая спека `254` уже реализована в этом коммите (`grep -E "AppHeader :back=\"zakromaHeaderBack\"" ZakromaView.vue`).
- [ ] T002 [P] Запустить `bash tools/check-eslint-baseline.sh karaoke-public` — 0/0 violations baseline.
- [ ] T003 [P] Запустить `cd karaoke-public && npm run build` — PASS, bundle должен содержать `zakromaHeaderBack` (проверить `grep`).

**Checkpoint**: Phase 1 complete.

---

## Phase 2: Foundational

**Purpose**: подтвердить наличие бага перед правкой (через DevTools-замер или формальное рассуждение).

- [ ] T004 Прочитать `ZakromaView.vue:498-505` (секция `computed:` — там определён `zakromaHeaderBack`). Проверить, что **нет** существующего watcher'а на `$route.query.author` (`grep -E "\\\$route.query" ZakromaView.vue` → пусто). **Это подтверждает наличие бага** (state не сбрасывается при смене query).

**Checkpoint**: баг подтверждён. Phase 3 готов к правке.

---

## Phase 3: User Story 1 + 2 + 3 — добавление watcher'а на `$route.query.author` (Priority: P1) 🎯 MVP

**Goal**: vue-router меняет URL → watcher реагирует → state сбрасывается (или переключается) → UI перерисовывается.

**Independent Test**: после применения watcher'а — клик на header-back-link или browser BACK переключает UI на сетку тайлов. См. quickstart.md ниже (V-1..V-5).

### Implementation

- [ ] T005 [US1] В `karaoke-public/src/views/ZakromaView.vue`, в секции `watch:` (рядом с `zakroma: { handler(list) {...} }` и `specialBucket: { handler(list) {...} }`, ~строка 510), добавить новый watcher:
    ```js
    // Спек 255: vue-router при навигации /zakroma?author=X → /zakroma (тот же path,
    // другой query) НЕ пересоздаёт инстанс компонента — `data()`-properties остаются
    // со старыми значениями. Watcher реагирует на любую смену ?author= и
    // синхронизирует data-state с URL:
    //   - если query.author стал пуст → снять выбор (header-back / browser back);
    //   - если query.author изменился на другой → перезагрузить стрим для нового автора.
    '$route.query.author'(newAuthor) {
      if (!newAuthor && this.authorChosen) {
        this.selectedAuthor = ''
        this.authorChosen = false
        this.specialBucketShown = false
        this.songFilter = ''
      } else if (newAuthor && newAuthor !== this.selectedAuthor) {
        this.selectedAuthor = newAuthor
        this.authorChosen = true
        this.songFilter = ''
        this.loadZakromaStream({ author: newAuthor, expectedCount: undefined })
      }
    },
    ```
  ВАЖНО: после `zakroma` и `specialBucket` watcher-объектов, **перед** закрывающей `},` секции watch.

- [ ] T006 [US1] Проверить, что новый watcher-блок НЕ нарушает существующие watcher'ы `zakroma` и `specialBucket`. Структурно — каждый объектный watcher-блок в Vue 2/3 Options API разделяется запятой; добавляем третий. **Lint check**: `cd karaoke-public && npm run lint` PASS.

- [ ] T007 [US1] Запустить `cd karaoke-public && npm run build` — PASS. После — в `dist/assets/*.js` найти `data` секцию ZakromaView, убедиться, что watcher-функция присутствует в скомпилированном JS (`grep -E "query\.author|\$\$route" dist/assets/*.js | head -5`).

- [ ] T008 [US1, US2] **Ручная проверка в браузере**: открыть `/zakroma?author=Машина Времени`, кликнуть на header-back-link «← К списку авторов», проверить:
    - URL стал `/zakroma` без query.
    - `document.querySelector('.km-author-header-sticky')` = null (фильтр-блок скрыт).
    - `document.querySelector('.at-grid, .km-author-tiles, [data-author-tiles]')` ≠ null (видна сетка тайлов).
    **⚠ Требует браузера — отложено на ручную проверку пользователем.**

- [ ] T009 [US1, US2] **Ручная проверка browser BACK**: на `/zakroma?author=X` нажать browser «←» — должна отрисоваться сетка тайлов. **⚠ Требует браузера**.

- [ ] T010 [US3] **Ручная проверка deep-link / смены автора**: на `/zakrama?author=A` через address bar поправить URL на `/zakrama?author=B` — контент перерисовывается на B. **⚠ Требует браузера**.

- [ ] T011 **Перепроверить спеку 254 — нет регрессии**:
    - На `/zakroma?author=X` кликнуть header-back-link → URL `/zakroma`, тайлы показаны (не как раньше — без сброса).
    - На `/zakroma` (нет автора) header-back-link скрыт.
    - На `/zakroma?specialBucket=true` header-back-link виден, клик → `/zakroma` (но `specialBucketShown` остаётся true — частичная регрессия, см. assumption (d) в спеке; out-of-scope для спеки 255, см. "Что НЕ входит").
    **⚠ Требует браузера.**

**Checkpoint**: основной баг устранён, регрессии нет.

---

## Phase 4: Polish & LiveDocs

- [ ] T012 [P] Запустить `cd karaoke-public && npm run lint` — 0 warnings.
- [ ] T13 [P] Запустить `bash tools/check-eslint-baseline.sh karaoke-public` — 0/0 violations.
- [ ] T14 [P] Запустить `cd karaoke-public && npm run build` — PASS.
- [ ] T15 [P] Запустить `./gradlew :karaoke-app:bootJar :karaoke-web:bootJar --parallel` — `:karaoke-web:bootJar UP-TO-DATE`, бэкенд не задет.
- [ ] T16 Создать LiveDoc `livedocs/features/255-fix-zakroma-state-reset-on-back-nav.md` по образцу `livedocs/features/252-fix-zakroma-progressbar.md`. Frontmatter: status: Active, slug: 255-, related → spec.md + plan.md + 254 + 250 + 008 + 012. Содержимое: корень бага (data() в Options API не пересчитывается), решение (watcher на $route.query.author), US1-US3, FR-001..FR-007, AC1..AC7, связанные LiveDocs, код-список (1 файл), история.
- [ ] T17 В `livedocs/features/254-fix-zakroma-header-back-link.md` добавить секцию «Bug-fix 255» — короткий параграф: «Спека 255 устраняет регрессию спеки 254: добавлен `$route.query.author` watcher в `ZakromaView.vue`, который сбрасывает state при навигации к тайлам. Без watcher'а vue-router обновляет URL, но `data`-properties остаются, поэтому `v-if="authorChosen"` остаётся true → старая view рендерится, новая не показывается».
- [ ] T18 Добавить запись `Pass 255` в `livedocs/architecture-notes.md` (по образцу Pass 251 / 252 / 253 / 254).
- [ ] T19 Проверить `git status` / `git diff --stat`:
    - Только `karaoke-public/src/views/ZakromaView.vue` + 3 LiveDoc.
    - Никаких `package.json`, lockfile, backend, secrets.
- [ ] T20 (CI-gate для master, NON-NEGOTIABLE) — **⚠ требует согласия пользователя**: `git push -u origin 255-fix-zakroma-state-reset-on-back-nav`, `gh pr create --base master`, `gh pr checks` → PASS, `gh pr merge --merge`. Lifecycle: ветка живёт после мёрджа (без `--delete-branch`).

---

## Dependencies & Execution Order

- **Phase 1 → Phase 2 → Phase 3 (T005) → Phase 3 (T006..T011 проверки) → Phase 4**.
- T005 — единственный код-таск. Все остальные — проверки + LiveDocs + PR.
- T016-T18 могут быть параллельны (разные файлы LiveDocs).

## Parallel Opportunities

- Phase 1: T002 + T003 параллельно (terminal).
- Phase 4: T12-T15 параллельно (разные команды).
- Phase 4: T16 + T17 + T18 параллельно (разные файлы LiveDocs).

## Implementation Strategy

### MVP First

1. Phase 1 (T001-T003) — старт-чек.
2. Phase 2 (T004) — подтверждение бага по коду (без браузера).
3. Phase 3 (T005) — добавление watcher'а (1 commit).
4. Phase 3 (T006-T007) — lint + build проверки (без браузера).
5. Phase 3 (T008-T011) — браузерные проверки (отложено на пользователя).
6. Phase 4 (T12-T18) — LiveDocs + PR.

### Incremental Delivery

- После MVP (T005): баг устранён, можно деплоить.
- Phase 4 — polish, можно отложить до согласования merge.

## Notes

- Watcher использует строковый синтаксис `'$route.query.author'` — стандартный паттерн в Vue 2/3 Options API для deep path в `watch:`.
- Watcher НЕ трогает Vuex state — `state.zakroma` остаётся закэшированным. Безопасно, потому что computed `displayedZakroma` использует store, но отображается только при `authorChosen = true`. После сброса store не используется до следующего выбора автора.
- Перед коммитом: `git status` + `git diff --stat` (AGENTS.md).
- Backend не пересобирается (NON-NEGOTIABLE; только bootJar для UP-TO-DATE-проверки в T15).
