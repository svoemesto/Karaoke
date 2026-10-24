import { promisedXMLHttpRequest } from '../../lib/utils.js'

/**
 * Проверить цену тарифа до отправки.
 *
 * BR-461: `canCreate` пропускал пустую цену (`'' >= 0` истинно), запрос уходил с
 * `priceRub=` и падал на `@RequestParam priceRub: Double` с 400 — молча, без
 * сообщения пользователю. Цена — деньги, поэтому валидируем явно.
 *
 * @param {*} value — значение из формы
 * @returns {Boolean}
 */
export function isValidTariffPrice(value) {
  // `Number(null)` и `Number('')` дают 0, поэтому тип и пустую строку проверяем явно:
  // пустая цена не должна считаться валидной нулевой.
  if (typeof value !== 'number' && typeof value !== 'string') return false
  if (typeof value === 'string' && value.trim() === '') return false
  const price = Number(value)
  return Number.isFinite(price) && price >= 0
}

// Тарифы подписки (монетизация, tbl_price_tariffs). tariffsTarget ('local'|'remote') — тот же
// паттерн, что siteUsersTarget/publicSettingsTarget: реальные тарифы правятся на прод-БД
// (target=remote), т.к. платёжный конвейер karaoke-web читает их оттуда.
/**
 * Компонент «Store».
 *
 * @see AGENTS.md
 */
export default {
  state: {
    tariffsList: [],
    tariffsIsLoading: false,
    tariffsTarget: 'local',
    // BR-461: последняя ошибка операции (null — ошибок нет). Нужна, чтобы UI показал
    // отказ, а не оставлял отредактированные значения «как сохранённые».
    tariffsError: null,
  },
  getters: {
    getTariffsList(state) {
      return state.tariffsList
    },
    getTariffsIsLoading(state) {
      return state.tariffsIsLoading
    },
    getTariffsTarget(state) {
      return state.tariffsTarget
    },
    getTariffsError(state) {
      return state.tariffsError
    },
  },
  mutations: {
    setTariffsList(state, list) {
      state.tariffsList = list
    },
    setTariffsIsLoading(state, v) {
      state.tariffsIsLoading = v
    },
    setTariffsTarget(state, target) {
      state.tariffsTarget = target
    },
    setTariffsError(state, error) {
      state.tariffsError = error
    },
  },
  actions: {
    loadTariffsList(ctx) {
      ctx.commit('setTariffsIsLoading', true)
      let request = {
        method: 'POST',
        url: '/api/tariffs/list',
        params: { target: ctx.state.tariffsTarget },
      }
      return promisedXMLHttpRequest(request)
        .then((data) => {
          let result = JSON.parse(data)
          ctx.commit('setTariffsList', result.tariffs)
          ctx.commit('setTariffsIsLoading', false)
          ctx.commit('setTariffsError', null)
          return true
        })
        .catch((error) => {
          ctx.commit('setTariffsIsLoading', false)
          ctx.commit('setTariffsError', String(error && error.message ? error.message : error))
          console.error('[Tariffs.loadTariffsList] failed', error)
          return false
        })
    },
    setTariffsTarget(ctx, target) {
      ctx.commit('setTariffsTarget', target)
    },
    createTariff(ctx, payload) {
      // BR-461: пустая/нечисловая цена раньше уходила на сервер как `priceRub=` и
      // получала 400 молча. Не отправляем такой запрос вовсе.
      if (!isValidTariffPrice(payload && payload.priceRub)) {
        ctx.commit('setTariffsError', 'Некорректная цена тарифа')
        return Promise.resolve(false)
      }
      let request = {
        method: 'POST',
        url: '/api/tariffs/create',
        params: Object.assign({ target: ctx.state.tariffsTarget }, payload),
      }
      return promisedXMLHttpRequest(request)
        .then(() => ctx.dispatch('loadTariffsList'))
        .then(() => true)
        .catch((error) => {
          // BR-461: без catch промис отклонялся, форма выглядела «отправленной»,
          // а в таблице оставались отредактированные значения.
          ctx.commit('setTariffsError', String(error && error.message ? error.message : error))
          console.error('[Tariffs.createTariff] failed', error)
          return false
        })
    },
    saveTariff(ctx, payload) {
      if (!isValidTariffPrice(payload && payload.priceRub)) {
        ctx.commit('setTariffsError', 'Некорректная цена тарифа')
        return Promise.resolve(false)
      }
      let request = {
        method: 'POST',
        url: '/api/tariffs/update',
        params: Object.assign({ target: ctx.state.tariffsTarget }, payload),
      }
      return promisedXMLHttpRequest(request)
        .then(() => ctx.dispatch('loadTariffsList'))
        .then(() => true)
        .catch((error) => {
          ctx.commit('setTariffsError', String(error && error.message ? error.message : error))
          console.error('[Tariffs.saveTariff] failed', error)
          return false
        })
    },
    deleteTariff(ctx, id) {
      let request = {
        method: 'POST',
        url: '/api/tariffs/delete',
        params: { id, target: ctx.state.tariffsTarget },
      }
      return promisedXMLHttpRequest(request)
        .then(() => ctx.dispatch('loadTariffsList'))
        .then(() => true)
        .catch((error) => {
          ctx.commit('setTariffsError', String(error && error.message ? error.message : error))
          console.error('[Tariffs.deleteTariff] failed', error)
          return false
        })
    },
  },
}
