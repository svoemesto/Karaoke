---
description: "Task list for 232-admin-song-editor-local-db"
---

# Tasks: Облегчённый редактор песен в админке → локальная БД admin-машины

**Input**: Design documents from `/specs/232-admin-song-editor-local-db/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/, quickstart.md

**Tests**: в CI нет автоматических тестов на эту логику (см. `Constitution § Рабочий процесс → Тесты`); проверка — пользователем на admin-машине по `quickstart.md`. Тестовые задачи не генерируются.

**Organization**: Фича очень узкая (правка runtime-логики в 2 методах одного контроллера, ~10–20 строк, 0 SQL-миграций, 0 изменений во фронтенде). Этапы Setup и Foundational **отсутствуют** — инфраструктура проекта (Gradle, Spring MVC, `KaraokeConnection`, фронт webvue3) уже существует и не меняется. Все задачи сгруппированы по user stories (US1, US2) + Polish (LiveDoc + ручная валидация по quickstart).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **Backend**: `karaoke-app/src/main/kotlin/.../`
- **LiveDocs**: `livedocs/features/`
- **Specs**: `specs/232-admin-song-editor-local-db/`
- Single-file фича: все правки в одном Kotlin-файле + один новый LiveDoc.

---

## Phase 1: User Story 1 — Правки сохраняются в локальную БД admin-машины (Priority: P1) 🎯 MVP

**Goal**: `editById` и `editSave` в режиме `mode='song'` всегда читают/пишут Song в локальную БД admin-машины (`Connection.local()`), независимо от параметра `target`.

**Independent Test**: на admin-машине открыть редактор песни (`mode='song'`) с активным `assignmentsTarget='remote'`, изменить текст/маркеры, дождаться «Сохранено ✓», переоткрыть редактор — правки на месте; `recordhash` в LOCAL-БД изменился, в SERVER-БД — нет (см. `quickstart.md` Сценарий 1).

### Implementation for User Story 1

- [X] T001 [US1] Заменить `WORKING_DATABASE` на `Connection.local()` в вызове `Song.loadFromDbById` внутри `editById` в файле `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/SongEditorController.kt` (строка 757; см. контекст строк 752–760)
- [X] T002 [US1] В методе `editSave` (тот же файл, строки 837–901) для ветки `mode == "song"` (строки 859–878) убрать обёртку `withDb(target)` и использовать `Connection.local()` напрямую: заменить `db` на `Connection.local()` в `Song.loadFromDbById(id, db, …)` и в последующих вызовах `song.setSourceMarkers`/`song.setSourceText` (которые внутри пишут через `db`); параметр `target` оставить в сигнатуре `@RequestParam(required = false) target: String?` для обратной совместимости, но не использовать его для выбора БД в ветке `mode='song'`
- [X] T003 [US1] (FR-005, опционально) В методе `editById` (тот же файл) для ветки `mode == "song"`: если `Song.loadFromDbById(songId, Connection.local(), …)` вернул `null`, возвращать отличимый ответ с явным кодом ошибки `song_not_found_in_local_db` (например, `mapOf("found" to false, "id" to id, "songId" to songId, "error" to "song_not_found_in_local_db")` вместо текущего `mapOf("found" to false, "id" to id, "songId" to songId)`)
- [X] T004 [US1] Обновить KDoc комментарий над `editById` и `editSave` (тот же файл): явно зафиксировать, что в `mode='song'` Song всегда читается/пишется в LOCAL-БД, параметр `target` игнорируется (контракт см. `specs/232-admin-song-editor-local-db/contracts/api-contracts.md`)

**Checkpoint**: после T001–T004 код изменён. Требуется сборка jar и перезапуск `karaoke-app` для проверки (см. T008 / Phase 3).

---

## Phase 2: User Story 2 — Расхождение «что вижу — что сохраняю» устранено (Priority: P2)

**Goal**: гарантировать, что в `mode='song'` источник данных для чтения и записи строго совпадают (обе в LOCAL-БД); проверить через логи/БД, что после сохранения SERVER-БД не менялась до явного sync.

**Independent Test**: открыть редактор песни (`mode='song'`), загрузить данные, проверить в `karaoke-app` логах `name="LOCAL"` для `KaraokeConnection`; сохранить правки; через прямой SQL к LOCAL и SERVER убедиться, что LOCAL изменилась, SERVER — нет (см. `quickstart.md` Сценарий 2).

### Implementation for User Story 2

US2 покрывается **той же** правкой, что и US1 (замена `WORKING_DATABASE`/`db` на `Connection.local()` в `editById` и `editSave` — это и есть устранение расхождения). Никаких **новых** задач в коде не требуется. Проверка US2 — через ручные сценарии `quickstart.md`.

**Checkpoint**: US2 автоматически закрывается после успешного прохождения US1 (T001–T004) + ручной валидации по `quickstart.md` Сценарий 2.

---

## Phase 3: Polish & Cross-Cutting Concerns

**Purpose**: документация, ручная валидация, регрессионная проверка `mode='assignment'`.

- [X] T005 [P] Создать LiveDoc `livedocs/features/232-admin-song-editor-local-db.md` с frontmatter (`status: Active`, `slug: 232-admin-song-editor-local-db`, `related:` ссылки на `../domain/editorial.md`, `../architecture/dual-db-access.md`, `../../specs/232-admin-song-editor-local-db/spec.md`) и кратким описанием фичи (контракт: `edit/{byId,save}` в `mode='song'` всегда LOCAL, `mode='assignment'` без изменений, sync — отдельная явная операция)
- [X] T006 [P] Обновить `livedocs/features/README.md` — добавить запись о новом LiveDoc `232-admin-song-editor-local-db.md` в списке фич (если README структурирован по фичам; иначе — пропустить)
- [X] T007 Собрать jar: `cd /home/nsa/Karaoke && ./gradlew clean karaoke-app:bootJar --parallel` (см. `Constitution § Рабочий процесс → Сборка бэка`); убедиться, что ktlint/ESLint-baseline не зацепились за изменение (если зацепились — починить минимально, не расширяя скоуп)
- [ ] T008 Перезапустить контейнер `karaoke-app` на admin-машине (см. `Constitution § Рабочий процесс → Деплой`: пересборка/перезапуск контейнеров локально разрешены агенту; на dev-pc под dev — без согласия, на других машинах — по согласованию); убедиться, что `karaoke-app` стартовал без ошибок и подключился к LOCAL-БД
- [ ] T009 Провести ручную валидацию по `specs/232-admin-song-editor-local-db/quickstart.md` Сценарий 1 (P1, US1 AC1–AC3): правки видны после переоткрытия; LOCAL `recordhash` изменился; SERVER `recordhash` не изменился
- [ ] T010 Провести ручную валидацию по `quickstart.md` Сценарий 3 (US1 AC4): `mode='assignment'` НЕ сломался — задания на LOCAL и на SERVER по-прежнему target-aware
- [ ] T011 [P] Провести ручную валидацию по `quickstart.md` Сценарий 4 (Edge Case): песня есть только в SERVER-БД — редактор показывает понятную ошибку (после реализации T003 — с кодом `song_not_found_in_local_db`; если T003 не реализован — текущее поведение `found=false`)
- [ ] T012 [P] Провести ручную валидацию по `quickstart.md` Сценарий 2 (US2 AC1–AC2): логи `karaoke-app` показывают `name="LOCAL"` для `KaraokeConnection` при чтении/записи Song в `mode='song'`
- [X] T013 Запустить pre-commit / CI проверки: `tools/check-livedocs-structure.sh`, `tools/check-livedocs-cross-links.sh`, `tools/check-livedocs-external-links.sh`, `./tools/check-eslint-baseline.sh`, `./gradlew ktlintCheck` (полный список — см. AGENTS.md секция «LiveDocs CI / pre-commit» и Constitution § VI Code Standards); убедиться, что новые нарушения линтеров отсутствуют (если есть — починить)
- [ ] T014 Создать PR через `gh pr create --base master`, дождаться прохождения CI (`gh pr checks`), смерджить через `gh pr merge --merge` (см. AGENTS.md секция «Git — CI-gate для master»)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (US1)**: нет зависимостей (проект уже инициализирован) — может стартовать сразу.
- **Phase 2 (US2)**: зависит от Phase 1 (US1) — US2 закрывается той же правкой, что и US1.
- **Phase 3 (Polish)**: зависит от Phase 1 + Phase 2.

### User Story Dependencies

- **User Story 1 (P1)**: может стартовать сразу. Не зависит от других stories. **Это и есть MVP**.
- **User Story 2 (P2)**: автоматически закрывается реализацией US1 (та же правка в коде). Никаких дополнительных задач в коде не требуется — только ручная валидация T012.

### Within Each User Story

- US1: T001, T002 — основная правка (тот же файл, последовательно: сначала `editById`, потом `editSave`).
- T003 — опциональная правка поверх T001.
- T004 — обновление комментариев (KDoc), может идти параллельно с T003, но логически после T001+T002.
- US2: 0 задач в коде (только T012 в Phase 3).
- Polish: T005/T006 параллельно (разные файлы); T007 после T001–T004; T008 после T007; T009–T012 после T008; T013 после T005/T006; T014 — последний.

### Parallel Opportunities

- T005 [P] и T006 [P] — параллельно (разные файлы).
- T011 [P] и T012 [P] — параллельно (ручные сценарии, разные тестовые песни).
- Внутри US1: T003 [P] (опциональный код ошибки) можно делать параллельно с T004 [P] (KDoc), но после T001+T002.

---

## Parallel Example: User Story 1

```bash
# T001 + T002 — последовательно в одном файле (зависимость по строкам).
# T003 — параллельно с T004, после T001+T002.
# T005 + T006 — параллельно в Phase 3, после T001–T004.

