# Research: 279 — Восстановить поиск родителя при добавлении файлов из папки

**Дата**: 2026-08-31 (обновлено после наблюдения пользователя)
**Спека**: [spec.md](spec.md)
**План**: [plan.md](plan.md)

## Цель

Подтвердить техническую гипотезу корневой причины сломанного поиска «родителя» в `Utils.findDuplicateOriginal` / `Utils.applyDuplicateOriginal` (вызывается из `ApiController.doCreateFromFolder`) и предложить точечный фикс.

## Ключевое наблюдение пользователя (ОБНОВЛЕНО 2026-08-31)

> «В описанном кейсе — и родитель не появился, и поиска текста в интернете не было запущено. Если не было запущено поиска текста — значит родитель был всё-таки найден, но т.к. его нет — значит он перезатёрся — искать надо в первую очередь это!»

> «Очень похоже на регресс после правки когда затирались key и bpm».

Логика пользователя:
1. `findYandexSongLyrics` запускается **только** если `textResolved = false`.
2. `textResolved = true` выставляется **только** если `applyDuplicateOriginal` отработал без исключения (либо если `findAudioParentByWaveform` отработал и применил маркеры).
3. Если поиск текста не запустился → значит `applyDuplicateOriginal` (или аудио-родитель) был успешен.
4. Но `root_id = 0` — значит `applyDuplicateOriginal` либо записал `root_id = 0` (?), либо записал `root_id > 0`, но потом это значение было перезатёрто.

**Гипотеза пользователя**: это регресс после спеки 278 (`acfb936d` от 2026-08-30, "race condition в doCreateFromFolder — тональность не теряется при синхронном поиске текста"). В этом коммите `applyDuplicateOriginal` был изменён:

**БЫЛО (до спеки 278):**
```kotlin
fun applyDuplicateOriginal(newSong: Song, original: Song) {
    newSong.rootId = original.id
    newSong.sourceText = original.sourceText
    newSong.resultText = original.resultText
    newSong.sourceMarkers = original.sourceMarkers
    newSong.formattedTextSong = original.formattedTextSong
    newSong.formattedTextTabs = original.formattedTextTabs
    newSong.formattedTextChords = original.formattedTextChords
    newSong.fields[SongField.ID_STATUS] = "1"
    newSong.saveToDb()
}
```

**СТАЛО (после спеки 278):**
```kotlin
fun applyDuplicateOriginal(newSong: Song, original: Song) {
    // specs/278-fix-key-loss-on-lyrics-search: между Song.createFromPath() и этим saveToDb()
    // может пройти достаточно времени, чтобы параллельный процесс успел обновить
    // song_tone/song_bpm/url'ы стемов в БД. Перезагружаем объект из БД, чтобы getDiff() не
    // включил эти поля в UPDATE.
    val songToSave =
        Song.loadFromDbById(
            id = newSong.id,
            database = newSong.database,
            storageService = newSong.storageService,
            storageApiClient = newSong.storageApiClient,
        ) ?: newSong
    songToSave.rootId = original.id
    songToSave.sourceText = original.sourceText
    songToSave.resultText = original.resultText
    songToSave.sourceMarkers = original.sourceMarkers
    songToSave.formattedTextSong = original.formattedTextSong
    songToSave.formattedTextTabs = original.formattedTextTabs
    songToSave.formattedTextChords = original.formattedTextChords
    songToSave.fields[SongField.ID_STATUS] = "1"
    songToSave.saveToDb()
}
```

## Гипотезы (отсортированы по вероятности)

### H1 (CONFIRMED, по анализу кода от 2026-08-31) — `applyDuplicateOriginal` после спеки 278 пишет в БД через `songToSave`, но `newSong` в памяти остаётся «грязным». `findAudioParentByWaveform` затем перезаписывает `root_id = 0`.

**Точная корневая причина** (выявлена анализом кода без логов):

В `applyDuplicateOriginal` после спеки 278 (`acfb936d`):
```kotlin
val songToSave = Song.loadFromDbById(newSong.id, ...) ?: newSong  // НОВЫЙ объект из БД
songToSave.rootId = original.id  // пишем в songToSave, не в newSong
songToSave.saveToDb()  // записывает root_id=50 в БД
// ⚠️ newSong.rootId всё ещё 0 в памяти!
```

`Song.loadFromDbById` создаёт **новый** объект `Song` через конструктор:
```kotlin
val song = Song(database = database, storageService = ..., storageApiClient = ...)
```
(см. `Song.kt:7731` в `loadListFromDb`). Этот объект **не равен** `newSong` (который был создан в `Song.createFromPath`).

Поэтому после `applyDuplicateOriginal`:
- В БД: `root_id = 50` (через `songToSave.saveToDb()`).
- В памяти `newSong.rootId = 0` (мы присваивали `songToSave.rootId`, а не `newSong.rootId`).

