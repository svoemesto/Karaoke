# Utilities overview

> **Домен**: system (cross-cutting)
> **Компонента**: детальный обзор утилитных модулей. Hotspots проекта
> с `fan_in` от 6 до 318.

## Ответственность | Responsibility

Утилитные модули — hotspots проекта (по codegraph `fan_in`). Неправильное
использование = рискованные регрессии. Каждый файл описан с его
назначением, публичным API, hot paths и ловушками.

**NB**: некоторые файлы огромные (`Utils.kt` — 5366 строк, `SVG.kt` —
930, `UtilsPictures.kt` — 1019). Это явный сигнал для **рефакторинга**
(P3+), но пока — задокументировать как есть.

## Сводная таблица

| Файл | Строк | Hot? | Описание |
|---|---|---|---|
| `karaoke-app/.../Utils.kt` | 5366 | 🔥 **hot** | Главный файл утилит (custom functions, sync, MLT, BPM/Key, search, ML) |
| `karaoke-app/.../UtilsPictures.kt` | 1019 | medium | Работа с изображениями (загрузка, ресайз, форматы) |
| `karaoke-app/.../UtilsAI.kt` | 919 | medium | AI-обёртки (LM Studio, см. LmStudioService) |
| `karaoke-app/.../SVG.kt` | 930 | medium | SVG-генерация (для обложек, маркеров) |
| `karaoke-app/.../Functions.kt` | 509 | medium | Математические/текстовые helpers |
| `karaoke-app/.../Converter.kt` | 296 | low | Преобразования типов |
| `karaoke-app/.../UtilsPlaywright.kt` | 237 | hot | Playwright helpers для headless-рендера |
| `karaoke-app/.../Poi.kt` | 243 | low | Apache POI для Excel (alignment dataset) |
| `karaoke-app/.../WaveformCompare.kt` | 232 | low | Сравнение waveform'ов |
| `karaoke-app/.../MediaInfo.kt` | 126 | medium | MediaInfo extraction (ffprobe) |
| `karaoke-app/.../SanitizePath.kt` | 80 | **hot** | Санитайзер путей (идемпотентный, issue #53) |
| `karaoke-app/.../Crypto.kt` | 48 | medium | Crypto helpers |
| `karaoke-app/.../DualStream.kt` | 31 | low | Дуплексные потоки |
| `karaoke-app/.../Uuids.kt` | 19 | low | Генерация UUID |
| `karaoke-web/.../TypographUtils.kt` | ? | low | Типографика (кавычки, ...) |

---

## `Utils.kt` — главный файл (5366 строк)

### Структура (по grep top-level)

**Важно**: файл НЕ организован по секциям, всё плоско. Это —

архитектурный долг. Список функций (по grep `^[[:space:]]*fun `):

```
Line  96: customFunction         — generic helper для модификации списков
Line 257: findParentForAuthor    — найти "родительского" автора
Line 405: findAudioParentForAuthor — найти аудио-родителя
Line 524: rescanAllCensoredNames — пересканировать цензурированные имена
Line 643: fillFormattedFields    — заполнить отформатированные поля
Line 673: checkHealth            — обёрка HealthReport (?)
Line 715: syncRemotePicturesInStorage — sync картинок с remote storage
Line 822: uploadPicturesToStorage — загрузить все картинки в хранилище
Line 856: setSongToSyncRemoteTable(id) — пометить песню для sync
Line 878: setSongToSyncRemoteTable(ids) — пакетная версия
Line 983: updateRemotePictureFromLocalDatabase — sync одной картинки
Line 998: updateRemoteSongFromLocalDatabase  — sync одной песни
Line 1019: updateRemoteDatabaseFromLocalDatabase — sync всех сущностей LOCAL→REMOTE
Line 1030: updateLocalDatabaseFromRemoteDatabase — sync всех сущностей REMOTE→LOCAL
Line 1055: runEntitySync         — sync одной entity (sync API)
Line 1075: updateDatabases       — главная точка двух-БД sync (вызывает runEntitySync)
Line 1527: deepCopy              — сериализуемая deep copy
Line 1559: getMd5Hash            — md5 от строки
Line 1569: bytesToHex            — конвертация
Line 1577: updateBpmAndKey       — обновить BPM/Key для песни
Line 1603: updateBpmAndKeyLV     — обновить BPM/Key (live version)
Line 1636: getBpmAndKeyFromCsv   — извлечь из CSV-лога
Line 1669: delDublicates         — удалить дубликаты
Line 1691: clearPreDublicates    — очистить "pre-dublicates" пометки
Line 1714: markDublicates        — пометить дубликаты
Line 1749: create720pForAllUncreated — создать 720p версии
Line 1776: copyIfNeed            — copy if need (file copy с проверкой)
Line 1798: collectDoneFilesToStoreFolderAndCreate720pForAllUncreated — сбор файлов
Line 1888: replaceSymbolsInSong   — заменить спецсимволы в тексте песни
Line 1951: createFilesByTags     — создать файлы по тегам (?)
Line 2012: createDigestForAllAuthors — дайджест всех авторов
Line 2035: createDigestForAllAuthorsForOper — то же, для оператора
Line 2066: getAuthorsForDigest   — список авторов для дайджеста
Line 2106: getAuthorDigest       — дайджест одного автора
Line 2142: ~~searchSongText2~~ — поиск текста песни (v2) — **УДАЛЁН в Pass 431** (dead code)
Line 2158: searchSongText        — поиск текста песни (v1 — старая)
Line 2408: getNewTone            — получить новый тон (transpose)
Line 2421: generateChordLayout   — сгенерировать chord layout (одна перегрузка)
Line 2432: generateChordLayout   — другая перегрузка
Line 2631: getFontSizeByHeight   — размер шрифта по высоте
Line ... (ещё много)
```

### Hot: что делает файл целиком

1. **Sync logic**: `updateDatabases`, `runEntitySync` (взаимодействует
   с [two-db-sync компонентой](../domains/processing/components/two-db-sync.md)).
2. **BPM/Key извлечение**: `updateBpmAndKey`, `getBpmAndKeyFromCsv`
   (через `AudioAnalize` или логи).
3. **Поиск текста**: только `searchSongText` (v2 `searchSongText2` — **УДАЛЁН** в Pass 431, dead code).
4. **Дубликаты**: `delDublicates`, `markDublicates`,
   `clearPreDublicates` — пайплайн пометки дублей.
5. **MLT-генерация**: `generateChordLayout`, `getFontSizeByHeight`.
6. **Image sync**: `syncRemotePicturesInStorage`,
   `uploadPicturesToStorage`.

### ~~Drift: `searchSongText` vs `searchSongText2`~~ — **FIXED in Pass 431** ✅

**Было** (до Pass 431): в `Utils.kt` было **две функции** для поиска
текста (`searchSongText` + `searchSongText2`); `searchSongText2`
**никем не вызывалась** (dead code).

**Исправлено** (Pass 431): `searchSongText2` **удалена** из `Utils.kt`.
Остался только `searchSongText` (вызывается из
`MainController.kt:1423, 1633`).

### Ловушки `Utils.kt`

1. **Большой файл, нет секций**: всё плоско. Найти функцию — `grep`.
2. **`updateDatabases` (line 1075)**: ~450 строк. Главная точка
   двух-БД sync. Любая правка = риск regression.
3. **BPM/Key логика размазана** между `updateBpmAndKey`,
   `getBpmAndKeyFromCsv`, `AudioAnalize.kt` — единого места нет.

### Что НЕ описано в `Utils.kt`

- **ProcessBuilder wrappers**: **отсутствуют** в `Utils.kt`.
  ProcessBuilder используется напрямую:
  - `Utils.kt:3808` — `processBuilder.start()` (в `createFilesK2`?).
  - `Utils.kt:4476-4484` — process с environment + `redirectErrorStream(true)`.
  - `KaraokeProcessWorker.kt:188` — process для subprocess заданий.

  Единой обёртки `ProcessCreator` /`ProcessRunner` НЕТ. Возможный
  рефакторинг — вынести обёртку с обязательным
  `redirectErrorStream(true)`.
- **`redirectErrorStream(true)`**: где enforce'ится? Должно быть в
  обёртке `ProcessBuilder`.

---

## `UtilsPictures.kt` (1019 строк)

### Что

Загрузка, ресайз, конвертация изображений (обложки альбомов, превью).

### Hot paths

- **Импорт альбомов**: парсинг обложки из mp3-тегов → upload в MinIO.
- **Prewarm превью**: `VkPreviewWarmupClient` использует эти утилиты.

### Ловушки

- **Большие изображения**: ресайз до загрузки в MinIO, иначе — таймаут.
- **EXIF**: может содержать приватные данные (см. CONSTITUTION VIII).

---

## `UtilsAI.kt` (919 строк)

### Что

AI-обёртки: тонкие клиенты над локальными AI-сервисами.

### Сервисы (по grep)

- **LM Studio** (chat completions): см. [LmStudioService.kt](../domains/storage/components/karaoke-storage-service.md) (P0 gaps).
- **Whisper ASR** (распознавание речи): см. `WhisperAsrService.kt`
  (упоминается в P0 как gaps).
- **Alignment** (forced alignment маркеров): см. `AlignmentServiceClient.kt`.

### Hot paths

- **`FORCED_ALIGN_MARKERS`** KaraokeProcessType использует
  `Utils.executeForcedAlignMarkers` (см. KDoc).

### Ловушки

- **Таймауты**: AI-модели могут думать долго. Все вызовы MUST иметь
  настраиваемые таймауты.
- **API key**: в `KaraokeProperties`, не в коде (CONSTITUTION VIII).

---

## `SVG.kt` (930 строк)

### Что

SVG-генерация: для обложек, маркеров на preview, и т.д.

### Используется

- `generateChordLayout` (в `Utils.kt`).
- Превью VK (`VkPreviewWarmupClient`).

### Ловушки

- **Размер SVG**: может расти. Делать caching.

---

## `Functions.kt` (509 строк)

### Что

Математические/текстовые helpers. По grep:

- **`customFunction`** (line 96) — generic helper для модификации списков.
- Числовые преобразования (предположительно).
- Строковые преобразования.

### Hot

`fan_in` около 6 (по codegraph `hotspots` boundary). Используется
повсеместно.

---

## `Converter.kt` (296 строк)

### Что

Преобразования типов. По grep:

- Поля/геттеры для конвертации между типами.
- Без `is*`-префикса (см. CONSTITUTION/CONTRIBUTING).

---

## `UtilsPlaywright.kt` (237 строк)

### Что

Playwright helpers для headless-рендера. Используется в:

- `PlayerMp4RenderService` (рендер кадров через headless Chrome).

### Связь

См. [processing/playwright-rendering](../domains/processing/components/playwright-rendering.md).

---

## `Poi.kt` (243 строки)

### Что

Apache POI wrapper для чтения/записи Excel.

### Используется

- `ExportAlignmentDataset.kt` — экспорт alignment dataset в Excel.

---

## `WaveformCompare.kt` (232 строки)

### Что

Сравнение двух waveform'ов (для AudioAnalize — проверить, что
обработка не сдвинула звук).

