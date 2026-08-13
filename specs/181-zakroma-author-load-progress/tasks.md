---
description: "Task list for 181-zakroma-author-load-progress (real-time NDJSON-stream progress)"
---

# Tasks: 181-zakroma-author-load-progress

**Input**: Design documents from `/specs/181-zakroma-author-load-progress/`
- `spec.md` (User Stories US1-US3, FR-BE-*, FR-FE-*, FR-NX-*, SC-001..007)
- `plan.md` (технический контекст, Constitution Check, структура файлов, риски)
- `quickstart.md` (ручные сценарии 1-10)

**Prerequisites**: ✅ plan.md (required), ✅ spec.md, ✅ quickstart.md.

**Tests**: В CI тестов нет (`constitution.md` § Тесты + AGENTS.md, «Тесты»).
Тесты = ручная проверка по `quickstart.md` (сценарии 1-10). Тестовые задачи
не выделены; вместо них — manual verification tasks на каждом чекпоинте.

**Organization**: задачи сгруппированы по user story (US1-US3) для
независимой реализации и тестирования. US1 (очистка) и US2 (real progress)
— обе P1, идут последовательно (backend endpoint строится сразу с стримом).
US3 (debounce + 30s rule) — P2, идёт после US2.

**Commit grouping**: задачи сгруппированы в **6 коммитов** (зафиксировано
в `spec.md` § Implementation Plan; обновлено в связи с I5):
- ✅ #1: инфраструктура (T001).
- ✅ #2: спека (T002).
- ✅ #3: plan + quickstart (T003).
- ⏳ #4: backend DTOs + endpoint + nginx config (T004, T005, T006, T007, T008, T009, T010, T011 — backend spring endpoint + nginx).
- ⏳ #5: frontend streaming layer (T012, T013, T014 — backend loop + frontend parser + UI прогресс; T015 manual verify).
- ⏳ #6: docs + cleanup + lint + PR (T016-T023 docs/code, T024-T025 manual, T026-T027 git).

**Примечание**: T011, T015, T019, T025 — это **manual verification**
checkpoint'ы (без них PR нельзя мерджить). Все остальные T004-T027 — это
commit-content.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: можно запускать параллельно (разные файлы, нет зависимостей).
- **[Story]**: к какой user story относится (US1, US2, US3).
- В описании — точные пути к файлам.

---

## Phase 1: Setup (Shared Infrastructure)

- [x] **T001** ✅ `chore(spec)`: `.specify/extensions.yml` +
  `tools/specify-bootstrap.sh` + AGENTS.md секция «Создание спецификации».
  Commit `b7481ba9`. (Tools for automated branch creation per
  Constitution NON-NEGOTIABLE.)
- [x] **T002** ✅ `spec(181)`: `specs/181-zakroma-author-load-progress/spec.md`
  + `checklists/requirements.md` (full rewrite for real-stream instead
  of hybrid). Commit `443cd959`.
- [x] **T003** ✅ `plan(181)`: `specs/181-zakroma-author-load-progress/plan.md`
  + `quickstart.md`. Commit `3c38084c`.

**Checkpoint**: foundation spec/plan документы готовы. Переходим к Phase 2.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Backend DTOs + nginx config — оба блокирующие для US1/US2,
без них ничего не работает. Параллельная работа (разные файлы).

- [x] **T004** [P] ✅ Создан
  `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/ZakromaAlbumMetaPublicDto.kt` —
  лёгкий DTO метаданных альбома БЕЗ `albumSettings`. ~55 строк. KDoc +
  `@see docs/features/zakroma-stream-progress.md`. `fromAlbum()` companion
  для конверсии `ZakromaAlbum → ZakromaAlbumMetaPublicDto`.
- [x] **T005** [P] ✅ Создан
  `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/ZakromaStreamMessageDto.kt` —
  NDJSON-wrapper. `@JsonInclude(NON_NULL)`, 5 типов сообщений, companion
  factories `meta()/album()/song()/done()/error()`. ~70 строк. KDoc + `@see`.
