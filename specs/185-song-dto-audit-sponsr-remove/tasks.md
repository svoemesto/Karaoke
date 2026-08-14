# Tasks: Аудит публичного DTO песни и удаление ссылки на Sponsr

**Input**: Design documents from `/specs/185-song-dto-audit-sponsr-remove/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: Не запрошены (в проекте нет CI-тестов для этого уровня, см. AGENTS.md «Тесты»).

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

---

## Phase 1: Setup

**Purpose**: Подтвердить рабочее окружение перед правкой кода.

- [x] T001 Подтвердить branch = `185-song-dto-audit-sponsr-remove` через `git branch --show-current`; `git status` без untracked `.env`/секретов; рабочий каталог — `/home/nsa/Karaoke`.

---

## Phase 2: User Story 1 — Vue SPA без иконки Sponsr (Priority: P1) 🎯 MVP

**Goal**: Гость на `/zakroma` и `/search` больше не видит иконку Sponsr в таблицах песен (FR-001, FR-002, FR-003).

**Independent Test**: Открыть `https://sm-karaoke.ru/zakroma?author=КИНО` в браузере, проверить отсутствие `<a href="https://sponsr.ru/...">` в строках песен. То же — для `/search?q=...`.

### Implementation for User Story 1

- [x] T002 [P] [US1] Удалить 3 вхождения `<PlatformLink link-name="sponsr" :link-value="sett.linkSponsrPlay" :song-id="sett.id" song-version="all" />` (desktop `<td>` + mobile `<div>` в карточке) в `karaoke-public/src/views/ZakromaView.vue` (строки 319-326 и 354-360).
- [x] T003 [P] [US1] Удалить 3 вхождения того же блока в `karaoke-public/src/views/SearchView.vue` (строки 140-147 и 183-189).
- [x] T004 [US1] Пересчитать `<col style="width: Npx">` в `karaoke-public/src/views/ZakromaView.vue` после удаления колонки: явная ширина для каждой оставшейся колонки, `table-layout: fixed` требует явной `width` (см. AGENTS.md «Таблицы karaoke-public»).
- [x] T005 [US1] Пересчитать `<col style="width: Npx">` в `karaoke-public/src/views/SearchView.vue` аналогично.
- [x] T006 [US1] Собрать публичный SPA локально: `cd karaoke-public && npm run build` (должен завершиться без ошибок и предупреждений о неиспользуемых `<col>`).

**Checkpoint**: User Story 1 функционально завершён — иконка Sponsr не рендерится во Vue SPA. JSON от бэка пока ещё содержит `linkSponsrPlay` (это будет исправлено в US2).

---

## Phase 3: User Story 2 — Аудит публичного DTO (Priority: P1)

**Goal**: Backend-ответы `/api/public/zakroma`, `/api/public/songs`, `/api/public/song/{id}`, `/api/public/zakroma/stream` содержат только нужные поля; payload сокращается на ≥80% (SC-001).

**Independent Test**: `curl https://sm-karaoke.ru/api/public/zakroma?author=КИНО | jq '.songs[0] | keys'` — ключи НЕ должны содержать `linkSponsrPlay`, `linkBoosty`, `linkDzen*`, `linkVk*`, `linkTg*`, `linkMax*`, `linkPl*`, `sponsrLinkGeneral`, `vkPictureBase64`, `idStatus`, `authorAlias`. Размер ответа ≤ 8 KB (SC-001).

### Implementation for User Story 2

- [x] T007 [P] [US2] Удалить 26 полей из `SongPublicDto` (21 ссылка + 5 служебных: `sponsrLinkGeneral`, `haveVkGroupLink`, `vkPictureBase64`, `idStatus`, `linkSponsrPlay`, `linkBoostyTxt`) в `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/SongPublicDto.kt`.
- [x] T008 [P] [US2] Поправить `SongPublicDto.fromSong()`: удалить инициализацию удалённых полей в том же файле.
- [x] T009 [P] [US2] Удалить 21 поле ссылок (`linkBoosty`, `linkSponsrPlay`, `linkDzen*`, `linkVk*`, `linkTg*`, `linkPl*`, `linkMax*`) из `ZakromaAlbumSongPublicDto` в `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/ZakromaPublicDto.kt`.
- [x] T010 [P] [US2] Поправить `ZakromaAlbumSongPublicDto.fromZakroma()`: убрать обращения к удалённым полям в том же файле.
- [x] T011 [P] [US2] Удалить 21 параметр из inline-конструктора `ZakromaAlbumSongPublicDto` в `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt` (метод `zakromaStream`, ~строки 344-389).
- [x] T012 [P] [US2] Удалить 21 `var`-поле из `ZakromaAlbumSong` (~строки 265-285) и 21 строку сборщика `zakromaAlbumSong.linkXxx = song.linkXxx` (~строки 207-225) в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Zakroma.kt`.
- [x] T013 [US2] Собрать backend локально: `./gradlew clean :karaoke-app:compileKotlin :karaoke-web:compileKotlin` (без ошибок компиляции).

**Checkpoint**: User Story 2 функционально завершён — backend-ответ содержит только нужные поля. Payload `/api/public/zakroma?author=КИНО` ≤ 8 KB (SC-001).

---

## Phase 4: User Story 3 — Legacy Thymeleaf-сайт (Priority: P2)

**Goal**: Старый сайт на Thymeleaf (`filter.html`, `zakroma.html`, `testpage.html`) тоже очищен от блоков ссылок на соцсети — консистентно с новым SPA (Q1=B, FR-009..FR-011).

**Independent Test**: Открыть `https://sm-karaoke.ru/zakroma` (legacy) — в строке песни нет блока с иконками платформ соцсетей. То же для `/filter`, `/testpage`.

