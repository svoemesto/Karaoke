---
description: "Task list — Альбомы: клик по ячейке открывает модалку обложки альбома"
---

# Tasks: Альбомы — клик по ячейке открывает модалку обложки альбома

**Input**: Design documents from `/specs/014-album-cell-album-cover-modal/`
**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅, contracts/api.md ✅, quickstart.md ✅
**Tests**: Опционально, не запрошены (см. `AGENTS.md` — тесты в CI отсутствуют; проверка — вручную по `quickstart.md`).

**Organization**: Задачи сгруппированы по user stories (US1 P1 → US2 P2 → US3 P3) для независимой реализации и тестирования. MVP = US1.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: можно выполнять параллельно (разные файлы, нет зависимостей на in-flight таски).
- **[Story]**: лейбл user story (US1/US2/US3) — обязателен для всех фаз после Foundational.
- **Точные пути** в описании.

## Path Conventions

Проект — Web app (`karaoke-app` backend + `webvue3` frontend). Пути в тасках используют абсолютные префиксы репо:

- Backend: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/`
- Frontend: `webvue3/src/components/Albums/` и `webvue3/src/components/Songs/edit/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Проверка, что ветка и инфраструктура готовы.

- [ ] T001 Подтвердить активную git-ветку `014-album-cell-album-cover-modal` через `git branch --show-current` (root `/home/nsa/Karaoke`)
- [ ] T002 [P] Прочитать `AGENTS.md` (секции «Документация и иерархия», «Q&A» — пункты про Jackson-`is`-префикс и `redirectErrorStream`)
- [ ] T003 [P] Прочитать `CONTRIBUTING.md` (стиль Kotlin/Vue/ESLint baseline-файлы — что **нельзя** менять)

**Checkpoint**: окружение + правила известны, можно приступать к фундаменту.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Backend-эндпоинт, без которого ни одна user story не работает. Должен быть готов до начала любой US-фазы.

**⚠️ CRITICAL**: US1, US2, US3 все зависят от `POST /api/albums/firstsongid` — без него фронт не сможет получить `id` песни-контекста для модалки.

- [ ] T004 [P] Добавить helper `fun getFirstSongId(albumId: Long, database: KaraokeConnection): Long?` в файл `/home/nsa/Karaoke/karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Album.kt` (рядом с `countSongsByAlbumIds` строки ~227). SQL: сначала `SELECT id FROM tbl_songs WHERE album_id = ? AND first_song_in_album = TRUE ORDER BY id LIMIT 1`, fallback `SELECT id FROM tbl_songs WHERE album_id = ? ORDER BY id LIMIT 1`. **Обязательно** KDoc с `@see specs/014-album-cell-album-cover-modal/contracts/api.md` (FR-006).
- [ ] T005 Добавить endpoint `apisGetFirstSongIdByAlbumId(@RequestParam albumId: Long): Long` в файл `/home/nsa/Karaoke/karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt` (рядом с `apisAlbumsDigest` строки ~5783, в секции `/api/albums/*`). Возвращает `Album.getFirstSongId(albumId, WORKING_DATABASE) ?: 0L`. **Обязательно** KDoc с `@see specs/014-album-cell-album-cover-modal/contracts/api.md` (FR-006).
- [ ] T006 [P] Проверить, что в `/home/nsa/Karaoke/karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/sync/SyncRegistry.kt` (или `SyncTarget.kt`) новый endpoint **НЕ** добавлен в sync (это read-only lookup, sync не нужен — см. Constitution Principle III).

**Checkpoint**: бэкенд-эндпоинт готов. Можно собирать karaoke-app и проверять `curl -X POST 'http://localhost:8080/api/albums/firstsongid?albumId=<X>'` (должен вернуть `Long` или `0`).

---

## Phase 3: User Story 1 — Клик по preview обложки открывает модалку (Priority: P1) 🎯 MVP

**Goal**: Администратор кликает по preview-обложке в колонке `(альбом)` таблицы `AlbumsTable` — открывается та же модалка `AlbumCoverModal`, что и в `SongEdit.vue` (та же шапка, те же шаги `view` → `searching` → `results` → `cropping`, то же сохранение в `LogoAlbum.png`).