- [x] **T006** [P] ✅ Создан фрагмент + деплой-скрипт (адаптация):
  - `deploy/80to8897.stream-addition.frag` — готовый location-блок для
    вставки в `/etc/nginx/sites-enabled/80to8897` на проде (с комментариями).
  - `tools/deploy-nginx-stream.sh` — скрипт: rsync фрагмента → SSH →
    prepend content в `80to8897` (если ещё не добавлен) → `nginx -t` →
    `systemctl reload nginx`. С бэкапом и автоматическим откатом при
    ошибке nginx -t.
  - **Отклонение от T006**: полный `deploy/80to8897` не в репо (только на
    сервере, см. AGENTS.md «nginx 80to8897»). Поэтому фрагмент-подход —
    копируется на сервер и конкатенируется в существующий конфиг.
      1. Прочитать существующий `deploy/80to8897` — найти upstream name
         (например, `karaoke-web-upstream`) и существующий `/api/public/`
         location (он задаёт `proxy_set_header` директивы, которые надо
         скопировать).
      2. Создать новый `location /api/public/zakroma/stream { ... }`:
         - `proxy_buffering off;`
         - `gzip off;`
         - `proxy_cache off;`
         - `proxy_read_timeout 300s;`
         - `proxy_set_header Host $host;`
         - `proxy_set_header X-Real-IP $remote_addr;`
         - `proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;`
         - `proxy_set_header X-Forwarded-Proto $scheme;`
         - `proxy_pass http://<upstream-name>;` (то же имя, что в существующем).
      3. Сохранить файл (`/etc/nginx/sites-enabled/80to8897` — **отдельный
         файл, не симлинк**, см. AGENTS.md «nginx 80to8897»).
      4. ~10 строк. **НЕ** применяется автоматически — применяется
         через `tools/deploy-nginx-stream.sh` (T026).

**Checkpoint**: DTO и nginx шаблон готовы. Можно приступать к Phase 3 (backend endpoint).

---

## Phase 3: User Story 1 — Очистка + базовый стрим (Priority: P1) 🎯 MVP

**Goal**: при клике по тайлу автора `state.zakroma` очищается, индикатор
загрузки появляется синхронно, новый endpoint `/api/public/zakroma/stream`
открывается и стримит первые сообщения. Real progress-часть — в Phase 4.

**Independent Test**: scenario 1 из `quickstart.md` (только проверка очистки
+ появления индикатора). Backend compile + первый chunked-ответ через curl.

- [x] **T007** ✅ [US1] Добавлен endpoint `zakromaStream` в
  `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt`:
  - Сигнатура: `fun zakromaStream(author: String?, anonId: String?, referrer: String?, request: HttpServletRequest): ResponseEntity<StreamingResponseBody>`.
  - `@GetMapping("/zakroma/stream", produces = ["application/x-ndjson"])`.
  - Внутри тела (`StreamingResponseBody` lambda):
    1. `BufferedWriter(OutputStreamWriter(out, StandardCharsets.UTF_8))`.
    2. Регистрация события `doRegisterEvent` (`CALL_REST` + `ZAKROMA` + `stream: true`).
    3. `onlyPublished = onlyPublishedFor(request)` (тот же код что в существующем endpoint).
    4. `meta` сообщение с `expectedCount = Song.loadAuthorSongCounts(author, onlyPublished)` — **MUST** быть идентичной формулой с тайлом (FR-BE-003).
    5. **Заглушка отменена**: streaming loop по `zak.albums.sorted() → album → albumSongs` с
       `flush()` после каждого NDJSON-сообщения. Полная реализация T012 уже включена.
    6. `done` сообщение с `actualCount`.
    7. `try/catch` вокруг всей стрим-логики → `{"type":"error",...}` + close, HTTP 200 (FR-BE-006).
  - KDoc endpoint с `@see docs/features/zakroma-stream-progress.md`.
  - Возврат: `ResponseEntity.ok().contentType(MediaType("application", "x-ndjson")).body(body)`.
  - **Verified**: `./gradlew :karaoke-web:compileKotlin` ✅ BUILD SUCCESSFUL.
