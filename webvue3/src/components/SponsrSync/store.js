import { promisedXMLHttpRequest } from '../../lib/utils.js'

// Sponsr-синхронизация премиума (см. SponsrSyncService в karaoke-app). sponsrSyncTarget ('local'|'remote') —
// тот же паттерн, что siteUsersTarget/tariffsTarget: реальные подписчики матчатся на прод-БД.
/**
 * Компонент «Store».
 *
 * @see AGENTS.md
 */
export default {
  state: {
    sponsrSyncStatus: null,
    sponsrSyncIsLoading: false,
    sponsrSyncLastResult: null,
    sponsrSyncTarget: 'local',
    // BR-446 (аудит #336, пакет PKG-S3-33, OpenProject #405): ошибка ТРАНСПОРТА.
    // Бизнес-отказ (`ok: false` в теле ответа при коде 200) — это `sponsrSyncLastResult`;
    // отказ запроса (5xx, обрыв, таймаут) — это здесь. Смешивать их нельзя: раньше
    // отказ проглатывался, и панель продолжала показывать ПРЕДЫДУЩИЙ успешный
    // результат («Найдено идентификаторов: N») — ложный успех.
    sponsrSyncError: null,
  },
  getters: {
    getSponsrSyncStatus(state) {
      return state.sponsrSyncStatus
    },
    getSponsrSyncIsLoading(state) {
      return state.sponsrSyncIsLoading
    },
    getSponsrSyncLastResult(state) {
      return state.sponsrSyncLastResult
    },
    getSponsrSyncTarget(state) {
      return state.sponsrSyncTarget
    },
    getSponsrSyncError(state) {
      return state.sponsrSyncError
    },
  },
  mutations: {
    setSponsrSyncStatus(state, v) {
      state.sponsrSyncStatus = v
    },
    setSponsrSyncIsLoading(state, v) {
      state.sponsrSyncIsLoading = v
    },
    setSponsrSyncLastResult(state, v) {
      state.sponsrSyncLastResult = v
    },
    setSponsrSyncTarget(state, target) {
      state.sponsrSyncTarget = target
    },
    setSponsrSyncError(state, v) {
      state.sponsrSyncError = v
    },
  },
  actions: {
    loadSponsrSyncStatus(ctx) {
      let request = { method: 'POST', url: '/api/sponsrsync/status', params: {} }
      return promisedXMLHttpRequest(request)
        .then((data) => {
          ctx.commit('setSponsrSyncStatus', JSON.parse(data))
        })
        .catch((e) => console.log(e))
    },
    setSponsrSyncTarget(ctx, target) {
      ctx.commit('setSponsrSyncTarget', target)
    },
    importSponsrList(ctx, identifiers) {
      ctx.commit('setSponsrSyncIsLoading', true)
      ctx.commit('setSponsrSyncError', null)
      let request = {
        method: 'POST',
        url: '/api/sponsrsync/import',
        params: { identifiers, target: ctx.state.sponsrSyncTarget },
      }
      return promisedXMLHttpRequest(request)
        .then((data) => {
          ctx.commit('setSponsrSyncLastResult', JSON.parse(data))
          ctx.commit('setSponsrSyncIsLoading', false)
        })
        .catch((e) => {
          ctx.commit('setSponsrSyncIsLoading', false)
          // BR-446: прежний результат больше не показывается как результат ЭТОЙ
          // операции — он очищается, а ошибка попадает в отдельное состояние.
          ctx.commit('setSponsrSyncLastResult', null)
          ctx.commit('setSponsrSyncError', sponsrSyncErrorText(e, 'импорта списка подписчиков'))
          console.log(e)
        })
    },
    runSponsrSync(ctx) {
      ctx.commit('setSponsrSyncIsLoading', true)
      ctx.commit('setSponsrSyncError', null)
      let request = {
        method: 'POST',
        url: '/api/sponsrsync/run',
        params: { target: ctx.state.sponsrSyncTarget },
      }
      return promisedXMLHttpRequest(request)
        .then((data) => {
          ctx.commit('setSponsrSyncLastResult', JSON.parse(data))
          ctx.commit('setSponsrSyncIsLoading', false)
        })
        .catch((e) => {
          ctx.commit('setSponsrSyncIsLoading', false)
          // BR-446: см. importSponsrList — старый результат очищается.
          ctx.commit('setSponsrSyncLastResult', null)
          ctx.commit('setSponsrSyncError', sponsrSyncErrorText(e, 'синхронизации'))
          console.log(e)
        })
    },
  },
}

/**
 * Текст ошибки транспорта для панели Sponsr-sync.
 *
 * @param {any} e — исключение/ошибка отклонённого промиса.
 * @param {string} operation — что именно не удалось выполнить.
 * @returns {string} текст для показа пользователю.
 */
function sponsrSyncErrorText(e, operation) {
  const detail = e && e.message ? e.message : String(e)
  return `Не удалось выполнить ${operation}: ${detail}`
}
