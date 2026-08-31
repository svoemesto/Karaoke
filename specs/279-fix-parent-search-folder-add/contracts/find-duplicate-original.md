# Contract: findDuplicateOriginal — поиск «родителя» при импорте из папки

**Дата**: 2026-08-31
**Спека**: [../spec.md](../spec.md)
**Файл**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt:4402`

## Сигнатура

```kotlin
fun findDuplicateOriginal(
    newSong: Song,
    database: KaraokeConnection,
    storageService: KaraokeStorageService,
    storageApiClient: StorageApiClient,
): Song?
```

## Назначение

Для новой песни (обычно только что импортированной из папки через `Song.createFromPath`) ищет «оригинал» — уже существующую в БД песню **с тем же автором** (регистронезависимо) и **тем же нормализованным названием** (без содержимого в скобках, без знаков препинания, регистронезависимо, «е»/«ё» эквивалентны), у которой уже есть текст (`source_text` не пуст после `TRIM`). При наличии нескольких подходящих записей возвращает запись с наименьшим `id`.

Если ни одной подходящей записи не найдено — возвращает `null` (никакого fallback'а на других авторов — это требование спеки 238).

## Входные параметры

| Параметр | Тип | Описание |
|----------|-----|----------|
| `newSong` | `Song` | Новая песня, для которой ищем родителя. Должна иметь `id > 0` (быть уже записана в БД). Используются поля `id`, `songName`, `author`. |
| `database` | `KaraokeConnection` | Соединение с БД. |
| `storageService` | `KaraokeStorageService` | Сервис хранилища (для загрузки `Song` через `loadFromDbById`). |
| `storageApiClient` | `StorageApiClient` | API клиент хранилища. |

## Возвращаемое значение

| Условие | Результат |
|---------|-----------|
| Найден кандидат с тем же автором и тем же нормализованным названием, у которого `TRIM(source_text) <> ''` | `Song?` — загруженная через `Song.loadFromDbById` запись с минимальным `id`. |
| Нет ни одной подходящей записи (любая причина: другой автор, другое нормализованное имя, пустой `source_text`) | `null` |
| `database.getConnection() == null` (БД недоступна) | `null` |
| `normalizeSongNameForSearch(newSong.songName).isBlank()` (пустое нормализованное имя) | `null` (ранний выход до SQL) |

## Контракт SQL

### Текущий (сломанный) SQL

```kotlin
val sql =
    "SELECT id, song_name FROM tbl_songs" +
        " WHERE id <> ?" +
        (if (sameAuthorOnly) " AND LOWER(song_author) = LOWER(?)" else "") +
        " AND TRIM(source_text) <> ''" +
        " ORDER BY id ASC"
val ps = connection.prepareStatement(sql)
var idx = 1
ps.setLong(idx++, newSong.id)
if (sameAuthorOnly) ps.setString(idx, newSong.author)
```

### Ожидаемый (после фикса) SQL

```kotlin
val sql =
    "SELECT id, song_name FROM tbl_songs" +
        " WHERE id <> ?" +
        (if (sameAuthorOnly) " AND song_author ILIKE ?" else "") +
        " AND TRIM(source_text) <> ''" +
        " ORDER BY id ASC"
