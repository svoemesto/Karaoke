# Data Model: Переименование `sett`/`settings` → `song` (260-rename-sett-vars)

**Input**: [`spec.md`](./spec.md), [`research.md`](./research.md)
**Дата**: 2026-08-28

## TL;DR

> **Задача чисто переименовательная. Физическая схема БД НЕ меняется. Никаких новых сущностей, атрибутов, миграций.**

Эта фича **не затрагивает** модель данных проекта (`Song`, `tbl_songs`, связанные DTO). Она затрагивает только **имена идентификаторов в исходном коде** (Kotlin-поля, Thymeleaf-атрибуты, Vue-итераторы, SQL-алиасы, KDoc-комментарии), значения которых остаются идентичными по типу и семантике.

## Затронутые сущности (без изменения формы, только имени)

### `Song` (Kotlin class, `karaoke-app/.../model/Song.kt`)

**Не меняется**:
- Имя класса: `Song` (миграция Settings→Song завершена, спека 011).
- Физическая таблица: `tbl_songs` (миграция `deploy/karaoke-db/28_rename_settings_to_songs.sql`).
- Физическая колонка `settings_id` в других таблицах (например, `KaraokeProcess.settings_id` через `@KaraokeDbTableField(name = "settings_id")` в `KaraokeProcess.kt`) — **НЕ переименовывается** (спека 102 FR-005, прецедент).

**Меняется** (только Kotlin-имена переменных, не сущность):
- Локальные переменные / параметры / лямбда-параметры / поля с типом `Song`, поименованные `sett` или `settings` — переименовываются в `song` (или осмысленное производное при конфликте).

**Примеры** (НЕ исчерпывающий список, а паттерны):
- `fun getVKPictureBase64(settings: Song): String` → `fun getVKPictureBase64(song: Song): String`. (Имена в файлах спеки 102 не сохранились, но контекст из `UtilsPictures.kt` — теперь имена уже правильные.)
- `val sett = song` (в `mlt/mko/*.kt`) → `val targetSong = song` (или `renderSong`, `songForRender`) — из-за конфликта с параметром `song: Song` в том же классе.
- `song?.let { sett -> ... }` (в `MainController.kt`) → `song?.let { song -> ... }` → `song?.let { it -> ... }` (по смыслу; см. tasks.md T019).
- `model.addAttribute("sett", sett)` → `model.addAttribute("song", song)`.
- `${sett.id}` в Thymeleaf → `${song.id}`.
- `v-for="sett in searchResults"` в Vue → `v-for="song in searchResults"`.
- `tbl_songs sett on e.song_id = sett.id` в inline SQL → `tbl_songs song on e.song_id = song.id`.

## Затронутые сущности (с изменением формы и/или контракта) — НЕТ

Если бы задача включала:
- Переименование физической колонки `settings_id` → `song_id` — нужна SQL-миграция, новый `@KaraokeDbTableField(name = "song_id")`, запись в `deploy/karaoke-db/`, переписывание всех триггеров `recordhash` на новый хэш.
- Переименование DTO-поля `albumSettings` → `albumSongs` — нужна синхронная правка backend+webvue3+frontend, JSON-ключа, всех `*MetaPublicDto`, риск регрессии в стриме NDJSON.

Оба варианта **явно вне scope** (Clarifications Q1 + Q2 + FR-005 + FR-006 спеки).

## Связи (relationships)

Без изменений. Сущности и их связи (`Song` ↔ `Album`, `Song` ↔ `Author`, `Song` ↔ `KaraokeProcess`, и т.д.) не затрагиваются.

## Валидации

Не затрагиваются (методы валидации остаются те же, только имена параметров меняются).

## Lifecycle / State transitions

Не затрагиваются.

## Файлы с потенциальным конфликтом имён при переименовании

> **Это список для `tasks.md` Phase 2** (baseline + пофайловый план переименования). Полный baseline-таблица в `research.md` §1.

### `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/mlt/mko/`

**13 файлов**, в каждом есть и параметр `song: Song` (или `val song`), и локальная `val sett = song`. Прямое переименование создаст shadowing.

Файлы (точно):
- `MkoChordPictureElement.kt`
- `MkoChordPictureFader.kt`
- `MkoChordPictureImage.kt`
- `MkoChordPictureLines.kt`
- `MkoChords.kt`
- `MkoElement.kt`
- `MkoFill.kt`
- `MkoLines.kt`
- `MkoLineTrack.kt`
- `MkoMelodyNote.kt`
- `MkoMelodyTabs.kt`
- `MkoSepar.kt`
- `MkoString.kt`

