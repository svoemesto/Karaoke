# Feature Specification: Цензурирование на karaoke-web — глобалы + Unicode regex

**Feature Branch**: `141-fix-censored-web-storage-globals`

**Created**: 2026-08-04

**Status**: Ready for review

**Input**: После PR #180 (140-fix-zakroma-censored-database) Закрома перестали падать с
500, но словарь «Censored» возвращался пустым (с предупреждением `lateinit property KSS_APP
has not been initialized` в логах), и реальное цензурирование на karaoke-web не работало.

## Два независимых бага

### 1. `KSS_APP`/`SAC_APP`/`APP_WORK_*` не инициализированы в karaoke-web

`Dictionary.kt:23` использует `storageService: KaraokeStorageService = KSS_APP` и
`storageApiClient: StorageApiClient = SAC_APP` как конструкторные дефолты. На проде
работает только karaoke-web (без бина `KaraokeAppService` модуля karaoke-app, который
инициализирует эти глобалы). Поэтому первое же обращение к `TextFileDictionary.dict` через
`Dictionary.loadValues("Censored", database)` создаёт `Dictionary()`, читает дефолт
`storageService = KSS_APP` и падает с `UninitializedPropertyAccessException`. Catch в
`TextFileDictionary.dict:60` ловит исключение, логирует как «ОШИБКА чтения словаря» и
возвращает пустой список — цензурирование тихо деградирует.

### 2. `\b` в Java/Kotlin regex не работает с русскими буквами

После фикса #1 словарь читается (58 записей), но цензурирование всё равно не применяется к
названиям вроде «Хуйня», «Бляди», «Шлюха». Причина: `String.censored()` строит regex
`\\b$uncensored\\b` — Java `Pattern` по умолчанию использует ASCII `\w = [a-zA-Z_0-9]`,
русские буквы НЕ являются word-characters, поэтому `\bхуй\b` не сматчится в строке
«Хуйня» (нет word boundary между пробелом и «Х», и между «й» и «н»).

Подтверждено экспериментально:
```
[DEBUG-141] regex test: 'Хуйня Бляди' / '\bхуй\b' matches=false
```

Все слова словаря «Censored» — русские. До этого фикса цензурирование на русских
названиях в принципе не работало ни в одном эндпоинте (включая admin-путь karaoke-app).
Это давний баг, замаскированный тем, что на admin-машине словарь всё равно читался, а
применимость regex никто не проверял.

## Решение

### Фикс #1: инициализация karaoke-app-глобалов в karaoke-web

`KaraokeWebService.init{}` (модуль karaoke-web) инжектит через Spring DI
`WebKaraokeStorageServiceImpl` (заглушка `KaraokeStorageService` для karaoke-web —
уже была в коде, бросает `UnsupportedOperationException` на все методы, но безопасно
для чтения БД: `KaraokeDbTable.loadList` не вызывает `storageService.X()`) и
`StorageApiClientWeb` (заглушка `StorageApiClient`), и инициализирует ими karaoke-app-
глобалы `KSS_APP`, `SAC_APP`, а также защитный слой `APP_WORK_IN_CONTAINER` /
`APP_WORK_ON_SERVER` (на случай если кто-то обратится к karaoke-app-глобалу
`WORKING_DATABASE` из karaoke-web — после PR #140 таких мест быть не должно, но
защита не лишняя).

### Фикс #2: Unicode word boundary в `String.censored()`

`\\b$uncensored\\b` → `(?<![\\p{L}\\p{N}_])$uncensored(?![\\p{L}\\p{N}_])` —
lookbehind/lookahead с Unicode property classes. `\p{L}` = любая буква (включая
русскую), `\p{N}` = любая цифра, `_` = ASCII underscore. Теперь граница слова
работает для любого Unicode-текста.

## Изменённые файлы

- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Extentions.kt` —
  Unicode lookbehind/lookahead в `censored()` (+7/−2)
- `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/KaraokeWebService.kt` —
  инициализация `KSS_APP`/`SAC_APP`/`APP_WORK_*` через DI бины заглушек (+23/−0)

## Проверка

1. `curl -G "http://localhost:8897/api/public/zakroma" --data-urlencode "author=Ленинград"` →
   HTTP 200, матерные слова в `songName` замаскированы:
   - `Бляди` → `Бл█ди`
   - `Хуйня` → `Х█йня`
   - `Мне похуй всё` → `Мне пох█й всё`
   - `Пидарасы` → `Пид█расы`
   - `Распиздяй` → `Расп█здяй`
   - `Хуямба` → `Х█ямба`
   - `На хуй рок-н-ролл` → `На х█й рок-н-ролл`
2. В логах karaoke-web нет `lateinit property KSS_APP has not been initialized`.
3. `gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin` — успешно.
4. `gradlew :karaoke-app:ktlintCheck :karaoke-web:ktlintCheck` — успешно.
5. `gradlew :karaoke-web:bootJar` — успешно.
6. Перезапуск через `deploy/do.sh build_start_web --force` — успешно, без
   регрессии на других эндпоинтах.

## Известные ограничения (отдельные задачи)

- `/api/public/songs` и `/api/public/song/{id}` (DTO `SongPublicDto.fromSong`) не
  прогоняют `songName` через `censored()` — это сознательно или нет, нужно
  уточнить. Не блокирует основную задачу (Закрома работают с цензурой).
- В логах `StatsCacheScheduler`/`SseNotificationService` (admin-путь) цензурирование
  тоже будет работать корректно после фикса #2 — это побочный положительный эффект.