- [x] **T008** ✅ [US1] Создан
  `karaoke-public/src/composables/useZakromaStreamProgress.js` — composable-каркас:
  - `setup()` возвращает: refs `isVisible`, `progress` (=0),
    `receivedCount` (=0), `expectedCount` (=0), `errorMessage`
    (ref<string|null>); method `start(author, expectedCount)`,
    `cancel()`, `result` Promise.
  - Внутри `setup()` — `let controller = null`, `let cleanup = () => {}`.
  - `start()`: **синхронно** очищается локальный буфер + refs (FR-FE-004 ≤ 50 мс).
    Создаётся `AbortController`, fetch запускается к `/api/public/zakroma/stream?author=...`.
    Полный NDJSON-парсер добавляется в T013.
  - `cancel()` — `controller.abort()`, очищает буфер, reject Promise.
  - JSDoc + `@see docs/features/zakroma-stream-progress.md`.
  - **Verified**: ESLint ✅ no warnings.
- [x] **T009** ✅ [US1] `karaoke-public/src/store/modules/zakroma.js`:
  - `loadZakroma` action заменена на `loadZakromaStream({ author, expectedCount })`.
  - State: `isLoading` → `isStreaming` ✅. **NEW** `streamProgress`, `streamError`,
    `lastLoadedTimestampByAuthor`.
  - Mutations: `setZakroma`, `setStreaming`, `setStreamProgress`, `setStreamError`,
    `setLastLoadedTimestamp`.
  - FR-FE-009 dedup: `lastTs && Date.now() - lastTs < 30_000` → no-op.
  - При error — сбрасывает `lastLoadedTimestamp[author] = 0` (force refresh на retry).
  - **Verified**: ESLint ✅.
- [x] **T010** ✅ [US1] `karaoke-public/src/views/ZakromaView.vue`:
  - `mapGetters` обновлён: `isLoading` → `isStreaming`, добавлены `streamProgress`, `streamError`.
  - `mapActions`: `loadZakroma` → `loadZakromaStream`.
  - `onAuthorSelect`: берёт `expectedCount` из `authorTiles.find(t => t.author === author)?.songCount`.
  - Placeholder: `<div v-if="...isStreaming">{receivedCount}/{expectedCount}</div>`.
  - Retry button через `streamError` → `retryLoadZakroma()`.
  - `isLoadingAny` обновлён на `isStreaming`.
  - **Verified**: ESLint ✅.
- [ ] **T011** [US1] **Manual Verification (Phase 3)** ⚠️ Требует пользователя:
  - Backend: `./gradlew :karaoke-web:compileKotlin` ✅ DONE (8/8 elapsed).
  - Deploy: пользователь запускает `bash deploy/do.sh build_start_web`
    + `bash tools/deploy-nginx-stream.sh`.
  - Localhost: `curl -N "http://localhost:8897/api/public/zakroma/stream?author=Test"` —
    должен вернуть как минимум 2 строки NDJSON (`meta` + `done`).
  - Browser: `/zakroma` → клик по автору → placeholder «Loading...» синхронно.

**Checkpoint**: после Phase 3 очистка списка работает синхронно, endpoint
отдаёт NDJSON, UI показывает placeholder. End-to-end пока без real
progress (он появится в Phase 4).

---

## Phase 4: User Story 2 — Real-time прогресс (Priority: P1) 🎯 MVP

**Goal**: NDJSON-парсер в composable + стриминг loop в backend — посетитель
видит реальный счётчик «получено X из N».

**Independent Test**: scenarios 1, 2, 5 из `quickstart.md` (real progress,
отмена, ошибка сети).

