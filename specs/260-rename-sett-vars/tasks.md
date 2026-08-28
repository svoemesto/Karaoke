---
description: "Task list for feature 260-rename-sett-vars"
---

# Tasks: Переименование `sett`/`settings` → `song` (260-rename-sett-vars)

**Input**: Design documents from `/specs/260-rename-sett-vars/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/contracts.md`, `quickstart.md`

**Tests**: Не запрошены явно (spec.md не требует TDD; проект не полагается на автотесты как gate — см. constitution.md «Рабочий процесс»). Верификация — компиляция/линт после каждой группы задач + ручные сценарии `quickstart.md`.

**Delivery note (FR-013/FR-014 спеки 260)**: Несмотря на разбивку по user story ниже, весь результат мержится и деплоится **одним PR + один атомарный deploy backend+Thymeleaf** (`karaoke-app`/`karaoke-web`), плюс **отдельный deploy `karaoke-public`**, плюс **отдельный deploy `karaoke-web`** (если понадобится). `webvue3` вне scope целиком. Разбивка по фазам — это порядок работы и внутренние чекпоинты, не отдельные релизы.

## Format: `[ID] [P?] [Story?] Description with file path`

- **[P]**: Можно выполнять параллельно (разные файлы, нет зависимости от незавершённых задач)
- **[Story]**: US1 / US2 / US3 — соответствие user story в `spec.md`
- Все пути указаны от корня репозитория `/home/nsa/Karaoke/`

---

## Phase 1: Setup

**Purpose**: Зафиксировать «чистый» baseline до начала переименования

- [ ] T001 Убедиться, что перед началом работы все три модуля собираются и линтуются без ошибок:
      `./gradlew :karaoke-app:ktlintCheck :karaoke-web:ktlintCheck`,
      `./gradlew clean :karaoke-app:bootJar :karaoke-web:bootJar --parallel` (на `dev-pc`/`dev` или с согласия пользователя),
      `tools/check-eslint-baseline.sh karaoke-public`,
      `cd karaoke-public && npm run lint && cd ..` — если что-то уже красное
      **до этой задачи**, остановиться и разобраться отдельно (это не относится к рефакторингу).
- [ ] T002 [P] Прогнать baseline-грепы из `quickstart.md` (Сценарии 3 и 4) на текущем `HEAD` и зафиксировать числа:
      ```
      grep -rn '\bsett\b' --include='*.kt' --include='*.html' --include='*.js' --include='*.vue' --include='*.ts' --include='*.sql' \
        --exclude-dir=build --exclude-dir=node_modules --exclude-dir=.git --exclude-dir=dist \
        karaoke-app karaoke-web karaoke-public | wc -l
      # ожидаемо ≥546 (или актуальная цифра)
      ```
      Плюс отдельные срезы по модулям: `grep -rn '\bsett\b' --include='*.kt' ... karaoke-app/.../controllers/MainController.kt | wc -l`
      и т.п. — нужны для tracking-а прогресса и для сверки SC-001 при завершении.
- [ ] T003 [P] Зафиксировать точный список `val settings = Song.loadFromDbById(...)` в `karaoke-app/.../controllers/ApiController.kt` (≥ 6 мест по `research.md` §1; baseline-число должно совпадать с SC-002):
      ```
      grep -n '^[[:space:]]*val settings =\|= settings' karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt
      ```
      Ожидаемый минимум: 6851, 6900, 6990, и возможно ещё (новые методы после спеки 102). Записать номера строк в отчёт задачи.

**Checkpoint**: Известно, что перед рефакторингом всё зелёное, посчитан baseline, зафиксирован точный список мест `val settings`.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Подтвердить границы исключений (Категория «Не входит», `data-model.md`, FR-007 спеки) прямо перед началом правок — если что-то в репозитории изменилось со времени `/speckit.plan`, лучше узнать сейчас, а не после переименования 30+ файлов.

**⚠️ CRITICAL**: Не начинать Phase 3/4, пока эта проверка не пройдена.

