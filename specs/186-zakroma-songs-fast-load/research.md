# Research: Ускорение загрузки песен в Закромах

**Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Date**: 2026-08-14

## Контекст

Фича про скорость загрузки страницы «Закрома» (karaoke-public). На авторе с 2500 песен пользователь видит ~17 секунд задержки: 5 секунд «пустой паузы» + 12 секунд работы прогрессометра.

Предыдущая фича (spec 181 — `zakroma-author-load-progress`) уже оптимизировала прогрессометр до real-time: `FR-FE-008` явно запрещает `setInterval` для синтетического прогресса. Источник прогресса — `receivedCount.value / expectedCount.value` из NDJSON-стрима. См. [`docs/features/zakroma-stream-progress.md`](../../docs/features/zakroma-stream-progress.md).

Эта фича — продолжение оптимизации: устранить backend N+1 + лишние flush + frontend-баг с тротлингом `setTimeout` в фоновой вкладке.

## Метод

Использовал `codegraph_explore` для:
- `useZakromaStreamProgress` — composable стрима на фронте.
- `loadZakromaStream` — Vuex action.
- `Zakroma.buildFromSongs` — backend построение структуры альбомов.
- `zakromaStream` — backend endpoint.

Прочитал вручную для подтверждения:
- `karaoke-public/src/composables/useZakromaStreamProgress.js` (414 строк).
- `karaoke-public/src/store/modules/zakroma.js` (loadZakromaStream action, 155-226).
- `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt` (zakromaStream, 254-403).
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Zakroma.kt` (buildFromSongs, 96-215).
- `.specify/memory/constitution.md` (принципы II, V, VI).

---

## Узкое место R1: N+1 SQL-запросы в `buildFromSongs`

**Файл**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Zakroma.kt:96-215`

### Текущая реализация

```kotlin
private fun buildFromSongs(songList: List<Song>, ...): List<Zakroma> {
    val songsByAuthor = songList.groupBy { it.author }
    return songsByAuthor.map { (authorName, songsByAuthor) ->
        val zakroma = Zakroma(database)
        // LOOKUP 1: портрет автора (full size)
        zakroma.picture = Pictures.getPictureByName(name = authorName, ...)?.full ?: ""
        // LOOKUP 2: портрет автора (preview)
        val picForAuthorPreview = Pictures.getPictureByName(name = authorName, ..., ignoreUseInList = false)
        zakroma.picturePreviewFileName = picForAuthorPreview?.storageFileNamePreview ?: ""
        // LOOKUP 3: описание/короткое/предупреждение автора
        Author.getAuthorByName(author = authorName, ...)?.let { ... }
        val songsByAlbum = songsByAuthor.groupBy { it.year to it.album }
        zakroma.albums = songsByAlbum.map { (albumKey, songsByAlbum) ->
            // LOOKUP 4: обложка альбома (full size)
            album.picture = Pictures.getPictureByName(name = "$authorName - ${album.year} - $albumName", ...).full
            // LOOKUP 5: обложка альбома (preview)
            val picForPreview = Pictures.getPictureByName(name = pictureName, ..., ignoreUseInList = false)
            // LOOKUP 6: описание/тип/сортировка альбома (если есть albumId)
            songsByAlbum.firstOrNull { it.albumId != null }?.albumId?.let { linkedAlbumId ->
                Album.getAlbumById(id = linkedAlbumId, ...).let { ... }
            }
            ...
        }
    }
}
```

**Подсчёт SQL-запросов** для автора с 30 альбомами:
- На автора: 2 `Pictures.getPictureByName` + 1 `Author.getAuthorByName` = **3 SQL**.
- На каждый из 30 альбомов: 2 `Pictures.getPictureByName` + (если есть `albumId`) 1 `Album.getAlbumById` = **3 SQL × 30 = 90 SQL**.
- **Итого: 93 SQL-запроса** на одну загрузку страницы автора.

