---
status: Active
slug: 245-storage-download-streaming
related:
  - ../domain/media-storage.md
  - ../architecture/L3-components.md
  - ../../specs/245-storage-download-streaming/spec.md
  - 241-db-storage-perf-audit
---

# 245 — Streaming download из MinIO в StorageController.downloadFile (LiveDoc)

> Drill-down — [specs/245-storage-download-streaming/spec.md](../../specs/245-storage-download-streaming/spec.md).
> Parent — [241-db-storage-perf-audit](241-db-storage-perf-audit.md).

## Что делает

Устраняет OOM-риск в `StorageController.downloadFile` (строки 116-146):
`inputStream.readAllBytes()` → `Resource` через `LengthAwareInputStreamResource`.

## Эффект

* Heap: +100 MB → +30 MB на MP4 100 MB.
* **500 MB без OOM** (раньше гарантированный OOM при heap 256 MB).
* TTFB: 5-15 сек → ≤ 200 мс (стриминг начинает отдавать сразу).
* HTTP `Range` → HTTP 206 автоматически (Spring `ResourceHttpMessageConverter`).

## Admin-only

Endpoint доступен только в karaoke-app. На проде karaoke-web использует `fetchFromMinIO`
через nginx-proxy (см. `WebKaraokeStorageServiceImpl` — заглушка).

## Реализация

* `LengthAwareInputStreamResource` — обёртка с явным `contentLength()` (Spring 5+ удалил `getContentLength()` из `Resource`).
* Content-Length из `karaokeStorageService.getFileStat()` (FR-002).
* `application.yml`: `server.tomcat.max-swallow-size: -1` (без ограничения размера response body).