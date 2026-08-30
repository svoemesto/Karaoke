# Data Model: Поле `song_name_censored` в `tbl_songs`

## Колонка `tbl_songs.song_name_censored`

Файл миграции: `deploy/karaoke-db/42_song_name_censored.sql`
(применяется вручную на LOCAL + PROD, см. Конституцию Принцип II/III).

| Аспект | Значение | Обоснование |
|---|---|---|
| Имя колонки | `song_name_censored` | Совпадает с уже существующим computed-свойством `Song.songNameCensored` (см. `Song.kt:608`) — переход прозрачный. |
| Тип | `VARCHAR(255)` | Конвенция проекта для однострочных полей (см. `31_entity_description_fields.sql` для `short_description`, `warning`). research.md §1. |
| NULL/NOT NULL | `NOT NULL DEFAULT ''` | Гарантирует, что после миграции все строки имеют значение (пусть даже пустое). Совместимо со старым кодом, который НЕ знает про колонку — DEFAULT покрывает INSERT. |
| Бэкфилл | `UPDATE tbl_songs SET song_name_censored = song_name WHERE id > 0` (после `ALTER TABLE`) | research.md §2 — копия, не цензурирование. Цензурирование делает фоновая функция `rescanAllCensoredNames` уже после деплоя. |
| Recordhash | Включается в md5 `update_tbl_songs_recordhash()` (см. `31_entity_description_fields.sql` как образец) | Без этого LOCAL↔SERVER diff не увидит новую колонку — синхронизация сломается (Принцип II/III). |
| Backfill recordhash | `UPDATE tbl_songs SET recordhash = md5(...) WHERE id > 0` (по образцу `31_entity_description_fields.sql:220-318`) | Иначе после миграции старые строки будут иметь устаревший recordhash до первого UPDATE каждой строки. |

## Сущность `Song` (in-memory)

Файл: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt`.

### Enum `SongField`

Файл: `karaoke-app/.../model/SongField.kt`.

Добавить новое значение (рядом с `NAME`, см. текущий порядок в `SongField.kt:10-12`):

```kotlin
// Цензурированное название песни (предвычисленное по словарю «Censored» или
// введённое вручную в SongEdit). Замена song.songName.censored(database) на
// горячем пути. См. specs/277-song-name-censored.
NAME_CENSORED,
```

### Свойство `songNameCensored` (геттер + сеттер)

Файл: `Song.kt:607-608` (текущая реализация — computed через `.censored()`):

```kotlin
// БЫЛО (Song.kt:607-608):
val songName: String get() = fields[SongField.NAME] ?: ""
val songNameCensored: String get() = songName.censored(database)

// СТАЛО (read/write через SongField, без словарного вызова):
val songName: String get() = fields[SongField.NAME] ?: ""
var songNameCensored: String
    get() = fields[SongField.SONG_NAME_CENSORED] ?: ""
    set(value) { fields[SongField.SONG_NAME_CENSORED] = value }
