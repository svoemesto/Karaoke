---
description: "Task list template for feature implementation"
---

# Tasks: 282 — Кортеж заданий при «Добавить файлы из папки» (mp3 голоса/аккомпанимента → локальное + удалённое хранилище)

**Input**: Design documents from `/specs/282-import-folder-mp3-uploads-tuple/`
- [plan.md](plan.md) (required) — Technical Context, Constitution Check 8/8 PASS, Project Structure
- [spec.md](spec.md) (required) — US1 (P1: кортеж), US2 (P2: дедупликация), 7 Success Criteria
- [research.md](research.md) — 8 технических решений (R-001..R-008)
- [data-model.md](data-model.md) — без изменений схемы БД; 1 затронутый файл исходников
- [contracts/process-context.md](contracts/process-context.md) — контракт `context` для новых `KaraokeProcess.createProcess` вызовов
- [quickstart.md](quickstart.md) — 11 шагов end-to-end проверки + 3 граничных сценария

**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅, contracts/ ✅, quickstart.md ✅

**Tests**: НЕ генерируются. В CI тестов нет (см. AGENTS.md, Constitution § «Тесты»); существующие тесты `@Disabled`. Валидация — ручная пользователем по `quickstart.md` (обязательная часть US1/US2 фаз).

**Organization**: Задачи сгруппированы по user story для независимой реализации и валидации. Фича локальна: единственный файл исходников — `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt` (метод `createFromPath`); дополнительно обновляется 1 LiveDoc.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: можно выполнять параллельно (разные файлы, нет зависимостей)
- **[Story]**: метка фазы user story (US1, US2)
- В описании — точные пути файлов

## Path Conventions

