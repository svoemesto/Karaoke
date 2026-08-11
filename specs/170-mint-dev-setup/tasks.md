# Tasks: Воспроизводимая настройка Linux Mint для проекта Karaoke

**Input**: Design documents from `/specs/170-mint-dev-setup/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, quickstart.md
**Tests**: smoke-test ОБЯЗАТЕЛЕН (FR-008 спеки: проверка 4 эндпоинтов после `docker compose up`).

**Organization**: US1 — основная работа (создание артефактов), US2 — верификация воспроизводимости, US3 — документация для AI-агента.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- Артефакты спеки: `specs/170-mint-dev-setup/` (plan.md, research.md, quickstart.md, setup-mint.sh, checklists/)
- Env-шаблон: `deploy/do.env.template`
- Per-feature документ: `docs/features/docker-deploy.md`
- Связанные правки: `docs/features/README.md`, `docs/onboarding.md`, `docs/architecture-notes.md`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Подготовить структуру спеки, убедиться, что design-документы на месте, проинициализировать git-ветку.

- [X] T001 Verify `specs/170-mint-dev-setup/{plan.md,research.md,quickstart.md,spec.md,checklists/requirements.md}` exist (all should be present from `/speckit.specify` and `/speckit.plan`)
- [X] T002 Verify current branch is `170-mint-dev-setup` via `git branch --show-current` (should be already checked out)
- [X] T003 Verify `.gitignore` contains `/deploy/.env` and `/deploy/do.env` patterns (Principle VIII.1) — `grep -E '(/deploy/\.env|/deploy/do\.env)' .gitignore`
- [X] T004 [P] Verify `git credential.helper=store` is set globally so `git push` works without re-prompting (already configured in this session; document for the user in commit body if needed)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Создать **артефакты-фундамент**, без которых US1 не может быть выполнена: `do.env.template` (env-шаблон) и `setup-mint.sh` (главный скрипт). Также — per-feature документ, обязательный по Constitution (FR-009).

**⚠️ CRITICAL**: US1, US2, US3 не могут стартовать, пока эти артефакты не лежат в репо.

- [X] T005 [P] Create `deploy/do.env.template` with all required env variables from `research.md R-004` (secrets as `<SET-ME>` or empty strings, paths as `<ABSOLUTE-PATH>`, ports as real defaults)
- [X] T006 [P] Create `specs/170-mint-dev-setup/setup-mint.sh` (idempotent bash, ~250 lines, sections per `plan.md` § Summary and `research.md` R-005/R-006/R-007/R-008) — script MUST: check OS = Linux Mint 22.2 Zara (fail-fast, R-003), check `sudo` availability, install apt packages idempotently via `dpkg-query`, install Node 22 via NodeSource (R-001), install Docker CE from `download.docker.com` (R-002), add user to `docker` group, configure `git blame.ignoreRevsFile`, create bind-mount folders with `sudo mkdir -p`, run `bash deploy/do.sh pull` then `start all` (R-006), start MinIO via `docker compose -f deploy/docker-compose-storage.yml up -d`, run smoke-test with 5-retry loop (R-007)
- [X] T007 [P] Create `docs/features/docker-deploy.md` per-feature document with sections: «Что делает», «Зачем», «Как работает», «Инварианты/правила», «Известные ловушки», «Ссылки» — covering 8+ containers, bind-mounts, `karaokenet` network, `do.env` secret hygiene, `ENABLE_APP_GPU=0` on dev, `nginx:stable` not `alpine` (4-5 ловушек из AGENTS.md → «Dockerfile-ловушки»)
- [X] T008 Make `setup-mint.sh` executable: `chmod +x specs/170-mint-dev-setup/setup-mint.sh`
- [X] T009 Verify `setup-mint.sh` syntax: `bash -n specs/170-mint-dev-setup/setup-mint.sh` (MUST exit 0)
- [X] T010 Verify `set -a; source deploy/do.env.template; set +a; env` works without exporting `#`-comments (per `research.md R-010`) — bash 5.2 пропускает `#`-комментарии корректно
- [X] T011 Verify `git ls-files | grep -iE '(do\.env|\.env)$'` returns empty (Principle VIII.1) — `.template` files НЕ подпадают
- [X] T012 Run `bash tools/check-feature-doc.sh docs/features/docker-deploy.md` — MUST exit 0 (per Q&A «Как добавить per-feature документ»)

**Checkpoint**: Foundation ready — `do.env.template`, `setup-mint.sh`, `docker-deploy.md` лежат в репо и прошли базовые проверки. US1 можно стартовать.

---