```

`var`, не `val` — сеттер нужен для SongEdit через существующий
механизм `setCurrentSongField` в `webvue3-store` (`store.js:1610`).

### Точки правки в `Song.kt`

| # | Метод/функция | Что меняется | Строки (по состоянию на Pass 277) |
|---|---|---|---|
| 1 | `getSqlToInsert()` (sync=false) | + `("song_name_censored", song.songNameCensored)` в `fieldsValues` (после `("song_name", …)`) | `~5872` (рядом с `song_name`) |
| 2 | `getSqlToInsert(sync: Boolean)` | то же для `tbl_songs_sync` | (та же функция, обе ветки) |
| 3 | `loadListFromDb` (`SELECT *` цикл) | + `rs.getString("song_name_censored")?.let { song.fields[SongField.SONG_NAME_CENSORED] = it }` сразу после `song_name` | `~7709` |
| 4 | `loadFromDbById` | то же (для одиночной загрузки) | аналогично (см. функцию в районе `Song.kt:7888`) |
| 5 | `saveToDb()` — формирование diff'а | ПЕРЕД вычислением `diff`: если `fields[SongField.SONG_NAME_CENSORED]` пустое И `fields[SongField.NAME]` непустое — `fields[SongField.SONG_NAME_CENSORED] = songName.censored(database)` (FR-003, research.md §3) | в `saveToDb()` (около `Song.kt:5157` или перед `getDiff(this, savedSong)`) |
| 6 | **`getDiff(settA, settB)`** | **+ `if (settA.songNameCensored != settB.songNameCensored) result.add(RecordDiff("song_name_censored", …))`** (рядом с другими `RecordDiff`-строками, после `warning`) | **~6830 (рядом с `warning`)** — критично для прокидывания изменений через sync, см. bug-fix note ниже |
| 7 | `loadFromDbById` (для `rescanAllCensoredNames`) | Используется существующий метод — никаких правок | n/a |

> **Bug-fix note (обнаружено после применения миграции на проде, 2026-08-30):**
> `getDiff` явно сравнивает каждое поле по имени (`if (settA.X != settB.X) result.add(RecordDiff("X", …))`).
> Без явного сравнения `songNameCensored` поле остаётся «невидимым» для sync-механизма:
> `recordhash` на LOCAL меняется (триггер включает колонку), sync видит расхождение, но
> `UPDATE` SET строится ТОЛЬКО из diff'а — а его там нет → REMOTE не получает новую цензурированную
> форму, расхождение recordhash'ей остаётся навсегда. Симптом: «в локальной базе цензурированные
> данные есть, а на сервер не попадают». Фикс — добавить строку #6 в таблицу.

### Точки замены `song.songName.censored(database)` → `song.songNameCensored`

Политика «доверие редактору» (Clarifications Session 2026-08-30 Q1/A):
никакого re-censor на горячем пути при формировании DTO/шаблона. Все
нижеперечисленные выражения заменяются на чтение поля `song.songNameCensored`
(без вызова `censored()` и без запроса в `tbl_dictionaries`):

| Файл | Строки | Метод/функция |
|---|---|---|
| `Song.kt` | `608` | определение `songNameCensored` (см. выше) |
| `Song.kt` | `4651` | (один из `cutByWords`-хедеров) |
| `Song.kt` | `4663` | `getTextBoostyHead()` |
| `Song.kt` | `4665` | `getTextBoostyFilesHead()` |
| `Song.kt` | `4707` | (хедер с `songVersion.text`) |
| `Song.kt` | `4715` | (хедер со ссылкой SM) |
| `Song.kt` | `4724` | (хедер со ссылкой SM, вариант) |
| `Song.kt` | `4778` | `getDescriptionDemoHeader()` |
| `Song.kt` | `4781` | `getDescriptionVkDemoHeader()` |
| `Song.kt` | `4784` | (Demo со ссылкой SM) |
| `Song.kt` | `4789` | (Demo со ссылкой SM, вариант) |
| `Song.kt` | `4822` | `getVKGroupDescription()` |
| `Publication.kt` | `91, 99, 107, 115, 123, 131, 139, 147, 155, 163, 171, 179, 187, 195` | `publish10..publish23` — 14 top-песен |
| `VkTemplateService.kt` | `146` | `"songNameCensored" to song.songNameCensored` (в `buildReplacements`); + 2 правки в KDoc (lines 24, 72) |
| `TelegramTemplateService.kt` | `103` | то же в `buildReplacements`; + 2 правки в KDoc (lines 30, 59) |
| `NewsTemplateService.kt` | `252` | то же в `buildReplacements`; + 2 правки в KDoc (lines 33, 87) |
| `UtilsPictures.kt` | `189, 263, 330, 399, 482, 597, 976` | 7 замен в текстовых полях для генерируемых картинок |

**НЕ заменяются** (это не «горячий путь публикации», а
  low-level утилита, оставляем как есть):
- `String.censored(database)` (определение в `Extentions.kt:214-230`) —
  функция остаётся в коде для других возможных применений
  (`getCensoredPair` для `Zakroma.kt` — там другой паттерн, не
  касается этой фичи).

## Фоновая функция `Utils.rescanAllCensoredNames()`

Файл: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt`
(по соседству с существующей `customFunction()`, строка 96).

