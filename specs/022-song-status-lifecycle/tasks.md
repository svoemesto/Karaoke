---

description: "Task list for feature implementation"
---

# Tasks: Расширенный жизненный цикл статусов готовности песни

**Input**: Design documents from `/specs/022-song-status-lifecycle/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/song-status-lifecycle.md, quickstart.md

**Tests**: в CI автотестов для этого слоя нет (Конституция, «Рабочий процесс»); тестовые задачи не генерируются — проверка через `quickstart.md` вручную (см. Polish-фазу).

**Organization**: задачи сгруппированы по user story (spec.md) для независимой реализации/проверки каждой.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: можно выполнять параллельно (разные файлы, нет пересекающихся зависимостей)
- **[Story]**: US1/US2/US3 — какой user story принадлежит задача
- Указаны точные пути файлов

## Path Conventions

Существующий multi-module проект: `karaoke-app/src/main/kotlin/...`, `karaoke-web/src/main/kotlin/...`, `webvue3/src/...`, `deploy/karaoke-db/...` — см. `plan.md` → Project Structure.

---

## Phase 1: Setup

**Purpose**: подготовка SQL-миграции (без DDL, без пересоздания recordhash-триггеров — колонка `id_status` уже существует)

- [X] T001 Создать `deploy/karaoke-db/32_song_status_lifecycle.sql` — `UPDATE public.tbl_songs SET id_status = 6 WHERE id_status >= 3;` с шапкой-комментарием «Один раз на LOCAL и на SERVER отдельно» (по образцу `deploy/karaoke-db/27_author_special_order.sql`), см. `research.md` Decision 2

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: развязать легаси MLT-рендер-пайплайн от `id_status` и обновить лейблы статуса — без этого числа 4/6 продолжат означать два разных, конфликтующих смысла (`research.md` Decision 1)

**⚠️ CRITICAL**: пока эта фаза не завершена, не начинать User Story работы — иначе завершение легаси MELT-рендера может незаконно перепрыгнуть песню сразу на статус 6 в обход этапа 5

- [X] T002 [P] Обновить лейблы в геттере `Song.status` (`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt:360-371`): `when(idStatus)` → `0→"NONE"`, `1→"TEXT_CREATE"`, `2→"TEXT_CHECK"`, `3→"TEXT_WORDS_VERIFIED"`, `4→"MARKERS_CREATED"`, `5→"MARKERS_VERIFIED"`, `6→"READY"` (см. `data-model.md` таблицу лейблов)
- [X] T003 Убрать побочный эффект простановки статуса из `Song.createKaraoke()` (`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt:5417-5419`): удалить блок `if (idStatus < 3) { fields[SongField.ID_STATUS] = "3" }` целиком — рендер видео для соцплатформ больше не трогает статус готовности (тот же файл, что T002 — выполнять после T002)
- [X] T004 [P] Убрать дублирующий побочный эффект в легаси-эндпоинте `/song/{id}/createkaraoke` (`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/MainController.kt:1430-1436`): удалить блок `if (settings.idStatus < 3) { settings.fields[SongField.ID_STATUS] = "3"; settings.saveToDb() }` (тот же побочный эффект, что T003, другой call site)
- [X] T005 [P] Нейтрализовать автозавершение `idStatus==4L → "6"` в `KaraokeProcess.updateStatusProcessSettings` (`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcess.kt:405-429`): удалить условие `&& settings.idStatus == 4L` вместе с блоком `settings.fields[SongField.ID_STATUS] = "6"` в обеих ветках (`KaraokeProcessTypes.MELT_LYRICS`, `KaraokeProcessTypes.MELT_KARAOKE`) — завершение рендера видео для соцплатформ больше не переводит песню в «готова»
- [X] T006 Обновить `docs/features/mlt-generator.md` (FR-009 Конституции): зафиксировать, что `createKaraoke()`/`MELT_LYRICS`/`MELT_KARAOKE` больше не читают/не пишут `id_status` — рендер видео для соцплатформ и жизненный цикл готовности песни теперь полностью независимы (после T003-T005)

**Checkpoint**: легаси MLT-пайплайн отделён от `id_status`; лейблы статуса обновлены — можно параллельно начинать любую из трёх user story.

---

## Phase 3: User Story 1 — Существующие «готовые» песни остаются готовыми после миграции (Priority: P1) 🎯 MVP

**Goal**: перенести порог «готова» с `id_status>=3` на `>=6` во всех публичных read-путях и синхронизированных с ними admin-индикаторах, без потери видимости уже опубликованных песен

**Independent Test**: `quickstart.md` сценарии 1, 2, 6 — миграция идемпотентна; набор видимых на `karaoke-public` песен не меняется; admin-иконки доступности плеера согласованы с реальной доступностью

### Implementation for User Story 1

- [X] T007 [US1] Применить (LOCAL sandbox готово, дважды проверено — идемпотентно; SERVER — отдельно, ждёт согласия пользователя) `deploy/karaoke-db/32_song_status_lifecycle.sql` на LOCAL sandbox БД (дважды — проверить идемпотентность). Применение на SERVER — отдельным явным действием по прямому согласию пользователя (Конституция, «Ограничения и доступы агента», п. 2 «Категорически запрещено»)
- [X] T008 [P] [US1] Обновить `CONTENT_READY_FILTER` в `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/StatBySong.kt:50` (`id_status >= 3` → `id_status >= 6`)
- [X] T009 [P] [US1] Обновить `stemsReady()` в `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicPlayerController.kt:116-122` (`idStatus >= 3` → `idStatus >= 6`)
- [X] T010 [P] [US1] Обновить `onlyPublishedFor(...)` в `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt:250` (`">=3"` → `">=6"`)
- [X] T011 [P] [US1] Обновить фильтр `attr["id_status"]` в `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/MainController.kt:334` (legacy `/zakroma`, `/filter`, `">=3"` → `">=6"`)
- [X] T012 [P] [US1] Обновить `onlyPublished`-фильтр в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Zakroma.kt:36,74` (обе строки `getZakroma`/`getZakromaBySpecialOrder`, `">=3"` → `">=6"`)
- [X] T013 [P] [US1] Обновить `deleteSearchResultsForReadySongs` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReport.kt:2210` (`id_status >= 3` → `>= 6`)
- [X] T014 [P] [US1] Обновить фильтр экспорта датасета в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/ExportAlignmentDataset.kt:63` (`id_status >= 3` → `>= 6`)
- [X] T015 [P] [US1] Обновить иконки «Открыть онлайн-плеер»/«Открыть DEMO-плеер» в `webvue3/src/components/Songs/SongsTable.vue:220-287` (`#cell(player)`/`#cell(playerDemo)`: `idStatus >= 3` → `>= 6`, тексты тултипов «статус < 3» → «статус < 6»)
- [X] T016 [US1] Обновить порог доступности назначения crowd-редактору в `webvue3/src/components/Songs/SongsTable.vue:288-290` (`#cell(assign)`: `idStatus < 3` → `< 6`; тот же файл, что T015 — выполнять после T015)
- [X] T017 [P] [US1] Обновить `fsm-row-low-status` в `webvue3/src/components/Songs/edit/FamilySongsModal.vue:53` (`idStatus < 3` → `< 6`)
- [X] T018 [US1] Обновить `docs/features/stats.md` (FR-009 Конституции): новое определение «готова» (`id_status >= 6`), включая упоминания в комментариях `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/StatsCacheScheduler.kt:12,44` и `karaoke-public/src/store/modules/stats.js:12` (после T008-T017)
- [X] T035 [US1] *(обнаружено при реализации, не было в исходном research.md/tasks.md)* Обновить `crossedReadyThreshold` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt:4991` (`savedSettings.idStatus < 3L && this.idStatus >= 3L` → `< 6L`/`>= 6L`) — гейт пересчёта персистентных player-readiness флагов (`HealthReport.recomputeAndBroadcast`) и очистки результатов поиска текста при пересечении песней порога готовности в `saveToDb()`; без этой правки флаги готовности пересчитывались бы на старом (неверном) переходе статуса

**Checkpoint**: набор «готовых»/видимых песен на публичном сайте и в согласованных admin-индикаторах идентичен состоянию до миграции — User Story 1 полностью функциональна и проверяема независимо.

---

## Phase 4: User Story 2 — Администратор управляет расширенным статусом песни в редакторе (Priority: P2)

**Goal**: сделать все 7 значений статуса видимыми, корректно подписанными и свободно переключаемыми в SongEdit — без «легаси»-путаницы и без пропущенного значения 5

**Independent Test**: `quickstart.md` сценарий 3 — все 7 кнопок статуса кликабельны, корректно подписаны, сохранение/повторное открытие показывает правильное значение

### Implementation for User Story 2

> Все задачи этой фазы — один файл (`SongEdit.vue`), выполнять последовательно (не помечены `[P]`).

- [X] T019 [US2] Переразметить кнопки статуса 0,1,2,3,4,6 в `webvue3/src/components/Songs/edit/SongEdit.vue:1937-1998`: новые подписи и тултипы под `data-model.md` (например: 0 «❎ Новая», 1 «Txt🛠 Текст найден», 2 «Txt✅₁ Текст проверен: орфография/пунктуация», 3 «Txt✅₂ Текст проверен: слова соответствуют песне», 4 «Mrk🛠 Маркеры расставлены», 6 «✅ Готова»); убрать `class="group-button-legacy"` и тултипы «Легаси: этап старого MLT-рендера…» у кнопок 4 и 6; обновить тултип кнопки 3 (сейчас «Проект создан — доступно в онлайн-плеере» — это больше не так, доступность в плеере теперь у кнопки 6)
- [X] T020 [US2] Добавить недостающую кнопку статуса 5 «Mrk✅ Маркеры проверены» в `webvue3/src/components/Songs/edit/SongEdit.vue` (тот же блок ~1937-1998, между кнопками 4 и 6, по паттерну существующих: `class="group-button"`, `:class="statusButtonClass(5)"`, `@click="setStatus(5)"`)
- [X] T021 [US2] Обновить `setStatus(idStatus)` в `webvue3/src/components/Songs/edit/SongEdit.vue:3662-3696` — JS `switch` под новую 7-значную шкалу (`case 3: 'TEXT_WORDS_VERIFIED'`, `case 4: 'MARKERS_CREATED'`, добавить `case 5: 'MARKERS_VERIFIED'`, `case 6: 'READY'`), синхронно с `Song.status` (T002)
- [X] T022 [US2] Обновить гейт кнопки «Точные маркеры» в `webvue3/src/components/Songs/edit/SongEdit.vue:2346-2361` (`:disabled="song.idStatus >= 3"` → `>= 4`; тултип «недоступно — статус песни уже >= 3 (маркеры финальны)» → «>= 4»), согласовать с T024 (см. `research.md` Decision 6)
- [X] T023 [US2] Обновить JSDoc-комментарий главного компонента `SongEdit.vue` (Constitution VI) под новую семантику статуса

**Checkpoint**: администратор видит и может установить любое из 7 значений статуса с понятными названиями — User Story 2 полностью функциональна и проверяема независимо от US1/US3.

---

## Phase 5: User Story 3 — Автоматические этапы конвейера проставляют правильный промежуточный статус (Priority: P2)

**Goal**: каждый автоматический процесс, программно меняющий `id_status`, проставляет значение, соответствующее новой 7-значной таблице, продвигаясь строго на 1 шаг и никогда не понижая статус (FR-009, FR-010, FR-011 spec.md)

**Independent Test**: `quickstart.md` сценарий 4 — forced-align на песне со статусом 1 не поднимает статус до 4 (этапы 2/3 не пройдены); на песне со статусом 3 — поднимает ровно до 4; повторный более ранний автошаг не понижает статус 5/6

### Implementation for User Story 3

> T024-T027 частично пересекаются по затрагиваемым файлам (`Utils.kt`, `ApiController.kt`) — не помечены `[P]` относительно друг друга, выполнять последовательно. T028, T030 — отдельные файлы, могут выполняться параллельно с T024-T027; T029 — тот же файл, что T028 (после него); T031 (документация) — после всех.

- [X] T024 [US3] Переписать `executeForcedAlignMarkers` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt:3678-3723`: гейт постановки (строка ~3690, `if (settings.idStatus >= 3) return false`) → `if (settings.idStatus >= 4) return false`; итоговую простановку статуса (строки ~3718-3721, `if (settings.idStatus < 2) { fields[ID_STATUS] = "2" }`) заменить на `if (settings.idStatus == 3L) { settings.fields[SongField.ID_STATUS] = "4"; settings.saveToDb() }` — строго один шаг 3→4, статус не меняется вовсе, если текущий не 3 (FR-011, `research.md` Decision 5/6)
- [X] T025 [US3] Обновить гейты постановки в очередь `doProcessForcedAlignMarkers` (`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt:3762-3812`, строка ~3777: `settings.idStatus < 3` → `< 4`) и `getSongsCreateForcedAlignMarkersAll` (тот же файл, строка ~3841: `settings.idStatus < 3` → `< 4`), включая текст предупреждения «маркеры финальны» — согласовать с T022/T024
- [X] T026 [US3] Обновить `applyAudioParentMarkers` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt:4345-4358` (целевое значение `"3"` → `"6"` безусловно) и порог поиска родителя в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt:5249` (`audioParent.idStatus >= 3` → `>= 6`) — копирование только от полностью готового родителя (`research.md` Decision 5)
- [X] T027 [US3] Обновить `editSave` (апрув crowd-редактора) в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/SongEditorController.kt:353-357` (`if (settings.idStatus < 3) { fields[ID_STATUS] = "3" }` → `if (settings.idStatus < 6) { fields[ID_STATUS] = "6" }`; обновить комментарий «Сделать песню доступной в онлайн-плеере (idStatus>=3)» → «>=6»)
- [X] T028 [P] [US3] Обновить `fromnotdone`-пресет в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Publication.kt:1403,1688` (обе строки: `idStatus < 3L` → `< 6L`)
- [X] T029 [US3] Обновить `fromnotcheck`-пресет в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Publication.kt:1427,1712` (обе строки: `idStatus < 4L` → `< 3L`; убрать/обновить комментарий, ссылающийся на легаси `PROJECT_CHECK`) — тот же файл, что T028, выполнять после T028
- [X] T030 [P] [US3] Обновить SQL-прескан кандидатов в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt:102` (`WHERE root_id = 0 AND id_status < 3` → `< 6`); внутренний защитный guard `settings.idStatus >= 2` в `Utils.kt:136` НЕ менять — именно он защищает уже проверенный текст от перезаписи (`research.md` Decision 4)
- [X] T031 [US3] Обновить `docs/features/llm-lyrics-search.md` (FR-009 Конституции): зафиксировать новую 7-значную шкалу целиком и правки `executeForcedAlignMarkers`/`applyAudioParentMarkers`/прескана `Utils.kt:102` (после T024-T030)