### Используется

- `AudioAnalize` (предположительно).

---

## `MediaInfo.kt` (126 строк)

### Что

Извлечение метаданных медиа (ffprobe). Используется для импорта.

---

## `SanitizePath.kt` (80 строк)

### Что

**Санитайзер путей**. Идемпотентен: `sanitize(sanitize(s)) == sanitize(s)`.

### Ловушка (прецедент issue #53)

Удаление символа (drop) вместо замены (replace) ломает идемпотентность.
Например, `!`/`?` молча удалялись → файлы не находились при импорте.

### Контракт

`sanitize(s)` MUST быть идемпотентным. Любая правка = риск regression.

См. `docs/features/idempotent-path-sanitize.md`.

---

## `Crypto.kt` (48 строк)

### Что

`Crypto.kt` (48 строк) — AES/CBC/PKCS5PADDING wrapper:

```kotlin
class Crypto {
    companion object {
        private const val KEY = "aesEncryptionKey"
        private const val INIT_VECTOR = "nbwZ08J5101kUxCQ"
        const val WORDS_TO_CHECK = "Строка для проверки крипты"
        fun decrypt(encrypted: String?): String?
        fun encrypt(value: String): String?
    }
}
```

Используется в:
- `Utils.kt:888, 902` — `Crypto.encrypt(sqlToDelete)` и
  `Crypto.encrypt(sqlToInsert)`: **шифрование SQL-команд** для
  передачи через `/api/sync/changerecords` (two-DB sync).
