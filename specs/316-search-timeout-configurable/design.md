# Phase 1 Design: Настраиваемый таймаут между поисковыми запросами (iter #316, rev 3)

**Branch**: `316-search-timeout-configurable` | **Date**: 2026-09-08
**Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Research**: [research.md](./research.md)
**rev 3** (2026-09-08): UI fix + авто-подбор таймаута. SearchTimeoutDialog.vue **УДАЛЁН** — поле таймаута в существующих `<custom-confirm>`. Добавлены FR-009 (мин. интервал) + FR-010 (лог + SSE).

> Имплементационные подсказки для Алины (programmer). Точные строки кода подтверждены grep'ом
> на ветке `316-search-timeout-configurable` (working tree, 0 коммитов). Все ссылки сверены.

---

## 0. Различие с rev 2 (что УБРАТЬ / что ДОБАВИТЬ)

**Убрать**:
- `webvue3/src/components/Songs/SearchTimeoutDialog.vue` — **НЕ создаём** (rev 1-2 отклонён владельцем).
- `isSearchTimeoutVisible` флаг, методы `onSearchTimeoutConfirm`/`onSearchTimeoutCancel`, поля `searchTimeoutDefault` в SongsTable.vue и HomeView.vue.
- `<SearchTimeoutDialog>` в шаблонах SongsTable.vue и HomeView.vue.
- Двухшаговый flow «отдельный диалог → custom-confirm» — заменяем на одношаговый «custom-confirm с timeout полем».

**Добавить**:
- Поле `timeout` в `customConfirmParams.fields` существующих модалок (SongsTable.vue:1289-1314 + HomeView.vue:255-268).
- Backend: замер `minIntervalMs` (FR-009) в обоих путях A и B.
- Backend-лог `[lyrics-search-summary]` после каждого цикла (FR-010.a).
- SSE notification `massSearchSummary` после каждого цикла (FR-010.b).
- Тип `MASS_SEARCH_SUMMARY` — **enum** в `karaoke-app/.../model/SseNotificationType.kt` (УЖЕ существует, broadcast по умолчанию — НЕ в `addressedTypes`).

---

## 1. Backend — `Karaoke.kt` (MODIFY): хранилище

### 1.1 Поле в companion object (rev 3, без изменений vs rev 2)

Файл: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Karaoke.kt`
Класс `Karaoke : Serializable` (**стр. 156**), companion object (`**стр. 157**). Прецедент `checkSearchAsync` (:159-162).

```kotlin
// specs/316-search-timeout-configurable (FR-007): таймаут между поисковыми запросами, сек.
// Хранится в KaraokeProperties (backend, /sm-karaoke/system/Karaoke.properties), default 10.
var lyricsSearchTimeoutSeconds: Int
    get() = KaraokeProperties.getInt("lyricsSearchTimeoutSeconds").takeIf { it >= 1 } ?: 10
    set(value) { KaraokeProperties.set("lyricsSearchTimeoutSeconds", value.coerceAtLeast(1)) }
```

⚠️ **Tasks rev 2/3 согласован** (Кирилл Р-8): T003 уже использует `getInt(...).takeIf { it >= 1 } ?: 10`. Расхождение устранено (2026-09-08).

---

## 2. Backend — `ApiController.kt` (MODIFY): 2 точки внедрения + endpoint + авто-подбор

### 2.1 Путь A — `getSearchSongTextAll` (R-002, серийный + FR-009/FR-010)

Файл: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt`
Метод `getSearchSongTextAll` — **строки 4769-4809**. Цикл `ids.forEach { id -> ... }` (**стр. 4786**).

В **сигнатуру** добавить `@RequestParam(required = false) timeout: Int? = null`. В **начале тела** (после `val resolvedEngine = ...`):

```kotlin
// specs/316-search-timeout-configurable (R-002, FR-007, FR-008): effective timeout =
// query-param или сохранённое значение из KaraokeProperties (default 10, read в момент запуска).
val effectiveTimeout =
    timeout?.coerceAtLeast(1)
        ?: KaraokeProperties.getInt("lyricsSearchTimeoutSeconds").takeIf { it >= 1 } ?: 10

