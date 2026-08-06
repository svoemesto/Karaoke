# Tasks: Упрощение PublishTableBodyTd + полная чистка DTO от processColor*

**Input**: Design documents from `/home/nsa/Karaoke/specs/160-publish-body-td-remove-six-columns/`
**Branch**: `160-publish-body-td-remove-six-columns`
**Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Research**: [research.md](./research.md) | **Data Model**: [data-model.md](./data-model.md) | **Contracts**: [contracts/api-songsdigests.md](./contracts/api-songsdigests.md) | **Quickstart**: [quickstart.md](./quickstart.md)

**Tests**: НЕ запрашивались в спеке (см. Assumptions). Валидация — ручная по [quickstart.md](./quickstart.md) + линтеры/CI-gate.

**Organization**: Задачи сгруппированы по user story для независимой реализации и валидации.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: можно выполнять параллельно (разные файлы, нет зависимостей)
- **[Story]**: к какой user story относится задача (US1, US2, US3, US4)
- В описании — точные пути файлов и номера строк

## Path Conventions

Это web-приложение (Option 2):
- Backend: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/...`
- Frontend: `webvue3/src/components/...`
- Docs: `docs/...`

---

## Phase 1: Setup (Shared Infrastructure)

**Цель**: проверить, что рабочая среда соответствует требованиям; загрузить артефакты плана в контекст.

- [X] T001 Перейти на ветку `160-publish-body-td-remove-six-columns` и убедиться, что `git status` показывает чистое состояние (только untracked файлы `research.md`, `data-model.md`, `quickstart.md`, `contracts/`, `plan.md` — никаких модифицированных трекаемых файлов) в репозитории `/home/nsa/Karaoke`
- [X] T002 Прочитать дизайн-артефакты: `specs/160-publish-body-td-remove-six-columns/spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/api-songsdigests.md`, `quickstart.md` — для полного понимания скоупа перед редактированием
- [X] T003 [P] Проверить pre-conditions: `java -version` (JDK 17), `node -v` (v22 LTS), `./gradlew --version` (Gradle wrapper), `ls /home/nsa/Karaoke/webvue3/node_modules` (зависимости установлены)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Цель**: pre-flight grep-аудит для подтверждения, что мы знаем все места, которые НЕ должны трогать.

**⚠️ CRITICAL**: никакая user story не может стартовать, пока эти проверки не пройдены. Если grep показывает неожиданные результаты — остановиться и перечитать `research.md` §2.

- [X] T004 [P] Pre-flight grep `processColor[A-Za-z]+` в `karaoke-public/src/` и `karaoke-web/` — должно вернуть **0 результатов** (подтверждает, что публичный фронт не зависит от удаляемых полей)
- [X] T005 [P] Pre-flight grep `processColor[A-Za-z]+` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Publication.kt` — должно вернуть **≥30 результатов** (строки ~249+; подтверждает, что `Publication.kt` использует геттеры `Song`, а не DTO, и НЕ требует правок)
- [X] T006 [P] Pre-flight grep `processColor[A-Za-z]+` в `karaoke-app/src/main/resources/templates/*.html` — должно вернуть **≥60 результатов** в `publications.html`/`unpublications.html`/`songs.html`/`songs2.html`/`area_left_column.html`; подтверждает, что шаблоны получают сырой `Song` через `${song.processColorX}` (НЕ DTO) и продолжат работать после PR

---

## Phase 3: User Story 1 — Читаемая ячейка с названием песни в таблице «Публикации» (Priority: P1) 🎯 MVP

**Goal**: ячейка `PublishTableBodyTd` содержит только название песни шириной 210 px; 6 цветовых ячеек-индикаторов удалены; клик по `.publish-name` сохранён.

**Independent Test**: открыть «Публикации» в `webvue3` на dev-сервере; визуально подтвердить только `.publish-name` 210 px; в DevTools → Elements → Computed → `width: 210px`; `document.querySelectorAll('.publish-column').length === 0`.

### Implementation for User Story 1

Все задачи трогают **один файл** `webvue3/src/components/Publish/components/PublishTableBodyTd.vue` — должны выполняться последовательно.

