// Регрессионные тесты пакета PKG-S3-32 (аудит #336, OpenProject #404) — процессы.
//
// Записи пакета в этом файле:
//   BR-398 — CSV-экспорт отчёта: формульная инъекция (значение с `= + - @`).
//   BR-400 — блок ошибки загрузки процесса был вложен в `v-if="process"` и был
//            недостижим: при отказе `process` = null.
//   BR-401 — отказ опроса async-задачи: статус навсегда оставался `RUNNING`, отчёта
//            не было, исключение уходило в unhandled rejection.
//   BR-402 — терминальная обработка брала id из текущего `bulkSelectionIds`, а не из
//            запущенной операции: при смене фильтра вычитались чужие id.
//   BR-403 — audit-лог хранился в общем слоте без сброса и без привязки к процессу.
//   BR-408 — после применения фильтра загружалась первая сотня строк (без limit/offset),
//            страница не сбрасывалась, оба диспатча шли без `catch`.
//
// Запуск: cd webvue3 && node --test src/components/Processes/__tests__/pkg-s3-32-processes.test.js

import { test, describe, beforeEach } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

import processes from '../store.js'
import { FakeXhr } from '../../../test-utils/fakeXhr.js'

// Комментарии вырезаются: иначе проверка «в коде есть такой вызов» ловила бы
// и описание дефекта в комментарии.
const stripComments = (source) =>
  source
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/(^|[^:])\/\/[^\n]*/g, '$1')

/**
 * Ограничивает ожидание промиса: на старом коде часть экшенов не возвращает промис
 * (или возвращает промис, который никогда не резолвится), и тест зависал бы вместо
 * того, чтобы упасть с AssertionError.
 *
 * @param {Promise<any>} promise — ожидаемый промис.
 * @param {number} ms — предел ожидания, мс.
 * @returns {Promise<any>} результат промиса либо отказ по таймауту.
 */
const withTimeout = (promise, ms = 2000) =>
  Promise.race([
    promise,
    new Promise((_resolve, reject) =>
      setTimeout(() => reject(new Error(`таймаут ожидания промиса (${ms} мс)`)), ms),
    ),
  ])

const PROCESSES_STORE = stripComments(
  readFileSync(new URL('../store.js', import.meta.url), 'utf-8'),
)
const PROCESS_EDIT = stripComments(
  readFileSync(new URL('../edit/ProcessEdit.vue', import.meta.url), 'utf-8'),
)
const PROCESS_DELETE_MODAL = stripComments(
  readFileSync(new URL('../delete/ProcessDeleteModal.vue', import.meta.url), 'utf-8'),
)
const PROCESS_AUDIT_MODAL = stripComments(
  readFileSync(new URL('../audit/ProcessAuditModal.vue', import.meta.url), 'utf-8'),
)
const PROCESS_BULK_REPORT = stripComments(
  readFileSync(new URL('../ProcessBulkReportModal.vue', import.meta.url), 'utf-8'),
)
const PROCESSES_TABLE = stripComments(
  readFileSync(new URL('../ProcessesTable.vue', import.meta.url), 'utf-8'),
)
const PROCESSES_FILTER_MODAL = stripComments(
  readFileSync(new URL('../filter/ProcessesFilterModal.vue', import.meta.url), 'utf-8'),
)

/**
 * Минимальный Vuex-контекст: state/commit/dispatch, без зависимости от vuex.
 *
 * @param {Object} module — модуль стора.
 * @returns {{ctx: Object, state: Object}} контекст и state.
 */
function createCtx(module) {
  const state = structuredClone(module.state)
  const dispatched = []
  const ctx = { state, getters: {} }
  ctx.commit = (type, payload) => {
    const mutation = module.mutations[type]
    assert.ok(mutation, `неизвестная мутация ${type}`)
    mutation(state, payload)
  }
  ctx.dispatch = (type, payload) => {
    const action = module.actions[type]
    if (!action) {
      dispatched.push({ type, payload })
      return Promise.resolve(undefined)
    }
    return action(ctx, payload)
  }
  return { ctx, state, dispatched }
}

