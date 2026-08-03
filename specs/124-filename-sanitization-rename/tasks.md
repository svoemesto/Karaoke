---

description: "Task list for 124-filename-sanitization-rename"
---

# Tasks: Санитайзинг имён файлов при импорте и переименование при редактировании

**Input**: Design documents from `/specs/124-filename-sanitization-rename/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/song-rename-contract.md](./contracts/song-rename-contract.md), [quickstart.md](./quickstart.md)

**Tests**: Не запрошены явно в спецификации и в проекте нет CI-тестового харнесса для
этого модуля (см. `plan.md` → Technical Context → Testing) — тестовые задачи не
генерируются; вместо них — ручная валидация по `quickstart.md` (Polish-фаза).

**Organization**: Задачи сгруппированы по user story (US1 = P1 импорт, US2 = P2
каскадное переименование в SongEdit) для независимой реализации и проверки.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Может выполняться параллельно (разные файлы, нет зависимости от незавершённых задач)
- **[Story]**: US1 (импорт) или US2 (каскадное переименование)

## Path Conventions

Существующий Gradle multi-module репозиторий (не новый проект): backend —
`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/`, admin frontend —
`webvue3/src/`, документация — `docs/features/`.

---

## Phase 1: Setup

**Purpose**: Подтвердить рабочее окружение — новых модулей/зависимостей эта
фича не создаёт (см. `plan.md` → Project Structure → Structure Decision).

- [X] T001 Проверить, что активна ветка `124-filename-sanitization-rename` и она синхронизирована с `master` (`git status`, `git log --oneline -5`)

**Checkpoint**: Окружение готово, новых зависимостей/скаффолдинга не требуется.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Общая инфраструктура, от которой зависят обе user story —
расширенная функция санитайзинга и общий хелпер проверки коллизии имени файла
в пределах папки.

**⚠️ CRITICAL**: Ни US1, ни US2 не должны начинаться до завершения этой фазы.

- [X] T002 [P] ~~Расширить `rightFileNameSymbols()`~~ **ОТКЛОНЕНИЕ ОТ ПЛАНА (обнаружено при реализации)**: `rightFileName()`/`rightFileNameSymbols()` вызываются в 24+ местах кодовой базы НЕ только на "голых" именах файлов, но и на уже собранных абсолютных путях (`StemJobProcessing.kt`, `KaraokeProcess.kt` — например `"$tempFolder/upload.ext".rightFileName()`). Добавление удаления `/`/`\` туда сломало бы разделители пути везде. Вместо этого добавлена НОВАЯ функция `String.sanitizeSongFileName()` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Extentions.kt` (убирает `/`, `\`, `"`, затем переиспользует `rightFileNameSymbols()`) — применяется только к "голому" фрагменту имени файла песни, не трогает существующие 24 вызывающих места
- [X] T003 KDoc добавлен инлайн вместе с T002 — `rightFileNameSymbols()` (уточнение, почему `/`/`\` не трогаются) и `sanitizeSongFileName()` (новая функция), оба с `@see docs/features/async-process-queue.md`
- [X] ~~T004~~ **НЕ ТРЕБУЕТСЯ (обнаружено при реализации)**: хелпер проверки коллизии уже существует — `Song.loadListFromDb(args = mapOf("file_name" to fileName, "root_folder" to rootFolder), ...)` (`Song.kt:7603`, использует `getWhereList()` → `WHERE LOWER(file_name)=... AND LOWER(root_folder)=...`, `Song.kt:7343-7344`) — именно эта проверка уже используется в `createFromPath()` для skip-if-already-imported; переиспользована как есть и в US1 (после сортировки коллизии), и будет переиспользована в US2 (T011) вместо нового метода

**Checkpoint**: Общая функция санитайзинга и хелпер коллизии готовы — можно начинать US1 и US2 (параллельно, разными участниками, если нужно).

---

## Phase 3: User Story 1 - Импорт песен с "проблемными" символами (Priority: P1) 🎯 MVP

**Goal**: `Song.createFromPath()` санитайзирует имя файла на диске до перекодирования в FLAC, сохраняя оригинальные символы в названии песни; коллизии внутри одной папки разрешаются автоматическим числовым суффиксом.

**Independent Test**: `quickstart.md`, сценарии 1-3 — импорт файла `2012 (01) [Ария] - Дай жару!.mp3` даёт песню с названием `Дай жару!` и файлом `2012 (01) [Ария] - Дай жару.flac` на диске, дальнейшая обработка (Demucs) не падает.

### Implementation for User Story 1

- [X] T005 [US1] В `Song.createFromPath()` применён `.sanitizeSongFileName()` к `rawFileName` сразу после парсинга regex, ДО блока перекодирования в FLAC; санитайзированное значение используется и для `ffmpeg`-вывода/переименования на диске (включая новый `mv`-путь для источников, уже бывших `.flac`), и для `song.fileName` — устраняет корневую причину падения Demucs на `!`
- [X] T006 [US1] Реализован авто-суффикс при коллизии (FR-005) через локальный `usedFileNamesThisRun: MutableMap<rootFolder, MutableSet<fileName>>`, живущий только в рамках одного вызова `createFromPath()` — коллизия с другим файлом ЭТОГО ЖЕ импорта даёт `" (2)"`, `" (3)"`; проверка "уже импортировано" через существующий `loadListFromDb` (см. T004-note) не меняет семантику
- [X] T007 [US1] `song.fields[SongField.NAME] = songNameStr` не тронут — `songNameStr` берётся из regex-парсинга `rawFileName` (до санитайзинга); добавлен комментарий-инвариант в коде
- [X] T008 [US1] KDoc для `Song.createFromPath()` обновлён — описывает санитайзинг+авто-суффикс, `@see docs/features/async-process-queue.md`
- [X] T009 [P] [US1] `docs/features/async-process-queue.md` обновлён — новая запись в «Известные ловушки» про specs/124

**Checkpoint**: US1 полностью функциональна и независимо тестируема — `quickstart.md` сценарии 1-3 проходят.

---

## Phase 4: User Story 2 - Каскадное переименование через SongEdit (Priority: P2)

**Goal**: Изменение «Имя файла» в SongEdit санитайзирует значение, блокирует сохранение при коллизии/пустом имени/активной фоновой обработке, и каскадно переименовывает все существующие производные артефакты на диске и в обоих хранилищах (best-effort вперёд, без отката; частичный отказ проявляется через существующий `HealthReport`).

**Independent Test**: `quickstart.md`, сценарии 4-9 — переименование песни с уже посчитанными стемами и загрузкой в оба хранилища переносит все артефакты под новое имя; коллизия/пустое имя/активная обработка блокируют сохранение с понятным сообщением; отсутствующие артефакты не считаются ошибкой.

### Implementation for User Story 2

- [X] T010 [P] [US2] `KaraokeProcess.hasActiveProcess(songId, database): Boolean` добавлен в `KaraokeProcess.kt` — переиспользует существующий `loadList(args = mapOf("song_id" to ...))`, фильтрует по статусу в Kotlin (WAITING/WORKING)
- [X] T011 [US2] В `ApiController.songs2Update` добавлена валидация при непустом `fileName`, отличном от текущего: санитайзинг через `sanitizeSongFileName()`, затем (a) не пусто, (b) нет коллизии в `rootFolder` (переиспользован `Song.loadListFromDb`, см. T004-note), (c) нет активного процесса (`T010`); при нарушении — `fileName` не применяется, `fileNameRenameError` в ответе (T016)
- [X] ~~T012~~ **СУЩЕСТВЕННОЕ ОТКРЫТИЕ при реализации**: локальное каскадное переименование ОСНОВНОГО аудио (FLAC) и FLAC/WAV-вариантов ВСЕХ стемов УЖЕ существовало — `Song.renameFilesIfDiff()` (`Song.kt:6029`), вызывается автоматически из `saveToDb()` (`Song.kt:5389`) при ЛЮБОМ изменении `fileName`, не только через SongEdit. Подтверждённые РЕАЛЬНЫЕ пробелы (не покрыты `renameFilesIfDiff`): `.kdenlive`-проект, файл субтитров (`.kdenlive.srt`), устаревший sidecar `.song`-файл под старым именем, mp3-варианты стемов на диске. Реализовано новым методом `Song.renameCascadeExtraArtifacts(oldFileName)` (инстанс-метод `Song.kt`, не отдельный файл `SongFileRename.kt` — переиспользует приватные path-геттеры класса)
- [X] T013 [US2] В том же методе `renameCascadeExtraArtifacts()` — каскад для mp3-стемов (accompaniment/vocal/bass/drums/other) в `KaraokeStorageService` (локальное хранилище, бакет `"karaoke"`, ключ `"${storageFileName}${suffix}.${extention}"` — шаблон подтверждён по коду `ApiController.pushMp3ToStorage`) и `StorageApiClient` (удалённое) тем же шаблоном; загрузка под новым ключом из уже переименованного локального mp3 (или download-старого/upload-нового как фолбэк), затем удаление старого ключа. **⚠️ Шаблон ключа для УДАЛЁННОГО хранилища взят по аналогии с локальным (идентичная форма интерфейса), но реально НЕ верифицирован живой проверкой в этой сессии** — см. пометку в `docs/features/async-process-queue.md`
- [X] T014 [US2] `renameCascadeExtraArtifacts(oldFileName)` вызывается из `songs2Update` сразу после `sett.fileName = sanitized`, до `sett.saveToDb()`/`sett.saveToFile()` (которые запускают уже существующий `renameFilesIfDiff` и перезаписывают sidecar)
- [X] T015 [P] [US2] Частично проверено: механизм `HealthReport` (ERROR/FATAL_ERROR при отсутствии ожидаемого файла) применим по конструкции к любому артефакту, путь которого вычисляется через `song.fileName`-геттеры. **Не верифицировано исчерпывающе**: отслеживает ли существующий вызывающий код `HealthReport` конкретно `.kdenlive`/`.kdenlive.srt` (per-song editing-project файлы) как отдельный `KaraokeFileType` — похожий enum-вариант `PROJECT_SONGVERSION_KDENLIVE` относится к `karaokeFileTypeFor=SONGVERSION` (per-версии рендер), что может быть другой сущностью; изменений в `HealthReport.kt` не вносилось
- [X] T016 [US2] Ответ `songs2Update`/`POST /song/update` расширен до `ApiController.SongUpdateResultDto(albumLinkValid: Boolean, fileNameRenameError: String?)` вместо голого `Boolean` — безопасно, т.к. ни один текущий вызывающий код (`webvue3/store.js`) ранее не читал значение ответа
- [X] T017 [P] [US2] `webvue3/src/components/Songs/store.js` → `saveSong` теперь парсит JSON-ответ, бросает `Error(fileNameRenameError)` при наличии — существующий `.catch()` пробрасывает его дальше
- [X] T018 [US2] `webvue3/src/components/Songs/edit/SongEdit.vue` → `executeSave()` catch-блок показывает `createToast` (variant danger) с текстом ошибки вместо только `console.error`
- [X] T019 [US2] KDoc добавлен инлайн вместе с T012/T013 в `Song.kt` (`@see docs/features/premium-stems.md`) и в `ApiController.kt` (`SongUpdateResultDto`, `@see docs/features/premium-stems.md`)
- [X] ~~T020~~ **ИСПРАВЛЕНА ЦЕЛЬ (обнаружено при реализации)**: `docs/features/premium-stems.md` документирует ДРУГУЮ подсистему — премиум-фичу `StemJob` (пользовательская загрузка файла → стем-сепарация, `tbl_stem_jobs`), НЕ хранение стемов в каталоге `tbl_songs`. Секция про каскадное переименование добавлена вместо этого в `docs/features/async-process-queue.md` (та же секция «Известные ловушки», где уже находится запись про T009) — там же зафиксирована пометка о неверифицированном шаблоне удалённого хранилища (T013)

**Checkpoint**: US2 полностью функциональна и независимо тестируема — `quickstart.md` сценарии 4-9 проходят.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Финальные проверки качества перед коммитом/PR (обязательны по CLAUDE.md/Конституции).

- [X] T021 [P] `./gradlew ktlintCheck` — чисто, нарушений нет
- [X] T022 [P] `npx eslint` + `npx prettier --check` для `store.js`/`SongEdit.vue` — чисто
- [X] T023 KDoc karaoke-app 96.7% (377/390), karaoke-web 100%; JSDoc webvue3 99.3% (134/135) — оба выше порога ≥50%, регрессии нет
- [ ] T024 **НЕ ВЫПОЛНЕНО в этой сессии** — нужна ручная проверка по `quickstart.md` (все 9 сценариев) в production-like окружении с реальным Demucs/MinIO; в этой сессии проверено только компиляцией (`./gradlew :karaoke-app:compileKotlin`) и статическим анализом, БЕЗ живого запуска импорта/переименования
- [X] T025 `git diff --name-only | xargs pre-commit run --files` — все проверки Passed (ktlint, eslint×2, prettier×2, lychee, per-feature-doc structure)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: без зависимостей
- **Foundational (Phase 2)**: зависит от Setup — БЛОКИРУЕТ обе user story
- **User Story 1 (Phase 3)**: зависит только от Foundational — независима от US2
- **User Story 2 (Phase 4)**: зависит только от Foundational — независима от US1 (обе истории трогают разные функции в разных/новых файлах, см. ниже)
- **Polish (Phase 5)**: зависит от завершения желаемого набора user story (минимум US1 для MVP)

### User Story Dependencies

- **US1 (P1)**: может начинаться сразу после Foundational; не зависит от US2
- **US2 (P2)**: может начинаться сразу после Foundational; не зависит от US1 (переиспользует общий хелпер `T004`, но использует его в другом коде-пути — валидация reject, а не авто-суффикс)

### Within Each User Story

- US1: T005 → T006 → T007 → T008 (один и тот же метод/файл, последовательно); T009 — параллельно (другой файл)
- US2: T010 — параллельно; T011 зависит от T004+T010; T012 → T013 (один файл, последовательно), могут стартовать параллельно с T011 (другой файл), но интегрируются в T014, которая зависит от T011+T012+T013; T015 — параллельно; T016 зависит от T014 (тот же файл `ApiController.kt`); T017 зависит от T016 (форма ответа), но другой файл; T018 зависит от T017 по смыслу (использует то, что возвращает store), но другой файл; T019 — после T012/T013 (тот же файл); T020 — параллельно

### Parallel Opportunities

- Foundational: `T002` и `T004` — разные файлы, параллельно
- US1: `T009` параллельно с T005-T008
- US2: `T010`, `T012` (старт), `T015`, `T017`, `T020` — кандидаты на параллельное выполнение с остальными задачами US2 (разные файлы)
- После Foundational — US1 и US2 можно вести параллельно (два разработчика/два независимых прохода агента)

---

## Parallel Example: Foundational

```bash
Task: "Extend rightFileNameSymbols() in karaoke-app/.../Extentions.kt"
Task: "Add Song.existsWithFileNameInFolder() collision helper in karaoke-app/.../model/Song.kt"
```

## Parallel Example: User Story 2 (после T004/T011 готовы)

```bash
Task: "Add KaraokeProcess.hasActiveProcess() helper in karaoke-app/.../KaraokeProcess.kt"
Task: "Implement Song.renameLocalArtifacts() in karaoke-app/.../model/SongFileRename.kt"
Task: "Verify HealthReport coverage for partial-rename-failure in karaoke-app/.../HealthReport.kt"
Task: "Update docs/features/premium-stems.md"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1: Setup
2. Phase 2: Foundational (КРИТИЧНО — блокирует обе истории)
3. Phase 3: User Story 1 — устраняет блокирующий баг «падает Demucs на `!`»
4. **СТОП и ПРОВЕРКА**: `quickstart.md` сценарии 1-3
5. При необходимости — деплой/демонстрация MVP до реализации US2

### Incremental Delivery

1. Setup + Foundational → фундамент готов
2. US1 → проверить независимо → MVP (устраняет прод-баг импорта)
3. US2 → проверить независимо → полная фича (безопасное ручное переименование)
4. Polish → финальные проверки качества перед PR

---

## Notes

- [P] задачи = разные файлы, нет зависимостей
- [Story] label связывает задачу с конкретной user story
- Тестовые задачи не генерировались — тестов для этого модуля нет в CI (см. Конституцию); вместо этого — `quickstart.md` в Polish-фазе
- Коммитить после каждой задачи или логической группы (по явному запросу пользователя — см. правила git-workflow проекта)
- US1 и US2 намеренно не пересекаются по коду напрямую (US1 — `createFromPath`, US2 — `songs2Update` + новый `SongFileRename.kt`), кроме общего хелпера `T004` из Foundational — независимость сохранена
