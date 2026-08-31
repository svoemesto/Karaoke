# Tasks: 279 — Восстановить поиск родителя при добавлении файлов из папки

**Input**: Design documents from `/specs/279-fix-parent-search-folder-add/`
**Prerequisites**: plan.md, spec.md, research.md (H1 CONFIRMED), contracts/apply-duplicate-original.md
**Tests**: Существующие интеграционные тесты в `karaoke-app/src/test` помечены `@Disabled` (Constitution § «Тесты»). Verify — ручная на стороне пользователя по `quickstart.md` (SC-001..SC-007).

**Organization**: Все три user stories (US1/US2/US3) покрываются одним и тем же фиксом — синхронизация `newSong` с записанным состоянием в `applyDuplicateOriginal` после `songToSave.saveToDb()`. Задачи сгруппированы по user story для traceability; реализация — одна (точечный фикс).

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Подготовка к фиксу — проверить окружение и текущее состояние.

- [x] T001 Verify working tree on `279-fix-parent-search-folder-add` branch in `/home/nsa/Karaoke`
- [x] T002 Verify baseline `karaoke-app` builds before any change: `cd /home/nsa/Karaoke && ./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel`

**Checkpoint**: Окружение проверено, baseline сборка проходит. Можно приступать к фиксу.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Никаких инфраструктурных задач для этого bugfix не требуется — изменения локализованы в одном файле. Phase пустой.

---

## Phase 3: User Story 1 — Поиск родителя у того же автора при импорте из папки (Priority: P1) 🎯 MVP

**Goal**: Для только что импортированной песни с суффиксом в скобках (например, «Камнем по голове (Epic Orchestral, Cover-2)») того же автора — `root_id` указывает на базовую песню и НЕ перезатирается последующим `findAudioParentByWaveform`.

**Independent Test**: Импортировать файл `[Король и Шут] - Камнем по голове (Epic Orchestral, Cover-2).flac` при существующей в БД базовой песне «Камнем по голове» от того же автора. Через несколько секунд проверить: `SELECT root_id FROM tbl_songs WHERE song_name = 'Камнем по голове (Epic Orchestral, Cover-2)'` возвращает `id` базовой песни (а не 0).

### Implementation for User Story 1

- [x] T003 [US1] Синхронизировать `newSong` с записанным состоянием в `applyDuplicateOriginal` после `songToSave.saveToDb()` в файле `/home/nsa/Karaoke/karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt` (строки ~4528–4553). После существующего блока присваивания полей в `songToSave` и его `saveToDb()` добавить те же присваивания в `newSong` (`newSong.rootId = original.id`, `newSong.sourceText = original.sourceText`, `newSong.resultText = original.resultText`, `newSong.sourceMarkers = original.sourceMarkers`, `newSong.formattedTextSong = original.formattedTextSong`, `newSong.formattedTextTabs = original.formattedTextTabs`, `newSong.formattedTextChords = original.formattedTextChords`, `newSong.fields[SongField.ID_STATUS] = "1"`) с KDoc-комментарием, объясняющим регресс спеки 278 (см. [research.md § H1 CONFIRMED](../research.md))

**Checkpoint**: После T003 — `applyDuplicateOriginal` корректно синхронизирует `newSong` с БД; следующий `findAudioParentByWaveform` → `song.saveToDb()` не перезатирает `root_id`.

---

## Phase 4: User Story 2 — Поиск родителя НЕ пересекает авторов (Priority: P2)

**Goal**: При импорте файла автора «Кино» с названием, совпадающим с песней автора «Король и Шут» — `root_id` остаётся 0 (привязки к чужому автору не происходит).

**Independent Test**: Импортировать файл `[Кино] - Звезда по имени Солнце (Cover).flac` при наличии в БД «Король и Шут» — «Звезда по имени Солнце». Проверить: `SELECT root_id FROM tbl_songs WHERE song_name = 'Звезда по имени Солнце (Cover)'` возвращает 0 (а не id чужого автора).

### Implementation for User Story 2

- [x] T004 [US2] Verify US2 в `applyDuplicateOriginal` после T003 — фикс US1 не нарушает ограничение «только тот же автор» (зафиксировано в [specs/238](../238-import-folder-author-album-cover/)). Логика не меняется: `findDuplicateOriginal` уже использует `song_author = ?` в SQL. Verify в `/home/nsa/Karaoke/karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt:4402-4447` — SQL остаётся без изменений (только та же логика SQL `LOWER(song_author) = LOWER(?)`, фильтр `sameAuthorOnly = true` без fallback после спеки 238)