**Independent Test**: Запустить контейнер `karaoke-app` с новым кодом, открыть `/Albums` в `webvue3`, применить фильтр, кликнуть preview `(альбом)` альбома с `songsCount > 0` — модалка открылась с текущей обложкой (или плейсхолдером). Шаги `view`/`searching`/`results`/`cropping` работают как в `SongEdit`. Сохранение обновляет превью в строке таблицы.

### Implementation for User Story 1

- [ ] T007 [P] [US1] Добавить Vuex action `getFirstSongIdByAlbumIdPromise(ctx, albumId)` в файл `/home/nsa/Karaoke/webvue3/src/components/Albums/store.js` (рядом с `loadOneRecord` строки ~49). URL: `POST /api/albums/firstsongid`, params: `{ albumId }`, возвращает `Number(data)`. **Обязательно** JSDoc с `@see specs/014-album-cell-album-cover-modal/contracts/api.md` (FR-006).
- [ ] T008 [P] [US1] Добавить computed `canEditCover(item)` (возвращает `item && item.songsCount > 0`) в `<script>` секцию файла `/home/nsa/Karaoke/webvue3/src/components/Albums/AlbumsTable.vue` (рядом с `showEmptyHint` строки ~213).
- [ ] T009 [P] [US1] Добавить data-поля `isAlbumCoverModalVisible: false`, `prevCurrentSongId: null`, `currentAlbumCoverFirstSongId: null` в `data()` файла `/home/nsa/Karaoke/webvue3/src/components/Albums/AlbumsTable.vue` (строки ~184-198).
- [ ] T010 [P] [US1] Импортировать `AlbumCoverModal` (путь `'../Songs/edit/AlbumCoverModal.vue'`) в `<script>` файла `/home/nsa/Karaoke/webvue3/src/components/Albums/AlbumsTable.vue` (строки ~143-147). Зарегистрировать в `components: { ... }` (строки ~175-182).
- [ ] T011 [P] [US1] Добавить `<AlbumCoverModal v-if="isAlbumCoverModalVisible" @saved="onAlbumCoverSaved" @close="closeAlbumCoverModal" />` в `<template>` файла `/home/nsa/Karaoke/webvue3/src/components/Albums/AlbumsTable.vue` (рядом с `<PictureEditModal>` строки ~9).
- [ ] T012 [US1] Добавить methods `openAlbumCoverModal(item)`, `closeAlbumCoverModal()`, `onAlbumCoverSaved()` в `<script>` файла `/home/nsa/Karaoke/webvue3/src/components/Albums/AlbumsTable.vue` (рядом с `closePictureEdit` строки ~311). Логика — по `contracts/api.md` секция «Methods». `openAlbumCoverModal`: проверка `canEditCover` → сохранить `prevCurrentSongId = getCurrentSongId` → dispatch `getFirstSongIdByAlbumIdPromise` → `setCurrentSongIdOnly(firstSongId)` → `isAlbumCoverModalVisible = true`. `closeAlbumCoverModal`: сбросить `isAlbumCoverModalVisible` → восстановить `setCurrentSongIdOnly(prevCurrentSongId || null)`. `onAlbumCoverSaved`: dispatch `loadOneRecord(albumId)` (для обновления превью).
- [ ] T013 [US1] Изменить `<template #cell(albumPicture)="data">` в файле `/home/nsa/Karaoke/webvue3/src/components/Albums/AlbumsTable.vue` (строки ~59-73): добавить `:class="{ 'is-clickable': canEditCover(data.item) }"`, изменить `:title` на условный («У альбома нет песен — обложка недоступна» если `!canEditCover`), заменить `@click.left="editPicture(...)"` на `@click.left="canEditCover(data.item) && openAlbumCoverModal(data.item)"`. **Поведение** клика по preview — теперь открывает `AlbumCoverModal` вместо `PictureEditModal` (требование пользователя).
- [ ] T014 [P] [US1] Добавить CSS-стили `.fld-picture-preview.is-clickable:hover` и `.is-clickable` (cursor: pointer, лёгкий hover-эффект) в `<style scoped>` файла `/home/nsa/Karaoke/webvue3/src/components/Albums/AlbumsTable.vue` (рядом с `.fld-picture-preview:hover` строки ~615-617). Не ломать существующий hover для `editPicture`-сценария (если он ещё где-то используется).

**Checkpoint**: US1 работает. Клик по preview открывает модалку; сохранение обновляет превью; восстановление `currentSongId` — пока не покрыто (это US3).

