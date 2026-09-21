# Q1 — Исследование пула HealthReport и точки слома всплытия

> **Тикет**: [wayfinder:research] #120
> **Тип**: research (read-only)
> **Дата**: 2026
> **Стек**: karaoke-app (Kotlin backend), webvue3 (Vue 2/3 админка)

## TL;DR

В проекте Karaoke «пул HealthReport» существует **на двух уровнях**:

1. **Backend (`karaoke-app`)**: «пул заданий ремонта» — это **таблица `tbl_processes` в Postgres** (НЕ in-memory очередь, НЕ redis). Каждое repair-задание вставляется в `tbl_processes` со `status = WAITING`, `thread_id = THREAD_LANE_HEALTH_REPORT` (= 1) и приоритетом `prior = -2`. Выборка делается SQL-запросом с оконной функцией `ROW_NUMBER() OVER (PARTITION BY thread_id ORDER BY process_priority, process_order, id)` — т.е. **FIFO по `id` внутри одного `process_priority`**. Отдельной структуры «всплытия»/«приоритета наоборот» нет.

2. **Frontend (`webvue3/SongsTable.vue`)**: «пул HR-запросов для UI» — это **JavaScript-массив `hrQueue`** (FIFO: `push` в конец, `shift` из начала) с лимитом параллельности `HR_MAX_CONCURRENT = 3`. **Точка слома всплытия** — строки `1069` и `1901` файла `SongsTable.vue`: при каждой смене страницы (`watch.currentPage`) или открытии редактора (`editSong`) `hrQueue = []` — **вся очередь очищается без восстановления**. Задания, которые были поставлены в очередь для предыдущей страницы, **теряются**.

API endpoint для количества заданий в пуле: **`POST /processes/countwaiting`** (`ApiController.kt:2106`) — возвращает `Long`, считает **все** `WAITING` во **всех** lanes (не только HR). Дополнительно есть SSE-событие `PROCESS_COUNT_WAITING` (см. `KaraokeProcessWorker.kt:803`), которое обновляет Vuex `Processes.store.js::countWaiting` в реальном времени.

---

## 1. Backend: где хранится очередь repair-заданий

### 1.1 Физический носитель

**`tbl_processes` в Postgres** (НЕ in-memory atomic-очередь, НЕ redis). Задание — запись со статусом `WAITING` / `WORKING` / `DONE` / `ERROR` / `CREATING`.

Подтверждение: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcess.kt:505` —

```kotlin
fun getCountWaiting(
    database: KaraokeConnection,
    throwOnError: Boolean = false,
): Long {
    ...
    val sql = "select count(*) as cnt from tbl_processes where process_status = 'WAITING' and process_command <> 'tail'"
    ...
}
```

### 1.2 Thread-lanes

Lane определяется через поле `thread_id` (`KaraokeProcess.kt:462-466`):

```kotlin
const val THREAD_LANE_HEAVY_RENDER = 0         // MELT_*, DEMUCS*, SHEETSAGE — тяжёлые, не параллелить
const val THREAD_LANE_LIGHT_BACKGROUND = -1    // SmartCopy, uploadToLocalStore
const val THREAD_LANE_REMOTE_STORE_UPLOAD = -2 // uploadToRemoteStore
const val THREAD_LANE_HEALTH_REPORT = 1        // автоисправление HealthReport (кроме MELT_*)
const val THREAD_LANE_STEM_JOBS = 2            // Premium StemJob
```

HealthReport использует **только `THREAD_LANE_HEALTH_REPORT = 1`** (кроме MELT_*, которые на `0`).

### 1.3 Метод `add` / создание задания

Файл: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcess.kt:1003-1100` — `createProcess(...)`.

Цитата (логика добавления в пул):

