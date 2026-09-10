import { promisedXMLHttpRequest } from '../../lib/utils'

/**
 * Vuex-модуль «tableSettings».
 *
 * Хранит per-table количество строк на странице для admin-таблиц webvue3
 * (specs/358-rows-per-page). Значения читаются из `/api/propertiesdigests`
 * (один раз при старте SPA, дальше — локальный кеш), пишутся через
 * `/api/properties/setproperty`. Хранение — глобальное per-table в
 * `KaraokeProperties` (НЕ per-user).
 *
 * @see specs/358-rows-per-page
 * @see AGENTS.md
 */

/**
 * Hardcoded дефолты для каждой таблицы — ДОЛЖНЫ совпадать с
 * `listKaraokeProperties` в `karaoke-app/.../KaraokeProperties.kt`
 * (FR-010 spec.md: дефолты идентичны текущему hardcoded поведению).
 */
const DEFAULT_ROWS_PER_PAGE = Object.freeze({
  songs: 50,
  authors: 30,
  albums: 30,
  pictures: 30,
  site_users: 30,
  subscriptions: 25,
  share_links: 25,
  dictionaries: 30,
  properties: 50,
  site_playlists: 30,
  listening_history: 500,
  processes: 50,
  news: 30,
})

export default {
  state: () => ({
    // Map: { 'songs': 50, 'authors': 30, ... } — заполняется при loadTableSettings().
    rowsPerPage: {},
    // Загружали ли уже с сервера (защита от повторных вызовов).
    loaded: false,
    // In-flight saves per table для индикации loading в UI.
    saving: {},
  }),
  getters: {
    /**
     * Возвращает rowsPerPage для таблицы или hardcoded дефолт.
     * @param {string} tableKey — ключ таблицы ('songs', 'authors', и т.п.)
     * @returns {number}
     */
    getRowsPerPage: (state) => (tableKey) => {
      const fromState = state.rowsPerPage[tableKey]
      if (typeof fromState === 'number' && fromState >= 1 && fromState <= 1000) {
        return fromState
      }
      return DEFAULT_ROWS_PER_PAGE[tableKey] || 30
    },
    /**
     * Сохраняется ли сейчас значение для таблицы.
     */
    isSavingRowsPerPage: (state) => (tableKey) => {
      return !!state.saving[tableKey]
    },
    /**
     * Список всех известных tableKey с их effective значениями
     * (state.rowsPerPage[tableKey] ?? DEFAULT_ROWS_PER_PAGE[tableKey]).
     * Полезно для UI «Настройки» или отладки.
     */
    getAllTableRowsPerPage: (state) => () => {
      const result = {}
      for (const key of Object.keys(DEFAULT_ROWS_PER_PAGE)) {
        const fromState = state.rowsPerPage[key]
        result[key] =
          typeof fromState === 'number' && fromState >= 1 && fromState <= 1000
            ? fromState
            : DEFAULT_ROWS_PER_PAGE[key]
      }
      return result
    },
  },
  actions: {
    /**
     * Загрузить ВСЕ настройки таблиц из backend (`/api/propertiesdigests`)
     * один раз при старте SPA. Защищён флагом `loaded`.
     */
    async loadTableSettings({ commit, state }) {
      if (state.loaded) return
      try {
        const data = await promisedXMLHttpRequest({
          method: 'POST',
          url: '/api/propertiesdigests',
        })
        const parsed = JSON.parse(data)
        const properties = (parsed && parsed.propertiesDigests) || []
        const map = {}
        for (const prop of properties) {
          if (
            prop &&
            typeof prop.key === 'string' &&
            prop.key.startsWith('ui.') &&
            prop.key.endsWith('.rows_per_page')
          ) {
            // Ключ вида 'ui.songs.rows_per_page' → tableKey = 'songs'
            const tableKey = prop.key.slice(3, -'.rows_per_page'.length)
            const value = parseInt(prop.value, 10)
            if (!isNaN(value) && value >= 1 && value <= 1000) {
              map[tableKey] = value
            }
          }
        }
        commit('setRowsPerPageMap', map)
        commit('markLoaded')
      } catch (err) {
        // Не блокируем UI: дефолты останутся.
        // eslint-disable-next-line no-console
        console.error('[tableSettings] loadTableSettings error', err)
        commit('markLoaded')
      }
    },
    /**
     * Сохранить rowsPerPage для таблицы в backend.
     * После успешного ответа — обновить state.
     * Не оптимистично (см. Clarifications Q3 spec.md).
     *
     * @param {Object} payload — { tableKey: string, value: number }
     */
    async setRowsPerPage({ commit }, payload) {
      const { tableKey, value } = payload || {}
      if (!tableKey || typeof value !== 'number' || value < 1 || value > 1000) {
        return false
      }
      commit('setSaving', { tableKey, saving: true })
      try {
        await promisedXMLHttpRequest({
          method: 'POST',
          url: '/api/properties/setproperty',
          params: {
            key: `ui.${tableKey}.rows_per_page`,
            stringValue: String(value),
          },
        })
        commit('setRow', { tableKey, value })
        return true
      } catch (err) {
        // eslint-disable-next-line no-console
        console.error('[tableSettings] setRowsPerPage error', err)
        return false
      } finally {
        commit('setSaving', { tableKey, saving: false })
      }
    },
  },
  mutations: {
    setRowsPerPageMap(state, map) {
      Object.assign(state.rowsPerPage, map || {})
    },
    setRow(state, { tableKey, value }) {
      state.rowsPerPage[tableKey] = value
    },
    markLoaded(state) {
      state.loaded = true
    },
    setSaving(state, { tableKey, saving }) {
      if (saving) {
        state.saving[tableKey] = true
      } else {
        delete state.saving[tableKey]
      }
    },
  },
}
