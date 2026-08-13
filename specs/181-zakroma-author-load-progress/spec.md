# Feature Specification: Zakroma — очистка списка + real-time прогресс через NDJSON-стрим

**Feature Branch**: `181-zakroma-author-load-progress`

**Created**: 2026-08-13

**Status**: Draft → в реализации (после уточнения Q5 пользователь выбрал real-time прогресс через backend NDJSON chunked-stream вместо гибрида)

**Input**: User description: "Задача - в закромах при выборе автора нужно сначала очищать уже выведенный список песен предыдущего автора (а то пока грузиться список нового автора старый виден) и процесс загрузки сделать с нормальным прогрессометром с процентами, а не просто спиннер. Если у автора много песен спиннер крутится долго и создаётся ощущение что зависло. Должны идти проценты загрузки."

> **Уточнение (Pass 51)**: «счётчик синтетический — почему? на момент клика по плашке автора в его подписи уже есть количество песен. Это именно то количество, которое мы ждём, и можем точно отображать прогрессометр, не так ли?». Пользователь предпочёл **реальный** прогресс через backend NDJSON chunked-stream (см. Clarifications Q5), а не гибридное число + синтетическую полосу. Это переписывает несколько FR (старый FR-004 «синтетический прогресс» удалён, FR новые).

## Clarifications

### Session 2026-08-13

- Q: Где должен жить таймер инкремента синтетического прогресса? → A: Архитектура пересмотрена (см. Q5) — синтетический таймер больше не нужен, реальный прогресс приходит из backend NDJSON-стрима. Composаble `useZakromaStreamProgress` (без таймера, без `setInterval`) только рендерит состояние стрима (`isVisible`, `progress`, `receivedCount`, `expectedCount`).
- Q: Что должно происходить при повторном клике по тому же уже-выбранному автору? → A: Force refresh, только если прошло > 30 с с последней успешной загрузки этого автора. Защита от случайных дабл-кликов + возможность обновить данные для длительно открытой вкладки.
- Q: Должна ли у прогрессометра быть кнопка «Отмена»? → A: Да, кнопка «Отмена» рядом с прогрессометром. По клику — возврат к сетке тайлов авторов (аналогично `backToAuthors()`), `AbortController.abort()` отменяет fetch для активного стрима.
- Q: Должны ли мы логировать метрики эффективности этой фичи? → A: Агрегировать в `sessionStorage` + батч-отправка в `tbl_events` при `pagehide`. Поля: `eventType`, `author`, `durationMs`, `firstChunkMs` (TTFB первого чанка), `expectedCount`, `receivedCount`, `streamAborted` (true/false).
- Q: Счётчик на тайле автора показывает точное число песен — можно ли использовать его для реального прогресса? → A: **Real-time прогресс через backend NDJSON chunked-stream** (не гибрид). Backend через `StreamingResponseBody` шлёт NDJSON (по одному сообщению на альбом/песню), frontend через `fetch(...).body.getReader()` парсит поток и считает реальный `receivedCount / expectedCount`. Число `expectedCount` берётся из `selectedAuthor.songCount` (на тайле) и используется как знаменатель; число `receivedCount` — из стрима.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Моментальная очистка списка песен при выборе нового автора (Priority: P1)

Сценарий без изменений (см. старую версию спеки): при клике по тайлу нового автора `zakroma` в сторе очищается синхронно (до сетевого запроса), старый список перестаёт отображаться в течение 50 мс.

**Acceptance Scenarios** (без изменений):
1. **Given** посетитель находится на странице «Закрома», список автора X виден,
   **When** клик по тайлу автора Y,
   **Then** `zakroma` очищен до возврата ответа бэка, появился индикатор загрузки.
2. **Given** посетитель зашёл на `/zakroma?author=...`,
   **When** идёт начальная загрузка,
   **Then** индикатор виден сразу, никаких пустых состояний.
3. **Given** посетитель переключается с X на Y, Y загружен,
   **Then** индикатор скрыт, видны только песни Y (никаких «хвостов» от X).

