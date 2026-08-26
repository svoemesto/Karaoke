# Specification Quality Checklist: Streaming download из MinIO в StorageController.downloadFile

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-08-26
**Feature**: [spec.md](spec.md)
**Parent**: [`specs/241-db-storage-perf-audit/spec.md`](../241-db-storage-perf-audit/spec.md) — Tier-1 / FR-104

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
  - Спека ссылается на конкретный endpoint и file:line (StorageController.kt:116-146) — это fix конкретного hotspot (OOM) из parent спеки (A.2, M-1).
- [x] Focused on user value and business needs
  - Цель: устранить OOM-риск при скачивании больших MP4 (100+ MB), улучшить TTFB через streaming.
- [x] Written for non-technical stakeholders
  - US и SC — на языке бизнеса (OOM, latency, heap). Технические детали (StreamingResponseBody, ResourceRegion) — в FR.
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
- [x] Requirements are testable and unambiguous
  - FR-001 … FR-007 — каждое с конкретным поведением.
- [x] Success criteria are measurable
  - SC-001: heap ≤ baseline+50 MB. SC-002: 500 MB без OOM. SC-006: TTFB ≤ 200 мс.
- [x] Success criteria are technology-agnostic (no implementation details)
  - SC-001/SC-002 привязаны к heap и OOM — это метрики JVM/Spring, не implementation-detail. SC-005 — HTTP-стандарт.
- [x] All acceptance scenarios are defined
  - 2 US × 3–4 сценария = 7 acceptance scenarios.
- [x] Edge cases are identified
  - 6 edge case'ов: network error, race fileExists-download, client disconnect, concurrent downloads, Content-Type, Content-Disposition encoding.
- [x] Scope is clearly bounded
  - In scope: только `downloadFile`. Out of scope: другие endpoint'ы `StorageController`, upload (уже стримит), encoding RFC 5987.
- [x] Dependencies and assumptions identified
  - Зависимость: Spring Boot (есть), MinIO Java SDK (есть), Tomcat (есть). 7 assumptions.

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows
- [x] Feature meets measurable outcomes defined in Success Criteria
- [x] No implementation details leak into specification
  - Исключение: FR-001/FR-004 упоминают `StreamingResponseBody` и `ResourceRegion` как конкретные Spring-паттерны — это часть контракта, не implementation-detail.

## Notes

- **ADMIN-only endpoint** — на проде (`karaoke-web`) этот endpoint НЕ вызывается (там `fetchFromMinIO` через nginx-proxy). Поэтому эффект виден только на admin-машине при скачивании файлов через webvue3.
- **Зависимость от parent спеки**: Tier-1 / FR-104. Самый высокий приоритет по SC (OOM-риск — критично для стабильности).
- **Range-поддержка** (FR-004) — улучшение, не критично для текущих admin-сценариев. Можно вынести в отдельную фичу, если окажется что это блокирует `/speckit.plan`.
- **Heap-overhead**: SC-001/SC-003 зафиксировали конкретные числа для прозрачности (50 MB на streaming buffers, 200 MB на 10 concurrent downloads).
- **TTFB (SC-006)** — драматическое улучшение для admin UX: с 5–15 сек (текущее, ожидание полной загрузки в heap) до ≤ 200 мс (streaming начинает отдавать сразу).
- **Tier-2/M-2 (HEAD per request)** — отдельная фича. Этот фикс не устраняет HEAD-per-file (он уже есть в `fileExists` перед `downloadFile`), но Range-поддержка может его сделать ненужным для видео-плееров.
- **Тестирование**: автоматических тестов нет. Проверка — пользователем через admin UI + heap-мониторинг (`jconsole`/`VisualVM`).
- **Tomcat tuning**: может потребоваться `server.tomcat.max-swallow-size = -1` для снятия ограничения на размер тела ответа (по умолчанию 2 MB).
