# Contracts: Переименование `sett`/`settings` → `song` (260-rename-sett-vars)

**Input**: [`spec.md`](./spec.md), [`research.md`](./research.md), [`data-model.md`](./data-model.md)
**Дата**: 2026-08-28

## TL;DR

> **Задача чисто переименовательная. Никакие runtime-контракты проекта (HTTP-endpoints, DTO-JSON, SSE-payloads, Thymeleaf attributes передаваемые через `model`, wire-level identifiers) НЕ меняются по форме — изменяется только то, что уже было переименовано ранее (спека 102) или что является исключением по FR-006/FR-007.**

`albumSettings`, физические колонки БД, `SyncTarget.key = "settings"` и подобное — **вне scope** (явные deferrals в `research.md` §5).

## Что этот документ фиксирует

Контракты для текущей задачи — это перечень **внутренних** идентификаторов, которые сейчас называются `sett`/`settings` (где значение/тип — `Song`), и которые **внутренние** потребители (шаблоны, SQL, Vue-итераторы, Kotlin-вызывающие) должны синхронно обновить. Никаких новых public API не появляется, никаких старых не переименовывается как wire-контракт.

> Сравни: в спеке 102 был документ `contracts/rename-contracts.md`, который описывал **wire-level** контракты (DTO-поля, SSE-ключи, HTTP-параметры, Thymeleaf-имена). Здесь такого контракта нет — потому что wire-контракты в этой задаче не задеваются. Все правки — внутренние (Kotlin-internal, Thymeleaf-internal в пределах одного WAR-артефакта, Vue-internal в одном проекте `karaoke-public`).

## Перечень внутренних контрактов, затрагиваемых этой задачей

### Contract-1: Thymeleaf attribute name в `MainController.kt` (legacy админка)

**До переименования**:
```kotlin
// karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/MainController.kt
@PostMapping("/songs_update", ...) // (один из двух методов)
fun songsUpdate(
    @RequestParam(name = "select_id") selectId: Long,
    ...,
): String {
    val song = Song.loadFromDbById(selectId) ?: return "redirect:/songs"
    song?.let { sett ->
        sett.fields[SongField.ID] = song_id
        sett.fields[SongField.NAME] = song_songName
        // ... ~50 строк sett.fields[SongField.*] = song_*
        sett.saveToDb()
        sett.saveToFile()
    }
    model.addAttribute("sett", song)  // ← атрибут для шаблона
    return "songs"
}
```

```html
<!-- karaoke-app/src/main/resources/templates/songs.html :~2286 -->
<tr th:each="song:${sett}" onClick="mouseClickRow(this)"
    th:style="'background-color: ' + ${song.color}"
    th:id="'row' + ${song.id}">
```

**После переименования**:
```kotlin
song?.let { song ->
    song.fields[SongField.ID] = song_id
    ...
    song.saveToDb()
    song.saveToFile()
}
model.addAttribute("song", song)
```

```html
<tr th:each="song:${song}" onClick="mouseClickRow(this)" ...>
```

> **Синхронный атомарный шаг**: backend (`model.addAttribute("song", ...)`) + Thymeleaf (`${song.*}` / `th:each="...:${song}"`). Шаблоны в одном WAR/bootJar — один deployment, один atomic commit (FR-013 спеки).

**Затрагиваемые файлы** (полный список по baseline):
- `karaoke-app/.../controllers/MainController.kt` — обе функции `/songs_update` (~строки 1765 и 1954) + все `model.addAttribute("sett", ...)` + все `val sett = ...` / `settings: Song` параметры/локалы.
- `karaoke-app/src/main/resources/templates/area_left_column.html` (~:115) — `th:each="song:${sett}"`.
- `karaoke-app/src/main/resources/templates/area_center_column.html` — закомментированные блоки с `${sett.*}`.
- `karaoke-app/src/main/resources/templates/songs.html` (~:2286) — `th:each="song:${sett}"`.
- `karaoke-app/src/main/resources/templates/filter.html` (~:376-410) — итерация по песням.
- `karaoke-app/src/main/resources/templates/zakroma.html` (~:436-500) — итерация по песням.

> Замечание: `karаоке-web/src/main/resources/templates/{filter,zakroma,song,testpage}.html` тоже имеют `${sett.*}` — но это **legacy-контракт между `karaoke-web` контроллером и его шаблонами**, тоже атомарно в одном WAR/bootJar. Переименовывается в том же PR.

### Contract-2: Vue v-for iterator в `karaoke-public` (публичный SPA)

**До переименования**:
```vue
<!-- karaoke-public/src/views/SearchView.vue :74 -->
<tr v-for="sett in searchResults" :key="sett.id" class="km-tr">
  <td class="km-td km-td-center">{{ sett.year }}</td>
  <td class="km-td">{{ sett.album }}</td>
  ...
</tr>
```

**После переименования**:
```vue
<tr v-for="song in searchResults" :key="song.id" class="km-tr">
  <td class="km-td km-td-center">{{ song.year }}</td>
  <td class="km-td">{{ song.album }}</td>
  ...
</tr>
```

> **НЕ атомарно с backend** — `karaoke-public` деплоится отдельно (см. spec.md FR-014). Это короткое окно (одна сборка, ~5-10 минут) когда `karaoke-public` ещё со старым `sett`-итератором, а backend уже использует `song`-имена. **Никакого риска** — все обращения к API/`Song`-DTO уже используют `song`-имена (из спеки 102 + этого переименования). Vue-итератор затрагивает только рендеринг локального state (`searchResults` уже `Song[]`).

