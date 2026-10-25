import { setWebvueProp } from '../../../lib/utils.js'

// BR-424 (аудит #336, пакет PKG-S3-32, OpenProject #404).
//
// ДЕФЕКТ. Server-side persistence фильтра пользователей был реализован в мутациях:
// каждая мутация вызывала `setWebvueProp` и сразу отправляла `POST /api/setwebvueprop`.
// Мутации вызываются из `v-model`-сеттеров модалки на КАЖДОЕ нажатие клавиши —
// ввод 30 символов давал 30 POST'ов подряд. `hydrateSiteUsersFilter` при каждом
// открытии таблицы/модалки последовательно ждал 9 запросов `/api/getwebvueprop`
// (waterfall) и каждым коммитом СНОВА писал то же значение на сервер: до 9 лишних
// POST'ов на один проход гидратации.
//
// РЕШЕНИЕ (тот же паттерн, что в Albums/filter/store.js, BR-366 — аудит #336,
// пакет PKG-S3-31, OpenProject #403). Мутации стали чистыми (только state).
// Запись на сервер — по действию пользователя («Применить»), в action
// `persistSiteUsersFilter` (с обработкой отказа). Гидратация — `Promise.allSettled`
// (без waterfall) и БЕЗ записи обратно, в action `restoreSiteUsersFilter`.

/**
 * Ключи настроек фильтра пользователей сайта (совпадают с именами полей в state).
 *
 * @type {Array<{stateKey: string, mutation: string}>}
 */
const SITE_USERS_FILTER_KEYS = [
  { stateKey: 'siteUsersFilterId', mutation: 'setSiteUsersFilterId' },
  { stateKey: 'siteUsersFilterEmail', mutation: 'setSiteUsersFilterEmail' },
  { stateKey: 'siteUsersFilterDisplayName', mutation: 'setSiteUsersFilterDisplayName' },
  { stateKey: 'siteUsersFilterSponsrUid', mutation: 'setSiteUsersFilterSponsrUid' },
  { stateKey: 'siteUsersFilterIsPremium', mutation: 'setSiteUsersFilterIsPremium' },
  {
    stateKey: 'siteUsersFilterIsPermanentPremium',
    mutation: 'setSiteUsersFilterIsPermanentPremium',
  },
  {
    stateKey: 'siteUsersFilterIsEffectivePremium',
    mutation: 'setSiteUsersFilterIsEffectivePremium',
  },
  { stateKey: 'siteUsersFilterIsEditor', mutation: 'setSiteUsersFilterIsEditor' },
  { stateKey: 'siteUsersFilterIsBanned', mutation: 'setSiteUsersFilterIsBanned' },
]

/**
 * Компонент «Store».
 *
 * @see AGENTS.md
 */