- [X] T007 [US1] Удалить 6 блоков `<div class="publish-column">...</div>` из template в `webvue3/src/components/Publish/components/PublishTableBodyTd.vue` (строки 17–48 включительно с `processColorMeltLyrics`, `processColorSponsr`, `processColorDzenLyrics`, `processColorVkLyrics`, `processColorPlLyrics`, `processColorTelegramLyrics` и парными `-Karaoke` ячейками)
- [X] T008 [US1] Обновить CSS в `<style scoped>` блоке файла `webvue3/src/components/Publish/components/PublishTableBodyTd.vue`: `.publish` `min-width: 200px` → `min-width: 210px`, `max-width: 200px` → `max-width: 210px` (строки 183–184); `.publish-name` `width: 150px` → `width: 210px` (строка 192)
- [X] T009 [US1] Удалить 20 computed-свойств `processColor*` (от `processColorBoosty` до `processColorPlChords`) из блока `computed:` в `webvue3/src/components/Publish/components/PublishTableBodyTd.vue` (строки 97–153 включительно)
- [X] T010 [US1] Удалить 3 метода `dblClickKaraoke`, `dblClickLyrics`, `dblClickChords` из блока `methods:` в `webvue3/src/components/Publish/components/PublishTableBodyTd.vue` (строки 163–171)
- [X] T011 [US1] Удалить 3 CSS-правила `.publish-column`, `.publish-column-cell-top`, `.publish-column-cell-bottom` из `<style scoped>` блока файла `webvue3/src/components/Publish/components/PublishTableBodyTd.vue` (строки 207–232 включительно)
- [ ] T012 [US1] Ручная визуальная валидация US1: запустить `cd /home/nsa/Karaoke/webvue3 && npm run dev`, открыть раздел «Публикации», через DevTools → Elements подтвердить: `width` элемента `.publish-name` = `210px`; `document.querySelectorAll('.publish-column').length === 0`; hover на названии даёт `color: red; cursor: pointer`; клик по названию открывает `SongEditModal`

---

## Phase 4: User Story 2 — Корректная отрисовка пустой ячейки (Priority: P2)

**Goal**: плейсхолдер `.empty` (для строк без `publish`) имеет ширину 210 px и визуально совпадает с заполненными ячейками.

**Independent Test**: в таблице «Публикации» найти строку с пустой ячейкой; визуально подтвердить, что `.empty` имеет ту же ширину, что и `.publish-name`; в DevTools → Computed → `width: 210px`.

### Implementation for User Story 2

Трогает **тот же файл**, что US1 — должен идти после Phase 3.

- [X] T013 [US2] Обновить CSS-правило `.empty` в `<style scoped>` блоке файла `webvue3/src/components/Publish/components/PublishTableBodyTd.vue`: `width: 200px` → `width: 210px` (строка 235)
- [ ] T014 [US2] Ручная визуальная валидация US2: на dev-сервере в таблице «Публикации» найти строку с пустой ячейкой; в DevTools → Elements подтвердить, что `getComputedStyle(document.querySelector('.empty')).width === '210px'`; визуально `.empty` совпадает по горизонтальным границам с соседними заполненными ячейками

---

## Phase 5: User Story 3 — Кнопки PLAY в SongEdit без раскраски (Priority: P1)

**Goal**: 4 кнопки «PLAY LYRICS / KARAOKE / CHORDS / TABS» в `SongEdit.vue` теряют inline `background-color`; фон становится одинаковым (CSS-класс `.group-button`).

**Independent Test**: открыть `SongEdit.vue` для любой песни; через DevTools → Elements подтвердить, что inline-стиль `style="background-color:..."` на 4 кнопках PLAY отсутствует.

### Implementation for User Story 3

Трогает **другой файл** — может выполняться параллельно с US1/US2.

