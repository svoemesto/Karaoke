# Implementation Plan: Streaming download из MinIO в StorageController.downloadFile

**Branch**: `245-storage-download-streaming` | **Date**: 2026-08-26 | **Spec**: [spec.md](spec.md)
**Parent**: [`specs/241-db-storage-perf-audit/spec.md`](../241-db-storage-perf-audit/spec.md) — Tier-1 / FR-104

**Input**: Feature specification from `/specs/245-storage-download-streaming/spec.md`

## Summary

Заменить `inputStream.readAllBytes()` (StorageController.kt:134) на streaming через `StreamingResponseBody` или `Resource`+`ResourceRegion`. Устранить OOM-риск для MP4 100+ MB, добавить поддержку HTTP Range-запросов (для видеоплеера). На MP4 100 MB: heap +100 MB → +10–30 MB, TTFB 5-15 сек → ≤200 мс.

## Technical Context

**Language/Version**: Kotlin 1.x (JDK 17), Spring Boot 2.x/3.x, Spring Web MVC
**Primary Dependencies**: `org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody`, `org.springframework.core.io.support.ResourceRegion`, `io.minio.GetObjectArgs`, `io.minio.MinioClient`
**Storage**: MinIO (S3-compatible, прямой доступ из karaoke-app; на проде через nginx-proxy для karaoke-web, но karaoke-app использует MinIO напрямую через Java SDK)
**Testing**: ручное на admin-машине через curl + admin UI
**Target Platform**: Linux server (admin-машина; на проде karaoke-web не использует этот endpoint)
**Project Type**: library/multi-module Gradle
**Performance Goals**: heap ≤ baseline + 50 MB при скачивании MP4 100 MB (SC-001); OOM-free для 500 MB (SC-002); TTFB ≤ 200 мс (SC-006); Range support (SC-005)
**Constraints**: backward-compat endpoint (FR-006); Content-Disposition (FR-003); Content-Length (FR-002); не падать на broken pipe (FR-005)
**Scale/Scope**: admin-сценарий, типично 1-3 одновременных download'а; редко 10+ (видеоплеер seek)

## Constitution Check

- ✅ **Principle I**: не затрагивается.
- ✅ **Principle II (Сырой JDBC)**: N/A (это MinIO, не БД). Но доступ к MinIO через Java SDK — сохраняется (а не через самописный HTTP).
- ✅ **Principle III-VIII**: не затрагиваются.

**Constitution Check: PASS**.

## Project Structure

```text
specs/245-storage-download-streaming/
├── plan.md              # Этот файл
├── spec.md              # Feature specification (FR-104 из parent)
├── checklists/
│   └── requirements.md  # 16/16 ✅
└── tasks.md             # Phase 2

karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/
└── StorageController.kt     # ИЗМЕНЕНИЕ: downloadFile

kara-app/src/main/resources/
└── application.yml          # Возможно: server.tomcat.max-swallow-size = -1

specs/245-storage-download-streaming/contracts/
└── download-api.md          # Phase 1: API contract для Range
```

**Structure Decision**: Single project. Изменения в одном endpoint + опциональный Tomcat config.

## Implementation Approach

### Phase 1: Выбор подхода (StreamingResponseBody vs Resource)

**Вариант A**: `StreamingResponseBody` (Spring 5+, callback-based).
```kotlin
@GetMapping("/download")
fun downloadFile(...): ResponseEntity<StreamingResponseBody> { ... }
```
**Pros**: простой API, нативная Spring-поддержка.
**Cons**: ручная обработка Range header, нет встроенной поддержки `ResourceRegion`.

**Вариант B**: `Resource` + `ResourceRegion` (Spring-built-in для Range).
```kotlin
@GetMapping("/download")
fun downloadFile(...): ResponseEntity<Resource> { ... }
```
**Pros**: встроенная поддержка Range, Spring автоматически парсит Range header.
**Cons**: чуть сложнее setup, `InputStreamResource` не поддерживает `getContentLength()` (нужен `FileSystemResource` или кастомный `Resource`).

**Рекомендация**: Вариант B (Resource+ResourceRegion). Стандартный Spring-паттерн, Range из коробки.

### Phase 2: Реализация через `Resource`

```kotlin
@GetMapping("/download")
fun downloadFile(
    @RequestParam("bucketName") bucketName: String,
    @RequestParam("fileName") fileName: String,
    @RequestHeader(value = "Range", required = false) rangeHeader: String?,
    request: HttpServletRequest,
): ResponseEntity<Resource> {
    if (!isValidFileName(fileName)) {
        logger.warn("Invalid file name: $fileName from IP: ${request.remoteAddr}")
        return ResponseEntity.badRequest().build()
    }

    if (!karaokeStorageService.fileExists(bucketName, fileName)) {
        logger.info("File not found: $fileName in bucket: $bucketName")
        return ResponseEntity.notFound().build()
    }

    // FR-002: Content-Length
    val fileStat = karaokeStorageService.getFileStat(bucketName, fileName)
    val contentLength = fileStat?.size() ?: -1L

    // FR-001: streaming через Resource (InputStreamResource — легковесный wrapper)
    val streamResource = object : InputStreamResource(
        karaokeStorageService.downloadFile(bucketName, fileName)
    ) {
        override fun getContentLength(): Long = contentLength
        override fun contentLength(): Long = contentLength
    }

    // FR-003: Content-Disposition
    val headers = HttpHeaders().apply {
        add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$fileName\"")
        if (contentLength > 0) {
            setContentLength(contentLength)
        }
    }

    return ResponseEntity
        .ok()
        .headers(headers)
        .body(streamResource)
    // Spring автоматически обработает Range header и вернёт ResourceRegion
}
```

