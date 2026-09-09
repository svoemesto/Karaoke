# Composable: useZakromaStreamProgress

> **Домен**: system (frontend)
> **Компонента**: `karaoke-public/src/composables/useZakromaStreamProgress.js`
> — real-time NDJSON stream parser.

## Файл

`karaoke-public/src/composables/useZakromaStreamProgress.js` (488 строк)

## Назначение

**Real-time NDJSON-stream parser** для `/api/public/zakroma/stream`
(FR-FE-001).

Заменяет синхронный `useZakromaLoadProgress` (если был) на **реальный**
прогресс из backend chunked-stream endpoint.

## Refs (экспортируются)

```javascript
const isVisible = ref(false)              // показывать ли прогресс-бар
const progress = ref(0)                    // 0..100
const receivedCount = ref(0)              // сколько альбомов уже пришло
const expectedCount = ref(0)              // сколько ждём
const errorMessage = ref(null)
const albums = ref([])                    // локальный буфер полученных альбомов
```

## Methods (экспортируются)

```javascript
start(author, expectedCount)     // запуск stream-парсинга
cancel()                          // AbortController.abort()
cleanup()                        // для onBeforeUnmount
```

## FR-контракт (FR-FE-*)

| FR | Что |
|---|---|
| FR-FE-001 | Экспортирует refs + methods (см. выше) |
| FR-FE-004 | `start()` **синхронно** очищает локальный буфер + refs ДО fetch |
| FR-FE-007 | cleanup AbortController на уходе со страницы |
| FR-FE-008 | **НИКАКОГО `setInterval`** — прогресс полностью из реальных чанков |
| FR-FE-010 | Метрики в `sessionStorage[km_zakroma_stream_metrics]`, `pagehide` → `sendBeacon` (fallback `fetch + keepalive`) в `POST /api/public/zakroma/stream/metrics` |
| FR-FE-011 | aria-live throttle через `requestAnimationFrame` (флаг `rafThrottleFlag`) |

## Метрики (FR-FE-010)

```javascript
const STREAM_METRICS_KEY = 'km_zakroma_stream_metrics'
// В sessionStorage: { author, startedAt, completedAt, receivedCount, errors }
// На pagehide: sendBeacon / fetch+keepalive
```

## Domain Invariants

1. **Прогресс ТОЛЬКО из реальных чанков** (FR-FE-008) — нет
   fake-progress.
2. **AbortController** — для каждого `start()` новый, для `cleanup()`
   abort.
3. **Метрики обязательны** (FR-FE-010) — для backend-аналитики.

## Hot paths

- **`/api/public/zakroma/stream`** — long-running chunked stream
  (NDJSON).

## Связь

- **PublicZakromaController** ([karaoke-web/public-controllers.md](../../domains/karaoke-web/components/public-controllers.md)) —
  `/api/public/zakroma/stream`.
- **Закрома (cross-tab)** ([entities-catalog.md#crosssong-cross-tab-helper-reporting](../../domains/catalog/components/entities-catalog.md#crosssong-cross-tab-helper-reporting)) — данные.

## Changelog

- **Pass 387** (2026-09-09): Initial. Автор: agent (Karaoke).