- [x] **T012** ✅ [US2] В `PublicApiController.zakromaStream(...)` (расширение T007 — **уже сделано в T007**, см. выше):
  - Streaming loop по `zakroma` → `albums.sorted()` → для каждого альбома:
    1. `album` сообщение с `ZakromaAlbumMetaPublicDto.fromAlbum(album)` + flush.
    2. По `album.albumSongs` — `song` сообщение с `ZakromaAlbumSongPublicDto(...)` + flush + `actualCount++`.
  - `done` сообщение с `actualCount` (FR-BE-003 + FR-BE-008).
  - `try/catch` → `{"type":"error",...}` + close, HTTP 200 (FR-BE-006).
  - **Verified**: `./gradlew :karaoke-web:compileKotlin` ✅.
  - В streaming loop: после `meta` сообщения итерировать по `zakroma`
    → `albums` → для каждого альбома:
    1. Написать `{"type":"album","album":<meta-dto>}` + `\n` + `flush()`.
    2. Итерировать по `album.albumSongs` — для каждой песни
       написать `{"type":"song","song":<dto>}` + `\n` + `flush()`,
       инкрементить `actualCount` (использовать `ZakromaAlbumSongPublicDto.fromAlbumSong(...)`
       — добавить этот companion-метод если отсутствует, проверить
       `ZakromaPublicDto.kt:103-191`).
    3. `ZakromaAlbumPublicDto` (для `albumTypeCounts` сводки) сейчас
       не нужен в NDJSON — фронт соберёт `albumTypeCounts` сам из
       своих данных (или это вычислим позже, см. ниже).
  - В конце цикла — `done` сообщение с `actualCount` (как уже было
    в T007).
  - Обработка ошибок: `try/catch` вокруг всей стрим-логики, при
    исключении — написать `{"type":"error","message":"<user-friendly>"}`
    + close, **НЕ** отдавать 500 (иначе fetch не сможет парсить тело).
  - ~80 строк (включая KDoc).
- [ ] **T012-b** [US2] Добавить endpoint `POST /api/public/zakroma/stream/metrics`
      в `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt`:
  - Сигнатура: `@PostMapping("/zakroma/stream/metrics") fun zakromaStreamMetrics(@RequestBody metrics: List<ZakromaStreamMetricDto>): ResponseEntity<Unit>`.
  - На каждый элемент списка — `doRegisterEvent(mapOf("eventType" to eventType, "parameters" to mapOf("author", "firstChunkMs", "durationMs", ...)))`.
  - `eventType` = конкретный тип (`zakroma_stream_start` / `_done` / `_error` / `_abort`),
    НЕ `CALL_REST`.
  - Возврат `ResponseEntity.ok().build()` — даже если что-то упало в
    БД (метрики не должны ломать UX).
  - **Совместимо с `sendBeacon`** — endpoint принимает JSON
    `Content-Type: application/json` (который шлёт `sendBeacon`).
  - KDoc + `@see docs/features/zakroma-stream-progress.md`.
  - ~50 строк.
- [ ] **T012-c** [US2] Создать
      `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/ZakromaStreamMetricDto.kt`:
  - Поля: `eventType: String`, `author: String`, `firstChunkMs: Long?`,
    `durationMs: Long?`, `expectedCount: Long?`, `receivedCount: Long?`,
    `streamAborted: Boolean` (= false default),
    `errorCategory: String?`.
  - KDoc + `@see`.
