---

description: "Task list template for feature implementation"
---

# Tasks: Доп. поля Author/Album/Song (Описание/Короткое описание/Предупреждение) + новый UI Закромов

**Input**: Design documents from `/specs/012-entity-description-fields/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/api.md, quickstart.md (все присутствуют)

**Tests**: В проекте нет CI-тестового слоя для этой части стека (см. plan.md
Technical Context) — тестовые задачи не генерируются; проверка — через
`quickstart.md` (Полировка, последняя задача).

**Organization**: Задачи сгруппированы по user story из spec.md (US1-US6, в
порядке приоритета P1→P1→P2→P2→P3→P3).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Можно выполнять параллельно (разные файлы, нет зависимости от незавершённых задач)
- **[Story]**: К какой user story относится задача (US1...US6)
- Пути указаны точные, от корня репозитория

## Path Conventions

Web-приложение, двух-фронтенд (Принцип V конституции):
- Backend: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/...` (ядро),
  `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/...` (публичный API)
- Admin frontend: `webvue3/src/components/...`
- Public frontend: `karaoke-public/src/views/...`
- Миграции: `deploy/karaoke-db/...`

---

## Phase 1: Setup

**Purpose**: Схема БД для всех трёх сущностей — предпосылка для абсолютно всего остального.

- [X] T001 Создать миграцию `deploy/karaoke-db/31_entity_description_fields.sql`: `ALTER TABLE tbl_authors/tbl_albums/tbl_songs ADD COLUMN IF NOT EXISTS description TEXT, short_description VARCHAR(255), warning VARCHAR(255) DEFAULT ''` (везде `description` — именно `TEXT`, не `VARCHAR`, см. data-model.md) для всех трёх таблиц; `CREATE OR REPLACE FUNCTION update_tbl_authors_recordhash()/update_tbl_albums_recordhash()/update_tbl_songs_recordhash()`, включив 3 новые колонки в конец md5-конкатенации каждой (по образцу `27_author_special_order.sql`/`29_albums.sql`); backfill `UPDATE ... SET recordhash = md5(...)` для существующих строк всех трёх таблиц. Применить вручную на LOCAL (PROD — только по согласованию с пользователем).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Поля должны существовать в entity-слое и DTO раньше, чем к ним обратится любая user story (admin-редактирование ИЛИ публичное отображение).

**⚠️ CRITICAL**: Ни одна user story не может стартовать до завершения этой фазы.