**Checkpoint**: US2 по-прежнему соблюдается после фикса US1.

---

## Phase 5: User Story 3 — Регистр автора (Priority: P3)

**Goal**: Поиск родителя работает, даже если в имени файла автор указан в другом регистре (например, «король и шут» vs «Король и Шут» в БД).

**Independent Test**: Импортировать файл `[король и шут] - Камнем по голове (Cover).flac` при наличии в БД «Король и Шут» — «Камнем по голове». Проверить: `SELECT root_id FROM tbl_songs WHERE song_name = 'Камнем по голове (Cover)'` возвращает `id` базовой песни.

### Implementation for User Story 3

- [x] T005 [US3] Verify US3 — синхронизация из T003 покрывает этот кейс без дополнительных изменений (логика сравнения автора через `LOWER()` в SQL не меняется в T003). Запустить сценарий 4 из `quickstart.md` (импорт с автором в нижнем регистре) после фикса US1 — должно работать корректно

**Checkpoint**: US3 работает через ту же логику `findDuplicateOriginal` (SQL `LOWER(song_author) = LOWER(?)`).

---

## Phase 6: Consistency — applyAudioParentMarkers

**Goal**: Применить тот же фикс (синхронизация `song` после `songToSave.saveToDb()`) к `applyAudioParentMarkers` для consistency. Эта функция также была изменена в спеке 278 аналогичным образом и потенциально может иметь тот же регресс для `audio_*` полей.

**Independent Test**: Импортировать файл, для которого `findAudioParentByWaveform` находит аудио-родителя с `idStatus >= 6` (порог 95% сходства). Проверить: `SELECT audio_parent_id, audio_similarity_percent, audio_delta_ms, id_status FROM tbl_songs WHERE ...` — поля заполнены корректно и НЕ перезатёрты.

### Implementation for Phase 6

- [x] T006 [P] Синхронизировать `song` с записанным состоянием в `applyAudioParentMarkers` после `songToSave.saveToDb()` в файле `/home/nsa/Karaoke/karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt` (строки ~4568–4595). После блока присваивания полей в `songToSave` и его `saveToDb()` добавить те же присваивания в `song` (`song.audioParentId`, `song.audioSimilarityPercent`, `song.audioDeltaMs`, `song.sourceText`, `song.resultText`, `song.sourceMarkers`, `song.formattedText*`, `song.fields[SongField.ID_STATUS]`) с KDoc-комментарием-ссылкой на спеку 279

**Checkpoint**: Обе функции `applyDuplicateOriginal` и `applyAudioParentMarkers` согласованы в подходе.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Линтеры, документация, ручная проверка.

- [x] T007 [P] Run `./gradlew :karaoke-web:ktlintCheck` in `/home/nsa/Karaoke` — должно пройти без НОВЫХ нарушений baseline (`config/ktlint/baseline-*.xml`)
- [x] T008 Build `karaoke-app` bootJar: `cd /home/nsa/Karaoke && ./gradlew :karaoke-app:bootJar --parallel`
- [x] T009 [P] Обновить `## История` в `/home/nsa/Karaoke/livedocs/features/238-import-folder-author-album-cover.md` — запись «2026-08-31: bugfix (спека 279) — после спеки 278 `applyDuplicateOriginal`/`applyAudioParentMarkers` пишут в БД через `songToSave`, но `newSong`/`song` в памяти оставался «грязным». Добавлена явная синхронизация после `songToSave.saveToDb()`.»
- [x] T010 [P] Commit изменения: `git add karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt livedocs/features/238-import-folder-author-album-cover.md && git commit -m "fix(karaoke-app): синхронизация newSong после applyDuplicateOriginal (спека 279)"`
- [x] T011 Push и создать PR: `git push -u origin 279-fix-parent-search-folder-add && gh pr create --base master`
- [ ] T012 Run SC-001..SC-007 из `/home/nsa/Karaoke/specs/279-fix-parent-search-folder-add/quickstart.md` на стороне пользователя (admin-машина) — выполняется пользователем после merge PR #393 (Constitution § «Ограничения»: агенту запрещено пересобирать/перезапускать `karaoke-app`)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: Нет зависимостей.
- **Phase 2 (Foundational)**: Пустая (не нужна для этого bugfix).
- **Phase 3 (US1)**: Зависит от Phase 1 (verify baseline). Блокирует US2, US3, Phase 6, Phase 7.
- **Phase 4 (US2)**: Зависит от Phase 3 (verify US1 не сломал логику same-author).
- **Phase 5 (US3)**: Зависит от Phase 3 (verify US1 покрывает регистр).
- **Phase 6 (Consistency)**: Зависит от Phase 3 (apply тот же паттерн).
- **Phase 7 (Polish)**: Зависит от Phase 3, 4, 5, 6 (все реализации готовы).