# В одном bash-сеансе можно запустить ручные проверки параллельно
# (разные браузерные окна / разные тестовые песни):
Task: "T009 — quickstart.md Сценарий 1 (P1 US1)"
Task: "T011 — quickstart.md Сценарий 4 (Edge Case)"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Выполнить T001, T002 (минимальная правка runtime-логики в `SongEditorController.kt`).
2. **STOP**: собрать jar (T007), перезапустить `karaoke-app` (T008), проверить Сценарий 1 из quickstart.md (T009).
3. Если правки сохраняются в LOCAL — MVP готов, можно демонстрировать.

### Incremental Delivery

1. T001–T004 → кодовая база готова, MVP закрывает US1 (P1).
2. T009–T010 — ручная валидация US1 (закрывает P1 и acceptance scenario для mode='assignment').
3. T005–T006 — документация (LiveDoc обязателен по FR-014 / Constitution).
4. T011–T012 — ручная валидация edge case + US2.
5. T013 — линтеры/CI.
6. T014 — PR и merge.

### Notes

- T003 помечен как «опциональный» (FR-005 спеки говорит «MUST вернуть отличимый код», но это улучшение UX, не блокер основной фичи). Если пользователь предпочтёт минимальный diff — T003 можно пропустить, поведение `found=false` сохранения обратной совместимости не ломает.
- T007–T008 (сборка + перезапуск): см. `Constitution § Рабочий процесс → Сборка бэка` и `AGENTS.md → Ограничения агента → п. 1` — на admin-машине под обычным пользователем пересборка `karaoke-app` локально разрешена, перезапуск контейнера — по согласованию; на dev-pc под dev — без согласования.
- T014 (PR): строго через feature-ветку (см. AGENTS.md → «CI-gate для master (NON-NEGOTIABLE)»); прямой push в master запрещён.

