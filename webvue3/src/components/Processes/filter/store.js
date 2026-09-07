import { setWebvueProp } from '../../../lib/utils'

/**
 * Компонент «Store» (фильтр процессов).
 *
 * @see AGENTS.md
 */

export default {
  state: {
    processesFilterStatus: [],
    processesFilterType: [],
    processesFilterThreadId: '',
    processesFilterChainId: '',
    processesFilterIncludeDeleted: false,
    processesFilterName: '',
  },
  getters: {
    getProcessesFilterStatus(state) {
      return state.processesFilterStatus
    },
    getProcessesFilterType(state) {
      return state.processesFilterType
    },
    getProcessesFilterThreadId(state) {
      return state.processesFilterThreadId
    },
    getProcessesFilterChainId(state) {
      return state.processesFilterChainId
    },
    getProcessesFilterIncludeDeleted(state) {
      return state.processesFilterIncludeDeleted
    },
    getProcessesFilterName(state) {
      return state.processesFilterName
    },
  },
  mutations: {
    setProcessesFilterStatus(state, value) {
      setWebvueProp(state.processesFilterStatus, 'processesFilterStatus', JSON.stringify(value))
      state.processesFilterStatus = value
    },
    setProcessesFilterType(state, value) {
      setWebvueProp(state.processesFilterType, 'processesFilterType', JSON.stringify(value))
      state.processesFilterType = value
    },
    setProcessesFilterThreadId(state, value) {
      setWebvueProp(state.processesFilterThreadId, 'processesFilterThreadId', value)
      state.processesFilterThreadId = value
    },
    setProcessesFilterChainId(state, value) {
      setWebvueProp(state.processesFilterChainId, 'processesFilterChainId', value)
      state.processesFilterChainId = value
    },
    setProcessesFilterIncludeDeleted(state, value) {
      setWebvueProp(state.processesFilterIncludeDeleted, 'processesFilterIncludeDeleted', value)
      state.processesFilterIncludeDeleted = value
    },
    setProcessesFilterName(state, value) {
      setWebvueProp(state.processesFilterName, 'processesFilterName', value)
      state.processesFilterName = value
    },
  },
  actions: {
    setProcessesFilterStatus(ctx, payload) {
      ctx.commit('setProcessesFilterStatus', payload.value)
    },
    setProcessesFilterType(ctx, payload) {
      ctx.commit('setProcessesFilterType', payload.value)
    },
    setProcessesFilterThreadId(ctx, payload) {
      ctx.commit('setProcessesFilterThreadId', payload.value)
    },
    setProcessesFilterChainId(ctx, payload) {
      ctx.commit('setProcessesFilterChainId', payload.value)
    },
    setProcessesFilterIncludeDeleted(ctx, payload) {
      ctx.commit('setProcessesFilterIncludeDeleted', payload.value)
    },
    setProcessesFilterName(ctx, payload) {
      ctx.commit('setProcessesFilterName', payload.value)
    },
  },
}
