import { promisedXMLHttpRequest } from '../../lib/utils.js'
import { createRequestGeneration } from '../../lib/target-request-guard.js'

/**
 * Компонент «Store».
 *
 * @see AGENTS.md
 */

// Хелпер GET → JSON. promisedXMLHttpRequest не сериализует params в query-string для GET
// (устоявшийся квирк проекта), поэтому все параметры собираем в URL вручную (как Stats/store.js).
function getJson(url) {
  return promisedXMLHttpRequest({ method: 'GET', url, params: {} }).then((data) => JSON.parse(data))
}

// Строит query-string для GET /api/admin/processes из фильтров. Локальная функция
// (НЕ Vuex action — dispatch возвращает Promise, что ломает синхронную сборку URL).
function buildProcessQuery(filters) {
  const q = []
  if (filters.status && filters.status.length) {
    filters.status.forEach((s) => q.push(`status=${encodeURIComponent(s)}`))
  }
  if (filters.type && filters.type.length) {
    filters.type.forEach((t) => q.push(`type=${encodeURIComponent(t)}`))
  }
  if (filters.threadId) q.push(`threadId=${encodeURIComponent(filters.threadId)}`)
  if (filters.chainId) q.push(`chainId=${encodeURIComponent(filters.chainId)}`)
  if (filters.includeDeleted) q.push('includeDeleted=true')
  if (filters.name) q.push(`name=${encodeURIComponent(filters.name)}`)
  // R-006 (Lesson #10 iter #2 / RC iter #3): topLevelOnly = !filterChainId (не безусловно true).
  const topLevelOnly = filters.topLevelOnly !== undefined ? filters.topLevelOnly : !filters.chainId
  if (topLevelOnly) q.push('topLevelOnly=true')
  if (filters.parentId) q.push(`parentId=${encodeURIComponent(filters.parentId)}`)
  if (filters.limit) q.push(`limit=${encodeURIComponent(filters.limit)}`)
  if (filters.offset) q.push(`offset=${encodeURIComponent(filters.offset)}`)
  return q.join('&')
}

// BR-388 (аудит #336, пакет PKG-S2-17, OpenProject #380).
//
// ДЕФЕКТ. Массовые операции берут id из серверного snapshot `bulkSelectionIds`, но при
// смене фильтра snapshot не очищался и не помечался поколением: `loadProcesses` и
// `fetchBulkSelectionIds` уходили параллельно, `clearBulkSelection` не диспатчился
// нигде. Пока snapshot нового фильтра не пришёл (или если он упал — `catch` только
// `console.error` + `throw`, а `loadPage` промис не ловил), кнопки оставались активны
// (`canBulk` = `total > 0`), а `effectiveBulkIds` отдавал id ПРОШЛОГО фильтра: админ
// видел один набор, а «Массовое удаление» применялось к другому. После самой операции
// snapshot тоже не пересчитывался, поэтому повторная операция снова слала старые id.
//
// РЕШЕНИЕ (fail-closed). (1) Поколение snapshot: применяет результат только владелец
// последнего запроса. (2) Старт каждой загрузки помечает выборку устаревшей
// (`markBulkSelectionStale`) — сразу после смены фильтра массовые кнопки неактивны, пока
// не придёт snapshot ЭТОГО фильтра. (3) Сбой snapshot оставляет выборку пустой и
// неготовой, а не «прошлую». (4) Fallback компонента на `items` (только текущая
// страница) убран — он и создавал расхождение «показано N, удалено M».
//
// Функция вынесена из action, чтобы тест мог подставить транспорт
// (`src/components/Processes/__tests__/bulk-selection-generation.test.js`).
const bulkSelectionGeneration = createRequestGeneration()

// BR-403 (аудит #336, пакет PKG-S3-32, OpenProject #404): поколение запросов audit-лога.
// Ответ применяет только владелец последнего запроса — «побеждает последний запрос»,
// а не последний пришедший ответ.
const processAuditGeneration = createRequestGeneration()

/**
 * Загружает snapshot id массовой выборки, отбрасывая результаты устаревших запросов.
 *
 * @param {Object} params
 * @param {function(string): Promise<Object>} params.getJson
 * @param {function(Object): string} params.buildQuery
 * @param {function(string, any): void} params.commit
 * @param {{next: function(): number, isCurrent: function(number): boolean}} params.generation
 * @param {Object} params.filters
 * @returns {Promise<Object|null>} snapshot либо null, если ответ устарел/сбой.
 */
export function fetchBulkSelectionIdsWith({ getJson, buildQuery, commit, generation, filters }) {
  const ticket = generation.next()
  const query = buildQuery(filters)
  const url = `/api/admin/processes/bulk/snapshot${query ? `?${query}` : ''}`
  // Выборка прежнего фильтра не должна участвовать в массовых операциях ни секунды
  // дольше, чем нужно: помечаем устаревшей ДО запроса.
  commit('markBulkSelectionStale')
  return getJson(url)
    .then((result) => {
      // Пока шёл запрос, фильтр мог измениться — тогда применяет владелец нового.
      if (!generation.isCurrent(ticket)) return null
      commit('setBulkSelection', {
        ids: result.ids || [],
        total: result.total || 0,
        timestamp: new Date().toISOString(),
      })
      return result
    })
    .catch((error) => {
      if (generation.isCurrent(ticket)) {
        // Snapshot не получен — выборки нет. Никакого «оставим прежнюю».
        commit('markBulkSelectionStale')
      }
      // eslint-disable-next-line no-console
      console.error('[bulk] fetchBulkSelectionIds failed', url, error)
      return null
    })
}