describe('PKG-S3-32 / Процессы', () => {
  beforeEach(() => {
    FakeXhr.install()
  })

  describe('BR-398 — CSV-экспорт не исполняет формулы', () => {
    test('модалка использует общий модуль csv-export', () => {
      assert.match(
        PROCESS_BULK_REPORT,
        /import \{ buildCsv \} from '\.\.\/\.\.\/lib\/csv-export\.js'/,
        'BR-398: экранирование ячейки должно быть в общем модуле (lib/csv-export.js)',
      )
      assert.doesNotMatch(
        PROCESS_BULK_REPORT,
        /function escapeCsvCell\s*\(/,
        'BR-398: локальная копия escapeCsvCell должна быть удалена',
      )
    })
  })

  describe('BR-400 — ошибка загрузки процесса достижима', () => {
    test('блок ошибки находится вне v-if="process"', () => {
      const errorIndex = PROCESS_EDIT.indexOf('class="pe-error"')
      const processIndex = PROCESS_EDIT.indexOf('v-if="process"')
      assert.ok(errorIndex > 0, 'BR-400: блок ошибки не найден')
      assert.ok(processIndex > 0, 'BR-400: блок формы не найден')
      assert.ok(
        errorIndex < processIndex,
        'BR-400: блок ошибки обязан быть ДО блока формы (вне v-if="process")',
      )
    })

    test('есть состояние загрузки и кнопка «Повторить»', () => {
      assert.match(PROCESS_EDIT, /loadState/, 'BR-400: нужно явное состояние загрузки')
      assert.match(PROCESS_EDIT, /Повторить/, 'BR-400: нужна кнопка «Повторить»')
      assert.match(PROCESS_EDIT, /Закрыть/, 'BR-400: нужна кнопка «Закрыть»')
    })

    test('модалка удаления показывает ошибку, а не вечное «Загрузка...»', () => {
      assert.match(
        PROCESS_DELETE_MODAL,
        /v-if="error"[\s\S]*pdm-error/,
        'BR-400: блок ошибки должен быть вне v-if="process"',
      )
      assert.ok(
        PROCESS_DELETE_MODAL.indexOf('pdm-error') < PROCESS_DELETE_MODAL.indexOf('v-if="process"'),
        'BR-400: блок ошибки обязан быть ДО блока формы',
      )
      assert.match(PROCESS_DELETE_MODAL, /loadState/, 'BR-400: нужно состояние загрузки')
    })
  })

  describe('BR-401 — отказ опроса фиксируется терминальным состоянием', () => {
    test('отказ опроса не бросает исключение и пишет FAILED', async () => {
      const { ctx, state } = createCtx(processes)
      state.items = [{ id: 1 }, { id: 2 }]

      const poll = ctx.dispatch('pollBulkTask', {
        taskId: 'task-1',
        statusUrl: '/api/admin/tasks/task-1',
        ids: [1, 2],
        action: 'bulk_delete',
      })
      FakeXhr.last((request) => request.url === '/api/admin/tasks/task-1').respond(500, '')
      // Промис НЕ должен отклоняться (иначе — unhandled rejection из таймерного цикла).
      await poll

      assert.ok(state.bulkTaskStatus, 'BR-401: статус задачи должен быть зафиксирован')
      assert.equal(state.bulkTaskStatus.status, 'FAILED', 'BR-401: статус должен стать FAILED')
      assert.ok(state.bulkOperationReport, 'BR-401: отчёт должен быть создан')
      assert.equal(state.bulkOperationReport.status, 'FAILED')
      assert.equal(state.bulkOperationReport.failed, 2)
      assert.equal(state.bulkOperationInProgress, false, 'BR-401: флаг операции снимается')
    })

    test('отказ запуска async-операции тоже даёт терминальное состояние', async () => {
      const { ctx, state } = createCtx(processes)
      const run = ctx.dispatch('bulkDeleteProcessesAsync', { ids: [1, 2, 3] })
      FakeXhr.last((request) => request.url === '/api/admin/processes/bulk-delete-async').respond(
        500,
        '',
      )
      await assert.rejects(withTimeout(run))
      assert.ok(state.bulkTaskStatus, 'BR-401: статус задачи должен быть зафиксирован')
      assert.equal(state.bulkTaskStatus.status, 'FAILED', 'BR-401: статус должен стать FAILED')
      assert.ok(state.bulkOperationReport, 'BR-401: отчёт должен быть создан')
    })
  })

  describe('BR-402 — терминальная обработка работает с id своей операции', () => {
    test('из items вычитаются только id запущенной операции', async () => {
      const { ctx, state } = createCtx(processes)
      state.items = [{ id: 1 }, { id: 2 }, { id: 99 }]

      const poll = ctx.dispatch('pollBulkTask', {
        taskId: 'task-2',
        statusUrl: '/api/admin/tasks/task-2',
        ids: [1, 2],
        action: 'bulk_delete',
      })
      FakeXhr.last((request) => request.url === '/api/admin/tasks/task-2').respond(200, {
        status: 'COMPLETED',
        action: 'bulk_delete',
        totalCount: 2,
        succeededCount: 2,
        failedCount: 0,
        errors: [],
      })
      await poll

      assert.deepEqual(
        state.items.map((i) => i.id),
        [99],
        'BR-402: удалены должны быть только id ЭТОЙ операции',
      )
    })

    test('id операции передаются в опрос при запуске', async () => {
      const { ctx } = createCtx(processes)
      ctx.dispatch('bulkDeleteProcessesAsync', { ids: [5, 6] })
      const dispatched = []
      // Захватываем аргументы dispatch'а pollBulkTask.
      const originalDispatch = ctx.dispatch
      ctx.dispatch = (type, payload) => {
        dispatched.push({ type, payload })
        if (type === 'pollBulkTask') return Promise.resolve(undefined)
        return originalDispatch(type, payload)
      }
      FakeXhr.last((request) => request.url === '/api/admin/processes/bulk-delete-async').respond(
        200,
        JSON.stringify({ taskId: 't', statusUrl: '/api/admin/tasks/t' }),
      )
      await new Promise((resolve) => setImmediate(resolve))

      const pollCall = dispatched.find((d) => d.type === 'pollBulkTask')
      assert.ok(pollCall, 'BR-402: pollBulkTask должен быть вызван')
      assert.deepEqual(pollCall.payload.ids, [5, 6], 'BR-402: id операции должны уйти в опрос')
    })
  })

  describe('BR-403 — audit-лог привязан к процессу', () => {
    test('перед запросом снимок чистится, а id сохраняется', async () => {
      const { ctx, state } = createCtx(processes)
      state.currentProcessAudit = [{ id: 1, action: 'старое' }]

      const load = ctx.dispatch('loadAudit', { id: 42, days: 30 })
      assert.deepEqual(state.currentProcessAudit, [], 'BR-403: снимок чистится ДО запроса')
      assert.equal(state.currentProcessAuditId, 42, 'BR-403: id процесса сохраняется')

      FakeXhr.last((request) => request.url.includes('/audit')).respond(200, {
        items: [{ id: 2, action: 'новое' }],
      })
      await withTimeout(load)
      assert.equal(state.currentProcessAudit.length, 1)
      assert.equal(state.currentProcessAudit[0].action, 'новое')
    })

    test('ответ для чужого id не применяется', async () => {
      const { ctx, state } = createCtx(processes)
      const load = ctx.dispatch('loadAudit', { id: 42, days: 30 })
      // Пока запрос в полёте, открыли audit другого процесса.
      state.currentProcessAuditId = 43
      FakeXhr.last((request) => request.url.includes('/audit')).respond(200, {
        items: [{ id: 2, action: 'чужое' }],
      })
      await withTimeout(load)
      assert.deepEqual(state.currentProcessAudit, [], 'BR-403: чужой ответ не применяется')
    })

    test('модалка показывает индикатор загрузки', () => {
      assert.match(
        PROCESS_AUDIT_MODAL,
        /getCurrentProcessAuditIsLoading/,
        'BR-403: модалка должна знать про состояние загрузки',
      )
      assert.match(PROCESS_AUDIT_MODAL, /Загрузка audit-лога/, 'BR-403: нужен индикатор загрузки')
    })
  })

  describe('BR-408 — фильтр применяется с limit/offset и сбросом страницы', () => {
    test('модалка отдаёт применение таблице, а не грузит список сама', () => {
      assert.match(
        PROCESSES_FILTER_MODAL,
        /\$emit\('applied'\)/,
        'BR-408: модалка должна сообщать таблице о применении фильтра',
      )
      assert.doesNotMatch(
        PROCESSES_FILTER_MODAL,
        /dispatch\('loadProcesses'/,
        'BR-408: список грузит таблица (у неё limit/offset)',
      )
    })

    test('таблица сбрасывает страницу на 1 и грузит её один раз', () => {
      assert.match(
        PROCESSES_TABLE,
        /onFilterApplied\s*\(\)\s*\{[\s\S]*skipNextPageLoad[\s\S]*loadPage\(\)/,
        'BR-408: при применении фильтра страница сбрасывается и грузится один раз',
      )
    })

    test('оба диспатча загрузки страницы обрабатывают отказ', () => {
      assert.match(
        PROCESSES_TABLE,
        /dispatch\('loadProcesses', filters\)\.catch\(/,
        'BR-408: отказ загрузки списка не должен быть unhandled rejection',
      )
      assert.match(
        PROCESSES_TABLE,
        /dispatch\('fetchBulkSelectionIds', filters\)\.catch\(/,
        'BR-408: отказ обновления snapshot не должен быть unhandled rejection',
      )
    })
  })
})
