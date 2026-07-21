import { setWebvueProp } from '../../../lib/utils'

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
  },
  mutations: {
    setSiteUsersFilterId(state, value) {
      setWebvueProp(state.siteUsersFilterId, 'siteUsersFilterId', value)
      state.siteUsersFilterId = value
    },
    setSiteUsersFilterEmail(state, value) {
      setWebvueProp(state.siteUsersFilterEmail, 'siteUsersFilterEmail', value)
      state.siteUsersFilterEmail = value
    },
    setSiteUsersFilterDisplayName(state, value) {
      setWebvueProp(state.siteUsersFilterDisplayName, 'siteUsersFilterDisplayName', value)
      state.siteUsersFilterDisplayName = value
    },
    setSiteUsersFilterSponsrUid(state, value) {
      setWebvueProp(state.siteUsersFilterSponsrUid, 'siteUsersFilterSponsrUid', value)
      state.siteUsersFilterSponsrUid = value
    },
    setSiteUsersFilterIsPremium(state, value) {
      setWebvueProp(state.siteUsersFilterIsPremium, 'siteUsersFilterIsPremium', value)
      state.siteUsersFilterIsPremium = value
    },
    setSiteUsersFilterIsPermanentPremium(state, value) {
      setWebvueProp(
        state.siteUsersFilterIsPermanentPremium,
        'siteUsersFilterIsPermanentPremium',
        value,
      )
      state.siteUsersFilterIsPermanentPremium = value
    },
    setSiteUsersFilterIsEffectivePremium(state, value) {
      setWebvueProp(
        state.siteUsersFilterIsEffectivePremium,
        'siteUsersFilterIsEffectivePremium',
        value,
      )
      state.siteUsersFilterIsEffectivePremium = value
    },
    setSiteUsersFilterIsEditor(state, value) {
      setWebvueProp(state.siteUsersFilterIsEditor, 'siteUsersFilterIsEditor', value)
      state.siteUsersFilterIsEditor = value
    },
    setSiteUsersFilterIsBanned(state, value) {
      setWebvueProp(state.siteUsersFilterIsBanned, 'siteUsersFilterIsBanned', value)
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
    // Подтягивает персистентные значения (setWebvueProp) до первой загрузки таблицы, чтобы
    // фильтр применялся сразу при заходе в раздел, а не только после открытия модалки.
    async hydrateSiteUsersFilter(ctx) {
      const keys = [
        ['siteUsersFilterId', 'setSiteUsersFilterId'],
        ['siteUsersFilterEmail', 'setSiteUsersFilterEmail'],
        ['siteUsersFilterDisplayName', 'setSiteUsersFilterDisplayName'],
        ['siteUsersFilterSponsrUid', 'setSiteUsersFilterSponsrUid'],
        ['siteUsersFilterIsPremium', 'setSiteUsersFilterIsPremium'],
        ['siteUsersFilterIsPermanentPremium', 'setSiteUsersFilterIsPermanentPremium'],
        ['siteUsersFilterIsEffectivePremium', 'setSiteUsersFilterIsEffectivePremium'],
        ['siteUsersFilterIsEditor', 'setSiteUsersFilterIsEditor'],
        ['siteUsersFilterIsBanned', 'setSiteUsersFilterIsBanned'],
      ]
      for (const [propKey, mutation] of keys) {
        const value = await ctx.rootGetters.getWebvueProp(propKey, '')
        ctx.commit(mutation, value)
      }
    },
  },
}