```kotlin
@Volatile private var isCensoredRescanInProgress: Boolean = false

/**
 * Фоновая функция: реckan всех `tbl_songs.song_name_censored` по актуальному
 * словарю «Censored». По образцу `customFunction()` (строка 96).
 * Идемпотентный повторный запуск после завершения; защита от гонок — in-memory
 * флаг (single-instance JVM, см. research.md §4).
 *
 * @return "OK" если запущено, "ALREADY_RUNNING" если уже идёт.
 * @see specs/277-song-name-censored
 */
fun rescanAllCensoredNames(
    storageService: KaraokeStorageService,
    lyricsFinderService: LyricsFinderService,
    storageApiClient: StorageApiClient,
): String {
    if (isCensoredRescanInProgress) return "ALREADY_RUNNING"
    isCensoredRescanInProgress = true
    thread {
        try {
            // SELECT id FROM tbl_songs ORDER BY id (один запрос)
            // для каждого id: SELECT song_name, song_name_censored FROM tbl_songs WHERE id = ?
            // сравнить song_name.censored(database) с song_name_censored
            // если отличается — UPDATE tbl_songs SET song_name_censored = ? WHERE id = ?
            // по завершении — SseNotification.send(...) с числом обработанных/изменённых строк
        } catch (e: Exception) {
            println("rescanAllCensoredNames: ошибка — ${e.message}")
        } finally {
            isCensoredRescanInProgress = false
        }
    }
    return "OK"
}
```

## Endpoint'ы

| Метод | Путь | Файл | Назначение |
|---|---|---|---|
| `POST` | `/api/utils/rescanallcensorednames` | `karaoke-app/.../controllers/ApiController.kt` (рядом с `customfunction`, строка 5876) | Вызов из `webvue3` через `store.js` action |
| `GET`  | `/utils/rescanallcensorednames` | `karaoke-app/.../controllers/MainController.kt` (рядом с `customfunction`, строка 155) | Зеркало для Thymeleaf-страниц (по образцу существующего GET-зеркала `customfunction`) |

Оба делегируют в `Utils.rescanAllCensoredNames(...)` и возвращают строку
(`"OK"` / `"ALREADY_RUNNING"`).

## UI

### Кнопка в `HomeView.vue`

Файл: `webvue3/src/views/HomeView.vue` (рядом с существующим
`customFunction()` методом, строки 742-765).

```html
<button class="btn btn-warning" @click="rescanAllCensoredNames">
  Пересканировать цензурированные названия песен
</button>
```

```js
methods: {
  // ... existing methods ...
  rescanAllCensoredNames() {
    this.customConfirmParams = {
      header: 'Подтвердите действие',
      body: `Пересканировать цензурированные названия ВСЕХ песен (≈18k строк) по
             актуальному словарю «Censored»?<br><strong>Операция перезапишет ВСЕ
             цензурированные названия, включая ручные правки в SongEdit.</strong>
             Идёт в фоне, итог придёт SSE-уведомлением.`,
      timeout: 15,
      callback: this.doRescanAllCensoredNames,
    }
    this.isCustomConfirmVisible = true
  },
  doRescanAllCensoredNames() {
    this.$store.dispatch('rescanAllCensoredNamesPromise').then((response) => {
      // ... показать тост-подтверждение ...
    })
  },
}
```

### Store action `webvue3/src/components/Songs/store.js`

```js
rescanAllCensoredNamesPromise() {
  let request = { method: 'POST', url: '/api/utils/rescanallcensorednames' }
  return promisedXMLHttpRequest(request)
},
```

### Поле в `SongEdit.vue`

Файл: `webvue3/src/components/Songs/edit/SongEdit.vue` (сразу под полем
«Композиция», строки 106-126).

```html
<div class="label-and-input">
  <div class="label" :title="censorHint">Композиция (цензурированная):</div>
  <input
    v-model="song.songNameCensored"
    class="input-field"
    :title="censorHint"
  />
  <button class="btn-round" :disabled="notChanged('songNameCensored')"
          @click="undoField('songNameCensored')">
    <img alt="undo" src="...icon_undo.svg" />
  </button>
  <button class="btn-round" :disabled="!song.songNameCensored"
          @click="copyToClipboard(song.songNameCensored, 'songNameCensored')">
    <img alt="copy" src="...icon_copy.svg" />
  </button>
  <button class="btn-round" @click="pasteFromClipboard('songNameCensored')">
    <img alt="paste" src="...icon_paste.svg" />
  </button>
</div>
```

`censorHint` — строка из Assumptions спеки: «Ручное значение используется
в публикациях (VK/Telegram/News) и публичном API БЕЗ повторной
фильтрации. Редактируйте на свой страх и риск.»