## Phase 3: User Story 1 — Разработчик с нуля поднимает рабочую среду (Priority: P1) 🎯 MVP

**Goal**: Чистый Linux Mint 22.2 → запустил `setup-mint.sh` → 8+ контейнеров работают, smoke-test зелёный.

**Independent Test**: На чистой VM с Linux Mint 22.2 (или этой машине после `docker compose down -v` и `rm -rf /sm-karaoke/system/Караоке-*`) выполнить `bash setup-mint.sh` и проверить: (1) `docker ps` показывает 8 контейнеров в статусе `Up`; (2) `curl http://localhost:7906` возвращает 200; (3) `curl http://localhost:8888` возвращает 200; (4) `curl http://localhost:9001/minio/health/live` возвращает 200; (5) `psql -h localhost -U postgres -d karaoke -c 'SELECT 1'` возвращает 1.

### Smoke-test for User Story 1 (ОБЯЗАТЕЛЕН по FR-008)

> **NOTE**: smoke-test пишется в `setup-mint.sh` как финальный шаг; запускается пользователем, не агентом.

- [X] T013 [P] [US1] Add `check_container_up` helper function to `setup-mint.sh` (10 lines, retry-loop: 5 attempts × 3 sec, print container name + status on failure)
- [X] T014 [P] [US1] Add `check_http_200` helper function to `setup-mint.sh` (15 lines, retry-loop for `curl -s -o /dev/null -w '%{http_code}'`, print URL + code on failure)
- [X] T015 [P] [US1] Add `check_postgres_ready` helper function to `setup-mint.sh` (15 lines, retry-loop for `psql -h localhost -U postgres -d karaoke -c 'SELECT 1'`, source password from `do.env`)
- [X] T016 [US1] Wire 8+ container-up checks into smoke-test section of `setup-mint.sh` (8 calls: `karaoke-app`, `karaoke-web`, `karaoke-webvue3`, `karaoke-public`, `searxng`, `fourget`, `karaoke-db`, `karaoke-storage`)
- [X] T017 [US1] Wire 4 HTTP-200 checks into smoke-test: webvue3 (7906), karaoke-public (8888), MinIO health (9001), MinIO S3 API (9000)
- [X] T018 [US1] Wire Postgres-ready check (calls `check_postgres_ready`, exits 0 on success, non-zero with diagnostic on failure)
- [X] T019 [US1] Add rollback section to `setup-mint.sh` (function `print_rollback_instructions`, prints 5-step instructions: `do.sh stop all`, `docker compose ... down`, optional `rm -rf` of DB_FOLDER/STORAGE_FOLDER, optional `docker rmi`)

### Implementation for User Story 1 (документация и финальные правки)

- [X] T020 [P] [US1] Update `docs/features/README.md` — add `docker-deploy.md` to the table of 9 features (10th row, columns: name = "Docker deploy", path = "docs/features/docker-deploy.md", description = "9 контейнеров локально + MinIO + do.env hygiene")
- [X] T021 [P] [US1] Update `docs/onboarding.md` — add a new section «### Linux Mint 22.2 (Zara)» after the macOS/Ubuntu/Arch sections, with 1-line summary: «Для Linux Mint 22.2 — см. [`specs/170-mint-dev-setup/`](./specs/170-mint-dev-setup/) (артефакт `setup-mint.sh` + `deploy/do.env.template`)»
- [X] T022 [US1] Update `AGENTS.md` (Q&A секция) — add new Q&A entry: «Q: Настроить Linux Mint 22.2 для Karaoke? A: см. `specs/170-mint-dev-setup/` — идемпотентный `setup-mint.sh` + `do.env.template`» (short, ~5 lines)
- [X] T023 [US1] Run quickstart.md validation on this machine — execute all 5 steps from `quickstart.md`, confirm smoke-test passes (or document what blocks it; if blocked by missing `do.env`, document as expected pre-merge state)
- [X] T024 [US1] Stage and commit all new files in 1 logical commit: `git add deploy/do.env.template specs/170-mint-dev-setup/setup-mint.sh docs/features/docker-deploy.md docs/features/README.md docs/onboarding.md AGENTS.md` (только эти файлы, **не** `deploy/do.env` — его нет, и **не** `do.env` — он не в гите)

**Checkpoint**: US1 complete — артефакты созданы, smoke-test зелёный (или задокументировано почему ещё нет), документация обновлена, изменения готовы к PR.

---

## Phase 4: User Story 2 — Воспроизведение спеки на втором компьютере (Priority: P1)

**Goal**: Подтвердить, что скрипт воспроизводим: на **другой** чистой VM с тем же Linux Mint 22.2 он даёт идентичный результат без изменений.