- [X] T002 [P] Добавить `description`, `shortDescription`, `warning` (`@KaraokeDbTableField`, default `""`) в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Author.kt`, прокинуть в `toDTO()`
- [X] T003 [P] Добавить `description`, `shortDescription`, `warning: String = ""` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/AuthorDTO.kt`, прокинуть в `fromDto()`
- [X] T004 [P] Добавить `description`, `shortDescription`, `warning` (`@KaraokeDbTableField`, default `""`) в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Album.kt`, прокинуть в `toDTO()`
- [X] T005 [P] Добавить `description`, `shortDescription`, `warning: String = ""` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/AlbumDTO.kt`, прокинуть в `fromDto()`
- [X] T006 Добавить `DESCRIPTION`, `SHORT_DESCRIPTION`, `WARNING` в `enum class SongField` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SongField.kt` (рядом с `FORMATTED_TEXT_*`, строки 55-57)
- [X] T007 В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt` добавить свойства-обёртки `description`/`shortDescription`/`warning` (по образцу `formattedTextSong`, строки 164-188), прокинуть в INSERT field list (~5489), `getDiff()` (~6386-6399), загрузку из `ResultSet` (~7314-7316), `toDTO()` (~7838-7840) — зависит от T006
- [X] T008 [P] Добавить `description`, `shortDescription`, `warning: String = ""` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SongDTO.kt` — зависит от T007. По факту также добавлено в `SongDTOdigest.kt`/`toDtoDigest()` — обнаружилось, что digest уже переносит `formattedTextSong/Tabs/Chords` (не облегчённая проекция, как предполагало data-model.md), поэтому для консистентности новые поля добавлены туда же

**Checkpoint**: Слой сущностей/DTO готов для всех трёх сущностей — можно начинать любую user story.

---

## Phase 3: User Story 1 - Редактор заполняет описательные поля (Priority: P1) 🎯 MVP

**Goal**: Администратор может увидеть и отредактировать 9 новых полей (3 сущности × 3 поля) в webvue3.

**Independent Test**: quickstart.md, Сценарий 1 — заполнить все 9 полей для одного автора/альбома/песни, перезагрузить страницу админки, убедиться что значения сохранились.

### Implementation for User Story 1

- [X] T009 [P] [US1] Добавить ветку `fldIsTextarea` в `webvue3/src/components/Common/CustomConfirm.vue` (рендерит `<textarea v-model="fld.fldValue">` вместо однострочного `<input>`, когда `fld.fldIsTextarea === true`, рядом с уже существующими ветками `fldIsBoolean`/`fldIsSelect`, строки ~10-52)
- [X] T010 [US1] Добавить поля `description` (`fldIsTextarea: true`), `shortDescription`, `warning` в массив `fields` метода `changeValue()` в `webvue3/src/components/Authors/AuthorsTable.vue` — зависит от T009. Проверка подтвердила U1: `apisUpdateAuthor` (`ApiController.kt`, karaoke-app) использует явный allow-list `@RequestParam`, а НЕ generic pass-through — добавлены `description`/`shortDescription`/`warning` как новые `@RequestParam`
- [X] T011 [US1] Добавить поля `description` (`fldIsTextarea: true`), `shortDescription`, `warning` в массивы `fields` методов `changeValue()`/`createAlbum()` в `webvue3/src/components/Albums/AlbumsTable.vue` — зависит от T009. Аналогично T010: `apisUpdateAlbum`/`apisCreateAlbum` (`ApiController.kt`) доработаны новыми `@RequestParam`
- [X] T012 [US1] Добавить `<textarea v-model="song.description">`, `<input v-model="song.shortDescription">`, `<input v-model="song.warning">` рядом с полями тональности/темпа в `webvue3/src/components/Songs/edit/SongEdit.vue` (~строки 234-267), с undo-кнопками по образцу соседних полей
- [X] T013 [US1] ~~Добавить `description`/`shortDescription`/`warning` в diff/`params`-сборку `saveSong()`~~ — не потребовалось: `getSongDiff`/`executeSave()`/`saveSong` в `webvue3/src/components/Songs/store.js` и `SongEdit.vue` полностью универсальны (диффят и шлют ЛЮБЫЕ изменившиеся ключи `currentSong` через `Object.keys()`, без статического списка полей) — новые поля подхватились автоматически, как только появились в `SongDTO` (T008) и в UI (T012). Контроллер `POST /api/song/update` (`ApiController.songs2Update`, karaoke-app) доработан: добавлены `@RequestParam` `description`/`shortDescription`/`warning` (см. примечание к T010 — allow-list, не generic pass-through, как предполагалось изначально)

**Checkpoint**: US1 полностью функциональна и тестируема независимо (quickstart Сценарий 1).

---

## Phase 4: User Story 2 - Предупреждения/описания автора и альбома на Закромах (Priority: P1)

**Goal**: Посетитель видит предупреждение (красным, над именем/названием), короткое описание (серым, после имени/названия) и описание (в тултипе) для автора и для каждого альбома на странице Закромов; тип альбома под названием соответствует связанной сущности Album.

**Independent Test**: quickstart.md, Сценарий 2.

### Implementation for User Story 2

- [X] T014 [US2] В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Zakroma.kt`: добавить поля `authorDescription`, `authorShortDescription`, `authorWarning` в класс `Zakroma` (~строка 182) и `description`, `shortDescription`, `warning`, `albumTypeLabel` (= `AlbumType.fromDb(albumType).description`) в класс `ZakromaAlbum` (~строка 245) — зависит от Foundational (T002-T005); используется в T015
- [X] T015 [US2] В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Zakroma.kt` `buildFromSettings()`: подгрузить `Author` по имени и скопировать `description`/`shortDescription`/`warning` на `Zakroma`; при резолве связанного `Album` (существующий блок ~119-137) дополнительно скопировать его `description`/`shortDescription`/`warning` и **переопределить** `album.albumName` значением `linkedAlbum.name` (а не оставлять свободнотекстовое имя из группировки песен) — зависит от T014 (поля уже должны существовать в классах)
- [X] T016 [US2] В `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/ZakromaPublicDto.kt`: добавить `authorDescription`/`authorShortDescription`/`authorWarning` в `ZakromaPublicDto` и `description`/`shortDescription`/`warning`/`albumTypeLabel` в `ZakromaAlbumPublicDto`, прокинуть все поля в `fromZakroma()` — зависит от T014, T015
- [X] T017 [P] [US2] В блоке автора (`.km-author-header`, ~строки 124-139) `karaoke-public/src/views/ZakromaView.vue`: отрендерить `authorWarning` красным над именем, `authorShortDescription` серым через пробел после имени, `title`-атрибут с `authorDescription` на имени — зависит от T016
- [X] T018 [P] [US2] В блоке альбома (`.km-album-header`, ~строки 143-157) `karaoke-public/src/views/ZakromaView.vue`: отрендерить `warning` красным над названием, `shortDescription` серым после названия, `title`-атрибут с `description`; заменить текущую рассинхронизированную локальную RU-мапу типов альбома (~строка 450, `albumTypeLabel()`) на значение `albumTypeLabel` из DTO; **дополнительно убрать/пересмотреть условие `v-if="alb.albumType && alb.albumType !== 'studio'"` (~строка 151)** — по FR-017 тип MUST отображаться для всех 5 типов, включая «Студийный альбом» (сейчас бейдж для studio скрыт целиком) — зависит от T016

**Checkpoint**: US2 полностью функциональна и тестируема независимо поверх данных US1 (quickstart Сценарий 2).

---

## Phase 5: User Story 3 - Описательные поля песни в карточке песни (Priority: P2)

**Goal**: Посетитель видит предупреждение/короткое описание/описание песни в информационном блоке страницы песни (рядом с тональностью/темпом).

**Independent Test**: quickstart.md, Сценарий 3.

### Implementation for User Story 3

- [X] T019 [US3] В `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/SongPublicDto.kt`: добавить `description`/`shortDescription`/`warning`, прокинуть в `fromSettings()` за флагом `includeDetails` (по образцу `formattedTextSong`, ~строка 102) — зависит от Foundational (T006-T008)
- [X] T020 [US3] В `.km-meta-card`/`.km-meta-grid` (~строки 77-116) `karaoke-public/src/views/SongView.vue`: добавить `.km-meta-item` для `warning` (красным), `shortDescription` (серым, рядом с названием песни), `title`-тултип с `description` — зависит от T019

**Checkpoint**: US3 функциональна независимо (quickstart Сценарий 3).

---

## Phase 6: User Story 4 - Альбомы в порядке поля сортировки, Album как источник истины (Priority: P2)

**Goal**: Альбомы автора на Закромах идут строго по `Album.sortOrder`; для ещё не связанных альбомов сохраняется прежний фолбэк (год/название).

**Independent Test**: quickstart.md, Сценарий 4.

### Implementation for User Story 4

- [X] T021 [US4] Проверено: `ZakromaAlbum.compareTo` (`Zakroma.kt`) не менялся T014/T015 и по-прежнему сортирует по `sortOrder`→`year`→`albumName` (сентинел `Int.MAX_VALUE` для несвязанных альбомов); `ZakromaPublicDto.fromZakroma()` — чистый `.map` без пересортировки; `PublicApiController.zakroma()` возвращает результат `fromZakroma()` напрямую (`return ZakromaPublicDto.fromZakroma(zakroma)`, строка 195) без доп. сортировки. Порядок сохраняется end-to-end, правок не потребовалось

**Checkpoint**: US4 тестируема независимо; вместе с US2 Закрома полностью источником истины использует сущность `Album`.

---

## Phase 7: User Story 5 - Переключатель сквозной/групповой (Priority: P3)

**Goal**: Заголовок Закромов получает переключатель отображения альбомов; в групповом режиме — разделы с заголовками по типу в фиксированном порядке.

**Independent Test**: quickstart.md, Сценарий 5.

### Implementation for User Story 5

- [X] T022 [US5] В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/AlbumType.kt`: добавить поля `groupLabel` («Студийные альбомы», «Синглы», «Концертные альбомы», «Сборники», «Бутлеги») и `filterLabel` («Студийные», «Синглы», «Концертные», «Сборники», «Бутлеги») к каждой константе; добавить `companion object` константу `ZAKROMA_GROUP_ORDER: List<AlbumType>` = `[STUDIO, SINGLE, LIVE, COMPILATION, BOOTLEG]`
- [X] T023 [US5] Реализовано в `ZakromaPublicDto.kt` (`fromZakroma()`, groupingBy/eachCount по уже присланному `zak.albums`) вместо отдельной функции в `Zakroma.kt` — проще и не требует лишнего прохода по данным в karaoke-app. Посчитать `albumTypeCounts: List<AlbumTypeSummaryDto>` (новый класс: `dbValue`, `groupLabel`, `filterLabel`, `count`) по списку альбомов автора, только типы с `count > 0`, в порядке `AlbumType.ZAKROMA_GROUP_ORDER`; прокинуть в `ZakromaPublicDto` — зависит от T022
- [X] T024 [US5] В шапке `karaoke-public/src/views/ZakromaView.vue`: добавить переключатель «сквозной/по типу» (переиспользовать визуальный паттерн `.km-theme-toggle`, ~строки 12-34/517-587), логику группировки альбомов по `albumType` в порядке `albumTypeCounts`, рендер заголовка-разделителя (`groupLabel`) перед каждой непустой группой, персистентность выбора в `localStorage` — зависит от T023