- [X] T036 [US3] *(обнаружено при реализации)* Обновить порог поиска «готового» тёзки-оригинала по имени в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt:4156,4170` (`idStatus >= 3L` → `>= 6L`, аналог `applyAudioParentMarkers` из T026 — копировать текст/маркеры только от полностью готовой песни)
- [X] T037 [US1] *(обнаружено при реализации)* Обновить `onlyPublished`-фильтр в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt:6821` (`loadAuthorSongCounts`, `"id_status >= 3"` → `">= 6"`) — используется в `PublicApiController.kt` для подписи количества песен автора в закромах на проде

**Checkpoint**: все проверенные в `research.md` Decision 5 точки программной установки статуса приведены к новой семантике — User Story 3 полностью функциональна и проверяема независимо от US1/US2.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: финальная проверка качества и соответствия чек-листам проекта

- [X] T032 [P] Проверить/дополнить KDoc для затронутых публичных функций (`Song.status`, `Song.createKaraoke`, `KaraokeProcess.updateStatusProcessSettings`, `StatBySong.CONTENT_READY_FILTER`, `executeForcedAlignMarkers`, `applyAudioParentMarkers`, `editSave`) — Constitution VI / FR-006, если не покрыто по ходу T002-T031 (KDoc 96.8%, JSDoc 100% — `check-kdoc-coverage.sh`/`check-jsdoc-coverage.sh`, порог ≥50%)
- [X] T033 Прогнать сценарии `quickstart.md` на LOCAL sandbox — ЧАСТИЧНО: пересобраны и перезапущены `karaoke-web`+`webvue3` (реальные образы, не только `npm run build`); Сценарий 1 (миграция, идемпотентность) подтверждён напрямую SQL; Сценарий 2 (порог готовности) подтверждён вживую через `GET /api/public/stats` (14562 песни в коллекции) и `GET /api/public/zakroma` (автор с `id_status=6` → песни видны; автор только с `id_status=0` → пустой список); деплой-бандл `webvue3` проверен на наличие новых строк (`TEXT_WORDS_VERIFIED`, `MARKERS_VERIFIED`, «Готова — доступна в онлайн-плеере»). Сценарии 3-6 (интерактивные клики в SongEdit, forced-align в реальном времени) НЕ проверены — в окружении нет браузерного/Playwright-инструмента для клик-тестирования; рекомендуется пользователю проверить вручную или через `/run-skill-generator`
- [X] T034 Прогнать обязательный чек-лист перед коммитом из `CLAUDE.md`: `./gradlew ktlintCheck` ✅, `karaoke-app:compileKotlin`+`karaoke-web:compileKotlin` ✅, `cd webvue3 && npm run lint:check` ✅ && `npx prettier --check "src/**/*.{vue,js,ts,json}"` ✅ && `npm run build` ✅, `bash tools/check-kdoc-coverage.sh` ✅ (96.8%), `bash tools/check-jsdoc-coverage.sh webvue3` ✅ (100%), `pre-commit run --all-files` ✅ (все 7 хуков)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: без зависимостей — можно начинать сразу
- **Foundational (Phase 2)**: зависит от Setup; БЛОКИРУЕТ все user story (иначе легаси MELT-завершение может перепрыгнуть статус 5)
- **User Stories (Phase 3-5)**: все зависят от завершения Foundational; между собой независимы — можно вести параллельно (US1, US2, US3 не пересекаются по файлам, кроме общего понимания новой шкалы из `data-model.md`)
- **Polish (Phase 6)**: зависит от завершения желаемых user story (минимум US1 для MVP)