**Independent Test**: Скопировать `setup-mint.sh` + `do.env.template` (из репо) на вторую чистую VM с Linux Mint 22.2 Zara, выполнить, и сравнить `docker ps --format "{{.Names}}\t{{.Status}}"` с первой машиной. Списки контейнеров и статусы должны совпадать (с точностью до `BUILD_VERSION`).

### Tests for User Story 2 (воспроизводимость — это форма теста)

- [ ] T025 [P] [US2] Document reproducibility test procedure in `specs/170-mint-dev-setup/REPRODUCIBILITY.md` (new file, ~30-50 lines): prerequisites (вторая VM с Linux Mint 22.2), steps (copy 2 files, run, capture `docker ps` output), pass criteria (8+ containers, same names, same Up status)
- [ ] T026 [P] [US2] Add explicit fail-fast to `setup-mint.sh` if `/etc/os-release` shows anything other than `Linux Mint 22.2 (Zara)` — print clear error message with link to `REPRODUCIBILITY.md` and `specs/170-mint-dev-setup/spec.md` (R-003)
- [ ] T027 [US2] Document what "reproducibility" means in `docs/onboarding.md` reference to spec — add 1 paragraph explaining that the spec is the **replay formula**, not just a description (FR-002, SC-002)
- [ ] T028 [US2] Cross-link from `docs/features/docker-deploy.md` to `REPRODUCIBILITY.md` (in «Ссылки» section) so a new developer finds both

**Checkpoint**: US2 complete — fail-fast защита на месте, reproducibility procedure задокументирована, перекрёстные ссылки проставлены. **Фактическая проверка на 2-й VM** — это post-merge активность (не блокирует PR; это acceptance test для следующего релиза).

---

## Phase 5: User Story 3 — Документация для AI-агента (Priority: P2)

**Goal**: AI-агент (opencode) на новой Linux Mint-машине понимает контекст: какие контейнеры подняты, какие порты, какие ограничения.

**Independent Test**: Открыть новую opencode-сессию, спросить «какие контейнеры должны быть запущены локально?», получить ответ с 8+ именами + портами. Спросить «могу ли я перезапустить karaoke-app?», получить корректный ответ с учётом Constitution «Ограничения агента» (hostname `nsa-G501VW` ≠ `dev-pc`, user `nsa` ≠ `dev` → запрет).

### Tests for User Story 3 (явные — это вопросы агенту)

- [ ] T029 [P] [US3] Add «AI-агент quick reference» section to `docs/features/docker-deploy.md` (~30-50 lines): list of 8 container names + ports, list of bind-mount paths (placeholders), 3 explicit «разрешено»/«запрещено» матрицы (per Constitution: может читать логи; может `start_web`/`start_public`; **не может** `build_start_app` без согласия)
- [ ] T030 [P] [US3] Add troubleshooting decision tree to `setup-mint.sh` `--help` output: 5-7 типовых ошибок (port conflict, Postgres auth, MinIO XDG_RUNTIME_DIR, do.env missing, java main class) с конкретными командами диагностики
- [ ] T031 [US3] Update `AGENTS.md` to add explicit reference to `specs/170-mint-dev-setup/` in the «Документация и иерархия» table (link: «Setup Linux Mint 22.2 — `specs/170-mint-dev-setup/`», priority = 8.5 between `CONTRIBUTING.md` and `docs/features/`)
- [ ] T032 [US3] Verify AI-agent can answer the 2 test questions (out of scope for `/speckit.tasks` — это manual check на новой сессии; в `tasks.md` фиксируем как out-of-band verification)

**Checkpoint**: US3 complete — AI-агент имеет quick reference и troubleshooting. Ручная проверка в новой opencode-сессии — post-merge.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Финальные проверки, commit hygiene, PR-creation, post-merge bookkeeping.

