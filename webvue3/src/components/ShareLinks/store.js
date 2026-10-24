import { promisedXMLHttpRequest } from '../../lib/utils.js'
import { createRequestGeneration, isStaleTargetResponse } from '../../lib/target-request-guard.js'

// Временные share-ссылки пользователей ПУБЛИЧНОГО САЙТА (tbl_song_share_links). Read-only
// глобальный список для админ-SPA. target ('local'|'remote') — как в SiteUsers. JOIN к
// tbl_songs / tbl_site_users делается на бэкенде одним батчем (см. ShareLinksAdminController.kt).
// Действие «Отозвать» диспатчит существующий `revokeSiteUserShareLink` из shareLinkStore.js —
// эндпоинт `/api/siteusers/share/links/revoke` переиспользуется 100% (см. RQ-6 в research.md).
//
// BR-409 (аудит #336, пакет PKG-RACE-TARGET, OpenProject #367): дайджест share-ссылок —
// target-aware, но ответ применялся без сверки. При быстром переключении local → remote → local
// ответ старой БД мог прийти последним и остаться в таблице; далее «Отозвать» брал id строки из
// чужой БД, а target — текущий из store, и отзывал запись в другой базе.
//
// Что изменилось:
// 1. Поколение запроса + сверка target: устаревший ответ не меняет state.
// 2. `shareLinksDigestTarget` — БД, из которой получен показанный дайджест; отзыв адресуется ей
//    (а компонент дополнительно фиксирует её в момент открытия подтверждения).
// 3. Смена target очищает дайджест: строки прежней БД не выглядят данными новой.
//
// Почему не уникальный ключ/условный UPDATE: правка клиентская, БД правится атомарно существующим
// серверным методом; API не меняем — поколение ответа закрывает окно.
const digestGeneration = createRequestGeneration()

/**
 * Vuex-модуль «Временные ссылки» (tbl_song_share_links). Глобальный read-only список
 * для админ-SPA с действием «Отозвать» (in-place обновление строки после успешного revoke).
 *
 * Хранит:
 * - `shareLinksDigest` — массив строк текущей страницы.
 * - `shareLinksDigestTotalCount` — общее число строк.
 * - `shareLinksDigestTarget` — БД, из которой получен показанный дайджест.
 * - `shareLinksTarget` — 'local' | 'remote'.
 * - `shareLinksTableCurrentPage` — персистентность страницы (см. AGENTS.md).
 *
 * @see AGENTS.md
 * @see specs/171-admin-subscriptions-history/contracts/sharelinks-digest.md
 */
export default {
  state: {
    shareLinksDigest: [],
    shareLinksDigestTotalCount: 0,
    shareLinksDigestIsLoading: false,
    shareLinksDigestTarget: null,
    shareLinksTarget: 'local',
    shareLinksTableCurrentPage: 1,
  },
  getters: {
    getShareLinksDigest(state) {
      return state.shareLinksDigest
    },
    getShareLinksDigestTotalCount(state) {
      return state.shareLinksDigestTotalCount
    },
    getShareLinksDigestIsLoading(state) {
      return state.shareLinksDigestIsLoading
    },
    getShareLinksDigestTarget(state) {
      return state.shareLinksDigestTarget
    },
    getShareLinksTarget(state) {
      return state.shareLinksTarget
    },
    getShareLinksTableCurrentPage(state) {
      return state.shareLinksTableCurrentPage
    },
  },
  mutations: {
    setShareLinksDigest(state, result) {
      state.shareLinksDigest = result || []
    },
    setShareLinksDigestTotalCount(state, total) {
      state.shareLinksDigestTotalCount = total || 0
    },
    setShareLinksDigestIsLoading(state, isLoading) {
      state.shareLinksDigestIsLoading = isLoading
    },
    setShareLinksDigestTarget(state, target) {
      state.shareLinksDigestTarget = target
    },
    setShareLinksTarget(state, target) {
      if (state.shareLinksTarget === target) return
      state.shareLinksTarget = target
      // Строки прежней БД не должны выглядеть данными новой, пока не пришёл ответ.
      state.shareLinksDigest = []
      state.shareLinksDigestTotalCount = 0
    },
    setShareLinksTableCurrentPage(state, page) {
      state.shareLinksTableCurrentPage = page
    },
    // In-place обновление строки после успешного revoke (FR-022). Перезаписываем объект
    // в массиве по id — Vuex реактивность видит новое значение и Vue перерисовывает только
    // эту строку.
    updateShareLinksDigestItem(state, updatedItem) {
      const index = state.shareLinksDigest.findIndex((x) => x.id === updatedItem.id)
      if (index !== -1) {
        state.shareLinksDigest.splice(index, 1, updatedItem)
      }
    },
  },
  actions: {
    loadShareLinksDigest(ctx, params = {}) {
      const requestTarget = ctx.state.shareLinksTarget
      const ticket = digestGeneration.next()
      const fullParams = Object.assign({}, params, { target: requestTarget })
      const request = { method: 'POST', url: '/api/sharelinks/digest', params: fullParams }
      ctx.commit('setShareLinksDigestIsLoading', true)
      const isStale = () =>
        isStaleTargetResponse({
          ticket,
          requestTarget,
          currentTarget: ctx.state.shareLinksTarget,
          generation: digestGeneration,
        })
      return promisedXMLHttpRequest(request)
        .then((data) => {
          if (isStale()) return
          const result = JSON.parse(data)
          ctx.commit('setShareLinksDigest', result.shareLinksDigest)
          ctx.commit('setShareLinksDigestTotalCount', result.totalCount)
          // Дайджест принадлежит той БД, из которой пришёл, — даже если target уже сменился
          // (тогда ответ выше отброшен и сюда не попадём).
          ctx.commit('setShareLinksDigestTarget', requestTarget)
          ctx.commit('setShareLinksDigestIsLoading', false)
        })
        .catch((error) => {
          if (isStale()) return
          ctx.commit('setShareLinksDigestIsLoading', false)
          console.log(error)
        })
    },
    setShareLinksTarget(ctx, target) {
      ctx.commit('setShareLinksTarget', target)
    },
    // Обёртка над revokeSiteUserShareLink для удобства компонента таблицы (используем тот же
    // action из shareLinkStore.js — НЕ дублируем логику). После успешного revoke — перезагружаем
    // одну строку через digest с постраничной выборкой (или локально патчим объект). Здесь —
    // патчим: быстрее для UX, не дёргает БД дважды.
    //
    // BR-409: target берётся у строки/дайджеста, а не у текущего селекта. Иначе отзыв строки из
    // показанной БД уходил бы в ту, которую админ успел выбрать после открытия подтверждения.
    revokeShareLink(ctx, { shareLinkId, reason = 'admin', target }) {
      const rowTarget = target || ctx.state.shareLinksDigestTarget
      if (!rowTarget) {
        return Promise.reject(new Error('Неизвестна БД строки: дайджест ещё не загружен.'))
      }
      return ctx.dispatch('revokeSiteUserShareLink', {
        shareLinkId,
        reason,
        target: rowTarget,
      })
    },
  },
}
