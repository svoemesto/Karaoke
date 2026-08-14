---
description: "Task list for ускорение загрузки песен в Закромах"
---

# Tasks: Ускорение загрузки песен в Закромах

**Input**: Design documents from `/specs/186-zakroma-songs-fast-load/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/, quickstart.md

**Tests**: Tests are OPTIONAL — not requested in spec.md. Validation via quickstart.md scenarios (6 ручных + 2 опциональных backend).

**Organization**: 3 user stories (US1, US2 — оба P1; US3 — P2). Разбиты на 5 фаз (Setup → Foundational → 3 US → Polish). Направления R1+R2 (backend) реализуют US1. R3 (frontend) обслуживает US2 + US3.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **Backend**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/` и `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/`
- **Frontend**: `karaoke-public/src/composables/` и `karaoke-public/src/store/modules/`
- **Документация**: `docs/features/`, `docs/architecture-notes.md`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Подготовка рабочего места, baseline проверки (Constitution VI).

- [X] T001 Проверить baseline линтеров ktlint/ESLint — должны быть на месте (Constitution VI FR-007), иначе добавить `New violations today: 0` в `tools/baseline-stats.sh` отчёт
- [X] T002 Прочитать `docs/features/zakroma-stream-progress.md` (актуальное состояние per-feature документа для спеки 181) — нужно для последующего обновления в Phase 6

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Shared batch-инфраструктура для backend (R1). Без неё US1 не может стартовать. US2 и US3 от неё НЕ зависят (frontend-only), но если Phase 2 заблокирован — можно стартовать US3 параллельно.

**⚠️ CRITICAL**: Phase 2 MUST быть завершена до старта US1.

- [X] T003 [P] Добавить метод `Pictures.getPicturesByNames(names: List<String>, database: KaraokeConnection, storageService: KaraokeStorageService, storageApiClient: StorageApiClient, ignoreUseInList: Boolean = false): Map<String, Picture>` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Pictures.kt` — один SQL `WHERE name IN (?, ?, ...)` вместо N×1, KDoc + `@see docs/features/zakroma-stream-progress.md` обязательно (Constitution VI FR-006)
- [X] T004 [P] Добавить метод `Album.getAlbumsByIds(ids: List<Long>, database: KaraokeConnection, storageService: KaraokeStorageService, storageApiClient: StorageApiClient): Map<Long, Album>` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Album.kt` — один SQL `WHERE id IN (?, ?, ...)` вместо N×1, KDoc обязательно
- [X] T005 [P] Запустить `./gradlew :karaoke-app:compileKotlin` — обе компиляции должны пройти без ошибок (быстрая проверка корректности сигнатур)

**Checkpoint**: Foundation ready — backend умеет batch-загружать картинки и альбомы. US1 можно начинать.

---

## Phase 3: User Story 1 — Быстрая загрузка песен крупного автора (Priority: P1) 🎯 MVP

**Goal**: Устранить backend N+1 SQL (`Zakroma.buildFromSongs`) и лишние flush (`PublicApiController.zakromaStream`), чтобы для автора с 2500 песен полное время загрузки было ≤ 7 сек (SC-002), первая партия ≤ 2 сек (SC-001), улучшение минимум ×2 (SC-005).

**Independent Test**: Сценарий 1-3 + 7 из `quickstart.md` (замеры backend latency, Network → TTFB, видеозапись прогрессометра, SQL-логи backend).

### Implementation for User Story 1

