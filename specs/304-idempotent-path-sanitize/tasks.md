---
description: "Task list for идемпотентная санитиризация путей и имён файлов и папок (304-idempotent-path-sanitize)"
---

# Tasks: Идемпотентная санитиризация путей и имён файлов и папок

**Input**: Design documents from `/specs/304-idempotent-path-sanitize/`
**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅, contracts/sanitizer-contract.md ✅, quickstart.md ✅

**Tests**: запрошены явно (FR-009 спеки + FR-014 side-effect идемпотентность). Тесты пишутся **перед** реализацией в каждой user-story фазе.

**Organization**: задачи сгруппированы по user story (3 истории, все P1). US-1 — MVP, она доказывает базовый фикс (`!` в имени папки). US-2 — расширенное покрытие символов. US-3 — обратная совместимость с прод-данными.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: можно запускать параллельно (разные файлы, нет зависимостей на in-progress задачи).
- **[Story]**: к какой user story относится задача (US1, US2, US3).
- Каждая задача содержит точный file path.

## Path Conventions

- **Single project**: Kotlin extension-functions в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/`.
- **Tests**: `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/`.
- **Per-feature doc**: `docs/features/idempotent-path-sanitize.md`.
- **Governance**: `AGENTS.md`, `.specify/memory/constitution.md`, `config/ktlint/baseline-karaoke-app.xml`.

---

## Phase 1: Setup (Environment Verification)

**Purpose**: проверить, что окружение готово к реализации (JDK 17, slf4j, Logback, JUnit 5 уже в classpath; `Extentions.kt` собирается без изменений).

- [ ] T001 Запустить `./gradlew tasks --all` для проверки, что Gradle wrapper работает с JDK 17 в karaoke-app: `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:tasks --all`
- [ ] T002 Запустить `./gradlew :karaoke-app:compileKotlin` для проверки, что текущий `Extentions.kt` собирается без изменений: `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:compileKotlin --parallel`
- [ ] T003 [P] Проверить наличие `org.slf4j.LoggerFactory` и `ch.qos.logback.core.read.ListAppender` в classpath: `cd /home/nsa/Karaoke && GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:dependencies --configuration testRuntimeClasspath | grep -E 'slf4j|logback'`

**Checkpoint**: окружение готово — slf4j и Logback доступны, существующий код собирается.

---

## Phase 2: Foundational (Core Implementation)

**Purpose**: создать единое идемпотентное ядро `SanitizePath` с двумя extension-functions и тонкие алиасы-обёртки в `Extentions.kt`. **Блокирует** все user stories (без ядра нельзя писать тесты).

**⚠️ CRITICAL**: user stories не могут начаться, пока эта фаза не завершена.

- [ ] T004 Создать файл `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/SanitizePath.kt` со скелетом: package, imports (`org.slf4j.Logger`, `org.slf4j.LoggerFactory`), object `SanitizePath`, `private val LOG: Logger = LoggerFactory.getLogger(SanitizePath::class.java)`, KDoc на object с `@see docs/features/idempotent-path-sanitize.md` и `@see specs/304-idempotent-path-sanitize/spec.md`
- [ ] T005 Реализовать `fun String.sanitizePathSegment(): String` внутри `SanitizePath` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/SanitizePath.kt`: char-by-char `when` по таблице из `data-model.md` § 2 (FR-002 + FR-003 + FR-004), `LOG.info("Sanitize [pos=$i, char='$c']: \"$this\" → \"$result\"")` при каждой фактической замене, KDoc с примерами edge cases из `contracts/sanitizer-contract.md`
- [ ] T006 Реализовать `fun String.sanitizePath(): String` внутри `SanitizePath` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/SanitizePath.kt`: split по `/` ИЛИ `\\` (regex `Regex("[/\\\\\\\\]")`), `joinToString("/")` после `sanitizePathSegment()` каждого сегмента, KDoc с примерами `"C:\\Users\\test\\file?.flac" → "C:\\Users\\test\\file_.flac"`
- [ ] T007 Обновить `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Extentions.kt`: заменить тела `rightFileNameSymbols()`, `sanitizeSongFileName()`, `rightFileName()` на однострочные вызовы нового ядра. **Предпочтительный паттерн**: добавить `import com.svoemesto.karaokeapp.sanitizePathSegment` + `import com.svoemesto.karaokeapp.sanitizePath` в шапку файла, затем `fun String.rightFileNameSymbols() = sanitizePathSegment()` / `fun String.sanitizeSongFileName() = sanitizePathSegment()` / `fun String.rightFileName() = sanitizePath()` — прямые вызовы extension-function **без** обёртки `SanitizePath.run { ... }` (которая может сломать KDoc-резолюцию и type inference для компилятора и Dokka). Сохранить существующий KDoc на каждой функции + добавить `@see docs/features/idempotent-path-sanitize.md`
- [ ] T008 Запустить сборку + линтер для проверки, что ядро и обёртки компилируются: `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:compileKotlin :karaoke-app:ktlintCheck --parallel`

**Checkpoint**: foundation ready — `SanitizePath.sanitizePathSegment()` и `SanitizePath.sanitizePath()` работают; 200+ вызывающих мест не сломаны; ktlint проходит.

---

## Phase 3: User Story 1 — Папка альбома с `!` импортируется без потери данных (Priority: P1) 🎯 MVP

**Goal**: воспроизвести баг из issue #53 и доказать, что `!` (и `?`) заменяются на `_` (а не удаляются), сохраняя идемпотентность.

**Independent Test**: создать папку `2012 - Лучшее!` с 2 MP3-файлами, прогнать санитайзер в unit-тесте на строках из quickstart.md § 6, убедиться что:
- `sanitizePathSegment("Лучшее!") == "Лучшее_"` (заменили `!` на `_`, кириллица сохранилась)
- `sanitizePathSegment(sanitizePathSegment("Лучшее!")) == "Лучшее_"` (идемпотентность)

- [ ] T009 [P] [US1] Unit-тест: замена `!` и `?` на `_` + идемпотентность: создать `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/SanitizePathTest.kt`, тест `\`sanitizePathSegment заменяет восклицательный знак на _\`` проверяет `"Hello!".sanitizePathSegment() == "Hello_"` + `sanitizePathSegment(sanitizePathSegment("Hello!")) == sanitizePathSegment("Hello!")` + то же для `?`
- [ ] T010 [P] [US1] Unit-тест: пустая строка + only-problematic: тест `\`sanitizePathSegment обрабатывает пустую строку и строку из только проблемных символов\`` проверяет `"".sanitizePathSegment() == ""` + `"!?*".sanitizePathSegment() == "___"` + `sanitize(sanitize("!?*")) == "_"` (idempotent re-run)
- [ ] T011 [P] [US1] Unit-тест: кириллица + `!` mixed: тест `\`sanitizePathSegment сохраняет кириллицу и заменяет проблемные символы\`` проверяет `"Лучшее!".sanitizePathSegment() == "Лучшее_"` + `"Привет, мир!?".sanitizePathSegment() == "Привет, мир__"` + идемпотентность
- [ ] T012 [US1] Запустить unit-тесты для US1: `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:test --tests "com.svoemesto.karaokeapp.SanitizePathTest.*" --info`, ожидаемый результат: 3 теста зелёные

