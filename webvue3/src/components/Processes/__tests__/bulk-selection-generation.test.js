// Регрессионный тест на «протухший» snapshot массовой выборки (BR-388, аудит #336,
// пакет PKG-S2-17, OpenProject #380).
//
// ДО ПРАВКИ `fetchBulkSelectionIds` коммитил snapshot безусловно, `clearBulkSelection`
// не вызывался нигде, а компонент при пустом snapshot подставлял id текущей страницы.
// Пока snapshot нового фильтра не пришёл (или если запрос упал), `canBulk` оставался
// true (`total > 0`), а `effectiveBulkIds` отдавал id ПРОШЛОГО фильтра: админ подтверждал
// удаление одного набора, а бэкенд удалял другой. После операции snapshot не
// пересчитывался — повторная операция снова слала старые id.
//
// Тест фиксирует fail-closed контракт: выборка помечается устаревшей до запроса,
// применяется только результат последнего запроса, сбой оставляет выборку пустой.
//
// Запуск: cd webvue3 && node --test src/components/Processes/__tests__/bulk-selection-generation.test.js

import { test, describe } from 'node:test'
import assert from 'node:assert/strict'

import { fetchBulkSelectionIdsWith } from '../store.js'
import { createRequestGeneration } from '../../../lib/target-request-guard.js'

function makeTransport() {
  const pending = []
  return {
    pending,
    getJson(url) {
      return new Promise((resolve, reject) => {
        pending.push({ url, resolve, reject })
      })
    },
  }
}

function makeCommitLog() {
  const log = []
  return {
    log,
    commit(type, payload) {
      log.push([type, payload])
    },
    // Воспроизводит state: stale очищает, setBulkSelection заполняет.
    readyAfter() {
      let ready = false
      for (const [type] of log) {
        if (type === 'markBulkSelectionStale') ready = false
        if (type === 'setBulkSelection') ready = true
      }
      return ready
    },
  }
}

const buildQuery = (filters) => (filters && filters.name ? `name=${filters.name}` : '')
const flush = () => new Promise((resolve) => setImmediate(resolve))

describe('bulk-snapshot: выборка принадлежит текущему фильтру (BR-388)', () => {
  test('при смене фильтра snapshot прежней выборки не применяется', async () => {
    const transport = makeTransport()
    const commitLog = makeCommitLog()
    const { commit } = commitLog
    const log = commitLog.log
    const generation = createRequestGeneration()

    // Фильтр A (старый) — snapshot ушёл, ответ задерживается.
    const a = fetchBulkSelectionIdsWith({
      getJson: transport.getJson,
      buildQuery,
      commit,
      generation,
      filters: { name: 'A' },
    })
    // Фильтр B: приходит новый запрос.
    const b = fetchBulkSelectionIdsWith({
      getJson: transport.getJson,
      buildQuery,
      commit,
      generation,
      filters: { name: 'B' },
    })

    // B отвечает первым, A — позже.
    transport.pending[1].resolve({ ids: [20], total: 1 })
    await flush()
    transport.pending[0].resolve({ ids: [10], total: 1 })
    await a
    await b

    const applied = log.filter(([type]) => type === 'setBulkSelection')
    assert.equal(applied.length, 1, 'применяется только snapshot последнего фильтра')
    assert.equal(applied[0][1].ids[0], 20, 'id прежнего фильтра не должны попасть в выборку')
    assert.equal(commitLog.readyAfter(), true)
  })

  test('до ответа snapshot выборка помечается устаревшей (кнопки закрыты)', () => {
    const transport = makeTransport()
    const commitLog = makeCommitLog()
    const { commit } = commitLog
    const log = commitLog.log
    const generation = createRequestGeneration()

    fetchBulkSelectionIdsWith({
      getJson: transport.getJson,
      buildQuery,
      commit,
      generation,
      filters: {},
    })

    assert.deepEqual(
      log[0],
      ['markBulkSelectionStale', undefined],
      'первое действие — сброс выборки',
    )
    assert.equal(
      commitLog.readyAfter(),
      false,
      'пока snapshot не пришёл, массовые операции запрещены',
    )
    transport.pending[0].resolve({ ids: [], total: 0 })
  })

  test('сбой snapshot оставляет выборку пустой, а не прежней', async () => {
    const transport = makeTransport()
    const commitLog = makeCommitLog()
    const { commit } = commitLog
    const log = commitLog.log
    const generation = createRequestGeneration()

    const p = fetchBulkSelectionIdsWith({
      getJson: transport.getJson,
      buildQuery,
      commit,
      generation,
      filters: {},
    })
    transport.pending[0].reject(new Error('HTTP 500'))
    const result = await p

    assert.equal(result, null)
    const last = log[log.length - 1]
    assert.deepEqual(
      last,
      ['markBulkSelectionStale', undefined],
      'ошибка не должна оставлять старые id',
    )
    assert.equal(commitLog.readyAfter(), false)
  })

  test('успешный snapshot помечает выборку готовой ровно для своего фильтра', async () => {
    const transport = makeTransport()
    const { commit } = makeCommitLog()
    const generation = createRequestGeneration()

    const p = fetchBulkSelectionIdsWith({
      getJson: transport.getJson,
      buildQuery,
      commit,
      generation,
      filters: { name: 'B' },
    })
    assert.match(transport.pending[0].url, /name=B/, 'фильтр должен уходить в snapshot')
    transport.pending[0].resolve({ ids: [1, 2, 3], total: 3 })
    const result = await p
    assert.deepEqual(result.ids, [1, 2, 3])
  })
})