- [X] T006 [US1] Переписать `private fun buildFromSongs(songList: List<Song>, database, storageService, storageApiClient)` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Zakroma.kt` (строки 96-215): собрать ВСЕ author names + album lookup names + album IDs ДО цикла, сделать 2 batch-вызова `Pictures.getPicturesByNames` (для авторов, для альбомов) + 1 batch `Album.getAlbumsByIds` + 1 batch `Author.getAuthorByName` (опц., для будущего), затем строить `Zakroma`/`ZakromaAlbum` из Map без дополнительных SQL. Цель: для автора с 30 альбомами — **≤ 4 SQL вместо 93**. KDoc метода обновить с указанием нового контракта.
- [X] T007 [US1] Запустить `./gradlew :karaoke-app:compileKotlin` после T006 — убедиться, что `buildFromSongs` компилируется без ошибок и типы Map<String, Picture>/Map<Long, Album> используются корректно
- [X] T008 [US1] Переписать цикл стриминга в `fun zakromaStream(...)` в `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt` (строки 336-369): добавить `StringBuilder(64 * 1024)` буфер + `var bufferedSongCount = 0` + `val FLUSH_EVERY_N_SONGS = 50`. В цикле `for (song in album.albumSongs)` — писать песни в буфер (НЕ в writer); когда `bufferedSongCount >= 50` — `writer.write(buffer.toString())` + `buffer.clear()` + `writer.flush()` + `out.flush()`. В конце — финальный `writer.write(remaining buffer) + done message + flush`. Album-сообщения отправлять **сразу** (не батчатся с песнями). Цель: **82 flush вместо 5064** для 2500 песен / 30 альбомах. Комментарий в коде: ссылка на research.md R2.
- [X] T009 [US1] Запустить `./gradlew :karaoke-web:compileKotlin` после T008 — убедиться, что компилируется без ошибок
- [X] T010 [P] [US1] Запустить `./gradlew ktlintCheck` — обе компиляции (karaoke-app, karaoke-web) НЕ должны увеличивать baseline (`config/ktlint/baseline-*.xml`). Если появились новые violations — починить (Constitution VI FR-007).
- [X] T011 [US1] Собрать fat-jar: `./gradlew karaoke-app:bootJar karaoke-web:bootJar --parallel` — обе сборки должны быть SUCCESS без warnings о неиспользованных импортах
- [ ] T012 [US1] Валидация (сценарий 7 из `quickstart.md`): задеплоить собранные jar'ы локально (если есть доступ к admin-машине), открыть DevTools → Network, кликнуть на тайл крупного автора, в логах backend'а (`docker logs karaoke-app`) подтвердить: число SQL-запросов к `tbl_pictures` **≤ 2**, к `tbl_albums` **≤ 1** (вместо `2 + 60 + 30 = 92`). Если > 2 к pictures или > 1 к albums — откатить T006 и проверить batch-методы.
- [ ] T013 [US1] Валидация (сценарий 1 из `quickstart.md`): на проде/local, замерить полное время загрузки автора с 2500 песен. Должно быть **≤ 7 сек** (SC-002). Если > 7 сек — замерить отдельно backend latency (`/api/public/zakroma/stream` в Network tab → `Time`) и frontend perceived latency (от клика до `state.zakroma` непустой). Если backend > 1 сек — оптимизировать Jackson (`ObjectMapper` reuse / `writer.writeValues(out)` с массивом — research.md R4 backlog). Если frontend > 5 сек — добавить дополнительный батчинг в T008 (например, `FLUSH_EVERY_N_SONGS = 100`).
- [ ] T014 [US1] Валидация (сценарий 2 из `quickstart.md`): замерить TTFB и время до первой песни в списке. Должно быть **≤ 2 сек** (SC-001). Если нет — проверить, что frontend передаёт `expectedCount` в URL (см. `useZakromaStreamProgress.js:107-109`), иначе backend ждёт `Song.loadAuthorSongCounts()` (100-500мс).

**Checkpoint**: User Story 1 (MVP) полностью функциональна и независимо тестируема. Автор с 2500 песен грузится ≤ 7 сек, первая партия ≤ 2 сек.

---

## Phase 4: User Story 2 — Прогрессометр остаётся плавным в активной вкладке (Priority: P1)

**Goal**: Заменить `setTimeout(resolve, 0)` × N на batched yield по 50 сообщений (через `queueMicrotask`), чтобы в **активной** вкладке прогрессометр рос плавно (не скачками), но без траты 2500 event-loop ticks.

**Independent Test**: Сценарий 3 из `quickstart.md` — видеозапись прогрессометра. В Network tab: число flush должно быть ~82 (вместо 5064). На timeline Performance tab: между обработкой сообщений НЕ должно быть пауз > 50мс.

### Implementation for User Story 2

- [X] T015 [US2] Заменить `await new Promise((resolve) => setTimeout(resolve, 0))` в `karaoke-public/src/composables/useZakromaStreamProgress.js` (строка 173) на batched yield: добавить `let batchCount = 0; const BATCH_FLUSH = 50` снаружи `while` цикла (строки 144-175), инкрементить `batchCount++` в цикле обработки NDJSON-строк, делать yield только когда `batchCount >= BATCH_FLUSH` (т.е. один yield на 50 сообщений, а не на каждое). Заменить `setTimeout(resolve, 0)` на `Promise.resolve().then(resolve)` (microtask, не тротлится). JSDoc комментарий обновить с обоснованием batched yield vs per-message yield. Цель: 50 yield вместо 2500.
- [X] T016 [US2] Запустить `cd karaoke-public && npm run lint:check` — должно быть 0 новых violations (`webvue3`/etc не затрагивается, проверяется только `karaoke-public/.eslint-baseline.json`)
- [X] T017 [US2] Собрать `karaoke-public`: `cd karaoke-public && npm run build` — сборка должна быть SUCCESS без warnings о неиспользованных переменных (`batchCount` etc.)
- [ ] T018 [US2] Валидация (сценарий 3 из `quickstart.md`): видеозаписать прогрессометр при загрузке крупного автора. Прогрессометр должен расти **плавно** (не скачками), появляться в течение 100 мс (FR-004, SC-003). В Network tab проверить: общее время ответа backend'а, число NDJSON-сообщений `song` = числу песен автора.
- [ ] T019 [US2] Валидация (Performance tab): записать performance profile, проверить, что в timeline нет длинных блокирующих тасков > 50мс (script-blocking) во время обработки стрима. Если есть — увеличить `BATCH_FLUSH` до 100 в T015.

**Checkpoint**: User Story 2 функциональна. Прогрессометр плавный в активной вкладке. User Story 1 НЕ сломана (US1 + US2 вместе).

---

## Phase 5: User Story 3 — Корректность при переключении вкладок (Priority: P2)

**Goal**: Исправить баг «прогрессометр не сдвигается, когда пользователь на другой вкладке». При возврате на вкладку прогрессометр должен показать актуальный процент, а не то значение, на котором ушли со вкладки (FR-005, FR-006, SC-004).

**Independent Test**: Сценарий 4 из `quickstart.md` — Chrome, переключиться на другую вкладку на 30 сек во время загрузки, вернуться. Прогрессометр должен быть ≥ 95 % или скрыт (если загрузка завершилась).

### Implementation for User Story 3

- [X] T020 [US3] Добавить `visibilitychange` listener в `karaoke-public/src/composables/useZakromaStreamProgress.js`: (а) внутри `start()` объявить `let pendingVisibilityPush = false`; (б) добавить флаг `if (document.visibilityState === 'hidden') { pendingVisibilityPush = true; }` в цикле обработки NDJSON — в этом случае НЕ yield'ить (`batchCount` сбрасывать, но yield пропустить); (в) добавить глобальный listener на `document.addEventListener('visibilitychange', ...)` — если `visibilityState === 'visible'` и `pendingVisibilityPush === true`, вызвать `nextTick().then(() => { pendingVisibilityPush = false })` (импорт `nextTick` из `'vue'`). JSDoc объяснить причину: `setTimeout` тротлится в фоне до 1000мс/yield, на 2500 чанков это 41 минута обработки в фоне.
- [X] T021 [US3] Запустить `cd karaoke-public && npm run lint:check` после T020 — 0 новых violations
- [X] T022 [US3] Собрать `karaoke-public`: `cd karaoke-public && npm run build` — SUCCESS
- [ ] T023 [US3] Валидация (сценарий 4 из `quickstart.md`, **Chrome обязательно**): кликнуть на тайл крупного автора → **сразу** переключиться на другую вкладку (например, `google.com`) → подождать **30 секунд** → вернуться. Ожидаемое поведение: прогрессометр **сразу** (≤ 500 мс) показывает ≥ 95 % или скрыт (если загрузка завершилась). В списке видны все песни. В Console НЕТ ошибок. В Network tab: стрим отмечен как `(canceled)` или завершён — но фронт уже обработал данные (благодаря тому, что в фоне yield'ов нет).
- [ ] T024 [US3] Регрессионная проверка (сценарий 5 из `quickstart.md`): кликнуть на крупного автора → через 1-2 сек (пока идёт загрузка) кликнуть на другого. Ожидаемое поведение: первый стрим прерван, второй — новый, в списке видны песни только второго автора. **Это требование из спеки 181 — `AbortController` уже должен работать; проверить, что T020 не сломал его**. Если сломал — в T020 добавить `pendingVisibilityPush = false` в `cancel()`.

**Checkpoint**: User Story 3 функциональна. Все 3 user stories (US1+US2+US3) работают вместе без регрессий.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Документация, финальные проверки, baseline.

- [X] T025 [P] Обновить `docs/features/zakroma-stream-progress.md`: добавить секцию «Pass 52 — ускорение (spec 186)» с описанием (а) batch lookup'ов в `Pictures`/`Album`, (б) batched flush в `PublicApiController`, (в) queueMicrotask + visibilitychange в `useZakromaStreamProgress`. Указать целевые метрики (≤ 7 сек на 2500 песен) и ссылки на `specs/186-zakroma-songs-fast-load/research.md`. Сохранить существующее описание спеки 181 — это **дополнение**, не замена.
- [X] T026 [P] Добавить запись в `docs/architecture-notes.md` (Pass 52): дата 2026-08-14, краткое описание фичи (3 user stories, целевые метрики), список изменённых файлов, урок (Chrome тротлит setTimeout в фоне до 1000мс/yield — важно для будущих стримов). Стиль записи — как у предыдущих Pass'ов.
- [ ] T027 [P] Прогнать quickstart сценарии 1-6 на финальной сборке, заполнить чек-лист в `quickstart.md` (заменить ☐ на ☑ для пройденных сценариев). Если какой-то сценарий FAIL — открыть follow-up задачу (не блокирует merge, если это **не** SC-001/SC-002/SC-004 — эти три MUST быть PASS).
- [X] T028 [P] Финальная проверка линтеров: `./gradlew ktlintCheck` + `cd webvue3 && npm run lint:check` + `cd karaoke-public && npm run lint:check` — все три должны быть зелёными (или baseline без роста)
- [X] T029 [P] KDoc coverage check: `bash tools/check-kdoc-coverage.sh` — должно быть 100% (Constitution VI FR-006). Новые публичные методы (`Pictures.getPicturesByNames`, `Album.getAlbumsByIds`) — с полным KDoc.
- [X] T030 [P] JSDoc coverage check: `bash tools/check-jsdoc-coverage.sh karaoke-public` — должно быть 100%. Все изменённые функции в `useZakromaStreamProgress.js` — с JSDoc.
- [ ] T031 Создать PR: `git push -u origin 186-zakroma-songs-fast-load` → `gh pr create --base master --title "Pass 52: ускорение загрузки песен в Закромах (spec 186)" --body "..."` (см. `.github/PULL_REQUEST_TEMPLATE.md`). Дождаться CI 7/7 SUCCESS (см. AGENTS.md «CI-gate для master»). **Ветку НЕ удалять** после merge.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately.
- **Foundational (Phase 2)**: Depends on Setup (Phase 1) — BLOCKS US1 (US2/US3 — frontend-only, могут стартовать параллельно после Setup).
- **US1 (Phase 3)**: Depends on Foundational (Phase 2). Блокирует US2 частично (US2 frontend валидирует результат US1 backend).
- **US2 (Phase 4)**: Зависит от Foundational (Phase 2) — формально нет, но логически рекомендуется после US1 (легче отлаживать на работающем backend).
- **US3 (Phase 5)**: Зависит от Foundational (Phase 2) — формально нет, frontend-only. Но **изменения в T020 накладываются на T015**, поэтому US3 делается после US2.
- **Polish (Phase 6)**: Depends on all 3 user stories complete.

### User Story Dependencies

- **US1 (P1)**: Требует Phase 2 (batch-методы в `Pictures`/`Album`). No dependencies on US2/US3.
- **US2 (P1)**: Требует Phase 2 опционально (для сборки/тестирования end-to-end). Файлы: только `useZakromaStreamProgress.js`. No dependencies on US1/US3.
- **US3 (P2)**: Требует US2 (изменения в T020 накладываются на T015). Файлы: только `useZakromaStreamProgress.js`. No dependencies on US1.

### Within Each User Story

- **US1**: T006 → T007 → T008 → T009 → T010 → T011 → (T012, T013, T014 валидация параллельно)
- **US2**: T015 → T016 → T017 → (T018, T019 валидация)
- **US3**: T020 → T021 → T022 → (T023, T024 валидация)

### Parallel Opportunities

- **Phase 1 (T001, T002)** — `[P]` можно параллельно (разные инструменты: gradle baseline vs чтение MD).
- **Phase 2 (T003, T004, T005)** — T003 и T004 `[P]` (разные файлы: `Pictures.kt` vs `Album.kt`), T005 после них.
- **Phase 3 (T010, T012, T013, T014)** — T010 `[P]` (ktlint независим от других). T012/T013/T014 — `[P]` друг с другом (валидация разных аспектов).
- **Phase 4 (T018, T019)** — `[P]` друг с другом (валидация визуала vs performance).
- **Phase 5 (T023, T024)** — `[P]` друг с другом (разные сценарии).
- **Phase 6 (T025, T026, T027, T028, T029, T030)** — большинство `[P]` (документация + проверки независимы).
- **Кросс-US параллелизм**: US1 (backend) и US2+US3 (frontend) можно вести параллельно разными разработчиками — файлы не пересекаются.

---

## Parallel Example: User Story 1

```bash
# Phase 2 — оба batch-метода параллельно:
Task: "Добавить Pictures.getPicturesByNames в karaoke-app/.../Pictures.kt"
Task: "Добавить Album.getAlbumsByIds в karaoke-app/.../Album.kt"