- [X] T015 [US3] Удалить 4 атрибута `:style="{ backgroundColor: song.processColorMeltKaraoke }"`, `:style="{ backgroundColor: song.processColorMeltLyrics }"`, `:style="{ backgroundColor: song.processColorMeltChords }"`, `:style="{ backgroundColor: song.processColorMeltMelody }"` в 4 `<button>`-элементах «PLAY KARAOKE / LYRICS / CHORDS / TABS» в `webvue3/src/components/Songs/edit/SongEdit.vue` (строки 2297–2328, по одному атрибуту на кнопку)
- [ ] T016 [US3] Ручная визуальная валидация US3: открыть `SongEdit.vue` для любой песни; в DevTools → Elements выделить 4 кнопки PLAY; убедиться, что inline-стиль `style` для каждой кнопки не содержит `background-color`; фон у всех 4 кнопок визуально одинаков (CSS-класс `.group-button`)

---

## Phase 6: User Story 4 — Чистый DTO без полей, которые никто не читает (Priority: P1)

**Goal**: DTO `SongDTO` и `SongDTOdigest` содержат ровно 1 поле `processColor*` — `processColorPlayerDemo`; 27 остальных удалены; компиляция и runtime продолжают работать.

**Independent Test**: `curl /api/songsdigests | jq '.[0] | keys | map(select(startswith("processColor")))'` возвращает массив из одного элемента `["processColorPlayerDemo"]`; в `SongsTable.vue` бейдж `DE` (`flagPlayerDemo`) показывает цвет из `processColorPlayerDemo`.

### Implementation for User Story 4

Логически зависит от US1+US3 (DTO-чистка не должна ломать живые ссылки). Трогает 3 разных backend-файла.

- [X] T017 [P] [US4] Удалить 27 полей `processColor*` (от `processColorBoosty` до `processColorMaxMelody`, **кроме `processColorPlayerDemo`**) из data class `SongDTOdigest` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SongDTOdigest.kt` (строки 61–88 включительно, оставить только строку 68 `val processColorPlayerDemo: String,`)
- [X] T018 [P] [US4] Удалить 27 полей `processColor*` (от `processColorBoosty` до `processColorMaxMelody`, **кроме `processColorPlayerDemo`**) из data class `SongDTO` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SongDTO.kt` (строки 68–95 включительно, оставить только строку 75 `val processColorPlayerDemo: String,`); **зависит от T017 неявно** (порядок полей в `toDtoDigest()` должен соответствовать порядку в `SongDTOdigest`)
- [X] T019 [US4] Удалить 27 присваиваний `processColorX = processColorX,` (от `processColorBoosty = processColorBoosty,` до `processColorMaxMelody = processColorMaxMelody,`, **кроме `processColorPlayerDemo = processColorPlayerDemo,`**) в методе `SongDTO.toDtoDigest()` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SongDTO.kt` (строки 353–380 включительно, оставить только строку 360); **зависит от T017** (после удаления полей в `SongDTOdigest` эти присваивания не компилируются)
- [X] T020 [US4] Удалить 27 присваиваний `processColorX = processColorX,` (от `processColorBoosty = processColorBoosty,` до `processColorMaxMelody = processColorMaxMelody,`, **кроме `processColorPlayerDemo = processColorPlayerDemo,`**) в методе `Song.toDTO()` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt` (строки 8232–8259 включительно, оставить только строку 8239); **зависит от T018** (после удаления полей в `SongDTO` эти присваивания не компилируются)
- [X] T021 [US4] Проверить компиляцию: `cd /home/nsa/Karaoke && ./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin` — должно пройти без ошибок; если есть ошибки — перечитать `data-model.md` §2.3 (соответствие порядка полей) и `research.md` §3.1; **зависит от T019 и T020**
- [ ] T022 [US4] Ручная JSON-валидация: запустить backend dev-сервер, выполнить `curl -s -b "JSESSIONID=..." "http://localhost:8080/api/songsdigests" | jq '.[0] | keys | map(select(startswith("processColor")))'` — ожидается `["processColorPlayerDemo"]`; проверить, что бейдж `DE` в `SongsTable.vue` показывает цвет из `processColorPlayerDemo`

---

## Phase 7: Polish & Cross-Cutting Concerns

**Цель**: убедиться, что PR не сломал baseline, KDoc/JSDoc покрытие, и документация обновлена.

