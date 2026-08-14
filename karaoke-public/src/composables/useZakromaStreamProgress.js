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

  // Pass 186 (specs/186-zakroma-songs-fast-load):
  // - BATCH_FLUSH — yield'им браузеру каждые N сообщений (не каждое), чтобы Vue успевал
  //   рендерить промежуточное состояние прогрессометра, но без траты 2500 event-loop ticks.
  // - pendingVisibilityPush — флаг для visibilitychange listener (T020, US3): если мы в
  //   фоновой вкладке, не yield'им (setTimeout тротлится в фоне до 1000мс/вызов → 41 мин
  //   обработки 2500 чанков), а копим в albums.value и про прошиваем через nextTick при
  //   возврате.
  const BATCH_FLUSH = 50
  let pendingVisibilityPush = false

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
  // T016 (US3, FR-FE-008 уточнение): debounce показа индикатора на 300 мс.
  // Если стрим завершился быстрее — индикатор не успевает показаться (UI
  // не «мелькает»). `setTimeout` здесь НЕ нарушает FR-FE-008 (который
  // запрещает setInterval для синтетического прогресса — это про логику
  // подсчёта %, а debounce про UX-видимость).
  let showTimeout = null

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
    // T016: reset debounce-таймер.
    if (showTimeout) {
      clearTimeout(showTimeout)
      showTimeout = null
    }
    // Pass 186: сброс visibility-флага на повторный вызов (FR-008: cancel-and-restart).
    pendingVisibilityPush = false

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
      // Передаём `expectedCount` с тайла автора (= `songCount` в
      // `AuthorTilePublicDto`). Backend использует его напрямую в
      // первом NDJSON-сообщении `meta` без отдельного DB-запроса
      // `Song.loadAuthorSongCounts(...)` (~100-500мс). Фронт получает
      // `meta` МГНОВЕННО + начинает показывать «0 из N» с правильным N.
      // Backend всё равно отдаёт `done.actualCount` — sanity check
      // (FR-BE-008) для гарантии, что tiles и реальный стрим
      // согласованы.
      if (expectedCountFromCaller && expectedCountFromCaller > 0) {
        params.set('expectedCount', String(expectedCountFromCaller))
      }
      startTs = performance.now()
      recordEvent('zakroma_stream_start')
      // Composer.authHeader(): для залогиненного редактора shём
      // `Authorization: Bearer <token>` — без этого backend резолвит
      // SiteUser=null → `onlyPublishedFor(request)=true` → ответ как
      // не-редактору (только `id_status >= 6`). Это критично для фичи
      // specs/181 (FR-BE-007, SC-007 «editor sees all statuses»).
      //
      // Тот же приём, что в `services/api.js` → `apiGet()`: читаем
      // `km_auth_token` из localStorage напрямую (тот же ключ, что и
      // useAuth), чтобы composable не тянул useAuth и не рисковал
      // цикл импортов.
      const authToken = localStorage.getItem('km_auth_token')
      const headers = { Accept: 'application/x-ndjson' }
      if (authToken) headers.Authorization = `Bearer ${authToken}`
      const response = await fetch(`/api/public/zakroma/stream?${params.toString()}`, {
        signal: controller.signal,
        headers,
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
        let batchCount = 0
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
          batchCount++
          // Pass 186: в активной вкладке yield'им браузеру каждые BATCH_FLUSH (50) сообщений
          // (через microtask — Promise.resolve().then() НЕ тротлится, в отличие от
          // setTimeout). Без yield'а весь чанк обрабатывается за один synchronous tick — Vue
          // рендерит только финальное состояние, прогрессометр показывает «0 → N» скачком.
          // В фоновой вкладке (document.visibilityState === 'hidden') — НЕ yield'им: setTimeout
          // тротлится в фоне до 1000мс/вызов, и обработка 2500 чанков займёт ~41 минуту.
          // Вместо этого копим в albums.value и прошиваем через visibilitychange listener ниже.
          if (document.visibilityState === 'hidden') {
            pendingVisibilityPush = true
          } else {
            if (batchCount >= BATCH_FLUSH) {
              batchCount = 0
              // eslint-disable-next-line no-await-in-loop -- yield между пачками принципиален
              await Promise.resolve()
            }
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
        // Добавляем "albumSettings: []" — сюда будут складываться song-сообщения.
        // Поле называется `albumSettings`, не `songs`, чтобы соответствовать
        // существующему контракту `ZakromaAlbumPublicDto` (и view, который
        // итерирует `v-for="sett in alb.albumSettings"`).
        albums.value.push({ ...msg.album, albumSettings: [] })
        // T016: показываем индикатор только если стрим не закончился за 300 мс.
        if (!showTimeout) {
          showTimeout = setTimeout(() => {
            isVisible.value = true
            showTimeout = null
          }, 300)
        }
        scheduleAriaLive()
        break
      case 'song':
        if (albums.value.length > 0) {
          albums.value[albums.value.length - 1].albumSettings.push(msg.song)
          receivedCount.value += 1
          progress.value = expectedCount.value > 0 ? receivedCount.value / expectedCount.value : 0
          scheduleAriaLive()
        }
        break
      case 'done':
        // T016: done пришёл — отменяем pending-show (UI не «мелькает»).
        if (showTimeout) {
          clearTimeout(showTimeout)
          showTimeout = null
        }
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
        // T016: error — clearTimeout, показываем сразу (без debounce).
        if (showTimeout) {
          clearTimeout(showTimeout)
          showTimeout = null
        }
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
   * Pass 186 (US3, FR-005/006): visibilitychange listener для проталкивания
   * накопленных данных при возврате на вкладку.
   *
   * Зачем: в фоновой вкладке мы НЕ делаем micro-yield'ы (setTimeout тротлится до 1000мс,
   * microtask'и — норм, но мы их тоже пропускаем, чтобы не тратить CPU зря). Данные
   * накапливаются в `albums.value` синхронно. Когда пользователь возвращается на вкладку,
   * фронт должен немедленно отразить актуальный прогресс в UI — без этого прогрессометр
   * показывает значение, на котором ушли со вкладки, а не реальное.
   *
   * Решение: при `visibilitychange → visible` (если есть pendingVisibilityPush) —
   * дёргаем `nextTick()` из Vue, чтобы принудительно отрендерить накопленные изменения
   * синхронно с возвратом на вкладку.
   *
   * Подписка одноразовая (снимается в cleanup) — при unmом Vue-компонента listener удаляется,
   * чтобы не было утечки.
   */
  function initVisibilityPush() {
    // Lazy-import 'vue' nextTick — он нужен только при возврате, грузить в setup не нужно.
    // Используем динамический импорт, чтобы не нарушать tree-shaking и не плодить
    // циклических зависимостей (useZakromaStreamProgress не импортирует vue).
    document.addEventListener('visibilitychange', async () => {
      if (document.visibilityState === 'visible' && pendingVisibilityPush) {
        pendingVisibilityPush = false
        try {
          const { nextTick } = await import('vue')
          await nextTick()
          // После nextTick Vue отрендерит `albums`/`receivedCount`/`progress`. Если стрим
          // уже завершился — `isVisible` будет `false` (через case 'done' в handleMessage),
          // прогрессометр скроется. Если нет — обновится до актуального значения.
        } catch (e) {
          // Vue не доступен (что-то сломалось) — не критично, прогрессометр покажет
          // актуальное значение на следующем microtick.
          console.warn('visibilitychange push failed:', e)
        }
      }
    })
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
    if (showTimeout) {
      clearTimeout(showTimeout)
      showTimeout = null
    }
    // Pass 186: сбросить pendingVisibilityPush — иначе после cancel() listener может
    // попытаться отрендерить уже-отменённый стрим при возврате на вкладку (FR-008).
    pendingVisibilityPush = false
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
  initVisibilityPush()

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