---

## Phase 4: User Story 2 — Клик по названию альбома тоже открывает модалку (Priority: P2)

**Goal**: Клик по ячейке в колонке `Название` открывает `AlbumCoverModal` (а не `CustomConfirm` редактирования атрибутов альбома, как было).

**Independent Test**: Кликнуть по ячейке `Название` альбома с `songsCount > 0` — открывается `AlbumCoverModal`; кликнуть по ячейке `Название` альбома с `songsCount = 0` — ничего не происходит, title содержит «У альбома нет песен — обложка недоступна». Клик по другим кликабельным ячейкам (`id`, `authorId`, `year`, `sortOrder`) по-прежнему открывает `CustomConfirm` (regression).

### Implementation for User Story 2

- [ ] T015 [US2] Изменить `<template #cell(name)="data">` в файле `/home/nsa/Karaoke/webvue3/src/components/Albums/AlbumsTable.vue` (строки ~91-93): добавить `:class="{ 'is-clickable': canEditCover(data.item) }"`, изменить `@click.left="changeValue(data.item)"` на `@click.left="canEditCover(data.item) && openAlbumCoverModal(data.item)"`, изменить `:title` на условный. **Изменение поведения**: `changeValue()` для ячейки `name` отключается, но остаётся для `id`/`authorId`/`year`/`sortOrder` (regression).
- [ ] T016 [P] [US2] Добавить CSS `.fld-album-name.is-clickable:hover` (cursor: pointer, существующий hover на `.fld-album-name` уже есть — переопределить/дополнить в файле `/home/nsa/Karaoke/webvue3/src/components/Albums/AlbumsTable.vue` строки ~594-602) — единый стиль кликабельности с preview.

**Checkpoint**: US1 + US2 работают. Клик по двум разным колонкам открывает одну и ту же модалку.

---

## Phase 5: User Story 3 — Восстановление currentSongId после закрытия (Priority: P3)

**Goal**: После открытия/закрытия модалки `AlbumCoverModal` из `/Albums` `currentSongId` в `SongsStore` восстанавливается к прежнему значению (если было) или сбрасывается в `null` (если до клика не было). Защита от потери рабочего контекста администратора (SC-004).

**Independent Test**: В `/Songs` открыть `SongEdit` любой песни (currentSongId устанавливается). Перейти в `/Albums`, кликнуть preview, сохранить новую обложку, закрыть модалку. Вернуться в `/Songs` — открыта та же песня, что и до ухода в `/Albums`.

### Implementation for User Story 3

- [ ] T017 [US3] Проверить, что `openAlbumCoverModal()` (из T012) **уже сохраняет** `prevCurrentSongId` перед подменой и `closeAlbumCoverModal()` **уже восстанавливает** — это покрывает US3. Если что-то пропущено (например, обработка ошибки dispatch) — добавить `try/finally` для гарантированного восстановления в файле `/home/nsa/Karaoke/webvue3/src/components/Albums/AlbumsTable.vue` (метод `openAlbumCoverModal`, рядом со строкой T012).
- [ ] T018 [P] [US3] Проверить в `quickstart.md` сценарий 5 (восстановление `currentSongId`) и убедиться, что он **выполняется** после реализации. Если сценарий не сходится — откорректировать реализацию в `/home/nsa/Karaoke/webvue3/src/components/Albums/AlbumsTable.vue` или `quickstart.md` соответственно.

**Checkpoint**: US1 + US2 + US3 работают. Контекст администратора не теряется.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Линт, документация, per-feature чек, финальная валидация.