- [ ] T004 Grep-подтвердить, что границы исключений из `FR-007` спеки всё ещё верны на текущем `HEAD`:
      - `KaraokePlatform.settingsFieldPublicationId`/`settingsFieldVersionNumber` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokePlatform.kt` — НЕ переименовываются.
      - `LS_SETTINGS_KEY`, `PlayerSettings`, `EDITOR_SETTINGS_LS_KEY` в `karaoke-public/src/player/KaraokePlayer.js`, `webvue3/src/player/KaraokePlayer.js`, `webvue3/src/composables/useKaraokeEditor.js` — НЕ переименовываются.
      - `webvue3/src/components/Songs/edit/SubsEdit.vue:183` — label UI-кнопки, НЕ переименовывается (Clarifications Q5).
      - `webvue3/src/components/PublicSettings/PublicSettingsTable.vue` — НЕ переименовывается (другая таблица `tbl_public_settings`).
      - `@KaraokeDbTableField(name = "settings_id")` в `karaoke-app/.../KaraokeProcess.kt` — строковый аргумент аннотации остаётся.
      - `SyncTarget.key = "settings"` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/sync/SyncTarget.kt:221` — НЕ переименовывается.
      - `tbl_public_settings` ссылки во `CaptchaConfigService.kt`, `PublicSettingsWebController.kt` — НЕ переименовываются.
      - `@PostMapping("/playlists/{id}/settings")` в `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicPlaylistController.kt` — endpoint плейлиста, НЕ переименовывается.
      - `KDoc`-ссылки `tbl_settings` в `karaoke-web/.../dto/ZakromaPublicDto.kt:9, 19` и `karaoke-web/.../services/ShareLinkSweeper.kt:130` — **будут** обновлены на `tbl_songs` в T031 (US2).

**Checkpoint**: Границы задачи подтверждены — можно приступать к US1/US2.

---

## Phase 3: User Story 1 — Читаемые Kotlin-методы без сокращений (Priority: P1) 🎯 MVP-срез

**Goal**: Все параметры/локальные переменные/поля с типом (или производным значением) `Song`, не пересекающие границу backend↔frontend или backend↔SQL, переименованы `sett`→`song`, `settings`→`song` (FR-001, FR-002).

**Independent Test**: Открыть любой Kotlin-файл из списка ниже — у функций/полей/лямбда-параметров с типом `Song` имя `song` (или явное производное при коллизии, напр. `targetSong`), а не `sett`. `./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel` и `./gradlew :karaoke-app:ktlintCheck :karaoke-web:ktlintCheck` проходят без новых ошибок.

### Implementation for User Story 1

#### Подфаза 3A — листовые Kotlin-файлы без конфликтов имён (параллельно)

