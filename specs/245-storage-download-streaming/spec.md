# Feature Specification: Streaming download из MinIO в StorageController.downloadFile

**Feature Branch**: `245-storage-download-streaming`
**Created**: 2026-08-26
**Status**: Draft
**Parent**: [`specs/241-db-storage-perf-audit/spec.md`](../241-db-storage-perf-audit/spec.md) — Tier-1 / FR-104
**Input**: User description (через parent спеку): "Streaming для `StorageController.downloadFile` — устранить OOM-риск через `readAllBytes()`, перейти на `StreamingResponseBody` или `Resource` для больших MP4 (100+ MB)."

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Скачивание больших MP4 без OOM (Priority: P1)

Администратор через webvue3 (admin SPA) или через прямой GET-запрос скачивает MP4 100+ MB из MinIO через `StorageController.downloadFile`. Сервер НЕ падает с `OutOfMemoryError`. Файл стримится порциями через `StreamingResponseBody` или `Resource`, не загружаясь целиком в heap.

**Why this priority**: текущий код `val bytes = inputStream.readAllBytes()` (StorageController.kt:134) загружает ВЕСЬ файл в heap. Для MP4 100 MB при heap 256 MB — риск OOM, для 500 MB — гарантированный OOM. На проде MinIO доступен через nginx-proxy (см. KDoc `WebKaraokeStorageServiceImpl`), и прямой download через этот endpoint — единственный путь для admin'а получить файл.

**Independent Test**: запрос MP4 500 MB через `GET /api/storage/download?bucketName=...&fileName=...` возвращает файл (Content-Length = 524288000) без `OutOfMemoryError`, heap-использование JVM НЕ превышает baseline + 50 MB.

