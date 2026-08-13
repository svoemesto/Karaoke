# Zakroma — real-time прогресс через NDJSON chunked-stream

> **Status**: active
> **Feature Key**: zakroma-stream-progress
> **Last Updated**: 2026-08-13
> **Спека**: [specs/181-zakroma-author-load-progress/spec.md](../../specs/181-zakroma-author-load-progress/spec.md)
> **PR**: `181-zakroma-author-load-progress`

## Что делает

Заменяет синхронную загрузку каталога автора в Закромах (`GET /api/public/zakroma`)
на **chunked NDJSON-стрим** (`GET /api/public/zakroma/stream`), который
отдаёт по одному сообщению на альбом/песню. Фронт парсит поток через
`fetch().body.getReader()` + `TextDecoder` и рендерит **real-time прогрессометр**
«Получено X из N песен автора Y…» синхронно с приходом чанков.

Чистый side-effect: при клике по новому тайлу автора `state.zakroma` очищается
синхронно (≤ 50 мс SC-001), посетитель не видит «хвостов» от предыдущего автора.

## Зачем

До фичи: посетитель кликает по тайлу автора с 234 песнями → 5-10 секунд
крутится неопределённый «спиннер» (или фейковый процент). Нет ощущения
прогресса, создаётся впечатление «зависло».

После фичи: через ~200 мс после клика на экране уже
«Загружаем 1 из 234 песен Агаты Кристи…», цифры растут в реальном времени
по мере прихода чанков с сервера.

## Как работает

### Архитектура

```
┌────────────────────┐                  ┌────────────────────┐
│ ZakromaView.vue    │                  │ PublicApiController│
│ (frontend)         │                  │ (backend, Spring)  │
│                    │                  │                    │
│ onAuthorSelect ────┼── store action ──┼─→ zakromaStream()  │
│                    │                  │   ↓                │
│ useZakromaStream   │                  │ StreamingResponse  │
│ Progress composable│◄── fetch NDJSON──┤ Body (chunked)     │
│  ↓                 │                  │   ↓                │
│ AbortController    │                  │ BufferedWriter     │
│  ↓                 │                  │  + flush() после   │
│ parse JSON line ───┼── handleMessage ──┼─  каждой строки    │
│                    │                  │                    │
│ UI progress meter  │                  │ zakroma.albums[]   │
│ (sticky bar)       │                  │  + albumsongs[]    │
└────────────────────┘                  └────────────────────┘
```

### Wire protocol (FR-BE-003, 5 типов NDJSON-сообщений)

| Type | JSON | Когда |
|---|---|---|
| `meta` | `{"type":"meta","author":"<name>","expectedCount":234}` | Первое сообщение |
| `album` | `{"type":"album","album":{...ZakromaAlbumMetaPublicDto...}}` | На каждый альбом |
| `song` | `{"type":"song","song":{...ZakromaAlbumSongPublicDto...}}` | На каждую песню |
| `done` | `{"type":"done","actualCount":234}` | Финальное |
| `error` | `{"type":"error","message":"..."}` | При SQL/IO исключении |

**Sequential grouping**: `song` принадлежит **последнему** пришедшему `album`
(`albums[albums.length-1].songs.push(msg.song)`). `albumId` в протоколе
**НЕ используется** — упрощает wire protocol на 1 поле и устраняет O(n)
lookup на бэке.

**HTTP 200 даже при ошибке** (FR-BE-006): `{"type":"error","message":"..."}`
+ close, **НЕ 500** — иначе fetch не сможет прочитать тело после разрыва.

### Backend (Kotlin/Spring)

`PublicApiController.zakromaStream(author, anonId, referrer, request)`:

```kotlin
@GetMapping("/zakroma/stream", produces = ["application/x-ndjson"])
fun zakromaStream(...): ResponseEntity<StreamingResponseBody> {
    val body = StreamingResponseBody { out ->
        val writer = BufferedWriter(OutputStreamWriter(out, StandardCharsets.UTF_8))
        // 1. meta — до загрузки данных!
        val expectedCount = Song.loadAuthorSongCounts(author, onlyPublished)[author] ?: 0L
        writer.write(mapper.writeValueAsString(ZakromaStreamMessageDto.meta(author, expectedCount)))
        writer.flush()
        // 2. load zakroma (тот же код, что в /api/public/zakroma)
        val zakroma = Zakroma.getZakroma(...)
        // 3. streaming loop
        for (zak in zakroma) for (album in zak.albums.sorted()) {
            writer.write(...album...); writer.flush()
            for (song in album.albumSongs) {
                writer.write(...song...); writer.flush(); actualCount++
            }
        }
        // 4. done
        writer.write(...done...); writer.flush()
    }
    return ResponseEntity.ok().contentType(MediaType("application", "x-ndjson")).body(body)
}
```