- [ ] T005 [P] [US1] Переименовать `val sett = Song.loadFromDbById(...)` и все использования `sett.*` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/TelegramUpdatesConsumer.kt` (≈строка 124) — переменная переименована в `song`.
- [ ] T006 [P] [US1] Переименовать `settings: Song` → `song` и все `settings.*` внутри `fun publishToVkNow(...)` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt` (≈строка 6851, и до ≈6888) — все использования, включая `song = settings` в именованном аргументе вызова `publishToVk(settings, pubType)`.
- [ ] T007 [P] [US1] Переименовать `settings: Song` → `song` внутри `fun publishPremiumTelegram(...)` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt` (≈строка 6900, до ≈6996).
- [ ] T008 [P] [US1] Переименовать `settings: Song` → `song` внутри `fun publishPremiumVk(...)` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt` (≈строка 6990, и до конца метода).
- [ ] T009 [P] [US1] Переименовать лямбда-параметр `sett` в `forEach { sett -> ... }`, `map { sett -> ... }`, `filter { sett -> ... }` и обращения `sett.*` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/CrossSong.kt` (≥7 мест: 80, 95, 113, 146, 184, 219, 257) — переименовать в `song`.
- [ ] T010 [P] [US1] Переименовать лямбда-параметр `sett` и обращения `sett.*` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Pictures.kt:145` (`?.let { sett -> ... sett.rootFolder ... }`) — переименовать в `song`.
- [ ] T011 [P] [US1] Переименовать `sett` в `MainController.kt` (модуль `karaoke-app/.../controllers/`) **внутренние** `val sett = ...` и `sett.*` — без `model.addAttribute("sett", ...)` (это в T028, US2) и без изменений Thymeleaf (тоже US2). Учесть, что есть ~60 вхождений.
- [ ] T012 [P] [US1] Переименовать `val sett = Song.loadFromDbById(...)` и `sett.*` в `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt` (≈строка 727, и ниже до ≈769; см. `isSelfAssignEditor`/`Song` обращения) — переименовать в `song`.
- [ ] T013 [P] [US1] Переименовать `val sett = ...` и `sett.*` в `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/MainController.kt` **внутренние** (≈строки 450, 516) — без `model.addAttribute("sett", ...)` (это в T029, US2).
- [ ] T014 [P] [US1] Переименовать `sett` → `targetSong` (или `renderSong`, по смыслу использования) в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/mlt/mko/MkoString.kt` — `val sett = song` и обращения (конфликт с `song: Song` параметром конструктора; использовать явное имя по `data-model.md` секции «Файлы с потенциальным конфликтом имён»).
- [ ] T015 [P] [US1] То же что T14 для `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/mlt/mko/MkoMelodyNote.kt` — переименовать `sett` → осмысленное производное имя (напр. `renderSong`).
- [ ] T016 [P] [US1] То же что T14 для `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/mlt/mko/MkoFill.kt`.
- [ ] T017 [P] [US1] То же что T14 для `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/mlt/mko/MkoMelodyTabs.kt`.
- [ ] T018 [P] [US1] То же что T14 для `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/mlt/mko/MkoSepar.kt`.
- [ ] T019 [P] [US1] То же что T14 для `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/mlt/mko/MkoChordPictureLines.kt`.
- [ ] T020 [P] [US1] То же что T14 для `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/mlt/mko/MkoChords.kt`.
- [ ] T021 [P] [US1] То же что T14 для `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/mlt/mko/MkoLines.kt`.
- [ ] T022 [P] [US1] То же что T14 для `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/mlt/mko/MkoElement.kt`.
- [ ] T023 [P] [US1] То же что T14 для `MkoChordPictureElement.kt`, `MkoChordPictureFader.kt`, `MkoChordPictureImage.kt`, `MkoLineTrack.kt` (4 файла с одиночными `sett`-вхождениями в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/mlt/mko/`).

#### Подфаза 3B — SQL inline + чекпоинт

- [ ] T024 [US1] Переименовать алиас `tbl_songs sett` → `tbl_songs song` и все `sett.*` ссылки в двух SQL-запросах в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/StatBySong.kt`:
      - Запрос 1 (≈строки 458-491): `sett.id`, `sett.song_author`, `sett.song_album`, `sett.song_name` в SELECT/JOIN/GROUP BY.
      - Запрос 2 (≈строки 591-621): то же во втором запросе.
      Физическое имя таблицы `tbl_songs` остаётся (FR-005).
- [ ] T025 [US1] Чекпоинт User Story 1:
      `./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel` — без ошибок.
      `./gradlew :karaoke-app:ktlintCheck :karaoke-web:ktlintCheck` — без новых нарушений (baseline-файлы `config/ktlint/baseline-*.xml` без изменений).
      Если есть ошибки — откатить соответствующую задачу T005–T024 и исправить.

**Checkpoint**: User Story 1 полностью выполнен — внутренний Kotlin/SQL-код читается корректно. Никаких wire/UI-изменений ещё не сделано.

---

## Phase 4: User Story 2 — Шаблоны, фронтенд и SQL без `sett` (Priority: P1)

**Goal**: Thymeleaf-атрибут `model.addAttribute("sett", ...)` + соответствующие `${sett.*}` в шаблонах + Vue-итераторы в `karaoke-public` + KDoc/JSDoc со ссылками на `tbl_settings` переименованы синхронно (FR-003, FR-004, FR-005 [KDoc], FR-008).

**Independent Test**: Открыть `area_left_column.html` / `songs.html` — итерация по `${song.id}` (без `null` после деплоя). Открыть `SearchView.vue` — `v-for="song in searchResults"`, все обращения `song.*`. KDoc-ссылки `tbl_settings` обновлены. Backend не падает на компиляции.

### Implementation for User Story 2

#### Подфаза 4A — `karaoke-app` backend + Thymeleaf атомарно (один deployment unit)

- [ ] T026 [US1+US2] (зависит от T011) В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/MainController.kt` переименовать **строковые литералы**:
      - `model.addAttribute("sett", sett)` → `model.addAttribute("song", song)` (оба места, ~строки 466 и 523 — проверить по baseline).
      - Проверить и обновить все `redirectAttributes.addFlashAttribute("sett", ...)`, `model["sett"] = ...` и подобные строковые ключи, где фигурирует `"sett"` рядом с переменной `sett`.
