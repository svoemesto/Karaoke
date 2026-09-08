import { promisedXMLHttpRequest } from '../../lib/utils'

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

export default {
  state: {
    processesDigest: [],
    processesDigestIsLoading: false,
    processIsWorking: false,
    processWillStopAfterThreadIsDone: false,
    workingProcessByThreadId: {},
    countWaiting: '...',
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
    // --- specs/319-process-bulk-actions-v2: bulk state (FR-002, FR-007, US3) ---
    bulkSelectionIds: [], // Array<Number> — snapshot id по текущему фильтру (≤ 10000)
    bulkSelectionTotal: 0, // Number — total в БД по фильтру (может > ids.length из-за cap)
    bulkSelectionTimestamp: null, // ISO String — когда сделан snapshot
    bulkOperationInProgress: false, // Boolean — флаг «операция идёт»
    bulkOperationReport: null, // Object | null — последний BulkOperationReport
    bulkTaskStatus: null, // Object | null — для async: { taskId, status, processedCount, ... }
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
    // --- specs/319-process-bulk-actions-v2: bulk getters (FR-001, FR-002) ---
    getBulkSelectionIds: (state) => state.bulkSelectionIds,
    getBulkSelectionTotal: (state) => state.bulkSelectionTotal,
    getBulkSelectionTimestamp: (state) => state.bulkSelectionTimestamp,
    getBulkSelectionCount: (state) => state.bulkSelectionIds.length,
    getCanBulk: (state) => state.bulkSelectionIds.length >= 1,
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
    // --- specs/319-process-bulk-actions-v2: bulk mutations (T008) ---
    setBulkSelection(state, { ids, total, timestamp }) {
      state.bulkSelectionIds = ids || []
      state.bulkSelectionTotal = total || 0
      state.bulkSelectionTimestamp = timestamp || new Date().toISOString()
    },
    clearBulkSelection(state) {
      state.bulkSelectionIds = []
      state.bulkSelectionTotal = 0
      state.bulkSelectionTimestamp = null
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
    setProcessIsWorking(ctx, processIsWorking) {
      ctx.commit('setProcessIsWorking', processIsWorking)
    },
    setCountWaiting(ctx, userEventData) {
      ctx.commit('setCountWaiting', userEventData)
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
    loadAudit(ctx, { id, days = 30 }) {
      return getJson(`/api/admin/processes/${id}/audit?days=${days}`)
        .then((result) => {
          ctx.commit('setCurrentProcessAudit', result.items || [])
          return result
        })
        .catch((error) => {
          throw error
        })
    },
    // --- specs/319-process-bulk-actions-v2: bulk actions ---

    /**
     * GET /api/admin/processes/bulk/snapshot — snapshot id по текущему фильтру.
     *
     * @param {Object} filters - {status[], type[], threadId, chainId, includeDeleted, name, topLevelOnly, parentId}
     * @see specs/319-process-bulk-actions-v2/contracts/admin-process-bulk-rest-api.md (EP-5)
     */
    fetchBulkSelectionIds(ctx, filters = {}) {
      const query = buildProcessQuery(filters)
      // Путь /bulk/snapshot вместо /ids-by-filter — чтобы избежать routing-конфликта
      // с GET /{id} в Spring Boot 3.x (см. WP #68 comment 320).
      const url = `/api/admin/processes/bulk/snapshot${query ? `?${query}` : ''}`
      // eslint-disable-next-line no-console
      console.debug('[bulk] fetchBulkSelectionIds', { url, filters })
      return getJson(url)
        .then((result) => {
          // eslint-disable-next-line no-console
          console.debug('[bulk] fetchBulkSelectionIds result', result)
          ctx.commit('setBulkSelection', {
            ids: result.ids || [],
            total: result.total || 0,
            timestamp: new Date().toISOString(),
          })
          return result
        })
        .catch((error) => {
          // eslint-disable-next-line no-console
          console.error('[bulk] fetchBulkSelectionIds failed', url, error)
          throw error
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
          ctx.dispatch('pollBulkTask', { taskId, statusUrl })
          return { taskId, statusUrl }
        })
        .catch((error) => {
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
          ctx.dispatch('pollBulkTask', { taskId, statusUrl })
          return { taskId, statusUrl }
        })
        .catch((error) => {
          ctx.commit('setBulkOperationInProgress', false)
          throw error
        })
    },

    /**
     * Polling helper для async-операций (US4, T031).
     *
     * Опрос каждые 2 сек через `setTimeout` (не setInterval — переживает ручную отмену).
     * На terminal status — коммитит `SET_BULK_OPERATION_REPORT` для показа в UI.
     */
    pollBulkTask(ctx, { taskId, statusUrl }) {
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
                action: status.action,
                requested: status.totalCount,
                succeeded: status.succeededCount,
                failed: status.failedCount,
                errors: status.errors || [],
                durationMs:
                  status.finishedAt && status.startedAt ? status.finishedAt - status.startedAt : 0,
              })
              ctx.commit('setBulkOperationInProgress', false)
              // Удалить удалённые id из items (bulk_delete terminal).
              if (status.action === 'bulk_delete') {
                const failedIds = new Set((status.errors || []).map((e) => e.processId))
                const deletedIds = (ctx.state.bulkSelectionIds || []).filter(
                  (id) => !failedIds.has(id),
                )
                const items = ctx.state.items.filter((item) => !deletedIds.includes(item.id))
                ctx.commit('setProcessesItems', items)
              }
            }
          })
          .catch((error) => {
            ctx.commit('setBulkOperationInProgress', false)
            throw error
          })
      }
      poll()
    },
  },
}
