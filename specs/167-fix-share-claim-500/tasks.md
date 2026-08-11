---

description: "Task list for hotfix 500 share.notFound on POST /api/public/share/claim"
---

# Tasks: Починить 500 на POST /api/public/share/claim

**Input**: Design documents from `/specs/167-fix-share-claim-500/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md (resolved), data-model.md, contracts/, quickstart.md

**Tests**: NOT requested in spec. Тесты в проекте отсутствуют (`AGENTS.md`: «Тестов в CI нет. Существующие тесты — интеграционные, требуют сеть/браузер/credentials, большинство @Disabled. Не полагайся на них как на проверку»). Verification через ручные сценарии в `quickstart.md` (см. SC-001..SC-006).

**Organization**: Tasks grouped by user story (US1 P1, US2 P1, US3 P2) для независимой реализации и проверки. Foundational phase содержит общие изменения типов данных и операционные prerequisites (миграции на проде).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: US1 = claim works end-to-end, US2 = other endpoints don't mask, US3 = diagnostic /debug
- Setup / Foundational / Polish phases: NO story label
- File paths relative to repo root

## Path Conventions

- `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/` — backend (Kotlin/Spring Boot)
- `deploy/karaoke-db/` — миграции БД
- `docs/features/` — per-feature документация (FR-009)
- `docs/architecture-notes.md` — changelog по PR
- `AGENTS.md` — Q&A для агентов

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Verify working state in feature branch, подтвердить prerequisites.

- [X] T001 [P] Verify working branch `167-fix-share-claim-500` and no changes in master: `git status` (clean working tree на untracked `specs/167-fix-share-claim-500/`), `git log master..167-fix-share-claim-500 --oneline` (пусто — branch не коммитил ничего), `git branch --show-current` (выдаёт `167-fix-share-claim-500`)
- [X] T002 [P] Verify migration files exist with idempotency markers: `ls -la deploy/karaoke-db/38_song_share_links.sql deploy/karaoke-db/39_song_share_recordhash.sql`, проверка наличия `CREATE TABLE IF NOT EXISTS`, `CREATE OR REPLACE FUNCTION`, `DO`-блоков

**Checkpoint**: Branch готов, prerequisites на месте.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Общие изменения типов (новый код ошибки) + операционная документация в миграциях. ДОЛЖНЫ быть готовы до Phase 3.

**⚠️ CRITICAL**: Никакие user story tasks не могут начаться, пока T003 (enum) не завершён — `ShareException.InternalError` ссылается на `ShareErrorCode.INTERNAL`.

- [X] T003 [P] Add enum member `INTERNAL("share.internal")` to `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/util/ShareErrorCode.kt` after `TOKEN_MISSING` (последний существующий), с KDoc-комментарием описывающим назначение (FR-013). Использовать ту же сигнатуру `(dbValue: String)` что и существующие члены
- [X] T004 [P] Update header comment in `deploy/karaoke-db/38_song_share_links.sql`: добавить явную строку «ПРИМЕНИТЬ НА PROD-БД ВРУЧНУЮ ДО деплоя karaoke-web» (заменить/дополнить существующую «в один деплой с новым karaoke-web/karaoke-app») — FR-004, FR-001
- [X] T005 [P] Update header comment in `deploy/karaoke-db/39_song_share_recordhash.sql`: добавить явную строку «применять ПОСЛЕ 38_song_share_links.sql, ДО деплоя karaoke-web» — FR-004, FR-002

**Checkpoint**: Foundation готов — sealed-подтипы можно создавать, миграции документированы.

---

## Phase 3: User Story 1 — Claim работает end-to-end (Priority: P1) 🎯 MVP

**Goal**: После применения миграций + деплоя кода гость с валидной свежей ссылкой получает HTTP 200 на `POST /api/public/share/claim` с валидным `sessionTokenHash` (64 hex).

**Independent Test**: quickstart.md scenario 4 (claim smoke test) + scenario 5 (PSQLException simulation). Запускается на проде после deploy.

### Implementation for User Story 1

- [X] T006 [US1] Add `class InternalError(cause: Throwable) : ShareException(ShareErrorCode.INTERNAL, 500)` sealed subclass в `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/SongShareLinkService.kt` сразу после `class TokenMissing(...)` (line 193), с KDoc описывающим назначение + `@see docs/features/guest-share-link.md` (FR-010). Использовать `init { addSuppressed(cause) }` для сохранения оригинального стек-трейса
- [X] T007 [US1] Modify catch-all `catch (e: Exception) { log.error(...); throw NotFound() }` в `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/SongShareLinkService.kt:597-602` (метод `tryClaim`) — заменить `throw NotFound()` на `throw InternalError(e)`. Существующий `catch (e: ShareException)` оставить без изменений (FR-011)
- [X] T008 [US1] Modify catch-all в `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicShareController.kt:174-175` (метод `claim`) — заменить `catch (_: Exception) { ResponseEntity.status(500).body(mapOf("errorCode" to "share.notFound")) }` на `catch (_: SongShareLinkService.InternalError) { ResponseEntity.status(500).body(mapOf("errorCode" to "share.internal")) }` (FR-012)
- [X] T009 [US1] **Manual PROD operation**: применить `deploy/karaoke-db/38_song_share_links.sql` на прод-БД через `ssh root@${PROD_HOST:-188.119.64.111} 'docker exec -i karaoke-db psql -U postgres -d karaoke < /root/Karaoke/deploy/karaoke-db/38_song_share_links.sql'` (FR-001). `${PROD_HOST}` — env-var из Constitution VIII.5 (AGENTS.md «Деплой»). Проверить: `docker exec karaoke-db psql -U postgres -d karaoke -c "\\dt tbl_song_share*"` возвращает 2 строки (SC-005). Операция делается пользователем, не агентом (AGENTS.md «Ограничения агента», п. 2)
- [X] T010 [US1] **Manual PROD operation**: применить `deploy/karaoke-db/39_song_share_recordhash.sql` на прод-БД через `ssh root@${PROD_HOST:-188.119.64.111} 'docker exec -i karaoke-db psql -U postgres -d karaoke < /root/Karaoke/deploy/karaoke-db/39_song_share_recordhash.sql'` (FR-002). `${PROD_HOST}` — env-var из Constitution VIII.5. Проверить: `docker exec karaoke-db psql -U postgres -d karaoke -c "\\df update_tbl_song_share*"` возвращает 2 функции + `SELECT tgname FROM pg_trigger WHERE tgname LIKE '%song_share%'` возвращает 4 триггера
- [ ] T011 [US1] **Verify on PROD** (после деплоя нового кода): создать ссылку через UI (admin) → скопировать `secret` → `curl -X POST https://svoemesto.ru/api/public/share/claim -H 'Content-Type: application/json' -d "{\"secret\":\"<secret>\",\"browserHash\":\"deadbeef$(uuidgen | tr -d - | head -c 16)\"}"` (без Cookie) → проверить HTTP 200 + JSON содержит `sessionTokenHash` длиной 64 hex + `redirectTo` вида `/player/{songId}?share=1&session={sessionTokenHash}` (SC-001). Также проверить что в `tbl_song_share_sessions` появилась запись с `finished_at IS NULL, result=''` и в `tbl_song_share_links` инкремент `sessions_total`