Сразу после `applyDuplicateOriginal` в `doCreateFromFolder` (строка 5425) вызывается `findAudioParentByWaveform(newSong, ...)`. Эта функция **вызывает `song.saveToDb()` несколько раз** (`Utils.kt:4879, 4898, 4919, 4933`), даже если аудио-родитель не найден (`matched = false`).

Внутри `newSong.saveToDb()` (`Song.kt:5169`):
```kotlin
val savedSong = loadFromDbById(id = id, ...)  // savedSong.rootId = 50 (из БД)
...
val diff = getDiff(this, savedSong)
if (diff.isEmpty()) return  // пустой → ничего не записываем
// UPDATE: SET root_id = 0
```

`this.rootId = 0` (в памяти), `savedSong.rootId = 50` (из БД). `0 != 50` → diff включает `root_id = 0` → UPDATE перезатирает `root_id` обратно в 0.

После этого:
- В БД: `root_id = 0` (перезатёрто).
- `textResolved = true` (от `applyDuplicateOriginal`).
- `findYandexSongLyrics` НЕ запускается (блок `if (!textResolved)`).
- Пользователь видит ровно то, что описал: «`root_id = 0` И поиск текста не запустился».

**Почему для нового автора всё работает**:
- `findDuplicateOriginal` возвращает null → `applyDuplicateOriginal` НЕ вызывается → `newSong.rootId = 0` И в БД `root_id = 0` — согласованы.
- `findAudioParentByWaveform` → `song.saveToDb()` → diff пуст по `root_id` (оба 0) → ничего не перезаписывается.
- `textResolved = false` → `findYandexSongLyrics` запускается. ✓

**Почему не видно логов**:
- Никаких исключений нет. `applyDuplicateOriginal` отрабатывает нормально. `findAudioParentByWaveform` отрабатывает нормально. UPDATE выполняется корректно (`SET root_id = 0` — это валидный SQL).
- Просто `root_id` возвращается к 0 после второго UPDATE.

**Регрессия после спеки 278**:

До спеки 278 `applyDuplicateOriginal` писал напрямую в `newSong`:
```kotlin
newSong.rootId = original.id  // пишем в newSong
newSong.saveToDb()             // согласованное сохранение
```
После спеки 278 — пишем в `songToSave` (новый объект), `newSong` остаётся «грязным».

### H2 — частный случай H1 для `applyAudioParentMarkers`

Та же проблема, но для аудио-родителя. После спеки 278 `applyAudioParentMarkers` также использует `songToSave`:
```kotlin
val songToSave = Song.loadFromDbById(song.id, ...) ?: song
songToSave.sourceText = audioParent.sourceText
...
songToSave.saveToDb()
// song.* в памяти не обновлены
```

Если после `applyAudioParentMarkers` кто-то вызовет `song.saveToDb()` — будет аналогичная перезапись `audio_*` полей. Но `applyAudioParentMarkers` ставит `id_status = 5` (MARKERS_CHECK), что обычно финальный статус — дальнейшие `saveToDb` редки. Тем не менее, для consistency нужно применить тот же фикс.

### H3 (отвергнуто) — `LOWER()` ломает поиск для кириллицы

`LOWER(song_author) = LOWER(?)` в локали `C`/`POSIX` мог ломать поиск для русских имён. Но эта гипотеза объясняет только «родитель не найден», а не «`root_id = 0` И поиск не запущен». По наблюдению пользователя, родитель найден (иначе `textResolved` остался бы false и поиск запустился). Гипотеза H3 может применяться для других кейсов (когда родитель должен быть найден, но не находится), но не для текущего бага.
- Затем в БД:
  ```bash
  SELECT id, song_name, root_id, source_text, song_tone, song_bpm FROM tbl_songs WHERE song_name LIKE '%Камнем по голове (Epic%';
  ```

### H2 — `findDuplicateOriginal` некорректно срабатывает, `applyDuplicateOriginal` молча НЕ записывает root_id

**Что происходит**:
- `findDuplicateOriginal` возвращает `Song?` (не null) — пользователь считает, что родитель найден.
- Но в `applyDuplicateOriginal` — `loadFromDbById(newSong.id)` возвращает объект, у которого ВСЕ поля уже совпадают с `original` (что не должно быть для только что созданной песни). Тогда `getDiff` возвращает пустой diff, `saveToDb` ничего не записывает. `root_id` остаётся 0.
- `textResolved = true` (потому что `applyDuplicateOriginal` не упал).

**Когда это могло произойти**: если `loadFromDbById` по какой-то причине вернул `original` (а не `newSong`), то `songToSave.rootId = original.id` = `original.id` (например, 50), `savedSong.rootId = 50` (тоже), diff пуст. Но почему `loadFromDbById(newSong.id)` вернул бы `original`?