**Затрагиваемые файлы**:
- `karaoke-public/src/views/SearchView.vue` (~60+ обращений `sett.*`).
- `karaoke-public/src/views/ZakromaView.vue` (~60+ обращений `sett.*` + `v-for="sett in item.alb.albumSettings"`).
- `karaoke-public/src/views/AuthorPlaylistView.vue:280` — `const setts = [...(alb.albumSettings || [])].sort(...)` → `songs`.
- `karaoke-public/src/composables/useZakromaStreamProgress.js:225` — JSDoc обновить имя.

> **Не затрагивается**: `karaoke-public/src/player/KaraokePlayer.js` (`LS_SETTINGS_KEY` и т.п. — настройки плеера, не Song). См. spec.md FR-007 + Clarifications Q4.

### Contract-3: SQL inline alias в `StatBySong.kt`

**До переименования**:
```kotlin
// karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/StatBySong.kt :458-491
val sql =
    """
    select
        e.song_id,
        sett.song_author,
        ...
    from tbl_events e
    left join tbl_songs sett on e.song_id = sett.id
    where e.song_id is not null and e.song_id > 0
    group by e.song_id, sett.song_author, sett.song_album, sett.song_name
    order by total desc, e.song_id asc
    limit $limit offset $offset
    ;
    """.trimIndent()
```

**После переименования**:
```kotlin
val sql =
    """
    select
        e.song_id,
        song.song_author,
        ...
    from tbl_events e
    left join tbl_songs song on e.song_id = song.id
    where e.song_id is not null and e.song_id > 0
    group by e.song_id, song.song_author, song.song_album, song.song_name
    order by total desc, e.song_id asc
    limit $limit offset $offset
    ;
    """.trimIndent()
```

> **Не изменяется**:
> - Имя таблицы `tbl_songs` (физическое имя, миграция БД не входит).
> - Поведение SQL-запроса (алиас может быть любым, PostgreSQL не валидирует).
> - Подстановка `$limit` / `$offset` через Kotlin string template — продолжает работать.
>
> Альтернативное имя `s` (single-letter) отклонено: `song` самодокументируется и согласовано с остальной переименовательной политикой.

**Затрагиваемые места**: 2 SQL-запроса в `StatBySong.kt` (~:485, ~:611).

### Contract-4: KDoc/JSDoc-комментарии с устаревшими ссылками

**До переименования**:
```kotlin
// karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/ZakromaPublicDto.kt :19
/**
 * DTO для zakroma album settings public: сериализуемое представление для API/UI.
 *
 * @property contentReady `song.isContentReady` — персистентные флаги из tbl_settings (Pass 100,
 * ...).
 */
```

**После переименования**:
```kotlin
/**
 * DTO для zakroma album songs public: сериализуемое представление для API/UI.
 *
 * @property contentReady `song.isContentReady` — персистентные флаги из tbl_songs (Pass 100,
 * ...).
 */
```

> **Не wire-level**: KDoc/JSDoc — комментарии для Dokka/typedoc, которые читают разработчики и `tools/check-kdoc-coverage.sh` / `check-jsdoc-coverage.sh`. Они не сериализуются и не влияют на runtime.

**Затрагиваемые места**:
- `karaoke-web/.../dto/ZakromaPublicDto.kt:9, 19` — 2 ссылки `tbl_settings` + 1 «settings public» → «songs public».
- `karaoke-web/.../services/ShareLinkSweeper.kt:130` — 1 ссылка `tbl_settings`.
- `karaoke-public/src/composables/useZakromaStreamProgress.js:225` — JSDoc с `v-for="sett in ..."` (контракт на уровне docs, не code).

## Что НЕ контракт этой задачи (deferrals из research.md §5)

- DTO-поле `albumSettings` + JSON-ключ + webvue3-sync — **другая задача** (Contract из спеки 102 был уже отработан там, но не покрывал wire-rename этого поля).
- HTTP-endpoints, query-parameters, JSON body keys, SSE event payloads — **никаких изменений**, спека 102 их покрыла, нового в этой задаче нет.
- Файлы `webvue3` (`KaraokePlayer.js`, `useKaraokeEditor.js`, `PublicSettingsTable.vue`, `SubsEdit.vue`) — вне scope (Clarifications Q5).
- `tbl_public_settings` SQL/SQL-поля, `KaraokePlatform.settingsField*` — конфигурация, не Song.
- `@PostMapping("/playlists/{id}/settings")` в `PublicPlaylistController.kt` — endpoint плейлиста, не Song.

## Сводка

> **Zero новых runtime-контрактов.** Zero изменений JSON-ключей, HTTP-параметров, wire-уровня. Только **имена внутренних идентификаторов** + согласованные Thymeleaf-атрибуты + Vue-итераторы + SQL-алиасы + KDoc-комментарии. Все они в пределах одного deployment unit (один WAR для backend, один bundle для `karaoke-public`).

Поэтому в этой задаче нет отдельного документа контрактов в формате OpenAPI/asyncapi/json-schema (как в спеке 102 `contracts/rename-contracts.md`) — он бы был пустой.