### User Story 2 — Real-time прогресс через NDJSON-стрим с бэкенда (Priority: P1)

Посетитель видит во время загрузки не синтетический «34%», а **точные числа в реальном времени**: «Получено 87 из 234 песен… 37%». Числа приходят с бэкенда по одному сообщению на чанк данных (NDJSON через `StreamingResponseBody`). Это даёт:

- **Точность** — процент = реально полученные / всего (не «средне-потолочное»).
- **Скорость отклика** — первое сообщение приходит через ~200 мс TTFB, посетитель сразу видит «Получено 1 из 234… 0%», потом цифры растут по мере прихода.
- **Доверие** — посетитель видит, что сервер реально работает (счётчик растёт), а не «крутит спиннер наугад».

**Why this priority**: альтернатива (синтетический прогресс) даёт иллюзию контроля без реальной информации. Пользователь прямо попросил real-time — это первичное требование.

**Independent Test**: Открыть `https://sm-karaoke.ru/zakroma`, выбрать автора с ≥ 200 песен (например, «Агата Кристи»), в DevTools Network наблюдать: `Transfer-Encoding: chunked`, `Content-Type: application/x-ndjson`, ответ приходит порциями (несколько чанков по 100-500 мс). На странице: счётчик «Получено X из N» растёт в реальном времени, X = количество полученных песен в стриме (НЕ синтетический таймер).

**Acceptance Scenarios**:

1. **Given** запрос стрима начат,
   **When** приходит первое сообщение NDJSON,
   **Then** UI показывает «Получено 1 из N…», где N = `expectedCount` с тайла (знаменатель).
2. **Given** стрим идёт, получено 87 песен из 234,
   **When** приходит очередной чанк,
   **Then** UI обновляется до «Получено 105 из 234… 45%» (синхронно с событием `ReadableStream.read()`).
3. **Given** стрим завершён (получено `done` сообщение от бэка),
   **When** посетитель всё ещё на странице,
   **Then** индикатор скрывается через 300 мс, таблица с альбомами/песнями отрисована.
4. **Given** запрос стрима завершился ошибкой (5xx/сеть/Aborted),
   **When** ошибка поймана,
   **Then** показывается дружелюбное сообщение с кнопкой «Повторить», индикатор скрыт.
5. **Given** посетитель кликает «Отмена» во время стрима,
   **When** fetch активен,
   **Then** `AbortController.abort()` отменяет запрос, `zakroma` пустой, `selectedAuthor` сброшен (соответствует `backToAuthors()`), счётчик метрик `streamAborted: true`.

### User Story 3 — Быстрые сценарии (без изменений: deduped с прогрессом)

Без изменений (см. старую версию спеки, US3). Уточнение: при «мгновенных» ответах (стрим завершился за < 300 мс) индикатор не показывается — debounce через `setTimeout(300)` отменяется при `done`-сообщении.

---

### Edge Cases