// specs/316-search-timeout-configurable (FR-009, rev 3): замер minIntervalMs
// между двумя подряд успешными getLyricsSearch (упавшие запросы не учитываются).
val cycleStart = System.currentTimeMillis()
var lastSuccessTime: Long? = null
var minIntervalMs: Long? = null
var successfulCount = 0
```

Заменить `ids.forEach { id -> ... }` (**стр. 4786**) на `ids.forEachIndexed { index, id -> ... }`. Внутри цикла, **после успешного** `getLyricsSearch(...)` (return non-null + нет exception) — обновлять замер и паузу:

```kotlin
ids.forEachIndexed { index, id ->
    val song = Song.loadFromDbById(...)
    var songSearchSucceeded = false
    val requestStart = System.currentTimeMillis()
    song?.let {
        if (!song.haveSourceText || ids.size == 1) {
            try {
                getLyricsSearch(...)
                songSearchSucceeded = true
            } catch (e: Exception) {
                // упавший запрос — НЕ учитывается в minIntervalMs (FR-009)
            }
        }
    }
    result = true

    // FR-009 (rev 3): обновить minIntervalMs при успехе.
    if (songSearchSucceeded) {
        if (lastSuccessTime != null) {
            val delta = requestStart - lastSuccessTime!!
            if (minIntervalMs == null || delta < minIntervalMs!!) minIntervalMs = delta
        }
        lastSuccessTime = requestStart
        successfulCount++
    }

    // specs/316-search-timeout-configurable (R-002): пауза между запросами, кроме последнего.
    if (index < ids.size - 1 && effectiveTimeout > 0) {
        Thread.sleep(effectiveTimeout * 1000L)
    }
}
```

**После цикла** — FR-010 (rev 3): backend-лог + SSE notification:

```kotlin
// specs/316-search-timeout-configurable (FR-010, rev 3): backend-лог + SSE notification.
val totalDurationMs = System.currentTimeMillis() - cycleStart
println("[lyrics-search-summary] path=A count=${ids.size} successful=$successfulCount minIntervalMs=${minIntervalMs ?: "null"} totalDurationMs=$totalDurationMs")
sseNotificationService.send(SseNotification(
    SseNotificationType.MASS_SEARCH_SUMMARY,
    mapOf(
        "path" to "A",
        "count" to ids.size,
        "successfulCount" to successfulCount,
        "minIntervalMs" to minIntervalMs,
        "totalDurationMs" to totalDurationMs
    )
))
```

> ⚠️ **`Thread.sleep`, НЕ coroutine delay**: метод не suspend (Spring MVC, Tomcat thread).
> ⚠️ **`KaraokeProperties` доступен напрямую** в ApiController (прецедент checkSearchAsync :160).
> ⚠️ **`SseNotificationService`** уже инжектируется в `ApiController` (стр. 197).

### 2.2 Путь B — `doCreateFromFolder` (R-002a, rate-limit + FR-009/FR-010)

Метод `doCreateFromFolder` — **строки 5204-5343**. Цикл `createdList.forEach { newSong -> ... }` (**стр. 5217**), submit `lyricsSearchExecutor.submit { getLyricsSearch(...) }` (**стр. 5302**). Пул `lyricsSearchExecutor = Executors.newFixedThreadPool(4)` (**стр. 209**).

**В сигнатуру** `doCreateFromFolder` добавить `@RequestParam(required = false) timeout: Int? = null`. В начале выполнения (после `val createdList = importResult.addedSongs`, стр. 5216):

```kotlin
// specs/316-search-timeout-configurable (R-002a, FR-007 путь B): effective timeout = query-param
// или сохранённое значение из KaraokeProperties с fallback chain (Кирилл Р-3, Р-9).
val searchTimeout = timeout?.coerceAtLeast(1)
    ?: KaraokeProperties.getInt("lyricsSearchTimeoutSeconds").takeIf { it >= 1 } ?: 10