val ps = connection.prepareStatement(sql)
var idx = 1
ps.setLong(idx++, newSong.id)
if (sameAuthorOnly) ps.setString(idx, newSong.author)
```

Изменения:
- `LOWER(song_author) = LOWER(?)` → `song_author ILIKE ?` — устраняет зависимость от локали PostgreSQL (`C`/`POSIX` ломает `LOWER` для не-ASCII).
- Параметр `?` остаётся `newSong.author` без `lowercase()` — `ILIKE` сам обрабатывает регистр.

## Семантика сравнения

### Имя песни

Через `normalizeSongNameForSearch(name: String): String` (`Utils.kt:4383`):

1. `name.replace(Regex("""\([^)]*\)"""), "")` — удалить содержимое в скобках.
2. `.lowercase()` — привести к нижнему регистру.
3. `.replace('ё', 'е')` — «ё»→«е».
4. `.replace(Regex("""[^\p{L}\p{Nd}\s]"""), "")` — удалить пунктуацию (Unicode-aware: `\p{L}` буквы всех алфавитов, `\p{Nd}` цифры всех алфавитов).
5. `.replace(Regex("""\s+"""), " ")` — схлопнуть множественные пробелы.
6. `.trim()` — убрать пробелы по краям.

Сравнение `==` (после нормализации обеих сторон).

### Имя автора

Через SQL `song_author ILIKE ?` — регистронезависимое сравнение, работающее с unicode в любой локали PostgreSQL.

## Поведение «не найден»

- `null` возвращается.
- Вызывающий код (`ApiController.doCreateFromFolder:5407-5417`) проверяет `if (original != null)` и при `null` НЕ вызывает `applyDuplicateOriginal`.
- Новая песня остаётся с `root_id = 0` и без скопированного текста/маркеров.
- Дальше работает `findYandexSongLyrics` (синхронно) и `lyricsSearchExecutor.submit` (фоновый SearXNG/fourget поиск) — как и прежде.

## Поведение «найден»

- Возвращается объект `Song`, загруженный через `Song.loadFromDbById(id = найденный_id, database, storageService, storageApiClient)`.
- Этот объект содержит заполненные `sourceText`, `sourceMarkers`, `resultText`, `formattedTextSong`, `formattedTextTabs`, `formattedTextChords` (если родитель их имеет).
- Вызывающий код вызывает `applyDuplicateOriginal(newSong, original)` для копирования полей в новую песню.

## Потокобезопасность

- `database.getConnection()` берётся через `KaraokeConnection` (см. Constitution § II — сырой JDBC). Не предполагается одновременный вызов `findDuplicateOriginal` для одной и той же `newSong` в разных потоках.
- Если `doCreateFromFolder` вызывается из нескольких HTTP-запросов одновременно (теоретически) — каждый обрабатывает свои `newSong` независимо.

## Вызывающие места

| Где | Что делает с результатом |
|-----|--------------------------|
| `ApiController.doCreateFromFolder:5407` (файл `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt`) | Если `original != null` → `applyDuplicateOriginal(newSong, original)`. |
| Других вызовов в текущей кодовой базе НЕТ (проверено `grep -rn "findDuplicateOriginal" karaoke-app/`). | — |

## Связь с другими функциями

- `normalizeSongNameForSearch` (`Utils.kt:4383`) — нормализация имени для сравнения. Не изменяется.
- `applyDuplicateOriginal` (`Utils.kt:4528`) — применяет найденного родителя. Не изменяется.
- `findParentCandidateId` (`Utils.kt:4463`) — параллельная функция для `customFunction`. Сравнение автора делает в Kotlin (`equals(ignoreCase = true)`), не страдает от той же проблемы. Не изменяется.
- `findAudioParentByWaveform` — независимая ветка (по звучанию), вызывается после `findDuplicateOriginal`. Не изменяется.

## Обратная совместимость

- Сигнатура функции не меняется (FR-011 спеки).
- Поведение для ASCII-имён (английские авторы) НЕ меняется: `ILIKE` для ASCII работает идентично `LOWER(...) = LOWER(...)`.
- Поведение для кириллических имён ВОССТАНОВЛЕНО: до фикса (без fallback после спеки 238) — `root_id = 0`, после фикса — `root_id = <id родителя>`.
- Поведение «межавторская привязка» (FR-004 спеки) СОХРАНЕНО: `song_author ILIKE ?` исключает других авторов точно так же, как `LOWER(song_author) = LOWER(?)`.
- Поведение «выбор минимального id» (FR-002 спеки) СОХРАНЕНО: `ORDER BY id ASC` остаётся без изменений.
- Поведение «только с непустым `source_text`» СОХРАНЕНО: `AND TRIM(source_text) <> ''` остаётся без изменений.

## Тестирование

См. [../quickstart.md](../quickstart.md) — ручная проверка сценариев SC-001..SC-007.

## TODO после реализации

1. Проверить `searchSongsByNormalizedName` (`Utils.kt:4499`) — там SQL `WHERE id <> ? AND TRIM(source_text) <> ''` (без фильтра по автору на SQL-уровне, фильтрация по автору не нужна, ищет среди всех). Не затрагивается, но если в будущем добавится фильтр по автору — применить тот же фикс.
2. Проверить все места с `LOWER(song_author) = LOWER(?)` в проекте (`Song.kt`, фильтры в админке, `SongDTOdigest.filter_root_id`) — отдельная задача вне этой спеки, если будут регрессии.