### Implementation for User Story 3

- [x] T014 [P] [US3] Удалить блоки ссылок `<a th:linkValue="${sett.linkSponsrPlay}">`, `<a th:linkValue="${sett.linkDzen*}">`, `<a th:linkValue="${sett.linkVk*}">`, `<a th:linkValue="${sett.linkTg*}">`, `<a th:linkValue="${sett.linkMax*}">`, `<a th:linkValue="${sett.linkPl*}">`, `<a th:linkValue="${sett.linkBoosty}">` + соответствующие `<col>`/`<th>` в `karaoke-web/src/main/resources/templates/filter.html`.
- [x] T015 [P] [US3] То же в `karaoke-web/src/main/resources/templates/zakroma.html`.
- [x] T016 [P] [US3] То же в `karaoke-web/src/main/resources/templates/testpage.html`. **ОСТАВИТЬ** `${sett.idStatus}` и `${sett.haveVkGroupLink}` на ~строке 300 (нужны для логики отображения картинки, см. plan.md «Constraints»).
- [x] T017 [US3] Пересобрать karaoke-web локально: `./gradlew :karaoke-web:bootJar` (должен пересобраться без ошибок Thymeleaf-шаблонов).

**Checkpoint**: User Story 3 функционально завершён — legacy Thymeleaf-сайт консистентен с новым SPA.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Линтеры, регрессионная проверка, документация, PR.

- [x] T018 [P] Запустить `./gradlew ktlintCheck` — baseline не должен вырасти (FR-007).
- [x] T019 [P] Запустить `cd karaoke-public && npm run lint:check && bash ../../tools/check-eslint-baseline.sh` — baseline не должен вырасти.
- [x] T020 [P] Запустить `bash tools/check-kdoc-coverage.sh` — должно оставаться 100%.
- [ ] T021 Выполнить ручную проверку по `specs/185-song-dto-audit-sponsr-remove/quickstart.md` — все SC-001..SC-007 должны пройти (curl + jq, Vue SPA в браузере, Thymeleaf в браузере, webvue3 в браузере, боты-проверка, DevTools, линтеры). Запустить после деплоя на прод.
- [x] T022 [P] Проверить `git status` (нет untracked `.env`/секретов/мусорных файлов); `git diff --stat` показывает только ожидаемые правки в 9 файлах: `SongPublicDto.kt`, `ZakromaPublicDto.kt`, `PublicApiController.kt`, `Zakroma.kt`, `ZakromaView.vue`, `SearchView.vue`, `filter.html`, `zakroma.html`, `testpage.html`.
- [x] T023 Создать коммит: `git add` ожидаемых файлов (БЕЗ `deploy/ollama_data/`, `dist/`, `node_modules/`, `deploy/.env`, `deploy/do.env`), `git commit -m "185-song-dto-audit-sponsr-remove: аудит DTO песни и удаление ссылок на соцсети из публичных шаблонов"`.
- [ ] T024 Создать PR: `git push -u origin 185-song-dto-audit-sponsr-remove` → `gh pr create --base master --title "185: аудит DTO песни и удаление ссылок на соцсети из публичных шаблонов"`.
- [ ] T025 Дождаться CI 7/7 SUCCESS (`gh pr checks` или `gh run watch`); выполнить `gh pr merge --merge` **БЕЗ** `--delete-branch` (см. AGENTS.md «Жизненный цикл feature-ветки»).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: без зависимостей — стартует немедленно.
- **User Story 1 (Phase 2)**: зависит от Setup; независим от других US (правки в `*.vue`).
- **User Story 2 (Phase 3)**: зависит от Setup; независим от US1 по файлам, но **усиливает** US1 (JSON перестаёт содержать `linkSponsrPlay`).
- **User Story 3 (Phase 4)**: зависит от Setup; независим от US1/US2 по файлам (правки в Thymeleaf).
- **Polish (Phase 5)**: зависит от завершения US1+US2+US3.