**Маловероятно**, но возможно при какой-то экзотической ошибке в кэше запросов или гонке с параллельным `saveToDb`.

### H3 — `findDuplicateOriginal` нашёл родителя, `applyDuplicateOriginal` записал `root_id`, но параллельный процесс перезатёр

**Что происходит**:
- После `applyDuplicateOriginal` срабатывает какой-то параллельный процесс, который перезаписывает `root_id = 0`.
- Места, где `rootId` может быть присвоен:
  - `Utils.applyDuplicateOriginal:4544` — наш кейс
  - `Utils.applyFamilySongSelection:4636` — ручной выбор в модалке (НЕ вызывается автоматически)
  - `Utils.customFunction:161` — повторный поиск (НЕ вызывается автоматически)
  - `Utils.assignRootIdsToListOfSongs:4368` — старая функция (нужно проверить, вызывается ли)
  - `Functions.kt` — конструкторы `SongVoice` и т.п. (не вызываются из `doCreateFromFolder`)

**Маловероятно**, но возможно, если какая-то из этих функций вызывается из фонового процесса.

### H4 (SECONDARY, исходная гипотеза) — `LOWER()` в `findDuplicateOriginal` ломает поиск по кириллице

**Что могло произойти**: до спеки 238 был fallback на `sameAuthorOnly = false`, который маскировал проблему с `LOWER()` для кириллицы. После спеки 238 fallback убран, и кириллические авторы не находятся.

**НО это противоречит наблюдению пользователя**: если `findDuplicateOriginal` возвращает null, то `original != null` ложно, `applyDuplicateOriginal` НЕ вызывается, `textResolved = false`. Дальше `findYandexSongLyrics` должен запуститься. Пользователь говорит, что поиск текста НЕ запустился. Значит `findDuplicateOriginal` НЕ возвращает null — он либо нашёл, либо упал.

**Вывод**: гипотеза H4 остаётся валидной для **других** кейсов (когда родитель есть, но поиск по `LOWER()` не работает), но **не объясняет** текущий баг пользователя.

## План диагностики (перед фиксом)

Прежде чем предлагать фикс, нужно собрать факты. Без логов из контейнера `karaoke-app` точная причина не устанавливается.

### D1 — Сбор логов `karaoke-app`

```bash
# Воспроизвести баг: импортировать папку с файлом "Камнем по голове (Epic Orchestral, Cover-2)"
docker logs --tail 500 karaoke-app 2>&1 > /tmp/karaoke-app.log
grep -E "(doCreateFromFolder|findDuplicateOriginal|applyDuplicateOriginal|findYandexSongLyrics|error|Exception|rootId|root_id)" /tmp/karaoke-app.log | tail -100
```

### D2 — Проверка состояния БД после импорта

```bash
docker exec -it karaoke-postgres psql -U postgres -d karaoke -c "
SELECT id, song_name, song_author, root_id, id_status,
       LENGTH(source_text) AS src_len,
       LENGTH(source_markers) AS mrk_len,
       song_tone, song_bpm
FROM tbl_songs
WHERE song_name LIKE '%Камнем по голове (Epic%'
ORDER BY id DESC LIMIT 5;"
```

### D3 — Воспроизведение с подробным логированием

Если логов недостаточно, добавить в `applyDuplicateOriginal` подробное логирование перед фиксом:

```kotlin
fun applyDuplicateOriginal(newSong: Song, original: Song) {
    println("[${Timestamp.from(Instant.now())}] applyDuplicateOriginal START: newSong.id=${newSong.id}, original.id=${original.id}, newSong.rootId(before)=${newSong.rootId}, original.sourceText.length=${original.sourceText.length}")
    try {
        val songToSave = Song.loadFromDbById(id = newSong.id, ...) ?: newSong
        println("[${Timestamp.from(Instant.now())}] applyDuplicateOriginal RELOAD: songToSave.id=${songToSave.id}, songToSave.rootId(before set)=${songToSave.rootId}")
        songToSave.rootId = original.id
        songToSave.sourceText = original.sourceText
        songToSave.resultText = original.resultText
        songToSave.sourceMarkers = original.sourceMarkers
        songToSave.formattedTextSong = original.formattedTextSong
        songToSave.formattedTextTabs = original.formattedTextTabs
        songToSave.formattedTextChords = original.formattedTextChords
        songToSave.fields[SongField.ID_STATUS] = "1"
        println("[${Timestamp.from(Instant.now())}] applyDuplicateOriginal BEFORE SAVE: songToSave.rootId=${songToSave.rootId}, songToSave.fields[ID_STATUS]=${songToSave.fields[SongField.ID_STATUS]}")
        songToSave.saveToDb()
        println("[${Timestamp.from(Instant.now())}] applyDuplicateOriginal AFTER SAVE: OK")
        // Проверка после save
        val reloaded = Song.loadFromDbById(id = newSong.id, ...)
        println("[${Timestamp.from(Instant.now())}] applyDuplicateOriginal VERIFY: reloaded.rootId=${reloaded?.rootId}, reloaded.id_status=${reloaded?.idStatus}")
    } catch (e: Exception) {
        println("[${Timestamp.from(Instant.now())}] applyDuplicateOriginal EXCEPTION: ${e.javaClass.name}: ${e.message}")
        e.printStackTrace()
        throw e
    }
}
```