- **Nginx буферизация (prod).** По умолчанию nginx буферизует chunked-ответы до ~4 KB. Без `proxy_buffering off` посетитель получит ВСЕ чанки разом после полной отдачи бэка. Правка в `/etc/nginx/sites-enabled/80to8897` для пути `/api/public/zakroma/stream`: `proxy_buffering off; gzip off; proxy_cache off; proxy_read_timeout 300s;` — иначе фича не работает на проде.
- **CORS.** На проде `sm-karaoke.ru` — same-origin (фронт тоже на этом домене через nginx), CORS не нужен. На localhost dev — vite proxy, тоже ОК.
- **Readуctored stream оборвался посередине** (например, бэк упал с OOM). Frontend получит `TypeError: Failed to fetch` или `AbortError`. Catch в composable → сообщение об ошибке + Retry. Не должно быть «зависшего на 78%» индикатора.
- **Пустой стрим (0 песен после фильтра).** Бэк шлёт `{"type":"done","actualCount":0}` сразу. UI скрывает индикатор, показывает «У этого автора пока нет доступных песен» (или эквивалент).
- **AbortController не закрыт** (посетитель ушёл со страницы во время стрима). Composаble cleanup в `onBeforeUnmount` вызывает `controller.abort()` — fetch оборвётся, ресурсы освободятся.
- **Стрим активен, посетитель переключает автора** (быстрый клик). Composаble останавливает предыдущий стрим (abort), запускает новый. UI сбрасывается на 0.
- **Очень длинный стрим (> 60 сек nginx-таймаут).** Nginx `proxy_read_timeout 300s` решает проблему. Без него nginx оборвёт стрим через 60 сек.
- **Gzip сжатие порвёт NDJSON**. Если nginx сжимает chunked ответы, `\n` может оказаться в середине gzip-чанка. `gzip off;` для пути обязателен.
- **A11y.** Прогрессометр имеет `role="progressbar"`, `aria-valuemin/max/now`, `aria-live="polite"` (озвучивать изменения счётчика). Screen reader не должен говорить при КАЖДОМ чанке (иначе спам) — throttle `aria-live` через `requestAnimationFrame`.
- **`Transfer-Encoding: chunked` не пришёл**. Если первый ответ пришёл БЕЗ chunked header (например, ответ короткий и влезает в один TCP-пакет), `ReadableStream` всё равно работает — парсер NDJSON по `\n` устойчив.
- **Поддержка браузеров.** `ReadableStream` + `getReader()` поддерживается всеми evergreen-браузерами + mobile Safari 10+ (см. caniuse). Никаких полифиллов не нужно.
- **Тёмная тема.** Прогрессометр на CSS-переменных `--km-*`.

## Requirements *(mandatory)*

### Backend (NEW) — NDJSON chunked-stream

- **FR-BE-001**: `PublicApiController` MUST предоставлять новый эндпоинт
  `GET /api/public/zakroma/stream?author=...` (без изменений существующего
  `GET /api/public/zakroma`).
- **FR-BE-002**: Эндпоинт MUST возвращать `Content-Type: application/x-ndjson`
  с `Transfer-Encoding: chunked`, реализован через `StreamingResponseBody`.
- **FR-BE-003**: Формат NDJSON — каждое сообщение на отдельной строке `\n`,
  JSON без вложенных `\n`. **Три типа сообщений**:
  - `{"type":"meta","author":"<name>","expectedCount":234}\n` —
    первое сообщение; `expectedCount` = `Song.loadAuthorSongCounts(...)`
    для этого автора (тот же счётчик, что на тайле — НЕ пересчёт в
    `getZakroma`, т.к. фильтр одинаков).
  - `{"type":"album","album":{...AlbumPublicDto...}}\n` — для каждого
    альбома с метаданными (год, имя, тип, описание), БЕЗ `albumSettings`
    (они придут отдельными сообщениями).
  - `{"type":"song","albumId":"<album-key>","song":{...SongPublicDto...}}\n` —
    для каждой песни. `albumId` = ключ альбома (например, `"1995 — Альбом"`
    или просто индекс), чтобы фронт положил песню в правильный альбом.
  - `{"type":"done","actualCount":234}\n` — финальное сообщение, после
    него стрим закрывается.
- **FR-BE-004**: Итерация MUST идти **по альбомам**: для каждого альбома
  сначала шлём `album` сообщение, потом для каждой песни — `song` сообщения.
  Альбомы перебираются **в порядке их следования в БД** (тот же порядок,
  что в текущем `Zakroma.getZakroma`); песни внутри альбома — в порядке
  `track` (НЕ случайный, иначе UI «прыгает»).
- **FR-BE-005**: После каждого сообщения MUST быть `flush()` на
  `OutputStream` — иначе chunked-ответ задержится в буфере до заполнения
  ~16 КБ (Tomcat default). Без flush стрим не будет «порционным».
- **FR-BE-006**: При ошибке БД (SQLException) MUST быть записано
  `{"type":"error","message":"<user-friendly>"}\n` и стрим закрыт с HTTP
  200 (НЕ 500 — иначе fetch не сможет парсить тело). Для клиента ошибка
  определяется по `type: "error"`.