### Phase 3: FR-005 (broken pipe)

Добавить глобальный error handler или try/catch в streaming-коде:

```kotlin
// В Spring controller advice (если есть) или в KaraokeStorageServiceImpl.downloadFile
try {
    outputStream.write(buffer)
    outputStream.flush()
} catch (e: IOException) {
    // FR-005: client disconnect — НЕ throw, только log
    logger.warn("Download aborted by client: bucket=$bucketName, file=$fileName, cause=${e.message}")
    // НЕ пробросить — Spring закроет stream
}
```

Spring обычно сам обрабатывает `IOException` в streaming без unhandled exception в Tomcat. Но для уверенности — добавить try/catch в `KaraokeStorageServiceImpl.downloadFile`.

### Phase 4: Tomcat config (опционально)

`application.yml`:
```yaml
server:
  tomcat:
    # FR-004: поддержка больших тел в response
    max-swallow-size: -1
    # Таймаут для streaming download'а (по умолчанию 60 сек)
    connection-timeout: 300s
```

## Risks & Mitigations

| Риск | Вероятность | Митигация |
|------|-------------|-----------|
| `InputStreamResource.getContentLength()` не вызывается Spring'ом | Низкая | Override оба метода: `getContentLength()` (legacy) + `contentLength()` (новый). |
| `karaokeStorageService.downloadFile(bucketName, fileName)` возвращает InputStream, который MinIO закрывает после исчерпания | Средняя | Проверить в `KaraokeStorageServiceImpl` — должен быть `Closeable` и закрываться в finally. |
| Range-парсинг Spring ломается на `bytes=0-` (open-ended) | Средняя | Spring Web корректно обрабатывает open-ended Range. Проверить в тестах. |
| `Content-Length` неверный для файлов > 2 GB (int overflow) | Низкая | Используем `Long` для `contentLength`. Spring поддерживает `setContentLength(Long)` начиная с 5.x. |
| OOM при попытке `InputStreamResource.read()` буферизации | Низкая | Spring читает порциями через `Resource.transferredTo(OutputStream)` (Java 9+) или `StreamUtils.copy(...)`. |
| Tomcat default `max-swallow-size=2MB` режет большой download | Средняя | Установить `-1` в application.yml (Phase 4). |
| nginx между admin и пользователем буферизует | Низкая | На admin-машине nginx обычно не используется. Документировать. |

## Out-of-Scope

- Изменение других endpoint'ов `StorageController` (Tier-2).
- Streaming для `uploadFile` (multipart уже стримит).
- Изменение Auth / `WebKaraokeStorageServiceImpl`.
- Encoding RFC 5987 для кириллицы в Content-Disposition.
- HTTP/2 push (если нужен — отдельная задача).

## Complexity Tracking

*Нет нарушений Constitution Check.*

## Verification Plan

### До деплоя

1. Замерить baseline heap-usage JVM karaoke-app (`jconsole`/`VisualVM` или `-Xlog:gc`).
2. Попробовать скачать MP4 100 MB через текущий endpoint → замерить peak heap (ожидание +100 MB).

### После деплоя

1. **Heap-test**: MP4 100 MB → peak heap ≤ baseline + 50 MB (SC-001).
2. **OOM-test**: MP4 500 MB → успешный download без OOM (SC-002). Это критичный тест.
3. **Concurrent-test**: 10 одновременных download'ов MP4 100 MB → heap ≤ baseline + 200 MB (SC-003).
4. **TTFB-test**: `curl -w '%{time_starttransfer}' -o /dev/null` → ≤ 200 мс (SC-006).
5. **Range-test**: `curl -H "Range: bytes=0-1023" -i` → HTTP 206, `Content-Range: bytes 0-1023/<total>` (SC-005).
6. **Regression**: маленький файл (50 KB) — latency overhead ≤ 5 мс (SC-007).

### Acceptance (mapping)

- **SC-001**: jconsole/VisualVM.
- **SC-002**: manual OOM-test.
- **SC-003**: stress-test с 10 concurrent curl.
- **SC-004**: `curl -I` + проверка Content-Length.
- **SC-005**: `curl -H "Range:"` + проверка HTTP 206.
- **SC-006**: `curl -w '%{time_starttransfer}'`.
- **SC-007**: замер для файла 50 KB.

## Timeline Estimate

- Phase 1 (выбор подхода): 10 мин (уже сделано в плане).
- Phase 2 (Resource+ResourceRegion): 45 мин.
- Phase 3 (broken pipe): 20 мин.
- Phase 4 (Tomcat config): 5 мин.
- **Итого: ~1.5 часа кодинга**.
- Тестирование (включая OOM-test): 1 час.

## Definition of Done

- [ ] FR-001 … FR-007 реализованы.
- [ ] SC-001 … SC-007 измеримы и подтверждены (особенно SC-002 — OOM-test).
- [ ] ktlintCheck + compile проходят.
- [ ] Tomcat config (application.yml) обновлён.
- [ ] PR создан через `gh pr create --base master`.
- [ ] CI 8/8 PASS.
- [ ] Deploy на admin-машину + ручное тестирование (curl + UI).

## Next Step

→ `/speckit.tasks specs/245-storage-download-streaming`.