- Backend (изменяемый): `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/`
- LiveDocs (обновляемые): `livedocs/features/`
- Спека/план (не меняются в этой фиче): `specs/282-import-folder-mp3-uploads-tuple/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Верификация фиче-ветки, baseline сборка, готовность рабочего дерева.

- [ ] T001 Verify feature branch is active (`git branch --show-current` ожидает `282-import-folder-mp3-uploads-tuple`)
- [ ] T002 Read spec.md и plan.md (контекст: что меняется, FR-001 фиксирует точные 6 вызовов; см. research.md R-001..R-008)
- [ ] T003 [P] Baseline compile без изменений: `./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel` (ожидание: BUILD SUCCESSFUL)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Чистое рабочее дерево против master; baseline линтеров без новых нарушений. Блокирует все user stories.

**⚠️ CRITICAL**: никакая работа по user stories не может начаться до завершения этой фазы.

- [ ] T004 Verify clean working tree before implementation: `git diff master..HEAD --stat` должен показать только `specs/282-import-folder-mp3-uploads-tuple/` (никаких изменений в исходниках `karaoke-app/`)
- [ ] T005 Baseline ktlint: `./gradlew :karaoke-web:ktlintCheck` (ожидание: PASS без новых нарушений)

**Checkpoint**: Foundation ready — можно приступать к user story.

---

## Phase 3: User Story 1 — Кортеж `demucs → mp3 → upload` в `Song.createFromPath()` (Priority: P1) 🎯 MVP

**Goal**: При импорте папки в `Song.createFromPath()` явно добавить 6 новых вызовов `KaraokeProcess.createProcess(...)` после существующего `DEMUCS2`, чтобы mp3-файлы голоса и аккомпанимента автоматически оказывались в локальном и удалённом MinIO (см. spec.md FR-001 и contracts/process-context.md).

**Independent Test**: Сценарий quickstart.md шаги 1-7 — после импорта папки из 2-3 треков все 11 записей в `tbl_processes` (1 KEY_BPM + 1 DEMUCS + 2 FF_MP3 + 4 UPLOAD × 2 mp3 типа × 2 хранилища) на песню; mp3 на диске, в локальном и удалённом MinIO; `stemVocalReady`/`stemAccompanimentReady` = true.

### Implementation for User Story 1

- [ ] T006 [US1] Edit `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt`: после существующего блока `KaraokeProcess.createProcess(song, action = DEMUCS2, doWait = true, prior = -1, threadId = 1)` (строка ~8200) добавить 6 новых вызовов согласно FR-001:
  1. `KaraokeProcess.createProcess(song, action = FF_MP3_ACCOMPANIMENT, doWait = true, prior = -1, threadId = 1)` — без `context`
  2. `KaraokeProcess.createProcess(song, action = FF_MP3_VOCAL, doWait = true, prior = -1, threadId = 1)` — без `context`
  3. `KaraokeProcess.createProcess(song, action = UPLOAD_TO_LOCAL_STORE, doWait = true, prior = -2, threadId = 1, context = mapOf("pathToFile" to song.accompanimentNameMp3, "karaokeFileType" to "MP3_ACCOMPANIMENT", "storageFileName" to "${song.storageFileName}.accompaniment.mp3", "bucketName" to song.storageBucketName))`
  4. То же для `UPLOAD_TO_LOCAL_STORE` с `pathToFile = song.vocalsNameMp3`, `karaokeFileType = "MP3_VOCAL"`, `storageFileName = "${song.storageFileName}.vocals.mp3"`
  5. То же для `UPLOAD_TO_REMOTE_STORE` с `MP3_ACCOMPANIMENT`
  6. То же для `UPLOAD_TO_REMOTE_STORE` с `MP3_VOCAL`

- [ ] T007 [US1] Edit `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt`: удалить закомментированные блоки `KaraokeProcess.createProcess(... FF_MP3_KAR ...)` и `... FF_MP3_LYR ...` (строки ~8208-8222) — cleanup согласно research.md R-004

- [ ] T008 [US1] Edit `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt`: обновить KDoc-комментарий метода `createFromPath` (строки 8058-8071) — в блоке «Кортеж задач» явно перечислить новые 6 шагов после `DEMUCS2` (см. существующий комментарий про «Первый шаг кортежа демукс→mp3→загрузка»)

- [ ] T009 [US1] Backend build (per AGENTS.md «Обязательная проверка после ЛЮБОГО изменения кода» шаги 1+3+4):
  - `./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel`
  - `./gradlew :karaoke-web:ktlintCheck` — без новых нарушений
  - `./gradlew :karaoke-web:bootJar --parallel`
  Ожидание: BUILD SUCCESSFUL; jar в `karaoke-web/build/libs/`

- [ ] T010 [US1] Manual validation через quickstart.md шаги 1-7:
  - Перезапустить `karaoke-app` локально (только если машина `dev-pc`/`dev`, см. Constitution § «Ограничения»)
  - Шаг 1: запустить «Добавить файлы из папки» на тестовой папке из 2-3 FLAC
  - Шаг 2: SQL-проверка `tbl_processes` — 11 записей на песню (соответствие SC-001)
  - Шаг 3: дождаться `DONE` для всех заданий
  - Шаг 4: проверить файлы на диске (`.mp3` для acc и vocals)
  - Шаг 5: проверить локальный MinIO (HTTP 200 на mp3-объекты)
  - Шаг 6: проверить удалённый MinIO (HTTP 200 на mp3-объекты)
  - Шаг 7: проверить UI карточки песни (`stemVocalReady`/`stemAccompanimentReady` = true)
  Ожидание: все 7 шагов PASS

**Checkpoint**: US1 полностью функциональна и независимо валидируема. После этой фазы фича уже даёт value (MVP).

---

## Phase 4: User Story 2 — Кортеж не дублирует уже существующие in-progress задачи (Priority: P2)

**Goal**: Подтвердить, что дедупликация `KaraokeProcess.createProcess` (R-001) корректно отрабатывает при повторных запусках импорта и при параллельной работе `HealthReport.startRepairAll` — никаких задвоений задач в `tbl_processes`, итоговый результат идентичен ожидаемому.

**Independent Test**: Сценарий quickstart.md шаги 8-11 — повторный импорт по той же папке не создаёт дублей; импорт 10 треков добавляет ровно 110 строк; UI не меняется; `HealthReport.startRepairAll` не дублирует уже идущие задачи кортежа.

**Замечание**: US2 не требует нового кода (дедупликация уже реализована в `KaraokeProcess.createProcess.kt:1001-1018`). Эта фаза — **валидация** существующего поведения под нагрузкой US1.

### Implementation for User Story 2

- [ ] T011 [US2] Manual validation через quickstart.md шаги 8-9:
  - Шаг 8: повторный запуск «Добавить файлы из папки» по той же папке (после US1 шага 10); ожидание: 0 новых строк в `tbl_processes` (соответствие SC-003)
  - Шаг 9: импорт папки из 10 треков; ожидание: ровно 110 новых строк в `tbl_processes` (соответствие SC-004)

- [ ] T012 [US2] Manual validation через quickstart.md шаги 10-11 + граничный сценарий G-01:
  - Шаг 10: `git diff master..HEAD -- webvue3/ karaoke-public/` — должно быть пусто (соответствие SC-006)
  - Шаг 11: запустить «Исправить всё» (`HealthReport.startRepairAll`) на импортированной песне через UI карточки; ожидание: НЕ создаются дубликаты `UPLOAD_*` (соответствие SC-005 и SC-007)
  - G-01: запустить «Добавить файлы из папки» дважды подряд; ожидание: повторный запуск пропускает уже созданные песни

**Checkpoint**: US1 и US2 обе работают независимо и валидируются; фича готова к Phase 5 (Polish).

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Обновление LiveDoc (FR-014), финальные проверки линтеров/секретов, коммит и PR.

- [ ] T013 [P] Edit `livedocs/features/082-fix-import-folder-oom.md`: обновить секцию «Что делает» (подраздел «Кортеж задач» — 3 шага → 7 шагов: DEMUCS2 → 2 × FF_MP3 → 4 × UPLOAD), секцию «Acceptance Criteria» (AC3: «Кортеж задач — 7 заданий в одном lane»), секцию «Связанные LiveDocs» (cross-link на `specs/282-import-folder-mp3-uploads-tuple/spec.md`). См. research.md R-006.

- [ ] T014 [P] Финальная проверка ktlint: `./gradlew :karaoke-web:ktlintCheck` (PASS, никаких новых нарушений)

- [ ] T015 [P] Pre-commit проверка секретов (Constitution § VIII): `git ls-files | grep -iE '\.env$|do\.env$|\.key$|\.pem$'` — ожидание: пусто

- [ ] T016 [P] Финальная проверка UI-независимости: `git diff master..HEAD -- webvue3/ karaoke-public/` — ожидание: пусто

- [ ] T017 Commit: `git add -A && git commit -m "282: добавить в кортеж импорта папки задания mp3 и загрузку в хранилища"`. Commit message на русском, в стиле `area: краткое описание` (см. AGENTS.md § «Рабочий процесс»)

- [ ] T018 Push: `git push -u origin 282-import-folder-mp3-uploads-tuple`

- [ ] T019 Create PR: `gh pr create --base master --title "282: mp3 голоса/аккомпанимента в кортеже импорта папки" --body "<ссылка на spec.md + plan.md + список SC-001..SC-007>"`. Дождаться `gh pr checks` (CI lint.yml: 7/7 PASS). Затем `gh pr merge --merge` (БЕЗ `--delete-branch` — lifecycle: ветка живёт после мёрджа, см. AGENTS.md § «Git — CI-gate для master»)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: нет зависимостей — стартует немедленно
- **Foundational (Phase 2)**: зависит от Setup — БЛОКИРУЕТ все user stories
- **User Stories (Phase 3, 4)**: зависят от Foundational
  - US1 (P1) выполняется первой (MVP)
  - US2 (P2) выполняется ПОСЛЕ US1 (это валидация поверх US1)
- **Polish (Phase 5)**: зависит от завершения US1 и US2

### User Story Dependencies

- **US1 (P1)**: стартует после Foundational — никаких зависимостей от других stories
- **US2 (P2)**: стартует после US1 — интегрируется с US1, но валидируется независимо

### Within Each User Story

- Редактирование исходников (T006, T007, T008) — все в одном файле, последовательно
- Backend build (T009) — после T006-T008
- Manual validation (T010) — после T009
- US2 (T011, T012) — после T010 (нужны импортированные данные)

### Parallel Opportunities

- T001, T002, T003 в Phase 1 — частично параллельны (T003 — gradle build, не зависит от T001/T002 после их запуска)
- T013, T014, T015, T016 в Phase 5 — все `[P]`, параллельны (разные файлы / разные команды)
- US1 implementation tasks (T006, T007, T008) — последовательны в одном файле

---

## Parallel Example: User Story 1

```bash
# Phase 5 — параллельные проверки (разные файлы / команды):
Task T013: "Edit livedocs/features/082-fix-import-folder-oom.md (LiveDoc update)"
Task T014: "Run ./gradlew :karaoke-web:ktlintCheck"
Task T015: "Run git ls-files | grep -iE secrets-check"
Task T016: "Run git diff master..HEAD -- webvue3/ karaoke-public/"