```kotlin
@Suppress("UNCHECKED_CAST")
fun createProcess(
    song: Song,
    action: KaraokeProcessTypes,
    doWait: Boolean = false,
    prior: Int = 1,
    threadId: Int,
    context: Map<String, Any> = emptyMap(),
): Long {
    // Находим есть ли уже такой процесс. Если нет - создаём. Если есть и не в статусе "в работе" - пересоздаём
    val existedProcessesLookupArgs =
        mutableMapOf(
            "song_id" to song.id.toString(),
            "process_type" to action.name,
            "thread_id" to threadId.toString(),
        )
    ...
    val existedProcesses = loadList(existedProcessesLookupArgs, song.database)
    var wasWorking = false
    existedProcesses.forEach { existedProcess ->
        if (existedProcess.status != KaraokeProcessStatuses.WORKING.name) {
            delete(existedProcess.id, song.database)        // ← дедуп: старый WAITING удаляется
        } else {
            wasWorking = true
        }
    }
    if (wasWorking) return 0

    val karaokeProcess = KaraokeProcess(song.database)
    with(karaokeProcess) {
        ...
        this.status = if (doWait) KaraokeProcessStatuses.WAITING.name else KaraokeProcessStatuses.CREATING.name
        this.order = -1                                       // ← порядок по умолчанию
        this.priority = prior                                  // ← приоритет из аргумента
        ...
        this.threadId = threadId                               // ← lane
        ...
    }
    karaokeProcess.save()
    return karaokeProcess.id
}
```

**Добавление в пул (т.е. в таблицу `tbl_processes`)**: при первом вызове — `INSERT` с `status = WAITING` (если `doWait=true`) или `status = CREATING` (если `doWait=false`, далее фоновый поток переводит в `WAITING`). При наличии существующего WAITING-процесса того же типа/песни/лейна — старый **удаляется** и создаётся новый (`existedProcess.status != WORKING` → `delete(id)`).

**В конец или в начало?** Ни то, ни другое в прямом смысле. Порядок задаётся **полем `process_priority` (default `prior = 1` или `-2` для HR-repair) + `process_order` (default `-1`) + `id` (монотонный автоинкремент)**. Сортировка — по `(process_priority DESC, process_order DESC, id DESC)` (см. п. 1.5). Поскольку `process_priority` HR-заданий всегда `-2` (см. ниже), в начале оказываются **самые свежие** по `id` — т.е. **LIFO-подобное поведение внутри одного приоритета**.

### 1.4 Какие типы попадают в HR-repair

Файл: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReport.kt:2259-2274` —

```kotlin
// Типы заданий, которые HealthReport ставит в очередь как автоисправление. Только для них
// имеет смысл пересчитывать HealthReport после завершения задания в воркере — это исключает
// многократный (по числу sub-шагов) пересчёт на тяжёлых MELT*-рендерах.
val HR_REPAIR_PROCESS_TYPES: Set<KaraokeProcessTypes> =
    setOf(
        KaraokeProcessTypes.UPLOAD_TO_LOCAL_STORE,
        KaraokeProcessTypes.UPLOAD_TO_REMOTE_STORE,
        KaraokeProcessTypes.KEY_BPM_FROM_FILE,
        KaraokeProcessTypes.DEMUCS2,
        KaraokeProcessTypes.DEMUCS5,
        KaraokeProcessTypes.FF_MP3_ACCOMPANIMENT,
        KaraokeProcessTypes.FF_MP3_VOCAL,
        KaraokeProcessTypes.FF_MP3_BASS,
        KaraokeProcessTypes.FF_MP3_DRUMS,
        KaraokeProcessTypes.FF_MP3_OTHER,
    )
```

10 типов. **MELT_*-рендеры НЕ входят** — они идут в `THREAD_LANE_HEAVY_RENDER = 0`.

### 1.5 Метод, отвечающий за порядок обработки чанков (выбор следующего)

Файл: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcess.kt:792-865` — `getProcessesToStart(...)`. Цитата ключевого SQL:

```kotlin
fun getProcessesToStart(
    database: KaraokeConnection,
    throwOnError: Boolean = false,
): Map<Int, KaraokeProcess> {
    val result: MutableMap<Int, KaraokeProcess> = mutableMapOf()
    ...
    // val sql = "SELECT * FROM tbl_processes WHERE process_status = 'WAITING' ORDER BY process_priority, process_order, id LIMIT 1;"

    val sql =
        """
        SELECT *
        FROM (
            SELECT *,
                   ROW_NUMBER() OVER (
                       PARTITION BY thread_id
                       ORDER BY process_priority, process_order, id
                   ) AS rn
            FROM tbl_processes
            WHERE process_status = 'WAITING'
              AND process_deleted_at IS NULL
        ) ranked
        WHERE rn = 1;
        """.trimIndent()

    try {
        statement = connection.createStatement()
        rs = statement.executeQuery(sql)
        while (rs.next()) {
            ...
            result.put(process.threadId, process)
        }
    }
    ...
    return result
}
```