**Checkpoint**: US5 тестируема независимо (quickstart Сценарий 5).

---

## Phase 8: User Story 6 - Быстрые фильтры по типу альбома (Priority: P3)

**Goal**: Кнопки быстрого фильтра по типу альбома со счётчиком, скрыты для отсутствующих типов, скрывают/показывают альбомы в обоих режимах отображения.

**Independent Test**: quickstart.md, Сценарий 6.

### Implementation for User Story 6

- [X] T025 [US6] Реализовано вместе с T024 (общий блок `.km-album-controls-bar`): кнопки быстрого фильтра из `zakromaAlbumTypeCounts` (подпись `"${filterLabel} (${count})"`), состояние `hiddenAlbumTypes` (Set, persist в `localStorage` ключ `km-zakroma-hidden-album-types`), применяется в `visibleAlbums()`/`albumRenderItems()` независимо от `albumDisplayMode`

**Checkpoint**: Все 6 user stories независимо функциональны.

---

## Phase 9: Polish & Cross-Cutting Concerns

- [X] T026 [P] Обновить `docs/features/dual-db-sync.md`: зафиксировать, что `tbl_authors`/`tbl_albums`/`tbl_songs` получили 3 новые колонки и пересобранные recordhash-триггеры (per-feature-doc FR-009 конституции — не локальный FR-009 этой спеки, см. Constitution Check плана)
- [X] T027 [P] Прогнать `bash tools/check-kdoc-coverage.sh` — добавить KDoc с `@see specs/012-entity-description-fields/spec.md` к новым публичным символам (`AlbumTypeSummaryDto`, новые поля `AlbumType`, новые свойства сущностей)
- [X] T028 [P] Прогнать `bash tools/check-jsdoc-coverage.sh webvue3` и `bash tools/check-jsdoc-coverage.sh karaoke-public` — добавить JSDoc к новым/изменённым компонентам
- [X] T029 Прогнать `./gradlew ktlintCheck`, `cd webvue3 && npm run lint:check && npx prettier --check "src/**/*.{vue,js,ts,json}"`, `cd karaoke-public && npm run lint:check && npx prettier --check "src/**/*.{vue,js,ts,json}"` (обязательный чек-лист перед коммитом, CLAUDE.md)
- [ ] T030 Вручную пройти все 6 сценариев `quickstart.md` end-to-end в браузере на LOCAL-окружении

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: без зависимостей — можно начинать сразу
- **Foundational (Phase 2)**: зависит от Setup (T001) — БЛОКИРУЕТ все user stories
- **User Stories (Phase 3-8)**: все зависят от завершения Foundational
  - US1, US2 (P1) — независимы друг от друга (US2 не требует, чтобы админ УЖЕ заполнил поля через UI из US1 — можно проверить, поставив значения напрямую в БД)
  - US3, US4 (P2) — независимы от US1/US2/друг друга (используют тот же Foundational-слой)
  - US5, US6 (P3) — зависят от T014/T023 (данные `albumTypeCounts`), не зависят от US1-US4 по функциональности, но физически расширяют тот же файл `ZakromaView.vue`, что и US2 (T017/T018) — рекомендуется реализовывать после US2 во избежание конфликтов слияния в одном файле
