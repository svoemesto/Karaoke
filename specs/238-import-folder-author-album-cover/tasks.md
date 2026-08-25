# Tasks: 238 — Импорт из папки: родители только у того же автора + автообложка альбома

**Input**: Design documents from `/specs/238-import-folder-author-album-cover/`
**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/, quickstart.md

**Tests**: НЕ запрашивались в спеке. Constitution § «Тесты» явно указывает, что существующие юнит-тесты в `karaoke-app/src/test` — `@Disabled`, проверка делается пользователем вручную через `quickstart.md`. Тестовые задачи НЕ включаются.

**Organization**: Задачи сгруппированы по user story (US1 — поиск «родителя» у того же автора; US2 — автообложка нового альбома). Каждая user story — независимый инкремент; обе правят разные Kotlin-файлы и могут коммититься вместе в одном PR как единая фича.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: можно запустить параллельно (разные файлы, нет зависимостей).
- **[Story]**: к какой user story относится задача (US1, US2).
- В описании — точные пути к файлам.

## Path Conventions

- **Multi-module Gradle (Spring Boot)**: фича правит только `karaoke-app/src/main/kotlin/...`. Никаких изменений в `webvue3/`, `karaoke-public/`, `karaoke-web/`, `karaoke-db/`.
- Все пути Kotlin-файлов ниже — абсолютные относительно корня репозитория.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: инициализация проекта и базовая структура.

> **NOTE**: фича работает поверх существующего кода — проект уже инициализирован, новая инфраструктура не требуется. Фаза пуста.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: core-инфраструктура, которая ДОЛЖНА быть готова до начала любой user story.

> **NOTE**: обе user story правят разные Kotlin-файлы и не требуют общих блокирующих prerequisites (никаких миграций БД, никакого нового фреймворка, никакого нового сервиса). Фаза пуста.

---

## Phase 3: User Story 1 — Поиск «родителя» только у того же автора (Priority: P1) 🎯 MVP-ready

**Goal**: устранить ложные привязки текста/маркеров от песен **других** авторов при импорте новой песни из папки. Поиск ограничивается только песнями того же автора.