**Checkpoint**: User Story 1 полностью функциональна — гость успешно делает claim по валидной ссылке.

---

## Phase 4: User Story 2 — Другие эндпоинты не маскируют системные ошибки (Priority: P1)

**Goal**: `/create` и `/heartbeat` также различают системные ошибки (500 `share.internal`) от доменных (404/409/410/429).

**Independent Test**: quickstart.md scenario 5 (simulate DROP TABLE) — вызвать `POST /create` и `POST /heartbeat` с валидными данными, получить 500 `share.internal` (а не `share.notFound` / `share.leaseExpired`).

### Implementation for User Story 2

- [X] T012 [P] [US2] Modify catch-all в `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicShareController.kt:87-89` (метод `createLink` для POST `/api/public/share/{songId}/create`) — заменить `catch (_: Exception) { ResponseEntity.status(500).body(mapOf("errorCode" to "share.notFound")) }` на `catch (_: SongShareLinkService.InternalError) { ResponseEntity.status(500).body(mapOf("errorCode" to "share.internal")) }` (FR-014)
- [X] T013 [P] [US2] Modify catch-all в `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicShareController.kt:189-191` (метод `heartbeat`) — заменить `catch (_: Exception) { ResponseEntity.status(410).body(mapOf("errorCode" to "share.leaseExpired")) }` на `catch (_: SongShareLinkService.InternalError) { ResponseEntity.status(500).body(mapOf("errorCode" to "share.internal")) }` (FR-014). Документированное поведение `share.leaseExpired` для **доменных** ошибок (lease истёк) сохраняется через существующий `catch (e: ShareException)` — НЕ маскируется
- [X] T013a [US2] Document FR-014 audit conclusion в `specs/167-fix-share-claim-500/quickstart.md` (или `plan.md`): для 4 неисправленных эндпоинтов (`/release` — строки 197-210, `/mine/{songId}` — 92-119, `/mine/{songId}/revoke` — 121-130, `/debug` — 218-225) **отсутствует** `catch (_: Exception)` — audit **PASS**, эти эндпоинты не маскируют системные ошибки, default Spring-handler вернёт 500 без `errorCode` (acceptable для hotfix; backlog spec 164). Это закрывает traceability FR-014 → tasks: T012+T013 фиксят 2 из 5 эндпоинтов, T013a фиксирует «3 из 5 — audit pass, не требуют правок»
- [ ] T014 [US2] **Verify locally** (SC-003): на локальной БД выполнить `DROP TABLE tbl_song_share_links` + попытаться `POST /create` (с премиум-куками) и `POST /heartbeat` (с `sessionTokenHash`) → проверить HTTP 500 + `errorCode: "share.internal"` + в логах `karaoke-web` строка `ShareLink ... UNEXPECTED class=org.postgresql.util.PSQLException msg=ERROR: relation "tbl_song_share_links" does not exist` с полным стек-трейсом. После проверки — `CREATE TABLE` обратно (через повторное применение миграции 38, идемпотентно)

