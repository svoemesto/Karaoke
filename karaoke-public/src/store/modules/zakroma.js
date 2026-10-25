import { apiGet } from '../../services/api'
import { useZakromaStreamProgress } from '../../composables/useZakromaStreamProgress'
import { releaseStream, shouldReplaceActiveStream } from '../../utils/streamLifecycle.js'
import {
  createGenerationCounter,
  invalidateGeneration,
  isCurrentGeneration,
  nextGeneration,
} from '../../utils/streamGeneration.js'

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

// BR-313 (аудит #336, PKG-S2-14): composable создаётся на КАЖДЫЙ старт стрима, поэтому
// предыдущий экземпляр обязан снять свои подписки (pagehide + два visibilitychange) —
// иначе они копятся по одной тройке на каждую загрузку автора и удерживают полный
// список песен автора в своём замыкании.
let activeStream = null

// BR-356 (аудит #336, PKG-S3-28): поколение стрима.
//
// Раньше store коммитил результат того стрима, который завершился последним, без
// проверки «а тот ли это автор». Сценарий: открыт автор A (стрим идёт), пользователь
// уходит на сетку и открывает автора B — стрим B стартует, а стрим A, завершившись
// позже, делал `setZakroma([A])`: страница `/zakroma/B` показывала песни автора A,
// а его `finally` гасил `isStreaming` (прогресс-бар B исчезал) и показывал ошибку A.
//
// Теперь у каждого стрима есть номер поколения: коммитить данные/ошибку/флаг
// стриминга может ТОЛЬКО текущее поколение. Устаревшее поколение молча уходит,
// освобождая свои ресурсы (cleanup/cancel) в finally.
const streamGeneration = createGenerationCounter()

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
     * @see archive/docs/features/zakroma-stream-progress.md
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
     * @see archive/docs/features/site-traffic-resilience.md (FR-014)
     */
    lastLoadedTilesAt: 0,
    // (Спека 259: lastSongReferrer удалён — SongView читает authorId из SongPublicDto.authorId.)
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
     * @see archive/docs/features/site-traffic-resilience.md (FR-014)
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
    async loadZakromaStream(
      { commit, state },
      { author, expectedCount, force = false, albumId = null },
    ) {
      // FR-FE-009: dedup — проверяем ДО очистки state.zakroma. Иначе при
      // browser back из /song/{id} state очищается, dedup срабатывает,
      // фетча нет → пустая страница. С новой логикой: dedup позволяет
      // UI сохранить данные из state.zakroma (которые лежат в кэше
      // Vuex-Pinia-style store), без нового запроса.
      //
      // Pass 359 fix: при `force=true` (прямой заход на /zakroma/{id}?albumId=...
      // когда state.zakroma пуст) пропускаем dedup — иначе страница остаётся
      // пустой потому что dedup возвращает no-op, а state.zakroma пустой.
      // Pass 359 (albumId): dedup-ключ — `author + albumId`. Кэш автора (без
      // фильтра по альбому) не должен использоваться для запроса с albumId=N
      // (там совсем другие песни).
      const cacheKey = albumId ? `${author}:${albumId}` : author
      const lastTs = state.lastLoadedTimestampByAuthor[cacheKey]
      if (!force && lastTs && Date.now() - lastTs < 30_000) {
        // No-op: state.zakroma уже содержит данные с предыдущей
        // успешной загрузки (тот же автор + albumId, < 30с). UI продолжает
        // работать с тем же zakroma[].
        return
      }

      // BR-356 (аудит #336, PKG-S3-28): это поколение стрима. Только оно вправе
      // коммитить данные/ошибку/флаг стриминга — устаревший (перебитый новым) стрим
      // молча уходит, не подменяя данные чужого автора.
      const generation = nextGeneration(streamGeneration)

      // US1: очистка СИНХРОННО (FR-FE-004). UI сразу видит пустоту.
      commit('setZakroma', [])
      commit('setStreaming', true)
      commit('setStreamProgress', { receivedCount: 0, expectedCount: expectedCount || 0 })
      commit('setStreamError', null)

      // Создаём composable (FR-FE-001) и запускаем стрим. Предыдущий экземпляр (если был)
      // сначала освобождает подписки — см. activeStream выше.
      // BR-320 (аудит #336, PKG-S3-27): новый стим ВСЕГДА заменяет текущий (cancel-and-restart) —
      // два одновременных NDJSON-потока на один публичный hot-path эндпоинт это ровно та
      // двойная нагрузка, которую фиксирует аудит.
      if (shouldReplaceActiveStream(activeStream)) releaseStream(activeStream)
      const composable = useZakromaStreamProgress()
      activeStream = composable
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
        // Pass 359: передаём albumId в стрим-эндпоинт, чтобы бэк фильтровал
        // песни по `tbl_songs.album_id` и не гонял все 388 песен автора.
        const result = await composable.start(author, expectedCount, albumId)
        // BR-356: пока шёл стрим, его мог перебить более новый (другой автор/альбом).
        // Устаревшее поколение НЕ коммитит ничего — иначе на странице `/zakroma/B`
        // появлялись песни автора A.
        if (!isCurrentGeneration(streamGeneration, generation)) return
        // FR-BE-008: actualCount должен совпадать с expectedCount (если фильтр
        // не удалил). UI ничего не показывает, но sanity-check логируем.
        if (result && result.albums) {
          // Преобразуем к формату ZakromaPublicDto: {author, authorPictureUrl, albums: [...]}.
          // У нас нет authorPictureUrl — UI может не показывать картинку,
          // если нету (или взять из кэша?). Для MVP — оставляем пустым.
          // albumTypeCounts — собираем на фронте из полученных albums
          // (mirror backend `ZakromaPublicDto.fromZakroma()`).
          //
          // BR-354 (аудит #336, PKG-S3-28): авторские поля (описание/предупреждение/
          // SKIP) приходят в NDJSON-сообщении `meta` — раньше они терялись при переходе
          // на стрим, и блок автора на странице показывал пустые значения.
          commit('setZakroma', [
            {
              author,
              authorPictureUrl: '',
              authorDescription: result.authorDescription || '',
              authorShortDescription: result.authorShortDescription || '',
              authorWarning: result.authorWarning || '',
              authorSkip: result.authorSkip === true,
              albums: result.albums,
              albumTypeCounts: buildAlbumTypeCounts(result.albums),
            },
          ])
          // Pass 359: ключ дедупа — `author + albumId` (см. cacheKey выше).
          commit('setLastLoadedTimestamp', { author: cacheKey, ts: Date.now() })
        }
      } catch (err) {
        // BR-356: ошибка устаревшего стрима — не наша (страница уже показывает другой
        // автор). Молча уходим, не показывая чужую ошибку.
        if (!isCurrentGeneration(streamGeneration, generation)) return
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
        // Стрим завершён (успех/ошибка/abort) — снимаем подписки и отложенный таймер
        // индикатора; refs после завершения больше не меняются.
        // BR-320: cleanup() обязателен в finally — иначе AbortController и подписки жили
        // до конца вкладки, а первый стрим продолжал коммитить setZakroma после ухода
        // пользователя со страницы.
        releaseStream(composable)
        if (activeStream === composable) activeStream = null
        // BR-356: флаг стриминга гасит только ТЕКУЩЕЕ поколение — иначе завершившийся
        // старый стрим прятал прогресс-бар нового.
        if (isCurrentGeneration(streamGeneration, generation)) {
          commit('setStreaming', false)
        }
      }
    },
    /**
     * BR-320 (аудит #336, PKG-S3-27): НАСТОЯЩАЯ отмена активного NDJSON-стрима.
     *
     * Раньше кнопка «Отмена» на странице лишь ПОВТОРНО диспатчила тот же action без `force`:
     * на первом заходе (дедуп ещё не сработал) это запускало ВТОРОЙ стрим поверх живого
     * первого, а при повторном заходе (< 30 с) не делало вообще ничего. Теперь поток
     * действительно прерывается (AbortController) и состояние сбрасывается.
     */
    cancelZakromaStream({ commit, state }) {
      if (activeStream) {
        releaseStream(activeStream)
        activeStream = null
      }
      // BR-356: отмена инвалидирует текущее поколение — висящий стрим не сможет
      // принести данные/ошибку после отмены.
      invalidateGeneration(streamGeneration)
      commit('setZakroma', [])
      commit('setStreaming', false)
      commit('setStreamProgress', { receivedCount: 0, expectedCount: 0 })
      commit('setStreamError', null)
      // Сбрасываем метку дедупа для ВСЕХ авторов: после отмены следующий заход должен
      // действительно загрузить данные, а не получить no-op по устаревшей метке.
      for (const key of Object.keys(state.lastLoadedTimestampByAuthor || {})) {
        commit('setLastLoadedTimestamp', { author: key, ts: 0 })
      }
    },
    /**
     * Загружает данные для виртуальной плашки "Отдельные песни разных авторов" одним
     * batch-запросом (/api/public/zakroma?specialBucket=true) — бэкенд сам находит всех
     * is_special_order=true авторов и отдаёт их песни одним SQL-запросом. Результат —
     * список ZakromaPublicDto (та же форма, что и для обычного автора, Автор→Альбом→Песни).
     *
     * @see specs/008-special-orders/spec.md
     * @see archive/docs/features/special-orders.md
     */
    async loadSpecialBucket({ commit }) {
      const data = await apiGet('/api/public/zakroma', { specialBucket: true })
      commit('setSpecialBucket', data || [])
    },
  },
}
