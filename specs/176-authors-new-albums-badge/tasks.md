---

description: "Task list for 176-authors-new-albums-badge"

---

# Tasks: Бейдж «новые альбомы» в пункте меню «Авторы»

**Input**: Design documents from `/specs/176-authors-new-albums-badge/`
**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅, contracts/ ✅, quickstart.md ✅
**Tests**: НЕ запрашивались в спеке (CI для бэка отсутствует — см. AGENTS.md «Тесты»). Валидация — ручная по `quickstart.md`.

**Organization**: 3 user stories из spec.md. Каждая фаза — независимый инкремент.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: можно параллельно (разные файлы, нет зависимостей)
- **[Story]**: к какому user story относится (US1/US2/US3)
- Полные пути к файлам в описании

## Path Conventions

Multi-module Gradle (backend `karaoke-app` + frontend `webvue3`). Все пути — от корня репо.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Никакой отдельной инфраструктуры для этой фичи не требуется — проект уже развёрнут (`karaoke-app`, `webvue3`, Postgres). Только убедиться, что ветка зарезервирована и текущая.

- [X] T001 Зарезервировать номер ветки через `./tools/reserve-branch-number.sh authors-new-albums-badge` и переключиться на `176-authors-new-albums-badge` *(сделано: ветка создана, spec-директория переименована 175→176 для соответствия, stale `seq/175` удалён)*

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Нет блокирующих prerequisites для этой фичи. Schema `tbl_authors` уже существует, Vuex-стор `Authors` уже есть, `App.vue` уже монтирует polling. Phase 2 пропущен — переход к Phase 3 (User Story 1).

**Checkpoint**: Foundation ready → можно начинать US1.

---

## Phase 3: User Story 1 — Бейдж с числом авторов с `haveNewAlbum` (Priority: P1) 🎯 MVP

**Goal**: В левом сайдбаре `webvue3` рядом с пунктом «Авторы» появляется красный бейдж с числом авторов, у которых `haveNewAlbum = true`. Если число 0 — бейдж скрыт.

**Independent Test**: Открыть админку при наличии ≥ 1 автора с `haveNewAlbum = true` в БД — бейдж появляется в течение ≤ 20 сек с правильным числом. Проверка через DevTools → Elements: `<span class="authors-nav-badge">{{N}}</span>`. Подробнее — [quickstart.md Сценарий 1](./quickstart.md) и [Сценарий 2](./quickstart.md).

### Implementation for User Story 1

**Backend (2 файла, можно параллельно):**

- [X] T002 [P] [US1] Добавить companion-метод `Author.countWithNewAlbum(database: KaraokeConnection): Int` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Author.kt` рядом с `loadList`/`getAuthorById` (см. [research.md D-2](./research.md) — raw SQL `SELECT COUNT(*) FROM tbl_authors WHERE watched = true AND (ym_id <> '' OR vk_id <> '') AND (last_album_ym <> last_album_processed OR last_album_vk <> last_album_processed)`, шаблон — `SiteChatMessage.countUnreadFromUsers` в `karaoke-app/.../model/SiteChatMessage.kt:271-282`; обязательно KDoc с `@see docs/features/...` или ссылкой на спеку + комментарий-зеркало на `Author.haveNewAlbum` и `Author.getWhereList["haveNewAlbum=+"]`)
- [X] T003 [US1] Добавить endpoint `apisAuthorsWithNewAlbumCount` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt` рядом с `apisAuthorsDigest` (строка 6040): `@PostMapping("/authors/withnewalbumcount") @ResponseBody fun apisAuthorsWithNewAlbumCount(): Int = Author.countWithNewAlbum(WORKING_DATABASE)` + KDoc со ссылкой на контракт (depends on T002)

**Frontend (2 файла, можно параллельно после бэка готов):**

- [X] T004 [P] [US1] В `webvue3/src/components/Authors/store.js` добавить state-поле `authorsWithNewAlbumCount: 0`, getter `getAuthorsWithNewAlbumCount`, mutation `setAuthorsWithNewAlbumCount`, action `loadAuthorsWithNewAlbumCount` (шаблон — `loadSubmittedAssignmentsCount` в `SongEditor/store.js:254-265`; URL `'/api/authors/withnewalbumcount'`, парсинг `parseInt(data, 10) || 0`; JSDoc со ссылкой на `contracts/api-authors-withnewalbumcount.md`)
- [X] T005 [US1] В `webvue3/src/App.vue` заменить `<li class="nav-item"><router-link class="nav-link" to="/authors">Авторы</router-link></li>` (строка 17-19) на версию с `<router-link class="nav-link authors-nav-link" to="/authors">Авторы <span v-if="authorsWithNewAlbumCount > 0" class="authors-nav-badge">{{ authorsWithNewAlbumCount }}</span></router-link>` + добавить computed `authorsWithNewAlbumCount()` в блоке `computed:` (рядом с `chatUnreadTotal`/`submittedAssignmentsCount`, строки 172-179) — `return this.$store.getters.getAuthorsWithNewAlbumCount` (depends on T004)