- [X] T023 [P] Запустить `cd /home/nsa/Karaoke/webvue3 && npm run lint:check` — должно пройти без новых нарушений ESLint
- [X] T024 [P] Запустить `cd /home/nsa/Karaoke && ./gradlew ktlintCheck` — должно пройти без новых нарушений ktlint
- [X] T025 [P] Запустить `cd /home/nsa/Karaoke && bash tools/check-eslint-baseline.sh webvue3` — должно показать, что baseline не вырос (новых нарушений 0); если есть — добавить в `webvue3/.eslint-baseline.json` в том же PR (FR-007 constitution)
- [X] T026 [P] Запустить `cd /home/nsa/Karaoke && bash tools/check-kdoc-coverage.sh` — должно показать 100% покрытие для изменённых Kotlin-классов (`SongDTO`, `SongDTOdigest`)
- [X] T027 [P] Запустить `cd /home/nsa/Karaoke && bash tools/check-jsdoc-coverage.sh webvue3` — должно показать 100% покрытие для `export default PublishTableBodyTd` (JSDoc-блок сохранён на строках 57–61)
- [X] T028 [P] Запустить `cd /home/nsa/Karaoke && pre-commit run --all-files` — все 7 проверок зелёные (ktlint, ESLint webvue3, ESLint karaoke-public, Docs, Baseline, KDoc, JSDoc); **зависит от T023–T027**
- [X] T029 [P] Обновить `docs/features/songs-table.md` (per FR-009 constitution): добавить запись о PR #160: «после визуального удаления в #156 DTO тоже почищен от неиспользуемых `processColor*` полей (`SongDTO` + `SongDTOdigest`); осталось ровно одно поле — `processColorPlayerDemo` (бейдж `DE`); раскраска PLAY в `SongEdit.vue` тоже снята (FR-016); геттеры в `Song.kt` и diff-логика сохранены»
- [X] T030 [P] Обновить `docs/architecture-notes.md`: добавить запись Pass 35 с описанием PR #160 (ссылка на спеку `specs/160-publish-body-td-remove-six-columns`, эффект — ~5 МБ экономии на `/api/songsdigests`, чистка 27 полей)
- [ ] T031 [P] Запустить полную ручную валидацию по [quickstart.md](./quickstart.md): 5 шагов end-to-end (визуальная проверка таблицы «Публикации», PLAY-кнопки, JSON `/api/songsdigests`, бейдж `DE`, линтеры); **зависит от T023–T030**

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: без зависимостей — стартует немедленно.
- **Foundational (Phase 2)**: зависит от Setup — **БЛОКИРУЕТ** все user stories.
- **User Stories (Phase 3–6)**:
  - **US1 (P1)**: зависит от Foundational; не зависит от других story.
  - **US2 (P2)**: зависит от Foundational **и US1** (тот же файл `PublishTableBodyTd.vue`).
  - **US3 (P1)**: зависит от Foundational; **может идти параллельно с US1/US2** (другой файл `SongEdit.vue`).
  - **US4 (P1)**: зависит от Foundational **и US1+US3** (логически: DTO-чистка должна идти после UI-чистки, чтобы не осталось ссылок на удалённые поля).
- **Polish (Phase 7)**: зависит от всех US, которые были завершены (US1+US2+US3+US4).

### User Story Dependencies

```
US1 → US2    (тот же файл PublishTableBodyTd.vue, последовательно)
US1, US3 → US4  (US4 чистит DTO, на которое больше нет ссылок во фронте после US1+US3)
US3 ║ US1, US2  (параллельно — разные файлы)
```

### Within Each User Story

- В US1 (T007–T012): все задачи трогают `PublishTableBodyTd.vue` → **строго последовательно**.
- В US4 (T017–T022):
  - **T017 и T018** параллельны (разные файлы `SongDTOdigest.kt` и `SongDTO.kt`).
  - **T019** зависит от T017 (после удаления полей в `SongDTOdigest`).
  - **T020** зависит от T018 (после удаления полей в `SongDTO`).
  - **T021** зависит от T019+T020.
  - **T022** зависит от T021.

### Parallel Opportunities

- **Setup**: T002 и T003 параллельны.
- **Foundational**: T004, T005, T006 — все параллельны (только grep-аудит, read-only).
- **User Stories**: US3 параллельно с US1/US2 (если 2 разработчика: один — frontend US1+US2+US3, другой — backend US4 после UI готов).
- **Polish**: T023–T027 — все параллельны; T028 зависит от T023–T027; T029, T030 параллельны; T031 — последняя.