Это покажет, где именно происходит сбой (если он есть).

## Решение (резюме)

### H1 подтверждён анализом кода — фикс в `applyDuplicateOriginal` и `applyAudioParentMarkers`

**Фикс**: после `songToSave.saveToDb()` синхронизировать `newSong` (или `song`) в памяти с записанным состоянием. Это устраняет расхождение между памятью и БД, из-за которого следующий `song.saveToDb()` (внутри `findAudioParentByWaveform`) перезаписывает только что записанный `root_id` обратно в 0.

```kotlin
fun applyDuplicateOriginal(
    newSong: Song,
    original: Song,
) {
    val songToSave =
        Song.loadFromDbById(
            id = newSong.id,
            database = newSong.database,
            storageService = newSong.storageService,
            storageApiClient = newSong.storageApiClient,
        ) ?: newSong
    songToSave.rootId = original.id
    songToSave.sourceText = original.sourceText
    songToSave.resultText = original.resultText
    songToSave.sourceMarkers = original.sourceMarkers
    songToSave.formattedTextSong = original.formattedTextSong
    songToSave.formattedTextTabs = original.formattedTextTabs
    songToSave.formattedTextChords = original.formattedTextChords
    songToSave.fields[SongField.ID_STATUS] = "1"
    songToSave.saveToDb()
    
    // specs/279-fix-parent-search-folder-add: синхронизировать newSong с записанным состоянием.
    // Без этого следующий шаг doCreateFromFolder (findAudioParentByWaveform → song.saveToDb)
    // увидит this.rootId=0 (в памяти) != savedSong.rootId=original.id (из БД) → diff включит root_id=0
    // → UPDATE перезатрёт только что записанный root_id обратно в 0. Регресс после спеки 278.
    newSong.rootId = original.id
    newSong.sourceText = original.sourceText
    newSong.resultText = original.resultText
    newSong.sourceMarkers = original.sourceMarkers
    newSong.formattedTextSong = original.formattedTextSong
    newSong.formattedTextTabs = original.formattedTextTabs
    newSong.formattedTextChords = original.formattedTextChords
    newSong.fields[SongField.ID_STATUS] = "1"
}
```

Аналогичный фикс для `applyAudioParentMarkers` (consistency).

### H3 — отдельный фикс для регистрозависимости кириллицы

Если нужно (отдельная задача, не связана с текущим багом пользователя): заменить `LOWER(song_author) = LOWER(?)` на `song_author ILIKE ?` в `findDuplicateOriginal`. Это решает кейсы «родитель должен быть найден, но не находится из-за локали C/POSIX в PostgreSQL».

## Сводка по FR спеки

| FR | Реализация |
|----|------------|
| FR-001 | После фикса H1 — поиск родителя работает, `root_id` записывается и сохраняется. |
| FR-004 | Сохраняется (только тот же автор). |
| FR-007 | Защита от race condition (reload) СОХРАНЯЕТСЯ (мы только синхронизируем `newSong` после reload, не убираем сам reload). |
| FR-007a | Больше не нужен как основной фикс (исключение не при чём), но оставляем как defensive coding. |
| FR-007b | Подробное логирование — больше не критично для диагностики (причина известна), но можно оставить. |
| FR-008 | Поведение «родитель не найден» не изменяется. |

## TODO после реализации

1. Применить фикс H1 в `applyDuplicateOriginal` и `applyAudioParentMarkers`.
2. Ручная проверка SC-001..SC-007 на стороне пользователя.
3. Обновить LiveDoc 238 в `## История`: запись о фиксе регрессии спеки 278.
4. (опционально) Применить H3 для других кейсов с кириллицей — отдельная задача.

## Связанные LiveDocs и спеки

- [specs/238-import-folder-author-album-cover/](../238-import-folder-author-album-cover/spec.md) — основа (поиск родителя только у того же автора).
- [specs/278-fix-key-loss-on-lyrics-search/](../278-fix-key-loss-on-lyrics-search/spec.md) — race condition защита, которая вызывает регресс (по наблюдению пользователя).

## TODO после диагностики

1. Получить логи `karaoke-app` (D1).
2. Подтвердить или отвергнуть H1.
3. Применить фикс A1 (или другую гипотезу по результатам).
4. Обновить LiveDoc 238 в `## История` записью о фиксе.
