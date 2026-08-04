---
description: "Task list for removing the 'Ссылки на просмотр' block from the public song page"
---

# Tasks: 142-remove-watch-links-block — Удалить блок «Ссылки на просмотр» со страницы песни

**Input**: Design documents in `/home/nsa/Karaoke/specs/142-remove-watch-links-block/`
**Prerequisites**: `plan.md` (required), `spec.md` (required), `research.md`, `data-model.md`, `contracts/README.md`, `quickstart.md`.

**Tests**: Автоматизированных тестов в проекте нет (см. AGENTS.md → «Тесты»:
«В CI нет. … Не полагайся на них как на проверку»). Проверки — ручные по
`quickstart.md` (`npm run lint:check`, `npm run build`, dev-сервер `npm run dev`,
визуальный осмотр + `curl`/`grep` на проде).

**Organization**: задачи сгруппированы по двум user stories из `spec.md`:

- **US1 (P1)** — убрать DOM-блок `.km-links-card` (пользовательская цель).
- **US2 (P2)** — code hygiene: убрать мёртвый CSS + неиспользуемый импорт
  `PlatformLink` из того же файла.

Все правки — в **одном файле** `karaoke-public/src/views/SongView.vue`, поэтому
US1 и US2 в этой фиче выполняются последовательно в рамках одного PR.

## Path Conventions

Мульти-модульный Gradle-репозиторий (см. AGENTS.md → «Модули»):

- **Изменяется**: `karaoke-public/src/views/SongView.vue` (только этот файл).
- **НЕ затрагиваются**: `karaoke-app/`, `karaoke-web/`, `webvue3/`,
  `karaoke-public/src/components/PlatformLink.vue`,
  `karaoke-public/src/views/SearchView.vue`,
  `karaoke-public/src/views/ZakromaView.vue`,
  БД (PostgreSQL), `SyncRegistry`, `recordhash`-триггеры.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Подготовка feature-ветки и убедиться, что рабочее дерево чистое.

- [x] T001 Зарезервировать номер ветки через `./tools/reserve-branch-number.sh` (если номер ещё не зарезервирован; для фичи 142 — тег `seq/142` уже в origin) и перейти на ветку `git checkout -b 142-remove-watch-links-block` от свежего `master` (`git fetch origin && git checkout -b 142-remove-watch-links-block origin/master`)
- [x] T002 Убедиться, что `git status --short` возвращает пусто и текущая ветка — `142-remove-watch-links-block` (`git branch --show-current`); если есть незакоммиченные изменения — либо закоммитить, либо спрятать в stash, чтобы стартовать от чистого состояния

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Pre-check, без которого нельзя начинать удаление (см. `plan.md → Open Questions / Carry-over` и `quickstart.md → Сценарий 2.2`).

**⚠️ CRITICAL**: никакая правка `SongView.vue` не должна начаться, пока T003 не подтвердит, что `km-link-*` классы используются только внутри удаляемого блока. Иначе удаление CSS «сломает» другие элементы страницы.

- [x] T003 Выполнить `grep -n "km-link" /home/nsa/Karaoke/karaoke-public/src/views/SongView.vue` и убедиться, что **все** вхождения находятся внутри блока `v-if="currentSong.onAir"` (текущие строки ~201–333). Если найдётся вхождение вне блока — НЕ удалять CSS, а обновить план: либо оставить часть CSS, либо расширить scope правки. Фиксировать результат в комментарии к commit'у.

**Checkpoint**: T003 passed — можно приступать к удалению DOM-блока.

---

## Phase 3: User Story 1 — Убрать блок «Ссылки на просмотр» с публичной страницы песни (Priority: P1) 🎯 MVP

**Goal**: DOM-блок `.km-links-card` (заголовок «Ссылки на просмотр» + сетка из
~15 `<PlatformLink>` в 5 группах: Все / Karaoke / Lyrics / TABS / Chords) удалён
из `karaoke-public/src/views/SongView.vue`. Эфирные песни (`onAir=true`) больше
не показывают этот блок, остальная страница работает без изменений.

**Independent Test** (см. `quickstart.md → Сценарии 1, 3, 4, 6`):

1. Локально: `npm run dev` → открыть `http://localhost:5173/song?id=<id_эфирной>`
   → в DevTools `<div class="km-links-card">` отсутствует, визуально под плеером
   пусто (никаких заглушек).
2. На проде после деплоя:
   `curl -s "https://sm-karaoke.ru/song?id=<id_эфирной>" | grep km-links-card`
   возвращает пусто.
3. На дизайне `classic` и `modern` (переключение через `localStorage` `km.design`)
   поведение одинаковое.