- [ ] T033 [P] Run `bash tools/check-feature-doc.sh docs/features/*.md` — MUST exit 0 (все per-feature документы прошли структурную проверку)
- [ ] T034 [P] Run `bash tools/check-kdoc-coverage.sh` — MUST report 100% (эта спека не меняет Kotlin, coverage должна остаться 100%)
- [ ] T035 [P] Run `bash tools/check-jsdoc-coverage.sh webvue3` and `bash tools/check-jsdoc-coverage.sh karaoke-public` — MUST report 100%
- [ ] T036 Run pre-commit: `pre-commit run --all-files` — MUST be green (или baseline-acceptable); если падает — фиксить до PR
- [ ] T037 [P] Run `git status` — verify only intended files are staged (`deploy/do.env.template`, `specs/170-mint-dev-setup/setup-mint.sh`, `docs/features/docker-deploy.md`, `docs/features/README.md`, `docs/onboarding.md`, `AGENTS.md`); **НЕ должно быть** `deploy/do.env` (его нет), `dist/`, `node_modules/`, `deploy/ollama_data/`
- [ ] T038 [P] Run `git ls-files | grep -iE '(do\.env|\.env)$'` — MUST return empty (Principle VIII.1)
- [ ] T039 Create commit with message style `infra(mint): reproducible Linux Mint 22.2 dev setup via setup-mint.sh + do.env.template` (Russian per `constitution.md` § Рабочий процесс, area-prefix style; примерно 1-2 строки description)
- [ ] T040 Push branch: `git push -u origin 170-mint-dev-setup` (uses saved credentials from `~/.git-credentials`)
- [ ] T041 Create PR: `gh pr create --base master --title "infra(mint): reproducible Linux Mint 22.2 dev setup" --body "..."` (в body — ссылка на `specs/170-mint-dev-setup/spec.md` + 1-2 строки summary)
- [ ] T042 Wait for CI 7/7 PASS via `gh pr checks` или `gh run watch` (per AGENTS.md → «CI-gate для master»)
- [ ] T043 Merge PR: `gh pr merge 170 --merge` (**БЕЗ** `--delete-branch` per AGENTS.md → «Жизненный цикл feature-ветки»)
- [ ] T044 [P] After merge, add entry to `docs/architecture-notes.md` (new section «PR #170 — Воспроизводимая настройка Linux Mint») — date, branch, summary, 3-5 lines of context
- [ ] T045 [P] Update `AGENTS.md` version: 1.6.1 → 1.7.0 (MINOR: новая секция в Q&A, новые ссылки) — bump в шапке файла, `Last updated: 2026-08-11`, добавить в «Как обновлять этот файл» changelog

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately.
- **Foundational (Phase 2)**: Depends on Phase 1 completion — **BLOCKS** US1/US2/US3.
- **User Stories (Phase 3-5)**: All depend on Phase 2 completion. US1 — основная работа; US2 и US3 — это доп. документация и fail-fast, могут идти параллельно с US1 **после** Phase 2.
- **Polish (Phase 6)**: Depends on US1/US2/US3 completion.

### User Story Dependencies

- **US1 (P1)**: Blocks US2 и US3 (без артефактов `do.env.template` и `setup-mint.sh` нет смысла писать US2/US3 документацию).
- **US2 (P1)**: Зависит от US1.phase-2 (fail-fast в скрипте = часть US1, документация REPRODUCIBILITY = часть US2).
- **US3 (P2)**: Зависит от US1.phase-2 (AI-quick-reference = часть US3, но troubleshooting в `setup-mint.sh --help` = часть US1).

**Рекомендуемый порядок**: Phase 1 → Phase 2 (все 3 файла в параллель: T005, T006, T007) → Phase 3 (US1, последовательно) → Phase 4 (US2 параллельно) → Phase 5 (US3 параллельно) → Phase 6.

### Within Each User Story

- Smoke-test helpers (T013/T014/T015) пишутся **первыми** в US1, **до** их использования в T016-T018.
- Container-up checks (T016) — **до** HTTP-200 checks (T017) — **до** Postgres check (T018) — потому что если контейнеры не поднялись, HTTP-чек бесполезен.
- Документация (T020-T022) идёт **после** всех артефактов и smoke-test (T013-T019), чтобы ссылки были валидны.

### Parallel Opportunities

- **Phase 2 (massive parallel)**: T005 (`do.env.template`), T006 (`setup-mint.sh`), T007 (`docker-deploy.md`) — **3 разных файла, 0 зависимостей**, можно делать параллельно.
- **Phase 2 (parallel checks)**: T008 (`chmod`), T009 (`bash -n`), T010 (`set -a` test), T011 (`git ls-files`), T012 (`check-feature-doc.sh`) — все 5 могут идти параллельно после T005/T006/T007.
- **Phase 3 (US1 smoke-test)**: T013, T014, T015 — 3 helper-функции в одном файле, но **логически независимы** (можно писать в любом порядке; parallel на уровне copy-paste).
- **Phase 3 (US1 docs)**: T020 (`features/README.md`), T021 (`onboarding.md`), T022 (`AGENTS.md`) — 3 разных файла, можно параллельно.
- **Phase 4 (US2)**: T025 (`REPRODUCIBILITY.md`), T026 (`fail-fast` в setup-mint.sh) — T026 — это правка `setup-mint.sh`, **зависит от T006**; T025 — независим.
- **Phase 5 (US3)**: T029 (правка `docker-deploy.md`), T030 (правка `setup-mint.sh --help`) — оба зависят от T007/T006; T031 (правка `AGENTS.md`) — независим.
- **Phase 6**: T033-T035 (3 разных lint-чека), T037/T038 (2 read-only git-чека), T040/T041 (push + PR — последовательно), T044/T045 (post-merge) — параллельно.

---

## Parallel Example: Phase 2 (Foundational)

```bash
# Launch 3 artifact-creation tasks in parallel:
Task: "Create deploy/do.env.template" (T005)
Task: "Create specs/170-mint-dev-setup/setup-mint.sh" (T006)
Task: "Create docs/features/docker-deploy.md" (T007)

# After all 3 complete, launch 5 verification tasks in parallel:
Task: "chmod +x setup-mint.sh" (T008)
Task: "bash -n setup-mint.sh" (T009)
Task: "set -a; source do.env.template; set +a test" (T010)
Task: "git ls-files | grep do.env check" (T011)
Task: "check-feature-doc.sh docker-deploy.md" (T012)
```

---

## Parallel Example: Phase 3 (US1)

```bash
# Smoke-test helpers (3 parallel):
Task: "check_container_up helper" (T013)
Task: "check_http_200 helper" (T014)
Task: "check_postgres_ready helper" (T015)

# Then sequential wiring (depends on helpers):
Task: "Wire 8 container-up checks" (T016)
Task: "Wire 4 HTTP-200 checks" (T017)
Task: "Wire Postgres check" (T018)
Task: "Add rollback section" (T019)

# Then 3 documentation updates in parallel:
Task: "Update features/README.md" (T020)
Task: "Update onboarding.md" (T021)
Task: "Update AGENTS.md" (T022)
```

---

## Implementation Strategy

### MVP First (US1 only)

1. Complete Phase 1 (Setup verification).
2. Complete Phase 2 (Foundational: 3 артефакта + 5 проверок).
3. Complete Phase 3 (US1: smoke-test + docs).
4. **STOP and VALIDATE**: запустить `setup-mint.sh` на чистой VM, smoke-test зелёный.
5. **MVP готов** — даже без US2/US3 спека уже даёт воспроизводимый setup (fail-fast в setup-mint.sh покрывает US2.AC3; AI-quick-reference можно добавить в US3 позже).

### Incremental Delivery

1. Phase 1 + Phase 2 → **MVP**: артефакты на месте, базовый setup работает.
2. Phase 3 (US1) → **первый PR-ready state**: smoke-test, docs, commit готов.
3. Phase 4 (US2) → **reproducibility hardened**: REPRODUCIBILITY.md + явный fail-fast.
4. Phase 5 (US3) → **AI-friendly**: troubleshooting + quick reference.
5. Phase 6 (Polish) → **CI-gate passed, merged**, post-merge bookkeeping.

**Если ресурс ограничен** (один агент, 1 заход): MVP = Phase 1-3, дальше можно постепенно в follow-up PR из той же ветки `170-mint-dev-setup` (per AGENTS.md → «Жизненный цикл feature-ветки» — ветка **не** удаляется после мержа, можно докатывать).

### Parallel Team Strategy

С 1-2 разработчиками:
1. Together: Phase 1-2.
2. Split:
   - Dev A: Phase 3 (US1 — основная работа).
   - Dev B: Phase 4 (US2 — документация + fail-fast) **после** того, как Dev A смержит T006 (создание `setup-mint.sh`).
3. Phase 5 (US3) — Dev B.
4. Phase 6 (Polish + PR) — together.

---

## Notes

- [P] tasks = different files, no dependencies. **T006 и T026** (правки `setup-mint.sh`) — **не** параллельны; правки в один файл.
- [Story] label: T005-T012 — Setup/Foundational (no label), T013-T024 — US1, T025-T028 — US2, T029-T032 — US3, T033-T045 — Polish.
- Tests (smoke-test) написаны **до** основной логики поднятия, чтобы их failure был чётким сигналом «скрипт не работает», а не «может, а может нет».
- Каждый commit — логически завершённый шаг: T024 = 1 commit на US1; T039 = 1 commit на финальный набор (Phase 3+).
- Stop at any checkpoint to validate story independently (особенно после Phase 2 — там основная работа сделана).
- Avoid: vague tasks (все имеют file path), same-file conflicts (T006 vs T026 vs T030 — последовательно!), cross-story dependencies that break independence (US2/US3 зависят от US1, но **не друг от друга**).