**Checkpoint**: После T002..T005 US1 полностью функциональна: backend отдаёт число, frontend отображает бейдж. Можно открыть админку и проверить визуально (бейдж покажет число, но polling пока не запущен — US3).

---

## Phase 4: User Story 2 — Визуальная консистентность с бейджами «Чат» / «Задания редактора» (Priority: P1)

**Goal**: Бейдж «Авторы» визуально идентичен бейджам «Чат» и «Задания редактора» (тот же `#d02c3a`, та же форма, тот же шрифт).

**Independent Test**: Сравнить computed-стили трёх бейджей в DevTools (`background-color`, `border-radius`, `min-width`, `height`, `font-size`, `color`). Подробнее — [quickstart.md Сценарий 5](./quickstart.md).

### Implementation for User Story 2

- [X] T006 [US2] В `webvue3/src/App.vue` (внутри тега `<style>` в нижней части файла, рядом с `.chat-nav-link`/`.chat-nav-badge` на строках 733-748 и `.songeditor-nav-link`/`.songeditor-nav-badge` на строках 750-765) добавить CSS-классы `.authors-nav-link { display: flex !important; align-items: center; justify-content: space-between; }` и `.authors-nav-badge { background-color: #d02c3a; color: #fff; border-radius: 10px; min-width: 18px; height: 18px; line-height: 18px; text-align: center; font-size: 11px; padding: 0 5px; }` — точная копия `.chat-nav-link`/`.chat-nav-badge` (depends on T005, тот же файл)

**Checkpoint**: После T006 все три бейджа визуально идентичны.

---

## Phase 5: User Story 3 — Автообновление через polling каждые 20 сек (Priority: P2)

**Goal**: Бейдж обновляется автоматически без F5 — каждые 20 сек фоновый запрос за новым числом. Cleanup таймера при размонтировании. Resilience к ошибкам сети (предыдущее значение сохраняется).

**Independent Test**: Открыть DevTools → Network, увидеть `POST /api/authors/withnewalbumcount` каждые ~20 сек. Изменить число «новых» авторов в БД — через ≤ 20 сек бейдж обновится без F5. Перевести браузер в Offline — бейдж не пропадает. Подробнее — [quickstart.md Сценарий 3](./quickstart.md) и [Сценарий 4](./quickstart.md).

### Implementation for User Story 3

- [X] T007 [US3] В `webvue3/src/App.vue` (внутри `export default { data() {...}, mounted() {...}, beforeUnmount() {...} }`, рядом с `chatUnreadPollTimer`/`submittedAssignmentsPollTimer`):
  1. Добавить `const AUTHORS_NEW_ALBUMS_POLL_INTERVAL_MS = 20000` рядом с `CHAT_UNREAD_POLL_INTERVAL_MS` (строка 135) и `SONGEDITOR_SUBMITTED_POLL_INTERVAL_MS` (строка 138)
  2. В `data()` добавить `authorsWithNewAlbumPollTimer: null` рядом с `chatUnreadPollTimer` (строка 168)
  3. В `mounted()` (после `loadSubmittedAssignmentsCount`+`setInterval` на строках 231-235) добавить `this.$store.dispatch('loadAuthorsWithNewAlbumCount')` + `this.authorsWithNewAlbumPollTimer = setInterval(() => this.$store.dispatch('loadAuthorsWithNewAlbumCount'), AUTHORS_NEW_ALBUMS_POLL_INTERVAL_MS)`
  4. В `beforeUnmount()` (после cleanup `submittedAssignmentsPollTimer` на строках 247-250) добавить `if (this.authorsWithNewAlbumPollTimer) { clearInterval(this.authorsWithNewAlbumPollTimer); this.authorsWithNewAlbumPollTimer = null }` (depends on T005, тот же файл)

**Checkpoint**: После T007 фича полностью работает: бейдж появляется, обновляется, не ломается при ошибках.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Финальная валидация, линт, документация.