### User Story Dependencies

- **US1 (P1)**: Нет зависимостей от других stories. **MVP** — минимально проверяемое восстановление поиска родителей.
- **US2 (P2)**: Зависит от US1 (verify, что фикс US1 не сломал межавторскую защиту).
- **US3 (P3)**: Зависит от US1 (verify, что фикс US1 работает для разных регистров автора).
- **Phase 6 (Consistency)**: Зависит от US1 (тот же фикс-паттерн в смежной функции).

### Within Each Phase

- T001 → T002 (последовательно, verify baseline перед фиксом).
- T003 standalone (один точечный фикс).
- T004, T005 — verify после T003.
- T006 — параллельно с T003 (можно сделать до/после, не критично — это consistency в смежной функции).
- T007-T012 — последовательно: lint → build → docs → commit → PR → ручная проверка.

### Parallel Opportunities

- T003 (applyDuplicateOriginal) и T006 (applyAudioParentMarkers) — разные функции, можно делать параллельно.
- T007 (ktlintCheck) и T009 (LiveDoc update) — параллельно.
- T011 (PR) — после T010 (commit).
- T012 (ручная проверка) — после deploy на admin-машине (на стороне пользователя).

---

## Parallel Example: User Story 1 + Phase 6

```bash
# После T002 (verify baseline), запустить фиксы параллельно:
# Agent A: T003 — применить синхронизацию в applyDuplicateOriginal (Utils.kt:4528)
# Agent B: T006 — применить ту же синхронизацию в applyAudioParentMarkers (Utils.kt:4568)

# После завершения обоих:
# T007 (lint), T009 (docs update) — параллельно
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. **T001-T002**: Verify baseline.
2. **T003**: Применить точечный фикс в `applyDuplicateOriginal` (~10 строк).
3. **T008**: Build.
4. **T012 (частично, SC-001)**: Ручная проверка на admin-машине.
5. **STOP**: Если SC-001 прошёл — MVP готов, можно создавать PR.

### Incremental Delivery

1. **T001-T003**: Базовый фикс (US1) — минимальное восстановление поиска родителей.
2. **T004-T005**: Verify US2, US3 (не сломалось ли что-то ещё).
3. **T006**: Apply к `applyAudioParentMarkers` (consistency).
4. **T007-T011**: Линтеры, документация, commit, PR.
5. **T012**: Полная ручная проверка SC-001..SC-007 после deploy на prod.

### Risk Mitigation

- **R1** (из plan.md): Если T003 нарушает существующий race condition protection (спека 278) — откатить, пересмотреть подход. Но фикс только ДОБАВЛЯЕТ присваивания, не убирает reload-from-db-before-save → защита сохраняется.
- **R2**: Если T006 ломает какой-то существующий кейс — откатить, но поскольку это зеркало T003, риск минимален.

---

## Notes

- **T003 — единственный обязательный таск для MVP.** Все остальные — verify (T004, T005) или consistency (T006) или polish (T007-T012).
- **T003 не имеет [P]** потому что относится к конкретному месту в `Utils.kt` и должен выполняться последовательно с verify (T004, T005) и consistency (T006) — чтобы не было конфликтов в одном файле.
- **Тесты не генерируются** — Constitution § «Тесты» явно указывает, что существующие тесты `@Disabled`, проверка — ручная (quickstart.md).
- **KDoc-комментарии обязательны** — Constitution § VI (FR-006) — публичные API должны сопровождаться KDoc со ссылкой на per-feature документ. T003 и T006 — изменяют публичные функции `applyDuplicateOriginal` и `applyAudioParentMarkers`, поэтому KDoc обязателен.
- **Single commit (T010)** объединяет все изменения кода и документации — атомарный фикс легко откатить.
- **PR (T011)** создаётся через `gh pr create --base master` (Constitution § «CI-gate для master»: прямые коммиты в master ЗАПРЕЩЕНЫ).
