# Feature Specification: Переименование переменных `sett` (и оставшихся `settings`) на `song`

**Feature Branch**: `260-rename-sett-vars`
**Created**: 2026-08-28
**Status**: Draft
**Input**: User description: "В коде встречаются артефакты типа `sett.id` или `sett.songName` — у нас давно таблица называется не `tbl_settings`, а `tbl_songs`, и подобные переменные `sett` в коде должны быть переименованы в `song`."

## Контекст

> **История вопроса.** В 2024–2025 класс `Settings` был переименован в
> `Song` (см. `specs/011-album-song-rename`, миграция БД
> `deploy/karaoke-db/28_rename_settings_to_songs.sql`). Позднее была
> отдельная задача по переименованию параметров и локальных переменных с
> типом `Song` в коде: `specs/102-rename-song-settings-vars`
> (завершена, ветка `102-rename-song-settings-vars` влита в master).
> Спека 102 покрыла **полную форму** `settings`/`settingsId`/`settings_*`
> в Kotlin, DTO, HTTP, SSE и Thymeleaf-шаблонах. Однако в кодовой базе
> осталась **сокращённая форма `sett`** (всегда тип/значение `Song`), а
> также **единичные остатки полной формы** в коде, написанном уже после
> спеки 102 (новые endpoint'ы `publishToVkNow`, `publishPremiumTelegram`),
> и в DTO/комментариях, до которых спекой 102 «дотянуться» не получилось.
> Текущая задача закрывает эти остатки.

## Clarifications

### Session 2026-08-28

- Q: Какой объём охвата? Переименование **только** сокращённой формы `sett`, или также подтянуть оставшиеся единичные вхождения полной формы `settings`/`settings:` где значение/тип — `Song`, и забытые ссылки `tbl_settings` в комментариях? → A: Все три категории вместе (полный остаточный охват, по аналогии с решением спеки 102 в Clarifications Q1).
- Q: В DTO `ZakromaPublicDto` есть поле `albumSettings: List<ZakromaAlbumSongPublicDto>` (и его JSON-ключ), читаемое фронтендом через `v-for="sett in alb.albumSettings"`. Это имя поля DTO, оно сериализуется в JSON — переименование синхронизирует с webvue3 и касается контракта backend↔frontend. Включаем `albumSettings` → `albumSongs` (или `albumSongsFor*`) в эту задачу, или оставляем отдельной? → A: Оставляем отдельной задачей (имя поля DTO + JSON-ключ + все потребители — отдельный frontend-sync PR). В этой спеке `albumSettings` остаётся без изменений.
- Q: Шаблонный атрибут `model.addAttribute("sett", sett)` (в `MainController.kt`) используется Thymeleaf-шаблонами как `${sett.*}` и/или `th:each="song:${sett}"` (см. `area_left_column.html:115`, `songs.html:2286`). Переименовываем имя атрибута в `"song"` и все обращения в шаблонах — `${song.*}` / `th:each="song:${song}"`? Это требует синхронного апдейта backend (имя атрибута) + 4 Thymeleaf-шаблонов (`filter.html`, `zakroma.html`, `area_left_column.html`, `songs.html`, плюс закомментированные блоки в `area_center_column.html`). → A: Переименовываем — атрибут + все обращения во всех задействованных шаблонах, по образцу FR-016 спеки 102 (один синхронный шаг backend+HTML, шаблоны в этом же модуле `karaoke-app`).
- Q: В `karaoke-public` есть Vue-переменная `sett` (SearchView, ZakromaView, AuthorPlaylistView) — это итерационная переменная по `searchResults`/`alb.albumSettings`. В спеке 102 модуль `karaoke-public` явно выведен из охвата (его `settings` — это пользовательские настройки плеера, не `Song`). Переименовывать ли `sett` в `karaoke-public` сейчас? → A: Переименовать всю Vue-переменную `sett` в `song` в `karaoke-public` (она представляет песню, никаких пользовательских настроек плеера это не касается). Файл `karaoke-public/src/player/KaraokePlayer.js` (`LS_SETTINGS_KEY` и подобные) **не трогать** — это реально пользовательские настройки плеера, не `Song`.
- Q: В `webvue3` (admin SPA — отдельное приложение от `karaoke-public`, см. Constitution V) найдено ровно одно вхождение `sett` — `webvue3/src/components/Songs/edit/SubsEdit.vue:183`, и это **label UI-кнопки** в редакторе субтитров (label «sett» рядом с «eol», «endofline»; JS-аргумент `'setting'` в `@click="onOffShowMarkerType('setting')"`). Это НЕ переменная типа `Song` и НЕ контракт backend↔frontend. Контрактные webvue3-файлы (SongsTable.vue, Songs/store.js, HealthReport/*) уже переименованы спекой 102, других `sett`-артефактов в webvue3 нет. Как поступить с этим единственным вхождением? → A: **`webvue3` целиком вне scope** этой задачи (по аналогии со спекой 102 — только контрактные имена, перечисленные там). `SubsEdit.vue:183` остаётся как label UI-кнопки редактора субтитров (это не legacy-переименование, а осознанный UI-выбор автора редактора — «setting marker», не «settings of song»); переименование этого label может сломать UX и не имеет отношения к сущности `Song`.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Читаемые Kotlin-методы без сокращений (Priority: P1)

Разработчик открывает любой Kotlin-файл модулей `karaoke-app` или `karaoke-web` и видит переменные/параметры/поля с типом (или производным значением) `Song` под именами `song`/`songId`/`songList`/и т.д. — а не под историческим сокращением `sett` (или полной формой `settings`, оставшейся в новом коде после спеки 102).

**Why this priority:** Прямой запрос пользователя и единственная цель фичи. `sett`/`settings.songName` в коде — это когнитивный шум («что такое sett? неужели settings?»), замедляющий чтение и приглашающий к ошибке при будущей правке.

**Independent Test:** Открыть любой Kotlin-файл из списка ниже — у функций/полей/лямбда-параметров с типом `Song` имя `song` (или явное производное при коллизии), а не `sett`.

**Acceptance Scenarios:**

1. **Given** функция с `val sett = song` в `MkoString.kt`, **When** применяется рефакторинг, **Then** локальная переименована в `song`, и все `sett.xxx` ниже переписаны на `song.xxx`.
2. **Given** лямбда-параметр `song?.let { sett -> ... }` в `MainController.kt` (оба метода `/songs_update`-семейства), **When** применяется рефакторинг, **Then** лямбда-параметр переименован в `song`, и тело лямбды переписано.
3. **Given** новая функция `publishToVkNow` с `val settings = Song.loadFromDbById(...)` в `ApiController.kt`, **When** применяется рефакторинг, **Then** переменная и все её использования (`settings.idVk`, `settings.saveToDb()`, `song = settings` в именованном аргументе) переименованы в `song`.

### User Story 2 — Шаблоны, фронтенд и SQL без `sett` (Priority: P1)

Разработчик, читающий HTML-шаблоны, Vue-компоненты и SQL-алиасы, видит `song`/`songId`/`songList` там, где сейчас `sett`/`sett.id`/`setts`/алиас таблицы `tbl_songs sett`. Thymeleaf-атрибут `model.addAttribute("sett", ...)` и соответствующие `${sett.*}` в шаблонах переименованы синхронно.

**Why this priority:** Без синхронной правки шаблоны получат `null` после деплоя backend с новым именем атрибута — рендеринг песен в legacy-таблицах админки молча сломается. Без синхронной правки Vue-итераторов (`v-for="sett in ..."` → `v-for="song in ..."`) — оба сайта (`karaoke-public` Search/Zakroma) не отрисуют списки песен.

**Independent Test:** Открыть `karaoke-app/src/main/resources/templates/area_left_column.html` и `SearchView.vue` — переменная цикла/обращения `${song.id}` / `v-for="song in searchResults"`. SQL в `StatBySong.kt` использует алиас `song` (`tbl_songs song on ... song.id = ...`).

**Acceptance Scenarios:**

1. **Given** `model.addAttribute("sett", sett)` в `MainController.kt` для endpoint, рендерящего список песен, **When** применяется рефакторинг, **Then** строка становится `model.addAttribute("song", song)`, и все `${sett.*}` в Thymeleaf-шаблонах (`area_left_column.html`, `songs.html`, `area_center_column.html`, `filter.html`, `zakroma.html`) переписаны на `${song.*}`.
2. **Given** `v-for="sett in searchResults"` в `karaoke-public/src/views/SearchView.vue`, **When** применяется рефакторинг, **Then** итератор и все 60+ обращений `sett.*` в шаблоне/script переименованы в `song.*` (включая `setts` во `AuthorPlaylistView.vue`).
3. **Given** inline SQL с алиасом `tbl_songs sett on e.song_id = sett.id` в `StatBySong.kt`, **When** применяется рефакторинг, **Then** алиас переименован в `song`, и все ссылки на `sett.song_*` в select/group by переписаны на `song.song_*`.

### User Story 3 — Не задеты понятия «настроек», не связанные с `Song` (Priority: P2)

Разработчик, читающий код после рефакторинга, видит, что идентификаторы `sett`/`settings`, которые **не** представляют сущность `Song` (например, имя DTO-поля `albumSettings`, конфигурация плеера в `karaoke-public`, физическое имя колонки БД `settings_id`), остались без изменений. Также комментарии с устаревшей ссылкой `tbl_settings` (там, где имеется в виду именно `tbl_songs`) обновлены.

**Why this priority:** Слепое переименование по тексту «sett/settings» повредило бы несвязанные сущности (DTO-поля, конфигурацию плеера, имена колонок БД) или создало бы ложное впечатление, что миграция БД произведена. Это более рискованный класс ошибок, чем недостаточное переименование.

**Independent Test:** Сравнить `git diff` со списком исключений (`albumSettings` в `ZakromaPublicDto`, `LS_SETTINGS_KEY` в `KaraokePlayer.js`, `@KaraokeDbTableField(name = "settings_id")` в `KaraokeProcess.kt`) — все они остались без изменений; комментарии `tbl_settings` обновлены.

**Acceptance Scenarios:**

1. **Given** `val albumSettings: List<ZakromaAlbumSongPublicDto>` в `ZakromaPublicDto.kt` (имя DTO-поля + JSON-ключ, контракт с фронтендом), **When** применяется рефакторинг, **Then** имя остаётся без изменений — это отдельная задача по DTO-контрактам, не входит в этот рефакторинг.
2. **Given** JSDoc-комментарий `// итерирует v-for="sett in alb.albumSettings"` в `karaoke-public/src/composables/useZakromaStreamProgress.js`, **When** применяется рефакторинг, **Then** имя итератора в комментарии тоже обновлено на `song` (это часть User Story 2 — внутри `v-for` рендерится песня), а `albumSettings` в той же строке остаётся (это поле DTO, см. предыдущий сценарий).
3. **Given** KDoc-комментарий `* персистентные флаги из tbl_settings (Pass 100, ...)` в `ZakromaPublicDto.kt:19`, **When** применяется рефакторинг, **Then** ссылка в комментарии обновлена на `tbl_songs` — таблица давно переименована, KDoc ссылается на уже несуществующее имя.

### Edge Cases

- **Конфликт имён** (`song` уже есть в области видимости): переменная должна получить другое явное имя (`targetSong`, `songArg`, `songFromDb`, `existingSong` и т.п.), а не быть пропущена.
- **`setts` (мн.ч.)** в `AuthorPlaylistView.vue:280` (`const setts = [...(alb.albumSettings || [])].sort(...)`): это итератор списка песен — переименовывается в `songs` (мн.ч.) по смыслу, не в `song`.
- **Алиас SQL `tbl_songs sett`**: в одном файле используется и как `e.song_id = sett.id`, и как `sett.song_name` в SELECT/GROUP BY. Все ссылки на алиас обновляются; имя таблицы `tbl_songs` остаётся без изменений (миграция БД не входит).
- **`sett` внутри `for`/`map`/`filter`/`forEach` по списку песен** (`CrossSong.kt:80-260`, `StatBySong.kt`, `Pictures.kt:145`): лямбда-параметр переименовывается в `song` (для одиночной записи) или в `song` + `song` для каждого элемента итерации.
- **Закомментированный код** с `sett.*` (встречается в `MainController.kt:457-462` — `//            if (!sett.haveVkGroupLink) { ... }`): тоже переименовывается, чтобы при будущей разкомментированности не было сюрприза.
- **`@PostMapping("/playlists/{id}/settings")` в `PublicPlaylistController.kt`** — это endpoint настройки плейлиста, **не** `Song`. Не затрагивается.
- **`KaraokePlatform.settingsFieldPublicationId`/`settingsFieldVersionNumber`** — конфигурация per-платформе, **не** `Song`. Не затрагивается (см. спекy 102, FR-004).
- **`@KaraokeDbTableField(name = "settings_id")` в `KaraokeProcess.kt`** — физическое имя колонки БД; строковый аргумент аннотации остаётся как есть (миграция БД не входит, прецедент спеки 102, FR-005).
- **`tbl_public_settings`** — другая таблица (не Song), конфигурация рантайма; не затрагивается.
- **`LS_SETTINGS_KEY` и аналогичные в `karaoke-public/src/player/KaraokePlayer.js`** — пользовательские настройки плеера; не затрагиваются (прецедент спеки 102, FR-014).
- **Label UI-кнопки `sett` в `webvue3/src/components/Songs/edit/SubsEdit.vue:183`** — это **не Song**: label кнопки типа маркера в редакторе субтитров (наряду с «eol», «endofline», «endofsyllable» и др.). См. Clarifications Q5 и FR-007. `webvue3` целиком вне scope этой задачи (Constitution V — admin SPA отдельное приложение от `karaoke-public`).
- **`webvue3` admin SPA** (как модуль) — вне scope (Constitution V); контрактные `webvue3`-файлы уже были переименованы спекой 102 (SongsTable.vue, Songs/store.js, HealthReport/*), других `sett`-артефактов в нём нет (последний — этот label). См. FR-014.
- **`Song` уже импортирован в файлах `mlt/mko/*.kt`** — переименование `val sett = song` в `val song = song` создаст конфликт. Решение: использовать другое имя (`targetSong`/`renderSong`) при коллизии.
- **Backend и frontend деплой**: `karaoke-public` — отдельный frontend-проект со своим релизным циклом; шаблоны Thymeleaf — внутри `karaoke-app`, деплоятся с backend. Спека требует синхронной правки Thymeleaf+backend (одна ветка, один деплой) по аналогии со спекой 102 FR-016.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: В каждом Kotlin-файле модулей `karaoke-app` и `karaoke-web` параметр функции / локальная переменная / лямбда-параметр / поле с типом `Song` (явным или выводимым) и именем `sett` ДОЛЖЕН быть переименован в `song` (или в другое однозначное имя при конфликте, см. Edge Cases), включая обновление всех использований в теле.
- **FR-002**: Каждый оставшийся Kotlin-параметр/переменная/лямбда-параметр/поле с типом `Song` и именем `settings` (полная форма, забытая спекой 102 в коде, написанном после неё — например, `ApiController.publishToVkNow`, `publishPremiumTelegram`) ДОЛЖЕН быть переименован в `song` по тем же правилам.
- **FR-003**: Thymeleaf-атрибут, передаваемый через `model.addAttribute("sett", ...)` в `MainController.kt`, ДОЛЖЕН быть переименован в `"song"`; все `${sett.*}` и `th:each="...:${sett}"` во всех Thymeleaf-шаблонах модуля `karaoke-app` (`area_left_column.html`, `area_center_column.html`, `songs.html`, `filter.html`, `zakroma.html`) ДОЛЖНЫ быть переписаны на `${song.*}` / `th:each="...:${song}"`. Закомментированные блоки Thymeleaf тоже обновляются.
- **FR-004**: Каждый Vue/JS-итератор (`v-for="sett in ..."`, `forEach { sett -> ... }`, `map { sett -> ... }`, `filter { sett -> ... }`) и параметр метода компонента (`showCoin(sett)`, `dateLabel(sett)`, ...) во **всех** `.vue`/`.js`-файлах модуля `karaoke-public` (кроме `src/player/KaraokePlayer.js` — пользовательские настройки плеера, FR-014) ДОЛЖЕН быть переименован в `song` (или `songs` для коллекций, см. Edge Cases) вместе со всеми обращениями `sett.*`/`setts.*` в той же области.
- **FR-005**: В inline SQL строках в `karaoke-app/.../model/StatBySong.kt` алиас таблицы `tbl_songs sett` ДОЛЖЕН быть переименован в `tbl_songs song`, и все ссылки на алиас в SELECT/JOIN/GROUP BY (`sett.id`, `sett.song_name`, `sett.song_author`, `sett.song_album`) обновлены. Физическое имя таблицы `tbl_songs` остаётся как есть.
- **FR-006**: Имя DTO-поля `albumSettings` и соответствующий JSON-ключ в `ZakromaPublicDto` (и связанные `ZakromaAlbumMetaPublicDto`, `ZakromaStreamMessageDto`) НЕ ДОЛЖНЫ быть изменены этим рефакторингом — это контракт с фронтом и отдельная задача (см. Clarifications Q2).
- **FR-007**: Идентификаторы `settings`/`sett`, не представляющие сущность `Song` и не входящие в список исключений (см. FR-006 и Edge Cases), НЕ ДОЛЖНЫ быть переименованы:
  - `KaraokePlatform.settingsFieldPublicationId`/`settingsFieldVersionNumber` (конфигурация платформ, не Song)
  - `@KaraokeDbTableField(name = "settings_id")` в `KaraokeProcess.kt` и физическое имя колонки БД `settings_id` (миграция БД не входит)
  - `tbl_public_settings` и SQL с этой таблицей (другая таблица, не Song)
  - `@PostMapping("/playlists/{id}/settings")` в `PublicPlaylistController.kt` (настройка плейлиста, не Song)
  - `LS_SETTINGS_KEY` и прочие `settings` в `karaoke-public/src/player/KaraokePlayer.js` и в `webvue3/src/player/KaraokePlayer.js` (настройки плеера, разные приложения — см. Constitution V)
  - `EDITOR_SETTINGS_LS_KEY` и `LocalStorage`-ключи `karaoke-editor-settings`/`karaoke-player-settings` в `webvue3/src/composables/useKaraokeEditor.js` и `webvue3/src/player/KaraokePlayer.js` (настройки редактора/плеера, не Song)
  - Компонент `webvue3/src/components/PublicSettings/PublicSettingsTable.vue` и его JSDoc («Таблица со списком settings») — список `tbl_public_settings`, не Song
  - Label UI-кнопки `sett` в `webvue3/src/components/Songs/edit/SubsEdit.vue:183` (label редактора субтитров «setting marker», не Song; см. Edge Cases)
  - `SyncTarget.key = "settings"` (спека 102, Категория 6)
- **FR-008**: KDoc/JSDoc-комментарии, ссылающиеся на устаревшее имя `tbl_settings` (ZakromaPublicDto.kt:19, ShareLinkSweeper.kt:130 — обе ссылки подразумевают песню/Song), ДОЛЖНЫ быть обновлены на `tbl_songs`. Прочие обновления KDoc/JSDoc — минимальные, только если они ломают актуальность (например, уже переименованные ссылки в JSDoc `useZakromaStreamProgress.js` — нужно синхронизировать с новым именем `song`).
- **FR-009**: После рефакторинга проект ДОЛЖЕН успешно собираться: `./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel`, `./gradlew clean karaoke-app:bootJar karaoke-web:bootJar --parallel` — без новых ошибок компиляции, в т.ч. из-за переименования алиаса `sett` в SQL (PostgreSQL его не валидирует на этапе компиляции, но Kotlin-код перестанет компилироваться, если строка формируется с обращением к алиасу через Kotlin string template).
- **FR-010**: Проверки `./gradlew :karaoke-web:ktlintCheck` + `tools/check-eslint-baseline.sh karaoke-public` + `cd karaoke-public && npm run lint` ДОЛЖНЫ проходить без **новых** нарушений (baseline OK).
- **FR-011**: Рефакторинг НЕ ДОЛЖЕН изменять поведение функций, SQL-запросов (кроме имени алиаса) или HTML/Vue-рендеринга — единственное изменение это **имена** идентификаторов и соответствующих обращений в той же области видимости.
- **FR-012**: При конфликте имён (`song` уже существует в области видимости с другим значением) ДОЛЖНО быть выбрано явное осмысленное имя (`targetSong`, `songArg`, `songFromDb`, `existingSong`, `renderSong` и т.п. по смыслу использования), а не пропуск переименования.
- **FR-013**: Все изменения модуля `karaoke-app` (Kotlin backend + Thymeleaf) ДОЛЖНЫ доставляться одним коммитом/набором коммитов в ветке `260-rename-sett-vars` и деплоиться одним атомарным шагом (по аналогии со спекой 102 FR-016), чтобы Thymeleaf и backend не расходились по имени атрибута.
- **FR-014**: Модуль `karaoke-public` (`SearchView.vue`, `ZakromaView.vue`, `AuthorPlaylistView.vue`, `useZakromaStreamProgress.js`) НЕ входит в синхронную поставку с backend (как было и в спеке 102) — но все Vue/JS правки в нём коммитятся в той же ветке/том же PR, чтобы один review покрыл весь остаточный `sett`. **Модуль `webvue3` (admin SPA) явно вне scope** этой задачи (см. Clarifications Q5): `SubsEdit.vue:183` остаётся как label UI-кнопки, контрактные файлы webvue3 уже переименованы спекой 102, других `sett`-артефактов нет. Замечание: `SubsEdit.vue` упомянутый в исходной версии FR-014 относится к **`webvue3`**, не `karaoke-public`; этот путь к файлу удалён из FR-014, чтобы не вводить в заблуждение.
- **FR-015**: После рефакторинга поиск по кодовой базе не должен находить **изолированного** идентификатора `sett` в Kotlin/HTML/JS/Vue/SQL, представляющего песню (см. SC-001), за исключением случаев, явно разрешённых FR-007.

### Key Entities

- **Kotlin-переменная типа `Song` с именем `sett`/`settings`**: место в Kotlin-коде модулей `karaoke-app`/`karaoke-web`, где объявлен идентификатор под историческим именем `sett` (или забытым `settings`), требующий переименования в `song`.
- **Thymeleaf-атрибут `"sett"`**: строковое имя, под которым `MainController.kt` отдаёт песню в шаблон, и которое читается в шаблонах как `${sett.*}` / `th:each="...:${sett}"`.
- **Vue/JS-итератор `sett`**: имя итератора/лямбда-параметра в `karaoke-public/src/views/{Search,Zakroma,AuthorPlaylist}View.vue` и `useZakromaStreamProgress.js`, представляющее песню (или список — `setts`). Файл `SubsEdit.vue` с label `sett` **относится к `webvue3`, не `karaoke-public`** и не входит в область (см. FR-014, Clarifications Q5).
- **SQL-алиас `sett`**: локальный алиас таблицы `tbl_songs` в inline-SQL строках `StatBySong.kt`.
- **Исключение (не `Song`)**: имя `settings`/`sett`, которое не связано с сущностью `Song` ни по типу, ни по контракту (DTO-поле `albumSettings`, настройки плеера, физическая колонка БД, имя endpoint плейлиста, имя таблицы `tbl_public_settings`, конфигурация платформ) — не входит в область изменений.
- **Забытая ссылка `tbl_settings` в комментариях**: упоминание старого имени таблицы в KDoc/JSDoc, которое (после миграции `28_rename_settings_to_songs.sql`) уже неактуально — обновляется на `tbl_songs` без миграции БД.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Поиск `grep -rn '\bsett\b' --include='*.kt' --include='*.html' --include='*.js' --include='*.vue' --include='*.ts' --include='*.sql' karaoke-app karaoke-web karaoke-public` находит 0 совпадений кроме строк из FR-007/Edge Cases (исключения). Baseline при первичном сканировании — не менее 546 совпадений в 30+ файлах (258 — Kotlin, прочее — Thymeleaf/Vue/JS/inline SQL).
- **SC-002**: Поиск `grep -rn '\bsettings\b' --include='*.kt'` находит 0 совпадений в Kotlin-коде модулей `karaoke-app`/`karaoke-web`, представляющих переменную типа `Song` (на момент первичного сканирования — минимум 5 таких мест в `ApiController.kt:6851, 6900` и других; baseline фиксируется в `tasks.md`/`plan.md`).
- **SC-003**: Полная сборка `karaoke-app` + `karaoke-web` (compileKotlin, ktlintCheck, bootJar) проходит на 100% без новых ошибок.
- **SC-004**: Линт `karaoke-public` (`npm run lint` + `eslint`) проходит без новых нарушений.
- **SC-005**: KDoc-ссылки на `tbl_settings` в `ZakromaPublicDto.kt` и `ShareLinkSweeper.kt` обновлены на `tbl_songs` (0 вхождений устаревшего `tbl_settings` в KDoc/JSDoc).
- **SC-006**: При ручной проверке: рендеринг legacy-страниц админки (`area_left_column.html` со списком песен, `songs.html`) показывает корректные значения полей песни (id, songName, author, year, album, track, color) — переименование атрибута не сломало привязку.
- **SC-007**: Поиск `grep -rn '\bsett\b'` по всему репозиторию (исключая `node_modules`, `build/`, `.git/`) находит только строки из FR-007/Edge Cases. Все изменения доставлены в одной ветке `260-rename-sett-vars` одним PR.

## Assumptions

- Эта задача **дополняет**, а не дублирует спеку 102 (`rename-song-settings-vars`). Спека 102 покрыла полную форму `settings`/`settingsId`/`settings_*`; текущая — сокращённую форму `sett` + оставшиеся единичные `settings` (новый код после спеки 102) + забытые `tbl_settings` в комментариях.
- **Имя `albumSettings` в DTO** (поле `ZakromaPublicDto.albumSettings` + JSON-ключ) — отдельная задача, требующая синхронной правки backend + webvue3 + karaoke-public. Не входит в эту спеку.
- **Физическая схема БД не меняется** (прецедент спеки 102, FR-005): `@KaraokeDbTableField(name = "settings_id")`, физические имена колонок `settings_id` и таблица `tbl_settings` (там, где ещё встречается в старых миграциях) остаются без изменений. SQL-миграция в этой задаче не создаётся.
- **KDoc/JSDoc-комментарии** обновляются точечно (только ссылки `tbl_settings`→`tbl_songs` и комментарии в JSDoc, ссылающиеся на переименованную переменную). Массовый KDoc/JSDoc-review вне этой задачи.
- **Thymeleaf-шаблоны** обновляются в той же ветке/том же PR, что и backend-Kotlin, по аналогии со спекой 102 (FR-016). Один деплой `karaoke-app` атомарно правит backend + шаблоны.
- **`karaoke-public` — отдельный frontend-проект**, его Vue-правки коммитятся в той же ветке `260-rename-sett-vars` (т.к. `sett` — остаток того же legacy-переименования), но деплоится независимо. PR один для review, deploy может быть поэтапным (FR-014).
- **`Song` — современное имя класса** в `karaoke-app/.../model/Song.kt` (миграция завершена, см. спекy 011). Текущая задача только про переменные/идентификаторы, не про сам класс.
- **Использованные исключения** (DTO `albumSettings`, настройки плеера, физическая колонка БД, имя endpoint плейлиста, `tbl_public_settings`, конфигурация платформ, `SyncTarget.key = "settings"`) — все явно перечислены в FR-007 + Edge Cases; их список можно верифицировать grep-проходом перед коммитом (`grep -rn 'sett\|settings'` с фильтром по контексту).