**Checkpoint**: User Stories 1 И 2 обе работают независимо — claim/create/heartbeat различают системные и доменные ошибки.

---

## Phase 5: User Story 3 — Диагностический /debug (Priority: P2)

**Goal**: `POST /api/public/share/debug` возвращает JSON с реальными классами исключений на каждом шаге (не маскирует под `NotFound`).

**Independent Test**: quickstart.md scenario 6 (`/debug` проверка).

### Implementation for User Story 3

- [ ] T015 [P] [US3] **Verify on local** (SC-004, FR-020): создать валидную ссылку + `POST /api/public/share/debug {"secret":"<valid>"}` → проверить HTTP 200 + JSON содержит ВСЕ шаги с префиксом `OK` (`step1_resolve`, `step2_ownerId`, `step3_songId`, `step4_songInfo`), а также `linkId`, `ownerId`, `songId`. Уже реализовано в `SongShareLinkService.kt:639-...` — нужно только подтвердить что после фикса T007 оно показывает реальные классы исключений на упавшем шаге (см. T016)
- [ ] T015a [P] [US3] **Verify on local** (FR-021 regression): `POST /api/public/share/debug {"secret":""}` (пустой secret) → проверить HTTP 400 + JSON `{"errorCode":"share.tokenMissing"}`. Это поведение «сохраняется» по FR-021 — после рефакторинга catch-блоков в T007/T008 легко случайно сломать `TokenMissing`-ветку. Проверяется **до** merge (regression guard)
- [ ] T016 [P] [US3] **Verify on local** (SC-004, FR-020): с эмулированной системной ошибкой (`DROP TABLE tbl_song_share_links`) `POST /api/public/share/debug {"secret":"<valid>"}` → проверить HTTP 200 + JSON содержит `step1_resolve: "FAILED: <класс>: <сообщение>"` с реальным классом исключения (`org.postgresql.util.PSQLException`), а не `FAILED: NotFound`. После проверки — восстановить таблицу (повторное применение миграции 38)
- [X] T017 [US3] Add section «Диагностика 500-ошибок claim» в `docs/features/guest-share-link.md` под «## Инварианты / правила» с 4-шаговой инструкцией: (1) `psql \dt tbl_song_share*` — таблицы должны быть; (2) Если таблиц нет — применить 38 + 39; (3) Если таблицы есть — `POST /api/public/share/debug {secret}` и проверить `step1_resolve`/etc.; (4) В логах искать `ShareLink tryClaim UNEXPECTED` / `ShareException` (FR-030). Сослаться на `specs/167-fix-share-claim-500/quickstart.md` для полного набора сценариев

**Checkpoint**: User Stories 1, 2 и 3 все работают — claim работает, ошибки не маскируются, /debug диагностирует.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Финальная документация, CI-gate, коммит + PR.

