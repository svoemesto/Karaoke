import { promisedXMLHttpRequest } from '../../lib/utils.js'
import { createRequestGeneration, isStaleTargetResponse } from '../../lib/target-request-guard.js'
import { parseServerFlag, parseServerId } from '../../lib/server-result.js'

// Акции (монетизация, tbl_promo_rules). promoRulesTarget ('local'|'remote') — тот же паттерн, что
// tariffsTarget/siteUsersTarget.
//
// BR-407 (аудит #336, пакет PKG-RACE-TARGET, OpenProject #367): ответ списка привязан к target,
// с которым был отправлен, а не к текущему состоянию селекта. Раньше `loadPromoRulesList` читал
// `promoRulesTarget` только при отправке, ответ применялся безусловно — при быстром переключении
// «Локальная/Сервер» побеждал последний пришедший ответ, и таблица показывала правила чужой БД.
// Дальше `savePromoRule`/`deletePromoRule` подставляли ТЕКУЩИЙ target, то есть правка строки из
// показанного (чужого) списка уходила в другую базу.
//
// Что изменилось:
// 1. Каждый запрос списка получает номер поколения; устаревший ответ отбрасывается.
// 2. `promoRulesListTarget` хранит БД, из которой реально получен показанный список; мутации
//    (create/save/delete) используют именно её, а не текущий селект. Компонент блокирует
//    кнопки, пока `promoRulesListTarget !== promoRulesTarget`.
// 3. Смена target очищает список, чтобы строки прежней БД не выглядели данными новой.
//
// Почему не «уникальный ключ»/условный UPDATE: гонка здесь не в БД (запись атомарна), а в
// клиентском состоянии; API менять нельзя, поэтому поколение запроса — минимальная правка.
const listGeneration = createRequestGeneration()

// BR-406 (аудит #336, пакет PKG-S3-32, OpenProject #404): разбор ответа мутации.
//
// Сервер сообщает об отказе ТЕЛОМ, а не кодом: `/api/promorules/create` и
// `/api/promorules/update` возвращают `Long` (id акции) и `0L`, если акция не
// найдена или не создана; `/api/promorules/delete` возвращает `Boolean` и `false`.
// HTTP-код при этом 200 — то есть «правило не найдено» приходило как успешная
// перезагрузка списка без изменений.
//
// Поэтому тело ответа проверяется: `0`/`false` — отказ, и он НЕ выдаётся за успех.
// Правило разбора общее для всех таких эндпоинтов — webvue3/src/lib/server-result.js.
/**
 * Разбирает id акции из тела ответа.
 *
 * @param {*} raw — тело ответа (`xhr.response`).
 * @returns {number} id акции либо 0 (отказ).
 */
export function parsePromoRuleId(raw) {
  return parseServerId(raw)
}

/**
 * Разбирает флаг из тела ответа.
 *
 * @param {*} raw — тело ответа (`xhr.response`).
 * @returns {boolean} признак успеха.
 */
export function parsePromoRuleFlag(raw) {
  return parseServerFlag(raw)
}

/**
 * Компонент «Store».
 *
 * @see AGENTS.md
 */
export default {
  state: {
    promoRulesList: [],
    promoRulesListTarget: null,
    promoRulesIsLoading: false,
    promoRulesTarget: 'local',
  },
  getters: {
    getPromoRulesList(state) {
      return state.promoRulesList
    },
    getPromoRulesListTarget(state) {
      return state.promoRulesListTarget
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
    setPromoRulesListTarget(state, target) {
      state.promoRulesListTarget = target
    },
    setPromoRulesIsLoading(state, v) {
      state.promoRulesIsLoading = v
    },
    setPromoRulesTarget(state, target) {
      if (state.promoRulesTarget === target) return
      state.promoRulesTarget = target
      // Строки прежней БД не должны выглядеть данными новой, пока не пришёл ответ.
      state.promoRulesList = []
    },
  },
  actions: {
    loadPromoRulesList(ctx) {
      const requestTarget = ctx.state.promoRulesTarget
      const ticket = listGeneration.next()
      ctx.commit('setPromoRulesIsLoading', true)
      let request = {
        method: 'POST',
        url: '/api/promorules/list',
        params: { target: requestTarget },
      }
      const isStale = () =>
        isStaleTargetResponse({
          ticket,
          requestTarget,
          currentTarget: ctx.state.promoRulesTarget,
          generation: listGeneration,
        })
      return promisedXMLHttpRequest(request)
        .then((data) => {
          if (isStale()) return
          let result = JSON.parse(data)
          ctx.commit('setPromoRulesList', result.promoRules)
          ctx.commit('setPromoRulesListTarget', requestTarget)
          ctx.commit('setPromoRulesIsLoading', false)
        })
        .catch((error) => {
          if (isStale()) return
          ctx.commit('setPromoRulesIsLoading', false)
          console.log(error)
        })
    },
    setPromoRulesTarget(ctx, target) {
      ctx.commit('setPromoRulesTarget', target)
    },
    // Мутации адресуются БД, из которой получен показанный список. `null` — список ещё ни
    // разу не загружался (или загрузится позже): писать некуда, кнопки в UI заблокированы.
    //
    // BR-406: отказ сервера (`0L` для create/update, `false` для delete) больше не
    // выдаётся за успех — action отклоняется с понятным сообщением, вызывающий
    // показывает его пользователю.
    createPromoRule(ctx, payload) {
      const target = ctx.state.promoRulesListTarget
      if (!target) return Promise.resolve(false)
      let request = {
        method: 'POST',
        url: '/api/promorules/create',
        params: Object.assign({ target }, payload),
      }
      return promisedXMLHttpRequest(request).then((data) => {
        if (!parsePromoRuleId(data)) {
          throw new Error('Акция не создана (сервер вернул id=0)')
        }
        return ctx.dispatch('loadPromoRulesList')
      })
    },
    savePromoRule(ctx, payload) {
      const target = ctx.state.promoRulesListTarget
      if (!target) return Promise.resolve(false)
      let request = {
        method: 'POST',
        url: '/api/promorules/update',
        params: Object.assign({ target }, payload),
      }
      return promisedXMLHttpRequest(request).then((data) => {
        if (!parsePromoRuleId(data)) {
          throw new Error('Акция не найдена (сервер вернул id=0) — изменения не сохранены')
        }
        return ctx.dispatch('loadPromoRulesList')
      })
    },
    deletePromoRule(ctx, id) {
      const target = ctx.state.promoRulesListTarget
      if (!target) return Promise.resolve(false)
      let request = {
        method: 'POST',
        url: '/api/promorules/delete',
        params: { id, target },
      }
      return promisedXMLHttpRequest(request).then((data) => {
        if (!parsePromoRuleFlag(data)) {
          throw new Error('Акция не удалена (сервер вернул false) — возможно, она уже удалена')
        }
        return ctx.dispatch('loadPromoRulesList')
      })
    },
  },
}