- **FR-BE-007**: Существующий `GET /api/public/zakroma` MUST остаться
  без изменений (обратная совместимость для других потребителей —
  статистика, telegram-бот, и т.п.). Никаких breaking changes.
- **FR-BE-008**: Метрика `actualCount` (число реально отправленных песен)
  MUST считаться на бэке и совпадать с `albums[*].albumSettings.length`
  на фронте после полного парсинга.

### Frontend / UX

- **FR-FE-001**: `useZakromaStreamProgress` (composable,
  `composables/useZakromaStreamProgress.js`) — инкапсулирует чтение
  NDJSON-стрима. Заменяет ранее запланированный `useZakromaLoadProgress`
  с синтетическим таймером.
  - Экспортирует `isVisible: Ref<boolean>`, `progress: number`
    (receivedCount / expectedCount), `receivedCount: number`,
    `expectedCount: number`, `errorMessage: string|null`, методы
    `start(author, expectedCount)`, `cancel()`.
  - `start()` создаёт `AbortController`, делает `fetch(...)`, читает
    `response.body.getReader()`, парсит NDJSON через `TextDecoder` +
    `split('\n')` + `JSON.parse`.
  - На каждое `album` сообщение — добавление пустого альбома в локальный
    буфер. На каждое `song` сообщение — добавление песни в правильный
    альбом + инкремент `receivedCount`.
  - На `done` сообщение — финал: `progress = 1.0`, через 300 мс
    `isVisible = false`, очистка AbortController.
  - `cancel()` вызывает `controller.abort()`, очищает буфер.
- **FR-FE-002**: Подписка на финальное состояние стрима — composаble
  возвращает Promise `<{albums: ZakromaPublicDto[]}>`, который резолвится
  при `done` сообщении или реджектится при `error`/abort.
- **FR-FE-003**: Action `loadZakroma` в сторе (`zakroma.js`) MUST быть
  заменён на `loadZakromaStream`, который вызывает composable и кладёт
  результат в `state.zakroma` через `commit('setZakroma', parsedAlbums)`
  + `commit('setLastLoadedTimestamp', {author, ts: Date.now()})`.
- **FR-FE-004**: Перед началом стрима (`start()`) composable MUST
  синхронно очистить `state.zakroma` (commit `setZakroma`, []) до
  fetch — посетитель сразу видит пустую область + индикатор, никаких
  «хвостов» от предыдущего автора (FR-001).
- **FR-FE-005**: ZakromaView MUST показывать подпись «Загружаем
  *receivedCount* из *expectedCount* песен автора {author}…» + полосу
  прогресса (`progress = receivedCount / expectedCount`). Если
  `expectedCount === 0` — индикатор не показывается (нет смысла ждать).
- **FR-FE-006**: Кнопка «Отмена» MUST вызывать `composable.cancel()` +
  `backToAuthors()` (аналогично US2 текущей спеки + AbortController).
- **FR-FE-007**: Composаble MUST использовать `AbortController` для
  cleanup в `onBeforeUnmount` — при уходе со страницы fetch оборвётся.
- **FR-FE-008**: Никакой синтетический таймер / `setInterval` — прогресс
  полностью из реальных сообщений бэка.
- **FR-FE-009**: При повторном клике по тому же автору в течение < 30 с
  после успешной загрузки — no-op (без нового fetch). Через > 30 с —
  force refresh: composable.cancel() старого (если был) +
  composable.start() нового.
- **FR-FE-010**: Composаble MUST агрегировать метрики в `sessionStorage`
  (ключ `km_zakroma_stream_metrics`, JSON-массив) + батч-отправка в
  существующую `tbl_events` при `pagehide`. Поля каждой записи:
  `eventType` (`zakroma_stream_start`/`zakroma_stream_done`/
  `zakroma_stream_error`/`zakroma_stream_abort`), `author`,
  `firstChunkMs` (TTFB первого сообщения `meta`), `durationMs`,
  `expectedCount`, `receivedCount`, `streamAborted`, `errorCategory`.
