---

description: "Tasks: пустые голоса песни удаляются при сохранении"
---

# Tasks: Пустые голоса песни удаляются при сохранении

**Input**: Design documents from `/specs/547-drop-empty-song-voices/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md),
[data-model.md](data-model.md), [contracts/](contracts/), [quickstart.md](quickstart.md)

**Tests**: юнит-тесты чистой функции обязательны — они и есть проверка FR-001…FR-006/FR-009/
FR-012 (без БД), см. [contracts/voice-normalization.md](contracts/voice-normalization.md#4-примеры-обязательные-тест-кейсы).
Интеграционные тесты проекта в основном `@Disabled` и не используются (конституция § «Тесты»).
Фронтенд-тестов в `webvue3` нет (только Playwright e2e, требует поднятого стенда) — проверка US1/US4
ручная по quickstart.

**Organization**: задачи сгруппированы по пользовательским историям — каждая история
самостоятельно проверяема.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: можно выполнять параллельно (другой файл, нет зависимостей)
- **[Story]**: US1…US4 — к какой истории относится задача
- Пути файлов указаны относительно корня репозитория `/home/nsa/Karaoke`

## Path Conventions

- Backend: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/…`
- Backend-тесты: `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/…`
- Админ-SPA: `webvue3/src/components/Songs/…`
- Документация: `docs/features/…`, `knowledge/domains/catalog/components/…`

---

## Phase 1: Setup

**Purpose**: подготовка артефактов, которые нужны всем историям

- [X] T001 [P] Создать заготовку per-feature документа `docs/features/drop-empty-song-voices.md`:
  назначение, правило «пустой голос удаляется при сохранении», ссылки на spec/plan, пустые секции
  «Контракт», «До/после», «FR-таблица» (конституция Principle VI / FR-009)
- [X] T002 [P] Снять базовый срез данных для приёмки (SQL из [quickstart.md](quickstart.md) §3 —
  песня 15696 и список песен с пустым не-первым голосом) и записать его в
  `docs/features/drop-empty-song-voices.md` § «До/после»

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: чистая нормализация и хук сохранения — без них не работает ни одна история

**⚠️ CRITICAL**: задачи US1–US4 не начинаются, пока фаза не завершена

**Principle VI (MUST)**: каждый новый публичный класс/функция из T003–T009 оформляется с KDoc и
`@see docs/features/drop-empty-song-voices.md` (конституция, FR-006; CI-гейт
`tools/check-kdoc-coverage.sh --strict`).

- [X] T003 Создать `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/VoiceNormalization.kt`:
  `data class VoiceNormalization` (`texts`, `markers`, `removedIndices`, `changed`, `mapVoice`,
  `count`) — контракт [contracts/voice-normalization.md](contracts/voice-normalization.md) §1–3;
  KDoc класса + `@see docs/features/drop-empty-song-voices.md`