- [ ] **T013** [US2] В `composables/useZakromaStreamProgress.js`
  (расширение T008):
  - В `start()` после создания `controller`:
    1. `fetch('/api/public/zakroma/stream?author=' + encodeURIComponent(author), { signal: controller.signal })`.
    2. Получить `response.body.getReader()`, создать `TextDecoder('utf-8')`.
    3. Loop: `const { done, value } = await reader.read()`. Если `done` — выйти.
    4. Декодировать чанк: `const chunk = decoder.decode(value, { stream: true })`.
    5. Split по `\n`, для каждой непустой строки:
       - `const msg = JSON.parse(line)`.
       - По `msg.type` диспатчить: `meta` → `expectedCount.value = msg.expectedCount`;
         `album` → локальный буфер `albums.value.push({ ...msg.album, songs: [] })`;
         `song` → `albums.value[albums.value.length - 1].songs.push(msg.song)`
         + `receivedCount.value++` + `progress.value = receivedCount.value / expectedCount.value`;
         `done` → cleanup, `isVisible.value = false`, resolve Promise;
         `error` → reject Promise, `errorMessage.value = msg.message`.
     6. Каждые ~16мс (через `requestAnimationFrame`) обновлять
       `aria-live` элемента для screen reader (throttle — НЕ спамить
       каждым чанком). Конкретно: держать ref `rafThrottleFlag`,
       который ставится в `true` при обновлении `aria-valuenow`;
       сбрасывается в `false` в `rAF` callback (если флаг не успевает
       сброситься — обновление aria-live подавляется).
  7. **Метрики (FR-FE-010)** — собирать в `sessionStorage.km_zakroma_stream_metrics`
     (JSON-массив). Поля каждой записи:
     - `eventType`: `zakroma_stream_start` / `zakroma_stream_done` /
       `zakroma_stream_error` / `zakroma_stream_abort`.
     - `author`, `firstChunkMs` (TTFB от `start()` до первого `meta`),
       `durationMs`, `expectedCount`, `receivedCount`,
       `streamAborted: boolean`, `errorCategory`.
     Фиксировать `firstChunkMs` = `performance.now() - startTs` при
     получении первого `meta` сообщения. При `done` / `error` / `abort`
     — append запись в массив.
  8. **BATCH POST (FR-FE-010)** — при `pagehide` (visibilitychange + flush)
     отправить весь массив в `POST /api/public/zakroma/stream/metrics`
     (новый endpoint, см. T012-b). Использовать `navigator.sendBeacon`
     (он работает на `pagehide`, гарантированно). Если `sendBeacon`
     не сработал (массив > 64 KB) — fallback `fetch` + `keepalive: true`.
     Если endpoint /metrics недоступен (404) — silent fallback, не
     ломать UI.
  - `cancel()`: `controller.abort()`, очистить буфер, reject Promise,
    append `zakroma_stream_abort` в metrics.
  - ~100 строк (включая метрики).
- [ ] **T014** [US2] В `ZakromaView.vue` (расширение T010) —
  реальный progress UI:
  - Заменить placeholder из T010 на полноценный прогрессометр:
    ```vue
    <div v-if="streamProgress.isVisible.value" class="km-stream-progress" role="progressbar"
         :aria-valuemin="0" :aria-valuemax="streamProgress.expectedCount.value"
         :aria-valuenow="streamProgress.receivedCount.value"
         aria-live="polite">
      <div class="km-stream-text">
        Загружаем {{ streamProgress.receivedCount.value }} из
        {{ streamProgress.expectedCount.value }} песен автора {{ selectedAuthor }}…
      </div>
      <div class="km-stream-bar">
        <div class="km-stream-bar-fill"
             :style="{ width: (streamProgress.progress.value * 100) + '%' }" />
      </div>
    </div>
    ```
  + ошибка + retry-кнопка (FR-FE-001 + SC-003):
    ```vue
    <div v-else-if="streamProgress.errorMessage.value" class="km-stream-error">
      {{ streamProgress.errorMessage.value }}
      <button @click="retryLoad()">Повторить</button>
    </div>
    ```
  - CSS: `.km-stream-progress` — sticky-панель под хедером (как
    `.km-filter-bar`); полоса `var(--km-accent)`, фон `var(--km-bg2)`,
    высота ~4px, переход `width 0.2s ease`.
  - Кнопка «Отмена» справа от прогрессометра → `streamProgress.cancel()` +
    `backToAuthors()` (FR-FE-006).
  - ~50 строк (HTML + CSS).
- [ ] **T015** [US2] **Manual Verification (Phase 4)**:
  - Сценарий 1 (quickstart): большой автор + DevTools Network → chunked
    NDJSON, UI обновляется по сообщениям.
  - Сценарий 2 (quickstart): отмена через «Отмена» — Network `(canceled)`.
  - Сценарий 5 (quickstart): оффлайн → ошибка + Retry.
  - Сценарий 6 (quickstart): старый endpoint `/api/public/zakroma` —
    возвращает JSON-массив.