export default {
  state: {
    processesDigest: [],
    processesDigestIsLoading: false,
    processIsWorking: false,
    processWillStopAfterThreadIsDone: false,
    workingProcessByThreadId: {},
    countWaiting: '...',
    // specs/129-hrpool-badge (OpenProject #129): размер приоритетной очереди
    // HealthReportBatchPool на бэке. Обновляется через SSE HEALTH_REPORT_POOL_COUNT
    // (см. action setHealthReportPoolCount ниже). Показывается зелёным бейджем
    // в ProcessWorker.vue слева от серого бейджа countWaiting.
    healthReportPoolCount: '...',
    // specs/132-hrwaiting-pool (OpenProject #132): размер реального backend-пула
    // WAITING-задач HealthReportBatchPool.waitingQueue (второй пул, 20 worker'ов,
    // LIFO). Обновляется через SSE HEALTH_REPORT_WAITING_POOL_SIZE. Показывается
    // голубым бейджем в ProcessWorker.vue справа сверху над кнопкой Старт/Стоп.
    healthReportWaitingPoolSize: 0,
    // specs/118 #397: размер cache-очереди StorageMetadataCache (см. GET /api/health/cache/cache-queue-size).
    cacheQueueSize: 0,
    // Текущая страница пагинации в ProcessesTable. Сохраняем в сторе, чтобы при уходе с компонента
    // и возврате — открывалась страница, на которой остановился пользователь.
    processesTableCurrentPage: 1,
    // specs/315-admin-ui-karaoke-process-v5 (US1..US5): админ-состояние.
    items: [],
    total: 0,
    processesLoading: false,
    processesError: null,
    expanded: {},
    childrenByParent: {},
    childrenLoading: {},
    currentProcessForEdit: null,
    currentProcessAudit: [],
    // BR-403: id процесса, к которому относится `currentProcessAudit`. Ответ с
    // чужим id отбрасывается — иначе в модалке audit процесса B видны записи A.
    currentProcessAuditId: null,
    // BR-403: явное состояние загрузки audit-лога (иначе «Нет записей за выбранный
    // период» отображалось и во время загрузки, и после отказа).
    currentProcessAuditIsLoading: false,
    currentProcessAuditError: '',
    // --- specs/319-process-bulk-actions-v2: bulk state (FR-002, FR-007, US3) ---
    bulkSelectionIds: [], // Array<Number> — snapshot id по текущему фильтру (≤ 10000)    bulkSelectionTotal: 0, // Number — total в БД по фильтру (может > ids.length из-за cap)
    bulkSelectionTimestamp: null, // ISO String — когда сделан snapshot
    // BR-388: snapshot готов для ТЕКУЩЕГО фильтра. Пока false — массовые операции запрещены
    // (fail-closed): иначе смена фильтра оставляла в state id прошлой выборки.
    bulkSelectionReady: false,
    bulkOperationInProgress: false, // Boolean — флаг «операция идёт»
    bulkOperationReport: null, // Object | null — последний BulkOperationReport
    bulkTaskStatus: null, // Object | null — для async: { taskId, status, processedCount, ... }
    // BR-402: последние применённые фильтры списка. Нужны, чтобы перезагрузить
    // `items` по завершении массовой операции (значения полей изменились на сервере).
    processesFilters: null,
  },
  getters: {
    getWorkingProcessForThreads: (state) => (includedThreadId, excludedThreadId) => {
      const entries = Object.values(state.workingProcessByThreadId)
      if (includedThreadId && includedThreadId.length !== 0) {
        return entries.find((p) => includedThreadId.includes(p.threadId))
      }
      if (excludedThreadId && excludedThreadId.length !== 0) {
        return entries.find((p) => !excludedThreadId.includes(p.threadId))
      }
      return entries[0]
    },
    getProcessesDigest(state) {
      return state.processesDigest
    },
    getProcessesDigestIds(state) {
      return state.processesDigest ? state.processesDigest.flatMap((process) => process.id) : []
    },
    getProcessesDigestIsLoading(state) {
      return state.processesDigestIsLoading
    },
    getProcessIsWorking(state) {
      return state.processIsWorking
    },
    getProcessWillStopAfterThreadIsDone(state) {
      return state.processWillStopAfterThreadIsDone
    },
    getCountWaiting(state) {
      return state.countWaiting
    },
    // specs/129-hrpool-badge (OpenProject #129).
    getHealthReportPoolCount(state) {
      return state.healthReportPoolCount
    },
    // specs/132-hrwaiting-pool (OpenProject #132).
    getHealthReportWaitingPoolSize(state) {
      return state.healthReportWaitingPoolSize
    },
    // specs/118 #397: размер cache-очереди StorageMetadataCache.
    getCacheQueueSize(state) {
      return state.cacheQueueSize
    },
    getProcessesTableCurrentPage(state) {
      return state.processesTableCurrentPage
    },
    // --- admin (US1..US5) ---
    getProcessesItems(state) {
      return state.items
    },
    getProcessesTotal(state) {
      return state.total
    },
    getProcessesLoading(state) {
      return state.processesLoading
    },
    getProcessesError(state) {
      return state.processesError
    },
    isProcessExpanded: (state) => (id) => !!state.expanded[id],
    getProcessChildren: (state) => (parentId) => state.childrenByParent[parentId] || [],
    isChildrenLoading: (state) => (parentId) => !!state.childrenLoading[parentId],
    getCurrentProcessForEdit(state) {
      return state.currentProcessForEdit
    },
    getCurrentProcessAudit(state) {
      return state.currentProcessAudit
    },
    // BR-403: id процесса, которому принадлежит audit-лог.
    getCurrentProcessAuditId(state) {
      return state.currentProcessAuditId
    },
    // BR-403: audit грузится — показываем индикатор, а не «нет записей».
    getCurrentProcessAuditIsLoading(state) {
      return state.currentProcessAuditIsLoading
    },
    // BR-403: текст ошибки загрузки audit-лога.
    getCurrentProcessAuditError(state) {
      return state.currentProcessAuditError
    },
    // --- specs/319-process-bulk-actions-v2: bulk getters (FR-001, FR-002) ---
    getBulkSelectionIds: (state) => state.bulkSelectionIds,
    getBulkSelectionTotal: (state) => state.bulkSelectionTotal,
    getBulkSelectionTimestamp: (state) => state.bulkSelectionTimestamp,
    getBulkSelectionCount: (state) => state.bulkSelectionIds.length,
    getCanBulk: (state) => state.bulkSelectionIds.length >= 1,
    // BR-388: готовность snapshot текущего фильтра — отдельно от непустоты списка.
    getIsBulkSelectionReady: (state) => state.bulkSelectionReady,
    getBulkOperationInProgress: (state) => state.bulkOperationInProgress,
    getBulkOperationReport: (state) => state.bulkOperationReport,
    getBulkTaskStatus: (state) => state.bulkTaskStatus,
  },
  mutations: {
    updateProcessesDigests(state, result) {
      state.processesDigest = result.processesDigests
    },
    setProcessesDigestIsLoading(state, isLoading) {
      state.processesDigestIsLoading = isLoading
    },
    updateProcessesDigestByIds(state, processesAndIndexesForUpdate) {
      // console.log('songsAndIndexesForUpdate', songsAndIndexesForUpdate)

      for (let processesAndIndexesFromRest of processesAndIndexesForUpdate) {
        for (let i = 0; i < state.processesDigest.length; i++) {
          let processInDigest = state.processesDigest[i]
          if (processInDigest.id === processesAndIndexesFromRest.song.id) {
            state.processesDigest.splice(i, 1, processesAndIndexesFromRest.song)
          }
        }
      }
    },
    updateProcessByUserEvent(state, userEventData) {
      let processId = userEventData.recordId
      for (let i = 0; i < state.processesDigest.length; i++) {
        let processInDigest = state.processesDigest[i]
        if (processInDigest.id === processId) {
          state.processesDigest.splice(i, 1, userEventData.record)
        }
      }
      const threadId = userEventData.record.threadId
      // В шапке показываем прогресс только реально выполняющихся (WORKING) заданий. Любой другой
      // статус (WAITING/DONE/ERROR/CREATING) убирает запись — в т.ч. при форс-стопе, когда поток
      // асинхронно возвращает задание в WAITING уже после очистки карты по PROCESS_WORKER_STATE.
      if (userEventData.record.status === 'WORKING') {
        state.workingProcessByThreadId[threadId] = Object.assign({}, userEventData.record)
      } else {
        delete state.workingProcessByThreadId[threadId]
      }
    },
    addProcessByUserEvent(_state, _userEventData) {
      // console.log('mutations addProcessByUserEvent from store.js ProcessesBv')
      // console.log('Событие добавления процесса: ', userEventData)
      // console.log('Процесс добавлен не будет, установлена заглушка.')
      // let processId = userEventData.recordId;
      // let processPriority = userEventData.record.priority;
      // for (let i = 0; i < state.processesDigest.length; i++) {
      //     let processInDigest = state.processesDigest[i];
      //     if (processPriority < processInDigest.priority || (processPriority === processInDigest.priority && processId < processInDigest.id)) {
      //         state.processesDigest.splice(i,0,userEventData.record);
      //         return;
      //     }
      // }
      // state.processesDigest.splice(state.processesDigest.length-1,0,userEventData.record);
    },
    deleteProcessByUserEvent(state, userEventData) {
      // console.log('Событие удаления процесса: ', userEventData)
      let processId = userEventData.recordId
      for (let i = 0; i < state.processesDigest.length; i++) {
        let processInDigest = state.processesDigest[i]
        if (processInDigest.id === processId) {
          state.processesDigest.splice(i, 1)
        }
      }
    },
    setProcessIsWorking(state, processIsWorking) {
      state.processIsWorking = processIsWorking
    },
    setCountWaiting(state, userEventData) {
      state.countWaiting = userEventData.countWaiting
    },
    // specs/129-hrpool-badge (OpenProject #129). payload: { count: Long }.
    setHealthReportPoolCount(state, userEventData) {
      state.healthReportPoolCount = userEventData.count
    },
    // specs/132-hrwaiting-pool (OpenProject #132). payload: { count: Long }.
    setHealthReportWaitingPoolSize(state, userEventData) {
      state.healthReportWaitingPoolSize = userEventData.count
    },
    // specs/118 #397: установить размер cache-очереди.
    setCacheQueueSize(state, size) {
      state.cacheQueueSize = typeof size === 'number' ? size : 0
    },
    setProcessWillStopAfterThreadIsDone(state, processWillStopAfterThreadIsDone) {
      state.processWillStopAfterThreadIsDone = processWillStopAfterThreadIsDone
    },
    updateProcessWorkerStateByUserEvent(state, userEventData) {
      // console.log('Событие изменения статуса воркера процесса: ', userEventData)
      state.processIsWorking = userEventData.work
      state.processWillStopAfterThreadIsDone = userEventData.stopAfterThreadIsDone
      // Воркер полностью остановлен — рабочих процессов быть не может, чистим прогресс-бар шапки
      // (в т.ч. при форс-стопе, когда задания возвращаются в WAITING без DONE/ERROR-события).
      if (!userEventData.work) {
        state.workingProcessByThreadId = {}
      }
    },
    setProcessesTableCurrentPage(state, page) {
      state.processesTableCurrentPage = page
    },
    // --- admin (US1..US5) ---
    setProcessesItems(state, items) {
      state.items = items
    },
    setProcessesTotal(state, total) {
      state.total = total
    },
    setProcessesLoading(state, loading) {
      state.processesLoading = loading
    },
    setProcessesError(state, error) {
      state.processesError = error
    },
    setProcessChildren(state, { parentId, children }) {
      state.childrenByParent[parentId] = children
    },
    toggleProcessExpanded(state, id) {
      state.expanded[id] = !state.expanded[id]
    },
    setChildrenLoading(state, { parentId, loading }) {
      state.childrenLoading[parentId] = loading
    },
    setCurrentProcessForEdit(state, process) {
      state.currentProcessForEdit = process
    },
    setCurrentProcessAudit(state, items) {
      state.currentProcessAudit = items
    },
    // BR-403: снимок audit-лога чистится перед запросом — иначе в модалке видны
    // записи ПРЕДЫДУЩЕГО процесса (в том числе после неудачного запроса).
    clearCurrentProcessAudit(state) {
      state.currentProcessAudit = []
    },
    // BR-403: привязка audit-лога к процессу.
    setCurrentProcessAuditId(state, id) {
      state.currentProcessAuditId = id
    },
    setCurrentProcessAuditIsLoading(state, isLoading) {
      state.currentProcessAuditIsLoading = !!isLoading
    },
    setCurrentProcessAuditError(state, message) {
      state.currentProcessAuditError = message || ''
    },
    // BR-402: последние применённые фильтры — для перезагрузки списка после
    // завершения массовой операции.
    setProcessesFilters(state, filters) {
      state.processesFilters = filters
    },
    // --- specs/319-process-bulk-actions-v2: bulk mutations (T008) ---
    setBulkSelection(state, { ids, total, timestamp }) {
      state.bulkSelectionIds = ids || []
      state.bulkSelectionTotal = total || 0
      state.bulkSelectionTimestamp = timestamp || new Date().toISOString()
      // BR-388: snapshot принадлежит текущему фильтру — только теперь bulk разрешён.
      state.bulkSelectionReady = true
    },
    clearBulkSelection(state) {
      state.bulkSelectionIds = []
      state.bulkSelectionTotal = 0
      state.bulkSelectionTimestamp = null
      state.bulkSelectionReady = false
    },
    // BR-388: «выборка устарела» — id прежнего фильтра больше не должны участвовать
    // в массовых операциях, пока не придёт snapshot нового.
    markBulkSelectionStale(state) {
      state.bulkSelectionIds = []
      state.bulkSelectionTotal = 0
      state.bulkSelectionTimestamp = null
      state.bulkSelectionReady = false
    },
    setBulkOperationInProgress(state, val) {
      state.bulkOperationInProgress = !!val
    },
    setBulkOperationReport(state, report) {
      state.bulkOperationReport = report
    },
    clearBulkOperationReport(state) {
      state.bulkOperationReport = null
    },
    setBulkTaskStatus(state, taskStatus) {
      state.bulkTaskStatus = taskStatus
    },
  },
  actions: {
    updateProcessesDigestByIds(ctx, payload) {
      ctx.commit('updateProcessesDigestByIds', payload.processesAndIndexesForUpdate)
    },
    updateProcessByUserEvent(ctx, userEventData) {
      ctx.commit('updateProcessByUserEvent', userEventData)
    },
    addProcessByUserEvent(ctx, userEventData) {
      // console.log('actions addProcessByUserEvent from store.js ProcessesBv')
      ctx.commit('addProcessByUserEvent', userEventData)
    },
    deleteProcessByUserEvent(ctx, userEventData) {
      ctx.commit('deleteProcessByUserEvent', userEventData)
    },
    loadProcessesDigests(ctx, params) {
      let request = { method: 'POST', url: '/api/processesdigests', params: params }
      ctx.commit('setProcessesDigestIsLoading', true)
      promisedXMLHttpRequest(request)
        .then((data) => {
          let result = JSON.parse(data)
          ctx.commit('updateProcessesDigests', result)
          ctx.commit('setProcessesDigestIsLoading', false)
        })
        .catch((error) => {
          console.log(error)
        })
    },
    deleteDoneProcessesPromise: () => {
      let request = { method: 'POST', url: '/api/processes/deletedone' }
      return promisedXMLHttpRequest(request)
    },
    startStopProcessWorker: () => {
      let request = { method: 'POST', url: '/api/processes/workerstartstop' }
      promisedXMLHttpRequest(request)
    },
    forceStopProcessWorker: () => {
      let request = { method: 'POST', url: '/api/processes/workerforcestop' }
      promisedXMLHttpRequest(request)
    },
    getProcessesWorkerStatusPromise: () => {
      let request = { method: 'POST', url: '/api/processes/workerstatus' }
      return promisedXMLHttpRequest(request)
    },
    getProcessesCountWaitingPromise: () => {
      let request = { method: 'POST', url: '/api/processes/countwaiting' }
      return promisedXMLHttpRequest(request)
    },
    // specs/118 #397: запросить размер cache-очереди.
    getCacheQueueSizePromise: () => {
      const request = { method: 'GET', url: '/api/health/cache/cache-queue-size' }
      return promisedXMLHttpRequest(request)
    },
    setProcessIsWorking(ctx, processIsWorking) {
      ctx.commit('setProcessIsWorking', processIsWorking)
    },
    setCountWaiting(ctx, userEventData) {
      ctx.commit('setCountWaiting', userEventData)
    },
    // specs/129-hrpool-badge (OpenProject #129).
    setHealthReportPoolCount(ctx, userEventData) {
      ctx.commit('setHealthReportPoolCount', userEventData)
    },
    // specs/132-hrwaiting-pool (OpenProject #132).
    setHealthReportWaitingPoolSize(ctx, userEventData) {
      ctx.commit('setHealthReportWaitingPoolSize', userEventData)
    },
    // specs/118 #397: action-обёртка для setCacheQueueSize.
    setCacheQueueSize(ctx, size) {
      ctx.commit('setCacheQueueSize', size)
    },
    setProcessWillStopAfterThreadIsDone(ctx, processWillStopAfterThreadIsDone) {
      ctx.commit('setProcessWillStopAfterThreadIsDone', processWillStopAfterThreadIsDone)
    },
    updateProcessWorkerStateByUserEvent(ctx, userEventData) {
      ctx.commit('updateProcessWorkerStateByUserEvent', userEventData)
    },
    // GET /api/admin/processes — список с фильтрами (US1, T013).
    loadProcesses(ctx, filters = {}) {
      ctx.commit('setProcessesLoading', true)
      ctx.commit('setProcessesError', null)
      // BR-402: запоминаем фильтры — по ним список перезагружается после
      // завершения массовой операции.
      ctx.commit('setProcessesFilters', filters)
      const query = buildProcessQuery(filters)
      const url = `/api/admin/processes${query ? `?${query}` : ''}`
      return getJson(url)
        .then((result) => {
          ctx.commit('setProcessesItems', result.items || [])
          ctx.commit('setProcessesTotal', result.total || 0)
          ctx.commit('setProcessesLoading', false)
          return result
        })
        .catch((error) => {
          ctx.commit('setProcessesError', error.message || 'Ошибка загрузки процессов')
          ctx.commit('setProcessesLoading', false)
          throw error
        })
    },
    // GET /api/admin/processes?topLevelOnly=false&parentId=... — lazy load детей (US1, T014).
    loadChildren(ctx, parentId) {
      ctx.commit('setChildrenLoading', { parentId, loading: true })
      const url = `/api/admin/processes?topLevelOnly=false&parentId=${encodeURIComponent(parentId)}`
      return getJson(url)
        .then((result) => {
          ctx.commit('setProcessChildren', { parentId, children: result.items || [] })
          ctx.commit('setChildrenLoading', { parentId, loading: false })
          return result
        })
        .catch((error) => {
          ctx.commit('setChildrenLoading', { parentId, loading: false })
          throw error
        })
    },
    toggleProcessExpanded(ctx, id) {
      ctx.commit('toggleProcessExpanded', id)
    },
    // GET /api/admin/processes/{id} — один процесс для edit (US2).
    loadProcessForEdit(ctx, id) {
      return getJson(`/api/admin/processes/${id}`)
        .then((process) => {
          ctx.commit('setCurrentProcessForEdit', process)
          return process
        })
        .catch((error) => {
          throw error
        })
    },
    // POST /api/admin/processes/{id}/edit — редактирование (US2, T020).
    editProcess(ctx, { id, changes }) {
      return promisedXMLHttpRequest({
        method: 'POST',
        url: `/api/admin/processes/${id}/edit`,
        body: changes,
      })
        .then((data) => {
          const updated = JSON.parse(data)
          const items = ctx.state.items.map((item) => (item.id === id ? updated : item))
          ctx.commit('setProcessesItems', items)
          return updated
        })
        .catch((error) => {
          throw error
        })
    },
    // POST /api/admin/processes/{id}/delete — soft-delete (US3, T027).
    deleteProcess(ctx, id) {
      return promisedXMLHttpRequest({
        method: 'POST',
        url: `/api/admin/processes/${id}/delete`,
        body: {},
      })
        .then((data) => {
          const items = ctx.state.items.filter((item) => item.id !== id)
          ctx.commit('setProcessesItems', items)
          return JSON.parse(data)
        })
        .catch((error) => {
          throw error
        })
    },
    // POST /api/admin/processes/{id}/retry — retry ERROR (US4, T032).
    retryProcess(ctx, id) {
      return promisedXMLHttpRequest({
        method: 'POST',
        url: `/api/admin/processes/${id}/retry`,
        body: {},
      })
        .then((data) => {
          const updated = JSON.parse(data)
          const items = ctx.state.items.map((item) => (item.id === id ? updated : item))
          ctx.commit('setProcessesItems', items)
          return updated
        })
        .catch((error) => {
          throw error
        })
    },
    // GET /api/admin/processes/{id}/audit?days=30 — audit-лог (US5, T037).
    /**
     * BR-403: загрузка audit-лога процесса.
     *
     * ДЕФЕКТ. Ответ коммитился в общий `currentProcessAudit` без сверки с id процесса:
     * открыв audit процесса A, закрыв и открыв audit процесса B, админ видел записи A
     * под заголовком B, пока не приходил ответ; при отказе B записи A оставались в
     * таблице и читались как лог B. Два быстрых «Обновить» с разными `days` могли
     * прийти не по порядку — побеждал последний ПРИШЕДШИЙ ответ.
     *
     * РЕШЕНИЕ. Снимок чистится ДО запроса; ответ применяется только владельцем текущего
     * поколения И только если id совпадает с текущим; есть явное состояние загрузки.
     */
    loadAudit(ctx, { id, days = 30 }) {
      const ticket = processAuditGeneration.next()
      ctx.commit('clearCurrentProcessAudit')
      ctx.commit('setCurrentProcessAuditId', id)
      ctx.commit('setCurrentProcessAuditIsLoading', true)
      ctx.commit('setCurrentProcessAuditError', '')
      return getJson(`/api/admin/processes/${id}/audit?days=${days}`)
        .then((result) => {
          if (!processAuditGeneration.isCurrent(ticket)) return { applied: false, stale: true }
          if (ctx.state.currentProcessAuditId !== id) return { applied: false, stale: true }
          ctx.commit('setCurrentProcessAudit', result.items || [])
          return { applied: true, stale: false, result }
        })
        .catch((error) => {
          if (processAuditGeneration.isCurrent(ticket)) {
            ctx.commit('setCurrentProcessAuditError', error.message || 'Ошибка загрузки audit-лога')
          }
          throw error
        })
        .finally(() => {
          if (processAuditGeneration.isCurrent(ticket)) {
            ctx.commit('setCurrentProcessAuditIsLoading', false)
          }
        })
    },
    // --- specs/319-process-bulk-actions-v2: bulk actions ---

    /**
     * GET /api/admin/processes/bulk/snapshot — snapshot id по текущему фильтру.
     *
     * @param {Object} filters - {status[], type[], threadId, chainId, includeDeleted, name, topLevelOnly, parentId}
     * @see specs/319-process-bulk-actions-v2/contracts/admin-process-bulk-rest-api.md (EP-5)
     */
    /**
     * BR-388: snapshot id для массовых операций, привязанный к поколению запроса.
     * Владелец только последнего запроса; смена фильтра и любой сбой очищают выборку.
     */
    fetchBulkSelectionIds(ctx, filters = {}) {
      return fetchBulkSelectionIdsWith({
        getJson,
        buildQuery: buildProcessQuery,
        commit: (type, payload) => ctx.commit(type, payload),
        generation: bulkSelectionGeneration,
        filters,
      })
    },

    /**
     * POST /api/admin/processes/bulk-update — sync bulk-edit (≤ 1000 процессов).
     *
     * @param {Object} payload - {ids: Number[], field: String, value: Any, batchId?: UUID}
     * @returns {Promise<BulkOperationReport>}
     * @see specs/319-process-bulk-actions-v2/contracts/admin-process-bulk-rest-api.md (EP-1)
     */
    bulkUpdateProcesses(ctx, { ids, field, value, batchId = null }) {
      ctx.commit('setBulkOperationInProgress', true)
      // По аналогии с createMP3KaraokeForAllPromise (см. webvue3/src/components/Songs/store.js:2802):
      // массив ids передаётся через `.join(';')`, всё тело — через `params:` (form-urlencoded).
      // Это обходит баг хелпера `promisedXMLHttpRequest` с дублем Content-Type
      // (default lowercase + body override capital — разные ключи в XHR → 415).
      return promisedXMLHttpRequest({
        method: 'POST',
        url: '/api/admin/processes/bulk-update',
        params: {
          ids: ids.join(';'),
          field,
          value: String(value),
          batchId: batchId || '',
        },
      })
        .then((data) => {
          const report = JSON.parse(data)
          ctx.commit('setBulkOperationReport', report)
          return report
        })
        .catch((error) => {
          throw error
        })
        .finally(() => {
          ctx.commit('setBulkOperationInProgress', false)
        })
    },

    /**
     * POST /api/admin/processes/bulk-delete — sync hard-delete (≤ 1000 процессов).
     *
     * @param {Object} payload - {ids: Number[], batchId?: UUID}
     * @returns {Promise<BulkOperationReport>}
     * @see specs/319-process-bulk-actions-v2/contracts/admin-process-bulk-rest-api.md (EP-2)
     */
    bulkDeleteProcesses(ctx, { ids, batchId = null }) {
      ctx.commit('setBulkOperationInProgress', true)
      return promisedXMLHttpRequest({
        method: 'POST',
        url: '/api/admin/processes/bulk-delete',
        params: {
          ids: ids.join(';'),
          batchId: batchId || '',
        },
      })
        .then((data) => {
          const report = JSON.parse(data)
          ctx.commit('setBulkOperationReport', report)
          // Удалить удалённые id из items (если они были видны в таблице).
          const failedIds = new Set((report.errors || []).map((e) => e.processId))
          const deletedIds = ids.filter((id) => !failedIds.has(id))
          const items = ctx.state.items.filter((item) => !deletedIds.includes(item.id))
          ctx.commit('setProcessesItems', items)
          // BR-402: список перезагружается по последним фильтрам — страница
          // наполняется заново (иначе после удаления всех строк остаётся пустая).
          ctx.dispatch('reloadProcessesItems')
          return report
        })
        .catch((error) => {
          throw error
        })
        .finally(() => {
          ctx.commit('setBulkOperationInProgress', false)
        })
    },

    /**
     * Сброс отчёта после показа админу (US3, T025).
     */
    clearBulkOperationReport(ctx) {
      ctx.commit('clearBulkOperationReport')
    },

    /**
     * POST /api/admin/processes/bulk-update-async — async bulk-edit (US4, T031).
     *
     * Returns `{ taskId, statusUrl }`. Стартует polling через `pollBulkTask`.
     */
    bulkUpdateProcessesAsync(ctx, { ids, field, value, batchId = null }) {
      ctx.commit('setBulkOperationInProgress', true)
      return promisedXMLHttpRequest({
        method: 'POST',
        url: '/api/admin/processes/bulk-update-async',
        params: {
          ids: ids.join(';'),
          field,
          value: String(value),
          batchId: batchId || '',
        },
      })
        .then((data) => {
          const { taskId, statusUrl } = JSON.parse(data)
          ctx.commit('setBulkTaskStatus', {
            taskId,
            statusUrl,
            status: 'RUNNING',
            action: 'bulk_update_field',
            totalCount: ids.length,
            processedCount: 0,
          })
          // BR-402: в опрос уходят id, с которыми операция ЗАПУЩЕНА. Раньше в
          // терминальной ветке брались текущие `bulkSelectionIds` — за время ожидания
          // админ мог сменить фильтр, и из таблицы вычитались бы ЧУЖИЕ id.
          ctx.dispatch('pollBulkTask', {
            taskId,
            statusUrl,
            ids: ids.slice(),
            action: 'bulk_update_field',
          })
          return { taskId, statusUrl }
        })
        .catch((error) => {
          // BR-401: терминальное состояние фиксируется в state — админ видит отказ,
          // а не «бесконечный RUNNING».
          ctx.commit('setBulkTaskStatus', {
            status: 'FAILED',
            action: 'bulk_update_field',
            totalCount: ids.length,
            processedCount: 0,
            error: error.message || 'Ошибка запуска массового обновления',
          })
          ctx.commit('setBulkOperationReport', {
            batchId: null,
            action: 'bulk_update_field',
            requested: ids.length,
            succeeded: 0,
            failed: ids.length,
            status: 'FAILED',
            error: error.message || 'Ошибка запуска массового обновления',
            errors: [
              {
                processId: null,
                reason: error.message || 'Ошибка запуска массового обновления',
                errorCode: 'START_FAILED',
              },
            ],
            durationMs: 0,
          })
          ctx.commit('setBulkOperationInProgress', false)
          throw error
        })
    },

    /**
     * POST /api/admin/processes/bulk-delete-async — async bulk-delete (US4, T031).
     */
    bulkDeleteProcessesAsync(ctx, { ids, batchId = null }) {
      ctx.commit('setBulkOperationInProgress', true)
      return promisedXMLHttpRequest({
        method: 'POST',
        url: '/api/admin/processes/bulk-delete-async',
        params: {
          ids: ids.join(';'),
          batchId: batchId || '',
        },
      })
        .then((data) => {
          const { taskId, statusUrl } = JSON.parse(data)
          ctx.commit('setBulkTaskStatus', {
            taskId,
            statusUrl,
            status: 'RUNNING',
            action: 'bulk_delete',
            totalCount: ids.length,
            processedCount: 0,
          })
          // BR-402: id операции фиксируются при запуске.
          ctx.dispatch('pollBulkTask', {
            taskId,
            statusUrl,
            ids: ids.slice(),
            action: 'bulk_delete',
          })
          return { taskId, statusUrl }
        })
        .catch((error) => {
          // BR-401: терминальное состояние фиксируется в state (см. bulkUpdateProcessesAsync).
          ctx.commit('setBulkTaskStatus', {
            status: 'FAILED',
            action: 'bulk_delete',
            totalCount: ids.length,
            processedCount: 0,
            error: error.message || 'Ошибка запуска массового удаления',
          })
          ctx.commit('setBulkOperationReport', {
            batchId: null,
            action: 'bulk_delete',
            requested: ids.length,
            succeeded: 0,
            failed: ids.length,
            status: 'FAILED',
            error: error.message || 'Ошибка запуска массового удаления',
            errors: [
              {
                processId: null,
                reason: error.message || 'Ошибка запуска массового удаления',
                errorCode: 'START_FAILED',
              },
            ],
            durationMs: 0,
          })
          ctx.commit('setBulkOperationInProgress', false)
          throw error
        })
    },

    /**
     * Polling helper для async-операций (US4, T031).
     *
     * Опрос каждые 2 сек через `setTimeout` (не setInterval — переживает ручную отмену).
     * На terminal status — коммитит `SET_BULK_OPERATION_REPORT` для показа в UI.
     *
     * BR-401: при отказе опроса фиксируется ТЕРМИНАЛЬНОЕ состояние (`status: 'FAILED'`
     * в `bulkTaskStatus` и отчёт в `bulkOperationReport`). Раньше `catch` сбрасывал
     * только флаг «операция идёт», а `bulkTaskStatus` навсегда оставался `RUNNING`,
     * отчёта не было, а исключение перебрасывалось из промиса без потребителя
     * (unhandled rejection). Из таймерного цикла исключение не перебрасывается.
     *
     * BR-402: `ids` — id, с которыми операция ЗАПУЩЕНА. Из `items` вычитаются только
     * они (раньше брались текущие `bulkSelectionIds` — за время ожидания админ мог
     * сменить фильтр, и из таблицы вычитались бы чужие id). Для `bulk_update` список
     * перезагружается: значения полей изменились на сервере.
     *
     * @param {Object} payload
     * @param {string} payload.taskId
     * @param {string} payload.statusUrl
     * @param {Array<Number>} payload.ids — id операции.
     * @param {string} payload.action — 'bulk_delete' | 'bulk_update_field'.
     */
    pollBulkTask(ctx, { taskId, statusUrl, ids = [], action = null }) {
      const operationIds = Array.isArray(ids) ? ids.slice() : []
      const poll = () => {
        return getJson(statusUrl || `/api/admin/tasks/${taskId}`)
          .then((status) => {
            ctx.commit('setBulkTaskStatus', status)
            if (status.status === 'RUNNING') {
              setTimeout(poll, 2000)
            } else {
              // Terminal: строим BulkOperationReport для UI
              ctx.commit('setBulkOperationReport', {
                batchId: taskId,
                action: status.action || action,
                requested:
                  status.totalCount !== undefined ? status.totalCount : operationIds.length,
                succeeded: status.succeededCount,
                failed: status.failedCount,
                status: status.status,
                errors: status.errors || [],
                durationMs:
                  status.finishedAt && status.startedAt ? status.finishedAt - status.startedAt : 0,
              })
              ctx.commit('setBulkOperationInProgress', false)
              // BR-402: удаляются только id ЭТОЙ операции.
              if (status.action === 'bulk_delete' || action === 'bulk_delete') {
                const failedIds = new Set((status.errors || []).map((e) => e.processId))
                const deletedIds = operationIds.filter((id) => !failedIds.has(id))
                const items = ctx.state.items.filter((item) => !deletedIds.includes(item.id))
                ctx.commit('setProcessesItems', items)
              }
              // BR-402: после bulk_update список перезагружается — иначе в таблице
              // остались бы прежние значения изменённых полей.
              if (status.action === 'bulk_update_field' || action === 'bulk_update_field') {
                ctx.dispatch('reloadProcessesItems')
              }
            }
          })
          .catch((error) => {
            // BR-401: терминальное состояние — админ видит отказ, а не вечный RUNNING.
            const reason = error.message || 'Ошибка опроса задачи'
            ctx.commit('setBulkTaskStatus', {
              taskId,
              statusUrl,
              status: 'FAILED',
              action: action,
              totalCount: operationIds.length,
              processedCount: 0,
              error: reason,
            })
            ctx.commit('setBulkOperationReport', {
              batchId: taskId,
              action: action || 'unknown',
              requested: operationIds.length,
              succeeded: 0,
              failed: operationIds.length,
              status: 'FAILED',
              error: reason,
              errors: [{ processId: null, reason: reason, errorCode: 'POLL_FAILED' }],
              durationMs: 0,
            })
            ctx.commit('setBulkOperationInProgress', false)
            // Исключение НЕ перебрасывается: из таймерного цикла оно ушло бы в
            // unhandled rejection, а состояние уже зафиксировано выше.
          })
      }
      return poll()
    },

    /**
     * BR-402: перезагрузка списка процессов по последним применённым фильтрам.
     * Нужна после массового обновления: значения полей изменились на сервере, а
     * локальный `items` — устаревший снимок.
     */
    reloadProcessesItems(ctx) {
      const filters = ctx.state.processesFilters
      if (!filters) return Promise.resolve(null)
      return ctx.dispatch('loadProcesses', filters)
    },
  },
}