### User Story Dependencies

- **US1 (P1)**: после Foundational — не зависит от US2/US3
- **US2 (P2)**: после Foundational — не зависит от US1/US3 (использует те же лейблы из `data-model.md`, что и Foundational T002, но не требует завершения US1/US3 кода)
- **US3 (P2)**: после Foundational — не зависит от US1/US2. Желательно (не обязательно) выполнять ПОСЛЕ US2 T022, т.к. T025/T024 меняют тот же гейт «Точные маркеры», что и T022 в SongEdit.vue (порог 3→4) — рассинхронизация фронта и бэкенда временно возможна, но не блокирует независимую разработку

### Within Each User Story

- US1: миграция (T007) логически первая, но НЕ блокирует остальные code-level задачи T008-T017 (это правки исходного кода, не зависящие от состояния БД) — блокирует только end-to-end проверку (Independent Test)
- US2: все задачи — один файл, строго последовательно T019→T020→T021→T022→T023
- US3: T024→T027 частично пересекаются по файлам — последовательно; T028→T029 — тот же файл, последовательно; T030 — после остальных (документирует финальное поведение)

### Parallel Opportunities

- Foundational: T002, T004, T005, T006(после) — T002 и T004/T005 в разных файлах, параллельны; T003 — после T002 (тот же файл)
- US1: T008-T015, T017 — все в разных файлах, параллельны; T016 — после T015 (тот же файл); T018 — после всех (документация)
- US2: без параллелизма (один файл)
- US3: T028 параллелен с T024-T027 (разные файлы); T024-T027 — последовательно (пересекающиеся файлы); T029 — после T028; T030 — после всех
- После Foundational — US1, US2, US3 можно вести параллельно как три независимых потока работы