### User Story Dependencies

- US1 (P1), US2 (P1), US3 (P2) — все независимы друг от друга по файлам. Могут выполняться параллельно (если есть ресурсы).
- US1 без US2 функционально работает: фронт просто перестаёт рендерить `linkSponsrPlay`, который всё ещё приходит от бэка — шаблон Vue тихо игнорирует обращение к неиспользуемому свойству.
- US2 без US1 функционально работает: JSON не содержит мусор, но Vue-шаблон ещё содержит `<PlatformLink link-name="sponsr" :link-value="undefined">` — рендерит пустую иконку.
- US3 без US1/US2 функционально работает: Thymeleaf просто перестаёт рендерить блоки ссылок.

### Within Each User Story

- US1: T002/T003 параллельно → T004/T005 последовательно → T006.
- US2: T007..T012 все [P] параллельно (разные файлы) → T013.
- US3: T014/T015/T016 параллельно → T017.

### Parallel Opportunities

- **Phase 2**: T002 + T003 параллельно (разные Vue-файлы).
- **Phase 3**: T007 + T008 (один файл — последовательно!), T009 + T010 (один файл — последовательно!), T011 + T012 — параллельно. Кластеры: {T007,T008}, {T009,T010}, T011, T012 — все параллельно.
- **Phase 4**: T014 + T015 + T016 параллельно (3 разных html-файла).
- **Phase 5**: T018 + T019 + T020 параллельно (3 разных линтера). T022 после T018..T021.

---

## Parallel Example: User Story 2

```bash
# Все правки в разных файлах — могут идти параллельно:
Task: "T007 [P] [US2] Удалить 26 полей из SongPublicDto.kt"
Task: "T009 [P] [US2] Удалить 21 поле из ZakromaAlbumSongPublicDto в ZakromaPublicDto.kt"
Task: "T011 [P] [US2] Удалить 21 параметр из PublicApiController.kt"
Task: "T012 [P] [US2] Удалить 21 var-поле из Zakroma.kt"

# Правки в ОДНОМ файле — последовательно:
T007 (поля) → T008 (конвертер) в SongPublicDto.kt
T009 (поля) → T010 (конвертер) в ZakromaPublicDto.kt
```

---

## Implementation Strategy

### MVP First (User Story 1 + User Story 2)

1. Завершить Setup (T001).
2. Завершить User Story 1 (T002..T006) — frontend-изменения (убрать иконку).
3. Завершить User Story 2 (T007..T013) — backend-изменения (убрать мусор из JSON).
4. **STOP и VALIDATE**: проверить, что `/zakroma?author=КИНО` отдаёт ≤ 8 KB JSON без `linkSponsrPlay`; фронт отрендерен без иконки Sponsr.
5. Demo MVP — готово к PR.

### Incremental Delivery

1. Setup → готово.
2. US1 + US2 → MVP (только новый SPA чистый).
3. US3 → дополнительная очистка legacy Thymeleaf-сайта.
4. Polish → линтеры, PR, CI 7/7, merge.

### Parallel Team Strategy

С одним разработчиком — последовательно по приоритетам (US1 → US2 → US3 → Polish).

---

## Notes

- Коммит-стиль: `185-song-dto-audit-sponsr-remove: краткое описание` (на русском).
- Branch НЕ удалять после мёрджа (см. AGENTS.md «Жизненный цикл feature-ветки»).
- **НЕ коммитить**: `deploy/ollama_data/`, `dist/`, `node_modules/`, `deploy/.env`, `deploy/do.env`.
- `idStatus` и `haveVkGroupLink` ОСТАЮТСЯ в `Song.kt` (karaoke-app) и в `testpage.html` (строка ~300) — не ошибка! Нужны для логики отображения картинки.
- Никаких БД-миграций (поля живут в `tbl_songs`, не трогаем).
- Никаких изменений в `Song.kt` (нужна админке `webvue3` и публикационным ботам).
- Никаких изменений в `webvue3` (админка работает через `ApiController.kt`, не через публичные DTO).
- Прямой push в master ЗАПРЕЩЁН — только через PR + CI 7/7 SUCCESS (см. AGENTS.md «CI-gate для master»).