4. На `onAir=false` поведение страницы без изменений (блок и так не показывался).

### Implementation for User Story 1

- [x] T004 [US1] Удалить DOM-блок `<div v-if="currentSong.onAir" class="km-links-card">…</div>` целиком в `karaoke-public/src/views/SongView.vue` (текущие строки 201–333), включая вложенные `<div class="km-links-title">`, `<div class="km-links-grid">`, 5 групп `<div class="km-link-group">` и все `<PlatformLink>` внутри. Не оставлять пустых обёрток и placeholder-надписей (NFR-001). Не трогать соседние блоки (`<div class="km-player-card">`, `<div class="km-waiting-card">`, идущие до/после).

**Checkpoint**: User Story 1 — пользовательская цель достигнута (блок отсутствует).

---

## Phase 4: User Story 2 — Санитарная чистка `SongView.vue` (Priority: P2)

**Goal**: После удаления блока (US1) — убрать неиспользуемые CSS-правила и
неиспользуемый импорт `PlatformLink` в этом же файле, чтобы не оставлять
мёртвый код (соответствует NFR-001 и FR-006).

**Independent Test**:

1. `grep -n "km-link" karaoke-public/src/views/SongView.vue` возвращает пусто.
2. `grep -n "PlatformLink" karaoke-public/src/views/SongView.vue` возвращает пусто.
3. `cd karaoke-public && npm run lint:check` проходит без новых нарушений
   (baseline может только СОКРАТИТЬСЯ).
4. `cd karaoke-public && npm run build` завершается с кодом 0.
5. `grep -rn "PlatformLink" karaoke-public/src/views/` показывает упоминания
   только в `SearchView.vue` и `ZakromaView.vue` (НЕ в `SongView.vue`) —
   соседние view не сломались (SC-005).

### Implementation for User Story 2

- [x] T005 [US2] Удалить CSS-правила `.km-links-card`, `.km-links-title`, `.km-links-grid`, `.km-link-group`, `.km-link-label`, `.km-link-icons` и адаптивное `.km-links-grid { gap: 0.5rem; }` из `<style scoped>` в `karaoke-public/src/views/SongView.vue` (текущие строки 927–961 и 1177). Перед удалением повторно убедиться `grep`, что других использований этих классов в файле нет (T003 уже прошёл, но между T004 и T005 ничего больше не менялось — связь сохраняется).
- [x] T006 [US2] Удалить `import PlatformLink from '../components/PlatformLink.vue'` и запись `PlatformLink` в `components: { … }` (или аналогичной регистрации в `<script setup>` / Options API) в `karaoke-public/src/views/SongView.vue` (текущие строки 453 + 491). Перед удалением убедиться `grep -n "PlatformLink" karaoke-public/src/views/SongView.vue`, что других использований `PlatformLink` в файле нет.

**Checkpoint**: User Story 2 — мёртвый код из файла вычищен.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Финальные проверки локально, коммит, PR через CI-gate (7/7),
деплой (пользователем), пост-деплойная проверка на проде.

