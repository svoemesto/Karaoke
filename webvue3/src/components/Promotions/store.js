import { promisedXMLHttpRequest } from '../../lib/utils'

// Акции (монетизация, tbl_promo_rules). promoRulesTarget ('local'|'remote') — тот же паттерн, что
// tariffsTarget/siteUsersTarget.
/**
 * Компонент «Store».
 *
 * @see AGENTS.md
 */
export default {
  state: {
    promoRulesList: [],
    promoRulesIsLoading: false,
    promoRulesTarget: 'local',
  },
  getters: {
    getPromoRulesList(state) {
      return state.promoRulesList
    },
    getPromoRulesIsLoading(state) {
      return state.promoRulesIsLoading
    },
    getPromoRulesTarget(state) {
      return state.promoRulesTarget
    },
  },
  mutations: {
    setPromoRulesList(state, list) {
      state.promoRulesList = list
    },
    setPromoRulesIsLoading(state, v) {
      state.promoRulesIsLoading = v
    },
    setPromoRulesTarget(state, target) {
      state.promoRulesTarget = target
    },
  },
  actions: {
    loadPromoRulesList(ctx) {
      ctx.commit('setPromoRulesIsLoading', true)
      let request = {
        method: 'POST',
        url: '/api/promorules/list',
        params: { target: ctx.state.promoRulesTarget },
      }
      return promisedXMLHttpRequest(request)
        .then((data) => {
          let result = JSON.parse(data)
          ctx.commit('setPromoRulesList', result.promoRules)
          ctx.commit('setPromoRulesIsLoading', false)
        })
        .catch((error) => {
          ctx.commit('setPromoRulesIsLoading', false)
          console.log(error)
        })
    },
    setPromoRulesTarget(ctx, target) {
      ctx.commit('setPromoRulesTarget', target)
    },
    createPromoRule(ctx, payload) {
      let request = {
        method: 'POST',
        url: '/api/promorules/create',
        params: Object.assign({ target: ctx.state.promoRulesTarget }, payload),
      }
      return promisedXMLHttpRequest(request).then(() => ctx.dispatch('loadPromoRulesList'))
    },
    savePromoRule(ctx, payload) {
      let request = {
        method: 'POST',
        url: '/api/promorules/update',
        params: Object.assign({ target: ctx.state.promoRulesTarget }, payload),
      }
      return promisedXMLHttpRequest(request).then(() => ctx.dispatch('loadPromoRulesList'))
    },
    deletePromoRule(ctx, id) {
      let request = {
        method: 'POST',
        url: '/api/promorules/delete',
        params: { id, target: ctx.state.promoRulesTarget },
      }
      return promisedXMLHttpRequest(request).then(() => ctx.dispatch('loadPromoRulesList'))
    },
  },
}