**Порядок**: `ORDER BY process_priority DESC, process_order DESC, id DESC` — **сначала высший `process_priority` (= наименьшее отрицательное число; для HR = `-2`), затем `process_order`, затем свежий `id`**. Возвращает **по одному заданию на lane** (`PARTITION BY thread_id` + `WHERE rn = 1`) — т.е. **`THREAD_LANE_HEALTH_REPORT` всегда берёт ровно одно задание (свежайшее по `id` среди равных по приоритету)**.

**Внутри `compareTo` Kotlin** (`KaraokeProcess.kt:281-287`) — тот же порядок для сортировки `List<KaraokeProcess>` в памяти:

```kotlin
override fun compareTo(other: KaraokeProcess): Int {
    var result = priority.compareTo(other.priority)
    if (result != 0) return result
    result = order.compareTo(other.order)
    if (result != 0) return result
    return id.compareTo(other.id)
}
```

### 1.6 Главный цикл воркера

Файл: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcessWorker.kt:827-1015` — `doStart(...)`.

```kotlin
private fun doStart(...) {
    val timeout = 10L
    ...
    while (isWork) {
        ...
        val processesToStart = getKaraokeProcessesToStart(database)
        // ↑ по одному на каждый lane
        processesToStart.forEach { (threadId, karaokeProcess) ->
            // Создаёт KaraokeProcessThread, стартует subprocess / runFunctionWithArgs
            ...
        }
        ...
        Thread.sleep(timeout)
    }
}
```

**Архитектурное решение (Pass 341, ADR-нет)**: один воркер-поток (`KaraokeProcessWorker.start()`) запускает `doStart()` в цикле; внутри одного lane задания идут **строго последовательно** (lane = сериализация). Тяжёлые рендеры (`THREAD_LANE_HEAVY_RENDER = 0`) — **никогда не параллельно** (см. KDoc `async-process-queue.md`).

### 1.7 Repair-loop поверх пула

Файл: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReport.kt:2267-2530` — `startRepairAll`, `executeResolvable`, `onRepairProcessFinished`. **Не пул, а **каскад** для одной песни, который при `executeSolutionActions()` вставляет задания в `tbl_processes` через `KaraokeProcess.createProcess(...)` с `threadId = THREAD_LANE_HEALTH_REPORT`.**

Цитата `executeResolvable` (`HealthReport.kt:2452-2456`):

```kotlin
private fun executeResolvable(reports: List<HealthReport>) {
    reports
        .filter { it.canResolve && it.healthReportStatus == ERROR }
        .forEach { it.executeSolutionActions() }
}
```

Каждое `executeSolutionActions` (`HealthReport.kt:66`) вызывает `solutionActions.forEach { action -> action() }`, и каждое action — это лямбда, **либо** синхронно чинющая файл, **либо** `KaraokeProcess.createProcess(... doWait = true, threadId = THREAD_LANE_HEALTH_REPORT ...)` (см. `actionsLocalStorage` / `actionsRemoteStorage` / `actionsLocalFileSystem`).

Каскад для одной песни защищён **per-song single-flight guard** (`HealthReport.kt:2289-2304`) через `attemptEnterRepair(songId) / exitRepair(songId)` — `ConcurrentHashMap<Long, AtomicBoolean>`. Pass 343, OpenProject #65.

**НЕ-пул**: `repairExecutor = Executors.newFixedThreadPool(4)` (`HealthReport.kt:94-97`) — это **НЕ** пул задач; это пул из 4 потоков, в котором запускаются каскады для **разных** песен. Каскад одной песни = одна задача в `repairExecutor`. После завершения под-задания воркер дёргает `onRepairProcessFinished(songId, success, ...)` (`HealthReport.kt:2501-2529`), который пересчитывает HR и запускает **следующий ставший решаемым шаг** каскада.