**Checkpoint**: end-to-end real progress работает на localhost + prod.

---

## Phase 5: User Story 3 — Debounce + 30s no-op (Priority: P2)

**Goal**: быстрые клики по тайлу не плодят запросов; повторный клик
через > 30с — force refresh.

**Independent Test**: scenarios 3, 4 из `quickstart.md`.

- [x] **T016** ✅ [US3] В `composables/useZakromaStreamProgress.js` —
  debounce для «быстрых» ответов:
  - `start()`: `let showTimeout = setTimeout(() => { isVisible.value = true }, 300)`.
  - При приходе `done` — `clearTimeout(showTimeout)`; если timeout
    НЕ сработал (стрим быстрее 300мс) — просто не показывать
    индикатор (`isVisible` остаётся false, UI не «мелькает»).
  - При приходе `error` — `clearTimeout(showTimeout)` + показать
    сразу (без debounce).
  - **Уточнение (A2)**: `setTimeout(300)` для visibility-debounce НЕ
    нарушает FR-FE-008 (который запрещает `setInterval` для
    синтетического прогресса). Debounce — это ожидание UI visibility,
    а не фейковое обновление прогресса.
  - Verify: ESLint ✅.
- [x] **T017** ✅ [US3] В `zakroma.js` store (расширение T009):
  - При вызове `loadZakromaStream(author, expectedCount)` — проверяется
    `lastLoadedTimestampByAuthor[author]`:
    - Если есть и `Date.now() - timestamp < 30_000` — **no-op** (вернуть
      Promise.resolve() сразу, НЕ запускать fetch).
    - Иначе — запустить стрим, после успеха commit
      `setLastLoadedTimestamp, { author, ts: Date.now() }`.
  - При получении новой ошибки — сбрасываем `lastLoadedTimestamp[author] = 0`,
    чтобы следующий клик прошёл как force refresh.
  - Verify: ESLint ✅.
- [x] **T018** ✅ [US3] В `ZakromaView.vue` — `cancelZakromaStream()`:
  - `loadZakromaStream({ author, expectedCount: 0 })` (force-refresh →
    composable.cancel() сработает через dedup-bypass) ВЫЗВАН ДО
    `backToAuthors()`.
  - Verify: ESLint ✅.
- [ ] **T019** [US3] **Manual Verification (Phase 5)**: ⚠️ Требует пользователя:
  - Сценарий 3 (quickstart): повторный клик по тому же автору в < 30с —
    нет нового fetch в Network.
  - Сценарий 4 (quickstart): повторный клик через 31с — force refresh,
    есть новый fetch.

**Checkpoint**: все 5 quickstart сценариев проходят.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: документация (обязательная по FR-009), cleanup, lint, PR.

- [x] **T020** ✅ [P] [US-all] KDoc + JSDoc покрытие:
  - `ZakromaAlbumMetaPublicDto` — KDoc + `@see docs/features/zakroma-stream-progress.md`.
  - `ZakromaStreamMessageDto` — KDoc + `@see`.
  - `ZakromaStreamMetricDto` — KDoc + `@see`.
  - `PublicApiController.zakromaStream(...)` — KDoc + `@see`.
  - `PublicApiController.zakromaStreamMetrics(...)` — KDoc + `@see`.
  - `useZakromaStreamProgress.js` — JSDoc + `@see`.
  - **Verified**: bash tools/check-kdoc-coverage.sh ✅ 96.3% (≥ 50% baseline).
  - **Verified**: bash tools/check-jsdoc-coverage.sh karaoke-public ✅ 98.5%.
  - `ZakromaAlbumMetaPublicDto` (класс — JSDoc, каждый public val — не
    обязательно).
  - `ZakromaStreamMessageDto` (класс — KDoc со ссылкой на спекy).
  - `PublicApiController.zakromaStream(...)` (endpoint — KDoc +
    `@see docs/features/zakroma-stream-progress.md`).
  И JSDoc на `useZakromaStreamProgress.js`.