- [X] T018 [P] Update `AGENTS.md` Q&A section «500 на /api/public/share/claim»: добавить финальную строку «После применения миграции 500 на этом endpoint'е должны исчезнуть, а оставшиеся 500 — это уже share.internal (новый код), и для диагностики применён POST /api/public/share/debug» (FR-032)
- [X] T019 [P] Add Pass 50+ entry в `docs/architecture-notes.md`: диагностика 500 share.notFound, применение DDL на прод, разделение ошибок `share.internal` vs `share.notFound`, ссылка на spec 167 и PR#N (FR-031). Формат записи — как существующие Pass 1-49
- [X] T020 **CI-gate local check** (перед коммитом, **все 7 проверок** по `.github/workflows/lint.yml` + Constitution FR-007):
  1. `./gradlew ktlintCheck` (ktlint — Kotlin)
  2. `cd webvue3 && npm run lint:check` (ESLint — Vue/TS admin SPA)
  3. `cd karaoke-public && npm run lint:check` (ESLint — Vue/TS public SPA)
  4. `bash tools/check-kdoc-coverage.sh` (KDoc coverage 100%)
  5. `bash tools/check-jsdoc-coverage.sh webvue3` (JSDoc coverage)
  6. `bash tools/check-jsdoc-coverage.sh karaoke-public` (JSDoc coverage)
  7. `pre-commit run --all-files` (все hooks: docs-structure, baseline-stats, и пр. из `.pre-commit-config.yaml`)

  Все должны быть зелёными (baseline = 0 OK). Не блокирует commit, но помечает PR как «CI-failed» если забыть любую проверку — GitHub Actions workflow `lint.yml` запустит те же 7 проверок и любой fail блокирует merge (AGENTS.md «CI-gate для master», NON-NEGOTIABLE)
- [X] T021 **Ask user for explicit permission** для коммита + push + open PR (AGENTS.md: «не коммитить без явного запроса пользователя»). Показать пользователю `git status` + `git diff --stat` перед коммитом
- [X] T022 (после T021 + явного одобрения) `git add` конкретных файлов: `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/SongShareLinkService.kt`, `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicShareController.kt`, `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/util/ShareErrorCode.kt`, `deploy/karaoke-db/38_song_share_links.sql`, `deploy/karaoke-db/39_song_share_recordhash.sql`, `docs/features/guest-share-link.md`, `docs/architecture-notes.md`, `AGENTS.md`. **Один коммит** (по конвенции — atomic per fix)
- [X] T023 (после T022) `git commit -m "fix(share-link): разделение share.internal vs share.notFound (Pass 50)"` — conventional commit, тело коммита содержит ссылку на FR-010..FR-014, FR-030..FR-032 + инструкцию «миграция 38/39 применяется на проде вручную ДО деплоя»
- [X] T024 (после T023) `git push -u origin 167-fix-share-claim-500` + `gh pr create --base master --title "fix(share-link): 500 share.notFound → share.internal (Pass 50)" --body "…"` — body содержит (a) симптом прод-инцидента 2026-08-11, (b) описание фикса, (c) инструкцию по миграциям для деплоера, (d) ссылки на spec/plan/quickstart
- [X] T025 (после T024) Wait for CI 7/7 SUCCESS via `gh pr checks` или `gh run watch`. После зелёного CI: `gh pr merge --merge` (**БЕЗ** `--delete-branch` — AGENTS.md «Жизненный цикл feature-ветки», NON-NEGOTIABLE). Опционально — `gh workflow run lint.yml --ref master` для re-validate merged master
- [ ] T026 **Manual deploy to PROD**: `cd deploy && bash do.sh build_start_web` (rebuild `karaoke-web` + restart на проде). Это делает пользователь (AGENTS.md «Ограничения агента», п. 2)
- [ ] T027 **Post-deploy PROD verify** (после T026): повторить quickstart.md scenarios 4 (claim smoke) + 5 (simulate error) + 7 (logs check) + 8 (regression) на проде. SC-001/SC-003/SC-005/SC-006 — все должны проходить

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion — **BLOCKS all user stories** (T003 must complete before T006)
- **User Story 1 (Phase 3)**: Depends on Foundational completion. T006 must complete before T007-T008 (same file as T007, file dependency on T008). T009-T010 (manual prod) can proceed in parallel with T006-T008 (code). T011 (verify) requires BOTH code (deployed) AND T009-T010 (migration applied)
- **User Story 2 (Phase 4)**: Depends on Phase 2 completion (uses T003's `INTERNAL` enum + T006's `InternalError` sealed class). T012-T013 are code-only, parallel possible. T014 verification depends on T012-T013 (code deployed locally)
- **User Story 3 (Phase 5)**: Depends on Phase 3 completion (T006 InternalError needed for /debug to surface real exceptions on failure step). T015-T016 are verifications (no new code). T017 is docs
- **Polish (Phase 6)**: Depends on all user stories being complete (docs reference fixed code, CI-gate runs after all code changes)

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Phase 2 — depends on T003 (enum) + T006 (sealed class). No dependency on other stories
- **User Story 2 (P1)**: Can start after Phase 2 — same deps as US1. Operates on different controller methods (create + heartbeat), parallel with US1 code changes after Foundational
- **User Story 3 (P2)**: Can start after US1 — /debug verification requires US1's InternalError to be in effect

### Within Each User Story