---

## 2. Frontend: клиентский пул HR-запросов в админке «Песни»

### 2.1 Структуры данных

Файл: `webvue3/src/components/Songs/SongsTable.vue:654-656` — `data()`:

```javascript
hrQueue: [],
hrRunning: 0,
HR_MAX_CONCURRENT: 3,
```

**`hrQueue`** — JavaScript `Array`, **FIFO-очередь id песен** (`push` в конец, `shift` из начала).
**`hrRunning`** — счётчик текущих активных HTTP-запросов `/api/song/healthReportList`.
**`HR_MAX_CONCURRENT = 3`** — лимит параллельных запросов.

### 2.2 `enqueue` / добавление в очередь

Файл: `webvue3/src/components/Songs/SongsTable.vue:1326-1339`:

```javascript
_enqueueHrRequest(songId) {
  this.hrQueue.push(songId)        // ← push В КОНЕЦ массива (FIFO)
  this._processHrQueue()
},

_processHrQueue() {
  while (this.hrRunning < this.HR_MAX_CONCURRENT && this.hrQueue.length > 0) {
    const id = this.hrQueue.shift() // ← shift ИЗ НАЧАЛА массива (FIFO)
    this.hrRunning++
    this.$store.dispatch('setCurrentSongHealthReports', id).finally(() => {
      this.hrRunning--
      this._processHrQueue()
    })
  }
},
```

**Добавление в конец (`push`), обработка с начала (`shift`) — классический FIFO**. Лимит — 3 параллельных запроса (это константа `HR_MAX_CONCURRENT = 3`).

### 2.3 `updateHealthReportForCurrentPage` — инициация каскада

Файл: `webvue3/src/components/Songs/SongsTable.vue:1312-1325`:

```javascript
updateHealthReportForCurrentPage() {
  for (const songId of this.songsIds) {
    const songPageNumber = this.songIdAndPageId.get(songId)
    if (songPageNumber === this.currentPage) {
      const filteredSongs = this.songsDigests.filter((song) => song.id === songId)
      if (filteredSongs && filteredSongs.length > 0) {
        const song = filteredSongs[0]
        if (song.healthReportText === '-') {        // ← только если ещё не загружено
          this._enqueueHrRequest(songId)
        }
      }
    }
  }
},
```

Перебирает **все id песен каталога** (`this.songsIds`), но ставит в очередь только те, что относятся к **текущей странице** (`songIdAndPageId.get(songId) === this.currentPage`) и **только если `healthReportText === '-'`** (т.е. ещё не подгружалось).

### 2.4 Что такое «всплытие» — точка слома

**`watch.currentPage`** (`SongsTable.vue:1065-1073`):

```javascript
currentPage: {
  handler(newPage) {
    // Сохраняем страницу в store, чтобы она восстановилась после переключения на другой компонент.
    this.$store.commit('setSongsTableCurrentPage', newPage)
    this.hrQueue = []                              // ← ТОЧКА СЛОМА #1
    this.updateHealthReportForCurrentPage()
    this.reloadAssignmentStatus()
  },
},
```

При **каждой смене страницы** (`currentPage`) фронт **очищает всю очередь `hrQueue = []`**.

**`editSong(id)`** (`SongsTable.vue:1900-1905`):

```javascript
async editSong(id) {
  this.hrQueue = []                                // ← ТОЧКА СЛОМА #2
  await this.$store.dispatch('setCurrentSongId', id)
  this.isSongEditVisible = true
  this.updateHealthReportForCurrentPage()
},
```

При **открытии редактора** (`editSong`) очередь тоже очищается.

### 2.5 Что происходит при возврате на посещённую страницу

В `SongsView.vue` нет `<keep-alive>`, поэтому `SongsTable` перемонтируется при возврате:

```html
<template>
  <div class="songstable">
    <SongsTable />
  </div>
</template>
```

`mounted()` (`SongsTable.vue:1075-1086`) выполняется заново:

```javascript
async mounted() {
  await this.$store.dispatch('loadTableSettings')
  this.perPage = this.$store.getters.getRowsPerPage('songs')
  this.allowAddSync = await this.propAllowAddSync()
  await this.$store.dispatch('loadEditorDefaultTarget')
  this.$store.dispatch('loadEditorSiteUsers', this.$store.getters.getEditorDefaultTarget)
  this.reloadAssignmentStatus()
  // ← НЕ вызывает updateHealthReportForCurrentPage() напрямую
}
```

`updateHealthReportForCurrentPage` запускается только через **`watch.countRows`** (`SongsTable.vue:1046-1057`) — он триггерится после первой загрузки `songsDigest`:

```javascript
countRows: {
  handler(newCount) {
    const totalPages = Math.max(1, Math.ceil(newCount / this.perPage))
    if (this.currentPage > totalPages) {
      this.currentPage = 1
    }
    this.updateHealthReportForCurrentPage()
    this.reloadAssignmentStatus()
  },
},
```

`hrQueue = []` при этом НЕ сбрасывается явно, но `data()` инициализирует его как `[]`, так что при перемонтировании он пуст. **То есть при возврате на посещённую страницу HR-запросы заново ставятся в пустую очередь → запросы повторяются (re-fetch).**

---

## 3. Точка слома всплытия (summary)

**Корень проблемы** — **в webvue3, не в backend**. Backend-cascade HealthReport (`startRepairAll` / `onRepairProcessFinished` / `repairExecutor`) **не имеет понятия «всплытия»** — это последовательный каскад на уровне одной песни, защищённый `attemptEnterRepair/exitRepair`. То, что воспринимается как «слом всплытия», — это **потеря HR-запросов текущей страницы** при навигации в `webvue3/SongsTable.vue`:

| Место | Действие | Эффект |
|---|---|---|
| `SongsTable.vue:1069` (`watch.currentPage.handler`) | `this.hrQueue = []` | При смене страницы очередь HR-запросов теряется |
| `SongsTable.vue:1901` (`editSong`) | `this.hrQueue = []` | При открытии редактора — то же самое |
| `SongsView.vue` | без `<keep-alive>` | При возврате SongsTable перемонтируется → `hrQueue = []` (default) |

**In-flight запросы** (`finally(() => { this.hrRunning--; this._processHrQueue() })`) **в принципе завершаются**, но поскольку `_processHrQueue` ищет следующий id в уже очищенной `hrQueue` — **следующие песни страницы не догружаются до явного `updateHealthReportForCurrentPage`**.

**Нет специальной логики «вернуть HR-запросы в начало очереди при возврате на страницу»** — это и есть «слом всплытия».

Backend `tbl_processes` — **НЕ имеет проблемы всплытия**: задания не теряются (живут в БД, видны через sync в `karaoke-web`), порядок определяется SQL-оконной функцией.

---

## 4. API endpoint количества заданий в пуле

### 4.1 REST: `POST /processes/countwaiting`

Файл: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt:2106-2108`:

```kotlin
// Получение списка статусов процессов
@PostMapping("/processes/countwaiting")
@ResponseBody
fun getCountWaiting(): Long = KaraokeProcess.getCountWaiting(database = WORKING_DATABASE)
```

**Контракт**:

- **Метод**: `POST`
- **URL**: `/processes/countwaiting`
- **Тело**: пустое
- **Ответ**: `Long` — количество `WAITING`-заданий **во всех lanes** (включая HR, MELT, SmartCopy, ...). SQL:
  ```sql
  select count(*) as cnt from tbl_processes
  where process_status = 'WAITING' and process_command <> 'tail'
  ```
- **Где используется**:
  - `monitor/checks/RenderQueueStalledCheck.kt:18` — мониторинг (RenderQueueStalled).
  - Внутри `KaraokeProcessWorker` — для SSE-счётчика.
- **Нет фильтра по `thread_id`** → возвращает общее число. Если нужен только HR — придётся добавить параметр `thread_id`.

### 4.2 SSE: `PROCESS_COUNT_WAITING`

Файл: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcessWorker.kt:801-817`:

```kotlin
fun sendCountWaitingMessage(countWaiting: Long) {
    val previous = lastSentCountWaiting
    if (previous != null && previous == countWaiting) return
    lastSentCountWaiting = countWaiting
    SNS.send(
        ProcessCountWaitingMessage(
            countWaiting = countWaiting,
            ...
        ),
    )
}
```