- `karaoke-web/.../MainController.kt` (использование не изучено).
- `Utils.kt:923, 950, 1147` — `Crypto.WORDS_TO_CHECK` (тестовая
  строка для проверки крипты).

### ⚠️ SECURITY ISSUE — **FIXED in Pass 430** ✅

**Было** (до Pass 430): ключ `KEY = "aesEncryptionKey"` и `INIT_VECTOR`
**HARDCODED** в исходниках — нарушало Constitution VIII.

**Исправлено** (Pass 430, ветка `342-knowledge-detail-2`):

- Ключ и IV теперь читаются из env-переменных `CRYPTO_AES_KEY` /
  `CRYPTO_AES_IV` через `System.getenv()`.
- Если env не заданы — **fallback** на hardcoded + однократный
  WARN-лог (чтобы существующие зашифрованные SQL не сломались
  сразу при обновлении).
- `legacyWarned` флаг — чтобы не спамить лог при каждом вызове.

```kotlin
private fun key(): String {
    val envKey = System.getenv(ENV_KEY)
    if (!envKey.isNullOrBlank()) return envKey
    if (!legacyWarned) {
        log.warn("CRYPTO_AES_KEY env not set — falling back to LEGACY hardcoded key. ...")
        legacyWarned = true
    }
    return LEGACY_KEY
}
```

**TODO для полного исправления** (Pass 343+):
1. Задать `CRYPTO_AES_KEY` и `CRYPTO_AES_IV` в `do.env` на проде.
2. Миграция: расшифровать все существующие зашифрованные SQL
   через legacy fallback, зашифровать заново через env-ключ.