### Frontend (Vue 3 + native Web Streams)

`composables/useZakromaStreamProgress.js`:
- `start(author, expectedCount)` — synchronous clear + fetch + `body.getReader()` loop.
- `cancel()` — `controller.abort()` + clear buffer + reject Promise.
- `cleanup()` — для `onBeforeUnmount` (FR-FE-007).

```js
const reader = response.body.getReader()
const decoder = new TextDecoder('utf-8')
let buffer = ''
while (true) {
  const { done, value } = await reader.read()
  if (done) break
  buffer += decoder.decode(value, { stream: true })
  while ((nlIdx = buffer.indexOf('\n')) !== -1) {
    const line = buffer.slice(0, nlIdx).trim()
    buffer = buffer.slice(nlIdx + 1)
    if (line) handleMessage(JSON.parse(line))
  }
}
```

### Nginx (prod, FR-NX-001)

Без правки nginx chunked-ответ буферизуется (~4 KB) и фронт получит
**все** чанки разом после полной отдачи backend — никакого real-time не будет.

Location-блок добавлен напрямую в [`deploy/web-server-deploy/deploy/80to8897`](../../deploy/web-server-deploy/deploy/80to8897),
внутри HTTPS server-блока (port 443), рядом с существующим `location /api/`:

```nginx
location /api/public/zakroma/stream {
    proxy_buffering off;
    gzip off;
    proxy_cache off;
    proxy_read_timeout 300s;
    proxy_send_timeout 300s;
    proxy_connect_timeout 5s;
    proxy_next_upstream off;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header X-Forwarded-Host $host;
    proxy_set_header X-Forwarded-Port $server_port;
    proxy_set_header Host $host;
    proxy_set_header X-Nginx-Proxy true;
    proxy_pass http://127.0.0.1:8897;
}
```