**Acceptance Scenarios**:
1. **Given** MP4 100 MB в MinIO, **When** admin запрашивает `GET /api/storage/download`, **Then** файл скачивается полностью, heap во время download ≤ baseline + 50 MB (типовое +10–30 MB на streaming buffers). Текущее — heap +100 MB (весь файл).
2. **Given** MP4 500 MB в MinIO, **When** admin запрашивает тот же endpoint, **Then** файл скачивается полностью без `OutOfMemoryError`. Текущее — OOM при heap 256 MB.
3. **Given** MP4 100 MB, **When** admin скачивает через тот же endpoint, **Then** Content-Length в HTTP-ответе совпадает с размером файла в MinIO (для прогресс-бара на клиенте).
4. **Given** файл 50 KB (маленький), **When** тот же endpoint, **Then** работает как раньше (overhead streaming-buffer'а незначителен).

---

### User Story 2 — Поддержка Range-запросов (Priority: P2)

Видеоплеер на стороне клиента (или `curl -r`) может запросить часть файла через HTTP `Range: bytes=X-Y`. Сервер корректно обрабатывает Range-запросы, возвращая `206 Partial Content` с правильным `Content-Range` и запрошенным куском файла.

**Why this priority**: для видео-плеера критично иметь возможность seek (перемотка). Сейчас без Range-поддержки плеер вынужден либо скачивать весь файл (медленно для 500 MB), либо использовать nginx-proxy напрямую (минуя наш endpoint). Range-поддержка через MinIO (S3 GetObject с Range) + Spring `ResourceRegion` — стандартный паттерн.

**Independent Test**: `curl -H "Range: bytes=0-1023" /api/storage/download?bucketName=...&fileName=mp4.mp4` возвращает первые 1024 байта с HTTP 206 и `Content-Range: bytes 0-1023/<total>`.

**Acceptance Scenarios**:
1. **Given** Range-запрос `bytes=0-1023`, **When** endpoint получает его, **Then** возвращается HTTP 206 с телом первых 1024 байт и заголовком `Content-Range`.
2. **Given** Range-запрос `bytes=100-199` для файла 1000 байт, **When** endpoint, **Then** HTTP 206 с телом байт 100–199 и `Content-Range: bytes 100-199/1000`.
3. **Given** Range-запрос за пределами файла, **When** endpoint, **Then** HTTP 416 (Range Not Satisfiable).

---

### Edge Cases

- **Что если MinIO недоступен (network error)?** Текущий код: `try/catch` + HTTP 500. Сохраняется.
- **Что если файл удалён между `fileExists` (HEAD) и `downloadFile` (GET)?** Текущий код: `try/catch` в `downloadFile` ловит `MinioException` → HTTP 500. Сохраняется.
- **Что если клиент отключился посреди download?** Текущий код: Spring закроет OutputStream, `StreamingResponseBody.writeTo` выбросит `IOException`. Нужно явно НЕ падать с unhandled exception (просто логировать).
- **Что если несколько потоков скачивают большие файлы одновременно?** Текущий код: каждый запрос занимает 1 heap-блок размера файла (100 MB × 10 запросов = 1 GB). Streaming: каждый запрос занимает ~64 KB streaming buffer × 10 = 640 KB. Драматическая разница.
- **Что если MinIO возвращает Content-Type неподходящий (например, `application/octet-stream` для MP4)?** Текущий код: не выставляет Content-Type явно (Spring сам определяет по расширению). Сохраняется или улучшается (выставить `Content-Type: video/mp4` явно).
- **Что если `Content-Disposition: attachment; filename="..."` содержит кириллицу?** Текущий код: прямая подстановка без encoding. Spring обычно сам делает RFC 5987 encoding. Сохраняется или улучшается.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: `StorageController.downloadFile` (строки 116–146) MUST заменить `val bytes = inputStream.readAllBytes()` + `ResponseEntity.ok().body(bytes)` на `ResponseEntity<StreamingResponseBody>` или `ResponseEntity<Resource>` (через `InputStreamResource`/`FileSystemResource`), чтобы файл стримился порциями, а не загружался целиком в heap.
- **FR-002**: Endpoint MUST выставлять `Content-Length` в HTTP-ответе (на основе `karaokeStorageService.getFileStat(bucketName, fileName)` или `getFileInfo`), чтобы клиент мог показать прогресс-бар. Для файлов > 2 GB — `Content-Length` как `Long` (стандарт HTTP).
- **FR-003**: Endpoint MUST сохранять `Content-Disposition: attachment; filename="..."` (текущее поведение, FR-006 — backward-compat).
- **FR-004**: Endpoint MUST поддерживать HTTP Range-запросы через Spring `ResourceRegion` (для `Resource` варианта) или явную обработку `Range` header в `StreamingResponseBody` (для `StreamingResponseBody` варианта). Возвращать `206 Partial Content` для валидных Range, `416 Range Not Satisfiable` для невалидных.
- **FR-005**: При отключении клиента посреди download (broken pipe) endpoint MUST НЕ бросать unhandled exception в глобальный error-handler. Логировать `WARN` через SLF4J logger (уже есть `private val logger: Logger = LoggerFactory.getLogger(...)`) и возвращать корректный код.
- **FR-006**: Backward-compatibility: сигнатура endpoint НЕ меняется (`@GetMapping("/download")` с параметрами `bucketName`, `fileName`). Поведение для маленьких файлов (≤1 MB) — без видимой разницы.
- **FR-007**: Должна быть возможность отключить Range-поддержку через `KaraokeProperties` (по умолчанию `true`), если окажется что она ломает какие-то клиенты.

### Key Entities

- **`StreamingResponseBody`** (Spring 5+) — callback-based streaming: `writeTo(OutputStream)` вызывается Spring'ом в отдельном потоке, можно писать порциями.
- **`InputStreamResource`** / **`FileSystemResource`** + `ResourceRegion` — Spring-паттерн для range-requests. `ResourceRegion` берёт кусок `Resource` по `start..end` байтам.
- **`karaokeStorageService.getFileStat(bucketName, fileName): StatObjectResponse?`** (уже есть, см. parent спека, A.2) — для получения `size` (Long) и `contentType` файла в MinIO. Используется для `Content-Length` и `Content-Type`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Запрос MP4 100 MB через endpoint завершается успешно с heap-потреблением JVM ≤ baseline + 50 MB (типовое +10–30 MB). Текущее — +100 MB (весь файл в heap).
- **SC-002**: Запрос MP4 500 MB через endpoint завершается успешно без `OutOfMemoryError` при стандартном heap 256 MB. Текущее — OOM гарантирован.
- **SC-003**: Concurrent-тест: 10 одновременных download'ов MP4 100 MB → heap-потребление ≤ baseline + 200 MB (10 × streaming buffers). Текущее — +1000 MB (10 × 100 MB), OOM при heap 1 GB.
- **SC-004**: HTTP `Content-Length` для MP4 100 MB = 104857600 (точно). Для проверки прогресс-бара на клиенте.
- **SC-005**: Range-запрос `bytes=0-1023` для MP4 100 MB возвращает HTTP 206 с `Content-Range: bytes 0-1023/104857600` и телом первых 1024 байт файла.
- **SC-006**: Latency первого байта (TTFB) для download MP4 100 MB — ≤ 200 мс (cold path, без прогрева). Сейчас первый байт приходит после полной загрузки файла в heap → 5–15 сек TTFB для 100 MB по медленной сети.
- **SC-007**: Маленькие файлы (≤1 MB) — overhead streaming-buffer'а не превышает +5 мс на запрос по сравнению с `readAllBytes()`.

## Assumptions

- **Spring Boot 2.x/3.x** (см. Constitution § Технологический стек) — поддерживает `StreamingResponseBody` и `Resource`/`ResourceRegion` нативно. Никаких новых зависимостей не требуется.
- **MinIO через nginx-proxy** на проде (см. KDoc `WebKaraokeStorageServiceImpl`): для karaoke-web — только через nginx; для karaoke-app (admin) — напрямую через MinIO Java SDK. Endpoint `StorageController.downloadFile` — это karaoke-app endpoint, используется только на admin-машине.
- **Tomcat (Spring Boot default)** — настраиваемый через `server.tomcat.max-swallow-size` и `server.tomcat.connection-timeout`. Для больших download'ов может потребоваться `max-swallow-size = -1` (без ограничения).
- **Auth на endpoint** — текущий код: нет auth (`permitAll()` для админки, см. Constitution § V). Сохраняется.
- **`Content-Disposition`**: текущий код использует `attachment; filename="$fileName"` (без encoding для кириллицы). Это работает для admin-браузеров, но RFC-корректный вариант — `filename*=UTF-8''...`. Улучшение вне scope.
- **Nginx buffering**: если за reverse-proxy, может буферизовать. Это вне scope — на admin-машине nginx обычно не используется.
- **Замер эффекта**: pre/post heap-usage через JVM flags (`-Xmx`, `jconsole`, `VisualVM`) или просто по отсутствию OOM в логах. `pg_log` неприменим (БД не задействована). См. parent спека, Clarifications Session 2026-08-26.

## Out of Scope (явно НЕ делается в этой фиче)

- Изменение других endpoint'ов в `StorageController` (`uploadFile`, `getFileUrl`, `getPresignedUrl`, `deleteFile`, `listFiles`, `checkIfExists` — FR-004 parent спеки, Tier-2/P2). Этот фикс — только `downloadFile`.
- Streaming для `uploadFile` — multipart upload уже стримит (Spring `@RequestParam("file") MultipartFile` инкапсулирует streaming). Не требует изменений.
- Изменение Auth / `WebKaraokeStorageServiceImpl` (для karaoke-web этот endpoint не используется — там `fetchFromMinIO`/`existsInMinIO` через nginx-proxy).
- SigV4 Range-запросы через nginx path-proxy (там ломается подпись, см. parent спека, A.2). Для Range-поддержки MinIO Java SDK используется напрямую из karaoke-app.
- Tier-2/Tier-3 оптимизации MinIO (parent спека, A.2, M-2/M-3/M-4/M-5) — отдельные фичи.
- Изменение Content-Type detection (сейчас Spring сам определяет по расширению).
- Encoding кириллицы в `Content-Disposition` (RFC 5987) — улучшение вне scope.