- [x] **T021** ✅ [P] [US-all] Создан `docs/features/zakroma-stream-progress.md` —
  6 обязательных разделов (проверено `bash tools/check-feature-doc.sh` ✅):
  - `## Что делает` (NDJSON stream + UI).
  - `## Зачем` (real-time прогресс vs spinner).
  - `## Как работает` (wire protocol, AbortController, nginx buffering).
  - `## Инварианты / правила` (старый endpoint без изменений).
  - `## Известные ловушки` (gzip разрывает NDJSON, AbortController cleanup).
  - `## Ссылки` (spec.md, plan.md, nginx конфиг).
  - ~280 строк.
  6 обязательных разделов (проверяется `tools/check-feature-doc.sh`):
  - `## Что делает` (NDJSON stream + UI).
  - `## Зачем` (real-time прогресс vs spinner).
  - `## Как работает` (wire protocol, AbortController, nginx buffering).
  - `## Инварианты / правила` (старый endpoint без изменений).
  - `## Известные ловушки` (gzip разрывает NDJSON, AbortController cleanup).
  - `## Ссылки` (spec.md, plan.md, nginx конфиг).
  - ~120-180 строк (по образцу существующих `docs/features/*.md`).
- [x] **T022** ✅ [P] [US-all] Обновлён `docs/features/README.md`:
  - Добавлена строка (26 фич): `26 | zakroma-stream-progress | Real-time прогресс через NDJSON chunked-stream (...) | [zakroma-stream-progress.md](./zakroma-stream-progress.md)`.
- [x] **T023** ✅ [P] [US-all] Cleanup старого кода (verified):
  - `grep -n latestRequestId karaoke-public/src/store/modules/zakroma.js` — 0 совпадений (был удалён в T009).
  - `grep -n 'Загрузка' karaoke-public/src/views/ZakromaView.vue` — 0 (заменён в T014).
  - `ls karaoke-public/src/composables/useZakromaLoadProgress.js` — file does not exist.
  - **0 cleanup-операций** (legacy-код уже отсутствовал).
- [x] **T024** ✅ [P] [US-all] Lint + coverage:
  - `./gradlew ktlintCheck` ✅ BUILD SUCCESSFUL.
  - `cd karaoke-public && npm run lint:check` ✅ 0 warnings.
  - `cd webvue3 && npm run lint:check` ✅ 0 warnings.
  - `bash tools/check-kdoc-coverage.sh` ✅ 96.3% (≥ 50% baseline).
  - `bash tools/check-jsdoc-coverage.sh karaoke-public` ✅ 98.5%.
  - `bash tools/check-feature-doc.sh docs/features/zakroma-stream-progress.md` ✅ OK.
  - `bash tools/check-enforcement.sh` ✅ baseline clean.
- [ ] **T025** [US-all] **Manual Verification (Phase 6)**: ⚠️ Требует пользователя:
  - Запустить все 10 сценариев `quickstart.md` на prod
    (после deploy пользователем).
  - Зафиксировать результаты в Pass-записи `docs/architecture-notes.md`
    (после merge).
- [ ] **T026** [US-all] Git workflow: ⚠️ Требует пользователя:
  - `git push -u origin 181-zakroma-author-load-progress`.
  - `gh pr create --base master --title "feat(zakroma): real-time progress via backend NDJSON chunked-stream (#181)" --body "<auto-summary>"`.
  - ⚠️ Если push через VPN падает — запустить без VPN.
- [ ] **T027** [US-all] CI 7/7 → merge: ⚠️ Требует пользователя:
  - `gh pr checks` или `gh run watch` — дождаться 7/7 SUCCESS.
  - `gh pr merge <N> --merge` (БЕЗ `--delete-branch` — см. AGENTS.md).
  - **Не** удалять ветку после merge.