- **Polish (Phase 9)**: после всех выбранных user stories

### Within Each User Story

- Backend-модель → Backend-DTO → Frontend (внутри US2/US3: entity/Zakroma-слой → publicDTO → Vue-компонент)
- US5 перед US6 логически (US6 переиспользует `albumTypeCounts` из T023), хотя формально можно делать параллельно после T023

### Parallel Opportunities

- Foundational: T002+T003 (Author), T004+T005 (Album) — разные файлы, можно параллельно; T006→T007→T008 (Song) — последовательно (один файл/зависимость)
- US1: T009 сначала (блокирует T010/T011); T010, T011, T012 — разные файлы, параллельно после T009
- US2: T017, T018 — разные блоки одного файла `ZakromaView.vue`, можно писать параллельно (не пересекаются секциями), но мержить осторожно
- US5/US6: T025 может начинаться сразу после T023, не дожидаясь T024

---

## Parallel Example: Foundational Phase

```bash
# Автор и Альбом — независимые файлы, параллельно:
Task: "T002 Author.kt — description/shortDescription/warning"
Task: "T003 AuthorDTO.kt — description/shortDescription/warning"
Task: "T004 Album.kt — description/shortDescription/warning"
Task: "T005 AlbumDTO.kt — description/shortDescription/warning"
```