# Можно запустить все 4 в параллельных шеллах.
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. ✅ Phase 1: Setup (T001-T003)
2. ✅ Phase 2: Foundational (T004-T005)
3. ✅ Phase 3: User Story 1 (T006-T010)
4. 🛑 **STOP and VALIDATE**: запустить quickstart.md шаги 1-7 вручную (T010)
5. Готово к деплою / демо — фича уже даёт value (mp3 в MinIO без ручных шагов)

### Incremental Delivery

1. Setup + Foundational → foundation ready
2. US1 → валидация → **MVP deploy** (mp3 в хранилищах)
3. US2 → валидация дедупликации + параллельности
4. Phase 5 (Polish) → LiveDoc, lint, secrets check, commit, PR
5. Каждая фаза добавляет value, не ломая предыдущие

### Parallel Team Strategy

Фича локальна (1 файл исходников), реальной потребности в параллельной работе нет. Один разработчик последовательно проходит US1 → US2 → Polish.

---

## Notes

- **[P] tasks** = разные файлы / разные команды, нет зависимостей от незавершённых задач
- **[Story] labels** для трассировки: T006-T010 → US1, T011-T012 → US2, остальные — Setup/Foundational/Polish
- Каждая user story **независимо валидируема** через соответствующие шаги `quickstart.md`
- Тестов НЕТ (см. AGENTS.md / Constitution); все проверки — ручные пользователем
- Коммитить после каждой задачи или логической группы (например, после T006+T007+T008 одной фичей)
- Остановиться на любом checkpoint для валидации story независимо
- Избегать: расплывчатых формулировок, конфликтов в одном файле, межstory-зависимостей, нарушающих независимость