// FR-009 (rev 3.1/RC-1, RC-2): замер minIntervalMs для пути B (упавшие запросы не учитываются).
val cycleStart = System.currentTimeMillis()
var lastSuccessTime: Long? = null
var minIntervalMs: Long? = null
var successfulCount = 0
val futures = mutableListOf<Future<*>>() // RC-1: собрать futures, чтобы дождаться до summary.
```

Заменить `createdList.forEach { newSong -> ... }` (**стр. 5217**) на `createdList.forEachIndexed { songIndex, newSong -> ... }`. Перед блоком submit (перед стр. 5302 `lyricsSearchExecutor.submit {`), но **внутри** того же if (`if (!textResolved) { ... }`), добавить замер + паузу **перед** submit, кроме последней итерации:

```kotlin
if (!textResolved) {
    // specs/316-search-timeout-configurable (R-002a): пауза ПЕРЕД submit (кроме последнего).
    if (songIndex < createdList.size - 1 && searchTimeout > 0) {
        Thread.sleep(searchTimeout * 1000L)
    }
    var songSearchSucceeded = false
    val future =
        lyricsSearchExecutor.submit {
            try {
                getLyricsSearch(
                    song = newSong,
                    lyricsFinderService = lyricsFinderService,
                    engine = resolveLyricsSearchEngine(),
                )
                songSearchSucceeded = true
            } catch (e: Exception) {
                // FR-009: упавший — не учитывается в minIntervalMs (путь B логирует свои ошибки).
            } finally {
                // FR-009 (rev 3.1/RC-2): обновить minIntervalMs при успехе (после завершения).
                // Завершения в 4-поточном пуле идут НЕ по порядку submit'ов → метку берём ВНУТРИ
                // synchronized: completedAt монотонен по построению (один поток за раз).
                // Thread-safe: synchronized на отдельном lyricsSearchTimeoutLock (не whole controller,
                // чтобы не блокировать другие endpoints). Production-hardening — out of scope.
                if (songSearchSucceeded) {
                    synchronized(lyricsSearchTimeoutLock) {
                        val completedAt = System.currentTimeMillis()
                        if (lastSuccessTime != null) {
                            val delta = completedAt - lastSuccessTime!!
                            if (minIntervalMs == null || delta < minIntervalMs!!) minIntervalMs = delta
                        }
                        lastSuccessTime = completedAt
                        successfulCount++
                    }
                }
            }
        }
    futures.add(future)
}
```

**После цикла** — RC-1: дождаться всех futures, затем FR-010 (rev 3) аналогично Пути A:

```kotlin
// specs/316-search-timeout-configurable (FR-010, rev 3.1/RC-1): ждём завершения ВСЕХ фоновых
// поисков ПЕРЕД summary — иначе successful≈0, minIntervalMs=null, totalDurationMs=длина submit-цикла.
futures.forEach { it.get() }

// specs/316-search-timeout-configurable (FR-010, rev 3): backend-лог + SSE notification.
val totalDurationMs = System.currentTimeMillis() - cycleStart
println("[lyrics-search-summary] path=B count=${createdList.size} successful=$successfulCount minIntervalMs=${minIntervalMs ?: "null"} totalDurationMs=$totalDurationMs")
sseNotificationService.send(SseNotification(
    SseNotificationType.MASS_SEARCH_SUMMARY,
    mapOf(
        "path" to "B",
        "count" to createdList.size,
        "successfulCount" to successfulCount,
        "minIntervalMs" to minIntervalMs,
        "totalDurationMs" to totalDurationMs
    )
))
```

> ⚠️ **Пул 4 потока НЕ менять** (spec.md:95, out of scope). Rate-limit — на submit-цикле (main-thread), не внутри executor'а.
> ⚠️ **Пауза перед submit** (R-002a), т.к. submit асинхронный; sleep после submit бессмысленно.
> ⚠️ **Thread-safety (rev 3.1/RC-2)**: `synchronized(lyricsSearchTimeoutLock)` — отдельная блокировка, НЕ whole controller (не блокирует другие endpoints). `completedAt` берётся внутри synchronized → монотонен. Production-hardening — out of scope.
> ⚠️ **RC-1**: `futures.forEach { it.get() }` перед summary блокирует HTTP-response до завершения всех поисков — это намеренно, чтобы summary был точным.

### 2.3 Новый endpoint — R-008 (для UI диалога, rev 3 без изменений)

```kotlin
/**
 * specs/316-search-timeout-configurable (R-008, FR-002/FR-004): read/write таймаута
 * для UI-диалога. Значение живёт в KaraokeProperties (backend), НЕ в web-storage.
 */
@GetMapping("/lyrics-search-timeout")
@ResponseBody
fun getLyricsSearchTimeout(): Map<String, Int> =
    mapOf("value" to KaraokeProperties.getInt("lyricsSearchTimeoutSeconds").takeIf { it >= 1 } ?: 10)

@PostMapping("/lyrics-search-timeout")
@ResponseBody
fun setLyricsSearchTimeout(@RequestParam("value") value: Int): Map<String, Any> {
    val validated = if (value >= 1) value else 10
    KaraokeProperties.set("lyricsSearchTimeoutSeconds", validated)
    return mapOf("status" to "ok", "value" to validated)
}
```

### 2.4 `MainController.kt` (MODIFY, deprecated — симметрично)

Метод `getSearchSongTextAll` — **строки 1602-1640**. Уже есть `Thread.sleep(2000)` (**стр. 1626**, хардкод).

Добавить параметр + заменить хардкод:

```kotlin
@PostMapping("/songs/searchsongtextall")
fun getSearchSongTextAll(
    @RequestParam(required = false) txt: String?,
    @RequestParam(required = false) timeout: Int? = null,
    model: Model,
): String {
    val effectiveTimeout =
        timeout?.coerceAtLeast(1)
            ?: KaraokeProperties.getInt("lyricsSearchTimeoutSeconds").takeIf { it >= 1 } ?: 10
    // ... заменить Thread.sleep(2000) на Thread.sleep(effectiveTimeout * 1000L)
}
```

> ⚠️ `MainController` deprecated — НЕ удалять, только параметризовать.

### 2.5 `KaraokeProperties.kt` (MODIFY, B1 fix, rev 3 без изменений)

Регистрация `lyricsSearchTimeoutSeconds` в `listKaraokeProperties` (после `lyricsSearchMinResults` ~стр. 209-210):

```kotlin
KaraokeProperty(key = "lyricsSearchTimeoutSeconds", defaultValue = 10, description = "Таймаут между поисковыми запросами текста, сек (specs/316-search-timeout-configurable)"),
```

---

## 3. Frontend — `Songs/store.js` (MODIFY)

### 3.1 Actions `getLyricsSearchTimeout` / `setLyricsSearchTimeout` (R-008, rev 3 без изменений)

```javascript
// specs/316-search-timeout-configurable (R-008, FR-002/FR-004): read/write таймаута
// через backend-API. НЕ setWebvueProp — значение живёт в KaraokeProperties.
getLyricsSearchTimeout(ctx) {
  return promisedXMLHttpRequest({ method: 'GET', url: '/api/lyrics-search-timeout' })
    .then((r) => {
      const parsed = JSON.parse(r) // promisedXMLHttpRequest возвращает raw string
      const n = parseInt(parsed.value, 10)
      return Number.isInteger(n) && n >= 1 ? n : 10
    })
    .catch(() => 10) // fallback на 10 при ошибке (FR-002)
},
setLyricsSearchTimeout(ctx, value) {
  return promisedXMLHttpRequest({ method: 'POST', url: '/api/lyrics-search-timeout', params: { value: value } })
},
```

### 3.2 `searchTextForAll` — добавить `timeout` в params (R-002, rev 3 без изменений)

```javascript
let params = {
  songsIds: ctx.getters.getSongsDigestIds.join(';'),
  forceResearch: true,
  timeout: <timeoutValue> // новый
}
if (payload && payload.timeout) params.timeout = payload.timeout
if (payload && payload.engine) params.engine = payload.engine
```

### 3.3 `createFromFolderPromise` — добавить `timeout` в params (R-002a, rev 3 без изменений)

```javascript
let params = {
  folder: this.pathToFolder,
  timeout: <timeoutValue> // новый
}
if (payload && payload.timeout) params.timeout = payload.timeout
```

---

## 4. Frontend — `<custom-confirm>` (existing, БЕЗ нового SearchTimeoutDialog.vue)

### 4.1 (rev 3 — ОТМЕНА): `SearchTimeoutDialog.vue` НЕ создаём

**УДАЛИТЬ** из rev 1-2:
- `webvue3/src/components/Songs/SearchTimeoutDialog.vue` (NEW, BModal + input + BButton).
- `<SearchTimeoutDialog>` в шаблонах SongsTable.vue и HomeView.vue (условный `v-if`).
- `isSearchTimeoutVisible` флаг, методы `openSearchTimeoutDialog`/`onSearchTimeoutConfirm`/`onSearchTimeoutCancel`, поля `searchTimeoutDefault`.

### 4.2 Интеграция таймаута в **существующую** модалку «Подтвердите поиск текста» (`SongsTable.vue`)

Файл: `webvue3/src/components/Songs/SongsTable.vue`. Текущий метод `searchTextForAll` (**стр. 1264-1281**) — открывает существующий `custom-confirm` с одним полем `engine`.

**rev 3 — добавляем поле `timeout`** в `customConfirmParams.fields` (рядом с `engine`):

```javascript
async searchTextForAll() {
  const defaultEngine = await this.$store.getters.getPropValue('lyricsSearchEngine')
  const defaultTimeout = await this.$store.dispatch('getLyricsSearchTimeout')
  this.customConfirmParams = {
    header: 'Подтвердите поиск текста',
    body: `Выбрано песен: <strong>${this.countRows}.</strong><br>Найти в Интернете тексты для всех песен, для которых ещё нет текстов? Ранее сохранённые результаты поиска (если есть) будут удалены.`,
    fields: [
      {
        fldName: 'engine',
        fldLabel: 'Движок поиска',
        fldIsSelect: true,
        fldOptions: ['YANDEX_SYNC', 'YANDEX_ASYNC', 'SEARXNG', 'FOURGET'],
        fldValue: defaultEngine || 'FOURGET',
      },
      {
        fldName: 'timeout',
        fldLabel: 'Таймаут (сек)',
        // БЕЗ fldIsSelect/fldIsBoolean/fldIsTextarea → default `<input>` (FR-006 validation на стороне callback).
        fldValue: defaultTimeout || 10,
      },
    ],
    callback: (ret) => {
      const timeout = Number(ret.timeout)
      if (!Number.isInteger(timeout) || timeout < 1) {
        // FR-006: ошибка валидации → заново открыть модалку или показать alert.
        this.customConfirmParams = {
          isAlert: true,
          alertType: 'warning',
          header: 'Ошибка ввода',
          body: 'Таймаут должен быть положительным целым числом ≥ 1.',
          timeout: 10,
        }
        this.isCustomConfirmVisible = true
        return
      }
      this.$store.dispatch('setLyricsSearchTimeout', timeout)
      this.doSearchTextForAll(ret.engine, timeout)
    },
  }
  this.isCustomConfirmVisible = true
}
```

> ⚠️ `<custom-confirm>` (CustomConfirm.vue) уже поддерживает массив `params.fields` с разными типами (boolean/select/textarea/text — default input). Новое поле = default input.
> ⚠️ **Кнопка `@click="searchTextForAll"`** остаётся **как есть** (стр. 423). Внутри метода — вызов `searchTextForAll()` (без параметра) — открывает модалку.
> ⚠️ **НЕ добавлять** `@click="openSearchTimeoutDialog"` (отменено в rev 3).

### 4.3 Интеграция таймаута в **существующую** модалку «Добавление файлов из папки» (`HomeView.vue`)

Файл: `webvue3/src/views/HomeView.vue`. Текущий метод `addFilesFromFolder` (**стр. 246-264**) — открывает существующий `custom-confirm` с информацией о формате.

**rev 3 — добавляем поле `timeout`** в `customConfirmParams.fields`:

```javascript
async addFilesFromFolder() {
  const defaultTimeout = await this.$store.dispatch('getLyricsSearchTimeout')
  this.customConfirmParams = {
    header: 'Добавление файлов из папки',
    body: `Добавить файлы из папки?<br>
           Файлы будут добавлены, если их ещё нет в базе данных<br>
           И имеют формат: <strong>YYYY (NN) [Автор] - Песня.flac</strong>
    `,
    fields: [
      {
        fldName: 'timeout',
        fldLabel: 'Таймаут (сек)',
        fldValue: defaultTimeout || 10,
      },
    ],
    timeout: 10,
    callback: (ret) => {
      const timeout = Number(ret.timeout)
      if (!Number.isInteger(timeout) || timeout < 1) {
        this.customConfirmParams = {
          isAlert: true,
          alertType: 'warning',
          header: 'Ошибка ввода',
          body: 'Таймаут должен быть положительным целым числом ≥ 1.',
          timeout: 10,
        }
        this.isCustomConfirmVisible = true
        return
      }
      this.$store.dispatch('setLyricsSearchTimeout', timeout)
      this.doAddFilesFromFolder(timeout)
    },
  }
  this.isCustomConfirmVisible = true
}
```

> ⚠️ **Кнопка `@click="addFilesFromFolder"`** остаётся **как есть** (стр. 32).

---

## 5. Validation (FR-006)

В обоих UI диалогах (SongsTable.vue §4.2, HomeView.vue §4.3) — callback `Number(ret.timeout) && Number.isInteger(timeout) && timeout >= 1`. Backend дополнительно `coerceAtLeast(1)`. Если невалидно — alert вместо dispatch.

---

## 6. KDoc / JSDoc (Constitution VI)

- `Karaoke.kt:167` — KDoc с описанием `lyricsSearchTimeoutSeconds` (назначение, ссылка на spec.md FR-007).
- `KaraokeProperties.kt` — описание для `lyricsSearchTimeoutSeconds` в `listKaraokeProperties`.
- `ApiController.kt` (2 точки + endpoint) — KDoc с ссылкой на spec.md FR-008, FR-009, FR-010.
- `SongsTable.vue:1289` — JSDoc на `searchTextForAll` (rev 3: timeout поле).
- `HomeView.vue:255` — JSDoc на `addFilesFromFolder` (rev 3: timeout поле).
- `store.js:139-149` — JSDoc на actions `getLyricsSearchTimeout`/`setLyricsSearchTimeout`.
- `SseNotificationService.kt` — НЕ модифицируется (broadcast по умолчанию). `model/SseNotificationType.kt` уже содержит `MASS_SEARCH_SUMMARY`.

---

## 7. 5-step Verification (NON-NEGOTIABLE, канон brief.md:59-63)

После каждого изменения кода (агент):

```bash
# 1. Backend compile: app+web
./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel

# 2. Линтеры: ktlint + npm lint
./gradlew :karaoke-web:ktlintCheck
cd webvue3 && npm run lint && cd ..

# 3. Backend bootJar
./gradlew :karaoke-web:bootJar --parallel
# На nsa-i9 под nsa: также :karaoke-app:bootJar

# 4. Frontend Vite: build + format:check
cd webvue3 && npm run build && npm run format:check && cd ..

# 5. Docker-образы: build_webvue3 (+ build_public если менялся)
cd deploy && bash do.sh build_webvue3
```

> На nsa-i9 под nsa допустим 3-step subset (compile + lint + bootJar) для правок только в `karaoke-app`/`webvue3`. Шаги 2/4/5 для `karaoke-public` — no-op.

---

## 8. Governance (NON-NEGOTIABLE)

- ❌ Агенты НЕ перезапускают контейнер `karaoke-app` (только владелец).
- ❌ Агенты НЕ делают smoke-test через curl против реальной БД (только владелец).
- ❌ Агенты НЕ делают visual verify (только владелец).
- ❌ Агенты НЕ делают deploy (только владелец).
- ❌ Агенты НЕ коммитят, НЕ пушат, НЕ мерджат в master (правило 2026-09-05).
- ✅ Вся работа в working tree до явного «go на финальный коммит» от владельца.

---

## 9. Изменяемые файлы (сводка, rev 3)

```
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
├── Karaoke.kt                                # MODIFY: добавить lyricsSearchTimeoutSeconds (getter+setter)
├── KaraokeProperties.kt                       # MODIFY: регистрация lyricsSearchTimeoutSeconds в listKaraokeProperties
├── controllers/
│   ├── ApiController.kt                      # MODIFY: 3 точки (getSearchSongTextAll + doCreateFromFolder + endpoint) + авто-подбор FR-009/010
│   └── MainController.kt                     # MODIFY: симметрично (deprecated)
└── services/
    └── (SseNotificationService.kt — НЕ MODIFY; broadcast по умолчанию)

webvue3/src/
├── components/Songs/
│   ├── store.js                              # MODIFY: actions getLyricsSearchTimeout/setLyricsSearchTimeout + timeout в searchTextForAll
│   └── SongsTable.vue                        # MODIFY: добавить timeout поле в customConfirmParams.fields (НЕ SearchTimeoutDialog.vue!)
└── views/
    └── HomeView.vue                          # MODIFY: добавить timeout поле в customConfirmParams.fields (НЕ SearchTimeoutDialog.vue!)
    └── App.vue                               # MODIFY (опц.): case SseNotificationType.MASS_SEARCH_SUMMARY в switch SSE

livedocs/
├── INDEX.md                                  # MODIFY: добавить строку-ссылку
└── features/
    └── 316-search-timeout-configurable.md     # NEW: livedoc по конвенции
```

---

## 10. Замечания к tasks.md (важные расхождения, проверить при implement)

1. **T008 — УДАЛИТЬ** (rev 3): больше не создаём `SearchTimeoutDialog.vue`. Вместо этого timeout поле в существующих `customConfirmParams.fields`.
2. **T009 (JSON.parse)** — tasks rev 2 уже согласован (Алина rev 2 использовала `JSON.parse`).
3. **T012, T013** — добавить поле `timeout` в `customConfirmParams.fields`. НЕ `SearchTimeoutDialog.vue`.
4. **T018, T019, T020** (NEW, rev 3) — авто-подбор (FR-009/FR-010): backend `minIntervalMs`, backend-лог, SSE notification.
5. **T020** (NEW, опционально) — `App.vue` case `SseNotificationType.MASS_SEARCH_SUMMARY`.

— Алина (programmer), Кирилл (critic), Марк (reviewer) rev 3 (2026-09-08)