- [x] T007 В `karaoke-public/` выполнить `npm run lint:check && npm run build` — обе команды должны завершиться с кодом 0 без новых ошибок и предупреждений. Если `npm run lint:check` сообщает о превышении baseline — перегенерировать baseline через `bash tools/generate-eslint-baseline.sh karaoke-public` (см. AGENTS.md → «Pre-commit checks»); baseline НЕ должен расти относительно текущего.
- [x] T008 Прогнать финальные grep-проверки success-criteria из `spec.md`: `grep -rn 'km-links-card|km-links-title|km-links-grid|km-links|km-link-' karaoke-public/src/` (пусто), `grep -n "km-link" karaoke-public/src/views/SongView.vue` (пусто), `grep -n "PlatformLink" karaoke-public/src/views/SongView.vue` (пусто), `grep -rn "PlatformLink" karaoke-public/src/views/` (`SearchView.vue` + `ZakromaView.vue` без `SongView.vue`).
- [ ] T009 Зафиксировать T004+T005+T006 одним коммитом на ветке `142-remove-watch-links-block`. Перед `git add` — `git status --short && git diff --stat` (AGENTS.md → «Git»: «обязательно проверить»). Сообщение на русском в стиле `area: краткое описание`, например `public/song-view: убрать блок "Ссылки на просмотр" со страницы песни`. Body коммита: краткое описание + ссылка на `specs/142-remove-watch-links-block/spec.md`.
- [ ] T010 Выполнить `git push -u origin 142-remove-watch-links-block`, затем `gh pr create --base master --title "public/song-view: убрать блок \"Ссылки на просмотр\" со страницы песни" --body "Closes #N (или без номера). См. спеку specs/142-remove-watch-links-block/spec.md."`. Тело PR сослаться на спек/план/quickstart.
- [ ] T011 Дождаться, пока `gh pr checks` покажет 7/7 SUCCESS (ktlint, ESLint webvue3, ESLint karaoke-public, Docs, Baseline, KDoc, JSDoc; см. `.github/workflows/lint.yml`). Если какая-то проверка упала — починить, force-push, НЕ пытаться смёрджить красный PR (AGENTS.md → «CI-gate для master»: «**Прямые пуши в `master` ЗАПРЕЩЕНЫ без прогона CI 7/7 SUCCESS**»). Самопроверка перед мержем: `git log -1 master` НЕ должен показывать одиночный коммит в обход PR.
- [ ] T012 После зелёного CI выполнить `gh pr merge --merge --delete-branch`. Удалённая ветка — норма (скрипт `reserve-branch-number.sh` уже защитил номер 142 в `seq/142`-теге).
- [ ] T013 После мержа — **попросить пользователя** выполнить `cd /home/nsa/Karaoke/deploy && bash do.sh build_start_public` (per AGENTS.md → «Деплой» и Constitution §V ст. 2 «Категорически запрещено агенту»: деплой на сервер — только по прямому согласию пользователя). В логах `do.sh` не должно быть `EOF` / `400 Bad request`; на сервере — `Status: Downloaded newer image` (не `Image is up to date`).
- [ ] T014 После деплоя — проверить прод (см. `quickstart.md → Сценарий 6`): `curl -s "https://sm-karaoke.ru/song?id=<id_эфирной>" | grep -E "km-links-card|km-links-title|km-link-group"` возвращает пусто; в браузере визуально блок отсутствует; очистить кеш через `Ctrl+Shift+R` если нужно. Если блок всё ещё виден — проверить CDN/Cloudflare/кеш nginx, при необходимости hard-reload.
- [ ] T015 Добавить запись в `docs/architecture-notes.md` о выполненном PR (Pass 14+ исторически фиксирует каждое изменение: дата, номер PR, цель, ссылки на спеку/план; пример формата — см. прошлые PR в том же файле).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)** → нет зависимостей; можно начать сразу.
- **Foundational (Phase 2)** → зависит от Phase 1; **БЛОКИРУЕТ все user stories**
  (без T003 нельзя трогать CSS — рискуем удалить нужное).
- **User Stories (Phase 3 — US1, Phase 4 — US2)** → зависят от Phase 2.
  - US1 (P1, T004) и US2 (P2, T005+T006) правят **один файл**, поэтому в этой
    фиче они выполняются строго последовательно в одном коммите (T009).
  - **Параллелить их нельзя** — конфликт по одному файлу.
- **Polish (Phase 5)** → зависит от US1+US2; затем PR+CI+merge+deploy.

### User Story Dependencies

- **US1 (P1)**: можно начать после Phase 2 (T003 green).
- **US2 (P2)**: формально независима от US1, но логически идёт сразу после
  US1 (правим один файл, US2 чистит мёртвый код, оставшийся после US1).

### Within Each User Story

- **US1**: проверки ручные (`npm run dev`, визуальный осмотр + DevTools).
  Одна задача (T004) — это и есть «реализация». Альтернатив «до/после» нет.
- **US2**: T005 и T006 — разные секции одного файла (`<style>` vs `<script>`).
  На практике лучше делать последовательно, чтобы один коммит был атомарным;
  поэтому T005/T006 **НЕ помечены [P]**.

### Parallel Opportunities

- На этой фиче **параллелизм ограничен** одним файлом и 3 правками в нём.
- T007 (`npm run lint:check && build`) и T008 (grep-проверки) — независимые
  shell-команды, но практически выполняются за секунды; можно выполнить
  одной командой через `&&`, но оставляем как два шага для прозрачности.
- T011 (`gh pr checks` waiting) и T010 (push + PR create) — последовательны.
- Существенного параллелизма здесь нет — задача однопоточная.

---

## Parallel Example: эта фича

Сценарий «выполнить всё сразу» (без реального параллелизма):