**Решение**: использовать `targetSong` / `renderSong` / `songForRender` для локальной переменной вместо `song` — задача `tasks.md` Phase 3 T011 выбирает имя per-file.

### `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/MainController.kt`

**Несколько сигнатур `settings: Song`** + **лямбда-параметры `sett` в `song?.let { sett -> ... }` (для обоих методов `/songs_update`-семейства: строки ~1765, ~1954)** + **`model.addAttribute("sett", ...)`** + **`"sett"` строковый литерал в Thymeleaf-френдли имени**.

**Решение**: переименовать ВСЕ эти места последовательно (внутренние `sett` → `song` или `it`, плюс строковое `"sett"` → `"song"`), плюс синхронно шаблоны Thymeleaf в `tasks.md` Phase 4.

### `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt`

**3 места `val settings = Song.loadFromDbById(...)`** (в методах `publishToVkNow`, `publishPremiumTelegram`, `publishPremiumVk`). Это **забытая спекой 102 категория** (полная форма `settings` в новом коде).

**Решение**: переименовать все 3 в `song` со всеми обращениями (`settings.idVk`, `settings.saveToDb()`, `song = settings` в именованном аргументе).

### `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/TelegramUpdatesConsumer.kt`

**1 место** (`val sett = Song.loadFromDbById(...)`, ~строка 124). Прямое переименование в `song` безопасно (нет одноимённого параметра).

### `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/{StatBySong,CrossSong,Pictures}.kt`

**Лямбда-параметры `sett`** в `forEach`/`map`/`filter`/`let`. Прямое переименование безопасно (имена методов и параметров не конфликтуют).

**Особое**: `StatBySong.kt` имеет inline SQL с алиасом `sett` — это переименовывается внутри SQL-литерала (между `"""`), и Kotlin-компилятор это съест без проблем.

### `karaoke-app/src/main/resources/templates/`

**Файлы**: `area_left_column.html`, `area_center_column.html` (закомментированные блоки), `songs.html`, `filter.html`, `zakroma.html`. Все имеют `${sett.*}` и/или `th:each="song:${sett}"`.

**Решение**: переименовать шаблонные обращения `${sett.*}` → `${song.*}` и `th:each="x:${sett}"` → `th:each="x:${song}"` синхронно с `MainController.kt` `model.addAttribute`.

### `karaoke-public/src/views/{Search,Zakroma}View.vue` и `AuthorPlaylistView.vue`, `useZakromaStreamProgress.js`, `SubsEdit.vue` (НЕ трогать)

**Большое количество обращений** `sett.*` (60+) и `setts` (мн.ч.). Переименование в `song` безопасно (конфликта с другими итераторами нет в этих scope).

**`AuthorPlaylistView.vue:280`**: `const setts = [...(alb.albumSettings || [])].sort(...)` — переменная коллекции, переименовать в `songs`.

**JSDoc** `useZakromaStreamProgress.js:225` — обновить строку-документацию с `v-for="sett in ..."` на `v-for="song in ..."`.

### `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/{MainController,PublicApiController}.kt`

**Аналог `karaoke-app/.../MainController.kt`** — `model.addAttribute("sett", ...)` и `val sett = ...` локальные, и несколько `sett.id` в шаблонах `karаоке-web/src/main/resources/templates/{filter,zakroma,song,testpage}.html`.

**Решение**: переименовать в `song` плюс синхронно шаблоны `karаоке-web/src/main/resources/templates/*` (если найдены в baseline).

## Затронутые комментарии (KDoc/JSDoc)

**Файлы**:
- `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/ZakromaPublicDto.kt:9, 19` — упоминание «zakroma album settings public» и «`tbl_settings`».
- `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/ShareLinkSweeper.kt:130` — упоминание `tbl_settings`.

**Решение**: переименовать `tbl_settings` → `tbl_songs` в комментариях (FR-008 спеки).

## Сводка

> **Никаких новых сущностей, атрибутов, отношений, валидаций, лайфциклов.**
> **Никаких изменений в физической схеме БД.**
> **Никаких изменений в DTO-полях или JSON-контрактах.**
> **Изменяются только имена идентификаторов в исходном коде + строковые комментарии.**

Это — **исключительно рефакторинг идентификаторов**. Именно поэтому Phase 1 дизайн-секции data-model минимален.