**Checkpoint**: PR смержен в master, на prod real-time прогресс работает.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: ✅ Done (T001-T003 в master через feature-ветку 181).
- **Foundational (Phase 2)**: depends on Setup. Блокирует US1.
- **User Story 1 (Phase 3)**: depends on Phase 2 (T004-T006).
  - T007 зависит от T004, T005 (DTO компилируются).
  - T008 (composable) не зависит от backend, можно параллельно с T007.
  - T009 (store) — после T008.
  - T010 (view) — после T009.
- **User Story 2 (Phase 4)**: depends on Phase 3 (расширяют тот же код).
  - T012 (backend loop) — расширяет T007.
  - T013 (parser) — расширяет T008.
  - T014 (UI) — расширяет T010.
  - T015 (manual) — после T012, T013, T014.
- **User Story 3 (Phase 5)**: depends on Phase 4.
  - T016 — расширяет T008/T013.
  - T017 — расширяет T009.
  - T018 — уточняет T014.
- **Polish (Phase 6)**: depends on US1+US2+US3.
  - T020-T023 — параллельно.
  - T024 — после T020-T023.
  - T025 — после T024 (deploy).
  - T026-T027 — после T025.

### Within Each User Story

- DTOs (T004, T005) перед endpoint (T007).
- Backend endpoint (T007) перед frontend composable (T008) — формат
  wire должен быть финализирован.
- Composаble (T008) перед store action (T009).
- Store (T009) перед view (T010).
- Implementation перед manual verification (T011, T015, T019).

### Parallel Opportunities

- T004, T005, T006 — параллельно (разные файлы, никаких зависимостей).
- T020, T021, T022, T023 — параллельно (после Phase 5).
- Кросс-US параллелизм: US1 и US2 идут последовательно по логике стрима
  (T008 → T013 → T014). US3 — после US2.

---

## Implementation Strategy

### MVP First (User Story 1 + 2)

1. ✅ Phase 1: Setup.
2. ⏳ Phase 2: Foundational (DTOs + nginx template).
3. ⏳ Phase 3: User Story 1 (базовый endpoint + composable skeleton + очистка).
4. **STOP and VALIDATE** (T011): backend компилируется, отдаёт `meta + done`,
   UI очищается синхронно.
5. ⏳ Phase 4: User Story 2 (streaming loop + NDJSON парсер + UI прогрессометра).
6. **STOP and VALIDATE** (T015): real-time прогресс работает на localhost +
   prod (после deploy).

### Incremental Delivery

1. Phase 1-2: foundation спецификация/план.
2. Phase 3: endpoint возвращает NDJSON, UI очищается (можно задеплоить
   ИЛИ не деплоить до Phase 4 — рекомпиляция только ради очистки
   лишняя).
3. Phase 4: real-time прогресс end-to-end. **DEPLOY** — это MVP.
4. Phase 5: debounce + 30s no-op (полировка, можно объединить с
   Phase 4 в один деплой).
5. Phase 6: lint/CI/docs/PR.

**Рекомендация**: Phase 3 + Phase 4 — одним деплоем (они неразрывно
связаны, очистка без real-time бесполезна). Phase 5 — в том же деплое
(мелкая правка). Итого 1 deploy после Phase 5.

### Out of Scope Reminders

- Никаких новых сущностей в БД.
- Никаких изменений в `webvue3`.
- Никаких изменений в `SearchView.vue`.
- Никакого прогресса для `loadSpecialBucket`.
- Никаких изменений в `archive-notes.md` (это Pass-пользователь сделает сам).

---

## Notes

- [P] задачи = разные файлы, нет зависимостей — параллелятся.
- [Story] label задаёт traceability к конкретной user story.
- Каждая user story должна быть независимо завершаемой и тестируемой.
- Commit после каждой задачи или логической группы (внутри Phase).
- Останавливаться на каждом **Checkpoint** для независимой валидации.
- Избегать: расплывчатых задач, конфликтов за один файл, cross-story
  зависимостей, ломающих независимость.
- **ВАЖНО**: на каждом **Manual Verification** шаге (T011, T015, T019,
  T025) проверять через `quickstart.md`. Если сценарий не проходит —
  фиксить ДО продолжения, не оставлять «на потом».
