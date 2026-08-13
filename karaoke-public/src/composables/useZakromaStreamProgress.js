import { ref } from 'vue'
import { getAnonId } from '../services/clientId'
import { consumeEntryReferrer } from '../services/entryReferrer'

const STREAM_METRICS_KEY = 'km_zakroma_stream_metrics'

/**
 * Real-time NDJSON-stream parser для /api/public/zakroma/stream (FR-FE-001).
 *
 * Заменяет синхронный `useZakromaLoadProgress` (если был) на **реальный** прогресс
 * из backend chunked-stream endpoint. Подробности:
 *
 * - **FR-FE-001**: экспортирует refs `isVisible`, `progress`, `receivedCount`,
 *   `expectedCount`, `errorMessage` + `albums` (локальный буфер) + methods
 *   `start(author, expectedCount)`, `cancel()`, `cleanup()` (для onBeforeUnmount).
 * - **FR-FE-004**: `start()` **синхронно** очищает локальный буфер + refs ДО fetch.
 * - **FR-FE-007**: cleanup AbortController на уходе со страницы.
 * - **FR-FE-008**: НИКАКОГО `setInterval` — прогресс полностью из реальных чанков.
 * - **FR-FE-010**: метрики в `sessionStorage[km_zakroma_stream_metrics]`,
 *   `pagehide` → `sendBeacon` (fallback `fetch + keepalive`) в
 *   `POST /api/public/zakroma/stream/metrics`.
 * - **FR-FE-011**: aria-live throttle через `requestAnimationFrame` (флаг
 *   `rafThrottleFlag`).
 *
 * @see docs/features/zakroma-stream-progress.md
 * @see specs/181-zakroma-author-load-progress/spec.md
 */