**Применяется** обычным циклом nginx-конфига: правка `80to8897` в репо → rsync →
ручное `cp /root/Karaoke/deploy/80to8897 /etc/nginx/sites-enabled/80to8897
&& nginx -t && systemctl reload nginx` (см. [AGENTS.md § nginx 80to8897](../../AGENTS.md#Деплой)).

**Раньше** использовался отдельный фрагмент `deploy/80to8897.stream-addition.frag`
+ скрипт `tools/deploy-nginx-stream.sh` (УДАЛЕНЫ в Pass 51-3, 2026-08-13).
Скрипт делал `cat >>` в конец файла — location оказывался ВНЕ server-блока,
`nginx -t` падал с `"location" directive is not allowed here`. Прямое
редактирование `80to8897` (который УЖЕ в репо, см.
`deploy/web-server-deploy/deploy/80to8897`) — единственный надёжный путь.

## Инварианты / правила

1. **`expectedCount` MUST быть идентичной формулой с тайлом** (FR-BE-003).
   Используется `Song.loadAuthorSongCounts(author, onlyPublished)`, **НЕ**
   `zakroma.albums[*].albumSettings.size` после фильтрации (drift).
2. **`albumSettings` НЕ передаются в NDJSON** (out of scope, FR-BE-003).
   Фронт собирает свою статистику альбомов из полученных `song`-сообщений.
3. **`albumId` НЕ используется в протоколе** (sequential grouping).
   Упрощает wire protocol и устраняет O(n) lookup.
4. **HTTP 200 даже при ошибке** (FR-BE-006). `{"type":"error",...}` + close.
   Иначе fetch не сможет парсить тело.
5. **`flush()` после каждого NDJSON-сообщения** (FR-BE-005). Без flush
   chunked-ответ задержится в буфере до ~16 KB (Tomcat default).
6. **AbortController cleanup на `onBeforeUnmount`** (FR-FE-007). Без cleanup
   утечка fetch + Server side продолжает слать данные.
7. **Никакого `setInterval` для синтетического прогресса** (FR-FE-008).
   `setTimeout(300)` для debounce visibility — допустим (это UX, а не
   подсчёт %).
8. **`pagehide` / `visibilitychange` → `sendBeacon`** (FR-FE-010). Если
   `sendBeacon` не справился (payload > 64 KB) — fallback `fetch + keepalive`.
9. **`aria-live` throttle через rAF** (FR-FE-011). Screen reader не должен
   зачитывать 50+ раз/сек при активном стриме.
10. **Старый `GET /api/public/zakroma` БЕЗ ИЗМЕНЕНИЙ** (FR-BE-007, SC-005).
    Другие потребители API (статистика, telegram-боты) не должны сломаться.

## Известные ловушки

### 1. Nginx буферизует по умолчанию

`proxy_buffering on` (default) — посетитель получит все чанки после
полной отдачи backend. **Обязательно** обновить `deploy/web-server-deploy/deploy/80to8897`
и применить на проде (правка → rsync → `cp + nginx -t + systemctl reload nginx`)
после изменения кода endpoint'а.

### 2. Gzip ломает NDJSON

nginx сжимает chunked-ответы — `\n` может оказаться в середине gzip-чанка.
Без `gzip off;` для пути стрима файл становится испорченным.

### 3. `AbortController` не закрыт = утечка

Если посетитель уходит со страницы во время стрима без `controller.abort()`,
Tomcat продолжает писать в OutputStream, пока не дойдёт до конца итерации.
Composаble MUST звать `cleanup()` в `onBeforeUnmount`.

### 4. Дрейф `expectedCount`

Если `expectedCount` в `meta` отличается от числа на тайле автора
(`AuthorTilePublicDto.songCount`) — фронт покажет «получено 87 из 230»,
а подпись тайла «234». Источник: разные формулы (`loadAuthorSongCounts`
vs `zakroma.albums[*].albumSettings.size`). **MUST** использовать одну
формулу.

### 5. `sendBeacon` отправляет синхронно, но не гарантирует доставку

Если страница закрылась до того, как background sendBeacon успел отправить —
событие теряется. Это **expected** (best-effort), но об этом стоит помнить
при анализе SC-004 (метрики могут быть < 100% покрытия).

### 6. На localhost без nginx — всё работает

Vite proxy отдаёт chunked as-is (нет buffering). Это удобно для разработки,
но **ovвerconfidence**: реальный nginx на проде может буферизовать.
Обязательно проверять `nginx -t` + `systemctl reload nginx` после deploy.

### 7. На мобильных Safari < 10 `ReadableStream` недоступен

Реально < 1% посетителей (см. caniuse). **Out of scope** для этой фичи —
если будут жалобы, добавить graceful fallback на синхронный `loadZakroma`.

### 8. `cancel()` через `setZakroma` race condition

Если пользователь быстро кликает «Отмена» → текущий стрим абортится через
`loadZakromaStream({author, expectedCount: 0})` (force-refresh → composable
создаётся новый → done приходит с `actualCount=0` → cancel handler).
В T018 добавить explicit `composable.cancel()` через setup() return.

## Метрики (FR-FE-010)

Каждое событие стрима (`start` / `done` / `error` / `abort`) агрегируется
в `sessionStorage.km_zakroma_stream_metrics` (JSON-массив) и батчем
отправляется в `POST /api/public/zakroma/stream/metrics` при `pagehide`.
Backend регистрирует каждое событие в `tbl_events` (eventType
специализированный, НЕ `CALL_REST`).

Поля:
- `eventType` — `zakroma_stream_start` / `_done` / `_error` / `_abort`.
- `author` — имя автора.
- `firstChunkMs` — TTFB от `start()` до первого `meta` (SC-002 ≤ 500 мс).
- `durationMs` — полное время стрима.
- `expectedCount` — знаменатель из тайла.
- `receivedCount` — фактически полученных песен.
- `streamAborted` — `true` для `_abort`.
- `errorCategory` — текст ошибки (для `_error`).

Через 7 дней после деплоя в `tbl_events` доступна статистика (SC-004):
- median `firstChunkMs` (≤ 500 мс?),
- распределение `expectedCount vs receivedCount` (должны совпадать),
- `streamAborted` rate ≤ 5% (иначе что-то ломается в UX).

## Ссылки

- [specs/181-zakroma-author-load-progress/spec.md](../../specs/181-zakroma-author-load-progress/spec.md)
- [specs/181-zakroma-author-load-progress/plan.md](../../specs/181-zakroma-author-load-progress/plan.md)
- [specs/181-zakroma-author-load-progress/quickstart.md](../../specs/181-zakroma-author-load-progress/quickstart.md)
- [docs/features/special-orders.md](./special-orders.md) — соседняя виртуальная плашка в Закромах
- [docs/features/ci-lint-enforcement.md](./ci-lint-enforcement.md) — линтеры для KDoc/JSDoc
- [CONTRIBUTING.md — § NDJSON Streaming](../../CONTRIBUTING.md) — стиль streaming-эндпоинтов
- [AGENTS.md — § nginx 80to8897](../../AGENTS.md) — обновление nginx-конфига на проде
- [AGENTS.md — § «Тип песни (song_type)»](../../AGENTS.md) — Out of Scope: передавать `songType` в NDJSON
- [AGENTS.md — § «Выполнение после merge»](../../AGENTS.md) — git push и PR
