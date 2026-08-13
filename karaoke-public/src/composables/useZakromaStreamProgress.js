import { ref } from 'vue'
import { getAnonId } from '../services/clientId'
import { consumeEntryReferrer } from '../services/entryReferrer'

/**
 * Real-time NDJSON-stream parser для /api/public/zakroma/stream.
 *
 * Заменяет ранее запланированный `useZakromaLoadProgress` (с синтетическим
 * таймером) на **реальный** прогресс из backend chunked-stream endpoint.
 *
 * FR-FE-001: экспортирует refs для UI (`isVisible`, `progress`, `receivedCount`,
 * `expectedCount`, `errorMessage`) + метод `start(author, expectedCount)` /
 * `cancel()` + Promise с финальным результатом.
 *
 * FR-FE-004: `start()` **синхронно** очищает локальный буфер альбомов
 * (вместе с локальным `expectedCount = 0`), ПОТОМ создаёт AbortController
 * и запускает fetch. Это даёт посетителю мгновенное «очищение» предыдущего
 * автора (US1: 50 мс SC) — задолго до прихода первого chunked-сообщения.
 *
 * FR-FE-007: `onBeforeUnmount(() => cleanup())` — на уходе со страницы fetch
 * прерывается, ресурсы освобождаются.
 *
 * FR-FE-008: НИКАКОГО `setInterval` — прогресс полностью из реальных
 * chunked-сообщений backend. Throttle для aria-live — через RAF
 * (см. T013, реализация добавляется поэтапно).
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

  // Локальный буфер результирующих альбомов (= ZakromaPublicDto без albumTypeCounts).
  const albums = ref([])

  // Активный fetch.
  let controller = null
  // Текущая типизация abort — promise rejection при ошибке/cancel.
  // eslint-disable-next-line no-unused-vars -- используется в T013 при расширении парсера
  let resolveResult = null
  let rejectResult = null
  // Защита от двойного resolve/reject.
  let settled = false

  function cleanup() {
    if (controller) {
      controller.abort()
      controller = null
    }
  }

  /**
   * Запустить стрим. Возвращает Promise `Promise<{albums: Array, author: string}>`,
   * который резолвится при `done` сообщении и реджектится при `error`/abort.
   */
  function start(author, expectedCountFromCaller) {
    // 1. Синхронно очищаем состояние (FR-FE-004).
    //    Это MUST быть синхронно — посетитель видит «очистку» до fetch.
    albums.value = []
    receivedCount.value = 0
    expectedCount.value = expectedCountFromCaller || 0
    progress.value = 0
    errorMessage.value = null
    settled = false

    // 2. Создаём AbortController.
    controller = new AbortController()

    const resultPromise = new Promise((resolve, reject) => {
      resolveResult = resolve
      rejectResult = reject
    })

    // 3. Запускаем fetch. Полный NDJSON-парсер + UI прогресс + метрики
    //    добавляются в T013 (Phase 4). На этом этапе — заглушка: fetch
    //    активен, AbortController создан, кириллица URL escape работает.
    const params = new URLSearchParams({
      author: author || '',
      anonId: getAnonId(),
      referrer: consumeEntryReferrer() || '',
    })
    fetch(`/api/public/zakroma/stream?${params.toString()}`, {
      signal: controller.signal,
      headers: { Accept: 'application/x-ndjson' },
    }).catch((err) => {
      // Сетевые ошибки / 5xx / abort — handled в T013-MVP (Phase 4).
      // Здесь сохраняем controller abort как основной источник ошибок.
      if (!settled) {
        settled = true
        if (err && err.name === 'AbortError') {
          rejectResult({ code: 'aborted', message: 'Загрузка отменена' })
        } else {
          errorMessage.value = 'Не удалось загрузить песни автора'
          rejectResult({ code: 'network', message: errorMessage.value })
        }
      }
    })

    return resultPromise
  }

  function cancel() {
    if (controller) {
      controller.abort()
      controller = null
    }
    if (!settled) {
      settled = true
      albums.value = []
      receivedCount.value = 0
      progress.value = 0
      errorMessage.value = null
      isVisible.value = false
      if (rejectResult) {
        rejectResult({ code: 'aborted', message: 'Загрузка отменена' })
      }
    }
  }

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