**Checkpoint**: US1 работает — `!`/`?` корректно заменяются на `_`, идемпотентность доказана, кириллица сохраняется. Базовый баг issue #53 исправлен.

---

## Phase 4: User Story 2 — Единый санитайзер покрывает полный набор shell-/FS-опасных символов (Priority: P1)

**Goal**: расширить покрытие до полной таблицы из FR-002/003/004, верифицировать side-effect идемпотентность (FR-014) через `ListAppender`.

**Independent Test**: unit-тест прогоняет **каждый символ** из таблицы `data-model.md` § 2 (12 из FR-002 + 4 из FR-004 + preserve-список из FR-003), плюс тест с `ListAppender` для FR-014.

### Tests for User Story 2 (FR-009, FR-014)

- [ ] T013 [P] [US2] Unit-тест: каждый символ из FR-002 (12 символов): тест `\`sanitizePathSegment заменяет все 12 символов из FR-002 на _\`` параметризованный через `@ParameterizedTest` + `@ValueSource(strings = ["!", "?", "\n", "\r", "\t", "\0", "<", ">", "&", ";", "\""])` — каждый одиночный символ даёт `"_"`
- [ ] T014 [P] [US2] Unit-тест: каждый символ из FR-003 (preserve): тест `\`sanitizePathSegment сохраняет структурно значимые символы\`` параметризованный через `@ParameterizedTest` + `@ValueSource(strings = ["(", ")", "а", "Я", "1", "-", ".", "+", "=", "@", "#"])` — каждый символ сохраняется как есть
- [ ] T015 [P] [US2] Unit-тест: legacy-mapping FR-004 (4 правила): тест `\`sanitizePathSegment применяет legacy-mapping FR-004 идемпотентно\`` проверяет `"a'b".sanitizePathSegment() == "a\`b"` + `"$5".sanitizePathSegment() == "s5"` + `"*".sanitizePathSegment() == "x"` + `":".sanitizePathSegment() == "-"` + idempotent re-run для каждого
- [ ] T016 [P] [US2] Unit-тест: side-effect идемпотентность через Logback `ListAppender`: тест `\`sanitize не плодит лог-записей на повторный прогон (FR-014)\`` создаёт `ListAppender<ILoggingEvent>`, attaches к `SanitizePath.LOG`, вызывает `"Hello!".sanitizePathSegment()` дважды, проверяет что `listAppender.list.size == 1` после двух вызовов (1 запись от первой замены `!`, 0 от re-run)

