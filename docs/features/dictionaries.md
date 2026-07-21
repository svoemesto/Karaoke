# Словари (Dictionaries) и текстовые справочники

> **Status**: active
> **Feature Key**: dictionaries
> **Last Updated**: 2026-07-21

## Что делает

Двухуровневая система словарей для замены/нормализации текста в lyrics и
распознавания пользовательских правок:

1. **DB-словари** (`tbl_dictionaries`) — `Dictionary` (entity) + `DictionariesController` (REST).
   Произвольные пары ключ/значение для замены в текстах.
2. **TextFile-словари** (`src/main/resources/*.txt`) — `TextFileDictionary` (interface) +
   конкретные `CensoredWords`, `YoWords`, `SyncIds`, `Test`. Загружаются
   в память при старте приложения.

## Зачем

Lyrics с LLM-поиска часто содержат:
- `ё` → `е` (русский LLM часто нормализует, но в оригинале `ё`).
- Нецензурные слова (нужна цензура в UI).
- Сокращения/аббревиатуры (ЧФ — «Чёрный Флаг», МГ — «Московский Газ»).
- Идентификаторы синхронизации (код + значение, например
  `syncId=12345` → не трогать).

Словари позволяют **зафиксировать** эти замены в одном месте и применять
автоматически при загрузке lyrics, при отображении в плеере, при
синхронизации LOCAL↔SERVER.

## Как работает (кратко)

### DB-словари (`Dictionary.kt`, `DictionariesController.kt`)

```kotlin
data class Dictionary(
    val dictName: String,    // "censored_words", "yo_to_e", ...
    val element: String,     // "ё"
    val value: String,       // "е"
    val isDeleted: Boolean
)
```

REST-эндпоинты (DictionariesController):
- `GET /api/dictionaries` — список всех словарей.
- `POST /api/dictionaries/update` — добавить/обновить/удалить запись.
- `GET /api/dictionaries/textFile?action=getDict&name=censored` — дамп.

Используется в:
- `Settings.loadListFromDb()` — при загрузке lyrics.
- `Utils.replaceSymbolsInSong()` — финальная нормализация перед MLT.
- `ApiController.doTextFileDictionary()` — фасад над TextFile-словарями.

### TextFile-словари (`textfiledictionary/TextFileDictionary.kt`)

```kotlin
interface TextFileDictionary {
    fun dictName(): String        // "CensoredWords", "YoWords", "SyncIds", "Test"
    fun pathToFile(): String      // "/sm-karaoke/system/CensoredWords.txt"
    fun save(content: String)     // пишет в файл
    fun loadList(): List<String>  // читает из файла
    fun have(element: String): Boolean
    fun addValues(values: List<String>)
    fun removeValues(values: List<String>)
}
```

Конкретные реализации:
- **CensoredWords** — список нецензурных слов для фильтрации.
- **YoWords** — список слов, в которых LLM заменил `ё` на `е` (для обратной замены).
- **SyncIds** — список ID записей, которые нельзя удалять при синхронизации.
- **Test** — тестовые данные (только для dev-режима).

Загружаются в `TEXT_FILE_DICTS` (Karaoke.kt) при старте. Доступ через
`ApiController.doTextFileDictionary(action, name, values)`.

## Инварианты / правила

- **MUST**: `Dictionary` и `TextFileDictionary` имеют одинаковый интерфейс
  (`loadList()`, `have()`, `addValues()`, `removeValues()`) — это позволяет
  коду (`Settings`, `Utils`) работать с обоими типами одинаково.
- **MUST**: при изменении DB-словаря через `DictionariesController` —
  запись попадает в `tbl_settings_sync` для синхронизации LOCAL↔SERVER.
- **MUST**: `CensoredWords` обновляются через UI, **не** вручную в файле
  (файл — только инициализация при старте).
- **SHOULD**: новый словарь добавляется как `class FooWords : TextFileDictionary`
  с `dictName() = "FooWords"`, плюс регистрация в `TEXT_FILE_DICTS`.

## Известные ловушки

- **Большие словари вставляются в SSE-payload.** Если словарь > 100 KB
  и шлётся через `recordChange` (SSE) — клиент рвёт соединение. Используйте
  пейджинг или `doTextFileDictionary` с действием `getDict` (не SSE).
- **Словари не версионируются.** Изменения в `CensoredWords.txt` сразу
  попадают на прод после деплоя — нет `git`-истории изменений.
- **TextFile-словари НЕ синхронизируются** между LOCAL и SERVER — только
  DB-словари. Файлы нужно копировать вручную (`rsync` в `deploy_web.sh`).

## Ссылки

### Ключевые классы и файлы

- [`Dictionary.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Dictionary.kt) — entity DB-словаря
- [`TextFileDictionary.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/textfiledictionary/TextFileDictionary.kt) — interface текстового словаря
- [`CensoredWords.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/textfiledictionary/CensoredWordsDictionary.kt) — список нецензурных слов
- [`DictionariesController.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/DictionariesController.kt) — REST CRUD для DB-словарей
- `ApiController.doTextFileDictionary()` — фасад для фронта в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt`
- `Settings.getWhereList()` — потребитель словарей в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Settings.kt`

### Связанные документы

- [dual-db-sync.md](./dual-db-sync.md) — как DB-словари попадают на SERVER
- [llm-lyrics-search.md](./llm-lyrics-search.md) — откуда берутся замены в lyrics
- [CONTRIBUTING.md](../../CONTRIBUTING.md) — правила оформления кода
