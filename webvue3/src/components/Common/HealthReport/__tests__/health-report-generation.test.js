// Регрессионный тест на гонку ответов HR-списка и адресность ремонта (BR-380,
// аудит #336, пакет PKG-S2-17, OpenProject #380).
//
// ДО ПРАВКИ `loadHealthReportList` коммитил ответ безусловно, а `healthReportList` —
// один глобальный список в Vuex. Открыть тяжёлую песню A (перебор KaraokeFileType ×
// MinIO), закрыть модалку, открыть B → ответ A приходил позже ответа B и затирал
// список: модалка с `:id` песни B показывала строки A, а «repair» отправлял
// `item.songId` (A) в `/api/song/executeHealthReportActions` — ремонт ЧУЖОЙ песни.
//
// Тест фиксирует: (1) применяется только ответ последнего запроса; (2) список помнит,
// какой песне он принадлежит; (3) «repair» отказывается работать по чужой строке.
//
// Запуск: cd webvue3 && node --test \
//   src/components/Common/HealthReport/__tests__/health-report-generation.test.js

import { test, describe } from 'node:test'
import assert from 'node:assert/strict'

import { loadHealthReportListWith, repairTargetError } from '../store.js'
import { createRequestGeneration } from '../../../../lib/target-request-guard.js'

function makeTransport() {
  const pending = []
  return {
    pending,
    request(request) {
      return new Promise((resolve, reject) => {
        pending.push({ request, resolve, reject })
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
  }
}

const flush = () => new Promise((resolve) => setImmediate(resolve))

describe('HR-список: ответ применяется только для актуальной песни (BR-380)', () => {
  test('поздний ответ песни A не затирает список песни B', async () => {
    const transport = makeTransport()
    const { log, commit } = makeCommitLog()
    const generation = createRequestGeneration()

    const a = loadHealthReportListWith({ request: transport.request, commit, generation, id: 1 })
    const b = loadHealthReportListWith({ request: transport.request, commit, generation, id: 2 })

    assert.equal(transport.pending[1].request.params.id, 2, 'второй запрос — за песню B')

    // Ответ B пришёл первым, A — позже.
    transport.pending[1].resolve(JSON.stringify([{ songId: 2, description: 'B' }]))
    await flush()
    transport.pending[0].resolve(JSON.stringify([{ songId: 1, description: 'A' }]))
    await a
    await b

    const applied = log.filter(([type]) => type === 'updateHealthReportList')
    assert.equal(applied.length, 1, 'применён должен быть ровно один список')
    assert.equal(applied[0][1][0].songId, 2, 'под модалкой B не должно оказаться строк A')
    assert.deepEqual(
      log.filter(([type]) => type === 'setHealthReportListSongId').pop(),
      ['setHealthReportListSongId', 2],
      'store должен помнить, чей это список',
    )
  })

  test('«repair» отказывается ремонтировать чужую песню', async () => {
    assert.match(String(repairTargetError(2, 1)), /ремонт отменён/)
    assert.equal(repairTargetError(2, 2), null, 'своя песня — можно')
    assert.equal(repairTargetError('2', 2), null, 'сравнение не зависит от типа id')
    assert.match(String(repairTargetError(null, 2)), /ещё не загружен/)
  })

  test('устаревший ответ не снимает флаг загрузки актуального', async () => {
    const transport = makeTransport()
    const { log, commit } = makeCommitLog()
    const generation = createRequestGeneration()

    const a = loadHealthReportListWith({ request: transport.request, commit, generation, id: 1 })
    const b = loadHealthReportListWith({ request: transport.request, commit, generation, id: 2 })

    transport.pending[0].resolve('[]')
    await a
    assert.deepEqual(
      log.filter(([type]) => type === 'setHealthReportListIsLoading').pop(),
      ['setHealthReportListIsLoading', true],
      'актуальный запрос ещё в полёте — «Загрузка…» должна остаться',
    )

    transport.pending[1].resolve('[]')
    await b
    assert.deepEqual(log.filter(([type]) => type === 'setHealthReportListIsLoading').pop(), [
      'setHealthReportListIsLoading',
      false,
    ])
  })

  test('ошибка снимает флаг загрузки (#325: модалка живёт и при нуле записей)', async () => {
    const transport = makeTransport()
    const { log, commit } = makeCommitLog()
    const generation = createRequestGeneration()

    const p = loadHealthReportListWith({ request: transport.request, commit, generation, id: 1 })
    transport.pending[0].reject(new Error('HTTP 500'))
    const result = await p

    assert.equal(result.applied, false)
    assert.deepEqual(log.filter(([type]) => type === 'setHealthReportListIsLoading').pop(), [
      'setHealthReportListIsLoading',
      false,
    ])
  })
})