---

## Parallel Execution Examples

### Example 1: Foundational — все pre-flight аудиты параллельно

```bash
# Запустить в одном batch (3 параллельных grep):
grep -rEn 'processColor[A-Za-z]+' /home/nsa/Karaoke/karaoke-public/src /home/nsa/Karaoke/karaoke-web
grep -rEn 'processColor[A-Za-z]+' /home/nsa/Karaoke/karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Publication.kt
grep -rEn 'processColor[A-Za-z]+' /home/nsa/Karaoke/karaoke-app/src/main/resources/templates/*.html
```

### Example 2: User Story 4 — параллельная чистка DTO

```bash
# Параллельно (разные файлы):
Edit: T017 [P] [US4] SongDTOdigest.kt (27 полей)
Edit: T018 [P] [US4] SongDTO.kt (27 полей)

# После завершения T017 + T018 (последовательно):
Edit: T019 [US4] SongDTO.kt:toDtoDigest() (27 присваиваний, зависит от T017)
Edit: T020 [US4] Song.kt:toDTO() (27 присваиваний, зависит от T018)

# Проверка:
./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin
```

### Example 3: User Stories 1+2 vs 3 параллельно

```bash
# Разработчик A (frontend US1+US2):
Tasks: T007–T014 (PublishTableBodyTd.vue)

# Разработчик B (frontend US3) параллельно:
Tasks: T015–T016 (SongEdit.vue)

# Разработчик C (backend US4) после A+B:
Tasks: T017–T022 (SongDTO, SongDTOdigest, Song.kt)
```

---

## Implementation Strategy

### MVP First (User Story 1 + 3)

1. Завершить Phase 1: Setup (T001–T003).
2. Завершить Phase 2: Foundational (T004–T006) — **CRITICAL**, блокирует все US.
3. Завершить Phase 3: US1 (T007–T012) + Phase 5: US3 (T015–T016) параллельно.
4. **STOP и VALIDATE**: проверить US1 + US3 независимо на dev-сервере.
5. Готово к деплою MVP — визуальная часть работает, DTO ещё не тронут.

### Incremental Delivery

1. Setup + Foundational → готов фундамент.
2. US1 + US2 (последовательно) + US3 (параллельно) → frontend MVP (визуальная чистка).
3. US4 → backend MVP (DTO-чистка + JSON-оптимизация).
4. Polish → CI-gate, документация, финальная валидация.

### Parallel Team Strategy

С 2–3 разработчиками:

1. Все вместе: Phase 1 (Setup) + Phase 2 (Foundational).
2. После Foundational:
   - Разработчик A: Phase 3 (US1) → Phase 4 (US2).
   - Разработчик B: Phase 5 (US3) параллельно с A.
   - Разработчик C ждёт A+B, затем делает Phase 6 (US4).
3. Все вместе: Phase 7 (Polish — линтеры, документация, валидация).

---

## Notes

- [P] задачи = разные файлы, нет зависимостей (можно запускать параллельно).
- [Story] метка привязывает задачу к user story для traceability (US1, US2, US3, US4).
- Каждая user story должна быть завершаема и валидируема независимо (см. «Independent Test» в каждой фазе).
- **Тесты не пишутся** (явно не запрошены в спеке). Валидация — ручная по [quickstart.md](./quickstart.md) + линтеры/CI-gate.
- Коммит — после каждой задачи или логической группы (Phase 3 одной группой, Phase 4 отдельно, Phase 5 отдельно, Phase 6 одной группой, Phase 7 — коммит docs).
- **Push/PR — за пользователем** (см. AGENTS.md → «CI-gate для master»). После завершения всех задач пользователь: `git push -u origin 160-publish-body-td-remove-six-columns`, `gh pr create --base master`, дождаться CI 7/7 SUCCESS, `gh pr merge --merge` (без `--delete-branch`).
- Избегать: расплывчатых задач, конфликтов в одном файле, cross-story зависимостей, ломающих независимость story.