- [ ] T027 [US2] Переименовать `${sett.*}` → `${song.*}` и `th:each="...:${sett}"` → `th:each="...:${song}"` во **всех** Thymeleaf-шаблонах модуля `karaoke-app/src/main/resources/templates/`:
      - `area_left_column.html` (~:115 — `th:each="song:${sett}"`).
      - `area_center_column.html` (закомментированные блоки `${sett.*}` — обновить тоже).
      - `songs.html` (~:2286 — `th:each="song:${sett}"`).
      - `filter.html` (~:376-410 — `${sett.author}`, `${sett.year}`, `${sett.album}`, `${sett.track}`, `${sett.songName}`, `${sett.id}`, `${sett.linkSponsrPlay}`, `${sett.onAir}`).
      - `zakroma.html` (~:436-500 — `${sett.linkMaxTabs}`, `${sett.linkVkTabs}`, `${sett.linkTgTabs}`, и т.п.).
      Не забыть обновить все `th:text="${sett.*}"`, `th:href="${sett.*}"`, `th:if="${sett.* == '...'}"` и аналогичные.
      **Зависит от T026** (строковый литерал `"sett"` в контроллере должен стать `"song"` раньше, чем шаблоны обновятся — единый атомарный коммит).

#### Подфаза 4B — `karaoke-web` legacy + Thymeleaf

- [ ] T028 [US2] В `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/MainController.kt` переименовать ВСЕ `val sett = ...` локальные и/или аргументы `sett: Song` → `song` (или производное при коллизии). Учесть, что в `data-model.md` §3 указано, что шаблоны `karaoke-web/src/main/resources/templates/{filter,zakroma,song,testpage}.html` могут иметь `${sett.*}` — обновить синхронно с этим шагом.
- [ ] T029 [US2] Переименовать `${sett.*}` → `${song.*}` в Thymeleaf-шаблонах модуля `karaoke-web/src/main/resources/templates/`:
      - `filter.html`, `zakroma.html`, `song.html`, `testpage.html` (проверить содержимое перед коммитом — могут быть `class="sett-..."` или другие места, см. `research.md` §3.5).

#### Подфаза 4C — Vue/JS в `karaoke-public` (отдельный deployment)

