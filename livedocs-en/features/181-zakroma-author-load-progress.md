---
status: Active
slug: 181-zakroma-author-load-progress
related:
  - ../domain/catalog.md
  - ../features/186-zakroma-songs-fast-load.md
  - ../architecture/L3-components.md
  - ../../specs/181-zakroma-author-load-progress/spec.md
---

# 181 — Zakroma: real-time прогресс через NDJSON-стрим (LiveDoc)

> Drill-down — [specs/181-zakroma-author-load-progress/spec.md](../../specs/181-zakroma-author-load-progress/spec.md).

## What it does

При выборе автора в «Закромах» (`/zakroma`) теперь видны **точные проценты
загрузки в реальном времени**, а не крутящийся спиннер. Бэкенд отдаёт песни
порциями через NDJSON chunked-stream (`StreamingResponseBody`), фронт читает
`ReadableStream.getReader()` и показывает «Получено 87 из 234… 37%».

Дополнительно: при выборе нового автора **старый список песен очищается
мгновенно** (синхронно, до ответа бэка), без эффекта «мешанины» из песен
двух авторов.

Кнопка «Отмена» рядом с прогрессометром — `AbortController.abort()` обрывает
fetch. Метрики (`firstChunkMs`, `durationMs`, `streamAborted`) логируются в
`sessionStorage` + батч-отправка в `tbl_events` при `pagehide`.

## User Stories (краткий список)

- **US1** (P1): Моментальная очистка списка песен при выборе нового автора (≤ 50 мс).
- **US2** (P1): Real-time прогресс «Получено X из N… M%» через backend NDJSON-стрим.
- **US3** (P2): Быстрые сценарии (стрим завершился < 300 мс → индикатор не показывается).
- **Force refresh** (P3): повторный клик на того же автора после > 30 с с последней успешной загрузки = refresh.

## Functional Requirements (указатель)

- **FR-BE-001**: Новый endpoint `GET /api/public/zakroma/stream?author=...`.
- **FR-BE-002**: `Content-Type: application/x-ndjson`, `Transfer-Encoding: chunked`.
- **FR-BE-003**: 5 типов сообщений NDJSON (author/done, song/chunk, album/chunk, error, done).
- **FR-NGINX-001**: В `80to8897` для пути `/api/public/zakroma/stream`:
  `proxy_buffering off; gzip off; proxy_cache off; proxy_read_timeout 300s`.
- **FR-FE-001**: `useZakromaStreamProgress` composable (без `setInterval`).
- **FR-FE-002**: cleanup `controller.abort()` в `onBeforeUnmount`.
- **FR-FE-003**: Polling `/api/zakroma/stream` через `AbortController`.
- **FR-FE-004**: Метрики → `sessionStorage` + батч `tbl_events` при `pagehide`.

## Acceptance Criteria

- [ ] **AC1**: При выборе автора список очищается до ответа бэка (≤ 50 мс).
- [ ] **AC2**: Stream виден в DevTools: `Transfer-Encoding: chunked`, `Content-Type: application/x-ndjson`, TTFB ≈ 200 мс.
- [ ] **AC3**: Счётчик растёт синхронно с чанками («Получено 105 из 234… 45%»).
- [ ] **AC4**: При ошибке / Aborted — дружелюбное сообщение + Retry.
- [ ] **AC5**: При `streamAborted=true` (cancel) → метрика сохранена, UI сброшен.

## Related LiveDocs

- Domain: [catalog.md](../domain/catalog.md) (Song, Author — данные стрима)
- Feature: [186-zakroma-songs-fast-load.md](../features/186-zakroma-songs-fast-load.md) (предыдущая оптимизация Закромов)
- Architecture: [L3-components.md](../architecture/L3-components.md) (контроллеры), [nginx](L2-containers.md)

## Code

- Backend: `karaoke-web/src/main/kotlin/.../controllers/PublicApiController.kt` — `zakroma/stream` (StreamingResponseBody)
- Frontend: `karaoke-public/src/composables/useZakromaStreamProgress.ts` (без таймера!)
- Frontend: `karaoke-public/src/store/modules/zakroma.js` (Vuex модуль)
- Frontend: `karaoke-public/src/views/ZakromaView.vue` (прогрессометр + Cancel)
- Nginx: `deploy/web-server-deploy/deploy/80to8897` — `proxy_buffering off; gzip off; proxy_cache off;`
- Метрики: `web/lib/streamMetrics.ts` → `sessionStorage` → `tbl_events` батч

## History

- Created: 2026-08-14
- Last updated: 2026-08-14