- [ ] T019 [P] Запустить `./gradlew ktlintCheck` из `/home/nsa/Karaoke` — все проверки должны быть зелёными. Если упало на **новом** коде (helper `getFirstSongId` или endpoint `apisGetFirstSongIdByAlbumId`) — исправить, baseline-файл НЕ редактировать.
- [ ] T020 [P] Запустить `cd /home/nsa/Karaoke/webvue3 && npm run lint:check` — все проверки зелёные. Если упало на **новом** коде в `AlbumsTable.vue` или `Albums/store.js` — исправить, `.eslint-baseline.json` НЕ редактировать.
- [ ] T021 [P] Проверить KDoc-покрытие: `bash /home/nsa/Karaoke/tools/check-kdoc-coverage.sh` (FR-006). Новый helper `getFirstSongId` и endpoint `apisGetFirstSongIdByAlbumId` **MUST** иметь KDoc.
- [ ] T022 [P] Проверить JSDoc-покрытие: `bash /home/nsa/Karaoke/tools/check-jsdoc-coverage.sh webvue3` (FR-006). Новый action `getFirstSongIdByAlbumIdPromise` **MUST** иметь JSDoc.
- [ ] T023 Добавить запись в `/home/nsa/Karaoke/docs/architecture-notes.md` — Pass 27 (или следующий): короткое описание PR (новый endpoint, новый UI-flow), дата, ссылка на `specs/014-album-cell-album-cover-modal/`. Это **обязательно** для traceability (FR-009 + конституция v1.2.0).
- [ ] T024 [P] Обновить `.specify/feature.json` после `git commit`/merge — `feature_directory` остаётся `specs/014-album-cell-album-cover-modal` (уже стоит с момента /speckit.specify), но **проверить**, что файл коммитится в ветке `014-album-cell-album-cover-modal`, а не в master.
- [ ] T025 Прогнать pre-commit вручную: `cd /home/nsa/Karaoke && pre-commit run --all-files` (все 7 проверок должны быть зелёными).
- [ ] T026 [P] Проверить, что **`AlbumCoverModal.vue` НЕ изменён** (`git diff --stat 014-album-cell-album-cover-modal~ -- webvue3/src/components/Songs/edit/AlbumCoverModal.vue` должен быть пустым — это инвариант требования пользователя «такая же модалка»).
- [ ] T027 [P] Проверить, что **`Song.kt` / `Picture.kt` / `Pictures.kt` / `SyncRegistry` НЕ изменены** (`git diff --stat 014-album-cell-album-cover-modal~ -- karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt ...`). Это инвариант из research.md «Что НЕ меняется».
- [ ] T028 Вручную выполнить сценарии 1-7 из `/home/nsa/Karaoke/specs/014-album-cell-album-cover-modal/quickstart.md` после деплоя на admin-машину (только пользователь, не агент). Пометить таску ✅ после успешного прохождения.
- [ ] T029 Создать PR из ветки `014-album-cell-album-cover-modal` в `master` (только по явному запросу пользователя — см. `AGENTS.md` «Git — не коммитить без явного запроса»).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: T001 (проверка ветки) — должен быть первым. T002, T003 (чтение правил) — могут идти параллельно.
- **Foundational (Phase 2)**: T004, T005 (backend) → T006 (verify) после них. **T004 + T005 [P]** — параллельно в разных файлах. T006 (sync check) — после T005, чтобы убедиться, что endpoint не попал в sync случайно.
- **User Story 1 (Phase 3)**: T007-T011 (frontend state + action + template + import) — все [P], параллельно. **T012 (openAlbumCoverModal methods) — после T007, T009, T011**, потому что использует их. T013, T014 (template + CSS) — после T012 (нужен метод `openAlbumCoverModal`).
- **User Story 2 (Phase 4)**: T015 (template), T016 (CSS) — T016 [P], T015 после T012 (нужен `openAlbumCoverModal`).
- **User Story 3 (Phase 5)**: T017, T018 — верификация уже сделанного в T012.
- **Polish (Phase 6)**: все таски после всех US-фаз. T019, T020, T021, T022, T023, T024, T025, T026, T027 — [P] между собой. T028, T029 — последовательно.

### User Story Dependencies

- **US1 (P1)**: начинается после Phase 2 (нужен endpoint). Зависит только от T004-T006.
- **US2 (P2)**: начинается после US1.T012 (нужен метод `openAlbumCoverModal` в AlbumsTable). Не нуждается в дополнительном бэкенде.
- **US3 (P3)**: начинается после US1.T012 (логика восстановления живёт в `openAlbumCoverModal`/`closeAlbumCoverModal`). Не нуждается в дополнительном бэкенде.

**MVP = US1.** US2 и US3 — улучшения; без них фича уже «работает» (превью открывает модалку, обложка сохраняется, превью обновляется), просто без удобства (только preview, без восстановления контекста).

### Within Each User Story

- Computed/state (T008, T009) до methods (T012) — methods их читают.
- Action в store (T007) до methods (T012) — methods его дёргают.
- Import + register component (T010) до template (T011) — template рендерит компонент.
- Methods (T012) до cell-handler в template (T013) — handler вызывает метод.