Каждый запрос — round-trip к PostgreSQL. При среднем RTT 1мс (localhost) — 93мс. При RTT 5мс (внешняя БД) — **465мс**. Для автора с 50 альбомами — 1500мс (1.5 секунды только на lookup'ы).

### Решение

Добавить batch-методы:

1. **`Pictures.getPicturesByNames(names: List<String>, database, storageService, storageApiClient, ignoreUseInList)`**:
   - `WHERE name IN (?, ?, ..., ?)` — один SQL-запрос.
   - Возвращает `Map<String, Picture>` для O(1) lookup по имени.
   - Учесть параметр `ignoreUseInList` (используется в 3 местах — для автора full, для автора preview с `ignoreUseInList = false`, для альбома full и preview с `ignoreUseInList = false`). На практике для Закромов все 3 вызова используют `ignoreUseInList = false` (см. Zakroma.kt:119, 163). Но метод должен поддерживать оба варианта для обратной совместимости.

2. **`Album.getAlbumsByIds(ids: List<Long>, database, storageService, storageApiClient)`**:
   - `WHERE id IN (?, ?, ..., ?)` — один SQL-запрос.
   - Возвращает `Map<Long, Album>`.

3. **`Author.getAuthorsByNames(names: List<String>, ...)`** (опционально, для единообразия):
   - `WHERE name IN (?, ?, ...)` — один SQL-запрос.
   - Возвращает `Map<String, Author>`.

### Переписать `buildFromSongs`

```kotlin
private fun buildFromSongs(songList: List<Song>, ...): List<Zakroma> {
    val songsByAuthor = songList.groupBy { it.author }
    
    // Собираем ВСЕ нужные имена/id ДО цикла — 1 batch на категорию.
    val authorNames = songsByAuthor.keys.toList()
    val albumKeys = songsByAuthor.flatMap { (author, songs) ->
        songs.map { Triple(author, it.year, it.album) }
    }.distinct()
    val albumLookupNames = albumKeys.map { (author, year, albumName) -> "$author - $year - $albumName" }
    val albumIds = songsByAuthor.values.flatMap { songs ->
        songs.mapNotNull { it.albumId }
    }.distinct()
    
    // BATCH (3 SQL вместо N×3):
    val authorPicturesByName = Pictures.getPicturesByNames(authorNames, ..., ignoreUseInList = false)
    val albumPicturesByName = Pictures.getPicturesByNames(albumLookupNames, ..., ignoreUseInList = false)
    val authorsByName = Author.getAuthorsByNames(authorNames, ...)
    val albumsById = if (albumIds.isNotEmpty()) Album.getAlbumsByIds(albumIds, ...) else emptyMap()
    
    // O(1) lookup в основном цикле — без дополнительных SQL.
    return songsByAuthor.map { (authorName, songs) -> /* строим Zakroma из Map */ }
}
```

### Ожидаемый эффект

| Сценарий | Было | Стало | Выигрыш |
|----------|------|-------|---------|
| Автор с 30 альбомами | 93 SQL | 4 SQL | ×23 |
| Автор с 50 альбомами | 153 SQL | 4 SQL | ×38 |
| Автор с 1 альбомом | 6 SQL | 4 SQL | ×1.5 |

**Ожидаемое сокращение времени backend:** 400-1000мс для крупных авторов.

### Соответствие Constitution

- ✅ **Principle II** (сырой JDBC): batch через `WHERE IN (...)` — стандартный паттерн для JDBC, не вводит ORM.
- ✅ **Principle VI** (Code Standards): новые публичные методы — с KDoc + `@see docs/features/zakroma-stream-progress.md`.

### Альтернативы, отклонённые

1. **JOIN вместо IN**: один SQL-запрос с `JOIN`. Отклонено — усложняет маппинг `ResultSet → Map<String, Picture>`, плюс `Pictures` может вернуть мульти-совпадения (full + preview имеют разные параметры `ignoreUseInList`).
2. **Кеш в памяти** (`ConcurrentHashMap<String, Picture>`): отклонено для v1 — спека не требует (текущая цель — ускорение, а не масштабирование). Backlog (записываем как future improvement в `docs/features/zakroma-stream-progress.md`).
3. **`EXISTS` / предзагрузка триггером**: отклонено — добавляет хрупкую синхронизацию БД ↔ backend.

---

## Узкое место R2: `flush()` после каждой NDJSON-строки

**Файл**: `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt:254-403`

### Текущая реализация

```kotlin
val body = StreamingResponseBody { out ->
    val writer = BufferedWriter(OutputStreamWriter(out, StandardCharsets.UTF_8))
    val mapper = ObjectMapper()
    try {
        // ... meta ...
        writer.write(mapper.writeValueAsString(meta))
        writer.newLine()
        writer.flush()
        out.flush()  // ← явный out.flush() после каждой строки
        
        val zakroma = Zakroma.getZakroma(...)
        
        for (zak in zakroma) {
            for (album in zak.albums.sorted()) {
                writer.write(mapper.writeValueAsString(albumMessage))
                writer.newLine()
                writer.flush()
                out.flush()  // ← flush × N
                for (song in album.albumSongs) {
                    writer.write(mapper.writeValueAsString(songMessage))  // ← Jackson × N
                    writer.newLine()
                    writer.flush()
                    out.flush()  // ← flush × N
                    actualCount++
                }
            }
        }
        // ... done ...
    }
}
```

**Подсчёт операций** для автора с 2500 песнями / 30 альбомами:
- 1 (meta) + 30 (album) + 2500 (song) + 1 (done) = **2532 NDJSON-строки**.
- **5064 flush** (writer + out × 2532).
- **2532 вызова `ObjectMapper.writeValueAsString`** (Jackson — не самый быстрый, ~50-200µs на короткий объект).

Каждый `out.flush()` — системный вызов `OutputStream.flush()` через Tomcat servlet container → kernel `write()` → TCP `send()`. На 5064 flush = 5064 system calls на пустом месте.

### Решение

Собирать сообщения в `StringBuilder` пачками по 50 песен (или по 64 КБ — что наступит раньше), потом один `writer.write` + один `out.flush`.

```kotlin
val body = StreamingResponseBody { out ->
    val writer = BufferedWriter(OutputStreamWriter(out, StandardCharsets.UTF_8))
    val mapper = ObjectMapper()
    try {
        val metaMsg = mapper.writeValueAsString(ZakromaStreamMessageDto.meta(auth, metaExpectedCount))
        writer.write(metaMsg)
        writer.newLine()
        writer.flush()
        out.flush()  // meta — сразу, чтобы фронт мог показать "0 из N"
        
        val zakroma = Zakroma.getZakroma(...)
        
        val buffer = StringBuilder(64 * 1024)  // 64 KB initial capacity
        var bufferedSongCount = 0
        val FLUSH_EVERY_N_SONGS = 50
        
        for (zak in zakroma) {
            for (album in zak.albums.sorted()) {
                // Album message — flush целиком (один на альбом, не батчится с песнями).
                writer.write(mapper.writeValueAsString(ZakromaStreamMessageDto.album(...)))
                writer.newLine()
                // Не flush — копим в writer-буфере (default 8 KB).
                for (song in album.albumSongs) {
                    buffer.append(mapper.writeValueAsString(ZakromaStreamMessageDto.song(...)))
                    buffer.append('\n')
                    bufferedSongCount++
                    actualCount++
                    
                    if (bufferedSongCount >= FLUSH_EVERY_N_SONGS) {
                        writer.write(buffer.toString())
                        buffer.clear()
                        bufferedSongCount = 0
                        writer.flush()
                        out.flush()  // один flush на 50 песен
                    }
                }
            }
        }
        
        // Финальный flush — остаток песен.
        if (bufferedSongCount > 0) {
            writer.write(buffer.toString())
            buffer.clear()
        }
        writer.write(mapper.writeValueAsString(ZakromaStreamMessageDto.done(actualCount)))
        writer.newLine()
        writer.flush()
        out.flush()
    }
}
```

**Подсчёт операций после оптимизации** для 2500 песен:
- 1 (meta) + 30 (album) + 50 (batch flush × 50 песен) + 1 (done) = **82 flush** (вместо 5064). **×62 меньше.**
- Jackson × 2532 → × 2532 (то же самое — не уменьшаем число сериализаций, см. R4).

### Альтернативы, отклонённые

1. **Полная отмена `out.flush()` (положиться на Tomcat auto-flush)**: отклонено — `meta` должна дойти до клиента сразу (фронт показывает «0 из N» в первом кадре). Без `out.flush()` после `meta` клиент может получить весь стрим одним блоком.
2. **Flush после каждого `album` (но не после каждого `song`)**: рассмотрено, отклонено — для автора с 30 альбомами это всё ещё 30 flush × 60 (album + done-блоки) = 1800 flush. Лучше batched по 50.
4. **`Nagle-algorithm off` + `TCP_NODELAY`**: рассмотрено, отклонено для v1 — это низкоуровневая оптимизация на стороне Tomcat/Jetty-конфига, требует изменения `application.yml` + регрессионного тестирования. Backlog (future).

### Ожидаемый эффект

**Ожидаемое сокращение времени backend:** 200-500мс для крупных авторов (за счёт сокращения system calls). Не главный выигрыш, но аддитивный к R1.

### Соответствие Constitution

- ✅ **Principle II**: затрагивает только сериализацию, не вводит ORM.
- ✅ **Principle VI**: код остаётся читаемым с комментариями.
- ⚠️ **Ловушка nginx**: `proxy_buffering off` уже включён в `deploy/80to8897` для `/api/public/zakroma/stream` (см. `docs/features/zakroma-stream-progress.md`). Без этого nginx буферизует ответ и фронт не увидит промежуточных flush'ей.

---

## Узкое место R3: `setTimeout(0)` × N тротлится в фоновой вкладке

**Файл**: `karaoke-public/src/composables/useZakromaStreamProgress.js:139-175`

### Текущая реализация

```javascript
const reader = response.body.getReader()
const decoder = new TextDecoder('utf-8')
let buffer = ''

while (true) {
    const { done, value } = await reader.read()
    if (done) break
    if (firstChunkTs === 0) firstChunkTs = performance.now()
    buffer += decoder.decode(value, { stream: true })
    let nlIdx
    while ((nlIdx = buffer.indexOf('\n')) !== -1) {
        const line = buffer.slice(0, nlIdx).trim()
        buffer = buffer.slice(nlIdx + 1)
        if (!line) continue
        try {
            handleMessage(JSON.parse(line))
        } catch (e) {
            console.warn('NDJSON parse error:', e, line)
        }
        // Micro-yield: между сообщениями даём браузеру шанс
        // отрендерить промежуточное состояние прогрессометра.
        await new Promise((resolve) => setTimeout(resolve, 0))  // ← ПРОБЛЕМА
    }
}
```

**Почему `setTimeout(0)` здесь был выбран** (комментарий 164-171 в исходном коде): дать браузеру тик для рендера промежуточного состояния прогрессометра. Без этого Vue обработает все 2500 сообщений за один synchronous tick и пользователь увидит «0 → 2500» скачком (только финальное состояние).

### Проблема с фоновой вкладкой

**Факт**: Chrome, Edge, Firefox **тротлят `setTimeout`/`setInterval` в скрытых вкладках** до **минимум 1000мс между вызовами** (для экономии батареи). См.:
- [Chrome: Timers in background tabs](https://developer.chrome.com/blog/timer-throttling-in-chrome-88) (минимум 1 минута для цепочек >5 минут).
- Firefox: минимальный интервал 1000мс в фоне.

`fetch().body.getReader()` — это **native I/O**, оно НЕ тротлится. То есть браузер продолжает получать байты от сервера в фоне. Но обработка каждой строки через `await setTimeout(0)` будет ждать 1000мс.

**Расчёт для автора с 2500 песен в фоне:**
- 2500 сообщений × 1000мс = **2 500 000мс = 41 минута** обработки в фоне.
- Когда пользователь возвращается через 30 секунд: обработано ~30 сообщений → `receivedCount ≈ 30` → прогрессометр показывает «30 из 2500» (1.2%).
- С точки зрения пользователя: «прогрессометр не сдвигается, хотя я вернулся через минуту».

Это и есть баг, описанный пользователем.

### Решение

**Вариант A**: Заменить `setTimeout(0)` на microtask `Promise.resolve().then(resolve)` (НЕ тротлится в фоне, даёт event-loop tick).

```javascript
// Было:
await new Promise((resolve) => setTimeout(resolve, 0))

// Стало:
await Promise.resolve()  // microtask — НЕ тротлится, но даёт меньше времени на рендер
```

**Минус A**: на 2500 сообщений в активной вкладке Vue всё ещё может не успевать рендерить (микротаски выполняются до macrotask'ов, включая рендер). Нужно проверить, что `nextTick()` достаточно.

**Вариант B**: рендерить **пачками по N сообщений** (например, по 50), yield только между пачками. С backend-пачкой (R2) и frontend-пачкой → 50 yields вместо 2500.

```javascript
let batchCount = 0
const BATCH_FLUSH = 50
while ((nlIdx = buffer.indexOf('\n')) !== -1) {
    // ... обработка сообщения ...
    batchCount++
    if (batchCount >= BATCH_FLUSH) {
        batchCount = 0
        // Один yield на 50 сообщений — не тротлится в фоне (50мс в худшем случае),
        // даёт Vue время отрендерить.
        await new Promise((resolve) => setTimeout(resolve, 0))
    }
}
```

**Минус B**: всё ещё использует `setTimeout`, но только 50 раз вместо 2500. В фоне: 50 × 1000мс = 50 секунд (вместо 41 минуты). Лучше, но не идеально.

**Вариант C** (рекомендуемый): **комбинация A + B + visibilitychange listener**.

```javascript
// 1. Микрозадача вместо setTimeout — НЕ тротлится.
async function yieldToBrowser() {
    return new Promise((resolve) => {
        // queueMicrotask — гарантированно не тротлится.
        if (typeof queueMicrotask === 'function') {
            queueMicrotask(resolve)
        } else {
            Promise.resolve().then(resolve)
        }
    })
}

// 2. Visibility listener — при возврате на вкладку принудительно
//    проталкиваем накопленные данные.
let pendingVisibilityPush = false
document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible' && pendingVisibilityPush) {
        // Принудительно отдать Vue tick для рендера накопленных данных.
        nextTick().then(() => {
            pendingVisibilityPush = false
        })
    }
})

// 3. В цикле обработки:
while ((nlIdx = buffer.indexOf('\n')) !== -1) {
    // ... обработка ...
    if (document.visibilityState === 'hidden') {
        // В фоне — НЕ yield'им, копим в albums.value пачкой.
        // yield будет только когда вернёмся.
        pendingVisibilityPush = true
    } else {
        // В активной вкладке — yield каждый N сообщений для плавности.
        batchCount++
        if (batchCount >= BATCH_FLUSH) {
            batchCount = 0
            await yieldToBrowser()
        }
    }
}
```

### Ожидаемый эффект

| Сценарий | Было | Стало (вариант C) |
|----------|------|-------------------|
| Активная вкладка, 2500 песен | 2500 yields × ~1мс = ~2.5 сек | 50 yields × ~1мс = 50мс |
| Фоновая вкладка, 30 сек | 30 сообщений обработано (1.2%) | ~Все 2500 обработаны (т.к. нет yield'ов) |
| Возврат на вкладку после 30 сек в фоне | Прогрессометр показывает 1.2% | `nextTick()` через `visibilitychange` проталкивает state — прогрессометр показывает 100% (или реальное значение) |

### Соответствие Constitution

- ✅ **Principle VI** (Code Standards): код остаётся читаемым; добавляется комментарий о причине `queueMicrotask` vs `setTimeout`.
- ✅ **Принцип V** (двух-фронтенд): изменения только в `karaoke-public`.

### Альтернативы, отклонённые

1. **WebSocket вместо NDJSON**: отклонено — текущий контракт стрима работает, переход на WebSocket требует изменений nginx-конфига (Upgrade headers) и не даёт пропорционального выигрыша. Backlog.
2. **Server-Sent Events (SSE) вместо NDJSON**: рассмотрено, отклонено — `EventSource` имеет свои тротлинг-проблемы в фоне (браузер может закрывать соединение). NDJSON + fetch — проще и работает.
3. **`IntersectionObserver` на прогрессометре**: не применимо — observer следит за видимостью DOM, а не за активностью вкладки.

---

## Узкое место R4: `ObjectMapper.writeValueAsString` × N

**Файл**: `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt:340-365`

### Текущая реализация

`mapper.writeValueAsString(songMessage)` для КАЖДОЙ песни. На 2500 песен = 2500 вызовов Jackson.

### Анализ

- Jackson `ObjectMapper` — тяжёлый (~50KB на каждый инстанс), `writeValueAsString` создаёт промежуточный `StringWriter` (~8KB на короткий объект).
- Каждый вызов — ~50-200µs (зависит от JVM warmup).
- 2500 × 100µs ≈ 250мс только на сериализацию.

### Решение (в рамках R2)

В R2 `writeValueAsString` остаётся, но **один раз на пачку**: вместо `mapper.writeValueAsString(songMessage)` × 50 → `mapper.writeValueAsString(listOf(songMsg1, songMsg2, ..., songMsg50))` × 1.

**Но** это меняет контракт — клиент ожидает **одна NDJSON-строка = одно сообщение**. Решение: писать пачку как 50 NDJSON-строк через `mapper.writeValueAsString(map)` + `\n` join, или использовать `mapper.writer().writeValues(out)` с `JsonGenerator`.

**Проще**: использовать `ObjectMapper.writer().writeValues(out)` с массивом + цикл по элементам. Или ещё проще — оставить `writeValueAsString` × N, но с учётом, что R2 уже сокращает flush × 62. Jackson × 2500 — не главный bottleneck (250мс из ~3000мс общего времени backend).

**Решение для v1**: оставить как есть (Jackson × N внутри пачки), фокус на R2 (batched flush). Замерить — если backend всё ещё > 1.5 сек на 2500 песен, тогда оптимизировать Jackson отдельно.

### Ожидаемый эффект

Зависит от замеров. Ожидаемо: не более 100-200мс на Jackson × 2500 (после R1+R2 backend ≈ 1-1.5 сек, из которых Jackson — 10-15%). Если больше — выделить в отдельную задачу.

### Соответствие Constitution

- ✅ Не нарушает никаких принципов.

### Альтернатива (если потребуется)

- **`ObjectMapper` с включённым `SerializationFeature.CLOSE_CLOSEABLE`** + `OutputStream` напрямую: писать JSON в `ByteArrayOutputStream` → `out.toString("UTF-8")`. Снижает аллокации. Backlog.

---

## Узкое место R5: 5-секундная пауза перед началом

### Анализ

Из исследования: текущий код `zakromaStream()` **уже** отправляет `meta` сразу с `expectedCount` (если фронт передал параметр) **ДО** вызова `Zakroma.getZakroma()`:

```kotlin
val metaExpectedCount: Long =
    if (expectedCount != null && expectedCount > 0) {
        expectedCount  // ← trust фронта, без DB count
    } else {
        Song.loadAuthorSongCounts(...)[auth] ?: 0L  // ← fallback (100-500мс)
    }
writer.write(mapper.writeValueAsString(ZakromaStreamMessageDto.meta(auth, metaExpectedCount)))
// ↑ отправляется ДО Zakroma.getZakroma()
```

Фронт (`useZakromaStreamProgress.js:107-109`) передаёт `expectedCount` с тайла `AuthorTilePublicDto.songCount`.

**Подсчёт**: для автора с 2500 песен, чей тайл был загружен ранее (`authors-tiles`) — `expectedCount=2500`, meta шлётся мгновенно. **5-секундная пауза НЕ объясняется этим кодом**.

**Гипотеза** (требует подтверждения замерами на проде):
1. **`Zakroma.getZakroma()` сам по себе занимает 3-5 секунд** из-за N+1 SQL (R1) — 93 запроса × RTT.
2. **Первый `out.flush()` после `meta` не доходит до фронта** из-за nginx `proxy_buffering` (уже решено в спеке 181, FR-BE-006).
3. **DNS/connection setup** для первого запроса к новому хосту — но это разовое, не должно быть 5 сек.

**Самая вероятная причина**: гипотеза 1 (N+1). После R1 backend должен уложиться в 1-2 сек на 2500 песен.

### Решение

Решается **автоматически** как побочный эффект R1. Отдельных действий не требуется.

### Проверка

После имплементации R1+R3 — замерить:
- Время до первого flush (meta).
- Время до `done` от сервера.
- Время до полной отрисовки во фронте (через `performance.now()` в `useZakromaStreamProgress.js:start()` + `zakroma.js:loadZakromaStream()`).

Если 5-секундная пауза остаётся — открыть отдельную задачу с замерами.

---

## Сводка решений

| # | Узкое место | Решение | Файлы | Сложность | Эффект |
|---|-------------|---------|-------|-----------|--------|
| R1 | N+1 SQL | Batch `getPicturesByNames` + `getAlbumsByIds` | `Pictures.kt`, `Album.kt`, `Zakroma.kt` | M | ×23 быстрее на 30 альбомах |
| R2 | Flush × N | Batched flush по 50 песен | `PublicApiController.kt` | S | ×62 меньше flush |
| R3 | setTimeout тротлится в фоне | queueMicrotask + visibilitychange | `useZakromaStreamProgress.js` | M | Баг с переключением вкладка исправлен |
| R4 | Jackson × N | Оставить для v1 (аддитивно к R2) | — | none | Замерить post-R1+R2 |
| R5 | 5-сек пауза | Решается побочно через R1 | — | none | Автоматически |

**Приоритет реализации**: R1 (главный выигрыш) → R3 (фич бага) → R2 (вспомогательный). R4 — только если замеры покажут необходимость.

**Ни одно решение НЕ нарушает контракт NDJSON-стрима** (5 типов сообщений `meta`/`album`/`song`/`done`/`error` остаются). Меняется только **ритмика отправки**.

## Ссылки

- [Спека 181 — zakroma-author-load-progress](../181-zakroma-author-load-progress/spec.md) — предыдущая фича, оптимизировавшая прогрессометр до real-time.
- [docs/features/zakroma-stream-progress.md](../../docs/features/zakroma-stream-progress.md) — per-feature документ (обновляется в этом PR).
- [.specify/memory/constitution.md](../../.specify/memory/constitution.md) — принципы II (сырой JDBC), V (двух-фронтенд), VI (Code Standards).
- [Chrome: Timers throttling in background tabs](https://developer.chrome.com/blog/timer-throttling-in-chrome-88) — почему `setTimeout(0)` тротлится в фоне.