---

## Parallel Example: User Story 1

```bash
# После завершения Foundational (Phase 2), параллельно:
Task: "Обновить CONTENT_READY_FILTER в karaoke-web/.../StatBySong.kt:50"
Task: "Обновить stemsReady() в karaoke-web/.../PublicPlayerController.kt:116-122"
Task: "Обновить onlyPublishedFor(...) в karaoke-web/.../PublicApiController.kt:250"
Task: "Обновить attr[\"id_status\"] в karaoke-web/.../MainController.kt:334"
Task: "Обновить onlyPublished-фильтр в karaoke-app/.../model/Zakroma.kt:36,74"
Task: "Обновить deleteSearchResultsForReadySongs в karaoke-app/.../HealthReport.kt:2210"
Task: "Обновить фильтр экспорта датасета в karaoke-app/.../ExportAlignmentDataset.kt:63"
Task: "Обновить иконки плеера в webvue3/.../SongsTable.vue:220-287"
Task: "Обновить fsm-row-low-status в webvue3/.../FamilySongsModal.vue:53"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1: Setup (T001)
2. Phase 2: Foundational (T002-T006, КРИТИЧНО)
3. Phase 3: User Story 1 (T007-T018)
4. **STOP и ПРОВЕРИТЬ**: `quickstart.md` сценарии 1, 2, 6
5. MVP готов — публичный сайт использует новый порог `>=6`, набор видимых песен не изменился

### Incremental Delivery

1. Setup + Foundational → легаси MLT-пайплайн развязан, лейблы обновлены
2. + US1 → новый порог готовности на публичных поверхностях → MVP
3. + US2 → администратор может вручную вести песню через все 7 стадий
4. + US3 → автоматические процессы (Whisper forced-align и др.) корректно и безопасно продвигают статус
5. Каждая история добавляет ценность, не ломая предыдущие (файлы не пересекаются между US1/US2/US3)

### Parallel Team Strategy

После Foundational: один разработчик — US1 (backend readiness sweep), второй — US2 (SongEdit UI), третий — US3 (автоматика конвейера). Пересечение только концептуальное (общая шкала `data-model.md`), не файловое.

---

## Notes

- [P] задачи = разные файлы, нет пересекающихся зависимостей
- [Story] маппит задачу на конкретную user story для трассируемости
- Тестовые задачи не генерировались (не запрошены явно, в CI автотестов для этого слоя нет — см. `plan.md` Technical Context → Testing)
- Три пункта из `research.md` Decision 4/5 (`Utils.kt:102` прескан, `Publication.kt` оба пресета, `applyAudioParentMarkers`/`editSave`) получили окончательные решения на этом этапе — см. таблицы в `research.md` перед генерацией этого файла
- Применение миграции на SERVER (в отличие от LOCAL) требует отдельного явного согласия пользователя на каждое действие (Конституция, «Ограничения и доступы агента», п. 2)