# После компиляции T005 — US1 implementation:
Task: "Переписать buildFromSongs в karaoke-app/.../Zakroma.kt"
Task: "Переписать zakromaStream flush в karaoke-web/.../PublicApiController.kt"

# Валидация US1 — все три сценария параллельно:
Task: "Backend SQL log validation"
Task: "Network TTFB / full-load timing"
Task: "Performance profile"

# Параллельно с US1 — можно стартовать US2/US3 (frontend, разные файлы):
Task: "Batched yield в useZakromaStreamProgress.js"  # US2
Task: "visibilitychange listener в useZakromaStreamProgress.js"  # US3 (после US2)
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001, T002)
2. Complete Phase 2: Foundational (T003, T004, T005)
3. Complete Phase 3: User Story 1 (T006-T014)
4. **STOP and VALIDATE**: прогнать сценарии 1-3 + 7 из `quickstart.md`. Все 4 должны быть PASS.
5. Deploy на local/staging, дать редакторам потрогать (или пользователю).
6. **Опционально**: merge US1 как отдельный PR (минимальный риск регрессии).

### Incremental Delivery

Рекомендуемый порядок (минимизация риска):
1. Phase 1 + Phase 2 → Foundation ready
2. **PR #1**: US1 (backend batch SQL + flush) → backend-only изменения, frontend не затрагивается → merge → деплой на прод → замеры
3. **PR #2**: US2 (batched yield на frontend) → merge → деплой → замеры
4. **PR #3**: US3 (visibilitychange fix) → merge → деплой → замеры
5. **PR #4**: Polish (документация + финальные проверки) → merge

