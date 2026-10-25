import { promisedXMLHttpRequest } from '../../../lib/utils.js'
import { createRequestGeneration } from '../../../lib/target-request-guard.js'

// BR-380 (аудит #336, пакет PKG-S2-17, OpenProject #380).
//
// ДЕФЕКТ. `healthReportList` — единственный глобальный список в Vuex, а
// `loadHealthReportList` коммитил ответ безусловно: ни поколения запроса, ни сверки
// «ответ всё ещё для той песни». `HealthReportTable` монтируется на каждую песню
// (`AssignModal.vue:166`, `v-if="hrSongId"`) и размонтируется при закрытии, но
// отправленный запрос продолжает жить. Открыть тяжёлую песню A (перебор
// KaraokeFileType × MinIO), закрыть, открыть B → ответ A приходит позже ответа B и
// затирает список B: модалка с `:id` песни B показывает строки A, а кнопка «repair»
// отправляет `item.songId` (A) в `/api/song/executeHealthReportActions` — ремонт
// ДРУГОЙ песни, чем та, для которой открыта модалка.
//
// РЕШЕНИЕ. (1) Поколение запроса: применяет ответ только владелец текущего поколения.
// (2) `healthReportListSongId` — песня, которой принадлежит показанный список; «repair»
// отказывается работать, если строка относится к другой песне (fail-closed: лучше
// не отремонтировать, чем отремонтировать чужое).
//
// Функция вынесена из action, чтобы тест мог подставить транспорт
// (`src/components/Common/HealthReport/__tests__/health-report-generation.test.js`).
const healthReportGeneration = createRequestGeneration()

/**
 * Загружает HR-список песни, отбрасывая ответы устаревших запросов.
 *
 * @param {Object} params
 * @param {function(Object): Promise<string>} params.request
 * @param {function(string, any): void} params.commit
 * @param {{next: function(): number, isCurrent: function(number): boolean}} params.generation
 * @param {number|string} params.id — id песни.
 * @returns {Promise<{applied: boolean, stale: boolean, error?: Error}>}
 */
export function loadHealthReportListWith({ request, commit, generation, id }) {
  const ticket = generation.next()
  commit('setHealthReportListIsLoading', true)
  // BR-381: пустой успешный ответ и ошибка — РАЗНЫЕ состояния. Стираем прежнюю
  // ошибку и заводим новую попытку.
  commit('setHealthReportListError', '')
  return request({ method: 'POST', url: '/api/song/healthReportList', params: { id } })
    .then((data) => {
      if (!generation.isCurrent(ticket)) return { applied: false, stale: true }
      commit('updateHealthReportList', JSON.parse(data))
      commit('setHealthReportListSongId', id)
      commit('setHealthReportListError', '')
      return { applied: true, stale: false }
    })
    .catch((error) => {
      if (!generation.isCurrent(ticket)) return { applied: false, stale: true }
      // BR-381: ошибка фиксируется в state — без этого пустой список показывался
      // как «нарушений не найдено», то есть ложное «всё хорошо».
      commit('setHealthReportListError', (error && error.message) || 'Ошибка загрузки HR')
      console.log(error)
      return { applied: false, stale: false, error }
    })
    .finally(() => {
      // OpenProject #325: модалка HR живёт и при нуле записей, поэтому флаг загрузки
      // обязан сбрасываться и при ошибке — иначе останется «Загрузка…».
      if (generation.isCurrent(ticket)) commit('setHealthReportListIsLoading', false)
    })
}

/**
 * Компонент «Store».
 *
 * @see AGENTS.md
 */

/**
 * Проверяет, что строка отчёта относится к песне, для которой открыта модалка.
 *
 * @param {number|string|null|undefined} shownSongId — песня показанного списка.
 * @param {number|string} itemSongId — песня строки, по которой нажали «repair».
 * @returns {string|null} текст ошибки либо null, если ремонт допустим.
 */
export function repairTargetError(shownSongId, itemSongId) {
  if (shownSongId === null || shownSongId === undefined) {
    return 'Список отчётов ещё не загружен — ремонт отменён.'
  }
  if (String(itemSongId) !== String(shownSongId)) {
    return `Строка принадлежит песне ${itemSongId}, а открыта песня ${shownSongId} — ремонт отменён.`
  }
  return null
}

export default {
  state: {
    healthReportList: [],
    healthReportListIsLoading: false,
    // Песня, которой принадлежит показанный healthReportList (null — список ещё не загружен).
    healthReportListSongId: null,
    // BR-381: текст ошибки загрузки ('' — ошибки нет). Позволяет отличить
    // «нарушений не найдено» от «отчёт не получен».
    healthReportListError: '',
  },
  getters: {
    getHealthReportList(state) {
      return state.healthReportList
    },
    getHealthReportListIsLoading(state) {
      return state.healthReportListIsLoading
    },
    getHealthReportListSongId(state) {
      return state.healthReportListSongId
    },
    // BR-381: текст ошибки загрузки HR-списка ('' — ошибки нет).
    getHealthReportListError(state) {
      return state.healthReportListError
    },
  },
  mutations: {
    updateHealthReportList(state, result) {
      state.healthReportList = result
    },
    setHealthReportListIsLoading(state, isLoading) {
      state.healthReportListIsLoading = isLoading
    },
    setHealthReportListSongId(state, songId) {
      state.healthReportListSongId = songId === undefined ? null : songId
    },
    setHealthReportListError(state, message) {
      state.healthReportListError = message || ''
    },
  },
  actions: {
    loadHealthReportList(ctx, id) {
      return loadHealthReportListWith({
        request: promisedXMLHttpRequest,
        commit: (type, payload) => ctx.commit(type, payload),
        generation: healthReportGeneration,
        id,
      })
    },
    repairOneRecord(ctx, item) {
      // BR-380: fail-closed сверка «строка относится к открытой модалке». Если список
      // принадлежит другой песне (гонка ответов, старая строка в UI), ремонт ушёл бы
      // в чужую песню — молча. Лучше отказаться с понятной ошибкой.
      const guardError = repairTargetError(ctx.state.healthReportListSongId, item.songId)
      if (guardError) return Promise.reject(new Error(guardError))
      const params = {
        id: item.songId,
        healthReportTypeName: item.healthReportTypeName,
        healthReportStatusName: item.healthReportStatusName,
        description: item.description,
      }
      let request = { method: 'POST', url: '/api/song/executeHealthReportActions', params: params }
      promisedXMLHttpRequest(request)
        .then((_data) => {
          this.dispatch('loadHealthReportList', item.songId)
        })
        .catch((error) => {
          console.log(error)
        })
    },
    // Каскадное «Исправить всё» одним серверным вызовом: бэкенд сам выполняет всё решаемое сейчас
    // и по мере завершения задач ставит следующие шаги цепочки. Это заменяет прежний параллельный
    // цикл repairOneRecord по каждому отчёту (в т.ч. убирает гонку, когда getHealthReport на бэке
    // возвращал null из-за смены статуса между параллельными запросами).
    repairAllPromise(ctx, id) {
      let request = { method: 'POST', url: '/api/song/repairAll', params: { id: id } }
      promisedXMLHttpRequest(request)
        .then((_data) => {
          this.dispatch('loadHealthReportList', id)
        })
        .catch((error) => {
          console.log(error)
        })
    },
  },
}