- **FR-FE-011**: Прогрессометр MUST иметь `role="progressbar"`,
  `aria-valuemin/max/now`, `aria-live="polite"` (с throttle через rAF,
  чтобы screen reader не спамил каждым чанком).

### Nginx (prod, NEW)

- **FR-NX-001**: В файле `/etc/nginx/sites-enabled/80to8897` (см. AGENTS.md,
  «nginx 80to8897») для пути `/api/public/zakroma/stream` MUST быть
  добавлен `location`-блок с:
  ```
  proxy_buffering off;
  gzip off;
  proxy_cache off;
  proxy_read_timeout 300s;
  proxy_pass http://karaoke-web-upstream;  # имя upstream как в остальном конфиге
  ```
- **FR-NX-002**: После правки MUST быть выполнено `nginx -t` (проверка
  синтаксиса) И `systemctl reload nginx` — иначе изменения не вступят
  в силу (см. Constitution «Push-ловушка» + AGENTS.md).
- **FR-NX-003**: При deploy через `deploy_web.sh` файл `/root/Karaoke/deploy/80to8897`
  обновляется через rsync, но `/etc/nginx/sites-enabled/80to8897` НЕ
  обновляется автоматически (см. AGENTS.md «nginx 80to8897» — файл,
  не симлинк). Поэтому в этом PR в скрипт `deploy_web.sh` или
  `do.sh build_start_web` MUST быть добавлен ручной шаг копирования
  + reload (или отдельный скрипт `tools/deploy-nginx-stream.sh`).

## Key Entities *(include if feature involves data)*

- **`ZakromaState`** (Vuex state, `store/modules/zakroma.js`):
  - `zakroma: List<ZakromaPublicDto>` — очищается при `start()` до fetch
    (FR-FE-004).
  - `isStreaming: boolean` — флаг активного стрима (новое, вместо
    `isLoading`).
  - **NEW** `streamProgress: { receivedCount: number, expectedCount: number }`
    — для рендера прогрессометра. `expectedCount` фиксируется при
    `start()`, `receivedCount` инкрементируется по каждому `song` сообщению.
  - **NEW** `streamError: string|null` — текст ошибки (если стрим упал).
  - **NEW** `lastLoadedTimestampByAuthor: Record<string, number>` —
    `Date.now()` последней успешной загрузки. Используется для правила
    «no-op, если < 30 с» (FR-FE-009).

- **`useZakromaStreamProgress`** (composable):
  - Экспортирует: `isVisible`, `progress`, `receivedCount`,
    `expectedCount`, `errorMessage`, `start(author, expectedCount)`,
    `cancel()`, Promise с финальным результатом.
  - Внутри: `AbortController`, `fetch()` со stream reader, парсер NDJSON.

- **NDJSON-сообщение** (формат wire protocol):
  - `meta`: `{type: 'meta', author, expectedCount}`
  - `album`: `{type: 'album', album: AlbumPublicDto}`
  - `song`: `{type: 'song', albumId, song: SongPublicDto}`
  - `done`: `{type: 'done', actualCount}`
  - `error`: `{type: 'error', message}`

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: При клике по тайлу нового автора `zakroma` очищается в
  сторе в течение 50 мс (синхронно, до fetch).
- **SC-002**: На стандартном интернете (≥ 5 Мбит/с) для автора с ≥ 200
  песнями первое NDJSON-сообщение (`meta`) приходит за ≤ 500 мс TTFB
  (time-to-first-byte), счётчик `receivedCount` обновляется в UI
  синхронно с парсингом каждого `song`-сообщения (задержка рендера
  ≤ 100 мс).
- **SC-003**: 0 случаев «зависшего спиннера» — при таймауте/5xx
  показывается ошибка с Retry, не крутящийся бесконечно индикатор.
- **SC-004**: Через 7 дней после деплоя в `tbl_events` доступна
  статистика: медиана `firstChunkMs`, распределение
  `expectedCount vs receivedCount` (должны совпадать), `streamAborted`
  rate ≤ 5% (иначе что-то ломается).