- [X] T008 [P] Обновить `docs/architecture-notes.md` — добавить запись о PR в конец файла (Pass 14+ конвенция): `Pass 58: feat(admin) — красный бейдж «новые альбомы» в пункте меню «Авторы» (2026-08-12, branch 176-authors-new-albums-badge)`
- [X] T009 Прогнать `pre-commit run --files ...` на изменённых файлах — ktlint/ESLint/prettier все PASS
- [ ] T010 Прогнать ручную валидацию по [quickstart.md](./quickstart.md) — все 7 сценариев + smoke E2E; записать результат (PASS/FAIL) в PR-описание *(требует пересборки `karaoke-app` + `webvue3` — на этой машине под пользователем `nsa` запрещено; пользователь делает вручную)*
- [ ] T011 Создать PR через `gh pr create --base master --title "feat: бейдж «новые альбомы» в пункте меню «Авторы»" --body "..."`, дождаться CI 7/7 SUCCESS, merge в master (НЕ удалять ветку — см. AGENTS.md «Жизненный цикл feature-ветки») *(требует явного согласия пользователя)*

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: без зависимостей
- **Phase 2 (Foundational)**: пропущена (нет prerequisites)
- **Phase 3 (US1)**: depends on Phase 1
- **Phase 4 (US2)**: depends on Phase 3 (тот же файл App.vue, что T005)
- **Phase 5 (US3)**: depends on Phase 3 (тот же файл App.vue, что T005)
- **Phase 6 (Polish)**: depends on Phase 3, 4, 5

### User Story Dependencies

- **US1 (P1)**: после Setup. Нет зависимостей от других stories — самостоятельная.
- **US2 (P1)**: depends on US1 (CSS для бейджа не имеет смысла без бейджа). Но функционально независима — можно было бы сделать стили заранее.
- **US3 (P2)**: depends on US1 (polling без бейджа бесполезен). Но функционально независима.

**Практический порядок**: T002..T005 → T006 → T007 → T008..T011. Между US2 и US3 нет файловых конфликтов (T006 — секция `<style>`, T007 — `<script>`), но `App.vue` — общий файл → строго последовательно.

### Within Each User Story

- T002 и T003 — последовательно (T003 использует T002)
- T004 — параллельно с T002/T003 (другой файл)
- T005 — последовательно после T004 (использует getter из T004)
- T006 — последовательно после T005 (тот же файл, секция `<style>`)
- T007 — последовательно после T005 (тот же файл, секция `<script>`)

### Parallel Opportunities

- **T002 || T003 || T004** — все три в разных файлах (Author.kt, ApiController.kt, Authors/store.js), нет зависимостей. Можно параллельно.
- **T006 || T007** — формально параллельны (разные секции `<style>`/`<script>` App.vue), но один файл — лучше последовательно, чтобы избежать merge-конфликтов при правках.
- **T008** — параллельно с T009/T010/T011 (другой файл).

---

## Parallel Example: User Story 1 (MVP)

```bash
# Запустить параллельно (3 разных файла, нет зависимостей):
Task: "T002 Добавить Author.countWithNewAlbum в karaoke-app/.../model/Author.kt"
Task: "T003 Добавить endpoint в karaoke-app/.../controllers/ApiController.kt (depends on T002)"
Task: "T004 Добавить Vuex store в webvue3/src/components/Authors/store.js"

# Затем последовательно:
Task: "T005 Заменить <router-link> в webvue3/src/App.vue (depends on T004)"
```

**MVP scope**: T001..T005 = US1 только. После T005 бейдж уже работает (хотя polling ещё не запущен — это US3). Достаточно для демонстрации фичи.

---

## Implementation Strategy

### MVP First (US1 + US3)

Минимально жизнеспособная фича = US1 + US3 (T001..T005, T007, T011). Бейдж появляется с правильным числом и автоматически обновляется. US2 (CSS-консистентность) — косметика, можно отложить.

### Incremental Delivery

1. **MVP (US1 + US3)** — бейдж работает, но без стилей (или с минимальным inline-стилем) → демо
2. **+ US2** — добавить CSS-консистентность → финальный вид
3. **+ Polish** — линт, документация, PR, merge

### Parallel Team Strategy

С одним разработчиком: строго последовательно (T001 → T002..T005 → T006 → T007 → T008..T011), т.к. 4 файла и одна feature-ветка.

---

## Notes

- [P] задачи = разные файлы, нет зависимостей.
- [Story] лейбл мапит задачу в user story для traceability (US1=badge, US2=styles, US3=polling).
- Каждая user story независимо завершаема и тестируема.
- Tests skipped: в спеке не запрошены + CI для бэка отсутствует (см. AGENTS.md). Валидация ручная по quickstart.md.
- Commit после каждой задачи или логической группы (например, после T002+T003 один коммит "backend", после T004+T005+T006+T007 — второй коммит "frontend").
- Остановиться на любом чекпоинте для валидации story независимо.
- Избегать: расплывчатых задач, конфликтов в одном файле (T005/T006/T007 — в App.vue, последовательно), cross-story зависимостей.
- Post-implementation: обязательно `pre-commit run --all-files` и ручная проверка по `quickstart.md` перед PR.