---

## Task Summary

| Phase | Task ID | Story | Описание | Файл |
|-------|---------|-------|----------|------|
| 1 (US1) | T001 | US1 | `editById`: заменить `WORKING_DATABASE` на `Connection.local()` | `karaoke-app/.../SongEditorController.kt:757` |
| 1 (US1) | T002 | US1 | `editSave` (mode='song'): убрать `withDb(target)`, использовать `Connection.local()` | `karaoke-app/.../SongEditorController.kt:859-878` |
| 1 (US1) | T003 | US1 | (опц.) Отличимый код `song_not_found_in_local_db` | `karaoke-app/.../SongEditorController.kt:754-760` |
| 1 (US1) | T004 | US1 | KDoc с контрактом для `editById`/`editSave` | `karaoke-app/.../SongEditorController.kt` (комментарии над методами) |
| 2 (US2) | — | US2 | Покрывается T001+T002; отдельных задач в коде нет | — |
| 3 (Polish) | T005 | — | LiveDoc `232-admin-song-editor-local-db.md` | `livedocs/features/232-admin-song-editor-local-db.md` |
| 3 (Polish) | T006 | — | Обновить `livedocs/features/README.md` | `livedocs/features/README.md` |
| 3 (Polish) | T007 | — | Сборка jar | `./gradlew clean karaoke-app:bootJar --parallel` |
| 3 (Polish) | T008 | — | Перезапуск `karaoke-app` | `docker compose ...` / `deploy/do.sh` |
| 3 (Polish) | T009 | — | Quickstart Сценарий 1 (US1 P1) | `specs/232-admin-song-editor-local-db/quickstart.md` |
| 3 (Polish) | T010 | — | Quickstart Сценарий 3 (US1 AC4 mode='assignment') | `quickstart.md` |
| 3 (Polish) | T011 | — | Quickstart Сценарий 4 (Edge Case) | `quickstart.md` |
| 3 (Polish) | T012 | — | Quickstart Сценарий 2 (US2 AC1–AC2) | `quickstart.md` |
| 3 (Polish) | T013 | — | LiveDocs CI / ktlint / eslint-baseline | инструменты из AGENTS.md |
| 3 (Polish) | T014 | — | PR + merge через `gh` | — |