- [ ] T030 [P] [US2] В `karaoke-public/src/views/SearchView.vue` переименовать `v-for="sett in searchResults"`, `v-for="sett in searchResults"` (второе вхождение, строки 74 и 124), все обращения `sett.*` в шаблоне (~60+), и все методы `showCoin(sett)`, `dateLabel(sett)`, `onSubscribeClick(sett)`, `isSongActiveForUser(sett)`, `showCartIcon(sett)`, `isPurchased(sett)`, `showDate(sett)`, `isSongContentReady(sett)` — параметр переименован в `song`, тело методов обновлено.
- [ ] T031 [P] [US2] В `karaoke-public/src/views/ZakromaView.vue` переименовать `v-for="sett in item.alb.albumSettings"` и `v-for="sett in item.alb.albumSettings"` (строки 250 и 298), все обращения `sett.*` в шаблоне (~60+), методы `isSongActiveForUser(sett)`, `isSongContentReady(sett)`, `showCoin(sett)`, `showCartIcon(sett)`, `isPurchased(sett)`, `onSubscribeClick(sett, zak.author)`, `showDate(sett)`, `dateLabel(sett)` — параметр переименован в `song` (для `onSubscribeClick` остаётся второй параметр `author`).
- [ ] T032 [P] [US2] В `karaoke-public/src/views/AuthorPlaylistView.vue:280` переименовать `const setts = [...(alb.albumSettings || [])].sort(...)` — переменная переименована в `songs` (мн.ч., по смыслу — коллекция).
- [ ] T033 [P] [US2] В `karaoke-public/src/composables/useZakromaStreamProgress.js:225` обновить JSDoc-комментарий: `// итерирует \`v-for="sett in alb.albumSettings"\`` → `// итерирует \`v-for="song in alb.albumSettings"\`` (синхронизация с T031).

#### Подфаза 4D — KDoc/JSDoc с устаревшими `tbl_settings` ссылками

- [ ] T034 [P] [US2] В `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/ZakromaPublicDto.kt`:
      - Строка 9: «`DTO для zakroma album settings public`» → «`DTO для zakroma album songs public`».
      - Строка 19: «`персистентные флаги из tbl_settings (Pass 100, ...)`» → «`персистентные флаги из tbl_songs (Pass 100, ...)`».
- [ ] T035 [P] [US2] В `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/ShareLinkSweeper.kt:130` обновить KDoc: «(`tbl_settings`)» → «(`tbl_songs`)».

#### Подфаза 4E — чекпоинт US2

- [ ] T036 [US2] Чекпоинт User Story 2:
      - `./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel` — без ошибок (контроль правильности синхронной правки backend+HTML).
      - `./gradlew :karaoke-app:ktlintCheck :karaoke-web:ktlintCheck` — без новых нарушений.
      - `tools/check-eslint-baseline.sh karaoke-public` — без новых нарушений.
      - `cd karaoke-public && npm run lint && cd ..` — без ошибок.
      - `grep -rn '\bsett\b' karaoke-web/src/main/resources/templates/*` — `0` совпадений.
      - `grep -rn '\bsett\b' karaoke-app/src/main/resources/templates/*` — `0` совпадений.

**Checkpoint**: User Story 1 и 2 вместе работают — внутренний код, контракты (Thymeleaf атрибут в karaoke-app + karaoke-web), Vue-итераторы в karaoke-public, KDoc — все синхронизированы.

---

## Phase 5: User Story 3 — Не задеты понятия «настроек», не связанные с `Song` (Priority: P2)

**Goal**: Подтвердить, что исключения (Category Not-Song, FR-007 спеки) остались нетронутыми после Phase 3-4 (FR-004, FR-005 спеки 260).

**Independent Test**: Все проверки ниже возвращают «без изменений» — сравнение с baseline из T002.

### Verification for User Story 3

