# Component: health-report

> **Домен**: [health](../domain.md)
> **Компонента**: детальное описание `HealthReport` data class и его
> companion object.

## Ответственность | Responsibility

`HealthReport` — единичный отчёт о нарушении по одной песне.
Содержит: **что не так** + **как исправить** (набор actions в виде
лямбд). Не выполняет actions сам — это **план**, который пользователь
(webvue3) может запустить одной кнопкой или автоматически через
`autoRepair`.

`HealthReport.getHealthReportList(song: Song)` собирает **полный
список нарушений по песне**, перебирая все её `KaraokeFileType`
× все `KaraokeFileTypeLocations`, где этот тип должен быть.

## Интерфейсы и Контракты | Interfaces and Contracts

### Data class `HealthReport`

```kotlin
data class HealthReport(
    val song: Song,
    val description: String = "",
    val healthReportType: HealthReportType,
    val healthReportStatus: HealthReportStatus,
    val canResolve: Boolean = false,
    val problemText: String = "",
    val solutionText: String = "",
    val solutionActions: List<() -> Unit> = emptyList(),
    val waitingFileTask: HealthReportBatchPool.WaitingFileTask? = null,
    val storageStateUnknown: Boolean = false,
)
```

| Поле | Что | Когда заполняется |
|---|---|---|
| `song` | Сама песня | Всегда (даже для OK-отчётов) |
| `description` | Что проверяли: `"MLT/LOCAL_FILESYSTEM"`, `"COVER/LOCAL_STORAGE"`, ... | Всегда; формат `"<type>/<location>"` |
| `healthReportType` | Категория нарушения | Всегда |
| `healthReportStatus` | `OK` / `WARNING` / `ERROR` / `FATAL_ERROR` / `IN_PROGRESS` | Всегда |
| `canResolve` | Может ли система исправить сама | Почти всегда true, кроме FATAL_ERROR |
| `problemText` | Человеческое описание проблемы | Если status ≠ OK |
| `solutionText` | Человеческое описание решения | Если status ≠ OK и есть actions |
| `solutionActions` | Лямбды для исправления | Если canResolve && status ∈ {ERROR, WARNING, IN_PROGRESS} |
| `storageStateUnknown` | Проверка файла не выполнялась (circuit breaker open, `WAITING`); состояние хранилища неизвестно | Только в отчётах «хранилище недоступно» (BR-279, #376) |

### `HealthReportType`

```kotlin
enum class HealthReportType {
    CONSISTENCY_VIOLATION,  // файл есть, но симлинки/метаданные не консистентны
    FILE_VIOLATION,         // файл отсутствует или повреждён
}
```

### `HealthReportStatus`

```kotlin
enum class HealthReportStatus(val color: String) {
    OK(color = "#99FF99"),          // Всё хорошо
    WARNING(color = "#99CCFF"),     // Всё хорошо, но есть нюансы
    IN_PROGRESS(color = "#FFFF99"), // Уже чиним (есть KaraokeProcess)
    ERROR(color = "#FF9999"),       // Всё плохо, но можно сделать хорошо
    FATAL_ERROR(color = "#FF0000"), // Всё совсем плохо
}
```

## Логика и Алгоритмы | Logic and Algorithms

### Сборка отчёта: `getHealthReportList(song: Song)`

Точка входа: `HealthReport.kt:1119`. Возвращает `List<HealthReport>`
для одной песни. Перебирает:

1. Все `KaraokeFileType` для песни (см. `KaraokeFileType.willBeInFileSystem` / `willBeInLocalStorage` / `willBeInRemoteStorage`).
2. Для каждой комбинации (type, location) вызывает `actions(location = ...)`.
3. `actions()` (строка 62) делегирует в `actionsLocalFileSystem` / `actionsLocalStorage` / `actionsRemoteStorage`.

**Внутри каждого `actions*`** — большой decision tree (комментарий в коде на 50+ строк, см. `HealthReport.kt:186-230`):

```
Если файл должен быть (canBe):
  Если файл реально есть:
    Если должны быть симлинки:
      Если симлинки есть и целые → OK
      Если симлинки есть, но broken → удалить + создать (ERROR)
      Если симлинков нет → создать (ERROR)
  Если файла реально нет (!exists):
    Если canResolve:
        Если есть задача в процессах (inProgressOwn) → IN_PROGRESS
        Если !inProgressOwn и canCreate → создать процесс (ERROR + action)
        Если !canCreate:
          Если inProgressParent → поставить в очередь после родителя
          Если !inProgressParent → tryRestoreFromStorage
      Если !canResolve → FATAL_ERROR (tryRestoreFromStorage)

Если файл НЕ должен быть (!canBe):
  Если реально есть → удалить (ERROR + action)
  Если реально нет:
    Если должны быть симлинки → удалить симлинки (ERROR)
    Иначе → OK
```

### Актуальность файлов, у которых нет локальной копии (OpenProject #328, specs/552)

Для типов с `locations` без `LOCAL_FILESYSTEM` (пять mp3-типов после specs/544) файл на диске
**не является эталоном актуальности**. Такая копия появляется как побочный продукт ленивой
конвертации плеера (`convertFlacToMp3`, VBR `-qscale:a 2`) и не перезаливается в хранилища,
поэтому сравнение её размера с объектом давало ложные ERROR после рендера демо.

Правило:

- при наличии объекта в обоих хранилищах сравниваются только `size` объектов LOCAL и REMOTE
  (`storageActuality`); диск не читается;
- равные размеры → OK; разные → WARNING «Размеры файлов в локальном и удалённом хранилищах
  различаются» без автоматических действий (`canResolve = false`), потому что выбрать
  «правильную» сторону нельзя;
- `etag` между хранилищами не сравнивается: у multipart-загрузок он не равен MD5 и различается
  между путями заливки;
- для типов с `LOCAL_FILESYSTEM` поведение прежнее — диск остаётся источником истины
  (`diskCopyIsAuthoritative`).

Ветки восстановления, когда объектов в хранилищах нет, не менялись: ремонт из дисковой копии
по-прежнему возможен, это не проверка актуальности.

### Повторная проверка файла на диске перед сравнением (BR-278, #376)

`existsInLocalFileSystem` вычисляется в `getHealthReportList` в самом начале, **до** походов
в кеш метаданных, БД и MinIO. Сравнение размера (`File(pathToFile).length() == size`) идёт
позже — через десятки–сотни миллисекунд. Если за это время файл переименован
(каскад `SongArtifactRenamer.renameLocal`, specs/124) или удалён вручную, `File.length()`
возвращает `0`, и старая формула выносила вердикт «неактуально»: repair удалял валидный
объект из LOCAL/REMOTE-хранилища и ставил загрузку из уже несуществующего пути.

Правило (BR-278):

- существование файла проверяется **ещё раз непосредственно перед сравнением** —
  `diskFileMatchesStoredSize(pathToFile, storedSize)`;
- `null` (файл исчез, путь пуст) — это отдельная ветка «диск исчез», **не** «неактуально»:
  `WARNING`, `canResolve = false`, объект хранилища не удаляется и не перезагружается;
- `false` — файл есть, но размер другой; только здесь допустимы delete + upload;
- то же правило действует и в зеркальной REMOTE-ветке `actionsRemoteStorage`.

### Hot spots и производительность

`getHealthReportList` — источник MinIO-запросов. Оптимизировано в OpenProject #75:

**Кеширование (Pass 344, #69)**:
- `StorageMetadataCache` — вечный in-process кеш результатов `fileExists`.
- Таблица `tbl_storage_metadata_cache` в локальной БД (persists across restarts).
- Write-through: `uploadFile`/`deleteFile` автоматически инвалидируют кеш.
- **Pass 434 (#158)**: также кешируются `getFileInfo` (etag/size) — HealthReport
  получает `StorageFileInfo` через `cachedFileInfo(...)`, а `fileIsActual`
  вычисляется сравнением `size` (диск vs хранилище, local vs remote), **без**
  отдельного `statObject`. Раньше кешировался только `fileExists`, а
  `fileIsActual`/`getFileInfo` шли в MinIO каждый раз.
  `selectFileInfo` возвращает `null` для неполных строк (size IS NULL) — иначе
  ложное `size=0` → «файл неактуальный».

**Async cold-start (Pass 364, #75, US2; уточнено в Pass 132, #132)**:
- `StorageMetadataCache.peekFileExists()` — неблокирующее чтение кеша (без fill).
- REMOTE storage: cache miss → `WAITING` + `waitingFileTask` в
  `HealthReportBatchPool.waitingQueue` (20 worker'ов синхронно проверяют файл
  и заполняют кеш). Старый `cachedFileExistsAsync`/`getFileExistsAsync`
  fire-and-forget остаётся примитивом кеша, но health-flow его больше не
  использует (он сериализовался в один поток `cacheFillerExecutor`).
- Warm cache: <1ms (cache hit).

**Circuit breaker (Pass 364, #75, FR-006; разделение Pass 429, #153)**:
- **Два независимых** `StorageCircuitBreaker`: `localStorageCircuitBreaker` и
  `remoteStorageCircuitBreaker` (общий config, раздельное состояние).
- `actionsLocalStorage` → **local**-брейкер: при OPEN `FATAL_ERROR`
  «Локальное хранилище недоступно».
- `actionsRemoteStorage` → **remote**-брейкер: при OPEN `FATAL_ERROR`
  «Удалённое хранилище недоступно».
- Pass 432 (#156): диагностика использует `isFastFail()` (нетранзишн), НЕ
  `acquire()` — иначе диагностический вызов «съедал» probe и circuit зацикливался
  `OPEN→HALF_OPEN→watchdog OPEN`. Реальный probe исполняет `decorate`.
- Pass 433 (#157): `isFastFail()` cooldown-aware — после истечения cooldown
  возвращает `false`, чтобы вызов дошёл до `decorate` и стал probe'ом (иначе
  circuit залипал в OPEN).
- State machine у каждого: CLOSED → OPEN → HALF_OPEN → CLOSED.
- Сбой одного хранилища не влияет на другое (изоляция).
- **NB**: до Pass 429 был один breaker (защищал remote), и `actionsLocalStorage`
  ошибочно читал его — remote-сбой валил локальный путь. Исправлено.

**Async repair (Pass 364, #75, US3)**:
- `startRepairAll()` — fire-and-repair через `repairExecutor` (4-thread pool).
- HTTP-тред не блокируется: `executeResolvable` выполняется в фоне.
- UI получает initial SSE мгновенно.

### Пакет `PKG-S3-26` (OpenProject #398, аудит #336): непротиворечивость отчёта о здоровье

Восемь записей аудита одного файла (`HealthReport.kt`) закрыты одним набором правил.
Суть общего корня: **отчёт о здоровье не должен показывать «всё хорошо», когда
проверка не выполнялась или выполнялась частично**, и **ремонт не должен молчать
об ошибке**.

| BR | Что было | Стало |
|---|---|---|
| BR-280 | На каждую пару «тип файла × локация» шёл свой `KaraokeProcess.loadList` (~18 SQL-запросов на песню, тысячи на страницу «Песни») | Задания песни читаются **одним** запросом в `getHealthReportList` и раздаются в `actions*` параметром `songProcesses`; отбор — `filterSongProcesses(...)` в памяти (те же правила, что у `loadList`: `song_id`, `process_status`, `process_type`, `thread_id`, `process_args LIKE`) |
| BR-281 | `cachedFileInfo` возвращал `null` и при «объекта нет», и при «не удалось узнать»; вызывающий трактовал `null` как «файл актуален» → отчёт `OK` при недоступном MinIO | Типизированный результат `StorageFileInfoProbe` (`Known` / `Missing` / `Unknown`) + вердикт `FileActualityVerdict` (`ACTUAL` / `NOT_ACTUAL` / `DISK_VANISHED` / `UNKNOWN`). «Не удалось узнать» → `WARNING` + `storageStateUnknown = true` (готовность плеера не сбрасывается), а не `OK` |
| BR-282 | `recomputeAndBroadcast` стоял **до** `submit` и вне `try`: его исключение оставляло `repairInFlight[songId] = true` навсегда — «Исправить всё» и каскад для песни молча умирали до рестарта | Guard берётся и снимается в `try/catch/finally` вокруг **всего** тела `startRepairAll` |
| BR-283 | Ошибка удаления объекта из REMOTE гасилась `println` внутри лямбды — UI показывал «исправлено», объект оставался | `deleteRemoteFileOrThrow`: SLF4J `ERROR` с throwable **и** проброс исключения в repair-цикл |
| BR-284 | Исключение в одном действии обрывало весь каскад: остальные действия не выполнялись, финальный пересчёт и SSE не уходили | Каждое действие — в своём `try/catch`; `executeResolvable` возвращает число неуспешных действий; ошибка уходит в лог (`infra.health.report`) и в SSE (`type=error`) |
| BR-285 | `POST /api/song/executeHealthReportActions` выполнял `solutionActions` (в т.ч. `downloadFile` из MinIO) **синхронно в потоке Tomcat** — нарушение инварианта storage-домена № 4 | Действие уходит в `repairExecutor` (`HealthReport.executeHealthReportActionsAsync`), эндпоинт отвечает `202 Accepted`, фактический результат — по SSE (`HEALTH_REPORTS` + сообщение об ошибке) |
| BR-286 | Непрочитанный каталог симлинков (`AccessDeniedException` и т.п.) гасился `println` + `return@forEach` — каталог выпадал из аудита, отчёт выглядел здоровым | `WARNING`-запись без действия («Каталог ссылок не прочитан») + SLF4J `WARN` с throwable |
| BR-287 | `recalculatePlayerReadiness` считал `failed`, но возвращал `total` — SSE всегда показывал «Проверено песен: N» | Возвращается `ReadinessRecalcResult(processed, failed)`; при `failed > 0` SSE — `type=error` с обоими числами; причина ошибки логируется через SLF4J с throwable |

### Repair-loop: `startRepairAll` / `recomputeAndBroadcast`

Точка входа: `HealthReport.kt:2259` (Pass 364 — async, не блокирует HTTP). Используется при:

1. Завершении `KaraokeProcess*` (`onRepairProcessFinished`, строка 2277).
2. Ручном запуске из webvue3 «Починить всё».
3. Старте admin-машины (cold start).

**Pass 364 (OpenProject #75):** `startRepairAll` выполняется асинхронно.
HTTP-тред отправляет initial SSE и сразу возвращает ответ.
`executeResolvable` работает в `repairExecutor` (4-thread pool).

Цикл:
```
startRepairAll (async, HTTP thread) →
  recomputeAndBroadcast()              // initial SSE
  repairExecutor.execute {             // background thread
    recomputeAndBroadcast()            // before repair
    executeSolutionActions()           // выполнить лямбды
    recomputeAndBroadcast()           // after repair, SSE update
  }
```

После repair пользователь видит обновление **через SSE**
(`HEALTH_REPORTS`), без перезагрузки страницы.

### `reconcilePlayerReadinessFlags`

`HealthReport.kt:2113-2148`. Решает, может ли плеер играть песню,
обновляя **4 персистентных флага** на `Song`:

- `song.stemAccompanimentReady` ← `MP3_ACCOMPANIMENT` OK в `LOCAL_STORAGE`.
- `song.stemVocalReady` ← `MP3_VOCAL` OK в `LOCAL_STORAGE`.
- `song.pictureAlbumReady` ← `PICTURE_ALBUM` OK в `LOCAL_STORAGE`.
- `song.pictureAuthorReady` ← `PICTURE_AUTHOR` OK в `LOCAL_STORAGE`.

**Запись флагов — атомарная (BR-075, аудит #336, PKG-S3-22).** Установка флага
(`song.stemAccompanimentReady = it` и т.п.) пишет его точечным compare-and-swap в
`player_readiness_flags`, а не перезаписывает весь JSON: фоновый repair-loop и
планировщик автопубликации больше не затирают ключи друг друга. Поле
`player_readiness_flags` исключено из дифф-обновления песни (`saveToDb`) — иначе
оно снова записало бы устаревший JSON.

**Починка `root_folder` (BR-073, аудит #336, PKG-S3-22).** Если каталог песни
не существует, `getHealthReportList` вызывает `Song.repairRootFolder()`: кандидат
принимается, только если он единственный и в нём лежит файл этой песни. Раньше
починка была побочным эффектом чтения геттера `rootFolder` (запись в БД из
read-пути + риск указать на чужой каталог).

Внутренний helper `localStorageReadinessVerdict(reports, karaokeFileType)`
(до BR-279 — `isOkInLocalStorage`, возвращал только `Boolean`):

```kotlin
// null — проверка не выполнялась: состояние хранилища неизвестно
fun localStorageReadinessVerdict(reports, karaokeFileType): Boolean? {
    val own = reports.filter { it.description == "${type}/${LOCAL_STORAGE.name}" }
    if (own.any { it.storageStateUnknown || it.healthReportStatus == WAITING }) return null
    return own.any { it.healthReportStatus == OK }
}
```

**BR-279 (#376).** При открытом circuit breaker'е `actionsLocalStorage` файл не проверяет
вовсе: возвращает `FATAL_ERROR` «Локальное хранилище недоступно» (`storageStateUnknown = true`).
До правки это трактовалось как «файла нет», и кратковременный сбой локального MinIO
(cooldown 30 с) обнулял `stemAccompanimentReady` / `stemVocalReady` / `pictureAlbumReady` /
`pictureAuthorReady` у уже готовых песен — вплоть до записи в БД через `song.saveToDbLocked()`.
Теперь `null` означает «неизвестно», и флаг сохраняет прежнее значение. Отсутствие отчёта
по типу и настоящий `ERROR` (проверка выполнена, файла нет) по-прежнему дают `false`.

Если хотя бы один флаг изменился — `song.saveToDbLocked()` (см. [persistence domain](../../persistence/domain.md)).

**NB**: колонки `*Ready` создаются с `DEFAULT false` (см.
`deploy/karaoke-db/26_player_readiness_flags.sql`), поэтому для ранее
существовавших песен нужен разовый backfill — это делает
`recalculatePlayerReadiness`.

### `recomputeAndBroadcast`

`HealthReport.kt:2153-2171`. Единая точка «пересчитать HealthReport
песни и разослать SSE healthReports» (та же логика, что в эндпоинте
`/song/healthReportList`).

```kotlin
fun recomputeAndBroadcast(songId, database, storageService, storageApiClient):
    List<HealthReport> {
    val song = Song.loadFromDbById(...) ?: return emptyList()
    val reports = song.healthReportList()
    reconcilePlayerReadinessFlags(song, reports)
    val dtoErrors = reports.errorsOnly().map { it.toDTO() }
    SNS.send(SseNotification.healthReports(songId, dtoErrors))
    return reports
}
```

Возвращает **полный список отчётов (с canResolve)** — нужен каскаду для
выбора следующего решаемого шага (`startRepairAll`).

### `executeResolvable`

`HealthReport.kt`. Простой helper:

```kotlin
private fun executeResolvable(reports: List<HealthReport>) {
    reports
        .filter { it.canResolve && it.healthReportStatus == ERROR }
        .forEach { it.executeSolutionActions() }
}
```

Фильтрует отчёты, которые система **может** решить сама (`canResolve`)
и которые сейчас в `ERROR`, и выполняет их `solutionActions`.
**Pass 364 (OpenProject #75):** выполняется асинхронно в `repairExecutor`
(не блокирует HTTP).

### `startRepairAll` / `recalculatePlayerReadiness`

`HealthReport.kt` — точка входа каскадного «Исправить всё»:
пометить песню и выполнить всё решаемое сейчас.
**Pass 364 (OpenProject #75):** async, fire-and-forget.

`HealthReport.kt` — разовый backfill персистентных флагов
готовности плеера для уже существующих песен (после миграции
`26_player_readiness_flags.sql`). Параметр `author` (null/пусто →
**все** песни). Логирует прогресс каждые `PROGRESS_LOG_EVERY = 200`
песен. **Должен вызываться в фоне** — полный скан тысяч песен
небыстрый (I/O на файл).

## Зависимости | Dependencies

- **Async Process Queue** (`KaraokeProcess`):
  repair часто создаёт `KaraokeProcess.createProcess(...)` как одну
  из solution-actions. См. Pass 342 (P1).
- **Storage layer** (`KaraokeStorageService` + `StorageApiClient`):
  см. [storage domain](../../storage/domain.md) (Pass 341).
- **SSE / WebSocket**:
  `recomputeAndBroadcast` публикует в SSE-канал. SSE ещё не описан
  (P2).
- **Monitor checks** (`monitor-checks.md`):
  НЕ часть мониторинга — это **реактивный** механизм. Monitor-checks
  это **проактивный** пинг (ProdContainerCheck, RenderQueueStalledCheck).

## Known issues

- **#65** (in progress) «Ошибка при проверке наличия файла в
  удаленном хранилище»: race condition в `StorageApiClient.fileExists`.
  `WebKaraokeStorageServiceImpl` — заглушка (см. storage), реальный
  HTTP-клиент — `StorageApiClientWeb`, который использует
  WebClient+reactor.
- **Repair-loop race fix (Pass 343)**: perSong single-flight guard
  (см. [race-fixed-65.md](race-fixed-65.md)) — `attemptEnterRepair` /
  `exitRepair` через `AtomicBoolean`. **FIXED**. 4/4 unit-тестов.

## Код (физическая реализация)

- `karaoke-app/.../HealthReport.kt` (2302 строк, всё в companion)
- `karaoke-app/.../HealthReportDTO.kt`
- `karaoke-app/.../HealthReportType.kt`
- `karaoke-app/.../HealthReportStatus.kt`
- `karaoke-app/.../KaraokeFileType.kt`
- `karaoke-app/.../KaraokeFileTypeLocations.kt`
- `karaoke-app/.../KaraokeFileSymlink.kt`
- `karaoke-app/.../KaraokeFileTypeKind.kt`
- `karaoke-app/.../KaraokeFileTypeFor.kt`

## Known gaps (TODO для следующих Pass)

- [x] **UI-сторона**: `webvue3/src/components/Common/HealthReport/`
      (компоненты + Vuex `webvue3/src/components/Common/HealthReport/store.js`)
      уже есть; API-эндпоинт — `POST /api/song/healthReportList`
      (batch-вариант: `POST /api/song/healthReportList/batch`,
      `ApiController.kt:7736`/`:7763`). Прежний `/api/health/getHealthReportList`
      не существует.
- [x] **webvue3 HealthReport UI** (`webvue3/src/components/Common/HealthReport/`)
      + Vuex-модуль + endpoint `POST /api/song/healthReportList`.
- [ ] **`KaraokeProcess.THREAD_LANE_HEALTH_REPORT`** — отдельная
      thread lane; см. [async-process-queue](../../processing/components/async-process-queue.md).
- [ ] **`LEGACY_MLT_FILE_TYPES`** (строка 1107) — set из 4 типов,
      помечен как legacy; нужно понять, планируется ли удаление.
- [ ] **`StatsCache` / `StatsDebugController`** — связь
      HealthReport с admin-метриками.
- [ ] **`executeResolvable`** (строка 2252): точка входа repair-loop,
      7 строк, но не описана.
- [ ] **`recomputeAndBroadcast`** (строка 2153): связь с SSE.
- [ ] **Race conditions в `StorageApiClient.fileExists`** (задача #65).
- [ ] **`KaraokeProcess.THREAD_LANE_HEALTH_REPORT`** — отдельный
      thread lane; описан в Async Process Queue (Pass 342).
- [ ] **`LEGACY_MLT_FILE_TYPES`** (строка 1107): set из 4 типов,
      помечен как legacy; нужно понять, планируется ли удаление.

### Пакет `PKG-S3-26` (OpenProject #398, аудит #336) — правила, зафиксированные в Knowledge

| BR | Файл | Правило |
|---|---|---|
| BR-280 | `karaoke-app/.../HealthReport.kt` | Задания песни читаются **одним** `KaraokeProcess.loadList` в `getHealthReportList` и раздаются в `actions*` параметром `songProcesses`; отбор — `filterSongProcesses(...)` в памяти. Новый `loadList` внутри `actions*` = возврат N+1 |
| BR-281 | `karaoke-app/.../HealthReport.kt` | `cachedFileInfo` возвращает типизированный `StorageFileInfoProbe` (`Known`/`Missing`/`Unknown`), а не `null`. «Не удалось узнать» (`Unknown`) → `WARNING` + `storageStateUnknown = true`, **не** `OK` |
| BR-282 | `karaoke-app/.../HealthReport.kt` | Guard `repairInFlight` (`attemptEnterRepair`/`exitRepair`) берётся и снимается в `try/catch/finally` вокруг **всего** тела `startRepairAll` |
| BR-283 | `karaoke-app/.../HealthReport.kt` | Ошибка удаления объекта из REMOTE-хранилища не гасится: `deleteRemoteFileOrThrow` логирует через SLF4J с throwable и пробрасывает исключение в repair-цикл |
| BR-284 | `karaoke-app/.../HealthReport.kt` | Каждое действие ремонта выполняется в своём `try/catch`; `executeResolvable` возвращает число неуспешных действий, они уходят в лог и в SSE (`type=error`) |
| BR-285 | `karaoke-app/.../HealthReport.kt` + `ApiController.kt` | `POST /api/song/executeHealthReportActions` **не** выполняет `solutionActions` в потоке HTTP-запроса (там есть блокирующие `downloadFile` из MinIO): действие уходит в `repairExecutor`, эндпоинт отвечает `202 Accepted`, результат — по SSE |
| BR-286 | `karaoke-app/.../HealthReport.kt` | Непрочитанный каталог симлинков даёт `WARNING`-запись без действия + SLF4J `WARN` с throwable (категория `infra.health.report`), а не тихий пропуск |
| BR-287 | `karaoke-app/.../HealthReport.kt` + `ApiController.kt` | `recalculatePlayerReadiness` возвращает `ReadinessRecalcResult(processed, failed)`; SSE показывает оба числа, при `failed > 0` — `type=error` |
| BR-288 | `karaoke-app/.../CacheEtagSizeBackfill.kt` | Ошибка выборки строк кеша **пробрасывается** (SSE `error`), а не превращается в пустой список с сообщением «обработано 0 строк» |

## Ловушки (пакет `PKG-S3-26`, OpenProject #398)

1. **`null` из `cachedFileInfo` больше не означает «файл актуален».** Если вызывающий
   хочет сравнить размер объекта с размером файла на диске, он обязан спросить
   `fileActualityVerdict(probe, pathToFile)` и разобрать **четыре** исхода, а не два.
   `UNKNOWN` → `WARNING` + `storageStateUnknown = true`; `NOT_ACTUAL` → delete + upload;
   `DISK_VANISHED` → `WARNING` без действий (BR-278); `ACTUAL` → `OK`.
2. **Готовность плеера (`song_name_censored` → `*Ready`) не сбрасывается по неполной
   проверке.** `reconcilePlayerReadinessFlags` пропускает флаг, если отчёт помечен
   `storageStateUnknown` или имеет статус `WAITING` (BR-279 + BR-281).
3. **Задания песни читаются один раз.** `getHealthReportList` загружает `songProcesses`
   и передаёт их в `actions*`. Новый код в `actions*` не должен вызывать
   `KaraokeProcess.loadList` — это возвращает N+1 (BR-280).
4. **Ремонт выполняется вне HTTP-треда.** `POST /api/song/executeHealthReportActions`
   отвечает `202 Accepted` и НЕ выполняет `solutionActions` в потоке запроса: там есть
   блокирующие `downloadFile` из MinIO (BR-285).
5. **Guard `repairInFlight` снимается в `finally`.** Любая новая точка входа в repair
   обязана брать guard (`attemptEnterRepair`) и снимать его (`exitRepair`) в `finally`
   (BR-282), иначе песня «залипает» до рестарта приложения.
6. **Ошибка действия ремонта не остаётся тихой.** `executeResolvable` возвращает число
   неуспешных действий; вызывающий обязан показать его в SSE (BR-284).
7. **Битый симлинк удаляется через `Files.isSymbolicLink` / `Files.deleteIfExists`.**
   `File.exists()` для битого симлинка = `false` — действие «удалить файл» было no-op
   (BR-277).
8. **Логика отчёта о здоровье пишется через SLF4J-категорию `infra.health.report`**,
   а не в `println`: пропуск проверки обязан быть виден в логах (BR-286).

## Связанные ADR | Related ADRs

- `archive/docs/features/monitoring.md` — оригинальный документ,
  archive (НЕ Knowledge). Требует миграции в Knowledge.
- `archive/docs/features/dual-db-sync.md` — упомянут в KDoc.