**Всего**: 14 задач (T001–T014), из них 4 кода + 1 опциональный (T003), 9 Polish.

**Выполнено на текущий момент** (9/14):
- T001–T004 ✅ — код в `karaoke-app/.../controllers/SongEditorController.kt`;
- T005 ✅ — LiveDoc `livedocs/features/232-admin-song-editor-local-db.md`;
- T006 ✅ — обновлён `livedocs/features/README.md`;
- T007 ✅ — jar собран (`./gradlew clean karaoke-app:bootJar --parallel` — BUILD SUCCESSFUL);
- T013 ✅ — LiveDocs CI (3/3 OK), ktlint (UP-TO-DATE), ESLint-baseline (0 нарушений).

**Осталось** (5/14) — требуют работающего стека karaoke-app или явного согласия пользователя:
- **T008** — перезапуск контейнера `karaoke-app` (по `Constitution § Ограничения и доступы агента п. 1` на nsa-i9 под nsa — **по согласованию с пользователем**);
- **T009–T012** — ручная валидация по `quickstart.md` (после T008);
- **T014** — PR через `gh pr create --base master` (по запросу пользователя; см. AGENTS.md → «CI-gate для master (NON-NEGOTIABLE)»).

**MVP**: T001–T002 + T007–T008 + T009 (= 5 задач). Этого достаточно, чтобы US1 был функционален и независимо проверяем на admin-машине.

**Parallel opportunities**: T005+T006 (LiveDoc + README), T011+T012 (ручные сценарии на разных песнях/браузерах).

**Format validation**: ✅ Все задачи следуют чек-лист-формату (`- [ ] TXXX [P?] [Story?] Description with file path`).