### Implementation for User Story 2

- [ ] T017 [US2] Запустить unit-тесты для US2: `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:test --tests "com.svoemesto.karaokeapp.SanitizePathTest.*" --info`, ожидаемый результат: все тесты US1 + US2 зелёные (7+ тестов)

**Checkpoint**: US2 работает — таблица замен полная, side-effect идемпотентность доказана.

---

## Phase 5: User Story 3 — Существующие пути на проде остаются работоспособными (Priority: P1)

**Goal**: доказать, что новый алгоритм не ломает уже-санитайзенные имена файлов на проде (legacy-mapping FR-004 идемпотентен; удалённые `!`/`?` остаются как есть).

**Independent Test**: взять 100+ имён из прод-БД (или сгенерировать синтетическую выборку на основе legacy-паттернов), прогнать через `sanitizePathSegment()`, убедиться что результат **равен** входу (идемпотентность для уже-санитайзенного).

### Tests for User Story 3 (FR-004)

- [ ] T018 [P] [US3] Unit-тест: прод-имена с legacy-mapping сохраняются (SC-003, 100+ имён): тест `\`sanitizePathSegment идемпотентен для уже-санитайзенных прод-имён\`` параметризованный через `@ParameterizedTest` + `@MethodSource` ссылка на companion-функцию `prodBackwardCompatCases()`, которая возвращает **100+ имён**: предпочтительно live-DB (`SELECT file_name FROM tbl_songs ORDER BY random() LIMIT 100` через `KaraokeConnection.local()`) с **fallback на hardcoded список 100+ имён** (синтетическая выборка на основе legacy-паттернов из `data-model.md` § 2, чтобы тест был self-contained и запускался в CI без БД) — каждый вход == выходу `sanitize(input) == input`
- [ ] T019 [P] [US3] Unit-тест: regression на синтетической выборке legacy-паттернов: тест `\`sanitizePathSegment не ломает legacy-санитайзенные имена\`` проверяет набор кейсов из `data-model.md` § 2: `"2012 - Daj zaru.flac"`, `"Queen s"`, `"Queen\` + \" + \` lyrics"`, `"Daj zaru (2).flac"`, `"Daj zaru [live].flac"`, `"x-track.mp3"`, `"2024-01-15 mix.flac"` — каждое `sanitize(input) == input`

### Implementation for User Story 3

- [ ] T020 [US3] Запустить unit-тесты для US3: `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:test --tests "com.svoemesto.karaokeapp.SanitizePathTest.*" --info`, ожидаемый результат: все тесты US1 + US2 + US3 зелёные (10+ тестов), 0 регрессий

**Checkpoint**: US3 работает — обратная совместимость с прод-данными доказана.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: документация (FR-010), governance (FR-011), финальная верификация, OpenProject workflow (FR-013).

