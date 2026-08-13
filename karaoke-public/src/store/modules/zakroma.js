import { apiGet } from '../../services/api'
import { useZakromaStreamProgress } from '../../composables/useZakromaStreamProgress'

/**
 * Компонент «Zakroma».
 *
 * @see AGENTS.md
 * @see specs/008-special-orders/spec.md — виртуальная плашка «Отдельные песни разных авторов»
 * @see specs/181-zakroma-author-load-progress/spec.md — real-time NDJSON chunked-stream progress
 */

export default {
  namespaced: true,
  state: {
    authors: [],
    authorTiles: [],
    zakroma: [],
    /**
     * Виртуальная плашка в конце Закромов: данные по спецзаказным авторам (1-2 песни каждый).
     * @see specs/008-special-orders
     */
    specialBucket: [],
    /**
     * Заменено с `isLoading` на `isStreaming` (181) — показывает, что активен
     * chunked-stream endpoint, а не просто «не пустой ответ на /zakroma».
     */
    isStreaming: false,
    /**
     * Прогресс текущего стрима (181, FR-FE-001). Заполняется composable
     * useZakromaStreamProgress во время fetch.
     *
     * @see docs/features/zakroma-stream-progress.md
     */
    streamProgress: { receivedCount: 0, expectedCount: 0 },
    /**
     * Текст ошибки стрима (181, FR-FE-001). `null` = нет ошибки.
     */
    streamError: null,
    /**
     * `Date.now()` последней успешной загрузки для каждого автора (181, FR-FE-009).
     * Используется для правила «no-op, если < 30 с с последнего успеха».
     */
    lastLoadedTimestampByAuthor: {},
  },
  getters: {
    authors: (state) => state.authors,
    authorTiles: (state) => state.authorTiles,
    zakroma: (state) => state.zakroma,
    specialBucket: (state) => state.specialBucket,
    isStreaming: (state) => state.isStreaming,
    streamProgress: (state) => state.streamProgress,
    streamError: (state) => state.streamError,
    lastLoadedTimestampByAuthor: (state) => state.lastLoadedTimestampByAuthor,
  },
  mutations: {
    setAuthors(state, authors) {
      state.authors = authors
    },
    setAuthorTiles(state, tiles) {
      state.authorTiles = tiles
    },
    setZakroma(state, zakroma) {
      state.zakroma = zakroma
    },
    setSpecialBucket(state, zakroma) {
      state.specialBucket = zakroma
    },
    setStreaming(state, value) {
      state.isStreaming = value
    },
    setStreamProgress(state, progress) {
      state.streamProgress = progress
    },
    setStreamError(state, error) {
      state.streamError = error
    },
    setLastLoadedTimestamp(state, { author, ts }) {
      state.lastLoadedTimestampByAuthor = {
        ...state.lastLoadedTimestampByAuthor,
        [author]: ts,
      }
    },
  },
  actions: {
    async loadAuthors({ commit }, scope = 'main') {
      const authors = await apiGet('/api/public/authors', { scope })
      commit('setAuthors', authors)
    },
    async loadAuthorTiles({ commit }, scope = 'main') {
      const tiles = await apiGet('/api/public/authors-tiles', { scope })
      commit('setAuthorTiles', tiles)
    },
    /**
     * Real-time NDJSON chunked-stream loader (181, FR-FE-003, FR-FE-004, FR-FE-009).
     *
     * Перед стартом синхронно очищает `state.zakroma` (US1: 50мс SC-001),
     * создаёт composable, вызывает `start(author, expectedCount)`. По приходу
     * `done` сообщения — `commit('setZakroma', parsedAlbums)`.
     *
     * FR-FE-009: если < 30с с последней успешной загрузки этого автора —
     * no-op (без нового fetch). Через > 30с — force refresh.
     *
     * @param {string} author имя автора
     * @param {number} expectedCount число песен с тайла (= `selectedAuthor.songCount`)
     * @returns {Promise<void>}
     */
    async loadZakromaStream({ commit, state }, { author, expectedCount }) {
      // US1: очистка СИНХРОННО (FR-FE-004). UI сразу видит пустоту.
      commit('setZakroma', [])
      commit('setStreaming', true)
      commit('setStreamProgress', { receivedCount: 0, expectedCount: expectedCount || 0 })
      commit('setStreamError', null)

      // FR-FE-009: dedup — если < 30с с последней успешной загрузки, no-op.
      const lastTs = state.lastLoadedTimestampByAuthor[author]
      if (lastTs && Date.now() - lastTs < 30_000) {
        // Тихо восстанавливаем UI из последнего стрима (но не делаем fetch).
        // На текущей фазе useZakromaStreamProgress не помнит результаты — skip update.
        commit('setStreaming', false)
        return
      }

      // Создаём composable (FR-FE-001) и запускаем стрим.
      const composable = useZakromaStreamProgress()
      try {
        const result = await composable.start(author, expectedCount)
        // Полное обновление albums + zаркома при приходе `done` —
        // реализуется в T013 (Phase 4). Здесь — заглушка: ничего не делаем,
        // composable resolver кидает ошибку reject для cancel() / network().
        if (result && result.albums) {
          commit('setZakroma', result.albums)
          commit('setLastLoadedTimestamp', { author, ts: Date.now() })
        }
      } catch (err) {
        // Любая ошибка стрима (network/error/abort) — фиксируем в state.
        // UI использует streamError для retry-кнопки (FR-FE-001 сценарий 4).
        if (err && err.code === 'aborted') {
          // Cancel — НЕ ошибка, сбрасываем UI.
          commit('setZakroma', [])
          commit('setStreamError', null)
        } else {
          commit('setStreamError', (err && err.message) || 'Не удалось загрузить песни автора')
        }
        // При ошибке — сбрасываем lastLoadedTimestamp, чтобы следующий клик
        // прошёл как force refresh.
        commit('setLastLoadedTimestamp', { author, ts: 0 })
      } finally {
        commit('setStreaming', false)
      }
    },
    /**
     * Загружает данные для виртуальной плашки "Отдельные песни разных авторов" одним
     * batch-запросом (/api/public/zakroma?specialBucket=true) — бэкенд сам находит всех
     * is_special_order=true авторов и отдаёт их песни одним SQL-запросом. Результат —
     * список ZakromaPublicDto (та же форма, что и для обычного автора, Автор→Альбом→Песни).
     *
     * @see specs/008-special-orders/spec.md
     * @see docs/features/special-orders.md
     */
    async loadSpecialBucket({ commit }) {
      const data = await apiGet('/api/public/zakroma', { specialBucket: true })
      commit('setSpecialBucket', data || [])
    },
  },
}