- **SC-005**: Сохранение обратной совместимости: `GET /api/public/zakroma`
  БЕЗ `?stream=true` продолжает работать без изменений (для статистики,
  telegram-ботов и т.п.). Только НОВЫЙ путь `/api/public/zakroma/stream`
  использует chunked NDJSON.
- **SC-006**: На проде после правки nginx: `curl -N https://sm-karaoke.ru/api/public/zakroma/stream?author=Аквариум | head -3` показывает как минимум первое NDJSON-сообщение за ≤ 1 сек. `nginx -t` после правки конфига возвращает `syntax is ok`.
- **SC-007**: При нажатии кнопки «Отмена» во время стрима `AbortController.abort()` отменяет запрос за ≤ 100 мс, в DevTools Network видно `(canceled)` статус.

## Assumptions

- Используется **нативный** `fetch().body.getReader()` + `TextDecoder`
  (без полифиллов). Поддержка: все evergreen-браузеры + mobile Safari 10+
  (см. caniuse, 99%+ пользователей). На старых браузерах — graceful
  fallback на обычный `loadZakroma` (без прогресса), но в этой фиче
  не реализуем (примем как TODO если будут жалобы).
- Nginx `proxy_buffering off` + `gzip off` для пути стрима — стандартная
  практика для chunked SSE/NDJSON-эндпоинтов; другого способа
  отдавать ответ «в реальном времени» через nginx нет.
- Фронт использует `AbortController` для отмены fetch — поддерживается
  везде где и `fetch` (с ~2017 года).
- `Zakroma.getZakroma(...)` (karaoke-app) — итерация уже идёт
  альбом→песни в правильном порядке (см. существующий код в
  `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/`),
  надо только обернуть вызовы в «на каждую итерацию → write + flush».
- NDJSON через chunked — рабочий паттерн для streaming JSON, проверен
  в Spring экосистеме (см. `StreamingResponseBody` Javadoc + примеры
  в Spring docs).
- Для **localhost dev** (karaoke-public через vite) nginx-конфиг НЕ
  нужен — vite proxy отдаёт ответ as-is. Правка nginx только для
  прода.

## Out of Scope

- Server-Sent Events (SSE) — overkill для нашего случая.
- WebSocket — нет смысла (одноразовый запрос).
- Поддержка старых браузеров без `ReadableStream` (IE11, etc.).
- Параллельная загрузка нескольких авторов одновременно.
- Skeleton-плейсхолдеры строк таблицы (выводим реальные строки
  по мере прихода — это уже достаточно).
- Persist стрима на бэке между запросами (fire-and-forget).
- Аутентификация стрима (как и обычный `/zakroma`, эндпоинт публичный).

## Implementation Plan (для /speckit.plan)

Эта фича требует **5 коммитов** в ветке `181-zakroma-author-load-progress`:

1. **Commit #1** (✅ уже сделан в этом PR): инфраструктура
   (.specify/extensions.yml + tools/specify-bootstrap.sh + AGENTS.md
   секция «Создание спецификации»).
2. **Commit #2**: backend NDJSON endpoint
   (`PublicApiController.zakromaStream(...)` + `StreamingResponseBody`,
   итерация по `Zakroma.getZakroma` с flush + новый DTO для NDJSON
   wrapper-сообщений).
3. **Commit #3**: nginx config (правка `deploy/80to8897` +
   `tools/deploy-nginx-stream.sh` для ручного копирования на прод).
4. **Commit #4**: frontend streaming layer
   (`useZakromaStreamProgress` composable + новый action
   `loadZakromaStream` в сторе + интеграция в `ZakromaView.vue`).
5. **Commit #5**: cleanup old synchronous code
   (удаление `useZakromaLoadProgress`, старой `loadZakroma`, текста
   «Загрузка...», debounce — не нужен, реальные события приходят
   быстрее 300 мс; проверка CI/линт).