- Foundational types (enum + sealed class) before tryClaim catch-all (T006 → T007)
- tryClaim catch-all before controller catch-alls that catch `InternalError` (T007 → T008, T012-T013)
- Code changes before manual PROD operations (T006-T008 → T009-T010)
- Code + migration before verification (T006-T008 + T009-T010 → T011)
- All code merged before docs update in Polish

### Parallel Opportunities

- **Within Phase 2**: T003, T004, T005 все `[P]` — разные файлы, нет зависимостей между ними
- **Within Phase 3**: T006 (SongShareLinkService.kt) и T008 (PublicShareController.kt) — разные файлы, можно `[P]`. T007 (тот же файл что T006) — sequential после T006. T009-T010 (операции на проде) — manual, sequential, могут идти параллельно с T006-T008 (код в ветке)
- **Within Phase 4**: T012, T013 оба `[P]` — тот же файл (`PublicShareController.kt`), но РАЗНЫЕ методы (create vs heartbeat), изменения в разных строках. Можно либо sequential, либо объединить в один edit. T014 зависит от T012-T013
- **Within Phase 5**: T015, T016 — оба `[P]` verification scenarios. T017 — docs, можно параллельно с T015-T016
- **Within Phase 6**: T018, T019 — оба `[P]` (AGENTS.md vs architecture-notes.md). T020-T027 — sequential

---

## Parallel Example: User Story 1

```bash
# Phase 2 (Foundational) — все 3 задачи [P]:
Task: "Add enum member INTERNAL in karaoke-web/.../util/ShareErrorCode.kt"
Task: "Update header in deploy/karaoke-db/38_song_share_links.sql"
Task: "Update header in deploy/karaoke-db/39_song_share_recordhash.sql"

# Phase 3 (US1) — T006 и T008 параллельно (разные файлы), T007 sequential после T006:
Task: "Add InternalError sealed class in SongShareLinkService.kt"
Task: "Modify claim catch-all in PublicShareController.kt"

# Phase 3 — T009-T010 manual operations параллельно с T006-T008 code (агент не делает — пользователь):
Task: "Apply 38_song_share_links.sql on PROD"
Task: "Apply 39_song_share_recordhash.sql on PROD"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001-T002)
2. Complete Phase 2: Foundational (T003-T005) — CRITICAL: T003 blocks all code changes
3. Complete Phase 3: User Story 1 (T006-T011) — code + migration + verify
4. **STOP and VALIDATE**: T011 verifies end-to-end claim на проде. Если success — MVP delivered
5. Deploy/demo if ready (T026 manual + T011 verify)

### Incremental Delivery

1. Setup + Foundational → types готовы (T001-T005)
2. Add User Story 1 (T006-T011) → MVP delivered: claim работает, /claim ошибка различается
3. Add User Story 2 (T012-T014) → /create и /heartbeat ошибки различаются (расширение fix)
4. Add User Story 3 (T015-T017) → /debug документирован (инструмент диагностики)
5. Polish (T018-T027) → docs updated, PR merged, deployed on PROD, all SC verified

### Parallel Team Strategy

С одним разработчиком: strictly sequential (т.к. большинство tasks трогает одни и те же 3 файла).

Если два разработчика:
- Dev A: Phase 3 US1 code (T006-T008) — `SongShareLinkService.kt` + `PublicShareController.kt`
- Dev B: Phase 4 US2 code (T012-T013) — `PublicShareController.kt` (другие методы)
- Координация: merge через ветку `167-fix-share-claim-500` (Dev B подтягивает Dev A's commits перед T012)

---

## Notes

- **Operational constraint**: миграции T009-T010 и deploy T026 — **manual only**, делает пользователь (AGENTS.md «Ограничения агента», п. 2). Агент НЕ выполняет эти команды.
- **Per-feature doc update обязателен** (FR-009): `docs/features/guest-share-link.md` обновляется в том же PR (T017).
- **Single commit pattern** (AGENTS.md): все правки в одном коммите (T023). История фичи — линейная.
- **Feature-branch lifecycle**: НЕ удалять ветку после мёрджа (T025: `gh pr merge --merge` без `--delete-branch`). Для follow-up правок работать в той же ветке.
- **CI-gate**: PR триггерит GitHub Actions `lint.yml` с 7 проверками. Все должны быть зелёными до мёрджа (T025).
- **Никаких новых env-переменных** (R8 в research.md). Никаких изменений в `deploy/.env` / `deploy/do.env`.
- **Никаких новых таблиц, индексов, триггеров** в коде — DDL уже в гите.
- **Никаких тестов** — проект без CI-тестов (AGENTS.md). Verification через ручные сценарии quickstart.md.