export function useZakromaStreamProgress() {
  const isVisible = ref(false)
  const progress = ref(0)
  const receivedCount = ref(0)
  const expectedCount = ref(0)
  const errorMessage = ref(null)
  const albums = ref([])

  let controller = null
  let resolveResult = null
  let rejectResult = null
  let settled = false
  let rafThrottleFlag = false
  // Метрики (FR-FE-010).
  let startTs = 0
  let firstChunkTs = 0
  let streamAborted = false
  let errorCategory = null
  const metricsBatch = []
  let currentAuthor = ''
  let currentExpectedCount = 0

  function cleanup() {
    if (controller) {
      controller.abort()
      controller = null
    }
  }

  /**
   * Запуск нового стрима. Возвращает Promise<{albums: Array, author: string}>.
   */
  async function start(author, expectedCountFromCaller) {
    // 1. Синхронно очищаем state (FR-FE-004, SC-001 ≤ 50 мс).
    albums.value = []
    receivedCount.value = 0
    expectedCount.value = expectedCountFromCaller || 0
    progress.value = 0
    errorMessage.value = null
    settled = false
    streamAborted = false
    errorCategory = null
    firstChunkTs = 0
    currentAuthor = author || ''
    currentExpectedCount = expectedCountFromCaller || 0

    // 2. Создаём AbortController (FR-FE-007).
    controller = new AbortController()
    const resultPromise = new Promise((resolve, reject) => {
      resolveResult = resolve
      rejectResult = reject
    })

    // 3. Запускаем fetch + NDJSON парсер.
    try {
      const params = new URLSearchParams({
        author: author || '',
        anonId: getAnonId(),
        referrer: consumeEntryReferrer() || '',
      })
      startTs = performance.now()
      recordEvent('zakroma_stream_start')
      const response = await fetch(`/api/public/zakroma/stream?${params.toString()}`, {
        signal: controller.signal,
        headers: { Accept: 'application/x-ndjson' },
      })

      if (!response.ok) {
        // 5xx / 4xx — fetch сам бросает на network error обычно, но на всякий
        // случай (если backend случайно отдал 500) — catch здесь.
        throw new Error(`HTTP ${response.status}`)
      }
      if (!response.body) {
        throw new Error('No response body')
      }

      const reader = response.body.getReader()
      const decoder = new TextDecoder('utf-8')
      let buffer = ''

      // eslint-disable-next-line no-constant-condition -- stream loop
      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        if (firstChunkTs === 0) {
          firstChunkTs = performance.now()
        }
        buffer += decoder.decode(value, { stream: true })
        // NDJSON: split по '\n'. Если в одном чанке несколько строк — все ок.
        let nlIdx
        // eslint-disable-next-line no-cond-assign
        while ((nlIdx = buffer.indexOf('\n')) !== -1) {
          const line = buffer.slice(0, nlIdx).trim()
          buffer = buffer.slice(nlIdx + 1)
          if (!line) continue
          try {
            handleMessage(JSON.parse(line))
          } catch (e) {
            // Битый JSON — игнорируем одну строку, продолжаем.
            console.warn('NDJSON parse error:', e, 'line:', line)
          }
        }
      }
      // Финальный остаток в буфере (если стрим оборвался без \n) — игнорируем.
      if (buffer.trim()) {
        try {
          handleMessage(JSON.parse(buffer))
        } catch (_) {
          // ignore
        }
      }
    } catch (err) {
      handleError(err)
    }

    return resultPromise
  }

  /**
   * Обработка одного NDJSON-сообщения (FR-BE-003, 5 типов).
   */
  function handleMessage(msg) {
    if (!msg || typeof msg.type !== 'string') return
    switch (msg.type) {
      case 'meta':
        // expectedCount мог прийти как ожидаемое — перезаписываем локальный
        expectedCount.value = msg.expectedCount || 0
        break
      case 'album':
        // Добавляем "songs: []" — сюда будут складываться song-сообщения.
        albums.value.push({ ...msg.album, songs: [] })
        isVisible.value = true
        scheduleAriaLive()
        break
      case 'song':
        if (albums.value.length > 0) {
          albums.value[albums.value.length - 1].songs.push(msg.song)
          receivedCount.value += 1
          progress.value = expectedCount.value > 0 ? receivedCount.value / expectedCount.value : 0
          scheduleAriaLive()
        }
        break
      case 'done':
        recordEvent('zakroma_stream_done')
        if (!settled) {
          settled = true
          isVisible.value = false
          if (resolveResult) {
            resolveResult({
              albums: albums.value,
              author: currentAuthor,
              expectedCount: currentExpectedCount,
              actualCount: msg.actualCount,
            })
          }
        }
        break
      case 'error':
        recordEvent('zakroma_stream_error', { errorCategory: msg.message || 'unknown' })
        throw new Error(msg.message || 'stream error')
      // eslint-disable-next-line no-fallthrough -- throw прыгает в catch ниже
      default:
        // неизвестный тип — игнорируем
        break
    }
  }

  /**
   * Обработка ошибки стрима.
   */
  function handleError(err) {
    if (settled) return
    settled = true
    isVisible.value = false
    if (err && (err.name === 'AbortError' || streamAborted)) {
      // Cancel — НЕ ошибка, сбрасываем state.
      recordEvent('zakroma_stream_abort')
      if (rejectResult) {
        rejectResult({ code: 'aborted', message: 'Загрузка отменена' })
      }
    } else {
      errorCategory = (err && err.message) || 'unknown'
      errorMessage.value = 'Не удалось загрузить песни автора'
      if (rejectResult) {
        rejectResult({ code: 'network', message: errorMessage.value })
      }
    }
  }

  /**
   * Throttle aria-live updates через rAF (FR-FE-011): screen reader
   * не должен зачитывать каждое чанк-обновление (50+ раз/сек).
   */
  function scheduleAriaLive() {
    if (rafThrottleFlag) return
    rafThrottleFlag = true
    requestAnimationFrame(() => {
      rafThrottleFlag = false
      // Сам факт rAF-callback — это момент, когда UI «запоминает» новое
      // значение `receivedCount` (render уже произошёл). Screen reader
      // получит обновление через aria-live="polite" на следующем тике.
    })
  }

  /**
   * Метрика (FR-FE-010). Фиксирует событие в `metricsBatch`, который
   * отправляется в `POST /api/public/zakroma/stream/metrics` на `pagehide`.
   */
  function recordEvent(eventType, extra = {}) {
    const now = performance.now()
    const entry = {
      eventType,
      author: currentAuthor,
      firstChunkMs: firstChunkTs > 0 ? Math.round(firstChunkTs - startTs) : null,
      durationMs: Math.round(now - startTs),
      expectedCount: currentExpectedCount,
      receivedCount: receivedCount.value,
      streamAborted,
      errorCategory,
      ...extra,
    }
    metricsBatch.push(entry)
    persistMetrics()
  }

  /**
   * Сохранение текущего батча в sessionStorage (на случай закрытия
   * вкладки/окна без срабатывания pagehide — браузер всё равно
   * синхронно закоммитит sessionStorage).
   */
  function persistMetrics() {
    try {
      sessionStorage.setItem(STREAM_METRICS_KEY, JSON.stringify(metricsBatch))
    } catch (_) {
      // sessionStorage переполнен — отбрасываем по одному самые старые.
      if (metricsBatch.length > 50) metricsBatch.shift()
    }
  }

  /**
   * Инициализация batch collectа: подписываемся на pagehide и visibilitychange.
   * Вызывается один раз на load каждой композиции (через setup() Vue).
   */
  function initMetricsCollection() {
    window.addEventListener('pagehide', flushMetrics)
    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'hidden') flushMetrics()
    })
  }

  /**
   * Отправка батча в backend. Вызывается на pagehide.
   */
  function flushMetrics() {
    if (metricsBatch.length === 0) return
    const payload = JSON.stringify(metricsBatch)
    let sent = false
    // 1. sendBeacon (гарантированно работает на pagehide).
    try {
      if (navigator.sendBeacon) {
        const blob = new Blob([payload], { type: 'application/json' })
        sent = navigator.sendBeacon('/api/public/zakroma/stream/metrics', blob)
      }
    } catch (_) {
      // ignore
    }
    // 2. Fallback fetch + keepalive (если sendBeacon не справился — payload > 64 KB).
    if (!sent) {
      try {
        fetch('/api/public/zakroma/stream/metrics', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: payload,
          keepalive: true,
        }).catch(() => {
          // silent — best effort
        })
      } catch (_) {
        // ignore
      }
    }
    metricsBatch.length = 0
    try {
      sessionStorage.removeItem(STREAM_METRICS_KEY)
    } catch (_) {
      // ignore
    }
  }

  function cancel() {
    if (controller) {
      streamAborted = true
      controller.abort()
      controller = null
    }
    if (!settled) {
      settled = true
      isVisible.value = false
      if (rejectResult) {
        rejectResult({ code: 'aborted', message: 'Загрузка отменена' })
      }
    }
  }

  // Single-shot init: подписки на pagehide / visibilitychange.
  initMetricsCollection()

  return {
    isVisible,
    progress,
    receivedCount,
    expectedCount,
    errorMessage,
    albums,
    start,
    cancel,
    cleanup,
  }
}