- [X] T004 В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/VoiceNormalization.kt`
  реализовать чистые парсеры `parseVoiceTexts` / `parseVoiceMarkers` (легаси-плоский массив → один
  голос, битый JSON → `null`, без сайд-эффектов — research R2)
- [X] T005 В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/VoiceNormalization.kt`
  реализовать `normalizeVoices(texts, markers)` + хелпер `canNormalize(hasMarkersField, hasTextField)`
  (гард research R3) и `nonEmptyVoiceIndices(markers)` (для US3): правила I1–I6 из
  [data-model.md](data-model.md#3-результат-нормализации)
- [X] T006 Создать `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/model/VoiceNormalizationTest.kt`:
  12+ кейсов из [contracts/voice-normalization.md](contracts/voice-normalization.md#4-примеры-обязательные-тест-кейсы)
  + кейсы `canNormalize` (поля отсутствуют → нормализация запрещена) и `nonEmptyVoiceIndices`
- [X] T007 Добавить в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt` метод
  `setSourceTextAndMarkers(voice, text, markers)`: меняет оба списка в памяти и делает **один**
  `saveToDbLocked()` (research R4 — иначе удалённый пустой голос возвращается через `setSourceText`);
  KDoc метода + `@see docs/features/drop-empty-song-voices.md`
- [X] T008 Добавить в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt` метод
  `setAllVoices(texts, markers)`: заменяет оба списка целиком одним сохранением (нужен для апрува
  черновика, US2); KDoc метода + `@see docs/features/drop-empty-song-voices.md`
- [X] T009 Добавить в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt` хук
  нормализации в начале `saveToDb()`: гард `canNormalize` по `fields[SongField.SOURCE_MARKERS]`/
  `SOURCE_TEXT` (research R3), перезапись полей, пересчёт `result_text`/`formatted_text_*` только
  при фактическом удалении (research R6), WARN в лог с id песни и удалёнными индексами, сохранение
  результата в транзиентное поле `lastVoiceNormalization` для API (research R5); KDoc на хук
  (приватный — краткий комментарий «почему») + `@see docs/features/drop-empty-song-voices.md`

**Checkpoint**: нормализация покрыта юнит-тестами, сохранение песни чистит пустые голоса

---

## Phase 3: User Story 1 — Пустой голос не сохраняется (Priority: P1) 🎯 MVP

**Goal**: сохранение голоса из редактора разметки админки не оставляет пустой голос; редактор
после сохранения остаётся на реальном голосе.

**Independent Test**: открыть песню с одним голосом (15696), выбрать «Добавить», ничего не
вводить, нажать Save → в БД один голос, текст и 621 маркер первого не изменились, редактор
переключился на голос 1.

### Implementation for User Story 1

- [X] T010 [US1] Переписать `saveSourceTextAndMarkers` в
  `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt`: один вызов
  `song.setSourceTextAndMarkers(...)` вместо трёх сохранений, ответ
  `{"ok":true,"voice":<индекс>,"countVoices":<N>}` / `voice=-1` при удалении, ошибки
  `song_not_found` / `bad_markers` / `bad_voice` — контракт
  [contracts/save-voice-endpoint.md](contracts/save-voice-endpoint.md)
- [X] T011 [P] [US1] Обновить `webvue3/src/components/Songs/store.js` (действие
  `saveSourceTextAndMarkers`): разобрать JSON-ответ и вернуть `{ok, voice, countVoices}`
  (сейчас возвращается сырое тело `true`)
- [X] T012 [US1] Обновить `webvue3/src/components/Songs/edit/SubsEdit.vue`: после сохранения
  применить ответ — `voice >= 0` → `currentVoice = voice`; `voice == -1` → перейти на последний
  реальный голос (`countVoices - 1`); привести длину `dataVoices` к `countVoices`
- [X] T013 [US1] Прогнать сценарий US1 по [quickstart.md](quickstart.md) §4 (сохранение пустого
  второго голоса) и записать результат (запрос/ответ/SQL до и после) в
  `docs/features/drop-empty-song-voices.md` § «До/после» (требует согласия владельца на
  пересборку `karaoke-app` — см. Notes)

**Checkpoint**: US1 работает и проверена на песне 15696

---

## Phase 4: User Story 2 — Любое сохранение песни чистит пустые голоса (Priority: P1)

**Goal**: уже сохранённый пустой голос исчезает при любом следующем сохранении (голос, карточка,
апрув черновика) без потери правок.

**Independent Test**: у 15696 изменить название в карточке и сохранить → пустого голоса нет,
название применено; отдельно — апрув черновика онлайн-редактора не искажает присланную разметку.

### Implementation for User Story 2

- [X] T014 [US2] Перевести легаси-эндпоинты `saveSourceMarkers` в
  `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/MainController.kt` на
  `song.setSourceTextAndMarkers(...)` (текст берётся текущий), контракт (вью `text`) не менять
- [X] T015 [P] [US2] Перевести `saveSourceMarkers`/`saveSourceText` в
  `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt` на тот же
  комбинированный сеттер, чтобы текст и маркеры одного голоса писались одним сохранением
- [X] T016 [US2] Переписать применение черновика в
  `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/SongEditorController.kt`
  (`approve`): собрать итоговые `markersPerVoice`/`textsPerVoice`, применить одним вызовом
  `song.setAllVoices(...)`, затем `song.truncateVoicesTo(...)` — устранить сдвиг голосов
  нормализацией внутри цикла `setSourceMarkers` (дефект из research R4)
- [ ] T017 [US2] Проверить апрув на черновике `[v1, пусто, v3]`: разметка применяется полностью
  (голосов 2, второй = `v3`), пустого среднего нет; результат зафиксировать в
  `docs/features/drop-empty-song-voices.md` § «Апрув черновика»
- [X] T018 [US2] Прогнать сценарии US2 по [quickstart.md](quickstart.md) §4 (карточка, голос,
  апрув) и зафиксировать SQL-проверку «0 пустых голосов среди пересохранённых» в
  `docs/features/drop-empty-song-voices.md` (требует согласия владельца на пересборку
  `karaoke-app` — см. Notes)

**Checkpoint**: US1 и US2 работают; данные 15696 чистятся обычным сохранением

---

## Phase 5: User Story 3 — Пустой голос в середине не «съедает» следующие (Priority: P2)

**Goal**: при пустом среднем голосе реальные голоса после него не теряются — и после сохранения,
и при подготовке рендера для ещё не пересохранённых песен.

**Independent Test**: песня [A, пусто, B] → после сохранения [A, B]; подготовка рендера видит
оба реальных голоса.

### Implementation for User Story 3

- [X] T019 [US3] Исправить `getVoices` в
  `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Functions.kt`: индексировать
  `sourceMarkersList` по списку непустых голосов (`nonEmptyVoiceIndices`), а не по позиции —
  для песен, которые ещё не пересохранены
- [X] T020 [P] [US3] Дополнить `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/model/VoiceNormalizationTest.kt`
  кейсом `nonEmptyVoiceIndices`: `[[M],[],[M2]] → [0,2]`
- [ ] T021 [US3] Прогнать сценарий US3 по [quickstart.md](quickstart.md) §5 (копия песни с
  пустым средним голосом) и записать результат в `docs/features/drop-empty-song-voices.md`
  (требует согласия владельца на пересборку `karaoke-app` — см. Notes)

**Checkpoint**: US3 работает независимо от US1/US2 (даже без пересохранения песни)

---

## Phase 6: User Story 4 — Редактор не «воскрешает» голос и не дублирует правки (Priority: P2)

**Goal**: после сдвига нумерации повторные сохранения попадают в тот же голос; список голосов
в редакторе соответствует данным песни.

**Independent Test**: при пустом втором голосе открыть третий, нажать Save три раза подряд —
голосов два, все маркеры в голосе 2, дубликатов нет.

### Implementation for User Story 4

- [X] T022 [US4] Довести `webvue3/src/components/Songs/edit/SubsEdit.vue`: при сохранении с
  удалением/сдвигом обновлять `dataVoices` (усечение) и `currentVoice` **до** следующего Save;
  не создавать плейсхолдер «Добавить», если он указывает на удалённый голос
- [X] T023 [P] [US4] Довести `webvue3/src/components/Songs/store.js`: при расхождении
  `countVoices` из ответа с локальным списком — перечитать `/api/song/voices` и вернуть
  актуальный список вызывающему
- [X] T024 [US4] Прогнать сценарий US4 по [quickstart.md](quickstart.md) §6 (10 сохранений
  подряд) и записать результат в `docs/features/drop-empty-song-voices.md` (требует согласия
  владельца на пересборку `karaoke-app` — см. Notes)

**Checkpoint**: все четыре истории работают независимо

---

## Phase 7: Polish & Cross-Cutting Concerns

- [X] T025 [P] Обновить `knowledge/domains/catalog/components/song-entity.md`: правило
  нормализации голосов при сохранении (что удаляется, что пересчитывается, fail-safe) — research R8
- [X] T026 [P] Добавить кросс-ссылку в `knowledge/domains/catalog/components/dictionaries.md`
  на определение «пустой голос» (требование `.ssot-map.yml` для изменений `Song.kt`)
- [X] T027 [P] Дописать `docs/features/drop-empty-song-voices.md`: контракт эндпоинта, FR-таблица,
  итоги проверок (per-feature document, FR-009 конституции)
- [X] T028 Прогнать линтеры и сборку изменённых модулей (`webvue3/package.json` → `lint`,
  `format:check`, `build`; ktlint для `karaoke-app`): `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle
  ./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel` и
  `:karaoke-app:ktlintCheck`, затем `cd webvue3 && npm run lint && npm run format:check && npm run build`
- [ ] T029 Прогнать [quickstart.md](quickstart.md) §1–3, §8–9 (юнит-тесты, SQL, идемпотентность,
  SSOT-гейт) + процедуры замеров §10–11 (SC-002/SC-005/SC-007); шаги §4–6, требующие
  пересборки `karaoke-app`, — после согласия владельца (T013/T018/T021/T024)
- [ ] T030 Прогнать регрессионный набор [quickstart.md](quickstart.md) §12 (SC-008): сценарии
  спек `544`, `546`, `299`, `357` в части сохранения песни — 0 регрессий, результат записать в
  `docs/features/drop-empty-song-voices.md` § «Регрессии»
- [X] T031 Проверить неизменность заданий и черновиков (FR-015): снять снимок
  `tbl_song_assignments` и `tbl_song_assignment_drafts` до и после сохранения песни с
  нормализацией (`specs/547-drop-empty-song-voices/quickstart.md` §13), отличий — 0

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: T001 и T002 параллельны, зависимостей нет
- **Foundational (Phase 2)**: зависит от Setup; **блокирует US1–US4**
  - T003 → T004 → T005 → T006 (файл и тесты)
  - T007 → T008 → T009 (все в `Song.kt`, последовательно)
  - T009 зависит от T005 (использует `canNormalize`)
- **User Stories (Phase 3–6)**: после Phase 2
  - US1: T010 → T011 → T012 → T013
  - US2: T014 и T015 параллельны; T016 → T017; T018 после T014–T016
  - US3: T019 → T020; T021 после T019
  - US4: T022 → T023 (разные файлы, могут идти параллельно после T012) → T024
- **Polish (Phase 7)**: после нужных историй; T025–T027 параллельны, T028 → T029 → T030;
  T031 — после T004/T009 (нужна нормализация) и может идти параллельно T029

### User Story Dependencies

- **US1 (P1)**: только Phase 2. Даёт MVP.
- **US2 (P1)**: только Phase 2; использует методы `Song`, добавленные в Phase 2 (T007/T008), от US1
  не зависит. Пересечение с US1 — только файл `ApiController.kt` (разные функции).
- **US3 (P2)**: только Phase 2 (хелпер `nonEmptyVoiceIndices`); от US1/US2 не зависит.
- **US4 (P2)**: зависит от контракта ответа US1 (T010/T011/T012) — без него нечего применять на
  клиенте; поэтому выполняется после US1 (или в той же ветке сразу за ней).

### Within Each User Story

- Модель/чистая функция → хук сохранения → эндпоинт → клиент → ручная проверка
- Ручные проверки (T013, T018, T021, T024) выполняются только после соответствующей реализации

### Parallel Opportunities

- T001 ∥ T002 (разные файлы)
- T014 ∥ T015 (разные контроллеры) — после Phase 2
- T019 ∥ T022 (backend `Functions.kt` ∥ frontend `SubsEdit.vue`) — после Phase 2 (T022 требует T012)
- T025 ∥ T026 ∥ T027 (разные файлы документации)
- T020 ∥ T022 (тест ∥ UI)

---

## Parallel Example: User Story 2

```bash
# Разные контроллеры, оба после Phase 2:
Task: "T014 [US2] MainController.kt — комбинированный сеттер в легаси-эндпоинтах"
Task: "T015 [US2] ApiController.kt — комбинированный сеттер в /song/savesourcemarkers и /song/savesourcetext"
```

## Parallel Example: Polish

```bash
Task: "T025 [P] knowledge/domains/catalog/components/song-entity.md"
Task: "T026 [P] knowledge/domains/catalog/components/dictionaries.md"
Task: "T027 [P] docs/features/drop-empty-song-voices.md"
```

---

## Implementation Strategy

### MVP First (User Story 1 only)

1. Phase 1 (T001–T002) — артефакты и базовый срез
2. Phase 2 (T003–T009) — нормализация + хук сохранения + юнит-тесты
3. Phase 3 (T010–T013) — эндпоинт + клиент + проверка на 15696
4. **STOP и проверка**: US1 независимо (quickstart §4) — это уже закрывает источник дефекта
5. Дальше — US2 (чистка уже испорченных песен), US3, US4

### Incremental Delivery

1. Phase 2 → Phase 3: пустые голоса перестают появляться (MVP)
2. Phase 4: существующие песни чистятся при следующем сохранении
3. Phase 5: рендер перестаёт терять голоса после пустого (в т. ч. до пересохранения)
4. Phase 6: редактор остаётся согласованным после сдвига нумерации
5. Phase 7: документация, knowledge, линтеры, SSOT-гейт

### Parallel Team Strategy

Работа в одной ветке `547-drop-empty-song-voices` (конституция IX.3 требует отдельных worktree
только для параллельных PR-веток). Если задач больше одной сессии: backend (T003–T010, T014–T016,
T019) и frontend (T011–T012, T022–T023) можно вести параллельно после Phase 2 — файлы не
пересекаются.

---

## Notes

- [P] = разные файлы и нет незавершённых зависимостей
- Перезапуск `karaoke-app` для сквозной проверки — **только с согласия владельца** и после
  остановки очереди (AGENTS.md, Pass 380/381); шаги T013/T018/T021/T024/T030/T031 можно
  выполнить на пересобранном контейнере либо отложить до согласия, зафиксировав это в отчёте
- Проверки без рестарта доступны всегда: юнит-тесты (T006, T020), линтеры/сборка (T028),
  SQL-снимки и SSOT-гейт (T029), процедуры замеров §10–11 и снимки заданий §13
- Каждая ручная проверка фиксируется в `docs/features/drop-empty-song-voices.md` (артефакт, а не
  «на словах»)
- Коммиты: `area: краткое описание` на русском; ветка — feature, не master (AGENTS.md)

## Статус выполнения (2026-10-20)

**Сделано (27 задач)**: T001–T016, T018–T020, T022–T028, T031.

Проверено локально, без пересборки `karaoke-app`:

- `:karaoke-app:compileKotlin`, `:karaoke-web:compileKotlin`, `:karaoke-app:ktlintCheck`,
  `:karaoke-web:ktlintCheck` — BUILD SUCCESSFUL;
- `VoiceNormalizationTest` — 20 тестов, 0 падений, 0 пропусков;
- `webvue3`: `npm run build` ✓, ESLint и Prettier по изменённым файлам ✓;
- `tools/lint-knowledge.py` — без нарушений; `tools/check-knowledge-cross-links.sh` — 678/678
  ссылок валидны; `tools/check-feature-doc.sh` — документ валиден.

Проверено на живом `karaoke-app` (после пересборки владельцем, 2026-10-20):

- **US1/US2/T013, T018**: песня 15696 (2 голоса, второй пустой) → 1 голос, 621 маркер, текст
  первого голоса побайтово равен отправленному; сохранение карточки голосов не добавляет.
- **Edge**: песня 2278 (текст без маркеров) после сохранения — по-прежнему 2 голоса.
- **US4/T024**: 10 сохранений голоса 0 подряд — 1 голос, маркеры и `result_text` без изменений.
- **T030 (частично)**: 544 — `.srt` не создаются (0 файлов в папке песни); 299 — параллельная
  правка карточки (`key=Am`) и сохранение голоса с зазором 50 мс: обе правки в БД.
- **T031**: отпечатки `tbl_song_assignments` / `tbl_song_assignment_drafts` до и после — идентичны.
- **Латентность**: карточка медиана 9.3 мс / p90 11.0 мс; голос с 60 КБ маркеров 30.0 / 41.8 мс.

**Осталось (4 задачи), `[ ]`**:

- **T017** (апрув черновика с пустым средним голосом) — апрув реального задания поднимает
  `id_status` до 6 и запускает публикацию; нужен отдельный стенд/согласие владельца.
- **T021** (сквозная проверка рендера) — US3-фикс работает на легаси-пути MLT, который в текущем
  конвейере не используется; юнит-тест `nonEmptyVoiceIndices` зелёный.
- **T029** — осталось §11 (выборка 20 песен) и §9 (перегон `check-ssot-impact.py` после коммита:
  до коммита гейт видит 0 значимых файлов).
- **T030** — остались импортные регрессии 546 и 357 (сценарии создают новые песни).

**Найдено на живой проверке и исправлено**: ответ `savesourcetextmarkers` возвращал `voice=1`
вместо `-1` при удалении голоса — выражение `mapVoice(voice) ?: voice` съедало `null`. Исправлено
на `song.lastVoiceNormalization?.let { it.mapVoice(voice) ?: -1 } ?: voice`; после пересборки
проверено живьём: удаление пустого голоса → `voice=-1`, сдвиг третьего голоса на второй →
`voice=1`, `countVoices=2`.