SSE-сообщение `PROCESS_COUNT_WAITING` шлётся при:

- старте воркера (`doStart` — одно начальное сообщение),
- создании нового WAITING-задания (`createDbInstance` → `KaraokeProcessWorker.sendCountWaitingMessage(KaraokeProcess.getCountWaiting(...))`),
- завершении subprocess (в `KaraokeProcessThread.run`),
- force-stop (в `KaraokeProcessWorker.forceStop`),
- перезапуске БД-коннекшна.

**Дедуп**: если значение не изменилось — сообщение НЕ шлётся (см. `lastSentCountWaiting`).

### 4.3 Frontend-приёмник SSE

Файл: `webvue3/src/components/Processes/store.js:45, 197`:
```javascript
state.countWaiting = '...',
...
state.countWaiting = userEventData.countWaiting
```

Файл: `webvue3/src/components/Common/ProcessWorker.vue:23, 94`:
```html
<div class="text-count-waiting" v-text="countWaiting" />
...
countWaiting() { ... }
```

То есть `countWaiting` отображается как **общий счётчик «Заданий в очереди»** в верхней панели админки (`ProcessWorker.vue`). **Не разделяет по lanes** (нет UI-бейджа только для HR).

### 4.4 Endpoint списка (с фильтром по lane)

Файл: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/KaraokeProcessAdminController.kt:32, 45`:

```kotlin
@RequestMapping("/api/admin/processes")
...
/** GET /api/admin/processes — список с фильтрацией. */
```

**Контракт**:

- **Метод**: `GET`
- **URL**: `/api/admin/processes`
- **Query**: `thread_id`, `process_status`, `filter_limit`, и пр. (см. `KaraokeProcessAdminService.kt:44+`)
- **Ответ**: список `KaraokeProcessAdminDTO` с фильтрацией по любой колонке `tbl_processes`, включая **`thread_id = 1` (THREAD_LANE_HEALTH_REPORT)**.

Этот endpoint **уже есть** и может быть использован для UI-бейджа «в пуле HR сейчас N заданий» (Q2) без нового бэкенда.

### 4.5 Endpoint health-report list

Файл: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt:7673-7680`:

```kotlin
// Получение healthReportList
@PostMapping("/song/healthReportList")
@ResponseBody
fun getHealthReportList(...) = ...
```

**Контракт**:

- **Метод**: `POST`
- **URL**: `/api/song/healthReportList`
- **Параметры**: `{ id: songId }`
- **Ответ**: `List<HealthReportDTO>` — полный список отчётов по одной песне.

**Используется**:

- `webvue3/src/components/Songs/store.js:1846-1860` — `setCurrentSongHealthReports(ctx, currId)` — главный вызов.
- `webvue3/src/components/Common/HealthReport/store.js:30` — `loadHealthReportList` — для модалки `HealthReportTable.vue`.

---

## 5. Где инициируется каскад HealthReport при навигации

**На frontend (страница «Песни»)**:

1. `mounted()` (SongsTable.vue:1075) — НЕ вызывает `updateHealthReportForCurrentPage` напрямую.
2. `watch.countRows` (SongsTable.vue:1046-1057) — после первой загрузки каталога песен → `updateHealthReportForCurrentPage()` → для всех песен текущей страницы с `healthReportText === '-'` → `_enqueueHrRequest(songId)` → ставит в `hrQueue`.
3. `watch.currentPage` (SongsTable.vue:1065-1073) — при смене страницы → **`this.hrQueue = []`** → `updateHealthReportForCurrentPage()` (точка слома #1).
4. `editSong(id)` (SongsTable.vue:1900-1905) — при открытии редактора → **`this.hrQueue = []`** → `updateHealthReportForCurrentPage()` (точка слома #2).

**На backend (страница «Песни» → ремонт)**:

- `webvue3/src/components/Common/HealthReport/store.js:63-72` — `repairAllPromise(ctx, id)` → `POST /api/song/repairAll` → бэкенд вызывает `HealthReport.startRepairAll(song, ...)` → submit в `repairExecutor` → `executeResolvable` → вставляет `KaraokeProcess` в `tbl_processes` через `createProcess(... threadId = THREAD_LANE_HEALTH_REPORT, prior = -2, ...)`.
- После завершения каждого под-задания воркер дёргает `onRepairProcessFinished(songId, success, ...)` (если тип ∈ `HR_REPAIR_PROCESS_TYPES`) → рекомпьют + следующий ставший решаемым шаг.

---

## 6. Поиски, которые НЕ нашли ничего релевантного (фиксация)

> По условию задачи явно зафиксировать отрицательные результаты.

- **`HealthReport*Pool*`** / **`HealthReport*Queue*`** / **`HealthReport*AsyncPool*`** / **`HealthReport*PriorityQueue*`** → **no relevant code** (никаких структур с такими именами нет; пул = таблица `tbl_processes`).
- **`HealthReport.addFirst` / `addLast` / `enqueue`** → **no relevant code** в `HealthReport.kt` — добавление идёт через `KaraokeProcess.createProcess(...)` (см. п. 1.3).
- **`PriorityQueue`** в контексте HealthReport → **no relevant code** (приоритет есть, но хранится в колонке `process_priority`, не в `PriorityQueue`).
- **`ChunkExecutor` / `BatchExecutor` / `TaskQueue`** в контексте HealthReport → **no relevant code** (см. `research/83-db-pool-root-cause/REPORT.md` — там `chunk` относится к SQL-чанкам в sync-цикле `processRemoteSongsSyncBatch`, не к HR).
- **`PoolBadge`** / **`HealthBadge`** как готовые Vue-компоненты → **no relevant code** (бейдж `countWaiting` уже есть в `ProcessWorker.vue`, но **общий**, не для HR; компонента `HealthBadge` или `PoolBadge` не существует).
- **`AutoOneClickSyncScheduler.kt:267` `history.addLast(run)` + `pollFirst()`** — найдено, **НЕ относится к HealthReport**: это bounded-history `LinkedList` последних 10 sync-ранов (FR-009), не пул задач.
- **`StorageMetadataCache.kt:73-84` `LinkedBlockingQueue`** — найдено, **НЕ относится к HealthReport**: это `LinkedBlockingQueue` внутри `ThreadPoolExecutor` (кеш pool — потоки умирают после простоя, **НЕ** очередь HR-заданий).

---

## 7. Файлы и строки (сводка для fix-спеки)

### Backend
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReport.kt:43-66` — `data class HealthReport`, `executeSolutionActions`.
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReport.kt:94-97` — `repairExecutor` (4-thread pool).
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReport.kt:2259-2274` — `HR_REPAIR_PROCESS_TYPES` (10 типов).
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReport.kt:2280-2304` — `autoRepairSongIds`, `attemptEnterRepair/exitRepair/cleanupRepair` (per-song single-flight).
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReport.kt:2452-2456` — `executeResolvable`.
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReport.kt:2467-2492` — `startRepairAll`.
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReport.kt:2501-2529` — `onRepairProcessFinished`.
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcess.kt:281-287` — `compareTo` (priority → order → id).
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcess.kt:462-466` — `THREAD_LANE_*` константы.
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcess.kt:505-538` — `getCountWaiting` (SQL).
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcess.kt:792-865` — `getProcessesToStart` (SQL `ROW_NUMBER()`).
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcess.kt:1003-1100` — `createProcess` (add/enqueue).
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcessWorker.kt:801-817` — `sendCountWaitingMessage` (SSE).
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcessWorker.kt:827-1015` — `doStart` (главный цикл).
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt:2106-2108` — `POST /processes/countwaiting`.
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt:7673-7680` — `POST /song/healthReportList`.
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/KaraokeProcessAdminController.kt:32-45` — `GET /api/admin/processes` (с фильтром по `thread_id`).
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/monitor/checks/RenderQueueStalledCheck.kt:18` — единственный потребитель `getCountWaiting` для алертов.

### Frontend
- `webvue3/src/components/Songs/SongsTable.vue:654-656` — `data(): hrQueue, hrRunning, HR_MAX_CONCURRENT = 3`.
- `webvue3/src/components/Songs/SongsTable.vue:1040-1073` — `watch.countRows`, `watch.currentPage` (точка слома #1: строка 1069).
- `webvue3/src/components/Songs/SongsTable.vue:1075-1086` — `mounted()`.
- `webvue3/src/components/Songs/SongsTable.vue:1312-1339` — `updateHealthReportForCurrentPage`, `_enqueueHrRequest`, `_processHrQueue`.
- `webvue3/src/components/Songs/SongsTable.vue:1340-1358` — `repairAllForCurrentPage`.
- `webvue3/src/components/Songs/SongsTable.vue:1846-1860` — `setCurrentSongHealthReports` (action → POST /api/song/healthReportList).
- `webvue3/src/components/Songs/SongsTable.vue:1890-1905` — `showHealthReportTable`, `closeHealthReportTable`, `editSong` (точка слома #2: строка 1901).
- `webvue3/src/components/Songs/store.js:1549-1550` — `setCurrentSongHealthReports` (commit).
- `webvue3/src/components/Songs/store.js:1670-1725` — `healthReportMessageByUserEvent` (SSE-обновление).
- `webvue3/src/components/Songs/store.js:1844-1860` — `setCurrentSongHealthReports` (action).
- `webvue3/src/components/Songs/store.js:2588-2590` — `healthReportMessageByUserEvent` (action).
- `webvue3/src/views/SongsView.vue` — контейнер `<SongsTable />`, **БЕЗ `<keep-alive>`**.
- `webvue3/src/components/Common/HealthReport/store.js:30-72` — `loadHealthReportList`, `repairAllPromise`.
- `webvue3/src/components/Common/ProcessWorker.vue:23, 94, 165` — UI `countWaiting` (общий, не для HR).
- `webvue3/src/components/Processes/store.js:45, 94, 197` — Vuex `countWaiting`.
- `webvue3/src/App.vue:390-392, 423-426` — SSE-приёмник `HEALTH_REPORTS` → `setCountWaiting`.
- `webvue3/src/App.vue:437-439` — `setCountWaiting` → dispatch.

---

## 8. Подтверждённые факты для Q3/Q4

### Для Q3 (фикс всплытия)

**Всплытие — frontend-проблема**. Backend-cascade `startRepairAll / onRepairProcessFinished` **не теряет задания** (живут в `tbl_processes`).

Точки слома на frontend:
1. `SongsTable.vue:1069` — `hrQueue = []` при смене страницы.
2. `SongsTable.vue:1901` — `hrQueue = []` при открытии редактора.
3. `SongsView.vue` — отсутствие `<keep-alive>` → при возврате SongsTable перемонтируется → `hrQueue` начинается с `[]` (default).

Возможные направления фикса (НЕ делать в этом тикете):
- **A. Не сбрасывать `hrQueue`** при смене страницы/открытии редактора (но тогда при возврате на страницу могут прилетать устаревшие результаты для предыдущей страницы).
- **B. Использовать `<keep-alive>` для `SongsTable`** в `SongsView.vue` (состояние не теряется при возврате).
- **C. Превратить `hrQueue` в `Set` с `Map<songId, pageId>`** — при смене страницы оставлять только id текущей страницы, при возврате — догружать.

### Для Q4 (UI-бейдж количества HR-заданий)

Backend готов:
- `POST /processes/countwaiting` (без фильтра) — есть.
- `GET /api/admin/processes?thread_id=1` (с фильтром по `THREAD_LANE_HEALTH_REPORT`) — есть, но возвращает список, не счётчик.
- **Нужен новый endpoint**: `GET /api/processes/countwaiting?thread_id=1` (добавить фильтр к существующему `getCountWaiting`).

Frontend готов:
- SSE-канал `PROCESS_COUNT_WAITING` уже есть, обновляет `Processes.store.js::countWaiting`.
- Vuex `Processes/store.js::countWaiting` уже подключён к `ProcessWorker.vue`.

**Нужно**: либо (а) расширить backend-эндпоинт `countwaiting` параметром `thread_id`, либо (б) добавить SSE-сообщение `PROCESS_LANE_COUNT` с `threadId`, и отображать отдельный бейдж для HR.

---

## Changelog

- **2026-09 (Pass ?)**: Q1 research by ai-agent (Karaoke). Read-only. Никаких правок кода.