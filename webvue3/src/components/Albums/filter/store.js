import { setWebvueProp } from '../../../lib/utils'

// BR-366 (аудит #336, пакет PKG-S3-31, OpenProject #403).
//
// ДЕФЕКТ. Server-side persistence фильтра (принятый паттерн — см.
// knowledge/system/frontend/filter-stores.md § Persistence) был реализован в мутациях:
// каждая мутация вызывала `setWebvueProp`, который сразу отправлял
// `POST /api/setwebvueprop`. Мутации вызываются из `v-model`-сеттеров модалки
// (`AlbumsFilterModal.vue`) на КАЖДОЕ нажатие клавиши: ввод 30-символьного названия —
// 30 POST'ов подряд. Запросы не упорядочены и не отменялись (при перестановке ответов
// на сервере оставался более ранний префикс строки), а ошибка записи не обрабатывалась
// (необработанный промис).
//
// РЕШЕНИЕ. Запись фильтра на сервер — по действию пользователя («Применить»), а не на
// каждый `input`: мутации стали чистыми (только state), а запись вынесена в actions
// `persistAlbumsFilter` (с обработкой отказа) и `restoreAlbumsFilter` (чтение без
// записи — прежняя «восстановляющая» запись тех же значений тоже была лишней).

/**
 * Ключи настроек фильтра альбомов (совпадают с именами полей в state).
 *
 * @type {string[]}
 */
const ALBUMS_FILTER_KEYS = [
  { stateKey: 'albumsFilterId', mutation: 'setAlbumsFilterId' },
  { stateKey: 'albumsFilterAuthorName', mutation: 'setAlbumsFilterAuthorName' },
  { stateKey: 'albumsFilterYear', mutation: 'setAlbumsFilterYear' },
  { stateKey: 'albumsFilterName', mutation: 'setAlbumsFilterName' },
  { stateKey: 'albumsFilterAlbumType', mutation: 'setAlbumsFilterAlbumType' },
  { stateKey: 'albumsFilterSongsCountMin', mutation: 'setAlbumsFilterSongsCountMin' },
]

/**
 * Компонент «Store».
 *
 * @see AGENTS.md
 */
export default {
  state: {
    albumsFilterId: '',
    albumsFilterAuthorName: '',
    albumsFilterYear: '',
    albumsFilterName: '',
    albumsFilterAlbumType: '',
    albumsFilterSongsCountMin: '',
  },
  getters: {
    getAlbumsFilterId(state) {
      return state.albumsFilterId
    },
    getAlbumsFilterAuthorName(state) {
      return state.albumsFilterAuthorName
    },
    getAlbumsFilterYear(state) {
      return state.albumsFilterYear
    },
    getAlbumsFilterName(state) {
      return state.albumsFilterName
    },
    getAlbumsFilterAlbumType(state) {
      return state.albumsFilterAlbumType
    },
    getAlbumsFilterSongsCountMin(state) {
      return state.albumsFilterSongsCountMin
    },
    /**
     * specs/358-rows-per-page: агрегирующий геттер, возвращает объект `params`
     * в формате, который принимает `loadAlbumsDigests`. Используется в
     * `AlbumsTable.onPerPageChange` чтобы сохранить текущие фильтры.
     */
    getAlbumsFilter(state) {
      const params = {}
      if (
        state.albumsFilterId !== undefined &&
        state.albumsFilterId !== '' &&
        state.albumsFilterId !== null
      )
        params.filterId = state.albumsFilterId
      if (
        state.albumsFilterAuthorName !== undefined &&
        state.albumsFilterAuthorName !== '' &&
        state.albumsFilterAuthorName !== null
      )
        params.filterAuthorName = state.albumsFilterAuthorName
      if (
        state.albumsFilterYear !== undefined &&
        state.albumsFilterYear !== '' &&
        state.albumsFilterYear !== null
      )
        params.filterYear = state.albumsFilterYear
      if (
        state.albumsFilterName !== undefined &&
        state.albumsFilterName !== '' &&
        state.albumsFilterName !== null
      )
        params.filterName = state.albumsFilterName
      if (
        state.albumsFilterAlbumType !== undefined &&
        state.albumsFilterAlbumType !== '' &&
        state.albumsFilterAlbumType !== null
      )
        params.filterAlbumType = state.albumsFilterAlbumType
      if (
        state.albumsFilterSongsCountMin !== undefined &&
        state.albumsFilterSongsCountMin !== '' &&
        state.albumsFilterSongsCountMin !== null
      )
        params.filterSongsCountMin = state.albumsFilterSongsCountMin
      return params
    },
  },

  mutations: {
    // BR-366: мутации только присваивают state — сетевой записи здесь нет.
    setAlbumsFilterId(state, value) {
      state.albumsFilterId = value
    },
    setAlbumsFilterAuthorName(state, value) {
      state.albumsFilterAuthorName = value
    },
    setAlbumsFilterYear(state, value) {
      state.albumsFilterYear = value
    },
    setAlbumsFilterName(state, value) {
      state.albumsFilterName = value
    },
    setAlbumsFilterAlbumType(state, value) {
      state.albumsFilterAlbumType = value
    },
    setAlbumsFilterSongsCountMin(state, value) {
      state.albumsFilterSongsCountMin = value
    },
  },
  actions: {
    setAlbumsFilterId(ctx, payload) {
      ctx.commit('setAlbumsFilterId', payload.value)
    },
    setAlbumsFilterAuthorName(ctx, payload) {
      ctx.commit('setAlbumsFilterAuthorName', payload.value)
    },
    setAlbumsFilterYear(ctx, payload) {
      ctx.commit('setAlbumsFilterYear', payload.value)
    },
    setAlbumsFilterName(ctx, payload) {
      ctx.commit('setAlbumsFilterName', payload.value)
    },
    setAlbumsFilterAlbumType(ctx, payload) {
      ctx.commit('setAlbumsFilterAlbumType', payload.value)
    },
    setAlbumsFilterSongsCountMin(ctx, payload) {
      ctx.commit('setAlbumsFilterSongsCountMin', payload.value)
    },
    /**
     * BR-366: восстановление фильтра с сервера — ЧТЕНИЕ без записи.
     *
     * Прежний `beforeMount` модалки читал шесть значений и тут же записывал их
     * обратно (шесть лишних POST'ов на каждое открытие модалки).
     *
     * @returns {Promise<void>}
     */
    async restoreAlbumsFilter(ctx) {
      const results = await Promise.allSettled(
        ALBUMS_FILTER_KEYS.map(({ stateKey }) => ctx.getters.getWebvueProp(stateKey, '')),
      )
      results.forEach((result, index) => {
        if (result.status === 'fulfilled') {
          ctx.commit(ALBUMS_FILTER_KEYS[index].mutation, result.value)
        }
      })
    },
    /**
     * BR-366: сохранение фильтра на сервере — по кнопке «Применить», а не на каждый
     * `input`. Отказ обрабатывается (предупреждение), промис возвращается.
     *
     * @returns {Promise<boolean>} true — все значения сохранены.
     */
    async persistAlbumsFilter(ctx) {
      const results = await Promise.all(
        ALBUMS_FILTER_KEYS.map(({ stateKey }) =>
          setWebvueProp(ctx.state[stateKey], stateKey, ctx.state[stateKey]),
        ),
      )
      return results.every((ok) => ok !== false)
    },
  },
}
