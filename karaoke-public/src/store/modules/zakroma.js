import { apiGet } from '../../services/api'
import { useZakromaStreamProgress } from '../../composables/useZakromaStreamProgress'

// Локальный кеш AlbumType-метаданных (mirror из
// karaoke-app/src/main/kotlin/.../AlbumType.kt). Нужен для построения
// albumTypeCounts на фронте после получения NDJSON-стрима (бэк не шлёт
// готовый albumTypeCounts — FR-BE-003 out of scope).
const ALBUM_TYPE_LABELS = {
  studio: { dbValue: 'studio', groupLabel: 'Студийные альбомы', filterLabel: 'Студийные' },
  single: { dbValue: 'single', groupLabel: 'Синглы', filterLabel: 'Синглы' },
  live: { dbValue: 'live', groupLabel: 'Концертные альбомы', filterLabel: 'Концертные' },
  compilation: { dbValue: 'compilation', groupLabel: 'Сборники', filterLabel: 'Сборники' },
  bootleg: { dbValue: 'bootleg', groupLabel: 'Бутлеги', filterLabel: 'Бутлеги' },
  archive: { dbValue: 'archive', groupLabel: 'Архивные записи', filterLabel: 'Архивные' },
  tribute: { dbValue: 'tribute', groupLabel: 'Трибьют/Кавер', filterLabel: 'Трибьют/Кавер' },
}
const ALBUM_TYPE_ZAKROMA_ORDER = [
  'studio',
  'single',
  'live',
  'compilation',
  'bootleg',
  'archive',
  'tribute',
]

/**
 * Построить albumTypeCounts из списка альбомов (mirror
 * `ZakromaPublicDto.fromZakroma()` в Karaoke-web). Только типы с count > 0,
 * в порядке ZAKROMA_GROUP_ORDER.
 */
function buildAlbumTypeCounts(albums) {
  const counts = {}
  for (const alb of albums || []) {
    const dbValue = alb.albumType || 'studio'
    counts[dbValue] = (counts[dbValue] || 0) + 1
  }
  const out = []
  for (const dbValue of ALBUM_TYPE_ZAKROMA_ORDER) {
    if (counts[dbValue] > 0) {
      out.push({
        dbValue,
        groupLabel: ALBUM_TYPE_LABELS[dbValue].groupLabel,
        filterLabel: ALBUM_TYPE_LABELS[dbValue].filterLabel,
        count: counts[dbValue],
      })
    }
  }
  return out
}

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
    /**
     * US4 (FR-014): время последней успешной загрузки `authorTiles`. Используется
     * для правила «no-op, если < 30 с с последнего успеха И массив не пустой».
     * Снижает нагрузку на `/api/public/authors-tiles` для SPA-навигации
     * (вход на /zakroma → переход в /song/{id} → back → /zakroma).
     *
     * @see docs/features/site-traffic-resilience.md (FR-014)
     */
    lastLoadedTilesAt: 0,
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
    lastLoadedTilesAt: (state) => state.lastLoadedTilesAt,
  },
  mutations: {
    setAuthors(state, authors) {
      state.authors = authors
    },
    setAuthorTiles(state, tiles) {
      state.authorTiles = tiles
    },
    setLastLoadedTilesAt(state, ts) {
      state.lastLoadedTilesAt = ts
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
    /**
     * Загрузка тайлов авторов для сетки /zakroma (US4 / FR-014).
     *
     * Дедуп: если < 30 секунд с последней успешной загрузки И `state.authorTiles` не пустой —
     * no-op (без HTTP-запроса). После успешной загрузки — фиксируем `lastLoadedTilesAt`.
     *
     * Это дополняет server-side защиты (FR-002: прямой URL на MinIO минует Spring-контроллер).
     * Без дедупа SPA-навигация (вход → переход в song → back) дёргает `/authors-tiles`
     * 2-3 раза за сессию.
     *
     * @see docs/features/site-traffic-resilience.md (FR-014)
     */
    async loadAuthorTiles({ commit, state }, scope = 'main') {
      // FR-014: dedup — если последний успех был < 30с назад И массив не пустой,
      // не делаем fetch. Пустой массив (например, после первой ошибки загрузки) —
      // НЕ считается кэшированным состоянием, делаем fetch.
      if (
        state.lastLoadedTilesAt > 0 &&
        Date.now() - state.lastLoadedTilesAt < 30_000 &&
        Array.isArray(state.authorTiles) &&
        state.authorTiles.length > 0
      ) {
        return
      }
      const tiles = await apiGet('/api/public/authors-tiles', { scope })
      commit('setAuthorTiles', tiles)
      commit('setLastLoadedTilesAt', Date.now())
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
      // FR-FE-009: dedup — проверяем ДО очистки state.zakroma. Иначе при
      // browser back из /song/{id} state очищается, dedup срабатывает,
      // фетча нет → пустая страница. С новой логикой: dedup позволяет
      // UI сохранить данные из state.zakroma (которые лежат в кэше
      // Vuex-Pinia-style store), без нового запроса.
      const lastTs = state.lastLoadedTimestampByAuthor[author]
      if (lastTs && Date.now() - lastTs < 30_000) {
        // No-op: state.zakroma уже содержит данные с предыдущей
        // успешной загрузки (тот же автор, < 30с). UI продолжает
        // работать с тем же zakroma[].
        return
      }

      // US1: очистка СИНХРОННО (FR-FE-004). UI сразу видит пустоту.
      commit('setZakroma', [])
      commit('setStreaming', true)
      commit('setStreamProgress', { receivedCount: 0, expectedCount: expectedCount || 0 })
      commit('setStreamError', null)

      // Создаём composable (FR-FE-001) и запускаем стрим.
      const composable = useZakromaStreamProgress()
      // Передаём прогресс в state.streamProgress через watcher на composable refs.
      // Vuex-совместимо: используем watch из 'vue'.
      const { watch: vueWatch } = await import('vue')
      const stopProgress = vueWatch(
        () => ({
          receivedCount: composable.receivedCount.value,
          expectedCount: composable.expectedCount.value,
        }),
        (val) => commit('setStreamProgress', val),
        { deep: true, immediate: true },
      )

      try {
        const result = await composable.start(author, expectedCount)
        // FR-BE-008: actualCount должен совпадать с expectedCount (если фильтр
        // не удалил). UI ничего не показывает, но sanity-check логируем.
        if (result && result.albums) {
          // Преобразуем к формату ZakromaPublicDto: {author, authorPictureUrl, albums: [...]}.
          // У нас нет authorPictureUrl — UI может не показывать картинку,
          // если нету (или взять из кэша?). Для MVP — оставляем пустым.
          // albumTypeCounts — собираем на фронте из полученных albums
          // (mirror backend `ZakromaPublicDto.fromZakroma()`).
          commit('setZakroma', [
            {
              author,
              authorPictureUrl: '',
              albums: result.albums,
              albumTypeCounts: buildAlbumTypeCounts(result.albums),
            },
          ])
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
        stopProgress()
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