**Independent Test**: сценарии 1-2 из `quickstart.md`:
- Сценарий 1: импорт папки с новой песней «Звезда» для автора B при существующей одноимённой песне автора A → новая песня B НЕ получает `source_text`/`result_text`/`source_markers`/`root_id` от A.
- Сценарий 2: импорт папки с такой же песней для того же автора A → новая песня получает текст/маркеры от оригинала (поведение не regression'нулось).

### Implementation for User Story 1

- [ ] T001 [US1] В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt` (строка 4296) заменить `val id = findId(sameAuthorOnly = true) ?: findId(sameAuthorOnly = false) ?: return null` на `val id = findId(sameAuthorOnly = true) ?: return null` — убрать fallback на поиск среди всех авторов.
- [ ] T002 [US1] В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt` (строки 4262-4267, KDoc функции `findDuplicateOriginal`) обновить комментарий: убрать упоминание про fallback на «среди всех авторов»; описать новое поведение — поиск **только** у того же автора, при ненахождении возвращается `null`. KDoc MUST содержать `@see specs/238-import-folder-author-album-cover/spec.md` (Constitution § VI, FR-006).

**Checkpoint**: US1 функционально завершена — `findDuplicateOriginal` ищет «родителя» только у того же автора; все вызывающие (`ApiController.doCreateFromFolder`, `MainController.doCreateFromFolder` через `Song.createFromPath`) автоматически получают новое поведение без правок.

---

## Phase 4: User Story 2 — Автообложка нового альбома из графического файла в `rootFolder` (Priority: P1) 🎯 MVP-ready

**Goal**: при создании **нового** альбома в импорте из папки автоматически находить ровно один графический файл в `rootFolder` каждой песни и использовать как исходник для `LogoAlbum.png` + превью + запись в `Pictures` (локальное + MinIO). Существующие альбомы не перезатираются. UI/UX не меняется.

**Independent Test**: сценарии 3-11 из `quickstart.md`:
- Сценарий 3: новый альбом + ровно один графический файл → `LogoAlbum.png` (400×400 PNG) создан, превью создано, `tbl_pictures` обновлена, MinIO обновлён.
- Сценарий 4: ноль графических файлов → обложка не создаётся, импорт песен проходит без ошибок.
- Сценарий 5: ≥2 графических файлов → обложка не создаётся.
- Сценарий 6: альбом уже существует → лежащий рядом графический файл НЕ перезатирает существующую обложку.
- Сценарий 7: много-дисковый альбом (`CD1/`, `CD2/`) → обложка в каждой `rootFolder` отдельно.
- Сценарий 8: `LogoAlbum.png` + `cover.jpg` в папке → автообложка не создаётся (считается как 2 файла).
- Сценарий 9: битый графический файл → автообложка не создаётся, импорт песен продолжается без ошибок.
- Сценарий 10: `git diff webvue3/` показывает только пустые изменения (UI не задет).
- Сценарий 11: кнопка «Найти и обработать дубликаты» работает как раньше (FR-004).

### Implementation for User Story 2

- [ ] T003 [P] [US2] В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Album.kt` (companion object) добавить новый helper `findOrCreateForSongImportRaw(...)` — копия существующей логики `findOrCreateForSongImport`, но возвращающая `Pair<Album?, Boolean>` (`isJustCreated`). Альтернативный вариант — модифицировать существующую `findOrCreateForSongImport` чтобы возвращала тот же `Pair` (минимально-инвазивно, если существующие вызовы совместимы с новой сигнатурой; выбрать вариант с минимальным blast radius на этапе имплементации).
- [ ] T004 [P] [US2] В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Album.kt` (companion object) добавить новый helper `applyAutoAlbumCoverFromFolder(rootFolder, authorName, year, albumName, song, database, storageService, storageApiClient): Boolean` — плоский обход `rootFolder` через `File.listFiles`, фильтр по расширению (`jpg|jpeg|png|webp|bmp|tiff`, без учёта регистра, исключая скрытые), при ровно одном кандидате — `cropCenterSquareAndResize(bytes, 400)` + `ImageIO.write(..., "png", ...)` в `$rootFolder/LogoAlbum.png` + `chmod 666` + создание/обновление `Pictures` через `song.pictureAlbum`. Возвращает `true` если обложка создана/обновлена, иначе `false`. При любом исключении внутри (битый файл, ошибка ImageIO) — return `false` без проброса (FR-010).
- [ ] T005 [US2] В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Album.kt` (companion object) добавить новый публичный метод `findOrCreateForSongImportWithAutoCover(authorName, year, albumName, rootFolder, song, database, storageService, storageApiClient): Album?` — вызывает `findOrCreateForSongImportRaw`; если `Album != null` и `isJustCreated == true` — вызывает `applyAutoAlbumCoverFromFolder`; возвращает `Album?`. Для `albumName.isBlank()` (сингл) возвращает `null`. Существующая `findOrCreateForSongImport` остаётся **без изменений** для обратной совместимости с `AlbumBackfill`.
- [ ] T006 [US2] В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Album.kt` добавить KDoc к новым методам `findOrCreateForSongImportWithAutoCover`, `findOrCreateForSongImportRaw`, `applyAutoAlbumCoverFromFolder` с `@see`-ссылкой на `specs/238-import-folder-author-album-cover/spec.md` (Constitution § VI, FR-006). В KDoc явно указать: новый helper активирует автообложку **только** при создании нового альбома; существующая `findOrCreateForSongImport` остаётся для `AlbumBackfill` и других путей без автообложки.
- [ ] T007 [US2] В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt` (строка 8064, метод `createFromPath`) заменить вызов `Album.findOrCreateForSongImport(authorName = authorStr, year = yearStr.toIntOrNull() ?: 0, albumName = albumStr, database = database, storageService = storageService, storageApiClient = storageApiClient)?.id` на `Album.findOrCreateForSongImportWithAutoCover(authorName = authorStr, year = yearStr.toIntOrNull() ?: 0, albumName = albumStr, rootFolder = rootFolder, song = song, database = database, storageService = storageService, storageApiClient = storageApiClient)?.id`. Никаких других изменений в `createFromPath`.

**Checkpoint**: US2 функционально завершена — `Song.createFromPath` создаёт/переиспользует альбом через новый helper, и для **вновь создаваемых** альбомов автоматически создаётся обложка из графического файла в `rootFolder` (если он ровно один). Оба эндпоинта (`ApiController.doCreateFromFolder`, `MainController.doCreateFromFolder`) автоматически покрыты через общую функцию (см. `contracts/apply-auto-album-cover.md`, `research.md` R5).

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: финальные проверки, документирование, наблюдаемость.

- [x] T008 [P] Собрать `karaoke-app` локально: `cd /sm-karaoke/system/deploy && bash do.sh build_app` (или `./gradlew clean karaoke-app:bootJar --parallel`) — убедиться, что все 3 правки компилируются без ошибок и без новых warnings от ktlint. **Выполнено агентом**: `./gradlew :karaoke-app:bootJar` → `BUILD SUCCESSFUL in 3m 49s`. Пересборка/перезапуск контейнера `karaoke-app` НЕ выполнялась — за пределами полномочий агента на этой машине (см. Constitution § I, п. 1; hostname ≠ `dev-pc`).
- [x] T009 [P] Запустить pre-commit hooks (если настроены через `.pre-commit-config.yaml`): `./tools/check-kdoc-coverage.sh` (FR-006), `./gradlew ktlintCheck` (FR-007) — на изменённых файлах (`Utils.kt`, `Album.kt`, `Song.kt`) НЕ должно быть новых нарушений baseline. **Выполнено агентом**: `./gradlew :karaoke-app:ktlintCheck` → `BUILD SUCCESSFUL` (нет новых нарушений); `tools/check-kdoc-coverage.sh` → 96.3% (выше целевого 50%, FR-006 выполнен).
- [ ] T010 Прогнать ручные сценарии 1-12 из `specs/238-import-folder-author-album-cover/quickstart.md` на dev-pc (admin-машина): запустить `karaoke-app`, выполнить каждый сценарий, зафиксировать результаты в PR-описании (что прошло / что требует доработки). Дополнительно: выполнить `git diff webvue3/src/views/HomeView.vue webvue3/src/components/Albums/` — должен быть пуст или содержать только служебные правки (SC-006); задокументировать результат в PR-описании. **НЕ выполнено агентом**: требует работающий контейнер `karaoke-app` и admin-машину (Constitution § I, п. 1; hostname ≠ `dev-pc`); пользователь запускает вручную после `git pull`/`merge` этой ветки.
- [x] T011 [P] Создать per-feature LiveDoc `livedocs/features/238-import-folder-author-album-cover.md` по шаблону из `specs/082-fix-import-folder-oom` (≤80 строк, frontmatter `status: Active`, `slug: 238-import-folder-author-album-cover`, `related:` — ссылки на `domain/catalog.md` и `architecture/L3-components.md`). Это обязательный пункт Constitution § VI (FR-009) — per-feature документ создаётся в том же PR, что и код фичи. **Выполнено агентом**: файл создан; `tools/check-livedocs-structure.sh` → 7/7 PASS.
- [ ] T012 [P] Удалить тестовые данные после прогонов (`/sm-karaoke/work/_test_238/` + соответствующие `tbl_songs`/`tbl_albums`/`tbl_pictures` записи; см. раздел «Cleanup» в `quickstart.md`). **НЕ выполнено агентом**: тестовые данные не создавались (сценарии T010 ещё не прогонялись); cleanup выполняется пользователем после T010.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: пусто, no-op.
- **Phase 2 (Foundational)**: пусто, no-op.
- **Phase 3 (US1)**: может начинаться сразу после Phase 1-2 (no-op).
- **Phase 4 (US2)**: может начинаться сразу после Phase 1-2 (no-op). **Не зависит** от US1 — обе правят разные файлы.
- **Phase 5 (Polish)**: зависит от завершения **обеих** US1 и US2 (T008, T009, T010 — это прогон всего вместе).

### User Story Dependencies

- **US1 (P1)**: независима, может стартовать параллельно с US2 (разные файлы).
- **US2 (P2)**: независима, может стартовать параллельно с US1 (разные файлы).

Однако обе US идут в **одном PR** (одна фича, оба изменения в одном поставке — оба помечены P1 в спеке и устраняют две конкретные операционные проблемы одного пользовательского сценария «Добавить файлы из папки»).

### Within Each User Story

- US1: T001 → T002 (правка кода → обновление KDoc).
- US2: T003, T004 параллельны (разные методы в одном файле `Album.kt`, но логически независимы — порядок не критичен, оба добавляются в один companion object); T005 зависит от T003+T004 (использует оба); T006 зависит от T003+T004+T005 (KDoc для всех трёх); T007 зависит от T005 (вызов нового helper).

### Parallel Opportunities

- T003 и T004 [P] — параллельно (разные методы, логически независимы).
- T008 и T009 [P] — параллельно (сборка и линт — независимые шаги).
- T011 и T012 [P] — параллельно (LiveDoc создание и cleanup — независимые).
- US1 (T001+T002) и US2 (T003+T004+T005+T006+T007) — формально независимы по файлам; в рамках одного PR разрабатываются последовательно одним инженером, но в теории могут быть распараллелены между двумя.

---

## Parallel Example: US2

```bash
# T003 и T004 можно подготовить параллельно (разные companion-методы):
Task: "T003 [US2] Добавить findOrCreateForSongImportRaw в Album.kt"
Task: "T004 [US2] Добавить applyAutoAlbumCoverFromFolder в Album.kt"

# Затем последовательно:
Task: "T005 [US2] Добавить findOrCreateForSongImportWithAutoCover (зависит от T003+T004)"
Task: "T006 [US2] KDoc для всех трёх (зависит от T003+T004+T005)"
Task: "T007 [US2] Заменить вызов в Song.createFromPath (зависит от T005)"
```

---

## Implementation Strategy

### MVP First (обе US одновременно — одна фича)

Фича неделима — обе US правят один пользовательский сценарий («Добавить файлы из папки»), поэтому реализуются и поставляются вместе в одном PR.

1. Phase 1 (Setup) — пусто.
2. Phase 2 (Foundational) — пусто.
3. Phase 3 (US1) — T001 + T002 в `Utils.kt`.
4. Phase 4 (US2) — T003 + T004 + T005 + T006 + T007 в `Album.kt` и `Song.kt`.
5. Phase 5 (Polish) — T008 + T009 + T010 + T011 + T012.

### Incremental Delivery

Один PR, один релиз. После мержа в master:
- Post-merge: `livedocs/features/238-import-folder-author-album-cover.md` готов к публикации (T11).
- Никаких backfill/миграций в БД не требуется.
- Никаких изменений на prod-сервере (фича только в `karaoke-app`, который на проде не разворачивается — см. Constitution § «Технологический стек»). Замечание: фича деплоится через `do.sh build_app && do.sh start_app` на admin-машине.

### Parallel Team Strategy

Для фичи такого размера (~80 строк в 3 файлах) — один инженер последовательно. При желании:
- Dev A: US1 (T001, T002) + начало US2 (T003, T004).
- Dev B: US2 продолжение (T005, T006, T007) после T003+T004 от Dev A.

---

## Notes

- **[P] tasks** = разные файлы или независимые методы внутри одного файла, нет зависимостей по результату.
- **[Story] label** мапит задачу на US1 или US2 для трассировки (`contracts/find-parent-same-author.md` ↔ US1, `contracts/apply-auto-album-cover.md` ↔ US2).
- Каждая user story **независимо завершаема и тестируема** через `quickstart.md` (хотя в этой фиче обе поставляются вместе).
- **Тесты не запрашивались** → test-tasks намеренно отсутствуют.
- **Commit** после каждой user story (или после Phase 3 и Phase 4 как logical groups) — облегчает откат.
- **Избегать**: расплывчатых формулировок в задачах; конфликтов при merge (T003/T004 в одном файле `Album.kt` — рекомендуется коммитить вместе); cross-story зависимостей (эта фича их не имеет).
- **Constitution gates**: T008-T009 обеспечивают FR-007 (ktlint baseline не regress'нул); T011 обеспечивает FR-009 (per-feature LiveDoc в том же PR); T006 обеспечивает FR-006 (KDoc с `@see` на новые публичные методы).