### Parallel Opportunities

- **Phase 1 Setup**: T002 || T003.
- **Phase 2 Foundational**: T004 || T005. T006 после T005.
- **Phase 3 US1**: T007 || T008 || T009 || T010 || T011. T012 после T007+T009. T013, T014 || между собой, но после T012.
- **Phase 4 US2**: T015 после T012. T016 || T015.
- **Phase 5 US3**: T017 || T018.
- **Phase 6 Polish**: T019 || T020 || T021 || T022 || T023 || T024 || T025 || T026 || T027. T028 после T019-T027. T029 после T028.

---

## Parallel Example: User Story 1

```bash
# Phase 3, US1 — запуск в одной пачке (разные файлы, нет зависимостей):
Task: "T007 [P] [US1] Vuex action в webvue3/src/components/Albums/store.js"
Task: "T008 [P] [US1] computed canEditCover в webvue3/src/components/Albums/AlbumsTable.vue (script)"
Task: "T009 [P] [US1] data-поля в webvue3/src/components/Albums/AlbumsTable.vue (script)"
Task: "T010 [P] [US1] import AlbumCoverModal в webvue3/src/components/Albums/AlbumsTable.vue (script)"
Task: "T011 [P] [US1] <AlbumCoverModal> в webvue3/src/components/Albums/AlbumsTable.vue (template)"

# Затем (после T007+T009):
Task: "T012 [US1] methods open/close/onSaved в webvue3/src/components/Albums/AlbumsTable.vue (script)"

# Затем (после T012), параллельно:
Task: "T013 [US1] cell(albumPicture) handler в webvue3/src/components/Albums/AlbumsTable.vue (template)"
Task: "T014 [P] [US1] CSS .is-clickable в webvue3/src/components/Albums/AlbumsTable.vue (style)"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. ✅ Phase 1: Setup (3 таски, ~5 мин)
2. ✅ Phase 2: Foundational (3 таски, ~15 мин — бэкенд endpoint + helper)
3. ✅ Phase 3: User Story 1 (8 тасок, ~30 мин — фронт)
4. 🛑 **STOP и VALIDATE**: вручную сценарии 1, 2, 4, 6 из `quickstart.md` (MVP готов)
5. Деплой на admin-машину (`bash deploy/do.sh build_app && build_start_web` — пользователь)

### Incremental Delivery

1. Phase 1 + Phase 2 → backend endpoint работает (curl-проверка)
2. + Phase 3 → MVP (US1): preview клик → модалка → сохранение
3. + Phase 4 → US2: ещё и название кликабельно
4. + Phase 5 → US3: гарантия восстановления `currentSongId`
5. + Phase 6 → Polish (lint, JSDoc, docs)

### Parallel Team Strategy

Для одного разработчика — последовательно. Для нескольких:

- **Dev A** (backend): Phase 2 целиком (T004-T006).
- **Dev B** (frontend): ждёт Phase 2 → Phase 3 (US1) → Phase 4 (US2) → Phase 5 (US3).
- **Dev C** (docs): Phase 6.T023 (architecture-notes) может стартовать сразу, как только PR-описание ясно.

Но в Karaoke — один разработчик + один AI-агент, так что последовательно.

---

## Notes

- **MVP = US1**: ~3 файла (Album.kt, ApiController.kt, AlbumsTable.vue) + 1 store action. Без US2/US3 фича уже полезна.
- **Без тестов**: проект не использует CI-тесты (см. `AGENTS.md`). Проверка — пользователем по `quickstart.md` сценарии 1-7.
- **Без `git commit`**: только по явному запросу пользователя. PR (T029) — только после явного «создай PR».
- **Без деплоя**: `bash deploy/do.sh build_app` и `build_start_web` запускает только пользователь (см. constitution Principle V/«Ограничения и доступы агента»).
- **Инварианты** (проверяются в T026-T027): `AlbumCoverModal.vue`, `Song.kt`, `Picture.kt`, `Pictures.kt`, `SyncRegistry` НЕ изменяются.
- **KDoc/JSDoc обязательны** (FR-006): на helper, endpoint, action. Не забыть — иначе упадёт check-kdoc-coverage.sh.
- **Не менять baseline-файлы** линтеров: `config/ktlint/baseline-*.xml`, `webvue3/.eslint-baseline.json` — только править код.