## Parallel Example: User Story 1

```bash
Task: "T010 AuthorsTable.vue — новые поля в changeValue()"
Task: "T011 AlbumsTable.vue — новые поля в changeValue()/createAlbum()"
Task: "T012 SongEdit.vue — новые v-model поля"
# (после завершения T009)
```

---

## Implementation Strategy

### MVP First (User Story 1 + User Story 2)

1. Phase 1: Setup (T001)
2. Phase 2: Foundational (T002-T008) — КРИТИЧНО, блокирует всё
3. Phase 3: US1 (T009-T013) — админка может заполнять поля
4. Phase 4: US2 (T014-T018) — посетитель видит предупреждения на Закромах
5. **STOP и проверить**: quickstart Сценарии 1-2 — это уже самостоятельная ценность (юридически значимые предупреждения видны на сайте)

### Incremental Delivery

1. Setup + Foundational → готова основа
2. US1 → US2 → проверить/задеплоить (MVP: предупреждения работают)
3. US3 → та же ценность для страницы песни
4. US4 → корректный порядок альбомов (может идти параллельно с US3)
5. US5 → US6 → удобство навигации по большой дискографии (наименее критичная часть)

### Independent Test Criteria (для каждой истории — из spec.md)

- US1: quickstart Сценарий 1 (сохранение 9 полей в админке)
- US2: quickstart Сценарий 2 (визуальные правила автора/альбома на Закромах)
- US3: quickstart Сценарий 3 (карточка песни)
- US4: quickstart Сценарий 4 (порядок альбомов по sortOrder)
- US5: quickstart Сценарий 5 (переключатель режима + разделители групп)
- US6: quickstart Сценарий 6 (быстрые фильтры со счётчиком)

## Notes

- [P]-задачи = разные файлы, нет зависимостей между ними
- [Story]-метка — трассируемость к конкретной user story
- Каждая user story должна быть независимо завершаемой и тестируемой
- Коммитить после каждой завершённой задачи или логической группы (только по явному запросу пользователя — см. CLAUDE.md workflow)
- Избегать: расплывчатых формулировок задач, конфликтов на одном файле без явного указания порядка, кросс-стори зависимостей, ломающих независимость