```bash
# 1. Setup
git fetch origin && git checkout -b 142-remove-watch-links-block origin/master

# 2. Foundational pre-check
grep -n "km-link" karaoke-public/src/views/SongView.vue

# 3. Apply edits in SongView.vue (manually, по Сценарию 2 из quickstart.md):
#    - удалить DOM-блок v-if="currentSong.onAir" class="km-links-card" (T004)
#    - удалить CSS .km-links-* + .km-link-* (T005)
#    - удалить import + регистрацию PlatformLink (T006)

# 4. Local checks
cd karaoke-public && npm run lint:check && npm run build && cd ..

# 5. SC greps
grep -rn 'km-links-card|km-links-title|km-links-grid|km-links|km-link-' karaoke-public/src/
grep -rn "PlatformLink" karaoke-public/src/views/

# 6. Commit + push + PR
git add karaoke-public/src/views/SongView.vue
git status --short && git diff --stat
git commit -m "public/song-view: убрать блок \"Ссылки на просмотр\""
git push -u origin 142-remove-watch-links-block
gh pr create --base master --title "public/song-view: убрать блок \"Ссылки на просмотр\" со страницы песни" --body "Closes ... См. specs/142-remove-watch-links-block/spec.md, plan.md, quickstart.md."

# 7. Wait for CI
gh pr checks --watch

# 8. Merge after green
gh pr merge --merge --delete-branch

# 9. Ask user to deploy
echo "Run: cd deploy && bash do.sh build_start_public"

# 10. Post-deploy check
curl -s "https://sm-karaoke.ru/song?id=<id_эфирной>" | grep -E "km-links-card|km-links-title|km-link-group"

# 11. Update architecture notes
echo "[2026-08-04] PR #NNN: 142-remove-watch-links-block — убран блок «Ссылки на просмотр» со страницы песни" >> docs/architecture-notes.md
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

Технически весь скоуп — это две последовательные правки одного файла.
**MVP = US1** (T004): блок убран с прод-страницы. **US2 (T005+T006)** — это
code-hygiene, делается заодно в том же коммите (по сути одна атомарная
правка «убрать блок целиком с мёртвым кодом»). PR содержит US1+US2.

### Incremental Delivery

Из-за малого объёма (1 файл, 3 edit-операции, 1 PR, 1 деплой) — incremental
доставка не применяется. Нет смысла выкатывать T004 отдельно от T005+T006 —
всё в одном коммите, всё в одном деплое.

### Parallel Team Strategy

**Не применимо.** Задача однопоточная, объёмом < 30 минут работы для одного
разработчика. Распараллеливать нечего.

---

## Notes

- [P] tasks = разные файлы, нет зависимостей. На этой фиче **нет [P]-задач** —
  все правки в одном файле `SongView.vue`. T005/T006 теоретически в разных
  секциях (`<style>` vs `<script>`), но безопаснее редактировать
  последовательно и атомарным коммитом (T009).
- [Story] label maps task → user story для traceability.
- Автоматизированных тестов нет — все проверки ручные по `quickstart.md`.
- Коммит после T008 (когда T004+T005+T006 применены и локальные проверки
  зелёные); см. также AGENTS.md → «CI-gate для master» — порядок строго:
  feature-branch → push → PR → CI 7/7 → merge. Прямой push в `master`
  ЗАПРЕЩЁН.
- После T011 — если CI падает (хотя бы 1/7 красный), чинить и force-push;
  НЕ пытаться смёрджить красный PR.
- T013 — деплой выполняет пользователь, не агент (Constitution §V ст. 2).
  Агент на этом шаге только сообщает пользователю команду.
- T014 выполняется после того, как пользователь подтвердит завершение T013.
- Stop at any checkpoint to validate story independently (US1 можно
  валидировать сразу после T004, до T005/T006 — но в этой фиче
  валидация откладывается до T007, чтобы не гонять сборку дважды).
- Avoid: vague tasks, same-file conflicts (поэтому T004/T005/T006
  не-[P] и в одном коммите), cross-story dependencies (US2 не зависит
  от «feature» US1, но логически ей следует).

---

## Self-Check (контрольная сверка исполнителем в конце работы)

Прогон по списку ниже НЕ отдельный шаг, а контрольная сверка всех задач
после их последовательного выполнения. Каждый пункт соответствует task ID.

- **Setup (T001–T002)**: ветка `142-remove-watch-links-block` создана, working tree чист.
- **Foundational (T003)**: pre-check `grep "km-link"` — все вхождения внутри удаляемого блока.
- **US1 (T004)**: DOM-блок `.km-links-card` удалён из `SongView.vue`.
- **US2 (T005, T006)**: CSS `.km-links-*`/`.km-link-*` удалён, import + регистрация `PlatformLink` удалены.
- **Локальные проверки (T007, T008)**: `npm run lint:check && build` зелёные; 5 grep-SC прошли.
- **Git/CI (T009–T012)**: один коммит → `gh pr create` → CI 7/7 SUCCESS → `gh pr merge --merge --delete-branch`.
- **Деплой (T013, T014)**: пользователь выполнил `do.sh build_start_public` (логи чистые), прод проверен через `curl` и браузер.
- **Changelog (T015)**: запись добавлена в `docs/architecture-notes.md`.