**Альтернатива (один большой PR)**: US1+US2+US3 вместе — допустимо, но больше риск regression в одном merge. Если нет жёсткого требования к одному PR, лучше разбить на 3.

### Parallel Team Strategy

С 2+ разработчиками:
1. Оба делают Phase 1+2 вместе (1-2 часа).
2. После Phase 2:
   - **Developer A** — US1 (backend: T006-T014).
   - **Developer B** — US2 + US3 (frontend: T015-T024).
3. Developer B может стартовать US2 сразу после Phase 2 (не дожидаясь US1). US3 — после US2.
4. Polish — оба вместе.

---

## Notes

- **[P] tasks** = different files, no dependencies. **НЕ** помечать `[P]` задачи, которые трогают один файл (race condition при параллельном выполнении).
- **[Story] label** — обязателен для US-фаз. Setup/Foundational/Polish — без лейбла.
- **Каждая US-фаза должна быть независимо завершаемой** — после US1 можно merge, не дожидаясь US2/US3.
- **Коммитить после каждой задачи** или логической группы (Phase 2 — 1 коммит: «feat(karaoke-app): add batch getPicturesByNames/getAlbumsByIds»; Phase 3 US1 — 2 коммита: «feat(karaoke-app): rewrite buildFromSongs with batch lookups» + «feat(karaoke-web): batched NDJSON flush»).
- **Stop at any checkpoint** для валидации US независимо.
- **Избегать**: расплывчатых задач, конфликтов в одном файле, cross-story зависимостей ломающих независимость.
- **Важно**: baseline ktlint/ESLint НЕ должен расти (Constitution VI FR-007). Если растёт — починить до commit.
- **CI-gate**: PR проходит ТОЛЬКО после CI 7/7 SUCCESS (см. AGENTS.md «CI-gate для master»). Ветку после merge **НЕ удалять** (NON-NEGOTIABLE).
- **После merge** — обновить `docs/architecture-notes.md` отдельным коммитом (Pass 52), если ещё не сделано в T026. Это допускается как direct-to-master commit для docs-only (см. AGENTS.md «Исключения для документации-only изменений»).