3. После полной миграции — удалить `LEGACY_KEY` / `LEGACY_INIT_VECTOR`.

### Ловушка

**НЕ хардкодить ключи** (CONSTITUTION VIII). Все ключи — из env.

---

## `DualStream.kt` (31 строка)

### Что

Дуплексный поток для одновременного чтения/записи (используется
`AudioAnalize`).

---

## `Uuids.kt` (19 строк)

### Что

UUID-генерация. Используется для:

- `tabId` (SSE, см. [sse domain](../domains/sse/domain.md)).
- `SongShareLink.token`.
- Других уникальных идентификаторов.

---

## `karaoke-web/TypographUtils.kt`

### Что

Типографические преобразования:

- Кавычки « » (русские).
- Тире — (длинное).
- Многоточие ….

### Используется

- `PublicTypographController` — публичный endpoint для типографики.

---

## Архитектурные долги

1. **`Utils.kt` слишком большой**: 5366 строк, плоская структура.
   Нужно разделение:
   - `UtilsSync.kt` (sync-related функции).
   - `UtilsBpmKey.kt` (BPM/Key).
   - `UtilsSearch.kt` (search).
   - `UtilsDuplicates.kt` (dubl-пайплайн).
   - `UtilsMLT.kt` (chord/font).

2. **Drift: `searchSongText` vs `searchSongText2`** — **FIXED in Pass 431** ✅
      (v2 удалена как dead code).
   TODO Pass 343.

3. **`UtilsPictures.kt` + `SVG.kt` + `UtilsPlaywright.kt`**: 2190 строк
   в сумме. Может быть один `ImageUtils.kt`.

4. **`UtilsAI.kt`**: 919 строк. LmStudio/Whisper/Alignment — могут
   быть отдельные файлы (как `LmStudioService.kt`).

## Конвенции (CONSTITUTION/CONTRIBUTING)

### `redirectErrorStream(true)`

**NON-NEGOTIABLE**: ВСЕ `ProcessBuilder` MUST использовать
`redirectErrorStream(true)`. Без этого stderr переполняется и процесс
блокируется. См. ADR-0006.

**Где enforce**: найдены 10+ вызовов `redirectErrorStream(true)` в:

- `karaoke-app/.../KaraokeProcessWorker.kt:188`
- `karaoke-app/.../services/PlayerMp4MuxService.kt:170`
- `karaoke-app/.../MediaInfo.kt:29, 102`
- `karaoke-app/.../WaveformCompare.kt:129`
- `karaoke-app/.../Utils.kt:4480`
- `karaoke-app/.../controllers/ApiController.kt:7824`
- `karaoke-app/.../controllers/SongEditorController.kt:102`
- `karaoke-app/.../monitor/checks/ProdContainerCheck.kt:175, 220`

Это правило **размазано по коду** (нет единой обёртки). Возможный
рефакторинг — создать `ProcessBuilder.startWithRedirectError(cmd: List<String>): Process`
в `Utils.kt` и заменить все10+ мест. (Pass 343+, не блокирующий.)

### Идемпотентность санитайзера

`sanitize(sanitize(s)) == sanitize(s)`. Удаление символа (drop)
вместо замены (replace) ломает идемпотентность. См. `SanitizePath.kt`,
issue #53.

### DTO без `is*` префикса

`isActive`, `isPublic` и т.п. Jackson может сериализовать
неправильно. Используйте `active`, `public`.

---

## Известные TODO (Pass 343+)

- [ ] **Каждая функция в `Utils.kt`** заслуживает отдельной страницы.
- [x] **Drift `searchSongText` vs `searchSongText2`** — **FIXED in Pass 431** ✅
      (v2 удалена как dead code).
- [ ] **`redirectErrorStream(true)` enforcement**: где, как.
- [ ] **`SanitizePath` контракт**: подробнее, unit-тесты.
- [x] **`Crypto.kt` — fix в Pass 430** ✅: ключ и IV из env
      (`CRYPTO_AES_KEY`/`CRYPTO_AES_IV`), legacy fallback + WARN-лог.
- [ ] **`Functions.kt`** и **`Converter.kt`** — какие именно helpers.
- [ ] **`UtilsPictures.kt`**: какие форматы, лимиты размера.
- [ ] **`UtilsAI.kt`**: список AI-сервисов, таймауты.
- [ ] **`SVG.kt`**: какие именно шаблоны генерируются.

## Код (физическая реализация)

См. таблицу выше. Все файлы — в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/`.

## Связанные ADR

- [ADR-0006-processbuilder-redirect-errorstream](../adr/0006-processbuilder-redirect-errorstream.md) — обязательное правило.
- Constitution VIII — секреты и git-гигиена.

## Changelog

- **Pass 341 P3c** (2026-09-09): Initial detailed. Автор: agent (Karaoke).