export default {
  state: {
    siteUsersFilterId: '',
    siteUsersFilterEmail: '',
    siteUsersFilterDisplayName: '',
    siteUsersFilterSponsrUid: '',
    siteUsersFilterIsPremium: '',
    siteUsersFilterIsPermanentPremium: '',
    siteUsersFilterIsEffectivePremium: '',
    siteUsersFilterIsEditor: '',
    siteUsersFilterIsBanned: '',
  },
  getters: {
    getSiteUsersFilterId(state) {
      return state.siteUsersFilterId
    },
    getSiteUsersFilterEmail(state) {
      return state.siteUsersFilterEmail
    },
    getSiteUsersFilterDisplayName(state) {
      return state.siteUsersFilterDisplayName
    },
    getSiteUsersFilterSponsrUid(state) {
      return state.siteUsersFilterSponsrUid
    },
    getSiteUsersFilterIsPremium(state) {
      return state.siteUsersFilterIsPremium
    },
    getSiteUsersFilterIsPermanentPremium(state) {
      return state.siteUsersFilterIsPermanentPremium
    },
    getSiteUsersFilterIsEffectivePremium(state) {
      return state.siteUsersFilterIsEffectivePremium
    },
    getSiteUsersFilterIsEditor(state) {
      return state.siteUsersFilterIsEditor
    },
    getSiteUsersFilterIsBanned(state) {
      return state.siteUsersFilterIsBanned
    },
    /**
     * specs/358-rows-per-page: агрегирующий геттер, возвращает объект `params`
     * в формате, который принимает `loadSiteUsersDigests`. Используется в
     * `SiteUsersTable.onPerPageChange` чтобы сохранить текущие фильтры.
     */
    getSiteUsersFilter(state) {
      const params = {}
      if (
        state.siteUsersFilterId !== undefined &&
        state.siteUsersFilterId !== '' &&
        state.siteUsersFilterId !== null
      )
        params.filterId = state.siteUsersFilterId
      if (
        state.siteUsersFilterEmail !== undefined &&
        state.siteUsersFilterEmail !== '' &&
        state.siteUsersFilterEmail !== null
      )
        params.filterEmail = state.siteUsersFilterEmail
      if (
        state.siteUsersFilterDisplayName !== undefined &&
        state.siteUsersFilterDisplayName !== '' &&
        state.siteUsersFilterDisplayName !== null
      )
        params.filterDisplayName = state.siteUsersFilterDisplayName
      if (
        state.siteUsersFilterSponsrUid !== undefined &&
        state.siteUsersFilterSponsrUid !== '' &&
        state.siteUsersFilterSponsrUid !== null
      )
        params.filterSponsrUid = state.siteUsersFilterSponsrUid
      if (
        state.siteUsersFilterIsPremium !== undefined &&
        state.siteUsersFilterIsPremium !== '' &&
        state.siteUsersFilterIsPremium !== null
      )
        params.filterIsPremium = state.siteUsersFilterIsPremium
      if (
        state.siteUsersFilterIsPermanentPremium !== undefined &&
        state.siteUsersFilterIsPermanentPremium !== '' &&
        state.siteUsersFilterIsPermanentPremium !== null
      )
        params.filterIsPermanentPremium = state.siteUsersFilterIsPermanentPremium
      if (
        state.siteUsersFilterIsEffectivePremium !== undefined &&
        state.siteUsersFilterIsEffectivePremium !== '' &&
        state.siteUsersFilterIsEffectivePremium !== null
      )
        params.filterIsEffectivePremium = state.siteUsersFilterIsEffectivePremium
      if (
        state.siteUsersFilterIsEditor !== undefined &&
        state.siteUsersFilterIsEditor !== '' &&
        state.siteUsersFilterIsEditor !== null
      )
        params.filterIsEditor = state.siteUsersFilterIsEditor
      if (
        state.siteUsersFilterIsBanned !== undefined &&
        state.siteUsersFilterIsBanned !== '' &&
        state.siteUsersFilterIsBanned !== null
      )
        params.filterIsBanned = state.siteUsersFilterIsBanned
      return params
    },
  },

  mutations: {
    setSiteUsersFilterId(state, value) {
      state.siteUsersFilterId = value
    },
    setSiteUsersFilterEmail(state, value) {
      state.siteUsersFilterEmail = value
    },
    setSiteUsersFilterDisplayName(state, value) {
      state.siteUsersFilterDisplayName = value
    },
    setSiteUsersFilterSponsrUid(state, value) {
      state.siteUsersFilterSponsrUid = value
    },
    setSiteUsersFilterIsPremium(state, value) {
      state.siteUsersFilterIsPremium = value
    },
    setSiteUsersFilterIsPermanentPremium(state, value) {
      state.siteUsersFilterIsPermanentPremium = value
    },
    setSiteUsersFilterIsEffectivePremium(state, value) {
      state.siteUsersFilterIsEffectivePremium = value
    },
    setSiteUsersFilterIsEditor(state, value) {
      state.siteUsersFilterIsEditor = value
    },
    setSiteUsersFilterIsBanned(state, value) {
      state.siteUsersFilterIsBanned = value
    },
  },
  actions: {
    setSiteUsersFilterId(ctx, payload) {
      ctx.commit('setSiteUsersFilterId', payload.value)
    },
    setSiteUsersFilterEmail(ctx, payload) {
      ctx.commit('setSiteUsersFilterEmail', payload.value)
    },
    setSiteUsersFilterDisplayName(ctx, payload) {
      ctx.commit('setSiteUsersFilterDisplayName', payload.value)
    },
    setSiteUsersFilterSponsrUid(ctx, payload) {
      ctx.commit('setSiteUsersFilterSponsrUid', payload.value)
    },
    setSiteUsersFilterIsPremium(ctx, payload) {
      ctx.commit('setSiteUsersFilterIsPremium', payload.value)
    },
    setSiteUsersFilterIsPermanentPremium(ctx, payload) {
      ctx.commit('setSiteUsersFilterIsPermanentPremium', payload.value)
    },
    setSiteUsersFilterIsEffectivePremium(ctx, payload) {
      ctx.commit('setSiteUsersFilterIsEffectivePremium', payload.value)
    },
    setSiteUsersFilterIsEditor(ctx, payload) {
      ctx.commit('setSiteUsersFilterIsEditor', payload.value)
    },
    setSiteUsersFilterIsBanned(ctx, payload) {
      ctx.commit('setSiteUsersFilterIsBanned', payload.value)
    },
    /**
     * BR-424: восстановление фильтра с сервера — ЧТЕНИЕ без записи.
     *
     * Запросы идут ПАРАЛЛЕЛЬНО (`Promise.allSettled`), а не последовательно:
     * прежний цикл `for … await` ждал 9 запросов подряд. Значения больше не
     * записываются обратно на сервер (прежняя «восстанавливающая» запись тех же
     * значений давала до 9 лишних POST'ов на каждое открытие таблицы/модалки).
     * Отказ отдельного чтения не роняет остальные.
     *
     * @returns {Promise<void>}
     */
    async restoreSiteUsersFilter(ctx) {
      const results = await Promise.allSettled(
        SITE_USERS_FILTER_KEYS.map(({ stateKey }) => ctx.rootGetters.getWebvueProp(stateKey, '')),
      )
      results.forEach((result, index) => {
        if (result.status === 'fulfilled') {
          ctx.commit(SITE_USERS_FILTER_KEYS[index].mutation, result.value)
        }
      })
    },
    /**
     * BR-424: сохранение фильтра на сервере — по кнопке «Применить», а не на каждый
     * `input`. Отказ обрабатывается (`setWebvueProp` не бросает), промис возвращается.
     *
     * @returns {Promise<boolean>} true — все значения сохранены.
     */
    async persistSiteUsersFilter(ctx) {
      const results = await Promise.all(
        SITE_USERS_FILTER_KEYS.map(({ stateKey }) =>
          setWebvueProp(ctx.state[stateKey], stateKey, ctx.state[stateKey]),
        ),
      )
      return results.every((ok) => ok !== false)
    },
    // Подтягивает персистентные значения (setWebvueProp) до первой загрузки таблицы, чтобы
    // фильтр применялся сразу при заходе в раздел, а не только после открытия модалки.
    //
    // BR-424: прежнее имя сохранено как синоним (вызывается из SiteUsersTable и
    // SiteUsersFilterModal) — чтение без записи и без waterfall.
    async hydrateSiteUsersFilter(ctx) {
      return ctx.dispatch('restoreSiteUsersFilter')
    },
  },
}