- [ ] T037 [P] [US3] Проверить, что `KaraokePlatform.settingsFieldPublicationId`/`settingsFieldVersionNumber` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokePlatform.kt` НЕ изменились (`git diff master -- karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokePlatform.kt` показывает только T024-эквивалентный churn в SQL, если вообще что-то; эти поля `settings` должны быть идентичны baseline).
- [ ] T038 [P] [US3] Проверить, что модуль `webvue3` не затронут:
      ```
      git diff --stat -- webvue3
      ```
      Ожидаемый результат: `0` строк diff (SubsEdit.vue:183, PublicSettingsTable.vue, useKaraokeEditor.js, KaraokePlayer.js — всё без изменений).
- [ ] T039 [P] [US3] Проверить, что физическая БД не затронута:
      ```
      git diff --stat -- deploy/karaoke-db
      # ожидаемо пусто (новых .sql миграций нет)
      grep '@KaraokeDbTableField(name = "settings_id")' karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcess.kt
      # ожидаемо строковый аргумент аннотации остался
      grep 'key = "settings"' karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/sync/SyncTarget.kt
      # ожидаемо SyncTarget.key = "settings" без изменений
      ```
- [ ] T040 [P] [US3] Проверить, что модуль `karaoke-public/src/player/KaraokePlayer.js` не затронут:
      ```
      git diff --stat -- karaoke-public/src/player/KaraokePlayer.js
      # ожидаемо пусто
      grep -c 'LS_SETTINGS_KEY' karaoke-public/src/player/KaraokePlayer.js
      # ожидаемо baseline-число (без изменений)
      ```
- [ ] T041 [P] [US3] Проверить, что другие модули `webvue3` не затронуты:
      `webvue3/src/composables/useKaraokeEditor.js`, `webvue3/src/components/PublicSettings/PublicSettingsTable.vue`, `webvue3/src/player/KaraokePlayer.js` (если есть), `webvue3/src/components/Songs/edit/SubsEdit.vue` (`sett`-label остаётся).
- [ ] T042 [US3] Финальный grep-регресс из `quickstart.md` (Сценарии 3 и 4): сравнить с baseline из T002:
      - `grep -rn '\bsett\b' --include='*.kt' --include='*.html' --include='*.js' --include='*.vue' --include='*.ts' --include='*.sql' --exclude-dir=build --exclude-dir=node_modules --exclude-dir=.git --exclude-dir=dist karaoke-app karaoke-web karaoke-public` — должно вернуть **0** строк вне исключений.
      - `grep -rn '\bsettings\b\|: settings\|^[[:space:]]*\(val\|var\) settings\b' --include='*.kt' ... karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb` — должно вернуть **0** строк вне исключений.

**Checkpoint**: Все три user story подтверждены независимо; готово к Polish.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Обязательные по конституции завершающие шаги, общие для всей фичи

- [ ] T043 Обновить (если ещё существует) `docs/features/<slug>-settings-rename.md` или аналогичный per-feature документ, ссылающийся на `settingsId=0` → заменить на `songId=0` и «нет привязки к `Settings`/`tbl_settings`» → «нет привязки к `Song`/`tbl_songs`» (прецедент спеки 102 T041).
      Если per-feature документ не найден в baseline — задача сводится к `echo "не требуется: нет per-feature документа"` и закрывается.
- [ ] T044 [P] Прогнать KDoc/JSDoc coverage на переименованных публичных сигнатурах:
      `bash tools/check-kdoc-coverage.sh`,
      `bash tools/check-jsdoc-coverage.sh webvue3 karaoke-public` (webvue3 без изменений, но baseline должен сохраниться).
- [ ] T045 [P] Прогнать полный pre-commit gate по `AGENTS.md`:
      ```
      pre-commit run --all-files
      ./gradlew :karaoke-app:ktlintCheck :karaoke-web:ktlintCheck
      tools/check-eslint-baseline.sh karaoke-public
      bash tools/check-kdoc-coverage.sh
      bash tools/check-jsdoc-coverage.sh webvue3 karaoke-public
      ```
      Все — без новых нарушений.
- [ ] T046 Выполнить вручную сценарии 6, 7, 8 из `quickstart.md`:
      - Сценарий 6: пересобрать `karaoke-app`, открыть `/songs` legacy-админки в браузере — список песен отрисовался (`id`, `songName`, `author`, `year`, `album`, `track`, `color`).
      - Сценарий 7: перезапустить `karaoke-public`, открыть `/search` и `/zakroma` — списки песен отрисовались без ошибок в JS-консоли.
      - Сценарий 8: `grep -n 'from\|join' karaoke-app/src/main/kotlin/.../StatBySong.kt | head` — алиас `song` присутствует, `sett` отсутствует.
      По правилу проекта «тестов в CI нет» — проверка делается пользователем/агентом вручную.
- [ ] T047 Подготовить единый PR по всем изменениям (FR-013, FR-014 спеки 260):
      - Один коммит/набор коммитов в ветке `260-rename-sett-vars`, покрывающий Phase 3-6 целиком (исключая `karaoke-public` деплой — он отдельный, но в том же PR по FR-014).
      - Заголовок PR в стиле `260: rename sett/settings → song across backend, Thymeleaf, Vue, SQL, KDoc`.
      - Описание PR — список user story с independent test criteria (из spec.md).
- [ ] T048 После мержа PR — добавить хэш(ы) финального коммита(ов) в `.git-blame-ignore-revs` (Constitution VII.2, прецедент спеки 102 T046) — follow-up действие, выполняется отдельно пользователем/агентом после факта мержа.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: без зависимостей.
- **Foundational (Phase 2)**: зависит от Setup; блокирует Phase 3 и 4.
- **User Story 1 (Phase 3)**: зависит от Foundational; **подфаза 3A листовые файлы** параллелизуемы, **подфаза 3B SQL** — после 3A.
- **User Story 2 (Phase 4)**: зависит от Foundational и частично от US1:
  - T026 (строковые литералы `"sett"` в `MainController.kt`) — **зависит от T011** (внутренние `val sett` уже переименованы).
  - T027 (Thymeleaf в `karaoke-app/templates`) — **зависит от T026** (atomic deploy).
  - T028/T029 (`karaoke-web` backend + Thymeleaf) — зависит от T013, можно параллельно с T026-T027.
  - T030-T033 (Vue/JS) — параллельно с backend.
  - T034-T035 (KDoc) — параллельно.
- **User Story 3 (Phase 5)**: зависит от завершения Phase 3 и 4 — это **верификация результата**, не независимая ветка работы.
- **Polish (Phase 6)**: зависит от Phase 5.

### Within Each User Story

- **US1 листовые файлы** (T005–T013, T014–T023) — `[P]`, разные файлы, нет общих символов.
- **SQL-переименование** (T024) — после листовых mlt/mko файлов (нет реальной зависимости данных, но логически «внутренние переменные сначала»).
- **US2 Thymeleaf** (T027, T029) — после backend (T026, T028) из-за synchronous-deploy-требования.
- **US2 Vue/JS** (T030–T033) — независимо от backend-файлов; параллельно.
- **US2 KDoc** (T034-T035) — независимо; параллельно.

### Parallel Opportunities

#### Setup (Phase 1)

- T001 → затем T002, T003 параллельно.

#### US1 Подфаза 3A — листовые файлы

- T005–T013 (разные файлы) → параллельно.
- T014–T023 (13 файлов `mlt/mko/*.kt`, каждый самодостаточен) → параллельно.

#### US2 Подфаза 4C+D — Vue/JS + KDoc

- T030 (`SearchView.vue`), T031 (`ZakromaView.vue`), T032 (`AuthorPlaylistView.vue`), T033 (`useZakromaStreamProgress.js`) → параллельно (4 разных файла, синтаксически независимы).
- T034, T035 → параллельно (разные файлы в `karaoke-web`).

#### US3 (Phase 5)

- T037, T038, T039, T040, T041 — все `[P]`, каждый проверяет свой исключённый путь.

---

## Parallel Examples

### Phase 3A — US1 leaf files

```bash
# Все листовые Kotlin-файлы можно переименовывать параллельно:
Task: "Переименовать sett→song в TelegramUpdatesConsumer.kt"
Task: "Переименовать settings→song в ApiController.kt publishToVkNow"
Task: "Переименовать settings→song в ApiController.kt publishPremiumTelegram"
Task: "Переименовать settings→song в ApiController.kt publishPremiumVk"
Task: "Переименовать sett→song в CrossSong.kt"
Task: "Переименовать sett→song в Pictures.kt"
Task: "Переименовать sett→song в MainController.kt (внутренние, без атрибута)"
Task: "Переименовать sett→song в PublicApiController.kt"
Task: "Переименовать sett→song в karaoke-web MainController.kt (внутренние)"
# + все 10 mlt/mko/*.kt файлов параллельно
```

### Phase 4C+D — US2 Vue/JS + KDoc

```bash
# Все Vue/JS + KDoc можно переименовывать параллельно:
Task: "Переименовать v-for=sett в SearchView.vue"
Task: "Переименовать v-for=sett в ZakromaView.vue"
Task: "Переименовать setts→songs в AuthorPlaylistView.vue"
Task: "Обновить JSDoc в useZakromaStreamProgress.js"
Task: "Обновить KDoc в ZakromaPublicDto.kt"
Task: "Обновить KDoc в ShareLinkSweeper.kt"
```

---

## Implementation Strategy

### Порядок работы (внутри единого PR)

1. Phase 1 (Setup) — обязательный старт.
2. Phase 2 (Foundational) — обязательная верификация границ исключений.
3. Phase 3 (US1) — самый большой по числу файлов (~25 файлов), но наименее рискованный срез (чисто внутренний код, ноль контрактных изменений). Чекпоинт T025 должен быть зелёным перед продолжением.
4. Phase 4 (US2) — рискованный срез (задевает контракт backend↔Thymeleaf и UI `karaoke-public`); каждая контрактная пара (backend-файл + frontend/шаблон-потребитель) правится и проверяется вместе (T026-T027 — атомарно; T028-T029 — атомарно).
5. Phase 5 (US3) — чистая верификация, не производит новых изменений (если что-то не совпало — это находка бага в Phase 3/4, требующая точечного исправления).
6. Phase 6 (Polish) — документация, финальные гейты, единый PR, пост-мерж `.git-blame-ignore-revs`.

### Инкрементальная проверка (не инкрементальный релиз)

В отличие от типичного MVP-флоу, здесь **нет** «задеплоить после US1» — весь результат уходит одним PR (FR-013 спеки 260). Тем не менее чекпоинты T025/T036/T042 дают возможность остановиться и проверить прогресс независимо на каждом этапе перед тем, как двигаться дальше.

### Why MVP = US1 only

US1 (Kotlin + SQL rename) — это **максимально-низкорисковый срез** задачи: изменения только во внутренностях кода, ноль контрактных сдвигов. Если бы пришлось откатывать всё из-за регрессии — можно было бы откатить US1 отдельно без ущерба для US2/US3. Поэтому **MVP = US1** в смысле «минимально-отделимый кусок рефакторинга», хотя фактический деплой — один на всё.

---

## Notes

- `[P]` — разные файлы, нет зависимости от незавершённых задач. Особенно важно для US3 (Phase 5), где `[P]` позволяет запустить все grep-проверки одновременно.
- `[US1]`/`[US2]`/`[US3]` — трассировка к user story в `spec.md`.
- Коммитить можно после каждой задачи или логической группы — в единый PR войдут все коммиты ветки `260-rename-sett-vars`.
- **Избегать**: переименования вслепую по текстовому совпадению `sett`/`settings` без проверки по `data-model.md`/FR-007 — граница задачи неоднократно уточнялась в `spec.md` именно потому, что текстовое совпадение вводит в заблуждение.
- **Особо**: `mlt/mko/*.kt` файлы требуют **внимательного** выбора производного имени при конфликте (`targetSong`/`renderSong`/`songForRender` — по смыслу использования в файле); НЕЛЬЗЯ использовать `song` (создаст shadowing с `song: Song` параметром конструктора).
- **Особо**: `MainController.kt` (и `karaoke-app`, и `karaoke-web`) — **двухфазная** правка: сначала внутренние `val sett` (T011, T013, T028), потом строковый литерал `model.addAttribute("sett", ...)` (T026) + синхронно Thymeleaf (T027, T029). В одном PR — да, но в одном atomic коммите — обязательно.
- Сравнение с прецедентом спеки 102 (`specs/102-rename-song-settings-vars/tasks.md`) показывает, что текущая задача **меньше** по объёму (нет DTO-переименований, нет изменений webvue3) и **больше** по Thymeleaf-renames (5 шаблонов + строковый литерал) + добавляет SQL-rename (`StatBySong.kt`) + Vue-rename (`karaoke-public`).