- [ ] T021 [P] Создать `docs/features/idempotent-path-sanitize.md`: per-feature документ с разделами: «Контракт» (FR-001..FR-014 краткая выжимка), «Таблица замен» (FR-002/003/004 машино-читаемо), «Идемпотентность» (формальное определение), «Side-effect идемпотентность (FR-014)», «Граница с дедупликатором (FR-007)», «Test coverage map (FR-009)», cross-reference на `specs/304-idempotent-path-sanitize/spec.md`
- [ ] T022 Обновить `AGENTS.md`: добавить пункт TOP-11 в секцию «TOP-10 ловушек» (см. `CLAUDE.md` для стиля): «При правке санитайзера (`SanitizePath.kt`, `Extentions.kt`) — соблюдать идемпотентность и обратную совместимость с уже-санитайзенными именами на проде (см. `docs/features/idempotent-path-sanitize.md`). Удаление символа (drop) вместо замены (replace) ломает идемпотентность и ведёт к потере данных при импорте (issue #53).»
- [ ] T023 Запустить полную проверку CI-блока (см. AGENTS.md § «🚦 CI 7/7 PASS»): `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:ktlintCheck :karaoke-app:test :karaoke-app:compileKotlin --parallel && bash tools/check-kdoc-coverage.sh && bash tools/check-jsdoc-coverage.sh webvue3 && bash tools/check-jsdoc-coverage.sh karaoke-public && pre-commit run --all-files`, ожидаемый результат: все проверки зелёные, ktlint покрытие ≥50%
- [ ] T024 Manual smoke (см. quickstart.md § 6 + SC-004 e2e scenarios из specs/124): (a) открыть Kotlin REPL, выполнить 10 сниппетов из quickstart.md (включая `"Лучшее!".sanitizePathSegment() == "Лучшее_"`, `sanitize(sanitize(s)) == sanitize(s)`, legacy-mapping `"Queen$1's*file:"`); (b) **e2e прогон 10+ сценариев из specs/124** на локальном karaoke-app + локальной БД — импорт папки с `!`/`?` в имени, переименование через SongEdit, перенос стемов через Demucs, загрузка в MinIO — все должны пройти без ошибок (SC-004: 0 регрессий), ожидаемый результат: все примеры и все e2e сценарии дают ожидаемый вывод
- [ ] T025 OpenProject workflow (FR-013): создать `specs/304-idempotent-path-sanitize/report.md` с отчётом о выполненной работе (что реализовано, ссылка на PR, пройденные проверки), затем `cd /home/nsa/Karaoke && source .env.local-tracker && ./tools/tracker.sh add-comment 53 --file specs/304-idempotent-path-sanitize/report.md && ./tools/tracker.sh mark-review 53`
- [ ] T026 Создать PR через `gh pr create --base master --title "feat(304-idempotent-path-sanitize): единый идемпотентный санитайзер путей" --body "Closes #53 в OpenProject. См. specs/304-idempotent-path-sanitize/{spec.md, plan.md, data-model.md, contracts/sanitizer-contract.md, quickstart.md, report.md}."`, ожидаемый результат: PR создан, CI 7/7 PASS
- [ ] T027 [P] FR-012 governance (см. уточнённый FR-012 в spec.md): выбрать вариант по согласованию с командой и реализовать ДО T026 (PR creation): **Вариант A** — bump `constitution.md` до v2.2.0 (MINOR: новый Principle IX «Санитайзер идемпотентен» — `sanitize(sanitize(s)) == sanitize(s)` обязательно для любых path/name sanitizer'ов) с Sync Impact Report в HTML-комментарии по governance v2.1.0 §5 + отдельный PR на constitution (этот PR ссылается на него через cross-reference), **ИЛИ Вариант B** — добавить секцию «Инварианты санитайзера» в `DEVELOPMENT.md` с формулировкой контракта + cross-reference на `docs/features/idempotent-path-sanitize.md`. После реализации: `grep -rE 'sanitize.*idempotent|idempotent.*sanitize' constitution.md DEVELOPMENT.md docs/features/idempotent-path-sanitize.md 2>/dev/null` возвращает ≥1 hit (acceptance FR-012)

**Checkpoint**: фича полностью реализована, документирована, проходит все проверки, OpenProject в In review, PR готов к merge.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: нет зависимостей — стартует немедленно.
- **Foundational (Phase 2)**: зависит от Setup (Phase 1) — **БЛОКИРУЕТ** все user stories.
- **User Stories (Phase 3-5)**: зависят от Foundational (Phase 2). Все три истории — P1, могут идти последовательно в порядке US1 → US2 → US3 или параллельно (если есть 3 разработчика).
- **Polish (Phase 6)**: зависит от завершения всех user stories.

### User Story Dependencies

- **User Story 1 (P1)**: стартует после Foundational. Нет зависимостей от других историй.
- **User Story 2 (P1)**: стартует после Foundational. Нет зависимостей от US1 (расширяет тесты на ту же реализацию).
- **User Story 3 (P1)**: стартует после Foundational. Нет зависимостей от US1/US2 (тестирует обратную совместимость той же реализации).

### Within Each User Story

- Tests пишутся **ПЕРВЫМИ**, проверяется их ПРОВАЛ, затем реализация (если применимо — здесь реализация уже в Phase 2, поэтому тесты должны сразу проходить).
- В нашем случае: реализация уже в Phase 2 (T004-T008), тесты в US1/US2/US3 проверяют, что реализация корректна. Если тест проваливается — фиксим реализацию в Phase 2, не в user-story.

### Parallel Opportunities

- Phase 1: T001, T002, T003 — все [P] могут идти параллельно (нет зависимостей между ними).
- Phase 2: T004 → T005 → T006 → T007 → T008 (строго последовательно: скелет → segment → path → обёртки → verify).
- Phase 3 (US1): T009, T010, T011 — все [P] могут идти параллельно (разные тесты в одном файле `SanitizePathTest.kt`).
- Phase 4 (US2): T013, T014, T015, T016 — все [P] могут идти параллельно.
- Phase 5 (US3): T018, T019 — [P] могут идти параллельно.
- Phase 6: T021 [P] можно делать параллельно с T022-T026 (разные файлы).

### Within-File Conflict

- T005 и T006 оба в `SanitizePath.kt` — строго последовательно.
- T007 в `Extentions.kt` — после T005 (нужен `SanitizePath.sanitizePathSegment()` для обёртки).
- T009, T010, T011, T013, T014, T015, T016, T018, T019 — все в `SanitizePathTest.kt`, **строго последовательно** (один файл, риск merge conflict при параллельной правке). `[P]` marker на этих задачах — для параллельного запуска тестов после написания, не для параллельного написания.

---

## Parallel Example: User Story 1

```bash
# Запустить все тесты US1 параллельно (после их написания):
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:test \
  --tests "com.svoemesto.karaokeapp.SanitizePathTest.sanitizePathSegment заменяет восклицательный знак на _" \
  --tests "com.svoemesto.karaokeapp.SanitizePathTest.sanitizePathSegment обрабатывает пустую строку и строку из только проблемных символов" \
  --tests "com.svoemesto.karaokeapp.SanitizePathTest.sanitizePathSegment сохраняет кириллицу и заменяет проблемные символы" \
  --info --parallel
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001-T003)
2. Complete Phase 2: Foundational (T004-T008)
3. Complete Phase 3: User Story 1 (T009-T012)
4. **STOP and VALIDATE**: запустить `./gradlew :karaoke-app:test` — 3+ теста зелёные.
5. **Optional demo**: manual smoke в REPL (quickstart.md § 6) — показать, что `"Лучшее!".sanitizePathSegment() == "Лучшее_"`.

Это — **минимальный** набор, который доказывает базовый фикс issue #53.
Можно открыть PR уже на этом этапе (с пометкой WIP), но полный PR
включает US2 + US3 + Polish.

### Incremental Delivery

1. Setup + Foundational → foundation ready (T001-T008)
2. Add US1 → test independently → MVP готов (T009-T012)
3. Add US2 → test independently → расширенное покрытие готово (T013-T017)
4. Add US3 → test independently → обратная совместимость готова (T018-T020)
5. Polish → документация, governance, OpenProject (T021-T027)
6. Каждая фаза добавляет ценность, не ломая предыдущие.

### Parallel Team Strategy

С 3 разработчиками (overkill для 27 задач, но возможно):

1. Команда вместе: Setup + Foundational (T001-T008).
2. После Foundational:
   - Developer A: User Story 1 (T009-T012).
   - Developer B: User Story 2 (T013-T017).
   - Developer C: User Story 3 (T018-T020) — нужны прод-данные, может стартовать позже.
3. Все вместе: Polish (T021-T027).

---

## Notes

- [P] tasks = разные файлы или независимые операции, можно параллелить.
- [Story] label связывает задачу с конкретной user story для traceability.
- Каждая user story должна быть **независимо завершаемой и тестируемой**.
- **Verify tests pass** (не "fail before implementation", потому что реализация уже в Phase 2). Если тест проваливается — фиксим реализацию в Phase 2 (T005/T006), не в user-story фазе.
- Commit после каждой задачи или логической группы (например, после T008 + T012 вместе, как «core + US1 MVP»).
- Остановиться на любой checkpoint для валидации story независимо.
- Избегать: vague tasks, same-file conflicts (T009-T019 все в одном файле, потому строго последовательно при написании).
- **Полная пересборка обязательна после правок** (см. AGENTS.md «Обязательная проверка после ЛЮБОГО изменения кода»): `compileKotlin` → `ktlintCheck` → `bootJar` → Vite build → Docker images.